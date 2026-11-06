package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.ui.theme.pillShape
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.VideoScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Redesigned Card for displaying a playlist item.
 * Exactly matches MediaCard (VideoCard) measurements, aspect ratio (16:9), and styling.
 * Displays the first media item's thumbnail with an overlay playlist icon,
 * or falls back to a deterministic Material-colored styled card for online M3U / unextractable streams.
 */
@Composable
fun PlaylistCard(
  playlist: PlaylistEntity,
  itemCount: Int,
  uiSettings: UiSettings,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  onThumbClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  isGridMode: Boolean = false,
  gridColumns: Int = 1,
  mostRecentVideoPath: String? = null,
  isRecentlyPlayed: Boolean = false,
  thumbnailSize: Dp = 128.dp,
  thumbnailAspectRatio: Float = 16f / 9f,
  shape: Shape? = null,
) {
  val maxLines = if (uiSettings.unlimitedNameLines) Int.MAX_VALUE else 2

  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val configuration = LocalConfiguration.current
  val thumbWidthDp = if (isGridMode) {
    if (gridColumns == 1) configuration.screenWidthDp.dp else 180.dp
  } else thumbnailSize
  val aspect = thumbnailAspectRatio
  val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

  val context = LocalContext.current
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

  // Fast fallback dummy video while resolvedVideo loads or for network streams
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
      if (activeVideo != null && thumbnailKey != null && uiSettings.showVideoThumbnails) {
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

  LaunchedEffect(thumbnailKey, uiSettings.showVideoThumbnails) {
    if (thumbnailKey != null && activeVideo != null && thumbnail == null && uiSettings.showVideoThumbnails) {
      thumbnail = withContext(Dispatchers.IO) {
        thumbnailRepository.getThumbnail(activeVideo, thumbWidthPx, thumbHeightPx)
      }
    }
  }

  // Deterministic Material palette fallback when thumbnail is unavailable (e.g. online M3U)
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

  BaseMediaCard(
    title = playlist.name,
    listTitleStyle = MaterialTheme.typography.bodyMedium,
    titleTextAlign = TextAlign.Start,
    modifier = modifier,
    thumbnailAspectRatio = aspect,
    thumbnailSize = thumbWidthDp,
    thumbnail = if (uiSettings.showVideoThumbnails) thumbnail?.asImageBitmap() else null,
    thumbnailIcon = {
      // Deterministic Material styled fallback
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
          modifier = Modifier.size(if (isGridMode) 44.dp else 38.dp),
          tint = fallbackIconColor.copy(alpha = 0.85f),
        )
      }
    },
    onClick = onClick,
    onLongClick = onLongClick,
    onThumbClick = onThumbClick,
    isSelected = isSelected,
    isRecentlyPlayed = isRecentlyPlayed,
    isWatched = false,
    isGridMode = isGridMode,
    gridColumns = gridColumns,
    maxTitleLines = maxLines,
    shape = shape,
    overlayContent = {
      // Center Playlist Icon over the video thumbnail (exact same size as fallback, no circular shape)
      if (thumbnail != null) {
        val iconVector = if (playlist.isM3uPlaylist) Icons.Filled.Stream else Icons.AutoMirrored.Filled.PlaylistPlay
        val iconSize = if (isGridMode) 44.dp else 38.dp

        // Subtle drop shadow for crisp contrast against any video frame
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
      }

      // Bottom-end badge: item count pill (only in grid mode)
      if (isGridMode && itemCount > 0) {
        Surface(
          shape = pillShape,
          color = Color.Black.copy(alpha = 0.72f),
          contentColor = Color.White,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
              tint = Color.White,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (itemCount == 1) "1 item" else "$itemCount items",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    },
    chipsContent = if (isGridMode) null else ({
      val chipText = if (playlist.id < 0) "Auto" else if (playlist.isM3uPlaylist) "Network" else "Local"
      val (chipColor, chipBgColor) = if (playlist.id < 0) {
        MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
      } else if (playlist.isM3uPlaylist) {
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
      } else {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
      }
      MediaMetadataChip(
        text = chipText,
        color = chipBgColor,
        contentColor = chipColor,
      )

      MediaMetadataChip(
        text = if (itemCount == 1) "1 item" else "$itemCount items",
      )

      if (uiSettings.showDateChip && playlist.updatedAt > 0) {
        MediaMetadataChip(
          text = MediaFormatter.formatDate(playlist.updatedAt),
        )
      }
    }),
  )
}
