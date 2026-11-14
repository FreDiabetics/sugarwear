package app.sugarlicious.model

import kotlin.math.abs

/** Single owner for canonical CGM measurement identity; transport sequence numbers are never identity. */
object CanonicalCgmIdentity {
    const val HISTORY_TIMESTAMP_TOLERANCE_MS = 90_000L

    fun sameMeasurement(
        first: GlucoseSample,
        second: GlucoseSample,
        timestampToleranceMs: Long = HISTORY_TIMESTAMP_TOLERANCE_MS,
    ): Boolean {
        require(timestampToleranceMs >= 0L)
        val timeDifference = abs(first.measuredAtEpochMs - second.measuredAtEpochMs)
        val sameKnownSensor = first.sensorId != null && first.sensorId == second.sensorId
        val sameKnownSession = first.sessionId != null && first.sessionId == second.sessionId

        if (sameKnownSensor && sameKnownSession && timeDifference <= timestampToleranceMs) return true
        if (first.sensorId != null && second.sensorId != null && first.sensorId != second.sensorId) return false
        if (first.sessionId != null && second.sessionId != null && first.sessionId != second.sessionId) return false
        return timeDifference == 0L && first.source == second.source
    }

    fun sameMeasurement(
        mobile: CgmSourceCandidate?,
        watch: CgmSourceCandidate?,
    ): Boolean {
        if (mobile == null || watch == null) return false
        if (mobile.measuredAtEpochMs != watch.measuredAtEpochMs) return false
        if (mobile.sensorId == null || watch.sensorId == null || mobile.sensorId != watch.sensorId) return false
        if (mobile.sessionId == null || watch.sessionId == null || mobile.sessionId != watch.sessionId) return false
        return abs(mobile.glucoseMgDl - watch.glucoseMgDl) <= 1.0
    }
}
