package app.aapswear.g7watch

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import app.aapswear.g7.G7PersistedState
import kotlinx.serialization.json.Json

internal class G7SensorStateStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("g7_collector_state", Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun read(): G7PersistedState =
        preferences
            .getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString<G7PersistedState>(it) }.getOrNull() }
            ?: G7PersistedState()

    fun save(state: G7PersistedState) =
        synchronized(lifecycleLock) {
            // Authentication material is intentionally not part of G7PersistedState. Once the
            // protocol is validated, secrets must be stored with Android Keystore protection.
            if (read().collectorEnabled != state.collectorEnabled) lifecycleGeneration++
            preferences.edit { putString(KEY_STATE, json.encodeToString(G7PersistedState.serializer(), state)) }
        }

    /** Reject an in-flight cycle's snapshot after an intentional pause. */
    fun collectorGeneration(): Long = synchronized(lifecycleLock) { lifecycleGeneration }

    fun saveFromCollector(
        state: G7PersistedState,
        expectedGeneration: Long = collectorGeneration(),
    ): Boolean =
        synchronized(lifecycleLock) {
            if (!read().collectorEnabled || expectedGeneration != lifecycleGeneration) return@synchronized false
            save(state)
            true
        }

    companion object {
        internal val lifecycleLock = Any()
        private var lifecycleGeneration = 0L
        private const val KEY_STATE = "state"
    }
}

internal class G7RuntimeRecycleStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences("g7_runtime_recovery", Context.MODE_PRIVATE)

    fun lastRecycleAtEpochMs(): Long? =
        preferences.getLong(KEY_LAST_RECYCLE_AT, 0L).takeIf { it > 0L }

    @Synchronized
    @SuppressLint("UseKtx") // The Boolean commit result is the ownership claim result.
    fun tryClaim(nowEpochMs: Long): Boolean {
        val previous = lastRecycleAtEpochMs()
        if (previous != null && nowEpochMs - previous < G7_RUNTIME_RECYCLE_COOLDOWN_MS) return false
        return preferences.edit().putLong(KEY_LAST_RECYCLE_AT, nowEpochMs).commit()
    }

    private companion object {
        const val KEY_LAST_RECYCLE_AT = "last_runtime_recycle_at"
    }
}

internal fun maskedPairingCode(pairingCode: String?): String =
    if (pairingCode.isNullOrBlank()) "—" else "•".repeat(pairingCode.length)

internal fun pairingCodeDisplayValue(pairingCode: String?, revealed: Boolean): String =
    if (revealed) pairingCode ?: "—" else maskedPairingCode(pairingCode)
