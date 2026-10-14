package xyz.mpv.rex.ui.browser.networkstreaming

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertSame
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
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.preferences.UiPreferences
import xyz.mpv.rex.preferences.UiSettings
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.ui.browser.cards.CardLayoutConfigProvider

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkBrowserScreenLayoutTest {

  private val testDispatcher = StandardTestDispatcher()
  private val application = mockk<Application>(relaxed = true)
  private val repository = mockk<NetworkRepository>(relaxed = true)
  private val metadataCache = mockk<VideoMetadataCacheRepository>(relaxed = true)
  private val uiPreferences = mockk<UiPreferences>(relaxed = true)
  private val playbackStateRepository = mockk<PlaybackStateRepository>(relaxed = true)
  private val hybridMediaIndexRepository = mockk<HybridMediaIndexRepository>(relaxed = true)

  private lateinit var fakePreferenceStore: InMemoryPreferenceStore
  private lateinit var browserPreferences: BrowserPreferences

  private val sampleConnection = NetworkConnection(
    id = 101L,
    name = "StorageBox",
    protocol = NetworkProtocol.SMB,
    host = "192.168.1.50",
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
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  private fun createViewModel(path: String = "/"): NetworkBrowserViewModel =
    NetworkBrowserViewModel(
      application = application,
      connectionId = sampleConnection.id,
      currentPath = path,
      defaultDispatcher = testDispatcher,
    )

  @Test
  fun `viewMode initially defaults to LIST in viewModel and browserPreferences`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(NetworkViewMode.LIST, browserPreferences.networkViewMode.get())
    assertEquals(NetworkViewMode.LIST, viewModel.viewMode.value)
  }

  @Test
  fun `viewModel setViewMode updates viewMode StateFlow reactively across all four modes`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val targetModes = listOf(
      NetworkViewMode.GALLERY,
      NetworkViewMode.GRID,
      NetworkViewMode.DETAIL,
      NetworkViewMode.LIST,
    )

    for (targetMode in targetModes) {
      viewModel.setViewMode(targetMode)
      advanceUntilIdle()
      assertEquals("browserPreferences must be updated to $targetMode", targetMode, browserPreferences.networkViewMode.get())
      assertEquals("viewModel.viewMode StateFlow must emit $targetMode", targetMode, viewModel.viewMode.value)
    }
  }

  @Test
  fun `updating BrowserPreferences directly updates viewModel viewMode StateFlow`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    browserPreferences.networkViewMode.set(NetworkViewMode.GRID)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.GRID, viewModel.viewMode.value)

    browserPreferences.networkViewMode.set(NetworkViewMode.DETAIL)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.DETAIL, viewModel.viewMode.value)
  }

  @Test
  fun `viewMode persists across folder drill-down navigation`() = runTest {
    val rootViewModel = createViewModel(path = "/")
    advanceUntilIdle()

    rootViewModel.setViewMode(NetworkViewMode.GALLERY)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.GALLERY, rootViewModel.viewMode.value)

    val childViewModel = createViewModel(path = "/Movies/Action")
    advanceUntilIdle()

    assertEquals(
      "Child folder screen must inherit active viewMode (GALLERY) from BrowserPreferences",
      NetworkViewMode.GALLERY,
      childViewModel.viewMode.value,
    )

    childViewModel.setViewMode(NetworkViewMode.GRID)
    advanceUntilIdle()

    val siblingViewModel = createViewModel(path = "/Movies/Comedy")
    advanceUntilIdle()

    assertEquals(
      "Subsequent folder screen must reflect latest chosen viewMode (GRID)",
      NetworkViewMode.GRID,
      siblingViewModel.viewMode.value,
    )
  }

  @Test
  fun `viewMode persists across application restart simulation`() = runTest {
    val initialViewModel = createViewModel()
    advanceUntilIdle()

    initialViewModel.setViewMode(NetworkViewMode.DETAIL)
    advanceUntilIdle()
    assertEquals(NetworkViewMode.DETAIL, browserPreferences.networkViewMode.get())

    val context = mockk<Context>(relaxed = true)
    val res = mockk<Resources>(relaxed = true)
    every { context.resources } returns res
    every { res.configuration } returns Configuration()
    val restartedBrowserPreferences = BrowserPreferences(fakePreferenceStore, context)

    stopKoin()
    startKoin {
      modules(
        module {
          single<Context> { context }
          single { repository }
          single { metadataCache }
          single { uiPreferences }
          single { playbackStateRepository }
          single { restartedBrowserPreferences }
          single { hybridMediaIndexRepository }
          single { mockk<RecentlyPlayedRepository>(relaxed = true) }
          single { mockk<AdvancedPreferences>(relaxed = true) }
        },
      )
    }

    val restartedViewModel = createViewModel(path = "/")
    advanceUntilIdle()

    assertEquals(
      "Active viewMode must persist across app restart and restore DETAIL mode",
      NetworkViewMode.DETAIL,
      restartedViewModel.viewMode.value,
    )
  }

  @Test
  fun `partitionNetworkFiles with empty list returns empty collections and isEmpty true`() {
    val partitioned = partitionNetworkFiles(emptyList())

    assertTrue(partitioned.folders.isEmpty())
    assertTrue(partitioned.videos.isEmpty())
    assertTrue(partitioned.otherFiles.isEmpty())
    assertTrue(partitioned.isEmpty)
    assertEquals(0, partitioned.totalCount)
  }

  @Test
  fun `partitionNetworkFiles with pure folders routes all entries to folders without dropping any`() {
    val folderList = listOf(
      NetworkFile(name = "Movies", path = "/Movies", size = 0L, isDirectory = true),
      NetworkFile(name = "TV Shows", path = "/TV Shows", size = 0L, isDirectory = true),
      NetworkFile(name = "Music", path = "/Music", size = 0L, isDirectory = true),
      NetworkFile(name = "Documentaries", path = "/Documentaries", size = 0L, isDirectory = true),
    )

    val partitioned = partitionNetworkFiles(folderList)

    assertEquals(4, partitioned.folders.size)
    assertEquals(0, partitioned.videos.size)
    assertEquals(0, partitioned.otherFiles.size)
    assertFalse(partitioned.isEmpty)
    assertEquals(4, partitioned.totalCount)
    assertEquals(folderList, partitioned.folders)
  }

  @Test
  fun `partitionNetworkFiles with pure media files routes all entries to videos without dropping any`() {
    val mediaFiles = listOf(
      NetworkFile(name = "movie1.mp4", path = "/movie1.mp4", isDirectory = false, size = 1000L),
      NetworkFile(name = "movie2.mkv", path = "/movie2.mkv", isDirectory = false, size = 2000L),
      NetworkFile(name = "movie3.avi", path = "/movie3.avi", isDirectory = false, size = 3000L),
      NetworkFile(name = "audio1.mp3", path = "/audio1.mp3", isDirectory = false, size = 4000L),
      NetworkFile(name = "audio2.flac", path = "/audio2.flac", isDirectory = false, size = 5000L),
      NetworkFile(name = "video4.webm", path = "/video4.webm", isDirectory = false, size = 6000L),
    )

    val partitioned = partitionNetworkFiles(mediaFiles)

    assertEquals(0, partitioned.folders.size)
    assertEquals(6, partitioned.videos.size)
    assertEquals(0, partitioned.otherFiles.size)
    assertFalse(partitioned.isEmpty)
    assertEquals(6, partitioned.totalCount)
    assertEquals(mediaFiles, partitioned.videos)
  }

  @Test
  fun `partitionNetworkFiles with pure non-media files routes all entries to otherFiles without dropping any`() {
    val otherFiles = listOf(
      NetworkFile(name = "subs.srt", path = "/subs.srt", isDirectory = false, size = 120L),
      NetworkFile(name = "subs2.vtt", path = "/subs2.vtt", isDirectory = false, size = 130L),
      NetworkFile(name = "info.nfo", path = "/info.nfo", isDirectory = false, size = 40L),
      NetworkFile(name = "readme.txt", path = "/readme.txt", isDirectory = false, size = 50L),
      NetworkFile(name = "manual.pdf", path = "/manual.pdf", isDirectory = false, size = 500L),
      NetworkFile(name = "extras.zip", path = "/extras.zip", isDirectory = false, size = 8000L),
    )

    val partitioned = partitionNetworkFiles(otherFiles)

    assertEquals(0, partitioned.folders.size)
    assertEquals(0, partitioned.videos.size)
    assertEquals(6, partitioned.otherFiles.size)
    assertFalse(partitioned.isEmpty)
    assertEquals(6, partitioned.totalCount)
    assertEquals(otherFiles, partitioned.otherFiles)
  }

  @Test
  fun `partitionNetworkFiles with heterogeneous mix completely categorizes all items without data loss`() {
    val heterogeneousFiles = listOf(
      NetworkFile(name = "Season 1", path = "/Season 1", size = 0L, isDirectory = true),
      NetworkFile(name = "Season 2", path = "/Season 2", size = 0L, isDirectory = true),
      NetworkFile(name = "ep01.mkv", path = "/ep01.mkv", isDirectory = false, size = 500_000_000L),
      NetworkFile(name = "ep01.srt", path = "/ep01.srt", isDirectory = false, size = 25_000L),
      NetworkFile(name = "ep02.mp4", path = "/ep02.mp4", isDirectory = false, size = 480_000_000L),
      NetworkFile(name = "ep02.ass", path = "/ep02.ass", isDirectory = false, size = 35_000L),
      NetworkFile(name = "soundtrack.flac", path = "/soundtrack.flac", isDirectory = false, size = 30_000_000L),
      NetworkFile(name = "cover.jpg", path = "/cover.jpg", isDirectory = false, size = 120_000L),
      NetworkFile(name = "metadata.nfo", path = "/metadata.nfo", isDirectory = false, size = 2_000L),
      NetworkFile(name = "release.zip", path = "/release.zip", isDirectory = false, size = 15_000_000L),
    )

    val partitioned = partitionNetworkFiles(heterogeneousFiles)

    assertEquals(
      "Sum of partitioned categories must equal total input file count",
      heterogeneousFiles.size,
      partitioned.folders.size + partitioned.videos.size + partitioned.otherFiles.size,
    )
    assertEquals(heterogeneousFiles.size, partitioned.totalCount)

    assertEquals(2, partitioned.folders.size)
    assertTrue(partitioned.folders.all { it.isDirectory })

    assertEquals(3, partitioned.videos.size)
    assertTrue(partitioned.videos.all { !it.isDirectory })

    assertEquals(5, partitioned.otherFiles.size)
    assertTrue(partitioned.otherFiles.all { !it.isDirectory })

    assertEquals(listOf("/Season 1", "/Season 2"), partitioned.folders.map { it.path })
    assertEquals(listOf("/ep01.mkv", "/ep02.mp4", "/soundtrack.flac"), partitioned.videos.map { it.path })
    assertEquals(
      listOf("/ep01.srt", "/ep02.ass", "/cover.jpg", "/metadata.nfo", "/release.zip"),
      partitioned.otherFiles.map { it.path },
    )
  }

  @Test
  fun `folder containing ONLY non-media files is NOT reported as empty`() {
    val subtitleOnlyFiles = listOf(
      NetworkFile(name = "subs.srt", path = "/subs.srt", isDirectory = false, size = 1500L),
      NetworkFile(name = "subs.vtt", path = "/subs.vtt", isDirectory = false, size = 1600L),
    )

    val partitioned = partitionNetworkFiles(subtitleOnlyFiles)

    assertEquals(0, partitioned.folders.size)
    assertEquals(0, partitioned.videos.size)
    assertEquals(2, partitioned.otherFiles.size)
    assertFalse("Directory with non-media files must NOT be considered empty", partitioned.isEmpty)
  }

  @Test
  fun `gallery mode configuration invariants across diverse device widths`() {
    val screenWidths = listOf(320, 360, 412, 600, 720, 900, 1200)

    for (width in screenWidths) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.GALLERY, screenWidthDp = width)

      assertEquals(NetworkViewMode.GALLERY, config.viewMode)
      assertTrue("Gallery must use grid container flag", config.isGridMode)
      assertEquals("Gallery hero cards must use single column across all widths", 1, config.gridColumns)
      assertEquals(16f / 9f, config.thumbnailAspectRatio, 0.001f)
      assertTrue("Gallery target dimension must be high-res >= 512px", config.targetDimensionPx >= 512)
      assertTrue("Gallery displays metadata chips", config.showChips)
      assertFalse("Gallery is not detail mode", config.isDetailMode)
      assertEquals(width.dp, config.thumbnailSizeDp)
    }
  }

  @Test
  fun `grid mode responsive column invariants across phone, tablet, and desktop breakpoints`() {
    for (width in listOf(320, 360, 400, 480, 599)) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = width)
      assertEquals("Phone width $width dp must yield 2 columns", 2, config.gridColumns)
      assertTrue(config.isGridMode)
      assertFalse("Grid cells suppress chips", config.showChips)
      assertEquals(384, config.targetDimensionPx)
    }

    for (width in listOf(600, 720, 800, 899)) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = width)
      assertEquals("Tablet width $width dp must yield 3 columns", 3, config.gridColumns)
      assertTrue(config.isGridMode)
      assertFalse(config.showChips)
    }

    for (width in listOf(900, 1024, 1200, 1600)) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = width)
      assertEquals("Desktop width $width dp must yield 4 columns", 4, config.gridColumns)
      assertTrue(config.isGridMode)
      assertFalse(config.showChips)
    }
  }

  @Test
  fun `grid cells adaptive layout column count scaling invariants`() {
    fun adaptiveColumns(widthDp: Int, minSizeDp: Int = 160): Int =
      maxOf(1, widthDp / minSizeDp)

    assertEquals(2, adaptiveColumns(360))
    assertEquals(2, adaptiveColumns(400))
    assertEquals(3, adaptiveColumns(600))
    assertEquals(4, adaptiveColumns(720))
    assertEquals(5, adaptiveColumns(840))
    assertEquals(6, adaptiveColumns(1024))
  }

  @Test
  fun `list mode layout configuration invariants across screen widths`() {
    val screenWidths = listOf(360, 600, 900)

    for (width in screenWidths) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.LIST, screenWidthDp = width)

      assertEquals(NetworkViewMode.LIST, config.viewMode)
      assertFalse("List mode is horizontal row, not grid", config.isGridMode)
      assertEquals(1, config.gridColumns)
      assertEquals(128.dp, config.thumbnailSizeDp)
      assertEquals(16f / 9f, config.thumbnailAspectRatio, 0.001f)
      assertTrue("List mode displays metadata chips", config.showChips)
      assertFalse("List mode is not detail mode", config.isDetailMode)
      assertEquals(256, config.targetDimensionPx)
    }
  }

  @Test
  fun `detail mode layout configuration invariants across screen widths`() {
    val screenWidths = listOf(360, 600, 900)

    for (width in screenWidths) {
      val config = CardLayoutConfigProvider.configFor(NetworkViewMode.DETAIL, screenWidthDp = width)

      assertEquals(NetworkViewMode.DETAIL, config.viewMode)
      assertFalse("Detail mode is horizontal row, not grid", config.isGridMode)
      assertEquals(1, config.gridColumns)
      assertEquals(40.dp, config.thumbnailSizeDp)
      assertEquals(1f, config.thumbnailAspectRatio, 0.001f)
      assertEquals("Detail mode title is strictly 1 line", 1, config.maxTitleLines)
      assertFalse("Detail mode suppresses chip flow rows", config.showChips)
      assertTrue("Detail mode flag is set", config.isDetailMode)
      assertEquals(96, config.targetDimensionPx)
    }
  }

  @Test
  fun `unlimitedNameLines preference expands maxTitleLines on gallery, grid, list while preserving 1 in detail`() {
    val gallery = CardLayoutConfigProvider.configFor(NetworkViewMode.GALLERY, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, gallery.maxTitleLines)

    val grid = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, grid.maxTitleLines)

    val list = CardLayoutConfigProvider.configFor(NetworkViewMode.LIST, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, list.maxTitleLines)

    val detail = CardLayoutConfigProvider.configFor(NetworkViewMode.DETAIL, unlimitedNameLines = true)
    assertEquals(
      "Detail mode must strictly preserve single-line title density even when unlimitedNameLines is enabled",
      1,
      detail.maxTitleLines,
    )
  }

  @Test
  fun `resolveFastScrollbarBinding binds gridState and null listState in GRID mode`() {
    val dummyListState = mockk<LazyListState>(relaxed = true)
    val dummyGridState = mockk<LazyGridState>(relaxed = true)

    val (activeList, activeGrid) = resolveFastScrollbarBinding(
      viewMode = NetworkViewMode.GRID,
      listState = dummyListState,
      gridState = dummyGridState,
    )

    assertNull("In GRID mode, listState must be null to avoid dual scrollbar binding", activeList)
    assertNotNull("In GRID mode, gridState must be non-null", activeGrid)
    assertSame("In GRID mode, the provided gridState must be bound", dummyGridState, activeGrid)
  }

  @Test
  fun `resolveFastScrollbarBinding binds listState and null gridState in GALLERY, LIST, and DETAIL modes`() {
    val dummyListState = mockk<LazyListState>(relaxed = true)
    val dummyGridState = mockk<LazyGridState>(relaxed = true)

    val columnModes = listOf(
      NetworkViewMode.GALLERY,
      NetworkViewMode.LIST,
      NetworkViewMode.DETAIL,
    )

    for (mode in columnModes) {
      val (activeList, activeGrid) = resolveFastScrollbarBinding(
        viewMode = mode,
        listState = dummyListState,
        gridState = dummyGridState,
      )

      assertNotNull("In $mode mode, listState must be non-null", activeList)
      assertSame("In $mode mode, the provided listState must be bound", dummyListState, activeList)
      assertNull("In $mode mode, gridState must be null", activeGrid)
    }
  }

  @Test
  fun `grid scroll row and target index calculation invariants for FastScrollbar`() {
    fun computeRows(totalItems: Int, columns: Int): Int =
      (totalItems + columns - 1) / columns

    fun computeFirstRow(firstItemIndex: Int, columns: Int): Int =
      firstItemIndex / columns

    fun computeTargetIndex(targetRow: Int, columns: Int, totalItems: Int): Int =
      (targetRow * columns).coerceIn(0, totalItems - 1)

    assertEquals(5, computeRows(10, 2))
    assertEquals(0, computeFirstRow(0, 2))
    assertEquals(0, computeFirstRow(1, 2))
    assertEquals(1, computeFirstRow(2, 2))
    assertEquals(4, computeFirstRow(8, 2))
    assertEquals(8, computeTargetIndex(4, 2, 10))

    assertEquals(4, computeRows(11, 3))
    assertEquals(0, computeFirstRow(2, 3))
    assertEquals(1, computeFirstRow(3, 3))
    assertEquals(3, computeFirstRow(9, 3))
    assertEquals(9, computeTargetIndex(3, 3, 11))

    assertEquals(1, computeRows(1, 4))
    assertEquals(0, computeTargetIndex(0, 4, 1))
  }
}
