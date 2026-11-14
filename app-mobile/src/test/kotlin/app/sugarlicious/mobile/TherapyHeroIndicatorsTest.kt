package app.sugarlicious.mobile

import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.EffectiveBasalPresentation
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyHistorySample
import app.sugarlicious.model.effectiveBasalPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TherapyHeroIndicatorsTest {
    @Test fun `split flap rolls individual decimal places through carries and reversals`() {
        assertEquals(2, splitFlapSteps('9', '1', 1))
        assertEquals(2, splitFlapSteps('1', '9', -1))
        assertEquals(0, splitFlapSteps('5', '5', 1))
        assertEquals(0, splitFlapSteps('U', 'U', 1))
        assertEquals(9, splitFlapSteps('1', '0', 1))
        assertTrue(therapyIndicatorPresentations(null, 10f, 1000L).all { it.numericValue == null })
    }

    @Test
    fun `Basal icon matches IOB while COB retains its approved size`() {
        val indicators = therapyIndicatorPresentations(state(), 10f, 1_000L)
        assertEquals(
            19f,
            app.sugarlicious.model.TherapyIndicatorGeometry
                .iconSizeDp(app.sugarlicious.model.TherapyProgressMetric.IOB, 66f),
        )
        assertEquals(
            17f,
            app.sugarlicious.model.TherapyIndicatorGeometry
                .iconSizeDp(app.sugarlicious.model.TherapyProgressMetric.COB, 66f),
        )
        assertEquals(
            19f,
            app.sugarlicious.model.TherapyIndicatorGeometry
                .iconSizeDp(app.sugarlicious.model.TherapyProgressMetric.BASAL, 66f),
        )
    }

    @Test
    fun `three rings use canonical values and clamp their scales`() {
        val state =
            state(
                insulin = InsulinState(totalIob = 12.0),
                carbs = CarbState(cobGrams = 450.0),
                basal = BasalState(currentUnitsPerHour = 0.8, tempAbsoluteUnitsPerHour = 1.2, tempPercent = 150),
            )
        val values = therapyIndicatorPresentations(state, iobMaximumUnits = 10f, nowEpochMs = 1_000L)
        assertEquals(3, values.size)
        assertEquals(1f, values[0].progress!!, 0.0001f)
        assertEquals(1f, values[1].progress!!, 0.0001f)
        assertEquals("12.00U", values[0].value)
        assertEquals("450g", values[1].value)
        assertEquals("1.20U", values[2].value)
        assertEquals("@150%", values[2].secondary)
        assertEquals(0.5625f, values[2].progress!!, 0.0001f)
    }

    @Test
    fun `cob ring uses its independently configured maximum`() {
        val values =
            therapyIndicatorPresentations(
                state = state(carbs = CarbState(cobGrams = 75.0)),
                iobMaximumUnits = 10f,
                nowEpochMs = 1_000L,
                cobMaximumGrams = 150f,
            )

        assertEquals(0.5f, values[1].progress!!, 0.0001f)
    }

    @Test
    fun `basal icon follows standard lower and higher temp basal`() {
        assertEquals(app.sugarlicious.uishared.R.drawable.ic_basal, basalIconResource(100))
        assertEquals(app.sugarlicious.uishared.R.drawable.ic_basalless, basalIconResource(80))
        assertEquals(app.sugarlicious.uishared.R.drawable.ic_basalmore, basalIconResource(120))
    }

    @Test
    fun `latest canonical therapy history supplies missing basal snapshot`() {
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = 1_000L,
                therapyHistory =
                    listOf(
                        TherapyHistorySample(800L, basalUnitsPerHour = 0.75, baseBasalUnitsPerHour = 0.5),
                    ),
            )
        val basal = therapyIndicatorPresentations(state, 10f, 1_000L)[2]
        assertEquals("0.75U", basal.value)
        assertEquals("@150%", basal.secondary)
        assertEquals(app.sugarlicious.uishared.R.drawable.ic_basalmore, basal.iconRes)
    }

    @Test
    fun `basal progress reserves half ring for the normal rate`() {
        assertEquals(0f, basalProgress(0))
        assertEquals(0.25f, basalProgress(50))
        assertEquals(0.5f, basalProgress(100))
        assertEquals(0.75f, basalProgress(300))
        assertEquals(1f, basalProgress(500))
    }

    @Test
    fun `zero IOB maximum is safe and never invents progress`() {
        val values = therapyIndicatorPresentations(state(insulin = InsulinState(totalIob = 2.0)), 0f, 1_000L)
        assertNull(values.first().progress)
    }

    @Test
    fun `missing IOB and COB remain unavailable instead of inventing zero`() {
        val values = therapyIndicatorPresentations(null, 10f, 1_000L)
        assertEquals("—", values[0].value)
        assertEquals("—", values[1].value)
        assertEquals("—", values[2].value)
        assertNull(values[0].progress)
        assertNull(values[1].progress)
        assertNull(values[2].secondary)
    }

    @Test
    fun `negative IOB is displayed instead of replaced by a dash`() {
        val values = therapyIndicatorPresentations(state(insulin = InsulinState(totalIob = -0.65)), 10f, 1_000L)

        assertEquals("-0.65U", values[0].value)
    }

    @Test
    fun `IOB secondary shows the latest valid bolus with compact age`() {
        val now = 10 * 60_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = InsulinState(totalIob = 1.2),
                therapyEvents =
                    listOf(
                        TherapyEvent("meal", TherapyEventKind.MEAL_BOLUS, now - 9 * 60_000L, 2.0),
                        TherapyEvent("smb", TherapyEventKind.SMB, now - 8 * 60_000L, 0.3),
                        TherapyEvent("carbs", TherapyEventKind.MEAL_CARBS, now - 60_000L, 20.0),
                        TherapyEvent("future", TherapyEventKind.MANUAL_CORRECTION, now + 1L, 4.0),
                        TherapyEvent("invalid", TherapyEventKind.MANUAL_CORRECTION, now - 1L, Double.NaN),
                    ),
            )

        assertEquals("vor 8m", therapyIndicatorPresentations(state, 10f, now)[0].secondary)
    }

    @Test fun `last bolus age switches from minutes to whole hours at sixty minutes`() {
        assertEquals("vor 59m", formatCompactBolusAge(59L * 60_000L))
        assertEquals("vor 60m", formatCompactBolusAge(60L * 60_000L))
        assertEquals("vor 179m", formatCompactBolusAge(179L * 60_000L))
    }

    @Test
    fun `IOB secondary is absent when no positive canonical bolus exists`() {
        val now = 10 * 60_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = InsulinState(totalIob = 1.2),
                therapyEvents = listOf(TherapyEvent("zero", TherapyEventKind.SMB, now - 60_000L, 0.0)),
            )

        assertNull(therapyIndicatorPresentations(state, 10f, now)[0].secondary)
    }

    @Test
    fun `expired temp basal falls back to canonical base rate and 100 percent`() {
        val basal =
            BasalState(
                currentUnitsPerHour = 0.8,
                tempAbsoluteUnitsPerHour = 1.2,
                tempPercent = 150,
                tempEndsAtEpochMs = 900L,
            )
        assertEquals(EffectiveBasalPresentation(0.8, 100), effectiveBasalPresentation(basal, 1_000L))
    }

    @Test
    fun `graph separates plot from time and value axis background lanes`() {
        val bounds = mobileCgmGraphBounds(400f, 240f, 1f, 32f, 40f, scaleOnRight = true)
        assertEquals(359f, bounds.plot.right, 0.001f)
        assertEquals(bounds.plot.bottom, bounds.timeAxis.top, 0.001f)
        assertEquals(bounds.plot.right, bounds.valueAxis.left, 0.001f)
        assertTrue(bounds.tile.contains(bounds.plot))
        assertTrue(!bounds.plot.contains(bounds.valueAxis.centerX(), bounds.valueAxis.centerY()))
    }

    @Test
    fun `CGM graph scale lane moves continuously between sides`() {
        val left = mobileCgmGraphBounds(400f, 240f, 1f, 32f, 40f, scaleRightBlend = 0f)
        val middle = mobileCgmGraphBounds(400f, 240f, 1f, 32f, 40f, scaleRightBlend = 0.5f)
        val right = mobileCgmGraphBounds(400f, 240f, 1f, 32f, 40f, scaleRightBlend = 1f)

        assertEquals((left.plot.left + right.plot.left) / 2f, middle.plot.left, 0.001f)
        assertEquals((left.plot.right + right.plot.right) / 2f, middle.plot.right, 0.001f)
    }

    @Test
    fun `metabolic graph uses one plot region with a minimal square internal separator`() {
        val bounds = mobileMetabolicGraphBounds(400f, 260f, 1f, 24f, 29f, scaleOnRight = true)

        assertEquals(bounds.plotRegion.right, bounds.valueAxis.left, 0.001f)
        assertEquals(bounds.plotRegion.bottom, bounds.timeAxis.top, 0.001f)
        assertEquals(2f, bounds.separator.height(), 0.001f)
        assertEquals(bounds.iobLane.bottom, bounds.separator.top, 0.001f)
        assertEquals(bounds.separator.bottom, bounds.cobLane.top, 0.001f)
        assertTrue(bounds.iobData.height() > bounds.iobLane.height() * 0.65f)
        assertTrue(bounds.cobData.height() > bounds.cobLane.height() * 0.65f)
    }

    @Test
    fun `metabolic graph scale lane moves continuously between sides`() {
        val left = mobileMetabolicGraphBounds(400f, 260f, 1f, 24f, 29f, scaleRightBlend = 0f)
        val middle = mobileMetabolicGraphBounds(400f, 260f, 1f, 24f, 29f, scaleRightBlend = 0.5f)
        val right = mobileMetabolicGraphBounds(400f, 260f, 1f, 24f, 29f, scaleRightBlend = 1f)

        assertEquals((left.plotRegion.left + right.plotRegion.left) / 2f, middle.plotRegion.left, 0.001f)
        assertEquals((left.plotRegion.right + right.plotRegion.right) / 2f, middle.plotRegion.right, 0.001f)
    }

    private fun state(
        insulin: InsulinState? = null,
        carbs: CarbState? = null,
        basal: BasalState? = null,
    ) = TherapyDisplayState(receivedAtEpochMs = 1_000L, insulin = insulin, carbs = carbs, basal = basal)
}
