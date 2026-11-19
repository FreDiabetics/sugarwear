package app.sugarlicious.model

/**
 * STATIC = fixed linear bounds; DYNAMIC = visible-window linear bounds;
 * LOGARITHMIC = fixed logarithmic bounds; LOGARITHMIC_DYNAMIC = visible-window log bounds.
 */
enum class CgmGraphScaleMode { STATIC, DYNAMIC, LOGARITHMIC, LOGARITHMIC_DYNAMIC }

/** One canonical CGM value-to-screen transform shared by every layer in a graph render. */
data class CgmGraphYScale(
    val mode: CgmGraphScaleMode,
    val minimumMgDl: Double,
    val maximumMgDl: Double,
    val logarithmicOffsetMgDl: Double = 0.0,
) {
    init {
        require(logarithmicOffsetMgDl.isFinite() && logarithmicOffsetMgDl >= 0.0)
        require(minimumMgDl >= 0.0)
        require(maximumMgDl > minimumMgDl)
    }

    fun ratio(valueMgDl: Double): Double {
        if (!usesCompressedSensorFloor) return asAxisScale().ratio(valueMgDl + offset)
        val bounded = valueMgDl.coerceIn(0.0, maximumMgDl)
        if (bounded <= SENSOR_FLOOR_MG_DL) {
            return bounded / SENSOR_FLOOR_MG_DL * SENSOR_FLOOR_AXIS_FRACTION
        }
        return SENSOR_FLOOR_AXIS_FRACTION +
            sensorReadableScale().ratio(bounded + offset) * (1.0 - SENSOR_FLOOR_AXIS_FRACTION)
    }

    fun inverseRatio(ratio: Double): Double {
        if (!usesCompressedSensorFloor) return asAxisScale().inverseRatio(ratio) - offset
        val bounded = ratio.coerceIn(0.0, 1.0)
        if (bounded <= SENSOR_FLOOR_AXIS_FRACTION) {
            return bounded / SENSOR_FLOOR_AXIS_FRACTION * SENSOR_FLOOR_MG_DL
        }
        return sensorReadableScale().inverseRatio(
            (bounded - SENSOR_FLOOR_AXIS_FRACTION) / (1.0 - SENSOR_FLOOR_AXIS_FRACTION),
        ) - offset
    }

    // Offset zero preserves the strict logarithm for all existing callers.
    private val offset: Double get() = if (mode.isLogarithmic) logarithmicOffsetMgDl else 0.0

    private val usesCompressedSensorFloor: Boolean
        get() = minimumMgDl == 0.0 && maximumMgDl > SENSOR_FLOOR_MG_DL

    private fun sensorReadableScale() =
        GraphAxisScale(
            mode = mode,
            bounds = GraphBounds(SENSOR_FLOOR_MG_DL + offset, maximumMgDl + offset),
            logarithmicDomain = LogarithmicDomain.POSITIVE,
        )

    private fun asAxisScale() =
        GraphAxisScale(
            mode = mode,
            bounds = GraphBounds(minimumMgDl + offset, maximumMgDl + offset),
            // A signed log transform has a defined origin. This keeps the physical 0 mg/dL
            // baseline visible in logarithmic modes instead of pretending that 40 is zero.
            logarithmicDomain = if (minimumMgDl == 0.0 && offset == 0.0) LogarithmicDomain.SIGNED else LogarithmicDomain.POSITIVE,
        )

    companion object {
        const val DEFAULT_MINIMUM_MG_DL = 40.0
        const val DEFAULT_MAXIMUM_MG_DL = 400.0
        const val SENSOR_FLOOR_MG_DL = 40.0
        const val SENSOR_FLOOR_AXIS_FRACTION = 0.06

        fun resolve(
            mode: CgmGraphScaleMode,
            visibleValuesMgDl: Iterable<Double>,
            staticMinimumMgDl: Double = DEFAULT_MINIMUM_MG_DL,
            staticMaximumMgDl: Double = DEFAULT_MAXIMUM_MG_DL,
            requiredValuesMgDl: Iterable<Double> = emptyList(),
        ): CgmGraphYScale {
            val staticMin = staticMinimumMgDl.coerceIn(0.0, 300.0)
            val staticMax = staticMaximumMgDl.coerceIn(staticMin + 20.0, 1_000.0)
            if (mode == CgmGraphScaleMode.STATIC || mode == CgmGraphScaleMode.LOGARITHMIC) {
                return CgmGraphYScale(mode, staticMin, staticMax)
            }
            val values =
                (visibleValuesMgDl + requiredValuesMgDl)
                    .filter { it.isFinite() && it >= 0.0 }
            if (values.isEmpty()) return CgmGraphYScale(mode, staticMin, staticMax)
            val includeRealZero = staticMin == 0.0 || requiredValuesMgDl.any { it == 0.0 }
            val rawMin = if (includeRealZero) 0.0 else values.minOrNull()!!.coerceAtLeast(20.0)
            val rawMax = values.maxOrNull()!!.coerceAtLeast(rawMin + 1.0)
            val span = (rawMax - rawMin).coerceAtLeast(20.0)
            val minimum = if (includeRealZero) 0.0 else (rawMin - span * 0.12).coerceAtLeast(20.0)
            val maximum = (rawMax + span * 0.12).coerceAtLeast(minimum + 20.0)
            return CgmGraphYScale(mode, minimum, maximum)
        }
    }
}
