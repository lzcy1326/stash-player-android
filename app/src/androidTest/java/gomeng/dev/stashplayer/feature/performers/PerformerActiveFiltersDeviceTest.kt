package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerActiveFiltersDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appliedFavoriteConditionIsVisibleAndClearRemovesIt() {
        compose.setContent {
            MaterialTheme {
                PerformersRoute(StashServerProfile(baseUrl = "http://localhost"),
                    loadPage = { _, _, _, _, _, _ -> StashPerformerPage(emptyList(), 0) })
            }
        }
        compose.onNodeWithText("Performer filters").performClick()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))[0].performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Favorites only")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Performer filters").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Favorites only").assertDoesNotExist()
    }
}
