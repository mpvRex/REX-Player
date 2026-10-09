package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.ui.player.controls.components.panels.toColorHexString
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Subtitle touch handling (drag to reposition, pinch to resize, "grabbed" marker).
 *
 * Design in one paragraph: a touch that STARTS on a subtitle belongs to the subtitle from the
 * first finger down until the last finger up. A single pointer recognizer ([subtitleTouchOwner])
 * decides that at touch-down and publishes it through [SubtitleTouchGate]; every other recognizer in
 * GestureHandler checks the gate right after its own touch-down and steps aside for the whole
 * touch. The finger logic itself lives in [SubtitleGestureEngine] (pure Kotlin, unit tested);
 * this file only connects it to Compose pointer events and to mpv.
 */

private const val SUBTITLE_HIGHLIGHT_COLOR = "#66808080"
private const val SUBTITLE_HIGHLIGHT_PADDING = "4"
private const val SCALE_WRITE_STEP = 0.005f

/**
 * mpv has ONE subtitle style that the primary and the secondary text subtitle both use (there are no
 * `secondary-sub-border-style` / `-back-color` options), so the grab tint always goes through the
 * `sub-*` properties and would show on every visible text subtitle. [SubtitleGrabHighlight] therefore
 * isolates the touched subtitle: it hides the OTHER text subtitle for the duration of the touch.
 */
private const val STYLE_PREFIX = "sub"

/**
 * Primary native ASS/SSA tracks ignore the shared `sub-*` style unless `sub-ass-override=force`
 * (REX runs the primary at `scale` when "override ASS" is off; the secondary is always forced).
 * When true, the primary ASS override is forced ONLY while the primary ASS is grabbed, then restored.
 * `force` can change how that ASS looks while grabbed (font, size, outline, position). If that is
 * visible on a real device, set this to false: the primary ASS then simply gets no tint.
 */
private const val FORCE_PRIMARY_ASS_WHILE_GRABBED = true

/**
 * The touch slop is only a guard against accidental drags. The subtitle has already been grabbed by
 * the time it matters, and the slop distance is not applied to the position (that is what keeps the
 * subtitle from jumping), so a smaller slop directly shortens the "dead" start of a drag.
 */
private const val SUBTITLE_DRAG_SLOP_FACTOR = 0.5f

/**
 * Tells the other gesture recognizers that the current touch belongs to a subtitle.
 *
 * It is deliberately NOT Compose state: it is written and read inside pointer-event handling, where
 * a recomposition would arrive too late and would needlessly restart recognizers.
 */
internal class SubtitleTouchGate {
  @Volatile
  var isActive: Boolean = false
    internal set
}

/** What the subtitle gestures need from the player UI. */
internal interface SubtitleTouchUi {
  fun showText(text: String)
  fun clearText()
  fun onDragCompleted()
  fun hapticDragStart()
  fun hapticPinchStart()
}

internal data class SubtitleHitTarget(
  val prefix: String,
  val textBased: Boolean,
  /**
   * Highest position the drag may reach, for a given subtitle scale, while some of the subtitle
   * stays grabbable on screen. It is a function because pinching changes the subtitle's size.
   */
  val maxPositionForScale: (Float) -> Float = { SubtitleGestureEngine.POSITION_MAX },
)

/**
 * Screen pixels per mpv "scaled pixel" for the live player. The live mpv property wins over the
 * saved preference because the subtitle panel flips `sub-scale-by-window` at runtime.
 */
internal fun subtitleUnitScale(height: Float, prefs: SubtitlesPreferences): Float {
  val scaleByWindow = MPVLib.getPropertyString("sub-scale-by-window")
    ?.let { it == "yes" }
    ?: prefs.scaleByWindow.get()
  return mpvScaledPixelFactor(scaleByWindow, height)
}

/**
 * Whether the estimated box can be trusted for this subtitle. If not, a touch must NOT grab it,
 * otherwise a touch on empty video "grabs" a subtitle that is drawn somewhere else (or not at all):
 *  - A hidden subtitle (`sub-visibility=no`) still reports its text but draws nothing.
 *  - A native ASS/SSA track with `sub-ass-override=no` ignores `sub-pos` and uses its own
 *    placement, so the box estimated from `sub-pos` is somewhere else entirely (and dragging
 *    could not move it anyway). This guard is deliberately kept: forcing the override only AFTER
 *    the touch cannot help, because the hit test has to succeed first and mpv exposes no real
 *    bounds for such a track. In REX this state is not reachable once a file is loaded:
 *    applySubtitlePreferences() sets the primary override to `force` or `scale` on every file load.
 */
private fun isSubtitleBoxEstimable(prefix: String): Boolean {
  if (MPVLib.getPropertyString("$prefix-visibility") == "no") return false
  val codecProperty = if (prefix == "secondary-sub") "current-tracks/sub2/codec" else "current-tracks/sub/codec"
  val codec = MPVLib.getPropertyString(codecProperty)?.lowercase()
  if (codec == "ass" || codec == "ssa") {
    if (MPVLib.getPropertyString("$prefix-ass-override") == "no") return false
  }
  return true
}

private val BITMAP_SUBTITLE_CODECS = setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub")

/**
 * Image subtitles (PGS...) are drawn where the disc authors put them, usually a little ABOVE the
 * bottom edge, and mpv exposes no bounds for them. The old fallback zone hugged the bottom edge, so
 * a touch right on the subtitle missed it while a touch just below it worked. While a bitmap line is
 * on screen, the zone below is added: it covers the usual place of the picture, follows `sub-pos`
 * (mpv moves a bitmap by (100 - sub-pos)% of the height) and grows/shrinks with `sub-scale`.
 * These fractions are of the viewport height and are estimates, not measurements.
 */
private const val BITMAP_NOMINAL_BOTTOM = 0.96f
private const val BITMAP_ZONE_HEIGHT = 0.20f
private const val BITMAP_ZONE_SIDE_MARGIN = 0.20f

/** Lets a two-finger pinch resize image subtitles too. Set to false to go back to drag only. */
private const val ALLOW_BITMAP_PINCH = true

/** True while the track has a current line (mpv reports `sub-start` only then). */
private fun isBitmapSubtitleOnScreen(prefix: String): Boolean {
  val property = if (prefix == "secondary-sub") "secondary-sub-start" else "sub-start"
  return MPVLib.getPropertyDouble(property) != null
}

internal fun estimateRenderedBitmapTouchRect(
  positionPercent: Int,
  scale: Float,
  width: Float,
  height: Float,
): SubtitleTouchRect {
  if (width <= 0f || height <= 0f) return SubtitleTouchRect(0f, 0f, 0f, 0f)

  val shift = (100 - positionPercent.coerceIn(0, 150)) / 100f * height
  val zoneHeight = height * BITMAP_ZONE_HEIGHT
  // mpv stops moving a bitmap up when it reaches the top of the frame, so the zone stops there too
  // (without this, a position near 0 would shrink the zone to nothing).
  val bottom = (height * BITMAP_NOMINAL_BOTTOM - shift).coerceIn(zoneHeight, height)
  val top = bottom - zoneHeight
  // mpv scales a bitmap around its own centre. Same limits as the pinch gesture itself.
  val centre = (top + bottom) / 2f
  val factor = scale.coerceIn(SubtitleGestureEngine.SCALE_MIN, SubtitleGestureEngine.SCALE_MAX)
  val margin = width * BITMAP_ZONE_SIDE_MARGIN

  return SubtitleTouchRect(
    left = margin,
    top = (centre - (centre - top) * factor).coerceAtLeast(0f),
    right = width - margin,
    bottom = (centre + (bottom - centre) * factor).coerceAtMost(height),
  )
}

/**
 * True only for image-based subtitles (PGS, VobSub, DVB). A TEXT subtitle that merely has no line on
 * screen right now (a gap between two lines) must not be treated as one: its "zone" would grab touches
 * on empty video and tint the other subtitle.
 */
private fun isBitmapSubtitleTrack(prefix: String): Boolean {
  val property = if (prefix == "secondary-sub") "current-tracks/sub2/codec" else "current-tracks/sub/codec"
  val codec = MPVLib.getPropertyString(property)?.lowercase() ?: return false
  return codec in BITMAP_SUBTITLE_CODECS
}

/**
 * Returns the subtitle track whose estimated touch block contains the supplied touch points.
 * Secondary subtitles are checked first because they are visually layered above primary subtitles.
 *
 * For bitmap subtitles mpv exposes no subtitle text, so a conservative fallback zone is used when
 * [allowBitmapFallback] is true (callers pass true when dragging or pinching is enabled).
 */
internal fun findSubtitleUnderTouch(
  points: List<Pair<Float, Float>>,
  width: Float,
  height: Float,
  prefs: SubtitlesPreferences,
  allowBitmapFallback: Boolean,
): SubtitleHitTarget? {
  if (width <= 0f || height <= 0f) return null

  for (prefix in listOf("secondary-sub", "sub")) {
    val isSecondary = prefix == "secondary-sub"
    val sidProperty = if (isSecondary) "secondary-sid" else "sid"
    if ((MPVLib.getPropertyInt(sidProperty) ?: 0) <= 0) continue
    if (!isSubtitleBoxEstimable(prefix)) continue

    val text = MPVLib.getPropertyString("$prefix-text")?.trim().orEmpty()
    if (text.isNotEmpty()) {
      val position = (MPVLib.getPropertyInt("$prefix-pos")
        ?: if (isSecondary) prefs.secondarySubPos.get() else prefs.subPos.get()).coerceIn(0, 150)
      val scale = MPVLib.getPropertyDouble("$prefix-scale")?.toFloat()
        ?: if (isSecondary) prefs.secondarySubScale.get() else prefs.subScale.get()

      // Both values are in mpv scaled pixels; unitScale turns them into screen pixels.
      val subtitleMarginY = MPVLib.getPropertyDouble("sub-margin-y")?.toFloat() ?: 34f
      val unitScale = subtitleUnitScale(height, prefs)
      // The subtitle panel changes sub-font-size at runtime, so the live value wins.
      val baseFontSize = MPVLib.getPropertyInt("sub-font-size") ?: prefs.fontSize.get()
      val rect = estimateSubtitleTouchRect(
        text = text,
        positionPercent = position,
        scale = scale,
        isSecondary = isSecondary,
        width = width,
        height = height,
        baseFontSize = baseFontSize,
        verticalMargin = subtitleMarginY,
        unitScale = unitScale,
      )
      if (subtitleTouchContains(rect, points)) {
        return SubtitleHitTarget(
          prefix = prefix,
          textBased = true,
          maxPositionForScale = { forScale ->
            // The subtitle must stay fully inside the frame; the grabbable limit is a safety net
            // for the (unusual) case where the frame bound would still leave nothing to grab.
            min(
              maxPositionInsideFrame(
                text = text,
                scale = forScale,
                width = width,
                height = height,
                baseFontSize = baseFontSize,
                verticalMargin = subtitleMarginY,
                unitScale = unitScale,
              ),
              maxGrabbableSubtitlePosition(
                text = text,
                scale = forScale,
                isSecondary = isSecondary,
                width = width,
                height = height,
                baseFontSize = baseFontSize,
                verticalMargin = subtitleMarginY,
                unitScale = unitScale,
              ),
            )
          },
        )
      }
    } else if (allowBitmapFallback && isBitmapSubtitleTrack(prefix)) {
      val position = (MPVLib.getPropertyInt("$prefix-pos")
        ?: if (isSecondary) prefs.secondarySubPos.get() else prefs.subPos.get()).coerceIn(0, 150)
      val rect = estimateBitmapSubtitleTouchRect(
        positionPercent = position,
        isSecondary = isSecondary,
        width = width,
        height = height,
      )
      val renderedRect = if (isBitmapSubtitleOnScreen(prefix)) {
        val bitmapScale = MPVLib.getPropertyDouble("$prefix-scale")?.toFloat()
          ?: if (isSecondary) prefs.secondarySubScale.get() else prefs.subScale.get()
        estimateRenderedBitmapTouchRect(position, bitmapScale, width, height)
      } else {
        null
      }
      if (subtitleTouchContains(rect, points) ||
        (renderedRect != null && subtitleTouchContains(renderedRect, points))
      ) {
        return SubtitleHitTarget(
          prefix = prefix,
          textBased = false,
          maxPositionForScale = {
            maxGrabbableBitmapSubtitlePosition(isSecondary, width, height)
          },
        )
      }
    }
  }

  return null
}

/**
 * The "grabbed" marker: while a finger is on a text subtitle its background box is tinted by mpv
 * itself (so the box hugs the real glyphs and moves with them), and everything is restored afterwards.
 *
 * Because mpv has a single shared subtitle style, three things happen while a TEXT subtitle is grabbed:
 *  1. the shared `sub-*` style gets the translucent background-box;
 *  2. the OTHER text subtitle is hidden, so only the touched one shows the box;
 *  3. a primary native ASS/SSA is temporarily forced to accept the shared style.
 * Touching an image subtitle (PGS/VobSub/DVB) shows no highlight at all and changes nothing else.
 *
 * [show] is idempotent, so a second finger never causes a restore/reapply flash.
 */
internal class SubtitleGrabHighlight(
  private val prefs: SubtitlesPreferences,
) {
  private data class SavedStyle(
    val borderStyle: String,
    val backColor: String,
    val shadowOffset: Int,
  )

  private var activePrefix: String? = null
  private var savedStyle: SavedStyle? = null

  /** Visibility property of the other subtitle ("sub-visibility"...) and its value before we hid it. */
  private var hiddenVisibilityProperty: String? = null
  private var hiddenVisibilityValue: String? = null

  /** Primary `sub-ass-override` value before we forced it, or null if we did not touch it. */
  private var savedPrimaryAssOverride: String? = null

  fun show(prefix: String) {
    if (activePrefix == prefix && savedStyle != null) return

    restore()

    // An image subtitle (PGS/VobSub/DVB) cannot take the text box, and the shared style would
    // tint the OTHER text subtitle instead. So touching one highlights nothing at all.
    // Drag and position handling are not affected: they do not depend on the highlight.
    if (isBitmapSubtitleTrack(prefix)) return

    val fallbackBorder = prefs.borderStyle.get().value
    val fallbackBackColor = prefs.backgroundColor.get().toColorHexString()
    val fallbackShadowOffset = prefs.shadowOffset.get()

    savedStyle = SavedStyle(
      borderStyle = MPVLib.getPropertyString("$STYLE_PREFIX-border-style")?.takeIf { it.isNotBlank() } ?: fallbackBorder,
      backColor = MPVLib.getPropertyString("$STYLE_PREFIX-back-color")?.takeIf { it.isNotBlank() } ?: fallbackBackColor,
      shadowOffset = MPVLib.getPropertyInt("$STYLE_PREFIX-shadow-offset") ?: fallbackShadowOffset,
    )
    activePrefix = prefix

    runCatching {
      hideOtherTextSubtitle(prefix)
      forcePrimaryAssIfNeeded(prefix)
      MPVLib.setPropertyString("$STYLE_PREFIX-border-style", "background-box")
      MPVLib.setPropertyString("$STYLE_PREFIX-back-color", SUBTITLE_HIGHLIGHT_COLOR)
      MPVLib.setPropertyInt("$STYLE_PREFIX-shadow-offset", SUBTITLE_HIGHLIGHT_PADDING.toInt())
    }.onFailure {
      restore()
      return
    }
  }

  /**
   * Hides the other TEXT subtitle (only if it is currently selected and visible) and remembers the
   * exact visibility value so it can be put back. A subtitle the user already hid stays hidden.
   */
  private fun hideOtherTextSubtitle(prefix: String) {
    val otherIsSecondary = prefix != "secondary-sub"
    val otherPrefix = if (otherIsSecondary) "secondary-sub" else "sub"
    val otherSid = MPVLib.getPropertyInt(if (otherIsSecondary) "secondary-sid" else "sid") ?: 0
    if (otherSid <= 0) return
    if (isBitmapSubtitleTrack(otherPrefix)) return

    val property = "$otherPrefix-visibility"
    val current = MPVLib.getPropertyString(property) ?: "yes"
    if (current == "no") return

    hiddenVisibilityProperty = property
    hiddenVisibilityValue = current
    MPVLib.setPropertyString(property, "no")
  }

  /** A primary ASS/SSA track only accepts the shared style under `force`. See the constant above. */
  private fun forcePrimaryAssIfNeeded(prefix: String) {
    if (!FORCE_PRIMARY_ASS_WHILE_GRABBED || prefix != "sub") return
    val codec = MPVLib.getPropertyString("current-tracks/sub/codec")?.lowercase()
    if (codec != "ass" && codec != "ssa") return
    val current = MPVLib.getPropertyString("sub-ass-override") ?: return
    if (current == "force") return

    savedPrimaryAssOverride = current
    MPVLib.setPropertyString("sub-ass-override", "force")
  }

  /** Restores the user's exact runtime subtitle state. Safe to call repeatedly. */
  fun release() {
    restore()
  }

  private fun restore() {
    val style = savedStyle ?: return
    activePrefix = null
    savedStyle = null

    val visibilityProperty = hiddenVisibilityProperty
    val visibilityValue = hiddenVisibilityValue
    val assOverride = savedPrimaryAssOverride
    hiddenVisibilityProperty = null
    hiddenVisibilityValue = null
    savedPrimaryAssOverride = null

    // Each step is guarded on its own so one failing write can never leave the others stuck.
    runCatching {
      MPVLib.setPropertyString("$STYLE_PREFIX-border-style", style.borderStyle)
      MPVLib.setPropertyString("$STYLE_PREFIX-back-color", style.backColor)
      MPVLib.setPropertyInt("$STYLE_PREFIX-shadow-offset", style.shadowOffset)
    }
    if (assOverride != null) {
      runCatching { MPVLib.setPropertyString("sub-ass-override", assOverride) }
    }
    if (visibilityProperty != null && visibilityValue != null) {
      runCatching { MPVLib.setPropertyString(visibilityProperty, visibilityValue) }
    }
  }
}

/** Applies the engine's decisions to mpv and to the player UI. */
internal class MpvSubtitleGestureListener(
  private val prefix: String,
  private val prefs: SubtitlesPreferences,
  private val highlight: SubtitleGrabHighlight,
  private val ui: SubtitleTouchUi,
) : SubtitleGestureListener {
  private val isSecondary = prefix == "secondary-sub"
  private var appliedPosition = Int.MIN_VALUE
  private var appliedScale = Float.NaN
  private var lastFeedbackText: String? = null
  private var feedbackShown = false

  override fun onGrabbed() {
    highlight.show(prefix)
  }

  override fun onDragStarted() {
    ui.hapticDragStart()
  }

  override fun onPosition(positionPercent: Float) {
    // mpv's sub-pos is an integer percentage, so only write when the integer actually changes.
    val value = positionPercent.roundToInt().coerceIn(0, 150)
    if (value == appliedPosition) return
    appliedPosition = value
    MPVLib.setPropertyInt("$prefix-pos", value)
    ui.onDragCompleted()
    feedback(if (isSecondary) "Secondary sub position: $value" else "Sub position: $value")
  }

  override fun onPinchStarted() {
    ui.hapticPinchStart()
  }

  override fun onScale(scale: Float) {
    if (!appliedScale.isNaN() && kotlin.math.abs(scale - appliedScale) < SCALE_WRITE_STEP) return
    appliedScale = scale
    MPVLib.setPropertyDouble("$prefix-scale", scale.toDouble())
    // Same format as the subtitle panel (1.72, not 172%).
    val size = String.format(java.util.Locale.US, "%.2f", scale)
    feedback(if (isSecondary) "Secondary sub size: $size" else "Sub size: $size")
  }

  override fun onEnded(position: Float, scale: Float, moved: Boolean, scaled: Boolean) {
    highlight.release()

    // Persist exactly what was applied so it carries across videos.
    if (appliedPosition != Int.MIN_VALUE) {
      if (isSecondary) prefs.secondarySubPos.set(appliedPosition) else prefs.subPos.set(appliedPosition)
    }
    if (scaled) {
      val finalScale = if (appliedScale.isNaN()) scale else appliedScale
      if (isSecondary) prefs.secondarySubScale.set(finalScale) else prefs.subScale.set(finalScale)
    }
    if (feedbackShown) {
      feedbackShown = false
      ui.clearText()
    }
  }

  /**
   * Every changed value is shown. The values are already de-duplicated (integer position, scale
   * step), so there is no timer: a 100 ms throttle used to swallow every other number (2, 4, 6...)
   * and could even drop the last one.
   */
  private fun feedback(text: String) {
    if (text == lastFeedbackText) return
    lastFeedbackText = text
    feedbackShown = true
    ui.showText(text)
  }
}

/**
 * The single pointer recognizer for subtitle touches.
 *
 * It listens in the INITIAL pass, which Compose always delivers to every node before the MAIN
 * pass that all other recognizers use. So at the moment the other recognizers see a touch-down,
 * [gate] is already set and they can step aside. Only the FIRST finger decides: a second finger
 * landing on a subtitle while the first finger is elsewhere never starts a subtitle session.
 */
internal fun Modifier.subtitleTouchOwner(
  gate: SubtitleTouchGate,
  enabled: Boolean,
  dragEnabled: Boolean,
  pinchEnabled: Boolean,
  prefs: SubtitlesPreferences,
  highlight: SubtitleGrabHighlight,
  ui: SubtitleTouchUi,
): Modifier = pointerInput(enabled, dragEnabled, pinchEnabled) {
  if (!enabled || (!dragEnabled && !pinchEnabled)) return@pointerInput

  val slop = viewConfiguration.touchSlop

  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val target = findSubtitleUnderTouch(
      points = listOf(down.position.x to down.position.y),
      width = width,
      height = height,
      prefs = prefs,
      allowBitmapFallback = dragEnabled || (pinchEnabled && ALLOW_BITMAP_PINCH),
    ) ?: return@awaitEachGesture

    // Bitmap subtitles expose no text bounds; their zone is an estimate (see BITMAP_NOMINAL_BOTTOM).
    // mpv scales them when the matching sub-ass-override allows it, so pinch is allowed for them too.
    val canPinch = pinchEnabled && (target.textBased || ALLOW_BITMAP_PINCH)
    if (!dragEnabled && !canPinch) return@awaitEachGesture

    val prefix = target.prefix
    val isSecondary = prefix == "secondary-sub"
    val startPosition = (MPVLib.getPropertyInt("$prefix-pos")
      ?: if (isSecondary) prefs.secondarySubPos.get() else prefs.subPos.get()).coerceIn(0, 150).toFloat()
    val startScale = MPVLib.getPropertyDouble("$prefix-scale")?.toFloat()
      ?: if (isSecondary) prefs.secondarySubScale.get() else prefs.subScale.get()

    val engine = SubtitleGestureEngine(
      listener = MpvSubtitleGestureListener(prefix, prefs, highlight, ui),
      dragEnabled = dragEnabled,
      pinchEnabled = canPinch,
      viewportHeight = height,
      startPosition = startPosition,
      startScale = startScale,
      dragSlop = slop * SUBTITLE_DRAG_SLOP_FACTOR,
      pinchSlop = slop * 1.5f,
      // Recomputed from the live scale, so pinching and then dragging keeps a grabbable strip.
      // A subtitle that starts above the ceiling keeps its overshoot, so grabbing cannot jump it.
      positionMaxForScale = target.maxPositionForScale,
    )

    gate.isActive = true
    try {
      down.consume()
      engine.begin(listOf(TouchPoint(down.id.value, down.position.x, down.position.y)))

      do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressed = event.changes.filter { it.pressed }
        event.changes.forEach { it.consume() }
        engine.update(pressed.map { TouchPoint(it.id.value, it.position.x, it.position.y) })
      } while (pressed.isNotEmpty())
    } finally {
      // Runs on a normal end AND if this coroutine is cancelled mid-touch, so neither the marker
      // nor the gate can ever get stuck.
      engine.finish()
      gate.isActive = false
    }
  }
}
