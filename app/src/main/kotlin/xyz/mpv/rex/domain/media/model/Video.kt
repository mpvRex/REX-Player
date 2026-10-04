package xyz.mpv.rex.domain.media.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Video(
  val id: Long,
  val title: String,
  val displayName: String,
  val path: String,
  val uri: Uri,
  val duration: Long,
  val durationFormatted: String,
  val size: Long,
  val sizeFormatted: String,
  val dateModified: Long,
  val dateAdded: Long,
  val mimeType: String,
  val bucketId: String,
  val bucketDisplayName: String,
  val width: Int,
  val height: Int,
  val rotation: Int = 0,
  val fps: Float,
  val resolution: String,
  val hasEmbeddedSubtitles: Boolean = false,
  val subtitleCodec: String = "",
  val matchingExternalSubtitleFormats: List<String> = emptyList(),
  /**
   * True when the subtitle metadata needed by the browser indicator has been resolved.
   * This covers both embedded-subtitle metadata and same-folder external-subtitle discovery.
   */
  val subtitleIndicatorReady: Boolean = false,
  val isAudio: Boolean = false,
  val artist: String = "",
  val album: String = "",
  val savedOrientation: Int? = null,
)
