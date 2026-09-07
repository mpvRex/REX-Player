
package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum
import xyz.mpv.rex.ui.player.BackgroundPlaybackMode
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.RepeatMode
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.ui.player.VideoAspect

class PlayerPreferences(
  preferenceStore: PreferenceStore,
) {
  val orientation = preferenceStore.getEnum("player_orientation", PlayerOrientation.Video)
  val backgroundPlayback = preferenceStore.getEnum("background_playback_mode", BackgroundPlaybackMode.AudioOnly)
  val invertDuration = preferenceStore.getBoolean("invert_duration")
  val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
  val rememberLongPressSpeed = preferenceStore.getBoolean("remember_long_press_speed", true)
  val showDynamicSpeedOverlay = preferenceStore.getBoolean("show_dynamic_speed_overlay", true)
  val showSpeedIndicatorOverlay = preferenceStore.getBoolean("show_speed_indicator_overlay", true)
  val showDoubleTapOvals = preferenceStore.getBoolean("show_double_tap_ovals", true)
  val showCircularDoubleTapSeek = preferenceStore.getBoolean("show_circular_double_tap_seek", true)
  val showSeekTimeWhileSeeking = preferenceStore.getBoolean("show_seek_time_while_seeking", true)
  val usePreciseSeeking = preferenceStore.getBoolean("use_precise_seeking", false)

  val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
  val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)
  val pinchToZoomGesture = preferenceStore.getBoolean("pinch_to_zoom_gesture", true)
  val horizontalSwipeToSeek = preferenceStore.getBoolean("horizontal_swipe_to_seek", true)
  val swipeToSubtitleSeek = preferenceStore.getBoolean("swipe_to_subtitle_seek", true)
  val moveSubtitleByDragging = preferenceStore.getBoolean("move_subtitle_by_dragging", true)
  val horizontalSwipeSensitivity = preferenceStore.getFloat("horizontal_swipe_sensitivity", 0.05f)

  val customAspectRatios = preferenceStore.getStringSet("custom_aspect_ratios", emptySet())

  val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
  val speedPresets =
    preferenceStore.getStringSet(
      "default_speed_presets",
      setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
    )
  val displayVolumeAsPercentage = preferenceStore.getBoolean("display_volume_as_percentage", true)
  val swapVolumeAndBrightness = preferenceStore.getBoolean("display_volume_on_right")
  val showLoadingCircle = preferenceStore.getBoolean("show_loading_circle", true)
  val savePositionOnQuit = preferenceStore.getBoolean("save_position", true)
  val resumePlaybackMode = preferenceStore.getEnum("resume_playback_mode", ResumePlaybackMode.Always)

  val closeAfterReachingEndOfVideo = preferenceStore.getBoolean("close_after_eof", true)

  val rememberBrightness = preferenceStore.getBoolean("remember_brightness")
  val defaultBrightness = preferenceStore.getFloat("default_brightness", -1f)

  val allowGesturesInPanels = preferenceStore.getBoolean("allow_gestures_in_panels")
  val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar")
  val showSystemNavigationBar = preferenceStore.getBoolean("show_system_navigation_bar")
  val reduceMotion = preferenceStore.getBoolean("reduce_motion", true)
  val playerGradientOpacity = preferenceStore.getFloat("player_gradient_opacity", 1.0f)
  val playerTimeToDisappear = preferenceStore.getInt("player_time_to_disappear", 4000)

  val defaultVideoZoom = preferenceStore.getFloat("default_video_zoom", 0f)
  val defaultVideoPanX = preferenceStore.getFloat("default_video_pan_x", 0f)
  val defaultVideoPanY = preferenceStore.getFloat("default_video_pan_y", 0f)
  val panAndZoomEnabled = preferenceStore.getBoolean("pan_and_zoom_enabled", false)

  val advancedZoomEnabled = preferenceStore.getBoolean("advanced_zoom_enabled", false)
  val defaultVideoScaleX = preferenceStore.getFloat("default_video_scale_x", 1f)
  val defaultVideoScaleY = preferenceStore.getFloat("default_video_scale_y", 1f)

  val includeSubtitlesInSnapshot = preferenceStore.getBoolean("include_subtitles_in_snapshot", false)

  val playlistMode = preferenceStore.getBoolean("playlist_mode", true)
  val playlistViewMode = preferenceStore.getBoolean("playlist_view_mode_list", true) // true = list, false = grid

  // When enabled, tapping a media item starts playback directly in the bottom mini player bar
  // (headless, no full-screen player) instead of opening PlayerActivity.
  val playInMiniPlayerDirectly = preferenceStore.getBoolean("play_in_mini_player_directly", false)

  val useWavySeekbar = preferenceStore.getBoolean("use_wavy_seekbar", true)
  val bottomControlsBelowSeekbar = preferenceStore.getBoolean("bottom_controls_below_seekbar", false)
  val showSeekBarWhenSeeking = preferenceStore.getBoolean("show_seekbar_when_seeking", false)
  val whiteSeekBar = preferenceStore.getBoolean("white_seekbar", false)
  val showSeekbarChapters = preferenceStore.getBoolean("show_seekbar_chapters", true)
  val showSeekbarReadAhead = preferenceStore.getBoolean("show_seekbar_read_ahead", true)
  val hideOsdText = preferenceStore.getBoolean("hide_osd_text_v2", false)

  val customSkipDuration = preferenceStore.getInt("custom_skip_duration", 90)

  val repeatMode = preferenceStore.getEnum("repeat_mode", RepeatMode.OFF)
  val shuffleEnabled = preferenceStore.getBoolean("shuffle_enabled", false)

  // New: autoplay next video when current file ends
  val autoplayNextVideo = preferenceStore.getBoolean("autoplay_next_video", true)
  val autoplayOnOpen = preferenceStore.getBoolean("autoplay_on_open", true)
  val showControlsOnPlay = preferenceStore.getBoolean("show_controls_on_play", true)

  val autoPiPOnNavigation = preferenceStore.getBoolean("auto_pip_on_navigation", false)

  val keepScreenOnWhenPaused = preferenceStore.getBoolean("keep_screen_on_when_paused", false)
  val resumeOnUnlock = preferenceStore.getBoolean("resume_on_unlock", false)

  /**
   * Baseline default aspect ratio used when opening videos (Fit, Crop, Stretch).
   */
  val defaultVideoAspect = preferenceStore.getEnum("default_video_aspect", VideoAspect.Fit)
  val defaultCustomAspectRatio = preferenceStore.getObject(
    key = "default_custom_aspect_ratio",
    defaultValue = -1.0,
    serializer = { it.toString() },
    deserializer = { it.toDoubleOrNull() ?: -1.0 }
  )

  /**
   * Whether aspect ratio adjustments made during video playback should persist across videos
   * or remain session-only.
   */
  val rememberVideoAspect = preferenceStore.getBoolean("remember_video_aspect", false)

  // Custom Buttons - JSON List
  val customButtons = preferenceStore.getString("custom_buttons_json", "[]")

  // Ambience Mode
  val isAmbientEnabled = preferenceStore.getBoolean("ambient_enabled", false)

  // External media controls
  val disableMediaButtons = preferenceStore.getBoolean("disable_media_buttons", false)
}
