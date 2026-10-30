package xyz.mpv.rex.ui.player.delegates

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.openContentFd
import xyz.mpv.rex.ui.player.resolveUri
import xyz.mpv.rex.utils.media.FolderPlaylistOps
import xyz.mpv.rex.utils.media.HttpUtils

/**
 * Controller responsible for parsing incoming launch intents, content URIs,
 * subtitle and header extras, initial orientation detection, and activity result dispatch.
 */
class PlayerIntentHandler(
  private val activity: PlayerActivity,
) {
  /**
   * Sets the result intent with current playback position and duration.
   * Called when activity is finishing to return data to caller.
   */
  fun setReturnIntent() {
    Log.d(TAG, "Setting return intent")

    val resultIntent =
      Intent(RESULT_INTENT).apply {
        activity.viewModel.pos?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
        activity.viewModel.duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
      }

    activity.setResult(Activity.RESULT_OK, resultIntent)
  }

  /**
   * Parses the file path from the intent.
   *
   * This method checks the intent action and data to determine the file path.
   * It supports the following actions:
   * - ACTION_VIEW: The file path is contained in the intent data.
   * - ACTION_SEND: The file path is contained in the intent extras.
   *
   * @param intent The intent containing the file URI
   * @return The resolved file path, or null if not found
   */
  fun parsePathFromIntent(intent: Intent): String? =
    when (intent.action) {
      Intent.ACTION_VIEW -> {
        val data = intent.data
        if (data?.scheme.equals("smb", ignoreCase = true)) {
          intent.dataString ?: data.toString()
        } else {
          data?.resolveUri(activity)
        }
      }
      Intent.ACTION_SEND -> parsePathFromSendIntent(intent)
      else -> intent.getStringExtra("uri")
    }

  /**
   * Parses the file path from a SEND intent.
   *
   * This method checks the intent extras for the file path.
   *
   * @param intent The SEND intent
   * @return The resolved file path, or null if not found
   */
  fun parsePathFromSendIntent(intent: Intent): String? =
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      uri?.resolveUri(activity)
    } else {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let { rawText ->
        val urlRegex = Regex("""https?://[^\s]+""")
        val foundUrl = urlRegex.find(rawText)?.value
        if (foundUrl != null) {
          val titleCandidate = rawText.replace(foundUrl, "").trim()
          if (titleCandidate.isNotBlank() && !intent.hasExtra("title")) {
            intent.putExtra("title", titleCandidate)
          }
          foundUrl
        } else {
          val uri = rawText.trim().toUri()
          if (uri.isHierarchical && !uri.isRelative) {
            uri.resolveUri(activity)
          } else {
            null
          }
        }
      }
    }

  /**
   * Extracts and resolves the file name from the intent.
   *
   * @param intent The intent containing the file URI
   * @return The display name of the file, or empty string if not found
   */
  fun getFileName(intent: Intent): String {
    // First check if a custom title/filename was provided via intent extras
    intent.getStringExtra("title")?.let { return it }
    intent.getStringExtra("filename")?.let { return it }

    val uri = extractUriFromIntent(intent) ?: return ""

    // Try content resolver first for content:// URIs
    getDisplayNameFromUri(uri)?.let { return it }

    // Extract filename from URL/URI
    return extractFileNameFromUri(uri)
  }

  /**
   * Extracts filename from URI, handling URL encoding and network URLs properly.
   * For network streams, returns a temporary name that will be updated async via HTTP headers.
   *
   * @param uri The URI to extract filename from
   * @return The extracted filename
   */
  fun extractFileNameFromUri(uri: Uri): String {
    // For HTTP/HTTPS URLs, extract from path (will be updated async via HTTP headers)
    if (HttpUtils.isNetworkStream(uri)) {
      // Get the last path segment and decode URL encoding
      val path = uri.path ?: return uri.host ?: "Network Stream"
      val lastSegment = path.substringAfterLast("/")

      if (lastSegment.isNotBlank()) {
        // Decode URL encoding (e.g., %20 -> space)
        return try {
          java.net.URLDecoder.decode(lastSegment, "UTF-8")
            .substringBefore("?") // Remove query parameters
            .substringBefore("#") // Remove fragments (only for network streams)
            .takeIf { it.isNotBlank() } ?: uri.host ?: "Network Stream"
        } catch (e: Exception) {
          lastSegment
            .substringBefore("?")
            .substringBefore("#")
        }
      }

      // If no filename in path, use hostname
      return uri.host ?: "Network Stream"
    }

    // For file:// and content:// URIs - preserve # characters as they're part of the filename
    val lastSegment = uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: "Unknown Video"

    // For local files, only decode URL encoding but preserve # characters
    return try {
      java.net.URLDecoder.decode(lastSegment, "UTF-8")
    } catch (e: Exception) {
      lastSegment
    }
  }

  /**
   * Extracts the URI from the intent based on intent type.
   *
   * @param intent The intent to extract URI from
   * @return The extracted URI, or null if not found
   */
  fun extractUriFromIntent(intent: Intent): Uri? =
    if (intent.type == "text/plain" || intent.action == Intent.ACTION_SEND) {
      val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (!rawText.isNullOrBlank()) {
        val urlRegex = Regex("""https?://[^\s]+""")
        val foundUrl = urlRegex.find(rawText)?.value
        if (foundUrl != null) {
          foundUrl.toUri()
        } else {
          rawText.trim().toUri()
        }
      } else {
        intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
      }
    } else {
      intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
      }
    }

  /**
   * Queries the content resolver to get the display name for a URI.
   *
   * @param uri The URI to query
   * @return The display name, or null if not found
   */
  fun getDisplayNameFromUri(uri: Uri): String? =
    runCatching {
      activity.contentResolver
        .query(
          uri,
          arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
          null,
          null,
          null,
        )?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.onFailure { e ->
      Log.e(TAG, "Error getting display name from URI", e)
    }.getOrNull()

  /**
   * Converts the intent URI to a playable URI string for MPV.
   *
   * @param intent The intent containing the file URI
   * @return A playable URI string, or null if unable to resolve
   */
  fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent)
      ?: (if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
        FolderPlaylistOps.extractExternalPlaylist(intent)?.let { playlist ->
          playlist.items.getOrNull(playlist.initialIndex)?.let { firstUri ->
            if (firstUri.scheme.equals("smb", ignoreCase = true)) {
              firstUri.toString()
            } else {
              firstUri.resolveUri(activity) ?: firstUri.toString()
            }
          }
        }
      } else null)
      ?: return null

    return when {
      uri.startsWith("content://") -> uri.toUri().openContentFd(activity)
      uri.startsWith("smb://", ignoreCase = true) -> {
        runBlocking(Dispatchers.IO) {
          FolderPlaylistOps.resolveSmbUri(uri)?.url
        } ?: uri
      }
      else -> uri
    }
  }

  /**
   * Loads subtitles specified in intent extras.
   *
   * @param extras Bundle containing subtitle URIs
   */
  fun addSubtitlesFromExtras(extras: Bundle) {
    if (!extras.containsKey("subs")) return

    val subList = Utils.getParcelableArray<Uri>(extras, "subs")
    val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

    activity.lifecycleScope.launch(Dispatchers.Default) {
      val preferredEncoding = activity.subtitlesPreferences.subtitleEncoding.get()
      for (suburi in subList) {
        val (normalizedPath, _) = xyz.mpv.rex.utils.media.SubtitleEncodingUtils.normalizeSubtitleUri(
          activity,
          suburi,
          preferredEncoding
        )
        val subfile = if (normalizedPath.startsWith("content://") || normalizedPath.startsWith("file://")) {
          suburi.resolveUri(activity) ?: continue
        } else {
          normalizedPath
        }
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }
  }

  /**
   * Sets HTTP headers from intent extras for network playback.
   */
  fun setHttpHeadersFromExtras(extras: Bundle?) {
    val headerMap = mutableMapOf<String, String>()

    val uri = extractUriFromIntent(activity.intent)
    if (uri != null && HttpUtils.isNetworkStream(uri)) {
      HttpUtils.extractRefererDomain(uri)?.let { referer ->
        headerMap["Referer"] = referer
        Log.d(TAG, "Auto-detected Referer: $referer")
      }
    }

    extras?.getStringArray("headers")?.let { headers ->
      if (headers.size < 2) return@let

      if (headers[0]?.startsWith("User-Agent", ignoreCase = true) == true) {
        headers[1]?.let { ua ->
          activity.safeSetPropertyString("user-agent", ua)
        }
      }

      headers.asSequence()
        .chunked(2)
        .filter { it.size == 2 && !it[0].isNullOrBlank() && !it[1].isNullOrBlank() }
        .forEach { (key, value) ->
          headerMap[key!!] = value!!
        }
    }

    if (headerMap.isNotEmpty()) {
      val headersString = headerMap
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
        .joinToString(",")

      activity.safeSetPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers: $headersString")
    } else {
      activity.safeSetPropertyString("http-header-fields", "")
      Log.d(TAG, "Cleared HTTP headers")
    }
  }

  /**
   * Sets initial orientation synchronously from intent parameters or metadata cache
   * to avoid orientation jumps on activity launch or intent update.
   */
  fun applyInitialOrientationFromIntent(targetIntent: Intent) {
    val orient = activity.playerPreferences.orientation.get()
    if (orient != PlayerOrientation.Video && orient != PlayerOrientation.Smart) {
      activity.setOrientation()
      return
    }

    // 1. Try saved orientation from intent extras (for Smart mode)
    val intentSavedOrientation = targetIntent.getIntExtra("saved_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    if (orient == PlayerOrientation.Smart && intentSavedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      activity.requestedOrientation = intentSavedOrientation
      activity.isOrientationRestored = true
      Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation ${activity.requestedOrientation} from intent")
      return
    }

    // 2. Try dimensions from intent extras (for Video/Smart mode)
    val intentWidth = targetIntent.getIntExtra("width", -1)
    val intentHeight = targetIntent.getIntExtra("height", -1)
    val intentRotation = targetIntent.getIntExtra("rotation", 0)
    if (intentWidth > 0 && intentHeight > 0) {
      activity.setOrientation(intentWidth, intentHeight, intentRotation)
      return
    }

    // 3. Fallback: try saved orientation from DB or metadata cache asynchronously
    val targetFileName = getFileName(targetIntent).ifBlank { targetIntent.data?.lastPathSegment ?: "Unknown Video" }
    activity.lifecycleScope.launch(Dispatchers.IO) {
      if (orient == PlayerOrientation.Smart) {
        val state = activity.playbackStateRepository.getVideoDataByTitle(targetFileName)
        if (state?.savedOrientation != null && state.savedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          withContext(Dispatchers.Main) {
            activity.requestedOrientation = state.savedOrientation!!
            activity.isOrientationRestored = true
            Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation ${activity.requestedOrientation} from DB")
          }
          return@launch
        }
      }

      val path = parsePathFromIntent(targetIntent)
      if (path != null) {
        val file = File(path)
        if (file.exists() && !xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(file)) {
          val metadata = activity.metadataCache.getOrExtractMetadata(file, targetIntent.data ?: "".toUri(), targetFileName)
          if (metadata != null && metadata.width > 0 && metadata.height > 0) {
            withContext(Dispatchers.Main) {
              activity.setOrientation(metadata.width, metadata.height, metadata.rotation)
            }
          }
        }
      }
    }
  }

  /**
   * Handles new intents to load a different file without recreating the activity.
   */
  fun handleNewIntent(intent: Intent) {
    activity.isAutoplayNextTriggered = false
    activity.pendingIntentExtras = true
    activity.setIntent(intent)

    val hasIntentMedia = intent.data != null || intent.hasExtra("video_list") || parsePathFromIntent(intent) != null
    val incomingFileName = getFileName(intent).ifBlank { intent.data?.lastPathSegment ?: "" }
    val incomingMediaIdentifier = if (incomingFileName.isNotBlank()) activity.getMediaIdentifier(intent, incomingFileName) else ""

    val isSameMedia = activity.isReady && hasIntentMedia &&
      ((incomingMediaIdentifier.isNotBlank() && incomingMediaIdentifier == activity.mediaIdentifier) ||
       (incomingFileName.isNotBlank() && incomingFileName == activity.fileName))

    // If expanding active session without intent media, or if the exact same media is already active in foreground
    if (activity.isReady && (!hasIntentMedia || (isSameMedia && !activity.isInBackgroundPlayback && !activity.isManualBackgroundPlayback))) {
      // PRELOAD-RESUME-STATE-V1: this intent did not start a new file load.
      activity.pendingIntentExtras = false
      Log.d(TAG, "onNewIntent: current media already playing or expanding active session, restoring player without reload")
      activity.enableVideoAfterBackground()
      @Suppress("DEPRECATION")
      activity.overridePendingTransition(android.R.anim.fade_in, 0)
      return
    }
    val externalPlaylist = if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
      FolderPlaylistOps.extractExternalPlaylist(intent)
    } else null
    // Check if this intent has playlist information
    val hasPlaylistExtras = intent.hasExtra("playlist_id") ||
      intent.hasExtra("playlist") ||
      externalPlaylist != null

    // Clean up background playback state when loading a different video
    if (activity.isManualBackgroundPlayback || activity.isInBackgroundPlayback) {
      activity.isManualBackgroundPlayback = false
      activity.endBackgroundPlayback()
      activity.enableVideoAfterBackground()
      activity.miniPlayerStateManager.clearState()
    }

    // Clear stale playlist from previous video so auto-generate runs fresh for the new file
    activity.viewModel.playlistManager.setPlaylist(items = emptyList(), index = 0)

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }

    // Only update playlist state if we have new playlist information
    if (externalPlaylist != null) {
      activity.viewModel.playlistManager.setPlaylist(
        items = externalPlaylist.items,
        index = externalPlaylist.initialIndex,
        titles = externalPlaylist.titles
      )
      Log.d(TAG, "onNewIntent: Loaded external playlist: ${externalPlaylist.items.size} items at index ${externalPlaylist.initialIndex}")
      activity.updateMiniPlayerPlaylistState()
    } else if (hasPlaylistExtras || playlistFromIntent.isNotEmpty()) {
      val newPlaylistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
      val newPlaylistIndex = intent.getIntExtra("playlist_index", 0)
      val titlesFromIntent = intent.getStringArrayListExtra("playlist_titles") ?: emptyList()

      activity.viewModel.playlistManager.setPlaylist(
        items = playlistFromIntent,
        index = newPlaylistIndex,
        id = newPlaylistId,
        titles = titlesFromIntent
      )
    }

    // If playlist is empty but playlist_id is provided, load from database
    if (activity.viewModel.playlistManager.playlist.value.isEmpty() && activity.viewModel.playlistManager.playlistId != null) {
      activity.lifecycleScope.launch(Dispatchers.IO) {
        val pid = activity.viewModel.playlistManager.playlistId ?: return@launch
        try {
          val playlistItems = activity.playlistRepository.getPlaylistItems(pid)
          val items = playlistItems.map { item ->
            if (item.filePath.startsWith("/") || item.filePath.startsWith("file://")) {
              val path = if (item.filePath.startsWith("file://")) item.filePath.removePrefix("file://") else item.filePath
              Uri.fromFile(File(path))
            } else {
              Uri.parse(item.filePath)
            }
          }
          val titles = playlistItems.map { it.fileName }
          val totalCount = items.size
          withContext(Dispatchers.Main) {
            activity.viewModel.playlistManager.setPlaylist(
              items = items,
              index = activity.viewModel.playlistManager.currentIndex.value,
              id = pid,
              totalCount = totalCount,
              titles = titles
            )
            Log.d(TAG, "onNewIntent: Loaded ${items.size} items from playlist $pid")
            activity.updateMiniPlayerPlaylistState()
          }
        } catch (e: Exception) {
          Log.e(TAG, "onNewIntent: Failed to load playlist from database", e)
        }
      }
    }

    // Auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (activity.viewModel.playlistManager.playlist.value.isEmpty() && activity.viewModel.playlistManager.playlistId == null && activity.playerPreferences.playlistMode.get()) {
      activity.autoGeneratePlaylist(intent)
    }

    // Extract the new fileName before loading the file
    var fileName = if (externalPlaylist != null && !externalPlaylist.titles.getOrNull(externalPlaylist.initialIndex).isNullOrBlank()) {
      externalPlaylist.titles[externalPlaylist.initialIndex]
    } else {
      getFileName(intent)
    }
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    activity.fileName = fileName
    activity.mediaIdentifier = activity.getMediaIdentifier(intent, fileName)
    activity.viewModel.setMediaTitle(fileName)
    activity.viewModel.setMediaIdentifier(activity.mediaIdentifier)
    activity.jellyfinExternalInfo = xyz.mpv.rex.jellyfin.JellyfinExternalHelper.detect(intent)

    // Synchronously set orientation for the new file before displaying activity
    applyInitialOrientationFromIntent(intent)

    // Set HTTP headers (including referer) BEFORE loading the new file
    setHttpHeadersFromExtras(intent.extras)

    // Load the new file
    getPlayableUri(intent)?.let { uriStr ->
      activity.switchActiveNetworkStream(uriStr)

      val parsedUri = runCatching { Uri.parse(uriStr) }.getOrNull()
      val fastDurationMs = if (parsedUri != null) activity.getFastDurationMsForUri(parsedUri) else 0L
      val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
      val isNetwork = parsedUri != null && HttpUtils.isNetworkStream(parsedUri)
      activity.viewModel.prepareForFileLoad(fastDurationSec, isNetwork = isNetwork)

      if (parsedUri != null && activity.isUriM3U(parsedUri)) {
        activity.loadM3uPlaylistOrPlayDirectly(uriStr)
      } else {
        activity.loadMediaOrResolveWebStream(uriStr)
      }
    }
  }

  companion object {
    private const val RESULT_INTENT = "xyz.mpv.rex.ui.player.PlayerActivity.result"
    private const val MILLISECONDS_TO_SECONDS = 1000
  }
}
