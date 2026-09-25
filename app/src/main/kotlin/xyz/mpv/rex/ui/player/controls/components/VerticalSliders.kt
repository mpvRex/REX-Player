package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.FastOutSlowInEasing
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import xyz.mpv.rex.ui.player.controls.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.theme.spacing
import kotlin.math.roundToInt

fun percentage(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
): Float = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

fun percentage(
  value: Int,
  range: ClosedRange<Int>,
): Float = ((value - range.start - 0f) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

@Composable
fun VerticalSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  overflowValue: Float? = null,
  overflowRange: ClosedFloatingPointRange<Float>? = null,
  isActive: Boolean = false
) {
  val coercedValue = value.coerceIn(range)

  // Read Toggle for Bounce Animation from Preferences
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableBounceAnimation by appearancePrefs.enableBounceAnimation.collectAsState()
  val enableGlass by appearancePrefs.enableGlassPlayerControls.collectAsState()

  val matchTheme by appearancePrefs.matchPlayerControlsToTheme.collectAsState()

  val trackWidthAnim = remember { Animatable(22f) }

  // Listen for changes to the interaction state (touching vs. released)
  LaunchedEffect(isActive, enableBounceAnimation) {
    if (!enableBounceAnimation) {
      trackWidthAnim.snapTo(22f)
      return@LaunchedEffect
    }
    if (isActive) {
      trackWidthAnim.animateTo(
        targetValue = 32f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMedium
        )
      )
    } else {
      kotlinx.coroutines.delay(150)
      trackWidthAnim.animateTo(
        targetValue = 22f,
        animationSpec = spring(
          dampingRatio = 0.4f,
          stiffness = Spring.StiffnessLow
        )
      )
    }
  }

  val trackWidth = trackWidthAnim.value.dp

  val trackModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = RoundedCornerShape(16.dp),
      backgroundColor = Color.White.copy(alpha = 0.05f),
      borderColor = Color.White.copy(alpha = 0.15f),
      borderWidth = 1.dp,
      outerShadowColor = Color.Black.copy(alpha = 0.00f),
      outerShadowBlur = 0.dp,
      outerShadowOffsetX = 0.dp,
      outerShadowOffsetY = 0.dp,
      innerHighlightColor = Color.White.copy(alpha = 0.35f),
      innerHighlightBlur = 5.dp,
      innerHighlightOffsetX = (-2).dp,
      innerHighlightOffsetY = (-2).dp,
      innerShadowColor = Color.Black.copy(alpha = 0.35f),
      innerShadowBlur = 5.dp,
      innerShadowOffsetX = 2.dp,
      innerShadowOffsetY = 2.dp
    )
  } else {
    Modifier
      .background(
        if (matchTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.background,
        RoundedCornerShape(16.dp)
      )
      .border(
        width = 1.dp,
        color = if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
      )
  }

  // Outer fixed-width container prevents layout shifts
  Box(
    modifier =
      modifier
      .height(120.dp)
      .width(32.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    // Inner track that actually animates
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(trackWidth)
        .clip(RoundedCornerShape(16.dp))
        .then(trackModifier),
      contentAlignment = Alignment.BottomCenter,
    ) {
      val targetHeight by animateFloatAsState(percentage(coercedValue, range), label = "vsliderheight")
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(targetHeight)
          .background(MaterialTheme.colorScheme.primary),
      )
      if (overflowRange != null && overflowValue != null) {
        val overflowHeight by animateFloatAsState(
          percentage(overflowValue, overflowRange),
          label = "vslideroverflowheight",
        )
        Box(
          Modifier
            .fillMaxWidth()
            .fillMaxHeight(overflowHeight)
            .background(MaterialTheme.colorScheme.errorContainer),
        )
      }
    }
  }
}

@Composable
fun VerticalSlider(
  value: Int,
  range: ClosedRange<Int>,
  modifier: Modifier = Modifier,
  overflowValue: Int? = null,
  overflowRange: ClosedRange<Int>? = null,
  isActive: Boolean = false
) {
  val coercedValue = value.coerceIn(range)

  // Read Toggle for Bounce Animation from Preferences
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableBounceAnimation by appearancePrefs.enableBounceAnimation.collectAsState()
  val enableGlass by appearancePrefs.enableGlassPlayerControls.collectAsState()

  val trackWidthAnim = remember { Animatable(22f) }

  // Listen for changes to the interaction state (touching vs. released)
  LaunchedEffect(isActive, enableBounceAnimation) {
    if (!enableBounceAnimation) {
      trackWidthAnim.snapTo(22f)
      return@LaunchedEffect
    }
    if (isActive) {
      trackWidthAnim.animateTo(
        targetValue = 32f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMedium
        )
      )
    } else {
      kotlinx.coroutines.delay(150)
      trackWidthAnim.animateTo(
        targetValue = 22f,
        animationSpec = spring(
          dampingRatio = 0.4f,
          stiffness = Spring.StiffnessLow
        )
      )
    }
  }

  val trackWidth = trackWidthAnim.value.dp

  val trackModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = RoundedCornerShape(16.dp),
      backgroundColor = Color.White.copy(alpha = 0.05f),
      borderColor = Color.White.copy(alpha = 0.15f),
      borderWidth = 1.dp,
      outerShadowColor = Color.Black.copy(alpha = 0.00f),
      outerShadowBlur = 0.dp,
      outerShadowOffsetX = 0.dp,
      outerShadowOffsetY = 0.dp,
      innerHighlightColor = Color.White.copy(alpha = 0.35f),
      innerHighlightBlur = 5.dp,
      innerHighlightOffsetX = (-2).dp,
      innerHighlightOffsetY = (-2).dp,
      innerShadowColor = Color.Black.copy(alpha = 0.35f),
      innerShadowBlur = 5.dp,
      innerShadowOffsetX = 2.dp,
      innerShadowOffsetY = 2.dp
    )
  } else {
    Modifier
      .background(MaterialTheme.colorScheme.background)
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
      )
  }

  // Outer fixed-width container
  Box(
    modifier = modifier
      .height(120.dp)
      .width(32.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    // Inner track that actually animates
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(trackWidth)
        .clip(RoundedCornerShape(16.dp))
        .then(trackModifier),
      contentAlignment = Alignment.BottomCenter,
    ) {
      val targetHeight by animateFloatAsState(percentage(coercedValue, range), label = "vsliderheight")
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(targetHeight)
          .background(MaterialTheme.colorScheme.primary),
      )
      if (overflowRange != null && overflowValue != null) {
        val overflowHeight by animateFloatAsState(
          percentage(overflowValue, overflowRange),
          label = "vslideroverflowheight",
        )
        Box(
          Modifier
            .fillMaxWidth()
            .fillMaxHeight(overflowHeight)
            .background(MaterialTheme.colorScheme.errorContainer),
        )
      }
    }
  }
}

@Composable
fun BrightnessSlider(
  brightness: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  isActive: Boolean = false
) {
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableGlass by appearancePrefs.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePrefs.matchPlayerControlsToTheme.collectAsState()

  val glassModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = RoundedCornerShape(20.dp),
      backgroundColor = Color.White.copy(alpha = 0.05f),
      borderColor = Color.White.copy(alpha = 0.15f),
      borderWidth = 1.dp,
      outerShadowColor = Color.Black.copy(alpha = 0.00f),
      outerShadowBlur = 0.dp,
      outerShadowOffsetX = 0.dp,
      outerShadowOffsetY = 0.dp,
      innerHighlightColor = Color.White.copy(alpha = 0.35f),
      innerHighlightBlur = 5.dp,
      innerHighlightOffsetX = (-2).dp,
      innerHighlightOffsetY = (-2).dp,
      innerShadowColor = Color.Black.copy(alpha = 0.35f),
      innerShadowBlur = 5.dp,
      innerShadowOffsetX = 2.dp,
      innerShadowOffsetY = 2.dp
    )
  } else {
    Modifier
  }

  val surfaceColor = when {
    enableGlass -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val contentColor = when {
    enableGlass -> Color.White
    matchTheme -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurface
  }

  val border = when {
    enableGlass -> null
    matchTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  }

  val coercedBrightness = brightness.coerceIn(range)
  Surface(
    modifier = modifier.then(glassModifier),
    shape = RoundedCornerShape(20.dp),
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      Text(
        (coercedBrightness * 100).toInt().toString(),
        style = MaterialTheme.typography.bodySmall,
      )
      VerticalSlider(
        coercedBrightness,
        range,
        isActive = isActive
      )
      Icon(
        when (percentage(coercedBrightness, range)) {
          in 0f..0.3f -> Icons.Default.BrightnessLow
          in 0.3f..0.6f -> Icons.Default.BrightnessMedium
          in 0.6f..1f -> Icons.Default.BrightnessHigh
          else -> Icons.Default.BrightnessMedium
        },
        contentDescription = null,
      )
    }
  }
}

@Composable
fun VolumeSlider(
  volume: Int,
  mpvVolume: Int,
  range: ClosedRange<Int>,
  boostRange: ClosedRange<Int>?,
  modifier: Modifier = Modifier,
  displayAsPercentage: Boolean = false,
  isActive: Boolean = false
) {
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableGlass by appearancePrefs.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePrefs.matchPlayerControlsToTheme.collectAsState()

  val glassModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = RoundedCornerShape(20.dp),
      backgroundColor = Color.White.copy(alpha = 0.05f),
      borderColor = Color.White.copy(alpha = 0.15f),
      borderWidth = 1.dp,
      outerShadowColor = Color.Black.copy(alpha = 0.00f),
      outerShadowBlur = 0.dp,
      outerShadowOffsetX = 0.dp,
      outerShadowOffsetY = 0.dp,
      innerHighlightColor = Color.White.copy(alpha = 0.35f),
      innerHighlightBlur = 5.dp,
      innerHighlightOffsetX = (-2).dp,
      innerHighlightOffsetY = (-2).dp,
      innerShadowColor = Color.Black.copy(alpha = 0.35f),
      innerShadowBlur = 5.dp,
      innerShadowOffsetX = 2.dp,
      innerShadowOffsetY = 2.dp
    )
  } else {
    Modifier
  }

  val surfaceColor = when {
    enableGlass -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val contentColor = when {
    enableGlass -> Color.White
    matchTheme -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurface
  }

  val border = when {
    enableGlass -> null
    matchTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  }

  val percentage = (percentage(volume, range) * 100).roundToInt()
  Surface(
    modifier = modifier.then(glassModifier),
    shape = RoundedCornerShape(20.dp),
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      val boostVolume = mpvVolume - 100
      Text(
        getVolumeSliderText(volume, mpvVolume, boostVolume, percentage, displayAsPercentage),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
      )
      VerticalSlider(
        if (displayAsPercentage) percentage else volume,
        if (displayAsPercentage) 0..100 else range,
        overflowValue = boostVolume,
        overflowRange = boostRange,
        isActive = isActive
      )
      Icon(
        when (percentage) {
          0 -> Icons.AutoMirrored.Default.VolumeOff
          in 0..30 -> Icons.AutoMirrored.Default.VolumeMute
          in 30..60 -> Icons.AutoMirrored.Default.VolumeDown
          in 60..100 -> Icons.AutoMirrored.Default.VolumeUp
          else -> Icons.AutoMirrored.Default.VolumeOff
        },
        contentDescription = null,
      )
    }
  }
}

val getVolumeSliderText: @Composable (Int, Int, Int, Int, Boolean) -> String =
  { volume, mpvVolume, boostVolume, percentage, displayAsPercentage ->
    when {
      mpvVolume == 100 ->
        if (displayAsPercentage) {
          "$percentage"
        } else {
          "$volume"
        }

      mpvVolume > 100 -> {
        if (displayAsPercentage) {
          "${percentage + boostVolume}"
        } else {
          stringResource(R.string.volume_slider_absolute_value, volume + boostVolume)
        }
      }

      mpvVolume < 100 -> {
        if (displayAsPercentage) {
          "${percentage + boostVolume}"
        } else {
          stringResource(R.string.volume_slider_absolute_value, volume + boostVolume)
        }
      }

      else -> {
        if (displayAsPercentage) {
          "$percentage"
        } else {
          "$volume"
        }
      }
    }
  }
