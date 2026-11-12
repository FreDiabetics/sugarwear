package app.sugarwear

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.sensor.protocol.CgmReading
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
internal data class CgmHistoryBinding(
    val sensorId: String,
    val sessionId: String,
    val canonicalSensorId: String,
    val canonicalSessionId: String,
    val addressHash: String,
    val sensorStartEpochMs: Long,
)

/** Historical provenance only: no Bluetooth address, key, code or reconnect target is retained. */
internal class CgmHistoryIdentityStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences("g7_history_identity", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedRaw: String? = null
    private var cachedBindings = emptyList<CgmHistoryBinding>()

    private fun bindings(): List<CgmHistoryBinding> {
        val raw = preferences.getString("bindings", "[]") ?: "[]"
        if (raw != cachedRaw) {
            cachedBindings =
                try {
                    json.decodeFromString<List<CgmHistoryBinding>>(raw)
                } catch (error: kotlinx.serialization.SerializationException) {
                    android.util.Log.e("CgmHistoryIdentity", "Historical identity metadata could not be decoded", error)
                    emptyList()
                }
            cachedRaw = raw
        }
        return cachedBindings
    }

    fun canonical(sensorId: String, sessionId: String): Pair<String, String> {
        val binding = bindings().singleOrNull { it.sensorId == sensorId && it.sessionId == sessionId }
        return (binding?.canonicalSensorId ?: sensorId) to (binding?.canonicalSessionId ?: sessionId)
    }

    fun canonical(reading: CgmReading): CgmReading {
        val (sensor, session) = canonical(reading.sensorId, reading.sessionId)
        return if (sensor == reading.sensorId && session == reading.sessionId) reading else reading.copy(sensorId = sensor, sessionId = session)
    }

    /** Called only after successful sensor authentication and a validated LIVE packet. */
    fun rememberAuthenticated(address: String, sensorStart: Long, sensorId: String, sessionId: String) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            require(address.isNotBlank() && sensorStart > 0)
            val hash = hashAddress(address)
            val entries = bindings()
            val own = entries.singleOrNull { it.sensorId == sensorId && it.sessionId == sessionId }
            if (own != null) {
                // Sensor start is reconstructed from wall time. A clock correction must not
                // invalidate an already authenticated session or rewrite its historical origin.
                check(own.addressHash == hash) { "Conflicting authenticated history identity" }
                return@synchronized
            }
            val matches =
                entries
                    .filter { it.addressHash == hash && kotlin.math.abs(it.sensorStartEpochMs - sensorStart) <= START_TOLERANCE_MS }
                    .map { it.canonicalSensorId to it.canonicalSessionId }
                    .distinct()
            check(matches.size <= 1) { "Ambiguous historical sensor identity" }
            val canonical = matches.singleOrNull() ?: (sensorId to sessionId)
            val binding = CgmHistoryBinding(sensorId, sessionId, canonical.first, canonical.second, hash, sensorStart)
            preferences.edit(commit = true) { putString("bindings", json.encodeToString(entries + binding)) }
            cachedRaw = null
        }

    companion object {
        private const val START_TOLERANCE_MS = 90_000L

        fun hashAddress(address: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(address.uppercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
