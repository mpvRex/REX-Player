package xyz.mpv.rex.utils.storage

import android.util.Log
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.database.entities.FolderAggregateEntity
import java.io.File

/**
 * Flattened, in-memory view of the persisted folder aggregates.
 *
 * This is the replacement for the old whole-library `Map<String, FolderNode>`. It carries no
 * retention information, only numeric facts already folded once per rebuild, so building it is
 * arithmetic over rows in memory rather than a provider query.
 */
internal class LibrarySnapshot(
    val nodes: Map<String, TreeNode>,
    private val isStorageRoot: (String) -> Boolean,
) {
    /** Paths of folders that add nothing to navigation and are promoted past on the way down. */
    val flattenedPaths: MutableSet<String> = mutableSetOf()

    private val childrenByParent: Map<String, List<TreeNode>> =
        nodes.values.groupBy { it.parentPath }

    fun node(path: String): TreeNode? = nodes[folderKey(path)]

    fun childrenOf(parentPath: String): List<TreeNode> = childrenByParent[folderKey(parentPath)].orEmpty()

    /**
     * Direct children with flattened folders collapsed out of the way.
     *
     * A folder is only ever collapsed when exactly one of its children bears media, so this can
     * never promote a sibling into the place of a folder that is merely empty or unindexed.
     *
     * Collapsing is **disabled for [parentPath] itself**: in a drill-down tree the folder's own row
     * is the only way to reach what is below it, so replacing it with a grandchild removes the
     * only tap target. That is what made the tree appear empty (the root's top-level folders were
     * consumed, leaving only media-less leaves, which the listing then filters out).
     * `flattenForNavigation = false` disables the promotion entirely.
     */
    fun effectiveChildren(
        parentPath: String,
        flattenForNavigation: Boolean = true,
    ): List<TreeNode> {
        val direct = childrenOf(parentPath)
        if (!flattenForNavigation) return direct
        if (direct.none { it.path in flattenedPaths }) return direct
        val result = mutableListOf<TreeNode>()
        for (child in direct) {
            if (child.path in flattenedPaths) {
                result += effectiveChildren(child.path, flattenForNavigation = false)
            } else {
                result += child
            }
        }
        return result
    }

    /**
     * Marks folders that add nothing to navigation: no direct media and only one media-bearing
     * child. Storage roots are never collapsed.
     */
    fun computeFlattening(showAudioFiles: Boolean) {
        flattenedPaths.clear()
        for (node in nodes.values) {
            val hasDirectMedia = if (showAudioFiles) {
                node.directVideoCount > 0 || node.directAudioCount > 0
            } else {
                node.directVideoCount > 0
            }
            if (hasDirectMedia) continue

            val childrenWithMedia = childrenByParent[node.path].orEmpty().count { child ->
                if (showAudioFiles) {
                    child.recursiveVideoCount > 0 || child.recursiveAudioCount > 0
                } else {
                    child.recursiveVideoCount > 0
                }
            }
            if (childrenWithMedia == 1 && !isStorageRoot(node.path)) {
                flattenedPaths += node.path
            }
        }
    }

    fun toMediaFolder(node: TreeNode, recursive: Boolean): MediaFolder =
        if (recursive) {
            MediaFolder(
                id = node.path,
                name = node.name,
                path = node.path,
                videoCount = node.recursiveVideoCount,
                audioCount = node.recursiveAudioCount,
                totalSize = node.recursiveSize,
                totalDuration = node.recursiveDuration,
                videoSize = node.recursiveVideoSize,
                audioSize = node.recursiveAudioSize,
                videoDuration = node.recursiveVideoDuration,
                audioDuration = node.recursiveAudioDuration,
                lastModified = node.latestModified,
                hasSubfolders = node.hasSubfolders,
                isRecursive = true,
                newCount = node.recursiveNewCount,
                unwatchedVideoCount = node.recursiveUnwatchedCount,
            )
        } else {
            MediaFolder(
                id = node.path,
                name = node.name,
                path = node.path,
                videoCount = node.directVideoCount,
                audioCount = node.directAudioCount,
                totalSize = node.directSize,
                totalDuration = node.directDuration,
                videoSize = node.directVideoSize,
                audioSize = node.directAudioSize,
                videoDuration = node.directVideoDuration,
                audioDuration = node.directAudioDuration,
                lastModified = node.lastModified,
                hasSubfolders = node.hasSubfolders,
                isRecursive = false,
                newCount = node.directNewCount,
                unwatchedVideoCount = node.directUnwatchedCount,
            )
        }
}

/** One folder in [LibrarySnapshot]; carries direct and folded (recursive) totals. */
internal class TreeNode(
    val path: String,
    val name: String,
    val parentPath: String,
    val directVideoCount: Int,
    val directAudioCount: Int,
    val directNewCount: Int,
    val directUnwatchedCount: Int,
    val directVideoSize: Long,
    val directAudioSize: Long,
    val directVideoDuration: Long,
    val directAudioDuration: Long,
    val lastModified: Long,
    val fromFileSystem: Boolean,
) {
    var hasSubfolders: Boolean = false

    var recursiveVideoCount: Int = directVideoCount
    var recursiveAudioCount: Int = directAudioCount
    var recursiveNewCount: Int = directNewCount
    var recursiveUnwatchedCount: Int = directUnwatchedCount
    var recursiveVideoSize: Long = directVideoSize
    var recursiveAudioSize: Long = directAudioSize
    var recursiveVideoDuration: Long = directVideoDuration
    var recursiveAudioDuration: Long = directAudioDuration
    var latestModified: Long = lastModified

    val directSize: Long get() = directVideoSize + directAudioSize
    val directDuration: Long get() = directVideoDuration + directAudioDuration
    val recursiveSize: Long get() = recursiveVideoSize + recursiveAudioSize
    val recursiveDuration: Long get() = recursiveVideoDuration + recursiveAudioDuration

    companion object {
        fun from(row: FolderAggregateEntity): TreeNode = TreeNode(
            path = row.path,
            name = row.name,
            parentPath = row.parentPath,
            directVideoCount = row.directVideoCount,
            directAudioCount = row.directAudioCount,
            directNewCount = row.directNewCount,
            directUnwatchedCount = row.directUnwatchedCount,
            directVideoSize = row.directVideoSize,
            directAudioSize = row.directAudioSize,
            directVideoDuration = row.directVideoDuration,
            directAudioDuration = row.directAudioDuration,
            lastModified = row.lastModified,
            fromFileSystem = row.fromFileSystem,
        )
    }
}
