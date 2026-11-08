package xyz.mpv.rex.ui.browser.recentlyplayed

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import xyz.mpv.rex.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.FolderCard
import xyz.mpv.rex.ui.browser.cards.PlaylistCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.ContentSurfaceContainer
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.playlist.PlaylistDetailScreen
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import org.koin.compose.koinInject
import java.io.File

@Serializable
object RecentlyPlayedScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val playlistRepository = koinInject<PlaylistRepository>()
    val viewModel: RecentlyPlayedViewModel =
      viewModel(factory = RecentlyPlayedViewModel.factory(context.applicationContext as android.app.Application))

    val recentItems by viewModel.recentItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteFilesCheckbox = rememberSaveable { mutableStateOf(false) }
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }
    val isRefreshing = remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Selection manager for all items (videos and playlists)
    val selectionManager =
      rememberSelectionManager(
        items = recentItems,
        getId = { item ->
          when (item) {
            is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}"
            is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}"
          }
        },
        onDeleteItems = { items, deleteFiles ->
          viewModel.deleteRecentItems(items, deleteFiles)
        },
        onRenameItem = null, // Cannot rename from history screen
        onOperationComplete = { },
      )

    val canNavigateBack = remember { backStack.size > 1 }

    // Handle back button during selection mode or when navigated from another screen
    BackHandler(enabled = selectionManager.isInSelectionMode || canNavigateBack) {
      if (selectionManager.isInSelectionMode) {
        selectionManager.clear()
      } else {
        backStack.navigateBack()
      }
    }

    // Track scroll for FAB visibility - create states here to pass to content
    val listState = remember { LazyListState() }
    val gridState = remember { LazyGridState() }
    val browserPreferences = koinInject<BrowserPreferences>()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val appearancePreferences = koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
    val miniPlayerStateManager = koinInject<xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager>()
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val hidePlayFabShowInHeader by appearancePreferences.hidePlayFabShowInHeader.collectAsState()
    xyz.mpv.rex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = false,
      onExpandedChange = { },
    )

    LaunchedEffect(Unit) {
      xyz.mpv.rex.ui.browser.MainScreen.scrollToTopRequest.collect { tabId ->
        if (tabId == "recents") {
          coroutineScope.launch {
            if (mediaLayoutMode == MediaLayoutMode.GRID) {
              gridState.animateScrollToItem(0)
            } else {
              listState.animateScrollToItem(0)
            }
          }
        }
      }
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      // Nested/pushed screens must set the screen tone themselves; it does not
      // propagate from MainScreen across a NavDisplay boundary, and a nested
      // Scaffold overrides it. surfaceContainer is what shows through the
      // content surface's gaps and around its 24.dp corners.
      containerColor = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Black) {
        androidx.compose.ui.graphics.Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
        topBar = {
          BrowserTopBar(
            title = stringResource(R.string.recently_played),
            scrollBehavior = scrollBehavior,
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = recentItems.size,
            onBackClick = if (canNavigateBack) {
              {
                if (selectionManager.isInSelectionMode) {
                  selectionManager.clear()
                } else {
                  backStack.navigateBack()
                }
              }
            } else null,
            onCancelSelection = { selectionManager.clear() },
            onSortClick = null, // No sorting in recently played
            onSettingsClick = {
              backStack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
            },
            normalPlayClick = if (hidePlayFabShowInHeader && recentItems.isNotEmpty()) {
              {
                coroutineScope.launch {
                  val recentlyPlayedVideos = xyz.mpv.rex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                  val lastPlayed = recentlyPlayedVideos.firstOrNull()
                  if (lastPlayed != null) {
                    MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                  } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.no_recently_played_videos), android.widget.Toast.LENGTH_SHORT).show()
                  }
                }
              }
            } else null,
            isSingleSelection = selectionManager.isSingleSelection,
            onInfoClick = null, // No info in recently played
            onPlayClick = null,
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
            onDeleteClick = { deleteDialogOpen.value = true },
          )
        },
      floatingActionButton = {
        val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        val miniPlayerStateManager = koinInject<xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager>()
        val miniPlayerState by miniPlayerStateManager.state.collectAsState()

        if (!hidePlayFabShowInHeader && recentItems.isNotEmpty()) {
          TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(R.string.play_recently_played_or_first)) } },
            state = rememberTooltipState(),
          ) {
            FloatingActionButtonMenu(
              modifier = Modifier.padding(
                bottom = navigationBarHeight + 8.dp
              ),
              expanded = isFabExpanded.value,
              button = {
                TooltipBox(
                  positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    if (isFabExpanded.value) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                  ),
                  tooltip = { PlainTooltip { Text(stringResource(R.string.play_recently_played_or_first)) } },
                  state = rememberTooltipState(),
                ) {
                  Box(
                    modifier = Modifier.animateFloatingActionButton(
                      visible = !selectionManager.isInSelectionMode && isFabVisible.value,
                      alignment = Alignment.BottomEnd,
                    )
                  ) {
                    ToggleFloatingActionButton(
                      checked = isFabExpanded.value,
                      onCheckedChange = { /* handled by overlay */ },
                    ) {
                      val imageVector by remember {
                        derivedStateOf {
                          if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.PlayArrow
                        }
                      }
                      Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                      )
                    }
                    Box(
                      modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                          detectTapGestures(
                            onTap = {
                              if (isFabExpanded.value) {
                                isFabExpanded.value = false
                              } else {
                                coroutineScope.launch {
                                  val recentlyPlayedVideos =
                                    xyz.mpv.rex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                                  val lastPlayed = recentlyPlayedVideos.firstOrNull()
                                  if (lastPlayed != null) {
                                    MediaUtils.playFile(
                                      lastPlayed.filePath,
                                      context,
                                      "recently_played_button"
                                    )
                                  } else {
                                    android.widget.Toast.makeText(
                                      context,
                                      context.getString(R.string.no_recently_played_videos),
                                      android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                  }
                                }
                              }
                            },
                            onLongPress = {
                              if (!isFabExpanded.value) isFabExpanded.value = true
                            }
                          )
                        }
                    )
                  }
                }
              },
            ) {
              FloatingActionButtonMenuItem(
                onClick = { isFabExpanded.value = false },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(text = stringResource(R.string.play_recently_played_or_first)) },
              )
            }
          }
        }
      },
    ) { padding ->
      when {
        !enableRecentlyPlayed -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.History,
              title = stringResource(R.string.recently_played_disabled_title),
              message = stringResource(R.string.recently_played_disabled_message),
            )
          }
        }

        isLoading && recentItems.isEmpty() -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        recentItems.isEmpty() && !isLoading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.History,
              title = stringResource(R.string.no_recently_played_videos),
              message = stringResource(R.string.no_recently_played_videos_message),
            )
          }
        }

        else -> {
          RecentItemsContent(
            recentItems = recentItems,
            playlistRepository = playlistRepository,
            uiSettings = uiSettings,
            recentlyPlayedFilePath = recentlyPlayedFilePath,
            selectionManager = selectionManager,
            onVideoClick = { video ->
              // Always play individual videos without creating a playlist
              // regardless of playlist mode setting
              MediaUtils.playFile(video, context, "recently_played")
            },
            onPlaylistClick = { playlistItem ->
              // Navigate to playlist detail screen
              backStack.add(PlaylistDetailScreen(playlistItem.playlist.id))
            },
            // The Scaffold padding repeats the navigation-bar inset that the
            // content surface already adds (plus its own 14.dp gap), so keep the
            // top inset only and let the container own the bottom.
            modifier = Modifier.padding(
              PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = 0.dp,
              )
            ),
            isInSelectionMode = selectionManager.isInSelectionMode,
            listState = listState,
            gridState = gridState,
            isRefreshing = isRefreshing,
            headerScrollState = topAppBarState,
            onRefresh = { viewModel.refresh() }
          )
        }
      }

      // Delete confirmation dialog
      if (deleteDialogOpen.value && selectionManager.isInSelectionMode) {
        // Remove selected items from history
        val itemCount = selectionManager.selectedCount
        val itemText = pluralStringResource(R.plurals.item_type_item_plural, itemCount)
        val deleteFiles = deleteFilesCheckbox.value

        val title = if (deleteFiles) {
          stringResource(R.string.delete_files_title, itemCount, itemText)
        } else {
          stringResource(R.string.remove_from_history_title, itemCount, itemText)
        }

        val subtitle = if (deleteFiles) {
          stringResource(R.string.delete_files_msg)
        } else {
          stringResource(R.string.remove_from_history_msg, itemText)
        }

        ConfirmDialog(
          title = title,
          subtitle = subtitle,
          customContent = {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              androidx.compose.material3.Checkbox(
                checked = deleteFilesCheckbox.value,
                onCheckedChange = {
                  deleteFilesCheckbox.value = it
                },
              )
              Text(
                text = stringResource(R.string.also_delete_files),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          },
          onConfirm = {
            selectionManager.deleteSelected(deleteFilesCheckbox.value)
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
          onCancel = {
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
        )
      }
      
    }
  }
}

@Composable
private fun RecentItemsContent(
  recentItems: List<RecentlyPlayedItem>,
  playlistRepository: PlaylistRepository,
  uiSettings: UiSettings,
  recentlyPlayedFilePath: String?,
  selectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<RecentlyPlayedItem, String>,
  onVideoClick: (Video) -> Unit,
  onPlaylistClick: suspend (RecentlyPlayedItem.PlaylistItem) -> Unit,
  modifier: Modifier = Modifier,
  isInSelectionMode: Boolean = false,
  listState: LazyListState,
  gridState: LazyGridState,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  headerScrollState: TopAppBarState? = null,
) {
  val coroutineScope = rememberCoroutineScope()

  ContentSurfaceContainer(
    navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current,
    modifier = modifier,
  ) {
    UnifiedExplorerContent(
      items = recentItems,
      isLoading = false,
      uiSettings = uiSettings,
      isSelected = { selectionManager.isSelected(it) },
      onClick = { item ->
        when (item) {
          is RecentlyPlayedItem.VideoItem -> onVideoClick(item.video)
          is RecentlyPlayedItem.PlaylistItem -> {
            coroutineScope.launch {
              onPlaylistClick(item)
            }
          }
        }
      },
      onLongClick = { item -> selectionManager.handleLongClick(item) },
      onToggleSelection = { selectionManager.toggle(it) },
      emptyTitle = stringResource(R.string.no_recently_played_items),
      emptyMessage = stringResource(R.string.no_recently_played_items_message),
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      headerScrollState = headerScrollState,
      isInSelectionMode = isInSelectionMode,
      listState = listState,
      gridState = gridState,
      useContentSurface = true,
    )
  }
}
