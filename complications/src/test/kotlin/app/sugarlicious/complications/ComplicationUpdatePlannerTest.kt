package app.sugarlicious.complications

import app.sugarlicious.model.CarbState
import app.sugarlicious.model.DeviceState
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.PumpState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationUpdatePlannerTest {
    @Test
    fun `sensor lifecycle change refreshes warning footer and analog header without a glucose change`() {
        val old = state().copy(sourceContract = "CANONICAL_CGM_V2:WATCH_DIRECT:test:SENSOR_ACTIVE")
        val changed = old.copy(sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_NOT_ACTIVE")
        val providers = ComplicationUpdatePlanner.affectedProviders(old, changed)
        assertTrue(DirectToWatchClockComplication::class.java in providers)
        assertTrue(DirectToWatchAnalogHeaderComplication::class.java in providers)
        assertTrue(DirectToWatchGraphComplication::class.java in providers)
    }

    @Test
    fun `managed provider set includes fixed direct to watch providers`() {
        val providers = ComplicationUpdatePlanner.allManagedProviders

        assertTrue(DirectToWatchHeaderComplication::class.java in providers)
        assertTrue(DirectToWatchGraphComplication::class.java in providers)
        assertTrue(DirectToWatchStatusComplication::class.java in providers)
        assertTrue(DirectToWatchClockComplication::class.java in providers)
        assertTrue(DirectToWatchAmbientClockComplication::class.java in providers)
        assertTrue(DirectToWatchAmbientHeaderComplication::class.java in providers)
        assertTrue(DirectToWatchAmbientGraphComplication::class.java in providers)
        assertEquals(providers.size, providers.distinct().size)
    }

    @Test
    fun `minute refresh includes every glucose trend graph and Vigil surface`() {
        val providers = ComplicationUpdatePlanner.timeSensitiveProviders

        assertTrue(GlucoseRangedValueComplication::class.java in providers)
        assertTrue(GlucoseTrendRangedValueComplication::class.java in providers)
        assertTrue(TrendOnlyComplication::class.java in providers)
        assertTrue(GlucoseGraphComplication::class.java in providers)
        assertTrue(DirectToWatchHeaderComplication::class.java in providers)
        assertTrue(DirectToWatchGraphComplication::class.java in providers)
        assertTrue(DirectToWatchAmbientHeaderComplication::class.java in providers)
        assertTrue(DirectToWatchAmbientGraphComplication::class.java in providers)
        assertEquals(providers.size, providers.distinct().size)
    }

    @Test
    fun `identical state requests no provider updates`() {
        val state = state()
        assertTrue(ComplicationUpdatePlanner.affectedProviders(state, state).isEmpty())
    }

    @Test
    fun `glucose changes update regular and G6 glucose graph providers`() {
        val old = state()
        val new = old.copy(glucose = old.glucose!!.copy(valueMgDl = 124.0))
        val affected = ComplicationUpdatePlanner.affectedProviders(old, new)

        assertTrue(GlucoseComplication::class.java in affected)
        assertTrue(GlucoseGraphComplication::class.java in affected)
        assertTrue(DirectToWatchHeaderComplication::class.java in affected)
        assertTrue(DirectToWatchGraphComplication::class.java in affected)
        assertTrue(DirectToWatchStatusComplication::class.java in affected)
        assertFalse(IobComplication::class.java in affected)
        assertFalse(CobComplication::class.java in affected)
        assertFalse(BasalComplication::class.java in affected)
    }

    @Test
    fun `IOB changes update IOB and combined providers only`() {
        val old = state()
        val new = old.copy(insulin = InsulinState(totalIob = 2.1))
        val affected = ComplicationUpdatePlanner.affectedProviders(old, new)

        assertEquals(
            setOf(
                IobComplication::class.java,
                IobRangedValueComplication::class.java,
                IobCobComplication::class.java,
                IobCobLongTextComplication::class.java,
                IobCobBasalComplication::class.java,
                IobCobBasalLongTextComplication::class.java,
                TherapyStatusComplication::class.java,
            ),
            affected.toSet(),
        )
    }

    @Test
    fun `pump and phone battery providers receive their own updates`() {
        val old = state()

        val pumpAffected =
            ComplicationUpdatePlanner.affectedProviders(
                old,
                old.copy(pump = PumpState(reservoirUnits = 120.0, batteryPercent = 72)),
            )
        assertTrue(PumpBatteryComplication::class.java in pumpAffected)
        assertTrue(ReservoirComplication::class.java in pumpAffected)
        assertFalse(PhoneBatteryComplication::class.java in pumpAffected)

        val phoneAffected =
            ComplicationUpdatePlanner.affectedProviders(
                old,
                old.copy(device = DeviceState(phoneBatteryPercent = 85)),
            )
        assertEquals(listOf(PhoneBatteryComplication::class.java), phoneAffected)
    }

    @Test
    fun `history changes update regular and G6 graph without glucose header`() {
        val old = state()
        val new =
            old.copy(
                glucoseHistory =
                    old.glucoseHistory +
                        GlucoseSample(
                            valueMgDl = 124.0,
                            measuredAtEpochMs = 2L,
                        ),
            )
        val affected = ComplicationUpdatePlanner.affectedProviders(old, new)

        assertTrue(GlucoseGraphComplication::class.java in affected)
        assertTrue(DirectToWatchGraphComplication::class.java in affected)
        assertTrue(TirComplication::class.java in affected)
        assertFalse(GlucoseComplication::class.java in affected)
        assertFalse(DirectToWatchHeaderComplication::class.java in affected)
    }

    private fun state() =
        TherapyDisplayState(
            receivedAtEpochMs = 1L,
            glucose =
                GlucoseState(
                    valueMgDl = 123.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    measuredAtEpochMs = 1L,
                ),
            glucoseHistory = listOf(GlucoseSample(123.0, 1L)),
            insulin = InsulinState(totalIob = 1.2),
            carbs = CarbState(cobGrams = 15.0),
        )
}
