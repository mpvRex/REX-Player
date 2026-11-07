package xyz.mpv.rex.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import xyz.mpv.rex.R
import java.util.concurrent.ConcurrentHashMap

data class ThemePresetDefinition(
    val name: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val seedColor: Color,
    val paletteStyle: String = "VIBRANT"
)

val PRESET_THEMES = listOf(
    ThemePresetDefinition("Default", R.string.theme_default, R.string.theme_default, Color(0xFF6750A4), "TONAL_SPOT"),
    ThemePresetDefinition("Warm", R.string.theme_preset_warm, R.string.theme_preset_warm_desc, Color(0xFFFF6B35), "VIBRANT"),
    ThemePresetDefinition("Cool", R.string.theme_preset_cool, R.string.theme_preset_cool_desc, Color(0xFF1E88E5), "VIBRANT"),
    ThemePresetDefinition("Forest", R.string.theme_preset_forest, R.string.theme_preset_forest_desc, Color(0xFF2E7D32), "TONAL_SPOT"),
    ThemePresetDefinition("Rose", R.string.theme_preset_rose, R.string.theme_preset_rose_desc, Color(0xFFE91E63), "VIBRANT"),
    ThemePresetDefinition("Monochrome", R.string.theme_preset_monochrome, R.string.theme_preset_monochrome_desc, Color(0xFF757575), "MONOCHROME"),
    ThemePresetDefinition("Lavender", R.string.theme_preset_lavender, R.string.theme_preset_lavender_desc, Color(0xFF7C4DFF), "EXPRESSIVE"),
    ThemePresetDefinition("Ocean", R.string.theme_preset_ocean, R.string.theme_preset_ocean_desc, Color(0xFF006064), "VIBRANT"),
    ThemePresetDefinition("Aurora", R.string.theme_preset_aurora, R.string.theme_preset_aurora_desc, Color(0xFF00C853), "VIBRANT"),
    ThemePresetDefinition("Amber", R.string.theme_preset_amber, R.string.theme_preset_amber_desc, Color(0xFFFF6F00), "VIBRANT"),
    ThemePresetDefinition("Crimson", R.string.theme_preset_crimson, R.string.theme_preset_crimson_desc, Color(0xFFB71C1C), "VIBRANT"),
    ThemePresetDefinition("Emerald", R.string.theme_preset_emerald, R.string.theme_preset_emerald_desc, Color(0xFF00897B), "VIBRANT"),
    ThemePresetDefinition("Mint", R.string.theme_preset_mint, R.string.theme_preset_mint_desc, Color(0xFF0097A7), "EXPRESSIVE")
)

private val PRESET_THEMES_MAP = PRESET_THEMES.associateBy { it.name }
private val presetCache = ConcurrentHashMap<String, ColorScheme>()

fun getPresetColorScheme(
    name: String,
    darkTheme: Boolean,
): ColorScheme {
    val cacheKey = "${name}_${if (darkTheme) "dark" else "light"}"
    presetCache[cacheKey]?.let { return it }

    val preset = PRESET_THEMES_MAP[name] ?: PRESET_THEMES.first()
    val hct = Hct.fromInt(preset.seedColor.toArgb())

    val scheme = ColorExtractor.createDynamicScheme(
        sourceHct = hct,
        paletteStyle = preset.paletteStyle,
        isDark = darkTheme,
    )
    presetCache[cacheKey] = scheme
    return scheme
}

fun parseCustomHexColorScheme(
    hexString: String,
    darkTheme: Boolean,
): ColorScheme? {
    val cleanHex = hexString.removePrefix("#").trim()
    val colorInt = try {
        when (cleanHex.length) {
            6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> return null
        }
    } catch (_: Exception) {
        return null
    }

    val cacheKey = "custom_${cleanHex}_${if (darkTheme) "dark" else "light"}"
    presetCache[cacheKey]?.let { return it }

    val hct = Hct.fromInt(colorInt)
    val scheme = ColorExtractor.createDynamicScheme(
        sourceHct = hct,
        paletteStyle = "VIBRANT",
        isDark = darkTheme,
    )
    presetCache[cacheKey] = scheme
    return scheme
}
