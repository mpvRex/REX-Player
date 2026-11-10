package xyz.mpv.rex.ui.player.controls.components.panels

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.presentation.components.OutlinedNumericChooser
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.theme.spacing
import kotlin.math.roundToInt

@Composable
fun AudioDelayPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  if (isPortrait) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
      Column(
        modifier = modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(top = 4.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(bottom = MaterialTheme.spacing.small),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.player_sheets_audio_delay_card_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          IconButton(onClick = onDismissRequest) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        AudioDelayContent()
      }
    }
  } else {
    AudioDelaySideSheet(
      onDismissRequest = onDismissRequest,
      modifier = modifier,
    )
  }
}

@Composable
private fun AudioDelaySideSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var isVisible by remember { mutableStateOf(false) }
  val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)

  val scrimAlpha by animateFloatAsState(
    targetValue = if (isVisible) 0.5f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "audio_side_sheet_scrim",
  )

  val dismissWithAnimation: () -> Unit = {
    scope.launch {
      isVisible = false
      delay(250)
      latestOnDismissRequest()
    }
  }

  BackHandler(enabled = isVisible, onBack = { dismissWithAnimation() })

  LaunchedEffect(Unit) {
    isVisible = true
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = scrimAlpha))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { dismissWithAnimation() },
      ),
    contentAlignment = Alignment.CenterEnd,
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = xyz.mpv.rex.ui.motion.RexMotion.panelEnter(fromEnd = true),
      exit = xyz.mpv.rex.ui.motion.RexMotion.panelExit(fromEnd = true),
    ) {
      Surface(
        modifier = modifier
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}, // Prevent clicking through sheet to dismiss
          )
          .widthIn(max = 380.dp)
          .fillMaxHeight()
          .windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
          ),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MaterialTheme.spacing.medium)
              .padding(top = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.player_sheets_audio_delay_card_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { dismissWithAnimation() }) {
              Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

          Spacer(Modifier.height(MaterialTheme.spacing.small))

          AudioDelayContent()
        }
      }
    }
  }
}

@Composable
private fun AudioDelayContent(
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<AudioPreferences>()
  val delay by MPVLib.propDouble["audio-delay"].collectAsState()
  val delayFloat by remember { derivedStateOf { (delay ?: 0.0).toFloat() } }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .padding(bottom = MaterialTheme.spacing.large),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
  ) {
    // Stepper & Quick Adjustment Section
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          Icon(
            Icons.Default.Audiotrack,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.player_sheets_sub_delay_card_delay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        TextButton(onClick = { MPVLib.setPropertyDouble("audio-delay", 0.0) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FormatClear, null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.generic_reset))
          }
        }
      }

      OutlinedNumericChooser(
        value = delayFloat,
        onChange = { MPVLib.setPropertyDouble("audio-delay", it.toDouble()) },
        step = 0.05f,
        min = -100f,
        max = 100f,
        suffix = { Text("s") },
        valueFormatter = { "%.2f".format(it) },
      )

      // Quick adjustment chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        listOf(-0.5f, -0.1f, 0.0f, 0.1f, 0.5f).forEach { step ->
          FilterChip(
            selected = false,
            onClick = {
              val newDelay = if (step == 0.0f) 0.0 else (delayFloat + step).toDouble()
              MPVLib.setPropertyDouble("audio-delay", newDelay)
            },
            label = {
              Text(
                if (step == 0.0f) "0s" else if (step > 0) "+${step}s" else "${step}s",
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    // A/V Sync Calibration Card
    OutlinedCard(
      shape = RoundedCornerShape(16.dp),
      colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
      ),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          Icon(
            Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "A/V Sync Assistant",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }

        var isDirectionPositive by remember { mutableStateOf<Boolean?>(null) }
        var timerStart by remember { mutableStateOf<Long?>(null) }
        var finalDelay by remember { mutableStateOf(delayFloat) }

        LaunchedEffect(isDirectionPositive) {
          if (isDirectionPositive == null) {
            MPVLib.setPropertyDouble("audio-delay", finalDelay.toDouble())
            return@LaunchedEffect
          }
          finalDelay = delayFloat
          val startTime = System.currentTimeMillis()
          timerStart = startTime
          val startingDelay = finalDelay
          while (isDirectionPositive != null && timerStart != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val direction = isDirectionPositive ?: break
            finalDelay = startingDelay + (if (direction) elapsed / 1000f else -elapsed / 1000f)
            delay(20)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          Button(
            onClick = {
              isDirectionPositive = if (isDirectionPositive == null) true else null
            },
            modifier = Modifier.weight(1f),
            enabled = isDirectionPositive != true,
          ) {
            Text(stringResource(R.string.player_sheets_sub_delay_audio_sound_heard))
          }

          Button(
            onClick = {
              isDirectionPositive = if (isDirectionPositive == null) false else null
            },
            modifier = Modifier.weight(1f),
            enabled = isDirectionPositive != false,
          ) {
            Text(stringResource(R.string.player_sheets_sub_delay_sound_sound_spotted))
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    // Set as Default Action
    Button(
      onClick = { preferences.defaultAudioDelay.set((delayFloat * 1000).roundToInt()) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.player_sheets_delay_set_as_default))
      }
    }
  }
}
