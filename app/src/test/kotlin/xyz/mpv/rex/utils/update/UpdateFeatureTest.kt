package xyz.mpv.rex.utils.update

import kotlinx.serialization.json.Json
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.BuildConfig

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
    fun isAutoCheckDue_respectsOneHourWindow() {
        val hour = UpdateManager.AUTO_CHECK_INTERVAL_MS
        val now = 1_000_000_000_000L

        // Never checked -> due
        assertTrue(UpdateManager.isAutoCheckDue(0L, now))
        assertTrue(UpdateManager.isAutoCheckDue(-5L, now))

        // Restarting within the hour must NOT hit the server
        assertFalse(UpdateManager.isAutoCheckDue(now, now))
        assertFalse(UpdateManager.isAutoCheckDue(now - 1000L, now))
        assertFalse(UpdateManager.isAutoCheckDue(now - hour + 1L, now))

        // An hour later -> due again
        assertTrue(UpdateManager.isAutoCheckDue(now - hour, now))
        assertTrue(UpdateManager.isAutoCheckDue(now - hour - 1L, now))

        // Clock moved backwards must not block checks forever
        assertTrue(UpdateManager.isAutoCheckDue(now + 5000L, now))
    }

    @Test
    fun cachedRelease_roundTripsThroughPrefs() {
        val prefs = FakePrefs()
        val release = Release(
            tagName = "r1042",
            name = "REX r1042",
            body = "notes",
            publishedAt = "2026-10-26T00:00:00Z",
            assets = listOf(Asset("https://example.com/app.apk", "app-arm64-v8a.apk", 123L, "apk")),
            htmlUrl = "https://example.com/release"
        )

        assertNull(UpdateManager.readCachedRelease(prefs, "k"))

        prefs.edit().putString("k", Json.encodeToString(Release.serializer(), release)).apply()
        val restored = UpdateManager.readCachedRelease(prefs, "k")
        assertNotNull(restored)
        assertEquals(release, restored)

        // Corrupt payload must not crash, just behave as "no cache".
        prefs.edit().putString("k", "{not json").apply()
        assertNull(UpdateManager.readCachedRelease(prefs, "k"))
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
    fun previewChangelogEntries_filtersByInstalledBuildAndOrdersNewestFirst() {
        val release = previewRelease(
            listOf(
                ChangelogEntry(1011, "Improve subtitle rendering", "def456"),
                ChangelogEntry(1005, "Older than installed build", "old123"), // <= current, excluded
                ChangelogEntry(1015, "Fix crash on playback start (fixes #42)", "abc123"),
                ChangelogEntry(1014, "Fix subtitle offsets on low-fps clips", "ghi789")
            )
        )

        val entries = UpdateManager.previewChangelogEntries(release, currentCommitCount = 1010)

        assertEquals(listOf(1015, 1014, 1011), entries.map { it.serial })
    }

    @Test
    fun previewChangelogEntries_handlesBoundaryAndEmptyResults() {
        val atInstalledBuild = previewRelease(listOf(ChangelogEntry(1010, "At installed build", "sha")))
        assertTrue(UpdateManager.previewChangelogEntries(atInstalledBuild, 1010).isEmpty())

        val nextCommit = previewRelease(listOf(ChangelogEntry(1011, "Next commit", "sha")))
        assertEquals(
            listOf(1011),
            UpdateManager.previewChangelogEntries(nextCommit, 1010).map { it.serial }
        )

        // No changelog at all (older manifest / HTML fallback)
        assertTrue(UpdateManager.previewChangelogEntries(previewRelease(), 1000).isEmpty())
    }

    @Test
    fun previewChangelogSkipped_countsCommitsOutsideWindow() {
        val window = (996..1015).map { n -> ChangelogEntry(n, "Commit $n", "sha$n") }
        val release = previewRelease(window)

        val shown = UpdateManager.previewChangelogEntries(release, 800)
        assertEquals(20, shown.size)
        assertEquals(195, UpdateManager.previewChangelogSkipped(release, 800, shown.size))

        // Whole range covered by the window -> no extra note
        val fullyCovered = UpdateManager.previewChangelogEntries(release, 995)
        assertEquals(0, UpdateManager.previewChangelogSkipped(release, 995, fullyCovered.size))

        // Nothing shown -> no note
        assertEquals(0, UpdateManager.previewChangelogSkipped(release, 1015, 0))
    }

    @Test
    fun parseChangelogSegments_splitsIssueReferences() {
        assertEquals(
            listOf(
                ChangelogSegment.Text("Fix crash on playback start (fixes "),
                ChangelogSegment.Ref("42"),
                ChangelogSegment.Text(")")
            ),
            UpdateManager.parseChangelogSegments("Fix crash on playback start (fixes #42)")
        )

        assertEquals(
            listOf(
                ChangelogSegment.Text("collisions ("),
                ChangelogSegment.Ref("396"),
                ChangelogSegment.Text(") ("),
                ChangelogSegment.Ref("455"),
                ChangelogSegment.Text(")")
            ),
            UpdateManager.parseChangelogSegments("collisions (#396) (#455)")
        )

        assertEquals(
            listOf(ChangelogSegment.Ref("12"), ChangelogSegment.Text(" lead")),
            UpdateManager.parseChangelogSegments("#12 lead")
        )

        assertEquals(
            listOf(ChangelogSegment.Text("no references here")),
            UpdateManager.parseChangelogSegments("no references here")
        )
    }

    @Test
    fun commitAndIssueUrls_pointToRepositoryPages() {
        assertEquals(
            "https://github.com/mpvRex/REX-Player/commit/abc123",
            UpdateManager.commitUrl("abc123")
        )
        assertEquals(
            "https://github.com/mpvRex/REX-Player/issues/442",
            UpdateManager.issueUrl("442")
        )
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

    // --- Cached-restore staleness tests ---

    @Test
    fun isNewerVersion_comparesDottedVersions() {
        assertTrue(UpdateManager.isNewerVersion("5.4.1", "5.4.0"))
        assertFalse(UpdateManager.isNewerVersion("5.4.0", "5.4.1"))
        assertFalse(UpdateManager.isNewerVersion("5.4.0", "5.4.0"))
        assertTrue(UpdateManager.isNewerVersion("2.0", "1.9.9"))
        // Unparseable segments count as 0 on both sides
        assertFalse(UpdateManager.isNewerVersion("5.4.x", "5.4.0-beta"))
    }

    @Test
    fun isCachedReleaseStillNewer_revalidatesAgainstTheInstalledBuild() {
        // Preview: the cache names r1015, newer while r1010 runs, same after self-update
        val previewCache = previewRelease(tagName = "r1015")
        assertTrue(
            UpdateManager.isCachedReleaseStillNewer(
                previewCache,
                isPreviewBuild = true,
                currentCommitCount = 1010,
                currentVersionName = "5.4.0-1010"
            )
        )
        assertFalse(
            UpdateManager.isCachedReleaseStillNewer(
                previewCache,
                isPreviewBuild = true,
                currentCommitCount = 1015,
                currentVersionName = "5.4.0-1015"
            )
        )

        // Release: the "v" prefix and "-dev" suffix are ignored
        val releaseCache = previewRelease(tagName = "v5.5.0")
        assertTrue(
            UpdateManager.isCachedReleaseStillNewer(
                releaseCache,
                isPreviewBuild = false,
                currentCommitCount = 0,
                currentVersionName = "5.4.0"
            )
        )
        assertTrue(
            UpdateManager.isCachedReleaseStillNewer(
                releaseCache,
                isPreviewBuild = false,
                currentCommitCount = 0,
                currentVersionName = "5.4.0-dev"
            )
        )
        assertFalse(
            UpdateManager.isCachedReleaseStillNewer(
                releaseCache,
                isPreviewBuild = false,
                currentCommitCount = 0,
                currentVersionName = "5.5.0"
            )
        )
        assertFalse(
            UpdateManager.isCachedReleaseStillNewer(
                releaseCache,
                isPreviewBuild = false,
                currentCommitCount = 0,
                currentVersionName = "5.6.0"
            )
        )
    }

    /** An [UpdateManager] whose prefs are the given fakes, like the real one through Context. */
    private fun managerWithPrefs(prefs: FakePrefs): UpdateManager {
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return UpdateManager(context)
    }

    private fun releaseWith(tagName: String): Release = previewRelease(tagName = tagName)

    @Test
    fun cachedReleaseIfStillNewer_restoresOnlyReleasesNewerThanTheInstalledBuild() {
        val manager = managerWithPrefs(FakePrefs())

        // Clearly newer than the test build -> restored for the restore-when-throttled path
        val newer = releaseWith(tagName = "v99.0.0")
        manager.cacheRelease(newer)
        assertEquals(newer, manager.cachedReleaseIfStillNewer())
        assertNotNull(manager.cachedRelease())

        // Cached tag matches the installed build (the self-update scenario):
        // it must not resurrect, and the stale cache entry must be dropped.
        manager.cacheRelease(releaseWith(tagName = "v" + BuildConfig.VERSION_NAME))
        assertNull(manager.cachedReleaseIfStillNewer())
        assertNull(manager.cachedRelease())
    }

    @Test
    fun dropStateFromPreviousBuild_resetsThrottleAndCacheAfterAppUpdate() {
        val prefs = FakePrefs()
        val manager = managerWithPrefs(prefs)

        // Simulate state left behind by the previous build (checked recently, cache filled);
        // key names mirror the private UpdateManager.KEY_* constants.
        prefs.edit()
            .putLong("last_update_check", System.currentTimeMillis() - 60_000L)
            .putString("last_update_check_version", "5.3.9")
            .putString("cached_release", Json.encodeToString(Release.serializer(), previewRelease()))
            .apply()

        manager.dropStateFromPreviousBuild()

        // Throttle dropped so the first launch of the new build checks immediately...
        assertTrue(manager.isAutoCheckDue())
        // ...the stale cache is gone, and the stamp identifies the running build.
        assertNull(manager.cachedRelease())
        assertEquals(BuildConfig.VERSION_NAME, prefs.getString("last_update_check_version", null))

        // A second call on the same build must not keep resetting the state.
        prefs.edit().putLong("last_update_check", System.currentTimeMillis() - 60_000L).apply()
        manager.dropStateFromPreviousBuild()
        assertFalse(manager.isAutoCheckDue())
    }
}

/** Minimal in-memory [SharedPreferences] for unit tests. */
internal class FakePrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { applyInternal(); return true }
        override fun apply() { applyInternal() }

        private fun applyInternal() {
            if (clear) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
            pending.clear()
            removals.clear()
            clear = false
        }
    }
}
