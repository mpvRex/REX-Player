package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import xyz.mpv.rex.R

@Composable
fun SlideToUnlock(
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier,
  onDraggingChanged: (Boolean) -> Unit = {},
) {
  val coroutineScope = rememberCoroutineScope()
  
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableGlass by appearancePrefs.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePrefs.matchPlayerControlsToTheme.collectAsState()

  val containerShape = RoundedCornerShape(20.dp)
  val sliderShape = RoundedCornerShape(16.dp)

  var containerWidthPx by remember { mutableFloatStateOf(0f) }
  val sliderSize = 56.dp

  val offsetX = remember { Animatable(0f) }
  var isDragging by remember { mutableStateOf(false) }
  
  val containerModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = containerShape,
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
        if (matchTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f),
        containerShape
      )
      .border(
        width = 1.dp,
        color = if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        shape = containerShape
      )
  }

  val sliderModifier = if (enableGlass) {
    Modifier.glassSurface(
      shape = sliderShape,
      backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
      borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
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
    Modifier.background(MaterialTheme.colorScheme.primary, sliderShape)
  }

  Box(
    modifier = modifier
      .width(200.dp)
      .height(64.dp)
      .clip(containerShape)
      .then(containerModifier)
      .padding(4.dp)
      .onSizeChanged { size ->
        containerWidthPx = size.width.toFloat()
      },
  ) {
    val sliderSizePx = containerWidthPx * (56f / 192f) // Accounting for padding (200 - 8)
    val maxOffset = if (containerWidthPx > 0f) containerWidthPx - sliderSizePx else 0f
    val unlockThreshold = if (maxOffset > 0f) maxOffset * 0.85f else Float.MAX_VALUE

    // Background text - slightly to the right
    val textColor = when {
      enableGlass -> Color.White.copy(alpha = 0.7f)
      matchTheme -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
      else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }

    Box(
      modifier = Modifier
        .matchParentSize()
        .padding(start = 55.dp)
        .alpha(if (maxOffset > 0f) 1f - (offsetX.value / maxOffset).coerceIn(0f, 1f) else 1f),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(R.string.slide_to_unlock),
        color = textColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    // Slider button
    val progress = if (maxOffset > 0f) (offsetX.value / maxOffset).coerceIn(0f, 1f) else 0f
    val showUnlockIcon = progress > 0.5f

    Box(
      modifier = Modifier
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .size(sliderSize)
        .clip(sliderShape)
        .then(sliderModifier)
        .pointerInput(containerWidthPx) {
          if (containerWidthPx <= 0f) return@pointerInput

          detectHorizontalDragGestures(
            onDragStart = {
              isDragging = true
              onDraggingChanged(true)
            },
            onDragEnd = {
              isDragging = false
              onDraggingChanged(false)
              if (offsetX.value >= unlockThreshold) {
                // Unlock triggered - instantly unlock without animation
                onUnlock()
              } else {
                // Snap back
                coroutineScope.launch {
                  offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300),
                  )
                }
              }
            },
            onDragCancel = {
              isDragging = false
              onDraggingChanged(false)
              coroutineScope.launch {
                offsetX.animateTo(
                  targetValue = 0f,
                  animationSpec = tween(durationMillis = 300),
                )
              }
            },
            onHorizontalDrag = { _, dragAmount ->
              coroutineScope.launch {
                val newValue = (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                offsetX.snapTo(newValue)
              }
            },
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      // Crossfade between lock and unlock icons
      androidx.compose.animation.Crossfade(
        targetState = showUnlockIcon,
        animationSpec = tween(durationMillis = 200),
      ) { showUnlock ->
        Icon(
          imageVector = if (showUnlock) Icons.Filled.LockOpen else Icons.Filled.Lock,
          contentDescription = stringResource(R.string.slide_to_unlock),
          tint = Color.White,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}