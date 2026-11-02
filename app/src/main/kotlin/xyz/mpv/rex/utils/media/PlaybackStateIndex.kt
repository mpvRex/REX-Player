package xyz.mpv.rex.utils.media

import java.io.File
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.media.model.Video

/**
 * Lookup index over playback states.
 *
 * Playback history is stored under either a full path or a bare file name, and browsers used to
 * resolve it with a linear `playbackStates.find { ... }` for every item. That is
 * O(items x history) and it grows with the whole library, which made large folders take seconds
 * to appear. This index answers the same lookups in O(1).
 */
class PlaybackStateIndex private constructor(
  private val byTitle: Map<String, PlaybackStateEntity>,
  private val byFileName: Map<String, PlaybackStateEntity>,
) {
  /** Resolves by an exact stored key (path or name). */
  fun forKey(key: String?): PlaybackStateEntity? {
    if (key.isNullOrEmpty()) return null
    return byTitle[key]
  }

  /** Resolves by a full path, falling back to its file name as history may store either form. */
  fun forPath(path: String?): PlaybackStateEntity? {
    if (path.isNullOrEmpty()) return null
    byTitle[path]?.let { return it }
    return forName(fileNameOf(path))
  }

  /** Resolves by a file name, also matching rows that recorded the full path. */
  fun forName(name: String?): PlaybackStateEntity? {
    if (name.isNullOrEmpty()) return null
    return byTitle[name] ?: byFileName[name]
  }

  /** Resolves by a path, falling back to [displayName] when it differs from the path's file name. */
  fun forVideoPathOrName(path: String?, displayName: String?): PlaybackStateEntity? {
    forPath(path)?.let { return it }
    if (displayName.isNullOrEmpty()) return null
    // forPath already tried the path's own file name, so only a distinct name is left to try.
    return if (displayName == fileNameOf(path)) null else forName(displayName)
  }

  /** Convenience overload matching the historical `mediaTitle == path || mediaTitle == displayName`. */
  fun forVideo(video: Video): PlaybackStateEntity? =
    forVideoPathOrName(video.path, video.displayName)

  private fun fileNameOf(path: String?): String {
    if (path.isNullOrEmpty()) return ""
    return path.substringAfterLast('/').ifEmpty { path }
  }

  companion object {
    private val EMPTY = PlaybackStateIndex(emptyMap(), emptyMap())

    fun empty(): PlaybackStateIndex = EMPTY

    fun of(states: List<PlaybackStateEntity>): PlaybackStateIndex {
      if (states.isEmpty()) return EMPTY
      val byTitle = HashMap<String, PlaybackStateEntity>(states.size * 2)
      for (state in states) {
        byTitle[state.mediaTitle] = state
      }
      // Second pass so bare-file-name lookups never shadow a genuine key recorded by another row.
      // History may store either a full path or just the file name, so both must resolve.
      val byFileName = HashMap<String, PlaybackStateEntity>()
      for (state in states) {
        val name = state.mediaTitle.substringAfterLast('/')
        if (name.isNotEmpty() && name != state.mediaTitle) {
          byFileName.putIfAbsent(name, state)
        }
      }
      return PlaybackStateIndex(byTitle, byFileName)
    }
  }
}
