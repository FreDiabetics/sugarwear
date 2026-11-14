package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HistoryDeltaCompletionTest {
    private val now = 10_000_000L

    private fun state(source: DataSourceId = DataSourceId.DEXCOM_G7_APP) =
        TherapyDisplayState(
            source = source,
            receivedAtEpochMs = now,
            glucose = GlucoseState(125.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now, source = source, sensorId = "sensor", sessionId = "session"),
            glucoseHistory = listOf(GlucoseSample(120.0, now - 300_000L, source, "sensor", "session")),
        )

    @Test
    fun `history completes delta immediately for every supported source`() {
        DataSourceId.entries.filter { it != DataSourceId.OTHER }.forEach { source ->
            val result = state(source).withHistoryDerivedDelta(TrendRateProfile.SUGARLICIOUS)
            assertEquals(5.0, result.glucose?.deltaMgDl)
            assertEquals(now, result.glucose?.measuredAtEpochMs)
            assertEquals(ValueProvenance.DERIVED, result.fieldProvenance[CanonicalDataField.DELTA])
            assertEquals(source, result.fieldSources[CanonicalDataField.DELTA])
        }
    }

    @Test
    fun `source supplied delta and trend remain authoritative`() {
        val initial = state().let { it.copy(glucose = it.glucose!!.copy(deltaMgDl = 4.0, trend = Trend.SINGLE_UP)) }
        assertSame(initial, initial.withHistoryDerivedDelta(TrendRateProfile.SUGARLICIOUS))
        val sourceTrend = state().let { it.copy(glucose = it.glucose!!.copy(trend = Trend.SINGLE_UP, trendOrigin = ValueProvenance.SOURCE)) }
        assertEquals(Trend.SINGLE_UP, sourceTrend.withHistoryDerivedDelta(TrendRateProfile.SUGARLICIOUS).glucose?.trend)
    }

    @Test
    fun `gaps source changes and sensor changes do not manufacture delta`() {
        val initial = state()
        val previous = initial.glucoseHistory.single()
        listOf(
            previous.copy(measuredAtEpochMs = now - 1_200_000L),
            previous.copy(source = DataSourceId.NIGHTSCOUT),
            previous.copy(sensorId = "different"),
            previous.copy(sessionId = "different"),
            previous.copy(quality = CgmQuality.INVALID),
        ).forEach { candidate ->
            val value = initial.copy(glucoseHistory = listOf(candidate))
            assertSame(value, value.withHistoryDerivedDelta(TrendRateProfile.SUGARLICIOUS))
        }
    }
}
