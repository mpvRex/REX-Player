package xyz.mpv.rex.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.Decoder
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.TrackNode
import androidx.navigation3.runtime.NavBackStack
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.player.controls.components.sheets.AspectRatioSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.AudioTracksSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.ChaptersSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.CustomSkipDurationSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.SleepTimerSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.VideoZoomSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.DecodersSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.FrameNavigationSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.MoreSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.PlaybackSpeedSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.PlaylistSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.SubtitlesSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.OnlineSubtitleSearchSheet
import xyz.mpv.rex.ui.player.controls.components.sheets.ClipExportSheet
import xyz.mpv.rex.utils.media.MediaInfoParser
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState as composeCollectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PlayerSheets(
  sheetShown: Sheets,
  viewModel: xyz.mpv.rex.ui.player.PlayerViewModel,
  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri) -> Unit,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  onRemoveSubtitle: (Int) -> Unit,
  // audio sheet
  audioTracks: ImmutableList<TrackNode>,
  onAddAudio: (Uri) -> Unit,
  onSelectAudio: (TrackNode) -> Unit,
  // chapters sheet
  chapter: Segment?,
  chapters: ImmutableList<Segment>,
  onSeekToChapter: (Int) -> Unit,
  currentChapterIndex: Int? = null,
  // Decoders sheet
  decoder: Decoder,
  onUpdateDecoder: (Decoder) -> Unit,
  // Speed sheet
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetSpeedPresets: () -> Unit,
  onMakeDefaultSpeed: (Float) -> Unit,
  onResetDefaultSpeed: () -> Unit,
  // More sheet
  sleepTimerTimeRemaining: Int,
  onStartSleepTimer: (Int) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  onShowSheet: (Sheets) -> Unit,
  onDismissRequest: () -> Unit,
) {
  when (sheetShown) {
    Sheets.None -> {}
    Sheets.SubtitleTracks -> {
      val subtitlesPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddSubtitle(it)
        }

      val subtitlesPreferences = koinInject<xyz.mpv.rex.preferences.SubtitlesPreferences>()
      val savedPickerPath = subtitlesPreferences.pickerPath.get()
      val customFolder = subtitlesPreferences.customSubtitleFolder.get()
      val openAtVideoLocation = subtitlesPreferences.openPickerAtVideoLocation.get()

      val currentMediaTitle = viewModel.currentMediaTitle
      val matchToName = if (currentMediaTitle.isNotBlank()) {
          // Remove extension if present to improve matching
          currentMediaTitle.substringBeforeLast(".")
      } else null

      var showFilePicker by remember { mutableStateOf(false) }

      if (showFilePicker) {
          val initialPath = remember {
              val videoPath = `is`.xyz.mpv.MPVLib.getPropertyString("path")
              val videoDir = if (openAtVideoLocation && videoPath != null && videoPath.startsWith("/")) {
                  java.io.File(videoPath).parent
              } else null

              videoDir ?: customFolder.takeIf { it.isNotBlank() } ?: savedPickerPath.takeIf { it.isNotBlank() } ?: android.os.Environment.getExternalStorageDirectory().absolutePath
          }

          xyz.mpv.rex.ui.browser.dialogs.FilePickerDialog(
              isOpen = true,
              currentPath = initialPath,
              onDismiss = { showFilePicker = false },
              onPathChanged = { path ->
                  if (path != null) {
                      subtitlesPreferences.pickerPath.set(path)
                  }
              },
              onFileSelected = { path ->
                  showFilePicker = false
                   onAddSubtitle(Uri.parse("file://$path"))
              },
              onSystemPickerRequest = {
                  showFilePicker = false
                  subtitlesPicker.launch(
                    arrayOf(
                      "text/plain",
                      "text/srt",
                      "text/vtt",
                      "application/x-subrip",
                      "application/x-subtitle",
                      "text/x-ssa",
                      "*/*",
                    ),
                  )
              },
              matchToName = matchToName
          )
      }

      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onToggleSubtitle = onToggleSubtitle,
        isSubtitleSelected = isSubtitleSelected,
        onAddSubtitle = { showFilePicker = true },
        onRemoveSubtitle = onRemoveSubtitle,
        onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
        onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
        onOpenOnlineSearch = { onShowSheet(Sheets.OnlineSubtitleSearch) },
        onDismissRequest = onDismissRequest
      )
    }

    Sheets.OnlineSubtitleSearch -> {
      val isSearching by viewModel.isSearchingSub.composeCollectAsState()
      val isDownloading by viewModel.isDownloadingSub.composeCollectAsState()
      val results by viewModel.wyzieSearchResults.composeCollectAsState()
      val isOnlineSectionExpanded by viewModel.isOnlineSectionExpanded.composeCollectAsState()

      // Media Search / Autocomplete
      val mediaResults by viewModel.mediaSearchResults.composeCollectAsState()
      val isSearchingMedia by viewModel.isSearchingMedia.composeCollectAsState()
      
      // TV Show / Seasons / Episodes
      val selectedTvShow by viewModel.selectedTvShow.composeCollectAsState()
      val isFetchingTvDetails by viewModel.isFetchingTvDetails.composeCollectAsState()
      val selectedSeason by viewModel.selectedSeason.composeCollectAsState()
      val seasonEpisodes by viewModel.seasonEpisodes.composeCollectAsState()
      val isFetchingEpisodes by viewModel.isFetchingEpisodes.composeCollectAsState()
      val selectedEpisode by viewModel.selectedEpisode.composeCollectAsState()

      OnlineSubtitleSearchSheet(
        onDismissRequest = onDismissRequest,
        onDownloadOnline = { viewModel.downloadSubtitle(it) },
        isSearching = isSearching,
        isDownloading = isDownloading,
        searchResults = results.toImmutableList(),
        isOnlineSectionExpanded = isOnlineSectionExpanded,
        onToggleOnlineSection = { viewModel.toggleOnlineSection() },
        mediaTitle = viewModel.currentMediaTitle,
        // Autocomplete & Series Selection
        mediaSearchResults = mediaResults.toImmutableList(),
        isSearchingMedia = isSearchingMedia,
        onSearchMedia = { query ->
          // Parse both the user's search query and the original filename
          val queryInfo = MediaInfoParser.parse(query)
          val fileInfo = MediaInfoParser.parse(viewModel.currentMediaTitle)
          
          // Use clean title from query for TMDB search (strip S01E05 noise)
          val searchTitle = queryInfo.title.ifBlank { query }
          viewModel.subtitleManager.searchMedia(searchTitle)
          
          // Priority: TMDB selection > query parsed > file parsed
          val s = selectedSeason?.season_number ?: queryInfo.season ?: fileInfo.season
          val e = selectedEpisode?.episode_number ?: queryInfo.episode ?: fileInfo.episode
          val y = queryInfo.year ?: fileInfo.year
          viewModel.subtitleManager.searchSubtitles(searchTitle, s, e, y)
        },
        onSelectMedia = { viewModel.subtitleManager.selectMedia(it) },
        selectedTvShow = selectedTvShow,
        isFetchingTvDetails = isFetchingTvDetails,
        selectedSeason = selectedSeason,
        onSelectSeason = { viewModel.subtitleManager.selectSeason(it) },
        seasonEpisodes = seasonEpisodes.toImmutableList(),
        isFetchingEpisodes = isFetchingEpisodes,
        selectedEpisode = selectedEpisode,
        onSelectEpisode = { viewModel.subtitleManager.selectEpisode(it, viewModel.currentMediaTitle) },
        onClearMediaSelection = { viewModel.subtitleManager.clearMediaSelection() }
      )
    }

    Sheets.AudioTracks -> {
      val audioPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddAudio(it)
        }

      val audioPreferences = koinInject<xyz.mpv.rex.preferences.AudioPreferences>()
      val savedPickerPath = audioPreferences.pickerPath.get()
      val openAtVideoLocation = audioPreferences.openPickerAtVideoLocation.get()

      val currentMediaTitle = viewModel.currentMediaTitle
      val matchToName = if (currentMediaTitle.isNotBlank()) {
          // Remove extension if present to improve matching
          currentMediaTitle.substringBeforeLast(".")
      } else null

      var showFilePicker by remember { mutableStateOf(false) }

      if (showFilePicker) {
          val initialPath = remember {
              val videoPath = `is`.xyz.mpv.MPVLib.getPropertyString("path")
              val videoDir = if (openAtVideoLocation && videoPath != null && videoPath.startsWith("/")) {
                  java.io.File(videoPath).parent
              } else null

              videoDir ?: savedPickerPath.takeIf { it.isNotBlank() } ?: android.os.Environment.getExternalStorageDirectory().absolutePath
          }

          xyz.mpv.rex.ui.browser.dialogs.FilePickerDialog(
              isOpen = true,
              title = "Select Audio Track",
              currentPath = initialPath,
              onDismiss = { showFilePicker = false },
              onPathChanged = { path ->
                  if (path != null) {
                      audioPreferences.pickerPath.set(path)
                  }
              },
              onFileSelected = { path ->
                  showFilePicker = false
                  onAddAudio(Uri.parse("file://$path"))
              },
              onSystemPickerRequest = {
                  showFilePicker = false
                  audioPicker.launch(arrayOf("*/*"))
              },
              matchToName = matchToName,
              allowedExtensions = listOf(
                "mp3", "m4a", "ogg", "flac", "wav", "opus", "aac", "wma", "ac3", "eac3", "dts", "mka", "m4b", "ape"
              )
          )
      }

      AudioTracksSheet(
        tracks = audioTracks,
        onSelect = onSelectAudio,
        onAddAudioTrack = { showFilePicker = true },
        onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
        onDismissRequest,
      )
    }

    Sheets.Chapters -> {
      ChaptersSheet(
        chapters = chapters,
        currentChapter = chapter,
        currentChapterIndex = currentChapterIndex,
        onChapterClick = onSeekToChapter,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.Decoders -> {
      DecodersSheet(
        selectedDecoder = decoder,
        onSelect = onUpdateDecoder,
        onDismissRequest,
      )
    }

    Sheets.More -> {
      MoreSheet(
        remainingTime = sleepTimerTimeRemaining,
        onStartTimer = onStartSleepTimer,
        onDismissRequest = onDismissRequest,
        onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
        onAnime4KChanged = { viewModel.restartAmbientIfActive() },
        viewModel = viewModel,
        onShowSheet = onShowSheet,
      )
    }

    Sheets.PlaybackSpeed -> {
      PlaybackSpeedSheet(
        speed,
        onSpeedChange = onSpeedChange,
        speedPresets = speedPresets,
        onAddSpeedPreset = onAddSpeedPreset,
        onRemoveSpeedPreset = onRemoveSpeedPreset,
        onResetPresets = onResetSpeedPresets,
        onMakeDefault = onMakeDefaultSpeed,
        onResetDefault = onResetDefaultSpeed,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.VideoZoom -> {
      val videoZoom by viewModel.videoZoom.composeCollectAsState()
      VideoZoomSheet(
        videoZoom = videoZoom,
        onSetVideoZoom = viewModel::setVideoZoom,
        onResetVideoPan = viewModel::resetVideoPan,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.AspectRatios -> {
      val playerPreferences = koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
      val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
      val currentRatio by viewModel.currentAspectRatio.composeCollectAsState()
      val videoZoom by viewModel.videoZoom.composeCollectAsState()
      val videoPanX by viewModel.videoPanX.composeCollectAsState()
      val videoPanY by viewModel.videoPanY.composeCollectAsState()
      val advancedZoomEnabled by viewModel.advancedZoomEnabled.composeCollectAsState()
      val videoScaleX by viewModel.videoScaleX.composeCollectAsState()
      val videoScaleY by viewModel.videoScaleY.composeCollectAsState()
      val customRatios =
        customRatiosSet.mapNotNull { str ->
          val parts = str.split("|")
          if (parts.size == 2) {
            xyz.mpv.rex.ui.player.controls.components.sheets.AspectRatio(
              label = parts[0],
              ratio = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
              isCustom = true,
            )
          } else {
            null
          }
        }

      AspectRatioSheet(
        currentRatio = currentRatio,
        customRatios = customRatios,
        videoZoom = videoZoom,
        videoPanX = videoPanX,
        videoPanY = videoPanY,
        onZoomChange = { zoom ->
          viewModel.setVideoZoom(zoom)
          playerPreferences.defaultVideoZoom.set(zoom)
        },
        onPanXChange = { panX ->
          viewModel.setVideoPan(panX, viewModel.videoPanY.value)
          playerPreferences.defaultVideoPanX.set(panX)
        },
        onPanYChange = { panY ->
          viewModel.setVideoPan(viewModel.videoPanX.value, panY)
          playerPreferences.defaultVideoPanY.set(panY)
        },
        advancedZoomEnabled = advancedZoomEnabled,
        videoScaleX = videoScaleX,
        videoScaleY = videoScaleY,
        onAdvancedZoomToggle = { enabled ->
          viewModel.setAdvancedZoomEnabled(enabled)
          playerPreferences.advancedZoomEnabled.set(enabled)
          if (!enabled) {
            playerPreferences.defaultVideoScaleX.set(1f)
            playerPreferences.defaultVideoScaleY.set(1f)
          }
        },
        onScaleXChange = { scale ->
          viewModel.setVideoScaleX(scale)
          playerPreferences.defaultVideoScaleX.set(scale)
        },
        onScaleYChange = { scale ->
          viewModel.setVideoScaleY(scale)
          playerPreferences.defaultVideoScaleY.set(scale)
        },
        onResetAdvancedZoom = {
          viewModel.resetAdvancedZoom()
          playerPreferences.defaultVideoScaleX.set(1f)
          playerPreferences.defaultVideoScaleY.set(1f)
        },
        onSelectRatio = { ratio ->
          if (ratio < 0) {
            // Default selected - apply Fit mode
            viewModel.changeVideoAspect(xyz.mpv.rex.ui.player.VideoAspect.Fit)
          } else {
            // Custom ratio selected
            viewModel.setCustomAspectRatio(ratio)
          }
        },
        onAddCustomRatio = { label, ratio ->
          playerPreferences.customAspectRatios.set(customRatiosSet + "$label|$ratio")
          viewModel.setCustomAspectRatio(ratio)
        },
        onDeleteCustomRatio = { ratio ->
          val toRemove = "${ratio.label}|${ratio.ratio}"
          playerPreferences.customAspectRatios.set(customRatiosSet - toRemove)
          // If the deleted ratio is currently active, reset to default (Fit)
          if (kotlin.math.abs(currentRatio - ratio.ratio) < 0.01) {
            viewModel.changeVideoAspect(xyz.mpv.rex.ui.player.VideoAspect.Fit)
          }
        },
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.FrameNavigation -> {
      val currentFrame by viewModel.currentFrame.composeCollectAsState()
      val totalFrames by viewModel.totalFrames.composeCollectAsState()
      FrameNavigationSheet(
        currentFrame = currentFrame,
        totalFrames = totalFrames,
        onUpdateFrameInfo = viewModel::updateFrameInfo,
        onPause = viewModel::pause,
        onUnpause = viewModel::unpause,
        onPauseUnpause = viewModel::pauseUnpause,
        onSeekTo = { position, _ -> viewModel.seekTo(position) },
        onDismissRequest = onDismissRequest,
      )
    }


    Sheets.Playlist -> {
      // Refresh playlist items when sheet is shown
      LaunchedEffect(Unit) {
        viewModel.refreshPlaylistItems()
      }

      // Observe playlist updates
      val playlist by viewModel.playlistItems.collectAsState()
      val playerPreferences = koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()

      if (playlist.isNotEmpty()) {
        val playlistImmutable = playlist.toImmutableList()
        val totalCount = viewModel.getPlaylistTotalCount()
        val isM3U = viewModel.isPlaylistM3U()
        PlaylistSheet(
          playlist = playlistImmutable,
          onDismissRequest = onDismissRequest,
          onItemClick = { item ->
            viewModel.playPlaylistItem(item.index)
          },
          onReorderItem = { from, to ->
            viewModel.reorderPlaylistItem(from, to)
          },
          onRemoveItems = { indexes ->
            viewModel.removePlaylistItems(indexes)
          },
          totalCount = totalCount,
          isM3UPlaylist = isM3U,
          playerPreferences = playerPreferences,
        )
      }
    }


    Sheets.CustomSkipDuration -> {
      val playerPreferences = koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
      val customSkipDuration by playerPreferences.customSkipDuration.collectAsState()
      CustomSkipDurationSheet(
        duration = customSkipDuration,
        onDurationChange = { playerPreferences.customSkipDuration.set(it) },
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.SleepTimer -> {
      SleepTimerSheet(
        remainingTime = sleepTimerTimeRemaining,
        onStartTimer = onStartSleepTimer,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.ClipExport -> {
      val a by viewModel.abLoopA.composeCollectAsState()
      val b by viewModel.abLoopB.composeCollectAsState()
      val valA = a ?: 0.0
      val valB = b ?: 0.0
      val startSec = minOf(valA, valB)
      val endSec = maxOf(valA, valB)
      val context = androidx.compose.ui.platform.LocalContext.current

      ClipExportSheet(
        startSec = startSec,
        endSec = endSec,
        onExport = { mode ->
          viewModel.cutABLoopClip(context, mode)
        },
        onDismissRequest = onDismissRequest,
      )
    }
  }
}
