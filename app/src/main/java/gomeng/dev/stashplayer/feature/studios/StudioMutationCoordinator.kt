package gomeng.dev.stashplayer.feature.studios

import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioMutableFields
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudioMutationStatus(
    val fields: StashStudioMutableFields,
    val busy: Boolean = false,
    val error: Boolean = false,
)

class StudioMutationCoordinator(
    private val profile: StashServerProfile,
    private val scope: CoroutineScope,
    private val updateFavorite: suspend (StashServerProfile, String, Boolean) -> StashStudioMutableFields = { server, id, value ->
        StashGraphQlClient(server).updateStudioFavorite(id, value)
    },
    private val updateRating: suspend (StashServerProfile, String, Int?) -> StashStudioMutableFields = { server, id, value ->
        StashGraphQlClient(server).updateStudioRating(id, value)
    },
    private val readFields: suspend (StashServerProfile, String) -> StashStudioMutableFields? = { server, id ->
        StashGraphQlClient(server).findStudioDetail(id)?.mutableFields()
    },
) {
    private val mutableStatuses = MutableStateFlow<Map<String, StudioMutationStatus>>(emptyMap())
    val statuses = mutableStatuses.asStateFlow()
    var revision: Long = 0
        private set

    fun reconcile(cards: List<StashStudioCard>, requestRevision: Long) {
        if (requestRevision != revision) return
        val updated = mutableStatuses.value.toMutableMap()
        cards.forEach { card ->
            val old = updated[card.id]
            if (old != null && !old.busy) updated[card.id] = old.copy(fields = card.mutableFields(), error = false)
        }
        mutableStatuses.value = updated
    }

    fun toggleFavorite(card: StashStudioCard) = update(card) { current ->
        updateFavorite(profile, card.id, !current.favorite)
    }

    fun setRating(card: StashStudioCard, rating100: Int?) = update(card) { _ ->
        updateRating(profile, card.id, rating100)
    }

    private fun update(card: StashStudioCard, request: suspend (StashStudioMutableFields) -> StashStudioMutableFields) {
        val current = mutableStatuses.value[card.id] ?: StudioMutationStatus(card.mutableFields())
        if (current.busy) return
        revision++
        setStatus(card.id, current.copy(busy = true, error = false))
        scope.launch {
            try {
                val result = request(current.fields)
                require(result.id == card.id)
                setStatus(card.id, StudioMutationStatus(result))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val authoritative = try {
                    readFields(profile, card.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                setStatus(card.id, StudioMutationStatus(authoritative ?: current.fields, error = true))
            } finally {
                revision++
            }
        }
    }

    private fun setStatus(id: String, status: StudioMutationStatus) {
        val next = mutableStatuses.value.toMutableMap()
        next[id] = status
        while (next.size > 100) {
            val removable = next.entries.firstOrNull { !it.value.busy && it.key != id } ?: break
            next.remove(removable.key)
        }
        mutableStatuses.value = next
    }
}

internal fun StashStudioCard.mutableFields() = StashStudioMutableFields(id, favorite, rating100)
