package gomeng.dev.stashplayer.feature.settings

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.AppUpdateChecker
import gomeng.dev.stashplayer.core.network.AppUpdateInstaller
import gomeng.dev.stashplayer.core.network.AppUpdateNotice
import gomeng.dev.stashplayer.core.network.UpdateDownload
import gomeng.dev.stashplayer.core.network.UpdateDownloadPhase
import gomeng.dev.stashplayer.core.ui.components.openSupportUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneUpdateCard(
    currentVersion: String,
    notice: AppUpdateNotice?,
    onNoticeChange: (AppUpdateNotice?) -> Unit,
) {
    val context = LocalContext.current
    val installer = remember(context.applicationContext) { AppUpdateInstaller(context) }
    val checker = remember { AppUpdateChecker() }
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<UpdateDownload?>(null) }
    var speed by remember { mutableLongStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf<AppUpdateNotice?>(null) }

    LaunchedEffect(installer, currentVersion, refresh) {
        var previous: UpdateDownload? = null
        var sampledAt = SystemClock.elapsedRealtime()
        do {
            val next = try {
                withContext(Dispatchers.IO) { installer.current(currentVersion) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = R.string.settings_support_update_retry_message
                task?.copy(phase = UpdateDownloadPhase.Failed)
            }
            val now = SystemClock.elapsedRealtime()
            val last = previous
            speed = if (next?.phase == UpdateDownloadPhase.Downloading &&
                last?.phase == UpdateDownloadPhase.Downloading && next.id == last.id &&
                next.downloadedBytes >= last.downloadedBytes
            ) {
                val sample = (next.downloadedBytes - last.downloadedBytes) * 1000.0 / (now - sampledAt).coerceAtLeast(1)
                if (speed == 0L) sample.toLong() else (speed * 0.55 + sample * 0.45).toLong()
            } else 0L
            task = next
            previous = next
            sampledAt = now
            loaded = true
            if (next == null || next.phase == UpdateDownloadPhase.Complete || next.phase == UpdateDownloadPhase.Failed) break
            delay(1000)
        } while (true)
    }

    val activeTask = task
    val version = activeTask?.version ?: notice?.latestVersionName
    val matchingNotice = notice?.takeIf { it.latestVersionName == version }
    val phase = activeTask?.phase
    val active = phase == UpdateDownloadPhase.Downloading || phase == UpdateDownloadPhase.Pending ||
        phase == UpdateDownloadPhase.Waiting
    val button = when {
        phase == UpdateDownloadPhase.Complete -> R.string.settings_support_update_install_action
        phase == UpdateDownloadPhase.Failed -> R.string.settings_support_update_retry_action
        starting -> R.string.settings_support_update_preparing
        checking -> R.string.settings_support_update_checking
        notice?.apkAsset != null -> R.string.settings_support_update_download_button
        notice != null -> R.string.settings_support_update_open_action
        else -> R.string.settings_support_update_check_button
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_support_update_check_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_support_installed_version, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (version != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(R.string.settings_support_update_target, version),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    if (matchingNotice != null) {
                        TextButton(onClick = { notes = matchingNotice }) {
                            Text(stringResource(R.string.settings_support_update_changelog_action))
                        }
                    }
                }
            }
            val message = when {
                phase == UpdateDownloadPhase.Waiting -> R.string.settings_support_update_waiting_network
                phase == UpdateDownloadPhase.Failed -> R.string.settings_support_update_retry_message
                phase == UpdateDownloadPhase.Complete -> status ?: R.string.settings_support_update_downloaded
                else -> status
            }
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            if (activeTask != null && active) {
                DownloadProgress(activeTask, speed)
            } else {
                Button(enabled = loaded && !checking && !starting, onClick = {
                    status = null
                    if (phase == UpdateDownloadPhase.Complete) {
                        runCatching {
                            if (installer.canRequestPackageInstalls()) {
                                installer.launchInstaller(context, activeTask)
                            } else {
                                installer.openUnknownAppSourcesSettings(context)
                                status = R.string.settings_support_update_install_permission_required
                            }
                        }.onFailure {
                            status = R.string.settings_support_update_install_failed
                            refresh++
                        }
                    } else if (notice != null && notice.apkAsset == null && phase != UpdateDownloadPhase.Failed) {
                        openSupportUrl(context, notice.releaseUrl)
                    } else {
                        val shouldDownload = notice?.apkAsset != null || phase == UpdateDownloadPhase.Failed
                        starting = shouldDownload
                        checking = !shouldDownload
                        scope.launch {
                            try {
                                val release = notice?.takeIf { it.apkAsset != null }
                                    ?: checker.checkForUpdate(currentVersion)
                                onNoticeChange(release)
                                val asset = release?.apkAsset
                                if (shouldDownload && release != null && asset != null) {
                                    task = withContext(Dispatchers.IO) {
                                        installer.enqueue(currentVersion, release.latestVersionName, asset)
                                    }
                                    refresh++
                                } else if (release == null) {
                                    status = R.string.settings_support_update_up_to_date
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                status = if (shouldDownload) R.string.settings_support_update_retry_message
                                else R.string.settings_support_update_failed
                            } finally {
                                starting = false
                                checking = false
                            }
                        }
                    }
                }) { Text(stringResource(button)) }
            }
        }
    }
    notes?.let { release ->
        AlertDialog(
            onDismissRequest = { notes = null },
            title = { Text(stringResource(R.string.settings_support_update_changelog_title, release.latestVersionName)) },
            text = { Text(release.changelog ?: stringResource(R.string.settings_support_update_changelog_empty)) },
            confirmButton = {
                TextButton(onClick = { notes = null }) { Text(stringResource(R.string.settings_support_update_changelog_close)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadProgress(task: UpdateDownload, speed: Long) {
    val context = LocalContext.current
    val progress = if (task.totalBytes > 0) {
        (task.downloadedBytes.toDouble() / task.totalBytes).coerceIn(0.0, 1.0)
    } else null
    val label = when {
        progress == 1.0 -> stringResource(R.string.settings_support_update_verifying)
        progress != null -> stringResource(R.string.settings_support_update_progress, floor(progress * 100).toInt())
        else -> stringResource(R.string.settings_support_update_downloading)
    }
    val colors = MaterialTheme.colorScheme
    val fill = if (colors.onSurface.luminance() > 0.5f) colors.primary else colors.primaryContainer
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val track = Modifier.widthIn(min = 176.dp).heightIn(min = 44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f))
            .then(if (progress != null) Modifier.progressSemantics(progress.toFloat()) else Modifier.progressSemantics())
        Box(track.align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
            if (progress != null) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat()).background(fill))
                }
            }
            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        val detail = when {
            task.phase == UpdateDownloadPhase.Downloading && speed > 0 && progress != 1.0 ->
                stringResource(R.string.settings_support_update_speed, Formatter.formatShortFileSize(context, speed))
            progress == null && task.downloadedBytes > 0 -> Formatter.formatShortFileSize(context, task.downloadedBytes)
            else -> null
        }
        detail?.let { Text(it, modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall) }
    }
}
