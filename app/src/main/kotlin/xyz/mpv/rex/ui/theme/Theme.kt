package xyz.mpv.rex.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import org.koin.compose.koinInject
import kotlin.math.hypot

// ============================================================================
// Theme Transition Animation State & Components
// ============================================================================
/**
 * State for managing theme transition animations.
 * This class holds the animation state and provides methods to trigger transitions.
 */
class ThemeTransitionState {
    var isAnimating by mutableStateOf(false)
        private set
    var isReverse by mutableStateOf(false)
        private set
    var clickPosition by mutableStateOf(Offset.Zero)
        private set
    var screenshotBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var animationProgress = Animatable(0f)
        private set
    
    private var captureView: View? = null
    
    fun setView(view: View) {
        captureView = view
    }
    
    /**
     * Start a theme transition animation from the given position.
     * Captures the current screen and begins the reveal animation.
     * Will NOT start a new animation if one is already in progress.
     */
    fun startTransition(position: Offset, isReverse: Boolean = false) {
        // Don't allow new animation while one is in progress
        if (isAnimating) return
        
        captureView?.let { view ->
            try {
                // Capture before setting isAnimating to ensure we get the current state
                val bitmap = view.drawToBitmap()
                screenshotBitmap = bitmap
                clickPosition = position
                this.isReverse = isReverse
                isAnimating = true
            } catch (e: Exception) {
                // If capture fails, just skip the animation
                screenshotBitmap = null
                isAnimating = false
            }
        }
    }
    
    fun finishTransition() {
        screenshotBitmap = null
        clickPosition = Offset.Zero
        isReverse = false
        isAnimating = false
    }
    
    suspend fun resetProgress() {
        animationProgress.snapTo(0f)
    }
}

/**
 * CompositionLocal to provide ThemeTransitionState down the composition tree
 */
val LocalThemeTransitionState = staticCompositionLocalOf<ThemeTransitionState?> { null }

@Composable
fun rememberThemeTransitionState(): ThemeTransitionState {
    val view = LocalView.current
    val state = remember { ThemeTransitionState() }
    state.setView(view)
    return state
}

/**
 * Overlay composable that handles the circular reveal animation.
 * Uses Shape-based clipping for smooth Telegram-like rendering.
 */
@Composable
private fun ThemeTransitionOverlay(
    state: ThemeTransitionState,
    content: @Composable () -> Unit
) {
    val isAnimating = state.isAnimating
    val bitmap = state.screenshotBitmap
    val progress = state.animationProgress.value


    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Render the new content at the base layer (underneath the overlay)
        content()

        // 2. Render the overlay of the old screen state on top if animating and we have a bitmap
        if (isAnimating && bitmap != null && !bitmap.isRecycled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Use CircularRevealShape to clip the old screenshot.
                        // When isReverse is false, clips to area OUTSIDE circle (expanding hole / zoom out).
                        // When isReverse is true, clips to circle area (shrinking circle / reverse).
                        clip = true
                        shape = CircularRevealShape(
                            progress = progress,
                            center = state.clickPosition,
                            containerSize = size,
                            isReverse = state.isReverse,
                        )
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }

    LaunchedEffect(state.isAnimating) {
        if (state.isAnimating) {
            state.resetProgress()
            // Wait for the heavy theme switch recomposition to finish in the background
            kotlinx.coroutines.delay(250)
            state.animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            )
            state.finishTransition()
        }
    }
}

/**
 * Custom Shape that creates a circular reveal effect.
 * When isReverse is false: The circle expands from center, and the shape clips TO THE AREA OUTSIDE the circle (inverse clip / zoom out).
 * When isReverse is true: The circle shrinks towards center, and the shape clips TO THE CIRCLE (zoom in / reverse).
 */
private class CircularRevealShape(
    private val progress: Float,
    private val center: Offset,
    private val containerSize: Size,
    private val isReverse: Boolean = false,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val actualCenter = if (center == Offset.Zero) {
            Offset(size.width / 2f, size.height / 2f)
        } else {
            center
        }
        
        // Calculate the maximum radius needed to cover entire screen from center point
        val maxRadius = longestDistanceToCorner(size, actualCenter) * 1.1f
        
        val path = if (isReverse) {
            // Shrinking circle: old content shrinks from maxRadius down to 0 at actualCenter (reverse reveal)
            val currentRadius = (maxRadius * (1f - progress)).coerceAtLeast(0f)
            android.graphics.Path().apply {
                addCircle(actualCenter.x, actualCenter.y, currentRadius, android.graphics.Path.Direction.CW)
            }
        } else {
            // Expanding hole: old content has a hole expanding from 0 to maxRadius (forward reveal)
            val currentRadius = (maxRadius * progress).coerceAtLeast(0f)
            android.graphics.Path().apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                // Add the entire rectangle
                addRect(0f, 0f, size.width, size.height, android.graphics.Path.Direction.CW)
                // Subtract the circle (creates hole in the middle)
                addCircle(actualCenter.x, actualCenter.y, currentRadius, android.graphics.Path.Direction.CCW)
            }
        }
        
        return androidx.compose.ui.graphics.Outline.Generic(
            path.asComposePath()
        )
    }
    
    private fun longestDistanceToCorner(size: Size, center: Offset): Float {
        val topLeft = hypot(center.x, center.y)
        val topRight = hypot(size.width - center.x, center.y)
        val bottomLeft = hypot(center.x, size.height - center.y)
        val bottomRight = hypot(size.width - center.x, size.height - center.y)
        return maxOf(topLeft, topRight, bottomLeft, bottomRight)
    }
}

/**
 * Extension to convert Android Path to Compose Path
 */
private fun android.graphics.Path.asComposePath(): androidx.compose.ui.graphics.Path {
    val composePath = androidx.compose.ui.graphics.Path()
    composePath.asAndroidPath().set(this)
    return composePath
}

@Composable
private fun ThemeTransitionContent(content: @Composable () -> Unit) {
    val state = LocalThemeTransitionState.current
    
    if (state != null) {
        ThemeTransitionOverlay(state = state, content = content)
    } else {
        content()
    }
}

// ============================================================================
// Main Theme
// ============================================================================

private val lightScheme =
  lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
  )

private val darkScheme =
  darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
  )

private val pureBlackColorScheme =
  darkColorScheme(
    primary = primaryPureBlack,
    onPrimary = onPrimaryPureBlack,
    primaryContainer = primaryContainerPureBlack,
    onPrimaryContainer = onPrimaryContainerPureBlack,
    secondary = secondaryPureBlack,
    onSecondary = onSecondaryPureBlack,
    secondaryContainer = secondaryContainerPureBlack,
    onSecondaryContainer = onSecondaryContainerPureBlack,
    tertiary = tertiaryPureBlack,
    onTertiary = onTertiaryPureBlack,
    tertiaryContainer = tertiaryContainerPureBlack,
    onTertiaryContainer = onTertiaryContainerPureBlack,
    error = errorPureBlack,
    onError = onErrorPureBlack,
    errorContainer = errorContainerPureBlack,
    onErrorContainer = onErrorContainerPureBlack,
    background = backgroundPureBlack,
    onBackground = onBackgroundPureBlack,
    surface = surfacePureBlack,
    onSurface = onSurfacePureBlack,
    surfaceVariant = surfaceVariantPureBlack,
    onSurfaceVariant = onSurfaceVariantPureBlack,
    outline = outlinePureBlack,
    outlineVariant = outlineVariantPureBlack,
    scrim = scrimPureBlack,
    inverseSurface = inverseSurfacePureBlack,
    inverseOnSurface = inverseOnSurfacePureBlack,
    inversePrimary = inversePrimaryPureBlack,
    surfaceDim = surfaceDimPureBlack,
    surfaceBright = surfaceBrightPureBlack,
    surfaceContainerLowest = surfaceContainerLowestPureBlack,
    surfaceContainerLow = surfaceContainerLowPureBlack,
    surfaceContainer = surfaceContainerPureBlack,
    surfaceContainerHigh = surfaceContainerHighPureBlack,
    surfaceContainerHighest = surfaceContainerHighestPureBlack,
  )

internal var darkColorScheme = darkColorScheme()

fun ColorScheme.toAmoledElevationScheme(): ColorScheme = this.copy(
    background = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.Black,
    surfaceDim = androidx.compose.ui.graphics.Color.Black,
    surfaceBright = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF121212),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
)

fun resolveColorScheme(
    appTheme: AppTheme,
    themePreset: String,
    themeIntensity: String,
    useDarkTheme: Boolean,
    isAmoled: Boolean,
    context: android.content.Context,
): ColorScheme {
    val baseScheme = when {
        appTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset.isNotEmpty() && themePreset != "Default" -> {
            getPresetColorScheme(name = themePreset, darkTheme = useDarkTheme, intensity = themeIntensity)
        }
        useDarkTheme -> appTheme.getDarkColorScheme()
        else -> appTheme.getLightColorScheme()
    }

    return if (isAmoled) {
        baseScheme.toAmoledElevationScheme()
    } else {
        baseScheme
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpvexTheme(content: @Composable () -> Unit) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val amoledMode by preferences.amoledMode.collectAsState()
  val appTheme by preferences.appTheme.collectAsState()
  val themePreset by preferences.themePreset.collectAsState()
  val themeIntensity by preferences.themeIntensity.collectAsState()
  val useSystemFont by preferences.useSystemFont.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val context = LocalContext.current

    val useDarkTheme = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> darkTheme
    }

    val isAmoled = useDarkTheme && amoledMode
    val colorScheme = resolveColorScheme(
        appTheme = appTheme,
        themePreset = themePreset,
        themeIntensity = themeIntensity,
        useDarkTheme = useDarkTheme,
        isAmoled = isAmoled,
        context = context,
    )

    // Provide theme transition state first, OUTSIDE MaterialTheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalThemeTransitionState provides rememberThemeTransitionState(),
    ) {
        ThemeTransitionContent {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = getTypography(useSystemFont),
                shapes = AppShapes,
                motionScheme = MotionScheme.expressive(),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpvexPlayerTheme(content: @Composable () -> Unit) {
    val preferences = koinInject<AppearancePreferences>()
    val playerAlwaysDarkMode by preferences.playerAlwaysDarkMode.collectAsState()
    val enableGlassPlayerControls by preferences.enableGlassPlayerControls.collectAsState()
    val darkMode by preferences.darkMode.collectAsState()
    val amoledMode by preferences.amoledMode.collectAsState()
    val appTheme by preferences.appTheme.collectAsState()
    val themePreset by preferences.themePreset.collectAsState()
    val themeIntensity by preferences.themeIntensity.collectAsState()
    val useSystemFont by preferences.useSystemFont.collectAsState()
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val isPlayerAlwaysDark = playerAlwaysDarkMode || enableGlassPlayerControls
    val useDarkTheme = if (isPlayerAlwaysDark) {
        true
    } else {
        when (darkMode) {
            DarkMode.Dark -> true
            DarkMode.Light -> false
            DarkMode.System -> darkTheme
        }
    }

    val isAmoled = useDarkTheme && amoledMode
    val colorScheme = resolveColorScheme(
        appTheme = appTheme,
        themePreset = themePreset,
        themeIntensity = themeIntensity,
        useDarkTheme = useDarkTheme,
        isAmoled = isAmoled,
        context = context,
    )

    // Provide theme transition state first, OUTSIDE MaterialTheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalThemeTransitionState provides rememberThemeTransitionState(),
    ) {
        ThemeTransitionContent {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = getTypography(useSystemFont),
                shapes = AppShapes,
                motionScheme = MotionScheme.expressive(),
                content = content,
            )
        }
    }
}


enum class DarkMode(
  @StringRes val titleRes: Int,
) {
  Dark(R.string.pref_appearance_darkmode_dark),
  Light(R.string.pref_appearance_darkmode_light),
  System(R.string.pref_appearance_darkmode_system),
}

private const val RIPPLE_DRAGGED_ALPHA = .5f
private const val RIPPLE_FOCUSED_ALPHA = .6f
private const val RIPPLE_HOVERED_ALPHA = .4f
private const val RIPPLE_PRESSED_ALPHA = .6f

@OptIn(ExperimentalMaterial3Api::class)
val playerRippleConfiguration
  @Composable get() =
    RippleConfiguration(
      color = MaterialTheme.colorScheme.primaryContainer,
      rippleAlpha =
        RippleAlpha(
          draggedAlpha = RIPPLE_DRAGGED_ALPHA,
          focusedAlpha = RIPPLE_FOCUSED_ALPHA,
          hoveredAlpha = RIPPLE_HOVERED_ALPHA,
          pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )
