package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmGlucosePacketParser
import app.sugarlicious.sensor.protocol.CgmPersistedState

/** Never infer a sensor's end time from installation time or device defaults. */
internal data class CgmSensorLifetime(
    val hasVerifiedEnd: Boolean,
    val days: Long = 0L,
    val hours: Long = 0L,
    val minutes: Long = 0L,
    val remainingDaySegments: Int = 0,
    val expired: Boolean = false,
    val hasVerifiedGraceEnd: Boolean = false,
    val inGracePeriod: Boolean = false,
    val graceExpired: Boolean = false,
    val graceHours: Long = 0L,
    val graceMinutes: Long = 0L,
)

internal fun CgmSensorLifetime.compactRemainingTime(): String = "${days}T ${hours}h ${minutes}m"

internal fun CgmSensorLifetime.toleranceTimeText(): String? =
    when {
        !hasVerifiedGraceEnd -> null
        graceExpired -> "Toleranzzeit beendet"
        inGracePeriod -> "${graceHours}h ${graceMinutes}m Toleranzzeit"
        else -> "+ 12h Toleranzzeit"
    }


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
    val graceEnd = sensor.graceEndEpochMs ?: matchedReading?.graceEndEpochMs
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
    // Only accept a G7 grace end belonging to this sensor/session and matching the protocol's 12h interval.
    val graceVerified = graceEnd != null && graceEnd > end && graceEnd - end == CgmGlucosePacketParser.CGM_GRACE_PERIOD_MS
    val inGrace = graceVerified && nowEpochMs >= end && nowEpochMs < checkNotNull(graceEnd)
    val graceRemainingMs = if (inGrace) (checkNotNull(graceEnd) - nowEpochMs).coerceAtLeast(0L) else 0L
    val graceRemainingMinutes = graceRemainingMs / 60_000L + if (graceRemainingMs % 60_000L == 0L) 0L else 1L
    return CgmSensorLifetime(
        hasVerifiedEnd = true,
        days = remainingDays,
        hours = remainingHours,
        minutes = minutes,
        remainingDaySegments = segments,
        expired = remainingMs == 0L,
        hasVerifiedGraceEnd = graceVerified,
        inGracePeriod = inGrace,
        graceExpired = graceVerified && nowEpochMs >= checkNotNull(graceEnd),
        graceHours = graceRemainingMinutes / 60L,
        graceMinutes = graceRemainingMinutes % 60L,
    )
}
