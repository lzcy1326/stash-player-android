package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerFilterWithTagsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filterWithSelectedTagCanBeOpened() {
        updateStashStringContext(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            MaterialTheme {
                PerformerFilterDialog(PerformerFilter(tags = listOf(StashSelectedTag("1", "Fixture tag"))), null, onApply = {}, onDismiss = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Tags (1)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Apply").assertExists()
    }
}
