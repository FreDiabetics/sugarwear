package app.sugarwear

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import app.sugarlicious.sensor.protocol.CollectorDiagnosticResult
import app.sugarlicious.sensor.protocol.CollectorDiagnosticStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class CgmReceiverWorkDispatcher(
    private val launch: ((() -> Unit) -> Unit),
    private val recover: (Context, String?) -> Unit,
    private val recoveryLock: Any? = null,
    private val scheduleTimeout: (Long, () -> Unit) -> Unit = CgmReceiverDeadline::schedule,
) {
    fun dispatch(
        context: Context,
        action: String?,
        onBeforeLaunch: () -> Unit = {},
        onLaunchFailure: () -> Unit = {},
        onFinished: () -> Unit,
    ) {
        val finished = AtomicBoolean(false)
        val finishOnce = { if (finished.compareAndSet(false, true)) onFinished() }
        try {
            scheduleTimeout(RECEIVER_WORK_TIMEOUT_MS, finishOnce)
            onBeforeLaunch()
            launch {
                try {
                    val appContext = context.applicationContext
                    recoveryLock?.let { lock -> synchronized(lock) { recover(appContext, action) } }
                        ?: recover(appContext, action)
                } finally {
                    finishOnce()
                }
            }
        } catch (error: Throwable) {
            runCatching(onLaunchFailure).exceptionOrNull()?.let(error::addSuppressed)
            finishOnce()
            throw error
        }
    }

    private companion object {
        const val RECEIVER_WORK_TIMEOUT_MS = 8_000L
    }
}

private object CgmReceiverDeadline {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(timeoutMs: Long, block: () -> Unit) {
        scope.launch {
            delay(timeoutMs)
            block()
        }
    }
}

internal class CgmOrderedWorkLauncher(
    threadName: String,
) : Closeable {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, threadName).apply { isDaemon = true }
        }

    fun launch(block: () -> Unit) = executor.execute(block)

    override fun close() = executor.shutdownNow().let { }
}

internal object CgmReceiverWork {
    private val orderedLauncher = CgmOrderedWorkLauncher("g7-receiver-work")
    private val recoveryLock = Any()

    fun dispatcher(recover: (Context, String?) -> Unit) =
        CgmReceiverWorkDispatcher(
            launch = orderedLauncher::launch,
            recover = recover,
            recoveryLock = recoveryLock,
        )
}

private object CgmBootRecovery {
    val dispatcher = CgmReceiverWork.dispatcher(::recover)

    private fun recover(
        context: Context,
        action: String?,
    ) {
        val state = CgmSensorStateStore(context).read()
        if (!shouldRestoreCgmCollector(action, state.collectorEnabled)) return
        CgmAlarmCoordinator.restore(context)
        // Boot removes OS alarms. Recreate the durable wake path before requesting the service:
        // an asynchronous foreground-start timeout cannot be caught by startForegroundService.
        CgmReconnectAlarmScheduler.ensureCollectorSchedule(context, state)
        runCatching { CgmCollectorService.resumeEnabled(context) }
    }
}

internal fun shouldRestoreCgmCollector(
    action: String?,
    collectorEnabled: Boolean,
): Boolean =
    collectorEnabled &&
        (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)

internal fun shouldRecoverCgmAfterBluetoothState(
    action: String?,
    adapterState: Int,
    collectorEnabled: Boolean,
    hasSensor: Boolean,
): Boolean =
    action == BluetoothAdapter.ACTION_STATE_CHANGED &&
        adapterState == BluetoothAdapter.STATE_ON &&
        collectorEnabled &&
        hasSensor

class CgmBluetoothStateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
        val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        val pendingResult = goAsync()
        CgmReceiverWork
            .dispatcher { app, action ->
                val state = CgmSensorStateStore(app).read()
                if (!shouldRecoverCgmAfterBluetoothState(action, adapterState, state.collectorEnabled, state.sensor != null)) {
                    return@dispatcher
                }
                SensorScanner.forceCleanup()
                CgmGattCallbackDispatcher.reset()
                CgmRuntimeReconciler.reconcile(app, CgmRuntimeEntryPoint.RECONNECT_RECEIVER)
                CgmReconnectAlarmScheduler.ensureCollectorSchedule(app, CgmSensorStateStore(app).read())
                CgmCollectorService.restart(app)
            }.dispatch(context, intent.action) { pendingResult.finish() }
    }
}

/**
 * Very short alarm→FGS CPU handoff. The normal bounded cycle WakeLock in CgmCollectorService takes
 * over immediately after the service starts. The timeout guarantees that a failed service launch
 * cannot leave the CPU held indefinitely.
 */
internal object CgmWakeHandoff {
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        release()
        wakeLock =
            context.applicationContext
                .getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:G7AlarmHandoff")
                .apply {
                    setReferenceCounted(false)
                    acquire(HANDOFF_TIMEOUT_MS)
                }
    }

    @Synchronized
    fun release() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
    }

    private const val HANDOFF_TIMEOUT_MS = 15_000L
}

class CgmBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CgmBootRecovery.dispatcher.dispatch(context, intent.action) { pendingResult.finish() }
    }
}

class CgmReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CgmReceiverWork
            .dispatcher { app, _ -> recoverScheduledReconnect(app) }
            .dispatch(
                context = context,
                action = intent.action,
                onBeforeLaunch = { CgmWakeHandoff.acquire(context) },
                onLaunchFailure = CgmWakeHandoff::release,
                onFinished = pendingResult::finish,
            )
    }

    private fun recoverScheduledReconnect(context: Context) {
        var handedOffToService = false
        try {
            val state = CgmSensorStateStore(context).read()
            if (!state.collectorEnabled) return
            val now = System.currentTimeMillis()
            val diagnosticStore = CgmCollectorDiagnosticStore(context)
            val scheduled = diagnosticStore.markScheduledAlarmReceived(now)
            CgmExpectedWindowLedger(context).markPrimaryTriggered(scheduled?.expectedWindowId, now)
            // The service will stage the following slot before BLE work. At receiver level this is an
            // observation-only reconciliation so the just-fired diagnostic envelope is not replaced.
            CgmRuntimeReconciler.reconcile(
                context,
                CgmRuntimeEntryPoint.RECONNECT_RECEIVER,
                allowRepair = false,
                nowEpochMs = now,
            )
            CgmExpectedWindowLedger(context).markServiceRequested(scheduled?.expectedWindowId, System.currentTimeMillis())
            runCatching { CgmCollectorService.startScheduledReconnect(context) }
                .onSuccess { handedOffToService = true }
                .onFailure { error ->
                    // The alarm that brought us here has already fired. Always stage another future
                    // slot before returning, otherwise a transient FGS launch rejection can strand the
                    // collector indefinitely.
                    CgmReconnectAlarmScheduler.ensureCollectorSchedule(context, state, now)
                    val attempt =
                        diagnosticStore.begin(
                            manual = false,
                            restart = false,
                            cycle = scheduled?.copy(cycleEndedAt = System.currentTimeMillis()),
                            nowEpochMs = now,
                        )
                    diagnosticStore.setClassification(attempt.attemptId, CollectorCycleClassification.SERVICE_START_FAILED)
                    CgmExpectedWindowLedger(
                        context,
                    ).markFinal(
                        scheduled?.expectedWindowId,
                        CollectorCycleClassification.SERVICE_START_FAILED,
                        recoveryRequired = true,
                        reason = error.javaClass.simpleName,
                    )
                    diagnosticStore.record(
                        attempt.attemptId,
                        CollectorDiagnosticStage.ERROR,
                        CollectorDiagnosticResult.RECOVERABLE_ERROR,
                        "FGS_RESTART_FAILED · Foreground-Service konnte aus dem Sensorfenster-Alarm nicht gestartet werden; Folgeslot wurde geplant (${error.javaClass.simpleName})",
                        nowEpochMs = System.currentTimeMillis(),
                    )
                }
        } finally {
            if (!handedOffToService) CgmWakeHandoff.release()
        }
    }
}
