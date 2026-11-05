package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StashPerformerContractTest {
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

    @Test fun `required performer survives user filters and clearing them`() {
        val userFilter = mapOf("rating100" to mapOf("value" to 70, "modifier" to "GREATER_THAN"))
        val combined = withRequiredPerformer(userFilter, " 42 ")
        assertEquals(userFilter, combined["AND"])
        assertEquals(mapOf("value" to listOf("42"), "modifier" to "INCLUDES"), combined["performers"])
        assertTrue(withRequiredPerformer(null, "42").containsKey("performers"))
        assertThrows(IllegalArgumentException::class.java) { withRequiredPerformer(null, " ") }
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
