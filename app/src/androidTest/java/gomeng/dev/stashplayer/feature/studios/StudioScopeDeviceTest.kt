package gomeng.dev.stashplayer.feature.studios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.*
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioScopeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun includingChildrenUpdatesSummaryScenesAndPlaybackScope() {
        val request = AtomicReference<Boolean>()
        val playback = AtomicReference<PlayerPlaybackQueueContinuation?>()
        val summary = AtomicReference<Boolean>()
        compose.setContent {
            MaterialTheme {
                StudioDetailRoute("parent", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    onOpenScene = { _, _, _, next -> playback.set(next) },
                    loadDetail = { _, _ -> StashStudioCard("parent", "Parent", children = listOf(StashStudioReference("child", "Child"))) },
                    loadSummary = { _, _, children -> summary.set(children); StashPerformerSceneSummary(if (children) 13 else 0, 0.0, 0.0) },
                    loadScenes = { _, id, _, _, _, _, _, children ->
                        assertEquals("parent", id)
                        request.set(children)
                        StashSceneCardPage(if (children) (1..12).map { SceneCardModel("$it", "Child scene $it", "", "", 0f) } else emptyList(), if (children) 13 else 0)
                    },
                )
            }
        }
        compose.waitUntil(10_000) { request.get() == false && summary.get() == false }
        compose.onNodeWithTag("studio-detail-list").performScrollToNode(hasText("Include subsidiary studios"))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).performClick()
        compose.waitUntil(10_000) { request.get() == true && summary.get() == true }
        compose.onNodeWithTag("studio-detail-list").performScrollToNode(hasText("Child scene 1", substring = false))
        compose.onNodeWithText("Child scene 1").performClick()
        val continuation = playback.get() as PlayerPlaybackQueueContinuation.Studio
        assertEquals("parent", continuation.studioId)
        assertTrue(continuation.includeChildren)
        assertTrue(continuation.hasMore)
        assertEquals(2, continuation.nextPage)
    }

    @Test fun hierarchyLinksOpenTheCorrectStudio() {
        val opened = AtomicReference<String>()
        compose.setContent {
            MaterialTheme {
                StudioDetailRoute("current", false, StashServerProfile(baseUrl = "http://localhost"), {},
                    onOpenStudio = { opened.set(it) },
                    loadDetail = { _, _ -> StashStudioCard("current", "Current", parent = StashStudioReference("parent", "Parent"), children = listOf(StashStudioReference("child", "Child"))) },
                    loadSummary = { _, _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                    loadScenes = { _, _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Parent studio: Parent")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parent studio: Parent").performClick()
        assertEquals("parent", opened.get())
        compose.onNodeWithText("Child").performClick()
        assertEquals("child", opened.get())
    }
}
