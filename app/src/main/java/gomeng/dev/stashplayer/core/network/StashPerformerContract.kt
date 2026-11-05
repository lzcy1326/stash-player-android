package gomeng.dev.stashplayer.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Minimal list response, separate from the existing id/name entity selector. */
data class StashPerformerCard(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val imagePath: String?,
    val gender: String?,
    val country: String?,
    val birthdate: String?,
    val deathDate: String?,
    val favorite: Boolean,
    val rating100: Int?,
    val sceneCount: Int,
)

data class StashPerformerPage(val performers: List<StashPerformerCard>, val count: Int)

internal val performerCardsQuery = """
    query PerformerCards(${'$'}filter: FindFilterType, ${'$'}performerFilter: PerformerFilterType) {
      findPerformers(filter: ${'$'}filter, performer_filter: ${'$'}performerFilter) {
        count
        performers {
          id name alias_list image_path gender country birthdate death_date favorite rating100 scene_count
        }
      }
    }
""".trimIndent()

internal fun performerCardsVariables(
    page: Int,
    perPage: Int,
    query: String?,
    sort: String = "name",
    direction: String = "ASC",
    performerFilter: Map<String, Any?>? = null,
): Map<String, Any?> = mapOf(
    "filter" to mapOf(
        "page" to page.coerceAtLeast(1),
        "per_page" to perPage.coerceAtLeast(1),
        "q" to query?.trim()?.takeIf(String::isNotEmpty),
        "sort" to sort,
        "direction" to direction,
    ),
    "performerFilter" to performerFilter,
)

/** Adds the required performer without losing any existing scene criteria. */
internal fun withRequiredPerformer(
    sceneFilter: Map<String, Any?>?,
    performerId: String,
): Map<String, Any?> {
    val id = performerId.trim().also { require(it.isNotEmpty()) }
    val required = mapOf("performers" to mapOf("value" to listOf(id), "modifier" to "INCLUDES"))
    return if (sceneFilter.isNullOrEmpty()) required else mapOf("AND" to sceneFilter) + required
}

private val performerMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

internal fun parsePerformerCards(json: String): StashPerformerPage {
    val response = performerMoshi.adapter(PerformerEnvelope::class.java).fromJson(json)
        ?: error("Empty GraphQL response")
    response.errors?.firstOrNull()?.message?.let { error("GraphQL error: ${redactStashCredentialText(it)}") }
    val result = response.data?.findPerformers ?: error("Missing performer data")
    return StashPerformerPage(
        count = result.count,
        performers = result.performers.map { performer ->
            StashPerformerCard(
                id = performer.id,
                name = performer.name,
                aliases = performer.aliasList,
                imagePath = performer.imagePath,
                gender = performer.gender,
                country = performer.country,
                birthdate = performer.birthdate,
                deathDate = performer.deathDate,
                favorite = performer.favorite,
                rating100 = performer.rating100,
                sceneCount = performer.sceneCount,
            )
        },
    )
}

private data class PerformerEnvelope(val data: PerformerData? = null, val errors: List<PerformerError>? = null)
private data class PerformerError(val message: String)
private data class PerformerData(val findPerformers: PerformerResult? = null)
private data class PerformerResult(val count: Int, val performers: List<PerformerPayload>)
private data class PerformerPayload(
    val id: String,
    val name: String,
    @Json(name = "alias_list") val aliasList: List<String> = emptyList(),
    @Json(name = "image_path") val imagePath: String? = null,
    val gender: String? = null,
    val country: String? = null,
    val birthdate: String? = null,
    @Json(name = "death_date") val deathDate: String? = null,
    val favorite: Boolean = false,
    val rating100: Int? = null,
    @Json(name = "scene_count") val sceneCount: Int = 0,
)
