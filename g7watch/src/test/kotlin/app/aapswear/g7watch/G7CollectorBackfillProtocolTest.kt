package app.aapswear.g7watch

import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.G7Reading
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SensorState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class G7CollectorBackfillProtocolTest {
    @Test
    fun `request uses verified G7 opcode and little endian clocks`() {
        assertArrayEquals(
            byteArrayOf(0x59, 0x04, 0x03, 0x02, 0x01, 0x04, 0x04, 0x02, 0x01),
            G7CollectorBackfillProtocol.request(0x01020304, 0x01020404),
        )
    }

    @Test
    fun `request cannot exceed verified 24 hour G7 window`() {
        runCatching { G7CollectorBackfillProtocol.request(1, 1 + G7CollectorBackfillProtocol.MAX_WINDOW_SECONDS + 1) }
            .exceptionOrNull()
            .let { assertEquals(IllegalArgumentException::class.java, it?.javaClass) }
    }

    @Test
    fun `up to date stream does not request history`() {
        assertNull(G7CollectorBackfillProtocol.requestedStart(9_700, 10_000))
    }

    @Test
    fun `first connection requests all sensor history from first cadence slot`() {
        assertEquals(300L, G7CollectorBackfillProtocol.requestedStart(null, 4L * 60L * 60L))
        assertEquals(3_600L, G7CollectorBackfillProtocol.requestedStart(null, 25L * 60L * 60L))
    }

    @Test
    fun `history request ends at the slot before the already received live value`() {
        assertEquals(9_700L, G7CollectorBackfillProtocol.requestedEnd(10_000))
        assertArrayEquals(
            byteArrayOf(0x59, 0xE4.toByte(), 0x25, 0, 0, 0xE4.toByte(), 0x25, 0, 0),
            G7CollectorBackfillProtocol.request(9_700, 9_700),
        )
    }

    @Test
    fun `record is historical and timestamps use sensor clock`() {
        val packet =
            ByteBuffer
                .allocate(9)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(7_200)
                .putShort(145)
                .put(0x06)
                .put(0)
                .put(10)
                .array()
        val live =
            G7Reading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = 99,
                glucoseMgDl = 150.0,
                sensorTimestampEpochMs = 11_000_000,
                receivedAtEpochMs = 11_000_000,
                sensorClockSeconds = 10_000,
                sensorStartEpochMs = 1_000_000,
                sensorState = G7SensorState.ACTIVE,
            )
        val parsed =
            G7CollectorBackfillProtocol.parseRecord(
                packet,
                G7Sensor("sensor", "session"),
                live,
                12_000_000,
            )
        assertEquals(CgmReadingOrigin.BACKFILL, parsed.origin)
        assertEquals(8_200_000L, parsed.sensorTimestampEpochMs)
        assertEquals(12_000_000L, parsed.receivedAtEpochMs)
        assertEquals(145.0, parsed.glucoseMgDl, 0.0)
    }
}
