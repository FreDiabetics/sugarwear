package app.sugarlicious.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DailyTimeInRange
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Phone-local measured history. Never enters TherapyDisplayState or Wear transport. */
internal class LongTermGlucoseArchive(
    context: Context,
) : SQLiteOpenHelper(context, "measured_glucose_archive.db", null, 2) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE samples (time INTEGER NOT NULL, source TEXT NOT NULL, sensor TEXT NOT NULL, session TEXT NOT NULL, sequence TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(time,source,sensor,session,sequence))")
        db.execSQL("CREATE INDEX measured_time ON samples(time)")
        createBoundaries(db)
    }

    private fun createBoundaries(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS invalid_boundaries (time INTEGER NOT NULL, source TEXT NOT NULL, sensor TEXT NOT NULL, session TEXT NOT NULL, PRIMARY KEY(time,source,sensor,session))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createBoundaries(db)
    }

    suspend fun record(samples: List<GlucoseSample>, now: Long) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val valid = CanonicalCgmHistory.merge(samples, now, windowMs = DailyTimeInRange.LONG_TERM_ARCHIVE_WINDOW_MS, futureToleranceMs = 0, maxPoints = samples.size.coerceAtLeast(1))
                val db = writableDatabase
                var changed = false
                db.beginTransaction()
                try {
                    for (sample in valid) {
                        val identity = arrayOf(sample.measuredAtEpochMs.toString(), sample.source.name, sample.sensorId.orEmpty(), sample.sessionId.orEmpty(), sample.sequenceNumber?.toString().orEmpty())
                        val payload = json.encodeToString(sample)
                        val old = db.query("samples", arrayOf("payload"), "time=? AND source=? AND sensor=? AND session=? AND sequence=?", identity, null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }
                        changed = db.delete("invalid_boundaries", "time=? AND source=? AND sensor=? AND session=?", identity.take(4).toTypedArray()) > 0 || changed
                        if (payload == old) continue
                        val values =
                            ContentValues().apply {
                                put("time", sample.measuredAtEpochMs)
                                put("source", sample.source.name)
                                put("sensor", sample.sensorId.orEmpty())
                                put("session", sample.sessionId.orEmpty())
                                put("sequence", sample.sequenceNumber?.toString().orEmpty())
                                put("payload", payload)
                            }
                        db.insertWithOnConflict("samples", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        changed = true
                    }
                    // Invalid events are boundaries, never invented glucose values. A later
                    // validated correction clears a matching boundary above.
                    val validTimes = valid.map { listOf(it.measuredAtEpochMs.toString(), it.source.name, it.sensorId.orEmpty(), it.sessionId.orEmpty()) }.toSet()
                    samples
                        .filter { it.quality != CgmQuality.VALID || !it.valueMgDl.isFinite() || it.valueMgDl !in 20.0..1000.0 }
                        .filter { it.measuredAtEpochMs in (now - DailyTimeInRange.LONG_TERM_ARCHIVE_WINDOW_MS)..now }
                        .forEach { sample ->
                            val identity = listOf(sample.measuredAtEpochMs.toString(), sample.source.name, sample.sensorId.orEmpty(), sample.sessionId.orEmpty())
                            if (identity in validTimes) return@forEach
                            val values =
                                ContentValues().apply {
                                    put("time", sample.measuredAtEpochMs)
                                    put("source", sample.source.name)
                                    put("sensor", sample.sensorId.orEmpty())
                                    put("session", sample.sessionId.orEmpty())
                                }
                            if (db.insertWithOnConflict("invalid_boundaries", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) changed = true
                        }
                    changed = db.delete("invalid_boundaries", "time < ?", arrayOf((now - DailyTimeInRange.LONG_TERM_ARCHIVE_WINDOW_MS).toString())) > 0 || changed
                    changed = db.delete("samples", "time < ?", arrayOf((now - DailyTimeInRange.LONG_TERM_ARCHIVE_WINDOW_MS).toString())) > 0 || changed
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                if (changed) revision.value += 1
            }
        }

    suspend fun removeSource(source: DataSourceId) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val db = writableDatabase
                var changed = false
                db.beginTransaction()
                try {
                    changed = db.delete("samples", "source=?", arrayOf(source.name)) > 0
                    changed = db.delete("invalid_boundaries", "source=?", arrayOf(source.name)) > 0 || changed
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                if (changed) revision.value += 1
            }
        }

    suspend fun read(start: Long, now: Long): List<GlucoseSample> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val boundedStart = maxOf(start - 450_000L, now - DailyTimeInRange.LONG_TERM_ARCHIVE_WINDOW_MS)
                val raw =
                    readableDatabase.query("samples", arrayOf("payload"), "time >= ? AND time <= ?", arrayOf(boundedStart.toString(), now.toString()), null, null, "time ASC").use { cursor ->
                        buildList { while (cursor.moveToNext()) runCatching { json.decodeFromString<GlucoseSample>(cursor.getString(0)) }.getOrNull()?.let(::add) }
                    }
                val canonical = CanonicalCgmHistory.merge(raw, now, windowMs = (now - boundedStart).coerceAtLeast(1), futureToleranceMs = 0, maxPoints = raw.size.coerceAtLeast(1))
                val boundaries =
                    readableDatabase.query("invalid_boundaries", arrayOf("time", "source", "sensor", "session"), "time >= ? AND time <= ?", arrayOf(boundedStart.toString(), now.toString()), null, null, "time ASC").use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(GlucoseSample(Double.NaN, cursor.getLong(0), DataSourceId.valueOf(cursor.getString(1)), cursor.getString(2).ifEmpty { null }, cursor.getString(3).ifEmpty { null }, quality = CgmQuality.INVALID))
                        }
                    }
                // Boundaries stay local, outside the canonical valid-history merge. The
                // range integrator uses them to split measured intervals, never as data.
                (canonical + boundaries).sortedBy { it.measuredAtEpochMs }
            }
        }

    companion object {
        private val helpers = ConcurrentHashMap<String, LongTermGlucoseArchive>()

        fun get(context: Context): LongTermGlucoseArchive {
            val application = context.applicationContext
            return helpers.computeIfAbsent(application.getDatabasePath("measured_glucose_archive.db").absolutePath) { LongTermGlucoseArchive(application) }
        }

        private val lock = Mutex()
        val revision = MutableStateFlow(0L)
    }
}
