package xyz.mpv.rex.utils.storage

import android.util.Log
import java.io.File

/**
 * File Filter Utilities
 * Handles file and folder filtering logic
 */
object FileFilterUtils {
    private const val TAG = "FileFilterUtils"

    // Folders to skip during scanning (system/cache folders)
    private val SKIP_FOLDERS = setOf(
        // System & OS Junk
        "android", "data", "obb", "system", "lost.dir", ".android_secure", "android_secure",

        // Hidden & Temp Files
        ".thumbnails", "thumbnails", "thumbs", ".thumbs",
        ".cache", "cache", "temp", "tmp", ".temp", ".tmp",

        // Trash & Recycle Bins
        ".trash", "trash", ".trashbin", ".trashed", "recycle", "recycler",

        // App Clutters
        "log", "logs", "backup", "backups",
        "stickers", "whatsapp stickers", "telegram stickers"
    )

    /**
     * Checks if a folder contains a .nomedia file
     */
    fun hasNoMediaFile(folder: File): Boolean {
        if (!folder.isDirectory || !folder.canRead()) {
            return false
        }

        return try {
            val noMediaFile = File(folder, ".nomedia")
            noMediaFile.exists()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for .nomedia file in: ${folder.absolutePath}", e)
            false
        }
    }

    /**
     * Checks if a folder should be skipped during scanning
     */
    fun shouldSkipFolder(
        folder: File,
        policy: MediaScanPolicy = MediaScanPolicy(),
    ): Boolean {
        if (!policy.includeNoMediaContent && hasNoMediaFile(folder)) {
            return true
        }

        return shouldSkipFolderName(folder.name, policy)
    }

    fun shouldSkipFolderName(name: String, policy: MediaScanPolicy = MediaScanPolicy()): Boolean {
        val normalizedName = name.lowercase()
        return (!policy.showHiddenFiles && normalizedName.startsWith(".")) || SKIP_FOLDERS.contains(normalizedName)
    }

    /**
     * Checks if a file should be skipped during file listing
     */
    fun shouldSkipFile(file: File, policy: MediaScanPolicy = MediaScanPolicy()): Boolean {
        return !policy.showHiddenFiles && file.name.startsWith(".")
    }

    fun isHiddenPath(path: String): Boolean {
        val normalized = selectedFolderPath(path)
        val components = if (normalized.startsWith("content://")) {
            normalized.substringAfter("/document/").substringAfter(':')
        } else normalized
        return components.split('/').any { it.startsWith(".") && it != "." && it != ".." }
    }

    /**
     * Returns true when [folder] is at or below a readable .nomedia boundary.
     */
    fun isWithinNoMediaBoundary(folder: File): Boolean {
        var current: File? = folder
        while (current != null) {
            if (hasNoMediaFile(current)) return true
            current = current.parentFile
        }
        return false
    }
}
