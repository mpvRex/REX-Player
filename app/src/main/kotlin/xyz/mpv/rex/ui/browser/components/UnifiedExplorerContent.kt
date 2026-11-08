package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import xyz.mpv.rex.utils.sort.PinnedSortUtils
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
import androidx.compose.material3.TopAppBarState
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
  /** Collapsing header above this content; a pull refreshes only once it is expanded. */
  headerScrollState: TopAppBarState? = null,
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
  /** Paths of pinned entries; when non-empty and [showSections] is set they render in a "Pinned" section on top. */
  pinnedPaths: List<String> = emptyList(),
  listState: LazyListState? = null,
  gridState: LazyGridState? = null,
  useContentSurface: Boolean = false,
  /** List and section branches keep the scaffold-coloured 3.dp gaps
    *  instead of the white-sheet backplate (media view & tree view opt in). */
  keepListGaps: Boolean = false,
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
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
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
        .padding(bottom = 64.dp),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary,
      )
    }
  } else if (items.isEmpty()) {
    if (useContentSurface) {
      Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        color = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Black) {
          androidx.compose.ui.graphics.Color.Black
        } else {
          MaterialTheme.colorScheme.surface
        },
      ) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            message = emptyMessage,
          )
        }
      }
    } else {
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
    }
  } else {
    val safeItems = remember(items) {
      items.distinctBy { getItemId(it) }
    }
    // O(1) pin lookups for the card badges, rebuilt only when pins change.
    val pinnedPathSet = remember(pinnedPaths) { pinnedPaths.toHashSet() }
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

            // Pinned entries stay inside their normal Folders/Media sections
            // (sorted to the front), so the index math is just "sections above
            // + offset within this section".
            val gridMode = mediaLayoutMode == MediaLayoutMode.GRID
            val folderCols = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
            val videoCols = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
            val sorted = PinnedSortUtils.sortPinnedFirst(safeItems, pinnedPaths) { pinnedKeyOf(it) ?: "" }
            val folderItems = sorted.filter { it is VideoFolder || it is FileSystemItem.Folder }
            val videoItems = sorted.filter { it is Video || it is VideoWithPlaybackInfo || it is FileSystemItem.VideoFile || it is RecentlyPlayedItem.VideoItem }

            val isFolder = matchedItem is VideoFolder || matchedItem is FileSystemItem.Folder
            val targetIndex = if (isFolder) {
              val folderIndex = folderItems.indexOf(matchedItem)
              if (folderIndex != -1) {
                if (gridMode) {
                  1 + (folderIndex / folderCols)
                } else {
                  1 + folderIndex
                }
              } else 0
            } else {
              val videoIndex = videoItems.indexOf(matchedItem)
              if (videoIndex != -1) {
                if (folderItems.isNotEmpty()) {
                  if (gridMode) {
                    val numFolderRows = (folderItems.size + folderCols - 1) / folderCols
                    numFolderRows + 3 + (videoIndex / videoCols)
                  } else {
                    folderItems.size + 3 + videoIndex
                  }
                } else {
                  if (gridMode) {
                    1 + (videoIndex / videoCols)
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

    val verticalGapDp = if (useContentSurface) 3.dp else 2.dp

    // Viewport height of the list, and the height one card occupies. Together
    // they size the content-surface filler to exactly the space left below the
    // last card. Using fillParentMaxHeight() alone would give the filler the
    // whole viewport, making even a 3-folder list scrollable.
    var listViewportHeightPx by remember { mutableIntStateOf(0) }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val measureCard = Modifier.onGloballyPositioned { coordinates ->
      // Cards differ slightly (1 vs 2 title lines); the tallest is used so the
      // filler never overruns the bottom edge.
      val height = coordinates.size.height
      if (height > cardHeightPx) cardHeightPx = height
    }
    val gapPx = with(LocalDensity.current) { verticalGapDp.roundToPx() }
    val itemCount = safeItems.size
    // Grid mode packs `columns` cards into each row, so the vertical space they
    // occupy is rows × cardHeight, not itemCount × cardHeight. Counting cards
    // as rows made `leftover` collapse to 0 for any list of more than a row,
    // which pinned the filler to the selection reserve and left the sheet
    // stopping short of the container's bottom edge.
    val isGridSurface = useContentSurface && mediaLayoutMode == MediaLayoutMode.GRID
    val rowCount = if (isGridSurface) {
      (itemCount + columns - 1) / columns
    } else {
      itemCount
    }
    // Rows plus their gaps, plus the gap between the last row and the filler.
    val cardsHeightPx = cardHeightPx * rowCount + gapPx * rowCount
    val leftoverPx = (listViewportHeightPx - cardsHeightPx).coerceAtLeast(0)
    // Keep a fixed strip at the end even when the list already fills the
    // screen: it lets the last card scroll clear of the selection action bar
    // that appears over it. Matches the reserve used by animatedBottomPadding
    // in selection mode (navigationBarHeight + 88.dp vs + 16.dp).
    val selectionReservePx = with(LocalDensity.current) { (88.dp - 16.dp).roundToPx() }
    val fillerHeightPx = maxOf(leftoverPx, selectionReservePx)

    // The painted face of the content surface in surface mode: card colour. Gaps
    // between/around cards show whatever is behind the list; with the backplate
    // that is this colour (white in light theme, black in AMOLED). Without it
    // the Scaffold tone (surfaceContainer) shows through instead.
    val contentSurfaceSheetColor = if (MaterialTheme.colorScheme.background == Color.Black) {
      Color.Black
    } else {
      MaterialTheme.colorScheme.surface
    }
    val contentBlock: @Composable BoxScope.() -> Unit = {
      if (showSections) {
        // Pinned entries stay inside their normal Folders/Media sections but are
        // sorted to the front in pin order, mirroring the album/video-list views.
        val (folderItems, videoItems) = remember(safeItems, pinnedPaths) {
          val sorted = PinnedSortUtils.sortPinnedFirst(safeItems, pinnedPaths) { pinnedKeyOf(it) ?: "" }
          sorted.filter { it is VideoFolder || it is FileSystemItem.Folder } to
            sorted.filter { it is Video || it is VideoWithPlaybackInfo || it is FileSystemItem.VideoFile || it is RecentlyPlayedItem.VideoItem }
        }

        val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
        val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

        // The list must be composed whether the backplate paints or not — only
        // the background is conditional. White sheet behind the sectioned list;
        // tree view no longer opts out; the lattice opt-outs (media view,
        // folder view) are all plain-list or grid screens, not sections.
        Box(
          modifier = Modifier
            .fillMaxSize()
            .then(
              if (useContentSurface && !keepListGaps) {
                Modifier.background(contentSurfaceSheetColor)
              } else {
                Modifier
              }
            )
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = if (useContentSurface) 0.dp else 8.dp,
              end = if (useContentSurface) 0.dp else 8.dp,
              top = if (useContentSurface) 0.dp else 8.dp,
              bottom = if (useContentSurface) 0.dp else animatedBottomPadding
            ),
            verticalArrangement = if (useContentSurface) Arrangement.spacedBy(3.dp) else Arrangement.spacedBy(2.dp)
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
                          shape = if (useContentSurface) RoundedCornerShape(4.dp) else null,
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
                          showSections = showSections,
                          showAudioFiles = showAudioFiles,
                          pinnedPathSet = pinnedPathSet,
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
                itemsIndexed(
                  items = folderItems,
                  key = { _, it -> getItemId(it) }
                ) { index, item ->
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

                  val cardShape = if (useContentSurface) {
                    RoundedCornerShape(2.dp)
                  } else null

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
                    showSections = showSections,
                    showAudioFiles = showAudioFiles,
                    pinnedPathSet = pinnedPathSet,
                    shape = cardShape,
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
                          shape = if (useContentSurface) RoundedCornerShape(4.dp) else null,
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
                          showSections = showSections,
                          showAudioFiles = showAudioFiles,
                          pinnedPathSet = pinnedPathSet,
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
                itemsIndexed(
                  items = videoItems,
                  key = { _, it -> getItemId(it) }
                ) { index, item ->
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

                  val cardShape = if (useContentSurface) {
                    RoundedCornerShape(2.dp)
                  } else null

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
                    showSections = showSections,
                    showAudioFiles = showAudioFiles,
                    pinnedPathSet = pinnedPathSet,
                    shape = cardShape,
                  )
                }
              }
            }
          }
        }
      } else if (mediaLayoutMode == MediaLayoutMode.GRID) {
        // Grid surface mode paints the whole sheet in the card colour: the
        // user wants grid gaps white, not the scaffold tone showing through.
        // A backplate colours the horizontal and vertical gaps and the empty
        // cells of a partial row in one go. Deliberate exception to the
        // contrast rule — LIST gaps keep the scaffold tone.
        val gridSheetColor = contentSurfaceSheetColor
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(gridSheetColor)
        ) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
              .fillMaxSize()
              // Same reason as the list path: the filler is sized from the
              // viewport, and without this the grid branch never measures it.
              .onGloballyPositioned { listViewportHeightPx = it.size.height },
            contentPadding = PaddingValues(
              start = if (useContentSurface) 0.dp else 8.dp,
              end = if (useContentSurface) 0.dp else 8.dp,
              top = if (useContentSurface) 0.dp else 8.dp,
              bottom = if (useContentSurface) 0.dp else animatedBottomPadding
            ),
            // Surface mode contracts the gaps and lets the tile go full height so
            // the grid sets the row height; the cards then fill it and stay equal.
            horizontalArrangement = if (useContentSurface) Arrangement.spacedBy(3.dp) else Arrangement.spacedBy(4.dp),
            verticalArrangement = if (useContentSurface) Arrangement.spacedBy(3.dp) else Arrangement.spacedBy(4.dp)
          ) {
            if (!useContentSurface) {
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
                  showSections = showSections,
                  showAudioFiles = showAudioFiles,
                  pinnedPathSet = pinnedPathSet,
                )
              }
            } else {
              // Surface mode: rows of exactly `columns` tiles, each a full-width
              // span. A tile spanning several cells is not measured by the grid
              // for row height, so the tiles cannot size the row themselves —
              // rows would collapse. Spanning the row keeps the grid in charge.
              val surfaceCardShape = RoundedCornerShape(4.dp)
              safeItems.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                item(
                  key = "surface_row_$rowIndex",
                  span = { GridItemSpan(maxLineSpan) },
                ) {
                  Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEachIndexed { columnIndex, item ->
                      if (rowItems.size > 1) {
                        Box(modifier = Modifier.width(3.dp))
                      }
                      val effectiveOnClick = {
                        if (isInSelectionMode) {
                          onToggleSelection(item)
                        } else {
                          onClick(item)
                        }
                      }
                      val effectiveOnThumbClick = if (onThumbClick != null) {
                        { onThumbClick(item) }
                      } else if (tapThumbnailToSelect && !isInSelectionMode) {
                        { onToggleSelection(item) }
                      } else {
                        null
                      }

                      Box(
                        modifier = Modifier
                          .weight(1f)
                          .onGloballyPositioned { coordinates ->
                            val height = coordinates.size.height
                            if (height > cardHeightPx) cardHeightPx = height
                          },
                      ) {
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
                          showSections = showSections,
                          showAudioFiles = showAudioFiles,
                          pinnedPathSet = pinnedPathSet,
                          shape = surfaceCardShape,
                        )
                      }
                      // Pad the final row so a partial row keeps its column widths.
                      if (columnIndex == rowItems.lastIndex) {
                        repeat(columns - rowItems.size) {
                          Box(modifier = Modifier.width(3.dp))
                          Box(modifier = Modifier.weight(1f))
                        }
                      }
                    }
                  }
                }
              }

              item(
                key = "content_surface_filler",
                span = { GridItemSpan(maxLineSpan) },
              ) {
                ContentSurfaceFiller(heightPx = fillerHeightPx)
              }
            }
          }
        }
      } else {
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
          if (isReorderMode && onReorder != null) {
            onReorder(from.index, to.index)
          }
        }

        // The list must be composed whether the backplate paints or not — only
        // the background is conditional. White sheet behind the plain list,
        // except media view and folder view, which pass keepListGaps = true
        // and keep the grey 3.dp lattice.
        Box(
          modifier = Modifier
            .fillMaxSize()
            .then(
              if (useContentSurface && !keepListGaps) {
                Modifier.background(contentSurfaceSheetColor)
              } else {
                Modifier
              }
            )
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .onGloballyPositioned { listViewportHeightPx = it.size.height },
            contentPadding = PaddingValues(
              start = if (useContentSurface) 0.dp else 8.dp,
              end = if (useContentSurface) 0.dp else 8.dp,
              top = if (useContentSurface) 0.dp else 8.dp,
              bottom = if (useContentSurface) 0.dp else animatedBottomPadding
            ),
            verticalArrangement = if (useContentSurface) Arrangement.spacedBy(3.dp) else Arrangement.spacedBy(2.dp)
          ) {
            itemsIndexed(
              items = safeItems,
              key = { _, item -> getItemId(item) }
            ) { index, item ->
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

              val cardShape = if (useContentSurface) {
                RoundedCornerShape(2.dp)
              } else null

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
                        showSections = showSections,
                        showAudioFiles = showAudioFiles,
                        pinnedPathSet = pinnedPathSet,
                        shape = cardShape,
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
                Box(modifier = measureCard) {
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
                    showSections = showSections,
                    showAudioFiles = showAudioFiles,
                    pinnedPathSet = pinnedPathSet,
                    shape = cardShape,
                  )
                }
              }
            }

            if (useContentSurface && !showSections) {
              // Filler card: occupies the space left below the last item, or a
              // fixed strip when the list fills the screen. The strip keeps the
              // last card scrollable clear of the selection action bar, and the
              // leftover keeps the surface reaching the container's rounded
              // bottom edge.
              //
              // Section mode mixes headers, spacers and grid rows into the same
              // list, so the uniform "cards + gaps" height math does not apply;
              // it is skipped there rather than guessing.
              item(key = "content_surface_filler") {
                ContentSurfaceFiller(heightPx = fillerHeightPx)
              }
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
            .padding(top = 8.dp, bottom = if (useContentSurface) 16.dp else animatedBottomPadding),
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
          headerScrollState = headerScrollState,
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

/**
 * Empty card that closes off the content surface in surface mode.
 *
 * Takes exactly [heightPx]: either the space left below the last card (so a
 * short list reaches the container's bottom edge and keeps its rounded corners
 * visible) or a fixed strip when the list already fills the screen (so the last
 * card can scroll clear of the selection action bar). Not using
 * fillParentMaxHeight(), which would give it the whole viewport height and add
 * unwanted scroll range to every list.
 */
@Composable
private fun ContentSurfaceFiller(heightPx: Int) {
  val containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
    Color.Black
  } else {
    MaterialTheme.colorScheme.surface
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(with(LocalDensity.current) { heightPx.toDp() })
      .clip(RoundedCornerShape(2.dp))
      .background(containerColor)
  )
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
  showAudioFiles: Boolean = true,
  pinnedPathSet: Set<String> = emptySet(),
  shape: Shape? = null,
) {
  val lastPlayedVideoPathsInFolder = LocalLastPlayedVideoPathsInFolder.current
  val recentlyPlayedFilePaths = LocalRecentlyPlayedFilePaths.current
  // Derived once here so every card type (folder or video) shows the pin badge.
  val isPinned = remember(item, pinnedPathSet) {
    val path = pinnedKeyOf(item)
    path != null && path in pinnedPathSet
  }
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
      val isWatched = item.activeCount(showAudioFiles) > 0 && item.unwatchedVideoCount == 0

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
        newVideoCount = item.newCount,
        isPinned = isPinned,
        shape = shape,
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
        isRecentlyPlayed = isRecentlyPlayed,
        isPinned = isPinned,
        shape = shape,
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
        isRecentlyPlayed = isRecentlyPlayed,
        isPinned = isPinned,
        shape = shape,
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
        isRecentlyPlayed = isRecentlyPlayed,
        isPinned = isPinned,
        shape = shape,
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
      val isWatched = item.activeCount(showAudioFiles) > 0 && item.unwatchedVideoCount == 0

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
        newVideoCount = item.newCount,
        isPinned = isPinned,
        shape = shape,
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
        isRecentlyPlayed = isRecentlyPlayed,
        isPinned = isPinned,
        shape = shape,
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
        showSubtitleIndicator = showSubtitleIndicator,
        shape = shape,
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

/**
 * Path used for pinning lookups, or null for item types that can't be pinned
 * (playlists, recently played rows, ...). Both [VideoFolder] and [FileSystemItem]
 * are identified by their absolute path, which is what the repository stores.
 *
 * Internal (not private) so the media-badge wiring can be unit tested.
 */
internal fun <T> pinnedKeyOf(item: T): String? =
  when (item) {
    is VideoFolder -> item.path.ifBlank { item.bucketId }
    is FileSystemItem.Folder -> item.path
    is FileSystemItem.VideoFile -> item.path.ifBlank { item.video.path }
    is Video -> item.path.ifBlank { item.uri.toString() }
    is VideoWithPlaybackInfo -> item.video.path.ifBlank { item.video.uri.toString() }
    is RecentlyPlayedItem.VideoItem -> item.video.path.ifBlank { item.video.uri.toString() }
    else -> null
  }
