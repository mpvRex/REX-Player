package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.browser.sheets.AutoPlaylistsSheet
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object MediaLibraryPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val foldersPreferences = koinInject<FoldersPreferences>()
    val appearancePreferences = koinInject<AppearancePreferences>()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val hybridMediaIndex = koinInject<HybridMediaIndexRepository>()
    val metadataCache = koinInject<VideoMetadataCacheRepository>()
    val mpvexDatabase = koinInject<MpvExDatabase>()

    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val includeNoMediaContent by browserPreferences.includeNoMediaContent.collectAsState()
    val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
    val libraryScanRoots by foldersPreferences.libraryScanRoots.collectAsState()
    val showNetworkThumbnails by appearancePreferences.showNetworkThumbnails.collectAsState()
    val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
    val watchedThreshold by browserPreferences.watchedThreshold.collectAsState()
    val showTreeViewPath by browserPreferences.showTreeViewPath.collectAsState()
    val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
    val showUnplayedOldVideoLabel by appearancePreferences.showUnplayedOldVideoLabel.collectAsState()
    val unplayedOldVideoDays by appearancePreferences.unplayedOldVideoDays.collectAsState()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val autoRemoveDeleted by advancedPreferences.autoRemoveDeletedFromHistory.collectAsState()
    val excludeExternalPlayback by advancedPreferences.excludeExternalPlaybackFromHistory.collectAsState()
    var showNetworkWarning by remember { mutableStateOf(false) }
    var showAutoPlaylistsSheet by remember { mutableStateOf(false) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_media_library_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
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
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      ProvidePreferenceLocals {
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          // Content & Discovery Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_content_discovery))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_media_library_title, R.string.pref_include_no_media_content_title),
              ) {
                SwitchPreference(
                  value = includeNoMediaContent,
                  onValueChange = { newValue ->
                    browserPreferences.includeNoMediaContent.set(newValue)
                    MediaLibraryEvents.notifyChanged()
                    scope.launch(Dispatchers.IO) {
                      runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
                    }
                  },
                  title = { Text(text = stringResource(R.string.pref_include_no_media_content_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_include_no_media_content_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_show_audio_files_title,
              ) {
                SwitchPreference(
                  value = showAudioFiles,
                  onValueChange = { newValue ->
                    browserPreferences.showAudioFiles.set(newValue)
                    MediaLibraryEvents.notifyChanged()
                  },
                  title = { Text(text = stringResource(R.string.pref_show_audio_files_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_show_audio_files_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_appearance_show_network_thumbnails_title,
              ) {
                Column {
                  SwitchPreference(
                    value = showNetworkThumbnails,
                    onValueChange = { newValue ->
                      if (newValue) {
                        showNetworkWarning = true
                      } else {
                        appearancePreferences.showNetworkThumbnails.set(false)
                      }
                    },
                    title = { Text(text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_title)) },
                    summary = {
                      Text(
                        text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_summary),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  )

                  if (showNetworkWarning) {
                    AlertDialog(
                      onDismissRequest = { showNetworkWarning = false },
                      title = { Text(stringResource(R.string.pref_appearance_network_thumbnails_dialog_title)) },
                      text = {
                        Column {
                          Text(
                            text = stringResource(R.string.pref_appearance_network_thumbnails_dialog_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                      },
                      confirmButton = {
                        TextButton(onClick = {
                          appearancePreferences.showNetworkThumbnails.set(true)
                          showNetworkWarning = false
                        }) {
                          Text(stringResource(R.string.generic_confirm))
                        }
                      },
                      dismissButton = {
                        TextButton(onClick = { showNetworkWarning = false }) {
                          Text(stringResource(R.string.generic_cancel))
                        }
                      }
                    )
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.auto_playlists,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.auto_playlists)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.auto_playlists_desc),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { showAutoPlaylistsSheet = true },
                )
              }
            }
          }

          // File Browser Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_appearance_category_file_browser))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_appearance_unlimited_name_lines_title,
              ) {
                SwitchPreference(
                  value = unlimitedNameLines,
                  onValueChange = { appearancePreferences.unlimitedNameLines.set(it) },
                  title = { Text(text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_title)) },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_appearance_show_unplayed_old_video_label_title,
              ) {
                SwitchPreference(
                  value = showUnplayedOldVideoLabel,
                  onValueChange = { appearancePreferences.showUnplayedOldVideoLabel.set(it) },
                  title = { Text(text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_title)) },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_appearance_unplayed_old_video_days_title,
              ) {
                SliderPreference(
                  value = unplayedOldVideoDays.toFloat(),
                  onValueChange = { appearancePreferences.unplayedOldVideoDays.set(it.roundToInt()) },
                  title = { Text(text = stringResource(id = R.string.pref_appearance_unplayed_old_video_days_title)) },
                  valueRange = 1f..30f,
                  summary = {
                    Text(
                      text = stringResource(
                        id = R.string.pref_appearance_unplayed_old_video_days_summary,
                        unplayedOldVideoDays,
                      ),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onSliderValueChange = { appearancePreferences.unplayedOldVideoDays.set(it.roundToInt()) },
                  sliderValue = unplayedOldVideoDays.toFloat(),
                  enabled = showUnplayedOldVideoLabel
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_appearance_auto_scroll_title,
              ) {
                SwitchPreference(
                  value = autoScrollToLastPlayed,
                  onValueChange = { browserPreferences.autoScrollToLastPlayed.set(it) },
                  title = { Text(text = stringResource(R.string.pref_appearance_auto_scroll_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_appearance_auto_scroll_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_appearance_watched_threshold_title,
              ) {
                SliderPreference(
                  value = watchedThreshold.toFloat(),
                  onValueChange = { browserPreferences.watchedThreshold.set(it.roundToInt()) },
                  sliderValue = watchedThreshold.toFloat(),
                  onSliderValueChange = { browserPreferences.watchedThreshold.set(it.roundToInt()) },
                  title = { Text(text = stringResource(id = R.string.pref_appearance_watched_threshold_title)) },
                  valueRange = 50f..100f,
                  valueSteps = 9,
                  summary = {
                    Text(
                      text = stringResource(
                        id = R.string.pref_appearance_watched_threshold_summary,
                        watchedThreshold,
                      ),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_show_tree_view_path_title,
              ) {
                SwitchPreference(
                  value = showTreeViewPath,
                  onValueChange = { browserPreferences.showTreeViewPath.set(it) },
                  title = { Text(text = stringResource(id = R.string.pref_show_tree_view_path_title)) },
                  summary = {
                    Text(
                      text = stringResource(id = R.string.pref_show_tree_view_path_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                )
              }
            }
          }

          // Storage & Exclusions Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_storage_exclusions))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_folders_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_folders_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_folders_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { backstack.add(FoldersPreferencesScreen) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_library_roots_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_library_roots_title)) },
                  summary = {
                    Text(
                      text = if (libraryScanRoots.isEmpty()) {
                        stringResource(R.string.pref_library_roots_empty_title)
                      } else {
                        stringResource(R.string.pref_library_root_count, libraryScanRoots.size)
                      },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { backstack.add(LibraryRootsPreferencesScreen) },
                )
              }
            }
          }

          // History Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_advanced_section_history))
          }

          item {
            var isConfirmDialogShown by remember { mutableStateOf(false) }

            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_advanced_enable_recently_played_title,
              ) {
                SwitchPreference(
                  value = enableRecentlyPlayed,
                  onValueChange = advancedPreferences.enableRecentlyPlayed::set,
                  title = { Text(stringResource(R.string.pref_advanced_enable_recently_played_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_advanced_enable_recently_played_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_advanced_history_auto_remove_deleted_title,
              ) {
                SwitchPreference(
                  value = autoRemoveDeleted,
                  onValueChange = advancedPreferences.autoRemoveDeletedFromHistory::set,
                  enabled = enableRecentlyPlayed,
                  title = { Text(stringResource(R.string.pref_advanced_history_auto_remove_deleted_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_advanced_history_auto_remove_deleted_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_advanced_history_exclude_external_title,
              ) {
                SwitchPreference(
                  value = excludeExternalPlayback,
                  onValueChange = advancedPreferences.excludeExternalPlaybackFromHistory::set,
                  enabled = enableRecentlyPlayed,
                  title = { Text(stringResource(R.string.pref_advanced_history_exclude_external_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_advanced_history_exclude_external_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_advanced_clear_playback_history,
              ) {
                Column {
                  Preference(
                    title = { Text(stringResource(R.string.pref_advanced_clear_playback_history)) },
                    onClick = { isConfirmDialogShown = true },
                  )

                  if (isConfirmDialogShown) {
                    ConfirmDialog(
                      stringResource(R.string.pref_advanced_clear_playback_history_confirm_title),
                      stringResource(R.string.pref_advanced_clear_playback_history_confirm_subtitle),
                      onConfirm = {
                        scope.launch(Dispatchers.IO) {
                          runCatching {
                            mpvexDatabase.videoDataDao().clearAllPlaybackStates()
                            RecentlyPlayedOps.clearAll()
                          }.onSuccess {
                            withContext(Dispatchers.Main) {
                              isConfirmDialogShown = false
                              Toast
                                .makeText(
                                  context,
                                  context.getString(R.string.pref_advanced_cleared_playback_history),
                                  Toast.LENGTH_SHORT,
                                ).show()
                            }
                          }.onFailure { error ->
                            withContext(Dispatchers.Main) {
                              isConfirmDialogShown = false
                              Toast
                                .makeText(
                                  context,
                                  context.getString(R.string.clear_failed, error.message),
                                  Toast.LENGTH_LONG,
                                ).show()
                            }
                          }
                        }
                      },
                      onCancel = { isConfirmDialogShown = false },
                    )
                  }
                }
              }
            }
          }

          // Library Maintenance Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_library_maintenance))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_rescan_library_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_rescan_library_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_rescan_library_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
                    }
                    Toast.makeText(context, context.getString(R.string.pref_rescan_started_toast), Toast.LENGTH_SHORT).show()
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_clear_metadata_cache_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_clear_metadata_cache_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_clear_metadata_cache_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      runCatching { metadataCache.clearAll() }
                    }
                    Toast.makeText(context, context.getString(R.string.pref_cache_cleared_toast), Toast.LENGTH_SHORT).show()
                  },
                )
              }
            }
          }
        }
      }

      AutoPlaylistsSheet(
        isOpen = showAutoPlaylistsSheet,
        onDismiss = { showAutoPlaylistsSheet = false },
      )
    }
  }
}
