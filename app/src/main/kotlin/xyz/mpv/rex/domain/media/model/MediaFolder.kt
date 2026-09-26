package xyz.mpv.rex.domain.media.model

import androidx.compose.runtime.Immutable

/**
 * Unified model for a media folder used across all views (Folder View, Tree View, etc.)
 * 
 * @param id Unique identifier (usually the absolute path)
 * @param name Display name of the folder
 * @param path Absolute path on the filesystem
 * @param videoCount Number of videos in this folder (or recursively if [isRecursive] is true)
 * @param audioCount Number of audio files in this folder (or recursively if [isRecursive] is true)
 * @param totalSize Combined size of all media in bytes
 * @param totalDuration Combined duration of all media in milliseconds
 * @param lastModified Latest modification timestamp
 * @param hasSubfolders True if this folder contains subdirectories
 * @param isRecursive True if the counts and totals include descendants (used for Tree View roots)
 */
@Immutable
data class MediaFolder(
  val id: String,
  val name: String,
  val path: String,
  val videoCount: Int,
  val audioCount: Int = 0,
  val totalSize: Long = 0L,
  val totalDuration: Long = 0L,
  val videoSize: Long = 0L,
  val audioSize: Long = 0L,
  val videoDuration: Long = 0L,
  val audioDuration: Long = 0L,
  val lastModified: Long = 0L,
  val hasSubfolders: Boolean = false,
  val isRecursive: Boolean = false,
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
