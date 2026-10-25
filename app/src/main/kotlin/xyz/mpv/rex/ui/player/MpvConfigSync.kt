package xyz.mpv.rex.ui.player

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import xyz.mpv.rex.domain.hdr.HdrToysManager
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.utils.media.FolderFile
import xyz.mpv.rex.utils.media.MPV_SYNC_FONT_EXTENSIONS
import xyz.mpv.rex.utils.media.copyFontIfAbsent
import xyz.mpv.rex.utils.media.syncFolderFonts
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils
import java.io.File
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared MPV config/asset preparation.
 *
 * Copies bundled assets and syncs the user's configured MPV directory (mpv.conf, input.conf,
 * scripts/, script-opts/, shaders/, fonts/) into internal storage BEFORE libmpv is initialized,
 * since mpv loads scripts during init.
 *
 * Extracted from PlayerActivity so both the full-screen player and the headless
 * [HeadlessPlaybackController] share the exact same preparation logic.
 */
object MpvConfigSync : KoinComponent {
  private const val TAG = "MpvConfigSync"

  private val advancedPreferences: AdvancedPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val decoderPreferences: DecoderPreferences by inject()
  private val hdrToysManager: HdrToysManager by inject()
  private val fileManager: FileManager by inject()

  /**
   * Prepares all MPV config/assets. Call once before [`is`.xyz.mpv.BaseMPVView.initialize].
   * Safe to call from any thread; does file IO.
   */
  fun prepare(context: Context) {
    runCatching {
      Utils.copyAssets(context)
      syncFromUserMpvDirectory(context)

      // Configure hdr-toys conditional profile in mpv.conf if enabled
      val isEnabled = decoderPreferences.enableHdrToys.get()
      val toneStr = decoderPreferences.hdrToysToneMapping.get()
      val gamutStr = decoderPreferences.hdrToysGamutMapping.get()

      val tone = runCatching { HdrToysManager.ToneMapping.valueOf(toneStr) }.getOrDefault(HdrToysManager.ToneMapping.ASTRA)
      val gamut = runCatching { HdrToysManager.GamutMapping.valueOf(gamutStr) }.getOrDefault(HdrToysManager.GamutMapping.BOTTOSSON)

      hdrToysManager.configureMpvConf(context.filesDir, isEnabled, tone, gamut)

      Log.d(TAG, "MPV config and scripts prepared successfully")
    }.onFailure { e ->
      Log.e(TAG, "Error copying MPV config and scripts", e)
    }
  }

  /**
   * Syncs ALL MPV assets from the user's configured MPV directory to internal storage.
   * Handles: mpv.conf, input.conf, scripts/, script-opts/, shaders/, fonts/
   *
   * Uses case-insensitive subfolder matching and falls back to root scanning
   * if standard subfolders don't exist. Falls back to preferences-based config
   * if no user directory is configured.
   */
  private fun syncFromUserMpvDirectory(context: Context) {
    val mpvConfStorageUri = advancedPreferences.mpvConfStorageUri.get()

    val tree = if (mpvConfStorageUri.isNotBlank()) {
      runCatching {
        DocumentFile.fromTreeUri(context, mpvConfStorageUri.toUri())
      }.getOrNull()?.takeIf { it.exists() && it.canRead() }
    } else null

    if (tree != null) {
      Log.d(TAG, "Syncing from user MPV directory: ${tree.uri}")
      syncConfigFiles(context, tree)
      syncScripts(context, tree)
      syncScriptOpts(context, tree)
      syncShaders(context, tree)
      syncFonts(context, tree)
      Log.d(TAG, "Full MPV directory sync completed")
    } else {
      Log.d(TAG, "No MPV directory configured, using preferences fallback")
      copyMPVConfigFromPreferences(context)
    }
  }

  // ==================== Config Files Sync ====================

  private fun syncConfigFiles(context: Context, tree: DocumentFile) {
    for (configName in listOf("mpv.conf", "input.conf")) {
      runCatching {
        val configFile = findFileCaseInsensitive(tree, configName)
        if (configFile != null && configFile.exists() && configFile.canRead()) {
          context.contentResolver.openInputStream(configFile.uri)?.use { input ->
            val content = input.bufferedReader().readText()
            File(context.filesDir, configName).writeText(content)
            when (configName) {
              "mpv.conf" -> advancedPreferences.mpvConf.set(content)
              "input.conf" -> advancedPreferences.inputConf.set(content)
            }
            Log.d(TAG, "Synced config: $configName (${content.length} chars)")
          }
        } else {
          val prefContent = when (configName) {
            "mpv.conf" -> advancedPreferences.mpvConf.get()
            "input.conf" -> advancedPreferences.inputConf.get()
            else -> ""
          }
          File(context.filesDir, configName).apply {
            if (!exists()) createNewFile()
            if (prefContent.isNotBlank()) writeText(prefContent)
          }
          Log.d(TAG, "Config not found in directory, used preferences: $configName")
        }
      }.onFailure { e ->
        Log.e(TAG, "Error syncing config: $configName", e)
      }
    }
  }

  // ==================== Scripts Sync ====================

  private fun syncScripts(context: Context, tree: DocumentFile) {
    val internalScriptsDir = File(context.filesDir, "scripts")
    internalScriptsDir.mkdirs()
    internalScriptsDir.listFiles()?.forEach { it.delete() }

    if (!advancedPreferences.enableLuaScripts.get()) {
      Log.d(TAG, "Lua scripts disabled, skipping")
      return
    }

    val scriptsSubdir = findSubdirCaseInsensitive(tree, "scripts")
    val sourceDir = scriptsSubdir ?: tree
    val scriptExtensions = setOf("lua", "js")
    var count = 0

    sourceDir.listFiles().forEach { file ->
      if (!file.isFile) return@forEach
      val name = file.name ?: return@forEach
      val ext = name.substringAfterLast('.', "").lowercase()
      if (ext !in scriptExtensions) return@forEach

      val selectedScripts = advancedPreferences.selectedLuaScripts.get()
      if (!selectedScripts.contains(name)) {
        return@forEach
      }

      runCatching {
        context.contentResolver.openInputStream(file.uri)?.use { input ->
          File(internalScriptsDir, name).outputStream().use { output ->
            input.copyTo(output)
          }
          count++
          Log.d(TAG, "Synced script: $name")
        }
      }.onFailure { e ->
        Log.e(TAG, "Error syncing script: $name", e)
      }
    }

    Log.d(TAG, "Scripts sync: $count file(s) from ${if (scriptsSubdir != null) "scripts/" else "root"}")
  }

  // ==================== Script Options Sync ====================

  private fun syncScriptOpts(context: Context, tree: DocumentFile) {
    val internalScriptOptsDir = File(context.filesDir, "script-opts")
    internalScriptOptsDir.mkdirs()
    internalScriptOptsDir.listFiles()?.forEach { it.delete() }

    val scriptOptsSubdir = findSubdirCaseInsensitive(tree, "script-opts")
    if (scriptOptsSubdir == null) {
      Log.d(TAG, "No script-opts/ subfolder found, skipping")
      return
    }

    var count = 0
    scriptOptsSubdir.listFiles().forEach { file ->
      if (!file.isFile) return@forEach
      val name = file.name ?: return@forEach

      runCatching {
        context.contentResolver.openInputStream(file.uri)?.use { input ->
          File(internalScriptOptsDir, name).outputStream().use { output ->
            input.copyTo(output)
          }
          count++
          Log.d(TAG, "Synced script-opt: $name")
        }
      }.onFailure { e ->
        Log.e(TAG, "Error syncing script-opt: $name", e)
      }
    }

    Log.d(TAG, "Script-opts sync: $count file(s)")
  }

  // ==================== Shaders Sync ====================

  private fun syncShaders(context: Context, tree: DocumentFile) {
    val shadersDir = File(context.filesDir, "shaders")
    shadersDir.mkdirs()

    val shadersSubdir = findSubdirCaseInsensitive(tree, "shaders")
    val sourceDir = shadersSubdir ?: tree
    val shaderExtensions = setOf("glsl", "hook", "comp")

    val count = syncShaderDirRecursive(context, sourceDir, shadersDir, shaderExtensions)
    Log.d(TAG, "Shaders sync: $count file(s) completed")
  }

  private fun syncShaderDirRecursive(
    context: Context,
    sourceDir: DocumentFile,
    targetDir: File,
    shaderExtensions: Set<String>
  ): Int {
    var count = 0
    sourceDir.listFiles().forEach { file ->
      val name = file.name ?: return@forEach
      if (file.isDirectory) {
        val nextTarget = File(targetDir, name)
        nextTarget.mkdirs()
        count += syncShaderDirRecursive(context, file, nextTarget, shaderExtensions)
      } else if (file.isFile) {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in shaderExtensions) {
          runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
              File(targetDir, name).outputStream().use { output ->
                input.copyTo(output)
              }
              count++
              Log.d(TAG, "Synced shader: $name")
            }
          }.onFailure { e ->
            Log.e(TAG, "Error syncing shader: $name", e)
          }
        }
      }
    }
    return count
  }

  // ==================== Fonts Sync ====================

  private fun syncFonts(context: Context, tree: DocumentFile) {
    val internalFontsDir = File(context.filesDir, "fonts")
    internalFontsDir.mkdirs()

    val fontsSubdir = findSubdirCaseInsensitive(tree, "fonts")
    val sourceDir = fontsSubdir ?: tree
    val fontExtensions = MPV_SYNC_FONT_EXTENSIONS
    var count = 0

    sourceDir.listFiles().forEach { file ->
      if (!file.isFile) return@forEach
      val name = file.name ?: return@forEach
      // As before: only fonts that are not installed yet (so never one added with "Add Font File"), only the
      // extensions listed above; takes the shared fonts lock.
      val copied =
        copyFontIfAbsent(
          fontsDir = internalFontsDir,
          fileName = name,
          allowedExtensions = fontExtensions,
          open = { context.contentResolver.openInputStream(file.uri) },
          onError = { e -> Log.e(TAG, "Error syncing font: $name", e) },
        )
      if (copied) {
        count++
        Log.d(TAG, "Synced font: $name")
      }
    }

    // Also sync from subtitle preferences font folder if set
    runCatching {
      val fontsFolderUri = subtitlesPreferences.fontsFolder.get()
      if (fontsFolderUri.isNotBlank()) {
        val fontsDir = fileManager.fromUri(fontsFolderUri.toUri())
        if (fontsDir != null && fileManager.exists(fontsDir)) {
          // The Font Folder keeps its refresh behaviour (a changed font replaces the old copy) and never replaces a
          // font added with "Add Font File". The original copied every file of the folder; now only font files are
          // copied (same extensions as the config directory above, so .ttc/.woff/.woff2 still work), not TXT, ZIP, etc.
          // Non-recursive, as copyDirectoryWithContent(.., false).
          val folderFiles =
            fileManager
              .listFiles(fontsDir)
              .filter { fileManager.isFile(it) }
              .map { folderFile -> FolderFile(fileManager.getName(folderFile)) { fileManager.getInputStream(folderFile) } }
          syncFolderFonts(
            fontsDir = internalFontsDir,
            files = folderFiles,
            allowedExtensions = fontExtensions,
            onError = { e -> Log.e(TAG, "Error syncing subtitle font", e) },
          )
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Error syncing subtitle fonts: ${e.message}")
    }

    Log.d(TAG, "Fonts sync: $count file(s) from MPV directory")
  }

  // ==================== Helpers ====================

  private fun copyMPVConfigFromPreferences(context: Context) {
    runCatching {
      File(context.filesDir, "mpv.conf").apply {
        if (!exists()) createNewFile()
        val content = advancedPreferences.mpvConf.get()
        if (content.isNotBlank()) writeText(content)
      }
      File(context.filesDir, "input.conf").apply {
        if (!exists()) createNewFile()
        val content = advancedPreferences.inputConf.get()
        if (content.isNotBlank()) writeText(content)
      }
      File(context.filesDir, "scripts").mkdirs()
      File(context.filesDir, "fonts").mkdirs()
    }.onFailure { e ->
      Log.e(TAG, "Error creating fallback config files", e)
    }
  }

  fun findSubdirCaseInsensitive(parent: DocumentFile, name: String): DocumentFile? =
    parent.listFiles().firstOrNull {
      it.isDirectory && it.name?.equals(name, ignoreCase = true) == true
    }

  private fun findFileCaseInsensitive(parent: DocumentFile, name: String): DocumentFile? =
    parent.listFiles().firstOrNull {
      it.isFile && it.name?.equals(name, ignoreCase = true) == true
    }
}
