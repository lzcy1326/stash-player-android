package gomeng.dev.stashplayer.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.network.StashTvPairingCode
import gomeng.dev.stashplayer.core.network.StashTvPairingResult
import gomeng.dev.stashplayer.core.network.pairSavedStashProfileWithTv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private class PairingConfirmation(val serverName: String) {
    val decision = CompletableDeferred<Boolean>()
}

internal data class TvPairingAction(
    val enabled: Boolean,
    val progressMessage: Int?,
    val resultMessage: Int?,
    val onClick: () -> Unit,
)

@Composable
internal fun SendServerToTv(savedProfile: StashServerProfile?, repository: StashSettingsRepository) {
    val action = rememberTvPairingAction(savedProfile != null, repository)
    OutlinedButton(enabled = action.enabled, onClick = action.onClick) {
        Text(stringResource(action.progressMessage ?: R.string.tv_pairing_scan))
    }
    action.resultMessage?.let { Text(stringResource(it)) }
}

@Composable
internal fun rememberTvPairingAction(
    hasSavedProfile: Boolean,
    repository: StashSettingsRepository,
): TvPairingAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var resultMessage by remember { mutableStateOf<Int?>(null) }
    var confirmation by remember { mutableStateOf<PairingConfirmation?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanning = false
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val code = StashTvPairingCode.parse(contents)
        if (code == null) {
            resultMessage = R.string.tv_pairing_invalid
        } else {
            progress = R.string.tv_pairing_connecting
            resultMessage = null
            scope.launch {
                try {
                    val profile = repository.savedServerProfile.first() ?: error("No saved server profile")
                    val result = context.pairSavedStashProfileWithTv(code) { remainingMs ->
                        withContext(Dispatchers.Main) {
                            val request = PairingConfirmation(profile.name)
                            confirmation = request
                            val confirmed = try {
                                withTimeoutOrNull(remainingMs) { request.decision.await() }
                            } finally {
                                confirmation = null
                            }
                            when (confirmed) {
                                true -> {
                                    progress = R.string.tv_pairing_sending
                                    profile
                                }
                                false -> null
                                null -> {
                                    resultMessage = R.string.tv_pairing_confirmation_expired
                                    null
                                }
                            }
                        }
                    }
                    when (result) {
                        StashTvPairingResult.Saved -> resultMessage = R.string.tv_pairing_saved
                        StashTvPairingResult.Rejected -> resultMessage = R.string.tv_pairing_rejected
                        null -> Unit
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    resultMessage = R.string.tv_pairing_send_failed
                } finally {
                    progress = null
                }
            }
        }
    }
    confirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { request.decision.complete(false) },
            title = { Text(stringResource(R.string.tv_pairing_confirm_title)) },
            text = { Text(stringResource(R.string.tv_pairing_confirm_message, request.serverName)) },
            confirmButton = {
                TextButton(onClick = { request.decision.complete(true) }) {
                    Text(stringResource(R.string.tv_pairing_confirm_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { request.decision.complete(false) }) {
                    Text(stringResource(R.string.tv_pairing_cancel))
                }
            },
        )
    }
    return TvPairingAction(
        enabled = hasSavedProfile && hasCamera && progress == null && !scanning,
        progressMessage = progress,
        resultMessage = resultMessage,
        onClick = {
            scanning = true
            resultMessage = null
            scanner.launch(
                ScanOptions()
                    .setCaptureActivity(TvPairingScannerActivity::class.java)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setOrientationLocked(false)
                    .setPrompt(context.getString(R.string.tv_pairing_scan_prompt))
                    .setBeepEnabled(false),
            )
        },
    )
}
