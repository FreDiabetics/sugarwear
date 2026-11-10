package app.sugarwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSessionManager
import app.sugarlicious.sensor.protocol.CgmSetupPayload
import app.sugarlicious.storage.DiagnosticEventStore

internal data class CgmUnlinkResult(
    val bondRemovalStatus: CgmBondRemovalStatus,
    val localAssociationCleared: Boolean = bondRemovalStatus != CgmBondRemovalStatus.FAILED,
    val failureReason: String? = null,
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
    saveSetup: (CgmSetupPayload) -> Unit = { CgmCredentialStore(context.applicationContext).saveSetup(it) },
): CgmSensor {
    val payload = CgmSetupPayload(pairingCode)
    // Explicit unlink must authorize and confirm the previous bond removal first.
    val previous = CgmSensorStateStore(context.applicationContext).read()
    check(previous.sensor == null && !CgmCredentialStore(context.applicationContext).hasSetupMaterial()) {
        "Bitte den bisherigen Sensor zuerst über 'Sensor trennen' vollständig entkoppeln"
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
): CgmUnlinkResult {
    val app = context.applicationContext
    val stateStore = CgmSensorStateStore(app)
    val addresses = cgmRecordedSensorAddresses(app)
    // Stop the live BLE owner before touching a bond. Failed removal never resumes it.
    releaseCgmCollectorOwnership(app)
    CgmCollectorDiagnosticStore(app).discardScheduledCycle()
    if (addresses.size > 1) {
        return CgmUnlinkResult(
            CgmBondRemovalStatus.FAILED,
            failureReason = "Widersprüchliche Sensoradressen: keine fremden Bluetooth-Bonds entfernt.",
        )
    }
    val bondRemovalStatus = bondRemover.remove(app, addresses.singleOrNull())
    // Preserve credentials only if the platform refuses to confirm removal,
    // so explicit unlink can be retried without accidentally reconnecting.
    if (!shouldClearCgmAssociation(bondRemovalStatus)) {
        return CgmUnlinkResult(
            bondRemovalStatus,
            failureReason = "Android hat die G7-Bluetooth-Entkopplung nicht bestätigt. Collector pausiert; Entkopplung erneut versuchen.",
        )
    }

    // Never touch g7_readings.db: history is retained across sensor changes.
    CgmCredentialStore(app).clearAll()
    stateStore.reset()
    CgmAlarmCoordinator.clearForSensorRemoval(app)
    CgmCollectorDiagnosticStore(app).clearAll()
    CgmExpectedWindowLedger(app).clearAll()
    CgmRetiredSensors(app).clearAll()
    // The general diagnostic stream can also contain prior G7 session identifiers;
    // it is not historical glucose data and must not survive explicit unlink.
    kotlinx.coroutines.runBlocking {
        DiagnosticEventStore(app).clear()
    }
    val runtimePreferences = app.getSharedPreferences("g7_runtime_recovery", Context.MODE_PRIVATE)
    check(runtimePreferences.edit().clear().commit()) { "G7 runtime recovery cleanup failed" }
    // A detached sensor's retained readings must not be displayed as current.
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
    cgmSensorUnlinkAddresses(context)

/** Never unpair all G7 devices seen in the global Bluetooth bond list. */
internal fun cgmSensorUnlinkAddresses(context: Context): Set<String> =
    listOfNotNull(
        CgmSensorStateStore(context).read().sensor?.deviceAddress,
        context.getSharedPreferences(CGM_CREDENTIAL_PREFERENCES, Context.MODE_PRIVATE).getString("shared_address", null),
    ).filter(String::isNotBlank)
        .distinctBy { it.uppercase(java.util.Locale.ROOT) }
        .toSet()

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
        if (adapter.state != BluetoothAdapter.STATE_ON) return@runCatching CgmBondRemovalStatus.FAILED
        val bonded = adapter.bondedDevices ?: return@runCatching CgmBondRemovalStatus.FAILED
        val device = bonded.firstOrNull { it.address.equals(address, ignoreCase = true) } ?: adapter.getRemoteDevice(address)
        val initialBondState = readableCgmBondState({ adapter.state }, { device.bondState }) ?: return@runCatching CgmBondRemovalStatus.FAILED
        if (Build.VERSION.SDK_INT >= 36) {
            // Android's pairing hardening requires the public, app-owned companion path.
            // Do not fall back to hidden privileged Bluetooth APIs on these releases.
            return@runCatching removeCgmBondThroughCompanion(context, address, device)
        }
        if (initialBondState == BluetoothDevice.BOND_NONE) return@runCatching CgmBondRemovalStatus.REMOVED
        val method = device.javaClass.getMethod("removeBond")
        val before = initialBondState
        val requested = method.invoke(device) as? Boolean ?: false
        android.util.Log.i("G7BondRemoval", "remove_requested=$requested adapter=${adapter.state} state_before=$before")
        if (!requested) {
            val after = readableCgmBondState({ adapter.state }, { device.bondState })
            android.util.Log.w("G7BondRemoval", "request_declined state_after=$after")
            return@runCatching if (after == BluetoothDevice.BOND_NONE) CgmBondRemovalStatus.REMOVED else CgmBondRemovalStatus.FAILED
        }
        if (awaitCgmBondRemoval(readBondState = { readableCgmBondState({ adapter.state }, { device.bondState }) })) {
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

/** Android can return BOND_NONE merely because its radio service is unavailable. */
internal fun readableCgmBondState(readAdapterState: () -> Int, readBondState: () -> Int): Int? {
    if (readAdapterState() != BluetoothAdapter.STATE_ON) return null
    val state = readBondState()
    return state.takeIf { readAdapterState() == BluetoothAdapter.STATE_ON }
}

internal fun awaitCgmBondRemoval(
    timeoutMs: Long = 5_000L,
    pollMs: Long = 100L,
    readBondState: () -> Int?,
    sleep: (Long) -> Unit = Thread::sleep,
): Boolean {
    require(timeoutMs >= 0L)
    require(pollMs > 0L)
    var elapsed = 0L
    while (true) {
        val state = readBondState() ?: return false
        if (state == BluetoothDevice.BOND_NONE) return true
        if (elapsed >= timeoutMs) return false
        val delay = minOf(pollMs, timeoutMs - elapsed)
        sleep(delay)
        elapsed += delay
    }
}
