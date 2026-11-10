package xyz.mpv.rex.ui.preferences

import android.media.MediaCodecList
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack

data class CodecInfoItem(
  val name: String,
  val isHardware: Boolean,
  val isVideo: Boolean,
  val mimeType: String,
  val formatName: String,
  val maxResolution: String? = null,
)

object CodecUtils {
  private fun getFormatName(mimeType: String): String {
    return when (mimeType.lowercase()) {
      "video/avc" -> "H.264 / AVC"
      "video/hevc" -> "H.265 / HEVC"
      "video/av01" -> "AV1"
      "video/x-vnd.on2.vp9" -> "VP9"
      "video/x-vnd.on2.vp8" -> "VP8"
      "video/mp4v-es" -> "MPEG-4"
      "video/3gpp" -> "H.263"
      "video/mpeg2" -> "MPEG-2"
      "video/vc1" -> "VC-1"
      "audio/mp4a-latm" -> "AAC"
      "audio/mpeg" -> "MP3"
      "audio/opus" -> "Opus"
      "audio/flac" -> "FLAC"
      "audio/ac3" -> "AC-3 (Dolby Digital)"
      "audio/eac3" -> "E-AC-3 (Dolby Digital Plus)"
      "audio/vorbis" -> "Vorbis"
      "audio/raw" -> "PCM / Raw"
      "audio/g711-alaw" -> "G.711 a-law"
      "audio/g711-mlaw" -> "G.711 µ-law"
      else -> mimeType.substringAfter("/").uppercase()
    }
  }

  fun getDeviceCodecs(): List<CodecInfoItem> {
    val items = mutableListOf<CodecInfoItem>()
    try {
      val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
      val codecInfos = codecList.codecInfos

      for (info in codecInfos) {
        if (info.isEncoder) continue

        val lowerName = info.name.lowercase()
        val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          info.isHardwareAccelerated
        } else {
          !(lowerName.startsWith("omx.google.") ||
              lowerName.startsWith("c2.android.") ||
              lowerName.contains(".sw.") ||
              lowerName.contains("software"))
        }

        for (mimeType in info.supportedTypes) {
          val isVideo = mimeType.startsWith("video/", ignoreCase = true)
          val isAudio = mimeType.startsWith("audio/", ignoreCase = true)
          if (!isVideo && !isAudio) continue

          var maxRes: String? = null
          if (isVideo) {
            try {
              val caps = info.getCapabilitiesForType(mimeType)
              val videoCaps = caps?.videoCapabilities
              if (videoCaps != null) {
                val maxW = videoCaps.supportedWidths.upper
                val maxH = videoCaps.supportedHeights.upper
                maxRes = "${maxW}x${maxH}"
              }
            } catch (_: Exception) {}
          }

          items.add(
            CodecInfoItem(
              name = info.name,
              isHardware = isHardware,
              isVideo = isVideo,
              mimeType = mimeType,
              formatName = getFormatName(mimeType),
              maxResolution = maxRes,
            )
          )
        }
      }
    } catch (_: Exception) {}

    return items.sortedWith(
      compareByDescending<CodecInfoItem> { it.isVideo }
        .thenByDescending { it.isHardware }
        .thenBy { it.formatName }
    )
  }
}

enum class CodecFilter {
  ALL, VIDEO, AUDIO, HW_ONLY
}

@Serializable
object CodecInformationScreen : Screen {
  override val transformSourceId: String
    get() = "settings:CodecInformationScreen"

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current

    val allCodecsState by produceState<List<CodecInfoItem>?>(initialValue = null) {
      value = withContext(Dispatchers.IO) {
        CodecUtils.getDeviceCodecs()
      }
    }

    var selectedFilter by remember { mutableStateOf(CodecFilter.ALL) }

    val pageColor = MaterialTheme.colorScheme.surfaceContainer
    val cardColor = MaterialTheme.colorScheme.surface

    CollapsibleHeaderScreen(
      containerColor = pageColor,
      title = stringResource(R.string.pref_decoder_codec_info_title),
      showBackButton = true,
      onBackClick = backstack::navigateBack,
    ) { contentModifier ->
      val allCodecs = allCodecsState
      if (allCodecs == null) {
        Box(
          modifier = contentModifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      } else {
        val filteredCodecs = remember(selectedFilter, allCodecs) {
          when (selectedFilter) {
            CodecFilter.ALL -> allCodecs
            CodecFilter.VIDEO -> allCodecs.filter { it.isVideo }
            CodecFilter.AUDIO -> allCodecs.filter { !it.isVideo }
            CodecFilter.HW_ONLY -> allCodecs.filter { it.isHardware }
          }
        }

        val totalCount = allCodecs.size
        val hwCount = allCodecs.count { it.isHardware }
        val swCount = totalCount - hwCount

        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
        ProvidePreferenceLocals {
          LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
          ) {
            // Overview Summary Card
            item {
              GroupedListColumn {
                GroupedPreferenceCard(position = GroupPosition.ONLY, color = cardColor, tonalElevation = 0.dp) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Column {
                        Text(
                          text = stringResource(R.string.codec_info_stats_title),
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = stringResource(R.string.codec_info_stats_summary, totalCount, hwCount, swCount),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.outline,
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Major Format Quick Status Matrix
                    val majorFormats = listOf(
                      "H.264 / AVC" to "video/avc",
                      "H.265 / HEVC" to "video/hevc",
                      "AV1" to "video/av01",
                      "VP9" to "video/x-vnd.on2.vp9",
                      "AAC" to "audio/mp4a-latm",
                      "Opus" to "audio/opus"
                    )

                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      majorFormats.forEach { (label, mime) ->
                        val matching = allCodecs.filter { it.mimeType.equals(mime, ignoreCase = true) }
                        val hasHW = matching.any { it.isHardware }
                        val hasSW = matching.any { !it.isHardware }

                        val badgeColor = when {
                          hasHW -> MaterialTheme.colorScheme.primaryContainer
                          hasSW -> MaterialTheme.colorScheme.secondaryContainer
                          else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val textColor = when {
                          hasHW -> MaterialTheme.colorScheme.onPrimaryContainer
                          hasSW -> MaterialTheme.colorScheme.onSecondaryContainer
                          else -> MaterialTheme.colorScheme.outline
                        }
                        val statusText = when {
                          hasHW -> "HW"
                          hasSW -> "SW"
                          else -> "N/A"
                        }

                        Surface(
                          color = badgeColor,
                          shape = RoundedCornerShape(8.dp),
                        ) {
                          Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                          ) {
                            Text(
                              text = label,
                              style = MaterialTheme.typography.labelMedium,
                              fontWeight = FontWeight.SemiBold,
                              color = textColor,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                              modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(textColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                              Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
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

            // Filter Chips
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                FilterChip(
                  selected = selectedFilter == CodecFilter.ALL,
                  onClick = { selectedFilter = CodecFilter.ALL },
                  label = { Text(stringResource(R.string.codec_info_filter_all)) },
                )
                FilterChip(
                  selected = selectedFilter == CodecFilter.VIDEO,
                  onClick = { selectedFilter = CodecFilter.VIDEO },
                  label = { Text(stringResource(R.string.codec_info_filter_video)) },
                )
                FilterChip(
                  selected = selectedFilter == CodecFilter.AUDIO,
                  onClick = { selectedFilter = CodecFilter.AUDIO },
                  label = { Text(stringResource(R.string.codec_info_filter_audio)) },
                )
                FilterChip(
                  selected = selectedFilter == CodecFilter.HW_ONLY,
                  onClick = { selectedFilter = CodecFilter.HW_ONLY },
                  label = { Text(stringResource(R.string.codec_info_filter_hw_only)) },
                )
              }
            }

            item {
              PreferenceSectionHeader(
                title = if (selectedFilter == CodecFilter.VIDEO) "Video Decoders"
                else if (selectedFilter == CodecFilter.AUDIO) "Audio Decoders"
                else "Available Decoders (${filteredCodecs.size})"
              )
            }

            item {
              GroupedListColumn {
                filteredCodecs.forEachIndexed { index, codec ->
                  val pos = when {
                    filteredCodecs.size == 1 -> GroupPosition.ONLY
                    index == 0 -> GroupPosition.FIRST
                    index == filteredCodecs.lastIndex -> GroupPosition.LAST
                    else -> GroupPosition.MIDDLE
                  }
                  GroupedPreferenceCard(position = pos, color = cardColor, tonalElevation = 0.dp) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = codec.formatName,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                        )

                        Surface(
                          color = if (codec.isHardware)
                            Color(0xFF2E7D32).copy(alpha = 0.15f)
                          else
                            Color(0xFFE65100).copy(alpha = 0.15f),
                          shape = RoundedCornerShape(12.dp),
                        ) {
                          Text(
                            text = if (codec.isHardware)
                              stringResource(R.string.codec_info_hw_badge)
                            else
                              stringResource(R.string.codec_info_sw_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (codec.isHardware) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(4.dp))

                      Text(
                        text = codec.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                      )

                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Text(
                          text = codec.mimeType,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.outline,
                        )

                        if (codec.maxResolution != null) {
                          Text(
                            text = stringResource(R.string.codec_info_max_res, codec.maxResolution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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
    }
  }
}
