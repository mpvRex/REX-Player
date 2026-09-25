package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.preferences.allPlayerButtons
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SeekbarStyle
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.preferences.components.PlayerButtonChip
import org.koin.compose.koinInject

// Enum to identify which region we are editing
@Serializable
enum class ControlRegion {
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    PORTRAIT_BOTTOM,
    MORE_SHEET,
}

@Serializable
object PlayerControlsPreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val appearancePrefs = koinInject<AppearancePreferences>()
        val playerPrefs = koinInject<PlayerPreferences>()

        val topRState by appearancePrefs.topRightControls.collectAsState()
        val bottomRState by appearancePrefs.bottomRightControls.collectAsState()
        val bottomLState by appearancePrefs.bottomLeftControls.collectAsState()
        val portraitBottomState by appearancePrefs.portraitBottomControls.collectAsState()
        val moreSheetState by appearancePrefs.moreSheetControls.collectAsState()

        val topRightButtons = remember(topRState) {
            appearancePrefs.parseButtons(topRState, mutableSetOf())
        }
        val bottomRightButtons = remember(bottomRState) {
            appearancePrefs.parseButtons(bottomRState, mutableSetOf())
        }
        val bottomLeftButtons = remember(bottomLState) {
            appearancePrefs.parseButtons(bottomLState, mutableSetOf())
        }
        val portraitBottomButtons = remember(portraitBottomState) {
            appearancePrefs.parseButtons(portraitBottomState, mutableSetOf())
        }
        val moreSheetButtons = remember(moreSheetState, topRState, bottomRState, bottomLState, portraitBottomState) {
            val manualOrder = appearancePrefs.parseButtons(moreSheetState, mutableSetOf())
            val landscapeSet = (topRState.split(',') + bottomRState.split(',') + bottomLState.split(','))
                .filter(String::isNotBlank)
                .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
                .toSet()
            val portraitSet = portraitBottomState.split(',')
                .filter(String::isNotBlank)
                .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
                .toSet()

            val intersection = landscapeSet.intersect(portraitSet)
            val orphans = allPlayerButtons.filter { it !in intersection }
            val orderedOrphans = manualOrder.filter { it in orphans }
            val remainingOrphans = orphans.filter { it !in orderedOrphans }
            orderedOrphans + remainingOrphans
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.pref_layout_title),
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
                    // Landscape Controls Section
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_layout_landscape_controls_header))
                    }
                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = listOf(R.string.pref_layout_title, R.string.pref_layout_top_right_controls),
                            ) {
                                Column {
                                    PreferenceCategoryWithEditButton(
                                        title = stringResource(id = R.string.pref_layout_top_right_controls),
                                        onClick = {
                                            backstack.add(ControlLayoutEditorScreen(ControlRegion.TOP_RIGHT))
                                        },
                                    )
                                    PreferenceIconSummary(buttons = topRightButtons)
                                }
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_layout_bottom_right_controls,
                            ) {
                                Column {
                                    PreferenceCategoryWithEditButton(
                                        title = stringResource(id = R.string.pref_layout_bottom_right_controls),
                                        onClick = {
                                            backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_RIGHT))
                                        },
                                    )
                                    PreferenceIconSummary(buttons = bottomRightButtons)
                                }
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_layout_bottom_left_controls,
                            ) {
                                Column {
                                    PreferenceCategoryWithEditButton(
                                        title = stringResource(id = R.string.pref_layout_bottom_left_controls),
                                        onClick = {
                                            backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_LEFT))
                                        },
                                    )
                                    PreferenceIconSummary(buttons = bottomLeftButtons)
                                }
                            }
                        }
                    }

                    // Portrait Controls Section
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_layout_portrait_controls_header))
                    }
                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_layout_portrait_bottom_controls,
                            ) {
                                Column {
                                    PreferenceCategoryWithEditButton(
                                        title = stringResource(id = R.string.pref_layout_portrait_bottom_controls),
                                        onClick = {
                                            backstack.add(ControlLayoutEditorScreen(ControlRegion.PORTRAIT_BOTTOM))
                                        },
                                    )
                                    PreferenceIconSummary(buttons = portraitBottomButtons)
                                }
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_layout_more_sheet_controls_header))
                    }
                    item {
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_layout_more_sheet_controls_title,
                            ) {
                                Column {
                                    PreferenceCategoryWithEditButton(
                                        title = stringResource(R.string.pref_layout_more_sheet_controls_title),
                                        onClick = {
                                            backstack.add(ControlLayoutEditorScreen(ControlRegion.MORE_SHEET))
                                        },
                                    )
                                    PreferenceIconSummary(buttons = moreSheetButtons)
                                }
                            }
                        }
                    }

                    // Seekbar Section
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_seekbar_style_header))
                    }
                    item {
                        val currentSeekbarStyle by appearancePrefs.seekbarStyle.collectAsState()
                        val whiteSeekBar by playerPrefs.whiteSeekBar.collectAsState()
                        val showSeekbarChapters by playerPrefs.showSeekbarChapters.collectAsState()
                        val showSeekbarReadAhead by playerPrefs.showSeekbarReadAhead.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = R.string.pref_seekbar_style_header,
                            ) {
                                Column {
                                    SeekbarStyle.entries.forEachIndexed { index, style ->
                                        ListItem(
                                            headlineContent = {
                                                Text(text = style.name)
                                            },
                                            trailingContent = {
                                                RadioButton(
                                                    selected = currentSeekbarStyle == style,
                                                    onClick = null
                                                )
                                            },
                                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                            modifier = Modifier
                                                .clickable { appearancePrefs.seekbarStyle.set(style) }
                                        )
                                        if (index < SeekbarStyle.entries.size - 1) {
                                            PreferenceDivider()
                                        }
                                    }
                                }
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_player_white_seekbar_title,
                            ) {
                                SwitchPreference(
                                    value = whiteSeekBar,
                                    onValueChange = { playerPrefs.whiteSeekBar.set(it) },
                                    title = {
                                        Text(text = stringResource(R.string.pref_player_white_seekbar_title))
                                    },
                                    summary = {
                                        Text(text = stringResource(R.string.pref_player_white_seekbar_summary))
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_player_show_seekbar_chapters_title,
                            ) {
                                SwitchPreference(
                                    value = showSeekbarChapters,
                                    onValueChange = { playerPrefs.showSeekbarChapters.set(it) },
                                    title = {
                                        Text(text = stringResource(R.string.pref_player_show_seekbar_chapters_title))
                                    },
                                    summary = {
                                        Text(text = stringResource(R.string.pref_player_show_seekbar_chapters_summary))
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_player_show_seekbar_read_ahead_title,
                            ) {
                                SwitchPreference(
                                    value = showSeekbarReadAhead,
                                    onValueChange = { playerPrefs.showSeekbarReadAhead.set(it) },
                                    title = {
                                        Text(text = stringResource(R.string.pref_player_show_seekbar_read_ahead_title))
                                    },
                                    summary = {
                                        Text(text = stringResource(R.string.pref_player_show_seekbar_read_ahead_summary))
                                    },
                                )
                            }
                        }
                    }

                    // Bottom Controls Layout Section
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_controls_layout_header))
                    }
                    item {
                        val bottomControlsBelowSeekbar by playerPrefs.bottomControlsBelowSeekbar.collectAsState()
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.ONLY,
                                highlightKey = R.string.pref_controls_layout_below_seekbar_title,
                            ) {
                                SwitchPreference(
                                    value = bottomControlsBelowSeekbar,
                                    onValueChange = { playerPrefs.bottomControlsBelowSeekbar.set(it) },
                                    title = {
                                        Text(text = stringResource(R.string.pref_controls_layout_below_seekbar_title))
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(
                                                if (bottomControlsBelowSeekbar)
                                                    R.string.pref_controls_layout_below_seekbar_summary_true
                                                else
                                                    R.string.pref_controls_layout_below_seekbar_summary_false
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Appearance Section
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_player_appearance_header))
                    }
                    item {
                        val enableBounceAnimation by appearancePrefs.enableBounceAnimation.collectAsState()
                        val hidePlayerButtonsBackground by appearancePrefs.hidePlayerButtonsBackground.collectAsState()
                        val showNextPreviousButtons by appearancePrefs.showNextPreviousButtons.collectAsState()
                        val enableGlassPlayerControls by appearancePrefs.enableGlassPlayerControls.collectAsState()
                        val enableGlassSeekbarBackground by appearancePrefs.enableGlassSeekbarBackground.collectAsState()
                        val playerAlwaysDarkMode by appearancePrefs.playerAlwaysDarkMode.collectAsState()
                        val playerTimeToDisappear by playerPrefs.playerTimeToDisappear.collectAsState()
                        val predefinedTimeValues = listOf(0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000)
                        val isCustomTimeValue = !predefinedTimeValues.contains(playerTimeToDisappear)
                        val offLabel = stringResource(R.string.generic_off)
                        val customLabel = stringResource(R.string.generic_custom)
                        var showCustomTimeDialog by remember { mutableStateOf(false) }
                        var customTimeValue by remember { mutableStateOf("") }
                        val showControlsOnPlay by playerPrefs.showControlsOnPlay.collectAsState()
                        val playerGradientOpacity by playerPrefs.playerGradientOpacity.collectAsState()

                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST,
                                highlightKey = R.string.pref_appearance_enable_bounce_animation_title,
                            ) {
                                SwitchPreference(
                                    value = enableBounceAnimation,
                                    onValueChange = { appearancePrefs.enableBounceAnimation.set(it) },
                                    title = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_bounce_animation_title)
                                        )
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_bounce_animation_summary)
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_hide_player_buttons_background_title,
                            ) {
                                SwitchPreference(
                                    value = hidePlayerButtonsBackground,
                                    onValueChange = { appearancePrefs.hidePlayerButtonsBackground.set(it) },
                                    title = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_title),
                                        )
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_summary),
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_show_next_previous_buttons_title,
                            ) {
                                SwitchPreference(
                                    value = showNextPreviousButtons,
                                    onValueChange = { appearancePrefs.showNextPreviousButtons.set(it) },
                                    title = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_show_next_previous_buttons_title),
                                        )
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_show_next_previous_buttons_summary),
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_enable_glass_player_controls_title,
                            ) {
                                SwitchPreference(
                                    value = enableGlassPlayerControls,
                                    onValueChange = { appearancePrefs.enableGlassPlayerControls.set(it) },
                                    title = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_glass_player_controls_title),
                                        )
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_glass_player_controls_summary),
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_enable_glass_seekbar_title,
                            ) {
                                SwitchPreference(
                                    value = enableGlassSeekbarBackground,
                                    onValueChange = { appearancePrefs.enableGlassSeekbarBackground.set(it) },
                                    enabled = enableGlassPlayerControls,
                                    title = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_glass_seekbar_title),
                                        )
                                    },
                                    summary = {
                                        Text(
                                            text = stringResource(id = R.string.pref_appearance_enable_glass_seekbar_summary),
                                        )
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_player_always_dark_mode_title,
                            ) {
                                SwitchPreference(
                                    value = if (enableGlassPlayerControls) true else playerAlwaysDarkMode,
                                    onValueChange = { appearancePrefs.playerAlwaysDarkMode.set(it) },
                                    enabled = !enableGlassPlayerControls,
                                    onDisabledClick = {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.pref_appearance_player_always_dark_mode_disabled_toast),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    title = {
                                        Text(text = stringResource(R.string.pref_appearance_player_always_dark_mode_title))
                                    },
                                    summary = {
                                        Text(text = stringResource(R.string.pref_appearance_player_always_dark_mode_summary))
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_show_controls_on_play_title,
                            ) {
                                SwitchPreference(
                                    value = showControlsOnPlay,
                                    onValueChange = { playerPrefs.showControlsOnPlay.set(it) },
                                    title = {
                                        Text(text = stringResource(R.string.pref_appearance_show_controls_on_play_title))
                                    },
                                    summary = {
                                        Text(text = stringResource(R.string.pref_appearance_show_controls_on_play_summary))
                                    },
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE,
                                highlightKey = R.string.pref_appearance_player_gradient_opacity_title,
                            ) {
                                SliderPreference(
                                    value = playerGradientOpacity,
                                    onValueChange = { playerPrefs.playerGradientOpacity.set(it.toFixed(2)) },
                                    title = { Text(stringResource(R.string.pref_appearance_player_gradient_opacity_title)) },
                                    valueRange = 0f..1f,
                                    summary = {
                                        val opacityPercent = (playerGradientOpacity * 100).toInt()
                                        Text(
                                            text = stringResource(
                                                R.string.pref_appearance_player_gradient_opacity_current,
                                                opacityPercent
                                            ),
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    onSliderValueChange = { playerPrefs.playerGradientOpacity.set(it.toFixed(2)) },
                                    sliderValue = playerGradientOpacity,
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST,
                                highlightKey = R.string.pref_player_display_hide_player_control_time,
                            ) {
                                ListPreference(
                                    value = if (isCustomTimeValue) -1 else playerTimeToDisappear,
                                    onValueChange = { newValue ->
                                        if (newValue == -1) {
                                            customTimeValue = playerTimeToDisappear.toString()
                                            showCustomTimeDialog = true
                                        } else {
                                            playerPrefs.playerTimeToDisappear.set(newValue)
                                        }
                                    },
                                    values = predefinedTimeValues + listOf(-1),
                                    valueToText = { value ->
                                        when (value) {
                                            0 -> AnnotatedString(offLabel)
                                            -1 -> AnnotatedString(customLabel)
                                            else -> AnnotatedString("$value ms")
                                        }
                                    },
                                    title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
                                    summary = {
                                        Text(
                                            text = when {
                                                playerTimeToDisappear == 0 -> offLabel
                                                isCustomTimeValue -> {
                                                    stringResource(
                                                        R.string.pref_player_display_custom_time_summary,
                                                        playerTimeToDisappear
                                                    )
                                                }
                                                else -> {
                                                    stringResource(
                                                        R.string.pref_player_display_time_ms_format,
                                                        playerTimeToDisappear
                                                    )
                                                }
                                            },
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                )
                            }
                        }

                        if (showCustomTimeDialog) {
                            AlertDialog(
                                onDismissRequest = { showCustomTimeDialog = false },
                                title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
                                text = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pref_player_display_custom_hide_time_dialog_message),
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                        OutlinedTextField(
                                            value = customTimeValue,
                                            onValueChange = { customTimeValue = it },
                                            label = { Text(stringResource(id = R.string.milliseconds)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val value = customTimeValue.toIntOrNull()
                                            if (value != null && value in 0..1000000000) {
                                                playerPrefs.playerTimeToDisappear.set(value)
                                                showCustomTimeDialog = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.generic_ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCustomTimeDialog = false }) {
                                        Text(stringResource(R.string.generic_cancel))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Custom composable for the category header with an Edit button.
     */
    @Composable
    private fun PreferenceCategoryWithEditButton(
        title: String,
        onClick: () -> Unit,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.generic_edit_content_description, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    /**
     * Custom composable to show a row of icons for the summary.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PreferenceIconSummary(buttons: List<PlayerButton>) {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            if (buttons.isEmpty()) {
                Text(
                    stringResource(R.string.generic_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                buttons.forEach { button ->
                    PlayerButtonChip(
                        button = button,
                        enabled = true,
                        onClick = null,
                        badgeIcon = null,
                        badgeColor = null
                    )
                }
            }
        }
    }
}
