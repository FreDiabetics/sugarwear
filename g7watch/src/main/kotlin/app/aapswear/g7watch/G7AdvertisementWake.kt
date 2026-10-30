package app.aapswear.g7watch

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorDiagnosticResult
import app.aapswear.g7.CollectorDiagnosticStage

/**
 * Durable, filtered BLE wake-up for the already paired G7 sensor.
 *
 * AlarmManager remains a watchdog, but a five-minute CGM cadence must not depend on repeated
 * allow-while-idle alarms. Android can deliver a PendingIntent BLE scan result while the app
 * process is asleep, so the exact known sensor advertisement becomes the primary event that wakes
 * the bounded foreground collection cycle.
 */
internal object G7AdvertisementWakeScheduler {
    const val ACTION_SENSOR_ADVERTISEMENT = "app.aapswear.g7watch.G7_SENSOR_ADVERTISEMENT"
    private const val REQUEST_CODE = 7012
    private const val PREFS = "g7_advertisement_wake"
    private const val KEY_REGISTERED_ADDRESS = "registered_address"
    private const val KEY_REGISTRATION_STATUS = "registration_status"
    private const val KEY_REGISTRATION_AT = "registration_at"
    private const val KEY_LAST_CALLBACK_ERROR = "last_callback_error"
    private const val KEY_LAST_CALLBACK_ERROR_AT = "last_callback_error_at"
    private const val KEY_LAST_FORWARDED_AT = "last_forwarded_at"
    private const val KEY_FORWARDED_COUNT = "forwarded_count"
    private const val KEY_LAST_FORWARDED_SLOT = "last_forwarded_slot"

    fun arm(context: Context): Int? {
        val app = context.applicationContext
        val state = G7SensorStateStore(app).read()
        val address = state.sensor?.deviceAddress?.takeIf(String::isNotBlank)
        if (!state.collectorEnabled || address == null) {
            disarm(app)
            return null
        }
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            rememberRegistration(app, address, REGISTRATION_PERMISSION_MISSING)
            return REGISTRATION_PERMISSION_MISSING
        }
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            rememberRegistration(app, address, REGISTRATION_BLUETOOTH_UNAVAILABLE)
            return REGISTRATION_BLUETOOTH_UNAVAILABLE
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            rememberRegistration(app, address, REGISTRATION_SCANNER_UNAVAILABLE)
            return REGISTRATION_SCANNER_UNAVAILABLE
        }

        val pending = scanPendingIntent(app)
        runCatching { scanner.stopScan(pending) }
        val status =
            runCatching {
                scanner.startScan(
                    listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                    ScanSettings
                        .Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setReportDelay(0L)
                        .build(),
                    pending,
                )
            }.getOrElse { REGISTRATION_START_FAILED }
        rememberRegistration(app, address, status)
        return status
    }

    fun disarm(context: Context) {
        val app = context.applicationContext
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            val scanner =
                runCatching {
                    app.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
                }.getOrNull()
            scanner?.let { runCatching { it.stopScan(scanPendingIntent(app)) } }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_REGISTERED_ADDRESS)
            remove(KEY_REGISTRATION_STATUS)
            remove(KEY_REGISTRATION_AT)
        }
    }

    fun lastForwardedAt(context: Context): Long? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FORWARDED_AT, 0L)
            .takeIf { it > 0L }

    fun markForwarded(
        context: Context,
        nowEpochMs: Long,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_LAST_FORWARDED_AT, nowEpochMs)
            putLong(KEY_FORWARDED_COUNT, prefs.getLong(KEY_FORWARDED_COUNT, 0L) + 1L)
        }
    }

    fun lastForwardedSlot(context: Context): Long? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FORWARDED_SLOT, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }

    fun markForwardedSlot(
        context: Context,
        slotEpochMs: Long,
        nowEpochMs: Long,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_LAST_FORWARDED_SLOT, slotEpochMs)
            putLong(KEY_LAST_FORWARDED_AT, nowEpochMs)
            putLong(KEY_FORWARDED_COUNT, prefs.getLong(KEY_FORWARDED_COUNT, 0L) + 1L)
        }
    }

    fun markCallbackError(
        context: Context,
        errorCode: Int,
        nowEpochMs: Long,
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_LAST_CALLBACK_ERROR, errorCode)
            putLong(KEY_LAST_CALLBACK_ERROR_AT, nowEpochMs)
        }
    }

    private fun rememberRegistration(
        context: Context,
        address: String,
        status: Int,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_REGISTERED_ADDRESS, address)
            putInt(KEY_REGISTRATION_STATUS, status)
            putLong(KEY_REGISTRATION_AT, System.currentTimeMillis())
        }
    }

    private fun scanPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, G7AdvertisementWakeReceiver::class.java).setAction(ACTION_SENSOR_ADVERTISEMENT),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    const val REGISTRATION_PERMISSION_MISSING = -1001
    const val REGISTRATION_BLUETOOTH_UNAVAILABLE = -1002
    const val REGISTRATION_SCANNER_UNAVAILABLE = -1003
    const val REGISTRATION_START_FAILED = -1004
}

internal fun shouldForwardG7AdvertisementWake(
    collectorEnabled: Boolean,
    callbackErrorCode: Int,
    hasMatchingResult: Boolean,
    lastForwardedAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (!collectorEnabled || callbackErrorCode != 0 || !hasMatchingResult) return false
    val previous = lastForwardedAtEpochMs ?: return true
    if (nowEpochMs < previous) return true
    return nowEpochMs - previous >= G7_ADVERTISEMENT_WAKE_THROTTLE_MS
}

internal fun shouldForwardG7AdvertisementForSlot(
    collectorEnabled: Boolean,
    hasActiveCycle: Boolean,
    callbackErrorCode: Int,
    hasMatchingResult: Boolean,
    expectedSlotEpochMs: Long?,
    lastForwardedSlotEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (!collectorEnabled || hasActiveCycle || callbackErrorCode != 0 || !hasMatchingResult) return false
    val slot = expectedSlotEpochMs ?: return false
    if (lastForwardedSlotEpochMs == slot) return false
    return kotlin.math.abs(nowEpochMs - slot) <= G7_ADVERTISEMENT_SLOT_WINDOW_MS
}

internal const val G7_ADVERTISEMENT_WAKE_THROTTLE_MS = 45_000L
internal const val G7_ADVERTISEMENT_SLOT_WINDOW_MS = 90_000L

class G7AdvertisementWakeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != G7AdvertisementWakeScheduler.ACTION_SENSOR_ADVERTISEMENT) return
        val callbackError = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        val results =
            intent
                .getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                    ScanResult::class.java,
                ).orEmpty()
        val pending = goAsync()
        G7ReceiverWork
            .dispatcher { app, _ -> handleAdvertisement(app, callbackError, results) }
            .dispatch(context, intent.action, onFinished = pending::finish)
    }

    private fun handleAdvertisement(
        app: Context,
        callbackError: Int,
        results: List<ScanResult>,
    ) {
        val state = G7SensorStateStore(app).read()
        if (!state.collectorEnabled) {
            G7AdvertisementWakeScheduler.disarm(app)
            return
        }

        val now = System.currentTimeMillis()
        val effectiveStrategy =
            effectiveG7ReconnectStrategy(
                G7ReconnectStrategyStore.read(app),
                state.health,
            )
        if (effectiveStrategy != G7ReconnectStrategy.BOUNDED_SCAN) {
            G7AdvertisementWakeScheduler.disarm(app)
            return
        }
        if (callbackError != 0) {
            // The AlarmManager watchdog is already staged by the same scheduling operation that
            // armed this scan. Do not immediately re-arm from an error callback: that can create a
            // tight registration-failure loop on a broken Bluetooth stack.
            G7AdvertisementWakeScheduler.markCallbackError(app, callbackError, now)
            return
        }

        val knownAddress = state.sensor?.deviceAddress
        val hasMatchingResult =
            knownAddress != null &&
                results.any { result ->
                    runCatching { result.device.address.equals(knownAddress, ignoreCase = true) }.getOrDefault(false)
                }
        val diagnostics = G7CollectorDiagnosticStore(app)
        val expectedSlot = diagnostics.pendingScheduledCycle()?.expectedReadingEpoch
        if (!shouldForwardG7AdvertisementForSlot(
                collectorEnabled = state.collectorEnabled,
                hasActiveCycle = diagnostics.hasActiveAttempt() || state.activeAttemptId != null,
                callbackErrorCode = callbackError,
                hasMatchingResult = hasMatchingResult,
                expectedSlotEpochMs = expectedSlot,
                lastForwardedSlotEpochMs = G7AdvertisementWakeScheduler.lastForwardedSlot(app),
                nowEpochMs = now,
            )
        ) {
            return
        }

        G7AdvertisementWakeScheduler.markForwardedSlot(app, requireNotNull(expectedSlot), now)
        G7WakeHandoff.acquire(app)
        runCatching { G7CollectorService.startScheduledReconnect(app) }
            .onFailure { error ->
                G7WakeHandoff.release()
                G7ReconnectAlarmScheduler.scheduleRecovery(app, state, now)
                val attempt =
                    diagnostics.begin(
                        manual = false,
                        restart = false,
                        nowEpochMs = now,
                    )
                diagnostics.setClassification(
                    attempt.attemptId,
                    CollectorCycleClassification.SERVICE_START_FAILED,
                )
                diagnostics.record(
                    attempt.attemptId,
                    CollectorDiagnosticStage.ERROR,
                    CollectorDiagnosticResult.RECOVERABLE_ERROR,
                    "SERVICE_START_FAILED · G7-Advertisement erkannt, Foreground-Service konnte aber nicht gestartet werden; Recovery-Slot geplant (${error.javaClass.simpleName})",
                    nowEpochMs = System.currentTimeMillis(),
                )
            }
    }
}
