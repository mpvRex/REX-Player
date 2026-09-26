package xyz.mpv.rex.ui.browser.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.UiPreferences
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

enum class SearchScope {
  ALL_STORAGE,
  CURRENT_FOLDER,
}

@OptIn(FlowPreview::class)
class SearchViewModel(
  application: Application,
  private val initialPath: String? = null,
  val initialFolderName: String? = null,
) : AndroidViewModel(application), KoinComponent {

  private val TAG = "SearchViewModel"

  private val metadataCache: VideoMetadataCacheRepository by inject()
  private val uiPreferences: UiPreferences by inject()
  private val browserPreferences: BrowserPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val foldersPreferences: FoldersPreferences by inject()
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val hybridMediaIndex: HybridMediaIndexRepository by inject()

  val uiSettings: StateFlow<UiSettings> = uiPreferences.observeUiSettings()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), uiPreferences.getUiSettings())

  val searchQuery = MutableStateFlow("")
  val searchScope = MutableStateFlow(if (initialPath != null) SearchScope.CURRENT_FOLDER else SearchScope.ALL_STORAGE)

  private val _searchResults = MutableStateFlow<List<FileSystemItem>>(emptyList())
  val searchResults: StateFlow<List<FileSystemItem>> = _searchResults.asStateFlow()

  private val _isSearchLoading = MutableStateFlow(false)
  val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

  private val _videoFilesWithPlayback = MutableStateFlow<Map<Long, Float>>(emptyMap())
  val videoFilesWithPlayback: StateFlow<Map<Long, Float>> = _videoFilesWithPlayback.asStateFlow()

  private val _newVideoIds = MutableStateFlow<Set<Long>>(emptySet())
  val newVideoIds: StateFlow<Set<Long>> = _newVideoIds.asStateFlow()

  private val _watchedVideoIds = MutableStateFlow<Set<Long>>(emptySet())
  val watchedVideoIds: StateFlow<Set<Long>> = _watchedVideoIds.asStateFlow()

  val recentlyPlayedFilePath: StateFlow<String?> =
    RecentlyPlayedOps.observeLastPlayedPath()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val recentlyPlayedFilePaths: StateFlow<Set<String>> =
    RecentlyPlayedOps.observeLastPlayedPathsForHighlight()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  val recentlyPlayedPaths: StateFlow<Set<String>> =
    RecentlyPlayedOps.observeRecentlyPlayedPaths()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  private var searchJob: Job? = null

  init {
    viewModelScope.launch {
      searchQuery
        .debounce(150L)
        .collectLatest { query ->
          executeSearch(query, searchScope.value)
        }
    }

    viewModelScope.launch {
      searchScope.collectLatest { scope ->
        if (searchQuery.value.isNotBlank()) {
          executeSearch(searchQuery.value, scope)
        }
      }
    }
  }

  fun updateQuery(newQuery: String) {
    searchQuery.value = newQuery
  }

  fun setScope(scope: SearchScope) {
    searchScope.value = scope
  }

  private fun executeSearch(query: String, scope: SearchScope) {
    searchJob?.cancel()
    if (query.isBlank()) {
      _searchResults.value = emptyList()
      _isSearchLoading.value = false
      return
    }

    searchJob = viewModelScope.launch(Dispatchers.IO) {
      _isSearchLoading.value = true
      try {
        val targetPath = if (scope == SearchScope.CURRENT_FOLDER) initialPath else null
        val isAudioEnabled = browserPreferences.showAudioFiles.get()
        val includeNoMedia = browserPreferences.includeNoMediaContent.get()
        val blacklist = foldersPreferences.blacklistedFolders.get()

        val results = mutableListOf<FileSystemItem>()

        // 1. Query videos from Hybrid DB
        val videos = hybridMediaIndex.searchMedia(query, targetPath, includeNoMedia)
          .filter { isAudioEnabled || !it.isAudio }
          .filter { it.path !in blacklist && !blacklist.any { b -> it.path.startsWith("$b/") } }

        // Pre-apply cached metadata to ensure valid durations on frame 1
        val cachedVideos = MetadataRetrieval.applyCachedMetadata(videos, browserPreferences, metadataCache)

        cachedVideos.forEach { video ->
          results.add(
            FileSystemItem.VideoFile(
              name = video.displayName,
              path = video.path,
              lastModified = video.dateModified,
              video = video,
            )
          )
        }

        // 2. Query Folders if searching all storage or targetPath
        if (targetPath == null) {
          val folders = MediaFileRepository.getAllVideoFolders(getApplication())
          folders.filter { it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true) }
            .filter { it.path !in blacklist && (isAudioEnabled || it.videoCount > 0) }
            .forEach { folder ->
              results.add(
                FileSystemItem.Folder(
                  name = folder.name,
                  path = folder.path,
                  lastModified = folder.lastModified,
                  videoCount = folder.videoCount,
                  audioCount = folder.audioCount,
                  totalSize = folder.totalSize,
                  totalDuration = folder.totalDuration,
                  videoSize = folder.videoSize,
                  audioSize = folder.audioSize,
                  videoDuration = folder.videoDuration,
                  audioDuration = folder.audioDuration,
                  hasSubfolders = false,
                  newCount = folder.newCount,
                  unwatchedVideoCount = folder.unwatchedVideoCount,
                )
              )
            }
        }

        val uniqueResults = results.distinctBy {
          when (it) {
            is FileSystemItem.VideoFile -> "video_${it.path.ifBlank { it.video.uri.toString() }}"
            is FileSystemItem.Folder -> "folder_${it.path}"
          }
        }

        // Update playback and watched state
        updatePlaybackStates(uniqueResults)
        _searchResults.value = uniqueResults
        _isSearchLoading.value = false

        // Background enrich any videos with missing durations or chips
        val isMetadataNeeded = MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)
        val videoItems = uniqueResults.filterIsInstance<FileSystemItem.VideoFile>()
        val uncached = videoItems.map { it.video }.filter { it.duration <= 0L || (isMetadataNeeded && (it.fps == 0f || it.subtitleCodec.isEmpty())) }
        if (uncached.isNotEmpty()) {
          val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
            context = getApplication(),
            videos = videoItems.map { it.video },
            browserPreferences = browserPreferences,
            metadataCache = metadataCache,
          )
          val enrichedMap = enrichedVideos.associateBy { it.id }
          val finalResults = uniqueResults.map { item ->
            if (item is FileSystemItem.VideoFile) {
              val enriched = enrichedMap[item.video.id] ?: item.video
              item.copy(video = enriched)
            } else {
              item
            }
          }
          updatePlaybackStates(finalResults)
          _searchResults.value = finalResults
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error executing search", e)
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
      }
    }
  }

  private suspend fun updatePlaybackStates(items: List<FileSystemItem>) {
    val playbackStates = playbackStateRepository.getAllPlaybackStates()
    val playbackMap = mutableMapOf<Long, Float>()
    val newIds = mutableSetOf<Long>()
    val watchedIds = mutableSetOf<Long>()
    val currentTime = System.currentTimeMillis()
    val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
    val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
    val watchedThreshold = browserPreferences.watchedThreshold.get()

    items.filterIsInstance<FileSystemItem.VideoFile>().forEach { item ->
      val video = item.video
      val state = playbackStates.find { it.mediaTitle == video.path || it.mediaTitle == video.displayName }
      if (state != null) {
        var isWatched = state.hasBeenWatched
        if (video.duration > 0 && state.timeRemaining != -1) {
          val durationSeconds = video.duration / 1000
          val watched = durationSeconds - state.timeRemaining.toLong()
          val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
          if (progressValue >= (watchedThreshold / 100f)) {
            isWatched = true
          }
          if (progressValue >= 0.01f && !isWatched) {
            playbackMap[video.id] = progressValue
          }
        }
        if (isWatched) {
          watchedIds.add(video.id)
        }
        if (state.timeRemaining == -1) {
          newIds.add(video.id)
        }
      } else {
        val videoAge = currentTime - (video.dateModified * 1000)
        if (videoAge <= thresholdMillis) {
          newIds.add(video.id)
        }
      }
    }
    _videoFilesWithPlayback.value = playbackMap
    _newVideoIds.value = newIds
    _watchedVideoIds.value = watchedIds
  }

  suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    val result = StorageOps.deleteVideos(getApplication(), videos)
    val paths = videos.map { it.path }
    metadataCache.invalidateVideos(paths)
    MediaFileRepository.clearCache()
    
    val deletedPaths = videos.map { it.path.ifBlank { it.uri.toString() } }.toSet()
    _searchResults.value = _searchResults.value.filterNot {
      it is FileSystemItem.VideoFile && (it.video.path.ifBlank { it.video.uri.toString() }) in deletedPaths
    }
    MediaLibraryEvents.notifyChanged()
    return result
  }

  suspend fun deleteFolders(folders: List<FileSystemItem.Folder>): Pair<Int, Int> {
    var successCount = 0
    var failureCount = 0
    withContext(Dispatchers.IO) {
      for (folder in folders) {
        try {
          val dir = File(folder.path)
          if (dir.deleteRecursively()) {
            successCount++
          } else {
            failureCount++
          }
        } catch (_: Exception) {
          failureCount++
        }
      }
    }
    if (successCount > 0) {
      val deletedFolderPaths = folders.map { it.path }.toSet()
      _searchResults.value = _searchResults.value.filterNot {
        it is FileSystemItem.Folder && it.path in deletedFolderPaths
      }
      MediaFileRepository.clearCache()
      MediaLibraryEvents.notifyChanged()
    }
    return Pair(successCount, failureCount)
  }

  suspend fun renameVideo(video: Video, newDisplayName: String): Result<Unit> {
    val oldPath = video.path
    val result = StorageOps.renameVideo(getApplication(), video, newDisplayName)
    if (result.isSuccess) {
      val parent = File(oldPath).parent ?: ""
      val newPath = if (parent.isNotBlank()) "$parent/$newDisplayName" else oldPath
      val updatedVideo = video.copy(
        displayName = newDisplayName,
        title = File(newDisplayName).nameWithoutExtension,
        path = newPath,
      )
      _searchResults.value = _searchResults.value.map { item ->
        if (item is FileSystemItem.VideoFile && item.video.id == video.id) {
          item.copy(name = newDisplayName, path = updatedVideo.path, video = updatedVideo)
        } else {
          item
        }
      }
      metadataCache.invalidateVideos(listOf(oldPath))
      MediaFileRepository.clearCache()
      MediaLibraryEvents.notifyChanged()
    }
    return result
  }

  suspend fun renameFolder(folder: FileSystemItem.Folder, newName: String): Boolean {
    val src = File(folder.path)
    val dst = File(src.parent ?: return false, newName)
    if (dst.exists()) return false
    val ok = src.renameTo(dst)
    if (ok) {
      android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
      _searchResults.value = _searchResults.value.map { item ->
        if (item is FileSystemItem.Folder && item.path == folder.path) {
          item.copy(name = newName, path = dst.absolutePath)
        } else {
          item
        }
      }
      MediaFileRepository.clearCache()
      MediaLibraryEvents.notifyChanged()
    }
    return ok
  }
}
