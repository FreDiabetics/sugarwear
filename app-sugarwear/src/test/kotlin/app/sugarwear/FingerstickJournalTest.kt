package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FingerstickJournalTest {
    @Test
    fun `entry survives restart and replay retains the original measurement`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FingerstickJournal(context).use { journal ->
            journal.record("first", "5.8", "mmol/l", 1000L)
        }
        FingerstickJournal(context).use { journal ->
            journal.record("first", "115", "mg/dl", 2000L)
            journal.record("second", "115", "mg/dl", 2000L)
            journal.readableDatabase.rawQuery("SELECT value, unit, measured_at FROM fingersticks ORDER BY measured_at", null).use {
                assertEquals(2, it.count)
                it.moveToFirst()
                assertEquals("5.8", it.getString(0))
                assertEquals("mmol/l", it.getString(1))
                assertEquals(1000L, it.getLong(2))
                it.moveToNext()
                assertEquals("115", it.getString(0))
                assertEquals("mg/dl", it.getString(1))
            }
        }
        assertFalse(context.getDatabasePath("g7_readings.db").exists())
    }
}
