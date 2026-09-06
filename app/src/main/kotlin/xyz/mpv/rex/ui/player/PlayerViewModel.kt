package xyz.mpv.rex.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.repository.wyzie.WyzieSearchRepository
import xyz.mpv.rex.repository.wyzie.WyzieSubtitle
import xyz.mpv.rex.utils.media.ChecksumUtils
import xyz.mpv.rex.utils.media.MediaInfoParser
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import xyz.mpv.rex.utils.storage.FileTypeUtils
import androidx.documentfile.provider.DocumentFile
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


enum class RepeatMode {
  OFF,      // No repeat
  ONE,      // Repeat current file
  ALL       // Repeat all (playlist)
}

class PlayerViewModelProviderFactory(
  private val host: PlayerHost,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(
    modelClass: Class<T>,
    extras: CreationExtras,
  ): T {
    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PlayerViewModel(host) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private val host: PlayerHost,
) : ViewModel(),
  KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private val decoderPreferences: DecoderPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val json: Json by inject()
  private val playbackStateRepository: xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository by inject()
  private val recentlyPlayedRepository: xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()
  private val wyzieRepository: WyzieSearchRepository by inject()

  private val browserPreferences: xyz.mpv.rex.preferences.BrowserPreferences by inject()
  private val miniPlayerStateManager: MiniPlayerStateManager by inject()

  // Cache the application context to prevent leaking the Activity context
  private val appContext = host.context.applicationContext
  
  /**
   * Manager for playlist state and logic.
   */
  private val _playlistManager = PlaylistManager()
  val playlistManager: PlaylistManager get() = _playlistManager

  /**
   * Manager for subtitle state and operations.
   */
  private val _subtitleManager = SubtitleManager(
    context = appContext,
    wyzieRepository = wyzieRepository,
    scope = viewModelScope,
    onShowToast = { showToast(it) }
  )
  val subtitleManager: SubtitleManager get() = _subtitleManager

  /**
   * Manager for history tracking and position saving.
   */
  private val _historyManager = xyz.mpv.rex.utils.history.HistoryManager(
    context = appContext,
    recentlyPlayedRepository = recentlyPlayedRepository,
    playbackStateRepository = playbackStateRepository,
    advancedPreferences = advancedPreferences,
    scope = viewModelScope
  )
  val historyManager: xyz.mpv.rex.utils.history.HistoryManager get() = _historyManager

  /**
   * Manager for custom user-defined buttons.
   */
  private val _customButtonManager = CustomButtonManager(
    context = appContext,
    playerPreferences = playerPreferences,
    advancedPreferences = advancedPreferences,
    json = json,
    scope = viewModelScope
  )
  val customButtonManager: CustomButtonManager get() = _customButtonManager

  /**
   * Manager for playback operations (seeking, speed).
   */
  private val _playbackManager: PlaybackManager by inject()
  val playbackManager: PlaybackManager get() = _playbackManager

  // Subtitle state delegates
  val isDownloadingSub = _subtitleManager.isDownloadingSub
  val isSearchingSub = _subtitleManager.isSearchingSub
  val isOnlineSectionExpanded = _subtitleManager.isOnlineSectionExpanded
  val wyzieSearchResults = _subtitleManager.wyzieSearchResults
  val mediaSearchResults = _subtitleManager.mediaSearchResults
  val isSearchingMedia = _subtitleManager.isSearchingMedia
  val selectedTvShow = _subtitleManager.selectedTvShow
  val isFetchingTvDetails = _subtitleManager.isFetchingTvDetails
  val selectedSeason = _subtitleManager.selectedSeason
  val seasonEpisodes = _subtitleManager.seasonEpisodes
  val isFetchingEpisodes = _subtitleManager.isFetchingEpisodes
  val selectedEpisode = _subtitleManager.selectedEpisode

  // Playlist items for the playlist sheet
  private val _playlistItems = kotlinx.coroutines.flow.MutableStateFlow<List<xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistItem>>(emptyList())
  val playlistItems: kotlinx.coroutines.flow.StateFlow<List<xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistItem>> = _playlistItems.asStateFlow()

  fun toggleOnlineSection() = _subtitleManager.toggleOnlineSection()

  // Cache for video metadata to avoid re-extracting — LruCache handles bounds + thread-safety
  private val metadataCache = object : android.util.LruCache<String, Pair<String, String>>(100) {}

  private fun updateMetadataCache(key: String, value: Pair<String, String>) {
    metadataCache.put(key, value)
  }

  // MPV properties with efficient collection
  val paused by MPVLib.propBoolean["pause"].collectAsState(viewModelScope)
  val pos by MPVLib.propInt["time-pos"].collectAsState(viewModelScope)
  private val _mpvDuration by MPVLib.propInt["duration"].collectAsState(viewModelScope)
  val duration: Int?
    get() = if (_externalAudioTracks.isNotEmpty() && (_primaryVideoDuration.value ?: 0.0) > 0.0) {
      _primaryVideoDuration.value?.toInt()
    } else {
      _preciseDuration.value.takeIf { it > 0f }?.toInt() ?: _mpvDuration.takeIf { !_isLoadingFile.value }
    }

  // External audio state and duration tracking
  private val _externalAudioTracks = mutableListOf<String>()
  val externalAudioTracks: List<String>
    get() = synchronized(_externalAudioTracks) { _externalAudioTracks.toList() }

  private val _primaryVideoDuration = MutableStateFlow<Double?>(null)
  val primaryVideoDuration: StateFlow<Double?> = _primaryVideoDuration.asStateFlow()

  private val _externalAudioEofEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val externalAudioEofEvent = _externalAudioEofEvent.asSharedFlow()

  // High-precision position and duration for smooth seekbar
  private val _precisePosition = MutableStateFlow(0f)
  val precisePosition = _precisePosition.asStateFlow()

  /**
   * True between asking mpv to load a file and mpv reporting it loaded.
   *
   * mpv keeps answering `time-pos`/`duration` for the *outgoing* file until the new one is open,
   * which on a network stream is seconds rather than milliseconds. Without this gate the seek bar
   * shows the previous video's position and length while the next one buffers.
   */
  private val _isLoadingFile = MutableStateFlow(false)
  val isLoadingFile: StateFlow<Boolean> = _isLoadingFile.asStateFlow()

  private val _preciseDuration = MutableStateFlow(0f)
  val preciseDuration = _preciseDuration.asStateFlow()

  // Audio state
  val currentVolume = MutableStateFlow(host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)
  
  // Gesture state for seekbar bouncing animation
  private val _isGestureSeeking = MutableStateFlow(false)
  val isGestureSeeking: StateFlow<Boolean> = _isGestureSeeking.asStateFlow()

  fun setGestureSeeking(isSeeking: Boolean) {
    _isGestureSeeking.value = isSeeking
  }

  // Gesture state for vertical bouncing animation
  val isVerticalGestureActive = MutableStateFlow(false)

  val maxVolume = host.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

  val subtitleTracks: StateFlow<List<TrackNode>> =
    observeTracks(json)
      .map { it.filter { t -> t.isSubtitle } }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val audioTracks: StateFlow<List<TrackNode>> =
    observeTracks(json)
      .map { it.filter { t -> t.isAudio } }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val chapters: StateFlow<List<dev.vivvvek.seeker.Segment>> =
    observeChapters(json)
      .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  // UI state
  private val _controlsShown = MutableStateFlow(false)
  val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()

  private val _seekBarShown = MutableStateFlow(false)
  val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()

  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)

  // Resume playback prompt state (for Ask Every Time mode)
  private val _resumePrompt = MutableStateFlow<ResumePromptData?>(null)
  val resumePrompt: StateFlow<ResumePromptData?> = _resumePrompt.asStateFlow()

  fun showResumePrompt(position: Int, duration: Int = 0) {
    _resumePrompt.value = ResumePromptData(position = position, duration = duration)
  }

  fun clearResumePrompt() {
    _resumePrompt.value = null
  }

  fun dismissResumePrompt() {
    _resumePrompt.value = null
    runCatching { MPVLib.setPropertyBoolean("pause", false) }
  }

  fun confirmResume(position: Int) {
    _resumePrompt.value = null
    seekTo(position)
    runCatching { MPVLib.setPropertyBoolean("pause", false) }
  }

  fun restartFromBeginning() {
    _resumePrompt.value = null
    seekTo(0)
    runCatching { MPVLib.setPropertyBoolean("pause", false) }
  }

  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val volumeSliderTimestamp = MutableStateFlow(0L)
  val brightnessSliderTimestamp = MutableStateFlow(0L)
  val currentBrightness =
    MutableStateFlow(
      runCatching {
        Settings.System
          .getFloat(host.hostContentResolver, Settings.System.SCREEN_BRIGHTNESS)
          .normalize(0f, 255f, 0f, 1f)
      }.getOrElse { 0f },
    )

  val sheetShown = MutableStateFlow(Sheets.None)
  val lastMoreSheetTab = MutableStateFlow(0) // Default to Controls tab (index 0)
  val panelShown = MutableStateFlow(Panels.None)
  val isSpeedLocked = MutableStateFlow(false)

  // Seek state
  private val _seekText = MutableStateFlow<String?>(null)
  val seekText: StateFlow<String?> = _seekText.asStateFlow()

  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount: StateFlow<Int> = _doubleTapSeekAmount.asStateFlow()

  // Captured position at the start of a double-tap seek sequence so the time
  // display stays stable while MPV's time-pos asynchronously catches up.
  private val _doubleTapSeekBasePos = MutableStateFlow<Int?>(null)
  val doubleTapSeekBasePos: StateFlow<Int?> = _doubleTapSeekBasePos.asStateFlow()

  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards: StateFlow<Boolean> = _isSeekingForwards.asStateFlow()

  // Frame navigation
  private val _currentFrame = MutableStateFlow(0)
  val currentFrame: StateFlow<Int> = _currentFrame.asStateFlow()

  private val _totalFrames = MutableStateFlow(0)
  val totalFrames: StateFlow<Int> = _totalFrames.asStateFlow()

  private val _isFrameNavigationExpanded = MutableStateFlow(false)
  val isFrameNavigationExpanded: StateFlow<Boolean> = _isFrameNavigationExpanded.asStateFlow()

  private val _isSnapshotLoading = MutableStateFlow(false)
  val isSnapshotLoading: StateFlow<Boolean> = _isSnapshotLoading.asStateFlow()

  // Video zoom
  private val _videoZoom = MutableStateFlow(0f)
  val videoZoom: StateFlow<Float> = _videoZoom.asStateFlow()

 // Video aspect ratio (now persisted via preferences)
  private val _videoAspect = MutableStateFlow(playerPreferences.defaultVideoAspect.get())
  val videoAspect: StateFlow<VideoAspect> = _videoAspect.asStateFlow()

  // Current aspect ratio value (for custom ratios and tracking)
  private val _currentAspectRatio = MutableStateFlow(
    if (playerPreferences.rememberVideoAspect.get()) {
      playerPreferences.defaultCustomAspectRatio.get()
    } else {
      -1.0
    }
  )
  val currentAspectRatio: StateFlow<Double> = _currentAspectRatio.asStateFlow()

  // Timer
  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

  // Media title for subtitle association
  private val _mediaTitle = MutableStateFlow("")
  val mediaTitle: StateFlow<String> = _mediaTitle.asStateFlow()

  private val _mediaIdentifier = MutableStateFlow("")
  val mediaIdentifier: StateFlow<String> = _mediaIdentifier.asStateFlow()

  fun setMediaIdentifier(identifier: String) {
    _mediaIdentifier.value = identifier
  }

  var currentMediaTitle: String
    get() = _mediaTitle.value
    set(value) { _mediaTitle.value = value }
  private var lastAutoSelectedMediaTitle: String? = null

  // External subtitle tracking
  val externalSubtitles: List<String> get() = _subtitleManager.externalSubtitles

  // Repeat and Shuffle state
  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  // A-B Loop state
  private val _abLoopA = MutableStateFlow<Double?>(null)
  val abLoopA: StateFlow<Double?> = _abLoopA.asStateFlow()

  private val _abLoopB = MutableStateFlow<Double?>(null)
  val abLoopB: StateFlow<Double?> = _abLoopB.asStateFlow()

  private val _isABLoopExpanded = MutableStateFlow(false)
  val isABLoopExpanded: StateFlow<Boolean> = _isABLoopExpanded.asStateFlow()

  // Mirroring state
  private val _isMirrored = MutableStateFlow(false)
  val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

  // Vertical flip state
  private val _isVerticalFlipped = MutableStateFlow(false)
  val isVerticalFlipped: StateFlow<Boolean> = _isVerticalFlipped.asStateFlow()

  // ==================== Ambience Mode ======================================
  // Ambient mode manager handles all ambient mode functionality
  private val ambientModeManager = AmbientModeManager(
    playerPreferences = playerPreferences,
    cacheDir = host.context.cacheDir,
    scope = viewModelScope,
    onShowText = { isOn ->
      val text = host.context.getString(
        if (isOn) R.string.ambient_mode_on else R.string.ambient_mode_off
      )
      playerUpdate.value = PlayerUpdates.ShowText(text)
    }
  )

  // Expose ambient mode state through the manager
  val isAmbientEnabled: StateFlow<Boolean> = ambientModeManager.isAmbientEnabled

  // ==================== Screen Unlock Resume ===============================

  private val _screenStateManager = ScreenStateManager(
    context = appContext,
    playerPreferences = playerPreferences,
    onResumePlayback = { unpause() },
    isPaused = { paused ?: true }
  )
  val screenStateManager: ScreenStateManager get() = _screenStateManager

  var isActivityResumed: Boolean
    get() = _screenStateManager.isActivityResumed
    set(value) { _screenStateManager.isActivityResumed = value }

  var isActivityStarted: Boolean
    get() = _screenStateManager.isActivityStarted
    set(value) { _screenStateManager.isActivityStarted = value }

  var wasPlayingBeforePause: Boolean
    get() = _screenStateManager.wasPlayingBeforePause
    set(value) { _screenStateManager.wasPlayingBeforePause = value }

  fun setupScreenStateReceiver() {
    _screenStateManager.setup()
  }

  fun handlePendingResumeOnUnlock() {
    _screenStateManager.handlePendingResumeOnUnlock()
  }

  init {
    // Track selection is now handled by TrackSelector in PlayerActivity
    
    // Restore repeat mode and shuffle state from preferences
    _repeatMode.value = playerPreferences.repeatMode.get()
    _shuffleEnabled.value = playerPreferences.shuffleEnabled.get()

    // Observe repeat mode preference changes
    viewModelScope.launch {
      playerPreferences.repeatMode.changes().collect { mode ->
        _repeatMode.value = mode
        miniPlayerStateManager.updateState(
          repeatMode = mode,
          hasNext = hasNext(),
          hasPrevious = hasPrevious(),
        )
      }
    }

    // Observe shuffle enabled preference changes
    viewModelScope.launch {
      playerPreferences.shuffleEnabled.changes().collect { enabled ->
        _shuffleEnabled.value = enabled
        _playlistManager.setShuffleEnabled(enabled)
        miniPlayerStateManager.updateState(
          shuffleEnabled = enabled,
          hasNext = hasNext(),
          hasPrevious = hasPrevious(),
        )
      }
    }

    // Observe volume boost cap changes to enforce limits dynamically (in PiP)
    viewModelScope.launch {
      audioPreferences.volumeBoostCap.changes().collect { cap ->
        val maxVol = 100 + cap
        runCatching {
          MPVLib.setPropertyString("volume-max", maxVol.toString())
          
          // Clamp current volume if it exceeds the new limit
          val currentMpvVol = MPVLib.getPropertyInt("volume") ?: 100
          if (currentMpvVol > maxVol) {
            MPVLib.setPropertyInt("volume", maxVol)
          }
        }.onFailure { e ->
          Log.e(TAG, "Error setting volume-max: $maxVol", e)
        }
      }
    }

    // Monitor duration and AB loop changes to automatically enable precise seeking
    viewModelScope.launch {
      combine(
        MPVLib.propInt["duration"],
        abLoopA,
        abLoopB
      ) { duration, loopA, loopB ->
        Triple(duration, loopA, loopB)
      }.collect { (duration, loopA, loopB) ->
        val videoDuration = duration ?: 0
        // Use precise seeking for videos shorter than 2 minutes, or if AB loop is active, or if preference is enabled
        val isLoopActive = loopA != null || loopB != null
        val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || (videoDuration in 1..119) || isLoopActive
        
        // Update hr-seek settings dynamically
        MPVLib.setPropertyString("hr-seek", if (shouldUsePreciseSeeking) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", if (shouldUsePreciseSeeking) "no" else "yes")
      }
    }
    
    
    // Refresh custom buttons when Lua scripts are enabled/disabled or configuration changes
    viewModelScope.launch {
      combine(
        advancedPreferences.enableLuaScripts.changes().drop(1),
        playerPreferences.customButtons.changes().drop(1)
      ) { _, _ -> }.collect {
        _customButtonManager.setup()
      }
    }

    _customButtonManager.setup()

    // Poll precise position when controls, seekbar, or gesture seek is visible (whether playing or paused)
    viewModelScope.launch {
      combine(
        controlsShown,
        seekBarShown,
        isGestureSeeking
      ) { controlsVisible, seekbarVisible, gestureSeeking ->
        controlsVisible || seekbarVisible || gestureSeeking
      }.collectLatest { shouldPoll ->
        if (shouldPoll) {
          while (isActive) {
            val time = if (_isLoadingFile.value) null else MPVLib.getPropertyDouble("time-pos")
            if (time != null) {
              val primaryDur = _primaryVideoDuration.value
              if (_externalAudioTracks.isNotEmpty() && primaryDur != null && primaryDur > 0) {
                if (time >= primaryDur - 0.25) {
                  _precisePosition.value = primaryDur.toFloat()
                  _externalAudioEofEvent.tryEmit(Unit)
                  delay(16)
                  continue
                }
              }
              _precisePosition.value = time.toFloat()
            }
            delay(16) // ~60fps updates
          }
        }
      }
    }

    // Update precise position whenever integer time-pos changes in MPV (e.g. seeking while paused or controls hidden)
    viewModelScope.launch {
      MPVLib.propInt["time-pos"].collect { _ ->
        val time = if (_isLoadingFile.value) null else MPVLib.getPropertyDouble("time-pos")
        if (time != null) {
          val primaryDur = _primaryVideoDuration.value
          if (_externalAudioTracks.isNotEmpty() && primaryDur != null && primaryDur > 0) {
            if (time >= primaryDur - 0.25) {
              _precisePosition.value = primaryDur.toFloat()
              _externalAudioEofEvent.tryEmit(Unit)
              return@collect
            }
          }
          _precisePosition.value = time.toFloat()
        }
      }
    }

    // Update precise duration when the integer duration changes (avoid polling)
    viewModelScope.launch {
      MPVLib.propInt["duration"].collect { _ ->
        val dur = MPVLib.getPropertyDouble("duration")
        if (dur != null && dur > 0) {
          if (_externalAudioTracks.isEmpty()) {
            _primaryVideoDuration.value = dur
          }
          val effectiveDur = if (_externalAudioTracks.isNotEmpty() && (_primaryVideoDuration.value ?: 0.0) > 0.0) {
            _primaryVideoDuration.value!!
          } else {
            dur
          }
          _preciseDuration.value = effectiveDur.toFloat()

          // --- AMBIENT FIX: Adapt shader to new file dimensions ---
          ambientModeManager.resetAmbientMode()
          viewModelScope.launch {
            // Slight delay ensures MPV's video-params (w/h/crop) are fully populated
            delay(250)
            ambientModeManager.updateAmbientStretch()
          }
          // --------------------------------------------------------
        } else if (dur == null || dur <= 0) {
          if (_externalAudioTracks.isEmpty()) {
            _primaryVideoDuration.value = null
            _preciseDuration.value = 0f
          }
        }
      }
    }
  }

  fun onMpvCoreInitialized() {
    _customButtonManager.onMpvInitialized()
  }

  // ==================== Custom Buttons ====================

    val customButtons = _customButtonManager.customButtons

    fun callCustomButton(id: String) = _customButtonManager.callButton(id)

    fun callCustomButtonLongPress(id: String) = _customButtonManager.callButtonLongPress(id)

  // Cached values
  private val doubleTapToSeekDuration by lazy { gesturePreferences.doubleTapToSeekDuration.get() }
  private val inputMethodManager by lazy {
    host.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  }

  private companion object {
    const val TAG = "PlayerViewModel"
    val VALID_SUBTITLE_EXTENSIONS =
      setOf(
        // Common & modern
        "srt", "vtt", "ass", "ssa",
        // DVD / Blu-ray
        "sub", "idx", "sup",
        // Streaming / XML / Professional
        "xml", "ttml", "dfxp", "itt", "ebu", "imsc", "usf",
        // Online platforms
        "sbv", "srv1", "srv2", "srv3", "json",
        // Legacy & niche
        "sami", "smi", "mpl", "pjs", "stl", "rt", "psb", "cap",
        // Broadcast captions
        "scc", "vttx",
        // Karaoke / lyrics
        "lrc", "krc",
        // Fallback / raw text
        "txt", "pgs"
      )
  }

  // ==================== Timer ====================

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return

    timerJob =
      viewModelScope.launch {
        for (time in seconds downTo 0) {
          _remainingTime.value = time
          delay(1000)
        }
        MPVLib.setPropertyBoolean("pause", true)
        showToast(host.context.getString(R.string.toast_sleep_timer_ended))
      }
  }

  // ==================== Decoder ====================

  // ==================== Audio/Subtitle Management ====================

  fun addAudio(uri: Uri, select: Boolean = true, silent: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        // Save primary video duration before adding external audio if not saved yet
        if (_primaryVideoDuration.value == null || (_primaryVideoDuration.value ?: 0.0) <= 0.0) {
          val currentDur = MPVLib.getPropertyDouble("duration")
          if (currentDur != null && currentDur > 0) {
            _primaryVideoDuration.value = currentDur
          }
        }

        val path =
          uri.resolveUri(host.context)
            ?: return@launch withContext(Dispatchers.Main) {
              if (!silent) showToast("Failed to load audio file: Invalid URI")
            }

        synchronized(_externalAudioTracks) {
          val uriStr = uri.toString()
          if (!_externalAudioTracks.contains(uriStr)) {
            _externalAudioTracks.add(uriStr)
          }
        }

        val flag = if (select) "select" else "cached"
        MPVLib.command("audio-add", path, flag)

        _primaryVideoDuration.value?.let { primDur ->
          if (primDur > 0) {
            _preciseDuration.value = primDur.toFloat()
          }
        }

        if (!silent) {
          withContext(Dispatchers.Main) {
            showToast("Audio track added")
          }
        }
      }.onFailure { e ->
        if (!silent) {
          withContext(Dispatchers.Main) {
            showToast("Failed to load audio: ${e.message}")
          }
        }
        android.util.Log.e("PlayerViewModel", "Error adding audio", e)
      }
    }
  }

  fun resetExternalAudioTracks() {
    synchronized(_externalAudioTracks) {
      _externalAudioTracks.clear()
    }
    _primaryVideoDuration.value = null
  }

  fun prepareForFileLoad(initialDurationSec: Float? = null) {
    _isLoadingFile.value = true
    resetExternalAudioTracks()
    _precisePosition.value = 0f
    if (initialDurationSec != null && initialDurationSec > 0f) {
      _primaryVideoDuration.value = initialDurationSec.toDouble()
      _preciseDuration.value = initialDurationSec
    } else {
      _primaryVideoDuration.value = null
      _preciseDuration.value = 0f
    }
  }

  fun onFileStartLoading() {
    if (_externalAudioTracks.isEmpty()) {
      _precisePosition.value = 0f
      if (_primaryVideoDuration.value == null) {
        _preciseDuration.value = 0f
      }
    }
  }

  fun onFileLoaded(durationSec: Double) {
    _isLoadingFile.value = false
    if (durationSec > 0) {
      if (_externalAudioTracks.isEmpty()) {
        _primaryVideoDuration.value = durationSec
        _preciseDuration.value = durationSec.toFloat()
      }
    }
  }

  fun addSubtitle(uri: Uri, select: Boolean = true, silent: Boolean = false) = _subtitleManager.addSubtitle(uri, select, silent)

  private fun scanLocalSubtitles(mediaTitle: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val saveFolderUri = subtitlesPreferences.subtitleSaveFolder.get()
      if (saveFolderUri.isBlank()) return@launch
      
      try {
        val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
        val fullTitle = mediaTitle.substringBeforeLast(".")
        val checksumTitle = ChecksumUtils.getCRC32(mediaTitle)
        val parentDir = DocumentFile.fromTreeUri(host.context, Uri.parse(saveFolderUri)) ?: return@launch
        
        // Scan potential folder names for compatibility: checksum, full, and sanitized
        listOf(checksumTitle, fullTitle, sanitizedTitle).distinct().forEach { folderName ->
          val movieDir = parentDir.findFile(folderName) ?: return@forEach
          if (movieDir.isDirectory) {
            movieDir.listFiles().forEach { file ->
              val fileName = file.name ?: ""
              val lower = fileName.lowercase()
              val isValid = lower.endsWith(".srt") || lower.endsWith(".vtt") ||
                            lower.endsWith(".ssa") || lower.endsWith(".ass")
              
              if (file.isFile && isValid) {
                withContext(Dispatchers.Main) {
                  // Don't auto-select during scan, just make available
                  addSubtitle(file.uri, select = false, silent = true)
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error scanning local subtitles: ${e.message}", e)
      }
    }
  }

  fun setMediaTitle(mediaTitle: String) {
    if (currentMediaTitle != mediaTitle) {
      currentMediaTitle = mediaTitle
      lastAutoSelectedMediaTitle = null
      // Clear external subtitles when media changes
      _subtitleManager.clearExternalSubtitles()
      // Scan for previously downloaded/added subtitles
      scanLocalSubtitles(mediaTitle)
    }
  }


  fun removeSubtitle(id: Int) = _subtitleManager.removeSubtitle(id, subtitleTracks.value)

  // --- Media Search and Series Management ---

  fun searchMedia(query: String) = _subtitleManager.searchMedia(query)

  fun selectMedia(result: xyz.mpv.rex.repository.wyzie.WyzieTmdbResult) = _subtitleManager.selectMedia(result)

  fun selectSeason(season: xyz.mpv.rex.repository.wyzie.WyzieSeason) = _subtitleManager.selectSeason(season)

  fun selectEpisode(episode: xyz.mpv.rex.repository.wyzie.WyzieEpisode) = _subtitleManager.selectEpisode(episode, currentMediaTitle)

  fun clearMediaSelection() = _subtitleManager.clearMediaSelection()

  // --- Subtitle Search ---
  fun searchSubtitles(query: String, season: Int? = null, episode: Int? = null, year: String? = null) = _subtitleManager.searchSubtitles(query, season, episode, year)

  fun downloadSubtitle(subtitle: WyzieSubtitle) = _subtitleManager.downloadSubtitle(subtitle, currentMediaTitle)


  fun toggleSubtitle(id: Int) {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0

    when {
      id == primarySid -> {
        // Unselecting primary subtitle
        if (secondarySid > 0) {
          // If there's a secondary subtitle, promote it to primary
          val secondaryToPromote = secondarySid
          MPVLib.setPropertyString("secondary-sid", "no")
          MPVLib.setPropertyInt("sid", secondaryToPromote)
          val overrideAssSubs = subtitlesPreferences.overrideAssSubs.get()
          MPVLib.setPropertyString("sub-ass-override", if (overrideAssSubs) "force" else "scale")
          MPVLib.setPropertyInt("sub-pos", subtitlesPreferences.subPos.get())
          MPVLib.setPropertyFloat("sub-scale", subtitlesPreferences.subScale.get())
          val track = subtitleTracks.value.firstOrNull { it.id == secondaryToPromote }
          TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
        } else {
          // No secondary, just turn off primary
          MPVLib.setPropertyString("sid", "no")
          TrackSelector.rememberSubtitleTrack(null, null, isOff = true)
        }
      }
      id == secondarySid -> MPVLib.setPropertyString("secondary-sid", "no")
      primarySid <= 0 -> {
        MPVLib.setPropertyInt("sid", id)
        val track = subtitleTracks.value.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
      secondarySid <= 0 -> {
        MPVLib.setPropertyString("secondary-sub-ass-override", "force")
        MPVLib.setPropertyInt("secondary-sub-pos", subtitlesPreferences.secondarySubPos.get())
        MPVLib.setPropertyFloat("secondary-sub-scale", subtitlesPreferences.secondarySubScale.get())
        MPVLib.setPropertyInt("secondary-sid", id)
      }
      else -> {
        MPVLib.setPropertyInt("sid", id)
        val track = subtitleTracks.value.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
    }
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    return (id == primarySid && primarySid > 0) || (id == secondarySid && secondarySid > 0)
  }

  fun selectAudioTrack(id: Int, title: String?, lang: String?) {
    val currentAid = MPVLib.getPropertyInt("aid") ?: 0
    if (currentAid == id) {
      MPVLib.setPropertyString("aid", "no")
      TrackSelector.rememberAudioTrack(null, null)
    } else {
      MPVLib.setPropertyInt("aid", id)
      TrackSelector.rememberAudioTrack(title, lang)
      _playbackManager.resyncAudioOnTrackChange(viewModelScope)
    }
  }

  private fun getFileNameFromUri(uri: Uri): String? =
    when (uri.scheme) {
      "content" ->
        host.context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }

      "file" -> uri.lastPathSegment
      else -> uri.lastPathSegment
    }

  private fun isValidSubtitleFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in VALID_SUBTITLE_EXTENSIONS

  // ==================== Playback Control ====================

  fun pauseUnpause() {
    viewModelScope.launch(Dispatchers.IO) {
      val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
      if (isPaused) {
        // We are about to unpause, so request focus
        withContext(Dispatchers.Main) { host.requestAudioFocus() }
        MPVLib.setPropertyBoolean("pause", false)
      } else {
        // We are about to pause
        wasPlayingBeforePause = false
        MPVLib.setPropertyBoolean("pause", true)
        withContext(Dispatchers.Main) { host.abandonAudioFocus() }
      }
    }
  }

  fun pause() {
    viewModelScope.launch(Dispatchers.IO) {
      wasPlayingBeforePause = false
      MPVLib.setPropertyBoolean("pause", true)
      withContext(Dispatchers.Main) { host.abandonAudioFocus() }
    }
  }

  fun unpause() {
    viewModelScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) { host.requestAudioFocus() }
      MPVLib.setPropertyBoolean("pause", false)
    }
  }

  // ==================== UI Control ====================

  fun showControls() {
    if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        host.windowInsetsController.isAppearanceLightStatusBars = false
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      // Defensive: InsetsController animation can crash under FD pressure
      // (e.g. during high-res HEVC playback on certain devices)
      Log.e(TAG, "Failed to show system bars", e)
    }
    _controlsShown.value = true
  }

  fun hideControls() {
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = false
  }

  fun autoHideControls() {
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = true
  }

  fun showSeekBar() {
    if (sheetShown.value == Sheets.None) {
      _seekBarShown.value = true
    }
  }

  fun hideSeekBar() {
    _seekBarShown.value = false
  }

  fun lockControls() {
    _areControlsLocked.value = true
  }

  fun unlockControls() {
    _areControlsLocked.value = false
  }

  // ==================== Seeking ====================

  fun seekBy(offset: Int) {
    _playbackManager.seekBy(viewModelScope, offset)
  }

  fun seekTo(position: Int) {
    _playbackManager.seekTo(viewModelScope, position, _abLoopA.value, _abLoopB.value)
  }

  fun setPlaybackSpeed(speed: Float) {
    _playbackManager.setSpeed(speed)
  }

  fun resetPlaybackSpeed() {
    _playbackManager.resetSpeed()
  }

  fun setSubtitleSpeed(speed: Double) {
    _playbackManager.setSubSpeed(speed)
  }

  fun leftSeek() {
    if (_doubleTapSeekAmount.value == 0) _doubleTapSeekBasePos.value = pos
    if ((pos ?: 0) > 0) {
      _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    }
    _isSeekingForwards.value = false
    seekBy(-doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun rightSeek() {
    if (_doubleTapSeekAmount.value == 0) _doubleTapSeekBasePos.value = pos
    val curDuration = duration ?: 0
    if (curDuration <= 0 || (pos ?: 0) < curDuration) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    seekBy(doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun leftSubSeek() {
    val sid = MPVLib.getPropertyInt("sid") ?: 0
    if (sid != 0) {
      val pos1 = MPVLib.getPropertyDouble("time-pos") ?: 0.0
      MPVLib.command("sub-seek", "-1")

      viewModelScope.launch(Dispatchers.IO) {
        kotlinx.coroutines.delay(50)
        val pos2 = MPVLib.getPropertyDouble("time-pos") ?: pos1
        val diff = pos2 - pos1
        _isSeekingForwards.value = false
        _doubleTapSeekAmount.value += diff.toInt()
      }
      if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
    } else leftSeek()
  }

  fun rightSubSeek() {
    val sid = MPVLib.getPropertyInt("sid") ?: 0
    if (sid != 0) {
      val pos1 = MPVLib.getPropertyDouble("time-pos") ?: 0.0
      MPVLib.command("sub-seek", "1")

      viewModelScope.launch(Dispatchers.IO) {
        kotlinx.coroutines.delay(50)
        val pos2 = MPVLib.getPropertyDouble("time-pos") ?: pos1
        val diff = pos2 - pos1
        _isSeekingForwards.value = true
        _doubleTapSeekAmount.value += diff.toInt()
      }
      if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
    } else rightSeek()
  }

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.value = amount
    if (amount == 0) _doubleTapSeekBasePos.value = null
  }

  fun updateSeekText(text: String?) {
    _seekText.value = text
  }

  fun updateIsSeekingForwards(isForwards: Boolean) {
    _isSeekingForwards.value = isForwards
  }

  private fun seekToWithText(
    seekValue: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    _isSeekingForwards.value = seekValue > currentPos
    _doubleTapSeekAmount.value = seekValue - currentPos
    _seekText.value = text
    seekTo(seekValue)
  }

  private fun seekByWithText(
    value: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    val maxDuration = duration ?: 0

    _doubleTapSeekAmount.update {
      if ((value < 0 && it < 0) || (maxDuration > 0 && currentPos + value > maxDuration)) 0 else it + value
    }
    _seekText.value = text
    _isSeekingForwards.value = value > 0
    seekBy(value)
  }

  // ==================== Brightness & Volume ====================

  fun changeBrightnessTo(brightness: Float) {
    val coercedBrightness = brightness.coerceIn(0f, 1f)
    host.hostWindow.attributes =
      host.hostWindow.attributes.apply {
        screenBrightness = coercedBrightness
      }
    currentBrightness.value = coercedBrightness

    // Save brightness to preferences if enabled
    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.set(coercedBrightness)
    }
  }

  fun displayBrightnessSlider() {
    isBrightnessSliderShown.value = true
    brightnessSliderTimestamp.value = System.currentTimeMillis()
  }

  fun changeVolumeBy(change: Int) {
    val mpvVolume = MPVLib.getPropertyInt("volume")
    val absoluteMaxVolume = volumeBoostCap ?: (audioPreferences.volumeBoostCap.get() + 100)

    if (absoluteMaxVolume > 100 && currentVolume.value == maxVolume) {
      if (mpvVolume == 100 && change < 0) {
        changeVolumeTo(currentVolume.value + change)
      }
      val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
      if (finalMPVVolume in 100..absoluteMaxVolume) {
        return changeMPVVolumeTo(finalMPVVolume)
      }
    }
    changeVolumeTo(currentVolume.value + change)
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    currentVolume.value = newVolume
  }

  fun changeMPVVolumeTo(volume: Int) {
    MPVLib.setPropertyInt("volume", volume)
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.value = true
    volumeSliderTimestamp.value = System.currentTimeMillis()
  }

  // ==================== Video Aspect ====================

  private var _cachedVideoRotation = 0

  // Restores the user's preferred video aspect ratio and restores pan/zoom settings
  fun resetVisualPreferences() {

    // Cache the video rotation once it's available (used by stretch mode)
    _cachedVideoRotation = MPVLib.getPropertyInt("video-params/rotate") ?: 0
    
    // 1. Apply saved aspect ratio preference without overriding active zoom and pan
    val savedAspect = playerPreferences.defaultVideoAspect.get()
    val savedCustomRatio = if (playerPreferences.rememberVideoAspect.get()) {
      playerPreferences.defaultCustomAspectRatio.get()
    } else {
      -1.0
    }

    if (savedCustomRatio > 0) {
      setCustomAspectRatio(savedCustomRatio, resetZoomAndPan = false, showUpdate = false, persistToPreferences = false)
    } else {
      changeVideoAspect(savedAspect, showUpdate = false, resetZoomAndPan = false, persistToPreferences = false)
    }

    // 2. Load zoom and pan preferences or sync from mpv.conf/engine defaults
    val prefZoom = playerPreferences.defaultVideoZoom.get()
    val prefPanX = playerPreferences.defaultVideoPanX.get()
    val prefPanY = playerPreferences.defaultVideoPanY.get()

    if (prefZoom != 0f) {
      MPVLib.setPropertyDouble("video-zoom", prefZoom.toDouble())
      _videoZoom.value = prefZoom
    } else {
      val mpvZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
      _videoZoom.value = mpvZoom
    }

    if (prefPanX != 0f || prefPanY != 0f) {
      MPVLib.setPropertyDouble("video-pan-x", prefPanX.toDouble())
      MPVLib.setPropertyDouble("video-pan-y", prefPanY.toDouble())
      _videoPanX.value = prefPanX
      _videoPanY.value = prefPanY
    } else {
      val mpvPanX = MPVLib.getPropertyDouble("video-pan-x")?.toFloat() ?: 0f
      val mpvPanY = MPVLib.getPropertyDouble("video-pan-y")?.toFloat() ?: 0f
      _videoPanX.value = mpvPanX
      _videoPanY.value = mpvPanY
    }

    val prefAdvancedZoomEnabled = playerPreferences.advancedZoomEnabled.get()
    _advancedZoomEnabled.value = prefAdvancedZoomEnabled
    if (prefAdvancedZoomEnabled) {
      val prefScaleX = playerPreferences.defaultVideoScaleX.get()
      val prefScaleY = playerPreferences.defaultVideoScaleY.get()
      MPVLib.setPropertyDouble("video-scale-x", prefScaleX.toDouble())
      MPVLib.setPropertyDouble("video-scale-y", prefScaleY.toDouble())
      _videoScaleX.value = prefScaleX
      _videoScaleY.value = prefScaleY
    } else {
      MPVLib.setPropertyDouble("video-scale-x", 1.0)
      MPVLib.setPropertyDouble("video-scale-y", 1.0)
      _videoScaleX.value = 1f
      _videoScaleY.value = 1f
    }
  }

  fun changeVideoAspect(
    aspect: VideoAspect,
    showUpdate: Boolean = true,
    resetZoomAndPan: Boolean = true,
    persistToPreferences: Boolean = true,
  ) {
    if (resetZoomAndPan) {
      setVideoZoom(0f)
      setVideoPan(0f, 0f)
    }
    when (aspect) {
      VideoAspect.Fit -> {
        // To FIT: Reset both properties to their defaults.
        MPVLib.setPropertyDouble("panscan", 0.0)
        MPVLib.setPropertyDouble("video-aspect-override", -1.0)
      }
      VideoAspect.Crop -> {
        // To CROP: Reset aspect override first, then set panscan
        MPVLib.setPropertyDouble("video-aspect-override", -1.0)
        MPVLib.setPropertyDouble("panscan", 1.0)
      }
      VideoAspect.Stretch -> {
        // To STRETCH: Calculate screen ratio accounting for video rotation
        @Suppress("DEPRECATION")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        host.hostWindowManager.defaultDisplay.getRealMetrics(dm)
        
        val isVideoRotated = (_cachedVideoRotation % 180 == 90)
        
        // Calculate screen ratio, inverting if video is rotated
        val screenRatio = if (isVideoRotated) {
          // Video is rotated, so invert the screen ratio
          dm.heightPixels.toDouble() / dm.widthPixels.toDouble()
        } else {
          // Video is not rotated, use normal screen ratio
          dm.widthPixels.toDouble() / dm.heightPixels.toDouble()
        }

        // Set aspect override first, then reset panscan
        // This prevents the brief flash of Fit mode
        MPVLib.setPropertyDouble("video-aspect-override", screenRatio)
        MPVLib.setPropertyDouble("panscan", 0.0)
      }
    }

    // Update the state and persist to preferences
    _videoAspect.value = aspect
    _currentAspectRatio.value = -1.0 // Reset custom ratio when using standard modes
    if (persistToPreferences && playerPreferences.rememberVideoAspect.get()) {
      playerPreferences.defaultVideoAspect.set(aspect)
      playerPreferences.defaultCustomAspectRatio.set(-1.0)
    }

    // Notify the UI
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  fun setCustomAspectRatio(
    ratio: Double,
    resetZoomAndPan: Boolean = true,
    showUpdate: Boolean = true,
    persistToPreferences: Boolean = true,
  ) {
    if (resetZoomAndPan) {
      setVideoZoom(0f)
      setVideoPan(0f, 0f)
    }
    MPVLib.setPropertyDouble("panscan", 0.0)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    _currentAspectRatio.value = ratio
    if (persistToPreferences && playerPreferences.rememberVideoAspect.get()) {
      playerPreferences.defaultCustomAspectRatio.set(ratio)
    }
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  // ==================== Screen Rotation ====================

  fun cycleScreenRotations() {
    // Temporarily cycle orientation WITHOUT modifying preferences
    // Preferences remain the single source of truth and will be reapplied on next video
    host.hostRequestedOrientation =
      when (host.hostRequestedOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        -> {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        else -> {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
      }
  }

  // ==================== Lua Invocation Handling ====================

  fun handleLuaInvocation(
    property: String,
    value: String,
  ) {
    val data = value.removeSurrounding("\"").ifEmpty { return }

    when (property.substringAfterLast("/")) {
      "show_text" -> playerUpdate.value = PlayerUpdates.ShowText(data)
      "toggle_ui" -> handleToggleUI(data)
      "show_panel" -> handleShowPanel(data)
      "seek_to_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekToWithText(seekValue.toInt(), text)
      }
      "seek_by_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekByWithText(seekValue.toInt(), text)
      }
      "seek_by" -> seekByWithText(data.toInt(), null)
      "seek_to" -> seekToWithText(data.toInt(), null)
      "software_keyboard" -> handleSoftwareKeyboard(data)
    }

    MPVLib.setPropertyString(property, "")
  }

  private fun handleToggleUI(data: String) {
    when (data) {
      "show" -> showControls()
      "toggle" -> if (controlsShown.value) hideControls() else showControls()
      "hide" -> {
        sheetShown.value = Sheets.None
        panelShown.value = Panels.None
        hideControls()
      }
    }
  }

  private fun handleShowPanel(data: String) {
    when (data) {
      "frame_navigation" -> {
        sheetShown.value = Sheets.FrameNavigation
      }
      else -> {
        panelShown.value =
          when (data) {
            "subtitle_settings" -> Panels.SubtitleSettings
            "subtitle_delay" -> Panels.SubtitleDelay
            "audio_delay" -> Panels.AudioDelay
            "video_filters" -> Panels.VideoFilters
            else -> Panels.None
          }
      }
    }
  }

  private fun handleSoftwareKeyboard(data: String) {
    when (data) {
      "show" -> forceShowSoftwareKeyboard()
      "hide" -> forceHideSoftwareKeyboard()
      "toggle" ->
        if (!inputMethodManager.isActive) {
          forceShowSoftwareKeyboard()
        } else {
          forceHideSoftwareKeyboard()
        }
    }
  }

  @Suppress("DEPRECATION")
  private fun forceShowSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
  }

  @Suppress("DEPRECATION")
  private fun forceHideSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
  }

  // ==================== Gesture Handling ====================

  private fun executeGestureAction(
    action: SingleActionGesture,
    isLeft: Boolean = false,
    isRight: Boolean = false,
  ) {
    when (action) {
      SingleActionGesture.Seek -> {
        if (isLeft) leftSeek() else if (isRight) rightSeek()
      }
      SingleActionGesture.SubSeek -> {
        if (isLeft) leftSubSeek() else if (isRight) rightSubSeek()
      }
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> {
        viewModelScope.launch(Dispatchers.IO) {
          val keyCode = when {
            isLeft -> CustomKeyCodes.DoubleTapLeft.keyCode
            isRight -> CustomKeyCodes.DoubleTapRight.keyCode
            else -> CustomKeyCodes.DoubleTapCenter.keyCode
          }
          MPVLib.command("keypress", keyCode)
        }
      }
      SingleActionGesture.PlaylistNext -> playNext()
      SingleActionGesture.PlaylistPrev -> playPrevious()
      SingleActionGesture.None -> {}
    }
  }

  fun handleLeftDoubleTap() {
    executeGestureAction(gesturePreferences.leftSingleActionGesture.get(), isLeft = true)
  }

  fun handleCenterDoubleTap() {
    executeGestureAction(gesturePreferences.centerSingleActionGesture.get())
  }

  fun handleCenterSingleTap() {
    executeGestureAction(gesturePreferences.centerSingleActionGesture.get())
  }

  fun handleLeftSingleTap() {
    executeGestureAction(gesturePreferences.leftSingleActionGesture.get(), isLeft = true)
  }

  fun handleRightSingleTap() {
    executeGestureAction(gesturePreferences.rightSingleActionGesture.get(), isRight = true)
  }

  fun handleRightDoubleTap() {
    executeGestureAction(gesturePreferences.rightSingleActionGesture.get(), isRight = true)
  }

  fun handleMediaPlayPause() {
    executeGestureAction(gesturePreferences.mediaPlayGesture.get())
  }

  fun handleMediaNext() {
    executeGestureAction(gesturePreferences.mediaNextGesture.get(), isRight = true)
  }

  fun handleMediaPrevious() {
    executeGestureAction(gesturePreferences.mediaPreviousGesture.get(), isLeft = true)
  }

  // ==================== Video Zoom ====================

  fun setVideoZoom(zoom: Float) {
    _videoZoom.value = zoom
    MPVLib.setPropertyDouble("video-zoom", zoom.toDouble())
  }

  // Video pan (for pan & zoom feature)
  private val _videoPanX = MutableStateFlow(0f)
  val videoPanX: StateFlow<Float> = _videoPanX.asStateFlow()

  private val _videoPanY = MutableStateFlow(0f)
  val videoPanY: StateFlow<Float> = _videoPanY.asStateFlow()

  fun setVideoPan(x: Float, y: Float) {
    _videoPanX.value = x
    _videoPanY.value = y
    MPVLib.setPropertyDouble("video-pan-x", x.toDouble())
    MPVLib.setPropertyDouble("video-pan-y", y.toDouble())
  }

  fun resetVideoPan() {
    setVideoPan(0f, 0f)
  }

  fun resetVideoZoom() {
    setVideoZoom(0f)
  }

  private val _advancedZoomEnabled = MutableStateFlow(false)
  val advancedZoomEnabled: StateFlow<Boolean> = _advancedZoomEnabled.asStateFlow()

  private val _videoScaleX = MutableStateFlow(1f)
  val videoScaleX: StateFlow<Float> = _videoScaleX.asStateFlow()

  private val _videoScaleY = MutableStateFlow(1f)
  val videoScaleY: StateFlow<Float> = _videoScaleY.asStateFlow()

  fun setAdvancedZoomEnabled(enabled: Boolean) {
    _advancedZoomEnabled.value = enabled
    if (enabled) {
      setVideoZoom(0f)
    } else {
      setVideoScaleX(1f)
      setVideoScaleY(1f)
    }
  }

  fun setVideoScaleX(scale: Float) {
    _videoScaleX.value = scale
    MPVLib.setPropertyDouble("video-scale-x", scale.toDouble())
  }

  fun setVideoScaleY(scale: Float) {
    _videoScaleY.value = scale
    MPVLib.setPropertyDouble("video-scale-y", scale.toDouble())
  }

  fun resetAdvancedZoom() {
    setVideoScaleX(1f)
    setVideoScaleY(1f)
  }

  // ==================== Frame Navigation ====================

  fun updateFrameInfo() {
    _currentFrame.value = MPVLib.getPropertyInt("estimated-frame-number") ?: 0

    val durationValue = MPVLib.getPropertyDouble("duration") ?: 0.0
    val fps =
      MPVLib.getPropertyDouble("container-fps")
        ?: MPVLib.getPropertyDouble("estimated-vf-fps")
        ?: 0.0

    _totalFrames.value =
      if (durationValue > 0 && fps > 0) {
        (durationValue * fps).toInt()
      } else {
        0
      }
  }

  fun toggleFrameNavigationExpanded() {
    val wasExpanded = _isFrameNavigationExpanded.value
    _isFrameNavigationExpanded.update { !it }
    // Update frame info and pause when expanding (going from false to true)
    if (!wasExpanded) {
      // Pause the video if it's playing
      if (paused != true) {
        pauseUnpause()
      }
      updateFrameInfo()
      showFrameInfoOverlay()
    }
  }

  private fun showFrameInfoOverlay() {
    playerUpdate.value = PlayerUpdates.FrameInfo(_currentFrame.value, _totalFrames.value)
  }

  fun frameStepForward() {
    viewModelScope.launch(Dispatchers.IO) {
      if (paused != true) {
        pauseUnpause()
        delay(50)
      }
      MPVLib.command("no-osd", "frame-step")
      delay(100)
      updateFrameInfo()
      withContext(Dispatchers.Main) {
        showFrameInfoOverlay()
      }
    }
  }

  fun frameStepBackward() {
    viewModelScope.launch(Dispatchers.IO) {
      if (paused != true) {
        pauseUnpause()
        delay(50)
      }
      MPVLib.command("no-osd", "frame-back-step")
      delay(100)
      updateFrameInfo()
      withContext(Dispatchers.Main) {
        showFrameInfoOverlay()
      }
    }
  }

  fun takeSnapshot(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      _isSnapshotLoading.value = true
      try {
        val includeSubtitles = playerPreferences.includeSubtitlesInSnapshot.get()

        // Generate filename with timestamp
        val timestamp =
          java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val filename = "mpv_snapshot_$timestamp.png"

        // Create a temporary file first
        val tempFile = File(context.cacheDir, filename)

        // Take screenshot using MPV to temp file, with or without subtitles
        if (includeSubtitles) {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "subtitles")
        } else {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "video")
        }

        // Wait a bit for MPV to finish writing the file
        delay(200)

        // Check if file was created
        if (!tempFile.exists() || tempFile.length() == 0L) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to create screenshot", Toast.LENGTH_SHORT).show()
          }
          return@launch
        }

        // Use different methods based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          // Android 10+ - Use MediaStore with RELATIVE_PATH
          val contentValues =
            android.content.ContentValues().apply {
              put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
              put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
              put(
                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                "${android.os.Environment.DIRECTORY_PICTURES}/mpvSnaps",
              )
              put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }

          val resolver = context.contentResolver
          val imageUri =
            resolver.insert(
              android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              contentValues,
            )

          if (imageUri != null) {
            // Copy temp file to MediaStore
            resolver.openOutputStream(imageUri)?.use { outputStream ->
              tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
              }
            }

            // Mark as finished
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)

            // Delete temp file
            tempFile.delete()

            // Show success toast
            withContext(Dispatchers.Main) {
              Toast
                .makeText(
                  context,
                  context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                  Toast.LENGTH_SHORT,
                ).show()
            }
          } else {
            throw Exception("Failed to create MediaStore entry")
          }
        } else {
          // Android 9 and below - Use legacy external storage
          val picturesDir =
            android.os.Environment.getExternalStoragePublicDirectory(
              android.os.Environment.DIRECTORY_PICTURES,
            )
          val snapshotsDir = File(picturesDir, "mpvSnaps")

          // Create directory if it doesn't exist
          if (!snapshotsDir.exists()) {
            val created = snapshotsDir.mkdirs()
            if (!created && !snapshotsDir.exists()) {
              throw Exception("Failed to create mpvSnaps directory")
            }
          }

          val destFile = File(snapshotsDir, filename)
          tempFile.copyTo(destFile, overwrite = true)
          tempFile.delete()

          // Notify media scanner about the new file
          android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("image/png"),
            null,
          )

          withContext(Dispatchers.Main) {
            Toast
              .makeText(
                context,
                context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                Toast.LENGTH_SHORT,
              ).show()
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(context, "Failed to save snapshot: ${e.message}", Toast.LENGTH_LONG).show()
        }
      } finally {
        _isSnapshotLoading.value = false
      }
    }
  }

  // ==================== Playlist Management ====================

  fun hasPlaylistSupport(): Boolean {
    val playlistModeEnabled = playerPreferences.playlistMode.get()
    val isM3u = _playlistManager.isM3uPlaylist
    return (playlistModeEnabled || isM3u) && _playlistManager.playlist.value.isNotEmpty()
  }

  fun getPlaylistInfo(): String? {
    if (_playlistManager.playlist.value.isEmpty()) return null

    val totalCount = getPlaylistTotalCount()
    return "${_playlistManager.currentIndex.value + 1}/$totalCount"
  }

  fun isPlaylistM3U(): Boolean {
    return _playlistManager.isM3uPlaylist
  }

  fun getPlaylistTotalCount(): Int {
    val totalCount = _playlistManager.playlistTotalCount
    return if (totalCount > 0) totalCount else _playlistManager.playlist.value.size
  }

  fun getPlaylistData(): List<xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistItem>? {
    val activity = host as? PlayerActivity ?: return null
    if (_playlistManager.playlist.value.isEmpty()) return null

    // Get current video progress
    val currentPos = pos ?: 0
    val currentDuration = duration ?: 0
    val currentProgress = if (currentDuration > 0) {
      ((currentPos.toFloat() / currentDuration.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 0f

    return _playlistManager.playlist.value.mapIndexed { index, uri ->
      val title = _playlistManager.getTitleAt(index) ?: activity.getPlaylistItemTitle(uri)
      // Path is not used for thumbnail loading - thumbnails are loaded directly from URI
      // Keep it for cache key compatibility
      val path = uri.toString()
      val isCurrentlyPlaying = index == _playlistManager.currentIndex.value

      // Try to get from cache first (synchronized access)
      val cacheKey = uri.toString()
      val (durationStr, resolutionStr) = synchronized(metadataCache) { metadataCache[cacheKey] } ?: ("" to "")
      
      val watchedThreshold = browserPreferences.watchedThreshold.get().toFloat()

      val pathOrName = uri.path ?: title
      val isAudio = (isCurrentlyPlaying && activity.isCurrentMediaAudio()) ||
        FileTypeUtils.isAudioFile(File(pathOrName)) ||
        FileTypeUtils.isAudioFile(File(title)) ||
        uri.toString().contains("/audio/") ||
        (runCatching { activity.contentResolver.getType(uri)?.startsWith("audio/") == true }.getOrDefault(false))

      xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistItem(
        uri = uri,
        title = title,
        index = index + _playlistManager.playlistWindowOffset,
        isPlaying = isCurrentlyPlaying,
        path = path,
        progressPercent = if (isCurrentlyPlaying) currentProgress else 0f,
        isWatched = isCurrentlyPlaying && currentProgress >= watchedThreshold,
        duration = durationStr,
        resolution = resolutionStr,
        isAudio = isAudio,
      )
    }
  }

  private fun getVideoMetadata(uri: Uri): Pair<String, String> {
    // Skip metadata extraction for network streams and M3U playlists
    if (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms") {
      return "" to ""
    }

    // Skip M3U/M3U8 files
    val uriString = uri.toString().lowercase()
    if (uriString.contains(".m3u8") || uriString.contains(".m3u")) {
      return "" to ""
    }

    // Try MediaStore first (much faster - uses cached values)
    val mediaStoreMetadata = getVideoMetadataFromMediaStore(uri)
    if (mediaStoreMetadata != null) {
      return mediaStoreMetadata
    }

    // Fallback to MediaMetadataRetriever only if MediaStore fails
    val retriever = android.media.MediaMetadataRetriever()
    return try {
      // For file:// URIs, use the path directly (faster)
      if (uri.scheme == "file") {
        retriever.setDataSource(uri.path)
      } else {
        // For content:// URIs, use context
        retriever.setDataSource(host.context, uri)
      }

      // Get duration
      val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationStr = if (durationMs != null) {
        formatDuration(durationMs.toLong())
      } else ""

      // Get resolution
      val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      val resolutionStr = if (width != null && height != null) {
        "${width}x${height}"
      } else ""

      durationStr to resolutionStr
    } catch (e: Exception) {
      android.util.Log.e("PlayerViewModel", "Failed to get video metadata for $uri", e)
      "" to ""
    } finally {
      try {
        retriever.release()
      } catch (e: Exception) {
        // Ignore release errors
      }
    }
  }

  /**
   * Get video metadata from MediaStore (fast - uses cached system values).
   * Returns null if the video is not found in MediaStore.
   */
  private fun getVideoMetadataFromMediaStore(uri: Uri): Pair<String, String>? {
    return try {
      val projection = arrayOf(
        android.provider.MediaStore.Video.Media.DURATION,
        android.provider.MediaStore.Video.Media.WIDTH,
        android.provider.MediaStore.Video.Media.HEIGHT,
        android.provider.MediaStore.Video.Media.DATA
      )

      // Determine the query URI based on the input URI scheme
      val queryUri = when (uri.scheme) {
        "content" -> {
          // If it's already a content URI, use it directly
          if (uri.toString().startsWith(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
            uri
          } else {
            // Try to find by path if available
            null
          }
        }
        "file" -> {
          // For file:// URIs, query by path
          null
        }
        else -> null
      }

      // Query by URI if we have a content URI
      if (queryUri != null) {
        host.context.contentResolver.query(
          queryUri,
          projection,
          null,
          null,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val durationColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.HEIGHT)

            val durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
            val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
            val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0

            val durationStr = formatDuration(durationMs)

            val resolutionStr = if (width > 0 && height > 0) {
              "${width}x${height}"
            } else ""

            return durationStr to resolutionStr
          }
        }
      }

      // Query by file path if we have a file:// URI or content URI without direct match
      val filePath = when (uri.scheme) {
        "file" -> uri.path
        "content" -> {
          // Try to get the file path from content URI
          host.context.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.Video.Media.DATA),
            null,
            null,
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val dataColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
              if (dataColumn >= 0) cursor.getString(dataColumn) else null
            } else null
          }
        }
        else -> null
      }

      if (filePath != null) {
        val selection = "${android.provider.MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        host.context.contentResolver.query(
          android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          selectionArgs,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val durationColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.HEIGHT)

            val durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
            val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
            val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0

            val durationStr = formatDuration(durationMs)

            val resolutionStr = if (width > 0 && height > 0) {
              "${width}x${height}"
            } else ""

            return durationStr to resolutionStr
          }
        }
      }

      null
    } catch (e: Exception) {
      android.util.Log.w("PlayerViewModel", "Failed to get metadata from MediaStore for $uri, will try MediaMetadataRetriever", e)
      null
    }
  }

  /**
   * Format duration in milliseconds to hh:mm:ss or mm:ss format
   */
  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
      String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format("%d:%02d", minutes, seconds)
    }
  }
  
  

  fun playPlaylistItem(index: Int) {
    val activity = host as? PlayerActivity ?: return
    activity.playPlaylistItem(index)
  }

  fun reorderPlaylistItem(fromIndex: Int, toIndex: Int) {
    _playlistManager.reorder(fromIndex, toIndex)
    refreshPlaylistItems()
  }

  fun removePlaylistItem(index: Int) {
    val wasPlaying = index == _playlistManager.currentIndex.value
    _playlistManager.removeAt(index)
    refreshPlaylistItems()
    
    if (wasPlaying) {
      if (_playlistManager.playlist.value.isNotEmpty()) {
        val nextIndex = _playlistManager.currentIndex.value
        playPlaylistItem(nextIndex)
      } else {
        (host as? PlayerActivity)?.finish()
      }
    }
  }

  fun removePlaylistItems(indexes: List<Int>) {
    val currentIdx = _playlistManager.currentIndex.value
    val wasPlayingRemoved = indexes.contains(currentIdx)
    
    val sortedIndexes = indexes.sortedDescending()
    sortedIndexes.forEach { index ->
      _playlistManager.removeAt(index)
    }
    refreshPlaylistItems()
    
    if (wasPlayingRemoved) {
      if (_playlistManager.playlist.value.isNotEmpty()) {
        val nextIndex = _playlistManager.currentIndex.value.coerceIn(0, _playlistManager.playlist.value.size - 1)
        playPlaylistItem(nextIndex)
      } else {
        (host as? PlayerActivity)?.finish()
      }
    }
  }

  /**
   * Refreshes the playlist items to update the currently playing indicator.
   * Called when a new video starts playing to update the playlist UI.
   */
  fun refreshPlaylistItems() {
    viewModelScope.launch(Dispatchers.IO) {
      val updatedItems = getPlaylistData()
      if (updatedItems != null) {
        // Clear cache if playlist size changed
        if (_playlistItems.value.size != updatedItems.size) {
          metadataCache.evictAll()
        }

        _playlistItems.value = updatedItems

        // Load metadata asynchronously in the background
        loadPlaylistMetadataAsync(updatedItems)
      }
    }
  }

  /**
   * Loads metadata for all playlist items asynchronously in the background.
   * Updates the playlist items as metadata becomes available.
   * Uses batched updates to avoid O(n²) complexity with large playlists.
   * Skips metadata extraction for M3U playlists (network streams).
   */
  private fun loadPlaylistMetadataAsync(items: List<xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      // Skip metadata extraction for M3U playlists
      val activity = host as? PlayerActivity
      if (activity?.isCurrentPlaylistM3U() == true) {
        Log.d(TAG, "Skipping metadata extraction for M3U playlist")
        return@launch
      }

      // Limit concurrent metadata extraction to avoid overwhelming resources
      val batchSize = 5
      items.chunked(batchSize).forEach { batch ->
        val updates = mutableMapOf<String, Pair<String, String>>()

        // Extract metadata for the batch
        batch.forEach { item ->
          val cacheKey = item.uri.toString()

          // Skip if already in cache (LruCache is thread-safe)
          if (metadataCache.get(cacheKey) == null) {
            // Extract metadata
            val (durationStr, resolutionStr) = getVideoMetadata(item.uri)

            // Update cache and track update
            updateMetadataCache(cacheKey, durationStr to resolutionStr)
            updates[cacheKey] = durationStr to resolutionStr
          }
        }

        // Apply all batched updates at once (single playlist update)
        if (updates.isNotEmpty()) {
          _playlistItems.value = _playlistItems.value.map { currentItem ->
            val cacheKey = currentItem.uri.toString()
            val (durationStr, resolutionStr) = updates[cacheKey] ?: return@map currentItem
            currentItem.copy(duration = durationStr, resolution = resolutionStr)
          }
        }
      }
    }
  }

  fun hasNext(): Boolean = _playlistManager.hasNext(shouldRepeatPlaylist())

  fun hasPrevious(): Boolean = _playlistManager.hasPrevious(shouldRepeatPlaylist())

  fun playNext() {
    val nextIndex = _playlistManager.getNextIndex(shouldRepeatPlaylist())
    if (nextIndex != null) {
      (host as? PlayerActivity)?.loadPlaylistItem(nextIndex)
    }
  }

  fun playPrevious() {
    val prevIndex = _playlistManager.getPreviousIndex(shouldRepeatPlaylist())
    if (prevIndex != null) {
      (host as? PlayerActivity)?.loadPlaylistItem(prevIndex)
    }
  }

  // ==================== Repeat and Shuffle ====================

  fun applyPersistedShuffleState() {
    if (_shuffleEnabled.value) {
      _playlistManager.setShuffleEnabled(true)
    }
  }

  fun cycleRepeatMode() {
    val hasPlaylist = _playlistManager.playlist.value.isNotEmpty()

    _repeatMode.value = when (_repeatMode.value) {
      RepeatMode.OFF -> RepeatMode.ONE
      RepeatMode.ONE -> if (hasPlaylist) RepeatMode.ALL else RepeatMode.OFF
      RepeatMode.ALL -> RepeatMode.OFF
    }

    // Persist the repeat mode
    playerPreferences.repeatMode.set(_repeatMode.value)

    // Show overlay update instead of toast
    playerUpdate.value = PlayerUpdates.RepeatMode(_repeatMode.value)
  }

  fun toggleShuffle() {
    _shuffleEnabled.value = !_shuffleEnabled.value

    // Persist the shuffle state
    playerPreferences.shuffleEnabled.set(_shuffleEnabled.value)

    // Notify manager to handle shuffle state change
    _playlistManager.setShuffleEnabled(_shuffleEnabled.value)

    // Show overlay update instead of toast
    playerUpdate.value = PlayerUpdates.Shuffle(_shuffleEnabled.value)
  }

  fun shouldRepeatCurrentFile(): Boolean {
    return _repeatMode.value == RepeatMode.ONE ||
      (_repeatMode.value == RepeatMode.ALL && _playlistManager.playlist.value.isEmpty())
  }

  fun shouldRepeatPlaylist(): Boolean {
    return _repeatMode.value == RepeatMode.ALL && _playlistManager.playlist.value.isNotEmpty()
  }

  // ==================== A-B Loop ====================

  fun toggleABLoopExpanded() {
    _isABLoopExpanded.update { !it }
  }

  fun setLoopA() {
    if (_abLoopA.value != null) {
      // Toggle off - clear point A
      _abLoopA.value = null
      MPVLib.setPropertyString("ab-loop-a", "no")
      return
    }

    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopA.value = currentPos
    MPVLib.setPropertyDouble("ab-loop-a", currentPos)
  }

  fun setLoopB() {
    if (_abLoopB.value != null) {
      // Toggle off - clear point B
      _abLoopB.value = null
      MPVLib.setPropertyString("ab-loop-b", "no")
      return
    }

    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopB.value = currentPos
    MPVLib.setPropertyDouble("ab-loop-b", currentPos)
  }

  fun clearABLoop() {
    val hadLoop = _abLoopA.value != null || _abLoopB.value != null
    _abLoopA.value = null
    _abLoopB.value = null
    MPVLib.setPropertyString("ab-loop-a", "no")
    MPVLib.setPropertyString("ab-loop-b", "no")
  }

  fun cutABLoopClip(context: Context, mode: `is`.xyz.mpv.FastClipper.ClipMode = `is`.xyz.mpv.FastClipper.ClipMode.FAST_COPY) {
    val a = _abLoopA.value
    val b = _abLoopB.value
    if (a == null || b == null) {
      Toast.makeText(context, context.getString(R.string.ab_loop_set_both_points), Toast.LENGTH_SHORT).show()
      return
    }

    val startMs = (minOf(a, b) * 1000).toLong()
    val endMs = (maxOf(a, b) * 1000).toLong()

    if (endMs <= startMs) {
      Toast.makeText(context, context.getString(R.string.ab_loop_set_both_points), Toast.LENGTH_SHORT).show()
      return
    }

    val path = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
    if (path.isNullOrBlank()) {
      Toast.makeText(context, "No active video source found", Toast.LENGTH_SHORT).show()
      return
    }

    Toast.makeText(context, context.getString(R.string.ab_loop_exporting_clip), Toast.LENGTH_SHORT).show()

    viewModelScope.launch {
      val outputFile = xyz.mpv.rex.utils.media.VideoClipper.getOutputClipFile(path, startMs, endMs, mode)
      val result = xyz.mpv.rex.utils.media.VideoClipper.cutClip(context, path, outputFile, startMs, endMs, mode)

      withContext(Dispatchers.Main) {
        if (result.isSuccess) {
          val savedFile = result.getOrNull() ?: outputFile
          Toast.makeText(
            context,
            context.getString(R.string.ab_loop_clip_saved, savedFile.name),
            Toast.LENGTH_LONG
          ).show()
        } else {
          Toast.makeText(
            context,
            context.getString(R.string.ab_loop_clip_error, result.exceptionOrNull()?.localizedMessage ?: "Unknown error"),
            Toast.LENGTH_LONG
          ).show()
        }
      }
    }
  }

  fun formatTimestamp(seconds: Double): String {
    val totalSec = seconds.toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
  }

  // ==================== Mirroring ====================

  fun toggleMirroring() {
    val newMirrorState = !_isMirrored.value
    _isMirrored.value = newMirrorState

    updateVideoFlipFilters()
    playerUpdate.value = PlayerUpdates.ShowText(if (newMirrorState) "H-Flip On" else "H-Flip Off")
  }

  fun toggleVerticalFlip() {
    val newState = !_isVerticalFlipped.value
    _isVerticalFlipped.value = newState

    updateVideoFlipFilters()
    playerUpdate.value = PlayerUpdates.ShowText(if (newState) "V-Flip On" else "V-Flip Off")
  }

  private fun updateVideoFlipFilters() {
    val isMirrored = _isMirrored.value
    val isFlipped = _isVerticalFlipped.value

    if (isMirrored || isFlipped) {
      // Software video filters (vf) require hwdec copy mode when hardware decoding is active
      val currentHwDec = MPVLib.getPropertyString("hwdec-current") ?: ""
      if (currentHwDec == "mediacodec") {
        MPVLib.setPropertyString("hwdec", "mediacodec-copy")
      }

      if (isMirrored) {
        MPVLib.command("vf", "add", "@mpvex_hflip:hflip")
      } else {
        MPVLib.command("vf", "remove", "@mpvex_hflip")
      }

      if (isFlipped) {
        MPVLib.command("vf", "add", "@mpvex_vflip:vflip")
      } else {
        MPVLib.command("vf", "remove", "@mpvex_vflip")
      }
    } else {
      // Remove filters and restore preferred hwdec mode
      MPVLib.command("vf", "remove", "@mpvex_hflip")
      MPVLib.command("vf", "remove", "@mpvex_vflip")

      val preferredHwDec = if (decoderPreferences.tryHWDecoding.get()) "mediacodec" else "no"
      MPVLib.setPropertyString("hwdec", preferredHwDec)
    }
  }

  // ==================== Ambient Mode Integration ====================

  fun toggleAmbientMode() = ambientModeManager.toggleAmbientMode()

  fun onOrientationChanged(isPortrait: Boolean) = ambientModeManager.onOrientationChanged(isPortrait)

  fun resetAmbientMode() = ambientModeManager.resetAmbientMode()

  fun restartAmbientIfActive() = ambientModeManager.restartAmbientIfActive()

  fun updateAmbientStretch() = ambientModeManager.updateAmbientStretch()

  // ==================== Utility ====================

  fun showToast(message: String) {
    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
  }

  override fun onCleared() {
    super.onCleared()
    _screenStateManager.cleanup()
    ambientModeManager.cleanup()
  }
}

// Extension functions
fun Float.normalize(
  inMin: Float,
  inMax: Float,
  outMin: Float,
  outMax: Float,
): Float = (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

fun <T> Flow<T>.collectAsState(
  scope: CoroutineScope,
  initialValue: T? = null,
) = object : ReadOnlyProperty<Any?, T?> {
  private var value: T? = initialValue

  init {
    scope.launch { collect { value = it } }
  }

  override fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = value
}
