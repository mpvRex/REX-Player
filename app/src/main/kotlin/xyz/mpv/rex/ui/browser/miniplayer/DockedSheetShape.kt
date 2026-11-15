package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Dynamic shape for the floating mini player:
 * When collapsed (fraction = 0), all 4 corners are rounded with [collapsedRadius].
 * As the sheet morphs towards full screen (fraction = 1), the corners smoothly animate to square (0.dp).
 */
internal class DockedSheetShape(
  private val fraction: () -> Float,
  private val collapsedRadius: Dp = MiniPlayerDefaults.CornerRadius,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radius =
      with(density) {
        collapsedRadius.toPx() * (1f - fraction().coerceIn(0f, 1f))
      }
    return Outline.Rounded(
      RoundRect(
        rect = Rect(0f, 0f, size.width, size.height),
        topLeft = CornerRadius(radius, radius),
        topRight = CornerRadius(radius, radius),
        bottomRight = CornerRadius(radius, radius),
        bottomLeft = CornerRadius(radius, radius),
      ),
    )
  }
}

/** Convenience for call sites that want the silhouette with a fixed radius. */
internal fun dockedSheetShape(radius: Dp = MiniPlayerDefaults.CornerRadius): Shape =
  DockedSheetShape(fraction = { 0f }, collapsedRadius = radius)
