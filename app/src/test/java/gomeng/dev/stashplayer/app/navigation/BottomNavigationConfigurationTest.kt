package gomeng.dev.stashplayer.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavigationConfigurationTest {
    @Test fun missingOrInvalidConfigurationRestoresDefaults() {
        assertEquals(defaultNavigationFeatures, restoreNavigationFeatures(null))
        assertEquals(defaultNavigationFeatures, restoreNavigationFeatures("home,settings,unknown"))
    }

    @Test fun restoreKeepsFirstValidUniqueFeaturesAndFillsMissingSlots() {
        assertEquals(
            listOf(NavigationFeature.Studios, NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Gallery),
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

    @Test fun addingAtCapacityDoesNotReplaceAndRemovingThenAddingAppends() {
        val initial = defaultNavigationFeatures.toList()
        assertEquals(AddNavigationFeatureResult.AtCapacity, addNavigationFeature(initial, NavigationFeature.Performers))
        assertEquals(defaultNavigationFeatures, initial)
        assertEquals(
            AddNavigationFeatureResult.Added(listOf(NavigationFeature.Explore, NavigationFeature.Shorts, NavigationFeature.Queue, NavigationFeature.Performers)),
            addNavigationFeature(initial - NavigationFeature.Gallery, NavigationFeature.Performers),
        )
    }

    @Test fun movesAreBoundedAndPreserveAllItems() {
        assertEquals(defaultNavigationFeatures, moveNavigationFeature(defaultNavigationFeatures, -1, 2))
        assertEquals(defaultNavigationFeatures, moveNavigationFeature(defaultNavigationFeatures, 0, 4))
        assertEquals(
            listOf(NavigationFeature.Shorts, NavigationFeature.Gallery, NavigationFeature.Explore, NavigationFeature.Queue),
            moveNavigationFeature(defaultNavigationFeatures, 0, 2),
        )
    }
}
