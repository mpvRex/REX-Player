package xyz.mpv.rex.ui.player.delegates

import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import xyz.mpv.rex.ui.player.MPVPipHelper
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG

/**
 * Controller responsible for Picture-in-Picture (PiP) lifecycle, aspect ratio updates,
 * controls alpha management, and system UI coordination during PiP entry/exit.
 */
class PlayerPipController(
  private val activity: PlayerActivity,
) {
  val pipHelper: MPVPipHelper by lazy {
    MPVPipHelper(activity = activity, mpvView = activity.player)
  }

  var isEnteringPip: Boolean = false
  var wasInPipMode: Boolean = false
  private var savedBrightnessOverride: Float? = null

  /**
   * Configures window for Picture-in-Picture mode.
   * Shows system UI and navigation bars.
   */
  fun enterPipUIMode() {
    activity.systemUiController.enterPipUIMode()
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
  fun exitPipUIMode() {
    activity.systemUiController.exitPipUIMode()
  }

  /**
   * Enters Picture-in-Picture mode.
   */
  fun enterPipMode() {
    isEnteringPip = true
    pipHelper.enterPipMode()
  }

  /**
   * Updates Picture-in-Picture parameters (e.g., aspect ratio, actions).
   */
  fun updatePictureInPictureParams() {
    pipHelper.updatePictureInPictureParams()
  }

  /**
   * Cleans up PiP receiver and state on stop.
   */
  fun onStop() {
    pipHelper.onStop()
  }

  /**
   * Enters Picture-in-Picture mode and hides all overlay controls.
   */
  fun enterPipModeHidingOverlay() {
    isEnteringPip = true
    runCatching {
      enterPipUIMode()
      handlePipBrightness(true)
    }.onFailure { e ->
      Log.e(TAG, "Error entering PiP mode with hidden overlay", e)
    }

    activity.binding.controls.alpha = 0f
    activity.miniPlayerStateManager.clearState()

    pipHelper.enterPipMode()
  }

  /**
   * Handles configuration and UI changes when entering or exiting Picture-in-Picture mode.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    isEnteringPip = false
    wasInPipMode = isInPictureInPictureMode
    pipHelper.onPictureInPictureModeChanged(isInPictureInPictureMode)

    activity.binding.controls.alpha = if (isInPictureInPictureMode) 0f else 1f

    runCatching {
      if (isInPictureInPictureMode) {
        activity.miniPlayerStateManager.clearState()
        enterPipUIMode()
        handlePipBrightness(true)
      } else {
	    activity.viewModel.hideControls()
        exitPipUIMode()
        handlePipBrightness(false)
      }
    }.onFailure { e ->
      Log.e(TAG, "Error handling PiP mode change", e)
    }
  }

  private fun handlePipBrightness(enteringPip: Boolean) {
    if (enteringPip) {
      val currentBrightness = activity.window.attributes.screenBrightness
      if (currentBrightness >= 0f) {
        savedBrightnessOverride = currentBrightness
        activity.window.attributes =
          activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
          }
      }
    } else {
      savedBrightnessOverride?.let { saved ->
        activity.window.attributes =
          activity.window.attributes.apply {
            screenBrightness = saved
          }
        savedBrightnessOverride = null
      }
    }
  }
}
