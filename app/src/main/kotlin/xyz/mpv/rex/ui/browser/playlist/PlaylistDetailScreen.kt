package xyz.mpv.rex.ui.browser.playlist

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.M3UVideoCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.UnifiedExplorerContent
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.MediaInfoOps
import xyz.mpv.rex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Playlist detail screen showing videos in a playlist.
 *
 * **M3U Playlist Behavior:**
 * M3U playlists (streaming URLs) are handled differently to prevent ANR issues:
 * - Each stream is played individually (no playlist navigation in PlayerActivity)
 * - No next/previous buttons - each stream URL is opened standalone
 * - This prevents loading thousands of URLs into memory at once
 * - Users can manually select and play different streams from the list
 *
 * **Regular Playlist Behavior:**
 * Local file playlists support full playlist navigation:
 * - Next/previous buttons available during playback
 * - Playlist continuation and shuffle modes
 * - Full playlist loaded into PlayerActivity
 */
@Serializable
data class PlaylistDetailScreen(val playlistId: Int) : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<PlaylistRepository>()
    val backStack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel
    val viewModel: PlaylistDetailViewModel =
      viewModel(
        key = "PlaylistDetailViewModel_$playlistId",
        factory = PlaylistDetailViewModel.factory(
          context.applicationContext as android.app.Application,
          playlistId,
        ),
      )

    val playlist by viewModel.playlist.collectAsState()
    val isAutoPlaylist = playlistId < 0
    val isRecentlyAdded = playlistId == PlaylistViewModel.ID_RECENTLY_ADDED
    val videoItems by viewModel.videoItems.collectAsState()
    val videos = videoItems.map { it.video }
    val isLoading by viewModel.isLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val isRefreshing = remember { mutableStateOf(false) }

    var mediaInfoUri by remember { mutableStateOf<Uri?>(null) }
    var multiSelectionInfo by remember { mutableStateOf<Triple<Int, Long, Long>?>(null) }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Filter video items based on search query
    val filteredVideoItems = if (isSearching && searchQuery.isNotBlank()) {
      videoItems.filter { item ->
        item.video.displayName.contains(searchQuery, ignoreCase = true) ||
          item.video.path.contains(searchQuery, ignoreCase = true)
      }
    } else {
      videoItems
    }
    val filteredVideos = filteredVideoItems.map { it.video }

    // Request focus when search is activated
    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    // Selection manager - use playlist item ID as unique key, work with filtered items
    val selectionManager =
      rememberSelectionManager(
        items = filteredVideoItems,
        getId = { it.playlistItem.id },
        onDeleteItems = { itemsToDelete, _ ->
          if (isRecentlyAdded) {
            viewModel.deleteVideos(itemsToDelete.map { it.video })
          } else {
            viewModel.removeVideosFromPlaylist(itemsToDelete)
            Pair(itemsToDelete.size, 0)
          }
        },
        onOperationComplete = { viewModel.refresh() },
      )

    // UI State
    val listState = rememberLazyListState()
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val mediaInfoDialogOpen = rememberSaveable { mutableStateOf(false) }
    val selectedVideo = remember { mutableStateOf<Video?>(null) }
    val mediaInfoData = remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }
    val mediaInfoLoading = remember { mutableStateOf(false) }
    val mediaInfoError = remember { mutableStateOf<String?>(null) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var urlDialogContent by remember { mutableStateOf("") }

    // Reorder mode state
    var isReorderMode by rememberSaveable { mutableStateOf(false) }

    // Predictive back: Intercept when in selection mode, reorder mode, or searching
    BackHandler(enabled = selectionManager.isInSelectionMode || isReorderMode || isSearching) {
      when {
        isReorderMode -> isReorderMode = false
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }
        selectionManager.isInSelectionMode -> selectionManager.clear()
      }
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                placeholder = { Text(stringResource(R.string.search_videos)) },
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
            title = playlist?.name ?: "Playlist",
            scrollBehavior = scrollBehavior,
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = videos.size,
            onBackClick = {
              when {
                isReorderMode -> isReorderMode = false
                selectionManager.isInSelectionMode -> selectionManager.clear()
                else -> backStack.navigateBack()
              }
            },
            onCancelSelection = { selectionManager.clear() },
            isSingleSelection = selectionManager.isSingleSelection,
            useRemoveIcon = !isRecentlyAdded, // Show remove icon for regular playlists, delete icon for recently added
            deleteBeforeInfo = true,
            onInfoClick = {
              val selected = selectionManager.getSelectedItems()
              if (selectionManager.isSingleSelection) {
                val item = selected.firstOrNull()
                if (item != null) {
                  if (playlist?.isM3uPlaylist == true) {
                    urlDialogContent = item.video.path
                    showUrlDialog = true
                  } else {
                    mediaInfoUri = item.video.uri
                  }
                }
              } else {
                multiSelectionInfo = Triple(
                  selected.size,
                  selected.sumOf { it.video.size },
                  selected.sumOf { it.video.duration },
                )
              }
            },
            onPlayClick = null, // Don't show play icon in selection mode for playlist
            selectionOverflowActions = buildList {
              add(SelectionOverflowAction(
                icon = Icons.Filled.PictureInPictureAlt,
                label = stringResource(R.string.open_with_mini_player),
                onClick = {
                  MediaUtils.playInMiniPlayer(selectionManager.getSelectedItems().map { it.video })
                  selectionManager.clear()
                },
              ))
              if (playlist?.isM3uPlaylist != true) {
                add(SelectionOverflowAction(
                  icon = Icons.Filled.Share,
                  label = "Share",
                  onClick = {
                    val videosToShare = selectionManager.getSelectedItems().map { it.video }
                    MediaUtils.shareVideos(context, videosToShare)
                  },
                ))
              }
              if (!isAutoPlaylist && selectionManager.isSingleSelection) {
                val selected = selectionManager.getSelectedItems().firstOrNull()
                val isCurrentThumbnail = selected?.video?.path != null && selected.video.path == playlist?.customThumbnailPath
                if (isCurrentThumbnail) {
                  add(SelectionOverflowAction(
                    icon = Icons.Filled.Image,
                    label = "Reset playlist thumbnail",
                    onClick = {
                      coroutineScope.launch {
                        viewModel.setPlaylistThumbnail(null)
                        Toast.makeText(context, "Reset thumbnail to first video", Toast.LENGTH_SHORT).show()
                        selectionManager.clear()
                      }
                    },
                  ))
                } else {
                  add(SelectionOverflowAction(
                    icon = Icons.Filled.Image,
                    label = "Set as playlist thumbnail",
                    onClick = {
                      if (selected != null) {
                        coroutineScope.launch {
                          viewModel.setPlaylistThumbnail(selected.video.path)
                          Toast.makeText(context, "Playlist thumbnail updated", Toast.LENGTH_SHORT).show()
                          selectionManager.clear()
                        }
                      }
                    },
                  ))
                }
              }
            },
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
            onDeleteClick = if (isAutoPlaylist && !isRecentlyAdded) null else ({ deleteDialogOpen.value = true }),
            additionalActions = {
              when {
                // Show done button when in reorder mode
                isReorderMode -> {
                  IconButton(
                    onClick = { isReorderMode = false },
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = stringResource(R.string.done_reordering),
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
                // Show reorder button and play button when not in selection mode
                !selectionManager.isInSelectionMode && videos.isNotEmpty() -> {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    // Search button
                    IconButton(
                      onClick = { isSearching = true },
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_videos),
                        tint = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Reorder button (hide for M3U playlists and auto playlists)
                    if (playlist?.isM3uPlaylist != true && !isAutoPlaylist) {
                      IconButton(
                        onClick = { isReorderMode = true },
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.SwapVert,
                          contentDescription = stringResource(R.string.reorder_playlist),
                          tint = MaterialTheme.colorScheme.onSurface,
                        )
                      }
                      Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Play button
                    Button(
                      onClick = {
                        if (playlist?.isM3uPlaylist == true) {
                          // M3U playlists: Play only the first/most recent stream (no playlist navigation)
                          val mostRecentlyPlayedItem = videoItems
                            .filter { it.playlistItem.lastPlayedAt > 0 }
                            .maxByOrNull { it.playlistItem.lastPlayedAt }

                          val itemToPlay = mostRecentlyPlayedItem ?: videoItems.firstOrNull()

                          if (itemToPlay != null) {
                            coroutineScope.launch {
                              viewModel.updatePlayHistory(itemToPlay.video.path)
                            }

                            // Play single stream URL without playlist
                            MediaUtils.playFile(itemToPlay.video, context, "m3u_playlist")
                          }
                        } else {
                          // Regular playlists: Play with full playlist navigation
                          val mostRecentlyPlayedItem = videoItems
                            .filter { it.playlistItem.lastPlayedAt > 0 }
                            .maxByOrNull { it.playlistItem.lastPlayedAt }

                          val startIndex = if (mostRecentlyPlayedItem != null) {
                            videoItems.indexOfFirst { it.playlistItem.id == mostRecentlyPlayedItem.playlistItem.id }
                          } else {
                            0
                          }

                          if (videos.isNotEmpty() && startIndex >= 0) {
                            coroutineScope.launch {
                              viewModel.updatePlayHistory(videos[startIndex].path)
                            }

                            MediaUtils.playPlaylist(
                              videos = videos,
                              startIndex = startIndex,
                              context = context,
                              launchSource = if (isAutoPlaylist) "auto_playlist" else "playlist",
                              playlistId = if (isAutoPlaylist) null else playlistId
                            )
                          }
                        }
                      },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      ),
                      shape = MaterialTheme.shapes.large,
                      modifier = Modifier.padding(end = 20.dp),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.PlayArrow,
                          contentDescription = null,
                          modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = "Play",
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                      }
                    }
                  }
                }
              }
            },
          )
        }
      },
      floatingActionButton = { },
    ) { padding ->
      // Show "no results" message when searching with no results
      if (isSearching && filteredVideoItems.isEmpty() && searchQuery.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = null,
              modifier = Modifier.size(64.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "No videos found",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Try a different search term",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        val pullToRefreshEnabled =
          !selectionManager.isInSelectionMode && !isReorderMode && !isSearching

        PullRefreshBox(
          isRefreshing = isRefreshing,
          enabled = pullToRefreshEnabled,
          listState = listState,
          modifier = Modifier.fillMaxSize().padding(padding),
          onRefresh = {
            val isM3uPlaylist = playlist?.isM3uPlaylist == true
            if (isM3uPlaylist) {
              val result = viewModel.refreshM3UPlaylist()
              result
                .onSuccess {
                  Toast.makeText(context, "Playlist refreshed successfully", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                  Toast.makeText(context, "Failed to refresh: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } else {
              viewModel.refreshNow()
            }
          },
        ) {
          PlaylistVideoListContent(
            videoItems = filteredVideoItems,
            isLoading = isLoading && videoItems.isEmpty(),
            uiSettings = uiSettings,
            selectionManager = selectionManager,
            isM3uPlaylist = playlist?.isM3uPlaylist == true,
            isReorderMode = isReorderMode,
            onReorder = { fromIndex, toIndex ->
              coroutineScope.launch {
                viewModel.reorderPlaylistItems(fromIndex, toIndex)
              }
            },
            onVideoItemClick = { item ->
              if (selectionManager.isInSelectionMode) {
                selectionManager.toggle(item)
              } else {
                coroutineScope.launch {
                  viewModel.updatePlayHistory(item.video.path)
                }

                val startIndex = videoItems.indexOfFirst { it.playlistItem.id == item.playlistItem.id }
                if (startIndex >= 0) {
                  if (videos.size == 1) {
                    MediaUtils.playFile(item.video, context, "playlist_detail")
                  } else {
                    MediaUtils.playPlaylist(
                      videos = videos,
                      startIndex = startIndex,
                      context = context,
                      launchSource = if (isAutoPlaylist) "auto_playlist" else "playlist",
                      playlistId = if (isAutoPlaylist) null else playlistId
                    )
                  }
                } else {
                  MediaUtils.playFile(item.video, context, "playlist_detail")
                }
              }
            },
            onVideoItemLongClick = { item ->
              selectionManager.handleLongClick(item)
            },
            listState = listState,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // Dialogs
      if (isRecentlyAdded) {
        DeleteConfirmationDialog(
          isOpen = deleteDialogOpen.value,
          onDismiss = { deleteDialogOpen.value = false },
          onConfirm = { selectionManager.deleteSelected() },
          itemTypePluralRes = R.plurals.item_type_video_plural,
          itemCount = selectionManager.selectedCount,
          itemNames = selectionManager.getSelectedItems().map { it.video.displayName },
        )
      } else {
        RemoveFromPlaylistDialog(
          isOpen = deleteDialogOpen.value,
          onDismiss = { deleteDialogOpen.value = false },
          onConfirm = { selectionManager.deleteSelected() },
          itemCount = selectionManager.selectedCount,
        )
      }

      // URL Dialog for M3U streams
      if (showUrlDialog) {
        StreamUrlDialog(
          url = urlDialogContent,
          onDismiss = { showUrlDialog = false },
          onCopy = {
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(context.getString(R.string.stream_url), urlDialogContent)
            clipboardManager.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
          }
        )
      }

      mediaInfoUri?.let { uri ->
        xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet(uri = uri, onDismiss = { mediaInfoUri = null })
      }
      multiSelectionInfo?.let { (count, bytes, duration) ->
        xyz.mpv.rex.ui.browser.sheets.MultiSelectionInfoSheet(count = count, totalBytes = bytes, totalDurationMs = duration, onDismiss = { multiSelectionInfo = null })
      }
    }
  }
}

@Composable
private fun PlaylistVideoListContent(
  videoItems: List<PlaylistVideoItem>,
  isLoading: Boolean,
  uiSettings: UiSettings,
  selectionManager: xyz.mpv.rex.ui.browser.selection.SelectionManager<PlaylistVideoItem, Int>,
  isReorderMode: Boolean,
  onReorder: (Int, Int) -> Unit,
  onVideoItemClick: (PlaylistVideoItem) -> Unit,
  onVideoItemLongClick: (PlaylistVideoItem) -> Unit,
  listState: androidx.compose.foundation.lazy.LazyListState,
  modifier: Modifier = Modifier,
  isM3uPlaylist: Boolean = false,
) {
  UnifiedExplorerContent(
    items = videoItems,
    isLoading = isLoading,
    uiSettings = uiSettings,
    isSelected = { selectionManager.isSelected(it) },
    onClick = onVideoItemClick,
    onLongClick = onVideoItemLongClick,
    onToggleSelection = { selectionManager.toggle(it) },
    modifier = modifier,
    emptyTitle = stringResource(R.string.no_videos_in_playlist),
    emptyMessage = stringResource(R.string.no_videos_in_playlist_desc),
    isInSelectionMode = selectionManager.isInSelectionMode,
    isReorderMode = isReorderMode,
    onReorder = onReorder,
    listState = listState
  )
}

@Composable
private fun StreamUrlDialog(
  url: String,
  onDismiss: () -> Unit,
  onCopy: () -> Unit,
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.stream_url)) },
    text = {
      Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
      )
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = {
          onCopy()
          onDismiss()
        }
      ) {
        Icon(
          imageVector = Icons.Filled.ContentCopy,
          contentDescription = null,
          modifier = Modifier.padding(end = 4.dp).size(18.dp)
        )
        Text(stringResource(R.string.copy))
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

@Composable
private fun RemoveFromPlaylistDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  itemCount: Int,
) {
  if (!isOpen) return

  val itemText = if (itemCount == 1) "video" else "videos"

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.remove_from_playlist, itemCount, itemText),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
      )
    },
    text = {
      androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
      ) {
        androidx.compose.material3.Card(
          colors =
            androidx.compose.material3.CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Text(
            text = stringResource(
              R.string.remove_from_playlist_confirm,
              itemText,
              if (itemCount == 1) "file" else "files"
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors =
          androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(
          text = stringResource(R.string.remove_from_playlist_action),
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(stringResource(R.string.generic_cancel), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
  return try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      cursor.moveToFirst()
      cursor.getString(nameIndex)
    } ?: uri.lastPathSegment
  } catch (e: Exception) {
    uri.lastPathSegment
  }
}
