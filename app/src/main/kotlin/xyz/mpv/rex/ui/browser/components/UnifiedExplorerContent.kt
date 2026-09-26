package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.ui.browser.playlist.PlaylistWithCount
import xyz.mpv.rex.ui.browser.playlist.PlaylistVideoItem
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedItem
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.ui.browser.cards.FolderCard
import xyz.mpv.rex.ui.browser.cards.PlaylistCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo
import xyz.mpv.rex.preferences.UiSettings
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import androidx.compose.foundation.layout.fillMaxHeight

val LocalLastPlayedVideoPathsInFolder = compositionLocalOf<Set<String>> { emptySet() }
val LocalRecentlyPlayedFilePaths = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun <T> UnifiedExplorerContent(
  items: List<T>,
  isLoading: Boolean,
  uiSettings: UiSettings,
  isSelected: (T) -> Boolean,
  onClick: (T) -> Unit,
  onLongClick: (T) -> Unit,
  onToggleSelection: (T) -> Unit,
  modifier: Modifier = Modifier,
  emptyTitle: String = "No items",
  emptyMessage: String = "This folder is empty",
  emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.VideoLibrary,
  onThumbClick: ((T) -> Unit)? = null,
  isRefreshing: MutableState<Boolean>? = null,
  onRefresh: (suspend () -> Unit)? = null,
  isInSelectionMode: Boolean = false,
  recentlyPlayedFilePath: String? = null,
  recentlyPlayedFilePaths: Set<String> = emptySet(),
  recentlyPlayedPaths: Set<String> = emptySet(),
  playedFolderPaths: Set<String> = emptySet(),
  newVideoIds: Set<Long> = emptySet(),
  watchedVideoIds: Set<Long> = emptySet(),
  videoPlaybackProgress: Map<Long, Float> = emptyMap(),
  autoScrollToLastPlayed: Boolean = false,
  scrollTriggerKey: Any? = null,
  gridColumns: Int? = null,
  showSections: Boolean = false,
  isReorderMode: Boolean = false,
  onReorder: ((Int, Int) -> Unit)? = null,
  listState: LazyListState? = null,
  gridState: LazyGridState? = null,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val gesturePreferences = koinInject<GesturePreferences>()

  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
  
  val columns = gridColumns ?: run {
    val isFolder = remember(items) {
      items.firstOrNull()?.let {
        it is VideoFolder || 
        it is FileSystemItem.Folder
      } ?: false
    }
    if (isFolder) {
      if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
    } else {
      if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
    }
  }

  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val navigationBarHeight = LocalNavigationBarHeight.current

  val animatedBottomPadding by animateDpAsState(
    targetValue = if (isInSelectionMode) {
      navigationBarHeight + 88.dp
    } else {
      navigationBarHeight + 16.dp
    },
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "explorerBottomContentPadding"
  )

  if (isLoading && items.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(bottom = 80.dp),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary,
      )
    }
  } else if (items.isEmpty()) {
    Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      EmptyState(
        icon = emptyIcon,
        title = emptyTitle,
        message = emptyMessage,
      )
    }
  } else {
    val safeItems = remember(items) {
      items.distinctBy { getItemId(it) }
    }
    val listState = listState ?: rememberLazyListState()
    val gridState = gridState ?: rememberLazyGridState()

    val lastPlayedVideoPathsInFolder = remember(safeItems, recentlyPlayedPaths, recentlyPlayedFilePaths) {
      val pathsInItems = safeItems.mapNotNull { item ->
        when (item) {
          is Video -> item.path
          is VideoWithPlaybackInfo -> item.video.path
          is RecentlyPlayedItem.VideoItem -> item.video.path
          is FileSystemItem.VideoFile -> item.video.path
          is PlaylistVideoItem -> item.video.path
          else -> null
        }
      }.toSet()
      val overallLastPlayed = pathsInItems.filter { it in recentlyPlayedFilePaths }.toSet()
      if (overallLastPlayed.isNotEmpty()) {
        overallLastPlayed
      } else {
        val fallbackPath = recentlyPlayedPaths.firstOrNull { it in pathsInItems }
        if (fallbackPath != null) setOf(fallbackPath) else emptySet()
      }
    }

    // Scroll to top whenever the caller changes the sort key, skipping the initial composition
    val isInitialTrigger = remember { mutableStateOf(true) }
    LaunchedEffect(scrollTriggerKey) {
      if (isInitialTrigger.value) {
        isInitialTrigger.value = false
        return@LaunchedEffect
      }
      if (mediaLayoutMode == MediaLayoutMode.GRID && !showSections) {
        gridState.scrollToItem(0)
      } else {
        listState.scrollToItem(0)
      }
    }

    val hasAutoScrolled = rememberSaveable(inputs = arrayOf(recentlyPlayedFilePath ?: "")) { mutableStateOf(false) }
    LaunchedEffect(recentlyPlayedFilePath, safeItems, autoScrollToLastPlayed) {
      if (autoScrollToLastPlayed && recentlyPlayedFilePath != null && safeItems.isNotEmpty() && !hasAutoScrolled.value) {
        val lastPlayedIndex = safeItems.indexOfFirst { item ->
          when (item) {
            is Video -> item.path == recentlyPlayedFilePath
            is VideoWithPlaybackInfo -> item.video.path == recentlyPlayedFilePath
            is RecentlyPlayedItem.VideoItem -> item.video.path == recentlyPlayedFilePath
            is FileSystemItem.VideoFile -> item.video.path == recentlyPlayedFilePath
            is VideoFolder -> {
              if (showSections) {
                recentlyPlayedFilePath.startsWith(item.path + "/") || recentlyPlayedFilePath == item.path || java.io.File(recentlyPlayedFilePath).parent == item.path
              } else {
                java.io.File(recentlyPlayedFilePath).parent == item.path
              }
            }
            is FileSystemItem.Folder -> {
              if (showSections) {
                recentlyPlayedFilePath.startsWith(item.path + "/") || recentlyPlayedFilePath == item.path || java.io.File(recentlyPlayedFilePath).parent == item.path
              } else {
                java.io.File(recentlyPlayedFilePath).parent == item.path
              }
            }
            else -> false
          }
        }
        if (lastPlayedIndex != -1) {
          hasAutoScrolled.value = true
          if (showSections) {
            val matchedItem = safeItems[lastPlayedIndex]
            val isFolder = matchedItem is VideoFolder || matchedItem is FileSystemItem.Folder
            val folderItems = safeItems.filter { it is VideoFolder || it is FileSystemItem.Folder }
            val videoItems = safeItems.filter { it is Video || it is VideoWithPlaybackInfo || it is FileSystemItem.VideoFile || it is RecentlyPlayedItem.VideoItem }

            val targetIndex = if (isFolder) {
              val folderIndex = folderItems.indexOf(matchedItem)
              if (folderIndex != -1) {
                if (mediaLayoutMode == MediaLayoutMode.GRID) {
                  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
                  1 + (folderIndex / folderGridColumns)
                } else {
                  1 + folderIndex
                }
              } else 0
            } else {
              val videoIndex = videoItems.indexOf(matchedItem)
              if (videoIndex != -1) {
                if (folderItems.isNotEmpty()) {
                  if (mediaLayoutMode == MediaLayoutMode.GRID) {
                    val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
                    val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
                    val numFolderRows = (folderItems.size + folderGridColumns - 1) / folderGridColumns
                    numFolderRows + 3 + (videoIndex / videoGridColumns)
                  } else {
                    folderItems.size + 3 + videoIndex
                  }
                } else {
                  if (mediaLayoutMode == MediaLayoutMode.GRID) {
                    val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
                    1 + (videoIndex / videoGridColumns)
                  } else {
                    1 + videoIndex
                  }
                }
              } else 0
            }
            listState.scrollToItem(targetIndex)
          } else {
            if (mediaLayoutMode == MediaLayoutMode.GRID) {
              gridState.scrollToItem(lastPlayedIndex)
            } else {
              listState.scrollToItem(lastPlayedIndex)
            }
          }
        }
      }
    }

    val contentBlock: @Composable BoxScope.() -> Unit = {
      if (showSections) {
        val folderItems = safeItems.filter { it is VideoFolder || it is FileSystemItem.Folder }
        val videoItems = safeItems.filter { it is Video || it is VideoWithPlaybackInfo || it is FileSystemItem.VideoFile || it is RecentlyPlayedItem.VideoItem }

        val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
        val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = animatedBottomPadding
          ),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          // --- FOLDERS SECTION ---
          if (folderItems.isNotEmpty()) {
            item(key = "folders_header") {
              SectionHeader(title = stringResource(R.string.folders_count, folderItems.size))
            }

            if (mediaLayoutMode == MediaLayoutMode.GRID) {
              val chunkedFolders = folderItems.chunked(folderGridColumns)
              itemsIndexed(
                items = chunkedFolders,
                key = { index, chunk -> "folder_row_${index}_${getItemId(chunk.first())}" }
              ) { _, rowItems ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                      val effectiveOnClick = {
                        if (isInSelectionMode) {
                          onToggleSelection(item)
                        } else {
                          onClick(item)
                        }
                      }
                      val effectiveOnThumbClick = if (onThumbClick != null) {
                        { onThumbClick(item) }
                      } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
                        { onToggleSelection(item) }
                      } else {
                        null
                      }

                      ExplorerItemCard(
                        item = item,
                        isSelected = isSelected(item),
                        showSubtitleIndicator = showSubtitleIndicator,
                        isGridMode = true,
                        columns = folderGridColumns,
                        uiSettings = uiSettings,
                        onClick = effectiveOnClick,
                        onLongClick = { onLongClick(item) },
                        onThumbClick = effectiveOnThumbClick,
                        recentlyPlayedFilePath = recentlyPlayedFilePath,
                        recentlyPlayedPaths = recentlyPlayedPaths,
                        playedFolderPaths = playedFolderPaths,
                        newVideoIds = newVideoIds,
                        watchedVideoIds = watchedVideoIds,
                        videoPlaybackProgress = videoPlaybackProgress,
                        showSections = showSections
                      )
                    }
                  }
                  val emptySlots = folderGridColumns - rowItems.size
                  repeat(emptySlots) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            } else {
              // List Mode
              items(
                items = folderItems,
                key = { getItemId(it) }
              ) { item ->
                val effectiveOnClick = {
                  if (isInSelectionMode) {
                    onToggleSelection(item)
                  } else {
                    onClick(item)
                  }
                }
                val effectiveOnThumbClick = if (onThumbClick != null) {
                  { onThumbClick(item) }
                } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
                  { onToggleSelection(item) }
                } else {
                  null
                }

                ExplorerItemCard(
                  item = item,
                  isSelected = isSelected(item),
                  showSubtitleIndicator = showSubtitleIndicator,
                  isGridMode = false,
                  columns = 1,
                  uiSettings = uiSettings,
                  onClick = effectiveOnClick,
                  onLongClick = { onLongClick(item) },
                  onThumbClick = effectiveOnThumbClick,
                  recentlyPlayedFilePath = recentlyPlayedFilePath,
                  recentlyPlayedPaths = recentlyPlayedPaths,
                  playedFolderPaths = playedFolderPaths,
                  newVideoIds = newVideoIds,
                  watchedVideoIds = watchedVideoIds,
                  videoPlaybackProgress = videoPlaybackProgress,
                  showSections = showSections
                )
              }
            }
          }

          // Section Spacer
          if (folderItems.isNotEmpty() && videoItems.isNotEmpty()) {
            item(key = "section_divider") {
              Spacer(modifier = Modifier.height(4.dp))
            }
          }

          // --- MEDIA SECTION ---
          if (videoItems.isNotEmpty()) {
            item(key = "media_header") {
              SectionHeader(title = stringResource(R.string.media_count, videoItems.size))
            }

            if (mediaLayoutMode == MediaLayoutMode.GRID) {
              val chunkedVideos = videoItems.chunked(videoGridColumns)
              itemsIndexed(
                items = chunkedVideos,
                key = { index, chunk -> "video_row_${index}_${getItemId(chunk.first())}" }
              ) { _, rowItems ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                      val effectiveOnClick = {
                        if (isInSelectionMode) {
                          onToggleSelection(item)
                        } else {
                          onClick(item)
                        }
                      }
                      val effectiveOnThumbClick = if (onThumbClick != null) {
                        { onThumbClick(item) }
                      } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
                        { onToggleSelection(item) }
                      } else {
                        null
                      }

                      ExplorerItemCard(
                        item = item,
                        isSelected = isSelected(item),
                        showSubtitleIndicator = showSubtitleIndicator,
                        isGridMode = true,
                        columns = videoGridColumns,
                        uiSettings = uiSettings,
                        onClick = effectiveOnClick,
                        onLongClick = { onLongClick(item) },
                        onThumbClick = effectiveOnThumbClick,
                        recentlyPlayedFilePath = recentlyPlayedFilePath,
                        recentlyPlayedPaths = recentlyPlayedPaths,
                        playedFolderPaths = playedFolderPaths,
                        newVideoIds = newVideoIds,
                        watchedVideoIds = watchedVideoIds,
                        videoPlaybackProgress = videoPlaybackProgress,
                        showSections = showSections
                      )
                    }
                  }
                  val emptySlots = videoGridColumns - rowItems.size
                  repeat(emptySlots) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            } else {
              // List Mode
              items(
                items = videoItems,
                key = { getItemId(it) }
              ) { item ->
                val effectiveOnClick = {
                  if (isInSelectionMode) {
                    onToggleSelection(item)
                  } else {
                    onClick(item)
                  }
                }
                val effectiveOnThumbClick = if (onThumbClick != null) {
                  { onThumbClick(item) }
                } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
                  { onToggleSelection(item) }
                } else {
                  null
                }

                ExplorerItemCard(
                  item = item,
                  isSelected = isSelected(item),
                  showSubtitleIndicator = showSubtitleIndicator,
                  isGridMode = false,
                  columns = 1,
                  uiSettings = uiSettings,
                  onClick = effectiveOnClick,
                  onLongClick = { onLongClick(item) },
                  onThumbClick = effectiveOnThumbClick,
                  recentlyPlayedFilePath = recentlyPlayedFilePath,
                  recentlyPlayedPaths = recentlyPlayedPaths,
                  playedFolderPaths = playedFolderPaths,
                  newVideoIds = newVideoIds,
                  watchedVideoIds = watchedVideoIds,
                  videoPlaybackProgress = videoPlaybackProgress,
                  showSections = showSections
                )
              }
            }
          }
        }
      } else if (mediaLayoutMode == MediaLayoutMode.GRID) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(columns),
          state = gridState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = animatedBottomPadding
          ),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(
            items = safeItems,
            key = { getItemId(it) }
          ) { item ->
            val effectiveOnClick = {
              if (isInSelectionMode) {
                onToggleSelection(item)
              } else {
                onClick(item)
              }
            }
            val effectiveOnThumbClick = if (onThumbClick != null) {
              { onThumbClick(item) }
            } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
              { onToggleSelection(item) }
            } else {
              null
            }

            ExplorerItemCard(
              item = item,
              isSelected = isSelected(item),
              showSubtitleIndicator = showSubtitleIndicator,
              isGridMode = true,
              columns = columns,
              uiSettings = uiSettings,
              onClick = effectiveOnClick,
              onLongClick = { onLongClick(item) },
              onThumbClick = effectiveOnThumbClick,
              recentlyPlayedFilePath = recentlyPlayedFilePath,
              recentlyPlayedPaths = recentlyPlayedPaths,
              playedFolderPaths = playedFolderPaths,
              newVideoIds = newVideoIds,
              watchedVideoIds = watchedVideoIds,
              videoPlaybackProgress = videoPlaybackProgress,
              showSections = showSections
            )
          }
        }
      } else {
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
          if (isReorderMode && onReorder != null) {
            onReorder(from.index, to.index)
          }
        }

        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = animatedBottomPadding
          ),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          items(
            items = safeItems,
            key = { getItemId(it) }
          ) { item ->
            val effectiveOnClick = {
              if (isInSelectionMode) {
                onToggleSelection(item)
              } else {
                onClick(item)
              }
            }
            val effectiveOnThumbClick = if (onThumbClick != null) {
              { onThumbClick(item) }
            } else if (tapThumbnailToSelect && mediaLayoutMode != MediaLayoutMode.GRID && !isInSelectionMode) {
              { onToggleSelection(item) }
            } else {
              null
            }

            if (isReorderMode) {
              ReorderableItem(reorderState, key = getItemId(item)) { _ ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(modifier = Modifier.weight(1f)) {
                    ExplorerItemCard(
                      item = item,
                      isSelected = isSelected(item),
                      showSubtitleIndicator = showSubtitleIndicator,
                      isGridMode = false,
                      columns = 1,
                      uiSettings = uiSettings,
                      onClick = effectiveOnClick,
                      onLongClick = { onLongClick(item) },
                      onThumbClick = effectiveOnThumbClick,
                      recentlyPlayedFilePath = recentlyPlayedFilePath,
                      recentlyPlayedPaths = recentlyPlayedPaths,
                      playedFolderPaths = playedFolderPaths,
                      newVideoIds = newVideoIds,
                      watchedVideoIds = watchedVideoIds,
                      videoPlaybackProgress = videoPlaybackProgress,
                      showSections = showSections
                    )
                  }
                  IconButton(
                    onClick = { },
                    modifier = Modifier
                      .size(48.dp)
                      .draggableHandle(),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.DragHandle,
                      contentDescription = stringResource(R.string.drag_to_reorder),
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            } else {
              ExplorerItemCard(
                item = item,
                isSelected = isSelected(item),
                showSubtitleIndicator = showSubtitleIndicator,
                isGridMode = false,
                columns = 1,
                uiSettings = uiSettings,
                onClick = effectiveOnClick,
                onLongClick = { onLongClick(item) },
                onThumbClick = effectiveOnThumbClick,
                recentlyPlayedFilePath = recentlyPlayedFilePath,
                recentlyPlayedPaths = recentlyPlayedPaths,
                playedFolderPaths = playedFolderPaths,
                newVideoIds = newVideoIds,
                watchedVideoIds = watchedVideoIds,
                videoPlaybackProgress = videoPlaybackProgress,
                showSections = showSections
              )
            }
          }
        }
      }

      // Scrollbar overlay with bottom padding to avoid overlap with navigation
      if (items.size > 20) {
        FastScrollbar(
          listState = if (mediaLayoutMode == MediaLayoutMode.GRID && !showSections) null else listState,
          gridState = if (mediaLayoutMode == MediaLayoutMode.GRID && !showSections) gridState else null,
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = animatedBottomPadding),
          thumbColor = MaterialTheme.colorScheme.primary,
        )
      }
    }

    CompositionLocalProvider(
      LocalLastPlayedVideoPathsInFolder provides lastPlayedVideoPathsInFolder,
      LocalRecentlyPlayedFilePaths provides recentlyPlayedFilePaths
    ) {
      if (isRefreshing != null && onRefresh != null) {
        PullRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = onRefresh,
          modifier = modifier.fillMaxSize(),
          listState = listState,
          content = contentBlock
        )
      } else {
        Box(modifier = modifier.fillMaxSize()) {
          contentBlock()
        }
      }
    }
  }
}

private fun <T> getItemId(item: T): String {
  return when (item) {
    is VideoFolder -> "folder_${item.bucketId.ifBlank { item.path }}"
    is Video -> "video_${item.path.ifBlank { item.uri.toString() }}"
    is VideoWithPlaybackInfo -> "video_${item.video.path.ifBlank { item.video.uri.toString() }}"
    is PlaylistWithCount -> "playlist_${item.playlist.id}"
    is RecentlyPlayedItem.VideoItem -> "recent_video_${item.video.path.ifBlank { item.video.uri.toString() }}"
    is RecentlyPlayedItem.PlaylistItem -> "recent_playlist_${item.playlist.id}"
    is FileSystemItem.Folder -> "fs_folder_${item.path}"
    is FileSystemItem.VideoFile -> "fs_video_${item.path.ifBlank { item.video.uri.toString() }}"
    is PlaylistVideoItem -> "playlist_item_${item.playlistItem.id}"
    else -> "item_${item.hashCode()}"
  }
}

@Composable
private fun <T> ExplorerItemCard(
  item: T,
  isSelected: Boolean,
  showSubtitleIndicator: Boolean,
  isGridMode: Boolean,
  columns: Int,
  uiSettings: UiSettings,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onThumbClick: (() -> Unit)? = null,
  recentlyPlayedFilePath: String? = null,
  recentlyPlayedPaths: Set<String> = emptySet(),
  playedFolderPaths: Set<String> = emptySet(),
  newVideoIds: Set<Long> = emptySet(),
  watchedVideoIds: Set<Long> = emptySet(),
  videoPlaybackProgress: Map<Long, Float> = emptyMap(),
  showSections: Boolean = false,
) {
  val lastPlayedVideoPathsInFolder = LocalLastPlayedVideoPathsInFolder.current
  val recentlyPlayedFilePaths = LocalRecentlyPlayedFilePaths.current
  when (item) {
    is VideoFolder -> {
      val isRecentlyPlayed = recentlyPlayedFilePaths.any { path ->
        if (showSections) {
          path.startsWith(item.path + "/") || path == item.path || java.io.File(path).parent == item.path
        } else {
          java.io.File(path).parent == item.path
        }
      }
      val isNeverPlayed = item.path !in playedFolderPaths
      val isWatched = (item.videoCount > 0 || item.audioCount > 0) && item.unwatchedVideoCount == 0

      FolderCard(
        folder = item,
        uiSettings = uiSettings,
        isSelected = isSelected,
        isRecentlyPlayed = isRecentlyPlayed,
        isNeverPlayed = isNeverPlayed,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        newVideoCount = item.newCount
      )
    }
    is Video -> {
      val isOldAndUnplayed = newVideoIds.contains(item.id)
      val isWatched = watchedVideoIds.contains(item.id)
      val isRecentlyPlayed = item.path in lastPlayedVideoPathsInFolder

      VideoCard(
        video = item,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        showSubtitleIndicator = showSubtitleIndicator,
        isOldAndUnplayed = isOldAndUnplayed,
        isWatched = isWatched,
        isRecentlyPlayed = isRecentlyPlayed
      )
    }
    is VideoWithPlaybackInfo -> {
      val isRecentlyPlayed = item.video.path in lastPlayedVideoPathsInFolder

      VideoCard(
        video = item.video,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        showSubtitleIndicator = showSubtitleIndicator,
        progressPercentage = item.progressPercentage,
        isWatched = item.isWatched,
        isOldAndUnplayed = item.isOldAndUnplayed,
        isNeverPlayed = item.isNeverPlayed,
        isRecentlyPlayed = isRecentlyPlayed
      )
    }
    is PlaylistWithCount -> {
      PlaylistCard(
        playlist = item.playlist,
        itemCount = item.itemCount,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        mostRecentVideoPath = item.firstItemPath,
      )
    }
    is RecentlyPlayedItem.VideoItem -> {
      val isRecentlyPlayed = item.video.path in lastPlayedVideoPathsInFolder

      VideoCard(
        video = item.video,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        showSubtitleIndicator = showSubtitleIndicator,
        progressPercentage = item.progress,
        isWatched = item.isWatched,
        isRecentlyPlayed = isRecentlyPlayed
      )
    }
    is RecentlyPlayedItem.PlaylistItem -> {
      PlaylistCard(
        playlist = item.playlist,
        itemCount = item.videoCount,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        mostRecentVideoPath = item.mostRecentVideoPath,
      )
    }
    is FileSystemItem.Folder -> {
      val folderModel = VideoFolder(
        bucketId = item.path,
        name = item.name,
        path = item.path,
        videoCount = item.videoCount,
        audioCount = item.audioCount,
        totalSize = item.totalSize,
        totalDuration = item.totalDuration,
        videoSize = item.videoSize,
        audioSize = item.audioSize,
        videoDuration = item.videoDuration,
        audioDuration = item.audioDuration,
        lastModified = item.lastModified / 1000,
        newCount = item.newCount,
        unwatchedVideoCount = item.unwatchedVideoCount,
      )
      val isRecentlyPlayed = recentlyPlayedFilePaths.any { path ->
        if (showSections) {
          path.startsWith(item.path + "/") || path == item.path || java.io.File(path).parent == item.path
        } else {
          java.io.File(path).parent == item.path
        }
      }
      val isNeverPlayed = item.path !in playedFolderPaths
      val isWatched = (item.videoCount > 0 || item.audioCount > 0) && item.unwatchedVideoCount == 0

      FolderCard(
        folder = folderModel,
        uiSettings = uiSettings,
        isSelected = isSelected,
        isRecentlyPlayed = isRecentlyPlayed,
        isNeverPlayed = isNeverPlayed,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        newVideoCount = item.newCount
      )
    }
    is FileSystemItem.VideoFile -> {
      val isOldAndUnplayed = newVideoIds.contains(item.video.id)
      val isWatched = watchedVideoIds.contains(item.video.id)
      val isRecentlyPlayed = item.video.path in lastPlayedVideoPathsInFolder

      VideoCard(
        video = item.video,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        showSubtitleIndicator = showSubtitleIndicator,
        progressPercentage = videoPlaybackProgress[item.video.id],
        isOldAndUnplayed = isOldAndUnplayed,
        isWatched = isWatched,
        isNeverPlayed = videoPlaybackProgress[item.video.id] == null,
        isRecentlyPlayed = isRecentlyPlayed
      )
    }
    is PlaylistVideoItem -> {
      VideoCard(
        video = item.video,
        uiSettings = uiSettings,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onThumbClick = onThumbClick,
        isGridMode = isGridMode,
        gridColumns = columns,
        showSubtitleIndicator = showSubtitleIndicator
      )
    }
  }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 12.dp)
  )
}
