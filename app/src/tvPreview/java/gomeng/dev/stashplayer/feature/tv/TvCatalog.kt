package gomeng.dev.stashplayer.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class TvSection { Videos, Performers, Studios }

internal data class TvEntityScope(
    val section: TvSection,
    val id: String,
    val name: String,
    val includeChildren: Boolean = false,
)

private data class TvEntity(
    val id: String,
    val name: String,
    val image: String?,
    val sceneCount: Int,
    val favorite: Boolean,
    val rating: Int?,
)

@Composable
internal fun TvHome(profile: StashServerProfile, onSettings: () -> Unit) {
    var section by remember { mutableStateOf(TvSection.Videos) }
    when (section) {
        TvSection.Videos -> TvLibrary(profile, onSettings, onSelectTab = { section = it })
        TvSection.Performers, TvSection.Studios -> TvEntityCatalog(profile, section, onSettings) { section = it }
    }
}

@Composable
internal fun TvSectionNavigation(selected: TvSection, onSelect: (TvSection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (section in TvSection.entries) {
            val label = stringResource(when (section) {
                TvSection.Videos -> R.string.tv_library
                TvSection.Performers -> R.string.tv_performers
                TvSection.Studios -> R.string.tv_studios
            })
            TvButton(if (selected == section) "• $label" else label, { onSelect(section) })
        }
    }
}

@Composable
private fun TvEntityCatalog(profile: StashServerProfile, section: TvSection, onSettings: () -> Unit, onSelectTab: (TvSection) -> Unit) {
    val client = remember(profile) { StashGraphQlClient(profile) }
    val grid = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    var draft by remember(section) { mutableStateOf("") }
    var query by remember(section) { mutableStateOf("") }
    var items by remember(section) { mutableStateOf(emptyList<TvEntity>()) }
    var page by remember(section) { mutableIntStateOf(1) }
    var total by remember(section) { mutableIntStateOf(0) }
    var reload by remember(section) { mutableIntStateOf(0) }
    var loading by remember(section) { mutableStateOf(true) }
    var failed by remember(section) { mutableStateOf(false) }
    var selected by remember(section) { mutableStateOf<TvEntity?>(null) }
    var focusItem by remember(section) { mutableStateOf<String?>(null) }

    fun search(value: String) {
        keyboard?.hide()
        query = value.trim()
        items = emptyList()
        page = 1
        total = 0
        focusItem = null
        loading = true
        reload++
    }

    LaunchedEffect(section, page, reload) {
        loading = true
        failed = false
        try {
            val (incoming, count) = when (section) {
                TvSection.Performers -> client.findPerformerCardsPage(page, 24, query.ifBlank { null }).let { result ->
                    result.performers.map { TvEntity(it.id, it.name, it.imagePath, it.sceneCount, it.favorite, it.rating100) } to result.count
                }
                TvSection.Studios -> client.findStudioCardsPage(page, 24, query.ifBlank { null }).let { result ->
                    result.studios.map { TvEntity(it.id, it.name, it.imagePath, it.sceneCount, it.favorite, it.rating100) } to result.count
                }
                TvSection.Videos -> error("Unsupported section")
            }
            val ids = items.mapTo(mutableSetOf()) { it.id }
            val added = incoming.filter { ids.add(it.id) }
            items = items + added
            total = if (incoming.size < 24 || added.isEmpty()) items.size else count
            if (page == 1) grid.scrollToItem(0)
            focusItem = added.firstOrNull()?.id
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    val current = selected
    if (current != null) {
        TvEntityDetail(client, profile, section, current, onSettings,
            onUpdate = { updated -> items = items.map { if (it.id == updated.id) updated else it }; selected = updated },
            onBack = { focusItem = current.id; selected = null },
        )
        return
    }
    LaunchedEffect(focusItem) {
        val index = items.indexOfFirst { it.id == focusItem }
        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.key == focusItem }) grid.scrollToItem(index)
    }
    BackHandler(enabled = query.isNotBlank()) { draft = ""; search("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvSectionNavigation(section, onSelectTab)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                label = { Text(stringResource(if (section == TvSection.Performers) R.string.tv_search_performers else R.string.tv_search_studios)) },
                modifier = Modifier.weight(1f).onTvTextFieldKeys({ keyboard?.show() }, onDown = { focusManager.moveFocus(FocusDirection.Down) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchFocus.requestFocus(); search(draft) }),
            )
            TvButton(stringResource(R.string.tv_submit_search), { search(draft) }, Modifier.focusRequester(searchFocus))
            TvButton(stringResource(R.string.tv_server), onSettings)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp), state = grid, modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { entity ->
                TvEntityCard(profile, entity, section, focusItem == entity.id,
                    onFocused = { if (focusItem == entity.id) focusItem = null }, onOpen = { selected = entity })
            }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                val retryFocus = remember { FocusRequester() }
                LaunchedEffect(failed, loading, items.isEmpty()) {
                    if (failed && items.isEmpty()) retryFocus.requestFocus()
                    else if (!loading && items.isEmpty()) searchFocus.requestFocus()
                }
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        failed -> { Text(stringResource(R.string.tv_catalog_error)); TvButton(stringResource(R.string.tv_retry), { reload++ }, Modifier.focusRequester(retryFocus)) }
                        loading -> Text(stringResource(R.string.tv_loading))
                        items.isEmpty() -> Text(stringResource(R.string.tv_catalog_empty))
                        items.size < total -> TvButton(stringResource(R.string.tv_load_more), { page++ })
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEntityCard(profile: StashServerProfile, entity: TvEntity, section: TvSection, requestFocus: Boolean, onFocused: () -> Unit, onOpen: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            delay(100)
            requester.requestFocus()
        }
    }
    Column(Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused; if (focused && requestFocus) onFocused() }
        .clip(shape).background(if (focused) colors.primary else colors.surface)
        .border(2.dp, if (focused) Color.White else Color.Transparent, shape).clickable(onClick = onOpen).padding(6.dp)) {
        TvEntityImage(profile, entity.image, Modifier.fillMaxWidth().aspectRatio(if (section == TvSection.Performers) 0.8f else 1.5f))
        Text(entity.name, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), color = if (focused) colors.onPrimary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.tv_scene_count, entity.sceneCount), Modifier.padding(horizontal = 4.dp), color = if (focused) colors.onPrimary else colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TvEntityImage(profile: StashServerProfile, url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.background)) {
        url?.takeIf { it.isNotBlank() }?.let {
            val image = remember(profile, it, context) {
                val absolute = profile.absoluteUrl(it)
                ImageRequest.Builder(context).data(absolute).apply {
                    profile.authHeadersFor(absolute).forEach { (name, value) -> addHeader(name, value) }
                }.build()
            }
            AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun TvEntityDetail(client: StashGraphQlClient, profile: StashServerProfile, section: TvSection, entity: TvEntity,
    onSettings: () -> Unit, onUpdate: (TvEntity) -> Unit, onBack: () -> Unit) {
    var showScenes by remember(entity.id) { mutableStateOf(false) }
    var includeChildren by remember(entity.id) { mutableStateOf(false) }
    var description by remember(entity.id) { mutableStateOf<String?>(null) }
    var extra by remember(entity.id) { mutableStateOf<String?>(null) }
    var error by remember(entity.id) { mutableStateOf(false) }
    var loading by remember(entity.id) { mutableStateOf(true) }
    var busy by remember(entity.id) { mutableStateOf(false) }
    var reload by remember(entity.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scenesFocus = remember { FocusRequester() }

    fun updateRating(rating: Int?) {
        if (busy || loading) return
        busy = true
        error = false
        scope.launch {
            try {
                val saved = if (section == TvSection.Performers) client.updatePerformerRating(entity.id, rating)
                    .rating100 else client.updateStudioRating(entity.id, rating).rating100
                onUpdate(entity.copy(rating = saved))
            } catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true } finally { busy = false }
        }
    }

    LaunchedEffect(entity.id, reload) {
        loading = true; error = false
        try {
            if (section == TvSection.Performers) {
                val detail = requireNotNull(client.findPerformerDetail(entity.id))
                description = detail.details
                extra = listOfNotNull(detail.card.country, detail.card.birthdate, detail.heightCm?.let { "$it cm" }).joinToString(" · ")
                onUpdate(entity.copy(name = detail.card.name, image = detail.card.imagePath, sceneCount = detail.card.sceneCount, favorite = detail.card.favorite, rating = detail.card.rating100))
            } else {
                val detail = requireNotNull(client.findStudioDetail(entity.id))
                description = detail.details
                extra = detail.parent?.name
                onUpdate(entity.copy(name = detail.name, image = detail.imagePath, sceneCount = detail.sceneCount, favorite = detail.favorite, rating = detail.rating100))
            }
        } catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true } finally { loading = false }
    }
    if (showScenes) {
        TvLibrary(profile, onSettings, scope = TvEntityScope(section, entity.id, entity.name, includeChildren), onExitScope = { showScenes = false })
        return
    }
    LaunchedEffect(entity.id, showScenes) { scenesFocus.requestFocus() }
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        TvEntityImage(profile, entity.image, Modifier.weight(0.32f).aspectRatio(if (section == TvSection.Performers) 0.8f else 1.5f))
        Column(Modifier.weight(0.68f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entity.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.tv_scene_count, entity.sceneCount) + (entity.rating?.let { " · " + stringResource(R.string.tv_detail_rating, it) } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(stringResource(R.string.tv_view_scenes), { showScenes = true }, Modifier.focusRequester(scenesFocus))
                TvButton(stringResource(if (entity.favorite) R.string.tv_unfavorite else R.string.tv_favorite), {
                    if (!busy) {
                        busy = true; error = false
                        scope.launch {
                            try {
                                val favorite = if (section == TvSection.Performers) client.updatePerformerFavorite(entity.id, !entity.favorite).favorite else client.updateStudioFavorite(entity.id, !entity.favorite).favorite
                                onUpdate(entity.copy(favorite = favorite))
                            } catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true } finally { busy = false }
                        }
                    }
                }, enabled = !busy && !loading)
                TvButton(stringResource(R.string.tv_back), onBack)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(stringResource(R.string.tv_rating_down), { updateRating(((entity.rating ?: 0) - 10).coerceAtLeast(0)) }, enabled = !busy && !loading)
                TvButton(stringResource(R.string.tv_rating_up), { updateRating(((entity.rating ?: 0) + 10).coerceAtMost(100)) }, enabled = !busy && !loading)
                TvButton(stringResource(R.string.tv_rating_clear), { updateRating(null) }, enabled = !busy && !loading && entity.rating != null)
            }
            if (section == TvSection.Studios) TvButton(stringResource(if (includeChildren) R.string.tv_include_children_on else R.string.tv_include_children_off), { includeChildren = !includeChildren })
            if (loading) Text(stringResource(R.string.tv_loading))
            if (error) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tv_catalog_error)); TvButton(stringResource(R.string.tv_retry), { reload++ })
            }
            extra?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            description?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
