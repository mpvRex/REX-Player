package xyz.mpv.rex.ui.player.delegates

import android.net.Uri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.PlayerUpdates
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.VideoAspect
import java.util.Locale

/**
 * Controller responsible for saving and restoring video playback state (resume position,
 * playback speed, audio/subtitle tracks, delays, aspect ratio, zoom) to and from the database.
 */
class PlayerPlaybackStateController(
  private val activity: PlayerActivity,
) {
  // PRELOAD-RESUME-STATE-V1
  data class PreparedPlaybackState(
    val generation: Long,
    val mediaIdentifier: String,
    val state: PlaybackStateEntity?,
    val startPositionSeconds: String?,
    val resumePosition: Int?,
    val explicitPositionMs: Long?,
    val jellyfinPositionMs: Int?,
    val resumeMode: ResumePlaybackMode,
    val autoResumeOnAsk: Boolean,
  )

  @Volatile
  var isPlaybackStateLoaded: Boolean = false

  var savePlaybackStateJob: Job? = null

  /**
   * Saves the current playback state to the database.
   *
   * Uses lifecycleScope to save state; cancels previous pending saves.
   *
   * @param mediaTitle The title of the media being played
   */
  fun saveVideoPlaybackState(mediaTitle: String, isEof: Boolean = false) {
    val identifier = activity.mediaIdentifier
    if (identifier.isBlank()) return

    // Capture current playback state before switching files
    val currentPos = activity.viewModel.pos ?: 0
    val currentDuration = activity.viewModel.duration ?: 0
    val currentSpeed = MPVLib.getPropertyDouble("speed") ?: DEFAULT_PLAYBACK_SPEED
    val isStateLoaded = isPlaybackStateLoaded
    val currentZoom = activity.viewModel.videoZoom.value
    val currentAspect = activity.viewModel.videoAspect.value.name
    val currentCustomRatio = activity.viewModel.currentAspectRatio.value
    val currentSid = activity.player.sid
    val currentSecondarySid = activity.player.secondarySid
    val currentAid = activity.player.aid
    val currentSubDelay = (MPVLib.getPropertyDouble("sub-delay") ?: 0.0)
    val currentAudioDelay = (MPVLib.getPropertyDouble("audio-delay") ?: 0.0)
    val currentSubSpeed = MPVLib.getPropertyDouble("sub-speed") ?: DEFAULT_SUB_SPEED
    val currentOrientation = activity.requestedOrientation
    val currentExternalSubs = activity.viewModel.externalSubtitles
      .filter { !it.startsWith("http", ignoreCase = true) && !it.startsWith("edl", ignoreCase = true) }
      .toList()
    val currentExternalAudio = activity.viewModel.externalAudioTracks
      .filter { !it.startsWith("http", ignoreCase = true) && !it.startsWith("edl", ignoreCase = true) }
      .toList()

    // Cancel any previous pending save operation
    savePlaybackStateJob?.cancel()

    // Launch new save job and track it
    savePlaybackStateJob = activity.lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val oldState = activity.playbackStateRepository.getVideoDataByTitle(identifier)
        Log.d(TAG, "Saving playback state for: $mediaTitle (identifier: $identifier) at position: $currentPos")

        val currentZoomToSave = if (isStateLoaded) currentZoom else (oldState?.videoZoom ?: currentZoom)
        val currentAspectToSave = if (isStateLoaded) {
          currentAspect
        } else {
          oldState?.videoAspect ?: currentAspect
        }
        val currentCustomRatioToSave = if (isStateLoaded) {
          currentCustomRatio
        } else {
          oldState?.customAspectRatio ?: currentCustomRatio
        }

        val watchedThreshold = activity.browserPreferences.watchedThreshold.get()
        val progress = if (currentDuration > 0) currentPos.toFloat() / currentDuration.toFloat() else 0f
        val isFinished = isEof || ((currentDuration > 0) && (currentPos >= currentDuration - 1))

        // Calculate save position
        val savePos = if (!activity.playerPreferences.savePositionOnQuit.get()) {
          oldState?.lastPosition ?: 0
        } else if (isFinished) {
          // If video finished or reached within 1 second of the end, restart from beginning next time
          0
        } else if (currentDuration > 0) {
          currentPos
        } else {
          // If duration is not yet available, preserve old position or use current (0)
          oldState?.lastPosition ?: currentPos
        }

        val timeRemaining = if (isFinished) 0 else if (currentDuration > savePos) currentDuration - savePos else 0

        activity.playbackStateRepository.upsert(
          PlaybackStateEntity(
            mediaTitle = identifier,
            lastPosition = savePos,
            playbackSpeed = currentSpeed,
            videoZoom = currentZoomToSave,
            sid = currentSid,
            secondarySid = currentSecondarySid,
            subDelay = (currentSubDelay * MILLISECONDS_TO_SECONDS).toInt(),
            subSpeed = currentSubSpeed,
            aid = currentAid,
            audioDelay = (currentAudioDelay * MILLISECONDS_TO_SECONDS).toInt(),
            timeRemaining = timeRemaining,
            savedOrientation = currentOrientation,
            externalSubtitles = currentExternalSubs.joinToString("|"),
            externalAudioTracks = currentExternalAudio.joinToString("|"),
            videoAspect = currentAspectToSave,
            customAspectRatio = currentCustomRatioToSave,
            hasBeenWatched = isFinished || progress >= (watchedThreshold / 100f),
          ),
        )
      }.onFailure { e ->
        Log.e(TAG, "Error saving playback state", e)
      }
    }
  }

  /**
   * Reads the resume record before MPV starts opening the media. The resulting state object is
   * carried through FILE_LOADED so restoration does not query the database or seek a second time.
   */
  suspend fun preparePlaybackState(
    mediaIdentifier: String,
    generation: Long,
    explicitPositionMs: Long?,
    jellyfinPositionMs: Int?,
  ): PreparedPlaybackState {
    val resumeMode = activity.playerPreferences.resumePlaybackMode.get()
    val autoResumeOnAsk = activity.playerPreferences.autoResumeOnAsk.get()
    val savePosition = activity.playerPreferences.savePositionOnQuit.get()
    val state = if (mediaIdentifier.isBlank()) {
      null
    } else {
      try {
        withContext(Dispatchers.IO) {
          activity.playbackStateRepository.getVideoDataByTitle(mediaIdentifier)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error preparing playback state for $mediaIdentifier", e)
        null
      }
    }

    val resumePosition = state?.takeIf {
      savePosition &&
        resumeMode != ResumePlaybackMode.Never &&
        it.lastPosition > 3 &&
        !(it.hasBeenWatched && it.timeRemaining <= 0)
    }?.lastPosition
    val startPositionSeconds = when {
      explicitPositionMs != null -> String.format(
        Locale.US,
        "%.3f",
        explicitPositionMs.coerceAtLeast(0L) / 1000.0,
      )
      resumePosition != null && (
        resumeMode == ResumePlaybackMode.Always ||
          (resumeMode == ResumePlaybackMode.Ask && autoResumeOnAsk)
        ) -> resumePosition.toString()
      else -> null
    }

    return PreparedPlaybackState(
      generation = generation,
      mediaIdentifier = mediaIdentifier,
      state = state,
      startPositionSeconds = startPositionSeconds,
      resumePosition = resumePosition,
      explicitPositionMs = explicitPositionMs,
      jellyfinPositionMs = jellyfinPositionMs,
      resumeMode = resumeMode,
      autoResumeOnAsk = autoResumeOnAsk,
    )
  }

  /**
   * Loads and applies the state already prepared for this request. Unprepared MPV-native playlist
   * transitions retain the legacy lookup path as a fallback.
   */
  suspend fun loadVideoPlaybackState(
    mediaTitle: String,
    preparedState: PreparedPlaybackState? = null,
    expectedMediaIdentifier: String = activity.mediaIdentifier,
  ): Boolean {
    if (expectedMediaIdentifier.isBlank()) return false
    if (preparedState != null && preparedState.mediaIdentifier != expectedMediaIdentifier) return false

    return try {
      val state = if (preparedState != null) {
        preparedState.state
      } else {
        activity.playbackStateRepository.getVideoDataByTitle(expectedMediaIdentifier)
      }

      applyPlaybackState(state)
      applyDefaultSettings(state)
      applyResumePosition(state, preparedState)
      state != null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error loading playback state for $expectedMediaIdentifier", e)
      false
    }
  }

  /**
   * Applies saved playback state to MPV.
   *
   * Restores subtitle delay, audio delay, audio and track selections, and playback speed.
   * Also restores saved time position if enabled.
   *
   * @param state The saved playback state entity
   */
  private suspend fun applyPlaybackState(state: PlaybackStateEntity?) {
    if (state == null) {
      return
    }

    val subDelay = state.subDelay / DELAY_DIVISOR
    val audioDelay = state.audioDelay / DELAY_DIVISOR

    // Restore external subtitles first (local files only)
    if (state.externalSubtitles.isNotBlank()) {
      val externalSubUris = state.externalSubtitles.split("|").filter { it.isNotBlank() }
      Log.d(TAG, "Restoring ${externalSubUris.size} external subtitle(s)")

      val lastUri = externalSubUris.last()
      for (subUri in externalSubUris) {
        val uri = Uri.parse(subUri)
        if (uri.scheme?.startsWith("http") != true && uri.scheme != "edl") {
          activity.viewModel.addSubtitle(uri, select = subUri == lastUri, silent = true)
        }
      }
    }

    // Restore external audio tracks (local files only)
    if (state.externalAudioTracks.isNotBlank()) {
      val externalAudioUris = state.externalAudioTracks.split("|").filter { it.isNotBlank() }
      Log.d(TAG, "Restoring ${externalAudioUris.size} external audio track(s)")

      val lastUri = externalAudioUris.last()
      for (audioUri in externalAudioUris) {
        val uri = Uri.parse(audioUri)
        if (uri.scheme?.startsWith("http") != true && uri.scheme != "edl") {
          activity.viewModel.addAudio(uri, select = audioUri == lastUri, silent = true)
        }
      }
    }

    // Always restore subtitle and audio tracks from saved state
    // User's manual selection has highest priority
    if (state.sid > 0 || state.sid == -1) {
      activity.player.sid = state.sid
      Log.d(TAG, "Restored primary subtitle track: ${state.sid} (user selection)")
    }

    if (state.secondarySid > 0 || state.secondarySid == -1) {
      activity.player.secondarySid = state.secondarySid
      Log.d(TAG, "Restored secondary subtitle track: ${state.secondarySid} (user selection)")
    }

    if (state.aid > 0) {
      // The saved `aid` is an absolute mpv track id. Alternate audio streams are
      // injected as external tracks *after* load and get their own ids, so the
      // numeric value captured earlier may now point at a different (external)
      // stream. Only re-apply it when it still resolves to a non-external audio
      // track; otherwise mpv's own default selection wins.
      if (isRestorableAudioTrackId(state.aid)) {
        activity.player.aid = state.aid
        Log.d(TAG, "Restored audio track: ${state.aid} (user selection)")
      } else {
        Log.d(TAG, "Skipped restoring stale audio track id ${state.aid} (no longer a primary audio track)")
      }
    }

    MPVLib.setPropertyDouble("sub-delay", subDelay)
    MPVLib.setPropertyDouble("speed", state.playbackSpeed)
    MPVLib.setPropertyDouble("audio-delay", audioDelay)
    MPVLib.setPropertyDouble("sub-speed", state.subSpeed)

    // Restore orientation if in Smart mode
    if (activity.playerPreferences.orientation.get() == PlayerOrientation.Smart && state.savedOrientation != null) {
      withContext(Dispatchers.Main) {
        activity.requestedOrientation = state.savedOrientation
        activity.isOrientationRestored = true
        Log.d(TAG, "Restored orientation for Smart mode: ${state.savedOrientation}")
      }
    }

    // Restore video zoom from saved state
    MPVLib.setPropertyDouble("video-zoom", state.videoZoom.toDouble())
    activity.viewModel.setVideoZoom(state.videoZoom)

    // Restore video aspect ratio from saved state
    if (state.videoAspect != null) {
      val aspect = runCatching { VideoAspect.valueOf(state.videoAspect) }.getOrDefault(VideoAspect.Fit)
      withContext(Dispatchers.Main) {
        if (state.customAspectRatio > 0.0) {
          activity.viewModel.setCustomAspectRatio(
            state.customAspectRatio,
            resetZoomAndPan = false,
            showUpdate = false,
            persistToPreferences = false,
          )
        } else {
          activity.viewModel.changeVideoAspect(
            aspect,
            showUpdate = false,
            resetZoomAndPan = false,
            persistToPreferences = false,
          )
        }
      }
    }

    isPlaybackStateLoaded = true
  }

  private suspend fun applyResumePosition(
    state: PlaybackStateEntity?,
    preparedState: PreparedPlaybackState?,
  ) {
    if (preparedState != null) {
      if (preparedState.explicitPositionMs != null) {
        preparedState.jellyfinPositionMs?.let {
          xyz.mpv.rex.jellyfin.JellyfinExternalHelper.logSeekApplied(it)
        }
        withContext(Dispatchers.Main) {
          activity.viewModel.clearResumePrompt()
        }
      } else if (preparedState.resumeMode == ResumePlaybackMode.Ask && preparedState.resumePosition != null) {
        withContext(Dispatchers.Main) {
          activity.viewModel.playerUpdate.value = if (preparedState.autoResumeOnAsk) {
            PlayerUpdates.ResumedFrom(preparedState.resumePosition)
          } else {
            PlayerUpdates.PromptResume(preparedState.resumePosition)
          }
        }
      }
      return
    }

    // Fallback for MPV-native playlist transitions that were not started by an app load request.
    if (state == null) return
    val resumeMode = activity.playerPreferences.resumePlaybackMode.get()
    val hasValidSavedPosition = state.lastPosition > 3 &&
      !(state.hasBeenWatched && state.timeRemaining <= 0)

    when {
      !activity.playerPreferences.savePositionOnQuit.get() || resumeMode == ResumePlaybackMode.Never || !hasValidSavedPosition -> Unit
      resumeMode == ResumePlaybackMode.Ask -> {
        if (activity.playerPreferences.autoResumeOnAsk.get()) {
          MPVLib.setPropertyInt("time-pos", state.lastPosition)
          withContext(Dispatchers.Main) {
            activity.viewModel.playerUpdate.value = PlayerUpdates.ResumedFrom(state.lastPosition)
          }
        } else {
          withContext(Dispatchers.Main) {
            activity.viewModel.playerUpdate.value = PlayerUpdates.PromptResume(state.lastPosition)
          }
        }
      }
      resumeMode == ResumePlaybackMode.Always -> MPVLib.setPropertyInt("time-pos", state.lastPosition)
    }
  }

  /**
   * Applies default settings when no saved state exists.
   *
   * Sets subtitle speed to user default if not present in saved state.
   *
   * @param state The saved playback state entity (null if no saved state)
   */
  private fun applyDefaultSettings(state: PlaybackStateEntity?) {
    isPlaybackStateLoaded = true
    if (state == null) {
      val defaultSubSpeed = activity.subtitlesPreferences.defaultSubSpeed.get().toDouble()
      MPVLib.setPropertyDouble("sub-speed", defaultSubSpeed)
    }
  }

  /**
   * Returns true when [trackId] still resolves to a primary (non-external) audio track
   * in mpv's current track list. Saved audio ids are only meaningful while the track
   * layout is unchanged; alternate web-audio streams are injected as external tracks
   * after load and would otherwise collide with a stale numeric id.
   */
  private fun isRestorableAudioTrackId(trackId: Int): Boolean {
    val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull() ?: return false
    for (i in 0 until count) {
      val id = runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
      if (id != trackId) continue
      val type = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull()
      val external = runCatching { MPVLib.getPropertyBoolean("track-list/$i/external") }.getOrNull() ?: false
      return type == "audio" && !external
    }
    return false
  }

  /**
   * Wait for any pending save operation to complete before destroying MPV with a bounded timeout.
   * This prevents the main thread from blocking infinitely during activity destruction (ANR prevention).
   */
  fun waitForPendingSave(timeoutMs: Long = 200) {
    savePlaybackStateJob?.let { job ->
      if (job.isActive) {
        Log.d(TAG, "Waiting for save playback state job to complete (bounded timeout)...")
        runCatching {
          kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
              job.join()
            }
          }
        }
        Log.d(TAG, "Save playback state job finished or timed out safely")
      }
    }
  }

  companion object {
    private const val DEFAULT_PLAYBACK_SPEED = 1.0
    private const val DEFAULT_SUB_SPEED = 1.0
    private const val MILLISECONDS_TO_SECONDS = 1000
    private const val DELAY_DIVISOR = 1000.0
  }
}
