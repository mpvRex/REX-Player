package xyz.mpv.rex.presentation.components.pullrefresh

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A reusable Box that wraps content with classic pull-to-refresh: the content
 * stays fixed in place and only the refresh indicator moves, dropping in from
 * the top edge.
 *
 * The content is deliberately NOT translated. Moving it slid the whole list —
 * including any rounded container corners — down the screen while pulling,
 * which broke the content-surface layout.
 *
 * @param isRefreshing Tracks refresh state.
 * @param onRefresh Invoked when refresh is triggered.
 * @param modifier Box modifier.
 * @param enabled Toggles pull-to-refresh.
 * @param refreshThreshold Pull distance at which a refresh triggers.
 * @param delayAfterRefresh Delay (ms) to keep the indicator visible after completion.
 * @param content Content displayed inside the Box.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullRefreshBox(
  isRefreshing: MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  refreshThreshold: Dp = 80.dp,
  delayAfterRefresh: Long = 800L,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val state = rememberPullToRefreshState()
  val density = LocalDensity.current

  // Tracks active refresh to cancel previous jobs on rapid pulls.
  val activeJob = remember { mutableStateOf<Job?>(null) }

  // Indicator travel: how far down from the top edge the spinner sits.
  val maxTranslationPx = with(density) { refreshThreshold.toPx() }

  val indicatorTranslationY = if (isRefreshing.value) {
    maxTranslationPx
  } else {
    (state.distanceFraction * maxTranslationPx).coerceAtLeast(0f)
  }

  // Follow the drag directly while pulling; settle back once released.
  // Bouncy easing would overshoot past zero on release, so the indicator dips
  // below the top edge and then pops back into view — the hidden edge flashing.
  // No-bounce easing keeps the return monotonic: it hides once and stays hidden.
  val animatedTranslationY by animateFloatAsState(
    targetValue = indicatorTranslationY,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "refresh_indicator_translationY",
  )

  // Fixed size: the indicator never scales. It slides in from above the top
  // edge instead, revealed by the container's own bounds.
  val indicatorSize = 56.dp
  val indicatorSizePx = remember(density) { with(density) { indicatorSize.toPx() } }

  Box(
    modifier = modifier
      // Clips the indicator while it is still above the top edge, so it appears
      // to drop in from the header rather than fade or grow into place.
      .clipToBounds()
      .pullToRefresh(
        isRefreshing = isRefreshing.value,
        state = state,
        enabled = enabled,
        onRefresh = {
          activeJob.value?.cancel()
          isRefreshing.value = true
          activeJob.value = coroutineScope.launch {
            try {
              onRefresh()
              delay(delayAfterRefresh)
            } finally {
              isRefreshing.value = false
            }
          }
        },
      ),
  ) {
    // LAYER 1: Content, static. Never translated.
    Box(modifier = Modifier.matchParentSize()) {
      content()
    }

    // LAYER 2: Refresh indicator, fixed size, sliding down from the top edge.
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        // Emerges from behind the top edge as progress grows; fully out of
        // sight at rest.
        .graphicsLayer {
          translationY = animatedTranslationY - indicatorSizePx
        }
        .size(indicatorSize)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(6.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (isRefreshing.value) {
        // Indeterminate overload (no progress lambda): keeps morphing shapes.
        LoadingIndicator(
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        LoadingIndicator(
          progress = { state.distanceFraction.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
