package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.ui.components.StashRatingMediaFormatFilterSheet
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import gomeng.dev.stashplayer.feature.player.PlayerRatingControls
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedRatingAccessibilityDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playerAnnouncesCurrentRatingAndOffersAccurateChoices() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val chosen = AtomicInteger(-1)
        compose.setContent {
            MaterialTheme { PlayerRatingControls(2, null, false, onSelectRatingStep = { chosen.set(it) }) }
        }
        compose.onNodeWithContentDescription("Player rating 1-star rating", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("4-star rating") and hasClickAction(), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(8, chosen.get())
    }

    @Test fun filterOffersAccurateRatingChoicesForBothBounds() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            MaterialTheme {
                StashRatingMediaFormatFilterSheet(StashVideoFilterState(), onApply = {},
                    onClearRatingRange = {}, onClearMediaFormat = {}, onDismiss = {})
            }
        }
        val choices = compose.onAllNodes(hasContentDescription("4-star rating") and hasClickAction(), useUnmergedTree = true)
        assertEquals(2, choices.fetchSemanticsNodes().size)
    }
}
