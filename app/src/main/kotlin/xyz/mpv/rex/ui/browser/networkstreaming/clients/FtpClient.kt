package xyz.mpv.rex.ui.browser.networkstreaming.clients

import android.net.Uri
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.utils.storage.FileTypeUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.InputStream

/**
 * FTP client implementation.
 */
class FtpClient(connection: NetworkConnection) : BaseNetworkClient(connection) {
    private var ftpClient: FTPClient? = null

    override suspend fun performConnect() {
        val client = FTPClient()
        client.controlEncoding = "UTF-8"
        client.setConnectTimeout(15000)
        client.setDataTimeout(60000)
        client.setDefaultTimeout(60000)
        client.controlKeepAliveTimeout = 300
        client.setControlKeepAliveReplyTimeout(10000)

        client.connect(connection.host, connection.port)

        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            client.disconnect()
            throw Exception("FTP server refused connection")
        }

        val success = if (connection.isAnonymous) {
            client.login("anonymous", "")
        } else {
            client.login(connection.username, connection.password)
        }

        if (!success) {
            client.disconnect()
            throw Exception("Login failed")
        }

        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.enterLocalPassiveMode()
        runCatching { client.sendCommand("OPTS UTF8 ON") }
        client.bufferSize = 1024 * 64

        if (connection.path != "/" && connection.path.isNotEmpty()) {
            client.changeWorkingDirectory(connection.path)
        }

        ftpClient = client
    }

    override suspend fun performDisconnect() {
        ftpClient?.let { client ->
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
        }
        ftpClient = null
    }

    override fun checkIsConnected(): Boolean = ftpClient?.isConnected == true

    override suspend fun performListFiles(path: String): List<NetworkFile> {
        val client = ftpClient ?: throw Exception("Not connected")
        val workingPath = normalizePath(path)
        val files = client.listFiles(workingPath) ?: emptyArray()

        return files.map { file ->
            val fileName = file.name
            val filePath = if (path.isEmpty() || path == "/") fileName
            else "${path.trimEnd('/')}/$fileName"

            NetworkFile(
                name = fileName,
                path = filePath,
                isDirectory = file.isDirectory,
                size = file.size,
                lastModified = file.timestamp?.timeInMillis ?: 0,
                mimeType = if (file.isDirectory) null else getMimeType(fileName),
                isHidden = fileName.startsWith("."),
            )
        }
    }

    override suspend fun performGetFileStream(path: String): InputStream {
        val client = ftpClient ?: throw Exception("Not connected")
        return client.retrieveFileStream(path) ?: throw Exception("Failed to get file stream")
    }

    override suspend fun performGetFileUri(path: String): Uri {
        val protocol = "ftp"
        val basePath = connection.path.trim('/')
        val cleanPath = path.trim('/')
        
        val fullPath = when {
            cleanPath.isEmpty() -> basePath
            basePath.isEmpty() -> cleanPath
            else -> "$basePath/$cleanPath"
        }

        val uriString = if (connection.isAnonymous) {
            "$protocol://${connection.host}:${connection.port}/$fullPath"
        } else {
            "$protocol://${connection.username}:${connection.password}@${connection.host}:${connection.port}/$fullPath"
        }
        return Uri.parse(uriString)
    }

    override suspend fun performGetFileSize(path: String): Long {
        val client = ftpClient ?: throw Exception("Not connected")
        return client.mlistFile(path)?.size ?: 0L
    }

    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mime = FileTypeUtils.getMimeTypeFromExtension(extension)
        return mime.takeIf { it != "*/*" }
    }
}
