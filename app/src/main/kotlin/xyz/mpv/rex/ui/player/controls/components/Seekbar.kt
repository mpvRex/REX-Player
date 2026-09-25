package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import xyz.mpv.rex.ui.player.controls.LocalPlayerButtonsClickEvent
import xyz.mpv.rex.ui.theme.spacing
import xyz.mpv.rex.preferences.SeekbarStyle
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.preference.collectAsState

@Composable
fun SeekbarWithTimers(
  position: () -> Float,
  duration: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  timersInverted: Pair<Boolean, Boolean>,
  positionTimerOnClick: () -> Unit,
  durationTimerOnCLick: () -> Unit,
  chapters: ImmutableList<Segment>,
  paused: Boolean,
  readAheadValue: () -> Float = position,
  seekbarStyle: SeekbarStyle = SeekbarStyle.Wavy,
  loopStart: Float? = null,
  loopEnd: Float? = null,
  isGestureSeeking: Boolean = false,
  isCancelActive: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isUserInteracting by remember { mutableStateOf(false) }
  var userPosition by remember { mutableFloatStateOf(position()) }

  // Animated position for smooth transitions
  val animatedPosition = remember { Animatable(position()) }
  val scope = rememberCoroutineScope()
  var lastInteractionTime by remember { mutableLongStateOf(0L) }

  // Read Toggle for Bounce Animation from Preferences
  val appearancePrefs = koinInject<AppearancePreferences>()
  val enableBounceAnimation by appearancePrefs.enableBounceAnimation.collectAsState()
  val enableGlassPlayerControls by appearancePrefs.enableGlassPlayerControls.collectAsState()
  val enableGlassSeekbar by appearancePrefs.enableGlassSeekbarBackground.collectAsState()
  val isGlassActive = enableGlassPlayerControls && enableGlassSeekbar

  // Determine if the seekbar should be actively squeezed
  val shouldSqueeze = isUserInteracting || isGestureSeeking
  
  val squeezeAnim = remember { Animatable(1f) }

  // Trigger animation whenever the interaction state change
  LaunchedEffect(shouldSqueeze, enableBounceAnimation) {
    if (!enableBounceAnimation) {
      squeezeAnim.snapTo(1f)
      return@LaunchedEffect
    }
    if (shouldSqueeze) {
      squeezeAnim.animateTo(
        targetValue = 0.75f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMedium
        )
      )
    } else {
      kotlinx.coroutines.delay(150)
      squeezeAnim.animateTo(
        targetValue = 1f,
        animationSpec = spring(
          dampingRatio = 0.4f,
          stiffness = Spring.StiffnessLow
        )
      )
    }
  }

  val squeezeScale = squeezeAnim.value

  // Only animate position updates when user is not interacting
  // Using snapshotFlow to avoid registering a state read in this composable's scope
  LaunchedEffect(Unit) {
    snapshotFlow { position() }.collect { currentPosVal ->
      if (!isUserInteracting && currentPosVal != animatedPosition.value) {
        if (currentPosVal <= 0.05f) {
          animatedPosition.snapTo(0f)
          userPosition = 0f
          return@collect
        }

        // If we recently interacted (within 2s) and the position is significantly different from the seeked target (>10s),
        // assume it's the old position and ignore it to prevent "back and forth" glitches.
        val timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime
        if (timeSinceInteraction < 2000 && kotlin.math.abs(currentPosVal - userPosition) > 10f) {
          return@collect
        }

        animatedPosition.animateTo(
          targetValue = currentPosVal,
          animationSpec =
            tween(
              durationMillis = 200,
              easing = LinearEasing,
            ),
        )
      }
    }
  }

  val rowModifier = if (isGlassActive) {
    modifier
      .height(54.dp)
      .padding(horizontal = 16.dp)
      .glassSurface(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White.copy(alpha = 0.05f),
        borderColor = Color.White.copy(alpha = 0.15f),
        borderWidth = 1.dp,
        outerShadowColor = Color.Black.copy(alpha = 0.00f),
        outerShadowBlur = 0.dp,
        outerShadowOffsetX = 0.dp,
        outerShadowOffsetY = 0.dp,
        innerHighlightColor = Color.White.copy(alpha = 0.35f),
        innerHighlightBlur = 5.dp,
        innerHighlightOffsetX = (-2).dp,
        innerHighlightOffsetY = (-2).dp,
        innerShadowColor = Color.Black.copy(alpha = 0.35f),
        innerShadowBlur = 5.dp,
        innerShadowOffsetX = 2.dp,
        innerShadowOffsetY = 2.dp
      )
      .padding(horizontal = 14.dp)
  } else {
    modifier.height(48.dp)
  }

  Row(
    modifier = rowModifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    VideoTimer(
      value = { if (isUserInteracting) userPosition else position() },
      timersInverted.first,
      onClick = {
        clickEvent()
        positionTimerOnClick()
      },
      modifier = if (isGlassActive) Modifier.wrapContentWidth() else Modifier.width(92.dp),
    )

    // Seekbar
    Box(
      modifier =
        Modifier
          .weight(1f)
          .height(48.dp)
          .graphicsLayer {
            scaleY = squeezeScale
          },
      contentAlignment = Alignment.Center,
    ) {

      when (seekbarStyle) {
        SeekbarStyle.Standard -> {
          StandardSeekbar(
            position = { if (isUserInteracting) userPosition else animatedPosition.value },
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isCancelActive = isCancelActive,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              lastInteractionTime = System.currentTimeMillis()
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
        SeekbarStyle.Wavy -> {
          SquigglySeekbar(
            position = { if (isUserInteracting) userPosition else animatedPosition.value },
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Wavy,
            isCancelActive = isCancelActive,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              lastInteractionTime = System.currentTimeMillis()
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
        SeekbarStyle.Circular -> {
           SquigglySeekbar(
            position = { if (isUserInteracting) userPosition else animatedPosition.value },
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Circular,
            isCancelActive = isCancelActive,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              lastInteractionTime = System.currentTimeMillis()
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
        SeekbarStyle.Simple -> {
             SquigglySeekbar(
            position = { if (isUserInteracting) userPosition else animatedPosition.value },
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = false,
            seekbarStyle = SeekbarStyle.Simple, 
            isCancelActive = isCancelActive,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              lastInteractionTime = System.currentTimeMillis()
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
        SeekbarStyle.Thick -> {
          StandardSeekbar(
            position = { if (isUserInteracting) userPosition else animatedPosition.value },
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            seekbarStyle = SeekbarStyle.Thick,
            isCancelActive = isCancelActive,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              isUserInteracting = false
              onValueChangeFinished()
            },
            loopStart = loopStart,
            loopEnd = loopEnd,
          )
        }

      }
    }

    VideoTimer(
      value = { if (timersInverted.second) position() - duration else duration },
      isInverted = timersInverted.second,
      onClick = {
        clickEvent()
        durationTimerOnCLick()
      },
      modifier = if (isGlassActive) Modifier.wrapContentWidth() else Modifier.width(92.dp),
    )
  }
}

@Composable
private fun SquigglySeekbar(
  position: () -> Float,
  duration: Float,
  readAheadValue: () -> Float,
  chapters: ImmutableList<Segment>,
  isPaused: Boolean,
  isScrubbing: Boolean,
  useWavySeekbar: Boolean,
  seekbarStyle: SeekbarStyle,
  isCancelActive: Boolean = false,
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  loopStart: Float? = null,
  loopEnd: Float? = null,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val whiteSeekBar by playerPreferences.whiteSeekBar.collectAsState()
  val showSeekbarChapters by playerPreferences.showSeekbarChapters.collectAsState()
  val showSeekbarReadAhead by playerPreferences.showSeekbarReadAhead.collectAsState()
  val primaryColor = if (whiteSeekBar) Color.White else MaterialTheme.colorScheme.primary
  val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

  val gesturePreferences = koinInject<GesturePreferences>()
  val preventSeekbarTap by gesturePreferences.preventSeekbarTap.collectAsState()
  val useRelativeSeeking by gesturePreferences.useRelativeSeeking.collectAsState()

  // Manual Interaction State Tracking
  var isPressed by remember { mutableStateOf(false) }
  var isDragged by remember { mutableStateOf(false) }
  var dragStartValue by remember { mutableFloatStateOf(0f) }
  val thumbScale by animateFloatAsState(
    targetValue = if (isCancelActive) 0.7f else 1f,
    label = "thumbScale"
  )
  val isInteracting = isPressed || isDragged || isScrubbing 

  // Animation state
  var phaseOffset by remember { mutableFloatStateOf(0f) }
  var heightFraction by remember { mutableFloatStateOf(1f) }

  val scope = rememberCoroutineScope()

  // Wave parameters
  val waveLength = 80f
  val lineAmplitude = if (useWavySeekbar) 6f else 0f
  val phaseSpeed = 10f // px per second
  val transitionPeriods = 1.5f
  val minWaveEndpoint = 0f
  val matchedWaveEndpoint = 1f
  val transitionEnabled = true

  // Animate height fraction based on paused state and scrubbing state
  LaunchedEffect(isPaused, isScrubbing, useWavySeekbar) {
    if (!useWavySeekbar) {
      heightFraction = 0f
      return@LaunchedEffect
    }

    val shouldFlatten = isPaused || isScrubbing
    val targetHeight = if (shouldFlatten) 0f else 1f
    val duration = if (shouldFlatten) 550 else 800
    val startDelay = if (shouldFlatten) 0L else 60L

    kotlinx.coroutines.delay(startDelay)

    val animator = Animatable(heightFraction)
    animator.animateTo(
      targetValue = targetHeight,
      animationSpec =
        tween(
          durationMillis = duration,
          easing = LinearEasing,
        ),
    ) {
      heightFraction = value
    }
  }

  // Animate wave movement only when not paused
  LaunchedEffect(isPaused, useWavySeekbar) {
    if (isPaused || !useWavySeekbar) return@LaunchedEffect

    var lastFrameTime = System.currentTimeMillis()
    while (isActive) {
      val currentTime = System.currentTimeMillis()
      val deltaTime = (currentTime - lastFrameTime) / 1000f
      phaseOffset += deltaTime * phaseSpeed
      phaseOffset %= waveLength
      lastFrameTime = currentTime
      kotlinx.coroutines.delay(33) // Target ~30 FPS
    }
  }

  val currentPosition by rememberUpdatedState(position)
  val currentDuration by rememberUpdatedState(duration)

  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            },
            onTap = { offset ->
                if (preventSeekbarTap) return@detectTapGestures
                val newPosition = (offset.x / size.width) * currentDuration
                onSeek(newPosition.coerceIn(0f, currentDuration))
                onSeekFinished()
            }
          )
        }
        .pointerInput(Unit) {
          var accumulatedDragPx = 0f

          detectDragGestures(
            onDragStart = { 
                isDragged = true
                dragStartValue = currentPosition()
                accumulatedDragPx = 0f
            },
            onDragEnd = { 
                isDragged = false
                onSeekFinished() 
            },
            onDragCancel = { 
                isDragged = false
                onSeekFinished() 
            },
          ) { change, dragAmount ->
            change.consume()
            if (useRelativeSeeking) {
              // Relative seeking: calculate position relative to drag start
              accumulatedDragPx += dragAmount.x
              if (currentDuration > 0f) {
                val newPosition = dragStartValue + (accumulatedDragPx / size.width) * currentDuration
                onSeek(newPosition.coerceIn(0f, currentDuration))
              }
            } else {
              // Absolute seeking: use current finger position
              val newPosition = (change.position.x / size.width) * currentDuration
              onSeek(newPosition.coerceIn(0f, currentDuration))
            }
          }
        },
  ) {
    val strokeWidth = 5.dp.toPx()
    val trackPosition = if (isDragged) dragStartValue else position()
    val progress = if (duration > 0f) (trackPosition / duration).coerceIn(0f, 1f) else 0f
    val thumbProgress = if (duration > 0f) (position() / duration).coerceIn(0f, 1f) else 0f
    val readAheadProgress = if (duration > 0f) (readAheadValue() / duration).coerceIn(0f, 1f) else 0f
    val totalWidth = size.width
    val totalProgressPx = totalWidth * progress
    val totalThumbPx = totalWidth * thumbProgress
    val totalReadAheadPx = totalWidth * readAheadProgress
    val centerY = size.height / 2f

    // Calculate wave progress
    val waveProgressPx =
      if (!transitionEnabled || progress > matchedWaveEndpoint) {
        totalWidth * progress
      } else {
        val t = (progress / matchedWaveEndpoint).coerceIn(0f, 1f)
        totalWidth * (minWaveEndpoint + (matchedWaveEndpoint - minWaveEndpoint) * t)
      }

    // Helper function to compute amplitude
    fun computeAmplitude(
      x: Float,
      sign: Float,
    ): Float =
      if (transitionEnabled) {
        val length = transitionPeriods * waveLength
        val coeff = ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
        sign * heightFraction * lineAmplitude * coeff
      } else {
        sign * heightFraction * lineAmplitude
      }

    val waveStart = -phaseOffset - waveLength / 2f

    // Draw path up to progress position using clipping
    val clipTop = lineAmplitude + strokeWidth
    val gapHalf = 1.dp.toPx()

    fun drawPathWithGaps(
      startX: Float,
      endX: Float,
      color: Color,
    ) {
      if (endX <= startX) return
      
      // Build wavy path dynamically up to endX to get a natural round cap at the end
      val path = Path()
      path.moveTo(waveStart, centerY)

      var currentX = waveStart
      var waveSign = 1f
      var currentAmp = computeAmplitude(currentX, waveSign)
      val dist = waveLength / 2f

      while (currentX < endX) {
        waveSign = -waveSign
        val nextX = currentX + dist
        
        if (nextX >= endX) {
          val remainingDist = endX - currentX
          val targetAmp = computeAmplitude(endX, waveSign)
          val controlX = currentX + remainingDist / 2f
          path.cubicTo(
            controlX,
            centerY + currentAmp,
            controlX,
            centerY + targetAmp,
            endX,
            centerY + targetAmp,
          )
          break
        }

        val nextAmp = computeAmplitude(nextX, waveSign)
        val midX = currentX + dist / 2f

        path.cubicTo(
          midX,
          centerY + currentAmp,
          midX,
          centerY + nextAmp,
          nextX,
          centerY + nextAmp,
        )

        currentAmp = nextAmp
        currentX = nextX
      }

      if (duration <= 0f) {
        clipRect(
          left = startX,
          top = centerY - clipTop,
          right = endX + strokeWidth,
          bottom = centerY + clipTop,
        ) {
          drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
        }
        return
      }
      val gaps =
        if (showSeekbarChapters) {
          chapters
            .map { (it.start / duration).coerceIn(0f, 1f) * totalWidth }
            .filter { it in startX..endX }
            .sorted()
            .map { x -> (x - gapHalf).coerceAtLeast(startX) to (x + gapHalf).coerceAtMost(endX) }
        } else emptyList()

      var segmentStart = startX
      for ((gapStart, gapEnd) in gaps) {
        if (gapStart > segmentStart) {
          clipRect(
            left = segmentStart,
            top = centerY - clipTop,
            right = gapStart,
            bottom = centerY + clipTop,
          ) {
            drawPath(
              path = path,
              color = color,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }
        segmentStart = gapEnd
      }
      if (segmentStart < endX) {
        clipRect(
          left = segmentStart,
          top = centerY - clipTop,
          right = endX + strokeWidth,
          bottom = centerY + clipTop,
        ) {
          drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
        }
      }
    }

    // Played segment
    drawPathWithGaps(0f, totalProgressPx, primaryColor)

    // Buffer segment
    if (showSeekbarReadAhead && totalReadAheadPx > totalProgressPx) {
      val bufferAlpha = 0.5f
      drawPathWithGaps(totalProgressPx, totalReadAheadPx, primaryColor.copy(alpha = bufferAlpha))
    }

    val unplayedStart = if (showSeekbarReadAhead) maxOf(totalProgressPx, totalReadAheadPx) else totalProgressPx

    if (transitionEnabled) {
      val disabledAlpha = 77f / 255f
      drawPathWithGaps(unplayedStart, totalWidth, primaryColor.copy(alpha = disabledAlpha))
    } else {
      drawLine(
        color = surfaceVariant.copy(alpha = 0.4f),
        start = Offset(unplayedStart, centerY),
        end = Offset(totalWidth, centerY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }

    // Draw round cap
    val startAmp = kotlin.math.cos(kotlin.math.abs(waveStart) / waveLength * (2f * kotlin.math.PI.toFloat()))
    drawCircle(
      color = primaryColor,
      radius = strokeWidth / 2f,
      center = Offset(0f, centerY + startAmp * lineAmplitude * heightFraction),
    )

// SquigglySeekbar (Circular Thumb)
    if (seekbarStyle == SeekbarStyle.Circular) {
         val thumbRadius = 10.dp.toPx() * thumbScale
         drawCircle(
            color = primaryColor,
            radius = thumbRadius,
            center = Offset(totalThumbPx, centerY)
         )
    } else {
        // Vertical Bar (Wavy/Simple Thumb)
        val barHalfHeight = (lineAmplitude + strokeWidth) * thumbScale
        val barWidth = 5.dp.toPx() * thumbScale

        if (barHalfHeight > 0.5f) {
            drawLine(
              color = primaryColor,
              start = Offset(totalThumbPx, centerY - barHalfHeight),
              end = Offset(totalThumbPx, centerY + barHalfHeight),
              strokeWidth = barWidth,
              cap = StrokeCap.Round,
            )
        }
    }

    // A-B Loop Indicators for SquigglySeekbar
    if (loopStart != null || loopEnd != null) {
      val loopColor = Color(0xFFFFB300)
      val markerWidth = 2.dp.toPx()

      if (loopStart != null && duration > 0f) {
        val startPx = (loopStart / duration).coerceIn(0f, 1f) * totalWidth
        drawLine(
          color = loopColor,
          start = Offset(startPx, centerY - lineAmplitude - strokeWidth),
          end = Offset(startPx, centerY + lineAmplitude + strokeWidth),
          strokeWidth = markerWidth,
        )
      }

      if (loopEnd != null && duration > 0f) {
        val endPx = (loopEnd / duration).coerceIn(0f, 1f) * totalWidth
        drawLine(
          color = loopColor,
          start = Offset(endPx, centerY - lineAmplitude - strokeWidth),
          end = Offset(endPx, centerY + lineAmplitude + strokeWidth),
          strokeWidth = markerWidth,
        )
      }

      if (loopStart != null && loopEnd != null && duration > 0f) {
        val minPx = (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * totalWidth
        val maxPx = (maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * totalWidth
        drawRect(
          color = loopColor.copy(alpha = 0.2f),
          topLeft = Offset(minPx, centerY - lineAmplitude - strokeWidth),
          size = Size(maxPx - minPx, (lineAmplitude + strokeWidth) * 2),
        )
      }
    }
  }
}

@Composable
fun VideoTimer(
  value: () -> Float,
  isInverted: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  val appearancePreferences = koinInject<AppearancePreferences>()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  
  val timeText = Utils.prettyTime(value().toInt(), isInverted)
  
  Text(
    modifier =
      modifier
        .fillMaxHeight()
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          onClick = onClick,
        )
        .wrapContentHeight(Alignment.CenterVertically),
    text = timeText,
    color = if (matchTheme) MaterialTheme.colorScheme.primary else Color.White,
    textAlign = TextAlign.Center,
  )
}

@Composable
fun StandardSeekbar(
    position: () -> Float,
    duration: Float,
    readAheadValue: () -> Float,
    chapters: ImmutableList<Segment>,
    isPaused: Boolean = false,
    isScrubbing: Boolean = false,
    useWavySeekbar: Boolean = false,
    seekbarStyle: SeekbarStyle = SeekbarStyle.Standard,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    loopStart: Float? = null,
    loopEnd: Float? = null,
    isCancelActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playerPreferences = koinInject<PlayerPreferences>()
    val whiteSeekBar by playerPreferences.whiteSeekBar.collectAsState()
    val showSeekbarChapters by playerPreferences.showSeekbarChapters.collectAsState()
    val showSeekbarReadAhead by playerPreferences.showSeekbarReadAhead.collectAsState()
    val primaryColor = if (whiteSeekBar) Color.White else MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    
    val isThick = seekbarStyle == SeekbarStyle.Thick
    val trackHeightDp = if (isThick) 16.dp else 8.dp
    val thumbWidth = 6.dp
    val thumbHeight = if (isThick) 16.dp else 24.dp
    val thumbShape = if (isThick) RoundedCornerShape(thumbWidth / 2) else CircleShape

    val gesturePreferences = koinInject<GesturePreferences>()
    val preventSeekbarTap by gesturePreferences.preventSeekbarTap.collectAsState()
    val useRelativeSeeking by gesturePreferences.useRelativeSeeking.collectAsState()

    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)

    val thumbScale by animateFloatAsState(
        targetValue = if (isCancelActive) 0.7f else 1f,
        label = "thumbScale"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    var userPosition by remember { mutableFloatStateOf(position()) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Update userPosition when not interacting
    if (!isUserInteracting) {
        userPosition = position()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (preventSeekbarTap) return@detectTapGestures
                        val newPosition = (offset.x / size.width) * currentDuration
                        onSeek(newPosition.coerceIn(0f, currentDuration))
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        isUserInteracting = true
                        dragStartValue = currentPosition()
                        accumulatedDragPx = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        isUserInteracting = false
                        onSeekFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        isUserInteracting = false
                        onSeekFinished()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val newPosition = if (useRelativeSeeking) {
                        // Relative seeking: calculate position relative to drag start
                        accumulatedDragPx += dragAmount.x
                        if (currentDuration > 0f) {
                            dragStartValue + (accumulatedDragPx / size.width) * currentDuration
                        } else {
                            currentPosition()
                        }
                    } else {
                        // Absolute seeking: use current finger position
                        (change.position.x / size.width) * currentDuration
                    }
                    userPosition = newPosition.coerceIn(0f, currentDuration)
                    onSeek(userPosition)
                }
            }
    ) {
        val safeDuration = duration.coerceAtLeast(0.1f)
        Slider(
            value = (if (isUserInteracting) userPosition else position()).coerceIn(0f, safeDuration),
            onValueChange = { /* Handled by custom gestures */ },
            onValueChangeFinished = { /* Handled by custom gestures */ },
            valueRange = 0f..safeDuration,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = false, // Disable built-in interaction
            track = { sliderState ->
            val disabledAlpha = 0.3f
            val bufferAlpha = 0.5f

            val appearancePreferences = koinInject<AppearancePreferences>()
            val enableGlassPlayerControls by appearancePreferences.enableGlassPlayerControls.collectAsState()
            val enableGlassSeekbar by appearancePreferences.enableGlassSeekbarBackground.collectAsState()
            val isGlassActive = enableGlassPlayerControls && enableGlassSeekbar
            val outerRadiusDp = trackHeightDp / 2

            val trackModifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp)
                .background(
                    color = if (isGlassActive) Color.White.copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = disabledAlpha),
                    shape = RoundedCornerShape(outerRadiusDp)
                )

            Box(
                modifier = trackModifier
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val min = sliderState.valueRange.start
                    val max = sliderState.valueRange.endInclusive
                    val range = (max - min).takeIf { it > 0f } ?: 1f

                    val trackValue = if (isDragging) dragStartValue else sliderState.value
                    val playedFraction = ((trackValue - min) / range).coerceIn(0f, 1f)
                    val readAheadFraction = ((readAheadValue() - min) / range).coerceIn(0f, 1f)

                    val playedPx = size.width * playedFraction
                    val readAheadPx = size.width * readAheadFraction
                    val trackHeight = size.height
                    
                    // Radius for the outer ends of the seekbar
                    val outerRadius = trackHeight / 2f
                    
                    // MODIFIED: For Thick style, inner corners now match the outer rounding
                    val innerRadius = if (isThick) outerRadius else 2.dp.toPx()
                    
                    val thumbTrackGapSize = 14.dp.toPx()
                    val gapHalf = thumbTrackGapSize / 2f
                    val chapterGapHalf = 1.dp.toPx()
                    
                    val thumbFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                    val thumbPx = size.width * thumbFraction
                    val thumbGapStart = (thumbPx - gapHalf).coerceIn(0f, size.width)
                    val thumbGapEnd = (thumbPx + gapHalf).coerceIn(0f, size.width)
                    
                    val chapterGaps = if (showSeekbarChapters && duration > 0f) {
                        chapters
                            .map { (it.start / duration).coerceIn(0f, 1f) * size.width }
                            .filter { it > 0f && it < size.width }
                            .map { x -> (x - chapterGapHalf) to (x + chapterGapHalf) }
                    } else emptyList()

                    val allGaps = (chapterGaps + (thumbGapStart to thumbGapEnd))
                        .filter { it.first < it.second }
                    
                    fun drawSegment(startX: Float, endX: Float, color: Color) {
                        if (endX - startX < 0.5f) return
                        
                        val path = Path()
                        val isOuterLeft = startX <= 0.5f
                        val isInnerLeft = kotlin.math.abs(startX - thumbGapEnd) < 0.5f
                        
                        val cornerRadiusLeft = when {
                            isOuterLeft -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                            isInnerLeft -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                            else -> androidx.compose.ui.geometry.CornerRadius.Zero
                        }

                        val isOuterRight = endX >= size.width - 0.5f
                        val isInnerRight = kotlin.math.abs(endX - thumbGapStart) < 0.5f
                        val isBufferRight = kotlin.math.abs(endX - readAheadPx) < 0.5f && readAheadPx > playedPx && showSeekbarReadAhead

                        val cornerRadiusRight = when {
                            isOuterRight || isBufferRight -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                            isInnerRight -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                            else -> androidx.compose.ui.geometry.CornerRadius.Zero
                        }
                        
                        path.addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = startX,
                                top = 0f,
                                right = endX,
                                bottom = trackHeight,
                                topLeftCornerRadius = cornerRadiusLeft,
                                bottomLeftCornerRadius = cornerRadiusLeft,
                                topRightCornerRadius = cornerRadiusRight,
                                bottomRightCornerRadius = cornerRadiusRight
                            )
                         )
                         drawPath(path, color)
                     }
                     
                     fun drawRangeWithGaps(
                         rangeStart: Float, 
                         rangeEnd: Float, 
                         gaps: List<Pair<Float, Float>>, 
                         color: Color
                     ) {
                         if (rangeEnd <= rangeStart) return
                         val relevantGaps = gaps
                             .filter { (gStart, gEnd) -> gEnd > rangeStart && gStart < rangeEnd }
                             .sortedBy { it.first }
                         
                         var currentPos = rangeStart
                         for ((gStart, gEnd) in relevantGaps) {
                             val segmentEnd = gStart.coerceAtMost(rangeEnd)
                             if (segmentEnd > currentPos) {
                                 drawSegment(currentPos, segmentEnd, color)
                             }
                             currentPos = gEnd.coerceAtLeast(currentPos)
                         }
                         if (currentPos < rangeEnd) {
                             drawSegment(currentPos, rangeEnd, color)
                         }
                     }
                     
                     // 1. Unplayed Background
                     if (!isGlassActive) {
                         drawRangeWithGaps(playedPx, size.width, allGaps, primaryColor.copy(alpha = disabledAlpha))
                     }
                     
                     // 2. Buffer
                     if (showSeekbarReadAhead && readAheadPx > playedPx) {
                         drawRangeWithGaps(playedPx, readAheadPx, allGaps, primaryColor.copy(alpha = bufferAlpha))
                     }
                     
                     // 3. Played
                     if (playedPx > 0f) {
                         drawRangeWithGaps(0f, playedPx, allGaps, primaryColor)
                     }
 
                     // 3. A-B Loop Indicators
                     if (loopStart != null || loopEnd != null) {
                         val loopColor = Color(0xFFFFB300) // Amber/Gold color for loop
                         val markerWidth = 2.dp.toPx()
                         
                         // Draw loop start marker
                         if (loopStart != null) {
                             val startPx = (loopStart / duration).coerceIn(0f, 1f) * size.width
                             drawLine(
                                 color = loopColor,
                                 start = Offset(startPx, 0f),
                                 end = Offset(startPx, size.height),
                                 strokeWidth = markerWidth
                             )
                         }
 
                         // Draw loop end marker
                         if (loopEnd != null) {
                             val endPx = (loopEnd / duration).coerceIn(0f, 1f) * size.width
                             drawLine(
                                 color = loopColor,
                                 start = Offset(endPx, 0f),
                                 end = Offset(endPx, size.height),
                                 strokeWidth = markerWidth
                             )
                         }
 
                         // Draw connected segment if both are set
                         if (loopStart != null && loopEnd != null) {
                             val minPx = (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width
                             val maxPx = (maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width
                             
                             // Draw a semi-transparent overlay between A and B
                             drawRect(
                                 color = loopColor.copy(alpha = 0.3f),
                                 topLeft = Offset(minPx, 0f),
                                 size = Size(maxPx - minPx, size.height)
                             )
                         }
                     }
                 }
             }
         },
        thumb = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .background(primaryColor, thumbShape)
            )
        }
        )
    }
}

@Preview
@Composable
private fun PreviewSeekBar() {
  SeekbarWithTimers(
    position = { 30f },
    duration = 180f,
    onValueChange = {},
    onValueChangeFinished = {},
    timersInverted = Pair(false, true),
    positionTimerOnClick = {},
    durationTimerOnCLick = {},
    chapters = persistentListOf(),
    paused = false,
    readAheadValue = { 90f },
  )
}
@Composable
fun SeekbarPreview(
  style: SeekbarStyle,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "seekbar_preview")
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(3000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "progress"
  )
  val duration = 100f
  val position = progress * duration

  // Dummy chapters for preview to visualize chapter separation
  val dummyChapters = persistentListOf(
    Segment(name = "Chapter 1", start = 0f),
    Segment(name = "Chapter 2", start = duration * 0.35f),
    Segment(name = "Chapter 3", start = duration * 0.65f),
  )
  
  Box(
    modifier = modifier
      .height(32.dp),
    contentAlignment = Alignment.Center
  ) {
    // Seekbar content
    when (style) {
      SeekbarStyle.Standard -> {
        StandardSeekbar(
          position = { position },
          duration = duration,
          readAheadValue = { position },
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Standard,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Wavy -> {
        SquigglySeekbar(
          position = { position },
          duration = duration,
          readAheadValue = { position },
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Wavy,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Circular -> {
        SquigglySeekbar(
          position = { position },
          duration = duration,
          readAheadValue = { position },
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Circular,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Simple -> {
           SquigglySeekbar(
          position = { position },
          duration = duration,
          readAheadValue = { position },
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = false,
          seekbarStyle = SeekbarStyle.Simple,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Thick -> {
        StandardSeekbar(
          position = { position },
          duration = duration,
          readAheadValue = { position },
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Thick,
          onSeek = {},
          onSeekFinished = {},
        )
      }
    }
    
    // Invisible overlay that intercepts all touch events and triggers onClick
    if (onClick != null) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // No ripple on overlay itself
            onClick = onClick
          )
      )
    }
  }
}
