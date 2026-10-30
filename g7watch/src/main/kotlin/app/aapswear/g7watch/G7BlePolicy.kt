package app.aapswear.g7watch

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import app.aapswear.g7.G7Sensor
import java.util.UUID

internal enum class G7ReconnectStrategy { BOUNDED_SCAN, KNOWN_ADDRESS_DIRECT }

internal fun shouldUseDirectReconnect(strategy: G7ReconnectStrategy, address: String?): Boolean =
    strategy == G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT && !address.isNullOrBlank()

internal fun shouldUseFallbackDiscovery(
    strategy: G7ReconnectStrategy,
    address: String?,
    fallbackUsed: Boolean,
    recoverable: Boolean,
): Boolean = !fallbackUsed && recoverable && shouldUseDirectReconnect(strategy, address)

internal fun shouldRetryNoCallbackDirectly(errorCode: String, retriesUsed: Int, fallbackUsed: Boolean): Boolean =
    errorCode in
        setOf(
            G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE,
            G7_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE,
            G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE,
        ) &&
        retriesUsed < 1 &&
        !fallbackUsed

internal fun g7ScanTimeoutMs(sensor: G7Sensor): Long =
    if (sensor.deviceAddress.isNullOrBlank()) G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS else G7_RECONNECT_SCAN_TIMEOUT_MS

internal fun knownG7AddressMatches(knownAddress: String?, candidateAddress: String): Boolean? =
    knownAddress?.takeIf { it.isNotBlank() }?.equals(candidateAddress, ignoreCase = true)

internal fun isConnectableG7Advertisement(connectable: Boolean): Boolean = connectable

internal fun usableG7SharedKey(sharedKey: ByteArray?, bondState: Int?): ByteArray? =
    if (sharedKey != null && bondState == BluetoothDevice.BOND_NONE) null else sharedKey

internal fun shouldResumeG7Pairing(sharedKey: ByteArray?, bondState: Int?): Boolean =
    sharedKey != null && bondState == BluetoothDevice.BOND_NONE

internal enum class G7WriteCallbackDisposition {
    EXPECTED_SUCCESS,
    EXPECTED_FAILURE,
    STALE_SUCCESS,
    STALE_FAILURE,
}

internal fun classifyG7WriteCallback(expectedUuid: UUID, actualUuid: UUID, status: Int): G7WriteCallbackDisposition =
    when {
        actualUuid == expectedUuid && status == BluetoothGatt.GATT_SUCCESS -> G7WriteCallbackDisposition.EXPECTED_SUCCESS
        actualUuid == expectedUuid -> G7WriteCallbackDisposition.EXPECTED_FAILURE
        status == BluetoothGatt.GATT_SUCCESS -> G7WriteCallbackDisposition.STALE_SUCCESS
        else -> G7WriteCallbackDisposition.STALE_FAILURE
    }

internal fun shouldFailCurrentG7Write(disposition: G7WriteCallbackDisposition): Boolean =
    disposition == G7WriteCallbackDisposition.EXPECTED_FAILURE

internal fun copyG7NotificationValue(value: ByteArray): ByteArray = value.copyOf()

internal fun g7DiscoveryScanTimeoutMs(
    sensor: G7Sensor,
    overrideMs: Long?,
    pairingRecoveryRequired: Boolean,
    fallbackUsed: Boolean,
): Long =
    when {
        // A saved address/key can precede Android consent. Until that bond completes,
        // short known-peer scans can repeatedly miss the actual five-minute radio window.
        // The caller's original pairing deadline still bounds the entire collection call.
        overrideMs != null && pairingRecoveryRequired -> overrideMs.coerceIn(1L, G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS)
        overrideMs != null -> overrideMs.coerceIn(5_000L, g7ScanTimeoutMs(sensor))
        pairingRecoveryRequired -> G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS
        fallbackUsed -> G7_FALLBACK_SCAN_TIMEOUT_MS
        else -> g7ScanTimeoutMs(sensor)
    }
