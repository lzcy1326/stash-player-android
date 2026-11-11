package gomeng.dev.stashplayer.feature.studios

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gomeng.dev.stashplayer.MainActivity
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.app.navigation.NavigationFeature
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.i18n.withStashAppLanguage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioNavigationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun configuredNavigationOpensRealStudioDetailsAndReturnsToResults(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realStudioValidation") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = StashSettingsRepository(context)
        val original = settings.bottomNavigationFeatures.first()
        val local = context.withStashAppLanguage(settings.appLanguage.first())
        val profile = requireNotNull(settings.savedServerProfile.first())
        val studios = StashGraphQlClient(profile).findStudioCardsPage()
        val studio = studios.studios.firstOrNull { it.sceneCount > 12 } ?: studios.studios.first()
        val firstScene = StashGraphQlClient(profile).findSceneCardsPage(studioId = studio.id, sort = "date").scenes.firstOrNull()
        try {
            settings.setBottomNavigationFeatures(listOf(NavigationFeature.Studios, NavigationFeature.Performers, NavigationFeature.Explore, NavigationFeature.Queue))
            val studioTab = hasText(local.getString(R.string.navigation_studios_label)) and hasClickAction()
            compose.waitUntil(30_000) { compose.onAllNodes(studioTab).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(studioTab).performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText(studios.studios.first().name, substring = false)).fetchSemanticsNodes().isNotEmpty() }
            saveScreenshot("studios-list")
            compose.onNode(hasSetTextAction()).performTextInput(studio.name)
            val studioCardName = hasText(studio.name, substring = false) and !hasSetTextAction()
            compose.waitUntil(30_000) { compose.onAllNodes(studioCardName).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(studioCardName).performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText(local.getString(R.string.performer_clear_rating))).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("studio-detail-list").assertExists()
            if (!studio.imagePath.isNullOrBlank()) {
                compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("studio-logo-loaded-${studio.id}"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            }
            saveScreenshot("studio-profile")
            if (firstScene != null) {
                val sceneTitle = hasText(firstScene.title, substring = false)
                val detailList = compose.onNodeWithTag("studio-detail-list")
                detailList.performScrollToNode(sceneTitle)
                compose.onNode(sceneTitle).performScrollTo().assertIsDisplayed()
            }
            saveScreenshot("studio-detail")
            compose.onNodeWithText(local.getString(R.string.performer_back)).performClick()
            compose.onNodeWithTag("studios-grid").assertExists()
            compose.onNode(hasSetTextAction()).assertTextContains(studio.name)
        } catch (error: Throwable) {
            saveScreenshot("studio-navigation-failure")
            throw error
        } finally {
            settings.setBottomNavigationFeatures(original)
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "studios-qa/$name.png").apply { parentFile?.mkdirs() }
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
