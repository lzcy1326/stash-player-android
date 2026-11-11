package gomeng.dev.stashplayer.feature.studios

import androidx.test.ext.junit.runners.AndroidJUnit4
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioMutationRequestDeviceTest {
    @Test fun clearingRatingSendsExplicitNullWithoutChangingFavorite() = runBlocking {
        var sentInput: JSONObject? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            sentInput = JSONObject(buffer.readUtf8()).getJSONObject("variables").getJSONObject("input")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"studioUpdate":{"id":"1","favorite":true,"rating100":null}}}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = StashGraphQlClient(StashServerProfile(baseUrl = "http://localhost"), http)
        val result = client.updateStudioRating("1", null)
        val input = requireNotNull(sentInput)
        assertEquals("1", input.getString("id"))
        assertTrue(input.has("rating100"))
        assertTrue(input.isNull("rating100"))
        assertFalse(input.has("favorite"))
        assertEquals(null, result.rating100)
        assertTrue(result.favorite)
    }
}
