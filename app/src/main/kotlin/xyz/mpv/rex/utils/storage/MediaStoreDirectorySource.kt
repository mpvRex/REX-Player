package xyz.mpv.rex.utils.storage

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * MediaStore access for a single directory.
 *
 * The tree browser renders one listing at a time, so these queries are scoped to that listing
 * instead of walking the whole library. Both forms are narrow: the `RELATIVE_PATH`/bucket id
 * comparison hits an index, and the `DATA LIKE` arm catches volumes where the relative path is
 * not available.
 */
internal object MediaStoreDirectorySource {

    private const val TAG = "MediaStoreDirSource"

    /**
     * Reads the media directly inside [directory].
     *
     * @return the rows, or `null` if the provider could not be queried at all (in which case the
     * caller must fall back to the filesystem rather than treat the folder as empty).
     */
    suspend fun read(
        context: Context,
        directory: File,
    ): List<ScannedItem>? = withContext(Dispatchers.IO) {
        val folderPath = normalizedPath(directory)
        // A null collection URI means the provider is unavailable in this environment; report
        // that rather than pretending the directory is empty.
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI ?: return@withContext null
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val videos = queryCollection(
            context = context,
            uri = videoUri,
            folderPath = folderPath,
            audio = false,
        )
        val audio = audioUri?.let {
            queryCollection(context = context, uri = it, folderPath = folderPath, audio = true)
        }
        if (videos == null && audio == null) return@withContext null
        (videos.orEmpty() + audio.orEmpty())
    }

    private suspend fun queryCollection(
        context: Context,
        uri: android.net.Uri,
        folderPath: String,
        audio: Boolean,
    ): List<ScannedItem>? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        // DATA LIKE is the portable arm; BUCKET_ID matches where the provider fills it in.
        val bucketId = folderPath.hashCode().toString()
        val selection = "(${MediaStore.MediaColumns.BUCKET_ID} = ? OR ${MediaStore.MediaColumns.DATA} LIKE ? ESCAPE '\\')"
        val likeArgument = folderPath
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") + "/%"
        val args = arrayOf(bucketId, likeArgument)

        try {
            val rows = ArrayList<ScannedItem>()
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val path = cursor.getString(dataIdx) ?: continue
                    val file = File(path)
                    // A LIKE on the prefix also matches deeper descendants; keep direct children.
                    if (file.parentFile?.let(::normalizedPath) != folderPath) continue
                    if (!file.exists()) continue
                    rows += ScannedItem(
                        name = cursor.getString(nameIdx) ?: file.name,
                        path = path,
                        size = cursor.getLong(sizeIdx),
                        duration = cursor.getLong(durationIdx),
                        dateModified = cursor.getLong(dateIdx),
                        isAudio = audio,
                    )
                }
            }
            return rows
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed for $folderPath", e)
            return null
        }
    }

}
