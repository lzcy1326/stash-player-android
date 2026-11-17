package gomeng.dev.stashplayer.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSpriteFrame
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.feature.player.PlayerSpriteThumbnail

@Composable
internal fun TvSeekPreview(
    targetMs: Long,
    durationMs: Long,
    frame: StashSpriteFrame?,
    profile: StashServerProfile,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (targetMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val scrim = Color(0xCC1B1725)
    // No full-width panel: the picture stays visible, only the preview window and the timeline
    // carry a translucent backdrop of their own.
    Column(
        modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(134.dp)) {
            val width = minOf(208.dp, maxWidth)
            val offset = (maxWidth * fraction - width / 2).coerceIn(0.dp, maxWidth - width)
            Box(
                Modifier.offset(x = offset).size(width, 132.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scrim)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (frame == null) {
                    Text(stringResource(R.string.tv_preview_unavailable), style = MaterialTheme.typography.labelMedium)
                } else {
                    PlayerSpriteThumbnail(frame, profile, imageLoader, Modifier.size(width - 8.dp, 116.dp))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scrim)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${formatPlayerPosition(targetMs)} / ${formatPlayerPosition(durationMs)}")
        }
    }
}
