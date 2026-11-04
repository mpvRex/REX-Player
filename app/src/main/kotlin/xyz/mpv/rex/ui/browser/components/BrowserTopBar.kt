package xyz.mpv.rex.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.LocalThemeTransitionState
import xyz.mpv.rex.ui.theme.pillShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * An action that appears in the selection-mode overflow (⋮) menu.
 * Pass a list of these via [BrowserTopBar.selectionOverflowActions] to populate the menu.
 */
data class SelectionOverflowAction(
  val icon: ImageVector,
  val label: String,
  val onClick: () -> Unit,
)

/**
 * Unified top bar for browser screens that switches between normal and selection modes
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserTopBar(
  title: String,
  isInSelectionMode: Boolean,
  selectedCount: Int,
  totalCount: Int,
  onCancelSelection: () -> Unit,
  modifier: Modifier = Modifier,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  onBackClick: (() -> Unit)? = null,
  onSortClick: (() -> Unit)? = null,
  onSearchClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onRenameClick: (() -> Unit)? = null,
  isSingleSelection: Boolean = false,
  onInfoClick: (() -> Unit)? = null,
  onPlayClick: (() -> Unit)? = null,
  normalPlayClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  selectionOverflowActions: List<SelectionOverflowAction> = emptyList(),
  normalOverflowActions: List<SelectionOverflowAction> = emptyList(),
  additionalActions: @Composable RowScope.() -> Unit = { },
  onTitleLongPress: (() -> Unit)? = null,
  useRemoveIcon: Boolean = false,
  deleteInOverflow: Boolean = false,
  deleteBeforeInfo: Boolean = false,
  isHomeScreen: Boolean = false,
) {
  AnimatedContent(
    targetState = isInSelectionMode,
    transitionSpec = {
      (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
        slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 4 })
        .togetherWith(
          fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 4 }
        )
    },
    label = "BrowserTopBarModeTransition",
  ) { inSelection ->
    if (inSelection) {
      SelectionTopBar(
        selectedCount = selectedCount,
        totalCount = totalCount,
        onCancel = onCancelSelection,
        onDelete = onDeleteClick,
        onRename = onRenameClick,
        isSingleSelection = isSingleSelection,
        onInfo = onInfoClick,
        onPlay = onPlayClick,
        onSelectAll = onSelectAll,
        onInvertSelection = onInvertSelection,
        onDeselectAll = onDeselectAll,
        overflowActions = selectionOverflowActions,
        modifier = modifier,
        useRemoveIcon = useRemoveIcon,
        deleteInOverflow = deleteInOverflow,
        deleteBeforeInfo = deleteBeforeInfo,
      )
    } else {
      NormalTopBar(
        title = title,
        onBackClick = onBackClick,
        onSortClick = onSortClick,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onPlayClick = normalPlayClick,
        normalOverflowActions = normalOverflowActions,
        additionalActions = additionalActions,
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        onTitleLongPress = onTitleLongPress,
        isHomeScreen = isHomeScreen,
      )
    }
  }
}

/**
 * Normal mode top bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalTopBar(
  title: String,
  onBackClick: (() -> Unit)?,
  onSortClick: (() -> Unit)?,
  onSearchClick: (() -> Unit)?,
  onSettingsClick: (() -> Unit)?,
  onPlayClick: (() -> Unit)? = null,
  normalOverflowActions: List<SelectionOverflowAction>,
  additionalActions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  onTitleLongPress: (() -> Unit)?,
  isHomeScreen: Boolean = false,
) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val themeTransition = LocalThemeTransitionState.current
  val coroutineScope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  
  val isCurrentlyDark = when (darkMode) {
    DarkMode.Dark -> true
    DarkMode.Light -> false
    DarkMode.System -> darkTheme
  }
  val currentIsDark by rememberUpdatedState(isCurrentlyDark)

  // Track title bounds for animation position
  val titleBounds = remember { mutableStateOf(Rect.Zero) }
  
  // Helper function to toggle dark mode
  fun toggleDarkMode() {
    when (darkMode) {
      DarkMode.System -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Light -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.System)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Dark -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.System)
      }
    }
  }

  val containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
    Color.Black
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val topBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = containerColor,
    scrolledContainerColor = containerColor,
  )

  val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
  val fontSize = (22 + (30 - 22) * (1 - collapsedFraction)).sp

  val titleContent = @Composable {
    val titleModifier = Modifier
      .onGloballyPositioned { coordinates ->
        titleBounds.value = coordinates.boundsInWindow()
      }
      .pointerInput(onTitleLongPress) {
        detectTapGestures(
          onTap = { localOffset ->
            // Don't allow theme change if animation is in progress
            if (themeTransition?.isAnimating == true) return@detectTapGestures
            
            // Calculate window position for circular reveal
            val windowOffset = Offset(
              titleBounds.value.left + localOffset.x,
              titleBounds.value.top + localOffset.y
            )
            // If currently dark, next theme is light: expand outwards (zoom out).
            // If currently light, next theme is dark: contract inwards (reverse / zoom in).
            themeTransition?.startTransition(windowOffset, isReverse = !currentIsDark)
            // Delay theme change to allow overlay to display first
            coroutineScope.launch {
              kotlinx.coroutines.delay(50)
              toggleDarkMode()
            }
          },
          onLongPress = if (onTitleLongPress != null) {
            { onTitleLongPress() }
          } else null
        )
      }

    Text(
      text = title,
      style =
        if (scrollBehavior != null) {
          MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize
          )
        } else if (onBackClick == null) {
          MaterialTheme.typography.headlineMediumEmphasized
        } else {
          MaterialTheme.typography.headlineSmall
        },
      fontWeight = FontWeight.ExtraBold,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier =
        titleModifier.then(
          if (onBackClick == null) {
            Modifier.padding(start = 8.dp)
          } else {
            Modifier
          },
        ),
    )
  }

  val navigationContent = @Composable {
    if (onBackClick != null) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onBackClick()
        },
        modifier = Modifier.padding(start = if (scrollBehavior != null) 6.dp else 2.dp),
      ) {
        if (scrollBehavior != null) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back),
              modifier = Modifier.size(22.dp),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        } else {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }

  val actionsContent: @Composable RowScope.() -> Unit = {
    if (onPlayClick != null) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onPlayClick()
        },
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.PlayArrow,
          contentDescription = stringResource(R.string.play_recently_played_or_first),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
    if (onSearchClick != null) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSearchClick()
        },
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.Search,
          contentDescription = stringResource(R.string.search_empty_title),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
    if (onSortClick != null) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSortClick()
        },
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Default.ViewComfy,
          contentDescription = stringResource(R.string.sort),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
    if (onSettingsClick != null) {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSettingsClick()
        },
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.Settings,
          contentDescription = stringResource(R.string.settings),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
    additionalActions()
    if (normalOverflowActions.isNotEmpty()) {
      var showNormalOverflowMenu by remember { mutableStateOf(false) }
      Box(modifier = Modifier.padding(start = 0.dp, end = 4.dp)) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            showNormalOverflowMenu = true
          },
        ) {
          Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        DropdownMenu(
          expanded = showNormalOverflowMenu,
          onDismissRequest = { showNormalOverflowMenu = false },
          shape = RoundedCornerShape(16.dp),
        ) {
          normalOverflowActions.forEach { action ->
            DropdownMenuItem(
              leadingIcon = {
                Icon(
                  imageVector = action.icon,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              text = { Text(action.label) },
              onClick = {
                action.onClick()
                showNormalOverflowMenu = false
              },
            )
          }
        }
      }
    }
  }

  if (scrollBehavior != null) {
    LargeTopAppBar(
      colors = topBarColors,
      title = titleContent,
      navigationIcon = navigationContent,
      actions = actionsContent,
      scrollBehavior = scrollBehavior,
      modifier = modifier,
    )
  } else {
    TopAppBar(
      colors = topBarColors,
      title = titleContent,
      navigationIcon = navigationContent,
      actions = actionsContent,
      modifier = modifier,
    )
  }
}

/**
 * Selection mode top bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionTopBar(
  selectedCount: Int,
  totalCount: Int,
  onCancel: () -> Unit,
  onDelete: (() -> Unit)?,
  onRename: (() -> Unit)?,
  isSingleSelection: Boolean,
  onInfo: (() -> Unit)?,
  onPlay: (() -> Unit)?,
  onSelectAll: (() -> Unit)?,
  onInvertSelection: (() -> Unit)?,
  onDeselectAll: (() -> Unit)?,
  overflowActions: List<SelectionOverflowAction> = emptyList(),
  modifier: Modifier = Modifier,
  useRemoveIcon: Boolean = false,
  deleteInOverflow: Boolean = false,
  deleteBeforeInfo: Boolean = false,
) {
  var showDropdown by remember { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }
  val haptic = LocalHapticFeedback.current

  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    title = {
      Box {
        Surface(
          shape = pillShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            showDropdown = true
          },
          modifier = Modifier.animateContentSize(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stringResource(R.string.selected_items, selectedCount, totalCount),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              imageVector = Icons.Filled.ArrowDropDown,
              contentDescription = stringResource(R.string.selection_options),
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        DropdownMenu(
          expanded = showDropdown,
          onDismissRequest = { showDropdown = false },
          shape = RoundedCornerShape(16.dp),
        ) {
          if (onSelectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.select_all)) },
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectAll()
                showDropdown = false
              },
            )
          }
          if (onInvertSelection != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.invert_selection)) },
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onInvertSelection()
                showDropdown = false
              },
            )
          }
          if (onDeselectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.deselect_all)) },
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDeselectAll()
                showDropdown = false
              },
            )
          }
        }
      }
    },
    navigationIcon = {
      IconButton(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onCancel()
        },
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(R.string.generic_cancel),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    },
    actions = {
      // Play icon
      if (onPlay != null) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPlay()
          },
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.play),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // Rename icon
      if (onRename != null) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRename()
          },
          enabled = isSingleSelection,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.DriveFileRenameOutline,
            contentDescription = stringResource(R.string.rename),
            modifier = Modifier.size(24.dp),
            tint =
              if (isSingleSelection) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
          )
        }
      }

      // Remove icon (playlist style) or Delete icon (when deleteBeforeInfo is true) — shown before Info for muscle-memory consistency
      if (onDelete != null && (useRemoveIcon || (deleteBeforeInfo && !deleteInOverflow))) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDelete()
          },
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            imageVector = if (useRemoveIcon) Icons.Filled.RemoveCircle else Icons.Filled.Delete,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      // Info icon
      if (onInfo != null) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onInfo()
          },
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Info,
            contentDescription = stringResource(R.string.info),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      // Delete icon — inline only when not using remove icon, not delegated to overflow, and not shown before info
      if (onDelete != null && !useRemoveIcon && !deleteInOverflow && !deleteBeforeInfo) {
        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDelete()
          },
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      // Overflow (⋮) menu — shown when there are overflow actions or delete is delegated here
      val hasDeleteInOverflow = deleteInOverflow && onDelete != null
      if (overflowActions.isNotEmpty() || hasDeleteInOverflow) {
        Box(modifier = Modifier.padding(start = 0.dp, end = 4.dp)) {
          IconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              showOverflowMenu = true
            },
          ) {
            Icon(
              Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.more_options),
              modifier = Modifier.size(24.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
          DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { showOverflowMenu = false },
            shape = RoundedCornerShape(16.dp),
          ) {
            // Delete shown first in overflow when deleteInOverflow is set
            if (hasDeleteInOverflow) {
              DropdownMenuItem(
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                  )
                },
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                  onDelete()
                  showOverflowMenu = false
                },
              )
            }
            overflowActions.forEach { action ->
              DropdownMenuItem(
                leadingIcon = {
                  Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                text = { Text(action.label) },
                onClick = {
                  action.onClick()
                  showOverflowMenu = false
                },
              )
            }
          }
        }
      }
    },
    modifier = modifier,
  )
}
