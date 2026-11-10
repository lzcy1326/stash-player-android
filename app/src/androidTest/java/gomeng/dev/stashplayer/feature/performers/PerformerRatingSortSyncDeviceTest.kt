package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerPage
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
class PerformerRatingSortSyncDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ratingChangeRefreshesSortedListWhenReturningFromDetail() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val actorRating = AtomicInteger(20)
        val ratingSortedRequests = AtomicInteger()
        val scope = MainScope()
        fun card(id: String, name: String, rating: Int) = StashPerformerCard(id, name, emptyList(), null, null, null, null, null, false, rating, 0)
        fun detail() = StashPerformerDetail(card("1", "Actor", actorRating.get()), emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList())
        val coordinator = PerformerMutationCoordinator(profile, scope, updateRating = { _, id, rating ->
            actorRating.set(requireNotNull(rating))
            StashPerformerMutableFields(id, false, rating)
        })
        var showDetail by mutableStateOf(false)
        try {
            compose.setContent {
                MaterialTheme {
                    if (showDetail) PerformerDetailRoute("1", false, profile, onBack = { showDetail = false }, mutations = coordinator,
                        loadDetail = { _, _ -> detail() },
                        loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                    else PerformersRoute(profile, mutations = coordinator, onOpenPerformer = { _, _ -> showDetail = true },
                        loadPage = { _, _, _, sort, _, _ ->
                            if (sort == "rating") ratingSortedRequests.incrementAndGet()
                            val actor = card("1", "Actor", actorRating.get())
                            val other = card("2", "Other", 60)
                            StashPerformerPage(if (actor.rating100!! > other.rating100!!) listOf(actor, other) else listOf(other, actor), 2)
                        })
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Actor")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Name").performClick()
            compose.onNodeWithText("Rating").performClick()
            compose.waitUntil(10_000) { ratingSortedRequests.get() >= 1 }
            compose.onNodeWithText("Actor").performClick()
            val fourthStar = hasContentDescription("4-star rating") and hasClickAction()
            compose.waitUntil(10_000) { compose.onAllNodes(fourthStar, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(fourthStar, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { actorRating.get() == 80 && coordinator.statuses.value["1"]?.busy == false }
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10_000) { ratingSortedRequests.get() >= 2 }
            val actorX = compose.onNodeWithText("Actor").fetchSemanticsNode().boundsInRoot.left
            val otherX = compose.onNodeWithText("Other").fetchSemanticsNode().boundsInRoot.left
            check(actorX < otherX) { "Updated rating did not move Actor ahead of Other" }
        } finally {
            scope.cancel()
        }
    }
}
