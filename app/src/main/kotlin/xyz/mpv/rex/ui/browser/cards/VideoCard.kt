package xyz.mpv.rex.ui.browser.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.ui.theme.pillShape

@Composable
fun VideoCard(
  video: Video,
  onClick: () -> Unit,
  uiSettings: UiSettings,
  modifier: Modifier = Modifier,
  isRecentlyPlayed: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  progressPercentage: Float? = null,
  isOldAndUnplayed: Boolean = false,
  isWatched: Boolean = false,
  isNeverPlayed: Boolean = false,
  onThumbClick: (() -> Unit)? = null,
  isGridMode: Boolean = false,
  gridColumns: Int = 1,
  showSubtitleIndicator: Boolean = true,
  useFolderNameStyle: Boolean = false,
  allowThumbnailGeneration: Boolean = true,
) {
  val maxLines = if (uiSettings.unlimitedNameLines) Int.MAX_VALUE else 2
  
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val thumbWidthDp = if (isGridMode) {
    if (gridColumns == 1) configuration.screenWidthDp.dp else 180.dp
  } else 128.dp
  val aspect = 16f / 9f
  val thumbWidthPx = with(LocalDensity.current) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

  val thumbnailKey = remember(video.id, video.dateModified, video.size, thumbWidthPx, thumbHeightPx) {
    thumbnailRepository.thumbnailKey(video, thumbWidthPx, thumbHeightPx)
  }

  var thumbnail by remember(thumbnailKey) {
    mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
  }

  LaunchedEffect(thumbnailKey) {
    thumbnailRepository.thumbnailReadyKeys.filter { it == thumbnailKey }.collect {
      thumbnail = thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx)
    }
  }

  LaunchedEffect(thumbnailKey, allowThumbnailGeneration, uiSettings.showVideoThumbnails) {
    if (thumbnail == null && uiSettings.showVideoThumbnails) {
      thumbnail = withContext(Dispatchers.IO) {
        if (allowThumbnailGeneration) {
          thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
        } else {
          thumbnailRepository.getCachedThumbnail(video, thumbWidthPx, thumbHeightPx)
        }
      }
    }
  }

  BaseMediaCard(
    title = video.displayName,
    listTitleStyle = MaterialTheme.typography.bodyMedium,
    modifier = modifier,
    thumbnailAspectRatio = 16f / 9f,
    thumbnail = if (uiSettings.showVideoThumbnails) thumbnail?.asImageBitmap() else null,
    thumbnailIcon = {
      Icon(
        if (video.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.secondary,
      )
    },
    onClick = onClick,
    onLongClick = onLongClick,
    onThumbClick = onThumbClick,
    isSelected = isSelected,
    isRecentlyPlayed = isRecentlyPlayed,
    isWatched = isWatched,
    isGridMode = isGridMode,
    gridColumns = gridColumns,
    progressPercentage = if (uiSettings.showProgressBar) progressPercentage else null,
    maxTitleLines = maxLines,
    thumbnailSize = thumbWidthDp,
    overlayContent = {
      // NEW Label
      if (uiSettings.showUnplayedOldVideoLabel && isOldAndUnplayed) {
        Surface(
          shape = pillShape,
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp),
          shadowElevation = 2.dp,
        ) {
          Text(
            text = stringResource(R.string.video_label_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }
      // Duration overlay
      Surface(
        shape = pillShape,
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(6.dp),
      ) {
        Text(
          text = video.durationFormatted,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
      }
    },
    infoContent = {
      if (video.isAudio && (video.artist.isNotBlank() || video.album.isNotBlank())) {
        val audioInfo = when {
          video.artist.isNotBlank() && video.album.isNotBlank() -> "${video.artist} • ${video.album}"
          video.artist.isNotBlank() -> video.artist
          else -> video.album
        }
        Text(
          text = audioInfo,
          style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    },
    chipsContent = {
      if (showSubtitleIndicator && video.hasEmbeddedSubtitles && video.subtitleCodec.isNotBlank()) {
        for (codec in video.subtitleCodec.split(" ")) {
          MediaMetadataChip(
            text = codec,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
      if (uiSettings.showSizeChip && video.sizeFormatted != "0 B" && video.sizeFormatted != "--") {
        MediaMetadataChip(text = video.sizeFormatted)
      }
      val fpsOnly = video.resolution.substringAfter("@", "")
      if (uiSettings.showResolutionChip && video.resolution != "--") {
        val displayRes = if (uiSettings.showFramerateInResolution) video.resolution else video.resolution.substringBefore("@")
        MediaMetadataChip(text = displayRes)
      } else if (uiSettings.showFramerateInResolution && fpsOnly.isNotEmpty()) {
        MediaMetadataChip(text = "$fpsOnly FPS")
      }
      if (uiSettings.showDateChip && video.dateModified > 0) {
        MediaMetadataChip(text = MediaFormatter.formatDate(video.dateModified * 1000))
      }
    }
  )
}
