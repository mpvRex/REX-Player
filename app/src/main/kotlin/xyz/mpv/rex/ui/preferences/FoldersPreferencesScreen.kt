package xyz.mpv.rex.ui.preferences

import android.app.Application
import android.widget.Toast
import xyz.mpv.rex.utils.storage.isExclusionStorageRoot
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.OpenInNew
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import xyz.mpv.rex.utils.storage.selectedFolderPath
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.selection.SelectionState
import xyz.mpv.rex.ui.browser.states.EmptyState
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object FoldersPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val hybridMediaIndex = koinInject<HybridMediaIndexRepository>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val blacklistedFolders by preferences.blacklistedFolders.collectAsState()
    var availableFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var selectionState by remember { mutableStateOf(SelectionState<String>()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        val path = selectedFolderPath(uri.toString())
        if (isExclusionStorageRoot(path)) {
          Toast.makeText(context, R.string.pref_exclusion_choose_subfolder, Toast.LENGTH_SHORT).show()
          return@rememberLauncherForActivityResult
        }
        preferences.blacklistedFolders.set(
          preferences.blacklistedFolders.get() + path,
        )
        MediaLibraryEvents.notifyChanged()
      }
    }

    val blacklistedFoldersList = remember(blacklistedFolders) { blacklistedFolders.toList() }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.pref_exclusion_screen_title),
          isInSelectionMode = selectionState.isInSelectionMode,
          selectedCount = selectionState.selectedCount,
          totalCount = blacklistedFoldersList.size,
          onCancelSelection = { selectionState = selectionState.clear() },
          onBackClick = backstack::navigateBack,
          onDeleteClick = {
            val updated = blacklistedFolders.toMutableSet().apply {
              removeAll(selectionState.selectedIds)
            }
            preferences.blacklistedFolders.set(updated)
            selectionState = selectionState.clear()
            MediaLibraryEvents.notifyChanged()
            coroutineScope.launch(Dispatchers.IO) {
              hybridMediaIndex.ensureFresh(force = true, userInitiated = true)
            }
          },
          onSelectAll = {
            selectionState = selectionState.selectAll(blacklistedFoldersList)
          },
          onInvertSelection = {
            selectionState = selectionState.invertSelection(blacklistedFoldersList)
          },
          onDeselectAll = {
            selectionState = selectionState.clear()
          },
          additionalActions = {
            if (!selectionState.isInSelectionMode && blacklistedFolders.isNotEmpty()) {
              IconButton(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.padding(horizontal = 2.dp),
              ) {
                Icon(
                  Icons.Outlined.Restore,
                  contentDescription = stringResource(R.string.pref_folders_clear_all),
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
            text = stringResource(R.string.pref_exclusion_screen_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))
        }

        if (blacklistedFolders.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          ) {
            EmptyState(
              icon = Icons.Filled.FolderOff,
              title = stringResource(R.string.pref_exclusion_empty_title),
              message = stringResource(R.string.pref_exclusion_empty_summary),
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(blacklistedFoldersList) { folderPath ->
              BlacklistedFolderItem(
                folderPath = folderPath,
                isSelected = selectionState.isSelected(folderPath),
                isInSelectionMode = selectionState.isInSelectionMode,
                onRemove = {
                  val updated = blacklistedFolders.toMutableSet().apply { remove(folderPath) }
                  preferences.blacklistedFolders.set(updated)
                  MediaLibraryEvents.notifyChanged()
                  coroutineScope.launch(Dispatchers.IO) {
                    hybridMediaIndex.ensureFresh(force = true, userInitiated = true)
                  }
                },
                onLongClick = {
                  selectionState = selectionState.toggle(folderPath)
                },
                onClick = {
                  if (selectionState.isInSelectionMode) {
                    selectionState = selectionState.toggle(folderPath)
                  }
                },
              )
            }
          }
        }

        if (!selectionState.isInSelectionMode) {
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = {
              showAddDialog = true
              isLoading = true
              coroutineScope.launch {
                try {
                  availableFolders = withContext(Dispatchers.IO) {
                    scanAllVideoFolders(context.applicationContext as Application)
                  }
                } finally {
                  isLoading = false
                }
              }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          ) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.pref_exclusion_choose_folders))
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.pref_folders_browse_system))
          }
        }
      }
    }

    if (showAddDialog) {
      ExclusionFolderPicker(
        folders = availableFolders,
        blacklistedFolders = blacklistedFolders,
        isLoading = isLoading,
        onDismiss = { showAddDialog = false },
        onBrowseSystem = { folderPicker.launch(null) },
        onAddFolders = { folderPaths ->
          val updated = blacklistedFolders.toMutableSet().apply { addAll(folderPaths) }
          preferences.blacklistedFolders.set(updated)
          MediaLibraryEvents.notifyChanged()
          coroutineScope.launch(Dispatchers.IO) {
            hybridMediaIndex.purgeExcludedFolders(folderPaths)
            hybridMediaIndex.ensureFresh(force = true, userInitiated = true)
          }
        },
      )
    }

    if (showClearAllDialog) {
      AlertDialog(
        onDismissRequest = { showClearAllDialog = false },
        title = { Text(stringResource(R.string.pref_folders_clear_all_confirm_title)) },
        text = { Text(stringResource(R.string.pref_folders_clear_all_confirm_message)) },
        confirmButton = {
          TextButton(
            onClick = {
              preferences.blacklistedFolders.set(emptySet())
              showClearAllDialog = false
              MediaLibraryEvents.notifyChanged()
              coroutineScope.launch(Dispatchers.IO) {
                hybridMediaIndex.ensureFresh(force = true, userInitiated = true)
              }
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
private fun BlacklistedFolderItem(
  folderPath: String,
  isSelected: Boolean,
  isInSelectionMode: Boolean,
  onRemove: () -> Unit,
  onLongClick: () -> Unit,
  onClick: () -> Unit,
) {
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
            text = folderPath.substringAfterLast('/'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = folderPath,
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

/**
 * Scans all storage volumes for folders containing videos
 * Uses optimized fast scanning for better performance
 */
private suspend fun scanAllVideoFolders(context: Application): List<VideoFolder> {
  // Use fast optimized scanning - 5-10x faster for large libraries
  return xyz.mpv.rex.repository.MediaFileRepository.getAllVideoFoldersFast(context)
}

