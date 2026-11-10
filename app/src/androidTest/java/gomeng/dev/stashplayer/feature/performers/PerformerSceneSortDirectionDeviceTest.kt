package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerSceneSortDirectionDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directionButtonTogglesSceneSortOrder() {
        val direction = AtomicReference<StashSortDirection>()
        val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, null, 0)
        compose.setContent {
            MaterialTheme {
                PerformerDetailRoute("1", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    loadDetail = { _, _ -> StashPerformerDetail(card, emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList()) },
                    loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                    loadScenes = { _, _, _, _, _, value, _ -> direction.set(value); StashSceneCardPage(emptyList(), 0) })
            }
        }
        compose.waitUntil(10_000) { direction.get() == StashSortDirection.Desc && compose.onAllNodes(hasText("Recently released")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Descending ↓").performClick()
        compose.waitUntil(10_000) { direction.get() == StashSortDirection.Asc }
        compose.onNodeWithContentDescription("Ascending ↑").performClick()
        compose.waitUntil(10_000) { direction.get() == StashSortDirection.Desc }
    }
}
