package xyz.mpv.rex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val videoZoom: Float = 0f,
  val sid: Int,
  val secondarySid: Int = -1, // Secondary subtitle track ID (-1 means disabled)
  val subDelay: Int,
  val subSpeed: Double,
  val aid: Int,
  val audioDelay: Int,
  val timeRemaining: Int = 0, // in seconds (duration - lastPosition)
  val savedOrientation: Int? = null, // Persisted orientation for Smart mode
  val externalSubtitles: String = "", // Pipe-separated list of external subtitle URIs
  val externalAudioTracks: String = "", // Pipe-separated list of external audio URIs
  val hasBeenWatched: Boolean = false, // Persistent flag: true if video has ever reached the watched threshold
  val videoAspect: String? = null, // Persisted aspect ratio mode name (e.g. Fit, Crop, Stretch)
  val customAspectRatio: Double = -1.0, // Persisted custom aspect ratio value
)
