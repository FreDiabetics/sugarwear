package app.sugarlicious.model

import kotlinx.serialization.Serializable

/** Canonical Sugarlicious glucose-range classification. Values are stored in mg/dL. */
enum class CgmRangeClass {
    VERY_LOW,
    LOW,
    IN_RANGE,
    HIGH,
    VERY_HIGH,
}

@Serializable
data class CgmThresholds(
    val veryHighMgDl: Double = DEFAULT_VERY_HIGH_MG_DL,
    val highMgDl: Double = DEFAULT_HIGH_MG_DL,
    val lowMgDl: Double = DEFAULT_LOW_MG_DL,
    val veryLowMgDl: Double = DEFAULT_VERY_LOW_MG_DL,
) {
    val isValid: Boolean
        get() =
            veryHighMgDl.isFinite() &&
                highMgDl.isFinite() &&
                lowMgDl.isFinite() &&
                veryLowMgDl.isFinite() &&
                veryLowMgDl < lowMgDl &&
                lowMgDl < highMgDl &&
                highMgDl < veryHighMgDl

    fun classify(valueMgDl: Double): CgmRangeClass? {
        if (!isValid || !valueMgDl.isFinite()) return null
        return when {
            valueMgDl <= veryLowMgDl -> CgmRangeClass.VERY_LOW
            valueMgDl <= lowMgDl -> CgmRangeClass.LOW
            valueMgDl < highMgDl -> CgmRangeClass.IN_RANGE
            valueMgDl < veryHighMgDl -> CgmRangeClass.HIGH
            else -> CgmRangeClass.VERY_HIGH
        }
    }

    companion object {
        const val DEFAULT_VERY_HIGH_MG_DL = 250.0
        const val DEFAULT_HIGH_MG_DL = 180.0
        const val DEFAULT_LOW_MG_DL = 70.0
        const val DEFAULT_VERY_LOW_MG_DL = 50.0
        val DEFAULT = CgmThresholds()
    }
}
