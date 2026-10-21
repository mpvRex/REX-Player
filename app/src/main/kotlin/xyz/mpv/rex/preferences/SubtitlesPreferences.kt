package xyz.mpv.rex.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum
import xyz.mpv.rex.ui.player.controls.components.panels.SubtitlesBorderStyle

class SubtitlesPreferences(
  preferenceStore: PreferenceStore,
) {
  val preferredLanguages = preferenceStore.getString("sub_preferred_languages")
  val disableSubtitlesByDefault = preferenceStore.getBoolean("sub_disable_by_default", false)
  val autoloadMatchingSubtitles = preferenceStore.getBoolean("sub_autoload_enabled", true)

  val fontsFolder = preferenceStore.getString("sub_fonts_folder")
  val font = preferenceStore.getString("sub_font", "")
  val fontSize = preferenceStore.getInt("sub_font_size", 55)
  val subScale = preferenceStore.getFloat("sub_scale", 1f)
  val borderSize = preferenceStore.getInt("sub_border_size", 3)
  val bold = preferenceStore.getBoolean("sub_bold", false)
  val italic = preferenceStore.getBoolean("sub_italic", false)

  val textColor = preferenceStore.getInt("sub_color_text", Color.White.toArgb())

  val borderColor = preferenceStore.getInt("sub_color_border", Color.Black.toArgb())
  val borderStyle = preferenceStore.getEnum("sub_border_style", SubtitlesBorderStyle.OutlineAndShadow)
  val shadowOffset = preferenceStore.getInt("sub_shadow_offset", 0)
  val backgroundColor = preferenceStore.getInt("sub_color_bg", Color.Transparent.toArgb())

  val justification = preferenceStore.getEnum("sub_justify", SubtitleJustification.Auto)
  val subPos = preferenceStore.getInt("sub_pos", 100)
  val secondarySubPos = preferenceStore.getInt("secondary_sub_pos", 10)
  val secondarySubScale = preferenceStore.getFloat("secondary_sub_scale", 1f)

  val overrideAssSubs = preferenceStore.getBoolean("sub_override_ass")
  val scaleByWindow = preferenceStore.getBoolean("sub_scale_by_window", true)
  val forceLtr = preferenceStore.getBoolean("sub_force_ltr", false)
  val subtitleEncoding = preferenceStore.getString("sub_encoding", "auto")

  val defaultSubDelay = preferenceStore.getInt("sub_default_delay")
  val defaultSubSpeed = preferenceStore.getFloat("sub_default_speed", 1f)
  
  val pickerPath = preferenceStore.getString("sub_picker_path")
  val fontPickerPath = preferenceStore.getString("sub_font_picker_path")
  val customSubtitleFolder = preferenceStore.getString("sub_custom_folder", "")
  val openPickerAtVideoLocation = preferenceStore.getBoolean("sub_open_at_video_location", false)
  
  val subdlApiKey = preferenceStore.getString("subdl_api_key", "")
  val subtitleSaveFolder = preferenceStore.getString("sub_save_folder", "")
  val subdlLanguages = preferenceStore.getStringSet("subdl_languages", setOf("en"))
  
  val wyzieSources = preferenceStore.getStringSet("wyzie_sources", setOf("all"))
  val wyzieFormats = preferenceStore.getStringSet("wyzie_formats", setOf("srt", "ass"))
  val wyzieEncodings = preferenceStore.getStringSet("wyzie_encodings", setOf("utf-8"))
  val wyzieHearingImpaired = preferenceStore.getBoolean("wyzie_hi", false)
  val wyzieApiKey = preferenceStore.getString("wyzie_api_key", "")
}

enum class SubtitleJustification(
  val value: String,
  val icon: ImageVector,
) {
  Left("left", Icons.AutoMirrored.Default.FormatAlignLeft),
  Center("center", Icons.Default.FormatAlignCenter),
  Right("right", Icons.AutoMirrored.Default.FormatAlignRight),
  Auto("auto", Icons.Default.FormatAlignJustify),
}
