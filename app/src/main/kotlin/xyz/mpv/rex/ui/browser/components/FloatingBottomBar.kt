package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerDefaults
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.ui.theme.pillShape
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Material 3 Floating Button Bar for file/folder operations
 * Icon-only buttons in a floating pill-shaped surface
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserBottomBar(
  isSelectionMode: Boolean,
  onCopyClick: () -> Unit,
  onMoveClick: () -> Unit,
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onAddToPlaylistClick: () -> Unit,
  modifier: Modifier = Modifier,
  showCopy: Boolean = true,
  showMove: Boolean = true,
  showRename: Boolean = true,
  showDelete: Boolean = true,
  showAddToPlaylist: Boolean = true,
  onMarkAsClick: (() -> Unit)? = null,
) {
  val miniPlayerStateManager = koinInject<MiniPlayerStateManager>()
  val miniPlayerState by miniPlayerStateManager.state.collectAsState()
  val backstack = LocalBackStack.current
  val isMainScreen = backstack.lastOrNull() == MainScreen
  val haptic = LocalHapticFeedback.current

  val navBarHeight = if (isMainScreen) 64.dp else 0.dp
  val miniPlayerOffset = if (miniPlayerState.isPlaybackActive) MiniPlayerDefaults.TotalCompactOffset else 0.dp
  val targetBottomPadding = navBarHeight + miniPlayerOffset + 16.dp

  val animatedBottomPadding by animateDpAsState(
    targetValue = targetBottomPadding,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "browserBottomBarPadding"
  )

  AnimatedVisibility(
    visible = isSelectionMode,
    modifier = modifier.padding(bottom = animatedBottomPadding),
    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
      slideInVertically(
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow
        ),
        initialOffsetY = { it / 2 }
      ) +
      scaleIn(
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow
        ),
        initialScale = 0.85f
      ),
    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
      slideOutVertically(
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        targetOffsetY = { it / 2 }
      ) +
      scaleOut(
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        targetScale = 0.9f
      ),
  ) {
    Surface(
      modifier = Modifier
        .windowInsetsPadding(WindowInsets.systemBars)
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
          shape = pillShape
        ),
      shape = pillShape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      tonalElevation = 6.dp,
      shadowElevation = 10.dp
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val buttonShape = RoundedCornerShape(14.dp)
        val buttonSize = 44.dp

        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCopyClick()
          },
          enabled = showCopy,
          modifier = Modifier.size(buttonSize),
          shape = buttonShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          )
        ) {
          Icon(
            Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier.size(20.dp)
          )
        }

        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onMoveClick()
          },
          enabled = showMove,
          modifier = Modifier.size(buttonSize),
          shape = buttonShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          )
        ) {
          Icon(
            Icons.AutoMirrored.Filled.DriveFileMove,
            contentDescription = stringResource(R.string.move),
            modifier = Modifier.size(20.dp)
          )
        }

        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRenameClick()
          },
          enabled = showRename,
          modifier = Modifier.size(buttonSize),
          shape = buttonShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          )
        ) {
          Icon(
            Icons.Filled.DriveFileRenameOutline,
            contentDescription = stringResource(R.string.rename),
            modifier = Modifier.size(20.dp)
          )
        }

        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddToPlaylistClick()
          },
          enabled = showAddToPlaylist,
          modifier = Modifier.size(buttonSize),
          shape = buttonShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          )
        ) {
          Icon(
            Icons.AutoMirrored.Filled.PlaylistAdd,
            contentDescription = stringResource(R.string.add_to_playlist),
            modifier = Modifier.size(20.dp)
          )
        }

        if (onMarkAsClick != null) {
          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onMarkAsClick()
            },
            modifier = Modifier.size(buttonSize),
            shape = buttonShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
          ) {
            Icon(
              Icons.Filled.Bookmarks,
              contentDescription = stringResource(R.string.mark_as),
              modifier = Modifier.size(20.dp)
            )
          }
        }

        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDeleteClick()
          },
          enabled = showDelete,
          modifier = Modifier.size(buttonSize),
          shape = buttonShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
          )
        ) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}
