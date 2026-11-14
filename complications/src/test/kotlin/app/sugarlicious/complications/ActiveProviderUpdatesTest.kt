package app.sugarlicious.complications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveProviderUpdatesTest {
    @Test fun `unacknowledged discovery is retried while active slots keep updating`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("provider_update_instances", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val provider = GlucoseComplication::class.java
        assertNull(ActiveProviderUpdates.targets(context, provider, 1_000L))
        assertEquals(emptyList<Int>(), ActiveProviderUpdates.targets(context, provider, 1_001L)?.toList())
        assertFalse(context.getSharedPreferences("provider_update_instances", Context.MODE_PRIVATE).contains(provider.name))
        assertNull(ActiveProviderUpdates.targets(context, provider, 61_000L))
        ActiveProviderUpdates.activate(context, provider, 41)
        ActiveProviderUpdates.activate(context, provider, 72)
        assertEquals(listOf(41, 72), ActiveProviderUpdates.targets(context, provider)?.sorted())
        ActiveProviderUpdates.deactivate(context, provider, 41)
        assertEquals(listOf(72), ActiveProviderUpdates.targets(context, provider)?.toList())
        ActiveProviderUpdates.deactivate(context, provider, 72)
        assertTrue(ActiveProviderUpdates.targets(context, provider)!!.isEmpty())
    }

    @Test fun `old unacknowledged empty entries migrate once without losing live IDs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = GlucoseComplication::class.java
        val live = GlucoseTrendComplication::class.java
        val prefs = context.getSharedPreferences("provider_update_instances", Context.MODE_PRIVATE)
        prefs
            .edit()
            .clear()
            .putStringSet(provider.name, emptySet())
            .putStringSet(live.name, setOf("9"))
            .commit()
        assertNull(ActiveProviderUpdates.targets(context, provider, 1_000L))
        assertEquals(listOf(9), ActiveProviderUpdates.targets(context, live)?.toList())
        ActiveProviderUpdates.deactivate(context, provider, 17)
        assertTrue(ActiveProviderUpdates.targets(context, provider, 1_000_000L)!!.isEmpty())
        assertTrue(prefs.contains(provider.name))
    }
}
