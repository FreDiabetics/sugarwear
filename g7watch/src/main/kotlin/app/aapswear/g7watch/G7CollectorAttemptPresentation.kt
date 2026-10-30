package app.aapswear.g7watch

import android.content.Context
import androidx.core.content.edit
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState

internal enum class G7UiSensorAction { PAUSE, RESUME, UNLINK }

internal data class G7UiSensorTransition(
    val action: G7UiSensorAction,
    val startedAt: Long,
)

internal data class G7CollectorAttemptPresentation(
    val title: String,
    val phase: String,
    val expiresAt: Long? = null,
)

internal fun collectorAttemptPresentation(state: G7PersistedState, transition: G7UiSensorTransition?, now: Long): G7CollectorAttemptPresentation? {
    transition ?: return null
    if (transition.startedAt > now) return null
    if (transition.action == G7UiSensorAction.RESUME) {
        if ((state.lastSuccessfulConnectionEpochMs ?: 0L) >= transition.startedAt) return null
        return G7CollectorAttemptPresentation("Verbindung wird hergestellt", if (!state.collectorEnabled) "Verbindung wird vorbereitet" else g7PairingProgress(state, transition.startedAt).message)
    }
    val expiry = transition.startedAt + 120_000L
    if (now >= expiry) return null
    val detached = transition.action == G7UiSensorAction.UNLINK && !hasG7SensorAssociation(state)
    val title =
        if (detached) {
            "Kein Sensor verbunden"
        } else if (transition.action == G7UiSensorAction.UNLINK) {
            "Sensor wird getrennt"
        } else {
            "Datenempfang pausiert"
        }
    val phase = if (state.connectionState == G7ConnectionState.DISCONNECTED) "Verbindung getrennt" else "Verbindung wird getrennt"
    return G7CollectorAttemptPresentation(title, phase, expiry)
}

internal object G7CollectorUiTransitionStore {
    private const val NAME = "g7_ui_sensor_transition"

    fun record(context: Context, action: G7UiSensorAction) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString("action", action.name)
            putLong("started_at", System.currentTimeMillis())
        }
    }

    fun read(context: Context): G7UiSensorTransition? {
        val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val action = preferences.getString("action", null)?.let { runCatching { G7UiSensorAction.valueOf(it) }.getOrNull() } ?: return null
        return G7UiSensorTransition(action, preferences.getLong("started_at", 0L))
    }
}
