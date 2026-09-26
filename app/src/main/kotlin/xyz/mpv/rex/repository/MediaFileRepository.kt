package xyz.mpv.rex.repository

import android.content.Context
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.browser.PathComponent
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.utils.storage.CoreMediaScanner
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.utils.storage.VideoScanUtils
import xyz.mpv.rex.utils.storage.FileSystemOps
import xyz.mpv.rex.utils.storage.MediaScanPolicy
import xyz.mpv.rex.utils.media.MediaMetadataOps
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Unified repository for ALL media file operations.
 * Now refactored to delegate to specialized logic classes (Ops).
 */
object MediaFileRepository {

  /**
   * Clears all caches
   */
  fun clearCache() {
    CoreMediaScanner.clearCache()
  }

  // ==================== FOLDER OPERATIONS ====================

  suspend fun getAllVideoFolders(context: Context): List<VideoFolder> =
    MediaMetadataOps.getAllMediaFolders(context)

  suspend fun getAllVideoFoldersFast(context: Context, onProgress: ((Int) -> Unit)? = null): List<VideoFolder> = 
    getAllVideoFolders(context)

  // ==================== VIDEO FILE OPERATIONS ====================

  suspend fun getVideosInFolder(context: Context, bucketId: String): List<Video> =
    withContext(Dispatchers.IO) {
      val koin = GlobalContext.get()
      val browserPreferences = koin.get<BrowserPreferences>()
      val policy = MediaScanPolicy(
        includeNoMediaContent = browserPreferences.includeNoMediaContent.get(),
      )
      val direct = runCatching {
        VideoScanUtils.scanFolder(context, bucketId, policy)
      }.getOrNull()?.takeIf { it.access == VideoScanUtils.FolderAccess.READABLE }?.videos.orEmpty()
      val indexed = koin.get<HybridMediaIndexRepository>().getVideosInFolder(bucketId)
      koin.get<HybridMediaIndexRepository>().enrichFolderMetadata(bucketId)
      (direct + indexed)
        .associateBy { video -> video.path.ifBlank { video.uri.toString() } }
        .values
        .sortedBy { it.displayName.lowercase() }
    }

  suspend fun getVideosForBuckets(context: Context, bucketIds: Set<String>): List<Video> =
    withContext(Dispatchers.IO) {
      bucketIds
        .flatMap { id -> getVideosInFolder(context, id) }
        .associateBy { it.path.ifBlank { it.uri.toString() } }
        .values
        .toList()
    }

  suspend fun getAllVideos(context: Context): List<Video> =
    withContext(Dispatchers.IO) {
      val folders = getAllVideoFolders(context)
      val bucketIds = folders.map { it.bucketId }.toSet()
      getVideosForBuckets(context, bucketIds)
        .associateBy { it.path.ifBlank { it.uri.toString() } }
        .values
        .toList()
    }

  // ==================== FILE SYSTEM BROWSING ====================

  fun getPathComponents(path: String): List<PathComponent> =
    FileSystemOps.getPathComponents(path)

  suspend fun scanDirectory(
    context: Context,
    path: String,
    showAllFileTypes: Boolean = false,
    useFastCount: Boolean = false,
  ): Result<List<FileSystemItem>> =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(path)
        if (!directory.exists() || !directory.canRead() || !directory.isDirectory) {
          return@withContext Result.failure(Exception("Invalid directory: $path"))
        }

        val items = mutableListOf<FileSystemItem>()
        val koin = GlobalContext.get()
        val browserPreferences = koin.get<BrowserPreferences>()
        val appearancePreferences = koin.get<AppearancePreferences>()
        val playbackStateRepository = koin.get<PlaybackStateRepository>()
        
        val isAudioEnabled = browserPreferences.showAudioFiles.get()
        val scanPolicy = MediaScanPolicy(
          includeNoMediaContent = browserPreferences.includeNoMediaContent.get(),
        )
        val playbackStates = playbackStateRepository.getAllPlaybackStates()
        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
        
        // Get blacklisted folders
        val foldersPreferences = koin.get<xyz.mpv.rex.preferences.FoldersPreferences>()
        val blacklistedFolders = foldersPreferences.blacklistedFolders.get()

        val folders = if (scanPolicy.includeNoMediaContent) {
          val hybridIndex = koin.get<HybridMediaIndexRepository>()
          hybridIndex.ensureFreshIfEmpty()
          hybridIndex.getFoldersInDirectory(
            parentPath = path,
            playbackStates = playbackStates,
            thresholdDays = thresholdDays,
            watchedThreshold = browserPreferences.watchedThreshold.get(),
          )
        } else {
          CoreMediaScanner.getFoldersInDirectory(
            context = context,
            parentPath = path,
            playbackStates = playbackStates,
            thresholdDays = thresholdDays,
            blacklistedFolders = blacklistedFolders,
            policy = scanPolicy,
          )
        }
        folders
          .filter { data ->
            (isAudioEnabled || data.videoCount > 0) &&
            (scanPolicy.includeNoMediaContent || !FileFilterUtils.isWithinNoMediaBoundary(File(data.path)))
          }
          .forEach { folderData ->
            items.add(
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
                unwatchedVideoCount = folderData.unwatchedVideoCount
              ),
            )
          }

        // Get videos in current directory - Hide if current path is blacklisted
        if (path !in blacklistedFolders) {
          val folderScan = VideoScanUtils.scanFolder(context, path, scanPolicy)
          if (folderScan.access == VideoScanUtils.FolderAccess.INACCESSIBLE) {
            return@withContext Result.failure(Exception("Directory is not accessible: $path"))
          }
          folderScan.videos
            .filter { video -> isAudioEnabled || !video.isAudio }
            .forEach { video ->
              items.add(
                FileSystemItem.VideoFile(
                  name = video.displayName,
                  path = video.path,
                  lastModified = File(video.path).lastModified(),
                  video = video,
                ),
              )
            }
        }

        Result.success(items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    FileSystemOps.getStorageRoots(context)
}
