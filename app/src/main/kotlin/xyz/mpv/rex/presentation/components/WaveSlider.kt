package xyz.mpv.rex.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.utils.HapticType
import xyz.mpv.rex.utils.HapticUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WaveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }
    val stroke = remember(strokeWidthPx) { Stroke(width = strokeWidthPx, cap = StrokeCap.Round) }

    val normalized = if (valueRange.endInclusive == valueRange.start) 0f
    else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    var isInteracting by remember { mutableStateOf(false) }
    var lastHapticValue by remember { mutableIntStateOf(Int.MIN_VALUE) }

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "thumb_morph"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = tween(300),
        label = "wave_amp"
    )

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            progress = { normalized },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadius),
            color = activeTrackColor,
            trackColor = inactiveTrackColor,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { p -> if (p > 0f) animatedAmplitude else 0f },
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStart = thumbRadiusPx
            val trackEnd = size.width - thumbRadiusPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2

            val currentWidth = androidx.compose.ui.util.lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.3f, thumbInteractionFraction)
            val currentHeight = androidx.compose.ui.util.lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val thumbX = (trackStart + (trackWidth * normalized)).coerceIn(currentWidth / 2f, size.width - currentWidth / 2f)

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX - currentWidth / 2f, thumbY - currentHeight / 2f),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange) {
                    if (!enabled) return@pointerInput
                    fun calcValue(x: Float): Float {
                        val norm = ((x - thumbRadiusPx) / (size.width - thumbRadiusPx * 2)).coerceIn(0f, 1f)
                        return valueRange.start + norm * (valueRange.endInclusive - valueRange.start)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isInteracting = true
                        down.consume()
                        val v = calcValue(down.position.x)
                        latestOnValueChange(v)
                        lastHapticValue = v.roundToInt()
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.position != change.previousPosition) {
                                change.consume()
                                val curV = calcValue(change.position.x)
                                latestOnValueChange(curV)
                                val tick = curV.roundToInt()
                                if (tick != lastHapticValue) {
                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                    lastHapticValue = tick
                                }
                            }
                        }
                        isInteracting = false
                        latestOnValueChangeFinished?.invoke()
                    }
                }
        )
    }
}
