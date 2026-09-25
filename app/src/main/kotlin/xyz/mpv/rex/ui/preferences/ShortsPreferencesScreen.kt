package xyz.mpv.rex.ui.preferences

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import xyz.mpv.rex.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object ShortsPreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val browserPreferences = koinInject<BrowserPreferences>()
        val enableShorts by browserPreferences.enableShorts.collectAsState()
        val autoSwipeShorts by browserPreferences.autoSwipeShorts.collectAsState()
        val enableGlassShortsControls by browserPreferences.enableGlassShortsControls.collectAsState()
        val showShortsBackButton by browserPreferences.showShortsBackButton.collectAsState()
        val includeHorizontal by browserPreferences.includeShortHorizontalVideos.collectAsState()
        val maxDuration by browserPreferences.maxHorizontalVideoDurationMinutes.collectAsState()
        val context = LocalContext.current
        val shortsSourceFolders by browserPreferences.shortsSourceFolders.collectAsState()
        
        var showFolderSelector by remember { mutableStateOf(false) }
        var allFolders by remember { mutableStateOf<List<xyz.mpv.rex.domain.media.model.MediaFolder>>(emptyList()) }
        
        LaunchedEffect(Unit) {
            allFolders = xyz.mpv.rex.utils.storage.CoreMediaScanner.getFlatMediaFolders(context)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.pref_category_rexshorts_settings),
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
                )
            },
        ) { padding ->
            val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
            val lazyListState = rememberPreferenceLazyListState()
            ProvidePreferenceLocals {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
                ) {
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.general))
                    }

                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = listOf(R.string.pref_category_rexshorts_settings, R.string.pref_enable_rexshorts),
                            ) {
                                SwitchPreference(
                                    value = enableShorts,
                                    onValueChange = { browserPreferences.enableShorts.set(it) },
                                    title = { Text(stringResource(R.string.pref_enable_rexshorts)) },
                                    summary = { Text(stringResource(R.string.pref_enable_rexshorts_summary)) },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_auto_swipe_shorts,
                            ) {
                                SwitchPreference(
                                    value = autoSwipeShorts,
                                    onValueChange = { browserPreferences.autoSwipeShorts.set(it) },
                                    title = { Text(stringResource(R.string.pref_auto_swipe_shorts)) },
                                    summary = { Text(stringResource(R.string.pref_auto_swipe_shorts_summary)) },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_enable_glass_shorts_controls,
                            ) {
                                SwitchPreference(
                                    value = enableGlassShortsControls,
                                    onValueChange = { browserPreferences.enableGlassShortsControls.set(it) },
                                    title = { Text(stringResource(R.string.pref_enable_glass_shorts_controls)) },
                                    summary = { Text(stringResource(R.string.pref_enable_glass_shorts_controls_summary)) },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_show_shorts_back_button,
                            ) {
                                SwitchPreference(
                                    value = showShortsBackButton,
                                    onValueChange = { browserPreferences.showShortsBackButton.set(it) },
                                    title = { Text(stringResource(R.string.pref_show_shorts_back_button)) },
                                    summary = { Text(stringResource(R.string.pref_show_shorts_back_button_summary)) },
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_category_discovery))
                    }

                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = R.string.pref_include_short_horizontal_videos,
                            ) {
                                SwitchPreference(
                                    value = includeHorizontal,
                                    onValueChange = { browserPreferences.includeShortHorizontalVideos.set(it) },
                                    title = { Text(stringResource(R.string.pref_include_short_horizontal_videos)) },
                                    summary = { Text(stringResource(R.string.pref_include_short_horizontal_videos_summary)) },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_max_horizontal_video_duration,
                            ) {
                                 SliderPreference(
                                    value = maxDuration.toFloat(),
                                    onValueChange = { browserPreferences.maxHorizontalVideoDurationMinutes.set(it.roundToInt()) },
                                    sliderValue = maxDuration.toFloat(),
                                    onSliderValueChange = { browserPreferences.maxHorizontalVideoDurationMinutes.set(it.roundToInt()) },
                                    title = { Text(stringResource(R.string.pref_max_horizontal_video_duration)) },
                                    summary = { 
                                        Text(
                                            text = pluralStringResource(R.plurals.pref_max_horizontal_video_duration_desc, maxDuration, maxDuration),
                                            color = MaterialTheme.colorScheme.outline
                                        ) 
                                    },
                                    valueRange = 1f..10f,
                                    valueSteps = 9,
                                    enabled = includeHorizontal,
                                )
                            }

                            val sourcedAllText = stringResource(R.string.pref_sourced_folders_all)
                            val sourcedCountText = stringResource(R.string.pref_sourced_folders_count, shortsSourceFolders.size)
                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_sourced_folders,
                            ) {
                                Preference(
                                    title = { Text(stringResource(R.string.pref_sourced_folders)) },
                                    summary = {
                                        val text = if (shortsSourceFolders.isEmpty()) {
                                            sourcedAllText
                                        } else {
                                            val names = allFolders.filter { it.path in shortsSourceFolders }.map { it.name }
                                            if (names.isEmpty()) {
                                                sourcedCountText
                                            } else {
                                                stringResource(R.string.pref_sourced_folders_list, names.joinToString())
                                            }
                                        }
                                        Text(text = text, color = MaterialTheme.colorScheme.outline)
                                    },
                                    onClick = { showFolderSelector = true }
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_category_content_management))
                    }

                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_blocked_videos,
                            ) {
                                Preference(
                                    title = { Text(stringResource(R.string.pref_blocked_videos)) },
                                    summary = { 
                                        Text(
                                            text = stringResource(R.string.pref_blocked_videos_summary),
                                            color = MaterialTheme.colorScheme.outline
                                        ) 
                                    },
                                    onClick = { backstack.add(BlockedShortsScreen) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFolderSelector && allFolders.isNotEmpty()) {
            var selectedFolders by remember { mutableStateOf(shortsSourceFolders) }
            AlertDialog(
                onDismissRequest = { showFolderSelector = false },
                title = { Text(stringResource(R.string.pref_select_sourced_folders)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.pref_select_sourced_folders_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(allFolders) { folder ->
                                val isChecked = selectedFolders.contains(folder.path)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFolders = if (isChecked) {
                                                selectedFolders - folder.path
                                            } else {
                                                selectedFolders + folder.path
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedFolders = if (checked == true) {
                                                selectedFolders + folder.path
                                            } else {
                                                selectedFolders - folder.path
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = folder.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            browserPreferences.shortsSourceFolders.set(selectedFolders)
                            showFolderSelector = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFolderSelector = false }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                }
            )
        }
    }
}
