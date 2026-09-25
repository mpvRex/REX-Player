package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import org.koin.compose.koinInject

@Composable
fun CurrentChapter(
  chapter: Segment,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val inDock = LocalInControlsDock.current

  val chapterShape = if (inDock) RoundedCornerShape(14.dp) else RoundedCornerShape(16.dp)
  val chapterHeight = if (inDock) 38.dp else 42.dp

  val surfaceColor = when {
    inDock -> Color.Transparent
    hideBackground -> Color.Transparent
    enableGlass -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val contentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  val timeColor = when {
    matchTheme -> MaterialTheme.colorScheme.primary
    enableGlass -> Color.White
    else -> MaterialTheme.colorScheme.primary
  }

  val borderColor = if (inDock || hideBackground || enableGlass) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
  )

  val glassModifier = if (enableGlass && !inDock && !hideBackground) {
    Modifier.glassSurface(
      shape = chapterShape,
      backgroundColor = Color.White.copy(alpha = 0.05f),
      borderColor = Color.White.copy(alpha = 0.15f),
      borderWidth = 1.dp,
      outerShadowColor = Color.Black.copy(alpha = 0.00f),
      outerShadowBlur = 0.dp,
      outerShadowOffsetX = 0.dp,
      outerShadowOffsetY = 0.dp,
      innerHighlightColor = Color.White.copy(alpha = 0.35f),
      innerHighlightBlur = 5.dp,
      innerHighlightOffsetX = (-2).dp,
      innerHighlightOffsetY = (-2).dp,
      innerShadowColor = Color.Black.copy(alpha = 0.35f),
      innerShadowBlur = 5.dp,
      innerShadowOffsetX = 2.dp,
      innerShadowOffsetY = 2.dp
    )
  } else {
    Modifier
  }

  Surface(
    modifier =
      modifier
        .then(glassModifier)
        .height(chapterHeight)
        .widthIn(max = 220.dp)
        .clip(chapterShape)
        .clickable(onClick = onClick),
    shape = chapterShape,
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = borderColor,
  ) {
    AnimatedContent(
      targetState = chapter,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
      contentAlignment = Alignment.Center,
      transitionSpec = {
        if (targetState.start > initialState.start) {
          (slideInVertically { height -> height } + fadeIn())
            .togetherWith(slideOutVertically { height -> -height } + fadeOut())
        } else {
          (slideInVertically { height -> -height } + fadeIn())
            .togetherWith(slideOutVertically { height -> height } + fadeOut())
        }.using(
          SizeTransform(clip = false),
        )
      },
      label = "Chapter",
    ) { currentChapter ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      ) {
        Text(
          text = Utils.prettyTime(currentChapter.start.toInt()),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          color = timeColor,
        )
        currentChapter.name.let {
          Text(
            text = Typography.bullet.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            color = contentColor,
            overflow = TextOverflow.Clip,
          )
          Text(
            text = it,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor,
            modifier = Modifier.basicMarquee(),
          )
        }
      }
    }
  }
}
