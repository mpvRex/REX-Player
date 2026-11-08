package xyz.mpv.rex.presentation.components.pullrefresh

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * Header priority: when [headerScrollState] is supplied, header expansion is
 * prioritized over pull-to-refresh. If the header is collapsed (or partially
 * collapsed) when a gesture begins, pull-to-refresh is disabled for the duration
 * of that gesture. Downward drags expand the header without showing the refresh
 * indicator. Only once the header is already fully expanded at the start of a
 * subsequent downward gesture can pull-to-refresh trigger.
 *
 * @param isRefreshing Tracks refresh state.
 * @param onRefresh Invoked when refresh is triggered.
 * @param modifier Box modifier.
 * @param enabled Toggles pull-to-refresh.
 * @param refreshThreshold Pull distance at which a refresh triggers.
 * @param delayAfterRefresh Delay (ms) to keep the indicator visible after completion.
 * @param headerScrollState Collapsible header above this box. When non-null the
 *   header must be expanded before a pull can refresh.
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
  headerScrollState: TopAppBarState? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val state = rememberPullToRefreshState()
  val density = LocalDensity.current

  // Tracks active refresh to cancel previous jobs on rapid pulls.
  val activeJob = remember { mutableStateOf<Job?>(null) }

  // Sample header state at the beginning of each touch gesture.
  // If the header is collapsed (heightOffset < -1f) when the gesture starts,
  // this gesture will expand the header and NOT trigger pull-to-refresh.
  // Pull-to-refresh will only be allowed on the next gesture once the header is fully expanded.
  var gestureAllowsRefresh by remember {
    mutableStateOf(headerScrollState == null || headerScrollState.heightOffset >= -1f)
  }

  val pointerModifier = if (headerScrollState == null) {
    Modifier
  } else {
    Modifier.pointerInput(headerScrollState) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gestureAllowsRefresh = headerScrollState.heightOffset >= -1f
        waitForUpOrCancellation()
        gestureAllowsRefresh = headerScrollState.heightOffset >= -1f
      }
    }
  }

  val isHeaderExpanded by remember(headerScrollState) {
    derivedStateOf {
      headerScrollState == null || headerScrollState.heightOffset >= -1f
    }
  }

  val pullRefreshEnabled = enabled && gestureAllowsRefresh && isHeaderExpanded

  // Max pixel offset the indicator travels down.
  val maxTranslationPx = with(density) { refreshThreshold.toPx() }

  val targetTranslationY = if (isRefreshing.value) {
    maxTranslationPx
  } else {
    (state.distanceFraction * maxTranslationPx).coerceAtLeast(0f)
  }

  val animatedTranslationY by animateFloatAsState(
    targetValue = targetTranslationY,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "refresh_indicator_translationY",
  )

  val indicatorSize = 56.dp
  val indicatorSizePx = remember(density) { with(density) { indicatorSize.toPx() } }

  Box(
    modifier = modifier
      .clipToBounds()
      .then(pointerModifier)
      .pullToRefresh(
        isRefreshing = isRefreshing.value,
        state = state,
        enabled = pullRefreshEnabled,
        threshold = refreshThreshold,
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
