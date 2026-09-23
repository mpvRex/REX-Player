package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.VideoAspect
import xyz.mpv.rex.ui.player.controls.components.ControlsButton
import xyz.mpv.rex.ui.player.controls.components.ControlsGroup
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopLeftPlayerControlsLandscape(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  viewModel: PlayerViewModel,
) {
  val appearancePreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val playlistModeEnabled = viewModel.hasPlaylistSupport()
  val clickEvent = LocalPlayerButtonsClickEvent.current

  val contentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  ControlsGroup(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.width(IntrinsicSize.Max).height(42.dp),
  ) {
    ControlsButton(
      icon = Icons.AutoMirrored.Default.ArrowBack,
      onClick = onBackPress,
      modifier = Modifier.size(38.dp),
    )

    if (!hideBackground) {
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(16.dp)
          .background(
            if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
          )
      )
    }

    val titleInteractionSource = remember { MutableInteractionSource() }

    Box(
      modifier =
        Modifier
          .fillMaxHeight()
          .clip(RoundedCornerShape(10.dp))
          .clickable(
            interactionSource = titleInteractionSource,
            indication = ripple(bounded = true),
            enabled = playlistModeEnabled,
            onClick = {
              clickEvent()
              onOpenSheet(Sheets.Playlist)
            },
          )
          .padding(horizontal = 8.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          mediaTitle ?: "",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f, fill = false),
        )
        viewModel.getPlaylistInfo()?.let { playlistInfo ->
          Text(
            " • $playlistInfo",
            maxLines = 1,
            overflow = TextOverflow.Visible,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.75f),
          )
        }
      }
    }
  }
}

@Composable
fun TopRightPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: xyz.mpv.rex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
) {
  if (buttons.isNotEmpty()) {
    ControlsGroup(
      shape = RoundedCornerShape(16.dp),
    ) {
      buttons.forEach { button ->
        RenderPlayerButton(
          button = button,
          chapters = chapters,
          currentChapter = currentChapter,
          isPortrait = false,
          isSpeedNonOne = isSpeedNonOne,
          currentZoom = currentZoom,
          aspect = aspect,
          mediaTitle = mediaTitle,
          hideBackground = hideBackground,
          decoder = decoder,
          playbackSpeed = playbackSpeed,
          onBackPress = onBackPress,
          onOpenSheet = onOpenSheet,
          onOpenPanel = onOpenPanel,
          viewModel = viewModel,
          activity = activity,
          buttonSize = 38.dp,
        )
      }
    }
  }
}

@Composable
fun BottomRightPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: xyz.mpv.rex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
) {
  if (buttons.isNotEmpty()) {
    ControlsGroup(
      shape = RoundedCornerShape(16.dp),
    ) {
      buttons.forEach { button ->
        RenderPlayerButton(
          button = button,
          chapters = chapters,
          currentChapter = currentChapter,
          isPortrait = false,
          isSpeedNonOne = isSpeedNonOne,
          currentZoom = currentZoom,
          aspect = aspect,
          mediaTitle = mediaTitle,
          hideBackground = hideBackground,
          decoder = decoder,
          playbackSpeed = playbackSpeed,
          onBackPress = onBackPress,
          onOpenSheet = onOpenSheet,
          onOpenPanel = onOpenPanel,
          viewModel = viewModel,
          activity = activity,
          buttonSize = 38.dp,
        )
      }
    }
  }
}

@Composable
fun BottomLeftPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: xyz.mpv.rex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
) {
  if (buttons.isNotEmpty()) {
    val chunks = if (buttons.size > 4) {
      buttons.chunked((buttons.size + 1) / 2)
    } else {
      listOf(buttons)
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      chunks.forEach { buttonGroup ->
        ControlsGroup(
          shape = RoundedCornerShape(16.dp),
        ) {
          buttonGroup.forEach { button ->
            RenderPlayerButton(
              button = button,
              chapters = chapters,
              currentChapter = currentChapter,
              isPortrait = false,
              isSpeedNonOne = isSpeedNonOne,
              currentZoom = currentZoom,
              aspect = aspect,
              mediaTitle = mediaTitle,
              hideBackground = hideBackground,
              decoder = decoder,
              playbackSpeed = playbackSpeed,
              onBackPress = onBackPress,
              onOpenSheet = onOpenSheet,
              onOpenPanel = onOpenPanel,
              viewModel = viewModel,
              activity = activity,
              buttonSize = 38.dp,
            )
          }
        }
      }
    }
  }
}



