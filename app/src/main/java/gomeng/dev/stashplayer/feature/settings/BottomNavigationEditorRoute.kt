package gomeng.dev.stashplayer.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
    val moveLeft = stringResource(R.string.navigation_editor_move_left)
    val moveRight = stringResource(R.string.navigation_editor_move_right)
    val haptic = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragStartIds by remember { mutableStateOf<List<String>?>(null) }
    val currentSelection by rememberUpdatedState(selected)
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slotWidthPx = with(LocalDensity.current) { maxWidth.toPx() / 6f }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    FixedNavigationItem(Icons.Outlined.Home, stringResource(R.string.navigation_home_label), Modifier.weight(1f))
                    selected.forEachIndexed { index, feature ->
                            key(feature.id) {
                            val isDragging = draggingId == feature.id
                            val releaseOffset = remember { Animatable(0f) }
                            val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, spring(), label = "navigation drag scale")
                            val animatedPosition by animateFloatAsState(
                                targetValue = index * slotWidthPx,
                                animationSpec = if (isDragging) snap() else spring(),
                                label = "navigation item position",
                            )
                            val actions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveLeft) {
                                    ids = moveNavigationFeature(selected, index, index - 1).map { it.id }; true
                                })
                                if (index < selected.lastIndex) add(CustomAccessibilityAction(moveRight) {
                                    ids = moveNavigationFeature(selected, index, index + 1).map { it.id }; true
                                })
                            }
                            val systemViewConfiguration = LocalViewConfiguration.current
                            val dragViewConfiguration = remember(systemViewConfiguration) {
                                object : ViewConfiguration by systemViewConfiguration {
                                    override val longPressTimeoutMillis: Long = 180L
                                }
                            }
                            CompositionLocalProvider(LocalViewConfiguration provides dragViewConfiguration) {
                            val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(
                                visibleState = visibility,
                                modifier = Modifier.weight(1f),
                                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 5 },
                            ) {
                            Column(
                                Modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)
                                    .offset { IntOffset(if (isDragging) dragOffset.roundToInt() else (animatedPosition - index * slotWidthPx + releaseOffset.value).roundToInt(), 0) }
                                    .scale(scale)
                                    .shadow(if (isDragging) 6.dp else 0.dp)
                                    .semantics { customActions = actions }
                                    .pointerInput(feature.id, slotWidthPx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingId = feature.id
                                                dragOffset = 0f
                                                dragStartIds = currentSelection.map { it.id }
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                val releasedAt = dragOffset
                                                scope.launch {
                                                    releaseOffset.snapTo(releasedAt)
                                                    draggingId = null
                                                    dragOffset = 0f
                                                    dragStartIds = null
                                                    releaseOffset.animateTo(0f, spring())
                                                }
                                            },
                                            onDragCancel = {
                                                dragStartIds?.let { ids = it }
                                                draggingId = null
                                                dragOffset = 0f
                                                dragStartIds = null
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.x
                                                val now = currentSelection
                                                val from = now.indexOf(feature)
                                                if (from >= 0 && slotWidthPx > 0f) {
                                                    val delta = (dragOffset / slotWidthPx).roundToInt()
                                                    val target = (from + delta).coerceIn(0, now.lastIndex)
                                                    if (target != from) {
                                                        ids = moveNavigationFeature(now, from, target).map { it.id }
                                                        dragOffset -= (target - from) * slotWidthPx
                                                    }
                                                    dragOffset = dragOffset.coerceIn(-slotWidthPx * from, slotWidthPx * (now.lastIndex - from))
                                                }
                                            },
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
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
                            }
                    }
                    repeat(MAX_NAVIGATION_FEATURES - selected.size) {
                        Column(Modifier.weight(1f).height(90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.navigation_editor_empty), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    FixedNavigationItem(Icons.Outlined.Settings, stringResource(R.string.navigation_settings_label), Modifier.weight(1f))
                }
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
