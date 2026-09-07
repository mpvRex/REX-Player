package xyz.mpv.rex.database.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.mpv.rex.database.dao.HybridMediaDao
import xyz.mpv.rex.database.entities.HybridMediaEntity
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences

import xyz.mpv.rex.utils.media.MediaInfoOps

import io.mockk.every
import io.mockk.mockkStatic
import android.net.Uri

class HybridMediaIndexRepositoryTest {
  private val dao = mockk<HybridMediaDao>(relaxed = true)
  private val metadataCacheRepository = mockk<VideoMetadataCacheRepository>()
  private val repository = HybridMediaIndexRepository(
    context = mockk<Context>(relaxed = true),
    dao = dao,
    browserPreferences = mockk<BrowserPreferences>(relaxed = true),
    foldersPreferences = mockk<FoldersPreferences>(relaxed = true),
    metadataCacheRepository = metadataCacheRepository,
  )

  @Test
  fun flatFolders_keepDuplicateNamesSeparateAndMatchPlaybackByLocation() = runTest {
    val first = media(
      identity = "file:/storage/A/clip.mp4",
      location = "/storage/A/clip.mp4",
      parent = "/storage/A",
    )
    val second = media(
      identity = "file:/storage/B/clip.mp4",
      location = "/storage/B/clip.mp4",
      parent = "/storage/B",
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(first, second)

    val playbackState = PlaybackStateEntity(
      mediaTitle = first.location,
      lastPosition = 100,
      playbackSpeed = 1.0,
      sid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
      hasBeenWatched = true,
    )

    val folders = repository.getFlatFolders(
      playbackStates = listOf(playbackState),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertEquals(2, folders.size)
    assertEquals(0, folders.first { it.path == "/storage/A" }.unwatchedVideoCount)
    assertEquals(1, folders.first { it.path == "/storage/B" }.unwatchedVideoCount)
  }

  @Test
  fun flatFolders_rewatchedVideoWithHasBeenWatchedFalse_countsAsUnwatched() = runTest {
    val clip = media(
      identity = "file:/storage/A/clip.mp4",
      location = "/storage/A/clip.mp4",
      parent = "/storage/A",
    ).copy(duration = 100_000L)
    coEvery { dao.getAvailableMedia(true) } returns listOf(clip)

    val rewatchedPlaybackState = PlaybackStateEntity(
      mediaTitle = clip.location,
      lastPosition = 20,
      timeRemaining = 80,
      playbackSpeed = 1.0,
      sid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
      hasBeenWatched = false,
    )

    val folders = repository.getFlatFolders(
      playbackStates = listOf(rewatchedPlaybackState),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertEquals(1, folders.size)
    assertEquals(1, folders.first().unwatchedVideoCount)
  }

  @Test
  fun flatFolders_finishedVideoResetToZeroPosWithHasBeenWatchedTrue_countsAsWatched() = runTest {
    val clip = media(
      identity = "file:/storage/A/clip.mp4",
      location = "/storage/A/clip.mp4",
      parent = "/storage/A",
    ).copy(duration = 100_000L)
    coEvery { dao.getAvailableMedia(true) } returns listOf(clip)

    val finishedPlaybackState = PlaybackStateEntity(
      mediaTitle = clip.location,
      lastPosition = 0,
      timeRemaining = 0,
      playbackSpeed = 1.0,
      sid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
      hasBeenWatched = true,
    )

    val folders = repository.getFlatFolders(
      playbackStates = listOf(finishedPlaybackState),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertEquals(1, folders.size)
    assertEquals(0, folders.first().unwatchedVideoCount)
  }

  @Test
  fun flatFolders_inProgressVideoBelowThreshold_countsAsUnwatched() = runTest {
    val clip = media(
      identity = "file:/storage/A/clip.mp4",
      location = "/storage/A/clip.mp4",
      parent = "/storage/A",
    ).copy(duration = 100_000L)
    coEvery { dao.getAvailableMedia(true) } returns listOf(clip)

    val inProgressState = PlaybackStateEntity(
      mediaTitle = clip.location,
      lastPosition = 94,
      timeRemaining = 6,
      playbackSpeed = 1.0,
      sid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
      hasBeenWatched = false,
    )

    val folders = repository.getFlatFolders(
      playbackStates = listOf(inProgressState),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertEquals(1, folders.size)
    assertEquals(1, folders.first().unwatchedVideoCount)
  }

  @Test
  fun flatFolders_inProgressVideoAtThreshold_countsAsWatched() = runTest {
    val clip = media(
      identity = "file:/storage/A/clip.mp4",
      location = "/storage/A/clip.mp4",
      parent = "/storage/A",
    ).copy(duration = 100_000L)
    coEvery { dao.getAvailableMedia(true) } returns listOf(clip)

    val inProgressState = PlaybackStateEntity(
      mediaTitle = clip.location,
      lastPosition = 95,
      timeRemaining = 5,
      playbackSpeed = 1.0,
      sid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
      hasBeenWatched = false,
    )

    val folders = repository.getFlatFolders(
      playbackStates = listOf(inProgressState),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertEquals(1, folders.size)
    assertEquals(0, folders.first().unwatchedVideoCount)
  }

  @Test
  fun noMediaPolicyIsAppliedWhenReadingPersistentIndex() = runTest {
    coEvery { dao.getAvailableMedia(false) } returns emptyList()

    repository.getFlatFolders(
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = false,
    )

    coVerify(exactly = 1) { dao.getAvailableMedia(false) }
  }

  @Test
  fun enrichFolderMetadata_updatesDaoWithIndexedStatus_whenExtractionSucceeds() = runTest {
    mockkStatic(Uri::class)
    val mockUri = mockk<Uri>(relaxed = true)
    every { Uri.fromFile(any()) } returns mockUri

    val pendingItem = media(
      identity = "file:/storage/A/pending.mp4",
      location = "/storage/A/pending.mp4",
      parent = "/storage/A",
    ).copy(metadataState = "PENDING")

    coEvery { dao.getAvailableMedia(true) } returns listOf(pendingItem)
    coEvery { metadataCacheRepository.getOrExtractMetadata(any(), any(), any()) } returns MediaInfoOps.VideoMetadata(
      sizeBytes = 100,
      durationMs = 12000,
      width = 1920,
      height = 1080,
      rotation = 0,
      fps = 30f,
      hasEmbeddedSubtitles = false,
    )

    repository.enrichFolderMetadata("/storage/A")

    coVerify(exactly = 1) {
      dao.updateMediaMetadata(
        identity = "file:/storage/A/pending.mp4",
        duration = 12000,
        width = 1920,
        height = 1080,
        rotation = 0,
        metadataState = "INDEXED",
      )
    }
  }

  @Test
  fun enrichFolderMetadata_updatesDaoWithFailedStatus_whenExtractionFails() = runTest {
    mockkStatic(Uri::class)
    val mockUri = mockk<Uri>(relaxed = true)
    every { Uri.fromFile(any()) } returns mockUri

    val corruptItem = media(
      identity = "file:/storage/A/corrupt.mp4",
      location = "/storage/A/corrupt.mp4",
      parent = "/storage/A",
    ).copy(metadataState = "PENDING")

    coEvery { dao.getAvailableMedia(true) } returns listOf(corruptItem)
    coEvery { metadataCacheRepository.getOrExtractMetadata(any(), any(), any()) } returns null

    repository.enrichFolderMetadata("/storage/A")

    coVerify(exactly = 1) {
      dao.updateMediaMetadata(
        identity = "file:/storage/A/corrupt.mp4",
        duration = 0,
        width = 0,
        height = 0,
        rotation = 0,
        metadataState = "FAILED",
      )
    }
  }

  private fun media(
    identity: String,
    location: String,
    parent: String,
  ) = HybridMediaEntity(
    identity = identity,
    sourceType = "DIRECT_FILE",
    sourceRoot = "direct:/storage",
    location = location,
    parentIdentity = parent,
    parentDisplayName = parent.substringAfterLast('/'),
    displayName = "clip.mp4",
    mimeType = "video/mp4",
    size = 100,
    dateModified = System.currentTimeMillis() / 1000,
    isAudio = false,
    isNoMedia = true,
    lastSeenGeneration = 1,
  )
}

