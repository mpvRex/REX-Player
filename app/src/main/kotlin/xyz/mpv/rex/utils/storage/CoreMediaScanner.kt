package xyz.mpv.rex.utils.storage

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import xyz.mpv.rex.database.entities.FolderAggregateEntity
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.utils.media.PlaybackStateIndex
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

    /** Safety limit for indexing a branch; media trees are far shallower than this. */
    private const val MAX_INDEX_DEPTH = 12
    
    private val store: MediaTreeStore
        get() = org.koin.core.context.GlobalContext.get().get()

    /**
     * Invalidates the persisted directory aggregates.
     *
     * This marks stored rows untrustworthy rather than deleting them: they are dropped lazily
     * when the directory they describe is next read, so a playback-state change no longer forces
     * a whole-library re-list.
     */
    fun clearCache() {
        Log.d(TAG, "Invalidating folder aggregates")
        runCatching { store.invalidate() }
    }
    
    /**
     * Indexes every directory that holds media.
     *
     * Only the callers that genuinely need a library-wide answer (album view, storage roots) use
     * this. The tree browser never does: opening a folder indexes that folder and nothing else.
     * The pass is skipped entirely when the stored rows are still valid for this generation.
     */
    private suspend fun ensureLibraryIndexed(
        context: Context,
        treeStore: MediaTreeStore,
        playbackStates: List<PlaybackStateEntity>,
        thresholdDays: Int,
        blacklistedFolders: Set<String>,
    ) {
        val browserPreferences = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
        val valid = !treeStore.storageLayoutChanged() &&
            treeStore.allRowsCurrent() &&
            treeStore.snapshot(context, flattenFolders = false, showAudioFiles = false)
                .nodes.values.any { it.directVideoCount > 0 || it.directAudioCount > 0 }
        if (valid) return

        val scan = scanMediaStore(context) ?: walkVolumes(context)
        persistRows(context, treeStore, scan, playbackStates, thresholdDays, browserPreferences, blacklistedFolders)
    }

    private suspend fun persistRows(
        context: Context,
        treeStore: MediaTreeStore,
        folders: Map<String, FolderMedia>,
        playbackStates: List<PlaybackStateEntity>,
        thresholdDays: Int,
        browserPreferences: BrowserPreferences,
        blacklistedFolders: Set<String>,
    ) {
        val currentTime = System.currentTimeMillis()
        val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
        val watchedThreshold = browserPreferences.watchedThreshold.get()
        val showAudioFiles = browserPreferences.showAudioFiles.get()
        val policy = MediaScanPolicy(includeNoMediaContent = browserPreferences.includeNoMediaContent.get())
        val playbackIndex = PlaybackStateIndex.of(playbackStates)

        val rows = ArrayList<FolderAggregateEntity>(folders.size)
        for ((path, folder) in folders) {
            currentCoroutineContext().ensureActive()
            if (path in blacklistedFolders) continue
            if (!policy.includeNoMediaContent && FileFilterUtils.isWithinNoMediaBoundary(File(path))) continue
            val stats = folder.items.toDirectStats(
                showAudioFiles = showAudioFiles,
                currentTime = currentTime,
                thresholdMillis = thresholdMillis,
                watchedThreshold = watchedThreshold,
                playbackIndex = playbackIndex,
            )
            rows += stats.toRow(
                path = path,
                name = folder.name,
                parentPath = folder.parentPath,
                fromFileSystem = folder.fromFileSystem,
            )
        }
        treeStore.replaceAll(rows)
        treeStore.markRebuilt()
    }

    /**
     * One library-wide provider pass. Used only by [ensureLibraryIndexed]; the tree path never
     * calls it.
     */
    private fun scanMediaStore(context: Context): Map<String, FolderMedia>? {
        val result = LinkedHashMap<String, FolderMedia>()
        var failed = false
        var succeeded = false
        val collections = listOfNotNull(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI?.let { it to false },
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI?.let { it to true },
        )
        if (collections.isEmpty()) return null
        for ((uri, isAudio) in collections) {
            if (queryCollectionInto(context, uri, isAudio, result)) succeeded = true else failed = true
        }
        if (!succeeded) return null
        if (failed) Log.w(TAG, "MediaStore snapshot is partial; filesystem rows will reconcile it")
        return result
    }

    private fun queryCollectionInto(
        context: Context,
        uri: android.net.Uri,
        isAudio: Boolean,
        target: MutableMap<String, FolderMedia>,
    ): Boolean = try {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
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
                val parent = file.parentFile ?: continue
                val folderPath = folderKey(normalizedPath(parent))
                val item = ScannedItem(
                    name = cursor.getString(nameIdx) ?: file.name,
                    path = path,
                    size = cursor.getLong(sizeIdx),
                    duration = cursor.getLong(durationIdx),
                    dateModified = cursor.getLong(dateIdx),
                    isAudio = isAudio,
                )
                val existing = target[folderPath]
                target[folderPath] = FolderMedia(
                    path = folderPath,
                    name = parent.name,
                    parentPath = parentKey(folderPath),
                    items = (existing?.items ?: emptyList()) + item,
                    fromFileSystem = existing?.fromFileSystem ?: false,
                )
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "MediaStore query error", e)
        false
    }

    /** Last-resort walk of every readable volume when the provider cannot answer. */
    private fun walkVolumes(context: Context): Map<String, FolderMedia> {
        val result = LinkedHashMap<String, FolderMedia>()
        val volumes = runCatching { StorageVolumeUtils.getAllStorageVolumes(context) }.getOrNull().orEmpty()
        for (volume in volumes) {
            val path = StorageVolumeUtils.getVolumePath(volume) ?: continue
            val root = File(path)
            if (!root.exists() || !root.canRead()) continue
            walkInto(root, result, depth = 0)
        }
        return result
    }

    private fun walkInto(directory: File, target: MutableMap<String, FolderMedia>, depth: Int) {
        if (depth > 20) return
        val entries = directory.listFiles() ?: return
        val items = entries
            .filter { it.isFile && !FileFilterUtils.shouldSkipFile(it) }
            .filter { FileTypeUtils.isVideoFile(it) || FileTypeUtils.isAudioFile(it) }
            .map { file ->
                ScannedItem(
                    name = file.name,
                    path = normalizedPath(file),
                    size = file.length(),
                    duration = 0L,
                    dateModified = file.lastModified() / 1000,
                    isAudio = FileTypeUtils.isAudioFile(file),
                )
            }
        if (items.isNotEmpty()) {
            val key = folderKey(directory.absolutePath)
            target[key] = FolderMedia(
                path = key,
                name = directory.name,
                parentPath = parentKey(key),
                items = items,
                fromFileSystem = true,
            )
        }
        for (child in entries) {
            if (child.isDirectory && !FileFilterUtils.shouldSkipFolder(child)) {
                walkInto(child, target, depth + 1)
            }
        }
    }

    /**
     * Get all folders that contain media files (flat list for Album View)
     */
    suspend fun getFlatMediaFolders(
        context: Context,
        playbackStates: List<PlaybackStateEntity> = emptyList(),
        thresholdDays: Int = 7,
        blacklistedFolders: Set<String> = emptySet()
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val browserPreferences = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
        val treeStore = store
        // This listing covers the whole library by definition (the album view lists every
        // folder), so it is the one caller that indexes broadly. Kept out of the tree path.
        ensureLibraryIndexed(
            context = context,
            treeStore = treeStore,
            playbackStates = playbackStates,
            thresholdDays = thresholdDays,
            blacklistedFolders = blacklistedFolders,
        )
        val snapshot = treeStore.snapshot(
            context = context,
            flattenFolders = browserPreferences.flattenFolders.get(),
            showAudioFiles = browserPreferences.showAudioFiles.get(),
        )

        snapshot.nodes.values
            .filter { it.directVideoCount > 0 || it.directAudioCount > 0 }
            .map { snapshot.toMediaFolder(it, recursive = false) }
            .sortedBy { lowerName(it.name) }
    }

    /**
     * Get immediate children of a parent path (for Tree/Filesystem View)
     *
     * Only the directories involved in this listing are read or re-listed. The previously
     * required whole-library tree build is gone; recursive totals come from the persisted
     * aggregates folded in memory.
     */
    suspend fun getFoldersInDirectory(
        context: Context,
        parentPath: String,
        playbackStates: List<PlaybackStateEntity> = emptyList(),
        thresholdDays: Int = 7,
        blacklistedFolders: Set<String> = emptySet(),
        policy: MediaScanPolicy = MediaScanPolicy(),
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val browserPreferences = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
        val treeStore = store
        val parent = File(parentPath)
        if (!parent.exists() || !parent.isDirectory) return@withContext emptyList()

        val currentTime = System.currentTimeMillis()
        val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
        val watchedThreshold = browserPreferences.watchedThreshold.get()
        val showAudioFiles = browserPreferences.showAudioFiles.get()
        val playbackIndex = PlaybackStateIndex.of(playbackStates)

        ensureDirectoryIndexed(
            context = context,
            directory = parent,
            treeStore = treeStore,
            policy = policy,
            blacklistedFolders = blacklistedFolders,
            showAudioFiles = showAudioFiles,
            currentTime = currentTime,
            thresholdMillis = thresholdMillis,
            watchedThreshold = watchedThreshold,
            playbackIndex = playbackIndex,
        )

        val snapshot = treeStore.snapshot(
            context = context,
            flattenFolders = browserPreferences.flattenFolders.get(),
            showAudioFiles = showAudioFiles,
        )

        val foldersByPath = snapshot
            .effectiveChildren(folderKey(parentPath))
            .filter { node ->
                policy.includeNoMediaContent || !FileFilterUtils.isWithinNoMediaBoundary(File(node.path))
            }
            .associate { node -> node.path to snapshot.toMediaFolder(node, recursive = true) }
            .toMutableMap()

        if (policy.includeNoMediaContent) {
            discoverDirectNoMediaFolders(parentPath, blacklistedFolders, policy)
                .forEach { foldersByPath[it.path] = it }
        }

        foldersByPath.values.sortedBy { lowerName(it.name) }
    }

    /**
     * Guarantees that [directory]'s branch is indexed deep enough for the tree to render it.
     *
     * MediaStore is asked only about [directory] itself, which is the listing about to be shown.
     * Directories below it are materialised as anchors (name + parent only) so flattening can
     * walk the branch, and their own media is filled in the first time they are opened. Nothing
     * here scales with the size of the library.
     */
    private suspend fun ensureDirectoryIndexed(
        context: Context,
        directory: File,
        treeStore: MediaTreeStore,
        policy: MediaScanPolicy,
        blacklistedFolders: Set<String>,
        showAudioFiles: Boolean,
        currentTime: Long,
        thresholdMillis: Long,
        watchedThreshold: Int,
        playbackIndex: PlaybackStateIndex,
    ) {
        val root = folderKey(directory.absolutePath)
        ensureAnchor(treeStore, directory, root, isRoot = true)

        // Every recursive total shown for a child covers its whole subtree, so the listing is
        // only correct once the branch below has been indexed down to the leaves. This is
        // bounded by the subtree being opened, not by the library — opening Download never
        // looks at Movies.
        val queue = ArrayDeque<File>().apply { add(directory) }
        val visited = HashSet<String>()
        var levels = 0

        while (queue.isNotEmpty() && levels <= MAX_INDEX_DEPTH) {
            currentCoroutineContext().ensureActive()
            val levelSize = queue.size
            val rows = ArrayList<FolderAggregateEntity>()
            for (i in 0 until levelSize) {
                val parent = queue.removeFirst()
                val parentPath = folderKey(parent.absolutePath)
                if (!visited.add(parentPath)) continue

                val children = parent.listFiles()
                    ?.filter { it.isDirectory }
                    ?.filterNot { it.absolutePath in blacklistedFolders }
                    ?.filterNot { FileFilterUtils.shouldSkipFolder(it, policy) }
                    .orEmpty()

                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val childPath = folderKey(child.absolutePath)
                    rows += buildRow(
                        context = context,
                        child = child,
                        childPath = childPath,
                        parentPath = parentPath,
                        policy = policy,
                        showAudioFiles = showAudioFiles,
                        currentTime = currentTime,
                        thresholdMillis = thresholdMillis,
                        watchedThreshold = watchedThreshold,
                        playbackIndex = playbackIndex,
                    )
                    queue.add(child)
                }
            }
            if (rows.isNotEmpty()) treeStore.upsert(rows)
            levels++
        }
    }

    /**
     * Aggregates one directory from the provider, reconciling with its own listing so files the
     * provider has not indexed yet still count.
     */
    private suspend fun buildRow(
        context: Context,
        child: File,
        childPath: String,
        parentPath: String,
        policy: MediaScanPolicy,
        showAudioFiles: Boolean,
        currentTime: Long,
        thresholdMillis: Long,
        watchedThreshold: Int,
        playbackIndex: PlaybackStateIndex,
    ): FolderAggregateEntity {
        val providerRows = MediaStoreDirectorySource.read(context, child)
        val items = when {
            providerRows == null -> listDirectMedia(child, policy).orEmpty()
            else -> {
                val merged = providerRows.toMutableList()
                val known = merged.mapTo(HashSet()) { it.path }
                listDirectMedia(child, policy)
                    ?.filterNot { it.path in known }
                    ?.let(merged::addAll)
                merged
            }
        }
        val stats = items.toDirectStats(
            showAudioFiles = showAudioFiles,
            currentTime = currentTime,
            thresholdMillis = thresholdMillis,
            watchedThreshold = watchedThreshold,
            playbackIndex = playbackIndex,
        )
        return stats.toRow(
            path = childPath,
            name = child.name,
            parentPath = parentPath,
            fromFileSystem = providerRows == null,
        )
    }

    /**
     * Discovers only immediate children of the active directory that live behind a `.nomedia`
     * boundary. Deliberately separate from the indexed aggregates: those directories are
     * excluded from the scan, so they have to be listed on demand when the user opts in.
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
            if (FileFilterUtils.shouldSkipFolder(child, MediaScanPolicy(includeNoMediaContent = true))) continue
            if (!parentIsNoMedia && !FileFilterUtils.hasNoMediaFile(child)) continue

            val entries = child.listFiles() ?: continue
            var videoCount = 0
            var audioCount = 0
            var videoSize = 0L
            var audioSize = 0L
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

                if (isAudio) {
                    audioCount++
                    audioSize += entry.length()
                } else {
                    videoCount++
                    videoSize += entry.length()
                }
                latestModified = maxOf(latestModified, entry.lastModified() / 1000)
            }

            if (videoCount > 0 || audioCount > 0 || hasSubfolders) {
                val normalized = runCatching { child.canonicalPath }.getOrElse { child.absoluteFile.normalize().path }
                result += MediaFolder(
                    id = normalized,
                    name = child.name,
                    path = normalized,
                    videoCount = videoCount,
                    audioCount = audioCount,
                    totalSize = videoSize + audioSize,
                    totalDuration = 0,
                    videoSize = videoSize,
                    audioSize = audioSize,
                    videoDuration = 0,
                    audioDuration = 0,
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

    /** Makes sure a row exists for [directory] so folded children have somewhere to hang. */
    private suspend fun ensureAnchor(
        treeStore: MediaTreeStore,
        directory: File,
        path: String,
        isRoot: Boolean,
    ) {
        if (treeStore.get(path) != null) return
        treeStore.upsert(
            listOf(
                FolderAggregateEntity(
                    path = path,
                    parentPath = parentKey(path),
                    name = directory.name,
                    lastModified = directory.lastModified() / 1000,
                    isAnchor = isRoot,
                ),
            ),
        )
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
        val browserPreferences = org.koin.core.context.GlobalContext.get().get<BrowserPreferences>()
        val treeStore = store
        // Storage roots expand to every volume, so this listing is library-wide by nature.
        ensureLibraryIndexed(
            context = context,
            treeStore = treeStore,
            playbackStates = playbackStates,
            thresholdDays = thresholdDays,
            blacklistedFolders = blacklistedFolders,
        )
        val snapshot = treeStore.snapshot(
            context = context,
            flattenFolders = browserPreferences.flattenFolders.get(),
            showAudioFiles = browserPreferences.showAudioFiles.get(),
        )
        snapshot.node(path)?.let { snapshot.toMediaFolder(it, recursive = true) }
    }
}
