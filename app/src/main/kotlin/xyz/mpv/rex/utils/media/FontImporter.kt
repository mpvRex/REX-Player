package xyz.mpv.rex.utils.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.Locale

/*
 * Core of the "Add Font File" feature.
 *
 * Deliberately free of Android classes so the whole import pipeline can be unit-tested on the JVM.
 * The Android-specific parts (URIs, content resolver, TTFFile) live in FontPreferencesUtils.kt.
 *
 * Guarantees for every imported file:
 *  - it is first written to a hidden staging file *inside the fonts directory* (never under its final name);
 *  - it is only published (rename inside the same directory) after it was fully written and validated;
 *  - an existing font is never overwritten by an import: identical content is reported as "already present",
 *    different content with the same name gets a free alternative name ("Name (1).ttf"). NOTE: rename(2) itself
 *    REPLACES an existing target, so this guarantee comes from [fontsDirLock] (every in-process writer of the
 *    fonts directory takes it), not from rename semantics;
 *  - the staging file is always removed, whatever happens;
 *  - any failure is confined to that single file (a batch keeps going);
 *  - a batch is bounded: [MAX_FONT_FILE_BYTES] per file and [MAX_FONT_BATCH_BYTES] of newly stored fonts in total.
 *
 * Every code path that creates, replaces or deletes files in `filesDir/fonts` goes through this file, and takes
 * [fontsDirLock] for the whole operation on one file:
 *  - [importFontSource]  "Add Font File": never overwrites; the stored name is recorded in a small manifest
 *                        (`.imported_fonts`) so the other paths can tell imported fonts from folder-managed ones.
 *                        Ownership rule: an explicit import wins. Importing a font that is already installed from a
 *                        folder records that existing file as imported (no copy); from then on it is kept by
 *                        "Clear cached fonts" and no longer refreshed from the folder;
 *  - [syncFolderFont]    "Font Folder" copy: keeps its original refresh behaviour (a changed font in the folder
 *                        replaces the old copy) but never touches a font recorded as imported;
 *  - [copyFontIfAbsent]  MPV config-directory sync: copies only fonts that are not installed yet (as the original did);
 *  - [clearFontFiles]    "Clear cached fonts": removes the fonts the folder/sync paths put there and keeps imported ones.
 * Nothing else writes there (MPV only reads the directory), so these are the complete set.
 */

/** Font file extensions accepted by the "Add Font File" flow (same set the font lists can display). */
val SUPPORTED_FONT_EXTENSIONS: List<String> = listOf("ttf", "otf")

/**
 * Upper bound for a single font picked through "Add Font File", enforced while copying (never an unbounded write).
 * It belongs to that flow only: the Font Folder copy and the MPV config sync are not limited by it (see [NO_SIZE_LIMIT]).
 */
const val MAX_FONT_FILE_BYTES: Long = 64L * 1024 * 1024

/**
 * Size limit of the Font Folder copy and the MPV config sync: none, exactly as before this feature existed. Those
 * paths copy fonts the user already keeps in their own folder, so a large font that worked before must keep working
 * (and must not fail silently because of a limit chosen for a different flow).
 */
const val NO_SIZE_LIMIT: Long = Long.MAX_VALUE

/**
 * Upper bound for the fonts newly stored by ONE batch (a multi-select import). Real fonts are 20 KB - ~20 MB, so
 * this never limits normal use (a dozen large CJK fonts still fit) but stops one careless select-all of a huge folder
 * from silently filling internal storage. Fonts reported as "already present" cost nothing. Files that would exceed
 * the remaining budget are reported as failed, like any other file that couldn't be added.
 */
const val MAX_FONT_BATCH_BYTES: Long = 256L * 1024 * 1024

private const val IMPORTED_MANIFEST_NAME = ".imported_fonts"
private const val STAGING_PREFIX = ".fontimport_"
private const val STAGING_SUFFIX = ".part"
private const val STALE_STAGING_AGE_MS = 10L * 60 * 1000
private const val MAX_NAME_BASE_LENGTH = 120
private const val MAX_RENAME_ATTEMPTS = 99

/** What happened to one font file. */
enum class FontImportOutcome {
  ADDED,
  ALREADY_PRESENT,
  FAILED,

  /** A valid, new font that was not stored because the batch size budget ([MAX_FONT_BATCH_BYTES]) would be exceeded. */
  SKIPPED_BATCH_LIMIT,
}

/** Aggregated result of importing one or more font files. */
data class FontImportResult(
  val added: Int,
  val alreadyPresent: Int,
  val failed: Int,
  val skippedBatchLimit: Int = 0,
)

/** One candidate font: a (lazily resolved) display name and a way to open its bytes. */
internal class FontSource(
  val displayName: () -> String?,
  val open: () -> InputStream?,
)

/**
 * Guards every write into `filesDir/fonts` (this importer, the "Font Folder" copy and the MPV config sync), so the
 * "is this name free?" check and the final rename can't interleave with another writer creating the same name.
 * Held per file, never for a whole batch.
 */
internal val fontsDirLock = Any()

fun isSupportedFontFileName(name: String): Boolean =
  name.substringAfterLast('.', "").lowercase(Locale.ROOT) in SUPPORTED_FONT_EXTENSIONS

/**
 * Reduces an arbitrary display name to a safe file name: no path components, no characters that are
 * problematic on file systems, no leading dots (hidden / our staging files), bounded length, and a
 * supported font extension. Returns null when no acceptable name can be derived.
 */
internal fun sanitizeFontFileName(raw: String?): String? {
  if (raw.isNullOrBlank()) return null
  val lastComponent = raw.substringAfterLast('/').substringAfterLast('\\').trim()
  val cleaned =
    buildString {
      lastComponent.forEach { c ->
        append(if (c.isISOControl() || c in "\\/:*?\"<>|") '_' else c)
      }
    }.trimStart('.', ' ')
  if (cleaned.isEmpty() || !isSupportedFontFileName(cleaned)) return null

  val extension = cleaned.substringAfterLast('.')
  var base = cleaned.substringBeforeLast('.').trimEnd('.', ' ')
  if (base.isEmpty()) return null
  if (base.length > MAX_NAME_BASE_LENGTH) {
    base = base.take(MAX_NAME_BASE_LENGTH)
    if (base.last().isHighSurrogate()) base = base.dropLast(1) // never cut a surrogate pair in half
  }
  return "$base.$extension"
}

/** True if the file starts with a known sfnt signature (TrueType, OpenType/CFF, Apple 'true', collection). */
internal fun hasSfntSignature(file: File): Boolean {
  val header = ByteArray(4)
  return runCatching {
    file.inputStream().use { input ->
      var read = 0
      while (read < header.size) {
        val n = input.read(header, read, header.size - read)
        if (n < 0) return@runCatching false
        read += n
      }
      true
    }
  }.getOrDefault(false) &&
    (
      header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
        String(header, Charsets.ISO_8859_1) in setOf("OTTO", "true", "ttcf")
    )
}

/** Fills [buffer] as far as possible; returns the number of bytes read (less than the size only at EOF). */
private fun readFully(input: InputStream, buffer: ByteArray): Int {
  var total = 0
  while (total < buffer.size) {
    val n = input.read(buffer, total, buffer.size - total)
    if (n < 0) break
    total += n
  }
  return total
}

/** Byte-for-byte comparison of two files (length is compared first). No per-chunk allocations. */
internal fun sameContent(a: File, b: File): Boolean {
  if (a.length() != b.length()) return false
  return try {
    a.inputStream().use { ia ->
      b.inputStream().use { ib ->
        val bufferA = ByteArray(DEFAULT_BUFFER_SIZE)
        val bufferB = ByteArray(DEFAULT_BUFFER_SIZE)
        var equal = true
        while (equal) {
          val readA = readFully(ia, bufferA)
          val readB = readFully(ib, bufferB)
          if (readA != readB) {
            equal = false
          } else if (readA == 0) {
            break
          } else {
            var i = 0
            while (i < readA && bufferA[i] == bufferB[i]) i++
            equal = i == readA
          }
        }
        equal
      }
    }
  } catch (e: java.io.IOException) {
    false
  }
}

/** Every installed font with exactly the candidate's content, in a stable (name) order. */
private fun findIdenticalFonts(fontsDir: File, candidate: File): List<File> =
  fontsDir
    .listFiles()
    ?.asSequence()
    ?.filter { it.isFile && isSupportedFontFileName(it.name) && it.length() == candidate.length() }
    ?.filter { sameContent(it, candidate) }
    ?.sortedBy { it.name }
    ?.toList()
    .orEmpty()

/** "Name.ttf" if free, otherwise "Name (1).ttf", "Name (2).ttf", ... (extension preserved). */
private fun pickFreeTarget(fontsDir: File, safeName: String): File? {
  val extension = safeName.substringAfterLast('.')
  val base = safeName.substringBeforeLast('.')
  val first = File(fontsDir, safeName)
  if (!first.exists()) return first
  for (i in 1..MAX_RENAME_ATTEMPTS) {
    val candidate = File(fontsDir, "$base ($i).$extension")
    if (!candidate.exists()) return candidate
  }
  return null
}

/** Removes staging files left behind by a crashed/killed earlier import (never a recent, possibly live one). */
private fun cleanupStaleStagingFiles(fontsDir: File) {
  val now = System.currentTimeMillis()
  fontsDir.listFiles()?.forEach { f ->
    if (f.isFile &&
      f.name.startsWith(STAGING_PREFIX) &&
      f.name.endsWith(STAGING_SUFFIX) &&
      now - f.lastModified() > STALE_STAGING_AGE_MS
    ) {
      f.delete()
    }
  }
}

// ---------------- Imported-fonts manifest ----------------
// One file name per line. Only ever read or written while holding [fontsDirLock].
//
// Invariant that matters: a name in the manifest means "this FILE was installed with Add Font File". A record without
// a file is stale (the app was killed between recording the name and publishing the font) and means nothing. Stale
// records are removed - on disk, not only in memory - by [readAndPruneImportedNames] before anything creates a font
// file, so a font copied later under that name can never inherit the record.

/**
 * The seam through which every staged file (a font or the manifest itself) is published: an atomic rename that
 * replaces an existing target. Tests replace it to inject a failure or an exception at exactly that step.
 */
@Volatile
internal var publishStagedFile: (staged: File, target: File) -> Boolean = { staged, target -> staged.renameTo(target) }

/** Names recorded as imported; empty when there is no manifest yet; a failure when it exists but can't be read. */
private fun readImportedNames(fontsDir: File): Result<Set<String>> {
  val manifest = File(fontsDir, IMPORTED_MANIFEST_NAME)
  if (!manifest.exists()) return Result.success(emptySet())
  return runCatching {
    manifest.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }
}

/** Atomic replace (staging file + publish), so a crash never leaves a half-written manifest. */
private fun writeImportedNames(fontsDir: File, names: Set<String>): Boolean {
  var temp: File? = null
  try {
    val t = File.createTempFile(STAGING_PREFIX, STAGING_SUFFIX, fontsDir)
    temp = t
    t.outputStream().use { out ->
      out.write(names.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
      out.flush()
      (out as? java.io.FileOutputStream)?.fd?.sync()
    }
    if (!publishStagedFile(t, File(fontsDir, IMPORTED_MANIFEST_NAME))) return false
    temp = null
    return true
  } catch (e: java.io.IOException) {
    return false
  } finally {
    temp?.delete() // only still set if the manifest was not published, whatever the reason (false or exception)
  }
}

/**
 * The imported names that still have a file. Records without a file are stale and are removed from the manifest on
 * disk first. Fails (and the caller must not create any font file) if the manifest is unreadable or the cleanup could
 * not be written: creating a file under a name that is still recorded would misclassify it as imported.
 */
private fun readAndPruneImportedNames(fontsDir: File): Result<Set<String>> {
  val recorded = readImportedNames(fontsDir).getOrElse { return Result.failure(it) }
  val live = recorded.filterTo(HashSet()) { File(fontsDir, it).exists() }
  if (live.size != recorded.size && !writeImportedNames(fontsDir, live)) {
    return Result.failure(java.io.IOException("Could not remove stale entries from $IMPORTED_MANIFEST_NAME"))
  }
  return Result.success(live)
}

/** Records [name] as imported (stale entries are dropped on the way). False if it could not be persisted. */
private fun recordImported(fontsDir: File, name: String): Boolean {
  val current = readImportedNames(fontsDir).getOrElse { return false }
  return writeImportedNames(fontsDir, current.filterTo(HashSet()) { File(fontsDir, it).exists() } + name)
}

private fun unrecordImported(fontsDir: File, name: String) {
  val current = readImportedNames(fontsDir).getOrNull() ?: return
  if (name in current) writeImportedNames(fontsDir, current - name)
}

// ---------------- Staging ----------------

/**
 * Copies [open] into a fresh hidden staging file inside [fontsDir], at most [maxBytes] bytes, flushed to disk.
 * Returns the staging file, which the caller owns (must publish or delete), or null - in which case nothing is left
 * behind - when the source can't be opened, is empty, is too big, or any I/O error occurs.
 */
private fun stageCopy(fontsDir: File, open: () -> InputStream?, maxBytes: Long): File? {
  val staging = File.createTempFile(STAGING_PREFIX, STAGING_SUFFIX, fontsDir)
  var keep = false
  try {
    val input = open() ?: return null
    input.use { src ->
      staging.outputStream().use { dst ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
          val read = src.read(buffer)
          if (read < 0) break
          total += read
          if (total > maxBytes) return null
          dst.write(buffer, 0, read)
        }
        dst.flush()
        (dst as? java.io.FileOutputStream)?.fd?.sync() // data on disk before the file is published
      }
    }
    if (staging.length() <= 0L) return null
    keep = true
    return staging
  } finally {
    if (!keep) staging.delete()
  }
}

/** A plain file name (no path components) whose extension is in [allowedExtensions] (case-insensitive). */
private fun isAcceptableFolderFileName(fileName: String, allowedExtensions: Set<String>): Boolean =
  fileName.isNotEmpty() &&
    fileName != "." &&
    fileName != ".." &&
    File(fileName).name == fileName &&
    fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in allowedExtensions

// ---------------- Add Font File ----------------

/** Outcome of one import plus the number of bytes it newly stored (0 unless [FontImportOutcome.ADDED]). */
internal class ImportAttempt(
  val outcome: FontImportOutcome,
  val bytesAdded: Long = 0L,
  /** Family name read from the font itself; set for [FontImportOutcome.ADDED] and [FontImportOutcome.ALREADY_PRESENT]. */
  val familyName: String? = null,
)

private val FAILED_ATTEMPT = ImportAttempt(FontImportOutcome.FAILED)
private val LIMIT_ATTEMPT = ImportAttempt(FontImportOutcome.SKIPPED_BATCH_LIMIT)

/**
 * Order of checks matters: the per-file size cap applies while copying; then validity; then DUPLICATES (which cost no
 * storage and therefore never count against the batch budget; an identical font that was not imported yet is promoted
 * to "imported" in the manifest); only a genuinely new font is checked against [batchBudgetBytes] before it is
 * recorded and published.
 *
 * Record first, publish second, and undo the record if the publish does not happen (false OR exception). The opposite
 * order could publish a font that is then never recorded. If the process dies between the two steps, what remains is
 * a record without a file, which [readAndPruneImportedNames] discards before any later copy can be affected by it.
 */
private fun importLocked(
  fontsDir: File,
  source: FontSource,
  readFamilyName: (File) -> String?,
  maxBytes: Long,
  batchBudgetBytes: Long,
): ImportAttempt {
  val safeName = sanitizeFontFileName(source.displayName()) ?: return FAILED_ATTEMPT

  if (!fontsDir.isDirectory) fontsDir.mkdirs()
  if (!fontsDir.isDirectory) return FAILED_ATTEMPT
  cleanupStaleStagingFiles(fontsDir)

  val stagingFile = stageCopy(fontsDir, source.open, maxBytes) ?: return FAILED_ATTEMPT
  var staging: File? = stagingFile
  var recordedName: String? = null
  try {
    val size = stagingFile.length()
    if (!hasSfntSignature(stagingFile)) return FAILED_ATTEMPT
    val familyName = readFamilyName(stagingFile)
    if (familyName.isNullOrBlank()) return FAILED_ATTEMPT

    val identical = findIdenticalFonts(fontsDir, stagingFile)
    if (identical.isNotEmpty()) {
      // The font is already installed, so nothing is copied - but the user chose it explicitly, so it must not stay
      // exposed to "Clear cached fonts" / Font Folder refreshes just because the same bytes arrived from a folder
      // first. If none of the identical files is an imported one yet, the first (by name) is promoted: recorded in the
      // manifest, no second file. If that can't be recorded we must not claim the font is safe: report a failure.
      val imported = readImportedNames(fontsDir).getOrElse { return FAILED_ATTEMPT }
      if (identical.none { it.name in imported } && !recordImported(fontsDir, identical.first().name)) {
        return FAILED_ATTEMPT
      }
      return ImportAttempt(FontImportOutcome.ALREADY_PRESENT, familyName = familyName)
    }
    if (size > batchBudgetBytes) return LIMIT_ATTEMPT

    val target = pickFreeTarget(fontsDir, safeName) ?: return FAILED_ATTEMPT
    if (!recordImported(fontsDir, target.name)) return FAILED_ATTEMPT
    recordedName = target.name
    if (!publishStagedFile(stagingFile, target)) return FAILED_ATTEMPT
    staging = null // published: nothing left to clean up
    recordedName = null // ...and the record now has its file
    return ImportAttempt(FontImportOutcome.ADDED, size, familyName)
  } finally {
    staging?.delete()
    recordedName?.let { unrecordImported(fontsDir, it) }
  }
}

/** Imports a single font and reports how many bytes it stored. Never throws (cancellation aside). */
internal fun importFontSourceDetailed(
  fontsDir: File,
  source: FontSource,
  readFamilyName: (File) -> String?,
  maxBytes: Long = MAX_FONT_FILE_BYTES,
  batchBudgetBytes: Long = Long.MAX_VALUE,
  onError: (Throwable) -> Unit = {},
): ImportAttempt =
  try {
    synchronized(fontsDirLock) { importLocked(fontsDir, source, readFamilyName, maxBytes, batchBudgetBytes) }
  } catch (e: Exception) {
    onError(e)
    FAILED_ATTEMPT
  }

/** Imports a single font. Never throws (cancellation aside): every failure is reported as [FontImportOutcome.FAILED]. */
internal fun importFontSource(
  fontsDir: File,
  source: FontSource,
  readFamilyName: (File) -> String?,
  maxBytes: Long = MAX_FONT_FILE_BYTES,
  onError: (Throwable) -> Unit = {},
): FontImportOutcome =
  importFontSourceDetailed(fontsDir, source, readFamilyName, maxBytes, Long.MAX_VALUE, onError).outcome

/**
 * Imports every source independently: one failing file never prevents the next ones from being processed.
 *
 * [checkCancelled] runs before each file and may throw (a coroutine's `ensureActive()`): it is the only place a batch
 * can be interrupted, so a file is either fully imported or not touched at all, and files already imported stay.
 *
 * [maxBatchBytes] bounds what the batch newly stores. Every file is still read and checked, so a duplicate is always
 * reported as "already present" (it costs no storage), but a new font that no longer fits in what is left of the
 * budget is reported as [FontImportOutcome.SKIPPED_BATCH_LIMIT] and not stored at all (never partially).
 */
internal fun importFontSources(
  fontsDir: File,
  sources: List<FontSource>,
  readFamilyName: (File) -> String?,
  maxBytes: Long = MAX_FONT_FILE_BYTES,
  onError: (Throwable) -> Unit = {},
  checkCancelled: () -> Unit = {},
  maxBatchBytes: Long = MAX_FONT_BATCH_BYTES,
): FontImportResult =
  importFontSourcesReport(fontsDir, sources, readFamilyName, maxBytes, onError, checkCancelled, maxBatchBytes).result

/**
 * A batch result plus what the UI needs to decide on automatic selection.
 *
 * [acceptedCount] is the number of files that ended up installed and usable: newly added or already present (both
 * are fonts the user picked and that are now in the list); failed, invalid and over-budget files do not count.
 * [familyToApply] is the family name (read from the font file itself, never derived from the file name) of the single
 * accepted font. It is set only when exactly ONE file was accepted, whatever the number of files picked: one valid
 * file among failures is applied, while two or more accepted fonts (no way to know which one the user wants) or none
 * leave it null and the active font must stay as it is.
 */
internal class FontImportBatchReport(
  val result: FontImportResult,
  val acceptedCount: Int,
  val familyToApply: String?,
)

/** Same as [importFontSources], but also reports which font (if any) should be applied automatically. */
internal fun importFontSourcesReport(
  fontsDir: File,
  sources: List<FontSource>,
  readFamilyName: (File) -> String?,
  maxBytes: Long = MAX_FONT_FILE_BYTES,
  onError: (Throwable) -> Unit = {},
  checkCancelled: () -> Unit = {},
  maxBatchBytes: Long = MAX_FONT_BATCH_BYTES,
): FontImportBatchReport {
  var added = 0
  var existing = 0
  var failed = 0
  var limited = 0
  var storedBytes = 0L
  var accepted = 0
  var lastAcceptedFamily: String? = null
  sources.forEach { source ->
    checkCancelled()
    val remaining = (maxBatchBytes - storedBytes).coerceAtLeast(0L)
    val attempt = importFontSourceDetailed(fontsDir, source, readFamilyName, maxBytes, remaining, onError)
    when (attempt.outcome) {
      FontImportOutcome.ADDED -> {
        added++
        storedBytes += attempt.bytesAdded
        accepted++
        lastAcceptedFamily = attempt.familyName
      }
      FontImportOutcome.ALREADY_PRESENT -> {
        existing++
        accepted++
        lastAcceptedFamily = attempt.familyName
      }
      FontImportOutcome.FAILED -> failed++
      FontImportOutcome.SKIPPED_BATCH_LIMIT -> limited++
    }
  }
  return FontImportBatchReport(
    result = FontImportResult(added, existing, failed, limited),
    acceptedCount = accepted,
    familyToApply = if (accepted == 1) lastAcceptedFamily else null,
  )
}

/**
 * Imports files picked by path (the in-app file picker) as ONE batch through [importFontSourcesReport]: duplicates in
 * [paths] are ignored, every file is its own source, so an unreadable or deleted file only fails itself.
 */
internal fun importFontPaths(
  fontsDir: File,
  paths: List<String>,
  readFamilyName: (File) -> String?,
  onError: (Throwable) -> Unit = {},
  checkCancelled: () -> Unit = {},
): FontImportBatchReport =
  importFontSourcesReport(
    fontsDir = fontsDir,
    sources =
      paths.distinct().map { path ->
        val file = File(path)
        FontSource(displayName = { file.name }, open = { file.inputStream() })
      },
    readFamilyName = readFamilyName,
    onError = onError,
    checkCancelled = checkCancelled,
  )

/**
 * Scope that runs the confirmed "Add Font File" imports (through the queue below). It is deliberately NOT tied to the settings panel: closing the
 * panel (or leaving the player) while a batch is being copied must not cancel it half-way, because files already
 * copied would then never be reported, listed or applied. It is not a general task system: only font imports run
 * here, each one finite and bounded by [MAX_FONT_BATCH_BYTES]; a failure of one import (SupervisorJob) affects no
 * other. What happens when an import ends is decided by the caller, which only touches the UI / mpv while the
 * player session is still alive.
 */
internal val fontImportScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Confirmed imports run strictly one at a time and in the order they were confirmed (e.g. the user closes the panel,
 * reopens it and confirms a second batch while the first is still copying). Writes to the fonts directory and to
 * `.imported_fonts` are already made safe, per file, by [fontsDirLock]; this queue only keeps batches from
 * interleaving and keeps their follow-ups (list refresh, automatic selection) in confirmation order, so an older
 * batch can never finish last and override the font a newer one applied.
 *
 * Order is guaranteed by the queue itself, not by coroutine scheduling: [launchFontImport] enqueues synchronously
 * (an unlimited FIFO channel, so the call never suspends and never fails), and a single consumer coroutine takes the
 * entries one by one. Launching a coroutine per batch and serialising them with a lock would not do: such coroutines
 * can reach the lock in a different order than they were started.
 */
private val fontImportQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

private val fontImportConsumer: Job =
  fontImportScope.launch {
    for (batch in fontImportQueue) {
      try {
        batch()
      } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive() // only the consumer's own cancellation ends the loop
      } catch (e: Exception) {
        // A failing batch is the caller's to report (the panel logs it); the next batch must still run.
      }
    }
  }

/**
 * Queues [block] (an import plus its follow-up) on [fontImportScope]. It starts after every batch queued before it has
 * finished, whether those succeeded or failed. Returns immediately.
 */
internal fun launchFontImport(block: suspend () -> Unit) {
  check(fontImportConsumer.isActive) { "Font import queue is not running" }
  fontImportQueue.trySend(block).getOrThrow()
}

// ---------------- Font Folder copy / MPV config sync ----------------

/** What [syncFolderFont] did with one font from the user's fonts folder. */
enum class FolderFontOutcome {
  ADDED,
  UPDATED,
  UNCHANGED,

  /** A same-named font installed with "Add Font File" exists; it is left exactly as it is. */
  KEPT_IMPORTED,

  /** Not a candidate (unsupported extension or a name with path components). Nothing was touched. */
  SKIPPED,
  FAILED,
}

/**
 * One font of the "Font Folder" copy. Keeps the original behaviour - the font in the folder is the source of truth, so
 * a changed file replaces the previously copied one and an identical one is left alone - with one exception: a font
 * recorded as imported ("Add Font File") is never replaced, because it does not come from that folder. In that case the
 * folder's same-named font is not copied (it stays in the folder, and the imported font keeps working); import it
 * explicitly, which stores it under another name, if both are wanted.
 *
 * Only names with an extension in [allowedExtensions] are considered. There is no size limit (see [NO_SIZE_LIMIT]),
 * as before. The replacement is staged and then published
 * atomically, so a failed copy never damages the existing font, and the staging file is removed on every failure path.
 * Stale manifest records are removed first (see [readAndPruneImportedNames]); if that is impossible nothing is created.
 * Never throws (cancellation aside); failures go to [onError].
 */
internal fun syncFolderFont(
  fontsDir: File,
  fileName: String,
  allowedExtensions: Set<String>,
  open: () -> InputStream?,
  maxBytes: Long = NO_SIZE_LIMIT,
  onError: (Throwable) -> Unit = {},
): FolderFontOutcome {
  if (!isAcceptableFolderFileName(fileName, allowedExtensions)) return FolderFontOutcome.SKIPPED
  return try {
    synchronized(fontsDirLock) {
      if (!fontsDir.isDirectory) fontsDir.mkdirs()
      if (!fontsDir.isDirectory) return@synchronized FolderFontOutcome.FAILED
      // If the manifest can't be read or cleaned we can't tell what is imported: touch nothing.
      val imported =
        readAndPruneImportedNames(fontsDir).getOrElse {
          onError(it)
          return@synchronized FolderFontOutcome.FAILED
        }
      val target = File(fontsDir, fileName)
      if (fileName in imported) return@synchronized FolderFontOutcome.KEPT_IMPORTED
      if (target.exists() && !target.isFile) return@synchronized FolderFontOutcome.FAILED
      cleanupStaleStagingFiles(fontsDir)

      val staged = stageCopy(fontsDir, open, maxBytes) ?: return@synchronized FolderFontOutcome.FAILED
      var staging: File? = staged
      try {
        val existed = target.exists()
        if (existed && sameContent(staged, target)) return@synchronized FolderFontOutcome.UNCHANGED
        if (!publishStagedFile(staged, target)) return@synchronized FolderFontOutcome.FAILED
        staging = null
        if (existed) FolderFontOutcome.UPDATED else FolderFontOutcome.ADDED
      } finally {
        staging?.delete()
      }
    }
  } catch (e: Exception) {
    onError(e)
    FolderFontOutcome.FAILED
  }
}

/**
 * Copies one font into [fontsDir] under exactly [fileName], but only if no file with that name exists yet (the original
 * behaviour of the MPV config-directory sync, kept as it was). Existing files - including imported fonts - are never
 * replaced. Only extensions in [allowedExtensions] are accepted; no size limit (see [NO_SIZE_LIMIT]). Like [syncFolderFont] it removes stale manifest
 * records before creating a file and cleans up its staging file on every failure path.
 * Returns true only if a new file was published. Never throws (cancellation aside); failures go to [onError].
 */
internal fun copyFontIfAbsent(
  fontsDir: File,
  fileName: String,
  allowedExtensions: Set<String>,
  open: () -> InputStream?,
  maxBytes: Long = NO_SIZE_LIMIT,
  onError: (Throwable) -> Unit = {},
): Boolean {
  if (!isAcceptableFolderFileName(fileName, allowedExtensions)) return false
  return try {
    synchronized(fontsDirLock) {
      if (!fontsDir.isDirectory) fontsDir.mkdirs()
      val target = File(fontsDir, fileName)
      if (!fontsDir.isDirectory || target.exists()) return@synchronized false
      readAndPruneImportedNames(fontsDir).getOrElse {
        onError(it)
        return@synchronized false
      }
      cleanupStaleStagingFiles(fontsDir)

      val staged = stageCopy(fontsDir, open, maxBytes) ?: return@synchronized false
      var staging: File? = staged
      try {
        if (!publishStagedFile(staged, target)) return@synchronized false
        staging = null
        true
      } finally {
        staging?.delete()
      }
    }
  } catch (e: Exception) {
    onError(e)
    false
  }
}

/**
 * Font extensions of the MPV config sync - the set its config-directory part always used. The "Font Folder" part of
 * the same sync uses it as well: the original code copied EVERY file of that folder, so the font types are kept
 * (.ttc, .woff, .woff2 too) and only non-font files (TXT, ZIP, images...) are no longer copied into the fonts
 * directory. That exclusion is deliberate. Note that "Clear cached fonts" and the font lists only handle .ttf / .otf,
 * exactly as before, so the extra types are copied for MPV's benefit and are not managed by them.
 */
val MPV_SYNC_FONT_EXTENSIONS: Set<String> = setOf("ttf", "otf", "ttc", "woff", "woff2")

/** One file of a user-chosen fonts folder: its name and a way to open it. */
internal class FolderFile(
  val name: String,
  val open: () -> InputStream?,
)

/**
 * Applies [syncFolderFont] to every file of a fonts folder (non-recursive, like the original directory copy) and
 * returns what happened to each file name. This is what the MPV config sync runs for its "Font Folder" part; the
 * Android code only turns the folder listing into [FolderFile]s.
 */
internal fun syncFolderFonts(
  fontsDir: File,
  files: List<FolderFile>,
  allowedExtensions: Set<String>,
  onError: (Throwable) -> Unit = {},
): Map<String, FolderFontOutcome> =
  files.associate { file ->
    file.name to syncFolderFont(fontsDir, file.name, allowedExtensions, file.open, onError = onError)
  }

/** Result of [clearFontFiles]: either it ran (possibly with nothing to do) or it could not run safely. */
sealed interface FontClearResult {
  /** [deleted] fonts removed; [failed] fonts that should have been removed but could not be deleted. */
  data class Cleared(val deleted: Int, val failed: Int) : FontClearResult

  /** Nothing was deleted because it was not safe or possible to tell what to delete; [cause] says why. */
  data class Unavailable(val cause: Throwable) : FontClearResult
}

/**
 * "Clear cached fonts": deletes the font files (`.ttf` / `.otf`) that the folder/sync paths copied into [fontsDir] -
 * everything the original action deleted - but keeps fonts installed with "Add Font File", which are user-added, not a
 * cache, and may have no other copy in the chosen folder. Takes [fontsDirLock], so it can't run in the middle of an
 * import or a copy. If the imported-fonts manifest exists but can't be read, nothing is deleted and the result is
 * [FontClearResult.Unavailable] (we can't tell which fonts are imported; never "delete everything" as a fallback).
 * Other files are left alone.
 */
internal fun clearFontFiles(fontsDir: File): FontClearResult =
  try {
    synchronized(fontsDirLock) {
      val imported =
        readImportedNames(fontsDir).getOrElse { return@synchronized FontClearResult.Unavailable(it) }
      if (!fontsDir.exists()) return@synchronized FontClearResult.Cleared(0, 0)
      val files =
        fontsDir.listFiles()
          ?: return@synchronized FontClearResult.Unavailable(java.io.IOException("Cannot list ${fontsDir.path}"))
      var deleted = 0
      var failed = 0
      files.forEach { file ->
        if (file.isFile && isSupportedFontFileName(file.name) && file.name !in imported) {
          if (file.delete()) deleted++ else failed++
        }
      }
      FontClearResult.Cleared(deleted, failed)
    }
  } catch (e: Exception) {
    FontClearResult.Unavailable(e)
  }
