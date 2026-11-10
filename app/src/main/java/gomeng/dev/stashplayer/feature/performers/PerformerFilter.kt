package gomeng.dev.stashplayer.feature.performers

import gomeng.dev.stashplayer.core.model.StashSelectedTag

data class PerformerFilter(
    val favoriteOnly: Boolean = false,
    val gender: String? = null,
    val tags: List<StashSelectedTag> = emptyList(),
    val ageMin: Int? = null, val ageMax: Int? = null,
    val ratingMin: Int? = null, val ratingMax: Int? = null,
    val country: String? = null,
    val sceneMin: Int? = null, val sceneMax: Int? = null,
    val heightMin: Int? = null, val heightMax: Int? = null,
    val careerStart: Int? = null, val careerEnd: Int? = null,
    val missing: String? = null,
) {
    fun toGraphQl(): Map<String, Any?>? = buildMap {
        if (favoriteOnly) put("filter_favorites", true)
        gender?.let { put("gender", mapOf("value_list" to listOf(it), "modifier" to "INCLUDES")) }
        if (tags.isNotEmpty()) put("tags", mapOf("value" to tags.map { it.id }, "modifier" to "INCLUDES_ALL", "depth" to 0))
        range(ageMin, ageMax)?.let { put("age", it) }
        range(ratingMin, ratingMax)?.let { put("rating100", it) }
        country?.trim()?.takeIf { it.isNotEmpty() }?.let { put("country", mapOf("value" to it.uppercase(), "modifier" to "EQUALS")) }
        range(sceneMin, sceneMax)?.let { put("scene_count", it) }
        range(heightMin, heightMax)?.let { put("height_cm", it) }
        careerStart?.let { put("career_start", mapOf("value" to "${it - 1}-12-31", "modifier" to "GREATER_THAN")) }
        careerEnd?.let { put("career_end", mapOf("value" to "${it + 1}-01-01", "modifier" to "LESS_THAN")) }
        missing?.takeIf { it.isNotBlank() }?.let { put("is_missing", it) }
    }.takeIf { it.isNotEmpty() }
}

private fun range(min: Int?, max: Int?): Map<String, Any>? = when {
    min != null && max != null -> mapOf("value" to min, "value2" to max, "modifier" to "BETWEEN")
    min != null -> mapOf("value" to min - 1, "modifier" to "GREATER_THAN")
    max != null -> mapOf("value" to max + 1, "modifier" to "LESS_THAN")
    else -> null
}
