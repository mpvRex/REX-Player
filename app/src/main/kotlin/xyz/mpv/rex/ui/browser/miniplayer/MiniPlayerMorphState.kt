package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.abs

/**
 * Pure drag maths for the docked mini player, kept out of the composable so the thresholds can be
 * unit-tested without a Compose runtime.
 *
 * The morph is described by one number, [expansion]: `0` is the bar docked on top of the
 * navigation bar, `1` is the full-screen page. Every visual property is a function of it, and a
 * finger drag is a function that maps vertical travel onto it.
 */
internal object MiniPlayerMorphMath {
  /**
   * Share of the bar's own height a downward drag has to cover from the docked state before
   * releasing closes the player. A quick flick closes earlier.
   */
  const val DISMISS_TRAVEL_FRACTION = 0.4f

  /** A downward flick at least this fast (dp/s) closes the bar from the docked state. */
  const val DISMISS_FLING_DP_PER_SECOND = 550f

  /** Release velocity (fraction/s) past which the direction of the throw, not the position, wins. */
  const val FLING_FRACTION_PER_SECOND = 0.35f

  /** Drag travel allowed past the docked anchor while pulling down, in bar heights. */
  const val PULL_LIMIT_BAR_HEIGHTS = 0.9f

  /** Expansion at which the bar's content has fully handed over to the page's content. */
  const val PAGE_FADE_START = 0.34f

  /** Linear interpolation. */
  fun lerp(
    from: Float,
    to: Float,
    fraction: Float,
  ): Float = from + (to - from) * fraction

  /** Smooth 0..1 ramp: 0 at or below [start], 1 at or above [end]. */
  fun smoothStep(
    start: Float,
    end: Float,
    value: Float,
  ): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  /** [value] linearly interpolated across [from]..[to], clamped to that range. */
  fun progressBetween(
    value: Float,
    from: Float,
    to: Float,
  ): Float =
    if (to - from <= 1e-4f) {
      if (value >= to) 1f else 0f
    } else {
      ((value - from) / (to - from)).coerceIn(0f, 1f)
    }

  /**
   * Maps a vertical drag delta onto the expansion fraction.
   *
   * Positive [dragY] is downwards. Away from the dock, dragging down grows the fraction (bar →
   * page) and dragging up shrinks it. From the docked state there is nothing below to pull up
   * into, so a downward drag instead *pulls the bar away* towards the close zone; that distance
   * comes back as [DragFrame.pullPx].
   *
   * Once a gesture has pulled the bar, [startedDocked] stays true for the whole gesture, so the
   * pull stays absolute to the finger instead of restarting from zero on every delta.
   */
  fun dragFrame(
    currentFraction: Float,
    dragY: Float,
    travelPx: Float,
    barHeightPx: Float,
    startedDocked: Boolean,
  ): DragFrame {
    if (startedDocked) {
      if (dragY <= 0f) return DragFrame(0f, 0f)
      val limit = barHeightPx * PULL_LIMIT_BAR_HEIGHTS
      return DragFrame(0f, dragY.coerceIn(0f, limit))
    }
    if (currentFraction <= 0f) return DragFrame(0f, 0f)
    val range = travelPx.coerceAtLeast(1f)
    return DragFrame((currentFraction + dragY / range).coerceIn(0f, 1f), 0f)
  }

  /**
   * Where a release should settle.
   *
   * A throw faster than [FLING_FRACTION_PER_SECOND] wins outright; otherwise the halfway point
   * decides, which is what makes a slow, deliberate drag feel like a physical stop.
   */
  fun resolveTarget(
    fraction: Float,
    velocityFractionPerSecond: Float,
  ): Boolean =
    if (abs(velocityFractionPerSecond) > FLING_FRACTION_PER_SECOND) {
      velocityFractionPerSecond > 0f
    } else {
      fraction > 0.5f
    }

  /**
   * Whether releasing a pull from the docked bar closes the player instead of springing back.
   *
   * [downVelocityDp] is positive downwards; [pullPx] is how far the finger dragged the bar down.
   */
  fun shouldDismissFromPull(
    pullPx: Float,
    barHeightPx: Float,
    downVelocityDp: Float,
  ): Boolean =
    pullPx > barHeightPx * DISMISS_TRAVEL_FRACTION || downVelocityDp > DISMISS_FLING_DP_PER_SECOND

  /** Opacity of the content behind the sheet at a given expansion: 0 docked, 0.6 full screen. */
  fun scrimAlpha(fraction: Float): Float = smoothStep(0f, 0.9f, fraction) * 0.6f

  /** Alpha of the bar's own content: gone once the surface has largely opened. */
  fun barContentAlpha(fraction: Float): Float = 1f - smoothStep(0.02f, PAGE_FADE_START, fraction)

  /**
   * Alpha of the full-screen page content: it lands *after* the surface has grown.
   *
   * Its window starts exactly where [barContentAlpha]'s ends ([PAGE_FADE_START]) so the two never
   * overlap: at that point the bar is fully gone and the page is still fully transparent, and the
   * screen is an empty piece of the sheet travelling upwards. That is what makes the page read as
   * arriving rather than as fading in behind a bar that is still there.
   */
  fun pageContentAlpha(fraction: Float): Float = smoothStep(PAGE_FADE_START, 1f, fraction)
}

/** One frame of a drag: the resolved expansion (0 docked, 1 page) and the pull past the dock. */
internal data class DragFrame(
  val fraction: Float,
  val pullPx: Float,
)

/**
 * The animation engine behind the morphing mini player.
 *
 * Holds the one value the sheet is drawn from — [expansion] — and owns every transition between
 * the three resting places: docked bar, full-screen page, dismissed. The distance a docked bar has
 * been pulled towards the close zone lives in [extension], a second animatable that the layout adds
 * to the bar's position below the dock.
 *
 * Only one animation runs at a time ([motionJob]); a tap during a settle takes over from where the
 * surface is instead of starting a second animation to fight the first.
 */
@Stable
internal class MiniPlayerMorphState(
  private val scope: CoroutineScope,
  initialExpansion: Float = 0f,
) {
  /** How far the sheet has been dragged below the dock, in px. Zero unless docked. */
  val extension = Animatable(0f)

  /**
   * The page is the destination: `true` once a transition towards the page starts, `false` once
   * one towards the dock starts.
   *
   * This exists so composition (the back handler, the backdrop's interactivity) can ask "is the
   * page open?" without reading [expansion]. The animated fraction crosses 0.5 during a drag and
   * would recompose the whole host on every frame that does; this flips once per transition.
   */
  var isExpanded by mutableStateOf(initialExpansion > 0.5f)
    private set

  /** 0 = docked bar, 1 = full-screen page. Read in layout/draw lambdas, not in composition. */
  val expansion = Animatable(initialExpansion)

  /**
   * True while the docked bar is held far enough down that releasing would close it. A plain
   * `State`, so the haptic only fires when the flag flips.
   */
  var isInDismissZone by mutableStateOf(false)
    private set

  private var motionJob: Job? = null

  /**
   * How far this gesture has pulled the docked bar away from the dock, in px. Reset by
   * [beginGesture] so a cancelled settle can never leave the next pull dead.
   */
  private var gesturePullPx = 0f

  /** True while the current gesture started at the dock and is therefore a dismiss pull. */
  private var gesturePulls = false

  private var accumulatedDragY = 0f
  private var initialFractionOnDrag = 0f

  /**
   * Writes [target] with a non-suspending snap.
   *
   * `Animatable.snapTo` is suspending only because it takes the internal mutator; the value is
   * set synchronously. A drag needs that value *now* — one frame per finger event — so the mutation
   * is run without suspending. Once the write lands, the animatable reports its new target and the
   * frame requests a redraw like any other animation.
   */
  private fun snapDragValue(
    animatable: Animatable<Float, *>,
    target: Float,
  ) {
    // `snapTo` is a suspend function only by signature: its body stops any running animation,
    // clamps, writes `internalState.value` and sets the target, then returns without ever
    // suspending (verified in animation-core 1.6.8 bytecode). The builder below runs that body up
    // to its first (non-existent) suspension point, so the value is written on this frame.
    suspend { animatable.snapTo(target) }.startCoroutine(Continuation(EmptyCoroutineContext) { })
  }

  /** True while anything about the surface is still moving. */
  val isMoving: Boolean get() = motionJob?.isActive == true

  /** Docked and still: the resting bar. */
  val isRestingDocked: Boolean
    get() = expansion.value == 0f && extension.value == 0f && !isMoving

  /** The page is the destination (or being dragged towards it). */
    fun expand(velocityFractionPerSecond: Float = 0f) {
    isExpanded = true
    extension.snapToSilently(0f)
    animateTo(1f, MiniPlayerMorphSpecs.open, velocityFractionPerSecond)
  }

  fun collapse(velocityFractionPerSecond: Float = 0f) {
    isExpanded = false
    extension.snapToSilently(0f)
    animateTo(0f, MiniPlayerMorphSpecs.close, velocityFractionPerSecond)
  }

  /**
   * A tap anywhere on the sheet.
   *
   * The decision is made from the *target*, not from where the surface happens to be. Reading the
   * animated value meant a tap at f = 0.3 during an opening spring counted as "not expanded" and
   * opened again; a second tap must always reverse the first one.
   */
  fun toggle() {
    if (isExpanded) collapse() else expand()
  }

  /**
   * Applies one drag delta directly to the surface's position.
   *
   * Moving UP (`deltaY < 0`) expands the player towards 1 (or reduces an active pull).
   * Moving DOWN (`deltaY > 0`) collapses the player towards 0, or pulls the collapsed bar towards dismiss.
   * Snapped synchronously without suspending so the card tracks the finger 1:1 on every frame.
   */
  fun onDragDelta(
    deltaY: Float,
    travelPx: Float,
    barHeightPx: Float,
  ) {
    val range = travelPx.coerceAtLeast(1f)
    val limit = barHeightPx * MiniPlayerMorphMath.PULL_LIMIT_BAR_HEIGHTS

    if (deltaY < 0f) {
      // Finger moving UP (towards expanded / away from dismiss)
      if (extension.value > 0f) {
        val prevExt = extension.value
        val newExtension = (prevExt + deltaY).coerceAtLeast(0f)
        gesturePullPx = newExtension
        snapDragValue(extension, newExtension)
        val leftover = deltaY + (prevExt - newExtension)
        if (newExtension == 0f && leftover < 0f) {
          val deltaFraction = -leftover / range
          val newFraction = (expansion.value + deltaFraction).coerceIn(0f, 1f)
          snapDragValue(expansion, newFraction)
        }
      } else {
        val deltaFraction = -deltaY / range
        val newFraction = (expansion.value + deltaFraction).coerceIn(0f, 1f)
        snapDragValue(expansion, newFraction)
      }
    } else if (deltaY > 0f) {
      // Finger moving DOWN (towards collapsed / towards dismiss)
      if (expansion.value > 0f) {
        val deltaFraction = -deltaY / range
        val prevFrac = expansion.value
        val newFraction = (prevFrac + deltaFraction).coerceAtLeast(0f)
        snapDragValue(expansion, newFraction)
        if (newFraction == 0f) {
          val leftover = deltaY - (prevFrac * range)
          if (leftover > 0f) {
            val newExtension = (extension.value + leftover).coerceIn(0f, limit)
            gesturePullPx = newExtension
            snapDragValue(extension, newExtension)
          }
        }
      } else {
        val newExtension = (extension.value + deltaY).coerceIn(0f, limit)
        gesturePullPx = newExtension
        snapDragValue(extension, newExtension)
      }
    }

    val inZone = gesturePullPx > barHeightPx * MiniPlayerMorphMath.DISMISS_TRAVEL_FRACTION
    if (inZone != isInDismissZone) isInDismissZone = inZone
  }

  /**
   * Arms a new gesture.
   *
   * The pull is a property of *this* gesture, not of the animation that may have been cancelled
   * before it: inferring "already docked" from `extension.value` meant that stopping a settle
   * mid-flight left a non-zero extension behind, and every later pull fell into the "already
   * open, dragging up" branch and did nothing.
   *
   * This deliberately does **not** stop a running transition. A press that turns out to be a tap on
   * a button, a seek scrub or a horizontal swipe must not cancel an in-flight open/settle; the
   * animation is stopped by [takeOver] at the moment the sheet's own vertical drag begins.
   */
  fun beginGesture() {
    gesturePullPx = 0f
    accumulatedDragY = 0f
    initialFractionOnDrag = expansion.value
    gesturePulls = expansion.value <= 0f
    isInDismissZone = false
  }

  /**
   * The finger is now actually moving the surface vertically: stop whatever animation was running
   * and take over from where it is, with no leftover pull.
   */
  fun takeOver() {
    stop()
    gesturePullPx = 0f
    accumulatedDragY = 0f
    initialFractionOnDrag = expansion.value
    snapDragValue(extension, 0f)
  }

  /** Stops a running settle so a new gesture can take the surface over from where it is. */
  fun stop() {
    motionJob?.cancel()
  }

  /**
   * Hands the surface back after the finger lifts.
   *
   * [downVelocityDp] is positive downwards; [velocityFractionPerSecond] is positive towards the
   * page. From the dock, a pull past the threshold or a downward flick closes the player.
   */
  fun onDragEnd(
    velocityFractionPerSecond: Float,
    downVelocityDp: Float,
    barHeightPx: Float,
    onDismissed: () -> Unit,
  ) {
    val pulled = gesturePullPx
    gesturePullPx = 0f
    if (pulled > 0f || extension.value > 0f) {
      isInDismissZone = false
      if (MiniPlayerMorphMath.shouldDismissFromPull(pulled, barHeightPx, downVelocityDp)) {
        dismissFrom(
          startPx = pulled,
          barHeightPx = barHeightPx,
          velocityDp = downVelocityDp,
          onDismissed = onDismissed,
        )
      } else {
        motionJob?.cancel()
        motionJob =
          scope.launch {
            extension.animateTo(0f, MiniPlayerMorphSpecs.settle, initialVelocity = downVelocityDp)
          }
      }
      return
    }
    val open = MiniPlayerMorphMath.resolveTarget(expansion.value, velocityFractionPerSecond)
    animateTo(
      target = if (open) 1f else 0f,
      spec = if (open) MiniPlayerMorphSpecs.open else MiniPlayerMorphSpecs.close,
      initialVelocity = velocityFractionPerSecond,
    )
  }

  /** Runs the bar off the bottom edge and calls [onDismissed] once it has left. */
  fun dismiss(
    barHeightPx: Float,
    velocityDp: Float,
    onDismissed: () -> Unit,
  ) = dismissFrom(extension.value, barHeightPx, velocityDp, onDismissed)

  private fun dismissFrom(
    startPx: Float,
    barHeightPx: Float,
    velocityDp: Float,
    onDismissed: () -> Unit,
  ) {
    motionJob?.cancel()
    isInDismissZone = false
    motionJob =
      scope.launch {
        // Keep whatever the finger left behind and add a whole bar of travel, so the surface
        // finishes leaving in the direction it was thrown.
        extension.animateTo(
          targetValue = startPx + barHeightPx,
          animationSpec = MiniPlayerMorphSpecs.dismiss,
          initialVelocity = velocityDp.coerceAtLeast(0f),
        )
        onDismissed()
      }
    // Cancelling the run must still close the session: without this, grabbing the bar mid-dismiss
    // left it alive with the sheet gone off-screen.
    motionJob?.invokeOnCompletion { cause ->
      if (cause != null) runCatching { onDismissed() }
    }
  }

  /** Returns to the docked state without animation; used when a session starts or ends. */
  fun resetDocked() {
    motionJob?.cancel()
    isInDismissZone = false
    gesturePullPx = 0f
    isExpanded = false
    motionJob =
      scope.launch {
        extension.snapTo(0f)
        expansion.snapTo(0f)
      }
  }

  private fun animateTo(
    target: Float,
    spec: FiniteAnimationSpec<Float>,
    initialVelocity: Float,
  ) {
    motionJob?.cancel()
    isInDismissZone = false
    gesturePullPx = 0f
    isExpanded = target > 0.5f
    motionJob =
      scope.launch {
        expansion.animateTo(target, spec, initialVelocity)
      }
  }

  private fun Animatable<Float, *>.snapToSilently(value: Float) {
    scope.launch { snapTo(value) }
  }
}

/**
 * The sheet's springs, chosen per direction rather than all derived from one scheme.
 *
 * Opening is the M3 Expressive slow spatial spring — the same family the theme installs, so a tap
 * reads as part of the app. Closing is slower and critically damped: leaving a page should glide,
 * not snap. A settle after a drag is a little bouncy, because the finger handed it speed.
 */
internal object MiniPlayerMorphSpecs {
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  val open: FiniteAnimationSpec<Float> get() = MotionScheme.expressive().slowSpatialSpec()

  val close: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 180f)

  val settle: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 420f)

  val dismiss: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)
}
