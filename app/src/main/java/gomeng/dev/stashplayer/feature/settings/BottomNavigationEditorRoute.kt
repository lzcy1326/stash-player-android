package gomeng.dev.stashplayer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.app.navigation.*
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import kotlinx.coroutines.launch

private fun NavigationFeature.label(): Int = when (this) {
    NavigationFeature.Explore -> R.string.navigation_explore_label
    NavigationFeature.Shorts -> R.string.navigation_shorts_label
    NavigationFeature.Gallery -> R.string.navigation_gallery_label
    NavigationFeature.Queue -> R.string.navigation_queue_label
    NavigationFeature.Performers -> R.string.navigation_performers_label
    NavigationFeature.Studios -> R.string.navigation_studios_label
}

private fun NavigationFeature.icon(): ImageVector = when (this) {
    NavigationFeature.Explore -> Icons.Outlined.VideoLibrary
    NavigationFeature.Shorts -> Icons.Outlined.PlayCircle
    NavigationFeature.Gallery -> Icons.Outlined.PhotoLibrary
    NavigationFeature.Queue -> Icons.AutoMirrored.Outlined.PlaylistPlay
    NavigationFeature.Performers -> Icons.Outlined.PersonOutline
    NavigationFeature.Studios -> Icons.Outlined.Videocam
}

@Composable
fun BottomNavigationEditorRoute(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { StashSettingsRepository(context) }
    val saved by repository.bottomNavigationFeatures.collectAsState(initial = null)
    val initial = saved ?: return
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.navigation_editor_save_error)
    BottomNavigationEditorScreen(initial, saving, snackbar, onNavigateBack) { selection ->
        if (!saving) {
            saving = true
            scope.launch {
                try {
                    if (repository.setBottomNavigationFeatures(selection)) onNavigateBack()
                    else snackbar.showSnackbar(saveError)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    snackbar.showSnackbar(saveError)
                } finally { saving = false }
            }
        }
    }
}

@Composable
internal fun BottomNavigationEditorScreen(
    initial: List<NavigationFeature>,
    saving: Boolean,
    snackbar: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSave: (List<NavigationFeature>) -> Unit,
) {
    var ids by rememberSaveable { mutableStateOf(initial.map { it.id }) }
    val selected = ids.mapNotNull { id -> navigationFeaturePool.firstOrNull { it.id == id } }
    val candidates = navigationFeaturePool.filterNot { it in selected }
    val scope = rememberCoroutineScope()
    val limitMessage = stringResource(R.string.navigation_editor_limit)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.settings_back_button)) }
                Text(stringResource(R.string.navigation_editor_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onSave(selected) }, enabled = isValidNavigationFeatures(selected) && !saving) {
                    Text(stringResource(R.string.navigation_editor_done))
                }
            }
            Text(stringResource(R.string.navigation_editor_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.navigation_editor_current), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    FixedNavigationItem(Icons.Outlined.Home, stringResource(R.string.navigation_home_label), Modifier.weight(1f))
                    repeat(MAX_NAVIGATION_FEATURES) { index ->
                        val feature = selected.getOrNull(index)
                        if (feature == null) {
                            Column(Modifier.weight(1f).height(90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.navigation_editor_empty), style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(feature.icon(), null, Modifier.padding(top = 14.dp, end = 10.dp).size(28.dp))
                                    val removeDescription = stringResource(R.string.navigation_editor_remove, stringResource(feature.label()))
                                    IconButton(onClick = { ids = ids - feature.id }, modifier = Modifier.size(48.dp).semantics { contentDescription = removeDescription }) {
                                        Text("−", Modifier.background(MaterialTheme.colorScheme.error, CircleShape).padding(horizontal = 5.dp), color = MaterialTheme.colorScheme.onError)
                                    }
                                }
                                Text(stringResource(feature.label()), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    FixedNavigationItem(Icons.Outlined.Settings, stringResource(R.string.navigation_settings_label), Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.navigation_editor_available), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    candidates.forEach { feature ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(feature.icon(), null, Modifier.padding(horizontal = 12.dp))
                            Text(stringResource(feature.label()), Modifier.weight(1f))
                            val addDescription = stringResource(R.string.navigation_editor_add, stringResource(feature.label()))
                            IconButton(modifier = Modifier.semantics { contentDescription = addDescription }, onClick = {
                                when (val result = addNavigationFeature(selected, feature)) {
                                    is AddNavigationFeatureResult.Added -> ids = result.features.map { it.id }
                                    AddNavigationFeatureResult.AtCapacity -> scope.launch { snackbar.showSnackbar(limitMessage) }
                                    AddNavigationFeatureResult.AlreadySelected -> Unit
                                }
                            }) { Text("+", Modifier.background(Color(0xFF4ADE80), CircleShape).padding(horizontal = 5.dp), color = Color.Black) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedNavigationItem(icon: ImageVector, label: String, modifier: Modifier) {
    Column(modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(stringResource(R.string.navigation_editor_fixed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
