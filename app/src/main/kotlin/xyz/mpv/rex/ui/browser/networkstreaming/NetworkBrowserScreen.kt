package xyz.mpv.rex.ui.browser.networkstreaming

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.CardLayoutConfigProvider
import xyz.mpv.rex.ui.browser.cards.NetworkFileCard
import xyz.mpv.rex.ui.browser.cards.NetworkFolderCard
import xyz.mpv.rex.ui.browser.cards.NetworkVideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.FastScrollbar
import xyz.mpv.rex.ui.browser.dialogs.NetworkSortDialog
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.storage.FileTypeUtils
import java.io.File

@Serializable
data class NetworkBrowserScreen(
  val connectionId: Long,
  val connectionName: String,
  val currentPath: String = "/",
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current

    val viewModel: NetworkBrowserViewModel =
      viewModel(
        key = "NetworkBrowser_${connectionId}_$currentPath",
        factory =
          NetworkBrowserViewModel.factory(
            context.applicationContext as android.app.Application,
            connectionId,
            currentPath,
          ),
      )

    val filesState = viewModel.items.collectAsState()
    val files = filesState.value
    val isLoading by viewModel.isLoading.collectAsState()
    val uiSettings by viewModel.uiSettings.collectAsState()
    val error by viewModel.error.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val connection by viewModel.connection.collectAsState()

    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(connectionId, currentPath) {
      viewModel.loadData()
    }

    BackHandler {
      backstack.removeLastOrNull()
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = connectionName,
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = { backstack.removeLastOrNull() },
          onCancelSelection = {},
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = null,
          onSettingsClick = {
            backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
          onDeleteClick = null,
          onRenameClick = null,
          isSingleSelection = false,
          onInfoClick = null,
          onPlayClick = null,
          onSelectAll = null,
          onInvertSelection = null,
          onDeselectAll = null,
        )
      },
    ) { padding ->
      val partitioned = remember(files) { partitionNetworkFiles(files) }

      NetworkBrowserContent(
        folders = partitioned.folders,
        videos = partitioned.media,
        otherFiles = partitioned.otherFiles,
        connectionId = connectionId,
        connectionName = connectionName,
        connection = connection,
        isLoading = isLoading && files.isEmpty(),
        uiSettings = uiSettings,
        viewMode = viewMode,
        isRefreshing = isRefreshing,
        error = error,
        onRefresh = { viewModel.loadDataSuspend() },
        onRetry = { viewModel.loadData() },
        onFolderClick = { folder ->
          backstack.add(
            NetworkBrowserScreen(
              connectionId = connectionId,
              connectionName = connectionName,
              currentPath = folder.path,
            ),
          )
        },
        onVideoClick = { video ->
          viewModel.playVideo(video)
        },
        onFileClick = { file ->
          android.widget.Toast.makeText(
            context,
            context.getString(R.string.network_not_playable, file.name),
            android.widget.Toast.LENGTH_SHORT,
          ).show()
        },
        modifier = Modifier.padding(top = padding.calculateTopPadding()),
      )
    }

    NetworkSortDialog(
      isOpen = sortDialogOpen.value,
      onDismiss = { sortDialogOpen.value = false },
    )
  }
}

@Composable
internal fun NetworkBrowserContent(
  folders: List<NetworkFile>,
  videos: List<NetworkFile>,
  otherFiles: List<NetworkFile>,
  connectionId: Long,
  connectionName: String,
  isLoading: Boolean,
  uiSettings: UiSettings,
  viewMode: NetworkViewMode,
  isRefreshing: MutableState<Boolean>,
  error: String?,
  onRefresh: suspend () -> Unit,
  onFolderClick: (NetworkFile) -> Unit,
  onVideoClick: (NetworkFile) -> Unit,
  onFileClick: (NetworkFile) -> Unit = {},
  connection: NetworkConnection? = null,
  onRetry: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) {
    when {
      isLoading && !isRefreshing.value -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      error != null -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.network_error_loading),
            message = error,
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
          )
        }
      }

      folders.isEmpty() && videos.isEmpty() && otherFiles.isEmpty() -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.empty_folder_title),
            message = stringResource(R.string.empty_folder_message),
          )
        }
      }

      else -> {
        val networkListState = rememberLazyListState()
        val networkGridState = rememberLazyGridState()

        val configuration = LocalConfiguration.current
        val layoutConfig = remember(viewMode, configuration.screenWidthDp, uiSettings.unlimitedNameLines) {
          CardLayoutConfigProvider.configFor(
            viewMode = viewMode,
            screenWidthDp = configuration.screenWidthDp,
            unlimitedNameLines = uiSettings.unlimitedNameLines,
          )
        }
        val gridColumns = layoutConfig.gridColumns

        var prevMode by remember { mutableStateOf(viewMode) }
        LaunchedEffect(viewMode) {
          if (prevMode != viewMode) {
            if (viewMode == NetworkViewMode.GRID && prevMode != NetworkViewMode.GRID) {
              networkGridState.scrollToItem(networkListState.firstVisibleItemIndex)
            } else if (viewMode != NetworkViewMode.GRID && prevMode == NetworkViewMode.GRID) {
              networkListState.scrollToItem(networkGridState.firstVisibleItemIndex)
            }
            prevMode = viewMode
          }
        }

        val (activeListState, activeGridState) = resolveFastScrollbarBinding(
          viewMode = viewMode,
          listState = networkListState,
          gridState = networkGridState,
        )

        val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        Box(modifier = Modifier.fillMaxSize()) {
          if (viewMode == NetworkViewMode.GRID) {
            LazyVerticalGrid(
              columns = GridCells.Fixed(gridColumns),
              state = networkGridState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = navigationBarHeight,
              ),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Text(
                    text = stringResource(R.string.network_section_folders),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = folders,
                  key = { it.path },
                ) { folder ->
                  NetworkFolderCard(
                    file = folder,
                    onClick = { onFolderClick(folder) },
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    uiSettings = uiSettings,
                    modifier = Modifier,
                  )
                }
              }

              if (videos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Text(
                    text = stringResource(R.string.network_section_media),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = videos,
                  key = { it.path },
                ) { video ->
                  connection?.let { conn ->
                    NetworkVideoCard(
                      file = video,
                      connection = conn,
                      uiSettings = uiSettings,
                      onClick = { onVideoClick(video) },
                      viewMode = viewMode,
                      gridColumns = gridColumns,
                      modifier = Modifier,
                    )
                  }
                }
              }

              if (otherFiles.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Text(
                    text = stringResource(R.string.network_section_files),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = otherFiles,
                  key = { it.path },
                ) { file ->
                  NetworkFileCard(
                    file = file,
                    onClick = { onFileClick(file) },
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    uiSettings = uiSettings,
                    modifier = Modifier,
                  )
                }
              }
            }
          } else {
            LazyColumn(
              state = networkListState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = navigationBarHeight,
              ),
              verticalArrangement = Arrangement.spacedBy(if (viewMode == NetworkViewMode.DETAIL) 2.dp else 4.dp),
            ) {
              if (folders.isNotEmpty()) {
                item {
                  Text(
                    text = stringResource(R.string.network_section_folders),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = folders,
                  key = { it.path },
                ) { folder ->
                  NetworkFolderCard(
                    file = folder,
                    onClick = { onFolderClick(folder) },
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    uiSettings = uiSettings,
                    modifier = Modifier,
                  )
                }
              }

              if (videos.isNotEmpty()) {
                item {
                  Text(
                    text = stringResource(R.string.network_section_media),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = videos,
                  key = { it.path },
                ) { video ->
                  connection?.let { conn ->
                    NetworkVideoCard(
                      file = video,
                      connection = conn,
                      uiSettings = uiSettings,
                      onClick = { onVideoClick(video) },
                      viewMode = viewMode,
                      gridColumns = gridColumns,
                      modifier = Modifier,
                    )
                  }
                }
              }

              if (otherFiles.isNotEmpty()) {
                item {
                  Text(
                    text = stringResource(R.string.network_section_files),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                  )
                }
                items(
                  items = otherFiles,
                  key = { it.path },
                ) { file ->
                  NetworkFileCard(
                    file = file,
                    onClick = { onFileClick(file) },
                    viewMode = viewMode,
                    gridColumns = gridColumns,
                    uiSettings = uiSettings,
                    modifier = Modifier,
                  )
                }
              }
            }
          }

          FastScrollbar(
            listState = activeListState,
            gridState = activeGridState,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .fillMaxHeight()
              .padding(top = 8.dp, bottom = navigationBarHeight),
            thumbColor = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

/**
 * Resolves which scroll state to bind to [FastScrollbar] based on the active [NetworkViewMode].
 * In [NetworkViewMode.GRID], binds to [LazyGridState].
 * In [NetworkViewMode.GALLERY], [NetworkViewMode.LIST], and [NetworkViewMode.DETAIL], binds to [LazyListState].
 */
fun resolveFastScrollbarBinding(
  viewMode: NetworkViewMode,
  listState: LazyListState,
  gridState: LazyGridState,
): Pair<LazyListState?, LazyGridState?> {
  return if (viewMode == NetworkViewMode.GRID) {
    null to gridState
  } else {
    listState to null
  }
}

/**
 * Result container for categorized network directory entries.
 *
 * @property folders Directories eligible for drill-down navigation.
 * @property media Playable media files (video and audio).
 * @property otherFiles Non-media files (subtitles, documents, archives, etc.).
 */
data class PartitionedNetworkItems(
  val folders: List<NetworkFile>,
  val media: List<NetworkFile>,
  val otherFiles: List<NetworkFile>,
) {
  val videos: List<NetworkFile> get() = media

  /**
   * Returns true if no items exist across all three categories.
   */
  val isEmpty: Boolean get() = folders.isEmpty() && media.isEmpty() && otherFiles.isEmpty()

  /**
   * Returns total count of all items.
   */
  val totalCount: Int get() = folders.size + media.size + otherFiles.size
}

/**
 * Partitions a list of [NetworkFile] entries into [folders], [media], and [otherFiles]
 * without omitting any file.
 *
 * - Directories are categorized into [folders].
 * - Non-directory media files (as determined by [FileTypeUtils.isMediaFile]) are categorized into [media].
 * - Non-directory non-media files are categorized into [otherFiles].
 */
fun partitionNetworkFiles(files: List<NetworkFile>): PartitionedNetworkItems {
  val folders = ArrayList<NetworkFile>()
  val media = ArrayList<NetworkFile>()
  val otherFiles = ArrayList<NetworkFile>()

  for (file in files) {
    when {
      file.isDirectory -> folders.add(file)
      FileTypeUtils.isMediaFile(File(file.name)) -> media.add(file)
      else -> otherFiles.add(file)
    }
  }
  return PartitionedNetworkItems(folders, media, otherFiles)
}
