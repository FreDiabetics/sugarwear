package app.sugarwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Read-only, platform-owned Bluetooth bond inventory. A successful GATT connection is never
 * interpreted as a Bluetooth bond; only BluetoothDevice.bondState is authoritative.
 * Unrecorded peers are diagnostic evidence, never candidates for automatic removal.
 */
internal data class CgmBluetoothBondSnapshot(
    val activeBondState: String,
    val knownSensorCount: Int,
    val otherG7BondCount: Int,
    val otherG7Devices: List<String>,
    val warning: String? = null,
)

internal fun cgmBondStateLabel(state: Int?): String =
    when (state) {
        BluetoothDevice.BOND_NONE -> "Nicht gekoppelt (BOND_NONE)"
        BluetoothDevice.BOND_BONDING -> "Kopplung läuft (BOND_BONDING)"
        BluetoothDevice.BOND_BONDED -> "Gekoppelt (BOND_BONDED)"
        else -> "Nicht ermittelbar"
    }

/** Never expose a full BLE address in the bond inventory UI. */
internal fun cgmMaskedBluetoothAddress(address: String): String {
    val parts = address.split(':')
    return if (parts.size == 6 && parts.all { it.length == 2 && it.all(Char::isLetterOrDigit) }) {
        "••:••:••:••:${parts[4].uppercase(Locale.ROOT)}:${parts[5].uppercase(Locale.ROOT)}"
    } else {
        "Adresse nicht darstellbar"
    }
}

internal fun cgmBondInventory(
    recordedAddresses: Set<String>,
    bondedDevices: List<Pair<String, String?>>,
    activeBondState: Int?,
): CgmBluetoothBondSnapshot {
    val normalized = recordedAddresses.map { it.uppercase(Locale.ROOT) }.toSet()
    val otherG7 =
        bondedDevices
            .filter { (address, name) -> address.uppercase(Locale.ROOT) !in normalized && isCgmAdvertisedName(name) }
            .distinctBy { it.first.uppercase(Locale.ROOT) }
    return CgmBluetoothBondSnapshot(
        activeBondState = if (normalized.size > 1) "Widersprüchliche Sensoradressen" else cgmBondStateLabel(activeBondState),
        knownSensorCount = normalized.size,
        otherG7BondCount = otherG7.size,
        otherG7Devices = otherG7.map { (address, name) -> "${name ?: "G7"} · ${cgmMaskedBluetoothAddress(address)}" },
        warning = if (normalized.size > 1) "Sensoridentität widersprüchlich: keine automatische Entkopplung zulässig." else null,
    )
}

@SuppressLint("MissingPermission")
internal fun readCgmBluetoothBondSnapshot(context: Context): CgmBluetoothBondSnapshot {
    val app = context.applicationContext
    val recorded = cgmRecordedSensorAddresses(app)
    if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return cgmBondInventory(recorded, emptyList(), null)
            .copy(warning = "Bluetooth-Zugriff fehlt; Bond-Status nicht ermittelbar.")
    }
    return try {
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return cgmBondInventory(recorded, emptyList(), null).copy(warning = "Bluetooth nicht verfügbar.")
        val bondState =
            recorded.singleOrNull()?.let { address ->
                adapter.getRemoteDevice(address).bondState
            }
        val devices = adapter.bondedDevices.orEmpty().map { it.address to it.name }
        cgmBondInventory(recorded, devices, bondState)
    } catch (_: SecurityException) {
        cgmBondInventory(recorded, emptyList(), null).copy(warning = "Android verweigert die Bluetooth-Bond-Abfrage.")
    } catch (_: IllegalArgumentException) {
        cgmBondInventory(recorded, emptyList(), null).copy(warning = "Gespeicherte Sensoradresse ist ungültig.")
    }
}
