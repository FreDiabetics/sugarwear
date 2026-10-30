package app.aapswear.g7watch

import android.os.SystemClock
import app.aapswear.g7.G7Sensor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Invocation-local candidate state, never a persisted sensor identity or exclusion. */
internal class G7DiscoveryCandidates(
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val rejected = ConcurrentHashMap.newKeySet<String>()
    private val deferredUntil = ConcurrentHashMap<String, Long>()

    fun clear() {
        rejected.clear()
        deferredUntil.clear()
    }

    fun reject(address: String) {
        rejected.add(address.uppercase(Locale.ROOT))
    }

    fun defer(address: String) {
        deferredUntil[address.uppercase(Locale.ROOT)] = elapsedRealtimeMs() + CANDIDATE_COOLDOWN_MS
    }

    fun accepts(address: String): Boolean {
        val key = address.uppercase(Locale.ROOT)
        if (key in rejected) return false
        val until = deferredUntil[key] ?: return true
        if (elapsedRealtimeMs() < until) return false
        deferredUntil.remove(key, until)
        return true
    }

    companion object {
        internal const val CANDIDATE_COOLDOWN_MS = 20_000L
    }
}

/** A discovered peer has not earned known-sensor retries until authentication identifies it. */
internal fun shouldDeferUnverifiedG7Candidate(
    initialSensor: G7Sensor,
    hasSessionKey: Boolean,
    errorCode: String,
    recoverable: Boolean,
): Boolean =
    initialSensor.deviceAddress.isNullOrBlank() &&
        !hasSessionKey &&
        recoverable &&
        errorCode in setOf(G7_GATT_133_ERROR_CODE, G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, "G7-GATT-201")
