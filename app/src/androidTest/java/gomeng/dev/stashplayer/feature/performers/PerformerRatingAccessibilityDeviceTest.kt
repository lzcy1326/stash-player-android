package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerRatingAccessibilityDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentRatingAndUnselectedStarAreDescribedAccurately() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, 20, 0)
        val detail = StashPerformerDetail(card, emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList())
        val scope = MainScope()
        val coordinator = PerformerMutationCoordinator(profile, scope, updateRating = { _, id, rating ->
            StashPerformerMutableFields(id, false, rating)
        })
        try {
            compose.setContent {
                MaterialTheme {
                    PerformerDetailRoute("1", false, profile, onBack = {}, mutations = coordinator,
                        loadDetail = { _, _ -> detail },
                        loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Performer rating 1-star rating"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Performer rating 1-star rating", useUnmergedTree = true).assertExists()
            compose.onNode(hasContentDescription("4-star rating") and hasClickAction(), useUnmergedTree = true).assertExists()
        } finally {
            scope.cancel()
        }
    }
}
