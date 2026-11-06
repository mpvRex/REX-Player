package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState

@Composable
fun NetworkFolderCard(
  file: NetworkFile,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  shape: Shape? = null,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val maxLines = if (unlimitedNameLines) Int.MAX_VALUE else 2

  BaseMediaCard(
    title = file.name,
    modifier = modifier,
    thumbnailSize = 64.dp,
    thumbnailIcon = {
      Icon(
        Icons.Filled.Folder,
        contentDescription = "Folder",
        modifier = Modifier.size(64.dp).scale(1.2f),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
      )
    },
    onClick = onClick,
    onLongClick = onLongClick,
    isSelected = isSelected,
    maxTitleLines = maxLines,
    shape = shape,
  )
}
