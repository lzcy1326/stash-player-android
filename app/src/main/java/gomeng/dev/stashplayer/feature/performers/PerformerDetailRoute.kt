package gomeng.dev.stashplayer.feature.performers

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.formatGalleryFileSize
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.model.StashMediaFormatFilter
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import gomeng.dev.stashplayer.core.model.defaultStashExploreSortOptions
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.performerAge
import gomeng.dev.stashplayer.core.network.performerCountryFlag
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import gomeng.dev.stashplayer.core.player.ratingStepForStashRating100
import gomeng.dev.stashplayer.core.player.stashRating100ForRatingStep
import gomeng.dev.stashplayer.core.ui.components.SceneCard
import gomeng.dev.stashplayer.core.ui.components.StashStarRatingSlider
import gomeng.dev.stashplayer.core.ui.components.StashDateDurationPlaybackFilterSheet
import gomeng.dev.stashplayer.core.ui.components.StashRatingMediaFormatFilterSheet
import gomeng.dev.stashplayer.core.ui.components.StashTagFilterSheet
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel

private const val PERFORMER_SCENE_PAGE_SIZE = 12

@Composable
fun PerformerDetailRoute(
    performerId: String,
    showScenes: Boolean,
    profile: StashServerProfile?,
    onBack: () -> Unit,
    mutations: PerformerMutationCoordinator? = null,
    onOpenScene: (String, List<SceneCardModel>, Boolean, PlayerPlaybackQueueContinuation?) -> Unit = { _, _, _, _ -> },
    loadDetail: suspend (StashServerProfile, String) -> StashPerformerDetail? = { server, id ->
        StashGraphQlClient(server).findPerformerDetail(id)
    },
    loadSummary: suspend (StashServerProfile, String) -> StashPerformerSceneSummary = { server, id ->
        StashGraphQlClient(server).findPerformerSceneSummary(id)
    },
    loadScenes: suspend (StashServerProfile, String, Int, String, String, StashSortDirection, StashVideoFilterState) -> StashSceneCardPage = { server, id, page, query, sort, direction, filter ->
        StashGraphQlClient(server).findSceneCardsPage(
            perPage = PERFORMER_SCENE_PAGE_SIZE, page = page, query = query,
            sort = sort, direction = direction, videoFilter = filter, performerId = id,
        )
    },
) {
    var detail by remember(profile, performerId) { mutableStateOf<StashPerformerDetail?>(null) }
    var summary by remember(profile, performerId) { mutableStateOf<StashPerformerSceneSummary?>(null) }
    var detailError by remember(profile, performerId) { mutableStateOf(false) }
    var summaryError by remember(profile, performerId) { mutableStateOf(false) }
    var detailRetry by remember { mutableIntStateOf(0) }
    var summaryRetry by remember { mutableIntStateOf(0) }
    var infoExpanded by rememberSaveable(performerId) { mutableStateOf(!showScenes) }
    var detailsExpanded by rememberSaveable(performerId) { mutableStateOf(false) }
    var aliasesExpanded by rememberSaveable(performerId) { mutableStateOf(false) }
    var imageOpen by rememberSaveable(performerId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val emptyStatuses = remember { MutableStateFlow<Map<String, PerformerMutationStatus>>(emptyMap()) }
    val mutationStatuses by (mutations?.statuses ?: emptyStatuses).collectAsState()
    val mutationStatus = mutationStatuses[performerId]
    val scenesState: PerformerScenesViewModel = viewModel()
    val context = LocalContext.current
    val localRepository = remember(context) { StashLocalLibraryRepository(context) }
    val favorites by localRepository.favoriteSceneIds.collectAsState(initial = emptySet())
    val watchLater by localRepository.watchLaterSceneIds.collectAsState(initial = emptySet())
    val queue by localRepository.queueSceneIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var sortOpen by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf("") }
    var tagQuery by remember { mutableStateOf("") }
    var tagOptions by remember { mutableStateOf(emptyList<StashSelectedTag>()) }
    var tagsLoading by remember { mutableStateOf(false) }
    var tagsError by remember { mutableStateOf(false) }
    var tagsRetry by remember { mutableIntStateOf(0) }
    val sortOptions = defaultStashExploreSortOptions()

    fun updateFilter(filter: StashVideoFilterState) {
        scenesState.page = 1
        scenesState.scenes = emptyList()
        scenesState.filter = filter
        scenesState.reload++
    }

    LaunchedEffect(filterSheet, tagQuery, profile, tagsRetry) {
        val active = profile ?: return@LaunchedEffect
        if (filterSheet != "tags") return@LaunchedEffect
        delay(300)
        tagsLoading = true
        tagsError = false
        try {
            val result = StashGraphQlClient(active).findTags(query = tagQuery)
            currentCoroutineContext().ensureActive()
            tagOptions = result
        } catch (e: CancellationException) { throw e } catch (_: Exception) { tagsError = true }
        finally { tagsLoading = false }
    }

    LaunchedEffect(profile, performerId) {
        if (scenesState.activeProfile != profile || scenesState.activePerformerId != performerId) {
            scenesState.activeProfile = profile
            scenesState.activePerformerId = performerId
            scenesState.scenes = emptyList()
            scenesState.page = 1
            scenesState.lastSuccess = null
        }
    }
    LaunchedEffect(scenesState.input) {
        delay(300)
        val next = scenesState.input.trim()
        if (next != scenesState.query) {
            scenesState.page = 1
            scenesState.scenes = emptyList()
            scenesState.query = next
        }
    }
    LaunchedEffect(profile, performerId, scenesState.query, scenesState.sort, scenesState.direction, scenesState.filter, scenesState.page, scenesState.reload) {
        val active = profile ?: return@LaunchedEffect
        val request = PerformerScenesRequest(active, performerId, scenesState.query, scenesState.sort, scenesState.direction, scenesState.filter, scenesState.page, scenesState.reload)
        val serial = ++scenesState.requestSerial
        if (request == scenesState.lastSuccess) return@LaunchedEffect
        scenesState.loading = true
        scenesState.error = false
        try {
            val result = loadScenes(active, performerId, request.page, request.query, request.sort, request.direction, request.filter)
            currentCoroutineContext().ensureActive()
            scenesState.scenes = if (request.page == 1) result.scenes else (scenesState.scenes + result.scenes).distinctBy { it.id }
            scenesState.total = result.totalCount
            scenesState.hasMore = result.scenes.isNotEmpty() && request.page * PERFORMER_SCENE_PAGE_SIZE < result.totalCount
            scenesState.lastSuccess = request
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            if (serial == scenesState.requestSerial && currentCoroutineContext().isActive) scenesState.error = true
        } finally {
            if (serial == scenesState.requestSerial) scenesState.loading = false
        }
    }

    LaunchedEffect(profile, performerId, detailRetry) {
        val active = profile ?: return@LaunchedEffect
        val requestRevision = mutations?.revision
        detailError = false
        try {
            val result = loadDetail(active, performerId)
            currentCoroutineContext().ensureActive()
            detail = result
            if (result != null && requestRevision != null) mutations?.reconcile(listOf(result.card), requestRevision)
            detailError = result == null
        } catch (e: CancellationException) { throw e } catch (_: Exception) { detailError = true }
    }
    LaunchedEffect(profile, performerId, summaryRetry) {
        val active = profile ?: return@LaunchedEffect
        summaryError = false
        try {
            val result = loadSummary(active, performerId)
            currentCoroutineContext().ensureActive()
            summary = result
        } catch (e: CancellationException) { throw e } catch (_: Exception) { summaryError = true }
    }
    LaunchedEffect(performerId, showScenes) {
        if (showScenes) listState.scrollToItem(2)
    }

    val visibleScenes = if (scenesState.filter.localFavoriteOnly) scenesState.scenes.filter { it.id in favorites } else scenesState.scenes
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.performer_back)) }
            Text(detail?.card?.name ?: stringResource(R.string.performers_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { infoExpanded = !infoExpanded }) {
                Text(stringResource(if (infoExpanded) R.string.performer_hide_info else R.string.performer_show_info))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("performer-detail-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "profile") {
                if (infoExpanded) {
                    when {
                        detailError -> RetryRow(R.string.performer_detail_error) { detailRetry++ }
                        detail == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else -> PerformerProfile(detail!!, profile, aliasesExpanded, { aliasesExpanded = !aliasesExpanded }, { imageOpen = true }, mutationStatus, mutations)
                    }
                }
            }
            item(key = "details") {
                if (infoExpanded && detail != null) {
                    TextButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.padding(horizontal = 8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(if (detailsExpanded) R.string.performer_hide_details else R.string.performer_show_details))
                    }
                    if (detailsExpanded) PerformerDetails(detail!!)
                }
            }
            item(key = "scenes") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.performer_scenes), style = MaterialTheme.typography.titleLarge)
                    when {
                        summaryError -> RetryRow(R.string.performer_scenes_error) { summaryRetry++ }
                        summary == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                        else -> {
                            val current = summary!!
                            val duration = formatPlayerPosition((current.durationSeconds * 1000).toLong())
                            val size = formatGalleryFileSize(current.sizeBytes.toLong()).orEmpty()
                            Text(stringResource(R.string.performer_summary, current.count, duration, size))
                        }
                    }
                    PerformerBrowseControls(
                        query = scenesState.input, onQueryChange = { scenesState.input = it },
                        searchLabel = stringResource(R.string.performer_scene_search),
                        sortLabel = sortOptions.firstOrNull { it.sort == scenesState.sort }?.label ?: stringResource(R.string.performer_scene_sort),
                        onOpenSort = { sortOpen = true }, ascending = scenesState.direction == StashSortDirection.Asc,
                        onToggleDirection = {
                            scenesState.page = 1
                            scenesState.scenes = emptyList()
                            scenesState.direction = if (scenesState.direction == StashSortDirection.Desc) StashSortDirection.Asc else StashSortDirection.Desc
                        },
                        filterLabel = stringResource(R.string.performer_scene_filter, scenesState.filter.activeFilterCount),
                        onOpenFilter = { filterSheet = "menu" },
                        sortMenu = {
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                sortOptions.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                    scenesState.page = 1
                                    scenesState.scenes = emptyList()
                                    scenesState.sort = option.sort
                                    scenesState.direction = option.defaultDirection
                                    scenesState.filter = scenesState.filter.copy(randomShuffleSeed = if (option.sort == "random") nextStashRandomSortSeed() else null)
                                    scenesState.reload++
                                    sortOpen = false
                                }) }
                            }
                        },
                    )
                    if (scenesState.error) RetryRow(R.string.performer_scenes_error) { scenesState.reload++ }
                    if (scenesState.loading) CircularProgressIndicator(Modifier.padding(16.dp))
                    if (!scenesState.loading && !scenesState.error && visibleScenes.isEmpty() && !scenesState.hasMore) Text(stringResource(R.string.performer_scenes_empty))
                }
            }
            visibleScenes.chunked(2).forEach { row ->
                item(key = "scene-row-${row.first().id}") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { scene ->
                            SceneCard(
                                scene = scene, modifier = Modifier.weight(1f), thumbnailHeight = 120.dp,
                                thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, profile),
                                isLocalFavorite = scene.id in favorites, isInWatchLater = scene.id in watchLater, isInQueue = scene.id in queue,
                                onToggleFavorite = { scope.launch { localRepository.setFavorite(scene, scene.id !in favorites) } },
                                onToggleWatchLater = { scope.launch { localRepository.setWatchLater(scene, scene.id !in watchLater) } },
                                onAddToQueue = { scope.launch { if (scene.id in queue) localRepository.removeFromQueue(scene.id) else localRepository.addToQueue(scene) } },
                                onClick = {
                                    val active = profile ?: return@SceneCard
                                    val continuation = PlayerPlaybackQueueContinuation.Performer(
                                        profile = active, performerId = performerId, query = scenesState.query,
                                        sort = scenesState.sort, direction = scenesState.direction, videoFilter = scenesState.filter,
                                        nextPage = scenesState.page + 1, pageSize = PERFORMER_SCENE_PAGE_SIZE, hasMore = scenesState.hasMore,
                                    )
                                    onOpenScene(scene.id, visibleScenes, scenesState.filter.randomShuffle, continuation)
                                },
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            item(key = "scene-footer") {
                if (scenesState.hasMore && !scenesState.loading && !scenesState.error) {
                    TextButton(onClick = { scenesState.page++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.performers_load_more)) }
                }
            }
        }
    }
    if (imageOpen && detail?.card?.imagePath != null) {
        AlertDialog(
            onDismissRequest = { imageOpen = false },
            confirmButton = { TextButton(onClick = { imageOpen = false }) { Text(stringResource(R.string.performer_close)) } },
            text = { AsyncImage(model = rememberStashThumbnailModel(detail?.card?.imagePath, profile), contentDescription = detail?.card?.name, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp), contentScale = ContentScale.Fit) },
        )
    }
    if (filterSheet == "menu") {
        AlertDialog(
            onDismissRequest = { filterSheet = "" },
            title = { Text(stringResource(R.string.performer_scene_filter_title)) },
            text = {
                Column {
                    TextButton(onClick = { filterSheet = "tags" }) { Text(stringResource(R.string.performer_tags)) }
                    TextButton(onClick = { filterSheet = "date" }) { Text(stringResource(R.string.performer_scene_date_duration)) }
                    TextButton(onClick = { filterSheet = "rating" }) { Text(stringResource(R.string.performer_scene_rating_format)) }
                    TextButton(onClick = { updateFilter(scenesState.filter.copy(localFavoriteOnly = !scenesState.filter.localFavoriteOnly)); filterSheet = "" }) {
                        Text(stringResource(if (scenesState.filter.localFavoriteOnly) R.string.performer_scene_favorites_on else R.string.performer_scene_favorites_off))
                    }
                    TextButton(onClick = { updateFilter(StashVideoFilterState(randomShuffleSeed = scenesState.filter.randomShuffleSeed)); filterSheet = "" }) {
                        Text(stringResource(R.string.performer_scene_clear_filters))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { filterSheet = "" }) { Text(stringResource(R.string.performer_close)) } },
        )
    }
    if (filterSheet == "date") StashDateDurationPlaybackFilterSheet(
        videoFilter = scenesState.filter,
        onApply = { updateFilter(it); filterSheet = "" },
        onClearDateRange = { updateFilter(scenesState.filter.copy(dateRange = null)) },
        onClearDurationRange = { updateFilter(scenesState.filter.copy(durationRange = null)) },
        onClearOCounter = { updateFilter(scenesState.filter.copy(oCounterFilter = null)) },
        onClearPlaybackState = { updateFilter(scenesState.filter.copy(playbackState = null)) },
        onDismiss = { filterSheet = "" },
    )
    if (filterSheet == "rating") StashRatingMediaFormatFilterSheet(
        videoFilter = scenesState.filter,
        onApply = { updateFilter(it); filterSheet = "" },
        onClearRatingRange = { updateFilter(scenesState.filter.copy(ratingRange = null)) },
        onClearMediaFormat = { updateFilter(scenesState.filter.copy(mediaFormat = StashMediaFormatFilter())) },
        onDismiss = { filterSheet = "" },
    )
    if (filterSheet == "tags") StashTagFilterSheet(
        videoFilter = scenesState.filter, tagQuery = tagQuery, tagOptions = tagOptions, isLoading = tagsLoading,
        error = if (tagsError) stringResource(R.string.performer_tags_error) else null,
        onTagQueryChange = { tagQuery = it },
        onApplyTags = { updateFilter(scenesState.filter.copy(tags = it)); filterSheet = "" },
        onRetry = { tagsRetry++ }, onDismiss = { filterSheet = "" },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformerProfile(detail: StashPerformerDetail, profile: StashServerProfile?, expanded: Boolean, toggleAliases: () -> Unit, openImage: () -> Unit, status: PerformerMutationStatus?, mutations: PerformerMutationCoordinator?) {
    val actor = detail.card
    val favorite = status?.fields?.favorite ?: actor.favorite
    val rating = status?.fields?.rating100 ?: if (status == null) actor.rating100 else null
    var dragStep by remember(actor.id) { mutableIntStateOf(ratingStepForStashRating100(rating)) }
    var dragging by remember(actor.id) { mutableStateOf(false) }
    LaunchedEffect(rating, dragging) { if (!dragging) dragStep = ratingStepForStashRating100(rating) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(88.dp).aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            if (actor.imagePath == null) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.size(64.dp))
            } else {
                AsyncImage(
                    model = rememberStashThumbnailModel(actor.imagePath, profile), contentDescription = actor.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable(onClick = openImage),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(actor.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { mutations?.toggleFavorite(actor) }, enabled = mutations != null && status?.busy != true) {
                    Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(if (favorite) R.string.performer_remove_favorite else R.string.performer_add_favorite),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            val gender = when (actor.gender) {
                "MALE" -> stringResource(R.string.performer_gender_male)
                "FEMALE" -> stringResource(R.string.performer_gender_female)
                "TRANSGENDER_MALE" -> stringResource(R.string.performer_gender_trans_male)
                "TRANSGENDER_FEMALE" -> stringResource(R.string.performer_gender_trans_female)
                "INTERSEX" -> stringResource(R.string.performer_gender_intersex)
                "NON_BINARY" -> stringResource(R.string.performer_gender_non_binary)
                else -> null
            }
            listOfNotNull(performerCountryFlag(actor.country), gender, performerAge(actor.birthdate, actor.deathDate)?.let { stringResource(R.string.performers_age, it) }).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Text(it) }
            if (actor.aliases.isNotEmpty()) {
                Text(actor.aliases.joinToString(", "), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = toggleAliases), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    StashStarRatingSlider(
        ratingStep = dragStep,
        contentDescriptionPrefix = stringResource(R.string.performer_rating),
        enabled = mutations != null && status?.busy != true,
        modifier = Modifier.width(240.dp),
        onInteractionStart = { dragging = true },
        onSelectRatingStep = { step ->
            dragStep = step
            if (!dragging) {
                mutations?.setRating(actor, stashRating100ForRatingStep(step))
                dragStep = ratingStepForStashRating100(rating)
            }
        },
        onInteractionEnd = {
            val selectedStep = dragStep
            dragging = false
            mutations?.setRating(actor, stashRating100ForRatingStep(selectedStep))
            dragStep = ratingStepForStashRating100(rating)
        },
    )
    TextButton(onClick = { mutations?.setRating(actor, null) }, enabled = mutations != null && status?.busy != true && rating != null) {
        Text(stringResource(R.string.performer_clear_rating))
    }
    }
    if (status?.error == true) Text(stringResource(R.string.performer_update_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PerformerDetails(detail: StashPerformerDetail) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailLine(R.string.performer_birthdate, detail.card.birthdate)
        DetailLine(R.string.performer_height, detail.heightCm?.let { "$it cm" })
        DetailLine(R.string.performer_measurements, detail.measurements)
        DetailLine(R.string.performer_ethnicity, detail.ethnicity)
        DetailLine(R.string.performer_hair, detail.hairColor)
        DetailLine(R.string.performer_eyes, detail.eyeColor)
        DetailLine(R.string.performer_career, listOfNotNull(detail.careerStart, detail.careerEnd).joinToString(" – ").ifBlank { null })
        DetailLine(R.string.performer_bio, detail.details)
        DetailLine(R.string.performer_tags, detail.tags.joinToString(", ").ifBlank { null })
        DetailLine(R.string.performer_stash_ids, detail.stashIds.joinToString("\n").ifBlank { null })
        detail.urls.filter { url -> Uri.parse(url).scheme?.lowercase() in setOf("http", "https") }.forEach { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(url, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DetailLine(label: Int, value: String?) {
    if (!value.isNullOrBlank()) Text(stringResource(label) + ": " + value)
}

@Composable
private fun RetryRow(label: Int, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text(stringResource(R.string.performers_retry)) }
    }
}
