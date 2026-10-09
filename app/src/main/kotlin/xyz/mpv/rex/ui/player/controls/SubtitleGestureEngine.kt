package xyz.mpv.rex.ui.player.controls

import kotlin.math.abs
import kotlin.math.sqrt

/** One finger that is currently pressed. [id] is the stable pointer id of that finger. */
internal data class TouchPoint(val id: Long, val x: Float, val y: Float)

/**
 * Receives the decisions made by [SubtitleGestureEngine]. The engine never talks to mpv or to
 * Compose directly, which keeps every finger transition testable on a plain JVM.
 */
internal interface SubtitleGestureListener {
  /** A finger landed on the subtitle. Show the "grabbed" marker. */
  fun onGrabbed()

  /** The finger moved far enough to start repositioning. */
  fun onDragStarted()

  /** New continuous vertical anchor in percent of the viewport height (0..150). */
  fun onPosition(positionPercent: Float)

  /** Two fingers moved far enough apart/together to start resizing. */
  fun onPinchStarted()

  /** New subtitle scale. */
  fun onScale(scale: Float)

  /** The last finger was lifted (or the session was cancelled). Called exactly once. */
  fun onEnded(position: Float, scale: Float, moved: Boolean, scaled: Boolean)
}

/**
 * State machine for ONE touch session that started on a subtitle.
 *
 * The session lives from the first finger down until the last finger up and supports any
 * sequence of fingers in between. A single rule makes every transition seamless:
 *
 *   whenever the set of fingers that drives the gesture changes, the engine re-anchors on the
 *   fingers' CURRENT geometry and on the CURRENT subtitle position/scale.
 *
 * Nothing is ever computed from where a finger was when the session started, so adding a
 * finger, lifting a finger, or going drag -> pinch -> drag can never make the subtitle jump,
 * and the remaining finger always keeps control.
 *
 * - One finger  : vertical drag (after [dragSlop]), 1:1 with the finger.
 * - Two fingers : pinch resize (after [pinchSlop] change of finger distance).
 * - Extra fingers are ignored; the tracked pair stays stable while both are down.
 */
internal class SubtitleGestureEngine(
  private val listener: SubtitleGestureListener,
  private val dragEnabled: Boolean,
  private val pinchEnabled: Boolean,
  private val viewportHeight: Float,
  startPosition: Float,
  startScale: Float,
  private val dragSlop: Float,
  private val pinchSlop: Float,
  /**
   * Highest vertical position the drag may reach. Callers lower it so the subtitle cannot be
   * dragged to where nothing of it is left on screen (and so cannot be grabbed again).
   */
  positionMax: Float = POSITION_MAX,
  /**
   * Scale-aware ceiling. The grabbable strip depends on the subtitle's size, so pinching must move
   * the ceiling too; when given it replaces [positionMax]. A subtitle that already starts above the
   * ceiling keeps that overshoot until it is dragged back inside, so grabbing it can never make it
   * jump, and it can never be dragged further out.
   */
  private val positionMaxForScale: ((Float) -> Float)? = null,
) {
  private enum class Mode { NONE, ONE, TWO }

  private val fixedMaxPosition: Float = positionMax.coerceIn(POSITION_MIN, POSITION_MAX)

  var scale: Float = startScale.coerceIn(SCALE_MIN, SCALE_MAX)
    private set

  /**
   * How far past the ceiling the subtitle was when it was grabbed. It only ever shrinks: once the
   * subtitle has been dragged back inside the allowed range it can no longer be dragged out again.
   */
  private var ceilingOvershoot: Float =
    positionMaxForScale?.let { maxOf(0f, startPosition - it(scale)) } ?: 0f

  var position: Float = startPosition.coerceIn(POSITION_MIN, currentMaxPosition())
    private set

  private var mode = Mode.NONE
  private var idA = NO_ID
  private var idB = NO_ID

  // One-finger anchors.
  private var anchorY = 0f
  private var anchorPosition = 0f

  // Two-finger anchors.
  private var baseDistance = 0f
  private var baseScale = 0f

  /** The drag crossed its slop at least once; it then survives finger hand-offs. */
  private var dragActive = false

  /** The current two-finger phase crossed its slop. Reset whenever the pair changes. */
  private var pinchActive = false

  private var moved = false
  private var scaled = false
  private var ended = false
  private var begun = false

  /** Starts the session with the finger(s) that are down right now. */
  fun begin(points: List<TouchPoint>) {
    if (begun || ended) return
    begun = true
    listener.onGrabbed()
    update(points)
  }

  /** Feed the currently pressed fingers after every pointer event. Empty list = all lifted. */
  fun update(points: List<TouchPoint>) {
    if (ended) return
    when {
      points.isEmpty() -> finish()
      points.size == 1 -> handleOneFinger(points[0])
      else -> handleTwoFingers(points)
    }
  }

  /** Ends the session. Safe to call any number of times; the listener is notified once. */
  fun finish() {
    if (ended) return
    ended = true
    listener.onEnded(position, scale, moved, scaled)
  }

  /** Highest position allowed right now, for the CURRENT scale. */
  private fun currentMaxPosition(): Float {
    val forScale = positionMaxForScale ?: return fixedMaxPosition
    return (forScale(scale) + ceilingOvershoot).coerceIn(POSITION_MIN, POSITION_MAX)
  }

  private fun handleOneFinger(p: TouchPoint) {
    if (mode != Mode.ONE || idA != p.id) {
      // A different finger (or a different mode) now drives the drag: re-anchor on it, using the
      // position the subtitle has RIGHT NOW. This is what removes jumps and "dead" fingers.
      mode = Mode.ONE
      idA = p.id
      idB = NO_ID
      anchorY = p.y
      anchorPosition = position
    }

    if (!dragEnabled || viewportHeight <= 0f) return

    val dy = p.y - anchorY
    if (!dragActive) {
      if (abs(dy) < dragSlop) return
      dragActive = true
      moved = true
      // Start from where the finger is now, so crossing the slop never shifts the subtitle.
      anchorY = p.y
      anchorPosition = position
      listener.onDragStarted()
      return
    }

    val next = (anchorPosition + dy / viewportHeight * 100f).coerceIn(POSITION_MIN, currentMaxPosition())
    if (next != position) {
      position = next
      positionMaxForScale?.let { limit ->
        ceilingOvershoot = minOf(ceilingOvershoot, maxOf(0f, next - limit(scale)))
      }
      listener.onPosition(next)
    }
  }

  private fun handleTwoFingers(points: List<TouchPoint>) {
    val a: TouchPoint
    val b: TouchPoint
    val keptA = if (mode == Mode.TWO) points.firstOrNull { it.id == idA } else null
    val keptB = if (mode == Mode.TWO) points.firstOrNull { it.id == idB } else null
    if (keptA != null && keptB != null) {
      a = keptA
      b = keptB
    } else {
      val sorted = points.sortedBy { it.id }
      a = sorted[0]
      b = sorted[1]
    }

    if (mode != Mode.TWO || idA != a.id || idB != b.id) {
      mode = Mode.TWO
      idA = a.id
      idB = b.id
      baseDistance = distance(a, b)
      baseScale = scale
      pinchActive = false
    }

    if (!pinchEnabled) return

    val d = distance(a, b)
    if (!pinchActive) {
      if (abs(d - baseDistance) < pinchSlop) return
      pinchActive = true
      scaled = true
      // Same idea as the drag slop: begin from the current distance and the current scale.
      baseDistance = d
      baseScale = scale
      listener.onPinchStarted()
      return
    }

    if (baseDistance < MIN_BASE_DISTANCE) return
    val next = (baseScale * (d / baseDistance)).coerceIn(SCALE_MIN, SCALE_MAX)
    if (next != scale) {
      scale = next
      listener.onScale(next)
      // A smaller subtitle has a lower ceiling. If it now sits past it, pull it back so it stays
      // grabbable instead of silently ending up off-screen.
      val ceiling = currentMaxPosition()
      if (position > ceiling) {
        position = ceiling
        moved = true
        listener.onPosition(ceiling)
      }
    }
  }

  private fun distance(a: TouchPoint, b: TouchPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
  }

  companion object {
    const val POSITION_MIN = 0f
    const val POSITION_MAX = 150f
    const val SCALE_MIN = 0.3f
    const val SCALE_MAX = 5f
    private const val MIN_BASE_DISTANCE = 8f
    private const val NO_ID = Long.MIN_VALUE
  }
}
