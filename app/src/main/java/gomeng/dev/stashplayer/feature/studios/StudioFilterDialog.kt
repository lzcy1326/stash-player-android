package gomeng.dev.stashplayer.feature.studios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashStudioReference
import gomeng.dev.stashplayer.core.ui.components.StashTagFilterSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioFilterDialog(initial: StudioFilter, profile: StashServerProfile?, onApply: (StudioFilter) -> Unit, onDismiss: () -> Unit) {
    var favorite by remember { mutableStateOf(initial.favoriteOnly) }
    var tags by remember { mutableStateOf(initial.tags) }
    var parent by remember { mutableStateOf(initial.parent) }
    var organized by remember { mutableStateOf(initial.organized) }
    var ratingMin by remember { mutableStateOf(initial.ratingMin?.toString().orEmpty()) }
    var ratingMax by remember { mutableStateOf(initial.ratingMax?.toString().orEmpty()) }
    var sceneMin by remember { mutableStateOf(initial.sceneMin?.toString().orEmpty()) }
    var sceneMax by remember { mutableStateOf(initial.sceneMax?.toString().orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var tagQuery by remember { mutableStateOf("") }
    var tagOptions by remember { mutableStateOf(emptyList<StashSelectedTag>()) }
    var parentQuery by remember { mutableStateOf("") }
    var parents by remember { mutableStateOf(emptyList<StashStudioReference>()) }
    var showParents by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(showTags, showParents, tagQuery, parentQuery, profile, retry) {
        error = false
        loading = false
        val active = profile ?: return@LaunchedEffect
        if (!showTags && !showParents) return@LaunchedEffect
        loading = true
        try {
            delay(300)
            val client = StashGraphQlClient(active)
            if (showTags) {
                val result = client.findTags(query = tagQuery)
                currentCoroutineContext().ensureActive()
                tagOptions = result
            } else {
                val result = client.findStudioCardsPage(query = parentQuery, perPage = 100)
                currentCoroutineContext().ensureActive()
                parents = result.studios.map { StashStudioReference(it.id, it.name) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.studio_filter_title), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(favorite, { favorite = it })
                    Text(stringResource(R.string.studio_favorites))
                }
                StudioRange(R.string.performer_filter_rating, ratingMin, { ratingMin = it }, ratingMax, { ratingMax = it })
                StudioRange(R.string.performer_filter_scenes, sceneMin, { sceneMin = it }, sceneMax, { sceneMax = it })
                TextButton(onClick = { showTags = true; showParents = false }) { Text(stringResource(R.string.performer_filter_tags, tags.size)) }
                TextButton(onClick = { showParents = !showParents }) {
                    Text(stringResource(R.string.studio_parent) + ": " + (parent?.name ?: stringResource(R.string.performer_filter_any)))
                }
                if (showParents) {
                    OutlinedTextField(parentQuery, { parentQuery = it }, label = { Text(stringResource(R.string.studio_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.performers_retry)) }
                    TextButton(onClick = { parent = null; showParents = false }) { Text(stringResource(R.string.performer_filter_any)) }
                    parents.forEach { option -> TextButton(onClick = { parent = option; showParents = false }) { Text(option.name) } }
                }
                Text(stringResource(R.string.studio_organized))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(null to R.string.performer_filter_any, true to R.string.studio_yes, false to R.string.studio_no).forEach { (value, label) ->
                        FilterChip(selected = organized == value, onClick = { organized = value }, label = { Text(stringResource(label)) })
                    }
                }
                if (invalid) Text(stringResource(R.string.performer_filter_invalid), color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onApply(StudioFilter()) }) { Text(stringResource(R.string.performer_filter_clear)) }
                TextButton(onClick = {
                    fun valid(a: String, b: String, max: Int) = listOf(a,b).all { it.isBlank() || it.toIntOrNull()?.let { n -> n in 0..max } == true } &&
                        (a.isBlank() || b.isBlank() || (a.toIntOrNull() ?: 0) <= (b.toIntOrNull() ?: 0))
                    if (!valid(ratingMin, ratingMax, 100) || !valid(sceneMin, sceneMax, 999999)) invalid = true
                    else onApply(StudioFilter(favorite, ratingMin.toIntOrNull(), ratingMax.toIntOrNull(), sceneMin.toIntOrNull(), sceneMax.toIntOrNull(), tags, parent, organized))
                }) { Text(stringResource(R.string.performer_filter_apply)) }
            }
        }
    }
    if (showTags) StashTagFilterSheet(
        videoFilter = StashVideoFilterState(tags = tags), tagQuery = tagQuery, tagOptions = tagOptions,
        isLoading = loading, error = if (error) stringResource(R.string.performer_tags_error) else null,
        onTagQueryChange = { tagQuery = it }, onApplyTags = { tags = it; showTags = false },
        onRetry = { retry++ }, onDismiss = { showTags = false },
    )
}

@Composable
private fun StudioRange(label: Int, min: String, onMin: (String) -> Unit, max: String, onMax: (String) -> Unit) {
    Text(stringResource(label))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(min, onMin, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.performer_filter_min)) }, singleLine = true)
        OutlinedTextField(max, onMax, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.performer_filter_max)) }, singleLine = true)
    }
}
