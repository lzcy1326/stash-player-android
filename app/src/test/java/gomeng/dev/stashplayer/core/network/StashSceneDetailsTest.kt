package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StashSceneDetailsTest {
    @Test
    fun parsesOptionalDetailsWithoutAffectingPlaybackFields() {
        val scene = requireNotNull(parseFindSceneResponse("""
            {"data":{"findScene":{
              "id":"42", "title":"Sample", "date":"2024-01-01",
              "details":"  Description  ",
              "performers":[{"id":"1","name":" Alice "},{"id":"2","name":""}],
              "paths":{"stream":"https://example.com/video.mp4"}
            }}}
        """.trimIndent()))
        assertEquals("2024-01-01", scene.date)
        assertEquals("Description", scene.details)
        assertEquals(listOf("Alice"), scene.performers)
        assertEquals("https://example.com/video.mp4", scene.streamUrl)
    }

    @Test
    fun missingDetailsRemainOptional() {
        val scene = requireNotNull(parseFindSceneResponse("""
            {"data":{"findScene":{"id":"42","paths":{"stream":"https://example.com/video.mp4"}}}}
        """.trimIndent()))
        assertNull(scene.date)
        assertNull(scene.details)
        assertTrue(scene.performers.isEmpty())
    }
}
