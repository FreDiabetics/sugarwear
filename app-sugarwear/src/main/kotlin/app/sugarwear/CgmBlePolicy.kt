package app.sugarwear

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmSensor
import java.util.UUID

internal enum class CgmReconnectStrategy { BOUNDED_SCAN, KNOWN_ADDRESS_DIRECT }

internal fun shouldUseDirectReconnect(strategy: CgmReconnectStrategy, address: String?): Boolean =
    strategy == CgmReconnectStrategy.KNOWN_ADDRESS_DIRECT && !address.isNullOrBlank()

internal fun shouldUseFallbackDiscovery(
    strategy: CgmReconnectStrategy,
    address: String?,
    fallbackUsed: Boolean,
    recoverable: Boolean,
): Boolean = !fallbackUsed && recoverable && shouldUseDirectReconnect(strategy, address)

internal fun shouldRetryNoCallbackDirectly(errorCode: String, retriesUsed: Int, fallbackUsed: Boolean): Boolean =
    errorCode in
        setOf(
            CGM_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE,
            CGM_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE,
            CGM_WRITE_CALLBACK_TIMEOUT_ERROR_CODE,
        ) &&
        retriesUsed < 1 &&
        !fallbackUsed

internal fun cgmScanTimeoutMs(sensor: CgmSensor): Long =
    if (sensor.deviceAddress.isNullOrBlank()) CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS else CGM_RECONNECT_SCAN_TIMEOUT_MS

internal fun knownCgmAddressMatches(knownAddress: String?, candidateAddress: String): Boolean? =
    knownAddress?.takeIf { it.isNotBlank() }?.equals(candidateAddress, ignoreCase = true)

internal fun isConnectableCgmAdvertisement(connectable: Boolean): Boolean = connectable

internal fun usableCgmSharedKey(sharedKey: ByteArray?, bondState: Int?): ByteArray? =
    if (sharedKey != null && bondState == BluetoothDevice.BOND_NONE) null else sharedKey

internal fun shouldResumeCgmPairing(sharedKey: ByteArray?, bondState: Int?): Boolean =
    sharedKey != null && bondState == BluetoothDevice.BOND_NONE

internal fun shouldRepairPreexistingCgmBond(
    initialAddress: String?,
    startedWithoutSharedKey: Boolean,
    bondStateBeforeAttempt: Int?,
    lastProtocolState: CgmProtocolState,
    repairAlreadyAttempted: Boolean,
): Boolean =
    !repairAlreadyAttempted &&
        initialAddress.isNullOrBlank() &&
        startedWithoutSharedKey &&
        bondStateBeforeAttempt == BluetoothDevice.BOND_BONDED &&
        lastProtocolState == CgmProtocolState.REQUESTING_GLUCOSE

internal fun shouldRepairPreexistingCgmBondAfterAuthFailure(
    initialAddress: String?,
    startedWithoutSharedKey: Boolean,
    bondStateBeforeAttempt: Int?,
    errorCode: String,
    repairAlreadyAttempted: Boolean,
): Boolean =
    !repairAlreadyAttempted &&
        initialAddress.isNullOrBlank() &&
        startedWithoutSharedKey &&
        bondStateBeforeAttempt == BluetoothDevice.BOND_BONDED &&
        errorCode in setOf("G7-AUTH-204", "G7-AUTH-211")

internal enum class CgmWriteCallbackDisposition {
    EXPECTED_SUCCESS,
    EXPECTED_FAILURE,
    STALE_SUCCESS,
    STALE_FAILURE,
}

internal fun classifyCgmWriteCallback(expectedUuid: UUID, actualUuid: UUID, status: Int): CgmWriteCallbackDisposition =
    when {
        actualUuid == expectedUuid && status == BluetoothGatt.GATT_SUCCESS -> CgmWriteCallbackDisposition.EXPECTED_SUCCESS
        actualUuid == expectedUuid -> CgmWriteCallbackDisposition.EXPECTED_FAILURE
        status == BluetoothGatt.GATT_SUCCESS -> CgmWriteCallbackDisposition.STALE_SUCCESS
        else -> CgmWriteCallbackDisposition.STALE_FAILURE
    }

internal fun shouldFailCurrentCgmWrite(disposition: CgmWriteCallbackDisposition): Boolean =
    disposition == CgmWriteCallbackDisposition.EXPECTED_FAILURE

internal fun copyCgmNotificationValue(value: ByteArray): ByteArray = value.copyOf()

internal fun cgmDiscoveryScanTimeoutMs(
    sensor: CgmSensor,
    overrideMs: Long?,
    pairingRecoveryRequired: Boolean,
    fallbackUsed: Boolean,
): Long =
    when {
        // A saved address/key can precede Android consent. Until that bond completes,
        // short known-peer scans can repeatedly miss the actual five-minute radio window.
        // The caller's original pairing deadline still bounds the entire collection call.
        overrideMs != null && pairingRecoveryRequired -> overrideMs.coerceIn(1L, CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS)
        overrideMs != null -> overrideMs.coerceIn(5_000L, cgmScanTimeoutMs(sensor))
        pairingRecoveryRequired -> CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS
        fallbackUsed -> CGM_FALLBACK_SCAN_TIMEOUT_MS
        else -> cgmScanTimeoutMs(sensor)
    }

/** Retry only the exact recorded peer after a protocol rejection and confirmed absent OS bond. */
internal fun shouldRetryAbsentCgmBond(
    initialAddress: String?,
    candidateAddress: String?,
    bondState: Int?,
    errorCode: String,
    recoveryAlreadyAttempted: Boolean,
): Boolean =
    !recoveryAlreadyAttempted &&
        !initialAddress.isNullOrBlank() &&
        initialAddress.equals(candidateAddress, ignoreCase = true) &&
        bondState == BluetoothDevice.BOND_NONE &&
        errorCode == "G7-AUTH-211"

internal fun cgmBondRepairFailureMessage(bondState: Int?): String =
    when (bondState) {
        BluetoothDevice.BOND_BONDED ->
            "Bluetooth-Kopplung konnte nicht erneuert werden. Falls der Sensor in den Bluetooth-Einstellungen angezeigt wird, dort entkoppeln und erneut verbinden."
        BluetoothDevice.BOND_NONE ->
            "Keine Bluetooth-Kopplung vorhanden. Sensor erneut verbinden."
        BluetoothDevice.BOND_BONDING ->
            "Bluetooth-Kopplung läuft noch. Bitte erneut verbinden, sobald sie beendet ist."
        else ->
            "Bluetooth-Kopplungsstatus nicht ermittelbar. Bluetooth prüfen und erneut verbinden."
    }
