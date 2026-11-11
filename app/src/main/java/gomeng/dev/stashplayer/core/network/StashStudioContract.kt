package gomeng.dev.stashplayer.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class StashStudioReference(val id: String, val name: String)

data class StashStudioCard(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    @Json(name = "image_path") val imagePath: String? = null,
    val favorite: Boolean = false,
    val rating100: Int? = null,
    @Json(name = "scene_count") val sceneCount: Int = 0,
    @Json(name = "performer_count") val performerCount: Int = 0,
    @Json(name = "parent_studio") val parent: StashStudioReference? = null,
    @Json(name = "child_studios") val children: List<StashStudioReference> = emptyList(),
    val urls: List<String> = emptyList(),
    val details: String? = null,
    val tags: List<StashStudioReference> = emptyList(),
    @Json(name = "stash_ids") val stashIds: List<StashStudioStashId> = emptyList(),
)

data class StashStudioStashId(val endpoint: String, @Json(name = "stash_id") val stashId: String)
data class StashStudioPage(val studios: List<StashStudioCard>, val count: Int)
data class StashStudioMutableFields(val id: String, val favorite: Boolean, val rating100: Int?)

private const val STUDIO_CARD_FIELDS = """
    id name aliases image_path favorite rating100 scene_count performer_count
    parent_studio { id name } child_studios { id name }
"""

internal val studioCardsQuery = """
    query StudioCards(${'$'}filter: FindFilterType, ${'$'}studioFilter: StudioFilterType) {
      findStudios(filter: ${'$'}filter, studio_filter: ${'$'}studioFilter) {
        count studios { $STUDIO_CARD_FIELDS }
      }
    }
""".trimIndent()

internal val studioDetailQuery = """
    query StudioDetail(${'$'}id: ID!) {
      findStudio(id: ${'$'}id) {
        $STUDIO_CARD_FIELDS
        urls details tags { id name } stash_ids { endpoint stash_id }
      }
    }
""".trimIndent()

internal val studioSummaryQuery = """
    query StudioSceneSummary(${'$'}id: ID!, ${'$'}depth: Int!) {
      findScenes(filter: { page: 1, per_page: 1 }, scene_filter: { studios: { value: [${'$'}id], modifier: INCLUDES, depth: ${'$'}depth } }) {
        count duration filesize
      }
    }
""".trimIndent()

internal val studioUpdateQuery = """
    mutation StudioUpdate(${'$'}input: StudioUpdateInput!) {
      studioUpdate(input: ${'$'}input) { id favorite rating100 }
    }
""".trimIndent()

internal fun studioCardsVariables(
    page: Int, perPage: Int, query: String?, sort: String = "name", direction: String = "ASC",
    studioFilter: Map<String, Any?>? = null,
): Map<String, Any?> = mapOf(
    "filter" to mapOf("page" to page.coerceAtLeast(1), "per_page" to perPage.coerceAtLeast(1),
        "q" to query?.trim()?.takeIf(String::isNotEmpty), "sort" to sort, "direction" to direction),
    "studioFilter" to studioFilter,
)

/** The fixed studio scope stays in an AND with user filters, including another studio filter. */
internal fun withRequiredStudio(sceneFilter: Map<String, Any?>?, studioId: String, includeChildren: Boolean): Map<String, Any?> {
    val id = studioId.trim().also { require(it.isNotEmpty()) }
    val required = mapOf("studios" to mapOf("value" to listOf(id), "modifier" to "INCLUDES", "depth" to if (includeChildren) -1 else 0))
    return if (sceneFilter.isNullOrEmpty()) required else mapOf("AND" to sceneFilter) + required
}

private val studioMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private data class StudioEnvelope(val data: StudioData? = null, val errors: List<StudioError> = emptyList())
private data class StudioError(val message: String)
private data class StudioData(
    val findStudios: StashStudioPage? = null,
    val findStudio: StashStudioCard? = null,
    val studioUpdate: StashStudioMutableFields? = null,
    val findScenes: StudioSummaryPayload? = null,
)
private data class StudioSummaryPayload(val count: Int, val duration: Double, val filesize: Double)

private fun studioData(json: String): StudioData {
    val result = studioMoshi.adapter(StudioEnvelope::class.java).fromJson(json) ?: error("Empty GraphQL response")
    result.errors.firstOrNull()?.let { error("GraphQL error: ${redactStashCredentialText(it.message)}") }
    return result.data ?: error("Missing studio data")
}

internal fun parseStudioCards(json: String): StashStudioPage = studioData(json).findStudios ?: error("Missing studio list")
internal fun parseStudioDetail(json: String): StashStudioCard? = studioData(json).findStudio
internal fun parseStudioUpdate(json: String): StashStudioMutableFields = studioData(json).studioUpdate ?: error("Missing studio update")
internal fun parseStudioSummary(json: String): StashPerformerSceneSummary {
    val result = studioData(json).findScenes ?: error("Missing studio summary")
    return StashPerformerSceneSummary(result.count, result.duration, result.filesize)
}
