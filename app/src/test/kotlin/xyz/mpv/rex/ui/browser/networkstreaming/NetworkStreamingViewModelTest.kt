package xyz.mpv.rex.ui.browser.networkstreaming

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import xyz.mpv.rex.domain.network.ConnectionStatus
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.repository.NetworkRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStreamingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val application = mockk<Application>(relaxed = true)
  private val repository = mockk<NetworkRepository>(relaxed = true)
  private val thumbnailRepository = mockk<ThumbnailRepository>(relaxed = true)

  private val sampleConnection = NetworkConnection(
    id = 42L,
    name = "TestShare",
    protocol = NetworkProtocol.SMB,
    host = "192.168.1.100",
    port = 445,
    path = "Videos",
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { repository.getAllConnections() } returns MutableStateFlow(listOf(sampleConnection))
    every { repository.connectionStatuses } returns MutableStateFlow(emptyMap())

    stopKoin()
    startKoin {
      modules(
        module {
          single { repository }
          single { thumbnailRepository }
        }
      )
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    stopKoin()
  }

  @Test
  fun `deleteConnection removes connection from repository and clears thumbnail cache`() = runTest {
    coEvery { repository.deleteConnection(sampleConnection) } returns Unit
    every { thumbnailRepository.clearConnectionThumbnailCache(42L) } returns Unit

    val viewModel = NetworkStreamingViewModel(application)
    viewModel.deleteConnection(sampleConnection)

    advanceUntilIdle()

    coVerify(exactly = 1) { repository.deleteConnection(sampleConnection) }
    verify(exactly = 1) { thumbnailRepository.clearConnectionThumbnailCache(42L) }
  }

  @Test
  fun `deleteConnection completes safely when thumbnailRepository is not available`() = runTest {
    stopKoin()
    startKoin {
      modules(
        module {
          single { repository }
        }
      )
    }

    coEvery { repository.deleteConnection(sampleConnection) } returns Unit

    val viewModel = NetworkStreamingViewModel(application)
    viewModel.deleteConnection(sampleConnection)

    advanceUntilIdle()

    coVerify(exactly = 1) { repository.deleteConnection(sampleConnection) }
  }
}
