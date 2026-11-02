package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPresentationRouteStateTest {
    @Test
    fun deviceLandscapePromotesWatchPageToFullscreen() {
        val state = PlayerPresentationRouteState
            .initial(PlayerPresentationMode.WatchPage)
            .forDeviceLandscape(isLandscape = true)

        assertEquals(PlayerPresentationMode.Fullscreen, state.targetMode)
        assertTrue(state.landscapeAutoFullscreen)
    }

    @Test
    fun returningToPortraitRestoresWatchPageForAutomaticFullscreen() {
        val state = PlayerPresentationRouteState
            .initial(PlayerPresentationMode.WatchPage)
            .forDeviceLandscape(isLandscape = true)
            .forDeviceLandscape(isLandscape = false)

        assertEquals(PlayerPresentationMode.WatchPage, state.targetMode)
        assertFalse(state.landscapeAutoFullscreen)
    }
}
