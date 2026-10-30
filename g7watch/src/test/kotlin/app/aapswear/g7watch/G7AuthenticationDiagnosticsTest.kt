package app.aapswear.g7watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class G7AuthenticationDiagnosticsTest {
    @Test
    fun `candidate mismatch remains recoverable without accepting the peer`() {
        val error = authenticationProcessingFailure("G7-AUTH-204", jamorham.keks.AuthenticationMismatchException())
        assertEquals("G7-AUTH-MISMATCH", error.errorCode)
        assertTrue(error.recoverable)
        assertFalse(error.message.contains("Mismatch - wait"))
    }

    @Test
    fun `response diagnostics preserve error policy while excluding secret-bearing exception text`() {
        val cause = SecurityException("pairing=123456 payload=deadbeef key=top-secret")
        cause.stackTrace = arrayOf(StackTraceElement("com.example.Plugin", "receivedResponse", "secret-code-123456.kt", 42))
        val error = authenticationProcessingFailure("G7-AUTH-204", cause)
        assertEquals("G7-AUTH-204", error.errorCode)
        assertFalse(error.recoverable)
        assertSame(cause, error.cause)
        assertTrue(error.message.contains("stage=AUTH_RESPONSE"))
        assertTrue(error.message.contains("cause=java.lang.SecurityException"))
        assertTrue(error.message.contains("com.example.Plugin#receivedResponse:42"))
        for (secret in listOf("123456", "deadbeef", "top-secret", "secret-code", "Sensor hat")) assertFalse(error.message.contains(secret))
    }

    @Test
    fun `extra data stage and invalid frame identifiers stay safe without claiming key rejection`() {
        val cause = IllegalArgumentException("private key content")
        cause.stackTrace = arrayOf(StackTraceElement("payload deadbeef", "123456", "secret", -1))
        val error = authenticationProcessingFailure("G7-AUTH-202", cause)
        assertTrue(error.message.contains("stage=EXTRA_DATA"))
        assertTrue(error.message.contains("cause=java.lang.IllegalArgumentException"))
        assertTrue(error.message.contains("unknown#unknown:0"))
        assertFalse(error.message.contains("private key content"))
        assertFalse(error.message.contains("abgelehnt"))
    }
}
