package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionComparisonTest {
    @Test
    fun lzcyForkFixVersionIncreasesWithinSameUpstreamVersion() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy1.0.0", "1.13.2-lzcy1.0.1"))
        assertFalse(isAppUpdateAvailable("1.13.2-lzcy1.0.1", "1.13.2-lzcy1.0.0"))
    }

    @Test
    fun lzcyForkFeatureVersionOutranksFixVersion() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy1.0.9", "1.13.2-lzcy1.1.0"))
    }

    @Test
    fun debugSuffixDoesNotHideForkRevisionUpdate() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy1.0.0-debug", "1.13.2-lzcy1.0.1"))
    }

    @Test
    fun newUpstreamVersionIsNewerThanOlderForkRevision() {
        assertTrue(isAppUpdateAvailable("1.13.2-lzcy9.9.9", "1.13.3-lzcy1.0.0"))
    }

    @Test
    fun initialForkRevisionIsNewerThanUnmodifiedUpstreamVersion() {
        assertTrue(isAppUpdateAvailable("1.13.2", "1.13.2-lzcy1.0.0"))
    }

    @Test
    fun releaseParserPreservesLzcyVersionSuffix() {
        val release = parseGitHubLatestRelease(
            """{"tag_name":"v1.13.2-lzcy1.0.0","html_url":"https://example.test/release"}""",
        )

        assertEquals("1.13.2-lzcy1.0.0", release?.versionName)
    }

    @Test
    fun channelManifestSelectsOnlyMatchingApk() {
        val manifest = """
            {
              "phone": {
                "versionName": "1.13.3-lzcy1.6.0",
                "versionCode": 60010600,
                "apkUrl": "https://github.com/lzcy1326/stash-player-android/releases/download/phone-v1.13.3-lzcy1.6.0/StashPlayer-Mobile-1.13.3-lzcy1.6.0.apk",
                "releaseUrl": "https://github.com/lzcy1326/stash-player-android/releases/tag/phone-v1.13.3-lzcy1.6.0"
              },
              "tv": {
                "versionName": "1.13.3-lzcy1.7.0",
                "versionCode": 60010700,
                "apkUrl": "https://github.com/lzcy1326/stash-player-android/releases/download/tv-v1.13.3-lzcy1.7.0/StashPlayer-TV-1.13.3-lzcy1.7.0.apk",
                "releaseUrl": "https://github.com/lzcy1326/stash-player-android/releases/tag/tv-v1.13.3-lzcy1.7.0"
              }
            }
        """.trimIndent()
        assertEquals("1.13.3-lzcy1.6.0", parseChannelUpdate(manifest, "phone")?.versionName)
        assertEquals("1.13.3-lzcy1.7.0", parseChannelUpdate(manifest, "tv")?.versionName)
        assertEquals(null, parseChannelUpdate(manifest.replace("StashPlayer-Mobile-", "StashPlayer-TV-"), "phone"))
        assertEquals(null, parseChannelUpdate("{}", "phone"))
    }
}
