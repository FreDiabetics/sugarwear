package app.sugarwear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReconnectScheduler
import app.sugarlicious.sensor.protocol.CollectorAlarmKind
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the durable recovery wake paths for the Watch collector.
 *
 * The exact/inexact AlarmManager slot is the primary wake path. The legacy bounded-scan strategy
 * may additionally register a filtered PendingIntent scan; direct reconnect deliberately disarms
 * it so the OS does not scan continuously between sensor windows. A future alarm is staged before
 * risky BLE/GATT work begins, so process
 * death during a cycle cannot strand the collector.
 */
internal object CgmReconnectAlarmScheduler {
    private const val REQUEST_CODE = 0
    private const val MIN_TRIGGER_LEAD_MS = 1_000L

    /** Idempotent lifecycle entry point: retain a valid future slot or rebuild exactly one. */
    fun ensureCollectorSchedule(
        context: Context,
        state: CgmPersistedState,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CollectorCycleTiming? {
        if (!state.collectorEnabled || state.sensor == null) return null
        val pending = CgmCollectorDiagnosticStore(context.applicationContext).pendingScheduledCycle()
        val validPending =
            pending?.takeIf {
                it.requestedReconnectEpoch?.let { requested -> requested > nowEpochMs + MIN_TRIGGER_LEAD_MS } == true &&
                    it.expectedWindowId == expectedWindowId(state, it.expectedReadingEpoch ?: Long.MIN_VALUE)
            }
        return validPending?.let { rearmScheduledCycle(context, it, state) }
            ?: scheduleRecovery(context, state, nowEpochMs)
    }

    fun scheduleForState(
        context: Context,
        state: CgmPersistedState,
    ): CollectorCycleTiming? {
        if (!state.collectorEnabled) return null
        val requestedAt = state.nextReconnectEpochMs ?: return null
        return scheduleRequested(
            context,
            requestedAt,
            directReconnect = directReconnectAvailable(context, state),
        )
    }

    fun scheduleSafetyForCycle(
        context: Context,
        scheduledCycle: CollectorCycleTiming?,
        state: CgmPersistedState,
        nowEpochMs: Long,
    ): CollectorCycleTiming? {
        if (!state.collectorEnabled) return null
        val requestedAt = nextSafetyReconnectEpoch(scheduledCycle, state, nowEpochMs) ?: return null
        val expectedAt =
            scheduledCycle?.expectedReadingEpoch?.plus(CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS)
                ?: requestedAt + CgmReconnectScheduler.PRECONNECT_LEAD_MS
        return scheduleRequested(
            context,
            requestedAt,
            expectedAt,
            directReconnect = directReconnectAvailable(context, state),
        )
    }

    fun scheduleRecovery(
        context: Context,
        state: CgmPersistedState,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CollectorCycleTiming? {
        if (!state.collectorEnabled) return null
        val plan = CgmReconnectScheduler.afterExpectedWindowMiss(nowEpochMs, state.lastReading?.timestampEpochMs)
        return scheduleRequested(
            context,
            plan.nextReconnectEpochMs,
            directReconnect = directReconnectAvailable(context, state),
        )
    }

    /** Reasserts an already calculated slot without changing its cadence or PendingIntent identity. */
    fun rearmScheduledCycle(
        context: Context,
        cycle: CollectorCycleTiming,
        state: CgmPersistedState,
    ): CollectorCycleTiming {
        val requested =
            cycle.requestedReconnectEpoch
                ?: error("Scheduled G7 cycle has no reconnect time")
        return scheduleRequested(
            context = context,
            requestedReconnectEpochMs = requested,
            expectedReadingEpochMs =
                cycle.expectedReadingEpoch
                    ?: (requested + CgmReconnectScheduler.PRECONNECT_LEAD_MS),
            directReconnect = directReconnectAvailable(context, state),
        )
    }

    fun scheduleRequested(
        context: Context,
        requestedReconnectEpochMs: Long,
        expectedReadingEpochMs: Long = requestedReconnectEpochMs + CgmReconnectScheduler.PRECONNECT_LEAD_MS,
        directReconnect: Boolean = false,
    ): CollectorCycleTiming =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (!CgmSensorStateStore(context).read().collectorEnabled) throw kotlinx.coroutines.CancellationException("Collector paused")
            val app = context.applicationContext

            if (directReconnect) {
                CgmAdvertisementWakeScheduler.disarm(app)
            } else {
                CgmAdvertisementWakeScheduler.arm(app)
            }

            val strategyRequest =
                alignReconnectRequestToStrategy(
                    requestedReconnectEpochMs,
                    expectedReadingEpochMs,
                    directReconnect,
                )
            val triggerAt = maxOf(strategyRequest, System.currentTimeMillis() + MIN_TRIGGER_LEAD_MS)
            val pending = reconnectPendingIntent(app)
            val alarmManager = app.getSystemService(AlarmManager::class.java)
            val exactAllowed = alarmManager.canScheduleExactAlarms()
            val exactScheduled =
                if (exactAllowed) {
                    runCatching {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        true
                    }.getOrElse {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        false
                    }
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    false
                }
            val power = app.getSystemService(PowerManager::class.java)
            val cycle =
                canonicalCollectorCycle(
                    ledger = CgmExpectedWindowLedger(app),
                    requestedReconnectEpochMs = triggerAt,
                    expectedReadingEpochMs = expectedReadingEpochMs,
                    exactScheduled = exactScheduled,
                    canScheduleExactAlarms = exactAllowed,
                    batteryUnrestricted = CgmBackgroundAccess.isBatteryUnrestricted(app),
                    deviceIdleMode = power.isDeviceIdleMode,
                    isInteractive = power.isInteractive,
                    charging = runCatching { app.getSystemService(BatteryManager::class.java).isCharging }.getOrNull(),
                )
            CgmCollectorDiagnosticStore(app).stageScheduledCycle(cycle)
            CgmSensorWindowWatchdog.arm(app, cycle)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                app.recordCgmDiagnostic(
                    "EXPECTED_WINDOW_CREATED",
                    "EXPECTED_WINDOW_CREATED · Primary und Watchdog armiert",
                    DiagnosticSeverity.INFO,
                    mapOf(
                        "expectedWindowId" to cycle.expectedWindowId,
                        "expectedAt" to cycle.expectedReadingEpoch,
                        "primaryAlarmScheduledAt" to triggerAt,
                        "watchdogScheduledAt" to
                            (requireNotNull(cycle.expectedReadingEpoch) + CgmSensorWindowWatchdog.WINDOW_TOLERANCE_MS),
                    ),
                )
                app.recordCgmDiagnostic(
                    "PRIMARY_ALARM_ARMED",
                    "PRIMARY_ALARM_ARMED · ${cycle.alarmKind.name}",
                    DiagnosticSeverity.INFO,
                    mapOf("expectedWindowId" to cycle.expectedWindowId, "requestedReconnectEpoch" to triggerAt),
                )
            }
            cycle
        }

    fun cancel(context: Context) {
        val app = context.applicationContext
        CgmAdvertisementWakeScheduler.disarm(app)
        CgmSensorWindowWatchdog.cancel(app)
        val pending = reconnectPendingIntent(app)
        app.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
        CgmCollectorDiagnosticStore(app).discardScheduledCycle()
    }

    private fun reconnectPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CgmReconnectReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun directReconnectAvailable(
        context: Context,
        state: CgmPersistedState,
    ): Boolean =
        shouldUseDirectReconnect(
            effectiveCgmReconnectStrategy(
                CgmReconnectStrategyStore.read(context.applicationContext),
                state.health,
            ),
            state.sensor?.deviceAddress,
        )
}

internal fun canonicalCollectorCycle(
    ledger: CgmExpectedWindowLedger,
    requestedReconnectEpochMs: Long,
    expectedReadingEpochMs: Long,
    exactScheduled: Boolean,
    canScheduleExactAlarms: Boolean = exactScheduled,
    batteryUnrestricted: Boolean = false,
    deviceIdleMode: Boolean = false,
    isInteractive: Boolean = false,
    charging: Boolean? = null,
): CollectorCycleTiming {
    val alarmKind = if (exactScheduled) CollectorAlarmKind.EXACT else CollectorAlarmKind.INEXACT
    val canonical = ledger.create(expectedReadingEpochMs, requestedReconnectEpochMs, alarmKind)
    return CollectorCycleTiming(
        expectedWindowId = canonical.expectedWindowId,
        expectedReadingEpoch = canonical.expectedAt,
        requestedReconnectEpoch = requestedReconnectEpochMs,
        alarmKind = alarmKind,
        canScheduleExactAlarms = canScheduleExactAlarms,
        batteryUnrestricted = batteryUnrestricted,
        deviceIdleMode = deviceIdleMode,
        isInteractive = isInteractive,
        charging = charging,
    )
}

private fun expectedWindowId(
    state: CgmPersistedState,
    expectedAt: Long,
): String = expectedWindowId(state.sensor?.sensorId, state.sensor?.sessionId ?: state.sensor?.sensorId, expectedAt)

// Healthy direct connections retain the short lead. A NO_CALLBACK failure switches the next
// cycle to presence-first recovery, which preserves the canonical 30 s scan lead instead.
internal const val CGM_DIRECT_PRECONNECT_LEAD_MS = 10_000L

internal fun alignReconnectRequestToStrategy(
    requestedReconnectEpochMs: Long,
    expectedReadingEpochMs: Long,
    directReconnect: Boolean,
): Long =
    if (directReconnect) {
        maxOf(requestedReconnectEpochMs, expectedReadingEpochMs - CGM_DIRECT_PRECONNECT_LEAD_MS)
    } else {
        requestedReconnectEpochMs
    }

internal fun nextSafetyReconnectEpoch(
    scheduledCycle: CollectorCycleTiming?,
    state: CgmPersistedState,
    nowEpochMs: Long,
): Long? {
    if (!state.collectorEnabled) return null
    scheduledCycle?.expectedReadingEpoch?.let { expected ->
        return expected +
            CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS -
            CgmReconnectScheduler.PRECONNECT_LEAD_MS
    }
    state.nextReconnectEpochMs?.takeIf { it > nowEpochMs + 1_000L }?.let { return it }
    return CgmReconnectScheduler
        .afterExpectedWindowMiss(
            nowEpochMs,
            state.lastReading?.timestampEpochMs,
        ).nextReconnectEpochMs
}

internal fun stagedSafetyCycle(
    currentCycle: CollectorCycleTiming?,
    pendingCycle: CollectorCycleTiming?,
): CollectorCycleTiming? {
    val currentExpected = currentCycle?.expectedReadingEpoch ?: return null
    val expectedSafetySlot = currentExpected + CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS
    return pendingCycle?.takeIf { it.expectedReadingEpoch == expectedSafetySlot }
}
