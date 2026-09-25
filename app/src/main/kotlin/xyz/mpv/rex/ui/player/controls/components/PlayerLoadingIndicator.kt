package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState

private const val LOADING_SHOW_DELAY_MS = 250L
private const val LOADING_HIDE_DELAY_MS = 250L

internal data class PlayerLoadingState(
  val visible: Boolean = false,
  val isCacheStall: Boolean = false,
  val percent: Int? = null,
)

@Composable
internal fun rememberPlayerLoadingState(
  isNetworkStream: Boolean = false,
  isLoadingUrl: Boolean = false,
  enabled: Boolean = true,
): PlayerLoadingState {
  val path by MPVLib.propString["path"].collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val pausedForCache by MPVLib.propBoolean["paused-for-cache"].collectAsState()
  val seeking by MPVLib.propBoolean["seeking"].collectAsState()
  val coreIdle by MPVLib.propBoolean["core-idle"].collectAsState()
  val eofReached by MPVLib.propBoolean["eof-reached"].collectAsState()
  val cacheBufferingState by MPVLib.propInt["cache-buffering-state"].collectAsState()

  val isPathNetwork = path?.let {
    it.startsWith("http://", true) ||
      it.startsWith("https://", true) ||
      it.startsWith("rtmp://", true) ||
      it.startsWith("rtsp://", true) ||
      it.startsWith("edl:", true) ||
      it.startsWith("ftp://", true) ||
      it.startsWith("sftp://", true)
  } ?: false

  val isNetwork = isNetworkStream || isPathNetwork || isLoadingUrl

  val isCacheStall = isNetwork && pausedForCache == true
  val isSeeking = isNetwork && seeking == true
  val isBuffering = isCacheStall || isSeeking || (coreIdle == true && isCacheStall)

  val stalled = enabled && isNetwork && (
    isLoadingUrl ||
      (eofReached != true && isBuffering)
  )

  var visible by remember { mutableStateOf(stalled && isLoadingUrl) }
  LaunchedEffect(stalled, isLoadingUrl) {
    if (visible == stalled) return@LaunchedEffect
    if (stalled) {
      if (!isLoadingUrl) {
        delay(LOADING_SHOW_DELAY_MS)
      }
      visible = true
    } else {
      delay(LOADING_HIDE_DELAY_MS)
      visible = false
    }
  }

  val percent = if (isCacheStall) cacheBufferingState?.takeIf { it in 1..99 } else null

  return PlayerLoadingState(
    visible = visible,
    isCacheStall = isCacheStall,
    percent = percent,
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerLoadingIndicator(
  loadingState: PlayerLoadingState,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = loadingState.visible,
    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.88f, animationSpec = tween(200)),
    exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.88f, animationSpec = tween(250)),
    modifier = modifier,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      LoadingIndicator(
        modifier = Modifier.size(76.dp),
        color = MaterialTheme.colorScheme.primary,
      )

      val percent = loadingState.percent
      val displayText = when {
        percent != null && percent in 1..99 ->
          stringResource(R.string.ui_loading_percent, percent)
        else ->
          stringResource(R.string.ui_loading)
      }

      val appearancePreferences = koinInject<AppearancePreferences>()
      val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
      val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

      val chipShape = RoundedCornerShape(12.dp)
      val glassModifier = if (enableGlass) {
        Modifier.glassSurface(
          shape = chipShape,
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
        matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
      }

      val contentColor = when {
        enableGlass -> Color.White
        matchTheme -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
      }

      val border = when {
        enableGlass -> null
        matchTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
      }

      Surface(
        shape = chipShape,
        color = surfaceColor,
        contentColor = contentColor,
        border = border,
        modifier = Modifier.then(glassModifier),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          val infiniteTransition = rememberInfiniteTransition(label = "pulse")
          val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
              animation = tween(700, easing = FastOutSlowInEasing),
              repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
          )

          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
          )

          Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium.copy(
              fontFeatureSettings = "tnum",
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 0.25.sp,
            ),
            color = contentColor,
          )
        }
      }
    }
  }
}
