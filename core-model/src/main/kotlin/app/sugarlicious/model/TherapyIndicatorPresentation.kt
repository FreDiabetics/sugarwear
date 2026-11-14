package app.sugarlicious.model

/** Renderer-neutral therapy-ring geometry shared by Mobile and Wear ProtoLayout. */
data class TherapyRingGeometry(
    val composeStartDegrees: Float = 130f,
    val sweepDegrees: Float = 280f,
    val strokeWidthDp: Float = 7f,
) {
    /** ProtoLayout measures zero degrees at 12 o'clock; Compose Canvas starts at 3 o'clock. */
    val protoLayoutStartDegrees: Float = (composeStartDegrees + 90f) % 360f
}

enum class TherapyIndicatorIcon { IOB, COB, BASAL, BASAL_LESS, BASAL_MORE }

data class EffectiveBasalPresentation(
    val unitsPerHour: Double,
    val percent: Int?,
)

fun basalIndicatorIcon(percent: Int?): TherapyIndicatorIcon =
    when {
        percent == null || percent == 100 -> TherapyIndicatorIcon.BASAL
        percent < 100 -> TherapyIndicatorIcon.BASAL_LESS
        else -> TherapyIndicatorIcon.BASAL_MORE
    }

fun effectiveBasalPresentation(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): EffectiveBasalPresentation? {
    val basal = state?.basal
    val historical =
        state
            ?.therapyHistory
            .orEmpty()
            .asSequence()
            .filter { it.measuredAtEpochMs <= nowEpochMs }
            .sortedByDescending { it.measuredAtEpochMs }
            .mapNotNull { sample ->
                val rate = sample.tempBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: sample.baseBasalUnitsPerHour
                rate?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                    val percent =
                        sample.baseBasalUnitsPerHour
                            ?.takeIf { base -> base.isFinite() && base > 0.0 }
                            ?.let { base -> (it / base * 100.0).toInt().coerceIn(0, 500) }
                    EffectiveBasalPresentation(it, percent)
                }
            }.firstOrNull()
    val current = basal?.let { effectiveBasalPresentation(it, nowEpochMs) }
    return current?.copy(percent = current.percent ?: historical?.percent) ?: historical
}

fun effectiveBasalPresentation(
    basal: BasalState,
    nowEpochMs: Long,
): EffectiveBasalPresentation? {
    val explicitEnd =
        basal.tempEndsAtEpochMs
            ?: basal.tempStartedAtEpochMs?.let { start -> basal.tempDurationMinutes?.let { start + it * 60_000L } }
    val tempActive =
        (basal.tempAbsoluteUnitsPerHour != null || basal.tempPercent != null) &&
            (explicitEnd == null || explicitEnd > nowEpochMs)
    val units = (if (tempActive) basal.tempAbsoluteUnitsPerHour else null) ?: basal.currentUnitsPerHour ?: return null
    if (!units.isFinite() || units < 0.0) return null
    val percent = if (tempActive) basal.tempPercent?.takeIf { it in 0..500 } else 100
    return EffectiveBasalPresentation(units, percent)
}
