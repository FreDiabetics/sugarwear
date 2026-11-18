package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorState

internal sealed interface CgmWarmupPresentation {
    data object NotWarmingUp : CgmWarmupPresentation

    data object UnknownRemainingTime : CgmWarmupPresentation

    data class CountingDown(
        val remainingMinutes: Long,
        val expectedEndEpochMs: Long,
    ) : CgmWarmupPresentation

    data object AwaitingFirstReading : CgmWarmupPresentation
}

internal object CgmWarmupPolicy {
    const val G7_WARMUP_DURATION_MS = 30L * 60L * 1000L

    fun evaluate(
        sensor: CgmSensor?,
        reading: CgmReading?,
        collectorEnabled: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmWarmupPresentation {
        if (sensor == null || !collectorEnabled) return CgmWarmupPresentation.NotWarmingUp
        if (sensor.state != CgmSensorState.WARMUP) return CgmWarmupPresentation.NotWarmingUp

        val validReadingAvailable = reading != null && reading.status == CgmReadingStatus.VALID && reading.sensorId == sensor.sensorId
        if (validReadingAvailable) return CgmWarmupPresentation.NotWarmingUp

        val startMs = sensor.sensorStartEpochMs ?: reading?.sensorStartEpochMs
        if (startMs == null || startMs <= 0L || startMs > nowEpochMs + 5 * 60_000L) {
            return CgmWarmupPresentation.UnknownRemainingTime
        }

        val expectedEnd = startMs + G7_WARMUP_DURATION_MS
        val remainingMs = expectedEnd - nowEpochMs

        return when {
            remainingMs <= 0L -> CgmWarmupPresentation.AwaitingFirstReading
            else -> {
                val minutes = (remainingMs + 59_999L) / 60_000L
                CgmWarmupPresentation.CountingDown(minutes, expectedEnd)
            }
        }
    }
}
