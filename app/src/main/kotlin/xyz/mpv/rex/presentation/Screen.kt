package xyz.mpv.rex.presentation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

interface Screen : NavKey {
  /**
   * Id of the card this screen expands out of, if any (see
   * `docs/ui-design/ANIMATION_STAGES.md`). Consumed by `NavDisplay` as the entry's
   * `contentKey` so the container transform can find the source bounds. `null` means the
   * screen is entered with the shared scale + fade transition instead.
   */
  val transformSourceId: String?
    get() = null

  @Composable
  fun Content()
}
