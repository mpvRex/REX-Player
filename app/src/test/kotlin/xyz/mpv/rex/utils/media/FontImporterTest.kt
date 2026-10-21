package xyz.mpv.rex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FontImporterTest {
  private val familyOk: (File) -> String? = { "Test Family" }
  private val ttfOtf = setOf("ttf", "otf")
  private val mpvDirExtensions = setOf("ttf", "otf", "ttc", "woff", "woff2")

  private fun newDir(): File = Files.createTempDirectory("font_import_test").toFile().also { it.deleteOnExit() }

  /** A fonts directory inside its own private parent, so "did anything escape?" checks can't see other tests' files. */
  /** Runs the clear action and returns how many fonts it deleted; fails the test if it did not run. */
  private fun clearedCount(dir: File): Int {
    val result = clearFontFiles(dir)
    assertTrue(result.toString(), result is FontClearResult.Cleared)
    return (result as FontClearResult.Cleared).deleted
  }

  private fun newNestedDir(): File = File(newDir(), "fonts").also { it.mkdirs() }

  /** Minimal bytes that start like a TrueType font; [seed] makes contents differ. */
  private fun fontBytes(seed: Int, size: Int = 512): ByteArray =
    ByteArray(size) { ((it * 31 + seed) and 0xFF).toByte() }.also {
      it[0] = 0; it[1] = 1; it[2] = 0; it[3] = 0
    }

  private fun source(name: String?, bytes: ByteArray) =
    FontSource(displayName = { name }, open = { ByteArrayInputStream(bytes) })

  private fun failingSource(name: String, failAfter: Int = 0) =
    FontSource(
      displayName = { name },
      open = {
        object : InputStream() {
          private var served = 0
          override fun read(): Int {
            if (served++ >= failAfter) throw IOException("No space left on device")
            return 1
          }
        }
      },
    )

  private fun File.fonts() = listFiles().orEmpty().filter { !it.name.startsWith(".") }.map { it.name }.sorted()

  private fun File.leftovers() = listFiles().orEmpty().filter { it.name.endsWith(".part") }

  @Test
  fun validFont_isPublished_andNoStagingLeft() {
    val dir = newDir()
    val bytes = fontBytes(1)
    val outcome = importFontSource(dir, source("My Font.ttf", bytes), familyOk)
    assertEquals(FontImportOutcome.ADDED, outcome)
    assertEquals(listOf("My Font.ttf"), dir.fonts())
    assertTrue(File(dir, "My Font.ttf").readBytes().contentEquals(bytes))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun invalidSignature_isRejected_andNotPublished() {
    val dir = newDir()
    val outcome = importFontSource(dir, source("fake.ttf", "<html>not a font</html>".toByteArray()), familyOk)
    assertEquals(FontImportOutcome.FAILED, outcome)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun parserRejects_isRejected_andNotPublished() {
    val dir = newDir()
    val outcome = importFontSource(dir, source("noname.otf", fontBytes(2)), { null })
    assertEquals(FontImportOutcome.FAILED, outcome)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("blank.otf", fontBytes(2)), { "  " }))
  }

  @Test
  fun emptyFile_isRejected() {
    val dir = newDir()
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("empty.ttf", ByteArray(0)), familyOk))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun sameNameSameContent_isAlreadyPresent_andUntouched() {
    val dir = newDir()
    val bytes = fontBytes(3)
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("A.ttf", bytes), familyOk))
    val before = File(dir, "A.ttf").lastModified()
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("A.ttf", bytes), familyOk))
    assertEquals(listOf("A.ttf"), dir.fonts())
    assertEquals(before, File(dir, "A.ttf").lastModified())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun differentNameSameContent_isAlreadyPresent_noDuplicateFile() {
    val dir = newDir()
    val bytes = fontBytes(4)
    importFontSource(dir, source("A.ttf", bytes), familyOk)
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("Copy of A.ttf", bytes), familyOk))
    assertEquals(listOf("A.ttf"), dir.fonts())
  }

  @Test
  fun sameNameDifferentContent_neverOverwrites_usesAlternativeName() {
    val dir = newDir()
    val original = fontBytes(5)
    importFontSource(dir, source("A.ttf", original), familyOk)
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("A.ttf", fontBytes(6)), familyOk))
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("A.ttf", fontBytes(7)), familyOk))
    assertEquals(listOf("A (1).ttf", "A (2).ttf", "A.ttf"), dir.fonts())
    assertTrue(File(dir, "A.ttf").readBytes().contentEquals(original)) // original intact
    assertTrue(File(dir, "A (1).ttf").readBytes().contentEquals(fontBytes(6)))
  }

  @Test
  fun alternativeName_keepsExtension_otf() {
    val dir = newDir()
    importFontSource(dir, source("B.otf", fontBytes(8)), familyOk)
    importFontSource(dir, source("B.otf", fontBytes(9)), familyOk)
    assertEquals(listOf("B (1).otf", "B.otf"), dir.fonts())
  }

  @Test
  fun oversizedFile_isRejected_whileCopying_andCleaned() {
    val dir = newDir()
    val outcome = importFontSource(dir, source("big.ttf", fontBytes(10, size = 5000)), familyOk, maxBytes = 1000)
    assertEquals(FontImportOutcome.FAILED, outcome)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun unreadableSource_nullStream_orThrowing_isFailed_andCleaned() {
    val dir = newDir()
    val nullStream = FontSource(displayName = { "n.ttf" }, open = { null })
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, nullStream, familyOk))
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, failingSource("t.ttf", failAfter = 100), familyOk))
    val throwingName = FontSource(displayName = { throw IOException("boom") }, open = { null })
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, throwingName, familyOk))
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun fontsDirectoryUnusable_isFailed_withoutThrowing() {
    val notADir = File.createTempFile("not_a_dir", ".tmp").also { it.deleteOnExit() }
    assertEquals(FontImportOutcome.FAILED, importFontSource(notADir, source("A.ttf", fontBytes(11)), familyOk))
  }

  @Test
  fun batch_oneFailingFile_doesNotStopTheOthers() {
    val dir = newDir()
    val errors = mutableListOf<Throwable>()
    val result =
      importFontSources(
        dir,
        listOf(
          source("one.ttf", fontBytes(12)),
          failingSource("two.ttf", failAfter = 50), // fails while being read
          source("three.otf", fontBytes(13)),
        ),
        familyOk,
        onError = { errors += it },
      )
    assertEquals(FontImportResult(added = 2, alreadyPresent = 0, failed = 1), result)
    assertEquals(listOf("one.ttf", "three.otf"), dir.fonts())
    assertEquals(1, errors.size)
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun batch_mixedOutcomes_areCountedAccurately() {
    val dir = newDir()
    val same = fontBytes(14)
    importFontSource(dir, source("exists.ttf", same), familyOk)
    val result =
      importFontSources(
        dir,
        listOf(
          source("exists.ttf", same), // already present
          source("new.ttf", fontBytes(15)), // added
          source("bad.ttf", "garbage".toByteArray()), // invalid
          source("notfont.txt", fontBytes(16)), // unsupported extension
        ),
        familyOk,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 2), result)
  }

  // ---- family to apply automatically after Add Font File ----

  private val familyFromContent: (File) -> String? = { f -> "Family-" + f.readBytes()[8].toInt() }

  private fun fontWithFamilyByte(seed: Int, marker: Int): ByteArray = fontBytes(seed).also { it[8] = marker.toByte() }

  @Test
  fun singleNewFont_reportsItsFamily_readFromTheFontNotTheFileName() {
    val dir = newDir()
    val report =
      importFontSourcesReport(dir, listOf(source("whatever-name.ttf", fontWithFamilyByte(30, 7))), familyFromContent)
    assertEquals(FontImportResult(added = 1, alreadyPresent = 0, failed = 0), report.result)
    assertEquals("Family-7", report.familyToApply)
  }

  @Test
  fun singleFontAlreadyInstalled_stillReportsItsFamily() {
    val dir = newDir()
    val bytes = fontWithFamilyByte(31, 9)
    importFontSource(dir, source("first.ttf", bytes), familyFromContent)
    val report = importFontSourcesReport(dir, listOf(source("again.ttf", bytes)), familyFromContent)
    assertEquals(FontImportResult(added = 0, alreadyPresent = 1, failed = 0), report.result)
    assertEquals("Family-9", report.familyToApply)
  }

  @Test
  fun singleInvalidFont_reportsNothingToApply() {
    val dir = newDir()
    val report = importFontSourcesReport(dir, listOf(source("bad.ttf", "garbage".toByteArray())), familyFromContent)
    assertEquals(FontImportResult(added = 0, alreadyPresent = 0, failed = 1), report.result)
    assertNull(report.familyToApply)
  }

  @Test
  fun singleFont_withUnreadableFamily_isRejected_andNothingIsApplied() {
    val dir = newDir()
    val report = importFontSourcesReport(dir, listOf(source("x.ttf", fontBytes(32))), { null })
    assertEquals(1, report.result.failed)
    assertNull(report.familyToApply)
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun multipleFonts_areAllImported_butNothingIsApplied() {
    val dir = newDir()
    val report =
      importFontSourcesReport(
        dir,
        listOf(
          source("a.ttf", fontWithFamilyByte(33, 1)),
          source("b.otf", fontWithFamilyByte(34, 2)),
          source("c.ttf", fontWithFamilyByte(35, 3)),
        ),
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 3, alreadyPresent = 0, failed = 0), report.result)
    assertNull(report.familyToApply)
    assertEquals(listOf("a.ttf", "b.otf", "c.ttf"), dir.fonts())
  }

  @Test
  fun multipleFilesPicked_onlyOneSucceeds_thatFontIsApplied() {
    val dir = newDir()
    val report =
      importFontSourcesReport(
        dir,
        listOf(
          source("broken.ttf", "not a font".toByteArray()),
          source("good.otf", fontWithFamilyByte(36, 4)),
          failingSource("io-error.ttf", failAfter = 20),
          source("notfont.txt", fontBytes(39)),
        ),
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 0, failed = 3), report.result)
    assertEquals(1, report.acceptedCount)
    assertEquals("Family-4", report.familyToApply)
    assertEquals(listOf("good.otf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun multipleFilesPicked_twoOrMoreSucceed_noneIsApplied_andAllAreImported() {
    val dir = newDir()
    val report =
      importFontSourcesReport(
        dir,
        listOf(
          source("good1.ttf", fontWithFamilyByte(40, 4)),
          source("broken.ttf", "not a font".toByteArray()),
          source("good2.otf", fontWithFamilyByte(41, 5)),
        ),
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 2, alreadyPresent = 0, failed = 1), report.result)
    assertEquals(2, report.acceptedCount)
    assertNull(report.familyToApply)
    assertEquals(listOf("good1.ttf", "good2.otf"), dir.fonts())
  }

  @Test
  fun multipleFilesPicked_allFail_noneIsApplied() {
    val dir = newDir()
    val report =
      importFontSourcesReport(
        dir,
        listOf(source("a.ttf", "x".toByteArray()), source("b.otf", "y".toByteArray()), source("c.txt", fontBytes(42))),
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 0, alreadyPresent = 0, failed = 3), report.result)
    assertEquals(0, report.acceptedCount)
    assertNull(report.familyToApply)
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun multipleFilesPicked_oneSucceeds_butItsFamilyIsUnreadable_noneIsApplied() {
    val dir = newDir()
    val report =
      importFontSourcesReport(
        dir,
        listOf(source("a.ttf", fontBytes(43)), source("bad.ttf", "junk".toByteArray())),
        { null },
      )
    assertEquals(0, report.acceptedCount)
    assertNull(report.familyToApply)
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun multipleFilesPicked_oneNewAndOneAlreadyInstalled_countsAsTwo_noneIsApplied() {
    val dir = newDir()
    val installed = fontWithFamilyByte(44, 8)
    importFontSource(dir, source("old.ttf", installed), familyFromContent)
    val report =
      importFontSourcesReport(
        dir,
        listOf(source("old-again.ttf", installed), source("new.ttf", fontWithFamilyByte(45, 9))),
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 0), report.result)
    assertEquals(2, report.acceptedCount)
    assertNull(report.familyToApply)
  }

  @Test
  fun filePathSources_asUsedByTheInAppPicker_followTheSameBatchRules() {
    val dir = newDir()
    val picked = newDir()
    val good = File(picked, "Good.ttf").also { it.writeBytes(fontWithFamilyByte(46, 3)) }
    val bad = File(picked, "Bad.otf").also { it.writeBytes("not a font".toByteArray()) }
    val missing = File(picked, "Gone.ttf") // deleted between selection and confirmation
    val report =
      importFontSourcesReport(
        dir,
        listOf(bad, good, missing).map { f -> FontSource(displayName = { f.name }, open = { f.inputStream() }) },
        familyFromContent,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 0, failed = 2), report.result)
    assertEquals("Family-3", report.familyToApply)
    assertEquals(listOf("Good.ttf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---- importFontPaths: the code importFontsFromPaths runs (the Android wrapper only adds the fonts directory,
  // the family reader and the logger), exercised directly ----

  private fun pickedFont(dir: File, name: String, seed: Int, marker: Int) =
    File(dir, name).also { it.writeBytes(fontWithFamilyByte(seed, marker)) }

  @Test
  fun importFontPaths_ignoresDuplicatePaths_andIsolatesBadFiles() {
    val dir = newDir()
    val picked = newDir()
    val a = pickedFont(picked, "A.ttf", 60, 1)
    val b = pickedFont(picked, "B.otf", 61, 2)
    val bad = File(picked, "Bad.ttf").also { it.writeBytes("nope".toByteArray()) }
    val gone = File(picked, "Gone.ttf")
    val report = importFontPaths(dir, listOf(a, bad, a, gone, b).map { it.path }, familyFromContent)
    assertEquals(FontImportResult(added = 2, alreadyPresent = 0, failed = 2), report.result)
    assertEquals(2, report.acceptedCount)
    assertNull(report.familyToApply)
    assertEquals(listOf("A.ttf", "B.otf"), dir.fonts())
  }

  @Test
  fun importFontPaths_oneAcceptedAmongSeveral_reportsThatFamily() {
    val dir = newDir()
    val picked = newDir()
    val only = pickedFont(picked, "Only.ttf", 62, 5)
    val bad = File(picked, "Bad.otf").also { it.writeBytes("nope".toByteArray()) }
    val report = importFontPaths(dir, listOf(bad.path, only.path), familyFromContent)
    assertEquals(FontImportResult(added = 1, alreadyPresent = 0, failed = 1), report.result)
    assertEquals("Family-5", report.familyToApply)
  }

  // ---- Lifecycle: a confirmed import must not die with the settings panel ----

  @Test
  fun confirmedImport_onFontImportScope_completesEvenIfThePanelScopeIsCancelledMidBatch() {
    val dir = newDir()
    val picked = newDir()
    val paths = (1..4).map { pickedFont(picked, "F$it.ttf", 70 + it, it).path }
    val firstFileReading = CountDownLatch(1)
    val panelClosed = CountDownLatch(1)
    val done = CompletableDeferred<FontImportBatchReport>()
    val panelScope = CoroutineScope(Job())

    panelScope.launch {
      // What the panel does on "Import": hand the batch to fontImportScope and move on.
      fontImportScope.launch {
        val report =
          importFontPaths(
            dir,
            paths,
            { f ->
              firstFileReading.countDown()
              panelClosed.await(5, TimeUnit.SECONDS)
              familyFromContent(f)
            },
            checkCancelled = { ensureActive() },
          )
        done.complete(report)
      }
    }

    assertTrue(firstFileReading.await(5, TimeUnit.SECONDS))
    panelScope.cancel() // the panel is closed while the batch is running
    panelClosed.countDown()

    val report = runBlocking { withTimeout(10_000) { done.await() } }
    assertEquals(FontImportResult(added = 4, alreadyPresent = 0, failed = 0), report.result)
    assertNull(report.familyToApply) // four accepted fonts: the active font stays
    assertEquals(listOf("F1.ttf", "F2.ttf", "F3.ttf", "F4.ttf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun sameBatch_runInThePanelScope_wouldBeCutShort_whichIsWhyItIsNotUsed() {
    val dir = newDir()
    val picked = newDir()
    val paths = (1..4).map { pickedFont(picked, "F$it.ttf", 80 + it, it).path }
    val firstFileReading = CountDownLatch(1)
    val panelClosed = CountDownLatch(1)
    val finished = CountDownLatch(1)
    val panelScope = CoroutineScope(Job())

    panelScope.launch {
      try {
        importFontPaths(
          dir,
          paths,
          { f ->
            firstFileReading.countDown()
            panelClosed.await(5, TimeUnit.SECONDS)
            familyFromContent(f)
          },
          checkCancelled = { ensureActive() },
        )
      } catch (e: CancellationException) {
        // expected: cancelled between files
      } finally {
        finished.countDown()
      }
    }

    assertTrue(firstFileReading.await(5, TimeUnit.SECONDS))
    panelScope.cancel()
    panelClosed.countDown()
    assertTrue(finished.await(10, TimeUnit.SECONDS))
    assertEquals(listOf("F1.ttf"), dir.fonts()) // only the file in progress was finished
  }

  @Test
  fun launchFontImport_quicklyConfirmedBatches_runInConfirmationOrder_neverOverlapping() {
    val count = 40
    val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
    val active = java.util.concurrent.atomic.AtomicInteger(0)
    val maxActive = java.util.concurrent.atomic.AtomicInteger(0)
    val gate = CountDownLatch(1)
    val allDone = CountDownLatch(count)

    // All batches are confirmed back to back from this thread, while the first one is still blocked: nothing waits
    // for an earlier batch to "start", which is exactly the race a coroutine-per-batch + lock design would lose.
    for (i in 0 until count) {
      launchFontImport {
        val now = active.incrementAndGet()
        maxActive.updateAndGet { maxOf(it, now) }
        if (i == 0) gate.await(5, TimeUnit.SECONDS)
        order += i
        active.decrementAndGet()
        allDone.countDown()
      }
    }
    gate.countDown()

    assertTrue(allDone.await(10, TimeUnit.SECONDS))
    assertEquals((0 until count).toList(), order.toList())
    assertEquals(1, maxActive.get())
  }

  @Test
  fun launchFontImport_realBatchesConfirmedBackToBack_importInOrder_andFirstFailureDoesNotBlockTheNext() {
    val dir = newDir()
    val picked = newDir()
    val first = (1..3).map { pickedFont(picked, "A$it.ttf", 90 + it, it).path }
    val second = (1..3).map { pickedFont(picked, "B$it.ttf", 95 + it, it + 3).path }
    val third = listOf(pickedFont(picked, "C.ttf", 99, 9).path)
    val finishedOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    val reports = java.util.Collections.synchronizedMap(mutableMapOf<String, FontImportBatchReport>())
    val done = CountDownLatch(3)
    val gate = CountDownLatch(1)

    launchFontImport {
      gate.await(5, TimeUnit.SECONDS)
      try {
        reports["A"] = importFontPaths(dir, first, familyFromContent)
        throw IllegalStateException("follow-up of the first batch fails")
      } finally {
        finishedOrder += "A"
        done.countDown()
      }
    }
    launchFontImport {
      reports["B"] = importFontPaths(dir, second, familyFromContent)
      finishedOrder += "B"
      done.countDown()
    }
    launchFontImport {
      reports["C"] = importFontPaths(dir, third, familyFromContent)
      finishedOrder += "C"
      done.countDown()
    }
    gate.countDown()

    assertTrue(done.await(10, TimeUnit.SECONDS))
    assertEquals(listOf("A", "B", "C"), finishedOrder.toList())
    assertEquals(3, reports.getValue("A").result.added)
    assertEquals(3, reports.getValue("B").result.added)
    assertEquals("Family-9", reports.getValue("C").familyToApply) // the newest batch is the one that applies last
    assertEquals(7, dir.fonts().size)
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun importedFonts_surviveRestart_theyAreJustFilesPlusManifest() {
    val dir = newDir()
    importFontSourcesReport(dir, listOf(source("keep.ttf", fontWithFamilyByte(38, 6))), familyFromContent)
    // "Restart": nothing in memory is needed - a fresh pass over the same directory still sees the font, and it is
    // still protected from "Clear cached fonts" because the manifest records it.
    assertEquals(listOf("keep.ttf"), dir.fonts())
    assertEquals(0, clearedCount(dir))
    assertEquals(listOf("keep.ttf"), dir.fonts())
  }

  @Test
  fun sanitize_removesPathsAndUnsafeCharacters() {
    assertEquals("evil.ttf", sanitizeFontFileName("../../evil.ttf"))
    assertEquals("evil.ttf", sanitizeFontFileName("..\\..\\evil.ttf"))
    assertEquals("evil.ttf", sanitizeFontFileName("/data/data/app/evil.ttf"))
    assertEquals("a_b_c.otf", sanitizeFontFileName("a:b*c.otf"))
    assertEquals("Osama Font.TTF", sanitizeFontFileName("  Osama Font.TTF "))
    assertEquals("خط عربي.ttf", sanitizeFontFileName("خط عربي.ttf"))
  }

  @Test
  fun sanitize_rejectsUnsupportedOrEmptyNames() {
    assertNull(sanitizeFontFileName(null))
    assertNull(sanitizeFontFileName(""))
    assertNull(sanitizeFontFileName("font.txt"))
    assertNull(sanitizeFontFileName("font.ttc"))
    assertNull(sanitizeFontFileName(".ttf"))
    assertNull(sanitizeFontFileName("..ttf"))
    assertNull(sanitizeFontFileName("noextension"))
    assertNull(sanitizeFontFileName("../"))
  }

  @Test
  fun sanitize_boundsLength_keepingExtension() {
    val name = sanitizeFontFileName("x".repeat(400) + ".otf")!!
    assertTrue(name.length <= 125 && name.endsWith(".otf"))
  }

  @Test
  fun traversalName_neverEscapesFontsDirectory() {
    val dir = newNestedDir()
    val outer = dir.parentFile
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("../escape.ttf", fontBytes(17)), familyOk))
    assertTrue(File(dir, "escape.ttf").exists())
    assertFalse(File(outer, "escape.ttf").exists())
  }

  @Test
  fun staleStagingFiles_areCleaned_recentOnesKept() {
    val dir = newDir()
    val stale = File(dir, ".fontimport_old.part").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 3_600_000) }
    val recent = File(dir, ".fontimport_new.part").apply { writeText("x") }
    importFontSource(dir, source("A.ttf", fontBytes(18)), familyOk)
    assertFalse(stale.exists())
    assertTrue(recent.exists())
  }

  @Test
  fun sfntSignature_acceptsKnownHeaders_only() {
    fun check(vararg b: Int): Boolean {
      val f = File.createTempFile("sig", ".bin").also { it.deleteOnExit() }
      f.writeBytes(ByteArray(b.size) { b[it].toByte() } + ByteArray(20))
      return hasSfntSignature(f)
    }
    assertTrue(check(0, 1, 0, 0))
    assertTrue(check('O'.code, 'T'.code, 'T'.code, 'O'.code))
    assertTrue(check('t'.code, 'r'.code, 'u'.code, 'e'.code))
    assertFalse(check('<'.code, 'h'.code, 't'.code, 'm'.code))
    assertFalse(check(0, 0, 0, 0))
  }

  // ---------------- Real font parser (TTFFile) integration ----------------
  // These use readFontFamilyName, the exact adapter the app uses, with a real 680-byte TTF generated for this project
  // (embedded below as Base64, family "Rex Test Font"; self-made, so no third-party licence).

  private val REX_TEST_FONT_BASE64 =
    (
      "AAEAAAAKAIAAAwAgT1MvMkUAQ9EAAAEoAAAAYGNtYXAAdABcAAABlAAAADxnbHlmTjxOOgAAAdgAAAA0aGVhZC9mGgIAAACsAAAA" +
      "NmhoZWEFegH4AAAA5AAAACRobXR4BdwAyAAAAYgAAAAMbG9jYQANACcAAAHQAAAACG1heHAABQAGAAABCAAAACBuYW1lJhK89wAA" +
      "AgwAAABycG9zdAAIACQAAAKAAAAAKAABAAAAAQAAvGXDQV8PPPUAAwPoAAAAAOb76zMAAAAA5vvrMwBkAAAB9AK8AAAAAwACAAAA" +
      "AAAAAAEAAAMg/zgAAAJYAGQAZAH0AAEAAAAAAAAAAAAAAAAAAAADAAEAAAADAAQAAQAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAwH0" +
      "AZAABQAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAPz8/PwAAACAAQQMg/zgAAAMgAMgA" +
      "AAAAAAAAAAAAAAAAAAAgAAACWABkASwAAAJYAGQAAAACAAAAAwAAABQAAwABAAAAFAAEACgAAAAGAAQAAQACACAAQf//AAAAIABB" +
      "////4f/BAAEAAAAAAAAAAAANAA0AGgABAGQAAAH0ArwAAwAAMxEhEWQBkAK8/UQAAAEAZAAAAfQCvAADAAAzESERZAGQArz9RAAA" +
      "AAAEADYAAQAAAAAAAQANAAAAAQAAAAAAAgAHAA0AAwABBAkAAQAaABQAAwABBAkAAgAOAC5SZXggVGVzdCBGb250UmVndWxhcgBS" +
      "AGUAeAAgAFQAZQBzAHQAIABGAG8AbgB0AFIAZQBnAHUAbABhAHIAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMA" +
      "AAADACQ="
    )

  private fun realFontBytes(): ByteArray = java.util.Base64.getDecoder().decode(REX_TEST_FONT_BASE64)

  @Test
  fun realFont_isAcceptedByRealParser_andPublished() {
    val dir = newDir()
    val bytes = realFontBytes()
    val outcome = importFontSource(dir, source("Real.ttf", bytes), ::readFontFamilyName)
    assertEquals(FontImportOutcome.ADDED, outcome)
    assertEquals(listOf("Real.ttf"), dir.fonts())
    assertEquals("Rex Test Font", readFontFamilyName(File(dir, "Real.ttf")))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun sfntSignatureOnly_isRejectedByRealParser() {
    val dir = newDir()
    // Starts with a valid sfnt signature (passes hasSfntSignature) but is not a font.
    val outcome = importFontSource(dir, source("Fake.ttf", fontBytes(7)), ::readFontFamilyName)
    assertEquals(FontImportOutcome.FAILED, outcome)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun realFont_sameNameDifferentContent_keepsOriginalIntact_withRealParser() {
    val dir = newDir()
    val original = realFontBytes()
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("F.ttf", original), ::readFontFamilyName))
    // Same bytes under another name -> duplicate, nothing new.
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("G.ttf", original), ::readFontFamilyName))
    assertEquals(listOf("F.ttf"), dir.fonts())
    assertTrue(File(dir, "F.ttf").readBytes().contentEquals(original))
  }

  // ---------------- Cancellation between files ----------------

  @Test
  fun cancellation_stopsBetweenFiles_keepsImportedOnes_andLeavesNoStaging() {
    val dir = newDir()
    var checks = 0
    val sources = listOf(source("One.ttf", fontBytes(21)), source("Two.ttf", fontBytes(22)), source("Three.ttf", fontBytes(23)))
    try {
      importFontSources(dir, sources, familyOk, checkCancelled = { if (++checks == 2) throw CancellationException("closed") })
      fail("expected cancellation")
    } catch (expected: CancellationException) {
      // The first file was fully imported before the checkpoint fired; the others were never touched.
    }
    assertEquals(listOf("One.ttf"), dir.fonts())
    assertTrue(File(dir, "One.ttf").readBytes().contentEquals(fontBytes(21)))
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---------------- Shared lock with the other writers ----------------
  //
  // Deterministic: instead of sleeping and hoping the worker reached the lock, the test waits until the JVM reports the
  // worker as BLOCKED (a thread that is waiting to enter a synchronized block is in exactly that state), checks that
  // nothing happened yet, and only then releases the lock.

  private fun awaitBlocked(t: Thread) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (t.state != Thread.State.BLOCKED) {
      if (!t.isAlive) fail("worker finished without ever waiting for fontsDirLock")
      if (System.nanoTime() > deadline) fail("worker never blocked on fontsDirLock (state=${t.state})")
      Thread.sleep(2)
    }
  }

  /** Runs [action] on a worker while another thread holds [fontsDirLock]; [whileBlocked] runs once it is provably waiting. */
  private fun <T> withLockHeldByOther(action: () -> T, whileBlocked: () -> Unit): T? {
    val lockTaken = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder =
      thread {
        synchronized(fontsDirLock) {
          lockTaken.countDown()
          release.await(20, TimeUnit.SECONDS)
        }
      }
    assertTrue(lockTaken.await(10, TimeUnit.SECONDS))
    var result: T? = null
    val worker = thread { result = action() }
    try {
      awaitBlocked(worker)
      whileBlocked()
    } finally {
      release.countDown()
    }
    worker.join(10_000)
    holder.join(10_000)
    assertFalse(worker.isAlive)
    return result
  }

  @Test
  fun import_waitsWhileAnotherWriterHoldsTheFontsDirLock() {
    val dir = newDir()
    val outcome =
      withLockHeldByOther(
        action = { importFontSource(dir, source("A.ttf", fontBytes(31)), familyOk) },
        whileBlocked = { assertTrue(dir.fonts().isEmpty()) },
      )
    assertEquals(FontImportOutcome.ADDED, outcome)
    assertEquals(listOf("A.ttf"), dir.fonts())
  }

  @Test
  fun clearFontFiles_waitsForTheLock_thenDeletesOnlyFonts() {
    val dir = newDir()
    File(dir, "A.ttf").writeBytes(fontBytes(32)) // copied by the folder / sync paths (not imported)
    File(dir, "B.otf").writeBytes(fontBytes(33))
    File(dir, "readme.txt").writeText("keep me")
    val deleted =
      withLockHeldByOther(
        action = { clearFontFiles(dir) },
        whileBlocked = { assertEquals(listOf("A.ttf", "B.otf", "readme.txt"), dir.fonts()) }, // untouched while blocked
      )
    assertEquals(FontClearResult.Cleared(deleted = 2, failed = 0), deleted)
    assertEquals(listOf("readme.txt"), dir.fonts())
  }

  @Test
  fun clearFontFiles_onMissingOrEmptyDirectory_isHarmless() {
    assertEquals(0, clearedCount(File(newDir(), "does_not_exist")))
    assertEquals(0, clearedCount(newDir()))
  }

  @Test
  fun importAfterClear_startsFromAnEmptyDirectory() {
    val dir = newDir()
    val bytes = fontBytes(34)
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "A.ttf", bytes))
    clearFontFiles(dir)
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("A.ttf", bytes), familyOk)) // not "already present"
    assertEquals(listOf("A.ttf"), dir.fonts())
  }

  @Test
  fun copyFontIfAbsent_waitsForTheLock() {
    val dir = newDir()
    val copied =
      withLockHeldByOther(
        action = { copyFontIfAbsent(dir, "A.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(35)) }) },
        whileBlocked = { assertTrue(dir.fonts().isEmpty()) },
      )
    assertEquals(true, copied)
    assertEquals(listOf("A.ttf"), dir.fonts())
  }

  // ---------------- copyFontIfAbsent (Font Folder copy / MPV config sync) ----------------

  @Test
  fun copyFontIfAbsent_copiesNewFile_withoutLeftovers() {
    val dir = File(newDir(), "fonts") // directory is created on demand
    val bytes = fontBytes(36)
    assertTrue(copyFontIfAbsent(dir, "New.ttf", ttfOtf, { ByteArrayInputStream(bytes) }))
    assertEquals(listOf("New.ttf"), dir.fonts())
    assertTrue(File(dir, "New.ttf").readBytes().contentEquals(bytes))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun copyFontIfAbsent_neverOverwritesAnImportedFont() {
    // The scenario from the review: "Add Font File" first, then the Font Folder holds a DIFFERENT file with that name.
    val dir = newDir()
    val imported = fontBytes(37)
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Osama.ttf", imported), familyOk))
    repeat(3) { // repeated syncs must not change anything either
      assertFalse(copyFontIfAbsent(dir, "Osama.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(38)) }))
    }
    assertEquals(listOf("Osama.ttf"), dir.fonts())
    assertTrue(File(dir, "Osama.ttf").readBytes().contentEquals(imported))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun import_afterFolderCopy_keepsTheFolderFont_andAddsTheImportUnderAnotherName() {
    val dir = newDir()
    val fromFolder = fontBytes(39)
    assertTrue(copyFontIfAbsent(dir, "Same.ttf", ttfOtf, { ByteArrayInputStream(fromFolder) }))
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Same.ttf", fontBytes(40)), familyOk))
    assertEquals(listOf("Same (1).ttf", "Same.ttf"), dir.fonts())
    assertTrue(File(dir, "Same.ttf").readBytes().contentEquals(fromFolder))
  }

  @Test
  fun copyFontIfAbsent_failures_leaveNothingBehind() {
    val dir = newDir()
    assertFalse(copyFontIfAbsent(dir, "n.ttf", ttfOtf, { null }))
    val errors = mutableListOf<Throwable>()
    val failing = failingSource("t.ttf", failAfter = 100)
    assertFalse(copyFontIfAbsent(dir, "t.ttf", ttfOtf, failing.open, onError = { errors += it }))
    assertEquals(1, errors.size)
    assertFalse(copyFontIfAbsent(dir, "empty.ttf", ttfOtf, { ByteArrayInputStream(ByteArray(0)) }))
    assertFalse(copyFontIfAbsent(dir, "big.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(41, size = 5000)) }, maxBytes = 1000))
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun copyFontIfAbsent_rejectsNamesWithPathComponents() {
    val dir = newNestedDir()
    val outer = dir.parentFile
    for (bad in listOf("../escape.ttf", "sub/escape.ttf", "", ".", "..")) {
      assertFalse(copyFontIfAbsent(dir, bad, ttfOtf, { ByteArrayInputStream(fontBytes(42)) }))
    }
    assertFalse(File(outer, "escape.ttf").exists())
    assertTrue(dir.fonts().isEmpty())
  }

  // ---------------- Batch size budget ----------------

  @Test
  fun batch_stopsStoringOnceTheBudgetIsSpent() {
    val dir = newDir()
    val result =
      importFontSources(
        dir,
        listOf(
          source("a.ttf", fontBytes(51, size = 512)),
          source("b.ttf", fontBytes(52, size = 512)),
          source("c.ttf", fontBytes(53, size = 512)),
          source("d.ttf", fontBytes(54, size = 512)),
        ),
        familyOk,
        maxBatchBytes = 1100, // room for two 512-byte fonts; the third would not fit in what is left (76 bytes)
      )
    assertEquals(FontImportResult(added = 2, alreadyPresent = 0, failed = 0, skippedBatchLimit = 2), result)
    assertEquals(listOf("a.ttf", "b.ttf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun batch_duplicatesAndRejectedFiles_doNotConsumeTheBudget() {
    val dir = newDir()
    val same = fontBytes(55, size = 512)
    importFontSource(dir, source("exists.ttf", same), familyOk)
    val result =
      importFontSources(
        dir,
        listOf(
          source("exists.ttf", same), // already present: free
          source("bad.ttf", "garbage".toByteArray()), // invalid: nothing stored
          source("new.ttf", fontBytes(56, size = 512)), // fits the whole budget
        ),
        familyOk,
        maxBatchBytes = 512,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 1), result)
  }

  @Test
  fun batch_defaultBudget_isFarAboveNormalUse() {
    assertTrue(MAX_FONT_BATCH_BYTES >= 4 * MAX_FONT_FILE_BYTES)
    val dir = newDir()
    val result = importFontSources(dir, (1..20).map { source("f$it.ttf", fontBytes(100 + it)) }, familyOk)
    assertEquals(FontImportResult(added = 20, alreadyPresent = 0, failed = 0), result)
  }

  // ---------------- Batch budget: duplicates are checked first ----------------

  @Test
  fun budgetSpent_duplicateIsStillReportedAsAlreadyPresent_notFailed() {
    val dir = newDir()
    val same = fontBytes(60, size = 512)
    val result =
      importFontSources(
        dir,
        listOf(source("first.ttf", same), source("second name.ttf", same)), // same bytes, different name
        familyOk,
        maxBatchBytes = 512, // exactly spent by the first font
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 0, skippedBatchLimit = 0), result)
    assertEquals(listOf("first.ttf"), dir.fonts())
  }

  @Test
  fun remainingBudgetSmallerThanDuplicate_duplicateIsStillAlreadyPresent() {
    val dir = newDir()
    val big = fontBytes(61, size = 800)
    val result =
      importFontSources(
        dir,
        listOf(
          source("small.ttf", fontBytes(62, size = 300)), // leaves 100 of a 400 budget
          source("big.ttf", big), // new, 800 > 100 -> limit (and must not be stored)
          source("again.ttf", fontBytes(62, size = 300)), // duplicate of small.ttf, 300 > 100 remaining
        ),
        familyOk,
        maxBatchBytes = 400,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 0, skippedBatchLimit = 1), result)
    assertEquals(listOf("small.ttf"), dir.fonts())
  }

  @Test
  fun newFontOverBudget_isNeverPartiallyPublished_andLeavesNoStaging() {
    val dir = newDir()
    val attempt =
      importFontSourceDetailed(dir, source("Big.ttf", fontBytes(63, size = 4000)), familyOk, batchBudgetBytes = 1000)
    assertEquals(FontImportOutcome.SKIPPED_BATCH_LIMIT, attempt.outcome)
    assertEquals(0L, attempt.bytesAdded)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
    assertFalse(File(dir, ".imported_fonts").exists()) // nothing recorded either
  }

  @Test
  fun invalidFile_whenBudgetIsSpent_isStillReportedAsFailed_notAsLimit() {
    val dir = newDir()
    val result =
      importFontSources(
        dir,
        listOf(source("a.ttf", fontBytes(64)), source("junk.ttf", "garbage".toByteArray())),
        familyOk,
        maxBatchBytes = 512,
      )
    assertEquals(FontImportResult(added = 1, alreadyPresent = 0, failed = 1, skippedBatchLimit = 0), result)
  }

  // ---------------- Imported-font manifest ----------------

  @Test
  fun import_recordsTheStoredName_onlyForAddedFonts() {
    val dir = newDir()
    val bytes = fontBytes(70)
    importFontSource(dir, source("A.ttf", bytes), familyOk)
    importFontSource(dir, source("A.ttf", fontBytes(71)), familyOk) // -> "A (1).ttf"
    importFontSource(dir, source("dup.ttf", bytes), familyOk) // already present: not recorded
    assertEquals(listOf("A (1).ttf", "A.ttf"), File(dir, ".imported_fonts").readLines().sorted())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun import_failsWithoutPublishing_whenTheManifestCannotBeWritten() {
    val dir = newDir()
    File(dir, ".imported_fonts").mkdir() // a directory where the manifest should be: unreadable and unwritable
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("A.ttf", fontBytes(72)), familyOk))
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---------------- Font Folder sync (syncFolderFont) ----------------

  private fun folderSync(dir: File, name: String, bytes: ByteArray, extensions: Set<String> = ttfOtf) =
    syncFolderFont(dir, name, extensions, { ByteArrayInputStream(bytes) })

  @Test
  fun folderSync_addsThenUpdatesThenLeavesUnchanged() {
    val dir = newDir()
    val v1 = fontBytes(80)
    val v2 = fontBytes(81)
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Folder.ttf", v1))
    assertEquals(FolderFontOutcome.UNCHANGED, folderSync(dir, "Folder.ttf", v1))
    val mtime = File(dir, "Folder.ttf").lastModified()
    assertEquals(FolderFontOutcome.UNCHANGED, folderSync(dir, "Folder.ttf", v1)) // repeated sync: no rewrite
    assertEquals(mtime, File(dir, "Folder.ttf").lastModified())
    assertEquals(FolderFontOutcome.UPDATED, folderSync(dir, "Folder.ttf", v2)) // the original refresh behaviour
    assertTrue(File(dir, "Folder.ttf").readBytes().contentEquals(v2))
    assertEquals(listOf("Folder.ttf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun folderSync_neverReplacesAnImportedFont_evenRepeatedly() {
    val dir = newDir()
    val imported = fontBytes(82)
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Osama.ttf", imported), familyOk))
    repeat(3) {
      assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Osama.ttf", fontBytes(83)))
    }
    assertEquals(listOf("Osama.ttf"), dir.fonts())
    assertTrue(File(dir, "Osama.ttf").readBytes().contentEquals(imported))
  }

  @Test
  fun importWithTheNameOfAFolderFont_keepsBoth_andOnlyTheFolderOneIsRefreshed() {
    val dir = newDir()
    folderSync(dir, "Same.ttf", fontBytes(84))
    // The user imports a different font that happens to have the same file name -> stored under another name.
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Same.ttf", fontBytes(85)), familyOk))
    assertEquals(listOf("Same (1).ttf", "Same.ttf"), dir.fonts())
    // The folder font changes: it is refreshed; the imported one is untouched.
    assertEquals(FolderFontOutcome.UPDATED, folderSync(dir, "Same.ttf", fontBytes(86)))
    assertTrue(File(dir, "Same.ttf").readBytes().contentEquals(fontBytes(86)))
    assertTrue(File(dir, "Same (1).ttf").readBytes().contentEquals(fontBytes(85)))
    // ...and an imported name is protected from then on.
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Same (1).ttf", fontBytes(87)))
    assertTrue(File(dir, "Same (1).ttf").readBytes().contentEquals(fontBytes(85)))
  }

  @Test
  fun folderSync_failedCopy_keepsTheExistingFont() {
    val dir = newDir()
    val v1 = fontBytes(88)
    folderSync(dir, "F.ttf", v1)
    val errors = mutableListOf<Throwable>()
    val outcome = syncFolderFont(dir, "F.ttf", ttfOtf, failingSource("x.ttf", failAfter = 100).open, onError = { errors += it })
    assertEquals(FolderFontOutcome.FAILED, outcome)
    assertEquals(1, errors.size)
    assertTrue(File(dir, "F.ttf").readBytes().contentEquals(v1))
    assertEquals(FolderFontOutcome.FAILED, syncFolderFont(dir, "F.ttf", ttfOtf, { null }))
    assertEquals(FolderFontOutcome.FAILED, folderSync(dir, "F.ttf", ByteArray(0)))
    assertTrue(File(dir, "F.ttf").readBytes().contentEquals(v1))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun folderSync_unreadableManifest_touchesNothing() {
    val dir = newDir()
    File(dir, ".imported_fonts").mkdir()
    assertEquals(FolderFontOutcome.FAILED, folderSync(dir, "F.ttf", fontBytes(89)))
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun folderSync_waitsForTheLock() {
    val dir = newDir()
    val outcome =
      withLockHeldByOther(
        action = { folderSync(dir, "A.ttf", fontBytes(90)) },
        whileBlocked = { assertTrue(dir.fonts().isEmpty()) },
      )
    assertEquals(FolderFontOutcome.ADDED, outcome)
  }

  // ---------------- Extension filtering per path ----------------

  @Test
  fun folderSync_rejectsNonFontFiles_andAcceptsTtfOtfInAnyCase() {
    val dir = newDir()
    for (name in listOf("notes.txt", "fonts.zip", "font.woff", "font.ttc", "noextension", "font.ttf.txt")) {
      assertEquals(name, FolderFontOutcome.SKIPPED, folderSync(dir, name, fontBytes(91)))
    }
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Upper.TTF", fontBytes(92)))
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Mixed.OtF", fontBytes(93)))
    assertEquals(listOf("Mixed.OtF", "Upper.TTF"), dir.fonts())
  }

  @Test
  fun folderSync_rejectsPathComponents() {
    val dir = newNestedDir()
    for (bad in listOf("../escape.ttf", "sub/escape.ttf", "", ".", "..")) {
      assertEquals(bad, FolderFontOutcome.SKIPPED, folderSync(dir, bad, fontBytes(94)))
    }
    assertFalse(File(dir.parentFile, "escape.ttf").exists())
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun mpvDirectorySync_keepsItsOwnExtensionSet_andStillRejectsGenericFiles() {
    val dir = newDir()
    for (name in listOf("a.ttf", "b.otf", "c.ttc", "d.woff", "e.woff2")) {
      assertTrue(name, copyFontIfAbsent(dir, name, mpvDirExtensions, { ByteArrayInputStream(fontBytes(95)) }))
    }
    for (name in listOf("notes.txt", "fonts.zip", "image.png")) {
      assertFalse(name, copyFontIfAbsent(dir, name, mpvDirExtensions, { ByteArrayInputStream(fontBytes(95)) }))
    }
    assertEquals(listOf("a.ttf", "b.otf", "c.ttc", "d.woff", "e.woff2"), dir.fonts())
  }

  // ---------------- Clear cached fonts with imported fonts ----------------

  @Test
  fun clear_keepsImportedFonts_removesFolderFonts_andFolderSyncRestoresThem() {
    val dir = newDir()
    val imported = fontBytes(100)
    val folderFont = fontBytes(101)
    importFontSource(dir, source("Mine.ttf", imported), familyOk)
    folderSync(dir, "FromFolder.ttf", folderFont)
    folderSync(dir, "FromFolder2.otf", fontBytes(102))
    File(dir, "readme.txt").writeText("keep me")

    assertEquals(2, clearedCount(dir))
    assertEquals(listOf("Mine.ttf", "readme.txt"), dir.fonts())
    assertTrue(File(dir, "Mine.ttf").readBytes().contentEquals(imported))

    // The next folder sync brings the folder fonts back; the imported font is still protected.
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "FromFolder.ttf", folderFont))
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Mine.ttf", fontBytes(103)))
    // Clearing again behaves the same (the manifest survived).
    assertEquals(1, clearedCount(dir))
    assertEquals(listOf("Mine.ttf", "readme.txt"), dir.fonts())
  }

  @Test
  fun clear_withoutManifest_removesEveryFont_likeTheOriginalAction() {
    val dir = newDir()
    File(dir, "legacy1.ttf").writeBytes(fontBytes(104))
    File(dir, "legacy2.OTF").writeBytes(fontBytes(105))
    assertEquals(2, clearedCount(dir))
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun clear_withUnreadableManifest_deletesNothing() {
    val dir = newDir()
    File(dir, ".imported_fonts").mkdir()
    File(dir, "legacy.ttf").writeBytes(fontBytes(106))
    val result = clearFontFiles(dir)
    assertTrue(result is FontClearResult.Unavailable) // reported as an error, not as "0 cleared"
    assertEquals(listOf("legacy.ttf"), dir.fonts())
  }

  // ---------------- Stale manifest records (app killed between "record" and "publish") ----------------

  private fun writeManifest(dir: File, vararg names: String) {
    File(dir, ".imported_fonts").writeText(names.joinToString("\n"))
  }

  private fun manifestNames(dir: File): List<String> =
    File(dir, ".imported_fonts").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.sorted().orEmpty()

  @Test
  fun staleRecord_folderCopyWithThatName_isNotTreatedAsImported() {
    val dir = newDir()
    writeManifest(dir, "Osama.ttf") // as left behind by a crash after "record" and before "publish"
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Osama.ttf", fontBytes(110)))
    assertEquals(emptyList<String>(), manifestNames(dir)) // the stale record was removed from disk
    // So it is an ordinary folder font: refreshed by the folder...
    assertEquals(FolderFontOutcome.UPDATED, folderSync(dir, "Osama.ttf", fontBytes(111)))
    assertTrue(File(dir, "Osama.ttf").readBytes().contentEquals(fontBytes(111)))
    // ...and removed by "Clear cached fonts".
    assertEquals(1, clearedCount(dir))
    assertTrue(dir.fonts().isEmpty())
  }

  @Test
  fun staleRecord_isPrunedWithoutTouchingLiveImports() {
    val dir = newDir()
    val imported = fontBytes(112)
    importFontSource(dir, source("Live.ttf", imported), familyOk)
    writeManifest(dir, "Live.ttf", "Ghost.ttf")
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Ghost.ttf", fontBytes(113)))
    assertEquals(listOf("Live.ttf"), manifestNames(dir))
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Live.ttf", fontBytes(114)))
    assertTrue(File(dir, "Live.ttf").readBytes().contentEquals(imported))
    assertEquals(1, clearedCount(dir)) // only Ghost.ttf (a folder font)
    assertEquals(listOf("Live.ttf"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun staleRecord_clearFirst_thenSync_behavesTheSame() {
    val dir = newDir()
    writeManifest(dir, "Ghost.ttf")
    assertEquals(0, clearedCount(dir)) // nothing to delete, and not an error
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Ghost.ttf", fontBytes(115)))
    assertEquals(1, clearedCount(dir))
  }

  @Test
  fun staleRecord_mpvDirectorySyncPrunesToo() {
    val dir = newDir()
    writeManifest(dir, "FromMpv.ttf")
    assertTrue(copyFontIfAbsent(dir, "FromMpv.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(116)) }))
    assertEquals(emptyList<String>(), manifestNames(dir))
    assertEquals(FolderFontOutcome.UPDATED, folderSync(dir, "FromMpv.ttf", fontBytes(117))) // not "kept imported"
  }

  @Test
  fun staleRecord_thatCannotBeRemoved_blocksCreatingTheFile() {
    val dir = newDir()
    writeManifest(dir, "Ghost.ttf")
    withPublisher({ _, target -> target.name != ".imported_fonts" }) { // the manifest cannot be rewritten
      assertEquals(FolderFontOutcome.FAILED, folderSync(dir, "Ghost.ttf", fontBytes(118)))
      assertFalse(copyFontIfAbsent(dir, "Ghost.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(118)) }))
    }
    assertTrue(dir.fonts().isEmpty()) // nothing was created under a still-recorded name
    assertEquals(listOf("Ghost.ttf"), manifestNames(dir))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun importAfterAStaleRecordForTheSameName_worksAndIsRecordedOnce() {
    val dir = newDir()
    writeManifest(dir, "Osama.ttf")
    assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Osama.ttf", fontBytes(119)), familyOk))
    assertEquals(listOf("Osama.ttf"), manifestNames(dir))
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Osama.ttf", fontBytes(120)))
  }

  // ---------------- Failure injection at the publish step ----------------

  /** Runs [block] with every publish (font or manifest) going through [publisher]; always restores the real one. */
  private fun withPublisher(publisher: (File, File) -> Boolean, block: () -> Unit) {
    val original = publishStagedFile
    publishStagedFile = publisher
    try {
      block()
    } finally {
      publishStagedFile = original
    }
  }

  private val explode: (File, File) -> Boolean = { _, _ -> throw SecurityException("injected publish failure") }

  @Test
  fun import_publishThrows_leavesNoFontNoStagingAndNoRecord() {
    val dir = newDir()
    val errors = mutableListOf<Throwable>()
    withPublisher({ staged, target -> if (target.name == ".imported_fonts") staged.renameTo(target) else explode(staged, target) }) {
      assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("A.ttf", fontBytes(121)), familyOk, onError = { errors += it }))
    }
    assertEquals(1, errors.size)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
    assertEquals(emptyList<String>(), manifestNames(dir)) // the record made before publishing was rolled back
  }

  @Test
  fun import_publishReturnsFalse_rollsBackTheRecord() {
    val dir = newDir()
    withPublisher({ staged, target -> if (target.name == ".imported_fonts") staged.renameTo(target) else false }) {
      assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("A.ttf", fontBytes(122)), familyOk))
    }
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
    assertEquals(emptyList<String>(), manifestNames(dir))
  }

  @Test
  fun manifestWrite_throws_import_isFailed_nothingPublished_noTempLeft() {
    val dir = newDir()
    withPublisher({ staged, target -> if (target.name == ".imported_fonts") explode(staged, target) else staged.renameTo(target) }) {
      assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("A.ttf", fontBytes(123)), familyOk))
    }
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun copyFontIfAbsent_publishThrows_leavesNothing() {
    val dir = newDir()
    val errors = mutableListOf<Throwable>()
    withPublisher(explode) {
      assertFalse(copyFontIfAbsent(dir, "A.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(124)) }, onError = { errors += it }))
    }
    assertEquals(1, errors.size)
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun folderSync_publishThrows_leavesExistingFontIntact_andNoStaging() {
    val dir = newDir()
    val v1 = fontBytes(125)
    folderSync(dir, "F.ttf", v1)
    val errors = mutableListOf<Throwable>()
    withPublisher(explode) {
      assertEquals(
        FolderFontOutcome.FAILED,
        syncFolderFont(dir, "F.ttf", ttfOtf, { ByteArrayInputStream(fontBytes(126)) }, onError = { errors += it }),
      )
      assertEquals(FolderFontOutcome.FAILED, folderSync(dir, "New.ttf", fontBytes(127)))
    }
    assertEquals(1, errors.size)
    assertEquals(listOf("F.ttf"), dir.fonts())
    assertTrue(File(dir, "F.ttf").readBytes().contentEquals(v1))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun successfulPublish_keepsTheFinalFile() {
    val dir = newDir()
    var published = 0
    withPublisher({ staged, target -> staged.renameTo(target).also { if (it) published++ } }) {
      assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Keep.ttf", fontBytes(128)))
      assertTrue(copyFontIfAbsent(dir, "Keep2.otf", ttfOtf, { ByteArrayInputStream(fontBytes(129)) }))
      assertEquals(FontImportOutcome.ADDED, importFontSource(dir, source("Keep3.ttf", fontBytes(130)), familyOk))
    }
    assertEquals(listOf("Keep.ttf", "Keep2.otf", "Keep3.ttf"), dir.fonts())
    assertTrue(published >= 3)
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---------------- Clear result distinguishes success from "could not run" ----------------

  @Test
  fun clear_reportsUnavailable_withACause_whenTheDirectoryCannotBeListed() {
    val notADirectory = File.createTempFile("fonts_is_a_file", ".tmp").also { it.deleteOnExit() }
    val result = clearFontFiles(notADirectory)
    assertTrue(result.toString(), result is FontClearResult.Unavailable)
    assertTrue((result as FontClearResult.Unavailable).cause is java.io.IOException)
  }

  @Test
  fun clear_unreadableManifest_carriesTheCause_andDeletesNothing() {
    val dir = newDir()
    File(dir, ".imported_fonts").mkdir()
    File(dir, "legacy.ttf").writeBytes(fontBytes(131))
    val result = clearFontFiles(dir)
    assertTrue(result is FontClearResult.Unavailable)
    assertEquals(listOf("legacy.ttf"), dir.fonts())
  }

  @Test
  fun clear_emptyDirectory_isSuccessWithZero_notAnError() {
    assertEquals(FontClearResult.Cleared(0, 0), clearFontFiles(newDir()))
  }

  // ---------------- Explicit import of a font that is already installed from a folder ----------------

  @Test
  fun importingAFolderFont_promotesIt_noDuplicate_thenFolderRefreshAndClearRespectTheChoice() {
    val dir = newDir()
    val bytes = fontBytes(140)
    // 1. The font arrives through the Font Folder.
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Osama.ttf", bytes))
    assertEquals(emptyList<String>(), manifestNames(dir))
    // 2. The user picks the same font with "Add Font File": already installed, no second file, but now owned by the user.
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("Whatever name.ttf", bytes), familyOk))
    assertEquals(listOf("Osama.ttf"), dir.fonts())
    assertEquals(listOf("Osama.ttf"), manifestNames(dir))
    // 3. The folder is refreshed with a changed font of that name: the imported copy is kept, not replaced.
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, folderSync(dir, "Osama.ttf", fontBytes(141)))
    assertTrue(File(dir, "Osama.ttf").readBytes().contentEquals(bytes))
    // 4. Other folder fonts stay refreshable and clearable; the promoted one survives "Clear cached fonts".
    assertEquals(FolderFontOutcome.ADDED, folderSync(dir, "Other.ttf", fontBytes(142)))
    assertEquals(1, clearedCount(dir))
    assertEquals(listOf("Osama.ttf"), dir.fonts())
    assertTrue(File(dir, "Osama.ttf").readBytes().contentEquals(bytes))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun importingAFolderFont_whenAnIdenticalImportedCopyExists_changesNothing() {
    val dir = newDir()
    val bytes = fontBytes(143)
    importFontSource(dir, source("Mine.ttf", bytes), familyOk) // imported
    folderSync(dir, "A Twin.ttf", bytes) // same bytes via the folder; sorts before "Mine.ttf", so a wrong promotion would pick it
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("Again.ttf", bytes), familyOk))
    assertEquals(listOf("Mine.ttf"), manifestNames(dir)) // not promoted: an imported copy already protects the font
    assertEquals(1, clearedCount(dir)) // the folder twin goes, the imported one stays
    assertEquals(listOf("Mine.ttf"), dir.fonts())
  }

  @Test
  fun importingAFolderFont_withSeveralFolderTwins_promotesExactlyOne_theFirstByName() {
    val dir = newDir()
    val bytes = fontBytes(144)
    folderSync(dir, "B.ttf", bytes)
    folderSync(dir, "A.ttf", bytes)
    assertEquals(FontImportOutcome.ALREADY_PRESENT, importFontSource(dir, source("x.ttf", bytes), familyOk))
    assertEquals(listOf("A.ttf"), manifestNames(dir))
    assertEquals(1, clearedCount(dir))
    assertEquals(listOf("A.ttf"), dir.fonts())
  }

  @Test
  fun importingAFolderFont_whenThePromotionCannotBeRecorded_isFailed_andNothingChanges() {
    val dir = newDir()
    val bytes = fontBytes(145)
    folderSync(dir, "F.ttf", bytes)
    withPublisher({ _, target -> target.name != ".imported_fonts" }) {
      assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("F.ttf", bytes), familyOk))
    }
    assertEquals(listOf("F.ttf"), dir.fonts())
    assertEquals(emptyList<String>(), manifestNames(dir))
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun importingAFolderFont_withAnUnreadableManifest_isFailed() {
    val dir = newDir()
    val bytes = fontBytes(146)
    File(dir, "F.ttf").writeBytes(bytes)
    File(dir, ".imported_fonts").mkdir()
    assertEquals(FontImportOutcome.FAILED, importFontSource(dir, source("F.ttf", bytes), familyOk))
    assertEquals(listOf("F.ttf"), dir.fonts())
  }

  @Test
  fun promotedFont_countsAsAlreadyInstalled_notAsAddedOrFailed_inABatch() {
    val dir = newDir()
    val bytes = fontBytes(147)
    folderSync(dir, "Folder.ttf", bytes)
    val result =
      importFontSources(dir, listOf(source("a.ttf", bytes), source("b.ttf", fontBytes(148))), familyOk, maxBatchBytes = 512)
    assertEquals(FontImportResult(added = 1, alreadyPresent = 1, failed = 0, skippedBatchLimit = 0), result)
    assertEquals(listOf("Folder.ttf", "b.ttf"), manifestNames(dir))
  }

  // ---------------- Size limit belongs to "Add Font File" only ----------------

  /** A stream of [size] bytes produced on the fly (never held in memory), so "bigger than the import limit" is cheap. */
  private fun generatedStream(size: Long): InputStream =
    object : InputStream() {
      private var left = size

      override fun read(): Int = if (left-- > 0) 7 else -1.also { left = 0 }

      override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = minOf(len.toLong(), left).toInt()
        java.util.Arrays.fill(b, off, off + n, 7)
        left -= n
        return n
      }
    }

  private fun File.deleteContents() = listFiles()?.forEach { it.delete() }

  @Test
  fun folderSync_hasNoSizeLimit_aFontLargerThanTheImportLimitIsStillCopiedAndRefreshed() {
    val dir = newDir()
    try {
      val big = MAX_FONT_FILE_BYTES + 1
      assertEquals(FolderFontOutcome.ADDED, syncFolderFont(dir, "Huge.ttf", ttfOtf, { generatedStream(big) }))
      assertEquals(big, File(dir, "Huge.ttf").length())
      // a refresh with an even larger font replaces it, as before
      assertEquals(FolderFontOutcome.UPDATED, syncFolderFont(dir, "Huge.ttf", ttfOtf, { generatedStream(big + 10) }))
      assertEquals(big + 10, File(dir, "Huge.ttf").length())
      assertTrue(dir.leftovers().isEmpty())
    } finally {
      dir.deleteContents()
    }
  }

  @Test
  fun mpvDirectorySync_hasNoSizeLimit_forItsExtraExtensionsToo() {
    val dir = newDir()
    try {
      val big = MAX_FONT_FILE_BYTES + 1
      assertTrue(copyFontIfAbsent(dir, "Huge.ttc", mpvDirExtensions, { generatedStream(big) }))
      assertEquals(big, File(dir, "Huge.ttc").length())
      assertTrue(dir.leftovers().isEmpty())
    } finally {
      dir.deleteContents()
    }
  }

  @Test
  fun addFontFile_stillEnforcesItsDefaultLimit_andLeavesNothingBehind() {
    val dir = newDir()
    try {
      val outcome =
        importFontSource(dir, FontSource(displayName = { "Huge.ttf" }, open = { generatedStream(MAX_FONT_FILE_BYTES + 1) }), familyOk)
      assertEquals(FontImportOutcome.FAILED, outcome)
      assertTrue(dir.fonts().isEmpty())
      assertTrue(dir.leftovers().isEmpty())
    } finally {
      dir.deleteContents()
    }
  }

  @Test
  fun explicitSizeLimit_onTheLegacyPaths_isStillHonoured_whenACallerAsksForOne() {
    val dir = newDir()
    assertEquals(FolderFontOutcome.FAILED, syncFolderFont(dir, "a.ttf", ttfOtf, { generatedStream(2000) }, maxBytes = 1000))
    assertFalse(copyFontIfAbsent(dir, "b.ttf", ttfOtf, { generatedStream(2000) }, maxBytes = 1000))
    assertTrue(dir.fonts().isEmpty())
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---------------- MPV config sync: the "Font Folder" part, through the function the sync actually calls ----------------

  private fun folderFiles(vararg entries: Pair<String, ByteArray>): List<FolderFile> =
    entries.map { (name, bytes) -> FolderFile(name) { ByteArrayInputStream(bytes) } }

  @Test
  fun mpvSyncFolder_copiesEveryFontType_theOriginalCopiedAndNoNonFontFiles() {
    assertEquals(setOf("ttf", "otf", "ttc", "woff", "woff2"), MPV_SYNC_FONT_EXTENSIONS) // the config-directory set
    val dir = newDir()
    val result =
      syncFolderFonts(
        dir,
        folderFiles(
          "a.ttf" to fontBytes(150),
          "b.otf" to fontBytes(151),
          "c.ttc" to fontBytes(152),
          "d.woff" to fontBytes(153),
          "e.woff2" to fontBytes(154),
          "UPPER.TTC" to fontBytes(155),
          "notes.txt" to fontBytes(156),
          "fonts.zip" to fontBytes(157),
          "preview.png" to fontBytes(158),
          "noextension" to fontBytes(159),
        ),
        MPV_SYNC_FONT_EXTENSIONS,
      )
    assertEquals(listOf("UPPER.TTC", "a.ttf", "b.otf", "c.ttc", "d.woff", "e.woff2"), dir.fonts())
    assertEquals(6, result.values.count { it == FolderFontOutcome.ADDED })
    assertEquals(4, result.values.count { it == FolderFontOutcome.SKIPPED })
    assertTrue(dir.leftovers().isEmpty())
  }

  @Test
  fun mpvSyncFolder_refreshesFolderFonts_includingTtc_andNeverReplacesImportedOnes() {
    val dir = newDir()
    val imported = fontBytes(160)
    importFontSource(dir, source("Mine.ttf", imported), familyOk)
    syncFolderFonts(dir, folderFiles("Shared.ttc" to fontBytes(161)), MPV_SYNC_FONT_EXTENSIONS)
    val second =
      syncFolderFonts(
        dir,
        folderFiles("Shared.ttc" to fontBytes(162), "Mine.ttf" to fontBytes(163)),
        MPV_SYNC_FONT_EXTENSIONS,
      )
    assertEquals(FolderFontOutcome.UPDATED, second["Shared.ttc"])
    assertEquals(FolderFontOutcome.KEPT_IMPORTED, second["Mine.ttf"])
    assertTrue(File(dir, "Shared.ttc").readBytes().contentEquals(fontBytes(162)))
    assertTrue(File(dir, "Mine.ttf").readBytes().contentEquals(imported))
  }

  @Test
  fun mpvSyncFolder_aFailingFileDoesNotStopTheOthers() {
    val dir = newDir()
    val errors = mutableListOf<Throwable>()
    val result =
      syncFolderFonts(
        dir,
        listOf(
          FolderFile("bad.woff") { failingSource("x.ttf", failAfter = 50).open() },
          FolderFile("good.woff2") { ByteArrayInputStream(fontBytes(164)) },
        ),
        MPV_SYNC_FONT_EXTENSIONS,
        onError = { errors += it },
      )
    assertEquals(FolderFontOutcome.FAILED, result["bad.woff"])
    assertEquals(FolderFontOutcome.ADDED, result["good.woff2"])
    assertEquals(1, errors.size)
    assertEquals(listOf("good.woff2"), dir.fonts())
    assertTrue(dir.leftovers().isEmpty())
  }

  // ---------------- sameContent ----------------

  @Test
  fun sameContent_detectsDifferencesAnywhere_acrossBufferBoundaries() {
    val size = DEFAULT_BUFFER_SIZE * 3 + 17
    val base = ByteArray(size) { (it % 251).toByte() }
    fun file(bytes: ByteArray) = File.createTempFile("cmp", ".bin").also { it.deleteOnExit(); it.writeBytes(bytes) }
    val a = file(base)
    assertTrue(sameContent(a, file(base.copyOf())))
    assertFalse(sameContent(a, file(base.copyOf().also { it[0] = (it[0] + 1).toByte() })))
    assertFalse(sameContent(a, file(base.copyOf().also { it[DEFAULT_BUFFER_SIZE] = (it[DEFAULT_BUFFER_SIZE] + 1).toByte() })))
    assertFalse(sameContent(a, file(base.copyOf().also { it[size - 1] = (it[size - 1] + 1).toByte() })))
    assertFalse(sameContent(a, file(base.copyOf(size - 1))))
  }
}
