package gomeng.dev.stashplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerResumeSettingTest {
    @Test fun `disabled resume starts from zero for saved queues and ordinary videos`() {
        assertEquals(0L, resolvePlayerResumeStartPositionMs(45_000L, 120.0, resumeEnabled = false))
        assertEquals(0L, resolvePlayerResumeStartPositionMs(45_000L, 120.0, savedQueuePositionMs = 30_000L, resumeEnabled = false))
        assertFalse(shouldPromptForPlayerResumePosition(0L))
    }

    @Test fun `enabled resume uses saved queue position with duration boundaries`() {
        assertEquals(30_000L, resolvePlayerResumeStartPositionMs(45_000L, 120.0, savedQueuePositionMs = 30_000L))
        assertEquals(0L, resolvePlayerResumeStartPositionMs(45_000L, 120.0, savedQueuePositionMs = 120_000L))
        assertEquals(0L, resolvePlayerResumeStartPositionMs(45_000L, 120.0, savedQueuePositionMs = -1L))
        assertEquals(45_000L, resolvePlayerResumeStartPositionMs(45_000L, 120.0))
    }
}
