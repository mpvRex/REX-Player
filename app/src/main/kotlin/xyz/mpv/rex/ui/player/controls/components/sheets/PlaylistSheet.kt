package xyz.mpv.rex.ui.player.controls.components.sheets

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import xyz.mpv.rex.utils.media.MediaThumbnailUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaylistItem(
  val uri: Uri,
  val title: String,
  val index: Int,
  val isPlaying: Boolean,
  val progressPercent: Float = 0f, // 0-100, progress of video watched
  val isWatched: Boolean = false,  // True if video is fully watched (100%)
  val path: String = "", // Video path for thumbnail loading
  val duration: String = "", // Duration in formatted string (e.g., "10:30")
  val resolution: String = "", // Resolution (e.g., "1920x1080")
  val isAudio: Boolean = false,
)

/**
 * LRU (Least Recently Used) cache for Bitmap thumbnails with a maximum size limit.
 * This prevents memory issues when dealing with large playlists (100+ videos).
 */
class LRUBitmapCache(private val maxSize: Int) {
  private val cache = LinkedHashMap<String, Bitmap?>(maxSize + 1, 1f, true)

  operator fun get(key: String): Bitmap? = synchronized(this) { cache[key] }

  operator fun set(key: String, value: Bitmap?) = synchronized(this) {
    cache[key] = value
    if (cache.size > maxSize) {
      // Remove the least recently used item
      cache.remove(cache.keys.firstOrNull())
    }
  }

  fun containsKey(key: String): Boolean = synchronized(this) { cache.containsKey(key) }

  fun clear() = synchronized(this) { cache.clear() }
}

@Composable
fun PlaylistSheet(
  playlist: ImmutableList<PlaylistItem>,
  onDismissRequest: () -> Unit,
  onItemClick: (PlaylistItem) -> Unit,
  onReorderItem: (Int, Int) -> Unit = { _, _ -> },
  onRemoveItems: (List<Int>) -> Unit = {},
  totalCount: Int = playlist.size,
  isM3UPlaylist: Boolean = false,
  playerPreferences: xyz.mpv.rex.preferences.PlayerPreferences,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current

  val accentColor = MaterialTheme.colorScheme.primary

  // Search state
  var isSearching by remember { mutableStateOf(false) }
  var searchQuery by rememberSaveable { mutableStateOf("") }

  // Reorder mode state
  var isReorderMode by remember { mutableStateOf(false) }

  // Selection mode state
  var isInSelectionMode by remember { mutableStateOf(false) }
  val selectedIndexes = remember { mutableStateListOf<Int>() }

  // Filtered playlist based on search query
  val filteredPlaylist by remember(playlist, searchQuery) {
    derivedStateOf {
      if (searchQuery.isBlank()) {
        playlist
      } else {
        playlist.filter { it.title.contains(searchQuery, ignoreCase = true) }
      }
    }
  }

  // Thumbnail cache with LRU eviction - limited size to prevent memory issues with large playlists
  val thumbnailCache by remember {
    mutableStateOf(LRUBitmapCache(maxSize = 50))
  }

  // Scroll state for the playlist
  val lazyListState = rememberLazyListState()

  // Find the currently playing item index in the filtered list
  val playingItemIndex by remember(filteredPlaylist) {
    derivedStateOf {
      filteredPlaylist.indexOfFirst { it.isPlaying }
    }
  }

  // Scroll to the currently playing item when the playing item changes or when sheet opens
  LaunchedEffect(playingItemIndex) {
    if (playingItemIndex >= 0) {
      lazyListState.animateScrollToItem(playingItemIndex)
    }
  }

  // Setup reorderable state
  val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
    onReorderItem(from.index, to.index)
  }

  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val screenHeight = LocalConfiguration.current.screenHeightDp.dp

  PlayerSheet(
    onDismissRequest = onDismissRequest,
    modifier = Modifier.fillMaxSize(),
    customMaxHeight = screenHeight,
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color.Transparent,
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
      ),
      tonalElevation = 0.dp,
    ) {
      Column(
        modifier = modifier
          .fillMaxSize()
          .padding(
            top = 0.dp,
            bottom = MaterialTheme.spacing.smaller,
            start = 0.dp,
            end = 0.dp
          )
      ) {
        // Header showing current playlist info with search and reorder options
        val currentItem = playlist.find { it.isPlaying }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              horizontal = MaterialTheme.spacing.medium,
              vertical = 0.dp,
            ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          if (isInSelectionMode) {
            // Selection Mode Header
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              modifier = Modifier.weight(1f)
            ) {
              IconButton(
                onClick = {
                  isInSelectionMode = false
                  selectedIndexes.clear()
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Cancel Selection",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
              Text(
                text = stringResource(R.string.playlist_selected_count, selectedIndexes.size),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface
              )
            }

            IconButton(
              onClick = {
                onRemoveItems(selectedIndexes.toList())
                isInSelectionMode = false
                selectedIndexes.clear()
              },
              enabled = selectedIndexes.isNotEmpty()
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Selected",
                tint = if (selectedIndexes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
              )
            }
          } else if (isReorderMode) {
            // Reorder Mode Header
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              modifier = Modifier.weight(1f)
            ) {
              Text(
                text = stringResource(R.string.playlist_reorder),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = accentColor,
                ),
              )
            }

            IconButton(
              onClick = { isReorderMode = false }
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else if (isSearching) {
            // Search Input taking place of Now Playing division
            BasicTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              modifier = Modifier
                .weight(1f)
                .padding(end = MaterialTheme.spacing.small),
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
              decorationBox = { innerTextField ->
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  contentAlignment = Alignment.CenterStart
                ) {
                  if (searchQuery.isEmpty()) {
                    Text(
                      text = stringResource(R.string.playlist_search_placeholder),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                  }
                  innerTextField()
                }
              }
            )

            IconButton(
              onClick = {
                searchQuery = ""
                isSearching = false
              }
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            // Original Now Playing / Total items layout
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              modifier = Modifier.weight(1f)
            ) {
              if (currentItem != null) {
                val playingIndex = playlist.indexOfFirst { it.isPlaying }
                val playingNumber = if (playingIndex != -1) playingIndex + 1 else 1
                Text(
                  text = stringResource(R.string.playlist_now_playing),
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                  ),
                )
                Text(
                  text = "•",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = stringResource(R.string.playlist_items_count, playingNumber, totalCount),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                Text(
                  text = stringResource(R.string.playlist_items_count, filteredPlaylist.size, totalCount),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = { isSearching = true }
              ) {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = "Search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              IconButton(
                onClick = { isReorderMode = true }
              ) {
                Icon(
                  imageVector = Icons.Outlined.SwapVert,
                  contentDescription = "Reorder",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Playlist/Track list
        if (filteredPlaylist.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(R.string.playlist_no_items),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          LazyColumn(
            state = lazyListState,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            items(filteredPlaylist, key = { "${it.index}_${it.uri}" }) { item ->
              if (isReorderMode) {
                ReorderableItem(reorderState, key = "${item.index}_${item.uri}") {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(modifier = Modifier.weight(1f)) {
                      PlaylistTrackListItem(
                        item = item,
                        context = context,
                        thumbnailCache = thumbnailCache,
                        onClick = { onItemClick(item) },
                        skipThumbnail = isM3UPlaylist,
                        accentColor = accentColor
                      )
                    }
                    IconButton(
                      onClick = { },
                      modifier = Modifier
                        .size(48.dp)
                        .draggableHandle(),
                    ) {
                      Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = accentColor,
                      )
                    }
                  }
                }
              } else {
                val isSelected = selectedIndexes.contains(item.index)
                PlaylistTrackListItem(
                  item = item,
                  context = context,
                  thumbnailCache = thumbnailCache,
                  onClick = { onItemClick(item) },
                  isInSelectionMode = isInSelectionMode,
                  isSelected = isSelected,
                  onToggleSelection = {
                    if (selectedIndexes.contains(item.index)) {
                      selectedIndexes.remove(item.index)
                      if (selectedIndexes.isEmpty()) {
                        isInSelectionMode = false
                      }
                    } else {
                      selectedIndexes.add(item.index)
                      isInSelectionMode = true
                    }
                  },
                  skipThumbnail = isM3UPlaylist,
                  accentColor = accentColor
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackListItem(
  item: PlaylistItem,
  context: Context,
  thumbnailCache: LRUBitmapCache,
  onClick: () -> Unit,
  isInSelectionMode: Boolean = false,
  isSelected: Boolean = false,
  onToggleSelection: () -> Unit = {},
  skipThumbnail: Boolean = false,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  // Use theme colors dynamically
  val accentSecondary = MaterialTheme.colorScheme.primaryContainer

  // Thumbnail state - uses cache to persist across recompositions
  val videoPath = item.path.ifBlank { item.uri.toString() }
  var thumbnail by remember(videoPath) {
    mutableStateOf(thumbnailCache[videoPath])
  }

  // Load thumbnail asynchronously using ThumbnailRepository / folder browser thumbnails
  // Skip thumbnail loading for M3U playlists (network streams)
  LaunchedEffect(videoPath) {
    if (!skipThumbnail && thumbnailCache[videoPath] == null) {
      val bmp = MediaThumbnailUtils.getCachedThumbnailForPath(context, videoPath, item.uri)
      thumbnail = bmp
      if (bmp != null) {
        thumbnailCache[videoPath] = bmp
      }
    }
  }

  val borderModifier = if (isSelected) {
    Modifier.border(
      width = 2.dp,
      color = Color.Red,
      shape = RoundedCornerShape(12.dp),
    )
  } else if (item.isPlaying) {
    Modifier.border(
      width = 2.dp,
      brush = Brush.linearGradient(listOf(accentColor, accentSecondary)),
      shape = RoundedCornerShape(12.dp),
    )
  } else {
    Modifier
  }

  val clickModifier = if (isInSelectionMode) {
    Modifier.clickable(onClick = onToggleSelection)
  } else {
    Modifier.combinedClickable(
      onClick = onClick,
      onLongClick = onToggleSelection
    )
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(
        horizontal = MaterialTheme.spacing.medium,
        vertical = MaterialTheme.spacing.extraSmall,
      )
      .clip(RoundedCornerShape(12.dp))
      .then(borderModifier)
      .then(clickModifier),
    color = if (isSelected) {
      Color.Red.copy(alpha = 0.2f)
    } else if (item.isPlaying) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
      Color.Transparent
    },
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.smaller),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      // Thumbnail with simple background, episode number, and progress
      Box(
        modifier = Modifier
          .width(100.dp)
          .height(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        // Show actual thumbnail or fallback icon
        thumbnail?.let { bmp ->
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Thumbnail",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
          )
        } ?: run {
          // Placeholder icon: Music note for audio, movie icon for video
          Icon(
            imageVector = if (item.isAudio) Icons.Filled.MusicNote else Icons.Outlined.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp),
          )
        }

        // Video number badge in top-left with better visibility
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .background(
              color = Color.Black.copy(alpha = 0.7f),
              shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text(
            text = "${item.index + 1}",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            ),
            color = Color.White,
          )
        }
      }

      // Title and info
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (item.isPlaying) FontWeight.Bold else FontWeight.Normal,
            color = if (item.isPlaying) {
              accentColor
            } else if (item.isWatched) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        // Duration and resolution chips - always show with loading state if empty
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Duration chip
          if (item.duration.isNotEmpty()) {
            Surface(
              color = if (item.isPlaying) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
              shape = RoundedCornerShape(4.dp),
            ) {
              Text(
                text = item.duration,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                ),
                color = if (item.isPlaying) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LoadingChip(width = 40.dp)
          }
          
          // Resolution chip (only for video files)
          if (!item.isAudio) {
            if (item.resolution.isNotEmpty()) {
              Surface(
                color = if (item.isPlaying) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(4.dp),
              ) {
                Text(
                  text = item.resolution,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                  ),
                  color = if (item.isPlaying) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              LoadingChip(width = 60.dp)
            }
          }
        }
      }

      // Status badges
      when {
        item.isPlaying -> {
          Surface(
            color = accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text(
              text = stringResource(R.string.playlist_playing),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
              ),
            )
          }
        }

      }
    }
  }
}




@Composable
fun LoadingChip(
  width: androidx.compose.ui.unit.Dp,
  height: androidx.compose.ui.unit.Dp = 18.dp,
  isDark: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
  val shimmerTranslate = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmer"
  )

  val baseColor = if (isDark) {
    Color.White.copy(alpha = 0.1f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHighest
  }
  
  val shimmerColor = if (isDark) {
    Color.White.copy(alpha = 0.2f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }

  Box(
    modifier = modifier
      .width(width)
      .height(height)
      .clip(RoundedCornerShape(4.dp))
      .background(
        brush = Brush.linearGradient(
          colors = listOf(
            baseColor,
            shimmerColor,
            baseColor,
          ),
          start = Offset(shimmerTranslate.value - 200f, 0f),
          end = Offset(shimmerTranslate.value, 0f)
        )
      )
  )
}
