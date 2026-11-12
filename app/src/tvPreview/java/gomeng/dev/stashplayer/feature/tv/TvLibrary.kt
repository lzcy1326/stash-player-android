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
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashPlaybackState
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException

private const val TvPageSize = 18

@Composable
internal fun TvLibrary(profile: StashServerProfile, onSettings: () -> Unit) {
    val client = remember(profile) { StashGraphQlClient(profile) }
    val gridState = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchButton = remember { FocusRequester() }
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var resumable by remember { mutableStateOf(false) }
    var scenes by remember { mutableStateOf(emptyList<SceneCardModel>()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SceneCardModel?>(null) }
    var focusScene by remember { mutableStateOf<String?>(null) }

    fun loadFirstPage(newQuery: String, resumeOnly: Boolean) {
        query = newQuery
        resumable = resumeOnly
        scenes = emptyList()
        total = 0
        page = 1
        reload++
        focusScene = null
        busy = true
    }

    LaunchedEffect(page, reload) {
        busy = true
        failed = false
        try {
            val result = client.findSceneCardsPage(
                perPage = TvPageSize,
                page = page,
                query = query.ifBlank { null },
                sort = if (resumable) "last_played_at" else "updated_at",
                videoFilter = StashVideoFilterState(
                    playbackState = if (resumable) StashPlaybackState.Resumable else null,
                ),
            )
            val previousIds = scenes.mapTo(mutableSetOf()) { it.id }
            val newItems = result.scenes.filter { previousIds.add(it.id) }
            scenes = scenes + newItems
            total = if (newItems.isEmpty() || result.scenes.size < TvPageSize) scenes.size else result.totalCount
            if (page == 1) gridState.scrollToItem(0)
            focusScene = newItems.firstOrNull()?.id
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            failed = true
        } finally {
            busy = false
        }
    }

    val video = selected
    if (video != null) {
        TvPlayer(client, profile, video) {
            focusScene = video.id
            selected = null
        }
        return
    }

    LaunchedEffect(focusScene) {
        val target = focusScene ?: return@LaunchedEffect
        val index = scenes.indexOfFirst { it.id == target }
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.key == target }) {
            gridState.scrollToItem(index)
        }
    }
    BackHandler(enabled = query.isNotBlank() || resumable) {
        draft = ""
        loadFirstPage("", false)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (resumable) R.string.tv_resume else R.string.tv_library),
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.tv_search)) },
                modifier = Modifier.weight(1f).onTvTextFieldKeys(
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
            TvButton(stringResource(R.string.tv_server), onSettings)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(scenes, key = { it.id }) { scene ->
                TvSceneCard(
                    profile = profile,
                    scene = scene,
                    requestFocus = focusScene == scene.id,
                    onFocusAcquired = { if (focusScene == scene.id) focusScene = null },
                    onOpen = { selected = scene },
                )
            }
            item(key = "tv-library-footer", span = { GridItemSpan(maxLineSpan) }) {
                val footerFocus = remember { FocusRequester() }
                LaunchedEffect(failed, busy, scenes.isEmpty()) {
                    if (failed && scenes.isEmpty()) footerFocus.requestFocus()
                    else if (!busy && scenes.isEmpty()) searchButton.requestFocus()
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

@Composable
private fun TvSceneCard(
    profile: StashServerProfile,
    scene: SceneCardModel,
    requestFocus: Boolean,
    onFocusAcquired: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
    }
    Column(
        Modifier.focusRequester(focusRequester).onFocusChanged {
            focused = it.isFocused
            if (focused && requestFocus) onFocusAcquired()
        }
            .clip(shape)
            .background(if (focused) colors.primary else colors.surface)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(onClick = onOpen)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(colors.background)) {
            scene.thumbnailUrl?.let { url ->
                val image = remember(profile, url, context) {
                    val absolute = profile.absoluteUrl(url)
                    ImageRequest.Builder(context).data(absolute).apply {
                        profile.authHeadersFor(absolute).forEach { (name, value) -> addHeader(name, value) }
                    }.build()
                }
                AsyncImage(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(
            scene.title,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            color = if (focused) colors.onPrimary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            scene.durationText,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = if (focused) colors.onPrimary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
