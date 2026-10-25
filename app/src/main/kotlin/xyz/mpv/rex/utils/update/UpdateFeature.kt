package xyz.mpv.rex.utils.update
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.BuildConfig
import kotlinx.coroutines.Dispatchers
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

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val PREVIEW_PAGE_URL = "https://mpvrex.github.io/REX-Player/"
        const val PREVIEW_JSON_URL = "https://mpvrex.github.io/REX-Player/version.json"
        const val RELEASE_API_URL = "https://api.github.com/repos/mpvRex/REX-Player/releases/latest"
        const val REPO_COMMIT_URL = "https://github.com/mpvRex/REX-Player/commit"
        const val REPO_ISSUE_URL = "https://github.com/mpvRex/REX-Player/issues"

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

        // --- Preview changelog → release notes markdown ---

        private val markdownEscapes = Regex("""([\`*_\[\]!~<>])""")
        private val issueRef = Regex("""#(\d+)""")

        fun escapeMarkdown(text: String): String {
            return markdownEscapes.replace(text) { "\\${it.groupValues[1]}" }
        }

        // Adjacent-span links: the message text links to the GitHub commit page and each
        // "#123" reference links to the issue/PR. Markdown forbids nested links, so the
        // message is split into ordered spans instead of wrapping the whole line.
        private fun commitSpan(entry: ChangelogEntry, text: String): String {
            if (text.isEmpty()) return ""
            // Keep pure-separator runs (")" etc.) plain; only real message text is a link.
            if (entry.sha.isBlank() || !text.any { it.isLetterOrDigit() }) {
                return escapeMarkdown(text)
            }
            return "[${escapeMarkdown(text)}]($REPO_COMMIT_URL/${entry.sha})"
        }

        private fun buildEntryLine(entry: ChangelogEntry): String {
            val line = StringBuilder("- **r").append(entry.serial).append("** ")
            val message = entry.message.trim()
            if (message.isEmpty()) return line.toString().trim()
            var index = 0
            for (match in issueRef.findAll(message)) {
                line.append(commitSpan(entry, message.substring(index, match.range.first)))
                line.append('[')
                    .append(match.value)
                    .append("](")
                    .append(REPO_ISSUE_URL)
                    .append('/')
                    .append(match.groupValues[1])
                    .append(')')
                index = match.range.last + 1
            }
            line.append(commitSpan(entry, message.substring(index)))
            return line.toString()
        }

        /**
         * Builds release notes for a preview update: commit messages between the installed
         * build (serial > currentCommitCount) and the available build. Entries older than
         * the changelog window are summarised in a trailing "and N more" note. Falls back
         * to the static body when no structured changelog is available.
         */
        fun buildPreviewReleaseNotes(release: Release, currentCommitCount: Int): String {
            if (release.changelog.isEmpty()) return release.body
            val shown = release.changelog
                .filter { it.serial > currentCommitCount }
                .sortedByDescending { it.serial }
            if (shown.isEmpty()) return release.body
            val notes = StringBuilder("### What's changed\n")
            shown.forEach { notes.append(buildEntryLine(it)).append('\n') }
            // Count every commit between the installed build and the release tag — not just
            // the entries in the (capped, possibly merge-less) changelog window.
            val latestSerial = parseCommitCount(release.tagName)
            val totalBetween = if (latestSerial != null) {
                latestSerial - currentCommitCount
            } else {
                shown.first().serial - currentCommitCount
            }
            val skipped = totalBetween - shown.size
            if (skipped > 0) {
                notes.append("\n*…and ").append(skipped)
                    .append(" more commits (only the latest 20 are shown)*\n")
            }
            return notes.toString().trim()
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

    suspend fun checkForUpdate(forceShow: Boolean = false): Release? {
        // Return null immediately if update feature is disabled (F-Droid flavor)
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
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
        val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")
        val remoteVersion = release.tagName.removePrefix("v")
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)

        // If this version was ignored, don't show it unless forced (manual check)
        if (!forceShow && ignoredVersion == remoteVersion) {
            return null
        }

        return if (isNewerVersion(remoteVersion, currentVersion)) {
            ensureAssetSizes(release)
        } else {
            null
        }
    }

    private suspend fun checkPreviewUpdate(forceShow: Boolean): Release? {
        val release = getLatestPreviewRelease() ?: return null
        val remoteVersion = release.tagName.removePrefix("v")
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)

        if (!forceShow && ignoredVersion == remoteVersion) {
            return null
        }

        val currentCommitCount = getCurrentCommitCount()
        return if (isNewerPreviewVersion(remoteVersion, currentCommitCount)) {
            val sizedRelease = ensureAssetSizes(release)
            sizedRelease.copy(body = buildPreviewReleaseNotes(release, currentCommitCount))
        } else {
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
        
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ignored_version", version)
            .apply()
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body.string()
        json.decodeFromString<Release>(responseBody)
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
        if (enabled) checkCompanionUpdate(manual = false)
    }

    fun refreshCompanionInstalled() {
        _isCompanionInstalled.value = ytdlpUpdateManager.isCompanionInstalled()
    }

    fun ytdlpInstalledVersion(): String? = ytdlpUpdateManager.installedVersion()

    fun checkCompanionUpdate(manual: Boolean = false) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        refreshCompanionInstalled()
        if (!_isCompanionInstalled.value) return
        viewModelScope.launch {
            _companionUpdateState.value = UpdateState.Loading
            try {
                val release = ytdlpUpdateManager.checkForUpdate(forceShow = manual)
                _companionUpdateState.value = when {
                    release != null -> UpdateState.Available(release)
                    manual -> UpdateState.NoUpdate
                    else -> UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _companionUpdateState.value = if (manual) UpdateState.Error else UpdateState.Idle
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
        // Only initialize auto-update if feature is enabled
        if (BuildConfig.ENABLE_UPDATE_FEATURE && isAutoUpdateEnabled.value) {
            checkForUpdate(manual = false)
        }
        // Companion auto-check only when addon is present (your conditional).
        if (BuildConfig.ENABLE_UPDATE_FEATURE && _isCompanionInstalled.value && _isYtdlpAutoUpdateEnabled.value) {
            checkCompanionUpdate(manual = false)
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
        
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual)
                if (release != null) {
                    val existingFile = updateManager.getApkFile(release)
                     if (existingFile != null) {
                         _updateState.value = UpdateState.ReadyToInstall(release)
                     } else {
                         _updateState.value = UpdateState.Available(release)
                     }
                } else {
                    if (manual) _updateState.value = UpdateState.NoUpdate
                    else _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual) _updateState.value = UpdateState.Error
                else _updateState.value = UpdateState.Idle
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
                    MarkdownText(
                        markdown = release.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
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
