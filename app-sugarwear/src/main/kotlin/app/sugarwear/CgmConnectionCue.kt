package app.sugarwear

import android.content.Context
import app.sugarlicious.sensor.protocol.CgmPersistedState

/** Durable claim precedes playback: recreation, duplicate callbacks and process restart are silent. */
internal object CgmConnectionCue {
    private const val NAME = "cgm_connection_cue"

    fun claim(context: Context, key: String): Boolean =
        synchronized(this) {
            val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            if (preferences.getString("claimed", null) == key) return@synchronized false
            val write = preferences.edit()
            write.putString("claimed", key)
            write.commit()
        }

    fun confirmed(context: Context, state: CgmPersistedState) {
        val transition = CgmCollectorUiTransitionStore.read(context)
        transition ?: return
        if (transition.action !in setOf(CgmUiSensorAction.RESUME, CgmUiSensorAction.PAIR) || !state.collectorEnabled || !cgmResumeMatchesSession(state, transition)) return
        val now = System.currentTimeMillis()
        if (isTerminalCgmPairingFailure(state, transition.startedAt, now)) return
        if (!cgmHasValidatedTransitionLiveValue(state, transition, now)) return
        if (!CgmCollectorUiTransitionStore.confirmConnection(context, transition, now)) return
        val key = "${transition.action}:${transition.startedAt}:${transition.sensorId}:${transition.sessionId}"
        if (claim(context, key)) android.os.Handler(android.os.Looper.getMainLooper()).post { playConnectionBeep(context) }
    }
}
