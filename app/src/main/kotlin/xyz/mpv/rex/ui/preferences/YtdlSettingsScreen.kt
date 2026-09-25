package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.ytdl.YtDlClient
import xyz.mpv.rex.domain.ytdl.model.ResolvedStream
import xyz.mpv.rex.domain.ytdl.model.YtdlpStatus
import xyz.mpv.rex.preferences.YtdlPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object YtdlSettingsScreen : Screen {
    private const val ADDON_GITHUB_RELEASES_URL = "https://github.com/mpvRex/REX-Ytdlp/releases"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val ytDlClient = koinInject<YtDlClient>()
        val preferences = koinInject<YtdlPreferences>()
        val backstack = LocalBackStack.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var isAddonInstalled by remember { mutableStateOf(ytDlClient.isAddonInstalled()) }
        var ytdlpStatus by remember { mutableStateOf<YtdlpStatus?>(null) }
        var isRefreshingStatus by remember { mutableStateOf(false) }

        // Update dialog state
        var showUpdateDialog by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }
        var updateLogs by remember { mutableStateOf("") }
        var updateSuccess by remember { mutableStateOf<Boolean?>(null) }

        // Test extraction state
        var testUrl by remember { mutableStateOf("") }
        var isTestingExtraction by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<ResolvedStream?>(null) }

        // Load status when addon is installed
        fun refreshStatus() {
            scope.launch {
                isRefreshingStatus = true
                isAddonInstalled = ytDlClient.isAddonInstalled()
                if (isAddonInstalled) {
                    ytdlpStatus = ytDlClient.getStatus()
                } else {
                    ytdlpStatus = null
                }
                isRefreshingStatus = false
            }
        }

        LaunchedEffect(Unit) {
            refreshStatus()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "yt-dlp",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshStatus() }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                )
            }
        ) { padding ->
            val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
            val lazyListState = rememberPreferenceLazyListState()
            ProvidePreferenceLocals {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = navBarHeight + 24.dp,
                    ),
                ) {
                    // Section 1: Addon Status Card
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .preferenceHighlight("yt-dlp", RoundedCornerShape(24.dp))
                        ) {
                            if (!isAddonInstalled) {
                                AddonNotInstalledCard(
                                    onDownloadClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ADDON_GITHUB_RELEASES_URL))
                                        context.startActivity(intent)
                                    },
                                    onRefreshClick = { refreshStatus() }
                                )
                            } else {
                                AddonInstalledCard(
                                    status = ytdlpStatus,
                                    isRefreshing = isRefreshingStatus,
                                    onInstall = {
                                        showUpdateDialog = true
                                        isUpdating = true
                                        updateLogs = "Installing yt-dlp into companion add-on...\n"
                                        updateSuccess = null
                                        scope.launch {
                                            val success = ytDlClient.runInstall { logMsg ->
                                                updateLogs += logMsg
                                            }
                                            isUpdating = false
                                            updateSuccess = success
                                            refreshStatus()
                                        }
                                    },
                                    onCheckUpdate = { nightly ->
                                        showUpdateDialog = true
                                        isUpdating = true
                                        updateLogs = if (nightly) "Updating yt-dlp to nightly channel...\n" else "Checking for yt-dlp updates...\n"
                                        updateSuccess = null
                                        scope.launch {
                                            val success = ytDlClient.runUpdate(nightly) { logMsg ->
                                                updateLogs += logMsg
                                            }
                                            isUpdating = false
                                            updateSuccess = success
                                            refreshStatus()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Section 2: Quality & Codec Preferences
                    item {
                        PreferenceSectionHeader(title = "Stream Quality")
                    }

                    item {
                        val quality by preferences.qualityPreference.collectAsState()
                        val geoBypass by preferences.geoBypass.collectAsState()
                        val preferNightly by preferences.preferNightly.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = "Resolution Preference",
                            ) {
                                ListPreference(
                                    value = quality,
                                    onValueChange = { preferences.qualityPreference.set(it) },
                                    values = listOf("auto", "2160", "1440", "1080", "720", "480", "audio_only"),
                                    valueToText = {
                                        AnnotatedString(
                                            when (it) {
                                                "2160" -> "4K (2160p)"
                                                "1440" -> "2K (1440p)"
                                                "1080" -> "Full HD (1080p)"
                                                "720" -> "HD (720p)"
                                                "480" -> "SD (480p)"
                                                "audio_only" -> "Audio Only"
                                                else -> "Auto / Best"
                                            }
                                        )
                                    },
                                    title = { Text("Resolution Preference") },
                                    summary = {
                                        val label = when (quality) {
                                            "2160" -> "4K (2160p)"
                                            "1440" -> "2K (1440p)"
                                            "1080" -> "Full HD (1080p)"
                                            "720" -> "HD (720p)"
                                            "480" -> "SD (480p)"
                                            "audio_only" -> "Audio Only"
                                            else -> "Auto / Best"
                                        }
                                        Text(label, color = MaterialTheme.colorScheme.outline)
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = "Geo-Bypass",
                            ) {
                                SwitchPreference(
                                    value = geoBypass,
                                    onValueChange = { preferences.geoBypass.set(it) },
                                    title = { Text("Geo-Bypass") },
                                    summary = { Text("Bypass geographic video restrictions where possible", color = MaterialTheme.colorScheme.outline) },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = "Prefer Nightly Channel",
                            ) {
                                SwitchPreference(
                                    value = preferNightly,
                                    onValueChange = { preferences.preferNightly.set(it) },
                                    title = { Text("Prefer Nightly Channel") },
                                    summary = { Text("Receive cutting-edge yt-dlp nightly extractor fixes", color = MaterialTheme.colorScheme.outline) },
                                )
                            }
                        }
                    }

                    // Section 3: Web Platform & URL Routing
                    item {
                        PreferenceSectionHeader(title = "URL & Platform Routing")
                    }

                    item {
                        val autoDetectWebPages by preferences.autoDetectWebPages.collectAsState()
                        val customDomains by preferences.customDomains.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = "Auto-detect Web Pages",
                            ) {
                                SwitchPreference(
                                    value = autoDetectWebPages,
                                    onValueChange = { preferences.autoDetectWebPages.set(it) },
                                    title = { Text("Auto-detect Web Pages") },
                                    summary = {
                                        Text(
                                            "Proactively probe unknown URLs to route HTML web pages through yt-dlp",
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = "Custom Supported Domains",
                            ) {
                                TextFieldPreference(
                                    value = customDomains,
                                    onValueChange = { preferences.customDomains.set(it) },
                                    textToValue = { it },
                                    title = { Text("Custom Supported Domains") },
                                    summary = {
                                        Text(
                                            customDomains.ifBlank { "None (uses default web video platforms)" },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    textField = { value, onValueChange, _ ->
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = onValueChange,
                                            label = { Text("Domains (comma or space separated)") },
                                            placeholder = { Text("e.g. example.com, archive.org, peertube.su") },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Section 4: Network & Custom Format
                    item {
                        PreferenceSectionHeader(title = "Network & Advanced")
                    }

                    item {
                        val customFormat by preferences.customFormat.collectAsState()
                        val customUserAgent by preferences.customUserAgent.collectAsState()
                        val proxy by preferences.proxy.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = "Custom Format Selector",
                            ) {
                                TextFieldPreference(
                                    value = customFormat,
                                    onValueChange = { preferences.customFormat.set(it) },
                                    textToValue = { it },
                                    title = { Text("Custom Format Selector") },
                                    summary = {
                                        Text(
                                            customFormat.ifBlank { "Default (uses resolution preference)" },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    textField = { value, onValueChange, _ ->
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = onValueChange,
                                            label = { Text("yt-dlp format selector") },
                                            placeholder = { Text("e.g. bestvideo+bestaudio/best") },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = "Proxy",
                            ) {
                                TextFieldPreference(
                                    value = proxy,
                                    onValueChange = { preferences.proxy.set(it) },
                                    textToValue = { it },
                                    title = { Text("Proxy") },
                                    summary = {
                                        Text(
                                            proxy.ifBlank { "None (direct connection)" },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    textField = { value, onValueChange, _ ->
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = onValueChange,
                                            label = { Text("Proxy URL") },
                                            placeholder = { Text("http://user:pass@host:port or socks5://...") },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = "Custom User-Agent",
                            ) {
                                TextFieldPreference(
                                    value = customUserAgent,
                                    onValueChange = { preferences.customUserAgent.set(it) },
                                    textToValue = { it },
                                    title = { Text("Custom User-Agent") },
                                    summary = {
                                        Text(
                                            customUserAgent.ifBlank { "Default" },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    textField = { value, onValueChange, _ ->
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = onValueChange,
                                            label = { Text("User-Agent Header") },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Section 4: Live Extraction Tester
                    item {
                        PreferenceSectionHeader(title = "Extractor Diagnostic Tool")
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Test URL Extraction",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Paste a video URL to test IPC resolution directly with the add-on.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = testUrl,
                                        onValueChange = { testUrl = it },
                                        label = { Text("Video URL") },
                                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = isAddonInstalled && !isTestingExtraction,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isTestingExtraction = true
                                                testResult = null
                                                val res = ytDlClient.resolveStream(
                                                    testUrl.trim(),
                                                    preferences.buildExtractionOptions(),
                                                )
                                                testResult = res
                                                isTestingExtraction = false
                                            }
                                        },
                                        enabled = isAddonInstalled && testUrl.isNotBlank() && !isTestingExtraction,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (isTestingExtraction) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Resolving via Addon...")
                                        } else {
                                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Test Extraction")
                                        }
                                    }

                                    testResult?.let { res ->
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (res.isSuccess) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = if (res.isSuccess) "Extraction Succeeded" else "Extraction Failed",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (res.isSuccess) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                if (res.isSuccess) {
                                                    Text("Title: ${res.title ?: "N/A"}", fontSize = 12.sp)
                                                    Text("Duration: ${res.durationSeconds}s", fontSize = 12.sp)
                                                    Text("DASH Separate Audio: ${if (res.isDASH) "Yes" else "No"}", fontSize = 12.sp)
                                                    Text("Headers Passed: ${res.httpHeaders.size}", fontSize = 12.sp)
                                                } else {
                                                    Text("Error: ${res.errorMessage ?: "Unknown error"}", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Update Progress Dialog
            if (showUpdateDialog) {
                val scrollState = rememberScrollState()
                LaunchedEffect(updateLogs) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                AlertDialog(
                    onDismissRequest = {
                        if (!isUpdating) showUpdateDialog = false
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isUpdating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Running yt-dlp Task...")
                            } else {
                                val icon = if (updateSuccess == true) Icons.Outlined.CheckCircle else Icons.Outlined.Warning
                                val tint = if (updateSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Icon(icon, contentDescription = null, tint = tint)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(if (updateSuccess == true) "Task Completed" else "Task Result")
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = updateLogs.ifBlank { "Waiting for logs..." },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showUpdateDialog = false },
                            enabled = !isUpdating,
                        ) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun AddonNotInstalledCard(
        onDownloadClick: () -> Unit,
        onRefreshClick: () -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Add-on Not Installed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The REX Ytdlp add-on provides the native Python runtime and yt-dlp scraper in a headless companion package. It has no launcher icon and runs safely in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Get Add-on")
                    }

                    OutlinedButton(
                        onClick = onRefreshClick,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun AddonInstalledCard(
        status: YtdlpStatus?,
        isRefreshing: Boolean,
        onInstall: () -> Unit,
        onCheckUpdate: (nightly: Boolean) -> Unit,
    ) {
        val isInstalled = status?.isInstalled == true && !status.version.isNullOrBlank()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "REX Ytdlp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isInstalled) "Status: Connected & Ready" else "Status: Connected • yt-dlp Missing",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isInstalled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("yt-dlp Core", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = status.version.takeIf { !it.isNullOrBlank() } ?: "Checking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column {
                                Text("Channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = status.channel.takeIf { !it.isNullOrBlank() } ?: "STABLE",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column {
                                Text("Git Commit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = status.shortCommitHash.takeIf { !it.isNullOrBlank() } ?: "Release",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onCheckUpdate(false) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onCheckUpdate(true) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nightly")
                            }

                            OutlinedButton(
                                onClick = onInstall,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reinstall")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "The companion add-on is active, but the yt-dlp scraper needs to be downloaded to enable web streaming.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Install yt-dlp")
                    }
                }
            }
        }
    }
}
