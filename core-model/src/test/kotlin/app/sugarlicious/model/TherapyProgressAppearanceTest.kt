package app.sugarlicious.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TherapyProgressAppearanceTest {
    @Test
    fun `defaults preserve all metrics and current geometry`() {
        val appearance = TherapyProgressAppearance()

        assertEquals(TherapyProgressMetric.entries.toSet(), appearance.validated().enabledMetrics)
        assertEquals(100, appearance.groupScalePercent)
        assertEquals(6, appearance.gapDp)
        assertEquals(TherapyMetricAppearance(), appearance.global)
    }

    @Test
    fun `invalid values and empty selection recover deterministically`() {
        val validated =
            TherapyProgressAppearance(
                enabledMetrics = emptySet(),
                groupScalePercent = -10,
                gapDp = 99,
                global = TherapyMetricAppearance(overallScalePercent = 500, ringThicknessPercent = 1),
            ).validated()

        assertEquals(TherapyProgressMetric.entries.toSet(), validated.enabledMetrics)
        assertEquals(50, validated.groupScalePercent)
        assertEquals(24, validated.gapDp)
        assertEquals(140, validated.global.overallScalePercent)
        assertEquals(50, validated.global.ringThicknessPercent)
    }

    @Test
    fun `global and metric factors compose for every dimension`() {
        val appearance =
            TherapyProgressAppearance(
                groupScalePercent = 120,
                global = TherapyMetricAppearance(ringThicknessPercent = 150, primaryTextPercent = 80),
                metricOverrides =
                    mapOf(
                        TherapyProgressMetric.IOB to
                            TherapyMetricAppearance(
                                overallScalePercent = 125,
                                ringThicknessPercent = 80,
                                primaryTextPercent = 150,
                                secondaryTextPercent = 110,
                                iconPercent = 90,
                            ),
                    ),
            )

        val resolved = appearance.resolveDimensions(TherapyProgressMetric.IOB)

        assertEquals(99f, resolved.diameterDp, 0.001f)
        assertEquals(12.6f, resolved.strokeWidthDp, 0.001f)
        assertEquals(25.2f, resolved.primaryTextSizeSp, 0.001f)
        assertEquals(16.5f, resolved.secondaryTextSizeSp, 0.001f)
        assertEquals(25.65f, resolved.iconSizeDp, 0.001f)
    }

    @Test
    fun `horizontal layouts cover every non-empty metric selection symmetrically`() {
        val metrics = TherapyProgressMetric.entries
        for (mask in 1 until (1 shl metrics.size)) {
            val enabled = metrics.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val slots =
                resolveHorizontalTherapyLayout(
                    availableWidthDp = 220f,
                    availableHeightDp = 90f,
                    appearance = TherapyProgressAppearance(enabledMetrics = enabled),
                )

            assertEquals(enabled.size, slots.size)
            assertEquals(110f, (slots.first().slot.leftDp + slots.last().slot.rightDp) / 2f, 0.001f)
            assertTrue(slots.all { it.slot.topDp >= 0f && it.slot.bottomDp <= 90f })
        }
    }

    @Test
    fun `round three metric layout is centered triangular and circle safe`() {
        val slots = resolveRoundTherapyLayout(192f, TherapyProgressAppearance())

        assertEquals(3, slots.size)
        assertEquals(slots[0].slot.centerYDp, slots[1].slot.centerYDp, 0.001f)
        assertEquals(96f, (slots[0].slot.centerXDp + slots[1].slot.centerXDp) / 2f, 0.001f)
        assertEquals(96f, slots[2].slot.centerXDp, 0.001f)
        assertTrue(slots[2].slot.centerYDp > slots[0].slot.centerYDp)
        slots.forEach { resolved ->
            val corners =
                listOf(
                    resolved.slot.leftDp to resolved.slot.topDp,
                    resolved.slot.rightDp to resolved.slot.topDp,
                    resolved.slot.leftDp to resolved.slot.bottomDp,
                    resolved.slot.rightDp to resolved.slot.bottomDp,
                )
            assertTrue(corners.all { (x, y) -> (x - 96f) * (x - 96f) + (y - 96f) * (y - 96f) <= 96f * 96f + 0.01f })
        }
    }

    @Test
    fun `unequal sizes and tiny bounds are fitted without moving symmetry`() {
        val appearance =
            TherapyProgressAppearance(
                groupScalePercent = 150,
                metricOverrides =
                    mapOf(
                        TherapyProgressMetric.IOB to TherapyMetricAppearance(overallScalePercent = 140),
                        TherapyProgressMetric.COB to TherapyMetricAppearance(overallScalePercent = 60),
                    ),
            )
        val slots = resolveHorizontalTherapyLayout(96f, 48f, appearance)

        assertEquals(3, slots.size)
        assertTrue(slots.all { it.slot.leftDp >= -0.001f && it.slot.rightDp <= 96.001f })
        assertTrue(slots.all { it.slot.topDp >= -0.001f && it.slot.bottomDp <= 48.001f })
        assertTrue(abs((slots.first().slot.leftDp + slots.last().slot.rightDp) / 2f - 48f) < 0.001f)
    }
}
