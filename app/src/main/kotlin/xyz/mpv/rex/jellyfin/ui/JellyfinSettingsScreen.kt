package xyz.mpv.rex.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.jellyfin.api.JellyfinApi
import xyz.mpv.rex.jellyfin.preferences.JellyfinPreferences
import xyz.mpv.rex.jellyfin.remote.JellyfinRemoteClient
import xyz.mpv.rex.jellyfin.remote.JellyfinRemoteService
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.presentation.components.edgeBlend
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.serialization.Serializable

@Serializable
object JellyfinSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val prefs: JellyfinPreferences = koinInject()
    val api: JellyfinApi = koinInject()
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(prefs.serverUrl ?: "") }
    var username by remember { mutableStateOf(prefs.username ?: "") }
    var password by remember { mutableStateOf("") }
    var enableRemote by remember { mutableStateOf(prefs.enableRemote) }
    var deviceName by remember { mutableStateOf(prefs.deviceName) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(prefs.isConfigured()) }
    val remoteClient: JellyfinRemoteClient = koinInject()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
      configured = prefs.isConfigured()
    }

    CollapsibleHeaderScreen(
      title = "Jellyfin",
      showBackButton = true,
      onBackClick = { backstack.navigateBack() },
    ) { contentModifier ->
      val scrollState = rememberScrollState()
      Column(
        modifier = contentModifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .edgeBlend(scrollState, 16.dp)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jellyfin Server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
              value = serverUrl,
              onValueChange = { serverUrl = it },
              label = { Text("Server URL (e.g. https://jellyfin.example.com)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
            )
            OutlinedTextField(
              value = username,
              onValueChange = { username = it },
              label = { Text("Username") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
            )
            OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              label = { Text("Password") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Button(
                onClick = {
                  if (serverUrl.isBlank() || username.isBlank()) {
                    status = "Server URL and username required"
                    return@Button
                  }
                  if (serverUrl.lowercase().startsWith("http://")) {
                    status = "Warning: Using insecure HTTP — credentials transmitted in plaintext. Consider using HTTPS."
                  } else {
                    status = null
                  }
                  loading = true
                  scope.launch {
                    val normalized = serverUrl.trim().trimEnd('/')
                    val result = api.authenticate(normalized, username.trim(), password)
                    result.onSuccess { resp ->
                      val token = resp.accessToken
                      val uid = resp.user?.id
                      if (token.isNullOrBlank() || uid.isNullOrBlank()) {
                        status = "Authentication response missing token/user"
                      } else {
                        prefs.serverUrl = normalized
                        prefs.userId = uid
                        prefs.accessToken = token
                        prefs.username = username.trim()
                        configured = true
                        status = "Connected as $username"
                      }
                    }.onFailure { e ->
                      status = "Failed: ${e.message}"
                    }
                    loading = false
                  }
                },
                enabled = !loading,
              ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Sign in")
              }
              if (configured) {
                Button(onClick = {
                  prefs.clearCredentials()
                  configured = false
                  status = "Disconnected"
                }) {
                  Text("Sign out")
                }
              }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            if (configured) {
              Text("Host: ${prefs.serverHost() ?: "-"}  User: ${prefs.username ?: "-"}", style = MaterialTheme.typography.bodySmall)
            }
          }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Enable remote target", style = MaterialTheme.typography.titleSmall)
                Text("Appear in Jellyfin Play on list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
              }
              Switch(
                checked = enableRemote,
                onCheckedChange = {
                  prefs.enableRemote = it
                  if (it) {
                    val intent = Intent(context, JellyfinRemoteService::class.java).apply { action = JellyfinRemoteService.ACTION_START }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                  } else {
                    val intent = Intent(context, JellyfinRemoteService::class.java).apply { action = JellyfinRemoteService.ACTION_STOP }
                    context.startService(intent)
                    remoteClient.disconnect()
                  }
                },
              )
            }
            OutlinedTextField(
              value = deviceName,
              onValueChange = { if (it.length <= 32) deviceName = it },
              label = { Text("Device name") },
              placeholder = { Text(android.os.Build.MODEL) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              supportingText = { Text("${deviceName.length}/32", style = MaterialTheme.typography.labelSmall) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = {
                val trimmed = deviceName.trim().replace("\"", "'")
                prefs.deviceName = if (trimmed.isBlank()) "" else trimmed
                // trigger re-register with new name
                if (prefs.enableRemote) {
                  remoteClient.disconnect()
                  val intent = Intent(context, JellyfinRemoteService::class.java).apply { action = JellyfinRemoteService.ACTION_START }
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                }
              }) {
                Text("Save device name")
              }
              Button(onClick = {
                deviceName = ""
                prefs.deviceName = ""
              }) {
                Text("Reset")
              }
            }
            Button(onClick = {
              val intent = Intent(context, JellyfinRemoteService::class.java).apply { action = JellyfinRemoteService.ACTION_START }
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }, enabled = configured) {
              Text("Register now")
            }
            Text("Registers mpvRex as remote player via /Sessions/Capabilities and opens WebSocket. Check logcat JellyfinRemote.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
          }
        }

        Text(
          "When Jellyfin launches mpvRex via external player, the item is identified from /Videos/{id}/stream. No filename matching is used. Progress is reported every ~10s and immediately on pause/stop/exit.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
