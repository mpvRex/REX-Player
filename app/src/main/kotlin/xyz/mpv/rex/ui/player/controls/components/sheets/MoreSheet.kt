package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.anime4k.Anime4KManager
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.GesturePreferences
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.player.ResumePlaybackMode
import xyz.mpv.rex.preferences.getPlayerButtonLabel
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.controls.RenderPlayerButton
import xyz.mpv.rex.ui.theme.spacing
import xyz.mpv.rex.ui.player.PlayerOrientation
import xyz.mpv.rex.ui.player.PlaybackControlsPosition
import `is`.xyz.mpv.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
  remainingTime: Int,
  onStartTimer: (Int) -> Unit,
  onDismissRequest: () -> Unit,
  onEnterFiltersPanel: () -> Unit,
  onAnime4KChanged: () -> Unit = {},
  viewModel: PlayerViewModel,
  onShowSheet: (Sheets) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs = listOf(
    stringResource(R.string.player_sheets_tab_controls),
    stringResource(R.string.player_sheets_tab_aesthetics),
    stringResource(R.string.player_sheets_tab_gestures),
    stringResource(R.string.player_sheets_tab_settings),
    stringResource(R.string.player_sheets_tab_interaction),
  )

  val initialTab = remember { viewModel.lastMoreSheetTab.get().coerceIn(0, tabs.lastIndex) }
  val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
  val scope = rememberCoroutineScope()

  // Persist tab change
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect {
      if (viewModel.lastMoreSheetTab.get() != it) {
        viewModel.lastMoreSheetTab.set(it)
      }
    }
  }

  PlayerSheet(
    onDismissRequest,
    modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = tween(durationMillis = 300))
    ) {
      PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 12.dp,
        divider = {}
      ) {
        tabs.forEachIndexed { index, title ->
          Tab(
            selected = pagerState.currentPage == index,
            onClick = { 
              scope.launch { pagerState.animateScrollToPage(index) }
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(44.dp).padding(horizontal = 4.dp),
            text = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = when(index) {
                      0 -> Icons.Default.Widgets
                      1 -> Icons.Default.Palette
                      2 -> Icons.Default.TouchApp
                      3 -> Icons.Default.Settings
                      else -> Icons.Default.Tune
                  },
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                )
              }
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

      HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 350.dp),
        verticalAlignment = Alignment.Top,
        beyondViewportPageCount = 1
      ) { page ->
        when (page) {
          0 -> ControlsTab(
            viewModel = viewModel,
            onDismissRequest = onDismissRequest,
            onShowSheet = onShowSheet
          )
          1 -> AestheticsTab()
          2 -> GesturesTab()
          3 -> SettingsTab(
            remainingTime = remainingTime,
            onStartTimer = onStartTimer,
            onEnterFiltersPanel = onEnterFiltersPanel,
            onAnime4KChanged = onAnime4KChanged,
            onDismissRequest = onDismissRequest,
            onShowSheet = onShowSheet,
          )
          4 -> InteractionTab()
        }
      }

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(
  remainingTime: Int,
  onStartTimer: (Int) -> Unit,
  onEnterFiltersPanel: () -> Unit,
  onAnime4KChanged: () -> Unit,
  onDismissRequest: () -> Unit,
  onShowSheet: (Sheets) -> Unit,
) {
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val decoderPreferences = koinInject<DecoderPreferences>()
  val anime4kManager = koinInject<Anime4KManager>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()

  val enableAnime4K by decoderPreferences.enableAnime4K.collectAsState()
  val anime4kMode by decoderPreferences.anime4kMode.collectAsState()
  val anime4kQuality by decoderPreferences.anime4kQuality.collectAsState()
  val gpuNext by decoderPreferences.gpuNext.collectAsState()
  val useVulkan by decoderPreferences.useVulkan.collectAsState()

  val scope = rememberCoroutineScope()
  val activity = LocalContext.current as PlayerActivity

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = MaterialTheme.spacing.medium)
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(id = R.string.player_sheets_more_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = { onShowSheet(Sheets.SleepTimer) }) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
              Icon(imageVector = Icons.Outlined.Timer, contentDescription = null)
              Text(
                text =
                  if (remainingTime == 0) {
                    stringResource(R.string.timer_title)
                  } else {
                    stringResource(
                      R.string.timer_remaining,
                      DateUtils.formatElapsedTime(remainingTime.toLong()),
                    )
                  },
              )
            }
          }
          TextButton(onClick = onEnterFiltersPanel) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
              Icon(imageVector = Icons.Default.Tune, contentDescription = null)
              Text(text = stringResource(id = R.string.player_sheets_filters_title))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))

      Text(
        text = stringResource(R.string.player_sheets_stats_page_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        items(6) { page ->
          FilterChip(
            label = {
              Text(
                stringResource(
                  if (page ==
                    0
                  ) {
                    R.string.player_sheets_tracks_off
                  } else {
                    R.string.player_sheets_stats_page_chip
                  },
                  page,
                ),
              )
            },
            onClick = {
              if ((page == 0) xor (statisticsPage == 0)) MPVLib.command("script-binding", "stats/display-stats-toggle")
              if (page != 0) MPVLib.command("script-binding", "stats/display-page-$page")
              advancedPreferences.enabledStatisticsPage.set(page)
            },
            selected = statisticsPage == page,
            leadingIcon = null,
          )
        }
      }

      // Shaders Controls
      if (enableAnime4K && (!gpuNext || useVulkan)) {
        // Auto-detect resolution to disable for 4K+
        val width = MPVLib.getPropertyInt("video-params/w") ?: 0
        val height = MPVLib.getPropertyInt("video-params/h") ?: 0
        val isHighRes = width >= 3840 || height >= 2160

        // Presets (Mode) - Now on Top
        Text(
            text = stringResource(R.string.anime4k_mode_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (isHighRes) {
            Text(
                text = stringResource(R.string.anime4k_not_available_high_res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          items(Anime4KManager.Mode.entries) { mode ->
            FilterChip(
              label = { Text(stringResource(mode.titleRes)) },
              selected = anime4kMode == mode.name,
              enabled = !isHighRes,
              leadingIcon = null,
              onClick = {
                decoderPreferences.anime4kMode.set(mode.name)

                // Apply shaders immediately (runtime change)
                scope.launch(Dispatchers.IO) {
                  runCatching {
                    val qualityStr = decoderPreferences.anime4kQuality.get()
                    val quality = try {
                      Anime4KManager.Quality.valueOf(qualityStr)
                    } catch (e: IllegalArgumentException) {
                      Anime4KManager.Quality.BALANCED
                    }
                    val currentMode = try {
                        Anime4KManager.Mode.valueOf(mode.name)
                    } catch (e: IllegalArgumentException) {
                        Anime4KManager.Mode.OFF
                    }

                    val shaderChain = anime4kManager.getShaderChain(currentMode, quality)

                    // Use setPropertyString for runtime changes
                    MPVLib.setPropertyString("glsl-shaders", if (shaderChain.isNotEmpty()) shaderChain else "")
                    // Restart ambient mode if it was ON (Anime4K reset wiped it)
                    onAnime4KChanged()
                  }
                }
              }
            )
          }
        }

        Text(
            text = stringResource(R.string.anime4k_quality_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          items(Anime4KManager.Quality.entries) { quality ->
             FilterChip(
              label = { Text(stringResource(quality.titleRes)) },
              selected = anime4kQuality == quality.name,
              enabled = anime4kMode != "OFF" && !isHighRes,
              leadingIcon = null,
              onClick = {
                decoderPreferences.anime4kQuality.set(quality.name)

                // Apply shaders immediately (runtime change)
                scope.launch(Dispatchers.IO) {
                  runCatching {
                    val modeStr = decoderPreferences.anime4kMode.get()
                    val modeEnum = try {
                      Anime4KManager.Mode.valueOf(modeStr)
                    } catch (e: IllegalArgumentException) {
                      Anime4KManager.Mode.OFF
                    }
                    val currentQuality = try {
                        Anime4KManager.Quality.valueOf(quality.name)
                    } catch (e: IllegalArgumentException) {
                        Anime4KManager.Quality.BALANCED
                    }

                    val shaderChain = anime4kManager.getShaderChain(modeEnum, currentQuality)

                    // Use setPropertyString for runtime changes
                    MPVLib.setPropertyString("glsl-shaders", if (shaderChain.isNotEmpty()) shaderChain else "")
                    // Restart ambient mode if it was ON (Anime4K reset wiped it)
                    onAnime4KChanged()
                  }
                }
              }
            )
          }
        }
      }
  }
}

@Composable
fun ControlsTab(
  viewModel: PlayerViewModel,
  onDismissRequest: () -> Unit,
  onShowSheet: (Sheets) -> Unit,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()

  val topRightControlsPref by appearancePreferences.topRightControls.collectAsState()
  val bottomRightControlsPref by appearancePreferences.bottomRightControls.collectAsState()
  val bottomLeftControlsPref by appearancePreferences.bottomLeftControls.collectAsState()
  val portraitBottomControlsPref by appearancePreferences.portraitBottomControls.collectAsState()
  val moreSheetControlsPref by appearancePreferences.moreSheetControls.collectAsState()

  val bottomControlsBelowSeekbar by playerPreferences.bottomControlsBelowSeekbar.collectAsState()
  val showControlsOnPlay by playerPreferences.showControlsOnPlay.collectAsState()

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

  // Data needed for visibility checks
  val chapters by viewModel.chapters.collectAsState(persistentListOf())
  val playlist by viewModel.playlistManager.playlist.collectAsState(emptyList())
  val availableVideoQualities by viewModel.availableVideoQualities.collectAsState()
  val hasPlaylistSupport = playlist.size > 1
  val hasMultipleQualities = availableVideoQualities.size > 1

  // 1. Determine what's on screen (to hide from sheet)
  val visibleOnScreen = remember(
      isPortrait,
      topRightControlsPref,
      bottomRightControlsPref,
      bottomLeftControlsPref,
      portraitBottomControlsPref,
      chapters,
      hasPlaylistSupport,
      hasMultipleQualities,
  ) {
      val visible = mutableSetOf<PlayerButton>()
      if (isPortrait) {
          val allPortrait = appearancePreferences.parseButtons(portraitBottomControlsPref, mutableSetOf())
          val visibleInRow = allPortrait.filter { button ->
              when (button) {
                  PlayerButton.BOOKMARKS_CHAPTERS -> chapters.isNotEmpty()
                  PlayerButton.SHUFFLE -> hasPlaylistSupport
                  PlayerButton.CURRENT_CHAPTER -> false
                  PlayerButton.VIDEO_QUALITY -> hasMultipleQualities
                  PlayerButton.NONE -> false
                  else -> true
              }
          }
          visible.addAll(visibleInRow)
      } else {
          val allLandscape = mutableSetOf<PlayerButton>()
          allLandscape.addAll(appearancePreferences.parseButtons(topRightControlsPref, mutableSetOf()))
          allLandscape.addAll(appearancePreferences.parseButtons(bottomRightControlsPref, mutableSetOf()))
          allLandscape.addAll(appearancePreferences.parseButtons(bottomLeftControlsPref, mutableSetOf()))
          val visibleInLandscape = allLandscape.filter { button ->
              when (button) {
                  PlayerButton.BOOKMARKS_CHAPTERS -> chapters.isNotEmpty()
                  PlayerButton.SHUFFLE -> hasPlaylistSupport
                  PlayerButton.CURRENT_CHAPTER -> chapters.isNotEmpty()
                  PlayerButton.VIDEO_QUALITY -> hasMultipleQualities
                  PlayerButton.NONE -> false
                  else -> true
              }
          }
          visible.addAll(visibleInLandscape)
      }
      // Always exclude these from the dynamic sheet calculations as they are static or special
      visible.add(PlayerButton.BACK_ARROW)
      visible.add(PlayerButton.VIDEO_TITLE)
      visible.add(PlayerButton.MORE_OPTIONS)
      visible
  }

  // 2. Calculate the dynamic list of buttons for the sheet
  val buttons = remember(moreSheetControlsPref, visibleOnScreen, chapters, hasPlaylistSupport, hasMultipleQualities) {
      // Start with buttons the user explicitly wants in the More Sheet (respect order)
      val userOrderedMoreButtons = appearancePreferences.parseButtons(moreSheetControlsPref, mutableSetOf())

      // Calculate "Orphaned" buttons: items in ALL buttons that are NOT on screen AND NOT in the More Sheet pref
      val allAvailable = xyz.mpv.rex.preferences.allPlayerButtons
      val orphanedButtons = allAvailable.filter { it !in visibleOnScreen && it !in userOrderedMoreButtons }

      // Combine them: User Order first, then the rest
      (userOrderedMoreButtons + orphanedButtons).filter { button ->
          // Don't show if already visible on screen
          if (visibleOnScreen.contains(button)) return@filter false

          // Functional filters (don't show buttons that can't work)
          when (button) {
              PlayerButton.SHUFFLE -> hasPlaylistSupport
              PlayerButton.BOOKMARKS_CHAPTERS -> chapters.isNotEmpty()
              PlayerButton.CURRENT_CHAPTER -> chapters.isNotEmpty()
              PlayerButton.AB_LOOP -> true // Always show in more sheet if missing from UI
              PlayerButton.VIDEO_QUALITY -> hasMultipleQualities
              else -> true
          }
      }
  }

  val currentChapter by MPVLib.propInt["chapter"].collectAsState(0)
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState(1f)
  val isSpeedNonOne by remember(playbackSpeed) {
    derivedStateOf { abs((playbackSpeed ?: 1f) - 1f) > 0.001f }
  }
  val currentZoom by viewModel.videoZoom.collectAsState()
  val aspect by viewModel.videoAspect.collectAsState()

  val activity = LocalContext.current as PlayerActivity
  val mpvDecoder by MPVLib.propString["hwdec-current"].collectAsState("")
  val decoder by remember { derivedStateOf { xyz.mpv.rex.ui.player.Decoder.getDecoderFromValue(mpvDecoder ?: "auto") } }

  val mediaTitle by remember(activity) {
      derivedStateOf {
          val title = activity.getTitleForControls()
          if (title.startsWith("http://") || title.startsWith("https://") || title.contains(".m3u8") || title.contains(".m3u")) {
              val raw = MPVLib.getPropertyString("media-title")
              if (raw != null && raw.isNotBlank() && !raw.startsWith("http://") && !raw.startsWith("https://") && !raw.contains(".m3u8") && !raw.contains(".m3u")) {
                  raw
              } else {
                  title
              }
          } else {
              title
          }
      }
  }

  Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium)
          .verticalScroll(rememberScrollState())
  ) {
      Text(
          text = stringResource(R.string.player_sheets_extended_controls_title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium)
      )

      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
          modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.medium)
      ) {
          buttons.forEach { button ->
              RenderPlayerButton(
                  button = button,
                  chapters = chapters,
                  currentChapter = currentChapter,
                  isPortrait = isPortrait, 
                  isSpeedNonOne = isSpeedNonOne,
                  currentZoom = currentZoom,
                  aspect = aspect,
                  mediaTitle = mediaTitle,
                  hideBackground = false, // Force background for better visibility on sheet surface
                  decoder = decoder,
                  playbackSpeed = playbackSpeed ?: 1f,
                  onBackPress = { activity.onBackPressedDispatcher.onBackPressed() },
                  onOpenSheet = {
                      onDismissRequest()
                      onShowSheet(it)
                  },
                  onOpenPanel = {
                      onDismissRequest()
                      viewModel.panelShown.value = it
                  },
                  viewModel = viewModel,
                  activity = activity,
                  buttonSize = 40.dp,
                  isMoreSheet = true
              )
          }
      }

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

      InteractionSwitch(
        label = stringResource(R.string.pref_controls_layout_below_seekbar_title),
        description = stringResource(if (bottomControlsBelowSeekbar) R.string.pref_controls_layout_below_seekbar_summary_true else R.string.pref_controls_layout_below_seekbar_summary_false),
        checked = bottomControlsBelowSeekbar,
        onCheckedChange = { playerPreferences.bottomControlsBelowSeekbar.set(it) }
      )

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))

      InteractionSwitch(
        label = stringResource(R.string.pref_appearance_show_controls_on_play_title),
        description = stringResource(R.string.pref_appearance_show_controls_on_play_summary),
        checked = showControlsOnPlay,
        onCheckedChange = { playerPreferences.showControlsOnPlay.set(it) }
      )

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))

      PlayerTimeToDisappearPreferenceItem()

      Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))

      PlaybackControlsPositionPreferenceItem()
  }
}

@Composable
fun AestheticsTab() {
  val context = LocalContext.current
  val appearancePreferences = koinInject<AppearancePreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()

  val whiteSeekBar by playerPreferences.whiteSeekBar.collectAsState()
  val enableBounceAnimation by appearancePreferences.enableBounceAnimation.collectAsState()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
  val enableGlassSeekbar by appearancePreferences.enableGlassSeekbarBackground.collectAsState()
  val matchPlayerControlsToTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val playerAlwaysDarkMode by appearancePreferences.playerAlwaysDarkMode.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Text(
      text = stringResource(R.string.player_sheets_tab_aesthetics),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = MaterialTheme.spacing.small)
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_hide_player_buttons_background_title),
      description = stringResource(R.string.pref_appearance_hide_player_buttons_background_summary),
      checked = hideBackground,
      onCheckedChange = { appearancePreferences.hidePlayerButtonsBackground.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_enable_glass_player_controls_title),
      description = stringResource(R.string.pref_appearance_enable_glass_player_controls_summary),
      checked = enableGlass,
      onCheckedChange = { appearancePreferences.enableGlassPlayerControls.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_enable_glass_seekbar_title),
      description = stringResource(R.string.pref_appearance_enable_glass_seekbar_summary),
      checked = enableGlassSeekbar,
      onCheckedChange = { appearancePreferences.enableGlassSeekbarBackground.set(it) },
      enabled = enableGlass
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_white_seekbar_title),
      description = stringResource(R.string.pref_player_white_seekbar_summary),
      checked = whiteSeekBar,
      onCheckedChange = { playerPreferences.whiteSeekBar.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_enable_bounce_animation_title),
      description = stringResource(R.string.pref_appearance_enable_bounce_animation_summary),
      checked = enableBounceAnimation,
      onCheckedChange = { appearancePreferences.enableBounceAnimation.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_match_player_controls_to_theme_title),
      description = stringResource(R.string.pref_appearance_match_player_controls_to_theme_summary),
      checked = matchPlayerControlsToTheme,
      onCheckedChange = { appearancePreferences.matchPlayerControlsToTheme.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_appearance_player_always_dark_mode_title),
      description = stringResource(R.string.pref_appearance_player_always_dark_mode_summary),
      checked = if (enableGlass) true else playerAlwaysDarkMode,
      onCheckedChange = { appearancePreferences.playerAlwaysDarkMode.set(it) },
      enabled = !enableGlass,
      onDisabledClick = {
        Toast.makeText(
          context,
          context.getString(R.string.pref_appearance_player_always_dark_mode_disabled_toast),
          Toast.LENGTH_SHORT
        ).show()
      }
    )
  }
}

@Composable
fun GesturesTab() {
  val playerPreferences = koinInject<PlayerPreferences>()
  val gesturePreferences = koinInject<GesturePreferences>()

  val brightnessGesture by playerPreferences.brightnessGesture.collectAsState()
  val volumeGesture by playerPreferences.volumeGesture.collectAsState()
  val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
  val horizontalSwipeToSeek by playerPreferences.horizontalSwipeToSeek.collectAsState()
  val preventSeekbarTap by gesturePreferences.preventSeekbarTap.collectAsState()
  val useSingleTapForCenter by gesturePreferences.useSingleTapForCenter.collectAsState()
  val useSingleTapForLeftRight by gesturePreferences.useSingleTapForLeftRight.collectAsState()
  val reverseDoubleTap by gesturePreferences.reverseDoubleTap.collectAsState()
  val dontShowSeekBarOnDoubleTap by gesturePreferences.dontShowSeekBarOnDoubleTap.collectAsState()
  val twoFingerTapToSkipChapter by gesturePreferences.twoFingerTapToSkipChapter.collectAsState()
  val swipeToSubtitleSeek by playerPreferences.swipeToSubtitleSeek.collectAsState()
  val moveSubtitleByDragging by playerPreferences.moveSubtitleByDragging.collectAsState()
  val pinchToResizeSubtitle by playerPreferences.pinchToResizeSubtitle.collectAsState()
  val panAndZoomEnabled by playerPreferences.panAndZoomEnabled.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Text(
      text = stringResource(R.string.player_sheets_tab_gestures),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = MaterialTheme.spacing.small)
    )

    // Player Orientation Preference Item
    OrientationPreferenceItem()

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_brightness),
      description = stringResource(R.string.pref_player_gestures_brightness),
      checked = brightnessGesture,
      onCheckedChange = { playerPreferences.brightnessGesture.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_volume),
      description = stringResource(R.string.pref_player_gestures_volume),
      checked = volumeGesture,
      onCheckedChange = { playerPreferences.volumeGesture.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_pinch_to_zoom),
      description = stringResource(R.string.pref_player_gestures_pinch_to_zoom),
      checked = pinchToZoomGesture,
      onCheckedChange = { playerPreferences.pinchToZoomGesture.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_horizontal_swipe_to_seek),
      description = stringResource(R.string.pref_player_gestures_horizontal_swipe_to_seek),
      checked = horizontalSwipeToSeek,
      onCheckedChange = { playerPreferences.horizontalSwipeToSeek.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_reverse_double_tap_title),
      description = stringResource(R.string.pref_gesture_reverse_double_tap_summary),
      checked = reverseDoubleTap,
      onCheckedChange = { gesturePreferences.reverseDoubleTap.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_dont_show_seekbar_on_double_tap_title),
      description = stringResource(R.string.pref_gesture_dont_show_seekbar_on_double_tap_summary),
      checked = dontShowSeekBarOnDoubleTap,
      onCheckedChange = { gesturePreferences.dontShowSeekBarOnDoubleTap.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_two_finger_tap_chapter_skip_title),
      description = stringResource(R.string.pref_gesture_two_finger_tap_chapter_skip_summary),
      checked = twoFingerTapToSkipChapter,
      onCheckedChange = { gesturePreferences.twoFingerTapToSkipChapter.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_prevent_seekbar_tap_title),
      description = stringResource(R.string.pref_gesture_prevent_seekbar_tap_summary),
      checked = preventSeekbarTap,
      onCheckedChange = { gesturePreferences.preventSeekbarTap.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_use_single_tap_for_center_title),
      description = stringResource(R.string.pref_gesture_use_single_tap_for_center_summary),
      checked = useSingleTapForCenter,
      onCheckedChange = { gesturePreferences.useSingleTapForCenter.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_gesture_use_single_tap_for_left_right_title),
      description = stringResource(R.string.pref_gesture_use_single_tap_for_left_right_summary),
      checked = useSingleTapForLeftRight,
      onCheckedChange = { gesturePreferences.useSingleTapForLeftRight.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_swipe_to_subtitle_seek_title),
      description = stringResource(R.string.pref_player_gestures_swipe_to_subtitle_seek_summary),
      checked = swipeToSubtitleSeek,
      onCheckedChange = { playerPreferences.swipeToSubtitleSeek.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_move_subtitle_by_dragging_title),
      description = stringResource(R.string.pref_player_gestures_move_subtitle_by_dragging_summary),
      checked = moveSubtitleByDragging,
      onCheckedChange = { playerPreferences.moveSubtitleByDragging.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_pinch_to_resize_subtitle_title),
      description = stringResource(R.string.pref_player_gestures_pinch_to_resize_subtitle_summary),
      checked = pinchToResizeSubtitle,
      onCheckedChange = { playerPreferences.pinchToResizeSubtitle.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_gestures_pan_and_zoom),
      description = stringResource(R.string.pref_player_gestures_pan_and_zoom_summary),
      checked = panAndZoomEnabled,
      onCheckedChange = { playerPreferences.panAndZoomEnabled.set(it) }
    )
  }
}

@Composable
fun InteractionTab() {
  val playerPreferences = koinInject<PlayerPreferences>()

  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val showSeekBarWhenSeeking by playerPreferences.showSeekBarWhenSeeking.collectAsState()
  val showDoubleTapOvals by playerPreferences.showDoubleTapOvals.collectAsState()
  val showCircularDoubleTapSeek by playerPreferences.showCircularDoubleTapSeek.collectAsState()
  val showSeekTimeWhileSeeking by playerPreferences.showSeekTimeWhileSeeking.collectAsState()
  val usePreciseSeeking by playerPreferences.usePreciseSeeking.collectAsState()
  val hideOsdText by playerPreferences.hideOsdText.collectAsState()
  val keepScreenOnWhenPaused by playerPreferences.keepScreenOnWhenPaused.collectAsState()
  val resumePlaybackMode by playerPreferences.resumePlaybackMode.collectAsState()
  val autoResumeOnAsk by playerPreferences.autoResumeOnAsk.collectAsState()
  val autoPiPOnNavigation by playerPreferences.autoPiPOnNavigation.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Text(
      text = stringResource(R.string.pref_player_interaction_header),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = MaterialTheme.spacing.small)
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_autoplay_title),
      description = stringResource(R.string.pref_autoplay_summary),
      checked = playlistMode,
      onCheckedChange = { playerPreferences.playlistMode.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_show_seekbar_when_seeking_title),
      description = stringResource(R.string.pref_player_show_seekbar_when_seeking_summary),
      checked = showSeekBarWhenSeeking,
      onCheckedChange = { playerPreferences.showSeekBarWhenSeeking.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.show_splash_ovals_on_double_tap_to_seek),
      description = stringResource(R.string.show_splash_ovals_on_double_tap_to_seek),
      checked = showDoubleTapOvals,
      onCheckedChange = { playerPreferences.showDoubleTapOvals.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_show_circular_double_tap_seek_title),
      description = stringResource(R.string.pref_player_show_circular_double_tap_seek_summary),
      checked = showCircularDoubleTapSeek,
      onCheckedChange = { playerPreferences.showCircularDoubleTapSeek.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.show_time_on_double_tap_to_seek),
      description = stringResource(R.string.show_time_on_double_tap_to_seek),
      checked = showSeekTimeWhileSeeking,
      onCheckedChange = { playerPreferences.showSeekTimeWhileSeeking.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_use_precise_seeking),
      description = stringResource(R.string.pref_player_use_precise_seeking),
      checked = usePreciseSeeking,
      onCheckedChange = { playerPreferences.usePreciseSeeking.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_hide_osd_text_title),
      description = stringResource(R.string.pref_player_hide_osd_text_summary),
      checked = hideOsdText,
      onCheckedChange = { playerPreferences.hideOsdText.set(it) }
    )

    InteractionSwitch(
      label = stringResource(R.string.pref_player_keep_screen_on_when_paused_title),
      description = stringResource(R.string.pref_player_keep_screen_on_when_paused_summary),
      checked = keepScreenOnWhenPaused,
      onCheckedChange = { playerPreferences.keepScreenOnWhenPaused.set(it) }
    )

    ResumePlaybackModePreferenceItem()

    if (resumePlaybackMode == ResumePlaybackMode.Ask) {
      InteractionSwitch(
        label = stringResource(R.string.pref_player_auto_resume_on_ask_title),
        description = stringResource(
          if (autoResumeOnAsk) {
            R.string.pref_player_auto_resume_on_ask_summary_on
          } else {
            R.string.pref_player_auto_resume_on_ask_summary_off
          }
        ),
        checked = autoResumeOnAsk,
        onCheckedChange = { playerPreferences.autoResumeOnAsk.set(it) }
      )
    }

    InteractionSwitch(
      label = stringResource(R.string.pref_auto_pip_title),
      description = stringResource(R.string.pref_auto_pip_summary),
      checked = autoPiPOnNavigation,
      onCheckedChange = { playerPreferences.autoPiPOnNavigation.set(it) }
    )
  }
}

@Composable
private fun InteractionSwitch(
  label: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null,
) {
  val alpha = if (enabled) 1.0f else 0.5f
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().alpha(alpha)
  ) {
    SwitchPreference(
      value = checked,
      onValueChange = onCheckedChange,
      enabled = enabled,
      onDisabledClick = onDisabledClick,
      title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
      summary = { 
        Text(
          text = description, 
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        ) 
      }
    )
  }
}

@Composable
private fun OrientationPreferenceItem() {
  val playerPreferences = koinInject<PlayerPreferences>()
  val orientation by playerPreferences.orientation.collectAsState()
  var showDialog by remember { mutableStateOf(false) }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth()
  ) {
    ListItem(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { showDialog = true },
      headlineContent = {
        Text(
          text = stringResource(R.string.pref_player_orientation),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      supportingContent = {
        Text(
          text = stringResource(orientation.titleRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      },
      trailingContent = {
        Icon(
          imageVector = Icons.Default.ScreenRotation,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = { Text(stringResource(R.string.pref_player_orientation)) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          PlayerOrientation.entries.forEach { mode ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  playerPreferences.orientation.set(mode)
                  showDialog = false
                }
                .padding(vertical = 12.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = orientation == mode,
                onClick = null
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = stringResource(mode.titleRes),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    )
  }
}

@Composable
private fun ResumePlaybackModePreferenceItem() {
  val playerPreferences = koinInject<PlayerPreferences>()
  val resumePlaybackMode by playerPreferences.resumePlaybackMode.collectAsState()
  var showDialog by remember { mutableStateOf(false) }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth()
  ) {
    ListItem(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { showDialog = true },
      headlineContent = {
        Text(
          text = stringResource(R.string.pref_player_resume_playback_title),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      supportingContent = {
        Text(
          text = stringResource(resumePlaybackMode.titleRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      },
      trailingContent = {
        Icon(
          imageVector = Icons.Default.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = { Text(stringResource(R.string.pref_player_resume_playback_title)) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          ResumePlaybackMode.entries.forEach { mode ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  playerPreferences.resumePlaybackMode.set(mode)
                  showDialog = false
                }
                .padding(vertical = 12.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = resumePlaybackMode == mode,
                onClick = null
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = stringResource(mode.titleRes),
                  style = MaterialTheme.typography.bodyLarge
                )
                Text(
                  text = stringResource(mode.summaryRes),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.outline
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    )
  }
}

@Composable
private fun PlayerTimeToDisappearPreferenceItem() {
  val playerPreferences = koinInject<PlayerPreferences>()
  val playerTimeToDisappear by playerPreferences.playerTimeToDisappear.collectAsState()
  val predefinedTimeValues = listOf(0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000)
  val isCustomTimeValue = !predefinedTimeValues.contains(playerTimeToDisappear)
  val offLabel = stringResource(R.string.generic_off)
  val customLabel = stringResource(R.string.generic_custom)
  var showDialog by remember { mutableStateOf(false) }
  var showCustomDialog by remember { mutableStateOf(false) }
  var customTimeValue by remember { mutableStateOf("") }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth()
  ) {
    ListItem(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { showDialog = true },
      headlineContent = {
        Text(
          text = stringResource(R.string.pref_player_display_hide_player_control_time),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      supportingContent = {
        Text(
          text = when {
            playerTimeToDisappear == 0 -> offLabel
            isCustomTimeValue -> stringResource(
              R.string.pref_player_display_custom_time_summary,
              playerTimeToDisappear
            )
            else -> stringResource(
              R.string.pref_player_display_time_ms_format,
              playerTimeToDisappear
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      },
      trailingContent = {
        Icon(
          imageVector = Icons.Outlined.Timer,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = { Text(stringResource(R.string.pref_player_display_hide_player_control_time)) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          (predefinedTimeValues + listOf(-1)).forEach { value ->
            val isSelected = if (value == -1) isCustomTimeValue else playerTimeToDisappear == value
            val labelText = when (value) {
              0 -> offLabel
              -1 -> customLabel
              else -> "$value ms"
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (value == -1) {
                    customTimeValue = playerTimeToDisappear.toString()
                    showDialog = false
                    showCustomDialog = true
                  } else {
                    playerPreferences.playerTimeToDisappear.set(value)
                    showDialog = false
                  }
                }
                .padding(vertical = 12.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = isSelected,
                onClick = null
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = labelText,
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    )
  }

  if (showCustomDialog) {
    AlertDialog(
      onDismissRequest = { showCustomDialog = false },
      title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          Text(
            text = stringResource(R.string.pref_player_display_custom_hide_time_dialog_message),
            modifier = Modifier.padding(bottom = 8.dp)
          )
          OutlinedTextField(
            value = customTimeValue,
            onValueChange = { customTimeValue = it },
            label = { Text(stringResource(id = R.string.milliseconds)) },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val value = customTimeValue.toIntOrNull()
            if (value != null && value in 0..1000000000) {
              playerPreferences.playerTimeToDisappear.set(value)
              showCustomDialog = false
            }
          }
        ) {
          Text(stringResource(R.string.generic_ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { showCustomDialog = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    )
  }
}

@Composable
private fun PlaybackControlsPositionPreferenceItem() {
  val playerPreferences = koinInject<PlayerPreferences>()
  val playbackControlsPosition by playerPreferences.playbackControlsPosition.collectAsState()
  var showDialog by remember { mutableStateOf(false) }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth()
  ) {
    ListItem(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { showDialog = true },
      headlineContent = {
        Text(
          text = stringResource(R.string.pref_playback_controls_position_title),
          style = MaterialTheme.typography.bodyLarge
        )
      },
      supportingContent = {
        Text(
          text = stringResource(playbackControlsPosition.titleRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
      },
      trailingContent = {
        Icon(
          imageVector = Icons.Default.Tune,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = { Text(stringResource(R.string.pref_playback_controls_position_title)) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          PlaybackControlsPosition.entries.forEach { position ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  playerPreferences.playbackControlsPosition.set(position)
                  showDialog = false
                }
                .padding(vertical = 12.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = playbackControlsPosition == position,
                onClick = null
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = stringResource(position.titleRes),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    )
  }
}

