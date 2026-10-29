package xyz.mpv.rex.utils.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instance tests for the companion (REX-Ytdlp) update cache lifecycle: the
 * restore-when-throttled path and the state dropped after a companion update.
 */
class YtdlpUpdateFeatureTest {

    private fun managerFor(prefs: FakePrefs, companionVersion: String?): YtdlpUpdateManager {
        val packageManager = mockk<PackageManager>()
        if (companionVersion == null) {
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
            every {
                packageManager.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>())
            } throws PackageManager.NameNotFoundException()
        } else {
            val info = PackageInfo()
            info.versionName = companionVersion
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns info
            every { packageManager.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } returns info
        }
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageManager } returns packageManager
        return YtdlpUpdateManager(context)
    }

    private fun cachedRelease(tagName: String): Release = Release(
        tagName = tagName,
        name = "REX-Ytdlp $tagName",
        body = "Release notes",
        publishedAt = "2026-10-26T00:00:00Z",
        assets = emptyList(),
        htmlUrl = null
    )

    @Test
    fun cachedReleaseIfStillNewer_restoresCacheWhileTheCompanionIsOlder() {
        val manager = managerFor(FakePrefs(), companionVersion = "1.2.3")
        val newer = cachedRelease(tagName = "v1.3.0")

        manager.cacheRelease(newer)
        assertEquals(newer, manager.cachedReleaseIfStillNewer())
    }

    @Test
    fun cachedReleaseIfStillNewer_clearsCacheAfterTheCompanionSelfUpdated() {
        // The cached entry was written while 1.2.3 ran; 1.3.0 got installed since.
        val manager = managerFor(FakePrefs(), companionVersion = "1.3.0")
        manager.cacheRelease(cachedRelease(tagName = "v1.3.0"))

        assertNull(manager.cachedReleaseIfStillNewer())
        // The stale entry must be dropped so it cannot resurrect on the next launch.
        assertNull(manager.cachedRelease())
    }

    @Test
    fun cachedReleaseIfStillNewer_clearsCacheWhenTheCompanionWasRemoved() {
        val manager = managerFor(FakePrefs(), companionVersion = null)
        manager.cacheRelease(cachedRelease(tagName = "v1.3.0"))

        assertNull(manager.cachedReleaseIfStillNewer())
        assertNull(manager.cachedRelease())
    }

    @Test
    fun dropStateFromPreviousCompanion_resetsThrottleAndCacheAfterCompanionUpdate() {
        val prefs = FakePrefs()
        // State left behind while the older companion was installed; key names mirror the
        // private YtdlpUpdateManager.KEY_* constants.
        prefs.edit()
            .putLong("last_ytdlp_update_check", System.currentTimeMillis() - 60_000L)
            .putString("ytdlp_last_check_version", "1.2.3")
            .putString("ytdlp_cached_release", Json.encodeToString(Release.serializer(), cachedRelease(tagName = "v1.3.0")))
            .apply()
        val manager = managerFor(prefs, companionVersion = "1.3.0")

        manager.dropStateFromPreviousCompanion()

        // Throttle dropped so the first launch after the update checks immediately...
        assertTrue(manager.isAutoCheckDue())
        // ...the stale cache is gone, and the stamp identifies the installed companion.
        assertNull(manager.cachedRelease())
        assertEquals("1.3.0", prefs.getString("ytdlp_last_check_version", null))

        // A second call on the same companion version must not keep resetting the state.
        prefs.edit().putLong("last_ytdlp_update_check", System.currentTimeMillis() - 60_000L).apply()
        manager.dropStateFromPreviousCompanion()
        assertFalse(manager.isAutoCheckDue())
    }

    @Test
    fun dropStateFromPreviousCompanion_keepsStateWhenTheCompanionDidNotChange() {
        val prefs = FakePrefs()
        val manager = managerFor(prefs, companionVersion = "1.2.3")
        prefs.edit()
            .putLong("last_ytdlp_update_check", System.currentTimeMillis() - 60_000L)
            .putString("ytdlp_last_check_version", "1.2.3")
            .apply()

        manager.dropStateFromPreviousCompanion()

        // Nothing changed -> throttle window stays in effect.
        assertFalse(manager.isAutoCheckDue())
    }
}
