package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.FileTypeUtils
import java.io.File

@Composable
fun NetworkVideoCard(
  file: NetworkFile,
  connection: NetworkConnection,
  uiSettings: UiSettings,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  viewMode: NetworkViewMode = NetworkViewMode.LIST,
  gridColumns: Int = 1,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val configuration = LocalConfiguration.current

  val layoutConfig = CardLayoutConfigProvider.configFor(
    viewMode = viewMode,
    screenWidthDp = configuration.screenWidthDp,
    unlimitedNameLines = uiSettings.unlimitedNameLines,
  )
  val targetDimensionPx = layoutConfig.targetDimensionPx
  val isAudio = remember(file.name) { FileTypeUtils.isAudioFile(File(file.name)) }

  var thumbnail by remember(connection.id, file.path, targetDimensionPx) {
    mutableStateOf(
      if (uiSettings.showNetworkThumbnails && !isAudio) {
        thumbnailRepository.getThumbnailViaProxyFromMemory(connection.id, file.path, targetDimensionPx)
      } else null
    )
  }

  var isFailed by remember(connection.id, file.path) {
    mutableStateOf(thumbnailRepository.isNetworkThumbnailFailed(connection.id, file.path))
  }
  var isLoading by remember(connection.id, file.path, targetDimensionPx) {
    mutableStateOf(!isAudio && uiSettings.showNetworkThumbnails && thumbnail == null && !isFailed)
  }

  LaunchedEffect(connection.id, file.path) {
    thumbnailRepository.networkThumbnailFailuresCleared.collect { clearedConnId ->
      if (clearedConnId == null || clearedConnId == connection.id) {
        isFailed = thumbnailRepository.isNetworkThumbnailFailed(connection.id, file.path)
      }
    }
  }

  LaunchedEffect(connection.id, file.path, targetDimensionPx, uiSettings.showNetworkThumbnails, isAudio, isFailed) {
    if (isAudio) {
      isLoading = false
      return@LaunchedEffect
    }
    if (thumbnail == null && uiSettings.showNetworkThumbnails && !isFailed) {
      isLoading = true
      try {
        val loaded = thumbnailRepository.getThumbnailViaProxy(
          path = file.path,
          name = file.name,
          size = file.size,
          connection = connection,
          dimension = targetDimensionPx
        )
        thumbnail = loaded
        if (loaded == null) {
          isFailed = true
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        isFailed = true
      } finally {
        isLoading = false
      }
    } else {
      isLoading = false
    }
  }

  BaseMediaCard(
    title = file.name,
    modifier = modifier,
    isGridMode = layoutConfig.isGridMode,
    gridColumns = if (viewMode == NetworkViewMode.GRID && gridColumns > 1) gridColumns else layoutConfig.gridColumns,
    isDetailMode = layoutConfig.isDetailMode,
    thumbnailSize = layoutConfig.thumbnailSizeDp,
    thumbnail = thumbnail?.asImageBitmap(),
    thumbnailAspectRatio = layoutConfig.thumbnailAspectRatio,
    thumbnailIcon = if (thumbnail == null) {
      {
        if (isLoading) {
          ThumbnailShimmer(
            modifier = Modifier.fillMaxSize(),
            showIcon = !layoutConfig.isDetailMode
          )
        } else {
          Icon(
            if (isAudio) Icons.Filled.AudioFile else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(if (layoutConfig.isDetailMode) 24.dp else 48.dp),
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (isFailed) 0.35f else 1f),
          )
        }
      }
    } else null,
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = layoutConfig.maxTitleLines,
    chipsContent = if (layoutConfig.showChips) {
      {
        if (uiSettings.showSizeChip && file.size > 0) {
          MediaMetadataChip(text = MediaFormatter.formatFileSize(file.size))
        }
        if (file.lastModified > 0) {
          MediaMetadataChip(text = MediaFormatter.formatDate(file.lastModified))
        }
      }
    } else null,
    infoContent = if (layoutConfig.isDetailMode) {
      {
        val ext = file.name.substringAfterLast('.', "").uppercase()
        val dateStr = if (file.lastModified > 0) MediaFormatter.formatDate(file.lastModified) else "--"
        val sizeStr = if (file.size > 0) MediaFormatter.formatFileSize(file.size) else "--"

        Text(
          text = sizeStr,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = " • ",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = dateStr,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ext.isNotBlank()) {
          Text(
            text = " • ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = ext,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (file.isHidden) {
          Text(
            text = " • ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = stringResource(R.string.network_badge_hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    } else null,
  )
}
