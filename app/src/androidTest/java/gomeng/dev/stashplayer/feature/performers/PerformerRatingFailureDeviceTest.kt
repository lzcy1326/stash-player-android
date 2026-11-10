package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerRatingFailureDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedRatingRestoresDisplayedValueAndRetrySucceeds() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, null, 0)
        val detail = StashPerformerDetail(card, emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList())
        val attempts = AtomicInteger()
        val scope = MainScope()
        val coordinator = PerformerMutationCoordinator(profile, scope,
            updateRating = { _, id, rating ->
                if (attempts.incrementAndGet() == 1) error("offline")
                StashPerformerMutableFields(id, false, rating)
            },
            readFields = { _, _ -> null },
        )
        val star = hasContentDescription("4-star rating") and hasClickAction()
        try {
            compose.setContent {
                MaterialTheme {
                    PerformerDetailRoute("1", false, profile, onBack = {}, mutations = coordinator,
                        loadDetail = { _, _ -> detail },
                        loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(star, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(star, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Update failed. Try again.")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Performer rating No rating", useUnmergedTree = true).assertExists()
            compose.onNode(star, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Performer rating 4-star rating"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            scope.cancel()
        }
    }
}
