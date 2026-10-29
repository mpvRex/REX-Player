package xyz.mpv.rex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.controls.TapChromeEngine
import xyz.mpv.rex.ui.player.controls.TapChromeListener
import xyz.mpv.rex.ui.player.controls.TapConfig
import xyz.mpv.rex.ui.player.controls.TapEnv
import xyz.mpv.rex.ui.player.controls.TapRegion

private fun cfg(
  instantCenter: Boolean = false,
  instantSides: Boolean = false,
  reverse: Boolean = false,
  seekLeft: Boolean = true,
  seekRight: Boolean = true,
) = TapConfig(
  width = 1000f,
  height = 800f,
  instantCenter = instantCenter,
  instantSides = instantSides,
  reverse = reverse,
  seekLeft = seekLeft,
  seekRight = seekRight,
)

private fun chrome(shown: Boolean) = if (shown) "show" else "hide"

/** Fake host: applies effects to a chrome flag and feeds that flag back at the next DOWN. */
private class Rig(start: Boolean, private val c: TapConfig = cfg()) : TapChromeListener {
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

  fun down(t: Long, x: Float = 500f, y: Float = 400f) =
    engine.down(t, x, y, TapEnv(shown, locked, blocked))

  fun up(t: Long, cleanTap: Boolean = true) =
    engine.up(t, TapEnv(shown, locked, blocked), cleanTap)

  /** An unclean gesture: the touch became a drag, a pinch or a long press before it lifted. */
  fun unclean(start: Long = 60, hold: Long = 0) = up(start + hold, cleanTap = false)

  fun tapped(instance: String) = log.count { it == instance }

  fun tap(t: Long, x: Float = 500f, y: Float = 400f, hold: Long = 60) {
    down(t, x, y)
    up(t + hold)
  }
}

class TapChromeEngineTest {

  // 1, 2 ---------------------------------------------------------------------------------------

  @Test
  fun singleTap_togglesOnLift_notOnDown() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.down(0)
      // Default-deny: landing the finger must not touch the chrome.
      assertEquals("start=$start", emptyList<String>(), r.log)
      assertEquals("start=$start", start, r.shown)
      r.up(60)
      assertEquals("start=$start", listOf(chrome(!start)), r.log)
      assertEquals("start=$start", !start, r.shown)
    }
  }

  // 3 ------------------------------------------------------------------------------------------

  @Test
  fun doubleTap_playPause_restoresPreGestureState() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.tap(0)
      r.tap(150)
      assertEquals("start=$start", start, r.shown)
      assertEquals(
        "start=$start",
        listOf(chrome(!start), chrome(start), "center:double"),
        r.log,
      )
    }
  }

  // 4 ------------------------------------------------------------------------------------------

  @Test
  fun doubleTap_seek_restoresPreGestureState() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.tap(0, x = 900f)
      r.tap(150, x = 900f)
      assertEquals("start=$start", start, r.shown)
      assertEquals(
        "start=$start",
        listOf(chrome(!start), chrome(start), "right:double"),
        r.log,
      )
    }
  }

  // 5, 6 ---------------------------------------------------------------------------------------

  @Test
  fun regression_secondDownMustNotResnapshot() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.tap(0)
      r.tap(150)
      assertEquals("start=$start", 1, r.engine.snapshotWrites)
      assertEquals("start=$start", start, r.shown) // the old double-toggle ended at !start
      r.tap(1000)
      r.tap(1150)
      assertEquals("start=$start", 2, r.engine.snapshotWrites)
      assertEquals("start=$start", start, r.shown)
    }
  }

  // 7 ------------------------------------------------------------------------------------------

  @Test
  fun seekChain_extraTaps_neverTouchChrome() {
    for (taps in 3..5) {
      val r = Rig(false)
      for (i in 0 until taps) r.tap(i * 150L, x = 900f)
      assertEquals("taps=$taps", 2, r.tapped("show") + r.tapped("hide"))
      assertEquals("taps=$taps", taps - 1, r.tapped("right:double"))
      assertFalse("taps=$taps", r.shown)
    }
  }

  // 8 ------------------------------------------------------------------------------------------

  @Test
  fun tripleTapCenter_thirdTapIsSwallowed() {
    val r = Rig(false)
    r.tap(0)
    r.tap(150)
    r.tap(300)
    assertEquals(listOf("show", "hide", "center:double"), r.log)
    assertFalse(r.shown)
  }

  // 9, 10, 11 ---------------------------------------------------------------------------------

  @Test
  fun instantSingleTap_middle_actionOnly() {
    for (start in listOf(false, true)) {
      val r = Rig(start, cfg(instantCenter = true))
      r.tap(0)
      assertEquals("start=$start", listOf("center:single"), r.log)
      assertEquals("start=$start", start, r.shown)
    }
  }

  @Test
  fun instantSingleTap_inExclusionZone_togglesWithoutAction() {
    for (start in listOf(false, true)) {
      val r = Rig(start, cfg(instantCenter = true))
      r.tap(0, y = 100f)
      assertEquals("start=$start", listOf(chrome(!start)), r.log)
      assertEquals("start=$start", !start, r.shown)
    }
  }

  @Test
  fun instantSingleTap_sides_behaveLikeCenter() {
    val c = cfg(instantSides = true)
    Rig(false, c).let { r ->
      r.tap(0, x = 900f)
      assertEquals(listOf("right:single"), r.log)
    }
    Rig(false, c).let { r ->
      r.tap(0, x = 100f)
      assertEquals(listOf("left:single"), r.log)
    }
    Rig(false, c).let { r ->
      r.tap(0, x = 900f, y = 100f)
      assertEquals(listOf("show"), r.log)
    }
  }

  // 12 -----------------------------------------------------------------------------------------

  @Test
  fun slowSecondTap_stillDouble_whenCleanupFiresMidPress() {
    val r = Rig(false)
    r.down(0)
    r.up(60)
    r.down(280)
    r.engine.tick(361) // the old cleanup effect zeroed tapCount here
    r.up(400)
    assertEquals(listOf("show", "hide", "center:double"), r.log)
    assertFalse(r.shown)
  }

  // 13 -----------------------------------------------------------------------------------------

  @Test
  fun regionChange_isTwoSingleTaps() {
    val r = Rig(false)
    r.tap(0, x = 900f)
    r.tap(150, x = 100f)
    assertEquals(2, r.engine.snapshotWrites)
    assertEquals(0, r.tapped("right:double") + r.tapped("left:double"))
    assertEquals(0, r.tapped("right:single") + r.tapped("left:single"))
    assertEquals(false, r.shown) // two toggles cancel
  }

  // 14 -----------------------------------------------------------------------------------------

  @Test
  fun abandonedChain_tapElsewhere_isAFreshSingleTap() {
    val r = Rig(false)
    r.tap(0, x = 900f)
    r.tap(150, x = 900f)
    // chainSeeking is true until a First tap resets it, so a different region must start over.
    r.tap(500, x = 100f)
    assertEquals(listOf("show", "hide", "right:double", "show"), r.log)
    assertTrue(r.shown)
    assertEquals(2, r.engine.snapshotWrites)
  }

  // 15 -----------------------------------------------------------------------------------------

  @Test
  fun lockedOrPanelOpen_commitsNothing() {
    val r = Rig(false)
    r.locked = true
    r.tap(0)
    r.locked = false
    r.blocked = true
    r.tap(1000)
    assertTrue(r.log.isEmpty())
  }

  @Test
  fun locked_withInstantAction_stillRevealsChrome() {
    val r = Rig(false, cfg(instantCenter = true))
    r.locked = true
    r.tap(0)
    assertEquals(listOf("show"), r.log)
  }

  // 16, 17 -------------------------------------------------------------------------------------

  @Test
  fun secondTapTurnsIntoDrag_endsAtPreGestureState() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.tap(0)
      r.down(150)
      r.unclean()
      assertEquals("start=$start", start, r.shown)
      assertEquals("start=$start", 0, r.tapped("center:double"))
      // The first tap committed at its lift; DOWN2 then restored it. The unclean lift adds nothing.
      assertEquals("start=$start", listOf(chrome(!start), chrome(start)), r.log)
    }
  }

  @Test
  fun firstTapTurnsIntoDrag_leavesTheChromeUntouched() {
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.down(0)
      r.unclean()
      // Nothing was ever committed, so there is nothing to roll back: no effect at all.
      assertEquals("start=$start", emptyList<String>(), r.log)
      assertEquals("start=$start", start, r.shown)
      assertEquals("start=$start", 0, r.engine.snapshotWrites)
    }
  }

  // 18 -----------------------------------------------------------------------------------------

  @Test
  fun doubleTapBoundary_isExclusiveAt300ms() {
    // 299 ms counts as a double tap.
    Rig(false).let { r ->
      r.tap(0)
      r.tap(299)
      assertEquals(listOf("show", "hide", "center:double"), r.log)
    }
    // 300 ms does not: two independent single taps.
    Rig(false).let { r ->
      r.tap(0)
      r.tap(300)
      assertEquals(2, r.engine.snapshotWrites)
      assertFalse(r.log.contains("center:double"))
    }
    // 301 ms likewise.
    Rig(false).let { r ->
      r.tap(0)
      r.tap(301)
      assertEquals(2, r.engine.snapshotWrites)
      assertFalse(r.log.contains("center:double"))
    }
  }

  @Test
  fun slopBoundary_isExclusiveAt100px() {
    // 99 px away still counts as the same spot.
    Rig(false).let { r ->
      r.tap(0, x = 500f)
      r.tap(150, x = 599f)
      assertEquals(listOf("show", "hide", "center:double"), r.log)
    }
    // 100 px away is a different spot.
    Rig(false).let { r ->
      r.tap(0, x = 500f)
      r.tap(150, x = 600f)
      assertEquals(2, r.engine.snapshotWrites)
      assertFalse(r.log.contains("center:double"))
    }
  }

  // 19 -----------------------------------------------------------------------------------------

  @Test
  fun reverse_swapsSides() {
    val r = Rig(false, cfg(reverse = true))
    r.tap(0, x = 900f)
    r.tap(150, x = 900f)
    // x=900 is physically right, but reverse makes it the "left" action.
    assertEquals(listOf("show", "hide", "left:double"), r.log)
    assertFalse(r.shown)
  }

  // 20 -----------------------------------------------------------------------------------------

  @Test
  fun longPress_togglesNothing_evenAfterTheDeadline() {
    // A hold emits no pointer events at all, so the host's loop never sees a move: the only thing
    // that can classify this as "not a tap" is the duration check at lift. Model that directly.
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.down(0)
      assertTrue("start=$start", r.engine.gestureOpen) // a still finger keeps the gesture open
      assertEquals("start=$start", emptyList<String>(), r.log) // nothing shown while held
      r.unclean(hold = 500) // lift well after the long-press deadline
      assertFalse("start=$start", r.engine.gestureOpen)
      assertEquals("start=$start", emptyList<String>(), r.log)
      assertEquals("start=$start", start, r.shown)
      assertEquals("start=$start", 0, r.engine.snapshotWrites)
    }
  }

  @Test
  fun tapJustUnderTheLongPressDeadline_stillCounts() {
    // 499 ms is a tap; 500 ms is a hold. The host compares against the same constant.
    val r = Rig(false)
    r.down(0)
    r.up(499)
    assertEquals(listOf("show"), r.log)
  }

  @Test
  fun uncleanGesture_isSilentForEveryKind() {
    // Every classification goes through the same cleanTap gate, so a drag/pinch/long press can
    // never reach the chrome no matter which tap of a sequence it interrupted.
    data class Case(val name: String, val downAt: Long, val setup: (Rig) -> Unit)

    val cases = listOf(
      // First: a lone touch that never lifts as a tap.
      Case("first", 1000L) { r -> r.tap(0) },
      // SecondOfDouble: the second touch of a double turns into a drag.
      Case("secondOfDouble", 150L) { r -> r.tap(0) },
      // Continuation: an extra tap in a seek chain turns into a drag.
      Case("continuation", 300L) { r ->
        r.tap(0, x = 900f)
        r.tap(150, x = 900f)
      },
      // Swallowed: a triple-tap tail turns into a drag.
      Case("swallowed", 300L) { r ->
        r.tap(0)
        r.tap(150)
      },
    )

    for (case in cases) {
      val r = Rig(false, cfg(instantCenter = true))
      case.setup(r)
      val before = r.log.toList()
      val writesBefore = r.engine.snapshotWrites
      r.down(case.downAt)
      r.unclean(case.downAt + 60)
      // Nothing after the setup may have changed: no effect, no snapshot, no action.
      assertEquals(case.name, before, r.log)
      assertEquals(case.name, writesBefore, r.engine.snapshotWrites)
    }
  }

  @Test
  fun uncleanFirstTap_startsNoWindow() {
    // An unclean gesture must not leave `count` at 1, or the next real tap would be treated as the
    // second of a double against a gesture that never happened.
    for (start in listOf(false, true)) {
      val r = Rig(start)
      r.down(0)
      r.unclean()
      r.tap(150)
      assertEquals("start=$start", listOf(chrome(!start)), r.log)
      assertEquals("start=$start", 1, r.engine.snapshotWrites)
      assertEquals("start=$start", 0, r.tapped("center:double"))
    }
  }

  @Test
  fun seekDisabled_doesNotStartAChain() {
    val r = Rig(false, cfg(seekRight = false))
    r.tap(0, x = 900f)
    r.tap(150, x = 900f)
    assertEquals(listOf("show", "hide", "right:double"), r.log)
    // Without seek on that side there is no chain, so tap 3 is swallowed as a triple-tap tail
    // rather than extending it: still exactly the two chrome effects from the first gesture.
    r.tap(300, x = 900f)
    assertEquals(2, r.tapped("show") + r.tapped("hide"))
  }
}
