package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.runBlocking

internal data class G7WatchdogDecision(
    val missed: Boolean,
    val reason: String,
)

internal fun evaluateG7WindowWatchdog(
    expectedAt: Long,
    nowEpochMs: Long,
    primaryTriggeredAt: Long?,
    cycleStartedAt: Long?,
    readingReceivedAt: Long?,
    finalResult: CollectorCycleClassification?,
    activeCycle: Boolean,
    plausibleFutureTriggerEpochMs: Long?,
    toleranceMs: Long = G7SensorWindowWatchdog.WINDOW_TOLERANCE_MS,
): G7WatchdogDecision {
    if (nowEpochMs <= expectedAt + toleranceMs) return G7WatchdogDecision(false, "WINDOW_NOT_DUE")
    if (readingReceivedAt != null || finalResult != null) return G7WatchdogDecision(false, "WINDOW_COMPLETED")
    if (activeCycle) return G7WatchdogDecision(false, "CYCLE_ACTIVE")
    if (plausibleFutureTriggerEpochMs != null && plausibleFutureTriggerEpochMs > nowEpochMs) {
        return G7WatchdogDecision(false, "FUTURE_RECOVERY_PRESENT")
    }
    return G7WatchdogDecision(
        true,
        when {
            cycleStartedAt != null -> "STALE_CYCLE_WITHOUT_RUNTIME"
            primaryTriggeredAt != null -> "SERVICE_START_FAILED"
            else -> "MISSED_SENSOR_WINDOW"
        },
    )
}

internal object G7SensorWindowWatchdog {
    const val WINDOW_TOLERANCE_MS = 45_000L
    private const val REQUEST_CODE = 7011
    private const val EXTRA_WINDOW_ID = "expectedWindowId"
    private const val EXTRA_EXPECTED_AT = "expectedAt"

    fun arm(
        context: Context,
        cycle: CollectorCycleTiming,
    ) {
        val expectedAt = cycle.expectedReadingEpoch ?: return
        val windowId = cycle.expectedWindowId ?: expectedWindowId(expectedAt)
        val triggerAt = expectedAt + WINDOW_TOLERANCE_MS
        val app = context.applicationContext
        val pending = pendingIntent(app, windowId, expectedAt)
        val alarms = app.getSystemService(AlarmManager::class.java)
        val exactAllowed = alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
                .getOrElse { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        G7ExpectedWindowLedger(app).markWatchdogScheduled(windowId, triggerAt)
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val pending = pendingIntent(app, null, 0L)
        app.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(
        context: Context,
        windowId: String?,
        expectedAt: Long,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, G7SensorWindowWatchdogReceiver::class.java).apply {
                putExtra(EXTRA_WINDOW_ID, windowId)
                putExtra(EXTRA_EXPECTED_AT, expectedAt)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun windowId(intent: Intent): String? = intent.getStringExtra(EXTRA_WINDOW_ID)

    fun expectedAt(intent: Intent): Long = intent.getLongExtra(EXTRA_EXPECTED_AT, 0L)
}

class G7SensorWindowWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val windowId = G7SensorWindowWatchdog.windowId(intent)
        val expectedAt = G7SensorWindowWatchdog.expectedAt(intent)
        if (windowId == null || expectedAt <= 0L) return
        val pendingResult = goAsync()
        G7ReceiverWork
            .dispatcher { app, _ -> handleWatchdog(app, windowId, expectedAt) }
            .dispatch(context, intent.action, onFinished = pendingResult::finish)
    }

    private fun handleWatchdog(
        app: Context,
        windowId: String,
        expectedAt: Long,
    ) {
        val now = System.currentTimeMillis()
        val ledger = G7ExpectedWindowLedger(app)
        ledger.markWatchdogTriggered(windowId, now)
        val window = ledger.window(windowId) ?: return
        val state = G7SensorStateStore(app).read()
        if (!state.collectorEnabled || state.sensor == null || G7CredentialStore(app).read() == null) return
        val pending = G7CollectorDiagnosticStore(app).pendingScheduledCycle()?.requestedReconnectEpoch
        val decision =
            evaluateG7WindowWatchdog(
                expectedAt = expectedAt,
                nowEpochMs = now,
                primaryTriggeredAt = window.primaryAlarmTriggeredAt,
                cycleStartedAt = window.cycleStartedAt,
                readingReceivedAt = window.readingReceivedAt,
                finalResult = window.finalResult,
                activeCycle = G7CollectorRuntimeRegistry.hasLiveCycle(),
                plausibleFutureTriggerEpochMs = pending,
            )
        runBlocking {
            app.recordG7Diagnostic(
                code = "WATCHDOG_FIRED",
                message = "WATCHDOG_FIRED · ${decision.reason}",
                severity = if (decision.missed) DiagnosticSeverity.WARNING else DiagnosticSeverity.INFO,
                metadata =
                    mapOf(
                        "expectedWindowId" to windowId,
                        "expectedAt" to expectedAt,
                        "latenessMs" to (now - expectedAt),
                        "primaryAlarmTriggeredAt" to window.primaryAlarmTriggeredAt,
                        "cycleStartedAt" to window.cycleStartedAt,
                        "futureTriggerEpochMs" to pending,
                    ),
            )
        }
        if (!decision.missed) return
        ledger.markFinal(windowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, recoveryRequired = true)
        runBlocking {
            app.recordG7Diagnostic(
                "WINDOW_RECOVERY_STARTED",
                "WINDOW_RECOVERY_STARTED · verpasstes Sensorfenster",
                DiagnosticSeverity.WARNING,
                mapOf("expectedWindowId" to windowId, "expectedAt" to expectedAt),
            )
        }
        G7RuntimeReconciler.reconcile(
            context = app,
            entryPoint = G7RuntimeEntryPoint.WATCHDOG,
            nowEpochMs = now,
        )
        val repaired = G7CollectorDiagnosticStore(app).pendingScheduledCycle()
        runBlocking {
            app.recordG7Diagnostic(
                code = "MISSED_SENSOR_WINDOW",
                message = "MISSED_SENSOR_WINDOW · Recovery-Zeitpfad wiederhergestellt",
                severity = DiagnosticSeverity.WARNING,
                metadata =
                    mapOf(
                        "expectedWindowId" to windowId,
                        "expectedAt" to expectedAt,
                        "nextReconnectEpochMs" to repaired?.requestedReconnectEpoch,
                    ),
            )
            app.recordG7Diagnostic(
                if (repaired?.requestedReconnectEpoch != null) "WINDOW_RECOVERY_SUCCESS" else "WINDOW_RECOVERY_FAILED",
                if (repaired?.requestedReconnectEpoch != null) {
                    "WINDOW_RECOVERY_SUCCESS · NEXT_WINDOW_REARMED"
                } else {
                    "WINDOW_RECOVERY_FAILED · kein zukünftiger Trigger"
                },
                if (repaired?.requestedReconnectEpoch != null) DiagnosticSeverity.INFO else DiagnosticSeverity.ERROR,
                mapOf("expectedWindowId" to windowId, "nextReconnectEpochMs" to repaired?.requestedReconnectEpoch),
            )
        }
    }
}
