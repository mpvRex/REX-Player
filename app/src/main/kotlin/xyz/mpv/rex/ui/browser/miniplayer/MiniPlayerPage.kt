@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.mpv.rex.ui.browser.miniplayer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.components.WaveSlider
import xyz.mpv.rex.ui.controls.TonalIconTone
import xyz.mpv.rex.ui.icons.RexIcon
import xyz.mpv.rex.ui.icons.RexIcons
import xyz.mpv.rex.ui.player.RepeatMode
import xyz.mpv.rex.ui.theme.pillShape
import xyz.mpv.rex.utils.media.MediaFormatter

/**
 * The full-screen player the docked bar morphs into.
 *
 * It is the *page*, not another bar with more rows: one artwork, one title, one seek, one
 * transport row. Everything is centred on a `Column` with the transport row pinned above the
 * bottom edge, so the page has the same visual weight as any player screen while still being the
 * same Compose subtree the bar lives in.
 *
 * **Adapting to the space.** In landscape, in split-screen and on short phones the fixed pieces
 * (header 56 + artwork + slider + transport + punch-out) can exceed the window. The column is
 * therefore scrollable, and the artwork height is computed from the height actually available, so
 * the transport row is never clipped and the page never overflows its own clip.
 *
 * @param onCollapse the explicit "back to the bar" affordance. Dragging down does the same thing.
 * @param onOpenFullPlayer hands the live session to [xyz.mpv.rex.ui.player.PlayerActivity], which
 *   is the only place the real video surface and the full control set exist.
 */
@Composable
internal fun MiniPlayerPage(
  state: MiniPlayerState,
  stateManager: MiniPlayerStateManager,
  onCollapse: () -> Unit,
  onOpenFullPlayer: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState(),
) {
  val scheme = MaterialTheme.colorScheme
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

  val openPlayerLabel = stringResource(R.string.open_full_player)
  val positionText = remember(state.currentPositionMs) { MediaFormatter.formatDuration(state.currentPositionMs) }
  val durationText = remember(state.durationMs) { MediaFormatter.formatDuration(state.durationMs) }

  if (isLandscape) {
    Row(
      modifier =
        modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left side: Artwork only. The header row and its collapse/close buttons are
      // intentionally omitted in landscape so the artwork gets the full column height.
      Box(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentAlignment = Alignment.Center,
      ) {
        PageArtwork(bitmap = state.thumbnail, isLandscape = isLandscape, screenHeightDp = configuration.screenHeightDp)
      }

      // Right side: Controls
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        PlayerControlsColumn(state, stateManager, onOpenFullPlayer, openPlayerLabel, positionText, durationText, isLandscape)
      }
    }
  } else {
    Column(
      modifier =
        modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .statusBarsPadding()
          .navigationBarsPadding()
          .verticalScroll(scrollState)
          .padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    // ── header ────────────────────────────────────────────────────────────────────────
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(56.dp)
          .padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MiniSheetIconButton(
        onClick = onCollapse,
        desc = stringResource(R.string.mini_player_collapse),
        size = 48.dp,
        tone = TonalIconTone.Muted,
      ) {
        RexIcon(RexIcons.ExpandMore, modifier = Modifier.size(24.dp))
      }

      Text(
        text = stringResource(R.string.playlist_now_playing),
        style = MaterialTheme.typography.labelMedium,
        color = scheme.primary,
      )

      MiniSheetIconButton(
        onClick = onClose,
        desc = stringResource(R.string.close),
        size = 48.dp,
        tone = TonalIconTone.Muted,
      ) {
        RexIcon(RexIcons.Close, modifier = Modifier.size(20.dp))
      }
    }

    Spacer(Modifier.height(8.dp))

    // ── artwork ───────────────────────────────────────────────────────────────────────
    PageArtwork(bitmap = state.thumbnail, isLandscape = isLandscape, screenHeightDp = configuration.screenHeightDp)

    Spacer(Modifier.height(if (isLandscape) 12.dp else 24.dp))

      PlayerControlsColumn(state, stateManager, onOpenFullPlayer, openPlayerLabel, positionText, durationText, isLandscape)
    }
  }
}

@Composable
private fun PlayerControlsColumn(
  state: MiniPlayerState,
  stateManager: MiniPlayerStateManager,
  onOpenFullPlayer: () -> Unit,
  openPlayerLabel: String,
  positionText: String,
  durationText: String,
  isLandscape: Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // ── title block ───────────────────────────────────────────────────────────────────
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(88.dp)
          .padding(horizontal = MiniPlayerPageDefaults.HorizontalGutter),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = state.title.ifBlank { stringResource(R.string.playlist_now_playing) },
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        color = scheme.onSurface,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = state.artist.ifBlank { stringResource(R.string.app_name) },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        color = scheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(if (isLandscape) 12.dp else 20.dp))

    // ── seek ─────────────────────────────────────────────────────────────────────────
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MiniPlayerPageDefaults.HorizontalGutter),
    ) {
      WaveSlider(
        value = state.currentPositionMs.toFloat(),
        onValueChange = { stateManager.seekTo(it.toLong()) },
        valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
        enabled = state.durationMs > 0,
        isPlaying = !state.isPaused,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = positionText,
          style = MaterialTheme.typography.labelSmall,
          color = scheme.onSurfaceVariant,
        )
        Text(
          text = durationText,
          style = MaterialTheme.typography.labelSmall,
          color = scheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.height(if (isLandscape) 12.dp else 24.dp))

    TransportRow(state = state, stateManager = stateManager)

    Spacer(Modifier.height(24.dp))

    BottomRow(
      state = state,
      stateManager = stateManager,
      onOpenFullPlayer = onOpenFullPlayer,
      openPlayerLabel = openPlayerLabel,
    )

    Spacer(Modifier.height(24.dp))
  }
}

private val ArtworkCorner = RoundedCornerShape(32.dp)

@Composable
private fun PageArtwork(
  bitmap: Bitmap?,
  isLandscape: Boolean,
  screenHeightDp: Int,
) {
  // An expressive, organic silhouette for the missing-artwork case; a real thumbnail is a plain
  // rounded rectangle so the artwork is never clipped into a shape it was not made for.
  val fallbackShape = MaterialShapes.Cookie9Sided.toShape()
  val safe = bitmap?.takeUnless { it.isRecycled }
  val hasArtwork = safe != null
  val shape = if (hasArtwork) ArtworkCorner else fallbackShape

  Box(
    modifier =
      Modifier
        .widthIn(max = 320.dp)
        .fillMaxWidth(if (isLandscape) 0.85f else 0.72f)
        .aspectRatio(1f)
        .shadow(elevation = 6.dp, shape = shape)
        .clip(shape)
        .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    if (safe != null) {
      Image(
        bitmap = safe.asImageBitmapCached(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      RexIcon(
        RexIcons.Movie,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
private fun TransportRow(
  state: MiniPlayerState,
  stateManager: MiniPlayerStateManager,
) {
  val playPauseLabel = stringResource(if (state.isPaused) R.string.play else R.string.pause)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MiniSheetIconButton(
      onClick = stateManager::playPrevious,
      desc = stringResource(R.string.mini_player_previous),
      size = 56.dp,
      tone = TonalIconTone.Muted,
    ) {
      RexIcon(RexIcons.SkipPrevious, isFilled = true, modifier = Modifier.size(32.dp))
    }

    // Large, pill-shaped Play/Pause button
    Surface(
      onClick = stateManager::togglePlayPause,
      shape = MaterialShapes.Pill.toShape(),
      color = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier =
        Modifier
          .weight(1f)
          .padding(horizontal = 16.dp)
          .height(64.dp)
          .semantics {
            contentDescription = playPauseLabel
            role = Role.Button
          },
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlayPauseGlyph(paused = state.isPaused, size = 36.dp)
      }
    }

    MiniSheetIconButton(
      onClick = stateManager::playNext,
      desc = stringResource(R.string.mini_player_next),
      size = 56.dp,
      tone = TonalIconTone.Muted,
    ) {
      RexIcon(RexIcons.SkipNext, isFilled = true, modifier = Modifier.size(32.dp))
    }
  }
}

@Composable
private fun BottomRow(
  state: MiniPlayerState,
  stateManager: MiniPlayerStateManager,
  onOpenFullPlayer: () -> Unit,
  openPlayerLabel: String,
) {
  val shuffleOn = state.shuffleEnabled
  val repeatOn = state.repeatMode != RepeatMode.OFF
  
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MiniSheetIconButton(
      onClick = stateManager::toggleShuffle,
      desc = stringResource(R.string.mini_player_shuffle),
      size = 48.dp,
      tone = if (shuffleOn) TonalIconTone.Active else TonalIconTone.Muted,
    ) {
      RexIcon(RexIcons.Shuffle, isFilled = shuffleOn, modifier = Modifier.size(24.dp))
    }
    
    Spacer(Modifier.width(16.dp))

    // ── punch-out to the real player ───────────────────────────────────────────────────
    Surface(
      onClick = onOpenFullPlayer,
      shape = pillShape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      contentColor = MaterialTheme.colorScheme.onSurface,
      modifier =
        Modifier.semantics {
          contentDescription = openPlayerLabel
          role = Role.Button
        },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RexIcon(RexIcons.Fullscreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = openPlayerLabel,
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
    
    Spacer(Modifier.width(16.dp))

    MiniSheetIconButton(
      onClick = stateManager::cycleRepeatMode,
      desc = stringResource(R.string.mini_player_repeat),
      size = 48.dp,
      tone = if (repeatOn) TonalIconTone.Active else TonalIconTone.Muted,
    ) {
      RexIcon(
        icon = if (state.repeatMode == RepeatMode.ONE) RexIcons.RepeatOne else RexIcons.Repeat,
        isFilled = repeatOn,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
