package gomeng.dev.stashplayer.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.Period

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

data class StashPerformerDetail(
    val card: StashPerformerCard,
    val urls: List<String>,
    val ethnicity: String?,
    val eyeColor: String?,
    val heightCm: Int?,
    val measurements: String?,
    val careerStart: String?,
    val careerEnd: String?,
    val hairColor: String?,
    val details: String?,
    val tags: List<String>,
    val stashIds: List<String>,
)

data class StashPerformerSceneSummary(val count: Int, val durationSeconds: Double, val sizeBytes: Double)

internal val performerDetailQuery = """
    query PerformerDetail(${'$'}id: ID!) {
      findPerformer(id: ${'$'}id) {
        id name alias_list image_path gender country birthdate death_date favorite rating100 scene_count
        urls ethnicity eye_color height_cm measurements career_start career_end hair_color details
        tags { name } stash_ids { endpoint stash_id }
      }
    }
""".trimIndent()

internal val performerSummaryQuery = """
    query PerformerSceneSummary(${'$'}id: ID!) {
      findScenes(filter: { page: 1, per_page: 1 }, scene_filter: { performers: { value: [${'$'}id], modifier: INCLUDES } }) {
        count duration filesize
      }
    }
""".trimIndent()

internal fun performerAge(birthdate: String?, deathDate: String?, today: LocalDate = LocalDate.now()): Int? {
    val birth = birthdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val end = deathDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    if (end.isBefore(birth) || end.isAfter(today)) return null
    return Period.between(birth, end).years
}

internal fun performerCountryFlag(country: String?): String? {
    val code = country?.uppercase(java.util.Locale.ROOT) ?: return null
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    return code.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

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
        performers = result.performers.map(PerformerPayload::toCard),
    )
}

internal fun parsePerformerDetail(json: String): StashPerformerDetail? {
    val response = performerMoshi.adapter(PerformerDetailEnvelope::class.java).fromJson(json)
        ?: error("Empty GraphQL response")
    response.errors?.firstOrNull()?.message?.let { error("GraphQL error: ${redactStashCredentialText(it)}") }
    val p = (response.data ?: error("Missing performer data")).findPerformer ?: return null
    return StashPerformerDetail(
        p.toCard(), p.urls.orEmpty(), p.ethnicity, p.eyeColor, p.heightCm, p.measurements,
        p.careerStart, p.careerEnd, p.hairColor, p.details,
        p.tags.map { it.name }, p.stashIds.map { "${it.endpoint}: ${it.stashId}" },
    )
}

internal fun parsePerformerSceneSummary(json: String): StashPerformerSceneSummary {
    val response = performerMoshi.adapter(PerformerSummaryEnvelope::class.java).fromJson(json)
        ?: error("Empty GraphQL response")
    response.errors?.firstOrNull()?.message?.let { error("GraphQL error: ${redactStashCredentialText(it)}") }
    val result = response.data?.findScenes ?: error("Missing scene summary")
    return StashPerformerSceneSummary(result.count, result.duration, result.filesize)
}

private fun PerformerPayload.toCard() = StashPerformerCard(
    id, name, aliasList, imagePath, gender, country, birthdate, deathDate, favorite, rating100, sceneCount,
)

private data class PerformerEnvelope(val data: PerformerData? = null, val errors: List<PerformerError>? = null)
private data class PerformerError(val message: String)
private data class PerformerData(val findPerformers: PerformerResult? = null)
private data class PerformerDetailEnvelope(val data: PerformerDetailData? = null, val errors: List<PerformerError>? = null)
private data class PerformerDetailData(val findPerformer: PerformerPayload? = null)
private data class PerformerSummaryEnvelope(val data: PerformerSummaryData? = null, val errors: List<PerformerError>? = null)
private data class PerformerSummaryData(val findScenes: PerformerSummaryPayload? = null)
private data class PerformerSummaryPayload(val count: Int, val duration: Double, val filesize: Double)
private data class PerformerTag(val name: String)
private data class PerformerStashId(val endpoint: String, @Json(name = "stash_id") val stashId: String)
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
    val urls: List<String>? = null,
    val ethnicity: String? = null,
    @Json(name = "eye_color") val eyeColor: String? = null,
    @Json(name = "height_cm") val heightCm: Int? = null,
    val measurements: String? = null,
    @Json(name = "career_start") val careerStart: String? = null,
    @Json(name = "career_end") val careerEnd: String? = null,
    @Json(name = "hair_color") val hairColor: String? = null,
    val details: String? = null,
    val tags: List<PerformerTag> = emptyList(),
    @Json(name = "stash_ids") val stashIds: List<PerformerStashId> = emptyList(),
)
