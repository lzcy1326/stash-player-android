package gomeng.dev.stashplayer.feature.tv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashPlaybackState
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun TvLibrary(
    profile: StashServerProfile,
    navigation: @Composable (() -> Unit)? = null,
    scope: TvEntityScope? = null,
    onExitScope: (() -> Unit)? = null,
    entityHeader: @Composable (() -> Unit)? = null,
) {
    val client = remember(profile) { StashGraphQlClient(profile) }
    val gridState = rememberLazyGridState()
    // One page is a whole number of rows, so no row is left half filled on any TV width.
    val configuration = LocalConfiguration.current
    val columns = remember(configuration.screenWidthDp) {
        tvGridColumnCount(configuration.screenWidthDp.dp - TvSideInset * 2 - TvGridContentPadding)
    }
    val pageSize = columns * TvGridRowsPerPage
    val spotlightHeight = (configuration.screenHeightDp * TvSpotlightHeightFraction).dp
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchButton = remember { FocusRequester() }
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var resumable by remember { mutableStateOf(false) }
    var scenes by remember(scope) { mutableStateOf(emptyList<SceneCardModel>()) }
    var total by remember(scope) { mutableIntStateOf(0) }
    var page by remember(scope) { mutableIntStateOf(1) }
    var reload by remember(scope) { mutableIntStateOf(0) }
    var busy by remember(scope) { mutableStateOf(true) }
    var failed by remember(scope) { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SceneCardModel?>(null) }
    var playing by remember { mutableStateOf(false) }
    var focusScene by remember { mutableStateOf<String?>(null) }
    var highlightedScene by remember { mutableStateOf<String?>(null) }
    var initialRelatedFocus by remember { mutableStateOf(true) }

    fun loadFirstPage(newQuery: String, resumeOnly: Boolean) {
        query = newQuery
        resumable = resumeOnly
        scenes = emptyList()
        total = 0
        page = 1
        reload++
        focusScene = null
        highlightedScene = null
        busy = true
    }

    LaunchedEffect(scope, page, reload, pageSize) {
        busy = true
        failed = false
        try {
            val result = client.findSceneCardsPage(
                perPage = pageSize,
                page = page,
                query = query.ifBlank { null },
                sort = if (resumable) "last_played_at" else "updated_at",
                videoFilter = StashVideoFilterState(
                    playbackState = if (resumable) StashPlaybackState.Resumable else null,
                ),
                performerId = scope?.id?.takeIf { scope.section == TvSection.Performers },
                studioId = scope?.id?.takeIf { scope.section == TvSection.Studios },
                includeStudioChildren = scope?.includeChildren == true,
            )
            val previousIds = scenes.mapTo(mutableSetOf()) { it.id }
            val newItems = result.scenes.filter { previousIds.add(it.id) }
            scenes = scenes + newItems
            total = if (newItems.isEmpty() || result.scenes.size < pageSize) scenes.size else result.totalCount
            if (page == 1) gridState.scrollToItem(0)
            if (scope == null || page > 1 || initialRelatedFocus) {
                newItems.firstOrNull()?.let {
                    focusScene = it.id
                    initialRelatedFocus = false
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            if (profile.baseUrl == TvDemoServerUrl) {
                Log.w("StashTvDemo", "Could not load example videos", failure)
            }
            failed = true
        } finally {
            busy = false
        }
    }

    val video = selected
    if (video != null) {
        fun closeVideo() {
            focusScene = video.id
            selected = null
            playing = false
        }
        if (playing) {
            TvPlayer(client, profile, video, onClose = ::closeVideo)
        } else {
            TvSceneDetail(
                client = client,
                profile = profile,
                scene = video,
                onPlay = { playing = true },
                onBack = ::closeVideo,
            )
        }
        return
    }

    LaunchedEffect(focusScene) {
        val target = focusScene ?: return@LaunchedEffect
        val index = scenes.indexOfFirst { it.id == target }
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.key == "scene-$target" }) {
            // The library's first page already shows the spotlight above the card, and scrolling it
            // into view before the grid has been measured would push the banner off screen.
            if (scope == null && page == 1) return@LaunchedEffect
            // Leading full-span items: the spotlight on the library page and/or the entity header.
            val leading = (if (scope == null && scenes.isNotEmpty()) 1 else 0) +
                (if (entityHeader == null) 0 else 1)
            gridState.scrollToItem(index + leading)
        }
    }
    // The focused first card drags the grid up before the first layout is measured, which cuts the
    // spotlight. Re-anchor once when the first page arrives; returning from playback keeps the
    // viewer's scroll position.
    var topAnchored by remember(scope) { mutableStateOf(false) }
    LaunchedEffect(scenes.isNotEmpty(), topAnchored) {
        if (!topAnchored && scenes.isNotEmpty()) {
            delay(200)
            gridState.scrollToItem(0)
            topAnchored = true
        }
    }
    BackHandler(enabled = onExitScope != null) { onExitScope?.invoke() }
    BackHandler(enabled = query.isNotBlank() || resumable) {
        draft = ""
        loadFirstPage("", false)
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val metadataStyle = MaterialTheme.typography.labelMedium
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    // All cards use the tallest caption on this page, so wrapped dates do not stagger the grid.
    val captionHeight = remember(scenes, columns, configuration.screenWidthDp, density, measurer, metadataStyle, titleStyle) {
        with(density) {
            val gridWidth = configuration.screenWidthDp.dp - TvSideInset * 2 - TvGridContentPadding
            val textWidth = ((gridWidth - TvTileSpacing * (columns - 1)) / columns - 20.dp)
                .toPx().toInt().coerceAtLeast(1)
            val gap = 8.dp.roundToPx()
            val titleHeight = measurer.measure("影片", titleStyle, maxLines = 1).size.height
            val baselineHeight = measurer.measure("2024-03-29", metadataStyle, maxLines = 1).size.height
            val metadataHeight = scenes.maxOfOrNull { scene ->
                val code = scene.studioCode?.let {
                    measurer.measure(it, metadataStyle.copy(fontWeight = FontWeight.SemiBold),
                        constraints = Constraints(maxWidth = textWidth)).size
                }
                val date = scene.releaseDate?.let { measurer.measure(it, metadataStyle, maxLines = 1).size }
                when {
                    code == null -> date?.height ?: baselineHeight
                    date == null -> code.height
                    code.width + gap + date.width <= textWidth -> maxOf(code.height, date.height)
                    else -> code.height + date.height
                }
            } ?: baselineHeight
            ((titleHeight + metadataHeight).toDp() + 8.dp).coerceAtLeast(54.dp)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = TvSideInset, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        navigation?.invoke()
        if (scope == null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (resumable) R.string.tv_resume else R.string.tv_library),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tv_search)) },
                    modifier = Modifier.widthIn(max = 232.dp).onTvTextFieldKeys(
                        onEdit = { keyboard?.show() },
                        onDown = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        searchButton.requestFocus()
                        loadFirstPage(draft.trim(), false)
                    }),
                )
                TvButton(stringResource(R.string.tv_submit_search), {
                    keyboard?.hide()
                    loadFirstPage(draft.trim(), false)
                }, Modifier.focusRequester(searchButton))
                TvButton(stringResource(if (resumable) R.string.tv_library else R.string.tv_resume), {
                    draft = ""
                    loadFirstPage("", !resumable)
                })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(TvTileSpacing),
            horizontalArrangement = Arrangement.spacedBy(TvTileSpacing),
        ) {
            if (scope == null) {
                // The spotlight is the first row of the grid, so it scrolls away with the list
                // instead of staying pinned above it.
                val spotlight = scenes.firstOrNull { it.id == highlightedScene } ?: scenes.firstOrNull()
                if (spotlight != null) {
                    item(key = "spotlight", span = { GridItemSpan(maxLineSpan) }) {
                        TvSceneSpotlight(profile, spotlight, spotlightHeight) {
                            selected = spotlight
                        }
                    }
                }
            }
            if (entityHeader != null) item(key = "entity-header", span = { GridItemSpan(maxLineSpan) }) {
                entityHeader()
            }
            items(scenes, key = { "scene-${it.id}" }) { scene ->
                TvBrowseTile(
                    profile = profile,
                    image = scene.thumbnailUrl,
                    title = scene.title,
                    subtitle = scene.studio,
                    studioCode = scene.studioCode,
                    releaseDate = scene.releaseDate,
                    captionHeight = captionHeight,
                    duration = scene.durationText,
                    progress = scene.progress,
                    playable = true,
                    requestFocus = focusScene == scene.id,
                    onFocused = {
                        highlightedScene = scene.id
                        if (focusScene == scene.id) focusScene = null
                    },
                    onOpen = {
                        selected = scene
                        playing = true
                    },
                )
            }
            item(key = "tv-library-footer", span = { GridItemSpan(maxLineSpan) }) {
                val footerFocus = remember { FocusRequester() }
                LaunchedEffect(failed, busy, scenes.isEmpty()) {
                    if (failed && scenes.isEmpty()) footerFocus.requestFocus()
                    else if (!busy && scenes.isEmpty() && scope == null) searchButton.requestFocus()
                }
                Row(
                    Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        failed -> {
                            Text(stringResource(R.string.tv_list_error))
                            TvButton(stringResource(R.string.tv_retry), { reload++ }, Modifier.focusRequester(footerFocus))
                        }
                        busy -> Text(stringResource(R.string.tv_loading))
                        scenes.isEmpty() -> Text(stringResource(R.string.tv_empty))
                        scenes.size < total -> TvButton(stringResource(R.string.tv_load_more), { page++ })
                    }
                }
            }
        }
    }
}
