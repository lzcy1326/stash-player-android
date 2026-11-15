package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TvScrubPolicyTest {
    @Test fun `moves both directions with acceleration and clamps to the timeline`() {
        assertEquals(60_000L, nextTvScrubTarget(50_000, 1, 0, 100_000))
        assertEquals(30_000L, nextTvScrubTarget(50_000, -1, 2_000, 100_000))
        assertEquals(80_000L, nextTvScrubTarget(50_000, 1, 5_000, 100_000))
        assertEquals(0L, nextTvScrubTarget(5_000, -1, 0, 100_000))
        assertEquals(100_000L, nextTvScrubTarget(95_000, 1, 0, 100_000))
    }

    @Test fun `invalid direction and unknown duration do not overflow`() {
        assertEquals(25_000L, nextTvScrubTarget(25_000, 0, 0, 100_000))
        assertEquals(Long.MAX_VALUE, nextTvScrubTarget(Long.MAX_VALUE - 2, 1, 0, 0))
    }
}
