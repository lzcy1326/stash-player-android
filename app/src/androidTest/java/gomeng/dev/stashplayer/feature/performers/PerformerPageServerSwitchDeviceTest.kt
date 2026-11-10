package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerPageServerSwitchDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingServerFromSecondPageStartsAtFirstPage() {
        val oldProfile = StashServerProfile(baseUrl = "http://old.example")
        val newProfile = StashServerProfile(baseUrl = "http://new.example")
        val newPages = CopyOnWriteArrayList<Int>()
        val oldPage = AtomicInteger()
        var useNew by mutableStateOf(false)
        fun card(id: Int, prefix: String) = StashPerformerCard(id.toString(), "$prefix $id", emptyList(), null, null, null, null, null, false, null, 0)
        compose.setContent {
            MaterialTheme {
                PerformersRoute(if (useNew) newProfile else oldProfile, loadPage = { server, page, _, _, _, _ ->
                    if (server == newProfile) {
                        newPages.add(page)
                        delay(100)
                        StashPerformerPage(listOf(card(1, "New")), 1)
                    } else {
                        oldPage.set(page)
                        val ids = if (page == 1) 1..40 else 41..41
                        StashPerformerPage(ids.map { card(it, "Old") }, 41)
                    }
                })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Old 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("performers-grid").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        compose.waitUntil(10_000) { oldPage.get() == 2 }
        compose.onNodeWithTag("performers-grid").performScrollToNode(hasText("Old 41"))
        compose.runOnIdle { useNew = true }
        compose.waitUntil(10_000) { newPages.contains(1) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New 1")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(1), newPages.toList())
    }
}
