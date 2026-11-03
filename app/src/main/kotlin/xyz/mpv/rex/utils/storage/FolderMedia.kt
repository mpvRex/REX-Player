package xyz.mpv.rex.utils.storage

import java.io.File
import java.util.Locale

/**
 * Media files recorded for a single directory, plus where they came from.
 */
internal data class FolderMedia(
    val path: String,
    val name: String,
    val parentPath: String,
    val items: List<ScannedItem>,
    /**
     * True when the rows came from a direct filesystem listing rather than MediaStore. Such a
     * row is complete; a MediaStore row may still be missing files the provider has not indexed.
     */
    val fromFileSystem: Boolean,
)

/** Basic info for a single media item found during scan. */
internal data class ScannedItem(
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateModified: Long,
    val isAudio: Boolean = false,
)

/** Aggregated direct-media stats for a single folder. */
internal class DirectStats(
    var videoCount: Int = 0,
    var audioCount: Int = 0,
    var newCount: Int = 0,
    var unwatchedCount: Int = 0,
    var videoSize: Long = 0L,
    var audioSize: Long = 0L,
    var videoDuration: Long = 0L,
    var audioDuration: Long = 0L,
    var latestModified: Long = 0L,
)

/** Raw MediaStore rows after one query pass, keyed by parent folder. */
internal data class MediaStoreScan(
    val byFolder: Map<String, FolderMedia>,
    /** True if at least one provider query failed, so the snapshot may be partial. */
    val incomplete: Boolean,
)

internal fun folderKey(path: String): String =
    path.trimEnd(File.separatorChar).ifEmpty { File.separator }

/** Parent folder path, or an empty string for a volume/filesystem root. */
internal fun parentKey(path: String): String {
    val key = folderKey(path)
    val parent = File(key).parent ?: return ""
    val parentFolder = folderKey(parent)
    return if (parentFolder == key) "" else parentFolder
}

internal fun normalizedPath(file: File): String =
    runCatching { file.canonicalPath }.getOrElse { file.absoluteFile.normalize().path }

/**
 * Every path component leading to [path], roots first. MediaStore only indexes leaves, but the
 * tree needs a row for each intermediate directory so a branch stays walkable.
 */
internal fun ancestorKeys(path: String): List<String> {
    val result = mutableListOf<String>()
    var current = parentKey(path)
    while (current.isNotEmpty() && current != File.separator) {
        result.add(current)
        val next = parentKey(current)
        if (next == current) break
        current = next
    }
    return result.asReversed()
}

internal fun lowerName(name: String): String = name.lowercase(Locale.getDefault())

/**
 * Fallback resolver for rows created by an older index, or for a folder whose branch was never
 * materialised. Noise folders and `.nomedia` boundaries stay hidden so a filesystem listing
 * cannot resurrect what the scanner deliberately skipped.
 */
internal fun listDirectMedia(folder: File, policy: MediaScanPolicy): List<ScannedItem>? {
    val entries = folder.listFiles() ?: return null
    if (!policy.includeNoMediaContent && FileFilterUtils.hasNoMediaFile(folder)) return emptyList()

    val result = ArrayList<ScannedItem>(entries.size)
    for (entry in entries) {
        if (!entry.isFile || FileFilterUtils.shouldSkipFile(entry)) continue
        val isVideo = FileTypeUtils.isVideoFile(entry)
        val isAudio = FileTypeUtils.isAudioFile(entry)
        if (!isVideo && !isAudio) continue
        result += ScannedItem(
            name = entry.name,
            path = normalizedPath(entry),
            size = entry.length(),
            duration = 0L,
            dateModified = entry.lastModified() / 1000,
            isAudio = isAudio,
        )
    }
    return result
}
