package xyz.mpv.rex.domain.browser

import xyz.mpv.rex.domain.media.model.Video

/**
 * Represents an item in the filesystem browser (either a folder or a video file)
 */
sealed class FileSystemItem {
  abstract val name: String
  abstract val path: String
  abstract val lastModified: Long

  data class Folder(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val videoCount: Int,
    val audioCount: Int = 0,
    val totalSize: Long = 0L,
    val totalDuration: Long = 0L,
    val videoSize: Long = 0L,
    val audioSize: Long = 0L,
    val videoDuration: Long = 0L,
    val audioDuration: Long = 0L,
    val hasSubfolders: Boolean = false,
    val newCount: Int = 0,
    val unwatchedVideoCount: Int = 0,
  ) : FileSystemItem() {
    fun activeSize(showAudioFiles: Boolean): Long =
      if (showAudioFiles) {
        totalSize
      } else if (videoSize == 0L && audioSize == 0L && totalSize > 0L) {
        if (videoCount > 0) totalSize else 0L
      } else {
        videoSize
      }

    fun activeDuration(showAudioFiles: Boolean): Long =
      if (showAudioFiles) {
        totalDuration
      } else if (videoDuration == 0L && audioDuration == 0L && totalDuration > 0L) {
        if (videoCount > 0) totalDuration else 0L
      } else {
        videoDuration
      }

    fun activeCount(showAudioFiles: Boolean): Int =
      if (showAudioFiles) videoCount + audioCount else videoCount
  }


  data class VideoFile(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val video: Video,
  ) : FileSystemItem()
}

/**
 * Represents a path component in the breadcrumb navigation
 */
data class PathComponent(
  val name: String,
  val fullPath: String,
)
