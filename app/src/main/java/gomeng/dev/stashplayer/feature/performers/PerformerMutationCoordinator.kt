package gomeng.dev.stashplayer.feature.performers

import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PerformerMutationStatus(
    val fields: StashPerformerMutableFields,
    val busy: Boolean = false,
    val error: Boolean = false,
)

class PerformerMutationCoordinator(
    private val profile: StashServerProfile,
    private val scope: CoroutineScope,
    private val updateFavorite: suspend (StashServerProfile, String, Boolean) -> StashPerformerMutableFields = { server, id, value ->
        StashGraphQlClient(server).updatePerformerFavorite(id, value)
    },
    private val updateRating: suspend (StashServerProfile, String, Int?) -> StashPerformerMutableFields = { server, id, value ->
        StashGraphQlClient(server).updatePerformerRating(id, value)
    },
    private val readFields: suspend (StashServerProfile, String) -> StashPerformerMutableFields? = { server, id ->
        StashGraphQlClient(server).findPerformerDetail(id)?.card?.mutableFields()
    },
) {
    private val mutableStatuses = MutableStateFlow<Map<String, PerformerMutationStatus>>(emptyMap())
    val statuses = mutableStatuses.asStateFlow()
    var revision: Long = 0
        private set

    fun reconcile(cards: List<StashPerformerCard>, requestRevision: Long) {
        if (requestRevision != revision) return
        val updated = mutableStatuses.value.toMutableMap()
        cards.forEach { card ->
            val old = updated[card.id]
            if (old != null && !old.busy) updated[card.id] = old.copy(fields = card.mutableFields(), error = false)
        }
        mutableStatuses.value = updated
    }

    fun toggleFavorite(card: StashPerformerCard) = update(card) { current ->
        updateFavorite(profile, card.id, !current.favorite)
    }

    fun setRating(card: StashPerformerCard, rating100: Int?) = update(card) { _ ->
        updateRating(profile, card.id, rating100)
    }

    private fun update(card: StashPerformerCard, request: suspend (StashPerformerMutableFields) -> StashPerformerMutableFields) {
        val current = mutableStatuses.value[card.id] ?: PerformerMutationStatus(card.mutableFields())
        if (current.busy) return
        revision++
        setStatus(card.id, current.copy(busy = true, error = false))
        scope.launch {
            try {
                val result = request(current.fields)
                require(result.id == card.id)
                setStatus(card.id, PerformerMutationStatus(result))
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
                setStatus(card.id, PerformerMutationStatus(authoritative ?: current.fields, error = true))
            } finally {
                revision++
            }
        }
    }

    private fun setStatus(id: String, status: PerformerMutationStatus) {
        val next = mutableStatuses.value.toMutableMap()
        next[id] = status
        while (next.size > 100) {
            val removable = next.entries.firstOrNull { !it.value.busy && it.key != id } ?: break
            next.remove(removable.key)
        }
        mutableStatuses.value = next
    }
}

internal fun StashPerformerCard.mutableFields() = StashPerformerMutableFields(id, favorite, rating100)
