package app.sugarwear

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState

internal enum class CgmUiSensorAction { PAUSE, RESUME, UNLINK, PAIR }

internal data class CgmUiSensorTransition(
    val action: CgmUiSensorAction,
    val startedAt: Long,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val completed: Boolean = false,
    val connectedAt: Long? = null,
)

internal data class CgmCollectorAttemptPresentation(
    val title: String,
    val phase: String,
    val expiresAt: Long? = null,
)

internal fun collectorAttemptPresentation(state: CgmPersistedState, transition: CgmUiSensorTransition?, now: Long): CgmCollectorAttemptPresentation? {
    transition ?: return null
    if (transition.action == CgmUiSensorAction.PAIR) return null
    if (transition.startedAt > now) return null
    if (transition.action == CgmUiSensorAction.RESUME) {
        if (transition.completed || cgmHasResumedLiveValue(state, transition, now)) return null
        if (state.sensor != null && !cgmResumeMatchesSession(state, transition)) return null
        return CgmCollectorAttemptPresentation(
            "Verbindung wird hergestellt",
            if (!state.collectorEnabled) {
                "Verbindung wird vorbereitet"
            } else if ((transition.connectedAt ?: state.lastSuccessfulConnectionEpochMs ?: 0L) >= transition.startedAt) {
                "Verbunden – warte auf Glukosewert"
            } else {
                cgmPairingProgress(state, transition.startedAt).message
            },
        )
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
        val sensor = CgmSensorStateStore(context).read().sensor
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString("action", action.name)
            putLong("started_at", System.currentTimeMillis())
            putString("sensor_id", sensor?.sensorId)
            putString("session_id", sensor?.sessionId)
            putBoolean("completed", false)
            remove("connected_at")
        }
    }

    fun completeResume(context: Context, transition: CgmUiSensorTransition) {
        if (read(context) == transition) context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { putBoolean("completed", true) }
    }

    fun confirmConnection(context: Context, transition: CgmUiSensorTransition, now: Long): Boolean =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (read(context) != transition || transition.connectedAt != null) return@synchronized false
            val write = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            write.putLong("connected_at", now)
            write.commit()
        }

    fun read(context: Context): CgmUiSensorTransition? {
        val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val action = preferences.getString("action", null)?.let { runCatching { CgmUiSensorAction.valueOf(it) }.getOrNull() } ?: return null
        return CgmUiSensorTransition(action, preferences.getLong("started_at", 0L), preferences.getString("sensor_id", null), preferences.getString("session_id", null), preferences.getBoolean("completed", false), preferences.getLong("connected_at", 0L).takeIf { it > 0L })
    }
}
