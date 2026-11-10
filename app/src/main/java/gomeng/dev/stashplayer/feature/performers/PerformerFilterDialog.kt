package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.components.StashTagFilterSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformerFilterDialog(initial: PerformerFilter, profile: StashServerProfile?, onApply: (PerformerFilter) -> Unit, onDismiss: () -> Unit) {
    var favorite by remember { mutableStateOf(initial.favoriteOnly) }
    var gender by remember { mutableStateOf(initial.gender) }
    var tags by remember { mutableStateOf(initial.tags) }
    var ageMin by remember { mutableStateOf(initial.ageMin?.toString().orEmpty()) }
    var ageMax by remember { mutableStateOf(initial.ageMax?.toString().orEmpty()) }
    var ratingMin by remember { mutableStateOf(initial.ratingMin?.toString().orEmpty()) }
    var ratingMax by remember { mutableStateOf(initial.ratingMax?.toString().orEmpty()) }
    var country by remember { mutableStateOf(initial.country.orEmpty()) }
    var sceneMin by remember { mutableStateOf(initial.sceneMin?.toString().orEmpty()) }
    var sceneMax by remember { mutableStateOf(initial.sceneMax?.toString().orEmpty()) }
    var heightMin by remember { mutableStateOf(initial.heightMin?.toString().orEmpty()) }
    var heightMax by remember { mutableStateOf(initial.heightMax?.toString().orEmpty()) }
    var careerStart by remember { mutableStateOf(initial.careerStart?.toString().orEmpty()) }
    var careerEnd by remember { mutableStateOf(initial.careerEnd?.toString().orEmpty()) }
    var missing by remember { mutableStateOf(initial.missing) }
    var genderOpen by remember { mutableStateOf(false) }
    var missingOpen by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var tagQuery by remember { mutableStateOf("") }
    var tagOptions by remember { mutableStateOf(emptyList<StashSelectedTag>()) }
    var tagLoading by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf(false) }
    var tagRetry by remember { mutableIntStateOf(0) }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(showTags, tagQuery, tagRetry, profile) {
        val active = profile ?: return@LaunchedEffect
        if (!showTags) return@LaunchedEffect
        delay(300)
        tagLoading = true; tagError = false
        try { tagOptions = StashGraphQlClient(active).findTags(query = tagQuery) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { tagError = true }
        finally { tagLoading = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.performer_filter_title), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row { Checkbox(checked = favorite, onCheckedChange = { favorite = it }); Text(stringResource(R.string.performer_filter_favorites), modifier = Modifier.padding(top = 12.dp)) }
                Box {
                    TextButton(onClick = { genderOpen = true }) { Text(stringResource(R.string.performer_filter_gender) + ": " + (performerGenderLabel(gender)?.let { stringResource(it) } ?: gender ?: stringResource(R.string.performer_filter_any))) }
                    DropdownMenu(expanded = genderOpen, onDismissRequest = { genderOpen = false }) {
                        listOf(null, "FEMALE", "MALE", "TRANSGENDER_FEMALE", "TRANSGENDER_MALE", "INTERSEX", "NON_BINARY").forEach { value ->
                            DropdownMenuItem(text = { Text(performerGenderLabel(value)?.let { stringResource(it) } ?: value ?: stringResource(R.string.performer_filter_any)) }, onClick = { gender = value; genderOpen = false })
                        }
                    }
                }
                TextButton(onClick = { showTags = true }) { Text(stringResource(R.string.performer_filter_tags, tags.size)) }
                FilterRange(R.string.performer_filter_age, ageMin, { ageMin = it }, ageMax, { ageMax = it })
                FilterRange(R.string.performer_filter_rating, ratingMin, { ratingMin = it }, ratingMax, { ratingMax = it })
                OutlinedTextField(country, { country = it }, label = { Text(stringResource(R.string.performer_filter_country)) }, singleLine = true)
                FilterRange(R.string.performer_filter_scenes, sceneMin, { sceneMin = it }, sceneMax, { sceneMax = it })
                FilterRange(R.string.performer_filter_height, heightMin, { heightMin = it }, heightMax, { heightMax = it })
                FilterRange(R.string.performer_filter_career, careerStart, { careerStart = it }, careerEnd, { careerEnd = it })
                Box {
                    TextButton(onClick = { missingOpen = true }) { Text(stringResource(R.string.performer_filter_missing) + ": " + (performerMissingLabel(missing)?.let { stringResource(it) } ?: missing ?: stringResource(R.string.performer_filter_any))) }
                    DropdownMenu(expanded = missingOpen, onDismissRequest = { missingOpen = false }) {
                        listOf(null, "image", "birthdate", "country", "gender", "rating", "tags", "details", "stash_id").forEach { value ->
                            DropdownMenuItem(text = { Text(performerMissingLabel(value)?.let { stringResource(it) } ?: value ?: stringResource(R.string.performer_filter_any)) }, onClick = { missing = value; missingOpen = false })
                        }
                    }
                }
                if (invalid) Text(stringResource(R.string.performer_filter_invalid), color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onApply(PerformerFilter()) }) { Text(stringResource(R.string.performer_filter_clear)) }
            TextButton(onClick = {
                val all = listOf(ageMin, ageMax, ratingMin, ratingMax, sceneMin, sceneMax, heightMin, heightMax, careerStart, careerEnd)
                val validNumbers = all.all { it.isBlank() || it.toIntOrNull()?.let { n -> n in 0..9999 } == true }
                fun validRange(a: String, b: String) = a.isBlank() || b.isBlank() || (a.toIntOrNull() ?: 0) <= (b.toIntOrNull() ?: 0)
                val valid = validNumbers && listOf(ageMin to ageMax, ratingMin to ratingMax, sceneMin to sceneMax, heightMin to heightMax, careerStart to careerEnd).all { validRange(it.first, it.second) } &&
                    listOf(ratingMin, ratingMax).all { it.isBlank() || (it.toIntOrNull() ?: 101) <= 100 } &&
                    listOf(careerStart, careerEnd).all { it.isBlank() || (it.toIntOrNull() ?: 0) in 1900..2100 } &&
                    (country.isBlank() || country.trim().matches(Regex("[A-Za-z]{2}")))
                if (!valid) invalid = true else onApply(PerformerFilter(favorite, gender, tags, ageMin.toIntOrNull(), ageMax.toIntOrNull(), ratingMin.toIntOrNull(), ratingMax.toIntOrNull(), country.trim().ifBlank { null }, sceneMin.toIntOrNull(), sceneMax.toIntOrNull(), heightMin.toIntOrNull(), heightMax.toIntOrNull(), careerStart.toIntOrNull(), careerEnd.toIntOrNull(), missing))
            }) { Text(stringResource(R.string.performer_filter_apply)) }
            }
        }
    }
    if (showTags) StashTagFilterSheet(
        videoFilter = StashVideoFilterState(tags = tags), tagQuery = tagQuery, tagOptions = tagOptions,
        isLoading = tagLoading, error = if (tagError) stringResource(R.string.performer_tags_error) else null,
        onTagQueryChange = { tagQuery = it }, onApplyTags = { tags = it },
        onRetry = { tagRetry++ }, onDismiss = { showTags = false },
    )
}

@Composable
private fun FilterRange(label: Int, min: String, onMin: (String) -> Unit, max: String, onMax: (String) -> Unit) {
    Text(stringResource(label), modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(min, onMin, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.performer_filter_min)) }, singleLine = true)
        OutlinedTextField(max, onMax, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.performer_filter_max)) }, singleLine = true)
    }
}
