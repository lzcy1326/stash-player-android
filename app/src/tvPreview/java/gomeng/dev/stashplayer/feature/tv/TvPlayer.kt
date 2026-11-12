package gomeng.dev.stashplayer.feature.tv

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.GraphQlStashStreamResolver
import gomeng.dev.stashplayer.core.network.ResolvedStashStreamCandidate
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashStream
import gomeng.dev.stashplayer.core.player.AspectRatioMode
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.core.player.resolvePlayerResumeActivitySavePayload
import gomeng.dev.stashplayer.core.player.resolvePlayerResumeStartPositionMs
import gomeng.dev.stashplayer.feature.player.PlayerSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun TvPlayer(
    client: StashGraphQlClient,
    profile: StashServerProfile,
    scene: SceneCardModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(scene.id) { StashPlayerController(context) }
    val player = controller.player
    val scope = rememberCoroutineScope()
    val saveMutex = remember { Mutex() }
    val surfaceFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    var stream by remember { mutableStateOf<StashStream?>(null) }
    var candidate by remember { mutableStateOf<ResolvedStashStreamCandidate?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playRequested by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var savedPosition by remember { mutableLongStateOf(-1L) }
    val sources = remember(stream) { stream?.resolvedCandidates.orEmpty().distinctBy { it.uri } }

    fun prepare(
        media: StashStream,
        source: ResolvedStashStreamCandidate?,
        startMs: Long,
        autoplay: Boolean = true,
    ) {
        playbackError = null
        ready = false
        candidate = source
        position = startMs
        controller.prepare(
            uri = source?.uri ?: media.uri,
            title = media.title,
            startPositionMs = startMs,
            playWhenReady = autoplay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            requestHeaders = source?.requestHeaders ?: media.requestHeaders,
            mimeType = if (source?.isHlsManifest ?: media.sourceIsHlsManifest) MimeTypes.APPLICATION_M3U8 else null,
        )
    }

    fun saveProgress(afterSave: (() -> Unit)? = null) {
        val currentPosition = player.currentPosition
        val currentDuration = player.duration
        val canSave = loadedOnce && currentPosition > 0L
        if (!canSave) {
            afterSave?.invoke()
            return
        }
        val payload = resolvePlayerResumeActivitySavePayload(currentPosition, currentDuration, 0.0)
        scope.launch {
            saveMutex.withLock {
                if (savedPosition != currentPosition) {
                    try {
                        client.saveActivity(scene.id, payload.resumeTimeSeconds)
                        savedPosition = currentPosition
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.tv_progress_not_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            afterSave?.invoke()
        }
    }

    fun closePlayer() {
        if (closing) return
        closing = true
        controller.pause()
        saveProgress(onClose)
    }

    fun togglePlayback() {
        if (player.playWhenReady) controller.pause() else player.play()
    }

    fun seek(deltaMs: Long) {
        if (ready) controller.seekBy(deltaMs)
    }

    fun switchStream() {
        val media = stream ?: return
        if (sources.size < 2) return
        val index = sources.indexOfFirst { it.uri == (candidate?.uri ?: media.uri) }
        val next = sources[(index + 1) % sources.size]
        prepare(media, next, player.currentPosition.coerceAtLeast(0L), autoplay = playbackError != null || playRequested)
    }

    LaunchedEffect(request) {
        loadFailed = false
        try {
            val media = GraphQlStashStreamResolver(client, profile).resolve(scene.id)
            stream = media
            prepare(media, null, resolvePlayerResumeStartPositionMs(media.startPositionMs, media.durationSeconds))
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            loadFailed = true
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED
                if (ready) loadedOnce = true
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playRequested = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !closing) {
                controller.pause()
                saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            controller.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    LaunchedEffect(loadedOnce) {
        if (loadedOnce) {
            delay(4_000)
            showHint = false
        }
    }

    val hasError = loadFailed || playbackError != null
    val showControls = controlsVisible || hasError
    LaunchedEffect(showControls, hasError) {
        if (showControls) controlsFocus.requestFocus() else surfaceFocus.requestFocus()
    }
    BackHandler {
        if (controlsVisible && !hasError) controlsVisible = false else closePlayer()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            val action = when {
                closing || hasError -> null
                code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ::togglePlayback
                code == KeyEvent.KEYCODE_MEDIA_PLAY -> ({ player.play() })
                code == KeyEvent.KEYCODE_MEDIA_PAUSE -> controller::pause
                code == KeyEvent.KEYCODE_MEDIA_REWIND -> ({ seek(-10_000) })
                code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ({ seek(10_000) })
                showControls -> null
                code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER -> ::togglePlayback
                code == KeyEvent.KEYCODE_DPAD_LEFT -> ({ seek(-10_000) })
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> ({ seek(10_000) })
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ->
                    ({ controlsVisible = true })
                else -> null
            }
            if (action != null && event.nativeKeyEvent.action == KeyEvent.ACTION_UP) action()
            action != null
        },
    ) {
        PlayerSurface(controller, AspectRatioMode.Fit, Modifier.fillMaxSize())
        if (!showControls) {
            Box(Modifier.fillMaxSize().focusRequester(surfaceFocus).focusable())
            if (loadedOnce && showHint) {
                Text(
                    stringResource(R.string.tv_player_hint),
                    Modifier.align(Alignment.BottomCenter).padding(24.dp)
                        .background(Color(0xD91B1725)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        when {
            hasError -> Column(
                Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (loadFailed) stringResource(R.string.tv_stream_unavailable)
                    else stringResource(R.string.tv_player_error, playbackError.orEmpty()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(stringResource(R.string.tv_retry), {
                        val media = stream
                        if (media == null) request++ else prepare(media, candidate, position)
                    }, Modifier.focusRequester(controlsFocus))
                    if (sources.size > 1) {
                        TvButton(stringResource(R.string.tv_switch_stream), ::switchStream)
                    }
                    TvButton(stringResource(R.string.tv_back), ::closePlayer)
                }
            }
            stream == null || !ready || closing ->
                Text(stringResource(R.string.tv_loading), Modifier.align(Alignment.Center), color = Color.White)
        }
        if (showControls && !hasError) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE61B1725))
                    .padding(horizontal = 42.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(scene.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(candidate?.sourceLabel ?: stream?.sourceLabel.orEmpty())
                LinearProgressIndicator(
                    progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${formatPlayerPosition(position)} / ${formatPlayerPosition(duration)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(
                        stringResource(if (playRequested) R.string.tv_pause else R.string.tv_play),
                        ::togglePlayback,
                        Modifier.focusRequester(controlsFocus),
                    )
                    TvButton(stringResource(R.string.tv_rewind), { seek(-10_000) })
                    TvButton(stringResource(R.string.tv_forward), { seek(10_000) })
                    if (sources.size > 1) {
                        TvButton(stringResource(R.string.tv_switch_stream), ::switchStream)
                    }
                    TvButton(stringResource(R.string.tv_back), ::closePlayer)
                }
            }
        }
    }
}
