package gomeng.dev.stashplayer.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    @Test
    fun configuredAndFirstRunProfilesChooseCorrectStartScreens() {
        assertEquals("home", resolveStashStartDestination(hasSavedProfile = true))
        assertEquals(SetupRoute, resolveStashStartDestination(hasSavedProfile = false))
    }
}
