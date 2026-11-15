@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.mpv.rex.ui.browser.miniplayer

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlin.math.abs
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
 * **One engine, three doors.** Every transition — the drag, the page's scroll, the collapse button,
 * a tap on the page, a Back press and a dismiss — goes through the same
 * [MiniPlayerMorphState.expansion] animatable. The previous version kept an `isExpanded` flag and an
 * `Animatable` per door, and the doors disagreed: the page-scroll path snapped its value inside a
 * `scope.launch`, which is cancelled as soon as the page stops being composed, and the collapse
 * button never cleared the pull distance the scroll handler had stored. Both left the card parked
 * somewhere between the bar and the page.
 *
 * **Gesture architecture:**
 * - Tap the bar, or drag up: grows 1:1 with the finger towards full screen ([MiniPlayerPage]).
 * - Drag down from the bar: pulls it towards the close zone; releasing past the threshold closes it.
 * - Expanded page: swipe down (scroll at the top first) or tap Collapse/the scrim to return to the bar.
 * - At rest: floats detached with rounded corners on all 4 sides and clear space underneath.
 * - "Open full player" hands off to [xyz.mpv.rex.ui.player.PlayerActivity].
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

  val morph = remember { MiniPlayerMorphState(scope) }

  // A new session always starts docked.
  LaunchedEffect(state.isPlaybackActive) {
    if (state.isPlaybackActive) morph.resetDocked()
  }

  // Forwarded as a snapshot-backed value: a plain `isExpanded` bool would lag the morph by a frame
  // for the scrim's click target, but more importantly the Back handler in MainActivity needs the
  // exact same truth the sheet draws from.
  LaunchedEffect(morph) {
    androidx.compose.runtime.snapshotFlow { morph.isExpanded }.collect(expandedChanged)
  }
  DisposableEffect(Unit) {
    onDispose { expandedChanged(false) }
  }

  val collapseAction: () -> Unit = { morph.collapse() }

  LaunchedEffect(Unit) {
    collapseRequest { collapseAction() }
  }
  DisposableEffect(Unit) {
    onDispose { collapseRequest(null) }
  }

  val dismiss: () -> Unit = {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    // Closing ends the session, so the surface must be back at the dock *before* the bar
    // disappears; otherwise the next session would start from wherever the page was left.
    morph.resetDocked()
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

    val pageScrollState = rememberScrollState()

    /**
     * The expanded page's scroll container: dragging down while it is already at the top drags the
     * surface towards the dock, and a downward fling from anywhere in the page settles it back to
     * the bar (the same rule [MiniPlayerMorphMath.resolveTarget] applies to a finger drag).
     *
     * The [MiniPlayerMorphState] is created once per sheet, so this connection is created once too:
     * it reads the animatable's live value instead of capturing a per-composition fraction.
     */
    val nestedScrollConnection =
      remember(morph, pageScrollState, travelPx) {
        object : NestedScrollConnection {
          override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
            // A downward (positive y) drag belongs to the surface while the page is at its top and
            // not yet fully docked. An upward drag belongs to the surface only while it is still
            // on its way down, so the page can start scrolling the moment it is open.
            val draggingDown = available.y > 0f && pageScrollState.value == 0
            val draggingUp = available.y < 0f && morph.expansion.value < 1f
            if (!draggingDown && !draggingUp) return androidx.compose.ui.geometry.Offset.Zero
            val deltaFraction = -available.y / travelPx
            val next = (morph.expansion.value + deltaFraction).coerceIn(0f, 1f)
            morph.snapExpansion(next)
            return available
          }

          override suspend fun onPreFling(available: Velocity): Velocity {
            val fraction = morph.expansion.value
            // Fully open: the page owns the fling, it may scroll.
            if (fraction >= 1f) return Velocity.Zero
            // Anywhere else the surface is the one being thrown: a flick opens (or closes) it, a
            // slow release falls back to the release threshold.
            val open = MiniPlayerMorphMath.resolveTarget(fraction, -available.y / travelPx)
            // `snapExpansion` above wrote the value through a continuation, so a running animation
            // still has to be stopped before the settle starts.
            morph.stop()
            morph.animateExpansionTo(
              target = if (open) 1f else 0f,
              initialVelocity = -available.y / travelPx,
            )
            return available
          }
        }
      }

    val f = morph.expansion.value.coerceIn(0f, 1f)
    val currentTop = floatingCardTopPx(f, morph.extension.value, barHeightPx, bottomMarginPx, bottomChromePx, windowHeightPx)
    val currentLeft = floatingCardStartPx(f, horizontalMarginPx)
    val currentWidth = floatingCardWidthPx(f, horizontalMarginPx, windowWidthPx)
    val currentHeight = floatingCardHeightPx(f, barHeightPx, windowHeightPx)
    val currentCornerRadius = lerp(MiniPlayerDefaults.CornerRadius, 0.dp, f)
    val currentElevation = lerp(MiniPlayerDefaults.FloatingElevation, 0.dp, f)
    val cardAlpha = (1f - (morph.extension.value / (barHeightPx * 2f))).coerceIn(0f, 1f)

    // Backdrop scrim active during expand transition and while expanded
    if (f > 0f) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          // The scrim only absorbs (and collapses on) taps once the page is the destination; during
          // the opening drag the finger that stays down must keep feeding the bar's gesture below.
          .then(
            if (morph.isExpanded) {
              Modifier
                .graphicsLayer { alpha = f * 0.45f }
                .background(Color.Black)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = collapseAction,
                )
            } else {
              Modifier.graphicsLayer { alpha = f * 0.45f }.background(Color.Black)
            }
          )
      )
    }

    val shape = RoundedCornerShape(currentCornerRadius)
    val isBarOnTop = f < 0.5f

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
          elevation = if (morph.extension.value > 0f) 0.dp else currentElevation,
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
        .miniPlayerSheetGestures(
          state = morph,
          barHeightPx = { barHeightPx },
          travelPx = { travelPx },
          onTap = { morph.toggle() },
          onDismissRequested = dismiss,
          onEnterDismissZone = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          },
        )
    ) {
      // Bar layer: active when docked or docked + pulled, fading out as the surface opens.
      if (f < MiniPlayerMorphMath.PAGE_FADE_START + 0.05f) {
        val barAlpha = MiniPlayerMorphMath.barContentAlpha(f)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(MiniPlayerDefaults.BarHeight)
            .zIndex(if (isBarOnTop) 1f else 0f)
            .graphicsLayer { this.alpha = barAlpha }
        ) {
          MiniPlayerBar(
            state = state,
            stateManager = stateManager,
            pullFraction = { (morph.extension.value / barHeightPx).coerceIn(0f, 1f) },
            onClose = dismiss,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // Page layer: active when expanding or expanded.
      if (f > 0.1f) {
        val pageAlpha = MiniPlayerMorphMath.pageContentAlpha(f)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { windowHeightPx.toDp() })
            .zIndex(if (isBarOnTop) 0f else 1f)
            .graphicsLayer {
              alpha = pageAlpha
              // Land the content on the last few dp of travel: the sheet finishes opening, then
              // the page settles into place.
              translationY = (1f - pageAlpha) * 24.dp.toPx()
            }
        ) {
          MiniPlayerPage(
            state = state,
            stateManager = stateManager,
            onCollapse = collapseAction,
            onOpenFullPlayer = {
              stateManager.openPlayer(context)
              morph.resetDocked()
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
