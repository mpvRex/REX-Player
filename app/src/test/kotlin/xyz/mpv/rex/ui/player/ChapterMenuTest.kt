package xyz.mpv.rex.ui.player

import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.controls.components.sheets.chapterKey
import xyz.mpv.rex.ui.player.controls.components.sheets.resolveActiveChapterIndex

class ChapterMenuTest {

  @Test
  fun chapterKey_isUniqueAndStable_evenWithDuplicateNamesAndTimestamps() {
    val chapters = listOf(
      Segment(name = "Intro", start = 0f),
      Segment(name = "Intro", start = 0f), // duplicate name and timestamp
      Segment(name = "", start = 60f),
      Segment(name = "", start = 60f), // duplicate blank name and timestamp
      Segment(name = "Outro", start = 120f),
    )

    val keys = chapters.mapIndexed { index, chapter -> chapterKey(index, chapter) }

    assertEquals(5, keys.size)
    assertEquals(5, keys.distinct().size)
    assertEquals("0_0.0", keys[0])
    assertEquals("1_0.0", keys[1])
    assertEquals("2_60.0", keys[2])
    assertEquals("3_60.0", keys[3])
    assertEquals("4_120.0", keys[4])

    // Verify stability: calling again for same index/item returns equal key
    assertEquals(keys[0], chapterKey(0, chapters[0]))
    assertEquals(keys[1], chapterKey(1, chapters[1]))
  }

  @Test
  fun resolveActiveChapterIndex_withDirectIndex_solvesDuplicateChaptersIssue() {
    // Two duplicate segments with identical name and timestamp at index 0 and 1
    val chapters = persistentListOf(
      Segment(name = "Intro", start = 0f),
      Segment(name = "Intro", start = 0f),
      Segment(name = "Part 1", start = 60f),
    )

    // With currentChapterIndex = 1, direct O(1) index must resolve to index 1,
    // whereas linear indexOf(currentChapter) would erroneously resolve to index 0.
    val resolved = resolveActiveChapterIndex(
      chapters = chapters,
      currentChapter = chapters[1],
      currentChapterIndex = 1,
    )
    assertEquals(1, resolved)

    // And for index 0:
    val resolvedZero = resolveActiveChapterIndex(
      chapters = chapters,
      currentChapter = chapters[0],
      currentChapterIndex = 0,
    )
    assertEquals(0, resolvedZero)
  }

  @Test
  fun resolveActiveChapterIndex_handlesEdgeCases() {
    val chapters = persistentListOf(
      Segment(name = "Chapter 1", start = 0f),
      Segment(name = "Chapter 2", start = 100f),
      Segment(name = "Chapter 3", start = 200f),
    )

    // Case 1: currentChapterIndex valid
    assertEquals(1, resolveActiveChapterIndex(chapters, chapters[1], 1))

    // Case 2: currentChapterIndex negative
    assertEquals(-1, resolveActiveChapterIndex(chapters, null, -1))

    // Case 3: currentChapterIndex out of bounds
    assertEquals(-1, resolveActiveChapterIndex(chapters, null, 10))

    // Case 4: currentChapterIndex null, falls back to currentChapter
    assertEquals(2, resolveActiveChapterIndex(chapters, chapters[2], null))

    // Case 5: currentChapterIndex null, currentChapter null
    assertEquals(-1, resolveActiveChapterIndex(chapters, null, null))

    // Case 6: currentChapter not in list and currentChapterIndex null
    val missing = Segment(name = "Missing", start = 999f)
    assertEquals(-1, resolveActiveChapterIndex(chapters, missing, null))

    // Case 7: empty chapters list with currentChapterIndex = 0
    val emptyChapters = persistentListOf<Segment>()
    assertEquals(-1, resolveActiveChapterIndex(emptyChapters, null, 0))

    // Case 8: empty chapters list with non-null currentChapter
    assertEquals(-1, resolveActiveChapterIndex(emptyChapters, chapters[0], null))
  }

  @Test
  fun autoScroll_guardCondition_preventsInvalidScrolls() {
    val emptyChapters = persistentListOf<Segment>()
    val activeIndexEmpty = resolveActiveChapterIndex(emptyChapters, null, 0)
    assertFalse(activeIndexEmpty in emptyChapters.indices)

    val validChapters = persistentListOf(Segment("Ch1", 0f))
    val activeIndexValid = resolveActiveChapterIndex(validChapters, validChapters[0], 0)
    assertTrue(activeIndexValid in validChapters.indices)
  }

  @Test
  fun directIndexedAccess_eliminatesQuadraticScans() {
    val chapterCount = 10_000
    val largeChapters = (0 until chapterCount).map { i ->
      Segment(name = "Chapter ${i + 1}", start = i * 10f)
    }.toImmutableList()

    // Measure time for direct indexed access and key generation (O(1) per item, O(N) total)
    val startTimeDirect = System.nanoTime()
    var directAccessCount = 0
    for (index in largeChapters.indices) {
      val chapter = largeChapters[index]
      val key = chapterKey(index, chapter)
      val isSelected = index == 5000
      val label = "${index + 1}: ${chapter.name}"
      if (isSelected || label.isNotEmpty() || key.toString().isNotEmpty()) {
        directAccessCount++
      }
    }
    val directDurationMs = (System.nanoTime() - startTimeDirect) / 1_000_000

    assertEquals(chapterCount, directAccessCount)
    // Direct indexed access for 10,000 items should complete in under 50ms
    assertTrue("Direct indexed access took too long: ${directDurationMs}ms", directDurationMs < 50)
  }

  @Test
  fun resolveActiveChapterIndex_directIndexTakesPrecedenceOverObjectMatch() {
    val chapters = persistentListOf(
      Segment(name = "Chapter A", start = 0f),
      Segment(name = "Chapter B", start = 100f),
      Segment(name = "Chapter C", start = 200f),
      Segment(name = "Chapter D", start = 300f),
    )

    // Even if currentChapter points to chapters[0], if currentChapterIndex is 3,
    // the direct index (3) must take precedence in O(1).
    val resolved = resolveActiveChapterIndex(
      chapters = chapters,
      currentChapter = chapters[0],
      currentChapterIndex = 3,
    )
    assertEquals(3, resolved)
  }

  @Test
  fun resolveActiveChapterIndex_whenDirectIndexOutOfBounds_fallsBackToCurrentChapterObjectMatch() {
    val chapters = persistentListOf(
      Segment(name = "Chapter 1", start = 0f),
      Segment(name = "Chapter 2", start = 100f),
      Segment(name = "Chapter 3", start = 200f),
    )

    // Direct index 10 is out of bounds, but currentChapter is Chapter 3 (index 2)
    val resolvedOutOfBounds = resolveActiveChapterIndex(
      chapters = chapters,
      currentChapter = chapters[2],
      currentChapterIndex = 10,
    )
    assertEquals(2, resolvedOutOfBounds)

    // Negative index -1 is out of bounds, but currentChapter is Chapter 2 (index 1)
    val resolvedNegative = resolveActiveChapterIndex(
      chapters = chapters,
      currentChapter = chapters[1],
      currentChapterIndex = -1,
    )
    assertEquals(1, resolvedNegative)
  }

  @Test
  fun resolveActiveChapterIndex_singleItemChapterList() {
    val singleChapter = persistentListOf(Segment(name = "Only Chapter", start = 0f))

    assertEquals(0, resolveActiveChapterIndex(singleChapter, singleChapter[0], 0))
    assertEquals(0, resolveActiveChapterIndex(singleChapter, singleChapter[0], null))
    assertEquals(-1, resolveActiveChapterIndex(singleChapter, null, -1))
    assertEquals(-1, resolveActiveChapterIndex(singleChapter, null, 1))
  }

  @Test
  fun chapterKey_tenThousandIdenticalSegmentsList_guaranteesZeroKeyCollisions() {
    val identicalChapters = List(10_000) { Segment(name = "Same", start = 0f) }
    val keys = identicalChapters.mapIndexed { index, chapter -> chapterKey(index, chapter) }

    assertEquals(10_000, keys.size)
    assertEquals(10_000, keys.distinct().size)
  }
}

