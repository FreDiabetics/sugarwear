package app.aapswear.g7watch

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class G7ScanFiltersTest {
    @Suppress("DEPRECATION")
    private fun advertisement(name: String?, type: Int = ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE): ScanResult {
        val data = name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val bytes = if (name == null) byteArrayOf(0) else byteArrayOf((data.size + 1).toByte(), type.toByte()) + data + byteArrayOf(0)
        val record = ReflectionHelpers.callStaticMethod<ScanRecord>(ScanRecord::class.java, "parseFromBytes", ClassParameter.from(ByteArray::class.java, bytes))
        return ScanResult(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"), record, -60, 0)
    }

    @Test
    fun `initial discovery has nonempty filters covering complete and shortened Dexcom names`() {
        val filters = g7ScanFilters(null)
        assertFalse(filters.isEmpty())
        for (type in listOf(ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE, ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT)) {
            for (name in listOf("DXCM1234", "DX011234", "DX021234")) {
                assertTrue(filters.any { it.matches(advertisement(name, type)) })
            }
        }
        assertFalse(filters.any { it.matches(advertisement("Unrelated")) })
    }

    @Test
    fun `known address filter accepts nameless target but rejects different peers`() {
        val advertisement = advertisement(null)
        assertTrue(g7ScanFilters("00:11:22:33:44:55").single().matches(advertisement))
        assertFalse(g7ScanFilters("00:11:22:33:44:66").single().matches(advertisement))
    }
}
