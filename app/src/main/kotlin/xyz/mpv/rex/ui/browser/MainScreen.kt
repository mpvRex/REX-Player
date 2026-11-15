package xyz.mpv.rex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.icons.RexIcon
import xyz.mpv.rex.ui.icons.RexIcons
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.dialogs.CommunityLinksDialog
import xyz.mpv.rex.ui.browser.folderlist.FolderListScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.shorts.ShortsScreen
import xyz.mpv.rex.ui.browser.you.YouScreen
import xyz.mpv.rex.ui.browser.selection.SelectionManager
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayer
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerDefaults
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.ui.motion.RexMotion
import xyz.mpv.rex.ui.motion.RexPress

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0
  private var persistentPreviousTab: Int = 0
  
  private val _tabRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
  val tabRequest = _tabRequest.asSharedFlow()

  private val _scrollToTopRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val scrollToTopRequest = _scrollToTopRequest.asSharedFlow()

  fun requestTab(tab: Int) {
    _tabRequest.tryEmit(tab)
  }
  
  fun requestPreviousTab() {
    _tabRequest.tryEmit(persistentPreviousTab)
  }

  // Shared state that can be updated by FileSystemBrowserScreen
  private val _isInSelectionModeShared = MutableStateFlow(false)
  val isInSelectionModeShared = _isInSelectionModeShared.asStateFlow()
  
  private val _shouldHideNavigationBar = MutableStateFlow(false)
  val shouldHideNavigationBar = _shouldHideNavigationBar.asStateFlow()

  /**
   * Height of the navigation bar + device inset currently painted under the browser, as seen by
   * anything composed *outside* this screen — in practice the mini player sheet, which lives in the
   * activity window so its page can cover pushed screens and therefore cannot read this tree's
   * composition locals.
   *
   * `null` means "no answer": this screen has not composed yet, or has been pushed over. The sheet
   * then docks on the window edge instead of on a bar that may not be painted.
   */
  private val _miniPlayerBottomChromeHeight = MutableStateFlow<Dp?>(null)
  val miniPlayerBottomChromeHeight = _miniPlayerBottomChromeHeight.asStateFlow()
  
  private val _isBrowserBottomBarVisible = MutableStateFlow(false)
  val isBrowserBottomBarVisible = _isBrowserBottomBarVisible.asStateFlow()
  
  private val _sharedVideoSelectionManager = MutableStateFlow<Any?>(null)
  val sharedVideoSelectionManager = _sharedVideoSelectionManager.asStateFlow()
  
  // Check if the selection contains only videos and update navigation bar visibility accordingly
  private val _onlyVideosSelected = MutableStateFlow(false)
  val onlyVideosSelected = _onlyVideosSelected.asStateFlow()
  
  // Track when permission denied screen is showing to hide FAB
  private val _isPermissionDenied = MutableStateFlow(false)
  val isPermissionDenied = _isPermissionDenied.asStateFlow()
  
  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    _isInSelectionModeShared.value = isInSelectionMode
    _onlyVideosSelected.value = isOnlyVideosSelected
    _sharedVideoSelectionManager.value = selectionManager
    
    // Only hide navigation bar when videos are selected AND in selection mode
    // This fixes the issue where bottom bar disappears when only videos are selected
    _shouldHideNavigationBar.value = isInSelectionMode && isOnlyVideosSelected
  }
  
  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    _isPermissionDenied.value = isDenied
  }

  /**
   * Get current permission denied state
   */
  fun getPermissionDeniedState(): Boolean = _isPermissionDenied.value

  /**
   * Update bottom navigation bar visibility based on floating bottom bar state
   */
  fun updateBottomBarVisibility(shouldShow: Boolean) {
    // Hide bottom navigation when floating bottom bar is visible
    _shouldHideNavigationBar.value = !shouldShow
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember {
      mutableIntStateOf(persistentSelectedTab)
    }
    
    var previousTab by remember {
      mutableIntStateOf(persistentPreviousTab)
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val appearancePreferences = koinInject<AppearancePreferences>()
    val miniPlayerStateManager = koinInject<MiniPlayerStateManager>()
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val isShortsEnabled by browserPreferences.enableShorts.collectAsState()
    val enableTabYou by browserPreferences.enableTabYou.collectAsState()
    val enableTabRecents by browserPreferences.enableTabRecents.collectAsState()
    val enableTabPlaylists by browserPreferences.enableTabPlaylists.collectAsState()
    val enableTabNetwork by browserPreferences.enableTabNetwork.collectAsState()
    val bottomNavOrder by browserPreferences.bottomNavOrder.collectAsState()
    val motionPreset by appearancePreferences.navigationMotionStyle.collectAsState()
    val customProfileImagePath by appearancePreferences.customProfileImagePath.collectAsState()

    val customAvatarBitmap = remember(customProfileImagePath) {
      if (customProfileImagePath.isNotBlank()) {
        val file = java.io.File(customProfileImagePath)
        if (file.exists()) {
          try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
          } catch (e: Exception) {
            null
          }
        } else null
      } else null
    }

    val homeLabel = stringResource(R.string.home)
    val shortsLabel = stringResource(R.string.shorts)
    val youLabel = stringResource(R.string.you)
    val recentsLabel = stringResource(R.string.recents)
    val playlistsLabel = stringResource(R.string.playlists)
    val networkLabel = stringResource(R.string.network)

    val visibleTabs = remember(
      isShortsEnabled, enableTabYou, enableTabRecents, enableTabPlaylists, enableTabNetwork,
      bottomNavOrder,
      homeLabel, shortsLabel, youLabel, recentsLabel, playlistsLabel, networkLabel
    ) {
      val rawTabs = buildList {
        add(
          VisibleTab("home", homeLabel, Icons.Filled.Home, RexIcons.Home) {
            FolderListScreen.Content()
          }
        )
        if (isShortsEnabled) {
          add(
            VisibleTab("shorts", shortsLabel, Icons.Outlined.VideoLibrary, RexIcons.VideoLibrary) {
              ShortsScreen().Content()
            }
          )
        }
        if (!enableTabYou) {
          if (enableTabRecents) {
            add(
              VisibleTab("recents", recentsLabel, Icons.Filled.History, RexIcons.History) {
                RecentlyPlayedScreen.Content()
              }
            )
          }
          if (enableTabPlaylists) {
            add(
              VisibleTab("playlists", playlistsLabel, Icons.AutoMirrored.Filled.PlaylistPlay, RexIcons.PlaylistPlay) {
                PlaylistScreen.Content()
              }
            )
          }
        }
        if (enableTabNetwork) {
          add(
            VisibleTab("network", networkLabel, Icons.Filled.Language, RexIcons.Language) {
              NetworkStreamingScreen.Content()
            }
          )
        }
        if (enableTabYou) {
          add(
            VisibleTab("you", youLabel, Icons.Filled.AccountCircle, RexIcons.Person) {
              YouScreen.Content()
            }
          )
        }
      }

      val orderList = browserPreferences.getBottomNavOrder()
      rawTabs.sortedBy { tab ->
        val idx = orderList.indexOf(tab.id)
        if (idx == -1) Int.MAX_VALUE else idx
      }
    }

    // Ensure selectedTab is always clamped within active tabs range
    LaunchedEffect(visibleTabs) {
      if (selectedTab >= visibleTabs.size) {
        selectedTab = 0
      }
    }

    // Intercept back button when on Shorts tab to return to Home or previous tab
    val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
    val homeIdx = visibleTabs.indexOfFirst { it.id == "home" }
    androidx.activity.compose.BackHandler(enabled = shortsIdx != -1 && selectedTab == shortsIdx) {
      val targetTab = when {
        previousTab != shortsIdx && previousTab in visibleTabs.indices -> previousTab
        homeIdx != -1 -> homeIdx
        else -> visibleTabs.indices.firstOrNull { it != shortsIdx } ?: 0
      }
      selectedTab = targetTab
    }


    // Shared state (across the app) collected reactively via StateFlow
    val isInSelectionMode by _isInSelectionModeShared.collectAsState()
    val hideNavigationBar by _shouldHideNavigationBar.collectAsState()
    val rawSelectionManager by _sharedVideoSelectionManager.collectAsState()
    val videoSelectionManager = rawSelectionManager as? SelectionManager<*, *>

    // Publish the navigation bar's real height to the activity, because the mini player sheet is
    // composed *beside* this screen (in MainActivity's window, so it can cover pushed screens) and
    // cannot see this tree's composition locals. This mirrors the `isNavBarVisible` used by the
    // Scaffold below: no bar on Shorts, in selection mode, or when there is only one tab.
    //
    // Zero when there is no bar, so the sheet docks on the window edge rather than leaving a 64 dp
    // gap above a navigation bar that is not being painted.
    val navBarVisibleForSheet =
      !hideNavigationBar && !(isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx) &&
        visibleTabs.size > 1
    val navBarInsetForSheet =
      ShortNavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(navBarVisibleForSheet, navBarInsetForSheet) {
      _miniPlayerBottomChromeHeight.value =
        if (navBarVisibleForSheet) 64.dp + navBarInsetForSheet else navBarInsetForSheet
    }
    // While this screen is pushed over (or not composed), it publishes nothing; the activity then
    // falls back to treating the bar as absent rather than guessing.
    DisposableEffect(Unit) {
      onDispose { _miniPlayerBottomChromeHeight.value = null }
    }

    // Update persistent state whenever tab changes
    LaunchedEffect(selectedTab) {
      if (selectedTab != persistentSelectedTab) {
        previousTab = persistentSelectedTab
        persistentPreviousTab = previousTab
      }
      android.util.Log.d("MainScreen", "selectedTab changed to: $selectedTab (was ${persistentSelectedTab}), previousTab is $previousTab")
      persistentSelectedTab = selectedTab
    }

    // Handle tab requests from other screens
    LaunchedEffect(Unit) {
      tabRequest.collect { tab ->
        selectedTab = tab
      }
    }

    // Community Hub auto-popup: Phase 1 (initial 1 min test threshold) / Phase 2 (15 days after "Already joined")
    val isCommunityPromptPermanentlyDismissed by appearancePreferences.communityPromptDismissedPermanently.collectAsState()
    val firstOpenTimestamp by appearancePreferences.communityFirstAppOpenTimestamp.collectAsState()
    val alreadyJoinedTimestamp by appearancePreferences.communityAlreadyJoinedTimestamp.collectAsState()
    var showCommunityAutoPopup by remember { mutableStateOf(false) }
    var isFollowupPrompt by remember { mutableStateOf(false) }
    var hasDismissedThisSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      if (firstOpenTimestamp == 0L) {
        appearancePreferences.communityFirstAppOpenTimestamp.set(System.currentTimeMillis())
      }
    }

    val isHomeTabActive = selectedTab in visibleTabs.indices && visibleTabs[selectedTab].id == "home"

    LaunchedEffect(
      isHomeTabActive,
      isCommunityPromptPermanentlyDismissed,
      hasDismissedThisSession,
      firstOpenTimestamp,
      alreadyJoinedTimestamp
    ) {
      if (isHomeTabActive && !isCommunityPromptPermanentlyDismissed && !hasDismissedThisSession) {
        val (remainingDelay, isFollowup) = if (alreadyJoinedTimestamp > 0L) {
          // Phase 2: User clicked "Already joined" in the past -> wait 10 days before follow-up
          val followupCooldownMs = 10L * 24 * 60 * 60 * 1000L // 10 days
          val elapsed = System.currentTimeMillis() - alreadyJoinedTimestamp
          val remaining = (followupCooldownMs - elapsed).coerceAtLeast(0L)
          remaining to true
        } else {
          // Phase 1: User hasn't clicked "Already joined" yet -> wait initial threshold (20 minutes)
          val initialDelayMs = 20 * 60 * 1000L // 20 minutes
          val currentFirstOpen = if (firstOpenTimestamp == 0L) System.currentTimeMillis() else firstOpenTimestamp
          val elapsed = System.currentTimeMillis() - currentFirstOpen
          val remaining = (initialDelayMs - elapsed).coerceAtLeast(0L)
          remaining to false
        }

        if (remainingDelay > 0L) {
          kotlinx.coroutines.delay(remainingDelay)
        }

        if (isHomeTabActive && !isCommunityPromptPermanentlyDismissed && !hasDismissedThisSession) {
          isFollowupPrompt = isFollowup
          showCommunityAutoPopup = true
        }
      }
    }

    // Scaffold with bottom navigation bar
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      // The screen tone behind the content surface. The cards are painted in
      // `surface` and the gaps between them only read as gaps because this
      // backdrop is the darker `surfaceContainer`; leaving the Scaffold on the
      // default `background` made the filled sheet sit on an almost identical
      // tone.
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      bottomBar = {
        // Animated bottom navigation bar with slide animations
        // Also hide if Shorts tab is active (index 1 when enabled, index -1 when disabled)
        val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
        val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
        
        AnimatedVisibility(
            visible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1,
            // Dock expand: the navigation bar grows out of the bottom edge it is anchored to.
            enter = RexMotion.dockEnter(LocalContext.current),
            exit = RexMotion.dockExit(LocalContext.current)
          ) {
            val navBarBottomInset = ShortNavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
            // The expressive navigation bar animates a pill behind the selected item and swaps the
            // icon for its filled variant, instead of cross-fading a fixed rounded rectangle.
            ShortNavigationBar(
              modifier = Modifier.height(64.dp + navBarBottomInset),
              containerColor = if (isShortsTabActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
              contentColor = if (isShortsTabActive) Color.White else MaterialTheme.colorScheme.onSurface,
            ) {
              val itemColors = if (isShortsTabActive) {
                ShortNavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  unselectedIconColor = Color.White.copy(alpha = 0.7f),
                  unselectedTextColor = Color.White.copy(alpha = 0.7f),
                  selectedIndicatorColor = Color.White.copy(alpha = 0.2f),
                )
              } else {
                ShortNavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.onSurface,
                  selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              visibleTabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == index
                val isYouTabWithCustomAvatar = tab.id == "you" && customAvatarBitmap != null
                val interactionSource = remember { MutableInteractionSource() }
                val pressScale by RexPress.pressScale(interactionSource)

                ShortNavigationBarItem(
                  modifier = Modifier
                    .offset(y = 2.dp)
                    .graphicsLayer {
                      scaleX = pressScale
                      scaleY = pressScale
                    },
                  interactionSource = interactionSource,
                  icon = {
                    if (isYouTabWithCustomAvatar) {
                      Image(
                        bitmap = customAvatarBitmap!!,
                        contentDescription = tab.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(24.dp)
                          .clip(CircleShape)
                          .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                          ),
                      )
                    } else if (tab.rexIcon != null) {
                      RexIcon(
                        icon = tab.rexIcon,
                        isFilled = isSelected,
                        modifier = Modifier.size(24.dp),
                      )
                    } else {
                      Icon(tab.icon, contentDescription = tab.label)
                    }
                  },
                  label = { Text(tab.label) },
                  selected = isSelected,
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isSelected) {
                      _scrollToTopRequest.tryEmit(tab.id)
                    } else {
                      selectedTab = index
                    }
                  },
                  colors = itemColors,
                )
              }
            }
          }
        }
    ) { _ ->
      Box(modifier = Modifier.fillMaxSize()) {
        val fabBottomPadding = 64.dp

        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            val forward = targetState > initialState
            val (enter, exit) = RexMotion.screenTransition(
              context = context,
              forward = forward,
              style = motionPreset,
            )
            (enter togetherWith exit).apply {
              targetContentZIndex = if (forward) 1f else -1f
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
          val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
          val isNavBarVisible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1
          val navBarBottomInset = ShortNavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
          // Docks the content sheet flush against the top edge of the navigation bar (64.dp bar
          // height + device navigation bar inset). If the bottom bar is hidden, the content sheet
          // still reserves the device inset so its rounded corners clear the 3-button bar.
          val navBarHeight = if (isNavBarVisible) 64.dp + navBarBottomInset else navBarBottomInset
          
          CompositionLocalProvider(
            LocalNavigationBarHeight provides navBarHeight,
            LocalBottomBarPresent provides isNavBarVisible,
            LocalMiniPlayerActive provides miniPlayerState.isPlaybackActive,
          ) {
            if (targetTab in visibleTabs.indices) {
              visibleTabs[targetTab].content()
            } else {
              FolderListScreen.Content()
            }
          }
        }
      }
    }

    if (showCommunityAutoPopup && !isCommunityPromptPermanentlyDismissed) {
      CommunityLinksDialog(
        isFollowupPrompt = isFollowupPrompt,
        onDismissRequest = {
          hasDismissedThisSession = true
          showCommunityAutoPopup = false
        }
      )
    }
  }
}

// CompositionLocal for navigation bar height
val LocalNavigationBarHeight = compositionLocalOf { 0.dp }

// CompositionLocal for bottom navigation bar visibility
val LocalBottomBarPresent = compositionLocalOf { false }

// CompositionLocal for whether mini player playback is currently active
val LocalMiniPlayerActive = compositionLocalOf { false }

private data class VisibleTab(
  val id: String,
  val label: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val rexIcon: xyz.mpv.rex.ui.icons.MaterialSymbolIcon? = null,
  val content: @Composable () -> Unit
)
