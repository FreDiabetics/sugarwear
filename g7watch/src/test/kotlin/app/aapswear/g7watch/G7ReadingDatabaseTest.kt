package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7Reading
import app.aapswear.g7.toCgm
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7ReadingDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: G7ReadingDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = G7ReadingDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `committed live reading requests Vigil providers in the collector package immediately`() =
        runBlocking {
            val now = System.currentTimeMillis()
            database.insert(CgmReading(id = "immediate", source = DataSourceId.DEXCOM_G7_WATCH, sensorId = "sensor", sessionId = "session", glucoseMgDl = 123.0, timestampEpochMs = now, receivedAtEpochMs = now, status = CgmReadingStatus.VALID))
            val components =
                org.robolectric.Shadows.shadowOf(context as android.app.Application).broadcastIntents.mapNotNull {
                    it.getParcelableExtra<android.content.ComponentName>("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT")
                }
            org.junit.Assert.assertTrue(components.contains(android.content.ComponentName(context, app.aapswear.complications.DirectToWatchHeaderComplication::class.java)))
            org.junit.Assert.assertTrue(components.contains(android.content.ComponentName(context, app.aapswear.complications.DirectToWatchGraphComplication::class.java)))
            org.junit.Assert.assertTrue(components.contains(android.content.ComponentName(context, app.aapswear.complications.DirectToWatchAmbientGraphComplication::class.java)))
        }

    @Test
    @Suppress("DEPRECATION")
    fun `coalesced publication waits for complete state and refreshes Vigil only once`() =
        runBlocking {
            val application = org.robolectric.Shadows.shadowOf(context as android.app.Application)

            fun headerRequests() =
                application.broadcastIntents.count {
                    it.getParcelableExtra<android.content.ComponentName>("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT") == android.content.ComponentName(context, app.aapswear.complications.DirectToWatchHeaderComplication::class.java)
                }
            val before = headerRequests()
            database.coalesceChangeNotifications {
                val now = System.currentTimeMillis()
                repeat(2) { index ->
                    database.insert(CgmReading(id = "batch-$index", source = DataSourceId.DEXCOM_G7_WATCH, sensorId = "sensor", sessionId = "session", glucoseMgDl = 123.0, timestampEpochMs = now - index * 300_000L, receivedAtEpochMs = now, status = CgmReadingStatus.VALID))
                }
                assertEquals(before, headerRequests())
            }
            assertEquals(before + 1, headerRequests())
        }

    @Test
    fun `thirty day retention does not discard a ten day history at two thousand rows`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val db = database.writableDatabase
            db.beginTransaction()
            try {
                repeat(3_000) { index ->
                    val at = now - (index + 1L) * 300_000L
                    db.execSQL(
                        "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status) VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf<Any>("historical-$index", "sensor", "session", 123.0, at, at, "FLAT", "VALID"),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            database.insert(
                CgmReading(
                    id = "latest",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor",
                    sessionId = "session",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                ),
            )
            db.rawQuery("SELECT COUNT(*) FROM readings", null).use {
                it.moveToFirst()
                assertEquals(3_001, it.getInt(0))
            }
        }

    @Test
    fun `retains all decoded sensor documentation fields`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val reading =
                CgmReading(
                    id = "reading-14",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-id",
                    sessionId = "session-id",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = now - 15_000L,
                    receivedAtEpochMs = now,
                    deltaMgDl = 2.0,
                    trend = Trend.FLAT,
                    trendRateMgDlPerMinute = 0.4,
                    predictedMgDl = 126.0,
                    sensorAgeSeconds = 15L,
                    status = CgmReadingStatus.VALID,
                    sequenceNumber = 14L,
                    displayOnly = true,
                    rawSourceTimestamp = 123_456L,
                    sensorStartEpochMs = now - 900_000L,
                    sensorEndEpochMs = now + 864_000_000L,
                    graceEndEpochMs = now + 907_200_000L,
                    protocolStatusCode = 1,
                    calibrationStateCode = 6,
                    reservedField = 42,
                )

            database.insert(reading)

            assertEquals(reading, database.getLatest())
        }

    @Test
    fun `updates live delta after its predecessor is backfilled`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val live =
                CgmReading(
                    id = "live",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor",
                    sessionId = "session",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    deltaMgDl = null,
                    trend = Trend.UNKNOWN,
                    origin = CgmReadingOrigin.LIVE,
                )
            database.insert(live)

            val historical = live.copy(id = "historical", timestampEpochMs = now - 300_000L, glucoseMgDl = 119.0, origin = CgmReadingOrigin.BACKFILL)
            database.insert(historical)
            assertNull(database.latestReading.value?.deltaMgDl)
            val packet = G7Reading(live.sensorId, live.sessionId, null, live.glucoseMgDl, now, now)
            val recomputed = packet.toCgm(database.getLatestValidBefore(live.sensorId, live.sessionId, now)).copy(id = live.id)
            assertEquals(4.0, recomputed.deltaMgDl!!, 0.0)
            assertEquals(true, database.updateDerivedFields(recomputed))
            assertEquals(recomputed, database.getLatest())
            assertEquals(recomputed, database.latestReading.value)
        }

    @Test
    fun `only valid readings enter the Watch to Mobile backfill queue`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val valid =
                CgmReading(
                    id = "valid",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-id",
                    sessionId = "session-id",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = now - 5_000L,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                )
            val sensorError =
                valid.copy(
                    id = "sensor-error",
                    timestampEpochMs = now,
                    status = CgmReadingStatus.SENSOR_ERROR,
                )
            val newerValid =
                valid.copy(
                    id = "newer-valid",
                    timestampEpochMs = now - 1_000L,
                    receivedAtEpochMs = now,
                )

            database.insert(newerValid)
            database.insert(sensorError)
            database.insert(valid)

            assertEquals(sensorError, database.getLatest())
            assertEquals(newerValid, database.getLatestValid())
        }

    @Test
    fun `temporal predecessor ignores future rows and other sessions`() =
        runBlocking {
            val currentAt = System.currentTimeMillis()
            val base =
                CgmReading(
                    id = "previous",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 120.0,
                    timestampEpochMs = currentAt - 300_000L,
                    receivedAtEpochMs = currentAt - 299_000L,
                    status = CgmReadingStatus.VALID,
                )
            val older = base.copy(id = "older", timestampEpochMs = currentAt - 600_000L)
            val future = base.copy(id = "future", timestampEpochMs = currentAt + 300_000L)
            val otherSession =
                base.copy(
                    id = "other-session",
                    sessionId = "session-b",
                    timestampEpochMs = currentAt - 60_000L,
                )

            database.insert(future)
            database.insert(otherSession)
            database.insert(older)
            database.insert(base)

            assertEquals(
                base,
                database.getLatestValidBefore("sensor-a", "session-a", currentAt),
            )
            assertNull(
                database.getLatestValidBefore("sensor-a", "missing-session", currentAt),
            )
        }

    @Test
    fun `duplicate sensor error sequence is retained only once`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val error =
                CgmReading(
                    id = "error-1",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 0.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.SENSOR_ERROR,
                    sequenceNumber = 42L,
                )

            assertEquals(true, database.insert(error))
            assertEquals(false, database.insert(error.copy(id = "error-2", receivedAtEpochMs = now + 300_000L)))
            assertEquals(1, database.query().size)
            assertEquals(true, database.insert(error.copy(id = "error-3", sequenceNumber = 43L)))
            assertEquals(2, database.query().size)
        }

    @Test
    fun `validated live and backfill identity deduplicates without a migration index`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val live =
                CgmReading(
                    id = "live-42",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    sequenceNumber = 42L,
                    rawSourceTimestamp = 12_600L,
                )

            assertEquals(true, database.insert(live))
            assertEquals(false, database.insert(live.copy(id = "backfill-42", receivedAtEpochMs = now + 10_000L)))
            assertEquals(true, database.insert(live.copy(id = "later-42", timestampEpochMs = now + 300_000L, rawSourceTimestamp = 12_900L)))
            assertEquals(2, database.query().size)
        }

    @Test
    fun `same real measurement deduplicates even when live and backfill sequences disagree`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val live =
                CgmReading(
                    id = "live-2423",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 193.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    sequenceNumber = 2_423L,
                    rawSourceTimestamp = 12_600L,
                    origin = CgmReadingOrigin.LIVE,
                )
            val backfill =
                live.copy(
                    id = "backfill-2420",
                    receivedAtEpochMs = now + 10_000L,
                    sequenceNumber = 2_420L,
                    rawSourceTimestamp = 12_300L,
                    origin = CgmReadingOrigin.BACKFILL,
                )

            assertEquals(true, database.insert(live))
            assertEquals(false, database.insert(backfill))
            assertEquals(listOf(live), database.query())
        }

    @Test
    fun `cadence aligned backfill deduplicates live packet age offset`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val live =
                CgmReading(
                    id = "live-aged",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 193.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    rawSourceTimestamp = 12_607L,
                    origin = CgmReadingOrigin.LIVE,
                )
            val cadenceAligned =
                live.copy(
                    id = "backfill-aligned",
                    timestampEpochMs = now - 7_000L,
                    rawSourceTimestamp = 12_601L,
                    origin = CgmReadingOrigin.BACKFILL,
                )

            assertEquals(true, database.insert(live))
            assertEquals(false, database.insert(cadenceAligned))
            assertEquals(listOf(live), database.query())
        }

    @Test
    fun `repeated backfill sensor clock deduplicates despite reconstructed start drift`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val first =
                CgmReading(
                    id = "backfill-first",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 145.0,
                    timestampEpochMs = now - 4_000L,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    rawSourceTimestamp = 24_560L,
                    origin = CgmReadingOrigin.BACKFILL,
                )
            val repeated =
                first.copy(
                    id = "backfill-repeat",
                    timestampEpochMs = now + 3_000L,
                    receivedAtEpochMs = now + 10_000L,
                )

            assertEquals(true, database.insert(first))
            assertEquals(false, database.insert(repeated))
            assertEquals(1, database.query().size)
        }

    @Test
    fun `live upgrades matching backfill without creating a second sync row`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val backfill =
                CgmReading(
                    id = "backfill",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 193.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now + 20_000L,
                    status = CgmReadingStatus.VALID,
                    sequenceNumber = 2_420L,
                    rawSourceTimestamp = 12_300L,
                    origin = CgmReadingOrigin.BACKFILL,
                )
            val live =
                backfill.copy(
                    id = "live",
                    receivedAtEpochMs = now + 1_000L,
                    sequenceNumber = 2_423L,
                    rawSourceTimestamp = 12_600L,
                    origin = CgmReadingOrigin.LIVE,
                )

            assertEquals(true, database.insert(backfill))
            assertEquals(true, database.insert(live))
            val stored = database.query()
            assertEquals(1, stored.size)
            assertEquals(CgmReadingOrigin.LIVE, stored.single().origin)
            assertEquals(2_423L, stored.single().sequenceNumber)
        }

    @Test
    fun `same sequence at a different measurement timestamp remains a distinct reading`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val first =
                CgmReading(
                    id = "first",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 120.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    status = CgmReadingStatus.VALID,
                    sequenceNumber = 330L,
                )
            val second = first.copy(id = "second", timestampEpochMs = now + 300_000L, receivedAtEpochMs = now + 300_000L)

            assertEquals(true, database.insert(first))
            assertEquals(true, database.insert(second))
            assertEquals(2, database.query().size)
        }

    @Test
    fun `backfill anchor remains before oldest unresolved gap after newer live values`() =
        runBlocking {
            val now = System.currentTimeMillis()

            fun reading(
                id: String,
                clock: Long,
            ) = CgmReading(
                id = id,
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor-a",
                sessionId = "session-a",
                glucoseMgDl = 120.0,
                timestampEpochMs = now + clock * 1_000L,
                receivedAtEpochMs = now + clock * 1_000L,
                status = CgmReadingStatus.VALID,
                rawSourceTimestamp = clock,
            )
            database.insert(reading("t0", 10_000L))
            database.insert(reading("t5", 10_300L))
            database.insert(reading("t15", 10_900L))
            database.insert(reading("t20", 11_200L))

            assertEquals(10_300L, database.getBackfillAnchorSensorClock("sensor-a", "session-a"))

            database.insert(reading("t10-backfill", 10_600L).copy(origin = CgmReadingOrigin.BACKFILL))
            assertEquals(11_200L, database.getBackfillAnchorSensorClock("sensor-a", "session-a"))
        }

    @Test
    fun `persisted gap anchor overrides a newer unrelated database gap`() =
        runBlocking {
            val now = System.currentTimeMillis()

            fun reading(
                id: String,
                minutes: Long,
                clock: Long,
            ) = CgmReading(
                id = id,
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor-a",
                sessionId = "session-a",
                glucoseMgDl = 120.0,
                timestampEpochMs = now + minutes * 60_000L,
                receivedAtEpochMs = now + minutes * 60_000L,
                status = CgmReadingStatus.VALID,
                rawSourceTimestamp = clock,
            )
            database.insert(reading("before", 0, 10_000L))
            database.insert(reading("after", 10, 10_600L))
            database.insert(reading("latest", 20, 11_200L))

            assertEquals(
                10_000L,
                database.getBackfillAnchorSensorClockForGap("sensor-a", "session-a", now + 5 * 60_000L),
            )
            assertNull(database.getBackfillAnchorSensorClockForGap("sensor-b", "session-b", now + 5 * 60_000L))
        }

    @Test
    fun `late first live value retries initial sensor history`() =
        runBlocking {
            val now = System.currentTimeMillis()
            database.insert(
                CgmReading(
                    id = "late-first-live",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 120.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    trend = Trend.FLAT,
                    status = CgmReadingStatus.VALID,
                    rawSourceTimestamp = 3L * 60L * 60L,
                    sensorStartEpochMs = now - 3L * 60L * 60_000L,
                ),
            )

            assertNull(database.getBackfillAnchorSensorClock("sensor-a", "session-a"))
        }

    @Test
    fun `late first retained value retries full rolling 24 hour sensor history`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val lateClock = 60L * 60L * 60L
            database.insert(
                CgmReading(
                    id = "late-old-sensor",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    glucoseMgDl = 120.0,
                    timestampEpochMs = now,
                    receivedAtEpochMs = now,
                    trend = Trend.FLAT,
                    status = CgmReadingStatus.VALID,
                    rawSourceTimestamp = lateClock,
                    sensorStartEpochMs = now - lateClock * 1_000L,
                ),
            )

            assertNull(database.getBackfillAnchorSensorClock("sensor-a", "session-a"))
        }

    @Test
    fun `version four migration collapses legacy timestamp duplicates and preserves sync`() =
        runBlocking {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
            context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { legacy ->
                legacy.execSQL(
                    "CREATE TABLE readings (id TEXT PRIMARY KEY, sensor_id TEXT NOT NULL, session_id TEXT NOT NULL, glucose REAL NOT NULL, measured_at INTEGER NOT NULL, received_at INTEGER NOT NULL, delta REAL, trend TEXT NOT NULL, trend_rate REAL, predicted REAL, sensor_age INTEGER, status TEXT NOT NULL, sequence_number INTEGER, display_only INTEGER NOT NULL DEFAULT 0, sensor_clock INTEGER, sensor_start INTEGER, sensor_end INTEGER, grace_end INTEGER, protocol_status INTEGER, calibration_state INTEGER, reserved_field INTEGER, origin TEXT NOT NULL DEFAULT 'LIVE', synced INTEGER NOT NULL DEFAULT 0)",
                )
                legacy.execSQL(
                    "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status,sequence_number,origin,synced) VALUES ('backfill','sensor','session',193,1000,3000,'FLAT','VALID',2420,'BACKFILL',1)",
                )
                legacy.execSQL(
                    "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status,sequence_number,origin,synced) VALUES ('live','sensor','session',193,1000,2000,'FLAT','VALID',2423,'LIVE',0)",
                )
                legacy.version = 3
            }

            database = G7ReadingDatabase(context)

            val stored = database.query()
            assertEquals(1, stored.size)
            assertEquals(CgmReadingOrigin.LIVE, stored.single().origin)
            assertEquals(2_423L, stored.single().sequenceNumber)
        }

    @Test
    fun `version six migration collapses repeated sensor clock rows and prefers live`() =
        runBlocking {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
            context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { legacy ->
                legacy.execSQL(
                    "CREATE TABLE readings (id TEXT PRIMARY KEY, sensor_id TEXT NOT NULL, session_id TEXT NOT NULL, glucose REAL NOT NULL, measured_at INTEGER NOT NULL, received_at INTEGER NOT NULL, delta REAL, trend TEXT NOT NULL, trend_rate REAL, predicted REAL, sensor_age INTEGER, status TEXT NOT NULL, sequence_number INTEGER, display_only INTEGER NOT NULL DEFAULT 0, sensor_clock INTEGER, sensor_start INTEGER, sensor_end INTEGER, grace_end INTEGER, protocol_status INTEGER, calibration_state INTEGER, reserved_field INTEGER, origin TEXT NOT NULL DEFAULT 'LIVE', synced INTEGER NOT NULL DEFAULT 0)",
                )
                legacy.execSQL(
                    "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status,sensor_clock,origin) VALUES ('old-backfill','sensor','session',193,1000,2000,'FLAT','VALID',245601,'BACKFILL')",
                )
                legacy.execSQL(
                    "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status,sensor_clock,origin) VALUES ('new-backfill','sensor','session',193,1200,3000,'FLAT','VALID',245601,'BACKFILL')",
                )
                legacy.execSQL(
                    "INSERT INTO readings (id,sensor_id,session_id,glucose,measured_at,received_at,trend,status,sensor_clock,origin) VALUES ('live','sensor','session',193,1100,2500,'FLAT','VALID',245601,'LIVE')",
                )
                legacy.version = 5
            }

            database = G7ReadingDatabase(context)

            val stored = database.query()
            assertEquals(1, stored.size)
            assertEquals("live", stored.single().id)
            assertEquals(CgmReadingOrigin.LIVE, stored.single().origin)
        }

    private companion object {
        const val DATABASE_NAME = "g7_readings.db"
    }
}
