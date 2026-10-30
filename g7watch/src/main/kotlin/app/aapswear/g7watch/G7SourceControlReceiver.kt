package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Source-selection signal from Sugarlicious Wear.
 *
 * Source selection and collector lifecycle remain separate. Changing the canonical display source
 * must never persist collectorEnabled=false. The central resolver separately supplies whether
 * Watch Direct is currently canonical and therefore allowed to raise user-facing alarms.
 */
class G7SourceControlReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SET_SOURCE) return

        val g7Selected = intent.getBooleanExtra(EXTRA_G7_SELECTED, false)
        val alarmsEnabled =
            if (intent.hasExtra(EXTRA_ALARMS_ENABLED)) {
                intent.getBooleanExtra(EXTRA_ALARMS_ENABLED, false)
            } else {
                g7Selected
            }
        val automaticEnableAt =
            intent.getLongExtra(EXTRA_AUTOMATIC_ENABLE_AT, 0L).takeIf { it > 0L }
        val pending = goAsync()
        G7ReceiverWork
            .dispatcher { app, _ -> applySourceControl(app, g7Selected, alarmsEnabled, automaticEnableAt) }
            .dispatch(context, intent.action, onFinished = pending::finish)
    }

    companion object {
        const val ACTION_SET_SOURCE = "app.aapswear.g7watch.SET_SOURCE"
        const val EXTRA_G7_SELECTED = "g7_selected"
        const val EXTRA_ALARMS_ENABLED = "alarms_enabled"
        const val EXTRA_AUTOMATIC_ENABLE_AT = "automatic_enable_at"
    }
}

internal fun applySourceControl(
    context: Context,
    g7Selected: Boolean,
    alarmsEnabled: Boolean,
    automaticEnableAt: Long?,
) {
    G7AlertPolicyStore.setPolicy(context, alarmsEnabled, automaticEnableAt)
    val state = G7SensorStateStore(context).read()

    if (!alarmsEnabled) {
        G7ErrorNotifier.clearActive(context)
        G7CgmAlarmCoordinator.clearSuppressed(context)
    } else {
        // The reading that caused Automatic mode to fail over may have arrived before this
        // policy broadcast. Evaluate it now so the source transition cannot miss an alarm.
        latestAlarmCandidate(context, state)?.let { G7CgmAlarmCoordinator.onReading(context, it) }
        G7CgmAlarmCoordinator.restore(context)
        G7SignalLossMonitor.scheduleFromState(context, state)
    }

    if (!shouldResumeEnabledCollectorForSourceSignal(g7Selected, state.collectorEnabled)) return
    runCatching { G7CollectorService.resumeEnabled(context) }
}

private fun latestAlarmCandidate(
    context: Context,
    state: app.aapswear.g7.G7PersistedState,
): app.aapswear.g7.CgmReading? {
    val sensor = state.sensor ?: return null
    val persisted =
        runCatching {
            G7ReadingDatabase(context).let { database ->
                try {
                    database
                        .query(
                            selection = "status IN (?,?) AND sensor_id=? AND session_id=?",
                            args = arrayOf("VALID", "SENSOR_ERROR", sensor.sensorId, sensor.sessionId.orEmpty()),
                            limit = 1,
                        ).firstOrNull()
                } finally {
                    database.close()
                }
            }
        }.getOrNull()
    return persisted ?: state.lastReading?.takeIf {
        it.sensorId == sensor.sensorId && it.sessionId == sensor.sessionId
    }
}

internal fun shouldResumeEnabledCollectorForSourceSignal(
    g7Selected: Boolean,
    collectorEnabled: Boolean,
): Boolean = g7Selected && collectorEnabled
