package xyz.mpv.rex.ui.player

import androidx.annotation.StringRes
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.preference.Preference

enum class PlayerOrientation(
  @StringRes val titleRes: Int,
) {
  Free(R.string.pref_player_orientation_free),
  Video(R.string.pref_player_orientation_video),
  Smart(R.string.pref_player_orientation_smart),
  Portrait(R.string.pref_player_orientation_portrait),
  ReversePortrait(R.string.pref_player_orientation_reverse_portrait),
  SensorPortrait(R.string.pref_player_orientation_sensor_portrait),
  Landscape(R.string.pref_player_orientation_landscape),
  ReverseLandscape(R.string.pref_player_orientation_reverse_landscape),
  SensorLandscape(R.string.pref_player_orientation_sensor_landscape),
}

enum class PlaybackControlsPosition(
  @StringRes val titleRes: Int,
) {
  Center(R.string.pref_playback_controls_position_center),
  Bottom(R.string.pref_playback_controls_position_bottom),
  Hide(R.string.pref_playback_controls_position_hide),
}

enum class BackgroundPlaybackMode(
  @StringRes val titleRes: Int,
) {
  Always(R.string.pref_background_playback_always),
  AudioOnly(R.string.pref_background_playback_audio_only),
  VideoOnly(R.string.pref_background_playback_video_only),
  Never(R.string.pref_background_playback_never),
}

enum class LongPressSpeedMemoryMode(
  @StringRes val titleRes: Int,
) {
  Global(R.string.pref_player_remember_long_press_speed_global),
  PerVideo(R.string.pref_player_remember_long_press_speed_per_video),
  Reset(R.string.pref_player_remember_long_press_speed_reset),
}

enum class ResumePlaybackMode(
  @StringRes val titleRes: Int,
  @StringRes val summaryRes: Int,
) {
  Always(
    R.string.pref_player_resume_mode_always,
    R.string.pref_player_resume_mode_always_summary,
  ),
  Ask(
    R.string.pref_player_resume_mode_ask,
    R.string.pref_player_resume_mode_ask_summary,
  ),
  Never(
    R.string.pref_player_resume_mode_never,
    R.string.pref_player_resume_mode_never_summary,
  ),
}

data class ResumePromptData(
  val position: Int,
  val duration: Int = 0,
)

enum class VideoAspect(
  @StringRes val titleRes: Int,
) {
  Crop(R.string.player_aspect_crop),
  Fit(R.string.player_aspect_fit),
  Stretch(R.string.player_aspect_stretch),
}

enum class SingleActionGesture(
  @StringRes val titleRes: Int,
) {
  None(R.string.pref_gesture_double_tap_none),
  Seek(R.string.pref_gesture_double_tap_seek),
  SubSeek(R.string.pref_gesture_double_tap_subseek),
  PlayPause(R.string.pref_gesture_double_tap_play),
  Custom(R.string.pref_gesture_double_tap_custom),
  PlaylistNext(R.string.pref_gesture_media_next),
  PlaylistPrev(R.string.pref_gesture_media_previous),
}

enum class CustomKeyCodes(
  val keyCode: String,
  @StringRes val titleRes: Int,
) {
  DoubleTapLeft("MBTN_LEFT_DBL", R.string.pref_gesture_double_tap_left),
  DoubleTapCenter("MBTN_MID_DBL", R.string.pref_gesture_double_tap_center),
  DoubleTapRight("MBTN_RIGHT_DBL", R.string.pref_gesture_double_tap_right),
  MediaPrevious("PREV", R.string.pref_gesture_media_previous),
  MediaPlay("PLAYPAUSE", R.string.pref_gesture_media_play),
  MediaNext("NEXT", R.string.pref_gesture_media_next),
}

enum class Decoder(
  val title: String,
  val value: String,
) {
  AutoCopy("Auto", "auto-copy"),
  Auto("Auto", "auto"),
  SW("SW", "no"),
  HW("HW", "mediacodec-copy"),
  HWPlus("HW+", "mediacodec"),
  ;

  companion object {
    fun getDecoderFromValue(value: String): Decoder = Decoder.entries.firstOrNull { it.value == value } ?: Auto
  }
}

enum class Debanding(
  @StringRes val titleRes: Int,
) {
  None(R.string.player_sheets_deband_none),
  CPU(R.string.player_sheets_deband_cpu),
  GPU(R.string.player_sheets_deband_gpu),
}

enum class MPVProfile(
  val displayName: String,
  val value: String,
) {
  Fast("Fast", "fast"),
  Default("Default", "default"),
  HighQuality("High Quality", "high-quality"),
  GpuHQ("GPU HQ", "gpu-hq"),
  LowLatency("Low Latency", "low-latency"),
  SwFast("SW Fast", "sw-fast"),
  ;

  override fun toString(): String = displayName

  companion object {
    fun fromValue(value: String): MPVProfile = entries.firstOrNull { it.value == value } ?: Fast
  }
}

enum class Sheets {
  None,
  PlaybackSpeed,
  SubtitleTracks,
  OnlineSubtitleSearch,
  AudioTracks,
  Chapters,
  Decoders,
  More,
  VideoZoom,
  AspectRatios,
  Playlist,
  FrameNavigation,
  CustomSkipDuration,
  SleepTimer,
  ClipExport,
  VideoQuality,
}

enum class Panels {
  None,
  SubtitleSettings,
  SubtitleDelay,
  AudioDelay,
  VideoFilters,
}

sealed class PlayerUpdates {
  data object None : PlayerUpdates()

  data object MultipleSpeed : PlayerUpdates()

  data class DynamicSpeedControl(
    val speed: Float,
    val showFullOverlay: Boolean = true,
  ) : PlayerUpdates()

  data class SpeedLockHint(
    val speed: Float,
    val isLocked: Boolean,
  ) : PlayerUpdates()

  data object AspectRatio : PlayerUpdates()

  data object VideoZoom : PlayerUpdates()

  data class HorizontalSeek(
    val currentTime: String,
    val seekDelta: String,
  ) : PlayerUpdates()

  data class ShowText(
    val value: String,
  ) : PlayerUpdates()

  data class ResumedFrom(
    val position: Int,
  ) : PlayerUpdates()

  data class PromptResume(
    val position: Int,
  ) : PlayerUpdates()

  data class RepeatMode(
    val mode: xyz.mpv.rex.ui.player.RepeatMode,
  ) : PlayerUpdates()

  data class Shuffle(
    val enabled: Boolean,
  ) : PlayerUpdates()

  data class FrameInfo(
    val currentFrame: Int,
    val totalFrames: Int,
  ) : PlayerUpdates()
}

/**
 * Filter presets for quick video color adjustments.
 * Each preset defines specific values for brightness, saturation, contrast, gamma, hue, and sharpness.
 * Sharpness uses MPV's 'sharpen' property which ranges from -5 (blur) to 5 (sharp).
 */
enum class FilterPreset(
  @StringRes val displayNameRes: Int,
  @StringRes val descriptionRes: Int,
  val brightness: Int,
  val saturation: Int,
  val contrast: Int,
  val gamma: Int,
  val hue: Int,
  val sharpness: Int,
) {
  NONE(
    displayNameRes = R.string.filter_preset_none,
    descriptionRes = R.string.filter_preset_none_desc,
    brightness = 0,
    saturation = 0,
    contrast = 0,
    gamma = 0,
    hue = 0,
    sharpness = 0,
  ),
  VIVID(
    displayNameRes = R.string.filter_preset_vivid,
    descriptionRes = R.string.filter_preset_vivid_desc,
    brightness = 5,
    saturation = 25,
    contrast = 15,
    gamma = 0,
    hue = 0,
    sharpness = 0,
  ),
  WARM_TONE(
    displayNameRes = R.string.filter_preset_warm_tone,
    descriptionRes = R.string.filter_preset_warm_tone_desc,
    brightness = 5,
    saturation = 10,
    contrast = 5,
    gamma = 5,
    hue = 15,
    sharpness = 0,
  ),
  COOL_TONE(
    displayNameRes = R.string.filter_preset_cool_tone,
    descriptionRes = R.string.filter_preset_cool_tone_desc,
    brightness = 0,
    saturation = 5,
    contrast = 10,
    gamma = 0,
    hue = -15,
    sharpness = 0,
  ),
  SOFT_PASTEL(
    displayNameRes = R.string.filter_preset_soft_pastel,
    descriptionRes = R.string.filter_preset_soft_pastel_desc,
    brightness = 10,
    saturation = -15,
    contrast = -10,
    gamma = 5,
    hue = 0,
    sharpness = 0,
  ),
  CINEMATIC(
    displayNameRes = R.string.filter_preset_cinematic,
    descriptionRes = R.string.filter_preset_cinematic_desc,
    brightness = -5,
    saturation = -10,
    contrast = 20,
    gamma = -5,
    hue = 5,
    sharpness = 0,
  ),
  DRAMATIC(
    displayNameRes = R.string.filter_preset_dramatic,
    descriptionRes = R.string.filter_preset_dramatic_desc,
    brightness = -10,
    saturation = 15,
    contrast = 30,
    gamma = -10,
    hue = 0,
    sharpness = 0,
  ),
  NIGHT_MODE(
    displayNameRes = R.string.filter_preset_night_mode,
    descriptionRes = R.string.filter_preset_night_mode_desc,
    brightness = -20,
    saturation = -5,
    contrast = 5,
    gamma = -10,
    hue = 0,
    sharpness = 0,
  ),
  NOSTALGIC(
    displayNameRes = R.string.filter_preset_nostalgic,
    descriptionRes = R.string.filter_preset_nostalgic_desc,
    brightness = 5,
    saturation = -20,
    contrast = 10,
    gamma = 0,
    hue = 20,
    sharpness = 0,
  ),
  GHIBLI_STYLE(
    displayNameRes = R.string.filter_preset_ghibli_style,
    descriptionRes = R.string.filter_preset_ghibli_style_desc,
    brightness = 8,
    saturation = 15,
    contrast = -5,
    gamma = 5,
    hue = 5,
    sharpness = 0,
  ),
  NEON_POP(
    displayNameRes = R.string.filter_preset_neon_pop,
    descriptionRes = R.string.filter_preset_neon_pop_desc,
    brightness = 5,
    saturation = 40,
    contrast = 20,
    gamma = 0,
    hue = 0,
    sharpness = 0,
  ),
  DEEP_BLACK(
    displayNameRes = R.string.filter_preset_deep_black,
    descriptionRes = R.string.filter_preset_deep_black_desc,
    brightness = -15,
    saturation = 5,
    contrast = 25,
    gamma = -15,
    hue = 0,
    sharpness = 0,
  ),
}

enum class VideoFilters(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
  val min: Int = -100,
  val max: Int = 100,
) {
  BRIGHTNESS(
    R.string.player_sheets_filters_brightness,
    { it.brightnessFilter },
    "brightness",
  ),
  SATURATION(
    R.string.player_sheets_filters_Saturation,
    { it.saturationFilter },
    "saturation",
  ),
  CONTRAST(
    R.string.player_sheets_filters_contrast,
    { it.contrastFilter },
    "contrast",
  ),
  GAMMA(
    R.string.player_sheets_filters_gamma,
    { it.gammaFilter },
    "gamma",
  ),
  HUE(
    R.string.player_sheets_filters_hue,
    { it.hueFilter },
    "hue",
  ),
  SHARPNESS(
    titleRes = R.string.player_sheets_filters_sharpness,
    preference = { it.sharpnessFilter },
    mpvProperty = "sharpen",
    min = -5,
    max = 5,
  ),
}

enum class DebandSettings(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
  val start: Int,
  val end: Int,
) {
  Iterations(
    R.string.player_sheets_deband_iterations,
    { it.debandIterations },
    "deband-iterations",
    0,
    16,
  ),
  Threshold(
    R.string.player_sheets_deband_threshold,
    { it.debandThreshold },
    "deband-threshold",
    0,
    200,
  ),
  Range(
    R.string.player_sheets_deband_range,
    { it.debandRange },
    "deband-range",
    1,
    64,
  ),
  Grain(
    R.string.player_sheets_deband_grain,
    { it.debandGrain },
    "deband-grain",
    0,
    200,
  ),
}
