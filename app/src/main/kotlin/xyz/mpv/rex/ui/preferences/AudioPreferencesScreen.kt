package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AudioChannels
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.edgeBlend
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject

@Serializable
object AudioPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<AudioPreferences>()

    CollapsibleHeaderScreen(
      title = stringResource(R.string.pref_audio),
      showBackButton = true,
      onBackClick = backstack::navigateBack,
    ) { contentModifier ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      val listState = rememberPreferenceLazyListState()
      ProvidePreferenceLocals {
        LazyColumn(
          state = listState,
          modifier = contentModifier.edgeBlend(listState, 16.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_audio))
          }
          
          item {
            val preferredLanguages by preferences.preferredLanguages.collectAsState()
            val audioPitchCorrection by preferences.audioPitchCorrection.collectAsState()
            val volumeNormalization by preferences.volumeNormalization.collectAsState()
            val audioChannel by preferences.audioChannels.collectAsState()
            val volumeBoostCap by preferences.volumeBoostCap.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_audio, R.string.pref_preferred_languages),
              ) {
                TextFieldPreference(
                  value = preferredLanguages,
                  onValueChange = { preferences.preferredLanguages.set(it) },
                  textToValue = { it },
                  title = { Text(stringResource(R.string.pref_preferred_languages)) },
                  summary = {
                    if (preferredLanguages.isNotBlank()) {
                      Text(
                        preferredLanguages,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    } else {
                      Text(
                        stringResource(R.string.not_set_video_default),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  textField = { value, onValueChange, _ ->
                    Column {
                      Text(stringResource(R.string.pref_audio_preferred_language))
                      TextField(
                        value,
                        onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                      )
                    }
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_audio_pitch_correction_title,
              ) {
                SwitchPreference(
                  value = audioPitchCorrection,
                  onValueChange = { preferences.audioPitchCorrection.set(it) },
                  title = { Text(stringResource(R.string.pref_audio_pitch_correction_title)) },
                  summary = { 
                    Text(
                      stringResource(R.string.pref_audio_pitch_correction_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_audio_volume_normalization_title,
              ) {
                SwitchPreference(
                  value = volumeNormalization,
                  onValueChange = { preferences.volumeNormalization.set(it) },
                  title = { Text(stringResource(R.string.pref_audio_volume_normalization_title)) },
                  summary = { 
                    Text(
                      stringResource(R.string.pref_audio_volume_normalization_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_audio_channels,
              ) {
                ListPreference(
                  value = audioChannel,
                  onValueChange = { preferences.audioChannels.set(it) },
                  values = AudioChannels.entries,
                  valueToText = { AnnotatedString(context.getString(it.title)) },
                  title = { Text(text = stringResource(id = R.string.pref_audio_channels)) },
                  summary = { 
                    Text(
                      text = context.getString(audioChannel.title),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_audio_volume_boost_cap,
              ) {
                SliderPreference(
                  value = volumeBoostCap.toFloat(),
                  onValueChange = { preferences.volumeBoostCap.set(it.toInt()) },
                  title = { Text(stringResource(R.string.pref_audio_volume_boost_cap)) },
                  valueRange = 0f..200f,
                  summary = {
                    Text(
                      if (volumeBoostCap == 0) {
                        stringResource(R.string.generic_disabled)
                      } else {
                        volumeBoostCap.toString()
                      },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onSliderValueChange = { preferences.volumeBoostCap.set(it.toInt()) },
                  sliderValue = volumeBoostCap.toFloat(),
                )
              }
            }
          }
        }
      }
    }
  }
}
