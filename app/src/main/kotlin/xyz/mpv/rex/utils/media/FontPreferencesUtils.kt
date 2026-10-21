package xyz.mpv.rex.utils.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Copies font files from the selected directory to the app's internal storage.
 *
 * Same behaviour as before for fonts that come from this folder: a new font is copied and a changed one replaces the
 * earlier copy. The one difference is that a font installed with "Add Font File" is never replaced by a same-named
 * font from the folder (see [syncFolderFont]).
 */
@SuppressLint("UseKtx")
fun copyFontsFromDirectory(
  context: Context,
  fileManager: FileManager,
  uriString: String,
) {
  runCatching {
    val fontsDir = File(context.filesDir, "fonts")
    val extensions = SUPPORTED_FONT_EXTENSIONS.toSet() // .ttf / .otf, as before

    val sourceDir = fileManager.fromUri(Uri.parse(uriString))
    if (sourceDir != null && fileManager.exists(sourceDir)) {
      fileManager.listFiles(sourceDir).forEach { file ->
        if (fileManager.isFile(file)) {
          val fileName = fileManager.getName(file)
          val outcome =
            syncFolderFont(
              fontsDir = fontsDir,
              fileName = fileName,
              allowedExtensions = extensions,
              open = { fileManager.getInputStream(file) },
              onError = ::logFontImportError,
            )
          if (outcome == FolderFontOutcome.KEPT_IMPORTED) {
            Log.w("SubtitlesPreferences", "Font \"$fileName\" not copied: a font with that name was added via Add Font File")
          }
        }
      }
    }
  }.onFailure { e ->
    Log.e("SubtitlesPreferences", "Error copying fonts", e)
  }
}

// getSimplifiedPathFromUri is defined in AdvancedPreferencesScreen.kt within this package.

/** Represents a custom font discovered in the app's internal fonts directory. */
data class CustomFontEntry(
  val familyName: String,
  val file: File,
)

/**
 * Loads custom fonts (family name + file) from the app's internal fonts directory for previews.
 * Returns one entry per family name (first match kept if duplicates exist).
 */
suspend fun loadCustomFontEntries(context: Context): List<CustomFontEntry> =
  withContext(Dispatchers.IO) {
    val fontsDir = File(context.filesDir, "fonts")
    if (!fontsDir.exists()) return@withContext emptyList()

    val fontFiles =
      fontsDir
        .listFiles()
        ?.filter { it.isFile && it.name.lowercase(Locale.ROOT).matches(".*\\.[ot]tf$".toRegex()) }
        .orEmpty()

    val entries = mutableListOf<CustomFontEntry>()
    val seenFamilies = mutableSetOf<String>()

    for (fontFile in fontFiles) {
      val familyName =
        runCatching {
          fontFile.inputStream().use { input ->
            TTFFile
              .open(input)
              .families
              .values
              .firstOrNull()
          }
        }.getOrNull()

      if (!familyName.isNullOrBlank() && seenFamilies.add(familyName)) {
        entries += CustomFontEntry(familyName, fontFile)
      }
    }

    entries.sortedBy { it.familyName }
  }

// ==================== Add Font File (single / multiple files) ====================
// The import pipeline itself (staging, validation, de-duplication, per-file isolation) lives in
// FontImporter.kt; this part only adapts Android inputs to it.

private const val FONT_IMPORT_TAG = "FontImport"

/**
 * Outcome of an Add Font File action. [familyToApply] is the family name read from the font file when exactly one
 * of the picked files was accepted (the others, if any, failed); null otherwise (see [FontImportBatchReport]).
 */
data class FontImportReport(
  val result: FontImportResult,
  val familyToApply: String?,
)

private fun queryDisplayName(context: Context, uri: Uri): String? =
  runCatching {
    context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
  }.getOrNull() ?: uri.lastPathSegment

/**
 * Reads the family name exactly the way the font lists do ([loadCustomFontEntries] and the in-player
 * list use `TTFFile.open(...).families.values`), so a font accepted here is one the lists can show.
 */
internal fun readFontFamilyName(file: File): String? =
  runCatching {
    file.inputStream().use { input -> TTFFile.open(input).families.values.firstOrNull() }
  }.getOrNull()

private fun logFontImportError(error: Throwable) {
  Log.e(FONT_IMPORT_TAG, "Error importing font", error)
}

/**
 * Imports fonts picked through the system document picker (one or several `content://` URIs) into the
 * app's internal fonts directory (`filesDir/fonts`, the directory mpv uses as `sub-fonts-dir`).
 *
 * Cancellation: checked only *between* files. The file in progress is always finished (copied, validated and
 * published, or discarded), so closing the panel never leaves a partial font behind; files already imported stay.
 * A cancelled batch simply has no result to show (its UI is gone) and the fonts list reloads next time it is opened.
 */
suspend fun importFontsFromUris(
  context: Context,
  uris: List<Uri>,
): FontImportReport =
  withContext(Dispatchers.IO) {
    importFontSourcesReport(
      fontsDir = File(context.filesDir, "fonts"),
      sources =
        uris.distinct().map { uri ->
          FontSource(
            displayName = { queryDisplayName(context, uri) },
            open = { context.contentResolver.openInputStream(uri) },
          )
        },
      readFamilyName = ::readFontFamilyName,
      onError = ::logFontImportError,
      checkCancelled = { ensureActive() },
    ).let { FontImportReport(it.result, it.familyToApply) }
  }

/** Imports a font picked through the in-app file picker (absolute file path). */
suspend fun importFontFromPath(
  context: Context,
  path: String,
): FontImportReport =
  withContext(Dispatchers.IO) {
    val file = File(path)
    importFontSourcesReport(
      fontsDir = File(context.filesDir, "fonts"),
      sources = listOf(FontSource(displayName = { file.name }, open = { file.inputStream() })),
      readFamilyName = ::readFontFamilyName,
      onError = ::logFontImportError,
    ).let { FontImportReport(it.result, it.familyToApply) }
  }

/**
 * Imports the fonts selected in the in-app file picker (absolute file paths) in ONE batch, through the same pipeline
 * as every other import ([importFontPaths]: per-file isolation, validation, [FontImportReport.familyToApply] rules).
 * Cancellation is checked only between files, like [importFontsFromUris].
 */
suspend fun importFontsFromPaths(
  context: Context,
  paths: List<String>,
): FontImportReport =
  withContext(Dispatchers.IO) {
    importFontPaths(
      fontsDir = File(context.filesDir, "fonts"),
      paths = paths,
      readFamilyName = ::readFontFamilyName,
      onError = ::logFontImportError,
      checkCancelled = { ensureActive() },
    ).let { FontImportReport(it.result, it.familyToApply) }
  }
