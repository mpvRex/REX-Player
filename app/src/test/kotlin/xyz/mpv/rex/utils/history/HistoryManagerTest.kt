package xyz.mpv.rex.utils.history

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.preferences.AdvancedPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryManagerTest {

  private val context = mockk<Context>(relaxed = true)
  private val recentlyPlayedRepository = mockk<RecentlyPlayedRepository>(relaxed = true)
  private val playbackStateRepository = mockk<PlaybackStateRepository>(relaxed = true)
  private val advancedPreferences = mockk<AdvancedPreferences>(relaxed = true)
  private val testScope = TestScope()

  private val historyManager = HistoryManager(
    context = context,
    recentlyPlayedRepository = recentlyPlayedRepository,
    playbackStateRepository = playbackStateRepository,
    advancedPreferences = advancedPreferences,
    scope = testScope,
  )

  @Test
  fun `markAs Finished sets hasBeenWatched to true and records recently played`() = runTest {
    val filePath = "/storage/emulated/0/Movies/test.mp4"
    val fileName = "test.mp4"
    val durationMs = 120_000L // 120 seconds

    coEvery { playbackStateRepository.getVideoDataByTitle(fileName) } returns null

    val entitySlot = slot<PlaybackStateEntity>()
    coEvery { playbackStateRepository.upsert(capture(entitySlot)) } returns Unit

    historyManager.markAs(filePath, fileName, durationMs, MarkAsState.Finished)

    coVerify(exactly = 1) { playbackStateRepository.upsert(any()) }
    val capturedEntity = entitySlot.captured
    assertEquals(fileName, capturedEntity.mediaTitle)
    assertEquals(120, capturedEntity.lastPosition)
    assertEquals(0, capturedEntity.timeRemaining)
    assertTrue("hasBeenWatched must be true when marked as Finished", capturedEntity.hasBeenWatched)

    coVerify(exactly = 1) {
      recentlyPlayedRepository.addRecentlyPlayed(
        filePath = filePath,
        fileName = fileName,
        duration = durationMs,
        launchSource = "mark_as",
      )
    }
  }

  @Test
  fun `markAs Finished preserves existing audio and subtitle settings while setting hasBeenWatched to true`() = runTest {
    val filePath = "/storage/emulated/0/Movies/test.mp4"
    val fileName = "test.mp4"
    val durationMs = 120_000L

    val existingState = PlaybackStateEntity(
      mediaTitle = fileName,
      lastPosition = 30,
      playbackSpeed = 1.75,
      videoZoom = 1.2f,
      sid = 2,
      secondarySid = 3,
      subDelay = 500,
      subSpeed = 0.9,
      aid = 1,
      audioDelay = -200,
      timeRemaining = 90,
      hasBeenWatched = false,
    )
    coEvery { playbackStateRepository.getVideoDataByTitle(fileName) } returns existingState

    val entitySlot = slot<PlaybackStateEntity>()
    coEvery { playbackStateRepository.upsert(capture(entitySlot)) } returns Unit

    historyManager.markAs(filePath, fileName, durationMs, MarkAsState.Finished)

    coVerify(exactly = 1) { playbackStateRepository.upsert(any()) }
    val captured = entitySlot.captured
    assertEquals(fileName, captured.mediaTitle)
    assertEquals(120, captured.lastPosition)
    assertEquals(0, captured.timeRemaining)
    assertEquals(1.75, captured.playbackSpeed, 0.001)
    assertEquals(1.2f, captured.videoZoom)
    assertEquals(2, captured.sid)
    assertEquals(3, captured.secondarySid)
    assertEquals(500, captured.subDelay)
    assertEquals(0.9, captured.subSpeed, 0.001)
    assertEquals(1, captured.aid)
    assertEquals(-200, captured.audioDelay)
    assertTrue("hasBeenWatched must be true when marked as Finished", captured.hasBeenWatched)
  }

  private fun createPlaybackState(
    mediaTitle: String,
    lastPosition: Int = 0,
    timeRemaining: Int = 0,
    hasBeenWatched: Boolean = false,
    playbackSpeed: Double = 1.0,
    videoZoom: Float = 0f,
    sid: Int = -1,
    secondarySid: Int = -1,
    subDelay: Int = 0,
    subSpeed: Double = 1.0,
    aid: Int = -1,
    audioDelay: Int = 0,
  ) = PlaybackStateEntity(
    mediaTitle = mediaTitle,
    lastPosition = lastPosition,
    playbackSpeed = playbackSpeed,
    videoZoom = videoZoom,
    sid = sid,
    secondarySid = secondarySid,
    subDelay = subDelay,
    subSpeed = subSpeed,
    aid = aid,
    audioDelay = audioDelay,
    timeRemaining = timeRemaining,
    hasBeenWatched = hasBeenWatched,
  )

  @Test
  fun `markAs New resets hasBeenWatched to false and removes from recently played`() = runTest {
    val filePath = "/storage/emulated/0/Movies/test.mp4"
    val fileName = "test.mp4"
    val durationMs = 120_000L

    val existingState = createPlaybackState(
      mediaTitle = fileName,
      lastPosition = 120,
      timeRemaining = 0,
      hasBeenWatched = true,
    )
    coEvery { playbackStateRepository.getVideoDataByTitle(fileName) } returns existingState

    val entitySlot = slot<PlaybackStateEntity>()
    coEvery { playbackStateRepository.upsert(capture(entitySlot)) } returns Unit
    coEvery { recentlyPlayedRepository.deleteByFilePath(filePath) } returns Unit

    historyManager.markAs(filePath, fileName, durationMs, MarkAsState.New)

    coVerify(exactly = 1) { playbackStateRepository.upsert(any()) }
    val captured = entitySlot.captured
    assertEquals(fileName, captured.mediaTitle)
    assertEquals(0, captured.lastPosition)
    assertEquals(-1, captured.timeRemaining)
    assertFalse("hasBeenWatched must be false when marked as New", captured.hasBeenWatched)

    coVerify(exactly = 1) { recentlyPlayedRepository.deleteByFilePath(filePath) }
  }

  @Test
  fun `markAs LastPlayed sets hasBeenWatched to false and records recently played`() = runTest {
    val filePath = "/storage/emulated/0/Movies/test.mp4"
    val fileName = "test.mp4"
    val durationMs = 120_000L

    val existingState = createPlaybackState(
      mediaTitle = fileName,
      lastPosition = 120,
      timeRemaining = 0,
      hasBeenWatched = true,
    )
    coEvery { playbackStateRepository.getVideoDataByTitle(fileName) } returns existingState

    val entitySlot = slot<PlaybackStateEntity>()
    coEvery { playbackStateRepository.upsert(capture(entitySlot)) } returns Unit

    historyManager.markAs(filePath, fileName, durationMs, MarkAsState.LastPlayed)

    coVerify(exactly = 1) { playbackStateRepository.upsert(any()) }
    val captured = entitySlot.captured
    assertEquals(fileName, captured.mediaTitle)
    assertEquals(0, captured.lastPosition)
    assertEquals(0, captured.timeRemaining)
    assertFalse("hasBeenWatched must be false when marked as LastPlayed", captured.hasBeenWatched)

    coVerify(exactly = 1) {
      recentlyPlayedRepository.addRecentlyPlayed(
        filePath = filePath,
        fileName = fileName,
        duration = durationMs,
        launchSource = "mark_as_last_played",
      )
    }
  }

  @Test
  fun `markAs None sets hasBeenWatched to false and removes from recently played`() = runTest {
    val filePath = "/storage/emulated/0/Movies/test.mp4"
    val fileName = "test.mp4"
    val durationMs = 120_000L

    val existingState = createPlaybackState(
      mediaTitle = fileName,
      lastPosition = 120,
      timeRemaining = 0,
      hasBeenWatched = true,
    )
    coEvery { playbackStateRepository.getVideoDataByTitle(fileName) } returns existingState

    val entitySlot = slot<PlaybackStateEntity>()
    coEvery { playbackStateRepository.upsert(capture(entitySlot)) } returns Unit
    coEvery { recentlyPlayedRepository.deleteByFilePath(filePath) } returns Unit

    historyManager.markAs(filePath, fileName, durationMs, MarkAsState.None)

    coVerify(exactly = 1) { playbackStateRepository.upsert(any()) }
    val captured = entitySlot.captured
    assertEquals(fileName, captured.mediaTitle)
    assertEquals(0, captured.lastPosition)
    assertEquals(120, captured.timeRemaining)
    assertFalse("hasBeenWatched must be false when marked as None", captured.hasBeenWatched)

    coVerify(exactly = 1) { recentlyPlayedRepository.deleteByFilePath(filePath) }
  }
}
