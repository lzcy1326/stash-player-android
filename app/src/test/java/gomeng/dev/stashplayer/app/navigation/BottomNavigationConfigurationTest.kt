package gomeng.dev.stashplayer.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavigationConfigurationTest {
    @Test fun missingOrInvalidConfigurationRestoresDefaults() {
        assertEquals(listOf(NavigationFeature.Explore, NavigationFeature.Performers, NavigationFeature.Studios, NavigationFeature.Queue), defaultNavigationFeatures)
        assertEquals(defaultNavigationFeatures, restoreNavigationFeatures(null))
        assertEquals(defaultNavigationFeatures, restoreNavigationFeatures("home,settings,unknown"))
    }

    @Test fun restoreKeepsFirstValidUniqueFeaturesAndFillsMissingSlots() {
        assertEquals(
            listOf(NavigationFeature.Studios, NavigationFeature.Explore, NavigationFeature.Performers, NavigationFeature.Queue),
            restoreNavigationFeatures("studios,studios,unknown,explore"),
        )
        assertEquals(
            listOf(NavigationFeature.Studios, NavigationFeature.Performers, NavigationFeature.Queue, NavigationFeature.Gallery),
            restoreNavigationFeatures("studios,performers,queue,gallery,explore"),
        )
    }

    @Test fun serializationRequiresFourUniqueFeaturesAndPreservesOrder() {
        val chosen = listOf(NavigationFeature.Studios, NavigationFeature.Queue, NavigationFeature.Explore, NavigationFeature.Performers)
        assertEquals(chosen, restoreNavigationFeatures(serializeNavigationFeatures(chosen)))
        assertNull(serializeNavigationFeatures(chosen.take(3)))
        assertNull(serializeNavigationFeatures(List(4) { NavigationFeature.Queue }))
    }

    @Test fun savedNavigationIsPreservedWhenDefaultsChange() {
        assertEquals(
            listOf(NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Gallery, NavigationFeature.Queue),
            restoreNavigationFeatures("explore,shorts,gallery,queue"),
        )
    }

    @Test fun addingAtCapacityDoesNotReplaceAndRemovingThenAddingAppends() {
        val initial = defaultNavigationFeatures.toList()
        assertEquals(AddNavigationFeatureResult.AtCapacity, addNavigationFeature(initial, NavigationFeature.Shorts))
        assertEquals(defaultNavigationFeatures, initial)
        assertEquals(
            AddNavigationFeatureResult.Added(listOf(NavigationFeature.Explore, NavigationFeature.Performers, NavigationFeature.Queue, NavigationFeature.Shorts)),
            addNavigationFeature(initial - NavigationFeature.Studios, NavigationFeature.Shorts),
        )
    }

    @Test fun movesAreBoundedAndPreserveAllItems() {
        assertEquals(defaultNavigationFeatures, moveNavigationFeature(defaultNavigationFeatures, -1, 2))
        assertEquals(defaultNavigationFeatures, moveNavigationFeature(defaultNavigationFeatures, 0, 4))
        assertEquals(
            listOf(NavigationFeature.Performers, NavigationFeature.Studios, NavigationFeature.Explore, NavigationFeature.Queue),
            moveNavigationFeature(defaultNavigationFeatures, 0, 2),
        )
    }
}
