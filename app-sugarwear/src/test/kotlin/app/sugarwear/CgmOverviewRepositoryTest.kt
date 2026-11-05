package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmPersistedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
