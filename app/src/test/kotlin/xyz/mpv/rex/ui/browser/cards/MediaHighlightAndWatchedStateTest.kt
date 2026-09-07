package xyz.mpv.rex.ui.browser.cards

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaHighlightAndWatchedStateTest {

  /**
   * Evaluates media card highlighting logic as implemented in BaseMediaCard.kt:
   * val shouldHighlight = isRecentlyPlayed && !isWatched
   */
  private fun computeShouldHighlight(isRecentlyPlayed: Boolean, isWatched: Boolean): Boolean {
    return isRecentlyPlayed && !isWatched
  }

  /**
   * Evaluates hasBeenWatched calculation logic as implemented in PlayerActivity.kt:
   * val progress = if (currentDuration > 0) currentPos.toFloat() / currentDuration.toFloat() else 0f
   * val isFinished = isEof || ((currentDuration > 0) && (currentPos >= currentDuration - 1))
   * val isCurrentlyWatched = progress >= (watchedThreshold / 100f)
   * isCurrentlyWatched || isFinished
   */
  private fun computeHasBeenWatched(
    currentPos: Int,
    currentDuration: Int,
    watchedThreshold: Int,
    isEof: Boolean = false,
  ): Boolean {
    val progress = if (currentDuration > 0) currentPos.toFloat() / currentDuration.toFloat() else 0f
    val isFinished = isEof || ((currentDuration > 0) && (currentPos >= currentDuration - 1))
    val isCurrentlyWatched = progress >= (watchedThreshold / 100f)
    return isCurrentlyWatched || isFinished
  }
  @Test
  fun `media card highlight evaluates to false when video is watched even if recently played`() {
    assertFalse(computeShouldHighlight(isRecentlyPlayed = true, isWatched = true))
    assertFalse(computeShouldHighlight(isRecentlyPlayed = false, isWatched = true))
  }

  @Test
  fun `media card highlight evaluates to true when video is recently played and not watched`() {
    assertTrue(computeShouldHighlight(isRecentlyPlayed = true, isWatched = false))
  }

  @Test
  fun `media card highlight evaluates to false when video is neither recently played nor watched`() {
    assertFalse(computeShouldHighlight(isRecentlyPlayed = false, isWatched = false))
  }

  @Test
  fun `rewatching finished video resets hasBeenWatched to false when progress below threshold`() {
    // 100s video, watched 20s (20% progress), threshold is 90%, isEof = false
    val hasBeenWatched = computeHasBeenWatched(
      currentPos = 20,
      currentDuration = 100,
      watchedThreshold = 90,
      isEof = false,
    )
    assertFalse(hasBeenWatched)
  }

  @Test
  fun `hasBeenWatched evaluates to false strictly below threshold`() {
    // 1000s video, watched 899s (89.9% progress), threshold is 90%
    val hasBeenWatched = computeHasBeenWatched(
      currentPos = 899,
      currentDuration = 1000,
      watchedThreshold = 90,
      isEof = false,
    )
    assertFalse(hasBeenWatched)
  }

  @Test
  fun `hasBeenWatched evaluates to true when progress meets or exceeds threshold`() {
    val hasBeenWatchedAtThreshold = computeHasBeenWatched(
      currentPos = 90,
      currentDuration = 100,
      watchedThreshold = 90,
      isEof = false,
    )
    assertTrue(hasBeenWatchedAtThreshold)

    val hasBeenWatchedAboveThreshold = computeHasBeenWatched(
      currentPos = 95,
      currentDuration = 100,
      watchedThreshold = 90,
      isEof = false,
    )
    assertTrue(hasBeenWatchedAboveThreshold)
  }

  @Test
  fun `hasBeenWatched evaluates to true when isEof is true even below threshold`() {
    val hasBeenWatched = computeHasBeenWatched(
      currentPos = 10,
      currentDuration = 100,
      watchedThreshold = 90,
      isEof = true,
    )
    assertTrue(hasBeenWatched)
  }

  @Test
  fun `hasBeenWatched evaluates to true when within 1 second of end even if threshold is 100 percent`() {
    // 100s video, pos 99 (within 1s of end), threshold 100%
    val hasBeenWatched = computeHasBeenWatched(
      currentPos = 99,
      currentDuration = 100,
      watchedThreshold = 100,
      isEof = false,
    )
    assertTrue(hasBeenWatched)
  }

  @Test
  fun `hasBeenWatched evaluates to false with zero or negative duration and not finished`() {
    assertFalse(
      computeHasBeenWatched(
        currentPos = 0,
        currentDuration = 0,
        watchedThreshold = 90,
        isEof = false,
      )
    )
    assertFalse(
      computeHasBeenWatched(
        currentPos = 0,
        currentDuration = -1,
        watchedThreshold = 90,
        isEof = false,
      )
    )
  }
}
