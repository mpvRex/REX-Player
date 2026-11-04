package xyz.mpv.rex.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.ui.icons.MaterialSymbolIcon

data class SettingsBadgePalette(
    val container: Color,
    val tint: Color
)

object SettingsPalettes {
    val Amber = SettingsBadgePalette(Color(0xFFFFDCBE), Color(0xFF7C4113))
    val Yellow = SettingsBadgePalette(Color(0xFFFFF0A4), Color(0xFF624F00))
    val Emerald = SettingsBadgePalette(Color(0xFFC4EED0), Color(0xFF18532A))
    val Cyan = SettingsBadgePalette(Color(0xFFC0EAE6), Color(0xFF006A6F))
    val SkyBlue = SettingsBadgePalette(Color(0xFFC2E7FF), Color(0xFF004D77))
    val Indigo = SettingsBadgePalette(Color(0xFFD6E3FF), Color(0xFF1D5FA9))
    val Purple = SettingsBadgePalette(Color(0xFFEEDCFF), Color(0xFF5D367B))
    val Rose = SettingsBadgePalette(Color(0xFFF7D8EB), Color(0xFF82276C))
    val Coral = SettingsBadgePalette(Color(0xFFFFD8E2), Color(0xFF962846))
    val Lime = SettingsBadgePalette(Color(0xFFE0F3B8), Color(0xFF3B5606))
    val Slate = SettingsBadgePalette(Color(0xFFE2E2E6), Color(0xFF44474E))
    val Teal = SettingsBadgePalette(Color(0xFFBCEBEB), Color(0xFF006874))
    val Orange = SettingsBadgePalette(Color(0xFFFFDAC5), Color(0xFF763710))
}

fun getIconPalette(icon: Any?): SettingsBadgePalette {
    val name = when (icon) {
        is MaterialSymbolIcon -> icon.name.lowercase()
        is ImageVector -> icon.name.lowercase()
        else -> ""
    }
    return when {
        name.contains("palette") || name.contains("theme") || name.contains("color") -> SettingsPalettes.Purple
        name.contains("play") || name.contains("video") || name.contains("aspect") -> SettingsPalettes.SkyBlue
        name.contains("layout") || name.contains("quilt") || name.contains("view") -> SettingsPalettes.Teal
        name.contains("audio") || name.contains("volume") || name.contains("sound") -> SettingsPalettes.Rose
        name.contains("subtitle") || name.contains("sub") || name.contains("text") -> SettingsPalettes.Cyan
        name.contains("folder") || name.contains("storage") || name.contains("file") || name.contains("library") -> SettingsPalettes.Yellow
        name.contains("gesture") || name.contains("touch") -> SettingsPalettes.Emerald
        name.contains("decoder") || name.contains("memory") || name.contains("hardware") -> SettingsPalettes.Lime
        name.contains("code") || name.contains("advanced") -> SettingsPalettes.Indigo
        name.contains("info") || name.contains("about") -> SettingsPalettes.Slate
        name.contains("download") || name.contains("cloud") -> SettingsPalettes.Coral
        else -> SettingsPalettes.Indigo
    }
}
