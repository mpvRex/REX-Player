package xyz.mpv.rex.ui.browser.cards

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.preferences.NetworkViewMode

/**
 * Layout configuration parameters mapped deterministically from [NetworkViewMode].
 *
 * @property viewMode The active network view mode.
 * @property isGridMode Whether the layout uses a grid container.
 * @property gridColumns Number of columns to render in grid layouts.
 * @property thumbnailSizeDp Requested display size of the thumbnail container in dp.
 * @property targetDimensionPx Target pixel dimension for thumbnail decoding/retrieval.
 * @property thumbnailAspectRatio Aspect ratio of the thumbnail image container.
 * @property maxTitleLines Maximum lines of text displayed for item titles.
 * @property showChips Whether metadata chips should be rendered.
 * @property isDetailMode Whether compact detail row layout is active.
 */
data class CardLayoutConfig(
  val viewMode: NetworkViewMode,
  val isGridMode: Boolean,
  val gridColumns: Int,
  val thumbnailSizeDp: Dp,
  val targetDimensionPx: Int,
  val thumbnailAspectRatio: Float,
  val maxTitleLines: Int,
  val showChips: Boolean,
  val isDetailMode: Boolean = false,
)

/**
 * Provides layout configuration properties mapped from [NetworkViewMode].
 */
object CardLayoutConfigProvider {
  /**
   * Computes the deterministic [CardLayoutConfig] for a given [viewMode] and screen configuration.
   *
   * @param viewMode The active network view mode.
   * @param screenWidthDp Screen width in density-independent pixels.
   * @param unlimitedNameLines Whether title line limits should be lifted.
   */
  fun configFor(
    viewMode: NetworkViewMode,
    screenWidthDp: Int = 360,
    unlimitedNameLines: Boolean = false,
  ): CardLayoutConfig {
    return when (viewMode) {
      NetworkViewMode.GALLERY -> CardLayoutConfig(
        viewMode = viewMode,
        isGridMode = true,
        gridColumns = 1,
        thumbnailSizeDp = screenWidthDp.dp,
        targetDimensionPx = 512,
        thumbnailAspectRatio = 16f / 9f,
        maxTitleLines = if (unlimitedNameLines) Int.MAX_VALUE else 2,
        showChips = true,
      )

      NetworkViewMode.GRID -> {
        val columns = when {
          screenWidthDp >= 900 -> 4
          screenWidthDp >= 600 -> 3
          else -> 2
        }
        CardLayoutConfig(
          viewMode = viewMode,
          isGridMode = true,
          gridColumns = columns,
          thumbnailSizeDp = (screenWidthDp / columns).dp,
          targetDimensionPx = 384,
          thumbnailAspectRatio = 16f / 9f,
          maxTitleLines = if (unlimitedNameLines) Int.MAX_VALUE else 2,
          showChips = false,
        )
      }

      NetworkViewMode.LIST -> CardLayoutConfig(
        viewMode = viewMode,
        isGridMode = false,
        gridColumns = 1,
        thumbnailSizeDp = 128.dp,
        targetDimensionPx = 256,
        thumbnailAspectRatio = 16f / 9f,
        maxTitleLines = if (unlimitedNameLines) Int.MAX_VALUE else 2,
        showChips = true,
      )

      NetworkViewMode.DETAIL -> CardLayoutConfig(
        viewMode = viewMode,
        isGridMode = false,
        gridColumns = 1,
        thumbnailSizeDp = 40.dp,
        targetDimensionPx = 96,
        thumbnailAspectRatio = 1f,
        maxTitleLines = 1,
        showChips = false,
        isDetailMode = true,
      )
    }
  }
}
