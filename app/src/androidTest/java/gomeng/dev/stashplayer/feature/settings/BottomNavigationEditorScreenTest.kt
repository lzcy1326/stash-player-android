package gomeng.dev.stashplayer.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.app.navigation.defaultNavigationFeatures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationEditorScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullCapacityThenRemoveAndAdd() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithContentDescription("Add Performers").performClick()
        compose.onNodeWithText("You can add up to 4 features").assertExists()
        compose.onNodeWithContentDescription("Remove Gallery").performClick()
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add Performers").performClick()
        compose.onNodeWithText("Done").assertIsEnabled()
    }
}
