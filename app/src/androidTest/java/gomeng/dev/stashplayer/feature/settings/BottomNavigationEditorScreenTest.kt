package gomeng.dev.stashplayer.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.cancel
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.app.navigation.NavigationFeature
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

    @Test fun longPressDragReordersOnlyMiddleFeatures() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        compose.onNodeWithText("Explore").performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(width * 1.2f, 0f))
            up()
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(NavigationFeature.Shorts, saved?.first())
            org.junit.Assert.assertEquals(NavigationFeature.Explore, saved?.get(1))
            org.junit.Assert.assertEquals(4, saved?.size)
        }
    }

    @Test fun remainingFeaturesAnimateIntoVacatedSlot() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        val before = compose.onNodeWithText("Shorts").getUnclippedBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Remove Explore").performClick()
            compose.mainClock.advanceTimeBy(48)
            val during = compose.onNodeWithText("Shorts").getUnclippedBoundsInRoot().left
            compose.mainClock.advanceTimeBy(2000)
            val after = compose.onNodeWithText("Shorts").getUnclippedBoundsInRoot().left
            org.junit.Assert.assertTrue("item should move left", after < before)
            org.junit.Assert.assertTrue("item should animate between slots: $before, $during, $after", during > after && during < before)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun removalAnimationDoesNotDelayDraftChanges() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Remove Gallery").performClick()
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("navigation-removal-animation").assertExists()
            compose.onNodeWithContentDescription("Add Performers").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("Done").assertIsEnabled()
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithTag("navigation-removal-animation").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun addedFeatureSlidesIntoItsSlot() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        compose.onNodeWithContentDescription("Remove Explore").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Add Performers").performClick()
            compose.mainClock.advanceTimeBy(48)
            val during = compose.onNodeWithText("Performers").getUnclippedBoundsInRoot().top
            compose.mainClock.advanceTimeBy(1000)
            val after = compose.onNodeWithText("Performers").getUnclippedBoundsInRoot().top
            org.junit.Assert.assertTrue("added item should slide up: $during, $after", during > after)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun releasedItemAnimatesBackToItsSlot() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        val resting = compose.onNodeWithText("Explore").getUnclippedBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Explore").performTouchInput {
                down(center)
                advanceEventTime(220)
                moveBy(Offset(width * 0.3f, 0f))
                up()
            }
            compose.mainClock.advanceTimeBy(48)
            val during = compose.onNodeWithText("Explore").getUnclippedBoundsInRoot().left
            compose.mainClock.advanceTimeBy(2000)
            val after = compose.onNodeWithText("Explore").getUnclippedBoundsInRoot().left
            org.junit.Assert.assertTrue("item should return from the released position: $resting, $during, $after", during > after)
            org.junit.Assert.assertEquals(resting.value, after.value, 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun cancellingDragRestoresOriginalOrder() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        compose.onNodeWithText("Explore").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(width * 1.2f, 0f))
            cancel()
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(defaultNavigationFeatures, saved) }
    }
}
