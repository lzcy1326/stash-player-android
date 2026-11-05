package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.performerAge
import gomeng.dev.stashplayer.core.network.performerCountryFlag
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

private const val PERFORMERS_PAGE_SIZE = 40

@Composable
fun PerformersRoute(
    profile: StashServerProfile?,
    modifier: Modifier = Modifier,
    loadPage: suspend (StashServerProfile, Int, String) -> StashPerformerPage = { server, page, query ->
        StashGraphQlClient(server).findPerformerCardsPage(page = page, query = query)
    },
) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var requestSerial by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var items by remember(profile) { mutableStateOf(emptyList<StashPerformerCard>()) }
    var count by remember(profile) { mutableIntStateOf(0) }
    var hasMore by remember(profile) { mutableStateOf(false) }
    var loading by remember(profile) { mutableStateOf(false) }
    var error by remember(profile) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(profile) {
        page = 1
        items = emptyList()
        count = 0
        hasMore = false
    }
    LaunchedEffect(input) {
        delay(300)
        val nextQuery = input.trim()
        if (query != nextQuery) {
            page = 1
            items = emptyList()
            query = nextQuery
        }
    }
    LaunchedEffect(profile, query, refresh, page, retry) {
        val serial = ++requestSerial
        error = false
        if (profile == null) return@LaunchedEffect
        loading = true
        try {
            val result = loadPage(profile, page, query)
            currentCoroutineContext().ensureActive()
            items = if (page == 1) result.performers else (items + result.performers).distinctBy { it.id }
            count = result.count
            hasMore = result.performers.isNotEmpty() && page * PERFORMERS_PAGE_SIZE < count
            if (page == 1) gridState.scrollToItem(0)
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
            Text(stringResource(R.string.performers_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { page = 1; refresh++ }, enabled = !loading) { Text(stringResource(R.string.performers_refresh)) }
        }
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            label = { Text(stringResource(R.string.performers_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.performers_load_error), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.performers_retry)) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp), state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("performers-grid"),
        ) {
            items(items, key = { it.id }) { performer -> PerformerListCard(performer, profile) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error -> Unit
                        items.isEmpty() -> Text(stringResource(R.string.performers_empty))
                        hasMore -> Button(onClick = { page++ }) { Text(stringResource(R.string.performers_load_more)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformerListCard(performer: StashPerformerCard, profile: StashServerProfile?) {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (performer.imagePath != null) {
                AsyncImage(
                    model = rememberStashThumbnailModel(performer.imagePath, profile), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            }
            Icon(
                if (performer.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.performers_favorite),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            performerCountryFlag(performer.country)?.let { flag ->
                Text(flag, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
        val gender = when (performer.gender) {
            "MALE", "TRANSGENDER_MALE" -> "♂ "
            "FEMALE", "TRANSGENDER_FEMALE" -> "♀ "
            else -> ""
        }
        Text(gender + performer.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        performerAge(performer.birthdate, performer.deathDate)?.let { age ->
            Text(stringResource(R.string.performers_age, age), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.performers_scene_count, performer.sceneCount), style = MaterialTheme.typography.bodySmall)
    }
}
