package gomeng.dev.stashplayer.feature.performers

import gomeng.dev.stashplayer.core.network.StashPerformerCard
import gomeng.dev.stashplayer.core.network.StashPerformerMutableFields
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformerMutationCoordinatorTest {
    private val profile = StashServerProfile(baseUrl = "http://localhost")
    private val card = StashPerformerCard("1", "Actor", emptyList(), null, null, null, null, null, false, null, 0)

    @Test fun pendingWriteRejectsDuplicateAndOldReadCannotOverwriteIt() = runBlocking {
        val result = CompletableDeferred<StashPerformerMutableFields>()
        var calls = 0
        val coordinator = PerformerMutationCoordinator(profile, this, updateFavorite = { _, _, _ ->
            calls++
            result.await()
        })
        val oldRevision = coordinator.revision
        coordinator.toggleFavorite(card)
        coordinator.toggleFavorite(card)
        yield()
        assertEquals(1, calls)
        assertTrue(coordinator.statuses.value.getValue("1").busy)
        result.complete(StashPerformerMutableFields("1", true, 80))
        yield()
        coordinator.reconcile(listOf(card), oldRevision)
        assertTrue(coordinator.statuses.value.getValue("1").fields.favorite)
        assertEquals(80, coordinator.statuses.value.getValue("1").fields.rating100)
    }

    @Test fun failedWriteReadsAuthoritativeValueAndAllowsRetry() = runBlocking {
        var calls = 0
        val coordinator = PerformerMutationCoordinator(profile, this,
            updateFavorite = { _, _, value ->
                calls++
                if (calls == 1) error("timeout")
                StashPerformerMutableFields("1", value, 20)
            },
            readFields = { _, _ -> StashPerformerMutableFields("1", true, 20) },
        )
        coordinator.toggleFavorite(card)
        yield()
        assertTrue(coordinator.statuses.value.getValue("1").error)
        assertTrue(coordinator.statuses.value.getValue("1").fields.favorite)
        coordinator.toggleFavorite(card)
        yield()
        assertFalse(coordinator.statuses.value.getValue("1").error)
        assertFalse(coordinator.statuses.value.getValue("1").fields.favorite)
        assertEquals(2, calls)
    }
}
