package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerFilterLabelsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun genderAndMissingOptionsShowLabelsButKeepServerValues() {
        val result = AtomicReference<PerformerFilter>()
        compose.setContent {
            MaterialTheme {
                PerformerFilterDialog(PerformerFilter(), null, onApply = { result.set(it) }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Gender: Any").performClick()
        compose.onNodeWithText("Female").performClick()
        compose.onNodeWithText("Gender: Female").assertExists()
        compose.onNodeWithText("Missing information: Any").performScrollTo().performClick()
        compose.onNodeWithText("Photo").performClick()
        compose.onNodeWithText("Missing information: Photo").assertExists()
        compose.onNodeWithText("Apply").performClick()
        assertEquals("FEMALE", result.get().gender)
        assertEquals("image", result.get().missing)
    }
}
