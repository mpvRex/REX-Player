package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList

internal fun chapterKey(index: Int, chapter: Segment): Any = "${index}_${chapter.start}"

internal fun resolveActiveChapterIndex(
  chapters: List<Segment>,
  currentChapter: Segment?,
  currentChapterIndex: Int?,
): Int = currentChapterIndex?.takeIf { it in chapters.indices }
  ?: currentChapter?.let { chapters.indexOf(it) }
  ?: -1

@Composable
fun ChaptersSheet(
  chapters: ImmutableList<Segment>,
  currentChapter: Segment?,
  onClick: (Segment) -> Unit = {},
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  currentChapterIndex: Int? = null,
  onChapterClick: ((Int) -> Unit)? = null,
) {
  val listState = rememberLazyListState()
  val hasScrolled = remember { mutableStateOf(false) }

  val activeChapterIndex = remember(currentChapterIndex, currentChapter, chapters) {
    resolveActiveChapterIndex(chapters, currentChapter, currentChapterIndex)
  }

  LaunchedEffect(activeChapterIndex, chapters.size) {
    if (!hasScrolled.value && activeChapterIndex in chapters.indices) {
      listState.scrollToItem(activeChapterIndex)
      hasScrolled.value = true
    }
  }

  GenericTracksSheet(
    tracks = chapters,
    lazyListState = listState,
    key = ::chapterKey,
    trackIndexed = { index, chapter ->
      ChapterTrack(
        chapter = chapter,
        index = index,
        selected = index == activeChapterIndex,
        onClick = {
          if (onChapterClick != null) {
            onChapterClick(index)
          } else {
            onClick(chapter)
          }
        },
      )
    },
    onDismissRequest = onDismissRequest,
    modifier =
      modifier
        .padding(vertical = MaterialTheme.spacing.medium),
  )
}

@Composable
fun ChapterTrack(
  chapter: Segment,
  index: Int,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val containerColor = if (selected) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }
  val borderColor = if (selected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
  } else Color.Transparent

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    border = if (selected) BorderStroke(1.dp, borderColor) else null,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(28.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxSize(),
        ) {
          Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = chapter.name.ifBlank { stringResource(R.string.player_sheets_track_title_wo_lang, index + 1, "") },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.weight(1f),
        overflow = TextOverflow.Ellipsis,
      )

      Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
      ) {
        Text(
          text = Utils.prettyTime(chapter.start.toInt()),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
  }
}

