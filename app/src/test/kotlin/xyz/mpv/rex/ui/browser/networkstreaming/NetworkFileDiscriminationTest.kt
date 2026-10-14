package xyz.mpv.rex.ui.browser.networkstreaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.utils.storage.NetworkFileTypeHelper
import xyz.mpv.rex.utils.storage.NetworkItemCategory

class NetworkFileDiscriminationTest {

  private fun file(name: String, isDir: Boolean = false, size: Long = 1024L): NetworkFile =
    NetworkFile(name = name, path = "/share/$name", size = size, isDirectory = isDir)

  @Test
  fun `directories resolve to FOLDER category and are navigable`() {
    val folder = file("Season 1", isDir = true)
    val category = NetworkFileTypeHelper.resolveCategory(folder)
    assertEquals(NetworkItemCategory.FOLDER, category)
    assertTrue(category.isNavigable)
    assertFalse(category.isPlayable)
    assertEquals("DIR", NetworkFileTypeHelper.badgeTextFor(folder, category))
  }

  @Test
  fun `video files resolve to VIDEO category and are playable`() {
    val extensions = listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v")
    for (ext in extensions) {
      val video = file("movie.$ext")
      val category = NetworkFileTypeHelper.resolveCategory(video)
      assertEquals("Extension $ext should resolve to VIDEO", NetworkItemCategory.VIDEO, category)
      assertTrue(category.isPlayable)
      assertFalse(category.isNavigable)
      assertEquals(ext.uppercase(), NetworkFileTypeHelper.badgeTextFor(video, category))
    }
  }

  @Test
  fun `audio files resolve to AUDIO category and are playable`() {
    val extensions = listOf("mp3", "flac", "wav", "m4a", "ogg", "opus")
    for (ext in extensions) {
      val audio = file("song.$ext")
      val category = NetworkFileTypeHelper.resolveCategory(audio)
      assertEquals("Extension $ext should resolve to AUDIO", NetworkItemCategory.AUDIO, category)
      assertTrue(category.isPlayable)
      assertFalse(category.isNavigable)
      assertEquals(ext.uppercase(), NetworkFileTypeHelper.badgeTextFor(audio, category))
    }
  }

  @Test
  fun `subtitle files resolve to SUBTITLE category and are not playable standalone`() {
    val extensions = listOf("srt", "vtt", "ass", "ssa", "sub")
    for (ext in extensions) {
      val sub = file("subtitles.$ext")
      val category = NetworkFileTypeHelper.resolveCategory(sub)
      assertEquals("Extension $ext should resolve to SUBTITLE", NetworkItemCategory.SUBTITLE, category)
      assertFalse(category.isPlayable)
      assertFalse(category.isNavigable)
      assertEquals(ext.uppercase(), NetworkFileTypeHelper.badgeTextFor(sub, category))
    }
  }

  @Test
  fun `document files resolve to DOCUMENT category and are not playable`() {
    val extensions = listOf("txt", "pdf", "nfo", "md", "doc", "log")
    for (ext in extensions) {
      val doc = file("info.$ext")
      val category = NetworkFileTypeHelper.resolveCategory(doc)
      assertEquals("Extension $ext should resolve to DOCUMENT", NetworkItemCategory.DOCUMENT, category)
      assertFalse(category.isPlayable)
      assertFalse(category.isNavigable)
      assertEquals(ext.uppercase(), NetworkFileTypeHelper.badgeTextFor(doc, category))
    }
  }

  @Test
  fun `archive files resolve to ARCHIVE category and are not playable`() {
    val extensions = listOf("zip", "rar", "7z", "tar", "gz", "iso")
    for (ext in extensions) {
      val archive = file("backup.$ext")
      val category = NetworkFileTypeHelper.resolveCategory(archive)
      assertEquals("Extension $ext should resolve to ARCHIVE", NetworkItemCategory.ARCHIVE, category)
      assertFalse(category.isPlayable)
      assertFalse(category.isNavigable)
      assertEquals(ext.uppercase(), NetworkFileTypeHelper.badgeTextFor(archive, category))
    }
  }

  @Test
  fun `unrecognized extensions resolve to GENERIC_FILE category`() {
    val unknown = file("firmware.bin")
    val category = NetworkFileTypeHelper.resolveCategory(unknown)
    assertEquals(NetworkItemCategory.GENERIC_FILE, category)
    assertFalse(category.isPlayable)
    assertFalse(category.isNavigable)
    assertEquals("BIN", NetworkFileTypeHelper.badgeTextFor(unknown, category))
  }

  @Test
  fun `files without extension resolve to GENERIC_FILE with default badge`() {
    val noExt = file("README")
    val category = NetworkFileTypeHelper.resolveCategory(noExt)
    assertEquals(NetworkItemCategory.GENERIC_FILE, category)
    assertEquals("FILE", NetworkFileTypeHelper.badgeTextFor(noExt, category))
  }

  @Test
  fun `case insensitivity handles uppercase extensions correctly`() {
    val uppercaseVideo = file("CLIP.MP4")
    assertEquals(NetworkItemCategory.VIDEO, NetworkFileTypeHelper.resolveCategory(uppercaseVideo))

    val uppercaseSub = file("TRACK.SRT")
    assertEquals(NetworkItemCategory.SUBTITLE, NetworkFileTypeHelper.resolveCategory(uppercaseSub))
  }
}
