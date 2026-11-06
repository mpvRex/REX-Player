package xyz.mpv.rex.ui.browser.networkstreaming

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlinx.serialization.Serializable
import xyz.mpv.rex.R
import xyz.mpv.rex.database.dao.NetworkConnectionDao
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cards.NetworkFolderCard
import xyz.mpv.rex.ui.browser.cards.NetworkVideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.ContentSurfaceContainer
import xyz.mpv.rex.ui.browser.components.FastScrollbar
import xyz.mpv.rex.ui.browser.dialogs.NetworkSortDialog
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.storage.FileTypeUtils

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

    // UI State
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }

    // Load files when connectionId or currentPath changes
    LaunchedEffect(connectionId, currentPath) {
      viewModel.loadData()
    }

    BackHandler {
      backstack.navigateBack()
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val contentSurfaceScaffoldColor = if (MaterialTheme.colorScheme.background == Color.Black) {
      Color.Black
    } else {
      MaterialTheme.colorScheme.surfaceContainer
    }

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      // The screen tone behind the content surface. This screen is pushed, so it
      // must set its own — a tone is not inherited across a NavDisplay boundary.
      containerColor = contentSurfaceScaffoldColor,
      topBar = {
        BrowserTopBar(
          title = connectionName,
          scrollBehavior = scrollBehavior,
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = { backstack.navigateBack() },
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
      NetworkBrowserContent(
        folders = remember { derivedStateOf { filesState.value.filter { it.isDirectory } } }.value,
        videos = remember { derivedStateOf { filesState.value.filter { !it.isDirectory && FileTypeUtils.isMediaFile(File(it.name)) } } }.value,
        connectionId = connectionId,
        connectionName = connectionName,
        isLoading = isLoading && files.isEmpty(),
        uiSettings = uiSettings,
        isRefreshing = isRefreshing,
        error = error,
        onRefresh = { viewModel.loadData() },
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
        modifier = Modifier.padding(
          PaddingValues(
            start = padding.calculateStartPadding(LocalLayoutDirection.current),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = 0.dp,
          )
        ),
      )
    }

    NetworkSortDialog(
      isOpen = sortDialogOpen.value,
      onDismiss = { sortDialogOpen.value = false },
    )
  }
}

@Composable
private fun NetworkBrowserContent(
  folders: List<NetworkFile>,
  videos: List<NetworkFile>,
  connectionId: Long,
  connectionName: String,
  isLoading: Boolean,
  uiSettings: UiSettings,
  isRefreshing: MutableState<Boolean>,
  error: String?,
  onRefresh: suspend () -> Unit,
  onFolderClick: (NetworkFile) -> Unit,
  onVideoClick: (NetworkFile) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Load connection details
  val dao = org.koin.compose.koinInject<NetworkConnectionDao>()
  var connection by remember { mutableStateOf<NetworkConnection?>(null) }

  LaunchedEffect(connectionId) {
    connection = dao.getConnectionById(connectionId)
  }

  ContentSurfaceContainer(
    navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current,
    modifier = modifier,
  ) {
    val contentSurfaceSheetColor = if (MaterialTheme.colorScheme.background == Color.Black) {
      Color.Black
    } else {
      MaterialTheme.colorScheme.surface
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(contentSurfaceSheetColor),
    ) {
      when {
        isLoading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.Folder,
              title = "Error loading files",
              message = error,
            )
          }
        }

        folders.isEmpty() && videos.isEmpty() -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.Folder,
              title = "Empty folder",
              message = "This folder contains no files or directories",
            )
          }
        }

        else -> {
          val networkListState = remember { LazyListState() }

          // Check if at top of list to hide scrollbar during pull-to-refresh
          val isAtTop by remember {
            derivedStateOf {
              networkListState.firstVisibleItemIndex == 0 && networkListState.firstVisibleItemScrollOffset == 0
            }
          }

          // Only show scrollbar if list has more than 20 items (folders + videos)
          val hasEnoughItems = (folders.size + videos.size) > 20

          // Animate scrollbar alpha
          val scrollbarAlpha by animateFloatAsState(
            targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
            animationSpec = tween(durationMillis = 200),
            label = "scrollbarAlpha",
          )

          PullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              LazyColumn(
                state = networkListState,
                modifier = Modifier.fillMaxSize(),
                // The container owns the bottom gap, so the list only carries
                // the 3.dp grid rhythm and its horizontal breathing room.
                contentPadding = PaddingValues(
                  start = 8.dp,
                  end = 8.dp,
                  top = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                // Folders section
                if (folders.isNotEmpty()) {
                  item(key = "folders_header") {
                    SectionHeader(title = stringResource(R.string.folders_count, folders.size))
                  }
                  items(
                    items = folders,
                    key = { it.path },
                  ) { folder ->
                    NetworkFolderCard(
                      file = folder,
                      onClick = { onFolderClick(folder) },
                      modifier = Modifier,
                      // Connected-list rows take the sheet's inner radius.
                      shape = RoundedCornerShape(2.dp),
                    )
                  }
                }

                // Videos section
                if (videos.isNotEmpty()) {
                  item(key = "videos_header") {
                    SectionHeader(title = "Videos")
                  }
                  items(
                    items = videos,
                    key = { it.path },
                  ) { video ->
                    // Only show card if connection is loaded
                    connection?.let { conn ->
                      NetworkVideoCard(
                        file = video,
                        connection = conn,
                        uiSettings = uiSettings,
                        onClick = { onVideoClick(video) },
                        modifier = Modifier,
                        shape = RoundedCornerShape(2.dp),
                      )
                    }
                  }
                }
              }

              FastScrollbar(
                listState = networkListState,
                modifier = Modifier
                  .align(Alignment.CenterEnd)
                  .fillMaxHeight()
                  .padding(top = 8.dp),
                thumbColor = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Section label on the content surface. Same style as the one in
 * `UnifiedExplorerContent`, which is private to that file.
 */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 12.dp),
  )
}
