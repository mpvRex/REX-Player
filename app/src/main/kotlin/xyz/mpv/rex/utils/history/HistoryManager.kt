package xyz.mpv.rex.utils.history

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.database.entities.RecentlyPlayedEntity
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.preferences.AdvancedPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Centralized manager for all history-related operations, including recently played 
 * tracking.
 */
class HistoryManager(
    private val context: Context,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val playbackStateRepository: PlaybackStateRepository,
    private val advancedPreferences: AdvancedPreferences,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "HistoryManager"
    }

    /**
     * Records the start of playback for a media item.
     */
    fun recordPlaybackStart(
        uri: Uri,
        fileName: String,
        launchSource: String,
        playlistId: Int? = null
    ) {
        if (!advancedPreferences.enableRecentlyPlayed.get()) return
        val isExternalOrShare = launchSource == "share" || launchSource == "open_file" || launchSource == "external"
        if (isExternalOrShare && advancedPreferences.excludeExternalPlaybackFromHistory.get()) {
            Log.d(TAG, "recordPlaybackStart: skipping history for external/share launchSource=$launchSource")
            return
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val filePath = resolveFilePath(uri)
                Log.d(TAG, "recordPlaybackStart: uri=$uri, resolvedPath=$filePath, source=$launchSource")
                if (shouldSkipHistory(filePath)) {
                    Log.d(TAG, "recordPlaybackStart: skipping history for $filePath")
                    return@launch
                }

                val videoTitle = MPVLib.getPropertyString("media-title")?.takeIf { it != fileName }
                val duration = (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
                val fileSize = getMPVFileSize()
                val (width, height) = getMPVResolution()
                val (artist, album) = getMPVMetadata()
                
                // Use extension first, then height
                val isAudio = xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(File(filePath)) || (height <= 0)

                addRecentlyPlayed(
                    filePath = filePath,
                    fileName = fileName,
                    videoTitle = videoTitle,
                    duration = duration,
                    fileSize = fileSize,
                    width = width,
                    height = height,
                    launchSource = launchSource,
                    playlistId = playlistId,
                    isAudio = isAudio,
                    artist = artist,
                    album = album
                )
                Log.d(TAG, "Recorded playback start: $filePath")
            }.onFailure { e ->
                Log.e(TAG, "Failed to record playback start", e)
            }
        }
    }

    /**
     * Directly adds an item to history.
     */
    suspend fun addRecentlyPlayed(
        filePath: String,
        fileName: String,
        videoTitle: String? = null,
        duration: Long = 0,
        fileSize: Long = 0,
        width: Int = 0,
        height: Int = 0,
        launchSource: String? = null,
        playlistId: Int? = null,
        isAudio: Boolean = false,
        artist: String = "",
        album: String = ""
    ) {
        if (!advancedPreferences.enableRecentlyPlayed.get()) return
        val isExternalOrShare = launchSource == "share" || launchSource == "open_file" || launchSource == "external"
        if (isExternalOrShare && advancedPreferences.excludeExternalPlaybackFromHistory.get()) return
        if (shouldSkipHistory(filePath)) return

        recentlyPlayedRepository.addRecentlyPlayed(
            filePath, fileName, videoTitle, duration, fileSize,
            width, height, launchSource, playlistId, isAudio, artist, album
        )
    }

    /**
     * Updates metadata for the currently playing media in history.
     */
    fun updateCurrentMediaMetadata(fileName: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val mediaTitle = MPVLib.getPropertyString("media-title")
                val duration = (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
                val fileSize = getMPVFileSize()
                val (width, height) = getMPVResolution()

                // Update the most recent entry ONLY if it matches the current file
                val lastPlayed = recentlyPlayedRepository.getRecentlyPlayed(1).firstOrNull()
                if (lastPlayed != null && (lastPlayed.fileName == fileName || lastPlayed.filePath.endsWith(fileName))) {
                    recentlyPlayedRepository.updateVideoMetadata(
                        lastPlayed.filePath,
                        mediaTitle,
                        duration,
                        fileSize,
                        width,
                        height
                    )
                    Log.d(TAG, "Updated recently played metadata for: ${lastPlayed.filePath}")
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to update current media metadata", e)
            }
        }
    }

    suspend fun clearAll() {
        recentlyPlayedRepository.clearAll()
    }

    suspend fun getRecentlyPlayed(limit: Int = 50): List<RecentlyPlayedEntity> {
        return recentlyPlayedRepository.getRecentlyPlayed(limit)
    }

    suspend fun getLastPlayed(): String? = withContext(Dispatchers.IO) {
        val autoRemove = advancedPreferences.autoRemoveDeletedFromHistory.get()
        val recent = runCatching { recentlyPlayedRepository.getRecentlyPlayed(50) }.getOrDefault(emptyList())
        for (entity in recent) {
            val path = entity.filePath
            if (isNonFileUri(path) || fileExists(path)) {
                return@withContext path
            } else if (autoRemove) {
                recentlyPlayedRepository.deleteByFilePath(path)
            }
        }
        null
    }

    suspend fun getLastPlayedEntity(): RecentlyPlayedEntity? = withContext(Dispatchers.IO) {
        val autoRemove = advancedPreferences.autoRemoveDeletedFromHistory.get()
        val recent = runCatching { recentlyPlayedRepository.getRecentlyPlayed(50) }.getOrDefault(emptyList())
        for (entity in recent) {
            val path = entity.filePath
            if (isNonFileUri(path) || fileExists(path)) {
                return@withContext entity
            } else if (autoRemove) {
                recentlyPlayedRepository.deleteByFilePath(path)
            }
        }
        null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLastPlayedPath(): Flow<String?> =
        recentlyPlayedRepository
            .observeLastPlayedForHighlight()
            .mapLatest { entity ->
                val path = entity?.filePath
                if (path.isNullOrEmpty()) null
                else if (isNonFileUri(path) || fileExists(path)) path
                else null
            }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    suspend fun onVideoDeleted(filePath: String) {
        if (filePath.isNotBlank()) {
            recentlyPlayedRepository.deleteByFilePath(filePath)
        }
    }

    /**
     * Manually sets the watch state of a media item.
     *
     * @param filePath  Stable file path (used for RecentlyPlayed lookups).
     * @param fileName  Display name / mediaTitle key (used for PlaybackState lookups).
     * @param duration  Video duration in milliseconds (needed for Finished state).
     */
    suspend fun markAs(filePath: String, fileName: String, duration: Long, state: MarkAsState) {
        when (state) {
            MarkAsState.New -> {
                // Remove progress, clear watched state, delete from history, and force the NEW badge by setting timeRemaining = -1.
                val existing = playbackStateRepository.getVideoDataByTitle(fileName)
                playbackStateRepository.upsert(
                    xyz.mpv.rex.database.entities.PlaybackStateEntity(
                        mediaTitle = fileName,
                        lastPosition = 0,
                        playbackSpeed = existing?.playbackSpeed ?: 1.0,
                        videoZoom = existing?.videoZoom ?: 0f,
                        sid = existing?.sid ?: -1,
                        secondarySid = existing?.secondarySid ?: -1,
                        subDelay = existing?.subDelay ?: 0,
                        subSpeed = existing?.subSpeed ?: 1.0,
                        aid = existing?.aid ?: -1,
                        audioDelay = existing?.audioDelay ?: 0,
                        timeRemaining = -1,
                        hasBeenWatched = false,
                        videoAspect = null,
                        customAspectRatio = -1.0,
                    )
                )
                recentlyPlayedRepository.deleteByFilePath(filePath)
            }
            MarkAsState.LastPlayed -> {
                // Clear watched status and progress first when marking as last played
                val durationSeconds = (duration / 1000).toInt().coerceAtLeast(0)
                val existing = playbackStateRepository.getVideoDataByTitle(fileName)
                playbackStateRepository.upsert(
                    xyz.mpv.rex.database.entities.PlaybackStateEntity(
                        mediaTitle = fileName,
                        lastPosition = 0,
                        playbackSpeed = existing?.playbackSpeed ?: 1.0,
                        videoZoom = existing?.videoZoom ?: 0f,
                        sid = existing?.sid ?: -1,
                        secondarySid = existing?.secondarySid ?: -1,
                        subDelay = existing?.subDelay ?: 0,
                        subSpeed = existing?.subSpeed ?: 1.0,
                        aid = existing?.aid ?: -1,
                        audioDelay = existing?.audioDelay ?: 0,
                        timeRemaining = existing?.timeRemaining ?: durationSeconds,
                        savedOrientation = existing?.savedOrientation,
                        externalSubtitles = existing?.externalSubtitles ?: "",
                        externalAudioTracks = existing?.externalAudioTracks ?: "",
                        hasBeenWatched = false,
                        videoAspect = existing?.videoAspect,
                        customAspectRatio = existing?.customAspectRatio ?: -1.0,
                    )
                )
                // Upsert a RecentlyPlayed entry with timestamp = now so the file surfaces
                // at the top of the Recently Played list.
                recentlyPlayedRepository.addRecentlyPlayed(
                    filePath = filePath,
                    fileName = fileName,
                    duration = duration,
                    launchSource = "mark_as_last_played",
                )
            }
            MarkAsState.Finished -> {
                val durationSeconds = (duration / 1000).toInt().coerceAtLeast(0)
                val existing = playbackStateRepository.getVideoDataByTitle(fileName)
                playbackStateRepository.upsert(
                    xyz.mpv.rex.database.entities.PlaybackStateEntity(
                        mediaTitle = fileName,
                        lastPosition = durationSeconds,
                        playbackSpeed = existing?.playbackSpeed ?: 1.0,
                        videoZoom = existing?.videoZoom ?: 0f,
                        sid = existing?.sid ?: -1,
                        secondarySid = existing?.secondarySid ?: -1,
                        subDelay = existing?.subDelay ?: 0,
                        subSpeed = existing?.subSpeed ?: 1.0,
                        aid = existing?.aid ?: -1,
                        audioDelay = existing?.audioDelay ?: 0,
                        timeRemaining = 0,
                        savedOrientation = existing?.savedOrientation,
                        externalSubtitles = existing?.externalSubtitles ?: "",
                        externalAudioTracks = existing?.externalAudioTracks ?: "",
                        hasBeenWatched = true,
                        videoAspect = existing?.videoAspect,
                        customAspectRatio = existing?.customAspectRatio ?: -1.0,
                    )
                )
                // Also add/bump in recently played history
                recentlyPlayedRepository.addRecentlyPlayed(
                    filePath = filePath,
                    fileName = fileName,
                    duration = duration,
                    launchSource = "mark_as",
                )
            }
            MarkAsState.None -> {
                // Suppress "NEW" badge and reset progress by inserting an inactive state.
                val durationSeconds = (duration / 1000).toInt().coerceAtLeast(0)
                val existing = playbackStateRepository.getVideoDataByTitle(fileName)
                playbackStateRepository.upsert(
                    xyz.mpv.rex.database.entities.PlaybackStateEntity(
                        mediaTitle = fileName,
                        lastPosition = 0,
                        playbackSpeed = existing?.playbackSpeed ?: 1.0,
                        videoZoom = existing?.videoZoom ?: 0f,
                        sid = existing?.sid ?: -1,
                        secondarySid = existing?.secondarySid ?: -1,
                        subDelay = existing?.subDelay ?: 0,
                        subSpeed = existing?.subSpeed ?: 1.0,
                        aid = existing?.aid ?: -1,
                        audioDelay = existing?.audioDelay ?: 0,
                        timeRemaining = durationSeconds,
                        savedOrientation = existing?.savedOrientation,
                        externalSubtitles = existing?.externalSubtitles ?: "",
                        externalAudioTracks = existing?.externalAudioTracks ?: "",
                        hasBeenWatched = false,
                        videoAspect = existing?.videoAspect,
                        customAspectRatio = existing?.customAspectRatio ?: -1.0,
                    )
                )
                // Also clear from recently played history
                recentlyPlayedRepository.deleteByFilePath(filePath)
            }
        }
    }

    suspend fun onVideoRenamed(oldPath: String, newPath: String) {
        if (oldPath.isBlank() || newPath.isBlank()) return
        val newFileName = File(newPath).name
        runCatching {
            recentlyPlayedRepository.updateFilePath(oldPath, newPath, newFileName)
        }
    }

    /**
     * Resolves a URI to a stable file path for history tracking.
     */
    fun resolveFilePath(uri: Uri): String {
        val path = when (uri.scheme) {
            "file" -> uri.path ?: uri.toString()
            "content" -> {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (index != -1) cursor.getString(index) else null
                        } else null
                    } ?: uri.toString()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve content URI: $uri", e)
                    uri.toString()
                }
            }
            else -> uri.toString()
        }
        Log.v(TAG, "resolveFilePath: $uri -> $path")
        return path
    }

    // ==================== Private Helpers ====================

    private fun shouldSkipHistory(path: String): Boolean {
        if (path.startsWith("smb://") || path.startsWith("ftp://") || 
            path.startsWith("ftps://") || path.startsWith("webdav://")) return true
            
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return false
        if (uri.host?.lowercase() in listOf("127.0.0.1", "localhost", "0.0.0.0")) return true
        return false
    }

    fun fileExists(path: String): Boolean = runCatching {
        if (path.startsWith("/") || path.startsWith("file://")) {
            val filePath = if (path.startsWith("file://")) path.removePrefix("file://") else path
            File(filePath).exists()
        } else {
            val uri = Uri.parse(path)
            val scheme = uri.scheme
            if (scheme == null || scheme.equals("file", ignoreCase = true)) {
                File(path).exists()
            } else if (scheme.equals("content", ignoreCase = true)) {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false
            } else {
                true
            }
        }
    }.getOrDefault(false)

    fun isNonFileUri(path: String): Boolean = runCatching {
        if (path.startsWith("/") || path.startsWith("file://")) false
        else {
            val scheme = Uri.parse(path).scheme
            scheme != null && !scheme.equals("file", ignoreCase = true) && !scheme.equals("content", ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun getMPVFileSize(): Long = runCatching {
        MPVLib.getPropertyDouble("file-size")?.toLong()
            ?: MPVLib.getPropertyDouble("stream-end")?.toLong() ?: 0L
    }.getOrDefault(0L)

    private fun getMPVResolution(): Pair<Int, Int> {
        val w = runCatching { MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0 }.getOrDefault(0)
        val h = runCatching { MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0 }.getOrDefault(0)
        return Pair(w, h)
    }

    private fun getMPVMetadata(): Pair<String, String> {
        val artist = MPVLib.getPropertyString("metadata/by-key/performer") ?: 
                     MPVLib.getPropertyString("metadata/by-key/artist") ?: ""
        val album = MPVLib.getPropertyString("metadata/by-key/album") ?: ""
        return Pair(artist, album)
    }
}
