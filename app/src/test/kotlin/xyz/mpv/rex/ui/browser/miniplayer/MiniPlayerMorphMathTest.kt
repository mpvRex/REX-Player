package xyz.mpv.rex.ui.browser.miniplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the drag thresholds of the docked mini player. The composable itself is not exercised here:
 * everything that decides *where the sheet goes* is a pure function on purpose.
 */
class MiniPlayerMorphMathTest {
  private val barHeight = 200f

  @Test
  fun draggingDownFromTheDockPullsTheBarAway() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0f,
        dragY = 50f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = true,
      )

    assertEquals(0f, frame.fraction, 0f)
    assertEquals(50f, frame.pullPx, 0.01f)
  }

  @Test
  fun pullingPastTheLimitStopsAtTheLimit() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0f,
        dragY = 10_000f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = true,
      )

    assertEquals(barHeight * MiniPlayerMorphMath.PULL_LIMIT_BAR_HEIGHTS, frame.pullPx, 0.01f)
  }

  @Test
  fun draggingUpFromTheDockDoesNothing() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0f,
        dragY = -80f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = true,
      )

    assertEquals(0f, frame.fraction, 0f)
    assertEquals(0f, frame.pullPx, 0f)
  }

  /**
   * Regression: a cancelled settle used to leave a non-zero extension behind, so the *next* pull
   * saw "already open, dragging up" and the bar could never be dismissed again. The gesture now
   * owns the pull, so a fresh docked gesture pulls even if the surface was left mid-flight.
   */
  @Test
  fun AFreshDockedGestureCanPullAgain() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0f,
        dragY = 90f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = true,
      )

    assertEquals(90f, frame.pullPx, 0.01f)
    assertTrue(
      MiniPlayerMorphMath.shouldDismissFromPull(
        pullPx = frame.pullPx,
        barHeightPx = barHeight,
        downVelocityDp = 0f,
      ),
    )
  }

  /** Once a pull is in progress, the gesture stays in the pull branch for every further delta. */
  @Test
  fun APullInProgressIgnoresTheFraction() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0.4f,
        dragY = 40f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = true,
      )

    assertEquals(0f, frame.fraction, 0f)
    assertEquals(40f, frame.pullPx, 0.01f)
  }

  /** Not a docked gesture and at rest: nothing moves (e.g. a drag that started on a closing sheet). */
  @Test
  fun ADragFromRestThatDidNotStartDockedDoesNothing() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0f,
        dragY = 60f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = false,
      )

    assertEquals(0f, frame.fraction, 0f)
    assertEquals(0f, frame.pullPx, 0f)
  }

  @Test
  fun swipeProgressIsClampedAndLinear() {
    assertEquals(0f, MiniPlayerMorphMath.progressBetween(0f, 0f, 160f), 0f)
    assertEquals(0.5f, MiniPlayerMorphMath.progressBetween(80f, 0f, 160f), 0.001f)
    assertEquals(1f, MiniPlayerMorphMath.progressBetween(400f, 0f, 160f), 0f)
    // A zero-width range must not divide by zero.
    assertEquals(0f, MiniPlayerMorphMath.progressBetween(0f, 100f, 100f), 0f)
    assertEquals(1f, MiniPlayerMorphMath.progressBetween(100f, 100f, 100f), 0f)
  }

  @Test
  fun draggingDownWhileOpenGrowsTheFraction() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0.5f,
        dragY = 200f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = false,
      )

    assertEquals(0.6f, frame.fraction, 0.0001f)
    assertEquals(0f, frame.pullPx, 0f)
  }

  @Test
  fun draggingUpWhileOpenClampsAtTheDock() {
    val frame =
      MiniPlayerMorphMath.dragFrame(
        currentFraction = 0.2f,
        dragY = -2000f,
        travelPx = 2000f,
        barHeightPx = barHeight,
        startedDocked = false,
      )

    assertEquals(0f, frame.fraction, 0f)
    assertEquals(0f, frame.pullPx, 0f)
  }

  @Test
  fun aDeliberateDragSettlesByPositionAndAThrowByDirection() {
    assertTrue(MiniPlayerMorphMath.resolveTarget(fraction = 0.9f, velocityFractionPerSecond = 0f))
    assertFalse(MiniPlayerMorphMath.resolveTarget(fraction = 0.1f, velocityFractionPerSecond = 0f))

    // Flicked towards the page even though it had barely started.
    assertTrue(MiniPlayerMorphMath.resolveTarget(fraction = 0.05f, velocityFractionPerSecond = 0.9f))
    // Flicked back down even though it was nearly open.
    assertFalse(MiniPlayerMorphMath.resolveTarget(fraction = 0.95f, velocityFractionPerSecond = -0.9f))
  }

  @Test
  fun aShortSlowPullSpringsBackAndALongOrFastOneCloses() {
    assertFalse(
      MiniPlayerMorphMath.shouldDismissFromPull(pullPx = 20f, barHeightPx = barHeight, downVelocityDp = 0f),
    )
    assertTrue(
      MiniPlayerMorphMath.shouldDismissFromPull(pullPx = 90f, barHeightPx = barHeight, downVelocityDp = 0f),
    )
    assertTrue(
      MiniPlayerMorphMath.shouldDismissFromPull(
        pullPx = 10f,
        barHeightPx = barHeight,
        downVelocityDp = MiniPlayerMorphMath.DISMISS_FLING_DP_PER_SECOND + 1f,
      ),
    )
  }

  @Test
  fun pageContentFadesInOnlyAfterTheSurfaceHasGrown() {
    // At rest the bar is fully visible and the page is fully hidden.
    assertEquals(1f, MiniPlayerMorphMath.barContentAlpha(0f), 0f)
    assertEquals(0f, MiniPlayerMorphMath.pageContentAlpha(0f), 0f)

    // At the hand-over point the bar is gone and the page has not appeared yet: the windows must
    // not overlap, or the page bleeds through the growing bar.
    assertEquals(0f, MiniPlayerMorphMath.barContentAlpha(MiniPlayerMorphMath.PAGE_FADE_START), 0f)
    assertEquals(0f, MiniPlayerMorphMath.pageContentAlpha(MiniPlayerMorphMath.PAGE_FADE_START), 0f)

    // Halfway the surface is still mostly a blank sheet travelling up.
    assertTrue(MiniPlayerMorphMath.pageContentAlpha(0.5f) < 0.2f)

    assertEquals(1f, MiniPlayerMorphMath.pageContentAlpha(1f), 0f)
    assertEquals(0f, MiniPlayerMorphMath.barContentAlpha(1f), 0f)
  }

  @Test
  fun smoothStepClampsAndEases() {
    assertEquals(0f, MiniPlayerMorphMath.smoothStep(0f, 1f, -1f), 0f)
    assertEquals(1f, MiniPlayerMorphMath.smoothStep(0f, 1f, 2f), 0f)
    assertEquals(0.5f, MiniPlayerMorphMath.smoothStep(0f, 1f, 0.5f), 0.0001f)
  }
}
