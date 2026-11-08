package xyz.mpv.rex.ui.browser.videolist

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import xyz.mpv.rex.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderViewMode
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.preferences.VideoSortType
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserBottomBar
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.ContentSurfaceContainer
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.FileOperationProgressDialog
import xyz.mpv.rex.ui.browser.dialogs.FolderPickerDialog
import xyz.mpv.rex.ui.browser.dialogs.VideoSortDialog
import xyz.mpv.rex.ui.browser.dialogs.LoadingDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet
import xyz.mpv.rex.ui.browser.fab.FabScrollHelper
import xyz.mpv.rex.ui.browser.selection.SelectionManager
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.ui.browser.sheets.MarkAsBottomSheet
import xyz.mpv.rex.utils.history.MarkAsState
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.CopyPasteOps
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.sort.PinnedSortUtils
import xyz.mpv.rex.utils.sort.SortUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt


@Serializable
data class VideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backstack = LocalBackStack.current
    val clipboardManager = LocalClipboardManager.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val playerPreferences = koinInject<PlayerPreferences>()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

    // ViewModel
    val viewModel: VideoListViewModel =
      viewModel(
        key = "VideoListViewModel_$bucketId",
        factory = VideoListViewModel.factory(context.applicationContext as android.app.Application, bucketId),
      )
    val videos by viewModel.videos.collectAsState()
    val videosWithPlaybackInfo by viewModel.videosWithPlaybackInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val recentlyPlayedPaths by viewModel.recentlyPlayedPaths.collectAsState()
    val recentlyPlayedFilePaths by viewModel.recentlyPlayedFilePaths.collectAsState()
    val lastPlayedInFolderPath by viewModel.lastPlayedInFolderPath.collectAsState()
    val playlistMode by playerPreferences.playlistMode.collectAsState()
    val videosWereDeletedOrMoved by viewModel.videosWereDeletedOrMoved.collectAsState()

    // Sorting
    val videoSortType by browserPreferences.videoSortType.collectAsState()
    val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()

    // Media pins are shared across tree / video list / media library views.
    val pinnedItemRepository = koinInject<xyz.mpv.rex.domain.pinned.PinnedItemRepository>()
    val mediaPins by
      pinnedItemRepository.pinnedPaths(xyz.mpv.rex.domain.pinned.PinScope.MEDIA)
        .collectAsState(initial = pinnedItemRepository.getPinnedPaths(xyz.mpv.rex.domain.pinned.PinScope.MEDIA))

    val sortedVideosWithInfo =
      remember(videosWithPlaybackInfo, videoSortType, videoSortOrder, mediaPins) {
        val infoById = videosWithPlaybackInfo.associateBy { it.video.id }
        val sortedVideos = SortUtils.sortVideos(videosWithPlaybackInfo.map { it.video }, videoSortType, videoSortOrder)
        // Maintain the playback info mapping — O(1) lookup per item
        val mapped = sortedVideos.map { video ->
          infoById[video.id] ?: VideoWithPlaybackInfo(video)
        }
        // Pinned media first, in pin order, then the regular sort.
        PinnedSortUtils.sortPinnedFirst(mapped, mediaPins) { it.video.path.ifBlank { it.video.uri.toString() } }
      }

    // Selection manager
    val selectionManager =
      rememberSelectionManager(
        items = sortedVideosWithInfo.map { it.video },
        getId = { it.id },
        onDeleteItems = { items, _ -> viewModel.deleteVideos(items) },
        onRenameItem = { video, newName -> viewModel.renameVideo(video, newName) },
        onOperationComplete = { viewModel.refresh() },
      )

    // UI State
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
    val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }

    // Copy/Move state
    val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
    val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
    val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
    val operationProgress by CopyPasteOps.operationProgress.collectAsState()
    val treePickerLauncher =
      rememberLauncherForActivityResult(OpenDocumentTreeContract()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selectedVideos = selectionManager.getSelectedItems()
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
            is CopyPasteOps.OperationType.Copy -> {
              CopyPasteOps.copyFilesToTreeUri(context, selectedVideos, uri)
            }

            is CopyPasteOps.OperationType.Move -> {
              CopyPasteOps.moveFilesToTreeUri(context, selectedVideos, uri)
            }

            else -> {}
          }
        }
      }

    // Private space state
    val movingToPrivateSpace = rememberSaveable { mutableStateOf(false) }
    val showPrivateSpaceCompletionDialog = rememberSaveable { mutableStateOf(false) }
    val privateSpaceMovedCount = remember { mutableIntStateOf(0) }

    val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName
    var mediaInfoUri by remember { mutableStateOf<Uri?>(null) }
    var multiSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }

    // FAB visibility state
    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }

    val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val appearancePreferences = koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
    val hidePlayFabShowInHeader by appearancePreferences.hidePlayFabShowInHeader.collectAsState()

    // UI state - use standalone states to avoid scroll issues with predictive back gesture
    val rememberedListIndex = rememberSaveable { mutableIntStateOf(0) }
    val rememberedListOffset = rememberSaveable { mutableIntStateOf(0) }
    val rememberedGridIndex = rememberSaveable { mutableIntStateOf(0) }
    val rememberedGridOffset = rememberSaveable { mutableIntStateOf(0) }
    val hasListAutoScrolled = rememberSaveable(inputs = arrayOf(lastPlayedInFolderPath ?: "")) { mutableStateOf(false) }
    val hasGridAutoScrolled = rememberSaveable(inputs = arrayOf(lastPlayedInFolderPath ?: "")) { mutableStateOf(false) }

    val initialListIndex = if (rememberedListIndex.intValue > 0) {
      rememberedListIndex.intValue
    } else if (autoScrollToLastPlayed && !hasListAutoScrolled.value && lastPlayedInFolderPath != null && sortedVideosWithInfo.isNotEmpty()) {
      var foundIndex = 0
      for (i in sortedVideosWithInfo.indices) {
        if (sortedVideosWithInfo[i].video.path == lastPlayedInFolderPath) {
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
    } else if (autoScrollToLastPlayed && !hasGridAutoScrolled.value && lastPlayedInFolderPath != null && sortedVideosWithInfo.isNotEmpty()) {
      var foundIndex = 0
      for (i in sortedVideosWithInfo.indices) {
        if (sortedVideosWithInfo[i].video.path == lastPlayedInFolderPath) {
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

    // Track scroll for FAB visibility
    xyz.mpv.rex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = false,
      onExpandedChange = { },
    )

    // Bottom bar animation state
    var showFloatingBottomBar by remember { mutableStateOf(false) }
    var showMarkAsSheet by remember { mutableStateOf(false) }
    var showWebShareSheet by remember { mutableStateOf(false) }
    val animationDuration = 300

    // Handle selection mode changes with animation
    LaunchedEffect(selectionManager.isInSelectionMode) {
      if (selectionManager.isInSelectionMode) {
        // Entering selection mode: Show floating bar immediately
        showFloatingBottomBar = true
      } else {
        // Exiting selection mode: Hide floating bar
        showFloatingBottomBar = false
      }
    }

    // Predictive back: Only intercept when in selection mode
    BackHandler(enabled = selectionManager.isInSelectionMode) {
      selectionManager.clear()
    }

    // Listen for lifecycle resume events and refresh videos when coming into focus
    DisposableEffect(lifecycleOwner) {
      val observer =
        LifecycleEventObserver { _, event ->
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
      // Pushed screens render in MainActivity's root Box, outside
      // MainScreen's Scaffold, so they must set the screen tone themselves.
      // surfaceContainer is what shows through the 3.dp card gaps and around
      // the content surface's 24.dp corners; the default `background` is too
      // close to the cards' `surface` fill for either to be visible.
      containerColor = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Black) {
        androidx.compose.ui.graphics.Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
      topBar = {
        BrowserTopBar(
          title = displayFolderName,
          scrollBehavior = scrollBehavior,
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = sortedVideosWithInfo.size,
          onBackClick = {
            if (selectionManager.isInSelectionMode) {
              selectionManager.clear()
            } else {
              backstack.navigateBack()
            }
          },
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = {
            backstack.add(
              xyz.mpv.rex.ui.browser.search.SearchScreen(
                initialPath = bucketId,
                initialFolderName = displayFolderName,
              )
            )
          },
          onSettingsClick = {
            backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
          normalPlayClick = if (hidePlayFabShowInHeader && sortedVideosWithInfo.isNotEmpty()) {
            {
              coroutineScope.launch {
                val folderPath = sortedVideosWithInfo.firstOrNull()?.video?.path?.let { File(it).parent } ?: ""
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 100)
                val lastPlayedInFolder = recentlyPlayedVideos.firstOrNull {
                  File(it.filePath).parent == folderPath
                }
                if (lastPlayedInFolder != null) {
                  MediaUtils.playFile(lastPlayedInFolder.filePath, context, "recently_played_button")
                } else {
                  MediaUtils.playFile(sortedVideosWithInfo.first().video, context, "first_video_button")
                }
              }
            }
          } else null,
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
      },
      floatingActionButton = {
        val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        val miniPlayerStateManager = koinInject<xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager>()
        val miniPlayerState by miniPlayerStateManager.state.collectAsState()
        if (!hidePlayFabShowInHeader && sortedVideosWithInfo.isNotEmpty()) {
          TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(R.string.play_recently_played_or_first)) } },
            state = rememberTooltipState(),
          ) {
            FloatingActionButtonMenu(
              modifier = Modifier.padding(bottom = if (miniPlayerState.isPlaybackActive) navigationBarHeight + 8.dp else (navigationBarHeight - 30.dp).coerceAtLeast(0.dp)),
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
                                  val folderPath = sortedVideosWithInfo.firstOrNull()?.video?.path?.let { File(it).parent } ?: ""
                                  val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 100)
                                  val lastPlayedInFolder = recentlyPlayedVideos.firstOrNull {
                                    File(it.filePath).parent == folderPath
                                  }
                                  if (lastPlayedInFolder != null) {
                                    MediaUtils.playFile(lastPlayedInFolder.filePath, context, "recently_played_button")
                                  } else {
                                    MediaUtils.playFile(sortedVideosWithInfo.first().video, context, "first_video_button")
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
      }
    ) { padding ->
      val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
      
      Box(modifier = Modifier.fillMaxSize()) {
        VideoListContent(
          folderId = bucketId,
          videosWithInfo = sortedVideosWithInfo,
          isLoading = isLoading && videos.isEmpty(),
          uiSettings = uiSettings,
          isRefreshing = isRefreshing,
          recentlyPlayedFilePath = lastPlayedInFolderPath ?: recentlyPlayedFilePath,
          recentlyPlayedFilePaths = recentlyPlayedFilePaths,
          recentlyPlayedPaths = recentlyPlayedPaths,
          videosWereDeletedOrMoved = videosWereDeletedOrMoved,
          autoScrollToLastPlayed = autoScrollToLastPlayed,
          onRefresh = { viewModel.refresh() },
          selectionManager = selectionManager,
          onVideoClick = { video ->
            if (selectionManager.isInSelectionMode) {
              selectionManager.toggle(video)
            } else {
              // Always use MediaUtils.playFile which lets PlayerActivity auto-generate playlist
              // This avoids TransactionTooLargeException from passing large playlists
              // PlayerActivity will auto-generate playlist from folder if playlistMode is enabled
              MediaUtils.playFile(video, context, "video_list")
            }
          },
          onVideoLongClick = { video -> selectionManager.handleLongClick(video) },
          isFabVisible = isFabVisible,
          // Top inset only: the content surface owns its own bottom inset.
          modifier = Modifier.padding(top = padding.calculateTopPadding()),
          showFloatingBottomBar = showFloatingBottomBar,
          sortType = videoSortType,
          sortOrder = videoSortOrder,
          listState = listState,
          gridState = gridState,
          pinnedPaths = mediaPins,
          headerScrollState = topAppBarState,
        )
        
        // Floating Material 3 Button Group overlay with animation
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
            showRename = selectionManager.isSingleSelection
          )
        }
      }

      // Sort Dialog
      VideoSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        onSortTypeChange = { browserPreferences.videoSortType.set(it) },
        onSortOrderChange = { browserPreferences.videoSortOrder.set(it) },
      )

      // Delete Dialog
      DeleteConfirmationDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemTypePluralRes = R.plurals.item_type_video_plural,
        itemCount = selectionManager.selectedCount,
        itemNames = selectionManager.getSelectedItems().map { it.displayName },
      )

      // Rename Dialog
      if (renameDialogOpen.value && selectionManager.isSingleSelection) {
        val video = selectionManager.getSelectedItems().firstOrNull()
        if (video != null) {
          val baseName = video.displayName.substringBeforeLast('.')
          val extension = "." + video.displayName.substringAfterLast('.', "")
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen.value = false },
            onConfirm = { newName -> selectionManager.renameSelected(newName) },
            currentName = baseName,
            itemTypeRes = R.string.item_type_file,
            extension = if (extension != ".") extension else null,
          )
        }
      }

      // Mark As Sheet
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

      // Folder Picker Dialog
      FolderPickerDialog(
        isOpen = folderPickerOpen.value,
        currentPath =
          videos.firstOrNull()?.let { File(it.path).parent }
            ?: Environment.getExternalStorageDirectory().absolutePath,
        onDismiss = { folderPickerOpen.value = false },
        onFolderSelected = { destinationPath ->
          folderPickerOpen.value = false
          val selectedVideos = selectionManager.getSelectedItems()
          if (selectedVideos.isNotEmpty() && operationType.value != null) {
            progressDialogOpen.value = true
            coroutineScope.launch {
              when (operationType.value) {
                is CopyPasteOps.OperationType.Copy -> {
                  CopyPasteOps.copyFiles(context, selectedVideos, destinationPath)
                }

                is CopyPasteOps.OperationType.Move -> {
                  CopyPasteOps.moveFiles(context, selectedVideos, destinationPath)
                }

                else -> {}
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
              operationProgress.error == null
            ) {
              viewModel.setVideosWereDeletedOrMoved()
            }
            operationType.value = null
            selectionManager.clear()
            viewModel.refresh()
          },
        )
      }

      // Private Space Loading Dialog
      LoadingDialog(
        isOpen = movingToPrivateSpace.value,
        message = stringResource(R.string.moving_to_private_space),
      )

      // Private Space Completion Dialog
      if (showPrivateSpaceCompletionDialog.value) {
        androidx.compose.material3.AlertDialog(
          onDismissRequest = { showPrivateSpaceCompletionDialog.value = false },
          title = {
            Text(
              text = stringResource(R.string.moved_to_private_space),
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          text = {
            Text(
              text = stringResource(R.string.moved_to_private_space_desc, privateSpaceMovedCount.intValue),
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          confirmButton = {
            androidx.compose.material3.Button(
              onClick = { showPrivateSpaceCompletionDialog.value = false },
            ) {
              Text(stringResource(R.string.close))
            }
          },
        )
      }

      // Add to Playlist Dialog
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
        MediaInfoSheet(uri = uri, onDismiss = { mediaInfoUri = null })
      }
      multiSelectionInfo?.let { (count, bytes, duration) ->
        MultiSelectionInfoSheet(count = count, totalBytes = bytes, totalDurationMs = duration, onDismiss = { multiSelectionInfo = null })
      }

      if (showWebShareSheet) {
        xyz.mpv.rex.feature.webshare.WebShareSheet(
          videos = selectionManager.getSelectedItems(),
          onDismiss = { showWebShareSheet = false }
        )
      }
    }
  }
}

@Composable
fun VideoListContent(
  folderId: String,
  videosWithInfo: List<VideoWithPlaybackInfo>,
  isLoading: Boolean,
  uiSettings: UiSettings,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  recentlyPlayedPaths: Set<String> = emptySet(),
  recentlyPlayedFilePaths: Set<String> = emptySet(),
  videosWereDeletedOrMoved: Boolean,
  autoScrollToLastPlayed: Boolean,
  onRefresh: suspend () -> Unit,
  selectionManager: SelectionManager<Video, Long>,
  onVideoClick: (Video) -> Unit,
  onVideoLongClick: (Video) -> Unit,
  isFabVisible: androidx.compose.runtime.MutableState<Boolean>,
  modifier: Modifier = Modifier,
  showFloatingBottomBar: Boolean = false,
  sortType: VideoSortType = VideoSortType.Title,
  sortOrder: SortOrder = SortOrder.Ascending,
  searchQuery: String? = null,
  listState: LazyListState = rememberLazyListState(),
  gridState: LazyGridState = rememberLazyGridState(),
  pinnedPaths: List<String> = emptyList(),
  headerScrollState: TopAppBarState? = null,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val density = androidx.compose.ui.platform.LocalDensity.current
  val thumbWidthDp = if (mediaLayoutMode == MediaLayoutMode.GRID) 160.dp else 128.dp
  val aspect = 16f / 9f
  val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = (thumbWidthPx / aspect).toInt()

  LaunchedEffect(folderId, showVideoThumbnails, videosWithInfo.size, thumbWidthPx, thumbHeightPx) {
    if (showVideoThumbnails && videosWithInfo.isNotEmpty()) {
      thumbnailRepository.startFolderThumbnailGeneration(
        folderId = folderId,
        videos = videosWithInfo.map { it.video },
        widthPx = thumbWidthPx,
        heightPx = thumbHeightPx,
      )
    }
  }

  ContentSurfaceContainer(
    navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current,
    modifier = modifier,
  ) {
    UnifiedExplorerContent(
      items = videosWithInfo,
      isLoading = isLoading,
      uiSettings = uiSettings,
      isSelected = { selectionManager.isSelected(it.video) },
      onClick = { onVideoClick(it.video) },
      onLongClick = { onVideoLongClick(it.video) },
      onToggleSelection = { selectionManager.toggle(it.video) },
      emptyTitle = if (searchQuery != null) {
        if (searchQuery.isBlank()) stringResource(R.string.search_empty_title) else stringResource(R.string.search_no_results_title)
      } else {
        stringResource(R.string.no_videos_in_folder)
      },
      emptyMessage = if (searchQuery != null) {
        if (searchQuery.isBlank()) stringResource(R.string.search_empty_message) else stringResource(R.string.search_no_results_message)
      } else {
        stringResource(R.string.no_videos_in_folder_desc)
      },
      emptyIcon = if (searchQuery != null) Icons.Filled.Search else Icons.Filled.VideoLibrary,
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      headerScrollState = headerScrollState,
      isInSelectionMode = selectionManager.isInSelectionMode,
      recentlyPlayedFilePath = recentlyPlayedFilePath,
      recentlyPlayedFilePaths = recentlyPlayedFilePaths,
      recentlyPlayedPaths = recentlyPlayedPaths,
      autoScrollToLastPlayed = autoScrollToLastPlayed,
      scrollTriggerKey = "${sortType.name}:${sortOrder.name}",
      listState = listState,
      gridState = gridState,
      pinnedPaths = pinnedPaths,
      useContentSurface = true,
    )
  }
}

