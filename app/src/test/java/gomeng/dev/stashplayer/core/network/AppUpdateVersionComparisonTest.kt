package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionComparisonTest {
    @Test
    fun lzcyForkRevisionIncreasesWithinSameUpstreamVersion() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy.1", "1.13.2-lzcy.2"))
        assertFalse(isAppUpdateAvailable("1.13.2-lzcy.2", "1.13.2-lzcy.1"))
    }

    @Test
    fun debugSuffixDoesNotHideForkRevisionUpdate() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy.1-debug", "1.13.2-lzcy.2"))
    }

    @Test
    fun newUpstreamVersionIsNewerThanOlderForkRevision() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy.9", "1.13.3-lzcy.1"))
    }

    @Test
    fun initialForkRevisionIsNewerThanUnmodifiedUpstreamVersion() {
        assertTrue(isAppUpdateAvailable("1.13.2", "1.13.2-lzcy.1"))
    }

    @Test
    fun releaseParserPreservesLzcyVersionSuffix() {
        val release = parseGitHubLatestRelease(
            """{"tag_name":"v1.13.2-lzcy.1","html_url":"https://example.test/release"}""",
        )

        assertEquals("1.13.2-lzcy.1", release?.versionName)
    }
}
