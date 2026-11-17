package gomeng.dev.stashplayer.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    // Full-bleed artwork with the metadata anchored to the bottom, so the page uses the whole
    // screen instead of a small top-left block with an empty lower half.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TvArtwork(
            profile = profile,
            url = detail?.screenshotUrl ?: scene.thumbnailUrl,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF20B0A10),
                    0.45f to Color(0xB30B0A10),
                    0.78f to Color(0x1A0B0A10),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.42f to Color.Transparent,
                    1f to Color(0xF20B0A10),
                ),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.68f)
                .padding(start = 52.dp, end = 24.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                detail?.title ?: scene.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
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
            if (meta.isNotBlank()) {
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                        Text(
                            stringResource(R.string.tv_detail_performers, it.joinToString("、")),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail?.tags?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            stringResource(R.string.tv_detail_tags, it.joinToString("、") { tag -> tag.label }),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    detail?.details?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
