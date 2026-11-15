package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * The one gesture on the mini player sheet: tap to open, drag to scrub between the docked bar and
 * the full-screen page, swipe down from the docked bar to close it.
 *
 * **Why one hand-written loop instead of stacked detectors.**
 * `detectTapGestures`, `detectVerticalDragGestures` and `anchoredDraggable` are three separate
 * pointer-input nodes. Inserting or removing one of them while a finger is down — which is exactly
 * what "tap expands, so now a drag detector replaces the tap detector" does — re-pairs the chain and
 * cancels the in-flight gesture. One node whose structure never changes makes the tap → drag
 * hand-over a pure state change, and lets a press that barely moved be re-read as a tap on release.
 *
 * It also lets the drag own the surface frame by frame: every delta is snapped straight into the
 * [MiniPlayerMorphState] animatables, so nothing recomposes while the sheet moves.
 *
 * The modifier wraps the bar *and* the page. Buttons inside consume their press; a gesture that
 * arrives with the change already consumed is left alone until every pointer lifts.
 */
internal fun Modifier.miniPlayerSheetGestures(
  state: MiniPlayerMorphState,
  barHeightPx: () -> Float,
  travelPx: () -> Float,
  onTap: () -> Unit,
  onDismissRequested: () -> Unit,
  onInteractionStart: () -> Unit = {},
  onInteractionEnd: () -> Unit = {},
  onEnterDismissZone: () -> Unit = {},
): Modifier =
  pointerInput(state) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)

      // A child took the press before it reached us: let it run to completion untouched.
      if (down.isConsumed) {
        awaitAllUp()
        return@awaitEachGesture
      }

      state.beginGesture()
      onInteractionStart()

      val tracker = VelocityTracker()
      tracker.addPosition(down.uptimeMillis, Offset.Zero)

      var travel = 0f
      var followed = false
      var consumedByChild = false
      var wasInDismissZone = false

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        if (change.isConsumed) {
          consumedByChild = true
          break
        }
        val delta = change.positionChange()
        tracker.addPosition(change.uptimeMillis, change.position)
        travel += delta.getDistance()
        if (!followed && travel > viewConfiguration.touchSlop) {
          followed = true
          // Only now does this gesture own the surface; a press that became a tap on a button or a
          // sideways swipe must leave an in-flight open/settle alone.
          state.takeOver()
        }
        if (followed && abs(delta.y) > 0f) {
          change.consume()
          state.onDragDelta(
            deltaY = delta.y,
            travelPx = travelPx(),
            barHeightPx = barHeightPx(),
          )
          // Crossing *into* the dismiss zone is the one moment worth announcing with a tick:
          // the bar is already moving under the finger, so the user has the rest for free.
          if (state.isInDismissZone && !wasInDismissZone) onEnterDismissZone()
          wasInDismissZone = state.isInDismissZone
        }
      }

      if (consumedByChild) {
        awaitAllUp()
        onInteractionEnd()
        return@awaitEachGesture
      }

      if (!followed) {
        onInteractionEnd()
        onTap()
        return@awaitEachGesture
      }

      val velocity = tracker.calculateVelocity()
      onInteractionEnd()
      state.onDragEnd(
        velocityFractionPerSecond = -velocity.y / travelPx().coerceAtLeast(1f),
        downVelocityDp = velocity.y,
        barHeightPx = barHeightPx(),
        onDismissed = onDismissRequested,
      )
    }
  }

/**
 * Horizontal swipe on the **docked bar**: previous on a right swipe, next on a left swipe.
 *
 * The bar is a single row that cannot grow a second set of buttons without breaking the docked
 * silhouette, so the track switch stays where the old card had it: a sideways swipe on the bar,
 * with the outgoing title and the incoming one sliding through each other. Vertical drags are left
 * to [miniPlayerSheetGestures] (this detector never claims them), and taps fail the gesture fast so
 * the press still reads as a tap on the sheet.
 *
 * @param onPreview the distance travelled, 0..1, so the bar can show what is coming.
 * @param onPreviewEnd `null` when the preview clears; `true` for next, `false` for previous.
 */
internal fun Modifier.miniPlayerTrackSwipe(
  swipeDistancePx: Float,
  enabledNext: () -> Boolean,
  enabledPrevious: () -> Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onPreview: (direction: Float, fraction: Float) -> Unit,
  onPreviewEnd: () -> Unit,
): Modifier =
  // The callbacks close over the current `MiniPlayerState` (does the queue have a next track?), so
  // the block must be re-keyed when they change identity. Keying on `Unit` kept the callbacks from
  // the first composition alive and the swipe stayed dead once the queue moved on.
  pointerInput(swipeDistancePx, enabledNext, enabledPrevious, onNext, onPrevious) {
    val distancePx = swipeDistancePx
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
      var totalX = 0f
      var totalY = 0f
      var locked: Int = AXIS_UNDECIDED
      // The preview is cleared in `finally`, so a cancelled gesture can never leave the title
      // permanently displaced.
      try {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Main)
          val change = event.changes.firstOrNull { it.id == down.id }
          if (change == null || !change.pressed) break
          // A button inside the bar took the press: never fight it.
          if (change.isConsumed) return@awaitEachGesture
          val delta = change.positionChange()
          totalX += delta.x
          totalY += delta.y

          if (locked == AXIS_UNDECIDED) {
            val ax = abs(totalX)
            val ay = abs(totalY)
            if (ax < viewConfiguration.touchSlop && ay < viewConfiguration.touchSlop) continue
            // Below the vertical slop, but clearly sideways: this is a track switch.
            locked = if (ax > ay) AXIS_HORIZONTAL else AXIS_VERTICAL
          }
          if (locked == AXIS_VERTICAL) return@awaitEachGesture

          change.consume()
          val towardsNext = totalX < 0f
          val allowed = if (towardsNext) enabledNext() else enabledPrevious()
          onPreview(
            if (towardsNext) -1f else 1f,
            if (allowed) MiniPlayerMorphMath.progressBetween(abs(totalX), 0f, distancePx) else 0f,
          )
        }

        if (locked != AXIS_HORIZONTAL) return@awaitEachGesture
        if (abs(totalX) >= distancePx) {
          if (totalX < 0f) {
            if (enabledNext()) onNext()
          } else if (enabledPrevious()) {
            onPrevious()
          }
        }
      } finally {
        onPreviewEnd()
      }
    }
  }

private const val AXIS_UNDECIDED = 0
private const val AXIS_HORIZONTAL = 1
private const val AXIS_VERTICAL = 2

/** Wait for every pointer to lift, so a sibling gesture never starts on a finger already down. */
private suspend fun AwaitPointerEventScope.awaitAllUp() {
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Main)
    if (event.changes.all { !it.pressed }) return
  }
}
