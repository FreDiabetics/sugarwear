package app.aapswear.g7watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.util.size
import app.aapswear.g7.DirectConnectResult
import app.aapswear.g7.G7AuthenticationSession
import app.aapswear.g7.G7GattProfile
import app.aapswear.g7.G7GlucosePacketParser
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7Reading
import app.aapswear.g7.G7Scanner
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS = 30 * 60_000L
internal const val G7_RECONNECT_SCAN_TIMEOUT_MS = 60_000L
internal const val G7_GATT_133_ERROR_CODE = "G7-GATT-133"
internal const val G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE = "G7-GATT-215"
internal const val G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE = "G7-GATT-216"
internal const val G7_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE = "G7-GATT-217"
internal const val G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE = "G7-GATT-218"
internal const val G7_DIRECT_CONNECT_CALLBACK_TIMEOUT_MS = 20_000L
internal const val G7_FALLBACK_SCAN_TIMEOUT_MS = 15_000L

internal sealed interface G7BleTelemetry

internal data class G7DiscoveryCandidateDeferred(
    val timestampEpochMs: Long,
    val errorCode: String,
) : G7BleTelemetry

internal data class G7DirectConnectStarted(
    val timestampEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val attemptId: Long = 0L,
    val gattGeneration: Long = 0L,
) : G7BleTelemetry

internal data class G7DirectConnectCompleted(
    val timestampEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val durationMs: Long,
    val result: DirectConnectResult,
    val status: Int? = null,
    val newState: Int? = null,
    val attemptId: Long = 0L,
    val gattGeneration: Long = 0L,
) : G7BleTelemetry

internal data class G7GattCleanupTelemetry(
    val timestampEpochMs: Long,
    val attemptId: Long,
    val gattGeneration: Long,
    val disconnected: Boolean,
    val started: Boolean,
) : G7BleTelemetry

internal data class G7StaleGattCallbackTelemetry(
    val timestampEpochMs: Long,
    val attemptId: Long,
    val gattGeneration: Long,
    val callback: String,
) : G7BleTelemetry

internal data class G7BackfillRequestTelemetry(
    val timestampEpochMs: Long,
    val attemptId: Long,
    val gattGeneration: Long,
    val startSensorClock: Long,
    val endSensorClock: Long,
) : G7BleTelemetry

internal data class G7ScanTelemetry(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val totalResults: Int,
    val connectableResults: Int,
    val namedG7Results: Int,
    val exactAddressResults: Int,
    val manufacturerDataResults: Int,
    val duplicateResults: Int,
    val rejectedNotConnectable: Int,
    val rejectedByIdentity: Int,
    val minRssi: Int?,
    val maxRssi: Int?,
    val adapterState: Int,
    val scanMode: Int,
    val callbackType: Int,
) : G7BleTelemetry

@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.copyLegacyG7NotificationValue(): ByteArray = copyG7NotificationValue(value ?: ByteArray(0))

internal object G7GattCallbackDispatcher {
    private val lock = Any()
    private var generation = 0L
    private var runtime: Pair<HandlerThread, Handler>? = null

    val handler: Handler
        get() =
            synchronized(lock) {
                runtime?.second ?: createRuntime().also { runtime = it }.second
            }

    /** Rotates only the app-owned callback looper after a completed degraded cycle. */
    fun reset(): Boolean =
        synchronized(lock) {
            val previous = runtime ?: return@synchronized false
            runtime = null
            previous.first.quitSafely()
        }

    private fun createRuntime(): Pair<HandlerThread, Handler> {
        generation += 1
        val thread = HandlerThread("G7-Gatt-Callbacks-$generation").apply { start() }
        return thread to Handler(thread.looper)
    }
}

// API 36 deprecates every public connectGatt overload without exposing a
// replacement. Keep the unavoidable call isolated and use the fullest overload
// so callbacks stay on the collector-owned background thread.
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun connectG7Gatt(
    device: BluetoothDevice,
    context: Context,
    callback: BluetoothGattCallback,
): BluetoothGatt =
    device.connectGatt(
        context,
        false,
        callback,
        BluetoothDevice.TRANSPORT_LE,
        BluetoothDevice.PHY_LE_1M_MASK,
        G7GattCallbackDispatcher.handler,
    )

internal fun interface G7DeviceMatcher {
    fun matches(
        device: BluetoothDevice,
        advertisedName: String?,
        knownSensor: G7Sensor?,
    ): Boolean
}

internal fun isG7AdvertisedName(name: String?): Boolean = name?.uppercase()?.matches(Regex("^DX(?:CM|01|02)[A-Z0-9]{0,8}$")) == true

internal class KnownG7DeviceMatcher : G7DeviceMatcher {
    @SuppressLint("MissingPermission")
    override fun matches(
        device: BluetoothDevice,
        advertisedName: String?,
        knownSensor: G7Sensor?,
    ): Boolean {
        knownG7AddressMatches(knownSensor?.deviceAddress, device.address)?.let { return it }
        val name = advertisedName ?: runCatching { device.name }.getOrNull() ?: return false
        return isG7AdvertisedName(name)
    }
}

internal class AndroidG7Scanner(
    private val context: Context,
    private val matcher: G7DeviceMatcher = KnownG7DeviceMatcher(),
) : G7Scanner {
    private val retiredSensors = G7RetiredSensors(context)
    var freshSetup: Boolean = false
    private val candidates = G7DiscoveryCandidates()

    fun clearRejectedCandidates() = candidates.clear()

    fun rejectCandidate(address: String) = candidates.reject(address)

    fun deferCandidate(address: String) = candidates.defer(address)

    var telemetryListener: (G7BleTelemetry) -> Unit = {}
    private val pendingTelemetry = AtomicReference<G7ScanTelemetry?>(null)

    fun flushTelemetry(radioEvent: G7BleTelemetry? = null) {
        if (radioEvent != null && radioEvent !is G7DirectConnectStarted && radioEvent !is G7GattCleanupTelemetry) return
        pendingTelemetry.getAndSet(null)?.let(telemetryListener)
    }

    @SuppressLint("MissingPermission")
    override suspend fun findKnownSensor(
        sensor: G7Sensor?,
        timeoutMs: Long,
    ): G7Sensor? {
        requirePermission(Manifest.permission.BLUETOOTH_SCAN, "G7-BLE-101")
        val adapter =
            context.getSystemService(BluetoothManager::class.java).adapter
                ?: throw G7BleException("G7-BLE-102", "Bluetooth ist auf der Uhr nicht verfügbar", false)
        if (!adapter.isEnabled) throw G7BleException("G7-BLE-103", "Bluetooth ist ausgeschaltet", true)
        val scanner =
            adapter.bluetoothLeScanner
                ?: throw G7BleException("G7-BLE-104", "Bluetooth-Suche ist nicht verfügbar", true)

        return suspendCancellableCoroutine { continuation ->
            val scanStartedAt = System.currentTimeMillis()
            val finished = AtomicBoolean(false)
            val totalResults = AtomicInteger(0)
            val connectableResults = AtomicInteger(0)
            val namedG7Results = AtomicInteger(0)
            val exactAddressResults = AtomicInteger(0)
            val manufacturerDataResults = AtomicInteger(0)
            val duplicateResults = AtomicInteger(0)
            val rejectedNotConnectable = AtomicInteger(0)
            val rejectedByIdentity = AtomicInteger(0)
            val seenAddresses = mutableSetOf<String>()
            var minRssi: Int? = null
            var maxRssi: Int? = null
            val handler = android.os.Handler(context.mainLooper)
            var timeoutCallback: Runnable? = null
            lateinit var callback: ScanCallback

            fun finish(
                result: G7Sensor?,
                error: Throwable? = null,
            ) {
                if (!finished.compareAndSet(false, true)) return
                timeoutCallback?.let(handler::removeCallbacks)
                timeoutCallback = null
                runCatching { scanner.stopScan(callback) }
                clearActiveScan(scanner, callback)
                val endedAt = System.currentTimeMillis()
                pendingTelemetry.set(
                    G7ScanTelemetry(
                        scanStartedAt,
                        endedAt,
                        endedAt - scanStartedAt,
                        totalResults.get(),
                        connectableResults.get(),
                        namedG7Results.get(),
                        exactAddressResults.get(),
                        manufacturerDataResults.get(),
                        duplicateResults.get(),
                        rejectedNotConnectable.get(),
                        rejectedByIdentity.get(),
                        minRssi,
                        maxRssi,
                        adapter.state,
                        ScanSettings.SCAN_MODE_LOW_LATENCY,
                        ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                    ),
                )
                if (!continuation.isActive) return
                if (error != null) continuation.resumeWithException(error) else continuation.resume(result)
            }
            callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        totalResults.incrementAndGet()
                        synchronized(seenAddresses) {
                            if (!seenAddresses.add(result.device.address)) duplicateResults.incrementAndGet()
                        }
                        minRssi = minRssi?.let { minOf(it, result.rssi) } ?: result.rssi
                        maxRssi = maxRssi?.let { maxOf(it, result.rssi) } ?: result.rssi
                        val advertisedName = result.scanRecord?.deviceName
                        if ((result.scanRecord?.manufacturerSpecificData?.size ?: 0) > 0) {
                            manufacturerDataResults.incrementAndGet()
                        }
                        if (isG7AdvertisedName(advertisedName)) namedG7Results.incrementAndGet()
                        if (knownG7AddressMatches(sensor?.deviceAddress, result.device.address) == true) {
                            exactAddressResults.incrementAndGet()
                        }
                        if (!isConnectableG7Advertisement(result.isConnectable)) {
                            rejectedNotConnectable.incrementAndGet()
                            return
                        }
                        connectableResults.incrementAndGet()
                        if (!candidates.accepts(result.device.address) ||
                            retiredSensors.excludesDiscovery(result.device.address, freshSetup) ||
                            !matcher.matches(result.device, advertisedName, sensor)
                        ) {
                            rejectedByIdentity.incrementAndGet()
                            return
                        }
                        val name = advertisedName ?: runCatching { result.device.name }.getOrNull() ?: sensor?.deviceName
                        val sensorId = sensor?.sensorId ?: name ?: "Dexcom-G7"
                        finish(
                            G7Sensor(
                                sensorId = sensorId,
                                sessionId = sensor?.sessionId ?: sensorId,
                                deviceName = name ?: "Dexcom G7",
                                deviceAddress = result.device.address,
                                sensorStartEpochMs = sensor?.sensorStartEpochMs,
                                state = sensor?.state ?: app.aapswear.g7.G7SensorState.UNKNOWN,
                            ),
                        )
                    }

                    override fun onScanFailed(errorCode: Int) {
                        finish(null, G7BleException("G7-BLE-105", "Sensorsuche fehlgeschlagen ($errorCode)", true))
                    }
                }
            runCatching {
                replaceActiveScan(scanner, callback)
                scanner.startScan(
                    g7ScanFilters(sensor?.deviceAddress),
                    ScanSettings
                        .Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                    callback,
                )
            }.onFailure { finish(null, G7BleException("G7-BLE-106", "Sensorsuche konnte nicht gestartet werden", true, it)) }
            if (!finished.get()) {
                timeoutCallback =
                    Runnable {
                        finish(
                            null,
                            G7BleException(
                                "G7-BLE-107",
                                "Kein sendender Dexcom-G7-Sensor gefunden · scan=${totalResults.get()} · connectable=${connectableResults.get()} · g7Name=${namedG7Results.get()} · exactAddress=${exactAddressResults.get()}",
                                true,
                            ),
                        )
                    }
                handler.postDelayed(
                    requireNotNull(timeoutCallback),
                    timeoutMs.coerceIn(5_000L, G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS),
                )
            }
            continuation.invokeOnCancellation { finish(null) }
        }
    }

    private fun requirePermission(
        permission: String,
        code: String,
    ) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw G7BleException(code, "Bluetooth-Berechtigung fehlt", false)
        }
    }

    companion object {
        private val activeScanLock = Any()
        private var activeScan: Pair<android.bluetooth.le.BluetoothLeScanner, ScanCallback>? = null

        @SuppressLint("MissingPermission")
        private fun replaceActiveScan(
            scanner: android.bluetooth.le.BluetoothLeScanner,
            callback: ScanCallback,
        ) = synchronized(activeScanLock) {
            activeScan?.let { (oldScanner, oldCallback) -> runCatching { oldScanner.stopScan(oldCallback) } }
            activeScan = scanner to callback
        }

        private fun clearActiveScan(
            scanner: android.bluetooth.le.BluetoothLeScanner,
            callback: ScanCallback,
        ) = synchronized(activeScanLock) {
            if (activeScan?.first === scanner && activeScan?.second === callback) activeScan = null
        }

        @SuppressLint("MissingPermission")
        fun forceCleanup(): Boolean =
            synchronized(activeScanLock) {
                val stale = activeScan ?: return@synchronized false
                runCatching { stale.first.stopScan(stale.second) }
                activeScan = null
                true
            }
    }
}

internal data class G7CollectionResult(
    val sensor: G7Sensor,
    val reading: G7Reading,
    val backfillReadings: List<G7Reading> = emptyList(),
    val sharedKey: ByteArray?,
)

internal class G7BleException(
    val errorCode: String,
    override val message: String,
    val recoverable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class AndroidG7Collector(
    private val context: Context,
    private val scanner: G7Scanner = AndroidG7Scanner(context),
    private val packetParser: G7GlucosePacketParser = G7GlucosePacketParser(),
) {
    @SuppressLint("MissingPermission")
    suspend fun collect(
        initialSensor: G7Sensor,
        credentials: StoredG7Credentials,
        onState: (G7ProtocolState) -> Unit,
        onSharedKey: (String, ByteArray) -> Unit = { _, _ -> },
        scanTimeoutMsOverride: Long? = null,
        reconnectStrategy: G7ReconnectStrategy = G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT,
        onTelemetry: (G7BleTelemetry) -> Unit = {},
        attemptId: Long = 0L,
        lastStoredSensorClock: Long? = null,
        onLiveReading: suspend (G7Reading) -> Unit = {},
        allowFallbackScan: Boolean = true,
    ): G7CollectionResult {
        var sensor = initialSensor
        if (G7RetiredSensors(context).contains(sensor.deviceAddress)) {
            throw G7BleException("G7-SETUP-RETIRED", "Dieser Sensor wurde vollständig getrennt und wird nicht erneut verbunden", false)
        }
        (scanner as? AndroidG7Scanner)?.clearRejectedCandidates()
        (scanner as? AndroidG7Scanner)?.freshSetup =
            allowsRetiredG7CandidateForSetup(initialSensor, credentials.sharedKey != null, credentials.sharedKeyAddress)
        val discoveryDeadline = android.os.SystemClock.elapsedRealtime() + (scanTimeoutMsOverride ?: g7ScanTimeoutMs(initialSensor))
        var sharedKey =
            credentials.sharedKey?.takeIf {
                credentials.sharedKeyAddress == null || credentials.sharedKeyAddress.equals(sensor.deviceAddress, true)
            }
        val initialBondState =
            sensor.deviceAddress?.let { address ->
                runCatching {
                    context
                        .getSystemService(BluetoothManager::class.java)
                        .adapter
                        .getRemoteDevice(address)
                        .bondState
                }.getOrNull()
            }
        val pairingRecoveryRequired = shouldResumeG7Pairing(sharedKey, initialBondState)
        sharedKey = usableG7SharedKey(sharedKey, initialBondState)
        var bondReconnectAttempts = 0
        var gatt133Retries = 0
        var noCallbackRetries = 0
        var discoveryRequired = pairingRecoveryRequired || !shouldUseDirectReconnect(reconnectStrategy, sensor.deviceAddress)
        var fallbackUsed = false
        var sensorFoundPending = false
        (scanner as? AndroidG7Scanner)?.telemetryListener = onTelemetry

        while (true) {
            if (discoveryRequired) {
                onState(G7ProtocolState.SCANNING)
                val scanTimeout =
                    g7DiscoveryScanTimeoutMs(sensor, scanTimeoutMsOverride, pairingRecoveryRequired, fallbackUsed)
                val remainingDiscoveryMs =
                    if (initialSensor.deviceAddress.isNullOrBlank()) discoveryDeadline - android.os.SystemClock.elapsedRealtime() else scanTimeout
                if (remainingDiscoveryMs <= 0L) {
                    throw G7BleException("G7-BLE-107", "Kein authentifizierbarer G7 im Suchzeitfenster gefunden", true)
                }
                val discovered =
                    try {
                        val remainingScan = minOf(scanTimeout, remainingDiscoveryMs)
                        withTimeout(remainingScan + SCAN_TIMEOUT_GUARD_MS) { scanner.findKnownSensor(sensor, remainingScan) }
                    } catch (error: G7BleException) {
                        (scanner as? AndroidG7Scanner)?.flushTelemetry()
                        if (fallbackUsed && error.errorCode == "G7-BLE-107") {
                            throw G7BleException(
                                "G7-BLE-FALLBACK-107",
                                "Direct Reconnect und kurzer Fallback-Scan ohne Sensor",
                                true,
                                error,
                            )
                        }
                        throw error
                    } catch (_: TimeoutCancellationException) {
                        (scanner as? AndroidG7Scanner)?.flushTelemetry()
                        throw G7BleException("G7-BLE-111", "Sensorsuche hat ihr begrenztes Zeitfenster überschritten", true)
                    }
                sensor = discovered ?: throw G7BleException("G7-BLE-107", "Kein sendender Dexcom-G7-Sensor gefunden", true)
                sensorFoundPending = true
                discoveryRequired = false
            }
            if (sharedKey != null &&
                credentials.sharedKeyAddress != null &&
                !credentials.sharedKeyAddress.equals(sensor.deviceAddress, ignoreCase = true)
            ) {
                sharedKey = null
            }

            // An application key can already have been emitted immediately before Android starts
            // its asynchronous bond. If that bond was interrupted, replaying the key while the
            // platform still reports BOND_NONE enters the stored-session path and fails before a
            // fresh createBond() can be requested. Keep the persisted key intact, but use the
            // normal pairing exchange for this connection so Android can establish the bond.
            val connection =
                G7GattConnection(
                    context,
                    sensor,
                    { event ->
                        // A successful scan must hand off to actual connectGatt first. Its
                        // callback only captured telemetry; serialize it here off the UI/BLE
                        // callback thread, after radio registration (or physical cleanup).
                        if (event is G7DirectConnectStarted || event is G7GattCleanupTelemetry) {
                            (scanner as? AndroidG7Scanner)?.flushTelemetry(event)
                            if (sensorFoundPending) {
                                sensorFoundPending = false
                                onState(G7ProtocolState.SENSOR_FOUND)
                            }
                        }
                        onTelemetry(event)
                    },
                    G7GattGenerationRegistry.acquire(attemptId),
                )
            try {
                val outcome =
                    withTimeout(SESSION_TIMEOUT_MS) {
                        connection.collect(
                            G7AuthenticationSession(credentials.pairingCode, credentials.gKey, sharedKey),
                            packetParser,
                            onState,
                            onSharedKey = {
                                sharedKey = it
                                sensor.deviceAddress?.let { address -> onSharedKey(address, it) }
                            },
                            lastStoredSensorClock = lastStoredSensorClock,
                            onLiveReading = onLiveReading,
                        )
                    }
                return G7CollectionResult(sensor, outcome.first, outcome.second, sharedKey)
            } catch (rebond: G7BondReconnectRequired) {
                connection.close()
                sharedKey = rebond.sharedKey
                bondReconnectAttempts += 1
                if (bondReconnectAttempts >= MAX_BOND_RECONNECT_ATTEMPTS) {
                    throw G7BleException("G7-AUTH-207", "Sensor wurde verbunden, die erneute Verbindung schlug aber fehl", true, rebond)
                }
                discoveryRequired = false
                onState(G7ProtocolState.RECOVERING)
                delay(BOND_RECONNECT_DELAY_MS)
            } catch (error: G7BleException) {
                // close-before-retry is deliberate: no delay or new connect may overlap the
                // terminal lifecycle of the generation that just failed.
                connection.close()
                if (error.errorCode == "G7-AUTH-MISMATCH" && initialSensor.deviceAddress.isNullOrBlank() && scanner is AndroidG7Scanner) {
                    sensor.deviceAddress?.let(scanner::rejectCandidate)
                    sensor = initialSensor
                    sharedKey = null
                    discoveryRequired = true
                    onState(G7ProtocolState.RECOVERING)
                } else if (scanner is AndroidG7Scanner &&
                    shouldDeferUnverifiedG7Candidate(initialSensor, sharedKey != null, error.errorCode, error.recoverable)
                ) {
                    sensor.deviceAddress?.let(scanner::deferCandidate)
                    onTelemetry(G7DiscoveryCandidateDeferred(System.currentTimeMillis(), error.errorCode))
                    sensor = initialSensor
                    discoveryRequired = true
                    gatt133Retries = 0
                    noCallbackRetries = 0
                    onState(G7ProtocolState.RECOVERING)
                    delay(GATT_133_STACK_SETTLE_DELAY_MS)
                } else if (shouldRetryNoCallbackDirectly(error.errorCode, noCallbackRetries, fallbackUsed)) {
                    noCallbackRetries += 1
                    discoveryRequired = false
                    onState(G7ProtocolState.RECOVERING)
                    delay(NO_CALLBACK_STACK_SETTLE_DELAY_MS)
                } else if (error.errorCode == G7_GATT_133_ERROR_CODE &&
                    gatt133Retries < maxGatt133RetriesForCycle(fallbackUsed)
                ) {
                    gatt133Retries += 1
                    discoveryRequired = false
                    onState(G7ProtocolState.RECOVERING)
                    delay(GATT_133_STACK_SETTLE_DELAY_MS)
                } else if (allowFallbackScan &&
                    shouldUseFallbackDiscovery(reconnectStrategy, sensor.deviceAddress, fallbackUsed, error.recoverable)
                ) {
                    fallbackUsed = true
                    discoveryRequired = true
                    onState(G7ProtocolState.RECOVERING)
                } else {
                    throw error
                }
            } catch (timeout: TimeoutCancellationException) {
                connection.close()
                val error =
                    G7BleException(
                        G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE,
                        "Direkte G7-Verbindung hat das begrenzte Zeitfenster überschritten",
                        true,
                        timeout,
                    )
                if (allowFallbackScan &&
                    shouldUseFallbackDiscovery(reconnectStrategy, sensor.deviceAddress, fallbackUsed, error.recoverable)
                ) {
                    fallbackUsed = true
                    discoveryRequired = true
                    onState(G7ProtocolState.RECOVERING)
                } else {
                    throw error
                }
            } finally {
                connection.close()
            }
        }
    }

    private companion object {
        const val SESSION_TIMEOUT_MS = 75_000L
        const val SCAN_TIMEOUT_GUARD_MS = 2_000L
        const val MAX_BOND_RECONNECT_ATTEMPTS = 2
        const val BOND_RECONNECT_DELAY_MS = 1_500L
        const val GATT_133_STACK_SETTLE_DELAY_MS = 1_500L
        const val NO_CALLBACK_STACK_SETTLE_DELAY_MS = 2_500L
    }
}

private class G7BondReconnectRequired(
    val sharedKey: ByteArray,
) : Exception()

internal enum class G7BondWaitDecision { KEEP_WAITING, BONDED, FAILED }

internal fun g7BondWaitDecision(
    bondState: Int,
    observedBonding: Boolean,
): G7BondWaitDecision =
    when (bondState) {
        BluetoothDevice.BOND_BONDED -> G7BondWaitDecision.BONDED
        BluetoothDevice.BOND_BONDING -> G7BondWaitDecision.KEEP_WAITING
        BluetoothDevice.BOND_NONE -> if (observedBonding) G7BondWaitDecision.FAILED else G7BondWaitDecision.KEEP_WAITING
        else -> G7BondWaitDecision.KEEP_WAITING
    }

private data class G7ConnectionCallback(
    val status: Int,
    val state: Int,
    val epochMs: Long,
    val elapsedMs: Long,
)

internal class G7GattConnection(
    private val context: Context,
    private val sensor: G7Sensor,
    private val onTelemetry: (G7BleTelemetry) -> Unit,
    private val ownership: G7GattOwnership,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val connectionEvents = Channel<G7ConnectionCallback>(CONTROL_EVENT_BUFFER_CAPACITY)
    private val serviceEvents = Channel<Int>(CONTROL_EVENT_BUFFER_CAPACITY)
    private val descriptorEvents = Channel<Pair<UUID, Int>>(CONTROL_EVENT_BUFFER_CAPACITY)
    private val writeEvents = Channel<Pair<UUID, Int>>(CONTROL_EVENT_BUFFER_CAPACITY)
    private val notifications = Channel<Pair<UUID, ByteArray>>(NOTIFICATION_BUFFER_CAPACITY)

    @Volatile private var connected = false
    private var gatt: BluetoothGatt? = null
    private var connectStartedAtEpochMs: Long? = null
    private var connectStartedElapsedMs: Long? = null
    private val directResultRecorded = AtomicBoolean(false)
    private val closeStarted = AtomicBoolean(false)

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (!acceptCallback(gatt, "CONNECTION_STATE")) return
                connected = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
                // Never serialize diagnostics on Android's BLE callback thread. Preserve the
                // actual arrival time and let the collector consume/persist it off that thread.
                connectionEvents.trySend(G7ConnectionCallback(status, newState, System.currentTimeMillis(), SystemClock.elapsedRealtime()))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (!acceptCallback(gatt, "SERVICES_DISCOVERED")) return
                serviceEvents.trySend(status)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (!acceptCallback(gatt, "DESCRIPTOR_WRITE")) return
                descriptorEvents.trySend(descriptor.characteristic.uuid to status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (!acceptCallback(gatt, "CHARACTERISTIC_WRITE")) return
                writeEvents.trySend(characteristic.uuid to status)
            }

            @Deprecated("Deprecated in Android 13")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (!acceptCallback(gatt, "CHARACTERISTIC_CHANGED_LEGACY")) return
                notifications.trySend(characteristic.uuid to characteristic.copyLegacyG7NotificationValue())
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (!acceptCallback(gatt, "CHARACTERISTIC_CHANGED")) return
                notifications.trySend(characteristic.uuid to copyG7NotificationValue(value))
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun collect(
        authentication: G7AuthenticationSession,
        parser: G7GlucosePacketParser,
        onState: (G7ProtocolState) -> Unit,
        onSharedKey: (ByteArray) -> Unit,
        lastStoredSensorClock: Long?,
        onLiveReading: suspend (G7Reading) -> Unit,
    ): Pair<G7Reading, List<G7Reading>> {
        requirePermission(Manifest.permission.BLUETOOTH_CONNECT, "G7-BLE-108")
        val address =
            sensor.deviceAddress
                ?: throw G7BleException("G7-BLE-109", "Sensoradresse fehlt", true)
        val adapter = manager.adapter ?: throw G7BleException("G7-BLE-102", "Bluetooth ist nicht verfügbar", false)
        val device =
            runCatching { adapter.getRemoteDevice(address) }
                .getOrElse {
                    recordDirectResult(DirectConnectResult.DEVICE_UNAVAILABLE)
                    throw G7BleException("G7-BLE-110", "Sensoradresse ist ungültig", false, it)
                }

        connectStartedAtEpochMs = System.currentTimeMillis()
        connectStartedElapsedMs = SystemClock.elapsedRealtime()
        gatt =
            try {
                connectG7Gatt(device, context, callback)
            } catch (security: SecurityException) {
                recordDirectResult(DirectConnectResult.SECURITY_ERROR)
                throw security
            }
        // Radio registration must precede preference/JSON work. Otherwise "connect started"
        // measured diagnostic overhead rather than the real Android connectGatt invocation.
        onTelemetry(
            G7DirectConnectStarted(
                requireNotNull(connectStartedAtEpochMs),
                requireNotNull(connectStartedElapsedMs),
                ownership.attemptId,
                ownership.generation,
            ),
        )
        onState(G7ProtocolState.CONNECTING)
        val event =
            try {
                withTimeout(G7_DIRECT_CONNECT_CALLBACK_TIMEOUT_MS) { connectionEvents.receive() }
            } catch (timeout: TimeoutCancellationException) {
                recordDirectResult(DirectConnectResult.NO_CALLBACK)
                throw G7BleException(G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, "Direkter G7-Connect ohne Callback", true, timeout)
            }
        val connectStatus = event.status
        val connectState = event.state
        recordDirectResult(classifyDirectConnectCallback(connectStatus, connectState), connectStatus, connectState, event.epochMs, event.elapsedMs)
        if (connectStatus != BluetoothGatt.GATT_SUCCESS || connectState != BluetoothProfile.STATE_CONNECTED) {
            if (connectStatus == GATT_ERROR_133) {
                throw G7BleException(G7_GATT_133_ERROR_CODE, "Temporärer BLE-Verbindungsfehler (133)", true)
            }
            throw G7BleException("G7-GATT-201", "Verbindung zum Sensor fehlgeschlagen ($connectStatus)", true)
        }

        onState(G7ProtocolState.DISCOVERING_SERVICES)
        val current = requireNotNull(gatt)
        if (!current.discoverServices()) throw G7BleException("G7-GATT-202", "Dienstsuche konnte nicht gestartet werden", true)
        val discoveryStatus =
            try {
                withTimeout(OPERATION_TIMEOUT_MS) { serviceEvents.receive() }
            } catch (timeout: TimeoutCancellationException) {
                throw G7BleException(G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE, "G7-Dienstsuche ohne Callback", true, timeout)
            }
        if (discoveryStatus != BluetoothGatt.GATT_SUCCESS) {
            throw G7BleException("G7-GATT-203", "G7-Dienste konnten nicht gelesen werden ($discoveryStatus)", true)
        }
        val service =
            current.getService(G7GattProfile.serviceUuid)
                ?: throw G7BleException("G7-GATT-204", "Dexcom-G7-Dienst fehlt", true)
        val authenticationCharacteristic = service.requireCharacteristic(G7GattProfile.authenticationUuid, "G7-GATT-205")
        val extraCharacteristic = service.requireCharacteristic(G7GattProfile.extraDataUuid, "G7-GATT-206")
        val controlCharacteristic = service.requireCharacteristic(G7GattProfile.controlUuid, "G7-GATT-207")
        val backfillCharacteristic = service.getCharacteristic(G7GattProfile.backfillUuid)

        onState(G7ProtocolState.ENABLING_NOTIFICATIONS)
        enable(current, extraCharacteristic, indication = false)
        enable(current, authenticationCharacteristic, indication = true)

        onState(G7ProtocolState.AUTHENTICATION_START)
        authentication.connected()
        sendNext(current, authenticationCharacteristic, extraCharacteristic, authentication, onState)

        while (true) {
            val (uuid, payload) = notifications.receive()
            when (uuid) {
                G7GattProfile.extraDataUuid -> {
                    if (runAuthentication("G7-AUTH-202") { authentication.onExtraData(payload) }) {
                        sendNext(current, authenticationCharacteristic, extraCharacteristic, authentication, onState)
                    }
                }

                G7GattProfile.authenticationUuid -> {
                    if (authentication.shouldBond(payload)) {
                        val key =
                            authentication.sharedKey()
                                ?: throw G7BleException("G7-AUTH-203", "Sitzungsschlüssel konnte nicht erzeugt werden", true)
                        onSharedKey(key)
                        onState(G7ProtocolState.BONDING)
                        ensureBonded(device)
                        if (!connected) throw G7BondReconnectRequired(key)
                        enable(current, controlCharacteristic, indication = true)
                        backfillCharacteristic?.let { enable(current, it, indication = false) }
                        onState(G7ProtocolState.REQUESTING_GLUCOSE)
                        write(current, controlCharacteristic, GLUCOSE_REQUEST, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    }
                    if (runAuthentication("G7-AUTH-204") { authentication.onAuthenticationData(payload) }) {
                        val next = authentication.next()
                        if (next != null && next.size == 1 && next[0]?.contentEquals(GLUCOSE_REQUEST) == true) {
                            authentication.sharedKey()?.let(onSharedKey)
                            enable(current, controlCharacteristic, indication = true)
                            backfillCharacteristic?.let { enable(current, it, indication = false) }
                            onState(G7ProtocolState.AUTHENTICATED)
                            onState(G7ProtocolState.REQUESTING_GLUCOSE)
                            write(current, controlCharacteristic, GLUCOSE_REQUEST, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        } else {
                            sendNext(current, authenticationCharacteristic, extraCharacteristic, next, onState)
                        }
                    }
                }

                G7GattProfile.controlUuid -> {
                    onState(G7ProtocolState.RECEIVING_GLUCOSE)
                    if (payload.firstOrNull() != GLUCOSE_REQUEST.first()) continue
                    val live =
                        runCatching { parser.parse(payload, sensor, System.currentTimeMillis()) }
                            .getOrElse { throw G7BleException("G7-DATA-301", "Ungültiges Glukosepaket empfangen", true, it) }
                    // Publish the validated live packet before optional history transfer. Pairing
                    // success and the current dashboard must never wait for backfill completion.
                    onLiveReading(live)
                    val start = G7CollectorBackfillProtocol.requestedStart(lastStoredSensorClock, live.sensorClockSeconds ?: 0L)
                    if (start == null || backfillCharacteristic == null) return live to emptyList()

                    onState(G7ProtocolState.BACKFILL)
                    // The live sample has already arrived on the control characteristic. G7's
                    // history range ends at the preceding five-minute slot; including the live
                    // clock makes affected firmware finish without returning the missing records.
                    val end =
                        G7CollectorBackfillProtocol.requestedEnd(requireNotNull(live.sensorClockSeconds))
                            ?: return live to emptyList()
                    if (start > end) return live to emptyList()
                    val request = G7CollectorBackfillProtocol.request(start, end)
                    onTelemetry(
                        G7BackfillRequestTelemetry(
                            timestampEpochMs = System.currentTimeMillis(),
                            attemptId = ownership.attemptId,
                            gattGeneration = ownership.generation,
                            startSensorClock = start,
                            endSensorClock = end,
                        ),
                    )
                    write(current, controlCharacteristic, request, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    val historical = mutableListOf<G7Reading>()
                    // History is a stream on 3536 followed by the 0x59 completion response on
                    // the control characteristic. Waiting only 2.5 seconds after the first record
                    // truncated real Watch transfers to a single value, especially while Wear OS
                    // throttled the BLE callback stream. Consume until the protocol completion
                    // marker (with bounded total/idle guards), and tolerate an Android callback
                    // containing more than one complete 9-byte record.
                    withTimeoutOrNull(BACKFILL_TOTAL_TIMEOUT_MS) {
                        backfill@ while (historical.size < MAX_BACKFILL_RECORDS) {
                            val event =
                                withTimeoutOrNull(BACKFILL_IDLE_TIMEOUT_MS) { notifications.receive() }
                                    ?: break@backfill
                            when (event.first) {
                                G7GattProfile.backfillUuid ->
                                    event.second
                                        .asList()
                                        .chunked(G7CollectorBackfillProtocol.RECORD_BYTES)
                                        .filter { it.size == G7CollectorBackfillProtocol.RECORD_BYTES }
                                        .forEach { record ->
                                            runCatching {
                                                G7CollectorBackfillProtocol.parseRecord(
                                                    record.toByteArray(),
                                                    sensor,
                                                    live,
                                                    System.currentTimeMillis(),
                                                )
                                            }.getOrNull()?.let(historical::add)
                                        }

                                G7GattProfile.controlUuid ->
                                    if (event.second.firstOrNull() == G7CollectorBackfillProtocol.REQUEST_OPCODE) {
                                        break@backfill
                                    }
                            }
                        }
                    }
                    return live to historical
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureBonded(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        if (!device.createBond()) throw G7BleException("G7-AUTH-206", "Bluetooth-Verbindung konnte nicht gestartet werden", false)

        // createBond() is asynchronous. On real Wear OS hardware it can return true a few
        // milliseconds before bondState changes from NONE to BONDING. Treating that initial NONE
        // as a terminal failure closes the GATT client while Android is only just starting SMP.
        var observedBonding = false
        try {
            withTimeout(BOND_TIMEOUT_MS) {
                while (true) {
                    val bondState = device.bondState
                    when (g7BondWaitDecision(bondState, observedBonding)) {
                        G7BondWaitDecision.BONDED, G7BondWaitDecision.FAILED -> return@withTimeout
                        G7BondWaitDecision.KEEP_WAITING -> Unit
                    }
                    if (bondState == BluetoothDevice.BOND_BONDING) observedBonding = true
                    delay(BOND_STATE_POLL_MS)
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Converted below into the stable, user-facing authentication error.
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            // The system consent UI is time limited. Missing that UI once must not leave initial
            // setup permanently dead; the normal scheduler may offer it again in a later sensor
            // window without deleting bonds, keys, or application data.
            throw G7BleException("G7-AUTH-209", "Bluetooth-Verbindung wurde nicht bestätigt", true)
        }
    }

    private suspend fun sendNext(
        gatt: BluetoothGatt,
        authCharacteristic: BluetoothGattCharacteristic,
        extraCharacteristic: BluetoothGattCharacteristic,
        authentication: G7AuthenticationSession,
        onState: (G7ProtocolState) -> Unit,
    ) = sendNext(gatt, authCharacteristic, extraCharacteristic, authentication.next(), onState)

    private suspend fun sendNext(
        gatt: BluetoothGatt,
        authCharacteristic: BluetoothGattCharacteristic,
        extraCharacteristic: BluetoothGattCharacteristic,
        packets: Array<ByteArray?>?,
        onState: (G7ProtocolState) -> Unit,
    ) {
        if (packets == null) throw G7BleException("G7-AUTH-210", "Authentifizierung lieferte keinen nächsten Schritt", true)
        if (packets.size ==
            3
        ) {
            throw G7BleException(
                "G7-AUTH-211",
                "Gespeicherte Verbindung ist ungültig; Sensor in Bluetooth entfernen und neu einrichten",
                false,
            )
        }
        if (packets.size == 1) return
        require(packets.size == 2) { "Unexpected G7 authentication packet count" }
        onState(G7ProtocolState.AUTHENTICATING)
        packets[1]?.let { data ->
            data.asList().chunked(EXTRA_CHUNK_BYTES).forEach { chunk ->
                write(
                    gatt,
                    extraCharacteristic,
                    chunk.toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                    awaitCallback = false,
                )
                delay(EXTRA_CHUNK_DELAY_MS)
            }
            delay(EXTRA_SETTLE_DELAY_MS)
        }
        packets[0]?.let { command ->
            write(gatt, authCharacteristic, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enable(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        indication: Boolean,
    ) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            throw G7BleException("G7-GATT-208", "G7-Benachrichtigung konnte nicht aktiviert werden", true)
        }
        val descriptor =
            characteristic.getDescriptor(G7GattProfile.clientConfigurationUuid)
                ?: throw G7BleException("G7-GATT-209", "G7-Benachrichtigungsdeskriptor fehlt", true)
        val value = if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (gatt.writeDescriptor(descriptor, value) != BluetoothStatusCodes.SUCCESS) {
            throw G7BleException("G7-GATT-210", "G7-Benachrichtigung konnte nicht konfiguriert werden", true)
        }
        val (uuid, status) =
            try {
                withTimeout(OPERATION_TIMEOUT_MS) { descriptorEvents.receive() }
            } catch (timeout: TimeoutCancellationException) {
                throw G7BleException(G7_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE, "G7-Descriptor ohne Callback", true, timeout)
            }
        if (uuid != characteristic.uuid || status != BluetoothGatt.GATT_SUCCESS) {
            throw G7BleException("G7-GATT-211", "G7-Benachrichtigung wurde abgelehnt ($status)", true)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        awaitCallback: Boolean = true,
    ) {
        val writeStatus = gatt.writeCharacteristic(characteristic, value, writeType)
        if (writeStatus != BluetoothStatusCodes.SUCCESS) {
            throw G7BleException("G7-GATT-212", "G7-Daten konnten nicht gesendet werden · status=$writeStatus · characteristic=${characteristic.uuid} · type=$writeType", true)
        }
        if (!awaitCallback) return
        try {
            withTimeout(OPERATION_TIMEOUT_MS) {
                while (true) {
                    val (uuid, status) = writeEvents.receive()
                    val disposition = classifyG7WriteCallback(characteristic.uuid, uuid, status)
                    when (disposition) {
                        G7WriteCallbackDisposition.EXPECTED_SUCCESS -> return@withTimeout
                        else ->
                            if (shouldFailCurrentG7Write(disposition)) {
                                throw G7BleException("G7-GATT-213", "G7-Daten wurden abgelehnt ($status)", true)
                            }
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw G7BleException(G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE, "G7-Schreibvorgang ohne Callback", true, timeout)
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        if (!closeStarted.compareAndSet(false, true)) return
        val current = gatt
        val cleanupStartedAt = System.currentTimeMillis()
        G7GattGenerationRegistry.invalidate(ownership)
        gatt = null
        connected = false
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
        onTelemetry(
            G7GattCleanupTelemetry(
                cleanupStartedAt,
                ownership.attemptId,
                ownership.generation,
                disconnected = current != null,
                started = true,
            ),
        )
        onTelemetry(
            G7GattCleanupTelemetry(
                System.currentTimeMillis(),
                ownership.attemptId,
                ownership.generation,
                disconnected = current != null,
                started = false,
            ),
        )
        connectionEvents.close()
        serviceEvents.close()
        descriptorEvents.close()
        writeEvents.close()
        notifications.close()
    }

    private fun acceptCallback(
        callbackGatt: BluetoothGatt,
        callbackName: String,
    ): Boolean {
        if (G7GattGenerationRegistry.isActive(ownership) && (gatt == null || callbackGatt === gatt)) {
            if (gatt == null) gatt = callbackGatt
            return true
        }
        onTelemetry(
            G7StaleGattCallbackTelemetry(
                System.currentTimeMillis(),
                ownership.attemptId,
                ownership.generation,
                callbackName,
            ),
        )
        return false
    }

    private fun recordDirectResult(
        result: DirectConnectResult,
        status: Int? = null,
        newState: Int? = null,
        epochMs: Long = System.currentTimeMillis(),
        elapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        if (!directResultRecorded.compareAndSet(false, true)) return
        val startElapsed = connectStartedElapsedMs ?: SystemClock.elapsedRealtime()
        onTelemetry(
            G7DirectConnectCompleted(
                timestampEpochMs = epochMs,
                elapsedRealtimeMs = elapsedMs,
                durationMs = (elapsedMs - startElapsed).coerceAtLeast(0L),
                result = result,
                status = status,
                newState = newState,
                attemptId = ownership.attemptId,
                gattGeneration = ownership.generation,
            ),
        )
    }

    private fun requirePermission(
        permission: String,
        code: String,
    ) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw G7BleException(code, "Bluetooth-Berechtigung fehlt", false)
        }
    }

    private fun BluetoothGattService.requireCharacteristic(
        uuid: UUID,
        code: String,
    ): BluetoothGattCharacteristic = getCharacteristic(uuid) ?: throw G7BleException(code, "Erforderliche G7-Eigenschaft fehlt", true)

    private inline fun <T> runAuthentication(
        code: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: SecurityException) {
            throw authenticationProcessingFailure(code, error)
        } catch (error: IllegalArgumentException) {
            throw authenticationProcessingFailure(code, error)
        }

    private companion object {
        val GLUCOSE_REQUEST = byteArrayOf(0x4e)
        const val BACKFILL_IDLE_TIMEOUT_MS = 10_000L
        const val BACKFILL_TOTAL_TIMEOUT_MS = 45_000L
        const val MAX_BACKFILL_RECORDS = 300
        const val CONTROL_EVENT_BUFFER_CAPACITY = 16
        const val NOTIFICATION_BUFFER_CAPACITY = 512
        const val GATT_ERROR_133 = 133
        const val OPERATION_TIMEOUT_MS = 15_000L
        const val BOND_TIMEOUT_MS = 35_000L
        const val BOND_STATE_POLL_MS = 250L
        const val EXTRA_CHUNK_BYTES = 20
        const val EXTRA_CHUNK_DELAY_MS = 40L
        const val EXTRA_SETTLE_DELAY_MS = 500L
    }
}

internal fun classifyDirectConnectCallback(
    status: Int,
    newState: Int,
): DirectConnectResult =
    when {
        status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> DirectConnectResult.SUCCESS
        status == 133 -> DirectConnectResult.STATUS_133
        status == 19 -> DirectConnectResult.STATUS_19
        status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED -> DirectConnectResult.DISCONNECTED_EARLY
        else -> DirectConnectResult.OTHER_STATUS
    }

internal fun maxGatt133RetriesForCycle(fallbackSensorConfirmed: Boolean): Int = if (fallbackSensorConfirmed) 2 else 1
