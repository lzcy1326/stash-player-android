package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerDetailRouteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailFailureDoesNotBlockSummaryAndRetriesIndependently() {
        val attempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                PerformerDetailRoute("1", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    loadDetail = { _, _ -> if (attempts.incrementAndGet() == 1) error("offline") else detail() },
                    loadSummary = { _, _ -> StashPerformerSceneSummary(2, 120.0, 1024.0) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Could not load performer")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 scenes · 02:00 · 1.0 KB").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Example")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Could not load performer").assertDoesNotExist()
    }

    @Test fun summaryFailureDoesNotBlockDetailAndRetriesIndependently() {
        val attempts = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                PerformerDetailRoute("1", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    loadDetail = { _, _ -> detail() },
                    loadSummary = { _, _ -> if (attempts.incrementAndGet() == 1) error("offline") else StashPerformerSceneSummary(2, 120.0, 1024.0) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Could not load scenes")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("Example")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2 scenes · 02:00 · 1.0 KB")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun detail() = StashPerformerDetail(
        card = StashPerformerCard("1", "Example", emptyList(), null, null, null, null, null, false, null, 2),
        urls = emptyList(), ethnicity = null, eyeColor = null, heightCm = null, measurements = null,
        careerStart = null, careerEnd = null, hairColor = null, details = null, tags = emptyList(), stashIds = emptyList(),
    )
}
