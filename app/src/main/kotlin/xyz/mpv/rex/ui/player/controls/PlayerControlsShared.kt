package xyz.mpv.rex.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as M3Surface
import xyz.mpv.rex.ui.player.controls.components.glassSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.VideoAspect
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.player.controls.components.ControlsButton
import xyz.mpv.rex.ui.player.controls.components.CurrentChapter
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun RenderPlayerButton(
  button: PlayerButton,
  chapters: List<Segment>,
  currentChapter: Int?,
  isPortrait: Boolean,
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
  buttonSize: Dp = 40.dp,
  isMoreSheet: Boolean = false,
) {
  val appearancePreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val inDock = xyz.mpv.rex.ui.player.controls.components.LocalInControlsDock.current
  val itemShape = if (inDock) RoundedCornerShape(10.dp) else RoundedCornerShape(12.dp)
  
  val surfaceColor = when {
    hideBackground -> Color.Transparent
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
  
  val activeSurfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
  }
  
  val activeContentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.onSecondaryContainer
    }
    else -> MaterialTheme.colorScheme.primary
  }
  
  val borderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  )
  
  val activeBorderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  )

  val clickEvent = LocalPlayerButtonsClickEvent.current
  when (button) {
    PlayerButton.BACK_ARROW -> {
      ControlsButton(
        icon = Icons.AutoMirrored.Default.ArrowBack,
        onClick = onBackPress,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.VIDEO_TITLE -> {
      val playlistModeEnabled = viewModel.hasPlaylistSupport()

      val titleInteractionSource = remember { MutableInteractionSource() }

      if (inDock) {
        Box(
          modifier =
            Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable(
                interactionSource = titleInteractionSource,
                indication = ripple(
                  bounded = true,
                ),
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
                stringResource(R.string.playlist_separator, playlistInfo),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f),
              )
            }
          }
        }
      } else {
        Surface(
          shape = itemShape,
          color = surfaceColor,
          contentColor = contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = borderColor,
          modifier =
            Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable(
                interactionSource = titleInteractionSource,
                indication = ripple(
                  bounded = true,
                ),
                enabled = playlistModeEnabled,
                onClick = {
                  clickEvent()
                  onOpenSheet(Sheets.Playlist)
                },
              ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier
                .padding(
                  horizontal = MaterialTheme.spacing.smaller,
                  vertical = MaterialTheme.spacing.smaller,
                ),
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
                stringResource(R.string.playlist_separator, playlistInfo),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }

    PlayerButton.BOOKMARKS_CHAPTERS -> {
      if (chapters.isNotEmpty()) {
        if (isMoreSheet) {
          val chapter = chapters.getOrNull(currentChapter ?: 0)
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable {
                clickEvent()
                onOpenSheet(Sheets.Chapters)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Bookmarks,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = chapter?.name ?: stringResource(R.string.chapters),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
              )
            }
          }
        } else {
          ControlsButton(
            Icons.Default.Bookmarks,
            onClick = { onOpenSheet(Sheets.Chapters) },
            modifier = Modifier.size(buttonSize),
          )
        }
      }
    }

    PlayerButton.PLAYBACK_SPEED -> {
      val playerPreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
      val speedCyclePresetsSet by playerPreferences.speedCyclePresets.collectAsState()
      val sortedCyclePresets = remember(speedCyclePresetsSet) {
        speedCyclePresetsSet.mapNotNull { it.toFloatOrNull() }.sorted().ifEmpty { listOf(1.0f) }
      }

      val cycleSpeed = {
        val currentIndex = sortedCyclePresets.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.01f }
        val nextSpeed = if (currentIndex != -1) {
          sortedCyclePresets[(currentIndex + 1) % sortedCyclePresets.size]
        } else {
          sortedCyclePresets.firstOrNull { it > playbackSpeed + 0.01f } ?: sortedCyclePresets.first()
        }
        `is`.xyz.mpv.MPVLib.setPropertyFloat("speed", nextSpeed.toFixed(2))
      }

      val showText = isSpeedNonOne || isMoreSheet

      @OptIn(ExperimentalFoundationApi::class)
      Surface(
        shape = itemShape,
        color = if (isSpeedNonOne) activeSurfaceColor else surfaceColor,
        contentColor = if (isSpeedNonOne) activeContentColor else contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isSpeedNonOne) activeBorderColor else borderColor,
        modifier = Modifier
          .height(buttonSize)
          .animateContentSize()
          .clip(itemShape)
          .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onClick = {
              clickEvent()
              cycleSpeed()
            },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.PlaybackSpeed)
            },
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.padding(
            horizontal = MaterialTheme.spacing.smaller,
            vertical = MaterialTheme.spacing.smaller,
          ),
        ) {
          Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = stringResource(R.string.playback_speed),
            tint = if (isSpeedNonOne) activeContentColor else contentColor,
            modifier = Modifier.size(24.dp),
          )
          AnimatedVisibility(
            visible = showText,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
          ) {
            Text(
              text = String.format("%.2fx", playbackSpeed),
              maxLines = 1,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(start = 4.dp),
            )
          }
        }
      }
    }

    PlayerButton.DECODER -> {
      Surface(
        shape = itemShape,
        color = surfaceColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = borderColor,
        modifier = Modifier
          .height(buttonSize)
          .clip(itemShape)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onClick = {
              clickEvent()
              onOpenSheet(Sheets.Decoders)
            },
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.smaller,
              ),
        ) {
          if (isMoreSheet) {
            Icon(
              imageVector = Icons.Outlined.Memory,
              contentDescription = null,
              modifier = Modifier.size(24.dp).padding(end = 6.dp)
            )
          }
          Text(
            text = decoder.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    PlayerButton.SCREEN_ROTATION -> {
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { viewModel.cycleScreenRotations() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.screen_rotation),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.ScreenRotation,
            onClick = viewModel::cycleScreenRotations,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.FRAME_NAVIGATION -> {
      val isExpanded by viewModel.isFrameNavigationExpanded.collectAsState()
      val isActive = isExpanded

      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable {
                viewModel.toggleFrameNavigationExpanded()
                onOpenSheet(Sheets.None)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.frame_nav),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        ControlsButton(
          icon = Icons.Default.Camera,
          onClick = viewModel::toggleFrameNavigationExpanded,
          isActive = isActive,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.PICTURE_IN_PICTURE -> {
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { activity.enterPipModeHidingOverlay() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.pip),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.PictureInPictureAlt,
            onClick = { activity.enterPipModeHidingOverlay() },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.ASPECT_RATIO -> {
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable {
                  when (aspect) {
                    VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                    VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                    VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
                  }
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = when (aspect) {
                    VideoAspect.Fit -> Icons.Default.AspectRatio
                    VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                    VideoAspect.Crop -> Icons.Default.FitScreen
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = aspect.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon =
              when (aspect) {
                VideoAspect.Fit -> Icons.Default.AspectRatio
                VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                VideoAspect.Crop -> Icons.Default.FitScreen
              },
            onClick = {
              when (aspect) {
                VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
              }
            },
            onLongClick = { onOpenSheet(Sheets.AspectRatios) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.VIDEO_ZOOM -> {
      val isZoomed = kotlin.math.abs(currentZoom) >= 0.005f
      if (isZoomed || isMoreSheet) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
          shape = itemShape,
          color = if (isZoomed) activeSurfaceColor else surfaceColor,
          contentColor = if (isZoomed) activeContentColor else contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = if (isZoomed) activeBorderColor else borderColor,
          modifier = Modifier
            .height(buttonSize)
            .clip(itemShape)
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.VideoZoom)
              },
              onLongClick = {
                clickEvent()
                viewModel.setVideoZoom(0f)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.small,
              vertical = MaterialTheme.spacing.small,
            ),
          ) {
            Icon(
              imageVector = Icons.Default.ZoomIn,
              contentDescription = stringResource(R.string.video_zoom),
              tint = if (isZoomed) activeContentColor else contentColor,
              modifier = Modifier.size(24.dp),
            )
            Text(
              text = String.format("%.0f%%", currentZoom * 100),
              maxLines = 1,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        ControlsButton(
          Icons.Default.ZoomIn,
          onClick = {
            clickEvent()
            onOpenSheet(Sheets.VideoZoom)
          },
          onLongClick = { viewModel.setVideoZoom(0f) },
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.LOCK_CONTROLS -> {
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { viewModel.lockControls() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.lock),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.LockOpen,
            onClick = viewModel::lockControls,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AUDIO_TRACK -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.AudioTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.audio),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.Audiotrack,
            onClick = { onOpenSheet(Sheets.AudioTracks) },
            onLongClick = { onOpenPanel(Panels.AudioDelay) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SUBTITLES -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.SubtitleTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.subtitles),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.Subtitles,
            onClick = { onOpenSheet(Sheets.SubtitleTracks) },
            onLongClick = { onOpenPanel(Panels.SubtitleDelay) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.MORE_OPTIONS -> {
      if (isMoreSheet) {
          // Hide more options inside more options sheet
      } else {
          ControlsButton(
            Icons.Default.MoreVert,
            onClick = { onOpenSheet(Sheets.More) },
            onLongClick = { onOpenPanel(Panels.VideoFilters) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CURRENT_CHAPTER -> {
      if (isPortrait || isMoreSheet) {
      } else {
        AnimatedVisibility(
          chapters.getOrNull(currentChapter ?: 0) != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          chapters.getOrNull(currentChapter ?: 0)?.let { chapter ->
            CurrentChapter(
              chapter = chapter,
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.Chapters)
              },
            )
          }
        }
      }
    }

    PlayerButton.REPEAT_MODE -> {
      val repeatMode by viewModel.repeatMode.collectAsState()
      val icon = when (repeatMode) {
        xyz.mpv.rex.ui.player.RepeatMode.OFF -> Icons.Default.Repeat
        xyz.mpv.rex.ui.player.RepeatMode.ONE -> Icons.Default.RepeatOne
        xyz.mpv.rex.ui.player.RepeatMode.ALL -> Icons.Default.RepeatOn
      }
      val isActive = repeatMode != xyz.mpv.rex.ui.player.RepeatMode.OFF
      
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { viewModel.cycleRepeatMode() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = when (repeatMode) {
                    xyz.mpv.rex.ui.player.RepeatMode.OFF -> stringResource(R.string.repeat_off)
                    xyz.mpv.rex.ui.player.RepeatMode.ONE -> stringResource(R.string.repeat_one)
                    xyz.mpv.rex.ui.player.RepeatMode.ALL -> stringResource(R.string.repeat_all)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = icon,
            onClick = viewModel::cycleRepeatMode,
            isActive = isActive,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CUSTOM_SKIP -> {
      val playerPreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
      val customSkipDuration by playerPreferences.customSkipDuration.collectAsState()
      if (isMoreSheet) {
          @OptIn(ExperimentalFoundationApi::class)
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .combinedClickable(
                onClick = {
                  clickEvent()
                  viewModel.seekBy(customSkipDuration)
                },
                onLongClick = {
                  clickEvent()
                  onOpenSheet(Sheets.CustomSkipDuration)
                }
              )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.skip_seconds, customSkipDuration),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.FastForward,
            onClick = { viewModel.seekBy(customSkipDuration) },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.CustomSkipDuration)
            },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SHUFFLE -> {
      // Only show shuffle button if there's a playlist (more than one video)
      if (viewModel.hasPlaylistSupport()) {
        val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = itemShape,
              color = if (shuffleEnabled) activeSurfaceColor else surfaceColor,
              contentColor = if (shuffleEnabled) activeContentColor else contentColor,
              border = if (shuffleEnabled) activeBorderColor else borderColor,
              modifier = Modifier
                .height(buttonSize)
                .clip(itemShape)
                .clickable { viewModel.toggleShuffle() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
              ) {
                Icon(
                  imageVector = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = if (shuffleEnabled) stringResource(R.string.shuffle_on) else stringResource(R.string.shuffle_off),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            ControlsButton(
              icon = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
              onClick = viewModel::toggleShuffle,
              isActive = shuffleEnabled,
              modifier = Modifier.size(buttonSize),
            )
        }
      }
    }

    PlayerButton.MIRROR -> {
      val isMirrored by viewModel.isMirrored.collectAsState()
      
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isMirrored) activeSurfaceColor else surfaceColor,
            contentColor = if (isMirrored) activeContentColor else contentColor,
            border = if (isMirrored) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { viewModel.toggleMirroring() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = if (isMirrored) stringResource(R.string.mirrored) else stringResource(R.string.mirror),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.Flip,
            onClick = viewModel::toggleMirroring,
            isActive = isMirrored,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.VERTICAL_FLIP -> {
      val isVerticalFlipped by viewModel.isVerticalFlipped.collectAsState()
      
      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isVerticalFlipped) activeSurfaceColor else surfaceColor,
            contentColor = if (isVerticalFlipped) activeContentColor else contentColor,
            border = if (isVerticalFlipped) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable { viewModel.toggleVerticalFlip() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(90f)
              )
              Text(
                text = if (isVerticalFlipped) stringResource(R.string.flipped) else stringResource(R.string.flip_vertical),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          Surface(
            shape = itemShape,
            color = if (isVerticalFlipped) activeSurfaceColor else surfaceColor,
            contentColor = if (isVerticalFlipped) activeContentColor else contentColor,
            border = if (isVerticalFlipped) activeBorderColor else borderColor,
            modifier = Modifier
              .size(buttonSize)
              .clip(itemShape)
              .clickable(onClick = viewModel::toggleVerticalFlip),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = stringResource(R.string.flip_vertical_desc),
                tint = if (isVerticalFlipped) activeContentColor else contentColor,
                modifier = Modifier
                  .padding(MaterialTheme.spacing.smaller)
                  .size(24.dp)
                  .rotate(90f),
              )
            }
          }
      }
    }

    PlayerButton.AB_LOOP -> {
      val isExpanded by viewModel.isABLoopExpanded.collectAsState()
      val loopA by viewModel.abLoopA.collectAsState()
      val loopB by viewModel.abLoopB.collectAsState()
      val isActive = loopA != null || loopB != null || isExpanded

      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable {
                viewModel.toggleABLoopExpanded()
                onOpenSheet(Sheets.None)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Text(
                text = stringResource(R.string.ab_loop_short),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              )
              Text(
                text = stringResource(R.string.loop),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        Surface(
          shape = itemShape,
          color = if (isActive) activeSurfaceColor else surfaceColor,
          border = if (isActive) activeBorderColor else borderColor,
          modifier = Modifier
            .size(buttonSize)
            .clip(itemShape)
            .clickable(onClick = viewModel::toggleABLoopExpanded),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = stringResource(R.string.ab_loop_short),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              color = if (isActive) activeContentColor else contentColor,
            )
          }
        }
      }
    }

    PlayerButton.BACKGROUND_PLAYBACK -> {
      val audioPreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AudioPreferences>()
      val automaticBackgroundPlayback by audioPreferences.automaticBackgroundPlayback.collectAsState()
      val icon = if (automaticBackgroundPlayback) Icons.Default.Headset else Icons.Default.HeadsetOff

      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .combinedClickable(
                onClick = { audioPreferences.automaticBackgroundPlayback.set(!automaticBackgroundPlayback) },
                onLongClick = { activity.triggerBackgroundPlayback() },
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
              )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.background_playback),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = icon,
            onClick = { audioPreferences.automaticBackgroundPlayback.set(!automaticBackgroundPlayback) },
            onLongClick = { activity.triggerBackgroundPlayback() },
            isActive = automaticBackgroundPlayback,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AMBIENT_MODE -> {
        val isAmbientEnabled by viewModel.isAmbientEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = itemShape,
              color = if (isAmbientEnabled) activeSurfaceColor else surfaceColor,
              contentColor = if (isAmbientEnabled) activeContentColor else contentColor,
              border = if (isAmbientEnabled) activeBorderColor else borderColor,
              modifier = Modifier
                .height(buttonSize)
                .clip(itemShape)
                .clickable { viewModel.toggleAmbientMode() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
              ) {
                Icon(
                  imageVector = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = stringResource(R.string.ambient),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            ControlsButton(
              icon = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
              title = stringResource(R.string.ambience_mode),
              onClick = {
                clickEvent()
                viewModel.toggleAmbientMode()
              },
              isActive = isAmbientEnabled,
              modifier = Modifier.size(buttonSize),
            )
        }
    }

    PlayerButton.SLEEP_TIMER -> {
      val remainingTime by viewModel.remainingTime.collectAsState()
      val isActive = remainingTime > 0

      if (isMoreSheet) {
          Surface(
            shape = itemShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(itemShape)
              .clickable {
                onOpenSheet(Sheets.SleepTimer)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = if (isActive) {
                  android.text.format.DateUtils.formatElapsedTime(remainingTime.toLong())
                } else {
                  stringResource(R.string.sleep_timer)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        ControlsButton(
          icon = Icons.Outlined.Timer,
          title = stringResource(R.string.sleep_timer),
          onClick = { onOpenSheet(Sheets.SleepTimer) },
          isActive = isActive,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.VIDEO_QUALITY -> {
      val availableQualities by viewModel.availableVideoQualities.collectAsState()
      val currentQuality by viewModel.currentVideoQuality.collectAsState()

      if (availableQualities.size > 1) {
        val label = currentQuality?.shortLabel ?: stringResource(R.string.video_quality_auto)
        Surface(
          shape = itemShape,
          color = surfaceColor,
          contentColor = contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = borderColor,
          modifier = Modifier
            .height(buttonSize)
            .clip(itemShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.VideoQuality)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.small,
              vertical = MaterialTheme.spacing.smaller,
            ),
          ) {
            Icon(
              imageVector = Icons.Outlined.HighQuality,
              contentDescription = stringResource(R.string.video_quality),
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = label,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }

    PlayerButton.NONE -> { /* Do nothing */
    }
  }
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val appearancePreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
    val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
    val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
    val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
    val inDock = xyz.mpv.rex.ui.player.controls.components.LocalInControlsDock.current

    val activeSurfaceColor = when {
        hideBackground -> androidx.compose.ui.graphics.Color.Transparent
        matchTheme -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
    }

    val isActive = color == activeSurfaceColor
    
    val finalColor = when {
        inDock && !isActive -> androidx.compose.ui.graphics.Color.Transparent
        enableGlass && color != androidx.compose.ui.graphics.Color.Transparent -> androidx.compose.ui.graphics.Color.Transparent
        else -> color
    }

    val finalBorder = when {
        inDock && !isActive -> null
        enableGlass && color != androidx.compose.ui.graphics.Color.Transparent -> null
        else -> border
    }

    val effectiveShape = when {
        inDock -> RoundedCornerShape(10.dp)
        shape == CircleShape -> RoundedCornerShape(12.dp)
        else -> shape
    }

    val glassModifier = if (enableGlass && !inDock && color != androidx.compose.ui.graphics.Color.Transparent) {
        Modifier.glassSurface(
            shape = effectiveShape as? RoundedCornerShape ?: RoundedCornerShape(12.dp),
            backgroundColor = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            borderColor = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
            borderWidth = 1.dp,
            outerShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.00f),
            outerShadowBlur = 0.dp,
            outerShadowOffsetX = 0.dp,
            outerShadowOffsetY = 0.dp,
            innerHighlightColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
            innerHighlightBlur = 5.dp,
            innerHighlightOffsetX = (-2).dp,
            innerHighlightOffsetY = (-2).dp,
            innerShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
            innerShadowBlur = 5.dp,
            innerShadowOffsetX = 2.dp,
            innerShadowOffsetY = 2.dp
        )
    } else {
        Modifier
    }

    M3Surface(
        modifier = modifier.then(glassModifier),
        shape = effectiveShape,
        color = finalColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = finalBorder,
        content = content
    )
}

