package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import xyz.mpv.rex.utils.media.FontClearResult
import xyz.mpv.rex.utils.media.clearFontFiles
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.fastJoinToString
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.SettingsManager
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.crash.CrashActivity
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.TwoTargetIconButtonPreference
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
object AdvancedPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val settingsManager = koinInject<SettingsManager>()
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importStats by remember { mutableStateOf<SettingsManager.ImportStats?>(null) }
    var exportStats by remember { mutableStateOf<SettingsManager.ExportStats?>(null) }

    // Export settings launcher
    val exportLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml"),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.exportSettings(it).fold(
              onSuccess = { stats ->
                exportStats = stats
                showExportDialog = true
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  context.getString(R.string.export_failed, error.message),
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    // Import settings launcher
    val importLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.importSettings(it).fold(
              onSuccess = { stats ->
                importStats = stats
                showImportDialog = true
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  context.getString(R.string.import_failed, error.message),
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    // Export results dialog
    if (showExportDialog && exportStats != null) {
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = { Text(stringResource(R.string.export_complete)) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          ) {
            Text(
              stringResource(R.string.export_complete_details, exportStats?.totalExported ?: 0)
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { showExportDialog = false }) {
            Text(stringResource(R.string.generic_ok))
          }
        },
      )
    }

    // Import results dialog
    if (showImportDialog && importStats != null) {
      AlertDialog(
        onDismissRequest = { showImportDialog = false },
        title = { Text(stringResource(R.string.import_complete)) },
        text = {
          Text(
            stringResource(
              R.string.import_complete_details,
              importStats?.imported ?: 0,
              importStats?.failed ?: 0,
              importStats?.version ?: "",
            ),
          )
        },
        confirmButton = {
          TextButton(onClick = { showImportDialog = false }) {
            Text(stringResource(R.string.generic_ok))
          }
        },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_advanced),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val locationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.mpvConfStorageUri.set(uri.toString())
          }
        val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          // Backup & Restore Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_backup_restore))
          }
          
          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_advanced, R.string.pref_export_settings_title),
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_export_settings_title)) },
                  summary = { 
                    Text(
                      text = stringResource(R.string.pref_export_settings_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                  icon = { 
                    Icon(
                      Icons.Outlined.FileUpload, 
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    ) 
                  },
                  onClick = {
                    exportLauncher.launch(settingsManager.getDefaultExportFilename())
                  },
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_import_settings_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_import_settings_title)) },
                  summary = { 
                    Text(
                      text = stringResource(R.string.pref_import_settings_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                  icon = { 
                    Icon(
                      Icons.Outlined.FileDownload, 
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    ) 
                  },
                  onClick = {
                    importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                  },
                )
              }
            }
          }
          
          // MPV Configuration Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_mpv_configuration))
          }
          
          item {
            var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
            var inputConf by remember { mutableStateOf(preferences.inputConf.get()) }
            
            // Load config files when storage location changes
            LaunchedEffect(mpvConfStorageLocation) {
              if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
              withContext(Dispatchers.IO) {
                val tempFile = kotlin.io.path.createTempFile()
                runCatching {
                  val tree =
                    DocumentFile.fromTreeUri(
                      context,
                      mpvConfStorageLocation.toUri(),
                    )
                  val mpvConfFile = tree?.findFile("mpv.conf")
                  if (mpvConfFile != null && mpvConfFile.exists()) {
                    context.contentResolver
                      .openInputStream(
                        mpvConfFile.uri,
                      )?.copyTo(tempFile.outputStream())
                    val content = tempFile.readLines().fastJoinToString("\n")
                    preferences.mpvConf.set(content)
                    File(context.filesDir, "mpv.conf").writeText(content)
                    withContext(Dispatchers.Main) {
                      mpvConf = content
                    }
                  }
                }
                tempFile.deleteIfExists()
              }
            }
            
            // Load input.conf when storage location changes
            LaunchedEffect(mpvConfStorageLocation) {
              if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
              withContext(Dispatchers.IO) {
                val tempFile = kotlin.io.path.createTempFile()
                runCatching {
                  val tree =
                    DocumentFile.fromTreeUri(
                      context,
                      mpvConfStorageLocation.toUri(),
                    )
                  val inputConfFile = tree?.findFile("input.conf")
                  if (inputConfFile != null && inputConfFile.exists()) {
                    context.contentResolver
                      .openInputStream(
                        inputConfFile.uri,
                      )?.copyTo(tempFile.outputStream())
                    val content = tempFile.readLines().fastJoinToString("\n")
                    preferences.inputConf.set(content)
                    File(context.filesDir, "input.conf").writeText(content)
                    withContext(Dispatchers.Main) {
                      inputConf = content
                    }
                  }
                }
                tempFile.deleteIfExists()
              }
            }
            
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_advanced_mpv_conf_storage_location,
              ) {
                TwoTargetIconButtonPreference(
                  title = { Text(stringResource(R.string.pref_advanced_mpv_conf_storage_location)) },
                  summary = {
                    if (mpvConfStorageLocation.isNotBlank()) {
                      Text(
                        getSimplifiedPathFromUri(mpvConfStorageLocation),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  onClick = { locationPicker.launch(null) },
                  iconButtonIcon = { 
                    Icon(
                      Icons.Default.Clear, 
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                    ) 
                  },
                  onIconButtonClick = { preferences.mpvConfStorageUri.delete() },
                  iconButtonEnabled = mpvConfStorageLocation.isNotBlank(),
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_advanced_mpv_conf,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_advanced_mpv_conf)) },
                  summary = {
                    val firstLine = mpvConf.lines().firstOrNull()
                    if (firstLine != null && firstLine.isNotBlank()) {
                      Text(
                        firstLine,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    } else {
                      Text(
                        stringResource(R.string.pref_advanced_tap_to_edit_configuration),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  onClick = {
                    backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.MPV_CONF))
                  },
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_advanced_input_conf,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_advanced_input_conf)) },
                  summary = {
                    val firstLine = inputConf.lines().firstOrNull()
                    if (firstLine != null && firstLine.isNotBlank()) {
                      Text(
                        firstLine,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    } else {
                      Text(
                        stringResource(R.string.pref_advanced_tap_to_edit_configuration),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  onClick = {
                    backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.INPUT_CONF))
                  },
                )
              }
            }
          }
          
          // Scripts Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_scripts))
          }
          
          item {
            val selectedScripts by preferences.selectedLuaScripts.collectAsState()
            val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
            
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_enable_lua_scripts_title,
              ) {
                SwitchPreference(
                  value = enableLuaScripts,
                  onValueChange = preferences.enableLuaScripts::set,
                  title = { Text(stringResource(R.string.pref_enable_lua_scripts_title)) },
                  summary = { 
                    Text(
                      stringResource(R.string.pref_enable_lua_scripts_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_manage_lua_scripts_title,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_manage_lua_scripts_title)) },
                  summary = {
                    when {
                      mpvConfStorageLocation.isBlank() || !enableLuaScripts -> Text(
                        stringResource(R.string.pref_advanced_lua_set_storage_first), 
                        color = MaterialTheme.colorScheme.outline
                      )
                      selectedScripts.isEmpty() -> Text(
                        stringResource(R.string.pref_advanced_no_scripts_enabled), 
                        color = MaterialTheme.colorScheme.outline
                      )
                      selectedScripts.size == 1 -> Text(
                        "1 script enabled",
                        color = MaterialTheme.colorScheme.outline
                      )
                      else -> Text(
                        "${selectedScripts.size} scripts enabled",
                        color = MaterialTheme.colorScheme.outline
                      )
                    }
                  },
                  onClick = {
                    backStack.add(LuaScriptsScreen)
                  },
                  enabled = mpvConfStorageLocation.isNotBlank() && enableLuaScripts,
                )
              }

            }
          }
          
          
          // Cache Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_cache))
          }
          
          item {
            var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
            var isClearThumbsConfirmShown by remember { mutableStateOf(false) }
            val thumbnailRepository = koinInject<ThumbnailRepository>()
            
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_clear_config_cache_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_clear_config_cache_title)) },
                  summary = { 
                    Text(
                      text = stringResource(R.string.pref_clear_config_cache_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      val mpvConfFile = File(context.filesDir, "mpv.conf")
                      mpvConfFile.delete()
                      preferences.mpvConf.delete()
                      withContext(Dispatchers.Main) {
                        mpvConf = ""
                        Toast
                          .makeText(
                            context,
                            context.getString(R.string.pref_advanced_config_cache_cleared),
                            Toast.LENGTH_SHORT,
                          ).show()
                      }
                    }
                  },
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_clear_thumbnail_cache_title,
              ) {
                Column {
                  Preference(
                    title = { Text(text = stringResource(R.string.pref_clear_thumbnail_cache_title)) },
                    summary = {
                      Text(
                        text = stringResource(R.string.pref_advanced_delete_thumbnails_summary),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                    onClick = { isClearThumbsConfirmShown = true },
                  )

                  if (isClearThumbsConfirmShown) {
                    ConfirmDialog(
                      title = stringResource(R.string.pref_advanced_clear_thumbnail_cache_confirm_title),
                      subtitle = stringResource(R.string.pref_advanced_clear_thumbnail_cache_confirm_subtitle),
                      onConfirm = {
                        scope.launch(Dispatchers.IO) {
                          runCatching {
                            thumbnailRepository.clearThumbnailCache()
                          }.onSuccess {
                            withContext(Dispatchers.Main) {
                              isClearThumbsConfirmShown = false
                              Toast.makeText(context, context.getString(R.string.pref_advanced_thumbnail_cache_cleared), Toast.LENGTH_SHORT).show()
                            }
                          }.onFailure { error ->
                            withContext(Dispatchers.Main) {
                              isClearThumbsConfirmShown = false
                              Toast.makeText(context, context.getString(R.string.clear_failed, error.message), Toast.LENGTH_LONG).show()
                            }
                          }
                        }
                      },
                      onCancel = { isClearThumbsConfirmShown = false },
                    )
                  }
                }
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_advanced_clear_fonts_cache,
              ) {
                Preference(
                  title = { Text(text = stringResource(id = R.string.pref_advanced_clear_fonts_cache)) },
                  summary = { 
                    Text(
                      text = stringResource(R.string.pref_advanced_remove_cached_fonts_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      // Under the shared fonts lock, so it can't interleave with "Add Font File" or a folder copy.
                      // Keeps fonts added with "Add Font File"; deletes nothing if that can't be determined safely.
                      val cleared =
                        when (val result = clearFontFiles(File(context.filesDir, "fonts"))) {
                          is FontClearResult.Cleared -> {
                            if (result.failed > 0) Log.w("AdvancedPreferences", "${result.failed} cached font(s) could not be deleted")
                            result.failed == 0
                          }
                          is FontClearResult.Unavailable -> {
                            Log.e("AdvancedPreferences", "Could not clear cached fonts", result.cause)
                            false
                          }
                        }
                      withContext(Dispatchers.Main) {
                        Toast
                          .makeText(
                            context,
                            context.getString(
                              if (cleared) R.string.pref_advanced_cleared_fonts_cache else R.string.pref_advanced_clear_fonts_failed,
                            ),
                            Toast.LENGTH_SHORT,
                          ).show()
                      }
                    }
                  },
                )
              }
            }
          }

          // System Integration Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_system_integration))
          }

          item {
            val enableMediaInfoActivity by preferences.enableMediaInfoActivity.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.ONLY,
                highlightKey = R.string.pref_advanced_enable_media_info_title,
              ) {
                SwitchPreference(
                  value = enableMediaInfoActivity,
                  onValueChange = {
                    preferences.enableMediaInfoActivity.set(it)
                    preferences.syncMediaInfoActivityStatus(context)
                  },
                  title = { Text(stringResource(R.string.pref_advanced_enable_media_info_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_advanced_enable_media_info_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }
          
          // Logging Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_logging))
          }
          
          item {
            val activity = LocalActivity.current!!
            @Suppress("DEPRECATION")
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            val verboseLogging by preferences.verboseLogging.collectAsState()
            
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_advanced_verbose_logging_title,
              ) {
                SwitchPreference(
                  value = verboseLogging,
                  onValueChange = preferences.verboseLogging::set,
                  title = { Text(stringResource(R.string.pref_advanced_verbose_logging_title)) },
                  summary = { 
                    Text(
                      stringResource(R.string.pref_advanced_verbose_logging_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                )
              }
              
              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_advanced_dump_logs_title,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_advanced_dump_logs_title)) },
                  summary = { 
                    Text(
                      stringResource(R.string.pref_advanced_dump_logs_summary),
                      color = MaterialTheme.colorScheme.outline,
                    ) 
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      val deviceInfo = CrashActivity.collectDeviceInfo()
                      val logcat = CrashActivity.collectLogcat()
      
                      clipboard.setText(AnnotatedString(CrashActivity.concatLogs(deviceInfo, null, logcat)))
                      CrashActivity.shareLogs(deviceInfo, null, logcat, activity)
                    }
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

fun getSimplifiedPathFromUri(uri: String): String =
  Environment.getExternalStorageDirectory().canonicalPath + "/" + Uri.decode(uri).substringAfterLast(":")
