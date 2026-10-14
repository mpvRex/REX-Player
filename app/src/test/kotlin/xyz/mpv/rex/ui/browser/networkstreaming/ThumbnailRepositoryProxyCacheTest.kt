package xyz.mpv.rex.ui.browser.networkstreaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailRepositoryProxyCacheTest {

  private lateinit var tempDir: File
  private val context = mockk<Context>(relaxed = true)
  private val appearancePreferences = mockk<AppearancePreferences>(relaxed = true)
  private val testMemoryCache = mockk<LruCache<String, Bitmap>>(relaxed = true)
  private val cacheStorage = ConcurrentHashMap<String, Bitmap>()

  private val sampleConnection1 = NetworkConnection(
    id = 1L,
    name = "Server1",
    protocol = NetworkProtocol.SMB,
    host = "192.168.1.10",
    port = 445,
    path = "Share1",
  )

  private val sampleConnection2 = NetworkConnection(
    id = 2L,
    name = "Server2",
    protocol = NetworkProtocol.SMB,
    host = "192.168.1.20",
    port = 445,
    path = "Share2",
  )

  private fun createRepo(): ThumbnailRepository =
    ThumbnailRepository(context, testMemoryCache)

  @Before
  fun setUp() {
    mockkStatic(Uri::class)
    val mockUri = mockk<Uri>(relaxed = true)
    every { Uri.parse(any()) } returns mockUri

    tempDir = Files.createTempDirectory("thumb-repo-test").toFile()
    every { context.filesDir } returns tempDir
    every { appearancePreferences.showNetworkThumbnails.get() } returns true

    cacheStorage.clear()
    every { testMemoryCache.get(any()) } answers { cacheStorage[firstArg()] }
    every { testMemoryCache.put(any(), any()) } answers {
      cacheStorage[firstArg()] = secondArg()
      secondArg()
    }
    every { testMemoryCache.remove(any()) } answers { cacheStorage.remove(firstArg()) }
    every { testMemoryCache.evictAll() } answers { cacheStorage.clear() }
    every { testMemoryCache.snapshot() } answers { HashMap(cacheStorage) }

    stopKoin()
    startKoin {
      modules(
        module {
          single { appearancePreferences }
        }
      )
    }
  }

  @After
  fun tearDown() {
    stopKoin()
    unmockkStatic(Uri::class)
    tempDir.deleteRecursively()
  }

  @Test
  fun `networkProxyThumbnailKey constructs expected key format with connection isolation`() {
    val path = "/Movies/Action/The Matrix (1999).mkv"
    val dimension = 256

    val key1 = ThumbnailRepository.networkProxyThumbnailKey(1L, path, dimension)
    val key2 = ThumbnailRepository.networkProxyThumbnailKey(2L, path, dimension)
    val defaultKey = ThumbnailRepository.networkProxyThumbnailKey(path, dimension)

    assertEquals("1|$path|networkProxy|$dimension", key1)
    assertEquals("2|$path|networkProxy|$dimension", key2)
    assertEquals("0|$path|networkProxy|$dimension", defaultKey)
    assertNotEquals(key1, key2)
  }

  @Test
  fun `networkProxyThumbnailKey distinguishes between different dimensions for the same path and connection`() {
    val path = "/Movies/sample.mp4"
    val keySmall = ThumbnailRepository.networkProxyThumbnailKey(1L, path, 128)
    val keyLarge = ThumbnailRepository.networkProxyThumbnailKey(1L, path, 512)

    assertNotEquals(keySmall, keyLarge)
    assertEquals("1|$path|networkProxy|128", keySmall)
    assertEquals("1|$path|networkProxy|512", keyLarge)
  }

  @Test
  fun `networkProxyThumbnailKey distinguishes between different paths with same dimension`() {
    val keyA = ThumbnailRepository.networkProxyThumbnailKey(1L, "/a.mp4", 256)
    val keyB = ThumbnailRepository.networkProxyThumbnailKey(1L, "/b.mp4", 256)

    assertNotEquals(keyA, keyB)
  }

  @Test
  fun `networkProxyThumbnailDiskKey constructs dimension-agnostic key with connection isolation`() {
    val path = "/Movies/sample.mp4"
    val diskKey1 = ThumbnailRepository.networkProxyThumbnailDiskKey(1L, path)
    val diskKey2 = ThumbnailRepository.networkProxyThumbnailDiskKey(2L, path)
    val defaultDiskKey = ThumbnailRepository.networkProxyThumbnailDiskKey(path)

    assertEquals("1|$path|networkProxy", diskKey1)
    assertEquals("2|$path|networkProxy", diskKey2)
    assertEquals("0|$path|networkProxy", defaultDiskKey)
    assertNotEquals(diskKey1, diskKey2)
  }

  @Test
  fun `getThumbnailViaProxyFromMemory respects connection ID isolation`() {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    val keyConn1 = ThumbnailRepository.networkProxyThumbnailKey(1L, "/video.mp4", 128)
    testMemoryCache.put(keyConn1, fakeBitmap)

    val result1 = repo.getThumbnailViaProxyFromMemory(1L, "/video.mp4", 128)
    assertNotNull(result1)
    assertEquals(fakeBitmap, result1)

    val result2 = repo.getThumbnailViaProxyFromMemory(2L, "/video.mp4", 128)
    assertNull("Connection 2 must not collide with Connection 1 cache", result2)
  }

  @Test
  fun `getThumbnailViaProxyFromMemory returns null when showNetworkThumbnails preference is false`() {
    every { appearancePreferences.showNetworkThumbnails.get() } returns false
    val repo = createRepo()

    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    testMemoryCache.put(ThumbnailRepository.networkProxyThumbnailKey(1L, "/video.mp4", 128), fakeBitmap)

    val result = repo.getThumbnailViaProxyFromMemory(1L, "/video.mp4", 128)
    assertNull("Must return null when showNetworkThumbnails is false", result)
  }

  @Test
  fun `isNetworkThumbnailFailed isolates failures by connection ID and supports clearing`() {
    val repo = createRepo()
    val path = "/corrupted_video.mp4"

    assertFalse(repo.isNetworkThumbnailFailed(1L, path))
    assertFalse(repo.isNetworkThumbnailFailed(2L, path))

    repo.markNetworkThumbnailFailed(1L, path)
    assertTrue("Connection 1 should be failed", repo.isNetworkThumbnailFailed(1L, path))
    assertFalse("Connection 2 should not be failed", repo.isNetworkThumbnailFailed(2L, path))

    repo.clearNetworkThumbnailFailures(1L)
    assertFalse(repo.isNetworkThumbnailFailed(1L, path))
  }

  @Test
  fun `getThumbnailViaProxy successfully extracts and caches bitmap`() = runTest {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap.compress(any(), any(), any()) } returns true

    repo.proxyExtractorForTesting = { _, _ -> fakeBitmap }

    val result = repo.getThumbnailViaProxy(
      path = "/movies/video.mp4",
      name = "video.mp4",
      size = 1024L,
      connection = sampleConnection1,
      dimension = 256,
    )

    assertEquals(fakeBitmap, result)
    val memCached = repo.getThumbnailViaProxyFromMemory(1L, "/movies/video.mp4", 256)
    assertEquals(fakeBitmap, memCached)
  }

  @Test
  fun `getThumbnailViaProxy deduplicates concurrent requests for the same media`() = runTest {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap.compress(any(), any(), any()) } returns true

    val executionCount = AtomicInteger(0)
    val gate = CompletableDeferred<Unit>()

    repo.proxyExtractorForTesting = { _, _ ->
      executionCount.incrementAndGet()
      gate.await()
      fakeBitmap
    }

    val job1 = async {
      repo.getThumbnailViaProxy(
        path = "/movies/shared.mp4",
        name = "shared.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
    }

    val job2 = async {
      repo.getThumbnailViaProxy(
        path = "/movies/shared.mp4",
        name = "shared.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
    }

    gate.complete(Unit)

    val res1 = job1.await()
    val res2 = job2.await()

    assertEquals(fakeBitmap, res1)
    assertEquals(fakeBitmap, res2)
    assertEquals("Extractor should only run once due to deduplication", 1, executionCount.get())
  }

  @Test
  fun `getThumbnailViaProxy throttles concurrency with semaphore permits`() = runTest {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap.compress(any(), any(), any()) } returns true

    val activeCount = AtomicInteger(0)
    val maxSeen = AtomicInteger(0)
    val finishGate = CompletableDeferred<Unit>()

    repo.proxyExtractorForTesting = { _, _ ->
      val current = activeCount.incrementAndGet()
      maxSeen.updateAndGet { maxOf(it, current) }
      finishGate.await()
      activeCount.decrementAndGet()
      fakeBitmap
    }

    val jobs = (1..6).map { i ->
      async {
        repo.getThumbnailViaProxy(
          path = "/movies/file_$i.mp4",
          name = "file_$i.mp4",
          size = 1024L,
          connection = sampleConnection1,
          dimension = 256,
        )
      }
    }

    delay(50)
    assertTrue("At most 3 concurrent extractions allowed by semaphore", maxSeen.get() <= 3)

    finishGate.complete(Unit)
    jobs.forEach { it.await() }
  }

  @Test
  fun `getThumbnailViaProxy cooperative cancellation decrements refCount and cancels background job`() = runTest {
    val repo = createRepo()
    val extractorStarted = CompletableDeferred<Unit>()
    val extractorCancelled = CompletableDeferred<Boolean>()

    repo.proxyExtractorForTesting = { _, _ ->
      try {
        extractorStarted.complete(Unit)
        delay(10_000)
        null
      } catch (e: CancellationException) {
        extractorCancelled.complete(true)
        throw e
      }
    }

    val callerJob = launch {
      repo.getThumbnailViaProxy(
        path = "/movies/cancelled.mp4",
        name = "cancelled.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
    }

    extractorStarted.await()
    callerJob.cancelAndJoin()

    val wasCancelled = extractorCancelled.await()
    assertTrue("Background job must be cooperatively cancelled when caller cancels", wasCancelled)
  }

  @Test
  fun `getThumbnailViaProxy records failure on extraction failure and clears on refresh`() = runTest {
    val repo = createRepo()
    val extractorCalls = AtomicInteger(0)

    repo.proxyExtractorForTesting = { _, _ ->
      extractorCalls.incrementAndGet()
      null
    }

    val result1 = repo.getThumbnailViaProxy(
      path = "/movies/broken.mp4",
      name = "broken.mp4",
      size = 1024L,
      connection = sampleConnection1,
      dimension = 256,
    )
    assertNull(result1)
    assertEquals(1, extractorCalls.get())
    assertTrue(repo.isNetworkThumbnailFailed(1L, "/movies/broken.mp4"))

    val result2 = repo.getThumbnailViaProxy(
      path = "/movies/broken.mp4",
      name = "broken.mp4",
      size = 1024L,
      connection = sampleConnection1,
      dimension = 256,
    )
    assertNull(result2)
    assertEquals("Extractor should not be invoked while failed", 1, extractorCalls.get())

    repo.clearNetworkThumbnailFailures(1L)
    assertFalse(repo.isNetworkThumbnailFailed(1L, "/movies/broken.mp4"))
  }

  @Test
  fun `clearConnectionThumbnailCache isolates memory, disk, and failure tracking per connection ID`() = runTest {
    val repo = createRepo()
    val fakeBitmap1 = mockk<Bitmap>(relaxed = true)
    val fakeBitmap2 = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap1.compress(any(), any(), any()) } returns true
    every { fakeBitmap2.compress(any(), any(), any()) } returns true

    repo.proxyExtractorForTesting = { _, _ -> fakeBitmap1 }
    repo.getThumbnailViaProxy(
      path = "/movies/film.mp4",
      name = "film.mp4",
      size = 1024L,
      connection = sampleConnection1,
      dimension = 256,
    )

    repo.proxyExtractorForTesting = { _, _ -> fakeBitmap2 }
    repo.getThumbnailViaProxy(
      path = "/movies/film.mp4",
      name = "film.mp4",
      size = 1024L,
      connection = sampleConnection2,
      dimension = 256,
    )

    repo.markNetworkThumbnailFailed(1L, "/movies/bad.mp4")
    repo.markNetworkThumbnailFailed(2L, "/movies/bad.mp4")

    val conn1Dir = File(tempDir, "thumbnails/network/1")
    val conn2Dir = File(tempDir, "thumbnails/network/2")
    assertTrue(conn1Dir.exists())
    assertTrue(conn2Dir.exists())

    repo.clearConnectionThumbnailCache(1L)

    assertNull(repo.getThumbnailViaProxyFromMemory(1L, "/movies/film.mp4", 256))
    assertFalse(repo.isNetworkThumbnailFailed(1L, "/movies/bad.mp4"))
    assertFalse(conn1Dir.exists())

    assertEquals(fakeBitmap2, repo.getThumbnailViaProxyFromMemory(2L, "/movies/film.mp4", 256))
    assertTrue(repo.isNetworkThumbnailFailed(2L, "/movies/bad.mp4"))
    assertTrue(conn2Dir.exists())
  }

  @Test
  fun `getThumbnailViaProxy cooperative cancellation preserves job for other active callers when refCount is greater than 1`() = runTest {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap.compress(any(), any(), any()) } returns true

    val extractorStarted = CompletableDeferred<Unit>()
    val finishGate = CompletableDeferred<Unit>()

    repo.proxyExtractorForTesting = { _, _ ->
      extractorStarted.complete(Unit)
      finishGate.await()
      fakeBitmap
    }

    val caller1 = async {
      repo.getThumbnailViaProxy(
        path = "/movies/shared_multi.mp4",
        name = "shared_multi.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
    }

    val caller2 = async {
      repo.getThumbnailViaProxy(
        path = "/movies/shared_multi.mp4",
        name = "shared_multi.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
    }

    extractorStarted.await()

    caller1.cancel()
    finishGate.complete(Unit)

    val result2 = caller2.await()
    assertEquals(fakeBitmap, result2)
  }

  @Test
  fun `clearNetworkThumbnailFailures emits cleared connection ID on networkThumbnailFailuresCleared flow`() = runTest {
    val repo = createRepo()
    val received = mutableListOf<Long?>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
      repo.networkThumbnailFailuresCleared.collect { received.add(it) }
    }

    repo.clearNetworkThumbnailFailures(1L)
    repo.clearNetworkThumbnailFailures(null)

    job.cancel()

    assertEquals(listOf(1L, null), received)
  }

  @Test
  fun `getThumbnailViaProxy reuses dimension-agnostic disk cache across different view mode dimensions`() = runTest {
    mockkStatic(BitmapFactory::class)
    try {
      val repo = createRepo()
      val fakeBitmap = mockk<Bitmap>(relaxed = true)
      every { fakeBitmap.compress(any(), any(), any()) } returns true
      every { BitmapFactory.decodeFile(any(), any()) } returns fakeBitmap

      var extractCount = 0
      repo.proxyExtractorForTesting = { _, _ ->
        extractCount++
        fakeBitmap
      }

      val resGallery = repo.getThumbnailViaProxy(
        path = "/movies/video.mp4",
        name = "video.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 512,
      )
      assertEquals(fakeBitmap, resGallery)
      assertEquals(1, extractCount)

      val connDir = File(tempDir, "thumbnails/network/1")
      assertTrue(connDir.exists())
      val diskFiles = connDir.listFiles()
      assertNotNull(diskFiles)
      assertEquals(1, diskFiles!!.size)

      testMemoryCache.evictAll()

      val resList = repo.getThumbnailViaProxy(
        path = "/movies/video.mp4",
        name = "video.mp4",
        size = 1024L,
        connection = sampleConnection1,
        dimension = 256,
      )
      assertEquals(fakeBitmap, resList)
      assertEquals(1, extractCount)
    } finally {
      unmockkStatic(BitmapFactory::class)
    }
  }

  @Test
  fun `getThumbnailViaProxy requests at least 512 dimension to prevent low-res disk cache`() = runTest {
    val repo = createRepo()
    val fakeBitmap = mockk<Bitmap>(relaxed = true)
    every { fakeBitmap.compress(any(), any(), any()) } returns true
    var capturedDimension = 0
    repo.proxyExtractorForTesting = { _, dim ->
      capturedDimension = dim
      fakeBitmap
    }

    val result = repo.getThumbnailViaProxy(
      path = "/movies/lowres.mp4",
      name = "lowres.mp4",
      size = 1024L,
      connection = sampleConnection1,
      dimension = 128,
    )

    assertEquals(fakeBitmap, result)
    assertEquals(512, capturedDimension)
  }
}
