package gomeng.dev.stashplayer.feature.studios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioMutableFields
import gomeng.dev.stashplayer.core.network.StashPerformerSceneSummary
import gomeng.dev.stashplayer.core.network.StashSceneCardPage
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioRatingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ratingSelectionAndClearSendExpectedValues() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val profile = StashServerProfile(baseUrl = "http://localhost")
        val card = StashStudioCard("1", "Studio", rating100 = 20)
        val detail = card
        val scope = MainScope()
        val sent = AtomicReference<Int?>()
        val count = AtomicInteger()
        val coordinator = StudioMutationCoordinator(profile, scope, updateRating = { _, id, rating ->
            sent.set(rating)
            count.incrementAndGet()
            StashStudioMutableFields(id, false, rating)
        })
        try {
            compose.setContent {
                MaterialTheme {
                    StudioDetailRoute("1", false, profile, onBack = {}, mutations = coordinator,
                        loadDetail = { _, _ -> detail },
                        loadSummary = { _, _, _ -> StashPerformerSceneSummary(0, 0.0, 0.0) },
                        loadScenes = { _, _, _, _, _, _, _, _ -> StashSceneCardPage(emptyList(), 0) })
                }
            }
            val fourthStar = hasContentDescription("4-star rating", substring = true) and hasClickAction()
            compose.waitUntil(10_000) { compose.onAllNodes(fourthStar, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(fourthStar, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { count.get() == 1 }
            assertEquals(80, sent.get())
            compose.onNode(fourthStar, useUnmergedTree = true)
                .performTouchInput { click(Offset(width * 0.25f, center.y)) }
            compose.waitUntil(10_000) { count.get() == 2 }
            assertEquals(70, sent.get())
            compose.onNode(fourthStar, useUnmergedTree = true)
                .performTouchInput { click(Offset(width * 0.75f, center.y)) }
            compose.waitUntil(10_000) { count.get() == 3 }
            assertEquals(80, sent.get())
            compose.onNodeWithText("Clear rating").performClick()
            compose.waitUntil(10_000) { count.get() == 4 }
            assertNull(sent.get())
        } finally {
            scope.cancel()
        }
    }
}
