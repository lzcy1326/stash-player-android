package gomeng.dev.stashplayer.feature.studios

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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudiosRouteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun favoriteFilterAndSortAreSentToServerAndCanBeCleared() {
        val request = AtomicReference<Pair<String, Map<String, Any?>?>>()
        compose.setContent {
            MaterialTheme {
                StudiosRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, _, _, sort, _, filter ->
                    request.set(sort to filter)
                    StashStudioPage(listOf(card(1)), 1)
                })
            }
        }
        compose.waitUntil(10_000) { request.get() != null }
        compose.onNodeWithText("Name").performClick()
        compose.onNodeWithText("Latest scene").performClick()
        compose.waitUntil(10_000) { request.get()?.first == "latest_scene" }
        compose.onNodeWithText("Studio filters (0)").performClick()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))[0].performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(10_000) { request.get()?.second?.get("favorite") == true }
        compose.onNodeWithText("Studio filters (1)").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(10_000) { request.get()?.second == null }
    }

    @Test fun secondPageFailureRetriesWithoutSkippingOrDuplicating() {
        val pageTwoAttempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                StudiosRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, page, _, _, _, _ ->
                    if (page == 2 && pageTwoAttempts.incrementAndGet() == 1) error("offline")
                    val ids = if (page == 1) 1..40 else 41..41
                    StashStudioPage(ids.map { card(it) }, 41)
                })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Studio 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("studios-grid").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        compose.waitUntil(10_000) { pageTwoAttempts.get() == 1 }
        compose.onNodeWithText("Unable to load studios").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { pageTwoAttempts.get() == 2 }
        compose.onNodeWithTag("studios-grid").performScrollToNode(hasText("Studio 41"))
        compose.onNodeWithText("Studio 41").assertExists()
        compose.onNodeWithText("Load more").assertDoesNotExist()
    }

    @Test fun lateSearchResponseCannotOverwriteNewerSearch() {
        val slowStarted = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                StudiosRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, _, query, _, _, _ ->
                    if (query == "old") {
                        slowStarted.incrementAndGet()
                        withContext(NonCancellable) { delay(1_500) }
                    }
                    val name = if (query.isEmpty()) "Initial" else query
                    StashStudioPage(listOf(card(1).copy(name = name)), 1)
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

    private fun card(id: Int) = StashStudioCard(id = id.toString(), name = "Studio $id")
}
