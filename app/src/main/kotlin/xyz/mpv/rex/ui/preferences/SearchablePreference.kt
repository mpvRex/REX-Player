package xyz.mpv.rex.ui.preferences

import androidx.annotation.StringRes
import xyz.mpv.rex.R
import xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen
import xyz.mpv.rex.presentation.Screen

/**
 * Represents a searchable preference item.
 * Used to index all preferences for the settings search feature.
 */
data class SearchablePreference(
    @StringRes val titleRes: Int? = null,
    val title: String? = null,
    @StringRes val summaryRes: Int? = null,
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val category: String,
    val screen: Screen,
    val targetIndex: Int = 0,
)

/**
 * All searchable preferences indexed for settings search.
 */
object SearchablePreferences {
    val allPreferences: List<SearchablePreference> by lazy {
        buildList {
            // Appearance preferences
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_title,
                summaryRes = R.string.pref_appearance_summary,
                keywords = listOf("theme", "dark", "light", "amoled", "material you", "color", "appearance"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_language_title,
                keywords = listOf("language", "locale", "translation", "english", "app language"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_amoled_mode_title,
                summaryRes = R.string.pref_appearance_amoled_mode_summary,
                keywords = listOf("amoled", "black", "dark", "oled", "pure black"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_use_system_font_title,
                summaryRes = R.string.pref_appearance_use_system_font_summary,
                keywords = listOf("font", "system font", "typography", "text"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_match_player_controls_to_theme_title,
                summaryRes = R.string.pref_appearance_match_player_controls_to_theme_summary,
                keywords = listOf("controls", "theme", "color", "accent", "match"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_player_always_dark_mode_title,
                summaryRes = R.string.pref_appearance_player_always_dark_mode_summary,
                keywords = listOf("dark mode", "player", "always dark", "black", "theme"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_home_title,
                summaryRes = R.string.pref_appearance_tab_home_summary,
                keywords = listOf("home", "tab", "navigation", "bottom bar"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_shorts_title,
                summaryRes = R.string.pref_appearance_tab_shorts_summary,
                keywords = listOf("shorts", "tab", "navigation", "bottom bar", "rexshorts"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_you_title,
                summaryRes = R.string.pref_appearance_tab_you_summary,
                keywords = listOf("you", "tab", "navigation", "bottom bar", "profile"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_recents_title,
                summaryRes = R.string.pref_appearance_tab_recents_summary,
                keywords = listOf("recents", "history", "tab", "navigation", "bottom bar"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_playlists_title,
                summaryRes = R.string.pref_appearance_tab_playlists_summary,
                keywords = listOf("playlists", "tab", "navigation", "bottom bar"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_tab_network_title,
                summaryRes = R.string.pref_appearance_tab_network_summary,
                keywords = listOf("network", "tab", "navigation", "bottom bar", "smb", "stream"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_unlimited_name_lines_title,
                summaryRes = R.string.pref_appearance_unlimited_name_lines_summary,
                keywords = listOf("name", "full", "truncate", "lines", "display"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_unplayed_old_video_label_title,
                summaryRes = R.string.pref_appearance_show_unplayed_old_video_label_summary,
                keywords = listOf("unplayed", "old", "label", "video", "new", "indicator"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_unplayed_old_video_days_title,
                keywords = listOf("days", "old", "video", "threshold", "time"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_auto_scroll_title,
                summaryRes = R.string.pref_appearance_auto_scroll_summary,
                keywords = listOf("scroll", "auto", "last played", "resume", "position"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_watched_threshold_title,
                summaryRes = R.string.pref_appearance_watched_threshold_summary,
                keywords = listOf("watched", "threshold", "percentage", "mark as watched", "played"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_audio_files_title,
                summaryRes = R.string.pref_show_audio_files_summary,
                keywords = listOf("audio", "music", "songs", "show audio files", "file explorer"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_include_no_media_content_title,
                summaryRes = R.string.pref_include_no_media_content_summary,
                keywords = listOf("nomedia", "hidden media", "file explorer", "scan", "excluded"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_tree_view_path_title,
                summaryRes = R.string.pref_show_tree_view_path_summary,
                keywords = listOf("tree view", "path", "breadcrumbs", "folder hierarchy", "directory"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_network_thumbnails_title,
                summaryRes = R.string.pref_appearance_show_network_thumbnails_summary,
                keywords = listOf("network", "thumbnail", "stream", "preview", "images"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 9,
            ))

            // Layout preferences
            add(SearchablePreference(
                titleRes = R.string.pref_layout_title,
                summaryRes = R.string.pref_layout_summary,
                keywords = listOf("layout", "controls", "buttons", "player", "customize", "arrange"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_top_right_controls,
                keywords = listOf("controls", "top", "right", "landscape", "buttons"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_bottom_right_controls,
                keywords = listOf("controls", "bottom", "right", "landscape", "buttons"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_bottom_left_controls,
                keywords = listOf("controls", "bottom", "left", "landscape", "buttons"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_portrait_bottom_controls,
                keywords = listOf("controls", "portrait", "bottom", "buttons"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_more_sheet_controls_title,
                keywords = listOf("controls tab", "more sheet", "buttons", "layout", "menu"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_seekbar_style_header,
                keywords = listOf("seekbar style", "modern", "classic", "progress", "appearance"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_show_seekbar_chapters_title,
                summaryRes = R.string.pref_player_show_seekbar_chapters_summary,
                keywords = listOf("chapters", "seekbar", "markers", "gaps", "progress", "player"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_show_seekbar_read_ahead_title,
                summaryRes = R.string.pref_player_show_seekbar_read_ahead_summary,
                keywords = listOf("buffer", "buffered", "cache", "read ahead", "visual hint", "seekbar", "progress", "player"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_controls_layout_below_seekbar_title,
                summaryRes = R.string.pref_controls_layout_below_seekbar_summary_true,
                keywords = listOf("bottom controls", "seekbar", "layout", "position", "below"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_enable_bounce_animation_title,
                summaryRes = R.string.pref_appearance_enable_bounce_animation_summary,
                keywords = listOf("bounce", "bouncy", "animation", "motion", "sliders"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_hide_player_buttons_background_title,
                summaryRes = R.string.pref_appearance_hide_player_buttons_background_summary,
                keywords = listOf("hide", "background", "buttons", "transparent", "player"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_next_previous_buttons_title,
                summaryRes = R.string.pref_appearance_show_next_previous_buttons_summary,
                keywords = listOf("next", "previous", "buttons", "controls", "skip", "overlay", "playlist"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_enable_glass_player_controls_title,
                summaryRes = R.string.pref_appearance_enable_glass_player_controls_summary,
                keywords = listOf("glass", "glassmorphism", "buttons", "shadow", "inner shadow", "highlight", "player", "appearance"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_enable_glass_seekbar_title,
                summaryRes = R.string.pref_appearance_enable_glass_seekbar_summary,
                keywords = listOf("glass", "glassmorphism", "seekbar", "progress", "slider", "player", "appearance"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_controls_on_play_title,
                summaryRes = R.string.pref_appearance_show_controls_on_play_summary,
                keywords = listOf("show controls", "play start", "controls overlay", "display"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_player_gradient_opacity_title,
                keywords = listOf("gradient", "opacity", "shadow", "overlay", "player controls"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_hide_player_control_time,
                keywords = listOf("time", "hide", "controls", "disappear", "timeout", "ms"),
                category = "Player Layout",
                screen = PlayerControlsPreferencesScreen,
                targetIndex = 11,
            ))

            // Player preferences
            add(SearchablePreference(
                titleRes = R.string.pref_player,
                summaryRes = R.string.pref_player_summary,
                keywords = listOf("player", "orientation", "gestures", "controls", "playback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_orientation,
                keywords = listOf("orientation", "landscape", "portrait", "rotate", "screen"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_default_aspect_ratio,
                keywords = listOf("aspect", "ratio", "fit", "crop", "stretch", "screen", "default", "scale"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_remember_aspect_ratio,
                summaryRes = R.string.pref_player_remember_aspect_ratio_summary,
                keywords = listOf("aspect", "ratio", "remember", "persist", "save", "session"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_resume_playback_title,
                summaryRes = R.string.pref_player_resume_playback_summary,
                keywords = listOf("resume", "playback", "position", "continue", "ask", "start over"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_auto_resume_on_ask_title,
                summaryRes = R.string.pref_player_auto_resume_on_ask_summary_on,
                keywords = listOf("auto resume", "resume", "ask", "playback", "start afresh"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_close_after_eof,
                keywords = listOf("close", "end", "playback", "quit", "finish"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_remember_brightness,
                keywords = listOf("brightness", "remember", "display", "screen"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_autoplay_title,
                summaryRes = R.string.pref_autoplay_summary,
                keywords = listOf("auto-queue", "queue", "playlist", "folder", "navigation", "next", "previous"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_autoplay_on_open_title,
                summaryRes = R.string.pref_autoplay_on_open_summary,
                keywords = listOf("autoplay", "open", "start", "pause", "playback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_auto_pip_title,
                summaryRes = R.string.pref_auto_pip_summary,
                keywords = listOf("pip", "picture", "auto", "navigation", "home", "back"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_keep_screen_on_when_paused_title,
                summaryRes = R.string.pref_player_keep_screen_on_when_paused_summary,
                keywords = listOf("screen", "awake", "pause", "timeout", "stay awake"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_background_playback,
                keywords = listOf("background", "playback", "audio", "service", "music", "mini", "player"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_play_in_mini_player,
                summaryRes = R.string.pref_player_play_in_mini_player_summary_on,
                keywords = listOf("mini player", "audio", "background", "bottom bar"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.show_splash_ovals_on_double_tap_to_seek,
                keywords = listOf("oval", "circle", "double tap", "seek", "visual", "feedback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_show_circular_double_tap_seek_title,
                summaryRes = R.string.pref_player_show_circular_double_tap_seek_summary,
                keywords = listOf("circular", "double tap", "seek", "overlay", "circle"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.show_time_on_double_tap_to_seek,
                keywords = listOf("time", "double tap", "seek", "overlay", "timestamp"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 5,
            ))
             add(SearchablePreference(
                 titleRes = R.string.pref_player_use_precise_seeking,
                 keywords = listOf("precise", "seek", "keyframes", "accurate", "navigation"),
                 category = "Player",
                 screen = PlayerPreferencesScreen,
                 targetIndex = 5,
             ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_show_seekbar_when_seeking_title,
                summaryRes = R.string.pref_player_show_seekbar_when_seeking_summary,
                keywords = listOf("seekbar", "progress", "seeking", "gesture seek", "display"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 5,
            ))
             add(SearchablePreference(
                 titleRes = R.string.pref_player_hide_osd_text_title,
                 keywords = listOf("osd", "hide", "seek", "subtitle", "gesture", "text"),
                 category = "Player",
                 screen = PlayerPreferencesScreen,
                 targetIndex = 5,
             ))
             add(SearchablePreference(
                 titleRes = R.string.pref_player_white_seekbar_title,
                 summaryRes = R.string.pref_player_white_seekbar_summary,
                 keywords = listOf("white", "seekbar", "progress", "bar", "color", "player"),
                 category = "Player",
                 screen = PlayerPreferencesScreen,
                 targetIndex = 5,
             ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_custom_skip_duration_title,
                summaryRes = R.string.pref_player_custom_skip_duration_summary,
                keywords = listOf("skip", "duration", "custom skip", "forward", "seconds"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_brightness,
                keywords = listOf("brightness", "gesture", "swipe", "display", "control"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_volume,
                keywords = listOf("volume", "gesture", "swipe", "audio", "sound"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_pinch_to_zoom,
                keywords = listOf("zoom", "pinch", "gesture", "scale", "crop", "video"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_pan_and_zoom,
                keywords = listOf("pan", "zoom", "drag", "scale", "pinch", "gesture", "video"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_horizontal_swipe_to_seek,
                keywords = listOf("horizontal", "swipe", "seek", "gesture", "left", "right"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_swipe_to_subtitle_seek_title,
                summaryRes = R.string.pref_player_gestures_swipe_to_subtitle_seek_summary,
                keywords = listOf("subtitle", "seek", "swipe", "gesture", "sync"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_move_subtitle_by_dragging_title,
                summaryRes = R.string.pref_player_gestures_move_subtitle_by_dragging_summary,
                keywords = listOf("subtitle", "drag", "reposition", "move", "gesture"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_horizontal_swipe_sensitivity,
                summaryRes = R.string.pref_player_gestures_horizontal_swipe_sensitivity_summary,
                keywords = listOf("horizontal", "swipe", "sensitivity", "seek", "distance", "speed"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_hold_for_multiple_speed,
                keywords = listOf("hold", "speed", "multiple", "playback", "tempo", "rate"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_dynamic_speed_overlay_title,
                summaryRes = R.string.pref_dynamic_speed_overlay_summary,
                keywords = listOf("dynamic", "speed", "overlay", "control", "hold", "swipe"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_remember_long_press_speed_title,
                summaryRes = R.string.pref_player_remember_long_press_speed_summary,
                keywords = listOf("speed", "long press", "remember", "playback rate", "hold"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_show_speed_indicator_overlay,
                summaryRes = R.string.pref_player_show_speed_indicator_overlay_summary,
                keywords = listOf("speed", "indicator", "overlay", "chip", "playback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_controls_disable_media_buttons_title,
                summaryRes = R.string.pref_player_controls_disable_media_buttons_summary,
                keywords = listOf("media buttons", "headset", "bluetooth", "hardware keys", "ignore"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_controls_allow_gestures_in_panels,
                keywords = listOf("gestures", "panels", "controls", "overlay", "enable"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.swap_the_volume_and_brightness_slider,
                keywords = listOf("swap", "volume", "brightness", "slider", "left", "right"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_controls_show_loading_circle,
                keywords = listOf("loading", "circle", "indicator", "buffer", "progress"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_show_status_bar,
                keywords = listOf("status bar", "navigation", "system", "show", "hide", "immersive"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_navigation_bar_title,
                keywords = listOf("navigation bar", "controls", "system", "show", "hide"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_reduce_player_animation,
                keywords = listOf("reduce", "animation", "motion", "performance", "smooth"),
                category = "Player",
                screen = PlayerPreferencesScreen,
                targetIndex = 11,
            ))

            // Gesture preferences
            add(SearchablePreference(
                titleRes = R.string.pref_gesture,
                summaryRes = R.string.pref_gesture_summary,
                keywords = listOf("gesture", "double tap", "swipe", "media controls", "touch"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_double_tap_seek_duration,
                keywords = listOf("seek", "duration", "double tap", "time", "seconds"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_seek_area_width_title,
                summaryRes = R.string.pref_double_tap_seek_area_width_summary,
                keywords = listOf("area", "width", "double tap", "seek", "region", "percent"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_reverse_double_tap_title,
                summaryRes = R.string.pref_gesture_reverse_double_tap_summary,
                keywords = listOf("reverse", "swap", "left", "right", "gestures", "invert"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_left_title,
                keywords = listOf("double tap", "left", "seek", "backward", "rewind"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_center_title,
                keywords = listOf("double tap", "center", "play", "pause", "action"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_right_title,
                keywords = listOf("double tap", "right", "seek", "forward", "advance"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_use_single_tap_for_center_title,
                summaryRes = R.string.pref_gesture_use_single_tap_for_center_summary,
                keywords = listOf("single", "tap", "center", "play", "pause"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_use_single_tap_for_left_right_title,
                summaryRes = R.string.pref_gesture_use_single_tap_for_left_right_summary,
                keywords = listOf("single tap", "left", "right", "gesture", "seek"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_prevent_seekbar_tap_title,
                summaryRes = R.string.pref_gesture_prevent_seekbar_tap_summary,
                keywords = listOf("prevent", "seekbar", "tap", "accidental", "touch"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_use_relative_seeking_title,
                summaryRes = R.string.pref_gesture_use_relative_seeking_summary,
                keywords = listOf("relative", "seeking", "drag", "seek", "gesture"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_enable_release_to_cancel_title,
                summaryRes = R.string.pref_gesture_enable_release_to_cancel_summary,
                keywords = listOf("release", "cancel", "revert", "drag", "gesture"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_previous,
                keywords = listOf("media", "previous", "gesture", "control", "backward"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_play,
                keywords = listOf("media", "play", "pause", "gesture", "control"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_next,
                keywords = listOf("media", "next", "gesture", "control", "forward"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_tap_thumbnail_to_select_title,
                summaryRes = R.string.pref_gesture_tap_thumbnail_to_select_summary,
                keywords = listOf("thumbnail", "tap", "select", "play", "preview"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
                targetIndex = 9,
            ))

            // Media & Library preferences
            add(SearchablePreference(
                titleRes = R.string.pref_media_library_title,
                summaryRes = R.string.pref_media_library_summary,
                keywords = listOf("media", "library", "nomedia", "scan", "rescan", "cache", "blacklist", "audio", "folders", "indexing"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_include_no_media_content_title,
                summaryRes = R.string.pref_include_no_media_content_summary,
                keywords = listOf("nomedia", "hidden", "folders", "scan", "media", "library"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_audio_files_title,
                summaryRes = R.string.pref_show_audio_files_summary,
                keywords = listOf("audio", "music", "songs", "sound", "media", "library"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.auto_playlists,
                summaryRes = R.string.auto_playlists_desc,
                keywords = listOf("playlist", "auto", "smart", "rules", "generation"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_library_roots_title,
                keywords = listOf("library", "roots", "scan", "folders", "directories", "storage"),
                category = "Media & Library",
                screen = LibraryRootsPreferencesScreen,
                targetIndex = 0,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_rescan_library_title,
                summaryRes = R.string.pref_rescan_library_summary,
                keywords = listOf("rescan", "scan", "refresh", "indexing", "library", "storage", "reindex"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_clear_metadata_cache_title,
                summaryRes = R.string.pref_clear_metadata_cache_summary,
                keywords = listOf("clear", "cache", "metadata", "thumbnails", "reset", "purge"),
                category = "Media & Library",
                screen = MediaLibraryPreferencesScreen,
                targetIndex = 5,
            ))

            // Folder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_folders_title,
                summaryRes = R.string.pref_folders_summary,
                keywords = listOf("folders", "blacklist", "hide", "exclude", "manage"),
                category = "Folders",
                screen = FoldersPreferencesScreen,
                targetIndex = 0,
            ))

            // Decoder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_decoder,
                summaryRes = R.string.pref_decoder_summary,
                keywords = listOf("decoder", "hardware", "gpu", "debanding", "video"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_profile_title,
                keywords = listOf("decoder profile", "mpv profile", "profile", "decoder", "fast", "high quality", "software", "sw", "hw", "performance"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_try_hw_dec_title,
                keywords = listOf("hardware", "decoding", "hw", "acceleration", "gpu"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_gpu_next_title,
                summaryRes = R.string.pref_decoder_gpu_next_summary,
                keywords = listOf("gpu", "next", "rendering", "backend", "vulkan", "opengl"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_vulkan_title,
                summaryRes = R.string.pref_decoder_vulkan_summary,
                keywords = listOf("vulkan", "gpu", "rendering", "graphics", "api", "performance"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_debanding_title,
                keywords = listOf("deband", "banding", "gradient", "visual", "quality"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_yuv420p_title,
                summaryRes = R.string.pref_decoder_yuv420p_summary,
                keywords = listOf("yuv420p", "chroma", "subsampling", "format", "compatibility"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_anime4k_title,
                summaryRes = R.string.pref_anime4k_summary,
                keywords = listOf("anime4k", "upscale", "shader", "anime", "upscale"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                title = "HDR-to-SDR Tone Mapping (hdr-toys)",
                summary = "Apply high quality GLSL shaders for HDR-to-SDR conversion. Requires gpu-next.",
                keywords = listOf("hdr", "sdr", "tone", "mapping", "hdr-toys", "shaders", "glsl", "color"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_codec_info_title,
                summaryRes = R.string.pref_decoder_codec_info_summary,
                keywords = listOf("codec", "hardware", "software", "decoder", "av1", "hevc", "h264", "vp9", "media", "gpu", "hw", "sw"),
                category = "Decoder",
                screen = CodecInformationScreen,
                targetIndex = 0,
            ))

            // Subtitle preferences
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles,
                summaryRes = R.string.pref_subtitles_summary,
                keywords = listOf("subtitles", "subs", "language", "fonts", "text", "wyzie", "subdl"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_preferred_languages,
                keywords = listOf("language", "preferred", "subtitle", "audio", "locale", "code"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_disable_by_default_title,
                summaryRes = R.string.pref_subtitles_disable_by_default_summary,
                keywords = listOf("disable", "subtitles", "default", "subs", "off"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_autoload_title,
                summaryRes = R.string.pref_subtitles_autoload_summary,
                keywords = listOf("autoload", "automatic", "subtitles", "external", "load"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.player_sheets_sub_override_ass,
                summaryRes = R.string.player_sheets_sub_override_ass_subtitle,
                keywords = listOf("ass", "override", "subtitle", "ssa", "format", "style"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.player_sheets_sub_scale_by_window,
                summaryRes = R.string.player_sheets_sub_scale_by_window_summary,
                keywords = listOf("scale", "window", "subtitle", "size", "resize", "fit"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_force_ltr_title,
                summaryRes = R.string.pref_subtitles_force_ltr_summary,
                keywords = listOf("force", "ltr", "left", "right", "direction", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_encoding_title,
                keywords = listOf("encoding", "charset", "utf-8", "subtitles", "codepage"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_open_at_video_location_title,
                summaryRes = R.string.pref_subtitles_open_at_video_location_summary,
                keywords = listOf("picker", "video", "location", "folder", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_custom_picker_folder_title,
                keywords = listOf("custom", "folder", "picker", "directory", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_fonts_dir,
                keywords = listOf("fonts", "directory", "subtitle", "custom", "folder"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitle_search_title,
                summaryRes = R.string.pref_subtitle_search_summary,
                keywords = listOf("subtitle", "search", "online", "download", "wyzie", "subdl", "subs"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_save_location,
                keywords = listOf("save", "location", "download", "folder", "directory", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_subdl_languages,
                keywords = listOf("languages", "subdl", "online", "search", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                title = "Advanced Search Filters",
                keywords = listOf("advanced", "filters", "subtitle", "search", "formats", "encodings", "hearing impaired"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                title = "Hearing-impaired friendly",
                summary = "Only show subtitles optimized for hearing impaired",
                keywords = listOf("hearing", "impaired", "sdh", "accessibility", "subtitles", "deaf"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_clear_downloads,
                summaryRes = R.string.pref_subtitles_clear_downloads_summary,
                keywords = listOf("clear", "delete", "downloads", "remove", "subtitles"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_wyzie_api_key_title,
                summaryRes = R.string.pref_subtitles_wyzie_api_key_summary,
                keywords = listOf("api", "key", "token", "wyzie", "subtitles", "vip"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
                targetIndex = 3,
            ))

            // Audio preferences
            add(SearchablePreference(
                titleRes = R.string.pref_audio,
                summaryRes = R.string.pref_audio_summary,
                keywords = listOf("audio", "language", "channels", "pitch", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_preferred_languages,
                keywords = listOf("language", "preferred", "subtitle", "audio", "locale", "code"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_pitch_correction_title,
                summaryRes = R.string.pref_audio_pitch_correction_summary,
                keywords = listOf("pitch", "correction", "speed", "audio", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_normalization_title,
                summaryRes = R.string.pref_audio_volume_normalization_summary,
                keywords = listOf("volume", "normalization", "loudness", "audio", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_channels,
                keywords = listOf("channels", "audio", "stereo", "surround", "output", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_boost_cap,
                keywords = listOf("volume", "boost", "cap", "maximum", "amplify"),
                category = "Audio",
                screen = AudioPreferencesScreen,
                targetIndex = 1,
            ))

            // Advanced preferences
            add(SearchablePreference(
                titleRes = R.string.pref_custom_lua_title,
                summaryRes = R.string.pref_custom_lua_summary,
                keywords = listOf("lua", "custom", "button", "code", "player", "overlay", "script"),
                category = "Player",
                screen = CustomButtonScreen,
                targetIndex = 0,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced,
                summaryRes = R.string.pref_advanced_summary,
                keywords = listOf("advanced", "mpv", "config", "logs", "debug"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_export_settings_title,
                summaryRes = R.string.pref_export_settings_summary,
                keywords = listOf("export", "backup", "settings", "xml", "save"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_import_settings_title,
                summaryRes = R.string.pref_import_settings_summary,
                keywords = listOf("import", "restore", "settings", "xml", "load"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_mpv_conf_storage_location,
                keywords = listOf("storage", "location", "directory", "folder", "config"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_mpv_conf,
                keywords = listOf("mpv", "conf", "config", "configuration", "settings"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_input_conf,
                keywords = listOf("input", "conf", "keybindings", "shortcuts", "keys", "controls"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_enable_lua_scripts_title,
                summaryRes = R.string.pref_enable_lua_scripts_summary,
                keywords = listOf("scripts", "lua", "enable", "load", "plugin"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_manage_lua_scripts_title,
                summaryRes = R.string.pref_manage_lua_scripts_summary,
                keywords = listOf("scripts", "lua", "manage", "select", "plugin"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 5,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_enable_recently_played_title,
                summaryRes = R.string.pref_advanced_enable_recently_played_summary,
                keywords = listOf("recently", "played", "history", "enable", "track"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_history_auto_remove_deleted_title,
                summaryRes = R.string.pref_advanced_history_auto_remove_deleted_summary,
                keywords = listOf("history", "auto", "remove", "deleted", "cleanup"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_history_exclude_external_title,
                summaryRes = R.string.pref_advanced_history_exclude_external_summary,
                keywords = listOf("history", "exclude", "external", "intent"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_playback_history,
                keywords = listOf("clear", "history", "playback", "reset", "delete"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 7,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_clear_config_cache_title,
                summaryRes = R.string.pref_clear_config_cache_summary,
                keywords = listOf("clear", "config", "cache", "mpv", "settings"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_clear_thumbnail_cache_title,
                summaryRes = R.string.pref_clear_thumbnail_cache_summary,
                keywords = listOf("clear", "thumbnail", "cache", "preview", "images"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_fonts_cache,
                keywords = listOf("clear", "fonts", "cache", "reset"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 9,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_enable_media_info_title,
                summaryRes = R.string.pref_advanced_enable_media_info_summary,
                keywords = listOf("media", "info", "activity", "system", "integration"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 11,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_verbose_logging_title,
                summaryRes = R.string.pref_advanced_verbose_logging_summary,
                keywords = listOf("verbose", "logging", "debug", "output"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 13,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_dump_logs_title,
                summaryRes = R.string.pref_advanced_dump_logs_summary,
                keywords = listOf("logs", "debug", "dump", "share", "export"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
                targetIndex = 13,
            ))

            // RexShorts preferences
            add(SearchablePreference(
                titleRes = R.string.pref_category_rexshorts_settings,
                summaryRes = R.string.pref_category_rexshorts_settings_desc,
                keywords = listOf("shorts", "rexshorts", "tiktok", "reels", "vertical", "video"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 0,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_enable_rexshorts,
                summaryRes = R.string.pref_enable_rexshorts_summary,
                keywords = listOf("shorts", "rexshorts", "enable", "disable", "tab", "vertical"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_auto_swipe_shorts,
                summaryRes = R.string.pref_auto_swipe_shorts_summary,
                keywords = listOf("auto swipe", "loop", "shorts", "rexshorts", "continuous"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_enable_glass_shorts_controls,
                summaryRes = R.string.pref_enable_glass_shorts_controls_summary,
                keywords = listOf("glass", "controls", "shorts", "rexshorts", "appearance", "blur"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_shorts_back_button,
                summaryRes = R.string.pref_show_shorts_back_button_summary,
                keywords = listOf("back button", "navigation", "shorts", "rexshorts", "controls"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 1,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_include_short_horizontal_videos,
                summaryRes = R.string.pref_include_short_horizontal_videos_summary,
                keywords = listOf("horizontal", "landscape", "short videos", "shorts", "rexshorts"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_max_horizontal_video_duration,
                keywords = listOf("duration", "max duration", "minutes", "horizontal", "shorts"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_sourced_folders,
                summaryRes = R.string.pref_sourced_folders_all,
                keywords = listOf("sourced folders", "folders", "shorts", "source", "select"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 3,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_blocked_videos,
                summaryRes = R.string.pref_blocked_videos_summary,
                keywords = listOf("blocked", "blacklist", "hide", "shorts", "manage"),
                category = "RexShorts",
                screen = ShortsPreferencesScreen,
                targetIndex = 5,
            ))

            // Integrations preferences
            add(SearchablePreference(
                title = "Jellyfin",
                summary = "External player sync",
                keywords = listOf("jellyfin", "server", "sync", "stream", "media", "integration"),
                category = "Integrations",
                screen = JellyfinSettingsScreen,
                targetIndex = 0,
            ))
            add(SearchablePreference(
                title = "yt-dlp",
                summary = "Manage REX Ytdlp & extractor preferences",
                keywords = listOf("ytdl", "yt-dlp", "youtube", "extractor", "stream", "downloader", "addon"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 0,
            ))
            add(SearchablePreference(
                title = "Resolution Preference",
                summary = "Auto / Best, 4K, 2K, 1080p, 720p, 480p, Audio Only",
                keywords = listOf("resolution", "quality", "1080p", "4k", "yt-dlp", "stream", "video"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 2,
            ))
            add(SearchablePreference(
                title = "Geo-Bypass",
                summary = "Bypass geographic video restrictions where possible",
                keywords = listOf("geo-bypass", "region", "country", "restrictions", "yt-dlp", "bypass"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 2,
            ))
            add(SearchablePreference(
                title = "Prefer Nightly Channel",
                summary = "Receive cutting-edge yt-dlp nightly extractor fixes",
                keywords = listOf("nightly", "channel", "updates", "yt-dlp", "extractor", "cutting-edge"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 2,
            ))
            add(SearchablePreference(
                title = "Auto-detect Web Pages",
                summary = "Proactively probe unknown URLs to route HTML web pages through yt-dlp",
                keywords = listOf("auto-detect", "web", "pages", "urls", "probe", "yt-dlp", "routing"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 4,
            ))
            add(SearchablePreference(
                title = "Custom Supported Domains",
                summary = "Domains (comma or space separated) routed through yt-dlp",
                keywords = listOf("custom domains", "domains", "websites", "url", "routing", "yt-dlp"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 4,
            ))
            add(SearchablePreference(
                title = "Custom Format Selector",
                summary = "yt-dlp format selector (e.g. bestvideo+bestaudio/best)",
                keywords = listOf("format", "format selector", "bestvideo", "bestaudio", "yt-dlp", "stream"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 6,
            ))
            add(SearchablePreference(
                title = "Proxy",
                summary = "Proxy URL for yt-dlp connections",
                keywords = listOf("proxy", "http", "socks5", "network", "yt-dlp"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 6,
            ))
            add(SearchablePreference(
                title = "Custom User-Agent",
                summary = "User-Agent Header for yt-dlp requests",
                keywords = listOf("user-agent", "header", "http", "browser", "yt-dlp"),
                category = "Integrations",
                screen = YtdlSettingsScreen,
                targetIndex = 6,
            ))

            // About
            add(SearchablePreference(
                titleRes = R.string.pref_about_title,
                summaryRes = R.string.pref_about_summary,
                keywords = listOf("about", "version", "licenses", "acknowledgments", "info", "app"),
                category = "About",
                screen = AboutScreen,
                targetIndex = 0,
            ))
        }
    }

    /**
     * Search preferences by query using fuzzy matching and relevance scoring.
     * Supports typo tolerance, acronyms/subsequences, and multi-word queries.
     * Results are sorted by relevance score descending.
     */
    fun search(query: String, getStringRes: (Int) -> String): List<SearchablePreference> {
        if (query.isBlank()) return emptyList()

        val normalizedQuery = query.trim()

        data class ScoredPreference(val preference: SearchablePreference, val score: Int)

        return allPreferences.mapNotNull { pref ->
            val title = (if (pref.titleRes != null) getStringRes(pref.titleRes) else pref.title ?: "").trim()
            val summary = (if (pref.summaryRes != null) getStringRes(pref.summaryRes) else pref.summary ?: "").trim()
            val category = pref.category.trim()

            val titleScore = FuzzySearch.score(normalizedQuery, title)
            val keywordScore = pref.keywords.maxOfOrNull { FuzzySearch.score(normalizedQuery, it) } ?: -1
            val summaryScore = if (summary.isNotEmpty()) FuzzySearch.score(normalizedQuery, summary) else -1
            val categoryScore = FuzzySearch.score(normalizedQuery, category)
            val isMultiToken = normalizedQuery.any { it.isWhitespace() || it in "-_/.,()" }
            val combinedScore = if (isMultiToken) {
                FuzzySearch.score(normalizedQuery, "$category $title ${pref.keywords.joinToString(" ")}")
            } else {
                -1
            }

            // Weight scores: title match highest, then keywords, then summary, then category
            val weightedTitle = if (titleScore > 0) titleScore * 3 else -1
            val weightedKeyword = if (keywordScore > 0) keywordScore * 2 else -1
            val weightedCombined = if (combinedScore > 0) combinedScore * 2 else -1
            val weightedSummary = if (summaryScore > 0) summaryScore else -1
            val weightedCategory = if (categoryScore > 0) categoryScore / 2 else -1

            val bestScore = maxOf(weightedTitle, weightedKeyword, weightedCombined, weightedSummary, weightedCategory)

            if (bestScore > 0) {
                ScoredPreference(pref, bestScore)
            } else {
                null
            }
        }
        .sortedByDescending { it.score }
        .map { it.preference }
    }
}
