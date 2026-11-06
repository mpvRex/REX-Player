package xyz.mpv.rex.ui.browser.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.PlaylistCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.ContentSurfaceContainer
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.sheets.AutoPlaylistsSheet
import xyz.mpv.rex.ui.browser.sheets.PlaylistActionSheet
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import org.koin.compose.koinInject

@Serializable
object PlaylistScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<PlaylistRepository>()
    val browserPreferences = koinInject<BrowserPreferences>()
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    // ViewModel
    val viewModel: PlaylistViewModel = viewModel(
      factory = PlaylistViewModel.factory(context.applicationContext as android.app.Application),
    )

    val playlistsWithCount by viewModel.playlistsWithCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Filter playlists based on search query
    val filteredPlaylists = if (isSearching && searchQuery.isNotBlank()) {
      playlistsWithCount.filter { playlistWithCount ->
        playlistWithCount.playlist.name.contains(searchQuery, ignoreCase = true)
      }
    } else {
      playlistsWithCount
    }

    // Request focus when search is activated
    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    // Selection manager - use user-manageable playlists only
    val selectablePlaylists = remember(filteredPlaylists) {
      filteredPlaylists.filter { !it.isAutoPlaylist && it.playlist.id >= 0 }
    }
    val selectionManager = rememberSelectionManager(
      items = selectablePlaylists,
      getId = { it.playlist.id },
      onDeleteItems = { itemsToDelete, _ ->
        // Delete all items sequentially
        scope.launch {
          itemsToDelete.forEach { item ->
            repository.deletePlaylist(item.playlist)
          }
          viewModel.refresh()
        }
        Pair(itemsToDelete.size, 0)
      },
      onOperationComplete = { viewModel.refresh() },
    )

    // Use the remembered states
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()

    LaunchedEffect(Unit) {
      xyz.mpv.rex.ui.browser.MainScreen.scrollToTopRequest.collect { tabId ->
        if (tabId == "playlists") {
          scope.launch {
            if (mediaLayoutMode == MediaLayoutMode.GRID) {
              gridState.animateScrollToItem(0)
            } else {
              listState.animateScrollToItem(0)
            }
          }
        }
      }
    }
    val isRefreshing = remember { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Playlist action sheet state
    var showPlaylistActionSheet by remember { mutableStateOf(false) }
    var showAutoPlaylistsSheet by rememberSaveable { mutableStateOf(false) }

    // FAB visibility for scroll-based hiding
    val isFabVisible = remember { mutableStateOf(true) }
    val canNavigateBack = remember { backStack.size > 1 }

    // Predictive back: Intercept when in selection mode, searching, or when navigated from another screen
    BackHandler(enabled = selectionManager.isInSelectionMode || isSearching || canNavigateBack) {
      when {
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }

        selectionManager.isInSelectionMode -> selectionManager.clear()
        else -> backStack.navigateBack()
      }
    }

    // Track scroll for FAB visibility
    xyz.mpv.rex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = false,
      onExpandedChange = {},
    )

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
          if (isSearching) {
            // Search mode - show search bar
            SearchBar(
              inputField = {
                SearchBarDefaults.InputField(
                  query = searchQuery,
                  onQueryChange = { searchQuery = it },
                  onSearch = { },
                  expanded = false,
                  onExpandedChange = { },
                  placeholder = { Text(stringResource(R.string.search_playlists)) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.Search,
                      contentDescription = stringResource(R.string.search_empty_title),
                    )
                  },
                  trailingIcon = {
                    IconButton(
                      onClick = {
                        isSearching = false
                        searchQuery = ""
                      },
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.generic_cancel),
                      )
                    }
                  },
                  modifier = Modifier.focusRequester(focusRequester),
                )
              },
              expanded = false,
              onExpandedChange = { },
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              shape = RoundedCornerShape(28.dp),
              tonalElevation = 6.dp,
            ) {
              // Empty content for SearchBar
            }
          } else {
            BrowserTopBar(
              title = stringResource(R.string.playlists),
              scrollBehavior = scrollBehavior,
              isInSelectionMode = selectionManager.isInSelectionMode,
              selectedCount = selectionManager.selectedCount,
              totalCount = playlistsWithCount.size,
              onBackClick = if (canNavigateBack) {
                {
                  when {
                    isSearching -> {
                      isSearching = false
                      searchQuery = ""
                    }
                    selectionManager.isInSelectionMode -> selectionManager.clear()
                    else -> backStack.navigateBack()
                  }
                }
              } else null,
              onCancelSelection = { selectionManager.clear() },
              isSingleSelection = selectionManager.isSingleSelection,
              onSearchClick = { isSearching = true },
              onSettingsClick = {
                backStack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
              },
              onRenameClick = if (selectionManager.isSingleSelection) {
                { showRenameDialog = true }
              } else null,
              onDeleteClick = { showDeleteDialog = true },
              onSelectAll = { selectionManager.selectAll() },
              onInvertSelection = { selectionManager.invertSelection() },
              onDeselectAll = { selectionManager.clear() },
              normalOverflowActions = listOf(
                xyz.mpv.rex.ui.browser.components.SelectionOverflowAction(
                  icon = Icons.Filled.AutoAwesome,
                  label = stringResource(R.string.auto_playlists),
                  onClick = { showAutoPlaylistsSheet = true },
                ),
              ),
            )
          }
        },
        floatingActionButton = {
          val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
          if (!selectionManager.isInSelectionMode && isFabVisible.value) {
            ExtendedFloatingActionButton(
              onClick = { showPlaylistActionSheet = true },
              icon = { Icon(Icons.Filled.Add, contentDescription = null) },
              text = { Text(stringResource(R.string.create_playlist)) },
              modifier = Modifier.padding(bottom = navigationBarHeight)
            )
          }
        }
      ) { paddingValues ->
        if (isSearching && filteredPlaylists.isEmpty() && searchQuery.isNotBlank()) {
          // Show "no results" for search
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.Search,
              title = stringResource(R.string.no_playlists_found),
              message = stringResource(R.string.try_different_search_term),
            )
          }
        } else if (playlistsWithCount.isEmpty() && hasCompletedInitialLoad) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              EmptyState(
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                title = stringResource(R.string.no_playlists_yet),
                message = stringResource(R.string.no_playlists_yet_desc),
              )
            }
          }
        } else {
          PlaylistListContent(
            playlistsWithCount = filteredPlaylists,
            listState = listState,
            gridState = gridState,
            uiSettings = uiSettings,
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            selectionManager = selectionManager,
            onPlaylistClick = { playlistWithCount ->
              if (selectionManager.isInSelectionMode) {
                if (!playlistWithCount.isAutoPlaylist && playlistWithCount.playlist.id >= 0) {
                  selectionManager.toggle(playlistWithCount)
                }
              } else {
                backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
              }
            },
            onPlaylistLongClick = { playlistWithCount ->
              if (!playlistWithCount.isAutoPlaylist && playlistWithCount.playlist.id >= 0) {
                selectionManager.handleLongClick(playlistWithCount)
              }
            },
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
            isInSelectionMode = selectionManager.isInSelectionMode,
          )
        }
      }

      // Create playlist and M3U playlist dialogs moved to MainScreen

      // Playlist action sheets
      PlaylistActionSheet(
        isOpen = showPlaylistActionSheet,
        onDismiss = { showPlaylistActionSheet = false },
        repository = repository,
        context = context,
      )

      if (showRenameDialog && selectionManager.isSingleSelection) {
        val selectedPlaylist = selectionManager.getSelectedItems().firstOrNull()
        if (selectedPlaylist != null) {
          var playlistName by remember(selectedPlaylist) {
            mutableStateOf(
              TextFieldValue(
                text = selectedPlaylist.playlist.name,
                selection = TextRange(selectedPlaylist.playlist.name.length),
              ),
            )
          }
          val focusRequester = remember { FocusRequester() }

          LaunchedEffect(Unit) {
            focusRequester.requestFocus()
          }

          androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_playlist)) },
            text = {
              androidx.compose.material3.OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
              )
            },
            confirmButton = {
              androidx.compose.material3.TextButton(
                onClick = {
                  if (playlistName.text.isNotBlank()) {
                    scope.launch {
                      repository.updatePlaylist(selectedPlaylist.playlist.copy(name = playlistName.text.trim()))
                      showRenameDialog = false
                      selectionManager.clear()
                    }
                  }
                },
                enabled = playlistName.text.isNotBlank(),
              ) {
                Text(stringResource(R.string.rename))
              }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(
                onClick = { showRenameDialog = false },
              ) {
                Text(stringResource(R.string.generic_cancel))
              }
            },
          )
        }
      }

      if (showDeleteDialog) {
        DeleteConfirmationDialog(
          isOpen = true,
          onDismiss = { showDeleteDialog = false },
          onConfirm = {
            selectionManager.deleteSelected()
            showDeleteDialog = false
          },
          itemTypePluralRes = R.plurals.item_type_playlist_plural,
          itemCount = selectionManager.selectedCount,
          itemNames = selectionManager.getSelectedItems().map { it.playlist.name },
        )
      }

      AutoPlaylistsSheet(
        isOpen = showAutoPlaylistsSheet,
        onDismiss = { showAutoPlaylistsSheet = false },
      )
    }
  }

  @Composable
  private fun PlaylistListContent(
    playlistsWithCount: List<PlaylistWithCount>,
    listState: LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    uiSettings: UiSettings,
    isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
    onRefresh: suspend () -> Unit,
    selectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<PlaylistWithCount, Int>,
    onPlaylistClick: (PlaylistWithCount) -> Unit,
    onPlaylistLongClick: (PlaylistWithCount) -> Unit,
    modifier: Modifier = Modifier,
    isInSelectionMode: Boolean = false,
  ) {
    ContentSurfaceContainer(
      navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current,
      modifier = modifier,
    ) {
      UnifiedExplorerContent(
        items = playlistsWithCount,
        isLoading = false,
        uiSettings = uiSettings,
        isSelected = { selectionManager.isSelected(it) },
        onClick = onPlaylistClick,
        onLongClick = onPlaylistLongClick,
        onToggleSelection = { selectionManager.toggle(it) },
        emptyTitle = stringResource(R.string.no_playlists_found),
        emptyMessage = stringResource(R.string.empty_playlists_desc),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        isInSelectionMode = isInSelectionMode,
        listState = listState,
        gridState = gridState,
        useContentSurface = true,
      )
    }
  }
