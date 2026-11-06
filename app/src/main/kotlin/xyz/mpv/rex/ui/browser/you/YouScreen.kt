package xyz.mpv.rex.ui.browser.you

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.pluralStringResource
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.ImageBitmap
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.utils.permission.PermissionUtils
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.ui.theme.pillShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.storage.VideoScanUtils
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.browser.cards.PlaylistCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.ContentSurfaceContainer
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.playlist.PlaylistDetailScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistViewModel
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedItem
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedViewModel
import xyz.mpv.rex.ui.browser.search.SearchScreen
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.browser.shorts.ShortsScreen
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils

@Serializable
object YouScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val appearancePreferences = koinInject<AppearancePreferences>()
    val playlistRepository = koinInject<PlaylistRepository>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val customProfileName by appearancePreferences.customProfileName.collectAsState()
    val customProfileImagePath by appearancePreferences.customProfileImagePath.collectAsState()

    val customAvatarBitmap = remember(customProfileImagePath) {
      if (customProfileImagePath.isNotBlank()) {
        val file = java.io.File(customProfileImagePath)
        if (file.exists()) {
          try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
          } catch (e: Exception) {
            null
          }
        } else null
      } else null
    }
    val isShortsEnabled by browserPreferences.enableShorts.collectAsState()
    val enableTabNetwork by browserPreferences.enableTabNetwork.collectAsState()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
    val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
    val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
    val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
    val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
    val isRefreshing = remember { mutableStateOf(false) }

    val recentsViewModel: RecentlyPlayedViewModel = viewModel(
      factory = RecentlyPlayedViewModel.factory(context.applicationContext as Application),
    )
    val playlistViewModel: PlaylistViewModel = viewModel(
      factory = PlaylistViewModel.factory(context.applicationContext as Application),
    )

    val recentItems by recentsViewModel.recentItems.collectAsState()
    val recentsUiSettings by recentsViewModel.uiSettings.collectAsState()

    val playlistsWithCount by playlistViewModel.playlistsWithCount.collectAsState()
    val playlistUiSettings by playlistViewModel.uiSettings.collectAsState()

    // Interactive Action States
    var activeVideoItem by remember { mutableStateOf<RecentlyPlayedItem.VideoItem?>(null) }
    var activePlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    // Dialog States
    var videoForPlaylist by remember { mutableStateOf<Video?>(null) }
    var videoForInfo by remember { mutableStateOf<Video?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var videoToDeleteFromRecents by remember { mutableStateOf<RecentlyPlayedItem.VideoItem?>(null) }
    val deleteFilesCheckbox = rememberSaveable { mutableStateOf(false) }
    var showProfileCustomizationDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
      MainScreen.scrollToTopRequest.collect { tabId ->
        if (tabId == "you") {
          scope.launch {
            listState.animateScrollToItem(0)
          }
        }
      }
    }

    val navigationBarHeight = LocalNavigationBarHeight.current

    // The painted face of the content surface: the card colour. In AMOLED it is
    // black so the sheet fuses with the OLED background; elsewhere it is
    // `surface`, one step lighter than the Scaffold's `surfaceContainer`.
    val contentSurfaceSheetColor = if (MaterialTheme.colorScheme.background == Color.Black) {
      Color.Black
    } else {
      MaterialTheme.colorScheme.surface
    }
    val contentSurfaceScaffoldColor = if (MaterialTheme.colorScheme.background == Color.Black) {
      Color.Black
    } else {
      MaterialTheme.colorScheme.surfaceContainer
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      // Matches the rest of the browser so the content surface's rounded bottom
      // edge reads against the darker tone instead of an almost identical one.
      containerColor = contentSurfaceScaffoldColor,
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.you),
          scrollBehavior = scrollBehavior,
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = null,
          onCancelSelection = {},
          onSearchClick = {
            backStack.add(SearchScreen())
          },
          onSettingsClick = {
            backStack.add(PreferencesScreen)
          },
        )
      },
    ) { paddingValues ->
      // Same reason as MediaLibraryContent: the Scaffold's padding already
      // carries the navigation-bar inset, and ContentSurfaceContainer adds it
      // again along with its own 14.dp gap. Pass the horizontal/top insets only
      // and let the container own the bottom.
      ContentSurfaceContainer(
        navigationBarHeight = navigationBarHeight,
        modifier = Modifier.padding(
          PaddingValues(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
            bottom = 0.dp,
          )
        ),
      ) {
        PullRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = {
            isRefreshing.value = true
            scope.launch {
              recentsViewModel.refresh()
              playlistViewModel.refresh()
              kotlinx.coroutines.delay(400)
              isRefreshing.value = false
            }
          },
          modifier = Modifier.fillMaxSize(),
        ) {
          // The sheet paints the card colour; the shelves and the filler sit on
          // top of it, so the empty space under the last shelf still keeps the
          // container's rounded bottom edge visible.
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(contentSurfaceSheetColor)
          ) {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

              // ==========================================
              // PROFILE & QUICK ACTIONS HEADER
              // ==========================================
              item(key = "profile_header") {
                YouProfileHeader(
                  name = customProfileName,
                  avatarBitmap = customAvatarBitmap,
                  onAvatarLongClick = {
                    showProfileCustomizationDialog = true
                  },
                  recentCount = recentItems.size,
                  playlistCount = playlistsWithCount.size,
                  onHistoryClick = { backStack.add(RecentlyPlayedScreen) },
                  onPlaylistsClick = { backStack.add(PlaylistScreen) },
                  onNetworkClick = if (!enableTabNetwork) { { backStack.add(NetworkStreamingScreen) } } else null,
                  onShortsClick = if (!isShortsEnabled) { { backStack.add(ShortsScreen()) } } else null,
                )
              }

              // ==========================================
              // 1. RECENTLY PLAYED SECTION (HORIZONTAL SHELF)
              // ==========================================
              item(key = "header_recents") {
                SectionHeader(
                  title = stringResource(R.string.recently_played),
                  onViewAllClick = {
                    backStack.add(RecentlyPlayedScreen)
                  },
                )
              }

              item(key = "content_recents") {
                if (!enableRecentlyPlayed) {
                  ShelfEmptyCard(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.recently_played_disabled_title),
                    message = stringResource(R.string.recently_played_disabled_message),
                  )
                } else {
                  val previewRecents = recentItems.take(15)
                  if (previewRecents.isEmpty()) {
                    ShelfEmptyCard(
                      icon = Icons.Filled.History,
                      title = stringResource(R.string.no_recently_played_videos),
                      message = stringResource(R.string.no_recently_played_videos_message),
                    )
                  } else {
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 8.dp),
                      horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      items(previewRecents, key = { item ->
                        when (item) {
                          is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}_${item.timestamp}"
                          is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.timestamp}"
                        }
                      }) { item ->
                        when (item) {
                          is RecentlyPlayedItem.VideoItem -> {
                            RecentlyPlayedVideoShelfCard(
                              video = item.video,
                              progressPercentage = item.progress,
                              isWatched = item.isWatched,
                              isRecentlyPlayed = true,
                              showThumbnails = recentsUiSettings.showVideoThumbnails,
                              onClick = {
                                MediaUtils.playFile(item.video, context, "you_tab")
                              },
                              onLongClick = {
                                activeVideoItem = item
                              },
                              modifier = Modifier.width(160.dp),
                            )
                          }
                          is RecentlyPlayedItem.PlaylistItem -> {
                            PlaylistShelfCard(
                              playlist = item.playlist,
                              itemCount = item.videoCount,
                              mostRecentVideoPath = item.mostRecentVideoPath,
                              showThumbnails = recentsUiSettings.showVideoThumbnails,
                              onClick = {
                                backStack.add(PlaylistDetailScreen(item.playlist.id))
                              },
                              onLongClick = {
                                activePlaylist = item.playlist
                              },
                              modifier = Modifier.width(160.dp),
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }

              // ==========================================
              // 2. PLAYLISTS SECTION (HORIZONTAL SHELF)
              // ==========================================
              item(key = "header_playlists") {
                SectionHeader(
                  title = stringResource(R.string.playlists),
                  onViewAllClick = {
                    backStack.add(PlaylistScreen)
                  },
                )
              }

              item(key = "content_playlists") {
                val isGrid = mediaLayoutMode == MediaLayoutMode.GRID
                val previewPlaylists = if (isGrid) {
                  playlistsWithCount.take(videoGridColumns * 3)
                } else {
                  playlistsWithCount.take(8)
                }
                if (previewPlaylists.isEmpty()) {
                  ShelfEmptyCard(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = stringResource(R.string.no_playlists_yet),
                    message = "Custom playlists you create will appear here",
                  )
                } else if (isGrid) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    val chunkedPlaylists = previewPlaylists.chunked(videoGridColumns)
                    for (rowItems in chunkedPlaylists) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                      ) {
                        for (playlistWithCount in rowItems) {
                          Box(modifier = Modifier.weight(1f)) {
                            PlaylistCard(
                              playlist = playlistWithCount.playlist,
                              itemCount = playlistWithCount.itemCount,
                              uiSettings = playlistUiSettings,
                              onClick = {
                                backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
                              },
                              onLongClick = {
                                activePlaylist = playlistWithCount.playlist
                              },
                              isGridMode = true,
                              gridColumns = videoGridColumns,
                              mostRecentVideoPath = playlistWithCount.firstItemPath,
                            )
                          }
                        }
                        val emptySlots = videoGridColumns - rowItems.size
                        repeat(emptySlots) {
                          Spacer(modifier = Modifier.weight(1f))
                        }
                      }
                    }
                  }
                } else {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    previewPlaylists.forEach { playlistWithCount ->
                      PlaylistCard(
                        playlist = playlistWithCount.playlist,
                        itemCount = playlistWithCount.itemCount,
                        uiSettings = playlistUiSettings,
                        onClick = {
                          backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
                        },
                        onLongClick = {
                          activePlaylist = playlistWithCount.playlist
                        },
                        isGridMode = false,
                        mostRecentVideoPath = playlistWithCount.firstItemPath,
                        // List rows take the connected-list radius of the sheet.
                        shape = RoundedCornerShape(2.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // ==========================================
    // ITEM ACTIONS BOTTOM SHEETS
    // ==========================================

    // 1. Video Options Sheet
    if (activeVideoItem != null) {
      val video = activeVideoItem!!.video
      ModalBottomSheet(
        onDismissRequest = { activeVideoItem = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.PlayArrow,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = video.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${video.durationFormatted} • ${video.sizeFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.Filled.PlayArrow,
            label = stringResource(R.string.play),
            onClick = {
              val v = video
              activeVideoItem = null
              MediaUtils.playFile(v, context, "you_tab")
            },
          )
          ActionSheetItem(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = stringResource(R.string.add_to_playlist),
            onClick = {
              val v = video
              activeVideoItem = null
              videoForPlaylist = v
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.info),
            onClick = {
              val v = video
              activeVideoItem = null
              videoForInfo = v
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Share,
            label = stringResource(R.string.generic_share),
            onClick = {
              val v = video
              activeVideoItem = null
              MediaUtils.shareVideos(context, listOf(v))
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.remove),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val itemToDelete = activeVideoItem
              activeVideoItem = null
              if (itemToDelete != null) {
                deleteFilesCheckbox.value = false
                videoToDeleteFromRecents = itemToDelete
              }
            },
          )
        }
      }
    }

    // 2. Playlist Options Sheet
    if (activePlaylist != null) {
      val playlist = activePlaylist!!
      ModalBottomSheet(
        onDismissRequest = { activePlaylist = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = if (playlist.isM3uPlaylist) "Network Playlist" else "Local Playlist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = stringResource(R.string.playlists),
            onClick = {
              val p = playlist
              activePlaylist = null
              backStack.add(PlaylistDetailScreen(p.id))
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.rename),
            onClick = {
              val p = playlist
              activePlaylist = null
              renameText = p.name
              playlistToRename = p
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val p = playlist
              activePlaylist = null
              playlistToDelete = p
            },
          )
        }
      }
    }

    // ==========================================
    // ACTION DIALOGS & OVERLAYS
    // ==========================================

    // Add To Playlist Dialog
    if (videoForPlaylist != null) {
      AddToPlaylistDialog(
        isOpen = true,
        videos = listOf(videoForPlaylist!!),
        onDismiss = { videoForPlaylist = null },
        onSuccess = {
          videoForPlaylist = null
          playlistViewModel.loadData()
        },
      )
    }

    // Media Info Sheet
    if (videoForInfo != null) {
      MediaInfoSheet(
        uri = videoForInfo!!.uri,
        onDismiss = { videoForInfo = null },
      )
    }

    // Playlist Rename Dialog
    if (playlistToRename != null) {
      AlertDialog(
        onDismissRequest = { playlistToRename = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
          OutlinedTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        confirmButton = {
          TextButton(
            enabled = renameText.isNotBlank(),
            onClick = {
              val target = playlistToRename!!
              val newName = renameText.trim()
              playlistToRename = null
              scope.launch {
                playlistRepository.updatePlaylist(target.copy(name = newName))
                playlistViewModel.loadData()
              }
            },
          ) {
            Text(stringResource(R.string.rename))
          }
        },
        dismissButton = {
          TextButton(onClick = { playlistToRename = null }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }

    // Playlist Delete Dialog
    if (playlistToDelete != null) {
      AlertDialog(
        onDismissRequest = { playlistToDelete = null },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text("Delete playlist \"${playlistToDelete!!.name}\"?") },
        confirmButton = {
          TextButton(
            onClick = {
              val target = playlistToDelete!!
              playlistToDelete = null
              scope.launch {
                playlistRepository.deletePlaylist(target)
                playlistViewModel.loadData()
              }
            },
          ) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { playlistToDelete = null }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }

    // Recently Played Video Delete / Remove Confirmation Dialog
    if (videoToDeleteFromRecents != null) {
      val target = videoToDeleteFromRecents!!
      val itemText = pluralStringResource(R.plurals.item_type_item_plural, 1)
      val deleteFiles = deleteFilesCheckbox.value

      val title = if (deleteFiles) {
        stringResource(R.string.delete_files_title, 1, itemText)
      } else {
        stringResource(R.string.remove_from_history_title, 1, itemText)
      }

      val subtitle = if (deleteFiles) {
        stringResource(R.string.delete_files_msg)
      } else {
        stringResource(R.string.remove_from_history_msg, itemText)
      }

      ConfirmDialog(
        title = title,
        subtitle = subtitle,
        customContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = deleteFilesCheckbox.value,
              onCheckedChange = {
                deleteFilesCheckbox.value = it
              },
            )
            Text(
              text = stringResource(R.string.also_delete_files),
              modifier = Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        },
        onConfirm = {
          val toDelete = target
          val shouldDeleteFiles = deleteFilesCheckbox.value
          videoToDeleteFromRecents = null
          deleteFilesCheckbox.value = false
          scope.launch {
            recentsViewModel.deleteRecentItems(listOf(toDelete), shouldDeleteFiles)
          }
        },
        onCancel = {
          videoToDeleteFromRecents = null
          deleteFilesCheckbox.value = false
        },
      )
    }

    // Profile Customization Dialog (Easter Egg)
    if (showProfileCustomizationDialog) {
      ProfileCustomizationDialog(
        initialName = customProfileName,
        currentImagePath = customProfileImagePath,
        currentImageBitmap = customAvatarBitmap,
        onDismissRequest = { showProfileCustomizationDialog = false },
        onSave = { newName, newImagePath ->
          appearancePreferences.customProfileName.set(newName)
          appearancePreferences.customProfileImagePath.set(newImagePath)
          showProfileCustomizationDialog = false
        },
      )
    }
  }

  /**
   * Action Sheet Item Row
   */
  @Composable
  private fun ActionSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
  ) {
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(12.dp),
      color = Color.Transparent,
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(24.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Normal,
          color = tint,
        )
      }
    }
  }

  /**
   * Top Profile & Quick Actions header for 'You' tab
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun YouProfileHeader(
    name: String,
    avatarBitmap: ImageBitmap?,
    onAvatarLongClick: () -> Unit,
    recentCount: Int,
    playlistCount: Int,
    onHistoryClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onNetworkClick: (() -> Unit)? = null,
    onShortsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
  ) {
    val haptic = LocalHapticFeedback.current

    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(top = 16.dp, bottom = 2.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Profile Info Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              shape = CircleShape,
            )
            .combinedClickable(
              onClick = {},
              onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAvatarLongClick()
              },
            ),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (avatarBitmap != null) {
              Image(
                bitmap = avatarBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
              )
            }
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = name.ifBlank { stringResource(R.string.app_name) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          val stats = buildList {
            if (recentCount > 0) add("$recentCount recent")
            if (playlistCount > 0) add("$playlistCount ${if (playlistCount == 1) "playlist" else "playlists"}")
          }.joinToString(" • ")

          if (stats.isNotBlank()) {
            Text(
              text = stats,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Quick action shortcut buttons (compact and scrollable if multiple chips)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CompactQuickActionChip(
          icon = Icons.Filled.History,
          label = stringResource(R.string.recently_played),
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onHistoryClick()
          },
        )
        CompactQuickActionChip(
          icon = Icons.AutoMirrored.Filled.PlaylistPlay,
          label = stringResource(R.string.playlists),
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPlaylistsClick()
          },
        )
        if (onNetworkClick != null) {
          CompactQuickActionChip(
            icon = Icons.Filled.Language,
            label = stringResource(R.string.network),
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onNetworkClick()
            },
          )
        }
        if (onShortsClick != null) {
          CompactQuickActionChip(
            icon = Icons.Outlined.VideoLibrary,
            label = stringResource(R.string.shorts),
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onShortsClick()
            },
          )
        }
      }
    }
  }

  @Composable
  private fun CompactQuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Surface(
      onClick = onClick,
      shape = pillShape,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      ),
      contentColor = MaterialTheme.colorScheme.onSurface,
      modifier = modifier.height(34.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
      }
    }
  }

  /**
   * Section Header with Title and "View all" navigation action
   */
  @Composable
  private fun SectionHeader(
    title: String,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      TextButton(
        onClick = onViewAllClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
      ) {
        Text(
          text = stringResource(R.string.view_all),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }

  /**
   * Card face of the shelves. The content surface sheet is painted in the same
   * colour, so the cards only read as cards through the 3.dp gaps and the tonal
   * contrast of their thumbnails against it.
   */
  @Composable
  private fun cardSurfaceColor(): Color = if (MaterialTheme.colorScheme.background == Color.Black) {
    Color.Black
  } else {
    MaterialTheme.colorScheme.surface
  }

  /**
   * Compact card for recently played video in horizontal shelf
   * Guarantees fixed dimensions to prevent layout shifts during horizontal scrolling
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun RecentlyPlayedVideoShelfCard(
    video: Video,
    progressPercentage: Float?,
    isWatched: Boolean,
    isRecentlyPlayed: Boolean,
    showThumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardRadius: Dp = 4.dp,
  ) {
    val thumbnailRepository = koinInject<ThumbnailRepository>()
    val density = LocalDensity.current
    val thumbWidthDp = 160.dp
    val aspect = 16f / 9f
    val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
    val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

    val thumbnailKey = remember(video.id, video.dateModified, video.size, thumbWidthPx, thumbHeightPx) {
      thumbnailRepository.thumbnailKey(video, thumbWidthPx, thumbHeightPx)
    }

    var thumbnail by remember(thumbnailKey) {
      mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
    }

    LaunchedEffect(thumbnailKey) {
      thumbnailRepository.thumbnailReadyKeys.filter { it == thumbnailKey }.collect {
        thumbnail = thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx)
      }
    }

    LaunchedEffect(thumbnailKey, showThumbnails) {
      if (thumbnail == null && showThumbnails) {
        thumbnail = withContext(Dispatchers.IO) {
          thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
        }
      }
    }

    Card(
      modifier = modifier
        .height(148.dp)
        .clip(RoundedCornerShape(cardRadius))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(cardRadius),
      colors = CardDefaults.cardColors(containerColor = cardSurfaceColor()),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        // 16:9 Thumbnail Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          if (showThumbnails && thumbnail != null) {
            Image(
              bitmap = thumbnail!!.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          } else {
            Icon(
              imageVector = if (video.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(36.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }

          // Progress Bar
          if (progressPercentage != null && !isWatched) {
            LinearProgressIndicator(
              progress = { progressPercentage },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
              color = MaterialTheme.colorScheme.primary,
              trackColor = Color.Black.copy(alpha = 0.35f),
            )
          }

          // Duration overlay
          if (video.durationFormatted.isNotBlank() && video.durationFormatted != "--:--" && video.durationFormatted != "00:00") {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color.Black.copy(alpha = 0.72f),
              contentColor = Color.White,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            ) {
              Text(
                text = video.durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Fixed height text container guarantees NO jumping effect on horizontal scroll
        Column(
          modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = video.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  /**
   * Compact card for playlist in horizontal shelf
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun PlaylistShelfCard(
    playlist: PlaylistEntity,
    itemCount: Int,
    mostRecentVideoPath: String? = null,
    showThumbnails: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardRadius: Dp = 4.dp,
  ) {
    val thumbnailRepository = koinInject<ThumbnailRepository>()
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbWidthDp = 160.dp
    val aspect = 16f / 9f
    val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
    val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

    var resolvedVideo by remember(mostRecentVideoPath) { mutableStateOf<Video?>(null) }

    LaunchedEffect(mostRecentVideoPath) {
      if (mostRecentVideoPath.isNullOrBlank()) {
        resolvedVideo = null
      } else {
        resolvedVideo = withContext(Dispatchers.IO) {
          val isNetwork = mostRecentVideoPath.startsWith("http://") || mostRecentVideoPath.startsWith("https://")
          if (isNetwork) null else VideoScanUtils.getVideoByPath(context, mostRecentVideoPath)
        }
      }
    }

    val dummyVideo = remember(mostRecentVideoPath) {
      if (mostRecentVideoPath.isNullOrBlank()) null
      else {
        val isNetwork = mostRecentVideoPath.startsWith("http://") || mostRecentVideoPath.startsWith("https://")
        val file = if (!isNetwork) java.io.File(mostRecentVideoPath) else null
        val exists = file?.exists() == true && file.isFile
        val size = if (exists) file.length() else 0L
        val dateModified = if (exists) file.lastModified() / 1000 else 0L

        Video(
          id = mostRecentVideoPath.hashCode().toLong(),
          title = playlist.name,
          displayName = playlist.name,
          path = mostRecentVideoPath,
          uri = if (isNetwork) {
            android.net.Uri.parse(mostRecentVideoPath)
          } else {
            android.net.Uri.fromFile(file ?: java.io.File(mostRecentVideoPath))
          },
          duration = 0,
          durationFormatted = "",
          size = size,
          sizeFormatted = "",
          dateModified = dateModified,
          dateAdded = dateModified,
          mimeType = "video/*",
          bucketId = "",
          bucketDisplayName = "",
          width = 0,
          height = 0,
          fps = 0f,
          resolution = ""
        )
      }
    }

    val activeVideo = resolvedVideo ?: dummyVideo

    val thumbnailKey = remember(activeVideo?.id, activeVideo?.dateModified, activeVideo?.size, activeVideo?.duration, thumbWidthPx, thumbHeightPx) {
      activeVideo?.let { thumbnailRepository.thumbnailKey(it, thumbWidthPx, thumbHeightPx) }
    }

    var thumbnail by remember(thumbnailKey) {
      mutableStateOf(
        if (activeVideo != null && thumbnailKey != null && showThumbnails) {
          thumbnailRepository.getThumbnailFromMemory(activeVideo, thumbWidthPx, thumbHeightPx)
        } else null
      )
    }

    LaunchedEffect(thumbnailKey) {
      if (thumbnailKey != null && activeVideo != null) {
        thumbnailRepository.thumbnailReadyKeys.filter { it == thumbnailKey }.collect {
          thumbnail = thumbnailRepository.getThumbnailFromMemory(activeVideo, thumbWidthPx, thumbHeightPx)
        }
      }
    }

    LaunchedEffect(thumbnailKey, showThumbnails) {
      if (thumbnailKey != null && activeVideo != null && thumbnail == null && showThumbnails) {
        thumbnail = withContext(Dispatchers.IO) {
          thumbnailRepository.getThumbnail(activeVideo, thumbWidthPx, thumbHeightPx)
        }
      }
    }

    val colorScheme = MaterialTheme.colorScheme
    val fallbackPalettes = remember(colorScheme) {
      listOf(
        colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
        colorScheme.secondaryContainer to colorScheme.onSecondaryContainer,
        colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
        colorScheme.surfaceContainerHighest to colorScheme.primary,
        colorScheme.primary.copy(alpha = 0.22f) to colorScheme.primary,
        colorScheme.tertiary.copy(alpha = 0.22f) to colorScheme.tertiary,
      )
    }
    val paletteIndex = remember(playlist.name, playlist.id) {
      val seed = playlist.name.ifBlank { playlist.id.toString() }
      kotlin.math.abs(seed.hashCode()) % fallbackPalettes.size
    }
    val (fallbackBgColor, fallbackIconColor) = fallbackPalettes[paletteIndex]

    Card(
      modifier = modifier
        .height(148.dp)
        .clip(RoundedCornerShape(cardRadius))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(cardRadius),
      colors = CardDefaults.cardColors(containerColor = cardSurfaceColor()),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          if (showThumbnails && thumbnail != null) {
            Image(
              bitmap = thumbnail!!.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )

            val iconVector = if (playlist.isM3uPlaylist) Icons.Filled.Stream else Icons.AutoMirrored.Filled.PlaylistPlay
            val iconSize = 36.dp

            Icon(
              imageVector = iconVector,
              contentDescription = null,
              modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 1.dp, y = 1.dp)
                .size(iconSize),
              tint = Color.Black.copy(alpha = 0.45f),
            )
            Icon(
              imageVector = iconVector,
              contentDescription = "Playlist",
              modifier = Modifier
                .align(Alignment.Center)
                .size(iconSize),
              tint = Color.White.copy(alpha = 0.95f),
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      fallbackBgColor,
                      fallbackBgColor.copy(alpha = 0.65f),
                    )
                  )
                ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (playlist.isM3uPlaylist) Icons.Filled.Stream else Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = fallbackIconColor.copy(alpha = 0.85f),
              )
            }
          }

          if (itemCount > 0) {
            Surface(
              shape = pillShape,
              color = Color.Black.copy(alpha = 0.72f),
              contentColor = Color.White,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            ) {
              Text(
                text = "$itemCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
          modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Text(
            text = if (playlist.isM3uPlaylist) "Network" else "Local",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }

  /**
   * Styled inline empty card for shelves
   */
  @Composable
  private fun ShelfEmptyCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
  ) {
    Card(
      // Sits on the sheet itself: the same face colour, with an outline ring so
      // it still reads as a distinct block against the white.
      colors = CardDefaults.cardColors(containerColor = cardSurfaceColor()),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      shape = RoundedCornerShape(4.dp),
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.size(42.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }

  @Composable
  private fun ProfileCustomizationDialog(
    initialName: String,
    currentImagePath: String,
    currentImageBitmap: ImageBitmap?,
    onDismissRequest: () -> Unit,
    onSave: (newName: String, newImagePath: String) -> Unit,
  ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nameText by rememberSaveable { mutableStateOf(initialName) }
    var selectedBitmap by remember { mutableStateOf(currentImageBitmap) }
    var tempCopiedFile by remember { mutableStateOf<java.io.File?>(null) }
    var isImageRemoved by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
      if (uri != null) {
        scope.launch(Dispatchers.IO) {
          try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
              val original = BitmapFactory.decodeStream(inputStream)
              if (original != null) {
                val maxDim = 512
                val width = original.width
                val height = original.height
                val scale = maxDim.toFloat() / maxOf(width, height)
                val targetWidth = if (scale < 1f) (width * scale).roundToInt().coerceAtLeast(1) else width
                val targetHeight = if (scale < 1f) (height * scale).roundToInt().coerceAtLeast(1) else height
                val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)

                val tempFile = java.io.File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.png")
                tempFile.outputStream().use { out ->
                  scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                withContext(Dispatchers.Main) {
                  selectedBitmap = scaled.asImageBitmap()
                  tempCopiedFile = tempFile
                  isImageRemoved = false
                }
              }
            }
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }
    }

    AlertDialog(
      onDismissRequest = {
        tempCopiedFile?.delete()
        onDismissRequest()
      },
      title = {
        Text(
          text = "Customize Profile",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Avatar preview with click to change
          Box(
            contentAlignment = Alignment.BottomEnd,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(
                  width = 1.5.dp,
                  color = MaterialTheme.colorScheme.outlineVariant,
                  shape = CircleShape,
                )
                .clickable {
                  photoPickerLauncher.launch("image/*")
                },
            ) {
              Box(contentAlignment = Alignment.Center) {
                if (selectedBitmap != null) {
                  Image(
                    bitmap = selectedBitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(60.dp),
                  )
                }
              }
            }

            // Small badge on bottom-right of avatar
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable {
                  photoPickerLauncher.launch("image/*")
                },
              shadowElevation = 2.dp,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.AddPhotoAlternate,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          // Action buttons row for image (Choose / Remove)
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = { photoPickerLauncher.launch("image/*") },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (selectedBitmap != null) "Change Photo" else "Choose Photo")
            }

            if (selectedBitmap != null) {
              TextButton(
                onClick = {
                  selectedBitmap = null
                  tempCopiedFile?.delete()
                  tempCopiedFile = null
                  isImageRemoved = true
                },
                colors = ButtonDefaults.textButtonColors(
                  contentColor = MaterialTheme.colorScheme.error,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove")
              }
            }
          }

          // Display name text field
          OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text(stringResource(R.string.name)) },
            placeholder = { Text(stringResource(R.string.app_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
              if (nameText.isNotEmpty()) {
                IconButton(onClick = { nameText = "" }) {
                  Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear",
                  )
                }
              }
            },
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch(Dispatchers.IO) {
              val finalPath = when {
                tempCopiedFile != null -> {
                  // Delete existing avatar files
                  context.filesDir.listFiles { file -> file.name.startsWith("custom_profile_avatar") }?.forEach { it.delete() }
                  val permanentFile = java.io.File(context.filesDir, "custom_profile_avatar_${System.currentTimeMillis()}.png")
                  tempCopiedFile!!.copyTo(permanentFile, overwrite = true)
                  tempCopiedFile!!.delete()
                  permanentFile.absolutePath
                }
                isImageRemoved -> {
                  context.filesDir.listFiles { file -> file.name.startsWith("custom_profile_avatar") }?.forEach { it.delete() }
                  ""
                }
                else -> currentImagePath
              }
              withContext(Dispatchers.Main) {
                onSave(nameText.trim(), finalPath)
              }
            }
          },
        ) {
          Text(stringResource(R.string.save))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            tempCopiedFile?.delete()
            onDismissRequest()
          },
        ) {
          Text(stringResource(R.string.generic_cancel))
        }
      },
    )
  }
}
