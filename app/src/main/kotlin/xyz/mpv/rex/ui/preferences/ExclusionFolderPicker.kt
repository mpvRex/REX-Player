package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import xyz.mpv.rex.utils.storage.ExclusionSelectionState
import xyz.mpv.rex.utils.storage.exclusionSelectionState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.utils.storage.ExclusionFolder
import xyz.mpv.rex.utils.storage.exclusionFolderHierarchy
import xyz.mpv.rex.utils.storage.isExclusionStorageRoot
import xyz.mpv.rex.utils.storage.isPathExcluded
import xyz.mpv.rex.utils.storage.minimalExclusionSelection
import xyz.mpv.rex.utils.storage.visibleExclusionFolders

private val PathSetSaver = listSaver<Set<String>, String>(
  save = { it.toList() },
  restore = { it.toSet() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExclusionFolderPicker(
  folders: List<VideoFolder>,
  blacklistedFolders: Set<String>,
  isLoading: Boolean,
  onDismiss: () -> Unit,
  onAddFolders: (Set<String>) -> Unit,
  onBrowseSystem: () -> Unit,
) {
  var selected by rememberSaveable(stateSaver = PathSetSaver) { mutableStateOf(emptySet<String>()) }
  var expanded by rememberSaveable(stateSaver = PathSetSaver) { mutableStateOf(emptySet<String>()) }
  val nodes = remember(folders, blacklistedFolders) {
    exclusionFolderHierarchy(folders.map { it.path })
      .filterNot { isPathExcluded(it.path, blacklistedFolders) }
  }
  val children = remember(nodes) { nodes.groupBy { it.parent } }
  val paths = remember(nodes) { nodes.associateBy { it.path } }
  val storageRoots = remember(nodes) { nodes.filter { isExclusionStorageRoot(it.path) }.map { it.path }.toSet() }
  val visible = remember(nodes, expanded) { visibleExclusionFolders(nodes, expanded + storageRoots) }
  val effectiveSelection = remember(selected) { minimalExclusionSelection(selected) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
          Column {
            TopAppBar(
              title = { Text(stringResource(R.string.pref_exclusion_picker_title)) },
              navigationIcon = {
                IconButton(onClick = onDismiss) {
                  Icon(Icons.Default.Close, stringResource(R.string.close))
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            Text(
              stringResource(R.string.pref_exclusion_picker_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
          }
        },
        bottomBar = {
          Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
            Column(Modifier.navigationBarsPadding()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    if (effectiveSelection.isEmpty()) stringResource(R.string.pref_exclusion_picker_none_selected)
                    else pluralStringResource(R.plurals.pref_exclusion_selected_count, effectiveSelection.size, effectiveSelection.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                  )
                  if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptySet() }) {
                      Text(stringResource(R.string.file_picker_clear_selection))
                    }
                  }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                  onClick = { onAddFolders(effectiveSelection); onDismiss() },
                  enabled = effectiveSelection.isNotEmpty() && !isLoading,
                  modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                  Icon(Icons.Outlined.FolderOff, contentDescription = null, modifier = Modifier.size(20.dp))
                  Spacer(Modifier.size(10.dp))
                  Text(
                    if (effectiveSelection.isEmpty()) stringResource(R.string.pref_exclusion_picker_title)
                    else pluralStringResource(R.plurals.pref_exclusion_action_count, effectiveSelection.size, effectiveSelection.size),
                  )
                }
              }
            }
          }
        },
      ) { padding ->
        when {
          isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
          nodes.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.pref_folders_no_folders), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onDismiss(); onBrowseSystem() }) {
              Text(stringResource(R.string.pref_folders_browse_system))
            }
          }
          else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
          ) {
            itemsIndexed(visible, key = { _, folder -> folder.path }, contentType = { _, folder -> if (folder.path in storageRoots) "storage" else "folder" }) { index, folder ->
              if (folder.path in storageRoots) {
                StorageSection(folder)
              } else {
                var depth = 0
                var parent = folder.parent
                while (parent != null && parent in paths) {
                  if (parent !in storageRoots) depth++
                  parent = paths[parent]?.parent
                }
                val selectionState = exclusionSelectionState(folder.path, effectiveSelection)
                val selectedInside = effectiveSelection.count { it != folder.path && isPathExcluded(it, setOf(folder.path)) }
                val startsGroup = depth == 0 || (index > 0 && !isPathExcluded(folder.path, setOf(visible[index - 1].path)) && folder.parent != visible[index - 1].parent)
                val includedBy = effectiveSelection.firstOrNull { it != folder.path && isPathExcluded(folder.path, setOf(it)) }
                ExclusionFolderRow(
                  folder = folder,
                  depth = depth,
                  childCount = children[folder.path].orEmpty().size,
                  expanded = folder.path in expanded,
                  selectionState = selectionState,
                  selectedInside = selectedInside,
                  startsGroup = startsGroup,
                  includedBy = includedBy?.let { paths[it]?.name },
                  onExpand = {
                    expanded = if (folder.path in expanded) expanded - folder.path else expanded + folder.path
                  },
                  onToggle = {
                    selected = if (folder.path in selected) selected - folder.path else selected + folder.path
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StorageSection(folder: ExclusionFolder) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    Text(
      if (folder.path.startsWith("/storage/emulated/")) stringResource(R.string.pref_folders_internal_storage)
      else stringResource(R.string.pref_exclusion_storage_volume, folder.name),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun ExclusionFolderRow(
  folder: ExclusionFolder,
  depth: Int,
  childCount: Int,
  expanded: Boolean,
  selectionState: ExclusionSelectionState,
  selectedInside: Int,
  startsGroup: Boolean,
  includedBy: String?,
  onExpand: () -> Unit,
  onToggle: () -> Unit,
) {
  val inherited = selectionState == ExclusionSelectionState.INHERITED
  val selected = selectionState == ExclusionSelectionState.SELECTED
  val partial = selectionState == ExclusionSelectionState.PARTIAL
  val hasChildren = childCount > 0
  val actionLabel = when {
    hasChildren && expanded -> stringResource(R.string.cd_collapse)
    hasChildren -> stringResource(R.string.cd_expand)
    else -> null
  }
  val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
  val selectedInsideLabel = pluralStringResource(R.plurals.pref_exclusion_selected_inside, selectedInside, selectedInside)
  Box(
    modifier = Modifier.fillMaxWidth()
      .drawBehind {
        fun x(logical: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - logical else logical
        // Indent guides span the whole subtree, including the space between groups.
        // Keeping them in the gutter leaves room for names even several levels deep.
        for (level in 0 until depth) {
          val rail = (level * 8 + 4).dp.toPx()
          drawLine(guideColor, Offset(x(rail), 0f), Offset(x(rail), size.height), strokeWidth = 0.75.dp.toPx())
        }
      }
      .padding(top = if (startsGroup) 6.dp else 0.dp),
  ) {
    Row(
      modifier = Modifier.padding(start = (depth * 8).dp)
        .fillMaxWidth()
        .clickable(enabled = hasChildren || !inherited, onClickLabel = actionLabel) {
          if (hasChildren) onExpand() else onToggle()
        }
        .heightIn(min = 48.dp)
        .padding(start = 8.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        when {
          hasChildren && expanded -> Icons.Default.ExpandMore
          hasChildren -> Icons.Default.ChevronRight
          else -> Icons.Outlined.Folder
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.size(12.dp))
      Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
          maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (partial) {
          Text(
            selectedInsideLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
          )
        }
      }
      if (inherited) {
        val includedLabel = stringResource(R.string.pref_exclusion_included_by, includedBy.orEmpty())
        Text(
          stringResource(R.string.pref_exclusion_included),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = includedLabel },
        )
      } else if (partial) {
        Box(
          modifier = Modifier.size(48.dp)
            .triStateToggleable(state = ToggleableState.Indeterminate, role = Role.Checkbox, onClick = onToggle)
            .semantics {
              contentDescription = folder.name
              stateDescription = selectedInsideLabel
            },
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier.height(26.dp).widthIn(min = 26.dp)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
              .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(selectedInside.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
        }
      } else {
        Checkbox(
          checked = selected,
          onCheckedChange = { onToggle() },
          modifier = Modifier.semantics { contentDescription = folder.name },
        )
      }
    }
  }
}
