package xyz.mpv.rex.database.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import xyz.mpv.rex.database.dao.HybridMediaDao
import xyz.mpv.rex.database.entities.HybridMediaEntity
import xyz.mpv.rex.database.entities.HybridMediaRootEntity
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.media.model.MediaFolder
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.storage.FileFilterUtils
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.utils.storage.StorageVolumeUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HybridIndexScanState(
  val isScanning: Boolean = false,
  val isUserInitiated: Boolean = false,
  val rootName: String? = null,
  val scannedItems: Int = 0,
  val completedRoots: Int = 0,
  val totalRoots: Int = 0,
  val error: String? = null,
)

/**
 * Persistent discovery index combining MediaStore with explicitly authorized
 * direct-file and SAF roots. Physical discovery is generation-safe and
 * single-flight; presentation data is derived from persisted cheap facts.
 */
class HybridMediaIndexRepository(
  private val context: Context,
  private val dao: HybridMediaDao,
  private val browserPreferences: BrowserPreferences,
  private val foldersPreferences: FoldersPreferences,
  private val metadataCacheRepository: VideoMetadataCacheRepository? = null,
) {
  private val scanMutex = Mutex()
  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var enrichmentJob: Job? = null
  private val _scanState = MutableStateFlow(HybridIndexScanState())
  val scanState: StateFlow<HybridIndexScanState> = _scanState.asStateFlow()

  init {
    repositoryScope.launch {
      foldersPreferences.blacklistedFolders.changes().drop(1).collect { blacklist ->
        purgeExcludedFolders(blacklist)
        ensureFresh(force = true, userInitiated = false)
      }
    }
  }

  suspend fun purgeExcludedFolders(excludedPaths: Set<String> = foldersPreferences.blacklistedFolders.get()) = withContext(Dispatchers.IO) {
    if (excludedPaths.isEmpty()) return@withContext
    Log.d(TAG, "Purging excluded folders from Room index: $excludedPaths")
    excludedPaths.forEach { rawPath ->
      val normalized = normalizePath(File(rawPath))
      val escaped = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
      dao.deleteByPathPrefix(normalized, "$escaped/%")
    }
  }

  suspend fun removeEntries(paths: List<String>) = withContext(Dispatchers.IO) {
    if (paths.isEmpty()) return@withContext
    val normalized = paths.map { normalizePath(File(it)) }
    dao.deleteByLocations((normalized + paths).distinct())
  }

  suspend fun ensureFresh(force: Boolean = false, userInitiated: Boolean = false) = withContext(Dispatchers.IO) {
    Log.d(TAG, "ensureFresh requested: force=$force, userInitiated=$userInitiated, activeScan=${activeScanJob?.isActive}")
    if (!force && !scanMutex.isLocked) {
      val requiredRoots = resolveRoots()
      val rootsById = dao.getRoots().associateBy { it.identity }
      val now = System.currentTimeMillis()
      val stale = requiredRoots.any { root ->
        val stored = rootsById[root.identity]
        stored == null || !stored.available || now - stored.lastCompletedAt > INDEX_TTL_MS
      }
      if (dao.getAvailableCount() > 0 && !stale) {
        Log.d(TAG, "ensureFresh skipped: index is already fresh (availableCount=${dao.getAvailableCount()})")
        return@withContext
      }
    }

    val runningJob = activeScanJob
    if (runningJob?.isActive == true) {
      if (force) {
        cancelScan()
      }
      runCatching { runningJob.join() }
      if (!force) return@withContext
    }

    scanMutex.withLock {
      val requiredRoots = resolveRoots()
      val rootsById = dao.getRoots().associateBy { it.identity }
      val now = System.currentTimeMillis()
      val stale = requiredRoots.any { root ->
        val stored = rootsById[root.identity]
        stored == null || !stored.available || now - stored.lastCompletedAt > INDEX_TTL_MS
      }
      if (force || dao.getAvailableCount() == 0 || stale) {
        performRefresh(requiredRoots, userInitiated = userInitiated)
      }
    }
  }

  suspend fun ensureFreshIfEmpty() = withContext(Dispatchers.IO) {
    val count = dao.getAvailableCount()
    Log.d(TAG, "ensureFreshIfEmpty: availableCount=$count")
    if (count == 0) {
      ensureFresh()
    }
  }

  suspend fun refresh(userInitiated: Boolean = false) = scanMutex.withLock {
    performRefresh(resolveRoots(), userInitiated = userInitiated)
  }

  suspend fun refreshMediaStore(userInitiated: Boolean = false) = scanMutex.withLock {
    performRefresh(listOf(mediaStoreRoot()), reconcileRootSet = false, userInitiated = userInitiated)
  }

  fun cancelScan() {
    Log.d(TAG, "cancelScan called")
    activeScanJob?.cancel()
  }

  private suspend fun performRefresh(
    roots: List<ScanRoot>,
    reconcileRootSet: Boolean = true,
    userInitiated: Boolean = false,
  ) {
    activeScanJob = currentCoroutineContext()[Job]
    val generation = System.currentTimeMillis()
    var scannedItems = 0
    var completedRoots = 0
    var lastError: String? = null
    Log.d(TAG, "performRefresh started: userInitiated=$userInitiated, roots=${roots.map { it.displayName }}")

    _scanState.value = HybridIndexScanState(
      isScanning = true,
      isUserInitiated = userInitiated,
      totalRoots = roots.size,
    )

    if (reconcileRootSet) {
      val desiredRootIds = roots.mapTo(mutableSetOf()) { it.identity }
      dao.getRoots()
        .filter { it.identity !in desiredRootIds && it.sourceType != SOURCE_MEDIA_STORE }
        .forEach {
          dao.markRootUnavailable(it.identity, "Root is no longer authorized or mounted")
          dao.markMediaUnavailable(it.identity)
        }
    }

    try {
      for (root in roots) {
        currentCoroutineContext().ensureActive()
        _scanState.value = _scanState.value.copy(rootName = root.displayName)

        dao.upsertRoot(
          HybridMediaRootEntity(
            identity = root.identity,
            sourceType = root.sourceType,
            location = root.location,
            displayName = root.displayName,
            available = true,
            lastGeneration = generation,
          ),
        )

        try {
          val count = when (root.sourceType) {
            SOURCE_DIRECT -> scanDirectRoot(root, generation, scannedItems)
            SOURCE_SAF -> scanSafRoot(root, generation, scannedItems)
            SOURCE_MEDIA_STORE -> scanMediaStore(root, generation, scannedItems)
            else -> 0
          }
          scannedItems += count
          dao.deleteStaleForCompletedGeneration(root.identity, generation)
          dao.markRootComplete(root.identity, generation, System.currentTimeMillis())
          completedRoots++
          _scanState.value = _scanState.value.copy(
            scannedItems = scannedItems,
            completedRoots = completedRoots,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          lastError = e.message ?: e.javaClass.simpleName
          Log.e(TAG, "Failed to scan root ${root.location}", e)
          dao.markRootUnavailable(root.identity, lastError)
          dao.markMediaUnavailable(root.identity)
          _scanState.value = _scanState.value.copy(error = lastError)
        }
      }
    } finally {
      activeScanJob = null
      _scanState.value = _scanState.value.copy(
        isScanning = false,
        isUserInitiated = false,
        rootName = null,
        scannedItems = scannedItems,
        completedRoots = completedRoots,
        error = lastError,
      )
      Log.d(TAG, "performRefresh completed in ${System.currentTimeMillis() - generation}ms, scannedItems=$scannedItems, completedRoots=$completedRoots, error=$lastError")
      startBackgroundEnrichment()
    }
  }

  suspend fun getNoMediaCount(): Int = withContext(Dispatchers.IO) {
    dao.getNoMediaCount()
  }

  /**
   * Starts background worker to enrich items with PENDING metadata status using bounded concurrency.
   */
  fun startBackgroundEnrichment() {
    enrichmentJob?.cancel()
    enrichmentJob = repositoryScope.launch {
      while (isActive) {
        val pending = dao.getPendingMetadataItems(16)
        if (pending.isEmpty()) break
        enrichPendingBatch(pending)
      }
    }
  }

  /**
   * Prioritizes metadata enrichment for files inside a specific folder when browsed by the user.
   */
  suspend fun enrichFolderMetadata(parentIdentity: String) = withContext(Dispatchers.IO) {
    val items = dao.getAvailableMedia(includeNoMedia = true)
      .filter { it.parentIdentity == parentIdentity && it.metadataState == "PENDING" }
    if (items.isNotEmpty()) {
      enrichPendingBatch(items)
    }
  }

  private suspend fun enrichPendingBatch(items: List<HybridMediaEntity>) = withContext(Dispatchers.IO) {
    val cacheRepo = metadataCacheRepository ?: return@withContext
    items.chunked(PARALLEL_ENRICHMENT_LIMIT).forEach { batch ->
      coroutineScope {
        batch.map { item ->
          async {
            currentCoroutineContext().ensureActive()
            val file = File(item.location)
            val uri = when {
              item.location.startsWith("content:") -> item.location.toUri()
              item.sourceType == SOURCE_SAF -> item.location.toUri()
              else -> Uri.fromFile(file)
            }

            val metadata = runCatching {
              cacheRepo.getOrExtractMetadata(
                file = file,
                uri = uri,
                displayName = item.displayName,
              )
            }.getOrNull()

            if (metadata != null) {
              dao.updateMediaMetadata(
                identity = item.identity,
                duration = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                rotation = metadata.rotation,
                metadataState = "INDEXED",
              )
            } else {
              dao.updateMediaMetadata(
                identity = item.identity,
                duration = 0,
                width = 0,
                height = 0,
                rotation = 0,
                metadataState = "FAILED",
              )
            }
          }
        }.awaitAll()
      }
    }
  }


  suspend fun getFlatFolders(
    playbackStates: List<PlaybackStateEntity>,
    thresholdDays: Int,
    watchedThreshold: Int,
    includeNoMedia: Boolean = browserPreferences.includeNoMediaContent.get(),
  ): List<MediaFolder> = withContext(Dispatchers.IO) {
    val items = dao.getAvailableMedia(includeNoMedia)
    val stateByIdentity = playbackStates.associateBy { it.mediaTitle }
    val thresholdMillis = thresholdDays * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()

    items.groupBy { it.parentIdentity }.map { (parent, media) ->
      val counts = presentationCounts(media, stateByIdentity, watchedThreshold, thresholdMillis, now)
      MediaFolder(
        id = parent,
        name = media.first().parentDisplayName,
        path = parent,
        videoCount = media.count { !it.isAudio },
        audioCount = media.count { it.isAudio },
        totalSize = media.sumOf { it.size },
        totalDuration = media.sumOf { it.duration },
        lastModified = media.maxOfOrNull { it.dateModified } ?: 0,
        hasSubfolders = false,
        isRecursive = false,
        newCount = counts.first,
        unwatchedVideoCount = counts.second,
      )
    }.sortedBy { it.name.lowercase() }
  }

  suspend fun getFoldersInDirectory(
    parentPath: String,
    playbackStates: List<PlaybackStateEntity>,
    thresholdDays: Int,
    watchedThreshold: Int,
    includeNoMedia: Boolean = browserPreferences.includeNoMediaContent.get(),
  ): List<MediaFolder> = withContext(Dispatchers.IO) {
    val tree = buildFileTree(
      dao.getAvailableMedia(includeNoMedia),
      playbackStates,
      thresholdDays,
      watchedThreshold,
    )
    getEffectiveChildren(parentPath, tree)
      .map { it.toMediaFolder(recursive = true) }
      .sortedBy { it.name.lowercase() }
  }

  suspend fun getRecursiveFolder(
    path: String,
    playbackStates: List<PlaybackStateEntity>,
    thresholdDays: Int,
    watchedThreshold: Int,
    includeNoMedia: Boolean = browserPreferences.includeNoMediaContent.get(),
  ): MediaFolder? = withContext(Dispatchers.IO) {
    buildFileTree(
      dao.getAvailableMedia(includeNoMedia),
      playbackStates,
      thresholdDays,
      watchedThreshold,
    )[normalizePath(File(path))]?.toMediaFolder(recursive = true)
  }

  suspend fun getVideosInFolder(
    parentIdentity: String,
    includeNoMedia: Boolean = browserPreferences.includeNoMediaContent.get(),
  ): List<Video> = withContext(Dispatchers.IO) {
    dao.getAvailableMedia(includeNoMedia)
      .asSequence()
      .filter { it.parentIdentity == parentIdentity }
      .map { it.toVideo() }
      .sortedBy { it.displayName.lowercase() }
      .toList()
  }

  suspend fun getVideoByLocation(path: String): Video? = withContext(Dispatchers.IO) {
    val normalized = path.removePrefix("file://")
    (dao.getMediaByLocation(normalized) ?: dao.getMediaByLocation(path))?.toVideo()
  }

  suspend fun searchMedia(
    query: String,
    parentPath: String? = null,
    includeNoMedia: Boolean = browserPreferences.includeNoMediaContent.get(),
  ): List<Video> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val entities = if (parentPath.isNullOrBlank()) {
      dao.searchMedia(query, includeNoMedia)
    } else {
      val prefix = "$parentPath/%"
      dao.searchMediaInPath(query, parentPath, prefix, includeNoMedia)
    }
    entities.map { it.toVideo() }
  }

  suspend fun clear() {
    dao.clearMedia()
    dao.clearRoots()
  }

  @Volatile
  private var activeScanJob: Job? = null

  private fun resolveRoots(): List<ScanRoot> {
    val roots = mutableListOf<ScanRoot>()

    if (hasDirectStorageAccess()) {
      StorageVolumeUtils.getAllStorageVolumes(context).forEach { volume ->
        val path = StorageVolumeUtils.getVolumePath(volume) ?: return@forEach
        val directory = File(path)
        if (directory.exists() && directory.canRead()) {
          val normalized = normalizePath(directory)
          roots += ScanRoot(
            identity = "direct:$normalized",
            sourceType = SOURCE_DIRECT,
            location = normalized,
            displayName = volume.getDescription(context),
          )
        }
      }
    }

    val persistedReadUris = context.contentResolver.persistedUriPermissions
      .filter { it.isReadPermission }
      .mapTo(mutableSetOf()) { it.uri.toString() }

    foldersPreferences.libraryScanRoots.get().forEach { uri ->
      roots += ScanRoot(
        identity = "saf:$uri",
        sourceType = SOURCE_SAF,
        location = uri,
        displayName = DocumentFile.fromTreeUri(context, uri.toUri())?.name ?: "Selected folder",
        authorized = uri in persistedReadUris,
      )
    }

    // MediaStore runs last so its valid metadata enriches matching direct files
    // while the direct pass remains responsible for .nomedia classification.
    roots += mediaStoreRoot()
    return roots.distinctBy { it.identity }
  }

  private fun mediaStoreRoot() = ScanRoot(
    identity = MEDIA_STORE_ROOT,
    sourceType = SOURCE_MEDIA_STORE,
    location = MediaStore.Files.getContentUri("external").toString(),
    displayName = "Android media library",
  )

  private fun hasDirectStorageAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

  private suspend fun scanDirectRoot(
    root: ScanRoot,
    generation: Long,
    alreadyScanned: Int,
  ): Int {
    val directory = File(root.location)
    if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
      throw IllegalStateException("Direct root is unavailable")
    }

    val includeNoMedia = browserPreferences.includeNoMediaContent.get()
    val blacklist = foldersPreferences.blacklistedFolders.get()
      .mapTo(mutableSetOf()) { normalizePath(File(it)) }
    val batch = mutableListOf<HybridMediaEntity>()
    var count = 0

    suspend fun flush() {
      if (batch.isNotEmpty()) {
        dao.upsertMedia(batch.toList())
        batch.clear()
      }
    }

    suspend fun walk(folder: File, inheritedNoMedia: Boolean, depth: Int) {
      currentCoroutineContext().ensureActive()
      if (depth > MAX_DEPTH) return
      val normalizedFolder = normalizePath(folder)
      if (blacklist.any { normalizedFolder == it || normalizedFolder.startsWith("$it/") }) {
        Log.d(TAG, "Skipping excluded directory tree: $normalizedFolder")
        return
      }

      val entries = folder.listFiles() ?: return
      val noMedia = inheritedNoMedia || entries.any { it.isFile && it.name == ".nomedia" }
      if (!includeNoMedia && noMedia) return
      for (entry in entries) {
        currentCoroutineContext().ensureActive()
        if (entry.isDirectory) {
          if (!FileFilterUtils.shouldSkipFolderName(entry.name)) {
            walk(entry, noMedia, depth + 1)
          }
          continue
        }
        if (!entry.isFile || FileFilterUtils.shouldSkipFile(entry)) continue

        val isVideo = FileTypeUtils.isVideoFile(entry)
        val isAudio = FileTypeUtils.isAudioFile(entry)
        if (!isVideo && !isAudio) continue

        val location = normalizePath(entry)
        batch += HybridMediaEntity(
          identity = "file:$location",
          sourceType = SOURCE_DIRECT,
          sourceRoot = root.identity,
          location = location,
          parentIdentity = normalizedFolder,
          parentDisplayName = folder.name,
          displayName = entry.name,
          mimeType = FileTypeUtils.getMimeTypeFromExtension(entry.extension.lowercase()),
          size = entry.length(),
          dateModified = entry.lastModified() / 1000,
          isAudio = isAudio,
          isNoMedia = noMedia,
          available = true,
          lastSeenGeneration = generation,
        )
        count++
        if (batch.size >= BATCH_SIZE) {
          flush()
          updateProgress(alreadyScanned + count)
        }
      }
    }

    walk(directory, false, 0)
    flush()
    return count
  }

  private suspend fun scanSafRoot(
    root: ScanRoot,
    generation: Long,
    alreadyScanned: Int,
  ): Int {
    if (!root.authorized) throw SecurityException("Persisted folder permission was revoked")
    val documentRoot = DocumentFile.fromTreeUri(context, root.location.toUri())
      ?: throw IllegalStateException("Selected folder is unavailable")
    if (!documentRoot.exists() || !documentRoot.canRead()) {
      throw SecurityException("Selected folder cannot be read")
    }

    val includeNoMedia = browserPreferences.includeNoMediaContent.get()
    val blacklist = foldersPreferences.blacklistedFolders.get()
    val batch = mutableListOf<HybridMediaEntity>()
    var count = 0

    suspend fun flush() {
      if (batch.isNotEmpty()) {
        dao.upsertMedia(batch.toList())
        batch.clear()
      }
    }

    suspend fun walk(folder: DocumentFile, inheritedNoMedia: Boolean, depth: Int) {
      currentCoroutineContext().ensureActive()
      if (depth > MAX_DEPTH) return
      val folderUriStr = folder.uri.toString()
      if (blacklist.any { excluded ->
          folderUriStr == excluded || folderUriStr.startsWith("$excluded/") ||
          (excluded.startsWith("/") && Uri.decode(folderUriStr).contains(excluded))
        }
      ) {
        Log.d(TAG, "Skipping excluded SAF directory tree: $folderUriStr")
        return
      }
      val entries = folder.listFiles()
      val noMedia = inheritedNoMedia || entries.any { it.isFile && it.name == ".nomedia" }
      if (!includeNoMedia && noMedia) return
      val parentIdentity = folder.uri.toString()
      val parentName = folder.name ?: "Folder"

      for (entry in entries) {
        currentCoroutineContext().ensureActive()
        val name = entry.name ?: continue
        if (entry.isDirectory) {
          if (!FileFilterUtils.shouldSkipFolderName(name)) {
            walk(entry, noMedia, depth + 1)
          }
          continue
        }
        if (!entry.isFile || name.startsWith(".")) continue

        val extension = name.substringAfterLast('.', "").lowercase()
        val mimeType = entry.type ?: FileTypeUtils.getMimeTypeFromExtension(extension)
        val isVideo = mimeType.startsWith("video/") || extension in FileTypeUtils.VIDEO_EXTENSIONS
        val isAudio = mimeType.startsWith("audio/") || extension in FileTypeUtils.AUDIO_EXTENSIONS
        if (!isVideo && !isAudio) continue

        val location = entry.uri.toString()
        batch += HybridMediaEntity(
          identity = "saf:$location",
          sourceType = SOURCE_SAF,
          sourceRoot = root.identity,
          location = location,
          parentIdentity = parentIdentity,
          parentDisplayName = parentName,
          displayName = name,
          mimeType = mimeType,
          size = entry.length(),
          dateModified = entry.lastModified() / 1000,
          isAudio = isAudio,
          isNoMedia = noMedia,
          available = true,
          lastSeenGeneration = generation,
        )
        count++
        if (batch.size >= BATCH_SIZE) {
          flush()
          updateProgress(alreadyScanned + count)
        }
      }
    }

    walk(documentRoot, false, 0)
    flush()
    return count
  }

  private suspend fun scanMediaStore(
    root: ScanRoot,
    generation: Long,
    alreadyScanned: Int,
  ): Int {
    val blacklist = foldersPreferences.blacklistedFolders.get()
      .mapTo(mutableSetOf()) { normalizePath(File(it)) }
    val batch = mutableListOf<HybridMediaEntity>()
    var count = 0

    suspend fun flush() {
      if (batch.isNotEmpty()) {
        dao.upsertMedia(batch.toList())
        batch.clear()
      }
    }

    suspend fun query(uri: Uri, isAudio: Boolean) {
      val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DATA)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DURATION)
        if (!isAudio) {
          add(MediaStore.MediaColumns.WIDTH)
          add(MediaStore.MediaColumns.HEIGHT)
        }
      }.toTypedArray()
      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

        while (cursor.moveToNext()) {
          currentCoroutineContext().ensureActive()
          val id = cursor.getLong(idColumn)
          val displayName = cursor.getString(nameColumn) ?: continue
          val path = if (dataColumn >= 0) cursor.getString(dataColumn) else null
          val file = path?.let(::File)
          if (file != null && !file.exists()) continue

          val contentUri = Uri.withAppendedPath(uri, id.toString()).toString()
          val location = file?.let(::normalizePath) ?: contentUri
          val parent = file?.parentFile?.let(::normalizePath) ?: contentUri.substringBeforeLast('/')
          if (blacklist.any { location == it || location.startsWith("$it/") || parent == it || parent.startsWith("$it/") }) {
            Log.d(TAG, "Skipping excluded MediaStore item: $location")
            continue
          }
          val parentName = file?.parentFile?.name ?: "MediaStore"
          batch += HybridMediaEntity(
            identity = if (file != null) "file:$location" else "content:$contentUri",
            sourceType = SOURCE_MEDIA_STORE,
            sourceRoot = root.identity,
            location = location,
            parentIdentity = parent,
            parentDisplayName = parentName,
            displayName = displayName,
            mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: if (isAudio) "audio/*" else "video/*" else if (isAudio) "audio/*" else "video/*",
            size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0,
            dateModified = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0,
            isAudio = isAudio,
            isNoMedia = file?.parentFile?.let(FileFilterUtils::isWithinNoMediaBoundary) == true,
            duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0,
            width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0,
            height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0,
            metadataState = "INDEXED",
            available = true,
            lastSeenGeneration = generation,
          )
          count++
          if (batch.size >= BATCH_SIZE) {
            flush()
            updateProgress(alreadyScanned + count)
          }
        }
      }
    }

    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false)
    val canReadAudio =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
          PackageManager.PERMISSION_GRANTED ||
        hasDirectStorageAccess()
    if (canReadAudio) {
      query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
    }
    flush()
    return count
  }

  private fun updateProgress(scanned: Int) {
    _scanState.value = _scanState.value.copy(scannedItems = scanned)
  }

  private fun presentationCounts(
    items: List<HybridMediaEntity>,
    stateByIdentity: Map<String, PlaybackStateEntity>,
    watchedThreshold: Int,
    thresholdMillis: Long,
    now: Long,
  ): Pair<Int, Int> {
    val showAudioFiles = browserPreferences.showAudioFiles.get()
    var newCount = 0
    var unwatchedCount = 0
    items.forEach { item ->
      if (!showAudioFiles && item.isAudio) return@forEach
      val fileName = java.io.File(item.location).name
      val state = stateByIdentity[item.location] ?: stateByIdentity[item.displayName] ?: stateByIdentity[fileName]
      val watched = state?.hasBeenWatched == true ||
        (state != null && item.duration > 0 && state.timeRemaining >= 0 &&
          ((item.duration / 1000 - state.timeRemaining).toFloat() / (item.duration / 1000).coerceAtLeast(1)) >= watchedThreshold / 100f)
      if (!watched) unwatchedCount++
      if ((state == null && now - item.dateModified * 1000 <= thresholdMillis) || state?.timeRemaining == -1) {
        newCount++
      }
    }
    return newCount to unwatchedCount
  }

  private fun buildFileTree(
    items: List<HybridMediaEntity>,
    playbackStates: List<PlaybackStateEntity>,
    thresholdDays: Int,
    watchedThreshold: Int,
  ): MutableMap<String, FolderStats> {
    val fileItems = items.filter { it.location.startsWith("/") }
    val stateByIdentity = playbackStates.associateBy { it.mediaTitle }
    val thresholdMillis = thresholdDays * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()
    val tree = mutableMapOf<String, FolderStats>()

    fileItems.groupBy { it.parentIdentity }.forEach { (path, directItems) ->
      val counts = presentationCounts(directItems, stateByIdentity, watchedThreshold, thresholdMillis, now)
      val directVideos = directItems.count { !it.isAudio }
      val directAudios = directItems.count { it.isAudio }
      tree[path] = FolderStats(
        path = path,
        name = File(path).name,
        directVideoCount = directVideos,
        directAudioCount = directAudios,
        videoCount = directVideos,
        audioCount = directAudios,
        totalSize = directItems.sumOf { it.size },
        totalDuration = directItems.sumOf { it.duration },
        lastModified = directItems.maxOfOrNull { it.dateModified } ?: 0,
        newCount = counts.first,
        unwatchedCount = counts.second,
      )
    }

    val ancestors = mutableSetOf<String>()
    tree.keys.forEach { path ->
      var parent = File(path).parent
      while (parent != null && parent.length > 1) {
        ancestors += parent
        parent = File(parent).parent
      }
    }
    ancestors.forEach { path ->
      tree.putIfAbsent(path, FolderStats(path = path, name = File(path).name))
    }

    tree.keys.sortedByDescending { it.length }.forEach { path ->
      val node = tree[path] ?: return@forEach
      val parentPath = File(path).parent ?: return@forEach
      val parent = tree[parentPath] ?: return@forEach
      parent.hasSubfolders = true
      parent.videoCount += node.videoCount
      parent.audioCount += node.audioCount
      parent.totalSize += node.totalSize
      parent.totalDuration += node.totalDuration
      parent.lastModified = maxOf(parent.lastModified, node.lastModified)
      parent.newCount += node.newCount
      parent.unwatchedCount += node.unwatchedCount
    }

    // SMART TREE FLATTENING:
    // A folder should be hidden (flattened) if it has NO direct media AND only has one child folder with media.
    // This brings nested folders forward in tree navigation.
    val sortedForFlattening = tree.keys.sortedBy { it.length }
    for (path in sortedForFlattening) {
      val node = tree[path] ?: continue
      if (node.directVideoCount > 0 || node.directAudioCount > 0) continue

      val childrenWithMedia = tree.values.filter {
        File(it.path).parent == path && (it.videoCount > 0 || it.audioCount > 0)
      }

      if (childrenWithMedia.size < 2) {
        val isStorageRoot = StorageVolumeUtils.isStorageRoot(context, path)
        if (!isStorageRoot) {
          node.isFlattened = true
        }
      }
    }

    return tree
  }

  private fun getEffectiveChildren(
    parentPath: String,
    allNodes: Map<String, FolderStats>,
    visited: Set<String> = emptySet(),
  ): List<FolderStats> {
    val normalizedParent = normalizePath(File(parentPath))
    val directChildren = allNodes.values.filter {
      File(it.path).parent?.let { p -> normalizePath(File(p)) } == normalizedParent
    }
    val result = mutableListOf<FolderStats>()
    for (child in directChildren) {
      if (child.path in visited) continue
      if (child.isFlattened) {
        result.addAll(getEffectiveChildren(child.path, allNodes, visited + child.path))
      } else {
        result.add(child)
      }
    }
    return result
  }

  private fun HybridMediaEntity.toVideo(): Video {
    val uri = when {
      location.startsWith("content:") -> location.toUri()
      sourceType == SOURCE_SAF -> location.toUri()
      else -> Uri.fromFile(File(location))
    }
    return Video(
      id = identity.hashCode().toLong(),
      title = displayName.substringBeforeLast('.'),
      displayName = displayName,
      path = location,
      uri = uri,
      duration = duration,
      durationFormatted = MediaFormatter.formatDuration(duration),
      size = size,
      sizeFormatted = MediaFormatter.formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateModified,
      mimeType = mimeType,
      bucketId = parentIdentity,
      bucketDisplayName = parentDisplayName,
      width = width,
      height = height,
      rotation = rotation,
      fps = 0f,
      resolution = if (isAudio) "" else MediaFormatter.formatResolution(width, height),
      isAudio = isAudio,
    )
  }

  private data class ScanRoot(
    val identity: String,
    val sourceType: String,
    val location: String,
    val displayName: String,
    val authorized: Boolean = true,
  )

  private data class FolderStats(
    val path: String,
    val name: String,
    val directVideoCount: Int = 0,
    val directAudioCount: Int = 0,
    var videoCount: Int = 0,
    var audioCount: Int = 0,
    var totalSize: Long = 0,
    var totalDuration: Long = 0,
    var lastModified: Long = 0,
    var hasSubfolders: Boolean = false,
    var newCount: Int = 0,
    var unwatchedCount: Int = 0,
    var isFlattened: Boolean = false,
  ) {
    fun toMediaFolder(recursive: Boolean) = MediaFolder(
      id = path,
      name = name,
      path = path,
      videoCount = videoCount,
      audioCount = audioCount,
      totalSize = totalSize,
      totalDuration = totalDuration,
      lastModified = lastModified,
      hasSubfolders = hasSubfolders,
      isRecursive = recursive,
      newCount = newCount,
      unwatchedVideoCount = unwatchedCount,
    )
  }

  companion object {
    private const val TAG = "HybridMediaIndex"
    private const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
    private const val SOURCE_DIRECT = "DIRECT_FILE"
    private const val SOURCE_SAF = "SAF"
    private const val MEDIA_STORE_ROOT = "mediastore:external"
    private const val BATCH_SIZE = 200
    private const val MAX_DEPTH = 64
    private const val INDEX_TTL_MS = 5 * 60 * 1000L
    private const val PARALLEL_ENRICHMENT_LIMIT = 4

    private fun normalizePath(file: File): String =
      runCatching { file.canonicalPath }.getOrElse { file.absoluteFile.normalize().path }
  }
}
