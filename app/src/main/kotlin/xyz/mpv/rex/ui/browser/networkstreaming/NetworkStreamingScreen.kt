package xyz.mpv.rex.ui.browser.networkstreaming

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.GroupedListItem
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.cards.NetworkConnectionCard
import xyz.mpv.rex.ui.browser.dialogs.AddConnectionSheet
import xyz.mpv.rex.ui.browser.dialogs.EditConnectionSheet
import xyz.mpv.rex.ui.preferences.GroupedPreferenceCard
import xyz.mpv.rex.ui.preferences.PreferenceSectionHeader
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.FolderViewMode
import android.widget.Toast

@Serializable
object NetworkStreamingScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val viewModel: NetworkStreamingViewModel =
      viewModel(factory = NetworkStreamingViewModel.factory(context.applicationContext as android.app.Application))

    val connections by viewModel.connections.collectAsState()
    val connectionStatuses by viewModel.connectionStatuses.collectAsState()
    val browserPreferences = koinInject<xyz.mpv.rex.preferences.BrowserPreferences>()
    val playedLinksSerialized by browserPreferences.playedNetworkLinks.collectAsState()
    val playedLinks by remember(playedLinksSerialized) {
      derivedStateOf {
        if (playedLinksSerialized.isBlank()) {
          emptyList()
        } else {
          playedLinksSerialized.split("\n").filter { it.isNotBlank() }
        }
      }
    }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<NetworkConnection?>(null) }
    val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

    // LazyList state for scroll tracking
    val listState = remember { LazyListState() }

    // Track scroll direction to show/hide FAB
    var previousFirstVisibleItemIndex by remember { mutableIntStateOf(0) }
    var previousFirstVisibleItemScrollOffset by remember { mutableIntStateOf(0) }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val isFabVisible by remember {
      derivedStateOf {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset

        // Show FAB when at the top
        if (currentIndex == 0 && currentOffset == 0) {
          true
        } else {
          // Show when scrolling up, hide when scrolling down
          val isScrollingUp = currentIndex < previousFirstVisibleItemIndex ||
            (currentIndex == previousFirstVisibleItemIndex && currentOffset < previousFirstVisibleItemScrollOffset)

          previousFirstVisibleItemIndex = currentIndex
          previousFirstVisibleItemScrollOffset = currentOffset

          isScrollingUp
        }
      }
    }

    val canNavigateBack = remember { backstack.size > 1 }

    androidx.activity.compose.BackHandler(enabled = canNavigateBack) {
      backstack.navigateBack()
    }

    Scaffold(
        topBar = {
          BrowserTopBar(
            title = stringResource(R.string.network),
            isInSelectionMode = false,
            selectedCount = 0,
            totalCount = 0,
            onBackClick = if (canNavigateBack) { { backstack.navigateBack() } } else null,
            onCancelSelection = { },
          onSortClick = null,
          // Search functionality disabled for production
          onSearchClick = null,
          onSettingsClick = {
            backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
          onDeleteClick = null,
          onRenameClick = null,
          isSingleSelection = false,
          onInfoClick = null,
          onPlayClick = null,
          onSelectAll = null,
          onInvertSelection = null,
          onDeselectAll = null,
        )
      },
      floatingActionButton = {
        val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        if (isFabVisible) {
          ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_connection)) },
            modifier = Modifier.padding(bottom = navigationBarHeight)
          )
        }
      },
    ) { padding ->
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(
          start = 0.dp,
          end = 0.dp,
          top = 4.dp,
          bottom = navigationBarHeight + 12.dp
        ),
      ) {
          // Section 1: Stream Link
          item(key = "stream_link") {
            StreamLinkSection(
              onPlayLink = { url ->
                val currentList = playedLinks.toMutableList()
                currentList.remove(url)
                currentList.add(0, url)
                val cappedList = if (currentList.size > 20) currentList.take(20) else currentList
                browserPreferences.playedNetworkLinks.set(cappedList.joinToString("\n"))

                MediaUtils.playFile(url, context, "network_stream")
              },
            )
          }

          // Section 1b: Played Links History Section
          if (playedLinks.isNotEmpty()) {
            item(key = "recent_links_header") {
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                PreferenceSectionHeader(
                  title = stringResource(R.string.network_recent_links),
                  modifier = Modifier.weight(1f),
                )
                TextButton(
                  onClick = { browserPreferences.playedNetworkLinks.set("") },
                  modifier = Modifier.padding(end = 12.dp),
                ) {
                  Text(stringResource(R.string.network_clear_all_links))
                }
              }
            }

            item(key = "recent_links_list") {
              GroupedListColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
              ) {
                val clipboardManager =
                  context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

                playedLinks.forEachIndexed { index, link ->
                  GroupedListItem(
                    position = when {
                      playedLinks.size == 1 -> GroupPosition.ONLY
                      index == 0 -> GroupPosition.FIRST
                      index == playedLinks.size - 1 -> GroupPosition.LAST
                      else -> GroupPosition.MIDDLE
                    },
                  ) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                          onClick = {
                            // Play link and move to top
                            val currentList = playedLinks.toMutableList()
                            currentList.remove(link)
                            currentList.add(0, link)
                            browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
                            MediaUtils.playFile(link, context, "network_stream")
                          },
                          onLongClick = {
                            // Copy link to clipboard
                            clipboardManager?.setPrimaryClip(
                              android.content.ClipData.newPlainText("link", link)
                            )
                            Toast.makeText(
                              context,
                              context.getString(R.string.copied_to_clipboard),
                              Toast.LENGTH_SHORT,
                            ).show()
                          },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(36.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                          )
                        }
                      }
                      Column(
                        modifier = Modifier
                          .weight(1f)
                          .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                      ) {
                        Text(
                          text = android.net.Uri.parse(link).lastPathSegment
                            ?.takeIf { it.isNotBlank() } ?: link,
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Medium,
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = link,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      IconButton(
                        onClick = {
                          val currentList = playedLinks.toMutableList()
                          currentList.remove(link)
                          browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
                        },
                        modifier = Modifier.size(28.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Close,
                          contentDescription = stringResource(R.string.network_remove_link_action),
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Section 2: Local Network header
          item(key = "local_network_header") {
            PreferenceSectionHeader(
              title = stringResource(R.string.network_local_network),
              modifier = Modifier.padding(top = 16.dp),
            )
          }

          // Show empty state or connection list
          if (connections.isEmpty()) {
            item(key = "connections_empty") {
              GroupedPreferenceCard(position = GroupPosition.ONLY) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        imageVector = Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Column {
                    Text(
                      text = stringResource(R.string.network_empty_title),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = stringResource(R.string.network_empty_description),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                }
              }
            }
          } else {
            itemsIndexed(connections, key = { _, item -> item.id }) { index, connection ->
              val status = connectionStatuses[connection.id]
              NetworkConnectionCard(
                connection = connection,
                onConnect = { conn ->
                  viewModel.connect(conn)
                },
                onDisconnect = { conn -> viewModel.disconnect(conn) },
                onEdit = { conn -> editingConnection = conn },
                onDelete = { conn -> viewModel.deleteConnection(conn) },
                onBrowse = { conn ->
                  // Navigate to browser screen if connected
                  if (status?.isConnected == true) {
                    backstack.add(
                      NetworkBrowserScreen(
                        connectionId = conn.id,
                        connectionName = conn.name,
                        currentPath = "/",  // Always start at root - conn.path is already included in connection
                      ),
                    )
                  }
                },
                onAutoConnectChange = { conn, autoConnect ->
                  viewModel.updateConnection(conn.copy(autoConnect = autoConnect))
                },
                isConnected = status?.isConnected ?: false,
                isConnecting = status?.isConnecting ?: false,
                error = status?.error,
                position = when {
                  connections.size == 1 -> GroupPosition.ONLY
                  index == 0 -> GroupPosition.FIRST
                  index == connections.size - 1 -> GroupPosition.LAST
                  else -> GroupPosition.MIDDLE
                },
                modifier = Modifier.padding(bottom = 3.dp),
              )
            }
          }
        }

      // Add Connection Sheet
      AddConnectionSheet(
        isOpen = showAddSheet,
        onDismiss = { showAddSheet = false },
        onSave = { connection ->
          viewModel.addConnection(connection)
          showAddSheet = false
        },
      )

      // Edit Connection Sheet
      editingConnection?.let { connection ->
        EditConnectionSheet(
          connection = connection,
          isOpen = true,
          onDismiss = { editingConnection = null },
          onSave = { updatedConnection ->
            viewModel.updateConnection(updatedConnection)
            editingConnection = null
          },
        )
      }
    }
  }
}

@Composable
private fun StreamLinkSection(
  onPlayLink: (String) -> Unit,
) {
  val context = LocalContext.current
  var linkUrl by rememberSaveable { mutableStateOf("") }

  fun pasteFromClipboard(): String? {
    val clipboardManager =
      context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    val clipData = clipboardManager?.primaryClip
    if (clipData != null && clipData.itemCount > 0) {
      val text = clipData.getItemAt(0).text?.toString() ?: ""
      if (text.isNotBlank()) {
        return text
      }
    }
    return null
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    PreferenceSectionHeader(
      title = stringResource(R.string.stream_link),
      modifier = Modifier.padding(top = 4.dp),
    )

    // Single M3E row: text input + one combined Paste/Play action
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = linkUrl,
        onValueChange = { linkUrl = it },
        placeholder = {
          Text(
            text = stringResource(R.string.network_stream_link_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        modifier = Modifier.weight(1f),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
      )

      // One button, two jobs: paste when empty, play when filled
      FilledTonalButton(
        onClick = {
          if (linkUrl.isBlank()) {
            pasteFromClipboard()?.let { linkUrl = it }
          } else {
            onPlayLink(linkUrl)
            linkUrl = ""
          }
        },
        modifier = Modifier.height(56.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      ) {
        Icon(
          imageVector = if (linkUrl.isBlank()) Icons.Filled.ContentPaste else Icons.Filled.PlayArrow,
          contentDescription = stringResource(
            if (linkUrl.isBlank()) R.string.paste else R.string.play
          ),
        )
      }
    }
  }
}
