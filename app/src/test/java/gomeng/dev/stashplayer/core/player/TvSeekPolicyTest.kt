package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TvSeekPolicyTest {
    @Test fun `repeated presses escalate the step and cap at one minute`() {
        assertEquals(10_000L, tvSeekStepMs(1))
        assertEquals(20_000L, tvSeekStepMs(2))
        assertEquals(30_000L, tvSeekStepMs(3))
        assertEquals(60_000L, tvSeekStepMs(4))
        assertEquals(60_000L, tvSeekStepMs(12))
    }

    @Test fun `unknown press counts fall back to the smallest step`() {
        assertEquals(10_000L, tvSeekStepMs(0))
        assertEquals(10_000L, tvSeekStepMs(-3))
    }

    @Test fun `scrub rate rises from 1x to 3x and then stays capped`() {
        assertEquals(20_000L, tvScrubStepMs(0))
        assertEquals(20_000L, tvScrubStepMs(1_499))
        assertEquals(40_000L, tvScrubStepMs(1_500))
        assertEquals(40_000L, tvScrubStepMs(3_999))
        assertEquals(60_000L, tvScrubStepMs(4_000))
        assertEquals(60_000L, tvScrubStepMs(60_000))
        assertEquals(3f, tvScrubRate(60_000))
    }
}
