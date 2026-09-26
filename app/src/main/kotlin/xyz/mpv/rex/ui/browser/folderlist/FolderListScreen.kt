package xyz.mpv.rex.ui.browser.folderlist

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ContentCopy
import xyz.mpv.rex.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.FolderViewMode
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.cards.FolderCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.FolderSortDialog
import xyz.mpv.rex.ui.browser.medialibrary.MediaLibraryContent
import xyz.mpv.rex.ui.browser.filesystem.FileSystemDirectoryScreen
import xyz.mpv.rex.ui.browser.filesystem.FileSystemBrowserRootScreen
import xyz.mpv.rex.ui.browser.components.BrowserBottomBar
import xyz.mpv.rex.ui.browser.dialogs.FileOperationProgressDialog
import xyz.mpv.rex.ui.browser.dialogs.FolderPickerDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.sheets.MarkAsBottomSheet
import xyz.mpv.rex.utils.media.CopyPasteOps
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet
import xyz.mpv.rex.ui.browser.sheets.PlayLinkSheet
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.browser.states.LoadingState
import xyz.mpv.rex.ui.browser.states.PermissionDeniedState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.permission.PermissionUtils
import xyz.mpv.rex.utils.sort.SortUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import org.koin.compose.koinInject
import java.io.File

@Serializable
object FolderListScreen : Screen {
  @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val folderViewMode by browserPreferences.folderViewMode.collectAsState()
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()

    when (folderViewMode) {
      FolderViewMode.FileManager -> FileSystemBrowserRootScreen.Content()
      FolderViewMode.AlbumView -> MediaStoreFolderListContent(
        folderSortType = folderSortType,
        folderSortOrder = folderSortOrder
      )
      FolderViewMode.MediaLibrary -> MediaLibraryContent()
    }
  }

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  private fun MediaStoreFolderListContent(
    folderSortType: FolderSortType,
    folderSortOrder: SortOrder,
  ) {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // ViewModels and preferences
    val viewModel: FolderListViewModel = viewModel(
      factory = FolderListViewModel.factory(context.applicationContext as android.app.Application)
    )
    val browserPreferences = koinInject<BrowserPreferences>()
    val gesturePreferences = koinInject<GesturePreferences>()
    val foldersPreferences = koinInject<FoldersPreferences>()
    val advancedPreferences = koinInject<xyz.mpv.rex.preferences.AdvancedPreferences>()

    // State collection
    val videoFolders by viewModel.videoFolders.collectAsState()
    val foldersWithNewCount by viewModel.foldersWithNewCount.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val recentlyPlayedPaths by viewModel.recentlyPlayedPaths.collectAsState()
    val recentlyPlayedFilePaths by viewModel.recentlyPlayedFilePaths.collectAsState()
    val playedFolderPaths by viewModel.playedFolderPaths.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()
    val foldersWereDeleted by viewModel.foldersWereDeleted.collectAsState()

    // Preferences
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
    val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
    val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
    val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
    val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()

    // UI state - use standalone states to avoid scroll issues with predictive back gesture
    val rememberedListIndex = rememberSaveable { mutableIntStateOf(0) }
    val rememberedListOffset = rememberSaveable { mutableIntStateOf(0) }
    val rememberedGridIndex = rememberSaveable { mutableIntStateOf(0) }
    val rememberedGridOffset = rememberSaveable { mutableIntStateOf(0) }
    val hasListAutoScrolled = rememberSaveable(inputs = arrayOf(recentlyPlayedFilePath ?: "")) { mutableStateOf(false) }
    val hasGridAutoScrolled = rememberSaveable(inputs = arrayOf(recentlyPlayedFilePath ?: "")) { mutableStateOf(false) }

    // Sorting and filtering
    val sortedFolders = remember(videoFolders, folderSortType, folderSortOrder, showAudioFiles) {
      SortUtils.sortFolders(videoFolders, folderSortType, folderSortOrder, showAudioFiles)
    }

    val initialListIndex = if (rememberedListIndex.intValue > 0) {
      rememberedListIndex.intValue
    } else if (autoScrollToLastPlayed && !hasListAutoScrolled.value && recentlyPlayedFilePath != null && sortedFolders.isNotEmpty()) {
      var foundIndex = 0
      val lastPlayedParentPath = java.io.File(recentlyPlayedFilePath!!).parent ?: "/"
      for (i in sortedFolders.indices) {
        if (sortedFolders[i].path == lastPlayedParentPath) {
          foundIndex = i
          break
        }
      }
      foundIndex
    } else {
      rememberedListIndex.intValue
    }

    val initialGridIndex = if (rememberedGridIndex.intValue > 0) {
      rememberedGridIndex.intValue
    } else if (autoScrollToLastPlayed && !hasGridAutoScrolled.value && recentlyPlayedFilePath != null && sortedFolders.isNotEmpty()) {
      var foundIndex = 0
      val lastPlayedParentPath = java.io.File(recentlyPlayedFilePath!!).parent ?: "/"
      for (i in sortedFolders.indices) {
        if (sortedFolders[i].path == lastPlayedParentPath) {
          foundIndex = i
          break
        }
      }
      foundIndex
    } else {
      rememberedGridIndex.intValue
    }

    val listState = rememberLazyListState(
      initialFirstVisibleItemIndex = initialListIndex,
      initialFirstVisibleItemScrollOffset = rememberedListOffset.intValue
    )
    val gridState = rememberLazyGridState(
      initialFirstVisibleItemIndex = initialGridIndex,
      initialFirstVisibleItemScrollOffset = rememberedGridOffset.intValue
    )

    val isInitialSortLoad = remember { mutableStateOf(true) }
    LaunchedEffect(folderSortType.name, folderSortOrder.name) {
      if (isInitialSortLoad.value) {
        isInitialSortLoad.value = false
        return@LaunchedEffect
      }
      rememberedListIndex.intValue = 0
      rememberedGridIndex.intValue = 0
      if (mediaLayoutMode == MediaLayoutMode.GRID) {
        gridState.scrollToItem(0)
      } else {
        listState.scrollToItem(0)
      }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
      rememberedListIndex.intValue = listState.firstVisibleItemIndex
      rememberedListOffset.intValue = listState.firstVisibleItemScrollOffset
      hasListAutoScrolled.value = true
    }

    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
      rememberedGridIndex.intValue = gridState.firstVisibleItemIndex
      rememberedGridOffset.intValue = gridState.firstVisibleItemScrollOffset
      hasGridAutoScrolled.value = true
    }

    LaunchedEffect(Unit) {
      xyz.mpv.rex.ui.browser.MainScreen.scrollToTopRequest.collect { tabId ->
        if (tabId == "home") {
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

    val navigationBarHeight = LocalNavigationBarHeight.current
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }
    var showMarkAsSheet by remember { mutableStateOf(false) }
    val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
    val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
    val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
    var renameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var folderSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }
    val operationProgress by CopyPasteOps.operationProgress.collectAsState()
    // FAB state
    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      uri?.let {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        MediaUtils.playFile(it.toString(), context, "open_file")
      }
    }

    val filteredFolders = sortedFolders

    // Selection manager
    val selectionManager = rememberSelectionManager<VideoFolder, String>(
      items = sortedFolders,
      getId = { it.bucketId },
      onDeleteItems = { folders, _ ->
        viewModel.deleteFolders(folders)
      },
      onOperationComplete = { viewModel.refresh() },
    )

    val treePickerLauncher = rememberLauncherForActivityResult(
      contract = OpenDocumentTreeContract(),
    ) { uri ->
      if (uri == null || operationType.value == null) return@rememberLauncherForActivityResult
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
      progressDialogOpen.value = true
      coroutineScope.launch {
        val selectedFolders = selectionManager.getSelectedItems()
        val selectedVideos = selectedFolders.flatMap { folder ->
          MediaFileRepository.getVideosForBuckets(context, setOf(folder.bucketId))
        }
        if (selectedVideos.isNotEmpty()) {
          when (operationType.value) {
            is CopyPasteOps.OperationType.Copy -> CopyPasteOps.copyFilesToTreeUri(context, selectedVideos, uri)
            is CopyPasteOps.OperationType.Move -> CopyPasteOps.moveFilesToTreeUri(context, selectedVideos, uri)
            else -> {}
          }
        }
      }
    }

    // Permissions
    val permissionState = PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() },
    )

    // Update MainScreen about permission state
    LaunchedEffect(permissionState.status) {
      xyz.mpv.rex.ui.browser.MainScreen.updatePermissionState(
        isDenied = permissionState.status is PermissionStatus.Denied
      )
    }

    // Lifecycle observer for refresh
    DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          viewModel.loadData()
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Optimized back handler for immediate response
    val isListScrolled = remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val isGridScrolled = remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 } }
    val isScrolled = if (mediaLayoutMode == MediaLayoutMode.GRID) isGridScrolled.value else isListScrolled.value
    
    val shouldHandleBack = selectionManager.isInSelectionMode || isFabExpanded.value || isScrolled
    androidx.activity.compose.BackHandler(enabled = shouldHandleBack) {
      when {
        isFabExpanded.value -> isFabExpanded.value = false
        selectionManager.isInSelectionMode -> selectionManager.clear()
        isScrolled -> {
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

    // FAB scroll tracking
    xyz.mpv.rex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = isFabExpanded.value,
      onExpandedChange = { isFabExpanded.value = it },
    )

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = stringResource(xyz.mpv.rex.R.string.app_name),
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = videoFolders.size,
          onBackClick = null,
          isHomeScreen = true,
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = { backstack.add(xyz.mpv.rex.ui.browser.search.SearchScreen()) },
            onSettingsClick = {
              backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
            },
            onRenameClick = null,
            isSingleSelection = selectionManager.isSingleSelection,
            onInfoClick = {
              val selected = selectionManager.getSelectedItems()
              folderSelectionInfo = Triple(
                selected.size,
                selected.sumOf { it.activeSize(showAudioFiles) },
                selected.sumOf { it.activeDuration(showAudioFiles) },
              )
            },
            onPlayClick = {
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = xyz.mpv.rex.repository.MediaFileRepository
                  .getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  if (allVideos.size == 1) {
                    MediaUtils.playFile(allVideos.first(), context)
                  } else {
                    MediaUtils.playPlaylist(allVideos, 0, context)
                  }
                  selectionManager.clear()
                }
              }
            },
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
            selectionOverflowActions = listOf(
              SelectionOverflowAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.generic_share),
                onClick = {
                  coroutineScope.launch {
                    val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                    val allVideos = xyz.mpv.rex.repository.MediaFileRepository
                      .getVideosForBuckets(context, selectedIds)
                    if (allVideos.isNotEmpty()) {
                      MediaUtils.shareVideos(context, allVideos)
                    }
                  }
                },
              ),
              SelectionOverflowAction(
                icon = Icons.Filled.Block,
                label = stringResource(R.string.pref_folders_blacklist),
                onClick = {
                  coroutineScope.launch {
                    val selectedFolders = selectionManager.getSelectedItems()
                    val blacklistedFolders = foldersPreferences.blacklistedFolders.get().toMutableSet()
                    selectedFolders.forEach { folder -> blacklistedFolders.add(folder.path) }
                    foldersPreferences.blacklistedFolders.set(blacklistedFolders)
                    selectionManager.clear()
                    viewModel.refresh()
                    android.widget.Toast.makeText(
                      context,
                      context.getString(xyz.mpv.rex.R.string.pref_folders_blacklisted),
                      android.widget.Toast.LENGTH_SHORT,
                    ).show()
                  }
                },
              ),
              SelectionOverflowAction(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.copy_folder_path),
                onClick = {
                  val selectedFolders = selectionManager.getSelectedItems()
                  if (selectedFolders.isNotEmpty()) {
                    val pathsString = selectedFolders.joinToString("\n") { it.path }
                    clipboardManager.setText(AnnotatedString(pathsString))
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                  }
                }
              ),
            ),
          )
        },
      floatingActionButton = {
        FloatingActionButtonMenu(
          modifier = Modifier.padding(bottom = navigationBarHeight + 8.dp),
          expanded = isFabExpanded.value,
          button = {
            TooltipBox(
              positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                if (isFabExpanded.value) {
                  TooltipAnchorPosition.Start
                } else {
                  TooltipAnchorPosition.Above
                }
              ),
              tooltip = { PlainTooltip { Text(stringResource(R.string.toggle_menu)) } },
              state = rememberTooltipState(),
            ) {
            Box(
              modifier = Modifier.animateFloatingActionButton(
                visible = !selectionManager.isInSelectionMode && isFabVisible.value && !xyz.mpv.rex.ui.browser.MainScreen.getPermissionDeniedState(),
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

              // Overlay to capture clicks and long-presses without internal interference
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
                            val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                            val lastPlayed = recentlyPlayedVideos.firstOrNull()
                            if (lastPlayed != null) {
                              MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                            } else {
                              Toast.makeText(context, context.getString(R.string.no_recently_played_videos), Toast.LENGTH_SHORT).show()
                            }
                          }
                        }
                      },
                      onLongPress = {
                        if (!isFabExpanded.value) {
                          isFabExpanded.value = true
                        }
                      }
                    )
                  }
              )
            }
            }
          },
        ) {
          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              filePicker.launch(arrayOf("video/*"))
            },
            icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
            text = { Text(text = "Open File") },
          )

          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              coroutineScope.launch {
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                val lastPlayed = recentlyPlayedVideos.firstOrNull()
                if (lastPlayed != null) {
                  MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                }
              }
            },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            text = { Text(text = "Recently Played") },
          )

          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              showLinkDialog.value = true
            },
            icon = { Icon(Icons.Filled.Link, contentDescription = null) },
            text = { Text(text = "Open Link") },
          )
        }
      },
    ) { padding ->
      Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
        when (permissionState.status) {
          PermissionStatus.Granted -> {
            FolderListContent(
              folders = filteredFolders,
              foldersWithNewCount = foldersWithNewCount,
              autoScrollToLastPlayed = autoScrollToLastPlayed,
              uiSettings = uiSettings,
              listState = listState,
              gridState = gridState,
              isRefreshing = isRefreshing,
              isLoading = isLoading,
              hasCompletedInitialLoad = hasCompletedInitialLoad,
              foldersWereDeleted = foldersWereDeleted,
              recentlyPlayedFilePath = recentlyPlayedFilePath,
              recentlyPlayedFilePaths = recentlyPlayedFilePaths,
              recentlyPlayedPaths = recentlyPlayedPaths,
              playedFolderPaths = playedFolderPaths,
              onRefresh = { viewModel.refresh() },
              mediaLayoutMode = mediaLayoutMode,
              folderGridColumns = folderGridColumns,
              tapThumbnailToSelect = tapThumbnailToSelect,
              navigationBarHeight = navigationBarHeight,
              selectionManager = selectionManager,
              onFolderClick = { folder ->
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(folder)
                } else {
                  backstack.add(xyz.mpv.rex.ui.browser.videolist.VideoListScreen(folder.bucketId, folder.name))
                }
              },
              onFolderLongClick = { folder ->
                selectionManager.handleLongClick(folder)
              },
              scrollTriggerKey = "${folderSortType.name}:${folderSortOrder.name}",
            )
          }

          is PermissionStatus.Denied -> {
            PermissionDeniedState(
              onRequestPermission = { permissionState.launchPermissionRequest() },
              modifier = Modifier,
            )
          }
        }
      }

      // Dialogs
      PlayLinkSheet(
        isOpen = showLinkDialog.value,
        onDismiss = { showLinkDialog.value = false },
        onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
      )

      FolderSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = folderSortType,
        sortOrder = folderSortOrder,
        onSortTypeChange = { 
          browserPreferences.folderSortType.set(it)
        },
        onSortOrderChange = { 
          browserPreferences.folderSortOrder.set(it)
        },
      )

      Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
          visible = selectionManager.isInSelectionMode,
          enter = slideInVertically(
            animationSpec = tween(durationMillis = 200),
            initialOffsetY = { fullHeight -> fullHeight },
          ),
          exit = slideOutVertically(
            animationSpec = tween(durationMillis = 200),
            targetOffsetY = { fullHeight -> fullHeight },
          ),
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          BrowserBottomBar(
            isSelectionMode = true,
            onCopyClick = {
              operationType.value = CopyPasteOps.OperationType.Copy
              if (CopyPasteOps.canUseDirectFileOperations()) {
                folderPickerOpen.value = true
              } else {
                treePickerLauncher.launch(null)
              }
            },
            onMoveClick = {
              operationType.value = CopyPasteOps.OperationType.Move
              if (CopyPasteOps.canUseDirectFileOperations()) {
                folderPickerOpen.value = true
              } else {
                treePickerLauncher.launch(null)
              }
            },
            onRenameClick = { renameDialogOpen = true },
            onDeleteClick = { deleteDialogOpen.value = true },
            onAddToPlaylistClick = { },
            showCopy = true,
            showMove = true,
            showRename = selectionManager.isSingleSelection,
            showDelete = selectionManager.selectedCount <= 1,
            showAddToPlaylist = false,
            onMarkAsClick = { showMarkAsSheet = true }
          )
        }
      }

      if (showMarkAsSheet) {
        MarkAsBottomSheet(
          onDismiss = { showMarkAsSheet = false },
          onMarkAs = { state ->
            coroutineScope.launch {
              val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
              val videos = MediaFileRepository.getVideosForBuckets(context, selectedIds)
              videos.forEach { video ->
                RecentlyPlayedOps.markAs(
                  filePath = video.path,
                  fileName = video.displayName,
                  duration = video.duration,
                  state = state,
                )
              }
            }
          },
        )
      }

      FolderPickerDialog(
        isOpen = folderPickerOpen.value,
        onDismiss = { folderPickerOpen.value = false },
        onFolderSelected = { destinationPath ->
          folderPickerOpen.value = false
          val op = operationType.value
          if (op != null) {
            coroutineScope.launch {
              val selectedFolders = selectionManager.getSelectedItems()
              if (selectedFolders.isNotEmpty()) {
                when (op) {
                  is CopyPasteOps.OperationType.Move -> {
                    val needFallback = mutableListOf<VideoFolder>()
                    for (folder in selectedFolders) {
                      val dst = File(destinationPath, folder.name)
                      if (!File(folder.path).renameTo(dst)) needFallback.add(folder)
                    }
                    if (needFallback.isNotEmpty()) {
                      progressDialogOpen.value = true
                      for (folder in needFallback) {
                        val videos = MediaFileRepository.getVideosForBuckets(context, setOf(folder.bucketId))
                        if (videos.isNotEmpty()) {
                          val subDest = File(destinationPath, folder.name).also { it.mkdirs() }.absolutePath
                          CopyPasteOps.moveFiles(context, videos, subDest)
                        }
                      }
                    } else {
                      selectionManager.clear()
                      viewModel.refresh()
                    }
                  }
                  is CopyPasteOps.OperationType.Copy -> {
                    progressDialogOpen.value = true
                    for (folder in selectedFolders) {
                      val videos = MediaFileRepository.getVideosForBuckets(context, setOf(folder.bucketId))
                      if (videos.isNotEmpty()) {
                        val subDest = File(destinationPath, folder.name).also { it.mkdirs() }.absolutePath
                        CopyPasteOps.copyFiles(context, videos, subDest)
                      }
                    }
                  }
                }
              }
            }
          }
        },
      )

      if (operationType.value != null) {
        FileOperationProgressDialog(
          isOpen = progressDialogOpen.value,
          operationType = operationType.value!!,
          progress = operationProgress,
          onCancel = { CopyPasteOps.cancelOperation() },
          onDismiss = {
            progressDialogOpen.value = false
            operationType.value = null
            selectionManager.clear()
            viewModel.refresh()
          },
        )
      }

      if (renameDialogOpen && selectionManager.isSingleSelection) {
        val folder = selectionManager.getSelectedItems().firstOrNull()
        if (folder != null) {
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen = false },
            onConfirm = { newName ->
              renameDialogOpen = false
              coroutineScope.launch {
                val ok = viewModel.renameFolder(folder, newName)
                if (!ok) {
                  android.widget.Toast.makeText(context, context.getString(R.string.rename_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
                selectionManager.clear()
                viewModel.refresh()
              }
            },
            currentName = folder.name,
            itemTypeRes = R.string.item_type_folder,
          )
        }
      }

      DeleteConfirmationDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemTypePluralRes = R.plurals.item_type_folder_plural,
        itemCount = selectionManager.selectedCount,
        itemNames = selectionManager.getSelectedItems().map { it.name },
      )

      folderSelectionInfo?.let { (count, bytes, duration) ->
        MultiSelectionInfoSheet(
          count = count,
          totalBytes = bytes,
          totalDurationMs = duration,
          onDismiss = { folderSelectionInfo = null },
          unit = "folder",
        )
      }
    }
  }
}

@Composable
private fun FolderListContent(
  folders: List<VideoFolder>,
  foldersWithNewCount: List<xyz.mpv.rex.ui.browser.folderlist.FolderWithNewCount>,
  uiSettings: UiSettings,
  recentlyPlayedFilePath: String?,
  recentlyPlayedPaths: Set<String> = emptySet(),
  recentlyPlayedFilePaths: Set<String> = emptySet(),
  playedFolderPaths: Set<String>,
  isLoading: Boolean,
  hasCompletedInitialLoad: Boolean,
  foldersWereDeleted: Boolean,
  mediaLayoutMode: MediaLayoutMode,
  folderGridColumns: Int,
  tapThumbnailToSelect: Boolean,
  navigationBarHeight: androidx.compose.ui.unit.Dp,
  listState: LazyListState,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  selectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  autoScrollToLastPlayed: Boolean,
  onRefresh: suspend () -> Unit,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
  scrollTriggerKey: Any? = null,
) {
  val showLoading = isLoading && !hasCompletedInitialLoad

  Box(modifier = Modifier.fillMaxSize()) {
    UnifiedExplorerContent(
      items = folders,
      isLoading = showLoading,
      uiSettings = uiSettings,
      isSelected = { selectionManager.isSelected(it) },
      onClick = { onFolderClick(it) },
      onLongClick = { onFolderLongClick(it) },
      onToggleSelection = { selectionManager.toggle(it) },
      emptyTitle = stringResource(R.string.no_video_folders_found),
      emptyMessage = stringResource(R.string.no_video_folders_found_desc),
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      isInSelectionMode = selectionManager.isInSelectionMode,
      recentlyPlayedFilePath = recentlyPlayedFilePath,
      recentlyPlayedFilePaths = recentlyPlayedFilePaths,
      recentlyPlayedPaths = recentlyPlayedPaths,
      playedFolderPaths = playedFolderPaths,
      autoScrollToLastPlayed = autoScrollToLastPlayed,
      listState = listState,
      gridState = gridState,
      scrollTriggerKey = scrollTriggerKey,
      gridColumns = folderGridColumns,
    )
  }
}
