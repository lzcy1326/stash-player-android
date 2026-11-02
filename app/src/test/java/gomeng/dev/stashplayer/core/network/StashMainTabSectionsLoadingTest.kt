package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashMainTabSection
import gomeng.dev.stashplayer.core.model.StashMainTabSectionSpec
import gomeng.dev.stashplayer.core.model.StashSortDirection
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class StashMainTabSectionsLoadingTest {
    @Test
    fun loadsAllSectionsConcurrentlyAndPreservesOrder() = runBlocking {
        val specs = listOf("first", "second", "third").map { id ->
            StashMainTabSectionSpec(
                id = id,
                title = id,
                sort = "updated_at",
                direction = StashSortDirection.Desc,
            )
        }
        val startedCount = AtomicInteger()
        val allSectionsStarted = CompletableDeferred<Unit>()

        val sections = withTimeout(2_000) {
            loadStashMainTabSectionsConcurrently(specs) { spec ->
                if (startedCount.incrementAndGet() == specs.size) {
                    allSectionsStarted.complete(Unit)
                }
                allSectionsStarted.await()
                StashMainTabSection(
                    spec = spec,
                    scenes = listOf(
                        SceneCardModel(
                            id = spec.id,
                            title = spec.id,
                            durationText = "",
                            studio = "",
                            progress = 0f,
                        ),
                    ),
                )
            }
        }

        assertEquals(specs.size, startedCount.get())
        assertEquals(listOf("first", "second", "third"), sections.map { it.spec.id })
    }
}
