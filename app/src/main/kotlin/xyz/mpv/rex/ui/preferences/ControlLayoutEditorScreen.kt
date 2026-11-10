package xyz.mpv.rex.ui.preferences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.preferences.allPlayerButtons
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.deleteAndGet
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.ui.preferences.components.PlayerButtonChip
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Serializable
data class ControlLayoutEditorScreen(
  val region: ControlRegion,
) : Screen {
  override val transformSourceId: String
    get() = "settings:ControlLayoutEditorScreen:${region.name}"
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()

    val prefs = remember(region) {
      when (region) {
        ControlRegion.TOP_RIGHT -> listOf(preferences.topRightControls, preferences.topLeftControls, preferences.bottomRightControls, preferences.bottomLeftControls)
        ControlRegion.BOTTOM_RIGHT -> listOf(preferences.bottomRightControls, preferences.topLeftControls, preferences.topRightControls, preferences.bottomLeftControls)
        ControlRegion.BOTTOM_LEFT -> listOf(preferences.bottomLeftControls, preferences.topLeftControls, preferences.topRightControls, preferences.bottomRightControls)
        ControlRegion.PORTRAIT_BOTTOM -> listOf(preferences.portraitBottomControls)
        ControlRegion.MORE_SHEET -> listOf(preferences.moreSheetControls)
      }
    }

    val prefToEdit: Preference<String> = prefs[0]

    val usedInOtherRegions by remember(region) {
      mutableStateOf(
        if (region == ControlRegion.MORE_SHEET) {
          val landscapeSet = (preferences.topLeftControls.get().split(',') +
                  preferences.topRightControls.get().split(',') +
                  preferences.bottomLeftControls.get().split(',') +
                  preferences.bottomRightControls.get().split(','))
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
          val portraitSet = preferences.portraitBottomControls.get().split(',')
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
          landscapeSet.intersect(portraitSet)
        } else {
          val others = when (region) {
            ControlRegion.TOP_RIGHT -> listOf(preferences.topLeftControls, preferences.bottomRightControls, preferences.bottomLeftControls)
            ControlRegion.BOTTOM_RIGHT -> listOf(preferences.topLeftControls, preferences.topRightControls, preferences.bottomLeftControls)
            ControlRegion.BOTTOM_LEFT -> listOf(preferences.topLeftControls, preferences.topRightControls, preferences.bottomRightControls)
            ControlRegion.PORTRAIT_BOTTOM -> emptyList() // Portrait is independent
            else -> emptyList()
          }
          others.flatMap { it.get().split(',') }
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .toSet()
        }
      )
    }

    var selectedButtons by remember {
      mutableStateOf(
        if (region == ControlRegion.MORE_SHEET) {
          val landscapeSet = (preferences.topLeftControls.get().split(',') +
                  preferences.topRightControls.get().split(',') +
                  preferences.bottomLeftControls.get().split(',') +
                  preferences.bottomRightControls.get().split(','))
            .filter(String::isNotBlank)
            .toSet()
          val portraitSet = preferences.portraitBottomControls.get().split(',')
            .filter(String::isNotBlank)
            .toSet()
          val onBothScreens = landscapeSet.intersect(portraitSet)
          preferences.moreSheetControls.get().split(',')
            .filter(String::isNotBlank)
            .filter { it !in onBothScreens }
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .filter { it != PlayerButton.MORE_OPTIONS }
        } else {
          prefToEdit.get().split(',')
            .filter(String::isNotBlank)
            .mapNotNull { try { PlayerButton.valueOf(it) } catch (_: Exception) { null } }
            .filter { region != ControlRegion.PORTRAIT_BOTTOM || it != PlayerButton.MORE_OPTIONS }
        }
      )
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var isReset by remember { mutableStateOf(false) }

    DisposableEffect(selectedButtons) {
      onDispose {
        if (!isReset) {
          prefToEdit.set(selectedButtons.joinToString(","))
        }
      }
    }

    val title = when (region) {
      ControlRegion.TOP_RIGHT -> stringResource(R.string.control_layout_edit_title_top_right)
      ControlRegion.BOTTOM_RIGHT -> stringResource(R.string.control_layout_edit_title_bottom_right)
      ControlRegion.BOTTOM_LEFT -> stringResource(R.string.control_layout_edit_title_bottom_left)
      ControlRegion.PORTRAIT_BOTTOM -> stringResource(R.string.control_layout_edit_title_portrait_bottom)
      ControlRegion.MORE_SHEET -> stringResource(R.string.control_layout_edit_title_more_sheet)
    }

    if (showResetDialog) {
      ConfirmDialog(
        title = stringResource(R.string.control_layout_reset_dialog_title),
        subtitle = stringResource(R.string.control_layout_reset_dialog_message),
        onConfirm = {
          isReset = true
          prefToEdit.deleteAndGet()
          backstack.navigateBack()
        },
        onCancel = { showResetDialog = false },
      )
    }

    CollapsibleHeaderScreen(
      title = title,
      showBackButton = true,
      onBackClick = backstack::navigateBack,
      actions = {
        IconButton(onClick = { showResetDialog = true }) {
          Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.control_layout_reset_to_default))
        }
      },
    ) { contentModifier ->
      ProvidePreferenceLocals {
        val gridState = rememberLazyGridState()
        val availableButtons = remember(region) {
          allPlayerButtons.filter {
            it != PlayerButton.NONE &&
              ((region != ControlRegion.PORTRAIT_BOTTOM && region != ControlRegion.MORE_SHEET) || it != PlayerButton.MORE_OPTIONS)
          }
        }
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
          val fromKey = from.key as? PlayerButton
          val toKey = to.key as? PlayerButton
          val fromIndex = selectedButtons.indexOf(fromKey)
          val toIndex = selectedButtons.indexOf(toKey)
          if (fromIndex in selectedButtons.indices && toIndex in selectedButtons.indices && fromIndex != toIndex) {
            selectedButtons = selectedButtons.toMutableList().apply {
              add(toIndex, removeAt(fromIndex))
            }
          }
        }

        LazyVerticalGrid(
          state = gridState,
          columns = GridCells.Adaptive(minSize = 72.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = contentModifier.fillMaxSize()
        ) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
              text = stringResource(R.string.control_layout_reorder_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
          }

          if (selectedButtons.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Column(
                  modifier = Modifier.fillMaxSize(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                  )
                  Text(
                    text = stringResource(R.string.control_layout_drop_zone_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = stringResource(R.string.control_layout_drop_zone_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                  )
                }
              }
            }
          } else {
            items(
              count = selectedButtons.size,
              key = { selectedButtons[it] },
              span = { index ->
                val button = selectedButtons[index]
                if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                  GridItemSpan(maxLineSpan)
                } else {
                  GridItemSpan(1)
                }
              }
            ) { index ->
              val button = selectedButtons[index]
              ReorderableItem(reorderableState, key = button) { isDragging ->
                val elevation by animateFloatAsState(targetValue = if (isDragging) 8f else 0f, label = "drag_elevation")
                androidx.compose.material3.Surface(
                  modifier = Modifier.draggableHandle().then(
                    if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) Modifier.wrapContentWidth(Alignment.Start) else Modifier
                  ),
                  shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                  shadowElevation = elevation.dp,
                  color = Color.Transparent
                ) {
                  PlayerButtonChip(
                    button = button,
                    enabled = true,
                    onClick = { selectedButtons = selectedButtons - button },
                    badgeIcon = Icons.Default.RemoveCircle,
                    badgeColor = Color(0xFFEF5350),
                  )
                }
              }
            }
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(40.dp))
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.material3.Card(
              modifier = Modifier.fillMaxWidth(),
              shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
              colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
              FlowRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                val orphanedButtons = availableButtons.filter { it !in selectedButtons }
                orphanedButtons.forEach { button ->
                  val isEnabled = button !in usedInOtherRegions
                  PlayerButtonChip(
                    button = button,
                    enabled = isEnabled,
                    onClick = { selectedButtons = selectedButtons + button },
                    badgeIcon = Icons.Default.AddCircle,
                    badgeColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                  )
                }
                if (orphanedButtons.isEmpty()) {
                  Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.control_layout_all_buttons_in_use), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }

          item(span = { GridItemSpan(maxLineSpan) }) {
            IconsLegend()
            Spacer(Modifier.height(16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun IconsLegend() {
  androidx.compose.material3.Card(
    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(text = stringResource(R.string.control_layout_icons_legend_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        allPlayerButtons.forEach { button ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
            if (button == PlayerButton.AB_LOOP) {
              // "AB" متروك عمداً بدون ترجمة — رمز بصري وظيفي (اختصار A-B loop) مو نص لغوي
              Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Text(text = "AB", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              Icon(imageVector = button.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).then(if (button == PlayerButton.VERTICAL_FLIP) Modifier.rotate(90f) else Modifier))
            }
            Text(text = xyz.mpv.rex.preferences.getPlayerButtonLabel(button), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
          }
        }
      }
    }
  }
}
