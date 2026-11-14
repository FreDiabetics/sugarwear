package app.sugarlicious.wear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WearStartupStateCoordinatorTest {
    @Test fun `rehydration preserves measurement identity and recalculates stale from event time`() {
        val eventAt = 1_000_000L
        val receivedAt = eventAt + 30_000L
        val persisted =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                receivedAtEpochMs = receivedAt,
                glucose =
                    GlucoseState(
                        valueMgDl = 176.0,
                        displayUnit = GlucoseUnit.MG_DL,
                        trend = Trend.SINGLE_UP,
                        measuredAtEpochMs = eventAt,
                        receivedAtEpochMs = receivedAt,
                    ),
            )

        val restored = prepareStartupSnapshot(persisted, eventAt + 20 * 60_000L)

        assertSame(persisted, restored.state)
        assertEquals(eventAt, restored.state?.glucose?.measuredAtEpochMs)
        assertEquals(receivedAt, restored.state?.glucose?.receivedAtEpochMs)
        assertEquals(receivedAt, restored.state?.receivedAtEpochMs)
        assertEquals(Freshness.SIGNAL_LOSS, restored.freshness)
    }

    @Test fun `rehydration keeps explicit no data instead of inventing defaults`() {
        val restored = prepareStartupSnapshot(null, 2_000_000L)

        assertEquals(null, restored.state)
        assertEquals(Freshness.NO_DATA, restored.freshness)
    }

    @Test fun `legacy direct snapshot is removed without mutating configured phone source to other`() {
        val measuredAt = 1_000_000L
        val persisted =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                sourceVersion = "SugarWear",
                sourceContract = "CANONICAL_CGM_V2:WATCH_DIRECT:test",
                receivedAtEpochMs = measuredAt + 5_000L,
                glucose =
                    GlucoseState(
                        valueMgDl = 123.0,
                        displayUnit = GlucoseUnit.MG_DL,
                        measuredAtEpochMs = measuredAt,
                        source = DataSourceId.DEXCOM_G7_WATCH,
                    ),
                glucoseHistory =
                    listOf(
                        app.sugarlicious.model.GlucoseSample(
                            valueMgDl = 123.0,
                            measuredAtEpochMs = measuredAt,
                            source = DataSourceId.DEXCOM_G7_WATCH,
                        ),
                    ),
            )

        val sanitized = persisted.withoutDirectToWatchInput()

        assertEquals(DataSourceId.ANDROID_APS, sanitized.source)
        assertEquals(null, sanitized.glucose)
        assertEquals(emptyList<app.sugarlicious.model.GlucoseSample>(), sanitized.glucoseHistory)
        assertEquals("WEAR_PHONE_ONLY:NO_DIRECT_WATCH_CGM", sanitized.sourceContract)
    }
}
