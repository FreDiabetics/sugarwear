package app.sugarlicious.mobile

import android.content.SharedPreferences
import androidx.core.content.edit
import app.sugarlicious.model.CgmThresholds

internal object CgmThresholdPreferences {
    const val VERY_HIGH = "cgm.threshold.veryHighMgDl"
    const val HIGH = "cgm.threshold.highMgDl"
    const val LOW = "cgm.threshold.lowMgDl"
    const val VERY_LOW = "cgm.threshold.veryLowMgDl"

    fun read(preferences: SharedPreferences): CgmThresholds {
        val candidate =
            CgmThresholds(
                veryHighMgDl = preferences.getFloat(VERY_HIGH, CgmThresholds.DEFAULT_VERY_HIGH_MG_DL.toFloat()).toDouble(),
                highMgDl = preferences.getFloat(HIGH, CgmThresholds.DEFAULT_HIGH_MG_DL.toFloat()).toDouble(),
                lowMgDl = preferences.getFloat(LOW, CgmThresholds.DEFAULT_LOW_MG_DL.toFloat()).toDouble(),
                veryLowMgDl = preferences.getFloat(VERY_LOW, CgmThresholds.DEFAULT_VERY_LOW_MG_DL.toFloat()).toDouble(),
            )
        return candidate.takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT
    }

    /** Saves the complete policy atomically. Invalid combinations never reach persistence. */
    fun save(
        preferences: SharedPreferences,
        thresholds: CgmThresholds,
    ): Boolean {
        if (!thresholds.isValid) return false
        preferences.edit(commit = true) {
            putFloat(VERY_HIGH, thresholds.veryHighMgDl.toFloat())
            putFloat(HIGH, thresholds.highMgDl.toFloat())
            putFloat(LOW, thresholds.lowMgDl.toFloat())
            putFloat(VERY_LOW, thresholds.veryLowMgDl.toFloat())
        }
        return true
    }
}
