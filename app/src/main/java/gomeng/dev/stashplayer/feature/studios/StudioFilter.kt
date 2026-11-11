package gomeng.dev.stashplayer.feature.studios

import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.network.StashStudioReference

data class StudioFilter(
    val favoriteOnly: Boolean = false,
    val ratingMin: Int? = null,
    val ratingMax: Int? = null,
    val sceneMin: Int? = null,
    val sceneMax: Int? = null,
    val tags: List<StashSelectedTag> = emptyList(),
    val parent: StashStudioReference? = null,
    val organized: Boolean? = null,
) {
    val activeCount: Int get() = listOf(favoriteOnly, ratingMin != null || ratingMax != null,
        sceneMin != null || sceneMax != null, tags.isNotEmpty(), parent != null, organized != null).count { it }

    fun toGraphQl(): Map<String, Any?>? = buildMap {
        if (favoriteOnly) put("favorite", true)
        range(ratingMin, ratingMax)?.let { put("rating100", it) }
        range(sceneMin, sceneMax)?.let { put("scene_count", it) }
        if (tags.isNotEmpty()) put("tags", mapOf("value" to tags.map { it.id }, "modifier" to "INCLUDES_ALL", "depth" to 0))
        parent?.let { put("parents", mapOf("value" to listOf(it.id), "modifier" to "INCLUDES")) }
        organized?.let { put("organized", it) }
    }.takeIf { it.isNotEmpty() }
}

private fun range(min: Int?, max: Int?): Map<String, Any>? = when {
    min != null && max != null -> mapOf("value" to min, "value2" to max, "modifier" to "BETWEEN")
    min != null -> mapOf("value" to min - 1, "modifier" to "GREATER_THAN")
    max != null -> mapOf("value" to max + 1, "modifier" to "LESS_THAN")
    else -> null
}
