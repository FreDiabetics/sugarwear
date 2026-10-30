package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7SessionState
import app.aapswear.model.CgmPresentationPolicy
import app.aapswear.model.CgmPresentationStatus
import app.aapswear.model.CgmQuality
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.runBlocking

internal object G7AlertPolicyStore {
    private const val PREFS = "g7_alert_policy"
    private const val KEY_ALARMS_ENABLED = "alarms_enabled"
    private const val KEY_AUTOMATIC_ENABLE_AT = "automatic_enable_at"

    fun alarmsEnabled(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!G7SensorStateStore(context).read().collectorEnabled) return false
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

internal const val G7_SIGNAL_LOSS_AFTER_MS = CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS
private const val G7_SIGNAL_LOSS_RECOVERY_CHECK_MS = 15L * 60_000L

/**
 * Keeps a missed signal-loss deadline on a deterministic recovery cadence. Clamping an expired
 * deadline to now + 1 second caused every failed collector cycle to replace the alarm with another
 * immediate wakeup. Anchoring recovery checks to the original deadline prevents both drift and a
 * wakeup storm while retaining a maximum fifteen-minute recovery check interval.
 */
internal fun nextG7SignalLossMonitorAt(
    signalLossDeadlineEpochMs: Long,
    nowEpochMs: Long,
): Long {
    if (signalLossDeadlineEpochMs > nowEpochMs) return signalLossDeadlineEpochMs
    val elapsed = nowEpochMs - signalLossDeadlineEpochMs
    val completedIntervals = elapsed / G7_SIGNAL_LOSS_RECOVERY_CHECK_MS
    return signalLossDeadlineEpochMs + (completedIntervals + 1L) * G7_SIGNAL_LOSS_RECOVERY_CHECK_MS
}

internal fun isG7SignalLoss(
    lastReadingEpochMs: Long?,
    nowEpochMs: Long,
): Boolean =
    CgmPresentationPolicy.classify(lastReadingEpochMs, CgmQuality.VALID, nowEpochMs) ==
        CgmPresentationStatus.SIGNAL_LOSS

internal fun shouldPostImmediateCollectorAlert(
    alarmsEnabled: Boolean,
    error: G7CollectorError,
    sessionState: G7SessionState,
): Boolean {
    if (!alarmsEnabled) return false
    if (!error.recoverable) return true
    return sessionState == G7SessionState.USER_INTERVENTION_REQUIRED ||
        sessionState == G7SessionState.REQUIRES_REBOND ||
        sessionState == G7SessionState.REQUIRES_FULL_HANDSHAKE
}

internal object G7SignalLossMonitor {
    private const val REQUEST_CODE = 7011
    private const val MIN_TRIGGER_LEAD_MS = 1_000L

    fun scheduleFromState(
        context: Context,
        state: G7PersistedState,
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
            nextG7SignalLossMonitorAt(lastReading + G7_SIGNAL_LOSS_AFTER_MS, now),
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
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!G7SensorStateStore(context).read().collectorEnabled) return@synchronized
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
            Intent(context, G7SignalLossReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class G7SignalLossReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // State deserialization, alarm-channel creation and notification delivery may all touch
        // disk or system services. A BroadcastReceiver only has a few seconds on its main thread;
        // keeping the complete signal-loss path behind goAsync avoids ANRs that would kill the
        // collector process precisely while it is waiting for the next sensor advertisement.
        val pending = goAsync()
        G7ReceiverWork
            .dispatcher { app, _ ->
                try {
                    val state = G7SensorStateStore(app).read()
                    if (!state.collectorEnabled) return@dispatcher

                    val lastReadingAt = state.lastReading?.timestampEpochMs
                    val now = System.currentTimeMillis()
                    // The signal-loss alarm is an independent, already proven wake path. Let it
                    // repair a dead collector chain, but keep it out of the primary collection
                    // cadence: reconciliation only arms the next plausible G7 slot.
                    G7RuntimeReconciler.reconcile(
                        app,
                        G7RuntimeEntryPoint.SIGNAL_LOSS,
                        nowEpochMs = now,
                    )
                    if (!isG7SignalLoss(lastReadingAt, now)) {
                        G7SignalLossMonitor.scheduleFromState(app, state)
                        return@dispatcher
                    }

                    if (!G7AlertPolicyStore.alarmsEnabled(app)) {
                        val nextHealthCheck = now + G7_SIGNAL_LOSS_RECOVERY_CHECK_MS
                        val nextPolicyCheck = G7AlertPolicyStore.nextAutomaticEnableAt(app, now)
                        G7SignalLossMonitor.schedulePolicyRecheck(app, minOf(nextPolicyCheck ?: nextHealthCheck, nextHealthCheck))
                        runBlocking {
                            app.recordG7Diagnostic(
                                code = "G7-SIGNAL-LOSS-SUPPRESSED",
                                message = "Direct G7 signal loss suppressed because another canonical source is active",
                                severity = DiagnosticSeverity.INFO,
                                metadata = mapOf("lastReadingEpochMs" to lastReadingAt),
                            )
                        }
                        return@dispatcher
                    }

                    G7SignalLossMonitor.scheduleRecoveryHealthCheck(app, now + G7_SIGNAL_LOSS_RECOVERY_CHECK_MS)
                    G7CgmAlarmCoordinator.onSignalLoss(app, state.lastReading, now)
                } catch (error: Throwable) {
                    runBlocking {
                        app.recordG7Diagnostic(
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
