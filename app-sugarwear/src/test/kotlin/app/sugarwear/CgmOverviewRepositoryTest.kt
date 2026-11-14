package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmPersistedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class CgmOverviewRepositoryTest {
    @Test fun `visible overview reacts to pending alarms and acknowledgement without polling`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CgmSensorStateStore(context).save(CgmPersistedState(collectorEnabled = true))
            CgmAlertPolicyStore.setLocalEnabled(context, true)
            val repository = AndroidCgmOverviewRepository(context, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            val snapshots = mutableListOf<CgmOverviewSnapshot>()
            backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { repository.observe().collect { snapshots.add(it) } }
            testScheduler.runCurrent()
            assertTrue(snapshots.last().pendingAlarms.isEmpty())
            val now = System.currentTimeMillis()
            CgmAlarmCoordinator.onReading(
                context,
                app.sugarlicious.sensor.protocol
                    .CgmReading("alarm-test", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "test-sensor", "test-session", 65.0, now, now),
                now,
            )
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
            testScheduler.runCurrent()
            assertTrue(snapshots.last().pendingAlarms.any { it.type == app.sugarlicious.sensor.protocol.CgmAlarmType.LOW })
            CgmAlarmCoordinator.acknowledge(context, app.sugarlicious.sensor.protocol.CgmAlarmType.LOW, now)
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
            testScheduler.runCurrent()
            assertTrue(snapshots.last().pendingAlarms.isEmpty())
        }

    @Test fun `subscription reloads durable state rather than database instance flow`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = CgmSensorStateStore(context)
            store.save(CgmPersistedState(collectorEnabled = false))
            val repository = AndroidCgmOverviewRepository(context, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            assertFalse(
                repository
                    .observe()
                    .first()
                    .persisted.collectorEnabled,
            )
            store.save(CgmPersistedState(collectorEnabled = true))
            assertTrue(
                repository
                    .observe()
                    .first()
                    .persisted.collectorEnabled,
            )
        }
}
