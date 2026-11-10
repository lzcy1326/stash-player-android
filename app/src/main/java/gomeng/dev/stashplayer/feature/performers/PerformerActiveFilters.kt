package gomeng.dev.stashplayer.feature.performers

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
internal fun PerformerActiveFilters(filter: PerformerFilter) {
    if (filter == PerformerFilter()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filter.favoriteOnly) FilterSummary(stringResource(R.string.performer_filter_favorites))
        filter.gender?.let { gender ->
            val name = performerGenderLabel(gender)
            FilterSummary("${stringResource(R.string.performer_filter_gender)}: ${name?.let { stringResource(it) } ?: gender}")
        }
        if (filter.tags.isNotEmpty()) FilterSummary(filter.tags.joinToString { it.name })
        FilterRangeSummary(R.string.performer_filter_age, filter.ageMin, filter.ageMax)
        FilterRangeSummary(R.string.performer_filter_rating, filter.ratingMin, filter.ratingMax)
        filter.country?.let { FilterSummary("${stringResource(R.string.performer_filter_country)}: $it") }
        FilterRangeSummary(R.string.performer_filter_scenes, filter.sceneMin, filter.sceneMax)
        FilterRangeSummary(R.string.performer_filter_height, filter.heightMin, filter.heightMax)
        FilterRangeSummary(R.string.performer_filter_career, filter.careerStart, filter.careerEnd)
        filter.missing?.let { missing ->
            val label = performerMissingLabel(missing)?.let { stringResource(it) } ?: missing
            FilterSummary("${stringResource(R.string.performer_filter_missing)}: $label")
        }
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
