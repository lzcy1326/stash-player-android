package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerFilterScrollResetDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun applyingFilterReturnsGridToFirstResult() {
        fun card(id: Int, name: String) = StashPerformerCard(id.toString(), name, emptyList(), null, null, null, null, null, false, null, 0)
        compose.setContent {
            MaterialTheme {
                PerformersRoute(StashServerProfile(baseUrl = "http://localhost"), loadPage = { _, _, _, _, _, filter ->
                    val prefix = if (filter?.get("filter_favorites") == true) "Filtered" else "Actor"
                    StashPerformerPage((1..40).map { card(it, "$prefix $it") }, 40)
                })
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Actor 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("performers-grid").performScrollToNode(hasText("Actor 35"))
        compose.onNodeWithText("Performer filters").performClick()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))[0].performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Filtered 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Filtered 1").assertExists()
    }
}
