package xyz.mpv.rex.ui.icons

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
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

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = imageVector.name,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = 24.sp,
                color = tint
            )
        )
    }
}
