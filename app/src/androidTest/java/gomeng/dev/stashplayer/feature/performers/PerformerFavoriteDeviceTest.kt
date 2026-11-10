package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerFavoriteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun favoriteButtonUpdatesCardWithoutOpeningDetails() {
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, null, 0)
        val opened = AtomicInteger()
        val updates = AtomicInteger()
        val scope = MainScope()
        val coordinator = PerformerMutationCoordinator(profile, scope, updateFavorite = { _, id, value ->
            updates.incrementAndGet()
            StashPerformerMutableFields(id, value, null)
        })
        try {
        compose.setContent {
            MaterialTheme {
                PerformersRoute(profile, mutations = coordinator, onOpenPerformer = { _, _ -> opened.incrementAndGet() },
                    loadPage = { _, _, _, _, _, _ -> StashPerformerPage(listOf(card), 1) })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Add performer favorite")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Add performer favorite").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Remove performer favorite")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, updates.get())
        assertEquals(0, opened.get())
        } finally {
            scope.cancel()
        }
    }
}
