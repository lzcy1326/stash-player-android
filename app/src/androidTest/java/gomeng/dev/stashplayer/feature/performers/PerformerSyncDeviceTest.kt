package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerDetail
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerSyncDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun favoriteChangesFollowPerformerBetweenListAndDetail() {
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val serverFavorite = AtomicBoolean(false)
        val scope = MainScope()
        val coordinator = PerformerMutationCoordinator(profile, scope, updateFavorite = { _, id, value ->
            serverFavorite.set(value)
            StashPerformerMutableFields(id, value, null)
        })
        fun card() = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, serverFavorite.get(), null, 0)
        fun detail() = StashPerformerDetail(card(), emptyList(), null, null, null, null, null, null, null, null, emptyList(), emptyList())
        var showDetail by mutableStateOf(false)
        try {
            compose.setContent {
                MaterialTheme {
                    if (showDetail) {
                        PerformerDetailRoute("1", false, profile, onBack = { showDetail = false }, mutations = coordinator,
                            loadDetail = { _, _ -> detail() },
                            loadSummary = { _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                            loadScenes = { _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                    } else {
                        PerformersRoute(profile, mutations = coordinator, onOpenPerformer = { _, _ -> showDetail = true },
                            loadPage = { _, _, _, _, _, _ -> StashPerformerPage(listOf(card()), 1) })
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Add performer favorite")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Add performer favorite").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Remove performer favorite")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Actor").performClick()
            compose.waitUntil(10_000) { showDetail && compose.onAllNodes(hasText("Actor")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Remove performer favorite").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Add performer favorite")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10_000) { !showDetail && compose.onAllNodes(hasContentDescription("Add performer favorite")).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            scope.cancel()
        }
    }
}
