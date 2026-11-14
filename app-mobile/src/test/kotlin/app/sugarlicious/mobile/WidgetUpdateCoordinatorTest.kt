package app.sugarlicious.mobile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class WidgetUpdateCoordinatorTest {
    @Test
    fun `failed publication is reported to flush and a later update can recover`() =
        runBlocking {
            val expected = IllegalStateException("publication failed")
            val rendered = mutableListOf<Int>()
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Int>(CoroutineScope(coroutineContext), { _, value ->
                    if (value == 1) throw expected
                    rendered += value
                })
            coordinator.submit("widget", 1, 1)
            val result = runCatching { kotlinx.coroutines.withTimeout(1000) { coordinator.flush("widget", 1) } }
            assertTrue("failed publication must not be acknowledged as successful: $result", result.exceptionOrNull() is IllegalStateException)
            assertEquals("publication failed", result.exceptionOrNull()?.message)
            coordinator.submit("widget", 2, 2)
            coordinator.flush("widget", 2)
            assertEquals(listOf(2), rendered)
        }

    @Test
    fun `cancelled composition releases lane and renders newest pending update`() =
        runBlocking {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val release = CompletableDeferred<Unit>()
            val rendered = mutableListOf<Long>()
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Long>(scope, { _, revision ->
                    if (revision == 1L) {
                        release.await()
                        throw kotlinx.coroutines.CancellationException("composition cancelled")
                    }
                    rendered += revision
                })
            coordinator.submit("same widget", 1, 1)
            coordinator.submit("same widget", 2, 2)
            release.complete(Unit)
            val completed =
                kotlinx.coroutines.withTimeoutOrNull(1000) {
                    coordinator.flush("same widget", 2)
                    true
                }
            scope.cancel()
            assertEquals(true, completed)
            assertEquals(listOf(2L), rendered)
        }

    @Test
    fun `family and settings publications use one serialized completed render path`() {
        val source = java.io.File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()
        assertFalse(source.contains(".updateAll(context)"))
        assertTrue(source.contains("override fun onCompositionError("))
        assertTrue(source.contains("WidgetRenderTransactions.run(appWidgetId)"))
        val settings = java.io.File("src/main/kotlin/app/sugarlicious/mobile/WidgetInstanceConfiguration.kt").readText()
        assertTrue(settings.contains("onSave = model::save"))
        val repository = java.io.File("src/main/kotlin/app/sugarlicious/mobile/WidgetConfigurationViewModel.kt").readText()
        assertTrue(repository.contains("WidgetRenderTransactions.run(widgetId)"))
        assertTrue(repository.contains("commitWidgetSettings("))
    }

    @Test
    fun `same widget serializes preparation through publication even across update sources`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val first =
                async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    WidgetRenderTransactions.run(77) {
                        events += "settings prepare"
                        started.complete(Unit)
                        release.await()
                        events += "settings publish"
                    }
                }
            started.await()
            val second =
                async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    WidgetRenderTransactions.run(77) { events += "data publish" }
                }
            assertEquals(listOf("settings prepare"), events)
            release.complete(Unit)
            first.await()
            second.await()
            assertEquals(listOf("settings prepare", "settings publish", "data publish"), events)
        }

    @Test
    fun `failed composition releases the same widget transaction`() =
        runBlocking {
            val failure = runCatching { WidgetRenderTransactions.run(78) { error("render failed") } }
            assertTrue(failure.isFailure)
            assertEquals(42, WidgetRenderTransactions.run(78) { 42 })
        }

    @Test
    fun `durable dispatcher completes update after activity scope is cancelled`() =
        runBlocking {
            val activityScope = CoroutineScope(coroutineContext + SupervisorJob())
            val processScope = CoroutineScope(coroutineContext + SupervisorJob())
            val rendered = CompletableDeferred<Int>()
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Int>(processScope, { _, value ->
                    delay(1)
                    rendered.complete(value)
                })

            coordinator.submit("widget", 1, 42)
            activityScope.cancel()

            assertEquals(42, rendered.await())
            processScope.cancel()
        }

    @Test
    fun `active render is followed only by newest pending revision`() =
        runBlocking {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val rendered = Collections.synchronizedList(mutableListOf<Long>())
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Long>(
                    scope = CoroutineScope(coroutineContext),
                    render = { _, revision ->
                        rendered += revision
                        if (revision == 1L) {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                        }
                    },
                )

            coordinator.submit("widget", 1L, 1L)
            firstStarted.await()
            coordinator.submit("widget", 2L, 2L)
            coordinator.submit("widget", 3L, 3L)
            releaseFirst.complete(Unit)
            coordinator.flush("widget", 3L)

            assertEquals(listOf(1L, 3L), rendered)
        }

    @Test
    fun `flush waits until requested revision is rendered`() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Long>(
                    scope = CoroutineScope(coroutineContext),
                    render = { _, _ -> release.await() },
                )

            coordinator.submit("widget", 7L, 7L)
            val flush = async { coordinator.flush("widget", 7L) }
            assertFalse(flush.isCompleted)
            release.complete(Unit)
            flush.await()
            assertTrue(flush.isCompleted)
        }

    @Test
    fun `different widget families render independently`() =
        runBlocking {
            val graphStarted = CompletableDeferred<Unit>()
            val releaseGraph = CompletableDeferred<Unit>()
            val glucoseFinished = CompletableDeferred<Unit>()
            val coordinator =
                LatestWinsSurfaceCoordinator<String, Long>(
                    scope = CoroutineScope(coroutineContext),
                    render = { key, _ ->
                        if (key == "graph") {
                            graphStarted.complete(Unit)
                            releaseGraph.await()
                        } else {
                            glucoseFinished.complete(Unit)
                        }
                    },
                )

            coordinator.submit("graph", 1L, 1L)
            graphStarted.await()
            coordinator.submit("glucose", 1L, 1L)

            assertTrue(glucoseFinished.isCompleted)
            releaseGraph.complete(Unit)
            Unit
        }
}
