package app.sugarlicious.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CanonicalDataChangeFanOutTest {
    @Test
    fun `slow widget invalidation cannot delay immediate wear push`() =
        runBlocking {
            val widgetStarted = CompletableDeferred<Unit>()
            val releaseWidget = CompletableDeferred<Unit>()
            val wearCompleted = CompletableDeferred<Unit>()

            val dispatch =
                async {
                    CanonicalDataChangeFanOut.dispatch(
                        updateWidgets = {
                            widgetStarted.complete(Unit)
                            releaseWidget.await()
                        },
                        pushWear = { wearCompleted.complete(Unit) },
                    )
                }

            widgetStarted.await()
            assertTrue(wearCompleted.isCompleted)
            releaseWidget.complete(Unit)
            dispatch.await()
        }

    @Test
    fun `one canonical commit invokes every consumer exactly once`() =
        runBlocking {
            val widgets = AtomicInteger()
            val wear = AtomicInteger()

            CanonicalDataChangeFanOut.dispatch(
                updateWidgets = { widgets.incrementAndGet() },
                pushWear = { wear.incrementAndGet() },
            )

            assertEquals(1, widgets.get())
            assertEquals(1, wear.get())
        }
}
