package gomeng.dev.stashplayer.feature.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashTvPairingReceiver
import gomeng.dev.stashplayer.core.network.StashTvPairingResult
import gomeng.dev.stashplayer.core.network.stashTvPairingAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TvServerPairingDialog(
    onDismiss: () -> Unit,
    saveProfile: suspend (StashServerProfile) -> Unit,
    onConnected: (TvConnectedServer) -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var claimed by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(R.string.tv_loading) }
    var error by remember { mutableStateOf<Int?>(null) }
    val closeFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        qr = null
        claimed = false
        status = R.string.tv_loading
        error = null
        val address = context.stashTvPairingAddress()
        if (address == null) {
            status = R.string.tv_pairing_no_network
            return@LaunchedEffect
        }
        try {
            val connected = withContext(Dispatchers.IO) {
                StashTvPairingReceiver(address).use { receiver ->
                    val bitmap = BarcodeEncoder().encodeBitmap(receiver.code.encode(), BarcodeFormat.QR_CODE, 420, 420)
                    withContext(Dispatchers.Main) {
                        qr = bitmap
                        status = R.string.tv_pairing_waiting
                    }
                    var saved: TvConnectedServer? = null
                    receiver.receive(onClaimChanged = { value ->
                        withContext(Dispatchers.Main) { claimed = value }
                    }) { draft ->
                        withContext(Dispatchers.Main) {
                            checking = true
                            error = null
                        }
                        try {
                            val result = connectTvServer(draft)
                            saveProfile(result.saved)
                            saved = result
                            StashTvPairingResult.Saved
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            withContext(Dispatchers.Main) {
                                error = tvConnectionError(failure)
                                checking = false
                            }
                            StashTvPairingResult.Rejected
                        }
                    }
                    saved
                }
            }
            if (connected != null) {
                onConnected(connected)
            } else {
                qr = null
                status = R.string.tv_pairing_expired
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            qr = null
            checking = false
            status = R.string.tv_pairing_start_failed
        }
    }

    LaunchedEffect(checking) {
        if (!checking) closeFocus.requestFocus()
    }
    Dialog(
        onDismissRequest = { if (!checking) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !checking,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.widthIn(max = 760.dp).padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                qr?.let {
                    val masked = claimed || checking
                    val preview = remember(it, masked) {
                        // Downsample below a QR module grid, including on Android 11 where Compose blur is unavailable.
                        (if (masked) Bitmap.createScaledBitmap(it, 10, 10, true) else it).asImageBitmap()
                    }
                    Image(
                        bitmap = preview,
                        contentDescription = if (masked) null else stringResource(R.string.tv_pairing_title),
                        modifier = Modifier.size(230.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tv_pairing_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(when {
                        checking -> R.string.tv_connecting
                        claimed -> R.string.tv_pairing_scanned
                        else -> status
                    }))
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(
                            stringResource(R.string.tv_back),
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(closeFocus),
                            enabled = !checking,
                        )
                        TvButton(
                            stringResource(R.string.tv_pairing_refresh),
                            onClick = { attempt++ },
                            modifier = Modifier.focusRequester(refreshFocus),
                            enabled = !checking,
                        )
                    }
                }
            }
        }
    }
}
