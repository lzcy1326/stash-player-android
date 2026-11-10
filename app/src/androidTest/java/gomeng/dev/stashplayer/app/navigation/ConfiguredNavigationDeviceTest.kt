package gomeng.dev.stashplayer.app.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.MainActivity
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfiguredNavigationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun configuredFeaturesAppearInNavigation() {
        val repository = StashSettingsRepository(compose.activity.applicationContext)
        val original = runBlocking { repository.bottomNavigationFeatures.first() }
        try {
            runBlocking {
                repository.setBottomNavigationFeatures(listOf(NavigationFeature.Performers, NavigationFeature.Studios, NavigationFeature.Explore, NavigationFeature.Queue))
            }
            compose.waitUntil(15_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Performers")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Performers").assertExists()
            compose.onNodeWithText("Studios").assertExists()
            compose.onNodeWithText("Performers").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Performer filters")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Performer filters").assertExists()
            compose.onNodeWithText("Studios").performClick()
            compose.onNodeWithText("Studios feature in development").assertExists()
            compose.onNodeWithText("Browse and filter studios here in a future update.").assertExists()
        } finally {
            runBlocking { repository.setBottomNavigationFeatures(original) }
        }
    }
}
