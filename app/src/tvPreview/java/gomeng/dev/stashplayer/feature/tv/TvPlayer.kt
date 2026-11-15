package gomeng.dev.stashplayer.feature.tv

import android.view.KeyEvent
import android.os.SystemClock
import android.view.ViewConfiguration
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.ImageLoader
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.GraphQlStashStreamResolver
import gomeng.dev.stashplayer.core.network.ResolvedStashStreamCandidate
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashStream
import gomeng.dev.stashplayer.core.network.findStashSpriteAtTime
import gomeng.dev.stashplayer.core.player.AspectRatioMode
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.core.player.nextTvScrubTarget
import gomeng.dev.stashplayer.core.player.resolvePlayerResumeActivitySavePayload
import gomeng.dev.stashplayer.core.player.resolvePlayerResumeStartPositionMs
import gomeng.dev.stashplayer.feature.player.PlayerSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

@Composable
internal fun TvPlayer(
    client: StashGraphQlClient,
    profile: StashServerProfile,
    scene: SceneCardModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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
    var heldDirection by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubResume by remember { mutableStateOf(false) }
    var previewTarget by remember { mutableStateOf<Long?>(null) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var hidePreviewJob by remember { mutableStateOf<Job?>(null) }
    val spriteLoader = remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient { OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build() }
            .build()
    }
    DisposableEffect(spriteLoader) { onDispose { spriteLoader.shutdown() } }
    val sources = remember(stream) { stream?.resolvedCandidates.orEmpty().distinctBy { it.uri } }
    var spriteFrames by remember(stream) { mutableStateOf(stream?.spriteFrames.orEmpty()) }

    LaunchedEffect(stream, client) {
        val media = stream ?: return@LaunchedEffect
        val vtt = media.spriteVttUrl ?: return@LaunchedEffect
        if (spriteFrames.isNotEmpty()) return@LaunchedEffect
        try {
            spriteFrames = client.fetchSpriteFrames(vtt)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // A missing sprite must not block seeking or playback.
        }
    }

    fun cancelScrub(resume: Boolean) {
        val shouldResume = scrubbing && scrubResume && resume
        heldDirection = 0
        holdJob?.cancel()
        holdJob = null
        hidePreviewJob?.cancel()
        hidePreviewJob = null
        scrubbing = false
        previewTarget = null
        if (shouldResume) controller.resumePlaybackIfDesired(true)
    }

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
        cancelScrub(resume = false)
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
                cancelScrub(resume = false)
                playbackError = error.errorCodeName
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !closing) {
                cancelScrub(resume = false)
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
    val showControls = (controlsVisible && !scrubbing) || hasError
    LaunchedEffect(showControls, hasError) {
        if (showControls) controlsFocus.requestFocus() else surfaceFocus.requestFocus()
    }
    BackHandler {
        if (scrubbing || heldDirection != 0) cancelScrub(resume = true)
        else if (controlsVisible && !hasError) controlsVisible = false else closePlayer()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            val direction = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> 0
            }
            if (direction != 0 && !closing && !hasError) {
                when (native.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (native.repeatCount == 0 && heldDirection == 0) {
                            heldDirection = direction
                            hidePreviewJob?.cancel()
                            previewTarget = null
                            val startedAt = SystemClock.uptimeMillis()
                            holdJob = scope.launch {
                                delay(ViewConfiguration.getLongPressTimeout().toLong())
                                if (heldDirection != direction || !ready || duration <= 0L) return@launch
                                scrubResume = player.playWhenReady
                                previewTarget = player.currentPosition.coerceAtLeast(0L)
                                controller.holdPlaybackForSeekPreview()
                                scrubbing = true
                                while (heldDirection == direction && scrubbing) {
                                    previewTarget = nextTvScrubTarget(
                                        previewTarget ?: 0L, direction,
                                        SystemClock.uptimeMillis() - startedAt, duration,
                                    )
                                    delay(250)
                                }
                            }
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        if (heldDirection == direction) {
                            holdJob?.cancel()
                            holdJob = null
                            heldDirection = 0
                            if (native.isCanceled) {
                                cancelScrub(resume = true)
                            } else if (scrubbing) {
                                val target = previewTarget ?: player.currentPosition
                                scrubbing = false
                                controller.seekTo(target, resumePlayback = scrubResume)
                                hidePreviewJob = scope.launch {
                                    delay(1_500)
                                    previewTarget = null
                                }
                            } else if (controlsVisible) {
                                focusManager.moveFocus(if (direction < 0) FocusDirection.Left else FocusDirection.Right)
                            } else {
                                seek(direction * 10_000L)
                            }
                        }
                    }
                }
                return@onPreviewKeyEvent true
            }
            val action = when {
                closing || hasError -> null
                code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ::togglePlayback
                code == KeyEvent.KEYCODE_MEDIA_PLAY -> ({ player.play() })
                code == KeyEvent.KEYCODE_MEDIA_PAUSE -> controller::pause
                code == KeyEvent.KEYCODE_MEDIA_REWIND -> ({ seek(-10_000) })
                code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ({ seek(10_000) })
                showControls -> null
                code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER -> ::togglePlayback
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
        previewTarget?.takeIf { !hasError }?.let { target ->
            TvSeekPreview(
                targetMs = target,
                durationMs = duration,
                frame = findStashSpriteAtTime(spriteFrames, target / 1000.0),
                profile = profile,
                imageLoader = spriteLoader,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
