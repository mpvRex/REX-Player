package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.NetworkFileTypeHelper
import xyz.mpv.rex.utils.storage.NetworkItemCategory

/**
 * Displays non-media network files (subtitles, documents, archives, generic files)
 * across all four [NetworkViewMode] layouts.
 */
@Composable
fun NetworkFileCard(
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
  val category = NetworkFileTypeHelper.resolveCategory(file)
  val icon = NetworkFileTypeHelper.iconFor(category)
  val badgeText = NetworkFileTypeHelper.badgeTextFor(file, category)
  val config = CardLayoutConfigProvider.configFor(
    viewMode = viewMode,
    screenWidthDp = configuration.screenWidthDp,
    unlimitedNameLines = uiSettings?.unlimitedNameLines ?: false
  )

  val baseIconTint = when (category) {
    NetworkItemCategory.SUBTITLE -> MaterialTheme.colorScheme.tertiary
    NetworkItemCategory.ARCHIVE -> MaterialTheme.colorScheme.secondary
    NetworkItemCategory.DOCUMENT -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
  }
  val iconTint = if (file.isHidden) baseIconTint.copy(alpha = 0.4f) else baseIconTint

  BaseMediaCard(
    title = file.name,
    modifier = modifier,
    isGridMode = config.isGridMode,
    gridColumns = if (viewMode == NetworkViewMode.GRID && gridColumns > 1) gridColumns else config.gridColumns,
    isDetailMode = config.isDetailMode,
    thumbnailSize = config.thumbnailSizeDp,
    thumbnailAspectRatio = config.thumbnailAspectRatio,
    thumbnailIcon = {
      Icon(
        imageVector = icon,
        contentDescription = badgeText,
        modifier = Modifier.size(if (config.isDetailMode) 24.dp else 40.dp),
        tint = iconTint,
      )
    },
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = config.maxTitleLines,
    chipsContent = if (config.showChips) {
      {
        MediaMetadataChip(
          text = badgeText,
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = iconTint,
        )
        if (uiSettings?.showSizeChip != false && file.size > 0) {
          MediaMetadataChip(text = MediaFormatter.formatFileSize(file.size))
        }
        if (file.lastModified > 0) {
          MediaMetadataChip(text = MediaFormatter.formatDate(file.lastModified))
        }
      }
    } else null,
    infoContent = if (config.isDetailMode) {
      {
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = iconTint,
        )
        if (file.size > 0) {
          Text(
            text = " • ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = MediaFormatter.formatFileSize(file.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
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
