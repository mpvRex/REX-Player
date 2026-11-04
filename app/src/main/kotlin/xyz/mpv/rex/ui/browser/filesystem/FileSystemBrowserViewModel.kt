package xyz.mpv.rex.ui.browser.filesystem

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.browser.PathComponent
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.pinned.PinScope
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.media.PlaybackStateIndex
import xyz.mpv.rex.utils.sort.SortUtils
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.utils.storage.MediaTreeStore
import xyz.mpv.rex.utils.storage.folderKey
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
import kotlinx.coroutines.flow.stateIn
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
  private val initialPath: String? = null,
) : BaseBrowserViewModel<FileSystemItem>(application),
  KoinComponent {
  private val foldersPreferences: xyz.mpv.rex.preferences.FoldersPreferences by inject()
  private val appearancePreferences: xyz.mpv.rex.preferences.AppearancePreferences by inject()
  private val pinnedItemRepository: xyz.mpv.rex.domain.pinned.PinnedItemRepository by inject()
  private val treeStore: MediaTreeStore by inject()

  // Special marker for "show storage volumes" mode

  private var isRootResolved = false

  private val _currentPath = MutableStateFlow(initialPath ?: STORAGE_ROOTS_MARKER)
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  private val _unsortedItems = MutableStateFlow<List<FileSystemItem>>(emptyList())

  // Folder pins (scoped to the tree view) merged with media pins (always shared
  // across tree/folder/library views), so a pinned video shows its badge here too.
  val pinnedPathList: StateFlow<List<String>> =
    combine(
      pinnedItemRepository.pinnedPaths(PinScope.TREE),
      pinnedItemRepository.pinnedPaths(PinScope.MEDIA),
    ) { folderPins, mediaPins -> (folderPins + mediaPins).distinct() }
      .stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        (pinnedItemRepository.getPinnedPaths(PinScope.TREE) +
          pinnedItemRepository.getPinnedPaths(PinScope.MEDIA)).distinct(),
      )

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

  val isAtRoot: StateFlow<Boolean> = MutableStateFlow(initialPath == null).asStateFlow()

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
    val defaultTarget = initialPath ?: runCatching { Environment.getExternalStorageDirectory()?.absolutePath }.getOrNull() ?: "/storage/emulated/0"
    _currentPath.value = defaultTarget
    _breadcrumbs.value = if (initialPath == null) emptyList() else MediaFileRepository.getPathComponents(defaultTarget)

    // Cache-first immediate render: load persisted snapshot directly from Room (0 ms spinner)
    viewModelScope.launch(Dispatchers.IO) {
      loadCachedSnapshot(defaultTarget)
    }

    if (initialPath == null) {
      viewModelScope.launch(Dispatchers.IO) {
        val roots = MediaFileRepository.getStorageRoots(getApplication())
        if (roots.size == 1) {
          val singleRoot = roots.first()
          _currentPath.value = singleRoot.path
        } else {
          _currentPath.value = STORAGE_ROOTS_MARKER
        }
        isRootResolved = true
        loadCurrentDirectory(silent = _unsortedItems.value.isNotEmpty())
      }
    } else {
      isRootResolved = true
      loadCurrentDirectory(silent = _unsortedItems.value.isNotEmpty())
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
        val visibleItems = items.filterNot { item ->
          (item is FileSystemItem.VideoFile && !showAudioFiles && item.video.isAudio) ||
          (currentPath != STORAGE_ROOTS_MARKER && item is FileSystemItem.Folder && item.activeCount(showAudioFiles) == 0)
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

    // Refresh when audio visibility changes
    viewModelScope.launch {
      browserPreferences.showAudioFiles.changes().drop(1).collectLatest {
        refresh(silent = true)
      }
    }

    // Refresh when folder flattening changes
    viewModelScope.launch {
      browserPreferences.flattenFolders.changes().drop(1).collectLatest {
        refresh(silent = true)
      }
    }

  }

  override fun loadData() {
    loadCurrentDirectory(silent = false)
  }

  fun loadData(silent: Boolean) {
    loadCurrentDirectory(silent = silent)
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

  /**
   * Pins (or unpins) the given items. Folders go to the tree view's pin set,
   * videos/audio go to the shared media pin set.
   */
  fun setPinned(
    folderPaths: List<String>,
    mediaPaths: List<String>,
    pinned: Boolean,
  ) {
    if (folderPaths.isEmpty() && mediaPaths.isEmpty()) return
    viewModelScope.launch {
      if (pinned) {
        if (folderPaths.isNotEmpty()) pinnedItemRepository.pinAll(PinScope.TREE, folderPaths)
        if (mediaPaths.isNotEmpty()) pinnedItemRepository.pinAll(PinScope.MEDIA, mediaPaths)
      } else {
        if (folderPaths.isNotEmpty()) pinnedItemRepository.unpinAll(PinScope.TREE, folderPaths)
        if (mediaPaths.isNotEmpty()) pinnedItemRepository.unpinAll(PinScope.MEDIA, mediaPaths)
      }
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
      deletedFolderPaths.forEach { deletedPath ->
        pinnedItemRepository.removePinsUnder(deletedPath)
      }
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
      pinnedItemRepository.unpinAll(PinScope.TREE, deletedPaths)
      pinnedItemRepository.unpinAll(PinScope.MEDIA, deletedPaths)
    }
    return result
  }

  override suspend fun renameVideo(video: Video, newDisplayName: String): Result<Unit> {
    val oldPath = video.path.ifBlank { video.uri.toString() }
    val result = super.renameVideo(video, newDisplayName)
    if (result.isSuccess) {
      // BaseBrowserViewModel performs the rename on disk; re-read the parent to
      // find the file's new path so an existing pin follows it.
      val parentDir = File(oldPath).parent
      if (parentDir != null) {
        val newPath = File(parentDir, newDisplayName).absolutePath
        pinnedItemRepository.renamePin(oldPath, newPath)
      }
    }
    return result
  }

  suspend fun renameFolder(folder: FileSystemItem.Folder, newName: String): Boolean {
    val src = File(folder.path)
    val dst = File(src.parent ?: return false, newName)
    if (dst.exists()) return false
    val ok = src.renameTo(dst)
    if (ok) {
      pinnedItemRepository.renamePin(folder.path, dst.absolutePath)
      android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
      setItemsWereDeletedOrMoved()
    }
    return ok
  }

  private suspend fun loadCachedSnapshot(path: String) {
    try {
      if (path == STORAGE_ROOTS_MARKER) return
      val snapshot = treeStore.snapshot(
        context = getApplication(),
        flattenFolders = browserPreferences.flattenFolders.get(),
        showAudioFiles = browserPreferences.showAudioFiles.get(),
      )
      val cachedFolders = snapshot
        .effectiveChildren(folderKey(path))
        .filter { node ->
          if (browserPreferences.showAudioFiles.get()) {
            node.recursiveVideoCount > 0 || node.recursiveAudioCount > 0
          } else {
            node.recursiveVideoCount > 0
          }
        }
        .map { node ->
          val folderData = snapshot.toMediaFolder(node, recursive = true)
          FileSystemItem.Folder(
            name = folderData.name,
            path = folderData.path,
            lastModified = folderData.lastModified,
            videoCount = folderData.videoCount,
            audioCount = folderData.audioCount,
            totalSize = folderData.totalSize,
            totalDuration = folderData.totalDuration,
            videoSize = folderData.videoSize,
            audioSize = folderData.audioSize,
            videoDuration = folderData.videoDuration,
            audioDuration = folderData.audioDuration,
            hasSubfolders = folderData.hasSubfolders,
            newCount = folderData.newCount,
            unwatchedVideoCount = folderData.unwatchedVideoCount,
          )
        }
      if (cachedFolders.isNotEmpty() && _unsortedItems.value.isEmpty()) {
        _unsortedItems.value = cachedFolders
        _isLoading.value = false
      }
    } catch (e: Exception) {
      Log.d(TAG, "Cache-first snapshot load skipped", e)
    }
  }

  private fun loadCurrentDirectory(silent: Boolean = false) {
    if (!isRootResolved) return
    directoryLoadJob?.cancel()
    directoryLoadJob = viewModelScope.launch(Dispatchers.IO) {
      if (!silent && _unsortedItems.value.isEmpty()) {
        _isLoading.value = true
      }
      _error.value = null

      try {
        val path = _currentPath.value
        _breadcrumbs.value = if (path == STORAGE_ROOTS_MARKER || initialPath == null) {
          emptyList()
        } else {
          MediaFileRepository.getPathComponents(path)
        }

        if (path == STORAGE_ROOTS_MARKER) {
          val roots = MediaFileRepository.getStorageRoots(getApplication())
          _unsortedItems.value = roots
          _isLoading.value = false
        } else {
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

              val showAudio = browserPreferences.showAudioFiles.get()
              val filteredItems = items.filterNot { item ->
                (item is FileSystemItem.VideoFile && !showAudio && item.video.isAudio) ||
                (path != STORAGE_ROOTS_MARKER && item is FileSystemItem.Folder && item.activeCount(showAudio) == 0)
              }

              // Everything below is decided once, with one clock reading, so the list that is
              // published is the only list the UI ever sees for this load.
              val now = System.currentTimeMillis()
              val thresholdMillis = appearancePreferences.unplayedOldVideoDays.get() * 24L * 60L * 60L * 1000L
              val watchedThreshold = browserPreferences.watchedThreshold.get()
              val playbackIndex = PlaybackStateIndex.of(playbackStateRepository.getAllPlaybackStates())

              val videoFiles = filteredItems.filterIsInstance<FileSystemItem.VideoFile>()

              // Cached metadata (duration, resolution, subtitles) never affects watched/progress/new,
              // so it is applied to the file metadata but not replayed through the badge pass.
              val preEnrichedItems = if (videoFiles.isNotEmpty()) {
                val cachedVideos = MetadataRetrieval.applyCachedMetadata(
                  videos = videoFiles.map { it.video },
                  browserPreferences = browserPreferences,
                  metadataCache = metadataCache,
                )
                val cachedVideoMap = cachedVideos.associateBy { it.id }
                filteredItems.map { item ->
                  if (item is FileSystemItem.VideoFile) {
                    val cached = cachedVideoMap[item.video.id]
                    if (cached != null) item.copy(video = cached) else item
                  } else {
                    item
                  }
                }
              } else {
                filteredItems
              }

              // One badge computation, reused for both the cached and the enriched list.
              val badges = FolderBadges()
              val decorated = badges.decorate(preEnrichedItems, playbackIndex, now, thresholdMillis, watchedThreshold)

              // Instant render: list with badges and cached metadata is published immediately (<50ms)
              _videoFilesWithPlayback.value = badges.progress
              _newVideoIds.value = badges.newIds
              _watchedVideoIds.value = badges.watchedIds
              _unsortedItems.value = decorated
              _isLoading.value = false

              // Decide whether enrichment is needed from the metadata we already have.
              val videosNeedingEnrichment = videoFiles.filter { file ->
                MetadataRetrieval.needsMetadata(file.video, browserPreferences)
              }

              // Background progressive enrichment: process uncached videos in responsive batches of 32
              if (videosNeedingEnrichment.isNotEmpty()) {
                videosNeedingEnrichment.chunked(32).forEach { chunk ->
                  if (!coroutineContext.isActive) return@launch
                  val enrichedChunk = MetadataRetrieval.enrichVideosIfNeeded(
                    context = getApplication(),
                    videos = chunk.map { it.video },
                    browserPreferences = browserPreferences,
                    metadataCache = metadataCache,
                  )
                  if (!coroutineContext.isActive) return@launch
                  val enrichedMap = enrichedChunk.associateBy { it.id }
                  _unsortedItems.value = _unsortedItems.value.map { item ->
                    if (item is FileSystemItem.VideoFile) {
                      val enriched = enrichedMap[item.video.id]
                      if (enriched != null) item.copy(video = enriched) else item
                    } else {
                      item
                    }
                  }

                  // If any video in this chunk had duration <= 0 originally and now has duration,
                  // update progress / watched state for that video.
                  for (enriched in enrichedChunk) {
                    val originalVideo = chunk.firstOrNull { it.video.id == enriched.id }?.video
                    if (originalVideo != null && originalVideo.duration <= 0L && enriched.duration > 0L) {
                      val state = playbackIndex.forVideoPathOrName(enriched.path, enriched.displayName)
                      if (state != null && state.timeRemaining != -1) {
                        val durationSec = enriched.duration / 1000
                        val watched = durationSec - state.timeRemaining.toLong()
                        val progress = (watched.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
                        if (progress >= (watchedThreshold / 100f)) {
                          _watchedVideoIds.value = _watchedVideoIds.value + enriched.id
                        } else if (progress >= 0.01f) {
                          _videoFilesWithPlayback.value = _videoFilesWithPlayback.value + (enriched.id to progress)
                        }
                      }
                    }
                  }
                }
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
