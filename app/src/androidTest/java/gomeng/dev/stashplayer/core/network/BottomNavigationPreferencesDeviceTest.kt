package gomeng.dev.stashplayer.core.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.app.navigation.NavigationFeature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationPreferencesDeviceTest {
    @Test fun savesOrderedSelectionAndRejectsIncompleteChanges() = runBlocking {
        val repository = StashSettingsRepository(ApplicationProvider.getApplicationContext())
        val original = repository.bottomNavigationFeatures.first()
        try {
            val custom = listOf(NavigationFeature.Studios, NavigationFeature.Queue, NavigationFeature.Performers, NavigationFeature.Explore)
            assertTrue(repository.setBottomNavigationFeatures(custom))
            assertEquals(custom, StashSettingsRepository(ApplicationProvider.getApplicationContext()).bottomNavigationFeatures.first())
            assertFalse(repository.setBottomNavigationFeatures(custom.dropLast(1)))
            assertEquals(custom, repository.bottomNavigationFeatures.first())
        } finally {
            repository.setBottomNavigationFeatures(original)
        }
    }
}
