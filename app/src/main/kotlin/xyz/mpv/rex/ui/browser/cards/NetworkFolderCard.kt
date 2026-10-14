package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.utils.media.MediaFormatter

@Composable
fun NetworkFolderCard(
  file: NetworkFile,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  viewMode: NetworkViewMode = NetworkViewMode.LIST,
  gridColumns: Int = 1,
  uiSettings: UiSettings? = null,
) {
  val configuration = LocalConfiguration.current
  val config = CardLayoutConfigProvider.configFor(
    viewMode = viewMode,
    screenWidthDp = configuration.screenWidthDp,
    unlimitedNameLines = uiSettings?.unlimitedNameLines ?: false,
  )

  val folderAspectRatio = if (viewMode == NetworkViewMode.GALLERY) 2.5f else 1f

  BaseMediaCard(
    title = file.name,
    modifier = modifier,
    isGridMode = config.isGridMode,
    gridColumns = if (viewMode == NetworkViewMode.GRID && gridColumns > 1) gridColumns else config.gridColumns,
    isDetailMode = config.isDetailMode,
    thumbnailSize = if (config.isDetailMode) 40.dp else 64.dp,
    thumbnailAspectRatio = folderAspectRatio,
    titleTextAlign = if (config.isGridMode && viewMode != NetworkViewMode.GALLERY) TextAlign.Center else TextAlign.Start,
    thumbnailIcon = {
      Icon(
        Icons.Filled.Folder,
        contentDescription = stringResource(R.string.network_badge_folder),
        modifier = if (config.isGridMode) {
          Modifier.fillMaxWidth().aspectRatio(folderAspectRatio).scale(1.2f)
        } else {
          Modifier.size(if (config.isDetailMode) 24.dp else 64.dp).scale(1.2f)
        },
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (file.isHidden) 0.3f else 0.5f),
      )
    },
    showThumbnailBackground = false,
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = config.maxTitleLines,
    infoContent = if (config.isDetailMode) {
      {
        Text(
          text = stringResource(R.string.network_badge_folder),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        if (file.lastModified > 0) {
          Text(
            text = " • ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = MediaFormatter.formatDate(file.lastModified),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
