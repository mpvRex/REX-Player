package xyz.mpv.rex.ui.browser.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.pillShape

/**
 * Common Metadata Chip for media cards
 */
@Composable
fun MediaMetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: Shape = pillShape,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Base Media Card that provides a unified layout for List and Grid modes.
 * Used by VideoCard, NetworkVideoCard, etc.
 */
@Composable
fun BaseMediaCard(
    title: String,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
    thumbnailIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onThumbClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isRecentlyPlayed: Boolean = false,
    isWatched: Boolean = false,
    isGridMode: Boolean = false,
    gridColumns: Int = 1,
    progressPercentage: Float? = null,
    maxTitleLines: Int = 2,
    titleTextAlign: TextAlign = TextAlign.Start,
    thumbnailSize: Dp = 64.dp,
    thumbnailAspectRatio: Float = 16f / 9f,
    listTitleStyle: TextStyle? = null,
    showThumbnailBackground: Boolean = true,
    infoContent: @Composable (RowScope.() -> Unit)? = null,
    chipsContent: @Composable (FlowRowScope.() -> Unit)? = null,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null,
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val cardBackground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                      else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardBackground"
    )
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isSelected) 22.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardCornerRadius"
    )
    val cardShape = RoundedCornerShape(animatedCornerRadius)
    val thumbnailShape = RoundedCornerShape(14.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isGridMode && gridColumns > 1) Modifier else Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .clip(cardShape)
            .background(cardBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        if (isGridMode) {
            // GRID LAYOUT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (gridColumns == 1) Modifier.padding(horizontal = 8.dp, vertical = 6.dp) else Modifier.padding(4.dp)),
                horizontalAlignment = Alignment.Start,
            ) {
                // Thumbnail Box
                Box(
                    modifier = if (showThumbnailBackground) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(thumbnailAspectRatio)
                            .clip(thumbnailShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .then(
                                if (onThumbClick != null) {
                                    Modifier.combinedClickable(
                                        onClick = { onThumbClick() },
                                        onLongClick = onLongClick
                                    )
                                } else Modifier
                            )
                    } else {
                        if (onThumbClick != null) {
                            Modifier.combinedClickable(
                                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onLongClick = onLongClick,
                                onClick = { onThumbClick() }
                            )
                        } else {
                            Modifier
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (thumbnailIcon != null) {
                        thumbnailIcon()
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    // Progress Bar
                    if (progressPercentage != null) {
                        LinearProgressIndicator(
                            progress = { progressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.35f),
                        )
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(22.dp),
                            shadowElevation = 3.dp,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(3.dp),
                            )
                        }
                    }

                    overlayContent?.invoke(this)
                }

                Spacer(modifier = Modifier.height(4.dp))

                val shouldHighlight = isRecentlyPlayed && !isWatched
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        shouldHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        isWatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = maxTitleLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = titleTextAlign,
                    fontWeight = if (shouldHighlight) FontWeight.Black else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Extra info row (if any)
                if (infoContent != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().then(
                            if (gridColumns == 1) Modifier.padding(vertical = 2.dp) else Modifier
                        ),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        infoContent()
                    }
                }

                if (chipsContent != null && gridColumns == 1) {
                    Spacer(modifier = Modifier.height(1.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        chipsContent()
                    }
                }
            }
        } else {
            // LIST LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
        // Thumbnail Box
        Box(
          modifier = if (showThumbnailBackground) {
            Modifier
              .width(thumbnailSize)
              .aspectRatio(thumbnailAspectRatio)
              .clip(thumbnailShape)
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .then(
                  if (onThumbClick != null) {
                      Modifier.combinedClickable(
                          onClick = { onThumbClick() },
                          onLongClick = onLongClick
                      )
                  } else Modifier
              )
          } else {
            if (onThumbClick != null) {
                Modifier.combinedClickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = { onThumbClick() }
                )
            } else {
                Modifier
            }
          },
          contentAlignment = Alignment.Center,
        ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (thumbnailIcon != null) {
                        thumbnailIcon()
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(thumbnailSize / 1.5f),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    // Progress Bar
                    if (progressPercentage != null) {
                        LinearProgressIndicator(
                            progress = { progressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.35f),
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(20.dp),
                            shadowElevation = 3.dp,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(2.5.dp),
                            )
                        }
                    }

                    overlayContent?.invoke(this)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val shouldHighlight = isRecentlyPlayed && !isWatched
                    Text(
                        text = title,
                        style = listTitleStyle ?: MaterialTheme.typography.titleMedium,
                        color = when {
                            shouldHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            isWatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = maxTitleLines,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (shouldHighlight) FontWeight.Black else FontWeight.Normal,
                    )

                    if (infoContent != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            infoContent()
                        }
                    }

                    if (chipsContent != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chipsContent()
                        }
                    }
                }
            }
        }
    }
}
