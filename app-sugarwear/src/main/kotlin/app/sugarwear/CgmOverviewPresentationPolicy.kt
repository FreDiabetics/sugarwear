package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus

internal const val CGM_PAUSED_VALUE_DURATION_MS = 15 * 60_000L

internal enum class CgmOverviewMode { NO_SENSOR, PAUSED, BLUETOOTH_OFF, PERMISSION_REQUIRED, SENSOR_ERROR, CONNECTING, SIGNAL_LOSS, LIVE, ERROR }

internal data class CgmOverviewPresentation(
    val mode: CgmOverviewMode,
    val reading: CgmReading? = null,
    val phase: String? = null,
    val animate: Boolean = false,
    val connectionConfirmed: Boolean = false,
)

internal fun cgmReadingBelongsToSession(state: CgmPersistedState, reading: CgmReading?): Boolean =
    reading != null && state.sensor?.let { it.sensorId == reading.sensorId && (it.sessionId == null || it.sessionId == reading.sessionId) } == true

internal fun cgmResumeMatchesSession(state: CgmPersistedState, transition: CgmUiSensorTransition): Boolean =
    state.sensor != null &&
        transition.sensorId != null &&
        state.sensor?.sensorId == transition.sensorId &&
        (transition.sessionId == null || state.sensor?.sessionId == transition.sessionId)

internal fun cgmHasResumedLiveValue(state: CgmPersistedState, transition: CgmUiSensorTransition, now: Long): Boolean {
    val reading = state.lastReading ?: return false
    return cgmResumeMatchesSession(state, transition) &&
        cgmReadingBelongsToSession(state, reading) &&
        reading.status == CgmReadingStatus.VALID &&
        !reading.displayOnly &&
        reading.origin == CgmReadingOrigin.LIVE &&
        reading.timestampEpochMs > transition.startedAt &&
        reading.receivedAtEpochMs >= transition.startedAt &&
        reading.timestampEpochMs <= now &&
        now - reading.timestampEpochMs < CGM_SIGNAL_LOSS_AFTER_MS &&
        (transition.connectedAt ?: state.lastSuccessfulConnectionEpochMs ?: 0L) >= transition.startedAt
}

/** Single UI projection. Alarm visibility never participates in sensor status selection. */
internal fun cgmOverviewPresentation(
    state: CgmPersistedState,
    credentialsPresent: Boolean,
    transition: CgmUiSensorTransition?,
    now: Long,
    bluetoothEnabled: Boolean = true,
    bluetoothPermission: Boolean = true,
    lastValidReading: CgmReading? = null,
): CgmOverviewPresentation {
    val status = deriveCgmStatusPillState(state, credentialsPresent, now)
    if (status == CgmStatusPillState.NO_ACTIVE_SENSOR) return CgmOverviewPresentation(CgmOverviewMode.NO_SENSOR)
    val reading = state.lastReading?.takeIf { cgmReadingBelongsToSession(state, it) && it.status == CgmReadingStatus.VALID && !it.displayOnly && it.glucoseMgDl.isFinite() && it.timestampEpochMs <= now }
    if (!state.collectorEnabled) {
        val pause = transition?.takeIf { it.action == CgmUiSensorAction.PAUSE && cgmResumeMatchesSession(state, it) }
        val previous = reading ?: lastValidReading?.takeIf { cgmReadingBelongsToSession(state, it) && it.status == CgmReadingStatus.VALID && !it.displayOnly && it.timestampEpochMs <= now }
        val historical = previous?.takeIf { pause != null && now >= pause.startedAt && now - pause.startedAt < CGM_PAUSED_VALUE_DURATION_MS }
        return CgmOverviewPresentation(CgmOverviewMode.PAUSED, historical)
    }
    if (!bluetoothPermission) return CgmOverviewPresentation(CgmOverviewMode.PERMISSION_REQUIRED, phase = "Bluetooth-Berechtigung erforderlich")
    if (!bluetoothEnabled) return CgmOverviewPresentation(CgmOverviewMode.BLUETOOTH_OFF)
    if (status == CgmStatusPillState.SENSOR_ERROR) return CgmOverviewPresentation(CgmOverviewMode.SENSOR_ERROR)
    val resume = transition?.takeIf { it.action in setOf(CgmUiSensorAction.RESUME, CgmUiSensorAction.PAIR) && !it.completed && it.startedAt <= now && cgmResumeMatchesSession(state, it) }
    if (resume != null && !cgmHasResumedLiveValue(state, resume, now)) {
        val progress = cgmPairingProgress(state, resume.startedAt)
        if (isTerminalCgmPairingFailure(state, resume.startedAt, now)) return CgmOverviewPresentation(CgmOverviewMode.ERROR, phase = progress.message)
        val connected = (resume.connectedAt ?: state.lastSuccessfulConnectionEpochMs ?: 0L) >= resume.startedAt
        return CgmOverviewPresentation(CgmOverviewMode.CONNECTING, phase = if (connected) "Verbunden – warte auf Glukosewert" else progress.message, animate = !connected && progress.animate, connectionConfirmed = connected)
    }
    if (status == CgmStatusPillState.SIGNAL_LOSS) return CgmOverviewPresentation(CgmOverviewMode.SIGNAL_LOSS)
    return if (reading != null) CgmOverviewPresentation(CgmOverviewMode.LIVE, reading) else CgmOverviewPresentation(CgmOverviewMode.ERROR, phase = "Keine aktuellen Daten")
}

internal enum class CgmGlucoseTileMode { ALARM, UNLINK_ERROR, PAIRING, STOP, SENSOR }

internal fun cgmGlucoseTileMode(hasPendingAlarm: Boolean, pairingStep: CgmPairingScreenStep?, stopRequested: Boolean, unlinkFailed: Boolean = false): CgmGlucoseTileMode =
    when {
        hasPendingAlarm -> CgmGlucoseTileMode.ALARM
        unlinkFailed -> CgmGlucoseTileMode.UNLINK_ERROR
        pairingStep != null -> CgmGlucoseTileMode.PAIRING
        stopRequested -> CgmGlucoseTileMode.STOP
        else -> CgmGlucoseTileMode.SENSOR
    }
