package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.controls.LocalPlayerButtonsClickEvent
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.spacing
import org.koin.compose.koinInject

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalInControlsDock = staticCompositionLocalOf { false }

@Suppress("ModifierClickableOrder")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsButton(
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onDoubleClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  title: String? = null,
  color: Color? = null,
  isActive: Boolean = false,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val inDock = LocalInControlsDock.current
  val clickEvent = LocalPlayerButtonsClickEvent.current

  val buttonShape = if (inDock) RoundedCornerShape(14.dp) else RoundedCornerShape(12.dp)

  val activeSurfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
  }

  val activeContentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.onSecondaryContainer
    }
    else -> MaterialTheme.colorScheme.primary
  }

  val activeBorderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  )

  val normalSurfaceColor = when {
    inDock -> Color.Transparent
    enableGlass && !hideBackground -> Color.Transparent
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val normalContentColor = when {
    color != null -> color
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  val normalBorderColor = if (inDock || enableGlass || hideBackground) {
    null
  } else {
    BorderStroke(
      1.dp,
      if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
  }

  val surfaceColor = if (isActive) activeSurfaceColor else normalSurfaceColor
  val contentColor = if (isActive) activeContentColor else normalContentColor
  val border = if (isActive) activeBorderColor else normalBorderColor

  val glassModifier = if (enableGlass && !inDock && !hideBackground) {
    Modifier.glassSurface(
      shape = buttonShape,
      backgroundColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
      borderColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
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

  Surface(
    modifier =
      modifier
        .then(glassModifier)
        .clip(buttonShape)
        .combinedClickable(
          onClick = {
            clickEvent()
            onClick()
          },
          onDoubleClick = onDoubleClick?.let {
            {
              clickEvent()
              it()
            }
          },
          onLongClick = onLongClick,
          interactionSource = interactionSource,
          indication = ripple(),
        ),
    shape = buttonShape,
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = contentColor,
      modifier =
        Modifier
          .padding(MaterialTheme.spacing.smaller)
          .size(24.dp),
    )
  }
}

@Composable
fun ControlsGroup(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(16.dp),
  content: @Composable RowScope.() -> Unit,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

  val surfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val contentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  val borderColor = if (hideBackground || enableGlass) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
  )

  val glassModifier = if (enableGlass && !hideBackground) {
    Modifier.glassSurface(
      shape = shape,
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

  CompositionLocalProvider(LocalInControlsDock provides true) {
    if (hideBackground) {
      Row(
        modifier = modifier.height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        content = content,
      )
    } else {
      Surface(
        modifier = modifier
          .height(42.dp)
          .then(glassModifier),
        shape = shape,
        color = surfaceColor,
        contentColor = contentColor,
        border = borderColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          content = content,
        )
      }
    }
  }
}

@Preview
@Composable
private fun PreviewControlsButton() {
  ControlsButton(
    Icons.Default.CatchingPokemon,
    onClick = {},
  )
}

