package app.sugarlicious.mobile

import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.CgmGraphYScale
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.isDynamic
import app.sugarlicious.model.isLogarithmic
import kotlin.math.ceil
import kotlin.math.floor

/** Mobile display policy; predictions never participate in the observation-based bounds. */
internal fun resolveMobileCgmScale(
    mode: CgmGraphScaleMode,
    measuredValues: List<Double>,
    configuredMinimum: Double,
    configuredMaximum: Double,
    thresholds: CgmThresholds,
): CgmGraphYScale {
    val resolved =
        CgmGraphYScale.resolve(
            mode = mode,
            visibleValuesMgDl = measuredValues,
            staticMinimumMgDl = if (mode.isDynamic) CgmGraphYScale.SENSOR_FLOOR_MG_DL else configuredMinimum,
            staticMaximumMgDl = configuredMaximum,
            requiredValuesMgDl = listOf(thresholds.lowMgDl, thresholds.highMgDl),
        )
    return resolved.copy(
        minimumMgDl = if (mode.isDynamic) floor(resolved.minimumMgDl / 5.0) * 5.0 else resolved.minimumMgDl,
        maximumMgDl = if (mode.isDynamic) ceil(resolved.maximumMgDl / 5.0) * 5.0 else resolved.maximumMgDl,
        logarithmicOffsetMgDl = if (mode.isLogarithmic) 200.0 else 0.0,
    )
}
