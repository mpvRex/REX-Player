package xyz.mpv.rex.utils.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.domain.network.NetworkFile
import java.io.File
import java.util.Locale

/**
 * Category classifications for items in network browsing.
 */
enum class NetworkItemCategory {
  FOLDER,
  VIDEO,
  AUDIO,
  SUBTITLE,
  DOCUMENT,
  ARCHIVE,
  GENERIC_FILE;

  val isPlayable: Boolean
    get() = this == VIDEO || this == AUDIO

  val isNavigable: Boolean
    get() = this == FOLDER
}

/**
 * Helper to discriminate network files into playable media, folders, subtitles, documents, archives, or generic files.
 */
object NetworkFileTypeHelper {
  private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz")
  private val DOCUMENT_EXTENSIONS = setOf("txt", "pdf", "nfo", "doc", "docx", "md", "log", "xml", "json", "rtf")

  /**
   * Resolves the high-level [NetworkItemCategory] for a given [NetworkFile].
   */
  fun resolveCategory(file: NetworkFile): NetworkItemCategory {
    if (file.isDirectory) return NetworkItemCategory.FOLDER
    val javaFile = File(file.name)
    val ext = javaFile.extension.lowercase(Locale.ROOT)

    return when {
      FileTypeUtils.isVideoFile(javaFile) -> NetworkItemCategory.VIDEO
      FileTypeUtils.isAudioFile(javaFile) -> NetworkItemCategory.AUDIO
      FileTypeUtils.isSubtitleFile(javaFile) -> NetworkItemCategory.SUBTITLE
      ARCHIVE_EXTENSIONS.contains(ext) -> NetworkItemCategory.ARCHIVE
      DOCUMENT_EXTENSIONS.contains(ext) -> NetworkItemCategory.DOCUMENT
      else -> NetworkItemCategory.GENERIC_FILE
    }
  }

  /**
   * Returns the Material [ImageVector] icon corresponding to a [NetworkItemCategory].
   */
  fun iconFor(category: NetworkItemCategory): ImageVector = when (category) {
    NetworkItemCategory.FOLDER -> Icons.Filled.Folder
    NetworkItemCategory.VIDEO -> Icons.Filled.PlayArrow
    NetworkItemCategory.AUDIO -> Icons.Filled.AudioFile
    NetworkItemCategory.SUBTITLE -> Icons.Filled.Subtitles
    NetworkItemCategory.DOCUMENT -> Icons.Filled.Description
    NetworkItemCategory.ARCHIVE -> Icons.Filled.Archive
    NetworkItemCategory.GENERIC_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
  }

  /**
   * Formats a short badge label for display in card metadata chips or details.
   */
  fun badgeTextFor(file: NetworkFile, category: NetworkItemCategory): String {
    val ext = File(file.name).extension.uppercase(Locale.ROOT)
    return when (category) {
      NetworkItemCategory.FOLDER -> "DIR"
      NetworkItemCategory.VIDEO -> ext.ifEmpty { "VIDEO" }
      NetworkItemCategory.AUDIO -> ext.ifEmpty { "AUDIO" }
      NetworkItemCategory.SUBTITLE -> ext.ifEmpty { "SUB" }
      NetworkItemCategory.DOCUMENT -> ext.ifEmpty { "DOC" }
      NetworkItemCategory.ARCHIVE -> ext.ifEmpty { "ZIP" }
      NetworkItemCategory.GENERIC_FILE -> ext.ifEmpty { "FILE" }
    }
  }
}
