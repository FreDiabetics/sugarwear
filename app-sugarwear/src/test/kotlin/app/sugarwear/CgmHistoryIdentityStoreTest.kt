package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmHistoryIdentityStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `established authenticated session survives wall clock corrections`() {
        val store = CgmHistoryIdentityStore(context)
        val start = 10_000_000L
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", start, "old", "old-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", start + 1_000, "new", "new-session")
        val preferences = context.getSharedPreferences("g7_history_identity", Context.MODE_PRIVATE)
        val originalBindings = preferences.getString("bindings", "")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", start + 600_000, "new", "new-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", start - 600_000, "new", "new-session")
        assertEquals("old" to "old-session", store.canonical("new", "new-session"))
        assertEquals(originalBindings, preferences.getString("bindings", ""))
    }

    @Test(expected = IllegalStateException::class)
    fun `established session rejects a different authenticated device`() {
        val store = CgmHistoryIdentityStore(context)
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", 100_000, "old", "old-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:02", 100_000, "old", "old-session")
    }

    @Test fun `authenticated reconnect recovers historical stream without storing a reconnect address`() {
        val store = CgmHistoryIdentityStore(context)
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", 100_000, "old", "old-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", 101_000, "new", "new-session")
        assertEquals("old" to "old-session", CgmHistoryIdentityStore(context).canonical("new", "new-session"))
        val raw = context.getSharedPreferences("g7_history_identity", Context.MODE_PRIVATE).getString("bindings", "")!!
        org.junit.Assert.assertFalse(raw.contains("AA:BB:CC"))
    }

    @Test fun `different hardware or different sensor start cannot inherit another stream`() {
        val store = CgmHistoryIdentityStore(context)
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", 100_000, "old", "old-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:02", 100_000, "different", "different-session")
        store.rememberAuthenticated("AA:BB:CC:DD:EE:01", 1_000_000, "replacement", "replacement-session")
        assertEquals("different" to "different-session", store.canonical("different", "different-session"))
        assertEquals("replacement" to "replacement-session", store.canonical("replacement", "replacement-session"))
        assertNotEquals(store.canonical("old", "old-session"), store.canonical("replacement", "replacement-session"))
    }
}
