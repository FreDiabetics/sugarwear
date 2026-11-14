package app.sugarwear.debug.playground.data

import android.content.Context
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.uishared.SharedWearCgmGraphInput
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import app.sugarwear.CgmAppearanceStore
import app.sugarwear.CgmDirectToWatchSettingsStore
import app.sugarwear.cgmSharedGraphInput
import app.sugarwear.ui.visual.GraphVisualStyle

/**
 * Maps raw readings and playground styles into shared graph input models.
 */
object PlaygroundDataMapper {
    internal fun toGraphInput(
        context: Context,
        readings: List<CgmReading>,
        graphHours: Int = 3,
        nowEpochMs: Long = System.currentTimeMillis(),
        visualStyle: GraphVisualStyle? = null,
    ): SharedWearCgmGraphInput {
        val palette = CgmAppearanceStore(context).load()
        val settings = CgmDirectToWatchSettingsStore(context)
        val styleOverride = visualStyle?.let {
            SharedWearCgmGraphStyle(
                dotRadiusDp = it.pointRadiusDp.value,
                dotOutlineWidthDp = it.dotOutlineWidthDp.value,
            )
        }

        return cgmSharedGraphInput(
            readings = readings,
            palette = palette,
            settings = settings,
            graphHours = graphHours,
            nowEpochMs = nowEpochMs,
            styleOverride = styleOverride,
        )
    }
}
