package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.pillShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ListItemDefaults.verticalAlignment
import org.koin.compose.koinInject
import kotlin.math.pow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.ImageBitmap

import xyz.mpv.rex.preferences.UiSettings

@Composable
fun FolderCard(
  folder: VideoFolder,
  onClick: () -> Unit,
  uiSettings: UiSettings,
  modifier: Modifier = Modifier,
  isRecentlyPlayed: Boolean = false,
  isNeverPlayed: Boolean = false,
  isWatched: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  onThumbClick: (() -> Unit)? = null,
  showDateModified: Boolean = false,
  customIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  newVideoCount: Int = 0,
  customChipContent: @Composable (() -> Unit)? = null,
  isGridMode: Boolean = false,
  gridColumns: Int = 1,
  thumbnailSize: androidx.compose.ui.unit.Dp = 64.dp,
  thumbnailAspectRatio: Float = 1f,
  thumbnail: ImageBitmap? = null,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
  val totalCount = if (showAudioFiles) folder.videoCount + folder.audioCount else folder.videoCount
  val countLabel = if (totalCount == 1) "1 Item" else "$totalCount Items"
  val maxLines = if (uiSettings.unlimitedNameLines) Int.MAX_VALUE else 2
  val parentPath = folder.path.substringBeforeLast("/", folder.path)

  BaseMediaCard(
    title = folder.name,
    modifier = modifier,
    thumbnail = thumbnail,
    thumbnailAspectRatio = thumbnailAspectRatio,
    thumbnailSize = thumbnailSize,
    titleTextAlign = if (isGridMode) TextAlign.Center else TextAlign.Start,
    thumbnailIcon = {
      Icon(
        customIcon ?: Icons.Filled.Folder,
        contentDescription = "Folder",
        modifier = if (isGridMode) {
          Modifier.fillMaxWidth().aspectRatio(thumbnailAspectRatio).scale(1.2f)
        } else {
          Modifier.size(thumbnailSize).scale(1.2f)
        },
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
      )
    },
    showThumbnailBackground = thumbnail != null,
    onClick = onClick,
    onLongClick = onLongClick,
    onThumbClick = onThumbClick,
    isSelected = isSelected,
    isRecentlyPlayed = isRecentlyPlayed,
    isWatched = isWatched,
    isGridMode = isGridMode,
    gridColumns = gridColumns,
    maxTitleLines = maxLines,
    overlayContent = {
      if (uiSettings.showUnplayedOldVideoLabel && newVideoCount > 0) {
        Surface(
          shape = pillShape,
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp),
          shadowElevation = 2.dp,
        ) {
          Text(
            text = newVideoCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }
      if (isGridMode && uiSettings.showTotalDurationChip && folder.totalDuration > 0) {
        Surface(
          shape = pillShape,
          color = Color.Black.copy(alpha = 0.72f),
          contentColor = Color.White,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp),
        ) {
          Text(
            text = MediaFormatter.formatDuration(folder.totalDuration),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }
    },
    infoContent = {
      if (!isGridMode && showFolderPath && parentPath.isNotEmpty()) {
        Text(
          parentPath,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    chipsContent = if (isGridMode) null else ({
      if (customChipContent != null) {
        customChipContent()
      }
      if (totalCount > 0) {
        MediaMetadataChip(text = countLabel)
      }
      if (uiSettings.showSizeChip && folder.totalSize > 0) {
        MediaMetadataChip(text = MediaFormatter.formatFileSize(folder.totalSize))
      }
      if (uiSettings.showTotalDurationChip && folder.totalDuration > 0) {
        MediaMetadataChip(text = MediaFormatter.formatDuration(folder.totalDuration))
      }
      if (uiSettings.showDateChip && folder.lastModified > 0) {
        MediaMetadataChip(text = MediaFormatter.formatDate(folder.lastModified * 1000))
      }
    })
  )
}
