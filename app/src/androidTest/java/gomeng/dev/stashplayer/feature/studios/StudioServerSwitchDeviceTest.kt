package gomeng.dev.stashplayer.feature.studios

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
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioMutableFields
import gomeng.dev.stashplayer.core.network.StashStudioPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioServerSwitchDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateFavoriteResponseFromOldServerDoesNotChangeNewServer() {
        val oldProfile = StashServerProfile(baseUrl = "http://old.example")
        val newProfile = StashServerProfile(baseUrl = "http://new.example")
        val result = CompletableDeferred<StashStudioMutableFields>()
        val scope = MainScope()
        val oldCoordinator = StudioMutationCoordinator(oldProfile, scope, updateFavorite = { _, _, _ -> result.await() })
        val newCoordinator = StudioMutationCoordinator(newProfile, scope)
        var useNew by mutableStateOf(false)
        fun card(name: String) = StashStudioCard("1", name)
        try {
            compose.setContent {
                MaterialTheme {
                    StudiosRoute(if (useNew) newProfile else oldProfile,
                        mutations = if (useNew) newCoordinator else oldCoordinator,
                        loadPage = { server, _, _, _, _, _ ->
                            StashStudioPage(listOf(card(if (server == oldProfile) "Old actor" else "New actor")), 1)
                        })
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Old actor")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Favorite studio").performClick()
            compose.runOnIdle { useNew = true }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("New actor")).fetchSemanticsNodes().isNotEmpty() }
            result.complete(StashStudioMutableFields("1", true, null))
            compose.waitUntil(10_000) { oldCoordinator.statuses.value["1"]?.busy == false }
            compose.onNodeWithContentDescription("Favorite studio").assertExists()
            compose.onNodeWithContentDescription("Unfavorite studio").assertDoesNotExist()
            compose.onNodeWithText("Old actor").assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }
}
