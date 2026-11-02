package app.aapswear.g7watch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class G7SensorMetadataTest {
    @Test fun `metadata reply yields the twelve digit identity without changing reading identity`() {
        assertEquals("123456789012", g7SensorSerialFromMetadata(reply(123456789012)))
    }

    @Test fun `rejects truncated replies wrong opcode and error status`() {
        val packet = reply(123456789012)
        assertNull(g7SensorSerialFromMetadata(packet.copyOf(19)))
        assertNull(g7SensorSerialFromMetadata(packet.copyOf(21)))
        assertNull(g7SensorSerialFromMetadata(packet.copyOf().apply { this[0] = 0x4e }))
        assertNull(g7SensorSerialFromMetadata(packet.copyOf().apply { this[1] = 1 }))
    }

    @Test fun `rejects invalid unsigned values rather than inventing a padded serial`() {
        listOf(0L, 99999999999L, 1000000000000L, -1L).forEach { assertNull(g7SensorSerialFromMetadata(reply(it))) }
    }

    @Test fun `optional metadata timeout does not fail the successful reading cycle`() =
        runBlocking {
            var sent = false
            assertNull(
                readG7SensorMetadata({ sent = true }, {
                    delay(100)
                    null
                }, 10),
            )
            assertTrue(sent)
        }

    @Test fun `optional metadata read does not swallow parent cancellation`() =
        runBlocking {
            var cancelled = false
            try {
                readG7SensorMetadata({ throw CancellationException("paused") }, { null }, 10)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }

    @Test fun `unrelated notification cannot become a sensor identity`() =
        runBlocking {
            val packets = ArrayDeque(listOf(byteArrayOf(0x4e, 0), reply(123456789012)))
            assertEquals("123456789012", readG7SensorMetadata({}, { packets.removeFirst() }))
        }

    @Test fun `optional metadata failures do not discard the already published live reading`() =
        runBlocking {
            assertNull(readG7SensorMetadata({ throw G7BleException("read", "unavailable", true) }, { null }))
            assertNull(readG7SensorMetadata({}, { throw kotlinx.coroutines.channels.ClosedReceiveChannelException("closed") }))
        }

    @Test fun `wrapped outer timeout is not mistaken for an optional read failure`() =
        runBlocking {
            var cancelled = false
            try {
                kotlinx.coroutines.withTimeout(10) {
                    readG7SensorMetadata({
                        try {
                            delay(100)
                        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                            throw G7BleException("write", "timed out", true, timeout)
                        }
                    }, { null })
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }

    private fun reply(serial: Long): ByteArray =
        ByteBuffer
            .allocate(20)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(0, 0x32)
                put(1, 0)
                put(2, 1)
                put(3, 0x41)
                putLong(12, serial)
            }.array()
}
