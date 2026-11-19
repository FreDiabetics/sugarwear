package app.sugarlicious.model

/** Canonical user-visible CGM state. Transport and collector phases are deliberately absent. */
enum class CgmPresentationStatus { CURRENT, AGING, STALE, SIGNAL_LOSS, SENSOR_ERROR, NO_SOURCE }

object CgmPresentationPolicy {
    const val SIGNAL_LOSS_AFTER_MS = 16L * 60_000L

    fun classify(
        measuredAtEpochMs: Long?,
        quality: CgmQuality,
        nowEpochMs: Long,
    ): CgmPresentationStatus {
        if (quality == CgmQuality.SENSOR_ERROR) return CgmPresentationStatus.SENSOR_ERROR
        if (quality != CgmQuality.VALID || measuredAtEpochMs == null) return CgmPresentationStatus.NO_SOURCE
        val ageMs = nowEpochMs - measuredAtEpochMs
        if (ageMs < -FreshnessPolicy.FUTURE_TOLERANCE_MS) return CgmPresentationStatus.NO_SOURCE
        return when {
            ageMs <= FreshnessPolicy.CURRENT_MAX_MS -> CgmPresentationStatus.CURRENT
            ageMs <= FreshnessPolicy.DELAYED_MAX_MS -> CgmPresentationStatus.AGING
            ageMs < SIGNAL_LOSS_AFTER_MS -> CgmPresentationStatus.STALE
            else -> CgmPresentationStatus.SIGNAL_LOSS
        }
    }

    fun classify(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): CgmPresentationStatus =
        classify(
            measuredAtEpochMs = state?.glucose?.measuredAtEpochMs,
            quality = state?.glucose?.quality ?: CgmQuality.INVALID,
            nowEpochMs = nowEpochMs,
        )
}
