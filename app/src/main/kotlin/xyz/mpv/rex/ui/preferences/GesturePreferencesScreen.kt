package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.player.CustomKeyCodes
import xyz.mpv.rex.ui.player.LongPressSpeedMemoryMode
import xyz.mpv.rex.ui.player.SingleActionGesture
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.FooterPreference
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object GesturePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<GesturePreferences>()
    val playerPreferences = koinInject<PlayerPreferences>()
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val useSingleTapForCenter by preferences.useSingleTapForCenter.collectAsState()
    val useSingleTapForLeftRight by preferences.useSingleTapForLeftRight.collectAsState()

    var showCustomSeekDialog by remember { mutableStateOf(false) }
    var customSeekValue by remember { mutableStateOf("") }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_gesture),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            ) 
          },
          navigationIcon = {
            IconButton(onClick = backstack::navigateBack) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack, 
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
            PreferenceSectionHeader(title = stringResource(R.string.pref_gesture_screen_gestures_title))
          }

          item {
            val brightnessGesture by playerPreferences.brightnessGesture.collectAsState()
            val volumeGesture by playerPreferences.volumeGesture.collectAsState()
            val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
            val panAndZoomEnabled by playerPreferences.panAndZoomEnabled.collectAsState()
            val horizontalSwipeToSeek by playerPreferences.horizontalSwipeToSeek.collectAsState()
            val swipeToSubtitleSeek by playerPreferences.swipeToSubtitleSeek.collectAsState()
            val moveSubtitleByDragging by playerPreferences.moveSubtitleByDragging.collectAsState()
            val pinchToResizeSubtitle by playerPreferences.pinchToResizeSubtitle.collectAsState()
            val horizontalSwipeSensitivity by playerPreferences.horizontalSwipeSensitivity.collectAsState()
            val holdForMultipleSpeed by playerPreferences.holdForMultipleSpeed.collectAsState()
            val longPressSpeedMemoryMode by playerPreferences.longPressSpeedMemoryMode.collectAsState()
            val showDynamicSpeedOverlay by playerPreferences.showDynamicSpeedOverlay.collectAsState()
            val showSpeedIndicatorOverlay by playerPreferences.showSpeedIndicatorOverlay.collectAsState()
            val tapThumbnailToSelect by preferences.tapThumbnailToSelect.collectAsState()
            val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_player_gestures_brightness,
              ) {
                SwitchPreference(
                  value = brightnessGesture,
                  onValueChange = playerPreferences.brightnessGesture::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_brightness)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_volume,
              ) {
                SwitchPreference(
                  value = volumeGesture,
                  onValueChange = playerPreferences.volumeGesture::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_volume)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_pinch_to_zoom,
              ) {
                SwitchPreference(
                  value = pinchToZoomGesture,
                  onValueChange = playerPreferences.pinchToZoomGesture::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_pinch_to_zoom)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_pan_and_zoom,
              ) {
                SwitchPreference(
                  value = panAndZoomEnabled,
                  onValueChange = playerPreferences.panAndZoomEnabled::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_pan_and_zoom)) },
                  summary = { Text(stringResource(R.string.pref_player_gestures_pan_and_zoom_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_horizontal_swipe_to_seek,
              ) {
                SwitchPreference(
                  value = horizontalSwipeToSeek,
                  onValueChange = playerPreferences.horizontalSwipeToSeek::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_horizontal_swipe_to_seek)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_horizontal_swipe_sensitivity,
              ) {
                SliderPreference(
                  value = horizontalSwipeSensitivity,
                  onValueChange = { playerPreferences.horizontalSwipeSensitivity.set(it.toFixed(3)) },
                  title = { Text(stringResource(R.string.pref_player_gestures_horizontal_swipe_sensitivity)) },
                  valueRange = 0.020f..0.1f,
                  summary = {
                    val sensitivityPercent = (horizontalSwipeSensitivity * 1000).toInt()
                    Text(
                      "Current: ${sensitivityPercent}/100 (${if (sensitivityPercent < 30) "Low" else if (sensitivityPercent < 55) "Medium" else "High"})",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onSliderValueChange = { playerPreferences.horizontalSwipeSensitivity.set(it.toFixed(3)) },
                  sliderValue = horizontalSwipeSensitivity,
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_swipe_to_subtitle_seek_title,
              ) {
                SwitchPreference(
                  value = swipeToSubtitleSeek,
                  onValueChange = playerPreferences.swipeToSubtitleSeek::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_swipe_to_subtitle_seek_title)) },
                  summary = { Text(stringResource(R.string.pref_player_gestures_swipe_to_subtitle_seek_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_move_subtitle_by_dragging_title,
              ) {
                SwitchPreference(
                  value = moveSubtitleByDragging,
                  onValueChange = playerPreferences.moveSubtitleByDragging::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_move_subtitle_by_dragging_title)) },
                  summary = { Text(stringResource(R.string.pref_player_gestures_move_subtitle_by_dragging_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_pinch_to_resize_subtitle_title,
              ) {
                SwitchPreference(
                  value = pinchToResizeSubtitle,
                  onValueChange = playerPreferences.pinchToResizeSubtitle::set,
                  title = { Text(stringResource(R.string.pref_player_gestures_pinch_to_resize_subtitle_title)) },
                  summary = { Text(stringResource(R.string.pref_player_gestures_pinch_to_resize_subtitle_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_gestures_hold_for_multiple_speed,
              ) {
                SliderPreference(
                  value = holdForMultipleSpeed,
                  onValueChange = { playerPreferences.holdForMultipleSpeed.set(it.toFixed(2)) },
                  title = { Text(stringResource(R.string.pref_player_gestures_hold_for_multiple_speed)) },
                  valueRange = 0f..6f,
                  summary = {
                    Text(
                      if (holdForMultipleSpeed == 0F) {
                        stringResource(R.string.generic_disabled)
                      } else {
                        "%.2fx".format(holdForMultipleSpeed)
                      },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onSliderValueChange = { playerPreferences.holdForMultipleSpeed.set(it.toFixed(2)) },
                  sliderValue = holdForMultipleSpeed,
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_remember_long_press_speed_title,
              ) {
                ListPreference(
                  value = longPressSpeedMemoryMode,
                  onValueChange = playerPreferences.longPressSpeedMemoryMode::set,
                  values = LongPressSpeedMemoryMode.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(stringResource(R.string.pref_player_remember_long_press_speed_title)) },
                  summary = {
                    Text(
                      stringResource(longPressSpeedMemoryMode.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_player_dynamic_speed_overlay, R.string.pref_dynamic_speed_overlay_title),
              ) {
                SwitchPreference(
                  value = showDynamicSpeedOverlay,
                  onValueChange = playerPreferences.showDynamicSpeedOverlay::set,
                  title = { Text(stringResource(R.string.pref_player_dynamic_speed_overlay)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_player_dynamic_speed_overlay_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_show_speed_indicator_overlay,
              ) {
                SwitchPreference(
                  value = showSpeedIndicatorOverlay,
                  onValueChange = playerPreferences.showSpeedIndicatorOverlay::set,
                  title = { Text(stringResource(R.string.pref_player_show_speed_indicator_overlay)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_player_show_speed_indicator_overlay_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_tap_thumbnail_to_select_title,
              ) {
                SwitchPreference(
                  value = tapThumbnailToSelect,
                  onValueChange = preferences.tapThumbnailToSelect::set,
                  title = { Text(text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_title)) },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_player_controls_allow_gestures_in_panels,
              ) {
                SwitchPreference(
                  value = allowGesturesInPanels,
                  onValueChange = playerPreferences.allowGesturesInPanels::set,
                  title = { Text(text = stringResource(id = R.string.pref_player_controls_allow_gestures_in_panels)) },
                )
              }
            }
          }

          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_gesture_double_tap_title))
          }

          item {
            val doubleTapSeekDuration by preferences.doubleTapToSeekDuration.collectAsState()
            val predefinedValues = listOf(3, 5, 10, 15, 20, 25, 30)
            val isCustomValue = !predefinedValues.contains(doubleTapSeekDuration)
            val customValueLabel = stringResource(R.string.pref_gesture_custom_value)
            val doubleTapSeekAreaWidth by preferences.doubleTapSeekAreaWidth.collectAsState()
            val seekAreaValues = listOf(20, 25, 30, 35, 40, 45)
            val reverseDoubleTap by preferences.reverseDoubleTap.collectAsState()
            val doubleTapChapterToSkip by preferences.doubleTapChapterToSkip.collectAsState()
            val leftDoubleTap by preferences.leftSingleActionGesture.collectAsState()
            val centerDoubleTap by preferences.centerSingleActionGesture.collectAsState()
            val rightDoubleTap by preferences.rightSingleActionGesture.collectAsState()
            val showDoubleTapOvals by playerPreferences.showDoubleTapOvals.collectAsState()
            val showCircularDoubleTapSeek by playerPreferences.showCircularDoubleTapSeek.collectAsState()
            val showSeekTimeWhileSeeking by playerPreferences.showSeekTimeWhileSeeking.collectAsState()
            val preventSeekbarTap by preferences.preventSeekbarTap.collectAsState()
            val useRelativeSeeking by preferences.useRelativeSeeking.collectAsState()
            val enableReleaseToCancel by preferences.enableReleaseToCancel.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_gesture, R.string.pref_player_double_tap_seek_duration),
              ) {
                ListPreference(
                  value = if (isCustomValue) -1 else doubleTapSeekDuration,
                  onValueChange = { newValue ->
                    if (newValue == -1) {
                      customSeekValue = doubleTapSeekDuration.toString()
                      showCustomSeekDialog = true
                    } else {
                      preferences.doubleTapToSeekDuration.set(newValue)
                    }
                  },
                  values = predefinedValues + listOf(-1),
                  valueToText = { value ->
                    if (value == -1) {
                      AnnotatedString(customValueLabel)
                    } else {
                      AnnotatedString("${value}s")
                    }
                  },
                  title = { Text(text = stringResource(id = R.string.pref_player_double_tap_seek_duration)) },
                  summary = {
                    Text(
                      text = if (isCustomValue) {
                        "Custom (${doubleTapSeekDuration}s)"
                      } else {
                        "${doubleTapSeekDuration}s"
                      },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_gesture_double_tap_seek_area_width_title, R.string.pref_double_tap_seek_area_width_title),
              ) {
                ListPreference(
                  value = doubleTapSeekAreaWidth,
                  onValueChange = { preferences.doubleTapSeekAreaWidth.set(it) },
                  values = seekAreaValues,
                  valueToText = { AnnotatedString("${it}%") },
                  title = { Text(text = stringResource(R.string.pref_gesture_double_tap_seek_area_width_title)) },
                  summary = {
                    Text(
                      text = "Current: ${doubleTapSeekAreaWidth}%",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.show_splash_ovals_on_double_tap_to_seek,
              ) {
                SwitchPreference(
                  value = showDoubleTapOvals,
                  onValueChange = playerPreferences.showDoubleTapOvals::set,
                  title = { Text(stringResource(R.string.show_splash_ovals_on_double_tap_to_seek)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_show_circular_double_tap_seek_title,
              ) {
                SwitchPreference(
                  value = showCircularDoubleTapSeek,
                  onValueChange = playerPreferences.showCircularDoubleTapSeek::set,
                  title = { Text(stringResource(R.string.pref_player_show_circular_double_tap_seek_title)) },
                  summary = { Text(stringResource(R.string.pref_player_show_circular_double_tap_seek_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.show_time_on_double_tap_to_seek,
              ) {
                SwitchPreference(
                  value = showSeekTimeWhileSeeking,
                  onValueChange = playerPreferences.showSeekTimeWhileSeeking::set,
                  title = { Text(stringResource(R.string.show_time_on_double_tap_to_seek)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_reverse_double_tap_title,
              ) {
                SwitchPreference(
                  value = reverseDoubleTap,
                  onValueChange = { preferences.reverseDoubleTap.set(it) },
                  title = { Text(text = stringResource(id = R.string.pref_gesture_reverse_double_tap_title)) },
                  summary = { Text(text = stringResource(id = R.string.pref_gesture_reverse_double_tap_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_double_tap_chapter_skip_title,
              ) {
                SwitchPreference(
                  value = doubleTapChapterToSkip,
                  onValueChange = { preferences.doubleTapChapterToSkip.set(it) },
                  title = { Text(text = stringResource(id = R.string.pref_gesture_double_tap_chapter_skip_title)) },
                  summary = { Text(text = stringResource(id = R.string.pref_gesture_double_tap_chapter_skip_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_gesture_double_tap_left_title, R.string.pref_gesture_single_tap_left_title),
              ) {
                ListPreference(
                  value = leftDoubleTap,
                  onValueChange = { preferences.leftSingleActionGesture.set(it) },
                  values = SingleActionGesture.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = {  
                    Text(
                      text =
                        stringResource(
                          if (useSingleTapForLeftRight) R.string.pref_gesture_single_tap_left_title else R.string.pref_gesture_double_tap_left_title,
                        ),
                    )
                  },
                  summary = { Text(
                    text = stringResource(leftDoubleTap.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_gesture_double_tap_center_title, R.string.pref_gesture_single_tap_center_title),
              ) {
                ListPreference(
                  value = centerDoubleTap,
                  onValueChange = { preferences.centerSingleActionGesture.set(it) },
                  values =
                    listOf(
                      SingleActionGesture.None,
                      SingleActionGesture.PlayPause,
                      SingleActionGesture.Custom,
                    ),
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = {
                    Text(
                      text =
                        stringResource(
                          if (useSingleTapForCenter) R.string.pref_gesture_single_tap_center_title else R.string.pref_gesture_double_tap_center_title,
                        ),
                    )
                  },
                  summary = { Text(
                    text = stringResource(centerDoubleTap.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_gesture_double_tap_right_title, R.string.pref_gesture_single_tap_right_title),
              ) {
                ListPreference(
                  value = rightDoubleTap,
                  onValueChange = { preferences.rightSingleActionGesture.set(it) },
                  values = SingleActionGesture.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = {  
                    Text(
                      text =
                        stringResource(
                          if (useSingleTapForLeftRight) R.string.pref_gesture_single_tap_right_title else R.string.pref_gesture_double_tap_right_title,
                        ),
                    )
                  },
                  summary = { Text(
                    text = stringResource(rightDoubleTap.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_use_single_tap_for_center_title,
              ) {
                SwitchPreference(
                  value = useSingleTapForCenter,
                  onValueChange = { preferences.useSingleTapForCenter.set(it) },
                  title = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_single_tap_for_center_title),
                    )
                  },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_single_tap_for_center_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_use_single_tap_for_left_right_title,
              ) {
                SwitchPreference(
                  value = useSingleTapForLeftRight,
                  onValueChange = { preferences.useSingleTapForLeftRight.set(it) },
                  title = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_single_tap_for_left_right_title),
                    )
                  },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_single_tap_for_left_right_summary),
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_instant_controls_toggle_title,
              ) {
                val instantControlsToggle by preferences.instantControlsToggle.collectAsState()
                SwitchPreference(
                  value = instantControlsToggle,
                  onValueChange = { preferences.instantControlsToggle.set(it) },
                  title = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_instant_controls_toggle_title),
                    )
                  },
                  summary = {
                    Text(
                      text = stringResource(
                        id =
                          if (instantControlsToggle) {
                            R.string.pref_gesture_instant_controls_toggle_on_summary
                          } else {
                            R.string.pref_gesture_instant_controls_toggle_off_summary
                          },
                      ),
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_prevent_seekbar_tap_title,
              ) {
                SwitchPreference(
                  value = preventSeekbarTap,
                  onValueChange = { preferences.preventSeekbarTap.set(it) },
                  title = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_prevent_seekbar_tap_title),
                    )
                  },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_prevent_seekbar_tap_summary),
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_use_relative_seeking_title,
              ) {
                SwitchPreference(
                  value = useRelativeSeeking,
                  onValueChange = { preferences.useRelativeSeeking.set(it) },
                  title = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_relative_seeking_title),
                    )
                  },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_gesture_use_relative_seeking_summary),
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_gesture_enable_release_to_cancel_title,
              ) {
                Column {
                  SwitchPreference(
                    value = enableReleaseToCancel,
                    onValueChange = { preferences.enableReleaseToCancel.set(it) },
                    title = {
                      Text(
                        text = stringResource(id = R.string.pref_gesture_enable_release_to_cancel_title),
                      )
                    },
                    summary = {
                      Text(
                        text = stringResource(id = R.string.pref_gesture_enable_release_to_cancel_summary),
                      )
                    },
                  )

                  val doubleTapKeyCodes =
                    listOf(
                      CustomKeyCodes.DoubleTapLeft,
                      CustomKeyCodes.DoubleTapCenter,
                      CustomKeyCodes.DoubleTapRight,
                    )
                  FooterPreference(
                    summary = {
                      val intro = stringResource(R.string.pref_gesture_double_tap_custom_info).substringBefore("\n\n")
                      val annotatedString =
                        buildAnnotatedString {
                          append(intro)
                          append("\n\n")
                          doubleTapKeyCodes.forEachIndexed { index, item ->
                            if (index > 0) append("\n")
                            append("${stringResource(item.titleRes)}: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                              append(item.keyCode)
                            }
                          }
                        }

                      Text(
                        text = annotatedString,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )
                }
              }
            }

            if (showCustomSeekDialog) {
              AlertDialog(
                onDismissRequest = { showCustomSeekDialog = false },
                title = { Text(text = stringResource(id = R.string.pref_player_double_tap_seek_duration)) },
                text = {
                  Column {
                    Text(
                      text = stringResource(R.string.pref_gesture_custom_seek_dialog_hint),
                      modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                      value = customSeekValue,
                      onValueChange = { customSeekValue = it },
                      label = { Text(stringResource(id = R.string.unit_seconds)) },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true,
                    )
                  }
                },
                confirmButton = {
                  TextButton(
                    onClick = {
                      val value = customSeekValue.toIntOrNull()
                      if (value != null && value in 1..120) {
                        preferences.doubleTapToSeekDuration.set(value)
                        showCustomSeekDialog = false
                      }
                    },
                  ) {
                    Text(stringResource(R.string.generic_ok))
                  }
                },
                dismissButton = {
                  TextButton(onClick = { showCustomSeekDialog = false }) {
                    Text(stringResource(R.string.generic_cancel))
                  }
                },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_gesture_media_title))
          }

          item {
            val mediaPreviousGesture by preferences.mediaPreviousGesture.collectAsState()
            val mediaPlayGesture by preferences.mediaPlayGesture.collectAsState()
            val mediaNextGesture by preferences.mediaNextGesture.collectAsState()
            val disableMediaButtons by playerPreferences.disableMediaButtons.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_gesture_media_previous,
              ) {
                ListPreference(
                  value = mediaPreviousGesture,
                  onValueChange = { preferences.mediaPreviousGesture.set(it) },
                  values = SingleActionGesture.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(R.string.pref_gesture_media_previous)) },
                  summary = { Text(
                    text = stringResource(mediaPreviousGesture.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_gesture_media_play,
              ) {
                ListPreference(
                  value = mediaPlayGesture,
                  onValueChange = { preferences.mediaPlayGesture.set(it) },
                  values =
                    listOf(
                      SingleActionGesture.None,
                      SingleActionGesture.PlayPause,
                      SingleActionGesture.Custom,
                    ),
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(R.string.pref_gesture_media_play)) },
                  summary = { Text(
                    text = stringResource(mediaPlayGesture.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_controls_disable_media_buttons_title,
              ) {
                SwitchPreference(
                  value = disableMediaButtons,
                  onValueChange = playerPreferences.disableMediaButtons::set,
                  title = { Text(stringResource(id = R.string.pref_player_controls_disable_media_buttons_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_player_controls_disable_media_buttons_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_gesture_media_next,
              ) {
                Column {
                  ListPreference(
                    value = mediaNextGesture,
                    onValueChange = { preferences.mediaNextGesture.set(it) },
                    values = SingleActionGesture.entries,
                    valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                    title = { Text(text = stringResource(R.string.pref_gesture_media_next)) },
                    summary = { Text(
                      text = stringResource(mediaNextGesture.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    ) },
                  )

                  val mediaKeyCodes =
                    listOf(
                      CustomKeyCodes.MediaPrevious,
                      CustomKeyCodes.MediaPlay,
                      CustomKeyCodes.MediaNext,
                    )
                  FooterPreference(
                    summary = {
                      val intro = stringResource(R.string.pref_gesture_media_custom_info).substringBefore("\n\n")
                      val annotatedString =
                        buildAnnotatedString {
                          append(intro)
                          append("\n\n")
                          mediaKeyCodes.forEachIndexed { index, item ->
                            if (index > 0) append("\n")
                            append("${stringResource(item.titleRes)}: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                              append(item.keyCode)
                            }
                          }
                        }

                      Text(
                        text = annotatedString,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
