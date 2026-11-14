package app.sugarlicious.mobile

import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticActivityTest {
    @Test
    fun `export includes stable code and sanitized metadata only`() {
        val output =
            formatDiagnosticEvents(
                listOf(
                    DiagnosticEvent(
                        id = "id",
                        occurredAtEpochMs = 1_000L,
                        origin = "WATCH",
                        module = "PREDICTION",
                        code = "PRED-CACHE-203",
                        severity = DiagnosticSeverity.WARNING,
                        message = "Cached predictions retained",
                        metadata = mapOf("displayPredictions" to "4"),
                    ),
                ),
            )

        assertTrue(output.contains("PRED-CACHE-203"))
        assertTrue(output.contains("displayPredictions=4"))
        assertFalse(output.contains("sharedKey"))
    }
}
