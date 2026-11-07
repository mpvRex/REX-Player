package xyz.mpv.rex.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.ui.icons.MaterialSymbolIcon

/**
 * A tinted pill used behind a settings row's icon.
 *
 * Each palette carries a light and a dark variant. The light values are the
 * original pastel pair (pale container, dark icon). The dark values are the
 * tonal counterparts Material 3 uses for dark schemes: a low-chroma darkened
 * container and a lightened icon that stays legible on it.
 */
data class SettingsBadgePalette(
    val container: Color,
    val tint: Color,
    val darkContainer: Color = container,
    val darkTint: Color = tint,
) {
  /** Picks the variant matching the active theme brightness. */
  @Composable
  fun resolved(): SettingsBadgePalette =
    if (isSystemInDarkTheme()) {
      copy(container = darkContainer, tint = darkTint)
    } else {
      this
    }
}

object SettingsPalettes {
  val Amber =
    SettingsBadgePalette(
      container = Color(0xFFFFDCBE),
      tint = Color(0xFF7C4113),
      darkContainer = Color(0xFF5A3215),
      darkTint = Color(0xFFFFDCBE),
    )
  val Yellow =
    SettingsBadgePalette(
      container = Color(0xFFFFF0A4),
      tint = Color(0xFF624F00),
      darkContainer = Color(0xFF4A3C00),
      darkTint = Color(0xFFFFE96B),
    )
  val Emerald =
    SettingsBadgePalette(
      container = Color(0xFFC4EED0),
      tint = Color(0xFF18532A),
      darkContainer = Color(0xFF16402A),
      darkTint = Color(0xFFA8D9B8),
    )
  val Cyan =
    SettingsBadgePalette(
      container = Color(0xFFC0EAE6),
      tint = Color(0xFF006A6F),
      darkContainer = Color(0xFF004F52),
      darkTint = Color(0xFFA6E8E2),
    )
  val SkyBlue =
    SettingsBadgePalette(
      container = Color(0xFFC2E7FF),
      tint = Color(0xFF004D77),
      darkContainer = Color(0xFF00395B),
      darkTint = Color(0xFFA9DDFF),
    )
  val Indigo =
    SettingsBadgePalette(
      container = Color(0xFFD6E3FF),
      tint = Color(0xFF1D5FA9),
      darkContainer = Color(0xFF1A3A63),
      darkTint = Color(0xFFBFD8FF),
    )
  val Purple =
    SettingsBadgePalette(
      container = Color(0xFFEEDCFF),
      tint = Color(0xFF5D367B),
      darkContainer = Color(0xFF3F2A55),
      darkTint = Color(0xFFE3C9FF),
    )
  val Rose =
    SettingsBadgePalette(
      container = Color(0xFFF7D8EB),
      tint = Color(0xFF82276C),
      darkContainer = Color(0xFF57204A),
      darkTint = Color(0xFFFFC4E4),
    )
  val Coral =
    SettingsBadgePalette(
      container = Color(0xFFFFD8E2),
      tint = Color(0xFF962846),
      darkContainer = Color(0xFF5E2033),
      darkTint = Color(0xFFFFC2D0),
    )
  val Lime =
    SettingsBadgePalette(
      container = Color(0xFFE0F3B8),
      tint = Color(0xFF3B5606),
      darkContainer = Color(0xFF2C3D0A),
      darkTint = Color(0xFFCFE9A4),
    )
  val Slate =
    SettingsBadgePalette(
      container = Color(0xFFE2E2E6),
      tint = Color(0xFF44474E),
      darkContainer = Color(0xFF383A40),
      darkTint = Color(0xFFD5D7DE),
    )
  val Teal =
    SettingsBadgePalette(
      container = Color(0xFFBCEBEB),
      tint = Color(0xFF006874),
      darkContainer = Color(0xFF004A52),
      darkTint = Color(0xFFA0E7E7),
    )
  val Orange =
    SettingsBadgePalette(
      container = Color(0xFFFFDAC5),
      tint = Color(0xFF763710),
      darkContainer = Color(0xFF572A11),
      darkTint = Color(0xFFFFD4BA),
    )
}

/** Infers a palette from an icon name when the row does not set one explicitly. */
fun getIconPalette(icon: Any?): SettingsBadgePalette {
  val name =
    when (icon) {
      is MaterialSymbolIcon -> icon.name.lowercase()
      is ImageVector -> icon.name.lowercase()
      else -> ""
    }
  return when {
    name.contains("palette") || name.contains("theme") || name.contains("color") ->
      SettingsPalettes.Purple
    name.contains("play") || name.contains("video") || name.contains("aspect") ->
      SettingsPalettes.SkyBlue
    name.contains("layout") || name.contains("quilt") || name.contains("view") ->
      SettingsPalettes.Teal
    name.contains("audio") || name.contains("volume") || name.contains("sound") ->
      SettingsPalettes.Rose
    name.contains("subtitle") || name.contains("sub") || name.contains("text") ->
      SettingsPalettes.Cyan
    name.contains("folder") ||
      name.contains("storage") ||
      name.contains("file") ||
      name.contains("library") -> SettingsPalettes.Yellow
    name.contains("gesture") || name.contains("touch") -> SettingsPalettes.Emerald
    name.contains("decoder") || name.contains("memory") || name.contains("hardware") ->
      SettingsPalettes.Lime
    name.contains("code") || name.contains("advanced") -> SettingsPalettes.Indigo
    name.contains("info") || name.contains("about") -> SettingsPalettes.Slate
    name.contains("download") || name.contains("cloud") -> SettingsPalettes.Coral
    else -> SettingsPalettes.Indigo
  }
}
