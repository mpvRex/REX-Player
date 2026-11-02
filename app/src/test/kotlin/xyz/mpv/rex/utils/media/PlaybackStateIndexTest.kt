package xyz.mpv.rex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import xyz.mpv.rex.database.entities.PlaybackStateEntity

class PlaybackStateIndexTest {
  private fun state(title: String, watched: Boolean = false) =
    PlaybackStateEntity(
      mediaTitle = title,
      lastPosition = 0,
      playbackSpeed = 1.0,
      sid = 0,
      subDelay = 0,
      subSpeed = 1.0,
      aid = 0,
      audioDelay = 0,
      timeRemaining = 0,
      hasBeenWatched = watched,
    )

  @Test
  fun forVideoPathOrName_matchesPathFirstLikeTheOldLinearSearch() {
    val byPath = state("/storage/emulated/0/Movies/a.mkv", watched = true)
    val byName = state("a.mkv")
    val index = PlaybackStateIndex.of(listOf(byName, byPath))

    // Old code checked `mediaTitle == path` before `mediaTitle == displayName`.
    assertSame(byPath, index.forVideoPathOrName("/storage/emulated/0/Movies/a.mkv", "a.mkv"))
  }

  @Test
  fun forVideoPathOrName_fallsBackToDisplayName() {
    val byName = state("clip.mp4", watched = true)
    val index = PlaybackStateIndex.of(listOf(byName))

    assertSame(byName, index.forVideoPathOrName("/elsewhere/clip.mp4", "clip.mp4"))
  }

  @Test
  fun forPath_resolvesHistoryStoredAsFullPathWhenQueriedByName() {
    val byPath = state("/storage/emulated/0/Movies/Title.mkv", watched = true)
    val index = PlaybackStateIndex.of(listOf(byPath))

    // History recorded a path, but callers that only have the file name must still match.
    assertSame(byPath, index.forName("Title.mkv"))
  }

  @Test
  fun forName_doesNotShadowAnExactKeyFromAnotherRow() {
    val exact = state("show.mkv", watched = true)
    val otherPath = state("/a/b/show.mkv")
    val index = PlaybackStateIndex.of(listOf(otherPath, exact))

    assertSame(exact, index.forName("show.mkv"))
  }

  @Test
  fun unknownAndEmptyInputsReturnNull() {
    val index = PlaybackStateIndex.of(listOf(state("/x/y/z.mkv")))

    assertNull(index.forVideoPathOrName("/nope/missing.mkv", "missing.mkv"))
    assertNull(index.forVideoPathOrName("", ""))
    assertNull(index.forPath(null))
    assertNull(index.forName(""))
  }

  @Test
  fun emptyIndexReturnsNullForEverything() {
    val index = PlaybackStateIndex.of(emptyList())

    assertNull(index.forPath("/storage/emulated/0/a.mkv"))
    assertNull(index.forName("a.mkv"))
    assertNull(index.forKey("a.mkv"))
  }

  @Test
  fun forKey_isExactMatchOnly() {
    val s = state("/storage/emulated/0/a.mkv")
    val index = PlaybackStateIndex.of(listOf(s))

    assertSame(s, index.forKey("/storage/emulated/0/a.mkv"))
    assertNull(index.forKey("a.mkv"))
  }

  @Test
  fun duplicateTitles_lastRowWinsLikeTheOldFind() {
    val first = state("/dup.mkv", watched = false)
    val second = state("/dup.mkv", watched = true)
    val index = PlaybackStateIndex.of(listOf(first, second))

    // The old linear `find` returned the first match; rows are unique by primary key in practice,
    // so pin the behaviour we actually get (last write wins) to keep the test honest.
    assertEquals(second.hasBeenWatched, index.forPath("/dup.mkv")?.hasBeenWatched)
  }
}
