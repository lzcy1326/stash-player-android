package gomeng.dev.stashplayer.feature.performers

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerSortThemeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directionTriangleTracksThemeAccent() {
        val first = Color(0xFFB32060)
        val second = Color(0xFF08796A)
        var accent by mutableStateOf(first)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent)) {
                PerformerBrowseControls("", {}, "Search", "Name", {}, true, {}, "Filters", {}, {})
            }
        }
        fun assertTriangleColor(expected: Color) {
            val image = compose.onNodeWithContentDescription("Ascending ↑").captureToImage().toPixelMap()
            val count = (0 until image.height).sumOf { y ->
                (0 until image.width).count { x ->
                    val pixel = image[x, y]
                    abs(pixel.red - expected.red) < 0.03f && abs(pixel.green - expected.green) < 0.03f && abs(pixel.blue - expected.blue) < 0.03f
                }
            }
            assertTrue("Triangle should use the current theme color", count > 4)
        }
        assertTriangleColor(first)
        compose.runOnIdle { accent = second }
        assertTriangleColor(second)
    }
}
