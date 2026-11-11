package gomeng.dev.stashplayer.feature.studios

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R

@Composable
internal fun StudioActiveFilters(filter: StudioFilter) {
    if (filter == StudioFilter()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filter.favoriteOnly) FilterSummary(stringResource(R.string.studio_favorites))
        if (filter.tags.isNotEmpty()) FilterSummary(filter.tags.joinToString { it.name })
        FilterRangeSummary(R.string.performer_filter_rating, filter.ratingMin, filter.ratingMax)
        FilterRangeSummary(R.string.performer_filter_scenes, filter.sceneMin, filter.sceneMax)
        filter.parent?.let { FilterSummary("${stringResource(R.string.studio_parent)}: ${it.name}") }
        filter.organized?.let { FilterSummary("${stringResource(R.string.studio_organized)}: ${stringResource(if (it) R.string.studio_yes else R.string.studio_no)}") }
    }
}

@Composable
private fun FilterRangeSummary(label: Int, min: Int?, max: Int?) {
    if (min == null && max == null) return
    val range = when {
        min != null && max != null -> "$min–$max"
        min != null -> "≥$min"
        else -> "≤$max"
    }
    FilterSummary("${stringResource(label)}: $range")
}

@Composable
private fun FilterSummary(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
