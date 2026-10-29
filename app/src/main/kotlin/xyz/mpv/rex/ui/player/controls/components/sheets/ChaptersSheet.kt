package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.BookmarkEntity
import xyz.mpv.rex.ui.theme.spacing
import `is`.xyz.mpv.Utils

internal fun chapterKey(index: Int, chapter: Segment): Any = "${index}_${chapter.start}"

internal fun resolveActiveChapterIndex(
  chapters: List<Segment>,
  currentChapter: Segment?,
  currentChapterIndex: Int?,
): Int = currentChapterIndex?.takeIf { it in chapters.indices }
  ?: currentChapter?.let { chapters.indexOf(it) }
  ?: -1

internal fun bookmarkKey(bookmark: BookmarkEntity): Any = "bookmark_${bookmark.id}"

/** Converts a stored bookmark into a seekable seekbar segment. */
internal fun BookmarkEntity.toSegment(): Segment =
  Segment(name = title, start = positionMs / 1000f)

/**
 * A single entry in the merged chapters/bookmarks list.
 * Embedded chapters keep their original mpv index so they can still be sought by index.
 */
sealed interface ChapterSheetItem {
  val startSeconds: Float

  data class BookmarkItem(val bookmark: BookmarkEntity) : ChapterSheetItem {
    override val startSeconds: Float get() = bookmark.positionMs / 1000f
  }

  data class ChapterItem(val index: Int, val chapter: Segment) : ChapterSheetItem {
    override val startSeconds: Float get() = chapter.start
  }
}

/**
 * Interleaves embedded chapters and manual bookmarks by time, so a bookmark at 12:00 sits
 * between chapters at 2:00 and 18:00. Ties place the bookmark first (it is the more specific pin).
 */
internal fun mergeChapterSheetItems(
  chapters: List<Segment>,
  bookmarks: List<BookmarkEntity>,
): List<ChapterSheetItem> {
  val items = ArrayList<ChapterSheetItem>(chapters.size + bookmarks.size)
  bookmarks.sortedBy { it.positionMs }.forEach { items.add(ChapterSheetItem.BookmarkItem(it)) }
  chapters.forEachIndexed { index, chapter -> items.add(ChapterSheetItem.ChapterItem(index, chapter)) }
  return items.sortedWith(
    compareBy<ChapterSheetItem> { it.startSeconds }.thenByDescending {
      it is ChapterSheetItem.BookmarkItem
    },
  )
}

internal fun ChapterSheetItem.key(): Any = when (this) {
  is ChapterSheetItem.BookmarkItem -> bookmarkKey(bookmark)
  is ChapterSheetItem.ChapterItem -> chapterKey(index, chapter)
}

/**
 * The entry the playhead is currently inside.
 *
 * Mirrors chapter semantics: a stop stays "active" from its timestamp until the next stop's
 * timestamp. Because bookmarks and chapters are merged into one sorted timeline, seeking to the
 * 12:00 bookmark keeps that bookmark active until the next stop (e.g. an 18:00 chapter), even
 * though a 02:00 chapter technically also covers that position.
 *
 * Manual bookmarks win ties at the same timestamp, since they are the more specific pin.
 */
internal fun activeChapterSheetItem(
  mergedItems: List<ChapterSheetItem>,
  currentSeconds: Float?,
  activeChapterIndex: Int?,
  toleranceSeconds: Float = 0.5f,
): ChapterSheetItem? {
  if (mergedItems.isEmpty()) return null
  if (currentSeconds != null) {
    // Last stop at or before the playhead; any position before the first stop uses that first stop.
    return mergedItems.lastOrNull { it.startSeconds <= currentSeconds + toleranceSeconds }
      ?: mergedItems.first()
  }
  // Playhead unknown: fall back to whichever chapter mpv reports as active.
  return mergedItems.filterIsInstance<ChapterSheetItem.ChapterItem>()
    .lastOrNull { it.index == activeChapterIndex }
    ?: mergedItems.firstOrNull()
}

/**
 * The next stop after [currentSeconds] across chapters *and* bookmarks, so double-tap chapter
 * skip also lands on user-pinned bookmarks interleaved between chapters.
 *
 * [mergedItems] is expected to be sorted (as produced by [mergeChapterSheetItems]). Entries at or
 * before the current position are ignored, so a stop the user already passed is never re-seeked.
 */

internal fun nextChapterSheetItem(
  mergedItems: List<ChapterSheetItem>,
  currentSeconds: Float,
  toleranceSeconds: Float = 0.5f,
): ChapterSheetItem? = mergedItems.firstOrNull { it.startSeconds > currentSeconds + toleranceSeconds }

/**
 * Chapters sheet showing embedded chapters and manual bookmarks as one chronological list.
 *
 * They are merged for display only: embedded chapters are read-only and owned by mpv, while
 * bookmarks are editable and persisted locally. Chapter rows keep their original index so the
 * `chapter` property seek still works.
 */
@Composable
fun ChaptersSheet(
  chapters: ImmutableList<Segment>,
  currentChapter: Segment?,
  onClick: (Segment) -> Unit = {},
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  currentChapterIndex: Int? = null,
  onChapterClick: ((Int) -> Unit)? = null,
  bookmarks: ImmutableList<BookmarkEntity> = persistentListOf(),
  currentPositionSeconds: Float? = null,
  activeStop: ChapterSheetItem? = null,
  onAddBookmark: () -> Unit = {},
  onSeekToBookmark: (BookmarkEntity) -> Unit = {},
  onRenameBookmark: (Long, String) -> Unit = { _, _ -> },
  onDeleteBookmark: (Long) -> Unit = {},
) {
  val activeChapterIndex = remember(currentChapterIndex, currentChapter, chapters) {
    resolveActiveChapterIndex(chapters, currentChapter, currentChapterIndex)
  }

  val items: ImmutableList<ChapterSheetItem> = remember(bookmarks, chapters) {
    mergeChapterSheetItems(chapters, bookmarks).toImmutableList()
  }

  // Follow the playhead: a bookmarked position highlights the bookmark, not its chapter.
  // Passed in from the view model so a tapped row highlights immediately, while the seek settles.
  val activeItem = activeStop ?: remember(items, activeChapterIndex, currentPositionSeconds) {
    activeChapterSheetItem(
      mergedItems = items,
      currentSeconds = currentPositionSeconds,
      activeChapterIndex = activeChapterIndex,
    )
  }

  // Start the list scrolled to whatever is currently playing.
  val initialIndex = remember(items, activeItem) {
    items.indexOfFirst { it.key() == activeItem?.key() }
  }
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
  val hasScrolled = remember { mutableStateOf(initialIndex >= 0) }

  LaunchedEffect(activeItem, items.size) {
    val target = items.indexOfFirst { it.key() == activeItem?.key() }
    if (!hasScrolled.value && target in items.indices) {
      listState.scrollToItem(target)
      hasScrolled.value = true
    }
  }

  var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

  GenericTracksSheet(
    tracks = items,
    lazyListState = listState,
    key = { _, item -> item.key() },
    header = {
      AddTrackRow(
        title = stringResource(R.string.bookmark_add),
        onClick = onAddBookmark,
        actions = {
          if (currentPositionSeconds != null) {
            Text(
              text = Utils.prettyTime(currentPositionSeconds.toInt()),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
      )
    },
    trackIndexed = { _, item ->
      when (item) {
        is ChapterSheetItem.BookmarkItem -> {
          val bookmark = item.bookmark
          ChapterTrack(
            name = bookmark.title,
            startSeconds = item.startSeconds,
            index = null,
            selected = item.key() == activeItem?.key(),
            onClick = { onSeekToBookmark(bookmark) },
            onLongClick = { editingBookmark = bookmark },
            badge = {
              Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = null,
                tint = if (item.key() == activeItem?.key()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
            },
          )
        }
        is ChapterSheetItem.ChapterItem -> {
          ChapterTrack(
            name = item.chapter.name,
            startSeconds = item.startSeconds,
            index = item.index,
            selected = item.key() == activeItem?.key(),
            onClick = {
              if (onChapterClick != null) {
                onChapterClick(item.index)
              } else {
                onClick(item.chapter)
              }
            },
          )
        }
      }
    },
    onDismissRequest = onDismissRequest,
    modifier =
      modifier
        .padding(vertical = MaterialTheme.spacing.medium),
  )

  editingBookmark?.let { bookmark ->
    BookmarkRenameDialog(
      initialTitle = bookmark.title,
      onConfirm = { newTitle ->
        onRenameBookmark(bookmark.id, newTitle)
        editingBookmark = null
      },
      onDismiss = { editingBookmark = null },
      onDelete = {
        onDeleteBookmark(bookmark.id)
        editingBookmark = null
      },
    )
  }
}

@Composable
private fun BookmarkRenameDialog(
  initialTitle: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  onDelete: () -> Unit,
) {
  var title by remember(initialTitle) { mutableStateOf(initialTitle) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.bookmark_rename_title)) },
    text = {
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(title) },
        enabled = title.isNotBlank(),
      ) {
        Text(stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      Row {
        TextButton(
          onClick = onDelete,
          colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(stringResource(R.string.bookmark_delete))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(android.R.string.cancel))
        }
      }
    },
  )
}

/**
 * The single row layout used by both chapters and bookmarks, so every entry has identical
 * height, padding and typography.
 *
 * [index] is the embedded chapter number (shown as a badge); pass `null` for bookmarks, which
 * show [badge] instead. Long-press (bookmarks only) opens rename/delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterTrack(
  name: String,
  startSeconds: Float,
  index: Int?,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  badge: (@Composable () -> Unit)? = null,
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
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
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
          if (index != null) {
            Text(
              text = "${index + 1}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            badge?.invoke()
          }
        }
      }

      Text(
        text = name.ifBlank {
          if (index != null) {
            stringResource(R.string.player_sheets_track_title_wo_lang, index + 1, "")
          } else {
            Utils.prettyTime(startSeconds.toInt())
          }
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.weight(1f),
        overflow = TextOverflow.Ellipsis,
      )

      if (onLongClick != null) {
        // Same optical weight as a chapter row: a chapter has nothing here, so keep this
        // glyph (and its touch target) small instead of IconButton's default 48dp box.
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onLongClick),
        ) {
          Icon(
            imageVector = Icons.Outlined.Bookmark,
            contentDescription = stringResource(R.string.bookmark_rename),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
      ) {
        Text(
          text = Utils.prettyTime(startSeconds.toInt()),
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
