package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.utils.media.MediaFormatter
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun NetworkVideoCard(
  file: NetworkFile,
  connection: NetworkConnection,
  uiSettings: UiSettings,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  shape: Shape? = null,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  var thumbnail by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }

  if (uiSettings.showNetworkThumbnails) {
    val density = LocalDensity.current
    val targetThumbnailSize = 128.dp
    val thumbWidthPx = with(density) { targetThumbnailSize.toPx().roundToInt() }

    LaunchedEffect(file.path) {
      if (thumbnail == null) {
        thumbnail = thumbnailRepository.getThumbnailViaProxy(
          path = file.path,
          name = file.name,
          size = file.size,
          connection = connection,
          dimension = thumbWidthPx
        )
      }
    }
  }

  val maxLines = if (uiSettings.unlimitedNameLines) Int.MAX_VALUE else 2

  BaseMediaCard(
    title = file.name,
    modifier = modifier,
    thumbnailSize = 128.dp,
    thumbnail = thumbnail?.asImageBitmap(),
    thumbnailAspectRatio = 16f / 9f,
    thumbnailIcon = if (thumbnail == null) {
      {
        Icon(
          Icons.Filled.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    } else null,
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = maxLines,
    shape = shape,
    chipsContent = {
      if (uiSettings.showSizeChip && file.size > 0) {
        MediaMetadataChip(text = MediaFormatter.formatFileSize(file.size))
      }
      if (file.lastModified > 0) {
        MediaMetadataChip(text = MediaFormatter.formatDate(file.lastModified))
      }
    }
  )
}
