package app.sugarlicious.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/** The independent numerical axes currently rendered by the Sugarlicious graphs. */
enum class GraphAxis { CGM, IOB, COB, INSULIN_ACTIVITY }

data class GraphBounds(
    val minimum: Double,
    val maximum: Double,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && maximum > minimum)
    }
}

enum class LogarithmicDomain { POSITIVE, SIGNED }

/**
 * One immutable vertical scale for one axis and one render. STATIC and LOGARITHMIC differ only
 * by their transform; DYNAMIC and LOGARITHMIC_DYNAMIC differ only by their transform as well.
 */
data class GraphAxisScale(
    val mode: CgmGraphScaleMode,
    val bounds: GraphBounds,
    val logarithmicDomain: LogarithmicDomain = LogarithmicDomain.SIGNED,
) {
    private val linearThreshold = ((bounds.maximum - bounds.minimum) * 0.01).coerceAtLeast(0.000001)

    fun ratio(value: Double): Double {
        val bounded = value.coerceIn(bounds.minimum, bounds.maximum)
        val transformedMinimum = transform(bounds.minimum)
        val transformedMaximum = transform(bounds.maximum)
        return ((transform(bounded) - transformedMinimum) / (transformedMaximum - transformedMinimum))
            .coerceIn(0.0, 1.0)
    }

    fun inverseRatio(ratio: Double): Double {
        val transformedMinimum = transform(bounds.minimum)
        val transformedMaximum = transform(bounds.maximum)
        val transformed = transformedMinimum + ratio.coerceIn(0.0, 1.0) * (transformedMaximum - transformedMinimum)
        return inverseTransform(transformed).coerceIn(bounds.minimum, bounds.maximum)
    }

    private fun transform(value: Double): Double =
        when {
            !mode.isLogarithmic -> value
            logarithmicDomain == LogarithmicDomain.POSITIVE -> ln(value.coerceAtLeast(bounds.minimum.coerceAtLeast(0.000001)))
            else -> sign(value) * ln(1.0 + abs(value) / linearThreshold)
        }

    private fun inverseTransform(value: Double): Double =
        when {
            !mode.isLogarithmic -> value
            logarithmicDomain == LogarithmicDomain.POSITIVE -> kotlin.math.exp(value)
            else -> sign(value) * linearThreshold * (kotlin.math.exp(abs(value)) - 1.0)
        }
}

val CgmGraphScaleMode.isDynamic: Boolean
    get() = this == CgmGraphScaleMode.DYNAMIC || this == CgmGraphScaleMode.LOGARITHMIC_DYNAMIC

val CgmGraphScaleMode.isLogarithmic: Boolean
    get() = this == CgmGraphScaleMode.LOGARITHMIC || this == CgmGraphScaleMode.LOGARITHMIC_DYNAMIC

/**
 * Central scale strategy. Static bounds are frozen once per axis for the lifetime of the graph
 * session; dynamic bounds are deterministically derived from the current visible X viewport.
 */
class GraphScaleSession {
    private val fixedBounds = mutableMapOf<GraphAxis, GraphBounds>()

    fun resolve(
        axis: GraphAxis,
        mode: CgmGraphScaleMode,
        seedValues: Iterable<Double>,
        visibleValues: Iterable<Double>,
        fallbackBounds: GraphBounds,
        minimumSpan: Double,
        maxTickCount: Int = 5,
        requiredValues: Iterable<Double> = emptyList(),
        logarithmicDomain: LogarithmicDomain = LogarithmicDomain.SIGNED,
    ): GraphAxisScale {
        val values = if (mode.isDynamic) visibleValues + requiredValues else seedValues + requiredValues
        val resolved =
            if (mode.isDynamic) {
                niceBounds(values, fallbackBounds, minimumSpan, maxTickCount)
            } else {
                fixedBounds.getOrPut(axis) { niceBounds(values, fallbackBounds, minimumSpan, maxTickCount) }
            }
        return GraphAxisScale(mode, resolved, logarithmicDomain)
    }

    fun useConfiguredBounds(
        axis: GraphAxis,
        bounds: GraphBounds,
    ) {
        fixedBounds[axis] = bounds
    }

    fun clear(axis: GraphAxis) {
        fixedBounds.remove(axis)
    }

    fun fixedBounds(axis: GraphAxis): GraphBounds? = fixedBounds[axis]
}

private fun niceBounds(
    values: Iterable<Double>,
    fallback: GraphBounds,
    minimumSpan: Double,
    maxTickCount: Int,
): GraphBounds {
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return fallback
    val dataMinimum = finite.minOrNull() ?: return fallback
    val dataMaximum = finite.maxOrNull() ?: return fallback
    val zeroFloor = dataMinimum >= 0.0
    val rawMinimum = if (zeroFloor) 0.0 else dataMinimum
    val rawMaximum = max(dataMaximum, rawMinimum + minimumSpan)
    val rawSpan = max(rawMaximum - rawMinimum, minimumSpan)
    val magnitude = 10.0.pow(floor(log10(rawSpan.coerceAtLeast(0.000001))))
    val fraction = rawSpan / magnitude
    val niceSpan =
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        } * magnitude
    val step = niceSpan / (maxTickCount.coerceAtLeast(2) - 1)
    val minimum = if (zeroFloor) 0.0 else kotlin.math.floor(rawMinimum / step) * step
    val maximum = kotlin.math.ceil(rawMaximum / step) * step
    return GraphBounds(minimum, max(maximum, minimum + minimumSpan))
}
