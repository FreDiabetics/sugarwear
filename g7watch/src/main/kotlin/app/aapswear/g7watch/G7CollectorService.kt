package app.aapswear.g7watch

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
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorDiagnosticResult
import app.aapswear.g7.CollectorDiagnosticStage
import app.aapswear.g7.CollectorSlotStrategy
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7ReconnectScheduler
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SessionState
import app.aapswear.g7.toCgm
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.uishared.ProductMigrationLifecycle
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

class G7CollectorService : Service() {
    private val processStartedElapsedMs by lazy { SystemClock.elapsedRealtime() }
    private lateinit var store: G7SensorStateStore
    private lateinit var credentials: G7CredentialStore
    private lateinit var attemptStore: G7CollectorDiagnosticStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private var collectionJob: Job? = null
    private var cycleWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var cycleToken: Long = 0L
    private var runtimeRecycleRequested = false
    private val vigilClockUpdates by lazy { G7VigilClockUpdates(this) }

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
        store = G7SensorStateStore(this)
        credentials = G7CredentialStore(this)
        attemptStore = G7CollectorDiagnosticStore(this)
        synchronized(G7SensorStateStore.lifecycleLock) {
            store.saveFromCollector(G7CollectorReliability.rehydrate(store.read(), System.currentTimeMillis()))
        }
        G7CollectorRuntimeRegistry.register(
            owner = this,
            activeCheck = { collectionJob?.isActive == true },
            cancelAction = {
                ++cycleToken
                collectionJob?.cancel()
            },
        )
        G7ErrorNotifier.ensureChannel(this)
        G7CgmAlarmCoordinator.restore(this)
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
            synchronized(G7SensorStateStore.lifecycleLock) {
                enforceG7CollectorEligibility(this, serviceStartAt, credentials.read() != null)
            }
        if (!persisted.collectorEnabled) {
            G7SignalLossMonitor.cancel(this)
            G7ErrorNotifier.clearActive(this)
            G7WakeHandoff.release()
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
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!store.read().collectorEnabled) {
            G7WakeHandoff.release()
            return@synchronized
        }
        G7RuntimeReconciler.reconcile(
            context = this,
            entryPoint = G7RuntimeEntryPoint.SERVICE_START,
            liveCycle = collectionJob?.isActive == true,
            // ACTION_RECONNECT owns a receiver-delivered envelope that is consumed immediately
            // below. Repairing before consumption would replace slot N with N+1, after which the
            // safety scheduler would incorrectly arm N+2 for the duration of this BLE attempt.
            allowRepair = shouldRepairG7RuntimeOnServiceStart(action),
            cancelLiveCycle = { collectionJob?.cancel() },
            nowEpochMs = serviceStartAt,
        )
        var persisted = store.read()
        if (!persisted.collectorEnabled) {
            G7WakeHandoff.release()
            return@synchronized
        }

        G7SignalLossMonitor.scheduleFromState(this, persisted)
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
                applicationContext.recordG7Diagnostic(
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
                g7StartupSensorWindow(persisted, attemptStore.pendingScheduledCycle(), serviceStartAt)
            } else {
                null
            }
        if (startupWindow != null) {
            val restored = G7ReconnectAlarmScheduler.rearmScheduledCycle(this, startupWindow, persisted)
            restored.requestedReconnectEpoch?.let { reconnectAt ->
                store.saveFromCollector(
                    clearG7ScanRuntime(store.read()).copy(
                        nextReconnectEpochMs = reconnectAt,
                        protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                        connectionState = G7ConnectionState.DISCONNECTED,
                        activeAttemptId = null,
                    ),
                )
            }
            G7WakeHandoff.release()
            return@synchronized
        }

        // Stage the next automatic slot before any scan/GATT work starts. The prior implementation
        // consumed the current alarm first and scheduled the next one only after cycle completion;
        // a process death mid-scan therefore left the collector with no future wake-up at all.
        if (request == CycleRequest.AUTOMATIC) {
            val safetyCycle =
                G7ReconnectAlarmScheduler.scheduleSafetyForCycle(
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
                G7RuntimeReconciler
                    .reconcile(
                        context = this,
                        entryPoint = G7RuntimeEntryPoint.SERVICE_START,
                        liveCycle = true,
                        allowRepair = true,
                        cancelLiveCycle = { collectionJob?.cancel() },
                        nowEpochMs = serviceStartAt,
                    ).detected.cleanupRequired
            } else {
                false
            }

        if (!staleCycleReplaced && shouldCoalesceG7CollectorTrigger(request == CycleRequest.AUTOMATIC, collectionJob?.isActive == true)) {
            scope.launch {
                applicationContext.recordG7Diagnostic(
                    "RECOVERY_TRIGGER_COALESCED",
                    "Duplicate automatic trigger ignored while the canonical collector cycle is active",
                    DiagnosticSeverity.INFO,
                    mapOf("expectedReadingEpoch" to scheduledCycle?.expectedReadingEpoch),
                )
            }
            G7WakeHandoff.release()
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
            val recovery = G7ReconnectAlarmScheduler.scheduleRecovery(this, store.read(), serviceStartAt)
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
                G7WakeHandoff.release()
                val cycle =
                    scheduledCycle?.copy(
                        serviceOnStartCommandAt = serviceStartAt,
                        wakeLockAcquiredAt = wakeAt,
                    )
                applicationContext.recordG7Diagnostic(
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

        fun persist(state: G7PersistedState): Boolean =
            synchronized(G7SensorStateStore.lifecycleLock) {
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
        G7ExpectedWindowLedger(
            this,
        ).markCycleStarted(scheduledCycle?.expectedWindowId, startedAt, attemptId, scheduledCycle?.wakeLockAcquiredAt)
        val persisted =
            store.read().let { state ->
                if (request == CycleRequest.RESTART) resetG7RuntimeForRestart(state) else state
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
                G7CollectorError(
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
                G7CollectorError("G7-SETUP-002", false, System.currentTimeMillis(), "Sensorcode fehlt oder ist nicht mehr lesbar"),
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
        val collectionSensor = restoreAuthenticatedG7Address(configuredSensor, storedCredentials.sharedKeyAddress)
        if (collectionSensor != configuredSensor) {
            persist(store.read().copy(sensor = collectionSensor))
        }

        try {
            val collector = AndroidG7Collector(this)
            persist(store.read().copy(health = G7CollectorReliability.collecting(store.read().health)))
            val sessionId = collectionSensor.sessionId ?: collectionSensor.sensorId
            val ledger = G7ExpectedWindowLedger(this)
            var recoveryGap: app.aapswear.g7.CollectorExpectedWindow?
            val lastStoredSensorClock =
                G7ReadingDatabase(this).let { database ->
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
                            applicationContext.recordG7Diagnostic(
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
                applicationContext.recordG7Diagnostic(
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
                    .takeIf { !hasEstablishedG7SensorSession(it) }
                    ?.pairingDeadlineEpochMs
                    ?.minus(System.currentTimeMillis())
            if (pairingRemainingMs != null && pairingRemainingMs <= 0L) {
                throw G7BleException("G7-PAIRING-TIMEOUT", "Verbindung zum Sensor fehlgeschlagen", false)
            }
            val boundedScanTimeout =
                when {
                    pairingRemainingMs != null -> pairingRemainingMs
                    request == CycleRequest.AUTOMATIC -> null
                    else -> G7_RECONNECT_SCAN_TIMEOUT_MS
                }
            var liveCommittedBeforeBackfill = false
            var backfillRequestedAt: Long? = null
            var liveMeasuredAtForRecovery: Long? = null
            val result =
                withinG7InitialPairingBudget(store.read(), System.currentTimeMillis()) {
                    val cycleContext = currentCoroutineContext()
                    collector.collect(
                        initialSensor = collectionSensor,
                        credentials = storedCredentials,
                        onState = { protocolState ->
                            cycleContext.ensureActive()
                            if (token != cycleToken || !store.read().collectorEnabled) throw CancellationException("Collector paused")
                            val current = store.read()
                            val now = System.currentTimeMillis()
                            requireG7InitialPairingBudget(current, now)
                            val health =
                                when (protocolState) {
                                    G7ProtocolState.SENSOR_FOUND -> G7CollectorReliability.reachable(current.health, now)
                                    G7ProtocolState.DISCOVERING, G7ProtocolState.DISCOVERING_SERVICES ->
                                        G7CollectorReliability.reachable(current.health, now, connected = true)
                                    G7ProtocolState.AUTHENTICATED -> G7CollectorReliability.authenticated(current.health, now)
                                    else -> current.health
                                }
                            val next =
                                current.copy(
                                    protocolState = protocolState,
                                    connectionState = protocolState.toConnectionState(),
                                    sessionState = protocolState.toSessionState(),
                                    authenticationState =
                                        if (protocolState == G7ProtocolState.AUTHENTICATED || protocolState == G7ProtocolState.REQUESTING_GLUCOSE) {
                                            app.aapswear.g7.G7AuthenticationState.AUTHENTICATED
                                        } else {
                                            current.authenticationState
                                        },
                                    pairingAttemptId = if (protocolState == G7ProtocolState.AUTHENTICATED || protocolState == G7ProtocolState.REQUESTING_GLUCOSE) null else current.pairingAttemptId,
                                    pairingStartedAtEpochMs = if (protocolState == G7ProtocolState.AUTHENTICATED || protocolState == G7ProtocolState.REQUESTING_GLUCOSE) null else current.pairingStartedAtEpochMs,
                                    pairingDeadlineEpochMs = if (protocolState == G7ProtocolState.AUTHENTICATED || protocolState == G7ProtocolState.REQUESTING_GLUCOSE) null else current.pairingDeadlineEpochMs,
                                    lastError = if (protocolState == G7ProtocolState.AUTHENTICATED || protocolState == G7ProtocolState.REQUESTING_GLUCOSE) null else current.lastError,
                                    scanStartedAtEpochMs =
                                        if (protocolState == G7ProtocolState.SCANNING) now else current.scanStartedAtEpochMs,
                                    scanTimeoutAtEpochMs =
                                        if (protocolState == G7ProtocolState.SCANNING) {
                                            current.pairingDeadlineEpochMs
                                                ?: now + (boundedScanTimeout ?: g7ScanTimeoutMs(collectionSensor))
                                        } else {
                                            current.scanTimeoutAtEpochMs
                                        },
                                    lastScanAtEpochMs =
                                        if (protocolState == G7ProtocolState.SCANNING) now else current.lastScanAtEpochMs,
                                    health = health,
                                )
                            persist(next)
                            updateAttemptCycleForProtocolState(attemptId, protocolState, now)
                            recordAttemptProtocolState(attemptId, protocolState, collectionSensor.sensorId)
                            scope.launch {
                                applicationContext.recordG7Diagnostic(
                                    protocolState.diagnosticCode(),
                                    protocolState.label(),
                                    metadata = mapOf("protocolState" to protocolState.name),
                                )
                            }
                        },
                        onSharedKey = { address, key ->
                            synchronized(G7SensorStateStore.lifecycleLock) {
                                cycleContext.ensureActive()
                                if (token != cycleToken || generation != store.collectorGeneration() || !store.read().collectorEnabled) {
                                    throw CancellationException("Collector paused")
                                }
                                requireG7InitialPairingBudget(store.read(), System.currentTimeMillis())
                                credentials.saveSharedKey(address, key)
                                val current = store.read()
                                if (current.sensor?.deviceAddress.isNullOrBlank()) {
                                    if (!persist(current.copy(sensor = current.sensor?.copy(deviceAddress = address)))) {
                                        throw CancellationException("Collector association changed")
                                    }
                                }
                                if (allowsRetiredG7CandidateForSetup(collectionSensor, storedCredentials.sharedKey != null, storedCredentials.sharedKeyAddress)) {
                                    G7RetiredSensors(this@G7CollectorService).reactivateAuthenticated(address)
                                }
                            }
                        },
                        scanTimeoutMsOverride = boundedScanTimeout,
                        reconnectStrategy =
                            effectiveG7ReconnectStrategy(
                                G7ReconnectStrategyStore.read(this),
                                store.read().health,
                            ),
                        onTelemetry = { telemetry ->
                            // Cleanup telemetry runs while the cycle is cancelling; never interrupt
                            // it before AndroidG7Ble has disconnected and closed the GATT client.
                            if (telemetry is G7DiscoveryCandidateDeferred) {
                                persist(
                                    store.read().copy(
                                        lastError =
                                            G7CollectorError(
                                                telemetry.errorCode,
                                                true,
                                                telemetry.timestampEpochMs,
                                                "G7-Kandidat nicht erreichbar · weitere Sensoren werden gesucht",
                                            ),
                                    ),
                                )
                            }
                            if (telemetry is G7BackfillRequestTelemetry) {
                                backfillRequestedAt = telemetry.timestampEpochMs
                                liveMeasuredAtForRecovery?.let { liveMeasuredAt ->
                                    G7ExpectedWindowLedger(this).markRecoveryRequestStarted(
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
                        allowFallbackScan = G7CollectorReliability.shouldRunPresenceScan(store.read().health),
                        onLiveReading = { live ->
                            cycleContext.ensureActive()
                            if (token != cycleToken || !store.read().collectorEnabled) throw CancellationException("Collector paused")
                            liveMeasuredAtForRecovery = live.sensorTimestampEpochMs
                            G7ReadingDatabase(this).let { database ->
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
                                                    sensor = sensorForG7LivePublication(collectionSensor, current.sensor, live.sensorState),
                                                    lastReading = provisional,
                                                    sessionState = app.aapswear.g7.G7SessionState.ACTIVE,
                                                    protocolState = G7ProtocolState.BACKFILL,
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
                G7ReadingDatabase(this).let { database ->
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
                    G7ReadingDatabase(this).let { database ->
                        try {
                            liveCommittedBeforeBackfill || database.insertOrIgnore(reading)
                        } finally {
                            database.close()
                        }
                    }
                } catch (error: Throwable) {
                    throw G7BleException("G7-STORE-500", "Lokaler G7-Wert konnte nicht gespeichert werden", true, error)
                }
            var backfillProcessedSuccessfully = true
            val (backfillInserted, committedBackfillMeasurements) =
                try {
                    G7ReadingDatabase(this).let { database ->
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
                    applicationContext.recordG7Diagnostic(
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
                G7ReadingDatabase(this).let { database ->
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
                G7CollectorBackfillProtocol.requestedStart(
                    lastStoredSensorClock,
                    result.reading.sensorClockSeconds ?: 0L,
                )
            val backfillEnd = G7CollectorBackfillProtocol.requestedEnd(result.reading.sensorClockSeconds ?: 0L)
            if (backfillStart != null && backfillEnd != null) {
                applicationContext.recordG7Diagnostic(
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
            G7ExpectedWindowLedger(this).markNextLiveAndBackfill(
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
            val ledgerUpdated = G7ExpectedWindowLedger(this).markReading(scheduledCycle?.expectedWindowId, storedAt)
            if (scheduledCycle?.expectedWindowId != null && !ledgerUpdated) {
                applicationContext.recordG7Diagnostic(
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
            val fresh = isFreshG7CycleReading(reading, storedAt)
            val manager = G7SessionManager(store.read().copy(sensor = documentedSensor))
            manager.authenticationSucceeded()
            val next =
                clearG7ScanRuntime(manager.readingReceived(reading, fresh = fresh, nowEpochMs = storedAt)).copy(
                    sensor = documentedSensor.copy(state = result.reading.sensorState),
                    connectionState = G7ConnectionState.DISCONNECTED,
                    protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                    lastSuccessfulConnectionEpochMs = storedAt,
                    activeAttemptId = null,
                    lastAttemptCompletedAtEpochMs = storedAt,
                    pairingAttemptId = null,
                    pairingStartedAtEpochMs = null,
                    pairingDeadlineEpochMs = null,
                    health = G7CollectorReliability.succeeded(store.read().health, reading.timestampEpochMs, storedAt),
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
                applicationContext.recordG7Diagnostic(
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

            synchronized(G7SensorStateStore.lifecycleLock) {
                if (token != cycleToken || generation != store.collectorGeneration() || !store.read().collectorEnabled) {
                    throw CancellationException("Collector lifecycle changed")
                }
                if (fresh) {
                    runCatching { G7ErrorNotifier.markRecovered(this) }
                    runCatching { G7CgmAlarmCoordinator.onReading(this, reading) }
                }
                if (shouldUpdateG7ForegroundNotification(inserted, classification)) {
                    updateForegroundForNewReading()
                }
                // For aged/invalid packets next.lastReading still points to the last fresh value, so
                // signal loss remains anchored to real current data rather than receive time.
                val scheduledReconnectAt = scheduleReconnect(next) ?: next.nextReconnectEpochMs
                G7SignalLossMonitor.scheduleFromState(this, next)
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
        } catch (error: G7BleException) {
            currentCoroutineContext().ensureActive()
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.GATT_CLOSE,
                CollectorDiagnosticResult.INFO,
                "BLE-/GATT-Ressourcen geschlossen",
            )
            fail(
                store.read(),
                G7CollectorError(error.errorCode, error.recoverable, System.currentTimeMillis(), error.message),
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
                G7CollectorError(
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
                G7CollectorError("G7-PERM-401", false, System.currentTimeMillis(), "Bluetooth-Berechtigung fehlt"),
                attemptId,
                startedAt,
            )
        } catch (error: Throwable) {
            fail(
                store.read(),
                G7CollectorError("G7-INT-500", true, System.currentTimeMillis(), "Unerwarteter Collector-Fehler"),
                attemptId,
                startedAt,
                unexpectedFailureMetadata("COLLECT", error),
            )
        } finally {
            finishCycle(token)
        }
    }

    private fun fail(
        state: G7PersistedState,
        error: G7CollectorError,
        attemptId: Long,
        startedAtEpochMs: Long,
        failureMetadata: Map<String, Any?> = emptyMap(),
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!store.read().collectorEnabled || store.read().activeAttemptId != attemptId) return@synchronized
        val cycle = attemptStore.snapshot().firstOrNull { it.attemptId == attemptId }?.cycle
        val bounded = ensureG7PairingAttempt(state, System.currentTimeMillis())
        if (!bounded.collectorEnabled) {
            attemptStore.setClassification(attemptId, classifyG7CycleFailure(error.code, cycle))
            attemptStore.record(
                attemptId,
                CollectorDiagnosticStage.ERROR,
                CollectorDiagnosticResult.FATAL_ERROR,
                bounded.lastError?.safeMessage ?: "Verbindung beendet",
                errorCode = bounded.lastError?.code,
                durationMs = System.currentTimeMillis() - startedAtEpochMs,
            )
            store.save(bounded)
            enforceG7CollectorEligibility(this, System.currentTimeMillis(), credentials.read() != null)
            return@synchronized
        }
        val stagedSafety =
            stagedSafetyCycle(
                cycle,
                attemptStore.pendingScheduledCycle(),
            )
        val managed = G7SessionManager(state).failure(error)
        val softWindowFailure = error.recoverable && error.code in SOFT_WINDOW_ERRORS
        val sensorAdvertisementSeen = (cycle?.scanNamedG7Results ?: 0) > 0 || (cycle?.scanExactAddressResults ?: 0) > 0
        val foreignAdvertisementsSeen = (cycle?.scanTotalResults ?: 0) > 0 && !sensorAdvertisementSeen
        val failureClass = g7FailureClass(error.code, sensorAdvertisementSeen, foreignAdvertisementsSeen, cycle?.directConnectResult)
        val reliability =
            G7CollectorReliability.failed(
                health = state.health,
                failure = failureClass,
                sensorAdvertisementSeen = sensorAdvertisementSeen,
                foreignAdvertisementsSeen = foreignAdvertisementsSeen,
                now = System.currentTimeMillis(),
            )
        val next =
            clearG7ScanRuntime(managed).copy(
                connectionState = G7ConnectionState.DISCONNECTED,
                protocolState = if (softWindowFailure) G7ProtocolState.RECOVERING else G7ProtocolState.ERROR,
                activeAttemptId = null,
                lastAttemptCompletedAtEpochMs = System.currentTimeMillis(),
                nextReconnectEpochMs =
                    stagedSafety?.expectedReadingEpoch?.minus(G7ReconnectScheduler.PRECONNECT_LEAD_MS)
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
        G7SignalLossMonitor.scheduleFromState(this, next)
        val classification =
            when {
                error.code.startsWith("G7-SETUP-") || error.code.startsWith("G7-PERM-") -> CollectorCycleClassification.SERVICE_START_FAILED
                error.code == "G7-STORE-500" -> CollectorCycleClassification.STORE_FAILED
                else -> classifyG7CycleFailure(error.code, cycle)
            }
        val ledgerUpdated = G7ExpectedWindowLedger(this).markFinal(cycle?.expectedWindowId, classification, recoveryRequired = true)
        if (cycle?.expectedWindowId != null && !ledgerUpdated) {
            scope.launch {
                applicationContext.recordG7Diagnostic(
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
                val staleScannerStopped = AndroidG7Scanner.forceCleanup()
                val callbackRuntimeReset = G7GattCallbackDispatcher.reset()
                val now = System.currentTimeMillis()
                val recycleStore = G7RuntimeRecycleStore(this)
                val recycleRuntime =
                    shouldRecycleG7Runtime(
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
                    store.saveFromCollector(resetG7RuntimeForRestart(store.read()))
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

        val alarmsEnabled = G7AlertPolicyStore.alarmsEnabled(this)
        if (shouldPostImmediateCollectorAlert(alarmsEnabled, error, next.sessionState)) {
            G7ErrorNotifier.show(this, error)
        }
        scope.launch {
            applicationContext.recordG7Diagnostic(
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
        telemetry: G7BleTelemetry,
    ) {
        when (telemetry) {
            is G7DiscoveryCandidateDeferred -> {
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.RECOVERY,
                    CollectorDiagnosticResult.RECOVERABLE_ERROR,
                    "Nicht authentifizierter G7-Kandidat vorübergehend zurückgestellt · weitere Kandidaten prüfen",
                    errorCode = telemetry.errorCode,
                    durationMs = G7DiscoveryCandidates.CANDIDATE_COOLDOWN_MS,
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            }
            is G7DirectConnectStarted -> {
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
                G7ExpectedWindowLedger(this).markGattStarted(windowId, telemetry.timestampEpochMs, telemetry.gattGeneration)
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.CONNECT_REQUEST,
                    CollectorDiagnosticResult.STARTED,
                    "GATT_GENERATION_CREATED · generation=${telemetry.gattGeneration}",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            }
            is G7DirectConnectCompleted -> {
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
                        app.aapswear.g7.DirectConnectResult.SUCCESS
                    ) {
                        CollectorDiagnosticResult.SUCCESS
                    } else {
                        CollectorDiagnosticResult.RECOVERABLE_ERROR
                    },
                    when (telemetry.result) {
                        app.aapswear.g7.DirectConnectResult.STATUS_133 -> "GATT_133_RECEIVED · callback=${telemetry.durationMs}ms · generation=${telemetry.gattGeneration}"
                        app.aapswear.g7.DirectConnectResult.NO_CALLBACK -> "GATT_NO_CALLBACK_TIMEOUT · timeout=${telemetry.durationMs}ms · generation=${telemetry.gattGeneration}"
                        else -> "Direct Connect ${telemetry.result.name} · callback=${telemetry.durationMs}ms · status=${telemetry.status ?: "—"} · state=${telemetry.newState ?: "—"}"
                    },
                    errorCode = directConnectDiagnosticCode(telemetry.result),
                    durationMs = telemetry.durationMs,
                    nowEpochMs = telemetry.timestampEpochMs,
                )
                G7ExpectedWindowLedger(this).markGattResult(
                    attemptStore.activeAttempt(attemptId)?.cycle?.expectedWindowId,
                    telemetry.result,
                )
            }
            is G7ScanTelemetry -> {
                attemptStore.updateCycle(attemptId) { cycle ->
                    cycle.copy(
                        fallbackScanUsed = cycle.fallbackScanUsed || cycle.directConnectAttempts > 0,
                        scanStartedAt = cycle.scanStartedAt ?: telemetry.startedAtEpochMs,
                        scanEndedAt = telemetry.endedAtEpochMs,
                        scanMode = telemetry.scanMode,
                        scanCallbackType = telemetry.callbackType,
                        scanTotalResults = telemetry.totalResults,
                        scanConnectableResults = telemetry.connectableResults,
                        scanNamedG7Results = telemetry.namedG7Results,
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
                    "Scan beendet · total=${telemetry.totalResults} · connectable=${telemetry.connectableResults} · manufacturer=${telemetry.manufacturerDataResults} · g7=${telemetry.namedG7Results} · known=${telemetry.exactAddressResults} · rejectedNonConnectable=${telemetry.rejectedNotConnectable} · rejectedIdentity=${telemetry.rejectedByIdentity} · duplicates=${telemetry.duplicateResults} · rssi=${telemetry.minRssi ?: "—"}..${telemetry.maxRssi ?: "—"}",
                    durationMs = telemetry.durationMs,
                    nowEpochMs = telemetry.endedAtEpochMs,
                )
                G7ExpectedWindowLedger(this).markScan(
                    attemptStore.activeAttempt(attemptId)?.cycle?.expectedWindowId,
                    telemetry.startedAtEpochMs,
                    telemetry.endedAtEpochMs,
                    telemetry.totalResults,
                    telemetry.endedAtEpochMs.takeIf { telemetry.namedG7Results > 0 || telemetry.exactAddressResults > 0 },
                )
            }
            is G7GattCleanupTelemetry ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.GATT_CLOSE,
                    CollectorDiagnosticResult.INFO,
                    "${if (telemetry.started) "GATT_CLEANUP_START" else "GATT_CLOSED"} · generation=${telemetry.gattGeneration}",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            is G7StaleGattCallbackTelemetry ->
                attemptStore.record(
                    attemptId,
                    CollectorDiagnosticStage.GATT_CLOSE,
                    CollectorDiagnosticResult.INFO,
                    "STALE_GATT_CALLBACK · generation=${telemetry.gattGeneration} · callback=${telemetry.callback}",
                    errorCode = "STALE_GATT_CALLBACK",
                    nowEpochMs = telemetry.timestampEpochMs,
                )
            is G7BackfillRequestTelemetry ->
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
        val openIntent = g7OpenAppIntent(this)
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_g7_notification)
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

    private fun scheduleReconnect(state: G7PersistedState): Long? {
        val cycle = G7ReconnectAlarmScheduler.scheduleForState(this, state) ?: return null
        scope.launch {
            applicationContext.recordG7Diagnostic(
                if (cycle.alarmKind == app.aapswear.g7.CollectorAlarmKind.EXACT) "G7-SCHED-200" else "G7-SCHED-201",
                if (cycle.alarmKind == app.aapswear.g7.CollectorAlarmKind.EXACT) {
                    "Exact G7 reconnect scheduled"
                } else {
                    "DEGRADED BACKGROUND RELIABILITY · exact alarm unavailable; inexact fallback scheduled"
                },
                if (cycle.alarmKind == app.aapswear.g7.CollectorAlarmKind.EXACT) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
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
        synchronized(G7SensorStateStore.lifecycleLock) {
            releaseCycleWakeLock()
            G7WakeHandoff.release()
            if (token != cycleToken) return@synchronized
            collectionJob = null
            var current = store.read()
            val now = System.currentTimeMillis()
            val pendingReconnect = attemptStore.pendingScheduledCycle()?.requestedReconnectEpoch
            if (needsG7FollowUpRepair(current.collectorEnabled, pendingReconnect, now)) {
                val repaired = G7ReconnectAlarmScheduler.scheduleRecovery(this, current, now)
                repaired?.requestedReconnectEpoch?.let { reconnectAt ->
                    current = current.copy(nextReconnectEpochMs = reconnectAt)
                    store.saveFromCollector(current)
                }
            }
            if (shouldKeepG7RuntimeForeground(current.collectorEnabled)) {
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
        G7WakeHandoff.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        vigilClockUpdates.stop()
        serviceRunning = false
        G7CollectorRuntimeRegistry.unregister(this)
        releaseCycleWakeLock()
        G7WakeHandoff.release()
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
                G7_GATT_133_ERROR_CODE,
                G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE,
                "G7-BLE-107",
                "G7-BLE-111",
                "G7-BLE-FALLBACK-107",
            )

        fun start(context: Context) =
            synchronized(G7SensorStateStore.lifecycleLock) {
                val app = context.applicationContext
                if (ProductMigrationLifecycle
                        .isQuiesced(app) ||
                    ProductMigrationLifecycle
                        .isRetired(app)
                ) {
                    return@synchronized
                }
                val stateStore = G7SensorStateStore(app)
                val current = stateStore.read()
                if (current.sensor == null || G7CredentialStore(app).read() == null) return@synchronized
                if (!current.collectorEnabled) {
                    val started = G7SessionManager(current).startCollector()
                    val now = System.currentTimeMillis()
                    stateStore.save(startG7PairingAttempt(started, now))
                }
                G7SignalLossMonitor.scheduleFromState(app, stateStore.read())
                app.startForegroundService(
                    Intent(app, G7CollectorService::class.java).setAction(ACTION_START),
                )
                G7ReadingDatabase(app).use { it.notifyCollectorStateChanged() }
            }

        fun startScheduledReconnect(context: Context) {
            val app = context.applicationContext
            val current = G7SensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || G7CredentialStore(app).read() == null) {
                G7WakeHandoff.release()
                return
            }
            app.startForegroundService(
                Intent(app, G7CollectorService::class.java).setAction(ACTION_RECONNECT),
            )
        }

        internal fun resumeEnabled(context: Context) =
            synchronized(G7SensorStateStore.lifecycleLock) {
                if (!G7SensorStateStore(context).read().collectorEnabled) return@synchronized
                start(context)
            }

        fun stop(context: Context) =
            synchronized(G7SensorStateStore.lifecycleLock) {
                val app = context.applicationContext
                val stateStore = G7SensorStateStore(app)
                stateStore.save(G7SessionManager(stateStore.read()).stop())
                G7CollectorRuntimeRegistry.cancelLiveCycle()
                cancelScheduledReconnect(app)
                G7SignalLossMonitor.cancel(app)
                G7ErrorNotifier.clearActive(app)
                G7CgmAlarmCoordinator.clearSuppressed(app)
                G7WakeHandoff.release()
                app.stopService(Intent(app, G7CollectorService::class.java))
                app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                G7ReadingDatabase(app).use { it.notifyCollectorStateChanged() }
            }

        fun restart(context: Context) {
            val app = context.applicationContext
            val current = G7SensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || G7CredentialStore(app).read() == null) return
            app.startForegroundService(Intent(app, G7CollectorService::class.java).setAction(ACTION_RESTART))
        }

        fun scanNow(context: Context) {
            val app = context.applicationContext
            val current = G7SensorStateStore(app).read()
            if (!current.collectorEnabled || current.sensor == null || G7CredentialStore(app).read() == null) return
            app.startForegroundService(Intent(app, G7CollectorService::class.java).setAction(ACTION_MANUAL_SCAN))
        }

        private fun cancelScheduledReconnect(context: Context) {
            G7ReconnectAlarmScheduler.cancel(context)
        }
    }
}

private enum class CycleRequest { AUTOMATIC, MANUAL, RESTART }

private fun G7CollectorService.updateAttemptCycleForProtocolState(
    attemptId: Long,
    state: G7ProtocolState,
    nowEpochMs: Long,
) {
    G7CollectorDiagnosticStore(this).updateCycle(attemptId) { cycle ->
        when (state) {
            G7ProtocolState.SCANNING -> cycle.copy(scanStartedAt = cycle.scanStartedAt ?: nowEpochMs)
            G7ProtocolState.SENSOR_FOUND -> cycle.copy(advertisementFoundAt = cycle.advertisementFoundAt ?: nowEpochMs)
            G7ProtocolState.CONNECTING -> cycle.copy(connectGattStartedAt = cycle.connectGattStartedAt ?: nowEpochMs)
            G7ProtocolState.DISCOVERING_SERVICES ->
                cycle.copy(
                    gattConnectedAt = cycle.gattConnectedAt ?: nowEpochMs,
                    serviceDiscoveryAt = cycle.serviceDiscoveryAt ?: nowEpochMs,
                )
            G7ProtocolState.AUTHENTICATION_START -> cycle.copy(authStartedAt = cycle.authStartedAt ?: nowEpochMs)
            G7ProtocolState.AUTHENTICATED -> cycle.copy(authSucceededAt = cycle.authSucceededAt ?: nowEpochMs)
            G7ProtocolState.RECEIVING_GLUCOSE -> cycle.copy(glucosePacketReceivedAt = cycle.glucosePacketReceivedAt ?: nowEpochMs)
            else -> cycle
        }
    }
}

private fun G7CollectorService.recordAttemptProtocolState(
    attemptId: Long,
    state: G7ProtocolState,
    sensorId: String,
) {
    val store = G7CollectorDiagnosticStore(this)

    fun record(
        stage: CollectorDiagnosticStage,
        result: CollectorDiagnosticResult,
        message: String,
    ) = store.record(attemptId, stage, result, message, sensorId = sensorId)

    when (state) {
        G7ProtocolState.SCANNING -> {
            record(CollectorDiagnosticStage.SCAN_START, CollectorDiagnosticResult.STARTED, "Scan gestartet")
            record(CollectorDiagnosticStage.SCANNING, CollectorDiagnosticResult.INFO, "Suche nach Sensor")
        }
        G7ProtocolState.SENSOR_FOUND ->
            record(
                CollectorDiagnosticStage.ADVERTISEMENT_FOUND,
                CollectorDiagnosticResult.SUCCESS,
                "G7 Advertisement erkannt · connectable=true",
            )
        G7ProtocolState.CONNECTING ->
            record(CollectorDiagnosticStage.CONNECT_REQUEST, CollectorDiagnosticResult.STARTED, "GATT-Verbindung angefordert")
        G7ProtocolState.DISCOVERING_SERVICES -> {
            record(CollectorDiagnosticStage.GATT_CONNECTED, CollectorDiagnosticResult.SUCCESS, "GATT verbunden")
            record(CollectorDiagnosticStage.SERVICE_DISCOVERY, CollectorDiagnosticResult.STARTED, "Service Discovery")
        }
        G7ProtocolState.ENABLING_NOTIFICATIONS ->
            record(CollectorDiagnosticStage.SERVICE_READY, CollectorDiagnosticResult.SUCCESS, "Dexcom Service gefunden")
        G7ProtocolState.AUTHENTICATION_START ->
            record(CollectorDiagnosticStage.AUTH_START, CollectorDiagnosticResult.STARTED, "Authentifizierung gestartet")
        G7ProtocolState.AUTHENTICATING -> {
            record(CollectorDiagnosticStage.AUTH_CHALLENGE, CollectorDiagnosticResult.INFO, "Authentifizierungs-Challenge empfangen")
            record(CollectorDiagnosticStage.AUTH_RESPONSE, CollectorDiagnosticResult.INFO, "Authentifizierungs-Response gesendet")
        }
        G7ProtocolState.AUTHENTICATED ->
            record(CollectorDiagnosticStage.AUTH_SUCCESS, CollectorDiagnosticResult.SUCCESS, "Authentifizierung erfolgreich")
        G7ProtocolState.REQUESTING_GLUCOSE ->
            record(CollectorDiagnosticStage.GLUCOSE_REQUEST, CollectorDiagnosticResult.STARTED, "Glukose Request")
        G7ProtocolState.RECEIVING_GLUCOSE ->
            record(CollectorDiagnosticStage.GLUCOSE_RECEIVED, CollectorDiagnosticResult.SUCCESS, "Glukose Packet empfangen")
        G7ProtocolState.RECOVERING -> {
            record(CollectorDiagnosticStage.RETRY, CollectorDiagnosticResult.RECOVERABLE_ERROR, "Begrenzter Retry")
            record(CollectorDiagnosticStage.RECOVERY, CollectorDiagnosticResult.INFO, "Recovery aktiv")
        }
        else -> Unit
    }
}
