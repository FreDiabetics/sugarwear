package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.TimeUnit

private suspend inline fun <reified T : Record> readHealthRecords(
    client: HealthConnectClient,
    granted: Set<String>,
    start: Instant,
    end: Instant,
): List<T> {
    if (HealthPermission.getReadPermission(T::class) !in granted) return emptyList()
    val result = mutableListOf<T>()
    var token: String? = null
    do {
        val response = client.readRecords(ReadRecordsRequest(T::class, TimeRangeFilter.between(start, end), pageToken = token))
        result += response.records
        token = response.pageToken
    } while (token != null)
    return result
}

@Serializable
internal data class HealthConnectSnapshot(
    val syncedAtEpochMs: Long,
    val steps: Long = 0,
    val latestHeartRate: Long? = null,
    val averageHeartRate: Double? = null,
    val restingHeartRate: Long? = null,
    val heartRateVariabilityMs: Double? = null,
    val activeCaloriesKcal: Double = 0.0,
    val totalCaloriesKcal: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val elevationMeters: Double = 0.0,
    val floorsClimbed: Double = 0.0,
    val activeMinutes: Long = 0,
    val sleepMinutes: Long = 0,
    val hydrationLiters: Double = 0.0,
    val nutritionCarbohydratesGrams: Double = 0.0,
    val nutritionEnergyKcal: Double = 0.0,
    val weightKg: Double? = null,
    val heightMeters: Double? = null,
    val bodyFatPercent: Double? = null,
    val bodyWaterKg: Double? = null,
    val leanBodyMassKg: Double? = null,
    val basalMetabolicRateKcalPerDay: Double? = null,
    val systolicMmHg: Double? = null,
    val diastolicMmHg: Double? = null,
    val bloodGlucoseMgDl: Double? = null,
    val oxygenSaturationPercent: Double? = null,
    val respiratoryRate: Double? = null,
    val vo2Max: Double? = null,
)

@Serializable
internal data class HealthConnectStatus(
    val lastAttemptAtEpochMs: Long = 0L,
    val lastSuccessAtEpochMs: Long = 0L,
    val glucoseWriteGranted: Boolean = false,
    val grantedReadPermissionCount: Int = 0,
    val lastExportedGlucoseAtEpochMs: Long = 0L,
    val lastExportedGlucoseCount: Int = 0,
    val lastErrorCode: String? = null,
    val lastErrorType: String? = null,
)

internal enum class HealthConnectExportState {
    SUCCESS,
    NO_DATA,
    PERMISSION_MISSING,
    UNAVAILABLE,
    FAILED,
}

internal data class HealthConnectExportResult(
    val state: HealthConnectExportState,
    val acceptedCount: Int = 0,
    val latestGlucoseAtEpochMs: Long = 0L,
    val errorCode: String? = null,
)

internal data class HealthConnectSyncResult(
    val snapshot: HealthConnectSnapshot,
    val glucoseExport: HealthConnectExportResult,
)

internal object HealthConnectIntegration {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private const val PREFS = "health_connect"
    private const val SNAPSHOT = "snapshot"
    private const val STATUS = "status_v2"
    private const val MAX_GLUCOSE_RECORDS_PER_EXPORT = 300
    private const val GLUCOSE_BACKFILL_MS = 24L * 60L * 60_000L
    private const val FUTURE_TOLERANCE_MS = 5L * 60_000L
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    internal val readableRecordTypes =
        setOf(
            HeartRateRecord::class,
            RestingHeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            StepsRecord::class,
            ActiveCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord::class,
            DistanceRecord::class,
            ElevationGainedRecord::class,
            FloorsClimbedRecord::class,
            ExerciseSessionRecord::class,
            SleepSessionRecord::class,
            HydrationRecord::class,
            NutritionRecord::class,
            WeightRecord::class,
            HeightRecord::class,
            BodyFatRecord::class,
            BodyWaterMassRecord::class,
            LeanBodyMassRecord::class,
            BasalMetabolicRateRecord::class,
            BloodPressureRecord::class,
            BloodGlucoseRecord::class,
            OxygenSaturationRecord::class,
            RespiratoryRateRecord::class,
            Vo2MaxRecord::class,
        )
    val glucoseWritePermission: String = HealthPermission.getWritePermission(BloodGlucoseRecord::class)
    val recordPermissions: Set<String> =
        readableRecordTypes.map(HealthPermission::getReadPermission).toSet() + glucoseWritePermission
    val permissions: Set<String> = recordPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    val permissionContract get() = PermissionController.createRequestPermissionResultContract()

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)

    suspend fun grantedPermissions(context: Context): Set<String> =
        if (availability(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        } else {
            emptySet()
        }

    suspend fun sync(context: Context): HealthConnectSyncResult? {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val glucoseExport = exportGlucoseState(context, TherapyStateStore(context).state.first(), client, granted)
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(24))
            val heartRates =
                readHealthRecords<HeartRateRecord>(
                    client,
                    granted,
                    start,
                    end,
                ).flatMap(HeartRateRecord::samples).sortedBy(HeartRateRecord.Sample::time)
            val exercises = readHealthRecords<ExerciseSessionRecord>(client, granted, start, end)
            val sleep = readHealthRecords<SleepSessionRecord>(client, granted, start, end)
            val nutrition = readHealthRecords<NutritionRecord>(client, granted, start, end)
            val latestPressure = readHealthRecords<BloodPressureRecord>(client, granted, start, end).maxByOrNull(BloodPressureRecord::time)
            val snapshot =
                HealthConnectSnapshot(
                    syncedAtEpochMs = System.currentTimeMillis(),
                    steps = readHealthRecords<StepsRecord>(client, granted, start, end).sumOf(StepsRecord::count),
                    latestHeartRate = heartRates.lastOrNull()?.beatsPerMinute,
                    averageHeartRate = heartRates.map(HeartRateRecord.Sample::beatsPerMinute).average().takeUnless(Double::isNaN),
                    restingHeartRate =
                        readHealthRecords<RestingHeartRateRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(RestingHeartRateRecord::time)?.beatsPerMinute,
                    heartRateVariabilityMs =
                        readHealthRecords<HeartRateVariabilityRmssdRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(HeartRateVariabilityRmssdRecord::time)?.heartRateVariabilityMillis,
                    activeCaloriesKcal =
                        readHealthRecords<ActiveCaloriesBurnedRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).sumOf { it.energy.inKilocalories },
                    totalCaloriesKcal =
                        readHealthRecords<TotalCaloriesBurnedRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).sumOf { it.energy.inKilocalories },
                    distanceMeters =
                        readHealthRecords<DistanceRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).sumOf { it.distance.inMeters },
                    elevationMeters =
                        readHealthRecords<ElevationGainedRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).sumOf { it.elevation.inMeters },
                    floorsClimbed = readHealthRecords<FloorsClimbedRecord>(client, granted, start, end).sumOf(FloorsClimbedRecord::floors),
                    activeMinutes = exercises.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) },
                    sleepMinutes = sleep.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) },
                    hydrationLiters = readHealthRecords<HydrationRecord>(client, granted, start, end).sumOf { it.volume.inLiters },
                    nutritionCarbohydratesGrams = nutrition.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 },
                    nutritionEnergyKcal = nutrition.sumOf { it.energy?.inKilocalories ?: 0.0 },
                    weightKg =
                        readHealthRecords<WeightRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(WeightRecord::time)?.weight?.inKilograms,
                    heightMeters =
                        readHealthRecords<HeightRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(HeightRecord::time)?.height?.inMeters,
                    bodyFatPercent =
                        readHealthRecords<BodyFatRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(BodyFatRecord::time)?.percentage?.value,
                    bodyWaterKg =
                        readHealthRecords<BodyWaterMassRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(BodyWaterMassRecord::time)?.mass?.inKilograms,
                    leanBodyMassKg =
                        readHealthRecords<LeanBodyMassRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(LeanBodyMassRecord::time)?.mass?.inKilograms,
                    basalMetabolicRateKcalPerDay =
                        readHealthRecords<BasalMetabolicRateRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(BasalMetabolicRateRecord::time)?.basalMetabolicRate?.inKilocaloriesPerDay,
                    systolicMmHg = latestPressure?.systolic?.inMillimetersOfMercury,
                    diastolicMmHg = latestPressure?.diastolic?.inMillimetersOfMercury,
                    bloodGlucoseMgDl =
                        readHealthRecords<BloodGlucoseRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(BloodGlucoseRecord::time)?.level?.inMilligramsPerDeciliter,
                    oxygenSaturationPercent =
                        readHealthRecords<OxygenSaturationRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(OxygenSaturationRecord::time)?.percentage?.value,
                    respiratoryRate =
                        readHealthRecords<RespiratoryRateRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(RespiratoryRateRecord::time)?.rate,
                    vo2Max =
                        readHealthRecords<Vo2MaxRecord>(
                            client,
                            granted,
                            start,
                            end,
                        ).maxByOrNull(Vo2MaxRecord::time)?.vo2MillilitersPerMinuteKilogram,
                )
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(SNAPSHOT, json.encodeToString(HealthConnectSnapshot.serializer(), snapshot))
            }
            context.recordMobileDiagnostic(
                module = "HEALTH-CONNECT",
                code = "HC-SYNC-200",
                message = "Health Connect data synchronized",
                metadata =
                    mapOf(
                        "readPermissions" to granted.count { it.startsWith("android.permission.health.READ_") },
                        "steps" to snapshot.steps,
                        "glucoseExport" to glucoseExport.state,
                    ),
            )
            HealthConnectSyncResult(snapshot, glucoseExport)
        } catch (error: Exception) {
            val previous = status(context)
            persistStatus(
                context,
                previous.copy(
                    lastAttemptAtEpochMs = System.currentTimeMillis(),
                    lastErrorCode = "HC-SYNC-500",
                    lastErrorType = error.javaClass.simpleName,
                ),
            )
            context.recordMobileDiagnostic(
                "HEALTH-CONNECT",
                "HC-SYNC-500",
                "Health Connect synchronization failed",
                DiagnosticSeverity.WARNING,
                mapOf("error" to error.javaClass.simpleName),
            )
            throw error
        }
    }

    suspend fun syncInBackground(context: Context) {
        val granted = grantedPermissions(context)
        if (HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted) {
            sync(context)
        } else {
            TherapyStateStore(context).state.first()?.let { exportCgmReading(context, it) }
        }
    }

    suspend fun exportCgmReading(
        context: Context,
        state: TherapyDisplayState,
    ): HealthConnectExportResult {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HealthConnectExportResult(HealthConnectExportState.UNAVAILABLE, errorCode = "HC-UNAVAILABLE")
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            exportGlucoseState(context, state, client, granted)
        } catch (error: Exception) {
            val previous = status(context)
            persistStatus(
                context,
                previous.copy(
                    lastAttemptAtEpochMs = System.currentTimeMillis(),
                    lastErrorCode = "HC-GLUCOSE-500",
                    lastErrorType = error.javaClass.simpleName,
                ),
            )
            context.recordMobileDiagnostic(
                "HEALTH-CONNECT",
                "HC-GLUCOSE-500",
                "Health Connect client could not export blood glucose",
                DiagnosticSeverity.WARNING,
                mapOf("error" to error.javaClass.simpleName),
            )
            HealthConnectExportResult(HealthConnectExportState.FAILED, errorCode = "HC-GLUCOSE-500")
        }
    }

    private suspend fun exportGlucoseState(
        context: Context,
        state: TherapyDisplayState?,
        client: HealthConnectClient,
        granted: Set<String>,
    ): HealthConnectExportResult {
        val now = System.currentTimeMillis()
        val previousStatus = status(context)
        val readPermissionCount = granted.count { it.startsWith("android.permission.health.READ_") }
        if (glucoseWritePermission !in granted) {
            val result = HealthConnectExportResult(HealthConnectExportState.PERMISSION_MISSING, errorCode = "HC-PERM-403")
            persistStatus(
                context,
                previousStatus.copy(
                    lastAttemptAtEpochMs = now,
                    glucoseWriteGranted = false,
                    grantedReadPermissionCount = readPermissionCount,
                    lastErrorCode = result.errorCode,
                    lastErrorType = "WRITE_BLOOD_GLUCOSE not granted",
                ),
            )
            if (previousStatus.glucoseWriteGranted || previousStatus.lastErrorCode != result.errorCode) {
                context.recordMobileDiagnostic("HEALTH-CONNECT", result.errorCode!!, "Blood glucose write permission is missing")
            }
            return result
        }
        if (state == null) {
            val result = HealthConnectExportResult(HealthConnectExportState.NO_DATA)
            persistStatus(
                context,
                previousStatus.copy(
                    lastAttemptAtEpochMs = now,
                    glucoseWriteGranted = true,
                    grantedReadPermissionCount = readPermissionCount,
                    lastErrorCode = null,
                    lastErrorType = null,
                ),
            )
            return result
        }

        val lastExported = previousStatus.lastExportedGlucoseAtEpochMs.takeIf { it in 1..(now + FUTURE_TOLERANCE_MS) } ?: 0L
        val records = buildGlucoseRecords(state, now, lastExported)
        if (records.isEmpty()) {
            val result = HealthConnectExportResult(HealthConnectExportState.NO_DATA, latestGlucoseAtEpochMs = lastExported)
            persistStatus(
                context,
                previousStatus.copy(
                    lastAttemptAtEpochMs = now,
                    glucoseWriteGranted = true,
                    grantedReadPermissionCount = readPermissionCount,
                    lastExportedGlucoseCount = 0,
                    lastErrorCode = null,
                    lastErrorType = null,
                ),
            )
            return result
        }

        return runCatching { client.insertRecords(records) }
            .fold(
                onSuccess = {
                    val latest = records.maxOf { it.time.toEpochMilli() }
                    val result = HealthConnectExportResult(HealthConnectExportState.SUCCESS, records.size, latest)
                    persistStatus(
                        context,
                        previousStatus.copy(
                            lastAttemptAtEpochMs = now,
                            lastSuccessAtEpochMs = now,
                            glucoseWriteGranted = true,
                            grantedReadPermissionCount = readPermissionCount,
                            lastExportedGlucoseAtEpochMs = maxOf(previousStatus.lastExportedGlucoseAtEpochMs, latest),
                            lastExportedGlucoseCount = records.size,
                            lastErrorCode = null,
                            lastErrorType = null,
                        ),
                    )
                    context.recordMobileDiagnostic(
                        "HEALTH-CONNECT",
                        "HC-GLUCOSE-200",
                        "Blood glucose records accepted by Health Connect",
                        metadata = mapOf("count" to records.size, "latestAt" to latest),
                    )
                    result
                },
                onFailure = { error ->
                    val code = if (error is SecurityException) "HC-PERM-403" else "HC-GLUCOSE-500"
                    persistStatus(
                        context,
                        previousStatus.copy(
                            lastAttemptAtEpochMs = now,
                            glucoseWriteGranted = glucoseWritePermission in granted,
                            grantedReadPermissionCount = readPermissionCount,
                            lastExportedGlucoseCount = 0,
                            lastErrorCode = code,
                            lastErrorType = error.javaClass.simpleName,
                        ),
                    )
                    context.recordMobileDiagnostic(
                        "HEALTH-CONNECT",
                        code,
                        "Blood glucose export failed",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                    HealthConnectExportResult(HealthConnectExportState.FAILED, errorCode = code)
                },
            )
    }

    internal fun buildGlucoseRecords(
        state: TherapyDisplayState,
        nowEpochMs: Long,
        lastExportedAtEpochMs: Long = 0L,
    ): List<BloodGlucoseRecord> {
        val current = state.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, state.source) }
        return (state.glucoseHistory + listOfNotNull(current))
            .asSequence()
            .filter { sample ->
                sample.valueMgDl.isFinite() &&
                    sample.valueMgDl in 20.0..1000.0 &&
                    sample.measuredAtEpochMs in (nowEpochMs - GLUCOSE_BACKFILL_MS)..(nowEpochMs + FUTURE_TOLERANCE_MS) &&
                    sample.measuredAtEpochMs >= lastExportedAtEpochMs
            }.distinctBy { it.source to it.measuredAtEpochMs }
            .sortedBy(GlucoseSample::measuredAtEpochMs)
            .toList()
            .takeLast(MAX_GLUCOSE_RECORDS_PER_EXPORT)
            .map(::toBloodGlucoseRecord)
    }

    private fun toBloodGlucoseRecord(sample: GlucoseSample): BloodGlucoseRecord {
        val time = Instant.ofEpochMilli(sample.measuredAtEpochMs)
        return BloodGlucoseRecord(
            time = time,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
            metadata =
                Metadata.autoRecorded(
                    device =
                        Device(
                            type = if (sample.source == DataSourceId.DEXCOM_G7_WATCH) Device.TYPE_WATCH else Device.TYPE_PHONE,
                            manufacturer = android.os.Build.MANUFACTURER,
                            model = android.os.Build.MODEL,
                        ),
                    clientRecordId = "sugarlicious:cgm:${sample.source.name}:${sample.measuredAtEpochMs}",
                    clientRecordVersion = sample.measuredAtEpochMs.coerceAtLeast(1L),
                ),
            level = BloodGlucose.milligramsPerDeciliter(sample.valueMgDl),
            specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
            mealType = MealType.MEAL_TYPE_UNKNOWN,
            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
        )
    }

    fun snapshot(context: Context): HealthConnectSnapshot? =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SNAPSHOT, null)
            ?.let { runCatching { json.decodeFromString<HealthConnectSnapshot>(it) }.getOrNull() }

    fun status(context: Context): HealthConnectStatus =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(STATUS, null)
            ?.let { runCatching { json.decodeFromString<HealthConnectStatus>(it) }.getOrNull() }
            ?: HealthConnectStatus()

    fun statusLabel(context: Context): String =
        when (availability(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val status = status(context)
                when {
                    status.lastAttemptAtEpochMs == 0L -> "Berechtigungen einrichten"
                    !status.glucoseWriteGranted -> "BZ-Schreibrecht fehlt"
                    status.lastErrorCode != null -> "Prüfen · ${status.lastErrorCode}"
                    status.glucoseWriteGranted && status.lastSuccessAtEpochMs > 0L -> "BZ-Export aktiv"
                    status.glucoseWriteGranted -> "BZ-Schreibrecht aktiv"
                    else -> "Berechtigungen einrichten"
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect aktualisieren"
            else -> "Nicht verfügbar"
        }

    fun detailLabel(context: Context): String {
        val status = status(context)
        val export =
            status.lastExportedGlucoseAtEpochMs
                .takeIf { it > 0L }
                ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
                ?: "noch kein bestätigter BZ-Export"
        return "Blutzucker schreiben: ${if (status.glucoseWriteGranted) "erlaubt" else "nicht erlaubt"} · Leserechte: ${status.grantedReadPermissionCount}/${readableRecordTypes.size} · Letzter BZ: $export"
    }

    private fun persistStatus(
        context: Context,
        status: HealthConnectStatus,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(STATUS, json.encodeToString(HealthConnectStatus.serializer(), status))
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("health_connect_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching {
            HealthConnectIntegration.syncInBackground(applicationContext)
            SugarliciousWidgets.update(applicationContext)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
