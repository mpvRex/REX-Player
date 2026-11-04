package xyz.mpv.rex.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val EDGE_RAMP_STEPS = 10

// Smoothstep polynomial curve stops: t * t * (3 - 2 * t)
private val FadeInStops: Array<Pair<Float, Color>> = Array(EDGE_RAMP_STEPS + 1) { i ->
    val t = i / EDGE_RAMP_STEPS.toFloat()
    val eased = t * t * (3f - 2f * t)
    t to Color.Black.copy(alpha = eased)
}

private val FadeOutStops: Array<Pair<Float, Color>> = Array(EDGE_RAMP_STEPS + 1) { i ->
    val t = i / EDGE_RAMP_STEPS.toFloat()
    val eased = 1f - t * t * (3f - 2f * t)
    t to Color.Black.copy(alpha = eased)
}

fun Modifier.edgeBlend(
    lazyListState: LazyListState,
    fadeHeight: Dp = 24.dp,
): Modifier = composed {
    val canScrollBackward by remember { derivedStateOf { lazyListState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { lazyListState.canScrollForward } }

    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeHeight.toPx()

            if (canScrollBackward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeInStops, startY = 0f, endY = fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (canScrollForward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeOutStops, startY = size.height - fadePx, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

fun Modifier.edgeBlend(
    lazyGridState: LazyGridState,
    fadeHeight: Dp = 24.dp,
): Modifier = composed {
    val canScrollBackward by remember { derivedStateOf { lazyGridState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { lazyGridState.canScrollForward } }

    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeHeight.toPx()

            if (canScrollBackward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeInStops, startY = 0f, endY = fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (canScrollForward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeOutStops, startY = size.height - fadePx, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

fun Modifier.edgeBlend(
    scrollState: ScrollState,
    fadeHeight: Dp = 24.dp,
): Modifier = composed {
    val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeHeight.toPx()

            if (canScrollBackward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeInStops, startY = 0f, endY = fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (canScrollForward) {
                drawRect(
                    brush = Brush.verticalGradient(colorStops = FadeOutStops, startY = size.height - fadePx, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
