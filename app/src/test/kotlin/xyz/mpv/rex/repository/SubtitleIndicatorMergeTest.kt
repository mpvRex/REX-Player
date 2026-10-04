package xyz.mpv.rex.repository

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.storage.VideoScanUtils
import java.io.File

class SubtitleIndicatorMergeTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun discovers_exact_matching_external_subtitles_case_insensitively() {
    val files = listOf("Movie.MP4", "movie.SRT", "MOVIE.ass", "Movie-English.SRT")
      .map { tmp.newFile(it) }

    val result = VideoScanUtils.buildMatchingExternalSubtitleFormats(files)

    assertEquals(listOf("ASS", "SRT"), result["movie"])
    assertEquals(listOf("SRT"), result["movie-english"])

    assertEquals(setOf("movie", "movie-english"), result.keys)
  }

  @Test
  fun preserves_direct_external_subtitle_metadata_when_indexed_copy_exists() {
    val direct = video(
      hasEmbeddedSubtitles = true,
      subtitleCodec = "PGS",
      matchingExternalSubtitleFormats = listOf("SRT"),
      subtitleIndicatorReady = true,
    )
    val indexed = video(
      hasEmbeddedSubtitles = true,
      subtitleCodec = "PGS",
      matchingExternalSubtitleFormats = emptyList(),
    )

    val merged = MediaFileRepository.mergeFolderVideos(listOf(direct), listOf(indexed))

    assertEquals(1, merged.size)
    assertEquals("PGS", merged.single().subtitleCodec)
    assertTrue(merged.single().hasEmbeddedSubtitles)
    assertEquals(listOf("SRT"), merged.single().matchingExternalSubtitleFormats)
    assertTrue(merged.single().subtitleIndicatorReady)
  }

  @Test
  fun direct_empty_external_state_clears_stale_indexed_value() {
    val direct = video(matchingExternalSubtitleFormats = emptyList())
    val indexed = video(matchingExternalSubtitleFormats = listOf("SRT"))

    val merged = MediaFileRepository.mergeFolderVideos(listOf(direct), listOf(indexed))

    assertEquals(emptyList<String>(), merged.single().matchingExternalSubtitleFormats)
  }

  @Test
  fun direct_only_and_indexed_only_videos_are_both_kept_and_sorted() {
    val direct = video(name = "b.mkv", matchingExternalSubtitleFormats = listOf("SRT"))
    val indexed = video(name = "A.mkv")

    val merged = MediaFileRepository.mergeFolderVideos(listOf(direct), listOf(indexed))

    assertEquals(listOf("A.mkv", "b.mkv"), merged.map { it.displayName })
    assertEquals(listOf("SRT"), merged[1].matchingExternalSubtitleFormats)
  }

  @Test
  fun embedded_metadata_and_ready_flag_merge_from_either_source() {
    val direct = video(subtitleIndicatorReady = true)
    val indexed = video(hasEmbeddedSubtitles = true, subtitleCodec = "PGS")

    val merged = MediaFileRepository.mergeFolderVideos(listOf(direct), listOf(indexed)).single()

    assertTrue(merged.hasEmbeddedSubtitles)
    assertEquals("PGS", merged.subtitleCodec)
    assertTrue(merged.subtitleIndicatorReady)
  }

  private fun video(
    hasEmbeddedSubtitles: Boolean = false,
    subtitleCodec: String = "",
    matchingExternalSubtitleFormats: List<String> = emptyList(),
    subtitleIndicatorReady: Boolean = false,
    name: String = "lala.mkv",
  ): Video = Video(
    id = 1L,
    title = "lala",
    displayName = name,
    path = "/tmp/subtitle-test/$name",
    uri = mockk<Uri>(relaxed = true),
    duration = 60000L,
    durationFormatted = "01:00",
    size = 100L,
    sizeFormatted = "100 B",
    dateModified = 1L,
    dateAdded = 1L,
    mimeType = "video/x-matroska",
    bucketId = "/tmp/subtitle-test",
    bucketDisplayName = "subtitle-test",
    width = 1920,
    height = 1080,
    fps = 24f,
    resolution = "1920x1080",
    hasEmbeddedSubtitles = hasEmbeddedSubtitles,
    subtitleCodec = subtitleCodec,
    matchingExternalSubtitleFormats = matchingExternalSubtitleFormats,
    subtitleIndicatorReady = subtitleIndicatorReady,
    isAudio = false,
  )
}
