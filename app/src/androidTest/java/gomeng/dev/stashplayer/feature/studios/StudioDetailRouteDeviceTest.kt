package gomeng.dev.stashplayer.feature.studios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioDetailRouteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailFailureDoesNotBlockSummaryAndRetriesIndependently() {
        val attempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                StudioDetailRoute("1", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    loadDetail = { _, _ -> if (attempts.incrementAndGet() == 1) error("offline") else detail() },
                    loadSummary = { _, _, _ -> StashPerformerSceneSummary(2, 120.0, 1024.0) },
                    loadScenes = { _, _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Unable to load this studio")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 scenes · 02:00 · 1.0 KB").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Example")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unable to load this studio").assertDoesNotExist()
    }

    @Test fun summaryFailureDoesNotBlockDetailAndRetriesIndependently() {
        val attempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                StudioDetailRoute("1", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    loadDetail = { _, _ -> detail() },
                    loadSummary = { _, _, _ -> if (attempts.incrementAndGet() == 1) error("offline") else StashPerformerSceneSummary(2, 120.0, 1024.0) },
                    loadScenes = { _, _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Could not load scenes")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("Example")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 scenes · 02:00 · 1.0 KB")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun scenePlaybackCapturesStudioAndPagingContext() {
        val continuation = AtomicReference<PlayerPlaybackQueueContinuation?>()
        val requestedStudio = AtomicReference<String>()
        compose.setContent {
            MaterialTheme {
                StudioDetailRoute("42", true, StashServerProfile(baseUrl = "http://localhost"), {},
                    onOpenScene = { _, _, _, next -> continuation.set(next) },
                    loadDetail = { _, _ -> detail() },
                    loadSummary = { _, _, _ -> StashPerformerSceneSummary(13, 120.0, 1024.0) },
                    loadScenes = { _, id, _, _, _, _, _, _ ->
                        requestedStudio.set(id)
                        StashSceneCardPage((1..12).map { SceneCardModel("$it", "Scene $it", "", "", 0f) }, 13)
                    },
                )
            }
        }
        compose.waitUntil(10_000) { requestedStudio.get() != null }
        compose.onNodeWithTag("studio-detail-list").performScrollToNode(hasText("Scene 1"))
        compose.onNodeWithText("Scene 1").performClick()
        val next = continuation.get() as PlayerPlaybackQueueContinuation.Studio
        assertEquals("42", requestedStudio.get())
        assertEquals("42", next.studioId)
        assertEquals(2, next.nextPage)
        assertTrue(next.hasMore)
    }

    private fun detail() = StashStudioCard("1", "Example", sceneCount = 2)
}
