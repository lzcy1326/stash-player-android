package gomeng.dev.stashplayer.feature.studios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import gomeng.dev.stashplayer.core.ui.components.StashEntityBrowseControls
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow

private const val STUDIOS_PAGE_SIZE = 40

internal data class StudioRequestKey(val profile: StashServerProfile, val query: String, val page: Int, val refresh: Int, val retry: Int, val sort: String, val direction: String, val filter: StudioFilter)

internal class StudioListViewModel : ViewModel() {
    var input by mutableStateOf("")
    var query by mutableStateOf("")
    var refresh by mutableIntStateOf(0)
    var retry by mutableIntStateOf(0)
    var requestSerial by mutableIntStateOf(0)
    var page by mutableIntStateOf(1)
    var items by mutableStateOf(emptyList<StashStudioCard>())
    var count by mutableIntStateOf(0)
    var hasMore by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf(false)
    var resetScroll by mutableStateOf(false)
    var sort by mutableStateOf("name")
    var direction by mutableStateOf("ASC")
    var randomSeed by mutableIntStateOf(nextStashRandomSortSeed())
    var filter by mutableStateOf(StudioFilter())
    var activeProfile: StashServerProfile? = null
    var lastSuccess: StudioRequestKey? = null
}

@Composable
fun StudiosRoute(
    profile: StashServerProfile?,
    modifier: Modifier = Modifier,
    mutations: StudioMutationCoordinator? = null,
    onOpenStudio: (String, Boolean) -> Unit = { _, _ -> },
    loadPage: suspend (StashServerProfile, Int, String, String, String, Map<String, Any?>?) -> StashStudioPage = { server, page, query, sort, direction, filter ->
        StashGraphQlClient(server).findStudioCardsPage(page = page, query = query, sort = sort, direction = direction, studioFilter = filter)
    },
) {
    val state: StudioListViewModel = viewModel()
    var input by state::input
    var query by state::query
    var refresh by state::refresh
    var retry by state::retry
    var requestSerial by state::requestSerial
    var page by state::page
    var items by state::items
    var count by state::count
    var hasMore by state::hasMore
    var loading by state::loading
    var error by state::error
    var resetScroll by state::resetScroll
    val gridState = rememberLazyGridState()
    val emptyStatuses = remember { MutableStateFlow<Map<String, StudioMutationStatus>>(emptyMap()) }
    val statuses by (mutations?.statuses ?: emptyStatuses).collectAsState()
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val sortOptions = listOf(
        "name" to R.string.studio_sort_name, "rating" to R.string.performer_sort_rating,
        "scenes_count" to R.string.performer_sort_scenes, "latest_scene" to R.string.performer_sort_latest,
        "updated_at" to R.string.performer_sort_updated,
        "random" to R.string.performer_sort_random,
        "created_at" to R.string.studio_sort_created, "child_count" to R.string.studio_sort_children,
        "images_count" to R.string.studio_sort_images, "galleries_count" to R.string.studio_sort_galleries,
    )
    LaunchedEffect(statuses, state.filter, state.sort) {
        val favoriteChanged = state.filter.favoriteOnly && items.any {
            statuses[it.id]?.let { status -> !status.busy && !status.error && !status.fields.favorite } == true
        }
        val ratingAffectsResults = state.sort == "rating" || state.filter.ratingMin != null || state.filter.ratingMax != null
        val ratingChanged = ratingAffectsResults && items.any { card ->
            statuses[card.id]?.let { status -> !status.busy && !status.error && status.fields.rating100 != card.rating100 } == true
        }
        if (favoriteChanged || ratingChanged) {
            page = 1
            resetScroll = true
            refresh++
        }
    }

    LaunchedEffect(profile) {
        if (state.activeProfile != profile) {
            state.activeProfile = profile
            state.lastSuccess = null
            state.filter = StudioFilter()
            showFilter = false
            page = 1
            items = emptyList()
            count = 0
            hasMore = false
            error = false
            loading = false
        }
    }
    LaunchedEffect(input) {
        delay(300)
        val nextQuery = input.trim()
        if (query != nextQuery) {
            page = 1
            items = emptyList()
            resetScroll = true
            query = nextQuery
        }
    }
    val requestKey = profile?.let { server ->
        val sort = if (state.sort == "random") "random_${state.randomSeed}" else state.sort
        StudioRequestKey(server, query, page, refresh, retry, sort, state.direction, state.filter)
    }
    LaunchedEffect(requestKey) {
        val serial = ++requestSerial
        error = false
        val key = requestKey ?: return@LaunchedEffect
        if (state.activeProfile != key.profile || page != key.page || state.filter != key.filter) return@LaunchedEffect
        if (key == state.lastSuccess) return@LaunchedEffect
        loading = true
        val requestRevision = mutations?.revision
        try {
            val result = loadPage(key.profile, key.page, key.query, key.sort, key.direction, key.filter.toGraphQl())
            currentCoroutineContext().ensureActive()
            if (requestRevision != null) mutations?.reconcile(result.studios, requestRevision)
            items = if (key.page == 1) result.studios else (items + result.studios).distinctBy { it.id }
            count = result.count
            hasMore = result.studios.isNotEmpty() && key.page * STUDIOS_PAGE_SIZE < count
            state.lastSuccess = key
            if (key.page == 1 && resetScroll) {
                gridState.requestScrollToItem(0)
                resetScroll = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (serial == requestSerial && currentCoroutineContext().isActive) error = true
        } finally {
            if (serial == requestSerial) loading = false
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.studios_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { page = 1; resetScroll = true; if (state.sort == "random") state.randomSeed = nextStashRandomSortSeed(); refresh++ }, enabled = !loading) { Text(stringResource(R.string.performers_refresh)) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            StashEntityBrowseControls(
                query = input, onQueryChange = { input = it }, searchLabel = stringResource(R.string.studio_search),
                sortLabel = stringResource(sortOptions.first { it.first == state.sort }.second), onOpenSort = { showSort = true },
                ascending = state.direction == "ASC", onToggleDirection = {
                    state.direction = if (state.direction == "ASC") "DESC" else "ASC"; page = 1; items = emptyList(); resetScroll = true
                },
                filterLabel = stringResource(R.string.studio_filter_count, state.filter.activeCount), onOpenFilter = { showFilter = true },
                sortMenu = {
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    sortOptions.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.second)) }, onClick = {
                        state.sort = option.first; state.direction = if (option.first == "name") "ASC" else "DESC"
                        if (option.first == "random") state.randomSeed = nextStashRandomSortSeed()
                        page = 1; items = emptyList(); resetScroll = true; refresh++; showSort = false
                    }) }
                }
                },
            )
        }
        StudioActiveFilters(state.filter)
        Text(stringResource(R.string.studio_count, count), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (profile == null) Text(stringResource(R.string.studio_not_connected), modifier = Modifier.padding(16.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.studio_load_error), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.performers_retry)) }
            }
        }
        if (statuses.values.any { it.error }) {
            Text(stringResource(R.string.studio_update_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp), state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("studios-grid"),
        ) {
            items(items, key = { it.id }) { studio ->
                StudioListCard(studio, profile, onOpenStudio, statuses[studio.id], mutations)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error -> Unit
                        items.isEmpty() -> Text(stringResource(R.string.studio_empty))
                        hasMore -> Button(onClick = { page++ }) { Text(stringResource(R.string.performers_load_more)) }
                    }
                }
            }
        }
    }
    if (showFilter) StudioFilterDialog(initial = state.filter, profile = profile, onApply = {
        state.filter = it; page = 1; items = emptyList(); resetScroll = true; refresh++; showFilter = false
    }, onDismiss = { showFilter = false })
}

@Composable
private fun StudioListCard(
    studio: StashStudioCard,
    profile: StashServerProfile?,
    onOpenStudio: (String, Boolean) -> Unit,
    status: StudioMutationStatus?,
    mutations: StudioMutationCoordinator?,
) {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpenStudio(studio.id, false) }) {
            StudioLogo(studio, profile, Modifier.fillMaxSize().padding(12.dp))
            val favorite = status?.fields?.favorite ?: studio.favorite
            IconButton(onClick = { mutations?.toggleFavorite(studio) }, enabled = mutations != null && status?.busy != true, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.studio_remove_favorite else R.string.studio_add_favorite),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(studio.name, modifier = Modifier.clickable { onOpenStudio(studio.id, false) }.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        studio.parent?.let { parent ->
            TextButton(onClick = { onOpenStudio(parent.id, false) }, contentPadding = PaddingValues(0.dp)) {
                Text(parent.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onOpenStudio(studio.id, true) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.performers_scene_count, studio.sceneCount), style = MaterialTheme.typography.bodySmall)
            }
            if (studio.children.isNotEmpty()) TextButton(onClick = { onOpenStudio(studio.id, false) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.studio_children, studio.children.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
