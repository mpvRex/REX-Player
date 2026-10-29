package xyz.mpv.rex.utils.update
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import xyz.mpv.rex.utils.media.MediaFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// --- Data Models ---

@Serializable
data class Release(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("assets") val assets: List<Asset>,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("changelog") val changelog: List<ChangelogEntry> = emptyList()
)

@Serializable
data class Asset(
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)

// One commit of a preview build's changelog. `serial` matches the "r<serial>" preview
// version name, `message` is the commit subject (first line only) and `sha` links to
// the GitHub commit page.
@Serializable
data class ChangelogEntry(
    @SerialName("serial") val serial: Int,
    @SerialName("message") val message: String,
    @SerialName("sha") val sha: String = ""
)

/** A piece of a changelog message: plain text, or an "#123" issue/PR reference. */
sealed interface ChangelogSegment {
    data class Text(val text: String) : ChangelogSegment
    data class Ref(val number: String) : ChangelogSegment
}

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    private val client = clientWithUserAgent()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    companion object {
        const val PREVIEW_PAGE_URL = "https://mpvrex.github.io/REX-Player/"
        const val PREVIEW_JSON_URL = "https://mpvrex.github.io/REX-Player/version.json"
        const val RELEASE_API_URL = "https://api.github.com/repos/mpvRex/REX-Player/releases/latest"
        const val REPO_COMMIT_URL = "https://github.com/mpvRex/REX-Player/commit"
        const val REPO_ISSUE_URL = "https://github.com/mpvRex/REX-Player/issues"

        /** Minimum gap between two automatic (non-manual) server checks. */
        const val AUTO_CHECK_INTERVAL_MS = 60L * 60L * 1000L

        private const val PREFS_NAME = "mpvEx_prefs"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_LAST_CHECK_VERSION = "last_update_check_version"
        private const val KEY_CACHED_RELEASE = "cached_release"

        /** True if an automatic check is allowed right now (never checked, or interval elapsed). */
        fun isAutoCheckDue(lastCheckMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (lastCheckMillis <= 0L) return true
            // Clock moved backwards or timer overflowed -> treat as due instead of blocking forever.
            if (nowMillis < lastCheckMillis) return true
            return nowMillis - lastCheckMillis >= AUTO_CHECK_INTERVAL_MS
        }

        /** Decodes a cached [Release] stored under [key], or null if absent/corrupt. */
        fun readCachedRelease(prefs: SharedPreferences, key: String): Release? {
            val raw = prefs.getString(key, null) ?: return null
            return try {
                Json { ignoreUnknownKeys = true }.decodeFromString(Release.serializer(), raw)
            } catch (_: Exception) {
                null
            }
        }

        /** GitHub API requires a User-Agent; without one requests are rejected. */
        fun clientWithUserAgent(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "mpvRex/${BuildConfig.VERSION_NAME}")
                        .build()
                )
            }
            .build()

        fun isPreviewBuild(): Boolean {
            return BuildConfig.IS_PREVIEW || BuildConfig.BUILD_TYPE == "preview" || BuildConfig.APPLICATION_ID.endsWith(".preview")
        }

        fun getCurrentCommitCount(): Int {
            if (BuildConfig.GIT_COUNT > 0) return BuildConfig.GIT_COUNT
            val suffix = BuildConfig.VERSION_NAME.substringAfterLast('-', "")
            return suffix.toIntOrNull() ?: 0
        }

        fun parseCommitCount(tag: String): Int? {
            val clean = tag.trim().removePrefix("r").removePrefix("R").removePrefix("v").removePrefix("V")
            return clean.toIntOrNull()
        }

        fun isNewerPreviewVersion(remoteTag: String, currentCommitCount: Int): Boolean {
            val remoteCount = parseCommitCount(remoteTag) ?: return false
            return if (currentCommitCount > 0) {
                remoteCount > currentCommitCount
            } else {
                false
            }
        }

        /** Standard "x.y.z" comparison; unparseable segments count as 0. */
        fun isNewerVersion(remote: String, current: String): Boolean {
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

        /**
         * True if a cached release is still newer than the build it would be shown to.
         *
         * The cache stores the release that was newer at the last successful check,
         * relative to the build that ran it. When that update is then installed
         * (in-app self-update, sideload) the process dies before the cache can be
         * cleared, so restoring from cache must re-validate against the *installed*
         * version instead of resurrecting an already-installed "update available".
         */
        fun isCachedReleaseStillNewer(
            release: Release,
            isPreviewBuild: Boolean,
            currentCommitCount: Int,
            currentVersionName: String
        ): Boolean {
            return if (isPreviewBuild) {
                isNewerPreviewVersion(release.tagName, currentCommitCount)
            } else {
                isNewerVersion(release.tagName.removePrefix("v"), currentVersionName.replace("-dev", ""))
            }
        }

        // --- Preview changelog ---

        private val issueRef = Regex("""#(\d+)""")

        fun commitUrl(sha: String): String = "$REPO_COMMIT_URL/$sha"

        fun issueUrl(number: String): String = "$REPO_ISSUE_URL/$number"

        /** Splits a commit subject into plain text and "#123" issue/PR reference segments. */
        fun parseChangelogSegments(message: String): List<ChangelogSegment> {
            val segments = mutableListOf<ChangelogSegment>()
            var index = 0
            for (match in issueRef.findAll(message)) {
                if (match.range.first > index) {
                    segments += ChangelogSegment.Text(message.substring(index, match.range.first))
                }
                segments += ChangelogSegment.Ref(match.groupValues[1])
                index = match.range.last + 1
            }
            if (index < message.length) {
                segments += ChangelogSegment.Text(message.substring(index))
            }
            return segments
        }

        /** Commits between the installed build (serial > currentCommitCount) and the available build. */
        fun previewChangelogEntries(release: Release, currentCommitCount: Int): List<ChangelogEntry> {
            return release.changelog
                .filter { it.serial > currentCommitCount }
                .sortedByDescending { it.serial }
        }

        /**
         * Commits between the installed build and the release tag that are not listed in the
         * changelog window (capped by the workflow; merge commits are excluded too).
         */
        fun previewChangelogSkipped(
            release: Release,
            currentCommitCount: Int,
            shownCount: Int
        ): Int {
            if (shownCount <= 0) return 0
            val latestSerial = parseCommitCount(release.tagName)
                ?: release.changelog.maxOfOrNull { it.serial }
                ?: return 0
            return (latestSerial - currentCommitCount) - shownCount
        }

        fun parseFileSizeToBytes(sizeStr: String): Long {
            val trimmed = sizeStr.trim().uppercase(Locale.US)
            return try {
                when {
                    trimmed.endsWith("GB") || trimmed.endsWith("G") -> {
                        val num = trimmed.removeSuffix("GB").removeSuffix("G").trim().toDouble()
                        (num * 1024 * 1024 * 1024).toLong()
                    }
                    trimmed.endsWith("MB") || trimmed.endsWith("M") -> {
                        val num = trimmed.removeSuffix("MB").removeSuffix("M").trim().toDouble()
                        (num * 1024 * 1024).toLong()
                    }
                    trimmed.endsWith("KB") || trimmed.endsWith("K") -> {
                        val num = trimmed.removeSuffix("KB").removeSuffix("K").trim().toDouble()
                        (num * 1024).toLong()
                    }
                    trimmed.endsWith("B") -> {
                        trimmed.removeSuffix("B").trim().toLong()
                    }
                    else -> trimmed.toLongOrNull() ?: 0L
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun parsePreviewHtml(html: String): Release {
            val versionRegex = Regex("""class=["']version-badge["']>\s*(r?\d+)\s*<""", RegexOption.IGNORE_CASE)
            val versionMatch = versionRegex.find(html)
            val tag = versionMatch?.groupValues?.get(1) ?: throw IOException("Could not find preview version in page")
            val normalizedTag = if (tag.startsWith("r", ignoreCase = true)) tag else "r$tag"

            val timeRegex = Regex("""Build time:\s*([^\n<]+)""", RegexOption.IGNORE_CASE)
            val timeMatch = timeRegex.find(html)
            val buildTime = timeMatch?.groupValues?.get(1)?.trim() ?: ""

            val apkRegex = Regex(
                """(?:class=["']item-size["']>\s*([^<]+)\s*</div>\s*)?<a\s+[^>]*href=["'](REX-Player-preview-[^"']+\.apk)["']""",
                RegexOption.IGNORE_CASE
            )
            val assets = apkRegex.findAll(html).map { matchResult ->
                val sizeStr = matchResult.groupValues[1]
                val fileName = matchResult.groupValues[2]
                val sizeBytes = parseFileSizeToBytes(sizeStr)
                val downloadUrl = if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
                    fileName
                } else {
                    PREVIEW_PAGE_URL.trimEnd('/') + "/" + fileName.trimStart('/')
                }
                Asset(
                    downloadUrl = downloadUrl,
                    name = fileName,
                    size = sizeBytes,
                    contentType = "application/vnd.android.package-archive"
                )
            }.toList()

            if (assets.isEmpty()) {
                throw IOException("No preview APK assets found in preview page")
            }

            return Release(
                tagName = normalizedTag,
                name = "REX Player Preview $normalizedTag",
                body = "Nightly preview build automatically generated from master branch.",
                publishedAt = buildTime,
                assets = assets,
                htmlUrl = PREVIEW_PAGE_URL
            )
        }

        fun getDeviceArchitecture(): String {
            // Get the primary ABI (Application Binary Interface)
            val primaryAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS[0]
            } else {
                @Suppress("DEPRECATION")
                Build.CPU_ABI
            }

            // Map Android ABI names to APK naming convention
            return when (primaryAbi) {
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a" -> "armeabi-v7a"
                "x86" -> "x86"
                "x86_64" -> "x86_64"
                else -> "universal" // Fallback for unknown architectures
            }
        }

        fun selectBestApkAsset(assets: List<Asset>, targetArch: String = getDeviceArchitecture()): Asset? {
            // First, try to find architecture-specific APK
            val archSpecificApk = assets.firstOrNull { asset ->
                asset.name.endsWith(".apk") && asset.name.contains(targetArch, ignoreCase = true)
            }

            if (archSpecificApk != null) {
                return archSpecificApk
            }

            // Fallback to universal APK
            val universalApk = assets.firstOrNull { asset ->
                asset.name.endsWith(".apk") && asset.name.contains("universal", ignoreCase = true)
            }

            if (universalApk != null) {
                return universalApk
            }

            // Last resort: any APK
            return assets.firstOrNull { it.name.endsWith(".apk") }
        }
    }

    /**
     * Checks the server for a newer release.
     *
     * @param forceShow manual check: bypasses the ignore list.
     * @param manual explicit user action: bypasses the hourly auto-check throttle.
     *   Automatic callers should skip the call when [isAutoCheckDue] is false.
     */
    suspend fun checkForUpdate(forceShow: Boolean = false, manual: Boolean = forceShow): Release? {
        // Return null immediately if update feature is disabled (F-Droid flavor)
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }

        if (!manual && !isAutoCheckDue()) {
            return null
        }

        return if (isPreviewBuild()) {
            checkPreviewUpdate(forceShow)
        } else {
            checkReleaseUpdate(forceShow)
        }
    }

    private suspend fun checkReleaseUpdate(forceShow: Boolean): Release? {
        val release = getLatestRelease(RELEASE_API_URL)
        // Server answered, so count this as a check even if the ignored list hides it.
        markChecked()
        val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")
        val remoteVersion = release.tagName.removePrefix("v")
        val ignoredVersion = prefs.getString("ignored_version", null)

        // If this version was ignored, don't show it unless forced (manual check)
        if (!forceShow && ignoredVersion == remoteVersion) {
            cacheRelease(null)
            return null
        }

        return if (isNewerVersion(remoteVersion, currentVersion)) {
            ensureAssetSizes(release).also { cacheRelease(it) }
        } else {
            cacheRelease(null)
            null
        }
    }

    private suspend fun checkPreviewUpdate(forceShow: Boolean): Release? {
        val release = getLatestPreviewRelease() ?: return null
        // Server answered, so count this as a check even if the ignored list hides it.
        markChecked()
        val remoteVersion = release.tagName.removePrefix("v")
        val ignoredVersion = prefs.getString("ignored_version", null)

        if (!forceShow && ignoredVersion == remoteVersion) {
            cacheRelease(null)
            return null
        }

        val currentCommitCount = getCurrentCommitCount()
        return if (isNewerPreviewVersion(remoteVersion, currentCommitCount)) {
            ensureAssetSizes(release).also { cacheRelease(it) }
        } else {
            cacheRelease(null)
            null
        }
    }

    private suspend fun getLatestPreviewRelease(): Release? = withContext(Dispatchers.IO) {
        // Try version.json first
        try {
            val jsonRequest = Request.Builder().url(PREVIEW_JSON_URL).build()
            client.newCall(jsonRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body.string()
                    if (bodyString.isNotBlank()) {
                        val release = json.decodeFromString<Release>(bodyString)
                        // If version.json assets already have valid sizes (> 0), use directly
                        if (release.assets.isNotEmpty() && release.assets.any { it.size > 0L }) {
                            return@withContext release
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to parsing preview web page HTML
        }

        val pageRequest = Request.Builder().url(PREVIEW_PAGE_URL).build()
        client.newCall(pageRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch preview page: $response")
            val html = response.body.string()
            parsePreviewHtml(html)
        }
    }

    fun ignoreVersion(version: String) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        prefs.edit()
            .putString("ignored_version", version)
            .apply()
        // Don't resurrect an ignored version from the cache on the next launch.
        cacheRelease(null)
    }

    /** True if an automatic (non-manual) check is allowed right now. */
    fun isAutoCheckDue(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return isAutoCheckDue(last)
    }

    /**
     * Records a successful check so the next automatic check waits [AUTO_CHECK_INTERVAL_MS].
     * Only called when the server actually answered, so network failures retry next launch.
     * Also stamps the build the check was made by, so [dropStateFromPreviousBuild] can
     * recognize state persisted by an older app version.
     */
    fun markChecked() {
        prefs.edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_CHECK_VERSION, BuildConfig.VERSION_NAME)
            .apply()
    }

    /**
     * Discards update state recorded by a different app build.
     *
     * An in-app self-update kills the process while the package installer runs, so the
     * cached release and the check timestamp survive in SharedPreferences even though
     * they describe the previous build: the cache may name the very version that was
     * just installed, and the timestamp would defer the first real check after the
     * update by up to an hour. On the first launch of a new build both are dropped so
     * the automatic check runs fresh against the new version.
     */
    fun dropStateFromPreviousBuild() {
        if (prefs.getString(KEY_LAST_CHECK_VERSION, null) == BuildConfig.VERSION_NAME) return
        prefs.edit()
            .putString(KEY_LAST_CHECK_VERSION, BuildConfig.VERSION_NAME)
            .putLong(KEY_LAST_CHECK, 0L)
            .remove(KEY_CACHED_RELEASE)
            .apply()
    }

    /** The newest release that was known to be available at the last successful check, if any. */
    fun cachedRelease(): Release? = readCachedRelease(prefs, KEY_CACHED_RELEASE)

    /**
     * The cached release, provided it is still newer than the installed build; otherwise
     * the stale cache is cleared and null returned. The restore-when-throttled path uses
     * this so an update that was installed after the check is not resurrected from cache.
     */
    fun cachedReleaseIfStillNewer(): Release? {
        val cached = cachedRelease() ?: return null
        if (isCachedReleaseStillNewer(
                release = cached,
                isPreviewBuild = isPreviewBuild(),
                currentCommitCount = getCurrentCommitCount(),
                currentVersionName = BuildConfig.VERSION_NAME
            )
        ) {
            return cached
        }
        cacheRelease(null)
        return null
    }

    /** Stores the newest available release; passing null clears it (already up to date). */
    fun cacheRelease(release: Release?) {
        prefs.edit().apply {
            if (release == null) remove(KEY_CACHED_RELEASE)
            else putString(KEY_CACHED_RELEASE, json.encodeToString(Release.serializer(), release))
        }.apply()
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body.string()
        json.decodeFromString<Release>(responseBody)
    }

    fun downloadUpdate(release: Release): Flow<Float> {
        // Return completed flow immediately if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return flowOf(100f)
        }
        
        val asset = selectBestApkAsset(release.assets)
            ?: throw Exception("No compatible APK asset found")
        
        val destination = File(context.externalCacheDir, asset.name)
        return downloadApk(asset.downloadUrl, destination)
    }

    private suspend fun resolveAssetSize(asset: Asset): Long = withContext(Dispatchers.IO) {
        if (asset.size > 0L) return@withContext asset.size
        try {
            val headRequest = Request.Builder().url(asset.downloadUrl).head().build()
            client.newCall(headRequest).execute().use { response ->
                val len = response.header("Content-Length")?.toLongOrNull()
                if (len != null && len > 0L) return@withContext len
            }
        } catch (_: Exception) {
            // Ignore error
        }
        asset.size
    }

    private suspend fun ensureAssetSizes(release: Release): Release {
        val bestAsset = selectBestApkAsset(release.assets) ?: return release
        if (bestAsset.size > 0L) return release

        val resolvedSize = resolveAssetSize(bestAsset)
        return if (resolvedSize > 0L) {
            val updatedAssets = release.assets.map {
                if (it.downloadUrl == bestAsset.downloadUrl) it.copy(size = resolvedSize) else it
            }
            release.copy(assets = updatedAssets)
        } else {
            release
        }
    }

    private fun downloadApk(url: String, destination: File): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val body = response.body
        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(destination)

        try {
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength.toFloat()) * 100
                } else {
                    -1f 
                }
                emit(progress)
            }
            outputStream.flush()
            emit(100f) 
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }.flowOn(Dispatchers.IO)
    
    fun getApkFile(release: Release): File? {
        // Return null if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }
        
        val asset = selectBestApkAsset(release.assets) ?: return null
        val file = File(context.externalCacheDir, asset.name)
        return if (file.exists()) file else null
    }

    fun clearCache() {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
         context.externalCacheDir?.listFiles()?.forEach { 
             if (it.name.endsWith(".apk")) it.delete()
         }
    }
}

// --- ViewModel ---

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)
    private val ytdlpUpdateManager = YtdlpUpdateManager(application)

    /** Single-flight guards so overlapping triggers never fire duplicate requests. */
    private var appCheckJob: Job? = null
    private var companionCheckJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float>(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val prefs = application.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
    private val _isAutoUpdateEnabled = MutableStateFlow(
        if (BuildConfig.ENABLE_UPDATE_FEATURE) prefs.getBoolean("auto_update", true) else false
    )
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    fun toggleAutoUpdate(enabled: Boolean) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        prefs.edit().putBoolean("auto_update", enabled).apply()
        _isAutoUpdateEnabled.value = enabled
        // Re-enabling runs a check, but only if the hourly window has elapsed.
        if (enabled) {
            checkForUpdate(manual = false)
        }
    }

    // --- Companion (REX-Ytdlp APK) update state ---

    private val _companionUpdateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val companionUpdateState: StateFlow<UpdateState> = _companionUpdateState.asStateFlow()

    private val _isCompanionInstalled = MutableStateFlow(ytdlpUpdateManager.isCompanionInstalled())
    val isCompanionInstalled: StateFlow<Boolean> = _isCompanionInstalled.asStateFlow()

    private val _isYtdlpAutoUpdateEnabled = MutableStateFlow(
        if (BuildConfig.ENABLE_UPDATE_FEATURE) prefs.getBoolean("ytdlp_auto_update", true) else false
    )
    val isYtdlpAutoUpdateEnabled: StateFlow<Boolean> = _isYtdlpAutoUpdateEnabled.asStateFlow()

    fun toggleYtdlpAutoUpdate(enabled: Boolean) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        prefs.edit().putBoolean("ytdlp_auto_update", enabled).apply()
        _isYtdlpAutoUpdateEnabled.value = enabled
        if (enabled) {
            checkCompanionUpdate(manual = false)
        }
    }

    fun refreshCompanionInstalled() {
        _isCompanionInstalled.value = ytdlpUpdateManager.isCompanionInstalled()
    }

    fun ytdlpInstalledVersion(): String? = ytdlpUpdateManager.installedVersion()

    fun checkCompanionUpdate(manual: Boolean = false) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        refreshCompanionInstalled()
        if (!_isCompanionInstalled.value) return
        // Never run two companion checks at once (avoids duplicate API hits).
        if (companionCheckJob?.isActive == true) return
        // Automatic checks are throttled to once per hour, across restarts.
        // When throttled, fall back to the last known release so the dialog still shows
        // offline; only if it is still newer than the companion APK actually installed
        // (an update installed after the check must not resurrect from the cache).
        if (!manual && !ytdlpUpdateManager.isAutoCheckDue()) {
            val cached = ytdlpUpdateManager.cachedReleaseIfStillNewer()
            if (cached != null) _companionUpdateState.value = UpdateState.Available(cached)
            return
        }
        // Preserve an already-displayed dialog instead of overwriting it with Loading.
        val hadUpdate = _companionUpdateState.value is UpdateState.Available
        companionCheckJob = viewModelScope.launch {
            if (!hadUpdate) _companionUpdateState.value = UpdateState.Loading
            try {
                val release = ytdlpUpdateManager.checkForUpdate(forceShow = manual, manual = manual)
                val current = _companionUpdateState.value
                when {
                    release != null -> _companionUpdateState.value = UpdateState.Available(release)
                    manual -> _companionUpdateState.value = UpdateState.NoUpdate
                    // Keep whatever the user is already looking at on a throttled/skipped check.
                    current is UpdateState.Available -> Unit
                    else -> _companionUpdateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val current = _companionUpdateState.value
                _companionUpdateState.value = when {
                    manual -> UpdateState.Error
                    current is UpdateState.Available -> current
                    else -> UpdateState.Idle
                }
            }
        }
    }

    fun dismissCompanionNoUpdate() {
        _companionUpdateState.value = UpdateState.Idle
    }

    fun ignoreYtdlpVersion(version: String) {
        ytdlpUpdateManager.ignoreVersion(version)
        _companionUpdateState.value = UpdateState.Idle
    }

    init {
        // Only initialize auto-update if feature is enabled.
        // The checks themselves are throttled to once per hour (persisted across launches),
        // so reopening the app repeatedly within the hour makes no server request.
        if (BuildConfig.ENABLE_UPDATE_FEATURE) {
            // State persisted by a previous app build (cached release + throttle window)
            // is invalid after a self-update; drop it so the checks below run fresh.
            // The companion is tracked separately because updating the addon does not
            // change the player app's own version.
            updateManager.dropStateFromPreviousBuild()
            ytdlpUpdateManager.dropStateFromPreviousCompanion()
            if (isAutoUpdateEnabled.value) {
                checkForUpdate(manual = false)
            }
            // Companion auto-check only when addon is present (your conditional).
            if (_isCompanionInstalled.value && _isYtdlpAutoUpdateEnabled.value) {
                checkCompanionUpdate(manual = false)
            }
        }
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Available(val release: Release) : UpdateState()
        object NoUpdate : UpdateState()
        object Error : UpdateState()
        data class ReadyToInstall(val release: Release) : UpdateState()
    }

    fun dismissNoUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun checkForUpdate(manual: Boolean = false) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        // Never run two app checks at once (avoids duplicate API hits).
        if (appCheckJob?.isActive == true) return
        // Automatic checks are throttled to once per hour, across app restarts.
        // When throttled, fall back to the last known release so the dialog still shows
        // offline; only if it is still newer than the installed build (a self-update
        // installed after the check must not resurrect from the cache).
        if (!manual && !updateManager.isAutoCheckDue()) {
            updateManager.cachedReleaseIfStillNewer()?.let { release ->
                _updateState.value = if (updateManager.getApkFile(release) != null) {
                    UpdateState.ReadyToInstall(release)
                } else {
                    UpdateState.Available(release)
                }
            }
            return
        }
        // Preserve an already-displayed dialog instead of overwriting it with Loading.
        val hadUpdate = _updateState.value.let {
            it is UpdateState.Available || it is UpdateState.ReadyToInstall
        }
        appCheckJob = viewModelScope.launch {
            if (!hadUpdate) _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual, manual = manual)
                if (release != null) {
                    val existingFile = updateManager.getApkFile(release)
                     if (existingFile != null) {
                         _updateState.value = UpdateState.ReadyToInstall(release)
                     } else {
                         _updateState.value = UpdateState.Available(release)
                     }
                } else {
                    val current = _updateState.value
                    when {
                        manual -> _updateState.value = UpdateState.NoUpdate
                        // Keep whatever the user is already looking at on a throttled/skipped check.
                        current is UpdateState.Available || current is UpdateState.ReadyToInstall -> Unit
                        else -> _updateState.value = UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val current = _updateState.value
                _updateState.value = when {
                    manual -> UpdateState.Error
                    current is UpdateState.Available || current is UpdateState.ReadyToInstall -> current
                    else -> UpdateState.Idle
                }
            }
        }
    }

    fun downloadUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                updateManager.downloadUpdate(release).collect { progress ->
                    _downloadProgress.value = progress
                }
                _isDownloading.value = false
                 _updateState.value = UpdateState.ReadyToInstall(release)
            } catch (e: Exception) {
                e.printStackTrace()
                _isDownloading.value = false
                _updateState.value = UpdateState.Error 
            }
        }
    }

    fun installUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        val file = updateManager.getApkFile(release) ?: return
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    fun dismiss() {
        // Clean up downloaded APK when user dismisses the dialog
        updateManager.clearCache()
        _updateState.value = UpdateState.Idle
    }

    fun ignoreVersion(version: String) {
        updateManager.ignoreVersion(version)
        _updateState.value = UpdateState.Idle
    }
}

// --- UI Components ---

@Composable
fun UpdateDialog(
    release: Release,
    isDownloading: Boolean,
    progress: Float,
    actionLabel: String,
    currentVersion: String,
    titleOverride: String? = null,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onIgnore: () -> Unit
) {
    val bestAsset = UpdateManager.selectBestApkAsset(release.assets)
    val downloadSize = bestAsset?.size ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.size ?: 0L
    val formattedDate = formatDate(release.publishedAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth(),
        icon = {
            Icon(
                imageVector = if (actionLabel == "Install") Icons.Filled.SystemUpdate else Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = titleOverride
                        ?: if (actionLabel == "Install") stringResource(R.string.update_ready_to_install)
                        else stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.tagName.removePrefix("v"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (actionLabel != "Install") {
                    // Show version info for update available state
                    InfoRow(label = stringResource(R.string.update_current_version), value = currentVersion)
                    InfoRow(label = stringResource(R.string.update_latest_version), value = release.tagName.removePrefix("v"))
                    InfoRow(label = stringResource(R.string.update_release_date), value = formattedDate)
                    InfoRow(
                        label = stringResource(R.string.update_size),
                        value = if (downloadSize > 0L) MediaFormatter.formatFileSize(downloadSize) else "--"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.update_release_notes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (release.changelog.isNotEmpty()) {
                        PreviewChangelogNotes(release)
                    } else {
                        MarkdownText(
                            markdown = release.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodySmall)
                        Text(text = "${progress.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (progress >= 0) progress / 100f else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onAction) {
                    Text(if (actionLabel == "Install") stringResource(R.string.update_install) else stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    if (actionLabel != "Install") {
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.update_ignore))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_cancel))
                    }
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDate(dateString: String): String {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )
    for (format in formats) {
        try {
            val date = format.parse(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                return outputFormat.format(date)
            }
        } catch (_: Exception) {
            // Try next format
        }
    }
    return dateString
}

private const val CHANGELOG_URL_TAG = "changelog_url"

/**
 * Preview changelog rendered as native Compose text so only issue/PR ids are accented:
 * commit subjects keep the normal text color (clicking opens the GitHub commit page) and
 * "#123" references are bold + blue (clicking opens the issue/PR page).
 */
@Composable
private fun PreviewChangelogNotes(release: Release) {
    val context = LocalContext.current
    val currentCommitCount = UpdateManager.getCurrentCommitCount()
    val entries = UpdateManager.previewChangelogEntries(release, currentCommitCount)
    if (entries.isEmpty()) {
        MarkdownText(
            markdown = release.body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    val refStyle = SpanStyle(
        color = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
        fontWeight = FontWeight.Bold
    )
    // ClickableText does not pick up the dialog's content color, so resolve it explicitly
    // to keep commit subjects readable in both light and dark themes.
    val messageStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.update_whats_changed),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        entries.forEach { entry ->
            val annotated = remember(entry, refStyle) {
                buildChangelogAnnotatedString(entry, refStyle)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(text = "•", style = messageStyle)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "r${entry.serial}",
                    style = messageStyle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                ClickableText(
                    text = annotated,
                    style = messageStyle,
                    modifier = Modifier.weight(1f),
                    onClick = { offset ->
                        annotated
                            .getStringAnnotations(CHANGELOG_URL_TAG, offset, offset)
                            .firstOrNull()
                            ?.let { openUrl(context, it.item) }
                    }
                )
            }
        }
        val skipped = UpdateManager.previewChangelogSkipped(release, currentCommitCount, entries.size)
        if (skipped > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.update_more_commits, skipped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildChangelogAnnotatedString(
    entry: ChangelogEntry,
    refStyle: SpanStyle
): AnnotatedString = buildAnnotatedString {
    val message = entry.message.trim()
    if (message.isEmpty()) return@buildAnnotatedString
    for (segment in UpdateManager.parseChangelogSegments(message)) {
        when (segment) {
            is ChangelogSegment.Text -> {
                if (entry.sha.isNotBlank()) {
                    withAnnotation(CHANGELOG_URL_TAG, UpdateManager.commitUrl(entry.sha)) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
            is ChangelogSegment.Ref -> {
                withAnnotation(CHANGELOG_URL_TAG, UpdateManager.issueUrl(segment.number)) {
                    withStyle(refStyle) { append("#${segment.number}") }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
