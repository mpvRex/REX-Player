package xyz.mpv.rex.ui.player.delegates

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import xyz.mpv.rex.ui.player.MediaPlaybackService
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG

import xyz.mpv.rex.ui.player.resolveUri
import xyz.mpv.rex.utils.media.FolderPlaylistOps
import xyz.mpv.rex.utils.media.HttpUtils
import xyz.mpv.rex.utils.media.M3UParseResult
import xyz.mpv.rex.utils.media.M3UParser

/**
 * Controller responsible for M3U playlist parsing, folder tree walking, auto-playlist generation,
 * playlist navigation, and network stream switching.
 */
class PlayerPlaylistLoader(
  private val activity: PlayerActivity,
) {
  // PRELOAD-RESUME-STATE-V1
  var activeNetworkStreamId: String? = null
  var isAutoplayNextTriggered: Boolean = false

  /**
   * Bumped on every SMB playlist navigation so a resolve that finishes late, after the user has
   * already pressed Next again, is discarded instead of loading on top of the newer one.
   */
  private var smbResolveToken = 0

  /**
   * Check if there's a next video in the playlist
   */
  fun hasNext(): Boolean {
    val playlistHasNext = activity.viewModel.playlistManager.hasNext(activity.viewModel.shouldRepeatPlaylist())
    val mpvCount = runCatching { MPVLib.getPropertyInt("playlist-count") }.getOrNull() ?: 0
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasNext = mpvCount > 1 && mpvPos < mpvCount - 1
    return playlistHasNext || mpvHasNext
  }

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean {
    val playlistHasPrev = activity.viewModel.playlistManager.hasPrevious(activity.viewModel.shouldRepeatPlaylist())
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasPrev = mpvPos > 0
    return playlistHasPrev || mpvHasPrev
  }

  fun updateMiniPlayerPlaylistState() {
    val nextIndex = activity.viewModel.playlistManager.getNextIndex(activity.viewModel.shouldRepeatPlaylist())
    val nextUri = if (nextIndex != null) activity.viewModel.playlistManager.playlist.value.getOrNull(nextIndex) else null
    val nextTitle = if (nextIndex != null) {
      activity.viewModel.playlistManager.getTitleAt(nextIndex) ?: nextUri?.let { activity.extractFileNameFromUri(it) }
    } else null

    val prevIndex = activity.viewModel.playlistManager.getPreviousIndex(activity.viewModel.shouldRepeatPlaylist())
    val prevUri = if (prevIndex != null) activity.viewModel.playlistManager.playlist.value.getOrNull(prevIndex) else null
    val prevTitle = if (prevIndex != null) {
      activity.viewModel.playlistManager.getTitleAt(prevIndex) ?: prevUri?.let { activity.extractFileNameFromUri(it) }
    } else null

    val hasNextItem = hasNext()
    val hasPrevItem = hasPrevious()

    activity.miniPlayerStateManager.updateState(
      hasNext = hasNextItem,
      hasPrevious = hasPrevItem,
      nextTitle = nextTitle,
      prevTitle = prevTitle,
    )

    activity.lifecycleScope.launch(Dispatchers.IO) {
      val cachedNextThumb = nextUri?.let { activity.getCachedThumbnailForUri(it) }
      val cachedPrevThumb = prevUri?.let { activity.getCachedThumbnailForUri(it) }
      withContext(Dispatchers.Main) {
        activity.miniPlayerStateManager.updateState(
          nextThumbnail = cachedNextThumb,
          prevThumbnail = cachedPrevThumb,
        )
      }
      val fullNextThumbnail = nextUri?.let { activity.extractThumbnailOrCoverArt(it) }
      val fullPrevThumbnail = prevUri?.let { activity.extractThumbnailOrCoverArt(it) }
      withContext(Dispatchers.Main) {
        activity.miniPlayerStateManager.updateState(
          nextThumbnail = fullNextThumbnail ?: cachedNextThumb,
          prevThumbnail = fullPrevThumbnail ?: cachedPrevThumb,
        )
      }
    }
  }

  /**
   * Play the next video in the playlist
   */
  fun playNext() {
    val nextIndex = activity.viewModel.playlistManager.getNextIndex(activity.viewModel.shouldRepeatPlaylist())
    if (nextIndex != null) {
      loadPlaylistItem(nextIndex)
    }
  }

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() {
    val prevIndex = activity.viewModel.playlistManager.getPreviousIndex(activity.viewModel.shouldRepeatPlaylist())
    if (prevIndex != null) {
      loadPlaylistItem(prevIndex)
    }
  }

  /**
   * Load a playlist item by index
   */
  fun loadPlaylistItem(index: Int) {
    val playlist = activity.viewModel.playlistManager.playlist.value
    // All items are loaded - just validate index and load directly
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }
    isAutoplayNextTriggered = true
    val loadGeneration = activity.beginPlaybackLoadRequest()
    loadPlaylistItemInternal(index, loadGeneration)
  }

  /**
   * Internal method to load a playlist item
   */
  fun loadPlaylistItemInternal(index: Int, loadGeneration: Long = activity.beginPlaybackLoadRequest()) {
    val playlist = activity.viewModel.playlistManager.playlist.value
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }

    val uri = playlist[index]
    if (uri.scheme.equals("smb", ignoreCase = true)) {
      val token = ++smbResolveToken
      activity.lifecycleScope.launch {
        val stream = withContext(Dispatchers.IO) {
          runCatching { FolderPlaylistOps.resolveSmbUri(uri.toString()) }
            .onFailure { Log.w(TAG, "Could not resolve the next network playlist item", it) }
            .getOrNull()
        }
        if (token != smbResolveToken || !activity.isPlaybackLoadRequestCurrent(loadGeneration)) {
          stream?.streamId?.takeIf { it.isNotEmpty() }?.let { streamId ->
            activity.lifecycleScope.launch(Dispatchers.IO) {
              NetworkStreamingProxy.getInstance()?.unregisterStream(streamId)
            }
          }
          return@launch
        }
        if (stream == null) {
          activity.viewModel.showToast(activity.getString(R.string.network_share_unreachable))
          return@launch
        }
        loadResolvedPlaylistItem(
          index = index,
          uri = uri,
          playableUri = stream.url,
          mediaIdentifierOverride = "network_${stream.connectionId}_${stream.filePath.hashCode()}",
          loadGeneration = loadGeneration,
        )
      }
      return
    }

    loadResolvedPlaylistItem(
      index,
      uri,
      uri.resolveUri(activity) ?: uri.toString(),
      loadGeneration = loadGeneration,
    )
  }

  fun switchActiveNetworkStream(playableUri: String?) {
    // Parsing a proxy URL is pure; never start the proxy to inspect a local file's URI.
    val newStreamId = NetworkStreamingProxy.extractStreamIdFromUrl(playableUri)
    val oldStreamId = activeNetworkStreamId
    if (oldStreamId != null && oldStreamId != newStreamId) {
      activity.lifecycleScope.launch(Dispatchers.IO) {
        NetworkStreamingProxy.getInstance()?.unregisterStream(oldStreamId)
      }
    }
    activeNetworkStreamId = newStreamId
  }

  /**
   * Loads a playlist item whose playable URI is already known.
   */
  fun loadResolvedPlaylistItem(
    index: Int,
    uri: Uri,
    playableUri: String,
    mediaIdentifierOverride: String? = null,
    loadGeneration: Long = activity.beginPlaybackLoadRequest(),
  ) {
    if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return
    activity.isReady = false
    // Save current video's playback state before switching
    if (activity.fileName.isNotBlank()) {
      activity.saveVideoPlaybackState(activity.fileName)
    }

    switchActiveNetworkStream(playableUri)

    // Update index in manager
    activity.viewModel.playlistManager.updateIndex(index)

    // Extract and set the new file name
    val customTitle = activity.viewModel.playlistManager.getTitleAt(index)
    activity.fileName = if (!customTitle.isNullOrBlank()) customTitle else getFileNameFromUri(uri)
    // Generate new media identifier for playback state
    activity.mediaIdentifier = mediaIdentifierOverride ?: getMediaIdentifierFromUri(uri, activity.fileName)
    activity.viewModel.setMediaTitle(activity.fileName)
    activity.viewModel.setMediaIdentifier(activity.mediaIdentifier)

    val cachedDurationMs = activity.viewModel.playlistManager.getDurationAt(index)
    val fastDurationMs = if (cachedDurationMs > 0L) cachedDurationMs else getFastDurationMsForUri(uri)
    val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
    val isNetwork = HttpUtils.isNetworkStream(uri)
    activity.viewModel.prepareForFileLoad(fastDurationSec, isNetwork = isNetwork)
    val loadedTitle = activity.fileName

    val targetUri = uri
    activity.lifecycleScope.launch(Dispatchers.IO) {
      val cachedThumb = activity.getCachedThumbnailForUri(targetUri)
      withContext(Dispatchers.Main) {
        if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@withContext
        val activeThumb = cachedThumb ?: MediaPlaybackService.thumbnail ?: activity.miniPlayerStateManager.state.value.thumbnail
        MediaPlaybackService.thumbnail = activeThumb
        activity.mediaPlaybackService?.setMediaInfo(
          title = loadedTitle,
          artist = "",
          thumbnail = activeThumb
        )
        activity.miniPlayerStateManager.updateState(
          title = loadedTitle,
          thumbnail = activeThumb,
          videoPath = targetUri.toString(),
          currentPositionMs = 0L,
          durationMs = fastDurationMs,
          nextThumbnail = null,
          prevThumbnail = null,
        )
      }
    }

    // Set HTTP headers (including referer) for network streams
    setHttpHeadersForUri(uri)

    // Update playlist play history if this is a custom playlist
    activity.viewModel.playlistManager.playlistId?.let { id ->
      activity.lifecycleScope.launch(Dispatchers.IO) {
        val filePath = when (uri.scheme) {
          "file" -> uri.path ?: uri.toString()
          "content" -> {
            activity.contentResolver.query(
              uri,
              arrayOf(MediaStore.MediaColumns.DATA),
              null,
              null,
              null,
            )?.use { cursor ->
              if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex != -1) cursor.getString(columnIndex) else null
              } else null
            } ?: uri.toString()
          }

          else -> uri.toString()
        }

        runCatching {
          activity.playlistRepository.updatePlayHistory(id, filePath)
          Log.d(TAG, "Updated playlist history for: $filePath in playlist $id")
        }.onFailure { e ->
          Log.e(TAG, "Error updating playlist history", e)
        }
      }
    }

    if (activity.mpvInitialized) {
      activity.safeSetPropertyString("vid", "no")
    }
    runCatching { MPVLib.setPropertyBoolean("pause", true) }
    // Prepare the resume position and cached playback state before MPV opens the item.
    activity.lifecycleScope.launch {
      activity.loadMediaWithPlaybackState(playableUri, loadGeneration)
    }

    // Update media title (this will trigger UI update)
    // Don't force media-title for standalone m3u/m3u8 streams - let MPV provide it
    // But if we are playing from an M3U playlist with custom titles, we MUST set it
    val isM3U = isUriM3U(uri)
    val isM3uPlaylist = activity.viewModel.playlistManager.isM3uPlaylist
    val hasCustomTitle = !activity.viewModel.playlistManager.getTitleAt(index).isNullOrBlank()
    if (!isM3U || isM3uPlaylist || hasCustomTitle) {
      activity.safeSetPropertyString("force-media-title", activity.fileName)
      activity.viewModel.setMediaTitle(activity.fileName)
    } else {
      activity.viewModel.setMediaTitle(activity.fileName)
    }

    // Update media session metadata
    activity.lifecycleScope.launch {
      kotlinx.coroutines.delay(100) // Wait for MPV to load the file
      if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@launch
      val mpvDuration = MPVLib.getPropertyDouble("duration")
      val durationMs = if (mpvDuration != null && mpvDuration > 0) {
        (mpvDuration * 1000).toLong()
      } else {
        fastDurationMs
      }
      activity.updateMediaSessionMetadata(
        title = activity.fileName,
        durationMs = durationMs,
      )
      // Refresh playlist items to update the currently playing indicator
      activity.viewModel.refreshPlaylistItems()
    }
  }

  /**
   * Fast synchronous lookup for media duration in milliseconds.
   * Checks MediaStore for content:// and file:// URIs.
   */
  fun getFastDurationMsForUri(uri: Uri): Long {
    return runCatching {
      when (uri.scheme) {
        "content" -> {
          activity.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DURATION),
            null,
            null,
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
              if (idx != -1) cursor.getLong(idx) else 0L
            } else 0L
          } ?: 0L
        }
        "file", null -> {
          val path = uri.path ?: return 0L
          activity.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DURATION),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(path),
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
              if (idx != -1) cursor.getLong(idx) else 0L
            } else 0L
          } ?: 0L
        }
        else -> 0L
      }
    }.getOrDefault(0L)
  }

  /**
   * Get file name from URI (used for playlist items)
   */
  fun getFileNameFromUri(uri: Uri): String {
    activity.getDisplayNameFromUri(uri)?.let { return it }
    return activity.extractFileNameFromUri(uri)
  }

  /**
   * Get the current video title for controls display.
   * Used as a fallback when MPV hasn't set the media-title property yet.
   * For m3u/m3u8 streams, returns the raw media-title from MPV instead of parsing.
   */
  fun getTitleForControls(): String {
    // 1. Check if we have a custom playlist title
    val index = activity.viewModel.playlistManager.currentIndex.value
    if (activity.viewModel.playlistManager.playlist.value.isNotEmpty() && index >= 0 && index < activity.viewModel.playlistManager.playlist.value.size) {
      val customTitle = activity.viewModel.playlistManager.getTitleAt(index)
      if (!customTitle.isNullOrBlank()) {
        return customTitle
      }
    }

    // For m3u/m3u8 streams, use MPV's raw media-title directly
    if (isCurrentStreamM3U()) {
      val rawTitle = MPVLib.getPropertyString("media-title")
      if (!rawTitle.isNullOrBlank()) {
        return rawTitle
      }
    }
    return activity.fileName
  }

  /**
   * Check if the currently playing media is an m3u or m3u8 stream.
   * Checks both the intent URI and the current playlist item if playing from a playlist.
   */
  fun isCurrentStreamM3U(): Boolean {
    // First check the intent URI
    val uri = activity.extractUriFromIntent(activity.intent)
    if (uri != null && isUriM3U(uri)) {
      return true
    }

    // Also check the current playlist item if playing from a playlist
    val playlist = activity.viewModel.playlistManager.playlist.value
    val playlistIndex = activity.viewModel.playlistManager.currentIndex.value
    if (playlist.isNotEmpty() && playlistIndex >= 0 && playlistIndex < playlist.size) {
      return isUriM3U(playlist[playlistIndex])
    }

    return false
  }

  /**
   * Check if a specific URI string is an m3u or m3u8 file/stream.
   */
  fun isUriM3U(uriStr: String): Boolean {
    val lowerUrl = uriStr.lowercase()
    return lowerUrl.contains(".m3u8") || lowerUrl.contains(".m3u") ||
      lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".m3u")
  }

  /**
   * Check if a specific URI is an m3u or m3u8 file/stream.
   */
  fun isUriM3U(uri: Uri): Boolean {
    return isUriM3U(uri.toString())
  }

  /**
   * Intercepts an M3U/M3U8 file or stream, parses its channels/items,
   * populates the playlist, and triggers playback of the first item.
   * Falls back to direct playback if the parse fails (e.g. HLS streams).
   */
  fun loadM3uPlaylistOrPlayDirectly(uriStr: String) {
    val loadGeneration = activity.beginPlaybackLoadRequest()
    val uri = Uri.parse(uriStr)
    activity.lifecycleScope.launch(Dispatchers.IO) {
      val result = if (uri.scheme == "http" || uri.scheme == "https") {
        M3UParser.parseFromUrl(uriStr)
      } else {
        M3UParser.parseFromUri(activity, uri)
      }

      withContext(Dispatchers.Main) {
        if (!activity.isPlaybackLoadRequestCurrent(loadGeneration)) return@withContext
        if (result is M3UParseResult.Success && result.items.isNotEmpty()) {
          val items = result.items
          val playlistUris = items.map { Uri.parse(it.url) }
          val playlistTitles = items.map { it.title ?: it.url }

          activity.viewModel.playlistManager.setPlaylist(
            items = playlistUris,
            index = 0,
            id = null,
            isM3u = true,
            titles = playlistTitles
          )

          // Play the first item
          loadPlaylistItemInternal(0, loadGeneration)

          // Set media title in UI
          val playlistName = result.playlistName
          activity.viewModel.setMediaTitle(playlistName)
          Log.d(TAG, "Loaded M3U playlist '${playlistName}' with ${items.size} items")
        } else {
          // If parsing failed or HLS, play the URI directly in MPV
          Log.d(TAG, "M3U parsing failed or HLS stream. Playing directly: $uriStr")
          if (activity.mpvInitialized) {
            activity.safeSetPropertyString("vid", "no")
          }
          runCatching { MPVLib.setPropertyBoolean("pause", true) }
          activity.lifecycleScope.launch {
            activity.loadMediaWithPlaybackState(uriStr, loadGeneration)
          }
        }
      }
    }
  }

  /**
   * Save recently played for a specific URI
   */
  suspend fun saveRecentlyPlayedForUri(
    uri: Uri,
    name: String,
  ) {
    activity.viewModel.historyManager.recordPlaybackStart(
      uri = uri,
      fileName = name,
      launchSource = "playlist",
      playlistId = activity.viewModel.playlistManager.playlistId
    )
  }

  /**
   * Generate a unique identifier for this media for playback state/history.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network streams via proxy (SMB/WebDAV/FTP), uses the stable network file path from intent extras.
   * For other network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  fun getMediaIdentifier(intent: Intent, fileName: String): String {
    // Check if this is a network file played via proxy (SMB/WebDAV/FTP)
    // Use the stable network file path instead of the temporary proxy URL
    val networkFilePath = intent.getStringExtra("network_file_path")
    val networkConnectionId = intent.getLongExtra("network_connection_id", -1L)

    if (networkFilePath != null && networkConnectionId != -1L) {
      // For network files via proxy: use connection ID + file path for stable identifier
      val identifier = "network_${networkConnectionId}_${networkFilePath.hashCode()}"
      Log.d(
        TAG,
        "Using network file identifier: $identifier (connection: $networkConnectionId, path: $networkFilePath)",
      )
      return identifier
    }

    val uri = activity.extractUriFromIntent(intent)
    return if (uri != null) getMediaIdentifierFromUri(uri, fileName) else fileName
  }

  /**
   * Generate a unique identifier for this media from a URI and name.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  fun getMediaIdentifierFromUri(uri: Uri, fileName: String): String {
    return if (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms" || uri.scheme.equals("smb", ignoreCase = true)) {
      "${fileName}_${uri.toString().hashCode()}"
    } else {
      fileName
    }
  }

  fun generatePlaylistFromMediaLibrary(currentPath: String) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val result = FolderPlaylistOps.generateMediaLibraryPlaylist(activity, currentPath)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = newPlaylist,
              index = newIndex,
              durations = durations
            )
            Log.d(TAG, "Auto-playlist generated from Media Library: ${newPlaylist.size} videos")
            updateMiniPlayerPlaylistState()
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Failed to auto-generate library playlist", e)
      }
    }
  }

  /**
   * Picks the sibling source that matches how this file was launched.
   *
   * SMB is checked first because for those launches [parsePathFromIntent] yields a local proxy URL,
   * which has no folder to scan: our own browser passes the share-relative path plus the connection
   * it came from, and external file managers that proxy SMB themselves (MiXplorer) put the real
   * location in a `real_path` extra. Without either, nothing changes for local/content launches.
   */
  fun autoGeneratePlaylist(intent: Intent) {
    val networkFilePath = intent.getStringExtra("network_file_path")
    val networkConnectionId = intent.getLongExtra("network_connection_id", -1L)
    val smbUri = intent.dataString?.takeIf { it.startsWith("smb://", ignoreCase = true) }
      ?: intent.getStringExtra("real_path")?.takeIf { it.startsWith("smb://", ignoreCase = true) }

    when {
      networkFilePath != null && networkConnectionId != -1L ->
        generatePlaylistFromNetworkFolder {
          FolderPlaylistOps.generateNetworkFolderPlaylist(networkConnectionId, networkFilePath)
        }

      smbUri != null ->
        generatePlaylistFromNetworkFolder { FolderPlaylistOps.generateNetworkFolderPlaylist(smbUri) }

      else -> {
        val path = activity.parsePathFromIntent(intent) ?: return
        if (intent.getStringExtra("launch_source") == "media_library_list") {
          generatePlaylistFromMediaLibrary(path)
        } else {
          generatePlaylistFromFolder(path)
        }
      }
    }
  }

  /**
   * Builds a playlist by listing a remote folder. A share we cannot list means no playlist — the
   * user asked to watch a file, not to build a playlist, so playback is left alone and it is only
   * logged.
   */
  private fun generatePlaylistFromNetworkFolder(generate: suspend () -> Result<Pair<List<Uri>, Int>?>) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
      val (newPlaylist, newIndex) = generate()
        .getOrElse { e ->
          Log.w(TAG, "Could not list the remote folder for an auto-playlist", e)
          return@launch
        } ?: return@launch

      withContext(Dispatchers.Main) {
        activity.viewModel.playlistManager.setPlaylist(items = newPlaylist, index = newIndex)
        Log.d(TAG, "Auto-playlist generated from network folder: ${newPlaylist.size} items")
        updateMiniPlayerPlaylistState()
      }
    }
  }

  fun generatePlaylistFromFolder(currentPath: String) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val launchSource = activity.intent.getStringExtra("launch_source")
        val result = FolderPlaylistOps.generateFolderPlaylist(activity, currentPath, launchSource)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = newPlaylist,
              index = newIndex,
              durations = durations
            )
            Log.d(TAG, "Auto-playlist generated: ${newPlaylist.size} videos")
            updateMiniPlayerPlaylistState()
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Failed to auto-generate playlist", e)
      }
    }
  }

  /**
   * Automatically extracts and sets the Referer header based on the URI origin.
   *
   * @param uri The URI to extract referer from and set headers for
   */
  fun setHttpHeadersForUri(uri: Uri) {
    if (!HttpUtils.isNetworkStream(uri)) return

    val headerMap = mutableMapOf<String, String>()

    // Automatically extract and set referer domain from the URI
    HttpUtils.extractRefererDomain(uri)?.let { referer ->
      headerMap["Referer"] = referer
      Log.d(TAG, "Auto-detected Referer for playlist item: $referer")
    }

    // Set all headers in MPV, or clear them if empty
    if (headerMap.isNotEmpty()) {
      val headersString = headerMap
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
        .joinToString(",")

      activity.safeSetPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers for playlist item: $headersString")
    } else {
      activity.safeSetPropertyString("http-header-fields", "")
      Log.d(TAG, "Cleared HTTP headers for playlist item")
    }
  }

  /**
   * Check if the current playlist is an M3U playlist (sourced from database).
   */
  fun isCurrentPlaylistM3U(): Boolean = activity.viewModel.playlistManager.isM3uPlaylist

  fun getPlaylistWindowOffset(): Int = activity.viewModel.playlistManager.playlistWindowOffset
}
