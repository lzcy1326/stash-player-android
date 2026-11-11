package gomeng.dev.stashplayer.feature.studios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel

/** Keep logos intact and retain a visible placeholder while loading or after a failed request. */
@Composable
internal fun StudioLogo(studio: StashStudioCard, profile: StashServerProfile?, modifier: Modifier = Modifier) {
    var loaded by remember(studio.id, studio.imagePath, profile) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) Icon(Icons.Outlined.Business, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        if (!studio.imagePath.isNullOrBlank()) AsyncImage(
            model = rememberStashThumbnailModel(studio.imagePath, profile),
            contentDescription = studio.name,
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { loaded = false },
            modifier = Modifier.fillMaxSize().then(if (loaded) Modifier.testTag("studio-logo-loaded-${studio.id}") else Modifier),
        )
    }
}
