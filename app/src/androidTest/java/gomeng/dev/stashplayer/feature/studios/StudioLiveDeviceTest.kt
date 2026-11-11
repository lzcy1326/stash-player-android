package gomeng.dev.stashplayer.feature.studios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.core.network.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in; uses the emulator's configured server and never mutates it. */
@RunWith(AndroidJUnit4::class)
class StudioLiveDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun profile(): StashServerProfile = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realStudioValidation") == "true")
        requireNotNull(StashSettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext).savedServerProfile.first())
    }

    @Test fun realListAndDetailRenderUsingExistingServerConfiguration() {
        val profile = profile()
        val first = runBlocking { StashGraphQlClient(profile).findStudioCardsPage().studios.first() }
        compose.setContent { MaterialTheme { StudiosRoute(profile) } }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(first.name, substring = false)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(first.name).assertIsDisplayed()
    }

    @Test fun realStudioHierarchyScopeSortingAndRandomPagingAgreeWithServer() = runBlocking {
        val client = StashGraphQlClient(profile())
        val ascending = client.findStudioCardsPage(perPage = 1000)
        assertTrue(ascending.studios.isNotEmpty())
        assertEquals(ascending.count, ascending.studios.size)
        val descending = client.findStudioCardsPage(perPage = 1000, direction = "DESC")
        assertEquals(ascending.studios.map { it.id }.reversed(), descending.studios.map { it.id })
        val parent = ascending.studios.first { it.children.isNotEmpty() }
        val direct = client.findStudioSceneSummary(parent.id)
        val inclusive = client.findStudioSceneSummary(parent.id, true)
        assertTrue(inclusive.count >= direct.count)
        val scenes = client.findSceneCardsPage(perPage = 1000, studioId = parent.id, includeStudioChildren = true)
        assertEquals(inclusive.count, scenes.totalCount)
        val allowedNames = (listOf(parent.name) + parent.children.map { it.name }).toSet()
        assertTrue(scenes.scenes.all { it.studio in allowedNames })
        val childList = client.findStudioCardsPage(studioFilter = StudioFilter(parent = StashStudioReference(parent.id, parent.name)).toGraphQl())
        assertEquals(parent.children.map { it.id }.toSet(), childList.studios.map { it.id }.toSet())
        val favoriteList = client.findStudioCardsPage(studioFilter = StudioFilter(favoriteOnly = true).toGraphQl())
        assertTrue(favoriteList.studios.all { it.favorite })
        val page1 = client.findStudioCardsPage(perPage = 3, sort = "random_12345")
        val page2 = client.findStudioCardsPage(perPage = 3, page = 2, sort = "random_12345")
        assertEquals(page1.studios.map { it.id }, client.findStudioCardsPage(perPage = 3, sort = "random_12345").studios.map { it.id })
        assertTrue(page1.studios.map { it.id }.toSet().intersect(page2.studios.map { it.id }.toSet()).isEmpty())
    }
}
