package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.RexSettingsGroup
import xyz.mpv.rex.presentation.components.RexSettingsItem
import xyz.mpv.rex.presentation.components.SettingsPalettes
import xyz.mpv.rex.presentation.components.edgeBlend
import xyz.mpv.rex.ui.icons.RexIcons
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val searchShape = RoundedCornerShape(20.dp)
    val preferences = koinInject<AppearancePreferences>()
    val badgeShapePref by preferences.settingsBadgeShape.collectAsState()
    val badgeShape = if (badgeShapePref == "CIRCLE") CircleShape else RoundedCornerShape(12.dp)

    // Same recipe as the home screen: the page is the `surfaceContainer` backdrop
    // (what BrowserTopBar paints) and the rows are lifted onto `surface` above it.
    val pageColor = MaterialTheme.colorScheme.surfaceContainer
    val cardColor = MaterialTheme.colorScheme.surface
    val searchColor = MaterialTheme.colorScheme.surfaceContainerHigh

    CollapsibleHeaderScreen(
      title = stringResource(R.string.pref_preferences),
      showBackButton = true,
      onBackClick = backstack::navigateBack,
      containerColor = pageColor,
      headerContent = {
        Surface(
          onClick = { backstack.add(SettingsSearchScreen) },
          shape = searchShape,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(searchShape),
          color = searchColor,
          tonalElevation = 0.dp,
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
              text = stringResource(R.string.settings_search_hint),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.outline,
            )
          }
        }
      }
    ) { contentModifier ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      val listState = rememberLazyListState()
      ProvidePreferenceLocals {
        LazyColumn(
          state = listState,
          modifier = contentModifier
            .padding(horizontal = 16.dp)
            .edgeBlend(listState, 16.dp),
          contentPadding = PaddingValues(top = 8.dp, bottom = navBarHeight + 24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

          // UI & Appearance Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_category_ui_appearance),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.Palette,
                  palette = SettingsPalettes.Purple,
                  title = { Text(stringResource(id = R.string.pref_appearance_title)) },
                  description = { Text(stringResource(id = R.string.pref_appearance_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(AppearancePreferencesScreen) }
                )
              )
            )
          }

          // Playback & Controls Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_category_playback_controls),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.Play,
                  palette = SettingsPalettes.SkyBlue,
                  title = { Text(stringResource(id = R.string.pref_player)) },
                  description = { Text(stringResource(id = R.string.pref_player_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(PlayerPreferencesScreen) }
                ),
                RexSettingsItem(
                  icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                  palette = SettingsPalettes.Teal,
                  title = { Text(stringResource(id = R.string.pref_layout_title)) },
                  description = { Text(stringResource(id = R.string.pref_layout_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(PlayerControlsPreferencesScreen) }
                ),
                RexSettingsItem(
                  icon = RexIcons.Gesture,
                  palette = SettingsPalettes.Emerald,
                  title = { Text(stringResource(id = R.string.pref_gesture)) },
                  description = { Text(stringResource(id = R.string.pref_gesture_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(GesturePreferencesScreen) }
                )
              )
            )
          }

          // Media & Library Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_media_library_title),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.VideoLibrary,
                  palette = SettingsPalettes.Yellow,
                  title = { Text(stringResource(R.string.pref_media_library_title)) },
                  description = { Text(stringResource(R.string.pref_media_library_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(MediaLibraryPreferencesScreen) }
                )
              )
            )
          }

          // Media Settings Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_category_media_settings),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.Hardware,
                  palette = SettingsPalettes.Lime,
                  title = { Text(stringResource(id = R.string.pref_decoder)) },
                  description = { Text(stringResource(id = R.string.pref_decoder_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(DecoderPreferencesScreen) }
                ),
                RexSettingsItem(
                  icon = RexIcons.Subtitles,
                  palette = SettingsPalettes.Cyan,
                  title = { Text(stringResource(id = R.string.pref_subtitles)) },
                  description = { Text(stringResource(id = R.string.pref_subtitles_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(SubtitlesPreferencesScreen) }
                ),
                RexSettingsItem(
                  icon = RexIcons.AudioTrack,
                  palette = SettingsPalettes.Rose,
                  title = { Text(stringResource(id = R.string.pref_audio)) },
                  description = { Text(stringResource(id = R.string.pref_audio_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(AudioPreferencesScreen) }
                )
              )
            )
          }

          // RexShorts Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_category_rexshorts),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.Movie,
                  palette = SettingsPalettes.Coral,
                  title = { Text(stringResource(R.string.pref_category_rexshorts_settings)) },
                  description = { Text(stringResource(R.string.pref_category_rexshorts_settings_desc)) },
                  showChevron = true,
                  onClick = { backstack.add(ShortsPreferencesScreen) }
                )
              )
            )
          }

          // Integrations Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = "Integrations",
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.VideoLibrary,
                  palette = SettingsPalettes.Indigo,
                  title = { Text("Jellyfin") },
                  description = { Text("External player sync") },
                  showChevron = true,
                  onClick = { backstack.add(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen) }
                ),
                RexSettingsItem(
                  icon = Icons.Outlined.CloudDownload,
                  palette = SettingsPalettes.Amber,
                  title = { Text("yt-dlp") },
                  description = { Text("Manage REX Ytdlp & extractor preferences") },
                  showChevron = true,
                  onClick = { backstack.add(YtdlSettingsScreen) }
                )
              )
            )
          }

          // Advanced & About Section
          item {
            RexSettingsGroup(
              containerColor = cardColor,
              title = stringResource(R.string.pref_category_advanced_about),
              badgeShape = badgeShape,
              items = listOf(
                RexSettingsItem(
                  icon = RexIcons.Code,
                  palette = SettingsPalettes.Indigo,
                  title = { Text(stringResource(R.string.pref_advanced)) },
                  description = { Text(stringResource(id = R.string.pref_advanced_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(AdvancedPreferencesScreen) }
                ),
                RexSettingsItem(
                  icon = RexIcons.Info,
                  palette = SettingsPalettes.Slate,
                  title = { Text(stringResource(id = R.string.pref_about_title)) },
                  description = { Text(stringResource(id = R.string.pref_about_summary)) },
                  showChevron = true,
                  onClick = { backstack.add(AboutScreen) }
                )
              )
            )
          }
        }
      }
    }
  }
}
