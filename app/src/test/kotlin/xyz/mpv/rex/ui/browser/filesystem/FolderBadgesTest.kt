package xyz.mpv.rex.ui.browser.filesystem

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.media.PlaybackStateIndex

/**
 * Guards the Phase 2 property: the directory listing is decorated once, from one set of inputs,
 * so a load publishes a single list instead of replacing the one it already published.
 */
class FolderBadgesTest {

  private val now = 1_800_000_000_000L
  private val day = 24L * 60L * 60L * 1000L

  private fun video(
    id: Long,
    path: String,
    durationMs: Long = 60_000L,
    dateModifiedSeconds: Long = now / 1000,
  ) = Video(
    id = id,
    title = path.substringAfterLast('/').substringBeforeLast('.'),
    displayName = path.substringAfterLast('/'),
    path = path,
    uri = mockk(relaxed = true),
    duration = durationMs,
    durationFormatted = "",
    size = 1L,
    sizeFormatted = "1 B",
    dateModified = dateModifiedSeconds,
    dateAdded = dateModifiedSeconds,
    mimeType = "video/mp4",
    bucketId = path.substringBeforeLast('/'),
    bucketDisplayName = path.substringBeforeLast('/').substringAfterLast('/'),
    width = 1920,
    height = 1080,
    fps = 30f,
    resolution = "1920x1080",
  )

  private fun item(v: Video): FileSystemItem.VideoFile =
    FileSystemItem.VideoFile(name = v.displayName, path = v.path, lastModified = 0L, video = v)

  /** History rows are keyed by [PlaybackStateEntity.mediaTitle]; `timeRemaining` is in seconds. */
  private fun state(
    title: String,
    remainingSeconds: Int,
    watched: Boolean = false,
  ) = PlaybackStateEntity(
    mediaTitle = title,
    lastPosition = 0,
    playbackSpeed = 1.0,
    sid = 0,
    subDelay = 0,
    subSpeed = 1.0,
    aid = 0,
    audioDelay = 0,
    timeRemaining = remainingSeconds,
    hasBeenWatched = watched,
  )

  @Test
  fun unplayedRecentVideo_isMarkedNew() = runTest {
    val v = video(1L, "/a/clip.mp4", dateModifiedSeconds = now / 1000 - 3600)
    val badges = FolderBadges()

    badges.decorate(listOf(item(v)), PlaybackStateIndex.of(emptyList()), now, 7 * day, 95)

    assertTrue(badges.newIds.contains(1L))
    assertTrue(badges.watchedIds.isEmpty())
  }

  @Test
  fun playedVideo_reportsProgressWithoutBeingNew() = runTest {
    val v = video(2L, "/a/clip.mp4", durationMs = 60_000L)
    val badges = FolderBadges()

    // 30s of a 60s file consumed -> 50% progress.
    badges.decorate(
      listOf(item(v)),
      PlaybackStateIndex.of(listOf(state("/a/clip.mp4", remainingSeconds = 30))),
      now,
      7 * day,
      95,
    )

    assertEquals(0.5f, badges.progress.getValue(2L), 0.001f)
    assertTrue(badges.newIds.isEmpty())
    assertTrue(badges.watchedIds.isEmpty())
  }

  @Test
  fun finishedVideo_isWatchedAndNoLongerInProgress() = runTest {
    val v = video(3L, "/a/clip.mp4", durationMs = 60_000L)
    val badges = FolderBadges()

    badges.decorate(
      listOf(item(v)),
      PlaybackStateIndex.of(emptyList()),
      now,
      7 * day,
      95,
    )

    val completed = video(3L, "/a/clip.mp4", durationMs = 60_000L)
    val badges2 = FolderBadges()
    badges2.decorate(
      listOf(item(completed)),
      PlaybackStateIndex.of(
        listOf(state("/a/clip.mp4", remainingSeconds = 0, watched = true)),
      ),
      now,
      7 * day,
      95,
    )

    assertTrue(badges2.watchedIds.contains(3L))
    assertTrue(badges2.progress.isEmpty())
  }

  @Test
  fun decoratingTwiceWithTheSameInputsProducesTheSameResult() = runTest {
    val items = listOf(
      item(video(10L, "/a/one.mp4", dateModifiedSeconds = now / 1000 - 30 * 24 * 3600)),
      item(video(11L, "/a/two.mp4", durationMs = 120_000L)),
    )
    val index = PlaybackStateIndex.of(listOf(state("/a/two.mp4", remainingSeconds = 60)))

    val first = FolderBadges().apply { decorate(items, index, now, 7 * day, 95) }
    val second = FolderBadges().apply { decorate(items, index, now, 7 * day, 95) }

    assertEquals(first.progress, second.progress)
    assertEquals(first.newIds, second.newIds)
    assertEquals(first.watchedIds, second.watchedIds)
  }

  @Test
  fun olderThanThresholdVideo_isNotMarkedNew() = runTest {
    val v = video(20L, "/a/old.mp4", dateModifiedSeconds = now / 1000 - 30 * 24 * 3600)
    val badges = FolderBadges()

    badges.decorate(listOf(item(v)), PlaybackStateIndex.of(emptyList()), now, 7 * day, 95)

    assertTrue(badges.newIds.isEmpty())
  }
}
