package xyz.mpv.rex.ui.player.controls.components.sheets

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.player.TrackNode
import xyz.mpv.rex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <T> GenericTracksSheet(
  tracks: ImmutableList<T>,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  lazyListState: LazyListState? = null,
  customMaxWidth: androidx.compose.ui.unit.Dp? = null,
  header: @Composable () -> Unit = {},
  track: @Composable (T) -> Unit = {},
  footer: @Composable () -> Unit = {},
  key: ((index: Int, item: T) -> Any)? = null,
  trackIndexed: (@Composable (index: Int, item: T) -> Unit)? = null,
) {
  val listState = lazyListState ?: rememberLazyListState()

  PlayerSheet(onDismissRequest, customMaxWidth = customMaxWidth) {
    Column(modifier) {
      header()
      LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        itemsIndexed(items = tracks, key = key) { index, item ->
          if (trackIndexed != null) trackIndexed(index, item) else track(item)
        }
        item {
          footer()
        }
      }
    }
  }
}

@Composable
fun AddTrackRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  androidx.compose.material3.Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      Icon(
        Icons.Default.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        modifier = Modifier.weight(1f),
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        actions()
      }
    }
  }
}

/**
 * Get a displayable title for a track node.
 * Uses title, language, or a default substitute.
 */
@Composable
fun getTrackTitle(
  track: TrackNode,
): String {
  // Handle external subtitles
  if (track.isSubtitle && track.external == true && track.externalFilename != null) {
    val decoded = Uri.decode(track.externalFilename)
    val fileName = decoded.substringAfterLast("/")
    return stringResource(R.string.player_sheets_track_title_wo_lang, track.id, fileName)
  }

  // Build title from available metadata
  val hasTitle = !track.title.isNullOrBlank()
  val hasLang = !track.lang.isNullOrBlank()

  return when {
    hasTitle && hasLang ->
      stringResource(
        R.string.player_sheets_track_title_w_lang,
        track.id,
        track.title,
        track.lang,
      )
    hasTitle -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, track.title)
    hasLang -> stringResource(R.string.player_sheets_track_lang_wo_title, track.id, track.lang)
    track.isSubtitle -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
    track.isAudio -> stringResource(R.string.player_sheets_chapter_title_substitute_audio, track.id)
    else -> ""
  }
}


