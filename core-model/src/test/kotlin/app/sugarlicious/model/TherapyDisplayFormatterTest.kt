package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TherapyDisplayFormatterTest {
    @Test
    fun `graph thresholds share the glucose unit format without constructing a synthetic reading`() {
        assertEquals("180", TherapyDisplayFormatter.glucose(180.0, GlucoseUnit.MG_DL))
        assertEquals("10.0", TherapyDisplayFormatter.glucose(180.0, GlucoseUnit.MMOL_L))
    }

    @Test
    fun `formats every AndroidAPS therapy field with one canonical visible contract`() {
        val semantics = TherapyBridgeDisplaySemantics()

        assertEquals("-0.10U", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.IOB, -0.1, semantics))
        assertEquals("0.00U", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.BOLUS_IOB, 0.0, semantics))
        assertEquals("1.24U", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.BASAL_IOB, 1.235, semantics))
        assertEquals("12g", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.COB, 12.49, semantics))
        assertEquals("5g", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.FUTURE_CARBS, 5.0, semantics))
        assertEquals("0.90U/h", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.BASAL, 0.9, semantics))
        assertEquals("1.10U/h", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.TEMP_BASAL, 1.1, semantics))
        assertEquals("0.0120U/min", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.INSULIN_ACTIVITY, 0.012, semantics))
        assertEquals("5.0h", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.DIA, 5.0, semantics))
        assertEquals("120U", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.RESERVOIR, 120.4, semantics))
        assertEquals("75%", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.BATTERY, 75.0, semantics))
    }

    @Test
    fun `canonical AndroidAPS formatter preserves finite sign and rejects only unavailable numbers`() {
        val semantics = TherapyBridgeDisplaySemantics()

        assertEquals("-0.00U", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.IOB, -0.0, semantics))
        assertEquals("—", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.IOB, null, semantics))
        assertEquals("—", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.IOB, Double.NaN, semantics))
        assertEquals("—", TherapyBridgeDisplayFormatter.format(TherapyBridgeDisplayField.IOB, Double.POSITIVE_INFINITY, semantics))
    }

    @Test
    fun `formats mgdl and mmol without locale-dependent separators`() {
        assertEquals("123", TherapyDisplayFormatter.glucose(glucose(123.4, GlucoseUnit.MG_DL)))
        assertEquals("6.9", TherapyDisplayFormatter.glucose(glucose(124.2, GlucoseUnit.MMOL_L)))
        assertEquals("+5", TherapyDisplayFormatter.signedDelta(5.0, GlucoseUnit.MG_DL))
        assertEquals("-0.3", TherapyDisplayFormatter.signedDelta(-5.4, GlucoseUnit.MMOL_L))
    }

    @Test
    fun `maps all trends and suppresses unknown trend`() {
        val expected = listOf("⇊", "↓", "↘", "→", "↗", "↑", "⇈", "")
        assertEquals(expected, Trend.entries.map(TherapyDisplayFormatter::trendArrow))
    }

    @Test
    fun `formats missing values and future timestamps safely`() {
        assertEquals("—", TherapyDisplayFormatter.units(null, "U", 2))
        assertEquals("—", TherapyDisplayFormatter.percent(null))
        assertEquals("0m", TherapyDisplayFormatter.ageMinutes(2_000L, 1_000L))
        assertEquals(0L, TherapyDisplayFormatter.ageMinutesValue(2_000L, 1_000L))
        assertEquals("—", TherapyDisplayFormatter.target(null, GlucoseUnit.MG_DL))
    }

    @Test
    fun `IOB preserves negative zero and positive AndroidAPS values and only hides invalid data`() {
        assertEquals("-0.10U", TherapyDisplayFormatter.iob(-0.1, "U", 2))
        assertEquals("0.00U", TherapyDisplayFormatter.iob(0.0, "U", 2))
        assertEquals("1.25U", TherapyDisplayFormatter.iob(1.25, "U", 2))
        assertEquals("—", TherapyDisplayFormatter.iob(null, "U", 2))
        assertEquals("—", TherapyDisplayFormatter.iob(Double.NaN, "U", 2))
        assertEquals("—", TherapyDisplayFormatter.iob(Double.POSITIVE_INFINITY, "U", 2))
    }

    @Test
    fun `formats target bounds in selected unit`() {
        val target = TargetState(lowMgDl = 72.0, highMgDl = 180.0)
        assertEquals("72–180", TherapyDisplayFormatter.target(target, GlucoseUnit.MG_DL))
        assertEquals("4.0–10.0", TherapyDisplayFormatter.target(target, GlucoseUnit.MMOL_L))
    }

    @Test
    fun `shares canonical freshness displayability and labels`() {
        val now = 20L * 60_000L
        assertEquals(Freshness.CURRENT, TherapyDisplayFormatter.freshness(stateAt(now - 5 * 60_000L), now))
        assertEquals(Freshness.DELAYED, TherapyDisplayFormatter.freshness(stateAt(now - 8 * 60_000L), now))
        assertEquals(Freshness.STALE, TherapyDisplayFormatter.freshness(stateAt(now - 13 * 60_000L), now))
        assertEquals(Freshness.NO_DATA, TherapyDisplayFormatter.freshness(null, now))
        assertEquals("AKTUELL", TherapyDisplayFormatter.freshnessLabel(Freshness.CURRENT))
        assertEquals("VERZÖGERT", TherapyDisplayFormatter.freshnessLabel(Freshness.DELAYED))
        assertEquals("VERALTET", TherapyDisplayFormatter.freshnessLabel(Freshness.STALE))
        assertEquals("SENSORFEHLER", TherapyDisplayFormatter.freshnessLabel(Freshness.ERROR))
        assertEquals("KEINE DATEN", TherapyDisplayFormatter.freshnessLabel(Freshness.NO_DATA))
        assertTrue(TherapyDisplayFormatter.isGlucoseDisplayable(stateAt(now - 8 * 60_000L), now))
        assertTrue(TherapyDisplayFormatter.isGlucoseDisplayable(stateAt(now - 13 * 60_000L), now))
        assertTrue(TherapyDisplayFormatter.isGlucoseDisplayable(stateAt(now - 16 * 60_000L), now))
        assertFalse(
            TherapyDisplayFormatter.isGlucoseDisplayable(
                stateAt(now).copy(glucose = stateAt(now).glucose?.copy(quality = CgmQuality.SENSOR_ERROR)),
                now,
            ),
        )
        assertEquals(
            Freshness.ERROR,
            TherapyDisplayFormatter.freshness(
                stateAt(now).copy(glucose = stateAt(now).glucose?.copy(quality = CgmQuality.SENSOR_ERROR)),
                now,
            ),
        )
        assertFalse(
            TherapyDisplayFormatter.isGlucoseDisplayable(
                stateAt(now).copy(glucose = stateAt(now).glucose?.copy(valueMgDl = Double.NaN)),
                now,
            ),
        )
    }

    @Test
    fun `uses stable source labels across display surfaces`() {
        assertEquals("Watch Direct", TherapyDisplayFormatter.sourceName(DataSourceId.DEXCOM_G7_WATCH))
        assertEquals("AndroidAPS", TherapyDisplayFormatter.sourceName(DataSourceId.ANDROID_APS))
        assertEquals("Nightscout", TherapyDisplayFormatter.sourceName(DataSourceId.NIGHTSCOUT))
        assertEquals("Andere Quelle", TherapyDisplayFormatter.sourceName(DataSourceId.OTHER))
        assertEquals("Keine Quelle", TherapyDisplayFormatter.sourceName(null))
    }

    private fun glucose(
        valueMgDl: Double,
        unit: GlucoseUnit,
    ) = GlucoseState(
        valueMgDl = valueMgDl,
        displayUnit = unit,
        trend = Trend.FLAT,
        measuredAtEpochMs = 1L,
        deltaMgDl = null,
        averageDeltaMgDl = null,
    )

    private fun stateAt(timestamp: Long) =
        TherapyDisplayState(
            source = DataSourceId.ANDROID_APS,
            receivedAtEpochMs = timestamp,
            glucose =
                GlucoseState(
                    valueMgDl = 123.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FLAT,
                    measuredAtEpochMs = timestamp,
                ),
        )
}
