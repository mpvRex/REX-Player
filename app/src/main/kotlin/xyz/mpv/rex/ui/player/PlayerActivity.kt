package xyz.mpv.rex.ui.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import xyz.mpv.rex.ui.player.ambient.AmbientGlowOverlay
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.runBlocking
import xyz.mpv.rex.MainActivity
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.databinding.PlayerLayoutBinding
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.domain.hdr.HdrToysManager
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.ui.player.controls.PlayerControls
import xyz.mpv.rex.ui.player.delegates.PlayerAudioController
import xyz.mpv.rex.ui.player.delegates.PlayerBackgroundPlaybackController
import xyz.mpv.rex.ui.player.delegates.PlayerIntentHandler
import xyz.mpv.rex.ui.player.delegates.PlayerKeyEventHandler
import xyz.mpv.rex.ui.player.delegates.PlayerMediaSessionController
import xyz.mpv.rex.ui.player.delegates.PlayerOrientationController
import xyz.mpv.rex.ui.player.delegates.PlayerPipController
import xyz.mpv.rex.ui.player.delegates.PlayerPlaybackStateController
import xyz.mpv.rex.ui.player.delegates.PlayerPlaylistLoader
import xyz.mpv.rex.ui.player.delegates.PlayerSystemUiController
import xyz.mpv.rex.ui.player.observers.MpvEventDispatcher
import xyz.mpv.rex.ui.theme.MpvexPlayerTheme
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.HttpUtils
import xyz.mpv.rex.utils.media.PlaybackHttpHeaders
import xyz.mpv.rex.utils.media.SubtitleOps
import xyz.mpv.rex.utils.media.M3UParser
import xyz.mpv.rex.utils.media.FolderPlaylistOps
import xyz.mpv.rex.utils.media.M3UParseResult
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.domain.thumbnail.isMostlySolidThumbnail
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.ytdl.model.ResolvedStream
import xyz.mpv.rex.domain.ytdl.model.VideoQuality
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.ui.player.SingleActionGesture
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.util.Locale

/**
 * Main player activity that handles video playback using the MPV library.
 *
 * This activity manages:
 * - Video playback using MPV library
 * - System UI visibility (immersive mode)
 * - Audio focus management
 * - Picture-in-Picture (PiP) mode
 * - Background playback service
 * - MediaSession for external controls (Android Auto, Bluetooth, etc.)
 * - Playback state persistence and restoration
 * - Subtitle and audio track management
 * - Hardware key event handling
 *
 * @see PlayerViewModel for UI state management
 * @see MediaPlaybackService for background playback functionality
 */
@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity :
  AppCompatActivity(),
  PlayerHost,
  MediaPlaybackService.ServiceListener {
  // ==================== ViewModels and Bindings ====================

  /**
   * View model for managing player UI state.
   */
  internal val viewModel: PlayerViewModel by viewModels<PlayerViewModel> {
    PlayerViewModelProviderFactory(this)
  }

  /**
   * Binding for the player layout.
   */
  internal val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }

  /**
   * Observer for MPV events.
   */
  private val playerObserver by lazy { PlayerObserver(this) }

  /**
   * Delegate for hardware and media key event handling.
   */
  private val keyEventHandler by lazy {
    PlayerKeyEventHandler(
      viewModel = viewModel,
      playerPreferences = playerPreferences,
      player = player,
      onFinishTask = { finishAndRemoveTask() },
    )
  }

  /**
   * Delegate for managing screen orientation.
   */
  private val orientationController by lazy {
    PlayerOrientationController(
      activity = this,
      playerPreferences = playerPreferences,
      player = player,
    )
  }

  /**
   * Delegate for managing system UI visibility, immersive mode, and window flags.
   */
  internal val systemUiController by lazy {
    PlayerSystemUiController(
      activity = this,
      window = window,
      playerPreferences = playerPreferences,
      rootViewProvider = { binding.root },
      onUpdatePipParams = { pipHelper.updatePictureInPictureParams() },
    )
  }

  /**
   * Delegate for managing audio focus and becoming-noisy events.
   */
  private val audioController by lazy {
    PlayerAudioController(
      context = this,
      audioManager = audioManager,
      onPausePlayback = { viewModel.pause() },
      onUnpausePlayback = { viewModel.unpause() },
      isPlayerPaused = { viewModel.paused ?: false },
      onDuckVolume = { factor -> MPVLib.command("multiply", "volume", factor.toString()) },
      onClearKeepScreenOn = { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) },
    )
  }

  /**
   * Delegate for managing MediaSession integration with system media controls.
   */
  private val mediaSessionController by lazy {
    PlayerMediaSessionController(
      context = this,
      viewModel = viewModel,
      playerPreferences = playerPreferences,
    )
  }

  /**
   * Delegate for managing background playback service binding and lifecycle.
   */
  private val backgroundPlaybackController by lazy {
    PlayerBackgroundPlaybackController(
      activity = this,
      serviceListener = this,
    )
  }

  /**
   * Delegate for managing video playback state persistence and resume position.
   */
  internal val playbackStateController by lazy {
    PlayerPlaybackStateController(this)
  }

  /**
   * Delegate for managing playlist loading, folder generation, and playlist navigation.
   */
  internal val playlistLoader by lazy {
    PlayerPlaylistLoader(this)
  }

  /**
   * Delegate for managing Picture-in-Picture mode and overlay transitions.
   */
  internal val pipController by lazy {
    PlayerPipController(this)
  }

  /**
   * Delegate for managing intent parsing, content URIs, and activity results.
   */
  internal val intentHandler by lazy {
    PlayerIntentHandler(this)
  }

  /**
   * Dispatcher for MPV property changes and core playback events.
   */
  private val mpvEventDispatcher by lazy {
    MpvEventDispatcher(object : MpvEventDispatcher.EventListener {
      override fun onVideoDimensionChanged(property: String, value: Long) {
        if (!mpvInitialized || player.isExiting || isFinishing) return
        val aspect = player.getVideoOutAspect()
        Log.d(TAG, "Video dimension changed: $property, aspect: $aspect")
        pipHelper.updatePictureInPictureParams()
        val currentOrientation = playerPreferences.orientation.get()
        if ((currentOrientation == PlayerOrientation.Video || currentOrientation == PlayerOrientation.Smart) &&
            aspect != null &&
            !orientationController.isUserOverridden) {
          setOrientation()
        }
        player.applyAnime4KShaders()

        if (property == "video-params/h" && value > 0) {
          val qualities = viewModel.availableVideoQualities.value
          if (qualities.isNotEmpty()) {
            val current = viewModel.currentVideoQuality.value
            if (current == null || current.height != value.toInt()) {
              val matched = qualities.firstOrNull { !it.isAudioOnly && it.height == value.toInt() }
              if (matched != null) {
                Log.d(TAG, "Matched current playing quality from video-params/h ($value) to ${matched.label}")
                viewModel.currentVideoQuality.value = matched
              }
            }
          }
        }
      }

      override fun onVideoAspectChanged(aspect: Double) {
        if (!mpvInitialized || player.isExiting || isFinishing) return
        val outAspect = player.getVideoOutAspect()
        Log.d(TAG, "video-params/aspect changed: $outAspect")
        pipHelper.updatePictureInPictureParams()
        val aspectOverride = MPVLib.getPropertyDouble("video-aspect-override") ?: -1.0
        val currentOrientation = playerPreferences.orientation.get()
        if ((currentOrientation == PlayerOrientation.Video || currentOrientation == PlayerOrientation.Smart) && 
            outAspect != null && 
            aspectOverride <= 0.0 &&
            viewModel.videoAspect.value == VideoAspect.Fit &&
            viewModel.currentAspectRatio.value <= 0.0 &&
            !orientationController.isUserOverridden) {
          setOrientation()
        }
      }

      override fun onPauseStateChanged(isPaused: Boolean) {
        handlePauseStateChange(isPaused)
        if (!isPaused && !isReady && !viewModel.isLoadingFile.value && !isAutoAdvancing) {
          isReady = true
        }
      }

      override fun onEofReached(isEof: Boolean) {
        if (isEof && (!isReady || isAutoAdvancing || viewModel.isLoadingFile.value)) {
          Log.w(
            TAG,
            "onEofReached: ignoring EOF because player is not ready or file is loading (isReady=$isReady, isAutoAdvancing=$isAutoAdvancing, isLoadingFile=${viewModel.isLoadingFile.value})"
          )
          return
        }
        handleEndOfFile(isEof)
      }

      override fun onLuaInvocation(property: String, value: String) {
        viewModel.handleLuaInvocation(property, value)
      }

      override fun onStartFile() {
        isReady = false
        webSubtitlesJob?.cancel()
        webSubtitlesJob = null
        if (!isQualitySwitching) {
          val currentPath = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
          val isNetwork = currentPath?.let { path ->
            HttpUtils.isNetworkStream(runCatching { Uri.parse(path) }.getOrNull())
          } ?: false
          viewModel.onFileStartLoading(isNetwork = isNetwork)
        } else {
          viewModel.onQualitySwitchLoading()
        }
      }

      override fun onFileLoaded() {
        Log.d(TAG, "onFileLoaded called")
        lastFileLoadedTimeMs = SystemClock.elapsedRealtime()
        isAutoAdvancing = false
        handleFileLoaded()
        isReady = true

        val subs = pendingWebSubtitles
        pendingWebSubtitles = null
        if (!subs.isNullOrEmpty()) {
          loadWebSubtitlesAsync(subs)
        }

        val extraAudio = pendingRemoteAudio
        pendingRemoteAudio = null
        if (!extraAudio.isNullOrEmpty()) {
          android.util.Log.i("YtdlIpc", "onFileLoaded drain: extraAudio=${extraAudio.size}")
          extraAudio.forEach { track ->
            viewModel.addRemoteAudio(track.url, track.label, select = false, lang = track.lang)
          }
        }
      }

      override fun onPlaybackRestart() {
        if (isFinishing || isUserFinishing || player.isExiting || MPVLifecycleLock.isTearingDown.value) return
        player.isExiting = false
        if (!isReady && !viewModel.isLoadingFile.value && !isAutoAdvancing) {
          isReady = true
        }
        viewModel.playbackManager.onPlaybackRestart()
        if (needsAspectReapply) {
          needsAspectReapply = false
          runOnUiThread {
            if (isPlaybackStateLoaded) {
              viewModel.reapplyCurrentVisualPreferences()
            } else {
              viewModel.resetVisualPreferences()
            }
          }
        }
      }
    })
  }

  // ==================== Dependency Injection ====================

  /**
   * Repository for managing playback state.
   */
  internal val playbackStateRepository: PlaybackStateRepository by inject()

  /**
   * Repository for managing playlists.
   */
  internal val playlistRepository: xyz.mpv.rex.database.repository.PlaylistRepository by inject()

  /**
   * Preferences for player settings.
   */
  internal val playerPreferences: PlayerPreferences by inject()

  /**
   * Preferences for gesture settings.
   */
  private val gesturePreferences: GesturePreferences by inject()

  /**
   * Preferences for audio settings.
   */
  private val audioPreferences: AudioPreferences by inject()

  /**
   * Preferences for subtitle settings.
   */
  internal val subtitlesPreferences: SubtitlesPreferences by inject()

  /**
   * Preferences for advanced settings.
   */
  private val advancedPreferences: AdvancedPreferences by inject()

  /**
   * Preferences for browser settings.
   */
  internal val browserPreferences: BrowserPreferences by inject()

  /**
   * Preferences for appearance settings.
   */
  private val appearancePreferences: AppearancePreferences by inject()

  /**
   * Manager for file operations.
   */
  private val fileManager: FileManager by inject()

  /**
   * Repository for video metadata cache.
   */
  internal val metadataCache: VideoMetadataCacheRepository by inject()

  /**
   * Preferences for decoder settings (hardware dec, gpu-next, shaders).
   */
  private val decoderPreferences: DecoderPreferences by inject()

  /**
   * Manager for hdr-toys shaders.
   */
  private val hdrToysManager: HdrToysManager by inject()
  internal val miniPlayerStateManager: MiniPlayerStateManager by inject()
  private val headlessPlaybackController: HeadlessPlaybackController by inject()
  private val thumbnailRepository: ThumbnailRepository by inject()
  private val remoteClient: xyz.mpv.rex.jellyfin.remote.JellyfinRemoteClient by inject()
  internal val ytDlClient: xyz.mpv.rex.domain.ytdl.YtDlClient by inject()
  private val ytdlPreferences: xyz.mpv.rex.preferences.YtdlPreferences by inject()
  internal var jellyfinExternalInfo: xyz.mpv.rex.jellyfin.JellyfinExternalHelper.ExternalInfo? = null
  private val uriThumbnailCache = android.util.LruCache<String, android.graphics.Bitmap>(32)

  /**
   * Track selector for automatic audio/subtitle selection
   */
  private val trackSelector: TrackSelector by lazy {
    TrackSelector(audioPreferences, subtitlesPreferences)
  }

  // ==================== Views ====================

  /**
   * The MPV player view.
   */
  val player by lazy { binding.player }

  // ==================== State Management ====================

  /**
   * Current video file name being played.
   */
  internal var fileName = ""

  /**
   * Unique identifier for the current media, used for saving/loading playback state.
   * For network streams, this includes a hash of the URI to ensure uniqueness.
   */
  internal var mediaIdentifier = ""

  internal var activeNetworkStreamId: String?
    get() = playlistLoader.activeNetworkStreamId
    set(value) {
      playlistLoader.activeNetworkStreamId = value
    }

  /**
   * Helper for managing Picture-in-Picture mode.
   */
  internal val pipHelper: MPVPipHelper
    get() = pipController.pipHelper

  internal var isReady = false // Single flag: true when video loaded and ready
  private var isAutoAdvancing = false
  private var lastFileLoadedTimeMs = 0L
  internal var startedAtSavedPosition = false
  private var pendingWebSubtitles: Map<String, String>? = null
  private var pendingRemoteAudio: List<xyz.mpv.rex.domain.ytdl.model.AudioTrack>? = null
  private var currentActiveWebSubtitles: Map<String, String>? = null
  private var currentResolvedStream: ResolvedStream? = null
  private var isQualitySwitching = false
  private var webSubtitlesJob: kotlinx.coroutines.Job? = null
  internal var isOrientationRestored: Boolean
    get() = orientationController.isOrientationRestored
    set(value) {
      orientationController.isOrientationRestored = value
    }
  private var isUserFinishing = false
  internal var wasInPipMode: Boolean
    get() = pipController.wasInPipMode
    set(value) {
      pipController.wasInPipMode = value
    }
  internal var isManualBackgroundPlayback: Boolean
    get() = backgroundPlaybackController.isManualBackgroundPlayback
    set(value) {
      backgroundPlaybackController.isManualBackgroundPlayback = value
    }
  internal var mpvInitialized = false // Track MPV initialization state
  private var savePlaybackStateJob: kotlinx.coroutines.Job?
    get() = playbackStateController.savePlaybackStateJob
    set(value) {
      playbackStateController.savePlaybackStateJob = value
    }
  internal var pendingIntentExtras = false // Track if intent extras should be applied to next loaded file
  private var lastVid: Int
    get() = backgroundPlaybackController.lastVid
    set(value) {
      backgroundPlaybackController.lastVid = value
    }
  internal var isInBackgroundPlayback: Boolean
    get() = backgroundPlaybackController.isInBackgroundPlayback
    set(value) {
      backgroundPlaybackController.isInBackgroundPlayback = value
    }
  private var inheritedNativeSession = false // MPV ownership came from HeadlessPlaybackController

  @Volatile private var needsAspectReapply = false // Track if aspect ratio needs to be reapplied after video is ready (for Video/Smart orientation modes)
  var isPlaybackStateLoaded: Boolean
    get() = playbackStateController.isPlaybackStateLoaded
    set(value) {
      playbackStateController.isPlaybackStateLoaded = value
    }
  internal var isAutoplayNextTriggered: Boolean
    get() = playlistLoader.isAutoplayNextTriggered
    set(value) {
      playlistLoader.isAutoplayNextTriggered = value
    }

  // ==================== Background Playback ====================

  /**
   * Reference to the background playback service.
   */
  internal var mediaPlaybackService: MediaPlaybackService?
    get() = backgroundPlaybackController.mediaPlaybackService
    set(value) {
      backgroundPlaybackController.mediaPlaybackService = value
    }

  /**
   * Tracks whether we're currently bound to the background playback service.
   */
  private var serviceBound: Boolean
    get() = backgroundPlaybackController.serviceBound
    set(value) {
      backgroundPlaybackController.serviceBound = value
    }

  private val serviceConnection: android.content.ServiceConnection
    get() = backgroundPlaybackController.serviceConnection



  @RequiresApi(Build.VERSION_CODES.P)
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    activeInstance = this
    // Smooth fade-in transition when opening the player from list
    @Suppress("DEPRECATION")
    overridePendingTransition(android.R.anim.fade_in, 0)
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    pendingIntentExtras = true
    // The headless controller may retain MPV idle after its mini player is closed. Always take
    // ownership before initializing so a normal video launch cannot create the global singleton
    // a second time.
    val attachExistingSession = headlessPlaybackController.ownsNativeSession
    if (attachExistingSession) {
      setupMPVForHandoff()
    } else {
      setupMPV()
    }
    viewModel.onMpvCoreInitialized()
    MediaPlaybackService.createNotificationChannel(this)
    setupAudio()
    setupBackPressHandler()
    setupPlayerControls()
    setupPipHelper()
    setupMediaSession()

    miniPlayerStateManager.onNextHandler = { playNext() }
    miniPlayerStateManager.onPreviousHandler = { playPrevious() }

    lifecycleScope.launch {
      miniPlayerStateManager.state.collect { miniState ->
        if (!miniState.isPlaybackActive && isManualBackgroundPlayback) {
          Log.d(TAG, "Playback stopped via MiniPlayer - finishing background PlayerActivity")
          isManualBackgroundPlayback = false
          finish()
        }
      }
    }

    lifecycleScope.launch {
      viewModel.repeatMode.collect {
        mediaPlaybackService?.updateMediaSession()
      }
    }

    lifecycleScope.launch {
      viewModel.shuffleEnabled.collect {
        mediaPlaybackService?.updateMediaSession()
      }
    }

    val externalPlaylist = if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
      FolderPlaylistOps.extractExternalPlaylist(intent)
    } else null

    val playlistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
    val playlistIndex = intent.getIntExtra("playlist_index", 0)

    // Load playlist from intent extras first (fast path - backward compatibility)
    val playlistFromIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }

    when {
      externalPlaylist != null -> {
        viewModel.playlistManager.setPlaylist(
          items = externalPlaylist.items,
          index = externalPlaylist.initialIndex,
          titles = externalPlaylist.titles
        )
        Log.d(TAG, "Loaded external playlist: ${externalPlaylist.items.size} items at index ${externalPlaylist.initialIndex}")
        updateMiniPlayerPlaylistState()
      }
      playlistFromIntent.isNotEmpty() || playlistId != null -> {
        val titlesFromIntent = intent.getStringArrayListExtra("playlist_titles") ?: emptyList()
        viewModel.playlistManager.setPlaylist(
          items = playlistFromIntent,
          index = playlistIndex,
          id = playlistId,
          titles = titlesFromIntent
        )
        updateMiniPlayerPlaylistState()
      }
    }

    // If playlist is empty but playlist_id is provided, load asynchronously from database
    // Load all items - LazyColumn handles pagination/virtualization efficiently
    if (viewModel.playlistManager.playlist.value.isEmpty() && playlistId != null) {
      lifecycleScope.launch(Dispatchers.IO) {
        val pid = playlistId
        try {
          // Check if this is an M3U playlist
          val playlistEntity = playlistRepository.getPlaylistById(pid)
          val isM3u = playlistEntity?.isM3uPlaylist ?: false

          // Load all items - LazyColumn will handle virtualization/pagination efficiently
          val playlistItems = playlistRepository.getPlaylistItems(pid)
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
            viewModel.playlistManager.setPlaylist(
              items = items,
              index = playlistIndex,
              id = pid,
              totalCount = totalCount,
              isM3u = isM3u,
              titles = titles
            )
            Log.d(TAG, "Loaded all $totalCount items from playlist $pid (isM3U: $isM3u)")
            updateMiniPlayerPlaylistState()
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load playlist from database", e)
        }
      }
    }

    // Only auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (viewModel.playlistManager.playlist.value.isEmpty() && playlistId == null && playerPreferences.playlistMode.get()) {
      autoGeneratePlaylist(intent)
    }

    // Extract fileName early so it's available when video loads
    fileName = if (externalPlaylist != null && !externalPlaylist.titles.getOrNull(externalPlaylist.initialIndex).isNullOrBlank()) {
      externalPlaylist.titles[externalPlaylist.initialIndex]
    } else {
      getFileName(intent)
    }
    if (fileName.isBlank()) {
      val mpvTitle = runCatching { MPVLib.getPropertyString("media-title") }.getOrNull()
      fileName = if (!mpvTitle.isNullOrBlank()) mpvTitle else (intent.data?.lastPathSegment ?: "Unknown Video")
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)
    viewModel.setMediaTitle(fileName)
    viewModel.setMediaIdentifier(mediaIdentifier)

    jellyfinExternalInfo = xyz.mpv.rex.jellyfin.JellyfinExternalHelper.detect(intent)

    // Set HTTP headers (including referer) BEFORE playing the file
    setHttpHeadersFromExtras(intent.extras)

    val currentMpvPath = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
    val playableUri = getPlayableUri(intent)
    val hasPlayableMediaInIntent = playableUri != null

    // Re-attach to active session ONLY when returning from notification/miniplayer (no new intent playable URI) AND MPV is currently playing media
    val isAlreadyPlayingCurrent = !hasPlayableMediaInIntent && !currentMpvPath.isNullOrBlank() && currentMpvPath != "null"

    if (hasPlayableMediaInIntent) {
      val isNetwork = HttpUtils.isNetworkStream(runCatching { Uri.parse(playableUri) }.getOrNull()) || ytDlClient.requiresYtdl(playableUri)
      viewModel.onFileStartLoading(isNetwork = isNetwork)
      activeNetworkStreamId = NetworkStreamingProxy.getInstance().extractStreamId(playableUri)
      if (isManualBackgroundPlayback || isInBackgroundPlayback) {
        isManualBackgroundPlayback = false
        endBackgroundPlayback()
        enableVideoAfterBackground()
        miniPlayerStateManager.clearState()
      }
      if (isUriM3U(playableUri)) {
        loadM3uPlaylistOrPlayDirectly(playableUri)
      } else {
        loadMediaOrResolveWebStream(playableUri)
      }
    } else if (isAlreadyPlayingCurrent) {
      Log.d(TAG, "MPV is already playing media: $currentMpvPath. Re-attaching to active session.")
      activeNetworkStreamId = NetworkStreamingProxy.getInstance().extractStreamId(currentMpvPath)
      isReady = true
      enableVideoAfterBackground()
    }

    // Set orientation early if we have metadata in intent or cache (avoids jumpy transition for Video/Smart modes)
    applyInitialOrientationFromIntent(intent)

    // Apply persisted shuffle state after playlist is loaded
    viewModel.applyPersistedShuffleState()

    // Observe selected Lua scripts for runtime loading
    lifecycleScope.launch {
      var previousScripts = advancedPreferences.selectedLuaScripts.get()
      advancedPreferences.selectedLuaScripts.changes().collect { newScripts ->
        val addedScripts = newScripts - previousScripts
        addedScripts.forEach { scriptName ->
          loadScriptAtRuntime(scriptName)
        }
        previousScripts = newScripts
      }
    }

    // Observe hideOsdText preference
    lifecycleScope.launch {
      playerPreferences.hideOsdText.changes().collect { hide ->
        if (mpvInitialized) {
          runCatching {
            MPVLib.setPropertyInt("osd-level", if (hide) 0 else 1)
          }.onFailure { e ->
            Log.e(TAG, "Error updating osd-level", e)
          }
        }
      }
    }

    // Observe external audio EOF event
    lifecycleScope.launch {
      viewModel.externalAudioEofEvent.collect {
        handleEndOfFile(true)
      }
    }

    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  override fun attachBaseContext(newBase: Context?) {
    if (newBase == null) {
      super.attachBaseContext(null)
      return
    }

    val originalConfiguration = newBase.resources.configuration
    val contextToUse =
      if (originalConfiguration.fontScale == 1f) {
        newBase
      } else {
        val updatedConfiguration = Configuration(originalConfiguration).apply { fontScale = 1f }
        val configurationContext = newBase.createConfigurationContext(updatedConfiguration)
        val configurationDisplayMetrics = configurationContext.resources.displayMetrics
        configurationDisplayMetrics.scaledDensity = updatedConfiguration.fontScale * configurationDisplayMetrics.density
        configurationContext
      }

    super.attachBaseContext(contextToUse)
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun handleOnBackPressed() {
          handleBackPress()
        }
      },
    )
  }

  internal fun playDirectMedia(playableUri: String) {
    isReady = false
    currentResolvedStream = null
    currentActiveWebSubtitles = null
    viewModel.clearVideoQualities()
    if (!playerPreferences.autoplayOnOpen.get() || playerPreferences.savePositionOnQuit.get() || playerPreferences.resumePlaybackMode.get() != ResumePlaybackMode.Never) {
      runCatching { MPVLib.setPropertyBoolean("pause", true) }
    }
    if (mpvInitialized && player.holder.surface.isValid) {
      lifecycleScope.launch(Dispatchers.Default) {
        MPVLib.command("loadfile", playableUri)
      }
    } else {
      player.playFile(playableUri)
    }
  }

  internal fun loadMediaOrResolveWebStream(playableUri: String) {
    val isNetwork = HttpUtils.isNetworkStream(runCatching { Uri.parse(playableUri) }.getOrNull()) || ytDlClient.requiresYtdl(playableUri)
    viewModel.onFileStartLoading(isNetwork = isNetwork)
    if (ytDlClient.requiresYtdl(playableUri)) {
      resolveWebStream(playableUri)
      return
    }

    val uri = runCatching { Uri.parse(playableUri) }.getOrNull()
    val isDirectMedia = uri != null && HttpUtils.isDirectMediaUrl(uri)

    // For unknown URLs when addon is installed: probe Content-Type if auto-detection is enabled
    if (!isDirectMedia && ytDlClient.isAddonInstalled() && ytdlPreferences.autoDetectWebPages.get()) {
      lifecycleScope.launch {
        val isWebPage = HttpUtils.probeIsHtmlWebPage(playableUri)
        if (isWebPage) {
          Log.d(TAG, "Auto-detected HTML web page for unknown URL: $playableUri. Resolving via yt-dlp.")
          resolveWebStream(playableUri)
        } else {
          Log.d(TAG, "Probed URL is direct media/stream: $playableUri. Playing directly via MPV.")
          playDirectMedia(playableUri)
        }
      }
      return
    }

    playDirectMedia(playableUri)
  }

  private fun resolveWebStream(playableUri: String) {
    if (!ytDlClient.isAddonInstalled()) {
      Log.w(TAG, "Web stream URL requires REX Ytdlp, but addon is not installed: $playableUri")
      android.widget.Toast.makeText(
        this,
        "REX Ytdlp required to play YouTube and web video links",
        android.widget.Toast.LENGTH_LONG
      ).show()
      viewModel.onFileLoaded(0.0)
      return
    }

    isReady = false
    viewModel.onFileStartLoading(isNetwork = true)
    runCatching { MPVLib.setPropertyString("idle", "yes") }

    lifecycleScope.launch {
      Log.d(TAG, "Resolving web stream URL via REX Ytdlp: $playableUri")
      val resolved = ytDlClient.resolveStream(playableUri, ytdlPreferences.buildExtractionOptions())
      if (resolved.isSuccess && !resolved.videoUrl.isNullOrBlank()) {
        Log.d(TAG, "Stream resolved successfully: ${resolved.title}, isDASH=${resolved.isDASH}")

        // Set up headers using PlaybackHttpHeaders
        val fullHeaders = PlaybackHttpHeaders.withFallbackHeaders(resolved.httpHeaders, playableUri)
        val userAgent = PlaybackHttpHeaders.userAgent(fullHeaders)
        val mpvHeaderFields = PlaybackHttpHeaders.toMpvHeaderFields(fullHeaders)

        if (!userAgent.isNullOrBlank()) {
          runCatching { MPVLib.setPropertyString("user-agent", userAgent) }
        }
        if (mpvHeaderFields.isNotBlank()) {
          Log.d(TAG, "Setting MPV http-header-fields: $mpvHeaderFields")
          runCatching { MPVLib.setPropertyString("http-header-fields", mpvHeaderFields) }
        } else {
          runCatching { MPVLib.setPropertyString("http-header-fields", "") }
        }

        // Set custom title for OSD and MediaSession
        val mediaTitle = resolved.title ?: fileName.ifBlank { "Web Stream" }
        fileName = mediaTitle
        mediaIdentifier = getMediaIdentifier(intent, fileName)
        viewModel.setMediaTitle(fileName)
        viewModel.setMediaIdentifier(mediaIdentifier)
        intent.putExtra("title", mediaTitle)
        runCatching { MPVLib.setPropertyString("force-media-title", mediaTitle) }

        // Construct playback stream URL: use MPV EDL for DASH multi-stream
        val streamToPlay = if (resolved.isDASH && !resolved.audioUrl.isNullOrBlank()) {
          val vBytes = resolved.videoUrl.toByteArray(Charsets.UTF_8).size
          val aBytes = resolved.audioUrl.toByteArray(Charsets.UTF_8).size
          "edl://!new_stream;!no_clip;!no_chapters;%$vBytes%${resolved.videoUrl};!new_stream;!no_clip;!no_chapters;%$aBytes%${resolved.audioUrl}"
        } else {
          resolved.videoUrl
        }

        currentResolvedStream = resolved
        currentActiveWebSubtitles = resolved.subtitles.takeIf { it.isNotEmpty() }
        pendingWebSubtitles = currentActiveWebSubtitles
        val ytdlPref = ytdlPreferences.qualityPreference.get()
        val matchedQuality = resolved.availableQualities.firstOrNull { it.videoUrl == resolved.videoUrl }
          ?: resolved.availableQualities.firstOrNull { it.videoUrl?.substringBefore("?") == resolved.videoUrl?.substringBefore("?") }
          ?: (if (ytdlPref == "audio_only") resolved.availableQualities.firstOrNull { it.isAudioOnly } else null)
          ?: (if (ytdlPref.toIntOrNull() != null) resolved.availableQualities.firstOrNull { it.height == ytdlPref.toInt() } else null)
          ?: resolved.availableQualities.firstOrNull()
        viewModel.setAvailableVideoQualities(resolved.availableQualities, matchedQuality)

        Log.d(TAG, "Starting playback of streamToPlay: $streamToPlay (isDASH=${resolved.isDASH})")

        // Defer alternate-audio attach until mpv has loaded the primary file:
        // audio-add on an idle/unloaded mpv is dropped, so we stash and drain in onFileLoaded().
        android.util.Log.i("YtdlIpc", "attach point: resolved.audioTracks=${resolved.audioTracks.size}")
        pendingRemoteAudio = resolved.audioTracks.takeIf { it.isNotEmpty() }

        val autoplay = playerPreferences.autoplayOnOpen.get()
        if (!autoplay) {
          runCatching { MPVLib.setPropertyBoolean("pause", true) }
        } else {
          runCatching { MPVLib.setPropertyBoolean("pause", false) }
        }

        // Check if there is a saved resume position
        val resumeMode = playerPreferences.resumePlaybackMode.get()
        val shouldAutoResume = (resumeMode == ResumePlaybackMode.Always) ||
            (resumeMode == ResumePlaybackMode.Ask && playerPreferences.autoResumeOnAsk.get())
        val savedPos = if (playerPreferences.savePositionOnQuit.get() && shouldAutoResume) {
          withContext(Dispatchers.IO) {
            playbackStateRepository.getVideoDataByTitle(mediaIdentifier)?.lastPosition?.takeIf { it > 3 }
          }
        } else null

        val hasMpvStarted = mpvInitialized
        val loadOptions = buildList {
          add(if (!autoplay) "pause=yes" else "pause=no")
          if (hasMpvStarted && savedPos != null) {
            add("start=$savedPos")
            startedAtSavedPosition = true
          }
          if (resolved.isDASH) {
            add("flatten-editions=yes")
          }
        }.joinToString(",")

        if (hasMpvStarted) {
          lifecycleScope.launch(Dispatchers.Default) {
            Log.d(TAG, "Executing MPVLib.command loadfile for web stream with options: $loadOptions")
            MPVLib.command("loadfile", streamToPlay, "replace", "-1", loadOptions)
          }
        } else {
          player.playFile(streamToPlay)
        }
      } else {
        Log.w(TAG, "Failed to resolve stream via yt-dlp: ${resolved.errorMessage}. Attempting direct MPV playback as fallback.")
        playDirectMedia(playableUri)
      }
    }
  }

  internal fun switchVideoQuality(quality: VideoQuality?) {
    val resolved = currentResolvedStream ?: return
    val targetStreamUrl: String
    val isDASH: Boolean

    if (quality == null) {
      // Revert to "Auto" (original resolved stream)
      val defaultQuality = resolved.availableQualities.firstOrNull { it.videoUrl == resolved.videoUrl }
      viewModel.currentVideoQuality.value = defaultQuality
      val vUrl = resolved.videoUrl ?: return
      val aUrl = resolved.audioUrl
      isDASH = resolved.isDASH && !aUrl.isNullOrBlank()
      targetStreamUrl = if (isDASH && !aUrl.isNullOrBlank()) {
        val vBytes = vUrl.toByteArray(Charsets.UTF_8).size
        val aBytes = aUrl.toByteArray(Charsets.UTF_8).size
        "edl://!new_stream;!no_clip;!no_chapters;%$vBytes%$vUrl;!new_stream;!no_clip;!no_chapters;%$aBytes%$aUrl"
      } else {
        vUrl
      }
    } else {
      viewModel.currentVideoQuality.value = quality
      val vUrl = quality.videoUrl ?: return
      val aUrl = quality.audioUrl
      isDASH = quality.isDASH && !aUrl.isNullOrBlank()
      targetStreamUrl = if (isDASH && !aUrl.isNullOrBlank()) {
        val vBytes = vUrl.toByteArray(Charsets.UTF_8).size
        val aBytes = aUrl.toByteArray(Charsets.UTF_8).size
        "edl://!new_stream;!no_clip;!no_chapters;%$vBytes%$vUrl;!new_stream;!no_clip;!no_chapters;%$aBytes%$aUrl"
      } else {
        vUrl
      }
    }

    val currentPos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
      ?: ((viewModel.pos ?: 0).toDouble())
    val isPaused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull() ?: false

    isQualitySwitching = true
    viewModel.onQualitySwitchStarted(currentPos)

    // Preserve pending subtitles so they reload once the new stream is loaded
    pendingWebSubtitles = currentActiveWebSubtitles

    val loadOptions = buildList {
      add(if (isPaused) "pause=yes" else "pause=no")
      if (currentPos > 0) add("start=$currentPos")
      if (isDASH) add("flatten-editions=yes")
    }.joinToString(",")

    lifecycleScope.launch(Dispatchers.Default) {
      Log.d(TAG, "Switching video quality to ${quality?.label ?: "Auto"} with options: $loadOptions")
      MPVLib.command("loadfile", targetStreamUrl, "replace", "-1", loadOptions)
    }

    val displayLabel = quality?.label ?: getString(R.string.video_quality_auto)
    android.widget.Toast.makeText(
      this,
      "${getString(R.string.video_quality)}: $displayLabel",
      android.widget.Toast.LENGTH_SHORT
    ).show()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun handleBackPress() {
    // Dismiss overlays first
    if (viewModel.sheetShown.value != Sheets.None) {
      viewModel.sheetShown.update { Sheets.None }
      viewModel.showControls()
      return
    }

    if (viewModel.panelShown.value != Panels.None) {
      viewModel.panelShown.update { Panels.None }
      viewModel.showControls()
      return
    }

    // Check if auto PIP is enabled - enter PIP mode instead of finishing
    if (playerPreferences.autoPiPOnNavigation.get() && isReady) {
      miniPlayerStateManager.clearState()
      pipHelper.enterPipMode()
      return
    }

    // Transition to background mode and MiniPlayer when going back to file browser only if video is actively playing
    val isPaused = viewModel.paused ?: (runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull() == true)
    if (isReady && fileName.isNotBlank() && !isPaused && audioPreferences.automaticBackgroundPlayback.get()) {
      isManualBackgroundPlayback = true
      startBackgroundPlayback()
      disableVideoForBackground()
      restoreSystemUI()
      val mainIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      startActivity(mainIntent)

      return
    }

    isUserFinishing = true
    player.isExiting = true
    viewModel.playbackManager.cancelPendingJobs()
    miniPlayerStateManager.clearState()
    finish()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupPlayerControls() {
    viewModel.ambientController.startCapture(
      surfaceViewProvider = { binding.player },
      isPlayingProvider = { isReady && !isFinishing && viewModel.paused != true },
      isEligibleProvider = {
        (viewModel.videoAspect.value == VideoAspect.Fit || viewModel.currentAspectRatio.value > 0.0) &&
          viewModel.getVideoOutAspect() != null
      }
    )

    binding.controls.setContent {
      MpvexPlayerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          AmbientGlowOverlay(viewModel = viewModel)
          PlayerControls(
            viewModel = viewModel,
            onBackPress = {
              handleBackPress()
            },
            modifier = Modifier,
          )
        }
      }
    }
  }

  /**
   * Initializes the Picture-in-Picture helper.
   */
  private fun setupPipHelper() {
    pipController.pipHelper
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      runCatching {
        safeSetPropertyString(it.property, it.value)
      }.onFailure { e ->
        Log.e(TAG, "Error setting audio channels: ${it.property}=${it.value}", e)
      }
    }

    audioController.setupAudioFocus(serviceBound, playerPreferences.autoplayOnOpen.get())
  }

  /**
   * @return true if audio focus was granted immediately, false otherwise
   */
  override fun requestAudioFocus(): Boolean = audioController.requestAudioFocus()

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // Enter PIP mode when user presses home button if auto PIP is enabled
    if (playerPreferences.autoPiPOnNavigation.get() && isReady && !isFinishing) {
      pipController.isEnteringPip = true
      miniPlayerStateManager.clearState()
      pipHelper.enterPipMode()
    } else if (isReady && !isFinishing) {
      val isEnding = isUserFinishing || isFinishing
      val shouldAllowBackgroundPlayback = isManualBackgroundPlayback || 
                                          (audioPreferences.automaticBackgroundPlayback.get() && !isEnding)
      if (shouldAllowBackgroundPlayback) {
        startBackgroundPlayback()
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onDestroy() {
    Log.d(TAG, "PlayerActivity onDestroy")
    player.isExiting = true
    viewModel.playbackManager.cancelPendingJobs()
    runCatching { remoteClient.onPlayerFinished() }

    runCatching {
      // Only stop the service if we're not doing manual background playback
      if ((isUserFinishing || isFinishing) && !isManualBackgroundPlayback) {
        if (serviceBound) {
          runCatching { unbindService(serviceConnection) }
          serviceBound = false
        }
        stopService(Intent(this, MediaPlaybackService::class.java))
        mediaPlaybackService = null
      }

      // Wait for any pending save operation to complete before destroying MPV with a 200ms bounded timeout
      // This prevents the main thread from blocking infinitely during activity destruction (ANR prevention)
      playbackStateController.waitForPendingSave(200)

      webSubtitlesJob?.cancel()
      webSubtitlesJob = null

      cleanupMPV()
      cleanupAudio()
      cleanupReceivers()
      releaseMediaSession()

      activeNetworkStreamId?.let { streamId ->
        activeNetworkStreamId = null
        if (!isManualBackgroundPlayback) {
          kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            NetworkStreamingProxy.getInstance().unregisterStream(streamId)
          }
        }
      }

      runCatching { ytDlClient.unbind() }
    }.onFailure { e ->
      Log.e(TAG, "Error during onDestroy", e)
    }

    if (activeInstance === this) {
      activeInstance = null
    }

    super.onDestroy()
  }

  private fun cleanupMPV() {
    isAutoAdvancing = false
    if (!mpvInitialized) return

    // Don't cleanup MPV if we're doing background playback
    if (!isFinishing || isManualBackgroundPlayback) return

    player.isExiting = true
    viewModel.playbackManager.cancelPendingJobs()

    // Stop media notification service when activity is destroyed
    endBackgroundPlayback()

    if (inheritedNativeSession) {
      runCatching { MPVLib.removeObserver(playerObserver) }
      // Prevent SurfaceView teardown from racing the explicit detach performed by the controller.
      player.holder.removeCallback(player)
      headlessPlaybackController.retainAfterPlayerExit()
      inheritedNativeSession = false
      mpvInitialized = false
      return
    }

    MPVLifecycleLock.onTeardownStart()
    try {
      runCatching {
        MPVLib.removeObserver(playerObserver)

        if (isReady) {
          MPVLib.setPropertyBoolean("pause", true)
          MPVLib.command("quit")
        }

        // Explicitly detach Surface and set VO to null so Android RenderThread drops ANativeWindow mutexes
        runCatching {
          MPVLib.setPropertyString("vo", "null")
          MPVLib.detachSurface()
        }

        MPVLib.destroy()
        mpvInitialized = false
      }.onFailure { e ->
        Log.e(TAG, "Error cleaning up MPV", e)
      }
    } finally {
      MPVLifecycleLock.onTeardownComplete()
    }
  }

  override fun abandonAudioFocus() {
    audioController.abandonAudioFocus()
  }

  private fun cleanupAudio() {
    abandonAudioFocus()
  }

  private fun cleanupReceivers() {
    audioController.unregisterNoisyReceiver()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPause() {
    runCatching {
      val isInPip = isInPictureInPictureMode || pipController.isEnteringPip || pipController.wasInPipMode
      val isInMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false
      val isEnding = isUserFinishing || isFinishing
      val isAutoPipEnabled = playerPreferences.autoPiPOnNavigation.get()
      val isPipTargeted = isInPip || (isAutoPipEnabled && !isEnding)
      val shouldPause = (!audioPreferences.automaticBackgroundPlayback.get() && !isManualBackgroundPlayback) || 
                        (isEnding && !isManualBackgroundPlayback)

      if (isPipTargeted) {
        miniPlayerStateManager.clearState()
      } else if (!isInMultiWindow) {
        if (shouldPause) {
          viewModel.pause()
        } else {
          // Background playback is active - disable video decoding to save battery
          startBackgroundPlayback()
          disableVideoForBackground()
        }
      }

      saveVideoPlaybackState(fileName)
    }.onFailure { e ->
      Log.e(TAG, "Error during onPause", e)
    }

    super.onPause()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun finish() {
    Log.d(TAG, "finish() called, caller stack:\n" + Log.getStackTraceString(Throwable()))
    runCatching {
      if (!isManualBackgroundPlayback) {
        player.isExiting = true
        viewModel.playbackManager.cancelPendingJobs()
        isReady = false
      }

      // Restore UI immediately for responsive exit
      if (!isInPictureInPictureMode) {
        restoreSystemUI()
      }
      
      // Clean up service when finishing (unless in background playback)
      if (!isManualBackgroundPlayback && (serviceBound || mediaPlaybackService != null)) {
        endBackgroundPlayback()
      }
      
      setReturnIntent()

    }.onFailure { e ->
      Log.e(TAG, "Error during finish", e)
    }

    super.finish()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun finishAndRemoveTask() {
    Log.d(TAG, "finishAndRemoveTask() called, caller stack:\n" + Log.getStackTraceString(Throwable()))
    runCatching {
      if (!isManualBackgroundPlayback) {
        player.isExiting = true
        viewModel.playbackManager.cancelPendingJobs()
        isReady = false
      }
      isUserFinishing = true
      
      // Restore UI immediately for responsive exit (same as finish())
      if (!isInPictureInPictureMode) {
        restoreSystemUI()
      }
      
      // Clean up service when finishing (unless in background playback)
      if (!isManualBackgroundPlayback && (serviceBound || mediaPlaybackService != null)) {
        endBackgroundPlayback()
      }
      
      setReturnIntent()

    }.onFailure { e ->
      Log.e(TAG, "Error during finishAndRemoveTask", e)
    }

    super.finishAndRemoveTask()
  }

  override fun onStop() {
    runCatching {
      pipHelper.onStop()
      saveVideoPlaybackState(fileName)

      audioController.unregisterNoisyReceiver()

      // Handle background playback based on preferences
      val isEnding = isUserFinishing || isFinishing
      val isPipDismissed = wasInPipMode && !isChangingConfigurations
      
      val shouldAllowBackgroundPlayback = isManualBackgroundPlayback || 
                                          (audioPreferences.automaticBackgroundPlayback.get() && !isEnding && !isPipDismissed)
      
      if (!shouldAllowBackgroundPlayback) {
        viewModel.pause()
        miniPlayerStateManager.clearState()
        if (isPipDismissed) {
          endBackgroundPlayback()
          finish()
        }
      } else {
        startBackgroundPlayback()
        if (!isInBackgroundPlayback) {
          // Ensure video is disabled when hidden, even if it wasn't handled in onPause (e.g. multi-window)
          disableVideoForBackground()
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onStop", e)
    }

    orientationController.stop()
    super.onStop()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onStart() {
    super.onStart()
    orientationController.start()

    runCatching {
      setupWindowFlags()
      setupSystemUI()

      // Restore video if it was disabled for background playback
      enableVideoAfterBackground()

      audioController.registerNoisyReceiver()

      if (!isInPictureInPictureMode && playerPreferences.rememberBrightness.get()) {
        val brightness = playerPreferences.defaultBrightness.get()
        if (brightness != BRIGHTNESS_NOT_SET) {
          viewModel.changeBrightnessTo(brightness)
        }
      }
      
      // Reset manual background playback flag when returning to foreground
      isManualBackgroundPlayback = false
    }.onFailure { e ->
      Log.e(TAG, "Error during onStart", e)
    }
  }

  private fun setupWindowFlags() {
    systemUiController.setupWindowFlags()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupSystemUI() {
    systemUiController.setupSystemUI()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun restoreSystemUI() {
    systemUiController.restoreSystemUI()
  }

  /**
   * Initializes the MPV player with the necessary paths and observers.
   * CRITICAL: Must copy config and scripts BEFORE initializing MPV, as MPV loads scripts during init.
   */
  private fun setupMPV() {
    if (MPVLifecycleLock.isTearingDown.value) {
      kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        MPVLifecycleLock.awaitTeardown()
      }
    }

    // Copy essential files FIRST, before MPV initialization
    // MPV will load scripts during initialize(), so they must exist beforehand
    MpvConfigSync.prepare(this@PlayerActivity)

    // NOW initialize MPV - it will find and load the scripts we just copied
    player.initialize(filesDir.path, cacheDir.path)
    mpvInitialized = true
    Log.d(TAG, "MPV initialized")

    // Configure initial OSD level based on preference
    runCatching {
      MPVLib.setPropertyInt("osd-level", if (playerPreferences.hideOsdText.get()) 0 else 1)
    }.onFailure { e ->
      Log.e(TAG, "Error setting initial osd-level", e)
    }

    // Add observer after initialization
    MPVLib.addObserver(playerObserver)
  }

  /**
   * Takes over an already-running MPV instance created by [HeadlessPlaybackController]
   * (direct mini player mode). Does NOT call [player.initialize] — that would invoke
   * `MPVLib.create()` a second time on the global native singleton and crash. Instead it
   * relinquishes ownership from the controller and re-registers this activity's surface
   * callback and observers, so playback continues seamlessly with video re-enabled.
   */
  private fun setupMPVForHandoff() {
    if (MPVLifecycleLock.isTearingDown.value) {
      kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        MPVLifecycleLock.awaitTeardown()
      }
    }

    headlessPlaybackController.detachForHandoff()
    player.attachToExistingSession()
    inheritedNativeSession = true
    mpvInitialized = true
    Log.d(TAG, "MPV attached to existing headless session")

    runCatching {
      MPVLib.setPropertyInt("osd-level", if (playerPreferences.hideOsdText.get()) 0 else 1)
    }.onFailure { e ->
      Log.e(TAG, "Error setting initial osd-level", e)
    }

    MPVLib.addObserver(playerObserver)
  }

  /**
   * Loads a specific Lua script at runtime without restarting the player.
   * Finds the script in the user's MPV directory, copies it to internal storage,
   * and commands MPV to load it.
   */
  private fun loadScriptAtRuntime(scriptName: String) {
    if (!mpvInitialized || isFinishing) return

    val mpvConfStorageUri = advancedPreferences.mpvConfStorageUri.get()
    if (mpvConfStorageUri.isBlank()) return

    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val tree = DocumentFile.fromTreeUri(this@PlayerActivity, mpvConfStorageUri.toUri())
        if (tree != null && tree.exists()) {
          // Look for scripts/ subfolder first (case-insensitive), fall back to root
          val scriptsDir = MpvConfigSync.findSubdirCaseInsensitive(tree, "scripts") ?: tree

          val scriptFile = scriptsDir.listFiles().firstOrNull {
            it.name == scriptName
          }

          if (scriptFile != null) {
            val internalScriptsDir = File(filesDir, "scripts")
            if (!internalScriptsDir.exists()) internalScriptsDir.mkdirs()

            val targetFile = File(internalScriptsDir, scriptName)

            contentResolver.openInputStream(scriptFile.uri)?.use { input ->
              targetFile.outputStream().use { output ->
                input.copyTo(output)
              }
            }

            withContext(Dispatchers.Main) {
              MPVLib.command("load-script", targetFile.absolutePath)
              viewModel.showToast("Loaded script: $scriptName")
            }
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Error loading script at runtime: $scriptName", e)
        withContext(Dispatchers.Main) {
          android.widget.Toast.makeText(
            this@PlayerActivity,
            "Failed to load script: ${e.message}",
            android.widget.Toast.LENGTH_LONG
          ).show()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    enableVideoAfterBackground()
    updateVolume()
  }

  /**
   * Updates the volume level to match the system volume.
   *
   * This method updates the current volume level by getting the current system volume
   * and adjusting the MPV volume accordingly. It ensures that the MPV volume is set
   * to the maximum allowed value if the system volume is lower than the maximum.
   */
  private fun updateVolume() {
    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also { volume ->
        if (volume < viewModel.maxVolume) {
          viewModel.changeMPVVolumeTo(MAX_MPV_VOLUME)
        }
      }
    }
  }

  /**
   * Processes intent extras to set initial playback position, subtitles, and HTTP headers.
   *
   * This method checks the intent extras for the following keys:
   * - "position": The initial playback position in seconds.
   * - "subs": A list of subtitle URIs to add.
   * - "subs.enable": A list of subtitle URIs to enable.
   * - "headers": A list of HTTP headers to set for network playback.
   *
   * @param extras Bundle containing intent extras
   */
  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    extras.getInt("position", POSITION_NOT_SET).takeIf { it != POSITION_NOT_SET }?.let {
      MPVLib.setPropertyInt("time-pos", it / MILLISECONDS_TO_SECONDS)
    }

    addSubtitlesFromExtras(extras)
    setHttpHeadersFromExtras(extras)
  }

  /**
   * Adds subtitle tracks from intent extras.
   *
   * This method checks the intent extras for the "subs" key, which contains a list
   * of subtitle URIs to add. It also checks for the "subs.enable" key, which contains
   * a list of subtitle URIs to enable.
   *
   * @param extras Bundle containing subtitle URIs
   */
  private fun addSubtitlesFromExtras(extras: Bundle) {
    intentHandler.addSubtitlesFromExtras(extras)
  }

  /**
   * Safe wrapper for MPVLib.setPropertyString to prevent native crashes (SIGSEGV).
   * Ensures that the property name and value are not null or blank before passing to JNI.
   */
  internal fun safeSetPropertyString(property: String, value: String?) {
    if (property.isBlank()) return
    if (value == null) {
      Log.w(TAG, "Attempted to set null value for MPV property: $property")
      return
    }
    runCatching {
      MPVLib.setPropertyString(property, value)
    }.onFailure { e ->
      Log.e(TAG, "Failed to set MPV property $property: ${e.message}")
    }
  }

  private fun setHttpHeadersFromExtras(extras: Bundle?) {
    intentHandler.setHttpHeadersFromExtras(extras)
  }
  /**
   * Sets HTTP headers for a specific URI (used for playlist items).
   * Automatically extracts and sets the Referer header based on the URI origin.
   *
   * @param uri The URI to extract referer from and set headers for
   */
  internal fun setHttpHeadersForUri(uri: Uri) = playlistLoader.setHttpHeadersForUri(uri)

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
  internal fun parsePathFromIntent(intent: Intent): String? = intentHandler.parsePathFromIntent(intent)

  private fun parsePathFromSendIntent(intent: Intent): String? = intentHandler.parsePathFromSendIntent(intent)

  internal fun getFileName(intent: Intent): String = intentHandler.getFileName(intent)

  internal fun extractFileNameFromUri(uri: Uri): String = intentHandler.extractFileNameFromUri(uri)

  internal fun getPlaylistItemTitle(uri: Uri): String {
    getDisplayNameFromUri(uri)?.let { return it }
    return extractFileNameFromUri(uri)
  }

  internal fun playPlaylistItem(index: Int) {
    if (index >= 0 && index < viewModel.playlistManager.playlist.value.size) {
      loadPlaylistItem(index)
    }
  }

  internal fun extractUriFromIntent(intent: Intent): Uri? = intentHandler.extractUriFromIntent(intent)

  internal fun getDisplayNameFromUri(uri: Uri): String? = intentHandler.getDisplayNameFromUri(uri)

  internal fun getPlayableUri(intent: Intent): String? = intentHandler.getPlayableUri(intent)

  /**
   * Handles device configuration changes.
   *
   * @param newConfig The new configuration
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
    viewModel.onOrientationChanged(isPortrait)
    if (isReady) {
      handleConfigurationChange()
    }
  }

  private fun applyInitialOrientationFromIntent(targetIntent: Intent) =
    intentHandler.applyInitialOrientationFromIntent(targetIntent)

  /**
   * Handles configuration changes by updating video aspect ratio.
   */
  private fun handleConfigurationChange() {
    if (!isInPictureInPictureMode) {
      if (viewModel.videoAspect.value == VideoAspect.Stretch && viewModel.currentAspectRatio.value <= 0) {
        viewModel.changeVideoAspect(VideoAspect.Stretch, showUpdate = false, resetZoomAndPan = false, persistToPreferences = false)
      }
    } else {
      viewModel.hideControls()
    }
  }

  // ==================== MPV Event Observers ====================

  /**
   * Observer callback for MPV property changes (Long values).
   * Handles video width and height changes.
   *
   * @param property The property name that changed
   * @param value The new Long value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: Long,
  ) {
    mpvEventDispatcher.dispatchProperty(property, value)
  }

  /**
   * Observer callback for MPV property changes (Boolean values).
   * Handles pause state and end-of-file events.
   *
   * @param property The property name that changed
   * @param value The new Boolean value
   */
  internal fun onObserverEvent(
    property: String,
    value: Boolean,
  ) {
    mpvEventDispatcher.dispatchProperty(property, value)
  }

  /**
   * Handles pause state changes by managing screen-on flag and MediaSession state.
   *
   * @param isPaused true if playback is paused, false if playing
   */
  private fun handlePauseStateChange(isPaused: Boolean) {
    if (isPaused) {
      // Only clear keep-screen-on if the preference is NOT enabled
      if (!playerPreferences.keepScreenOnWhenPaused.get()) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    updateMediaSessionPlaybackState(!isPaused)
    runCatching {
      if (isInPictureInPictureMode) {
        pipHelper.updatePictureInPictureParams()
      }
    }.onFailure { /* Silently ignore PiP update failures */ }
  }

  /**
   * Handles end-of-file event by playing next in playlist if available, otherwise finishing activity if configured.
   *
   * @param isEof true if end of file reached
   */
  private fun handleEndOfFile(isEof: Boolean) {
    if (isEof) {
      if (!isReady || isAutoAdvancing || viewModel.isLoadingFile.value) {
        Log.w(
          TAG,
          "handleEndOfFile: ignoring EOF because player is not ready or file is loading (isReady=$isReady, isAutoAdvancing=$isAutoAdvancing, isLoadingFile=${viewModel.isLoadingFile.value})"
        )
        return
      }

      // Ignore spurious EOF if the file was loaded less than 1 second ago
      val timeSinceLoad = SystemClock.elapsedRealtime() - lastFileLoadedTimeMs
      if (lastFileLoadedTimeMs > 0L && timeSinceLoad < 1000L) {
        Log.w(TAG, "handleEndOfFile: ignoring spurious EOF within 1000ms of file load (${timeSinceLoad}ms)")
        return
      }

      // Position sanity check: if duration is known and positive, EOF should only be accepted if we are near the end of video
      val currentPos = viewModel.pos ?: runCatching { MPVLib.getPropertyInt("time-pos") }.getOrNull() ?: 0
      val currentDuration = viewModel.duration ?: runCatching { MPVLib.getPropertyInt("duration") }.getOrNull() ?: 0
      if (currentDuration > 2 && currentPos < (currentDuration - 3)) {
        Log.w(
          TAG,
          "handleEndOfFile: ignoring spurious EOF because playback is not near end (pos=$currentPos, duration=$currentDuration)"
        )
        return
      }

      // Save state immediately when EOF is reached
      saveVideoPlaybackState(fileName, isEof = true)

      // Check if we should repeat the current file
      if (viewModel.shouldRepeatCurrentFile()) {
        MPVLib.command("seek", "0", "absolute")
        viewModel.unpause()
        return
      }

      // Handle playlist playback
      val playlist = viewModel.playlistManager.playlist.value
      if (playlist.isNotEmpty()) {
        val hasNextItem = viewModel.playlistManager.hasNext(viewModel.shouldRepeatPlaylist())

        if (hasNextItem) {
          // Play next item in playlist
          isAutoAdvancing = true
          isAutoplayNextTriggered = true
          isReady = false
          playNext()
        } else {
          isAutoAdvancing = false
          miniPlayerStateManager.clearState()
          if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
            finish()
          }
        }
      } else {
        // Single video playback (no playlist)
        isAutoAdvancing = false
        miniPlayerStateManager.clearState()
        if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
          finish()
        }
      }
    }
  }

  /**
   * Observer callback for MPV property changes (String values).
   * Handles Lua script invocations.
   *
   * @param property The property name that changed
   * @param value The new String value
   */
  internal fun onObserverEvent(
    property: String,
    value: String,
  ) {
    mpvEventDispatcher.dispatchProperty(property, value)
  }

  /**
   * Observer callback for MPV property changes (MPVNode values).
   *
   * This method is called when an MPV property (with MPVNode value) changes.
   * Extend this method to handle properties as needed.
   *
   * @param property The property name that changed
   * @param value The new MPVNode value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: MPVNode,
  ) {
    mpvEventDispatcher.dispatchProperty(property, value)
  }

  /**
   * Observer callback for MPV property changes (Double values).
   *
   * This method is called when an MPV property (with Double value) changes.
   * Extend this method to handle properties as needed.
   *
   * @param property The property name that changed
   * @param value The new Double value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: Double,
  ) {
    mpvEventDispatcher.dispatchProperty(property, value)
  }

  /**
   * Observer callback for MPV property changes (no value parameter).
   * Handles properties with no value parameter.
   *
   * @param property The property name that changed
   */
  internal fun onObserverEvent(property: String) {
    mpvEventDispatcher.dispatchProperty(property)
  }

  /**
   * Handles MPV core events such as file loaded and playback restart.
   *
   * Called by the player when critical playback events occur.
   *
   * @param eventId The MPV event ID
   */
  internal fun event(eventId: Int) {
    mpvEventDispatcher.dispatchEvent(eventId)
  }

  /**
   * Handles the file loaded event from MPV.
   * Initializes playback state, loads saved playback data, restores custom settings,
   * applies user preferences, and sets up metadata and media session.
   */
  private fun handleFileLoaded() {
    if (isQualitySwitching) {
      isQualitySwitching = false
      val loadedDurationSec = MPVLib.getPropertyDouble("duration") ?: 0.0
      viewModel.onFileLoaded(loadedDurationSec)
      return
    }

    // Extract fileName from intent only if not already set
    // This preserves fileName set in onNewIntent or onCreate
    if (fileName.isBlank()) {
      fileName = getFileName(intent)
      // Ensure fileName is not blank - use a fallback if necessary
      if (fileName.isBlank()) {
        fileName = intent.data?.lastPathSegment ?: "Unknown Video"
      }
      mediaIdentifier = getMediaIdentifier(intent, fileName)
    } else if (mediaIdentifier.isBlank()) {
      // If fileName was already set, but mediaIdentifier is missing, set it for safety
      mediaIdentifier = getMediaIdentifier(intent, fileName)
    }

    // Start media notification service only when going to background (like stock mpv-android)
    // startBackgroundPlayback() is now deferred to backgrounding lifecycle events

    // If we are currently in background playback, disable video for the new file too
    if (isInBackgroundPlayback) {
      disableVideoForBackground()
    }

    // Reset AB loop values when video changes
    viewModel.clearABLoop()

    // Reset external audio tracks when a new video starts
    viewModel.resetExternalAudioTracks()

    // Reset manual orientation override for the new video
    orientationController.isUserOverridden = false

    // Update VM and services with exact loaded duration
    val loadedDurationSec = MPVLib.getPropertyDouble("duration") ?: 0.0
    viewModel.onFileLoaded(loadedDurationSec)
    if (loadedDurationSec > 0.0) {
      val loadedDurationMs = (loadedDurationSec * 1000).toLong()
      miniPlayerStateManager.updateState(durationMs = loadedDurationMs)
      updateMediaSessionMetadata(title = fileName, durationMs = loadedDurationMs)
    }

    // Set the background playback toggle button default based on BackgroundPlaybackMode preference
    val isAudio = isCurrentMediaAudio()
    val defaultBgPlayback = when (playerPreferences.backgroundPlayback.get()) {
      BackgroundPlaybackMode.Always -> true
      BackgroundPlaybackMode.AudioOnly -> isAudio
      BackgroundPlaybackMode.VideoOnly -> !isAudio
      BackgroundPlaybackMode.Never -> false
    }
    audioPreferences.automaticBackgroundPlayback.set(defaultBgPlayback)

    if (pendingIntentExtras) {
      setIntentExtras(intent.extras)
      pendingIntentExtras = false
    }
    // Reset aspect ration to preferred and pan to neutral
    viewModel.resetVisualPreferences()
    viewModel.clearResumePrompt()
    needsAspectReapply = true
    isPlaybackStateLoaded = false

    val shouldAutoplay = playerPreferences.autoplayOnOpen.get() || isAutoplayNextTriggered
    isAutoplayNextTriggered = false
    isAutoAdvancing = false

    lifecycleScope.launch(Dispatchers.IO) {
      val externalPosMs = jellyfinExternalInfo?.positionMs
      val isJellyfinExternal = jellyfinExternalInfo != null && externalPosMs != null
      val hasState = if (isJellyfinExternal) {
        val s = loadVideoPlaybackState(fileName)
        val sec = externalPosMs!! / MILLISECONDS_TO_SECONDS
        MPVLib.setPropertyInt("time-pos", sec)
        xyz.mpv.rex.jellyfin.JellyfinExternalHelper.logSeekApplied(externalPosMs)
        viewModel.clearResumePrompt()
        s
      } else {
        loadVideoPlaybackState(fileName)
      }

      // Re-enable the video/album-art track when loading a file in the foreground.
      // onNewIntent loads new files with vid="no"; if we're not in background
      // playback we must restore it so both real video AND embedded album art
      // (which mpv exposes as a video track) render in the player. The background
      // case is already handled by disableVideoForBackground() above.
      runCatching {
        if (!isInBackgroundPlayback && MPVLib.getPropertyString("vid") == "no") {
          if (lastVid > 0) {
            MPVLib.setPropertyInt("vid", lastVid)
            lastVid = -1
          } else {
            safeSetPropertyString("vid", "auto")
          }
        }
      }

      // Unpause playback after position and state restoration complete (unless asking user to resume)
      if (shouldAutoplay && viewModel.resumePrompt.value == null) {
        withContext(Dispatchers.Main) { requestAudioFocus() }
        runCatching {
          MPVLib.setPropertyBoolean("pause", false)
        }
      } else {
        runCatching {
          MPVLib.setPropertyBoolean("pause", true)
        }
        withContext(Dispatchers.Main) {
          abandonAudioFocus()
          updateMediaSessionPlaybackState(isPlaying = false)
        }
      }

      // Apply track selection logic (defaults only apply when no saved state)
      trackSelector.onFileLoaded(hasState)

      // Apply default zoom only if there's no saved state
      if (!hasState) {
        withContext(Dispatchers.Main) {
          val zoomPreference = playerPreferences.defaultVideoZoom.get()
          if (zoomPreference != 0f) {
            MPVLib.setPropertyDouble("video-zoom", zoomPreference.toDouble())
            viewModel.setVideoZoom(zoomPreference)
          } else {
            val currentZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
            viewModel.setVideoZoom(currentZoom)
          }
        }
      }

      // Re-apply OSD level option on file load to prevent resets
      runCatching {
        MPVLib.setPropertyInt("osd-level", if (playerPreferences.hideOsdText.get()) 0 else 1)
      }.onFailure { e ->
        Log.e(TAG, "Error applying osd-level on file load", e)
      }
      
    }

    // Save to recently played when video actually loads and plays (only for internal launches)
    lifecycleScope.launch(Dispatchers.IO) {
      val playlist = viewModel.playlistManager.playlist.value
      val playlistIndex = viewModel.playlistManager.currentIndex.value
      val currentUri = if (playlist.isNotEmpty() && playlistIndex >= 0 && playlistIndex < playlist.size) {
        playlist[playlistIndex]
      } else {
        extractUriFromIntent(intent)
      }

      if (currentUri != null) {
        val isInternalLaunch = intent.getBooleanExtra("internal_launch", false)
        val isShare = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
        val rawLaunchSource = intent.getStringExtra("launch_source")

        val isExternalOrShare = !isInternalLaunch || isShare || rawLaunchSource == "share" || rawLaunchSource == "open_file" || rawLaunchSource == "external"

        // Should NOT record or update Chronological Playback History when opened from external source or share (if preference is enabled)
        if (isExternalOrShare && advancedPreferences.excludeExternalPlaybackFromHistory.get()) {
          Log.d(TAG, "Skipping chronological history recording for external/share launch (isInternal=$isInternalLaunch, isShare=$isShare, source=$rawLaunchSource)")
          return@launch
        }

        val launchSource = when {
          rawLaunchSource != null -> rawLaunchSource
          playlist.isNotEmpty() -> "playlist"
          isShare -> "share"
          !isInternalLaunch -> "external"
          else -> "normal"
        }

        viewModel.historyManager.recordPlaybackStart(
          uri = currentUri,
          fileName = fileName,
          launchSource = launchSource,
          playlistId = viewModel.playlistManager.playlistId
        )
      }
    }

    // Apply orientation when file is loaded
    val orientation = playerPreferences.orientation.get()
    if (orientation != PlayerOrientation.Video && orientation != PlayerOrientation.Smart) {
      setOrientation()
    } else {
      val aspect = player.getVideoOutAspect()
      if (aspect != null && aspect > 0.0) {
        setOrientation()
      } else if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
        applyInitialOrientationFromIntent(intent)
      }
    }

    applySubtitlePreferences()

    // Don't force media-title for standalone m3u/m3u8 streams - let MPV provide it
    // But if we are playing from an M3U playlist with custom titles, we MUST set it
    val isM3uPlaylist = viewModel.playlistManager.isM3uPlaylist
    val hasCustomTitle = !viewModel.playlistManager.getTitleAt(viewModel.playlistManager.currentIndex.value).isNullOrBlank()
    if (!isCurrentStreamM3U() || isM3uPlaylist || hasCustomTitle) {
      safeSetPropertyString("force-media-title", fileName)
      viewModel.setMediaTitle(fileName)
    } else {
      viewModel.setMediaTitle(fileName)
    }

    if (!shouldAutoplay || playerPreferences.showControlsOnPlay.get()) {
      viewModel.showControls()
    }

    val isWebStream = ytDlClient.requiresYtdl(parsePathFromIntent(intent) ?: "")
    if (subtitlesPreferences.autoloadMatchingSubtitles.get() && !isWebStream) {
      lifecycleScope.launch {
        // For network files played via proxy (SMB/WebDAV/FTP), use the original network file path
        val networkFilePath = intent.getStringExtra("network_file_path")
        val networkConnectionId = intent.getLongExtra("network_connection_id", -1L)

        if (networkFilePath != null && networkConnectionId != -1L) {
          // Pass network file path and connection ID for subtitle discovery
          SubtitleOps.autoloadSubtitles(
            videoFilePath = networkFilePath,
            videoFileName = fileName,
            networkConnectionId = networkConnectionId,
          )
        } else {
          // Regular file or direct network stream
          val filePath = parsePathFromIntent(intent)
          if (filePath != null) {
            SubtitleOps.autoloadSubtitles(
              videoFilePath = filePath,
              videoFileName = fileName,
            )
          }
        }
      }
    }

    updateMediaSessionMetadata(
      title = fileName,
      durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L,
    )
    val isPlaying = shouldAutoplay && viewModel.resumePrompt.value == null
    updateMediaSessionPlaybackState(isPlaying = isPlaying)

    // Update MiniPlayer state and media notification thumbnail for newly loaded track
    val currentUri = viewModel.playlistManager.getCurrentUri() ?: extractUriFromIntent(intent)
    val currentTitle = fileName
    val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""

    val nextIndex = viewModel.playlistManager.getNextIndex(viewModel.shouldRepeatPlaylist())
    val nextUri = if (nextIndex != null) viewModel.playlistManager.playlist.value.getOrNull(nextIndex) else null
    val nextTitle = if (nextIndex != null) {
      viewModel.playlistManager.getTitleAt(nextIndex) ?: nextUri?.let { extractFileNameFromUri(it) }
    } else null

    val prevIndex = viewModel.playlistManager.getPreviousIndex(viewModel.shouldRepeatPlaylist())
    val prevUri = if (prevIndex != null) viewModel.playlistManager.playlist.value.getOrNull(prevIndex) else null
    val prevTitle = if (prevIndex != null) {
      viewModel.playlistManager.getTitleAt(prevIndex) ?: prevUri?.let { extractFileNameFromUri(it) }
    } else null

    lifecycleScope.launch(Dispatchers.IO) {
      // Step 1: Immediately apply cached thumbnails (memory/disk/embedded cover art cache)
      val cachedCurrentThumb = currentUri?.let { getCachedThumbnailForUri(it) }
      val cachedNextThumb = nextUri?.let { getCachedThumbnailForUri(it) }
      val cachedPrevThumb = prevUri?.let { getCachedThumbnailForUri(it) }

      withContext(Dispatchers.Main) {
        val activeThumb = cachedCurrentThumb ?: MediaPlaybackService.thumbnail ?: miniPlayerStateManager.state.value.thumbnail
        MediaPlaybackService.thumbnail = activeThumb
        mediaPlaybackService?.setMediaInfo(title = currentTitle, artist = artist, thumbnail = activeThumb)
        miniPlayerStateManager.updateState(
          isPlaybackActive = true,
          isPaused = !isPlaying,
          title = currentTitle,
          artist = artist,
          thumbnail = activeThumb,
          videoPath = currentUri?.toString(),
          hasNext = hasNext(),
          hasPrevious = hasPrevious(),
          nextTitle = nextTitle,
          prevTitle = prevTitle,
          nextThumbnail = cachedNextThumb,
          prevThumbnail = cachedPrevThumb,
        )
      }

      // Step 2: Full thumbnail extraction/generation in background if needed
      val fullThumbnail = currentUri?.let { extractThumbnailOrCoverArt(it) }
      val fullNextThumbnail = nextUri?.let { extractThumbnailOrCoverArt(it) }
      val fullPrevThumbnail = prevUri?.let { extractThumbnailOrCoverArt(it) }

      withContext(Dispatchers.Main) {
        val finalThumb = fullThumbnail ?: cachedCurrentThumb ?: MediaPlaybackService.thumbnail ?: miniPlayerStateManager.state.value.thumbnail
        MediaPlaybackService.thumbnail = finalThumb
        mediaPlaybackService?.setMediaInfo(title = currentTitle, artist = artist, thumbnail = finalThumb)
        miniPlayerStateManager.updateState(
          thumbnail = finalThumb,
          nextThumbnail = fullNextThumbnail ?: cachedNextThumb,
          prevThumbnail = fullPrevThumbnail ?: cachedPrevThumb,
        )
      }
    }

    // Asynchronously fetch better filename from HTTP headers for network streams
    fetchNetworkStreamTitle()
  }

  /**
   * Fetches a better title from HTTP headers for network streams asynchronously.
   * Updates the title in UI, MPV, and media session if a better name is found.
   */
  private fun fetchNetworkStreamTitle() {
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        val uri = extractUriFromIntent(intent)
        if (uri == null || !HttpUtils.isNetworkStream(uri)) {
          return@launch
        }

        // Skip fetching for m3u/m3u8 streams - let MPV provide the title
        if (isCurrentStreamM3U()) {
          Log.d(TAG, "Skipping title fetch for m3u/m3u8 stream: $uri")
          return@launch
        }

        // Skip fetching if title was provided in intent extras (e.g. from Jellyfin or other external launchers)
        // This prevents overwriting the correct title with a generic filename from the URL (like "stream")
        if (intent.hasExtra("title") || intent.hasExtra("filename")) {
          Log.d(TAG, "Skipping title fetch because title was explicitly provided in intent: $fileName")
          return@launch
        }

        // Skip fetching for local proxy URLs (SMB/WebDAV/FTP files)
        // These already have correct filename from intent extras
        val host = uri.host?.lowercase()
        if (host == "127.0.0.1" || host == "localhost" || host == "0.0.0.0") {
          Log.d(TAG, "Skipping title fetch for local proxy URL: $uri")
          return@launch
        }

        val url = uri.toString()
        Log.d(TAG, "Fetching title from network stream: $url")

        val betterFilename = HttpUtils.extractFilenameFromUrl(url)
        if (betterFilename != null && betterFilename.isNotBlank() &&
          betterFilename != fileName &&
          betterFilename != uri.host &&
          betterFilename != "Network Stream"
        ) {

          Log.d(TAG, "Found better filename from HTTP headers: $betterFilename")

          // Update fileName
          fileName = betterFilename

          // DO NOT update mediaIdentifier - keep the original identifier for playback state consistency
          // The URI hash in mediaIdentifier ensures position is saved/loaded correctly even if filename changes

          // Update MPV title
          withContext(Dispatchers.Main) {
            safeSetPropertyString("force-media-title", fileName)
            viewModel.setMediaTitle(fileName)

            // Update media session
            val durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L
            updateMediaSessionMetadata(
              title = fileName,
              durationMs = durationMs,
            )

            // Update background service if connected
            if (serviceBound && mediaPlaybackService != null) {
              val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""
              val currentUri = uri
              lifecycleScope.launch(Dispatchers.IO) {
                val thumbnail = currentUri?.let { extractThumbnailOrCoverArt(it) }
                withContext(Dispatchers.Main) {
                  MediaPlaybackService.thumbnail = thumbnail
                  mediaPlaybackService?.setMediaInfo(title = fileName, artist = artist, thumbnail = thumbnail)
                  miniPlayerStateManager.updateState(thumbnail = thumbnail)
                }
              }
            }
          }

          // Update recently played with the parsed video title, duration, and file size
          val filePath = when (uri.scheme) {
            "file" -> uri.path ?: uri.toString()
            "content" -> {
              contentResolver.query(
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

          // Get duration and file size from MPV
          val updatedDuration = runCatching {
            (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
          }.getOrDefault(0L)

          val updatedFileSize = runCatching {
            // Try multiple properties to get file size
            MPVLib.getPropertyDouble("file-size")?.toLong()
              ?: MPVLib.getPropertyDouble("stream-end")?.toLong()
              ?: 0L
          }.getOrDefault(0L)

          // Get video resolution from MPV
          val updatedWidth = runCatching {
            MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
          }.getOrDefault(0)

          val updatedHeight = runCatching {
            MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
          }.getOrDefault(0)

          // Update metadata in history if this session is an internal launch or if excludeExternalPlaybackFromHistory is disabled
          val isInternalLaunch = intent.getBooleanExtra("internal_launch", false)
          val isShare = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
          val rawLaunchSource = intent.getStringExtra("launch_source")
          val isExternalOrShare = !isInternalLaunch || isShare || rawLaunchSource == "share" || rawLaunchSource == "open_file" || rawLaunchSource == "external"
          if (!isExternalOrShare || !advancedPreferences.excludeExternalPlaybackFromHistory.get()) {
            viewModel.historyManager.updateCurrentMediaMetadata(fileName)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching network stream title", e)
      }
    }
  }

  /**
   * Applies all saved subtitle preferences when a file is loaded.
   * This ensures subtitle customizations (font, colors, position, etc.) persist across videos.
   */
  private fun applySubtitlePreferences() {
    // Typography settings
    safeSetPropertyString("sub-font", subtitlesPreferences.font.get())
    safeSetPropertyString("secondary-sub-font", subtitlesPreferences.font.get())
    MPVLib.setPropertyInt("sub-font-size", subtitlesPreferences.fontSize.get())
    MPVLib.setPropertyBoolean("sub-bold", subtitlesPreferences.bold.get())
    MPVLib.setPropertyBoolean("sub-italic", subtitlesPreferences.italic.get())
    safeSetPropertyString("sub-justify", subtitlesPreferences.justification.get().value)
    safeSetPropertyString("sub-border-style", subtitlesPreferences.borderStyle.get().value)
    MPVLib.setPropertyInt("sub-outline-size", subtitlesPreferences.borderSize.get())
    MPVLib.setPropertyInt("sub-shadow-offset", subtitlesPreferences.shadowOffset.get())

    // Color settings
    safeSetPropertyString("sub-color", subtitlesPreferences.textColor.get().toColorHexString())
    safeSetPropertyString("sub-border-color", subtitlesPreferences.borderColor.get().toColorHexString())
    safeSetPropertyString("sub-back-color", subtitlesPreferences.backgroundColor.get().toColorHexString())

    // Miscellaneous settings
    val overrideAssSubs = subtitlesPreferences.overrideAssSubs.get()
    safeSetPropertyString("sub-ass-override", if (overrideAssSubs) "force" else "scale")
    safeSetPropertyString("secondary-sub-ass-override", "force")

    val scaleByWindow = subtitlesPreferences.scaleByWindow.get()
    val scaleValue = if (scaleByWindow) "yes" else "no"
    safeSetPropertyString("sub-scale-by-window", scaleValue)
    safeSetPropertyString("sub-use-margins", scaleValue)

    MPVLib.setPropertyFloat("sub-scale", subtitlesPreferences.subScale.get())
    MPVLib.setPropertyInt("sub-pos", subtitlesPreferences.subPos.get())
    MPVLib.setPropertyFloat("secondary-sub-scale", subtitlesPreferences.secondarySubScale.get())
    MPVLib.setPropertyInt("secondary-sub-pos", subtitlesPreferences.secondarySubPos.get())

    Log.d(TAG, "Applied subtitle preferences")
  }

  /**
   * Asynchronously loads web subtitles extracted by yt-dlp on a background IO thread.
   * Prioritizes preferred language and system locale, and paces requests to prevent
   * blocking libmpv's command queue or overwhelming network resources.
   */
  private fun loadWebSubtitlesAsync(subs: Map<String, String>) {
    webSubtitlesJob?.cancel()
    webSubtitlesJob = lifecycleScope.launch(Dispatchers.IO) {
      val preferredLanguages = subtitlesPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
      val systemLang = Locale.getDefault().language.lowercase()

      // Sort subtitles so preferred languages appear first
      val sortedSubs = subs.entries.sortedByDescending { (lang, _) ->
        val cleanLang = lang.lowercase()
        when {
          preferredLanguages.any { pref -> cleanLang == pref || cleanLang.startsWith("$pref-") || cleanLang.startsWith("${pref}_") } -> 2
          cleanLang == systemLang || cleanLang.startsWith("$systemLang-") || cleanLang.startsWith("${systemLang}_") -> 1
          else -> 0
        }
      }

      for ((lang, subUrl) in sortedSubs) {
        if (!isActive || !mpvInitialized || player.isExiting || isFinishing) break

        val locale = runCatching { Locale.forLanguageTag(lang) }.getOrNull()
        val friendlyTitle = locale?.getDisplayName(Locale.getDefault())
          ?.takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }
          ?: locale?.displayLanguage?.takeIf { it.isNotBlank() }
          ?: lang

        runCatching {
          MPVLib.command("sub-add", subUrl, "auto", friendlyTitle, lang)
        }.onFailure { e ->
          Log.w(TAG, "Failed to add web subtitle for $lang: ${e.message}")
        }
        delay(40)
      }
    }
  }

  /**
   * Helper extension function to convert Int color to hex string for MPV
   */
  @OptIn(ExperimentalStdlibApi::class)
  private fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

  /**
   * Saves the current playback state to the database.
   *
   * Uses lifecycleScope to save state; cancels previous pending saves.
   *
   * @param mediaTitle The title of the media being played
   */
  internal fun saveVideoPlaybackState(mediaTitle: String, isEof: Boolean = false) {
    playbackStateController.saveVideoPlaybackState(mediaTitle, isEof)
  }

  /**
   * Loads and applies saved playback state from the database.
   *
   * @param mediaTitle The title of the media being played
   * @return true if saved state was found and applied, false otherwise
   */
  internal suspend fun loadVideoPlaybackState(mediaTitle: String): Boolean {
    return playbackStateController.loadVideoPlaybackState(mediaTitle)
  }

  /**
   * Saves the currently playing file to recently played history.
   *
   * Handles various URI schemes and infers launch source.
   */
  // ==================== Intent and Result Management ====================

  /**
   * Sets the result intent with current playback position and duration.
   * Called when activity is finishing to return data to caller.
   */
  private fun setReturnIntent() {
    intentHandler.setReturnIntent()
  }

  /**
   * Handles new intents to load a different file without recreating the activity.
   *
   * @param intent The new intent
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intentHandler.handleNewIntent(intent)
  }

  // ==================== Picture-in-Picture Management ====================

  /**
   * Called when Picture-in-Picture mode changes.
   * Updates UI visibility and window configuration.
   *
   * @param isInPictureInPictureMode true if entering PiP, false if exiting
   * @param newConfig The new configuration
   */
  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    pipController.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
  }

  /**
   * Configures window for Picture-in-Picture mode.
   * Shows system UI and navigation bars.
   */
  private fun enterPipUIMode() {
    pipController.enterPipUIMode()
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
  private fun exitPipUIMode() {
    pipController.exitPipUIMode()
  }

  /**
   * Enters Picture-in-Picture mode and hides all overlay controls.
   */
  fun enterPipModeHidingOverlay() {
    pipController.enterPipModeHidingOverlay()
  }

  // ==================== Orientation Management ====================

  /**
   * Sets the screen orientation based on user preferences.
   *
   * @param width Optional video width from metadata to set orientation before video loads
   * @param height Optional video height from metadata to set orientation before video loads
   * @param rotation Optional video rotation from metadata to correctly determine aspect ratio
   */
  internal fun setOrientation(width: Int = -1, height: Int = -1, rotation: Int = 0) {
    orientationController.setOrientation(width, height, rotation)
  }

  // ==================== Key Event Handling ====================

  /**
   * Handles hardware key down events for player control.
   * Supports D-pad navigation, media keys, and volume controls.
   *
   * @param keyCode The key code
   * @param event The key event
   * @return true if event was handled, false otherwise
   */
  override fun onKeyDown(
    keyCode: Int,
    event: KeyEvent?,
  ): Boolean {
    return keyEventHandler.onKeyDown(keyCode, event) {
      super.onKeyDown(keyCode, event)
    }
  }

  /**
   * Handles hardware key up events for player control.
   *
   * @param keyCode The key code
   * @param event The key event
   * @return true if event was handled, false otherwise
   */
  override fun onKeyUp(
    keyCode: Int,
    event: KeyEvent?,
  ): Boolean {
    return keyEventHandler.onKeyUp(keyCode, event) {
      super.onKeyUp(keyCode, event)
    }
  }

  // ==================== System UI Management ====================

  /**
   * Restores system UI to normal state (shows status and navigation bars).
   * Called when finishing the activity to return to normal Android UI.
   */

  // ==================== MediaSession ====================

  /**
   * Initializes MediaSession for integration with system media controls.
   * Supports Android Auto, Wear OS, Bluetooth controls, and notification controls.
   */
  private fun setupMediaSession() {
    mediaSessionController.setup()
  }

  /**
   * Updates MediaSession playback state (playing/paused).
   *
   * @param isPlaying true if currently playing, false if paused
   */
  private fun updateMediaSessionPlaybackState(isPlaying: Boolean) {
    mediaSessionController.updatePlaybackState(isPlaying)
  }

  /**
   * Updates MediaSession metadata (title, duration, etc.).
   *
   * @param title The media title
   * @param durationMs The media duration in milliseconds
   */
  internal fun updateMediaSessionMetadata(
    title: String,
    durationMs: Long,
  ) {
    mediaSessionController.updateMetadata(title, durationMs)
  }

  /**
   * Releases MediaSession resources.
   * Called during activity cleanup.
   */
  private fun releaseMediaSession() {
    mediaSessionController.release()
  }

  // ==================== Background Playback Service ====================

  /**
   * Starts the background playback service and binds to it.
   */
  private fun startBackgroundPlayback() {
    backgroundPlaybackController.startBackgroundPlayback(
      fileName = fileName,
      isReady = isReady,
    )
    lifecycleScope.launch(Dispatchers.IO) {
      val currentUri = viewModel.playlistManager.getCurrentUri() ?: extractUriFromIntent(intent)
      val thumbnail = currentUri?.let { extractThumbnailOrCoverArt(it) }

      MediaPlaybackService.thumbnail = thumbnail
      miniPlayerStateManager.updateState(thumbnail = thumbnail)
    }
  }

  /**
   * Stops the background playback service and unbinds from it.
   */
  internal fun endBackgroundPlayback() {
    backgroundPlaybackController.endBackgroundPlayback()
  }

  /**
   * Manually triggers background playback when the user clicks the background playback button.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  fun triggerBackgroundPlayback() {
    backgroundPlaybackController.triggerBackgroundPlayback(
      fileName = fileName,
      isReady = isReady,
      onRestoreSystemUI = { restoreSystemUI() },
      onStartService = { startBackgroundPlayback() },
    )
  }

  /**
   * Disables video decoding to save battery when moving to background playback.
   */
  private fun disableVideoForBackground() {
    backgroundPlaybackController.disableVideoForBackground(
      isReady = isReady,
      fileName = fileName,
      hasVideoTrack = { hasVideoTrack() },
    )
  }

  /**
   * Restores video decoding when returning from background playback.
   */
  internal fun enableVideoAfterBackground() {
    backgroundPlaybackController.enableVideoAfterBackground(
      mpvInitialized = mpvInitialized,
      onSetPropertyString = { prop, value -> safeSetPropertyString(prop, value) },
    )
  }

  /**
   * Checks if the currently loaded media has a valid video track.
   */
  private fun hasVideoTrack(): Boolean {
    if (!mpvInitialized || player.isExiting) return false
    val w = runCatching { MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0 }.getOrDefault(0)
    if (w > 0) return true

    val trackCount = runCatching { MPVLib.getPropertyInt("track-list/count") ?: 0 }.getOrDefault(0)
    for (i in 0 until trackCount) {
      val type = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull()
      val isAlbumArt = runCatching { MPVLib.getPropertyBoolean("track-list/$i/albumart") }.getOrNull()
      if (type == "video" && isAlbumArt != true) {
        return true
      }
    }
    return false
  }

  /**
   * Checks if current loaded media is audio-only.
   */
  internal fun isCurrentMediaAudio(): Boolean {
    val path = parsePathFromIntent(intent)
    if (path != null) {
      val file = File(path)
      if (file.exists()) {
        if (xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(file)) {
          return true
        }
        if (xyz.mpv.rex.utils.storage.FileTypeUtils.isVideoFile(file)) {
          return false
        }
      }
    }
    return !hasVideoTrack()
  }

  private suspend fun createVideoForUri(uri: Uri): Video = withContext(Dispatchers.IO) {
    val path = when (uri.scheme) {
      "file" -> uri.path ?: uri.toString()
      "content" -> {
        val resolvedPath = runCatching {
          contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
              if (idx != -1) cursor.getString(idx) else null
            } else null
          }
        }.getOrNull()
        resolvedPath ?: uri.toString()
      }
      else -> uri.toString()
    }

    val file = if (path.startsWith("/")) File(path) else null
    val size = file?.length() ?: 0L
    val rawModified = file?.lastModified() ?: 0L
    val dateModified = if (rawModified > 100_000_000_000L) rawModified / 1000L else rawModified
    val isAudio = FileTypeUtils.isAudioFile(file ?: File(path))
    val name = extractFileNameFromUri(uri) ?: uri.lastPathSegment ?: "Media"

    val cachedMeta = file?.let { metadataCache.getOrExtractMetadata(it, uri, name) }
    val duration = cachedMeta?.durationMs ?: (runCatching { MPVLib.getPropertyDouble("duration")?.times(1000) }.getOrNull())?.toLong() ?: 0L

    Video(
      id = path.hashCode().toLong(),
      title = name,
      displayName = name,
      path = path,
      uri = uri,
      duration = duration,
      durationFormatted = MediaFormatter.formatDuration(duration),
      size = size,
      sizeFormatted = MediaFormatter.formatFileSize(size),
      dateModified = dateModified,
      dateAdded = 0,
      mimeType = if (isAudio) "audio/*" else "video/*",
      bucketId = file?.parent ?: "",
      bucketDisplayName = file?.parentFile?.name ?: "",
      width = cachedMeta?.width ?: 0,
      height = cachedMeta?.height ?: 0,
      fps = cachedMeta?.fps ?: 0f,
      resolution = "",
      isAudio = isAudio,
    )
  }

  internal suspend fun getCachedThumbnailForUri(uri: Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    val key = uri.toString()
    uriThumbnailCache.get(key)?.let { if (!it.isRecycled && !isMostlySolidThumbnail(it)) return@withContext it }
    val video = createVideoForUri(uri)
    thumbnailRepository.getCachedThumbnail(video, 256, 256)?.let {
      if (!it.isRecycled && !isMostlySolidThumbnail(it)) {
        uriThumbnailCache.put(key, it)
        return@withContext it
      }
    }

    if (uri.scheme == "file" || uri.scheme == "content") {
      runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
          if (uri.scheme == "file") {
            val path = uri.path
            if (path != null && File(path).exists()) {
              retriever.setDataSource(path)
            } else {
              retriever.setDataSource(this@PlayerActivity, uri)
            }
          } else {
            retriever.setDataSource(this@PlayerActivity, uri)
          }
          val picture = retriever.embeddedPicture
          if (picture != null) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
            if (bitmap != null && !bitmap.isRecycled) {
              uriThumbnailCache.put(key, bitmap)
              return@withContext bitmap
            }
          }
        } finally {
          runCatching { retriever.release() }
        }
      }
    }
    null
  }

  /**
   * Extracts embedded album cover art for audio files or a video frame thumbnail for video files.
   * Leverages [ThumbnailRepository] to ensure the exact same thumbnails are used in miniplayer as in the file browser.
   */
  internal suspend fun extractThumbnailOrCoverArt(uri: Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    val cacheKey = uri.toString()
    uriThumbnailCache.get(cacheKey)?.let {
      if (!it.isRecycled && !isMostlySolidThumbnail(it)) return@withContext it
    }

    runCatching {
      val video = createVideoForUri(uri)

      // 1. Primary: Use ThumbnailRepository (same repository as file browser)
      val repoThumbnail = thumbnailRepository.getThumbnail(video, 256, 256)
      if (repoThumbnail != null && !repoThumbnail.isRecycled && !isMostlySolidThumbnail(repoThumbnail)) {
        uriThumbnailCache.put(cacheKey, repoThumbnail)
        return@withContext repoThumbnail
      }

      // 2. Fallback for audio or embedded cover art via MediaMetadataRetriever
      val retriever = android.media.MediaMetadataRetriever()
      try {
        if (uri.scheme == "file") {
          val path = uri.path
          if (path != null && File(path).exists()) {
            retriever.setDataSource(path)
          } else {
            retriever.setDataSource(this@PlayerActivity, uri)
          }
        } else {
          retriever.setDataSource(this@PlayerActivity, uri)
        }

        val picture = retriever.embeddedPicture
        if (picture != null) {
          val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
          if (bitmap != null && !bitmap.isRecycled) {
            uriThumbnailCache.put(cacheKey, bitmap)
            return@withContext bitmap
          }
        }

        if (!video.isAudio) {
          val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
          val seekUs = if (durationMs > 2000L) 1_000_000L else 0L
          val frame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { retriever.getScaledFrameAtTime(seekUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256) }.getOrNull()
          } else null
          val fallbackFrame = frame ?: retriever.getFrameAtTime(seekUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          if (fallbackFrame != null && !fallbackFrame.isRecycled && !isMostlySolidThumbnail(fallbackFrame)) {
            uriThumbnailCache.put(cacheKey, fallbackFrame)
            return@withContext fallbackFrame
          }

          if (hasVideoTrack() && MPVLib.getPropertyString("vid") != "no") {
            val mpvThumb = runCatching { MPVLib.grabThumbnail(256) }.getOrNull()
            if (mpvThumb != null && !mpvThumb.isRecycled && !isMostlySolidThumbnail(mpvThumb)) {
              uriThumbnailCache.put(cacheKey, mpvThumb)
              return@withContext mpvThumb
            }
          }
        }
      } finally {
        runCatching { retriever.release() }
      }
      null
    }.getOrNull()
  }



  // ==================== PlayerHost ====================
  override val context: Context
    get() = this
  override val windowInsetsController: WindowInsetsControllerCompat
    get() = systemUiController.windowInsetsController
  override val hostWindow: android.view.Window
    get() = window
  override val hostWindowManager: WindowManager
    get() = windowManager
  override val hostContentResolver: android.content.ContentResolver
    get() = contentResolver
  override val audioManager: AudioManager
    get() = getSystemService(AUDIO_SERVICE) as AudioManager
  override var hostRequestedOrientation: Int
    get() = requestedOrientation
    set(value) {
      requestedOrientation = value
      orientationController.isUserOverridden = true
    }

  // ==================== ServiceListener ====================

   override fun onNextRequested() {
     viewModel.handleMediaNext()
   }

   override fun onPreviousRequested() {
     viewModel.handleMediaPrevious()
   }

   override fun onRepeatToggled() {
     viewModel.cycleRepeatMode()
     mediaPlaybackService?.updateMediaSession()
   }

   override fun onShuffleToggled() {
     viewModel.toggleShuffle()
     mediaPlaybackService?.updateMediaSession()
   }
  // ==================== Playlist Management ====================

  /**
   * Check if there's a next video in the playlist
   */
  fun hasNext(): Boolean = playlistLoader.hasNext()

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean = playlistLoader.hasPrevious()

  fun updateMiniPlayerPlaylistState() = playlistLoader.updateMiniPlayerPlaylistState()

  /**
   * Play the next video in the playlist
   */
  fun playNext() = playlistLoader.playNext()

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() = playlistLoader.playPrevious()

  /**
   * Load a playlist item by index
   */
  fun loadPlaylistItem(index: Int) = playlistLoader.loadPlaylistItem(index)

  internal fun switchActiveNetworkStream(playableUri: String?) = playlistLoader.switchActiveNetworkStream(playableUri)

  internal fun getFastDurationMsForUri(uri: Uri): Long = playlistLoader.getFastDurationMsForUri(uri)

  fun getTitleForControls(): String = playlistLoader.getTitleForControls()

  internal fun isCurrentStreamM3U(): Boolean = playlistLoader.isCurrentStreamM3U()

  internal fun isUriM3U(uriStr: String): Boolean = playlistLoader.isUriM3U(uriStr)

  internal fun isUriM3U(uri: Uri): Boolean = playlistLoader.isUriM3U(uri)

  internal fun loadM3uPlaylistOrPlayDirectly(uriStr: String) = playlistLoader.loadM3uPlaylistOrPlayDirectly(uriStr)

  internal suspend fun saveRecentlyPlayedForUri(uri: Uri, name: String) = playlistLoader.saveRecentlyPlayedForUri(uri, name)

  internal fun getMediaIdentifier(intent: Intent, fileName: String): String = playlistLoader.getMediaIdentifier(intent, fileName)

  internal fun getMediaIdentifierFromUri(uri: Uri, fileName: String): String = playlistLoader.getMediaIdentifierFromUri(uri, fileName)

  internal fun autoGeneratePlaylist(intent: Intent) = playlistLoader.autoGeneratePlaylist(intent)

  fun isCurrentPlaylistM3U(): Boolean = playlistLoader.isCurrentPlaylistM3U()

  fun getPlaylistWindowOffset(): Int = playlistLoader.getPlaylistWindowOffset()


  companion object {
    /**
     * Intent action used to return playback result data to the calling activity.
     */
    private const val RESULT_INTENT = "xyz.mpv.rex.ui.player.PlayerActivity.result"

    /**
     * Constant for "brightness not set".
     */
    private const val BRIGHTNESS_NOT_SET = -1f

    /**
     * Constant used when playback position is not set.
     */
    private const val POSITION_NOT_SET = 0

    /**
     * Maximum volume for MPV in percent.
     */
    private const val MAX_MPV_VOLUME = 100

    /**
     * Milliseconds-to-seconds conversion factor.
     */
    private const val MILLISECONDS_TO_SECONDS = 1000

    /**
     * Factor to divide subtitle and audio delays to convert from ms to seconds.
     */
    private const val DELAY_DIVISOR = 1000.0

    /**
     * Default playback speed (1.0 = normal).
     */
    private const val DEFAULT_PLAYBACK_SPEED = 1.0

    /**
     * Default subtitle speed (1.0 = normal).
     */
    private const val DEFAULT_SUB_SPEED = 1.0

    /**
     * General tag for logging from PlayerActivity.
     */
    const val TAG = "mpvex"

    @Volatile
    var activeInstance: PlayerActivity? = null

    fun finishBackgroundInstance() {
      activeInstance?.let { activity ->
        if (activity.isManualBackgroundPlayback || activity.isInBackgroundPlayback) {
          Log.d(TAG, "Finishing background PlayerActivity to hand MPV back to headless controller")
          activity.finish()
        }
      }
    }
  }
}
