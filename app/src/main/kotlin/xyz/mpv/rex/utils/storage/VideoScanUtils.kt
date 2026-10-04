package xyz.mpv.rex.utils.storage

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.media.MediaInfoOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Video Scanning Utilities
 * Handles single-folder video file scanning and metadata extraction.
 */
object VideoScanUtils {
    private const val TAG = "VideoScanUtils"
    
    /**
     * Video metadata extracted from files
     */
    data class VideoMetadata(
        val duration: Long,
        val mimeType: String,
        val width: Int = 0,
        val height: Int = 0,
        val rotation: Int = 0,
        val artist: String = "",
        val album: String = "",
    )
    
    /**
     * Folder scan result
     */
    data class FolderScanResult(
        val videos: List<Video>,
        val access: FolderAccess,
    )
    
    enum class FolderAccess {
        READABLE,
        INACCESSIBLE,
    }

    /**
     * Get a single Video object by its file path with exact MediaStore metadata.
     * Guaranteed to match the exact duration, size, and dateModified indexed in the library.
     */
    suspend fun getVideoByPath(context: Context, path: String): Video? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val file = File(path)
        if (!file.exists() || !file.isFile) return@withContext null

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        var video: Video? = null
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: file.name
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/*"
                    val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
                    val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))

                    video = Video(
                        id = id,
                        title = file.nameWithoutExtension,
                        displayName = name,
                        path = path,
                        uri = Uri.fromFile(file),
                        duration = duration,
                        durationFormatted = MediaFormatter.formatDuration(duration),
                        size = size,
                        sizeFormatted = MediaFormatter.formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        bucketId = file.parent ?: "",
                        bucketDisplayName = file.parentFile?.name ?: "",
                        width = width,
                        height = height,
                        fps = 0f,
                        resolution = MediaFormatter.formatResolution(width, height),
                        isAudio = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for video: $path", e)
        }

        // Fallback if not found in MediaStore
        if (video == null) {
            val isAudio = FileTypeUtils.isAudioFile(file)
            val size = file.length()
            val dateModified = file.lastModified() / 1000

            var duration = 0L
            var width = 0
            var height = 0
            var fps = 0f

            try {
                val koin = GlobalContext.getOrNull()
                val metadataCache = koin?.getOrNull<VideoMetadataCacheRepository>()
                val cached = metadataCache?.getOrExtractMetadata(file, Uri.fromFile(file), file.name)
                if (cached != null) {
                    duration = cached.durationMs
                    width = cached.width
                    height = cached.height
                    fps = cached.fps
                }
            } catch (e: Exception) {
                // Ignore
            }

            video = Video(
                id = path.hashCode().toLong(),
                title = file.nameWithoutExtension,
                displayName = file.name,
                path = path,
                uri = Uri.fromFile(file),
                duration = duration,
                durationFormatted = MediaFormatter.formatDuration(duration),
                size = size,
                sizeFormatted = MediaFormatter.formatFileSize(size),
                dateModified = dateModified,
                dateAdded = dateModified,
                mimeType = if (isAudio) "audio/*" else "video/*",
                bucketId = file.parent ?: "",
                bucketDisplayName = file.parentFile?.name ?: "",
                width = width,
                height = height,
                fps = fps,
                resolution = if (width > 0 && height > 0) MediaFormatter.formatResolution(width, height) else "",
                isAudio = isAudio
            )
        }

        video
    }

    /**
     * Retrieves the most recently added videos directly from MediaStore.
     */
    suspend fun getRecentlyAddedVideos(context: Context, limit: Int = 50): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val addCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext() && videos.size < limit) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (!file.exists() || !file.isFile) continue

                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: file.name
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)
                    val dateModified = cursor.getLong(modCol)
                    val dateAdded = cursor.getLong(addCol)
                    val mimeType = cursor.getString(mimeCol) ?: "video/*"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)

                    videos.add(
                        Video(
                            id = id,
                            title = file.nameWithoutExtension,
                            displayName = name,
                            path = path,
                            uri = Uri.fromFile(file),
                            duration = duration,
                            durationFormatted = MediaFormatter.formatDuration(duration),
                            size = size,
                            sizeFormatted = MediaFormatter.formatFileSize(size),
                            dateModified = dateModified,
                            dateAdded = dateAdded,
                            mimeType = mimeType,
                            bucketId = file.parent ?: "",
                            bucketDisplayName = file.parentFile?.name ?: "",
                            width = width,
                            height = height,
                            fps = 0f,
                            resolution = MediaFormatter.formatResolution(width, height),
                            isAudio = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying recently added videos from MediaStore", e)
        }
        videos
    }

    /**
     * Get all videos and audio in a specific folder.
     * MediaStore remains the fast source and direct storage reconciles it when allowed.
     */
    suspend fun getVideosInFolder(
        context: Context,
        folderPath: String,
        policy: MediaScanPolicy = MediaScanPolicy(),
    ): List<Video> = scanFolder(context, folderPath, policy).videos

    suspend fun scanFolder(
        context: Context,
        folderPath: String,
        policy: MediaScanPolicy = MediaScanPolicy(),
    ): FolderScanResult = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory || !folder.canRead()) {
            return@withContext FolderScanResult(emptyList(), FolderAccess.INACCESSIBLE)
        }

        if (!policy.includeNoMediaContent && FileFilterUtils.isWithinNoMediaBoundary(folder)) {
            return@withContext FolderScanResult(emptyList(), FolderAccess.READABLE)
        }

        val videosMap = mutableMapOf<String, Video>()
        val normalizedFolderPath = normalizePath(folder)
        val directChildren = folder.listFiles()

        scanVideosFromMediaStore(context, normalizedFolderPath, videosMap)
        scanAudioFromMediaStore(context, normalizedFolderPath, videosMap)

        // Reconcile with direct filesystem to pick up newly added or unindexed files
        // (e.g. fresh torrent downloads from Flud, browser downloads, or external transfers).
        val fsSuccess = scanMediaFromFileSystem(folder, videosMap, directChildren)
        if (!fsSuccess && videosMap.isEmpty()) {
            return@withContext FolderScanResult(emptyList(), FolderAccess.INACCESSIBLE)
        }

        // Fast DB cache enrichment for any files that don't have duration yet
        // Populate matching same-folder external subtitle indicators in one directory scan.
        // Matching is exact on the video base filename (case-insensitive), not prefix-based.
        // This is derived metadata only and is never persisted.
        val matchingSubtitleFormatsByBaseName = buildMatchingExternalSubtitleFormats(
            directChildren?.asIterable() ?: emptyList()
        )
        videosMap.replaceAll { _, video ->
            val baseName = File(video.path).nameWithoutExtension.trim().lowercase(Locale.ROOT)
            val formats = matchingSubtitleFormatsByBaseName[baseName].orEmpty()
            if (formats.isEmpty()) video else video.copy(matchingExternalSubtitleFormats = formats)
        }

        val uncachedDurationPaths = videosMap.values.filter { it.duration <= 0L }.map { it.path }
        if (uncachedDurationPaths.isNotEmpty()) {
            val metadataCache = runCatching {
                GlobalContext.get().get<VideoMetadataCacheRepository>()
            }.getOrNull()
            if (metadataCache != null) {
                val cached = metadataCache.getCachedMetadataBatch(uncachedDurationPaths)
                for ((path, meta) in cached) {
                    val v = videosMap[path]
                    if (v != null && meta.durationMs > 0) {
                        videosMap[path] = v.copy(
                            duration = meta.durationMs,
                            durationFormatted = MediaFormatter.formatDuration(meta.durationMs),
                            width = if (meta.width > 0) meta.width else v.width,
                            height = if (meta.height > 0) meta.height else v.height,
                            rotation = if (meta.rotation != 0) meta.rotation else v.rotation,
                            fps = meta.fps,
                            resolution = MediaFormatter.formatResolutionWithFps(meta.width, meta.height, meta.fps),
                            hasEmbeddedSubtitles = meta.hasEmbeddedSubtitles,
                            subtitleCodec = meta.subtitleCodec,
                            subtitleIndicatorReady = true,
                            artist = if (meta.artist.isNotEmpty()) meta.artist else v.artist,
                            album = if (meta.album.isNotEmpty()) meta.album else v.album,
                        )
                    }
                }
            }
        }

        FolderScanResult(
            videos = videosMap.values.sortedBy { it.displayName.lowercase(Locale.getDefault()) },
            access = FolderAccess.READABLE,
        )
    }
    
    /**
     * Scan videos from MediaStore
     */
    private suspend fun scanVideosFromMediaStore(
        context: Context,
        folderPath: String,
        videosMap: MutableMap<String, Video>
    ) {
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        // Add orientation column for API 29+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.ORIENTATION)
        }
        
        val bucketId = folderPath.hashCode().toString()
        val bucketIdLower = folderPath.lowercase(Locale.ROOT).hashCode().toString()
        val selection = "(${MediaStore.Video.Media.BUCKET_ID} = ? OR ${MediaStore.Video.Media.BUCKET_ID} = ? OR ${MediaStore.Video.Media.DATA} LIKE ?)"
        val selectionArgs = arrayOf(bucketId, bucketIdLower, "$folderPath/%")
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val orientationColumn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION)
                } else -1
                
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    // Only direct children
                    if (file.parentFile?.let(::normalizePath) != folderPath) continue
                    if (!file.exists()) continue
                    
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val title = file.nameWithoutExtension
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val rotation = if (orientationColumn != -1) cursor.getInt(orientationColumn) else 0
                    
                    val normalizedPath = normalizePath(file)

                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videosMap[normalizedPath] = Video(
                        id = id,
                        title = title,
                        displayName = displayName,
                        path = normalizedPath,
                        uri = uri,
                        duration = duration,
                        durationFormatted = MediaFormatter.formatDuration(duration),
                        size = size,
                        sizeFormatted = MediaFormatter.formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        bucketId = folderPath,
                        bucketDisplayName = File(folderPath).name,
                        width = width,
                        height = height,
                        rotation = rotation,
                        fps = 0f,
                        resolution = MediaFormatter.formatResolution(width, height),
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = "",
                        isAudio = false
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore video scan error", e)
        }
    }

    /**
     * Scan audio from MediaStore
     */
    private suspend fun scanAudioFromMediaStore(
        context: Context,
        folderPath: String,
        videosMap: MutableMap<String, Video>
    ) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        
        val bucketId = folderPath.hashCode().toString()
        val bucketIdLower = folderPath.lowercase(Locale.ROOT).hashCode().toString()
        val selection = "(${MediaStore.Audio.Media.BUCKET_ID} = ? OR ${MediaStore.Audio.Media.BUCKET_ID} = ? OR ${MediaStore.Audio.Media.DATA} LIKE ?)"
        val selectionArgs = arrayOf(bucketId, bucketIdLower, "$folderPath/%")
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    // Only direct children
                    if (file.parentFile?.let(::normalizePath) != folderPath) continue
                    if (!file.exists()) continue
                    
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val title = file.nameWithoutExtension
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "audio/*"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    
                    val normalizedPath = normalizePath(file)

                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videosMap[normalizedPath] = Video(
                        id = id,
                        title = title,
                        displayName = displayName,
                        path = normalizedPath,
                        uri = uri,
                        duration = duration,
                        durationFormatted = MediaFormatter.formatDuration(duration),
                        size = size,
                        sizeFormatted = MediaFormatter.formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        bucketId = folderPath,
                        bucketDisplayName = File(folderPath).name,
                        width = 0,
                        height = 0,
                        fps = 0f,
                        resolution = "",
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = "",
                        isAudio = true,
                        artist = artist,
                        album = album
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore audio scan error", e)
        }
    }
    
    /**
     * Cheap direct-folder discovery. Detailed metadata is enriched later by the
     * browser's bounded metadata pipeline.
     */
    private suspend fun scanMediaFromFileSystem(
        folder: File,
        videosMap: MutableMap<String, Video>,
        prelistedFiles: Array<File>? = null,
    ): Boolean {
        try {
            val files = prelistedFiles ?: folder.listFiles() ?: return false

            for (file in files) {
                currentCoroutineContext().ensureActive()
                try {
                    if (!file.isFile || FileFilterUtils.shouldSkipFile(file)) continue

                    val isVideo = FileTypeUtils.isVideoFile(file)
                    val isAudio = FileTypeUtils.isAudioFile(file)
                    if (!isVideo && !isAudio) continue

                    val path = normalizePath(file)
                    if (videosMap.containsKey(path)) continue

                    val size = file.length()
                    val dateModified = file.lastModified() / 1000
                    videosMap[path] = Video(
                        id = path.hashCode().toLong(),
                        title = file.nameWithoutExtension,
                        displayName = file.name,
                        path = path,
                        uri = Uri.fromFile(file),
                        duration = 0,
                        durationFormatted = MediaFormatter.formatDuration(0),
                        size = size,
                        sizeFormatted = MediaFormatter.formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateModified,
                        mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase()),
                        bucketId = normalizePath(folder),
                        bucketDisplayName = folder.name,
                        width = 0,
                        height = 0,
                        rotation = 0,
                        fps = 0f,
                        resolution = "",
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = "",
                        isAudio = isAudio,
                        artist = "",
                        album = "",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
                }
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Filesystem media scan error", e)
            return false
        }
    }

    internal fun buildMatchingExternalSubtitleFormats(
        siblingFiles: Iterable<File>,
    ): Map<String, List<String>> {
        val byBaseName = linkedMapOf<String, MutableList<String>>()
        for (file in siblingFiles) {
            if (!file.isFile || !FileTypeUtils.isSubtitleFile(file)) continue

            val baseName = file.nameWithoutExtension.trim()
            if (baseName.isEmpty()) continue

            val extension = file.extension.trim().uppercase(Locale.ROOT)
            if (extension.isEmpty()) continue

            byBaseName.getOrPut(baseName.lowercase(Locale.ROOT)) { mutableListOf() }
                .add(extension)
        }

        return byBaseName.mapValues { (_, formats) ->
            formats.distinct().sorted()
        }
    }

    private fun normalizePath(file: File): String =
        runCatching { file.canonicalPath }.getOrElse { file.absoluteFile.normalize().path }

    /**
     * Extracts video metadata using MediaInfo library
     */
    fun extractVideoMetadata(
        context: Context,
        file: File,
    ): VideoMetadata {
        var duration = 0L
        var mimeType = "video/*"
        var width = 0
        var height = 0
        var rotation = 0
        var artist = ""
        var album = ""
        
        try {
            val uri = Uri.fromFile(file)
            val result = runBlocking {
                MediaInfoOps.extractBasicMetadata(context, uri, file.name)
            }
            
            result.onSuccess { metadata ->
                duration = metadata.durationMs
                width = metadata.width
                height = metadata.height
                rotation = metadata.rotation
                artist = metadata.artist
                album = metadata.album
                mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
            }.onFailure { e ->
                Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
                mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
            mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
        }
        
        return VideoMetadata(duration, mimeType, width, height, rotation, artist, album)
    }
    
}
