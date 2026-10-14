package xyz.mpv.rex.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.media.MediaInfoOps
import xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.thumbnail.isMostlySolidThumbnail
import xyz.mpv.rex.domain.thumbnail.scaleToThumbnailMax
import xyz.mpv.rex.utils.storage.FileTypeUtils
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import kotlin.math.max

class ThumbnailRepository(
  private val context: Context,
  customMemoryCache: LruCache<String, Bitmap>? = null,
) {
  private val appearancePreferences by lazy { 
    org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.preferences.AppearancePreferences>(
      xyz.mpv.rex.preferences.AppearancePreferences::class.java
    ) 
  }
  private val diskCacheDimension = 1024
  private val diskJpegQuality = 100
  private val memoryCache: LruCache<String, Bitmap>
  private val networkDiskDir: File = File(context.filesDir, "thumbnails/network").apply { mkdirs() }
  private val localDiskDir: File  = File(context.filesDir, "thumbnails/local").apply  { mkdirs() }
  private class ProxyRequest(
    val deferred: Deferred<Bitmap?>,
    var refCount: Int = 1,
  )
  private class NetworkFailureRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val failureCount: Int = 1,
  )
  private val failureTtlMs = 60_000L

  private val ongoingOperations = ConcurrentHashMap<String, Deferred<Bitmap?>>()
  private val ongoingProxyOperations = ConcurrentHashMap<String, ProxyRequest>()
  private val proxyThumbnailSemaphore = Semaphore(3)
  internal var proxyExtractorForTesting: (suspend (tempVideo: Video, dimension: Int) -> Bitmap?)? = null

  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val maxconcurrentfolders = 3

  private data class FolderState(
    val signature: String,
    @Volatile var nextIndex: Int = 0,
  )

  private val folderStates = ConcurrentHashMap<String, FolderState>()
  private val folderJobs = ConcurrentHashMap<String, Job>()
  
  // Track local videos that failed with FastThumbnails and should use MediaStore
  private val useMediaStoreForVideo = ConcurrentHashMap<String, Boolean>()

  // Track network URLs where all extraction strategies have failed – bounded TTL retry
  private val networkThumbnailFailed = ConcurrentHashMap<String, NetworkFailureRecord>()
  private val networkMemoryKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

  private fun diskDirFor(video: Video): File =
    if (isNetworkUrl(video.path)) networkDiskDir else localDiskDir

  private val _thumbnailReadyKeys =
    MutableSharedFlow<String>(
      extraBufferCapacity = 256,
    )
  val thumbnailReadyKeys: SharedFlow<String> = _thumbnailReadyKeys.asSharedFlow()

  private val _networkThumbnailFailuresCleared =
    MutableSharedFlow<Long?>(
      extraBufferCapacity = 64,
    )
  val networkThumbnailFailuresCleared: SharedFlow<Long?> =
    _networkThumbnailFailuresCleared.asSharedFlow()

  init {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = maxMemoryKb / 6
    memoryCache = customMemoryCache ?:
      object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(
          key: String,
          value: Bitmap,
        ): Int = value.byteCount / 1024
      }
      runCatching {
        File(context.filesDir, "thumbnails")
        .listFiles()
        ?.filter { it.isFile && it.name.endsWith(".jpg") }
        ?.forEach { it.delete() }
      }
  }

  suspend fun getThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
    forceFirstFrame: Boolean = false,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = thumbnailKey(video, widthPx, heightPx, forceFirstFrame)

      if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
        return@withContext null
      }

      memoryCache.get(key)?.let { return@withContext it }

      ongoingOperations[key]?.let {
        return@withContext it.await()
      }

      val deferred =
        async {
          try {
            loadFromDisk(video, forceFirstFrame = forceFirstFrame)?.let { thumbnail ->
              if (isNetworkUrl(video.path)) networkMemoryKeys.add(key)
              memoryCache.put(key, thumbnail)
              _thumbnailReadyKeys.tryEmit(key)
              return@async thumbnail
            }

            if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
              return@async null
            }

            val videoKey = videoBaseKey(video)
            val isAudio = video.isAudio || xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(java.io.File(video.path))

            val thumbnail = if (forceFirstFrame) {
              // Direct First-Frame (0.0s) extraction for Shorts Player
              generateWithFastThumbnails(video, diskCacheDimension, targetOffsetsOverride = listOf(0.0))
                ?: generateWithMediaMetadataRetriever(video, diskCacheDimension, targetOffsetsOverride = listOf(0L))
            } else if (isNetworkUrl(video.path)) {
              
              // ---- Network path ------------------------------------------------
              // Android's native MediaStore cannot handle network URLs properly,
              // MediaMetadataRetriever -> FastThumbnails
              // Once both fail record it to avoid re-trying on every scroll.
              if (isNetworkThumbnailFailed(videoKey)) {
                android.util.Log.d("ThumbnailRepository", "Skipping network thumbnail (previously failed): ${video.displayName}")
                null
              } else {
                // Priority 1: MediaMetadataRetriever (10s, 15s with solid check)
                val retrieverResult = generateWithMediaMetadataRetriever(video, diskCacheDimension)
                if (retrieverResult != null) {
                  retrieverResult
                } else {
                  // Fallback: FastThumbnails (10s only, no solid check)
                  android.util.Log.w("ThumbnailRepository", "Retriever failed for network stream ${video.displayName}, trying FastThumbnails")
                  val fastResult = generateWithFastThumbnails(video, diskCacheDimension)
                  if (fastResult == null) {
                    android.util.Log.w("ThumbnailRepository", "All strategies failed for network stream ${video.displayName}")
                    markNetworkThumbnailFailed(videoKey)
                  }
                  fastResult
                }
              }
            } else if (isAudio) {
              // ---- Local-audio path ---------------------------------------------
              // Priority 1: Embedded album art (ID3 APIC / MP4 covr / FLAC picture)
              // Priority 2: Sibling or folder image (song.jpg, cover.jpg, folder.jpg, album.jpg)
              // Priority 3: Android MediaStore audio thumbnail / MediaMetadataRetriever fallback
              val embedded = generateFromEmbeddedPicture(video, diskCacheDimension)
              val sibling = embedded ?: loadFromSiblingImage(video, diskCacheDimension)
              val mediaStore = sibling ?: generateWithMediaStore(video, diskCacheDimension)
              mediaStore ?: generateAudioThumbnail(video, diskCacheDimension)
            } else {

              // ---- Local-file path ---------------------------------------------
              // Priority 0: sibling image (cover art next to video file).
              // Priority 1: embedded/attached picture (cover art). Catches MP4 covr,
              // MP3 APIC, and Matroska attachments — most notably files produced by
              // `yt-dlp --embed-thumbnail --merge-output-format mkv`. Skip frame-seeking
              // entirely when the container already carries a thumbnail.
              val sibling = loadFromSiblingImage(video, diskCacheDimension)
              val embedded = sibling ?: generateFromEmbeddedPicture(video, diskCacheDimension)
              if (embedded != null) {
                embedded
              } else {
              // For local videos, we can be more aggressive about trying to find a good thumbnail:
              // Short videos: (<2 min) get priority for MediaStore
              // Longer videos: FastThumbnails -> MediaMetadataRetriever -> MediaStore
              val isShortVideo = video.duration in 1L..120_000L

              // If it's a short video, or we already know heavy extractors fail on this file, go straight to MediaStore
              if (useMediaStoreForVideo.containsKey(videoKey) || isShortVideo) {
                val storeResult = generateWithMediaStore(video, diskCacheDimension)
                
                if (storeResult != null) {
                  storeResult
                } else if (isShortVideo) {
                  // If MediaStore inexplicably fails on a short video, run through the standard heavy fallback
                  android.util.Log.w("ThumbnailRepository", "MediaStore failed for short video ${video.displayName}, falling back to FastThumbnails")
                  val fastResult = generateWithFastThumbnails(video, diskCacheDimension)
                  if (fastResult != null) {
                    fastResult
                  } else {
                    generateWithMediaMetadataRetriever(video, diskCacheDimension)
                  }
                } else {
                  null
                }
              } else {
                // For videos > 120s: Priority 1 - FastThumbnails (10s, 20s, 30s)
                val fastResult = generateWithFastThumbnails(video, diskCacheDimension)
                if (fastResult != null) {
                  fastResult
                } else {
                  // Fallback 1: MediaMetadataRetriever (10s, 15s)
                  android.util.Log.w("ThumbnailRepository", "FastThumbnails failed for local ${video.displayName}, trying Retriever")
                  val retrieverResult = generateWithMediaMetadataRetriever(video, diskCacheDimension)
                  if (retrieverResult != null) {
                    retrieverResult
                  } else {
                    // Fallback 2: MediaStore & ThumbnailUtils
                    android.util.Log.w("ThumbnailRepository", "Retriever failed for local ${video.displayName}, falling back to MediaStore")
                    useMediaStoreForVideo[videoKey] = true
                    generateWithMediaStore(video, diskCacheDimension)
                  }
                }
              }
              }
            }

            if (thumbnail == null) {
              return@async null
            }
            if (isNetworkUrl(video.path)) networkMemoryKeys.add(key)
            memoryCache.put(key, thumbnail)
            _thumbnailReadyKeys.tryEmit(key)
            writeToDisk(video, thumbnail, forceFirstFrame = forceFirstFrame)

            thumbnail
          } finally {
            ongoingOperations.remove(key)
          }
        }

      ongoingOperations[key] = deferred
      return@withContext deferred.await()
    }

  suspend fun getCachedThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
    forceFirstFrame: Boolean = false,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
        return@withContext null
      }
      
      val key = thumbnailKey(video, widthPx, heightPx, forceFirstFrame)
      synchronized(memoryCache) { memoryCache.get(key) }?.let { return@withContext it }
      loadFromDisk(video, forceFirstFrame = forceFirstFrame)?.let { thumbnail ->
        synchronized(memoryCache) {
          if (isNetworkUrl(video.path)) networkMemoryKeys.add(key)
          memoryCache.put(key, thumbnail)
        }
        return@withContext thumbnail
      }
      null
    }

  fun getThumbnailFromMemory(
    video: Video,
    widthPx: Int,
    heightPx: Int,
    forceFirstFrame: Boolean = false,
  ): Bitmap? {
    if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
      return null
    }
    
    val key = thumbnailKey(video, widthPx, heightPx, forceFirstFrame)
    return synchronized(memoryCache) { memoryCache.get(key) }
  }

  /**
   * Constructs the deterministic cache key for proxy-stream thumbnails.
   *
   * @param connectionId The ID of the network connection.
   * @param path The remote file path or URI string.
   * @param dimension The requested pixel dimension.
   * @return Cache key in format "$connectionId|$path|networkProxy|$dimension".
   */
  fun networkProxyThumbnailKey(connectionId: Long, path: String, dimension: Int): String =
    Companion.networkProxyThumbnailKey(connectionId, path, dimension)

  fun networkProxyThumbnailKey(path: String, dimension: Int): String =
    Companion.networkProxyThumbnailKey(0L, path, dimension)

  fun networkProxyThumbnailDiskKey(connectionId: Long, path: String): String =
    Companion.networkProxyThumbnailDiskKey(connectionId, path)

  fun networkProxyThumbnailDiskKey(path: String): String =
    Companion.networkProxyThumbnailDiskKey(0L, path)

  /**
   * Performs an immediate, synchronous memory-cache lookup for a proxy-stream thumbnail.
   *
   * Used in Compose UI cards within [androidx.compose.runtime.remember] to instantly bind
   * cached bitmaps on frame 0 without placeholder flickering during scrolling.
   */
  fun getThumbnailViaProxyFromMemory(
    connectionId: Long,
    path: String,
    dimension: Int,
  ): Bitmap? {
    if (!appearancePreferences.showNetworkThumbnails.get()) return null
    val key = networkProxyThumbnailKey(connectionId, path, dimension)
    return synchronized(memoryCache) { memoryCache.get(key) }
  }

  fun getThumbnailViaProxyFromMemory(
    path: String,
    dimension: Int,
  ): Bitmap? = getThumbnailViaProxyFromMemory(0L, path, dimension)

  /**
   * Checks if proxy thumbnail generation previously failed for this path on this connection.
   * Employs bounded TTL expiration allowing transient network failures to be retried.
   */
  fun isNetworkThumbnailFailed(connectionId: Long, path: String): Boolean {
    val key = "$connectionId|$path"
    val record = networkThumbnailFailed[key] ?: return false
    if (System.currentTimeMillis() - record.timestamp > failureTtlMs) {
      networkThumbnailFailed.remove(key)
      return false
    }
    return true
  }

  fun isNetworkThumbnailFailed(path: String): Boolean {
    val record = networkThumbnailFailed[path] ?: networkThumbnailFailed["0|$path"] ?: return false
    if (System.currentTimeMillis() - record.timestamp > failureTtlMs) {
      networkThumbnailFailed.remove(path)
      networkThumbnailFailed.remove("0|$path")
      return false
    }
    return true
  }

  /**
   * Marks a remote path as failed for proxy thumbnail extraction.
   */
  fun markNetworkThumbnailFailed(connectionId: Long, path: String) {
    val key = "$connectionId|$path"
    val existing = networkThumbnailFailed[key]
    val count = (existing?.failureCount ?: 0) + 1
    networkThumbnailFailed[key] = NetworkFailureRecord(System.currentTimeMillis(), count)
  }

  fun markNetworkThumbnailFailed(path: String) {
    val existing = networkThumbnailFailed[path]
    val count = (existing?.failureCount ?: 0) + 1
    networkThumbnailFailed[path] = NetworkFailureRecord(System.currentTimeMillis(), count)
  }

  /**
   * Clears failure records for a specific connection or all connections on manual refresh.
   */
  fun clearNetworkThumbnailFailures(connectionId: Long? = null) {
    if (connectionId == null) {
      networkThumbnailFailed.clear()
    } else {
      val prefix = "$connectionId|"
      networkThumbnailFailed.keys.removeIf { it.startsWith(prefix) }
    }
    _networkThumbnailFailuresCleared.tryEmit(connectionId)
  }

  /**
   * Clears memory cache, disk cache, and failure tracking for a specific connection ID.
   */
  fun clearConnectionThumbnailCache(connectionId: Long) {
    clearNetworkThumbnailFailures(connectionId)
    val prefix = "$connectionId|"
    synchronized(memoryCache) {
      val snapshot = memoryCache.snapshot().keys.toList()
      for (key in snapshot) {
        if (key.startsWith(prefix)) {
          memoryCache.remove(key)
          networkMemoryKeys.remove(key)
        }
      }
    }
    runCatching {
      val connDir = File(networkDiskDir, connectionId.toString())
      if (connDir.exists()) connDir.deleteRecursively()
    }
  }

  fun clearLocalThumbnailCache() {
    folderJobs.values.forEach { it.cancel() }
    folderJobs.clear()
    folderStates.clear()
    useMediaStoreForVideo.clear()

    synchronized(memoryCache) {
      val snapshot = memoryCache.snapshot().keys.toList()
      for (key in snapshot) {
        if (key !in networkMemoryKeys) {
          memoryCache.remove(key)
        }
      }
    }

    runCatching {
      localDiskDir.listFiles()?.forEach { it.delete() }
    }
  }

  fun clearThumbnailCache() {
    folderJobs.values.forEach { it.cancel() }
    folderJobs.clear()
    folderStates.clear()
    ongoingOperations.clear()
    ongoingProxyOperations.clear()
    useMediaStoreForVideo.clear()
    networkThumbnailFailed.clear()
    networkMemoryKeys.clear()

    synchronized(memoryCache) {
      memoryCache.evictAll()
    }

    runCatching {
      if (networkDiskDir.exists()) networkDiskDir.listFiles()?.forEach { it.deleteRecursively() }
      if (localDiskDir.exists()) localDiskDir.listFiles()?.forEach { it.delete() }
    }
  }

  fun startFolderThumbnailGeneration(
    folderId: String,
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ) {
    val filteredVideos = if (appearancePreferences.showNetworkThumbnails.get()) {
      videos
    } else {
      videos.filterNot { v -> isNetworkUrl(v.path) }
    }
    
    if (filteredVideos.isEmpty()) return
    
    folderJobs.entries.removeAll { !it.value.isActive }
    
    if (folderJobs.size >= maxconcurrentfolders && !folderJobs.containsKey(folderId)) {
      folderJobs.entries.firstOrNull()?.let { (oldestId, job) ->
        job.cancel()
        folderJobs.remove(oldestId)
        folderStates.remove(oldestId)
      }
    }
    
    val signature = folderSignature(filteredVideos, widthPx, heightPx)
    val state =
      folderStates.compute(folderId) { _, existing ->
        if (existing == null || existing.signature != signature) {
          FolderState(signature = signature, nextIndex = 0)
        } else {
          existing
        }
      }!!

    folderJobs.remove(folderId)?.cancel()
    folderJobs[folderId] =
      repositoryScope.launch {
        var i = state.nextIndex
        while (i < filteredVideos.size) {
          val video = filteredVideos[i]
          getThumbnail(video, widthPx, heightPx)
          i++
          state.nextIndex = i
        }
      }
  }

  fun thumbnailKey(
    video: Video,
    width: Int,
    height: Int,
    forceFirstFrame: Boolean = false,
  ): String {
    val base = videoBaseKey(video)
    val suffix = if (forceFirstFrame) "|forceFirstFrame" else ""
    return "$base|$width|$height$suffix"
  }

  private fun videoBaseKey(video: Video): String {
    if (isNetworkUrl(video.path)) {
      val base = video.path.ifBlank { video.uri.toString() }
      return "$base|network"
    }
    
    return "${video.size}|${video.dateModified}|${video.duration}"
  }

  private fun keyToFileName(key: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(key.toByteArray())
    val hex = digest.joinToString("") { b -> "%02x".format(b) }
    return "$hex.jpg"
  }

  private fun diskKey(video: Video, forceFirstFrame: Boolean = false): String {
    val baseKey = videoBaseKey(video)
    if (forceFirstFrame) {
      return "$baseKey|disk|d$diskCacheDimension|forceFirstFrame"
    }
    return if (isNetworkUrl(video.path)) {
      "$baseKey|disk|d$diskCacheDimension|pos10s"
    } else {
      val isAudio = video.isAudio || xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(java.io.File(video.path))
      if (isAudio) {
        "$baseKey|disk|d$diskCacheDimension|audioArt"
      } else {
        val strategy = appearancePreferences.thumbnailStrategy.get()
        if (strategy == xyz.mpv.rex.preferences.ThumbnailStrategy.FirstFrame) {
          "$baseKey|disk|d$diskCacheDimension|firstFrame"
        } else {
          val percent = appearancePreferences.thumbnailPositionPercent.get()
          "$baseKey|disk|d$diskCacheDimension|pos${percent}pct"
        }
      }
    }
  }

  private fun loadFromDisk(
    video: Video,
    targetDimension: Int = diskCacheDimension,
    forceFirstFrame: Boolean = false,
  ): Bitmap? {
    val diskFile = File(diskDirFor(video), keyToFileName(diskKey(video, forceFirstFrame)))
    if (!diskFile.exists()) {
      if (!isNetworkUrl(video.path) && video.duration > 0L) {
        val altFile = File(diskDirFor(video), keyToFileName(diskKey(video.copy(duration = 0L), forceFirstFrame)))
        if (altFile.exists()) {
          android.util.Log.d("ThumbnailRepository", "Disk cache HIT (zero duration fallback): ${altFile.name} for ${video.displayName}")
          return decodeFileSafely(altFile.absolutePath, targetDimension)
        }
      }
      android.util.Log.d("ThumbnailRepository", "Disk cache MISS: ${diskFile.name} for ${video.displayName}")
      return null
    }
    android.util.Log.d("ThumbnailRepository", "Disk cache HIT: ${diskFile.name} for ${video.displayName}")
    return decodeFileSafely(diskFile.absolutePath, targetDimension)
  }

  private fun writeToDisk(
    video: Video,
    bitmap: Bitmap,
    forceFirstFrame: Boolean = false,
  ) {
    val diskFile = File(diskDirFor(video), keyToFileName(diskKey(video, forceFirstFrame)))
    runCatching {
      FileOutputStream(diskFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, diskJpegQuality, out)
        out.flush()
      }
      android.util.Log.d("ThumbnailRepository", "Disk cache written: ${diskFile.name} for ${video.displayName}")
    }.onFailure { e ->
      android.util.Log.e("ThumbnailRepository", "writeToDisk FAILED for ${video.displayName}", e)
    }
  }
  
  private fun decodeFileSafely(filePath: String, targetDimension: Int): Bitmap? {
    return runCatching {
      val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, this)
        inSampleSize = calculateThumbnailSampleSize(outWidth, outHeight, targetDimension)
        inJustDecodeBounds = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      BitmapFactory.decodeFile(filePath, options)
    }.onFailure { e ->
      android.util.Log.e("ThumbnailRepository", "Safe decode FAILED for $filePath", e)
    }.getOrNull()
  }

  private suspend fun rotateIfNeeded(
    video: Video,
    bitmap: Bitmap
  ): Bitmap {
    val rotation = MediaInfoOps.getRotation(context, video.uri, video.displayName)
    if (rotation == 0) return bitmap
    val matrix = android.graphics.Matrix()
    matrix.postRotate(rotation.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  /**
   * Software Decoder. Highly CPU-intensive fallback for unsupported codecs.
   * - Network: Single attempt (10s), no solid-frame evasion (saves bandwidth).
   * - Local: Multi-pass (10s, 20s, 30s) with solid-frame evasion.
   */
  private suspend fun generateWithFastThumbnails(
    video: Video,
    dimension: Int,
    targetOffsetsOverride: List<Double>? = null,
  ): Bitmap? {
    if (video.isAudio) return null
    
    // Additional extension-based safety check
    val extension = video.path.substringAfterLast(".", "").lowercase()
    val audioExtensions = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "opus", "m4p", "amr")
    if (extension in audioExtensions || video.mimeType.startsWith("audio/", ignoreCase = true)) return null
    
    val isNetwork = isNetworkUrl(video.path)
    val durationSec = video.duration / 1000.0

    return try {
      if (targetOffsetsOverride != null) {
        for (offset in targetOffsetsOverride) {
          val targetPosition = if (durationSec > 0) minOf(offset, max(0.0, durationSec - 0.1)) else offset
          val bmp = FastThumbnails.generateAsync(
              video.path.ifBlank { video.uri.toString() },
              targetPosition,
              dimension,
              useHwDec = false
          ) ?: continue
          return rotateIfNeeded(video, bmp)
        }
        null
      } else if (isNetwork) {
        // Network: Try 10s mark only, with 0s fallback for short clips / unknown duration.
        val targetPosition = if (durationSec > 0) minOf(10.0, max(0.0, durationSec - 1.0)) else 10.0
        val rawBmp = FastThumbnails.generateAsync(
            video.path.ifBlank { video.uri.toString() },
            targetPosition,
            dimension,
            useHwDec = false
        ) ?: if (targetPosition > 0.0) {
            FastThumbnails.generateAsync(
                video.path.ifBlank { video.uri.toString() },
                0.0,
                dimension,
                useHwDec = false
            )
        } else {
            null
        }
        val bmp = rawBmp ?: return null
        return rotateIfNeeded(video, bmp)
      } else {
        // Local: Check 10s, 20s, 30s. Skip solid frames.
        val attemptOffsets = listOf(10.0, 20.0, 30.0)
        var lastSolidBitmap: Bitmap? = null

        for (offset in attemptOffsets) {
          val targetPosition = if (durationSec > 0) minOf(offset, max(0.0, durationSec - 1.0)) else offset

          val bmp = FastThumbnails.generateAsync(
              video.path.ifBlank { video.uri.toString() },
              targetPosition,
              dimension,
              useHwDec = false
          ) ?: continue

          if (isMostlySolidThumbnail(bmp)) {
            lastSolidBitmap?.recycle()
            lastSolidBitmap = bmp
            continue
          }
          
          lastSolidBitmap?.recycle()
          return rotateIfNeeded(video, bmp)
        }
        
        if (lastSolidBitmap != null) {
          return rotateIfNeeded(video, lastSolidBitmap)
        }

        return null
      }
    } catch (e: Throwable) {
      return null
    }
  }

  /**
   * Checks for a sibling image file in the same directory with the same base name.
   * Typically produced by downloaders like yt-dlp that save separate cover images.
   */
  private suspend fun loadFromSiblingImage(
    video: Video,
    dimension: Int,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (isNetworkUrl(video.path)) return@withContext null

    val videoFile = File(video.path)
    val parentDir = videoFile.parentFile ?: return@withContext null
    if (!parentDir.exists() || !parentDir.isDirectory) return@withContext null

    val baseName = videoFile.nameWithoutExtension
    val extensions = listOf("jpg", "jpeg", "png", "webp")

    if (baseName.isNotBlank()) {
      for (ext in extensions) {
        val siblingFile = File(parentDir, "$baseName.$ext")
        if (siblingFile.exists() && siblingFile.isFile) {
          android.util.Log.d("ThumbnailRepository", "Found sibling thumbnail: ${siblingFile.absolutePath} for ${video.displayName}")
          val bmp = decodeFileSafely(siblingFile.absolutePath, dimension)
          if (bmp != null) {
            return@withContext rotateIfNeeded(video, bmp)
          }
        }
      }
    }

    val isAudio = video.isAudio || xyz.mpv.rex.utils.storage.FileTypeUtils.isAudioFile(videoFile)
    if (isAudio) {
      val commonNames = listOf("cover", "folder", "album", "art", "front")
      for (name in commonNames) {
        for (ext in extensions) {
          val coverFile = File(parentDir, "$name.$ext")
          if (coverFile.exists() && coverFile.isFile) {
            android.util.Log.d("ThumbnailRepository", "Found folder album cover thumbnail: ${coverFile.absolutePath} for ${video.displayName}")
            val bmp = decodeFileSafely(coverFile.absolutePath, dimension)
            if (bmp != null) {
              return@withContext rotateIfNeeded(video, bmp)
            }
          }
        }
      }
    }
    null
  }

  /**
   * Two-pass embedded-picture extractor for local video and audio files.
   *
   *   1. `MediaMetadataRetriever.getEmbeddedPicture()` — fast, handles MP4 `covr`
   *      atoms and MP3 ID3 APIC frames reliably.
   *   2. `FastThumbnails.getEmbeddedPicture()` (libmpv/FFmpeg) — fallback that
   *      catches Matroska attachments (yt-dlp's `--embed-thumbnail` MKVs) which
   *      Android's stock retriever frequently misses.
   *
   * Returns null when neither surface finds a picture.
   */
  private suspend fun generateFromEmbeddedPicture(
    video: Video,
    dimension: Int,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (isNetworkUrl(video.path)) return@withContext null
    android.util.Log.d("ThumbnailRepository", "generateFromEmbeddedPicture: started for path=${video.path}")

    val mmrBitmap = runCatching {
      val retriever = android.media.MediaMetadataRetriever()
      try {
        if (video.path.startsWith("content://") || video.path.startsWith("file://")) {
          retriever.setDataSource(context, video.uri)
        } else {
          retriever.setDataSource(video.path)
        }
        val bytes = retriever.embeddedPicture ?: return@runCatching null
        val options = BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
          inSampleSize = calculateThumbnailSampleSize(outWidth, outHeight, dimension)
          inJustDecodeBounds = false
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
      } finally {
        runCatching { retriever.release() }
      }
    }.onFailure {
      android.util.Log.d("ThumbnailRepository", "MediaMetadataRetriever failed for ${video.displayName}: ${it.message}")
    }.getOrNull()
    
    if (mmrBitmap != null) {
      android.util.Log.d("ThumbnailRepository", "Embedded picture via MediaMetadataRetriever for ${video.displayName}")
      return@withContext mmrBitmap
    }

    android.util.Log.d("ThumbnailRepository", "Calling FastThumbnails.getEmbeddedPictureAsync for ${video.displayName}")
    val ffmpegBitmap = runCatching {
      FastThumbnails.getEmbeddedPictureAsync(video.path, dimension)
    }.onFailure {
      android.util.Log.e("ThumbnailRepository", "FFmpeg attachment extraction failed for ${video.displayName}", it)
    }.getOrNull()
    
    if (ffmpegBitmap != null) {
      android.util.Log.d("ThumbnailRepository", "Embedded picture via FFmpeg attachment for ${video.displayName}")
      return@withContext ffmpegBitmap
    }

    android.util.Log.d("ThumbnailRepository", "No embedded/attached picture found for ${video.displayName}")
    null
  }

  private suspend fun generateAudioThumbnail(
    video: Video,
    dimension: Int,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (isNetworkUrl(video.path)) return@withContext null

    runCatching {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val contentUri = if (video.path.startsWith("content://")) {
          video.uri
        } else {
          android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            video.id
          )
        }
        context.contentResolver.loadThumbnail(
          contentUri,
          android.util.Size(dimension, dimension),
          null
        )
      } else {
        val retriever = android.media.MediaMetadataRetriever()
        try {
          if (video.path.startsWith("content://") || video.path.startsWith("file://")) {
            retriever.setDataSource(context, video.uri)
          } else {
            retriever.setDataSource(video.path)
          }
          val art = retriever.embeddedPicture
          if (art != null) {
            BitmapFactory.decodeByteArray(art, 0, art.size)
          } else {
            null
          }
        } finally {
          runCatching { retriever.release() }
        }
      }
    }.getOrNull()
  }

  private suspend fun generateWithMediaStore(
    video: Video,
    dimension: Int,
  ): Bitmap? {
    // MediaStore only works for local files, not network URLs
    if (isNetworkUrl(video.path)) {
      android.util.Log.w("ThumbnailRepository", "Cannot use MediaStore for network URL: ${video.path}")
      return null
    }
    
    return withContext(Dispatchers.IO) {
      // Try MediaStore first
      val mediaStoreThumbnail = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          // Use modern API for Android Q+
          // Build proper MediaStore content URI
          val baseUri = if (video.isAudio) {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
          } else {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
          }
          val contentUri = android.content.ContentUris.withAppendedId(baseUri, video.id)
          android.util.Log.d("ThumbnailRepository", "Generating MediaStore thumbnail for ${video.displayName} using loadThumbnail")
          val thumbnail = context.contentResolver.loadThumbnail(
            contentUri,
            android.util.Size(dimension, dimension),
            null
          )
          android.util.Log.d("ThumbnailRepository", "MediaStore thumbnail generated successfully for ${video.displayName}")
          if (video.isAudio) thumbnail else rotateIfNeeded(video, thumbnail)
        } else {
          // Use legacy API for older versions
          if (video.isAudio) {
            // Audio thumbnails not easily available via legacy MediaStore, use MediaMetadataRetriever
            null
          } else {
            android.util.Log.d("ThumbnailRepository", "Generating MediaStore thumbnail for ${video.displayName} using getThumbnail")
            @Suppress("DEPRECATION")
            val thumbnail = android.provider.MediaStore.Video.Thumbnails.getThumbnail(
              context.contentResolver,
              video.id,
              android.provider.MediaStore.Video.Thumbnails.MINI_KIND,
              null
            )
            if (thumbnail != null) {
              // Scale to desired dimension
              val scaled = Bitmap.createScaledBitmap(
                thumbnail,
                dimension,
                (dimension * thumbnail.height) / thumbnail.width,
                true
              )
              if (scaled != thumbnail) {
                thumbnail.recycle()
              }
              android.util.Log.d("ThumbnailRepository", "MediaStore thumbnail generated successfully for ${video.displayName}")
              rotateIfNeeded(video, scaled)
            } else {
              android.util.Log.w("ThumbnailRepository", "MediaStore returned null thumbnail for ${video.displayName}")
              null
            }
          }
        }
      }.onFailure { e ->
        android.util.Log.w("ThumbnailRepository", "MediaStore thumbnail failed for ${video.displayName}: ${e.message}")
      }.getOrNull()
      
      // If MediaStore failed, try ThumbnailUtils as last resort (only for video)
      if (mediaStoreThumbnail != null || video.isAudio) {
        return@withContext mediaStoreThumbnail
      }
      
      // Fallback to ThumbnailUtils (extracts directly from file)
      runCatching {
        android.util.Log.d("ThumbnailRepository", "Generating thumbnail using ThumbnailUtils for ${video.displayName}")
        val file = java.io.File(video.path)
        if (!file.exists()) {
          android.util.Log.e("ThumbnailRepository", "File does not exist: ${video.path}")
          return@runCatching null
        }
        
        val thumbnail = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          android.media.ThumbnailUtils.createVideoThumbnail(
            file,
            android.util.Size(dimension, dimension),
            null
          )
        } else {
          @Suppress("DEPRECATION")
          android.media.ThumbnailUtils.createVideoThumbnail(
            video.path,
            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
          )?.let { thumb ->
            // Scale to desired dimension
            Bitmap.createScaledBitmap(
              thumb,
              dimension,
              (dimension * thumb.height) / thumb.width,
              true
            ).also {
              if (it != thumb) thumb.recycle()
            }
          }
        }
        
        if (thumbnail != null) {
          android.util.Log.d("ThumbnailRepository", "ThumbnailUtils thumbnail generated successfully for ${video.displayName}")
          rotateIfNeeded(video, thumbnail)
        } else {
          android.util.Log.e("ThumbnailRepository", "ThumbnailUtils returned null for ${video.displayName}")
          null
        }
      }.onFailure { e ->
        android.util.Log.e("ThumbnailRepository", "ThumbnailUtils thumbnail generation failed for ${video.displayName}", e)
      }.getOrNull()
    }
  }

  /**
   * Hardware Decoder. Battery-efficient primary extractor for network 
   * streams, and low-cost fallback for local files.
   * - Evaluates 10s and 15s offsets with solid-frame evasion.
   */
  private suspend fun generateWithMediaMetadataRetriever(
    video: Video,
    dimension: Int,
    targetOffsetsOverride: List<Long>? = null,
  ): Bitmap? = withContext(Dispatchers.IO) {
    val url = video.path.ifBlank { video.uri.toString() }
    val retriever = android.media.MediaMetadataRetriever()
    var lastSolidBitmap: Bitmap? = null

    try {
      if (url.startsWith("content://") || url.startsWith("file://")) {
        retriever.setDataSource(context, video.uri)
      } else {
        retriever.setDataSource(url, emptyMap<String, String>())
      }
      val streamDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
      val durationMs = streamDurationMs ?: video.duration.takeIf { it > 0L }
      
      val attemptOffsetsUs = targetOffsetsOverride ?: listOf(10_000_000L, 15_000_000L, 0L)

      for (offsetUs in attemptOffsetsUs) {
        val positionUs: Long = if (durationMs != null && durationMs > 0L) {
          minOf(offsetUs, (durationMs - 100L).coerceAtLeast(0L) * 1000L)
        } else {
          offsetUs
        }

        val frame: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
          runCatching { retriever.getScaledFrameAtTime(positionUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dimension, dimension) }.getOrNull()
            ?: retriever.getFrameAtTime(positionUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.scaleToThumbnailMax(dimension)
        } else {
          retriever.getFrameAtTime(positionUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.scaleToThumbnailMax(dimension)
        }

        if (frame == null) continue 

        if (isMostlySolidThumbnail(frame)) {
          lastSolidBitmap?.recycle()
          lastSolidBitmap = frame
          continue
        }

        lastSolidBitmap?.recycle()
        return@withContext rotateIfNeeded(video, frame)
      }

      if (lastSolidBitmap != null) {
        return@withContext rotateIfNeeded(video, lastSolidBitmap)
      }

      return@withContext null
    } catch (e: Throwable) {
      lastSolidBitmap?.recycle()
      return@withContext null
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun preferredPositionSeconds(video: Video): Double {
    val isNetworkUrl = isNetworkUrl(video.path)

    if (isNetworkUrl) {
      val durationSec = video.duration / 1000.0

      if (durationSec > 0.0) {
        return minOf(10.0, max(0.0, durationSec - 0.1))
      }
      return 10.0
    }

    val durationSec = video.duration / 1000.0

    if (durationSec <= 0.0 || durationSec < 20.0) return 0.0

    val strategy = appearancePreferences.thumbnailStrategy.get()
    return if (strategy == xyz.mpv.rex.preferences.ThumbnailStrategy.FirstFrame) {
      // Hardcoded 10s logic for local videos
      minOf(10.0, max(0.0, durationSec - 0.1))
    } else {
      // Frame at position based on preference
      val percent = appearancePreferences.thumbnailPositionPercent.get() / 100.0
      val candidate = durationSec * percent
      candidate.coerceIn(0.0, max(0.0, durationSec - 0.1))
    }
  }
  
  private fun isNetworkUrl(path: String): Boolean {
    return path.startsWith("http://", ignoreCase = true) ||
      path.startsWith("https://", ignoreCase = true) ||
      path.startsWith("rtmp://", ignoreCase = true) ||
      path.startsWith("rtsp://", ignoreCase = true) ||
      path.startsWith("ftp://", ignoreCase = true) ||
      path.startsWith("sftp://", ignoreCase = true)
  }

  private fun folderSignature(
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ): String {
    val md = MessageDigest.getInstance("MD5")
    md.update("$widthPx|$heightPx|".toByteArray())
    for (v in videos) {
      md.update(v.path.toByteArray())
      md.update("|".toByteArray())
      md.update(v.size.toString().toByteArray())
      md.update("|".toByteArray())
      md.update(v.dateModified.toString().toByteArray())
      md.update(";".toByteArray())
    }
    return md.digest().joinToString("") { b -> "%02x".format(b) }
  }
  
  
/**
 * Resolves network video thumbnails via a localized proxy stream.
 * 
 * Strategy: MemCache -> DiskCache -> Proxy Extraction -> Cache/Fail.
 * Bypasses network extraction limits by registering a temporary [NetworkStreamingProxy] 
 * stream and feeding a mocked localhost URI to standard extractors. Guarantees proxy cleanup.
 */
  suspend fun getThumbnailViaProxy(
    path: String,
    name: String,
    size: Long,
    connection: NetworkConnection,
    dimension: Int = diskCacheDimension,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (!appearancePreferences.showNetworkThumbnails.get()) return@withContext null

    val videoKey = networkProxyThumbnailKey(connection.id, path, dimension)
    
    if (isNetworkThumbnailFailed(connection.id, path)) return@withContext null

    synchronized(memoryCache) { memoryCache.get(videoKey) }?.let { return@withContext it }

    val connectionDiskDir = File(networkDiskDir, connection.id.toString())
    val diskKey = networkProxyThumbnailDiskKey(connection.id, path)
    val diskFile = File(connectionDiskDir, keyToFileName(diskKey))
    if (diskFile.exists()) {
      decodeFileSafely(diskFile.absolutePath, dimension)?.let { bitmap ->
        synchronized(memoryCache) {
          memoryCache.put(videoKey, bitmap)
          networkMemoryKeys.add(videoKey)
        }
        return@withContext bitmap
      }
    }

    val deferred: Deferred<Bitmap?>
    synchronized(ongoingProxyOperations) {
      val existing = ongoingProxyOperations[videoKey]
      if (existing != null && !existing.deferred.isCancelled) {
        existing.refCount++
        deferred = existing.deferred
      } else {
        val newDeferred = repositoryScope.async {
          try {
            proxyThumbnailSemaphore.withPermit {
              coroutineContext.ensureActive()
              synchronized(memoryCache) { memoryCache.get(videoKey) }?.let { return@withPermit it }
              if (diskFile.exists()) {
                decodeFileSafely(diskFile.absolutePath, dimension)?.let { bitmap ->
                  synchronized(memoryCache) {
                    memoryCache.put(videoKey, bitmap)
                    networkMemoryKeys.add(videoKey)
                  }
                  return@withPermit bitmap
                }
              }
              if (isNetworkThumbnailFailed(connection.id, path)) return@withPermit null

              coroutineContext.ensureActive()
              val ext = File(name).extension
              val mimeType = FileTypeUtils.getMimeTypeFromExtension(ext).takeIf { it != "*/*" } ?: "video/mp4"

              val targetDimension = maxOf(dimension, 512)
              var bitmap: Bitmap? = null
              val testExtractor = proxyExtractorForTesting
              if (testExtractor != null) {
                val tempVideo = Video(
                  id = path.hashCode().toLong(),
                  title = name,
                  displayName = name,
                  path = path,
                  uri = android.net.Uri.parse("http://127.0.0.1/$path"),
                  duration = 0,
                  durationFormatted = "",
                  size = size,
                  sizeFormatted = "",
                  dateModified = 0,
                  dateAdded = 0,
                  mimeType = mimeType,
                  bucketId = "",
                  bucketDisplayName = "",
                  width = 0,
                  height = 0,
                  fps = 0f,
                  resolution = "",
                )
                bitmap = testExtractor(tempVideo, targetDimension)
              } else {
                val proxy = NetworkStreamingProxy.getInstance()
                val streamId = "thumb_${connection.id}_${path.hashCode()}_${System.nanoTime()}"

                val localUrl = proxy.registerStream(
                  streamId = streamId,
                  connection = connection,
                  filePath = path,
                  fileSize = size,
                  mimeType = mimeType,
                )

                try {
                  coroutineContext.ensureActive()
                  val tempVideo = Video(
                    id = path.hashCode().toLong(),
                    title = name,
                    displayName = name,
                    path = localUrl,
                    uri = android.net.Uri.parse(localUrl),
                    duration = 0,
                    durationFormatted = "",
                    size = size,
                    sizeFormatted = "",
                    dateModified = 0,
                    dateAdded = 0,
                    mimeType = mimeType,
                    bucketId = "",
                    bucketDisplayName = "",
                    width = 0,
                    height = 0,
                    fps = 0f,
                    resolution = "",
                  )

                  bitmap = generateWithMediaMetadataRetriever(tempVideo, targetDimension)
                  if (bitmap == null) {
                    coroutineContext.ensureActive()
                    bitmap = generateWithFastThumbnails(tempVideo, targetDimension)
                  }
                } finally {
                  proxy.unregisterStream(streamId)
                }
              }

              if (bitmap != null) {
                synchronized(memoryCache) {
                  memoryCache.put(videoKey, bitmap)
                  networkMemoryKeys.add(videoKey)
                }
                runCatching {
                  connectionDiskDir.mkdirs()
                  FileOutputStream(diskFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, diskJpegQuality, out)
                  }
                }
              } else {
                markNetworkThumbnailFailed(connection.id, path)
              }

              bitmap
            }
          } finally {
            synchronized(ongoingProxyOperations) {
              val current = ongoingProxyOperations[videoKey]
              if (current != null && current.deferred == coroutineContext[Job]) {
                ongoingProxyOperations.remove(videoKey)
              }
            }
          }
        }
        ongoingProxyOperations[videoKey] = ProxyRequest(newDeferred, 1)
        deferred = newDeferred
      }
    }

    return@withContext try {
      deferred.await()
    } catch (e: CancellationException) {
      synchronized(ongoingProxyOperations) {
        val state = ongoingProxyOperations[videoKey]
        if (state != null && state.deferred == deferred) {
          state.refCount--
          if (state.refCount <= 0) {
            ongoingProxyOperations.remove(videoKey)
            state.deferred.cancel()
          }
        }
      }
      throw e
    } catch (e: Exception) {
      null
    }
  }

  companion object {
    /**
     * Constructs the deterministic cache key for proxy-stream thumbnails.
     */
    fun networkProxyThumbnailKey(connectionId: Long, path: String, dimension: Int): String =
      "$connectionId|$path|networkProxy|$dimension"

    fun networkProxyThumbnailKey(path: String, dimension: Int): String =
      networkProxyThumbnailKey(0L, path, dimension)

    fun networkProxyThumbnailDiskKey(connectionId: Long, path: String): String =
      "$connectionId|$path|networkProxy"

    fun networkProxyThumbnailDiskKey(path: String): String =
      networkProxyThumbnailDiskKey(0L, path)
  }
}
