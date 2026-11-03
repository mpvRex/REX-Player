package xyz.mpv.rex.ui.browser.medialibrary

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.media.PlaybackStateIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.compose.runtime.Immutable

class MediaLibraryViewModel(
  application: Application,
) : BaseBrowserViewModel<VideoWithPlaybackInfo>(application),
  KoinComponent {
  private val appearancePreferences: xyz.mpv.rex.preferences.AppearancePreferences by inject()
  private val recentlyPlayedRepository: xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val tag = "MediaLibraryViewModel"

  init {
    loadData()
  }

  override fun loadData() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        // Fetch all videos from repository (respecting blacklists and filters via MediaMetadataOps)
        var videoList = MediaFileRepository.getAllVideos(getApplication())
        
        // Filter out audio if disabled
        if (!browserPreferences.showAudioFiles.get()) {
          videoList = videoList.filterNot { it.isAudio }
        }

        videoList = videoList.distinctBy { it.path.ifBlank { it.uri.toString() } }

        if (MetadataRetrieval.isVideoMetadataNeeded(browserPreferences) && videoList.isNotEmpty()) {
          videoList = MetadataRetrieval.applyCachedMetadata(
            videos = videoList,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
          )
        }

        _videos.value = videoList
        loadPlaybackInfo(videoList)
        _isLoading.value = false

        // Extract metadata in the background only for uncached videos
        if (MetadataRetrieval.isVideoMetadataNeeded(browserPreferences) && videoList.isNotEmpty()) {
          val uncachedCount = videoList.count { it.fps == 0f || it.subtitleCodec.isEmpty() }
          if (uncachedCount > 0) {
            val enrichedList = MetadataRetrieval.enrichVideosIfNeeded(
              context = getApplication(),
              videos = videoList,
              browserPreferences = browserPreferences,
              metadataCache = metadataCache
            ).distinctBy { it.path.ifBlank { it.uri.toString() } }
            _videos.value = enrichedList
            loadPlaybackInfo(enrichedList)
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(tag, "Error loading media library videos", e)
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
    return super.deleteVideos(videos)
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
        val playbackState = playbackIndex.forVideoPathOrName(video.path, video.displayName)

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
    _items.value = videosWithInfo
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return MediaLibraryViewModel(application) as T
        }
      }
  }
}
