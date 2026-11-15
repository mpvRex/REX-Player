@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.mpv.rex.ui.browser.miniplayer

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Layout constants used only inside the expanded page. */
internal object MiniPlayerPageDefaults {
  val HorizontalGutter = 24.dp
  val ArtworkSize = 260.dp

  /** Distance a horizontal swipe on the bar must cover to switch tracks. */
  val TrackSwipeDistance = 160.dp
}

/**
 * Layout constants shared by the floating mini player and browser screens that reserve insets.
 */
object MiniPlayerDefaults {
  /** Height of the floating pill. */
  val BarHeight = 64.dp

  /** Corner radius of the floating mini player pill (capsule). */
  val CornerRadius = 32.dp

  /** Horizontal margin: 16.dp on each side. */
  val FloatingHorizontalMargin = 16.dp

  /** Margin between the bottom of the floating bar and the top of the bottom chrome. */
  val FloatingBottomMargin = 16.dp

  /** Shadow elevation under the floating surface. */
  val FloatingElevation = 6.dp

  /**
   * How far floating chrome or browser list insets should lift themselves above the navigation bar
   * while the mini player is playing.
   */
  val TotalCompactOffset get() = BarHeight + FloatingBottomMargin

  val GripStrip = 0.dp
  val BarContentHeight = 64.dp
  val ProgressStrip = 4.dp

  @Deprecated("Use BarHeight: the mini player bar's height.")
  val CompactHeight get() = BarHeight

  @Deprecated("The mini player expands to full screen; there is no intermediate height.")
  val ExpandedHeight = 440.dp
}

/**
 * The floating mini player: a detached capsule pill floating above the bottom chrome.
 *
 * **Gesture architecture:**
 * - Tap or swipe up: grows 1:1 with finger towards full screen, expanding into [MiniPlayerPage].
 * - Swipe down: pulls down 1:1 with finger and fades out, releasing past threshold dismisses playback.
 * - At rest: floats detached with rounded corners on all 4 sides and clear space underneath.
 * - In expanded page: tap collapse or swipe down to return to the floating bar, or tap "Open full player"
 *   to launch [xyz.mpv.rex.ui.player.PlayerActivity].
 */
@Composable
fun MiniPlayer(
  stateManager: MiniPlayerStateManager,
  modifier: Modifier = Modifier,
  bottomChromeHeight: Dp = 0.dp,
  expandedChanged: (Boolean) -> Unit = {},
  collapseRequest: ((() -> Unit)?) -> Unit = {},
) {
  val state by stateManager.state.collectAsState()
  if (!state.isPlaybackActive) return

  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  val pullY = remember { Animatable(0f) }
  val expand = remember { Animatable(0f) }
  var isExpanded by remember { mutableStateOf(false) }

  // A new session always starts collapsed
  LaunchedEffect(state.isPlaybackActive) {
    if (state.isPlaybackActive && !isExpanded) {
      expand.snapTo(0f)
      pullY.snapTo(0f)
    }
  }

  LaunchedEffect(isExpanded) {
    expandedChanged(isExpanded)
  }
  DisposableEffect(Unit) {
    onDispose { expandedChanged(false) }
  }

  val collapseAction: () -> Unit = {
    scope.launch {
      isExpanded = false
      expand.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
    }
  }

  LaunchedEffect(Unit) {
    collapseRequest { collapseAction() }
  }
  DisposableEffect(Unit) {
    onDispose { collapseRequest(null) }
  }

  val dismiss: () -> Unit = {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    isExpanded = false
    scope.launch {
      expand.snapTo(0f)
      pullY.snapTo(0f)
    }
    stateManager.closeMiniPlayer(context)
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val windowHeightPx = with(density) { maxHeight.toPx() }
    val windowWidthPx = with(density) { maxWidth.toPx() }
    val barHeightPx = with(density) { MiniPlayerDefaults.BarHeight.toPx() }
    val bottomMarginPx = with(density) { MiniPlayerDefaults.FloatingBottomMargin.toPx() }
    val horizontalMarginPx = with(density) { MiniPlayerDefaults.FloatingHorizontalMargin.toPx() }
    val bottomChromePx = with(density) { bottomChromeHeight.toPx() }

    val restingTopPx = (windowHeightPx - bottomChromePx - bottomMarginPx - barHeightPx).coerceAtLeast(0f)
    val travelPx = restingTopPx.coerceAtLeast(1f)

    fun settleExpand(fraction: Float, velocityY: Float) {
      val expanding = velocityY < -400f || (velocityY <= 400f && fraction > 0.35f)
      scope.launch {
        expand.snapTo(fraction)
        if (expanding) {
          isExpanded = true
          expand.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
        } else {
          isExpanded = false
          expand.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
      }
    }

    fun settlePull(pulled: Float, velocityY: Float) {
      val dismissing = pulled > barHeightPx * 0.4f || velocityY > 600f
      scope.launch {
        if (dismissing) {
          pullY.animateTo(barHeightPx * 2f, tween(180))
          dismiss()
          pullY.snapTo(0f)
        } else {
          pullY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
      }
    }

    val pageScrollState = rememberScrollState()

    val nestedScrollConnection = remember(travelPx) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (available.y > 0 && pageScrollState.value == 0) {
            val newExpand = (expand.value - available.y / travelPx).coerceIn(0f, 1f)
            scope.launch { expand.snapTo(newExpand) }
            return Offset(0f, available.y)
          } else if (available.y < 0 && expand.value < 1f && expand.value > 0f) {
            val newExpand = (expand.value - available.y / travelPx).coerceIn(0f, 1f)
            scope.launch { expand.snapTo(newExpand) }
            return Offset(0f, available.y)
          }
          return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          if (expand.value < 1f) {
            val velocityY = available.y
            val collapsing = velocityY > 400f || (velocityY >= -400f && expand.value < 0.75f)
            if (collapsing) {
              isExpanded = false
              expand.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
            } else {
              expand.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
            }
            return available
          }
          return Velocity.Zero
        }
      }
    }

    val f = expand.value.coerceIn(0f, 1f)
    val currentTop = floatingCardTopPx(f, pullY.value, barHeightPx, bottomMarginPx, bottomChromePx, windowHeightPx)
    val currentLeft = floatingCardStartPx(f, horizontalMarginPx)
    val currentWidth = floatingCardWidthPx(f, horizontalMarginPx, windowWidthPx)
    val currentHeight = floatingCardHeightPx(f, barHeightPx, windowHeightPx)
    val currentCornerRadius = lerp(MiniPlayerDefaults.CornerRadius, 0.dp, f)
    val currentElevation = lerp(MiniPlayerDefaults.FloatingElevation, 0.dp, f)
    val cardAlpha = (1f - (pullY.value / (barHeightPx * 2f))).coerceIn(0f, 1f)

    // Backdrop scrim active during expand transition and while expanded
    if (f > 0f) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = f * 0.45f }
          .background(Color.Black)
          .then(
            if (isExpanded) {
              Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = collapseAction,
              )
            } else {
              Modifier
            }
          )
      )
    }

    val shape = RoundedCornerShape(currentCornerRadius)
    val isMiniActive = f < 0.5f

    Box(
      modifier = Modifier
        .offset { IntOffset(currentLeft.roundToInt(), currentTop.roundToInt()) }
        .size(
          width = with(density) { currentWidth.toDp() },
          height = with(density) { currentHeight.toDp() },
        )
        .graphicsLayer {
          alpha = cardAlpha
        }
        .shadow(
          elevation = if (pullY.value > 0f) 0.dp else currentElevation,
          shape = shape,
          clip = false,
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = (1f - f) * 0.25f),
          shape = shape,
        )
        .then(
          if (!isExpanded) {
            Modifier
              .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var pulled = 0f
                var grown = 0f
                detectVerticalDragGestures(
                  onDragStart = {
                    tracker.resetTracking()
                    pulled = pullY.value
                    grown = expand.value
                  },
                  onVerticalDrag = { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (pulled > 0f || (grown == 0f && amount > 0f)) {
                      pulled = (pulled + amount).coerceAtLeast(0f)
                      scope.launch { pullY.snapTo(pulled) }
                    } else {
                      grown = (grown - amount / travelPx).coerceIn(0f, 1f)
                      scope.launch { expand.snapTo(grown) }
                    }
                  },
                  onDragEnd = {
                    val velocityY = tracker.calculateVelocity().y
                    if (pulled > 0f) {
                      settlePull(pulled, velocityY)
                    } else if (grown > 0f) {
                      settleExpand(grown, velocityY)
                    }
                  },
                  onDragCancel = {
                    if (pulled > 0f) settlePull(pulled, 0f)
                    if (grown > 0f) settleExpand(grown, 0f)
                  }
                )
              }
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) {
                settleExpand(0f, -1000f)
              }
          } else {
            Modifier
          }
        )
    ) {
      // Bar layer: active when collapsed or collapsing
      if (f < 0.6f) {
        val barAlpha = (1f - f / 0.35f).coerceIn(0f, 1f)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(MiniPlayerDefaults.BarHeight)
            .zIndex(if (isMiniActive) 1f else 0f)
            .graphicsLayer { alpha = barAlpha }
        ) {
          MiniPlayerBar(
            state = state,
            stateManager = stateManager,
            pullFraction = { (pullY.value / barHeightPx).coerceIn(0f, 1f) },
            onClose = dismiss,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // Page layer: active when expanding or expanded
      if (f > 0.15f) {
        val pageAlpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { windowHeightPx.toDp() })
            .zIndex(if (isMiniActive) 0f else 1f)
            .graphicsLayer {
              alpha = pageAlpha
              translationY = (1f - pageAlpha) * 24.dp.toPx()
            }
        ) {
          MiniPlayerPage(
            state = state,
            stateManager = stateManager,
            onCollapse = collapseAction,
            onOpenFullPlayer = {
              stateManager.openPlayer(context)
              scope.launch {
                isExpanded = false
                expand.snapTo(0f)
              }
            },
            onClose = dismiss,
            scrollState = pageScrollState,
            modifier = Modifier.nestedScroll(nestedScrollConnection),
          )
        }
      }
    }
  }
}

/** Calculates the floating card's top offset in px. */
internal fun floatingCardTopPx(
  fraction: Float,
  extensionPx: Float,
  barHeightPx: Float,
  bottomMarginPx: Float,
  bottomChromePx: Float,
  windowHeightPx: Float,
): Float {
  val f = fraction.coerceIn(0f, 1f)
  val collapsedTop = (windowHeightPx - bottomChromePx - bottomMarginPx - barHeightPx).coerceAtLeast(0f)
  val targetTop = lerp(collapsedTop, 0f, f)
  return targetTop + extensionPx * (1f - f)
}

/** Calculates the floating card's horizontal start in px. */
internal fun floatingCardStartPx(
  fraction: Float,
  horizontalMarginPx: Float,
): Float = lerp(horizontalMarginPx, 0f, fraction.coerceIn(0f, 1f))

/** Calculates the floating card's width in px. */
internal fun floatingCardWidthPx(
  fraction: Float,
  horizontalMarginPx: Float,
  windowWidthPx: Float,
): Float = lerp(
  (windowWidthPx - 2 * horizontalMarginPx).coerceAtLeast(0f),
  windowWidthPx,
  fraction.coerceIn(0f, 1f),
)

/** Calculates the floating card's height in px. */
internal fun floatingCardHeightPx(
  fraction: Float,
  barHeightPx: Float,
  windowHeightPx: Float,
): Float = lerp(
  barHeightPx,
  windowHeightPx,
  fraction.coerceIn(0f, 1f),
)

@VisibleForTesting
internal fun testFloatingCardTop(
  fraction: Float,
  extensionPx: Float,
  barHeightPx: Float,
  bottomMarginPx: Float,
  bottomChromePx: Float,
  windowHeightPx: Float,
): Float = floatingCardTopPx(fraction, extensionPx, barHeightPx, bottomMarginPx, bottomChromePx, windowHeightPx)
