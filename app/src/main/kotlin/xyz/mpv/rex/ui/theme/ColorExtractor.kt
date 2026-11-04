package xyz.mpv.rex.ui.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracted palette data containing full M3 tonal color schemes.
 */
data class ExtractedColors(
    val seedColor: Int = 0,
    val isMonochrome: Boolean = false,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val darkPrimary: Int,
    val darkOnPrimary: Int,
    val darkPrimaryContainer: Int,
    val darkOnPrimaryContainer: Int,
    val darkSecondary: Int,
    val darkOnSecondary: Int,
    val darkSecondaryContainer: Int,
    val darkOnSecondaryContainer: Int,
    val darkTertiary: Int,
    val darkOnTertiary: Int,
    val darkTertiaryContainer: Int,
    val darkOnTertiaryContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int
)

@SuppressLint("RestrictedApi")
object ColorExtractor {

    private val colorCache = LruCache<Int, ExtractedColors>(32)
    private val dynamicColors = MaterialDynamicColors()

    /**
     * Extracts a balanced, representative seed color and generates M3 palettes.
     */
    suspend fun extractColorsFromBitmap(bitmap: Bitmap?): ExtractedColors? = withContext(Dispatchers.Default) {
        if (bitmap == null || bitmap.isRecycled) return@withContext null

        val cacheKey = bitmap.hashCode()
        colorCache.get(cacheKey)?.let { return@withContext it }

        try {
            // 1. Downscale to 128px max dimension for fast processing
            val maxDim = 128
            val scale = (maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val workingBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    false
                )
            } else {
                bitmap
            }

            val pixels = IntArray(workingBitmap.width * workingBitmap.height)
            workingBitmap.getPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }

            // 2. Quantize using Celebi quantizer
            val quantized = QuantizerCelebi.quantize(pixels, 128)
            val fallbackArgb = averageColorArgb(pixels)

            // 3. 12-bin Hue Clustering
            val (seedArgb, isMonochrome) = extractSeedAndNeutralStatus(quantized, fallbackArgb)

            // 4. Generate light and dark dynamic schemes
            val sourceHct = Hct.fromInt(seedArgb)
            val lightScheme = createDynamicScheme(sourceHct, if (isMonochrome) "MONOCHROME" else "CONTENT", false)
            val darkScheme = createDynamicScheme(sourceHct, if (isMonochrome) "MONOCHROME" else "CONTENT", true)

            val result = ExtractedColors(
                seedColor = seedArgb,
                isMonochrome = isMonochrome,
                primary = lightScheme.primary.toArgb(),
                onPrimary = lightScheme.onPrimary.toArgb(),
                primaryContainer = lightScheme.primaryContainer.toArgb(),
                onPrimaryContainer = lightScheme.onPrimaryContainer.toArgb(),
                secondary = lightScheme.secondary.toArgb(),
                onSecondary = lightScheme.onSecondary.toArgb(),
                secondaryContainer = lightScheme.secondaryContainer.toArgb(),
                onSecondaryContainer = lightScheme.onSecondaryContainer.toArgb(),
                tertiary = lightScheme.tertiary.toArgb(),
                onTertiary = lightScheme.onTertiary.toArgb(),
                tertiaryContainer = lightScheme.tertiaryContainer.toArgb(),
                onTertiaryContainer = lightScheme.onTertiaryContainer.toArgb(),
                darkPrimary = darkScheme.primary.toArgb(),
                darkOnPrimary = darkScheme.onPrimary.toArgb(),
                darkPrimaryContainer = darkScheme.primaryContainer.toArgb(),
                darkOnPrimaryContainer = darkScheme.onPrimaryContainer.toArgb(),
                darkSecondary = darkScheme.secondary.toArgb(),
                darkOnSecondary = darkScheme.onSecondary.toArgb(),
                darkSecondaryContainer = darkScheme.secondaryContainer.toArgb(),
                darkOnSecondaryContainer = darkScheme.onSecondaryContainer.toArgb(),
                darkTertiary = darkScheme.tertiary.toArgb(),
                darkOnTertiary = darkScheme.onTertiary.toArgb(),
                darkTertiaryContainer = darkScheme.tertiaryContainer.toArgb(),
                darkOnTertiaryContainer = darkScheme.onTertiaryContainer.toArgb(),
                surface = darkScheme.surface.toArgb(),
                onSurface = darkScheme.onSurface.toArgb(),
                surfaceVariant = darkScheme.surfaceVariant.toArgb(),
                onSurfaceVariant = darkScheme.onSurfaceVariant.toArgb()
            )

            colorCache.put(cacheKey, result)
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSeedAndNeutralStatus(quantized: Map<Int, Int>, fallbackArgb: Int): Pair<Int, Boolean> {
        if (quantized.isEmpty()) return fallbackArgb to false

        val colorfulEntries = quantized.entries.filter { (argb, pop) ->
            pop > 0 && !isArgbNearGrayscale(argb) && Hct.fromInt(argb).chroma >= 6.0
        }

        if (colorfulEntries.isEmpty()) {
            val dominant = quantized.maxByOrNull { it.value }?.key ?: fallbackArgb
            return dominant to true
        }

        val hueBins = DoubleArray(12)
        val hueBinColors = Array(12) { mutableListOf<Pair<Int, Int>>() }

        for ((argb, pop) in colorfulEntries) {
            val hct = Hct.fromInt(argb)
            val bin = ((MathUtils.sanitizeDegreesDouble(hct.hue) / 30.0).toInt()) % 12
            hueBins[bin] += pop.toDouble()
            hueBinColors[bin].add(argb to pop)
        }

        var bestBin = 0
        var maxPop = 0.0
        for (i in 0 until 12) {
            if (hueBins[i] > maxPop) {
                maxPop = hueBins[i]
                bestBin = i
            }
        }

        val dominantCluster = hueBinColors[bestBin]
        val bestColor = dominantCluster.maxByOrNull { (argb, pop) ->
            val hct = Hct.fromInt(argb)
            val popScore = (pop.toDouble() / maxPop) * 100.0
            val tonePenalty = when {
                hct.tone < 20.0 -> (20.0 - hct.tone) * 2.0
                hct.tone > 85.0 -> (hct.tone - 85.0) * 2.0
                else -> 0.0
            }
            val chromaBonus = hct.chroma.coerceIn(0.0, 60.0) * 0.5
            popScore - tonePenalty + chromaBonus
        }?.first

        return (bestColor ?: fallbackArgb) to false
    }

    fun isArgbNearGrayscale(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        return (max - min) <= 12
    }

    private fun averageColorArgb(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0xFF6750A4.toInt()
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) {
            r += (p ushr 16) and 0xFF
            g += (p ushr 8) and 0xFF
            b += p and 0xFF
        }
        val size = pixels.size.toLong()
        return (0xFF shl 24) or
               (((r / size).toInt().coerceIn(0, 255)) shl 16) or
               (((g / size).toInt().coerceIn(0, 255)) shl 8) or
               ((b / size).toInt().coerceIn(0, 255))
    }

    /**
     * Constructs a full Material 3 ColorScheme from an HCT seed and palette style.
     */
    fun createDynamicScheme(
        sourceHct: Hct,
        paletteStyle: String,
        isDark: Boolean,
        contrastLevel: Double = 0.0,
        chromaMultiplier: Double = 1.0
    ): ColorScheme {
        val effectiveHct = if (chromaMultiplier != 1.0 && sourceHct.chroma > 0.0) {
            val adjustedChroma = (sourceHct.chroma * chromaMultiplier).coerceIn(0.0, 150.0)
            Hct.from(sourceHct.hue, adjustedChroma, sourceHct.tone)
        } else {
            sourceHct
        }

        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        val scheme: DynamicScheme = when (paletteStyle.uppercase()) {
            "TONAL_SPOT" -> SchemeTonalSpot(effectiveHct, isDark, clampedContrast)
            "VIBRANT" -> SchemeVibrant(effectiveHct, isDark, clampedContrast)
            "EXPRESSIVE" -> SchemeExpressive(effectiveHct, isDark, clampedContrast)
            "FRUIT_SALAD" -> SchemeFruitSalad(effectiveHct, isDark, clampedContrast)
            "CONTENT" -> SchemeContent(effectiveHct, isDark, clampedContrast)
            "MONOCHROME" -> SchemeMonochrome(effectiveHct, isDark, clampedContrast)
            "NEUTRAL" -> SchemeNeutral(effectiveHct, isDark, clampedContrast)
            "FIDELITY" -> SchemeFidelity(effectiveHct, isDark, clampedContrast)
            "RAINBOW" -> SchemeRainbow(effectiveHct, isDark, clampedContrast)
            else -> SchemeTonalSpot(effectiveHct, isDark, clampedContrast)
        }

        val d = dynamicColors
        return ColorScheme(
            primary = Color(d.primary().getArgb(scheme)),
            onPrimary = Color(d.onPrimary().getArgb(scheme)),
            primaryContainer = Color(d.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = Color(d.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = Color(d.inversePrimary().getArgb(scheme)),
            secondary = Color(d.secondary().getArgb(scheme)),
            onSecondary = Color(d.onSecondary().getArgb(scheme)),
            secondaryContainer = Color(d.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = Color(d.onSecondaryContainer().getArgb(scheme)),
            tertiary = Color(d.tertiary().getArgb(scheme)),
            onTertiary = Color(d.onTertiary().getArgb(scheme)),
            tertiaryContainer = Color(d.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = Color(d.onTertiaryContainer().getArgb(scheme)),
            background = Color(d.background().getArgb(scheme)),
            onBackground = Color(d.onBackground().getArgb(scheme)),
            surface = Color(d.surface().getArgb(scheme)),
            onSurface = Color(d.onSurface().getArgb(scheme)),
            surfaceVariant = Color(d.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = Color(d.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = Color(d.surfaceTint().getArgb(scheme)),
            inverseSurface = Color(d.inverseSurface().getArgb(scheme)),
            inverseOnSurface = Color(d.inverseOnSurface().getArgb(scheme)),
            error = Color(d.error().getArgb(scheme)),
            onError = Color(d.onError().getArgb(scheme)),
            errorContainer = Color(d.errorContainer().getArgb(scheme)),
            onErrorContainer = Color(d.onErrorContainer().getArgb(scheme)),
            outline = Color(d.outline().getArgb(scheme)),
            outlineVariant = Color(d.outlineVariant().getArgb(scheme)),
            scrim = Color(d.scrim().getArgb(scheme)),
            surfaceBright = Color(d.surfaceBright().getArgb(scheme)),
            surfaceDim = Color(d.surfaceDim().getArgb(scheme)),
            surfaceContainerLowest = Color(d.surfaceContainerLowest().getArgb(scheme)),
            surfaceContainerLow = Color(d.surfaceContainerLow().getArgb(scheme)),
            surfaceContainer = Color(d.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = Color(d.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = Color(d.surfaceContainerHighest().getArgb(scheme))
        )
    }
}
