package xyz.mpv.rex.utils.storage

import android.content.Context
import android.os.storage.StorageVolume
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import xyz.mpv.rex.preferences.BrowserPreferences

class CoreMediaScannerTest {
  private lateinit var tempDir: File
  private val context = mockk<Context>(relaxed = true)
  private val browserPreferences = mockk<BrowserPreferences>(relaxed = true)
  private val mockVolume = mockk<StorageVolume>(relaxed = true)

  @Before
  fun setUp() {
    tempDir = Files.createTempDirectory("core-media-scanner-test").toFile()
    every { browserPreferences.watchedThreshold.get() } returns 95
    every { browserPreferences.includeNoMediaContent.get() } returns false
    every { browserPreferences.flattenFolders.get() } returns true

    mockkObject(StorageVolumeUtils)
    every { StorageVolumeUtils.getExternalStorageVolumes(any()) } returns listOf(mockVolume)
    every { StorageVolumeUtils.getAllStorageVolumes(any()) } returns listOf(mockVolume)
    every { StorageVolumeUtils.getVolumePath(mockVolume) } returns tempDir.absolutePath

    startKoin {
      modules(
        module {
          single { browserPreferences }
          single<FolderAggregateSource> { FakeFolderAggregateSource() }
          single { MediaTreeStore(context, get()) }
        },
      )
    }
    CoreMediaScanner.clearCache()
  }

  @After
  fun tearDown() {
    stopKoin()
    unmockkObject(StorageVolumeUtils)
    CoreMediaScanner.clearCache()
    tempDir.deleteRecursively()
  }

  @Test
  fun flatFolders_whenShowAudioFilesFalse_excludesAudioFromNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val musicFolder = File(tempDir, "Music").apply { mkdirs() }
    val mixedFolder = File(tempDir, "Mixed").apply { mkdirs() }

    File(musicFolder, "song.mp3").apply { writeText("audio") }
    File(mixedFolder, "clip.mp4").apply { writeText("video") }
    File(mixedFolder, "track.mp3").apply { writeText("audio") }

    val folders = CoreMediaScanner.getFlatMediaFolders(context)

    val music = folders.first { it.path == musicFolder.absolutePath }
    assertEquals(1, music.audioCount)
    assertEquals(0, music.videoCount)
    assertEquals(0, music.newCount)
    assertEquals(0, music.unwatchedVideoCount)

    val mixed = folders.first { it.path == mixedFolder.absolutePath }
    assertEquals(1, mixed.audioCount)
    assertEquals(1, mixed.videoCount)
    assertEquals(1, mixed.newCount)
    assertEquals(1, mixed.unwatchedVideoCount)
  }

  @Test
  fun flatFolders_whenShowAudioFilesTrue_includesAudioInNewAndUnwatchedCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val musicFolder = File(tempDir, "Music").apply { mkdirs() }
    val mixedFolder = File(tempDir, "Mixed").apply { mkdirs() }

    File(musicFolder, "song.mp3").apply { writeText("audio") }
    File(mixedFolder, "clip.mp4").apply { writeText("video") }
    File(mixedFolder, "track.mp3").apply { writeText("audio") }

    val folders = CoreMediaScanner.getFlatMediaFolders(context)

    val music = folders.first { it.path == musicFolder.absolutePath }
    assertEquals(1, music.audioCount)
    assertEquals(0, music.videoCount)
    assertEquals(0L, music.videoSize)
    assertEquals(File(musicFolder, "song.mp3").length(), music.audioSize)
    assertEquals(File(musicFolder, "song.mp3").length(), music.totalSize)
    assertEquals(0L, music.activeSize(showAudioFiles = false))
    assertEquals(File(musicFolder, "song.mp3").length(), music.activeSize(showAudioFiles = true))
    assertEquals(0, music.activeCount(showAudioFiles = false))
    assertEquals(1, music.activeCount(showAudioFiles = true))
    assertEquals(1, music.newCount)
    assertEquals(1, music.unwatchedVideoCount)

    val mixed = folders.first { it.path == mixedFolder.absolutePath }
    assertEquals(1, mixed.audioCount)
    assertEquals(1, mixed.videoCount)
    val videoLen = File(mixedFolder, "clip.mp4").length()
    val audioLen = File(mixedFolder, "track.mp3").length()
    assertEquals(videoLen, mixed.videoSize)
    assertEquals(audioLen, mixed.audioSize)
    assertEquals(videoLen + audioLen, mixed.totalSize)
    assertEquals(videoLen, mixed.activeSize(showAudioFiles = false))
    assertEquals(videoLen + audioLen, mixed.activeSize(showAudioFiles = true))
    assertEquals(1, mixed.activeCount(showAudioFiles = false))
    assertEquals(2, mixed.activeCount(showAudioFiles = true))
    assertEquals(2, mixed.newCount)
    assertEquals(2, mixed.unwatchedVideoCount)
  }

  @Test
  fun foldersInDirectory_whenShowAudioFilesFalse_excludesAudioFromHierarchyCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val parentFolder = File(tempDir, "Media").apply { mkdirs() }
    val subFolder = File(parentFolder, "Sub").apply { mkdirs() }

    File(parentFolder, "clip.mp4").apply { writeText("video") }
    File(subFolder, "song.mp3").apply { writeText("audio") }

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    val mediaNode = folders.firstOrNull { it.path == parentFolder.absolutePath }

    assertNotNull(mediaNode)
    assertEquals(1, mediaNode!!.audioCount)
    assertEquals(1, mediaNode.videoCount)
    val videoLen = File(parentFolder, "clip.mp4").length()
    val audioLen = File(subFolder, "song.mp3").length()
    assertEquals(videoLen, mediaNode.videoSize)
    assertEquals(audioLen, mediaNode.audioSize)
    assertEquals(videoLen + audioLen, mediaNode.totalSize)
    assertEquals(videoLen, mediaNode.activeSize(showAudioFiles = false))
    assertEquals(videoLen + audioLen, mediaNode.activeSize(showAudioFiles = true))
    assertEquals(1, mediaNode.activeCount(showAudioFiles = false))
    assertEquals(2, mediaNode.activeCount(showAudioFiles = true))
    assertEquals(1, mediaNode.newCount)
    assertEquals(1, mediaNode.unwatchedVideoCount)
  }

  @Test
  fun foldersInDirectory_whenShowAudioFilesTrue_includesAudioInHierarchyCounts() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val parentFolder = File(tempDir, "Media").apply { mkdirs() }
    val subFolder = File(parentFolder, "Sub").apply { mkdirs() }

    File(parentFolder, "clip.mp4").apply { writeText("video") }
    File(subFolder, "song.mp3").apply { writeText("audio") }

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    val mediaNode = folders.firstOrNull { it.path == parentFolder.absolutePath }

    assertNotNull(mediaNode)
    assertEquals(1, mediaNode!!.audioCount)
    assertEquals(1, mediaNode.videoCount)
    val videoLen = File(parentFolder, "clip.mp4").length()
    val audioLen = File(subFolder, "song.mp3").length()
    assertEquals(videoLen, mediaNode.videoSize)
    assertEquals(audioLen, mediaNode.audioSize)
    assertEquals(videoLen + audioLen, mediaNode.totalSize)
    assertEquals(videoLen, mediaNode.activeSize(showAudioFiles = false))
    assertEquals(videoLen + audioLen, mediaNode.activeSize(showAudioFiles = true))
    assertEquals(1, mediaNode.activeCount(showAudioFiles = false))
    assertEquals(2, mediaNode.activeCount(showAudioFiles = true))
    assertEquals(2, mediaNode.newCount)
    assertEquals(2, mediaNode.unwatchedVideoCount)
  }

  @Test
  fun cachedMediaData_invalidatesWhenShowAudioFilesPreferenceChanges() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val musicFolder = File(tempDir, "Music").apply { mkdirs() }
    File(musicFolder, "song.mp3").apply { writeText("audio") }

    val initialFolders = CoreMediaScanner.getFlatMediaFolders(context)
    val initialMusic = initialFolders.first { it.path == musicFolder.absolutePath }
    assertEquals(0, initialMusic.newCount)
    assertEquals(0, initialMusic.unwatchedVideoCount)

    every { browserPreferences.showAudioFiles.get() } returns true

    val updatedFolders = CoreMediaScanner.getFlatMediaFolders(context)
    val updatedMusic = updatedFolders.first { it.path == musicFolder.absolutePath }
    assertEquals(1, updatedMusic.newCount)
    assertEquals(1, updatedMusic.unwatchedVideoCount)
  }

  @Test
  fun folderRecursiveData_populatesUnwatchedVideoCount() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val mediaFolder = File(tempDir, "Media").apply { mkdirs() }
    val subFolder = File(mediaFolder, "Sub").apply { mkdirs() }

    File(mediaFolder, "clip.mp4").apply { writeText("video") }
    File(subFolder, "song.mp3").apply { writeText("audio") }

    val folderData = CoreMediaScanner.getFolderRecursiveData(context, path = mediaFolder.absolutePath)
    assertNotNull(folderData)
    assertEquals(2, folderData!!.unwatchedVideoCount)

    CoreMediaScanner.clearCache()
    every { browserPreferences.showAudioFiles.get() } returns false

    val folderDataAudioFalse = CoreMediaScanner.getFolderRecursiveData(context, path = mediaFolder.absolutePath)
    assertNotNull(folderDataAudioFalse)
    assertEquals(1, folderDataAudioFalse!!.unwatchedVideoCount)
  }

  @Test
  fun foldersInDirectory_intermediateFlattening_respectsShowAudioFiles() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val downloads = File(tempDir, "Downloads").apply { mkdirs() }
    val videoFolder = File(downloads, "Video").apply { mkdirs() }
    val musicFolder = File(downloads, "Music").apply { mkdirs() }

    File(videoFolder, "clip.mp4").apply { writeText("video") }
    File(musicFolder, "song.mp3").apply { writeText("audio") }

    // With flattening enabled and showAudioFiles=false, Downloads only leads to Video, so it collapses to Video.
    val foldersAudioFalse = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Video"), foldersAudioFalse.map { it.name })

    CoreMediaScanner.clearCache()
    every { browserPreferences.showAudioFiles.get() } returns true

    // With showAudioFiles=true, Downloads leads to both Video and Music, so Downloads does not collapse.
    val foldersAudioTrue = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Downloads"), foldersAudioTrue.map { it.name })
    assertEquals(1, foldersAudioTrue.single().audioCount)
  }

  @Test
  fun foldersInDirectory_intermediateFlattening_folderWithOnlyDirectAudioFlattenedWhenAudioDisabled() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val downloads = File(tempDir, "Downloads").apply { mkdirs() }
    val videoFolder = File(downloads, "Video").apply { mkdirs() }

    File(downloads, "song.mp3").apply { writeText("audio") }
    File(videoFolder, "clip.mp4").apply { writeText("video") }

    // With audio disabled, Downloads only leads to Video, so it collapses to Video.
    val foldersAudioFalse = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Video"), foldersAudioFalse.map { it.name })

    CoreMediaScanner.clearCache()
    every { browserPreferences.showAudioFiles.get() } returns true

    // With audio enabled, Downloads has direct audio, so it does not collapse.
    val foldersAudioTrue = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Downloads"), foldersAudioTrue.map { it.name })
  }

  /**
   * Mirrors the reported scenario: `Download/Flud` holds media directly, and
   * `Download/[Judas] JoJo S4` holds more media one level deeper. When only the JoJo
   * branch is indexed by MediaStore, flattening used to promote it and hide `Flud`.
   */
  @Test
  fun foldersInDirectory_unindexedFolderWithDirectMediaIsNotHiddenByFlattening() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val flud = File(download, "Flud").apply { mkdirs() }
    val jojo = File(download, "[Judas] JoJo S4 - Golden Wind").apply { mkdirs() }

    File(flud, "Steel Ball Run S06E07.mkv").apply { writeText("video") }
    File(flud, "subs.srt").apply { writeText("subtitle") }
    File(jojo, "S04E20.mkv").apply { writeText("video") }

    // MediaStore only knows about the JoJo file; Flud's media is on disk but not indexed yet.
    mockMediaStoreRows(videoFiles = listOf(File(jojo, "S04E20.mkv")))

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)

    // Download has media both directly (Flud) and through a deeper branch, so it stays visible
    // and neither of its children is promoted in its place.
    assertEquals(listOf("Download"), folders.map { it.name })
    assertEquals(2, folders.single().videoCount)
    assertTrue(folders.single().hasSubfolders)
  }

  @Test
  fun foldersInDirectory_unindexedFolderBesideIndexedOneIsDiscoveredFromFileSystem() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val flud = File(download, "Flud").apply { mkdirs() }
    val jojo = File(download, "[Judas] JoJo S4 - Golden Wind").apply { mkdirs() }
    File(flud, "clip.mkv").apply { writeText("video") }
    File(jojo, "S04E20.mkv").apply { writeText("video") }

    // MediaStore has indexed a sibling but not the freshly downloaded folder yet.
    mockMediaStoreRows(videoFiles = listOf(File(jojo, "S04E20.mkv")))

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    val downloadFolder = folders.single { it.name == "Download" }

    assertEquals(2, downloadFolder.videoCount)
    assertEquals(2, downloadFolder.unwatchedVideoCount)
    assertTrue(downloadFolder.hasSubfolders)
  }

  @Test
  fun foldersInDirectory_genuineSingleBranchChainIsStillFlattened() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val downloads = File(tempDir, "Downloads").apply { mkdirs() }
    val anime = File(downloads, "Anime").apply { mkdirs() }
    File(anime, "ep1.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(anime, "ep1.mkv")))

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)

    // A real one-child chain is collapsed, and the leaf keeps its media counts.
    assertEquals(listOf("Anime"), folders.map { it.name })
    assertEquals(1, folders.single().videoCount)
  }

  @Test
  fun foldersInDirectory_indexesOnlyTheOpenedBranch() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val nested = File(download, "Nested").apply { mkdirs() }
    File(nested, "clip.mkv").apply { writeText("video") }

    // 50 unrelated sibling folders, each holding media.
    val unrelated = (1..50).map { index ->
      File(tempDir, "Unrelated$index").apply { mkdirs() }.also {
        File(it, "movie$index.mkv").apply { writeText("video") }
      }
    }

    CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)

    // The index is the observable artifact: opening Download must leave no row for its siblings.
    val stored = org.koin.core.context.GlobalContext.get()
      .get<FolderAggregateSource>()
      .loadAll()
      .map { it.path }

    val downloadRoot = folderKey(download.absolutePath)
    val foreign = stored.filterNot { it == downloadRoot || it.startsWith("$downloadRoot/") }
    assertTrue("Opening Download indexed rows outside its branch: $foreign", foreign.isEmpty())
    assertTrue("The opened branch itself must be indexed", stored.contains(folderKey(nested.absolutePath)))
    assertEquals(
      "Siblings must not appear in the listing",
      listOf("Nested"),
      CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath).map { it.name },
    )
    assertTrue(unrelated.isNotEmpty())
  }

  /**
   * Walking down the tree the way the UI does: open the root, then open a folder it just
   * listed. Every level must keep showing its subfolders, otherwise the tree goes blank as
   * soon as the user navigates into a folder.
   */
  @Test
  fun foldersInDirectory_survivesWalkingDownTheTree() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val anime = File(download, "Anime").apply { mkdirs() }
    File(anime, "ep1.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(anime, "ep1.mkv")))

    val level1 = CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)
    assertEquals("Download must list Anime", listOf("Anime"), level1.map { it.name })

    // Now open Anime, exactly as tapping it would.
    val level2 = CoreMediaScanner.getFoldersInDirectory(context, parentPath = anime.absolutePath)
    val stored = org.koin.core.context.GlobalContext.get()
      .get<FolderAggregateSource>()
      .loadAll()

    // ep1.mkv is a direct file of Anime, so Anime legitimately has no subfolders to show. What
    // must not happen is the folder disappearing from its parent's listing.
    assertTrue("Anime must still be stored", stored.any { it.path == folderKey(anime.absolutePath) })
    assertTrue(level2.isEmpty())

    val reopened = CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)
    assertEquals("Download must still list Anime after visiting it", listOf("Anime"), reopened.map { it.name })
  }

  @Test
  fun foldersInDirectory_nestedFoldersStayVisibleWhenReopeningTheRoot() = runTest {
    every { browserPreferences.showAudioFiles.get()} returns true
    every { browserPreferences.flattenFolders.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val anime = File(download, "Anime").apply { mkdirs() }
    val season = File(anime, "Season 1").apply { mkdirs() }
    File(season, "ep1.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(season, "ep1.mkv")))

    CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)

    // Opening Anime lists Season 1, which must carry its recursive count.
    val animeLevel = CoreMediaScanner.getFoldersInDirectory(context, parentPath = anime.absolutePath)
    assertEquals(listOf("Season 1"), animeLevel.map { it.name })
    assertEquals(1, animeLevel.single().videoCount)

    // Under Download, Anime only leads to Season 1, so it collapses to Season 1.
    val rootLevel = CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)
    assertEquals(listOf("Season 1"), rootLevel.map { it.name })
    assertEquals(1, rootLevel.single().videoCount)
  }

  /**
   * The launch scenario: the app opens the storage root directly and must list its folders.
   * `MediaFileRepository.scanDirectory` hides folders whose recursive `videoCount` is 0, so this
   * also covers the filter that decides whether anything at all is shown.
   */
  @Test
  fun foldersInDirectory_listsFoldersAtTheStorageRoot() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false

    val download = File(tempDir, "Download").apply { mkdirs() }
    val anime = File(download, "Anime").apply { mkdirs() }
    File(anime, "ep1.mkv").apply { writeText("video") }
    val movies = File(tempDir, "Movies").apply { mkdirs() }
    File(movies, "film.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(anime, "ep1.mkv"), File(movies, "film.mkv")))

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)

    val visible = folders.filter { it.videoCount > 0 }
    assertTrue(
      "Launching into the storage root showed nothing; listing was: " +
        folders.joinToString { "${it.name}(videos=${it.videoCount})" },
      visible.isNotEmpty(),
    )
  }

  /**
   * Option 2: When flattenFolders is enabled, single-child chains without direct media
   * collapse directly to the media folder (e.g. Download -> Anime). When disabled,
   * the full hierarchy is preserved.
   */
  @Test
  fun foldersInDirectory_singleChildChainFlattenedOrPreservedBasedOnPreference() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false
    every { browserPreferences.flattenFolders.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val anime = File(download, "Anime").apply { mkdirs() }
    File(anime, "ep1.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(anime, "ep1.mkv")))

    val flattenedFolders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)

    assertEquals(
      "When flattenFolders is true, single-child Download collapses into Anime",
      listOf("Anime"),
      flattenedFolders.map { it.name },
    )

    CoreMediaScanner.clearCache()
    every { browserPreferences.flattenFolders.get() } returns false

    val unflattenedFolders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)

    assertEquals(
      "When flattenFolders is false, Download stays unflattened",
      listOf("Download"),
      unflattenedFolders.map { it.name },
    )
  }

  @Test
  fun foldersInDirectory_stillReadsTheOpenedBranch() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns true

    val download = File(tempDir, "Download").apply { mkdirs() }
    val nested = File(download, "Nested").apply { mkdirs() }
    File(nested, "clip.mkv").apply { writeText("video") }

    mockMediaStoreRows(videoFiles = listOf(File(nested, "clip.mkv")))

    val folders = CoreMediaScanner.getFoldersInDirectory(context, parentPath = download.absolutePath)

    assertEquals(listOf("Nested"), folders.map { it.name })
    assertEquals(1, folders.single().videoCount)
  }

  @Test
  fun foldersInDirectory_hidesBlacklistedFolderImmediately() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false
    every { browserPreferences.flattenFolders.get() } returns false

    val movies = File(tempDir, "Movies").apply { mkdirs() }
    File(movies, "film.mkv").apply { writeText("video") }
    mockMediaStoreRows(videoFiles = listOf(File(movies, "film.mkv")))

    val before = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Movies"), before.map { it.name })

    val after = CoreMediaScanner.getFoldersInDirectory(
      context = context,
      parentPath = tempDir.absolutePath,
      blacklistedFolders = setOf(movies.absolutePath),
    )
    assertTrue("Blacklisted folder must disappear on the next listing", after.isEmpty())

    // Lifting the blacklist must bring the persisted folder back without a rescan.
    val restored = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Movies"), restored.map { it.name })
  }

  @Test
  fun foldersInDirectory_hidesBlacklistedParentEvenWhenMediaIsInChildFolders() = runTest {
    every { browserPreferences.showAudioFiles.get() } returns false
    every { browserPreferences.flattenFolders.get() } returns false

    val download = File(tempDir, "Download").apply { mkdirs() }
    val movies = File(download, "Movies").apply { mkdirs() }
    File(movies, "film.mkv").apply { writeText("video") }
    mockMediaStoreRows(videoFiles = listOf(File(movies, "film.mkv")))

    val before = CoreMediaScanner.getFoldersInDirectory(context, parentPath = tempDir.absolutePath)
    assertEquals(listOf("Download"), before.map { it.name })

    // The media is one level below Download, so an exact-path check would leave it visible.
    val after = CoreMediaScanner.getFoldersInDirectory(
      context = context,
      parentPath = tempDir.absolutePath,
      blacklistedFolders = setOf(download.absolutePath),
    )
    assertTrue("Blacklisting a parent must hide its whole subtree", after.isEmpty())
  }

  /**
   * Answers the MediaStore video/audio queries with exactly [videoFiles]/[audioFiles].
   */
  private fun mockMediaStoreRows(
    videoFiles: List<File>,
    audioFiles: List<File> = emptyList(),
  ) {
    val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val audioUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    every { context.contentResolver.query(videoUri, any(), any(), any(), any()) } answers {
      mediaCursor(videoFiles.filter { it.absolutePath.startsWith(likePrefix(arg<Array<String>>(3))) })
    }
    every { context.contentResolver.query(audioUri, any(), any(), any(), any()) } answers {
      mediaCursor(audioFiles.filter { it.absolutePath.startsWith(likePrefix(arg<Array<String>>(3))) })
    }
  }

  @Test
  fun databaseModule_bindsFolderAggregateSource() {
    val koinApp = org.koin.dsl.koinApplication {
      modules(
        org.koin.dsl.module {
          single { context }
          single { mockk<xyz.mpv.rex.database.MpvExDatabase>(relaxed = true) }
        },
        xyz.mpv.rex.di.DatabaseModule,
      )
    }
    val store = koinApp.koin.getOrNull<MediaTreeStore>()
    assertNotNull("MediaTreeStore must be resolvable from DatabaseModule", store)
    val source = koinApp.koin.getOrNull<FolderAggregateSource>()
    assertNotNull("FolderAggregateSource must be resolvable from DatabaseModule", source)
    koinApp.close()
  }

  /**
   * The scanner narrows its query to one directory with a `DATA LIKE` prefix argument. The fake
   * cursor has no SQL engine, so apply that one prefix filter here instead.
   */
  private fun likePrefix(args: Array<String>?): String {
    val raw = args?.getOrNull(1) ?: return ""
    return raw.removeSuffix("%").replace("\\", "")
  }

  private fun mediaCursor(files: List<File>): android.database.Cursor {
    val cursor = mockk<android.database.Cursor>(relaxed = true)
    val names = listOf("_display_name", "_data", "_size", "duration", "date_modified")
    every { cursor.getColumnIndexOrThrow(any()) } answers {
      names.indexOf(firstArg<String>()).takeIf { it >= 0 } ?: 0
    }
    var index = -1
    every { cursor.moveToNext() } answers { ++index < files.size }
    every { cursor.getString(any()) } answers {
      val file = files[index]
      when (firstArg<Int>()) {
        0 -> file.name
        1 -> file.absolutePath
        else -> ""
      }
    }
    every { cursor.getLong(any()) } answers {
      val file = files[index]
      when (firstArg<Int>()) {
        2 -> file.length()
        4 -> file.lastModified() / 1000
        else -> 0L
      }
    }
    every { cursor.close() } returns Unit
    return cursor
  }
}

