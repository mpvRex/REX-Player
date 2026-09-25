package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.spacing

/**
 * A simplified speed indicator that shows the current playback speed with a jumping animation.
 */
@Composable
fun SpeedControlSlider(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  CompactSpeedIndicator(currentSpeed, modifier)
}

/**
 * A compact speed indicator that shows the icon and speed value with a jumping animation.
 */
@Composable
fun CompactSpeedIndicator(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
  prefix: String? = null,
  suffix: String? = null,
  onReset: (() -> Unit)? = null,
) {
  val speedString = remember(currentSpeed) {
    String.format("%.2f", currentSpeed)
  }

  val appearancePreferences = koinInject<AppearancePreferences>()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

  val chipShape = RoundedCornerShape(16.dp)

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
    else -> BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
  }

  Surface(
    shape = chipShape,
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
    modifier = modifier.then(glassModifier)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.FastForward,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = contentColor
      )
      
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
      ) {
        if (prefix != null) {
            Text(
                text = "$prefix ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.9f)
            )
        }

        AnimatedContent(
          targetState = speedString,
          transitionSpec = {
            (fadeIn(animationSpec = tween(100)) +
              scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
              slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            ).togetherWith(
              fadeOut(animationSpec = tween(100)) +
              scaleOut(targetScale = 1.1f, animationSpec = tween(100)) +
              slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(100))
            ).using(
              SizeTransform(clip = false)
            )
          },
          label = "SpeedJumpAnimation"
        ) { targetSpeed ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = targetSpeed,
              fontSize = 14.sp,
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.bodyLarge,
              color = contentColor
            )
            Text(
              text = "x",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(start = 1.dp),
              color = contentColor.copy(alpha = 0.75f)
            )
          }
        }

        if (suffix != null) {
            Text(
                text = " $suffix",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.9f)
            )
        }

        if (onReset != null) {
            val resetShape = RoundedCornerShape(10.dp)
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
            Surface(
                shape = resetShape,
                color = if (enableGlass) Color.White.copy(alpha = 0.15f) else if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(24.dp)
                    .clip(resetShape)
                    .clickable { onReset() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reset Speed",
                        modifier = Modifier.size(14.dp),
                        tint = if (matchTheme && !enableGlass) MaterialTheme.colorScheme.primary else contentColor
                    )
                }
            }
        }
      }
    }
  }
}
