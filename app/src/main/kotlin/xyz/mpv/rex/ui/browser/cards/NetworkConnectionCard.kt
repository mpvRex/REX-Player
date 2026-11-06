package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.ui.preferences.GroupedPreferenceCard

/**
 * Material 3 Expressive network connection card rendered as part of a
 * connected grouped stack on the Network tab.
 *
 * The trailing side of the hero row holds one big icon-over-label primary
 * action (Connect / Browse / Connecting) that replaces the status chip.
 * Edit, Delete, Auto-connect and Disconnect are grouped behind a long
 * press options sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkConnectionCard(
  connection: NetworkConnection,
  onConnect: (NetworkConnection) -> Unit,
  onDisconnect: (NetworkConnection) -> Unit,
  onEdit: (NetworkConnection) -> Unit,
  onDelete: (NetworkConnection) -> Unit,
  onBrowse: (NetworkConnection) -> Unit,
  onAutoConnectChange: (NetworkConnection, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isConnected: Boolean = false,
  isConnecting: Boolean = false,
  error: String? = null,
  position: GroupPosition = GroupPosition.ONLY,
  shape: Shape? = null,
) {
  val haptics = LocalHapticFeedback.current
  var showOptionsSheet by remember { mutableStateOf(false) }

  val cardContent: @Composable () -> Unit = {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = {},
          onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            showOptionsSheet = true
          },
        )
        .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      // Hero row: protocol avatar + name/subtitle + big primary action
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = if (isConnected) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
          },
          modifier = Modifier.size(42.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = connection.protocol.icon(),
              contentDescription = null,
              tint = if (isConnected) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(22.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = connection.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = stringResource(
              R.string.network_connection_protocol_host,
              connection.protocol.displayName,
              connection.host,
              connection.port,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        PrimaryAction(
          isConnecting = isConnecting,
          isConnected = isConnected,
          onConnect = { onConnect(connection) },
          onBrowse = { onBrowse(connection) },
        )
      }

      // Connection details (path, user) — aligned under the subtitle column
      if (connection.path != "/") {
        Text(
          text = stringResource(R.string.network_connection_path_prefix, connection.path),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 54.dp, top = 8.dp),
        )
      }

      if (connection.username.isNotEmpty() && !connection.isAnonymous) {
        Text(
          text = stringResource(R.string.network_connection_user_prefix, connection.username),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 54.dp, top = 4.dp),
        )
      }

      // Error message
      if (error != null) {
        Text(
          text = stringResource(R.string.network_connection_error_prefix, error),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }

  if (shape != null) {
    // Content-surface mode: a flat card painting the sheet's card colour, so on
    // a white sheet the rows read through their 3.dp gaps instead of as boxes.
    Surface(
      modifier = modifier
        .fillMaxWidth()
        .clip(shape),
      shape = shape,
      color = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color.Black
      } else {
        MaterialTheme.colorScheme.surface
      },
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      cardContent()
    }
  } else {
    GroupedPreferenceCard(
      position = position,
      modifier = modifier,
    ) {
      cardContent()
    }
  }

  // Long-press options sheet: Connect/Disconnect / Edit / Delete / Auto-connect
  if (showOptionsSheet) {
    ConnectionOptionsSheet(
      connection = connection,
      isConnected = isConnected,
      isConnecting = isConnecting,
      onConnect = { onConnect(connection) },
      onDisconnect = { onDisconnect(connection) },
      onEdit = {
        showOptionsSheet = false
        onEdit(connection)
      },
      onDelete = {
        showOptionsSheet = false
        onDelete(connection)
      },
      onAutoConnectChange = { checked ->
        onAutoConnectChange(connection, checked)
      },
      onDismiss = { showOptionsSheet = false },
    )
  }
}

/**
 * Big trailing icon-over-label action that takes the status chip's place:
 * Connect (offline) / Browse (connected) / Connecting (progress).
 */
@Composable
private fun PrimaryAction(
  isConnecting: Boolean,
  isConnected: Boolean,
  onConnect: () -> Unit,
  onBrowse: () -> Unit,
) {
  val shape = MaterialTheme.shapes.large

  if (isConnecting) {
    Surface(
      shape = shape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.network_connection_connecting),
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
    return
  }

  Surface(
    onClick = { if (isConnected) onBrowse() else onConnect() },
    shape = shape,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = if (isConnected) Icons.Filled.FolderOpen else Icons.Filled.Link,
        contentDescription = null,
        modifier = Modifier.size(26.dp),
      )
      Text(
        text = stringResource(
          if (isConnected) R.string.network_connection_browse else R.string.network_connection_connect
        ),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * Options sheet opened by long-pressing a connection card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionOptionsSheet(
  connection: NetworkConnection,
  isConnected: Boolean,
  isConnecting: Boolean,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onAutoConnectChange: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
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
      // Header
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
              imageVector = connection.protocol.icon(),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(22.dp),
            )
          }
        }
        Column {
          Text(
            text = connection.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = stringResource(
              R.string.network_connection_protocol_host,
              connection.protocol.displayName,
              connection.host,
              connection.port,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
      )

      // Connect / Disconnect contextual option
      when {
        isConnecting -> OptionRow(
          icon = Icons.Filled.Link,
          label = stringResource(R.string.network_connection_connecting),
          enabled = false,
          onClick = { },
        )
        isConnected -> OptionRow(
          icon = Icons.Filled.LinkOff,
          label = stringResource(R.string.network_connection_disconnect),
          onClick = {
            onDisconnect()
            onDismiss()
          },
        )
        else -> OptionRow(
          icon = Icons.Filled.Link,
          label = stringResource(R.string.network_connection_connect),
          onClick = {
            onConnect()
            onDismiss()
          },
        )
      }

      OptionRow(
        icon = Icons.Filled.Edit,
        label = stringResource(R.string.network_connection_edit),
        onClick = onEdit,
      )

      SwitchOptionRow(
        label = stringResource(R.string.network_connection_auto_connect),
        checked = connection.autoConnect,
        onCheckedChange = onAutoConnectChange,
      )

      OptionRow(
        icon = Icons.Filled.Delete,
        label = stringResource(R.string.network_connection_delete),
        tint = MaterialTheme.colorScheme.error,
        onClick = onDelete,
      )
    }
  }
}

@Composable
private fun OptionRow(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurface,
  enabled: Boolean = true,
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
        color = tint,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchOptionRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = { onCheckedChange(!checked) },
    shape = RoundedCornerShape(12.dp),
    color = Color.Transparent,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 2.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Bolt,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}

private fun NetworkProtocol.icon(): ImageVector = when (this) {
  NetworkProtocol.SMB -> Icons.Filled.Dns
  NetworkProtocol.FTP -> Icons.Filled.Storage
  NetworkProtocol.WEBDAV -> Icons.Filled.Language
}
