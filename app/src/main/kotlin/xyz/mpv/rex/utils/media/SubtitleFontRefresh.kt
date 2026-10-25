package xyz.mpv.rex.utils.media

/*
 * Pure decisions behind "Default"/"Reset" for the subtitle font (kept free of Android classes so they can be
 * unit-tested on the JVM; the mpv calls live in SubtitleSettingsTypographyCard.kt).
 *
 * Why this exists: mpv passes `sub-font` to libass as its *fallback family* and libass reads it only when its
 * renderer is created (video start, subtitle track (re)selection, or any UPDATE_SUB_HARD option change). An empty
 * `sub-font` makes libass fall back to that remembered family, so when the renderer was built while a custom
 * font was selected, the custom font stays on screen after Default/Reset even though `sub-font` is now "".
 * Writing `sub-fonts-dir` (UPDATE_SUB_HARD) makes mpv rebuild the renderer, which re-reads `sub-font` and
 * re-scans the fonts directory. mpv ignores a write of an identical value, so a textually different but
 * equivalent spelling of the same directory is written.
 */

/**
 * True when switching to [newFont] must be followed by a renderer rebuild: a switch to Default ("") while the primary
 * or the secondary subtitle font still points at a custom font. Either property can be the one the renderer was
 * built with, so one of them already being empty must not skip the rebuild. A null (unreadable) previous value is
 * treated as "may be custom".
 */
internal fun needsRendererRebuild(
  previousFont: String?,
  previousSecondaryFont: String?,
  newFont: String,
): Boolean = newFont.isEmpty() && (previousFont != "" || previousSecondaryFont != "")

/**
 * Same directory, different spelling: toggles the trailing slash. Returns null when no valid alternative
 * exists (blank value or the file-system root), in which case no rebuild is attempted.
 */
internal fun equivalentFontsDir(dir: String?): String? {
  if (dir.isNullOrBlank()) return null
  val alternative = if (dir.endsWith("/")) dir.trimEnd('/') else "$dir/"
  return alternative.takeIf { it.isNotEmpty() && it != dir }
}
