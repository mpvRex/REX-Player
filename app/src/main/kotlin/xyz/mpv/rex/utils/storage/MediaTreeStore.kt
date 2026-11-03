package xyz.mpv.rex.utils.storage

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import xyz.mpv.rex.database.entities.FolderAggregateEntity
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.Locale

/**
 * The tree browser's directory index.
 *
 * ## Why this exists
 *
 * The tree used to be rendered by querying MediaStore for the whole library and building a map
 * of every folder, then caching that map for three minutes. Rendering a single directory
 * therefore cost O(library), and any cache miss (TTL expiry, preference change, playback state
 * change, manual refresh) paid it again. Aggregates are now stored one row per directory
 * holding that directory's *own* media only, so opening a folder reads, and only when
 * necessary re-lists, the directories involved rather than the whole library.
 *
 * ## What is trusted
 *
 * Nothing is taken on faith. Every row records the media-scanner generation it was captured
 * under; that generation is bumped for each `MEDIA_SCANNER_FINISHED`/`SCAN_FILE` broadcast (see
 * [xyz.mpv.rex.utils.media.MediaScanReceiver]), and a row from any other generation is refused
 * and re-listed. Stored rows are therefore never displayed after a change the provider has
 * announced.
 */
internal class MediaTreeStore(
    context: Context,
    private val repository: FolderAggregateSource,
) {
    private val appContext = context.applicationContext
    private val generationPrefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var cachedSnapshot: LibrarySnapshot? = null
    private var cachedSnapshotKey: String? = null

    // ------------------------------------------------------------------ generation

    fun generation(): Long = generationPrefs.getLong(KEY_GENERATION, 0L)

    /** Declares every stored row untrustworthy. Rows are dropped lazily by readers. */
    fun invalidate() {
        generationPrefs.edit().putLong(KEY_GENERATION, generation() + 1).apply()
        cachedSnapshot = null
        cachedSnapshotKey = null
    }

    // ------------------------------------------------------------------ reading

    /**
     * The folded, optionally flattened library view used for `getEffectiveChildren`.
     *
     * Folding is arithmetic over rows already in memory and flattens the branch structure the
     * tree needs. It is the only whole-index operation on the read path, and unlike the old
     * rebuild it does not touch the provider.
     */
    suspend fun snapshot(
        context: Context,
        flattenFolders: Boolean,
        showAudioFiles: Boolean,
    ): LibrarySnapshot = withContext(Dispatchers.IO) {
        val key = "${generation()}|$flattenFolders|$showAudioFiles"
        cachedSnapshot?.let { if (cachedSnapshotKey == key) return@withContext it }

        val rows = repository.loadAll()
        val nodes = LinkedHashMap<String, TreeNode>(rows.size * 2)
        for (row in rows) {
            nodes[row.path] = TreeNode.from(row)
        }

        // Fold deepest-first so every child is final before it contributes to its parent.
        for (path in nodes.keys.sortedByDescending { it.length }) {
            val node = nodes[path] ?: continue
            val parent = nodes[node.parentPath] ?: continue
            parent.hasSubfolders = true
            parent.recursiveVideoCount += node.recursiveVideoCount
            parent.recursiveAudioCount += node.recursiveAudioCount
            parent.recursiveNewCount += node.recursiveNewCount
            parent.recursiveUnwatchedCount += node.recursiveUnwatchedCount
            parent.recursiveVideoSize += node.recursiveVideoSize
            parent.recursiveAudioSize += node.recursiveAudioSize
            parent.recursiveVideoDuration += node.recursiveVideoDuration
            parent.recursiveAudioDuration += node.recursiveAudioDuration
            if (node.latestModified > parent.latestModified) {
                parent.latestModified = node.latestModified
            }
        }

        val snapshot = LibrarySnapshot(nodes) { path -> StorageVolumeUtils.isStorageRoot(context, path) }
        if (flattenFolders) snapshot.computeFlattening(showAudioFiles)
        cachedSnapshot = snapshot
        cachedSnapshotKey = key
        snapshot
    }

    /**
     * Stored rows for the direct children of [directory], dropping any that this generation
     * can no longer justify so they are re-listed on demand.
     */
    suspend fun storedChildren(directory: File, policy: MediaScanPolicy): List<FolderAggregateEntity> =
        withContext(Dispatchers.IO) {
            val stored = repository.getChildren(directory.absolutePath)
            if (stored.isEmpty()) return@withContext emptyList()

            val current = generation()
            val survivors = ArrayList<FolderAggregateEntity>(stored.size)
            val stale = ArrayList<String>()
            for (row in stored) {
                if (row.indexedGeneration != current) {
                    stale += row.path
                } else {
                    survivors += row
                }
            }
            if (stale.isNotEmpty()) {
                repository.delete(stale)
                cachedSnapshot = null
                Log.d(TAG, "Dropped ${stale.size} stale folder rows under ${directory.name}")
            }
            survivors
        }

    suspend fun get(path: String): FolderAggregateEntity? =
        withContext(Dispatchers.IO) { repository.getByPath(path) }

    suspend fun knownParents(): List<String> = withContext(Dispatchers.IO) {
        repository.parents()
    }

    /** True when every stored row belongs to the current media generation. */
    suspend fun allRowsCurrent(): Boolean = withContext(Dispatchers.IO) {
        val current = generation()
        repository.loadAll().all { it.indexedGeneration == current }
    }

    // ------------------------------------------------------------------ writing

    suspend fun upsert(rows: Collection<FolderAggregateEntity>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        repository.upsert(rows.map { it.copy(indexedGeneration = generation()) })
        cachedSnapshot = null
    }

    suspend fun delete(paths: Collection<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        repository.delete(paths)
        cachedSnapshot = null
    }

    /** Wipes everything, used when the storage layout no longer matches the index. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        repository.clear()
        cachedSnapshot = null
        cachedSnapshotKey = null
    }

    /** Replaces the whole index with a freshly scanned snapshot. */
    suspend fun replaceAll(rows: Collection<FolderAggregateEntity>) = withContext(Dispatchers.IO) {
        val current = generation()
        repository.clear()
        repository.upsert(rows.map { it.copy(indexedGeneration = current) })
        cachedSnapshot = null
        cachedSnapshotKey = null
    }

    fun markRebuilt() {
        generationPrefs.edit().putString(KEY_STORAGE_SIGNATURE, storageSignature()).apply()
        invalidate()
    }

    suspend fun storageLayoutChanged(): Boolean = withContext(Dispatchers.IO) {
        generationPrefs.getString(KEY_STORAGE_SIGNATURE, null) != storageSignature()
    }

    private fun storageSignature(): String =
        runCatching { StorageVolumeUtils.getExternalStorageVolumes(appContext) }
            .getOrNull()
            ?.mapNotNull { StorageVolumeUtils.getVolumePath(it) }
            ?.joinToString("|")
            ?: ""

    companion object {
        private const val TAG = "MediaTreeStore"
        private const val PREFS_NAME = "media_tree_store"
        private const val KEY_GENERATION = "generation"
        private const val KEY_STORAGE_SIGNATURE = "storage_signature"
    }
}

/**
 * Per-file accumulation shared by the MediaStore and filesystem paths so both produce identical
 * counts, sizes and watched/new flags.
 */
internal fun accumulateItem(
    stats: DirectStats,
    item: ScannedItem,
    showAudioFiles: Boolean,
    currentTime: Long,
    thresholdMillis: Long,
    watchedThreshold: Int,
    playbackIndex: xyz.mpv.rex.utils.media.PlaybackStateIndex,
) {
    if (item.dateModified > stats.latestModified) stats.latestModified = item.dateModified

    if (item.isAudio) {
        stats.audioCount++
        stats.audioSize += item.size
        stats.audioDuration += item.duration
    } else {
        stats.videoCount++
        stats.videoSize += item.size
        stats.videoDuration += item.duration
    }

    if (!showAudioFiles && item.isAudio) return

    val playbackState = playbackIndex.forPath(item.path) ?: playbackIndex.forName(item.name)
    var isWatched = false
    if (playbackState != null) {
        if (playbackState.hasBeenWatched) {
            isWatched = true
        } else if (item.duration > 0 && playbackState.timeRemaining != -1) {
            val durationSeconds = item.duration / 1000
            val watched = durationSeconds - playbackState.timeRemaining.toLong()
            val progress = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
            if (progress >= (watchedThreshold / 100f)) isWatched = true
        }
    }
    if (!isWatched) stats.unwatchedCount++

    val age = currentTime - (item.dateModified * 1000)
    val isNew =
        (playbackState == null && age <= thresholdMillis) ||
            (playbackState != null && playbackState.timeRemaining == -1)
    if (isNew) stats.newCount++
}

internal fun List<ScannedItem>.toDirectStats(
    showAudioFiles: Boolean,
    currentTime: Long,
    thresholdMillis: Long,
    watchedThreshold: Int,
    playbackIndex: xyz.mpv.rex.utils.media.PlaybackStateIndex,
): DirectStats {
    val stats = DirectStats()
    for (item in this) {
        accumulateItem(
            stats = stats,
            item = item,
            showAudioFiles = showAudioFiles,
            currentTime = currentTime,
            thresholdMillis = thresholdMillis,
            watchedThreshold = watchedThreshold,
            playbackIndex = playbackIndex,
        )
    }
    return stats
}

/** Converts accumulated stats into a storable row. */
internal fun DirectStats.toRow(
    path: String,
    name: String,
    parentPath: String,
    fromFileSystem: Boolean,
): FolderAggregateEntity = FolderAggregateEntity(
    path = folderKey(path),
    parentPath = parentPath,
    name = name,
    lastModified = latestModified,
    directVideoCount = videoCount,
    directAudioCount = audioCount,
    directNewCount = newCount,
    directUnwatchedCount = unwatchedCount,
    directVideoSize = videoSize,
    directAudioSize = audioSize,
    directVideoDuration = videoDuration,
    directAudioDuration = audioDuration,
    fromFileSystem = fromFileSystem,
)
