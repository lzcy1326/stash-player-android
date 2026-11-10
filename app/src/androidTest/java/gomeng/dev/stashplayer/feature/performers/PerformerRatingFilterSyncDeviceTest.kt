package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerRatingFilterSyncDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ratingChangeRefreshesFilteredListAndRetryKeepsOldResults() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val rating = AtomicInteger(80)
        val filteredRequests = AtomicInteger()
        val offline = AtomicBoolean(false)
        val scope = MainScope()
        fun card() = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, rating.get(), 0)
        val coordinator = PerformerMutationCoordinator(profile, scope, updateRating = { _, id, value ->
            rating.set(requireNotNull(value))
            StashPerformerMutableFields(id, false, value)
        })
        var showDetail by mutableStateOf(false)
        try {
            compose.setContent {
                MaterialTheme {
                    if (showDetail) PerformerDetailRoute("1", false, profile, onBack = { showDetail = false }, mutations = coordinator,
                        loadDetail = { _, _ -> StashPerformerDetail(card(), emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList()) },
                        loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                    else {
                        val listState: PerformerListViewModel = viewModel()
                        LaunchedEffect(Unit) { listState.filter = PerformerFilter(ratingMin = 60) }
                        PerformersRoute(profile, mutations = coordinator, onOpenPerformer = { _, _ -> showDetail = true },
                            loadPage = { _, _, _, _, _, filter ->
                                if (filter != null) filteredRequests.incrementAndGet()
                                if (offline.get()) error("offline")
                                if (rating.get() >= 60) StashPerformerPage(listOf(card()), 1) else StashPerformerPage(emptyList(), 0)
                            })
                    }
                }
            }
            compose.waitUntil(10_000) { filteredRequests.get() == 1 && compose.onAllNodes(hasText("Actor")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Actor").performClick()
            val secondStar = hasContentDescription("2-star rating") and hasClickAction()
            compose.waitUntil(10_000) { compose.onAllNodes(secondStar, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(secondStar, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { rating.get() == 40 && coordinator.statuses.value["1"]?.busy == false }
            offline.set(true)
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Could not load performers")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            assertEquals("A failed refresh should not be automatically duplicated", 2, filteredRequests.get())
            compose.onNodeWithText("Actor").assertExists()
            offline.set(false)
            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(10_000) { filteredRequests.get() >= 3 && compose.onAllNodes(hasText("Actor")).fetchSemanticsNodes().isEmpty() }
        } finally {
            scope.cancel()
        }
    }
}
