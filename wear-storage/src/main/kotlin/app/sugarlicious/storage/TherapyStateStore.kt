package app.sugarlicious.storage
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.DailyTimeInRange
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val Context.therapyDataStore by preferencesDataStore("therapy_display_state")

class TherapyStateStore(
    private val context: Context,
) {
    private val key = stringPreferencesKey("state_v1")
    private val longTermArchive = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) null else LongTermGlucoseArchive.get(context.applicationContext)
    private val importedArchiveKey = booleanPreferencesKey("long_term_archive_imported_v1")

    /** Emits only after local measured archive changes; no polling and no Wear payload. */
    val archiveRevision: Flow<Long> = LongTermGlucoseArchive.revision

    suspend fun rangeHistory(startEpochMs: Long, now: Long = System.currentTimeMillis()): List<GlucoseSample> =
        historyMutex.withLock {
            importPreviousArchive(now)
            try {
                longTermArchive?.read(startEpochMs, now).orEmpty()
            } catch (error: SQLiteException) {
                Log.w("SugarliciousStorage", "Range archive unavailable: ${error.javaClass.simpleName}")
                emptyList()
            }
        }

    private suspend fun importPreviousArchive(now: Long) {
        val archive = longTermArchive ?: return
        val preferences = context.therapyDataStore.data.first()
        if (preferences[importedArchiveKey] == true) return
        val previous = preferences[archiveKey]?.let { runCatching { json.decodeFromString<List<GlucoseSample>>(it) }.getOrNull() }.orEmpty()
        try {
            archive.record(previous, now)
        } catch (error: SQLiteException) {
            Log.w("SugarliciousStorage", "Range archive import deferred: ${error.javaClass.simpleName}")
            return
        }
        context.therapyDataStore.edit { it[importedArchiveKey] = true }
    }

    private val archiveKey = stringPreferencesKey("daily_tir_history_v1")

    /** Account changes must clear both historical stores before the new account is composed. */
    suspend fun removeSourceHistory(source: DataSourceId) =
        historyMutex.withLock {
            context.therapyDataStore.edit { preferences ->
                val old = preferences[archiveKey]?.let { json.decodeFromString<List<GlucoseSample>>(it) }.orEmpty()
                preferences[archiveKey] = json.encodeToString(old.filter { it.source != source })
            }
            longTermArchive?.removeSource(source)
        }

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    val state: Flow<TherapyDisplayState?> =
        context.therapyDataStore.data.map { preferences ->
            preferences[key]?.let { raw ->
                runCatching { json.decodeFromString<TherapyDisplayState>(raw) }
                    .onFailure { Log.w("SugarliciousStorage", "State decode failed: ${it.javaClass.simpleName}") }
                    .getOrNull()
            }
        }

    suspend fun save(value: TherapyDisplayState) {
        context.therapyDataStore.edit { it[key] = json.encodeToString(value) }
        recordHistory(value)
    }

    /** Separate bounded historical cache: never replaces the resolver's current display state. */
    suspend fun recordHistory(value: TherapyDisplayState?, now: Long = System.currentTimeMillis()): List<GlucoseSample> =
        historyMutex.withLock {
            importPreviousArchive(now)
            var longTermIncoming = emptyList<GlucoseSample>()
            var result = emptyList<GlucoseSample>()
            context.therapyDataStore.edit { preferences ->
                val old = preferences[archiveKey]?.let { runCatching { json.decodeFromString<List<GlucoseSample>>(it) }.getOrNull() }.orEmpty()
                val incoming =
                    value?.glucoseHistory.orEmpty() +
                        listOfNotNull(
                            value?.glucose?.let {
                                GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality)
                            },
                        )
                longTermIncoming = incoming
                result = CanonicalCgmHistory.merge(old + incoming, now, windowMs = DailyTimeInRange.ARCHIVE_WINDOW_MS, futureToleranceMs = 0, maxPoints = DailyTimeInRange.ARCHIVE_MAX_POINTS)
                if (result != old) preferences[archiveKey] = json.encodeToString(result)
            }
            try {
                longTermArchive?.record(longTermIncoming, now)
            } catch (error: SQLiteException) {
                // Historical enrichment must never suppress current CGM delivery or alarms.
                Log.w("SugarliciousStorage", "Range archive update deferred: ${error.javaClass.simpleName}")
            }
            result
        }

    companion object {
        private val historyMutex = Mutex()
    }
}
