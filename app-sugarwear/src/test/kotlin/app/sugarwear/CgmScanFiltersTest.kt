package app.sugarwear

import android.bluetooth.le.ScanRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmScanFiltersTest {
    @Test
    fun `initial discovery filters name field types and validates G7 identity in callback`() {
        val filters = cgmScanFilters(null)
        assertEquals(2, filters.size)
        assertEquals(
            setOf(ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE, ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT),
            filters.map { it.advertisingDataType }.toSet(),
        )
        // Robolectric does not fully model ScanRecord's advertising-data structures;
        // verify filter configuration directly instead of using ScanFilter.matches().
        assertTrue(filters.all { it.deviceAddress == null })
        for (name in listOf("DXCM1234", "DX011234", "DX021234")) {
            assertTrue(isCgmAdvertisedName(name))
        }
        assertFalse(isCgmAdvertisedName("Unrelated"))
        assertFalse(isCgmAdvertisedName(null))
    }

    @Test
    fun `known address filter targets only the recorded sensor`() {
        val target = "00:11:22:33:44:55"
        val filters = cgmScanFilters(target)
        assertEquals(1, filters.size)
        assertEquals(target, filters.single().deviceAddress)
        assertFalse(filters.single().deviceAddress == "00:11:22:33:44:66")
    }
}
