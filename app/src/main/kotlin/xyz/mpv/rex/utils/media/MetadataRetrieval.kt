package xyz.mpv.rex.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.preferences.BrowserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lazy metadata retrieval system
 * Only extracts detailed metadata (duration, framerate, resolution) when chips are enabled
 * 
 * This improves app startup performance by deferring expensive metadata extraction
 * until the user actually needs to see the information.
 */
object MetadataRetrieval {
    private const val TAG = "MetadataRetrieval"

    /**
     * Checks if any metadata-dependent chips are enabled
     * If all chips are disabled, we can skip metadata extraction entirely
     */
    fun isMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        // Video card chips
        val needsVideoMetadata = browserPreferences.showResolutionChip.get() ||
                browserPreferences.showFramerateInResolution.get() ||
                browserPreferences.showSubtitleIndicator.get()

        // Folder card chips
        val needsFolderMetadata = browserPreferences.showTotalDurationChip.get()

        return needsVideoMetadata || needsFolderMetadata
    }

    /**
     * Checks if video-specific metadata is needed
     */
    fun isVideoMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        return browserPreferences.showResolutionChip.get() ||
                browserPreferences.showFramerateInResolution.get() ||
                browserPreferences.showSubtitleIndicator.get()
    }

    /**
     * Checks if folder-specific metadata is needed
     */
    fun isFolderMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        return browserPreferences.showTotalDurationChip.get()
    }

    /**
     * Enriches a video with metadata only if needed
     * Returns the video with metadata populated, or the original if metadata is disabled
     */
    suspend fun enrichVideoIfNeeded(
        context: Context,
        video: Video,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): Video = withContext(Dispatchers.IO) {
        // If metadata chips are disabled, return video as-is
        if (!isVideoMetadataNeeded(browserPreferences)) {
            return@withContext video
        }

        // If video already has metadata (including FPS and subtitle info), return as-is
        if (video.width > 0 && video.height > 0 && video.duration > 0 && video.fps > 0f && video.subtitleCodec.isNotEmpty()) {
            return@withContext video
        }

        // Extract metadata
        try {
            val file = File(video.path)
            if (!file.exists()) {
                return@withContext video
            }

            val metadata = metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)
            if (metadata != null) {
                video.copy(
                    duration = metadata.durationMs,
                    durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec
                )
            } else {
                video
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching video metadata: ${video.displayName}", e)
            video
        }
    }

    /**
     * Pre-enriches videos with metadata ALREADY stored in DB cache
     * Enables instant rendering of cached metadata (FPS, subtitles) on the first frame
     */
    suspend fun applyCachedMetadata(
        videos: List<Video>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): List<Video> = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) {
            return@withContext videos
        }
        val isMetadataNeeded = isVideoMetadataNeeded(browserPreferences)
        val hasMissingDurations = videos.any { it.duration <= 0L }
        if (!isMetadataNeeded && !hasMissingDurations) {
            return@withContext videos
        }

        val paths = videos.map { it.path }
        val cachedMap = metadataCache.getCachedMetadataBatch(paths)
        if (cachedMap.isEmpty()) {
            return@withContext videos
        }

        videos.map { video ->
            val cached = cachedMap[video.path]
            if (cached != null) {
                video.copy(
                    duration = if (cached.durationMs > 0) cached.durationMs else video.duration,
                    durationFormatted = if (cached.durationMs > 0) MediaFormatter.formatDuration(cached.durationMs) else video.durationFormatted,
                    width = if (cached.width > 0) cached.width else video.width,
                    height = if (cached.height > 0) cached.height else video.height,
                    fps = cached.fps,
                    resolution = MediaFormatter.formatResolutionWithFps(
                        if (cached.width > 0) cached.width else video.width,
                        if (cached.height > 0) cached.height else video.height,
                        cached.fps
                    ),
                    hasEmbeddedSubtitles = cached.hasEmbeddedSubtitles,
                    subtitleCodec = cached.subtitleCodec,
                    artist = if (cached.artist.isNotEmpty()) cached.artist else video.artist,
                    album = if (cached.album.isNotEmpty()) cached.album else video.album,
                )
            } else {
                video
            }
        }
    }

    /**
     * Enriches a list of videos with metadata only if needed
     * Processes in batches for better performance
     */
    suspend fun enrichVideosIfNeeded(
        context: Context,
        videos: List<Video>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): List<Video> = withContext(Dispatchers.IO) {
        val isMetadataNeeded = isVideoMetadataNeeded(browserPreferences)
        
        // Filter videos that need metadata extraction:
        // Always extract if duration is 0, or if chips are enabled and missing width/height/fps/subtitles
        val videosNeedingMetadata = videos.filter { video ->
            video.duration <= 0L || (isMetadataNeeded && (video.width == 0 || video.height == 0 || video.fps == 0f || video.subtitleCodec.isEmpty()))
        }

        if (videosNeedingMetadata.isEmpty()) {
            return@withContext videos
        }

        Log.d(TAG, "Enriching ${videosNeedingMetadata.size} videos with metadata")

        // Prepare batch extraction
        val fileTriples = videosNeedingMetadata.mapNotNull { video ->
            val file = File(video.path)
            if (file.exists()) {
                Triple(file, video.uri, video.displayName)
            } else {
                null
            }
        }

        // Batch extract metadata
        val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)

        // Update videos with metadata
        videos.map { video ->
            val metadata = metadataMap[video.path]
            if (metadata != null) {
                video.copy(
                    duration = if (metadata.durationMs > 0) metadata.durationMs else video.duration,
                    durationFormatted = if (metadata.durationMs > 0) MediaFormatter.formatDuration(metadata.durationMs) else video.durationFormatted,
                    width = if (metadata.width > 0) metadata.width else video.width,
                    height = if (metadata.height > 0) metadata.height else video.height,
                    fps = metadata.fps,
                    resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec,
                    artist = if (metadata.artist.isNotEmpty()) metadata.artist else video.artist,
                    album = if (metadata.album.isNotEmpty()) metadata.album else video.album,
                )
            } else {
                video
            }
        }
    }

    /**
     * Enriches videos progressively with metadata
     * Emits videos as they are processed, allowing UI to update incrementally
     */
    fun enrichVideosProgressively(
        context: Context,
        videos: List<Video>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): Flow<Video> = flow {
        // If metadata chips are disabled, emit all videos as-is
        if (!isVideoMetadataNeeded(browserPreferences)) {
            videos.forEach { emit(it) }
            return@flow
        }

        // Process each video
        for (video in videos) {
            // If video already has metadata (including FPS and subtitle info), emit as-is
            if (video.width > 0 && video.height > 0 && video.duration > 0 && 
                video.fps > 0f && video.subtitleCodec.isNotEmpty()) {
                emit(video)
                continue
            }

            // Extract metadata
            try {
                val file = File(video.path)
                if (!file.exists()) {
                    emit(video)
                    continue
                }

                val metadata = metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)
                if (metadata != null) {
                    emit(
                        video.copy(
                            duration = metadata.durationMs,
                            durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
                            width = metadata.width,
                            height = metadata.height,
                            fps = metadata.fps,
                            resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                            hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                            subtitleCodec = metadata.subtitleCodec
                        )
                    )
                } else {
                    emit(video)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enriching video metadata: ${video.displayName}", e)
                emit(video)
            }
        }
    }

    /**
     * Enriches a folder with metadata only if needed
     * Calculates total duration by extracting metadata from all videos in the folder
     */
    suspend fun enrichFolderIfNeeded(
        context: Context,
        folder: VideoFolder,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): VideoFolder = withContext(Dispatchers.IO) {
        // If folder duration chip is disabled, return folder as-is
        if (!isFolderMetadataNeeded(browserPreferences)) {
            return@withContext folder
        }

        // If folder has no videos or already has video duration, return as-is
        if (folder.videoCount == 0 || folder.videoDuration > 0L) {
            return@withContext folder
        }

        // Extract metadata for all videos in folder
        try {
            val directory = File(folder.path)
            if (!directory.exists() || !directory.isDirectory) {
                return@withContext folder
            }

            val videoFiles = directory.listFiles()?.filter { file ->
                file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS
            } ?: emptyList()

            if (videoFiles.isEmpty()) {
                return@withContext folder
            }

            // Batch extract metadata
            val fileTriples = videoFiles.map { file ->
                Triple(file, Uri.fromFile(file), file.name)
            }

            val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)

            // Calculate total duration
            val videoDuration = metadataMap.values.sumOf { it.durationMs }

            folder.copy(
                videoDuration = videoDuration,
                totalDuration = videoDuration + folder.audioDuration,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching folder metadata: ${folder.name}", e)
            folder
        }
    }

    /**
     * Enriches a list of folders with metadata only if needed
     * Processes in batches for better performance
     */
    suspend fun enrichFoldersIfNeeded(
        context: Context,
        folders: List<VideoFolder>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<VideoFolder> = withContext(Dispatchers.IO) {
        // If folder duration chip is disabled, return folders as-is
        if (!isFolderMetadataNeeded(browserPreferences)) {
            return@withContext folders
        }

        // Filter folders that need metadata extraction
        val foldersNeedingMetadata = folders.filter { it.videoCount > 0 && it.videoDuration == 0L }

        if (foldersNeedingMetadata.isEmpty()) {
            return@withContext folders
        }

        Log.d(TAG, "Enriching ${foldersNeedingMetadata.size} folders with metadata")

        var processed = 0
        val total = foldersNeedingMetadata.size

        // Process each folder
        val enrichedMap = foldersNeedingMetadata.associate { folder ->
            val enriched = enrichFolderIfNeeded(context, folder, browserPreferences, metadataCache)
            processed++
            onProgress?.invoke(processed, total)
            folder.path to enriched
        }

        // Return updated list
        folders.map { folder ->
            enrichedMap[folder.path] ?: folder
        }
    }

    // Helper: Video file extensions
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "3gp", "3g2", "mpg", "mpeg", "m2v", "ogv", "ts", "mts",
        "m2ts", "vob", "divx", "xvid", "f4v", "rm", "rmvb", "asf"
    )

}
