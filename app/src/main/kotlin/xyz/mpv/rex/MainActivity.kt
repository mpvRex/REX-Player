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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.repository.NetworkRepository
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import xyz.mpv.rex.utils.update.UpdateDialog
import xyz.mpv.rex.utils.update.UpdateViewModel
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.MpvexTheme
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.permission.PermissionUtils
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayer
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerDefaults
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
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

  private val openSettingsRequested = MutableStateFlow(false)

  private fun isSettingsIntent(intent: Intent?): Boolean {
    if (intent == null) return false
    return intent.getStringExtra("shortcut") == "settings" ||
      intent.action == "xyz.mpv.rex.action.SETTINGS" ||
      intent.action == Intent.ACTION_APPLICATION_PREFERENCES ||
      intent.hasExtra("open_settings") ||
      (intent.data?.scheme == "mpvrex" && intent.data?.host == "settings")
  }

  private fun setupDynamicShortcuts() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) return
    try {
      val shortcut = ShortcutInfoCompat.Builder(this, "settings")
        .setShortLabel(getString(R.string.settings))
        .setLongLabel(getString(R.string.settings))
        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_settings))
        .setIntent(
          Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut", "settings")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
        )
        .build()
      ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
    } catch (e: Exception) {
      Log.w("MainActivity", "Failed to setup dynamic shortcuts", e)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (isSettingsIntent(intent)) {
      openSettingsRequested.value = true
    }
  }

  override fun attachBaseContext(newBase: android.content.Context) {
    super.attachBaseContext(xyz.mpv.rex.utils.locale.LocaleHelper.wrapContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    
    if (isSettingsIntent(intent)) {
      openSettingsRequested.value = true
    }
    setupDynamicShortcuts()

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
        Surface {
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
    val initialOpenSettings = remember { isSettingsIntent(intent) && hasCompletedOnboarding }
    val initialScreen = remember {
      if (hasCompletedOnboarding) {
        MainScreen
      } else {
        WelcomeScreen
      }
    }
    val backstack = if (hasCompletedOnboarding && initialOpenSettings) {
      rememberNavBackStack(MainScreen, PreferencesScreen)
    } else {
      rememberNavBackStack(initialScreen)
    }

    @Suppress("UNCHECKED_CAST")
    val typedBackstack = backstack as NavBackStack<Screen>

    val shouldOpenSettings by openSettingsRequested.collectAsState()
    LaunchedEffect(shouldOpenSettings) {
      if (shouldOpenSettings) {
        if (hasCompletedOnboarding && typedBackstack.lastOrNull() != PreferencesScreen) {
          typedBackstack.add(PreferencesScreen)
        }
        openSettingsRequested.value = false
      }
    }

    val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")

    // Conditionally initialize update feature based on build config
    val updateViewModel: UpdateViewModel? = if (BuildConfig.ENABLE_UPDATE_FEATURE) {
      viewModel(context as ComponentActivity)
    } else {
      null
    }
    val updateState by (updateViewModel?.updateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
    val isDownloading by (updateViewModel?.isDownloading ?: MutableStateFlow(false)).collectAsState()
    val downloadProgress by (updateViewModel?.downloadProgress ?: MutableStateFlow(0f)).collectAsState()
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val hideNavigationBar by MainScreen.shouldHideNavigationBar.collectAsState()
    val currentRoute = typedBackstack.lastOrNull()
    val isMainScreen = currentRoute == MainScreen
    val isWelcomeScreen = currentRoute == WelcomeScreen
    
    val targetBottomPadding = if (isMainScreen && !hideNavigationBar) {
      if (miniPlayerState.isExpanded) 8.dp else 88.dp
    } else 8.dp
    val animatedBottomPadding by androidx.compose.animation.core.animateDpAsState(
      targetValue = targetBottomPadding,
      animationSpec = tween(220),
      label = "miniPlayerBottomPadding"
    )

    val targetMiniPlayerHeight = if (miniPlayerState.isPlaybackActive) MiniPlayerDefaults.CompactHeight else 0.dp
    val miniPlayerHeight by androidx.compose.animation.core.animateDpAsState(
      targetValue = targetMiniPlayerHeight,
      animationSpec = tween(220),
      label = "miniPlayerHeight"
    )
    val navBarHeight = if (isMainScreen && !hideNavigationBar) 80.dp else 0.dp
    val totalNavigationBarHeight = navBarHeight + miniPlayerHeight

    BackHandler(enabled = isMainScreen || isWelcomeScreen) {
      (context as? Activity)?.moveTaskToBack(true)
    }

    // Provide shared states to all screens
    CompositionLocalProvider(
      LocalBackStack provides typedBackstack,
      LocalUpdateViewModel provides updateViewModel,
      LocalNavigationBarHeight provides totalNavigationBarHeight
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = typedBackstack,
          onBack = { typedBackstack.removeLastOrNull() },
          entryProvider = { route -> NavEntry(route) { route.Content() } },
          popTransitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                slideIn(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  slideOut(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
            )
          },
          transitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                slideIn(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  slideOut(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
            )
          },
          predictivePopTransitionSpec = {
            (
              fadeIn(animationSpec = tween(220)) +
                scaleIn(
                  animationSpec = tween(220, delayMillis = 30),
                  initialScale = .9f,
                  TransformOrigin(-1f, .5f),
                )
            ) togetherWith (
                fadeOut(animationSpec = tween(220)) +
                  scaleOut(
                    animationSpec = tween(220, delayMillis = 30),
                    targetScale = .9f,
                    TransformOrigin(-1f, .5f),
                  )
            )
          },
        )

        MiniPlayer(
          stateManager = miniPlayerStateManager,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = animatedBottomPadding)
        )
      }

      // Display Update Dialog when appropriate (only if update feature is enabled)
      if (BuildConfig.ENABLE_UPDATE_FEATURE && updateViewModel != null) {
        when (updateState) {
          is UpdateViewModel.UpdateState.Available -> {
            val release = (updateState as UpdateViewModel.UpdateState.Available).release
            UpdateDialog(
              release = release,
              isDownloading = isDownloading,
              progress = downloadProgress,
              actionLabel = if (isDownloading) "Downloading..." else "Download",
              currentVersion = currentVersion,
              onDismiss = { updateViewModel.dismiss() },
              onAction = { 
                // Redirect to GitHub releases page as requested by user
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW, 
                    (release.htmlUrl ?: "https://github.com/mpvRex/REX-Player/releases/latest").toUri()
                  )
                )
                // updateViewModel.downloadUpdate(release) // Kept in code but disabled for now
              },
              onIgnore = { updateViewModel.ignoreVersion(release.tagName.removePrefix("v")) }
            )
          }
          is UpdateViewModel.UpdateState.ReadyToInstall -> {
            val release = (updateState as UpdateViewModel.UpdateState.ReadyToInstall).release
            UpdateDialog(
              release = release,
              isDownloading = isDownloading,
              progress = downloadProgress,
              actionLabel = "Install",
              currentVersion = currentVersion,
              onDismiss = { updateViewModel.dismiss() },
              onAction = { 
                // Redirect to GitHub releases page as requested by user
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW, 
                    (release.htmlUrl ?: "https://github.com/mpvRex/REX-Player/releases/latest").toUri()
                  )
                )
                // updateViewModel.installUpdate(release) // Kept in code but disabled for now
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
