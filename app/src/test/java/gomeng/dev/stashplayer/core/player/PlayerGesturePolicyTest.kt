package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturePolicyTest {
    @Test fun leftAndRightHalvesChooseBrightnessAndVolumeInBothPresentationModes() {
        for (mode in PlayerPresentationMode.entries) {
            assertEquals(PlayerSurfaceDragDecision.Playback(PlayerGestureMode.Brightness), resolvePlayerSurfaceDragDecision(mode, 49f, 100f, 0f, -30f))
            assertEquals(PlayerSurfaceDragDecision.Playback(PlayerGestureMode.Volume), resolvePlayerSurfaceDragDecision(mode, 50f, 100f, 0f, -30f))
            assertEquals(PlayerSurfaceDragDecision.Playback(PlayerGestureMode.Seek), resolvePlayerSurfaceDragDecision(mode, 49f, 100f, 30f, 0f))
        }
    }

    @Test fun reversedSidePreferenceRemainsSupported() {
        assertEquals(PlayerGestureMode.Volume, classifyPlayerDrag(25f, 100f, 0f, -30f, sideGestureLayout = PlayerSideGestureLayout.Reversed))
        assertEquals(PlayerGestureMode.Brightness, classifyPlayerDrag(75f, 100f, 0f, -30f, sideGestureLayout = PlayerSideGestureLayout.Reversed))
    }

    @Test fun watchPageContentPullStillEntersFullscreen() {
        assertEquals(PlayerPresentationGestureMode.EnterFullscreen, classifyPlayerPresentationDrag(PlayerPresentationMode.WatchPage, PlayerPresentationGestureStartArea.WatchPageContent, 30f, 100f, 0f, 40f))
    }

    @Test fun holdUsesConfiguredAbsoluteSpeedAndRestoresPreviousSpeed() {
        val started = PlayerFastPlaybackHoldState.Idle.start(1.5f, false, FastPlaybackHoldSpeedPreference.TwoPointZero)
        assertEquals(2f, started.playbackSpeed!!, 0f)
        assertTrue(started.state.active)
        val released = started.state.release()
        assertEquals(1.5f, released.playbackSpeed!!, 0f)
        assertFalse(released.state.active)
    }

    @Test fun repeatedStartDoesNotOverwriteRestoreSpeed() {
        val first = PlayerFastPlaybackHoldState.Idle.start(1.25f, false, FastPlaybackHoldSpeedPreference.TwoPointZero)
        val repeated = first.state.start(2f, false, FastPlaybackHoldSpeedPreference.TwoPointZero)
        assertEquals(1.25f, repeated.state.release().playbackSpeed!!, 0f)
    }
}
