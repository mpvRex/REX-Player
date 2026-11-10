package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.domain.hdr.HdrToysManager
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.edgeBlend
import xyz.mpv.rex.ui.player.Debanding
import xyz.mpv.rex.ui.player.MPVProfile
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import xyz.mpv.rex.ui.preferences.VulkanUtils
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object DecoderPreferencesScreen : Screen {
  override val transformSourceId: String
    get() = "settings:DecoderPreferencesScreen"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<DecoderPreferences>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val isVulkanSupported = remember { VulkanUtils.isVulkanSupported(context) }
    var showGpuNextWarning by remember { mutableStateOf(false) }
    val pageColor = MaterialTheme.colorScheme.surfaceContainer
    val cardColor = MaterialTheme.colorScheme.surface

    CollapsibleHeaderScreen(
      containerColor = pageColor,
      title = stringResource(R.string.pref_decoder),
      showBackButton = true,
      onBackClick = backstack::navigateBack,
    ) { contentModifier ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      val listState = rememberPreferenceLazyListState()
      ProvidePreferenceLocals {
        LazyColumn(
          state = listState,
          modifier = contentModifier.edgeBlend(listState, 16.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_decoder))
          }

          item {
            val profile by preferences.profile.collectAsState()
            val currentProfile = MPVProfile.fromValue(profile)
            val tryHWDecoding by preferences.tryHWDecoding.collectAsState()
            val gpuNext by preferences.gpuNext.collectAsState()
            val useVulkan by preferences.useVulkan.collectAsState()
            val debanding by preferences.debanding.collectAsState()
            val useYUV420p by preferences.useYUV420P.collectAsState()
            val enableAnime4K by preferences.enableAnime4K.collectAsState()
            val enableHdrToys by preferences.enableHdrToys.collectAsState()

            GroupedListColumn {
              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_decoder, R.string.pref_decoder_profile_title),
              ) {
                ListPreference(
                  value = currentProfile,
                  onValueChange = { preferences.profile.set(it.value) },
                  values = MPVProfile.entries,
                  title = { Text(stringResource(R.string.pref_decoder_profile_title)) },
                  summary = {
                    Text(
                      currentProfile.displayName,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_try_hw_dec_title,
              ) {
                SwitchPreference(
                  value = tryHWDecoding,
                  onValueChange = { preferences.tryHWDecoding.set(it) },
                  title = { Text(stringResource(R.string.pref_decoder_try_hw_dec_title)) },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_codec_info_title,
              ) {
                me.zhanghai.compose.preference.Preference(
                  title = { Text(stringResource(R.string.pref_decoder_codec_info_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_decoder_codec_info_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { backstack.add(CodecInformationScreen) },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_gpu_next_title,
              ) {
                Column {
                  SwitchPreference(
                    value = gpuNext,
                    onValueChange = { enabled ->
                      if (enabled && !gpuNext && !useVulkan) {
                        showGpuNextWarning = true
                      } else {
                        preferences.gpuNext.set(enabled)
                        if (enabled && !useVulkan) {
                          preferences.enableAnime4K.set(false)
                        }
                      }
                    },
                    title = { Text(stringResource(R.string.pref_decoder_gpu_next_title)) },
                    summary = {
                      Text(
                        stringResource(R.string.pref_decoder_gpu_next_summary),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )

                  if (showGpuNextWarning) {
                    AlertDialog(
                      onDismissRequest = { showGpuNextWarning = false },
                      title = { Text(stringResource(R.string.pref_decoder_gpu_next_enable_title)) },
                      text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                          Text(stringResource(R.string.pref_decoder_gpu_next_warning))
                          Text(stringResource(R.string.pref_decoder_gpu_next_purple_screen_fix))
                          
                          Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                          ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                              Text(
                                text = stringResource(R.string.pref_anime4k_incompatibility),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                              )
                              Text(
                                text = stringResource(R.string.pref_anime4k_gpu_next_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                              )
                            }
                          }
                        }
                      },
                      confirmButton = {
                        Button(onClick = {
                          preferences.gpuNext.set(true)
                          preferences.enableAnime4K.set(false)
                          showGpuNextWarning = false
                        }) {
                          Text(stringResource(R.string.pref_decoder_gpu_next_enable_anyway))
                        }
                      },
                      dismissButton = {
                        TextButton(onClick = { showGpuNextWarning = false }) {
                          Text(stringResource(R.string.generic_cancel))
                        }
                      }
                    )
                  }
                }
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_vulkan_title,
              ) {
                SwitchPreference(
                  value = useVulkan,
                  onValueChange = { enabled ->
                    preferences.useVulkan.set(enabled)
                    if (!enabled) {
                      val anime4kEnabled = preferences.enableAnime4K.get()
                      val gpuNextEnabled = preferences.gpuNext.get()
                      if (anime4kEnabled && gpuNextEnabled) {
                        preferences.gpuNext.set(false)
                      }
                    }
                  },
                  enabled = isVulkanSupported,
                  title = { Text(stringResource(R.string.pref_decoder_vulkan_title) + " (Experimental)") },
                  summary = {
                    Text(
                      stringResource(
                        if (isVulkanSupported) R.string.pref_decoder_vulkan_summary
                        else R.string.pref_decoder_vulkan_not_supported
                      ),
                      color = if (isVulkanSupported) MaterialTheme.colorScheme.outline
                             else MaterialTheme.colorScheme.error,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_debanding_title,
              ) {
                ListPreference(
                  value = debanding,
                  onValueChange = { preferences.debanding.set(it) },
                  values = Debanding.entries,
                  title = { Text(stringResource(R.string.pref_decoder_debanding_title)) },
                  summary = {
                    Text(
                      debanding.name,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_decoder_yuv420p_title,
              ) {
                SwitchPreference(
                  value = useYUV420p,
                  onValueChange = { preferences.useYUV420P.set(it) },
                  title = { Text(stringResource(R.string.pref_decoder_yuv420p_title)) },
                  summary = {
                    Text(
                      stringResource(R.string.pref_decoder_yuv420p_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.MIDDLE,
                highlightKey = R.string.pref_anime4k_title,
              ) {
                SwitchPreference(
                  value = enableAnime4K,
                  onValueChange = { enabled ->
                    preferences.enableAnime4K.set(enabled)
                    if (enabled) {
                      preferences.enableHdrToys.set(false)
                      if (!useVulkan) {
                        preferences.gpuNext.set(false)
                      }
                    }
                  },
                  title = { Text(stringResource(R.string.pref_anime4k_title)) },
                  summary = {
                    Column {
                      Text(
                        stringResource(R.string.pref_anime4k_summary),
                        color = MaterialTheme.colorScheme.outline,
                      )
                      Text(
                        text = "github.com/bloc97/Anime4K",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                          val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bloc97/Anime4K"))
                          context.startActivity(intent)
                        }
                      )
                    }
                  },
                )
              }

              GroupedPreferenceCard(
                color = cardColor, tonalElevation = 0.dp,
                position = GroupPosition.LAST,
                highlightKey = "HDR-to-SDR Tone Mapping (hdr-toys)",
              ) {
                Column {
                  SwitchPreference(
                    value = enableHdrToys,
                    onValueChange = { enabled ->
                      preferences.enableHdrToys.set(enabled)
                      if (enabled) {
                        preferences.gpuNext.set(true)
                        preferences.enableAnime4K.set(false)
                      }
                    },
                    title = { Text("HDR-to-SDR Tone Mapping (hdr-toys)") },
                    summary = {
                      Column {
                        Text(
                          "Apply high quality GLSL shaders for HDR-to-SDR conversion. Requires gpu-next.",
                          color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                          text = "github.com/natural-harmonia-gropius/hdr-toys",
                          color = MaterialTheme.colorScheme.primary,
                          style = MaterialTheme.typography.bodySmall,
                          textDecoration = TextDecoration.Underline,
                          modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/natural-harmonia-gropius/hdr-toys"))
                            context.startActivity(intent)
                          }
                        )
                      }
                    },
                  )

                  if (enableHdrToys) {
                    val toneMapping by preferences.hdrToysToneMapping.collectAsState()
                    val currentTone = runCatching { HdrToysManager.ToneMapping.valueOf(toneMapping) }.getOrDefault(HdrToysManager.ToneMapping.ASTRA)
                    ListPreference(
                      value = currentTone,
                      onValueChange = { preferences.hdrToysToneMapping.set(it.name) },
                      values = HdrToysManager.ToneMapping.entries,
                      title = { Text("  Tone Mapping Algorithm") },
                      summary = {
                        Text(
                          "  " + currentTone.name,
                          color = MaterialTheme.colorScheme.outline,
                        )
                      }
                    )

                    val gamutMapping by preferences.hdrToysGamutMapping.collectAsState()
                    val currentGamut = runCatching { HdrToysManager.GamutMapping.valueOf(gamutMapping) }.getOrDefault(HdrToysManager.GamutMapping.BOTTOSSON)
                    ListPreference(
                      value = currentGamut,
                      onValueChange = { preferences.hdrToysGamutMapping.set(it.name) },
                      values = HdrToysManager.GamutMapping.entries,
                      title = { Text("  Gamut Mapping Algorithm") },
                      summary = {
                        Text(
                          "  " + currentGamut.name,
                          color = MaterialTheme.colorScheme.outline,
                        )
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

object VulkanUtils {
    private const val TAG = "VulkanUtils"

    /**
     * Checks if the device supports Vulkan for MPV rendering
     *
     * Requirements for MPV androidvk context:
     * - Android 13 (API 33) minimum for Vulkan 1.3
     * - Vulkan 1.3 (0x00403000) hardware version
     * - GPU must also support OpenGL ES 3.1 or higher
     *
     * @return true if Vulkan 1.3+ is supported for MPV, false otherwise
     */
    fun isVulkanSupported(context: Context): Boolean {
        try {
            // Vulkan 1.3 requires Android 13 (API 33) minimum
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Vulkan not supported: Android version ${Build.VERSION.SDK_INT} < 33 (Tiramisu)")
                return false
            }

            val packageManager = context.packageManager

            // Check for OpenGL ES 3.1+ support (required by Android for Vulkan)
            val configInfo = packageManager.systemAvailableFeatures
                .firstOrNull { it.name == null }

            val glesVersion = configInfo?.reqGlEsVersion ?: 0
            val glesMajor = glesVersion shr 16
            val glesMinor = glesVersion and 0xFFFF

            Log.d(TAG, "Device OpenGL ES version: $glesMajor.$glesMinor (raw: 0x${glesVersion.toString(16)})")

            // OpenGL ES 3.1 = 0x00030001
            if (glesVersion < 0x00030001) {
                Log.d(TAG, "Vulkan not supported: OpenGL ES $glesMajor.$glesMinor < 3.1")
                return false
            }

            // Check for Vulkan 1.3 hardware version (required for proper MPV support)
            if (packageManager.hasSystemFeature(
                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
                    0x00403000 // Vulkan 1.3
                )) {
                Log.d(TAG, "Vulkan 1.3 supported ✓")
                return true
            }

            Log.d(TAG, "Vulkan not supported: Vulkan 1.3 not available")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error checking Vulkan support", e)
            return false
        }
    }
}
