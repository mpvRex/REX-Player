package xyz.mpv.rex.domain.media.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoFolder(
  val bucketId: String,
  val name: String,
  val path: String,
  val videoCount: Int,
  val audioCount: Int = 0,
  val totalSize: Long = 0L,
  val totalDuration: Long = 0L, // in milliseconds
  val videoSize: Long = 0L,
  val audioSize: Long = 0L,
  val videoDuration: Long = 0L,
  val audioDuration: Long = 0L,
  val lastModified: Long = 0L,
  val newCount: Int = 0,
  val unwatchedVideoCount: Int = 0,
) {
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
