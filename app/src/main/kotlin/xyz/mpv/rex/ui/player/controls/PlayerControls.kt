package xyz.mpv.rex.ui.player.controls

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as M3Surface
import xyz.mpv.rex.ui.player.controls.components.glassSurface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.preferences.preference.deleteAndGet
import xyz.mpv.rex.preferences.preference.plusAssign
import xyz.mpv.rex.preferences.preference.minusAssign
import xyz.mpv.rex.ui.player.Decoder.Companion.getDecoderFromValue
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerUpdates
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.PlayerTutorialManager
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.TrackSelector
import xyz.mpv.rex.ui.player.VideoAspect
import xyz.mpv.rex.ui.player.controls.components.BrightnessSlider
import xyz.mpv.rex.ui.player.controls.components.CompactSpeedIndicator
import xyz.mpv.rex.ui.player.controls.components.ControlsButton
import xyz.mpv.rex.ui.player.controls.components.LockHint
import xyz.mpv.rex.ui.player.controls.components.MultipleSpeedPlayerUpdate
import xyz.mpv.rex.ui.player.controls.components.PromptResumePlayerUpdate
import xyz.mpv.rex.ui.player.controls.components.ResumePlaybackPromptDialog
import xyz.mpv.rex.ui.player.controls.components.ResumedFromPlayerUpdate
import xyz.mpv.rex.ui.player.controls.components.SeekPlayerUpdate
import xyz.mpv.rex.ui.player.controls.components.SeekbarWithTimers
import xyz.mpv.rex.ui.player.controls.components.SlideToUnlock
import xyz.mpv.rex.ui.player.controls.components.SpeedControlSlider
import xyz.mpv.rex.ui.player.controls.components.SystemStatsOverlay
import xyz.mpv.rex.ui.player.controls.components.TextPlayerUpdate
import xyz.mpv.rex.ui.player.controls.components.VolumeSlider
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.playerRippleConfiguration
import xyz.mpv.rex.ui.theme.spacing
import xyz.mpv.rex.ui.utils.LocalBackStack
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject
import kotlin.math.abs

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> =
  tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
  )

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> =
  tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
  )

@OptIn(
  ExperimentalAnimationGraphicsApi::class,
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
@Suppress("CyclomaticComplexMethod", "ViewModelForwarding")
fun PlayerControls(
  viewModel: PlayerViewModel,
  onBackPress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = MaterialTheme.spacing
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val playerPreferences = koinInject<PlayerPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  val gesturePreferences = koinInject<GesturePreferences>()
  val playerTutorialManager = koinInject<PlayerTutorialManager>()
  val enableReleaseToCancel by gesturePreferences.enableReleaseToCancel.collectAsState()
  val showSystemStatusBar by playerPreferences.showSystemStatusBar.collectAsState()
  val showSystemNavigationBar by playerPreferences.showSystemNavigationBar.collectAsState()
  val playerGradientOpacity by playerPreferences.playerGradientOpacity.collectAsState()
  val interactionSource = remember { MutableInteractionSource() }
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekBarShown by viewModel.seekBarShown.collectAsState()
  val pausedForCache by MPVLib.propBoolean["paused-for-cache"].collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val demuxerCacheDuration by MPVLib.propFloat["demuxer-cache-duration"].collectAsState()
  val cacheBufferingState by MPVLib.propInt["cache-buffering-state"].collectAsState()
  val precisePosition by viewModel.precisePosition.collectAsState()
  val preciseDuration by viewModel.preciseDuration.collectAsState()
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()

  val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val doubleTapSeekBasePos by viewModel.doubleTapSeekBasePos.collectAsState()
  val showDoubleTapOvals by playerPreferences.showDoubleTapOvals.collectAsState()
  val showCircularDoubleTapSeek by playerPreferences.showCircularDoubleTapSeek.collectAsState()
  val showSeekTime by playerPreferences.showSeekTimeWhileSeeking.collectAsState()
  val showSpeedIndicatorOverlay by playerPreferences.showSpeedIndicatorOverlay.collectAsState()
  val hideOsdText by playerPreferences.hideOsdText.collectAsState()
  val isLoadingFile by viewModel.isLoadingFile.collectAsState()
  val mediaTitle by viewModel.mediaTitle.collectAsState()
  val mediaIdentifier by viewModel.mediaIdentifier.collectAsState()
  val playlistItems by viewModel.playlistManager.playlist.collectAsState()
  val playlistIndex by viewModel.playlistManager.currentIndex.collectAsState()
  var isSeeking by remember { mutableStateOf(false) }
  var dragStartValue by remember { mutableStateOf(-1f) }
  var isCloseToStart by remember { mutableStateOf(false) }
  var changeCount by remember { mutableStateOf(0) }
  var resetControlsTimestamp by remember { mutableStateOf(0L) }

  LaunchedEffect(playlistIndex, mediaIdentifier.ifBlank { mediaTitle }) {
    isSeeking = false
    dragStartValue = -1f
    isCloseToStart = false
    changeCount = 0
  }
  val seekText by viewModel.seekText.collectAsState()
  val currentChapter by MPVLib.propInt["chapter"].collectAsState()
  val mpvDecoder by MPVLib.propString["hwdec-current"].collectAsState()
  val decoder by remember { derivedStateOf { getDecoderFromValue(mpvDecoder ?: "auto") } }
  val isSpeedNonOne by remember(playbackSpeed) {
    derivedStateOf { abs((playbackSpeed ?: 1f) - 1f) > 0.001f }
  }
  val playerTimeToDisappear by playerPreferences.playerTimeToDisappear.collectAsState()
  val chapters by viewModel.chapters.collectAsState(persistentListOf())
  val playlist by viewModel.playlistManager.playlist.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val haptic = LocalHapticFeedback.current

  val customButtons by viewModel.customButtons.collectAsState()
    
  val abLoopA by viewModel.abLoopA.collectAsState()
  val abLoopB by viewModel.abLoopB.collectAsState()
  val isABLoopExpanded by viewModel.isABLoopExpanded.collectAsState()
  val isFrameNavigationExpanded by viewModel.isFrameNavigationExpanded.collectAsState()
  val isSnapshotLoading by viewModel.isSnapshotLoading.collectAsState()

  val isGestureSeeking by viewModel.isGestureSeeking.collectAsState()
  val isVerticalGestureActive by viewModel.isVerticalGestureActive.collectAsState()

  LaunchedEffect(controlsShown) {
    if (controlsShown) {
      val hasActiveSubtitle = (MPVLib.getPropertyInt("sid") ?: 0) != 0
      if (hasActiveSubtitle && playerTutorialManager.shouldShowSubtitleDragHint()) {
        viewModel.playerUpdate.value = PlayerUpdates.ShowText("Swipe vertically at bottom to adjust subtitle position")
        playerTutorialManager.incrementSubtitleDragHintCount()
        delay(3000L)
        if (viewModel.playerUpdate.value is PlayerUpdates.ShowText) {
          val currentText = (viewModel.playerUpdate.value as PlayerUpdates.ShowText).value
          if (currentText == "Swipe vertically at bottom to adjust subtitle position") {
            viewModel.playerUpdate.value = PlayerUpdates.None
          }
        }
      }
    }
  }

  val onOpenSheet: (Sheets) -> Unit = {
    viewModel.sheetShown.update { _ -> it }
    if (it == Sheets.None) {
      viewModel.showControls()
    } else {
      viewModel.hideControls()
      viewModel.panelShown.update { Panels.None }
    }
  }

  val onOpenPanel: (Panels) -> Unit = {
    viewModel.panelShown.update { _ -> it }
    if (it == Panels.None) {
      viewModel.showControls()
    } else {
      viewModel.hideControls()
      viewModel.sheetShown.update { Sheets.None }
    }
  }

  val topRightControlsPref by appearancePreferences.topRightControls.collectAsState()
  val bottomRightControlsPref by appearancePreferences.bottomRightControls.collectAsState()
  val bottomLeftControlsPref by appearancePreferences.bottomLeftControls.collectAsState()
  val portraitBottomControlsPref by appearancePreferences.portraitBottomControls.collectAsState()

  val (topRightButtons, bottomRightButtons, bottomLeftButtons) =
    remember(
      topRightControlsPref,
      bottomRightControlsPref,
      bottomLeftControlsPref,
    ) {
      val usedButtons = mutableSetOf<xyz.mpv.rex.preferences.PlayerButton>()
      val topR = appearancePreferences.parseButtons(topRightControlsPref, usedButtons)
      val bottomR = appearancePreferences.parseButtons(bottomRightControlsPref, usedButtons)
      val bottomL = appearancePreferences.parseButtons(bottomLeftControlsPref, usedButtons)
      listOf(topR, bottomR, bottomL)
    }

  val portraitBottomButtonsRaw = remember(portraitBottomControlsPref) {
    appearancePreferences.parseButtons(portraitBottomControlsPref, mutableSetOf())
  }

  val portraitVisibleButtons = remember(portraitBottomButtonsRaw, chapters, playlist, viewModel) {
    portraitBottomButtonsRaw.filter { button ->
      when (button) {
        xyz.mpv.rex.preferences.PlayerButton.BOOKMARKS_CHAPTERS -> chapters.isNotEmpty()
        xyz.mpv.rex.preferences.PlayerButton.SHUFFLE -> viewModel.hasPlaylistSupport()
        xyz.mpv.rex.preferences.PlayerButton.CURRENT_CHAPTER -> false
        xyz.mpv.rex.preferences.PlayerButton.NONE -> false
        else -> true
      }
    }
  }

  var isUnlockSliderDragging by remember { mutableStateOf(false) }

  LaunchedEffect(
    controlsShown,
    paused,
    isSeeking,
    resetControlsTimestamp,
    areControlsLocked,
    isUnlockSliderDragging,
    playerTimeToDisappear,
  ) {
    if (controlsShown && paused == false && !isSeeking && !isUnlockSliderDragging) {
      if (areControlsLocked) {
        // Use 2 second delay when controls are locked
        delay(2000L)
        viewModel.hideControls()
      } else if (playerTimeToDisappear > 0) {
        delay(playerTimeToDisappear.toLong())
        viewModel.hideControls()
      }
    }
  }

  val transparentOverlay by animateFloatAsState(
    if (controlsShown && !areControlsLocked) .8f else 0f,
    animationSpec = playerControlsExitAnimationSpec(),
    label = "controls_transparent_overlay",
  )

  GestureHandler(
    viewModel = viewModel,
    interactionSource = interactionSource,
  )

  DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, showDoubleTapOvals, showSeekTime, showSeekTime, interactionSource)

  CompositionLocalProvider(
    LocalRippleConfiguration provides playerRippleConfiguration,
    LocalPlayerButtonsClickEvent provides { resetControlsTimestamp = System.currentTimeMillis() },
  ) {
    CompositionLocalProvider(
      LocalContentColor provides Color.White,
    ) {
      CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
      ) {
      val configuration = LocalConfiguration.current
      val isPortrait by remember(configuration) {
        derivedStateOf { configuration.orientation == ORIENTATION_PORTRAIT }
      }

      ConstraintLayout(
        modifier =
          modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                Pair(0f, Color.Black),
                Pair(.2f, Color.Transparent),
                Pair(.7f, Color.Transparent),
                Pair(1f, Color.Black),
              ),
              alpha = transparentOverlay * playerGradientOpacity,
            ),
      ) {
        val (topLeftControls, topRightControls) = createRefs()
        val (volumeSlider, brightnessSlider) = createRefs()
        val unlockControlsButton = createRef()
        val (bottomRightControls, bottomLeftControls) = createRefs()
        val playerPauseButton = createRef()
        val seekbar = createRef()
        val (playerUpdates, playerLockHint) = createRefs()
        val systemStatsRef = createRef()
        val (customLeftButtonsRef, customRightButtonsRef) = createRefs()
        val customButtonsPortraitRef = createRef()
        val floatingABLoop = createRef()
        val floatingFrameNav = createRef()

        val bottomControlsBelowSeekbar by playerPreferences.bottomControlsBelowSeekbar.collectAsState()

        val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
        val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
        val brightness by viewModel.currentBrightness.collectAsState()
        val volume by viewModel.currentVolume.collectAsState()
        val mpvVolume by MPVLib.propInt["volume"].collectAsState()
        val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
        val reduceMotion by playerPreferences.reduceMotion.collectAsState()
        val advancedPreferences = koinInject<AdvancedPreferences>()
        val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()

        val activity = LocalActivity.current as PlayerActivity
        val aspect by viewModel.videoAspect.collectAsState()
        val currentZoom by viewModel.videoZoom.collectAsState()

        val rawMediaTitle by MPVLib.propString["media-title"].collectAsState()
        val mediaTitle by remember(rawMediaTitle, activity) {
          derivedStateOf {
            val title = activity.getTitleForControls()
            if (title.startsWith("http://") || title.startsWith("https://") || title.contains(".m3u8") || title.contains(".m3u")) {
              val raw = rawMediaTitle
              if (raw != null && raw.isNotBlank() && !raw.startsWith("http://") && !raw.startsWith("https://") && !raw.contains(".m3u8") && !raw.contains(".m3u")) {
                raw
              } else {
                title
              }
            } else {
              title
            }
          }
        }

        // Slider display duration: 1000ms shown + 300ms exit animation = 1300ms total
        val sliderDisplayDuration = 1000L

        val volumeSliderTimestamp by viewModel.volumeSliderTimestamp.collectAsState()
        val brightnessSliderTimestamp by viewModel.brightnessSliderTimestamp.collectAsState()

        // Track timestamp to restart timer on every gesture event
        LaunchedEffect(volumeSliderTimestamp) {
          if (isVolumeSliderShown && volumeSliderTimestamp > 0) {
            delay(sliderDisplayDuration)
            viewModel.isVolumeSliderShown.update { false }
          }
        }

        LaunchedEffect(brightnessSliderTimestamp) {
          if (isBrightnessSliderShown && brightnessSliderTimestamp > 0) {
            delay(sliderDisplayDuration)
            viewModel.isBrightnessSliderShown.update { false }
          }
        }

        val areSlidersShown = isBrightnessSliderShown || isVolumeSliderShown
        val areButtonsVisible = controlsShown && !areControlsLocked && !areSlidersShown

        val abLoopVerticalBias by animateFloatAsState(
          targetValue = if (areButtonsVisible) {
            if (isPortrait) 0.80f else 0.65f
          } else {
            if (isPortrait) 0.86f else 0.78f
          },
          label = "abLoopVerticalBias"
        )

        val abLoopActive = isABLoopExpanded
        val frameNavActive = isFrameNavigationExpanded

        val frameNavYOffset by animateDpAsState(
          targetValue = if (abLoopActive && frameNavActive) -48.dp else 0.dp,
          label = "frameNavYOffset"
        )

        val osdTopMargin by animateDpAsState(
          targetValue = if (areButtonsVisible) {
            if (isPortrait) 104.dp else 68.dp
          } else {
            if (isPortrait) 64.dp else 32.dp
          },
          label = "osdTopMargin"
        )

        AnimatedVisibility(
          isBrightnessSliderShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) {
                if (swapVolumeAndBrightness) -it else it
              } + fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) {
                if (swapVolumeAndBrightness) -it else it
              } + fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(brightnessSlider) {
              if (swapVolumeAndBrightness) {
                start.linkTo(parent.start, spacing.extraLarge)
              } else {
                end.linkTo(parent.end, spacing.extraLarge)
              }
              top.linkTo(parent.top, spacing.larger)
              bottom.linkTo(parent.bottom, spacing.extraLarge)
            },
        ) { BrightnessSlider(brightness, 0f..1f, isActive = isVerticalGestureActive) }

        AnimatedVisibility(
          isVolumeSliderShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { if (swapVolumeAndBrightness) it else -it } + fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { if (swapVolumeAndBrightness) it else -it } + fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier.constrainAs(volumeSlider) {
              if (swapVolumeAndBrightness) {
                end.linkTo(parent.end, spacing.extraLarge)
              } else {
                start.linkTo(parent.start, spacing.extraLarge)
              }
              top.linkTo(parent.top, spacing.larger)
              bottom.linkTo(parent.bottom, spacing.extraLarge)
            },
        ) {
          val boostCap by audioPreferences.volumeBoostCap.collectAsState()
          val displayVolumeAsPercentage by playerPreferences.displayVolumeAsPercentage.collectAsState()
          
          // Show if boost is allowed (boostCap > 0) OR if we are currently boosted (> 100)
          val currentBoost = (mpvVolume ?: 100) - 100
          val showBoost = boostCap > 0 || currentBoost > 0
          val effBoostCap = maxOf(boostCap, currentBoost)
          
          VolumeSlider(
            volume,
            mpvVolume = mpvVolume ?: 100,
            range = 0..viewModel.maxVolume,
            boostRange = if (showBoost) 0..effBoostCap else null,
            displayAsPercentage = displayVolumeAsPercentage,
            isActive = isVerticalGestureActive
          )
        }

        val holdForMultipleSpeed by playerPreferences.holdForMultipleSpeed.collectAsState()
        val currentPlayerUpdate by viewModel.playerUpdate.collectAsState()
        val aspectRatio by viewModel.videoAspect.collectAsState()
        val currentAspectRatio by viewModel.currentAspectRatio.collectAsState()
        val videoZoom by viewModel.videoZoom.collectAsState()

        LaunchedEffect(currentPlayerUpdate, aspectRatio, videoZoom) {
          if (currentPlayerUpdate is PlayerUpdates.MultipleSpeed ||
            currentPlayerUpdate is PlayerUpdates.DynamicSpeedControl ||
            currentPlayerUpdate is PlayerUpdates.None
          ) {
            return@LaunchedEffect
          }
          val dismissDelay = if (currentPlayerUpdate is PlayerUpdates.ResumedFrom || currentPlayerUpdate is PlayerUpdates.PromptResume) 4000L else 2000L
          delay(dismissDelay)
          viewModel.playerUpdate.update { PlayerUpdates.None }
        }

        val isSpeedLocked by viewModel.isSpeedLocked.collectAsState()
        val isSpeedUpdate = currentPlayerUpdate is PlayerUpdates.MultipleSpeed ||
          currentPlayerUpdate is PlayerUpdates.DynamicSpeedControl ||
          (currentPlayerUpdate is PlayerUpdates.SpeedLockHint && !(currentPlayerUpdate as PlayerUpdates.SpeedLockHint).isLocked)
        val shouldShowSpeedUpdate = if (isSpeedUpdate) showSpeedIndicatorOverlay else true

        AnimatedVisibility(
          shouldShowSpeedUpdate && (currentPlayerUpdate !is PlayerUpdates.None || isSpeedLocked || (doubleTapSeekAmount != 0 && !hideOsdText)),
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .constrainAs(playerUpdates) {
                linkTo(parent.start, parent.end)
                top.linkTo(parent.top, margin = osdTopMargin)
              },
        ) {
          val currentPlaybackSpeed = playbackSpeed ?: 1f
          if (doubleTapSeekAmount != 0 && !hideOsdText && !showDoubleTapOvals && !showCircularDoubleTapSeek) {
            val seekAmount = doubleTapSeekAmount
            // Use the position captured before the first seek so the displayed
            // target time stays correct even after MPV's time-pos updates.
            val basePos = doubleTapSeekBasePos ?: (position ?: 0)
            val targetTime = Utils.prettyTime(basePos + seekAmount)
            val deltaSign = if (seekAmount >= 0) "+" else "-"
            val deltaValue = Utils.prettyTime(abs(seekAmount))
            val seekDelta = "[$deltaSign$deltaValue]"
            SeekPlayerUpdate(
              currentTime = targetTime,
              seekDelta = seekDelta,
              modifier = Modifier,
            )
          } else if (isSpeedLocked && currentPlayerUpdate is PlayerUpdates.None && abs(currentPlaybackSpeed - 1f) > 0.01f) {
            CompactSpeedIndicator(
              currentSpeed = currentPlaybackSpeed,
              onReset = {
                viewModel.isSpeedLocked.value = false
                viewModel.resetPlaybackSpeed()
              }
            )
          } else {
            when (currentPlayerUpdate) {
              is PlayerUpdates.MultipleSpeed -> {
                if (showSpeedIndicatorOverlay) {
                  MultipleSpeedPlayerUpdate(currentSpeed = holdForMultipleSpeed)
                }
              }
              is PlayerUpdates.DynamicSpeedControl -> {
                if (showSpeedIndicatorOverlay) {
                  val speedUpdate = currentPlayerUpdate as PlayerUpdates.DynamicSpeedControl
                  val currentSpeed = speedUpdate.speed
                  CompactSpeedIndicator(currentSpeed = currentSpeed)
                }
              }

              is PlayerUpdates.SpeedLockHint -> {
                val hintUpdate = currentPlayerUpdate as PlayerUpdates.SpeedLockHint
                val currentSpeed = hintUpdate.speed
                val isLocked = hintUpdate.isLocked
                
                if (isLocked || showSpeedIndicatorOverlay) {
                  CompactSpeedIndicator(
                    currentSpeed = currentSpeed,
                    onReset = if (isLocked) {
                        {
                            viewModel.isSpeedLocked.value = false
                            viewModel.resetPlaybackSpeed()
                        }
                    } else null
                  )
                }
              }

            is PlayerUpdates.AspectRatio -> {
              val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
              val displayText = if (currentAspectRatio > 0) {
                // Custom aspect ratio - try to find its label first
                val customLabel = customRatiosSet.firstNotNullOfOrNull { str ->
                  val parts = str.split("|")
                  if (parts.size == 2) {
                    val savedRatio = parts[1].toDoubleOrNull()
                    if (savedRatio != null && kotlin.math.abs(savedRatio - currentAspectRatio) < 0.01) {
                      parts[0] // Return the label
                    } else null
                  } else null
                }
                
                customLabel ?: run {
                  // No custom label found, use preset names or format as ratio
                  val ratio = currentAspectRatio
                  when {
                    kotlin.math.abs(ratio - 16.0/9.0) < 0.01 -> "16:9"
                    kotlin.math.abs(ratio - 4.0/3.0) < 0.01 -> "4:3"
                    kotlin.math.abs(ratio - 16.0/10.0) < 0.01 -> "16:10"
                    kotlin.math.abs(ratio - 21.0/9.0) < 0.01 -> "21:9"
                    kotlin.math.abs(ratio - 32.0/9.0) < 0.01 -> "32:9"
                    kotlin.math.abs(ratio - 1.0) < 0.01 -> "1:1"
                    kotlin.math.abs(ratio - 2.35) < 0.01 -> "2.35:1"
                    kotlin.math.abs(ratio - 2.39) < 0.01 -> "2.39:1"
                    else -> String.format("%.2f:1", ratio)
                  }
                }
              } else {
                // Standard mode (Fit/Crop/Stretch)
                stringResource(aspectRatio.titleRes)
              }
              TextPlayerUpdate(displayText)
            }
            is PlayerUpdates.ShowText ->
              TextPlayerUpdate(
                (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                modifier = Modifier.widthIn(min = 120.dp),
              )

            is PlayerUpdates.VideoZoom -> {
              val zoomPercentage = (videoZoom * 100).toInt()
              TextPlayerUpdate(
                text = String.format("Zoom:%3d%%", zoomPercentage), 
                modifier = Modifier, // Let content size determine width
              )
            }

            is PlayerUpdates.HorizontalSeek -> {
              if (!hideOsdText) {
                val seekUpdate = currentPlayerUpdate as PlayerUpdates.HorizontalSeek
                SeekPlayerUpdate(
                  currentTime = seekUpdate.currentTime,
                  seekDelta = "[${seekUpdate.seekDelta}]",
                  modifier = Modifier, // Let content size determine width
                )
              }
            }
            is PlayerUpdates.RepeatMode -> {
              val mode = (currentPlayerUpdate as PlayerUpdates.RepeatMode).mode
              val text = when (mode) {
                xyz.mpv.rex.ui.player.RepeatMode.OFF -> "Repeat: Off"
                xyz.mpv.rex.ui.player.RepeatMode.ONE -> "Repeat: Current file"
                xyz.mpv.rex.ui.player.RepeatMode.ALL -> {
                  if (viewModel.hasPlaylistSupport()) {
                    "Repeat: All playlist"
                  } else {
                    "Repeat: Current file"
                  }
                }
              }
              TextPlayerUpdate(text)
            }

            is PlayerUpdates.Shuffle -> {
              val enabled = (currentPlayerUpdate as PlayerUpdates.Shuffle).enabled
              val text = if (enabled) {
                if (viewModel.hasPlaylistSupport()) {
                  "Shuffle: On"
                } else {
                  "Shuffle: Not available"
                }
              } else {
                "Shuffle: Off"
              }
              TextPlayerUpdate(text)
            }

            is PlayerUpdates.FrameInfo -> {
              val frameInfo = (currentPlayerUpdate as PlayerUpdates.FrameInfo)
              val text = if (frameInfo.totalFrames > 0) {
                "Frame: ${frameInfo.currentFrame}/${frameInfo.totalFrames}"
              } else {
                "Frame: ${frameInfo.currentFrame}"
              }
              TextPlayerUpdate(text)
            }

            is PlayerUpdates.ResumedFrom -> {
              val resumedUpdate = currentPlayerUpdate as PlayerUpdates.ResumedFrom
              ResumedFromPlayerUpdate(
                position = resumedUpdate.position,
                onRestart = {
                  viewModel.playerUpdate.value = PlayerUpdates.None
                  viewModel.restartFromBeginning()
                },
                modifier = Modifier,
              )
            }

            is PlayerUpdates.PromptResume -> {
              val promptUpdate = currentPlayerUpdate as PlayerUpdates.PromptResume
              PromptResumePlayerUpdate(
                position = promptUpdate.position,
                onResume = {
                  viewModel.playerUpdate.value = PlayerUpdates.None
                  viewModel.seekTo(promptUpdate.position)
                },
                modifier = Modifier,
              )
            }

            else -> {}
          }
        }
      }

        SystemStatsOverlay(
          visible = statisticsPage == 6,
          modifier = Modifier
            .windowInsetsPadding(
              WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
              ),
            )
            .padding(top = 16.dp, start = 28.dp)
            .constrainAs(systemStatsRef) {
              top.linkTo(parent.top)
              start.linkTo(parent.start)
            },
        )

        AnimatedVisibility(
            visible = areButtonsVisible && !isPortrait,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.constrainAs(customLeftButtonsRef) {
                start.linkTo(parent.start, spacing.large)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                verticalBias = 0.65f
                width = Dimension.preferredWrapContent
                height = Dimension.wrapContent
            }
        ) {
            val leftScrollState = rememberScrollState()
            LaunchedEffect(leftScrollState.isScrollInProgress) {
                if (leftScrollState.isScrollInProgress) {
                    while (leftScrollState.isScrollInProgress) {
                        resetControlsTimestamp = System.currentTimeMillis()
                        delay(1000)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .horizontalScroll(leftScrollState)
            ) {
                customButtons.filter { it.isLeft }.forEach { button ->
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = areButtonsVisible && !isPortrait,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.constrainAs(customRightButtonsRef) {
                end.linkTo(parent.end, spacing.large)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                verticalBias = 0.65f
                width = Dimension.preferredWrapContent
                height = Dimension.wrapContent
            }
        ) {
            val rightScrollState = rememberScrollState()
            LaunchedEffect(rightScrollState.isScrollInProgress) {
                if (rightScrollState.isScrollInProgress) {
                    while (rightScrollState.isScrollInProgress) {
                        resetControlsTimestamp = System.currentTimeMillis()
                        delay(1000)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rightScrollState, reverseScrolling = true)
            ) {
                customButtons.filter { !it.isLeft }.forEach { button ->
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = areButtonsVisible && isPortrait,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.constrainAs(customButtonsPortraitRef) {
                start.linkTo(parent.start, spacing.large)
                end.linkTo(parent.end, spacing.large)
                bottom.linkTo(seekbar.top, spacing.medium)
                width = Dimension.fillToConstraints
                height = Dimension.wrapContent
            }
        ) {
            val portraitScrollState = rememberScrollState()
            LaunchedEffect(portraitScrollState.isScrollInProgress) {
                if (portraitScrollState.isScrollInProgress) {
                    while (portraitScrollState.isScrollInProgress) {
                        resetControlsTimestamp = System.currentTimeMillis()
                        delay(1000)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .horizontalScroll(portraitScrollState)
            ) {
                customButtons.forEach { button ->
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = buttonInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButton(button.id)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetControlsTimestamp = System.currentTimeMillis()
                                    viewModel.callCustomButtonLongPress(button.id)
                                }
                            )
                    ) {
                        Text(
                            text = button.label,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .basicMarquee(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
          visible = controlsShown && areControlsLocked,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier =
            Modifier
              .constrainAs(unlockControlsButton) {
                bottom.linkTo(parent.bottom, spacing.extraLarge)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
              },
        ) {
          SlideToUnlock(
            onUnlock = { viewModel.unlockControls() },
            onDraggingChanged = { isDragging -> isUnlockSliderDragging = isDragging },
          )
        }

        AnimatedVisibility(
          visible =
            ((controlsShown && !areSlidersShown) && !areControlsLocked) || pausedForCache == true,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier =
            Modifier.constrainAs(playerPauseButton) {
              end.linkTo(parent.absoluteRight)
              start.linkTo(parent.absoluteLeft)
              if (isPortrait) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                verticalBias = 0.5f
              } else {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
              }
            },
        ) {
          val showLoadingCircle by playerPreferences.showLoadingCircle.collectAsState()
          val icon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
          val interaction = remember { MutableInteractionSource() }

          when {
            pausedForCache == true && showLoadingCircle -> {
              LoadingIndicator(
                modifier = Modifier.size(96.dp),
              )
            }

            controlsShown && !areControlsLocked -> {
              val buttonShadow =
                Brush.radialGradient(
                  0.0f to Color.Black.copy(alpha = 0.3f),
                  0.7f to Color.Transparent,
                  1.0f to Color.Transparent,
                )

              val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
              val surfaceColor = when {
                hideBackground -> Color.Transparent
                matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
              }
              val contentColor = when {
                matchTheme -> {
                    if (hideBackground) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                }
                else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
              }
              val borderColor = if (hideBackground) null else BorderStroke(
                1.dp,
                if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
              )

              if (playlistItems.isNotEmpty() && playlistIndex >= 0 && viewModel.hasPlaylistSupport()) {
                val canGoPrevious = viewModel.hasPrevious()
                val canGoNext = viewModel.hasNext()
                Row(
                  horizontalArrangement = Arrangement.spacedBy(24.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Surface(
                    modifier =
                      Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = canGoPrevious,
                          onClick = {
                            resetControlsTimestamp = System.currentTimeMillis()
                            viewModel.handleMediaPrevious()
                          },
                        )
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color = surfaceColor,
                    contentColor = contentColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = borderColor,
                  ) {
                    Icon(
                      imageVector = Icons.Default.SkipPrevious,
                      contentDescription = "Previous",
                      tint =
                        if (canGoPrevious) {
                          contentColor
                        } else {
                          contentColor.copy(alpha = 0.38f)
                        },
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.smaller),
                    )
                  }

                  Surface(
                    modifier =
                      Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(interaction, ripple(), onClick = {
                          resetControlsTimestamp = System.currentTimeMillis()
                          viewModel.handleMediaPlayPause()
                        })
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color = surfaceColor,
                    contentColor = contentColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = borderColor,
                  ) {
                    Image(
                      painter = rememberAnimatedVectorPainter(icon, paused == false),
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.small),
                      contentDescription = null,
                      colorFilter = ColorFilter.tint(contentColor),
                    )
                  }

                  Surface(
                    modifier =
                      Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                          enabled = canGoNext,
                          onClick = {
                            resetControlsTimestamp = System.currentTimeMillis()
                            viewModel.handleMediaNext()
                          },
                        )
                        .then(
                          if (hideBackground) {
                            Modifier.background(brush = buttonShadow, shape = CircleShape)
                          } else {
                            Modifier
                          },
                        ),
                    shape = CircleShape,
                    color = surfaceColor,
                    contentColor = contentColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = borderColor,
                  ) {
                    Icon(
                      imageVector = Icons.Default.SkipNext,
                      contentDescription = "Next",
                      tint =
                        if (canGoNext) {
                          contentColor
                        } else {
                          contentColor.copy(alpha = 0.38f)
                        },
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.smaller),
                    )
                  }
                }
              } else {
                Surface(
                  modifier =
                    Modifier
                      .size(56.dp)
                      .clip(CircleShape)
                      .clickable(interaction, ripple(), onClick = {
                        resetControlsTimestamp = System.currentTimeMillis()
                        viewModel.pauseUnpause()
                      })
                      .then(
                        if (hideBackground) {
                          Modifier.background(brush = buttonShadow, shape = CircleShape)
                        } else {
                          Modifier
                        },
                      ),
                  shape = CircleShape,
                  color = surfaceColor,
                  contentColor = contentColor,
                  tonalElevation = 0.dp,
                  shadowElevation = 0.dp,
                  border = borderColor,
                ) {
                  Image(
                    painter = rememberAnimatedVectorPainter(icon, paused == false),
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(MaterialTheme.spacing.small),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(contentColor),
                  )
                }
              }
            }
          }
        }

        AnimatedVisibility(
          visible = (controlsShown || seekBarShown) && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = if (!bottomControlsBelowSeekbar) navBarPadding.calculateBottomPadding() else 0.dp
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(seekbar) {
                if (isPortrait) {
                  if (bottomControlsBelowSeekbar) {
                    bottom.linkTo(bottomRightControls.top, spacing.smaller) // Tight gap to grid below
                  } else {
                    bottom.linkTo(parent.bottom, spacing.large) // Space from screen bottom
                  }
                } else {
                  if (bottomControlsBelowSeekbar) {
                    val bottomMargin = 45.dp + spacing.medium + spacing.small
                    bottom.linkTo(parent.bottom, bottomMargin)
                  } else {
                    bottom.linkTo(parent.bottom, spacing.medium)
                  }
                }
                start.linkTo(parent.start, spacing.large)
                end.linkTo(parent.end, spacing.large)
              },
        ) {
          val invertDuration by playerPreferences.invertDuration.collectAsState()
          val seekbarStyle by appearancePreferences.seekbarStyle.collectAsState()
          val effectiveDuration = if (preciseDuration > 0f) preciseDuration else if (isLoadingFile) 0f else duration?.toFloat() ?: 0f

          // Calculate read-ahead position (current position + buffered cache time)
          // No keys for remember, derivedStateOf reactively tracks read dependencies
          val readAheadPosition by remember {
            derivedStateOf {
              val currentPos = position?.toFloat() ?: 0f
              val cacheDuration = demuxerCacheDuration ?: 0f
              val totalDuration = effectiveDuration
              val isBuffering = cacheBufferingState ?: 0

              // If cache duration is available and valid, use it (up to 60 seconds)
              if (cacheDuration > 0.1f) {
                (currentPos + cacheDuration).coerceAtMost(totalDuration)
              } else if (isBuffering > 0 && isBuffering < 100) {
                // Show estimated buffer when actively buffering (up to 60 seconds)
                val estimatedBuffer = (isBuffering / 100f) * 60f
                (currentPos + estimatedBuffer).coerceAtMost(totalDuration)
              } else {
                // When not actively buffering and cache is full, show 1 minute buffer
                (currentPos + 60f).coerceAtMost(totalDuration)
              }
            }
          }

          key(playlistIndex, mediaIdentifier.ifBlank { mediaTitle }) {
            SeekbarWithTimers(
              position = { precisePosition },
              duration = effectiveDuration,
              onValueChange = { newValue ->
                if (dragStartValue == -1f) {
                  dragStartValue = precisePosition
                  changeCount = 0
                }
                changeCount++
                isSeeking = true
                resetControlsTimestamp = System.currentTimeMillis()

                val durationFloat = effectiveDuration
                val threshold = (durationFloat * 0.08f).coerceIn(15f, 400f)
                val close = enableReleaseToCancel && changeCount > 1 && abs(newValue - dragStartValue) < threshold

                if (close) {
                  isCloseToStart = true
                  viewModel.playerUpdate.value = PlayerUpdates.ShowText("Release to cancel")
                } else {
                  if (isCloseToStart) {
                    isCloseToStart = false
                    viewModel.playerUpdate.value = PlayerUpdates.None
                  }
                }
                viewModel.seekTo(newValue.toInt())
                viewModel.autoHideControls()
              },
              onValueChangeFinished = {
                if (isCloseToStart) {
                  viewModel.seekTo(dragStartValue.toInt())
                  viewModel.playerUpdate.value = PlayerUpdates.None
                }
                isSeeking = false
                dragStartValue = -1f
                isCloseToStart = false
                changeCount = 0
                resetControlsTimestamp = System.currentTimeMillis()
                viewModel.showControls()
              },
              timersInverted = Pair(false, invertDuration),
              durationTimerOnCLick = {
                resetControlsTimestamp = System.currentTimeMillis()
                playerPreferences.invertDuration.set(!invertDuration)
              },
              positionTimerOnClick = {},
              chapters = chapters.toImmutableList(),
              paused = paused ?: false,
              readAheadValue = { readAheadPosition },
              seekbarStyle = seekbarStyle,
              loopStart = abLoopA?.toFloat(),
              loopEnd = abLoopB?.toFloat(),
              isGestureSeeking = isGestureSeeking,
              isCancelActive = isCloseToStart
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = 0.dp
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(topLeftControls) {
                top.linkTo(parent.top, if (isPortrait) spacing.extraLarge else spacing.small)
                start.linkTo(parent.start, spacing.large)
                if (isPortrait) {
                  width = Dimension.fillToConstraints
                  end.linkTo(parent.end, spacing.large)
                } else {
                  width = Dimension.fillToConstraints
                  end.linkTo(topRightControls.start, spacing.extraSmall)
                }
              },
        ) {
          if (isPortrait) {
            TopPlayerControlsPortrait(
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              viewModel = viewModel,
            )
          } else {
            TopLeftPlayerControlsLandscape(
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              viewModel = viewModel,
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !isPortrait,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier
              .then(
                if (showSystemStatusBar) {
                  Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                  Modifier
                }
              )
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = 0.dp
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(topRightControls) {
                top.linkTo(parent.top, spacing.small)
                end.linkTo(parent.end, spacing.large)
              },
        ) {
          TopRightPlayerControlsLandscape(
            buttons = topRightButtons,
            chapters = chapters,
            currentChapter = currentChapter,
            isSpeedNonOne = isSpeedNonOne,
            currentZoom = videoZoom,
            aspect = aspect,
            mediaTitle = mediaTitle,
            hideBackground = hideBackground,
            decoder = decoder,
            playbackSpeed = playbackSpeed ?: 1f,
            onBackPress = onBackPress,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            viewModel = viewModel,
            activity = activity,
          )
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !areSlidersShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = if (bottomControlsBelowSeekbar) navBarPadding.calculateBottomPadding() else 0.dp
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(bottomRightControls) {
                if (isPortrait) {
                  if (bottomControlsBelowSeekbar) {
                    bottom.linkTo(parent.bottom, spacing.large) // Space from screen bottom
                  } else {
                    bottom.linkTo(seekbar.top, spacing.smaller) // Tight gap to seekbar
                  }
                  start.linkTo(parent.start, spacing.medium)
                  end.linkTo(parent.end, spacing.medium)
                  width = Dimension.fillToConstraints
                } else {
                  if (bottomControlsBelowSeekbar) {
                    bottom.linkTo(parent.bottom, spacing.medium)
                  } else {
                    bottom.linkTo(seekbar.top, spacing.small)
                  }
                  end.linkTo(parent.end, spacing.medium)
                }
              },
        ) {
          if (isPortrait) {
            BottomPlayerControlsPortrait(
              buttons = portraitVisibleButtons,
              chapters = chapters,
              currentChapter = currentChapter,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = videoZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              decoder = decoder,
              playbackSpeed = playbackSpeed ?: 1f,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
              bottomPadding = if (bottomControlsBelowSeekbar) spacing.medium else 0.dp,
            )
          } else {
            BottomRightPlayerControlsLandscape(
              buttons = bottomRightButtons,
              chapters = chapters,
              currentChapter = currentChapter,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = videoZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              decoder = decoder,
              playbackSpeed = playbackSpeed ?: 1f,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
            )
          }
        }

        AnimatedVisibility(
          visible = controlsShown && !areControlsLocked && !isPortrait && !areSlidersShown,
          enter =
            if (!reduceMotion) {
              slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                fadeIn(playerControlsEnterAnimationSpec())
            } else {
              fadeIn(playerControlsEnterAnimationSpec())
            },
          exit =
            if (!reduceMotion) {
              slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                fadeOut(playerControlsExitAnimationSpec())
            } else {
              fadeOut(playerControlsExitAnimationSpec())
            },
          modifier =
            Modifier
              .then(
                if (showSystemNavigationBar) {
                  val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                  Modifier.padding(
                    start = navBarPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = navBarPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = if (bottomControlsBelowSeekbar) navBarPadding.calculateBottomPadding() else 0.dp
                  )
                } else {
                  Modifier
                }
              )
              .constrainAs(bottomLeftControls) {
                if (bottomControlsBelowSeekbar) {
                  // Bottom controls at very bottom - more margin for navigation bar
                  bottom.linkTo(parent.bottom, spacing.medium)
                } else {
                  // Bottom controls above seekbar
                  bottom.linkTo(seekbar.top, spacing.small)
                }
                start.linkTo(parent.start, spacing.medium)
                width = Dimension.fillToConstraints
                end.linkTo(bottomRightControls.start, spacing.small)
              },
        ) {
          BottomLeftPlayerControlsLandscape(
            buttons = bottomLeftButtons,
            chapters = chapters,
            currentChapter = currentChapter,
            isSpeedNonOne = isSpeedNonOne,
            currentZoom = videoZoom,
            aspect = aspect,
            mediaTitle = mediaTitle,
            hideBackground = hideBackground,
            decoder = decoder,
            playbackSpeed = playbackSpeed ?: 1f,
            onBackPress = onBackPress,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            viewModel = viewModel,
            activity = activity,
          )
        }

        AnimatedVisibility(
          visible = currentPlayerUpdate is PlayerUpdates.SpeedLockHint,
          enter = fadeIn(playerControlsEnterAnimationSpec()),
          exit = fadeOut(playerControlsExitAnimationSpec()),
          modifier = Modifier.constrainAs(playerLockHint) {
            bottom.linkTo(parent.bottom, if (isPortrait) 64.dp else 32.dp)
            linkTo(parent.start, parent.end)
          }
        ) {
          if (currentPlayerUpdate is PlayerUpdates.SpeedLockHint) {
            val update = currentPlayerUpdate as PlayerUpdates.SpeedLockHint
            LockHint(text = if (update.isLocked) "Speed Locked" else "Swipe up to lock")
          }
        }

        AnimatedVisibility(
          visible = isABLoopExpanded,
          enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { it }),
          exit = fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { it }),
          modifier = Modifier.constrainAs(floatingABLoop) {
            end.linkTo(parent.end, spacing.extraLarge)
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            verticalBias = abLoopVerticalBias
          }
        ) {
          val buttonSize = 40.dp
          Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.height(buttonSize),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 4.dp),
            ) {

              Surface(
                shape = CircleShape,
                color = if (abLoopA != null) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                modifier = Modifier
                  .height(buttonSize - 4.dp)
                  .widthIn(min = buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = { viewModel.setLoopA() }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = if (abLoopA != null) viewModel.formatTimestamp(abLoopA!!) else "A",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abLoopA != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = if (abLoopA != null) 8.dp else 0.dp),
                  )
                }
              }

              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                  .size(buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = {
                    viewModel.clearABLoop()
                    viewModel.toggleABLoopExpanded()
                  }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Loop",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Surface(
                shape = CircleShape,
                color = if (abLoopB != null) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                modifier = Modifier
                  .height(buttonSize - 4.dp)
                  .widthIn(min = buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = { viewModel.setLoopB() }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = if (abLoopB != null) viewModel.formatTimestamp(abLoopB!!) else "B",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abLoopB != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = if (abLoopB != null) 8.dp else 0.dp),
                  )
                }
              }

              val context = LocalContext.current
              val canCut = abLoopA != null && abLoopB != null
              Surface(
                shape = CircleShape,
                color = if (canCut) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, if (canCut) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                  .size(buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = {
                    if (canCut) {
                      onOpenSheet(Sheets.ClipExport)
                    } else {
                      android.widget.Toast.makeText(context, context.getString(R.string.ab_loop_set_both_points), android.widget.Toast.LENGTH_SHORT).show()
                    }
                  }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = stringResource(R.string.ab_loop_cut_clip),
                    tint = if (canCut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }

        AnimatedVisibility(
          visible = isFrameNavigationExpanded,
          enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { it }),
          exit = fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { it }),
          modifier = Modifier
            .constrainAs(floatingFrameNav) {
              end.linkTo(parent.end, spacing.extraLarge)
              top.linkTo(parent.top)
              bottom.linkTo(parent.bottom)
              verticalBias = abLoopVerticalBias
            }
            .offset(y = frameNavYOffset)
        ) {
          val buttonSize = 40.dp
          val context = LocalContext.current
          Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.height(buttonSize),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 4.dp),
            ) {
              // Previous frame button
              Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                  .size(buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = {
                    viewModel.frameStepBackward()
                  }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Previous Frame",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }

              // Camera / Loading button
              if (isSnapshotLoading) {
                Surface(
                  shape = CircleShape,
                  color = Color.Transparent,
                  modifier = Modifier.size(buttonSize - 4.dp),
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              } else {
                @OptIn(ExperimentalFoundationApi::class)
                Surface(
                  shape = CircleShape,
                  color = Color.Transparent,
                  modifier = Modifier
                    .size(buttonSize - 4.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                      onClick = {
                        viewModel.takeSnapshot(context)
                      },
                      onLongClick = { onOpenSheet(Sheets.FrameNavigation) },
                    ),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.CameraAlt,
                      contentDescription = "Take Screenshot",
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(24.dp),
                    )
                  }
                }
              }

              // Next frame button
              Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                  .size(buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = {
                    viewModel.frameStepForward()
                  }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Next Frame",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }

              // Close button
              Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                  .size(buttonSize - 4.dp)
                  .clip(CircleShape)
                  .clickable(onClick = {
                    viewModel.toggleFrameNavigationExpanded()
                  }),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Frame Nav",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      }
     }
    }

    val sheetShown by viewModel.sheetShown.collectAsState()
    val subtitles by viewModel.subtitleTracks.collectAsState(persistentListOf())
    val audioTracks by viewModel.audioTracks.collectAsState(persistentListOf())
    val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsState()
    val speedPresets by playerPreferences.speedPresets.collectAsState()

    PlayerSheets(
      viewModel = viewModel,
      sheetShown = sheetShown,
      subtitles = subtitles.toImmutableList(),
      onAddSubtitle = viewModel::addSubtitle,
      onToggleSubtitle = viewModel::toggleSubtitle,
      isSubtitleSelected = viewModel::isSubtitleSelected,
      onRemoveSubtitle = viewModel::removeSubtitle,
      audioTracks = audioTracks.toImmutableList(),
      onAddAudio = viewModel::addAudio,
      onSelectAudio = {
        viewModel.selectAudioTrack(it.id, it.title, it.lang)
      },
      chapter = chapters.getOrNull(currentChapter ?: 0),
      chapters = chapters.toImmutableList(),
      currentChapterIndex = currentChapter ?: 0,
      onSeekToChapter = {
        MPVLib.setPropertyInt("chapter", it)
        viewModel.unpause()
      },
      decoder = decoder,
      onUpdateDecoder = { MPVLib.setPropertyString("hwdec", it.value) },
      speed = playbackSpeed ?: playerPreferences.defaultSpeed.get(),
      onSpeedChange = { MPVLib.setPropertyFloat("speed", it.toFixed(2)) },
      onMakeDefaultSpeed = { playerPreferences.defaultSpeed.set(it.toFixed(2)) },
      onAddSpeedPreset = { playerPreferences.speedPresets += it.toFixed(2).toString() },
      onRemoveSpeedPreset = { playerPreferences.speedPresets -= it.toFixed(2).toString() },
      onResetSpeedPresets = playerPreferences.speedPresets::delete,
      speedPresets = speedPresets.map { it.toFloat() }.sorted(),
      onResetDefaultSpeed = {
        MPVLib.setPropertyFloat("speed", playerPreferences.defaultSpeed.deleteAndGet().toFixed(2))
      },
      sleepTimerTimeRemaining = sleepTimerTimeRemaining,
      onStartSleepTimer = viewModel::startTimer,
      onOpenPanel = onOpenPanel,
      onShowSheet = onOpenSheet,
      onDismissRequest = { onOpenSheet(Sheets.None) },
    )

    val panel by viewModel.panelShown.collectAsState()
    PlayerPanels(
      panelShown = panel,
      onDismissRequest = { onOpenPanel(Panels.None) },
    )

    val resumePrompt by viewModel.resumePrompt.collectAsState()
    resumePrompt?.let { promptData ->
      ResumePlaybackPromptDialog(
        promptData = promptData,
        onConfirmResume = { pos -> viewModel.confirmResume(pos) },
        onRestart = { viewModel.restartFromBeginning() },
        onDismiss = { viewModel.dismissResumePrompt() },
      )
    }
  }
}

