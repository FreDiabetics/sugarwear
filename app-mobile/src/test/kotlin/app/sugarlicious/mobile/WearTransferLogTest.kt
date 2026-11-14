package app.sugarlicious.mobile

import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WearTransferLogTest {
    @Test fun `slow persistence does not delay transport and queue remains bounded`() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val writing = CompletableDeferred<Unit>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val persisted = mutableListOf<WearTransferRecord>()
            val writer =
                WearTransferWriter(scope, capacity = 2) {
                    writing.complete(Unit)
                    gate.await()
                    persisted += it
                }
            try {
                val result = logWearTransfer("state", "DataClient", "/state", "network", 2, record = { writer.enqueue(it) }) { 8 }
                assertEquals(8, result)
                assertTrue(writing.isCompleted)
                assertFalse(gate.isCompleted)
                assertTrue(persisted.isEmpty())
                val extra = WearTransferRecord("EXTRA", "queued", emptyMap(), 123L)
                assertTrue(writer.enqueue(extra))
                assertFalse(writer.enqueue(extra))
                gate.complete(Unit)
                yield()
                assertEquals(listOf("STARTED", "DROPPED", "API_ACCEPTED", "EXTRA"), persisted.map { it.outcome })
                assertEquals(1L, persisted[1].metadata["droppedCount"])
                assertEquals(123L, persisted.last().occurredAtEpochMs)
                assertEquals(persisted[0].metadata["transferId"], persisted[2].metadata["transferId"])
            } finally {
                scope.cancel()
            }
        }

    @Test fun `API completion is not delivery confirmation`() =
        runBlocking {
            val entries = mutableListOf<WearTransferRecord>()
            val value = logWearTransfer("state", "DataClient", "/state", "Wear-Netzwerk", 42, record = { entries += it }) { 7 }
            assertEquals(7, value)
            assertEquals(listOf("STARTED", "API_ACCEPTED"), entries.map { it.outcome })
            assertTrue(entries.last().message.contains("nicht bestätigt"))
            assertEquals(42, entries.last().metadata["bytes"])
        }

    @Test fun `failure remains failure and original exception is propagated`() =
        runBlocking {
            val entries = mutableListOf<WearTransferRecord>()
            val failure = IllegalStateException("private payload must never be logged")
            try {
                logWearTransfer("state", "MessageClient", "/state", "node", 4, record = { entries += it }) { throw failure }
                fail("must throw")
            } catch (actual: IllegalStateException) {
                assertSame(failure, actual)
            }
            assertEquals("FAILED", entries.last().outcome)
            assertFalse(entries.last().toString().contains("private payload"))
        }

    @Test fun `logging failure never changes successful transport result`() =
        runBlocking {
            assertEquals(8, logWearTransfer("state", "DataClient", "/state", "network", 2, record = { error("storage") }) { 8 })
        }

    @Test fun `cancellation is recorded and rethrown without retry`() =
        runBlocking {
            val entries = mutableListOf<WearTransferRecord>()
            val cancellation = CancellationException("timeout")
            try {
                logWearTransfer("state", "MessageClient", "/state", "node", 4, record = { entries += it }) { throw cancellation }
                fail("must cancel")
            } catch (actual: CancellationException) {
                assertSame(cancellation, actual)
            }
            assertEquals(listOf("STARTED", "CANCELLED"), entries.map { it.outcome })
        }

    @Test fun `transfer view keeps only newest bounded mobile transfer events`() {
        val events = (0..400).map { index -> DiagnosticEvent("$index", index.toLong(), "MOBILE", "WEAR_TRANSFER", "code", DiagnosticSeverity.INFO, "event") }
        val unrelated = DiagnosticEvent("other", 1000, "WATCH", "SYNC", "code", DiagnosticSeverity.INFO, "other")
        val selected = wearTransferEvents(events + unrelated)
        assertEquals(300, selected.size)
        assertEquals("400", selected.first().id)
        assertEquals("101", selected.last().id)
    }
}
