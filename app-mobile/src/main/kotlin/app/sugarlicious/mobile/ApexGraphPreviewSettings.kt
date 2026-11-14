package app.sugarlicious.mobile

import android.content.Context
import app.sugarlicious.model.CgmThresholds

internal data class ApexGraphPreviewSettings(
    val hours: Double,
    val thresholds: CgmThresholds,
)

internal fun loadApexGraphPreviewSettings(context: Context): ApexGraphPreviewSettings =
    ApexGraphPreviewSettings(
        hours = 1.5,
        thresholds = CgmThresholdPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
    )
