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

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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

    suspend fun checkForUpdate(forceShow: Boolean = false): Release? {
        // Hard gate: no companion installed -> never nag.
        if (!isCompanionInstalled()) return null
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return null

        val currentVersion = installedVersion() ?: return null

        val release = getLatestRelease(
            "https://api.github.com/repos/mpvRex/REX-Ytdlp/releases/latest"
        )
        val remoteVersion = release.tagName.removePrefix("v")
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ytdlp_ignored_version", null)

        if (!forceShow && ignoredVersion == remoteVersion) return null

        return if (isNewerVersion(remoteVersion, currentVersion)) release else null
    }

    fun ignoreVersion(version: String) {
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ytdlp_ignored_version", version).apply()
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        json.decodeFromString<Release>(response.body.string())
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    companion object {
        const val COMPANION_PACKAGE = "xyz.mpv.rex.addon.ytdl"
    }
}
