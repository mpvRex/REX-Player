package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import xyz.mpv.rex.R
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.selection.SelectionState
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object LibraryRootsPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val hybridMediaIndex = koinInject<HybridMediaIndexRepository>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val libraryScanRoots by preferences.libraryScanRoots.collectAsState()
    var selectionState by remember { mutableStateOf(SelectionState<String>()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val libraryRootsList = remember(libraryScanRoots) { libraryScanRoots.toList() }

    val addFolderLauncher = rememberLauncherForActivityResult(OpenDocumentTreeContract()) { uri ->
      uri ?: return@rememberLauncherForActivityResult
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }.onSuccess {
        val uriStr = uri.toString()
        preferences.libraryScanRoots.set(libraryScanRoots + uriStr)
        MediaLibraryEvents.notifyChanged()
        coroutineScope.launch(Dispatchers.IO) {
          runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
        }
      }
    }

    fun removeRoots(urisToRemove: Set<String>) {
      urisToRemove.forEach { uriStr ->
        runCatching {
          context.contentResolver.releasePersistableUriPermission(
            uriStr.toUri(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
      }
      val updated = libraryScanRoots.toMutableSet().apply { removeAll(urisToRemove) }
      preferences.libraryScanRoots.set(updated)
      MediaLibraryEvents.notifyChanged()
      coroutineScope.launch(Dispatchers.IO) {
        runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
      }
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.pref_library_roots_title),
          scrollBehavior = scrollBehavior,
          isInSelectionMode = selectionState.isInSelectionMode,
          selectedCount = selectionState.selectedCount,
          totalCount = libraryRootsList.size,
          onCancelSelection = { selectionState = selectionState.clear() },
          onBackClick = backstack::navigateBack,
          onDeleteClick = {
            removeRoots(selectionState.selectedIds)
            selectionState = selectionState.clear()
          },
          onSelectAll = {
            selectionState = selectionState.selectAll(libraryRootsList)
          },
          onInvertSelection = {
            selectionState = selectionState.invertSelection(libraryRootsList)
          },
          onDeselectAll = {
            selectionState = selectionState.clear()
          },
          additionalActions = {
            if (!selectionState.isInSelectionMode && libraryScanRoots.isNotEmpty()) {
              IconButton(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.padding(horizontal = 2.dp),
              ) {
                Icon(
                  Icons.Outlined.Restore,
                  contentDescription = stringResource(R.string.pref_clear_library_roots),
                  modifier = Modifier.size(28.dp),
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          },
          useRemoveIcon = true,
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp),
      ) {
        if (!selectionState.isInSelectionMode) {
          Text(
            text = stringResource(R.string.pref_library_roots_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))
        }

        if (libraryScanRoots.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          ) {
            EmptyState(
              icon = Icons.Filled.Folder,
              title = stringResource(R.string.pref_library_roots_empty_title),
              message = stringResource(R.string.pref_library_roots_empty_message),
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(libraryRootsList) { uriString ->
              LibraryRootItem(
                context = context,
                uriString = uriString,
                isSelected = selectionState.isSelected(uriString),
                isInSelectionMode = selectionState.isInSelectionMode,
                onRemove = { removeRoots(setOf(uriString)) },
                onLongClick = { selectionState = selectionState.toggle(uriString) },
                onClick = {
                  if (selectionState.isInSelectionMode) {
                    selectionState = selectionState.toggle(uriString)
                  }
                },
              )
            }
          }
        }

        if (!selectionState.isInSelectionMode) {
          Spacer(modifier = Modifier.height(16.dp))

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { addFolderLauncher.launch(null) },
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(modifier = Modifier.padding(8.dp))
              Text(
                text = stringResource(R.string.pref_add_library_root),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }

    if (showClearAllDialog) {
      AlertDialog(
        onDismissRequest = { showClearAllDialog = false },
        title = { Text(stringResource(R.string.pref_library_roots_clear_all_confirm_title)) },
        text = { Text(stringResource(R.string.pref_library_roots_clear_all_confirm_message)) },
        confirmButton = {
          TextButton(
            onClick = {
              removeRoots(libraryScanRoots)
              showClearAllDialog = false
            },
          ) {
            Text(stringResource(R.string.generic_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { showClearAllDialog = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRootItem(
  context: Context,
  uriString: String,
  isSelected: Boolean,
  isInSelectionMode: Boolean,
  onRemove: () -> Unit,
  onLongClick: () -> Unit,
  onClick: () -> Unit,
) {
  val (displayName, displayPath) = remember(uriString) {
    val uri = uriString.toUri()
    val docName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
    val name = docName ?: Uri.decode(uriString).substringAfterLast('/')
    val decoded = Uri.decode(uriString)
      .replace("content://com.android.externalstorage.documents/tree/", "")
      .replace("content://com.android.providers.downloads.documents/tree/", "Downloads/")
    name to decoded
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isInSelectionMode) {
          Checkbox(
            checked = isSelected,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 8.dp),
          )
        }
        Column {
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = displayPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (!isInSelectionMode) {
        IconButton(onClick = onRemove) {
          Icon(
            imageVector = Icons.Default.RemoveCircle,
            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}
