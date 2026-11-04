package xyz.mpv.rex.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.icons.MaterialSymbolIcon
import xyz.mpv.rex.ui.icons.Icon as RexIconComposable

data class RexSettingsItem(
    val icon: Any? = null,
    val palette: SettingsBadgePalette? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val showChevron: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
fun RexSettingsGroup(
    title: String? = null,
    items: List<RexSettingsItem>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    badgeShape: Shape = RoundedCornerShape(12.dp),
    outerCornerRadius: Dp = 20.dp,
    innerCornerRadius: Dp = 4.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp, top = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items.forEachIndexed { index, item ->
                val shape = when {
                    items.size == 1 -> RoundedCornerShape(outerCornerRadius)
                    index == 0 -> RoundedCornerShape(
                        topStart = outerCornerRadius,
                        topEnd = outerCornerRadius,
                        bottomStart = innerCornerRadius,
                        bottomEnd = innerCornerRadius
                    )
                    index == items.size - 1 -> RoundedCornerShape(
                        topStart = innerCornerRadius,
                        topEnd = innerCornerRadius,
                        bottomStart = outerCornerRadius,
                        bottomEnd = outerCornerRadius
                    )
                    else -> RoundedCornerShape(innerCornerRadius)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    RexSettingsRow(item = item, badgeShape = badgeShape)
                }
            }
        }
    }
}

@Composable
fun RexSettingsRow(
    item: RexSettingsItem,
    modifier: Modifier = Modifier,
    badgeShape: Shape = RoundedCornerShape(12.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rex_settings_row_scale"
    )

    val itemPalette = item.palette ?: getIconPalette(item.icon)
    val iconBgColor by animateColorAsState(
        targetValue = if (!item.enabled) MaterialTheme.colorScheme.surfaceContainerHighest else itemPalette.container,
        label = "rex_settings_icon_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (!item.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else itemPalette.tint,
        label = "rex_settings_icon_tint"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                enabled = item.enabled && item.onClick != null,
                interactionSource = interactionSource,
                indication = null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let { icon ->
            Surface(
                modifier = Modifier.size(40.dp),
                shape = badgeShape,
                color = iconBgColor
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    when (icon) {
                        is MaterialSymbolIcon -> RexIconComposable(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                        is ImageVector -> Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                item.title()
            }
            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                ) {
                    desc()
                }
            }
        }

        if (item.trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            item.trailingContent.invoke()
        } else if (item.showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
