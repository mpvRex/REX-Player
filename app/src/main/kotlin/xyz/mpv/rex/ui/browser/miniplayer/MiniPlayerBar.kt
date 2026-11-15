@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package xyz.mpv.rex.ui.browser.miniplayer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.controls.TonalIconTone
import xyz.mpv.rex.ui.controls.tonalIconButtonColors
import xyz.mpv.rex.ui.icons.RexIcon
import xyz.mpv.rex.ui.icons.RexIcons
import xyz.mpv.rex.ui.motion.RexPress
import xyz.mpv.rex.ui.theme.pillShape
import xyz.mpv.rex.utils.media.MediaFormatter

/**
 * The docked bar: a grip, the artwork, what is playing, and the two controls that make a bar worth
 * having — play/pause and close.
 *
 * Everything is anchored to the top of the sheet (the sheet's own height is the window), so the
 * bar stays exactly where it is while the surface grows underneath it, and only its alpha changes.
 *
 * @param pullFraction 0..1: how far the bar has been dragged towards the close zone. Used to
 *   squash the artwork and dim the grip as the bar is thrown away, never to move the bar itself —
 *   the sheet's own translation does that.
 * @param trackSwipe -1 (next is previewed, incoming text comes from the right), 0 (nothing),
 *   +1 (previous). Read in the layout/draw phase by the track-switching row.
 */
@Composable
internal fun MiniPlayerBar(
  state: MiniPlayerState,
  stateManager: MiniPlayerStateManager,
  pullFraction: () -> Float = { 0f },
  trackSwipe: () -> TrackSwipePreview = { NoTrackSwipe },
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val progress =
    remember(state.currentPositionMs, state.durationMs) {
      if (state.durationMs > 0) {
        (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
    }
  val timeSubtitle =
    remember(state.currentPositionMs, state.durationMs) {
      if (state.durationMs > 0) {
        "${MediaFormatter.formatDuration(state.currentPositionMs)} / ${MediaFormatter.formatDuration(state.durationMs)}"
      } else {
        null
      }
    }
  val subtitle = timeSubtitle ?: state.artist.ifBlank { stringResource(R.string.playlist_now_playing) }

  Box(modifier = modifier.fillMaxWidth().height(MiniPlayerDefaults.BarHeight)) {
    Row(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(start = 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MiniArtwork(
        bitmap = state.thumbnail,
        width = 60.dp,
        height = 44.dp,
        corner = 8.dp,
        modifier =
          Modifier.graphicsLayer {
            val s = 1f - pullFraction() * 0.18f
            scaleX = s
            scaleY = s
          },
      )

      Spacer(Modifier.width(12.dp))

      val preview = trackSwipe()
      val showsPreview = preview.direction != 0f && preview.fraction > 0f
      val displayTitle =
        when {
          !showsPreview -> state.title
          preview.direction < 0f -> state.nextTitle.orEmpty()
          else -> state.prevTitle.orEmpty()
        }.ifBlank { state.title.ifBlank { stringResource(R.string.playlist_now_playing) } }
      val forward = preview.direction < 0f
      val slideOffsetPx = preview.offsetPx
      Column(modifier = Modifier.weight(1f)) {
        AnimatedContent(
          targetState = displayTitle,
          transitionSpec = {
            (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
              (slideOutHorizontally { if (forward) -it / 2 else it / 2 } + fadeOut())
          },
          label = "miniPlayerTrackTitle",
        ) { text ->
          Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurface,
            modifier = Modifier.graphicsLayer { translationX = slideOffsetPx },
          )
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = scheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.width(4.dp))

      MiniSheetIconButton(
        onClick = stateManager::togglePlayPause,
        desc = stringResource(if (state.isPaused) R.string.play else R.string.pause),
        size = 40.dp,
      ) {
        PlayPauseGlyph(paused = state.isPaused, size = 22.dp)
      }

      MiniSheetIconButton(
        onClick = onClose,
        desc = stringResource(R.string.close),
        size = 40.dp,
        tone = TonalIconTone.Muted,
      ) {
        RexIcon(RexIcons.Close, modifier = Modifier.size(20.dp))
      }
    }

    LinearWavyProgressIndicator(
      progress = { progress },
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(4.dp)
          .padding(horizontal = 24.dp),
      color = scheme.primary,
      trackColor = androidx.compose.ui.graphics.Color.Transparent,
      amplitude = { if (state.isPaused) 0f else 1f },
      wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
      waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    )
  }
}

/**
 * The title, sliding in from the side the finger is pushing.
 *
 * While the swipe is in progress the text is translated by the distance travelled and the incoming
 * title replaces the outgoing one, so the bar shows *what is coming* before the finger lifts. A
 * committed switch plays the same slide through `AnimatedContent`.
 */
internal data class TrackSwipePreview(
  /** -1 while next is being previewed, +1 for previous, 0 for neither. */
  val direction: Float = 0f,
  /** 0..1 travel towards the switch threshold. */
  val fraction: Float = 0f,
  /** px the title block is pushed by the finger. */
  val offsetPx: Float = 0f,
)

/** The resting preview: no swipe in progress. */
internal val NoTrackSwipe = TrackSwipePreview()

/** Artwork tile, or a softened placeholder while the first frame is still being decoded. */
@Composable
internal fun MiniArtwork(
  bitmap: Bitmap?,
  size: Dp = 44.dp,
  width: Dp = size,
  height: Dp = size,
  modifier: Modifier = Modifier,
  corner: Dp = 10.dp,
) {
  val shape = remember(corner) { androidx.compose.foundation.shape.RoundedCornerShape(corner) }
  Box(
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    val safe = bitmap?.takeUnless { it.isRecycled }
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
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * `Bitmap.asImageBitmap()` allocates a wrapper every call, and the state re-emits at least once a
 * second, so the wrapper is remembered per bitmap instance.
 */
@Composable
internal fun Bitmap.asImageBitmapCached(): ImageBitmap = remember(this) { asImageBitmap() }

/** Play/pause that morphs between the two glyphs instead of swapping bitmaps. */
@Composable
internal fun PlayPauseGlyph(
  paused: Boolean,
  size: Dp,
) {
  AnimatedContent(
    targetState = paused,
    transitionSpec = {
      (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
        (
          scaleOut(spring(stiffness = Spring.StiffnessMediumLow)) +
            fadeOut()
        )
    },
    label = "miniPlayerPlayPause",
  ) { isPaused ->
    RexIcon(
      icon = if (isPaused) RexIcons.Play else RexIcons.Pause,
      isFilled = true,
      modifier = Modifier.size(size),
    )
  }
}

/**
 * A tonal icon button with expressive press feedback, sized by the caller.
 *
 * The press *scale* is used rather than the shape morph: `IconButtonDefaults.shapes()` presses a
 * 48/56 dp button toward a 10 dp corner radius, which reads as the button squaring off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MiniSheetIconButton(
  onClick: () -> Unit,
  desc: String,
  size: Dp,
  modifier: Modifier = Modifier,
  tone: TonalIconTone = TonalIconTone.Primary,
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  FilledTonalIconButton(
    onClick = onClick,
    interactionSource = interactionSource,
    colors = tonalIconButtonColors(tone),
    modifier =
      modifier
        .size(size)
        // `Button` role so TalkBack offers "double tap to activate" instead of reading a label
        // with no action attached.
        .semantics {
          contentDescription = desc
          role = Role.Button
        }
        .then(RexPress.pressScaleModifier(interactionSource)),
    content = content,
  )
}
