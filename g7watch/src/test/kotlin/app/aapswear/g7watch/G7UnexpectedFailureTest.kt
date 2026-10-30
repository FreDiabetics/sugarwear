package app.aapswear.g7watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class G7UnexpectedFailureTest {
    @Test fun `unexpected failure metadata is stable and excludes exception message`() {
        val first = unexpectedFailureMetadata("COLLECT", IllegalStateException("sensor-secret-one"))
        val second = unexpectedFailureMetadata("COLLECT", IllegalStateException("sensor-secret-two"))

        assertEquals("COLLECT", first["failurePhase"])
        assertEquals("java.lang.IllegalStateException", first["exceptionType"])
        assertEquals(first["failureFingerprint"], second["failureFingerprint"])
        assertFalse(first.values.any { it.toString().contains("sensor-secret") })
    }
}
