package gomeng.dev.stashplayer.feature.studios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashServerProfile

internal data class StudioScenesRequest(
    val profile: StashServerProfile,
    val studioId: String,
    val query: String,
    val sort: String,
    val direction: StashSortDirection,
    val filter: StashVideoFilterState,
    val page: Int,
    val reload: Int,
    val includeChildren: Boolean,
)

internal class StudioScenesViewModel : ViewModel() {
    var includeChildren by mutableStateOf(false)
    var input by mutableStateOf("")
    var query by mutableStateOf("")
    var sort by mutableStateOf("date")
    var direction by mutableStateOf(StashSortDirection.Desc)
    var filter by mutableStateOf(StashVideoFilterState())
    var page by mutableIntStateOf(1)
    var reload by mutableIntStateOf(0)
    var requestSerial by mutableIntStateOf(0)
    var scenes by mutableStateOf(emptyList<SceneCardModel>())
    var total by mutableIntStateOf(0)
    var hasMore by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf(false)
    var activeProfile: StashServerProfile? = null
    var activeStudioId: String? = null
    var lastSuccess: StudioScenesRequest? = null
}
