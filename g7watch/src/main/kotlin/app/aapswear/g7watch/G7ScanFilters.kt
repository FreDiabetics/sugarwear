package app.aapswear.g7watch

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord

/** A null/empty filter suspends discovery when Wear turns the display off. */
internal fun g7ScanFilters(address: String?): List<ScanFilter> {
    if (!address.isNullOrBlank()) return listOf(ScanFilter.Builder().setDeviceAddress(address).build())
    // G7/Dexcom One names begin with DX. Keep the stricter identity check in the
    // callback; these filters only make discovery eligible for screen-off delivery.
    return listOf(ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE, ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT).map { type ->
        ScanFilter
            .Builder()
            .setAdvertisingDataTypeWithData(type, byteArrayOf(0x44, 0x58), byteArrayOf(-1, -1))
            .build()
    }
}
