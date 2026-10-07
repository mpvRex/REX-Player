package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import xyz.mpv.rex.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.Preference
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.utils.TelegramIcon
import xyz.mpv.rex.ui.utils.CommunityIcon
import xyz.mpv.rex.MainActivity
import xyz.mpv.rex.LocalUpdateViewModel
import xyz.mpv.rex.utils.update.UpdateViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator

@Serializable
object AboutScreen : Screen {
  @Suppress("DEPRECATION")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val clipboardManager = LocalClipboardManager.current
    val updateViewModel = LocalUpdateViewModel.current
    val updateState by (updateViewModel?.updateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
    val preferences = koinInject<AppearancePreferences>()
    
    val packageManager: PackageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName?.substringBefore('-') ?: packageInfo.versionName
    val buildType = BuildConfig.BUILD_TYPE

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateViewModel.UpdateState.NoUpdate -> {
                Toast.makeText(context, context.getString(R.string.pref_about_up_to_date), Toast.LENGTH_SHORT).show()
                updateViewModel?.dismissNoUpdate()
            }
            is UpdateViewModel.UpdateState.Error -> {
                Toast.makeText(context, context.getString(R.string.pref_about_check_updates_failed), Toast.LENGTH_SHORT).show()
                updateViewModel?.dismissNoUpdate()
            }
            else -> {}
        }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_about_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            ) 
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { paddingValues ->
      val cs = MaterialTheme.colorScheme
      val colorPrimary = cs.primaryContainer
      val colorSecondary = cs.secondaryContainer
      val isAutoUpdateEnabled by (updateViewModel?.isAutoUpdateEnabled ?: MutableStateFlow(false)).collectAsState()
    val isCompanionInstalled by (updateViewModel?.isCompanionInstalled ?: MutableStateFlow(false)).collectAsState()
    val companionUpdateState by (updateViewModel?.companionUpdateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
    val isYtdlpAutoUpdateEnabled by (updateViewModel?.isYtdlpAutoUpdateEnabled ?: MutableStateFlow(false)).collectAsState()
      val transition = rememberInfiniteTransition()
      val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 5000),
            repeatMode = RepeatMode.Reverse,
          ),
      )
      val cornerRadius = 28.dp

      ProvidePreferenceLocals {
        Column(
          modifier =
            Modifier
              .padding(paddingValues)
              .verticalScroll(rememberScrollState()),
        ) {
          GroupedListColumn {
            GroupedPreferenceCard(position = GroupPosition.ONLY) {
              Box(
                modifier =
                  Modifier
                    .drawWithCache {
                      val cx = size.width - size.width * fraction
                      val cy = size.height * fraction

                      val gradient =
                        Brush.radialGradient(
                          colors = listOf(colorPrimary, colorSecondary),
                          center = Offset(cx, cy),
                          radius = 800f,
                        )

                      onDrawBehind {
                        drawRoundRect(
                          brush = gradient,
                          cornerRadius =
                            CornerRadius(
                              cornerRadius.toPx(),
                              cornerRadius.toPx(),
                            ),
                        )
                      }
                    }
                    .padding(16.dp),
              ) {
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp)) {
                      AndroidView(
                        modifier = Modifier.matchParentSize(),
                        factory = { ctx ->
                          ImageView(ctx).apply {
                            setImageResource(R.mipmap.ic_launcher)
                          }
                        },
                      )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                      )
                      Spacer(Modifier.height(4.dp))
                      Text(
                        text = "v$versionName $buildType",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(20.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                  ) {
                    val btnContainer = cs.primary
                    val btnContent = cs.onPrimary
                    Button(
                      onClick = { backstack.add(LibrariesScreen) },
                      modifier =
                        Modifier
                          .weight(1f)
                          .height(56.dp),
                      shape = RoundedCornerShape(16.dp),
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor = btnContainer,
                          contentColor = btnContent,
                        ),
                    ) {
                      Text(
                        text = stringResource(id = R.string.pref_about_oss_libraries),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }

                    Button(
                      onClick = {
                        context.startActivity(
                          Intent(
                            Intent.ACTION_VIEW,
                            context.getString(R.string.github_repo_url).toUri(),
                          ),
                        )
                      },
                      modifier =
                        Modifier
                          .weight(1f)
                          .height(56.dp),
                      shape = RoundedCornerShape(16.dp),
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor = btnContainer,
                          contentColor = btnContent,
                        ),
                    ) {
                      Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(20.dp))

                  Column(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clickable {
                          clipboardManager.setText(AnnotatedString(collectDeviceInfo()))
                        },
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(id = R.string.pref_about_device_info),
                        modifier = Modifier.size(20.dp),
                        tint = cs.onPrimaryContainer,
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = stringResource(id = R.string.pref_about_device_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer,
                      )
                    }
                    Text(
                      text = collectDeviceInfo(),
                      style = MaterialTheme.typography.bodySmall,
                      color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                  }
                }
              }
            }
          }

          Spacer(Modifier.height(8.dp))

          PreferenceSectionHeader(title = stringResource(R.string.pref_decoder_codec_info_title))
          GroupedListColumn {
            GroupedPreferenceCard(position = GroupPosition.ONLY) {
              Preference(
                title = { Text(text = stringResource(R.string.pref_decoder_codec_info_title)) },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_decoder_codec_info_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(CodecInformationScreen) },
              )
            }
          }

          Spacer(Modifier.height(8.dp))

          PreferenceSectionHeader(title = stringResource(id = R.string.pref_about_telegram_title))
          GroupedListColumn {
            GroupedPreferenceCard(position = GroupPosition.FIRST) {
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_about_telegram_channel)) },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_about_telegram_channel_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    imageVector = TelegramIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = {
                  context.startActivity(
                    Intent(
                      Intent.ACTION_VIEW,
                      context.getString(R.string.pref_about_telegram_url).toUri(),
                    ),
                  )
                },
              )
            }

            GroupedPreferenceCard(position = GroupPosition.LAST) {
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_about_telegram_group)) },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_about_telegram_group_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    imageVector = TelegramIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = {
                  context.startActivity(
                    Intent(
                      Intent.ACTION_VIEW,
                      context.getString(R.string.pref_about_telegram_chat_url).toUri(),
                    ),
                  )
                },
              )
            }
          }

          Spacer(Modifier.height(8.dp))

          // Updates Section
          if (BuildConfig.ENABLE_UPDATE_FEATURE) {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_updates))
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST) {
                SwitchPreference(
                  value = isAutoUpdateEnabled,
                  onValueChange = { updateViewModel?.toggleAutoUpdate(it) },
                  title = { Text(stringResource(R.string.pref_about_auto_check_updates)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_about_auto_check_updates_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  icon = {
                    Icon(
                      imageVector = Icons.Default.SystemUpdate,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                )
              }
              
              GroupedPreferenceCard(position = GroupPosition.LAST) {
                Preference(
                  title = { Text(stringResource(R.string.pref_about_check_updates)) },
                  summary = {
                    if (updateState is UpdateViewModel.UpdateState.Loading) {
                      Text(stringResource(R.string.pref_about_checking), color = MaterialTheme.colorScheme.primary)
                    } else {
                      Text(stringResource(R.string.pref_about_check_updates_summary), color = MaterialTheme.colorScheme.outline)
                    }
                  },
                  icon = {
                    if (updateState is UpdateViewModel.UpdateState.Loading) {
                       CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                       Icon(
                         imageVector = Icons.Default.SystemUpdate,
                         contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary
                       )
                    }
                  },
                  onClick = { updateViewModel?.checkForUpdate(manual = true) },
                  enabled = updateState !is UpdateViewModel.UpdateState.Loading
                )
              }
            }
          }

          // REX Ytdlp companion updates — only when the addon is installed.
          if (BuildConfig.ENABLE_UPDATE_FEATURE && isCompanionInstalled) {
            Spacer(Modifier.height(8.dp))
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_ytdlp_updates))
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST) {
                SwitchPreference(
                  value = isYtdlpAutoUpdateEnabled,
                  onValueChange = { updateViewModel?.toggleYtdlpAutoUpdate(it) },
                  title = { Text(stringResource(R.string.pref_about_auto_check_updates)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_about_auto_check_updates_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  icon = {
                    Icon(
                      imageVector = Icons.Default.SystemUpdate,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                )
              }

              GroupedPreferenceCard(position = GroupPosition.LAST) {
                Preference(
                  title = { Text(stringResource(R.string.pref_about_check_updates)) },
                  summary = {
                    if (companionUpdateState is UpdateViewModel.UpdateState.Loading) {
                      Text(stringResource(R.string.pref_about_checking), color = MaterialTheme.colorScheme.primary)
                    } else {
                      Text(stringResource(R.string.pref_about_check_updates_summary), color = MaterialTheme.colorScheme.outline)
                    }
                  },
                  icon = {
                    if (companionUpdateState is UpdateViewModel.UpdateState.Loading) {
                      CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                      )
                    }
                  },
                  onClick = { updateViewModel?.checkCompanionUpdate(manual = true) },
                  enabled = companionUpdateState !is UpdateViewModel.UpdateState.Loading
                )
              }
            }
          }

          Spacer(Modifier.height(xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current + 16.dp))
        }
      }
    }
  }
}

@Suppress("DEPRECATION")
@Serializable
object LibrariesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_about_oss_libraries),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            ) 
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
        )
      },
    ) { paddingValues ->
    }
  }
}
