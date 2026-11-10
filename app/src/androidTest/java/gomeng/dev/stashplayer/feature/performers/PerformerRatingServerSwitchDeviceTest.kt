package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerRatingServerSwitchDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateRatingResponseFromOldServerDoesNotChangeNewServer() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val oldProfile = StashServerProfile(baseUrl = "http://old.example")
        val newProfile = StashServerProfile(baseUrl = "http://new.example")
        val result = CompletableDeferred<StashPerformerMutableFields>()
        val scope = MainScope()
        val oldCoordinator = PerformerMutationCoordinator(oldProfile, scope, updateRating = { _, _, _ -> result.await() })
        val newCoordinator = PerformerMutationCoordinator(newProfile, scope)
        var useNew by mutableStateOf(false)
        fun detail(rating: Int?) = StashPerformerDetail(
            StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, rating, 0),
            emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList(),
        )
        val fourthStar = hasContentDescription("4-star rating") and hasClickAction()
        try {
            compose.setContent {
                MaterialTheme {
                    PerformerDetailRoute("1", false, if (useNew) newProfile else oldProfile, onBack = {},
                        mutations = if (useNew) newCoordinator else oldCoordinator,
                        loadDetail = { profile, _ -> detail(if (profile == oldProfile) null else 40) },
                        loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(fourthStar, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(fourthStar, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { oldCoordinator.statuses.value["1"]?.busy == true }
            compose.runOnIdle { useNew = true }
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Performer rating 2-star rating"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            result.complete(StashPerformerMutableFields("1", false, 80))
            compose.waitUntil(10_000) { oldCoordinator.statuses.value["1"]?.busy == false }
            compose.onNodeWithContentDescription("Performer rating 2-star rating", useUnmergedTree = true).assertExists()
            compose.onNodeWithContentDescription("Performer rating 4-star rating", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }
}
