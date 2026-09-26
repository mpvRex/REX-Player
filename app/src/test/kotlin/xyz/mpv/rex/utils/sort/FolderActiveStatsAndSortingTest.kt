package xyz.mpv.rex.utils.sort

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.ui.browser.filesystem.FileSystemBrowserViewModel
import xyz.mpv.rex.utils.media.MediaInfoOps
import xyz.mpv.rex.utils.media.MetadataRetrieval
import java.io.File
import java.nio.file.Files

class FolderActiveStatsAndSortingTest {

  @Test
  fun videoFolder_activeStats_whenAudioHidden_onlyReflectsVideos() {
    val mixedFolder = VideoFolder(
      bucketId = "1",
      name = "Mixed",
      path = "/storage/Mixed",
      videoCount = 1,
      audioCount = 2,
      videoSize = 1_000_000L,
      audioSize = 500_000L,
      totalSize = 1_500_000L,
      videoDuration = 60_000L,
      audioDuration = 30_000L,
      totalDuration = 90_000L,
    )

    assertEquals(1, mixedFolder.activeCount(showAudioFiles = false))
    assertEquals(1_000_000L, mixedFolder.activeSize(showAudioFiles = false))
    assertEquals(60_000L, mixedFolder.activeDuration(showAudioFiles = false))

    assertEquals(3, mixedFolder.activeCount(showAudioFiles = true))
    assertEquals(1_500_000L, mixedFolder.activeSize(showAudioFiles = true))
    assertEquals(90_000L, mixedFolder.activeDuration(showAudioFiles = true))
  }

  @Test
  fun videoFolder_activeStats_legacyCachedDataFallback() {
    // When legacy data has videoSize = 0 and audioCount = 0, fallback to totalSize/totalDuration
    val legacyFolder = VideoFolder(
      bucketId = "2",
      name = "Legacy",
      path = "/storage/Legacy",
      videoCount = 2,
      audioCount = 0,
      videoSize = 0L,
      audioSize = 0L,
      totalSize = 2_000_000L,
      videoDuration = 0L,
      audioDuration = 0L,
      totalDuration = 120_000L,
    )

    assertEquals(2, legacyFolder.activeCount(showAudioFiles = false))
    assertEquals(2_000_000L, legacyFolder.activeSize(showAudioFiles = false))
    assertEquals(120_000L, legacyFolder.activeDuration(showAudioFiles = false))

    // When legacy data has videoCount > 0 and audioCount > 0 (videoSize=0, audioSize=0),
    // it must safely fall back to totalSize/totalDuration rather than returning 0
    val legacyMixedFolder = VideoFolder(
      bucketId = "3",
      name = "LegacyMixed",
      path = "/storage/LegacyMixed",
      videoCount = 2,
      audioCount = 1,
      videoSize = 0L,
      audioSize = 0L,
      totalSize = 3_000_000L,
      videoDuration = 0L,
      audioDuration = 0L,
      totalDuration = 180_000L,
    )
    assertEquals(2, legacyMixedFolder.activeCount(showAudioFiles = false))
    assertEquals(3_000_000L, legacyMixedFolder.activeSize(showAudioFiles = false))
    assertEquals(180_000L, legacyMixedFolder.activeDuration(showAudioFiles = false))
    assertEquals(3, legacyMixedFolder.activeCount(showAudioFiles = true))
    assertEquals(3_000_000L, legacyMixedFolder.activeSize(showAudioFiles = true))
    assertEquals(180_000L, legacyMixedFolder.activeDuration(showAudioFiles = true))

    // When legacy data has videoCount = 0 and audioCount > 0, active stats when audio is hidden must be 0
    val legacyAudioOnly = VideoFolder(
      bucketId = "4",
      name = "LegacyAudio",
      path = "/storage/LegacyAudio",
      videoCount = 0,
      audioCount = 3,
      videoSize = 0L,
      audioSize = 0L,
      totalSize = 500_000L,
      videoDuration = 0L,
      audioDuration = 0L,
      totalDuration = 30_000L,
    )
    assertEquals(0, legacyAudioOnly.activeCount(showAudioFiles = false))
    assertEquals(0L, legacyAudioOnly.activeSize(showAudioFiles = false))
    assertEquals(0L, legacyAudioOnly.activeDuration(showAudioFiles = false))
    assertEquals(3, legacyAudioOnly.activeCount(showAudioFiles = true))
    assertEquals(500_000L, legacyAudioOnly.activeSize(showAudioFiles = true))
    assertEquals(30_000L, legacyAudioOnly.activeDuration(showAudioFiles = true))
  }

  @Test
  fun videoFolder_activeStats_zeroByteVideoInMixedFolder_returnsZeroForVideos() {
    val zeroByteVideoFolder = VideoFolder(
      bucketId = "5",
      name = "ZeroByteVideo",
      path = "/storage/ZeroByteVideo",
      videoCount = 1,
      audioCount = 1,
      videoSize = 0L,
      audioSize = 100_000L,
      totalSize = 100_000L,
      videoDuration = 0L,
      audioDuration = 5_000L,
      totalDuration = 5_000L,
    )
    assertEquals(1, zeroByteVideoFolder.activeCount(showAudioFiles = false))
    assertEquals(0L, zeroByteVideoFolder.activeSize(showAudioFiles = false))
    assertEquals(0L, zeroByteVideoFolder.activeDuration(showAudioFiles = false))
    assertEquals(2, zeroByteVideoFolder.activeCount(showAudioFiles = true))
    assertEquals(100_000L, zeroByteVideoFolder.activeSize(showAudioFiles = true))
    assertEquals(5_000L, zeroByteVideoFolder.activeDuration(showAudioFiles = true))
  }

  @Test
  fun mediaFolder_activeStats_whenAudioHidden_onlyReflectsVideos() {
    val mediaFolder = MediaFolder(
      id = "1",
      name = "Media",
      path = "/storage/Media",
      videoCount = 2,
      audioCount = 3,
      videoSize = 2_000_000L,
      audioSize = 300_000L,
      totalSize = 2_300_000L,
      videoDuration = 100_000L,
      audioDuration = 50_000L,
      totalDuration = 150_000L,
    )

    assertEquals(2, mediaFolder.activeCount(showAudioFiles = false))
    assertEquals(2_000_000L, mediaFolder.activeSize(showAudioFiles = false))
    assertEquals(100_000L, mediaFolder.activeDuration(showAudioFiles = false))

    assertEquals(5, mediaFolder.activeCount(showAudioFiles = true))
    assertEquals(2_300_000L, mediaFolder.activeSize(showAudioFiles = true))
    assertEquals(150_000L, mediaFolder.activeDuration(showAudioFiles = true))
  }

  @Test
  fun fileSystemItemFolder_activeStats_whenAudioHidden_onlyReflectsVideos() {
    val folder = FileSystemItem.Folder(
      name = "Downloads",
      path = "/storage/Downloads",
      lastModified = 0L,
      videoCount = 1,
      audioCount = 1,
      videoSize = 9_900_000_000L,
      audioSize = 400_000_000L,
      totalSize = 10_300_000_000L,
      videoDuration = 7_200_000L,
      audioDuration = 180_000L,
      totalDuration = 7_380_000L,
    )

    // Issue #405 reproduction: 9.9 GB video + 400 MB audio
    // When showAudioFiles is false, activeSize must be 9.9 GB, not 10.3 GB!
    assertEquals(1, folder.activeCount(showAudioFiles = false))
    assertEquals(9_900_000_000L, folder.activeSize(showAudioFiles = false))
    assertEquals(7_200_000L, folder.activeDuration(showAudioFiles = false))

    assertEquals(2, folder.activeCount(showAudioFiles = true))
    assertEquals(10_300_000_000L, folder.activeSize(showAudioFiles = true))
    assertEquals(7_380_000L, folder.activeDuration(showAudioFiles = true))
  }

  @Test
  fun sortFolders_bySize_respectsShowAudioFiles() {
    // Folder A: large video (800MB), small audio (100MB) -> videoSize = 800MB, totalSize = 900MB
    val folderA = VideoFolder(
      bucketId = "A",
      name = "FolderA",
      path = "/storage/FolderA",
      videoCount = 1,
      audioCount = 1,
      videoSize = 800L,
      audioSize = 100L,
      totalSize = 900L,
    )
    // Folder B: small video (500MB), large audio (600MB) -> videoSize = 500MB, totalSize = 1100MB
    val folderB = VideoFolder(
      bucketId = "B",
      name = "FolderB",
      path = "/storage/FolderB",
      videoCount = 1,
      audioCount = 2,
      videoSize = 500L,
      audioSize = 600L,
      totalSize = 1100L,
    )

    val list = listOf(folderA, folderB)

    // When showAudioFiles = true, totalSize determines order: folderA (900) < folderB (1100)
    val sortedWithAudio = SortUtils.sortFolders(
      folders = list,
      sortType = FolderSortType.Size,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = true,
    )
    assertEquals("FolderA", sortedWithAudio[0].name)
    assertEquals("FolderB", sortedWithAudio[1].name)

    // When showAudioFiles = false, videoSize determines order: folderB (500) < folderA (800)
    val sortedWithoutAudio = SortUtils.sortFolders(
      folders = list,
      sortType = FolderSortType.Size,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = false,
    )
    assertEquals("FolderB", sortedWithoutAudio[0].name)
    assertEquals("FolderA", sortedWithoutAudio[1].name)
  }

  @Test
  fun sortFolders_byDuration_respectsShowAudioFiles() {
    // Folder A: video 60s, audio 10s -> videoDuration = 60s, totalDuration = 70s
    val folderA = VideoFolder(
      bucketId = "A",
      name = "FolderA",
      path = "/storage/FolderA",
      videoCount = 1,
      audioCount = 1,
      videoDuration = 60L,
      audioDuration = 10L,
      totalDuration = 70L,
    )
    // Folder B: video 40s, audio 50s -> videoDuration = 40s, totalDuration = 90s
    val folderB = VideoFolder(
      bucketId = "B",
      name = "FolderB",
      path = "/storage/FolderB",
      videoCount = 1,
      audioCount = 1,
      videoDuration = 40L,
      audioDuration = 50L,
      totalDuration = 90L,
    )

    val list = listOf(folderA, folderB)

    // When showAudioFiles = true, totalDuration: folderA (70) < folderB (90)
    val sortedWithAudio = SortUtils.sortFolders(
      folders = list,
      sortType = FolderSortType.Duration,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = true,
    )
    assertEquals("FolderA", sortedWithAudio[0].name)
    assertEquals("FolderB", sortedWithAudio[1].name)

    // When showAudioFiles = false, videoDuration: folderB (40) < folderA (60)
    val sortedWithoutAudio = SortUtils.sortFolders(
      folders = list,
      sortType = FolderSortType.Duration,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = false,
    )
    assertEquals("FolderB", sortedWithoutAudio[0].name)
    assertEquals("FolderA", sortedWithoutAudio[1].name)
  }

  @Test
  fun sortFileSystemItems_respectsShowAudioFiles() {
    val folderA = FileSystemItem.Folder(
      name = "FolderA",
      path = "/storage/FolderA",
      lastModified = 0L,
      videoCount = 1,
      audioCount = 1,
      videoSize = 800L,
      audioSize = 100L,
      totalSize = 900L,
    )
    val folderB = FileSystemItem.Folder(
      name = "FolderB",
      path = "/storage/FolderB",
      lastModified = 0L,
      videoCount = 1,
      audioCount = 2,
      videoSize = 500L,
      audioSize = 600L,
      totalSize = 1100L,
    )

    val items = listOf(folderA, folderB)

    val sortedWithAudio = SortUtils.sortFileSystemItems(
      items = items,
      sortType = FolderSortType.Size,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = true,
    )
    assertEquals("FolderA", (sortedWithAudio[0] as FileSystemItem.Folder).name)
    assertEquals("FolderB", (sortedWithAudio[1] as FileSystemItem.Folder).name)

    val sortedWithoutAudio = SortUtils.sortFileSystemItems(
      items = items,
      sortType = FolderSortType.Size,
      sortOrder = SortOrder.Ascending,
      showAudioFiles = false,
    )
    assertEquals("FolderB", (sortedWithoutAudio[0] as FileSystemItem.Folder).name)
    assertEquals("FolderA", (sortedWithoutAudio[1] as FileSystemItem.Folder).name)
  }

  @Test
  fun fileSystemItem_audioVisibilityFiltering_excludesAudioFilesAndAudioOnlyFoldersWhenHidden() {
    val videoFile = FileSystemItem.VideoFile(
      name = "video.mp4",
      path = "/storage/video.mp4",
      lastModified = 0L,
      video = xyz.mpv.rex.domain.media.model.Video(
        id = 1L,
        title = "video",
        displayName = "video.mp4",
        path = "/storage/video.mp4",
        uri = mockk<android.net.Uri>(relaxed = true),
        duration = 1000L,
        durationFormatted = "00:01",
        size = 1000L,
        sizeFormatted = "1KB",
        dateModified = 0L,
        dateAdded = 0L,
        mimeType = "video/mp4",
        bucketId = "storage",
        bucketDisplayName = "storage",
        width = 1920,
        height = 1080,
        rotation = 0,
        fps = 30f,
        resolution = "1080p",
        isAudio = false,
      ),
    )
    val audioFile = FileSystemItem.VideoFile(
      name = "song.mp3",
      path = "/storage/song.mp3",
      lastModified = 0L,
      video = xyz.mpv.rex.domain.media.model.Video(
        id = 2L,
        title = "song",
        displayName = "song.mp3",
        path = "/storage/song.mp3",
        uri = mockk<android.net.Uri>(relaxed = true),
        duration = 1000L,
        durationFormatted = "00:01",
        size = 500L,
        sizeFormatted = "500B",
        dateModified = 0L,
        dateAdded = 0L,
        mimeType = "audio/mp3",
        bucketId = "storage",
        bucketDisplayName = "storage",
        width = 0,
        height = 0,
        rotation = 0,
        fps = 0f,
        resolution = "",
        isAudio = true,
      ),
    )
    val mixedFolder = FileSystemItem.Folder(
      name = "Mixed",
      path = "/storage/Mixed",
      lastModified = 0L,
      videoCount = 1,
      audioCount = 1,
      totalSize = 1500L,
    )
    val audioOnlyFolder = FileSystemItem.Folder(
      name = "AudioOnly",
      path = "/storage/AudioOnly",
      lastModified = 0L,
      videoCount = 0,
      audioCount = 2,
      totalSize = 1000L,
    )

    val allItems = listOf(videoFile, audioFile, mixedFolder, audioOnlyFolder)

    fun filterVisible(items: List<FileSystemItem>, showAudio: Boolean): List<FileSystemItem> {
      return if (!showAudio) {
        items.filterNot {
          (it is FileSystemItem.VideoFile && it.video.isAudio) ||
          (it is FileSystemItem.Folder && it.videoCount == 0)
        }
      } else {
        items
      }
    }

    val visibleWhenAudioHidden = filterVisible(allItems, showAudio = false)
    assertEquals(listOf(videoFile, mixedFolder), visibleWhenAudioHidden)

    val visibleWhenAudioShown = filterVisible(allItems, showAudio = true)
    assertEquals(allItems, visibleWhenAudioShown)
  }

  @Test
  fun fileSystemItem_storageRootsPreserved_whenAudioHiddenAndVideoCountZero() {
    val internalStorageRoot = FileSystemItem.Folder(
      name = "Internal Storage",
      path = "/storage/emulated/0",
      lastModified = 0L,
      videoCount = 0,
      audioCount = 5,
      videoSize = 0L,
      audioSize = 50_000_000L,
      totalSize = 50_000_000L,
    )
    val sdCardRoot = FileSystemItem.Folder(
      name = "SD Card",
      path = "/storage/1234-5678",
      lastModified = 0L,
      videoCount = 0,
      audioCount = 0,
      videoSize = 0L,
      audioSize = 0L,
      totalSize = 0L,
    )
    val audioOnlyFolder = FileSystemItem.Folder(
      name = "Podcasts",
      path = "/storage/emulated/0/Podcasts",
      lastModified = 0L,
      videoCount = 0,
      audioCount = 3,
      videoSize = 0L,
      audioSize = 30_000_000L,
      totalSize = 30_000_000L,
    )

    fun filterVisible(
      items: List<FileSystemItem>,
      currentPath: String,
      showAudio: Boolean,
    ): List<FileSystemItem> {
      return if (!showAudio) {
        items.filterNot {
          (it is FileSystemItem.VideoFile && it.video.isAudio) ||
          (currentPath != FileSystemBrowserViewModel.STORAGE_ROOTS_MARKER &&
            it is FileSystemItem.Folder && it.videoCount == 0)
        }
      } else {
        items
      }
    }

    // Storage roots at STORAGE_ROOTS_MARKER must NEVER be filtered out even when videoCount == 0
    val roots = listOf(internalStorageRoot, sdCardRoot)
    val rootsVisibleWhenAudioHidden = filterVisible(
      items = roots,
      currentPath = FileSystemBrowserViewModel.STORAGE_ROOTS_MARKER,
      showAudio = false,
    )
    assertEquals(roots, rootsVisibleWhenAudioHidden)

    val rootsVisibleWhenAudioShown = filterVisible(
      items = roots,
      currentPath = FileSystemBrowserViewModel.STORAGE_ROOTS_MARKER,
      showAudio = true,
    )
    assertEquals(roots, rootsVisibleWhenAudioShown)

    // Standard folder with videoCount == 0 must be filtered out when showAudio is false
    val subItems = listOf(audioOnlyFolder)
    val subItemsVisibleWhenAudioHidden = filterVisible(
      items = subItems,
      currentPath = "/storage/emulated/0",
      showAudio = false,
    )
    assertEquals(emptyList<FileSystemItem>(), subItemsVisibleWhenAudioHidden)

    val subItemsVisibleWhenAudioShown = filterVisible(
      items = subItems,
      currentPath = "/storage/emulated/0",
      showAudio = true,
    )
    assertEquals(subItems, subItemsVisibleWhenAudioShown)
  }

  @Test
  fun metadataRetrieval_enrichFoldersIfNeeded_identifiesMixedFolderNeedingVideoDuration() = runTest {
    val context = mockk<Context>(relaxed = true)
    val preferences = mockk<BrowserPreferences>(relaxed = true)
    val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
    every { preferences.showTotalDurationChip.get() } returns true

    // Mixed folder: has audioDuration > 0, totalDuration > 0, but videoDuration == 0L and videoCount > 0
    val mixedFolder = VideoFolder(
      bucketId = "1",
      name = "Mixed",
      path = "/nonexistent/path/Mixed",
      videoCount = 1,
      audioCount = 1,
      videoDuration = 0L,
      audioDuration = 10_000L,
      totalDuration = 10_000L,
    )
    // Already enriched video folder
    val alreadyEnriched = VideoFolder(
      bucketId = "2",
      name = "Enriched",
      path = "/nonexistent/path/Enriched",
      videoCount = 1,
      audioCount = 0,
      videoDuration = 20_000L,
      audioDuration = 0L,
      totalDuration = 20_000L,
    )
    // Audio-only folder (videoCount == 0)
    val audioOnly = VideoFolder(
      bucketId = "3",
      name = "AudioOnly",
      path = "/nonexistent/path/AudioOnly",
      videoCount = 0,
      audioCount = 2,
      videoDuration = 0L,
      audioDuration = 15_000L,
      totalDuration = 15_000L,
    )

    var progressCalls = 0
    val result = MetadataRetrieval.enrichFoldersIfNeeded(
      context = context,
      folders = listOf(mixedFolder, alreadyEnriched, audioOnly),
      browserPreferences = preferences,
      metadataCache = metadataCache,
      onProgress = { processed, total ->
        progressCalls++
        assertEquals(1, total) // Only mixedFolder should be processed!
      }
    )

    assertEquals(1, progressCalls)
    assertEquals(3, result.size)
  }

  @Test
  fun metadataRetrieval_enrichFolderIfNeeded_extractsDurationAndPreservesAudioDuration() = runTest {
    val tempFolder = Files.createTempDirectory("enrich-test").toFile()
    try {
      val videoFile = File(tempFolder, "clip.mp4").apply { writeText("dummy content") }
      val context = mockk<Context>(relaxed = true)
      val preferences = mockk<BrowserPreferences>(relaxed = true)
      val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
      every { preferences.showTotalDurationChip.get() } returns true

      val mockMeta = MediaInfoOps.VideoMetadata(
        sizeBytes = 1_000L,
        durationMs = 45_000L,
        width = 1920,
        height = 1080,
        fps = 30f,
        hasEmbeddedSubtitles = false,
      )
      coEvery { metadataCache.getOrExtractMetadataBatch(any()) } returns mapOf(videoFile.absolutePath to mockMeta)

      val mixedFolder = VideoFolder(
        bucketId = "1",
        name = "Mixed",
        path = tempFolder.absolutePath,
        videoCount = 1,
        audioCount = 1,
        videoDuration = 0L,
        audioDuration = 15_000L,
        totalDuration = 15_000L,
      )

      val enriched = MetadataRetrieval.enrichFolderIfNeeded(
        context = context,
        folder = mixedFolder,
        browserPreferences = preferences,
        metadataCache = metadataCache,
      )

      assertEquals(45_000L, enriched.videoDuration)
      assertEquals(15_000L, enriched.audioDuration)
      assertEquals(60_000L, enriched.totalDuration)
      assertEquals(45_000L, enriched.activeDuration(showAudioFiles = false))
      assertEquals(60_000L, enriched.activeDuration(showAudioFiles = true))
    } finally {
      tempFolder.deleteRecursively()
    }
  }

  @Test
  fun metadataRetrieval_enrichFolderIfNeeded_earlyReturnsWhenNoVideoOrAlreadyEnriched() = runTest {
    val context = mockk<Context>(relaxed = true)
    val preferences = mockk<BrowserPreferences>(relaxed = true)
    val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
    every { preferences.showTotalDurationChip.get() } returns true

    // Audio-only folder (videoCount == 0)
    val audioOnly = VideoFolder(
      bucketId = "1",
      name = "AudioOnly",
      path = "/nonexistent/path/AudioOnly",
      videoCount = 0,
      audioCount = 2,
      videoDuration = 0L,
      audioDuration = 20_000L,
      totalDuration = 20_000L,
    )
    val resultAudioOnly = MetadataRetrieval.enrichFolderIfNeeded(
      context = context,
      folder = audioOnly,
      browserPreferences = preferences,
      metadataCache = metadataCache,
    )
    assertEquals(audioOnly, resultAudioOnly)

    // Already enriched folder (videoDuration > 0L)
    val alreadyEnriched = VideoFolder(
      bucketId = "2",
      name = "AlreadyEnriched",
      path = "/nonexistent/path/AlreadyEnriched",
      videoCount = 1,
      audioCount = 1,
      videoDuration = 30_000L,
      audioDuration = 10_000L,
      totalDuration = 40_000L,
    )
    val resultAlreadyEnriched = MetadataRetrieval.enrichFolderIfNeeded(
      context = context,
      folder = alreadyEnriched,
      browserPreferences = preferences,
      metadataCache = metadataCache,
    )
    assertEquals(alreadyEnriched, resultAlreadyEnriched)
  }
}
