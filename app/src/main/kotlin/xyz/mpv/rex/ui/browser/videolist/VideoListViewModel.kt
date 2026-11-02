package xyz.mpv.rex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.media.PlaybackStateIndex
import xyz.mpv.rex.utils.storage.FileTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.compose.runtime.Immutable

@Immutable
data class VideoWithPlaybackInfo(
  val video: Video,
  val timeRemaining: Long? = null, // in seconds
  val progressPercentage: Float? = null, // 0.0 to 1.0
  val isOldAndUnplayed: Boolean = false, // true if video is older than threshold and never played
  val isWatched: Boolean = false, // true if video has any playback history
  val isNeverPlayed: Boolean = true, // true if video has never been opened
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel<VideoWithPlaybackInfo>(application),
  KoinComponent {
  private val appearancePreferences: xyz.mpv.rex.preferences.AppearancePreferences by inject()
  private val recentlyPlayedRepository: xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  // Track if items were deleted/moved leaving folder empty
  private val _videosWereDeletedOrMoved = MutableStateFlow(false)
  val videosWereDeletedOrMoved: StateFlow<Boolean> = _videosWereDeletedOrMoved.asStateFlow()

  val lastPlayedInFolderPath: StateFlow<String?> =
    recentlyPlayedRepository
      .observeRecentlyPlayed(limit = 100)
      .map { recentlyPlayedList ->
        val folderPath = _videos.value.firstOrNull()?.path?.let { File(it).parent }
        if (folderPath != null) {
          recentlyPlayedList.firstOrNull { entity ->
            try {
              File(entity.filePath).parent == folderPath
            } catch (_: Exception) {
              false
            }
          }?.filePath
        } else {
          null
        }
      }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Track previous video count to detect if folder became empty
  private var previousVideoCount = 0

  private val tag = "VideoListViewModel"

  init {
    loadData()
    viewModelScope.launch {
      playbackStateRepository.observeAllPlaybackStates()
        .drop(1)
        .debounce(250L)
        .collect {
          if (_videos.value.isNotEmpty()) {
            loadPlaybackInfo(_videos.value)
          }
        }
    }
  }

  override fun loadData() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        // First attempt to load videos (basic info from MediaStore)
        var videoList = MediaFileRepository.getVideosInFolder(getApplication(), bucketId)
        
        // Filter out audio if disabled
        if (!browserPreferences.showAudioFiles.get()) {
          videoList = videoList.filterNot { it.isAudio }
        }

        // Check if folder became empty
        if (previousVideoCount > 0 && videoList.isEmpty()) {
          _videosWereDeletedOrMoved.value = true
        } else if (videoList.isNotEmpty()) {
          _videosWereDeletedOrMoved.value = false
        }
        previousVideoCount = videoList.size

        if (videoList.isEmpty()) {
          _videos.value = emptyList()
          _videosWithPlaybackInfo.value = emptyList()
          _isLoading.value = false
        } else {
          // Pre-apply DB cached metadata before initial emission to ensure durations and chips appear immediately
          videoList = MetadataRetrieval.applyCachedMetadata(
            videos = videoList,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
          )
          _videos.value = videoList
          loadPlaybackInfo(videoList)
          _isLoading.value = false

          // Extract metadata in background for any uncached videos or videos with missing duration
          val isMetadataNeeded = MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)
          val uncachedCount = videoList.count { it.duration <= 0L || (isMetadataNeeded && (it.fps == 0f || it.subtitleCodec.isEmpty())) }
          if (uncachedCount > 0) {
            val enrichedList = MetadataRetrieval.enrichVideosIfNeeded(
              context = getApplication(),
              videos = videoList,
              browserPreferences = browserPreferences,
              metadataCache = metadataCache
            )
            _videos.value = enrichedList
            loadPlaybackInfo(enrichedList)
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        _videos.value = emptyList()
        _videosWithPlaybackInfo.value = emptyList()
      } finally {
        if (coroutineContext.isActive) {
          _isLoading.value = false
        }
      }
    }
  }

  override fun refresh(silent: Boolean) {
    loadData()
  }

  override suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    val deletedPaths = videos.map { it.path.ifBlank { it.uri.toString() } }.toSet()
    _videos.value = _videos.value.filterNot { (it.path.ifBlank { it.uri.toString() }) in deletedPaths }
    _videosWithPlaybackInfo.value = _videosWithPlaybackInfo.value.filterNot {
      (it.video.path.ifBlank { it.video.uri.toString() }) in deletedPaths
    }
    if (_videos.value.isEmpty()) {
      _videosWereDeletedOrMoved.value = true
    }
    return super.deleteVideos(videos)
  }

  /**
   * Set flag indicating videos were deleted or moved
   */
  fun setVideosWereDeletedOrMoved() {
    _videosWereDeletedOrMoved.value = true
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val playbackStates = playbackStateRepository.getAllPlaybackStates()
    val playbackIndex = PlaybackStateIndex.of(playbackStates)
    val currentTime = System.currentTimeMillis()
    val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
    val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
    val watchedThreshold = browserPreferences.watchedThreshold.get()

    val videosWithInfo =
      videos.map { video ->
        val playbackState = playbackIndex.forName(video.displayName)

        // Map saved orientation to video
        val videoWithOrientation = if (playbackState?.savedOrientation != null) {
          video.copy(savedOrientation = playbackState.savedOrientation)
        } else {
          video
        }

        // Calculate watch progress (0.0 to 1.0)
        val durationSeconds = if (video.duration > 0) video.duration / 1000 else 0L
        val progressValue = if (playbackState != null && durationSeconds > 0 && playbackState.timeRemaining != -1) {
          val watched = durationSeconds - playbackState.timeRemaining.toLong()
          (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
          null
        }

        val isWatched = if (playbackState != null) {
          if (playbackState.hasBeenWatched) {
            true
          } else if (progressValue != null) {
            progressValue >= (watchedThreshold / 100f)
          } else {
            false
          }
        } else {
          false
        }

        val progress = if (progressValue != null && progressValue >= 0.01f && !isWatched) {
          progressValue
        } else {
          null
        }

        // Correct logic for "NEW" label
        val videoAge = currentTime - (video.dateModified * 1000)
        val isOldAndUnplayed = (playbackState == null && videoAge <= thresholdMillis) || (playbackState != null && playbackState.timeRemaining == -1)

        VideoWithPlaybackInfo(
          video = videoWithOrientation,
          timeRemaining = playbackState?.timeRemaining?.toLong(),
          progressPercentage = progress,
          isOldAndUnplayed = isOldAndUnplayed,
          isWatched = isWatched,
          isNeverPlayed = playbackState == null,
        )
      }
    _videosWithPlaybackInfo.value = videosWithInfo
  }

  companion object {
    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }
}
