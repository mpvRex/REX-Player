package xyz.mpv.rex.ui.player.controls

import kotlin.math.hypot

/** Which third of the player a tap landed in, after [TapConfig.reverse] is applied. */
internal enum class TapRegion { Left, Center, Right }

/**
 * Receives the decisions made by [TapChromeEngine]. The engine never talks to mpv or to Compose
 * directly, which keeps every tap transition testable on a plain JVM.
 */
internal interface TapChromeListener {
  fun onShowChrome()

  fun onHideChrome()

  fun onAction(region: TapRegion, isSingleTap: Boolean)
}

/** Player state that is read fresh on every UP. The host fills this from the ViewModel. */
internal data class TapEnv(
  val chromeShown: Boolean,
  val locked: Boolean,
  val panelOrSheetOpen: Boolean,
)

internal data class TapConfig(
  val width: Float,
  val height: Float,
  val seekAreaPercent: Float = 30f,
  val reverse: Boolean = false,
  val instantCenter: Boolean = false,
  val instantSides: Boolean = false,
  val seekLeft: Boolean = true,
  val seekRight: Boolean = true,
  val doubleTapMs: Long = 300,
  val chainMs: Long = 650,
  val slop: Float = 100f,
  /**
   * When true, a single tap does not touch the chrome at UP. The toggle is queued and fires once
   * the double-tap window has closed, so a double-tap seek never shows the chrome speculatively.
   */
  val deferToggle: Boolean = false,
)

/**
 * Decides what a tap does to the player chrome and which action it runs.
 *
 * The engine is default-deny: [down] only classifies the touch and never touches the chrome. The
 * host decides whether the gesture ended as a clean tap and passes that to [up]. That single flag
 * replaces per-gesture `cancel()` calls, so a subtitle drag, a hold, a volume slide or a second
 * finger cannot reach the chrome by accident.
 *
 * Rules:
 *  - A clean single tap toggles the chrome at UP, so it feels instant while never flashing during
 *    a gesture that turned out not to be a tap. With [TapConfig.deferToggle] the toggle is instead
 *    queued until the double-tap window closes.
 *  - A double tap ends with the chrome exactly as it was before the first tap.
 *  - With an instant single-tap action enabled, a tap outside the top/bottom quarter runs the action
 *    and never touches the chrome.
 *  - An unclean gesture produces no chrome effect, writes no snapshot and starts no double-tap
 *    window: the next touch is still a first tap.
 *
 * Time is passed in explicitly, so a test drives the clock with its own arguments. Hosts must use
 * the same monotonic clock for every call, and call [tick] with the env overload while a toggle may
 * be pending (see [nextDeadline]).
 */
internal class TapChromeEngine(
  private val listener: TapChromeListener,
  private val config: () -> TapConfig,
) {
  private enum class Kind { First, SecondOfDouble, Continuation, Swallowed, Locked }

  // Sequence state, survives between gestures.
  private var count = 0
  private var lastTime = 0L
  private var lastUp = 0L
  private var lastX = 0f
  private var lastY = 0f
  private var lastRegion: TapRegion? = null
  private var chainSeeking = false
  private var shownBefore = false
  private var committed = false

  // Only used when config().deferToggle is true.
  private var pending = false
  private var pendingDeadline = 0L
  private var togglable = false

  // Latched at DOWN, consumed at UP.
  private var kind = Kind.First
  private var region = TapRegion.Center
  private var downTime = 0L
  private var downX = 0f
  private var downY = 0f
  private var spent = false
  private var live = false

  /** Test hook: must be exactly one per gesture sequence. */
  internal var snapshotWrites = 0
    private set

  /**
   * Test hook: true while a gesture is between [down] and [up].
   *
   * Everything the engine exposes is driven by explicit times and flags, but one piece of state is
   * invisible to a scripted test: a still finger emits no pointer events, so the host's clean-flag
   * loop never runs and the only thing that catches the press is the [up] deadline. A test can use
   * this to assert the host did not end the gesture early.
   */
  internal val gestureOpen: Boolean
    get() = live

  /**
   * When a deferred toggle is waiting, the monotonic time at which it becomes due. The host sleeps
   * until then; `null` means park until the next touch.
   */
  fun nextDeadline(): Long? = if (pending) pendingDeadline else null

  /**
   * Expires the double-tap window. Safe to call at any time; replaces the old cleanup effect.
   *
   * This overload cannot fire a deferred toggle, because it has no env to read the live chrome
   * state from. Any host driving [TapConfig.deferToggle] must call [tick] with an env instead.
   */
  fun tick(now: Long) {
    if (count == 1 && now - lastUp >= config().doubleTapMs) {
      count = 0
      lastRegion = null
      committed = false
    }
  }

  /** [tick] for hosts: fires a due deferred toggle before expiring the window. */
  fun tick(now: Long, env: TapEnv) {
    flush(now, env, force = false)
    tick(now)
  }

  /**
   * Classifies the touch. This has no chrome effect at all: the commit happens at [up], once the
   * host knows the gesture was a clean tap.
   *
   * [env] is used only to flush a toggle still pending from an *earlier* tap. Any DOWN other than
   * the one that can still make that tap a double proves it was a single, so the pending toggle is
   * honoured before this gesture starts. The current gesture never reads [env] here.
   */
  fun down(time: Long, x: Float, y: Float, env: TapEnv) {
    val c = config()
    tick(time)
    region = regionOf(x, c)
    downTime = time
    downX = x
    downY = y
    live = true
    val zone = c.height * 0.25f
    val inZone = y < zone || y > c.height - zone
    spent = hasInstant(region, c) && !inZone
    val near = lastRegion == region && hypot(x - lastX, y - lastY) < c.slop
    val closesWindow = count == 1 && near && time - lastTime < c.doubleTapMs
    kind = when {
      env.locked -> Kind.Locked
      closesWindow -> Kind.SecondOfDouble
      count >= 2 && chainSeeking && near && time - lastTime < c.chainMs -> Kind.Continuation
      // Including swallowed taps: they never become singles either, so they too must not flush.
      count >= 2 && time - lastTime < c.doubleTapMs -> Kind.Swallowed
      else -> Kind.First
    }
    // A pending deferred toggle is only cancelled by the DOWN that can still make it a double tap.
    // Any other DOWN proves the pending tap was a single tap, so honour it before this one starts.
    if (pending) {
      if (closesWindow) {
        pending = false
      } else {
        flush(time, env, force = true)
      }
      togglable = false
    }
    when (kind) {
      Kind.SecondOfDouble -> restore()
      Kind.First -> {
        // Reset sequence state for a fresh tap, but touch nothing visible: the commit is at UP.
        committed = false
        chainSeeking = false
        togglable = false
      }
      else -> Unit
    }
  }

  /**
   * Commits the gesture. [cleanTap] is the host's verdict: the touch was still a single pointer,
   * never became a drag/pinch/long press, and was released within the long-press deadline.
   *
   * A non-clean gesture is default-denied: no chrome effect, no snapshot, and no double-tap window,
   * so the next touch is classified from scratch. Every Kind goes through this one gate.
   */
  fun up(time: Long, env: TapEnv, cleanTap: Boolean) {
    if (!live) return
    live = false
    if (!cleanTap) {
      // Drop a queued toggle belonging to this gesture. A toggle queued by an *earlier* gesture is
      // left alone: it is settled by the next DOWN in down().
      if (kind == Kind.First) {
        restore()
        pending = false
        togglable = false
      }
      return
    }
    val c = config()
    // A tap that turns out to be part of a double already has its chrome settled at DOWN: the
    // second tap restores the snapshot and extra taps are swallowed. Committing a deferred toggle
    // for either would fire after the gesture as a spurious flash, so drop it here.
    if (kind == Kind.SecondOfDouble || kind == Kind.Swallowed) {
      pending = false
      togglable = false
    }
    when (kind) {
      Kind.Locked -> if (spent) listener.onShowChrome()
      Kind.Swallowed -> Unit
      Kind.SecondOfDouble, Kind.Continuation -> {
        if (kind == Kind.SecondOfDouble) {
          count = 2
          chainSeeking = isSeek(region, c)
        } else {
          count++
        }
        lastTime = downTime
        lastX = downX
        lastY = downY
        listener.onAction(region, isSingleTap = false)
      }
      Kind.First -> {
        count = 1
        lastTime = downTime
        lastUp = time
        lastX = downX
        lastY = downY
        lastRegion = region
        if (spent) {
          listener.onAction(region, isSingleTap = true)
        } else if (!env.panelOrSheetOpen) {
          // The chrome commit lives here, not at DOWN: the env is read fresh at lift, so a panel
          // that opened during the gesture is honoured.
          if (c.deferToggle) {
            togglable = true
            // The window closes at DOWN time, not UP time: that is the exact moment a second DOWN
            // can no longer still be a double tap, so the wait is as short as the rule allows.
            pending = true
            pendingDeadline = downTime + c.doubleTapMs
          } else {
            shownBefore = env.chromeShown
            snapshotWrites++
            committed = true
            if (env.chromeShown) listener.onHideChrome() else listener.onShowChrome()
          }
        }
      }
    }
  }

  private fun restore() {
    if (!committed) return
    if (shownBefore) listener.onShowChrome() else listener.onHideChrome()
    committed = false
  }

  /**
   * Performs a queued deferred toggle if it is due. The chrome state is read fresh from [env] at
   * fire time, because anything may have opened or closed between the tap and the deadline.
   */
  private fun flush(now: Long, env: TapEnv, force: Boolean) {
    if (!pending || (!force && now < pendingDeadline)) return
    pending = false
    togglable = false
    if (env.locked || env.panelOrSheetOpen) return
    // Late commit, so a DOWN that races this deadline as a double tap still restores correctly.
    shownBefore = env.chromeShown
    snapshotWrites++
    committed = true
    if (env.chromeShown) listener.onHideChrome() else listener.onShowChrome()
  }

  private fun regionOf(x: Float, c: TapConfig): TapRegion {
    val f = c.seekAreaPercent / 100f
    val raw = when {
      x > c.width * (1 - f) -> TapRegion.Right
      x < c.width * f -> TapRegion.Left
      else -> TapRegion.Center
    }
    return if (!c.reverse) {
      raw
    } else {
      when (raw) {
        TapRegion.Left -> TapRegion.Right
        TapRegion.Right -> TapRegion.Left
        else -> raw
      }
    }
  }

  private fun hasInstant(r: TapRegion, c: TapConfig) =
    if (r == TapRegion.Center) c.instantCenter else c.instantSides

  private fun isSeek(r: TapRegion, c: TapConfig) =
    when (r) {
      TapRegion.Left -> c.seekLeft
      TapRegion.Right -> c.seekRight
      else -> false
    }
}
