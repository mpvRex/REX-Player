package xyz.mpv.rex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.controls.calculateSubtitleScale
import xyz.mpv.rex.ui.player.controls.estimateBitmapSubtitleTouchRect
import xyz.mpv.rex.ui.player.controls.estimateSubtitleTouchRect
import xyz.mpv.rex.ui.player.controls.maxGrabbableBitmapSubtitlePosition
import xyz.mpv.rex.ui.player.controls.maxGrabbableSubtitlePosition
import xyz.mpv.rex.ui.player.controls.maxPositionInsideFrame
import xyz.mpv.rex.ui.player.controls.mpvScaledPixelFactor
import xyz.mpv.rex.ui.player.controls.subtitleTouchContains

class SubtitleGestureUtilsTest {

  @Test
  fun longPrimarySubtitleStaysWithinSubtitleLikeBounds() {
    val rect = estimateSubtitleTouchRect(
      text = "This is a deliberately long subtitle line that should provide a broad touch target",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1080f,
      height = 1920f,
      baseFontSize = 55,
    )

    assertTrue(rect.left > 0f)
    assertTrue(rect.right < 1080f)
    // libass wraps at the frame width minus the side margins and fills the lines evenly, so the
    // box must stay inside that wrap width instead of spanning the whole screen.
    assertTrue(rect.right - rect.left < 1042f)
    assertTrue(rect.bottom > rect.top)
  }

  @Test
  fun multilineSecondarySubtitleExtendsDownwardFromAnchor() {
    val rect = estimateSubtitleTouchRect(
      text = "First line\nSecond line",
      positionPercent = 10,
      scale = 1f,
      isSecondary = true,
      width = 1080f,
      height = 1920f,
      baseFontSize = 55,
    )

    assertTrue(rect.top < 220f)
    assertTrue(rect.bottom > rect.top + 100f)
  }

  @Test
  fun bitmapFallbackKeepsExistingCenterGestureArea() {
    val rect = estimateBitmapSubtitleTouchRect(
      positionPercent = 100,
      isSecondary = false,
      width = 1080f,
      height = 1920f,
    )

    assertEquals(259.2f, rect.left, 0.0001f)
    assertEquals(820.8f, rect.right, 0.0001f)
    assertTrue(rect.bottom > rect.top)
  }


  @Test
  fun shortSubtitleKeepsHitBoxCompact() {
    val rect = estimateSubtitleTouchRect(
      text = "Hello",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1080f,
      height = 1920f,
      baseFontSize = 55,
    )

    assertTrue(rect.right - rect.left < 180f)
    assertTrue(rect.bottom - rect.top < 100f)
    assertFalse(rect.contains(100f, (rect.top + rect.bottom) / 2f))
  }

  @Test
  fun subtitleMarginKeepsPrimaryHitBoxAboveTheBottomEdge() {
    val rect = estimateSubtitleTouchRect(
      text = "Hello world\nSecond line",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1600f,
      height = 720f,
      baseFontSize = 55,
      verticalMargin = 34f,
    )

    assertTrue(rect.bottom < 710f)
    assertTrue(rect.top < rect.bottom)
  }

  @Test
  fun realisticSubtitleRejectsWideSideAndTopAreas() {
    val rect = estimateSubtitleTouchRect(
      text = "أنها مجموعة مكالمات لأرقام\nلا يمكن تعقبها",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1600f,
      height = 720f,
      baseFontSize = 55,
    )

    assertTrue(rect.left > 300f)
    assertTrue(rect.right < 1300f)
    assertFalse(rect.contains(180f, 620f))
    assertFalse(rect.contains(1420f, 620f))
    assertFalse(rect.contains(800f, 350f))
    assertTrue(rect.contains(800f, (rect.top + rect.bottom) / 2f))
  }

  @Test
  fun singleFingerTouchOnlyAcceptsSubtitleOrVerySmallNearbySlop() {
    val rect = estimateSubtitleTouchRect(
      text = "Hello world",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1600f,
      height = 720f,
      baseFontSize = 55,
    )

    assertFalse(rect.contains(80f, 360f))
    assertFalse(rect.contains(1520f, 360f))
    assertFalse(rect.contains(800f, 250f))
    assertTrue(rect.contains((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f))
  }

  @Test
  fun primarySubtitleHitBoxIsTighterAboveWithoutChangingSideBounds() {
    val rect = estimateSubtitleTouchRect(
      text = "أنها مجموعة مكالمات لأرقام\nلا يمكن تعقبها",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1600f,
      height = 720f,
      baseFontSize = 55,
    )

    // The subtitle remains comfortably targetable in its own block.
    assertTrue(rect.contains((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f))
    // The top edge is deliberately pulled down to reject more of the empty area above the text.
    // For these fixed inputs, the previous untrimmed edge was about 535.7 px.
    assertTrue(rect.top > 545f)
    // Keep the horizontal hit width unchanged while tightening only the upper edge.
    assertEquals(499.3f, rect.left, 0.2f)
    assertEquals(1100.7f, rect.right, 0.2f)
  }

  @Test
  fun pinchRequiresMidpointInsideAndKeepsFingersClose() {

    val rect = estimateSubtitleTouchRect(
      text = "Hello world",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1080f,
      height = 1920f,
      baseFontSize = 55,
    )

    val midY = (rect.top + rect.bottom) / 2f
    val midX = (rect.left + rect.right) / 2f
    assertTrue(subtitleTouchContains(rect, listOf(midX - 20f to midY, midX + 20f to midY)))
    assertFalse(subtitleTouchContains(rect, listOf(40f to midY, 80f to midY)))
  }

  @Test
  fun assOverrideTagsDoNotCrashTouchRectCalculation() {
    val rect = estimateSubtitleTouchRect(
      text = "{\\an8}Hello {\\fs60}world",
      positionPercent = 100,
      scale = 1f,
      isSecondary = false,
      width = 1080f,
      height = 1920f,
      baseFontSize = 55,
    )

    assertTrue(rect.right > rect.left)
    assertTrue(rect.bottom > rect.top)
  }

  @Test
  fun subtitleScaleTracksPinchDistance() {
    assertEquals(1.5f, calculateSubtitleScale(1f, 200f, 300f), 0.0001f)
    assertEquals(0.5f, calculateSubtitleScale(1f, 200f, 100f), 0.0001f)
  }

  @Test
  fun invalidPinchDistanceKeepsOriginalScale() {
    assertEquals(1.25f, calculateSubtitleScale(1.25f, 0f, 200f), 0.0001f)
    assertEquals(1.25f, calculateSubtitleScale(1.25f, 200f, 0f), 0.0001f)
  }
  @Test
  fun primarySubtitleAtExtremeTopStillHasUsableHitBox() {
    val rect = estimateSubtitleTouchRect(
      text = "Top subtitle",
      positionPercent = 0,
      scale = 1f,
      isSecondary = false,
      width = 1600f,
      height = 720f,
      baseFontSize = 55,
    )

    assertTrue(rect.bottom > rect.top)
    assertTrue(rect.top >= 0f)
    assertTrue(rect.bottom <= 720f)
  }


  @Test
  fun extremeTopPositionDoesNotLoseThePrimarySubtitleHitTarget() {
    listOf(0, 1, 5, 10, 20).forEach { position ->
      val rect = estimateSubtitleTouchRect(
        text = "A subtitle near the top",
        positionPercent = position,
        scale = 1f,
        isSecondary = false,
        width = 1600f,
        height = 720f,
        baseFontSize = 55,
      )

      assertTrue("position=$position", rect.bottom > rect.top)
      assertTrue("position=$position", rect.top >= 0f)
      assertTrue("position=$position", rect.bottom <= 720f)
    }
  }

  @Test
  fun primaryTopTrimIsStableAcrossSubtitleScaling() {
    fun expectedBlockHeight(scale: Float): Float {
      val fontPx = 55f * scale
      val lineHeight = fontPx // libass: ascent + descent == font size
      val horizontalPadding = fontPx * 0.18f
      val verticalPadding = fontPx * 0.10f
      val approximateGlyphWidth = maxOf(1f, fontPx * 0.40f)
      val usableWidth = maxOf(1600f * 0.5f, 1600f - 2f * 19f * scale)
      val maxCharsPerLine = maxOf(1, ((usableWidth - 2f * horizontalPadding) / approximateGlyphWidth).toInt())
      val length = "Subtitle scale".trim().length
      val lineCount = maxOf(1, kotlin.math.ceil(length.toDouble() / maxCharsPerLine).toInt())
      return lineCount * lineHeight + 2f * verticalPadding
    }

    listOf(1f, 2f).forEach { scale ->
      val rect = estimateSubtitleTouchRect(
        text = "Subtitle scale",
        positionPercent = 100,
        scale = scale,
        isSecondary = false,
        width = 1600f,
        height = 720f,
        baseFontSize = 55,
      )

      val verticalSlop = 4f
      val geometricTop = rect.top + verticalSlop
      val geometricBottom = rect.bottom - verticalSlop
      val untrimmedTop = geometricBottom - expectedBlockHeight(scale)
      assertEquals(11f, geometricTop - untrimmedTop, 0.001f)
    }
  }

  // --- Drag ceiling: a subtitle must never be dragged to where it cannot be grabbed again -------

  private fun grabbableText(position: Float, isSecondary: Boolean, width: Float, height: Float, text: String = "Hello subtitle") =
    estimateSubtitleTouchRect(
      text = text,
      positionPercent = Math.round(position),
      scale = 1f,
      isSecondary = isSecondary,
      width = width,
      height = height,
      baseFontSize = 55,
    )

  @Test
  fun hitBoxIsEmptyOnceTheSubtitleIsFullyOffScreen() {
    // Documents the original bug: nothing left on screen means nothing to grab.
    val rect = grabbableText(150f, isSecondary = false, width = 2160f, height = 1080f)
    assertFalse(rect.bottom > rect.top)
  }

  @Test
  fun primaryPositionCeilingAlwaysLeavesAGrabbableStrip() {
    listOf(360f, 720f, 1080f, 1920f).forEach { height ->
      listOf("Hi", "Hello subtitle", "line one\nline two\nline three").forEach { text ->
        val width = height * 2f
        val cap = maxGrabbableSubtitlePosition(text, 1f, false, width, height, 55)
        val rect = grabbableText(cap, isSecondary = false, width = width, height = height, text = text)
        assertTrue("h=$height text=$text cap=$cap", rect.bottom - rect.top >= 24f)
        assertTrue("h=$height cap=$cap", cap <= 150f)
      }
    }
  }

  @Test
  fun secondaryPositionCeilingAlwaysLeavesAGrabbableStrip() {
    listOf(360f, 720f, 1080f, 1920f).forEach { height ->
      val width = height * 2f
      val cap = maxGrabbableSubtitlePosition("Hello subtitle", 1f, true, width, height, 55)
      val rect = grabbableText(cap, isSecondary = true, width = width, height = height)
      assertTrue("h=$height cap=$cap", rect.bottom - rect.top >= 24f)
    }
  }

  @Test
  fun primaryCeilingStillAllowsPositionsAboveOneHundredWhenPartlyVisible() {
    // The ceiling only removes positions where nothing is on screen; it does not forbid >100.
    val cap = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55)
    assertTrue("cap=$cap", cap > 100f)
    assertTrue("cap=$cap", cap < 150f)
  }

  @Test
  fun largerSubtitleScaleRaisesThePrimaryCeiling() {
    val small = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55)
    val big = maxGrabbableSubtitlePosition("Hello subtitle", 2f, false, 2160f, 1080f, 55)
    assertTrue(big > small)
  }

  @Test
  fun positionOneMoreStepPastTheCeilingEventuallyLosesTheHitBox() {
    val cap = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55)
    val rect = grabbableText(cap + 10f, isSecondary = false, width = 2160f, height = 1080f)
    assertTrue(rect.bottom - rect.top < 24f)
  }

  @Test
  fun bitmapSubtitleCeilingAlwaysLeavesAGrabbableStrip() {
    listOf(360f, 1080f, 1920f).forEach { height ->
      listOf(false, true).forEach { secondary ->
        val cap = maxGrabbableBitmapSubtitlePosition(secondary, height * 2f, height)
        val rect = estimateBitmapSubtitleTouchRect(Math.round(cap), secondary, height * 2f, height)
        assertTrue("h=$height secondary=$secondary cap=$cap", rect.bottom - rect.top >= 24f)
      }
    }
  }

  @Test
  fun invalidViewportKeepsTheDefaultCeiling() {
    assertEquals(150f, maxGrabbableSubtitlePosition("x", 1f, false, 0f, 0f, 55), 0.001f)
    assertEquals(150f, maxGrabbableBitmapSubtitlePosition(false, 0f, 0f), 0.001f)
  }

  // --- mpv "scaled pixels": sub-font-size / sub-margin-y are sized for a 720px-tall window -------

  @Test
  fun scaledPixelFactorFollowsWindowHeightWhenScaleByWindowIsOn() {
    assertEquals(1f, mpvScaledPixelFactor(true, 720f), 0.0001f)
    assertEquals(1.5f, mpvScaledPixelFactor(true, 1080f), 0.0001f)
    assertEquals(0.5f, mpvScaledPixelFactor(true, 360f), 0.0001f)
  }

  @Test
  fun scaledPixelFactorIsOneWhenScaleByWindowIsOff() {
    // sub-scale-by-window=no (with sub-scale-with-window left at its default yes) keeps the
    // size constant: the value is used as is, whatever the window or video height.
    assertEquals(1f, mpvScaledPixelFactor(false, 1080f), 0.0001f)
    assertEquals(1f, mpvScaledPixelFactor(false, 360f), 0.0001f)
  }

  @Test
  fun scaledPixelFactorNeverReturnsZeroOrNaN() {
    assertEquals(1f, mpvScaledPixelFactor(true, 0f), 0.0001f)
    assertEquals(1f, mpvScaledPixelFactor(false, 0f), 0.0001f)
  }

  @Test
  fun fontSizeFiftyFiveIsEightyTwoPointFivePixelsOnA1080pWindow() {
    val line = "Hello subtitle"
    val atFactorOne = estimateSubtitleTouchRect(line, 100, 1f, false, 2160f, 1080f, 55, unitScale = 1f)
    val at1080 = estimateSubtitleTouchRect(line, 100, 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    // Same text, 1.5x the font => 1.5x as wide (slop is only a few px, hence the tolerance).
    val w1 = atFactorOne.right - atFactorOne.left
    val w15 = at1080.right - at1080.left
    assertEquals(1.5f, w15 / w1, 0.05f)
    assertTrue(at1080.bottom - at1080.top > atFactorOne.bottom - atFactorOne.top)
  }

  @Test
  fun hitBoxIsSmallerOnAShortWindowAndLargerOnATallOne() {
    val line = "Hello subtitle"
    fun widthAt(height: Float): Float {
      val r = estimateSubtitleTouchRect(line, 100, 1f, false, height * 2f, height, 55, unitScale = height / 720f)
      return r.right - r.left
    }
    assertTrue(widthAt(360f) < widthAt(720f))
    assertTrue(widthAt(720f) < widthAt(1080f))
  }

  @Test
  fun marginYIsConvertedToScreenPixelsToo() {
    val line = "Hello subtitle"
    val atFactorOne = estimateSubtitleTouchRect(line, 100, 1f, false, 2160f, 1080f, 55, verticalMargin = 22f, unitScale = 1f)
    val atFactorTwo = estimateSubtitleTouchRect(line, 100, 1f, false, 2160f, 1080f, 55, verticalMargin = 22f, unitScale = 2f)
    // Font size doubles the block too, so compare the gap between the box bottom and the screen
    // bottom: it must equal the converted margin (22 * factor) minus the fixed vertical padding.
    val gap1 = 1080f - atFactorOne.bottom
    val gap2 = 1080f - atFactorTwo.bottom
    assertTrue("gap1=$gap1 gap2=$gap2", gap2 > gap1)
  }

  @Test
  fun marginYScalesWithSubtitleScale() {
    val line = "Hello subtitle"
    fun gap(scale: Float): Float {
      val r = estimateSubtitleTouchRect(line, 100, scale, false, 2160f, 1080f, 55, verticalMargin = 34f, unitScale = 1f)
      // bottom = height - margin + verticalPadding + verticalSlop (4px at this height)
      return 1080f - r.bottom + 4f
    }
    val at1 = gap(1f)
    val at2 = gap(2f)
    // margin and the padding both double with the font scale
    assertEquals(2f, at2 / at1, 0.01f)
  }

  @Test
  fun ceilingUsesTheConvertedUnitsToo() {
    val at1 = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55, unitScale = 1f)
    val at15 = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    assertTrue("at1=$at1 at15=$at15", at15 > at1)
    // And the strip rule still holds with realistic units.
    val rect = estimateSubtitleTouchRect("Hello subtitle", Math.floor(at15.toDouble()).toInt(), 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    assertTrue(rect.bottom - rect.top >= 24f)
  }

  @Test
  fun shrinkingTheSubtitleLowersTheCeiling() {
    val big = maxGrabbableSubtitlePosition("Hello subtitle", 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    val small = maxGrabbableSubtitlePosition("Hello subtitle", 0.5f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    assertTrue("big=$big small=$small", small < big)
  }

  @Test
  fun boxHeightMatchesLibassLineHeight() {
    // One line = the font size (libass REAL_DIM) plus the outline allowance, not 1.28x.
    val rect = estimateSubtitleTouchRect("Hi", 60, 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    val fontPx = 55f * 1.5f
    val expected = fontPx + 2f * fontPx * 0.10f
    val height = rect.bottom - rect.top + 11f // undo the primary top trim
    assertEquals(expected + 2f * 4f, height, 0.5f)
  }

  @Test
  fun longSubtitleIsBalancedAcrossLinesNotGreedy() {
    val text = "This is a deliberately long subtitle line that should provide a broad touch target"
    val rect = estimateSubtitleTouchRect(text, 100, 1f, false, 1080f, 1920f, 55)
    // 82 chars over 2 balanced lines: widest line is about 41 chars, far less than the wrap width.
    assertTrue(rect.right - rect.left < 960f)
  }

  // --- Low positions (0..30): the box must sit where libass really draws the subtitle -----------

  @Test
  fun lowPositionsKeepTheHitBoxOnTheTopOfTheScreenInPortrait() {
    // Regression: at sub-pos 0..30 on a tall portrait window the box used to be placed far below
    // the real subtitle (middle of the screen), so tapping the subtitle did nothing while tapping
    // the middle of the screen grabbed it.
    val height = 1920f
    val unit = height / 720f
    listOf(0, 5, 10, 20, 30).forEach { position ->
      val rect = estimateSubtitleTouchRect(
        text = "A fairly long subtitle that wraps over several lines on a portrait window",
        positionPercent = position,
        scale = 1f,
        isSecondary = false,
        width = 1080f,
        height = height,
        baseFontSize = 55,
        unitScale = unit,
      )
      // libass: bottom = (height - margin) * pos / 100, top clamped to 0.
      val margin = 34f * unit
      val realBottom = (height - margin) * position / 100f
      val probeY = (maxOf(0f, realBottom - 40f)).coerceAtLeast(20f)
      assertTrue("pos=$position rect=$rect", rect.contains(540f, probeY))
      assertFalse("pos=$position rect=$rect", rect.contains(540f, height / 2f + 600f))
    }
  }

  @Test
  fun subtitleAtPositionZeroStartsAtTheTopEdge() {
    val rect = estimateSubtitleTouchRect("Top subtitle", 0, 1f, false, 2160f, 1080f, 55, unitScale = 1.5f)
    // Real text block spans 0..blockHeight, so a tap on the first text line must hit.
    assertTrue(rect.contains(1080f, 40f))
  }

  @Test
  fun secondarySubtitleAtPositionZeroIsAtTheTopEdge() {
    val rect = estimateSubtitleTouchRect("Top secondary", 0, 1f, true, 2160f, 1080f, 55, unitScale = 1.5f)
    assertTrue(rect.contains(1080f, 40f))
  }

  @Test
  fun hitBoxFollowsLibassBottomFormulaWhenNotClamped() {
    val height = 1080f
    val unit = 1.5f
    val rect = estimateSubtitleTouchRect("Hi", 60, 1f, false, 2160f, height, 55, unitScale = unit)
    val margin = 34f * unit
    val textBottom = (height - margin) * 0.60f
    val padding = 55f * unit * 0.10f
    // bottom edge = textBottom + padding (+ vertical slop of 4px)
    assertEquals(textBottom + padding + 4f, rect.bottom, 0.5f)
  }

  // --- Frame bound: the whole subtitle must stay inside the frame --------------------------------

  @Test
  fun frameBoundAlwaysAllowsTheDefaultPosition() {
    listOf(720f to 1f, 1080f to 1.5f, 1920f to 2.667f, 2160f to 3f).forEach { (h, unit) ->
      listOf(0.5f, 1f, 1.72f, 3f).forEach { scale ->
        val max = maxPositionInsideFrame("Subtitle", scale, h * 1.78f, h, 55, unitScale = unit)
        assertTrue("h=$h scale=$scale max=$max", max >= 100f)
      }
    }
  }

  @Test
  fun bottomOfTheSubtitleStaysInsideTheFrameAtTheBound() {
    listOf(720f to 1f, 1080f to 1.5f, 1920f to 2.667f, 2160f to 3f).forEach { (h, unit) ->
      listOf(1f, 1.72f).forEach { scale ->
        val max = maxPositionInsideFrame("Subtitle", scale, h * 1.78f, h, 55, unitScale = unit)
        val margin = 34f * unit * scale
        val padding = 55f * unit * scale * 0.10f
        fun bottomAt(pos: Float) = (h - margin) * pos / 100f + padding
        assertTrue("h=$h scale=$scale", bottomAt(max) <= h + 0.01f)
        assertTrue("h=$h scale=$scale", bottomAt(max + 1f) > h)
      }
    }
  }

  @Test
  fun frameBoundDoesNotDependOnTheNumberOfLines() {
    val one = maxPositionInsideFrame("Hi", 1f, 1080f, 1920f, 55, unitScale = 2.667f)
    val many = maxPositionInsideFrame(
      "A very long subtitle that wraps over many lines on a narrow portrait window",
      1f, 1080f, 1920f, 55, unitScale = 2.667f,
    )
    assertEquals(one, many, 0.001f)
  }

  @Test
  fun frameBoundIsTighterThanTheGrabbableBoundForTallBlocks() {
    val text = "A very long subtitle that wraps over many lines on a narrow portrait window"
    val frame = maxPositionInsideFrame(text, 1f, 1080f, 1920f, 55, unitScale = 2.667f)
    val grab = maxGrabbableSubtitlePosition(text, 1f, false, 1080f, 1920f, 55, unitScale = 2.667f)
    assertTrue("frame=$frame grab=$grab", frame < grab)
  }
}
