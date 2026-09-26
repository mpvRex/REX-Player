package xyz.mpv.rex.ui.browser.search

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.feature.webshare.WebShareSheet
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.components.BrowserBottomBar
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.FileOperationProgressDialog
import xyz.mpv.rex.ui.browser.dialogs.FolderPickerDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.filesystem.FileSystemDirectoryScreen
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.sheets.MarkAsBottomSheet
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet
import xyz.mpv.rex.ui.browser.videolist.VideoListScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.CopyPasteOps
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SearchScreen(
  val initialPath: String? = null,
  val initialFolderName: String? = null,
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val navigationBarHeight = LocalNavigationBarHeight.current

    val viewModel: SearchViewModel = viewModel(
      key = "search_${initialPath ?: "all"}"
    ) {
      SearchViewModel(
        application = context.applicationContext as Application,
        initialPath = initialPath,
        initialFolderName = initialFolderName,
      )
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchScope by viewModel.searchScope.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val videoFilesWithPlayback by viewModel.videoFilesWithPlayback.collectAsState()
    val newVideoIds by viewModel.newVideoIds.collectAsState()
    val watchedVideoIds by viewModel.watchedVideoIds.collectAsState()

    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val recentlyPlayedFilePaths by viewModel.recentlyPlayedFilePaths.collectAsState()
    val recentlyPlayedPaths by viewModel.recentlyPlayedPaths.collectAsState()
    val browserPreferences = koinInject<BrowserPreferences>()
    val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()

    // Selection managers
    val folders = searchResults.filterIsInstance<FileSystemItem.Folder>()
    val videos = searchResults.filterIsInstance<FileSystemItem.VideoFile>().map { it.video }

    val folderSelectionManager = rememberSelectionManager(
      items = folders,
      getId = { it.path },
      onDeleteItems = { foldersToDelete, _ ->
        viewModel.deleteFolders(foldersToDelete)
      },
      onOperationComplete = {},
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
      onOperationComplete = {},
    )

    val isInSelectionMode = folderSelectionManager.isInSelectionMode || videoSelectionManager.isInSelectionMode
    val selectedCount = folderSelectionManager.selectedCount + videoSelectionManager.selectedCount
    val totalCount = folders.size + videos.size
    val isMixedSelection = folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode

    // Bottom action bar state
    var showFloatingBottomBar by remember { mutableStateOf(false) }
    LaunchedEffect(isInSelectionMode) {
      showFloatingBottomBar = isInSelectionMode
    }

    // Dialog states
    var deleteDialogOpen by rememberSaveable { mutableStateOf(false) }
    var renameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var addToPlaylistDialogOpen by rememberSaveable { mutableStateOf(false) }
    var showMarkAsSheet by remember { mutableStateOf(false) }
    var showWebShareSheet by remember { mutableStateOf(false) }
    var mediaInfoUri by remember { mutableStateOf<Uri?>(null) }
    var multiSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }
    var multiSelectionUnit by remember { mutableStateOf("item") }

    // Copy / Move state
    val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
    val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
    val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
    val operationProgress by CopyPasteOps.operationProgress.collectAsState()

    val treePickerLauncher = rememberLauncherForActivityResult(OpenDocumentTreeContract()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      val selectedVideos = videoSelectionManager.getSelectedItems()
      if (selectedVideos.isEmpty() || operationType.value == null) return@rememberLauncherForActivityResult

      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }

      progressDialogOpen.value = true
      coroutineScope.launch {
        when (operationType.value) {
          is CopyPasteOps.OperationType.Copy -> CopyPasteOps.copyFilesToTreeUri(context, selectedVideos, uri)
          is CopyPasteOps.OperationType.Move -> CopyPasteOps.moveFilesToTreeUri(context, selectedVideos, uri)
          else -> {}
        }
        folderSelectionManager.clear()
        videoSelectionManager.clear()
      }
    }

    // Auto focus search field on start
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }

    // Back handling
    BackHandler {
      if (isInSelectionMode) {
        folderSelectionManager.clear()
        videoSelectionManager.clear()
      } else {
        backstack.removeLastOrNull()
      }
    }

    Scaffold(
      topBar = {
        val topBarContainerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
          Color.Black
        } else {
          MaterialTheme.colorScheme.surfaceContainer
        }

        if (isInSelectionMode) {
          BrowserTopBar(
            title = stringResource(R.string.app_name),
            isInSelectionMode = true,
            selectedCount = selectedCount,
            totalCount = totalCount,
            onBackClick = {
              folderSelectionManager.clear()
              videoSelectionManager.clear()
            },
            onCancelSelection = {
              folderSelectionManager.clear()
              videoSelectionManager.clear()
            },
            onSortClick = {},
            onSearchClick = null,
            onSettingsClick = {},
            isSingleSelection = (videoSelectionManager.isSingleSelection || folderSelectionManager.isSingleSelection) && !isMixedSelection,
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
              coroutineScope.launch {
                val selectedVideos = videoSelectionManager.getSelectedItems()
                val selectedFolders = folderSelectionManager.getSelectedItems()
                val allVideos = mutableListOf<Video>()
                allVideos.addAll(selectedVideos)
                selectedFolders.forEach { folder ->
                  val folderVideos = xyz.mpv.rex.repository.MediaFileRepository.getVideosInFolder(context, folder.path)
                  allVideos.addAll(folderVideos)
                }
                val distinctVideos = allVideos.distinctBy { it.id }
                if (distinctVideos.isNotEmpty()) {
                  if (distinctVideos.size == 1) {
                    MediaUtils.playFile(distinctVideos.first(), context)
                  } else {
                    MediaUtils.playPlaylist(distinctVideos, 0, context)
                  }
                }
                folderSelectionManager.clear()
                videoSelectionManager.clear()
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
              add(
                SelectionOverflowAction(
                  icon = Icons.Filled.PictureInPictureAlt,
                  label = stringResource(R.string.open_with_mini_player),
                  onClick = {
                    coroutineScope.launch {
                      val selectedVideos = videoSelectionManager.getSelectedItems()
                      if (selectedVideos.isNotEmpty()) {
                        MediaUtils.playInMiniPlayer(selectedVideos)
                      }
                      folderSelectionManager.clear()
                      videoSelectionManager.clear()
                    }
                  }
                )
              )
              add(
                SelectionOverflowAction(
                  icon = Icons.Filled.Share,
                  label = stringResource(R.string.generic_share),
                  onClick = {
                    videoSelectionManager.shareSelected()
                    folderSelectionManager.clear()
                  }
                )
              )
              add(
                SelectionOverflowAction(
                  icon = Icons.Filled.Share,
                  label = stringResource(R.string.web_share_title),
                  onClick = { showWebShareSheet = true }
                )
              )
              val selectedVideos = videoSelectionManager.getSelectedItems()
              val selectedFolders = folderSelectionManager.getSelectedItems()
              if (selectedVideos.isNotEmpty() || selectedFolders.isNotEmpty()) {
                add(
                  SelectionOverflowAction(
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
                        clipboardManager.setText(AnnotatedString(paths.joinToString("\n")))
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                      }
                    }
                  )
                )
              }
            }
          )
        } else {
          Surface(
            color = topBarContainerColor,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
            ) {
              TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                  IconButton(onClick = { backstack.removeLastOrNull() }) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = stringResource(R.string.generic_cancel),
                      tint = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                },
                title = {
                  TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateQuery(it) },
                    placeholder = {
                      Text(
                        text = if (searchScope == SearchScope.CURRENT_FOLDER && initialFolderName != null) {
                          stringResource(R.string.search_in_folder_placeholder, initialFolderName)
                        } else {
                          stringResource(R.string.search_all_videos)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                      color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    colors = TextFieldDefaults.colors(
                      focusedContainerColor = Color.Transparent,
                      unfocusedContainerColor = Color.Transparent,
                      disabledContainerColor = Color.Transparent,
                      focusedIndicatorColor = Color.Transparent,
                      unfocusedIndicatorColor = Color.Transparent,
                      disabledIndicatorColor = Color.Transparent,
                      focusedTextColor = MaterialTheme.colorScheme.onSurface,
                      unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                      cursorColor = MaterialTheme.colorScheme.primary,
                      focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .focusRequester(focusRequester),
                  )
                },
                actions = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) {
                      Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.generic_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                },
              )

              // Scope filter chips if opened with initialPath
              if (initialPath != null) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  FilterChip(
                    selected = searchScope == SearchScope.CURRENT_FOLDER,
                    onClick = { viewModel.setScope(SearchScope.CURRENT_FOLDER) },
                    label = {
                      Text(
                        text = initialFolderName ?: File(initialPath).name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                  )

                  Spacer(modifier = Modifier.width(8.dp))

                  FilterChip(
                    selected = searchScope == SearchScope.ALL_STORAGE,
                    onClick = { viewModel.setScope(SearchScope.ALL_STORAGE) },
                    label = { Text(stringResource(R.string.search_in_all_storage_volumes)) },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                  )
                }
              }
            }
          }
        }
      }
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = padding.calculateTopPadding())
      ) {
        UnifiedExplorerContent(
          items = searchResults,
          isLoading = isSearchLoading,
          uiSettings = uiSettings,
          isSelected = { item ->
            when (item) {
              is FileSystemItem.Folder -> folderSelectionManager.isSelected(item)
              is FileSystemItem.VideoFile -> videoSelectionManager.isSelected(item.video)
            }
          },
          onClick = { item ->
            if (isInSelectionMode) {
              when (item) {
                is FileSystemItem.Folder -> folderSelectionManager.toggle(item)
                is FileSystemItem.VideoFile -> videoSelectionManager.toggle(item.video)
              }
            } else {
              when (item) {
                is FileSystemItem.Folder -> {
                  backstack.add(FileSystemDirectoryScreen(item.path))
                }
                is FileSystemItem.VideoFile -> {
                  MediaUtils.playFile(item.video, context, "search")
                }
              }
            }
          },
          onLongClick = { item ->
            when (item) {
              is FileSystemItem.Folder -> folderSelectionManager.handleLongClick(item)
              is FileSystemItem.VideoFile -> videoSelectionManager.handleLongClick(item.video)
            }
          },
          onToggleSelection = { item ->
            when (item) {
              is FileSystemItem.Folder -> folderSelectionManager.toggle(item)
              is FileSystemItem.VideoFile -> videoSelectionManager.toggle(item.video)
            }
          },
          emptyTitle = if (searchQuery.isBlank()) {
            stringResource(R.string.search_empty_title)
          } else {
            stringResource(R.string.search_no_results_title)
          },
          emptyMessage = if (searchQuery.isBlank()) {
            stringResource(R.string.search_empty_message)
          } else {
            stringResource(R.string.search_no_results_message)
          },
          emptyIcon = Icons.Filled.Search,
          isInSelectionMode = isInSelectionMode,
          recentlyPlayedFilePath = recentlyPlayedFilePath,
          recentlyPlayedFilePaths = recentlyPlayedFilePaths,
          recentlyPlayedPaths = recentlyPlayedPaths,
          newVideoIds = newVideoIds,
          watchedVideoIds = watchedVideoIds,
          videoPlaybackProgress = videoFilesWithPlayback,
          showSections = true,
        )

        // Floating Bottom Bar for multi-selection actions
        AnimatedVisibility(
          visible = showFloatingBottomBar,
          enter = slideInVertically(
            animationSpec = tween(durationMillis = 200),
            initialOffsetY = { fullHeight -> fullHeight }
          ),
          exit = slideOutVertically(
            animationSpec = tween(durationMillis = 200),
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
            onRenameClick = { renameDialogOpen = true },
            onDeleteClick = { deleteDialogOpen = true },
            onAddToPlaylistClick = { addToPlaylistDialogOpen = true },
            onMarkAsClick = { showMarkAsSheet = true },
            showRename = (videoSelectionManager.isSingleSelection || folderSelectionManager.isSingleSelection) && !isMixedSelection,
            showAddToPlaylist = videoSelectionManager.isInSelectionMode && !isMixedSelection,
            showDelete = !isMixedSelection && folderSelectionManager.selectedCount <= 1
          )
        }
      }
    }

    // Rename Dialog
    if (renameDialogOpen) {
      if (folderSelectionManager.isSingleSelection) {
        val folder = folderSelectionManager.getSelectedItems().firstOrNull()
        if (folder != null) {
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen = false },
            onConfirm = { newName ->
              renameDialogOpen = false
              coroutineScope.launch {
                val ok = viewModel.renameFolder(folder, newName)
                if (!ok) {
                  Toast.makeText(context, R.string.rename_failed, Toast.LENGTH_SHORT).show()
                }
                folderSelectionManager.clear()
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
            onDismiss = { renameDialogOpen = false },
            onConfirm = { newName ->
              videoSelectionManager.renameSelected(newName)
              renameDialogOpen = false
            },
            currentName = baseName,
            itemTypeRes = R.string.item_type_file,
            extension = if (extension != ".") extension else null,
          )
        }
      }
    }

    // Delete Confirmation Dialog
    DeleteConfirmationDialog(
      isOpen = deleteDialogOpen,
      onDismiss = { deleteDialogOpen = false },
      onConfirm = {
        if (folderSelectionManager.isInSelectionMode) {
          folderSelectionManager.deleteSelected()
        }
        if (videoSelectionManager.isInSelectionMode) {
          videoSelectionManager.deleteSelected()
        }
        deleteDialogOpen = false
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

    // Folder Picker Dialog for Copy/Move
    FolderPickerDialog(
      isOpen = folderPickerOpen.value,
      currentPath = initialPath ?: "",
      onDismiss = { folderPickerOpen.value = false },
      onFolderSelected = { targetPath ->
        folderPickerOpen.value = false
        val op = operationType.value
        if (op != null) {
          coroutineScope.launch {
            val selectedVideos = videoSelectionManager.getSelectedItems()
            val selectedFolders = folderSelectionManager.getSelectedItems()
            if (selectedVideos.isNotEmpty() || selectedFolders.isNotEmpty()) {
              progressDialogOpen.value = true
              when (op) {
                is CopyPasteOps.OperationType.Copy -> {
                  if (selectedVideos.isNotEmpty()) CopyPasteOps.copyFiles(context, selectedVideos, targetPath)
                  for (folder in selectedFolders) {
                    val folderVideos = xyz.mpv.rex.repository.MediaFileRepository.getVideosInFolder(context, folder.path)
                    if (folderVideos.isNotEmpty()) {
                      val subDest = File(targetPath, folder.name).also { it.mkdirs() }.absolutePath
                      CopyPasteOps.copyFiles(context, folderVideos, subDest)
                    }
                  }
                }
                is CopyPasteOps.OperationType.Move -> {
                  if (selectedVideos.isNotEmpty()) CopyPasteOps.moveFiles(context, selectedVideos, targetPath)
                  for (folder in selectedFolders) {
                    val dst = File(targetPath, folder.name)
                    if (!File(folder.path).renameTo(dst)) {
                      val folderVideos = xyz.mpv.rex.repository.MediaFileRepository.getVideosInFolder(context, folder.path)
                      if (folderVideos.isNotEmpty()) {
                        val subDest = dst.also { it.mkdirs() }.absolutePath
                        CopyPasteOps.moveFiles(context, folderVideos, subDest)
                      }
                    }
                  }
                }
              }
              folderSelectionManager.clear()
              videoSelectionManager.clear()
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
          operationType.value = null
          videoSelectionManager.clear()
          folderSelectionManager.clear()
        },
      )
    }

    // Add to Playlist Dialog
    AddToPlaylistDialog(
      isOpen = addToPlaylistDialogOpen,
      videos = videoSelectionManager.getSelectedItems(),
      onDismiss = { addToPlaylistDialogOpen = false },
      onSuccess = {
        videoSelectionManager.clear()
        addToPlaylistDialogOpen = false
      },
    )

    // Mark As Bottom Sheet
    if (showMarkAsSheet) {
      MarkAsBottomSheet(
        onDismiss = { showMarkAsSheet = false },
        onMarkAs = { state ->
          coroutineScope.launch {
            videoSelectionManager.getSelectedItems().forEach { video ->
              RecentlyPlayedOps.markAs(
                filePath = video.path,
                fileName = video.displayName,
                duration = video.duration,
                state = state,
              )
            }
            folderSelectionManager.getSelectedItems().forEach { folder ->
              val folderVideos = xyz.mpv.rex.repository.MediaFileRepository.getVideosInFolder(context, folder.path)
              folderVideos.forEach { video ->
                RecentlyPlayedOps.markAs(
                  filePath = video.path,
                  fileName = video.displayName,
                  duration = video.duration,
                  state = state,
                )
              }
            }
            folderSelectionManager.clear()
            videoSelectionManager.clear()
            showMarkAsSheet = false
          }
        },
      )
    }

    // Media Info Sheet
    mediaInfoUri?.let { uri ->
      MediaInfoSheet(
        uri = uri,
        onDismiss = { mediaInfoUri = null },
      )
    }

    // Multi-Selection Info Sheet
    multiSelectionInfo?.let { (count, size, duration) ->
      MultiSelectionInfoSheet(
        count = count,
        totalBytes = size,
        totalDurationMs = duration,
        unit = multiSelectionUnit,
        onDismiss = { multiSelectionInfo = null },
      )
    }

    // Web Share Sheet
    if (showWebShareSheet) {
      val selectedVideos = videoSelectionManager.getSelectedItems()
      WebShareSheet(
        videos = selectedVideos,
        onDismiss = {
          showWebShareSheet = false
          videoSelectionManager.clear()
        },
      )
    }
  }
}
