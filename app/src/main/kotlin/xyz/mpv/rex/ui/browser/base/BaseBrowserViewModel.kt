package xyz.mpv.rex.ui.browser.base

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.UiPreferences
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import kotlinx.coroutines.FlowPreview

/**
 * Base ViewModel for browser screens with shared functionality
 * handles common UI states and data management.
 * 
 * @param T The type of items displayed in the list
 */
@OptIn(FlowPreview::class)
abstract class BaseBrowserViewModel<T>(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  
  protected val metadataCache: VideoMetadataCacheRepository by inject()
  protected val uiPreferences: UiPreferences by inject()
  protected val playbackStateRepository: PlaybackStateRepository by inject()
  protected val browserPreferences: BrowserPreferences by inject()
  
  // Common UI States
  val uiSettings: StateFlow<UiSettings> = uiPreferences.observeUiSettings()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), uiPreferences.getUiSettings())

  protected val _items = MutableStateFlow<List<T>>(emptyList())
  val items: StateFlow<List<T>> = _items.asStateFlow()

  protected val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  /**
   * Observable recently played file path for highlighting
   */
  val recentlyPlayedFilePath: StateFlow<String?> =
    RecentlyPlayedOps
      .observeLastPlayedPath()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  /**
   * Observable set of active last played paths for highlighting
   */
  val recentlyPlayedFilePaths: StateFlow<Set<String>> =
    RecentlyPlayedOps
      .observeLastPlayedPathsForHighlight()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  /**
   * Observable set of recently played file paths for highlighting
   */
  val recentlyPlayedPaths: StateFlow<Set<String>> =
    RecentlyPlayedOps
      .observeRecentlyPlayedPaths()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  /**
   * Abstract load method to be implemented by subclasses
   */
  abstract fun loadData()

  init {
    // Reactive Synchronization:
    // Observe media library structural changes (file operations), debouncing triggers
    // to prevent redundant reload storms.
    viewModelScope.launch(Dispatchers.Main) {
      MediaLibraryEvents.changes
        .debounce(300L)
        .collectLatest {
          Log.d("BaseBrowserViewModel", "Refreshing browser data from media library event")
          loadData()
        }
    }

    // Observe playback state updates (when a video is watched or marked as watched/unwatched)
    // Clear the core media scanner cache and reload folder/video states
    viewModelScope.launch(Dispatchers.Main) {
      playbackStateRepository.observeAllPlaybackStates()
        .drop(1)
        .debounce(250L)
        .collectLatest {
          Log.d("BaseBrowserViewModel", "Refreshing browser data from playback state update")
          MediaFileRepository.clearCache()
          loadData()
        }
    }

    // Observe .nomedia folder visibility changes
    viewModelScope.launch(Dispatchers.Main) {
      browserPreferences.includeNoMediaContent.changes()
        .drop(1)
        .collectLatest { includeNoMedia ->
          Log.d("BaseBrowserViewModel", "includeNoMediaContent changed to $includeNoMedia")
          MediaFileRepository.clearCache()
          if (includeNoMedia) {
            val hybridIndex = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.database.repository.HybridMediaIndexRepository>()
            val count = withContext(Dispatchers.IO) { hybridIndex.getNoMediaCount() }
            if (count == 0) {
              withContext(Dispatchers.IO) {
                runCatching {
                  hybridIndex.ensureFresh(force = true, userInitiated = false)
                }
              }
            }
          }
          loadData()
        }
    }

    viewModelScope.launch(Dispatchers.Main) {
      browserPreferences.showAudioFiles.changes()
        .drop(1)
        .collectLatest {
          Log.d("BaseBrowserViewModel", "showAudioFiles changed to $it")
          MediaFileRepository.clearCache()
          loadData()
        }
    }
  }

  /**
   * Common hard refresh logic:
   * 1. Clear Cache
   * 2. Set Loading State (unless silent)
   * 3. Trigger Scan
   * 4. Reload after delay
   */
  open fun refresh(silent: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      if (!silent) {
        _isLoading.value = true
      }
      
      // Clear core media scanner cache
      MediaFileRepository.clearCache()
      
      loadData()
    }
  }

  /**
   * Delete videos from storage
   * Automatically removes from recently played history and invalidates cache
   *
   * @return Pair of (deletedCount, failedCount)
   */
  open suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    val result = StorageOps.deleteVideos(getApplication(), videos)

    // Invalidate cache for deleted videos
    val paths = videos.map { it.path }
    metadataCache.invalidateVideos(paths)
    MediaFileRepository.clearCache()

    return result
  }

  /**
   * Rename a video
   * Automatically updates recently played history and invalidates old cache entry
   */
  open suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> {
    val oldPath = video.path
    val result = StorageOps.renameVideo(getApplication(), video, newDisplayName)

    // Invalidate cache for old path
    result.onSuccess {
      metadataCache.invalidateVideo(oldPath)
    }

    return result
  }
}
