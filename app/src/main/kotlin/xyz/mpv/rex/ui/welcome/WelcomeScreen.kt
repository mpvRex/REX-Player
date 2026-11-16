package xyz.mpv.rex.ui.welcome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.locale.LocaleHelper
import xyz.mpv.rex.utils.permission.PermissionUtils

@Serializable
object WelcomeScreen : Screen {

  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }

    var isGranted by remember {
      mutableStateOf(PermissionUtils.isStoragePermissionGranted(context))
    }

    val appIconBitmap = remember(context) {
      try {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
      } catch (_: Exception) {
        null
      }
    }

    // Re-check permission status when app resumes from system settings dialog
    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          isGranted = PermissionUtils.isStoragePermissionGranted(context)
        }
      }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose {
        lifecycleOwner?.lifecycle?.removeObserver(observer)
      }
    }

    fun navigateToMain() {
      appearancePreferences.onboardingCompleted.set(true)
      if (backstack.lastOrNull() == WelcomeScreen) {
        // Add the destination before removing the current entry so the back stack is never
        // empty (NavDisplay rejects an empty back stack during recomposition).
        backstack.add(MainScreen)
        backstack.remove(WelcomeScreen)
      } else if (backstack.none { it == MainScreen }) {
        // Onboarding finished while this entry is no longer on top; make sure a root exists.
        backstack.add(MainScreen)
      }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) {
      isGranted = PermissionUtils.isStoragePermissionGranted(context)
      if (isGranted) {
        navigateToMain()
      }
    }

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        // Pinned sticky bottom container that respects system navigation bars
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.surfaceContainer,
          shadowElevation = 6.dp,
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            FilledTonalButton(
              onClick = {
                if (isGranted) {
                  navigateToMain()
                } else {
                  PermissionUtils.requestStorageAccess(context) {
                    permissionLauncher.launch(PermissionUtils.getStoragePermission())
                  }
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Icon(
                  imageVector = if (isGranted) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = if (isGranted) {
                    stringResource(R.string.welcome_get_started)
                  } else {
                    stringResource(R.string.welcome_grant_permission)
                  },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }

            if (!isGranted) {
              Spacer(modifier = Modifier.height(8.dp))
              TextButton(
                onClick = { navigateToMain() },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = stringResource(R.string.welcome_skip_permission),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      },
    ) { paddingValues ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(
          top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
          bottom = 24.dp,
          start = 24.dp,
          end = 24.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          // App Logo
          if (appIconBitmap != null) {
            Image(
              bitmap = appIconBitmap,
              contentDescription = null,
              modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp)),
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(28.dp))
        }

        item {
          // Language Picker Card
          val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .clickable { showLanguageDialog = true },
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(20.dp),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
              ) {
                Box(
                  modifier = Modifier.fillMaxSize(),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                }
              }

              Spacer(modifier = Modifier.width(16.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.welcome_language_section_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = if (currentLanguage.code.isEmpty()) {
                    stringResource(R.string.system_default)
                  } else {
                    "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
                  },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }

              TextButton(onClick = { showLanguageDialog = true }) {
                Text(
                  text = stringResource(R.string.browse),
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
        }

        item {
          // Storage Permission Card (Dynamic status & descriptions)
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            shape = RoundedCornerShape(20.dp),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  modifier = Modifier.size(40.dp),
                  shape = CircleShape,
                  color = if (isGranted) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.secondaryContainer
                  },
                ) {
                  Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Folder,
                      contentDescription = null,
                      modifier = Modifier.size(22.dp),
                      tint = if (isGranted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                      } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                      },
                    )
                  }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                  text = if (isGranted) {
                    stringResource(R.string.storage_access_granted)
                  } else {
                    stringResource(R.string.storage_access_required)
                  },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }

              Text(
                text = if (isGranted) {
                  stringResource(R.string.storage_access_granted_desc)
                } else if (BuildConfig.SCOPED_STORAGE_ONLY) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stringResource(R.string.permission_required_photos_videos)
                  } else {
                    stringResource(R.string.permission_required_storage)
                  }
                } else {
                  stringResource(R.string.permission_required_all_files)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              if (!isGranted) {
                // Why is this needed? link
                TextButton(
                  onClick = { showExplanationDialog = true },
                  modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.why_do_i_see_this),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
      val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
      var selectedCode by remember { mutableStateOf(currentCode) }
      val languages = remember { LocaleHelper.getSupportedLanguages(context) }

      AlertDialog(
        onDismissRequest = { showLanguageDialog = false },
        icon = {
          Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        title = {
          Text(
            text = stringResource(R.string.pref_appearance_language_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 350.dp)
              .verticalScroll(rememberScrollState())
              .selectableGroup(),
          ) {
            languages.forEach { language ->
              val isSelected = selectedCode == language.code
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .selectable(
                    selected = isSelected,
                    onClick = { selectedCode = language.code },
                    role = Role.RadioButton,
                  )
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  if (language.code.isNotEmpty()) {
                    Text(
                      text = language.localizedName,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          FilledTonalButton(
            onClick = {
              LocaleHelper.setAppLanguage(context, selectedCode)
              showLanguageDialog = false
            },
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(stringResource(R.string.generic_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { showLanguageDialog = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
        shape = RoundedCornerShape(24.dp),
      )
    }

    // Explanation Dialog
    if (showExplanationDialog) {
      val uriHandler = LocalUriHandler.current
      val githubUrl = "https://github.com/mpvRex/REX-Player"
      val isPlayStoreBuild = BuildConfig.SCOPED_STORAGE_ONLY

      AlertDialog(
        onDismissRequest = { showExplanationDialog = false },
        icon = {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        title = {
          Text(
            text = stringResource(R.string.why_this_permission_is_needed),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(
            modifier = Modifier
              .heightIn(max = 400.dp)
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (isPlayStoreBuild) {
              Text(
                text = stringResource(R.string.permission_explanation_playstore_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  stringResource(R.string.permission_explanation_tiramisu_plus)
                } else {
                  stringResource(R.string.permission_explanation_pre_tiramisu)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = stringResource(R.string.permission_used_exclusively_for),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = stringResource(R.string.permission_usage_bullet_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                text = stringResource(R.string.permission_explanation_standard_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = stringResource(R.string.permission_security_policy_change),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = stringResource(R.string.permission_privacy_assurance_standard),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Text(
              text = stringResource(R.string.opensource_github_notice),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
              text = githubUrl,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Medium,
              textDecoration = TextDecoration.Underline,
              modifier = Modifier.clickable { uriHandler.openUri(githubUrl) },
            )

            Text(
              text = stringResource(R.string.privacy_assurance_final),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium,
            )
          }
        },
        confirmButton = {
          FilledTonalButton(
            onClick = { showExplanationDialog = false },
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(stringResource(R.string.got_it))
          }
        },
        shape = RoundedCornerShape(20.dp),
      )
    }
  }
}
