package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerSummaryLabelsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryUsesReadableGenderAndMissingLabels() {
        compose.setContent {
            MaterialTheme { PerformerActiveFilters(PerformerFilter(gender = "FEMALE", missing = "image")) }
        }
        compose.onNodeWithText("Gender: Female").assertExists()
        compose.onNodeWithText("Missing information: Photo").assertExists()
        compose.onNodeWithText("Missing information: image").assertDoesNotExist()
    }
}
