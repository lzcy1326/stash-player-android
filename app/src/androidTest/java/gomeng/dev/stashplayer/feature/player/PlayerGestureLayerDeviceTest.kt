package gomeng.dev.stashplayer.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.player.PlayerPresentationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerGestureLayerDeviceTest {
    @get:Rule val compose = createComposeRule()

    private var brightness = 0.5f
    private var volume = 0.5f
    private var holdStarts = 0
    private var holdEnds = 0
    private var presentationUpdates = 0

    private fun showPlayer(mode: PlayerPresentationMode, locked: Boolean = false) {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                PlayerGestureLayer(
                    locked = locked, currentPositionMs = 20_000, durationMs = 120_000,
                    onToggleOverlay = {}, onPlayPause = {}, onSeekBy = {}, onSeekTo = {},
                    brightnessFraction = { brightness }, volumeFraction = { volume },
                    onBrightnessFraction = { brightness = it; "brightness" },
                    onVolumeFraction = { volume = it; "volume" },
                    onHudText = {}, onSeekPreview = {},
                    onFastPlaybackHoldStart = { holdStarts++ }, onFastPlaybackHoldEnd = { holdEnds++ },
                    previewFrameFor = { null }, modifier = Modifier.testTag("player-surface"),
                    presentationMode = mode,
                    onPresentationDragUpdate = { presentationUpdates++ },
                )
            }
        }
    }

    @Test fun verticalGesturesUseTheWholeHalfOfTheScreenInWatchPage() {
        showPlayer(PlayerPresentationMode.WatchPage)
        compose.onNodeWithTag("player-surface").performTouchInput {
            down(Offset(width * 0.42f, height * 0.65f)); moveBy(Offset(0f, -height * 0.25f)); up()
        }
        compose.runOnIdle { assertTrue("left side should increase brightness", brightness > 0.5f); assertEquals(0.5f, volume, 0f) }
        compose.onNodeWithTag("player-surface").performTouchInput {
            down(Offset(width * 0.58f, height * 0.65f)); moveBy(Offset(0f, -height * 0.25f)); up()
        }
        compose.runOnIdle { assertTrue("right side should increase volume", volume > 0.5f); assertEquals(0, presentationUpdates) }
    }

    @Test fun longPressStartsAndEndsExactlyOnce() {
        showPlayer(PlayerPresentationMode.Fullscreen)
        compose.onNodeWithTag("player-surface").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(1f, 0f)); up()
        }
        compose.runOnIdle { assertEquals(1, holdStarts); assertEquals(1, holdEnds) }
    }

    @Test fun movingBeforeTimeoutDoesNotStartFastPlayback() {
        showPlayer(PlayerPresentationMode.Fullscreen)
        compose.onNodeWithTag("player-surface").performTouchInput {
            down(Offset(width * 0.25f, height * 0.6f)); moveBy(Offset(0f, -height * 0.25f)); advanceEventTime(800); up()
        }
        compose.runOnIdle { assertEquals(0, holdStarts); assertEquals(0, holdEnds); assertTrue(brightness > 0.5f) }
    }

    @Test fun cancelledLongPressRestoresSpeed() {
        showPlayer(PlayerPresentationMode.Fullscreen)
        compose.onNodeWithTag("player-surface").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(1f, 0f)); cancel()
        }
        compose.runOnIdle { assertEquals(1, holdStarts); assertEquals(1, holdEnds) }
    }
}
