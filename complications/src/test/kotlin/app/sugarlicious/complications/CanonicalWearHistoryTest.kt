package app.sugarlicious.complications

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalWearHistoryTest {
    private val now = 20_000_000L
    private val phone = GlucoseSample(120.0, now - 5 * 60_000L, DataSourceId.ANDROID_APS)
    private val directGap = GlucoseSample(118.0, now - 2 * 60_000L, DataSourceId.DEXCOM_G7_WATCH)

    @Test
    fun `mobile primary never exposes watch direct as an additional graph dot`() {
        val result = canonicalWearHistory(listOf(phone), listOf(directGap), now, false, DataSourceId.ANDROID_APS)

        assertEquals(listOf(phone), result)
        assertTrue(result.none { it.source == DataSourceId.DEXCOM_G7_WATCH })
    }

    @Test
    fun `watch direct fallback can use local collector history`() {
        val result = canonicalWearHistory(listOf(phone), listOf(directGap), now, true, DataSourceId.DEXCOM_G7_WATCH)

        assertTrue(result.any { it.source == DataSourceId.DEXCOM_G7_WATCH })
    }
}
