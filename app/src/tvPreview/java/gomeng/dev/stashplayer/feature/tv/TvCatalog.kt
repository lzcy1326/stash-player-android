package gomeng.dev.stashplayer.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class TvSection { Videos, Performers, Studios }

internal data class TvEntityScope(
    val section: TvSection,
    val id: String,
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
    val navigation: @Composable () -> Unit = { TvSectionNavigation(section, { section = it }, onSettings) }
    when (section) {
        TvSection.Videos -> TvLibrary(profile, navigation = navigation)
        TvSection.Performers, TvSection.Studios -> TvEntityCatalog(profile, section, navigation)
    }
}

@Composable
private fun TvSectionNavigation(selected: TvSection, onSelect: (TvSection) -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Stash Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp))
        for (section in TvSection.entries) {
            val label = stringResource(when (section) {
                TvSection.Videos -> R.string.tv_library
                TvSection.Performers -> R.string.tv_performers
                TvSection.Studios -> R.string.tv_studios
            })
            var focused by remember { mutableStateOf(false) }
            val active = selected == section
            val shape = RoundedCornerShape(20.dp)
            Text(
                label,
                Modifier.onFocusChanged { focused = it.isFocused }
                    .semantics { this.selected = active }
                    .clip(shape)
                    .background(if (active) colors.primary else Color.Transparent)
                    .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
                    .clickable(role = Role.Tab) { onSelect(section) }
                    .padding(horizontal = 19.dp, vertical = 9.dp),
                color = if (active) colors.onPrimary else colors.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Box(Modifier.weight(1f))
        TvButton(stringResource(R.string.tv_server), onClick = onSettings)
    }
}

@Composable
private fun TvEntityCatalog(profile: StashServerProfile, section: TvSection, navigation: @Composable () -> Unit) {
    val client = remember(profile) { StashGraphQlClient(profile) }
    val grid = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    // Keep every page a whole number of rows so the last row is never half filled.
    val configuration = LocalConfiguration.current
    val columns = remember(configuration.screenWidthDp) {
        tvGridColumnCount(configuration.screenWidthDp.dp - TvSideInset * 2 - TvGridContentPadding)
    }
    val pageSize = columns * TvGridRowsPerPage
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

    LaunchedEffect(section, page, reload, pageSize) {
        loading = true
        failed = false
        try {
            val (incoming, count) = when (section) {
                TvSection.Performers -> client.findPerformerCardsPage(page, pageSize, query.ifBlank { null }).let { result ->
                    result.performers.map { TvEntity(it.id, it.name, it.imagePath, it.sceneCount, it.favorite, it.rating100) } to result.count
                }
                TvSection.Studios -> client.findStudioCardsPage(page, pageSize, query.ifBlank { null }).let { result ->
                    result.studios.map { TvEntity(it.id, it.name, it.imagePath, it.sceneCount, it.favorite, it.rating100) } to result.count
                }
                TvSection.Videos -> error("Unsupported section")
            }
            val ids = items.mapTo(mutableSetOf()) { it.id }
            val added = incoming.filter { ids.add(it.id) }
            items = items + added
            total = if (incoming.size < pageSize || added.isEmpty()) items.size else count
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
        TvEntityDetail(client, profile, section, current,
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
    Column(Modifier.fillMaxSize().padding(horizontal = TvSideInset, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        navigation()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (section == TvSection.Performers) R.string.tv_performers else R.string.tv_studios),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                label = { Text(stringResource(if (section == TvSection.Performers) R.string.tv_search_performers else R.string.tv_search_studios)) },
                modifier = Modifier.widthIn(max = 232.dp).onTvTextFieldKeys({ keyboard?.show() }, onDown = { focusManager.moveFocus(FocusDirection.Down) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchFocus.requestFocus(); search(draft) }),
            )
            TvButton(stringResource(R.string.tv_submit_search), { search(draft) }, Modifier.focusRequester(searchFocus))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns), state = grid, modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(TvTileSpacing), horizontalArrangement = Arrangement.spacedBy(TvTileSpacing),
        ) {
            items(items, key = { it.id }) { entity ->
                TvBrowseTile(
                    profile = profile,
                    image = entity.image,
                    title = entity.name,
                    subtitle = stringResource(R.string.tv_scene_count, entity.sceneCount),
                    requestFocus = focusItem == entity.id,
                    onFocused = { if (focusItem == entity.id) focusItem = null },
                    onOpen = { selected = entity },
                    aspectRatio = if (section == TvSection.Performers) 1f else 16f / 9f,
                    contentScale = if (section == TvSection.Performers) ContentScale.Crop else ContentScale.Fit,
                )
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
private fun TvEntityDetail(
    client: StashGraphQlClient,
    profile: StashServerProfile,
    section: TvSection,
    entity: TvEntity,
    onUpdate: (TvEntity) -> Unit,
    onBack: () -> Unit,
) {
    var includeChildren by remember(entity.id) { mutableStateOf(false) }
    var description by remember(entity.id) { mutableStateOf<String?>(null) }
    var extra by remember(entity.id) { mutableStateOf<String?>(null) }
    var error by remember(entity.id) { mutableStateOf(false) }
    var loading by remember(entity.id) { mutableStateOf(true) }
    var busy by remember(entity.id) { mutableStateOf(false) }
    var reload by remember(entity.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var initialFocusApplied by remember(entity.id) { mutableStateOf(false) }

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
    TvLibrary(
        profile,
        scope = TvEntityScope(section, entity.id, includeChildren),
        onExitScope = onBack,
        entityHeader = {
            val backFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (!initialFocusApplied) {
                    backFocus.requestFocus()
                    initialFocusApplied = true
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvArtwork(
                        profile, entity.image,
                        Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)),
                        if (section == TvSection.Performers) ContentScale.Crop else ContentScale.Fit,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            entity.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val summary = listOfNotNull(
                            stringResource(R.string.tv_scene_count, entity.sceneCount),
                            entity.rating?.let { stringResource(R.string.tv_detail_rating, it) },
                            extra?.takeIf { it.isNotBlank() },
                        )
                        Text(summary.joinToString("  /  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TvButton(stringResource(R.string.tv_back), onBack, Modifier.focusRequester(backFocus))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(stringResource(if (entity.favorite) R.string.tv_unfavorite else R.string.tv_favorite), {
                        if (!busy) {
                            busy = true
                            error = false
                            scope.launch {
                                try {
                                    val favorite = if (section == TvSection.Performers) {
                                        client.updatePerformerFavorite(entity.id, !entity.favorite).favorite
                                    } else {
                                        client.updateStudioFavorite(entity.id, !entity.favorite).favorite
                                    }
                                    onUpdate(entity.copy(favorite = favorite))
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    error = true
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }, enabled = !busy && !loading)
                    TvButton(stringResource(R.string.tv_rating_down), { updateRating(((entity.rating ?: 0) - 10).coerceAtLeast(0)) }, enabled = !busy && !loading)
                    TvButton(stringResource(R.string.tv_rating_up), { updateRating(((entity.rating ?: 0) + 10).coerceAtMost(100)) }, enabled = !busy && !loading)
                    TvButton(stringResource(R.string.tv_rating_clear), { updateRating(null) }, enabled = !busy && !loading && entity.rating != null)
                    if (section == TvSection.Studios) {
                        TvButton(
                            stringResource(if (includeChildren) R.string.tv_include_children_on else R.string.tv_include_children_off),
                            { includeChildren = !includeChildren },
                        )
                    }
                }
                if (error) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tv_catalog_error))
                    TvButton(stringResource(R.string.tv_retry), { reload++ })
                }
                Text(stringResource(R.string.tv_related_videos), style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}
