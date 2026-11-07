package xyz.mpv.rex.ui.icons

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import xyz.mpv.rex.R
import java.util.concurrent.ConcurrentHashMap

data class MaterialSymbolIcon(
    val name: String,
    val filled: Boolean = false,
    val defaultWeight: Int = 400,
    val grade: Float = 0f,
    val opticalSize: Float = 24f
) {
    fun filled(isFilled: Boolean = true): MaterialSymbolIcon = copy(filled = isFilled)
    fun withWeight(weight: Int): MaterialSymbolIcon = copy(defaultWeight = weight)
    fun withOpticalSize(size: Float): MaterialSymbolIcon = copy(opticalSize = size)
    fun withGrade(gradeValue: Float): MaterialSymbolIcon = copy(grade = gradeValue)
}

@OptIn(ExperimentalTextApi::class)
object RexIconFontCache {
    private val cache = ConcurrentHashMap<String, FontFamily>()

    fun getOrCreate(context: Context, filled: Boolean, weight: Int, grade: Float, opticalSize: Float): FontFamily {
        val manufacturer = Build.MANUFACTURER.lowercase()
        // Xiaomi / OEM ROM safety fallback
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            val tf = ResourcesCompat.getFont(context, R.font.material_symbols_outlined) ?: Typeface.DEFAULT
            return FontFamily(tf)
        }

        val key = "$filled-$weight-$grade-$opticalSize"
        return cache.getOrPut(key) {
            try {
                FontFamily(
                    Font(
                        resId = R.font.material_symbols_outlined,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(weight.coerceIn(100, 900)),
                            FontVariation.Setting("FILL", if (filled) 1f else 0f),
                            FontVariation.Setting("GRAD", grade.coerceIn(-25f, 200f)),
                            FontVariation.Setting("opsz", opticalSize.coerceIn(20f, 48f))
                        )
                    )
                )
            } catch (_: Throwable) {
                try {
                    val tf = ResourcesCompat.getFont(context, R.font.material_symbols_outlined) ?: Typeface.DEFAULT
                    FontFamily(tf)
                } catch (_: Throwable) {
                    FontFamily.Default
                }
            }
        }
    }
}

@Composable
fun RexIcon(
    icon: MaterialSymbolIcon,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    isFilled: Boolean = icon.filled,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = if (icon.filled != isFilled) icon.filled(isFilled) else icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun Icon(
    imageVector: MaterialSymbolIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val context = LocalContext.current
    val fontFamily = remember(imageVector) {
        RexIconFontCache.getOrCreate(
            context = context,
            filled = imageVector.filled,
            weight = imageVector.defaultWeight,
            grade = imageVector.grade,
            opticalSize = imageVector.opticalSize
        )
    }

    val semanticsModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    // The caller's modifier decides the glyph size (e.g. `Modifier.size(22.dp)`).
    // Falling back to 24.dp keeps call sites that pass no size unchanged.
    BoxWithConstraints(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        // Use the *minimum* constraint: `defaultMinSize` floors it at 24.dp, and a
        // caller's `Modifier.size(n)` fixes min == max == n. Reading maxWidth
        // instead would pick up a generous parent slot (e.g. the navigation bar's
        // icon area) and blow the glyph up to that width when no size is passed.
        val glyphSize = with(density) { constraints.minWidth.toDp() }
        val iconSizeSp = with(density) { glyphSize.toSp() }

        // Material Symbols draws one em per glyph, but the ink does not sit
        // centred on the em square: font ascent/descent push it down. Neither
        // `includeFontPadding = false` nor `LineHeightStyle` trimming moves the
        // ink, because both only adjust the line box around it.
        //
        // Compose does not expose glyph ink bounds, but it does expose the
        // paragraph height and first baseline. Icon-font glyphs are drawn
        // centred on the em square, so the em's visual centre sits half a font
        // size above the baseline. The box centres the *paragraph*; shifting by
        // the gap between the two centres lands the ink in the middle. Reading
        // the metrics at layout time keeps this correct for whichever variant of
        // the font was loaded (including the OEM fallback above).
        val textStyle = remember(fontFamily, iconSizeSp, tint) {
            TextStyle(
                fontFamily = fontFamily,
                fontSize = iconSizeSp,
                color = tint,
                lineHeight = iconSizeSp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        }
        var inkShiftY by remember(imageVector, textStyle) { mutableStateOf(0f) }

        Text(
            text = imageVector.name,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result ->
                if (result.lineCount > 0) {
                    // The glyph ink is centred on the em square, whose centre sits
                    // half a font size above the baseline. The Box centres the
                    // paragraph, so move the paragraph until the em centre lands
                    // on the box centre.
                    val fontSizePx = with(density) { iconSizeSp.toPx() }
                    val emCentreY = result.getLineTop(0) + result.getLineBaseline(0) - fontSizePx / 2f
                    // Where the paragraph's centre currently sits, in paragraph
                    // coordinates; the Box has already centred it on the box.
                    val paragraphCentreY = result.size.height / 2f
                    // Positive = ink is low = move the paragraph up.
                    val shift = paragraphCentreY - emCentreY
                    if (kotlin.math.abs(shift - inkShiftY) > 0.01f) inkShiftY = shift
                }
            },
            modifier = Modifier.graphicsLayer { translationY = inkShiftY }
        )
    }
}
