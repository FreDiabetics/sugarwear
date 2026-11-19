package app.sugarlicious.sensor.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object CgmGattProfile {
    val serviceUuid: UUID = UUID.fromString("f8083532-849e-531c-c594-30f1f86a4ea5")
    val controlUuid: UUID = UUID.fromString("f8083534-849e-531c-c594-30f1f86a4ea5")
    val authenticationUuid: UUID = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")
    val backfillUuid: UUID = UUID.fromString("f8083536-849e-531c-c594-30f1f86a4ea5")
    val extraDataUuid: UUID = UUID.fromString("f8083538-849e-531c-c594-30f1f86a4ea5")
    val clientConfigurationUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

interface CgmPacketParser {
    fun parse(
        packet: ByteArray,
        sensor: CgmSensor,
        receivedAtEpochMs: Long,
    ): SensorProtocolReading
}

class CgmGlucosePacketParser : CgmPacketParser {
    override fun parse(
        packet: ByteArray,
        sensor: CgmSensor,
        receivedAtEpochMs: Long,
    ): SensorProtocolReading {
        require(packet.size >= MIN_PACKET_SIZE) { "G7 glucose packet is too short" }
        val data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        require(data.get() == OPCODE) { "Unexpected G7 glucose opcode" }
        val protocolStatus = data.get().toInt() and 0xff
        val sensorClockSeconds = data.int.toLong() and 0xffff_ffffL
        val sequence = data.short.toLong() and 0xffffL
        val reserved = data.short.toInt() and 0xffff
        val ageSeconds = data.short.toLong() and 0xffffL
        val glucoseField = data.short.toInt() and 0xffff
        val glucose = glucoseField and 0x0fff
        val displayOnly = glucoseField and 0xf000 != 0
        val stateCode = data.get().toInt() and 0xff
        val trendRaw = data.get().toInt()
        val predictedRaw = data.short.toInt() and 0x03ff
        require(glucose in 14..1000) { "G7 packet does not contain a usable glucose value" }
        require(ageSeconds <= MAX_READING_AGE_SECONDS) { "G7 packet reading age is invalid" }
        val measuredAt = receivedAtEpochMs - ageSeconds * 1_000L
        val sensorStart = receivedAtEpochMs - sensorClockSeconds * 1_000L
        return SensorProtocolReading(
            sensorId = sensor.sensorId,
            sessionId = sensor.sessionId ?: sensor.sensorId,
            sequenceNumber = sequence,
            glucoseMgDl = glucose.toDouble(),
            sensorTimestampEpochMs = measuredAt,
            receivedAtEpochMs = receivedAtEpochMs,
            trendRateMgDlPerMinute = trendRaw.takeUnless { it == 127 }?.div(10.0),
            predictedMgDl = predictedRaw.takeUnless { it == 0x03ff }?.toDouble(),
            sensorAgeSeconds = ageSeconds,
            sensorState = stateCode.toSensorState(),
            displayOnly = displayOnly,
            sensorClockSeconds = sensorClockSeconds,
            sensorStartEpochMs = sensorStart,
            sensorEndEpochMs = sensorStart + CGM_SENSOR_LIFETIME_MS,
            graceEndEpochMs = sensorStart + CGM_SENSOR_LIFETIME_MS + CGM_GRACE_PERIOD_MS,
            protocolStatusCode = protocolStatus,
            calibrationStateCode = stateCode,
            reservedField = reserved,
        )
    }

    private fun Int.toSensorState(): CgmSensorState =
        when (this) {
            0x02, 0xc1 -> CgmSensorState.WARMUP
            0x06, 0x07 -> CgmSensorState.ACTIVE
            0x0f, 0x18, 0x1a, 0xc2 -> CgmSensorState.ENDED
            in 0x0b..0x17, 0x19, in 0x1b..0x1e -> CgmSensorState.ERROR
            else -> CgmSensorState.UNKNOWN
        }

    private companion object {
        const val OPCODE: Byte = 0x4e
        const val MIN_PACKET_SIZE = 19
        const val MAX_READING_AGE_SECONDS = 30 * 60L
        const val CGM_SENSOR_LIFETIME_MS = 10L * 24L * 60L * 60_000L
        const val CGM_GRACE_PERIOD_MS = 12L * 60L * 60_000L
    }
}

interface CgmScanner {
    suspend fun findKnownSensor(
        sensor: CgmSensor?,
        timeoutMs: Long,
    ): CgmSensor?
}

interface CgmConnectionManager {
    suspend fun collectNextReading(sensor: CgmSensor): SensorProtocolReading
}
