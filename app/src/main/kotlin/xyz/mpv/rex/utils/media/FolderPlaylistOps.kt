package xyz.mpv.rex.utils.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.core.net.toUri
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import xyz.mpv.rex.utils.sort.SortUtils
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.utils.storage.NetworkFileFilter
import xyz.mpv.rex.utils.storage.MediaScanPolicy
import xyz.mpv.rex.utils.storage.VideoScanUtils
import java.io.File
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared utility operations for auto-generating playlists from local folders or media library.
 * Used by both PlayerActivity and MediaUtils (for direct mini player playback).
 */
object FolderPlaylistOps : KoinComponent {
  private val browserPreferences: BrowserPreferences by inject()
  private val networkRepository: NetworkRepository by inject()

  private const val SMB_SCHEME = "smb://"

  /**
   * Generates a playlist of URIs in the same folder as [currentPath].
   *
   * @return Pair of (uris, initialIndex) or null if single/invalid file.
   */
  suspend fun generateFolderPlaylist(
    context: Context,
    currentPath: String,
    launchSource: String? = null,
  ): Pair<List<Uri>, Int>? {
    runCatching {
      val currentFile = resolveLocalFile(context, currentPath) ?: return null
      if (!currentFile.exists()) return null

      val parentFolder = currentFile.parentFile ?: return null
      val scanPolicy = MediaScanPolicy(
        includeNoMediaContent = browserPreferences.includeNoMediaContent.get(),
      )
      if (!scanPolicy.includeNoMediaContent &&
        FileFilterUtils.isWithinNoMediaBoundary(parentFolder)
      ) {
        return null
      }

      val showAudio = browserPreferences.showAudioFiles.get()
      val files = parentFolder.listFiles { file ->
        file.isFile &&
          !FileFilterUtils.shouldSkipFile(file) &&
          (FileTypeUtils.isVideoFile(file) || (showAudio && FileTypeUtils.isAudioFile(file)))
      } ?: return null

      val lSource = launchSource ?: ""
      val siblingFiles = if (lSource == "video_list" || lSource == "recently_played_button" || lSource == "first_video_button") {
        val videoSortType = browserPreferences.videoSortType.get()
        val videoSortOrder = browserPreferences.videoSortOrder.get()
        val bucketId = parentFolder.absolutePath.replace("\\", "/")
        val videosInFolder = VideoScanUtils.getVideosInFolder(context, bucketId, scanPolicy)
        val sortedVideos = SortUtils.sortVideos(videosInFolder, videoSortType, videoSortOrder)
        sortedVideos.mapNotNull { video -> files.find { it.absolutePath == video.path } }
      } else if (lSource == "tree_mode") {
        val folderSortType = browserPreferences.folderSortType.get()
        val folderSortOrder = browserPreferences.folderSortOrder.get()
        val videosInFolder = VideoScanUtils.getVideosInFolder(context, parentFolder.absolutePath, scanPolicy)
        val sortedVideos = when (folderSortType) {
          FolderSortType.Title -> videosInFolder.sortedWith { t1, t2 -> SortUtils.NaturalOrderComparator.DEFAULT.compare(t1.displayName, t2.displayName) }
          FolderSortType.Duration -> videosInFolder.sortedBy { it.duration }
          FolderSortType.Date -> videosInFolder.sortedBy { File(it.path).lastModified() }
          FolderSortType.Size -> videosInFolder.sortedBy { it.size }
          FolderSortType.VideoCount -> videosInFolder.sortedBy { it.duration }
        }
        val orderedVideos = if (folderSortOrder.isAscending) sortedVideos else sortedVideos.reversed()
        orderedVideos.mapNotNull { video -> files.find { it.absolutePath == video.path } }
      } else {
        files.sortedWith { f1, f2 -> SortUtils.NaturalOrderComparator.DEFAULT.compare(f1.name, f2.name) }
      }

      if (siblingFiles.size <= 1) return null

      val newPlaylist = siblingFiles.map { it.toUri() }
      val newIndex = siblingFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }

      if (newIndex != -1) {
        return Pair(newPlaylist, newIndex)
      }
    }
    return null
  }

  private fun resolveLocalFile(context: Context, value: String): File? {
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    if (uri?.scheme != "content") return File(value)

    return runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
        null,
        null,
        null,
      )?.use { cursor ->
        val dataColumn = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
        if (dataColumn >= 0 && cursor.moveToFirst()) {
          cursor.getString(dataColumn)?.let(::File)
        } else {
          null
        }
      }
    }.getOrNull()
  }

  /**
   * Generates a playlist of URIs from the media library.
   *
   * @return Pair of (uris, initialIndex) or null if single/invalid file.
   */
  suspend fun generateMediaLibraryPlaylist(
    context: Context,
    currentPath: String,
  ): Pair<List<Uri>, Int>? {
    runCatching {
      val allVideos = MediaFileRepository.getAllVideos(context)
      val videoSortType = browserPreferences.videoSortType.get()
      val videoSortOrder = browserPreferences.videoSortOrder.get()

      var filteredVideos = allVideos
      if (!browserPreferences.showAudioFiles.get()) {
        filteredVideos = allVideos.filterNot { it.isAudio }
      }

      val sortedVideos = SortUtils.sortVideos(filteredVideos, videoSortType, videoSortOrder)
      if (sortedVideos.size <= 1) return null

      val newPlaylist = sortedVideos.map { it.uri }
      val newIndex = sortedVideos.indexOfFirst { it.path == currentPath || it.uri.toString() == currentPath }

      if (newIndex != -1) {
        return Pair(newPlaylist, newIndex)
      }
    }
    return null
  }

  /**
   * Generates a playlist of `smb://` URIs sitting beside [smbPath] in the same remote folder.
   *
   * [smbPath] comes from an external file manager (MiXplorer's `real_path` extra); the credentials
   * come from a saved connection matched on host + share. Without one there is nothing we can list,
   * which is a "no playlist" answer, not an error.
   *
   * @return `failure` when the share could not be listed, `success(null)` when there is no playlist
   *   to build (not an SMB path, no saved connection, or no siblings).
   */
  /**
   * Represents an external playlist parsed from an external file manager intent (MX Player API).
   */
  data class ExternalPlaylist(
    val items: List<Uri>,
    val titles: List<String>,
    val initialIndex: Int,
    val isExplicit: Boolean,
  )

  /**
   * Extracts external playlist extras adhering to the MX Player intent API standard:
   * `video_list`, `video_list.name`, and `video_list_is_explicit`.
   */
  fun extractExternalPlaylist(intent: Intent): ExternalPlaylist? {
    val rawList: List<Uri> = run {
      val parcelableList = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra("video_list", Uri::class.java)
            ?: intent.getParcelableArrayListExtra("video_list", Parcelable::class.java)?.filterIsInstance<Uri>()
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra<Parcelable>("video_list")?.filterIsInstance<Uri>()
        }
      }.getOrNull()?.filterNotNull()
      if (!parcelableList.isNullOrEmpty()) {
        return@run parcelableList
      }

      val array = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayExtra("video_list", Uri::class.java)
            ?: intent.getParcelableArrayExtra("video_list", Parcelable::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayExtra("video_list")
        }
      }.getOrNull()
      val filteredArray = array?.mapNotNull { it as? Uri }
      if (!filteredArray.isNullOrEmpty()) {
        return@run filteredArray
      }

      val stringUris = (runCatching { intent.getStringArrayExtra("video_list")?.toList() }.getOrNull()
        ?: runCatching { intent.getStringArrayListExtra("video_list") }.getOrNull())
        ?.takeIf { it.isNotEmpty() }
        ?.mapNotNull { str -> str?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } }
      if (!stringUris.isNullOrEmpty()) {
        return@run stringUris
      }

      emptyList()
    }

    if (rawList.isEmpty()) return null

    val rawNames: List<String>? = runCatching {
      intent.getStringArrayExtra("video_list.name")?.filterNotNull()?.takeIf { it.isNotEmpty() }
        ?: intent.getStringArrayListExtra("video_list.name")?.filterNotNull()?.takeIf { it.isNotEmpty() }
        ?: intent.getCharSequenceArrayExtra("video_list.name")?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
        ?: intent.getCharSequenceArrayListExtra("video_list.name")?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    val isExplicit = intent.getBooleanExtra("video_list_is_explicit", false)

    val currentData = intent.data
    val initialIndex = currentData?.let { dataUri ->
      rawList.indexOfFirst { it == dataUri || it.toString().equals(dataUri.toString(), ignoreCase = true) }
        .takeIf { it >= 0 }
        ?: dataUri.path?.trimEnd('/')?.let { cleanDataPath ->
          rawList.indexOfFirst { it.path?.trimEnd('/')?.equals(cleanDataPath, ignoreCase = true) == true }.takeIf { it >= 0 }
        }
    } ?: 0

    val titles = rawList.mapIndexed { index, uri ->
      rawNames?.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "Video ${index + 1}"
    }

    return ExternalPlaylist(
      items = rawList,
      titles = titles,
      initialIndex = initialIndex,
      isExplicit = isExplicit,
    )
  }

  /**
   * Generates a playlist of `smb://` URIs sitting beside [smbPath] in the same remote folder.
   *
   * [smbPath] comes from an external file manager (MiXplorer's `real_path` extra or direct `smb://` intent);
   * credentials come from the URI itself or from a saved connection matched on host + share.
   * If neither exists, falls back to anonymous/guest enumeration.
   *
   * @return `failure` when the share could not be listed, `success(null)` when there is no playlist
   *   to build (not an SMB path or no siblings).
   */
  suspend fun generateNetworkFolderPlaylist(smbPath: String): Result<Pair<List<Uri>, Int>?> {
    val parsed = parseSmbPath(smbPath) ?: return Result.success(null)
    val connection = resolveNetworkConnection(parsed)
    return networkSiblings(connection, parsed.filePath, parsed)
  }

  /**
   * Same as above for files launched from our own network browser, which already knows which
   * connection it came from and passes a share-relative path.
   */
  suspend fun generateNetworkFolderPlaylist(
    connectionId: Long,
    filePath: String,
  ): Result<Pair<List<Uri>, Int>?> {
    val connection = networkRepository.getConnectionById(connectionId) ?: return Result.success(null)
    if (connection.protocol != NetworkProtocol.SMB) return Result.success(null)
    return networkSiblings(connection, filePath.trim('/'))
  }

  /**
   * A playable local-proxy URL for one `smb://` playlist entry, plus the identity of the remote file
   * behind it so playback state stays keyed the same way a direct launch keys it.
   */
  data class SmbStream(
    val url: String,
    val connectionId: Long,
    val filePath: String,
    val streamId: String = "",
  )

  /**
   * Resolves a [NetworkConnection] for an SMB path:
   * 1. If embedded credentials (username, password, port) exist in the URI, constructs a transient connection.
   * 2. Otherwise checks saved connections in [NetworkRepository].
   * 3. If no saved connection exists, falls back to an anonymous/guest connection.
   */
  internal suspend fun resolveNetworkConnection(
    parsed: SmbPath,
    netRepo: NetworkRepository? = null,
  ): NetworkConnection {
    val isGuest = parsed.username.isNullOrEmpty() && parsed.password.isNullOrEmpty()
    if (isGuest) {
      val repository = netRepo ?: runCatching { networkRepository }.getOrNull()
      val saved = repository?.findSmbConnection(parsed.host, parsed.share)
      if (saved != null) {
        return parsed.port?.takeIf { it != saved.port }?.let { saved.copy(port = it) } ?: saved
      }
    }

    return NetworkConnection(
      id = -1L,
      name = if (isGuest) "Guest SMB" else "Transient SMB",
      protocol = NetworkProtocol.SMB,
      host = parsed.host,
      port = parsed.port ?: NetworkProtocol.SMB.defaultPort,
      username = parsed.username ?: "",
      password = parsed.password ?: "",
      path = parsed.share,
      isAnonymous = isGuest,
    )
  }

  /**
   * Mints a proxy URL for a single `smb://` playlist entry.
   *
   * Deliberately one at a time: [NetworkStreamingProxy.registerStream] builds a client per call.
   * Previous streams are unregistered by PlayerActivity upon navigation or playback exit.
   */
  suspend fun resolveSmbUri(smbPath: String): SmbStream? {
    val parsed = parseSmbPath(smbPath) ?: return null
    val connection = resolveNetworkConnection(parsed)
    val extension = File(parsed.filePath).extension
    val streamId = "${if (connection.id != -1L) connection.id else "smb"}_${System.currentTimeMillis()}"
    val url = NetworkStreamingProxy.getInstance().registerStream(
      streamId = streamId,
      connection = connection,
      filePath = parsed.filePath,
      mimeType = FileTypeUtils.getMimeTypeFromExtension(extension),
    )
    return SmbStream(url, connection.id, parsed.filePath, streamId)
  }

  /**
   * Orders remote files the way the network browser is configured to show them, so an auto-generated
   * playlist walks the folder in the order the user is looking at.
   *
   * Duration and video count cannot be known without opening every remote file, so both fall back to
   * the filename. Name is also the tie-break, keeping the browser and the playlist in lockstep when
   * sizes or timestamps collide.
   */
  fun networkFileComparator(foldersFirst: Boolean = false): Comparator<NetworkFile> {
    return NetworkFileComparator.create(
      sortType = browserPreferences.networkSortType.get(),
      sortOrder = browserPreferences.networkSortOrder.get(),
      foldersFirst = foldersFirst,
    )
  }

  /**
   * Lists [filePath]'s remote folder and turns it into a playlist, mirroring the local rules:
   * media extensions only, the browser's sort preference, and no playlist for a lone file.
   */
  private suspend fun networkSiblings(
    connection: NetworkConnection,
    filePath: String,
    parsed: SmbPath? = null,
  ): Result<Pair<List<Uri>, Int>?> {
    val parentPath = filePath.substringBeforeLast('/', "")
    val files = networkRepository.listFiles(connection, parentPath)
      .getOrElse { return Result.failure(it) }

    val showHidden = browserPreferences.networkShowHiddenFiles.get()
    val siblings = NetworkFileFilter.filter(files, showHidden)
      .filter {
        !it.isDirectory && FileTypeUtils.isMediaFile(File(it.name))
      }
      .sortedWith(networkFileComparator())

    if (siblings.size <= 1) return Result.success(null)

    val index = siblings.indexOfFirst { it.path.equals(filePath, ignoreCase = true) }
    if (index == -1) return Result.success(null)

    val authPart = if (parsed != null && !parsed.username.isNullOrEmpty()) {
      "${parsed.username}${if (!parsed.password.isNullOrEmpty()) ":${parsed.password}" else ""}@"
    } else ""
    val effectivePort = (parsed?.port?.takeIf { it > 0 } ?: connection.port).takeIf { it > 0 && it != 445 }
    val portPart = if (effectivePort != null) ":$effectivePort" else ""
    val base = "$SMB_SCHEME$authPart${connection.host}$portPart/${connection.path.trim('/')}/"
    return Result.success(siblings.map { (base + it.path).toUri() } to index)
  }

  internal data class SmbPath(
    val host: String,
    val share: String,
    val filePath: String,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
  )

  /**
   * Splits `smb://[[user[:pass]@]host[:port]]/share/dir/file.ext` by hand.
   *
   * String surgery on purpose: external file managers hand over raw, unencoded spaces and
   * parentheses, which `Uri.parse` does not treat as a path. Percent escapes are left untouched for
   * the same reason — the observed input is unencoded, so a literal `%` in a file name must survive.
   * Extracts embedded username, password, and port when present.
   */
  internal fun parseSmbPath(raw: String): SmbPath? {
    if (!raw.startsWith(SMB_SCHEME, ignoreCase = true)) return null
    val rest = raw.substring(SMB_SCHEME.length)
    val authority = rest.substringBefore('/')
    if (authority.isEmpty()) return null
    val onShare = rest.substringAfter('/', "")
    val share = onShare.substringBefore('/')
    if (share.isEmpty()) return null
    val filePath = onShare.substringAfter('/', "")
    if (filePath.isEmpty()) return null

    var username: String? = null
    var password: String? = null
    val hostPort: String

    if (authority.contains('@')) {
      val userInfo = authority.substringBeforeLast('@')
      hostPort = authority.substringAfterLast('@')
      if (userInfo.isNotEmpty()) {
        val parts = userInfo.split(':', limit = 2)
        username = parts[0]
        password = parts.getOrNull(1)
      }
    } else {
      hostPort = authority
    }

    val host: String
    var port: Int? = null

    if (hostPort.startsWith('[') && hostPort.contains(']')) {
      host = hostPort.substringAfter('[').substringBefore(']')
      val afterBracket = hostPort.substringAfter(']')
      if (afterBracket.startsWith(':')) {
        port = afterBracket.removePrefix(":").toIntOrNull()
      }
    } else if (hostPort.count { it == ':' } > 1) {
      host = hostPort
      port = null
    } else if (hostPort.contains(':')) {
      host = hostPort.substringBefore(':')
      port = hostPort.substringAfter(':').toIntOrNull()
    } else {
      host = hostPort
    }

    if (host.isEmpty()) return null

    return SmbPath(
      host = host,
      share = share,
      filePath = filePath,
      port = port,
      username = username,
      password = password,
    )
  }
}
