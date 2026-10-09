package xyz.mpv.rex.ui.player.controls

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val PRIMARY_TOP_TRIM_PX = 11f

/** Smallest on-screen strip of a subtitle that still counts as something the user can grab. */
private const val MIN_GRABBABLE_STRIP_PX = 24f
private const val ABSOLUTE_MAX_POSITION = 150f

/** mpv expresses subtitle font size and margins in "scaled pixels" of a window this tall. */
internal const val MPV_SCALED_PIXEL_REFERENCE_HEIGHT = 720f

/**
 * Screen pixels per mpv "scaled pixel".
 *
 * `sub-font-size` and `sub-margin-y` are defined at a window height of 720, so a value of 55 is
 * 55px only on a 720px-tall window: 82.5px at 1080, 27.5px at 360.
 *
 * mpv has two separate options: `sub-scale-by-window` (default yes) and `sub-scale-with-window`
 * (default yes). With by-window=yes the size follows the window height. With by-window=no and
 * with-window=yes (REX never touches with-window) the size no longer follows the window, so the
 * value is used as is (factor 1). The with-window=no / video-height mode is NOT modelled.
 *
 * Why plain height / 720 holds in REX's configuration (by-window and with-window both yes,
 * sub-use-margins yes), also with letterboxing: mpv's get_libass_scale_height() determines the
 * effective height libass uses to scale subtitle text (video area / 720). With with-window=yes
 * sd_ass compensates by window height / that height, so the resulting window-relative factor is
 * window height / 720. Example, 1080x1920 portrait with a 16:9 video: libass 608/720 times mpv's
 * 1920/608 = 1920/720.
 *
 * Not modelled: native ASS tracks (\pos, \an, \fs, ...) are positioned and sized by the track
 * itself, so for them the estimated hit box is only a rough guess.
 */
internal fun mpvScaledPixelFactor(
  scaleByWindow: Boolean,
  windowHeight: Float,
): Float =
  if (scaleByWindow && windowHeight > 0f) windowHeight / MPV_SCALED_PIXEL_REFERENCE_HEIGHT else 1f

internal data class SubtitleTouchRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  fun contains(x: Float, y: Float): Boolean =
    x in left..right && y in top..bottom

  fun expanded(horizontal: Float, vertical: Float): SubtitleTouchRect = SubtitleTouchRect(
    left = left - horizontal,
    top = top - vertical,
    right = right + horizontal,
    bottom = bottom + vertical,
  )
}

/**
 * Estimates the actual rendered subtitle block as closely as possible from the information mpv
 * exposes. mpv does not expose final screen-space glyph bounds, so this intentionally uses the
 * subtitle text, scale, position and subtitle margin to build a compact hit target plus a small touch slop.
 */
internal fun estimateSubtitleTouchRect(
  text: String,
  positionPercent: Int,
  scale: Float,
  isSecondary: Boolean,
  width: Float,
  height: Float,
  baseFontSize: Int,
  /** `sub-margin-y` in mpv scaled pixels (converted with [unitScale]). */
  verticalMargin: Float = 34f,
  /** Screen pixels per mpv scaled pixel, see [mpvScaledPixelFactor]. 1 = values are already pixels. */
  unitScale: Float = 1f,
): SubtitleTouchRect {
  if (width <= 0f || height <= 0f) {
    return SubtitleTouchRect(0f, 0f, 0f, 0f)
  }

  val block = measureSubtitleBlock(text, scale, width, baseFontSize, unitScale)
  val blockWidth = block.width
  val blockHeight = block.height
  val verticalPadding = block.verticalPadding

  // mpv multiplies the margin by the same font scale as the text (including sub-scale).
  val effectiveMargin = subtitleEffectiveMargin(verticalMargin, unitScale, scale, height)

  // libass (ass_render.c, "subtitle" branch) places the BOTTOM of the text block at
  //   bottom = (height - margin) * pos / 100
  // and, when that would push the block above the screen, clamps the block's TOP to the top edge
  // ("clip to top ..."). Both primary and secondary subtitles go through this same code, because
  // mpv only passes 100 - sub-pos as libass' line position. The old code anchored the block at
  // `pos * height - margin` and, for small positions, at `pos * height + margin`, which put the
  // hit box far below the real subtitle (e.g. in the middle of a portrait screen at pos 0..30).
  val textBottom = subtitleTextBottom(positionPercent, height, effectiveMargin)
  val blockTop = max(0f, textBottom + verticalPadding - blockHeight)
  val blockBottom = blockTop + blockHeight

  val top: Float
  val bottom: Float
  if (isSecondary) {
    top = blockTop
    bottom = blockBottom
  } else {
    // The vertical hit target is intentionally tighter above a primary subtitle than below it.
    // Keep this trim fixed in screen pixels so scaling the subtitle does not change the amount
    // of empty space removed above it.
    top = blockTop + PRIMARY_TOP_TRIM_PX
    bottom = blockBottom
  }

  // Keep the hit slop small: close enough to the glyphs to make grabbing comfortable, but not
  // wide enough to turn a normal empty-area swipe into a subtitle gesture.
  val horizontalSlop = min(8f, max(4f, width * 0.003f))
  val verticalSlop = min(8f, max(4f, height * 0.003f))

  return SubtitleTouchRect(
    left = ((width - blockWidth) / 2f - horizontalSlop).coerceAtLeast(0f),
    top = (top - verticalSlop).coerceAtLeast(0f),
    right = ((width + blockWidth) / 2f + horizontalSlop).coerceAtMost(width),
    bottom = (bottom + verticalSlop).coerceAtMost(height),
  )
}

private fun subtitleEffectiveMargin(
  verticalMargin: Float,
  unitScale: Float,
  scale: Float,
  height: Float,
): Float = (verticalMargin * unitScale * scale.coerceAtLeast(0.1f)).coerceIn(0f, height * 0.25f)

/** Bottom edge of the subtitle text as libass places it, before the top clamp. */
private fun subtitleTextBottom(positionPercent: Int, height: Float, effectiveMargin: Float): Float =
  (height - effectiveMargin) * (positionPercent.coerceIn(0, 150) / 100f)

private class SubtitleBlockMetrics(
  val width: Float,
  val height: Float,
  val verticalPadding: Float,
)

/** Estimated size of the rendered subtitle block (shared by the hit box and the drag ceiling). */
private fun measureSubtitleBlock(
  text: String,
  scale: Float,
  width: Float,
  baseFontSize: Int,
  unitScale: Float,
): SubtitleBlockMetrics {
  val normalizedText = normalizeSubtitleText(text)
  val effectiveScale = scale.coerceAtLeast(0.1f)
  // sub-font-size is in mpv scaled pixels (size at a 720px-tall window). [unitScale] converts it
  // to real screen pixels; it is height / 720, NOT the raw height (that would be 720x too big).
  val fontPx = max(1f, baseFontSize.toFloat()) * unitScale.coerceAtLeast(0.01f) * effectiveScale
  // libass requests the font with FT_SIZE_REQUEST_TYPE_REAL_DIM (ass_font.c), i.e. ascent + descent
  // equals the font size. So one text line is exactly fontPx tall; the old 1.28 made every box
  // ~28% per line too tall, which grabbed touches below/above the visible text.
  val lineHeight = fontPx
  val horizontalPadding = fontPx * 0.18f
  val verticalPadding = fontPx * 0.10f
  // Keep the estimated block close to the visible glyphs. The previous 0.54 factor plus a
  // 90%-screen cap could turn long subtitle strings into a large central hit region that also
  // captured nearby empty areas.
  val approximateGlyphWidth = max(1f, fontPx * 0.40f)
  // libass wraps at the frame width minus the left/right margins (mpv's sub-margin-x, 19 scaled
  // pixels by default, multiplied by the font scale like sub-margin-y).
  val sideMargin = 19f * unitScale.coerceAtLeast(0.01f) * effectiveScale
  val usableWidth = max(width * 0.5f, width - 2f * sideMargin)
  val maxCharsPerLine = max(
    1,
    ((usableWidth - 2f * horizontalPadding) / approximateGlyphWidth).toInt(),
  )

  var lineCount = 0
  var widestLineChars = 0
  normalizedText.lines().forEach { rawLine ->
    val length = rawLine.trim().length
    if (length > 0) {
      val lines = ceil(length.toDouble() / maxCharsPerLine).toInt()
      lineCount += lines
      // libass wraps "smart" (WrapStyle 0): a long line is split into evenly filled lines, not
      // filled greedily up to the maximum width, so the widest line is about length / lines.
      widestLineChars = max(widestLineChars, ceil(length.toDouble() / lines).toInt())
    }
  }

  lineCount = max(1, lineCount)
  widestLineChars = max(1, widestLineChars)

  val estimatedWidth = widestLineChars * approximateGlyphWidth + 2f * horizontalPadding
  val minimumWidth = (fontPx * 1.10f).coerceAtMost(width * 0.14f)
  return SubtitleBlockMetrics(
    width = estimatedWidth.coerceIn(minimumWidth, usableWidth),
    height = lineCount * lineHeight + 2f * verticalPadding,
    verticalPadding = verticalPadding,
  )
}

/**
 * Highest `sub-pos` / `secondary-sub-pos` (percent) at which a text subtitle still leaves at least
 * [MIN_GRABBABLE_STRIP_PX] of its hit box on screen. Past that point the estimated box falls
 * entirely below the viewport, so a subtitle dragged there could never be grabbed again.
 *
 * Positions above 100 stay allowed while part of the subtitle is visible.
 */
internal fun maxGrabbableSubtitlePosition(
  text: String,
  scale: Float,
  isSecondary: Boolean,
  width: Float,
  height: Float,
  baseFontSize: Int,
  verticalMargin: Float = 34f,
  unitScale: Float = 1f,
): Float {
  if (width <= 0f || height <= 0f) return ABSOLUTE_MAX_POSITION

  val block = measureSubtitleBlock(text, scale, width, baseFontSize, unitScale)
  val margin = subtitleEffectiveMargin(verticalMargin, unitScale, scale, height)
  val strip = min(MIN_GRABBABLE_STRIP_PX, height)
  // Solve "box top == height - strip" for the text bottom, mirroring estimateSubtitleTouchRect():
  //   top = textBottom + padding - blockHeight (+ trim for primary)
  val trim = if (isSecondary) 0f else PRIMARY_TOP_TRIM_PX
  val textBottomMax = height - strip - trim + block.height - block.verticalPadding
  // textBottom = (height - margin) * pos / 100  =>  pos = textBottom / (height - margin) * 100
  val usableHeight = max(1f, height - margin)
  val positionMax = textBottomMax / usableHeight * 100f
  // mpv takes integer percentages and the drag rounds to the nearest one, so round the ceiling down.
  return floor(positionMax).coerceIn(0f, ABSOLUTE_MAX_POSITION)
}

/**
 * Highest `sub-pos` / `secondary-sub-pos` (percent) at which the WHOLE text subtitle is still inside
 * the frame, i.e. its bottom edge (plus a small outline allowance) does not pass the bottom of the
 * player.
 *
 * libass anchors the BOTTOM of the text block at `(height - margin) * pos / 100` (see
 * [estimateSubtitleTouchRect]), so this bound depends on the margin and the font size but not on how
 * many lines the subtitle has. There is no matching minimum to compute: libass itself clamps the
 * block's top to the top of the frame, so `0` can never push anything out of the top.
 *
 * Everything is derived from the live player [height], the subtitle [scale] and mpv's own units, so
 * it adapts to phones, tablets, TVs, split screen and any resolution without special cases.
 */
internal fun maxPositionInsideFrame(
  text: String,
  scale: Float,
  width: Float,
  height: Float,
  baseFontSize: Int,
  verticalMargin: Float = 34f,
  unitScale: Float = 1f,
): Float {
  if (width <= 0f || height <= 0f) return ABSOLUTE_MAX_POSITION

  val block = measureSubtitleBlock(text, scale, width, baseFontSize, unitScale)
  val margin = subtitleEffectiveMargin(verticalMargin, unitScale, scale, height)
  val textBottomMax = height - block.verticalPadding
  val usableHeight = max(1f, height - margin)
  // Round down: mpv takes integer percentages and the drag rounds to the nearest one.
  return floor(textBottomMax / usableHeight * 100f).coerceIn(0f, ABSOLUTE_MAX_POSITION)
}

/** Same as [maxGrabbableSubtitlePosition] for the bitmap fallback zone. */
internal fun maxGrabbableBitmapSubtitlePosition(
  isSecondary: Boolean,
  width: Float,
  height: Float,
): Float {
  if (width <= 0f || height <= 0f) return ABSOLUTE_MAX_POSITION

  val strip = min(MIN_GRABBABLE_STRIP_PX, height)
  val blockHeight = (height * 0.08f).coerceIn(60f, 150f)
  val anchorMax = if (isSecondary) {
    height - strip + height * 0.015f
  } else {
    height - strip + blockHeight
  }
  // mpv takes integer percentages and the drag rounds to the nearest one, so round the ceiling down.
  return floor(anchorMax / height * 100f).coerceIn(0f, ABSOLUTE_MAX_POSITION)
}

/**
 * Bitmap subtitles do not expose text bounds through mpv. Keep the fallback deliberately narrow so
 * it is centered around the subtitle anchor instead of occupying most of the screen.
 */
internal fun estimateBitmapSubtitleTouchRect(
  positionPercent: Int,
  isSecondary: Boolean,
  width: Float,
  height: Float,
): SubtitleTouchRect {
  if (width <= 0f || height <= 0f) {
    return SubtitleTouchRect(0f, 0f, 0f, 0f)
  }

  val anchorY = (positionPercent.coerceIn(0, 150) / 100f) * height
  val blockHeight = (height * 0.08f).coerceIn(60f, 150f)
  val top = if (isSecondary) anchorY - height * 0.015f else anchorY - blockHeight
  val bottom = if (isSecondary) anchorY + blockHeight else anchorY + height * 0.015f
  val horizontalMargin = width * 0.24f

  return SubtitleTouchRect(
    left = horizontalMargin,
    top = top.coerceAtLeast(0f),
    right = (width - horizontalMargin).coerceAtMost(width),
    bottom = bottom.coerceAtMost(height),
  )
}


/**
 * Checks a one-finger subtitle touch strictly against the compact hit box. For a two-finger pinch,
 * the midpoint must remain on the subtitle while each finger may sit a little outside its edge.
 */
internal fun subtitleTouchContains(
  rect: SubtitleTouchRect,
  points: List<Pair<Float, Float>>,
  pinchMargin: Float = 20f,
): Boolean {
  if (points.isEmpty()) return false
  if (points.size == 1) return rect.contains(points[0].first, points[0].second)

  val midpointX = points.map { it.first }.average().toFloat()
  val midpointY = points.map { it.second }.average().toFloat()
  if (!rect.contains(midpointX, midpointY)) return false

  val expanded = rect.expanded(pinchMargin, pinchMargin)
  return points.all { expanded.contains(it.first, it.second) }
}

internal fun calculateSubtitleScale(
  startScale: Float,
  startDistance: Float,
  currentDistance: Float,
): Float {
  if (startDistance <= 0f || currentDistance <= 0f) return startScale
  return startScale * (currentDistance / startDistance)
}

private fun normalizeSubtitleText(text: String): String =
  stripSubtitleStyleTags(text)
    .replace("\\N", "\n")
    .replace("\\n", "\n")
    .replace("\r\n", "\n")
    .replace("\r", "\n")

/**
 * Removes ASS override blocks and simple markup without using regular expressions.
 *
 * Android's ICU regex implementation is stricter than the JVM regex engine for some patterns.
 * Keeping this small normalization pass non-regex based avoids PatternSyntaxException on subtitle
 * input while remaining linear in the subtitle text size.
 */
private fun stripSubtitleStyleTags(text: String): String {
  val out = StringBuilder(text.length)
  var braceDepth = 0
  var angleDepth = 0

  for (char in text) {
    when {
      braceDepth > 0 -> {
        if (char == '}') braceDepth--
      }
      angleDepth > 0 -> {
        if (char == '>') angleDepth--
      }
      char == '{' -> braceDepth = 1
      char == '<' -> angleDepth = 1
      else -> out.append(char)
    }
  }

  return out.toString()
}
