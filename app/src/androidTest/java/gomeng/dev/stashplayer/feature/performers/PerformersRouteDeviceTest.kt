package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformersRouteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun secondPageFailureRetriesWithoutSkippingOrDuplicating() {
        val pageTwoAttempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                PerformersRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, page, _ ->
                    if (page == 2 && pageTwoAttempts.incrementAndGet() == 1) error("offline")
                    val ids = if (page == 1) 1..40 else 41..41
                    StashPerformerPage(ids.map { card(it) }, 41)
                })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Actor 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("performers-grid").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        compose.waitUntil(10_000) { pageTwoAttempts.get() == 1 }
        compose.onNodeWithText("Could not load performers").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { pageTwoAttempts.get() == 2 }
        compose.onNodeWithTag("performers-grid").performScrollToNode(hasText("Actor 41"))
        compose.onNodeWithText("Actor 41").assertExists()
        compose.onNodeWithText("Load more").assertDoesNotExist()
    }

    @Test fun lateSearchResponseCannotOverwriteNewerSearch() {
        val slowStarted = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                PerformersRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, _, query ->
                    if (query == "old") {
                        slowStarted.incrementAndGet()
                        withContext(NonCancellable) { delay(1_500) }
                    }
                    val name = if (query.isEmpty()) "Initial" else query
                    StashPerformerPage(listOf(card(1).copy(name = name)), 1)
                })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Initial")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("old")
        compose.waitUntil(10_000) { slowStarted.get() > 0 }
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("new")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("new")).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2_000)
        compose.onNodeWithText("new").assertExists()
        compose.onNodeWithText("old").assertDoesNotExist()
    }

    private fun card(id: Int) = StashPerformerCard(
        id = id.toString(), name = "Actor $id", aliases = emptyList(), imagePath = null,
        gender = null, country = null, birthdate = null, deathDate = null,
        favorite = false, rating100 = null, sceneCount = 0,
    )
}
