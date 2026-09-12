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

    mockkObject(StorageVolumeUtils)
    every { StorageVolumeUtils.getExternalStorageVolumes(any()) } returns listOf(mockVolume)
    every { StorageVolumeUtils.getVolumePath(mockVolume) } returns tempDir.absolutePath

    startKoin {
      modules(
        module {
          single { browserPreferences }
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
    assertEquals(1, music.newCount)
    assertEquals(1, music.unwatchedVideoCount)

    val mixed = folders.first { it.path == mixedFolder.absolutePath }
    assertEquals(1, mixed.audioCount)
    assertEquals(1, mixed.videoCount)
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
}
