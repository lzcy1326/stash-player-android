package gomeng.dev.stashplayer.feature.studios

import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashStudioMutableFields
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMutationCoordinatorTest {
    private val profile = StashServerProfile(baseUrl = "http://localhost")
    private val card = StashStudioCard("1", "Studio")

    @Test fun pendingWriteRejectsDuplicateAndOldReadCannotOverwriteIt() = runBlocking {
        val result = CompletableDeferred<StashStudioMutableFields>()
        var calls = 0
        val coordinator = StudioMutationCoordinator(profile, this, updateFavorite = { _, _, _ ->
            calls++
            result.await()
        })
        val oldRevision = coordinator.revision
        coordinator.toggleFavorite(card)
        coordinator.toggleFavorite(card)
        yield()
        assertEquals(1, calls)
        assertTrue(coordinator.statuses.value.getValue("1").busy)
        result.complete(StashStudioMutableFields("1", true, 80))
        yield()
        coordinator.reconcile(listOf(card), oldRevision)
        assertTrue(coordinator.statuses.value.getValue("1").fields.favorite)
        assertEquals(80, coordinator.statuses.value.getValue("1").fields.rating100)
    }

    @Test fun failedWriteReadsAuthoritativeValueAndAllowsRetry() = runBlocking {
        var calls = 0
        val coordinator = StudioMutationCoordinator(profile, this,
            updateFavorite = { _, _, value ->
                calls++
                if (calls == 1) error("timeout")
                StashStudioMutableFields("1", value, 20)
            },
            readFields = { _, _ -> StashStudioMutableFields("1", true, 20) },
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
