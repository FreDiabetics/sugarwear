package app.sugarlicious.model

enum class RangeExcursion {
    LOW,
    HIGH,
}

/**
 * Visual range status follows the newest validated measurement immediately, without confirmation.
 *
 * The canonical threshold policy owns the boundary semantics. VERY_LOW/VERY_HIGH do not create
 * additional graph regions; they collapse into the existing LOW/HIGH background excursion.
 */
object CgmGraphPolicy {
    fun rangeExcursion(
        samples: List<GlucoseSample>,
        thresholds: CgmThresholds,
    ): RangeExcursion? {
        if (!thresholds.isValid) return null

        // Backfill arrival order must not override the latest measured state.
        val latest =
            samples
                .asSequence()
                .filter {
                    it.quality == CgmQuality.VALID &&
                        it.valueMgDl.isFinite() &&
                        it.valueMgDl in 20.0..1_000.0
                }.maxByOrNull(GlucoseSample::measuredAtEpochMs) ?: return null
        return when (thresholds.classify(latest.valueMgDl)) {
            CgmRangeClass.LOW, CgmRangeClass.VERY_LOW -> RangeExcursion.LOW
            CgmRangeClass.HIGH, CgmRangeClass.VERY_HIGH -> RangeExcursion.HIGH
            else -> null
        }
    }

    /** Compatibility overload for existing callers while they migrate to the central policy. */
    fun rangeExcursion(
        samples: List<GlucoseSample>,
        lowMgDl: Double,
        highMgDl: Double,
    ): RangeExcursion? =
        rangeExcursion(
            samples,
            CgmThresholds(
                veryHighMgDl = maxOf(CgmThresholds.DEFAULT_VERY_HIGH_MG_DL, highMgDl + 1.0),
                highMgDl = highMgDl,
                lowMgDl = lowMgDl,
                veryLowMgDl = minOf(CgmThresholds.DEFAULT_VERY_LOW_MG_DL, lowMgDl - 1.0),
            ),
        )
}
