package app.sugarwear

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState

internal enum class CgmUiSensorAction { PAUSE, RESUME, UNLINK }

internal data class CgmUiSensorTransition(
    val action: CgmUiSensorAction,
    val startedAt: Long,
)

internal data class CgmCollectorAttemptPresentation(
    val title: String,
    val phase: String,
    val expiresAt: Long? = null,
)

internal fun collectorAttemptPresentation(state: CgmPersistedState, transition: CgmUiSensorTransition?, now: Long): CgmCollectorAttemptPresentation? {
    transition ?: return null
    if (transition.startedAt > now) return null
    if (transition.action == CgmUiSensorAction.RESUME) {
        if ((state.lastSuccessfulConnectionEpochMs ?: 0L) >= transition.startedAt) return null
        return CgmCollectorAttemptPresentation("Verbindung wird hergestellt", if (!state.collectorEnabled) "Verbindung wird vorbereitet" else cgmPairingProgress(state, transition.startedAt).message)
    }
    val expiry = transition.startedAt + 120_000L
    if (now >= expiry) return null
    val detached = transition.action == CgmUiSensorAction.UNLINK && !hasCgmSensorAssociation(state)
    val title =
        if (detached) {
            "Kein Sensor verbunden"
        } else if (transition.action == CgmUiSensorAction.UNLINK) {
            "Sensor wird getrennt"
        } else {
            "Datenempfang pausiert"
        }
    val phase = if (state.connectionState == CgmConnectionState.DISCONNECTED) "Verbindung getrennt" else "Verbindung wird getrennt"
    return CgmCollectorAttemptPresentation(title, phase, expiry)
}

internal object CgmCollectorUiTransitionStore {
    private const val NAME = "g7_ui_sensor_transition"

    fun record(context: Context, action: CgmUiSensorAction) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString("action", action.name)
            putLong("started_at", System.currentTimeMillis())
        }
    }

    fun read(context: Context): CgmUiSensorTransition? {
        val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val action = preferences.getString("action", null)?.let { runCatching { CgmUiSensorAction.valueOf(it) }.getOrNull() } ?: return null
        return CgmUiSensorTransition(action, preferences.getLong("started_at", 0L))
    }
}
