package app.sugarwear

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Non-time-critical upload worker: no alarm authority and no BLE transport ownership. */
internal class DexcomShareUploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = DexcomShareUploadStore(applicationContext)
        val config = settings.readEnabledConfig() ?: return@withContext Result.success()
        val collector = CgmSensorStateStore(applicationContext).read()
        val sensor = collector.sensor ?: return@withContext Result.success()
        if (!collector.collectorEnabled) return@withContext Result.success()
        val identity = CgmHistoryIdentityStore(applicationContext)
            .canonical(sensor.sensorId, sensor.sessionId ?: sensor.sensorId)
        val client = DexcomShareUploadClient()
        val readingDatabase = CgmReadingDatabase(applicationContext)
        try {
            for (batchNumber in 0 until MAX_BATCHES_PER_RUN) {
                if (isStopped || settings.readEnabledConfig()?.revision != config.revision) return@withContext Result.success()
                val now = System.currentTimeMillis()
                val cursor = settings.cursor(config, identity.first, identity.second)
                val lowerBound =
                    if (cursor == 0L) {
                        now - DexcomShareUploadPolicy.FIRST_UPLOAD_LOOKBACK_MS
                    } else {
                        maxOf(cursor, now - DexcomShareUploadPolicy.MAX_LOOKBACK_MS)
                    }
                val readings = readingDatabase.query(
                    selection = "status=? AND sensor_id=? AND session_id=? AND measured_at>?",
                    args = arrayOf(CgmReadingStatus.VALID.name, identity.first, identity.second, lowerBound.toString()),
                    ascending = true,
                    limit = MAX_FETCH,
                )
                val eligible = DexcomShareUploadPolicy.eligible(
                    readings,
                    identity.first,
                    identity.second,
                    lowerBound,
                    now,
                )
                if (eligible.isEmpty()) return@withContext Result.success()
                try {
                    client.upload(config, eligible)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: DexcomShareProtocolException) {
                    settings.markError(config, problem.issue)
                    return@withContext if (problem.recoverable) Result.retry() else Result.failure()
                }
                if (isStopped || !settings.markAccepted(
                        config,
                        identity.first,
                        identity.second,
                        eligible.last().measurementEpochMs,
                        System.currentTimeMillis(),
                    )
                ) return@withContext Result.success()
                if (eligible.size < DexcomShareUploadPolicy.MAX_BATCH) return@withContext Result.success()
            }
            // A capped catch-up batch is retried under WorkManager's backoff, never on the BLE thread.
            Result.retry()
        } finally {
            readingDatabase.close()
        }
    }

    private companion object {
        const val MAX_FETCH = 300
        const val MAX_BATCHES_PER_RUN = 4
    }
}

/** Existing-work coalescing and a 15-minute recovery check prevent a polling loop. */
internal object DexcomShareUploadScheduler {
    private const val IMMEDIATE = "sugarwear_dexcom_share_upload"
    private const val RECOVERY = "sugarwear_dexcom_share_recovery"

    fun onReadingsChanged(context: Context) {
        if (!DexcomShareUploadStore(context).enabled()) return
        val request = OneTimeWorkRequestBuilder<DexcomShareUploadWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP, request)
    }

    fun start(context: Context) {
        if (!DexcomShareUploadStore(context).enabled()) return
        val periodic = PeriodicWorkRequestBuilder<DexcomShareUploadWorker>(15L, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(RECOVERY, ExistingPeriodicWorkPolicy.KEEP, periodic)
        onReadingsChanged(context)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
        WorkManager.getInstance(context).cancelUniqueWork(RECOVERY)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
}
