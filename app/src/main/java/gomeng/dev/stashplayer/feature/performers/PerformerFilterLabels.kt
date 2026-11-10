package gomeng.dev.stashplayer.feature.performers

import androidx.annotation.StringRes
import gomeng.dev.stashplayer.R

@StringRes
internal fun performerGenderLabel(value: String?): Int? = when (value) {
    "FEMALE" -> R.string.performer_gender_female
    "MALE" -> R.string.performer_gender_male
    "TRANSGENDER_FEMALE" -> R.string.performer_gender_trans_female
    "TRANSGENDER_MALE" -> R.string.performer_gender_trans_male
    "INTERSEX" -> R.string.performer_gender_intersex
    "NON_BINARY" -> R.string.performer_gender_non_binary
    else -> null
}

@StringRes
internal fun performerMissingLabel(value: String?): Int? = when (value) {
    "image" -> R.string.performer_missing_image
    "birthdate" -> R.string.performer_birthdate
    "country" -> R.string.performer_missing_country
    "gender" -> R.string.performer_filter_gender
    "rating" -> R.string.performer_sort_rating
    "tags" -> R.string.performer_tags
    "details" -> R.string.performer_bio
    "stash_id" -> R.string.performer_stash_ids
    else -> null
}
