package xyz.mpv.rex.utils.update
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import xyz.mpv.rex.utils.media.MediaFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class Asset(
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long
)

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    private val client = clientWithUserAgent()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    companion object {
        const val RELEASE_API_URL = "https://api.github.com/repos/mpvRex/REX-Player/releases/latest"

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
        fun isCachedReleaseStillNewer(release: Release, currentVersionName: String): Boolean {
            return isNewerVersion(release.tagName.removePrefix("v"), currentVersionName.replace("-dev", ""))
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

        /**
         * Hosts the app is willing to point users at, and to measure, for releases.
         *
         * Both release manifests are remote documents, so a tampered or compromised
         * response must not be able to send the user anywhere it likes. Matching is
         * strict: exact host, `https` only, no userinfo, no port.
         */
        private val TRUSTED_RELEASE_HOSTS = setOf(
            "github.com",
            "api.github.com",
            "objects.githubusercontent.com",
            "mpvrex.github.io",
        )

        /** Path prefixes allowed per trusted host; anything else is rejected. */
        private val TRUSTED_RELEASE_PATHS = mapOf(
            "github.com" to listOf("/mpvRex/REX-Player/", "/mpvRex/REX-Ytdlp/"),
            "api.github.com" to listOf("/repos/mpvRex/REX-Player/", "/repos/mpvRex/REX-Ytdlp/"),
            "objects.githubusercontent.com" to listOf("/"),
            "mpvrex.github.io" to listOf("/REX-Player/"),
        )

        /**
         * True if [url] is an https URL on a trusted release host, under an allowed
         * path prefix. Rejects userinfo (`https://github.com@evil.tld/...`) and
         * non-default ports so the parsed host cannot be spoofed.
         */
        fun isTrustedReleaseUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val uri = try {
                java.net.URI(url)
            } catch (_: Exception) {
                return false
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            if (uri.rawUserInfo != null) return false
            if (uri.port != -1) return false
            val host = uri.host?.lowercase() ?: return false
            if (host !in TRUSTED_RELEASE_HOSTS) return false
            val path = uri.path ?: return false
            return TRUSTED_RELEASE_PATHS[host].orEmpty().any { path.startsWith(it) }
        }

        /**
         * Drops any asset whose download URL is not on a trusted host, and clears an
         * untrusted [Release.htmlUrl]. The returned release is safe to render and to
         * issue HTTP requests against.
         */
        fun sanitizeRelease(release: Release): Release = release.copy(
            htmlUrl = release.htmlUrl?.takeIf { isTrustedReleaseUrl(it) },
            assets = release.assets.filter { isTrustedReleaseUrl(it.downloadUrl) }
        )

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

        // Every build, preview included, tracks the same GitHub release feed.
        return checkReleaseUpdate(forceShow)
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
        sanitizeRelease(json.decodeFromString<Release>(responseBody))
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
}

// --- ViewModel ---

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)

    /** Single-flight guard so overlapping triggers never fire duplicate requests. */
    private var appCheckJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

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

    init {
        // Only initialize auto-update if feature is enabled.
        // The checks themselves are throttled to once per hour (persisted across launches),
        // so reopening the app repeatedly within the hour makes no server request.
        if (BuildConfig.ENABLE_UPDATE_FEATURE) {
            // State persisted by a previous app build (cached release + throttle window)
            // is invalid after a self-update; drop it so the checks below run fresh.
            updateManager.dropStateFromPreviousBuild()
            if (isAutoUpdateEnabled.value) {
                checkForUpdate(manual = false)
            }
        }
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Available(val release: Release) : UpdateState()
        object NoUpdate : UpdateState()
        object Error : UpdateState()
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
                _updateState.value = UpdateState.Available(release)
            }
            return
        }
        // Preserve an already-displayed dialog instead of overwriting it with Loading.
        val hadUpdate = _updateState.value is UpdateState.Available
        appCheckJob = viewModelScope.launch {
            if (!hadUpdate) _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual, manual = manual)
                if (release != null) {
                    _updateState.value = UpdateState.Available(release)
                } else {
                    val current = _updateState.value
                    when {
                        manual -> _updateState.value = UpdateState.NoUpdate
                        // Keep whatever the user is already looking at on a throttled/skipped check.
                        current is UpdateState.Available -> Unit
                        else -> _updateState.value = UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val current = _updateState.value
                _updateState.value = when {
                    manual -> UpdateState.Error
                    current is UpdateState.Available -> current
                    else -> UpdateState.Idle
                }
            }
        }
    }

    fun dismiss() {
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
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = titleOverride ?: stringResource(R.string.update_available_title),
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
                MarkdownText(
                    markdown = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onAction) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.update_ignore))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_cancel))
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


