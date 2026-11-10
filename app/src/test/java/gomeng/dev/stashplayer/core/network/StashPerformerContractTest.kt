package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import gomeng.dev.stashplayer.core.model.StashRatingRange
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import gomeng.dev.stashplayer.core.player.afterLoadedPage

class StashPerformerContractTest {
    @Test fun `age respects birthdays and unknown or invalid dates`() {
        val today = LocalDate.of(2026, 11, 5)
        assertEquals(25, performerAge("2000-11-06", null, today))
        assertEquals(26, performerAge("2000-11-05", null, today))
        assertEquals(20, performerAge("2000-01-01", "2020-06-01", today))
        assertNull(performerAge("2000", null, today))
        assertNull(performerAge("2030-01-01", null, today))
        assertNull(performerCountryFlag("unknown"))
        assertEquals("🇯🇵", performerCountryFlag("jp"))
    }
    @Test fun `list parses minimal and nullable data without losing aliases or count`() {
        val result = parsePerformerCards(
            """{"data":{"findPerformers":{"count":2,"performers":[
                {"id":"1","name":"Example","alias_list":["Alias"],"image_path":null,"favorite":true,"rating100":80,"scene_count":3},
                {"id":"2","name":"Other","alias_list":[],"favorite":false,"scene_count":0}
            ]}}}""".trimIndent(),
        )
        assertEquals(2, result.count)
        assertEquals(listOf("Alias"), result.performers.first().aliases)
        assertNull(result.performers.first().imagePath)
        assertEquals(3, result.performers.first().sceneCount)
        assertFalse(result.performers.last().favorite)
    }

    @Test fun `graphql errors and missing data are not treated as empty lists`() {
        assertThrows(IllegalStateException::class.java) { parsePerformerCards("""{"errors":[{"message":"failure"}]}""") }
        assertThrows(IllegalStateException::class.java) { parsePerformerCards("""{"data":{}}""") }
    }

    @Test fun `detail and scene summary preserve optional fields and aggregates`() {
        val detail = parsePerformerDetail("""{"data":{"findPerformer":{
            "id":"1","name":"Example","alias_list":["Alias"],"favorite":false,"scene_count":2,
            "height_cm":165,"urls":["https://example.test"],"tags":[{"name":"Tag"}],
            "stash_ids":[{"endpoint":"https://example.test/graphql","stash_id":"abc"}]
        }}}""")!!
        assertEquals(165, detail.heightCm)
        assertEquals(listOf("Tag"), detail.tags)
        assertEquals(listOf("https://example.test/graphql: abc"), detail.stashIds)
        assertNull(detail.eyeColor)
        assertNull(parsePerformerDetail("""{"data":{"findPerformer":null}}"""))
        assertThrows(IllegalStateException::class.java) { parsePerformerDetail("""{"data":null}""") }
        val summary = parsePerformerSceneSummary("""{"data":{"findScenes":{"count":2,"duration":125.5,"filesize":5000.0}}}""")
        assertEquals(StashPerformerSceneSummary(2, 125.5, 5000.0), summary)
    }

    @Test fun `performer update parses authoritative values and errors`() {
        assertEquals(StashPerformerMutableFields("1", true, null), parsePerformerUpdate("""{"data":{"performerUpdate":{"id":"1","favorite":true,"rating100":null}}}"""))
        assertThrows(IllegalStateException::class.java) { parsePerformerUpdate("""{"errors":[{"message":"failure"}]}""") }
        assertThrows(IllegalStateException::class.java) { parsePerformerUpdate("""{"data":{"performerUpdate":null}}""") }
    }

    @Test fun `required performer survives user filters and clearing them`() {
        val userFilter = mapOf("rating100" to mapOf("value" to 70, "modifier" to "GREATER_THAN"))
        val combined = withRequiredPerformer(userFilter, " 42 ")
        assertEquals(userFilter, combined["AND"])
        assertEquals(mapOf("value" to listOf("42"), "modifier" to "INCLUDES"), combined["performers"])
        assertTrue(withRequiredPerformer(null, "42").containsKey("performers"))
        assertThrows(IllegalArgumentException::class.java) { withRequiredPerformer(null, " ") }
    }

    @Test fun `scene paging preserves performer scope and user criteria without changing ordinary requests`() {
        val filter = StashVideoFilterState(ratingRange = StashRatingRange(min = 60))
        val scoped = buildFindScenesVariables(24, 2, "term", "date", StashSortDirection.Desc, filter, "42")
        val sceneFilter = scoped["sceneFilter"] as Map<*, *>
        assertEquals(mapOf("value" to listOf("42"), "modifier" to "INCLUDES"), sceneFilter["performers"])
        assertEquals(mapOf("rating100" to mapOf("value" to 59, "modifier" to "GREATER_THAN")), sceneFilter["AND"])
        val ordinary = buildFindScenesVariables(24, 1, null, "date", StashSortDirection.Desc, filter)
        assertFalse((ordinary["sceneFilter"] as Map<*, *>).containsKey("performers"))
        val cleared = buildFindScenesVariables(24, 1, null, "date", StashSortDirection.Desc, performerId = "42")
        assertTrue((cleared["sceneFilter"] as Map<*, *>).containsKey("performers"))
    }

    @Test fun `performer queue continuation carries immutable scope across pages`() {
        val original = PlayerPlaybackQueueContinuation.Performer(
            profile = StashServerProfile(baseUrl = "https://example.test"), performerId = "42", query = "term",
            sort = "date", direction = StashSortDirection.Desc, videoFilter = StashVideoFilterState(),
            nextPage = 2, pageSize = 24, hasMore = true,
        )
        val next = original.afterLoadedPage(2, 49) as PlayerPlaybackQueueContinuation.Performer
        assertEquals("42", next.performerId)
        assertEquals("term", next.query)
        assertEquals(3, next.nextPage)
        assertTrue(next.hasMore)
        assertFalse((next.afterLoadedPage(3, 49) as PlayerPlaybackQueueContinuation.Performer).hasMore)
    }

    @Test fun `paging normalizes values without affecting sort or filter`() {
        val variables = performerCardsVariables(0, 40, "  alias ", "random_123", performerFilter = mapOf("filter_favorites" to true))
        val filter = variables["filter"] as Map<*, *>
        assertEquals(1, filter["page"])
        assertEquals("alias", filter["q"])
        assertEquals("random_123", filter["sort"])
        assertEquals(mapOf("filter_favorites" to true), variables["performerFilter"])
    }
}
