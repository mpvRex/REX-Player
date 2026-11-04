package xyz.mpv.rex.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.mpv.rex.R

/**
 * Expressive Collapsible Header Screen scaffold.
 * Displays an expanded large headline that seamlessly collapses into a compact top bar as the user scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleHeaderScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    headerContent: (@Composable () -> Unit)? = null,
    headerContentSpacing: Dp = 8.dp,
    containerColor: Color = Color.Transparent,
    backButtonContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    backButtonContentColor: Color = MaterialTheme.colorScheme.onSurface,
    headerBlendHeight: Dp = 24.dp,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        topAppBarState,
        canScroll = { true }
    )

    // Entrance animation state
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(40)
        showContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "collapsibleContentAlpha"
    )

    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 24f,
        animationSpec = tween(durationMillis = 400),
        label = "collapsibleContentOffset"
    )

    val headerBlendBaseColor = if (containerColor == Color.Transparent) {
        MaterialTheme.colorScheme.surface
    } else {
        containerColor
    }

    var expandedHeaderHeight by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = floatingActionButton,
        topBar = {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            val fontSize = (22 + (32 - 22) * (1 - collapsedFraction)).sp

            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (scrollBehavior.state.heightOffset == 0f || expandedHeaderHeight == 0) {
                        expandedHeaderHeight = coordinates.size.height - scrollBehavior.state.heightOffset.toInt()
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                LargeTopAppBar(
                    title = {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(start = if (showBackButton) 4.dp else 12.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = fontSize
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(backButtonContainerColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                        tint = backButtonContentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            actions()
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = containerColor
                    )
                )
                if (headerContent != null) {
                    Spacer(modifier = Modifier.height(headerContentSpacing))
                    headerContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerBlendHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    headerBlendBaseColor,
                                    headerBlendBaseColor.copy(alpha = 0.72f),
                                    headerBlendBaseColor.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    ) { paddingValues ->
        val density = LocalDensity.current
        val baseTopPadding = remember(expandedHeaderHeight, paddingValues) {
            if (expandedHeaderHeight > 0) {
                with(density) { expandedHeaderHeight.toDp() }
            } else {
                paddingValues.calculateTopPadding()
            }
        }
        val dynamicTopPadding = with(density) {
            (baseTopPadding.toPx() + scrollBehavior.state.heightOffset).toDp()
                .coerceAtLeast(headerBlendHeight)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = (dynamicTopPadding - headerBlendHeight).coerceAtLeast(0.dp))
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                }
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

/**
 * Standalone Large Top App Bar with expressive collapsing typography.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleLargeTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Transparent,
    scrolledContainerColor: Color = containerColor,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    backButtonContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    backButtonContentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val fontSize = (22 + (32 - 22) * (1 - collapsedFraction)).sp

    LargeTopAppBar(
        modifier = modifier,
        title = {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = if (showBackButton) 4.dp else 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fontSize
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(backButtonContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = backButtonContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor
        )
    )
}
