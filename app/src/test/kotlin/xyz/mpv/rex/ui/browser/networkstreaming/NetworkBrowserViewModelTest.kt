package xyz.mpv.rex.ui.browser.networkstreaming

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.preferences.UiPreferences
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.preferences.AdvancedPreferences
import java.io.IOException
import xyz.mpv.rex.R

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkBrowserViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val application = mockk<Application>(relaxed = true)
  private val repository = mockk<NetworkRepository>()
  private val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
  private val uiPreferences = mockk<UiPreferences>(relaxed = true)
  private val playbackStateRepository = mockk<PlaybackStateRepository>(relaxed = true)
  private val hybridMediaIndexRepository = mockk<HybridMediaIndexRepository>(relaxed = true)

  private lateinit var fakePreferenceStore: InMemoryPreferenceStore
  private lateinit var browserPreferences: BrowserPreferences

  private val sampleConnection = NetworkConnection(
    id = 42L,
    name = "HomeServer",
    protocol = NetworkProtocol.SMB,
    host = "192.168.1.100",
    port = 445,
    path = "Media",
  )

  private class InMemoryPreference<T>(
    private val key: String,
    private val defaultValue: T,
  ) : Preference<T> {
    private val flow = MutableStateFlow(defaultValue)

    override fun key(): String = key
    override fun get(): T = flow.value
    override fun set(value: T) {
      flow.value = value
    }
    override fun isSet(): Boolean = true
    override fun delete() {
      flow.value = defaultValue
    }
    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flow.asStateFlow()
    override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow.asStateFlow()
  }

  private class InMemoryPreferenceStore : PreferenceStore {
    private val preferences = mutableMapOf<String, Preference<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrCreate(key: String, defaultValue: T): Preference<T> =
      preferences.getOrPut(key) { InMemoryPreference(key, defaultValue) } as Preference<T>

    override fun getString(key: String, defaultValue: String): Preference<String> =
      getOrCreate(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
      getOrCreate(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
      getOrCreate(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
      getOrCreate(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
      getOrCreate(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      getOrCreate(key, defaultValue)

    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = getOrCreate(key, defaultValue)

    override fun getAll(): Map<String, *> = preferences.mapValues { it.value.get() }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    fakePreferenceStore = InMemoryPreferenceStore()
    val context = mockk<Context>(relaxed = true)
    val res = mockk<Resources>(relaxed = true)
    val config = Configuration().apply { smallestScreenWidthDp = 360 }
    every { context.resources } returns res
    every { res.configuration } returns config

    browserPreferences = BrowserPreferences(fakePreferenceStore, context)

    every { uiPreferences.observeUiSettings() } returns flowOf(UiSettings())
    every { uiPreferences.getUiSettings() } returns UiSettings()
    every { playbackStateRepository.observeAllPlaybackStates() } returns emptyFlow()

    stopKoin()
    startKoin {
      modules(
        module {
          single<Context> { context }
          single { repository }
          single { metadataCache }
          single { uiPreferences }
          single { playbackStateRepository }
          single { browserPreferences }
          single { hybridMediaIndexRepository }
          single { mockk<RecentlyPlayedRepository>(relaxed = true) }
          single { mockk<AdvancedPreferences>(relaxed = true) }
        },
      )
    }

    coEvery { repository.getConnectionById(42L) } returns sampleConnection
    every { application.getString(R.string.network_share_unreachable) } returns "Can't reach the network share."
    every { application.getString(R.string.network_auth_failed) } returns "Authentication failed. Check your username and password."
    every { application.getString(R.string.network_access_denied) } returns "Access denied to the network share."
    every { application.getString(R.string.network_connection_not_found) } returns "Connection not found."
    every { application.getString(R.string.network_error_generic) } returns "Unable to access the network share."
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  private fun createViewModel(path: String = "/"): NetworkBrowserViewModel =
    NetworkBrowserViewModel(
      application = application,
      connectionId = 42L,
      currentPath = path,
      defaultDispatcher = testDispatcher,
    )

  @Test
  fun test_toggleShowHidden_instantUpdateWithoutNetworkCall() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "video.mp4", path = "/video.mp4", size = 1000L, isDirectory = false, isHidden = false),
      NetworkFile(name = ".DS_Store", path = "/.DS_Store", size = 100L, isDirectory = false, isHidden = true),
      NetworkFile(name = "secret.mkv", path = "/secret.mkv", size = 2000L, isDirectory = false, isHidden = true),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.listFiles(sampleConnection, "/") }
    assertEquals(1, viewModel.items.value.size)
    assertEquals("video.mp4", viewModel.items.value[0].name)
    assertEquals(3, viewModel.rawFiles.value.size)

    viewModel.setShowHiddenFiles(true)
    advanceUntilIdle()

    assertEquals(3, viewModel.items.value.size)
    coVerify(exactly = 1) { repository.listFiles(any(), any()) }

    viewModel.setShowHiddenFiles(false)
    advanceUntilIdle()

    assertEquals(1, viewModel.items.value.size)
    assertEquals("video.mp4", viewModel.items.value[0].name)
    coVerify(exactly = 1) { repository.listFiles(any(), any()) }
  }

  @Test
  fun test_filtering_mixedHiddenTypes_dotfilesAndSmbAttributesFiltered() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "NormalFolder", path = "/NormalFolder", size = 0L, isDirectory = true, isHidden = false),
      NetworkFile(name = ".HiddenFolder", path = "/.HiddenFolder", size = 0L, isDirectory = true, isHidden = true),
      NetworkFile(name = "visible.mp4", path = "/visible.mp4", size = 500L, isDirectory = false, isHidden = false),
      NetworkFile(name = ".dotfile.mp4", path = "/.dotfile.mp4", size = 200L, isDirectory = false, isHidden = false),
      NetworkFile(name = "smb_hidden.mp4", path = "/smb_hidden.mp4", size = 300L, isDirectory = false, isHidden = true),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    val hiddenOffNames = viewModel.items.value.map { it.name }
    assertEquals(listOf("NormalFolder", "visible.mp4"), hiddenOffNames)

    viewModel.setShowHiddenFiles(true)
    advanceUntilIdle()

    assertEquals(5, viewModel.items.value.size)
    coVerify(exactly = 1) { repository.listFiles(any(), any()) }
  }

  @Test
  fun test_filtering_allHiddenDirectory_transitionsBetweenEmptyAndPopulated() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = ".hidden1", path = "/.hidden1", size = 10L, isDirectory = false, isHidden = true),
      NetworkFile(name = ".hidden2", path = "/.hidden2", size = 20L, isDirectory = false, isHidden = true),
      NetworkFile(name = "system.sys", path = "/system.sys", size = 30L, isDirectory = false, isHidden = true),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertTrue(viewModel.items.value.isEmpty())
    assertFalse(viewModel.rawFiles.value.isEmpty())

    viewModel.setShowHiddenFiles(true)
    advanceUntilIdle()

    assertEquals(3, viewModel.items.value.size)
    coVerify(exactly = 1) { repository.listFiles(any(), any()) }
  }

  @Test
  fun test_sortTypeChange_titleToDateToSize_instantReorderWithoutNetworkCall() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "Beta.mp4", path = "/Beta.mp4", size = 3000L, isDirectory = false, lastModified = 1000L),
      NetworkFile(name = "Alpha.mp4", path = "/Alpha.mp4", size = 1000L, isDirectory = false, lastModified = 3000L),
      NetworkFile(name = "Gamma.mp4", path = "/Gamma.mp4", size = 2000L, isDirectory = false, lastModified = 2000L),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals(listOf("Alpha.mp4", "Beta.mp4", "Gamma.mp4"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Date, SortOrder.Ascending)
    advanceUntilIdle()
    assertEquals(listOf("Beta.mp4", "Gamma.mp4", "Alpha.mp4"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Size, SortOrder.Ascending)
    advanceUntilIdle()
    assertEquals(listOf("Alpha.mp4", "Gamma.mp4", "Beta.mp4"), viewModel.items.value.map { it.name })

    coVerify(exactly = 1) { repository.listFiles(any(), any()) }
  }

  @Test
  fun test_sortOrderToggle_ascendingToDescending_foldersFirstInvariantPreserved() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "folderB", path = "/folderB", size = 0L, isDirectory = true, lastModified = 200L),
      NetworkFile(name = "folderA", path = "/folderA", size = 0L, isDirectory = true, lastModified = 100L),
      NetworkFile(name = "fileB.mp4", path = "/fileB.mp4", size = 200L, isDirectory = false, lastModified = 400L),
      NetworkFile(name = "fileA.mp4", path = "/fileA.mp4", size = 100L, isDirectory = false, lastModified = 300L),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals(listOf("folderA", "folderB", "fileA.mp4", "fileB.mp4"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Title, SortOrder.Descending)
    advanceUntilIdle()
    assertEquals(listOf("folderB", "folderA", "fileB.mp4", "fileA.mp4"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Date, SortOrder.Descending)
    advanceUntilIdle()
    assertEquals(listOf("folderB", "folderA", "fileB.mp4", "fileA.mp4"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Size, SortOrder.Descending)
    advanceUntilIdle()
    assertTrue(viewModel.items.value[0].isDirectory)
    assertTrue(viewModel.items.value[1].isDirectory)
    assertFalse(viewModel.items.value[2].isDirectory)
    assertFalse(viewModel.items.value[3].isDirectory)
    assertEquals("fileB.mp4", viewModel.items.value[2].name)
    assertEquals("fileA.mp4", viewModel.items.value[3].name)

    coVerify(exactly = 1) { repository.listFiles(any(), any()) }
  }

  @Test
  fun test_sorting_naturalOrdering_episodesSequenceEp2BeforeEp10() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "Episode 10.mkv", path = "/ep10", size = 100L, isDirectory = false),
      NetworkFile(name = "Episode 2.mkv", path = "/ep2", size = 100L, isDirectory = false),
      NetworkFile(name = "Episode 1.mkv", path = "/ep1", size = 100L, isDirectory = false),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals(
      listOf("Episode 1.mkv", "Episode 2.mkv", "Episode 10.mkv"),
      viewModel.items.value.map { it.name },
    )
  }

  @Test
  fun test_sorting_sizeCollisionsTieBreakWithNaturalName() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "video_b.mp4", path = "/b", size = 500L, isDirectory = false),
      NetworkFile(name = "video_a.mp4", path = "/a", size = 500L, isDirectory = false),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    viewModel.setSort(FolderSortType.Size, SortOrder.Ascending)
    advanceUntilIdle()

    assertEquals(listOf("video_a.mp4", "video_b.mp4"), viewModel.items.value.map { it.name })
  }

  @Test
  fun test_stateTransition_successfulLoad_loadingToSuccess() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "movie.mkv", path = "/movie.mkv", size = 1024L, isDirectory = false),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    assertTrue(viewModel.isLoading.value)

    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertNull(viewModel.error.value)
    assertEquals(1, viewModel.items.value.size)
  }

  @Test
  fun test_isLoading_remainsTrueUntilDataLoaded_noFlashOfEmptyFolder() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "movie.mkv", path = "/movie.mkv", size = 1024L, isDirectory = false),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertTrue("isLoading must remain true before loadData finishes", viewModel.isLoading.value)
    assertTrue(viewModel.items.value.isEmpty())

    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertEquals(1, viewModel.items.value.size)
  }

  @Test
  fun test_stateTransition_emptyFolder_loadingToEmpty() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(emptyList())

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertNull(viewModel.error.value)
    assertTrue(viewModel.items.value.isEmpty())
  }

  @Test
  fun test_stateTransition_networkError_loadingToError() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(IOException("Host unreachable"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertEquals("Can't reach the network share.", viewModel.error.value)
    assertTrue(viewModel.items.value.isEmpty())
  }

  @Test
  fun test_stateTransition_connectionNotFound_immediateError() = runTest(testDispatcher) {
    coEvery { repository.getConnectionById(42L) } returns null

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertEquals("Connection not found.", viewModel.error.value)
  }

  @Test
  fun test_loadDataSuspend_cancellationException_rethrowsAndDoesNotSetError() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } throws CancellationException("Coroutines cancelled")

    val viewModel = createViewModel()
    var caught: CancellationException? = null
    try {
      viewModel.loadDataSuspend()
    } catch (e: CancellationException) {
      caught = e
    }

    assertNotNull(caught)
    assertEquals("Coroutines cancelled", caught?.message)
    assertNull(viewModel.error.value)
  }

  @Test
  fun test_loadData_cancellationException_doesNotSetErrorState() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } throws CancellationException("Cancelled")

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertNull(viewModel.error.value)
  }

  @Test
  fun test_loadDataSuspend_resultFailureWithCancellationException_rethrowsAndDoesNotSetError() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(CancellationException("Result cancelled"))

    val viewModel = createViewModel()
    var caught: CancellationException? = null
    try {
      viewModel.loadDataSuspend()
    } catch (e: CancellationException) {
      caught = e
    }

    assertNotNull(caught)
    assertEquals("Result cancelled", caught?.message)
    assertNull(viewModel.error.value)
  }

  @Test
  fun test_stateTransition_refreshRecovery_errorToSuccess() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(IOException("Timeout"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertEquals("Can't reach the network share.", viewModel.error.value)

    val recoveredFiles = listOf(NetworkFile(name = "recovered.mp4", path = "/rec", size = 10L, isDirectory = false))
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(recoveredFiles)

    viewModel.refresh()
    advanceUntilIdle()

    assertFalse(viewModel.isLoading.value)
    assertNull(viewModel.error.value)
    assertEquals(1, viewModel.items.value.size)
    assertEquals("recovered.mp4", viewModel.items.value[0].name)
    coVerify(exactly = 2) { repository.listFiles(sampleConnection, "/") }
  }

  @Test
  fun test_errorMapping_authenticationFailure_loginFailed() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(Exception("Login failed"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Authentication failed. Check your username and password.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_authenticationFailure_smbLogonFailure() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      com.hierynomus.smbj.common.SMBRuntimeException("STATUS_LOGON_FAILURE: Logon failure"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Authentication failed. Check your username and password.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_authenticationFailure_webDav401() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(Exception("HTTP 401 Unauthorized"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Authentication failed. Check your username and password.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_accessDenied_smbStatus() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(Exception("STATUS_ACCESS_DENIED: Access is denied"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Access denied to the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_accessDenied_webDav403() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(Exception("HTTP 403 Forbidden"))

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Access denied to the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_transportException_nestedTimeout() = runTest(testDispatcher) {
    val transportError = com.hierynomus.protocol.transport.TransportException(
      java.util.concurrent.ExecutionException("Timeout expired", java.util.concurrent.TimeoutException()),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(transportError)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_smbSessionNullOrShareFailed() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      java.net.SocketException("Session is null or share failed"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_unknownException_cleanFallback() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      IllegalStateException("Internal native driver crash code 0x80004005"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Unable to access the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_smbRuntimeException_nonTimeout_fallsBackToGeneric() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      com.hierynomus.smbj.common.SMBRuntimeException("Buffer underflow parsing tree connect response"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Unable to access the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_portOrIpContaining401_doesNotMisclassifyAsAuthFailure() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      java.net.ConnectException("Connection refused to 192.168.1.5:4010"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_portOrIpContaining403_doesNotMisclassifyAsAccessDenied() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      java.net.ConnectException("Connection refused to 192.168.1.5:4030"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_circularCauseChain_handledGracefully() = runTest(testDispatcher) {
    val inner = Exception("Timeout reading socket")
    val circular = object : Exception("Socket wrapper error") {
      override val cause: Throwable get() = inner
    }
    val root = object : Exception("Outer error") {
      override val cause: Throwable get() = circular
    }
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(root)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_errorMapping_smbStatusNetworkNameDeleted() = runTest(testDispatcher) {
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.failure(
      Exception("STATUS_NETWORK_NAME_DELETED: The network name is no longer available"),
    )

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    assertEquals("Can't reach the network share.", viewModel.error.value)
  }

  @Test
  fun test_viewMode_defaultsToListMode() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    assertEquals(NetworkViewMode.LIST, viewModel.viewMode.value)
  }

  @Test
  fun test_viewMode_reactivelyEmitsAllFourModes() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.setViewMode(NetworkViewMode.GALLERY)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.GALLERY, viewModel.viewMode.value)

    viewModel.setViewMode(NetworkViewMode.GRID)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.GRID, viewModel.viewMode.value)

    viewModel.setViewMode(NetworkViewMode.DETAIL)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.DETAIL, viewModel.viewMode.value)

    viewModel.setViewMode(NetworkViewMode.LIST)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.LIST, viewModel.viewMode.value)
  }

  @Test
  fun test_viewMode_persistsAcrossFolderNavigation() = runTest(testDispatcher) {
    val vm1 = createViewModel(path = "/")
    vm1.setViewMode(NetworkViewMode.GALLERY)
    advanceUntilIdle()

    val vm2 = createViewModel(path = "/Anime/Season1")
    advanceUntilIdle()

    assertEquals(NetworkViewMode.GALLERY, vm2.viewMode.value)
  }

  @Test
  fun test_setViewMode_mutatesPreferenceDirectly() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.setViewMode(NetworkViewMode.GRID)
    advanceUntilIdle()

    assertEquals(NetworkViewMode.GRID, browserPreferences.networkViewMode.get())
  }

  @Test
  fun test_mutatingHelpers_setShowHiddenFiles_and_setSort() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.setShowHiddenFiles(true)
    advanceUntilIdle()
    assertTrue(browserPreferences.networkShowHiddenFiles.get())
    assertTrue(viewModel.showHiddenFiles.value)

    viewModel.setSort(FolderSortType.Size, SortOrder.Descending)
    advanceUntilIdle()
    assertEquals(FolderSortType.Size, browserPreferences.networkSortType.get())
    assertEquals(FolderSortType.Size, viewModel.sortType.value)
    assertEquals(SortOrder.Descending, browserPreferences.networkSortOrder.get())
    assertEquals(SortOrder.Descending, viewModel.sortOrder.value)
  }

  @Test
  fun test_extremeFileSizeLongMaxValueNoOverflow() = runTest(testDispatcher) {
    val files = listOf(
      NetworkFile(name = "huge.iso", path = "/huge", size = Long.MAX_VALUE, isDirectory = false),
      NetworkFile(name = "tiny.txt", path = "/tiny", size = 0L, isDirectory = false),
    )
    coEvery { repository.listFiles(sampleConnection, "/") } returns Result.success(files)

    val viewModel = createViewModel()
    viewModel.loadData()
    advanceUntilIdle()

    viewModel.setSort(FolderSortType.Size, SortOrder.Descending)
    advanceUntilIdle()

    assertEquals(listOf("huge.iso", "tiny.txt"), viewModel.items.value.map { it.name })

    viewModel.setSort(FolderSortType.Size, SortOrder.Ascending)
    advanceUntilIdle()

    assertEquals(listOf("tiny.txt", "huge.iso"), viewModel.items.value.map { it.name })
  }

  @Test
  fun test_playVideo_errorSetsCleanMessage() = runTest(testDispatcher) {
    coEvery { repository.getConnectionById(42L) } returns null
    val viewModel = createViewModel()
    val file = NetworkFile(name = "movie.mp4", path = "/movie.mp4", size = 1024L, isDirectory = false)

    viewModel.playVideo(file)
    advanceUntilIdle()

    assertEquals("Connection not found.", viewModel.error.value)
  }
}
