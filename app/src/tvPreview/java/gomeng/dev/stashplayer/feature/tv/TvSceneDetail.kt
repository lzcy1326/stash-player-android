package gomeng.dev.stashplayer.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashScene
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import kotlinx.coroutines.CancellationException

@Composable
internal fun TvSceneDetail(
    client: StashGraphQlClient,
    profile: StashServerProfile,
    scene: SceneCardModel,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var detail by remember(scene.id) { mutableStateOf<StashScene?>(null) }
    var loading by remember(scene.id) { mutableStateOf(true) }
    var failed by remember(scene.id) { mutableStateOf(false) }
    var request by remember(scene.id) { mutableIntStateOf(0) }

    LaunchedEffect(scene.id, request) {
        loading = true
        failed = false
        try {
            detail = client.findScene(scene.id) ?: error("Scene not found")
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(scene.id) { playFocus.requestFocus() }
    BackHandler(onBack = onBack)

    Row(
        Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val imageUrl = detail?.screenshotUrl ?: scene.thumbnailUrl
        Box(
            Modifier.weight(0.42f).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
        ) {
            imageUrl?.let { url ->
                val image = remember(profile, url, context) {
                    val absolute = profile.absoluteUrl(url)
                    ImageRequest.Builder(context).data(absolute).apply {
                        profile.authHeadersFor(absolute).forEach { (name, value) -> addHeader(name, value) }
                    }.build()
                }
                AsyncImage(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                detail?.title ?: scene.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                detail?.studioName?.takeIf { it.isNotBlank() } ?: scene.studio.takeIf { it.isNotBlank() },
                detail?.date,
                scene.durationText.takeIf { it.isNotBlank() },
                detail?.let { if (it.width != null && it.height != null) "${it.width} × ${it.height}" else null },
                detail?.rating100?.let { stringResource(R.string.tv_detail_rating, it) },
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(stringResource(R.string.tv_play), onPlay, Modifier.focusRequester(playFocus))
                TvButton(stringResource(R.string.tv_back), onBack)
                if (failed) TvButton(stringResource(R.string.tv_retry), { request++ })
            }
            when {
                loading -> Text(stringResource(R.string.tv_loading))
                failed -> Text(stringResource(R.string.tv_detail_error), color = MaterialTheme.colorScheme.error)
                else -> {
                    detail?.resumeTimeSeconds?.takeIf { it > 0 }?.let {
                        Text(stringResource(R.string.tv_detail_resume, formatPlayerPosition((it * 1000).toLong())))
                    }
                    detail?.performers?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(R.string.tv_detail_performers, it.joinToString("、")), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    detail?.tags?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(R.string.tv_detail_tags, it.joinToString("、") { tag -> tag.label }), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    detail?.details?.let {
                        Text(it, modifier = Modifier.fillMaxWidth(), maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
