package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.StashRatingRange
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import gomeng.dev.stashplayer.core.player.afterLoadedPage
import gomeng.dev.stashplayer.feature.studios.StudioFilter
import org.junit.Assert.*
import org.junit.Test

class StashStudioContractTest {
    @Test fun `studio list preserves hierarchy aliases null rating and total`() {
        val result = parseStudioCards("""{"data":{"findStudios":{"count":41,"studios":[
            {"id":"1","name":"Studio","aliases":["Alias"],"image_path":null,"favorite":true,"rating100":null,"scene_count":0,
            "parent_studio":{"id":"p","name":"Parent"},"child_studios":[{"id":"c","name":"Child"}]}]}}}""")
        assertEquals(41, result.count)
        val card = result.studios.single()
        assertEquals(listOf("Alias"), card.aliases)
        assertEquals("p", card.parent?.id)
        assertEquals("c", card.children.single().id)
        assertNull(card.rating100)
        assertNull(card.imagePath)
        assertTrue(card.favorite)
    }

    @Test fun `detail and summary parse optional information and empty scopes`() {
        val card = parseStudioDetail("""{"data":{"findStudio":{"id":"1","name":"Studio","urls":["https://example.test"],"details":"Description","tags":[{"id":"t","name":"Tag"}],"stash_ids":[{"endpoint":"https://example.test/graphql","stash_id":"abc"}]}}}""")!!
        assertEquals("Description", card.details)
        assertEquals("t", card.tags.single().id)
        assertEquals("abc", card.stashIds.single().stashId)
        assertNull(parseStudioDetail("""{"data":{"findStudio":null}}"""))
        assertEquals(StashPerformerSceneSummary(0, 0.0, 0.0), parseStudioSummary("""{"data":{"findScenes":{"count":0,"duration":0,"filesize":0}}}"""))
        assertThrows(IllegalStateException::class.java) { parseStudioDetail("""{"data":null}""") }
        assertThrows(IllegalStateException::class.java) { parseStudioCards("""{"data":{}}""") }
        assertThrows(IllegalStateException::class.java) { parseStudioCards("""{"errors":[{"message":"offline"}]}""") }
    }

    @Test fun `studio filter uses favorite rather than performer filter_favorites and inclusive ranges`() {
        assertNull(StudioFilter().toGraphQl())
        val filter = StudioFilter(true, 0, 100, 0, 10, listOf(StashSelectedTag("t", "Tag")), StashStudioReference("p", "Parent"), false).toGraphQl()!!
        assertEquals(true, filter["favorite"])
        assertFalse(filter.containsKey("filter_favorites"))
        assertEquals(mapOf("value" to 0, "value2" to 100, "modifier" to "BETWEEN"), filter["rating100"])
        assertEquals(mapOf("value" to listOf("p"), "modifier" to "INCLUDES"), filter["parents"])
        assertEquals(false, filter["organized"])
        assertEquals(mapOf("value" to -1, "modifier" to "GREATER_THAN"), StudioFilter(sceneMin = 0).toGraphQl()!!["scene_count"])
    }

    @Test fun `fixed studio and child scope survive user filters and page continuation`() {
        val user = StashVideoFilterState(ratingRange = StashRatingRange(min = 60))
        val variables = buildFindScenesVariables(12, 2, " term ", "date", StashSortDirection.Desc, user, studioId = " 42 ", includeStudioChildren = true)
        assertEquals("term", variables["q"])
        assertEquals(2, variables["page"])
        val scoped = variables["sceneFilter"] as Map<*, *>
        assertEquals(mapOf("value" to listOf("42"), "modifier" to "INCLUDES", "depth" to -1), scoped["studios"])
        assertEquals(mapOf("rating100" to mapOf("value" to 59, "modifier" to "GREATER_THAN")), scoped["AND"])
        assertEquals(mapOf("studios" to mapOf("value" to listOf("42"), "modifier" to "INCLUDES", "depth" to 0)), withRequiredStudio(null, "42", false))
        assertNull(buildFindScenesVariables(12, 1, null, "date", StashSortDirection.Desc)["sceneFilter"])
        assertThrows(IllegalArgumentException::class.java) { withRequiredStudio(null, " ", false) }
        val original = PlayerPlaybackQueueContinuation.Studio(StashServerProfile(baseUrl = "https://example.test"), "42", true, "term", "date", StashSortDirection.Desc, user, 2, 12, true)
        val next = original.afterLoadedPage(2, 25) as PlayerPlaybackQueueContinuation.Studio
        assertEquals("42", next.studioId)
        assertTrue(next.includeChildren)
        assertEquals(user, next.videoFilter)
        assertEquals(3, next.nextPage)
        assertTrue(next.hasMore)
        assertFalse(next.afterLoadedPage(3, 25).hasMore)
    }

    @Test fun `clearing rating preserves null and mutations reject missing payload`() {
        assertEquals(StashStudioMutableFields("1", true, null), parseStudioUpdate("""{"data":{"studioUpdate":{"id":"1","favorite":true,"rating100":null}}}"""))
        assertThrows(IllegalStateException::class.java) { parseStudioUpdate("""{"data":{"studioUpdate":null}}""") }
        val variables = studioCardsVariables(0, 0, " alias ", "random_123", "DESC", mapOf("favorite" to true))
        val find = variables["filter"] as Map<*, *>
        assertEquals(1, find["page"])
        assertEquals(1, find["per_page"])
        assertEquals("alias", find["q"])
        assertEquals("random_123", find["sort"])
        assertEquals(mapOf("favorite" to true), variables["studioFilter"])
    }
}
