package xyz.mpv.rex.ui.player.controls.components.panels

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.preferences.preference.deleteAndGet
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.presentation.components.SliderItem
import xyz.mpv.rex.ui.player.DebandSettings
import xyz.mpv.rex.ui.player.Debanding
import xyz.mpv.rex.ui.player.FilterPreset
import xyz.mpv.rex.ui.player.VideoFilters
import xyz.mpv.rex.ui.theme.spacing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  if (isPortrait) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
      Column(
        modifier = modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(top = 4.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(bottom = MaterialTheme.spacing.small),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.player_sheets_video_settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          IconButton(onClick = onDismissRequest) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        VideoSettingsContent()
      }
    }
  } else {
    VideoSettingsSideSheet(
      onDismissRequest = onDismissRequest,
      modifier = modifier,
    )
  }
}

@Composable
private fun VideoSettingsSideSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var isVisible by remember { mutableStateOf(false) }
  val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)

  val scrimAlpha by animateFloatAsState(
    targetValue = if (isVisible) 0.5f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "side_sheet_scrim",
  )

  val dismissWithAnimation: () -> Unit = {
    scope.launch {
      isVisible = false
      delay(250)
      latestOnDismissRequest()
    }
  }

  BackHandler(enabled = isVisible, onBack = { dismissWithAnimation() })

  LaunchedEffect(Unit) {
    isVisible = true
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = scrimAlpha))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { dismissWithAnimation() },
      ),
    contentAlignment = Alignment.CenterEnd,
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = xyz.mpv.rex.ui.motion.RexMotion.panelEnter(fromEnd = true),
      exit = xyz.mpv.rex.ui.motion.RexMotion.panelExit(fromEnd = true),
    ) {
      Surface(
        modifier = modifier
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}, // Prevent clicking through sheet to dismiss
          )
          .widthIn(max = 380.dp)
          .fillMaxHeight()
          .windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
          ),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MaterialTheme.spacing.medium)
              .padding(top = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.player_sheets_video_settings_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { dismissWithAnimation() }) {
              Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

          Spacer(Modifier.height(MaterialTheme.spacing.small))

          VideoSettingsContent()
        }
      }
    }
  }
}

@Composable
private fun VideoSettingsContent(
  modifier: Modifier = Modifier,
) {
  val decoderPreferences = koinInject<DecoderPreferences>()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .padding(bottom = MaterialTheme.spacing.large),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
  ) {
    VideoSettingsPresetsSection(decoderPreferences)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    VideoSettingsAdjustmentsSection(decoderPreferences)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    VideoSettingsDebandingSection(decoderPreferences)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoSettingsPresetsSection(
  decoderPreferences: DecoderPreferences,
) {
  val brightness by decoderPreferences.brightnessFilter.collectAsState()
  val saturation by decoderPreferences.saturationFilter.collectAsState()
  val contrast by decoderPreferences.contrastFilter.collectAsState()
  val gamma by decoderPreferences.gammaFilter.collectAsState()
  val hue by decoderPreferences.hueFilter.collectAsState()
  val sharpness by decoderPreferences.sharpnessFilter.collectAsState()

  val currentPreset = FilterPreset.entries.find { preset ->
    preset.brightness == brightness &&
      preset.saturation == saturation &&
      preset.contrast == contrast &&
      preset.gamma == gamma &&
      preset.hue == hue &&
      preset.sharpness == sharpness
  } ?: FilterPreset.NONE.takeIf {
    brightness == 0 && saturation == 0 && contrast == 0 && gamma == 0 && hue == 0 && sharpness == 0
  }

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      Icon(
        Icons.Default.AutoAwesome,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = stringResource(R.string.video_settings_filter_presets_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      FilterPreset.entries.forEach { preset ->
        FilterChip(
          selected = currentPreset == preset,
          onClick = {
            decoderPreferences.brightnessFilter.set(preset.brightness)
            decoderPreferences.saturationFilter.set(preset.saturation)
            decoderPreferences.contrastFilter.set(preset.contrast)
            decoderPreferences.gammaFilter.set(preset.gamma)
            decoderPreferences.hueFilter.set(preset.hue)
            decoderPreferences.sharpnessFilter.set(preset.sharpness)

            MPVLib.setPropertyInt("brightness", preset.brightness)
            MPVLib.setPropertyInt("saturation", preset.saturation)
            MPVLib.setPropertyInt("contrast", preset.contrast)
            MPVLib.setPropertyInt("gamma", preset.gamma)
            MPVLib.setPropertyInt("hue", preset.hue)
            MPVLib.setPropertyInt("sharpen", preset.sharpness)
          },
          label = { Text(stringResource(preset.displayNameRes)) },
        )
      }
    }
  }
}

@Composable
private fun VideoSettingsAdjustmentsSection(
  decoderPreferences: DecoderPreferences,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Icon(
          Icons.Default.Tune,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.player_sheets_filters_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(
        onClick = {
          VideoFilters.entries.forEach {
            MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
          }
        },
      ) {
        Text(stringResource(R.string.generic_reset))
      }
    }

    VideoFilters.entries.forEach { filter ->
      val value by filter.preference(decoderPreferences).collectAsState()
      SliderItem(
        label = stringResource(filter.titleRes),
        value = value,
        valueText = value.toString(),
        onChange = {
          filter.preference(decoderPreferences).set(it)
          MPVLib.setPropertyInt(filter.mpvProperty, it)
        },
        max = filter.max,
        min = filter.min,
      )
    }

    if (!decoderPreferences.gpuNext.get()) {
      Text(
        text = stringResource(R.string.player_sheets_filters_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun VideoSettingsDebandingSection(
  decoderPreferences: DecoderPreferences,
) {
  val deband by decoderPreferences.debanding.collectAsState()

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Icon(
          Icons.Default.Gradient,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.player_sheets_deband_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(
        onClick = {
          decoderPreferences.debanding.set(Debanding.None)
          MPVLib.setOptionString("deband", "no")
          MPVLib.command("vf", "remove", "@deband")
          DebandSettings.entries.forEach {
            MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
          }
        },
      ) {
        Text(stringResource(R.string.generic_reset))
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Debanding.entries.forEach { mode ->
        FilterChip(
          selected = deband == mode,
          onClick = {
            decoderPreferences.debanding.set(mode)
            when (mode) {
              Debanding.None -> {
                MPVLib.setOptionString("deband", "no")
                MPVLib.command("vf", "remove", "@deband")
              }
              Debanding.CPU -> {
                MPVLib.setOptionString("deband", "no")
                MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
              }
              Debanding.GPU -> {
                MPVLib.setOptionString("deband", "yes")
                MPVLib.command("vf", "remove", "@deband")
              }
            }
          },
          leadingIcon = {
            when (mode) {
              Debanding.None -> Icon(Icons.Default.NotInterested, contentDescription = null, modifier = Modifier.size(18.dp))
              Debanding.CPU -> Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
              Debanding.GPU -> Icon(painterResource(R.drawable.expansion_card), contentDescription = null, modifier = Modifier.size(18.dp))
            }
          },
          label = { Text(stringResource(mode.titleRes)) },
        )
      }
    }

    if (deband != Debanding.None) {
      DebandSettings.entries.forEach { debandSettings ->
        val value by debandSettings.preference(decoderPreferences).collectAsState()
        SliderItem(
          label = stringResource(debandSettings.titleRes),
          value = value,
          valueText = value.toString(),
          onChange = {
            debandSettings.preference(decoderPreferences).set(it)
            MPVLib.setPropertyInt(debandSettings.mpvProperty, it)
          },
          min = debandSettings.start,
          max = debandSettings.end,
        )
      }
    }
  }
}
