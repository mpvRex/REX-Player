package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.MultiChoiceSegmentedButton
import xyz.mpv.rex.ui.preferences.components.ThemePicker
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.semantics.Role
import xyz.mpv.rex.utils.locale.LocaleHelper
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Serializable
object AppearancePreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val preferences = koinInject<AppearancePreferences>()
        val browserPreferences = koinInject<BrowserPreferences>()
        val backstack = LocalBackStack.current
        val systemDarkTheme = isSystemInDarkTheme()

        val darkMode by preferences.darkMode.collectAsState()
        val appTheme by preferences.appTheme.collectAsState()

        // Determine if we're in dark mode for theme preview
        val isDarkMode = when (darkMode) {
            DarkMode.Dark -> true
            DarkMode.Light -> false
            DarkMode.System -> systemDarkTheme
        }

        val context = LocalContext.current
        var showLanguageDialog by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.pref_appearance_title),
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
            ProvidePreferenceLocals {
                LazyColumn(
                    state = rememberPreferenceLazyListState(),
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
                ) {
                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_language_title))
                    }

                    item {
                        val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_appearance_language_title,
                            ) {
                                Preference(
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_language_title)) },
                                    summary = {
                                        Text(
                                            text = if (currentLanguage.code.isEmpty()) {
                                                stringResource(R.string.system_default)
                                            } else {
                                                "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
                                            },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = { showLanguageDialog = true },
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_theme))
                    }

                    item {
                        val amoledMode by preferences.amoledMode.collectAsState()
                        val useSystemFont by preferences.useSystemFont.collectAsState()
                        val hidePlayFabShowInHeader by preferences.hidePlayFabShowInHeader.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = listOf(
                                    R.string.pref_appearance_title,
                                    R.string.pref_appearance_amoled_mode_title,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    MultiChoiceSegmentedButton(
                                        choices = DarkMode.entries.map { stringResource(it.titleRes) }.toImmutableList(),
                                        selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
                                        onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
                                    )
                                    ThemePicker(
                                        currentTheme = appTheme,
                                        isDarkMode = isDarkMode,
                                        onThemeSelected = { preferences.appTheme.set(it) },
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                    SwitchPreference(
                                        value = amoledMode,
                                        onValueChange = { preferences.amoledMode.set(it) },
                                        title = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_title)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = R.string.pref_appearance_amoled_mode_summary),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        },
                                        enabled = darkMode != DarkMode.Light,
                                    )
                                }
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_use_system_font_title,
                            ) {
                                SwitchPreference(
                                    value = useSystemFont,
                                    onValueChange = { preferences.useSystemFont.set(it) },
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_use_system_font_title)) },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_use_system_font_summary),
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_appearance_hide_play_fab_show_in_header_title,
                            ) {
                                SwitchPreference(
                                    value = hidePlayFabShowInHeader,
                                    onValueChange = { preferences.hidePlayFabShowInHeader.set(it) },
                                    title = { Text(text = stringResource(id = R.string.pref_appearance_hide_play_fab_show_in_header_title)) },
                                    summary = { Text(text = stringResource(id = R.string.pref_appearance_hide_play_fab_show_in_header_summary)) },
                                )
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_bottom_nav))
                    }

                    item {
                        val enableShorts by browserPreferences.enableShorts.collectAsState()
                        val enableTabYou by browserPreferences.enableTabYou.collectAsState()
                        val enableTabRecents by browserPreferences.enableTabRecents.collectAsState()
                        val enableTabPlaylists by browserPreferences.enableTabPlaylists.collectAsState()
                        val enableTabNetwork by browserPreferences.enableTabNetwork.collectAsState()
                        val bottomNavOrder by browserPreferences.bottomNavOrder.collectAsState()

                        val orderedTabIds = remember(bottomNavOrder) {
                            browserPreferences.getBottomNavOrder()
                        }

                        val visibleTabConfigs = remember(
                            orderedTabIds,
                            enableShorts,
                            enableTabYou,
                            enableTabRecents,
                            enableTabPlaylists,
                            enableTabNetwork
                        ) {
                            val items = mutableListOf<BottomNavTabItem>()
                            for (id in orderedTabIds) {
                                when (id) {
                                    "home" -> items.add(
                                        BottomNavTabItem(
                                            id = "home",
                                            titleRes = R.string.pref_appearance_tab_home_title,
                                            summaryRes = R.string.pref_appearance_tab_home_summary,
                                            value = true,
                                            enabled = false,
                                            onValueChange = {}
                                        )
                                    )
                                    "shorts" -> items.add(
                                        BottomNavTabItem(
                                            id = "shorts",
                                            titleRes = R.string.pref_appearance_tab_shorts_title,
                                            summaryRes = R.string.pref_appearance_tab_shorts_summary,
                                            value = enableShorts,
                                            enabled = true,
                                            onValueChange = { browserPreferences.enableShorts.set(it) }
                                        )
                                    )
                                    "recents" -> if (!enableTabYou) {
                                        items.add(
                                            BottomNavTabItem(
                                                id = "recents",
                                                titleRes = R.string.pref_appearance_tab_recents_title,
                                                summaryRes = R.string.pref_appearance_tab_recents_summary,
                                                value = enableTabRecents,
                                                enabled = true,
                                                onValueChange = { browserPreferences.enableTabRecents.set(it) }
                                            )
                                        )
                                    }
                                    "playlists" -> if (!enableTabYou) {
                                        items.add(
                                            BottomNavTabItem(
                                                id = "playlists",
                                                titleRes = R.string.pref_appearance_tab_playlists_title,
                                                summaryRes = R.string.pref_appearance_tab_playlists_summary,
                                                value = enableTabPlaylists,
                                                enabled = true,
                                                onValueChange = { browserPreferences.enableTabPlaylists.set(it) }
                                            )
                                        )
                                    }
                                    "network" -> items.add(
                                        BottomNavTabItem(
                                            id = "network",
                                            titleRes = R.string.pref_appearance_tab_network_title,
                                            summaryRes = R.string.pref_appearance_tab_network_summary,
                                            value = enableTabNetwork,
                                            enabled = true,
                                            onValueChange = { browserPreferences.enableTabNetwork.set(it) }
                                        )
                                    )
                                    "you" -> items.add(
                                        BottomNavTabItem(
                                            id = "you",
                                            titleRes = R.string.pref_appearance_tab_you_title,
                                            summaryRes = R.string.pref_appearance_tab_you_summary,
                                            value = enableTabYou,
                                            enabled = true,
                                            onValueChange = { browserPreferences.enableTabYou.set(it) }
                                        )
                                    )
                                }
                            }
                            items
                        }

                        sh.calvin.reorderable.ReorderableColumn(
                            list = visibleTabConfigs,
                            onSettle = { fromIndex, toIndex ->
                                val updated = visibleTabConfigs.map { it.id }.toMutableList()
                                val moved = updated.removeAt(fromIndex)
                                updated.add(toIndex, moved)
                                val fullOrder = browserPreferences.getBottomNavOrder().toMutableList()
                                val reorderedFull = updated + fullOrder.filterNot { it in updated }
                                browserPreferences.setBottomNavOrder(reorderedFull)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)
                        ) { index, item, isDragging ->
                            ReorderableItem {
                                val position = when {
                                    visibleTabConfigs.size == 1 -> GroupPosition.ONLY
                                    index == 0 -> GroupPosition.FIRST
                                    index == visibleTabConfigs.lastIndex -> GroupPosition.LAST
                                    else -> GroupPosition.MIDDLE
                                }

                                val elevation by androidx.compose.animation.core.animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 1.dp,
                                    label = "elevation"
                                )

                                GroupedPreferenceCard(
                                    position = position,
                                    highlightKey = item.titleRes,
                                    tonalElevation = elevation,
                                ) {
                                    SwitchPreference(
                                        value = item.value,
                                        onValueChange = item.onValueChange,
                                        enabled = item.enabled,
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = stringResource(R.string.drag_to_reorder),
                                                modifier = Modifier
                                                    .draggableHandle()
                                                    .padding(end = 4.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        },
                                        title = { Text(text = stringResource(id = item.titleRes)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = item.summaryRes),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }


                }
            }
        }

        if (showLanguageDialog) {
            val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
            var selectedCode by remember { mutableStateOf(currentCode) }
            val languages = remember { LocaleHelper.getSupportedLanguages(context) }

            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.pref_appearance_language_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup(),
                    ) {
                        languages.forEach { language ->
                            val isSelected = selectedCode == language.code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { selectedCode = language.code },
                                        role = Role.RadioButton,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = language.nativeName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (language.code.isNotEmpty()) {
                                        Text(
                                            text = language.localizedName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            LocaleHelper.setAppLanguage(context, selectedCode)
                            showLanguageDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.generic_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp),
            )
        }
    }
}

private data class BottomNavTabItem(
    val id: String,
    val titleRes: Int,
    val summaryRes: Int,
    val value: Boolean,
    val enabled: Boolean,
    val onValueChange: (Boolean) -> Unit,
)
