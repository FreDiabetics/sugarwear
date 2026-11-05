package app.sugarwear

import android.content.Context
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmSessionManager
import app.sugarlicious.sensor.protocol.CgmSessionState
import app.sugarlicious.sensor.protocol.CollectorDiagnosticAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal enum class CgmRuntimeHealth {
    INACTIVE,
    HEALTHY_ACTIVE,
    HEALTHY_ARMED,
    STALE_ATTEMPT,
    MISSED_RECONNECT,
    ORPHANED_STATE,
    RECOVERY_REQUIRED,
    RECOVERY_INVARIANT_BROKEN,
}

internal enum class CgmRuntimeEntryPoint {
    SERVICE_CREATE,
    SERVICE_START,
    BOOT,
    PACKAGE_REPLACED,
    RECONNECT_RECEIVER,
    SIGNAL_LOSS,
    WATCH_APP,
    SYSTEM_STATUS,
    WATCHDOG,
}

internal data class CgmRuntimeAssessment(
    val health: CgmRuntimeHealth,
    val attemptAgeMs: Long? = null,
    val lastProgressAgeMs: Long? = null,
    val futureReconnectEpochMs: Long? = null,
    val cleanupRequired: Boolean = false,
)

internal data class CgmRuntimeReconcileResult(
    val detected: CgmRuntimeAssessment,
    val restoredHealth: CgmRuntimeHealth,
    val recoveryReconnectEpochMs: Long? = null,
)

/** Enforce the original explicit setup budget before any automatic repair can arm new work. */
internal fun enforceCgmCollectorEligibility(
    context: Context,
    nowEpochMs: Long,
    credentialsPresent: Boolean,
): CgmPersistedState =
    synchronized(CgmSensorStateStore.lifecycleLock) {
        val app = context.applicationContext
        val store = CgmSensorStateStore(app)
        val before = store.read()
        val bounded = ensureCgmPairingAttempt(before, nowEpochMs)
        val eligible =
            if (bounded.collectorEnabled && !credentialsPresent && !hasEstablishedCgmSensorSession(bounded)) {
                CgmSessionManager(bounded).stop().copy(
                    lastError = CgmCollectorError("G7-SETUP-001", false, nowEpochMs, "Sensor nicht eingerichtet · bitte Sensor verbinden wählen"),
                )
            } else {
                bounded
            }
        if (eligible != before) store.save(eligible)
        if (!eligible.collectorEnabled &&
            (before.collectorEnabled || CgmCollectorRuntimeRegistry.hasRuntimeService() || CgmCollectorDiagnosticStore(app).pendingScheduledCycle() != null)
        ) {
            // The established stop path cancels the live owner, scan, both alarms and wake handoff;
            // it retains credentials, history and the terminal setup error.
            CgmCollectorService.stop(app)
        }
        store.read()
    }

/** Process-local truth for whether the Service owns a real coroutine-backed BLE cycle. */
internal object CgmCollectorRuntimeRegistry {
    private var owner: Any? = null
    private var activeCheck: (() -> Boolean)? = null
    private var cancelAction: (() -> Unit)? = null

    @Synchronized
    fun register(
        owner: Any,
        activeCheck: () -> Boolean,
        cancelAction: () -> Unit,
    ) {
        this.owner = owner
        this.activeCheck = activeCheck
        this.cancelAction = cancelAction
    }

    @Synchronized
    fun unregister(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        activeCheck = null
        cancelAction = null
    }

    @Synchronized
    fun hasLiveCycle(): Boolean = activeCheck?.invoke() == true

    @Synchronized
    fun hasRuntimeService(): Boolean = owner != null

    @Synchronized
    fun cancelLiveCycle() {
        cancelAction?.invoke()
    }
}

private val persistedBusyConnectionStates =
    setOf(
        CgmConnectionState.SCANNING,
        CgmConnectionState.CONNECTING,
        CgmConnectionState.DISCOVERING,
        CgmConnectionState.CONNECTED,
    )

private val persistedBusyProtocolStates =
    setOf(
        CgmProtocolState.SCANNING,
        CgmProtocolState.SENSOR_FOUND,
        CgmProtocolState.CONNECTING,
        CgmProtocolState.DISCOVERING,
        CgmProtocolState.DISCOVERING_SERVICES,
        CgmProtocolState.ENABLING_NOTIFICATIONS,
        CgmProtocolState.AUTHENTICATION_START,
        CgmProtocolState.AUTHENTICATION_ROUND_1,
        CgmProtocolState.AUTHENTICATION_ROUND_2,
        CgmProtocolState.AUTHENTICATION_ROUND_3,
        CgmProtocolState.CHALLENGE,
        CgmProtocolState.CERTIFICATE_EXCHANGE,
        CgmProtocolState.KEY_EXCHANGE,
        CgmProtocolState.BONDING,
        CgmProtocolState.AUTHENTICATING,
        CgmProtocolState.AUTHENTICATED,
        CgmProtocolState.REQUESTING_GLUCOSE,
        CgmProtocolState.RECEIVING_GLUCOSE,
        CgmProtocolState.BACKFILL,
    )

private fun hasLiveCgmScanBudget(state: CgmPersistedState, attempt: CollectorDiagnosticAttempt?, liveCycle: Boolean, nowEpochMs: Long): Boolean {
    if (!liveCycle || attempt == null || state.activeAttemptId != attempt.attemptId) return false
    if (state.connectionState != CgmConnectionState.SCANNING || state.protocolState != CgmProtocolState.SCANNING || attempt.currentStage != app.sugarlicious.sensor.protocol.CollectorDiagnosticStage.SCANNING) return false
    val deadline = state.scanTimeoutAtEpochMs ?: return false
    val attemptDeadline = attempt.deadlineEpochMs ?: return false
    return nowEpochMs < deadline && nowEpochMs < attemptDeadline && deadline <= attempt.startedAtEpochMs + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS
}

internal fun assessCgmRuntime(
    state: CgmPersistedState,
    activeAttempt: CollectorDiagnosticAttempt?,
    pendingReconnectEpochMs: Long?,
    liveCycle: Boolean,
    sensorConfigured: Boolean,
    nowEpochMs: Long,
    reconnectToleranceMs: Long = CGM_RUNTIME_RECONNECT_TOLERANCE_MS,
    maxProgressInactivityMs: Long = CGM_RUNTIME_MAX_PROGRESS_INACTIVITY_MS,
): CgmRuntimeAssessment {
    if (!state.collectorEnabled || !sensorConfigured) return CgmRuntimeAssessment(CgmRuntimeHealth.INACTIVE)

    val attemptAge = activeAttempt?.let { (nowEpochMs - it.startedAtEpochMs).coerceAtLeast(0L) }
    val progressAge = activeAttempt?.let { (nowEpochMs - it.lastProgressAtEpochMs).coerceAtLeast(0L) }
    // Discovery may legitimately receive no callbacks until its bounded scan expires.
    // A retained SCANNING flag is insufficient: require the real live job, matching
    // attempt and explicit finite scan/attempt deadlines before relaxing inactivity.
    val waitingForAdvertisement = hasLiveCgmScanBudget(state, activeAttempt, liveCycle, nowEpochMs)
    val attemptStale =
        activeAttempt?.let {
            it.deadlineEpochMs?.let { deadline -> nowEpochMs >= deadline } == true ||
                progressAge?.let { age -> age >= maxProgressInactivityMs && !waitingForAdvertisement } == true
        } == true
    // nextReconnectEpochMs is only the desired time. A pending diagnostic envelope is written
    // after AlarmManager accepted the request and is the durable evidence that the wake path was
    // actually armed. Persisted future intent without that envelope must be repaired.
    // Tolerance applies to overdue delivery, not to a still-future armed alarm.
    // Discarding the final 30 seconds falsely broke the liveness invariant just
    // before the receiver was due and could rewrite a healthy active runtime.
    val futureReconnect = pendingReconnectEpochMs?.takeIf { it > nowEpochMs }
    val missedReconnect =
        listOfNotNull(pendingReconnectEpochMs, state.nextReconnectEpochMs)
            .any { it < nowEpochMs - reconnectToleranceMs }

    if (attemptStale) {
        return CgmRuntimeAssessment(
            health = CgmRuntimeHealth.STALE_ATTEMPT,
            attemptAgeMs = attemptAge,
            lastProgressAgeMs = progressAge,
            futureReconnectEpochMs = futureReconnect,
            cleanupRequired = true,
        )
    }

    if (liveCycle) {
        return if (futureReconnect != null) {
            CgmRuntimeAssessment(CgmRuntimeHealth.HEALTHY_ACTIVE, attemptAge, progressAge, futureReconnect)
        } else {
            CgmRuntimeAssessment(CgmRuntimeHealth.RECOVERY_INVARIANT_BROKEN, attemptAge, progressAge)
        }
    }

    val persistedClaimsActivity =
        state.activeAttemptId != null ||
            state.connectionState in persistedBusyConnectionStates ||
            state.protocolState in persistedBusyProtocolStates
    if (persistedClaimsActivity) {
        return CgmRuntimeAssessment(
            health = CgmRuntimeHealth.ORPHANED_STATE,
            attemptAgeMs = attemptAge,
            lastProgressAgeMs = progressAge,
            futureReconnectEpochMs = futureReconnect,
            cleanupRequired = true,
        )
    }
    if (futureReconnect != null) return CgmRuntimeAssessment(CgmRuntimeHealth.HEALTHY_ARMED, futureReconnectEpochMs = futureReconnect)
    if (missedReconnect) return CgmRuntimeAssessment(CgmRuntimeHealth.MISSED_RECONNECT)
    return CgmRuntimeAssessment(CgmRuntimeHealth.RECOVERY_REQUIRED)
}

/**
 * Reconciles durable state with the current process. Persisted CONNECTING/SCANNING state is never
 * accepted as proof that Android BLE objects survived process or service death.
 */
internal object CgmRuntimeReconciler {
    fun reconcile(
        context: Context,
        entryPoint: CgmRuntimeEntryPoint,
        liveCycle: Boolean? = null,
        allowRepair: Boolean = true,
        cancelLiveCycle: (() -> Unit)? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmRuntimeReconcileResult =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val app = context.applicationContext
            val stateStore = CgmSensorStateStore(app)
            val diagnosticStore = CgmCollectorDiagnosticStore(app)
            val configured = stateStore.read().sensor != null && CgmCredentialStore(app).read() != null
            val state = enforceCgmCollectorEligibility(app, nowEpochMs, configured)
            val activeAttempt = diagnosticStore.activeAttempt(state.activeAttemptId)
            val pendingCycle = diagnosticStore.pendingScheduledCycle()
            val pendingReconnect = pendingCycle?.requestedReconnectEpoch
            val effectiveLiveCycle = liveCycle ?: CgmCollectorRuntimeRegistry.hasLiveCycle()
            val assessment =
                assessCgmRuntime(
                    state = state,
                    activeAttempt = activeAttempt,
                    pendingReconnectEpochMs = pendingReconnect,
                    liveCycle = effectiveLiveCycle,
                    sensorConfigured = configured,
                    nowEpochMs = nowEpochMs,
                )
            recordRuntimeEvent(app, "RUNTIME_RECONCILE_START", entryPoint, assessment, state.nextReconnectEpochMs, null)
            if (
                state.collectorEnabled &&
                configured &&
                !CgmCollectorRuntimeRegistry.hasRuntimeService() &&
                entryPoint in
                setOf(
                    CgmRuntimeEntryPoint.BOOT,
                    CgmRuntimeEntryPoint.PACKAGE_REPLACED,
                    CgmRuntimeEntryPoint.SIGNAL_LOSS,
                    CgmRuntimeEntryPoint.WATCH_APP,
                )
            ) {
                recordRuntimeEvent(app, "FGS_MISSING", entryPoint, assessment, state.nextReconnectEpochMs, null)
            }
            if (assessment.health == CgmRuntimeHealth.STALE_ATTEMPT) {
                recordRuntimeEvent(app, "STALE_ATTEMPT_DETECTED", entryPoint, assessment, state.nextReconnectEpochMs, null)
            }

            val shouldReassertDurableAlarm =
                allowRepair &&
                    assessment.health == CgmRuntimeHealth.HEALTHY_ARMED &&
                    entryPoint in
                    setOf(
                        CgmRuntimeEntryPoint.BOOT,
                        CgmRuntimeEntryPoint.PACKAGE_REPLACED,
                        CgmRuntimeEntryPoint.SIGNAL_LOSS,
                    )
            if (shouldReassertDurableAlarm && pendingCycle != null) {
                val rearmed =
                    runCatching {
                        CgmReconnectAlarmScheduler.rearmScheduledCycle(app, pendingCycle, state)
                    }.getOrNull()
                val rearmedAt = rearmed?.requestedReconnectEpoch
                if (rearmedAt != null) stateStore.saveFromCollector(state.copy(nextReconnectEpochMs = rearmedAt))
                recordRuntimeEvent(
                    app,
                    if (rearmedAt != null) "RECOVERY_ALARM_ARMED" else "RECOVERY_ALARM_REARM_FAILED",
                    entryPoint,
                    assessment,
                    state.nextReconnectEpochMs,
                    rearmedAt,
                )
                return@synchronized CgmRuntimeReconcileResult(
                    assessment,
                    if (rearmedAt != null) CgmRuntimeHealth.HEALTHY_ARMED else CgmRuntimeHealth.RECOVERY_REQUIRED,
                    rearmedAt,
                )
            }

            if (!allowRepair ||
                assessment.health in setOf(CgmRuntimeHealth.INACTIVE, CgmRuntimeHealth.HEALTHY_ACTIVE, CgmRuntimeHealth.HEALTHY_ARMED)
            ) {
                val healthyEvent =
                    when (assessment.health) {
                        CgmRuntimeHealth.HEALTHY_ACTIVE -> "RUNTIME_HEALTHY_ACTIVE"
                        CgmRuntimeHealth.HEALTHY_ARMED -> "RUNTIME_HEALTHY_ARMED"
                        CgmRuntimeHealth.INACTIVE -> "RUNTIME_INACTIVE"
                        else -> "RUNTIME_RECONCILE_OBSERVED"
                    }
                recordRuntimeEvent(
                    app,
                    healthyEvent,
                    entryPoint,
                    assessment,
                    state.nextReconnectEpochMs,
                    assessment.futureReconnectEpochMs,
                )
                return@synchronized CgmRuntimeReconcileResult(assessment, assessment.health, assessment.futureReconnectEpochMs)
            }

            if (assessment.cleanupRequired) {
                (cancelLiveCycle ?: CgmCollectorRuntimeRegistry::cancelLiveCycle).invoke()
                SensorScanner.forceCleanup()
                CgmExpectedWindowLedger(app).markProcessInterrupted(
                    activeAttempt?.cycle?.expectedWindowId,
                    nowEpochMs,
                )
                diagnosticStore.expireStaleAttempts(nowEpochMs, 0L, interruptedByProcessRestart = !effectiveLiveCycle)
            }
            val sensor = state.sensor
            val reconstructedWindows =
                if (sensor != null && assessment.health != CgmRuntimeHealth.INACTIVE) {
                    val ledger = CgmExpectedWindowLedger(app)
                    val latestExpected =
                        ledger.latestExpectedAt(
                            sensorId = sensor.sensorId,
                            sessionId = sensor.sessionId ?: sensor.sensorId,
                        )
                    val firstMissing =
                        latestExpected?.plus(CGM_SLOT_INTERVAL_MS)
                            ?: state.lastReading?.timestampEpochMs?.plus(CGM_SLOT_INTERVAL_MS)
                    if (firstMissing != null && firstMissing < nowEpochMs - CGM_RUNTIME_RECONNECT_TOLERANCE_MS) {
                        ledger.reconstructMissed(
                            sensorId = sensor.sensorId,
                            sessionId = sensor.sessionId ?: sensor.sensorId,
                            fromExpectedAt = firstMissing,
                            untilExclusive = nowEpochMs - CGM_RUNTIME_RECONNECT_TOLERANCE_MS,
                            sensorStartAt = sensor.sensorStartEpochMs,
                            sensorEndAt = sensor.sensorEndEpochMs,
                            nowEpochMs = nowEpochMs,
                        )
                    } else {
                        0
                    }
                } else {
                    0
                }
            val cleaned =
                stateStore.read().copy(
                    connectionState = CgmConnectionState.DISCONNECTED,
                    protocolState = CgmProtocolState.RECOVERING,
                    sessionState = CgmSessionState.RECOVERING,
                    activeAttemptId = null,
                    scanStartedAtEpochMs = null,
                    scanTimeoutAtEpochMs = state.pairingDeadlineEpochMs,
                    lastError =
                        CgmCollectorError(
                            code = "G7-RUNTIME-276",
                            recoverable = true,
                            occurredAtEpochMs = nowEpochMs,
                            safeMessage = "Collector-Laufzeit wurde automatisch wiederhergestellt",
                        ),
                )
            stateStore.saveFromCollector(cleaned)

            val recovery =
                runCatching {
                    CgmReconnectAlarmScheduler.scheduleRecovery(app, cleaned, nowEpochMs)
                }.getOrNull()
            val recoveryAt = recovery?.requestedReconnectEpoch
            if (recoveryAt != null) {
                recordRuntimeEvent(app, "RECOVERY_SLOT_CALCULATED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            }
            val restored =
                if (recoveryAt != null && recoveryAt > nowEpochMs) {
                    stateStore.saveFromCollector(cleaned.copy(nextReconnectEpochMs = recoveryAt))
                    CgmRuntimeHealth.HEALTHY_ARMED
                } else {
                    CgmRuntimeHealth.RECOVERY_REQUIRED
                }
            val event =
                when (assessment.health) {
                    CgmRuntimeHealth.STALE_ATTEMPT -> "STALE_ATTEMPT_CLEANED"
                    CgmRuntimeHealth.ORPHANED_STATE -> "ORPHANED_CONNECTION_STATE"
                    CgmRuntimeHealth.MISSED_RECONNECT -> "MISSED_RECONNECT_DETECTED"
                    else -> if (restored == CgmRuntimeHealth.HEALTHY_ARMED) "RECOVERY_INVARIANT_RESTORED" else "RECOVERY_ALARM_REARM_FAILED"
                }
            recordRuntimeEvent(app, event, entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            if (reconstructedWindows > 0) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    app.recordCgmDiagnostic(
                        code = "MISSED_WINDOWS_RECONSTRUCTED",
                        message = "MISSED_WINDOWS_RECONSTRUCTED · $reconstructedWindows lifecycle gaps",
                        severity = DiagnosticSeverity.WARNING,
                        metadata =
                            mapOf(
                                "processInstanceId" to CgmProcessInstance.id,
                                "reconstructedWindowCount" to reconstructedWindows,
                            ),
                    )
                }
            }
            if (recoveryAt != null) {
                recordRuntimeEvent(app, "RECOVERY_ALARM_ARMED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
                recordRuntimeEvent(app, "RECOVERY_INVARIANT_RESTORED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            }
            return@synchronized CgmRuntimeReconcileResult(assessment, restored, recoveryAt)
        }

    private fun recordRuntimeEvent(
        context: Context,
        event: String,
        entryPoint: CgmRuntimeEntryPoint,
        assessment: CgmRuntimeAssessment,
        oldReconnectEpochMs: Long?,
        newReconnectEpochMs: Long?,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            context.recordCgmDiagnostic(
                code = event,
                message = "$event · ${entryPoint.name}",
                severity =
                    if (event.endsWith("FAILED") || assessment.health == CgmRuntimeHealth.RECOVERY_INVARIANT_BROKEN) {
                        DiagnosticSeverity.WARNING
                    } else {
                        DiagnosticSeverity.INFO
                    },
                metadata =
                    mapOf(
                        "processInstanceId" to CgmProcessInstance.id,
                        "pid" to android.os.Process.myPid(),
                        "processStartedAtEpochMs" to CgmProcessInstance.startedAtEpochMs,
                        "processUptimeMs" to android.os.SystemClock.elapsedRealtime(),
                        "appVersion" to
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull(),
                        "collectorEnabled" to CgmSensorStateStore(context).read().collectorEnabled,
                        "sensorId" to CgmSensorStateStore(context).read().sensor?.sensorId,
                        "sessionId" to CgmSensorStateStore(context).read().sensor?.sessionId,
                        "runtimeHealth" to assessment.health.name,
                        "attemptAgeMs" to assessment.attemptAgeMs,
                        "lastProgressAgeMs" to assessment.lastProgressAgeMs,
                        "oldReconnectEpochMs" to oldReconnectEpochMs,
                        "newReconnectEpochMs" to newReconnectEpochMs,
                    ),
            )
        }
    }
}

internal const val CGM_RUNTIME_RECONNECT_TOLERANCE_MS = 30_000L
internal const val CGM_RUNTIME_MAX_PROGRESS_INACTIVITY_MS = 2L * 60_000L
