package xyz.mpv.rex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.MetadataRetrieval
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.utils.storage.FileTypeUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class FolderWithNewCount(
  val folder: VideoFolder,
  val newVideoCount: Int = 0,
)

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel<FolderWithNewCount>(application),
  KoinComponent {
  private val foldersPreferences: FoldersPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject()
  private val hybridMediaIndex: HybridMediaIndexRepository by inject()

  private val _allVideoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  // Set of folder paths that have ever had a video played from them
  val playedFolderPaths: StateFlow<Set<String>> =
    recentlyPlayedRepository
      .observeRecentlyPlayed(limit = 1000)
      .map { list ->
        list.mapNotNull { java.io.File(it.filePath).parent }.toSet()
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  private val _foldersWithNewCount = MutableStateFlow<List<FolderWithNewCount>>(emptyList())
  val foldersWithNewCount: StateFlow<List<FolderWithNewCount>> = _foldersWithNewCount.asStateFlow()

  // Track if initial load has completed to prevent empty state flicker
  private val _hasCompletedInitialLoad = MutableStateFlow(false)
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  // Track if folders were deleted leaving list empty
  private val _foldersWereDeleted = MutableStateFlow(false)
  val foldersWereDeleted: StateFlow<Boolean> = _foldersWereDeleted.asStateFlow()

  // Track previous folder count to detect if all folders were deleted
  private var previousFolderCount = 0

  private val _isEnriching = MutableStateFlow(false)
  val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

  // Track the current scan job to prevent concurrent scans
  private var currentScanJob: Job? = null

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Load cached folders instantly for immediate display
    val hasCachedData = loadCachedFolders()

    if (hasCachedData) {
      // If we have cached data, show it immediately and refresh silently in background
      _hasCompletedInitialLoad.value = true
      _isLoading.value = false
      viewModelScope.launch(Dispatchers.IO) {
        loadData() 
      }
    } else {
      // No cache, must show scanning UI
      loadData()
    }

    // Note: BaseBrowserViewModel handles MediaLibraryEvents.changes and playback state observation centrally.

    // Filter folders based on blacklist and audio visibility
    viewModelScope.launch {
      combine(
        _allVideoFolders, 
        foldersPreferences.blacklistedFolders.changes(),
        browserPreferences.showAudioFiles.changes(),
        browserPreferences.includeNoMediaContent.changes(),
      ) { folders, blacklist, showAudio, includeNoMedia ->
        folders.filter { folder -> 
          folder.path !in blacklist &&
          (showAudio || folder.videoCount > 0) &&
          (includeNoMedia || !FileFilterUtils.isWithinNoMediaBoundary(File(folder.path)))
        }
      }.collectLatest { filteredFolders ->
        // Check if folders became empty after having folders
        if (previousFolderCount > 0 && filteredFolders.isEmpty()) {
          _foldersWereDeleted.value = true
          Log.d(TAG, "Folders became empty (had $previousFolderCount folders before)")
        } else if (filteredFolders.isNotEmpty()) {
          // Reset flag if folders now exist
          _foldersWereDeleted.value = false
        }

        // Update previous count
        previousFolderCount = filteredFolders.size

        _videoFolders.value = filteredFolders
        
        // Map to FolderWithNewCount using the pre-calculated newCount from repository
        _foldersWithNewCount.value = filteredFolders.map { 
          FolderWithNewCount(it, it.newCount) 
        }

        // Save to cache for next app launch (save unfiltered list)
        saveFoldersToCache(_allVideoFolders.value)
      }
    }
  }

  private fun loadCachedFolders(): Boolean {
    var hasCachedData = false
    val prefs =
      getApplication<Application>().getSharedPreferences("folder_cache", android.content.Context.MODE_PRIVATE)
    val cachedJson = prefs.getString("folders", null)

    if (cachedJson != null) {
      try {
        val folders = parseFoldersFromJson(cachedJson)
        if (folders.isNotEmpty()) {
          _allVideoFolders.value = folders
          hasCachedData = true
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error parsing cached folders", e)
      }
    }
    return hasCachedData
  }

  private fun saveFoldersToCache(folders: List<VideoFolder>) {
    try {
      val json = serializeFoldersToJson(folders)
      val prefs =
        getApplication<Application>().getSharedPreferences("folder_cache", android.content.Context.MODE_PRIVATE)
      prefs.edit().putString("folders", json).apply()
    } catch (e: Exception) {
      Log.e(TAG, "Error saving folders to cache", e)
    }
  }

  // Basic manual JSON serialization to avoid adding GSON/Kotlinx Serialization dependencies if not present
  // In a real app, use a proper library
  private fun serializeFoldersToJson(folders: List<VideoFolder>): String {
    // For now using a simple approach since we only cache basic info
    return folders.joinToString("|") { folder ->
      "${folder.bucketId};${folder.name};${folder.path};${folder.videoCount};${folder.audioCount};${folder.totalSize};${folder.totalDuration};${folder.lastModified};${folder.newCount};${folder.videoSize};${folder.audioSize};${folder.videoDuration};${folder.audioDuration}"
    }
  }

  private fun parseFoldersFromJson(json: String): List<VideoFolder> {
    if (json.isBlank()) return emptyList()
    return json.split("|").mapNotNull { line ->
      try {
        val parts = line.split(";")
        val videoCount = parts[3].toInt()
        val audioCount = parts[4].toInt()
        val totalSize = parts[5].toLong()
        val totalDuration = parts[6].toLong()
        val lastModified = parts[7].toLong()
        val newCount = if (parts.size > 8) parts[8].toInt() else 0
        val videoSize = if (parts.size > 9) parts[9].toLong() else 0L
        val audioSize = if (parts.size > 10) parts[10].toLong() else 0L
        val videoDuration = if (parts.size > 11) parts[11].toLong() else 0L
        val audioDuration = if (parts.size > 12) parts[12].toLong() else 0L
        VideoFolder(
          bucketId = parts[0],
          name = parts[1],
          path = parts[2],
          videoCount = videoCount,
          audioCount = audioCount,
          totalSize = totalSize,
          totalDuration = totalDuration,
          videoSize = videoSize,
          audioSize = audioSize,
          videoDuration = videoDuration,
          audioDuration = audioDuration,
          lastModified = lastModified,
          newCount = newCount,
        )
      } catch (e: Exception) {
        null
      }
    }
  }

  fun cancelIndexScan() {
    hybridMediaIndex.cancelScan()
  }

  override fun refresh(silent: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      if (!silent) _isLoading.value = true
      currentScanJob?.cancel()
      currentScanJob = null
      hybridMediaIndex.ensureFresh(force = true, userInitiated = !silent)
      loadData()
    }
  }

  override fun loadData() {
    loadVideoFolders()
  }

  private fun loadVideoFolders() {
    currentScanJob?.cancel()
    currentScanJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val isFirstLoad = _allVideoFolders.value.isEmpty()
        if (isFirstLoad) {
          _isLoading.value = true
        }
        
        val startTime = System.currentTimeMillis()
        var folders = MediaFileRepository.getAllVideoFolders(getApplication())
        
        val scanTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Media scan completed in ${scanTime}ms, found ${folders.size} folders")

        // Enrich with metadata only if needed
        if (MetadataRetrieval.isFolderMetadataNeeded(browserPreferences)) {
          if (isFirstLoad) {
            _isEnriching.value = true
          }
          folders = MetadataRetrieval.enrichFoldersIfNeeded(
            context = getApplication(),
            folders = folders,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
          )
          _isEnriching.value = false
        }

        val blacklist = foldersPreferences.blacklistedFolders.get()
        val showAudio = browserPreferences.showAudioFiles.get()
        val includeNoMedia = browserPreferences.includeNoMediaContent.get()
        val filteredFolders = folders.filter { folder -> 
          folder.path !in blacklist &&
          (showAudio || folder.videoCount > 0) &&
          (includeNoMedia || !FileFilterUtils.isWithinNoMediaBoundary(File(folder.path)))
        }
        _videoFolders.value = filteredFolders
        _foldersWithNewCount.value = filteredFolders.map { 
          FolderWithNewCount(it, it.newCount) 
        }
        _allVideoFolders.value = folders
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _videoFolders.value = emptyList()
        _foldersWithNewCount.value = emptyList()
        _allVideoFolders.value = emptyList()
      } finally {
        if (coroutineContext.isActive) {
          _isLoading.value = false
          _hasCompletedInitialLoad.value = true
        }
      }
    }
  }

  suspend fun renameFolder(folder: VideoFolder, newName: String): Boolean {
    val src = java.io.File(folder.path)
    val dst = java.io.File(src.parent ?: return false, newName)
    if (dst.exists()) return false
    val ok = src.renameTo(dst)
    if (ok) {
      android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
      _foldersWereDeleted.value = true
    }
    return ok
  }

  /**
   * Delete folders and update state
   */
  suspend fun deleteFolders(foldersToDelete: List<VideoFolder>): Pair<Int, Int> = withContext(Dispatchers.IO) {
    val showAudio = browserPreferences.showAudioFiles.get()
    var successCount = 0
    var failureCount = 0

    foldersToDelete.forEach { folder ->
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
          successCount++
        } else {
          failureCount++
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete folder contents: ${folder.path}", e)
        failureCount++
      }
    }

    // Update local state if anything was deleted
    if (successCount > 0) {
      val currentFolders = _allVideoFolders.value.toMutableList()
      foldersToDelete.forEach { folder ->
        // For simplicity, we remove all requested folders from UI state if we attempted cleaning
        currentFolders.removeAll { it.path == folder.path }
      }
      _allVideoFolders.value = currentFolders
      _foldersWereDeleted.value = true
      MediaLibraryEvents.notifyChanged()
    }

    Pair(successCount, failureCount)
  }
}
