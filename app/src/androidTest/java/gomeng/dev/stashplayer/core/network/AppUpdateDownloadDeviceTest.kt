package gomeng.dev.stashplayer.core.network

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import gomeng.dev.stashplayer.feature.settings.PhoneUpdateCard
import java.io.DataInputStream
import org.junit.Rule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDownloadDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun systemDownloadIsReusedAndCanBeRecoveredByANewInstaller() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.applicationInfo.sourceDir)
        val targetVersion = requireNotNull(context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        val previousVersion = "0.0.1"
        val installer = AppUpdateInstaller(context)
        check(installer.current(previousVersion) == null) { "Use an emulator without an existing update task" }
        val continueDownload = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val output = socket.getOutputStream()
                        output.write(
                            ("HTTP/1.1 200 OK\r\nContent-Length: ${archive.length()}\r\n" +
                                "Content-Type: application/vnd.android.package-archive\r\nConnection: close\r\n\r\n").toByteArray(),
                        )
                        archive.inputStream().use { input ->
                            output.write(ByteArray(1024 * 1024).also { DataInputStream(input).readFully(it) })
                            output.flush()
                            check(continueDownload.await(45, TimeUnit.SECONDS))
                            input.copyTo(output)
                        }
                    }
                }
            }
            try {
                val asset = AppUpdateApkAsset("test-update.apk", "http://127.0.0.1:${server.localPort}/update.apk")
                val task = installer.enqueue(previousVersion, targetVersion, asset)
                val reopened = AppUpdateInstaller(context)
                val inProgress = awaitDownload(reopened, previousVersion) {
                    it.phase == UpdateDownloadPhase.Downloading && it.downloadedBytes > 0
                }
                assertEquals(task.id, inProgress.id)
                assertEquals(archive.length(), inProgress.totalBytes)
                assertTrue(inProgress.downloadedBytes < inProgress.totalBytes)
                assertEquals(task.id, reopened.enqueue(previousVersion, targetVersion, asset).id)

                val showCard = mutableStateOf(true)
                val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
                compose.setContent {
                    if (showCard.value) PhoneUpdateCard(previousVersion, notice = null, onNoticeChange = {})
                }
                compose.waitUntil(5_000) { compose.onAllNodes(progress).fetchSemanticsNodes().isNotEmpty() }
                compose.runOnIdle { showCard.value = false }
                compose.waitUntil(5_000) { compose.onAllNodes(progress).fetchSemanticsNodes().isEmpty() }
                compose.runOnIdle { showCard.value = true }
                compose.waitUntil(5_000) { compose.onAllNodes(progress).fetchSemanticsNodes().isNotEmpty() }

                continueDownload.countDown()
                val complete = awaitDownload(AppUpdateInstaller(context), previousVersion) {
                    it.phase == UpdateDownloadPhase.Complete
                }
                assertEquals(task.id, complete.id)
            } finally {
                continueDownload.countDown()
                installer.current(targetVersion)
            }
        }
    }

    private fun awaitDownload(
        installer: AppUpdateInstaller,
        version: String,
        matches: (UpdateDownload) -> Boolean,
    ): UpdateDownload {
        var last: UpdateDownload? = null
        repeat(100) {
            last = installer.current(version)
            last?.let { if (matches(it)) return it }
            check(last?.phase != UpdateDownloadPhase.Failed) { "Download failed: $last" }
            Thread.sleep(200)
        }
        error("Download state did not converge: $last")
    }
}
