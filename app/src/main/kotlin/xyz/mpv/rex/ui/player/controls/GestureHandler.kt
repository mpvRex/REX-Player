package xyz.mpv.rex.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.controls.components.glassSurface
import xyz.mpv.rex.presentation.components.LeftSideOvalShape
import xyz.mpv.rex.presentation.components.RightSideOvalShape
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerUpdates
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.PlayerTutorialManager
import xyz.mpv.rex.ui.player.SingleActionGesture
import xyz.mpv.rex.ui.theme.playerRippleConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

@Suppress("CyclomaticComplexMethod", "MultipleEmitters")
@Composable
fun GestureHandler(
  viewModel: PlayerViewModel,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  val gesturePreferences = koinInject<GesturePreferences>()
  val subtitlesPreferences = koinInject<SubtitlesPreferences>()
  val playerTutorialManager = koinInject<PlayerTutorialManager>()
  val panelShown by viewModel.panelShown.collectAsState()
  val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val preciseDuration by viewModel.preciseDuration.collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
  val useSingleTapForCenter by gesturePreferences.useSingleTapForCenter.collectAsState()
  val useSingleTapForLeftRight by gesturePreferences.useSingleTapForLeftRight.collectAsState()
  val reverseDoubleTap by gesturePreferences.reverseDoubleTap.collectAsState()
  val doubleTapSeekAreaWidth by gesturePreferences.doubleTapSeekAreaWidth.collectAsState()
  var isDoubleTapSeeking by remember { mutableStateOf(false) }
  var longPressStartTime by remember { mutableStateOf(0L) }
  LaunchedEffect(seekAmount) {
    delay(800)
    isDoubleTapSeeking = false
    viewModel.updateSeekAmount(0)
    viewModel.updateSeekText(null)
    delay(100)
    viewModel.hideSeekBar()
  }
  val multipleSpeedGesture by playerPreferences.holdForMultipleSpeed.collectAsState()
  val rememberLongPressSpeed by playerPreferences.rememberLongPressSpeed.collectAsState()
  val showDynamicSpeedOverlay by playerPreferences.showDynamicSpeedOverlay.collectAsState()
  val brightnessGesture by playerPreferences.brightnessGesture.collectAsState()
  val volumeGesture by playerPreferences.volumeGesture.collectAsState()
  val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
  val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
  val panAndZoomEnabled by playerPreferences.panAndZoomEnabled.collectAsState()
  val advancedZoomEnabled by viewModel.advancedZoomEnabled.collectAsState()
  val horizontalSwipeToSeek by playerPreferences.horizontalSwipeToSeek.collectAsState()
  val swipeToSubtitleSeek by playerPreferences.swipeToSubtitleSeek.collectAsState()
  val moveSubtitleByDragging by playerPreferences.moveSubtitleByDragging.collectAsState()
  val horizontalSwipeSensitivity by playerPreferences.horizontalSwipeSensitivity.collectAsState()
  var isLongPressing by remember { mutableStateOf(false) }
  var isDynamicSpeedControlActive by remember { mutableStateOf(false) }
  var dynamicSpeedStartX by remember { mutableStateOf(0f) }
  var dynamicSpeedStartValue by remember { mutableStateOf(2f) }
  var lastAppliedSpeed by remember { mutableStateOf(2f) }
  var hasSwipedEnough by remember { mutableStateOf(false) }
  var longPressTriggeredDuringTouch by remember { mutableStateOf(false) }
  var isVerticalGestureActive by remember { mutableStateOf(false) }
  var hasIncrementedSpeedLockHint by remember { mutableStateOf(false) }
  val currentVolume by viewModel.currentVolume.collectAsState()
  val currentMPVVolume by MPVLib.propInt["volume"].collectAsState()
  val currentBrightness by viewModel.currentBrightness.collectAsState()
  val volumeBoostingCap = audioPreferences.volumeBoostCap.get()
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()

  // Isolated double-tap state tracking
  var tapCount by remember { mutableStateOf(0) }
  var lastTapTime by remember { mutableStateOf(0L) }
  var lastTapPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
  var lastTapRegion by remember { mutableStateOf<String?>(null) }
  var pendingSingleTapRegion by remember { mutableStateOf<String?>(null) }
  var pendingSingleTapPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
  var pendingTapInExclusionZone by remember { mutableStateOf(false) }
  val doubleTapTimeout = 250L
  val multiTapContinueWindow = 650L

  // Multi-tap seeking state
  var lastSeekRegion by remember { mutableStateOf<String?>(null) }
  var lastSeekTime by remember { mutableStateOf<Long?>(null) }

  // Auto-reset tap count on timeout and execute single tap if no double tap detected
  LaunchedEffect(tapCount, longPressTriggeredDuringTouch) {
    if (tapCount == 1) {
      delay(doubleTapTimeout)
      // Timeout occurred, execute single tap action only if not double-tap seeking and not triggered by long press
      if (tapCount == 1 && pendingSingleTapRegion != null && !isDoubleTapSeeking && !longPressTriggeredDuringTouch) {
        val region = pendingSingleTapRegion!!
        
        var handled = false
        if (areControlsLocked) {
             viewModel.showControls()
             handled = true
        } else if (!pendingTapInExclusionZone) {
            if (region == "center" && useSingleTapForCenter) {
              viewModel.handleCenterSingleTap()
              handled = true
            } else if (region == "left" && useSingleTapForLeftRight) {
              viewModel.handleLeftSingleTap()
              handled = true
            } else if (region == "right" && useSingleTapForLeftRight) {
              viewModel.handleRightSingleTap()
              handled = true
            }
        }

        if (!handled) {
          if (panelShown != Panels.None && !allowGesturesInPanels) {
            viewModel.panelShown.update { Panels.None }
          }
          if (controlsShown) {
            viewModel.hideControls()
          } else {
            viewModel.showControls()
          }
        }
        pendingSingleTapRegion = null
        pendingSingleTapPosition = null
      }
      tapCount = 0
      lastTapRegion = null
      if (!isDoubleTapSeeking) {
        isDoubleTapSeeking = false
        viewModel.updateSeekAmount(0)
      }
    }
  }

  // Reset double-tap seek state when seeking stops
  LaunchedEffect(seekAmount) {
    if (seekAmount == 0) {
      delay(100)
      isDoubleTapSeeking = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .pointerInput(areControlsLocked, doubleTapSeekAreaWidth, reverseDoubleTap) {
        // Isolated double-tap detection that doesn't interfere with other gestures
        if (isVerticalGestureActive) return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val downPosition = down.position
          val downTime = System.currentTimeMillis()

          // Calculate regions
          val seekAreaFraction = doubleTapSeekAreaWidth / 100f
          val leftBoundary = size.width * seekAreaFraction
          val rightBoundary = size.width * (1f - seekAreaFraction)
          val region = when {
            downPosition.x > rightBoundary -> if (reverseDoubleTap) "left" else "right"
            downPosition.x < leftBoundary -> if (reverseDoubleTap) "right" else "left"
            else -> "center"
          }

          // Track for potential drag
          var isDrag = false
          var wasConsumedByTapGesture = false

          do {
            val event = awaitPointerEvent()
            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

            // Check if this is a drag (not a tap)
            val distance = sqrt(
              (pointer.position.x - downPosition.x) * (pointer.position.x - downPosition.x) +
              (pointer.position.y - downPosition.y) * (pointer.position.y - downPosition.y)
            )

            if (distance > 10f) {
              isDrag = true
              // Don't consume - let other pointer inputs handle drag gestures
            }

            if (!pointer.pressed) {
              // Pointer lifted - this is a tap if it wasn't a drag
              if (!isDrag && !wasConsumedByTapGesture) {
                val timeSinceLastTap = downTime - lastTapTime
                val positionChange = sqrt(
                  (downPosition.x - lastTapPosition.x) * (downPosition.x - lastTapPosition.x) +
                  (downPosition.y - lastTapPosition.y) * (downPosition.y - lastTapPosition.y)
                )

                // Check if this is a continuation of multi-tap sequence
                val isMultiTapContinuation =
                  lastTapRegion == region &&
                  timeSinceLastTap < multiTapContinueWindow &&
                  positionChange < 100f &&
                  tapCount >= 2 &&
                  isDoubleTapSeeking

                // Check if this is a valid double-tap
                val isDoubleTap =
                  timeSinceLastTap < doubleTapTimeout &&
                  lastTapRegion == region &&
                  positionChange < 100f &&
                  tapCount == 1

                if (isDoubleTap && !areControlsLocked && !pendingTapInExclusionZone) {
                  // Valid double-tap detected
                  tapCount = 2
                  lastTapTime = downTime
                  lastTapPosition = downPosition
                  pendingSingleTapRegion = null // Cancel pending single tap
                  pendingSingleTapPosition = null
                  wasConsumedByTapGesture = true
                  pointer.consume()

                  when (region) {
                    "right" -> {
                      val rightGesture = gesturePreferences.rightSingleActionGesture.get()
                      if (rightGesture == SingleActionGesture.Seek) {
                        isDoubleTapSeeking = true
                        lastSeekRegion = "right"
                        lastSeekTime = System.currentTimeMillis()
                        if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleRightDoubleTap()
                    }
                    "left" -> {
                      val leftGesture = gesturePreferences.leftSingleActionGesture.get()
                      if (leftGesture == SingleActionGesture.Seek) {
                        isDoubleTapSeeking = true
                        lastSeekRegion = "left"
                        lastSeekTime = System.currentTimeMillis()
                        if (isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleLeftDoubleTap()
                    }
                    "center" -> {
                      viewModel.handleCenterDoubleTap()
                    }
                  }
                } else if (isMultiTapContinuation && isDoubleTapSeeking) {
                  // Continue multi-tap seeking
                  tapCount++
                  wasConsumedByTapGesture = true
                  pointer.consume()
                  lastSeekTime = System.currentTimeMillis()
                  lastTapTime = downTime
                  lastTapPosition = downPosition

                  when (region) {
                    "right" -> {
                      val rightGesture = gesturePreferences.rightSingleActionGesture.get()
                      if (rightGesture == SingleActionGesture.Seek) {
                        if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleRightDoubleTap()
                    }
                    "left" -> {
                      val leftGesture = gesturePreferences.leftSingleActionGesture.get()
                      if (leftGesture == SingleActionGesture.Seek) {
                        if (isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleLeftDoubleTap()
                    }
                    "center" -> {
                      viewModel.handleCenterDoubleTap()
                    }
                  }
                } else if (tapCount == 0 || timeSinceLastTap >= doubleTapTimeout) {
                  // Single tap or timed out - start new tap sequence
                  tapCount = 1
                  lastTapTime = downTime
                  lastTapPosition = downPosition
                  lastTapRegion = region
                  pendingSingleTapRegion = region
                  pendingSingleTapPosition = downPosition
                  wasConsumedByTapGesture = true
                  pointer.consume()
                  
                  // Instant single tap for center if enabled and not in exclusion zones
                  // Instant single tap logic
                  val isCenterImmediate = region == "center" && useSingleTapForCenter
                  val isLeftImmediate = region == "left" && useSingleTapForLeftRight
                  val isRightImmediate = region == "right" && useSingleTapForLeftRight

                  if ((isCenterImmediate || isLeftImmediate || isRightImmediate) && !longPressTriggeredDuringTouch) {
                    val exclusionZoneHeight = size.height * 0.25f // 25% from top and bottom
                    val inExclusionZone = downPosition.y < exclusionZoneHeight || 
                                          downPosition.y > size.height - exclusionZoneHeight
                    
                    pendingTapInExclusionZone = inExclusionZone // Store for delayed execution check

                    if (!inExclusionZone) {
                       if (areControlsLocked) {
                           viewModel.showControls()
                       } else {
                           if (isCenterImmediate) viewModel.handleCenterSingleTap()
                           else if (isLeftImmediate) viewModel.handleLeftSingleTap()
                           else if (isRightImmediate) viewModel.handleRightSingleTap()
                       }
                       
                       pendingSingleTapRegion = null 
                    }
                  } else {
                    pendingTapInExclusionZone = false
                  }
                }
              }
              break
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .pointerInput(areControlsLocked, multipleSpeedGesture, brightnessGesture, volumeGesture, moveSubtitleByDragging) {
        if ((!brightnessGesture && !volumeGesture && multipleSpeedGesture <= 0f && !moveSubtitleByDragging) || areControlsLocked) return@pointerInput

        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val startPosition = down.position

          // Reset long press tracking at the start of each gesture
          longPressTriggeredDuringTouch = false

          // State for vertical gestures (volume/brightness)
          var startingY = 0f
          var mpvVolumeStartingY = 0f
          var originalVolume = currentVolume
          var originalMPVVolume = currentMPVVolume
          var originalBrightness = currentBrightness
          var lastVolumeValue = currentVolume
          var lastMPVVolumeValue = currentMPVVolume ?: 100
          var lastBrightnessValue = currentBrightness
          val brightnessGestureSens = 0.0022f
          val volumeGestureSens = (viewModel.maxVolume * brightnessGestureSens).coerceAtLeast(0.001f)
          val mpvVolumeGestureSens = (volumeBoostingCap.coerceAtLeast(1) * brightnessGestureSens).coerceAtLeast(0.001f)

          // State for subtitle-position drag (touch on the subtitle, drag up/down)
          var subPosOriginal = 0
          var subPosDragStartY = 0f
          var lastSubPosValue = 0

          // Original speed for long press
          var originalSpeed = playbackSpeed ?: 1f

          // Track long press separately
          var longPressTriggered = false
          val longPressDelay = 500L
          var longPressJob = coroutineScope.launch {
            delay(longPressDelay)
            if (!longPressTriggered && paused == false) {
              val distance = sqrt(
                (down.position.x - startPosition.x) * (down.position.x - startPosition.x) +
                (down.position.y - startPosition.y) * (down.position.y - startPosition.y)
              )
              // Only trigger if still within tap threshold
              if (distance < 10f && multipleSpeedGesture > 0f) {
                longPressTriggered = true
                isLongPressing = true
                longPressStartTime = System.currentTimeMillis()
                longPressTriggeredDuringTouch = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                originalSpeed = playbackSpeed ?: 1f
                // Ramp speed up incrementally to avoid audio filter stutter
                val startSpeed = originalSpeed
                val targetSpeed = multipleSpeedGesture
                val steps = 5
                val stepDelay = 16L // ~one frame per step
                for (i in 1..steps) {
                  val t = i.toFloat() / steps
                  val intermediateSpeed = startSpeed + (targetSpeed - startSpeed) * t
                  MPVLib.setPropertyFloat("speed", intermediateSpeed)
                  if (i < steps) delay(stepDelay)
                }

                if (showDynamicSpeedOverlay) {
                  isDynamicSpeedControlActive = true
                  hasSwipedEnough = false
                  hasIncrementedSpeedLockHint = false
                  dynamicSpeedStartX = startPosition.x
                  dynamicSpeedStartValue = multipleSpeedGesture
                  lastAppliedSpeed = multipleSpeedGesture
                  viewModel.playerUpdate.update { PlayerUpdates.DynamicSpeedControl(multipleSpeedGesture, false) }
                } else {
                  viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
                }
              }
            }
          }

          var gestureType: String? = null

          do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }

            if (pointerCount == 1) {
              event.changes.forEach { change ->
                if (change.pressed) {
                  val currentPosition = change.position
                  val deltaX = currentPosition.x - startPosition.x
                  val deltaY = currentPosition.y - startPosition.y

                  // Determine gesture type based on initial drag direction
                  if (gestureType == null && (abs(deltaX) > 20f || abs(deltaY) > 20f)) {
                    // Cancel long press if drag started
                    longPressJob.cancel()

                    // Check if we're in long press mode with dynamic speed control
                    if (isLongPressing && isDynamicSpeedControlActive && showDynamicSpeedOverlay && abs(deltaX) > 10f) {
                      gestureType = "speed_control"
                    } else {
                      gestureType = if (abs(deltaX) > abs(deltaY) * 1.5f) {
                        "horizontal"
                      } else if (abs(deltaY) > abs(deltaX) * 1.5f) {
                        "vertical"
                      } else {
                        null
                      }
                    }

                    // Initialize gesture-specific state
                    when (gestureType) {
                      "speed_control" -> {
                        dynamicSpeedStartX = currentPosition.x
                        dynamicSpeedStartValue = MPVLib.getPropertyFloat("speed") ?: multipleSpeedGesture
                      }
                      "vertical" -> {
                        // If the drag started on the subtitle, reposition it instead of
                        // adjusting brightness/volume (XPlayer-style "grab the subtitle").
                        val subtitleActive = (MPVLib.getPropertyInt("sid") ?: 0) > 0
                        val secondarySubtitleActive = (MPVLib.getPropertyInt("secondary-sid") ?: 0) > 0
                        val curSubPos = (MPVLib.getPropertyInt("sub-pos") ?: subtitlesPreferences.subPos.get())
                          .coerceIn(0, 150)
                        val curSecSubPos = (MPVLib.getPropertyInt("secondary-sub-pos") ?: subtitlesPreferences.secondarySubPos.get())
                          .coerceIn(0, 150)

                        // sub-pos is the subtitle's vertical anchor as a % of height (100 = bottom).
                        // The text sits above that line, so bias the hit-band upward.
                        val subAnchorY = (curSubPos.coerceIn(0, 100) / 100f) * size.height
                        val secSubAnchorY = (curSecSubPos.coerceIn(0, 100) / 100f) * size.height

                        // Subtitles are horizontally centered, so only grab them in the centre
                        // band. This keeps brightness (far left) / volume (far right) swipes free
                        // even when they start low or high on the screen.
                        val startedOnSecondarySubtitle = moveSubtitleByDragging && secondarySubtitleActive && !isLongPressing &&
                          startPosition.y >= secSubAnchorY - size.height * 0.06f &&
                          startPosition.y <= secSubAnchorY + size.height * 0.22f &&
                          startPosition.x >= size.width * 0.2f &&
                          startPosition.x <= size.width * 0.8f

                        val startedOnPrimarySubtitle = moveSubtitleByDragging && subtitleActive && !isLongPressing &&
                          startPosition.y >= subAnchorY - size.height * 0.22f &&
                          startPosition.y <= subAnchorY + size.height * 0.06f &&
                          startPosition.x >= size.width * 0.2f &&
                          startPosition.x <= size.width * 0.8f

                        if (startedOnSecondarySubtitle) {
                          gestureType = "secondary_subtitle_pos"
                          subPosOriginal = curSecSubPos
                          lastSubPosValue = curSecSubPos
                          subPosDragStartY = startPosition.y
                          isVerticalGestureActive = true
                          viewModel.isVerticalGestureActive.value = true
                          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (startedOnPrimarySubtitle) {
                          gestureType = "subtitle_pos"
                          subPosOriginal = curSubPos
                          lastSubPosValue = curSubPos
                          subPosDragStartY = startPosition.y
                          isVerticalGestureActive = true
                          viewModel.isVerticalGestureActive.value = true
                          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if ((brightnessGesture || volumeGesture) && !isLongPressing) {
                          // Exclude system gestures from edges for volume and brightness adjustments
                          val edgeExcludePx = 24.dp.toPx()
                          val isNearEdge = startPosition.x < edgeExcludePx ||
                                           startPosition.x > size.width - edgeExcludePx ||
                                           startPosition.y < edgeExcludePx ||
                                           startPosition.y > size.height - edgeExcludePx
                          if (isNearEdge) {
                            gestureType = "ignored_edge"
                          } else {
                            isVerticalGestureActive = true
                            viewModel.isVerticalGestureActive.value = true
                            startingY = 0f
                            mpvVolumeStartingY = 0f
                            originalVolume = currentVolume
                            originalMPVVolume = currentMPVVolume
                            originalBrightness = currentBrightness
                            lastVolumeValue = currentVolume
                            lastMPVVolumeValue = currentMPVVolume ?: 100
                            lastBrightnessValue = currentBrightness
                          }
                        }
                      }
                    }
                  }

                  // Handle the appropriate gesture
                  when (gestureType) {
                    "speed_control" -> {
                      if (!showDynamicSpeedOverlay) return@forEach
                      if (isLongPressing && isDynamicSpeedControlActive && paused == false) {
                        change.consume()

                        val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
                        val screenWidth = size.width.toFloat()

                        val deltaX = currentPosition.x - dynamicSpeedStartX
                        val deltaY = currentPosition.y - startPosition.y
                        val swipeDetectionThreshold = 10.dp.toPx()
                        val lockThreshold = 60.dp.toPx()

                        // Vertical swipe to lock/unlock (Always available immediately after long press triggers)
                        if (abs(deltaY) > lockThreshold) {
                            if (deltaY < -lockThreshold && !viewModel.isSpeedLocked.value) {
                                // Swipe Up -> Lock
                                viewModel.isSpeedLocked.value = true
                                playerTutorialManager.markSpeedLockCompleted()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playerUpdate.update { PlayerUpdates.SpeedLockHint(lastAppliedSpeed, true) }
                            } else if (deltaY > lockThreshold && viewModel.isSpeedLocked.value) {
                                // Swipe Down -> Unlock
                                viewModel.isSpeedLocked.value = false
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playerUpdate.update { PlayerUpdates.SpeedLockHint(lastAppliedSpeed, false) }
                            }
                        }

                        if (!hasSwipedEnough && abs(deltaX) >= swipeDetectionThreshold) {
                          hasSwipedEnough = true
                        }

                        if (hasSwipedEnough) {
                          val presetsRange = speedPresets.size - 1
                          val indexDelta = (deltaX / screenWidth) * presetsRange * 3.5f

                          val startIndex = speedPresets.indexOfFirst {
                            abs(it - dynamicSpeedStartValue) < 0.01f
                          }.takeIf { it >= 0 } ?: 3 // Default to 1.0x (index 3)

                          val newIndex = (startIndex + indexDelta.toInt()).coerceIn(0, speedPresets.size - 1)
                          val newSpeed = speedPresets[newIndex]

                          if (abs(lastAppliedSpeed - newSpeed) > 0.01f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastAppliedSpeed = newSpeed
                            MPVLib.setPropertyFloat("speed", newSpeed)
                          }
                        }

                        // Smart Hint Logic: only show "Swipe up to lock" after 2 seconds of holding
                        val holdDuration = System.currentTimeMillis() - longPressStartTime
                        val isLocked = viewModel.isSpeedLocked.value
                        val shouldShowLockHint = isLocked || (holdDuration > 2000L && playerTutorialManager.shouldShowSpeedLockHint())

                        if (shouldShowLockHint) {
                            if (!isLocked && !hasIncrementedSpeedLockHint) {
                                hasIncrementedSpeedLockHint = true
                                playerTutorialManager.incrementSpeedLockHintCount()
                            }
                            viewModel.playerUpdate.update { 
                                PlayerUpdates.SpeedLockHint(lastAppliedSpeed, isLocked) 
                            }
                        } else {
                            viewModel.playerUpdate.update { 
                                PlayerUpdates.DynamicSpeedControl(lastAppliedSpeed, hasSwipedEnough) 
                            }
                        }
                      }
                    }
                    "subtitle_pos" -> {
                      // Move the subtitle 1:1 with the finger. Dragging up lowers sub-pos
                      // (subtitle moves up); dragging down raises it (subtitle moves down).
                      val deltaY = currentPosition.y - subPosDragStartY
                      val newSubPos = (subPosOriginal + (deltaY / size.height * 100f))
                        .toInt()
                        .coerceIn(0, 150)
                      if (newSubPos != lastSubPosValue) {
                        MPVLib.setPropertyInt("sub-pos", newSubPos)
                        lastSubPosValue = newSubPos
                        playerTutorialManager.markSubtitleDragCompleted()
                        viewModel.playerUpdate.update { PlayerUpdates.ShowText("Sub position: $newSubPos") }
                      }
                      change.consume()
                    }
                    "secondary_subtitle_pos" -> {
                      val deltaY = currentPosition.y - subPosDragStartY
                      val newSubPos = (subPosOriginal + (deltaY / size.height * 100f))
                        .toInt()
                        .coerceIn(0, 150)
                      if (newSubPos != lastSubPosValue) {
                        MPVLib.setPropertyInt("secondary-sub-pos", newSubPos)
                        lastSubPosValue = newSubPos
                        playerTutorialManager.markSubtitleDragCompleted()
                        viewModel.playerUpdate.update { PlayerUpdates.ShowText("Secondary sub position: $newSubPos") }
                      }
                      change.consume()
                    }
                    "vertical" -> {
                      if ((brightnessGesture || volumeGesture) && !isLongPressing) {
                        val amount = currentPosition.y - startPosition.y

                        val changeVolume: () -> Unit = {
                          val isIncreasingVolumeBoost: (Float) -> Boolean = {
                            volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                              (currentMPVVolume ?: 100) - 100 < volumeBoostingCap && amount < 0
                          }
                          val isDecreasingVolumeBoost: (Float) -> Boolean = {
                            volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                              (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap && amount > 0
                          }

                          if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                            if (mpvVolumeStartingY == 0f) {
                              startingY = 0f
                              originalVolume = currentVolume
                              mpvVolumeStartingY = currentPosition.y
                            }
                            val newMPVVolume = calculateNewVerticalGestureValue(
                              originalMPVVolume ?: 100,
                              mpvVolumeStartingY,
                              currentPosition.y,
                              mpvVolumeGestureSens,
                            ).coerceIn(100..volumeBoostingCap + 100)

                            if (newMPVVolume != lastMPVVolumeValue) {
                              viewModel.changeMPVVolumeTo(newMPVVolume)
                              lastMPVVolumeValue = newMPVVolume
                            }
                          } else {
                            if (startingY == 0f) {
                              mpvVolumeStartingY = 0f
                              originalMPVVolume = currentMPVVolume
                              startingY = currentPosition.y
                            }
                            val newVolume = calculateNewVerticalGestureValue(
                              originalVolume,
                              startingY,
                              currentPosition.y,
                              volumeGestureSens,
                            )

                            if (newVolume != lastVolumeValue) {
                              viewModel.changeVolumeTo(newVolume)
                              lastVolumeValue = newVolume
                            }
                          }

                          viewModel.displayVolumeSlider()
                        }
                        val changeBrightness: () -> Unit = {
                          if (startingY == 0f) startingY = currentPosition.y
                          val newBrightness = calculateNewVerticalGestureValue(
                            originalBrightness,
                            startingY,
                            currentPosition.y,
                            brightnessGestureSens,
                          )

                          if (abs(newBrightness - lastBrightnessValue) > 0.001f) {
                            viewModel.changeBrightnessTo(newBrightness)
                            lastBrightnessValue = newBrightness
                          }

                          viewModel.displayBrightnessSlider()
                        }

                        when {
                          volumeGesture && brightnessGesture -> {
                            if (swapVolumeAndBrightness) {
                              if (currentPosition.x > size.width / 2) changeBrightness() else changeVolume()
                            } else {
                              if (currentPosition.x < size.width / 2) changeBrightness() else changeVolume()
                            }
                          }
                          brightnessGesture -> changeBrightness()
                          volumeGesture -> changeVolume()
                          else -> {}
                        }

                        change.consume()
                      }
                    }
                  }
                }
              }
            } else if (pointerCount > 1) {
              // Multi-finger gesture detected
              longPressJob.cancel()
              if (gestureType != null) {
                when (gestureType) {
                  "vertical" -> {
                    if (brightnessGesture || volumeGesture) {
                      isVerticalGestureActive = false
                      viewModel.isVerticalGestureActive.value = false
                      startingY = 0f
                      lastVolumeValue = currentVolume
                      lastMPVVolumeValue = currentMPVVolume ?: 100
                      lastBrightnessValue = currentBrightness
                    }
                  }
                  "subtitle_pos" -> {
                    isVerticalGestureActive = false
                    viewModel.isVerticalGestureActive.value = false
                    subtitlesPreferences.subPos.set(lastSubPosValue)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                  }
                  "secondary_subtitle_pos" -> {
                    isVerticalGestureActive = false
                    viewModel.isVerticalGestureActive.value = false
                    subtitlesPreferences.secondarySubPos.set(lastSubPosValue)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                  }
                }
                gestureType = null
              }
              break
            }
          } while (event.changes.any { it.pressed })

          // Handle gesture end
          longPressJob.cancel()

          if (isLongPressing) {
            if (hasSwipedEnough && rememberLongPressSpeed && lastAppliedSpeed > 0f) {
              playerPreferences.holdForMultipleSpeed.set(lastAppliedSpeed)
            }
            isLongPressing = false
            isDynamicSpeedControlActive = false
            hasSwipedEnough = false
            
            // Only ramp back down if NOT locked
            if (!viewModel.isSpeedLocked.value) {
                // Ramp speed back down incrementally to avoid audio filter stutter
                val currentSpeed = MPVLib.getPropertyFloat("speed") ?: multipleSpeedGesture
                val targetSpeed = originalSpeed
                val steps = 5
                val stepDelay = 16L
                coroutineScope.launch {
                  for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val intermediateSpeed = currentSpeed + (targetSpeed - currentSpeed) * t
                    MPVLib.setPropertyFloat("speed", intermediateSpeed)
                    if (i < steps) delay(stepDelay)
                  }
                }
                viewModel.playerUpdate.update { PlayerUpdates.None }
            } else {
                // If locked, just clear the hint overlay
                viewModel.playerUpdate.update { PlayerUpdates.None }
            }
          }

          when (gestureType) {
            "vertical" -> {
              if (brightnessGesture || volumeGesture) {
                isVerticalGestureActive = false
                viewModel.isVerticalGestureActive.value = false
                startingY = 0f
                lastVolumeValue = currentVolume
                lastMPVVolumeValue = currentMPVVolume ?: 100
                lastBrightnessValue = currentBrightness
              }
            }
            "subtitle_pos" -> {
              isVerticalGestureActive = false
              viewModel.isVerticalGestureActive.value = false
              // Persist the final position so it carries across videos.
              subtitlesPreferences.subPos.set(lastSubPosValue)
              viewModel.playerUpdate.update { PlayerUpdates.None }
            }
            "secondary_subtitle_pos" -> {
              isVerticalGestureActive = false
              viewModel.isVerticalGestureActive.value = false
              subtitlesPreferences.secondarySubPos.set(lastSubPosValue)
              viewModel.playerUpdate.update { PlayerUpdates.None }
            }
          }
        }
      }
      .pointerInput(pinchToZoomGesture, panAndZoomEnabled, areControlsLocked, isVerticalGestureActive, advancedZoomEnabled) {
        if (!pinchToZoomGesture || areControlsLocked || isVerticalGestureActive || advancedZoomEnabled) return@pointerInput

        awaitEachGesture {
          var zoom = 0f
          var gestureStarted = false
          var prevDist = 0f
          var prevMidX = 0f
          var prevMidY = 0f
          // Locally tracked pan; read from mpv once at gesture start, mutated here.
          var localPanX = 0f
          var localPanY = 0f
          // Video display dims at 1x, cached once per gesture.
          var bw = 0f
          var bh = 0f

          awaitFirstDown(requireUnconsumed = false)

          do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.size == 2) {
              val p1 = pressed[0].position
              val p2 = pressed[1].position
              val dx = p2.x - p1.x
              val dy = p2.y - p1.y
              val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
              val midX = (p1.x + p2.x) / 2f
              val midY = (p1.y + p2.y) / 2f

              if (prevDist == 0f) {
                // First 2-finger frame — do the one-time JNI reads for the whole gesture.
                prevDist = dist
                zoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
                localPanX = MPVLib.getPropertyDouble("video-pan-x")?.toFloat() ?: 0f
                localPanY = MPVLib.getPropertyDouble("video-pan-y")?.toFloat() ?: 0f
                val sw = size.width.toFloat()
                val sh = size.height.toFloat()
                val va = MPVLib.getPropertyDouble("video-params/aspect")?.toFloat() ?: (sw / sh)
                val sa = sw / sh
                if (va >= sa) { bw = sw; bh = sw / va } else { bw = sh * va; bh = sh }
                prevMidX = midX
                prevMidY = midY
              } else {
                // Activate on significant pinch movement
                if (!gestureStarted && abs(dist - prevDist) > 5f) {
                  gestureStarted = true
                  viewModel.playerUpdate.update { PlayerUpdates.VideoZoom }
                }

                if (gestureStarted) {
                  // Per-frame zoom: small delta from previous distance → naturally smooth
                  val zoomDelta = ln((dist / prevDist).toDouble()).toFloat() * 1.2f
                  zoom = (zoom + zoomDelta).coerceIn(-1f, 3f)
                  viewModel.setVideoZoom(zoom)

                  // Simultaneous pan while pinching, all math local — one write to mpv.
                  if (panAndZoomEnabled && bw > 0f && bh > 0f) {
                    val scale = 2f.pow(zoom)
                    val panSensitivity = 2.2f
                    localPanX += ((midX - prevMidX) * panSensitivity) / (bw * scale)
                    localPanY += ((midY - prevMidY) * panSensitivity) / (bh * scale)
                    val maxPan = ((scale - 1f) / (2f * scale)).coerceAtLeast(0f)
                    localPanX = localPanX.coerceIn(-maxPan, maxPan)
                    localPanY = localPanY.coerceIn(-maxPan, maxPan)
                    viewModel.setVideoPan(localPanX, localPanY)
                  }
                }

                prevDist = dist
                prevMidX = midX
                prevMidY = midY
              }

              pressed.forEach { it.consume() }
            } else if (pressed.size < 2 && prevDist != 0f) {
              break
            }
          } while (event.changes.any { it.pressed })
        }
      }
      // Single-finger pan (only when Pan & Zoom enabled and zoomed in)
      .pointerInput(panAndZoomEnabled, pinchToZoomGesture, areControlsLocked, isVerticalGestureActive, advancedZoomEnabled) {
        if (!panAndZoomEnabled || !pinchToZoomGesture || areControlsLocked || isVerticalGestureActive || advancedZoomEnabled) return@pointerInput

        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          var panning = false
          var prevX = down.position.x
          var prevY = down.position.y
          val startX = prevX
          val startY = prevY

          // Per-gesture cached state. zoom/localPan* seeded from mpv on activation.
          var zoom = 0f
          var localPanX = 0f
          var localPanY = 0f
          var bw = 0f
          var bh = 0f
          var stateReady = false

          do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed && !it.isConsumed }

            if (pressed.size == 1) {
              val change = pressed[0]

              if (viewModel.isVerticalGestureActive.value || viewModel.isGestureSeeking.value) {
                continue
              }

              // Ignore panning if long-pressing or speed adjustment is active
              if (isLongPressing || isDynamicSpeedControlActive) {
                continue
              }

              val pos = change.position

              // Activate after 20px drag threshold
              if (!panning) {
                val dx = pos.x - startX
                val dy = pos.y - startY
                // If it's a horizontal seek gesture, let horizontal swipe-to-seek block handle it instead
                val isHorizontalSeek = horizontalSwipeToSeek && (abs(dx) > abs(dy) * 1.2f)
                if (isHorizontalSeek) {
                  continue
                }

                val d = sqrt(dx * dx + dy * dy)
                if (d > 20f) { panning = true; prevX = pos.x; prevY = pos.y }
              }

              if (panning) {
                if (!stateReady) {
                  // One-time JNI reads for the whole gesture.
                  zoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
                  if (zoom <= 0f) { continue }
                  localPanX = MPVLib.getPropertyDouble("video-pan-x")?.toFloat() ?: 0f
                  localPanY = MPVLib.getPropertyDouble("video-pan-y")?.toFloat() ?: 0f
                  val sw = size.width.toFloat()
                  val sh = size.height.toFloat()
                  if (sw <= 0f || sh <= 0f) { continue }
                  val va = MPVLib.getPropertyDouble("video-params/aspect")?.toFloat() ?: (sw / sh)
                  val sa = sw / sh
                  if (va >= sa) { bw = sw; bh = sw / va } else { bw = sh * va; bh = sh }
                  stateReady = true
                }

                val scale = 2f.pow(zoom)
                val panSensitivity = 2.2f
                localPanX += ((pos.x - prevX) * panSensitivity) / (bw * scale)
                localPanY += ((pos.y - prevY) * panSensitivity) / (bh * scale)
                val maxPan = ((scale - 1f) / (2f * scale)).coerceAtLeast(0f)
                localPanX = localPanX.coerceIn(-maxPan, maxPan)
                localPanY = localPanY.coerceIn(-maxPan, maxPan)
                viewModel.setVideoPan(localPanX, localPanY)
                prevX = pos.x
                prevY = pos.y
                change.consume()
              }
            } else if (pressed.size > 1) {
              break
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .pointerInput(horizontalSwipeToSeek, swipeToSubtitleSeek, areControlsLocked, gesturePreferences, isVerticalGestureActive) {
        if ((!horizontalSwipeToSeek && !swipeToSubtitleSeek) || areControlsLocked || isVerticalGestureActive) return@pointerInput

        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val startPosition = down.position
          val startTime = System.currentTimeMillis()
          
          var gestureType: String? = null
          var hasStartedSeeking = false
          var hasTriggeredSubSeek = false
          var initialVideoPosition = 0f
          // Use the sensitivity preference instead of hardcoded value
          val seekSensitivity = horizontalSwipeSensitivity
          
          do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }

            if (pointerCount == 1) {
              event.changes.forEach { change ->
                if (change.pressed) {
                  val currentPosition = change.position
                  val deltaX = currentPosition.x - startPosition.x
                  val deltaY = currentPosition.y - startPosition.y
                  val timeSinceStart = System.currentTimeMillis() - startTime

                  // Exclusion zone check (25% from top and bottom)
                  val exclusionZoneHeight = size.height * 0.25f
                  val inExclusionZone = startPosition.y < exclusionZoneHeight || 
                                        startPosition.y > size.height - exclusionZoneHeight

                  // Only activate if this is clearly a horizontal gesture
                  // and not conflicting with other gestures
                  if (gestureType == null && 
                      abs(deltaX) > 30f && 
                      abs(deltaX) > abs(deltaY) * 2f && // Must be strongly horizontal
                      timeSinceStart > 100L && // Avoid conflicts with double-tap
                      !isLongPressing && // Don't conflict with long press
                      !isDynamicSpeedControlActive && // Don't conflict with speed control
                      panelShown == Panels.None) { // Only when no panels are shown
                    
                    val hasActiveSubtitle = (MPVLib.getPropertyInt("sid") ?: 0) != 0
                    if (swipeToSubtitleSeek && inExclusionZone && hasActiveSubtitle) {
                      gestureType = "subtitle_seek"
                    } else if (horizontalSwipeToSeek) {
                      gestureType = "horizontal_seek"
                      hasStartedSeeking = true
                      viewModel.setGestureSeeking(true)
                      initialVideoPosition = position?.toFloat() ?: 0f
                      
                      // Show seekbar if preference enabled
                      if (playerPreferences.showSeekBarWhenSeeking.get()) {
                        viewModel.showSeekBar()
                      }
                    }
                    
                    if (gestureType != null) {
                      change.consume()
                    }
                  }

                  if (gestureType == "horizontal_seek" && hasStartedSeeking) {
                    // Calculate seek amount based on horizontal movement
                    val seekAmount = deltaX * seekSensitivity
                    val targetPosition = (initialVideoPosition + seekAmount).coerceAtLeast(0f)
                    val maxDuration = if (preciseDuration > 0f) preciseDuration else duration?.toFloat() ?: 0f
                    val clampedPosition = if (maxDuration > 0f) targetPosition.coerceAtMost(maxDuration) else targetPosition
                    
                    // Use the same seeking mechanism as seekbar scrubbing
                    // This will update the seekbar position and provide live preview
                    viewModel.seekTo(clampedPosition.toInt())
                    
                    // Format and display time position updates
                    val currentPos = clampedPosition.toInt()
                    val seekDelta = (clampedPosition - initialVideoPosition).toInt()
                    
                    val currentTimeStr = formatSeekTime(currentPos)
                    
                    // Format seek delta with +/- prefix
                    val deltaStr = if (seekDelta >= 0) {
                      "+${formatSeekTime(seekDelta)}"
                    } else {
                      "-${formatSeekTime(-seekDelta)}"
                    }
                    
                    // Use PlayerUpdates system like zoom updates
                    viewModel.playerUpdate.update { 
                      PlayerUpdates.HorizontalSeek(currentTimeStr, deltaStr)
                    }
                    
                    change.consume()
                  } else if (gestureType == "subtitle_seek" && !hasTriggeredSubSeek) {
                    // Trigger subtitle seek based on direction
                    // Threshold for triggering sub seek (e.g., 50 pixels)
                    if (abs(deltaX) > 50f) {
                      if (deltaX > 0) {
                        // Swipe Right -> Previous Subtitle
                        viewModel.leftSubSeek()
                      } else {
                        // Swipe Left -> Next Subtitle
                        viewModel.rightSubSeek()
                      }
                      hasTriggeredSubSeek = true
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      change.consume()
                    }
                  }
                }
              }
            } else if (pointerCount > 1) {
              // Multi-finger detected, cancel horizontal seek
              if (hasStartedSeeking) {
                hasStartedSeeking = false
                viewModel.setGestureSeeking(false)
                // Clean up seeking state without showing controls
                viewModel.playerUpdate.update { PlayerUpdates.None }
                viewModel.hideSeekBar()
              }
              break
            }
          } while (event.changes.any { it.pressed })

          // Apply the final seek when gesture ends
          if (hasStartedSeeking) {
            // Clear the horizontal seek update and hide seekbar after a short delay
            coroutineScope.launch {
              delay(300)
              viewModel.playerUpdate.update { PlayerUpdates.None }
              viewModel.hideSeekBar()
              viewModel.setGestureSeeking(false)
            }
          }
        }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapToSeekOvals(
  amount: Int,
  text: String?,
  showOvals: Boolean,
  showSeekIcon: Boolean,
  showSeekTime: Boolean,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  
  val doubleTapSeekAreaWidth by gesturePreferences.doubleTapSeekAreaWidth.collectAsState()
  val seekAreaFraction = doubleTapSeekAreaWidth / 100f
  
  val showCircularDoubleTapSeek by playerPreferences.showCircularDoubleTapSeek.collectAsState()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

  // Track the last direction/amount so we know where to slide/hide and show correct text even when amount becomes 0
  var lastIsRight by remember { mutableStateOf(true) }
  var lastNonNullAmount by remember { mutableStateOf(10) }
  var animationTrigger by remember { mutableStateOf(0) }
  LaunchedEffect(amount) {
    if (amount > 0) {
      lastIsRight = true
      lastNonNullAmount = abs(amount)
      animationTrigger = amount
    } else if (amount < 0) {
      lastIsRight = false
      lastNonNullAmount = abs(amount)
      animationTrigger = amount
    }
  }

  val isRight = if (amount != 0) amount > 0 else lastIsRight
  val seekDisplayAmount = if (amount != 0) abs(amount) else lastNonNullAmount
  val isVisible = amount != 0

  if (showCircularDoubleTapSeek) {
    // ----------------------------------------------------
    // New optional circular double tap seek overlay
    // ----------------------------------------------------
    // Scale animation for bounce effect on each double tap
    var scaleTarget by remember { mutableStateOf(1f) }
    val scale by animateFloatAsState(
      targetValue = scaleTarget,
      animationSpec = tween(durationMillis = 150),
      label = "circular_double_tap_scale"
    )

    LaunchedEffect(amount) {
      if (amount != 0) {
        scaleTarget = 1.15f
        delay(100)
        scaleTarget = 1f
      } else {
        scaleTarget = 1f
      }
    }

    val overlayAlpha by animateFloatAsState(
      targetValue = if (isVisible) 1f else 0f,
      animationSpec = tween(durationMillis = 150),
      label = "circular_seek_alpha"
    )
    val overlayScale by animateFloatAsState(
      targetValue = if (isVisible) 1f else 0.8f,
      animationSpec = tween(durationMillis = 150),
      label = "circular_seek_scale"
    )

    val circleShape = RoundedCornerShape(36.dp)

    val circleModifier = if (enableGlass) {
      Modifier.glassSurface(
        shape = circleShape,
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
    } else {
      Modifier
        .background(
          if (matchTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
          else Color.Black.copy(alpha = 0.55f),
          shape = circleShape
        )
        .then(
          if (matchTheme) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape = circleShape)
          } else {
            Modifier
          }
        )
    }

    if (amount != 0 || overlayAlpha > 0.01f) {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart,
      ) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(seekAreaFraction),
          contentAlignment = Alignment.Center,
        ) {
          Layout(
            content = {
              // 0: Rounded circle around the chevrons
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(circleShape)
                  .then(circleModifier),
                contentAlignment = Alignment.Center
              ) {
                CombiningChevronsAnimation(
                  isRight = isRight,
                  trigger = animationTrigger
                )
              }

              // 1: Seek time display right below the circle
              Text(
                text = if (isRight) "+${seekDisplayAmount}s" else "-${seekDisplayAmount}s",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
              )
            },
            modifier = Modifier
              .graphicsLayer {
                alpha = overlayAlpha
                scaleX = overlayScale * scale
                scaleY = overlayScale * scale
              }
          ) { measurables, constraints ->
            val circlePlaceable = measurables[0].measure(constraints)
            val textPlaceable = measurables[1].measure(constraints)
            val spacing = 8.dp.roundToPx()

            val width = maxOf(circlePlaceable.width, textPlaceable.width)
            // Report height as only the circle's height so Alignment.Center vertically
            // aligns the center of the circle exactly with the screen's center line
            // (matching the center play/pause/prev/next controls), while placing the text below.
            val height = circlePlaceable.height

            layout(width, height) {
              circlePlaceable.placeRelative(
                x = (width - circlePlaceable.width) / 2,
                y = 0
              )
              textPlaceable.placeRelative(
                x = (width - textPlaceable.width) / 2,
                y = circlePlaceable.height + spacing
              )
            }
          }
        }
      }
    }
  } else {
    // ----------------------------------------------------
    // Original oval/ripple-based double tap seek overlay
    // ----------------------------------------------------
    val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")

    // Scale animation for text
    var scaleTarget by remember { mutableStateOf(1f) }
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = tween(durationMillis = 150),
        label = "text_scale"
    )
    
    LaunchedEffect(amount) {
        if (amount != 0) {
            scaleTarget = 1.2f
            delay(100)
            scaleTarget = 1f
        } else {
          scaleTarget = 1f
        }
    }

    Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
      CompositionLocalProvider(
        LocalRippleConfiguration provides playerRippleConfiguration,
      ) {
        if (amount != 0) {
          Box(
            modifier = Modifier
              .fillMaxHeight()
              .fillMaxWidth(seekAreaFraction),
            contentAlignment = Alignment.Center,
          ) {
            if (showOvals) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                  .background(Color.White.copy(alpha))
                  .indication(interactionSource, ripple()),
              )
            }
            if (showSeekIcon || showSeekTime) {
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
              ) {
                  if (amount < 0) {
                      CombiningChevronsAnimation(isRight = false, trigger = amount)
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                          text = "- ${abs(amount)}",
                          fontSize = 22.sp,
                          fontWeight = FontWeight.Bold,
                          textAlign = TextAlign.Center,
                          color = Color.White,
                          modifier = Modifier.scale(scale)
                      )
                  } else {
                      Text(
                          text = "+ ${abs(amount)}",
                          fontSize = 22.sp,
                          fontWeight = FontWeight.Bold,
                          textAlign = TextAlign.Center,
                          color = Color.White,
                          modifier = Modifier.scale(scale)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      CombiningChevronsAnimation(isRight = true, trigger = amount)
                  }
              }
            }
          }
        }
      }
    }
  }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
  return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
  return originalValue + ((startingY - newY) * sensitivity)
}

private fun formatSeekTime(seconds: Int): String {
  val absSeconds = kotlin.math.abs(seconds)
  val hours = absSeconds / 3600
  val minutes = (absSeconds % 3600) / 60
  val secs = absSeconds % 60
  return if (hours > 0) {
    String.format("%d:%02d:%02d", hours, minutes, secs)
  } else {
    String.format("%02d:%02d", minutes, secs)
  }
}

@Composable
fun CombiningChevronsAnimation(
    isRight: Boolean,
    trigger: Int,
    modifier: Modifier = Modifier
) {
    // List of active animations (unique IDs)
    val animations = remember { mutableStateListOf<Long>() }

    // Fire a new animation whenever trigger changes
    LaunchedEffect(trigger) {
        animations.add(System.nanoTime())
    }

    Row(modifier = modifier) {
        Box {
             // Static Chevron
             Icon(
                imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            
            // Render active moving chevrons
            animations.forEach { animId ->
                key(animId) {
                    MovingChevron(
                        isRight = isRight,
                        onFinished = { animations.remove(animId) }
                    )
                }
            }
        }
    }
}

@Composable
fun MovingChevron(
    isRight: Boolean,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(250, easing = LinearEasing)
        )
        onFinished()
    }
    
    val startOffset = if (isRight) -15f else 15f
    val currentOffset = startOffset * (1f - progress.value)
    val alpha = 1f - progress.value
    
    Icon(
        imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(x = currentOffset.dp.roundToPx(), y = 0)
                }
            } 
    )
}
