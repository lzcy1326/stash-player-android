package gomeng.dev.stashplayer.feature.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashHttpClient
import gomeng.dev.stashplayer.core.network.StashLoginClient
import gomeng.dev.stashplayer.core.network.StashServerAuthMode
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.TimeUnit

class StashTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFC1A7FF),
                    onPrimary = Color(0xFF211139),
                    background = Color(0xFF101018),
                    surface = Color(0xFF20202D),
                ),
            ) {
                StashTvApp()
            }
        }
    }
}

@Composable
private fun StashTvApp() {
    val context = LocalContext.current
    val repository = remember(context) { StashSettingsRepository(context.applicationContext) }
    var initialized by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<StashServerProfile?>(null) }
    var activeProfile by remember { mutableStateOf<StashServerProfile?>(null) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        val saved = repository.savedServerProfile.first()
        profile = saved
        activeProfile = saved
        initialized = true
        if (saved != null && (saved.fallbackBaseUrl.isNotBlank() || saved.hasPasswordSessionCredentials())) {
            try {
                val connected = connectTvServer(saved)
                if (profile == saved) activeProfile = connected.active
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // The library can still show its connection error and let the user retry or edit.
            }
        }
    }

    TvScreen {
        val server = profile
        when {
            !initialized -> Text(stringResource(R.string.tv_loading))
            server == null || editing -> TvServerSetup(
                profile = server,
                onBack = if (server == null) null else ({ editing = false }),
                saveProfile = repository::saveServerProfile,
                onConnected = {
                    profile = it.saved
                    activeProfile = it.active
                    editing = false
                },
            )
            else -> {
                val connected = activeProfile ?: server
                key(connected) { TvLibrary(connected, onSettings = { editing = true }) }
            }
        }
    }
}

@Composable
private fun TvServerSetup(
    profile: StashServerProfile?,
    onBack: (() -> Unit)?,
    saveProfile: suspend (StashServerProfile) -> Unit,
    onConnected: (TvConnectedServer) -> Unit,
) {
    var address by remember { mutableStateOf(profile?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(profile?.apiKey.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var showPairing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val addressFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    val pairFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun connect() {
        if (busy) return
        val draft = profile?.takeIf { address.trim() == it.baseUrl && apiKey.trim() == it.apiKey } ?: StashServerProfile(
            baseUrl = address.trim(),
            apiKey = apiKey.trim(),
            authMode = if (apiKey.isBlank()) StashServerAuthMode.None else StashServerAuthMode.ApiKey,
        )
        keyboard?.hide()
        error = null
        busy = true
        scope.launch {
            try {
                val connected = connectTvServer(draft)
                saveProfile(connected.saved)
                onConnected(connected)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = tvConnectionError(failure)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { pairFocus.requestFocus() }
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    if (showPairing) {
        TvServerPairingDialog(
            onDismiss = { showPairing = false },
            saveProfile = saveProfile,
            onConnected = onConnected,
        )
    }

    Column(
        Modifier.padding(horizontal = 48.dp, vertical = 24.dp).widthIn(max = 640.dp)
            .fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tv_app_name), style = MaterialTheme.typography.headlineMedium)
        TvButton(
            stringResource(R.string.tv_show_pairing),
            onClick = {
                keyboard?.hide()
                showPairing = true
            },
            modifier = Modifier.focusRequester(pairFocus).focusProperties { down = addressFocus },
            enabled = !busy,
        )
        Text(stringResource(R.string.tv_setup_hint))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.tv_address)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(addressFocus)
                .onTvTextFieldKeys(
                    onEdit = { keyboard?.show() },
                    onUp = { pairFocus.requestFocus() },
                    onDown = { keyFocus.requestFocus() },
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { keyFocus.requestFocus() }),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.tv_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(keyFocus)
                .onTvTextFieldKeys(
                    onEdit = { keyboard?.show() },
                    onUp = { addressFocus.requestFocus() },
                    onDown = { connectFocus.requestFocus() },
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                connectFocus.requestFocus()
            }),
        )
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                stringResource(if (busy) R.string.tv_connecting else R.string.tv_connect),
                onClick = ::connect,
                modifier = Modifier.focusRequester(connectFocus).focusProperties { up = keyFocus },
            )
            if (onBack != null) TvButton(stringResource(R.string.tv_back), onClick = onBack)
        }
    }
}

@Composable
internal fun TvScreen(content: @Composable BoxScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

internal data class TvConnectedServer(val saved: StashServerProfile, val active: StashServerProfile)

private val tvSetupHttpClient by lazy {
    StashHttpClient.shared.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
}

internal suspend fun connectTvServer(profile: StashServerProfile): TvConnectedServer {
    val primary = profile.normalizedBaseUrl()
    val fallback = profile.normalizedFallbackBaseUrl()
    val addresses = listOf(primary, fallback).filter(String::isNotBlank).distinct()
    require(primary.isNotBlank() && addresses.all(::validTvServerAddress))
    val saved = profile.copy(baseUrl = primary, fallbackBaseUrl = fallback)
    var lastFailure: Exception? = null
    for (address in addresses) {
        try {
            var active = saved.copy(baseUrl = address)
            if (active.hasPasswordSessionCredentials()) {
                val login = StashLoginClient(tvSetupHttpClient).loginWithPassword(address, active.username, active.password)
                active = active.copy(sessionCookie = login.sessionCookie)
            }
            check(StashGraphQlClient(active, tvSetupHttpClient).testConnection() != "unknown")
            return TvConnectedServer(saved.copy(sessionCookie = active.sessionCookie), active)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            lastFailure = failure
        }
    }
    throw checkNotNull(lastFailure)
}

private fun validTvServerAddress(address: String): Boolean {
    val uri = runCatching { URI(address) }.getOrNull() ?: return false
    return (uri.scheme == "http" || uri.scheme == "https") && uri.host != null &&
        uri.userInfo == null && uri.query == null && uri.fragment == null && uri.port in -1..65535
}

internal fun tvConnectionError(failure: Exception): Int = when {
    failure is IllegalArgumentException -> R.string.tv_invalid_url
    failure.message.orEmpty().let {
        it.contains("HTTP 401") || it.contains("HTTP 403") || it.contains("unauthorized", ignoreCase = true)
    } -> R.string.tv_auth_error
    else -> R.string.tv_connection_error
}

internal fun Modifier.onTvTextFieldKeys(
    onEdit: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier = onPreviewKeyEvent { event ->
    val nativeEvent = event.nativeKeyEvent
    val move = when (nativeEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER -> onEdit
        KeyEvent.KEYCODE_DPAD_UP -> onUp
        KeyEvent.KEYCODE_DPAD_DOWN -> onDown
        else -> null
    }
    if (move != null && nativeEvent.action == KeyEvent.ACTION_UP) move()
    move != null
}

@Composable
internal fun TvButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) colors.primary else colors.surface,
            contentColor = if (focused) colors.onPrimary else colors.onSurface,
        ),
    ) {
        Text(label)
    }
}
