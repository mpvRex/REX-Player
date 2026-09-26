package xyz.mpv.rex.utils.media

import android.content.Context
import android.util.Log
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.utils.storage.FileFilterUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Operations for mapping media files to high-level domain models like [VideoFolder].
 */
object MediaMetadataOps {
    private const val TAG = "MediaMetadataOps"

    /**
     * Scans and maps all folders containing media into [VideoFolder] domain objects.
     */
    suspend fun getAllMediaFolders(context: Context): List<VideoFolder> =
        withContext(Dispatchers.IO) {
            try {
                val koin = GlobalContext.get()
                val browserPreferences = koin.get<BrowserPreferences>()
                val appearancePreferences = koin.get<AppearancePreferences>()
                val playbackStateRepository = koin.get<PlaybackStateRepository>()
                
                val isAudioEnabled = browserPreferences.showAudioFiles.get()
                val playbackStates = playbackStateRepository.getAllPlaybackStates()
                val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
                
                val foldersPreferences = koin.get<xyz.mpv.rex.preferences.FoldersPreferences>()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get()
                
                val hybridIndex = koin.get<HybridMediaIndexRepository>()
                hybridIndex.ensureFreshIfEmpty()
                val includeNoMedia = browserPreferences.includeNoMediaContent.get()
                val folders = hybridIndex.getFlatFolders(
                    playbackStates = playbackStates,
                    thresholdDays = thresholdDays,
                    watchedThreshold = browserPreferences.watchedThreshold.get(),
                )
                folders
                    .filter { folder -> 
                        (isAudioEnabled || folder.videoCount > 0) &&
                        folder.path !in blacklistedFolders &&
                        (includeNoMedia || !FileFilterUtils.isWithinNoMediaBoundary(File(folder.path)))
                    }
                    .map { folder ->
                        VideoFolder(
                            bucketId = folder.id,
                            name = folder.name,
                            path = folder.path,
                            videoCount = folder.videoCount,
                            audioCount = folder.audioCount,
                            totalSize = folder.totalSize,
                            totalDuration = folder.totalDuration,
                            videoSize = folder.videoSize,
                            audioSize = folder.audioSize,
                            videoDuration = folder.videoDuration,
                            audioDuration = folder.audioDuration,
                            lastModified = folder.lastModified,
                            newCount = folder.newCount,
                            unwatchedVideoCount = folder.unwatchedVideoCount
                        )
                    }
                    .distinctBy { it.bucketId }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping media folders", e)
                emptyList()
            }
        }
}
