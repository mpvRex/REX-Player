package xyz.mpv.rex.utils.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFeatureTest {

    @Test
    fun parseCommitCount_extractsIntegerFromTags() {
        assertEquals(1010, UpdateManager.parseCommitCount("r1010"))
        assertEquals(1010, UpdateManager.parseCommitCount("R1010"))
        assertEquals(1010, UpdateManager.parseCommitCount("1010"))
        assertEquals(1010, UpdateManager.parseCommitCount("v1010"))
        assertEquals(1010, UpdateManager.parseCommitCount("  r1010  "))
        assertNull(UpdateManager.parseCommitCount("v5.3.0"))
        assertNull(UpdateManager.parseCommitCount("invalid"))
    }

    @Test
    fun isNewerPreviewVersion_comparesCommitCountsCorrectly() {
        // Remote is newer
        assertTrue(UpdateManager.isNewerPreviewVersion("r1015", 1010))
        assertTrue(UpdateManager.isNewerPreviewVersion("1015", 1010))

        // Remote is same
        assertFalse(UpdateManager.isNewerPreviewVersion("r1010", 1010))
        assertFalse(UpdateManager.isNewerPreviewVersion("1010", 1010))

        // Remote is older
        assertFalse(UpdateManager.isNewerPreviewVersion("r1005", 1010))
        assertFalse(UpdateManager.isNewerPreviewVersion("1005", 1010))

        // Current count is 0 (fallback)
        assertFalse(UpdateManager.isNewerPreviewVersion("r1010", 0))
    }

    @Test
    fun parseFileSizeToBytes_convertsUnitsCorrectly() {
        assertEquals(101L * 1024 * 1024, UpdateManager.parseFileSizeToBytes("101M"))
        assertEquals(40L * 1024 * 1024, UpdateManager.parseFileSizeToBytes("40MB"))
        assertEquals(1L * 1024 * 1024 * 1024, UpdateManager.parseFileSizeToBytes("1G"))
        assertEquals(500L * 1024, UpdateManager.parseFileSizeToBytes("500KB"))
        assertEquals(1024L, UpdateManager.parseFileSizeToBytes("1024B"))
        assertEquals(0L, UpdateManager.parseFileSizeToBytes("invalid"))
    }

    @Test
    fun parsePreviewHtml_extractsVersionBuildTimeAndApks() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="version-badge">r1010</div>
                <div class="build-info">
                    Build time: 2026-10-12 13:26 UTC
                </div>
                <div class="download-list">
                    <div class="download-item">
                        <div class="item-size">101M</div>
                        <a href="REX-Player-preview-r1010-universal.apk" class="btn-download">Download</a>
                    </div>
                    <div class="download-item">
                        <div class="item-size">40M</div>
                        <a href="REX-Player-preview-r1010-arm64-v8a.apk" class="btn-download">Download</a>
                    </div>
                    <div class="download-item">
                        <div class="item-size">38M</div>
                        <a href="REX-Player-preview-r1010-armeabi-v7a.apk" class="btn-download">Download</a>
                    </div>
                    <div class="download-item">
                        <div class="item-size">41M</div>
                        <a href="REX-Player-preview-r1010-x86.apk" class="btn-download">Download</a>
                    </div>
                    <div class="download-item">
                        <div class="item-size">41M</div>
                        <a href="REX-Player-preview-r1010-x86_64.apk" class="btn-download">Download</a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val release = UpdateManager.parsePreviewHtml(sampleHtml)

        assertEquals("r1010", release.tagName)
        assertEquals("REX Player Preview r1010", release.name)
        assertEquals("2026-10-12 13:26 UTC", release.publishedAt)
        assertEquals("https://mpvrex.github.io/REX-Player/", release.htmlUrl)
        assertEquals(5, release.assets.size)

        val universal = release.assets.firstOrNull { it.name.contains("universal") }
        assertNotNull(universal)
        assertEquals("REX-Player-preview-r1010-universal.apk", universal!!.name)
        assertEquals("https://mpvrex.github.io/REX-Player/REX-Player-preview-r1010-universal.apk", universal.downloadUrl)
        assertEquals(101L * 1024 * 1024, universal.size)

        val arm64 = release.assets.firstOrNull { it.name.contains("arm64-v8a") }
        assertNotNull(arm64)
        assertEquals("REX-Player-preview-r1010-arm64-v8a.apk", arm64!!.name)
        assertEquals("https://mpvrex.github.io/REX-Player/REX-Player-preview-r1010-arm64-v8a.apk", arm64.downloadUrl)
        assertEquals(40L * 1024 * 1024, arm64.size)
    }

    @Test
    fun selectBestApkAsset_selectsMatchingArchOrFallback() {
        val assets = listOf(
            Asset("url1", "REX-Player-preview-r1010-universal.apk", 101L * 1024 * 1024, "application/vnd.android.package-archive"),
            Asset("url2", "REX-Player-preview-r1010-arm64-v8a.apk", 40L * 1024 * 1024, "application/vnd.android.package-archive"),
            Asset("url3", "REX-Player-preview-r1010-armeabi-v7a.apk", 38L * 1024 * 1024, "application/vnd.android.package-archive"),
            Asset("url4", "REX-Player-preview-r1010-x86.apk", 41L * 1024 * 1024, "application/vnd.android.package-archive"),
            Asset("url5", "REX-Player-preview-r1010-x86_64.apk", 41L * 1024 * 1024, "application/vnd.android.package-archive")
        )

        val bestArm64 = UpdateManager.selectBestApkAsset(assets, "arm64-v8a")
        assertNotNull(bestArm64)
        assertEquals("REX-Player-preview-r1010-arm64-v8a.apk", bestArm64!!.name)
        assertEquals(40L * 1024 * 1024, bestArm64.size)

        val bestArmV7 = UpdateManager.selectBestApkAsset(assets, "armeabi-v7a")
        assertNotNull(bestArmV7)
        assertEquals("REX-Player-preview-r1010-armeabi-v7a.apk", bestArmV7!!.name)

        val fallbackUniversal = UpdateManager.selectBestApkAsset(assets, "mips")
        assertNotNull(fallbackUniversal)
        assertEquals("REX-Player-preview-r1010-universal.apk", fallbackUniversal!!.name)
        assertEquals(101L * 1024 * 1024, fallbackUniversal.size)
    }

    // --- Preview changelog tests ---

    private fun previewRelease(
        changelog: List<ChangelogEntry> = emptyList(),
        tagName: String = "r1015"
    ): Release = Release(
        tagName = tagName,
        name = "REX Player Preview r1015",
        body = "Nightly preview build automatically generated from master branch.",
        publishedAt = "2026-10-25 12:00 UTC",
        assets = emptyList(),
        htmlUrl = null,
        changelog = changelog
    )

    @Test
    fun buildPreviewReleaseNotes_linksCommitsAndIssueReferences() {
        val release = previewRelease(
            listOf(
                ChangelogEntry(1015, "Fix crash on playback start (fixes #42)", "abc123"),
                ChangelogEntry(1014, "Fix subtitle offsets on low-fps clips", "ghi789"),
                ChangelogEntry(1013, "Rehome misplaced options", "jkl012"),
                ChangelogEntry(1012, "Deduplicate settings pages", "mno345"),
                ChangelogEntry(1011, "Improve subtitle rendering", "def456"),
                ChangelogEntry(1005, "Older than installed build", "old123") // <= current, excluded
            )
        )

        val notes = UpdateManager.buildPreviewReleaseNotes(release, currentCommitCount = 1010)

        val expected = "### What's changed\n" +
            "- **r1015** [Fix crash on playback start (fixes ](${UpdateManager.REPO_COMMIT_URL}/abc123)" +
            "[#42](${UpdateManager.REPO_ISSUE_URL}/42))\n" +
            "- **r1014** [Fix subtitle offsets on low-fps clips](${UpdateManager.REPO_COMMIT_URL}/ghi789)\n" +
            "- **r1013** [Rehome misplaced options](${UpdateManager.REPO_COMMIT_URL}/jkl012)\n" +
            "- **r1012** [Deduplicate settings pages](${UpdateManager.REPO_COMMIT_URL}/mno345)\n" +
            "- **r1011** [Improve subtitle rendering](${UpdateManager.REPO_COMMIT_URL}/def456)"
        assertEquals(expected, notes)
        assertFalse(notes.contains("r1005"))
    }

    @Test
    fun buildPreviewReleaseNotes_escapesMarkdownCharacters() {
        val release = previewRelease(
            tagName = "r1013",
            changelog = listOf(
                ChangelogEntry(1013, "fix: gamma *bold* _under_ [brk] `code` ~end~ <tag> !bang", "s1h2"),
                ChangelogEntry(1012, "Fix subtitle rounding", "ru2nd"),
                ChangelogEntry(1011, "Improve cache eviction", "ev1ct")
            )
        )

        val notes = UpdateManager.buildPreviewReleaseNotes(release, currentCommitCount = 1010)

        val expected = """### What's changed
- **r1013** [fix: gamma \*bold\* \_under\_ \[brk\] \`code\` \~end\~ \<tag\> \!bang](${UpdateManager.REPO_COMMIT_URL}/s1h2)
- **r1012** [Fix subtitle rounding](${UpdateManager.REPO_COMMIT_URL}/ru2nd)
- **r1011** [Improve cache eviction](${UpdateManager.REPO_COMMIT_URL}/ev1ct)""".trimIndent()
        assertEquals(expected, notes)
    }

    @Test
    fun buildPreviewReleaseNotes_fallsBackWhenChangelogMissingOrStale() {
        // No changelog (old manifest / HTML fallback): keep static body
        assertEquals("Nightly preview build automatically generated from master branch.",
            UpdateManager.buildPreviewReleaseNotes(previewRelease(), 1000))

        // All entries at or below the installed build: keep static body
        val stale = previewRelease(listOf(ChangelogEntry(900, "Old commit", "sha900")))
        assertEquals("Nightly preview build automatically generated from master branch.",
            UpdateManager.buildPreviewReleaseNotes(stale, 1000))
    }

    @Test
    fun buildPreviewReleaseNotes_notifiesAboutCommitsOutsideWindow() {
        val window = (996..1015).map { n -> ChangelogEntry(n, "Commit $n", "sha$n") }
        val release = previewRelease(window)

        val notes = UpdateManager.buildPreviewReleaseNotes(release, currentCommitCount = 800)

        assertTrue(notes.contains("- **r1015** [Commit 1015](${UpdateManager.REPO_COMMIT_URL}/sha1015)"))
        assertTrue(notes.contains("…and 195 more commits (only the latest 20 are shown)"))
        assertFalse(notes.contains("Commit 995"))
    }

    @Test
    fun buildPreviewReleaseNotes_keepsPlainMessageWithoutSha() {
        val release = previewRelease(
            tagName = "r1012",
            changelog = listOf(
                ChangelogEntry(1012, "Cleanup from rebase refs #7", ""),
                ChangelogEntry(1011, "Improve subtitle rendering", "def456")
            )
        )

        val notes = UpdateManager.buildPreviewReleaseNotes(release, currentCommitCount = 1010)

        val expected = "### What's changed\n" +
            "- **r1012** Cleanup from rebase refs [#7](${UpdateManager.REPO_ISSUE_URL}/7)\n" +
            "- **r1011** [Improve subtitle rendering](${UpdateManager.REPO_COMMIT_URL}/def456)"
        assertEquals(expected, notes)
    }

    @Test
    fun release_decodesChangelogFromPreviewManifestAndDefaultsMissingFields() {
        val payload = """
            {
              "tag_name": "r1015",
              "name": "REX Player Preview r1015",
              "body": "Nightly preview build automatically generated from master branch.",
              "published_at": "2026-10-25 12:00 UTC",
              "html_url": "https://mpvrex.github.io/REX-Player/",
              "assets": [
                {
                  "name": "REX-Player-preview-r1015-universal.apk",
                  "browser_download_url": "https://mpvrex.github.io/REX-Player/REX-Player-preview-r1015-universal.apk",
                  "size": 100,
                  "content_type": "application/vnd.android.package-archive"
                }
              ],
              "changelog": [
                { "serial": 1015, "message": "Fix crash on playback start (fixes #42)", "sha": "abc123" },
                { "serial": 1014, "message": "Improve subtitle rendering" }
              ]
            }
        """.trimIndent()

        val release = Json { ignoreUnknownKeys = true }.decodeFromString<Release>(payload)

        assertEquals(2, release.changelog.size)
        assertEquals(1015, release.changelog[0].serial)
        assertEquals("abc123", release.changelog[0].sha)
        assertEquals("", release.changelog[1].sha) // missing sha defaults to blank
        assertEquals(100L, release.assets[0].size)
    }

    @Test
    fun release_decodesWithoutChangelogField() {
        val payload = """
            {
              "tag_name": "v5.3.0",
              "name": "REX Player 5.3.0",
              "body": "Release notes",
              "published_at": "2026-10-01 09:00 UTC",
              "html_url": "https://github.com/mpvRex/REX-Player/releases/tag/v5.3.0",
              "assets": []
            }
        """.trimIndent()

        val release = Json { ignoreUnknownKeys = true }.decodeFromString<Release>(payload)

        assertTrue(release.changelog.isEmpty())
        assertEquals("Release notes", release.body)
    }
}
