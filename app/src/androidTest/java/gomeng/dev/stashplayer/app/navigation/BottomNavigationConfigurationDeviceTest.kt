package gomeng.dev.stashplayer.app.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationConfigurationDeviceTest {
    @Test fun configurationRecoversAndCapacityIsEnforcedOnDevice() {
        val restored = restoreNavigationFeatures("studios,studios,unknown")
        assertEquals(4, restored.size)
        assertEquals(NavigationFeature.Studios, restored.first())
        assertEquals(AddNavigationFeatureResult.AtCapacity, addNavigationFeature(restored, NavigationFeature.Performers))
        assertNull(serializeNavigationFeatures(restored.dropLast(1)))
        assertEquals(restored, restoreNavigationFeatures(serializeNavigationFeatures(restored)))
    }
}
