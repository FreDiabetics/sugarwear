package app.sugarlicious.mobile

import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseBarScale
import app.sugarlicious.model.GlucoseBarRange as CanonicalGlucoseBarRange

internal enum class GlucoseBarRange { LOW, TARGET, HIGH }

internal data class GlucoseBarState(
    val range: GlucoseBarRange,
    val position: Float,
)

/** Mobile adapter for the same clamped interval semantics used by Wear. */
internal fun glucoseBarState(value: Double?, thresholds: CgmThresholds): GlucoseBarState? =
    GlucoseBarScale.state(value, thresholds)?.let {
        GlucoseBarState(
            range =
                when (it.range) {
                    CanonicalGlucoseBarRange.LOW -> GlucoseBarRange.LOW
                    CanonicalGlucoseBarRange.TARGET -> GlucoseBarRange.TARGET
                    CanonicalGlucoseBarRange.HIGH -> GlucoseBarRange.HIGH
                },
            position = it.position,
        )
    }
