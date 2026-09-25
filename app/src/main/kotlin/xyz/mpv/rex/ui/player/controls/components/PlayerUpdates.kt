package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import `is`.xyz.mpv.Utils
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.spacing

@Composable
fun PlayerUpdate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

  val updateShape = RoundedCornerShape(16.dp)

  val glassModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = updateShape,
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
    shape = updateShape,
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
    modifier = modifier
      .then(glassModifier)
      .animateContentSize(),
  ) {
    Box(
      modifier = Modifier.padding(
        vertical = 6.dp,
        horizontal = 12.dp,
      ),
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}


@Composable
fun TextPlayerUpdate(
  text: String,
  modifier: Modifier = Modifier,
) {
  PlayerUpdate(modifier) {
    Text(
      text = text,
      fontSize = 14.sp,
      fontWeight = FontWeight.ExtraBold,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Composable
fun LockHint(
  text: String,
  modifier: Modifier = Modifier,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

  val hintShape = RoundedCornerShape(14.dp)
  val glassModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = hintShape,
      backgroundColor = Color.White.copy(alpha = 0.08f),
      borderColor = Color.White.copy(alpha = 0.18f),
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
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
  }

  val contentColor = when {
    enableGlass -> Color.White
    matchTheme -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurface
  }

  val border = when {
    enableGlass -> null
    matchTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  }

  Surface(
    shape = hintShape,
    color = surfaceColor,
    contentColor = contentColor,
    border = border,
    modifier = modifier.then(glassModifier),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = contentColor,
    )
  }
}

@Composable
fun MultipleSpeedPlayerUpdate(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  CompactSpeedIndicator(currentSpeed = currentSpeed, modifier = modifier)
}

@Composable
@Preview
private fun PreviewMultipleSpeedPlayerUpdate() {
  MultipleSpeedPlayerUpdate(currentSpeed = 2f)
}
@Composable
fun SeekPlayerUpdate(
  currentTime: String,
  seekDelta: String,
  modifier: Modifier = Modifier,
) {
  PlayerUpdate(modifier) {
    val contentColor = LocalContentColor.current
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = currentTime,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        color = contentColor,
        style = MaterialTheme.typography.bodyLarge,
      )

      Text(
        text = " $seekDelta",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = contentColor.copy(alpha = 0.75f),
      )
    }
  }
}

@Composable
fun ResumedFromPlayerUpdate(
  position: Int,
  onRestart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val actionShape = RoundedCornerShape(10.dp)
  PlayerUpdate(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.player_resumed_from_pill, Utils.prettyTime(position)),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
      )
      Surface(
        shape = actionShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
          .clip(actionShape)
          .clickable(onClick = onRestart),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Replay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = stringResource(R.string.player_restart_action),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
fun PromptResumePlayerUpdate(
  position: Int,
  onResume: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val actionShape = RoundedCornerShape(10.dp)
  PlayerUpdate(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.player_prompt_resume_pill, Utils.prettyTime(position)),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
      )
      Surface(
        shape = actionShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
          .clip(actionShape)
          .clickable(onClick = onResume),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = stringResource(R.string.player_resume_action_resume),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
