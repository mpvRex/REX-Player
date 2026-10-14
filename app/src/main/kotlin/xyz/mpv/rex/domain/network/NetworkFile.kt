package xyz.mpv.rex.domain.network

/**
 * Represents a file or directory on a network share.
 *
 * @property name File or directory name.
 * @property path Full path on the remote share.
 * @property size Size in bytes (0 for directories or unknown size).
 * @property isDirectory True if this entry is a directory.
 * @property lastModified Last modification timestamp in milliseconds since epoch (0 if unknown).
 * @property mimeType MIME type of the file, or null if unknown or directory.
 * @property isHidden True if this entry is a dotfile (starts with ".") or has the SMB hidden/system attribute.
 */
data class NetworkFile(
  val name: String,
  val path: String,
  val size: Long,
  val isDirectory: Boolean,
  val lastModified: Long = 0,
  val mimeType: String? = null,
  val isHidden: Boolean = false,
)
