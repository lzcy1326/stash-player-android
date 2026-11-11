package gomeng.dev.stashplayer.feature.studios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashServerAuthMode
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioTagGraphQlDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tagPickerSendsCriterionAndShowsOnlyMatchingStudio() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val received = AtomicReference<JSONObject>()
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "studio-tag-fixture") {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
                socket.use {
                    val input = it.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val byte = input.read()
                        check(byte >= 0)
                        header.append(byte.toChar())
                    }
                    val size = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val request = JSONObject(String(input.readNBytes(size), Charsets.UTF_8))
                    val query = request.getString("query")
                    val body = if (query.contains("FindTags")) {
                        """{"data":{"findTags":{"count":1,"tags":[{"id":"tag-1","name":"Fixture tag"}]}}}"""
                    } else {
                        val filter = request.optJSONObject("variables")?.optJSONObject("studioFilter")
                        if (filter != null) received.set(filter)
                        val tagged = filter?.optJSONObject("tags")?.optJSONArray("value")?.optString(0) == "tag-1"
                        val studios = if (tagged) """[{"id":"1","name":"Tagged studio"}]""" else """[{"id":"1","name":"Tagged studio"},{"id":"2","name":"Other studio"}]"""
                        """{"data":{"findStudios":{"count":${if (tagged) 1 else 2},"studios":$studios}}}"""
                    }.toByteArray(Charsets.UTF_8)
                    val output = it.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.write(body)
                    output.flush()
                }
            }
        }
        try {
            val profile = StashServerProfile(baseUrl = "http://127.0.0.1:${server.localPort}", authMode = StashServerAuthMode.None)
            compose.setContent { MaterialTheme { StudiosRoute(profile) } }
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Other studio")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Studio filters (0)").performClick()
            compose.onNodeWithText("Tags (0)").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Fixture tag")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture tag").performClick()
            compose.onNodeWithContentDescription("Apply tag filter").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Tags (1)")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(15_000) {
                received.get() != null && compose.onAllNodes(hasText("Tagged studio")).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(hasText("Other studio")).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("Tagged studio").assertExists()
            val tags = requireNotNull(received.get()).getJSONObject("tags")
            assertEquals("tag-1", tags.getJSONArray("value").getString(0))
            assertEquals("INCLUDES_ALL", tags.getString("modifier"))
            assertEquals(0, tags.getInt("depth"))
            compose.onNodeWithText("Studio filters (1)").performClick()
            compose.onNodeWithText("Clear").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Other studio")).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            server.close()
            worker.join(2_000)
        }
    }
}
