package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TvScrubPolicyTest {
    @Test fun `scrub rate grows with the hold time and clamps to the timeline`() {
        // The first tick uses the slowest step, then each stage roughly doubles the distance.
        assertEquals(70_000L, nextTvScrubTarget(50_000, 1, 0, 100_000))
        assertEquals(10_000L, nextTvScrubTarget(50_000, -1, 2_000, 100_000))
        assertEquals(100_000L, nextTvScrubTarget(50_000, 1, 5_000, 100_000))
        assertEquals(0L, nextTvScrubTarget(5_000, -1, 0, 100_000))
        assertEquals(100_000L, nextTvScrubTarget(95_000, 1, 0, 100_000))
    }

    @Test fun `long holds cross a long film quickly`() {
        assertEquals(110_000L, nextTvScrubTarget(50_000, 1, 12_000, 7_200_000))
        assertEquals(110_000L, nextTvScrubTarget(50_000, 1, 7_000, 10_000_000))
    }

    @Test fun `invalid direction and unknown duration do not overflow`() {
        assertEquals(25_000L, nextTvScrubTarget(25_000, 0, 0, 100_000))
        assertEquals(Long.MAX_VALUE, nextTvScrubTarget(Long.MAX_VALUE - 2, 1, 0, 0))
    }
}
