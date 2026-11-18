package xyz.mpv.rex.utils.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFilterUtilsTest {
  @Test
  fun hiddenPathChecksAllAncestorsIncludingDocumentIds() {
    assertTrue(FileFilterUtils.isHiddenPath("/storage/emulated/0/.hidden/Child/film.mp4"))
    assertTrue(FileFilterUtils.isHiddenPath("/storage/emulated/0/Movies/.film.mp4"))
    assertTrue(FileFilterUtils.isHiddenPath("/storage/emulated/0/.hidden/film:part1.mp4"))
    assertTrue(FileFilterUtils.isHiddenPath("content://com.android.externalstorage.documents/tree/primary%3A.hidden/document/primary%3A.hidden%2FChild"))
    assertFalse(FileFilterUtils.isHiddenPath("/storage/emulated/0/Movies/film.mp4"))
  }

  @Test
  fun hiddenAndNoMediaOptionsAreIndependent() {
    withTempDirectory { root ->
      val hidden = File(root, ".private").apply { mkdirs() }
      val media = File(hidden, ".clip.mp4").apply { createNewFile() }
      val showHidden = MediaScanPolicy(showHiddenFiles = true)
      assertFalse(FileFilterUtils.shouldSkipFolder(hidden, showHidden))
      assertFalse(FileFilterUtils.shouldSkipFile(media, showHidden))
      assertTrue(FileFilterUtils.shouldSkipFile(media))
      File(hidden, ".nomedia").createNewFile()
      assertTrue(FileFilterUtils.shouldSkipFolder(hidden, showHidden))
      assertTrue(FileFilterUtils.shouldSkipFolder(hidden, MediaScanPolicy(includeNoMediaContent = true)))
      assertFalse(FileFilterUtils.shouldSkipFolder(hidden, showHidden.copy(includeNoMediaContent = true)))
      assertTrue(FileFilterUtils.shouldSkipFolderName(".cache", showHidden))
    }
  }

  @Test
  fun noMediaFolder_isControlledIndependentlyByPolicy() {
    withTempDirectory { root ->
      val folder = File(root, "Private").apply { mkdirs() }
      File(folder, ".nomedia").createNewFile()

      assertTrue(FileFilterUtils.shouldSkipFolder(folder))
      assertFalse(
        FileFilterUtils.shouldSkipFolder(
          folder,
          MediaScanPolicy(includeNoMediaContent = true),
        ),
      )
    }
  }

  @Test
  fun systemAndDotHiddenFolders_remainSkippedWhenNoMediaIsEnabled() {
    withTempDirectory { root ->
      val hidden = File(root, ".private").apply { mkdirs() }
      val cache = File(root, "cache").apply { mkdirs() }
      val policy = MediaScanPolicy(includeNoMediaContent = true)

      assertTrue(FileFilterUtils.shouldSkipFolder(hidden, policy))
      assertTrue(FileFilterUtils.shouldSkipFolder(cache, policy))
    }
  }

  @Test
  fun noMediaBoundary_isInheritedByDescendants() {
    withTempDirectory { root ->
      val boundary = File(root, "Private").apply { mkdirs() }
      File(boundary, ".nomedia").createNewFile()
      val descendant = File(boundary, "Clips/More").apply { mkdirs() }

      assertTrue(FileFilterUtils.isWithinNoMediaBoundary(descendant))
    }
  }

  private fun withTempDirectory(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("nomedia-filter-test").toFile()
    try {
      block(directory)
    } finally {
      directory.deleteRecursively()
    }
  }
}
