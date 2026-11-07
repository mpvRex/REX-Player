package xyz.mpv.rex.utils.storage

import org.junit.Assert.*
import org.junit.Test

class FolderPathsTest {
  @Test
  fun descendantSelectionMarksEveryAncestorButNotSiblingsOrStorage() {
    val root = "/storage/emulated/0"
    val selected = setOf("$root/Family/Trips/Coast")
    assertEquals(ExclusionSelectionState.SELECTED, exclusionSelectionState("$root/Family/Trips/Coast", selected))
    assertEquals(ExclusionSelectionState.PARTIAL, exclusionSelectionState("$root/Family/Trips", selected))
    assertEquals(ExclusionSelectionState.PARTIAL, exclusionSelectionState("$root/Family", selected))
    assertEquals(ExclusionSelectionState.NONE, exclusionSelectionState("$root/Family/Trips/Mountains", selected))
    assertEquals(ExclusionSelectionState.NONE, exclusionSelectionState("$root/Family2", selected))
    assertEquals(ExclusionSelectionState.NONE, exclusionSelectionState(root, selected))
    assertEquals(ExclusionSelectionState.INHERITED, exclusionSelectionState("$root/Family/Trips/Coast/More", selected))
  }

  @Test
  fun selectingAndClearingParentPreservesTheNarrowerSelection() {
    val parent = "/storage/emulated/0/Family"
    val child = "$parent/Trips/Coast"
    val withParent = setOf(parent, child)
    assertEquals(ExclusionSelectionState.SELECTED, exclusionSelectionState(parent, withParent))
    assertEquals(ExclusionSelectionState.INHERITED, exclusionSelectionState(child, withParent))
    assertEquals(setOf(parent), minimalExclusionSelection(withParent))
    assertEquals(ExclusionSelectionState.PARTIAL, exclusionSelectionState(parent, withParent - parent))
    assertEquals(ExclusionSelectionState.SELECTED, exclusionSelectionState(child, withParent - parent))
    assertEquals(ExclusionSelectionState.NONE, exclusionSelectionState(parent, emptySet()))
  }

  @Test
  fun selectingAllKnownChildrenStillLeavesParentPartialRatherThanExcludingItsOwnMedia() {
    val parent = "/storage/emulated/0/Family"
    val nodes = exclusionFolderHierarchy(listOf("$parent/Trips", "$parent/Celebrations"))
    val selected = setOf("$parent/Trips", "$parent/Celebrations")
    assertEquals(ExclusionSelectionState.PARTIAL, exclusionSelectionState(parent, selected))
    val collapsed = visibleExclusionFolders(nodes, setOf("/storage/emulated/0"))
    assertTrue(collapsed.any { it.path == parent })
    assertFalse(collapsed.any { it.path in selected })
    assertEquals(selected, minimalExclusionSelection(selected))
  }

  @Test
  fun storageVolumesAreHeadersAndCannotBecomeExclusions() {
    assertTrue(isExclusionStorageRoot("/storage/emulated/0"))
    assertTrue(isExclusionStorageRoot("/storage/emulated/10/"))
    assertTrue(isExclusionStorageRoot("/sdcard"))
    assertTrue(isExclusionStorageRoot("/storage/1234-ABCD"))
    assertTrue(isExclusionStorageRoot("content://com.android.externalstorage.documents/tree/primary%3A"))
    assertFalse(isExclusionStorageRoot("/storage/emulated/0/Movies"))
    assertFalse(isExclusionStorageRoot("/storage/1234-ABCD/Movies"))
    assertEquals(
      setOf("/storage/emulated/0/Movies"),
      minimalExclusionSelection(setOf("/storage/emulated/0", "/storage/emulated/0/Movies", "/storage/emulated/0/Movies/Child")),
    )
  }

  @Test
  fun aSelectedParentCountsOnceWhileSiblingsStayIndependent() {
    val root = "/storage/emulated/0"
    assertEquals(
      setOf("$root/DCIM", "$root/Download"),
      minimalExclusionSelection(setOf("$root/DCIM", "$root/DCIM/Camera", "$root/Download")),
    )
    assertEquals(
      setOf("$root/DCIM/Camera", "$root/Download"),
      minimalExclusionSelection(setOf("$root/DCIM/Camera", "$root/Download")),
    )
  }

  @Test
  fun hierarchyIncludesParentsWithoutDirectMedia() {
    val root = "/storage/emulated/0"
    val nodes = exclusionFolderHierarchy(listOf("$root/Big/A", "$root/Big/B/Nested"))
    assertEquals(root, nodes.single { it.path == "$root/Big" }.parent)
    assertEquals("$root/Big", nodes.single { it.path == "$root/Big/A" }.parent)
    assertEquals("$root/Big/B", nodes.single { it.path == "$root/Big/B/Nested" }.parent)
    assertNull(nodes.single { it.path == root }.parent)
    assertFalse(nodes.any { it.path == "/storage/emulated" })
  }

  @Test
  fun expandedChildrenStayWithTheirParentBeforeSimilarlyNamedSiblings() {
    val root = "/storage/emulated/0"
    val nodes = exclusionFolderHierarchy(listOf("$root/A/Child", "$root/A Other"))
    assertEquals(listOf(root), visibleExclusionFolders(nodes, emptySet()).map { it.path })
    assertEquals(
      listOf(root, "$root/A", "$root/A/Child", "$root/A Other"),
      visibleExclusionFolders(nodes, setOf(root, "$root/A")).map { it.path },
    )
  }

  @Test
  fun systemPickerExclusionMatchesFilesystemAndSafDescendants() {
    val uri = "content://com.android.externalstorage.documents/tree/primary%3ABig"
    val child = "$uri/document/primary%3ABig%2FA%2Fclip.mp4"
    val exclusions = setOf(uri)
    assertEquals("/storage/emulated/0/Big", selectedFolderPath(uri))
    assertTrue(isPathExcluded("/storage/emulated/0/Big/A/clip.mp4", exclusions))
    assertTrue(isPathExcluded(child, exclusions))
    assertTrue(isPathExcluded("/sdcard/Big/A", exclusions))
    assertFalse(isPathExcluded("/storage/emulated/0/Big2/A", exclusions))
    assertFalse(isPathExcluded("/storage/emulated/0/Big/A", setOf("")))
  }

  @Test
  fun sdCardAndSpecialCharactersKeepTheirPathBoundaries() {
    val root = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AA%2BB%20%25"
    assertEquals("/storage/1234-ABCD/A+B %", selectedFolderPath(root))
    assertTrue(isPathExcluded("/storage/1234-ABCD/A+B %/child", setOf(root)))
    assertFalse(isPathExcluded("/storage/1234-ABCD/A+B %extra/child", setOf(root)))
    assertFalse(isPathExcluded("/storage/emulated/0/A+B %/child", setOf(root)))
  }


}
