package gomeng.dev.stashplayer.feature.performers

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.formatGalleryFileSize
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.performerAge
import gomeng.dev.stashplayer.core.network.performerCountryFlag
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun PerformerDetailRoute(
    performerId: String,
    showScenes: Boolean,
    profile: StashServerProfile?,
    onBack: () -> Unit,
    loadDetail: suspend (StashServerProfile, String) -> StashPerformerDetail? = { server, id ->
        StashGraphQlClient(server).findPerformerDetail(id)
    },
    loadSummary: suspend (StashServerProfile, String) -> StashPerformerSceneSummary = { server, id ->
        StashGraphQlClient(server).findPerformerSceneSummary(id)
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

    LaunchedEffect(profile, performerId, detailRetry) {
        val active = profile ?: return@LaunchedEffect
        detailError = false
        try {
            val result = loadDetail(active, performerId)
            currentCoroutineContext().ensureActive()
            detail = result
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.performer_back)) }
            Text(detail?.card?.name ?: stringResource(R.string.performers_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { infoExpanded = !infoExpanded }) {
                Text(stringResource(if (infoExpanded) R.string.performer_hide_info else R.string.performer_show_info))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "profile") {
                if (infoExpanded) {
                    when {
                        detailError -> RetryRow(R.string.performer_detail_error) { detailRetry++ }
                        detail == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else -> PerformerProfile(detail!!, profile, aliasesExpanded, { aliasesExpanded = !aliasesExpanded }, { imageOpen = true })
                    }
                }
            }
            item(key = "details") {
                if (infoExpanded && detail != null) {
                    TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                        Text(stringResource(if (detailsExpanded) R.string.performer_hide_details else R.string.performer_show_details))
                    }
                    if (detailsExpanded) PerformerDetails(detail!!)
                }
            }
            item(key = "scenes") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.performer_scenes), style = MaterialTheme.typography.titleLarge)
                    when {
                        summaryError -> RetryRow(R.string.performer_scenes_error) { summaryRetry++ }
                        summary == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                        else -> {
                            val current = summary!!
                            val duration = formatPlayerPosition((current.durationSeconds * 1000).toLong())
                            val size = formatGalleryFileSize(current.sizeBytes.toLong()).orEmpty()
                            Text(stringResource(R.string.performer_summary, current.count, duration, size))
                            Text(stringResource(R.string.performer_scenes_in_progress), modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
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
}

@Composable
private fun PerformerProfile(detail: StashPerformerDetail, profile: StashServerProfile?, expanded: Boolean, toggleAliases: () -> Unit, openImage: () -> Unit) {
    val actor = detail.card
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(120.dp).aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            if (actor.imagePath == null) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.size(64.dp))
            } else {
                AsyncImage(
                    model = rememberStashThumbnailModel(actor.imagePath, profile), contentDescription = actor.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable(onClick = openImage),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(actor.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                Text(actor.aliases.joinToString(", "), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = toggleAliases))
            }
            actor.rating100?.let { Text("★ %.1f / 5".format(it / 20f)) }
            if (actor.favorite) Text("♥ " + stringResource(R.string.performers_favorite))
        }
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
