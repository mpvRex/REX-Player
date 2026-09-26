package xyz.mpv.rex.utils.storage

import android.content.Context
import android.os.Environment
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.browser.PathComponent
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Operations for filesystem-level tasks like path parsing, directory scanning,
 * and storage volume management.
 */
object FileSystemOps {

    /**
     * Parses a path into breadcrumb components.
     */
    fun getPathComponents(path: String): List<PathComponent> {
        if (path.isBlank()) return emptyList()
        val components = mutableListOf<PathComponent>()
        val normalizedPath = path.trimEnd('/')
        val parts = normalizedPath.split("/").filter { it.isNotEmpty() }
        components.add(PathComponent("Root", "/"))
        var currentPath = ""
        for (part in parts) {
            currentPath += "/$part"
            components.add(PathComponent(part, currentPath))
        }
        return components
    }

    /**
     * Gets all storage volume roots with recursive counts.
     */
    suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
        withContext(Dispatchers.IO) {
            val roots = mutableListOf<FileSystemItem.Folder>()
            try {
                val koin = GlobalContext.get()
                val appearancePreferences = koin.get<AppearancePreferences>()
                val browserPreferences = koin.get<BrowserPreferences>()
                val playbackStateRepository = koin.get<PlaybackStateRepository>()
                val hybridIndex = koin.get<HybridMediaIndexRepository>()
                
                val playbackStates = playbackStateRepository.getAllPlaybackStates()
                val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
                val useHybridIndex = browserPreferences.includeNoMediaContent.get()
                if (useHybridIndex) hybridIndex.ensureFreshIfEmpty()

                val foldersPreferences = koin.get<xyz.mpv.rex.preferences.FoldersPreferences>()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get()

                // Internal Storage
                val primaryStorage = Environment.getExternalStorageDirectory()
                if (primaryStorage.exists() && primaryStorage.canRead()) {
                    val primaryPath = primaryStorage.absolutePath
                    val folderData = if (useHybridIndex) {
                        hybridIndex.getRecursiveFolder(
                            path = primaryPath,
                            playbackStates = playbackStates,
                            thresholdDays = thresholdDays,
                            watchedThreshold = browserPreferences.watchedThreshold.get(),
                        )
                    } else {
                        CoreMediaScanner.getFolderRecursiveData(context, primaryPath, playbackStates, thresholdDays, blacklistedFolders)
                    }
                    if (folderData != null) {
                        roots.add(
                            FileSystemItem.Folder(
                                name = "Internal Storage",
                                path = primaryPath,
                                lastModified = primaryStorage.lastModified(),
                                videoCount = folderData.videoCount,
                                audioCount = folderData.audioCount,
                                totalSize = folderData.totalSize,
                                totalDuration = folderData.totalDuration,
                                videoSize = folderData.videoSize,
                                audioSize = folderData.audioSize,
                                videoDuration = folderData.videoDuration,
                                audioDuration = folderData.audioDuration,
                                hasSubfolders = true,
                                newCount = folderData.newCount,
                                unwatchedVideoCount = folderData.unwatchedVideoCount
                            )
                        )
                    }
                }

                // External Volumes (SD Cards, USB)
                val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
                for (volume in externalVolumes) {
                    val volumePath = StorageVolumeUtils.getVolumePath(volume) ?: continue
                    val volumeDir = File(volumePath)
                    if (volumeDir.exists() && volumeDir.canRead()) {
                        val folderData = if (useHybridIndex) {
                            hybridIndex.getRecursiveFolder(
                                path = volumePath,
                                playbackStates = playbackStates,
                                thresholdDays = thresholdDays,
                                watchedThreshold = browserPreferences.watchedThreshold.get(),
                            )
                        } else {
                            CoreMediaScanner.getFolderRecursiveData(context, volumePath, playbackStates, thresholdDays, blacklistedFolders)
                        }
                        if (folderData != null) {
                            roots.add(
                                FileSystemItem.Folder(
                                    name = volume.getDescription(context),
                                    path = volumeDir.absolutePath,
                                    lastModified = volumeDir.lastModified(),
                                    videoCount = folderData.videoCount,
                                    audioCount = folderData.audioCount,
                                    totalSize = folderData.totalSize,
                                    totalDuration = folderData.totalDuration,
                                    videoSize = folderData.videoSize,
                                    audioSize = folderData.audioSize,
                                    videoDuration = folderData.videoDuration,
                                    audioDuration = folderData.audioDuration,
                                    hasSubfolders = true,
                                    newCount = folderData.newCount,
                                    unwatchedVideoCount = folderData.unwatchedVideoCount
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Log and return what we have
            }
            roots
        }
}
