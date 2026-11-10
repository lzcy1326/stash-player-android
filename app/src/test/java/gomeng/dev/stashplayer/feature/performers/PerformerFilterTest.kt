package gomeng.dev.stashplayer.feature.performers

import gomeng.dev.stashplayer.core.model.StashSelectedTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformerFilterTest {
    @Test fun `filter composes server criteria and inclusive bounds`() {
        val filter = PerformerFilter(favoriteOnly = true, gender = "FEMALE", tags = listOf(StashSelectedTag("3", "Tag")), ageMin = 20, ageMax = 30, ratingMin = 80, country = "jp", sceneMax = 10, heightMin = 150, careerStart = 2000, careerEnd = 2020, missing = "image")
        val result = filter.toGraphQl()!!
        assertEquals(true, result["filter_favorites"])
        assertEquals(mapOf("value_list" to listOf("FEMALE"), "modifier" to "INCLUDES"), result["gender"])
        assertEquals(mapOf("value" to listOf("3"), "modifier" to "INCLUDES_ALL", "depth" to 0), result["tags"])
        assertEquals(mapOf("value" to 20, "value2" to 30, "modifier" to "BETWEEN"), result["age"])
        assertEquals(mapOf("value" to 79, "modifier" to "GREATER_THAN"), result["rating100"])
        assertEquals(mapOf("value" to "JP", "modifier" to "EQUALS"), result["country"])
        assertEquals(mapOf("value" to 11, "modifier" to "LESS_THAN"), result["scene_count"])
        assertEquals(mapOf("value" to "1999-12-31", "modifier" to "GREATER_THAN"), result["career_start"])
        assertEquals(mapOf("value" to "2021-01-01", "modifier" to "LESS_THAN"), result["career_end"])
        assertEquals("image", result["is_missing"])
        assertNull(PerformerFilter().toGraphQl())
    }
}
