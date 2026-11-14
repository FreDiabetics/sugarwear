package app.sugarlicious.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataCapability
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TherapyStateStoreTest {
    @Test
    fun `remote account reset cannot reimport old account from daily cache`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()
            val retained = GlucoseSample(110.0, now, source = DataSourceId.ANDROID_APS)
            val store = TherapyStateStore(context)
            store.removeSourceHistory(DataSourceId.ANDROID_APS)
            store.removeSourceHistory(DataSourceId.NIGHTSCOUT)
            store.recordHistory(TherapyDisplayState(receivedAtEpochMs = now, glucoseHistory = listOf(retained, retained.copy(source = DataSourceId.NIGHTSCOUT))), now)
            store.removeSourceHistory(DataSourceId.NIGHTSCOUT)
            assertEquals(listOf(retained), store.recordHistory(null, now))
            assertEquals(listOf(retained), store.rangeHistory(now - 60_000, now))
        }

    @Test
    fun `daily tir archive survives new state without extending the live graph history`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()
            val yesterday = GlucoseSample(110.0, now - 30 * 60 * 60_000)
            val state = TherapyDisplayState(receivedAtEpochMs = now, glucoseHistory = listOf(yesterday))
            TherapyStateStore(context).save(state)
            val current = state.copy(glucoseHistory = emptyList())
            TherapyStateStore(context).save(current)
            assertEquals(current, TherapyStateStore(context).state.first())
            assertEquals(listOf(yesterday), TherapyStateStore(context).recordHistory(current, now))
        }

    @Test
    fun `last state survives repository recreation`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val expected =
                TherapyDisplayState(
                    receivedAtEpochMs = 1_234_567L,
                    sourceVersion = "3.4.0-dev",
                    sourceContract = "AAPS_EXTENDED_STATUS_V1",
                    glucose =
                        GlucoseState(
                            valueMgDl = 142.0,
                            displayUnit = GlucoseUnit.MG_DL,
                            trend = Trend.FORTY_FIVE_UP,
                            measuredAtEpochMs = 1_200_000L,
                            deltaMgDl = 4.0,
                            averageDeltaMgDl = 3.0,
                            trendRateMgDlPerMinute = 0.8,
                        ),
                    glucosePredictions =
                        listOf(
                            GlucosePrediction(
                                PredictionKind.IOB,
                                listOf(GlucoseSample(150.0, 1_500_000L)),
                            ),
                        ),
                    capabilities =
                        setOf(
                            DataCapability.GLUCOSE,
                            DataCapability.TREND,
                            DataCapability.PREDICTIONS,
                        ),
                )

            TherapyStateStore(context).save(expected)
            val restored = TherapyStateStore(context).state.first()

            assertNotNull(restored)
            assertEquals(expected, restored)
        }
}
