package xyz.mpv.rex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
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
    val density = LocalDensity.current
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
          VisibleTab("home", homeLabel, Icons.Filled.Home) {
            FolderListScreen.Content()
          }
        )
        if (isShortsEnabled) {
          add(
            VisibleTab("shorts", shortsLabel, Icons.Outlined.VideoLibrary) {
              ShortsScreen().Content()
            }
          )
        }
        if (!enableTabYou) {
          if (enableTabRecents) {
            add(
              VisibleTab("recents", recentsLabel, Icons.Filled.History) {
                RecentlyPlayedScreen.Content()
              }
            )
          }
          if (enableTabPlaylists) {
            add(
              VisibleTab("playlists", playlistsLabel, Icons.AutoMirrored.Filled.PlaylistPlay) {
                PlaylistScreen.Content()
              }
            )
          }
        }
        if (enableTabNetwork) {
          add(
            VisibleTab("network", networkLabel, Icons.Filled.Language) {
              NetworkStreamingScreen.Content()
            }
          )
        }
        if (enableTabYou) {
          add(
            VisibleTab("you", youLabel, Icons.Filled.AccountCircle) {
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
      bottomBar = {
        // Animated bottom navigation bar with slide animations
        // Also hide if Shorts tab is active (index 1 when enabled, index -1 when disabled)
        val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
        val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
        
        AnimatedVisibility(
            visible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1,
            enter = slideInVertically(
              animationSpec = tween(durationMillis = 300),
              initialOffsetY = { fullHeight -> fullHeight }
            ),
            exit = slideOutVertically(
              animationSpec = tween(durationMillis = 300),
              targetOffsetY = { fullHeight -> fullHeight }
            )
          ) {
            val navBarBottomInset = NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
            NavigationBar(
              modifier = Modifier.height(64.dp + navBarBottomInset),
              containerColor = if (isShortsTabActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
              contentColor = if (isShortsTabActive) Color.White else MaterialTheme.colorScheme.onSurface,
            ) {
              val itemColors = if (isShortsTabActive) {
                NavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  unselectedIconColor = Color.White.copy(alpha = 0.7f),
                  unselectedTextColor = Color.White.copy(alpha = 0.7f),
                  indicatorColor = Color.White.copy(alpha = 0.2f),
                )
              } else {
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.onSurface,
                  indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              visibleTabs.forEachIndexed { index, tab ->
                val isYouTabWithCustomAvatar = tab.id == "you" && customAvatarBitmap != null
                NavigationBarItem(
                  modifier = Modifier.offset(y = 2.dp),
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
                            width = if (selectedTab == index) 1.5.dp else 1.dp,
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                          ),
                      )
                    } else {
                      Icon(tab.icon, contentDescription = tab.label)
                    }
                  },
                  label = { Text(tab.label) },
                  selected = selectedTab == index,
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (selectedTab == index) {
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
            val slideDistance = with(density) { 48.dp.roundToPx() }
            val animationDuration = 250
            
            if (targetState > initialState) {
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { -slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            } else {
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { -slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
          val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
          val isNavBarVisible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1
          
          val navBarHeight = if (isNavBarVisible) 64.dp else 0.dp
          val miniPlayerHeight = if (miniPlayerState.isPlaybackActive) MiniPlayerDefaults.CompactHeight else 0.dp
          val totalBottomPadding = navBarHeight + miniPlayerHeight
          
          CompositionLocalProvider(
            LocalNavigationBarHeight provides totalBottomPadding
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

private data class VisibleTab(
  val id: String,
  val label: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val content: @Composable () -> Unit
)
