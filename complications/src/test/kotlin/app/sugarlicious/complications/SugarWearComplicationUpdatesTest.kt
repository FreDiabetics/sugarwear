package app.sugarlicious.complications

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SugarWearComplicationUpdatesTest {
    @Test fun `collector updates every ordinary provider only in standalone SugarWear`() {
        val base = ApplicationProvider.getApplicationContext<Context>()

        fun context(name: String) =
            object : ContextWrapper(base) {
                override fun getPackageName() = name
            }
        val requests = mutableListOf<Class<*>>()
        SugarWearComplicationUpdates.request(context("app.sugarwear")) { _, type -> requests += type }
        assertEquals(setOf(GlucoseComplication::class.java, GlucoseLongTextComplication::class.java, GlucoseRangedValueComplication::class.java, GlucoseTrendComplication::class.java, GlucoseTrendLongTextComplication::class.java, GlucoseTrendRangedValueComplication::class.java, TrendOnlyComplication::class.java, GlucoseDeltaComplication::class.java), requests.toSet())
        requests.clear()
        SugarWearComplicationUpdates.request(context("app.sugarlicious")) { _, type -> requests += type }
        assertTrue(requests.isEmpty())
    }
}
