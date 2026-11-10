package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerSortDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSortOptionsAndDirectionsReachPageLoader() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val request = AtomicReference<Pair<String, String>>()
        val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, null, 0)
        compose.setContent {
            MaterialTheme {
                PerformersRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, _, _, sort, direction, _ ->
                    request.set(sort to direction)
                    StashPerformerPage(listOf(card), 1)
                })
            }
        }
        compose.waitUntil(10_000) { request.get() == ("name" to "ASC") }
        var current = "Name"
        listOf("Rating" to "rating", "Scene count" to "scenes_count", "Latest scene" to "latest_scene",
            "Play count" to "play_count", "Updated" to "updated_at", "Random" to "random", "Name" to "name").forEach { (label, key) ->
            compose.onNodeWithText(current).performClick()
            compose.onNodeWithText(label).performClick()
            val initialDirection = if (key == "name") "ASC" else "DESC"
            fun matches(direction: String): Boolean = request.get()?.let { (sort, actualDirection) ->
                (if (key == "random") sort.matches(Regex("random_\\d+")) else sort == key) && actualDirection == direction
            } == true
            compose.waitUntil(10_000) { matches(initialDirection) }
            compose.onNodeWithContentDescription(if (initialDirection == "ASC") "Ascending ↑" else "Descending ↓").performClick()
            compose.waitUntil(10_000) { matches(if (initialDirection == "ASC") "DESC" else "ASC") }
            current = label
        }
    }
}
