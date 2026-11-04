package xyz.mpv.rex.ui.browser.miniplayer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import xyz.mpv.rex.ui.player.MediaPlaybackService
import xyz.mpv.rex.ui.player.RepeatMode
import xyz.mpv.rex.ui.theme.pillShape
import xyz.mpv.rex.utils.media.MediaFormatter
import kotlin.math.roundToInt

object MiniPlayerDefaults {
  val CompactHeight = 80.dp
  val ExpandedHeight = 440.dp
  val FloatingBottomMargin = 8.dp
  val TotalCompactOffset = 88.dp
}

@Composable
fun MiniPlayer(
  stateManager: MiniPlayerStateManager,
  modifier: Modifier = Modifier,
) {
  val state by stateManager.state.collectAsState()
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val offsetX = remember { Animatable(0f) }
  val expansionFraction = remember { Animatable(0f) }
  var contentWidth by remember { mutableFloatStateOf(1f) }
  var totalDragDelta by remember { mutableFloatStateOf(0f) }

  val fraction = expansionFraction.value.coerceIn(0f, 1f)

  val smoothSpringSpec = remember {
    spring<Float>(
      stiffness = Spring.StiffnessMediumLow,
      dampingRatio = Spring.DampingRatioLowBouncy,
    )
  }

  // Sync expansion state if triggered externally
  LaunchedEffect(state.isExpanded) {
    val target = if (state.isExpanded) 1f else 0f
    if (expansionFraction.value != target) {
      expansionFraction.animateTo(target, smoothSpringSpec)
    }
  }

  // Intercept back button when expanded to collapse sheet
  BackHandler(enabled = fraction > 0.5f) {
    coroutineScope.launch {
      expansionFraction.animateTo(0f, smoothSpringSpec)
      stateManager.setExpanded(false)
    }
  }

  AnimatedVisibility(
    visible = state.isPlaybackActive,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
    modifier = modifier,
  ) {
    val animatedHeight = lerp(MiniPlayerDefaults.CompactHeight, MiniPlayerDefaults.ExpandedHeight, fraction)
    val outerCorner = lerp(24.dp, 28.dp, fraction)

    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(animatedHeight)
        .padding(horizontal = 8.dp)
        .clip(RoundedCornerShape(
          topStart = outerCorner,
          topEnd = outerCorner,
          bottomStart = lerp(20.dp, 28.dp, fraction),
          bottomEnd = lerp(20.dp, 28.dp, fraction)
        )),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
      ),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            detectVerticalDragGestures(
              onDragStart = {
                totalDragDelta = 0f
              },
              onDragEnd = {
                coroutineScope.launch {
                  val target = when {
                    totalDragDelta < -30f -> 1f // Swiped upward -> expand
                    totalDragDelta > 30f -> 0f  // Swiped downward -> collapse
                    expansionFraction.value > 0.35f -> 1f
                    else -> 0f
                  }
                  expansionFraction.animateTo(target, smoothSpringSpec)
                  stateManager.setExpanded(target == 1f)
                }
              },
              onVerticalDrag = { change, dragAmount ->
                change.consume()
                totalDragDelta += dragAmount
                coroutineScope.launch {
                  val delta = -dragAmount / 350f
                  val newFraction = (expansionFraction.value + delta).coerceIn(0f, 1f)
                  expansionFraction.snapTo(newFraction)
                }
              }
            )
          }
      ) {

        val compactAlpha = (1f - fraction * 3.5f).coerceIn(0f, 1f)
        val expandedHeaderAlpha = ((fraction - 0.2f) * 2.5f).coerceIn(0f, 1f)
        val expandedTextAlpha = ((fraction - 0.3f) * 2.5f).coerceIn(0f, 1f)
        val seekbarAlpha = ((fraction - 0.4f) * 2.5f).coerceIn(0f, 1f)
        val expandedControlsAlpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)

        // ──────────────────────────────────────────────────────────────
        // Drag Pill Bar Visual Indicator
        // ──────────────────────────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .align(Alignment.TopCenter)
            .zIndex(10f),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(
            modifier = Modifier
              .padding(top = 7.dp)
              .width(36.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
          )
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Header (Collapse Arrow, "NOW PLAYING", Close Button)
        // ──────────────────────────────────────────────────────────────
        if (expandedHeaderAlpha > 0f) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 16.dp, start = 12.dp, end = 12.dp)
              .graphicsLayer {
                alpha = expandedHeaderAlpha
                translationY = (1f - expandedHeaderAlpha) * -15f
              }
              .align(Alignment.TopCenter)
              .zIndex(11f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(
              onClick = {
                coroutineScope.launch {
                  expansionFraction.animateTo(0f, smoothSpringSpec)
                  stateManager.setExpanded(false)
                }
              },
            ) {
              Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse Mini Player",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            Text(
              text = "NOW PLAYING",
              style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
              ),
              color = MaterialTheme.colorScheme.primary,
            )

            IconButton(
              onClick = {
                stateManager.closeMiniPlayer(context)
              },
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close Mini Player",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Collapsed Content Row (Track title/artist + Play/Pause + Close)
        // ──────────────────────────────────────────────────────────────
        if (compactAlpha > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 18.dp)
              .graphicsLayer { alpha = compactAlpha },
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(47.dp)
                .clipToBounds()
                .onSizeChanged { contentWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(state.hasNext, state.hasPrevious) {
                  detectHorizontalDragGestures(
                    onDragEnd = {
                      coroutineScope.launch {
                        val threshold = contentWidth * 0.25f
                        val currentOffset = offsetX.value

                        if (currentOffset < -threshold && state.hasNext) {
                          offsetX.animateTo(-contentWidth, tween(180))
                          stateManager.playNext()
                          offsetX.snapTo(0f)
                        } else if (currentOffset > threshold && state.hasPrevious) {
                          offsetX.animateTo(contentWidth, tween(180))
                          stateManager.playPrevious()
                          offsetX.snapTo(0f)
                        } else {
                          offsetX.animateTo(0f, smoothSpringSpec)
                        }
                      }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      coroutineScope.launch {
                        val proposed = offsetX.value + dragAmount
                        val clamped = when {
                          proposed < 0 -> if (state.hasNext) proposed else 0f
                          proposed > 0 -> if (state.hasPrevious) proposed else 0f
                          else -> proposed
                        }
                        offsetX.snapTo(clamped)
                      }
                    }
                  )
                }
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                ) {
                  coroutineScope.launch {
                    expansionFraction.animateTo(1f, smoothSpringSpec)
                    stateManager.setExpanded(true)
                  }
                },
            ) {
              val currentDx = offsetX.value.roundToInt()

              // Next track preview
              if (offsetX.value < 0 && state.hasNext) {
                val nextDx = (contentWidth + offsetX.value).roundToInt()
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(nextDx, 0) }
                    .padding(start = 18.dp, end = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    val nextThumb = state.nextThumbnail
                    if (nextThumb != null && !nextThumb.isRecycled) {
                      Image(
                        bitmap = nextThumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = state.nextTitle?.ifBlank { "Next Track" } ?: "Next Track",
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = "Swipe to play next",
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }

              // Previous track preview
              if (offsetX.value > 0 && state.hasPrevious) {
                val prevDx = (-contentWidth + offsetX.value).roundToInt()
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(prevDx, 0) }
                    .padding(start = 18.dp, end = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    val prevThumb = state.prevThumbnail
                    if (prevThumb != null && !prevThumb.isRecycled) {
                      Image(
                        bitmap = prevThumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = state.prevTitle?.ifBlank { "Previous Track" } ?: "Previous Track",
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = "Swipe to play previous",
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }

              // Current track title/artist & compact action buttons
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .fillMaxHeight()
                  .offset { IntOffset(currentDx, 0) }
                  .padding(start = 74.dp, end = 6.dp), // Start margin 74dp leaves space for morphing artwork
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = state.title.ifBlank { "Playing Media" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  val timeText = if (state.durationMs > 0) {
                    "${MediaFormatter.formatDuration(state.currentPositionMs)} / ${MediaFormatter.formatDuration(state.durationMs)}"
                  } else {
                    state.artist.ifBlank { "Background Playback" }
                  }
                  Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                FilledTonalIconButton(
                  onClick = { stateManager.togglePlayPause() },
                  colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                  modifier = Modifier.size(40.dp),
                ) {
                  AnimatedContent(
                    targetState = state.isPaused,
                    transitionSpec = {
                      (scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                        (scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut())
                    },
                    label = "compactPlayPauseMorph"
                  ) { isPaused ->
                    Icon(
                      imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                      contentDescription = if (isPaused) "Play" else "Pause",
                      modifier = Modifier.size(22.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                  onClick = {
                    stateManager.closeMiniPlayer(context)
                  },
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Mini Player",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } // end sliding Box

            val progress = if (state.durationMs > 0) {
              (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val animatedProgress by animateFloatAsState(
              targetValue = progress,
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
              label = "miniPlayerProgress"
            )

            val isPlaying = !state.isPaused
            val animatedAmplitude by animateFloatAsState(
              targetValue = if (isPlaying) 1f else 0f,
              animationSpec = tween(300),
              label = "miniPlayerWaveAmp"
            )

            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LinearWavyProgressIndicator(
              progress = { animatedProgress },
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              amplitude = { p -> if (p > 0f) animatedAmplitude else 0f },
              wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
              waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
            )
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Morphing Artwork Container (Glides smoothly between compact and expanded)
        // ──────────────────────────────────────────────────────────────
        val artworkSize = lerp(46.dp, 144.dp, fraction)
        val artworkCorner = lerp(10.dp, 20.dp, fraction)
        val artworkTopPadding = lerp(17.dp, 60.dp, fraction)
        val artworkHorizBias = lerp(-1f, 0f, fraction)
        val artworkStartPadding = lerp(16.dp, 0.dp, fraction)
        val artworkDx = (offsetX.value * compactAlpha).roundToInt()

        Box(
          modifier = Modifier
            .padding(top = artworkTopPadding, start = artworkStartPadding)
            .align(BiasAlignment(artworkHorizBias, -1f))
            .offset { IntOffset(artworkDx, 0) }
            .size(artworkSize)
            .shadow(
              elevation = lerp(0.dp, 8.dp, fraction),
              shape = RoundedCornerShape(artworkCorner)
            )
            .clip(RoundedCornerShape(artworkCorner))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
              if (fraction > 0.5f) {
                stateManager.openPlayer(context)
              } else {
                coroutineScope.launch {
                  expansionFraction.animateTo(1f, smoothSpringSpec)
                  stateManager.setExpanded(true)
                }
              }
            },
          contentAlignment = Alignment.Center,
        ) {
          val thumbnail = state.thumbnail
          if (thumbnail != null && !thumbnail.isRecycled) {
            Image(
              bitmap = thumbnail.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(
              imageVector = Icons.Filled.VideoLibrary,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(lerp(24.dp, 52.dp, fraction)),
            )
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Mode: Title & Subtitle Info (Fades in below expanded artwork)
        // ──────────────────────────────────────────────────────────────
        if (expandedTextAlpha > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 220.dp, start = 16.dp, end = 16.dp)
              .graphicsLayer {
                alpha = expandedTextAlpha
                translationY = (1f - expandedTextAlpha) * 20f
              },
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = state.title.ifBlank { "Playing Media" },
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )

            Text(
              text = state.artist.ifBlank { "REX Player" },
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Seekbar + Duration Labels
        // ──────────────────────────────────────────────────────────────
        if (seekbarAlpha > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 275.dp, start = 16.dp, end = 16.dp)
              .graphicsLayer {
                alpha = seekbarAlpha
                translationY = (1f - seekbarAlpha) * 25f
              },
          ) {
            var sliderValue by remember(state.currentPositionMs) {
              mutableFloatStateOf(state.currentPositionMs.toFloat())
            }
            var isDraggingSlider by remember { mutableStateOf(false) }

            val maxDuration = state.durationMs.coerceAtLeast(1L).toFloat()
            val currentPosFloat = if (isDraggingSlider) sliderValue else state.currentPositionMs.toFloat().coerceIn(0f, maxDuration)

            Slider(
              value = currentPosFloat,
              onValueChange = {
                isDraggingSlider = true
                sliderValue = it
              },
              onValueChangeFinished = {
                isDraggingSlider = false
                stateManager.seekTo(sliderValue.toLong())
              },
              valueRange = 0f..maxDuration,
              modifier = Modifier.fillMaxWidth(),
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              ),
            )

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = MediaFormatter.formatDuration(if (isDraggingSlider) sliderValue.toLong() else state.currentPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = MediaFormatter.formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Control Row (Shuffle | Prev | Play/Pause | Next | Repeat)
        // ──────────────────────────────────────────────────────────────
        if (expandedControlsAlpha > 0f) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 352.dp, start = 16.dp, end = 16.dp)
              .graphicsLayer {
                alpha = expandedControlsAlpha
                translationY = (1f - expandedControlsAlpha) * 35f
              },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val isShuffle = state.shuffleEnabled
            FilledTonalIconButton(
              onClick = { stateManager.toggleShuffle() },
              colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isShuffle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              modifier = Modifier.size(46.dp),
            ) {
              Icon(
                imageVector = if (isShuffle) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
                contentDescription = "Toggle Shuffle",
                modifier = Modifier.size(24.dp),
              )
            }

            FilledTonalIconButton(
              onClick = { stateManager.playPrevious() },
              colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
              ),
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous Track",
                modifier = Modifier.size(28.dp),
              )
            }

            Surface(
              onClick = { stateManager.togglePlayPause() },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              shadowElevation = 6.dp,
              modifier = Modifier.size(64.dp),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
              ) {
                AnimatedContent(
                  targetState = state.isPaused,
                  transitionSpec = {
                    (scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                      (scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut())
                  },
                  label = "expandedPlayPauseMorph"
                ) { isPaused ->
                  Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "Play" else "Pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                  )
                }
              }
            }

            FilledTonalIconButton(
              onClick = { stateManager.playNext() },
              colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
              ),
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next Track",
                modifier = Modifier.size(28.dp),
              )
            }

            val repeatIcon = when (state.repeatMode) {
              RepeatMode.OFF -> Icons.Filled.Repeat
              RepeatMode.ONE -> Icons.Filled.RepeatOne
              RepeatMode.ALL -> Icons.Filled.RepeatOn
            }
            val isRepeatActive = state.repeatMode != RepeatMode.OFF

            FilledTonalIconButton(
              onClick = { stateManager.cycleRepeatMode() },
              colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isRepeatActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              modifier = Modifier.size(46.dp),
            ) {
              Icon(
                imageVector = repeatIcon,
                contentDescription = "Cycle Repeat Mode",
                modifier = Modifier.size(24.dp),
              )
            }
          }
        }
      }
    }
  }
}
