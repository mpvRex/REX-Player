# Navigation Bar Insets and Edge-to-Edge Architecture

## Overview

This document describes the edge-to-edge window inset architecture, system navigation bar color adaptation, and content sheet geometry across `mpvRex`. It serves as a permanent reference for maintaining navigation bar insets, preventing regressions on both 3-button navigation and gesture navigation, and explaining the interactions between `MainActivity`, `MainScreen`, `ContentSurfaceContainer`, and pushed screens.

---

## Architecture Principles

### 1. Edge-to-Edge Window Setup
The app draws content edge-to-edge behind the system status bar and system navigation bar across all Android versions (API 26+).

```
+-------------------------------------------------------+
| Status Bar (Transparent, adaptive icon colors)        |
+-------------------------------------------------------+
|                                                       |
| Screen Content / ContentSurfaceContainer Sheet        |
|                                                       |
+-------------------------------------------------------+
| App Bottom Bar (ShortNavigationBar, height = 64.dp    |
|                 + navBarBottomInset)                  |
+-------------------------------------------------------+
| System 3-Button / Gesture Bar (Fully transparent,     |
| shows ShortNavigationBar or screen containerColor)    |
+-------------------------------------------------------+
```

To ensure the system navigation bar is fully transparent and adapts to whatever color is behind it without a forced system scrim:
- Both `statusBarStyle` **and** `navigationBarStyle` must be passed explicitly to `enableEdgeToEdge`.
- Scrims are set to `Color.Transparent.toArgb()` for both light and dark modes.
- On Android 10+ (API 29+), `window.isNavigationBarContrastEnforced = false` must be set. Otherwise, the Android window manager forces a translucent/white contrast scrim over the 3-button bar in light mode.
- Icon contrast (`WindowInsetsControllerCompat.isAppearanceLightNavigationBars`) is driven by whether the current theme is light (dark icons) or dark (light icons).

### 2. CompositionLocal: `LocalNavigationBarHeight`
`LocalNavigationBarHeight` defines the bottom reservation required for the screen's content to clear the bottom chrome:
- **On `MainScreen` (Tabs present)**: The bottom chrome is the app's `ShortNavigationBar`, which has a height of `64.dp + navBarBottomInset`. Therefore, `MainScreen` publishes `64.dp + navBarBottomInset` through `LocalNavigationBarHeight`.
- **On Pushed Screens (`VideoListScreen`, `PreferencesScreen`, etc.)**: The app bottom bar is absent (`isMainScreen = false`). `LocalNavigationBarHeight` remains `0.dp`, allowing `ContentSurfaceContainer` to apply its standard `14.dp` bottom gap without stacking unwanted extra padding above the gesture bar.

### 3. Content Surface Geometry (`ContentSurfaceContainer`)
The browser sheets use `ContentSurfaceContainer`:
- Corner radius: `RoundedCornerShape(24.dp)` on all four corners.
- Horizontal margins: `8.dp` start and end.
- Bottom padding formula:
  $$\text{bottomPadding} = \text{navigationBarHeight} + \text{bottomGap}$$
  where:
  $$\text{bottomGap} = \begin{cases} 0\text{ dp} & \text{if } \text{hasBottomBar is true (MainScreen)} \\ 14\text{ dp} & \text{if } \text{hasBottomBar is false (Pushed screens)} \end{cases}$$

- **On `MainScreen`**: `navigationBarHeight = 64.dp + navBarBottomInset`, `bottomGap = 0.dp`. The sheet ends at `screen_height - (64.dp + navBarBottomInset)`. `ShortNavigationBar` starts at `screen_height - (64.dp + navBarBottomInset)`. They meet flush, and the sheet's 24.dp rounded bottom-left and bottom-right corners curve inward right above the bar.
- **On Pushed Screens in Gesture Navigation (`VideoListScreen`)**: `navigationBarHeight = 0.dp`, `bottomGap = 14.dp`. The sheet ends 14.dp from the bottom of the screen, clearing the gesture handle with its 24.dp rounded corners intact.

---

## Bugs Solved & Root Causes

### 1. 3-Button Navigation Bar Painted White Strip (`#FDFEFE`)
- **Symptoms**: On devices with 3-button navigation, the system navigation bar at the bottom displayed a flat near-white bar (`#FDFEFE`) that did not match the `surfaceContainer` backdrop (`#EEF0F9`).
- **Root Cause A**: `enableEdgeToEdge()` in `MainActivity.kt` was called with only one positional argument:
  ```kotlin
  enableEdgeToEdge(
    SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { isDarkMode }
  )
  ```
  The first parameter of `enableEdgeToEdge` is `statusBarStyle`. Because named arguments were not used, `navigationBarStyle` defaulted to `SystemBarStyle.auto(DefaultLightScrim, DefaultDarkScrim)` where `DefaultLightScrim` is Android's default white scrim (`0xe6ffffff`).
- **Root Cause B**: On Android 10+ (API 29+), `SystemBarStyle.auto` has `nightMode = 0`. In `EdgeToEdgeApi29`, whenever `navigationBarStyle.nightMode == 0`, Android executes `window.setNavigationBarContrastEnforced(true)`, causing the window manager to draw a contrast scrim over the 3-button bar.
- **Solution**: Explicitly provide both `statusBarStyle` and `navigationBarStyle` using `SystemBarStyle.light()` / `SystemBarStyle.dark()`, and explicitly disable `window.isNavigationBarContrastEnforced = false` on API 29+.

### 2. File Browser Bottom Corners Hidden Behind Bottom Navigation
- **Symptoms**: On Home (`MainScreen`), the bottom-left and bottom-right rounded corners of the file browser card were chopped off and hidden behind the bottom navigation bar.
- **Root Cause**: An earlier refactor changed `MainScreen.kt` to:
  ```kotlin
  val navBarHeight = if (isNavBarVisible) 64.dp else 0.dp
  CompositionLocalProvider(LocalNavigationBarHeight provides navBarHeight)
  ```
  `MainScreen` overrode `LocalNavigationBarHeight` with only `64.dp`, leaving out `navBarBottomInset` (48.dp on 3-button nav, 24.dp on gesture nav).
  - `ShortNavigationBar` occupied height `64.dp + navBarBottomInset` (112.dp).
  - The content sheet only padded by `64.dp`, extending 48.dp underneath the bottom bar.
  - Since the sheet's corner radius is 24.dp, the entire bottom curve was covered by the bottom bar.
- **Solution**: Set `navBarHeight = if (isNavBarVisible) 64.dp + navBarBottomInset else navBarBottomInset` in `MainScreen.kt`, ensuring the sheet docks flush against the top edge of `ShortNavigationBar`.

### 3. Extra Bottom Padding on Video List Screen
- **Symptoms**: In gesture navigation, opening a folder (`VideoListScreen`) showed a large dead gap (~38.dp) below the bottom card, floating way higher than the intended 14.dp margin.
- **Root Cause**: `MainActivity.kt` computed `totalNavigationBarHeight = navBarHeight + systemBottomInset` and provided it to all screens. On pushed screens (`VideoListScreen`), `totalNavigationBarHeight` became 24.dp (the gesture inset). `ContentSurfaceContainer` added that 24.dp on top of `bottomGap = 14.dp`, producing a 38.dp bottom margin.
- **Solution**: Kept `totalNavigationBarHeight = navBarHeight` (0.dp on pushed screens) in `MainActivity.kt`. Pushed screens rely directly on `bottomGap = 14.dp`.

---

## File Change Reference

### 1. `app/src/main/kotlin/xyz/mpv/rex/MainActivity.kt`
- Configures transparent status and navigation bar styles:
  ```kotlin
  val barStyle = if (isDarkMode) {
    SystemBarStyle.dark(Color.Transparent.toArgb())
  } else {
    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
  }
  enableEdgeToEdge(
    statusBarStyle = barStyle,
    navigationBarStyle = barStyle,
  )
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    window.isNavigationBarContrastEnforced = false
  }
  ```
- Keeps `totalNavigationBarHeight = navBarHeight` so pushed screens do not inherit an unwanted bottom inset.

### 2. `app/src/main/kotlin/xyz/mpv/rex/ui/browser/MainScreen.kt`
- Supplies the combined navigation bar height (`64.dp + navBarBottomInset`) to all tabs via `LocalNavigationBarHeight`:
  ```kotlin
  val navBarBottomInset = ShortNavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
  val navBarHeight = if (isNavBarVisible) 64.dp + navBarBottomInset else navBarBottomInset
  CompositionLocalProvider(
    LocalNavigationBarHeight provides navBarHeight,
    LocalBottomBarPresent provides isNavBarVisible,
    LocalMiniPlayerActive provides miniPlayerState.isPlaybackActive,
  )
  ```

### 3. `app/src/main/kotlin/xyz/mpv/rex/ui/browser/folderlist/FolderListScreen.kt`
- Sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)` on the inner `Scaffold` so that `FloatingActionButtonMenu` and the content container are not double-inset above the outer `MainScreen` bottom bar.

### 4. `app/src/main/kotlin/xyz/mpv/rex/ui/browser/components/FloatingBottomBar.kt`
- Uses `LocalNavigationBarHeight.current` to position the floating pill bar (`BrowserBottomBar`), ensuring it clears `ShortNavigationBar` on `MainScreen` and rests 16.dp above the bottom on pushed screens.

---

## Verification Checklist

When touching insets or navigation bar code in the future, always verify both navigation modes:
1. **3-Button Navigation**:
   - Home tab: Nav bar area displays `surfaceContainer` (no white or grey scrim strip). Content sheet rounded bottom corners are flush against the bottom bar.
   - Pushed screens (`VideoListScreen`, `PreferencesScreen`): The 3 buttons are visible against the screen background. The last item in lists scrolls clear of the buttons.
2. **Gesture Navigation**:
   - Home tab: Nav bar area is transparent; pill sits above the gesture bar line.
   - Pushed screens (`VideoListScreen`): Sheet ends 14.dp above the bottom of the screen without dead space or double padding.
