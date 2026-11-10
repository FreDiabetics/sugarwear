package app.sugarwear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmReconnectScheduler
import app.sugarlicious.sensor.protocol.CgmSessionManager
import app.sugarlicious.sensor.protocol.CgmSessionState
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import app.sugarlicious.sensor.protocol.CollectorDiagnosticResult
import app.sugarlicious.sensor.protocol.CollectorDiagnosticStage
import app.sugarlicious.sensor.protocol.CollectorSlotStrategy
import app.sugarlicious.sensor.protocol.toCgm
import app.sugarlicious.uishared.ProductMigrationLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@dagger.hilt.android.AndroidEntryPoint
class CgmCollectorService : Service() {
    @javax.inject.Inject internal lateinit var sensorStore: dagger.Lazy<CgmSensorStateStore>

    @javax.inject.Inject internal lateinit var credentialStore: dagger.Lazy<CgmCredentialStore>

    @javax.inject.Inject internal lateinit var diagnosticStore: dagger.Lazy<CgmCollectorDiagnosticStore>
    private val processStartedElapsedMs by lazy { SystemClock.elapsedRealtime() }
    private lateinit var store: CgmSensorStateStore
    private lateinit var credentials: CgmCredentialStore
    private lateinit var attemptStore: CgmCollectorDiagnosticStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private var collectionJob: Job? = null
    private var cycleWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var cycleToken: Long = 0L
    private var runtimeRecycleRequested = false
    private val vigilClockUpdates by lazy { CgmVigilClockUpdates(this) }

    override fun onCreate() {
        super.onCreate()
        if (ProductMigrationLifecycle
                .isQuiesced(this) ||
            ProductMigrationLifecycle
                .isRetired(this)
        ) {
            stopSelf()
            return
        }
        serviceRunning = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "SugarWear", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Permanenter direkter Sensor-Collector"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        // Promote before opening retained stores or restoring alarms on a cold boot.
        startForegroundCollector()
        store = sensorStore.get()
        credentials = credentialStore.get()
        attemptStore = diagnosticStore.get()
        synchronized(CgmSensorStateStore.lifecycleLock) {
            store.saveFromCollector(CgmCollectorReliability.rehydrate(store.read(), System.currentTimeMillis()))
        }
        CgmCollectorRuntimeRegistry.register(
            owner = this,
            activeCheck = { collectionJob?.isActive == true },
            cancelAction = {
                ++cycleToken
                collectionJob?.cancel()
            },
        )
        CgmErrorNotifier.ensureChannel(this)
        CgmAlarmCoordinator.restore(this)
        vigilClockUpdates.start()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (ProductMigrationLifecycle
                .isQuiesced(this) ||
            ProductMigrationLifecycle
                .isRetired(this)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        val serviceStartAt = System.currentTimeMillis()
        if (intent?.action == ACTION_STOP) {
            stop(this)
            stopRuntimeForeground()
            return START_NOT_STICKY
        }

        val persisted =
            synchronized(CgmSensorStateStore.lifecycleLock) {
                enforceCgmCollectorEligibility(this, serviceStartAt, credentials.read() != null)
            }
        if (!persisted.collectorEnabled) {
            CgmSignalLossMonitor.cancel(this)
            CgmErrorNotifier.clearActive(this)
            CgmWakeHandoff.release()
            stopRuntimeForeground()
            return START_NOT_STICKY
        }

        // Android requires the foreground notification promptly. All persisted-ledger and runtime
        // recovery work is serialized on Dispatchers.IO after this call.
        startForegroundCollector()
        val action = intent?.action
        scope.launch {
            startMutex.withLock {
                prepareAndLaunchCycle(action, serviceStartAt)
            }
        }
        return START_STICKY
    }

    private fun prepareAndLaunchCycle(
        action: String?,
        serviceStartAt: Long,
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!store.read().collectorEnabled) {
            CgmWakeHandoff.release()
            return@synchronized
        }
        CgmRuntimeReconciler.reconcile(
            context = this,
            entryPoint = CgmRuntimeEntryPoint.SERVICE_START,
            liveCycle = collectionJob?.isActive == true,
            // ACTION_RECONNECT owns a receiver-delivered envelope that is consumed immediately
            // below. Repairing before consumption would replace slot N with N+1, after which the
            // safety scheduler would incorrectly arm N+2 for the duration of this BLE attempt.
            allowRepair = shouldRepairCgmRuntimeOnServiceStart(action),
            cancelLiveCycle = { collectionJob?.cancel() },
            nowEpochMs = serviceStartAt,
        )
        var persisted = store.read()
        if (!persisted.collectorEnabled) {
            CgmWakeHandoff.release()
            return@synchronized
        }

        CgmSignalLossMonitor.scheduleFromState(this, persisted)
        val request =
            when (action) {
                ACTION_RESTART -> CycleRequest.RESTART
                ACTION_MANUAL_SCAN -> CycleRequest.MANUAL
                else -> CycleRequest.AUTOMATIC
            }
        val scheduledCycle =
            if (action == ACTION_RECONNECT) {
                attemptStore.consumeScheduledCycle(serviceStartAt)
            } else {
                null
            }
        if (action == ACTION_RECONNECT) {
            scope.launch {
                applicationContext.recordCgmDiagnostic(
                    "FGS_RESTART_REQUESTED",
                    "Foreground-Service handoff accepted for scheduled G7 recovery",
                    DiagnosticSeverity.INFO,
                    mapOf("requestedReconnectEpoch" to scheduledCycle?.requestedReconnectEpoch),
                )
            }
        }

        // A process restored during sensor silence must not consume/rewrite the
        // already armed real window, including after a signal-loss gap.
        val startupWindow =
            if (request == CycleRequest.AUTOMATIC && scheduledCycle == null && collectionJob?.isActive != true) {
                cgmStartupSensorWindow(persisted, attemptStore.pendingScheduledCycle(), serviceStartAt)
            } else {
                null
            }
        if (startupWindow != null) {
            val restored = CgmReconnectAlarmScheduler.rearmScheduledCycle(this, startupWindow, persisted)
            restored.requestedReconnectEpoch?.let { reconnectAt ->
                store.saveFromCollector(
                    clearCgmScanRuntime(store.read()).copy(
                        nextReconnectEpochMs = reconnectAt,
                        protocolState = CgmProtocolState.WAITING_FOR_NEXT_READING,
                        connectionState = CgmConnectionState.DISCONNECTED,
                        activeAttemptId = null,
                    ),
                )
            }
            CgmWakeHandoff.release()
            return@synchronized
        }

        // Stage the next automatic slot before any scan/GATT work starts. The prior implementation
        // consumed the current alarm first and scheduled the next one only after cycle completion;
        // a process death mid-scan therefore left the collector with no future wake-up at all.
        if (request == CycleRequest.AUTOMATIC) {
            val safetyCycle =
                CgmReconnectAlarmScheduler.scheduleSafetyForCycle(
                    this,
                    scheduledCycle,
                    persisted,
                    serviceStartAt,
                )
            safetyCycle?.requestedReconnectEpoch?.let { reconnectAt ->
                store.saveFromCollector(store.read().copy(nextReconnectEpochMs = reconnectAt))
                persisted = store.read()
            }
        }

        // A new primary alarm is also the bounded liveness checkpoint for an older cycle. The
        // future N+1 trigger has already been staged above, so stale cleanup cannot strand the
        // collector or recreate the consumed N envelope.
        val staleCycleReplaced =
            if (request == CycleRequest.AUTOMATIC && collectionJob?.isActive == true) {
                CgmRuntimeReconciler
                    .reconcile(
                        context = this,
                        entryPoint = CgmRuntimeEntryPoint.SERVICE_START,
                        liveCycle = true,
                        allowRepair = true,
                        cancelLiveCycle = { collectionJob?.cancel() },
                        nowEpochMs = serviceStartAt,
                    ).detected.cleanupRequired
            } else {
                false
            }

        if (!staleCycleReplaced && shouldCoalesceCgmCollectorTrigger(request == CycleRequest.AUTOMATIC, collectionJob?.isActive == true)) {
            scope.launch {
                applicationContext.recordCgmDiagnostic(
                    "RECOVERY_TRIGGER_COALESCED",
                    "Duplicate automatic trigger ignored while the canonical collector cycle is active",
                    DiagnosticSeverity.INFO,
                    mapOf("expectedReadingEpoch" to scheduledCycle?.expectedReadingEpoch),
                )
            }
            CgmWakeHandoff.release()
            return@synchronized
        }
        launchCycle(request, scheduledCycle, serviceStartAt)
    }

    private fun launchCycle(
        request: CycleRequest,
        scheduledCycle: CollectorCycleTiming?,
        serviceStartAt: Long,
    ) {
        if (request != CycleRequest.AUTOMATIC) {
            // Replace the existing PendingIntent alarm directly. Never cancel first: process death
            // in a cancel -> set gap was one of the ways the recovery chain could disappear.
            val recovery = CgmReconnectAlarmScheduler.scheduleRecovery(this, store.read(), serviceStartAt)
            recovery?.requestedReconnectEpoch?.let { reconnectAt ->
                store.saveFromCollector(store.read().copy(nextReconnectEpochMs = reconnectAt))
            }
        }
        val previous = collectionJob
        val token = ++cycleToken
        collectionJob =
            scope.launch {
                previous?.cancelAndJoin()
                if (token != cycleToken) return@launch
                val wakeAt = acquireCycleWakeLock(request)
                CgmWakeHandoff.release()
                val cycle =
                    scheduledCycle?.copy(
                        serviceOnStartCommandAt = serviceStartAt,
                        wakeLockAcquiredAt = wakeAt,
                    )
                applicationContext.recordCgmDiagnostic(
                    "G7-COLLECT-100",
                    when (request) {
                        CycleRequest.AUTOMATIC -> "Automatic collector cycle started"
                        CycleRequest.MANUAL -> "Bounded manual sensor scan started"
                        CycleRequest.RESTART -> "Collector runtime restarted with retained sensor/session state"
                    },
                    metadata =
                        mapOf(
                            "scheduled" to (cycle != null),
                            "alarmLatenessMs" to cycle?.alarmLatenessMs,
                            "serviceStartLatenessMs" to cycle?.serviceStartLatenessMs,
                        ),
                )
                collectOnce(token, request, cycle)
            }
    }

    private suspend fun collectOnce(
        token: Long,
        request: CycleRequest,
        scheduledCycle: CollectorCycleTiming?,
    ) {
        val generation = store.collectorGeneration()

        fun persist(state: CgmPersistedState): Boolean =
            synchronized(CgmSensorStateStore.lifecycleLock) {
                if (token != cycleToken) return@synchronized false
                store.saveFromCollector(state, generation)
            }
        val startedAt = System.currentTimeMillis()
        val attempt =
            attemptStore.begin(
                manual = request == CycleRequest.MANUAL,
                restart = request == CycleRequest.RESTART,
                cycle = scheduledCycle,
                nowEpochMs = startedAt,
                deadlineEpochMs = startedAt + collectorAttemptDeadlineMs(store.read()),
            )
        val attemptId = attempt.attemptId
        CgmExpectedWindowLedger(
            this,
        ).markCycleStarted(scheduledCycle?.expectedWindowId, startedAt, attemptId, scheduledCycle?.wakeLockAcquiredAt)
        val persisted =
            store.read().let { state ->
                if (request == CycleRequest.RESTART) resetCgmRuntimeForRestart(state) else state
            }
        if (!persist(persisted.copy(activeAttemptId = attemptId))) {
            finishCycle(token)
            return
        }
        val configuredSensor = persisted.sensor
        if (!persisted.collectorEnabled) {
            attemptStore.setClassification(attemptId, CollectorCycleClassification.CANCELLED)
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.COMPLETE,
                CollectorDiagnosticResult.CANCELLED,
                "Collector ist deaktiviert",
            )
            persist(store.read().copy(activeAttemptId = null, lastAttemptCompletedAtEpochMs = System.currentTimeMillis()))
            finishCycle(token)
            return
        }
        if (configuredSensor == null) {
            fail(
                persisted,
                CgmCollectorError(
                    "G7-SETUP-001",
                    false,
                    System.currentTimeMillis(),
                    "Sensor muss zuerst eingerichtet und der Collector gestartet werden",
                ),
                attemptId,
                startedAt,
            )
            finishCycle(token)
            return
        }
        val storedCredentials = credentials.read()
        if (storedCredentials == null) {
            fail(
                persisted,
                CgmCollectorError("G7-SETUP-002", false, System.currentTimeMillis(), "Sensorcode fehlt oder ist nicht mehr lesbar"),
                attemptId,
                startedAt,
            )
            finishCycle(token)
            return
        }

        // A shared key is only emitted after the candidate completed the G7 application-level
        // authentication exchange. Keep that authenticated address as the sensor-change anchor
        // even when the following asynchronous Android bond has not completed yet. Otherwise the
        // next discovery can pick the still-advertising removed sensor again.
        val collectionSensor = restoreAuthenticatedCgmAddress(configuredSensor, storedCredentials.sharedKeyAddress)
        if (collectionSensor != configuredSensor) {
            persist(store.read().copy(sensor = collectionSensor))
        }

        try {
            val collector = CgmHardwareCollector(this)
            persist(store.read().copy(health = CgmCollectorReliability.collecting(store.read().health)))
            val sessionId = collectionSensor.sessionId ?: collectionSensor.sensorId
            val ledger = CgmExpectedWindowLedger(this)
            var recoveryGap: app.sugarlicious.sensor.protocol.CollectorExpectedWindow?
            val lastStoredSensorClock =
                CgmReadingDatabase(this).let { database ->
                    try {
                        val reconciliation =
                            ledger.reconcileUnfinished(
                                sensorId = collectionSensor.sensorId,
                                sessionId = sessionId,
                                nowEpochMs = System.currentTimeMillis(),
                                readingNear = { window ->
                                    database.validReadingNear(collectionSensor.sensorId, sessionId, window.expectedAt)
                                },
                            )
                        if (reconciliation.satisfiedByReading > 0 || reconciliation.recoverableGaps > 0) {
                            applicationContext.recordCgmDiagnostic(
                                "G7-LEDGER-RECONCILED",
                                "Persisted collector windows reconciled against durable readings",
                                metadata =
                                    mapOf(
                                        "satisfiedByReading" to reconciliation.satisfiedByReading,
                                        "recoverableGaps" to reconciliation.recoverableGaps,
                                    ),
                            )
                        }
                        recoveryGap = ledger.oldestOpenGap(collectionSensor.sensorId, sessionId)
                        while (recoveryGap != null) {
                            val gap = recoveryGap
                            val presentAt =
                                database.validReadingNear(collectionSensor.sensorId, sessionId, gap.expectedAt)
                                    ?: break
                            ledger.markSatisfiedByExistingReading(gap.expectedWindowId, presentAt, System.currentTimeMillis())
                            recoveryGap = ledger.oldestOpenGap(collectionSensor.sensorId, sessionId)
                        }
                        recoveryGap?.let {
                            database.getBackfillAnchorSensorClockForGap(collectionSensor.sensorId, sessionId, it.expectedAt)
                        } ?: database.getBackfillAnchorSensorClock(collectionSensor.sensorId, sessionId)
                    } finally {
                        database.close()
                    }
                }
            if (recoveryGap != null) {
                applicationContext.recordCgmDiagnostic(
                    "G7-BACKFILL-GAP-SELECTED",
                    "Oldest persisted recoverable gap selected for the next successful LIVE cycle",
                    metadata =
                        mapOf(
                            "expectedWindowId" to recoveryGap.expectedWindowId,
                            "gapExpectedAt" to recoveryGap.expectedAt,
                            "sensorId" to collectionSensor.sensorId,
                            "sessionId" to sessionId,
                            "recoveryAttemptCount" to recoveryGap.recoveryAttemptCount,
                            "anchorSensorClock" to lastStoredSensorClock,
                        ),
                )
            }
            val pairingRemainingMs =
                store
                    .read()
                    .takeIf { !hasEstablishedCgmSensorSession(it) }
                    ?.pairingDeadlineEpochMs
                    ?.minus(System.currentTimeMillis())
            if (pairingRemainingMs != null && pairingRemainingMs <= 0L) {
                throw CgmBleException("G7-PAIRING-TIMEOUT", "Verbindung zum Sensor fehlgeschlagen", false)
            }
            val boundedScanTimeout =
                when {
                    pairingRemainingMs != null -> pairingRemainingMs
                    request == CycleRequest.AUTOMATIC -> null
                    else -> CGM_RECONNECT_SCAN_TIMEOUT_MS
                }
            var liveCommittedBeforeBackfill = false
            var backfillRequestedAt: Long? = null
            var liveMeasuredAtForRecovery: Long? = null
            val result =
                withinCgmInitialPairingBudget(store.read(), System.currentTimeMillis()) {
                    val cycleContext = currentCoroutineContext()
                    collector.collect(
                        initialSensor = collectionSensor,
                        credentials = storedCredentials,
                        onState = { protocolState ->
                            cycleContext.ensureActive()
                            if (token != cycleToken || !store.read().collectorEnabled) throw CancellationException("Collector paused")
                            val current = store.read()
                            val now = System.currentTimeMillis()
                            requireCgmInitialPairingBudget(current, now)
                            val health =
                                when (protocolState) {
                                    CgmProtocolState.SENSOR_FOUND -> CgmCollectorReliability.reachable(current.health, now)
                                    CgmProtocolState.DISCOVERING, CgmProtocolState.DISCOVERING_SERVICES ->
                                        CgmCollectorReliability.reachable(current.health, now, connected = true)
                                    CgmProtocolState.AUTHENTICATED -> CgmCollectorReliability.authenticated(current.health, now)
                                    else -> current.health
                                }
                            val next =
                                current.copy(
                                    protocolState = protocolState,
                                    connectionState = protocolState.toConnectionState(),
                                    sessionState = protocolState.toSessionState(),
                                    authenticationState =
                                        if (protocolState == CgmProtocolState.AUTHENTICATED || protocolState == CgmProtocolState.REQUESTING_GLUCOSE) {
                                            app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED
                                        } else {
                                            current.authenticationState
                                        },
                                    pairingAttemptId = if (protocolState == CgmProtocolState.AUTHENTICATED || protocolState == CgmProtocolState.REQUESTING_GLUCOSE) null else current.pairingAttemptId,
                                    pairingStartedAtEpochMs = if (protocolState == CgmProtocolState.AUTHENTICATED || protocolState == CgmProtocolState.REQUESTING_GLUCOSE) null else current.pairingStartedAtEpochMs,
                                    pairingDeadlineEpochMs = if (protocolState == CgmProtocolState.AUTHENTICATED || protocolState == CgmProtocolState.REQUESTING_GLUCOSE) null else current.pairingDeadlineEpochMs,
                                    lastError = if (protocolState == CgmProtocolState.AUTHENTICATED || protocolState == CgmProtocolState.REQUESTING_GLUCOSE) null else current.lastError,
                                    scanStartedAtEpochMs =
                                        if (protocolState == CgmProtocolState.SCANNING) now else current.scanStartedAtEpochMs,
                                    scanTimeoutAtEpochMs =
                                        if (protocolState == CgmProtocolState.SCANNING) {
                                            current.pairingDeadlineEpochMs
                                                ?: now + (boundedScanTimeout ?: cgmScanTimeoutMs(collectionSensor))
                                        } else {
                                            current.scanTimeoutAtEpochMs
                                        },
                                    lastScanAtEpochMs =
                                        if (protocolState == CgmProtocolState.SCANNING) now else current.lastScanAtEpochMs,
                                    health = health,
                                )
                            persist(next)
                            updateAttemptCycleForProtocolState(attemptId, protocolState, now)
                            recordAttemptProtocolState(attemptId, protocolState, collectionSensor.sensorId)
                            scope.launch {
                                applicationContext.recordCgmDiagnostic(
                                    protocolState.diagnosticCode(),
                                    protocolState.label(),
                                    metadata = mapOf("protocolState" to protocolState.name),
                                )
                            }
                        },
                        onSharedKey = { address, key ->
                            synchronized(CgmSensorStateStore.lifecycleLock) {
                                cycleContext.ensureActive()
                                if (token != cycleToken || generation != store.collectorGeneration() || !store.read().collectorEnabled) {
                                    throw CancellationException("Collector paused")
                                }
                                requireCgmInitialPairingBudget(store.read(), System.currentTimeMillis())
                                credentials.saveSharedKey(address, key)
                                val current = store.read()
                                if (current.sensor?.deviceAddress.isNullOrBlank()) {
                                    if (!persist(current.copy(sensor = current.sensor?.copy(deviceAddress = address)))) {
                                        throw CancellationException("Collector association changed")
                                    }
                                }
                                if (allowsRetiredCgmCandidateForSetup(collectionSensor, storedCredentials.sharedKey != null, storedCredentials.sharedKeyAddress)) {
                                    CgmRetiredSensors(this@CgmCollectorService).reactivateAuthenticated(address)
                                }
                            }
                        },
                        scanTimeoutMsOverride = boundedScanTimeout,
                        reconnectStrategy =
                            effectiveCgmReconnectStrategy(
                                CgmReconnectStrategyStore.read(this),
                                store.read().health,
                            ),
                        onTelemetry = { telemetry ->
                            // Cleanup telemetry runs while the cycle is cancelling; never interrupt
                            // it before AndroidSensorBle has disconnected and closed the GATT client.
                            if (telemetry is CgmDiscoveryCandidateDeferred || telemetry is CgmDiscoveryCandidateRejected) {
                                val errorCode =
                                    when (telemetry) {
                                        is CgmDiscoveryCandidateDeferred -> telemetry.errorCode
                                        is CgmDiscoveryCandidateRejected -> telemetry.errorCode
                                    }
                                val occurredAt =
                                    when (telemetry) {
                                        is CgmDiscoveryCandidateDeferred -> telemetry.timestampEpochMs
                                        is CgmDiscoveryCandidateRejected -> telemetry.timestampEpochMs
                                    }
                                persist(
                                    store.read().copy(
                                        lastError =
                                            CgmCollectorError(
                                                errorCode,
                                                true,
                                                occurredAt,
                                                if (telemetry is CgmDiscoveryCandidateRejected) {
                                                    "Ein G7-Kandidat konnte nicht authentifiziert werden; Suche wird fortgesetzt"
                                                } else {
                                                    "G7-Kandidat nicht erreichbar · weitere Sensoren werden gesucht"
                                                },
                                            ),
                                    ),
                                )
                            }
                            if (telemetry is CgmBackfillRequestTelemetry) {
                                backfillRequestedAt = telemetry.timestampEpochMs
                                liveMeasuredAtForRecovery?.let { liveMeasuredAt ->
                                    CgmExpectedWindowLedger(this).markRecoveryRequestStarted(
                                        sensorId = collectionSensor.sensorId,
                                        sessionId = sessionId,
                                        liveMeasuredAt = liveMeasuredAt,
                                        requestedAt = telemetry.timestampEpochMs,
                                    )
                                }
                            }
                            recordBleTelemetry(attemptId, telemetry)
                        },
                        attemptId = attemptId,
                        lastStoredSensorClock = lastStoredSensorClock,
                        allowFallbackScan = CgmCollectorReliability.shouldRunPresenceScan(store.read().health),
                        onLiveReading = { live ->
                            cycleContext.ensureActive()
                            if (token != cycleToken || !store.read().collectorEnabled) throw CancellationException("Collector paused")
                            liveMeasuredAtForRecovery = live.sensorTimestampEpochMs
                            CgmReadingDatabase(this).let { database ->
                                try {
                                    database.coalesceChangeNotifications {
                                        val previous = database.getLatestValidBefore(live.sensorId, live.sessionId, live.sensorTimestampEpochMs)
                                        val provisional = live.toCgm(previous)
                                        if (provisional.status == CgmReadingStatus.VALID) {
                                            liveCommittedBeforeBackfill = database.insertOrIgnore(provisional)
                                            val current = store.read()
                                            // Publish only after both the measurement and sensor state are
                                            // visible. A first reading must not refresh Vigil with UNKNOWN
                                            // or the preceding sensor-error state while backfill is running.
                                            persist(
                                                current.copy(
                                                    sensor = sensorForCgmLivePublication(collectionSensor, current.sensor, live.sensorState),
                                                    lastReading = provisional,
                                                    sessionState = app.sugarlicious.sensor.protocol.CgmSessionState.ACTIVE,
                                                    protocolState = CgmProtocolState.BACKFILL,
                                                    lastSuccessfulConnectionEpochMs = System.currentTimeMillis(),
                                                ),
                                            )
                                        }
                                    }
                                } finally {
                                    database.close()
                                }
                            }
                        },
                    )
                }
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.GATT_CLOSE,
                CollectorDiagnosticResult.SUCCESS,
                "GATT-Verbindung sauber geschlossen",
                sensorId = result.sensor.sensorId,
            )
            result.sharedKey?.let { key -> result.sensor.deviceAddress?.let { credentials.saveSharedKey(it, key) } }

            val now = System.currentTimeMillis()
            val previousValid =
                CgmReadingDatabase(this).let { database ->
                    try {
                        database.getLatestValidBefore(
                            sensorId = result.reading.sensorId,
                            sessionId = result.reading.sessionId,
                            beforeEpochMs = result.reading.sensorTimestampEpochMs,
                        )
                    } finally {
                        database.close()
                    }
                }
            var reading = result.reading.toCgm(previousValid)
            attemptStore.updateCycle(attemptId) {
                it.copy(
                    glucosePacketReceivedAt = it.glucosePacketReceivedAt ?: now,
                    sensorAgeSeconds = reading.sensorAgeSeconds,
                    measurementTimestamp = reading.timestampEpochMs,
                    sequenceNumber = reading.sequenceNumber,
                )
            }
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.VALIDATION,
                if (reading.status ==
                    CgmReadingStatus.INVALID
                ) {
                    CollectorDiagnosticResult.RECOVERABLE_ERROR
                } else {
                    CollectorDiagnosticResult.SUCCESS
                },
                when (reading.status) {
                    CgmReadingStatus.VALID -> "Glukosewert validiert"
                    CgmReadingStatus.SENSOR_ERROR -> "Sensorfehlerstatus validiert"
                    CgmReadingStatus.INVALID -> "Ungültiger Glukosewert verworfen"
                },
                sensorId = reading.sensorId,
                sequence = reading.sequenceNumber,
            )

            val inserted =
                try {
                    CgmReadingDatabase(this).let { database ->
                        try {
                            liveCommittedBeforeBackfill || database.insertOrIgnore(reading)
                        } finally {
                            database.close()
                        }
                    }
                } catch (error: Throwable) {
                    throw CgmBleException("G7-STORE-500", "Lokaler G7-Wert konnte nicht gespeichert werden", true, error)
                }
            var backfillProcessedSuccessfully = true
            val (backfillInserted, committedBackfillMeasurements) =
                try {
                    CgmReadingDatabase(this).let { database ->
                        try {
                            var predecessor =
                                database.getLatestValidBefore(
                                    reading.sensorId,
                                    reading.sessionId,
                                    result.backfillReadings.minOfOrNull { it.sensorTimestampEpochMs } ?: reading.timestampEpochMs,
                                )
                            var acceptedCount = 0
                            val committed = mutableListOf<Long>()
                            database.coalesceChangeNotifications {
                                result.backfillReadings
                                    .sortedBy { it.sensorTimestampEpochMs }
                                    .forEach { historical ->
                                        val converted = historical.toCgm(predecessor)
                                        if (database.insertOrIgnore(converted)) acceptedCount += 1
                                        if (converted.status == CgmReadingStatus.VALID) predecessor = converted
                                        if (database.validReadingNear(converted.sensorId, converted.sessionId, converted.timestampEpochMs) !=
                                            null
                                        ) {
                                            committed += converted.timestampEpochMs
                                        }
                                    }
                            }
                            acceptedCount to committed
                        } finally {
                            database.close()
                        }
                    }
                } catch (error: Throwable) {
                    backfillProcessedSuccessfully = false
                    applicationContext.recordCgmDiagnostic(
                        "G7-BACKFILL-500",
                        "Collector history could not be stored",
                        metadata = mapOf("error" to error.javaClass.simpleName),
                    )
                    0 to emptyList<Long>()
                }
            // The live packet arrives before its history stream. After a signal-loss gap the
            // first live value was therefore initially compared with the old pre-gap value and
            // had no valid delta. Re-resolve its immediate predecessor after committing backfill,
            // then update the already stored live row and all observing surfaces atomically.
            if (result.backfillReadings.isNotEmpty()) {
                CgmReadingDatabase(this).let { database ->
                    try {
                        val restoredPrevious =
                            database.getLatestValidBefore(
                                reading.sensorId,
                                reading.sessionId,
                                reading.timestampEpochMs,
                            )
                        reading = result.reading.toCgm(restoredPrevious)
                        database.updateDerivedFields(reading)
                    } finally {
                        database.close()
                    }
                }
            }
            val backfillStart =
                CgmCollectorBackfillProtocol.requestedStart(
                    lastStoredSensorClock,
                    result.reading.sensorClockSeconds ?: 0L,
                )
            val backfillEnd = CgmCollectorBackfillProtocol.requestedEnd(result.reading.sensorClockSeconds ?: 0L)
            if (backfillStart != null && backfillEnd != null) {
                applicationContext.recordCgmDiagnostic(
                    "G7-BACKFILL-200",
                    "Collector history request completed",
                    metadata =
                        mapOf(
                            "backfillRequestId" to "$attemptId:$backfillStart-$backfillEnd",
                            "triggerReason" to if (lastStoredSensorClock == null) "INITIAL_HISTORY" else "GAP_OR_RECONNECT",
                            "requestedStartSensorClock" to backfillStart,
                            "requestedEndSensorClock" to backfillEnd,
                            "responseReceived" to true,
                            "parsed" to result.backfillReadings.size,
                            "inserted" to backfillInserted,
                            "duplicateOrRejected" to (result.backfillReadings.size - backfillInserted).coerceAtLeast(0),
                            "terminalState" to "COMPLETE",
                        ),
                )
            }
            val storedAt = System.currentTimeMillis()
            CgmExpectedWindowLedger(this).markNextLiveAndBackfill(
                sensorId = reading.sensorId,
                sessionId = reading.sessionId,
                liveMeasuredAt = reading.timestampEpochMs,
                liveReceivedAt = reading.receivedAtEpochMs,
                committedAt = storedAt,
                requestedAt = backfillRequestedAt,
                responseAt = if (backfillRequestedAt != null) now else null,
                inserted = committedBackfillMeasurements,
                responseProcessedSuccessfully = backfillProcessedSuccessfully,
            )
            val ledgerUpdated = CgmExpectedWindowLedger(this).markReading(scheduledCycle?.expectedWindowId, storedAt)
            if (scheduledCycle?.expectedWindowId != null && !ledgerUpdated) {
                applicationContext.recordCgmDiagnostic(
                    "G7-LEDGER-ID-MISMATCH",
                    "Successful collector cycle could not resolve its expected window",
                    DiagnosticSeverity.ERROR,
                    mapOf(
                        "expectedWindowId" to scheduledCycle.expectedWindowId,
                        "expectedReadingEpoch" to scheduledCycle.expectedReadingEpoch,
                        "classification" to CollectorCycleClassification.SUCCESS_FRESH.name,
                    ),
                )
            }
            attemptStore.updateCycle(attemptId) { it.copy(storeCompletedAt = storedAt) }
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.STORE,
                CollectorDiagnosticResult.SUCCESS,
                when {
                    !inserted -> "Bereits gespeichertes Collector-Ereignis dedupliziert"
                    reading.status == CgmReadingStatus.VALID -> "Glukosewert lokal auf der Watch gespeichert"
                    else -> "Collector-Status lokal zur Diagnose gespeichert"
                },
                sensorId = reading.sensorId,
                sequence = reading.sequenceNumber,
                nowEpochMs = storedAt,
            )

            currentCoroutineContext().ensureActive()
            if (token != cycleToken) throw CancellationException("Collector paused")
            val documentedSensor =
                result.sensor.copy(
                    sensorStartEpochMs = result.reading.sensorStartEpochMs,
                    sensorEndEpochMs = result.reading.sensorEndEpochMs,
                    graceEndEpochMs = result.reading.graceEndEpochMs,
                )
            val fresh = isFreshCgmCycleReading(reading, storedAt)
            val manager = CgmSessionManager(store.read().copy(sensor = documentedSensor))
            manager.authenticationSucceeded()
            val next =
                clearCgmScanRuntime(manager.readingReceived(reading, fresh = fresh, nowEpochMs = storedAt)).copy(
                    sensor = documentedSensor.copy(state = result.reading.sensorState),
                    connectionState = CgmConnectionState.DISCONNECTED,
                    protocolState = CgmProtocolState.WAITING_FOR_NEXT_READING,
                    lastSuccessfulConnectionEpochMs = storedAt,
                    activeAttemptId = null,
                    lastAttemptCompletedAtEpochMs = storedAt,
                    pairingAttemptId = null,
                    pairingStartedAtEpochMs = null,
                    pairingDeadlineEpochMs = null,
                    health = CgmCollectorReliability.succeeded(store.read().health, reading.timestampEpochMs, storedAt),
                )
            if (!persist(next)) throw CancellationException("Collector lifecycle changed")

            // Persistence is the collector's critical commit point. Finalize the attempt before
            // notifications, alarm presentation or the general diagnostic stream: none of those
            // secondary consumers may leave an already stored glucose packet looking HUNG.
            val classification =
                when {
                    reading.status == CgmReadingStatus.INVALID -> CollectorCycleClassification.INVALID_PACKET
                    reading.status == CgmReadingStatus.VALID && fresh -> CollectorCycleClassification.SUCCESS_FRESH
                    reading.status == CgmReadingStatus.VALID -> CollectorCycleClassification.SUCCESS_AGED
                    else -> CollectorCycleClassification.INVALID_PACKET
                }
            attemptStore.updateCycle(attemptId) { cycle ->
                cycle.copy(
                    slotStrategy =
                        when {
                            cycle.fallbackScanUsed -> CollectorSlotStrategy.FALLBACK_SCAN_SUCCESS
                            cycle.directConnectAttempts > 1 -> CollectorSlotStrategy.DIRECT_RETRY_SUCCESS
                            else -> CollectorSlotStrategy.DIRECT_ONLY_SUCCESS
                        },
                    radioFailureStreak = 0,
                    radioDegradedCluster = false,
                )
            }
            attemptStore.setClassification(attemptId, classification)
            val ageMinutes = ((storedAt - reading.timestampEpochMs).coerceAtLeast(0L) / 60_000L)
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.COMPLETE,
                if (classification ==
                    CollectorCycleClassification.SUCCESS_FRESH
                ) {
                    CollectorDiagnosticResult.SUCCESS
                } else {
                    CollectorDiagnosticResult.RECOVERABLE_ERROR
                },
                when (classification) {
                    CollectorCycleClassification.SUCCESS_FRESH -> "SUCCESS_FRESH · ${reading.glucoseMgDl.toInt()} mg/dL"
                    CollectorCycleClassification.SUCCESS_AGED -> "SUCCESS_AGED · Empfangen jetzt · Messwert $ageMinutes min alt"
                    else -> "${classification.name} · kein frischer aktueller CGM-Wert"
                },
                sensorId = reading.sensorId,
                sequence = reading.sequenceNumber,
                durationMs = storedAt - startedAt,
                nowEpochMs = storedAt,
            )

            try {
                applicationContext.recordCgmDiagnostic(
                    "G7-DATA-200",
                    when {
                        reading.status == CgmReadingStatus.VALID && fresh -> "Fresh validated G7 reading stored locally on Watch"
                        reading.status == CgmReadingStatus.VALID -> "Aged validated G7 reading stored locally on Watch"
                        reading.status == CgmReadingStatus.SENSOR_ERROR -> "Validated G7 sensor-error status stored locally on Watch"
                        else -> "Invalid G7 glucose stored for diagnostics only"
                    },
                    metadata =
                        mapOf(
                            "sequence" to reading.sequenceNumber,
                            "sensorState" to result.reading.sensorState,
                            "sensorClockSeconds" to reading.rawSourceTimestamp,
                            "sensorAgeSeconds" to reading.sensorAgeSeconds,
                            "measurementTimestamp" to reading.timestampEpochMs,
                            "freshCycle" to fresh,
                            "mobileBackfill" to false,
                            "collectorBackfillReceived" to result.backfillReadings.size,
                            "collectorBackfillInserted" to backfillInserted,
                        ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The collector attempt is already durably COMPLETE; diagnostics are best effort.
            }

            synchronized(CgmSensorStateStore.lifecycleLock) {
                if (token != cycleToken || generation != store.collectorGeneration() || !store.read().collectorEnabled) {
                    throw CancellationException("Collector lifecycle changed")
                }
                if (fresh) {
                    runCatching { CgmErrorNotifier.markRecovered(this) }
                    runCatching { CgmAlarmCoordinator.onReading(this, reading) }
                }
                if (shouldUpdateCgmForegroundNotification(inserted, classification)) {
                    updateForegroundForNewReading()
                }
                // For aged/invalid packets next.lastReading still points to the last fresh value, so
                // signal loss remains anchored to real current data rather than receive time.
                val scheduledReconnectAt = scheduleReconnect(next) ?: next.nextReconnectEpochMs
                CgmSignalLossMonitor.scheduleFromState(this, next)
                scheduledReconnectAt?.let { reconnectAt ->
                    attemptStore.record(
                        attemptId,
                        CollectorDiagnosticStage.WAITING_FOR_WINDOW,
                        CollectorDiagnosticResult.INFO,
                        "Nächstes Sensorfenster geplant",
                        durationMs = (reconnectAt - System.currentTimeMillis()).coerceAtLeast(0L),
                    )
                }
            }
        } catch (error: CgmBleException) {
            currentCoroutineContext().ensureActive()
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.GATT_CLOSE,
                CollectorDiagnosticResult.INFO,
                "BLE-/GATT-Ressourcen geschlossen",
            )
            fail(
                store.read(),
                CgmCollectorError(error.errorCode, error.recoverable, System.currentTimeMillis(), error.message),
                attemptId,
                startedAt,
            )
        } catch (_: TimeoutCancellationException) {
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.GATT_CLOSE,
                CollectorDiagnosticResult.INFO,
                "Scan-/GATT-Zeitfenster beendet",
            )
            fail(
                store.read(),
                CgmCollectorError(
                    "G7-BLE-111",
                    true,
                    System.currentTimeMillis(),
                    "Sensor aktuell nicht erreichbar – nächster automatischer Versuch folgt",
                ),
                attemptId,
                startedAt,
            )
        } catch (_: CancellationException) {
            attemptStore.setClassification(attemptId, CollectorCycleClassification.CANCELLED)
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.COMPLETE,
                CollectorDiagnosticResult.CANCELLED,
                "CANCELLED · Collection-Versuch kontrolliert beendet",
                durationMs = System.currentTimeMillis() - startedAt,
            )
            persist(store.read().copy(activeAttemptId = null, lastAttemptCompletedAtEpochMs = System.currentTimeMillis()))
        } catch (_: SecurityException) {
            fail(
                store.read(),
                CgmCollectorError("G7-PERM-401", false, System.currentTimeMillis(), "Bluetooth-Berechtigung fehlt"),
                attemptId,
                startedAt,
            )
        } catch (error: Throwable) {
            fail(
                store.read(),
                CgmCollectorError("G7-INT-500", true, System.currentTimeMillis(), "Unerwarteter Collector-Fehler"),
                attemptId,
                startedAt,
                unexpectedFailureMetadata("COLLECT", error),
            )
        } finally {
            finishCycle(token)
        }
    }

    private fun fail(
        state: CgmPersistedState,
        error: CgmCollectorError,
        attemptId: Long,
        startedAtEpochMs: Long,
        failureMetadata: Map<String, Any?> = emptyMap(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!store.read().collectorEnabled || store.read().activeAttemptId != attemptId) return@synchronized
        val cycle = attemptStore.snapshot().firstOrNull { it.attemptId == attemptId }?.cycle
        val bounded = ensureCgmPairingAttempt(state, System.currentTimeMillis())
        if (!bounded.collectorEnabled) {
            attemptStore.setClassification(attemptId, classifyCgmCycleFailure(error.code, cycle))
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.ERROR,
                CollectorDiagnosticResult.FATAL_ERROR,
                bounded.lastError?.safeMessage ?: "Verbindung beendet",
                errorCode = bounded.lastError?.code,
                durationMs = System.currentTimeMillis() - startedAtEpochMs,
            )
            store.save(bounded)
            enforceCgmCollectorEligibility(this, System.currentTimeMillis(), credentials.read() != null)
            return@synchronized
        }
        val stagedSafety =
            stagedSafetyCycle(
                cycle,
                attemptStore.pendingScheduledCycle(),
            )
        val managed = CgmSessionManager(state).failure(error)
        val softWindowFailure = error.recoverable && error.code in SOFT_WINDOW_ERRORS
        val sensorAdvertisementSeen = (cycle?.scanNamedCgmResults ?: 0) > 0 || (cycle?.scanExactAddressResults ?: 0) > 0
        val foreignAdvertisementsSeen = (cycle?.scanTotalResults ?: 0) > 0 && !sensorAdvertisementSeen
        val failureClass = cgmFailureClass(error.code, sensorAdvertisementSeen, foreignAdvertisementsSeen, cycle?.directConnectResult)
        val reliability =
            CgmCollectorReliability.failed(
                health = state.health,
                failure = failureClass,
                sensorAdvertisementSeen = sensorAdvertisementSeen,
                foreignAdvertisementsSeen = foreignAdvertisementsSeen,
                now = System.currentTimeMillis(),
            )
        val next =
            clearCgmScanRuntime(managed).copy(
                connectionState = CgmConnectionState.DISCONNECTED,
                protocolState = if (softWindowFailure) CgmProtocolState.RECOVERING else CgmProtocolState.ERROR,
                activeAttemptId = null,
                lastAttemptCompletedAtEpochMs = System.currentTimeMillis(),
                nextReconnectEpochMs =
                    stagedSafety?.expectedReadingEpoch?.minus(CgmReconnectScheduler.PRECONNECT_LEAD_MS)
                        ?: managed.nextReconnectEpochMs,
                health = reliability,
            )
        store.saveFromCollector(next)
        // The safety slot was armed with the PREVIOUS health/strategy. Reapply the same
        // canonical slot after failure so presence-first recovery gets its full scan lead.
        // scheduleReconnect replaces the PendingIntent directly; it never cancels first.
        val scheduledReconnectAt = scheduleReconnect(next)
        scheduledReconnectAt?.let { reconnectAt ->
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.WAITING_FOR_WINDOW,
                CollectorDiagnosticResult.INFO,
                "Nächstes Sensorfenster geplant",
                errorCode = error.code,
                durationMs = (reconnectAt - System.currentTimeMillis()).coerceAtLeast(0L),
            )
        }
        CgmSignalLossMonitor.scheduleFromState(this, next)
        val classification =
            when {
                error.code.startsWith("G7-SETUP-") || error.code.startsWith("G7-PERM-") -> CollectorCycleClassification.SERVICE_START_FAILED
                error.code == "G7-STORE-500" -> CollectorCycleClassification.STORE_FAILED
                else -> classifyCgmCycleFailure(error.code, cycle)
            }
        val ledgerUpdated = CgmExpectedWindowLedger(this).markFinal(cycle?.expectedWindowId, classification, recoveryRequired = true)
        if (cycle?.expectedWindowId != null && !ledgerUpdated) {
            scope.launch {
                applicationContext.recordCgmDiagnostic(
                    "G7-LEDGER-ID-MISMATCH",
                    "Failed collector cycle could not resolve its expected window",
                    DiagnosticSeverity.ERROR,
                    mapOf(
                        "expectedWindowId" to cycle.expectedWindowId,
                        "expectedReadingEpoch" to cycle.expectedReadingEpoch,
                        "classification" to classification.name,
                    ),
                )
            }
        }
        if (isCompleteRadioFailure(classification, cycle)) {
            // Attempt-shape counting used to reset on the direct-only cycle between two fallback
            // cycles. A watch could therefore spend days in NO_CALLBACK while always reporting
            // radioFailureStreak=1. The persisted health state is the canonical cross-cycle
            // counter and survives process/service recreation.
            val streak = nextRadioFailureStreak(state.health, failureClass)
            val degraded = streak >= RADIO_DEGRADED_CLUSTER_THRESHOLD
            attemptStore.updateCycle(attemptId) {
                it.copy(
                    radioFailureStreak = streak,
                    radioDegradedCluster = degraded,
                    slotStrategy = CollectorSlotStrategy.FULL_SLOT_FAILED,
                )
            }
            if (degraded) {
                val staleScannerStopped = SensorScanner.forceCleanup()
                val callbackRuntimeReset = CgmGattCallbackDispatcher.reset()
                val now = System.currentTimeMillis()
                val recycleStore = CgmRuntimeRecycleStore(this)
                val recycleRuntime =
                    shouldRecycleCgmRuntime(
                        cycle = cycle?.copy(radioFailureStreak = streak, radioDegradedCluster = degraded),
                        radioFailureStreak = streak,
                        knownSensorAddress = !state.sensor?.deviceAddress.isNullOrBlank(),
                        lastRecycleAtEpochMs = recycleStore.lastRecycleAtEpochMs(),
                        nowEpochMs = now,
                    ) &&
                        recycleStore.tryClaim(now)
                if (recycleRuntime) {
                    // Retain the user setting, sensor/session, last reading, history and ledger.
                    // Only volatile protocol state is reset before recreating the Service runtime.
                    store.saveFromCollector(resetCgmRuntimeForRestart(store.read()))
                    runtimeRecycleRequested = true
                }
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.RECOVERY,
                    CollectorDiagnosticResult.INFO,
                    "RADIO_DEGRADED_CLUSTER · streak=$streak · staleScannerStopped=$staleScannerStopped · callbackRuntimeReset=$callbackRuntimeReset · serviceRuntimeRecycle=$recycleRuntime",
                    errorCode = "G7-RADIO-CLUSTER",
                )
            }
        } else {
            attemptStore.updateCycle(attemptId) { it.copy(slotStrategy = CollectorSlotStrategy.FULL_SLOT_FAILED) }
        }
        attemptStore.setClassification(attemptId, classification)
        attemptStore.record(
            attemptId,
            CollectorDiagnosticStage.ERROR,
            if (error.recoverable) CollectorDiagnosticResult.RECOVERABLE_ERROR else CollectorDiagnosticResult.FATAL_ERROR,
            "${classification.name} · ${error.safeMessage}",
            errorCode = error.code,
            sensorId = next.sensor?.sensorId,
            durationMs = System.currentTimeMillis() - startedAtEpochMs,
        )

        val alarmsEnabled = CgmAlertPolicyStore.alarmsEnabled(this)
        if (shouldPostImmediateCollectorAlert(alarmsEnabled, error, next.sessionState)) {
            CgmErrorNotifier.show(this, error)
        }
        scope.launch {
            applicationContext.recordCgmDiagnostic(
                error.code,
                error.safeMessage,
                if (error.recoverable) DiagnosticSeverity.WARNING else DiagnosticSeverity.ERROR,
                mapOf(
                    "recoverable" to error.recoverable,
                    "classification" to classification.name,
                    "canonicalWatchAlerts" to alarmsEnabled,
                    "userAlertPosted" to shouldPostImmediateCollectorAlert(alarmsEnabled, error, next.sessionState),
                    "retryCount" to next.retryCount,
                    "nextReconnectEpochMs" to next.nextReconnectEpochMs,
                    "sessionState" to next.sessionState.name,
                ) + failureMetadata,
            )
        }
    }

    private fun recordBleTelemetry(
        attemptId: Long,
        telemetry: CgmBleTelemetry,
    ) {
        when (telemetry) {
            is CgmDiscoveryCandidateRejected ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.RECOVERY,
                    CollectorDiagnosticResult.RECOVERABLE_ERROR,
                    "G7-Signal und GATT-Verbindung vorhanden, aber Authentifizierungsnachweis fehlgeschlagen · nächster Kandidat",
                    errorCode = telemetry.errorCode,
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            is CgmDiscoveryCandidateDeferred -> {
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.RECOVERY,
                    CollectorDiagnosticResult.RECOVERABLE_ERROR,
                    "Nicht authentifizierter G7-Kandidat vorübergehend zurückgestellt · weitere Kandidaten prüfen",
                    errorCode = telemetry.errorCode,
                    durationMs = CgmDiscoveryCandidates.CANDIDATE_COOLDOWN_MS,
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            }
            is CgmDirectConnectStarted -> {
                attemptStore.updateCycle(attemptId) { cycle ->
                    cycle.copy(
                        connectGattStartedAt = cycle.connectGattStartedAt ?: telemetry.timestampEpochMs,
                        directConnectStartedElapsedRealtimeMs = telemetry.elapsedRealtimeMs,
                        processUptimeAtDirectConnectMs = (telemetry.elapsedRealtimeMs - processStartedElapsedMs).coerceAtLeast(0L),
                        directConnectAttempts = cycle.directConnectAttempts + 1,
                        gattGeneration = telemetry.gattGeneration,
                    )
                }
                val windowId = attemptStore.activeAttempt(attemptId)?.cycle?.expectedWindowId
                CgmExpectedWindowLedger(this).markGattStarted(windowId, telemetry.timestampEpochMs, telemetry.gattGeneration)
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.CONNECT_REQUEST,
                    CollectorDiagnosticResult.STARTED,
                    "GATT_GENERATION_CREATED · generation=${telemetry.gattGeneration}",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            }
            is CgmDirectConnectCompleted -> {
                attemptStore.updateCycle(attemptId) { cycle ->
                    cycle.copy(
                        directConnectCallbackAt = telemetry.timestampEpochMs,
                        directConnectResult = telemetry.result,
                        directConnectStatus = telemetry.status,
                        directConnectNewState = telemetry.newState,
                    )
                }
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.CONNECT_REQUEST,
                    if (telemetry.result ==
                        app.sugarlicious.sensor.protocol.DirectConnectResult.SUCCESS
                    ) {
                        CollectorDiagnosticResult.SUCCESS
                    } else {
                        CollectorDiagnosticResult.RECOVERABLE_ERROR
                    },
                    when (telemetry.result) {
                        app.sugarlicious.sensor.protocol.DirectConnectResult.STATUS_133 -> "GATT_133_RECEIVED · callback=${telemetry.durationMs}ms · generation=${telemetry.gattGeneration}"
                        app.sugarlicious.sensor.protocol.DirectConnectResult.NO_CALLBACK -> "GATT_NO_CALLBACK_TIMEOUT · timeout=${telemetry.durationMs}ms · generation=${telemetry.gattGeneration}"
                        else -> "Direct Connect ${telemetry.result.name} · callback=${telemetry.durationMs}ms · status=${telemetry.status ?: "—"} · state=${telemetry.newState ?: "—"}"
                    },
                    errorCode = directConnectDiagnosticCode(telemetry.result),
                    durationMs = telemetry.durationMs,
                    nowEpochMs = telemetry.timestampEpochMs,
                )
                CgmExpectedWindowLedger(this).markGattResult(
                    attemptStore.activeAttempt(attemptId)?.cycle?.expectedWindowId,
                    telemetry.result,
                )
            }
            is CgmScanTelemetry -> {
                attemptStore.updateCycle(attemptId) { cycle ->
                    cycle.copy(
                        fallbackScanUsed = cycle.fallbackScanUsed || cycle.directConnectAttempts > 0,
                        scanStartedAt = cycle.scanStartedAt ?: telemetry.startedAtEpochMs,
                        scanEndedAt = telemetry.endedAtEpochMs,
                        scanMode = telemetry.scanMode,
                        scanCallbackType = telemetry.callbackType,
                        scanTotalResults = telemetry.totalResults,
                        scanConnectableResults = telemetry.connectableResults,
                        scanNamedCgmResults = telemetry.namedCgmResults,
                        scanExactAddressResults = telemetry.exactAddressResults,
                        scanDuplicateResults = telemetry.duplicateResults,
                        scanMinRssi = telemetry.minRssi,
                        scanMaxRssi = telemetry.maxRssi,
                        bluetoothAdapterState = telemetry.adapterState,
                    )
                }
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.SCANNING,
                    CollectorDiagnosticResult.INFO,
                    "Scan beendet · total=${telemetry.totalResults} · connectable=${telemetry.connectableResults} · manufacturer=${telemetry.manufacturerDataResults} · g7=${telemetry.namedCgmResults} · known=${telemetry.exactAddressResults} · rejectedNonConnectable=${telemetry.rejectedNotConnectable} · rejectedIdentity=${telemetry.rejectedByIdentity} · duplicates=${telemetry.duplicateResults} · rssi=${telemetry.minRssi ?: "—"}..${telemetry.maxRssi ?: "—"}",
                    durationMs = telemetry.durationMs,
                    nowEpochMs = telemetry.endedAtEpochMs,
                )
                CgmExpectedWindowLedger(this).markScan(
                    attemptStore.activeAttempt(attemptId)?.cycle?.expectedWindowId,
                    telemetry.startedAtEpochMs,
                    telemetry.endedAtEpochMs,
                    telemetry.totalResults,
                    telemetry.endedAtEpochMs.takeIf { telemetry.namedCgmResults > 0 || telemetry.exactAddressResults > 0 },
                )
            }
            is CgmGattCleanupTelemetry ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.GATT_CLOSE,
                    CollectorDiagnosticResult.INFO,
                    "${if (telemetry.started) "GATT_CLEANUP_START" else "GATT_CLOSED"} · generation=${telemetry.gattGeneration}",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            is CgmStaleGattCallbackTelemetry ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.GATT_CLOSE,
                    CollectorDiagnosticResult.INFO,
                    "STALE_GATT_CALLBACK · generation=${telemetry.gattGeneration} · callback=${telemetry.callback}",
                    errorCode = "STALE_GATT_CALLBACK",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            is CgmBackfillRequestTelemetry ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.SYNC,
                    CollectorDiagnosticResult.STARTED,
                    "BACKFILL_REQUEST_STARTED · ${telemetry.startSensorClock}-${telemetry.endSensorClock} · generation=${telemetry.gattGeneration}",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
        }
    }

    private fun startForegroundCollector() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundForNewReading() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    internal fun notification(): Notification {
        val openIntent = cgmOpenAppIntent(this)
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_cgm_notification)
            .setColor(0xFF6DE892.toInt())
            .setContentTitle("Foreground Channel")
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun scheduleReconnect(state: CgmPersistedState): Long? {
        val cycle = CgmReconnectAlarmScheduler.scheduleForState(this, state) ?: return null
        scope.launch {
            applicationContext.recordCgmDiagnostic(
                if (cycle.alarmKind == app.sugarlicious.sensor.protocol.CollectorAlarmKind.EXACT) "G7-SCHED-200" else "G7-SCHED-201",
                if (cycle.alarmKind == app.sugarlicious.sensor.protocol.CollectorAlarmKind.EXACT) {
                    "Exact G7 reconnect scheduled"
                } else {
                    "DEGRADED BACKGROUND RELIABILITY · exact alarm unavailable; inexact fallback scheduled"
                },
                if (cycle.alarmKind == app.sugarlicious.sensor.protocol.CollectorAlarmKind.EXACT) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
                mapOf(
                    "expectedReadingEpoch" to cycle.expectedReadingEpoch,
                    "requestedReconnectEpoch" to cycle.requestedReconnectEpoch,
                    "alarmKind" to cycle.alarmKind.name,
                    "canScheduleExactAlarms" to cycle.canScheduleExactAlarms,
                    "batteryUnrestricted" to cycle.batteryUnrestricted,
                    "deviceIdleMode" to cycle.deviceIdleMode,
                    "isInteractive" to cycle.isInteractive,
                    "charging" to cycle.charging,
                ),
            )
        }
        return cycle.requestedReconnectEpoch
    }

    private fun acquireCycleWakeLock(request: CycleRequest): Long {
        if (cycleWakeLock?.isHeld == true) return System.currentTimeMillis()
        val initialPairing =
            store
                .read()
                .sensor
                ?.deviceAddress
                .isNullOrBlank()
        val timeout =
            if (initialPairing &&
                request == CycleRequest.AUTOMATIC
            ) {
                INITIAL_PAIRING_WAKE_LOCK_TIMEOUT_MS
            } else {
                NORMAL_CYCLE_WAKE_LOCK_TIMEOUT_MS
            }
        cycleWakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:G7Collection")
                .apply {
                    setReferenceCounted(false)
                    acquire(timeout)
                }
        return System.currentTimeMillis()
    }

    private fun releaseCycleWakeLock() {
        cycleWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) wakeLock.release()
        }
        cycleWakeLock = null
    }

    private fun finishCycle(token: Long) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            releaseCycleWakeLock()
            CgmWakeHandoff.release()
            if (token != cycleToken) return@synchronized
            collectionJob = null
            var current = store.read()
            val now = System.currentTimeMillis()
            val pendingReconnect = attemptStore.pendingScheduledCycle()?.requestedReconnectEpoch
            if (needsCgmFollowUpRepair(current.collectorEnabled, pendingReconnect, now)) {
                val repaired = CgmReconnectAlarmScheduler.scheduleRecovery(this, current, now)
                repaired?.requestedReconnectEpoch?.let { reconnectAt ->
                    current = current.copy(nextReconnectEpochMs = reconnectAt)
                    store.saveFromCollector(current)
                }
            }
            if (shouldKeepCgmRuntimeForeground(current.collectorEnabled)) {
                // The ongoing notification is only a foreground-service anchor. Runtime phase and
                // diagnostics belong in Systemstatus and must not churn the notification surface.
            } else {
                stopRuntimeForeground()
            }
            if (runtimeRecycleRequested) {
                runtimeRecycleRequested = false
                stopRuntimeForeground()
            }
        }

    private fun stopRuntimeForeground() {
        releaseCycleWakeLock()
        CgmWakeHandoff.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        vigilClockUpdates.stop()
        serviceRunning = false
        CgmCollectorRuntimeRegistry.unregister(this)
        releaseCycleWakeLock()
        CgmWakeHandoff.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        private var serviceRunning = false

        internal fun isServiceRunning(): Boolean = serviceRunning

        const val ACTION_START = "app.aapswear.g7watch.START"
        const val ACTION_STOP = "app.aapswear.g7watch.STOP"
        const val ACTION_RESTART = "app.aapswear.g7watch.RESTART"
        const val ACTION_MANUAL_SCAN = "app.aapswear.g7watch.MANUAL_SCAN"
        const val ACTION_RECONNECT = "app.aapswear.g7watch.RECONNECT"
        internal const val CHANNEL = "g7_collector"
        internal const val NOTIFICATION_ID = 7001
        private const val NORMAL_CYCLE_WAKE_LOCK_TIMEOUT_MS = 3L * 60_000L
        private const val INITIAL_PAIRING_WAKE_LOCK_TIMEOUT_MS = 31L * 60_000L
        private val SOFT_WINDOW_ERRORS =
            setOf(
                CGM_GATT_133_ERROR_CODE,
                CGM_DIRECT_CONNECT_TIMEOUT_ERROR_CODE,
                "G7-BLE-107",
                "G7-BLE-111",
                "G7-BLE-FALLBACK-107",
            )

        fun start(context: Context) =
            synchronized(CgmSensorStateStore.lifecycleLock) {
                val app = context.applicationContext
                if (ProductMigrationLifecycle
                        .isQuiesced(app) ||
                    ProductMigrationLifecycle
                        .isRetired(app)
                ) {
                    return@synchronized
                }
                val stateStore = CgmSensorStateStore(app)
                val current = stateStore.read()
                if (current.sensor == null || CgmCredentialStore(app).read() == null) return@synchronized
                if (!current.collectorEnabled) {
                    val started = CgmSessionManager(current).startCollector()
                    val now = System.currentTimeMillis()
                    stateStore.save(startCgmPairingAttempt(started, now))
                }
                CgmSignalLossMonitor.scheduleFromState(app, stateStore.read())
                app.startForegroundService(
                    Intent(app, CgmCollectorService::class.java).setAction(ACTION_START),
                )
                CgmReadingDatabase(app).use { it.notifyCollectorStateChanged() }
            }

        fun startScheduledReconnect(context: Context) {
            val app = context.applicationContext
            val current = CgmSensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || CgmCredentialStore(app).read() == null) {
                CgmWakeHandoff.release()
                return
            }
            app.startForegroundService(
                Intent(app, CgmCollectorService::class.java).setAction(ACTION_RECONNECT),
            )
        }

        internal fun resumeEnabled(context: Context) =
            synchronized(CgmSensorStateStore.lifecycleLock) {
                if (!CgmSensorStateStore(context).read().collectorEnabled) return@synchronized
                start(context)
            }

        fun stop(context: Context) =
            synchronized(CgmSensorStateStore.lifecycleLock) {
                val app = context.applicationContext
                val stateStore = CgmSensorStateStore(app)
                stateStore.save(CgmSessionManager(stateStore.read()).stop())
                CgmCollectorRuntimeRegistry.cancelLiveCycle()
                cancelScheduledReconnect(app)
                CgmSignalLossMonitor.cancel(app)
                CgmErrorNotifier.clearActive(app)
                CgmAlarmCoordinator.clearSuppressed(app)
                CgmWakeHandoff.release()
                app.stopService(Intent(app, CgmCollectorService::class.java))
                app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                CgmReadingDatabase(app).use { it.notifyCollectorStateChanged() }
            }

        fun restart(context: Context) {
            val app = context.applicationContext
            val current = CgmSensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || CgmCredentialStore(app).read() == null) return
            app.startForegroundService(Intent(app, CgmCollectorService::class.java).setAction(ACTION_RESTART))
        }

        fun scanNow(context: Context) {
            val app = context.applicationContext
            val current = CgmSensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || CgmCredentialStore(app).read() == null) return
            app.startForegroundService(Intent(app, CgmCollectorService::class.java).setAction(ACTION_MANUAL_SCAN))
        }

        private fun cancelScheduledReconnect(context: Context) {
            CgmReconnectAlarmScheduler.cancel(context)
        }
    }
}

private enum class CycleRequest { AUTOMATIC, MANUAL, RESTART }

private fun CgmCollectorService.updateAttemptCycleForProtocolState(
    attemptId: Long,
    state: CgmProtocolState,
    nowEpochMs: Long,
) {
    CgmCollectorDiagnosticStore(this).updateCycle(attemptId) { cycle ->
        when (state) {
            CgmProtocolState.SCANNING -> cycle.copy(scanStartedAt = cycle.scanStartedAt ?: nowEpochMs)
            CgmProtocolState.SENSOR_FOUND -> cycle.copy(advertisementFoundAt = cycle.advertisementFoundAt ?: nowEpochMs)
            CgmProtocolState.CONNECTING -> cycle.copy(connectGattStartedAt = cycle.connectGattStartedAt ?: nowEpochMs)
            CgmProtocolState.DISCOVERING_SERVICES ->
                cycle.copy(
                    gattConnectedAt = cycle.gattConnectedAt ?: nowEpochMs,
                    serviceDiscoveryAt = cycle.serviceDiscoveryAt ?: nowEpochMs,
                )
            CgmProtocolState.AUTHENTICATION_START -> cycle.copy(authStartedAt = cycle.authStartedAt ?: nowEpochMs)
            CgmProtocolState.AUTHENTICATED -> cycle.copy(authSucceededAt = cycle.authSucceededAt ?: nowEpochMs)
            CgmProtocolState.RECEIVING_GLUCOSE -> cycle.copy(glucosePacketReceivedAt = cycle.glucosePacketReceivedAt ?: nowEpochMs)
            else -> cycle
        }
    }
}

private fun CgmCollectorService.recordAttemptProtocolState(
    attemptId: Long,
    state: CgmProtocolState,
    sensorId: String,
) {
    val store = CgmCollectorDiagnosticStore(this)

    fun record(
        stage: CollectorDiagnosticStage,
        result: CollectorDiagnosticResult,
        message: String,
    ) = store.record(attemptId, stage, result, message, sensorId = sensorId)

    when (state) {
        CgmProtocolState.SCANNING -> {
            record(CollectorDiagnosticStage.SCAN_START, CollectorDiagnosticResult.STARTED, "Scan gestartet")
            record(CollectorDiagnosticStage.SCANNING, CollectorDiagnosticResult.INFO, "Suche nach Sensor")
        }
        CgmProtocolState.SENSOR_FOUND ->
            record(
                CollectorDiagnosticStage.ADVERTISEMENT_FOUND,
                CollectorDiagnosticResult.SUCCESS,
                "G7 Advertisement erkannt · connectable=true",
            )
        CgmProtocolState.CONNECTING ->
            record(CollectorDiagnosticStage.CONNECT_REQUEST, CollectorDiagnosticResult.STARTED, "GATT-Verbindung angefordert")
        CgmProtocolState.DISCOVERING_SERVICES -> {
            record(CollectorDiagnosticStage.GATT_CONNECTED, CollectorDiagnosticResult.SUCCESS, "GATT verbunden")
            record(CollectorDiagnosticStage.SERVICE_DISCOVERY, CollectorDiagnosticResult.STARTED, "Service Discovery")
        }
        CgmProtocolState.ENABLING_NOTIFICATIONS ->
            record(CollectorDiagnosticStage.SERVICE_READY, CollectorDiagnosticResult.SUCCESS, "Dexcom Service gefunden")
        CgmProtocolState.AUTHENTICATION_START ->
            record(CollectorDiagnosticStage.AUTH_START, CollectorDiagnosticResult.STARTED, "Authentifizierung gestartet")
        CgmProtocolState.AUTHENTICATING -> {
            record(CollectorDiagnosticStage.AUTH_CHALLENGE, CollectorDiagnosticResult.INFO, "Authentifizierungs-Challenge empfangen")
            record(CollectorDiagnosticStage.AUTH_RESPONSE, CollectorDiagnosticResult.INFO, "Authentifizierungs-Response gesendet")
        }
        CgmProtocolState.AUTHENTICATED ->
            record(CollectorDiagnosticStage.AUTH_SUCCESS, CollectorDiagnosticResult.SUCCESS, "Authentifizierung erfolgreich")
        CgmProtocolState.REQUESTING_GLUCOSE ->
            record(CollectorDiagnosticStage.GLUCOSE_REQUEST, CollectorDiagnosticResult.STARTED, "Glukose Request")
        CgmProtocolState.RECEIVING_GLUCOSE ->
            record(CollectorDiagnosticStage.GLUCOSE_RECEIVED, CollectorDiagnosticResult.SUCCESS, "Glukose Packet empfangen")
        CgmProtocolState.RECOVERING -> {
            record(CollectorDiagnosticStage.RETRY, CollectorDiagnosticResult.RECOVERABLE_ERROR, "Begrenzter Retry")
            record(CollectorDiagnosticStage.RECOVERY, CollectorDiagnosticResult.INFO, "Recovery aktiv")
        }
        else -> Unit
    }
}
