package xyz.mpv.rex.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the card the user tapped lives on screen.
 *
 * @param rect window-space bounds of the card
 * @param cornerRadiusPx the card's corner radius at rest
 * @param boundsHeight window height, used to turn [rect] into fractions so the transform
 *   survives rotation and window resizing
 */
data class ContainerTransformSource(
  val rect: Rect,
  val cornerRadiusPx: Float,
  val boundsHeight: Float,
) {
  /** Band height as a fraction of the window height, clamped so it is never degenerate. */
  val scaleFraction: Float
    get() = if (boundsHeight <= 0f) 1f else (rect.height / boundsHeight).coerceIn(0.05f, 1f)

  /** Vertical position of the band's top edge, as a fraction of the window height. */
  val topFraction: Float
    get() = if (boundsHeight <= 0f) 0f else (rect.top / boundsHeight).coerceIn(0f, 1f)
}

/**
 * `id -> source` map for cards that can expand into the next screen.
 *
 * Held above `NavDisplay`, so a card registers on layout and the navigation transition can
 * look the source up by the pushed entry's `contentKey`.
 */
class ContainerTransformRegistry {
  private val sources: SnapshotStateMap<String, ContainerTransformSource> = mutableStateMapOf()

  fun put(id: String, source: ContainerTransformSource) {
    sources[id] = source
  }

  fun find(id: String?): ContainerTransformSource? = id?.let { sources[it] }
}

val LocalContainerTransformRegistry: ProvidableCompositionLocal<ContainerTransformRegistry?> =
  compositionLocalOf { null }

@Composable
fun rememberContainerTransformRegistry(): ContainerTransformRegistry =
  remember { ContainerTransformRegistry() }

/**
 * Window height in pixels. Captured once per composition; a rotation recomposes the whole
 * tree, so the value is refreshed when it matters.
 */
val LocalWindowHeight: ProvidableCompositionLocal<Float> =
  compositionLocalOf { 0f }

@Composable
fun rememberWindowHeightPx(): Float {
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  return remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
}

/**
 * Registers this card as a container-transform source.
 *
 * Bounds are captured in window space on every layout pass. When [id] is null the modifier
 * is a no-op, so every card type can call it unconditionally.
 */
@Composable
fun Modifier.transformSource(id: String?, cornerRadius: Dp = 0.dp): Modifier {
  val registry = LocalContainerTransformRegistry.current ?: return this
  if (id == null) return this
  val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
  val windowHeight = LocalWindowHeight.current
  return this.onGloballyPositioned { coordinates ->
    val rect = coordinates.boundsInWindow()
    if (rect.height <= 0f) return@onGloballyPositioned
    registry.put(
      id,
      ContainerTransformSource(
        rect = rect,
        cornerRadiusPx = radiusPx,
        boundsHeight = windowHeight,
      ),
    )
  }
}

/**
 * Entry transition for a navigation push: the destination grows out of [source]'s band.
 *
 * Sources are full-width rows, so the horizontal scale stays at 1 and the vertical scale runs
 * from the band height to the full screen. The pivot sits on the band's top edge, so the
 * screen unfolds downwards from the tapped row.
 */
fun containerTransformEnter(
  source: ContainerTransformSource,
  spec: FiniteAnimationSpec<Float>,
  effectsSpec: FiniteAnimationSpec<Float>,
): EnterTransition = scaleIn(
  animationSpec = spec,
  initialScale = source.scaleFraction,
  transformOrigin = TransformOrigin(0.5f, source.topFraction),
) + fadeIn(effectsSpec)
