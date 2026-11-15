package xyz.mpv.rex.ui.browser.miniplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the floating mini player geometry against the real numbers the test device reports:
 * 720 px wide, 1488 px tall app area at 320 dpi (density = 2 px per dp).
 */
class MiniPlayerDockGeometryTest {
  private val density = 2f
  private val windowHeightPx = 1488f
  private val windowWidthPx = 720f

  /** 88 dp at 320 dpi. */
  private val barHeightPx = MiniPlayerDefaults.BarHeight.value * density

  /** 8 dp margins at 320 dpi. */
  private val horizontalMarginPx = MiniPlayerDefaults.FloatingHorizontalMargin.value * density
  private val bottomMarginPx = MiniPlayerDefaults.FloatingBottomMargin.value * density

  /** Bottom chrome: 64 dp nav bar + 48 dp system inset at 320 dpi. */
  private val bottomChromePx = (64f + 48f) * density

  @Test
  fun collapsedCardIsExactlyOneBarTall() {
    assertEquals(
      barHeightPx,
      floatingCardHeightPx(fraction = 0f, barHeightPx = barHeightPx, windowHeightPx = windowHeightPx),
      0.01f,
    )
  }

  @Test
  fun openCardIsFullWindowHeight() {
    assertEquals(
      windowHeightPx,
      floatingCardHeightPx(fraction = 1f, barHeightPx = barHeightPx, windowHeightPx = windowHeightPx),
      0.01f,
    )
  }

  @Test
  fun collapsedCardHasHorizontalMargins() {
    assertEquals(
      horizontalMarginPx,
      floatingCardStartPx(fraction = 0f, horizontalMarginPx = horizontalMarginPx),
      0.01f,
    )
    assertEquals(
      windowWidthPx - 2 * horizontalMarginPx,
      floatingCardWidthPx(fraction = 0f, horizontalMarginPx = horizontalMarginPx, windowWidthPx = windowWidthPx),
      0.01f,
    )
  }

  @Test
  fun openCardSpansFullWindowWidth() {
    assertEquals(
      0f,
      floatingCardStartPx(fraction = 1f, horizontalMarginPx = horizontalMarginPx),
      0.01f,
    )
    assertEquals(
      windowWidthPx,
      floatingCardWidthPx(fraction = 1f, horizontalMarginPx = horizontalMarginPx, windowWidthPx = windowWidthPx),
      0.01f,
    )
  }

  @Test
  fun collapsedCardFloatsAboveBottomChrome() {
    val top = floatingCardTopPx(
      fraction = 0f,
      extensionPx = 0f,
      barHeightPx = barHeightPx,
      bottomMarginPx = bottomMarginPx,
      bottomChromePx = bottomChromePx,
      windowHeightPx = windowHeightPx,
    )
    val bottom = top + barHeightPx
    val expectedBottom = windowHeightPx - bottomChromePx - bottomMarginPx

    // The bottom edge of the card sits exactly bottomMarginPx above the bottom chrome
    assertEquals(expectedBottom, bottom, 0.01f)
    // The top edge is barHeightPx above the card bottom, NOT at y = 0
    assertEquals(expectedBottom - barHeightPx, top, 0.01f)
  }

  @Test
  fun openCardSitsAtTopZero() {
    val top = floatingCardTopPx(
      fraction = 1f,
      extensionPx = 0f,
      barHeightPx = barHeightPx,
      bottomMarginPx = bottomMarginPx,
      bottomChromePx = bottomChromePx,
      windowHeightPx = windowHeightPx,
    )
    assertEquals(0f, top, 0.01f)
  }

  @Test
  fun pullMovesTheCardDownByExtension() {
    val resting = floatingCardTopPx(
      fraction = 0f,
      extensionPx = 0f,
      barHeightPx = barHeightPx,
      bottomMarginPx = bottomMarginPx,
      bottomChromePx = bottomChromePx,
      windowHeightPx = windowHeightPx,
    )
    val pulled = floatingCardTopPx(
      fraction = 0f,
      extensionPx = 60f,
      barHeightPx = barHeightPx,
      bottomMarginPx = bottomMarginPx,
      bottomChromePx = bottomChromePx,
      windowHeightPx = windowHeightPx,
    )
    assertEquals(60f, pulled - resting, 0.01f)
  }
}
