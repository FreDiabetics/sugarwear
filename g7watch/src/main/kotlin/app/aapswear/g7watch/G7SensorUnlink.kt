package app.aapswear.g7watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SetupPayload

internal data class G7UnlinkResult(
    val bondRemovalStatus: G7BondRemovalStatus,
) {
    val bondRemovalAttempted: Boolean get() = bondRemovalStatus != G7BondRemovalStatus.NOT_REQUIRED
    val bondRemovalRequested: Boolean get() = bondRemovalStatus == G7BondRemovalStatus.REMOVED
    val detached: Boolean get() = bondRemovalStatus != G7BondRemovalStatus.FAILED
}

internal enum class G7BondRemovalStatus { NOT_REQUIRED, REMOVED, FAILED }

internal fun interface G7BondRemover {
    fun remove(
        context: Context,
        address: String?,
    ): G7BondRemovalStatus
}

internal data class G7DetachSemantics(
    val stopsCollector: Boolean = true,
    val clearsLocalSession: Boolean = true,
    val preservesHistory: Boolean = true,
    val sendsSensorEndCommand: Boolean = false,
)

internal fun g7DetachSemantics() = G7DetachSemantics()

internal fun shouldClearG7Association(status: G7BondRemovalStatus): Boolean = status != G7BondRemovalStatus.FAILED

/** Stops this watch as BLE owner without deleting sensor identity, credentials or history. */
internal fun releaseG7CollectorOwnership(context: Context) {
    val app = context.applicationContext
    val stateStore = G7SensorStateStore(app)
    val current = stateStore.read()
    stateStore.save(current.copy(health = G7CollectorReliability.releasePending(current.health, System.currentTimeMillis())))
    G7CollectorRuntimeRegistry.cancelLiveCycle()
    AndroidG7Scanner.forceCleanup()
    G7CollectorService.stop(app)
}

/**
 * Prepares this watch to take over the receiver slot. The previous watch still has to release its
 * own bond locally; watches paired to different phones cannot modify each other's Bluetooth store.
 * Collection stays disabled until the user explicitly starts reception.
 */
internal fun moveG7SensorToThisWatch(
    context: Context,
    pairingCode: String,
    bondRemover: G7BondRemover = G7BondRemover(::removeG7Bond),
    saveSetup: (G7SetupPayload) -> Unit = { G7CredentialStore(context.applicationContext).saveSetup(it) },
): G7Sensor {
    val payload = G7SetupPayload(pairingCode)
    check(unlinkG7Sensor(context, bondRemover, permanentlyRetire = false).detached) {
        "Der bisherige Sensor ist noch per Bluetooth gebondet"
    }
    saveSetup(payload)
    val sensorId = "G7-${java.util.UUID.randomUUID().toString().take(8)}"
    val sensor = G7Sensor(sensorId = sensorId, sessionId = sensorId, deviceName = "Dexcom G7")
    val stateStore = G7SensorStateStore(context.applicationContext)
    stateStore.save(G7SessionManager(stateStore.read()).prepareInitialSetup(sensor))
    return sensor
}

/** The explicit Connect action prepares the association before enabling reception. */
internal fun connectG7SensorToThisWatch(
    context: Context,
    pairingCode: String,
    prepare: (Context, String) -> G7Sensor = { app, code -> moveG7SensorToThisWatch(app, code) },
    start: (Context) -> Unit = { G7CollectorService.start(it) },
): G7Sensor {
    val sensor = prepare(context, pairingCode)
    start(context)
    return sensor
}

/** Destructive only for the explicit sensor/auth association; history and user settings remain. */
internal fun unlinkG7Sensor(
    context: Context,
    bondRemover: G7BondRemover = G7BondRemover(::removeG7Bond),
    bondedAddresses: (Context) -> Set<String> = ::g7BondedSensorAddresses,
    permanentlyRetire: Boolean = true,
): G7UnlinkResult {
    val app = context.applicationContext
    val stateStore = G7SensorStateStore(app)
    val beforeRelease = stateStore.read()
    val addresses =
        try {
            g7SensorUnlinkAddresses(app, bondedAddresses)
        } catch (_: SecurityException) {
            releaseG7CollectorOwnership(app)
            return G7UnlinkResult(G7BondRemovalStatus.FAILED)
        }
    stateStore.save(beforeRelease.copy(health = G7CollectorReliability.releasePending(beforeRelease.health, System.currentTimeMillis())))
    // Cancel the live BLE owner before removing the Android bond. stopService() alone is
    // asynchronous and previously allowed the old watch to remain connected during takeover.
    releaseG7CollectorOwnership(app)
    G7CollectorDiagnosticStore(app).discardScheduledCycle()
    val removals = if (addresses.isEmpty()) listOf(bondRemover.remove(app, null)) else addresses.map { bondRemover.remove(app, it) }
    val bondRemovalStatus =
        when {
            G7BondRemovalStatus.FAILED in removals -> G7BondRemovalStatus.FAILED
            G7BondRemovalStatus.REMOVED in removals -> G7BondRemovalStatus.REMOVED
            else -> G7BondRemovalStatus.NOT_REQUIRED
        }
    if (!shouldClearG7Association(bondRemovalStatus)) {
        return G7UnlinkResult(bondRemovalStatus)
    }
    // Commit exclusions only after every requested bond is confirmed gone. A failed
    // detach must not silently block the retained association on the next explicit retry.
    if (permanentlyRetire) G7RetiredSensors(app).retire(addresses)
    G7ExpectedWindowLedger(app).markSessionEnded(
        beforeRelease.sensor?.sensorId,
        beforeRelease.sensor?.sessionId ?: beforeRelease.sensor?.sensorId,
        System.currentTimeMillis(),
    )
    G7CredentialStore(app).clearAll()
    stateStore.save(G7PersistedState())
    G7CgmAlarmCoordinator.clearSuppressed(app)
    // State changed but the retained reading rows did not. Explicitly invalidate both provider
    // views so Vigil switches to its detached status without requiring a new glucose insert.
    app.contentResolver.notifyChange(G7ReadingProvider.STATE_URI, null)
    app.contentResolver.notifyChange(G7ReadingProvider.CONTENT_URI, null)
    app.sendBroadcast(Intent(G7ReadingDatabase.ACTION_G7_READING_UPDATED).setPackage("app.sugarlicious"))
    return G7UnlinkResult(bondRemovalStatus)
}

@SuppressLint("MissingPermission")
internal fun g7BondedSensorAddresses(context: Context): Set<String> =
    context
        .getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.bondedDevices
        .orEmpty()
        .filter { isG7AdvertisedName(it.name) }
        .map { it.address }
        .toSet()

/** Include orphaned address metadata even if encrypted credentials can no longer be opened. */
internal fun g7SensorUnlinkAddresses(
    context: Context,
    bondedAddresses: (Context) -> Set<String> = ::g7BondedSensorAddresses,
): Set<String> =
    bondedAddresses(context) +
        listOfNotNull(
            G7SensorStateStore(context).read().sensor?.deviceAddress,
            context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE).getString("shared_address", null),
        ).filter(String::isNotBlank)

@SuppressLint("MissingPermission", "DiscouragedPrivateApi")
private fun removeG7Bond(
    context: Context,
    address: String?,
): G7BondRemovalStatus {
    if (address.isNullOrBlank()) return G7BondRemovalStatus.NOT_REQUIRED
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return G7BondRemovalStatus.FAILED
    }
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: return@runCatching G7BondRemovalStatus.FAILED
        val device = adapter.bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) } ?: adapter.getRemoteDevice(address)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android's pairing hardening requires the public, app-owned companion path.
            // Do not fall back to hidden privileged Bluetooth APIs on these releases.
            return@runCatching removeG7BondThroughCompanion(context, address, device)
        }
        if (device.bondState == BluetoothDevice.BOND_NONE) return@runCatching G7BondRemovalStatus.REMOVED
        val method = device.javaClass.getMethod("removeBond")
        val before = device.bondState
        val requested = method.invoke(device) as? Boolean ?: false
        android.util.Log.i("G7BondRemoval", "remove_requested=$requested adapter=${adapter.state} state_before=$before")
        if (!requested) {
            val after = device.bondState
            android.util.Log.w("G7BondRemoval", "request_declined state_after=$after")
            return@runCatching if (after == BluetoothDevice.BOND_NONE) G7BondRemovalStatus.REMOVED else G7BondRemovalStatus.FAILED
        }
        if (awaitG7BondRemoval(readBondState = { device.bondState })) {
            G7BondRemovalStatus.REMOVED
        } else {
            android.util.Log.w("G7BondRemoval", "confirmation_timeout state_after=${device.bondState}")
            G7BondRemovalStatus.FAILED
        }
    }.getOrElse { error ->
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        // Class only: do not log peer identity, key material or the pairing code.
        android.util.Log.w("G7BondRemoval", "platform_failure=${cause.javaClass.simpleName}")
        G7BondRemovalStatus.FAILED
    }
}

internal fun awaitG7BondRemoval(
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
