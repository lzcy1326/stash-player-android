package gomeng.dev.stashplayer.feature.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.MainActivity
import gomeng.dev.stashplayer.app.navigation.NavigationFeature
import gomeng.dev.stashplayer.app.navigation.defaultNavigationFeatures
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationFlowDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsEditorSavesSelectionToNavigation() {
        val repository = StashSettingsRepository(compose.activity.applicationContext)
        val original = runBlocking { repository.bottomNavigationFeatures.first() }
        try {
            runBlocking { repository.setBottomNavigationFeatures(defaultNavigationFeatures) }
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("Settings")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Interface").performClick()
            compose.onNodeWithText("Bottom navigation").performClick()
            compose.onNodeWithContentDescription("Remove Gallery").performClick()
            compose.onNodeWithContentDescription("Add Performers").performClick()
            compose.onNodeWithText("Done").performClick()
            compose.waitUntil(20_000) { runBlocking { repository.bottomNavigationFeatures.first().contains(NavigationFeature.Performers) } }
            assertEquals(
                listOf(NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Queue, NavigationFeature.Performers),
                runBlocking { repository.bottomNavigationFeatures.first() },
            )
            compose.onNodeWithText("Performers").assertExists()
        } finally {
            runBlocking { repository.setBottomNavigationFeatures(original) }
        }
    }
}
