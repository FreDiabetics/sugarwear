package app.sugarlicious.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearOverviewRepositoryTest {
    private fun input(time: Long) =
        WearOverviewSnapshot(
            therapy =
                TherapyDisplayState(
                    source = DataSourceId.ANDROID_APS,
                    receivedAtEpochMs = time,
                    glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, measuredAtEpochMs = time, receivedAtEpochMs = time),
                ),
        )

    @Test fun `quiet phone source expires through time alone and retains original input`() =
        runBlocking {
            val repository = AndroidWearOverviewRepository(ApplicationProvider.getApplicationContext<Context>())
            val now = System.currentTimeMillis()
            val snapshot = input(now)
            assertNotNull(repository.resolve(snapshot, now).therapy?.glucose)
            assertNull(repository.resolve(snapshot, now + 16 * 60_000L).therapy?.glucose)
            assertEquals(now, snapshot.therapy?.glucose?.measuredAtEpochMs)
        }

    @Test fun `future source becomes eligible without another storage event`() =
        runBlocking {
            val repository = AndroidWearOverviewRepository(ApplicationProvider.getApplicationContext<Context>())
            val now = System.currentTimeMillis()
            val snapshot = input(now + 6 * 60_000L)
            assertNull(repository.resolve(snapshot, now).therapy?.glucose)
            assertNotNull(repository.resolve(snapshot, now + 6 * 60_000L).therapy?.glucose)
        }
}
