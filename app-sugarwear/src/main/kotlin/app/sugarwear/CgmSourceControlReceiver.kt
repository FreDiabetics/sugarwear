package app.sugarwear

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
class CgmSourceControlReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SET_SOURCE) return

        val cgmSelected = intent.getBooleanExtra(EXTRA_CGM_SELECTED, false)
        val alarmsEnabled =
            if (intent.hasExtra(EXTRA_ALARMS_ENABLED)) {
                intent.getBooleanExtra(EXTRA_ALARMS_ENABLED, false)
            } else {
                cgmSelected
            }
        val automaticEnableAt =
            intent.getLongExtra(EXTRA_AUTOMATIC_ENABLE_AT, 0L).takeIf { it > 0L }
        val pending = goAsync()
        CgmReceiverWork
            .dispatcher { app, _ -> applySourceControl(app, cgmSelected, alarmsEnabled, automaticEnableAt) }
            .dispatch(context, intent.action, onFinished = pending::finish)
    }

    companion object {
        const val ACTION_SET_SOURCE = "app.aapswear.g7watch.SET_SOURCE"
        const val EXTRA_CGM_SELECTED = "g7_selected"
        const val EXTRA_ALARMS_ENABLED = "alarms_enabled"
        const val EXTRA_AUTOMATIC_ENABLE_AT = "automatic_enable_at"
    }
}

internal fun applySourceControl(
    context: Context,
    cgmSelected: Boolean,
    alarmsEnabled: Boolean,
    automaticEnableAt: Long?,
) {
    CgmAlertPolicyStore.setPolicy(context, alarmsEnabled, automaticEnableAt)
    val state = CgmSensorStateStore(context).read()

    if (!alarmsEnabled) {
        CgmErrorNotifier.clearActive(context)
        CgmAlarmCoordinator.clearSuppressed(context)
    } else {
        // The reading that caused Automatic mode to fail over may have arrived before this
        // policy broadcast. Evaluate it now so the source transition cannot miss an alarm.
        latestAlarmCandidate(context, state)?.let { CgmAlarmCoordinator.onReading(context, it) }
        CgmAlarmCoordinator.restore(context)
        CgmSignalLossMonitor.scheduleFromState(context, state)
    }

    if (!shouldResumeEnabledCollectorForSourceSignal(cgmSelected, state.collectorEnabled)) return
    runCatching { CgmCollectorService.resumeEnabled(context) }
}

private fun latestAlarmCandidate(
    context: Context,
    state: app.sugarlicious.sensor.protocol.CgmPersistedState,
): app.sugarlicious.sensor.protocol.CgmReading? {
    val sensor = state.sensor ?: return null
    val persisted =
        runCatching {
            CgmReadingDatabase(context).let { database ->
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
    cgmSelected: Boolean,
    collectorEnabled: Boolean,
): Boolean = cgmSelected && collectorEnabled
