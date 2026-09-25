package xyz.mpv.rex.ui.preferences

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.documentfile.provider.DocumentFile
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import xyz.mpv.rex.utils.media.OpenDocumentTreeContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import xyz.mpv.rex.repository.wyzie.WyzieEncodings
import xyz.mpv.rex.repository.wyzie.WyzieFormats
import xyz.mpv.rex.repository.wyzie.WyzieSources
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.CustomFontEntry
import xyz.mpv.rex.utils.media.copyFontsFromDirectory
import xyz.mpv.rex.utils.media.loadCustomFontEntries
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import android.net.Uri
import xyz.mpv.rex.repository.wyzie.WyzieLanguages
import xyz.mpv.rex.utils.media.SubtitleEncodingUtils
import org.koin.compose.koinInject

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()
    val fileManager = koinInject<FileManager>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_subtitles),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = backstack::removeLastOrNull,
            ) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val fontsFolder by preferences.fontsFolder.collectAsState()
        var availableFonts by remember { mutableStateOf<List<String>>(emptyList()) }
        var customFontEntries by remember { mutableStateOf<List<CustomFontEntry>>(emptyList()) }
        var fontLoadTrigger by remember { mutableIntStateOf(0) }
        var isLoadingFonts by remember { mutableStateOf(false) }

        val locationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.fontsFolder.set(uri.toString())

            // Copy fonts immediately in background
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
              isLoadingFonts = true
              copyFontsFromDirectory(context, fileManager, uri.toString())
              withContext(Dispatchers.Main) {
                fontLoadTrigger++
                isLoadingFonts = false
              }
            }
          }

        // Load fonts when folder changes or trigger is fired
        LaunchedEffect(fontsFolder, fontLoadTrigger) {
          customFontEntries = loadCustomFontEntries(context)
          availableFonts = listOf("Default") + customFontEntries.map { it.familyName }
        }

        // Auto-refresh fonts on app restart if directory is set
        LaunchedEffect(Unit) {
          if (fontsFolder.isNotBlank()) {
            isLoadingFonts = true
            withContext(Dispatchers.IO) {
              copyFontsFromDirectory(context, fileManager, fontsFolder)
            }
            fontLoadTrigger++
            isLoadingFonts = false
          }
        }

        val subtitleSaveFolder by preferences.subtitleSaveFolder.collectAsState()
        val wyzieHearingImpaired by preferences.wyzieHearingImpaired.collectAsState()
        val wyzieSources by preferences.wyzieSources.collectAsState()
        val wyzieFormats by preferences.wyzieFormats.collectAsState()
        val wyzieEncodings by preferences.wyzieEncodings.collectAsState()
        val wyzieApiKey by preferences.wyzieApiKey.collectAsState()

        val saveLocationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.subtitleSaveFolder.set(uri.toString())
          }

        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          // === GENERAL SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.general))
          }

          item {
            val preferredLanguages by preferences.preferredLanguages.collectAsState()
            val disableByDefault by preferences.disableSubtitlesByDefault.collectAsState()
            val autoload by preferences.autoloadMatchingSubtitles.collectAsState()
            val overrideAss by preferences.overrideAssSubs.collectAsState()
            val scaleByWindow by preferences.scaleByWindow.collectAsState()
            val forceLtr by preferences.forceLtr.collectAsState()
            val openAtVideoLocation by preferences.openPickerAtVideoLocation.collectAsState()
            val customSubtitleFolder by preferences.customSubtitleFolder.collectAsState()
            val customFolderPicker =
              rememberLauncherForActivityResult(
                OpenDocumentTreeContract(),
              ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult

                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                
                val path = getSimplifiedPathFromUri(uri.toString())
                preferences.customSubtitleFolder.set(path)
              }

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_subtitles, R.string.pref_preferred_languages),
              ) {
                TextFieldPreference(
                  value = preferredLanguages,
                  onValueChange = preferences.preferredLanguages::set,
                  textToValue = { it },
                  title = { Text(stringResource(R.string.pref_preferred_languages)) },
                  summary = {
                    if (preferredLanguages.isNotBlank()) {
                      Text(
                        preferredLanguages,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    } else {
                      Text(
                        stringResource(R.string.not_set_video_default),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  },
                  textField = { value, onValueChange, _ ->
                    Column {
                      Text(stringResource(R.string.enter_language_codes))
                      TextField(
                        value,
                        onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.language_codes_placeholder)) },
                      )
                    }
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_disable_by_default_title,
              ) {
                SwitchPreference(
                  value = disableByDefault,
                  onValueChange = { preferences.disableSubtitlesByDefault.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_disable_by_default_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_disable_by_default_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_autoload_title,
              ) {
                SwitchPreference(
                  value = autoload,
                  onValueChange = { preferences.autoloadMatchingSubtitles.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_autoload_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_autoload_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.player_sheets_sub_override_ass,
              ) {
                SwitchPreference(
                  value = overrideAss,
                  onValueChange = { preferences.overrideAssSubs.set(it) },
                  title = { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
                  summary = {
                    Text(
                      stringResource(R.string.player_sheets_sub_override_ass_subtitle),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.player_sheets_sub_scale_by_window,
              ) {
                SwitchPreference(
                  value = scaleByWindow,
                  onValueChange = { preferences.scaleByWindow.set(it) },
                  title = { Text(stringResource(R.string.player_sheets_sub_scale_by_window)) },
                  summary = {
                    Text(
                      stringResource(R.string.player_sheets_sub_scale_by_window_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_force_ltr_title,
              ) {
                SwitchPreference(
                  value = forceLtr,
                  onValueChange = { preferences.forceLtr.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_force_ltr_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_force_ltr_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              val subtitleEncoding by preferences.subtitleEncoding.collectAsState()
              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_encoding_title,
              ) {
                ListPreference(
                  value = subtitleEncoding,
                  onValueChange = { preferences.subtitleEncoding.set(it) },
                  values = SubtitleEncodingUtils.ENCODINGS.map { it.code },
                  valueToText = { androidx.compose.ui.text.AnnotatedString(SubtitleEncodingUtils.getDisplayName(it)) },
                  title = { Text(stringResource(R.string.pref_subtitles_encoding_title)) },
                  summary = {
                    Text(
                      SubtitleEncodingUtils.getDisplayName(subtitleEncoding),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_open_at_video_location_title,
              ) {
                SwitchPreference(
                  value = openAtVideoLocation,
                  onValueChange = { preferences.openPickerAtVideoLocation.set(it) },
                  title = { Text(stringResource(R.string.pref_subtitles_open_at_video_location_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_subtitles_open_at_video_location_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_custom_picker_folder_title,
              ) {
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clickable { customFolderPicker.launch(null) }
                      .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        stringResource(R.string.pref_subtitles_custom_picker_folder_title),
                        style = MaterialTheme.typography.titleMedium,
                      )
                      Text(
                        if (customSubtitleFolder.isBlank()) stringResource(R.string.not_set_video_default) else customSubtitleFolder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (customSubtitleFolder.isNotBlank()) {
                      IconButton(onClick = { preferences.customSubtitleFolder.set("") }) {
                        Icon(
                          Icons.Default.Clear,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.tertiary,
                        )
                      }
                    }
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_subtitles_fonts_dir,
              ) {
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clickable { locationPicker.launch(null) }
                      .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                    ) {
                      Text(
                        stringResource(R.string.pref_subtitles_fonts_dir),
                        style = MaterialTheme.typography.titleMedium,
                      )
                      if (fontsFolder.isBlank()) {
                        Text(
                          stringResource(R.string.not_set_system_fonts),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      } else {
                        if (availableFonts.isNotEmpty()) {
                          Text(
                            stringResource(R.string.fonts_loaded, availableFonts.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                          )
                        }
                      }
                    }

                    if (fontsFolder.isNotBlank()) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoadingFonts) {
                          Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                          ) {
                            CircularProgressIndicator(
                              modifier = Modifier.size(24.dp),
                              strokeWidth = 2.dp,
                              color = MaterialTheme.colorScheme.primary,
                            )
                          }
                        } else {
                          IconButton(
                            onClick = {
                              kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                isLoadingFonts = true
                                copyFontsFromDirectory(context, fileManager, fontsFolder)
                                withContext(Dispatchers.Main) {
                                  fontLoadTrigger++
                                  isLoadingFonts = false
                                }
                              }
                            },
                          ) {
                            Icon(
                              Icons.Default.Refresh,
                              contentDescription = stringResource(R.string.reload_fonts),
                              tint = MaterialTheme.colorScheme.primary,
                            )
                          }
                        }

                        IconButton(
                          onClick = {
                            preferences.fontsFolder.set("")
                            fontLoadTrigger++
                          },
                        ) {
                          Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear_font_directory),
                            tint = MaterialTheme.colorScheme.tertiary,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          // === ONLINE SUBTITLE SECTION ===
          item {
            PreferenceSectionHeader(title = "Subtitle Search")
          }

          item {
            var showClearDialog by remember { mutableStateOf(false) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val subdlLanguages by preferences.subdlLanguages.collectAsState()
            var showAdvanced by remember { mutableStateOf(false) }

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_subtitles_save_location,
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveLocationPicker.launch(null) }
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                ) {
                  Column {
                    Text(
                      stringResource(R.string.pref_subtitles_save_location),
                      style = MaterialTheme.typography.titleMedium,
                    )
                    val folderPath = if (subtitleSaveFolder.isBlank()) {
                      stringResource(R.string.not_set_video_default)
                    } else {
                      Uri.parse(subtitleSaveFolder).path ?: subtitleSaveFolder
                    }
                    Text(
                      text = folderPath,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf("Subtitle Sources", R.string.pref_subtitle_search_title),
              ) {
                MultiChoicePreference(
                  title = { Text("Subtitle Sources") },
                  summary = {
                    val summaryText = if (wyzieSources.isEmpty() || wyzieSources.contains("all")) {
                      "All"
                    } else {
                      wyzieSources.mapNotNull { WyzieSources.ALL[it] }.joinToString(", ")
                    }
                    Text(summaryText, color = MaterialTheme.colorScheme.outline)
                  },
                  values = WyzieSources.ALL,
                  selectedValues = wyzieSources,
                  onValuesChange = { preferences.wyzieSources.set(it) },
                  hasAllOption = true
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_subdl_languages,
              ) {
                MultiChoicePreference(
                  title = { Text(stringResource(R.string.pref_subtitles_subdl_languages)) },
                  summary = {
                    val summaryText = if (subdlLanguages.isEmpty() || subdlLanguages.contains("all")) {
                      stringResource(R.string.all_languages)
                    } else {
                      subdlLanguages.mapNotNull { WyzieLanguages.ALL[it] }.joinToString(", ")
                    }
                    Text(summaryText, color = MaterialTheme.colorScheme.outline)
                  },
                  values = WyzieLanguages.SORTED,
                  selectedValues = subdlLanguages,
                  onValuesChange = { preferences.subdlLanguages.set(it) },
                  hasAllOption = true
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = listOf("Advanced Search Filters", "Hearing-impaired friendly"),
              ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { showAdvanced = !showAdvanced }
                      .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = "Advanced Search Filters",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold
                    )
                    Icon(
                      imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                  
                  if (showAdvanced) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                      SwitchPreference(
                        value = wyzieHearingImpaired,
                        onValueChange = { preferences.wyzieHearingImpaired.set(it) },
                        title = { Text("Hearing-impaired friendly") },
                        summary = { Text("Only show subtitles optimized for hearing impaired") }
                      )

                      PreferenceDivider()

                      MultiChoicePreference(
                        title = { Text("Preferred Formats") },
                        summary = {
                          val summaryText = if (wyzieFormats.isEmpty() || wyzieFormats.contains("all")) {
                            "All"
                          } else {
                            wyzieFormats.mapNotNull { WyzieFormats.ALL[it] }.joinToString(", ")
                          }
                          Text(summaryText, color = MaterialTheme.colorScheme.outline)
                        },
                        values = WyzieFormats.ALL,
                        selectedValues = wyzieFormats,
                        onValuesChange = { preferences.wyzieFormats.set(it) },
                        hasAllOption = true
                      )

                      PreferenceDivider()

                      MultiChoicePreference(
                        title = { Text("Preferred Encodings") },
                        summary = {
                          val summaryText = if (wyzieEncodings.isEmpty() || wyzieEncodings.contains("all")) {
                            "All"
                          } else {
                            wyzieEncodings.mapNotNull { WyzieEncodings.ALL[it] }.joinToString(", ")
                          }
                          Text(summaryText, color = MaterialTheme.colorScheme.outline)
                        },
                        values = WyzieEncodings.ALL,
                        selectedValues = wyzieEncodings,
                        onValuesChange = { preferences.wyzieEncodings.set(it) },
                        hasAllOption = true
                      )
                      
                      Spacer(modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_subtitles_clear_downloads,
              ) {
                Preference(
                  title = { Text(stringResource(R.string.pref_subtitles_clear_downloads), color = MaterialTheme.colorScheme.error) },
                  summary = { Text(stringResource(R.string.pref_subtitles_clear_downloads_summary)) },
                  onClick = { showClearDialog = true },
                  enabled = subtitleSaveFolder.isNotBlank()
                )

                if (showClearDialog) {
                  AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text(stringResource(R.string.pref_subtitles_clear_downloads)) },
                    text = { Text(stringResource(R.string.pref_subtitles_clear_downloads_confirmation)) },
                    confirmButton = {
                      TextButton(
                        onClick = {
                          showClearDialog = false
                          scope.launch(Dispatchers.IO) {
                            runCatching {
                              val uri = Uri.parse(subtitleSaveFolder)
                              val folder = DocumentFile.fromTreeUri(context, uri)
                              folder?.listFiles()?.forEach { it.delete() }
                              withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, R.string.toast_subtitles_cleared, android.widget.Toast.LENGTH_SHORT).show()
                              }
                            }.onFailure { e ->
                              withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                              }
                            }
                          }
                        }
                      ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                      }
                    },
                    dismissButton = {
                      TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                      }
                    }
                  )
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_subtitles_wyzie_api_key_title,
              ) {
                Column {
                  TextFieldPreference(
                    value = wyzieApiKey,
                    onValueChange = preferences.wyzieApiKey::set,
                    textToValue = { it.trim() },
                    title = { Text(stringResource(R.string.pref_subtitles_wyzie_api_key_title)) },
                    summary = {
                      val summaryText = if (wyzieApiKey.isNotBlank()) {
                        stringResource(R.string.pref_subtitles_wyzie_api_key_masked)
                      } else {
                        stringResource(R.string.pref_subtitles_wyzie_api_key_not_set)
                      }
                      Text(summaryText, color = MaterialTheme.colorScheme.outline)
                    },
                    textField = { value, onValueChange, _ ->
                      val uriHandler = LocalUriHandler.current
                      val fullString = stringResource(R.string.pref_subtitles_wyzie_api_key_summary)
                      val linkText = "sub.wyzie.io/redeem"
                      
                      val annotatedString = buildAnnotatedString {
                          val startIndex = fullString.indexOf(linkText)
                          if (startIndex >= 0) {
                              append(fullString.substring(0, startIndex))
                              pushStringAnnotation(tag = "URL", annotation = "https://$linkText")
                              withStyle(style = SpanStyle(
                                  color = MaterialTheme.colorScheme.primary,
                                  textDecoration = TextDecoration.Underline,
                                  fontWeight = FontWeight.Bold
                              )) {
                                  append(linkText)
                              }
                              pop()
                              append(fullString.substring(startIndex + linkText.length))
                          } else {
                              append(fullString)
                          }
                      }

                      Column(modifier = Modifier.padding(top = 8.dp)) {
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                          value = value,
                          onValueChange = onValueChange,
                          modifier = Modifier.fillMaxWidth(),
                          singleLine = true,
                          placeholder = { Text(stringResource(R.string.pref_subtitles_wyzie_api_key_hint)) },
                          shape = RoundedCornerShape(12.dp),
                          colors = OutlinedTextFieldDefaults.colors(
                              focusedBorderColor = MaterialTheme.colorScheme.primary,
                              unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                              focusedContainerColor = MaterialTheme.colorScheme.surface,
                              unfocusedContainerColor = MaterialTheme.colorScheme.surface
                          )
                        )
                      }
                    },
                  )

                  PreferenceDivider()
                  
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Subtitle Search provided by",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = "sub.wyzie.io",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sub.wyzie.io"))
                        context.startActivity(intent)
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MultiChoicePreference(
  title: @Composable () -> Unit,
  summary: @Composable () -> Unit,
  values: Map<String, String>,
  selectedValues: Set<String>,
  onValuesChange: (Set<String>) -> Unit,
  hasAllOption: Boolean = false
) {
  var showDialog by remember { mutableStateOf(false) }
  var tempSelection by remember(showDialog) { mutableStateOf(selectedValues) }

  Preference(
    title = title,
    summary = summary,
    onClick = { showDialog = true }
  )

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = title,
      text = {
        LazyColumn {
          items(values.toList().size) { index ->
            val entry = values.toList()[index]
            val key = entry.first
            val checked = if (hasAllOption && tempSelection.contains("all")) {
              true
            } else {
              tempSelection.contains(key)
            }
            
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  val newSet = tempSelection.toMutableSet()
                  if (hasAllOption) {
                    if (key == "all") {
                      if (checked) newSet.clear()
                      else newSet.addAll(values.keys)
                    } else {
                      newSet.remove("all")
                      if (checked) newSet.remove(key) else newSet.add(key)
                    }
                  } else {
                    if (checked) newSet.remove(key) else newSet.add(key)
                  }
                  tempSelection = newSet
                }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = null
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = entry.second)
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          enabled = !tempSelection.isEmpty(),
          onClick = {
            onValuesChange(tempSelection)
            showDialog = false
          }
        ) {
          Text(stringResource(android.R.string.ok))
        }
      }
    )
  }
}
