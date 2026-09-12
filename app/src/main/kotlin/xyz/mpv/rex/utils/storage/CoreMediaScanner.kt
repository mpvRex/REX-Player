package xyz.mpv.rex.utils.storage

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Core Media Scanner Engine (Unified)
 * 
 * This is the central engine for discovering media files and folders.
 * It combines MediaStore (fast) and filesystem (fallback) scanning into a 
 * single, consistent model that powers all views.
 */
object CoreMediaScanner {
    private const val TAG = "CoreMediaScanner"
    
    // Smart cache with configurable TTL
    private var cachedMediaData: Map<String, FolderNode>? = null
    private var cacheTimestamp: Long = 0
    private var cachedShowAudioFiles: Boolean? = null
    private const val CACHE_TTL_MS = 180_000L // 3 minutes for standard refreshes
    
    /**
     * Clear all scanning caches
     */
    fun clearCache() {
        Log.d(TAG, "Clearing core media scanner cache")
        cachedMediaData = null
        cacheTimestamp = 0
        cachedShowAudioFiles = null
    }
    
    /**
     * Internal node for the hierarchical storage tree
     */
    private data class FolderNode(
        val path: String,
        val name: String,
        val directVideoCount: Int = 0,
        val directAudioCount: Int = 0,
        val directNewCount: Int = 0,
        val directUnwatchedCount: Int = 0,
        val directSize: Long = 0L,
        val directDuration: Long = 0L,
        val lastModified: Long = 0L,
        val hasDirectSubfolders: Boolean = false,
        var isFlattened: Boolean = false,
        // Recursive properties (will be calculated after scan)
        var recursiveVideoCount: Int = 0,
        var recursiveAudioCount: Int = 0,
        var recursiveNewCount: Int = 0,
        var recursiveUnwatchedCount: Int = 0,
        var recursiveSize: Long = 0L,
        var recursiveDuration: Long = 0L,
        var latestModified: Long = 0L
    )

    /**
     * Basic info for a single media item found during scan
     */
    private data class ScannedItem(
        val name: String,
        val path: String,
        val size: Long,
        val duration: Long,
        val dateModified: Long,
        val isAudio: Boolean = false
    )

    /**
     * Get all folders that contain media files (flat list for Album View)
     */
    suspend fun getFlatMediaFolders(
        context: Context,
        playbackStates: List<PlaybackStateEntity> = emptyList(),
        thresholdDays: Int = 7,
        blacklistedFolders: Set<String> = emptySet()
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val allNodes = getOrBuildMediaTree(context, playbackStates, thresholdDays, blacklistedFolders)
        
        // Filter for folders that have DIRECT media files
        allNodes.values
            .filter { it.directVideoCount > 0 || it.directAudioCount > 0 }
            .map { node ->
                MediaFolder(
                    id = node.path,
                    name = node.name,
                    path = node.path,
                    videoCount = node.directVideoCount,
                    audioCount = node.directAudioCount,
                    totalSize = node.directSize,
                    totalDuration = node.directDuration,
                    lastModified = node.lastModified,
                    hasSubfolders = node.hasDirectSubfolders,
                    isRecursive = false,
                    newCount = node.directNewCount,
                    unwatchedVideoCount = node.directUnwatchedCount
                )
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /**
     * Get immediate children of a parent path (for Tree/Filesystem View)
     */
    suspend fun getFoldersInDirectory(
        context: Context,
        parentPath: String,
        playbackStates: List<PlaybackStateEntity> = emptyList(),
        thresholdDays: Int = 7,
        blacklistedFolders: Set<String> = emptySet(),
        policy: MediaScanPolicy = MediaScanPolicy(),
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val allNodes = getOrBuildMediaTree(context, playbackStates, thresholdDays, blacklistedFolders)

        val foldersByPath = getEffectiveChildren(parentPath, allNodes)
            .associate { node ->
                node.path to MediaFolder(
                    id = node.path,
                    name = node.name,
                    path = node.path,
                    videoCount = node.recursiveVideoCount,
                    audioCount = node.recursiveAudioCount,
                    totalSize = node.recursiveSize,
                    totalDuration = node.recursiveDuration,
                    lastModified = node.latestModified,
                    hasSubfolders = node.hasDirectSubfolders,
                    isRecursive = true,
                    newCount = node.recursiveNewCount,
                    unwatchedVideoCount = node.recursiveUnwatchedCount,
                )
            }
            .toMutableMap()

        if (policy.includeNoMediaContent) {
            discoverDirectNoMediaFolders(parentPath, blacklistedFolders, policy)
                .forEach { foldersByPath[it.path] = it }
        }

        foldersByPath.values.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /**
     * Discovers only immediate children of the active directory. This is
     * deliberately separate from the cached global MediaStore tree.
     */
    private suspend fun discoverDirectNoMediaFolders(
        parentPath: String,
        blacklistedFolders: Set<String>,
        policy: MediaScanPolicy,
    ): List<MediaFolder> {
        val parent = File(parentPath)
        val children = parent.listFiles() ?: return emptyList()
        val parentIsNoMedia = FileFilterUtils.isWithinNoMediaBoundary(parent)
        val result = mutableListOf<MediaFolder>()

        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (!child.isDirectory || child.absolutePath in blacklistedFolders) continue
            if (FileFilterUtils.shouldSkipFolder(child, policy)) continue
            if (!parentIsNoMedia && !FileFilterUtils.hasNoMediaFile(child)) continue

            val entries = child.listFiles() ?: continue
            var videoCount = 0
            var audioCount = 0
            var totalSize = 0L
            var latestModified = child.lastModified() / 1000
            var hasSubfolders = false

            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                if (entry.isDirectory) {
                    if (!FileFilterUtils.shouldSkipFolder(entry, policy)) hasSubfolders = true
                    continue
                }
                if (!entry.isFile || FileFilterUtils.shouldSkipFile(entry)) continue

                val isVideo = FileTypeUtils.isVideoFile(entry)
                val isAudio = FileTypeUtils.isAudioFile(entry)
                if (!isVideo && !isAudio) continue

                if (isAudio) audioCount++ else videoCount++
                totalSize += entry.length()
                latestModified = maxOf(latestModified, entry.lastModified() / 1000)
            }

            if (videoCount > 0 || audioCount > 0 || hasSubfolders) {
                val normalizedPath =
                    runCatching { child.canonicalPath }.getOrElse { child.absoluteFile.normalize().path }
                result += MediaFolder(
                    id = normalizedPath,
                    name = child.name,
                    path = normalizedPath,
                    videoCount = videoCount,
                    audioCount = audioCount,
                    totalSize = totalSize,
                    totalDuration = 0,
                    lastModified = latestModified,
                    hasSubfolders = hasSubfolders,
                    isRecursive = false,
                    newCount = 0,
                    unwatchedVideoCount = videoCount + audioCount,
                )
            }
        }

        return result
    }

    /**
     * Helper to find effective children by bypassing flattened folders
     */
    private fun getEffectiveChildren(parentPath: String, allNodes: Map<String, FolderNode>): List<FolderNode> {
        val directChildren = allNodes.values.filter { 
            val parentFile = File(it.path).parent
            parentFile == parentPath
        }
        
        val result = mutableListOf<FolderNode>()
        for (child in directChildren) {
            if (child.isFlattened) {
                // If child is flattened, its children become our children
                result.addAll(getEffectiveChildren(child.path, allNodes))
            } else {
                result.add(child)
            }
        }
        return result
    }

    /**
     * Get recursive folder data for a specific path (for Storage Roots)
     */
    suspend fun getFolderRecursiveData(
        context: Context,
        path: String,
        playbackStates: List<PlaybackStateEntity> = emptyList(),
        thresholdDays: Int = 7,
        blacklistedFolders: Set<String> = emptySet()
    ): MediaFolder? = withContext(Dispatchers.IO) {
        val allNodes = getOrBuildMediaTree(context, playbackStates, thresholdDays, blacklistedFolders)
        allNodes[path]?.let { node ->
            MediaFolder(
                id = node.path,
                name = node.name,
                path = node.path,
                videoCount = node.recursiveVideoCount,
                audioCount = node.recursiveAudioCount,
                totalSize = node.recursiveSize,
                totalDuration = node.recursiveDuration,
                lastModified = node.latestModified,
                hasSubfolders = node.hasDirectSubfolders,
                isRecursive = true,
                newCount = node.recursiveNewCount
            )
        }
    }

    /**
     * Main entry point for the scanning engine
     */
    private suspend fun getOrBuildMediaTree(
        context: Context,
        playbackStates: List<PlaybackStateEntity>,
        thresholdDays: Int,
        blacklistedFolders: Set<String> = emptySet()
    ): Map<String, FolderNode> {
        val browserPreferences = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
        val showAudioFiles = browserPreferences.showAudioFiles.get()
        val now = System.currentTimeMillis()
        cachedMediaData?.let { cached ->
            if (now - cacheTimestamp < CACHE_TTL_MS && cachedShowAudioFiles == showAudioFiles) {
                return cached
            }
        }
        
        val tree = buildFullMediaTree(context, playbackStates, thresholdDays, blacklistedFolders, browserPreferences, showAudioFiles)
        cachedMediaData = tree
        cacheTimestamp = now
        cachedShowAudioFiles = showAudioFiles
        return tree
    }

    /**
     * Performs the actual scan and hierarchy calculation
     */
    private suspend fun buildFullMediaTree(
        context: Context,
        playbackStates: List<PlaybackStateEntity>,
        thresholdDays: Int,
        blacklistedFolders: Set<String>,
        browserPreferences: BrowserPreferences,
        showAudioFiles: Boolean,
    ): Map<String, FolderNode> {
        val allNodes = mutableMapOf<String, FolderNode>()
        val rawMediaByFolder = mutableMapOf<String, MutableList<ScannedItem>>()

        // Step 1: MediaStore Scan
        scanMediaStore(context, rawMediaByFolder)
        
        // Step 2: Filesystem Scan for external volumes
        scanExternalVolumes(context, rawMediaByFolder)

        val currentTime = System.currentTimeMillis()
        val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
        
        // Get watched threshold from preferences
        val watchedThreshold = browserPreferences.watchedThreshold.get()

        // Step 3: Build Nodes for folders with direct media
        for ((folderPath, items) in rawMediaByFolder) {
            val isBlacklisted = blacklistedFolders.contains(folderPath)

            val file = File(folderPath)
            var videoCount = 0
            var audioCount = 0
            var newCount = 0
            var unwatchedCount = 0
            var totalSize = 0L
            var totalDuration = 0L
            var latestModified = 0L
            
            if (!isBlacklisted) {
                for (item in items) {
                    totalSize += item.size
                    totalDuration += item.duration
                    if (item.dateModified > latestModified) latestModified = item.dateModified
                    
                    if (item.isAudio) {
                        audioCount++
                    } else {
                        videoCount++
                    }

                    if (!showAudioFiles && item.isAudio) continue

                    // Calculate unwatched status for all media (audio and video)
                    val playbackState = playbackStates.find {
                        it.mediaTitle == item.path || it.mediaTitle == item.name || it.mediaTitle == File(item.path).name
                    }
                    var isWatched = false
                    
                    if (playbackState != null) {
                        if (playbackState.hasBeenWatched) {
                            isWatched = true
                        } else if (item.duration > 0 && playbackState.timeRemaining != -1) {
                            val durationSeconds = item.duration / 1000
                            val watched = durationSeconds - playbackState.timeRemaining.toLong()
                            val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
                            if (progressValue >= (watchedThreshold / 100f)) {
                                 isWatched = true
                            }
                        }
                    }
                    
                    if (!isWatched) {
                        unwatchedCount++
                    }

                    // Calculate NEW status: unplayed videos within the time threshold
                    val videoAge = currentTime - (item.dateModified * 1000)
                    val isNew = (playbackState == null && videoAge <= thresholdMillis) || (playbackState != null && playbackState.timeRemaining == -1)
                    if (isNew) {
                        newCount++
                    }
                }
            }
            
            allNodes[folderPath] = FolderNode(
                path = folderPath,
                name = file.name,
                directVideoCount = videoCount,
                directAudioCount = audioCount,
                directNewCount = newCount,
                directUnwatchedCount = unwatchedCount,
                directSize = totalSize,
                directDuration = totalDuration,
                lastModified = latestModified
            )
        }

        // Step 4: Build Hierarchy and Calculate Recursive Counts
        buildHierarchy(context, allNodes, blacklistedFolders)
        
        return allNodes
    }

    private fun scanMediaStore(
        context: Context,
        rawMedia: MutableMap<String, MutableList<ScannedItem>>
    ) {
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI ?: return
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI ?: return
        // Step 1: Scan Videos
        queryMediaStore(context, videoUri, false, rawMedia)
        // Step 2: Scan Audio
        queryMediaStore(context, audioUri, true, rawMedia)
    }

    private fun queryMediaStore(
        context: Context,
        uri: android.net.Uri,
        isAudio: Boolean,
        rawMedia: MutableMap<String, MutableList<ScannedItem>>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIdx) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    rawMedia.getOrPut(folderPath) { mutableListOf() }.add(
                        ScannedItem(
                            name = cursor.getString(nameIdx) ?: file.name,
                            path = path,
                            size = cursor.getLong(sizeIdx),
                            duration = cursor.getLong(durationIdx),
                            dateModified = cursor.getLong(dateIdx),
                            isAudio = isAudio
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query error", e)
        }
    }

    private fun scanExternalVolumes(
        context: Context,
        rawMedia: MutableMap<String, MutableList<ScannedItem>>
    ) {
        try {
            val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
            for (volume in externalVolumes) {
                val volumePath = StorageVolumeUtils.getVolumePath(volume) ?: continue
                val volumeDir = File(volumePath)
                if (volumeDir.exists() && volumeDir.canRead()) {
                    recursiveFileSystemScan(volumeDir, rawMedia, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "External volume scan error", e)
        }
    }

    private fun recursiveFileSystemScan(
        directory: File,
        rawMedia: MutableMap<String, MutableList<ScannedItem>>,
        depth: Int
    ) {
        if (depth > 20) return // Safety limit
        val files = directory.listFiles() ?: return
        
        val itemsInFolder = mutableListOf<ScannedItem>()
        for (file in files) {
            if (file.isDirectory) {
                if (!FileFilterUtils.shouldSkipFolder(file)) {
                    recursiveFileSystemScan(file, rawMedia, depth + 1)
                }
            } else if (file.isFile) {
                if (FileTypeUtils.isMediaFile(file)) {
                    itemsInFolder.add(
                        ScannedItem(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            duration = 0, // Filesystem doesn't give duration
                            dateModified = file.lastModified() / 1000,
                            isAudio = FileTypeUtils.isAudioFile(file)
                        )
                    )
                }
            }
        }
        
        if (itemsInFolder.isNotEmpty()) {
            val path = directory.absolutePath
            // Only add if MediaStore didn't already pick up this folder
            if (!rawMedia.containsKey(path)) {
                rawMedia[path] = itemsInFolder
            }
        }
    }

    private fun buildHierarchy(context: Context, nodes: MutableMap<String, FolderNode>, blacklistedFolders: Set<String>) {
        val sortedPaths = nodes.keys.sortedByDescending { it.length }
        
        // Find all parent paths needed
        val allPathsNeeded = mutableSetOf<String>()
        for (path in nodes.keys) {
            var p = File(path).parent
            while (p != null && p.length > 1) {
                allPathsNeeded.add(p)
                p = File(p).parent
            }
        }
        
        // Create nodes for parents that don't have direct media
        for (p in allPathsNeeded) {
            if (!nodes.containsKey(p)) {
                nodes[p] = FolderNode(path = p, name = File(p).name)
            }
        }
        
        // Recalculate sorted paths with new parent nodes
        val finalSortedPaths = nodes.keys.sortedByDescending { it.length }
        
        for (path in finalSortedPaths) {
            val node = nodes[path]!!
            
            // Set initial recursive values from direct values
            node.recursiveVideoCount = node.directVideoCount
            node.recursiveAudioCount = node.directAudioCount
            node.recursiveNewCount = node.directNewCount
            node.recursiveUnwatchedCount = node.directUnwatchedCount
            node.recursiveSize = node.directSize
            node.recursiveDuration = node.directDuration
            node.latestModified = node.lastModified
            
            // Accumulate from all children (nodes where this path is the direct parent)
            var hasSubfolders = false
            for (otherNode in nodes.values) {
                if (File(otherNode.path).parent == path) {
                    hasSubfolders = true
                    node.recursiveVideoCount += otherNode.recursiveVideoCount
                    node.recursiveAudioCount += otherNode.recursiveAudioCount
                    node.recursiveNewCount += otherNode.recursiveNewCount
                    node.recursiveUnwatchedCount += otherNode.recursiveUnwatchedCount
                    node.recursiveSize += otherNode.recursiveSize
                    node.recursiveDuration += otherNode.recursiveDuration
                    if (otherNode.latestModified > node.latestModified) {
                        node.latestModified = otherNode.latestModified
                    }
                }
            }
            
            // Update the property (direct modification of var in data class)
            // But node is val in the loop, so I need to update the map
            nodes[path] = node.copy(hasDirectSubfolders = hasSubfolders)
        }

        // SMART TREE FLATTENING (User Logic)
        // Logic: A folder should be hidden (flattened) if it has NO direct media AND only has one child folder with media.
        // This avoids deep nesting of single folders in Tree View.
        val sortedForFlattening = nodes.keys.sortedBy { it.length } // Shortest paths first to check from root down
        
        for (path in sortedForFlattening) {
            val node = nodes[path] ?: continue
            
            // If it has direct media, we MUST keep it
            if (node.directVideoCount > 0 || node.directAudioCount > 0) continue
            
            // Count direct children that actually contain media (recursive count > 0)
            val childrenWithMedia = nodes.values.filter { 
                File(it.path).parent == path && (it.recursiveVideoCount > 0 || it.recursiveAudioCount > 0)
            }
            
            // Logic: Mark as flattened if it only has one media child (e.g. Music/Recordings)
            // AND we are NOT at the storage root (roots should always be shown if they have media)
            if (childrenWithMedia.size < 2) {
                // Determine if this is a storage root - they are special
                val isStorageRoot = StorageVolumeUtils.isStorageRoot(context, path)
                if (!isStorageRoot) {
                    node.isFlattened = true
                }
            }
        }
        
        // Final pass to clean up empty folders that might have been left over
        nodes.entries.removeIf { it.value.recursiveVideoCount == 0 && it.value.recursiveAudioCount == 0 && !it.value.isFlattened }
    }
}
