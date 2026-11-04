package app.sugarwear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.sugarlicious.model.CgmPresentationPolicy
import app.sugarlicious.model.CgmPresentationStatus
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSessionState
import kotlinx.coroutines.runBlocking

internal object CgmAlertPolicyStore {
    private const val PREFS = "g7_alert_policy"
    private const val KEY_ALARMS_ENABLED = "alarms_enabled"
    private const val KEY_AUTOMATIC_ENABLE_AT = "automatic_enable_at"

    fun alarmsEnabled(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!CgmSensorStateStore(context).read().collectorEnabled) return false
        val preferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return preferences.getBoolean(KEY_ALARMS_ENABLED, false) ||
            preferences.getLong(KEY_AUTOMATIC_ENABLE_AT, 0L).let { it > 0L && nowEpochMs >= it }
    }

    fun nextAutomaticEnableAt(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Long? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_AUTOMATIC_ENABLE_AT, 0L)
            .takeIf { it > nowEpochMs }

    fun setPolicy(
        context: Context,
        enabled: Boolean,
        automaticEnableAtEpochMs: Long? = null,
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ALARMS_ENABLED, enabled)
            putLong(KEY_AUTOMATIC_ENABLE_AT, automaticEnableAtEpochMs?.takeIf { it > 0L } ?: 0L)
        }
    }
}

internal const val CGM_SIGNAL_LOSS_AFTER_MS = CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS
private const val CGM_SIGNAL_LOSS_RECOVERY_CHECK_MS = 15L * 60_000L

/**
 * Keeps a missed signal-loss deadline on a deterministic recovery cadence. Clamping an expired
 * deadline to now + 1 second caused every failed collector cycle to replace the alarm with another
 * immediate wakeup. Anchoring recovery checks to the original deadline prevents both drift and a
 * wakeup storm while retaining a maximum fifteen-minute recovery check interval.
 */
internal fun nextCgmSignalLossMonitorAt(
    signalLossDeadlineEpochMs: Long,
    nowEpochMs: Long,
): Long {
    if (signalLossDeadlineEpochMs > nowEpochMs) return signalLossDeadlineEpochMs
    val elapsed = nowEpochMs - signalLossDeadlineEpochMs
    val completedIntervals = elapsed / CGM_SIGNAL_LOSS_RECOVERY_CHECK_MS
    return signalLossDeadlineEpochMs + (completedIntervals + 1L) * CGM_SIGNAL_LOSS_RECOVERY_CHECK_MS
}

internal fun isCgmSignalLoss(
    lastReadingEpochMs: Long?,
    nowEpochMs: Long,
): Boolean =
    CgmPresentationPolicy.classify(lastReadingEpochMs, CgmQuality.VALID, nowEpochMs) ==
        CgmPresentationStatus.SIGNAL_LOSS

internal fun shouldPostImmediateCollectorAlert(
    alarmsEnabled: Boolean,
    error: CgmCollectorError,
    sessionState: CgmSessionState,
): Boolean {
    if (!alarmsEnabled) return false
    if (!error.recoverable) return true
    return sessionState == CgmSessionState.USER_INTERVENTION_REQUIRED ||
        sessionState == CgmSessionState.REQUIRES_REBOND ||
        sessionState == CgmSessionState.REQUIRES_FULL_HANDSHAKE
}

internal object CgmSignalLossMonitor {
    private const val REQUEST_CODE = 7011
    private const val MIN_TRIGGER_LEAD_MS = 1_000L

    fun scheduleFromState(
        context: Context,
        state: CgmPersistedState,
    ) {
        if (!state.collectorEnabled) {
            cancel(context)
            return
        }
        val lastReading =
            state.lastReading?.timestampEpochMs ?: run {
                cancel(context)
                return
            }
        val now = System.currentTimeMillis()
        schedule(
            context,
            nextCgmSignalLossMonitorAt(lastReading + CGM_SIGNAL_LOSS_AFTER_MS, now),
            now,
        )
    }

    fun cancel(context: Context) {
        val pending = pendingIntent(context)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun schedulePolicyRecheck(
        context: Context,
        atEpochMs: Long,
    ) {
        schedule(context, atEpochMs)
    }

    fun scheduleRecoveryHealthCheck(
        context: Context,
        atEpochMs: Long,
    ) {
        schedule(context, atEpochMs)
    }

    private fun schedule(
        context: Context,
        requestedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmSensorStateStore(context).read().collectorEnabled) return@synchronized
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val triggerAt = maxOf(requestedAtEpochMs, nowEpochMs + MIN_TRIGGER_LEAD_MS)
        val pending = pendingIntent(app)
        val exactAllowed = alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CgmSignalLossReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class CgmSignalLossReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // State deserialization, alarm-channel creation and notification delivery may all touch
        // disk or system services. A BroadcastReceiver only has a few seconds on its main thread;
        // keeping the complete signal-loss path behind goAsync avoids ANRs that would kill the
        // collector process precisely while it is waiting for the next sensor advertisement.
        val pending = goAsync()
        CgmReceiverWork
            .dispatcher { app, _ ->
                try {
                    val state = CgmSensorStateStore(app).read()
                    if (!state.collectorEnabled) return@dispatcher

                    val lastReadingAt = state.lastReading?.timestampEpochMs
                    val now = System.currentTimeMillis()
                    // The signal-loss alarm is an independent, already proven wake path. Let it
                    // repair a dead collector chain, but keep it out of the primary collection
                    // cadence: reconciliation only arms the next plausible G7 slot.
                    CgmRuntimeReconciler.reconcile(
                        app,
                        CgmRuntimeEntryPoint.SIGNAL_LOSS,
                        nowEpochMs = now,
                    )
                    if (!isCgmSignalLoss(lastReadingAt, now)) {
                        CgmSignalLossMonitor.scheduleFromState(app, state)
                        return@dispatcher
                    }

                    if (!CgmAlertPolicyStore.alarmsEnabled(app)) {
                        val nextHealthCheck = now + CGM_SIGNAL_LOSS_RECOVERY_CHECK_MS
                        val nextPolicyCheck = CgmAlertPolicyStore.nextAutomaticEnableAt(app, now)
                        CgmSignalLossMonitor.schedulePolicyRecheck(app, minOf(nextPolicyCheck ?: nextHealthCheck, nextHealthCheck))
                        runBlocking {
                            app.recordCgmDiagnostic(
                                code = "G7-SIGNAL-LOSS-SUPPRESSED",
                                message = "Direct G7 signal loss suppressed because another canonical source is active",
                                severity = DiagnosticSeverity.INFO,
                                metadata = mapOf("lastReadingEpochMs" to lastReadingAt),
                            )
                        }
                        return@dispatcher
                    }

                    CgmSignalLossMonitor.scheduleRecoveryHealthCheck(app, now + CGM_SIGNAL_LOSS_RECOVERY_CHECK_MS)
                    CgmAlarmCoordinator.onSignalLoss(app, state.lastReading, now)
                } catch (error: Throwable) {
                    runBlocking {
                        app.recordCgmDiagnostic(
                            code = "G7-SIGNAL-LOSS-500",
                            message = "Asynchronous signal-loss evaluation failed",
                            severity = DiagnosticSeverity.ERROR,
                            metadata = mapOf("errorType" to error.javaClass.simpleName.take(40)),
                        )
                    }
                }
            }.dispatch(context, intent.action, onFinished = pending::finish)
    }
}
