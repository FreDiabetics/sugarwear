package app.sugarlicious.complications

import android.content.Context
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.*
import app.sugarlicious.protocol.WatchDataSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DirectSessionSafetyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L

    private fun row(sensor: String = "current", session: String = "session") =
        DirectReading(sensor, session, 1, 123.0, now - 60_000L, now, 1.0, Trend.FLAT, CgmQuality.VALID)

    private fun provider(rows: List<DirectReading>, sensor: String? = "current", session: String? = "session", statePresent: Boolean = true): DirectCgmProvider =
        AndroidDirectCgmProvider(context) { uri ->
            if (uri.lastPathSegment == "state") {
                MatrixCursor(arrayOf("sensor_state", "session_state", "collector_enabled", "sensor_id", "session_id")).apply {
                    if (statePresent) addRow(arrayOf<Any?>("ACTIVE", "ACTIVE", 1, sensor, session))
                }
            } else {
                MatrixCursor(arrayOf("status", "glucose", "sensor_id", "session_id", "sequence_number", "measured_at", "received_at", "delta", "trend", "trend_rate")).apply {
                    rows.forEach { addRow(arrayOf<Any?>(it.quality.name, it.value, it.sensorId, it.sessionId, it.sequenceNumber, it.measuredAt, it.receivedAt, it.delta, it.trend.name, null)) }
                }
            }
        }

    private fun resolve(provider: DirectCgmProvider, mode: WatchDataSource = WatchDataSource.DEXCOM_G7_WATCH, mobileMinutesAgo: Long? = null): TherapyDisplayState? {
        val phone =
            mobileMinutesAgo?.let {
                TherapyDisplayState(receivedAtEpochMs = now, glucose = GlucoseState(115.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now - it * 60_000L))
            }
        return DirectSensorReadingResolver.resolve(context, phone, now, mode, provider)
    }

    @Test fun `previous sensor stays history after a new sensor is prepared`() {
        val state = resolve(provider(listOf(row("previous", "previous-session"))))
        assertNull(state?.glucose)
        assertEquals(CgmSourceState.NO_SOURCE, DirectSensorReadingResolver.sourceState(state))
        assertEquals(1, state?.glucoseHistory?.size)
    }

    @Test fun `missing partial and mismatched session identities fail closed`() {
        for (provider in listOf(provider(listOf(row()), statePresent = false), provider(listOf(row()), sensor = null), provider(listOf(row()), session = null), provider(listOf(row()), session = "new-session"))) {
            assertNull(resolve(provider)?.glucose)
        }
    }

    @Test fun `a previous sensor error cannot replace the current sensor value`() {
        val current = row().copy(measuredAt = now - 2 * 60_000L)
        val oldError = row("previous", "previous-session").copy(quality = CgmQuality.SENSOR_ERROR)
        val result = resolve(provider(listOf(current, oldError)))
        assertEquals(CgmQuality.VALID, result?.glucose?.quality)
        assertEquals("current", result?.glucose?.sensorId)
    }

    @Test fun `invalid receipt timestamp cannot reappear as watch-only fallback`() {
        val result = resolve(provider(listOf(row().copy(receivedAt = now - 60 * 60_000L))))
        assertNull(result?.glucose)
    }

    @Test fun `direct-only complication reads do not reset automatic mobile recovery`() {
        val provider = provider(listOf(row()))
        assertEquals(CgmSourceState.WATCH_DIRECT, DirectSensorReadingResolver.sourceState(resolve(provider, WatchDataSource.AUTOMATIC, 16)))
        assertEquals(CgmSourceState.MOBILE_RECOVERY, DirectSensorReadingResolver.sourceState(resolve(provider, WatchDataSource.AUTOMATIC, 2)))
        resolve(provider)
        assertEquals(CgmSourceState.MOBILE_PRIMARY, DirectSensorReadingResolver.sourceState(resolve(provider, WatchDataSource.AUTOMATIC, 0)))
    }

    @Test fun `automatic recovery survives a persisted no-source interval`() {
        val available = provider(listOf(row()))
        resolve(available, WatchDataSource.AUTOMATIC, 16)
        val unavailable = provider(emptyList())
        resolve(unavailable, WatchDataSource.AUTOMATIC, 16)
        val first = resolve(unavailable, WatchDataSource.AUTOMATIC, 1)
        assertNull(first?.glucose)
        assertEquals(CgmSourceState.NO_SOURCE, DirectSensorReadingResolver.sourceState(first))
        assertEquals(CgmSourceState.MOBILE_PRIMARY, DirectSensorReadingResolver.sourceState(resolve(unavailable, WatchDataSource.AUTOMATIC, 0)))
    }
}
