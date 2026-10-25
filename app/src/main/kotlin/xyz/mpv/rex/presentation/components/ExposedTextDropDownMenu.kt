package xyz.mpv.rex.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedTextDropDownMenu(
  selectedValue: String,
  options: ImmutableList<String>,
  label: String,
  onValueChangedEvent: (String) -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
  // Optional action pinned as the first menu entry (e.g. "Add Font File"). It does not change the selected value.
  actionLabel: String? = null,
  actionIcon: ImageVector? = null,
  actionIconTint: Color = LocalContentColor.current,
  // Optional icon shown in front of every regular option (e.g. the "Aa" glyph in the font menu).
  optionIcon: ImageVector? = null,
  onActionClick: (() -> Unit)? = null,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier,
  ) {
    OutlinedTextField(
      readOnly = true,
      value = selectedValue,
      onValueChange = {},
      label = { Text(text = label) },
      trailingIcon = {
        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
      },
      leadingIcon = leadingIcon,
      colors = OutlinedTextFieldDefaults.colors(),
      modifier =
        Modifier
          .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
          .fillMaxWidth(),
    )

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.heightIn(max = 300.dp),
    ) {
      if (actionLabel != null && onActionClick != null) {
        DropdownMenuItem(
          text = { Text(text = actionLabel) },
          leadingIcon = {
            if (actionIcon != null) {
              Icon(imageVector = actionIcon, contentDescription = null, tint = actionIconTint)
            }
          },
          onClick = {
            expanded = false
            onActionClick()
          },
        )
      }
      options.forEach { option: String ->
        DropdownMenuItem(
          text = { Text(text = option) },
          leadingIcon = optionIcon?.let { icon -> { Icon(imageVector = icon, contentDescription = null) } },
          onClick = {
            expanded = false
            onValueChangedEvent(option)
          },
        )
      }
    }
  }
}
