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
            assets = listOf(Asset("https://example.com/app.apk", "app-arm64-v8a.apk", 123L)),
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
    fun parseFileSizeToBytes_convertsUnitsCorrectly() {
        assertEquals(101L * 1024 * 1024, UpdateManager.parseFileSizeToBytes("101M"))
        assertEquals(40L * 1024 * 1024, UpdateManager.parseFileSizeToBytes("40MB"))
        assertEquals(1L * 1024 * 1024 * 1024, UpdateManager.parseFileSizeToBytes("1G"))
        assertEquals(500L * 1024, UpdateManager.parseFileSizeToBytes("500KB"))
        assertEquals(1024L, UpdateManager.parseFileSizeToBytes("1024B"))
        assertEquals(0L, UpdateManager.parseFileSizeToBytes("invalid"))
    }

    @Test
    fun selectBestApkAsset_selectsMatchingArchOrFallback() {
        val assets = listOf(
            Asset("url1", "REX-Player-preview-r1010-universal.apk", 101L * 1024 * 1024),
            Asset("url2", "REX-Player-preview-r1010-arm64-v8a.apk", 40L * 1024 * 1024),
            Asset("url3", "REX-Player-preview-r1010-armeabi-v7a.apk", 38L * 1024 * 1024),
            Asset("url4", "REX-Player-preview-r1010-x86.apk", 41L * 1024 * 1024),
            Asset("url5", "REX-Player-preview-r1010-x86_64.apk", 41L * 1024 * 1024)
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

        assertTrue(release.assets.isEmpty())
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
        // The "v" prefix and "-dev" suffix are ignored
        val cached = makeRelease(tagName = "v5.5.0")

        assertTrue(UpdateManager.isCachedReleaseStillNewer(cached, currentVersionName = "5.4.0"))
        assertTrue(UpdateManager.isCachedReleaseStillNewer(cached, currentVersionName = "5.4.0-dev"))
        assertFalse(UpdateManager.isCachedReleaseStillNewer(cached, currentVersionName = "5.5.0"))
        assertFalse(UpdateManager.isCachedReleaseStillNewer(cached, currentVersionName = "5.6.0"))
    }

    /** An [UpdateManager] whose prefs are the given fakes, like the real one through Context. */
    private fun managerWithPrefs(prefs: FakePrefs): UpdateManager {
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return UpdateManager(context)
    }

    private fun makeRelease(tagName: String): Release = Release(
        tagName = tagName,
        name = "REX Player $tagName",
        body = "",
        publishedAt = "",
        assets = emptyList()
    )

    private fun releaseWith(tagName: String): Release = makeRelease(tagName)

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
            .putString("cached_release", Json.encodeToString(Release.serializer(), makeRelease("v5.5.0")))
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

    @Test
    fun isTrustedReleaseUrl_acceptsExpectedReleaseHosts() {
        assertTrue(UpdateManager.isTrustedReleaseUrl("https://github.com/mpvRex/REX-Player/releases/latest"))
        assertTrue(UpdateManager.isTrustedReleaseUrl("https://api.github.com/repos/mpvRex/REX-Player/releases/latest"))
        assertTrue(UpdateManager.isTrustedReleaseUrl("https://mpvrex.github.io/REX-Player/version.json"))
        assertTrue(UpdateManager.isTrustedReleaseUrl("https://objects.githubusercontent.com/x/y.apk"))
    }

    @Test
    fun isTrustedReleaseUrl_rejectsLookalikesAndDowngrades() {
        // wrong scheme
        assertFalse(UpdateManager.isTrustedReleaseUrl("http://github.com/mpvRex/REX-Player/releases"))
        // wrong host
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://evil.example/mpvRex/REX-Player/x.apk"))
        // suffix / prefix host spoofing
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://github.com.evil.example/mpvRex/REX-Player/x.apk"))
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://notgithub.com/mpvRex/REX-Player/x.apk"))
        // userinfo trick: real host is evil.example
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://github.com@evil.example/mpvRex/REX-Player/x.apk"))
        // non-default port
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://github.com:8443/mpvRex/REX-Player/x.apk"))
        // trusted host, untrusted path
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://github.com/someoneelse/other/x.apk"))
        assertFalse(UpdateManager.isTrustedReleaseUrl("https://mpvrex.github.io/Other/x.apk"))
        // junk
        assertFalse(UpdateManager.isTrustedReleaseUrl(null))
        assertFalse(UpdateManager.isTrustedReleaseUrl(""))
        assertFalse(UpdateManager.isTrustedReleaseUrl("not a url"))
    }

    @Test
    fun sanitizeRelease_dropsUntrustedAssetsAndHtmlUrl() {
        val release = Release(
            tagName = "v5.5.1",
            name = "R",
            body = "b",
            publishedAt = "",
            assets = listOf(
                Asset("https://github.com/mpvRex/REX-Player/releases/download/v5.5.1/app.apk", "app.apk", 1L),
                Asset("https://evil.example/app.apk", "evil.apk", 1L),
                Asset("http://github.com/mpvRex/REX-Player/x.apk", "plain-http.apk", 1L)
            ),
            htmlUrl = "https://evil.example/phish"
        )

        val safe = UpdateManager.sanitizeRelease(release)

        assertEquals(listOf("app.apk"), safe.assets.map { it.name })
        assertNull(safe.htmlUrl)
    }

    @Test
    fun sanitizeRelease_keepsTrustedHtmlUrl() {
        val url = "https://github.com/mpvRex/REX-Player/releases/tag/v5.5.1"
        val release = Release("v5.5.1", "R", "b", "", emptyList(), htmlUrl = url)
        assertEquals(url, UpdateManager.sanitizeRelease(release).htmlUrl)
    }
}
