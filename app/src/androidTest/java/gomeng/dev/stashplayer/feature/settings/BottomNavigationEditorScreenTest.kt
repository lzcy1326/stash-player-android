package gomeng.dev.stashplayer.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
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

    @Test fun draggingExposesAccessibilityState() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        val explore = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText("Explore"))
        compose.onNodeWithText("Explore").performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(width * 0.1f, 0f))
        }
        val state = compose.onNode(explore, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        org.junit.Assert.assertEquals("Reordering", state)
        compose.onNodeWithText("Explore").performTouchInput { up() }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(compose.onNode(explore, useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
    }

    @Test fun longPressOnFeatureIconReordersWithoutRemoving() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        compose.onNodeWithContentDescription("Remove Explore").performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(width * 1.4f, 0f))
            up()
        }
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(NavigationFeature.Shorts, NavigationFeature.Explore, NavigationFeature.Gallery, NavigationFeature.Queue), saved)
        }
    }

    @Test fun secondFingerCancelsInProgressReorder() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        compose.onNodeWithText("Explore").performTouchInput {
            down(0, center)
            advanceEventTime(220)
            moveBy(0, Offset(width * 1.2f, 0f))
            down(1, center)
            up(1)
            up(0)
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(defaultNavigationFeatures, saved) }
    }

    @Test fun draggingPastEndStaysInsideEditableSlots() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        val lastSlot = compose.onNodeWithText("Queue").getUnclippedBoundsInRoot().left
        compose.onNodeWithText("Explore").performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(width * 8f, 0f))
        }
        val dragged = compose.onNodeWithText("Explore").getUnclippedBoundsInRoot().left
        compose.onNodeWithText("Explore").performTouchInput { up() }
        org.junit.Assert.assertTrue("dragged item crossed the last editable slot: $dragged > $lastSlot", dragged <= lastSlot + 2.dp)
    }

    @Test fun draggingPastStartStaysInsideEditableSlots() {
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
        }
        val firstSlot = compose.onNodeWithText("Explore").getUnclippedBoundsInRoot().left
        compose.onNodeWithText("Queue").performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(-width * 8f, 0f))
        }
        val dragged = compose.onNodeWithText("Queue").getUnclippedBoundsInRoot().left
        compose.onNodeWithText("Queue").performTouchInput { up() }
        org.junit.Assert.assertTrue("dragged item crossed the first editable slot: $dragged < $firstSlot", dragged >= firstSlot - 2.dp)
    }

    @Test fun narrowScreenRemoveTargetsAreLargeAndSeparate() {
        compose.setContent {
            Box(Modifier.width(309.dp)) {
                BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, {})
            }
        }
        val first = compose.onNodeWithContentDescription("Remove Explore").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithContentDescription("Remove Shorts").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("first target width: ${first.right - first.left}", (first.right - first.left) >= 48.dp)
        org.junit.Assert.assertTrue("second target width: ${second.right - second.left}", (second.right - second.left) >= 48.dp)
        org.junit.Assert.assertTrue("targets must not overlap", first.right <= second.left)
        org.junit.Assert.assertTrue("target height: ${first.bottom - first.top}", (first.bottom - first.top) >= 48.dp)
    }

    @Test fun saveFailureKeepsDraftAndAllowsImmediateRetry() {
        var attempts = 0
        var navigatedBack = false
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorController(defaultNavigationFeatures, { navigatedBack = true }) { selection ->
                attempts++
                when (attempts) {
                    1 -> false
                    2 -> throw java.io.IOException("simulated write failure")
                    else -> { saved = selection; true }
                }
            }
        }
        compose.onNodeWithContentDescription("Remove Gallery").performClick()
        compose.onNodeWithContentDescription("Add Performers").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Could not save. Try again.").assertExists()
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertFalse(navigatedBack) }
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(navigatedBack)
            org.junit.Assert.assertEquals(listOf(NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Queue, NavigationFeature.Performers), saved)
        }
    }

    @Test fun accessibilityActionsReorderMiddleFeatures() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        val explore = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText("Explore"))
        val actions = compose.onNode(explore, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        org.junit.Assert.assertEquals(listOf("Move right"), actions.map { it.label })
        compose.runOnIdle { org.junit.Assert.assertTrue(actions.single().action()) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(NavigationFeature.Shorts, NavigationFeature.Explore, NavigationFeature.Gallery, NavigationFeature.Queue), saved)
        }
    }

    @Test fun accessibilityMoveLeftStopsAtFirstEditableSlot() {
        var saved: List<NavigationFeature>? = null
        compose.setContent {
            BottomNavigationEditorScreen(defaultNavigationFeatures, false, SnackbarHostState(), {}, { saved = it })
        }
        val queue = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText("Queue"))
        repeat(3) {
            val actions = compose.onNode(queue, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { org.junit.Assert.assertTrue(actions.first { it.label == "Move left" }.action()) }
        }
        val actions = compose.onNode(queue, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        org.junit.Assert.assertEquals(listOf("Move right"), actions.map { it.label })
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(NavigationFeature.Queue, NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Gallery), saved)
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
