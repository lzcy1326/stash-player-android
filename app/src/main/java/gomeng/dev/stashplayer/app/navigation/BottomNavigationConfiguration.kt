package gomeng.dev.stashplayer.app.navigation

/** Stable identifiers used in preferences; these are independent of UI labels and enum names. */
internal enum class NavigationFeature(val id: String) {
    Explore("explore"),
    Shorts("shorts"),
    Gallery("gallery"),
    Queue("queue"),
    Performers("performers"),
    Studios("studios"),
}

internal const val MAX_NAVIGATION_FEATURES = 4
internal val navigationFeaturePool = NavigationFeature.entries.toList()
internal val defaultNavigationFeatures = listOf(
    NavigationFeature.Explore,
    NavigationFeature.Shorts,
    NavigationFeature.Gallery,
    NavigationFeature.Queue,
)

/** Restores a usable four-item configuration without writing anything to storage. */
internal fun restoreNavigationFeatures(value: String?): List<NavigationFeature> {
    val selected = value.orEmpty().split(',')
        .mapNotNull { id -> navigationFeaturePool.firstOrNull { it.id == id.trim() } }
        .distinct()
        .take(MAX_NAVIGATION_FEATURES)
    return (selected + defaultNavigationFeatures + navigationFeaturePool)
        .distinct()
        .take(MAX_NAVIGATION_FEATURES)
}

internal fun isValidNavigationFeatures(features: List<NavigationFeature>): Boolean =
    features.size == MAX_NAVIGATION_FEATURES && features.distinct().size == MAX_NAVIGATION_FEATURES

internal fun serializeNavigationFeatures(features: List<NavigationFeature>): String? =
    features.takeIf(::isValidNavigationFeatures)?.joinToString(",") { it.id }

internal sealed interface AddNavigationFeatureResult {
    data class Added(val features: List<NavigationFeature>) : AddNavigationFeatureResult
    data object AtCapacity : AddNavigationFeatureResult
    data object AlreadySelected : AddNavigationFeatureResult
}

internal fun addNavigationFeature(
    features: List<NavigationFeature>,
    feature: NavigationFeature,
): AddNavigationFeatureResult = when {
    feature in features -> AddNavigationFeatureResult.AlreadySelected
    features.size >= MAX_NAVIGATION_FEATURES -> AddNavigationFeatureResult.AtCapacity
    else -> AddNavigationFeatureResult.Added(features + feature)
}

internal fun moveNavigationFeature(
    features: List<NavigationFeature>,
    fromIndex: Int,
    toIndex: Int,
): List<NavigationFeature> {
    if (fromIndex !in features.indices || toIndex !in features.indices || fromIndex == toIndex) return features
    return features.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}
