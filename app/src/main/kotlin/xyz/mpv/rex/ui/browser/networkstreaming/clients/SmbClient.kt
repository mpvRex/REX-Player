package xyz.mpv.rex.ui.browser.networkstreaming.clients

import android.net.Uri
import android.util.Log
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.utils.storage.FileTypeUtils
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB client implementation (SMB 2/3).
 */
class SmbClient(connection: NetworkConnection) : BaseNetworkClient(connection) {
    private var smbClient: SMBClient? = null
    private var smbConnection: Connection? = null
    private var session: Session? = null
    private var shareName: String = ""
    private var resolvedHostIp: String = ""
    private val connectionMutex = Mutex()

    companion object {
        private const val TAG = "SmbClient"
        private const val FILE_ATTRIBUTE_HIDDEN = 0x00000002L
        private const val FILE_ATTRIBUTE_SYSTEM = 0x00000004L
        private const val FILE_ATTRIBUTE_DIRECTORY = 0x00000010L
    }

    /**
     * Recursively traverses the exception chain to detect underlying network transport or timeout failures.
     */
    private fun isNetworkError(e: Throwable?): Boolean {
        var current = e
        while (current != null) {
            if (current is java.util.concurrent.TimeoutException ||
                current is com.hierynomus.protocol.transport.TransportException ||
                current is com.hierynomus.smbj.common.SMBRuntimeException ||
                current is java.net.SocketException ||
                current is java.net.SocketTimeoutException ||
                current is java.io.EOFException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Executes a network operation with a single-retry mechanism.
     * Prevents race conditions during concurrent reconnects via strict session reference tracking.
     */
    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        val sessionAtStart = session
        
        return try {
            // Pre-flight check: Force a reconnect immediately if the session is inherently dead
            if (session == null) {
                throw java.net.SocketException("Session is null on execute")
            }
            block()
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                Log.w(TAG, "SMB socket dead. Reconnecting...", e)
                
                connectionMutex.withLock {
                    // Reconnect only if another coroutine hasn't already rebuilt this specific session
                    if (session === sessionAtStart) {
                        performDisconnect()
                        performConnect()
                    }
                }
                
                block()
            } else {
                throw e
            }
        }
    }

    override suspend fun performConnect() {
        val config = SmbConfig.builder()
            .withTimeout(30000, TimeUnit.MILLISECONDS)
            .withSoTimeout(35000, TimeUnit.MILLISECONDS)
            .withDialects(
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_1_1,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0_2,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_2_1,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_2_0_2,
            )
            .withDfsEnabled(false)
            .withSigningRequired(false)
            .withEncryptData(false)
            .build()

        smbClient = SMBClient(config)

        val resolvedAddress = withTimeout(5000) {
            java.net.InetAddress.getByName(connection.host)
        }
        resolvedHostIp = resolvedAddress.hostAddress ?: connection.host

        shareName = connection.path.trim('/')
        if (shareName.isEmpty() || shareName.contains('/')) {
            throw Exception("Share name required. Path should be just the share name (e.g., /Media).")
        }

        smbConnection = smbClient?.connect(resolvedHostIp, connection.port)
        
        val authContext = if (connection.isAnonymous) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(connection.username, connection.password.toCharArray(), "")
        }

        session = smbConnection?.authenticate(authContext)
        
        // Test share access
        (session?.connectShare(shareName) as? DiskShare)?.close()
    }

    override suspend fun performDisconnect() {
        runCatching { session?.close() }
        runCatching { smbConnection?.close() }
        smbClient = null
        session = null
        smbConnection = null
    }

    override fun checkIsConnected(): Boolean = session != null

    override suspend fun performListFiles(path: String): List<NetworkFile> {
        return executeWithRetry {
            val diskShare = session?.connectShare(shareName) as? DiskShare ?: throw java.net.SocketException("Session is null or share failed")
            
            diskShare.use { diskShare ->
                val smbPath = path.trim('/').replace('/', '\\')
                val fileList = diskShare.list(smbPath)
                
                fileList.filter { it.fileName != "." && it.fileName != ".." }
                    .map { info ->
                        val fileName = info.fileName
                        val filePath = if (path.isEmpty() || path == "/") fileName 
                        else "${path.trimEnd('/')}/$fileName"
                        val isDirectory = info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY != 0L
                        val isHidden = fileName.startsWith(".") ||
                            (info.fileAttributes and FILE_ATTRIBUTE_HIDDEN != 0L) ||
                            (info.fileAttributes and FILE_ATTRIBUTE_SYSTEM != 0L)

                        NetworkFile(
                            name = fileName,
                            path = filePath,
                            isDirectory = isDirectory,
                            size = info.endOfFile,
                            lastModified = info.changeTime.toEpoch(TimeUnit.MILLISECONDS),
                            mimeType = if (isDirectory) null else getMimeType(fileName),
                            isHidden = isHidden,
                        )
                    }
            }
        }
    }

    override suspend fun performGetFileStream(path: String): InputStream {
        return executeWithRetry {
            val diskShare = session?.connectShare(shareName) as? DiskShare ?: throw java.net.SocketException("Session is null or share failed")
            val smbPath = path.trim('/').replace('/', '\\')
            
            val file = diskShare.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            
            file.inputStream
        }
    }

    override suspend fun performGetFileUri(path: String): Uri {
        val cleanPath = path.trim('/')
        val uriString = if (connection.isAnonymous) {
            "smb://${connection.host}:${connection.port}/$shareName/$cleanPath"
        } else {
            "smb://${connection.username}:${connection.password}@${connection.host}:${connection.port}/$shareName/$cleanPath"
        }
        return Uri.parse(uriString)
    }

    /**
     * Runs [block] with this client's shared session, reconnecting
     * automatically (with the usual single-retry + session-reference guard)
     * if the session is dead.
     *
     * The session/connection are owned by this client and must NOT be closed
     * by the caller. Callers should open their own tree connection
     * (session.connectShare) and file handles inside [block], and close only
     * those.
     */
    suspend fun <T> withSharedSession(block: suspend (session: Session, share: String) -> T): T =
        executeWithRetry {
            val current = session ?: throw java.net.SocketException("Session is null on execute")
            block(current, shareName)
        }

    override suspend fun performGetFileSize(path: String): Long {
        return executeWithRetry {
            val diskShare = session?.connectShare(shareName) as? DiskShare ?: throw java.net.SocketException("Session is null or share failed")
            try {
                val smbPath = path.trim('/').replace('/', '\\')
                diskShare.getFileInformation(smbPath).standardInformation.endOfFile
            } finally {
                diskShare.close()
            }
        }
    }

    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mime = FileTypeUtils.getMimeTypeFromExtension(extension)
        return mime.takeIf { it != "*/*" }
    }
}
