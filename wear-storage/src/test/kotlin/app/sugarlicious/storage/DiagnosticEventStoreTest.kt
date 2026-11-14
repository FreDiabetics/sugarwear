package app.sugarlicious.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppClock
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticEventStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = AppClock { 1_800_000L }
    private val store = DiagnosticEventStore(context, clock)

    @Before
    fun clear() = runBlocking { store.clear() }

    @Test
    fun `records structured event and removes secret metadata`() =
        runBlocking {
            store.record(
                origin = "MOBILE",
                module = "SOURCE",
                code = "SRC-AAPS-001",
                severity = DiagnosticSeverity.WARNING,
                message = "Payload fehlt",
                metadata = mapOf("predictionCount" to 3, "sharedKey" to "do-not-store"),
                occurredAtEpochMs = clock.nowEpochMs(),
            )

            val event = store.events.first().single()
            assertEquals("SRC-AAPS-001", event.code)
            assertEquals("3", event.metadata["predictionCount"])
            assertFalse(event.metadata.containsKey("sharedKey"))
        }

    @Test
    fun `retains only bounded recent events`() =
        runBlocking {
            val now = clock.nowEpochMs() - 2_000L
            store.append(
                List(1_020) { index ->
                    DiagnosticEvent(
                        id = "event-$index",
                        occurredAtEpochMs = now + index,
                        origin = "WATCH",
                        module = "RENDER",
                        code = "RENDER-$index",
                        severity = DiagnosticSeverity.INFO,
                        message = "event",
                    )
                },
                nowEpochMs = now + 1_020,
            )

            val events = store.events.first()
            assertEquals(1_000, events.size)
            assertTrue(events.none { it.code == "RENDER-0" })
        }

    @Test
    fun `sanitizes diagnostics received from another app process`() =
        runBlocking {
            store.append(
                listOf(
                    DiagnosticEvent(
                        id = "remote",
                        occurredAtEpochMs = clock.nowEpochMs(),
                        origin = "WATCH\nINJECTED",
                        module = "G7",
                        code = "G7-DATA-200",
                        severity = DiagnosticSeverity.INFO,
                        message = "ok\nsecond line",
                        metadata = mapOf("sharedKey" to "do-not-store", "sequence" to "14"),
                    ),
                ),
            )

            val event = store.events.first().single()
            assertEquals("WATCH INJECTED", event.origin)
            assertEquals("ok second line", event.message)
            assertFalse(event.metadata.containsKey("sharedKey"))
            assertEquals("14", event.metadata["sequence"])
        }

    @Test
    fun `separate store instances observe appended events and clearing invalidates retained cache`() =
        runBlocking {
            val other = DiagnosticEventStore(context, clock)
            store.record(origin = "WATCH", module = "G7", code = "FIRST", message = "first")
            other.record(origin = "WATCH", module = "G7", code = "SECOND", message = "second", metadata = mapOf("password" to "never-retain", "count" to 2))
            assertEquals(setOf("FIRST", "SECOND"), store.snapshot().map { it.code }.toSet())
            assertFalse(store.snapshot().any { "password" in it.metadata })
            other.clear()
            store.record(origin = "WATCH", module = "G7", code = "THIRD", message = "third")
            assertEquals(listOf("THIRD"), other.snapshot().map { it.code })
        }
}
