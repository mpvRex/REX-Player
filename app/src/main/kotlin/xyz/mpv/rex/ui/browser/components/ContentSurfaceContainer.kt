package xyz.mpv.rex.ui.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The content surface: the constant rounded sheet that browser screens paint
 * their card lists on.
 *
 * The container owns the insets and the 24.dp rounding; the list inside it
 * (`UnifiedExplorerContent` with `useContentSurface = true`) owns the cards and
 * the filler that keeps the sheet painted to the bottom edge.
 *
 * Keep every screen on these insets. Screens that draw their own edges instead
 * end up with different corner radii and different gaps between the sheet and
 * the screen's sides.
 *
 * @param navigationBarHeight bottom inset of the device; the sheet stops above
 *   the navigation bar with a 14.dp gap.
 */
@Composable
fun ContentSurfaceContainer(
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
      .padding(
        start = 8.dp,
        end = 8.dp,
        top = 2.dp,
        bottom = navigationBarHeight + 14.dp,
      )
      .clip(RoundedCornerShape(24.dp)),
  ) {
    content()
  }
}

/**
 * Sectioned content surface: the same painted sheet, but for lists that mix
 * section headers and grid rows ([UnifiedExplorerContent] with
 * `showSections = true`).
 *
 * A regular [ContentSurfaceContainer] paints one flat sheet for one card list.
 * Search and the file-system browser group their results under "Folders" and
 * "Media" headers, which read as page structure rather than as rows of a single
 * sheet, so each section keeps its cards on the sheet while the gaps between
 * groups show the screen tone — the same contrast the card gaps use.
 */
@Composable
fun SectionedContentSurfaceContainer(
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
      .padding(
        start = 8.dp,
        end = 8.dp,
        top = 2.dp,
        bottom = navigationBarHeight + 14.dp,
      ),
  ) {
    content()
  }
}
