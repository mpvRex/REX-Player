package xyz.mpv.rex.ui.browser.cards

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer modifier applying an animated gradient sweep.
 * Matches the Material 3 surfaceContainerHighest / surfaceContainerHigh palette.
 */
@Composable
fun Modifier.shimmer(
  enabled: Boolean = true,
  baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
  highlightColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  durationMillis: Int = 1200,
): Modifier {
  if (!enabled) return this

  val transition = rememberInfiniteTransition(label = "shimmerTransition")
  val progress = transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = durationMillis, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "shimmerProgress",
  )

  return this.drawBehind {
    val width = size.width
    val shimmerWidth = width * 0.8f
    val x = -shimmerWidth + (width + 2 * shimmerWidth) * progress.value

    drawRect(
      brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(x, 0f),
        end = Offset(x + shimmerWidth, 0f),
      ),
      size = size,
    )
  }
}

/**
 * Shimmer skeleton loading placeholder for media cards.
 */
@Composable
fun ThumbnailShimmer(
  modifier: Modifier = Modifier,
  showIcon: Boolean = true,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .shimmer(),
    contentAlignment = Alignment.Center,
  ) {
    if (showIcon) {
      Icon(
        Icons.Filled.PlayArrow,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
      )
    }
  }
}
