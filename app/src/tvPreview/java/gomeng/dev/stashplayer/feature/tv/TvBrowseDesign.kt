package gomeng.dev.stashplayer.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.StashServerProfile

internal val TvSideInset = 42.dp
internal val TvTileSpacing = 16.dp
internal val TvTileMinWidth = 152.dp
internal val TvGridContentPadding = 8.dp
internal const val TvGridRowsPerPage = 3

/**
 * The spotlight keeps the emulator's proportions on every TV by taking a share of the available
 * height instead of a fixed height: 1080p TVs report 240 dpi (1280×720 dp) while the emulator
 * reports 320 dpi (960×540 dp), so a fixed dp height looks far thinner on the real set.
 */
internal const val TvSpotlightHeightFraction = 0.30f

/** Column count of the grid, mirroring [androidx.compose.foundation.lazy.grid.GridCells.Adaptive]. */
internal fun tvGridColumnCount(availableWidth: Dp): Int {
    val spacing = TvTileSpacing.value
    val columns = ((availableWidth.value + spacing) / (TvTileMinWidth.value + spacing)).toInt()
    return columns.coerceAtLeast(1)
}

@Composable
internal fun TvArtwork(
    profile: StashServerProfile,
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.background(Brush.linearGradient(listOf(colors.surface, colors.background)))) {
        if (!url.isNullOrBlank()) {
            val request = remember(profile, url, context) {
                val absolute = profile.absoluteUrl(url)
                ImageRequest.Builder(context).data(absolute).apply {
                    profile.authHeadersFor(absolute).forEach { (name, value) -> addHeader(name, value) }
                }.build()
            }
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = contentScale)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvBrowseTile(
    profile: StashServerProfile,
    image: String?,
    title: String,
    subtitle: String,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    aspectRatio: Float = 16f / 9f,
    duration: String = "",
    progress: Float = 0f,
    playable: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    studioCode: String? = null,
    releaseDate: String? = null,
    captionHeight: Dp = 54.dp,
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(requestFocus) {
        if (requestFocus) requester.requestFocus()
    }
    Column(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (focused) onFocused()
            }
            .graphicsLayer {
                val scale = if (focused) 1.035f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.surface)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))) {
            TvArtwork(profile, image, Modifier.fillMaxSize(), contentScale)
            if (duration.isNotBlank()) {
                Text(
                    duration,
                    Modifier.align(Alignment.BottomEnd).padding(7.dp)
                        .background(colors.background.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (focused && playable) {
                Text(
                    "▶",
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = colors.onPrimary,
                )
            }
            if (progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp).background(colors.primary),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().height(captionHeight).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (studioCode == null && releaseDate == null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    studioCode?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium,
                            color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                    releaseDate?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvSceneSpotlight(
    profile: StashServerProfile,
    scene: SceneCardModel,
    height: Dp,
    onDetails: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface),
    ) {
        // Full-bleed artwork: the 16:9 thumbnail is cropped by the banner, never squeezed into a
        // narrow strip beside the text.
        TvArtwork(profile, scene.thumbnailUrl, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to colors.surface.copy(alpha = 0.97f),
                    0.30f to colors.surface.copy(alpha = 0.88f),
                    0.58f to colors.surface.copy(alpha = 0.24f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.38f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.52f).padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                scene.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(scene.studio, scene.durationText).filter(String::isNotBlank).joinToString("  /  "),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                TvButton(stringResource(R.string.tv_details), onDetails)
            }
        }
    }
}
