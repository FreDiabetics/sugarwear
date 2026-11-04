package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7PersistedState
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
class G7OverviewRepositoryTest {
    @Test fun `subscription reloads durable state rather than database instance flow`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = G7SensorStateStore(context)
            store.save(G7PersistedState(collectorEnabled = false))
            val repository = AndroidG7OverviewRepository(context, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            assertFalse(
                repository
                    .observe()
                    .first()
                    .persisted.collectorEnabled,
            )
            store.save(G7PersistedState(collectorEnabled = true))
            assertTrue(
                repository
                    .observe()
                    .first()
                    .persisted.collectorEnabled,
            )
        }
}
