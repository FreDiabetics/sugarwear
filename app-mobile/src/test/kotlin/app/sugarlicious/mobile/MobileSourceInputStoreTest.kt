package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileSourceInputStoreTest {
    private val now = 1_800_000_000_000L

    private fun state(source: DataSourceId, session: String, offset: Long = 0L) =
        TherapyDisplayState(
            source = source,
            receivedAtEpochMs = now,
            glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, Trend.FLAT, now + offset, source = source, sessionId = session),
        )

    @Test fun `CloudEndpoint server identities archive independently and reject late old server replies`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = MobileSourceInputStore(context)
            store.clear(DataSourceId.NIGHTSCOUT)
            store.activateRemoteIdentity(DataSourceId.NIGHTSCOUT, "ns-a")
            store.completeRemoteIdentity(DataSourceId.NIGHTSCOUT, "ns-a")
            val old = state(DataSourceId.NIGHTSCOUT, "a").copy(sourceVersion = "ns-a")
            store.merge(old, now)
            store.activateRemoteIdentity(DataSourceId.NIGHTSCOUT, "ns-b")
            assertNull(store.read()[DataSourceId.NIGHTSCOUT])
            store.merge(old, now)
            assertNull(store.read()[DataSourceId.NIGHTSCOUT])
            store.activateRemoteIdentity(DataSourceId.NIGHTSCOUT, "ns-a")
            assertEquals("a", store.read()[DataSourceId.NIGHTSCOUT]!!.glucose!!.sessionId)
        }

    @Test fun `legacy Share history is not assigned to the first bound account`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = MobileSourceInputStore(context)
            store.clear(DataSourceId.DEXCOM_SHARE)
            store.merge(state(DataSourceId.DEXCOM_SHARE, "unscoped"), now)
            assertTrue(store.activateShareIdentity("new-account"))
            assertNull(store.read()[DataSourceId.DEXCOM_SHARE])
        }

    @Test fun `Share account switch archives old data blocks late replies and survives interrupted cleanup`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = MobileSourceInputStore(context)
            store.clear(DataSourceId.DEXCOM_SHARE)
            store.activateShareIdentity("account-a")
            store.completeShareIdentity("account-a")
            val first = state(DataSourceId.DEXCOM_SHARE, "session-a").copy(sourceVersion = "account-a")
            store.merge(first, now)
            assertTrue(store.activateShareIdentity("account-b"))
            assertNull(store.read()[DataSourceId.DEXCOM_SHARE])
            store.merge(first, now)
            assertNull(store.read()[DataSourceId.DEXCOM_SHARE])
            assertTrue(MobileSourceInputStore(context).activateShareIdentity("account-b"))
            store.completeShareIdentity("account-b")
            assertFalse(store.activateShareIdentity("account-b"))
            store.merge(state(DataSourceId.DEXCOM_SHARE, "session-b").copy(sourceVersion = "account-b"), now)
            assertTrue(store.activateShareIdentity("account-a"))
            assertEquals("session-a", store.read()[DataSourceId.DEXCOM_SHARE]!!.glucose!!.sessionId)
        }

    @Test fun `raw streams survive store recreation without overwriting each other`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = MobileSourceInputStore(context)
            store.clear(DataSourceId.ANDROID_APS)
            store.clear(DataSourceId.DEXCOM_G7_APP)
            store.merge(state(DataSourceId.ANDROID_APS, "aaps"), now)
            store.merge(state(DataSourceId.DEXCOM_G7_APP, "g7-app:1000"), now)
            val restored = MobileSourceInputStore(context).read()
            assertEquals("aaps", restored[DataSourceId.ANDROID_APS]!!.glucose!!.sessionId)
            assertEquals("g7-app:1000", restored[DataSourceId.DEXCOM_G7_APP]!!.glucose!!.sessionId)
        }

    @Test fun `a retired sensor session cannot return after restart`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = MobileSourceInputStore(context)
            store.clear(DataSourceId.DEXCOM_G7_APP)
            store.merge(state(DataSourceId.DEXCOM_G7_APP, "g7-app:1000", -600_000), now)
            store.merge(state(DataSourceId.DEXCOM_G7_APP, "g7-app:2000", -300_000), now)
            MobileSourceInputStore(context).merge(state(DataSourceId.DEXCOM_G7_APP, "g7-app:1000"), now)
            assertEquals("g7-app:2000", store.read()[DataSourceId.DEXCOM_G7_APP]!!.glucose!!.sessionId)
        }
}
