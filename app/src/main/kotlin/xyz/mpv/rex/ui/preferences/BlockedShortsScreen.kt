package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.database.dao.ShortsMediaDao
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.CollapsibleHeaderScreen
import xyz.mpv.rex.ui.browser.shorts.ShortsScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.navigateBack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object BlockedShortsScreen : Screen {
  override val transformSourceId: String
    get() = "settings:BlockedShortsScreen"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val shortsMediaDao = koinInject<ShortsMediaDao>()
        val scope = rememberCoroutineScope()
        
        val blockedVideos by shortsMediaDao.observeAllShortsMedia().collectAsState(initial = emptyList())
        val filteredBlocked = blockedVideos.filter { it.isBlocked }

        val pageColor = MaterialTheme.colorScheme.surfaceContainer
        val cardColor = MaterialTheme.colorScheme.surface

        CollapsibleHeaderScreen(
            title = stringResource(R.string.pref_blocked_videos),
            showBackButton = true,
            onBackClick = backstack::navigateBack,
            containerColor = pageColor,
        ) { contentModifier ->
            if (filteredBlocked.isEmpty()) {
                Box(
                    modifier = contentModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.blocked_shorts_empty_state),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
                LazyColumn(
                    modifier = contentModifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
                ) {
                    items(filteredBlocked, key = { it.path }) { media ->
                        val fileName = File(media.path).name
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // Launch this video in a 'Blocked Only' shorts session
                                    backstack.add(ShortsScreen(initialVideoPath = media.path, blockedOnly = true))
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = cardColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = media.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            // Unblock: deleting the entry removes it from the 'blocked' list instantly
                                            shortsMediaDao.deleteByPath(media.path)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.Red
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(stringResource(R.string.blocked_shorts_unblock_button), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Box(
    modifier: Modifier,
    contentAlignment: Alignment,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment) {
        content()
    }
}
