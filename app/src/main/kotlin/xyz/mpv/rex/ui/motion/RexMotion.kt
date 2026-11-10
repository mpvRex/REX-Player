package xyz.mpv.rex.ui.motion

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.MotionDurationScale

/**
 * One source of truth for app-wide motion.
 *
 * Three primitives, per docs/ui-design/ANIMATION_STAGES.md:
 *  - container transform: a tapped card expands into the pushed screen
 *  - shared X: scale + fade for pushes with no source card
 *  - dock expand: bottom bars / sheets grow from their docked edge
 *
 * Specs come from [MotionScheme.expressive] - the same scheme `MpvexTheme` installs on
 * `MaterialTheme` - read directly rather than through `MaterialTheme.motionScheme`, because
 * `NavDisplay` transition specs are not composable. Keeping one instance here means the
 * navigation spec cannot drift from the theme's motion tokens.
 */
object RexMotion {
  /** How far the outgoing screen recedes on a shared-X transition. */
  const val SHARED_X_SCALE: Float = 0.96f

  private val scheme: MotionScheme = MotionScheme.expressive()

  /** True when the user has not turned system animations off. */
  fun animationsEnabled(context: android.content.Context): Boolean {
    val scale = try {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    } catch (_: Exception) {
      1f
    }
    return scale > 0f
  }

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun spatialSpec(context: android.content.Context): FiniteAnimationSpec<Float> =
    if (animationsEnabled(context)) scheme.defaultSpatialSpec() else snap()

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun effectsSpec(context: android.content.Context): FiniteAnimationSpec<Float> =
    if (animationsEnabled(context)) scheme.defaultEffectsSpec() else tween(durationMillis = 0)

  /**
   * Push/pop transition for a navigation entry.
   *
   * @param forward true for push, false for pop
   * @param source the card the user tapped, when this entry has one
   */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun navTransition(
    context: android.content.Context,
    forward: Boolean,
    source: ContainerTransformSource?,
  ): Pair<EnterTransition, ExitTransition> {
    val spatial = spatialSpec(context)
    val effects = effectsSpec(context)

    if (source != null) {
      // Container transform. The source screen is only faded: translating it would read as
      // a slide, which is exactly what this change removes.
      return containerTransformEnter(source, spatial, effects) to fadeOut(effects)
    }

    // Shared X fallback: destination settles in, source recedes. Same on push and pop.
    val enter = scaleIn(animationSpec = spatial, initialScale = SHARED_X_SCALE) + fadeIn(effects)
    val exit = scaleOut(animationSpec = spatial, targetScale = SHARED_X_SCALE) + fadeOut(effects)
    return enter to exit
  }

  /**
   * Predictive-back transition.
   *
   * The system back gesture is driven by Navigation 3 itself (it previews the pop), so the
   * only thing this spec decides is the *shape* the departing screen collapses into: the tapped
   * card's band when there is a source, otherwise the shared-X scale. Progress is deliberately
   * not re-implemented here; Navigation 3 already maps gesture distance onto it.
   */
  fun predictiveBackTransition(
    context: android.content.Context,
    source: ContainerTransformSource?,
  ): Pair<EnterTransition, ExitTransition> {
    val effects = effectsSpec(context)
    val departure = scaleOut(
      animationSpec = settleSpec,
      targetScale = source?.scaleFraction ?: SHARED_X_SCALE,
      transformOrigin = TransformOrigin(0.5f, source?.topFraction ?: 0.5f),
    ) + fadeOut(effects)
    return fadeIn(effects) to departure
  }

  /** Settle used when the gesture commits; a spatial spring here would fight the finger. */
  private val settleSpec: FiniteAnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

  /**
   * Entrance for docked chrome (selection bars, mini player, floating bars).
   *
   * The element grows out of the edge it is docked to instead of travelling across the
   * screen, so it reads as attached to the surface rather than flying in. Same axis in and
   * out, which is what the mini player previously got wrong (up in, sideways out).
   */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun dockEnter(dockedToBottom: Boolean = true): EnterTransition =
    expandVertically(
      animationSpec = scheme.defaultSpatialSpec(),
      expandFrom = if (dockedToBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeIn(scheme.defaultEffectsSpec())

  /** Exit matching [dockEnter]: shrinks back into the docked edge. */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun dockExit(dockedToBottom: Boolean = true): ExitTransition =
    shrinkVertically(
      animationSpec = scheme.defaultSpatialSpec(),
      shrinkTowards = if (dockedToBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeOut(scheme.defaultEffectsSpec())

  /**
   * Reveal for player side panels.
   *
   * Panels are anchored to a screen edge, so they unfold from that edge instead of sliding
   * the full screen width: a short horizontal expand plus a small scale. Full-width travel
   * read as a new screen arriving on top of the video.
   *
   * @param fromEnd true when the panel is docked to the trailing edge
   */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun panelEnter(fromEnd: Boolean = true): EnterTransition =
    expandHorizontally(
      animationSpec = scheme.defaultSpatialSpec(),
      expandFrom = if (fromEnd) Alignment.End else Alignment.Start,
    ) +
      scaleIn(animationSpec = scheme.defaultSpatialSpec(), initialScale = 0.96f) +
      fadeIn(scheme.defaultEffectsSpec())

  /** Exit matching [panelEnter]. */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun panelExit(fromEnd: Boolean = true): ExitTransition =
    shrinkHorizontally(
      animationSpec = scheme.defaultSpatialSpec(),
      shrinkTowards = if (fromEnd) Alignment.End else Alignment.Start,
    ) +
      scaleOut(animationSpec = scheme.defaultSpatialSpec(), targetScale = 0.96f) +
      fadeOut(scheme.defaultEffectsSpec())
}
