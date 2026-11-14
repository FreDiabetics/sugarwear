package app.sugarlicious.model

import kotlin.math.hypot
import kotlin.math.min

enum class TherapyProgressMetric { IOB, COB, BASAL }

data class TherapyMetricAppearance(
    val overallScalePercent: Int = 100,
    val ringThicknessPercent: Int = 100,
    val primaryTextPercent: Int = 100,
    val secondaryTextPercent: Int = 100,
    val iconPercent: Int = 100,
) {
    fun validated(): TherapyMetricAppearance =
        copy(
            overallScalePercent = overallScalePercent.coerceIn(60, 140),
            ringThicknessPercent = ringThicknessPercent.coerceIn(50, 200),
            primaryTextPercent = primaryTextPercent.coerceIn(60, 180),
            secondaryTextPercent = secondaryTextPercent.coerceIn(60, 180),
            iconPercent = iconPercent.coerceIn(50, 180),
        )
}

data class TherapyProgressAppearance(
    val enabledMetrics: Set<TherapyProgressMetric> = TherapyProgressMetric.entries.toSet(),
    val groupScalePercent: Int = 100,
    val gapDp: Int = 6,
    val global: TherapyMetricAppearance = TherapyMetricAppearance(),
    val metricOverrides: Map<TherapyProgressMetric, TherapyMetricAppearance> = emptyMap(),
) {
    fun validated(): TherapyProgressAppearance =
        copy(
            enabledMetrics = enabledMetrics.ifEmpty { TherapyProgressMetric.entries.toSet() },
            groupScalePercent = groupScalePercent.coerceIn(50, 150),
            gapDp = gapDp.coerceIn(0, 24),
            global = global.validated(),
            metricOverrides = metricOverrides.mapValues { it.value.validated() },
        )

    fun resolveDimensions(
        metric: TherapyProgressMetric,
        baseDiameterDp: Float = 66f,
        baseStrokeWidthDp: Float = 7f,
        basePrimaryTextSizeSp: Float = TherapyIndicatorGeometry.VALUE_TEXT_SP,
        baseSecondaryTextSizeSp: Float = TherapyIndicatorGeometry.SECONDARY_TEXT_SP,
        baseIconSizeDp: Float = TherapyIndicatorGeometry.iconSizeDp(metric, TherapyIndicatorGeometry.REFERENCE_DIAMETER_DP),
    ): ResolvedTherapyDimensions {
        val safe = validated()
        val local = safe.metricOverrides[metric]?.validated() ?: TherapyMetricAppearance()
        val groupFactor = safe.groupScalePercent / 100f
        val overallFactor = local.overallScalePercent / 100f
        val common = groupFactor * overallFactor
        return ResolvedTherapyDimensions(
            diameterDp = baseDiameterDp * common,
            strokeWidthDp = baseStrokeWidthDp * common * safe.global.ringThicknessPercent / 100f * local.ringThicknessPercent / 100f,
            primaryTextSizeSp = basePrimaryTextSizeSp * common * safe.global.primaryTextPercent / 100f * local.primaryTextPercent / 100f,
            secondaryTextSizeSp = baseSecondaryTextSizeSp * common * safe.global.secondaryTextPercent / 100f * local.secondaryTextPercent / 100f,
            iconSizeDp = baseIconSizeDp * common * safe.global.iconPercent / 100f * local.iconPercent / 100f,
        )
    }
}

data class ResolvedTherapyDimensions(
    val diameterDp: Float,
    val strokeWidthDp: Float,
    val primaryTextSizeSp: Float,
    val secondaryTextSizeSp: Float,
    val iconSizeDp: Float,
) {
    fun scaled(factor: Float): ResolvedTherapyDimensions =
        copy(
            diameterDp = diameterDp * factor,
            strokeWidthDp = strokeWidthDp * factor,
            primaryTextSizeSp = primaryTextSizeSp * factor,
            secondaryTextSizeSp = secondaryTextSizeSp * factor,
            iconSizeDp = iconSizeDp * factor,
        )
}

data class TherapySlot(
    val centerXDp: Float,
    val centerYDp: Float,
    val diameterDp: Float,
) {
    val leftDp: Float get() = centerXDp - diameterDp / 2f
    val rightDp: Float get() = centerXDp + diameterDp / 2f
    val topDp: Float get() = centerYDp - diameterDp / 2f
    val bottomDp: Float get() = centerYDp + diameterDp / 2f
}

data class ResolvedTherapyIndicator(
    val metric: TherapyProgressMetric,
    val slot: TherapySlot,
    val strokeWidthDp: Float,
    val primaryTextSizeSp: Float,
    val secondaryTextSizeSp: Float,
    val iconSizeDp: Float,
)

fun resolveHorizontalTherapyLayout(
    availableWidthDp: Float,
    availableHeightDp: Float,
    appearance: TherapyProgressAppearance,
): List<ResolvedTherapyIndicator> {
    val safe = appearance.validated()
    val metrics = TherapyProgressMetric.entries.filter(safe.enabledMetrics::contains)
    val dimensions = metrics.map(safe::resolveDimensions)
    val desiredGap = safe.gapDp.toFloat()
    val desiredWidth = dimensions.sumOf { it.diameterDp.toDouble() }.toFloat() + desiredGap * (metrics.size - 1)
    val desiredHeight = dimensions.maxOf { it.diameterDp }
    val fit = min(1f, min(availableWidthDp.coerceAtLeast(1f) / desiredWidth, availableHeightDp.coerceAtLeast(1f) / desiredHeight))
    val scaled = dimensions.map { it.scaled(fit) }
    val gap = desiredGap * fit
    val groupWidth = scaled.sumOf { it.diameterDp.toDouble() }.toFloat() + gap * (metrics.size - 1)
    var cursor = (availableWidthDp - groupWidth) / 2f
    return metrics.mapIndexed { index, metric ->
        val item = scaled[index]
        val centerX = cursor + item.diameterDp / 2f
        cursor += item.diameterDp + gap
        item.toIndicator(metric, TherapySlot(centerX, availableHeightDp / 2f, item.diameterDp))
    }
}

fun resolveVerticalTherapyLayout(
    availableWidthDp: Float,
    availableHeightDp: Float,
    appearance: TherapyProgressAppearance,
): List<ResolvedTherapyIndicator> =
    resolveHorizontalTherapyLayout(availableHeightDp, availableWidthDp, appearance).map { indicator ->
        indicator.copy(
            slot =
                TherapySlot(
                    centerXDp = indicator.slot.centerYDp,
                    centerYDp = indicator.slot.centerXDp,
                    diameterDp = indicator.slot.diameterDp,
                ),
        )
    }

fun resolveResponsiveTherapyLayout(
    availableWidthDp: Float,
    availableHeightDp: Float,
    appearance: TherapyProgressAppearance,
): List<ResolvedTherapyIndicator> =
    if (availableWidthDp >= availableHeightDp) {
        resolveHorizontalTherapyLayout(availableWidthDp, availableHeightDp, appearance)
    } else {
        resolveVerticalTherapyLayout(availableWidthDp, availableHeightDp, appearance)
    }

fun resolveRoundTherapyLayout(
    availableSideDp: Float,
    appearance: TherapyProgressAppearance,
    baseDiameterDp: Float = 66f,
): List<ResolvedTherapyIndicator> {
    val safe = appearance.validated()
    val metrics = TherapyProgressMetric.entries.filter(safe.enabledMetrics::contains)
    val dimensions =
        metrics.map { metric ->
            val scale = baseDiameterDp / 66f
            safe.resolveDimensions(metric).scaled(scale)
        }
    val center = availableSideDp / 2f
    val gap = safe.gapDp.toFloat()
    val rawSlots =
        when (metrics.size) {
            1 -> listOf(TherapySlot(center, center, dimensions[0].diameterDp))
            2 -> {
                val width = dimensions[0].diameterDp + gap + dimensions[1].diameterDp
                val left = center - width / 2f
                listOf(
                    TherapySlot(left + dimensions[0].diameterDp / 2f, center, dimensions[0].diameterDp),
                    TherapySlot(left + dimensions[0].diameterDp + gap + dimensions[1].diameterDp / 2f, center, dimensions[1].diameterDp),
                )
            }
            else -> {
                val topWidth = dimensions[0].diameterDp + gap + dimensions[1].diameterDp
                val topLeft = center - topWidth / 2f
                val topDiameter = maxOf(dimensions[0].diameterDp, dimensions[1].diameterDp)
                val totalHeight = topDiameter + gap + dimensions[2].diameterDp
                val topY = center - totalHeight / 2f + topDiameter / 2f
                val bottomY = center + totalHeight / 2f - dimensions[2].diameterDp / 2f
                listOf(
                    TherapySlot(topLeft + dimensions[0].diameterDp / 2f, topY, dimensions[0].diameterDp),
                    TherapySlot(topLeft + dimensions[0].diameterDp + gap + dimensions[1].diameterDp / 2f, topY, dimensions[1].diameterDp),
                    TherapySlot(center, bottomY, dimensions[2].diameterDp),
                )
            }
        }
    val radius = availableSideDp.coerceAtLeast(1f) / 2f
    val farthest =
        rawSlots.maxOf { slot ->
            listOf(slot.leftDp to slot.topDp, slot.rightDp to slot.topDp, slot.leftDp to slot.bottomDp, slot.rightDp to slot.bottomDp)
                .maxOf { (x, y) -> hypot(x - center, y - center) }
        }
    val fit = min(1f, radius / farthest.coerceAtLeast(1f))
    return metrics.mapIndexed { index, metric ->
        val raw = rawSlots[index]
        val item = dimensions[index].scaled(fit)
        val slot =
            TherapySlot(
                center + (raw.centerXDp - center) * fit,
                center + (raw.centerYDp - center) * fit,
                raw.diameterDp * fit,
            )
        item.toIndicator(metric, slot)
    }
}

private fun ResolvedTherapyDimensions.toIndicator(
    metric: TherapyProgressMetric,
    slot: TherapySlot,
): ResolvedTherapyIndicator =
    ResolvedTherapyIndicator(metric, slot, strokeWidthDp, primaryTextSizeSp, secondaryTextSizeSp, iconSizeDp)
