package xyz.mpv.rex.ui.browser.filesystem

import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.media.PlaybackStateIndex

/**
 * Watched / progress / new badges for one directory listing.
 *
 * These depend only on playback history and file timestamps, never on extracted metadata, so
 * they are computed exactly once per load. That is what lets a load publish a single list
 * instead of publishing once and then silently replacing it.
 */
internal class FolderBadges {
  val progress: MutableMap<Long, Float> = mutableMapOf()
  val newIds: MutableSet<Long> = mutableSetOf()
  val watchedIds: MutableSet<Long> = mutableSetOf()

  /** Returns [items] with playback-derived fields applied and [progress]/[newIds]/[watchedIds] filled. */
  fun decorate(
    items: List<FileSystemItem>,
    playbackIndex: PlaybackStateIndex,
    now: Long,
    thresholdMillis: Long,
    watchedThreshold: Int,
  ): List<FileSystemItem> = items.map { item ->
    if (item !is FileSystemItem.VideoFile) return@map item
    val original = item.video
    val state = playbackIndex.forVideoPathOrName(original.path, original.displayName)

    var video: Video = original
    if (state != null) {
      if (state.savedOrientation != null) {
        video = video.copy(savedOrientation = state.savedOrientation)
      }

      var isWatched = state.hasBeenWatched
      if (video.duration > 0 && state.timeRemaining != -1) {
        val durationSeconds = video.duration / 1000
        val watched = durationSeconds - state.timeRemaining.toLong()
        val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        if (progressValue >= (watchedThreshold / 100f)) {
          isWatched = true
        }
        if (progressValue >= 0.01f && !isWatched) {
          this.progress[video.id] = progressValue
        }
      }
      if (isWatched) watchedIds.add(video.id)
      if (state.timeRemaining == -1) newIds.add(video.id)
    } else {
      val videoAge = now - (video.dateModified * 1000)
      if (videoAge <= thresholdMillis) newIds.add(video.id)
    }

    item.copy(video = video)
  }
}
