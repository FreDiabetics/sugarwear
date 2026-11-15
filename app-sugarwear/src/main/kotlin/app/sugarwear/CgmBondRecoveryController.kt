package app.sugarwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.sugarlicious.sensor.protocol.CgmSensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface CgmRecoveryResult {
    data object Connected : CgmRecoveryResult
    data object AwaitingAuthentication : CgmRecoveryResult
    data object AwaitingSystemConsent : CgmRecoveryResult
    data object SystemSettingsRequired : CgmRecoveryResult
    data class Failed(val reason: String) : CgmRecoveryResult
}

internal class CgmBondRecoveryController(
    private val context: Context,
) {
    private val app = context.applicationContext

    @SuppressLint("MissingPermission")
    suspend fun inspect(): CgmRecoveryState = withContext(Dispatchers.IO) {
        val bluetoothManager = app.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        val bluetoothAvailable = adapter != null && adapter.isEnabled
        val permissionGranted = app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

        if (!bluetoothAvailable) return@withContext CgmRecoveryState.BluetoothUnavailable
        if (!permissionGranted) return@withContext CgmRecoveryState.PermissionRequired

        val cdmManager = app.getSystemService(CompanionDeviceManager::class.java)
        val myAssociations = cdmManager?.myAssociations.orEmpty()

        val bondedDevices = adapter.bondedDevices.orEmpty().filter { isCgmAdvertisedName(it.name) }
        val recordedAddresses = cgmRecordedSensorAddresses(app)
        val primaryRecorded = recordedAddresses.firstOrNull()

        val candidates = bondedDevices.map { device ->
            val associationId = myAssociations.firstOrNull {
                it.deviceMacAddress?.toString().equals(device.address, ignoreCase = true)
            }?.id
            CgmBondCandidate(
                address = device.address,
                advertisedName = device.name,
                isBonded = true,
                companionAssociationId = associationId,
            )
        }

        CgmSensorRecoveryPlanner.determine(
            bluetoothAvailable = true,
            permissionGranted = true,
            recordedAddress = primaryRecorded,
            candidates = candidates,
        )
    }

    suspend fun reconnect(
        candidate: CgmBondCandidate,
        pairingCode: String,
    ): CgmRecoveryResult = withContext(Dispatchers.IO) {
        if (!canStartCgmPairing(null, false, pairingCode)) {
            return@withContext CgmRecoveryResult.Failed("Ungültiger G7-Pairing-Code")
        }
        val sensor = try {
            connectCgmSensorToThisWatch(app, pairingCode)
        } catch (e: Exception) {
            return@withContext CgmRecoveryResult.Failed(e.message ?: "Sensorverbindung fehlgeschlagen")
        }
        if (sensor.deviceAddress != null) {
            CgmRecoveryResult.Connected
        } else {
            CgmRecoveryResult.AwaitingAuthentication
        }
    }

    suspend fun repairBond(
        candidate: CgmBondCandidate,
        userConfirmed: Boolean,
    ): CgmRecoveryResult = withContext(Dispatchers.IO) {
        if (!userConfirmed) {
            return@withContext CgmRecoveryResult.Failed("Reparatur vom Benutzer nicht bestätigt")
        }

        releaseCgmCollectorOwnership(app)

        if (Build.VERSION.SDK_INT >= 36 && candidate.companionAssociationId != null) {
            val cdmManager = app.getSystemService(CompanionDeviceManager::class.java)
            if (cdmManager != null) {
                return@withContext try {
                    cdmManager.removeBond(candidate.companionAssociationId)
                    val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
                    val device = adapter?.getRemoteDevice(candidate.address)
                    val gone = awaitCgmBondRemoval(readBondState = { device?.bondState })
                    if (gone) CgmRecoveryResult.AwaitingAuthentication else CgmRecoveryResult.SystemSettingsRequired
                } catch (e: Exception) {
                    CgmRecoveryResult.SystemSettingsRequired
                }
            }
        }

        // On pre-API 36 or when removal is rejected by platform, inform user to repair in Android settings.
        CgmRecoveryResult.SystemSettingsRequired
    }
}
