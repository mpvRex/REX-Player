package xyz.mpv.rex.ui.browser.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Soft floating shadow shared by the app's floating chrome: a low-alpha black ambient and
 * spot pair instead of the default heavy shadow. Pair it with zero built-in elevation on the
 * surface it decorates.
 */
fun Modifier.softFloatingShadow(shape: Shape, elevation: Dp): Modifier =
  shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.2f),
    spotColor = Color.Black.copy(alpha = 0.2f),
  )
