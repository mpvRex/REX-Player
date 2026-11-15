package xyz.mpv.rex

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import xyz.mpv.rex.ui.motion.RexMotion
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.MotionStyle
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.utils.update.UpdateDialog
import xyz.mpv.rex.utils.update.UpdateManager
import xyz.mpv.rex.utils.update.UpdateViewModel
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.MpvexTheme
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.utils.permission.PermissionUtils
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayer
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.ui.browser.LocalBottomBarPresent
import xyz.mpv.rex.ui.browser.LocalMiniPlayerActive
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.welcome.WelcomeScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import androidx.compose.runtime.staticCompositionLocalOf

val LocalUpdateViewModel = staticCompositionLocalOf<UpdateViewModel?> { null }

/**
 * `contentKey` of the top entry in a `NavDisplay` scene, i.e. the id of the card the user
 * tapped. Returns null when the entry is not a container-transform source (its contentKey
 * is the `"root"` placeholder) or when the scene has no entries.
 */
/**
 * The screen a scene represents.
 *
 * A scene's key is the screen on the backstack. Anything that is not a [Screen] cannot happen
 * today (every NavKey here is one), but a transition must not throw, so it falls back to a
 * screen that declares no motion preference.
 */
private fun screenOf(scene: androidx.navigation3.scene.Scene<*>): Screen =
  scene.key as? Screen ?: FallbackScreen

/** Stand-in used only if a scene's key is unexpectedly not a [Screen]. */
private object FallbackScreen : Screen {
  @androidx.compose.runtime.Composable
  override fun Content() = Unit
}


/**
 * Main entry point for the application
 */
class MainActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()
  private val networkRepository by inject<NetworkRepository>()
  private val miniPlayerStateManager by inject<MiniPlayerStateManager>()
  
  // Create a coroutine scope tied to the activity lifecycle
  private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // Register the ActivityResultLauncher at class level
  private val mediaAccessLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    PermissionUtils.handleMediaAccessResult(result.resultCode)
  }

  override fun attachBaseContext(newBase: android.content.Context) {
    super.attachBaseContext(xyz.mpv.rex.utils.locale.LocaleHelper.wrapContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    
    PermissionUtils.setMediaAccessLauncher(mediaAccessLauncher)

    // Register proxy lifecycle observer for network streaming
    lifecycle.addObserver(xyz.mpv.rex.ui.browser.networkstreaming.proxy.ProxyLifecycleObserver())

    setContent {
      // Set up theme and edge-to-edge display
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)
      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      // Auto-connect to saved network connections
      LaunchedEffect(Unit) {
        autoConnectToNetworks()
      }

      MpvexTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
          Navigator()
        }
      }
    }
  }

  override fun onDestroy() {
    try {
      super.onDestroy()
    } catch (e: Exception) {
      Log.e("MainActivity", "Error during onDestroy", e)
    }
  }

  /**
   * Auto-connect to network connections that are marked for auto-connection
   */
  private suspend fun autoConnectToNetworks() {
    // Delay auto-connect to let UI settle first
    kotlinx.coroutines.delay(500)
    
    // Use coroutineScope for properly structured concurrency
    withContext(Dispatchers.IO) {
      try {
        val autoConnectConnections = networkRepository.getAutoConnectConnections()
        autoConnectConnections.forEach { connection ->
          withContext(Dispatchers.Main) {
            Log.d("MainActivity", "Auto-connecting to: ${connection.name}")
          }
          networkRepository.connect(connection)
            .onSuccess {
              withContext(Dispatchers.Main) {
                Log.d("MainActivity", "Auto-connected successfully: ${connection.name}")
              }
            }
            .onFailure { e ->
              withContext(Dispatchers.Main) {
                Log.e("MainActivity", "Auto-connect failed for ${connection.name}: ${e.message}")
              }
            }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Log.e("MainActivity", "Error during auto-connect", e)
        }
      }
    }
  }

  /**
   * Navigator that handles screen transitions and provides shared states
   */
  @Composable
  fun Navigator() {
    val context = LocalContext.current
    val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
    val initialScreen = remember {
      if (hasCompletedOnboarding) {
        MainScreen
      } else {
        WelcomeScreen
      }
    }
    val backstack = rememberNavBackStack(initialScreen)

    @Suppress("UNCHECKED_CAST")
    val typedBackstack = backstack as NavBackStack<Screen>

    val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")

    // Conditionally initialize update feature based on build config
    val updateViewModel: UpdateViewModel? = if (BuildConfig.ENABLE_UPDATE_FEATURE) {
      viewModel(context as ComponentActivity)
    } else {
      null
    }
    val updateState by (updateViewModel?.updateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
    val motionPreset by appearancePreferences.navigationMotionStyle.collectAsState()
    val hideNavigationBar by MainScreen.shouldHideNavigationBar.collectAsState()
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val currentRoute = typedBackstack.lastOrNull()
    val isMainScreen = currentRoute == MainScreen
    val isWelcomeScreen = currentRoute == WelcomeScreen

    // The mini player is hosted here, in the window, so its expanded page can cover pushed
    // screens too. It is docked on the navigation bar and does its own layout, so nothing in this
    // file offsets for it any more: `MainScreen` owns the content inset via
    // `LocalNavigationBarHeight`, and the sheet owns its own geometry.
    val navBarHeight = if (isMainScreen && !hideNavigationBar) 64.dp else 0.dp
    val totalNavigationBarHeight = navBarHeight

    // The floating mini player rests above the bottom chrome: the navigation bar + device inset
    // when on MainScreen, or the system navigation bar inset when pushed over.
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val publishedChrome by MainScreen.miniPlayerBottomChromeHeight.collectAsState()
    val miniPlayerBottomChrome = (if (isMainScreen) publishedChrome else null) ?: systemBottomInset

    // True while the mini player's page is the destination. Tracked here only so the fallback branch
    // below can double-check; the page itself owns Back (see the comment on the handler).
    var miniPlayerExpanded by remember { mutableStateOf(false) }
    val collapseMiniPlayer = remember { mutableStateOf<(() -> Unit)?>(null) }

    // System back: pop one entry, or send the task to the background at the root. Popping the root
    // entry is never allowed, because NavDisplay throws "backstack cannot be empty".
    //
    // The mini player's expanded page is *not* handled here. It registers its own `BackHandler` in
    // `MiniPlayer`, which composes after `NavDisplay` and therefore sits in front of both NavDisplay's
    // handler and every screen-level one (registration order decides: `ArrayDeque.addFirst` +
    // first-enabled-wins). This handler is registered before `NavDisplay`, so it is last in line and
    // only runs once every other handler has declined.
    BackHandler(enabled = typedBackstack.size > 1 || isMainScreen || isWelcomeScreen) {
      if (miniPlayerExpanded && collapseMiniPlayer.value != null) {
        // Defensive only: reachable if the sheet's own handler was somehow unregistered mid-press.
        collapseMiniPlayer.value?.invoke()
        return@BackHandler
      }
      if (!typedBackstack.navigateBack()) {
        (context as? Activity)?.moveTaskToBack(true)
      }
    }

    // Provide shared states to all screens
    CompositionLocalProvider(
      LocalBackStack provides typedBackstack,
      LocalUpdateViewModel provides updateViewModel,
      LocalNavigationBarHeight provides totalNavigationBarHeight,
      LocalBottomBarPresent provides (isMainScreen && !hideNavigationBar),
      LocalMiniPlayerActive provides miniPlayerState.isPlaybackActive,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = typedBackstack,
          onBack = { typedBackstack.navigateBack() },
          entryProvider = { route ->
            NavEntry(route) { route.Content() }
          },
          // The screen further up the stack owns the edge, so push and pop always mirror each
          // other and each edge has exactly one style. Pop reads the screen being left; push
          // reads the screen arriving.
          popTransitionSpec = {
            val screen = screenOf(initialState)
            val style = if (screen.motion == MotionStyle.Default) motionPreset else screen.motion
            RexMotion.screenTransition(context, forward = false, style = style)
              .let { (enter, exit) -> enter togetherWith exit }
          },
          transitionSpec = {
            val screen = screenOf(targetState)
            val style = if (screen.motion == MotionStyle.Default) motionPreset else screen.motion
            RexMotion.screenTransition(context, forward = true, style = style)
              .let { (enter, exit) -> enter togetherWith exit }
          },
          predictivePopTransitionSpec = { _ ->
            // Navigation 3 drives the gesture itself; this only decides the shape the departing
            // screen leaves with, so the preview and the committed pop agree.
            val screen = screenOf(initialState)
            val style = if (screen.motion == MotionStyle.Default) motionPreset else screen.motion
            RexMotion.predictiveBackTransition(context, style = style)
              .let { (enter, exit) -> enter togetherWith exit }
          },
        )

        MiniPlayer(
          stateManager = miniPlayerStateManager,
          bottomChromeHeight = miniPlayerBottomChrome,
          expandedChanged = { miniPlayerExpanded = it },
          collapseRequest = { req -> collapseMiniPlayer.value = req },
        )
      }

      // Display Update Dialog when appropriate (only if update feature is enabled)
      if (BuildConfig.ENABLE_UPDATE_FEATURE && updateViewModel != null) {
        when (updateState) {
          is UpdateViewModel.UpdateState.Available -> {
            val release = (updateState as UpdateViewModel.UpdateState.Available).release
            UpdateDialog(
              release = release,
              actionLabel = "Download",
              currentVersion = currentVersion,
              onDismiss = { updateViewModel.dismiss() },
              onAction = {
                // Hand off to the releases page; the app never downloads or
                // installs APKs itself. Every build tracks the same release feed.
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW,
                    (release.htmlUrl ?: "https://github.com/mpvRex/REX-Player/releases/latest").toUri()
                  )
                )
              },
              onIgnore = { updateViewModel.ignoreVersion(release.tagName.removePrefix("v")) }
            )
          }
          else -> {}
        }
      }
    }
  }
}
