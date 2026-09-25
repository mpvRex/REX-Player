package xyz.mpv.rex.ui.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.R

class SearchablePreferenceTest {

    private val stringMap = mapOf(
        // Appearance
        R.string.pref_appearance_title to "Appearance",
        R.string.pref_appearance_summary to "Theme, layout, and colors",
        R.string.pref_appearance_language_title to "Language",
        R.string.pref_appearance_amoled_mode_title to "Pure black (AMOLED)",
        R.string.pref_appearance_use_system_font_title to "Use system font",
        R.string.pref_appearance_use_system_font_summary to "Use default system font instead of custom fonts",
        R.string.pref_appearance_match_player_controls_to_theme_title to "Match player controls to theme",
        R.string.pref_appearance_player_always_dark_mode_title to "Player always dark mode",
        R.string.pref_appearance_tab_home_title to "Home",
        R.string.pref_appearance_tab_shorts_title to "Shorts",
        R.string.pref_appearance_tab_you_title to "You",
        R.string.pref_appearance_tab_recents_title to "Recents",
        R.string.pref_appearance_tab_playlists_title to "Playlists",
        R.string.pref_appearance_tab_network_title to "Network",
        R.string.pref_appearance_unlimited_name_lines_title to "Unlimited name lines",
        R.string.pref_appearance_watched_threshold_title to "Watched threshold",
        R.string.pref_show_audio_files_title to "Show audio files",
        R.string.pref_include_no_media_content_title to "Include .nomedia content",
        R.string.pref_show_tree_view_path_title to "Show tree view path",
        // Player
        R.string.pref_player to "Player",
        R.string.pref_player_summary to "Playback, seeking, controls, and display",
        R.string.pref_player_orientation to "Orientation",
        R.string.pref_player_default_aspect_ratio to "Default aspect ratio",
        R.string.pref_player_remember_aspect_ratio to "Remember aspect ratio",
        R.string.pref_player_resume_playback_title to "Resume playback",
        R.string.pref_player_resume_playback_summary to "Choose how previously watched videos are opened",
        R.string.pref_player_auto_resume_on_ask_title to "Auto resume playback",
        R.string.pref_player_auto_resume_on_ask_summary_on to "Automatically resume and offer to start afresh",
        R.string.pref_player_close_after_eof to "Close after playback ends",
        R.string.pref_player_remember_brightness to "Remember brightness",
        R.string.pref_autoplay_title to "Autoplay next video",
        R.string.pref_autoplay_on_open_title to "Autoplay on open",
        R.string.pref_auto_pip_title to "Auto PiP",
        R.string.pref_player_keep_screen_on_when_paused_title to "Keep screen on when paused",
        R.string.pref_player_keep_screen_on_when_paused_summary to "Prevents screen timeout during pause",
        R.string.pref_player_background_playback to "Background playback",
        R.string.pref_player_play_in_mini_player to "Play audio in mini player",
        R.string.pref_player_show_circular_double_tap_seek_title to "Show circular double tap seek",
        R.string.pref_player_show_seekbar_when_seeking_title to "Show seekbar when seeking",
        R.string.pref_player_custom_skip_duration_title to "Custom skip duration",
        R.string.pref_player_gestures_swipe_to_subtitle_seek_title to "Swipe to subtitle seek",
        R.string.pref_player_gestures_move_subtitle_by_dragging_title to "Drag subtitle to reposition",
        R.string.pref_player_remember_long_press_speed_title to "Remember adjusted speed",
        R.string.pref_player_show_speed_indicator_overlay to "Show Speed Indicator Overlay",
        R.string.pref_player_controls_disable_media_buttons_title to "Disable media buttons",
        // Layout
        R.string.pref_layout_title to "Player Layout",
        R.string.pref_layout_more_sheet_controls_title to "Buttons in Controls Tab",
        R.string.pref_seekbar_style_header to "Seekbar Style",
        R.string.pref_controls_layout_below_seekbar_title to "Bottom controls below seekbar",
        R.string.pref_appearance_enable_bounce_animation_title to "Enable bouncy animations",
        R.string.pref_appearance_show_controls_on_play_title to "Show controls on play start",
        R.string.pref_appearance_player_gradient_opacity_title to "Player gradient opacity",
        // Gestures
        R.string.pref_gesture to "Gestures",
        R.string.pref_gesture_summary to "Double-tap, seek gestures, media controls",
        R.string.pref_player_double_tap_seek_duration to "Double tap seek duration",
        R.string.pref_gesture_double_tap_seek_area_width_title to "Double Tap Seek Area Width",
        R.string.pref_double_tap_seek_area_width_title to "Double Tap Seek Area Width",
        R.string.pref_gesture_reverse_double_tap_title to "Reverse left/right gestures",
        R.string.pref_gesture_use_single_tap_for_center_title to "Use single tap for center gesture",
        R.string.pref_gesture_use_single_tap_for_left_right_title to "Use single tap for left/right gesture",
        R.string.pref_gesture_prevent_seekbar_tap_title to "Prevent Seeking on Accidental Taps",
        R.string.pref_gesture_use_relative_seeking_title to "Use Relative Seeking",
        R.string.pref_gesture_enable_release_to_cancel_title to "Enable \"Release to cancel\"",
        // Media Library
        R.string.pref_media_library_title to "Media & Library",
        R.string.pref_media_library_summary to "Folders, scan, library indexing",
        R.string.auto_playlists to "Auto Playlists",
        R.string.auto_playlists_desc to "Configure dynamic system-generated playlists",
        R.string.pref_folders_title to "Excluded Folders",
        R.string.pref_rescan_library_title to "Rescan library",
        // Subtitles
        R.string.pref_subtitles to "Subtitles",
        R.string.pref_subtitles_encoding_title to "Subtitle encoding",
        R.string.pref_subtitles_save_location to "Save Location",
        R.string.pref_subtitles_subdl_languages to "SubDL Languages",
        // Decoder
        R.string.pref_decoder to "Decoder",
        R.string.pref_decoder_profile_title to "MPV Profile",
        // RexShorts
        R.string.pref_category_rexshorts_settings to "RexShorts",
        R.string.pref_category_rexshorts_settings_desc to "RexShorts and Shorts tab configuration",
        R.string.pref_enable_rexshorts to "Enable RexShorts",
        R.string.pref_enable_rexshorts_summary to "Show the Shorts tab in the bottom navigation bar",
        R.string.pref_auto_swipe_shorts to "Auto swipe RexShorts",
        R.string.pref_auto_swipe_shorts_summary to "Automatically move to the next short after playback finishes",
        R.string.pref_enable_glass_shorts_controls to "Enable glass shorts controls",
        R.string.pref_show_shorts_back_button to "Show back button in shorts player",
        R.string.pref_include_short_horizontal_videos to "Include horizontal videos",
        R.string.pref_max_horizontal_video_duration to "Max horizontal video duration",
        R.string.pref_sourced_folders to "Sourced folders",
        R.string.pref_blocked_videos to "Blocked videos",
        R.string.pref_blocked_videos_summary to "Manage hidden or blocked shorts",
        // Advanced & About
        R.string.pref_advanced to "Advanced",
        R.string.pref_about_title to "About",
    )

    private val stringResolver: (Int) -> String = { resId ->
        stringMap[resId] ?: "res_$resId"
    }

    @Test
    fun `searching Resume finds resume playback settings (Issue 397)`() {
        val results = SearchablePreferences.search("Resume", stringResolver)
        assertFalse("Results should not be empty for 'Resume'", results.isEmpty())

        val titles = results.mapNotNull { it.titleRes ?: it.title }
        assertTrue(
            "Expected pref_player_resume_playback_title in results",
            titles.contains(R.string.pref_player_resume_playback_title)
        )
        assertTrue(
            "Expected pref_player_auto_resume_on_ask_title in results",
            titles.contains(R.string.pref_player_auto_resume_on_ask_title)
        )

        // Verify top result is resume playback
        val firstResult = results.first()
        assertEquals(
            "Top result should be Resume playback",
            R.string.pref_player_resume_playback_title,
            firstResult.titleRes
        )
        assertEquals("Player", firstResult.category)
        assertEquals(PlayerPreferencesScreen, firstResult.screen)
        assertEquals(1, firstResult.targetIndex)
    }

    @Test
    fun `searching RexShorts finds shorts preferences`() {
        val results = SearchablePreferences.search("Shorts", stringResolver)
        assertFalse(results.isEmpty())

        val foundEnableShorts = results.any { it.titleRes == R.string.pref_enable_rexshorts }
        val foundAutoSwipe = results.any { it.titleRes == R.string.pref_auto_swipe_shorts }
        val foundShortsCategory = results.any { it.titleRes == R.string.pref_category_rexshorts_settings }

        assertTrue("Expected pref_enable_rexshorts in results", foundEnableShorts)
        assertTrue("Expected pref_auto_swipe_shorts in results", foundAutoSwipe)
        assertTrue("Expected RexShorts category in results", foundShortsCategory)
    }

    @Test
    fun `searching Auto swipe finds auto swipe shorts preference`() {
        val results = SearchablePreferences.search("Auto swipe", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals(R.string.pref_auto_swipe_shorts, first.titleRes)
        assertEquals(ShortsPreferencesScreen, first.screen)
    }

    @Test
    fun `searching yt-dlp finds extractor and network preferences`() {
        val results = SearchablePreferences.search("yt-dlp", stringResolver)
        assertFalse(results.isEmpty())

        val titles = results.map { it.title }
        assertTrue("Expected yt-dlp in results", titles.contains("yt-dlp"))
        assertTrue("Expected Resolution Preference in results", titles.contains("Resolution Preference"))
        assertTrue("Expected Geo-Bypass in results", titles.contains("Geo-Bypass"))
        assertTrue("Expected Prefer Nightly Channel in results", titles.contains("Prefer Nightly Channel"))
    }

    @Test
    fun `searching resolution finds resolution preference`() {
        val results = SearchablePreferences.search("Resolution", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals("Resolution Preference", first.title)
        assertEquals(YtdlSettingsScreen, first.screen)
        assertEquals(2, first.targetIndex)
    }

    @Test
    fun `searching proxy finds proxy preference`() {
        val results = SearchablePreferences.search("proxy", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals("Proxy", first.title)
        assertEquals(YtdlSettingsScreen, first.screen)
        assertEquals(6, first.targetIndex)
    }

    @Test
    fun `searching Jellyfin finds jellyfin integration preference`() {
        val results = SearchablePreferences.search("Jellyfin", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals("Jellyfin", first.title)
        assertEquals(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen, first.screen)
        assertEquals(0, first.targetIndex)
    }

    @Test
    fun `searching auto playlists finds Auto Playlists preference`() {
        val results = SearchablePreferences.search("Auto Playlists", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals(R.string.auto_playlists, first.titleRes)
        assertEquals(MediaLibraryPreferencesScreen, first.screen)
        assertEquals(1, first.targetIndex)
    }

    @Test
    fun `searching gesture relative seeking finds relative seeking preference`() {
        val results = SearchablePreferences.search("Relative Seeking", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals(R.string.pref_gesture_use_relative_seeking_title, first.titleRes)
        assertEquals(GesturePreferencesScreen, first.screen)
        assertEquals(1, first.targetIndex)
    }

    @Test
    fun `searching release to cancel finds release to cancel gesture`() {
        val results = SearchablePreferences.search("release to cancel", stringResolver)
        assertFalse(results.isEmpty())

        val first = results.first()
        assertEquals(R.string.pref_gesture_enable_release_to_cancel_title, first.titleRes)
        assertEquals(GesturePreferencesScreen, first.screen)
    }

    @Test
    fun `searching encoding finds subtitle encoding preference`() {
        val results = SearchablePreferences.search("encoding", stringResolver)
        assertFalse(results.isEmpty())

        val match = results.firstOrNull { it.titleRes == R.string.pref_subtitles_encoding_title }
        assertNotNull("Expected subtitle encoding preference in results", match)
        assertEquals(SubtitlesPreferencesScreen, match?.screen)
    }

    @Test
    fun `searching Decoder profile finds decoder profile preference`() {
        val results = SearchablePreferences.search("Decoder profile", stringResolver)
        assertFalse(results.isEmpty())

        val match = results.firstOrNull { it.titleRes == R.string.pref_decoder_profile_title }
        assertNotNull("Expected decoder profile preference in results", match)
        assertEquals(DecoderPreferencesScreen, match?.screen)
        assertEquals(1, match?.targetIndex)
        assertEquals("Decoder", match?.category)
    }

    @Test
    fun `searching Hearing impaired finds hearing-impaired friendly preference`() {
        val results = SearchablePreferences.search("hearing impaired", stringResolver)
        assertFalse(results.isEmpty())

        val match = results.firstOrNull { it.title == "Hearing-impaired friendly" }
        assertNotNull("Expected Hearing-impaired friendly preference in results", match)
        assertEquals(SubtitlesPreferencesScreen, match?.screen)
        assertEquals(3, match?.targetIndex)
        assertEquals("Subtitles", match?.category)
    }

    @Test
    fun `searching Advanced Search Filters finds advanced search filters preference`() {
        val results = SearchablePreferences.search("Advanced Search Filters", stringResolver)
        assertFalse(results.isEmpty())

        val match = results.firstOrNull { it.title == "Advanced Search Filters" }
        assertNotNull("Expected Advanced Search Filters preference in results", match)
        assertEquals(SubtitlesPreferencesScreen, match?.screen)
        assertEquals(3, match?.targetIndex)
        assertEquals("Subtitles", match?.category)
    }

    @Test
    fun `searching Background Playback finds player background playback preference`() {
        val results = SearchablePreferences.search("Background playback", stringResolver)
        assertFalse(results.isEmpty())

        val match = results.firstOrNull { it.titleRes == R.string.pref_player_background_playback }
        assertNotNull("Expected pref_player_background_playback in results", match)
        assertEquals(PlayerPreferencesScreen, match?.screen)
        assertEquals(3, match?.targetIndex)
        assertEquals("Player", match?.category)
    }

    @Test
    fun `blank or empty search returns empty list`() {
        assertTrue(SearchablePreferences.search("", stringResolver).isEmpty())
        assertTrue(SearchablePreferences.search("   ", stringResolver).isEmpty())
    }

    @Test
    fun `all preferences have valid attributes`() {
        val all = SearchablePreferences.allPreferences
        assertTrue("Preferences list should not be empty", all.isNotEmpty())

        for (pref in all) {
            assertTrue(
                "Preference must have titleRes or title: $pref",
                pref.titleRes != null || !pref.title.isNullOrBlank()
            )
            assertTrue(
                "Preference category must not be blank: $pref",
                pref.category.isNotBlank()
            )
            assertTrue(
                "Preference targetIndex must be >= 0: $pref",
                pref.targetIndex >= 0
            )
        }
    }

    @Test
    fun `searching hyphenated queries finds matching preferences`() {
        val autoResumeResults = SearchablePreferences.search("auto-resume", stringResolver)
        assertFalse("Expected results for auto-resume", autoResumeResults.isEmpty())
        assertTrue(
            "Expected auto resume playback in results",
            autoResumeResults.any { it.titleRes == R.string.pref_player_auto_resume_on_ask_title }
        )

        val rexShortsResults = SearchablePreferences.search("rex-shorts", stringResolver)
        assertFalse("Expected results for rex-shorts", rexShortsResults.isEmpty())
        assertTrue(
            "Expected RexShorts in results",
            rexShortsResults.any { it.titleRes == R.string.pref_category_rexshorts_settings }
        )
    }

    @Test
    fun `exact multi-token matches rank strictly higher than typo multi-token matches`() {
        val exactScore = FuzzySearch.score("playback resume", "Resume playback")
        val typoScore = FuzzySearch.score("playbck resume", "Resume playback")
        assertTrue("Exact score should be positive", exactScore > 0)
        assertTrue("Typo score should be positive", typoScore > 0)
        assertTrue(
            "Exact match ($exactScore) must score strictly higher than typo match ($typoScore)",
            exactScore > typoScore
        )
    }

    @Test
    fun `cross-field category and title query matches preference`() {
        val results = SearchablePreferences.search("Player orientation", stringResolver)
        assertFalse(results.isEmpty())
        val first = results.first()
        assertEquals(R.string.pref_player_orientation, first.titleRes)
        assertEquals("Player", first.category)
    }

    @Test
    fun `fuzzy search handles acronyms and delimiters`() {
        assertTrue(FuzzySearch.score("hwdec", "Hardware decoding") > 0)
        assertTrue(FuzzySearch.score("yt dlp", "yt-dlp") > 0)
        assertTrue(FuzzySearch.score("auto-resume", "Auto resume playback") > 0)
    }

    @Test
    fun `contiguous multi-word match ranks strictly higher than scrambled multi-token match`() {
        val contiguousScore = FuzzySearch.score("resume playback", "Auto resume playback")
        val scrambledScore = FuzzySearch.score("playback resume", "Auto resume playback")
        assertTrue(
            "Contiguous phrase ($contiguousScore) must rank strictly higher than scrambled ($scrambledScore)",
            contiguousScore > scrambledScore
        )
    }

    @Test
    fun `repeated query tokens do not match single word target`() {
        val repeatedScore = FuzzySearch.score("video video", "Video")
        assertEquals(-1, repeatedScore)
    }

    @Test
    fun `cross-field search supports all punctuation delimiters`() {
        val commaResults = SearchablePreferences.search("Player, orientation", stringResolver)
        assertFalse("Expected comma-separated query to match", commaResults.isEmpty())
        assertEquals(R.string.pref_player_orientation, commaResults.first().titleRes)
    }

    @Test
    fun `punctuation normalized phrase matches with exact or prefix scale`() {
        val ytdlpScore = FuzzySearch.score("yt dlp", "yt-dlp")
        assertTrue("Expected exact phrase scale >= 850, got $ytdlpScore", ytdlpScore >= 850)
    }

    @Test
    fun `unanchored non-acronym queries do not produce false positive matches`() {
        assertEquals(-1, FuzzySearch.score("lr", "Player"))
    }

    @Test
    fun `all preferences have unique composite keys`() {
        val keys = SearchablePreferences.allPreferences.map { pref ->
            "${pref.titleRes ?: pref.title}_${pref.category}_${pref.screen}"
        }
        val duplicates = keys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("Duplicate preference keys found: $duplicates", duplicates.isEmpty())
    }
}



