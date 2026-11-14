package app.sugarlicious.mobile

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewRangePeriodStoreTest {
    @Test fun `period survives a new UI context and cycles from ninety back to today`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("overview_range_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        var days = OverviewRangePeriodStore.read(context)
        for (expected in listOf(2, 3, 7, 14, 30, 90)) {
            days = OverviewRangePeriodStore.advance(context, days)
            assertEquals(expected, days)
        }
        assertEquals(90, OverviewRangePeriodStore.read(ContextWrapper(context)))
        assertEquals(1, OverviewRangePeriodStore.advance(context, days))
        assertEquals(1, OverviewRangePeriodStore.read(ContextWrapper(context)))
        assertEquals(2, OverviewRangePeriodStore.select(context, 2))
        assertEquals(2, OverviewRangePeriodStore.read(ContextWrapper(context)))
    }
}
