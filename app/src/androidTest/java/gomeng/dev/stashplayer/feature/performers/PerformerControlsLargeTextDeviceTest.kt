package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformerControlsLargeTextDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsRemainSeparateAndClickableWithLargeText() {
        val sortClicks = AtomicInteger()
        val filterClicks = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        PerformerBrowseControls(query = "", onQueryChange = {}, searchLabel = "Search scenes",
                            sortLabel = "Recently released", onOpenSort = { sortClicks.incrementAndGet() }, ascending = false, onToggleDirection = {},
                            filterLabel = "Filters (3)", onOpenFilter = { filterClicks.incrementAndGet() }, sortMenu = {})
                    }
                }
            }
        }
        val sort = compose.onNodeWithText("Recently released", substring = true)
        val filter = compose.onNodeWithText("Filters (3)")
        val sortBounds = sort.fetchSemanticsNode().boundsInRoot
        val filterBounds = filter.fetchSemanticsNode().boundsInRoot
        assertTrue(sortBounds.right <= filterBounds.left)
        sort.performClick()
        filter.performClick()
        assertEquals(1, sortClicks.get())
        assertEquals(1, filterClicks.get())
    }
}
