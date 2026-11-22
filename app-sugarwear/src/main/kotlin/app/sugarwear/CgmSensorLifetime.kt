package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState

/** Never infer a sensor's end time from installation time or device defaults. */
internal data class CgmSensorLifetime(
    val hasVerifiedEnd: Boolean,
    val days: Long = 0L,
    val hours: Long = 0L,
    val minutes: Long = 0L,
    val remainingDaySegments: Int = 0,
    val expired: Boolean = false,
)

internal fun cgmSensorLifetime(
    state: CgmPersistedState,
    nowEpochMs: Long,
): CgmSensorLifetime? {
    val sensor = state.sensor ?: return null
    val matchedReading =
        state.lastReading?.takeIf {
            sensor.sessionId != null &&
                it.sensorId == sensor.sensorId &&
                it.sessionId == sensor.sessionId
        }
    val start = sensor.sensorStartEpochMs ?: matchedReading?.sensorStartEpochMs
    val end = sensor.sensorEndEpochMs ?: matchedReading?.sensorEndEpochMs
    if (end == null || end <= 0L || (start != null && (end <= start || nowEpochMs < start))) {
        return CgmSensorLifetime(hasVerifiedEnd = false)
    }

    val remainingMs = (end - nowEpochMs).coerceAtLeast(0L)
    val remainingMinutes = remainingMs / 60_000L + if (remainingMs % 60_000L == 0L) 0L else 1L
    val remainingDays = remainingMinutes / (24L * 60L)
    val remainingHours = (remainingMinutes % (24L * 60L)) / 60L
    val minutes = remainingMinutes % 60L
    val dayMs = 24L * 60L * 60_000L
    val segments = (remainingMs / dayMs + if (remainingMs % dayMs == 0L) 0L else 1L).coerceIn(0L, 10L).toInt()
    return CgmSensorLifetime(true, remainingDays, remainingHours, minutes, segments, remainingMs == 0L)
}
