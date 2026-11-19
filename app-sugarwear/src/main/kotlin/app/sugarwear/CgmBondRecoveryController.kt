package app.sugarwear

import android.Manifest
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface CgmRecoveryResult {
    data object Connected : CgmRecoveryResult

    data object AwaitingAuthentication : CgmRecoveryResult

    data object AwaitingSystemConsent : CgmRecoveryResult

    data object SystemSettingsRequired : CgmRecoveryResult

    data class Failed(
        val reason: String,
    ) : CgmRecoveryResult
}

internal class CgmBondRecoveryController(
    context: Context,
) {
    private val app = context.applicationContext

    suspend fun inspect(): CgmRecoveryState =
        withContext(Dispatchers.IO) {
            if (!hasBluetoothPermissions()) return@withContext CgmRecoveryState.PermissionRequired

            try {
                val adapter =
                    app.getSystemService(BluetoothManager::class.java)?.adapter
                        ?: return@withContext CgmRecoveryState.BluetoothUnavailable
                if (!adapter.isEnabled) return@withContext CgmRecoveryState.BluetoothUnavailable

                val associations = app.getSystemService(CompanionDeviceManager::class.java)?.myAssociations.orEmpty()
                val candidates =
                    adapter.bondedDevices
                        .orEmpty()
                        .filter { isCgmAdvertisedName(it.name) }
                        .map { device ->
                            CgmBondCandidate(
                                address = device.address,
                                advertisedName = device.name,
                                isBonded = true,
                                companionAssociationId =
                                    associations
                                        .firstOrNull {
                                            it.deviceMacAddress?.toString().equals(device.address, ignoreCase = true)
                                        }?.id,
                            )
                        }
                CgmSensorRecoveryPlanner.determine(
                    bluetoothAvailable = true,
                    permissionGranted = true,
                    recordedAddress = cgmRecordedSensorAddresses(app).firstOrNull(),
                    candidates = candidates,
                )
            } catch (error: SecurityException) {
                recordPlatformFailure("inspect", error)
                CgmRecoveryState.PermissionRequired
            }
        }

    suspend fun reconnect(
        candidate: CgmBondCandidate,
        pairingCode: String,
    ): CgmRecoveryResult =
        withContext(Dispatchers.IO) {
            if (!isValidCgmPairingCode(pairingCode)) {
                return@withContext CgmRecoveryResult.Failed("Ungültiger G7-Pairing-Code")
            }
            if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(candidate.address)) {
                return@withContext CgmRecoveryResult.Failed("Ungültige Sensoradresse")
            }

            val sensor =
                try {
                    connectCgmSensorToThisWatch(app, pairingCode)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: SecurityException) {
                    recordPlatformFailure("reconnect", error)
                    return@withContext CgmRecoveryResult.Failed("Bluetooth-Berechtigung fehlt")
                } catch (error: IllegalStateException) {
                    recordPlatformFailure("reconnect", error)
                    return@withContext CgmRecoveryResult.Failed("Sensorverbindung konnte nicht vorbereitet werden")
                } catch (error: java.security.GeneralSecurityException) {
                    recordPlatformFailure("reconnect", error)
                    return@withContext CgmRecoveryResult.Failed("Sensor-Zugangsdaten konnten nicht gelesen werden")
                }

            if (sensor.deviceAddress != null) CgmRecoveryResult.Connected else CgmRecoveryResult.AwaitingAuthentication
        }

    suspend fun repairBond(
        candidate: CgmBondCandidate,
        userConfirmed: Boolean,
    ): CgmRecoveryResult =
        withContext(Dispatchers.IO) {
            if (!userConfirmed) return@withContext CgmRecoveryResult.Failed("Reparatur vom Benutzer nicht bestätigt")
            if (!hasBluetoothPermissions()) return@withContext CgmRecoveryResult.Failed("Bluetooth-Berechtigung fehlt")
            if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(candidate.address)) {
                return@withContext CgmRecoveryResult.Failed("Ungültige Sensoradresse")
            }

            releaseCgmCollectorOwnership(app)

            if (Build.VERSION.SDK_INT < 36) return@withContext CgmRecoveryResult.SystemSettingsRequired
            val associationId = candidate.companionAssociationId ?: return@withContext CgmRecoveryResult.SystemSettingsRequired
            val manager =
                app.getSystemService(CompanionDeviceManager::class.java)
                    ?: return@withContext CgmRecoveryResult.SystemSettingsRequired

            try {
                if (!manager.removeBond(associationId)) return@withContext CgmRecoveryResult.SystemSettingsRequired
                val adapter =
                    app.getSystemService(BluetoothManager::class.java)?.adapter
                        ?: return@withContext CgmRecoveryResult.SystemSettingsRequired
                val device = adapter.getRemoteDevice(candidate.address)
                val removed =
                    awaitCgmBondRemoval(
                        readBondState = { readableCgmBondState({ adapter.state }, { device.bondState }) },
                    )
                if (removed) CgmRecoveryResult.AwaitingAuthentication else CgmRecoveryResult.SystemSettingsRequired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                recordPlatformFailure("repair", error)
                CgmRecoveryResult.Failed("Bluetooth-Berechtigung wurde entzogen")
            } catch (error: IllegalArgumentException) {
                recordPlatformFailure("repair", error)
                CgmRecoveryResult.SystemSettingsRequired
            } catch (error: IllegalStateException) {
                recordPlatformFailure("repair", error)
                CgmRecoveryResult.SystemSettingsRequired
            }
        }

    private fun hasBluetoothPermissions(): Boolean =
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun recordPlatformFailure(operation: String, error: Exception) {
        // Exception messages can include sensor addresses; only record the failure category.
        Log.w("CgmBondRecovery", "$operation failed: ${error.javaClass.simpleName}")
    }
}
