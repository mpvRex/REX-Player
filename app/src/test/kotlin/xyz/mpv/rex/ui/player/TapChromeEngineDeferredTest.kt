package xyz.mpv.rex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.controls.TapChromeEngine
import xyz.mpv.rex.ui.player.controls.TapChromeListener
import xyz.mpv.rex.ui.player.controls.TapConfig
import xyz.mpv.rex.ui.player.controls.TapEnv
import xyz.mpv.rex.ui.player.controls.TapRegion

private const val WINDOW = 300L

private fun deferredCfg(
  instantCenter: Boolean = false,
  instantSides: Boolean = false,
  seekRight: Boolean = true,
  deferToggle: Boolean = true,
) = TapConfig(
  width = 1000f,
  height = 800f,
  instantCenter = instantCenter,
  instantSides = instantSides,
  seekRight = seekRight,
  doubleTapMs = WINDOW,
  deferToggle = deferToggle,
)

private fun chrome(shown: Boolean) = if (shown) "show" else "hide"

/** Fake host for [TapChromeEngine] whose ticks always carry a freshly read env. */
private class DeferredRig(start: Boolean, val c: TapConfig = deferredCfg()) : TapChromeListener {
  var shown = start
  var locked = false
  var blocked = false
  val log = mutableListOf<String>()
  val engine = TapChromeEngine(this) { c }

  override fun onShowChrome() {
    shown = true
    log += "show"
  }

  override fun onHideChrome() {
    shown = false
    log += "hide"
  }

  override fun onAction(region: TapRegion, isSingleTap: Boolean) {
    log += "${region.name.lowercase()}:${if (isSingleTap) "single" else "double"}"
  }

  private fun env() = TapEnv(shown, locked, blocked)

  fun down(t: Long, x: Float = 500f, y: Float = 400f) = engine.down(t, x, y, env())

  fun up(t: Long, cleanTap: Boolean = true) = engine.up(t, env(), cleanTap)

  /** An unclean gesture: the touch became a drag, a pinch or a long press before it lifted. */
  fun unclean(t: Long = 60) = up(t, cleanTap = false)

  /** Host loop: only wakes at the deadline the engine asked for. */
  fun tick(t: Long) = engine.tick(t, env())

  fun tickUntilIdle(t: Long) {
    var now = t
    while (true) {
      val d = engine.nextDeadline() ?: return
      now = maxOf(now, d)
      tick(now)
    }
  }

  fun requestsDeadline() = engine.nextDeadline()

  fun tap(t: Long, x: Float = 500f, y: Float = 400f, hold: Long = 60) {
    down(t, x, y)
    up(t + hold)
  }

  fun chromeEffects() = log.count { it == "show" || it == "hide" }
}

/**
 * Mode B: the preference that restores the pre-5.5 deferred chrome toggle.
 *
 * The invariant that matters here is stronger than in Mode A: a double tap must produce no chrome
 * effect at all, not merely end in the pre-gesture state. That is the reason the option exists.
 */
class TapChromeEngineDeferredTest {

  // Toggle timing -------------------------------------------------------------------------------

  @Test
  fun singleTap_togglesNothingOnDownOrUp() {
    for (start in listOf(false, true)) {
      val r = DeferredRig(start)
      r.down(0)
      assertEquals("start=$start", 0, r.log.size)
      r.up(60)
      assertEquals("start=$start", 0, r.log.size)
      assertEquals("start=$start", start, r.shown)
    }
  }

  @Test
  fun singleTap_togglesExactlyAtDeadline() {
    for (start in listOf(false, true)) {
      val r = DeferredRig(start)
      r.tap(0)
      assertEquals("deadline is DOWN + window", 300L, r.requestsDeadline())
      r.tick(299)
      assertEquals("start=$start", 0, r.log.size)
      r.tick(300)
      assertEquals("start=$start", listOf(chrome(!start)), r.log)
      assertEquals("start=$start", !start, r.shown)
      assertNull("start=$start", r.requestsDeadline())
    }
  }

  @Test
  fun singleTap_holdLengthDoesNotDelayTheToggle() {
    val r = DeferredRig(false)
    r.tap(0, hold = 250)
    assertEquals(WINDOW, r.requestsDeadline())
    r.tick(300)
    assertEquals(listOf("show"), r.log)
  }

  @Test
  fun chromeState_isReadFreshAtFireTime() {
    val r = DeferredRig(false)
    r.tap(0)
    // Something else showed the chrome during the window; the tap must now hide it.
    r.shown = true
    r.tick(300)
    assertEquals(listOf("hide"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun panelOpenedDuringWindow_dropsTheToggle() {
    val r = DeferredRig(false)
    r.tap(0)
    r.blocked = true
    r.tick(300)
    assertTrue(r.log.isEmpty())
    assertNull(r.requestsDeadline())
  }

  @Test
  fun controlsLockedDuringWindow_dropsTheToggle() {
    val r = DeferredRig(false)
    r.tap(0)
    r.locked = true
    r.tick(300)
    assertTrue(r.log.isEmpty())
  }

  // No flash ------------------------------------------------------------------------------------

  @Test
  fun doubleTap_playPause_neverTouchesChrome() {
    for (start in listOf(false, true)) {
      val r = DeferredRig(start)
      r.tap(0)
      r.tap(150)
      r.tickUntilIdle(1000)
      assertEquals("start=$start", listOf("center:double"), r.log)
      assertEquals("start=$start", start, r.shown)
    }
  }

  @Test
  fun doubleTap_seek_neverTouchesChrome() {
    for (start in listOf(false, true)) {
      val r = DeferredRig(start)
      r.tap(0, x = 900f)
      r.tap(150, x = 900f)
      r.tickUntilIdle(1000)
      assertEquals("start=$start", listOf("right:double"), r.log)
      assertEquals("start=$start", start, r.shown)
    }
  }

  @Test
  fun seekChain_isSilentFromStartToFinish() {
    val r = DeferredRig(false)
    for (i in 0 until 5) {
      r.tap(i * 150L, x = 900f)
      // Nothing may surface in the middle of a chain either.
      assertEquals("taps=${i + 1}", 0, r.chromeEffects())
    }
    r.tickUntilIdle(2000)
    assertEquals(0, r.chromeEffects())
    assertEquals(4, r.log.count { it == "right:double" })
  }

  // Window edge cases ---------------------------------------------------------------------------

  @Test
  fun secondDownJustBeforeDeadline_cancelsThePendingToggle() {
    val r = DeferredRig(false)
    r.tap(0)
    assertEquals(300L, r.requestsDeadline())
    r.down(299)
    assertNull(r.requestsDeadline())
    assertEquals(0, r.chromeEffects())
    r.up(359)
    r.tickUntilIdle(1000)
    assertEquals(listOf("center:double"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun secondDownAtDeadline_isANewTap_afterTheFirstToggles() {
    val r = DeferredRig(false)
    r.tap(0)
    r.tap(300)
    // The deadline passed, so the first tap was genuinely single, then the second one follows.
    assertEquals(listOf("show"), r.log)
    r.tickUntilIdle(1000)
    assertEquals(listOf("show", "hide"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun secondTapDifferentRegion_flushesThenTogglesAgain() {
    val r = DeferredRig(false)
    r.tap(0, x = 900f)
    r.tap(150, x = 100f)
    assertEquals("first tap is honoured immediately", listOf("show"), r.log)
    r.tickUntilIdle(1000)
    assertEquals(listOf("show", "hide"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun lateDoubleTap_afterToggleAlreadyFired_restores() {
    // The pathological race: the deadline tick runs first, then the second DOWN lands in time.
    val r = DeferredRig(false)
    r.tap(0)
    r.tick(300)
    assertEquals(listOf("show"), r.log)
    r.down(290) // dispatched late, but still inside the window
    r.up(350)
    assertEquals(listOf("show", "hide", "center:double"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun uncleanGesture_queuesNothingAndTogglesNothing() {
    val r = DeferredRig(false)
    r.down(0)
    r.unclean()
    // Stronger than the old rollback: an unclean gesture never even queues a toggle.
    assertNull(r.requestsDeadline())
    r.tickUntilIdle(1000)
    assertTrue(r.log.isEmpty())
    assertEquals(false, r.shown)
    assertEquals(0, r.engine.snapshotWrites)
  }

  @Test
  fun longPressOnSecondTap_flushesTheEarlierTapOnly() {
    // A press well after a tap is a fresh single tap, so it settles that tap's pending toggle;
    // the unclean lift then drops its own. Only one effect may survive.
    val r = DeferredRig(false)
    r.tap(1000)
    r.down(4000)
    r.unclean(4600)
    r.tickUntilIdle(5000)
    assertTrue("at most one toggle", r.chromeEffects() <= 1)
  }

  @Test
  fun longPressAfterAPendingSingleTap_isNotADoubleTap() {
    // A long press during the window is not a double tap, so the first tap is not swallowed: it is
    // honoured at the DOWN, and the cancelled long press contributes nothing of its own.
    val r = DeferredRig(false)
    r.tap(0)
    r.down(200)
    r.unclean(260)
    assertEquals(0, r.log.size) // never fires on DOWN; the engine has no timers
    r.tick(200)
    assertEquals(0, r.log.size) // neither an instant flush nor an early fire
    r.tickUntilIdle(5000)
    assertTrue("exactly one toggle, not two", r.chromeEffects() <= 1)
  }

  @Test
  fun thirdTapOnCentre_isStillSwallowed() {
    val r = DeferredRig(false)
    r.tap(0)
    r.tap(150)
    r.tap(300)
    r.tickUntilIdle(2000)
    assertEquals(listOf("center:double"), r.log)
    assertEquals(false, r.shown)
  }

  @Test
  fun toggleUnavailable_queuesNothing() {
    // Locked, or an instant action already covers the tap: no deadline may be requested.
    val locked = DeferredRig(false).also { it.locked = true }
    locked.tap(0)
    assertNull(locked.requestsDeadline())

    val instant = DeferredRig(false, deferredCfg(instantCenter = true))
    instant.tap(0)
    assertNull(instant.requestsDeadline())
    assertEquals(listOf("center:single"), instant.log)
  }

  @Test
  fun instantSingleTap_inZone_stillToggles_late() {
    val r = DeferredRig(false, deferredCfg(instantCenter = true))
    r.tap(0, y = 100f)
    assertEquals(0, r.log.size)
    r.tick(300)
    assertEquals(listOf("show"), r.log)
  }

  // Mode-independent invariants ----------------------------------------------------------------

  @Test
  fun finalChromeEqualsPreGestureState_forEveryActionKind() {
    for (defer in listOf(true, false)) {
      for (start in listOf(false, true)) {
        val c = deferredCfg(deferToggle = defer)
        DeferredRig(start, c).let { r ->
          r.tap(0, x = 900f)
          r.tap(150, x = 900f)
          r.tickUntilIdle(1000)
          assertEquals("defer=$defer start=$start", start, r.shown)
        }
        DeferredRig(start, c).let { r ->
          r.tap(0)
          r.tap(150)
          r.tickUntilIdle(1000)
          assertEquals("defer=$defer start=$start", start, r.shown)
        }
      }
    }
  }

  @Test
  fun chainExtraTapsAreSilent_inBothModes() {
    for (defer in listOf(true, false)) {
      val r = DeferredRig(false, deferredCfg(deferToggle = defer))
      for (i in 0 until 4) {
        r.tap(i * 150L, x = 900f)
        if (!defer) r.tickUntilIdle(i * 150L + 400)
      }
      r.tickUntilIdle(2000)
      // Instant mode shows then hides speculatively; deferred mode never shows at all.
      if (defer) assertEquals(0, r.chromeEffects()) else assertEquals(2, r.chromeEffects())
      assertEquals("defer=$defer", 3, r.log.count { it == "right:double" })
      assertEquals("defer=$defer", false, r.shown)
    }
  }

  @Test
  fun lockedAndPanelTapsAreSilent_inBothModes() {
    for (defer in listOf(true, false)) {
      val r = DeferredRig(false, deferredCfg(deferToggle = defer))
      r.locked = true
      r.tap(0)
      r.locked = false
      r.blocked = true
      r.tap(1000)
      r.tickUntilIdle(2000)
      assertTrue("defer=$defer", r.log.isEmpty())
    }
  }

  // Mode A guard --------------------------------------------------------------------------------

  /**
   * Replays one scripted sequence with the flag off, once through [TapChromeEngine.tick] and once
   * through [TapChromeEngine.tick] with an env, and compares both to a hard-coded log. Comparing
   * the overloads only to each other would still pass if Mode A itself drifted.
   */
  private data class Replay(val log: List<String>, val snapshots: Int)

  private fun replay(withEnv: Boolean, instantCenter: Boolean = false): Replay {
    val r = DeferredRig(false, deferredCfg(deferToggle = false, instantCenter = instantCenter))
    fun tick(t: Long) =
      if (withEnv) r.engine.tick(t, TapEnv(r.shown, r.locked, r.blocked)) else r.engine.tick(t)

    r.tap(0) // single tap
    listOf(299L, 300L, 359L, 360L).forEach { tick(it) }
    r.tap(1000, x = 900f)
    r.tap(1150, x = 900f) // double-tap seek
    r.tap(1300, x = 900f) // chain continuation
    tick(1359)
    tick(1650)
    r.tap(2000, x = 100f)
    r.tap(2100, x = 900f) // region change: two singles
    tick(2459)
    tick(2460)
    if (instantCenter) {
      r.tap(3000) // middle: action only
      r.tap(4000, y = 100f) // zone: toggle only
      tick(4400)
    }
    return Replay(r.log.toList(), r.engine.snapshotWrites)
  }

  @Test
  fun flagOff_bothTickOverloadsMatchGolden() {
    val golden = Replay(
      listOf("show", "hide", "show", "right:double", "right:double", "hide", "show"),
      snapshots = 4,
    )
    assertEquals(golden, replay(withEnv = false))
    assertEquals(golden, replay(withEnv = true))
  }

  @Test
  fun flagOff_instantActionsMatchGolden() {
    val expected = Replay(
      listOf(
        "center:single",
        "show", "hide", // first tap toggles, second tap (region change) toggles back
        "right:double", "right:double", // double-tap seek, then a silent continuation
        "show", // x=100 tap
        "hide", // x=900 tap: a region change, so another full toggle
        "center:single", // instant middle tap: action, no chrome
        "show", // zone tap toggles
      ),
      snapshots = 4,
    )
    assertEquals(expected, replay(withEnv = false, instantCenter = true))
    assertEquals(expected, replay(withEnv = true, instantCenter = true))
  }
}
