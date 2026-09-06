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
import androidx.compose.ui.Modifier
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
import xyz.mpv.rex.ui.theme.MpvexPlayerTheme
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.HttpUtils
import xyz.mpv.rex.utils.media.SubtitleOps
import xyz.mpv.rex.utils.media.M3UParser
import xyz.mpv.rex.utils.media.FolderPlaylistOps
import xyz.mpv.rex.utils.media.M3UParseResult
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.domain.thumbnail.isMostlySolidThumbnail
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.ui.player.SingleActionGesture
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

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
  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> {
    PlayerViewModelProviderFactory(this)
  }

  /**
   * Binding for the player layout.
   */
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }

  /**
   * Observer for MPV events.
   */
  private val playerObserver by lazy { PlayerObserver(this) }

  // ==================== Dependency Injection ====================

  /**
   * Repository for managing playback state.
   */
  private val playbackStateRepository: PlaybackStateRepository by inject()

  /**
   * Repository for managing playlists.
   */
  private val playlistRepository: xyz.mpv.rex.database.repository.PlaylistRepository by inject()

  /**
   * Preferences for player settings.
   */
  private val playerPreferences: PlayerPreferences by inject()

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
  private val subtitlesPreferences: SubtitlesPreferences by inject()

  /**
   * Preferences for advanced settings.
   */
  private val advancedPreferences: AdvancedPreferences by inject()

  /**
   * Preferences for browser settings.
   */
  private val browserPreferences: BrowserPreferences by inject()

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
  private val metadataCache: VideoMetadataCacheRepository by inject()

  /**
   * Preferences for decoder settings (hardware dec, gpu-next, shaders).
   */
  private val decoderPreferences: DecoderPreferences by inject()

  /**
   * Manager for hdr-toys shaders.
   */
  private val hdrToysManager: HdrToysManager by inject()
  private val miniPlayerStateManager: MiniPlayerStateManager by inject()
  private val headlessPlaybackController: HeadlessPlaybackController by inject()
  private val thumbnailRepository: ThumbnailRepository by inject()
  private val remoteClient: xyz.mpv.rex.jellyfin.remote.JellyfinRemoteClient by inject()
  private var jellyfinExternalInfo: xyz.mpv.rex.jellyfin.JellyfinExternalHelper.ExternalInfo? = null
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
  private var fileName = ""

  /**
   * Unique identifier for the current media, used for saving/loading playback state.
   * For network streams, this includes a hash of the URI to ensure uniqueness.
   */
  private var mediaIdentifier = ""

  private var activeNetworkStreamId: String? = null

  /**
   * Helper for managing Picture-in-Picture mode.
   */
  private lateinit var pipHelper: MPVPipHelper

  private var isReady = false // Single flag: true when video loaded and ready
  private var isOrientationRestored = false // Track if orientation was restored from DB
  private var isUserFinishing = false
  private var wasInPipMode = false // Track if activity was in PiP mode
  private var isManualBackgroundPlayback = false // Track manual background playback trigger
  private var noisyReceiverRegistered = false
  private var mpvInitialized = false // Track MPV initialization state
  private var savePlaybackStateJob: kotlinx.coroutines.Job? = null // Track ongoing save job
  private var pendingIntentExtras = false // Track if intent extras should be applied to next loaded file
  private var lastVid = -1 // Track video track for background playback optimization
  private var isInBackgroundPlayback = false // Track if we are currently in background playback mode
  private var inheritedNativeSession = false // MPV ownership came from HeadlessPlaybackController

  @Volatile private var needsAspectReapply = false
  @Volatile
  private var isPlaybackStateLoaded = false // Track if aspect ratio needs to be reapplied after video is ready (for Video/Smart orientation modes)

  // ==================== Background Playback ====================

  /**
   * Reference to the background playback service.
   */
  private var mediaPlaybackService: MediaPlaybackService? = null

  /**
   * Tracks whether we're currently bound to the background playback service.
   */
  private var serviceBound = false

  // ==================== MediaSession ====================

  /**
   * MediaSession for integration with system media controls, Android Auto, and Wear OS.
   */
  private lateinit var mediaSession: MediaSession

  /**
   * Tracks whether MediaSession has been successfully initialized.
   */
  private var mediaSessionInitialized = false

  /**
   * Builder for MediaSession playback states.
   */
  private lateinit var playbackStateBuilder: PlaybackState.Builder

  // ==================== Audio Focus ====================

  /**
   * Audio focus request for API 26+.
   */
  private var audioFocusRequest: AudioFocusRequest? = null

  /**
   * Callback to restore audio focus after it's been lost and regained.
   */
  private var restoreAudioFocus: () -> Unit = {}

  // ==================== Broadcast Receivers ====================

  /**
   * Receiver for handling noisy audio events.
   */
  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        context: Context?,
        intent: Intent?,
      ) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          viewModel.pause()
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }
    }

  /**
   * Listener for audio focus changes.
   */
  private val audioFocusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
          -> {
          // Save current state to restore later
          val oldRestore = restoreAudioFocus
          val wasPlayerPaused = viewModel.paused ?: false
          viewModel.pause()
          restoreAudioFocus = {
            oldRestore()
            if (!wasPlayerPaused) viewModel.unpause()
          }
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // Lower volume temporarily
          MPVLib.command("multiply", "volume", "0.5")
          restoreAudioFocus = {
            MPVLib.command("multiply", "volume", "2")
          }
        }

        AudioManager.AUDIOFOCUS_GAIN -> {
          // Restore previous audio state
          restoreAudioFocus()
          restoreAudioFocus = {}
        }

        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
          Log.d(TAG, "Audio focus request failed")
        }
      }
    }

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
    viewModel.setupScreenStateReceiver()

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
        if (playerPreferences.savePositionOnQuit.get() || playerPreferences.resumePlaybackMode.get() != ResumePlaybackMode.Never) {
          runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        player.playFile(playableUri)
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
    miniPlayerStateManager.clearState()
    finish()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupPlayerControls() {
    binding.controls.setContent {
      MpvexPlayerTheme {
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

  /**
   * Initializes the Picture-in-Picture helper.
   */
  private fun setupPipHelper() {
    pipHelper = MPVPipHelper(activity = this, mpvView = player)
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      runCatching {
        safeSetPropertyString(it.property, it.value)
      }.onFailure { e ->
        Log.e(TAG, "Error setting audio channels: ${it.property}=${it.value}", e)
      }
    }

    if (!serviceBound) {
      audioFocusRequest =
        AudioFocusRequest
          .Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes
              .Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
              .build(),
          ).setOnAudioFocusChangeListener(audioFocusChangeListener)
          .setAcceptsDelayedFocusGain(true)
          .setWillPauseWhenDucked(true)
          .build()
      requestAudioFocus()
    }
  }

  /**
   * @return true if audio focus was granted immediately, false otherwise
   */
  override fun requestAudioFocus(): Boolean {
    val req = audioFocusRequest ?: return false
    val result = audioManager.requestAudioFocus(req)
    return when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        restoreAudioFocus = {}
        true
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        restoreAudioFocus = { requestAudioFocus() }
        false
      }

      else -> {
        restoreAudioFocus = {}
        false
      }
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // Enter PIP mode when user presses home button if auto PIP is enabled
    if (playerPreferences.autoPiPOnNavigation.get() && isReady && !isFinishing) {
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
      savePlaybackStateJob?.let { job ->
        if (job.isActive) {
          Log.d(TAG, "Waiting for save playback state job to complete (bounded timeout)...")
          runCatching {
            kotlinx.coroutines.runBlocking {
              kotlinx.coroutines.withTimeoutOrNull(200) {
                job.join()
              }
            }
          }
          Log.d(TAG, "Save playback state job finished or timed out safely")
        }
      }

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
    }.onFailure { e ->
      Log.e(TAG, "Error during onDestroy", e)
    }

    if (activeInstance === this) {
      activeInstance = null
    }

    super.onDestroy()
  }

  private fun cleanupMPV() {
    if (!mpvInitialized) return

    // Don't cleanup MPV if we're doing background playback
    if (!isFinishing || isManualBackgroundPlayback) return

    player.isExiting = true

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
    if (restoreAudioFocus != {}) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      restoreAudioFocus = {}
    }
  }

  private fun cleanupAudio() {
    abandonAudioFocus()
  }

  private fun cleanupReceivers() {
    if (noisyReceiverRegistered) {
      runCatching {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPause() {
    viewModel.isActivityResumed = false
    runCatching {
      val isInPip = isInPictureInPictureMode
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
          viewModel.wasPlayingBeforePause = !(viewModel.paused ?: true)
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
    runCatching {
      if (!isManualBackgroundPlayback) {
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
    runCatching {
      if (!isManualBackgroundPlayback) {
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
    viewModel.isActivityStarted = false
    runCatching {
      pipHelper.onStop()
      saveVideoPlaybackState(fileName)

      if (noisyReceiverRegistered) {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }

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

    super.onStop()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onStart() {
    super.onStart()
    viewModel.isActivityStarted = true

    runCatching {
      setupWindowFlags()
      setupSystemUI()

      // Restore video if it was disabled for background playback
      enableVideoAfterBackground()

      if (!noisyReceiverRegistered) {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
        noisyReceiverRegistered = true
      }

      if (playerPreferences.rememberBrightness.get()) {
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
    pipHelper.updatePictureInPictureParams()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupSystemUI() {
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    // Set status bar color for when it will be shown (with controls)
    if (playerPreferences.showSystemStatusBar.get()) {
      window.statusBarColor = android.graphics.Color.parseColor("#80000000") // Semi-transparent black
    }

    // Always start with status bar hidden - it will show when controls are shown
    try {
      windowInsetsController.apply {
        hide(WindowInsetsCompat.Type.statusBars())
        hide(WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to setup system UI insets", e)
    }

    // Don't use LOW_PROFILE if we plan to show status bar with controls
    // LOW_PROFILE causes only icons to show without background
    @Suppress("DEPRECATION")
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        if (playerPreferences.showSystemStatusBar.get()) 0 else View.SYSTEM_UI_FLAG_LOW_PROFILE
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun restoreSystemUI() {
    // Clear flags first for immediate effect
    // window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Set cutout mode before showing bars for smoother transition
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT

    // Update window insets configuration
    // WindowCompat.setDecorFitsSystemWindows(window, true)

    // Restore default behavior and show bars in one go
    try {
      windowInsetsController.apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        show(WindowInsetsCompat.Type.systemBars())
        show(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to restore system UI insets", e)
    }
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
    viewModel.isActivityResumed = true
    enableVideoAfterBackground()
    updateVolume()
    viewModel.handlePendingResumeOnUnlock()
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
    if (!extras.containsKey("subs")) return

    val subList = Utils.getParcelableArray<Uri>(extras, "subs")
    val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

    lifecycleScope.launch(Dispatchers.Default) {
      for (suburi in subList) {
        val subfile = suburi.resolveUri(this@PlayerActivity) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }
  }

  /**
   * Sets HTTP headers from intent extras for network playback.
   *
   * This method checks the intent extras for the "headers" key, which contains a list
   * of HTTP headers to set. It sets the User-Agent header and any additional headers
   * specified in the list.
   *
   * Also automatically adds Referer header based on the URL origin if not already provided.
   *
   * @param extras Bundle containing HTTP headers
   */
  /**
   * Safe wrapper for MPVLib.setPropertyString to prevent native crashes (SIGSEGV).
   * Ensures that the property name and value are not null or blank before passing to JNI.
   */
  private fun safeSetPropertyString(property: String, value: String?) {
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
    // Build header map starting with auto-detected referer
    val headerMap = mutableMapOf<String, String>()

    // Automatically extract and set referer domain from the URL
    val uri = extractUriFromIntent(intent)
    if (uri != null && HttpUtils.isNetworkStream(uri)) {
      HttpUtils.extractRefererDomain(uri)?.let { referer ->
        headerMap["Referer"] = referer
        Log.d(TAG, "Auto-detected Referer: $referer")
      }
    }

    // Process headers from extras (these can override the auto-detected referer)
    extras?.getStringArray("headers")?.let { headers ->
      if (headers.size < 2) return@let

      // Handle User-Agent if it's the first pair
      if (headers[0]?.startsWith("User-Agent", ignoreCase = true) == true) {
        headers[1]?.let { ua ->
          safeSetPropertyString("user-agent", ua)
        }
      }

      // Safe iteration in pairs to avoid null/unpaired crashes
      headers.asSequence()
        .chunked(2)
        .filter { it.size == 2 && !it[0].isNullOrBlank() && !it[1].isNullOrBlank() }
        .forEach { (key, value) ->
          headerMap[key!!] = value!!
        }
    }

    // Set all headers in MPV
    if (headerMap.isNotEmpty()) {
      val headersString = headerMap
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
        .joinToString(",")

      safeSetPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers: $headersString")
    } else {
      safeSetPropertyString("http-header-fields", "")
      Log.d(TAG, "Cleared HTTP headers")
    }
  }
  /**
   * Sets HTTP headers for a specific URI (used for playlist items).
   * Automatically extracts and sets the Referer header based on the URI origin.
   *
   * @param uri The URI to extract referer from and set headers for
   */
  private fun setHttpHeadersForUri(uri: Uri) {
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

      safeSetPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers for playlist item: $headersString")
    } else {
      safeSetPropertyString("http-header-fields", "")
      Log.d(TAG, "Cleared HTTP headers for playlist item")
    }
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
  private fun parsePathFromIntent(intent: Intent): String? =
    when (intent.action) {
      Intent.ACTION_VIEW -> {
        val data = intent.data
        if (data?.scheme.equals("smb", ignoreCase = true)) {
          intent.dataString ?: data.toString()
        } else {
          data?.resolveUri(this)
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
  private fun parsePathFromSendIntent(intent: Intent): String? =
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      uri?.resolveUri(this@PlayerActivity)
    } else {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
        val uri = text.trim().toUri()
        if (uri.isHierarchical && !uri.isRelative) {
          uri.resolveUri(this)
        } else {
          null
        }
      }
    }

  /**
   * Extracts and resolves the file name from the intent.
   *
   * @param intent The intent containing the file URI
   * @return The display name of the file, or empty string if not found
   */
  private fun getFileName(intent: Intent): String {
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
  private fun extractFileNameFromUri(uri: Uri): String {
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
   * Gets the display title for a playlist item URI.
   *
   * @param uri The URI to get the title for
   * @return The display name/title of the file
   */
  internal fun getPlaylistItemTitle(uri: Uri): String {
    // Try content resolver first for content:// URIs
    getDisplayNameFromUri(uri)?.let { return it }

    // Extract filename from URL/URI
    return extractFileNameFromUri(uri)
  }

  /**
   * Plays a playlist item by index.
   *
   * @param index The index of the playlist item to play
   */
  internal fun playPlaylistItem(index: Int) {
    if (index >= 0 && index < viewModel.playlistManager.playlist.value.size) {
      loadPlaylistItem(index)
    }
  }

  /**
   * Extracts the URI from the intent based on intent type.
   *
   * @param intent The intent to extract URI from
   * @return The extracted URI, or null if not found
   */
  private fun extractUriFromIntent(intent: Intent): Uri? =
    if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
    } else {
      intent.data ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
  private fun getDisplayNameFromUri(uri: Uri): String? =
    runCatching {
      contentResolver
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
  private fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent)
      ?: (if (intent.action == Intent.ACTION_VIEW || intent.action == null) {
        FolderPlaylistOps.extractExternalPlaylist(intent)?.let { playlist ->
          playlist.items.getOrNull(playlist.initialIndex)?.let { firstUri ->
            if (firstUri.scheme.equals("smb", ignoreCase = true)) {
              firstUri.toString()
            } else {
              firstUri.resolveUri(this) ?: firstUri.toString()
            }
          }
        }
      } else null)
      ?: return null

    return when {
      uri.startsWith("content://") -> uri.toUri().openContentFd(this)
      uri.startsWith("smb://", ignoreCase = true) -> {
        runBlocking(Dispatchers.IO) {
          FolderPlaylistOps.resolveSmbUri(uri)?.url
        } ?: uri
      }
      else -> uri
    }
  }

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

  /**
   * Sets initial orientation synchronously from intent parameters or metadata cache
   * to avoid orientation jumps on activity launch or intent update.
   */
  private fun applyInitialOrientationFromIntent(targetIntent: Intent) {
    val orient = playerPreferences.orientation.get()
    if (orient != PlayerOrientation.Video && orient != PlayerOrientation.Smart) {
      setOrientation()
      return
    }

    // 1. Try saved orientation from intent extras (for Smart mode)
    val intentSavedOrientation = targetIntent.getIntExtra("saved_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    if (orient == PlayerOrientation.Smart && intentSavedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      requestedOrientation = intentSavedOrientation
      isOrientationRestored = true
      Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation $requestedOrientation from intent")
      return
    }

    // 2. Try dimensions from intent extras (for Video/Smart mode)
    val intentWidth = targetIntent.getIntExtra("width", -1)
    val intentHeight = targetIntent.getIntExtra("height", -1)
    val intentRotation = targetIntent.getIntExtra("rotation", 0)
    if (intentWidth > 0 && intentHeight > 0) {
      setOrientation(intentWidth, intentHeight, intentRotation)
      return
    }

    // 3. Fallback: try saved orientation from DB or metadata cache asynchronously
    val targetFileName = getFileName(targetIntent).ifBlank { targetIntent.data?.lastPathSegment ?: "Unknown Video" }
    lifecycleScope.launch(Dispatchers.IO) {
      if (orient == PlayerOrientation.Smart) {
        val state = playbackStateRepository.getVideoDataByTitle(targetFileName)
        if (state?.savedOrientation != null && state.savedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          withContext(Dispatchers.Main) {
            requestedOrientation = state.savedOrientation!!
            isOrientationRestored = true
            Log.d(TAG, "applyInitialOrientationFromIntent - Smart mode: using restored orientation $requestedOrientation from DB")
          }
          return@launch
        }
      }

      val path = parsePathFromIntent(targetIntent)
      if (path != null) {
        val file = File(path)
        if (file.exists() && !xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(file)) {
          val metadata = metadataCache.getOrExtractMetadata(file, targetIntent.data ?: "".toUri(), targetFileName)
          if (metadata != null && metadata.width > 0 && metadata.height > 0) {
            withContext(Dispatchers.Main) {
              setOrientation(metadata.width, metadata.height, metadata.rotation)
            }
          }
        }
      }
    }
  }

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
    when (property) {
      "video-params/w",
      "video-params/h" -> {
        // Safety check: don't access MPV during cleanup
        if (!mpvInitialized || player.isExiting || isFinishing) return

        val aspect = player.getVideoOutAspect()
        Log.d(TAG, "Video dimension changed: $property, aspect: $aspect")
        pipHelper.updatePictureInPictureParams()
        // Update orientation when video dimensions change (fixes Video orientation mode)
        if (playerPreferences.orientation.get() == PlayerOrientation.Video && aspect != null) {
          setOrientation()
        }

        // Re-apply Anime4K shaders (check for resolution limit)
        player.applyAnime4KShaders()

        // Re-check ambient stretch — handles portrait videos and new content
        viewModel.updateAmbientStretch()
      }
    }
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
    when (property) {
      "pause" -> {
        handlePauseStateChange(value)
        // Ensure isReady is set when playback starts
        if (!value && !isReady) {
          isReady = true
        }
      }
      "eof-reached" -> handleEndOfFile(value)
    }
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

        // Check if autoplay next video is enabled
        val autoplayEnabled = playerPreferences.autoplayNextVideo.get()

        if (hasNextItem && (autoplayEnabled || viewModel.shouldRepeatPlaylist())) {
          // Play next item in playlist
          playNext()
        } else {
          miniPlayerStateManager.clearState()
          if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
            finish()
          }
        }
      } else {
        // Single video playback (no playlist)
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
    when (property.substringBeforeLast("/")) {
      "user-data/mpvex" -> viewModel.handleLuaInvocation(property, value)
    }
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
    // Currently no MPVNode properties are handled
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
    // Handle Double properties
    when (property) {
      "video-params/aspect" -> {
        // Safety check: don't access MPV during cleanup
        if (!mpvInitialized || player.isExiting || isFinishing) return

        val aspect = player.getVideoOutAspect()
        Log.d(TAG, "video-params/aspect changed: $aspect")
        pipHelper.updatePictureInPictureParams()
        // Update orientation when video aspect ratio changes (fixes Video orientation mode)
        // BUT: Don't update if aspect is being overridden (stretch/custom aspect mode)
        // to prevent infinite orientation switching loop
        val aspectOverride = MPVLib.getPropertyDouble("video-aspect-override") ?: -1.0
        if (playerPreferences.orientation.get() == PlayerOrientation.Video && 
            aspect != null && 
            aspectOverride <= 0.0) {
          setOrientation()
        }
      }
    }
  }

  /**
   * Observer callback for MPV property changes (no value parameter).
   * Handles properties with no value parameter.
   *
   * @param property The property name that changed
   */
  internal fun onObserverEvent(property: String) {
    // Currently no properties use this signature
  }

  /**
   * Handles MPV core events such as file loaded and playback restart.
   *
   * Called by the player when critical playback events occur.
   *
   * @param eventId The MPV event ID
   */
  internal fun event(eventId: Int) {
    when (eventId) {
      MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
        viewModel.onFileStartLoading()
      }

      MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
        handleFileLoaded()
        isReady = true
      }

      MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
        player.isExiting = false
        if (!isReady) {
          isReady = true
        }
        if (needsAspectReapply) {
          needsAspectReapply = false
          runOnUiThread {
            viewModel.resetVisualPreferences()
          }
        }
      }
    }
  }

  /**
   * Handles the file loaded event from MPV.
   * Initializes playback state, loads saved playback data, restores custom settings,
   * applies user preferences, and sets up metadata and media session.
   */
  private fun handleFileLoaded() {
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
      if (viewModel.resumePrompt.value == null) {
        runCatching {
          MPVLib.setPropertyBoolean("pause", false)
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

    // Only set orientation if NOT in Video or Smart mode, or if orientation has not been determined yet
    val orientation = playerPreferences.orientation.get()
    if (orientation != PlayerOrientation.Video && orientation != PlayerOrientation.Smart) {
      setOrientation()
    } else if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      applyInitialOrientationFromIntent(intent)
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

    viewModel.unpause()

    if (playerPreferences.showControlsOnPlay.get()) {
      viewModel.showControls()
    }

    if (subtitlesPreferences.autoloadMatchingSubtitles.get()) {
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
    updateMediaSessionPlaybackState(isPlaying = true)

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
  private fun saveVideoPlaybackState(mediaTitle: String, isEof: Boolean = false) {
    val identifier = mediaIdentifier
    if (identifier.isBlank()) return

    // Capture current playback state before switching files
    val currentPos = viewModel.pos ?: 0
    val currentDuration = viewModel.duration ?: 0
    val currentSpeed = MPVLib.getPropertyDouble("speed") ?: DEFAULT_PLAYBACK_SPEED
    val isStateLoaded = isPlaybackStateLoaded
    val currentZoom = viewModel.videoZoom.value
    val currentAspect = viewModel.videoAspect.value.name
    val currentCustomRatio = viewModel.currentAspectRatio.value
    val currentSid = player.sid
    val currentSecondarySid = player.secondarySid
    val currentAid = player.aid
    val currentSubDelay = (MPVLib.getPropertyDouble("sub-delay") ?: 0.0)
    val currentAudioDelay = (MPVLib.getPropertyDouble("audio-delay") ?: 0.0)
    val currentSubSpeed = MPVLib.getPropertyDouble("sub-speed") ?: DEFAULT_SUB_SPEED
    val currentOrientation = requestedOrientation
    val currentExternalSubs = viewModel.externalSubtitles.toList()
    val currentExternalAudio = viewModel.externalAudioTracks.toList()

    // Cancel any previous pending save operation
    savePlaybackStateJob?.cancel()

    // Launch new save job and track it
    savePlaybackStateJob = lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val oldState = playbackStateRepository.getVideoDataByTitle(identifier)
        Log.d(TAG, "Saving playback state for: $mediaTitle (identifier: $identifier) at position: $currentPos")

        val currentZoomToSave = if (isStateLoaded) currentZoom else (oldState?.videoZoom ?: currentZoom)
        val currentAspectToSave = if (isStateLoaded) {
          currentAspect
        } else {
          oldState?.videoAspect ?: currentAspect
        }
        val currentCustomRatioToSave = if (isStateLoaded) {
          currentCustomRatio
        } else {
          oldState?.customAspectRatio ?: currentCustomRatio
        }

        val watchedThreshold = browserPreferences.watchedThreshold.get()
        val progress = if (currentDuration > 0) currentPos.toFloat() / currentDuration.toFloat() else 0f
        val isFinished = isEof || ((currentDuration > 0) && (currentPos >= currentDuration - 1))

        // Calculate save position
        val savePos = if (!playerPreferences.savePositionOnQuit.get()) {
          oldState?.lastPosition ?: 0
        } else if (isFinished) {
          // If video finished or reached within 1 second of the end, restart from beginning next time
          0
        } else if (currentDuration > 0) {
          currentPos
        } else {
          // If duration is not yet available, preserve old position or use current (0)
          oldState?.lastPosition ?: currentPos
        }

        val timeRemaining = if (isFinished) 0 else if (currentDuration > savePos) currentDuration - savePos else 0

        playbackStateRepository.upsert(
          PlaybackStateEntity(
            mediaTitle = identifier,
            lastPosition = savePos,
            playbackSpeed = currentSpeed,
            videoZoom = currentZoomToSave,
            sid = currentSid,
            secondarySid = currentSecondarySid,
            subDelay = (currentSubDelay * MILLISECONDS_TO_SECONDS).toInt(),
            subSpeed = currentSubSpeed,
            aid = currentAid,
            audioDelay = (currentAudioDelay * MILLISECONDS_TO_SECONDS).toInt(),
            timeRemaining = timeRemaining,
            savedOrientation = currentOrientation,
            externalSubtitles = currentExternalSubs.joinToString("|"),
            externalAudioTracks = currentExternalAudio.joinToString("|"),
            videoAspect = currentAspectToSave,
            customAspectRatio = currentCustomRatioToSave,
            hasBeenWatched = run {
              // Check if we are at the end (effectively watched) or reached watched threshold
              val isCurrentlyWatched = progress >= (watchedThreshold / 100f)

              val oldProgress = if (currentDuration > 0) (oldState?.lastPosition?.toFloat() ?: 0f) / currentDuration.toFloat() else 0f
              val wasWatchedThisSession = oldProgress >= (watchedThreshold / 100f)

              isCurrentlyWatched || isFinished || wasWatchedThisSession || (oldState?.hasBeenWatched == true)
            },
          ),
        )
      }.onFailure { e ->
        Log.e(TAG, "Error saving playback state", e)
      }
    }
  }
  /**
   * Loads and applies saved playback state from the database.
   *
   * @param mediaTitle The title of the media being played
   * @return true if saved state was found and applied, false otherwise
   */
  private suspend fun loadVideoPlaybackState(mediaTitle: String): Boolean {
    if (mediaIdentifier.isBlank()) return false

    return runCatching {
      val state = playbackStateRepository.getVideoDataByTitle(mediaIdentifier)

      applyPlaybackState(state)
      applyDefaultSettings(state)

      state != null
    }.onFailure { e ->
      Log.e(TAG, "Error loading playback state", e)
    }.getOrDefault(false)
  }

  /**
   * Applies saved playback state to MPV.
   *
   * Restores subtitle delay, audio delay, audio and track selections, and playback speed.
   * Also restores saved time position if enabled.
   *
   * @param state The saved playback state entity
   */
  private suspend fun applyPlaybackState(state: PlaybackStateEntity?) {
    if (state == null) {
      // Force reset position for new items in playlist
      MPVLib.setPropertyInt("time-pos", 0)
      return
    }

    needsAspectReapply = false

    val subDelay = state.subDelay / DELAY_DIVISOR
    val audioDelay = state.audioDelay / DELAY_DIVISOR

    // Restore external subtitles first
    if (state.externalSubtitles.isNotBlank()) {
      val externalSubUris = state.externalSubtitles.split("|").filter { it.isNotBlank() }
      Log.d(TAG, "Restoring ${externalSubUris.size} external subtitle(s)")

      val lastUri = externalSubUris.last()
      for (subUri in externalSubUris) {
        viewModel.addSubtitle(Uri.parse(subUri), select = subUri == lastUri, silent = true)
      }
    }

    // Restore external audio tracks
    if (state.externalAudioTracks.isNotBlank()) {
      val externalAudioUris = state.externalAudioTracks.split("|").filter { it.isNotBlank() }
      Log.d(TAG, "Restoring ${externalAudioUris.size} external audio track(s)")

      val lastUri = externalAudioUris.last()
      for (audioUri in externalAudioUris) {
        viewModel.addAudio(Uri.parse(audioUri), select = audioUri == lastUri, silent = true)
      }
    }

    // Always restore subtitle and audio tracks from saved state
    // User's manual selection has highest priority
    if (state.sid > 0 || state.sid == -1) {
      player.sid = state.sid
      Log.d(TAG, "Restored primary subtitle track: ${state.sid} (user selection)")
    }

    if (state.secondarySid > 0 || state.secondarySid == -1) {
      player.secondarySid = state.secondarySid
      Log.d(TAG, "Restored secondary subtitle track: ${state.secondarySid} (user selection)")
    }

    if (state.aid > 0) {
      player.aid = state.aid
      Log.d(TAG, "Restored audio track: ${state.aid} (user selection)")
    }

    MPVLib.setPropertyDouble("sub-delay", subDelay)
    MPVLib.setPropertyDouble("speed", state.playbackSpeed)
    MPVLib.setPropertyDouble("audio-delay", audioDelay)
    MPVLib.setPropertyDouble("sub-speed", state.subSpeed)

    // Restore orientation if in Smart mode
    if (playerPreferences.orientation.get() == PlayerOrientation.Smart && state.savedOrientation != null) {
      withContext(Dispatchers.Main) {
        requestedOrientation = state.savedOrientation
        isOrientationRestored = true
        Log.d(TAG, "Restored orientation for Smart mode: ${state.savedOrientation}")
      }
    }

    // Restore video zoom from saved state
    MPVLib.setPropertyDouble("video-zoom", state.videoZoom.toDouble())
    viewModel.setVideoZoom(state.videoZoom)

    // Restore video aspect ratio from saved state
    if (state.videoAspect != null) {
      val aspect = runCatching { VideoAspect.valueOf(state.videoAspect) }.getOrDefault(VideoAspect.Fit)
      withContext(Dispatchers.Main) {
        if (state.customAspectRatio > 0.0) {
          viewModel.setCustomAspectRatio(
            state.customAspectRatio,
            resetZoomAndPan = false,
            showUpdate = false,
            persistToPreferences = false,
          )
        } else {
          viewModel.changeVideoAspect(
            aspect,
            showUpdate = false,
            resetZoomAndPan = false,
            persistToPreferences = false,
          )
        }
      }
    }

    isPlaybackStateLoaded = true

    val resumeMode = playerPreferences.resumePlaybackMode.get()
    val hasValidSavedPosition = state.lastPosition > 3

    when {
      !playerPreferences.savePositionOnQuit.get() || resumeMode == ResumePlaybackMode.Never || !hasValidSavedPosition -> {
        MPVLib.setPropertyInt("time-pos", 0)
      }
      resumeMode == ResumePlaybackMode.Ask -> {
        MPVLib.setPropertyInt("time-pos", 0)
        withContext(Dispatchers.Main) {
          viewModel.showResumePrompt(state.lastPosition, (state.timeRemaining + state.lastPosition))
        }
      }
      resumeMode == ResumePlaybackMode.Always -> {
        MPVLib.setPropertyInt("time-pos", state.lastPosition)
        withContext(Dispatchers.Main) {
          viewModel.playerUpdate.value = PlayerUpdates.ResumedFrom(state.lastPosition)
        }
      }
    }
  }

  /**
   * Applies default settings when no saved state exists.
   *
   * Sets subtitle speed to user default if not present in saved state.
   *
   * @param state The saved playback state entity (null if no saved state)
   */
  private fun applyDefaultSettings(state: PlaybackStateEntity?) {
    isPlaybackStateLoaded = true
    if (state == null) {
      val defaultSubSpeed = subtitlesPreferences.defaultSubSpeed.get().toDouble()
      MPVLib.setPropertyDouble("sub-speed", defaultSubSpeed)
    }
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
    Log.d(TAG, "Setting return intent")

    val resultIntent =
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
        viewModel.duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
      }

    setResult(RESULT_OK, resultIntent)
  }

  /**
   * Handles new intents to load a different file without recreating the activity.
   *
   * @param intent The new intent
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    pendingIntentExtras = true
    // Update the intent first so getFileName uses the new intent data
    setIntent(intent)

    val hasIntentMedia = intent.data != null || intent.hasExtra("video_list") || parsePathFromIntent(intent) != null
    val incomingFileName = getFileName(intent).ifBlank { intent.data?.lastPathSegment ?: "" }
    val incomingMediaIdentifier = if (incomingFileName.isNotBlank()) getMediaIdentifier(intent, incomingFileName) else ""

    val isSameMedia = isReady && hasIntentMedia &&
      ((incomingMediaIdentifier.isNotBlank() && incomingMediaIdentifier == mediaIdentifier) ||
       (incomingFileName.isNotBlank() && incomingFileName == fileName))

    // If expanding active session without intent media, or if the exact same media is already active in foreground
    if (isReady && (!hasIntentMedia || (isSameMedia && !isInBackgroundPlayback && !isManualBackgroundPlayback))) {
      Log.d(TAG, "onNewIntent: current media already playing or expanding active session, restoring player without reload")
      enableVideoAfterBackground()
      @Suppress("DEPRECATION")
      overridePendingTransition(android.R.anim.fade_in, 0)
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
    if (isManualBackgroundPlayback || isInBackgroundPlayback) {
      // Reset flag FIRST to prevent the state collector from calling finish()
      // when clearState() sets isPlaybackActive = false
      isManualBackgroundPlayback = false
      endBackgroundPlayback()
      enableVideoAfterBackground()
      miniPlayerStateManager.clearState()
    }

    // Clear stale playlist from previous video so auto-generate runs fresh for the new file
    viewModel.playlistManager.setPlaylist(items = emptyList(), index = 0)

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }


    // Only update playlist state if we have new playlist information
    // This prevents losing the playlist when coming back from notification/PiP
    if (externalPlaylist != null) {
      viewModel.playlistManager.setPlaylist(
        items = externalPlaylist.items,
        index = externalPlaylist.initialIndex,
        titles = externalPlaylist.titles
      )
      Log.d(TAG, "onNewIntent: Loaded external playlist: ${externalPlaylist.items.size} items at index ${externalPlaylist.initialIndex}")
      updateMiniPlayerPlaylistState()
    } else if (hasPlaylistExtras || playlistFromIntent.isNotEmpty()) {
      val newPlaylistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
      val newPlaylistIndex = intent.getIntExtra("playlist_index", 0)
      val titlesFromIntent = intent.getStringArrayListExtra("playlist_titles") ?: emptyList()
      
      viewModel.playlistManager.setPlaylist(
        items = playlistFromIntent,
        index = newPlaylistIndex,
        id = newPlaylistId,
        titles = titlesFromIntent
      )
    }

    // If playlist is empty but playlist_id is provided, load from database
    if (viewModel.playlistManager.playlist.value.isEmpty() && viewModel.playlistManager.playlistId != null) {
      lifecycleScope.launch(Dispatchers.IO) {
        val pid = viewModel.playlistManager.playlistId ?: return@launch
        try {
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
              index = viewModel.playlistManager.currentIndex.value,
              id = pid,
              totalCount = totalCount,
              titles = titles
            )
            Log.d(TAG, "onNewIntent: Loaded ${items.size} items from playlist $pid")
            updateMiniPlayerPlaylistState()
          }
        } catch (e: Exception) {
          Log.e(TAG, "onNewIntent: Failed to load playlist from database", e)
        }
      }
    }

    // Auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (viewModel.playlistManager.playlist.value.isEmpty() && viewModel.playlistManager.playlistId == null && playerPreferences.playlistMode.get()) {
      autoGeneratePlaylist(intent)
    }

    // Extract the new fileName before loading the file
    fileName = if (externalPlaylist != null && !externalPlaylist.titles.getOrNull(externalPlaylist.initialIndex).isNullOrBlank()) {
      externalPlaylist.titles[externalPlaylist.initialIndex]
    } else {
      getFileName(intent)
    }
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)
    viewModel.setMediaTitle(fileName)
    viewModel.setMediaIdentifier(mediaIdentifier)
    jellyfinExternalInfo = xyz.mpv.rex.jellyfin.JellyfinExternalHelper.detect(intent)

    // Synchronously set orientation for the new file before displaying activity
    applyInitialOrientationFromIntent(intent)

    // Set HTTP headers (including referer) BEFORE loading the new file
    setHttpHeadersFromExtras(intent.extras)

    // Load the new file
    getPlayableUri(intent)?.let { uriStr ->
      switchActiveNetworkStream(uriStr)

      val parsedUri = runCatching { Uri.parse(uriStr) }.getOrNull()
      val fastDurationMs = if (parsedUri != null) getFastDurationMsForUri(parsedUri) else 0L
      val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
      viewModel.prepareForFileLoad(fastDurationSec)

      if (parsedUri != null && isUriM3U(parsedUri)) {
        loadM3uPlaylistOrPlayDirectly(uriStr)
      } else {
        if (playerPreferences.savePositionOnQuit.get() || playerPreferences.resumePlaybackMode.get() != ResumePlaybackMode.Never) {
          runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        // Avoid blocking UI thread while mpv opens network streams (e.g., HLS).
        lifecycleScope.launch(Dispatchers.Default) {
          MPVLib.command("loadfile", uriStr)
        }
      }
    }
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

    wasInPipMode = isInPictureInPictureMode
    pipHelper.onPictureInPictureModeChanged(isInPictureInPictureMode)

    binding.controls.alpha = if (isInPictureInPictureMode) 0f else 1f

    runCatching {
      if (isInPictureInPictureMode) {
        miniPlayerStateManager.clearState()
        enterPipUIMode()
      } else {
        exitPipUIMode()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error handling PiP mode change", e)
    }
  }

  /**
   * Configures window for Picture-in-Picture mode.
   * Shows system UI and navigation bars.
   */
  private fun enterPipUIMode() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    WindowCompat.setDecorFitsSystemWindows(window, true)
    try {
      windowInsetsController.apply {
        show(WindowInsetsCompat.Type.systemBars())
        show(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to show system bars for PiP mode", e)
    }
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun exitPipUIMode() {
    setupWindowFlags()
    setupSystemUI()
  }

  /**
   * Enters Picture-in-Picture mode and hides all overlay controls.
   */
  fun enterPipModeHidingOverlay() {
    runCatching {
      enterPipUIMode()
    }.onFailure { e ->
      Log.e(TAG, "Error entering PiP mode with hidden overlay", e)
    }

    binding.controls.alpha = 0f
    miniPlayerStateManager.clearState()

    pipHelper.enterPipMode()
  }

  // ==================== Orientation Management ====================

  /**
   * Sets the screen orientation based on user preferences.
   *
   * IMPORTANT: Preferences are the single source of truth for orientation.
   * This method applies the preference value when videos load.
   * The rotation button temporarily overrides this without changing preferences.
   *
   * For "Video" orientation mode, this will wait for video-params/aspect to update
   * to the correct orientation, starting with landscape as fallback.
   *
   * @param width Optional video width from metadata to set orientation before video loads
   * @param height Optional video height from metadata to set orientation before video loads
   * @param rotation Optional video rotation from metadata to correctly determine aspect ratio
   */
  private fun setOrientation(width: Int = -1, height: Int = -1, rotation: Int = 0) {
    val orientationPref = playerPreferences.orientation.get()

    requestedOrientation =
      when (orientationPref) {
        PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientation.Smart, PlayerOrientation.Video -> {
          // For Smart mode, check if orientation was already restored from database
          val isSmartMode = orientationPref == PlayerOrientation.Smart
          
          // If in Smart mode and we've already restored a choice from the database,
          // keep using it and don't re-calculate from aspect ratio.
          if (isSmartMode && isOrientationRestored) {
             Log.d(TAG, "setOrientation - Smart mode: using restored orientation $requestedOrientation")
             return
          }

          // 1. Try provided width/height from metadata first (to avoid jumpy transition)
          if (width > 0 && height > 0) {
            // Swap dimensions if video has 90 or 270 degree rotation
            val isRotated = rotation == 90 || rotation == 270
            val effectiveWidth = if (isRotated) height else width
            val effectiveHeight = if (isRotated) width else height
            
            val aspect = effectiveWidth.toDouble() / effectiveHeight.toDouble()
            Log.d(TAG, "setOrientation - Using metadata: ${width}x${height}, rot=$rotation, aspect=$aspect")
            if (aspect > 1.0) {
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
          } else {
            // 2. Fallback to current player aspect ratio
            val aspect = runCatching { player.getVideoOutAspect() }.getOrNull()
            Log.d(TAG, "setOrientation - ${if (isSmartMode) "Smart (fallback)" else "Video"} mode: aspect=$aspect")
            if (aspect == null || aspect <= 0.0) {
              // Aspect not available yet or audio-only file - do not force orientation change
              Log.d(TAG, "setOrientation - Aspect not available or audio-only file")
              ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
              // Aspect available - set correct orientation now
              val orientation = if (aspect > 1.0) {
                Log.d(TAG, "setOrientation - Aspect $aspect > 1.0, setting landscape")
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
              } else {
                Log.d(TAG, "setOrientation - Aspect $aspect <= 1.0, setting portrait")
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
              }
              orientation
            }
          }
        }
        PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      }
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
  @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
  override fun onKeyDown(
    keyCode: Int,
    event: KeyEvent?,
  ): Boolean {
    val isTrackSheetOpen =
      viewModel.sheetShown.value == Sheets.SubtitleTracks ||
        viewModel.sheetShown.value == Sheets.AudioTracks
    val isNoSheetOpen = viewModel.sheetShown.value == Sheets.None

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_DPAD_LEFT,
        -> {
        if (isTrackSheetOpen) {
          return super.onKeyDown(keyCode, event)
        }

        if (isNoSheetOpen) {
          when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
              viewModel.handleRightDoubleTap()
              return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
              viewModel.handleLeftDoubleTap()
              return true
            }
          }
        }
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
        if (isTrackSheetOpen) {
          return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_SPACE -> {
        viewModel.pauseUnpause()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_STOP -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        finishAndRemoveTask()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_REWIND -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        viewModel.handleLeftDoubleTap()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        viewModel.handleRightDoubleTap()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_PLAY,
      KeyEvent.KEYCODE_MEDIA_PAUSE,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      KeyEvent.KEYCODE_MEDIA_NEXT,
      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        
        event?.let { player.onKey(it) }
        return super.onKeyDown(keyCode, event)
      }

      else -> {
        event?.let { player.onKey(it) }
        return super.onKeyDown(keyCode, event)
      }
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
    event?.let {
      if (player.onKey(it)) return true
    }
    return super.onKeyUp(keyCode, event)
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
    runCatching {
      mediaSession =
        MediaSession(this, TAG).apply {
          setCallback(
            object : MediaSession.Callback() {
              private fun canHandle() = !playerPreferences.disableMediaButtons.get()

              override fun onPlay() {
                if (!canHandle()) return
                viewModel.unpause()
                updateMediaSessionPlaybackState(isPlaying = true)
              }

              override fun onPause() {
                if (!canHandle()) return
                viewModel.pause()
                updateMediaSessionPlaybackState(isPlaying = false)
              }

              override fun onSkipToNext() {
                if (!canHandle()) return
                viewModel.handleMediaNext()
              }

              override fun onSkipToPrevious() {
                if (!canHandle()) return
                viewModel.handleMediaPrevious()
              }

              override fun onSeekTo(pos: Long) {
                if (!canHandle()) return
                viewModel.seekTo((pos / 1000).toInt())
                updateMediaSessionPlaybackState(isPlaying = viewModel.paused == false)
              }
            },
          )
          isActive = true
        }
      playbackStateBuilder =
        PlaybackState
          .Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_PLAY_PAUSE or
              PlaybackState.ACTION_SEEK_TO or
              PlaybackState.ACTION_SKIP_TO_NEXT or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS,
          )
      mediaSessionInitialized = true
    }.onFailure { e ->
      Log.e(TAG, "Failed to initialize MediaSession", e)
      mediaSessionInitialized = false
    }
  }

  /**
   * Updates MediaSession playback state (playing/paused).
   *
   * @param isPlaying true if currently playing, false if paused
   */
  private fun updateMediaSessionPlaybackState(isPlaying: Boolean) {
    if (!mediaSessionInitialized) return
    runCatching {
      val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
      val positionMs = (viewModel.pos ?: 0) * 1000L
      mediaSession.setPlaybackState(
        playbackStateBuilder
          .setState(state, positionMs, if (isPlaying) 1.0f else 0f)
          .build(),
      )
    }.onFailure { e -> Log.e(TAG, "Error updating playback state", e) }
  }

  /**
   * Updates MediaSession metadata (title, duration, etc.).
   *
   * @param title The media title
   * @param durationMs The media duration in milliseconds
   */
  private fun updateMediaSessionMetadata(
    title: String,
    durationMs: Long,
  ) {
    if (!mediaSessionInitialized) return
    runCatching {
      val metadata =
        MediaMetadata
          .Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, title)
          .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
          .build()
      mediaSession.setMetadata(metadata)
    }.onFailure { e -> Log.e(TAG, "Error updating metadata", e) }
  }

  /**
   * Releases MediaSession resources.
   * Called during activity cleanup.
   */
  private fun releaseMediaSession() {
    if (!mediaSessionInitialized) return
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }.onFailure { e -> Log.e(TAG, "Error releasing MediaSession", e) }
    mediaSessionInitialized = false
  }

  // ==================== Background Playback Service ====================

  /**
   * Service connection for binding to background playback service.
   */
  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?,
      ) {
        val binder = service as? MediaPlaybackService.MediaPlaybackBinder ?: return
        mediaPlaybackService = binder.getService()
        mediaPlaybackService?.setListener(this@PlayerActivity)
        serviceBound = true
        Log.d(TAG, "Service connected")
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "Service disconnected")
        mediaPlaybackService?.setListener(null)
        mediaPlaybackService = null
        serviceBound = false
      }
    }

  /**
   * Starts the background playback service and binds to it.
   *
   * This should only be called if a video is loaded and playback is initialized.
   * Responsible for starting and binding to the MediaPlaybackService, which
   * handles background playback.
   */
  private fun startBackgroundPlayback() {
    if (fileName.isBlank() || !isReady) {
      Log.w(TAG, "Cannot start background playback: video not ready")
      return
    }

    // Prevent starting service multiple times
    if (serviceBound) {
      Log.d(TAG, "Service already bound, skipping start")
      return
    }

    Log.d(TAG, "Starting background playback for: $fileName")
    
    // Ensure notification channel exists
    MediaPlaybackService.createNotificationChannel(this)
    
    val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""
    
    // Pass media info via intent extras
    val intent = Intent(this, MediaPlaybackService::class.java).apply {
      putExtra("media_title", fileName)
      putExtra("media_artist", artist)
    }
    
    try {
      startForegroundService(intent)
      bindService(intent, serviceConnection, BIND_AUTO_CREATE)
      Log.d(TAG, "Service start and bind initiated")
    } catch (e: Exception) {
      Log.e(TAG, "Error starting/binding service", e)
    }

    // Offload thumbnail or cover art extraction to background coroutine
    lifecycleScope.launch(Dispatchers.IO) {
      val currentUri = viewModel.playlistManager.getCurrentUri() ?: extractUriFromIntent(intent)
      val thumbnail = currentUri?.let { extractThumbnailOrCoverArt(it) }

      MediaPlaybackService.thumbnail = thumbnail
      miniPlayerStateManager.updateState(thumbnail = thumbnail)
    }
  }

  /**
   * Stops the background playback service and unbinds from it.
   *
   * Called when the activity is destroyed to remove the notification.
   */
  private fun endBackgroundPlayback() {
    Log.d(TAG, "Ending background playback service")
    
    if (serviceBound) {
      try {
        unbindService(serviceConnection)
        Log.d(TAG, "Service unbound successfully")
      } catch (e: Exception) {
        Log.e(TAG, "Error unbinding service", e)
      }
      serviceBound = false
    }
    
    // Stop the service which will trigger its onDestroy and cleanup
    try {
      stopService(Intent(this, MediaPlaybackService::class.java))
      Log.d(TAG, "Stop service command sent")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping service", e)
    }
    
    mediaPlaybackService = null
  }

  /**
   * Manually triggers background playback when the user clicks the background playback button.
   * This works independently of the BackgroundPlaybackMode preference.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  fun triggerBackgroundPlayback() {
    if (fileName.isBlank() || !isReady) {
      Log.w(TAG, "Cannot trigger background playback: video not ready")
      return
    }

    Log.d(TAG, "User triggered background playback")
    
    // Set flag to enable background playback (same logic as automatic)
    isManualBackgroundPlayback = true
    
    // Start background playback service
    startBackgroundPlayback()
    
    // Restore system UI before going to background
    restoreSystemUI()
    
    // Move to background by going to home screen (same behavior as automatic)
    val intent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    startActivity(intent)
  }

  /**
   * Disables video decoding to save battery when moving to background playback.
   */
  private fun disableVideoForBackground() {
    if (!isReady || fileName.isBlank()) return

    if (!hasVideoTrack()) {
      isInBackgroundPlayback = true
      Log.d(TAG, "Audio-only playback in background")
      return
    }

    val currentVid = MPVLib.getPropertyInt("vid") ?: -1
    if (currentVid > 0) {
      if (lastVid <= 0) {
        lastVid = currentVid
      }
      MPVLib.setPropertyString("vid", "no")
      isInBackgroundPlayback = true
      Log.d(TAG, "Video disabled for background playback (saved vid: $lastVid)")
    } else {
      if (MPVLib.getPropertyString("vid") != "no") {
        MPVLib.setPropertyString("vid", "no")
      }
      isInBackgroundPlayback = true
    }
  }

  /**
   * Restores video decoding when returning from background playback.
   */
  private fun enableVideoAfterBackground() {
    isInBackgroundPlayback = false
    if (lastVid > 0) {
      Log.d(TAG, "Restoring video after background playback (vid: $lastVid)")
      MPVLib.setPropertyInt("vid", lastVid)
      lastVid = -1
    } else if (mpvInitialized && MPVLib.getPropertyString("vid") == "no") {
      Log.d(TAG, "Restoring video after background playback (setting vid to auto)")
      safeSetPropertyString("vid", "auto")
      lastVid = -1
    }
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
      if (file.exists() && xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(file)) {
        return true
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

  private suspend fun getCachedThumbnailForUri(uri: Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
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
  private suspend fun extractThumbnailOrCoverArt(uri: Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
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
    get() = WindowCompat.getInsetsController(window, window.decorView)
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
  fun hasNext(): Boolean {
    val playlistHasNext = viewModel.playlistManager.hasNext(viewModel.shouldRepeatPlaylist())
    val mpvCount = runCatching { MPVLib.getPropertyInt("playlist-count") }.getOrNull() ?: 0
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasNext = mpvCount > 1 && mpvPos < mpvCount - 1
    return playlistHasNext || mpvHasNext
  }

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean {
    val playlistHasPrev = viewModel.playlistManager.hasPrevious(viewModel.shouldRepeatPlaylist())
    val mpvPos = runCatching { MPVLib.getPropertyInt("playlist-pos") }.getOrNull() ?: 0
    val mpvHasPrev = mpvPos > 0
    return playlistHasPrev || mpvHasPrev
  }

  fun updateMiniPlayerPlaylistState() {
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

    val hasNextItem = hasNext()
    val hasPrevItem = hasPrevious()

    miniPlayerStateManager.updateState(
      hasNext = hasNextItem,
      hasPrevious = hasPrevItem,
      nextTitle = nextTitle,
      prevTitle = prevTitle,
    )

    lifecycleScope.launch(Dispatchers.IO) {
      val cachedNextThumb = nextUri?.let { getCachedThumbnailForUri(it) }
      val cachedPrevThumb = prevUri?.let { getCachedThumbnailForUri(it) }
      withContext(Dispatchers.Main) {
        miniPlayerStateManager.updateState(
          nextThumbnail = cachedNextThumb,
          prevThumbnail = cachedPrevThumb,
        )
      }
      val fullNextThumbnail = nextUri?.let { extractThumbnailOrCoverArt(it) }
      val fullPrevThumbnail = prevUri?.let { extractThumbnailOrCoverArt(it) }
      withContext(Dispatchers.Main) {
        miniPlayerStateManager.updateState(
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
    val nextIndex = viewModel.playlistManager.getNextIndex(viewModel.shouldRepeatPlaylist())
    if (nextIndex != null) {
      loadPlaylistItem(nextIndex)
    }
  }

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() {
    val prevIndex = viewModel.playlistManager.getPreviousIndex(viewModel.shouldRepeatPlaylist())
    if (prevIndex != null) {
      loadPlaylistItem(prevIndex)
    }
  }

  /**
   * Load a playlist item by index
   */
  fun loadPlaylistItem(index: Int) {
    val playlist = viewModel.playlistManager.playlist.value
    // All items are loaded - just validate index and load directly
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }
    loadPlaylistItemInternal(index)
  }

  /**
   * Bumped on every SMB playlist navigation so a resolve that finishes late, after the user has
   * already pressed Next again, is discarded instead of loading on top of the newer one.
   */
  private var smbResolveToken = 0

  /**
   * Internal method to load a playlist item
   */
  private fun loadPlaylistItemInternal(index: Int) {
    val playlist = viewModel.playlistManager.playlist.value
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }

    val uri = playlist[index]
    if (uri.scheme.equals("smb", ignoreCase = true)) {
      val token = ++smbResolveToken
      lifecycleScope.launch {
        val stream = withContext(Dispatchers.IO) {
          runCatching { FolderPlaylistOps.resolveSmbUri(uri.toString()) }
            .onFailure { Log.w(TAG, "Could not resolve the next network playlist item", it) }
            .getOrNull()
        }
        if (token != smbResolveToken) {
          stream?.streamId?.takeIf { it.isNotEmpty() }?.let { streamId ->
            lifecycleScope.launch(Dispatchers.IO) {
              NetworkStreamingProxy.getInstance().unregisterStream(streamId)
            }
          }
          return@launch
        }
        if (stream == null) {
          viewModel.showToast(getString(R.string.network_share_unreachable))
          return@launch
        }
        loadResolvedPlaylistItem(
          index = index,
          uri = uri,
          playableUri = stream.url,
          mediaIdentifierOverride = "network_${stream.connectionId}_${stream.filePath.hashCode()}",
        )
      }
      return
    }

    loadResolvedPlaylistItem(index, uri, uri.resolveUri(this) ?: uri.toString())
  }

  private fun switchActiveNetworkStream(playableUri: String?) {
    val newStreamId = NetworkStreamingProxy.getInstance().extractStreamId(playableUri)
    val oldStreamId = activeNetworkStreamId
    if (oldStreamId != null && oldStreamId != newStreamId) {
      lifecycleScope.launch(Dispatchers.IO) {
        NetworkStreamingProxy.getInstance().unregisterStream(oldStreamId)
      }
    }
    activeNetworkStreamId = newStreamId
  }

  /**
   * Loads a playlist item whose playable URI is already known.
   */
  private fun loadResolvedPlaylistItem(
    index: Int,
    uri: Uri,
    playableUri: String,
    mediaIdentifierOverride: String? = null,
  ) {
    // Save current video's playback state before switching
    if (fileName.isNotBlank()) {
      saveVideoPlaybackState(fileName)
    }

    switchActiveNetworkStream(playableUri)

    // Update index in manager
    viewModel.playlistManager.updateIndex(index)

    // Extract and set the new file name
    val customTitle = viewModel.playlistManager.getTitleAt(index)
    fileName = if (!customTitle.isNullOrBlank()) customTitle else getFileNameFromUri(uri)
    // Generate new media identifier for playback state
    mediaIdentifier = mediaIdentifierOverride ?: getMediaIdentifierFromUri(uri, fileName)
    viewModel.setMediaTitle(fileName)
    viewModel.setMediaIdentifier(mediaIdentifier)

    val cachedDurationMs = viewModel.playlistManager.getDurationAt(index)
    val fastDurationMs = if (cachedDurationMs > 0L) cachedDurationMs else getFastDurationMsForUri(uri)
    val fastDurationSec = if (fastDurationMs > 0L) fastDurationMs / 1000f else null
    viewModel.prepareForFileLoad(fastDurationSec)

    val targetUri = uri
    lifecycleScope.launch(Dispatchers.IO) {
      val cachedThumb = getCachedThumbnailForUri(targetUri)
      withContext(Dispatchers.Main) {
        val activeThumb = cachedThumb ?: MediaPlaybackService.thumbnail ?: miniPlayerStateManager.state.value.thumbnail
        MediaPlaybackService.thumbnail = activeThumb
        mediaPlaybackService?.setMediaInfo(
          title = fileName,
          artist = "",
          thumbnail = activeThumb
        )
        miniPlayerStateManager.updateState(
          title = fileName,
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
    viewModel.playlistManager.playlistId?.let { id ->
      lifecycleScope.launch(Dispatchers.IO) {
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

        runCatching {
          playlistRepository.updatePlayHistory(id, filePath)
          Log.d(TAG, "Updated playlist history for: $filePath in playlist $id")
        }.onFailure { e ->
          Log.e(TAG, "Error updating playlist history", e)
        }
      }
    }

    if (mpvInitialized) {
      safeSetPropertyString("vid", "no")
      runCatching { MPVLib.setPropertyBoolean("pause", true) }
    } else if (playerPreferences.savePositionOnQuit.get() || playerPreferences.resumePlaybackMode.get() != ResumePlaybackMode.Never) {
      runCatching { MPVLib.setPropertyBoolean("pause", true) }
    }
    // Load the new video
    // Avoid blocking UI thread while mpv opens network streams (e.g., HLS).
    lifecycleScope.launch(Dispatchers.Default) {
      MPVLib.command("loadfile", playableUri)
    }

    // Update media title (this will trigger UI update)
    // Don't force media-title for standalone m3u/m3u8 streams - let MPV provide it
    // But if we are playing from an M3U playlist with custom titles, we MUST set it
    val isM3U = isUriM3U(uri)
    val isM3uPlaylist = viewModel.playlistManager.isM3uPlaylist
    val hasCustomTitle = !viewModel.playlistManager.getTitleAt(index).isNullOrBlank()
    if (!isM3U || isM3uPlaylist || hasCustomTitle) {
      safeSetPropertyString("force-media-title", fileName)
      viewModel.setMediaTitle(fileName)
    } else {
      viewModel.setMediaTitle(fileName)
    }

    // Update media session metadata
    lifecycleScope.launch {
      kotlinx.coroutines.delay(100) // Wait for MPV to load the file
      val mpvDuration = MPVLib.getPropertyDouble("duration")
      val durationMs = if (mpvDuration != null && mpvDuration > 0) {
        (mpvDuration * 1000).toLong()
      } else {
        fastDurationMs
      }
      updateMediaSessionMetadata(
        title = fileName,
        durationMs = durationMs,
      )
      // Refresh playlist items to update the currently playing indicator
      viewModel.refreshPlaylistItems()
    }
  }

  /**
   * Fast synchronous lookup for media duration in milliseconds.
   * Checks MediaStore for content:// and file:// URIs.
   */
  private fun getFastDurationMsForUri(uri: Uri): Long {
    return runCatching {
      when (uri.scheme) {
        "content" -> {
          contentResolver.query(
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
          contentResolver.query(
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
  private fun getFileNameFromUri(uri: Uri): String {
    getDisplayNameFromUri(uri)?.let { return it }
    return extractFileNameFromUri(uri)
  }

  /**
   * Get the current video title for controls display.
   * Used as a fallback when MPV hasn't set the media-title property yet.
   * For m3u/m3u8 streams, returns the raw media-title from MPV instead of parsing.
   */
  fun getTitleForControls(): String {
    // 1. Check if we have a custom playlist title
    val index = viewModel.playlistManager.currentIndex.value
    if (viewModel.playlistManager.playlist.value.isNotEmpty() && index >= 0 && index < viewModel.playlistManager.playlist.value.size) {
      val customTitle = viewModel.playlistManager.getTitleAt(index)
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
    return fileName
  }

  /**
   * Check if the currently playing media is an m3u or m3u8 stream.
   * Checks both the intent URI and the current playlist item if playing from a playlist.
   */
  private fun isCurrentStreamM3U(): Boolean {
    // First check the intent URI
    val uri = extractUriFromIntent(intent)
    if (uri != null && isUriM3U(uri)) {
      return true
    }

    // Also check the current playlist item if playing from a playlist
    val playlist = viewModel.playlistManager.playlist.value
    val playlistIndex = viewModel.playlistManager.currentIndex.value
    if (playlist.isNotEmpty() && playlistIndex >= 0 && playlistIndex < playlist.size) {
      return isUriM3U(playlist[playlistIndex])
    }

    return false
  }

  /**
   * Check if a specific URI string is an m3u or m3u8 file/stream.
   */
  private fun isUriM3U(uriStr: String): Boolean {
    val lowerUrl = uriStr.lowercase()
    return lowerUrl.contains(".m3u8") || lowerUrl.contains(".m3u") ||
      lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".m3u")
  }

  /**
   * Check if a specific URI is an m3u or m3u8 file/stream.
   */
  private fun isUriM3U(uri: Uri): Boolean {
    return isUriM3U(uri.toString())
  }

  /**
   * Intercepts an M3U/M3U8 file or stream, parses its channels/items,
   * populates the playlist, and triggers playback of the first item.
   * Falls back to direct playback if the parse fails (e.g. HLS streams).
   */
  private fun loadM3uPlaylistOrPlayDirectly(uriStr: String) {
    val uri = Uri.parse(uriStr)
    lifecycleScope.launch(Dispatchers.IO) {
      val result = if (uri.scheme == "http" || uri.scheme == "https") {
        M3UParser.parseFromUrl(uriStr)
      } else {
        M3UParser.parseFromUri(this@PlayerActivity, uri)
      }

      withContext(Dispatchers.Main) {
        if (result is M3UParseResult.Success && result.items.isNotEmpty()) {
          val items = result.items
          val playlistUris = items.map { Uri.parse(it.url) }
          val playlistTitles = items.map { it.title ?: it.url }

          viewModel.playlistManager.setPlaylist(
            items = playlistUris,
            index = 0,
            id = null,
            isM3u = true,
            titles = playlistTitles
          )

          // Play the first item
          loadPlaylistItemInternal(0)

          // Set media title in UI
          val playlistName = result.playlistName
          viewModel.setMediaTitle(playlistName)
          Log.d(TAG, "Loaded M3U playlist '${playlistName}' with ${items.size} items")
        } else {
          // If parsing failed or HLS, play the URI directly in MPV
          Log.d(TAG, "M3U parsing failed or HLS stream. Playing directly: $uriStr")
          if (mpvInitialized) {
            safeSetPropertyString("vid", "no")
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
          } else if (playerPreferences.savePositionOnQuit.get()) {
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
          }
          if (mpvInitialized) {
            lifecycleScope.launch(Dispatchers.Default) {
              MPVLib.command("loadfile", uriStr)
            }
          } else {
            player.playFile(uriStr)
          }
        }
      }
    }
  }

  /**
   * Save recently played for a specific URI
   */
  private suspend fun saveRecentlyPlayedForUri(
    uri: Uri,
    name: String,
  ) {
    viewModel.historyManager.recordPlaybackStart(
      uri = uri,
      fileName = name,
      launchSource = "playlist",
      playlistId = viewModel.playlistManager.playlistId
    )
  }

  /**
   * Generate a unique identifier for this media for playback state/history.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network streams via proxy (SMB/WebDAV/FTP), uses the stable network file path from intent extras.
   * For other network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  private fun getMediaIdentifier(intent: Intent, fileName: String): String {
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

    val uri = extractUriFromIntent(intent)
    return if (uri != null) getMediaIdentifierFromUri(uri, fileName) else fileName
  }

  /**
   * Generate a unique identifier for this media from a URI and name.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  private fun getMediaIdentifierFromUri(uri: Uri, fileName: String): String {
    return if (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms" || uri.scheme.equals("smb", ignoreCase = true)) {
      "${fileName}_${uri.toString().hashCode()}"
    } else {
      fileName
    }
  }

  private fun generatePlaylistFromMediaLibrary(currentPath: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val result = FolderPlaylistOps.generateMediaLibraryPlaylist(this@PlayerActivity, currentPath)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            viewModel.playlistManager.setPlaylist(
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
  private fun autoGeneratePlaylist(intent: Intent) {
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
        val path = parsePathFromIntent(intent) ?: return
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
    lifecycleScope.launch(Dispatchers.IO) {
      val (newPlaylist, newIndex) = generate()
        .getOrElse { e ->
          Log.w(TAG, "Could not list the remote folder for an auto-playlist", e)
          return@launch
        } ?: return@launch

      withContext(Dispatchers.Main) {
        viewModel.playlistManager.setPlaylist(items = newPlaylist, index = newIndex)
        Log.d(TAG, "Auto-playlist generated from network folder: ${newPlaylist.size} items")
        updateMiniPlayerPlaylistState()
      }
    }
  }

  private fun generatePlaylistFromFolder(currentPath: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val launchSource = intent.getStringExtra("launch_source")
        val result = FolderPlaylistOps.generateFolderPlaylist(this@PlayerActivity, currentPath, launchSource)
        if (result != null) {
          val (newPlaylist, newIndex) = result
          val durations = newPlaylist.map { getFastDurationMsForUri(it) }
          withContext(Dispatchers.Main) {
            viewModel.playlistManager.setPlaylist(
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
   * Check if the current playlist is an M3U playlist (sourced from database).
   */
  fun isCurrentPlaylistM3U(): Boolean = viewModel.playlistManager.isM3uPlaylist

  fun getPlaylistWindowOffset(): Int = viewModel.playlistManager.playlistWindowOffset


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
