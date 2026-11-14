package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.crypto.SHA256
import app.sugarlicious.sensor.auth.engine.util.Log
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class CryptoExecutionContractTest {
    @Test fun `SHA class lock protects shared working arrays under concurrent calls`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                (0 until 64).map { index ->
                    Callable {
                        val input = ByteArray(index * 37) { (it + index).toByte() }
                        MessageDigest.getInstance("SHA-256").digest(input) to SHA256.hash(input)
                    }
                }
            for (future in executor.invokeAll(tasks)) {
                val (expected, actual) = future.get()
                assertContentEquals(expected, actual)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `crypto diagnostics never include exception text or legacy packet messages`() {
        val logger = Logger.getLogger("app.aapswear.g7.crypto")
        val records = mutableListOf<LogRecord>()
        val handler =
            object : Handler() {
                override fun publish(record: LogRecord) {
                    records += record
                }

                override fun flush() = Unit

                override fun close() = Unit
            }
        val oldLevel = logger.level
        val oldParent = logger.useParentHandlers
        try {
            logger.level = Level.ALL
            logger.useParentHandlers = false
            handler.level = Level.ALL
            logger.addHandler(handler)
            Log.cryptoFailure("TEST_IMPORT", IllegalArgumentException("SYNTHETIC_SECRET_MARKER"))
            Log.d("TEST", "SYNTHETIC_SECRET_MARKER")
            Log.l("SYNTHETIC_SECRET_MARKER")
            assertEquals(listOf("TEST_IMPORT:IllegalArgumentException"), records.map { it.message })
            assertFalse(records.any { it.message.contains("SYNTHETIC_SECRET_MARKER") || it.thrown != null })
        } finally {
            logger.removeHandler(handler)
            logger.level = oldLevel
            logger.useParentHandlers = oldParent
        }
    }

    @Test fun `crypto error reporting preserves coroutine cancellation identity`() {
        val cancellation = CancellationException("synthetic cancellation")
        assertSame(cancellation, assertFailsWith<CancellationException> { Log.cryptoFailure("TEST", cancellation) })
    }

    @Test fun `signing retains the exact original provider SPI superclass`() {
        assertEquals("org.bouncycastle.jcajce.provider.asymmetric.ec.SignatureSpi\$ecDSA256", DSAChallenger::class.java.superclass.name)
    }
}
