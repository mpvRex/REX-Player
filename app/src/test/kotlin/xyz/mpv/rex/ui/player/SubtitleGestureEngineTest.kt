package xyz.mpv.rex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.controls.SubtitleGestureEngine
import xyz.mpv.rex.ui.player.controls.SubtitleGestureListener
import xyz.mpv.rex.ui.player.controls.TouchPoint

class SubtitleGestureEngineTest {

  private class Recorder : SubtitleGestureListener {
    var grabbed = 0
    var dragStarted = 0
    var pinchStarted = 0
    var ended = 0
    var endMoved = false
    var endScaled = false
    var endPosition = Float.NaN
    var endScale = Float.NaN
    val positions = mutableListOf<Float>()
    val scales = mutableListOf<Float>()

    override fun onGrabbed() { grabbed++ }
    override fun onDragStarted() { dragStarted++ }
    override fun onPosition(positionPercent: Float) { positions += positionPercent }
    override fun onPinchStarted() { pinchStarted++ }
    override fun onScale(scale: Float) { scales += scale }
    override fun onEnded(position: Float, scale: Float, moved: Boolean, scaled: Boolean) {
      ended++
      endPosition = position
      endScale = scale
      endMoved = moved
      endScaled = scaled
    }
  }

  private val height = 1000f
  private val slop = 8f

  private fun engine(
    rec: Recorder,
    drag: Boolean = true,
    pinch: Boolean = true,
    position: Float = 90f,
    scale: Float = 1f,
    viewportHeight: Float = height,
  ) = SubtitleGestureEngine(rec, drag, pinch, viewportHeight, position, scale, slop, slop * 1.5f)

  private fun p(id: Long, x: Float, y: Float) = TouchPoint(id, x, y)

  // 1 ------------------------------------------------------------------------------------------

  @Test
  fun tapOnSubtitleGrabsAndReleasesWithoutMovingAnything() {
    val rec = Recorder()
    val e = engine(rec)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 502f, 803f))) // inside the slop
    e.update(emptyList())

    assertEquals(1, rec.grabbed)
    assertEquals(0, rec.dragStarted)
    assertTrue(rec.positions.isEmpty())
    assertEquals(1, rec.ended)
    assertFalse(rec.endMoved)
    assertFalse(rec.endScaled)
  }

  // 2 ------------------------------------------------------------------------------------------

  @Test
  fun oneFingerDragFollowsTheFingerOneToOneWithoutAJumpAtTheSlop() {
    val rec = Recorder()
    val e = engine(rec, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 830f))) // crosses slop: starts, no position yet
    assertEquals(1, rec.dragStarted)
    assertTrue("no jump when the slop is crossed", rec.positions.isEmpty())

    e.update(listOf(p(1, 500f, 880f))) // +50px of 1000 = +5%
    assertEquals(95f, rec.positions.last(), 0.001f)
    e.update(listOf(p(1, 500f, 780f))) // -50px from the re-anchored 830 -> 80
    assertEquals(85f, rec.positions.last(), 0.001f)
  }

  @Test
  fun positionIsClampedToMpvRange() {
    val rec = Recorder()
    val e = engine(rec, position = 140f)
    e.begin(listOf(p(1, 500f, 100f)))
    e.update(listOf(p(1, 500f, 130f)))
    e.update(listOf(p(1, 500f, 900f)))
    assertEquals(150f, rec.positions.last(), 0.001f)
    e.update(listOf(p(1, 500f, -5000f)))
    assertEquals(0f, rec.positions.last(), 0.001f)
  }

  // 3 ------------------------------------------------------------------------------------------

  @Test
  fun pinchScalesByTheRatioOfFingerDistancesStartingFromTheCurrentScale() {
    val rec = Recorder()
    val e = engine(rec, scale = 1.5f)
    e.begin(listOf(p(1, 400f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f))) // distance 200: base
    e.update(listOf(p(1, 380f, 800f), p(2, 620f, 800f))) // 240: crosses slop, no jump
    assertEquals(1, rec.pinchStarted)
    assertTrue(rec.scales.isEmpty())

    e.update(listOf(p(1, 340f, 800f), p(2, 660f, 800f))) // 320 vs re-based 240 => x1.333
    assertEquals(1.5f * 320f / 240f, rec.scales.last(), 0.001f)
  }

  @Test
  fun scaleIsClamped() {
    val rec = Recorder()
    val e = engine(rec, scale = 1f)
    e.begin(listOf(p(1, 450f, 800f)))
    e.update(listOf(p(1, 450f, 800f), p(2, 550f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f)))
    e.update(listOf(p(1, 0f, 800f), p(2, 1000f, 800f)))
    assertEquals(5f, rec.scales.last(), 0.001f)
    e.update(listOf(p(1, 499f, 800f), p(2, 501f, 800f)))
    assertEquals(0.3f, rec.scales.last(), 0.001f)
  }

  // 4 : the exact sequences from the bug reports --------------------------------------------

  @Test
  fun holdThenAddFingerThenLiftFirstFingerThenRemainingFingerDragsWithoutJump() {
    val rec = Recorder()
    val e = engine(rec, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f), p(2, 200f, 300f)))
    e.update(listOf(p(2, 200f, 300f))) // finger 1 lifted; finger 2 is far away
    assertTrue("hand-off must not move the subtitle", rec.positions.isEmpty())

    e.update(listOf(p(2, 200f, 330f))) // crosses slop
    assertEquals(1, rec.dragStarted)
    e.update(listOf(p(2, 200f, 380f))) // +50px => +5%
    assertEquals(95f, rec.positions.last(), 0.001f)
  }

  @Test
  fun holdThenAddFingerThenLiftSecondFingerThenFirstFingerDragsEvenAfterBothMoved() {
    val rec = Recorder()
    val e = engine(rec, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f), p(2, 200f, 300f)))
    // Both fingers wander during the pinch phase; finger 1 ends 300px away from where it began.
    e.update(listOf(p(1, 520f, 500f), p(2, 220f, 100f)))
    e.update(listOf(p(1, 520f, 500f))) // finger 2 lifted
    assertTrue("same finger remaining must not make the subtitle jump", rec.positions.isEmpty())
    assertEquals(0, rec.dragStarted)

    e.update(listOf(p(1, 520f, 530f)))
    e.update(listOf(p(1, 520f, 580f)))
    assertEquals(95f, rec.positions.last(), 0.001f)
  }

  @Test
  fun dragThenPinchThenDragKeepsPositionAndContinuesImmediately() {
    val rec = Recorder()
    val e = engine(rec, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 830f)))
    e.update(listOf(p(1, 500f, 880f))) // position 95
    val afterDrag = rec.positions.last()
    assertEquals(95f, afterDrag, 0.001f)

    e.update(listOf(p(1, 500f, 880f), p(2, 300f, 400f))) // second finger
    e.update(listOf(p(1, 450f, 700f), p(2, 300f, 300f))) // pinching: fingers move a lot
    assertEquals("pinching must not move the subtitle", afterDrag, e.position, 0.0001f)

    e.update(listOf(p(1, 450f, 700f))) // finger 2 lifted, finger 1 remains elsewhere
    assertEquals(afterDrag, e.position, 0.0001f)
    e.update(listOf(p(1, 450f, 650f))) // 50px up from the new anchor => -5%
    assertEquals(90f, rec.positions.last(), 0.001f)
    assertEquals("drag survives the hand-off with no new slop", 1, rec.dragStarted)
  }

  @Test
  fun dragThenAddFingerThenPinchWorks() {
    val rec = Recorder()
    val e = engine(rec, scale = 1f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 840f))) // drag started
    e.update(listOf(p(1, 500f, 840f), p(2, 700f, 840f))) // base 200
    e.update(listOf(p(1, 480f, 840f), p(2, 720f, 840f))) // 240 starts the pinch
    e.update(listOf(p(1, 440f, 840f), p(2, 760f, 840f))) // 320
    assertEquals(1, rec.pinchStarted)
    assertEquals(320f / 240f, rec.scales.last(), 0.001f)
  }

  @Test
  fun pinchThenLiftThenAddFingerAgainPinchesAgainFromTheCurrentScale() {
    val rec = Recorder()
    val e = engine(rec, scale = 1f)
    e.begin(listOf(p(1, 450f, 800f)))
    e.update(listOf(p(1, 450f, 800f), p(2, 550f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f))) // starts (base 200)
    e.update(listOf(p(1, 350f, 800f), p(2, 650f, 800f))) // 300 => x1.5
    val first = rec.scales.last()
    assertEquals(1.5f, first, 0.001f)

    e.update(listOf(p(1, 350f, 800f))) // lift
    e.update(listOf(p(1, 350f, 800f), p(3, 450f, 800f))) // re-add: base 100, scale 1.5
    e.update(listOf(p(1, 300f, 800f), p(3, 500f, 800f))) // 200 starts, no jump
    assertEquals(first, e.scale, 0.0001f)
    e.update(listOf(p(1, 250f, 800f), p(3, 550f, 800f))) // 300 vs re-based 200 => x1.5
    assertEquals(1.5f * 1.5f, rec.scales.last(), 0.001f)
    assertEquals(2, rec.pinchStarted)
  }

  @Test
  fun handOffBeforeAnyDragStillAppliesTheSlopFromTheNewAnchor() {
    val rec = Recorder()
    val e = engine(rec)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f), p(2, 100f, 100f)))
    e.update(listOf(p(2, 100f, 100f)))
    e.update(listOf(p(2, 100f, 104f))) // inside slop of the NEW anchor
    assertEquals(0, rec.dragStarted)
    e.update(listOf(p(2, 100f, 120f)))
    assertEquals(1, rec.dragStarted)
  }

  // 5 ------------------------------------------------------------------------------------------

  @Test
  fun extraFingersAreIgnoredAndThePairStaysStable() {
    val rec = Recorder()
    val e = engine(rec, scale = 1f)
    e.begin(listOf(p(1, 450f, 800f)))
    e.update(listOf(p(1, 450f, 800f), p(2, 550f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f))) // starts
    e.update(listOf(p(1, 350f, 800f), p(2, 650f, 800f))) // x1.5
    val s = rec.scales.last()

    // A third finger lands far away: the (1,2) pair keeps driving, no jump.
    e.update(listOf(p(1, 350f, 800f), p(2, 650f, 800f), p(3, 50f, 50f)))
    assertEquals(s, e.scale, 0.0001f)
    e.update(listOf(p(1, 300f, 800f), p(2, 700f, 800f), p(3, 40f, 40f)))
    assertEquals(1.5f * 400f / 300f, rec.scales.last(), 0.001f)
  }

  // 6 ------------------------------------------------------------------------------------------

  @Test
  fun whenDraggingIsDisabledPinchStillWorksAndSingleFingerNeverMoves() {
    val rec = Recorder()
    val e = engine(rec, drag = false)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 300f)))
    assertTrue(rec.positions.isEmpty())
    assertEquals(0, rec.dragStarted)

    e.update(listOf(p(1, 500f, 300f), p(2, 700f, 300f)))
    e.update(listOf(p(1, 480f, 300f), p(2, 720f, 300f)))
    e.update(listOf(p(1, 440f, 300f), p(2, 760f, 300f)))
    assertEquals(1, rec.pinchStarted)
    assertFalse(rec.scales.isEmpty())
  }

  @Test
  fun whenPinchIsDisabledTwoFingersDoNothingAndTheRemainingFingerCanStillDrag() {
    val rec = Recorder()
    val e = engine(rec, pinch = false, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f), p(2, 700f, 800f)))
    e.update(listOf(p(1, 300f, 800f), p(2, 900f, 800f)))
    assertTrue(rec.scales.isEmpty())
    assertEquals(0, rec.pinchStarted)

    e.update(listOf(p(2, 900f, 800f)))
    e.update(listOf(p(2, 900f, 840f)))
    e.update(listOf(p(2, 900f, 890f)))
    assertEquals(95f, rec.positions.last(), 0.001f)
  }

  // 7 ------------------------------------------------------------------------------------------

  @Test
  fun endIsReportedExactlyOnceWithTheFinalValues() {
    val rec = Recorder()
    val e = engine(rec, position = 90f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 830f)))
    e.update(listOf(p(1, 500f, 880f)))
    e.update(emptyList())
    e.update(emptyList())
    e.finish()
    e.update(listOf(p(1, 500f, 100f))) // ignored after the end

    assertEquals(1, rec.ended)
    assertTrue(rec.endMoved)
    assertFalse(rec.endScaled)
    assertEquals(95f, rec.endPosition, 0.001f)
    assertEquals(1, rec.grabbed)
  }

  @Test
  fun finishWithoutAnyUpdateStillReleasesTheGrab() {
    val rec = Recorder()
    val e = engine(rec)
    e.begin(listOf(p(1, 500f, 800f)))
    e.finish()
    assertEquals(1, rec.ended)
  }

  @Test
  fun zeroHeightViewportNeverProducesNaNOrMovement() {
    val rec = Recorder()
    val e = engine(rec, viewportHeight = 0f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 900f)))
    e.update(listOf(p(1, 500f, 950f)))
    assertTrue(rec.positions.isEmpty())
    assertFalse(e.position.isNaN())
  }

  @Test
  fun fingersStartingVeryCloseDoNotExplodeTheScale() {
    val rec = Recorder()
    val e = engine(rec, scale = 1f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f), p(2, 502f, 800f))) // 2px apart
    e.update(listOf(p(1, 495f, 800f), p(2, 505f, 800f))) // 10px: still inside the 12px slop
    assertEquals(0, rec.pinchStarted)
    e.update(listOf(p(1, 490f, 800f), p(2, 510f, 800f))) // 20px: starts, base re-anchored to 20
    assertEquals(1, rec.pinchStarted)
    assertTrue(rec.scales.isEmpty())
    e.update(listOf(p(1, 480f, 800f), p(2, 520f, 800f))) // 40px => x2
    assertEquals(2f, rec.scales.last(), 0.001f)
  }

  // Drag ceiling ------------------------------------------------------------------------------

  @Test
  fun dragStopsAtTheSuppliedPositionCeiling() {
    val rec = Recorder()
    val e = SubtitleGestureEngine(rec, true, true, height, 90f, 1f, slop, slop * 1.5f, positionMax = 105f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f + slop + 1f))) // crosses slop
    e.update(listOf(p(1, 500f, 1800f))) // far below the ceiling
    assertEquals(105f, rec.positions.last(), 0.001f)
    assertTrue(rec.positions.all { it <= 105f })
  }

  @Test
  fun overshootingTheCeilingDoesNotShiftTheDragAnchor() {
    val rec = Recorder()
    val e = SubtitleGestureEngine(rec, true, true, height, 100f, 1f, slop, slop * 1.5f, positionMax = 105f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f + slop + 1f))) // slop crossed, anchor re-based at y=809, pos=100
    e.update(listOf(p(1, 500f, 1200f))) // overshoot, clamped at 105
    e.update(listOf(p(1, 500f, 809f + 25f))) // 25px of 1000px = 2.5%
    assertEquals(102.5f, rec.positions.last(), 0.001f)
    e.finish()
  }

  @Test
  fun defaultCeilingIsUnchanged() {
    val rec = Recorder()
    val e = engine(rec)
    assertEquals(150f, SubtitleGestureEngine.POSITION_MAX, 0.001f)
    e.finish()
  }

  // Scale-aware ceiling -----------------------------------------------------------------------

  /** Toy ceiling: 100 + 10 * scale, so a smaller subtitle has a lower ceiling. */
  private val ceilingForScale: (Float) -> Float = { sc -> 100f + 10f * sc }

  private fun scaledCeilingEngine(rec: Recorder, position: Float, scale: Float = 1f) =
    SubtitleGestureEngine(
      rec, true, true, height, position, scale, slop, slop * 1.5f,
      positionMaxForScale = ceilingForScale,
    )

  @Test
  fun pinchThenDragUsesTheCeilingOfTheNewScale() {
    val rec = Recorder()
    val e = scaledCeilingEngine(rec, position = 100f) // ceiling at scale 1.0 is 110
    e.begin(listOf(p(1, 400f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f))) // distance 200: base
    e.update(listOf(p(1, 420f, 800f), p(2, 580f, 800f))) // 160: crosses slop, no jump
    e.update(listOf(p(1, 450f, 800f), p(2, 550f, 800f))) // 100 vs re-based 160 => scale 0.625
    assertEquals(0.625f, e.scale, 0.001f)

    e.update(listOf(p(2, 550f, 800f))) // lift finger 1; finger 2 re-anchors
    e.update(listOf(p(2, 550f, 800f + slop + 1f)))
    e.update(listOf(p(2, 550f, 1900f))) // drag far down
    val expectedCeiling = 100f + 10f * 0.625f
    assertEquals(expectedCeiling, rec.positions.last(), 0.001f)
    assertTrue(rec.positions.all { it <= expectedCeiling + 0.001f })
    // The old, scale-1.0 ceiling (110) must NOT be reachable any more.
    assertTrue(rec.positions.none { it > expectedCeiling + 0.001f })
  }

  @Test
  fun shrinkingPastTheCeilingPullsTheSubtitleBackAndReportsIt() {
    val rec = Recorder()
    val e = scaledCeilingEngine(rec, position = 110f) // exactly at the scale-1.0 ceiling
    e.begin(listOf(p(1, 400f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f)))
    e.update(listOf(p(1, 420f, 800f), p(2, 580f, 800f)))
    e.update(listOf(p(1, 450f, 800f), p(2, 550f, 800f))) // scale 0.625 => ceiling 106.25
    assertEquals(106.25f, e.position, 0.001f)
    assertEquals(106.25f, rec.positions.last(), 0.001f)
    e.finish()
  }

  @Test
  fun growingTheSubtitleRaisesTheCeilingAgain() {
    val rec = Recorder()
    val e = scaledCeilingEngine(rec, position = 100f)
    e.begin(listOf(p(1, 400f, 800f)))
    e.update(listOf(p(1, 400f, 800f), p(2, 600f, 800f)))
    e.update(listOf(p(1, 380f, 800f), p(2, 620f, 800f)))
    e.update(listOf(p(1, 300f, 800f), p(2, 700f, 800f))) // 400 vs 240 => scale ~1.667
    e.update(listOf(p(2, 700f, 800f)))
    e.update(listOf(p(2, 700f, 800f + slop + 1f)))
    e.update(listOf(p(2, 700f, 1900f)))
    assertEquals(100f + 10f * e.scale, rec.positions.last(), 0.001f)
    assertTrue(rec.positions.last() > 110f)
  }

  @Test
  fun startingAboveTheCeilingNeverJumpsAndKeepsItsOvershoot() {
    val rec = Recorder()
    val e = scaledCeilingEngine(rec, position = 115f) // 5 above the scale-1.0 ceiling of 110
    e.begin(listOf(p(1, 500f, 800f)))
    assertEquals(115f, e.position, 0.001f)
    e.update(listOf(p(1, 500f, 800f + slop + 1f))) // slop crossed
    e.update(listOf(p(1, 500f, 1900f)))
    assertEquals(115f, rec.positions.lastOrNull() ?: 115f, 0.001f)
    assertTrue(rec.positions.none { it > 115f + 0.001f })
    e.finish()
  }

  @Test
  fun fixedPositionMaxStillWorksWhenNoScaleFunctionIsGiven() {
    val rec = Recorder()
    val e = SubtitleGestureEngine(rec, true, true, height, 90f, 1f, slop, slop * 1.5f, positionMax = 105f)
    e.begin(listOf(p(1, 500f, 800f)))
    e.update(listOf(p(1, 500f, 800f + slop + 1f)))
    e.update(listOf(p(1, 500f, 1900f)))
    assertEquals(105f, rec.positions.last(), 0.001f)
  }

  @Test
  fun overshootIsGoneOnceTheSubtitleIsDraggedBackInside() {
    val rec = Recorder()
    val e = scaledCeilingEngine(rec, position = 115f) // ceiling at scale 1.0 is 110
    e.begin(listOf(p(1, 500f, 1000f)))
    e.update(listOf(p(1, 500f, 1000f - slop - 1f))) // slop crossed
    e.update(listOf(p(1, 500f, 400f))) // far up: well inside the range
    assertTrue(e.position < 110f)
    e.update(listOf(p(1, 500f, 1900f))) // hard down again
    assertTrue("position=${e.position}", e.position <= 110.001f)
    assertTrue(rec.positions.none { it > 110.001f && it != 115f })
    e.finish()
  }
}
