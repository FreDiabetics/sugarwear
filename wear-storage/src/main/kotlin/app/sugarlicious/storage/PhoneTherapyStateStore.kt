package app.sugarlicious.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.phoneTherapyDataStore by preferencesDataStore("phone_therapy_input_state")

/** Raw phone-fed state kept independent from the canonical Mobile display state. */
class PhoneTherapyStateStore(
    private val context: Context,
) {
    private val key = stringPreferencesKey("state_v1")
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    val state: Flow<TherapyDisplayState?> =
        context.phoneTherapyDataStore.data.map { preferences ->
            preferences[key]?.let { raw ->
                runCatching { json.decodeFromString<TherapyDisplayState>(raw) }
                    .onFailure { Log.w("SugarliciousStorage", "Phone input decode failed: ${it.javaClass.simpleName}") }
                    .getOrNull()
            }
        }

    suspend fun save(value: TherapyDisplayState) {
        context.phoneTherapyDataStore.edit { it[key] = json.encodeToString(value) }
    }
}
