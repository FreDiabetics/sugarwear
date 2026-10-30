package app.aapswear.g7watch

import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.G7Reading
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SensorState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Dexcom G7 history is requested only by the Watch Collector BLE session. */
internal object G7CollectorBackfillProtocol {
    const val MAX_WINDOW_SECONDS = 24L * 60L * 60L
    const val MAX_WINDOW_MS = MAX_WINDOW_SECONDS * 1_000L
    const val EXPECTED_INTERVAL_SECONDS = 5L * 60L
    const val REQUEST_OPCODE: Byte = 0x59

    fun request(
        startSensorClock: Long,
        endSensorClock: Long,
    ): ByteArray {
        require(startSensorClock >= 1L && startSensorClock <= endSensorClock)
        require(endSensorClock - startSensorClock <= MAX_WINDOW_SECONDS)
        return ByteBuffer
            .allocate(9)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(REQUEST_OPCODE)
            .putInt(startSensorClock.toInt())
            .putInt(endSensorClock.toInt())
            .array()
    }

    fun requestedStart(
        lastStoredSensorClock: Long?,
        liveSensorClock: Long,
    ): Long? {
        if (liveSensorClock <= 0) return null
        // The sensor stores at most 24 hours and its history is addressed on five-minute
        // cadence slots. Real firmware rejects a bootstrap range starting at second 1.
        val oldestAllowed =
            (liveSensorClock - MAX_WINDOW_SECONDS).coerceAtLeast(EXPECTED_INTERVAL_SECONDS)
        val start = lastStoredSensorClock?.plus(EXPECTED_INTERVAL_SECONDS) ?: oldestAllowed
        return start
            .coerceAtLeast(oldestAllowed)
            .takeIf { liveSensorClock - it >= EXPECTED_INTERVAL_SECONDS }
    }

    fun requestedEnd(liveSensorClock: Long): Long? = (liveSensorClock - EXPECTED_INTERVAL_SECONDS).takeIf { it > 0L }

    fun parseRecord(
        packet: ByteArray,
        sensor: G7Sensor,
        live: G7Reading,
        receivedAtEpochMs: Long,
    ): G7Reading {
        require(packet.size == RECORD_BYTES) { "G7 backfill record must contain 9 bytes" }
        val data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        // The timestamp occupies three little-endian bytes; byte 3 is reserved.
        val sensorClock =
            (data.get().toLong() and 0xffL) or
                ((data.get().toLong() and 0xffL) shl 8) or
                ((data.get().toLong() and 0xffL) shl 16)
        data.get()
        val glucoseField = data.short.toInt() and 0xffff
        val glucose = glucoseField and 0x0fff
        val calibrationState = data.get().toInt() and 0xff
        val reserved = data.get().toInt() and 0xff
        val trendRaw = data.get().toInt()
        require(sensorClock in 1 until requireNotNull(live.sensorClockSeconds))
        require(glucose in 14..1_000)
        val sensorStart = requireNotNull(live.sensorStartEpochMs)
        val liveSensorClock = requireNotNull(live.sensorClockSeconds)
        return G7Reading(
            sensorId = sensor.sensorId,
            sessionId = sensor.sessionId ?: sensor.sensorId,
            sequenceNumber = sensorClock / EXPECTED_INTERVAL_SECONDS,
            glucoseMgDl = glucose.toDouble(),
            sensorTimestampEpochMs = sensorStart + sensorClock * 1_000L,
            receivedAtEpochMs = receivedAtEpochMs,
            trendRateMgDlPerMinute = trendRaw.takeUnless { it == 127 }?.div(10.0),
            sensorAgeSeconds = liveSensorClock - sensorClock,
            sensorState = calibrationState.toSensorState(),
            displayOnly = glucoseField and 0xf000 != 0,
            sensorClockSeconds = sensorClock,
            sensorStartEpochMs = live.sensorStartEpochMs,
            sensorEndEpochMs = live.sensorEndEpochMs,
            graceEndEpochMs = live.graceEndEpochMs,
            calibrationStateCode = calibrationState,
            reservedField = reserved,
            origin = CgmReadingOrigin.BACKFILL,
        )
    }

    private fun Int.toSensorState(): G7SensorState =
        when (this) {
            0x02, 0xc1 -> G7SensorState.WARMUP
            0x06, 0x07 -> G7SensorState.ACTIVE
            0x0f, 0x18, 0x1a, 0xc2 -> G7SensorState.ENDED
            in 0x0b..0x17, 0x19, in 0x1b..0x1e -> G7SensorState.ERROR
            else -> G7SensorState.UNKNOWN
        }

    const val RECORD_BYTES = 9
}
