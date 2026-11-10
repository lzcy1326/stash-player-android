package gomeng.dev.stashplayer.feature.performers

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
class PerformerTagGraphQlDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tagPickerSendsCriterionAndShowsOnlyMatchingActor() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val received = AtomicReference<JSONObject>()
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "performer-tag-fixture") {
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
                        val filter = request.optJSONObject("variables")?.optJSONObject("performerFilter")
                        if (filter != null) received.set(filter)
                        val tagged = filter?.optJSONObject("tags")?.optJSONArray("value")?.optString(0) == "tag-1"
                        val actors = if (tagged) """[{"id":"1","name":"Tagged actor"}]""" else """[{"id":"1","name":"Tagged actor"},{"id":"2","name":"Other actor"}]"""
                        """{"data":{"findPerformers":{"count":${if (tagged) 1 else 2},"performers":$actors}}}"""
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
            compose.setContent { MaterialTheme { PerformersRoute(profile) } }
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Other actor")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Performer filters").performClick()
            compose.onNodeWithText("Tags (0)").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Fixture tag")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture tag").performClick()
            compose.onNodeWithContentDescription("Apply tag filter").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Tags (1)")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(15_000) {
                received.get() != null && compose.onAllNodes(hasText("Tagged actor")).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(hasText("Other actor")).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("Tagged actor").assertExists()
            val tags = requireNotNull(received.get()).getJSONObject("tags")
            assertEquals("tag-1", tags.getJSONArray("value").getString(0))
            assertEquals("INCLUDES_ALL", tags.getString("modifier"))
            assertEquals(0, tags.getInt("depth"))
            compose.onNodeWithText("Performer filters").performClick()
            compose.onNodeWithText("Clear").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Other actor")).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            server.close()
            worker.join(2_000)
        }
    }
}
