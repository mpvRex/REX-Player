package xyz.mpv.rex.ui.browser.networkstreaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.utils.media.NetworkFileComparator
class NetworkFileComparatorTest {

  private fun folder(name: String, lastModified: Long = 0L, size: Long = 0L): NetworkFile =
    NetworkFile(name = name, path = "/share/$name", size = size, isDirectory = true, lastModified = lastModified)

  private fun file(name: String, lastModified: Long = 0L, size: Long = 1000L): NetworkFile =
    NetworkFile(name = name, path = "/share/$name", size = size, isDirectory = false, lastModified = lastModified)

  @Test
  fun `foldersFirst - name ascending places all directories before files`() {
    val items = listOf(
      file("z_file.mp4"),
      folder("Z_Folder"),
      file("a_file.mp4"),
      folder("A_Folder"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("A_Folder", "Z_Folder", "a_file.mp4", "z_file.mp4"), sorted.map { it.name })
  }

  @Test
  fun `foldersFirst - name descending preserves directories first invariant`() {
    val items = listOf(
      file("file_1.mp4"),
      folder("folder_1"),
      file("file_2.mp4"),
      folder("folder_2"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Descending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("folder_2", "folder_1", "file_2.mp4", "file_1.mp4"), sorted.map { it.name })
  }

  @Test
  fun `foldersFirst - date ascending places directories before files regardless of timestamp`() {
    val items = listOf(
      file("old_file.mp4", lastModified = 100L),
      folder("new_folder", lastModified = 900L),
      file("new_file.mp4", lastModified = 800L),
      folder("old_folder", lastModified = 200L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Ascending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("old_folder", "new_folder", "old_file.mp4", "new_file.mp4"), sorted.map { it.name })
  }

  @Test
  fun `foldersFirst - date descending places directories before files regardless of timestamp`() {
    val items = listOf(
      file("newest_file.mp4", lastModified = 9999L),
      folder("older_folder", lastModified = 1000L),
      file("older_file.mp4", lastModified = 500L),
      folder("newest_folder", lastModified = 5000L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Descending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("newest_folder", "older_folder", "newest_file.mp4", "older_file.mp4"), sorted.map { it.name })
  }

  @Test
  fun `foldersFirst - size ascending places directories before files regardless of size`() {
    val items = listOf(
      file("huge_file.mp4", size = 10_000_000L),
      folder("folder_b", size = 0L),
      file("tiny_file.mp4", size = 50L),
      folder("folder_a", size = 0L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Ascending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("folder_a", "folder_b", "tiny_file.mp4", "huge_file.mp4"), sorted.map { it.name })
  }

  @Test
  fun `foldersFirst - size descending places directories before files regardless of size`() {
    val items = listOf(
      file("small_file.mp4", size = 100L),
      file("huge_file.mp4", size = 50_000_000L),
      folder("folder_1", size = 0L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Descending, foldersFirst = true)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("folder_1", "huge_file.mp4", "small_file.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByName - natural order handles unpadded episode numbers correctly`() {
    val items = listOf(
      file("ep100.mp4"),
      file("ep1.mp4"),
      file("ep20.mp4"),
      file("ep2.mp4"),
      file("ep10.mp4"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("ep1.mp4", "ep2.mp4", "ep10.mp4", "ep20.mp4", "ep100.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByName - descending natural order reverses numbering correctly`() {
    val items = listOf(
      file("ep1.mp4"),
      file("ep2.mp4"),
      file("ep10.mp4"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Descending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("ep10.mp4", "ep2.mp4", "ep1.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByName - mixed zero padded and unpadded numbers`() {
    val items = listOf(
      file("10.mp4"),
      file("1.mp4"),
      file("02.mp4"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("1.mp4", "02.mp4", "10.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByName - case insensitive sorting`() {
    val items = listOf(
      file("cherry.mkv"),
      file("Apple.mkv"),
      file("banana.mkv"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("Apple.mkv", "banana.mkv", "cherry.mkv"), sorted.map { it.name })
  }

  @Test
  fun `sortByName - embedded season and episode tokens`() {
    val items = listOf(
      file("Show S01E10.mkv"),
      file("Show S01E01.mkv"),
      file("Show S01E02.mkv"),
      file("Show S01E09.mkv"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(
      listOf("Show S01E01.mkv", "Show S01E02.mkv", "Show S01E09.mkv", "Show S01E10.mkv"),
      sorted.map { it.name },
    )
  }

  @Test
  fun `sortByName - version numbering sorting`() {
    val items = listOf(
      file("release-v1.10.0.mkv"),
      file("release-v1.2.0.mkv"),
      file("release-v1.2.1.mkv"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(
      listOf("release-v1.2.0.mkv", "release-v1.2.1.mkv", "release-v1.10.0.mkv"),
      sorted.map { it.name },
    )
  }

  @Test
  fun `sortByName - whitespace skipping and bracketed tags`() {
    val items = listOf(
      file("[SubGroup] Ep 10.mkv"),
      file("[SubGroup] Ep 01.mkv"),
      file("[SubGroup] Ep 2.mkv"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(
      listOf("[SubGroup] Ep 01.mkv", "[SubGroup] Ep 2.mkv", "[SubGroup] Ep 10.mkv"),
      sorted.map { it.name },
    )
  }

  @Test
  fun `sortByName - unicode and non-ascii names`() {
    val items = listOf(
      file("映画 10.mp4"),
      file("映画 1.mp4"),
      file("映画 2.mp4"),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Title, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("映画 1.mp4", "映画 2.mp4", "映画 10.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByDate - ascending orders oldest first`() {
    val items = listOf(
      file("middle.mp4", lastModified = 2000L),
      file("newest.mp4", lastModified = 3000L),
      file("oldest.mp4", lastModified = 1000L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("oldest.mp4", "middle.mp4", "newest.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByDate - descending orders newest first`() {
    val items = listOf(
      file("middle.mp4", lastModified = 2000L),
      file("newest.mp4", lastModified = 3000L),
      file("oldest.mp4", lastModified = 1000L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Descending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("newest.mp4", "middle.mp4", "oldest.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByDate - identical timestamp falls back to natural name tie breaking`() {
    val items = listOf(
      file("ep10.mp4", lastModified = 1500L),
      file("ep1.mp4", lastModified = 1500L),
      file("ep2.mp4", lastModified = 1500L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("ep1.mp4", "ep2.mp4", "ep10.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortByDate - zero timestamp handled gracefully`() {
    val items = listOf(
      file("has_date.mp4", lastModified = 5000L),
      file("no_date.mp4", lastModified = 0L),
    )
    val comparatorAsc = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Ascending, foldersFirst = false)
    assertEquals(listOf("no_date.mp4", "has_date.mp4"), items.sortedWith(comparatorAsc).map { it.name })

    val comparatorDesc = NetworkFileComparator.create(FolderSortType.Date, SortOrder.Descending, foldersFirst = false)
    assertEquals(listOf("has_date.mp4", "no_date.mp4"), items.sortedWith(comparatorDesc).map { it.name })
  }

  @Test
  fun `sortBySize - ascending orders smallest first`() {
    val items = listOf(
      file("large.mp4", size = 10_000_000L),
      file("zero.mp4", size = 0L),
      file("small.mp4", size = 500L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("zero.mp4", "small.mp4", "large.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortBySize - descending orders largest first`() {
    val items = listOf(
      file("small.mp4", size = 500L),
      file("huge.mp4", size = 10_000_000L),
      file("zero.mp4", size = 0L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Descending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("huge.mp4", "small.mp4", "zero.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortBySize - identical size falls back to natural name tie breaking`() {
    val items = listOf(
      file("part10.mp4", size = 1024L),
      file("part1.mp4", size = 1024L),
      file("part2.mp4", size = 1024L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("part1.mp4", "part2.mp4", "part10.mp4"), sorted.map { it.name })
  }

  @Test
  fun `sortBySize - large 64-bit file sizes do not overflow`() {
    val items = listOf(
      file("remux_60gb.mkv", size = 60_000_000_000L),
      file("clip_100mb.mp4", size = 100_000_000L),
      file("movie_4gb.mkv", size = 4_500_000_000L),
    )
    val comparator = NetworkFileComparator.create(FolderSortType.Size, SortOrder.Ascending, foldersFirst = false)
    val sorted = items.sortedWith(comparator)

    assertEquals(listOf("clip_100mb.mp4", "movie_4gb.mkv", "remux_60gb.mkv"), sorted.map { it.name })
  }

  @Test
  fun `unsupportedSortTypes - Duration and VideoCount fall back to Title natural sort`() {
    val items = listOf(
      file("ep10.mp4"),
      file("ep1.mp4"),
      file("ep2.mp4"),
    )
    val durationCmp = NetworkFileComparator.create(FolderSortType.Duration, SortOrder.Ascending, foldersFirst = false)
    assertEquals(listOf("ep1.mp4", "ep2.mp4", "ep10.mp4"), items.sortedWith(durationCmp).map { it.name })

    val countCmp = NetworkFileComparator.create(FolderSortType.VideoCount, SortOrder.Ascending, foldersFirst = false)
    assertEquals(listOf("ep1.mp4", "ep2.mp4", "ep10.mp4"), items.sortedWith(countCmp).map { it.name })
  }

  @Test
  fun `boundary - empty list returns empty list`() {
    val comparator = NetworkFileComparator.create()
    val sorted = emptyList<NetworkFile>().sortedWith(comparator)
    assertTrue(sorted.isEmpty())
  }

  @Test
  fun `boundary - single item returns single item`() {
    val items = listOf(file("lone.mp4"))
    val comparator = NetworkFileComparator.create()
    assertEquals(items, items.sortedWith(comparator))
  }

  @Test
  fun `boundary - duplicate items remain stable`() {
    val item1 = file("dup.mp4", size = 100L)
    val item2 = file("dup.mp4", size = 100L)
    val items = listOf(item1, item2)
    val comparator = NetworkFileComparator.create()
    val sorted = items.sortedWith(comparator)
    assertEquals(2, sorted.size)
  }
}
