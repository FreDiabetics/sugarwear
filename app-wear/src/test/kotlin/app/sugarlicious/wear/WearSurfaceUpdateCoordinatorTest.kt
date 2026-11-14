package app.sugarlicious.wear

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WearSurfaceUpdateCoordinatorTest {
    @Test
    fun `slow complication invalidation does not delay app or tile invalidation`() =
        runBlocking {
            val complicationStarted = CompletableDeferred<Unit>()
            val releaseComplication = CompletableDeferred<Unit>()
            val appFinished = CompletableDeferred<Unit>()
            val tileFinished = CompletableDeferred<Unit>()

            val dispatch =
                async {
                    WearSurfaceUpdateCoordinator.dispatch(
                        updateApp = { appFinished.complete(Unit) },
                        updateTiles = { tileFinished.complete(Unit) },
                        updateComplications = {
                            complicationStarted.complete(Unit)
                            releaseComplication.await()
                        },
                    )
                }

            complicationStarted.await()
            assertTrue(appFinished.isCompleted)
            assertTrue(tileFinished.isCompleted)
            assertFalse(dispatch.isCompleted)
            releaseComplication.complete(Unit)
            dispatch.await()
            Unit
        }

    @Test
    fun `one failing surface does not suppress remaining invalidations`() =
        runBlocking {
            val completed = AtomicInteger()

            WearSurfaceUpdateCoordinator.dispatch(
                updateApp = { error("app failed") },
                updateTiles = { completed.incrementAndGet() },
                updateComplications = { completed.incrementAndGet() },
            )

            assertEquals(2, completed.get())
        }
}
