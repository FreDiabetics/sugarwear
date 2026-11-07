package app.sugarwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSessionManager
import app.sugarlicious.sensor.protocol.CgmSetupPayload

internal data class CgmUnlinkResult(
    val bondRemovalStatus: CgmBondRemovalStatus,
    val localAssociationCleared: Boolean = bondRemovalStatus != CgmBondRemovalStatus.FAILED,
) {
    val bondRemovalAttempted: Boolean get() = bondRemovalStatus != CgmBondRemovalStatus.NOT_REQUIRED
    val bondRemovalRequested: Boolean get() = bondRemovalStatus == CgmBondRemovalStatus.REMOVED
    val detached: Boolean get() = localAssociationCleared
}

internal enum class CgmBondRemovalStatus { NOT_REQUIRED, REMOVED, FAILED }

internal fun interface CgmBondRemover {
    fun remove(
        context: Context,
        address: String?,
    ): CgmBondRemovalStatus
}

internal data class CgmDetachSemantics(
    val stopsCollector: Boolean = true,
    val clearsLocalSession: Boolean = true,
    val preservesHistory: Boolean = true,
    val sendsSensorEndCommand: Boolean = false,
)

internal fun cgmDetachSemantics() = CgmDetachSemantics()

internal fun shouldClearCgmAssociation(status: CgmBondRemovalStatus): Boolean = status != CgmBondRemovalStatus.FAILED

/** Stops this watch as BLE owner without deleting sensor identity, credentials or history. */
internal fun releaseCgmCollectorOwnership(context: Context) {
    val app = context.applicationContext
    val stateStore = CgmSensorStateStore(app)
    val current = stateStore.read()
    stateStore.save(current.copy(collectorEnabled = false, health = CgmCollectorReliability.releasePending(current.health, System.currentTimeMillis())))
    CgmCollectorRuntimeRegistry.cancelLiveCycle()
    SensorScanner.forceCleanup()
    CgmCollectorService.stop(app)
}

/**
 * Prepares this watch to take over the receiver slot. The previous watch still has to release its
 * own bond locally; watches paired to different phones cannot modify each other's Bluetooth store.
 * Collection stays disabled until the user explicitly starts reception.
 */
internal fun moveCgmSensorToThisWatch(
    context: Context,
    pairingCode: String,
    bondRemover: CgmBondRemover = CgmBondRemover(::removeCgmBond),
    saveSetup: (CgmSetupPayload) -> Unit = { CgmCredentialStore(context.applicationContext).saveSetup(it) },
): CgmSensor {
    val payload = CgmSetupPayload(pairingCode)
    check(unlinkCgmSensor(context, bondRemover, permanentlyRetire = false).detached) {
        "Der bisherige Sensor ist noch per Bluetooth gebondet"
    }
    saveSetup(payload)
    val sensorId = "G7-${java.util.UUID.randomUUID().toString().take(8)}"
    val sensor = CgmSensor(sensorId = sensorId, sessionId = sensorId, deviceName = "Dexcom G7")
    val stateStore = CgmSensorStateStore(context.applicationContext)
    stateStore.save(CgmSessionManager(stateStore.read()).prepareInitialSetup(sensor))
    return sensor
}

/** The explicit Connect action prepares the association before enabling reception. */
internal fun connectCgmSensorToThisWatch(
    context: Context,
    pairingCode: String,
    prepare: (Context, String) -> CgmSensor = { app, code -> moveCgmSensorToThisWatch(app, code) },
    start: (Context) -> Unit = { CgmCollectorService.start(it) },
): CgmSensor {
    val sensor = prepare(context, pairingCode)
    start(context)
    return sensor
}

/** Destructive only for the explicit sensor/auth association; history and user settings remain. */
internal fun unlinkCgmSensor(
    context: Context,
    bondRemover: CgmBondRemover = CgmBondRemover(::removeCgmBond),
    bondedAddresses: (Context) -> Set<String> = { emptySet() },
    permanentlyRetire: Boolean = true,
): CgmUnlinkResult {
    val app = context.applicationContext
    val stateStore = CgmSensorStateStore(app)
    val beforeRelease = stateStore.read()
    var bondEnumerationFailed = false
    val addresses =
        try {
            cgmSensorUnlinkAddresses(app, bondedAddresses)
                .filterNot {
                    !permanentlyRetire && CgmRetiredSensors(app).contains(it)
                }.toSet()
        } catch (_: SecurityException) {
            if (!permanentlyRetire) {
                releaseCgmCollectorOwnership(app)
                return CgmUnlinkResult(CgmBondRemovalStatus.FAILED)
            }
            bondEnumerationFailed = true
            cgmSensorUnlinkAddresses(app, bondedAddresses = { emptySet() })
        }
    stateStore.save(beforeRelease.copy(health = CgmCollectorReliability.releasePending(beforeRelease.health, System.currentTimeMillis())))
    // Cancel the live BLE owner before removing the Android bond. stopService() alone is
    // asynchronous and previously allowed the old watch to remain connected during takeover.
    releaseCgmCollectorOwnership(app)
    CgmCollectorDiagnosticStore(app).discardScheduledCycle()
    val removals = if (addresses.isEmpty()) listOf(bondRemover.remove(app, null)) else addresses.map { bondRemover.remove(app, it) }
    val bondRemovalStatus =
        when {
            bondEnumerationFailed || CgmBondRemovalStatus.FAILED in removals -> CgmBondRemovalStatus.FAILED
            CgmBondRemovalStatus.REMOVED in removals -> CgmBondRemovalStatus.REMOVED
            else -> CgmBondRemovalStatus.NOT_REQUIRED
        }
    // A disconnect is complete only after Android confirms the sensor is unpaired.
    // Preserve identity/credentials when OS removal fails so the user can retry.
    if (!shouldClearCgmAssociation(bondRemovalStatus)) return CgmUnlinkResult(bondRemovalStatus)
    if (permanentlyRetire) CgmRetiredSensors(app).retire(addresses)
    CgmExpectedWindowLedger(app).markSessionEnded(
        beforeRelease.sensor?.sensorId,
        beforeRelease.sensor?.sessionId ?: beforeRelease.sensor?.sensorId,
        System.currentTimeMillis(),
    )
    CgmCredentialStore(app).clearAll()
    stateStore.save(CgmPersistedState())
    CgmAlarmCoordinator.clearSuppressed(app)
    // State changed but the retained reading rows did not. Explicitly invalidate both provider
    // views so Vigil switches to its detached status without requiring a new glucose insert.
    app.contentResolver.notifyChange(CgmReadingProvider.STATE_URI, null)
    app.contentResolver.notifyChange(CgmReadingProvider.CONTENT_URI, null)
    app.sendBroadcast(Intent(CgmReadingDatabase.ACTION_CGM_READING_UPDATED).setPackage("app.sugarlicious"))
    return CgmUnlinkResult(bondRemovalStatus, localAssociationCleared = true)
}

@SuppressLint("MissingPermission")
internal fun cgmBondedSensorAddresses(context: Context): Set<String> =
    context
        .getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.bondedDevices
        .orEmpty()
        .filter { isCgmAdvertisedName(it.name) }
        .map { it.address }
        .toSet()

/** Only the recorded sensor identity is eligible for automatic Bluetooth unpairing. */
internal fun cgmRecordedSensorAddresses(context: Context): Set<String> =
    cgmSensorUnlinkAddresses(context, bondedAddresses = { emptySet() })

/** Include orphaned address metadata even if encrypted credentials can no longer be opened. */
internal fun cgmSensorUnlinkAddresses(
    context: Context,
    bondedAddresses: (Context) -> Set<String> = ::cgmBondedSensorAddresses,
): Set<String> =
    bondedAddresses(context) +
        listOfNotNull(
            CgmSensorStateStore(context).read().sensor?.deviceAddress,
            context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE).getString("shared_address", null),
        ).filter(String::isNotBlank)

@SuppressLint("MissingPermission", "DiscouragedPrivateApi")
private fun removeCgmBond(
    context: Context,
    address: String?,
): CgmBondRemovalStatus {
    if (address.isNullOrBlank()) return CgmBondRemovalStatus.NOT_REQUIRED
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return CgmBondRemovalStatus.FAILED
    }
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: return@runCatching CgmBondRemovalStatus.FAILED
        val device = adapter.bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) } ?: adapter.getRemoteDevice(address)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android's pairing hardening requires the public, app-owned companion path.
            // Do not fall back to hidden privileged Bluetooth APIs on these releases.
            return@runCatching removeCgmBondThroughCompanion(context, address, device)
        }
        if (device.bondState == BluetoothDevice.BOND_NONE) return@runCatching CgmBondRemovalStatus.REMOVED
        val method = device.javaClass.getMethod("removeBond")
        val before = device.bondState
        val requested = method.invoke(device) as? Boolean ?: false
        android.util.Log.i("G7BondRemoval", "remove_requested=$requested adapter=${adapter.state} state_before=$before")
        if (!requested) {
            val after = device.bondState
            android.util.Log.w("G7BondRemoval", "request_declined state_after=$after")
            return@runCatching if (after == BluetoothDevice.BOND_NONE) CgmBondRemovalStatus.REMOVED else CgmBondRemovalStatus.FAILED
        }
        if (awaitCgmBondRemoval(readBondState = { device.bondState })) {
            CgmBondRemovalStatus.REMOVED
        } else {
            android.util.Log.w("G7BondRemoval", "confirmation_timeout state_after=${device.bondState}")
            CgmBondRemovalStatus.FAILED
        }
    }.getOrElse { error ->
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        // Class only: do not log peer identity, key material or the pairing code.
        android.util.Log.w("G7BondRemoval", "platform_failure=${cause.javaClass.simpleName}")
        CgmBondRemovalStatus.FAILED
    }
}

internal fun awaitCgmBondRemoval(
    timeoutMs: Long = 5_000L,
    pollMs: Long = 100L,
    readBondState: () -> Int,
    sleep: (Long) -> Unit = Thread::sleep,
): Boolean {
    require(timeoutMs >= 0L)
    require(pollMs > 0L)
    var elapsed = 0L
    while (true) {
        if (readBondState() == BluetoothDevice.BOND_NONE) return true
        if (elapsed >= timeoutMs) return false
        val delay = minOf(pollMs, timeoutMs - elapsed)
        sleep(delay)
        elapsed += delay
    }
}
