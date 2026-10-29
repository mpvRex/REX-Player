package xyz.mpv.rex.ui.player.managers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.BookmarkEntity
import xyz.mpv.rex.domain.bookmark.repository.BookmarkRepository
import xyz.mpv.rex.ui.player.PlayerUpdates
import `is`.xyz.mpv.MPVLib

/**
 * Owns manual (user-created) bookmarks for the currently playing media.
 *
 * Bookmarks are keyed by the player's stable media identifier rather than the display title,
 * so renamed files keep their pins. Embedded chapters are deliberately not handled here: they
 * are read-only and owned by mpv's demuxer.
 */
class BookmarkManager(
  private val repository: BookmarkRepository,
  private val context: Context,
  private val scope: CoroutineScope,
  private val onShowText: (String) -> Unit,
) {
  companion object {
    /** Minimum gap between two distinct bookmarks, so double presses do not stack pins. */
    private const val MIN_GAP_MS = 1000L
  }

  private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
  val bookmarks: Flow<List<BookmarkEntity>> = _bookmarks

  private var followJob: kotlinx.coroutines.Job? = null

  /**
   * Starts mirroring bookmarks for the media currently playing. Calling again (e.g. when the
   * activity switches tracks) replaces the previous subscription.
   */
  fun follow(
    mediaIdentifier: Flow<String>,
    mediaTitle: Flow<String>,
  ) {
    followJob?.cancel()
    followJob = scope.launch {
      combine(mediaIdentifier, mediaTitle) { identifier, title ->
        identifier.ifBlank { title }
      }
        // Both sources start blank and fill in once the media is set up. Reacting to the blank
        // interim value would briefly publish an empty list for the real media, so wait for a key.
        .distinctUntilChanged()
        .collectLatest { mediaKey ->
          if (mediaKey.isBlank()) return@collectLatest
          repository.observeByMediaKey(mediaKey).collect { _bookmarks.value = it }
        }
    }
  }

  fun currentPosition(): Long? =
    runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()?.times(1000)?.toLong()

  private fun currentDurationMs(): Long =
    (runCatching { MPVLib.getPropertyDouble("duration") }.getOrNull() ?: 0.0).times(1000).toLong()

  /**
   * Pins a bookmark at [positionMs] (defaults to the live playback position).
   * Ignores positions within [MIN_GAP_MS] of an existing bookmark on the same media.
   */
  fun add(
    mediaKey: String,
    positionMs: Long? = null,
  ) {
    if (mediaKey.isBlank()) return
    // No playhead yet (media still loading): say so instead of failing silently, which looks
    // exactly like a bookmark that was dropped.
    val rawPosition = positionMs ?: currentPosition()
    if (rawPosition == null) {
      onShowText(context.getString(R.string.bookmark_needs_media))
      return
    }
    scope.launch(Dispatchers.IO) {
      val duration = currentDurationMs()
      val position = rawPosition.coerceIn(0L, if (duration > 0) duration else rawPosition)
      val existing = repository.getByMediaKey(mediaKey)
      if (existing.any { kotlin.math.abs(it.positionMs - position) < MIN_GAP_MS }) {
        withContext(Dispatchers.Main) { onShowText(context.getString(R.string.bookmark_exists)) }
        return@launch
      }
      val defaultTitle = context.getString(
        R.string.bookmark_default_title,
        `is`.xyz.mpv.Utils.prettyTime((position / 1000).toInt()),
      )
      val newId = repository.add(mediaKey, position, defaultTitle)
      withContext(Dispatchers.Main) {
        onShowText(
          context.getString(if (newId > 0L) R.string.bookmark_added else R.string.bookmark_exists),
        )
      }
    }
  }

  fun rename(
    id: Long,
    title: String,
  ) {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return
    scope.launch(Dispatchers.IO) {
      repository.rename(id, trimmed)
      withContext(Dispatchers.Main) { onShowText(context.getString(R.string.bookmark_saved)) }
    }
  }

  fun delete(id: Long) {
    scope.launch(Dispatchers.IO) {
      repository.delete(id)
      withContext(Dispatchers.Main) { onShowText(context.getString(R.string.bookmark_deleted)) }
    }
  }

  fun cancel() {
    followJob?.cancel()
    followJob = null
  }
}
