package gomeng.dev.stashplayer.app.navigation

import gomeng.dev.stashplayer.core.player.PlaybackOrientationMode
import gomeng.dev.stashplayer.core.player.PlayerPresentationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationRequestTest {
    @Test
    fun manualPlayerSwitchTakesPriorityOverSavedOrientationSetting() {
        assertEquals(AppOrientationRequest.Landscape, resolveAppOrientationRequest(
            isFoldLikeLayout = false, route = "player/{sceneId}",
            playerPresentationMode = PlayerPresentationMode.Fullscreen,
            playbackOrientationMode = PlaybackOrientationMode.Off,
            playerOrientationOverride = AppOrientationRequest.Landscape,
        ))
        assertEquals(AppOrientationRequest.Portrait, resolveAppOrientationRequest(
            isFoldLikeLayout = false, route = "player/{sceneId}",
            playerPresentationMode = PlayerPresentationMode.WatchPage,
            playbackOrientationMode = PlaybackOrientationMode.Landscape,
            playerOrientationOverride = AppOrientationRequest.Portrait,
        ))
    }

    @Test
    fun playerOverrideDoesNotAffectOtherScreens() {
        assertEquals(AppOrientationRequest.Portrait, resolveAppOrientationRequest(
            isFoldLikeLayout = false, route = "home",
            playerPresentationMode = PlayerPresentationMode.Fullscreen,
            playbackOrientationMode = PlaybackOrientationMode.Sensor,
            playerOrientationOverride = AppOrientationRequest.Landscape,
        ))
    }
}
