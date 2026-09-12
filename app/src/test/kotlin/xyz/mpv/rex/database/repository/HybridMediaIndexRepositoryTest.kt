package xyz.mpv.rex.database.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
  private val browserPreferences = mockk<BrowserPreferences>(relaxed = true) {
    every { showAudioFiles.get() } returns false
  }
  private val repository = HybridMediaIndexRepository(
    context = mockk<Context>(relaxed = true),
    dao = dao,
    browserPreferences = browserPreferences,
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

  @Test
  fun getFoldersInDirectory_flattensSingleChildIntermediateFolders() = runTest {
    val opClip1 = media(
      identity = "file:/storage/emulated/0/Downloads/Anime/OnePiece/S01/ep1.mp4",
      location = "/storage/emulated/0/Downloads/Anime/OnePiece/S01/ep1.mp4",
      parent = "/storage/emulated/0/Downloads/Anime/OnePiece/S01",
    )
    val opClip2 = media(
      identity = "file:/storage/emulated/0/Downloads/Anime/OnePiece/S02/ep1.mp4",
      location = "/storage/emulated/0/Downloads/Anime/OnePiece/S02/ep1.mp4",
      parent = "/storage/emulated/0/Downloads/Anime/OnePiece/S02",
    )
    val narutoClip1 = media(
      identity = "file:/storage/emulated/0/Downloads/Anime/Naruto/S01/ep1.mp4",
      location = "/storage/emulated/0/Downloads/Anime/Naruto/S01/ep1.mp4",
      parent = "/storage/emulated/0/Downloads/Anime/Naruto/S01",
    )
    val narutoClip2 = media(
      identity = "file:/storage/emulated/0/Downloads/Anime/Naruto/S02/ep1.mp4",
      location = "/storage/emulated/0/Downloads/Anime/Naruto/S02/ep1.mp4",
      parent = "/storage/emulated/0/Downloads/Anime/Naruto/S02",
    )

    coEvery { dao.getAvailableMedia(true) } returns listOf(opClip1, opClip2, narutoClip1, narutoClip2)

    val rootFolders = repository.getFoldersInDirectory(
      parentPath = "/storage/emulated/0",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    // Downloads has only 1 child branch with media (Anime), so Downloads is flattened and Anime is brought forward to root
    assertEquals(1, rootFolders.size)
    assertEquals("Anime", rootFolders[0].name)
    assertEquals("/storage/emulated/0/Downloads/Anime", rootFolders[0].path)
    assertEquals(4, rootFolders[0].videoCount)

    // Inside Anime, it branches into OnePiece and Naruto (2 branches), so both are preserved
    val animeFolders = repository.getFoldersInDirectory(
      parentPath = "/storage/emulated/0/Downloads/Anime",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )
    assertEquals(2, animeFolders.size)
    assertEquals("Naruto", animeFolders[0].name)
    assertEquals("OnePiece", animeFolders[1].name)
  }

  @Test
  fun getFoldersInDirectory_doesNotFlattenFolderWithDirectMedia() = runTest {
    val downloadClip = media(
      identity = "file:/storage/emulated/0/Downloads/movie.mp4",
      location = "/storage/emulated/0/Downloads/movie.mp4",
      parent = "/storage/emulated/0/Downloads",
    )
    val animeClip = media(
      identity = "file:/storage/emulated/0/Downloads/Anime/OnePiece/ep1.mp4",
      location = "/storage/emulated/0/Downloads/Anime/OnePiece/ep1.mp4",
      parent = "/storage/emulated/0/Downloads/Anime/OnePiece",
    )

    coEvery { dao.getAvailableMedia(true) } returns listOf(downloadClip, animeClip)

    val rootFolders = repository.getFoldersInDirectory(
      parentPath = "/storage/emulated/0",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    // Downloads contains direct media (movie.mp4), so it must NOT be flattened
    assertEquals(1, rootFolders.size)
    assertEquals("Downloads", rootFolders[0].name)
    assertEquals("/storage/emulated/0/Downloads", rootFolders[0].path)
    assertEquals(2, rootFolders[0].videoCount)
  }

  @Test
  fun flatFolders_whenShowAudioFilesFalse_excludesAudioFromNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val audioOnly = media(
      identity = "file:/storage/Music/song.mp3",
      location = "/storage/Music/song.mp3",
      parent = "/storage/Music",
      isAudio = true,
    )
    val mixedVideo = media(
      identity = "file:/storage/Mixed/clip.mp4",
      location = "/storage/Mixed/clip.mp4",
      parent = "/storage/Mixed",
      isAudio = false,
    )
    val mixedAudio = media(
      identity = "file:/storage/Mixed/track.mp3",
      location = "/storage/Mixed/track.mp3",
      parent = "/storage/Mixed",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(audioOnly, mixedVideo, mixedAudio)

    val folders = repository.getFlatFolders(
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    val musicFolder = folders.first { it.path == "/storage/Music" }
    assertEquals(1, musicFolder.audioCount)
    assertEquals(0, musicFolder.videoCount)
    assertEquals(0, musicFolder.newCount)
    assertEquals(0, musicFolder.unwatchedVideoCount)

    val mixedFolder = folders.first { it.path == "/storage/Mixed" }
    assertEquals(1, mixedFolder.audioCount)
    assertEquals(1, mixedFolder.videoCount)
    assertEquals(1, mixedFolder.newCount)
    assertEquals(1, mixedFolder.unwatchedVideoCount)
  }

  @Test
  fun flatFolders_whenShowAudioFilesTrue_includesAudioInNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val audioOnly = media(
      identity = "file:/storage/Music/song.mp3",
      location = "/storage/Music/song.mp3",
      parent = "/storage/Music",
      isAudio = true,
    )
    val mixedVideo = media(
      identity = "file:/storage/Mixed/clip.mp4",
      location = "/storage/Mixed/clip.mp4",
      parent = "/storage/Mixed",
      isAudio = false,
    )
    val mixedAudio = media(
      identity = "file:/storage/Mixed/track.mp3",
      location = "/storage/Mixed/track.mp3",
      parent = "/storage/Mixed",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(audioOnly, mixedVideo, mixedAudio)

    val folders = repository.getFlatFolders(
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    val musicFolder = folders.first { it.path == "/storage/Music" }
    assertEquals(1, musicFolder.audioCount)
    assertEquals(0, musicFolder.videoCount)
    assertEquals(1, musicFolder.newCount)
    assertEquals(1, musicFolder.unwatchedVideoCount)

    val mixedFolder = folders.first { it.path == "/storage/Mixed" }
    assertEquals(1, mixedFolder.audioCount)
    assertEquals(1, mixedFolder.videoCount)
    assertEquals(2, mixedFolder.newCount)
    assertEquals(2, mixedFolder.unwatchedVideoCount)
  }

  @Test
  fun foldersInDirectory_whenShowAudioFilesFalse_excludesAudioFromNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val audioItem = media(
      identity = "file:/storage/Music/song.mp3",
      location = "/storage/Music/song.mp3",
      parent = "/storage/Music",
      isAudio = true,
    )
    val mixedVideo = media(
      identity = "file:/storage/Mixed/clip.mp4",
      location = "/storage/Mixed/clip.mp4",
      parent = "/storage/Mixed",
      isAudio = false,
    )
    val mixedAudio = media(
      identity = "file:/storage/Mixed/track.mp3",
      location = "/storage/Mixed/track.mp3",
      parent = "/storage/Mixed",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(audioItem, mixedVideo, mixedAudio)

    val folders = repository.getFoldersInDirectory(
      parentPath = "/storage",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    val musicFolder = folders.first { it.path == "/storage/Music" }
    assertEquals(1, musicFolder.audioCount)
    assertEquals(0, musicFolder.videoCount)
    assertEquals(0, musicFolder.newCount)
    assertEquals(0, musicFolder.unwatchedVideoCount)

    val mixedFolder = folders.first { it.path == "/storage/Mixed" }
    assertEquals(1, mixedFolder.audioCount)
    assertEquals(1, mixedFolder.videoCount)
    assertEquals(1, mixedFolder.newCount)
    assertEquals(1, mixedFolder.unwatchedVideoCount)
  }

  @Test
  fun foldersInDirectory_whenShowAudioFilesTrue_includesAudioInNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val audioItem = media(
      identity = "file:/storage/Music/song.mp3",
      location = "/storage/Music/song.mp3",
      parent = "/storage/Music",
      isAudio = true,
    )
    val mixedVideo = media(
      identity = "file:/storage/Mixed/clip.mp4",
      location = "/storage/Mixed/clip.mp4",
      parent = "/storage/Mixed",
      isAudio = false,
    )
    val mixedAudio = media(
      identity = "file:/storage/Mixed/track.mp3",
      location = "/storage/Mixed/track.mp3",
      parent = "/storage/Mixed",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(audioItem, mixedVideo, mixedAudio)

    val folders = repository.getFoldersInDirectory(
      parentPath = "/storage",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    val musicFolder = folders.first { it.path == "/storage/Music" }
    assertEquals(1, musicFolder.audioCount)
    assertEquals(0, musicFolder.videoCount)
    assertEquals(1, musicFolder.newCount)
    assertEquals(1, musicFolder.unwatchedVideoCount)

    val mixedFolder = folders.first { it.path == "/storage/Mixed" }
    assertEquals(1, mixedFolder.audioCount)
    assertEquals(1, mixedFolder.videoCount)
    assertEquals(2, mixedFolder.newCount)
    assertEquals(2, mixedFolder.unwatchedVideoCount)
  }

  @Test
  fun recursiveFolder_whenShowAudioFilesFalse_excludesAudioFromNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val directVideo = media(
      identity = "file:/storage/Music/clip.mp4",
      location = "/storage/Music/clip.mp4",
      parent = "/storage/Music",
      isAudio = false,
    )
    val nestedAudio = media(
      identity = "file:/storage/Music/Sub/song.mp3",
      location = "/storage/Music/Sub/song.mp3",
      parent = "/storage/Music/Sub",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(directVideo, nestedAudio)

    val musicFolder = repository.getRecursiveFolder(
      path = "/storage/Music",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertNotNull(musicFolder)
    assertEquals(1, musicFolder!!.audioCount)
    assertEquals(1, musicFolder.videoCount)
    assertEquals(1, musicFolder.newCount)
    assertEquals(1, musicFolder.unwatchedVideoCount)
  }

  @Test
  fun recursiveFolder_whenShowAudioFilesTrue_includesAudioInNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val directVideo = media(
      identity = "file:/storage/Music/clip.mp4",
      location = "/storage/Music/clip.mp4",
      parent = "/storage/Music",
      isAudio = false,
    )
    val nestedAudio = media(
      identity = "file:/storage/Music/Sub/song.mp3",
      location = "/storage/Music/Sub/song.mp3",
      parent = "/storage/Music/Sub",
      isAudio = true,
    )
    coEvery { dao.getAvailableMedia(true) } returns listOf(directVideo, nestedAudio)

    val musicFolder = repository.getRecursiveFolder(
      path = "/storage/Music",
      playbackStates = emptyList(),
      thresholdDays = 7,
      watchedThreshold = 95,
      includeNoMedia = true,
    )

    assertNotNull(musicFolder)
    assertEquals(1, musicFolder!!.audioCount)
    assertEquals(1, musicFolder.videoCount)
    assertEquals(2, musicFolder.newCount)
    assertEquals(2, musicFolder.unwatchedVideoCount)
  }

  private fun media(
    identity: String,
    location: String,
    parent: String,
    isAudio: Boolean = false,
    displayName: String = if (isAudio) "song.mp3" else "clip.mp4",
  ) = HybridMediaEntity(
    identity = identity,
    sourceType = "DIRECT_FILE",
    sourceRoot = "direct:/storage",
    location = location,
    parentIdentity = parent,
    parentDisplayName = parent.substringAfterLast('/'),
    displayName = displayName,
    mimeType = if (isAudio) "audio/mp3" else "video/mp4",
    size = 100,
    dateModified = System.currentTimeMillis() / 1000,
    isAudio = isAudio,
    isNoMedia = true,
    lastSeenGeneration = 1,
  )
}

