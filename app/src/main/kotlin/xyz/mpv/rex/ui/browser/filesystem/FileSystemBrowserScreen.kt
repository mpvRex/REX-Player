package xyz.mpv.rex.ui.browser.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import xyz.mpv.rex.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.FolderCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserBottomBar
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.FileOperationProgressDialog
import xyz.mpv.rex.ui.browser.dialogs.FolderPickerDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.dialogs.FileSystemSortDialog
import androidx.compose.material.icons.filled.VideoLibrary
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.sheets.MarkAsBottomSheet
import xyz.mpv.rex.ui.browser.sheets.PlayLinkSheet
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.browser.states.PermissionDeniedState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.CopyPasteOps
import xyz.mpv.rex.utils.media.MediaUtils
import androidx.compose.ui.text.style.TextOverflow
import xyz.mpv.rex.utils.permission.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.fillMaxHeight
import xyz.mpv.rex.ui.browser.components.FastScrollbar
import org.koin.compose.koinInject

/**
 * Root File System Browser screen - shows storage volumes
 */
@Serializable
object FileSystemBrowserRootScreen : xyz.mpv.rex.presentation.Screen {
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    FileSystemBrowserScreen(path = null)
  }
}

/**
 * File System Directory screen - shows contents of a specific directory
 */
@Serializable
data class FileSystemDirectoryScreen(
  val path: String,
) : xyz.mpv.rex.presentation.Screen {
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    FileSystemBrowserScreen(path = path)
  }
}

/**
 * File System Browser screen - browses directories and shows both folders and videos
 * @param path The directory path to browse, or null for storage roots
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileSystemBrowserScreen(path: String? = null) {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val clipboardManager = LocalClipboardManager.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val playerPreferences = koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  // ViewModel - use path parameter if provided, otherwise show roots
  val viewModel: FileSystemBrowserViewModel = viewModel(
    key = "FileSystemBrowser_${path ?: "root"}",
    factory = FileSystemBrowserViewModel.factory(
      context.applicationContext as android.app.Application,
      path,
    ),
  )

  // State collection
  val currentPath by viewModel.currentPath.collectAsState()
  val items by viewModel.items.collectAsState()
  val videoFilesWithPlayback by viewModel.videoFilesWithPlayback.collectAsState()
  val newVideoIds by viewModel.newVideoIds.collectAsState()
  val watchedVideoIds by viewModel.watchedVideoIds.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val uiSettings by viewModel.uiSettings.collectAsState()
  val error by viewModel.error.collectAsState()
  val isAtRoot by viewModel.isAtRoot.collectAsState()
  val breadcrumbs by viewModel.breadcrumbs.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val itemsWereDeletedOrMoved by viewModel.itemsWereDeletedOrMoved.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
  val recentlyPlayedPaths by viewModel.recentlyPlayedPaths.collectAsState()
  val recentlyPlayedFilePaths by viewModel.recentlyPlayedFilePaths.collectAsState()
  val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()

  // Use standalone local states instead of CompositionLocal to avoid scroll issues with predictive back gesture
  val rememberedIndex = rememberSaveable { mutableIntStateOf(0) }
  val rememberedOffset = rememberSaveable { mutableIntStateOf(0) }
  val hasAutoScrolled = rememberSaveable(inputs = arrayOf(recentlyPlayedFilePath ?: "")) { mutableStateOf(false) }

  val initialIndex = if (rememberedIndex.intValue > 0) {
    rememberedIndex.intValue
  } else if (autoScrollToLastPlayed && !hasAutoScrolled.value && recentlyPlayedFilePath != null && items.isNotEmpty()) {
    var foundIndex = 0
    for (i in items.indices) {
      val item = items[i]
      if (item is FileSystemItem.VideoFile && item.video.path == recentlyPlayedFilePath) {
        foundIndex = i
        break
      } else if (item is FileSystemItem.Folder && recentlyPlayedFilePath!!.startsWith(item.path + "/")) {
        // If the last played file is inside this folder, scroll to the folder
        foundIndex = i
        break
      }
    }
    foundIndex
  } else {
    rememberedIndex.intValue
  }

  val listState = rememberLazyListState(
    initialFirstVisibleItemIndex = initialIndex,
    initialFirstVisibleItemScrollOffset = rememberedOffset.intValue
  )

  val sortType by browserPreferences.folderSortType.collectAsState()
  val sortOrder by browserPreferences.folderSortOrder.collectAsState()

  val isInitialSortLoad = remember { mutableStateOf(true) }
  LaunchedEffect(sortType.name, sortOrder.name) {
    if (isInitialSortLoad.value) {
      isInitialSortLoad.value = false
      return@LaunchedEffect
    }
    rememberedIndex.intValue = 0
    listState.scrollToItem(0)
  }

  LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
    rememberedIndex.intValue = listState.firstVisibleItemIndex
    rememberedOffset.intValue = listState.firstVisibleItemScrollOffset
    hasAutoScrolled.value = true
  }
  
  // UI state
  val isRefreshing = remember { mutableStateOf(false) }
  val showLinkDialog = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }

  // FAB visibility for scroll-based hiding
  val isFabVisible = remember { mutableStateOf(true) }
  val isFabExpanded = remember { mutableStateOf(false) }
  
  // Selection info state
  var mediaInfoUri by remember { mutableStateOf<Uri?>(null) }
  var multiSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }
  var multiSelectionUnit by remember { mutableStateOf("file") }
  
  // Get navigation bar height from MainScreen
  val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

  // Copy/Move state
  val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
  val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
  val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
  val operationProgress by CopyPasteOps.operationProgress.collectAsState()

  // Bottom bar visibility state
  var showFloatingBottomBar by remember { mutableStateOf(false) }
  var showMarkAsSheet by remember { mutableStateOf(false) }
  var showWebShareSheet by remember { mutableStateOf(false) }

  // Animation duration for responsive slide animations
  val animationDuration = 200

  // Selection managers - separate for folders and videos
  val folders = items.filterIsInstance<FileSystemItem.Folder>()
  val videos = items.filterIsInstance<FileSystemItem.VideoFile>().map { it.video }

  val folderSelectionManager = rememberSelectionManager(
    items = folders,
    getId = { it.path },
    onDeleteItems = { foldersToDelete, _ ->
      viewModel.deleteFolders(foldersToDelete)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  val videoSelectionManager = rememberSelectionManager(
    items = videos,
    getId = { it.id },
    onDeleteItems = { videosToDelete, _ ->
      viewModel.deleteVideos(videosToDelete)
    },
    onRenameItem = { video, newName ->
      viewModel.renameVideo(video, newName)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  // Determine which selection manager is active
  val isInSelectionMode = folderSelectionManager.isInSelectionMode || videoSelectionManager.isInSelectionMode
  val selectedCount = folderSelectionManager.selectedCount + videoSelectionManager.selectedCount
  val totalCount = folders.size + videos.size
  val isMixedSelection = folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode

  // Show/hide floating bar based on selection state
  LaunchedEffect(isInSelectionMode) {
    showFloatingBottomBar = isInSelectionMode
  }

  // Permissions
  val permissionState = PermissionUtils.handleStoragePermission(
    onPermissionGranted = { viewModel.refresh() },
  )

  // Sync selection state to MainScreen (bottom nav stays visible — floating bar floats above it)
  LaunchedEffect(isInSelectionMode, isMixedSelection, videoSelectionManager.isInSelectionMode, permissionState.status) {
    if (isAtRoot) {
      try {
        val mainScreenObj = xyz.mpv.rex.ui.browser.MainScreen
        val onlyVideosSelected = videoSelectionManager.isInSelectionMode && !folderSelectionManager.isInSelectionMode
        mainScreenObj.updateSelectionState(
          isInSelectionMode = isInSelectionMode,
          isOnlyVideosSelected = onlyVideosSelected,
          selectionManager = if (onlyVideosSelected) videoSelectionManager else null
        )
        mainScreenObj.updatePermissionState(
          isDenied = permissionState.status is PermissionStatus.Denied
        )
      } catch (e: Exception) {
        Log.e("FileSystemBrowserScreen", "Failed to update MainScreen state", e)
      }
    }
  }

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

  // Tree picker for Play Store-safe copy/move destinations
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
      if (folderSelectionManager.isInSelectionMode) {
        val selectedVideos = folderSelectionManager.getSelectedItems()
          .flatMap { collectVideosRecursively(context, it.path) }
        if (selectedVideos.isNotEmpty()) {
          when (operationType.value) {
            is CopyPasteOps.OperationType.Copy -> CopyPasteOps.copyFilesToTreeUri(context, selectedVideos, uri)
            is CopyPasteOps.OperationType.Move -> CopyPasteOps.moveFilesToTreeUri(context, selectedVideos, uri)
            else -> {}
          }
        }
      } else {
        val selectedVideos = videoSelectionManager.getSelectedItems()
        if (selectedVideos.isNotEmpty()) {
          when (operationType.value) {
            is CopyPasteOps.OperationType.Copy -> CopyPasteOps.copyFilesToTreeUri(context, selectedVideos, uri)
            is CopyPasteOps.OperationType.Move -> CopyPasteOps.moveFilesToTreeUri(context, selectedVideos, uri)
            else -> {}
          }
        }
      }
    }
  }

  // Listen for lifecycle resume events
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(silent = true)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Optimized predictive back handler for immediate response
  val shouldHandleBack = isInSelectionMode || isFabExpanded.value
  BackHandler(enabled = shouldHandleBack) {
    when {
      isFabExpanded.value -> isFabExpanded.value = false
      isInSelectionMode -> {
        folderSelectionManager.clear()
        videoSelectionManager.clear()
      }
    }
  }

  // Track scroll for FAB visibility
  xyz.mpv.rex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
    listState = listState,
    gridState = null,
    isFabVisible = isFabVisible,
    expanded = isFabExpanded.value,
    onExpandedChange = { isFabExpanded.value = it },
  )

  // Main content
  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      topBar = {
        BrowserTopBar(
          title = if (isAtRoot) {
            stringResource(xyz.mpv.rex.R.string.app_name)
          } else {
            breadcrumbs.lastOrNull()?.name ?: stringResource(xyz.mpv.rex.R.string.tree_view)
          },
          isInSelectionMode = isInSelectionMode,
          selectedCount = selectedCount,
          totalCount = totalCount,
          isHomeScreen = isAtRoot,
          onBackClick = if (isAtRoot) {
            null
          } else {
            { backstack.removeLastOrNull() }
          },
          onCancelSelection = {
            folderSelectionManager.clear()
            videoSelectionManager.clear()
          },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = {
            backstack.add(
              xyz.mpv.rex.ui.browser.search.SearchScreen(
                initialPath = if (isAtRoot) null else currentPath,
                initialFolderName = breadcrumbs.lastOrNull()?.name,
              )
            )
          },
          onSettingsClick = {
            backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
            onRenameClick = if (videoSelectionManager.isSingleSelection && !isMixedSelection) {
              null
            } else {
              null
            },
            isSingleSelection = videoSelectionManager.isSingleSelection && !isMixedSelection,
            onInfoClick = when {
              videoSelectionManager.isInSelectionMode && !folderSelectionManager.isInSelectionMode -> {
                {
                  val selected = videoSelectionManager.getSelectedItems()
                  if (videoSelectionManager.isSingleSelection) {
                    mediaInfoUri = selected.firstOrNull()?.uri
                  } else {
                    multiSelectionUnit = "file"
                    multiSelectionInfo = Triple(
                      selected.size,
                      selected.sumOf { it.size },
                      selected.sumOf { it.duration },
                    )
                  }
                }
              }
              folderSelectionManager.isInSelectionMode && !videoSelectionManager.isInSelectionMode -> {
                {
                  val selected = folderSelectionManager.getSelectedItems()
                  multiSelectionUnit = "folder"
                  multiSelectionInfo = Triple(
                    selected.size,
                    selected.sumOf { it.activeSize(showAudioFiles) },
                    selected.sumOf { it.activeDuration(showAudioFiles) },
                  )
                }
              }
              isMixedSelection -> {
                {
                  val selectedVideos = videoSelectionManager.getSelectedItems()
                  val selectedFolders = folderSelectionManager.getSelectedItems()
                  multiSelectionUnit = "item"
                  multiSelectionInfo = Triple(
                    selectedVideos.size + selectedFolders.size,
                    selectedVideos.sumOf { it.size } + selectedFolders.sumOf { it.activeSize(showAudioFiles) },
                    selectedVideos.sumOf { it.duration } + selectedFolders.sumOf { it.activeDuration(showAudioFiles) },
                  )
                }
              }
              else -> null
            },
            onPlayClick = {
              when {
                // Mixed selection: play videos from both selected videos and selected folders
                isMixedSelection -> {
                  coroutineScope.launch {
                    val selectedVideos = videoSelectionManager.getSelectedItems()
                    val selectedFolders = folderSelectionManager.getSelectedItems()

                    // Get all videos recursively from selected folders
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }

                    // Combine and play all videos as playlist
                    val allVideos = (selectedVideos + videosFromFolders).distinctBy { it.id }
                    if (allVideos.isNotEmpty()) {
                      playVideosAsPlaylist(context, allVideos)
                    }

                    // Clear selections
                    folderSelectionManager.clear()
                    videoSelectionManager.clear()
                  }
                }
                // Folders only: play all videos from selected folders as playlist
                folderSelectionManager.isInSelectionMode -> {
                  coroutineScope.launch {
                    val selectedFolders = folderSelectionManager.getSelectedItems()
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }
                    if (videosFromFolders.isNotEmpty()) {
                      playVideosAsPlaylist(context, videosFromFolders)
                    }

                    // Clear selection
                    folderSelectionManager.clear()
                  }
                }
                // Videos only: use existing functionality
                videoSelectionManager.isInSelectionMode -> {
                  videoSelectionManager.playSelected()
                }
              }
            },
            onSelectAll = {
              folderSelectionManager.selectAll()
              videoSelectionManager.selectAll()
            },
            onInvertSelection = {
              folderSelectionManager.invertSelection()
              videoSelectionManager.invertSelection()
            },
            onDeselectAll = {
              folderSelectionManager.clear()
              videoSelectionManager.clear()
            },
            selectionOverflowActions = buildList {
              add(SelectionOverflowAction(
                icon = Icons.Filled.PictureInPictureAlt,
                label = stringResource(R.string.open_with_mini_player),
                onClick = {
                  coroutineScope.launch {
                    val selectedVideos = videoSelectionManager.getSelectedItems()
                    val videosFromFolders = folderSelectionManager.getSelectedItems().flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }
                    val allVideos = (selectedVideos + videosFromFolders).distinctBy { it.id }
                    if (allVideos.isNotEmpty()) MediaUtils.playInMiniPlayer(allVideos)
                    folderSelectionManager.clear()
                    videoSelectionManager.clear()
                  }
                },
              ))
              add(SelectionOverflowAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.generic_share),
                onClick = {
                  when {
                    isMixedSelection -> {
                      coroutineScope.launch {
                        val selectedVideos = videoSelectionManager.getSelectedItems()
                        val selectedFolders = folderSelectionManager.getSelectedItems()
                        val videosFromFolders = selectedFolders.flatMap { folder ->
                          collectVideosRecursively(context, folder.path)
                        }
                        val allVideos = (selectedVideos + videosFromFolders).distinctBy { it.id }
                        if (allVideos.isNotEmpty()) MediaUtils.shareVideos(context, allVideos)
                      }
                    }
                    folderSelectionManager.isInSelectionMode -> {
                      coroutineScope.launch {
                        val selectedFolders = folderSelectionManager.getSelectedItems()
                        val videosFromFolders = selectedFolders.flatMap { folder ->
                          collectVideosRecursively(context, folder.path)
                        }
                        if (videosFromFolders.isNotEmpty()) MediaUtils.shareVideos(context, videosFromFolders)
                      }
                    }
                    videoSelectionManager.isInSelectionMode -> {
                      videoSelectionManager.shareSelected()
                    }
                  }
                },
              ))
              add(SelectionOverflowAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.web_share_title),
                onClick = { showWebShareSheet = true },
              ))
              if (folderSelectionManager.isInSelectionMode && !videoSelectionManager.isInSelectionMode) {
                add(SelectionOverflowAction(
                  icon = Icons.Filled.Block,
                  label = stringResource(R.string.pref_folders_blacklist),
                  onClick = {
                    viewModel.blacklistFolders(folderSelectionManager.getSelectedItems())
                    folderSelectionManager.clear()
                  },
                ))
              }
              val selectedVideos = videoSelectionManager.getSelectedItems()
              val selectedFolders = folderSelectionManager.getSelectedItems()
              val hasSelection = selectedVideos.isNotEmpty() || selectedFolders.isNotEmpty()
              if (hasSelection) {
                add(SelectionOverflowAction(
                  icon = Icons.Filled.ContentCopy,
                  label = if (selectedVideos.isNotEmpty() && selectedFolders.isEmpty()) {
                    stringResource(R.string.copy_video_path)
                  } else if (selectedFolders.isNotEmpty() && selectedVideos.isEmpty()) {
                    stringResource(R.string.copy_folder_path)
                  } else {
                    stringResource(R.string.copy_path)
                  },
                  onClick = {
                    val paths = mutableListOf<String>()
                    selectedFolders.forEach { paths.add(it.path) }
                    selectedVideos.forEach { paths.add(it.path) }
                    
                    if (paths.isNotEmpty()) {
                      val pathsString = paths.joinToString("\n")
                      clipboardManager.setText(AnnotatedString(pathsString))
                      Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    }
                  }
                ))
              }
            },
            normalOverflowActions = emptyList(),
          )
        },
      floatingActionButton = {
        if (isAtRoot) {
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
                visible = !isInSelectionMode && isFabVisible.value && !xyz.mpv.rex.ui.browser.MainScreen.getPermissionDeniedState(),
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
                            val recentlyPlayedVideos = xyz.mpv.rex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
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
              text = { Text(text = stringResource(R.string.open_file)) },
            )

            FloatingActionButtonMenuItem(
              onClick = {
                isFabExpanded.value = false
                coroutineScope.launch {
                  val recentlyPlayedVideos = xyz.mpv.rex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                  val lastPlayed = recentlyPlayedVideos.firstOrNull()
                  if (lastPlayed != null) {
                    MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                  }
                }
              },
              icon = { Icon(Icons.Filled.History, contentDescription = null) },
              text = { Text(text = stringResource(R.string.recently_played)) },
            )

            FloatingActionButtonMenuItem(
              onClick = {
                isFabExpanded.value = false
                showLinkDialog.value = true
              },
              icon = { Icon(Icons.Filled.Link, contentDescription = null) },
              text = { Text(text = stringResource(R.string.open_link)) },
            )
          }
        }
      },
    ) { padding ->
      Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
        when (permissionState.status) {
          PermissionStatus.Granted -> {
            FileSystemBrowserContent(
              listState = listState,
              items = items,
              videoFilesWithPlayback = videoFilesWithPlayback,
              newVideoIds = newVideoIds,
              watchedVideoIds = watchedVideoIds,
              isLoading = isLoading && items.isEmpty(),
              uiSettings = uiSettings,
              isRefreshing = isRefreshing,
              error = error,
              isAtRoot = isAtRoot,
              breadcrumbs = breadcrumbs,
              playlistMode = playlistMode,
              itemsWereDeletedOrMoved = itemsWereDeletedOrMoved,
              showSubtitleIndicator = showSubtitleIndicator,
              recentlyPlayedFilePath = recentlyPlayedFilePath,
              recentlyPlayedFilePaths = recentlyPlayedFilePaths,
              recentlyPlayedPaths = recentlyPlayedPaths,
              autoScrollToLastPlayed = autoScrollToLastPlayed,
              navigationBarHeight = navigationBarHeight,
              onRefresh = { viewModel.refresh() },
              onFolderClick = { folder ->
                if (isInSelectionMode) {
                  folderSelectionManager.toggle(folder)
                } else {
                  backstack.add(FileSystemDirectoryScreen(folder.path))
                }
              },
              onFolderLongClick = { folder ->
                folderSelectionManager.handleLongClick(folder)
              },
              onVideoClick = { video ->
                if (isInSelectionMode) {
                  videoSelectionManager.toggle(video)
                } else {
                  // Use MediaUtils.playFile which correctly passes all extras (width, height, rotation, savedOrientation)
                  // and allows PlayerActivity to auto-generate the playlist if playlistMode is enabled.
                  MediaUtils.playFile(video, context, "tree_mode")
                }
              },
              onVideoLongClick = { video ->
                videoSelectionManager.handleLongClick(video)
              },
              onBreadcrumbClick = { component ->
                // Navigate to the breadcrumb by popping until we reach it
                // or pushing if it's a new path
                backstack.add(FileSystemDirectoryScreen(component.fullPath))
              },
              folderSelectionManager = folderSelectionManager,
              videoSelectionManager = videoSelectionManager,
              modifier = Modifier,
              isInSelectionMode = isInSelectionMode,
              scrollTriggerKey = "${sortType.name}:${sortOrder.name}",
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
    }

    // Independent Floating Bottom Bar - positioned at absolute bottom
    // Play Store gating is intentionally bypassed here.
    AnimatedVisibility(
      visible = showFloatingBottomBar,
      enter = slideInVertically(
        animationSpec = tween(durationMillis = animationDuration),
        initialOffsetY = { fullHeight -> fullHeight }
      ),
      exit = slideOutVertically(
        animationSpec = tween(durationMillis = animationDuration),
        targetOffsetY = { fullHeight -> fullHeight }
      ),
      modifier = Modifier.align(Alignment.BottomCenter)
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
        onRenameClick = { renameDialogOpen.value = true },
        onDeleteClick = { deleteDialogOpen.value = true },
        onAddToPlaylistClick = { addToPlaylistDialogOpen.value = true },
        onMarkAsClick = { showMarkAsSheet = true },
        showRename = (videoSelectionManager.isSingleSelection || folderSelectionManager.isSingleSelection) && !isMixedSelection,
        showAddToPlaylist = videoSelectionManager.isInSelectionMode && !isMixedSelection,
        showDelete = !isMixedSelection && folderSelectionManager.selectedCount <= 1
      )
    }

    // Mark As Sheet
    if (showMarkAsSheet) {
      MarkAsBottomSheet(
        onDismiss = { showMarkAsSheet = false },
        onMarkAs = { state ->
          coroutineScope.launch {
            // Videos selected directly
            videoSelectionManager.getSelectedItems().forEach { video ->
              RecentlyPlayedOps.markAs(
                filePath = video.path,
                fileName = video.displayName,
                duration = video.duration,
                state = state,
              )
            }
            // Folders selected — apply to all videos inside recursively
            folderSelectionManager.getSelectedItems().forEach { folder ->
              collectVideosRecursively(context, folder.path).forEach { video ->
                RecentlyPlayedOps.markAs(
                  filePath = video.path,
                  fileName = video.displayName,
                  duration = video.duration,
                  state = state,
                )
              }
            }
          }
        },
      )
    }

    // Dialogs
    PlayLinkSheet(
      isOpen = showLinkDialog.value,
      onDismiss = { showLinkDialog.value = false },
      onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
    )

    FileSystemSortDialog(
      isOpen = sortDialogOpen.value,
      onDismiss = { sortDialogOpen.value = false },
      isAtRoot = isAtRoot,
    )

    DeleteConfirmationDialog(
      isOpen = deleteDialogOpen.value,
      onDismiss = { deleteDialogOpen.value = false },
      onConfirm = {
        if (folderSelectionManager.isInSelectionMode) {
          folderSelectionManager.deleteSelected()
        }
        if (videoSelectionManager.isInSelectionMode) {
          videoSelectionManager.deleteSelected()
        }
      },
      itemTypePluralRes = when {
        folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode -> R.plurals.item_type_item_plural
        folderSelectionManager.isInSelectionMode -> R.plurals.item_type_folder_plural
        else -> R.plurals.item_type_video_plural
      },
      itemCount = selectedCount,
      itemNames = (folderSelectionManager.getSelectedItems().map { it.name } +
        videoSelectionManager.getSelectedItems().map { it.displayName }),
    )

    // Rename Dialog
    if (renameDialogOpen.value) {
      if (folderSelectionManager.isSingleSelection) {
        val folder = folderSelectionManager.getSelectedItems().firstOrNull()
        if (folder != null) {
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen.value = false },
            onConfirm = { newName ->
              renameDialogOpen.value = false
              coroutineScope.launch {
                val ok = viewModel.renameFolder(folder, newName)
                if (!ok) {
                  android.widget.Toast.makeText(context, context.getString(R.string.rename_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
                folderSelectionManager.clear()
                viewModel.refresh()
              }
            },
            currentName = folder.name,
            itemTypeRes = R.string.item_type_folder,
          )
        }
      } else if (videoSelectionManager.isSingleSelection) {
        val video = videoSelectionManager.getSelectedItems().firstOrNull()
        if (video != null) {
          val baseName = video.displayName.substringBeforeLast('.')
          val extension = "." + video.displayName.substringAfterLast('.', "")
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen.value = false },
            onConfirm = { newName -> videoSelectionManager.renameSelected(newName) },
            currentName = baseName,
            itemTypeRes = R.string.item_type_file,
            extension = if (extension != ".") extension else null,
          )
        }
      }
    }

    // Folder Picker Dialog
    FolderPickerDialog(
      isOpen = folderPickerOpen.value,
      currentPath = currentPath,
      onDismiss = { folderPickerOpen.value = false },
      onFolderSelected = { destinationPath ->
        folderPickerOpen.value = false
        val op = operationType.value
        if (op != null) {
          coroutineScope.launch {
            if (folderSelectionManager.isInSelectionMode) {
              val selectedFolders = folderSelectionManager.getSelectedItems()
              if (selectedFolders.isNotEmpty()) {
                when (op) {
                  is CopyPasteOps.OperationType.Move -> {
                    val needFallback = mutableListOf<FileSystemItem.Folder>()
                    for (folder in selectedFolders) {
                      val dst = File(destinationPath, folder.name)
                      if (!File(folder.path).renameTo(dst)) needFallback.add(folder)
                    }
                    if (needFallback.isNotEmpty()) {
                      progressDialogOpen.value = true
                      for (folder in needFallback) {
                        val videos = collectVideosRecursively(context, folder.path)
                        if (videos.isNotEmpty()) {
                          val subDest = File(destinationPath, folder.name).also { it.mkdirs() }.absolutePath
                          CopyPasteOps.moveFiles(context, videos, subDest)
                        }
                      }
                    } else {
                      viewModel.setItemsWereDeletedOrMoved()
                      folderSelectionManager.clear()
                      viewModel.refresh()
                    }
                  }
                  is CopyPasteOps.OperationType.Copy -> {
                    progressDialogOpen.value = true
                    for (folder in selectedFolders) {
                      val videos = collectVideosRecursively(context, folder.path)
                      if (videos.isNotEmpty()) {
                        val subDest = File(destinationPath, folder.name).also { it.mkdirs() }.absolutePath
                        CopyPasteOps.copyFiles(context, videos, subDest)
                      }
                    }
                  }
                }
              }
            } else {
              val selectedVideos = videoSelectionManager.getSelectedItems()
              if (selectedVideos.isNotEmpty()) {
                progressDialogOpen.value = true
                when (op) {
                  is CopyPasteOps.OperationType.Copy -> CopyPasteOps.copyFiles(context, selectedVideos, destinationPath)
                  is CopyPasteOps.OperationType.Move -> CopyPasteOps.moveFiles(context, selectedVideos, destinationPath)
                }
              }
            }
          }
        }
      },
    )

    // File Operation Progress Dialog
    if (operationType.value != null) {
      FileOperationProgressDialog(
        isOpen = progressDialogOpen.value,
        operationType = operationType.value!!,
        progress = operationProgress,
        onCancel = {
          CopyPasteOps.cancelOperation()
        },
        onDismiss = {
          progressDialogOpen.value = false
          // Set flag if move operation was successful
          if (operationType.value is CopyPasteOps.OperationType.Move &&
            operationProgress.isComplete &&
            operationProgress.error == null) {
            viewModel.setItemsWereDeletedOrMoved()
          }
          operationType.value = null
          videoSelectionManager.clear()
          folderSelectionManager.clear()
          viewModel.refresh()
        },
      )
    }

    // Add to Playlist Dialog
    AddToPlaylistDialog(
      isOpen = addToPlaylistDialogOpen.value,
      videos = videoSelectionManager.getSelectedItems(),
      onDismiss = { addToPlaylistDialogOpen.value = false },
      onSuccess = {
        videoSelectionManager.clear()
        viewModel.refresh()
      },
    )

    mediaInfoUri?.let { uri ->
      xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet(uri = uri, onDismiss = { mediaInfoUri = null })
    }
    multiSelectionInfo?.let { (count, bytes, duration) ->
      xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet(count = count, totalBytes = bytes, totalDurationMs = duration, onDismiss = { multiSelectionInfo = null }, unit = multiSelectionUnit)
    }

    if (showWebShareSheet) {
      xyz.mpv.rex.feature.webshare.WebShareSheet(
        videos = videoSelectionManager.getSelectedItems(),
        onDismiss = { showWebShareSheet = false }
      )
    }
  }
}

/**
 * Recursively searches for files matching the query in a directory and its subdirectories
 */
suspend fun searchRecursively(
  context: Context,
  directoryPath: String,
  query: String,
): List<FileSystemItem> {
  val results = mutableListOf<FileSystemItem>()
  
  try {
    Log.d("FileSystemBrowserScreen", "Scanning directory: $directoryPath for query: $query")
    // Scan the current directory
    val items = xyz.mpv.rex.repository.MediaFileRepository
      .scanDirectory(context, directoryPath, showAllFileTypes = false)
      .getOrNull() ?: emptyList()

    Log.d("FileSystemBrowserScreen", "Found ${items.size} items in $directoryPath")

    // Filter items that match the search query (case-insensitive) and respect audio preference
    val browserPreferences = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.preferences.BrowserPreferences>()
    val isAudioFilesVisible = browserPreferences.showAudioFiles.get()
    items.forEach { item ->
      when (item) {
        is FileSystemItem.VideoFile -> {
          if ((isAudioFilesVisible || !item.video.isAudio) && item.video.displayName.contains(query, ignoreCase = true)) {
            Log.d("FileSystemBrowserScreen", "Found matching video: ${item.video.displayName}")
            results.add(item)
          }
        }
        is FileSystemItem.Folder -> {
          if ((isAudioFilesVisible || item.videoCount > 0) && item.name.contains(query, ignoreCase = true)) {
            Log.d("FileSystemBrowserScreen", "Found matching folder: ${item.name}")
            results.add(item)
          }
          // Recursively search in subdirectories if they have content we care about
          if (isAudioFilesVisible || item.videoCount > 0) {
            try {
              val subResults = searchRecursively(context, item.path, query)
              results.addAll(subResults)
            } catch (e: Exception) {
              Log.e("FileSystemBrowserScreen", "Error searching subdirectory ${item.path}", e)
            }
          }
        }
      }
    }
    
    Log.d("FileSystemBrowserScreen", "Returning ${results.size} results from $directoryPath")
  } catch (e: Exception) {
    Log.e("FileSystemBrowserScreen", "Error searching directory $directoryPath", e)
  }

  return results
}

/**
 * Recursively collects all videos from a folder and its subfolders
 */
private suspend fun collectVideosRecursively(
  context: Context,
  folderPath: String,
): List<xyz.mpv.rex.domain.media.model.Video> {
  val videos = mutableListOf<xyz.mpv.rex.domain.media.model.Video>()

  try {
    // Scan the current directory using MediaFileRepository
    val items = xyz.mpv.rex.repository.MediaFileRepository
      .scanDirectory(context, folderPath, showAllFileTypes = false)
      .getOrNull() ?: emptyList()

    // Add videos from current folder respecting audio preference
    val browserPreferences = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.preferences.BrowserPreferences>()
    val isAudioFilesVisible = browserPreferences.showAudioFiles.get()
    items.filterIsInstance<FileSystemItem.VideoFile>().forEach { videoFile ->
      if (isAudioFilesVisible || !videoFile.video.isAudio) {
        videos.add(videoFile.video)
      }
    }

    // Recursively scan subfolders that have relevant content
    items.filterIsInstance<FileSystemItem.Folder>().forEach { folder ->
      if (isAudioFilesVisible || folder.videoCount > 0) {
        val subVideos = collectVideosRecursively(context, folder.path)
        videos.addAll(subVideos)
      }
    }
  } catch (e: Exception) {
    Log.e("FileSystemBrowserScreen", "Error collecting videos from $folderPath", e)
  }

  return videos
}

/**
 * Plays a list of videos as a playlist
 */
private fun playVideosAsPlaylist(
  context: Context,
  videos: List<xyz.mpv.rex.domain.media.model.Video>,
) {
  if (videos.isEmpty()) return

  if (videos.size == 1) {
    // Single video - play normally
    MediaUtils.playFile(videos.first(), context)
    } else {
      // Multiple videos - play as playlist
      MediaUtils.playPlaylist(videos, 0, context)
    }
  }

@Composable
private fun FileSystemBrowserContent(
  listState: LazyListState,
  items: List<FileSystemItem>,
  videoFilesWithPlayback: Map<Long, Float>,
  newVideoIds: Set<Long>,
  watchedVideoIds: Set<Long>,
  isLoading: Boolean,
  uiSettings: UiSettings,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  error: String?,
  isAtRoot: Boolean,
  breadcrumbs: List<xyz.mpv.rex.domain.browser.PathComponent>,
  playlistMode: Boolean,
  itemsWereDeletedOrMoved: Boolean,
  showSubtitleIndicator: Boolean,
  recentlyPlayedFilePath: String?,
  recentlyPlayedPaths: Set<String> = emptySet(),
  recentlyPlayedFilePaths: Set<String> = emptySet(),
  autoScrollToLastPlayed: Boolean,
  navigationBarHeight: Dp,
  onRefresh: suspend () -> Unit,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  onFolderLongClick: (FileSystemItem.Folder) -> Unit,
  onVideoClick: (xyz.mpv.rex.domain.media.model.Video) -> Unit,
  onVideoLongClick: (xyz.mpv.rex.domain.media.model.Video) -> Unit,
  onBreadcrumbClick: (xyz.mpv.rex.domain.browser.PathComponent) -> Unit,
  folderSelectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<FileSystemItem.Folder, String>,
  videoSelectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<xyz.mpv.rex.domain.media.model.Video, Long>,
  modifier: Modifier = Modifier,
  isInSelectionMode: Boolean = false,
  scrollTriggerKey: Any? = null,
) {
  val thumbnailRepository = koinInject<xyz.mpv.rex.domain.thumbnail.ThumbnailRepository>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showTreeViewPath by browserPreferences.showTreeViewPath.collectAsState()

  // Calculate thumbnail dimensions
  val thumbWidthDp = 160.dp
  val density = androidx.compose.ui.platform.LocalDensity.current
  val aspect = 16f / 9f
  val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = ((thumbWidthPx.toFloat() / aspect).toInt())

  val folders = items.filterIsInstance<FileSystemItem.Folder>()
  val videos = items.filterIsInstance<FileSystemItem.VideoFile>().map { it.video }

  val folderId = remember(folders, isAtRoot, breadcrumbs) {
    if (isAtRoot && breadcrumbs.isEmpty()) {
      "filesystem_root"
    } else {
      breadcrumbs.lastOrNull()?.fullPath ?: "filesystem_${breadcrumbs.size}"
    }
  }

  // Generate thumbnails sequentially
  LaunchedEffect(folderId, showVideoThumbnails, videos.size, thumbWidthPx, thumbHeightPx) {
    if (showVideoThumbnails && videos.isNotEmpty()) {
      thumbnailRepository.startFolderThumbnailGeneration(
        folderId = folderId,
        videos = videos,
        widthPx = thumbWidthPx,
        heightPx = thumbHeightPx,
      )
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    // Breadcrumb navigation (if not at root and enabled)
    if (showTreeViewPath && !isAtRoot && breadcrumbs.isNotEmpty()) {
      xyz.mpv.rex.ui.browser.filesystem.BreadcrumbNavigation(
        breadcrumbs = breadcrumbs,
        onBreadcrumbClick = onBreadcrumbClick,
      )
    }

    UnifiedExplorerContent(
      items = items,
      isLoading = isLoading,
      uiSettings = uiSettings,
      isSelected = { item ->
        when (item) {
          is FileSystemItem.Folder -> folderSelectionManager.isSelected(item)
          is FileSystemItem.VideoFile -> videoSelectionManager.isSelected(item.video)
        }
      },
      onClick = { item ->
        when (item) {
          is FileSystemItem.Folder -> onFolderClick(item)
          is FileSystemItem.VideoFile -> onVideoClick(item.video)
        }
      },
      onLongClick = { item ->
        when (item) {
          is FileSystemItem.Folder -> onFolderLongClick(item)
          is FileSystemItem.VideoFile -> onVideoLongClick(item.video)
        }
      },
      onToggleSelection = { item ->
        when (item) {
          is FileSystemItem.Folder -> folderSelectionManager.toggle(item)
          is FileSystemItem.VideoFile -> videoSelectionManager.toggle(item.video)
        }
      },
      modifier = Modifier.weight(1f),
      emptyTitle = stringResource(R.string.empty_folder_title),
      emptyMessage = stringResource(R.string.empty_folder_message),
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      isInSelectionMode = isInSelectionMode,
      recentlyPlayedFilePath = recentlyPlayedFilePath,
      recentlyPlayedFilePaths = recentlyPlayedFilePaths,
      recentlyPlayedPaths = recentlyPlayedPaths,
      autoScrollToLastPlayed = autoScrollToLastPlayed,
      listState = listState,
      newVideoIds = newVideoIds,
      watchedVideoIds = watchedVideoIds,
      videoPlaybackProgress = videoFilesWithPlayback,
      scrollTriggerKey = scrollTriggerKey,
      showSections = true,
    )
  }
}

