package xyz.mpv.rex.ui.player.managers

import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.player.CustomKeyCodes
import xyz.mpv.rex.ui.player.SingleActionGesture

/**
 * Manages touch gestures, double-tap seek calculations, seek state flows,
 * swipe thresholds, and gesture action mapping.
 */
class PlayerGestureManager(
  private val gesturePreferences: GesturePreferences,
  private val playerPreferences: PlayerPreferences,
  private val playbackManager: PlaybackManager,
  private val scope: CoroutineScope,
  private val getPosition: () -> Int?,
  private val getDuration: () -> Int?,
  private val onSeekTo: (position: Int) -> Unit,
  private val onSeekBy: (offset: Int) -> Unit,
  private val onShowSeekBar: () -> Unit,
  private val onPauseUnpause: () -> Unit,
  private val onPlayNext: () -> Unit,
  private val onPlayPrevious: () -> Unit,
) {
  val doubleTapToSeekDuration: Int
    get() = gesturePreferences.doubleTapToSeekDuration.get()

  // Gesture state for seekbar bouncing animation
  private val _isGestureSeeking = MutableStateFlow(false)
  val isGestureSeeking: StateFlow<Boolean> = _isGestureSeeking.asStateFlow()

  // Gesture state for vertical bouncing animation
  val isVerticalGestureActive = MutableStateFlow(false)

  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount: StateFlow<Int> = _doubleTapSeekAmount.asStateFlow()

  private val _doubleTapSeekBasePos = MutableStateFlow<Int?>(null)
  val doubleTapSeekBasePos: StateFlow<Int?> = _doubleTapSeekBasePos.asStateFlow()

  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards: StateFlow<Boolean> = _isSeekingForwards.asStateFlow()

  private val _seekText = MutableStateFlow<String?>(null)
  val seekText: StateFlow<String?> = _seekText.asStateFlow()

  fun setGestureSeeking(isSeeking: Boolean) {
    _isGestureSeeking.value = isSeeking
  }

  fun leftSeek() {
    val pos = getPosition()
    if (_doubleTapSeekAmount.value == 0) _doubleTapSeekBasePos.value = pos
    if ((pos ?: 0) > 0) {
      _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    }
    _isSeekingForwards.value = false
    onSeekBy(-doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get() && !gesturePreferences.dontShowSeekBarOnDoubleTap.get()) onShowSeekBar()
  }

  fun rightSeek() {
    val pos = getPosition()
    val curDuration = getDuration() ?: 0
    if (_doubleTapSeekAmount.value == 0) _doubleTapSeekBasePos.value = pos
    if (curDuration <= 0 || (pos ?: 0) < curDuration) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    onSeekBy(doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get() && !gesturePreferences.dontShowSeekBarOnDoubleTap.get()) onShowSeekBar()
  }

  fun leftSubSeek() {
    playbackManager.subSeek(
      scope = scope,
      forward = false,
      onDiffCalculated = { diff ->
        _isSeekingForwards.value = false
        _doubleTapSeekAmount.value += diff.toInt()
      },
      onFallback = { leftSeek() },
    )
    if (playerPreferences.showSeekBarWhenSeeking.get() && !gesturePreferences.dontShowSeekBarOnDoubleTap.get()) onShowSeekBar()
  }

  fun rightSubSeek() {
    playbackManager.subSeek(
      scope = scope,
      forward = true,
      onDiffCalculated = { diff ->
        _isSeekingForwards.value = true
        _doubleTapSeekAmount.value += diff.toInt()
      },
      onFallback = { rightSeek() },
    )
    if (playerPreferences.showSeekBarWhenSeeking.get() && !gesturePreferences.dontShowSeekBarOnDoubleTap.get()) onShowSeekBar()
  }

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.value = amount
    if (amount == 0) _doubleTapSeekBasePos.value = null
  }

  fun updateSeekText(text: String?) {
    _seekText.value = text
  }

  fun updateIsSeekingForwards(isForwards: Boolean) {
    _isSeekingForwards.value = isForwards
  }

  fun seekToWithText(
    seekValue: Int,
    text: String?,
  ) {
    val currentPos = getPosition() ?: return
    _isSeekingForwards.value = seekValue > currentPos
    _doubleTapSeekAmount.value = seekValue - currentPos
    _seekText.value = text
    onSeekTo(seekValue)
  }

  fun seekByWithText(
    value: Int,
    text: String?,
  ) {
    val currentPos = getPosition() ?: return
    val maxDuration = getDuration() ?: 0

    _doubleTapSeekAmount.update {
      if ((value < 0 && it < 0) || (maxDuration > 0 && currentPos + value > maxDuration)) 0 else it + value
    }
    _seekText.value = text
    _isSeekingForwards.value = value > 0
    onSeekBy(value)
  }

  fun executeGestureAction(
    action: SingleActionGesture,
    isLeft: Boolean = false,
    isRight: Boolean = false,
  ) {
    when (action) {
      SingleActionGesture.Seek -> {
        if (isLeft) leftSeek() else if (isRight) rightSeek()
      }
      SingleActionGesture.SubSeek -> {
        if (isLeft) leftSubSeek() else if (isRight) rightSubSeek()
      }
      SingleActionGesture.PlayPause -> onPauseUnpause()
      SingleActionGesture.Custom -> {
        scope.launch(Dispatchers.IO) {
          val keyCode = when {
            isLeft -> CustomKeyCodes.DoubleTapLeft.keyCode
            isRight -> CustomKeyCodes.DoubleTapRight.keyCode
            else -> CustomKeyCodes.DoubleTapCenter.keyCode
          }
          MPVLib.command("keypress", keyCode)
        }
      }
      SingleActionGesture.PlaylistNext -> onPlayNext()
      SingleActionGesture.PlaylistPrev -> onPlayPrevious()
      SingleActionGesture.None -> {}
    }
  }

  fun handleLeftDoubleTap() {
    executeGestureAction(gesturePreferences.leftSingleActionGesture.get(), isLeft = true)
  }

  fun handleCenterDoubleTap() {
    executeGestureAction(gesturePreferences.centerSingleActionGesture.get())
  }

  fun handleCenterSingleTap() {
    executeGestureAction(gesturePreferences.centerSingleActionGesture.get())
  }

  fun handleLeftSingleTap() {
    executeGestureAction(gesturePreferences.leftSingleTapGesture.get(), isLeft = true)
  }

  fun handleRightSingleTap() {
    executeGestureAction(gesturePreferences.rightSingleTapGesture.get(), isRight = true)
  }

  fun handleRightDoubleTap() {
    executeGestureAction(gesturePreferences.rightSingleActionGesture.get(), isRight = true)
  }

  fun handleMediaPlayPause() {
    executeGestureAction(gesturePreferences.mediaPlayGesture.get())
  }

  fun handleMediaNext() {
    executeGestureAction(gesturePreferences.mediaNextGesture.get(), isRight = true)
  }

  fun handleMediaPrevious() {
    executeGestureAction(gesturePreferences.mediaPreviousGesture.get(), isLeft = true)
  }
}
