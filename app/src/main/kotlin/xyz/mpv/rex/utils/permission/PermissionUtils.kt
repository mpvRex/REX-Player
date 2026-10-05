package xyz.mpv.rex.utils.permission

import android.app.Activity
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.media.PlaybackStateOps
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simplified storage permission utilities with MANAGE_EXTERNAL_STORAGE support.
 */
object PermissionUtils {
  private const val FILE_ACCESS_TAG = "FileAccessRequest"
  
  /**
   * Check if storage permission is currently granted.
   */
  fun isStoragePermissionGranted(context: Context): Boolean =
    if (!BuildConfig.SCOPED_STORAGE_ONLY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      android.os.Environment.isExternalStorageManager()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_MEDIA_VIDEO,
      ) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
    }

  /**
   * Launch system storage permission request (All Files Access settings on Android 11+ non-playstore, or runtime permission).
   */
  fun requestStorageAccess(context: Context, onRequestPermission: () -> Unit) {
    if (BuildConfig.SCOPED_STORAGE_ONLY) {
      onRequestPermission()
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
          val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
          }
          context.startActivity(intent)
        } catch (_: Exception) {
          val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
          context.startActivity(intent)
        }
      } else {
        onRequestPermission()
      }
    }
  }
  
  private var mediaRequestLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
  private var resultOkCallback: () -> Unit = {}
  private var resultCancelledCallback: () -> Unit = {}

  /**
   * Set the media access launcher from MainActivity.
   */
  fun setMediaAccessLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
    mediaRequestLauncher = launcher
  }

  /**
   * Handle result from MainActivity's launcher callback.
   */
  fun handleMediaAccessResult(resultCode: Int) {
    if (resultCode == Activity.RESULT_OK) {
      resultOkCallback()
    } else {
      resultCancelledCallback()
    }
  }

  suspend fun requestScopedWriteAccess(
    context: Context,
    uris: List<Uri>,
  ): Boolean = requestWriteAccess(context, uris)

  suspend fun requestScopedDeleteAccess(
    context: Context,
    uris: List<Uri>,
  ): Boolean = requestDeleteAccess(context, uris)

  private suspend fun requestWriteAccess(
    context: Context,
    uris: List<Uri>,
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return true
    return withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        val launcher = mediaRequestLauncher ?: run {
          continuation.resumeWith(Result.success(false))
          return@suspendCancellableCoroutine
        }

        resultOkCallback = { continuation.resumeWith(Result.success(true)) }
        resultCancelledCallback = { continuation.resumeWith(Result.success(false)) }
        
        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, uris)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent).build())
      }
    }
  }

  private suspend fun requestDeleteAccess(
    context: Context,
    uris: List<Uri>,
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return true
    return withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        val launcher = mediaRequestLauncher ?: run {
          continuation.resumeWith(Result.success(false))
          return@suspendCancellableCoroutine
        }

        resultOkCallback = { continuation.resumeWith(Result.success(true)) }
        resultCancelledCallback = { continuation.resumeWith(Result.success(false)) }
        
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent).build())
      }
    }
  }
  /**
   * Returns READ_EXTERNAL_STORAGE permission for all Android versions.
   * On Android 11+, MANAGE_EXTERNAL_STORAGE provides full file access.
   */
  fun getStoragePermission(): String =
    when {
      Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
        // Android 9 and below need WRITE permission to create folders/files (e.g., mpvsnaps)
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
      }

      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        // Android 13+: request media-specific permission
        android.Manifest.permission.READ_MEDIA_VIDEO
      }

      else -> android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

  /**
   * Creates a permission state for storage access.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun rememberStoragePermissionState(): PermissionState = rememberPermissionState(getStoragePermission())

  /**
   * Handles storage permission and invokes [onPermissionGranted] when granted.
   * On Android 11+, also checks MANAGE_EXTERNAL_STORAGE permission.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun handleStoragePermission(onPermissionGranted: () -> Unit): PermissionState {
    val permissionState = rememberStoragePermissionState()
    val context = LocalContext.current
    var lifecycleTrigger by remember { mutableIntStateOf(0) }

    // Re-check permission when app resumes from Settings
    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            lifecycleTrigger++
          }
        }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose {
        lifecycleOwner?.lifecycle?.removeObserver(observer)
      }
    }

    // Wrap permission state to consider MANAGE_EXTERNAL_STORAGE on Android 11+
    val effectivePermissionState =
      remember(permissionState.status, lifecycleTrigger) {
        if (!BuildConfig.SCOPED_STORAGE_ONLY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          if (android.os.Environment.isExternalStorageManager()) {
            object : PermissionState {
              override val permission = permissionState.permission
              override val status = PermissionStatus.Granted

              override fun launchPermissionRequest() = permissionState.launchPermissionRequest()
            }
          } else {
            object : PermissionState {
              override val permission = permissionState.permission
              override val status = PermissionStatus.Denied(false)

              override fun launchPermissionRequest() = permissionState.launchPermissionRequest()
            }
          }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
          android.os.Environment.isExternalStorageManager()
        ) {
          object : PermissionState {
            override val permission = permissionState.permission
            override val status = PermissionStatus.Granted

            override fun launchPermissionRequest() = permissionState.launchPermissionRequest()
          }
        } else {
          permissionState
        }
      }

    LaunchedEffect(effectivePermissionState.status) {
      if (effectivePermissionState.status == PermissionStatus.Granted) {
        onPermissionGranted()
      }
    }

    return effectivePermissionState
  }

  // --------------------------------------------------------------------------
  // Storage operations (automatically uses scoped storage or direct file access)
  // --------------------------------------------------------------------------

  object StorageOps {
    private const val TAG = "StorageOps"

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is available and granted
     */
    private fun hasManageStoragePermission(): Boolean =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
      } else {
        true // Pre-Android 11 devices don't need this permission
      }

    /**
     * Delete videos using direct file operations (requires MANAGE_EXTERNAL_STORAGE on Android 11+)
     */
    suspend fun deleteVideos(
      context: Context,
      videos: List<Video>,
    ): Pair<Int, Int> =
      withContext(Dispatchers.IO) {
        if (!BuildConfig.SCOPED_STORAGE_ONLY || hasManageStoragePermission()) {
          deleteVideosDirectly(context, videos)
        } else {
          deleteVideosScoped(context, videos)
        }
      }

    /**
     * Delete videos using direct file operations (requires MANAGE_EXTERNAL_STORAGE on Android 11+)
     */
    private suspend fun deleteVideosDirectly(context: Context, videos: List<Video>): Pair<Int, Int> =
      withContext(Dispatchers.IO) {
        var deleted = 0
        var failed = 0
        val deletedPaths = mutableListOf<String>()

        for (video in videos) {
          try {
            val file = File(video.path)
            if (file.exists() && file.delete()) {
              deleted++
              deletedPaths.add(video.path)
              RecentlyPlayedOps.onVideoDeleted(video.path)
              PlaybackStateOps.onVideoDeleted(video.path)

              runCatching {
                if (video.uri.scheme == "content") {
                  context.contentResolver.delete(video.uri, null, null)
                } else {
                  context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(video.path)
                  )
                }
              }
              android.media.MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
              Log.d(TAG, "✓ Deleted: ${video.displayName}")
            } else {
              failed++
              Log.w(TAG, "✗ Failed to delete: ${video.displayName}")
            }
          } catch (e: Exception) {
            failed++
            Log.e(TAG, "✗ Error deleting ${video.displayName}", e)
          }
        }

        // Notify that media library has changed and purge from index
        if (deleted > 0) {
          runCatching {
            val hybridIndex = org.koin.core.context.GlobalContext.get().getOrNull<xyz.mpv.rex.database.repository.HybridMediaIndexRepository>()
            hybridIndex?.removeEntries(deletedPaths)
          }
          MediaLibraryEvents.notifyChanged()
        }

        Pair(deleted, failed)
      }

    /**
     * Delete videos via scoped storage (Play Store flavor / no MANAGE_EXTERNAL_STORAGE)
     */
    private suspend fun deleteVideosScoped(
      context: Context,
      videos: List<Video>,
    ): Pair<Int, Int> =
      withContext(Dispatchers.IO) {
        var deleted = 0
        var failed = 0
        val deletedPaths = mutableListOf<String>()

        val contentVideos = videos.filter { it.uri.scheme == "content" }
        val fileVideos = videos.filter { it.uri.scheme != "content" }

        if (contentVideos.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val granted = requestDeleteAccess(context, contentVideos.map { it.uri })
          if (granted) {
            contentVideos.forEach { video ->
              deleted++
              deletedPaths.add(video.path)
              RecentlyPlayedOps.onVideoDeleted(video.path)
              PlaybackStateOps.onVideoDeleted(video.path)
              Log.d(TAG, "✓ Deleted (scoped request): ${video.displayName}")
            }
          } else {
            failed += contentVideos.size
            Log.w(TAG, "✗ Delete request denied/cancelled for ${contentVideos.size} item(s)")
          }
        } else {
          for (video in contentVideos) {
            try {
              val rows = context.contentResolver.delete(video.uri, null, null)
              if (rows > 0) {
                deleted++
                deletedPaths.add(video.path)
                RecentlyPlayedOps.onVideoDeleted(video.path)
                PlaybackStateOps.onVideoDeleted(video.path)
                Log.d(TAG, "✓ Deleted (scoped): ${video.displayName}")
              } else {
                failed++
                Log.w(TAG, "✗ Failed to delete (scoped): ${video.displayName}")
              }
            } catch (e: Exception) {
              failed++
              Log.e(TAG, "✗ Error deleting (scoped) ${video.displayName}", e)
            }
          }
        }

        for (video in fileVideos) {
          try {
            val file = File(video.path)
            if (!file.exists() || file.delete()) {
              deleted++
              deletedPaths.add(video.path)
              RecentlyPlayedOps.onVideoDeleted(video.path)
              PlaybackStateOps.onVideoDeleted(video.path)
              android.media.MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
              Log.d(TAG, "✓ Deleted (file fallback): ${video.displayName}")
            } else {
              failed++
              Log.w(TAG, "✗ Failed to delete (file fallback): ${video.displayName}")
            }
          } catch (e: Exception) {
            failed++
            Log.e(TAG, "✗ Error deleting (file fallback) ${video.displayName}", e)
          }
        }

        if (deleted > 0) {
          runCatching {
            val hybridIndex = org.koin.core.context.GlobalContext.get().getOrNull<xyz.mpv.rex.database.repository.HybridMediaIndexRepository>()
            hybridIndex?.removeEntries(deletedPaths)
          }
          MediaLibraryEvents.notifyChanged()
        }

        Pair(deleted, failed)
      }

    /**
     * Removes stale hybrid-index entries after a successful rename.
     *
     * Renaming changes the physical MediaStore/filesystem location, but the persistent hybrid
     * index is keyed by the old location. Purging the old entry before notifying observers keeps
     * browser reloads from temporarily showing both the old and new names.
     */
    private suspend fun removeStaleIndexEntries(paths: List<String>) {
      if (paths.isEmpty()) return
      runCatching {
        val hybridIndex =
          org.koin.core.context.GlobalContext.get()
            .getOrNull<xyz.mpv.rex.database.repository.HybridMediaIndexRepository>()
        hybridIndex?.removeEntries(paths)
      }.onFailure {
        Log.w(TAG, "Failed to prune index after rename: ${it.message}")
      }
    }

    /**
     * Rename video using direct file operations (requires MANAGE_EXTERNAL_STORAGE on Android 11+)
     */
    suspend fun renameVideo(
      context: Context,
      video: Video,
      newDisplayName: String,
    ): Result<Unit> =
      withContext(Dispatchers.IO) {
        if (!BuildConfig.SCOPED_STORAGE_ONLY || hasManageStoragePermission()) {
          renameVideoDirectly(context, video, newDisplayName)
        } else {
          renameVideoScoped(context, video, newDisplayName)
        }
      }

    /**
     * Rename video using direct file operations (requires MANAGE_EXTERNAL_STORAGE on Android 11+)
     */
    private suspend fun renameVideoDirectly(
      context: Context,
      video: Video,
      newDisplayName: String,
    ): Result<Unit> =
      withContext(Dispatchers.IO) {
        try {
          val oldFile = File(video.path)
          val newFile = File(oldFile.parentFile, newDisplayName)

          if (oldFile.exists() && oldFile.renameTo(newFile)) {
            // Update history
            RecentlyPlayedOps.onVideoRenamed(oldFile.absolutePath, newFile.absolutePath)
            PlaybackStateOps.onVideoRenamed(oldFile.absolutePath, newFile.absolutePath)

            // Remove the stale pre-rename index entry before observers reload the browser.
            removeStaleIndexEntries(listOf(oldFile.absolutePath, video.uri.toString()))

            // Notify that media library has changed
            MediaLibraryEvents.notifyChanged()

            Log.d(TAG, "✓ Renamed: ${video.displayName} -> $newDisplayName")
            try {
              android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(newFile.absolutePath),
                null,
                null,
              )
            } catch (e: Exception) {
              Log.w(TAG, "Media scan failed after rename: ${e.message}")
            }
            Result.success(Unit)
          } else {
            Log.w(TAG, "✗ Rename failed: ${video.displayName}")
            Result.failure(IllegalStateException("Rename operation failed"))
          }
        } catch (e: Exception) {
          Log.e(TAG, "✗ Error renaming ${video.displayName}", e)
          Result.failure(e)
        }
      }

    /**
     * Rename video via MediaStore update (scoped storage path)
     */
    private suspend fun renameVideoScoped(
      context: Context,
      video: Video,
      newDisplayName: String,
    ): Result<Unit> =
      withContext(Dispatchers.IO) {
        try {
          if (video.uri.scheme != "content") {
            return@withContext renameVideoDirectly(context, video, newDisplayName)
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val granted = requestWriteAccess(context, listOf(video.uri))
            if (!granted) {
              Log.w(TAG, "✗ Rename request denied/cancelled: ${video.displayName}")
              return@withContext Result.failure(SecurityException("Rename permission denied"))
            }
          }

          val values =
            ContentValues().apply {
              put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
            }

          val updated = context.contentResolver.update(video.uri, values, null, null)
          if (updated > 0) {
            val oldPath = video.path
            val parent = File(oldPath).parentFile
            val newPath = parent?.let { File(it, newDisplayName).absolutePath } ?: oldPath

            RecentlyPlayedOps.onVideoRenamed(oldPath, newPath)
            PlaybackStateOps.onVideoRenamed(oldPath, newPath)

            // Remove the stale pre-rename index entry before observers reload the browser.
            removeStaleIndexEntries(listOf(oldPath, video.uri.toString()))
            MediaLibraryEvents.notifyChanged()

            Log.d(TAG, "✓ Renamed (scoped): ${video.displayName} -> $newDisplayName")
            Result.success(Unit)
          } else {
            Log.w(TAG, "✗ Rename failed (scoped): ${video.displayName}")
            Result.failure(IllegalStateException("Rename operation failed"))
          }
        } catch (e: Exception) {
          Log.e(TAG, "✗ Error renaming (scoped) ${video.displayName}", e)
          Result.failure(e)
        }
      }
  }
}
