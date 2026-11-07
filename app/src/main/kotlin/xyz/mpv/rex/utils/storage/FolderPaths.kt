package xyz.mpv.rex.utils.storage

import java.io.File

internal data class ExclusionFolder(val path: String, val parent: String?, val name: String)

/** Storage volumes are navigation sections, never exclusion targets. */
internal fun isExclusionStorageRoot(path: String): Boolean {
    val normalized = comparableFolderPath(path)
    return Regex("^/storage/emulated/[^/]+$").matches(normalized) ||
        (File(normalized).parent == "/storage" && File(normalized).name != "emulated")
}

/** One selected parent represents its whole branch, without exposing volume-wide exclusions. */
internal fun minimalExclusionSelection(paths: Set<String>): Set<String> {
    val selectable = paths.filterNot(::isExclusionStorageRoot).toSet()
    return selectable.filterNot { isPathExcluded(it, selectable - it) }.toSet()
}

internal enum class ExclusionSelectionState { NONE, SELECTED, INHERITED, PARTIAL }

/** Partial selection means selections exist below this folder, even while collapsed. */
internal fun exclusionSelectionState(path: String, selected: Set<String>): ExclusionSelectionState {
    if (isExclusionStorageRoot(path)) return ExclusionSelectionState.NONE
    val key = comparableFolderPath(path)
    val ancestors = selected.filterNot { comparableFolderPath(it) == key }.toSet()
    return when {
        isPathExcluded(path, ancestors) -> ExclusionSelectionState.INHERITED
        selected.any { comparableFolderPath(it) == key } -> ExclusionSelectionState.SELECTED
        selected.any { isPathExcluded(it, setOf(path)) } -> ExclusionSelectionState.PARTIAL
        else -> ExclusionSelectionState.NONE
    }
}

/** Include intermediate parents even when only their descendants contain media. */
internal fun exclusionFolderHierarchy(paths: List<String>): List<ExclusionFolder> {
    val nodes = linkedMapOf<String, ExclusionFolder>()
    for (value in paths) {
        var path = selectedFolderPath(value)
        if (path.startsWith("content://")) {
            nodes[path] = ExclusionFolder(path, null, path.substringAfterLast('/'))
            continue
        }
        val root = Regex("^/storage/emulated/[^/]+|^/storage/[^/]+").find(path)?.value
        while (path.isNotEmpty() && path != "/") {
            val parent = if (path == root) null else File(path).parent?.takeUnless { it == "/" }
            nodes[path] = ExclusionFolder(path, parent, File(path).name)
            path = parent ?: break
        }
    }
    return nodes.values.sortedBy { it.path.lowercase() }
}

internal fun visibleExclusionFolders(nodes: List<ExclusionFolder>, expanded: Set<String>): List<ExclusionFolder> {
    val paths = nodes.mapTo(hashSetOf()) { it.path }
    val children = nodes.groupBy { it.parent }
    val result = mutableListOf<ExclusionFolder>()
    fun append(node: ExclusionFolder) {
        result += node
        if (node.path in expanded) {
            children[node.path].orEmpty().sortedBy { it.name.lowercase() }.forEach(::append)
        }
    }
    nodes.filter { it.parent !in paths }.sortedBy { it.name.lowercase() }.forEach(::append)
    return result
}
