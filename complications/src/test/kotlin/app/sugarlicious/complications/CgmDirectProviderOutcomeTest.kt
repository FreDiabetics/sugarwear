package app.sugarlicious.complications

import android.content.Context
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.protocol.WatchDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmDirectProviderOutcomeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L

    @Test
    fun `successful provider snapshot supplies Watch Direct reading`() {
        val resolved = resolve(DirectProviderOutcome.Success(DirectSnapshot(listOf(reading()), DirectStatus("ACTIVE", "ACTIVE", sensorId = "sensor", sessionId = "session"))))

        assertEquals(DataSourceId.DEXCOM_G7_WATCH, resolved?.source)
        assertEquals(123.0, resolved?.glucose?.valueMgDl ?: 0.0, 0.0)
        assertEquals(-1.6, resolved?.glucose?.trendRateMgDlPerMinute ?: 0.0, 0.0)
        assertTrue(resolved?.sourceContract?.endsWith("PROVIDER_SUCCESS") == true)
    }

    @Test
    fun `watch only retains stale graph history without marking the current source fresh`() {
        val history = listOf(17L, 22L, 45L, 120L).map { minutes -> reading().copy(measuredAt = now - minutes * 60_000L) }
        val resolved = resolve(DirectProviderOutcome.Success(DirectSnapshot(history, DirectStatus("ACTIVE", "ACTIVE", sensorId = "sensor", sessionId = "session"))))
        assertEquals(history.size, resolved?.glucoseHistory?.size)
        assertEquals(app.sugarlicious.model.CgmSourceState.NO_SOURCE, DirectSensorReadingResolver.sourceState(resolved))
        assertTrue(DirectToWatchPresentationFormatter.header(resolved, now).signalLoss)
        for (hours in listOf(1, 2, 3, 6, 12, 24)) {
            assertEquals(history.count { now - it.measuredAt <= hours * 3_600_000L }, DirectToWatchPresentationFormatter.samples(resolved, now, hours).size)
        }
    }

    @Test
    fun `paused collector retains history but cannot supply a current Watch Direct value`() {
        val resolved =
            resolve(
                DirectProviderOutcome.Success(
                    DirectSnapshot(listOf(reading()), DirectStatus("ACTIVE", "UNINITIALIZED", collectorEnabled = false, sensorId = "sensor", sessionId = "session")),
                ),
            )
        assertEquals(app.sugarlicious.model.CgmSourceState.NO_SOURCE, DirectSensorReadingResolver.sourceState(resolved))
        assertEquals(null, resolved?.glucose)
        assertEquals(1, resolved?.glucoseHistory?.size)
        assertEquals("PAUSED", DirectSensorReadingResolver.directSessionState(resolved))
    }

    @Test
    fun `Android provider reads explicit paused lifecycle independently of sensor state`() {
        val outcome =
            AndroidDirectCgmProvider(context) { uri ->
                emptyCursor(uri.lastPathSegment == "state").apply {
                    if (uri.lastPathSegment == "state") addRow(arrayOf<Any>("ACTIVE", "UNINITIALIZED", 0))
                }
            }.read()
        assertEquals(false, (outcome as DirectProviderOutcome.Success).snapshot.status?.collectorEnabled)
    }

    @Test
    fun `empty unavailable denied and malformed outcomes remain distinct and non throwing`() {
        val outcomes =
            listOf(
                DirectProviderOutcome.Empty to "EMPTY",
                DirectProviderOutcome.Failure(ProviderFailureKind.UNAVAILABLE) to "UNAVAILABLE",
                DirectProviderOutcome.Failure(ProviderFailureKind.DENIED) to "DENIED",
                DirectProviderOutcome.Failure(ProviderFailureKind.MALFORMED) to "MALFORMED",
            )

        outcomes.forEach { (outcome, code) ->
            context
                .getSharedPreferences("direct_provider_diagnostics", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            val resolved = resolve(outcome)
            assertEquals(null, resolved)
            assertEquals(
                code,
                context.getSharedPreferences("direct_provider_diagnostics", Context.MODE_PRIVATE).getString("last_code", null),
            )
        }
    }

    @Test
    fun `Android provider classifies empty unavailable denied and malformed cursors`() {
        val empty = AndroidDirectCgmProvider(context) { uri -> emptyCursor(uri.lastPathSegment == "state") }.read()
        val unavailable = AndroidDirectCgmProvider(context) { throw UnsupportedOperationException("offline") }.read()
        val denied = AndroidDirectCgmProvider(context) { throw SecurityException("denied") }.read()
        val malformed =
            AndroidDirectCgmProvider(context) {
                MatrixCursor(arrayOf("unexpected")).apply { addRow(arrayOf("value")) }
            }.read()

        assertEquals(DirectProviderOutcome.Empty, empty)
        assertEquals(DirectProviderOutcome.Failure(ProviderFailureKind.UNAVAILABLE), unavailable)
        assertEquals(DirectProviderOutcome.Failure(ProviderFailureKind.DENIED), denied)
        assertEquals(DirectProviderOutcome.Failure(ProviderFailureKind.MALFORMED), malformed)
    }

    private fun emptyCursor(status: Boolean): MatrixCursor =
        MatrixCursor(
            if (status) {
                arrayOf("sensor_state", "session_state", "collector_enabled")
            } else {
                arrayOf("status", "glucose", "sensor_id", "session_id", "sequence_number", "measured_at", "received_at", "delta", "trend", "trend_rate")
            },
        )

    private fun resolve(outcome: DirectProviderOutcome): TherapyDisplayState? =
        DirectSensorReadingResolver.resolve(
            context = context,
            fallback = null,
            nowEpochMs = now,
            dataSource = WatchDataSource.DEXCOM_G7_WATCH,
            provider = DirectCgmProvider { outcome },
        )

    private fun reading() =
        DirectReading(
            sensorId = "sensor",
            sessionId = "session",
            sequenceNumber = 7L,
            value = 123.0,
            measuredAt = now - 60_000L,
            receivedAt = now,
            delta = 1.0,
            trend = Trend.FLAT,
            quality = CgmQuality.VALID,
            trendRateMgDlPerMinute = -1.6,
        )
}
