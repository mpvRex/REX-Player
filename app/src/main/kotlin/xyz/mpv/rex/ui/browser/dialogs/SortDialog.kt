package xyz.mpv.rex.ui.browser.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Surface
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.VideoSortType
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.preferences.FolderViewMode
import xyz.mpv.rex.preferences.MediaLayoutMode
import xyz.mpv.rex.preferences.preference.collectAsState
import org.koin.compose.koinInject
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import xyz.mpv.rex.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun SortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  title: String,
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  modifier: Modifier = Modifier,
  typeLabels: List<String> = types,
  getLabelForType: @Composable (String) -> Pair<String, String> = { type ->
    when (type) {
      FolderSortType.Title.displayName -> Pair(stringResource(R.string.sort_order_az), stringResource(R.string.sort_order_za))
      FolderSortType.Duration.displayName -> Pair(stringResource(R.string.sort_order_shortest), stringResource(R.string.sort_order_longest))
      FolderSortType.Date.displayName -> Pair(stringResource(R.string.sort_order_oldest), stringResource(R.string.sort_order_newest))
      FolderSortType.Size.displayName -> Pair(stringResource(R.string.sort_order_smallest), stringResource(R.string.sort_order_largest))
      else -> Pair(stringResource(R.string.sort_order_asc_abbrev), stringResource(R.string.sort_order_desc_abbrev))
    }
  },
  visibilityToggles: List<VisibilityToggle> = emptyList(),
  viewModeSelector: MultiViewModeSelector? = null,
  layoutModeSelector: ViewModeSelector? = null,
  folderGridColumnSelector: GridColumnSelector? = null,
  videoGridColumnSelector: GridColumnSelector? = null,
  showSortOptions: Boolean = true,
  enableViewModeOptions: Boolean = true,
  enableLayoutModeOptions: Boolean = true,
  contentToggles: List<ContentToggle> = emptyList(),
) {
  // Backed by a persisted preference so the expand/collapse state is shared by all
  // dialog instances (Folder / Tree / Library views each compose their own dialog)
  // and survives view-mode switches and app restarts.
  val browserPreferences = koinInject<BrowserPreferences>()
  val filtersExpanded by browserPreferences.sortDialogFiltersExpanded.collectAsState()
  val onFiltersExpandedChange: (Boolean) -> Unit = {
    browserPreferences.sortDialogFiltersExpanded.set(it)
  }

  if (!isOpen) return

  val configuration = LocalConfiguration.current
  val contentPadding = if (configuration.screenWidthDp < 380) 16.dp else 24.dp
  val (ascLabel, descLabel) = getLabelForType(sortType)

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier =
      modifier
        .padding(horizontal = 16.dp)
        .widthIn(max = 480.dp)
        .fillMaxWidth(),
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      tonalElevation = 6.dp,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(contentPadding),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(bottom = 16.dp),
        )

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (showSortOptions) {
            SortTypeSelector(
              sortType = sortType,
              onSortTypeChange = onSortTypeChange,
              types = types,
              icons = icons,
              sortOrderAsc = sortOrderAsc,
              onSortOrderChange = onSortOrderChange,
              ascLabel = ascLabel,
              descLabel = descLabel,
              modifier = Modifier.fillMaxWidth(),
              typeLabels = typeLabels,
            )
          }

          if (viewModeSelector != null || layoutModeSelector != null) {
            SectionCard {
              Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
              ) {
                if (viewModeSelector != null) {
                  MultiViewModeSelectorComponent(
                    selector = viewModeSelector,
                    enabled = enableViewModeOptions,
                    modifier = Modifier.weight(3f),
                  )
                }

                if (viewModeSelector != null && layoutModeSelector != null) {
                  VerticalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                  )
                }

                if (layoutModeSelector != null) {
                  ViewModeSelectorComponent(
                    viewModeSelector = layoutModeSelector,
                    enabled = enableLayoutModeOptions,
                    modifier = Modifier.weight(2f),
                  )
                }
              }
            }
          }

          GridColumnsSection(
            folderGridColumnSelector = folderGridColumnSelector,
            videoGridColumnSelector = videoGridColumnSelector,
          )

          if (contentToggles.isNotEmpty()) {
            ContentTogglesSection(
              toggles = contentToggles,
              expanded = filtersExpanded,
              onExpandedChange = onFiltersExpandedChange,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          if (visibilityToggles.isNotEmpty()) {
            VisibilityTogglesSection(
              toggles = visibilityToggles,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

data class ContentToggle(
  val label: String,
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)

data class VisibilityToggle(
  val label: String,
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)

data class ViewModeSelector(
  val label: String,
  val firstOptionLabel: String,
  val secondOptionLabel: String,
  val firstOptionIcon: ImageVector,
  val secondOptionIcon: ImageVector,
  val isFirstOptionSelected: Boolean,
  val onViewModeChange: (Boolean) -> Unit,
)

data class ViewModeOption(
  val label: String,
  val icon: ImageVector,
  val isSelected: Boolean,
  val onClick: () -> Unit,
)

data class MultiViewModeSelector(
  val label: String,
  val options: List<ViewModeOption>,
)

data class GridColumnSelector(
  val label: String = "",
  val currentValue: Int,
  val onValueChange: (Int) -> Unit,
  val valueRange: ClosedFloatingPointRange<Float> = 1f..4f,
  val steps: Int = 2,
)

// -----------------------------------------------------------------------------
// Consolidated internal composable for sort UI (Material You styling)
// -----------------------------------------------------------------------------

@Composable
private fun SectionCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content
  )
}

@Composable
private fun SegmentedOptionGroup(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun RowScope.SegmentedOptionItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  iconSize: Dp = 20.dp,
) {
  val shape = RoundedCornerShape(12.dp)
  val containerColor = if (selected && enabled) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
  } else if (selected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
  } else {
    Color.Transparent
  }
  val contentColor = if (selected && enabled) {
    MaterialTheme.colorScheme.primary
  } else if (selected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
  } else if (enabled) {
    MaterialTheme.colorScheme.onSurfaceVariant
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
  }

  Column(
    modifier = modifier
      .weight(1f)
      .clip(shape)
      .background(containerColor)
      .clickable(
        enabled = enabled,
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true),
      )
      .padding(vertical = 8.dp, horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = contentColor,
      modifier = Modifier.size(iconSize),
    )
    Text(
      text = label,
      fontSize = 11.sp,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected && enabled) FontWeight.Bold else FontWeight.Medium,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SortTypeSelector(
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  ascLabel: String,
  descLabel: String,
  modifier: Modifier = Modifier,
  typeLabels: List<String> = types,
) {
  SectionCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.sort_by),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      // Compact order toggle
      val activeLabel = if (sortOrderAsc) ascLabel else descLabel
      val icon = if (sortOrderAsc) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
      
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
          .clickable { onSortOrderChange(!sortOrderAsc) }
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = activeLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          fontWeight = FontWeight.Medium
        )
        Icon(
          imageVector = icon,
          contentDescription = stringResource(R.string.cd_toggle_sort_order),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    SegmentedOptionGroup(modifier = Modifier.fillMaxWidth()) {
      types.forEachIndexed { index, type ->
        val selected = sortType == type
        val label = typeLabels.getOrNull(index) ?: type
        SegmentedOptionItem(
          selected = selected,
          onClick = { onSortTypeChange(type) },
          icon = icons[index],
          label = label,
        )
      }
    }
  }
}

@Composable
private fun MultiViewModeSelectorComponent(
  selector: MultiViewModeSelector,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = selector.label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      },
    )

    SegmentedOptionGroup(modifier = Modifier.fillMaxWidth()) {
      selector.options.forEach { option ->
        SegmentedOptionItem(
          selected = option.isSelected,
          onClick = option.onClick,
          icon = option.icon,
          label = option.label,
          enabled = enabled,
          iconSize = 22.dp,
        )
      }
    }
  }
}

@Composable
private fun ViewModeSelectorComponent(
  viewModeSelector: ViewModeSelector,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val options = listOf(viewModeSelector.firstOptionLabel, viewModeSelector.secondOptionLabel)
  val icons = listOf(viewModeSelector.firstOptionIcon, viewModeSelector.secondOptionIcon)
  val selectedIndex = if (viewModeSelector.isFirstOptionSelected) 0 else 1

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = viewModeSelector.label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      },
    )

    SegmentedOptionGroup(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, label ->
        SegmentedOptionItem(
          selected = index == selectedIndex,
          onClick = { viewModeSelector.onViewModeChange(index == 0) },
          icon = icons[index],
          label = label,
          enabled = enabled,
        )
      }
    }
  }
}

@Composable
private fun ContentTogglesSection(
  toggles: List<ContentToggle>,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
  ) {
    // Header row with Filters text and dropdown button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(bounded = true),
          onClick = { onExpandedChange(!expanded) }
        )
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.filters),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Icon(
        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ArrowDropDown,
        contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (expanded) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        toggles.forEach { toggle ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = { toggle.onCheckedChange(!toggle.checked) },
              )
              .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = toggle.label,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
              checked = toggle.checked,
              onCheckedChange = toggle.onCheckedChange,
              modifier = Modifier.scale(0.8f),
              thumbContent = {
                Crossfade(
                  targetState = toggle.checked,
                  animationSpec = tween(durationMillis = 200),
                  label = "SwitchIconAnimation"
                ) { isChecked ->
                  if (isChecked) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = null,
                      modifier = Modifier.size(SwitchDefaults.IconSize),
                      tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = null,
                      modifier = Modifier.size(SwitchDefaults.IconSize),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilityTogglesSection(
  toggles: List<VisibilityToggle>,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
  ) {
    // Header row with Fields text and dropdown button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(bounded = true),
          onClick = { expanded = !expanded }
        )
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.fields),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Icon(
        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ArrowDropDown,
        contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Expandable filter chips section
    if (expanded) {
      FlowRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        toggles.forEach { toggle ->
          FilterChip(
            selected = toggle.checked,
            onClick = { toggle.onCheckedChange(!toggle.checked) },
            label = {
              Text(
                text = toggle.label,
                style = MaterialTheme.typography.labelMedium,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
              selectedLabelColor = MaterialTheme.colorScheme.primary,
              containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = null,
            leadingIcon = null,
          )
        }
      }
    }
  }
}

@Composable
private fun GridColumnsSection(
  folderGridColumnSelector: GridColumnSelector?,
  videoGridColumnSelector: GridColumnSelector?,
  modifier: Modifier = Modifier,
) {
  if (folderGridColumnSelector == null && videoGridColumnSelector == null) return

  SectionCard(modifier = modifier) {
    Text(
      text = stringResource(R.string.grid_columns),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (folderGridColumnSelector != null) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(R.string.folder_grid),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = folderGridColumnSelector.currentValue.toFloat(),
            onValueChange = { folderGridColumnSelector.onValueChange(it.toInt()) },
            valueRange = folderGridColumnSelector.valueRange,
            steps = folderGridColumnSelector.steps,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = pluralStringResource(R.plurals.num_columns, folderGridColumnSelector.currentValue, folderGridColumnSelector.currentValue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }

      if (videoGridColumnSelector != null) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(R.string.video_grid),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = videoGridColumnSelector.currentValue.toFloat(),
            onValueChange = { videoGridColumnSelector.onValueChange(it.toInt()) },
            valueRange = videoGridColumnSelector.valueRange,
            steps = videoGridColumnSelector.steps,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = pluralStringResource(R.plurals.num_columns, videoGridColumnSelector.currentValue, videoGridColumnSelector.currentValue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }
    }
  }
}

@Composable
fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val backstack = LocalBackStack.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
  val includeNoMediaContent by browserPreferences.includeNoMediaContent.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showFileExtension by browserPreferences.showFileExtension.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = 1,
    )
  } else null

  val isAlbumView = folderViewMode == FolderViewMode.AlbumView

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = if (isAlbumView) stringResource(R.string.sort_and_view_options) else stringResource(R.string.view_options),
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries
        .find { it.displayName == typeName }
        ?.let { newType ->
          if (newType == sortType) {
            val newOrder = if (sortOrder.isAscending) SortOrder.Descending else SortOrder.Ascending
            browserPreferences.saveFolderSortOrder(newType, newOrder)
            onSortOrderChange(newOrder)
          } else {
            browserPreferences.setFolderSortType(newType)
            onSortTypeChange(newType)
            onSortOrderChange(browserPreferences.getFolderSortOrder(newType))
          }
        }
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      val newOrder = if (isAsc) SortOrder.Ascending else SortOrder.Descending
      browserPreferences.saveFolderSortOrder(sortType, newOrder)
      onSortOrderChange(newOrder)
    },
    types = listOf(FolderSortType.Title.displayName, FolderSortType.Duration.displayName, FolderSortType.Date.displayName, FolderSortType.Size.displayName),
    typeLabels = listOf(stringResource(FolderSortType.Title.stringRes), stringResource(FolderSortType.Duration.stringRes), stringResource(FolderSortType.Date.stringRes), stringResource(FolderSortType.Size.stringRes)),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.AccessTime,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    showSortOptions = isAlbumView,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.AlbumView)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.FileManager)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        )
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout),
      firstOptionLabel = stringResource(R.string.layout_mode_list),
      secondOptionLabel = stringResource(R.string.layout_mode_grid),
      firstOptionIcon = Icons.AutoMirrored.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    contentToggles = listOf(
      ContentToggle(
        label = stringResource(R.string.filter_audio_files),
        checked = showAudioFiles,
        onCheckedChange = { browserPreferences.showAudioFiles.set(it) },
      ),
      ContentToggle(
        label = stringResource(R.string.filter_show_nomedia_folders),
        checked = includeNoMediaContent,
        onCheckedChange = { browserPreferences.includeNoMediaContent.set(it) },
      ),
    ),
    visibilityToggles = listOf(
      VisibilityToggle(
        label = stringResource(R.string.field_video_thumbnails),
        checked = showVideoThumbnails,
        onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_full_name),
        checked = unlimitedNameLines,
        onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_file_extension),
        checked = showFileExtension,
        onCheckedChange = { browserPreferences.showFileExtension.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.path),
        checked = showFolderPath,
        onCheckedChange = { browserPreferences.showFolderPath.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_total_videos),
        checked = showTotalVideosChip,
        onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_total_duration),
        checked = showTotalDurationChip,
        onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_folder_size),
        checked = showTotalSizeChip,
        onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_file_size),
        checked = showSizeChip,
        onCheckedChange = { browserPreferences.showSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_resolution),
        checked = showResolutionChip,
        onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_framerate),
        checked = showFramerateInResolution,
        onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.sort_type_date),
        checked = showDateChip,
        onCheckedChange = { browserPreferences.showDateChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_progress_bar),
        checked = showProgressBar,
        onCheckedChange = { browserPreferences.showProgressBar.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_subtitle_indicator),
        checked = showSubtitleIndicator,
        onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
      ),
    ),
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun VideoSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: VideoSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (VideoSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val backstack = LocalBackStack.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
  val includeNoMediaContent by browserPreferences.includeNoMediaContent.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showFileExtension by browserPreferences.showFileExtension.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = 1,
    )
  } else null

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_and_view_options),
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      VideoSortType.entries.find { it.displayName == typeName }?.let { newType ->
        if (newType == sortType) {
          val newOrder = if (sortOrder.isAscending) SortOrder.Descending else SortOrder.Ascending
          browserPreferences.saveVideoSortOrder(newType, newOrder)
          onSortOrderChange(newOrder)
        } else {
          browserPreferences.setVideoSortType(newType)
          onSortTypeChange(newType)
          onSortOrderChange(browserPreferences.getVideoSortOrder(newType))
        }
      }
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      val newOrder = if (isAsc) SortOrder.Ascending else SortOrder.Descending
      browserPreferences.saveVideoSortOrder(sortType, newOrder)
      onSortOrderChange(newOrder)
    },
    types = listOf(VideoSortType.Title.displayName, VideoSortType.Duration.displayName, VideoSortType.Date.displayName, VideoSortType.Size.displayName),
    typeLabels = listOf(stringResource(VideoSortType.Title.stringRes), stringResource(VideoSortType.Duration.stringRes), stringResource(VideoSortType.Date.stringRes), stringResource(VideoSortType.Size.stringRes)),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.AccessTime,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.AlbumView)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.FileManager)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        )
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout),
      firstOptionLabel = stringResource(R.string.layout_mode_list),
      secondOptionLabel = stringResource(R.string.layout_mode_grid),
      firstOptionIcon = Icons.AutoMirrored.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    contentToggles = listOf(
      ContentToggle(
        label = stringResource(R.string.filter_audio_files),
        checked = showAudioFiles,
        onCheckedChange = { browserPreferences.showAudioFiles.set(it) },
      ),
      ContentToggle(
        label = stringResource(R.string.filter_show_nomedia_folders),
        checked = includeNoMediaContent,
        onCheckedChange = { browserPreferences.includeNoMediaContent.set(it) },
      ),
    ),
    visibilityToggles =
      listOf(
        VisibilityToggle(
          label = stringResource(R.string.field_video_thumbnails),
          checked = showVideoThumbnails,
          onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_full_name),
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_file_extension),
          checked = showFileExtension,
          onCheckedChange = { browserPreferences.showFileExtension.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.path),
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_total_videos),
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_total_duration),
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_folder_size),
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_file_size),
          checked = showSizeChip,
          onCheckedChange = { browserPreferences.showSizeChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_resolution),
          checked = showResolutionChip,
          onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_framerate),
          checked = showFramerateInResolution,
          onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.sort_type_date),
          checked = showDateChip,
          onCheckedChange = { browserPreferences.showDateChip.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_progress_bar),
          checked = showProgressBar,
          onCheckedChange = { browserPreferences.showProgressBar.set(it) },
        ),
        VisibilityToggle(
          label = stringResource(R.string.field_subtitle_indicator),
          checked = showSubtitleIndicator,
          onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
        ),
      ),
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun FileSystemSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  isAtRoot: Boolean = true,
) {
  val backstack = LocalBackStack.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val folderSortType by browserPreferences.folderSortType.collectAsState()
  val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showFileExtension by browserPreferences.showFileExtension.collectAsState()
  val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
  val includeNoMediaContent by browserPreferences.includeNoMediaContent.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = 1,
    )
  } else null

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_and_view_options),
    sortType = folderSortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let { newType ->
        if (newType == folderSortType) {
          val newOrder = if (folderSortOrder.isAscending) SortOrder.Descending else SortOrder.Ascending
          browserPreferences.saveFolderSortOrder(newType, newOrder)
        } else {
          browserPreferences.setFolderSortType(newType)
        }
      }
    },
    sortOrderAsc = folderSortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      val newOrder = if (isAsc) SortOrder.Ascending else SortOrder.Descending
      browserPreferences.saveFolderSortOrder(folderSortType, newOrder)
    },
    types = listOf(FolderSortType.Title.displayName, FolderSortType.Duration.displayName, FolderSortType.Date.displayName, FolderSortType.Size.displayName),
    typeLabels = listOf(stringResource(FolderSortType.Title.stringRes), stringResource(FolderSortType.Duration.stringRes), stringResource(FolderSortType.Date.stringRes), stringResource(FolderSortType.Size.stringRes)),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.AccessTime,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    showSortOptions = true,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.AlbumView)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.FileManager)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = {
            browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary)
            while (backstack.size > 1) {
              backstack.navigateBack()
            }
          }
        )
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout),
      firstOptionLabel = stringResource(R.string.layout_mode_list),
      secondOptionLabel = stringResource(R.string.layout_mode_grid),
      firstOptionIcon = Icons.AutoMirrored.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST
          else MediaLayoutMode.GRID
        )
      },
    ),
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
    enableLayoutModeOptions = true, // Enabled for FileSystem/Tree view too!
    contentToggles = listOf(
      ContentToggle(
        label = stringResource(R.string.filter_audio_files),
        checked = showAudioFiles,
        onCheckedChange = { browserPreferences.showAudioFiles.set(it) },
      ),
      ContentToggle(
        label = stringResource(R.string.filter_show_nomedia_folders),
        checked = includeNoMediaContent,
        onCheckedChange = { browserPreferences.includeNoMediaContent.set(it) },
      ),
    ),
    visibilityToggles = listOf(
      VisibilityToggle(
        label = stringResource(R.string.field_video_thumbnails),
        checked = showVideoThumbnails,
        onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_full_name),
        checked = unlimitedNameLines,
        onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_file_extension),
        checked = showFileExtension,
        onCheckedChange = { browserPreferences.showFileExtension.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.path),
        checked = showFolderPath,
        onCheckedChange = { browserPreferences.showFolderPath.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_total_videos),
        checked = showTotalVideosChip,
        onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_total_duration),
        checked = showTotalDurationChip,
        onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_folder_size),
        checked = showTotalSizeChip,
        onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_file_size),
        checked = showSizeChip,
        onCheckedChange = { browserPreferences.showSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_resolution),
        checked = showResolutionChip,
        onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_framerate),
        checked = showFramerateInResolution,
        onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.sort_type_date),
        checked = showDateChip,
        onCheckedChange = { browserPreferences.showDateChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_progress_bar),
        checked = showProgressBar,
        onCheckedChange = { browserPreferences.showProgressBar.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.field_subtitle_indicator),
        checked = showSubtitleIndicator,
        onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
      ),
    )
  )
}

/**
 * Sort options for a network share.
 *
 * Only title, date and size are offered: a folder listing over SMB carries name, timestamp and
 * size, but not duration, and probing every remote file for it would mean opening each one.
 */
@Composable
fun NetworkSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val sortType by browserPreferences.networkSortType.collectAsState()
  val sortOrder by browserPreferences.networkSortOrder.collectAsState()

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_and_view_options),
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let { newType ->
        if (newType == sortType) {
          val newOrder = if (sortOrder.isAscending) SortOrder.Descending else SortOrder.Ascending
          browserPreferences.saveNetworkSortOrder(newType, newOrder)
        } else {
          browserPreferences.setNetworkSortType(newType)
        }
      }
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      val newOrder = if (isAsc) SortOrder.Ascending else SortOrder.Descending
      browserPreferences.saveNetworkSortOrder(sortType, newOrder)
    },
    types = listOf(FolderSortType.Title.displayName, FolderSortType.Date.displayName, FolderSortType.Size.displayName),
    typeLabels = listOf(stringResource(FolderSortType.Title.stringRes), stringResource(FolderSortType.Date.stringRes), stringResource(FolderSortType.Size.stringRes)),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    showSortOptions = true,
    enableViewModeOptions = false,
    enableLayoutModeOptions = false,
  )
}
