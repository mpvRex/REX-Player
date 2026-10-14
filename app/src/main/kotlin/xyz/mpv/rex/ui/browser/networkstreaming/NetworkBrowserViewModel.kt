package xyz.mpv.rex.ui.browser.networkstreaming

import android.app.Application
import android.content.Intent
import android.util.Log
import xyz.mpv.rex.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.utils.storage.FileTypeUtils
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.media.NetworkFileComparator
import xyz.mpv.rex.utils.storage.NetworkFileFilter

/**
 * ViewModel for browsing files on a network share.
 * Maintains raw directory contents and reactively combines sorting and hidden-file preferences
 * in memory without remote network reloads.
 */
class NetworkBrowserViewModel(
  private val application: Application,
  private val connectionId: Long,
  private val currentPath: String,
  private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseBrowserViewModel<NetworkFile>(application),
  KoinComponent {
  private val repository: NetworkRepository by inject()
  private val thumbnailRepository: ThumbnailRepository? get() = runCatching {
    org.koin.java.KoinJavaComponent.get<ThumbnailRepository>(ThumbnailRepository::class.java)
  }.getOrNull()

  private val _connection = MutableStateFlow<NetworkConnection?>(null)
  val connection: StateFlow<NetworkConnection?> = _connection.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _rawFiles = MutableStateFlow<List<NetworkFile>>(emptyList())
  val rawFiles: StateFlow<List<NetworkFile>> = _rawFiles.asStateFlow()

  val viewMode: StateFlow<NetworkViewMode> =
    browserPreferences.networkViewMode.stateIn(viewModelScope)

  val showHiddenFiles: StateFlow<Boolean> =
    browserPreferences.networkShowHiddenFiles.stateIn(viewModelScope)

  val sortType: StateFlow<FolderSortType> =
    browserPreferences.networkSortType.stateIn(viewModelScope)

  val sortOrder: StateFlow<SortOrder> =
    browserPreferences.networkSortOrder.stateIn(viewModelScope)

  init {
    viewModelScope.launch(defaultDispatcher) {
      runCatching {
        _connection.value = repository.getConnectionById(connectionId)
      }
    }
    viewModelScope.launch(defaultDispatcher) {
      combine(
        _rawFiles,
        browserPreferences.networkShowHiddenFiles.changes(),
        browserPreferences.networkSortType.changes(),
        browserPreferences.networkSortOrder.changes(),
      ) { rawList, showHidden, sortType, sortOrder ->
        val filtered = NetworkFileFilter.filter(rawList, showHidden)
        filtered.sortedWith(
          NetworkFileComparator.create(
            sortType = sortType,
            sortOrder = sortOrder,
            foldersFirst = true,
          ),
        )
      }.collect { sortedFilteredFiles ->
        _items.value = sortedFilteredFiles
      }
    }
  }

  /**
   * Suspending data load for pull-to-refresh and direct invocation.
   * Keeps isLoading synchronized with item emission to prevent empty-folder flashing.
   */
  suspend fun loadDataSuspend() = withContext(defaultDispatcher) {
    _isLoading.value = true
    _error.value = null

    try {
      thumbnailRepository?.clearNetworkThumbnailFailures(connectionId)
    } catch (_: Exception) {}

    val conn = repository.getConnectionById(connectionId)
    if (conn == null) {
      Log.e(TAG, "Connection not found for connectionId $connectionId")
      _error.value = getStringSafe(R.string.network_connection_not_found, "Connection not found.")
      _isLoading.value = false
      return@withContext
    }
    _connection.value = conn

    try {

      repository.listFiles(conn, currentPath)
        .onSuccess { fileList ->
          val showHidden = browserPreferences.networkShowHiddenFiles.get()
          val sortType = browserPreferences.networkSortType.get()
          val sortOrder = browserPreferences.networkSortOrder.get()
          val filtered = NetworkFileFilter.filter(fileList, showHidden)
          val sorted = filtered.sortedWith(
            NetworkFileComparator.create(
              sortType = sortType,
              sortOrder = sortOrder,
              foldersFirst = true,
            ),
          )
          _rawFiles.value = fileList
          _items.value = sorted
          _isLoading.value = false
        }
        .onFailure { e ->
          if (e is CancellationException) throw e
          Log.e(TAG, "Failed to list files for connection $connectionId at path $currentPath", e)
          _error.value = resolveErrorMessage(e)
          _isLoading.value = false
        }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed to load network files for connection $connectionId", e)
      _error.value = resolveErrorMessage(e)
      _isLoading.value = false
    }
  }

  /**
   * Load files in the current directory from the remote network share.
   * Only called on initial navigation or explicit user refresh.
   */
  override fun loadData() {
    viewModelScope.launch {
      loadDataSuspend()
    }
  }

  override fun refresh(silent: Boolean) {
    loadData()
  }

  /**
   * Update active view mode.
   */
  fun setViewMode(mode: NetworkViewMode) {
    browserPreferences.networkViewMode.set(mode)
  }

  /**
   * Toggle visibility of hidden and dotfiles.
   */
  fun setShowHiddenFiles(show: Boolean) {
    browserPreferences.networkShowHiddenFiles.set(show)
  }

  /**
   * Update sorting type and direction.
   */
  fun setSort(type: FolderSortType, order: SortOrder) {
    browserPreferences.saveNetworkSortOrder(type, order)
    browserPreferences.networkSortType.set(type)
    browserPreferences.networkSortOrder.set(order)
  }

  /**
   * Play a video file
   */
  fun playVideo(file: NetworkFile) {
    viewModelScope.launch {
      try {
        val connection = repository.getConnectionById(connectionId)
        if (connection == null) {
          Log.e(TAG, "Connection not found for connectionId $connectionId")
          _error.value = getStringSafe(R.string.network_connection_not_found, "Connection not found.")
          return@launch
        }

        // Use proxy server for protocols that need seeking support
        val useProxy = connection.protocol in PROXY_PROTOCOLS

        val resolvedMimeType = file.mimeType
          ?: FileTypeUtils.getMimeTypeFromExtension(java.io.File(file.name).extension).takeIf { it != "*/*" }
          ?: if (FileTypeUtils.isAudioFile(java.io.File(file.name))) "audio/*" else "video/*"

        val uri = if (useProxy) {
          val proxy = xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy.getInstance()
          val streamId = "${connectionId}_${System.currentTimeMillis()}"
          val proxyMimeType = resolvedMimeType.takeIf { it != "*/*" }
            ?: if (FileTypeUtils.isAudioFile(java.io.File(file.name))) "audio/mpeg" else "video/mp4"
          val proxyUrl = proxy.registerStream(
            streamId = streamId,
            connection = connection,
            filePath = file.path,
            fileSize = file.size,
            mimeType = proxyMimeType,
          )
          android.net.Uri.parse(proxyUrl)
        } else {
          NetworkStreamingProvider.setConnection(connectionId, connection)
          NetworkStreamingProvider.getUri(application, connectionId, file.path)
        }

        // Launch the player
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(application, xyz.mpv.rex.ui.player.PlayerActivity::class.java)
        intent.putExtra("internal_launch", true)
        intent.putExtra("launch_source", "network_stream")
        intent.putExtra("title", file.name)
        intent.putExtra("filename", file.name)
        // Pass the original network file path for stable media identifier (position saving)
        intent.putExtra("network_file_path", file.path)
        intent.putExtra("network_connection_id", connectionId)
        intent.setDataAndType(uri, resolvedMimeType)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!useProxy) {
          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        application.startActivity(intent)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Error playing video", e)
        _error.value = resolveErrorMessage(e)
      }
    }
  }

  private fun resolveErrorMessage(throwable: Throwable): String {
    val chain = buildList {
      var current: Throwable? = throwable
      val seen = HashSet<Throwable>()
      while (current != null && seen.add(current)) {
        add(current)
        current = current.cause
      }
    }

    val isConnectionNotFound = chain.any { e ->
      e.message?.contains("connection not found", ignoreCase = true) == true
    }
    if (isConnectionNotFound) {
      return getStringSafe(R.string.network_connection_not_found, "Connection not found.")
    }

    val isUnreachableOrTimeout = chain.any { e ->
      val name = e::class.java.name
      val msg = e.message.orEmpty()
      e is java.net.SocketException ||
        e is java.net.SocketTimeoutException ||
        e is java.net.UnknownHostException ||
        e is java.net.ConnectException ||
        e is java.net.NoRouteToHostException ||
        e is java.util.concurrent.TimeoutException ||
        e is java.io.EOFException ||
        name.contains("TransportException") ||
        name.contains("TimeoutException") ||
        msg.contains("timeout", ignoreCase = true) ||
        msg.contains("timed out", ignoreCase = true) ||
        msg.contains("unreachable", ignoreCase = true) ||
        msg.contains("refused", ignoreCase = true) ||
        msg.contains("route to host", ignoreCase = true) ||
        msg.contains("transport", ignoreCase = true) ||
        msg.contains("session is null", ignoreCase = true) ||
        msg.contains("share failed", ignoreCase = true) ||
        msg.contains("share name required", ignoreCase = true) ||
        msg.contains("share not found", ignoreCase = true) ||
        msg.contains("bad network name", ignoreCase = true) ||
        msg.contains("network_name_deleted", ignoreCase = true) ||
        msg.contains("not connected", ignoreCase = true) ||
        msg.contains("connection failed", ignoreCase = true) ||
        msg.contains("connection closed", ignoreCase = true) ||
        msg.contains("connection reset", ignoreCase = true) ||
        msg.contains("network error", ignoreCase = true) ||
        msg.contains("broken pipe", ignoreCase = true)
    }
    if (isUnreachableOrTimeout) {
      return getStringSafe(R.string.network_share_unreachable, "Can't reach the network share.")
    }

    val isAuthFailure = chain.any { e ->
      val name = e::class.java.simpleName
      val msg = e.message.orEmpty()
      name.contains("Auth", ignoreCase = true) ||
        name.contains("Login", ignoreCase = true) ||
        name.contains("BadCredentials", ignoreCase = true) ||
        msg.contains("logon failure", ignoreCase = true) ||
        msg.contains("login failed", ignoreCase = true) ||
        msg.contains("wrong password", ignoreCase = true) ||
        msg.contains("bad credentials", ignoreCase = true) ||
        msg.contains("unauthorized", ignoreCase = true) ||
        msg.contains("status_logon_failure", ignoreCase = true) ||
        msg.contains("status_wrong_password", ignoreCase = true) ||
        msg.contains("status_account_disabled", ignoreCase = true) ||
        msg.contains("status_account_locked_out", ignoreCase = true) ||
        msg.contains("status_password_expired", ignoreCase = true) ||
        HTTP_401_REGEX.containsMatchIn(msg)
    }
    if (isAuthFailure) {
      return getStringSafe(R.string.network_auth_failed, "Authentication failed. Check your username and password.")
    }

    val isAccessDenied = chain.any { e ->
      val msg = e.message.orEmpty()
      msg.contains("access denied", ignoreCase = true) ||
        msg.contains("permission denied", ignoreCase = true) ||
        msg.contains("status_access_denied", ignoreCase = true) ||
        msg.contains("status_network_access_denied", ignoreCase = true) ||
        HTTP_403_REGEX.containsMatchIn(msg)
    }
    if (isAccessDenied) {
      return getStringSafe(R.string.network_access_denied, "Access denied to the network share.")
    }

    return getStringSafe(R.string.network_error_generic, "Unable to access the network share.")
  }

  private fun getStringSafe(resId: Int, fallback: String): String {
    return runCatching { application.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
  }

  companion object {
    private const val TAG = "NetworkBrowserVM"

    private val HTTP_401_REGEX = Regex("""\b401\b""")
    private val HTTP_403_REGEX = Regex("""\b403\b""")

    // Protocols that require proxy server for seeking support
    private val PROXY_PROTOCOLS = setOf(
      NetworkProtocol.SMB,
      NetworkProtocol.FTP,
      NetworkProtocol.WEBDAV,
    )

    fun factory(
      application: Application,
      connectionId: Long,
      currentPath: String,
    ): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          NetworkBrowserViewModel(application, connectionId, currentPath)
        }
      }
  }
}
