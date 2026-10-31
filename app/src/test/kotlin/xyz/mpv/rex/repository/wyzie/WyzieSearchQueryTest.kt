package xyz.mpv.rex.repository.wyzie

import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.mpv.rex.preferences.SubtitlesPreferences
import kotlinx.serialization.json.Json

class WyzieSearchQueryTest {
  private fun repo(): WyzieSearchRepository =
    WyzieSearchRepository(
      context = mockk(),
      client = OkHttpClient(),
      json = Json { ignoreUnknownKeys = true },
      preferences = mockk<SubtitlesPreferences>(),
    )

  @Test
  fun `strips release noise from a movie file name`() {
    val result = repo().normalizeSearchQuery("The.Dark.Knight.2008.1080p.BluRay.x264-GROUP.mkv")
    assertEquals("The Dark Knight", result)
  }

  @Test
  fun `strips season and episode markers`() {
    val result = repo().normalizeSearchQuery("Breaking.Bad.S05E16.720p.WEB-DL.mkv")
    assertEquals("Breaking Bad", result)
  }

  @Test
  fun `most searched test 1`() {
    assertEquals("Dexter", repo().normalizeSearchQuery("Dexter.S01E02.1080p.BluRay.mkv"))
  }

  @Test
  fun `placeholder titles are treated as no query`() {
    assertEquals("", repo().normalizeSearchQuery("Unknown Video"))
    assertEquals("", repo().normalizeSearchQuery("Network Stream"))
    assertEquals("", repo().normalizeSearchQuery("   "))
  }

  @Test
  fun `exact title beats a more popular remote match`() {
    val results = listOf(
      WyzieTmdbResult(id = 1, mediaType = "movie", title = "Dune: Part Two", releaseYear = "2024"),
      WyzieTmdbResult(id = 2, mediaType = "movie", title = "Dune", releaseYear = "2021"),
    )
    assertEquals(2, repo().pickTmdbMatch(results, "Dune", null)?.id)
  }

  @Test
  fun `year breaks a tie between identically named releases`() {
    val results = listOf(
      WyzieTmdbResult(id = 10, mediaType = "movie", title = "Dune", releaseYear = "1984"),
      WyzieTmdbResult(id = 11, mediaType = "movie", title = "Dune", releaseYear = "2021"),
    )
    assertEquals(11, repo().pickTmdbMatch(results, "Dune", "2021")?.id)
  }

  @Test
  fun `no tmdb results yields null instead of throwing`() {
    assertNull(repo().pickTmdbMatch(emptyList(), "Anything", null))
  }

  @Test
  fun `download file base names are sanitized`() {
    assertEquals("AC_DC - Live", WyzieSearchRepository.sanitizeFileBaseName("AC/DC - Live"))
    assertEquals("What_ A Name", WyzieSearchRepository.sanitizeFileBaseName("What: A Name"))
    assertEquals("subtitle", WyzieSearchRepository.sanitizeFileBaseName("   "))
  }
}
