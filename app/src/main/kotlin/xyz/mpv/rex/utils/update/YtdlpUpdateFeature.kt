package xyz.mpv.rex.utils.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import xyz.mpv.rex.BuildConfig

/**
 * Companion (REX-Ytdlp APK) update checker.
 *
 * Gated on the addon being installed: if the companion package is absent,
 * [checkForUpdate] returns null without touching the network, so users who
 * never installed the companion never see an update dialog.
 *
 * Reuses [Release]/[Asset] from UpdateFeature.kt. The version we compare
 * against is the companion APK's versionName (PackageManager), NOT the
 * yt-dlp core version reported over IPC.
 */
class YtdlpUpdateManager(private val context: Context) {

    private val client = UpdateManager.clientWithUserAgent()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE) }

    fun isCompanionInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    COMPANION_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun installedVersion(): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    COMPANION_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
            }
            info.versionName?.removePrefix("v")
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun checkForUpdate(forceShow: Boolean = false, manual: Boolean = forceShow): Release? {
        // Hard gate: no companion installed -> never nag.
        if (!isCompanionInstalled()) return null
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return null
        // Automatic checks are throttled to once per hour; manual ones always run.
        if (!manual && !isAutoCheckDue()) return null

        val currentVersion = installedVersion() ?: return null

        val release = getLatestRelease(
            "https://api.github.com/repos/mpvRex/REX-Ytdlp/releases/latest"
        )
        // Server answered, so count this as a check.
        markChecked()
        val remoteVersion = release.tagName.removePrefix("v")
        val ignoredVersion = prefs.getString("ytdlp_ignored_version", null)

        if (!forceShow && ignoredVersion == remoteVersion) {
            cacheRelease(null)
            return null
        }

        return if (UpdateManager.isNewerVersion(remoteVersion, currentVersion)) {
            release.also { cacheRelease(it) }
        } else {
            cacheRelease(null)
            null
        }
    }

    /** True if an automatic (non-manual) companion check is allowed right now. */
    fun isAutoCheckDue(): Boolean = UpdateManager.isAutoCheckDue(prefs.getLong(KEY_LAST_CHECK, 0L))

    /**
     * Records a successful check so the next automatic check waits an hour. Also stamps
     * the companion version the check compared against, so [dropStateFromPreviousCompanion]
     * can recognize state persisted while an older companion APK was installed.
     */
    fun markChecked() {
        prefs.edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_CHECK_VERSION, installedVersion())
            .apply()
    }

    /**
     * Discards companion update state recorded for a different companion build.
     *
     * Updating (or removing) the addon does not change the player app's own version, so
     * [KEY_LAST_CHECK_VERSION] stores the companion versionName observed at the last
     * successful check. When it differs, the throttle and cache describe a previous
     * companion build and are dropped: the cache may otherwise name the very version
     * that was just installed, and the throttle would defer a fresh check by an hour.
     */
    fun dropStateFromPreviousCompanion() {
        val installedVersion = installedVersion()
        if (prefs.getString(KEY_LAST_CHECK_VERSION, null) == installedVersion) return
        prefs.edit()
            .putString(KEY_LAST_CHECK_VERSION, installedVersion)
            .putLong(KEY_LAST_CHECK, 0L)
            .remove(KEY_CACHED_RELEASE)
            .apply()
    }

    /** The newest companion release known from the last successful check, if any. */
    fun cachedRelease(): Release? = UpdateManager.readCachedRelease(prefs, KEY_CACHED_RELEASE)

    /**
     * The cached companion release, provided it is still newer than the companion APK
     * currently installed; otherwise the stale cache is cleared and null returned. The
     * restore-when-throttled path uses this so an update that was installed after the
     * check is not resurrected from cache.
     */
    fun cachedReleaseIfStillNewer(): Release? {
        val cached = cachedRelease() ?: return null
        val installedVersion = installedVersion()
        if (installedVersion != null && UpdateManager.isNewerVersion(
                remote = cached.tagName.removePrefix("v"),
                current = installedVersion
            )
        ) {
            return cached
        }
        cacheRelease(null)
        return null
    }

    /** Stores the newest available companion release; passing null clears it. */
    fun cacheRelease(release: Release?) {
        prefs.edit().apply {
            if (release == null) remove(KEY_CACHED_RELEASE)
            else putString(KEY_CACHED_RELEASE, json.encodeToString(Release.serializer(), release))
        }.apply()
    }

    fun ignoreVersion(version: String) {
        prefs.edit().putString("ytdlp_ignored_version", version).apply()
        // Don't resurrect an ignored version from the cache on the next launch.
        cacheRelease(null)
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        json.decodeFromString<Release>(response.body.string())
    }

    companion object {
        const val COMPANION_PACKAGE = "xyz.mpv.rex.addon.ytdl"
        private const val KEY_LAST_CHECK = "last_ytdlp_update_check"
        private const val KEY_LAST_CHECK_VERSION = "ytdlp_last_check_version"
        private const val KEY_CACHED_RELEASE = "ytdlp_cached_release"
    }
}
