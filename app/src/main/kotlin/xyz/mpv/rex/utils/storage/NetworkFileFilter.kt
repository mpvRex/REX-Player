package xyz.mpv.rex.utils.storage

import xyz.mpv.rex.domain.network.NetworkFile

/**
 * Filter utility for remote [NetworkFile] items.
 * Evaluates dotfiles, SMB hidden attributes (0x02L), and system attributes (0x04L).
 */
object NetworkFileFilter {
  const val SMB_ATTR_HIDDEN = 0x02L
  const val SMB_ATTR_SYSTEM = 0x04L
  const val SMB_ATTR_DIRECTORY = 0x10L

  /**
   * Determines if a file is hidden based on its name and SMB attribute flags.
   */
  fun isHidden(name: String, fileAttributes: Long = 0L): Boolean {
    return name.startsWith(".") ||
      (fileAttributes and SMB_ATTR_HIDDEN != 0L) ||
      (fileAttributes and SMB_ATTR_SYSTEM != 0L)
  }

  /**
   * Determines if a [NetworkFile] is hidden.
   * Checks both the model's [NetworkFile.isHidden] flag and filename prefix for defense in depth.
   */
  fun isHidden(file: NetworkFile): Boolean {
    return file.isHidden || file.name.startsWith(".")
  }

  /**
   * Filters a list of [NetworkFile] items based on the user's [showHidden] preference.
   *
   * @param files The raw list of files and directories.
   * @param showHidden When false, excludes all dotfiles and SMB hidden/system files.
   *                   When true, returns all items unchanged.
   */
  fun filter(files: List<NetworkFile>, showHidden: Boolean): List<NetworkFile> {
    if (showHidden) return files
    return files.filterNot { isHidden(it) }
  }

}
