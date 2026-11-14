package app.sugarlicious.wear

import android.app.Application
import app.sugarlicious.complications.ComplicationGlucoseColorSource
import app.sugarlicious.model.CgmRangeClass

/** Supplies complication color hints from the same active profile as the Wear overview. */
@dagger.hilt.android.HiltAndroidApp
class SugarliciousWearApplication :
    Application(),
    ComplicationGlucoseColorSource {
    override fun onCreate() {
        super.onCreate()
        app.sugarlicious.uishared.ProductMigrationLifecycle
            .requireReady(this)
    }

    override fun outOfRangeGlucoseColor(valueMgDl: Double): Int? {
        val preferences = WearDisplayPreferences.read(this)
        return when (preferences.cgmThresholds.classify(valueMgDl)) {
            CgmRangeClass.VERY_LOW -> preferences.uiColors.glucoseVeryLow
            CgmRangeClass.LOW -> preferences.uiColors.glucoseLow
            CgmRangeClass.HIGH -> preferences.uiColors.glucoseHigh
            CgmRangeClass.VERY_HIGH -> preferences.uiColors.glucoseVeryHigh
            else -> null
        }
    }
}
