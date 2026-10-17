package xyz.mpv.rex.ui.browser.networkstreaming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.domain.network.ConnectionStatus
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.repository.NetworkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NetworkStreamingViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  private val repository: NetworkRepository by inject()
  private val thumbnailRepository: ThumbnailRepository? get() = runCatching {
    org.koin.java.KoinJavaComponent.get<ThumbnailRepository>(ThumbnailRepository::class.java)
  }.getOrNull()

  val connections: StateFlow<List<NetworkConnection>> =
    repository
      .getAllConnections()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val connectionStatuses: StateFlow<Map<Long, ConnectionStatus>> = repository.connectionStatuses

  fun addConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.addConnection(connection)
    }
  }

  fun updateConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.updateConnection(connection)
    }
  }

  fun deleteConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.deleteConnection(connection)
      thumbnailRepository?.clearConnectionThumbnailCache(connection.id)
    }
  }

  fun connect(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.connect(connection)
    }
  }

  fun disconnect(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.disconnect(connection)
    }
  }

  override fun onCleared() {
    super.onCleared()
    viewModelScope.launch {
      repository.disconnectAll()
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          NetworkStreamingViewModel(application)
        }
      }
  }
}
