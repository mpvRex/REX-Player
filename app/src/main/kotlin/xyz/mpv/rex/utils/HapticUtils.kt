package xyz.mpv.rex.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

enum class HapticType {
    LIGHT,       // Seeking ticks, toggle switches, subtle drag
    MEDIUM,      // Navigation clicks, tab switches
    HEAVY,       // Long press, delete, irreversible actions
    DOUBLE_CLICK // Play/pause click, double-tap seek
}

object HapticUtils {
    fun performHapticFeedback(context: Context, hapticFeedback: HapticFeedback, type: HapticType) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effectId = when (type) {
                        HapticType.LIGHT -> VibrationEffect.EFFECT_TICK
                        HapticType.MEDIUM -> VibrationEffect.EFFECT_CLICK
                        HapticType.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                        HapticType.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    }
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else {
                    @Suppress("DEPRECATION")
                    when (type) {
                        HapticType.LIGHT -> vibrator.vibrate(10L)
                        HapticType.MEDIUM -> vibrator.vibrate(25L)
                        HapticType.HEAVY -> vibrator.vibrate(55L)
                        HapticType.DOUBLE_CLICK -> vibrator.vibrate(longArrayOf(0, 15, 45, 15), -1)
                    }
                }
            } else {
                val composeType = when (type) {
                    HapticType.LIGHT -> HapticFeedbackType.TextHandleMove
                    else -> HapticFeedbackType.LongPress
                }
                hapticFeedback.performHapticFeedback(composeType)
            }
        } catch (_: Exception) {}
    }
}
