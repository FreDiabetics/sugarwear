package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchConnectionDiagnosticsTest {
    private lateinit var context: Context

    @Before
    fun clearDiagnostics() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `an incoming watch message immediately marks the watch connected`() {
        recordWatchContact(context, now = 12_345L)

        val diagnostics = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        assertEquals(1, diagnostics.getInt("reachableWatches", 0))
        assertEquals(12_345L, diagnostics.getLong("lastWatchContactAt", 0L))
        assertTrue(isWatchConnected(diagnostics.getInt("reachableWatches", 0)))
    }

    @Test
    fun `a fresh node query clears stale connected state when the watch is gone`() {
        recordWatchContact(context, now = 12_345L)
        recordReachableWatchCount(context, count = 0, now = 23_456L)

        val diagnostics = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        assertEquals(0, diagnostics.getInt("reachableWatches", 1))
        assertEquals(23_456L, diagnostics.getLong("watchReachabilityCheckedAt", 0L))
        assertFalse(isWatchConnected(diagnostics.getInt("reachableWatches", 1)))
    }

    @Test
    fun `reachable model names come from nodes and are cleared on disconnect`() {
        recordReachableWatchCount(context, 1, names = listOf("Galaxy Watch Ultra"))
        val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        assertEquals("Galaxy Watch Ultra", DiagnosticsSnapshot.read(preferences).connectedWatchModels)
        recordReachableWatchCount(context, 0)
        assertEquals("", DiagnosticsSnapshot.read(preferences).connectedWatchModels)
    }

    @Test
    fun `phone data synchronization alone is not a watch connection signal`() {
        val diagnostics = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        diagnostics.edit().putString("lastSyncStatus", "ok").commit()

        assertFalse(isWatchConnected(diagnostics.getInt("reachableWatches", 0)))
    }
}
