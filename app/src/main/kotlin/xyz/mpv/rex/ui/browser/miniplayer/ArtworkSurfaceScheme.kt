package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import org.koin.compose.koinInject
import xyz.mpv.rex.ui.theme.ColorExtractor
import xyz.mpv.rex.ui.theme.ExtractedColors

/**
 * The dark M3 scheme for the mini player: seeded from the current playback artwork and always
 * generated dark, so the pill separates from the background by color and its shadow reads as
 * soft depth instead of a hard halo, in both app themes. With no artwork it falls back to the
 * app scheme in a dark app theme, or to a dark scheme seeded from the app's primary in a light one.
 */
@Composable
fun rememberArtworkSurfaceScheme(): ColorScheme {
  val stateManager = koinInject<MiniPlayerStateManager>()
  val state by stateManager.state.collectAsState()
  val extractedColors by produceState<ExtractedColors?>(initialValue = null, state.thumbnail) {
    value = ColorExtractor.extractColorsFromBitmap(state.thumbnail)
  }
  val appScheme = MaterialTheme.colorScheme
  return remember(extractedColors, appScheme) {
    val extracted = extractedColors
    when {
      extracted != null ->
        ColorExtractor.createDynamicScheme(
          sourceHct = Hct.fromInt(extracted.seedColor),
          paletteStyle = if (extracted.isMonochrome) "MONOCHROME" else "CONTENT",
          isDark = true,
        )
      appScheme.surface.luminance() < 0.5f -> appScheme
      else ->
        ColorExtractor.createDynamicScheme(
          sourceHct = Hct.fromInt(appScheme.primary.toArgb()),
          paletteStyle = "CONTENT",
          isDark = true,
        )
    }
  }
}
