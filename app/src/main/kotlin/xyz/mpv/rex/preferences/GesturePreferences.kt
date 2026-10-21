package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum
import xyz.mpv.rex.ui.player.SingleActionGesture

class GesturePreferences(
  preferenceStore: PreferenceStore,
) {
  val doubleTapToSeekDuration = preferenceStore.getInt("double_tap_to_seek_duration", 10)
  val doubleTapSeekAreaWidth = preferenceStore.getInt("double_tap_seek_area_width", 35)
  val leftSingleActionGesture = preferenceStore.getEnum("left_double_tap_gesture", SingleActionGesture.Seek)
  val centerSingleActionGesture = preferenceStore.getEnum("center_drag_gesture", SingleActionGesture.PlayPause)
  val rightSingleActionGesture = preferenceStore.getEnum("right_drag_gesture", SingleActionGesture.Seek)
  val useSingleTapForCenter = preferenceStore.getBoolean("use_single_tap_for_center", false)
  val preventSeekbarTap = preferenceStore.getBoolean("prevent_seekbar_tap", false)
  val useRelativeSeeking = preferenceStore.getBoolean("use_relative_seeking", true)
  val enableReleaseToCancel = preferenceStore.getBoolean("enable_release_to_cancel", true)
  val leftSingleTapGesture = preferenceStore.getEnum("left_single_tap_gesture", SingleActionGesture.PlayPause)
  val rightSingleTapGesture = preferenceStore.getEnum("right_single_tap_gesture", SingleActionGesture.PlayPause)
  val useSingleTapForLeftRight = preferenceStore.getBoolean("use_single_tap_for_left_right", true)
  val reverseDoubleTap = preferenceStore.getBoolean("reverse_double_tap", false)
  val doubleTapChapterToSkip = preferenceStore.getBoolean("double_tap_chapter_to_skip", false)
  val dontShowSeekBarOnDoubleTap = preferenceStore.getBoolean("dont_show_seekbar_on_double_tap", true)
  val twoFingerTapToSkipChapter = preferenceStore.getBoolean("two_finger_tap_to_skip_chapter", true)
  val twoFingerDoubleTapToSkip = preferenceStore.getBoolean("two_finger_double_tap_to_skip", true)
  val mediaPreviousGesture = preferenceStore.getEnum("media_previous_gesture", SingleActionGesture.PlaylistPrev)
  val mediaPlayGesture = preferenceStore.getEnum("media_play_gesture", SingleActionGesture.PlayPause)
  val mediaNextGesture = preferenceStore.getEnum("media_next_gesture", SingleActionGesture.PlaylistNext)
  val tapThumbnailToSelect = preferenceStore.getBoolean("tap_thumbnail_to_select", false)
  val speedLockHintShownCount = preferenceStore.getInt("speed_lock_hint_shown_count", 0)
  val hasLockedSpeedBefore = preferenceStore.getBoolean("has_locked_speed_before", false)
  val subtitlePosHintShownCount = preferenceStore.getInt("subtitle_pos_hint_shown_count", 0)
  val hasDraggedSubtitleBefore = preferenceStore.getBoolean("has_dragged_subtitle_before", false)
}
