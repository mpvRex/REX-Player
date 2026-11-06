package xyz.mpv.rex.ui.browser.medialibrary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import xyz.mpv.rex.ui.browser.sheets.PlayLinkSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import android.widget.Toast
import xyz.mpv.rex.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import androidx.compose.ui.text.style.TextOverflow
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.browser.components.BrowserBottomBar
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.videolist.VideoListContent
import xyz.mpv.rex.ui.browser.dialogs.VideoSortDialog
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.browser.sheets.MarkAsBottomSheet
import xyz.mpv.rex.utils.history.MarkAsState
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.sort.PinnedSortUtils
import xyz.mpv.rex.utils.sort.SortUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryContent() {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val backstack = LocalBackStack.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hidePlayFabShowInHeader by appearancePreferences.hidePlayFabShowInHeader.collectAsState()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

  // ViewModel
  val viewModel: MediaLibraryViewModel = viewModel(
    factory = MediaLibraryViewModel.factory(context.applicationContext as android.app.Application)
  )
  val videos by viewModel.videos.collectAsState()
  val videosWithPlaybackInfo by viewModel.videosWithPlaybackInfo.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val uiSettings by viewModel.uiSettings.collectAsState()
  val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
  val recentlyPlayedFilePaths by viewModel.recentlyPlayedFilePaths.collectAsState()

  // Sorting
  val videoSortType by browserPreferences.videoSortType.collectAsState()
  val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()

  // Media pins are shared across tree / video list / media library views.
  val pinnedItemRepository = koinInject<xyz.mpv.rex.domain.pinned.PinnedItemRepository>()
  val mediaPins by
    pinnedItemRepository.pinnedPaths(xyz.mpv.rex.domain.pinned.PinScope.MEDIA)
      .collectAsState(initial = pinnedItemRepository.getPinnedPaths(xyz.mpv.rex.domain.pinned.PinScope.MEDIA))

  val sortedVideosWithInfo = remember(videosWithPlaybackInfo, videoSortType, videoSortOrder, mediaPins) {
    val infoById = videosWithPlaybackInfo.associateBy { it.video.path.ifBlank { it.video.uri.toString() } }
    val uniqueVideos = videosWithPlaybackInfo.map { it.video }.distinctBy { it.path.ifBlank { it.uri.toString() } }
    val sortedVideos = SortUtils.sortVideos(uniqueVideos, videoSortType, videoSortOrder)
    val mapped = sortedVideos.map { video ->
      val key = video.path.ifBlank { video.uri.toString() }
      infoById[key] ?: VideoWithPlaybackInfo(video)
    }
    // Pinned media first, in pin order, then the regular sort.
    PinnedSortUtils.sortPinnedFirst(mapped, mediaPins) { it.video.path.ifBlank { it.video.uri.toString() } }
  }

  // Selection manager
  val selectionManager = rememberSelectionManager(
    items = sortedVideosWithInfo.map { it.video },
    getId = { it.path.hashCode().toLong() },
    onDeleteItems = { items, _ -> 
      coroutineScope.launch { viewModel.deleteVideos(items) }
      Pair(items.size, 0)
    },
    onRenameItem = { video, newName -> 
      coroutineScope.launch { viewModel.renameVideo(video, newName) }
      Result.success(Unit)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  // UI State
  val isRefreshing = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }

  var mediaInfoUri by remember { mutableStateOf<Uri?>(null) }
  var multiSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }

  // Search state
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var isSearching by rememberSaveable { mutableStateOf(false) }

  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  val clipboardManager = LocalClipboardManager.current

  // Auto-focus search input when search is opened
  LaunchedEffect(isSearching) {
    if (isSearching) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  // FAB visibility state
  val isFabVisible = remember { mutableStateOf(true) }
  val isFabExpanded = remember { mutableStateOf(false) }
  val showLinkDialog = remember { mutableStateOf(false) }

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

  // Bottom bar animation state
  var showFloatingBottomBar by remember { mutableStateOf(false) }
  var showMarkAsSheet by remember { mutableStateOf(false) }
  var showWebShareSheet by remember { mutableStateOf(false) }
  val animationDuration = 300

  LaunchedEffect(selectionManager.isInSelectionMode) {
    showFloatingBottomBar = selectionManager.isInSelectionMode
  }

  val shouldHandleBack = selectionManager.isInSelectionMode || isSearching
  BackHandler(enabled = shouldHandleBack) {
    when {
      selectionManager.isInSelectionMode -> selectionManager.clear()
      isSearching -> {
        isSearching = false
        searchQuery = ""
      }
    }
  }

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
        SearchBar(
          inputField = {
            SearchBarDefaults.InputField(
              query = searchQuery,
              onQueryChange = { searchQuery = it },
              onSearch = { },
              expanded = false,
              onExpandedChange = { },
              placeholder = { Text(stringResource(R.string.search_all_videos), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_empty_title)) },
              trailingIcon = {
                IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                  Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.generic_cancel))
                }
              },
              modifier = Modifier.focusRequester(focusRequester),
            )
          },
          expanded = false,
          onExpandedChange = { },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          shape = RoundedCornerShape(28.dp),
          tonalElevation = 6.dp,
        ) { }
      } else {
        BrowserTopBar(
          title = stringResource(xyz.mpv.rex.R.string.app_name),
          scrollBehavior = scrollBehavior,
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = sortedVideosWithInfo.size,
          onBackClick = null, // Unified header: no back button at root
          isHomeScreen = true,
          normalPlayClick = if (hidePlayFabShowInHeader && sortedVideosWithInfo.isNotEmpty()) {
            {
              coroutineScope.launch {
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                val lastPlayed = recentlyPlayedVideos.firstOrNull()
                if (lastPlayed != null && sortedVideosWithInfo.any { it.video.path == lastPlayed.filePath }) {
                  MediaUtils.playFile(lastPlayed.filePath, context, "media_library_list")
                } else {
                  MediaUtils.playFile(sortedVideosWithInfo.first().video, context, "media_library_list")
                }
              }
            }
          } else null,
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = { backstack.add(xyz.mpv.rex.ui.browser.search.SearchScreen()) },
          onSettingsClick = {
            backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = {
            val selected = selectionManager.getSelectedItems()
            if (selectionManager.isSingleSelection) {
              mediaInfoUri = selected.firstOrNull()?.uri
            } else {
              multiSelectionInfo = Triple(
                selected.size,
                selected.sumOf { it.size },
                selected.sumOf { it.duration },
              )
            }
          },
          onPlayClick = { selectionManager.playSelected() },
          selectionOverflowActions = buildList {
            add(
              SelectionOverflowAction(
                icon = Icons.Filled.PictureInPictureAlt,
                label = stringResource(R.string.open_with_mini_player),
                onClick = { selectionManager.playSelectedInMiniPlayer() },
              )
            )
            add(
              SelectionOverflowAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.generic_share),
                onClick = { selectionManager.shareSelected() },
              )
            )
            add(
              SelectionOverflowAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.web_share_title),
                onClick = { showWebShareSheet = true },
              )
            )
            val selectedVideos = selectionManager.getSelectedItems()
            if (selectedVideos.isNotEmpty()) {
              add(
                SelectionOverflowAction(
                  icon = Icons.Filled.ContentCopy,
                  label = stringResource(R.string.copy_video_path),
                  onClick = {
                    val paths = selectedVideos.joinToString("\n") { it.path }
                    clipboardManager.setText(AnnotatedString(paths))
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                  }
                )
              )
            }
            // Pin / unpin selected media (shared across tree, folder and library views).
            val pinPaths = selectedVideos.map { it.path.ifBlank { it.uri.toString() } }.filter { it.isNotBlank() }
            if (pinPaths.isNotEmpty()) {
              val allPinned = pinPaths.all { it in mediaPins.toSet() }
              add(
                SelectionOverflowAction(
                  icon = Icons.Filled.PushPin,
                  label = stringResource(if (allPinned) R.string.unpin_from_top else R.string.pin_to_top),
                  onClick = {
                    coroutineScope.launch {
                      if (allPinned) {
                        pinnedItemRepository.unpinAll(xyz.mpv.rex.domain.pinned.PinScope.MEDIA, pinPaths)
                      } else {
                        pinnedItemRepository.pinAll(xyz.mpv.rex.domain.pinned.PinScope.MEDIA, pinPaths)
                      }
                      selectionManager.clear()
                    }
                  },
                )
              )
            }
          },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
        )
      }
    },
    floatingActionButton = {
      if (!hidePlayFabShowInHeader && !selectionManager.isInSelectionMode && isFabVisible.value && sortedVideosWithInfo.isNotEmpty()) {
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
                visible = true,
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

                            if (lastPlayed != null && sortedVideosWithInfo.any { it.video.path == lastPlayed.filePath }) {
                              MediaUtils.playFile(lastPlayed.filePath, context, "media_library_list")
                            } else {
                              MediaUtils.playFile(sortedVideosWithInfo.first().video, context, "media_library_list")
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
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                val lastPlayed = recentlyPlayedVideos.firstOrNull()
                if (lastPlayed != null) {
                  MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                } else {
                  Toast.makeText(context, context.getString(R.string.no_recently_played_videos), Toast.LENGTH_SHORT).show()
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
    }
  ) { padding ->
    val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
    val videosWereDeletedOrMoved = false

    val displayVideos = if (isSearching) {
      if (searchQuery.isBlank()) {
        emptyList()
      } else {
        sortedVideosWithInfo.filter { 
          it.video.displayName.contains(searchQuery, ignoreCase = true) 
        }
      }
    } else {
      sortedVideosWithInfo
    }

    Box(modifier = Modifier.fillMaxSize()) {
      VideoListContent(
        folderId = "media_library",
        videosWithInfo = displayVideos,
        isLoading = isLoading && videos.isEmpty(),
        uiSettings = uiSettings,
        isRefreshing = isRefreshing,
        recentlyPlayedFilePath = recentlyPlayedFilePath,
        recentlyPlayedFilePaths = recentlyPlayedFilePaths,
        videosWereDeletedOrMoved = videosWereDeletedOrMoved,
        autoScrollToLastPlayed = autoScrollToLastPlayed,
        onRefresh = { viewModel.refresh() },
        selectionManager = selectionManager,
        onVideoClick = { video ->
          if (selectionManager.isInSelectionMode) {
            selectionManager.toggle(video)
          } else {
            MediaUtils.playFile(video, context, "media_library_list")
          }
        },
        onVideoLongClick = { video -> selectionManager.handleLongClick(video) },
        isFabVisible = isFabVisible,
        // The Scaffold's padding includes the navigation-bar inset, and
        // VideoListContent's ContentSurfaceContainer adds it again on top of its
        // own 14.dp gap — which left a double-height empty band under the sheet.
        // Drop only the bottom here; the container owns the bottom inset.
        modifier = Modifier.padding(
          PaddingValues(
            start = padding.calculateStartPadding(LocalLayoutDirection.current),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = 0.dp,
          )
        ),
        showFloatingBottomBar = showFloatingBottomBar,
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        searchQuery = if (isSearching) searchQuery else null,
        pinnedPaths = mediaPins,
      )

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
          onCopyClick = { /* N/A */ },
          onMoveClick = { /* N/A */ },
          onRenameClick = { renameDialogOpen.value = true },
          onDeleteClick = { deleteDialogOpen.value = true },
          onAddToPlaylistClick = { addToPlaylistDialogOpen.value = true },
          onMarkAsClick = { showMarkAsSheet = true },
          showCopy = false,
          showMove = false,
          showAddToPlaylist = true
        )
      }
    }

    if (sortDialogOpen.value) {
      VideoSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        onSortTypeChange = { browserPreferences.videoSortType.set(it) },
        onSortOrderChange = { browserPreferences.videoSortOrder.set(it) }
      )
    }

    if (deleteDialogOpen.value) {
      DeleteConfirmationDialog(
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = {
          selectionManager.deleteSelected()
          deleteDialogOpen.value = false
        },
        itemCount = selectionManager.selectedCount,
        isOpen = deleteDialogOpen.value,
        itemTypePluralRes = R.plurals.item_type_video_plural
      )
    }

    if (renameDialogOpen.value) {
      val video = selectionManager.getSelectedItems().firstOrNull()
      if (video != null) {
        RenameDialog(
          onDismiss = { renameDialogOpen.value = false },
          onConfirm = { newName ->
            selectionManager.renameSelected(newName)
            renameDialogOpen.value = false
          },
          currentName = video.displayName,
          isOpen = renameDialogOpen.value,
          itemTypeRes = R.string.item_type_video
        )
      }
    }

    if (showMarkAsSheet) {
      MarkAsBottomSheet(
        onDismiss = { showMarkAsSheet = false },
        onMarkAs = { state ->
          val selected = selectionManager.getSelectedItems()
          coroutineScope.launch {
            selected.forEach { video ->
              RecentlyPlayedOps.markAs(
                filePath = video.path,
                fileName = video.displayName,
                duration = video.duration,
                state = state,
              )
            }
            viewModel.refresh()
          }
        },
      )
    }

    PlayLinkSheet(
      isOpen = showLinkDialog.value,
      onDismiss = { showLinkDialog.value = false },
      onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
    )

    AddToPlaylistDialog(
      isOpen = addToPlaylistDialogOpen.value,
      videos = selectionManager.getSelectedItems(),
      onDismiss = { addToPlaylistDialogOpen.value = false },
      onSuccess = {
        selectionManager.clear()
        viewModel.refresh()
      },
    )

    mediaInfoUri?.let { uri ->
      xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet(uri = uri, onDismiss = { mediaInfoUri = null })
    }
    multiSelectionInfo?.let { (count, bytes, duration) ->
      xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet(count = count, totalBytes = bytes, totalDurationMs = duration, onDismiss = { multiSelectionInfo = null })
    }

    if (showWebShareSheet) {
      xyz.mpv.rex.feature.webshare.WebShareSheet(
        videos = selectionManager.getSelectedItems(),
        onDismiss = { showWebShareSheet = false }
      )
    }
  }
}
