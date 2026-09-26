package xyz.mpv.rex.ui.browser.filesystem

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.browser.PathComponent
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.sort.SortUtils
import xyz.mpv.rex.utils.storage.FileTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * ViewModel for FileSystem Browser - based on Fossify's ItemsFragment logic
 * Handles directory navigation, file loading, sorting, and state management
 */
class FileSystemBrowserViewModel(
  application: Application,
  initialPath: String? = null,
) : BaseBrowserViewModel<FileSystemItem>(application),
  KoinComponent {
  private val foldersPreferences: xyz.mpv.rex.preferences.FoldersPreferences by inject()
  private val appearancePreferences: xyz.mpv.rex.preferences.AppearancePreferences by inject()

  // Special marker for "show storage volumes" mode

  private var homeDirectory: String? = null
  private var isRootResolved = false

  private val _currentPath = MutableStateFlow(initialPath ?: STORAGE_ROOTS_MARKER)
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  private val _unsortedItems = MutableStateFlow<List<FileSystemItem>>(emptyList())

  // Video playback progress map
  private val _videoFilesWithPlayback = MutableStateFlow<Map<Long, Float>>(emptyMap())
  val videoFilesWithPlayback: StateFlow<Map<Long, Float>> = _videoFilesWithPlayback.asStateFlow()

  // Set of video IDs that should show the "NEW" label
  private val _newVideoIds = MutableStateFlow<Set<Long>>(emptySet())
  val newVideoIds: StateFlow<Set<Long>> = _newVideoIds.asStateFlow()

  // Set of video IDs that have reached the watched threshold
  private val _watchedVideoIds = MutableStateFlow<Set<Long>>(emptySet())
  val watchedVideoIds: StateFlow<Set<Long>> = _watchedVideoIds.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _breadcrumbs = MutableStateFlow<List<PathComponent>>(emptyList())
  val breadcrumbs: StateFlow<List<PathComponent>> = _breadcrumbs.asStateFlow()

  val isAtRoot: StateFlow<Boolean> =
    MutableStateFlow(initialPath == null).apply {
      viewModelScope.launch {
        _currentPath.collect { path ->
          value = path == STORAGE_ROOTS_MARKER || path == homeDirectory
        }
      }
    }

  private val _itemsWereDeletedOrMoved = MutableStateFlow(false)
  val itemsWereDeletedOrMoved: StateFlow<Boolean> = _itemsWereDeletedOrMoved.asStateFlow()

  private val itemCountByPath = mutableMapOf<String, Int>()
  private var directoryLoadJob: Job? = null

  companion object {
    private const val TAG = "FileSystemBrowserVM"
    const val STORAGE_ROOTS_MARKER = "__STORAGE_ROOTS__"

    fun factory(
      application: Application,
      initialPath: String? = null,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FileSystemBrowserViewModel(application, initialPath) as T
    }
  }

  init {
    if (initialPath == null) {
      viewModelScope.launch(Dispatchers.IO) {
        val roots = MediaFileRepository.getStorageRoots(getApplication())
        if (roots.size == 1) {
          val singleRoot = roots.first()
          homeDirectory = singleRoot.path
          _currentPath.value = singleRoot.path
        } else {
          homeDirectory = null
        }
        isRootResolved = true
        loadData()
      }
    } else {
      homeDirectory = initialPath
      isRootResolved = true
      loadData()
    }

    // Note: BaseBrowserViewModel handles reactive updates for media library and playback changes.

    viewModelScope.launch {
      combine(
        _unsortedItems,
        _currentPath,
        browserPreferences.folderSortType.changes(),
        browserPreferences.folderSortOrder.changes(),
        browserPreferences.showAudioFiles.changes(),
      ) { items, currentPath, sortType, sortOrder, showAudioFiles ->
        val visibleItems = if (!showAudioFiles) {
          items.filterNot {
            (it is FileSystemItem.VideoFile && it.video.isAudio) ||
            (currentPath != STORAGE_ROOTS_MARKER && it is FileSystemItem.Folder && it.videoCount == 0)
          }
        } else {
          items
        }
        SortUtils.sortFileSystemItems(visibleItems, sortType, sortOrder, showAudioFiles)
      }.collectLatest { sortedItems ->
        _items.value = sortedItems
      }
    }

    // Refresh when blacklist changes
    viewModelScope.launch {
      foldersPreferences.blacklistedFolders.changes().drop(1).collectLatest {
        refresh(silent = true)
      }
    }

  }

  override fun loadData() {
    loadCurrentDirectory()
  }

  override fun refresh(silent: Boolean) {
    Log.d(TAG, "Hard refreshing current directory: ${_currentPath.value}")
    
    viewModelScope.launch(Dispatchers.IO) {
      if (!silent) {
        _isLoading.value = true
      }
      MediaFileRepository.clearCache()
      delay(if (silent) 100 else 300)
      loadData()
    }
  }

  fun setItemsWereDeletedOrMoved() {
    _itemsWereDeletedOrMoved.value = true
  }

  fun blacklistFolders(folders: List<FileSystemItem.Folder>) {
    viewModelScope.launch {
      val currentBlacklist = foldersPreferences.blacklistedFolders.get().toMutableSet()
      folders.forEach { currentBlacklist.add(it.path) }
      foldersPreferences.blacklistedFolders.set(currentBlacklist)
      // BaseBrowserViewModel or init block should handle the refresh via flow observation
    }
  }

  suspend fun deleteFolders(folders: List<FileSystemItem.Folder>): Pair<Int, Int> = withContext(Dispatchers.IO) {
    var successCount = 0
    var failureCount = 0
    val showAudio = browserPreferences.showAudioFiles.get()

    folders.forEach { folder ->
      try {
        val dir = File(folder.path)
        if (!dir.exists() || !dir.isDirectory) {
          failureCount++
          return@forEach
        }

        // Targeted deletion: Only delete media files in the immediate folder
        val children = dir.listFiles() ?: emptyArray()
        var filesDeleted = 0
        
        children.forEach { file ->
          if (file.isDirectory) return@forEach // Never recurse into subfolders
          
          val isVideo = FileTypeUtils.isVideoFile(file)
          val isSubtitle = FileTypeUtils.isSubtitleFile(file)
          val isAudio = showAudio && FileTypeUtils.isAudioFile(file)
          
          if (isVideo || isSubtitle || isAudio) {
            if (file.delete()) {
              filesDeleted++
            }
          }
        }

        if (filesDeleted > 0) {
          // Successfully cleaned some media
          successCount++
        } else {
          // Nothing was deleted
          failureCount++
        }
      } catch (e: Exception) {
        failureCount++
      }
    }

    if (successCount > 0) {
      val deletedFolderPaths = folders.map { it.path }.toSet()
      _items.value = _items.value.filterNot { it is FileSystemItem.Folder && it.path in deletedFolderPaths }
      _unsortedItems.value = _unsortedItems.value.filterNot { it is FileSystemItem.Folder && it.path in deletedFolderPaths }
      _itemsWereDeletedOrMoved.value = true
      MediaLibraryEvents.notifyChanged()
    }
    Pair(successCount, failureCount)
  }

  override suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    val deletedPaths = videos.map { it.path.ifBlank { it.uri.toString() } }.toSet()
    _items.value = _items.value.filterNot { it is FileSystemItem.VideoFile && (it.video.path.ifBlank { it.video.uri.toString() }) in deletedPaths }
    _unsortedItems.value = _unsortedItems.value.filterNot { it is FileSystemItem.VideoFile && (it.video.path.ifBlank { it.video.uri.toString() }) in deletedPaths }
    val result = super.deleteVideos(videos)
    if (result.first > 0) {
      _itemsWereDeletedOrMoved.value = true
    }
    return result
  }

  override suspend fun renameVideo(video: Video, newDisplayName: String): Result<Unit> {
    return super.renameVideo(video, newDisplayName)
  }

  suspend fun renameFolder(folder: FileSystemItem.Folder, newName: String): Boolean {
    val src = File(folder.path)
    val dst = File(src.parent ?: return false, newName)
    if (dst.exists()) return false
    val ok = src.renameTo(dst)
    if (ok) {
      android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
      setItemsWereDeletedOrMoved()
    }
    return ok
  }

  private fun loadCurrentDirectory() {
    if (!isRootResolved) return
    directoryLoadJob?.cancel()
    directoryLoadJob = viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      _error.value = null

      try {
        val path = _currentPath.value
        if (path == STORAGE_ROOTS_MARKER) {
          _breadcrumbs.value = emptyList()
          val roots = MediaFileRepository.getStorageRoots(getApplication())
          _unsortedItems.value = roots
          _isLoading.value = false
        } else {
          _breadcrumbs.value = MediaFileRepository.getPathComponents(path)
          MediaFileRepository
            .scanDirectory(getApplication(), path, showAllFileTypes = false)
            .onSuccess { items ->
              val previousCount = itemCountByPath[path] ?: 0
              if (previousCount > 0 && items.isEmpty()) {
                _itemsWereDeletedOrMoved.value = true
              } else if (items.isNotEmpty()) {
                _itemsWereDeletedOrMoved.value = false
              }
              itemCountByPath[path] = items.size

              val filteredItems = if (!browserPreferences.showAudioFiles.get()) {
                items.filterNot { 
                  (it is FileSystemItem.VideoFile && it.video.isAudio) ||
                  (it is FileSystemItem.Folder && it.videoCount == 0)
                }
              } else {
                items
              }

              // 1. Map playback info on basic items immediately (fast, local DB query)
              val basicPlaybackStates = playbackStateRepository.getAllPlaybackStates()
              val basicPlaybackMap = mutableMapOf<Long, Float>()
              val basicNewIds = mutableSetOf<Long>()
              val basicWatchedIds = mutableSetOf<Long>()
              val basicCurrentTime = System.currentTimeMillis()
              val basicThresholdDays = appearancePreferences.unplayedOldVideoDays.get()
              val basicThresholdMillis = basicThresholdDays * 24 * 60 * 60 * 1000L
              val basicWatchedThreshold = browserPreferences.watchedThreshold.get()

              val preEnrichedItems = run {
                val videoFiles = filteredItems.filterIsInstance<FileSystemItem.VideoFile>()
                if (videoFiles.isNotEmpty()) {
                  val cachedVideos = MetadataRetrieval.applyCachedMetadata(
                    videos = videoFiles.map { it.video },
                    browserPreferences = browserPreferences,
                    metadataCache = metadataCache
                  )
                  val cachedVideoMap = cachedVideos.associateBy { it.id }
                  filteredItems.map { item ->
                    if (item is FileSystemItem.VideoFile) {
                      val cached = cachedVideoMap[item.video.id]
                      if (cached != null) item.copy(video = cached) else item
                    } else item
                  }
                } else filteredItems
              }

              val basicEnrichedItems = preEnrichedItems.map { item ->
                if (item is FileSystemItem.VideoFile) {
                  val video = item.video
                  val state = basicPlaybackStates.find { it.mediaTitle == video.path || it.mediaTitle == video.displayName }
                  
                  var updatedVideo = video
                  if (state != null) {
                    if (state.savedOrientation != null) {
                      updatedVideo = updatedVideo.copy(savedOrientation = state.savedOrientation)
                    }
                    var isWatched = state.hasBeenWatched
                    if (video.duration > 0 && state.timeRemaining != -1) {
                      val durationSeconds = video.duration / 1000
                      val watched = durationSeconds - state.timeRemaining.toLong()
                      val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
                      
                      if (progressValue >= (basicWatchedThreshold / 100f)) {
                        isWatched = true
                      }
                      
                      if (progressValue >= 0.01f && !isWatched) {
                        basicPlaybackMap[video.id] = progressValue
                      }
                    }
                    if (isWatched) {
                      basicWatchedIds.add(video.id)
                    }
                    if (state.timeRemaining == -1) {
                      basicNewIds.add(video.id)
                    }
                  } else {
                    val videoAge = basicCurrentTime - (video.dateModified * 1000)
                    if (videoAge <= basicThresholdMillis) {
                      basicNewIds.add(video.id)
                    }
                  }
                  item.copy(video = updatedVideo)
                } else {
                  item
                }
              }

              // Instantly publish the pre-enriched items so the UI displays immediately with cached metadata
              _videoFilesWithPlayback.value = basicPlaybackMap
              _newVideoIds.value = basicNewIds
              _watchedVideoIds.value = basicWatchedIds
              _unsortedItems.value = basicEnrichedItems
              _isLoading.value = false

              // 2. Fetch detailed video metadata (MediaInfo) asynchronously in the background for uncached videos or missing durations
              val videoFiles = preEnrichedItems.filterIsInstance<FileSystemItem.VideoFile>()
              val isMetadataNeeded = MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)
              val uncachedVideos = videoFiles.map { it.video }.filter { it.duration <= 0L || (isMetadataNeeded && (it.fps == 0f || it.subtitleCodec.isEmpty())) }
              if (uncachedVideos.isNotEmpty()) {
                val videos = videoFiles.map { it.video }
                val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
                  context = getApplication(),
                  videos = videos,
                  browserPreferences = browserPreferences,
                  metadataCache = metadataCache
                )
                  
                  val enrichedVideoMap = enrichedVideos.associateBy { it.id }
                  
                  // Re-apply playback states to the enriched videos
                  val finalPlaybackStates = playbackStateRepository.getAllPlaybackStates()
                  val finalPlaybackMap = mutableMapOf<Long, Float>()
                  val finalNewIds = mutableSetOf<Long>()
                  val finalWatchedIds = mutableSetOf<Long>()
                  val finalCurrentTime = System.currentTimeMillis()
                  val finalThresholdDays = appearancePreferences.unplayedOldVideoDays.get()
                  val finalThresholdMillis = finalThresholdDays * 24 * 60 * 60 * 1000L
                  val finalWatchedThreshold = browserPreferences.watchedThreshold.get()

                  val finalEnrichedItems = filteredItems.map { item ->
                    when (item) {
                      is FileSystemItem.VideoFile -> {
                        var video = enrichedVideoMap[item.video.id] ?: item.video
                        val state = finalPlaybackStates.find { it.mediaTitle == video.path || it.mediaTitle == video.displayName }
                        
                        if (state != null) {
                          if (state.savedOrientation != null) {
                            video = video.copy(savedOrientation = state.savedOrientation)
                          }
                          var isWatched = state.hasBeenWatched
                          if (video.duration > 0 && state.timeRemaining != -1) {
                            val durationSeconds = video.duration / 1000
                            val watched = durationSeconds - state.timeRemaining.toLong()
                            val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
                            
                            if (progressValue >= (finalWatchedThreshold / 100f)) {
                              isWatched = true
                            }
                            
                            if (progressValue >= 0.01f && !isWatched) {
                              finalPlaybackMap[video.id] = progressValue
                            }
                          }
                          if (isWatched) {
                            finalWatchedIds.add(video.id)
                          }
                          if (state.timeRemaining == -1) {
                            finalNewIds.add(video.id)
                          }
                        } else {
                          val videoAge = finalCurrentTime - (video.dateModified * 1000)
                          if (videoAge <= finalThresholdMillis) {
                            finalNewIds.add(video.id)
                          }
                        }
                        item.copy(video = video)
                      }
                      else -> item
                    }
                  }

                  // Publish the final fully-enriched list
                  _videoFilesWithPlayback.value = finalPlaybackMap
                  _newVideoIds.value = finalNewIds
                  _watchedVideoIds.value = finalWatchedIds
                  _unsortedItems.value = finalEnrichedItems
                }
            }.onFailure { error ->
              _error.value = error.message
              _unsortedItems.value = emptyList()
              _items.value = emptyList()
            }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _error.value = e.message
        _unsortedItems.value = emptyList()
        _items.value = emptyList()
      } finally {
        if (coroutineContext.isActive) {
          _isLoading.value = false
        }
      }
    }
  }
}
