package app.sugarwear

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord

/** Keep a real hardware scan filter: unfiltered BLE discovery stops on Wear screen-off. */
internal fun cgmScanFilters(address: String?): List<ScanFilter> {
    if (!address.isNullOrBlank()) return listOf(ScanFilter.Builder().setDeviceAddress(address).build())
    // The exact DX byte-prefix scan filter is not consistently honored by Wear BLE
    // controllers when the local name arrives in the scan response. Filter for a
    // complete/short local-name field instead and validate DXCM/DX01/DX02 in the
    // existing callback before attempting GATT. This is used only for bounded scans,
    // not for continuous discovery or glucose polling.
    return listOf(ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE, ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT).map { type ->
        ScanFilter
            .Builder()
            .setAdvertisingDataType(type)
            .build()
    }
}
