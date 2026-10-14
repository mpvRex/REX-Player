package xyz.mpv.rex.ui.browser.networkstreaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.utils.storage.NetworkFileFilter

class NetworkFileFilterTest {

  private fun file(name: String, isHidden: Boolean = false): NetworkFile =
    NetworkFile(name = name, path = "/share/$name", size = 1000L, isDirectory = false, isHidden = isHidden)

  private fun folder(name: String, isHidden: Boolean = false): NetworkFile =
    NetworkFile(name = name, path = "/share/$name", size = 0L, isDirectory = true, isHidden = isHidden)

  @Test
  fun `filter - showHidden false excludes dotfiles`() {
    val items = listOf(
      file(".DS_Store", isHidden = true),
      file(".nomedia", isHidden = true),
      file("video.mp4", isHidden = false),
      file(".hidden_video.mkv", isHidden = true),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(listOf("video.mp4"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden false excludes dot directories`() {
    val items = listOf(
      folder(".thumbnails", isHidden = true),
      folder(".git", isHidden = true),
      folder("Movies", isHidden = false),
      folder(".Trash-1000", isHidden = true),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(listOf("Movies"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden false excludes SMB hidden attribute files`() {
    val items = listOf(
      file("secret_movie.mp4", isHidden = true),
      file("public_movie.mp4", isHidden = false),
      file("Thumbs.db", isHidden = true),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(listOf("public_movie.mp4"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden false retains non-hidden files and directories`() {
    val items = listOf(
      folder("Series", isHidden = false),
      file("episode1.mp4", isHidden = false),
      folder("Anime", isHidden = false),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(listOf("Series", "episode1.mp4", "Anime"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden false returns empty list when all items are hidden`() {
    val items = listOf(
      file(".hidden1", isHidden = true),
      folder(".hidden2", isHidden = true),
      file("secret.mp4", isHidden = true),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertTrue(filtered.isEmpty())
  }

  @Test
  fun `filter - showHidden false returns identical list when no items are hidden`() {
    val items = listOf(
      folder("Music", isHidden = false),
      file("track.mp3", isHidden = false),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(items, filtered)
  }

  @Test
  fun `filter - showHidden true includes dotfiles and dot directories`() {
    val items = listOf(
      folder(".thumbnails", isHidden = true),
      file(".DS_Store", isHidden = true),
      file("movie.mp4", isHidden = false),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = true)

    assertEquals(3, filtered.size)
    assertEquals(listOf(".thumbnails", ".DS_Store", "movie.mp4"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden true includes SMB hidden and system files`() {
    val items = listOf(
      file("desktop.ini", isHidden = true),
      folder("System Volume Information", isHidden = true),
      file("movie.mp4", isHidden = false),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = true)

    assertEquals(listOf("desktop.ini", "System Volume Information", "movie.mp4"), filtered.map { it.name })
  }

  @Test
  fun `filter - showHidden true preserves original order and item identity`() {
    val items = listOf(
      file("a.mp4", isHidden = false),
      file(".b.mp4", isHidden = true),
      folder("c", isHidden = false),
    )
    val filtered = NetworkFileFilter.filter(items, showHidden = true)

    assertEquals(items, filtered)
  }

  @Test
  fun `smbAttributes - hidden attribute 0x02 marks item as hidden`() {
    assertTrue(NetworkFileFilter.isHidden("video.mp4", fileAttributes = 0x02L))
  }

  @Test
  fun `smbAttributes - system attribute 0x04 marks item as hidden`() {
    assertTrue(NetworkFileFilter.isHidden("video.mp4", fileAttributes = 0x04L))
  }

  @Test
  fun `smbAttributes - hidden and system combined 0x06 marks item as hidden`() {
    assertTrue(NetworkFileFilter.isHidden("desktop.ini", fileAttributes = 0x02L or 0x04L))
  }

  @Test
  fun `smbAttributes - directory with hidden attribute 0x12 marks item as hidden`() {
    val directoryWithHidden = 0x10L or 0x02L
    assertTrue(NetworkFileFilter.isHidden("SecretFolder", fileAttributes = directoryWithHidden))
  }

  @Test
  fun `smbAttributes - directory with system attribute 0x14 marks item as hidden`() {
    val directoryWithSystem = 0x10L or 0x04L
    assertTrue(NetworkFileFilter.isHidden("System Volume Information", fileAttributes = directoryWithSystem))
  }

  @Test
  fun `smbAttributes - normal directory 0x10 is not marked hidden`() {
    assertFalse(NetworkFileFilter.isHidden("Movies", fileAttributes = 0x10L))
  }

  @Test
  fun `smbAttributes - normal file 0x20 or 0x80 is not marked hidden`() {
    assertFalse(NetworkFileFilter.isHidden("movie.mp4", fileAttributes = 0x20L))
    assertFalse(NetworkFileFilter.isHidden("movie.mp4", fileAttributes = 0x80L))
    assertFalse(NetworkFileFilter.isHidden("movie.mp4", fileAttributes = 0x00L))
  }

  @Test
  fun `dotfile - filename starting with dot is always hidden regardless of zero attributes`() {
    assertTrue(NetworkFileFilter.isHidden(".nomedia", fileAttributes = 0x00L))
    assertTrue(NetworkFileFilter.isHidden(".DS_Store", fileAttributes = 0x20L))
  }

  @Test
  fun `dotfile - filename with middle dots is not hidden`() {
    assertFalse(NetworkFileFilter.isHidden("Movie.2026.1080p.mkv", fileAttributes = 0x00L))
    assertFalse(NetworkFileFilter.isHidden("my.first.video.mp4", fileAttributes = 0x20L))
  }

  @Test
  fun `dotfile - filename with trailing dot is not hidden`() {
    assertFalse(NetworkFileFilter.isHidden("filename.", fileAttributes = 0x00L))
  }

  @Test
  fun `boundary - filtering empty list returns empty list`() {
    assertTrue(NetworkFileFilter.filter(emptyList(), showHidden = false).isEmpty())
    assertTrue(NetworkFileFilter.filter(emptyList(), showHidden = true).isEmpty())
  }

  @Test
  fun `boundary - filter does not mutate input collection`() {
    val items = listOf(file(".hidden", isHidden = true), file("visible.mp4"))
    val result = NetworkFileFilter.filter(items, showHidden = false)

    assertEquals(1, result.size)
    assertEquals(2, items.size)
  }

  @Test
  fun `boundary - defense in depth checks filename even if isHidden flag is false`() {
    val unflaggedDotfile = NetworkFile(name = ".secret", path = "/share/.secret", size = 0L, isDirectory = false, isHidden = false)
    val result = NetworkFileFilter.filter(listOf(unflaggedDotfile), showHidden = false)

    assertTrue("Dotfile must be excluded when showHidden is false even if isHidden boolean is false", result.isEmpty())
  }

}
