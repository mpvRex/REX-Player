package xyz.mpv.rex.repository

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.storage.VideoScanUtils
import java.io.File

class SubtitleIndicatorMergeTest {

  @Test
  fun discovers_exact_matching_external_subtitles_case_insensitively() {
    val files = listOf(
      File("/tmp/subtitle-test/Movie.MP4"),
      File("/tmp/subtitle-test/movie.SRT"),
      File("/tmp/subtitle-test/MOVIE.ass"),
      File("/tmp/subtitle-test/Movie-English.SRT"),
    )

    val result = VideoScanUtils.buildMatchingExternalSubtitleFormats(files)

    assertEquals(listOf("ASS", "SRT"), result["movie"])
    assertEquals(listOf("SRT"), result["movie-english"])

    // The lookup performed for a video named "Movie.MP4" uses the exact base-name key,
    // so "Movie-English.SRT" is not treated as a match.
    assertEquals(listOf("ASS", "SRT"), result["movie"])
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
  fun embedded_and_external_can_share_the_same_format() {
    val resolved = video(
      hasEmbeddedSubtitles = true,
      subtitleCodec = "SRT",
      matchingExternalSubtitleFormats = listOf("SRT"),
      subtitleIndicatorReady = true,
    )

    assertTrue(resolved.hasEmbeddedSubtitles)
    assertEquals("SRT", resolved.subtitleCodec)
    assertEquals(listOf("SRT"), resolved.matchingExternalSubtitleFormats)
  }

  @Test
  fun both_sources_are_ready_in_one_video_state() {
    val unresolved = video(
      hasEmbeddedSubtitles = true,
      subtitleCodec = "PGS",
      matchingExternalSubtitleFormats = listOf("SRT"),
      subtitleIndicatorReady = false,
    )
    assertFalse(unresolved.subtitleIndicatorReady)

    val resolved = unresolved.copy(subtitleIndicatorReady = true)
    assertTrue(resolved.subtitleIndicatorReady)
    assertEquals("PGS", resolved.subtitleCodec)
    assertEquals(listOf("SRT"), resolved.matchingExternalSubtitleFormats)
  }

  private fun video(
    hasEmbeddedSubtitles: Boolean = false,
    subtitleCodec: String = "",
    matchingExternalSubtitleFormats: List<String> = emptyList(),
    subtitleIndicatorReady: Boolean = false,
  ): Video = Video(
    id = 1L,
    title = "lala",
    displayName = "lala.mkv",
    path = "/tmp/subtitle-test/lala.mkv",
    uri = Uri.parse("file:///tmp/subtitle-test/lala.mkv"),
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
