package app.aapswear.g7watch

import android.content.Intent
import app.aapswear.complications.DirectToWatchAmbientClockComplication
import app.aapswear.complications.DirectToWatchClockComplication
import app.aapswear.complications.DirectToWatchHandsComplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7VigilClockUpdatesTest {
    @Test fun `system time events refresh only Vigil time providers during runtime ownership`() {
        val context = RuntimeEnvironment.getApplication()
        val requested = mutableListOf<Class<*>>()
        val updates = G7VigilClockUpdates(context) { requested += it }
        val clocks = listOf(DirectToWatchClockComplication::class.java, DirectToWatchAmbientClockComplication::class.java, DirectToWatchHandsComplication::class.java)
        updates.start()
        updates.start()
        assertEquals("Startup catches up once without duplicate receivers", clocks, requested)
        listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { action ->
            requested.clear()
            context.sendBroadcast(Intent(action))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(action, clocks.take(2), requested)
        }
        updates.stop()
        updates.stop()
        requested.clear()
        context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Destroyed runtime must release its receiver", emptyList<Class<*>>(), requested)
    }

    @Test fun `ordinary active complication ages share existing runtime time receiver`() {
        val context = RuntimeEnvironment.getApplication()
        var refreshes = 0
        val updates = G7VigilClockUpdates(context, refreshComplications = { refreshes++ }, requestUpdate = {})
        updates.start()
        assertEquals(1, refreshes)
        context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, refreshes)
        updates.stop()
        context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, refreshes)
    }
}
