package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.player.BackgroundPlaybackMode
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.VideoAspect
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object PlayerPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val preferences = koinInject<PlayerPreferences>()
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_player),
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
          // General Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_general))
          }

          item {
            val orientation by preferences.orientation.collectAsState()
            val defaultVideoAspect by preferences.defaultVideoAspect.collectAsState()
            val rememberVideoAspect by preferences.rememberVideoAspect.collectAsState()
            val resumePlaybackMode by preferences.resumePlaybackMode.collectAsState()
            val autoResumeOnAsk by preferences.autoResumeOnAsk.collectAsState()
            val closeAfterEndOfVideo by preferences.closeAfterReachingEndOfVideo.collectAsState()
            val autoplayOnOpen by preferences.autoplayOnOpen.collectAsState()
            val playlistMode by preferences.playlistMode.collectAsState()
            val rememberBrightness by preferences.rememberBrightness.collectAsState()
            val autoPiPOnNavigation by preferences.autoPiPOnNavigation.collectAsState()
            val keepScreenOnWhenPaused by preferences.keepScreenOnWhenPaused.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_player, R.string.pref_player_orientation),
              ) {
                ListPreference(
                  value = orientation,
                  onValueChange = preferences.orientation::set,
                  values = PlayerOrientation.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(id = R.string.pref_player_orientation)) },
                  summary = { 
                    Text(
                      text = stringResource(id = orientation.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_default_aspect_ratio,
              ) {
                ListPreference(
                  value = defaultVideoAspect,
                  onValueChange = { aspect ->
                    preferences.defaultVideoAspect.set(aspect)
                    preferences.defaultCustomAspectRatio.set(-1.0)
                  },
                  values = VideoAspect.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(id = R.string.pref_player_default_aspect_ratio)) },
                  summary = {
                    Text(
                      text = stringResource(id = defaultVideoAspect.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_remember_aspect_ratio,
              ) {
                SwitchPreference(
                  value = rememberVideoAspect,
                  onValueChange = preferences.rememberVideoAspect::set,
                  title = { Text(text = stringResource(R.string.pref_player_remember_aspect_ratio)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_player_remember_aspect_ratio_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = if (resumePlaybackMode != ResumePlaybackMode.Ask) {
                  listOf(R.string.pref_player_resume_playback_title, R.string.pref_player_auto_resume_on_ask_title)
                } else {
                  R.string.pref_player_resume_playback_title
                },
              ) {
                ListPreference(
                  value = resumePlaybackMode,
                  onValueChange = preferences.resumePlaybackMode::set,
                  values = ResumePlaybackMode.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(id = R.string.pref_player_resume_playback_title)) },
                  summary = {
                    Text(
                      text = stringResource(id = resumePlaybackMode.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              if (resumePlaybackMode == ResumePlaybackMode.Ask) {
                GroupedPreferenceCard(
                  position = GroupPosition.MIDDLE,
                  highlightKey = R.string.pref_player_auto_resume_on_ask_title,
                ) {
                  SwitchPreference(
                    value = autoResumeOnAsk,
                    onValueChange = preferences.autoResumeOnAsk::set,
                    title = { Text(stringResource(R.string.pref_player_auto_resume_on_ask_title)) },
                    summary = {
                      Text(
                        text = stringResource(
                          if (autoResumeOnAsk) {
                            R.string.pref_player_auto_resume_on_ask_summary_on
                          } else {
                            R.string.pref_player_auto_resume_on_ask_summary_off
                          }
                        ),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_close_after_eof,
              ) {
                SwitchPreference(
                  value = closeAfterEndOfVideo,
                  onValueChange = preferences.closeAfterReachingEndOfVideo::set,
                  title = { Text(stringResource(id = R.string.pref_player_close_after_eof)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_player_autoplay_on_open, R.string.pref_autoplay_on_open_title),
              ) {
                SwitchPreference(
                  value = autoplayOnOpen,
                  onValueChange = preferences.autoplayOnOpen::set,
                  title = { Text(text = stringResource(R.string.pref_player_autoplay_on_open)) },
                  summary = {
                    Text(
                      text = if (autoplayOnOpen)
                        stringResource(R.string.pref_player_autoplay_on_open_summary_on)
                      else
                        stringResource(R.string.pref_player_autoplay_on_open_summary_off),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_autoplay_title,
              ) {
                SwitchPreference(
                  value = playlistMode,
                  onValueChange = preferences.playlistMode::set,
                  title = { Text(text = stringResource(R.string.pref_autoplay_title)) },
                  summary = {
                    Text(
                      text = if (playlistMode)
                        stringResource(R.string.pref_player_playlist_mode_summary_on)
                      else
                        stringResource(R.string.pref_player_playlist_mode_summary_off),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_remember_brightness,
              ) {
                SwitchPreference(
                  value = rememberBrightness,
                  onValueChange = preferences.rememberBrightness::set,
                  title = { Text(text = stringResource(R.string.pref_player_remember_brightness)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf(R.string.pref_player_auto_pip, R.string.pref_auto_pip_title),
              ) {
                SwitchPreference(
                  value = autoPiPOnNavigation,
                  onValueChange = preferences.autoPiPOnNavigation::set,
                  title = { Text(stringResource(R.string.pref_player_auto_pip)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_player_auto_pip_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_player_keep_screen_on_when_paused_title,
              ) {
                SwitchPreference(
                  value = keepScreenOnWhenPaused,
                  onValueChange = preferences.keepScreenOnWhenPaused::set,
                  title = { Text(stringResource(R.string.pref_player_keep_screen_on_when_paused_title)) },
                  summary = {
                    Text(
                      text = if (keepScreenOnWhenPaused)
                        stringResource(R.string.pref_player_keep_screen_on_when_paused_summary_on)
                      else
                        stringResource(R.string.pref_player_keep_screen_on_when_paused_summary_off),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          // Mini Player Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_section_mini_player))
          }

          item {
            val backgroundPlayback by preferences.backgroundPlayback.collectAsState()
            val playInMiniPlayerDirectly by preferences.playInMiniPlayerDirectly.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_player_background_playback, R.string.background_playback_title),
              ) {
                ListPreference(
                  value = backgroundPlayback,
                  onValueChange = preferences.backgroundPlayback::set,
                  values = BackgroundPlaybackMode.entries,
                  valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                  title = { Text(text = stringResource(id = R.string.pref_player_background_playback)) },
                  summary = {
                    Text(
                      text = stringResource(id = backgroundPlayback.titleRes),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_player_play_in_mini_player,
              ) {
                SwitchPreference(
                  value = playInMiniPlayerDirectly,
                  onValueChange = preferences.playInMiniPlayerDirectly::set,
                  title = { Text(text = stringResource(R.string.pref_player_play_in_mini_player)) },
                  summary = {
                    Text(
                      text = if (playInMiniPlayerDirectly)
                        stringResource(R.string.pref_player_play_in_mini_player_summary_on)
                      else
                        stringResource(R.string.pref_player_play_in_mini_player_summary_off),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          // Seeking Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_seeking_title))
          }

          item {
            val usePreciseSeeking by preferences.usePreciseSeeking.collectAsState()
            val showSeekBarWhenSeeking by preferences.showSeekBarWhenSeeking.collectAsState()
            val hideOsdText by preferences.hideOsdText.collectAsState()
            val customSkipDuration by preferences.customSkipDuration.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_player_use_precise_seeking,
              ) {
                SwitchPreference(
                  value = usePreciseSeeking,
                  onValueChange = preferences.usePreciseSeeking::set,
                  title = { Text(stringResource(R.string.pref_player_use_precise_seeking)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_show_seekbar_when_seeking_title,
              ) {
                SwitchPreference(
                  value = showSeekBarWhenSeeking,
                  onValueChange = preferences.showSeekBarWhenSeeking::set,
                  title = { Text(stringResource(R.string.pref_player_show_seekbar_when_seeking_title)) },
                  summary = { Text(stringResource(R.string.pref_player_show_seekbar_when_seeking_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_player_hide_osd_text_title,
              ) {
                SwitchPreference(
                  value = hideOsdText,
                  onValueChange = preferences.hideOsdText::set,
                  title = { Text(stringResource(R.string.pref_player_hide_osd_text_title)) },
                  summary = { Text(stringResource(R.string.pref_player_hide_osd_text_summary)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_player_custom_skip_duration_title,
              ) {
                SliderPreference(
                  value = customSkipDuration.toFloat(),
                  onValueChange = { preferences.customSkipDuration.set(it.roundToInt()) },
                  title = { Text(stringResource(R.string.pref_player_custom_skip_duration_title)) },
                  valueRange = 5f..180f,
                  summary = {
                     val summaryText = stringResource(R.string.pref_player_custom_skip_duration_summary)
                     Text(
                       "$summaryText ($customSkipDuration s)",
                       color = MaterialTheme.colorScheme.outline,
                     )
                  },
                  onSliderValueChange = { preferences.customSkipDuration.set(it.roundToInt()) },
                  sliderValue = customSkipDuration.toFloat(),
                )
              }
            }
          }

          // Display Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_display))
          }

          item {
            val showSystemStatusBar by preferences.showSystemStatusBar.collectAsState()
            val showSystemNavigationBar by preferences.showSystemNavigationBar.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_player_display_show_status_bar,
              ) {
                SwitchPreference(
                  value = showSystemStatusBar,
                  onValueChange = preferences.showSystemStatusBar::set,
                  title = { Text(stringResource(R.string.pref_player_display_show_status_bar)) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = listOf(R.string.pref_player_display_show_navigation_bar, R.string.pref_show_navigation_bar_title),
              ) {
                SwitchPreference(
                  value = showSystemNavigationBar,
                  onValueChange = preferences.showSystemNavigationBar::set,
                  title = { Text(stringResource(R.string.pref_player_display_show_navigation_bar)) },
                )
              }
            }
          }
        }
      }
    }
  }
}
