package app.aapswear.g7watch

import android.content.Context
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SessionState
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal enum class G7RuntimeHealth {
    INACTIVE,
    HEALTHY_ACTIVE,
    HEALTHY_ARMED,
    STALE_ATTEMPT,
    MISSED_RECONNECT,
    ORPHANED_STATE,
    RECOVERY_REQUIRED,
    RECOVERY_INVARIANT_BROKEN,
}

internal enum class G7RuntimeEntryPoint {
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

internal data class G7RuntimeAssessment(
    val health: G7RuntimeHealth,
    val attemptAgeMs: Long? = null,
    val lastProgressAgeMs: Long? = null,
    val futureReconnectEpochMs: Long? = null,
    val cleanupRequired: Boolean = false,
)

internal data class G7RuntimeReconcileResult(
    val detected: G7RuntimeAssessment,
    val restoredHealth: G7RuntimeHealth,
    val recoveryReconnectEpochMs: Long? = null,
)

/** Enforce the original explicit setup budget before any automatic repair can arm new work. */
internal fun enforceG7CollectorEligibility(
    context: Context,
    nowEpochMs: Long,
    credentialsPresent: Boolean,
): G7PersistedState =
    synchronized(G7SensorStateStore.lifecycleLock) {
        val app = context.applicationContext
        val store = G7SensorStateStore(app)
        val before = store.read()
        val bounded = ensureG7PairingAttempt(before, nowEpochMs)
        val eligible =
            if (bounded.collectorEnabled && !credentialsPresent && !hasEstablishedG7SensorSession(bounded)) {
                G7SessionManager(bounded).stop().copy(
                    lastError = G7CollectorError("G7-SETUP-001", false, nowEpochMs, "Sensor nicht eingerichtet · bitte Sensor verbinden wählen"),
                )
            } else {
                bounded
            }
        if (eligible != before) store.save(eligible)
        if (!eligible.collectorEnabled &&
            (before.collectorEnabled || G7CollectorRuntimeRegistry.hasRuntimeService() || G7CollectorDiagnosticStore(app).pendingScheduledCycle() != null)
        ) {
            // The established stop path cancels the live owner, scan, both alarms and wake handoff;
            // it retains credentials, history and the terminal setup error.
            G7CollectorService.stop(app)
        }
        store.read()
    }

/** Process-local truth for whether the Service owns a real coroutine-backed BLE cycle. */
internal object G7CollectorRuntimeRegistry {
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
        G7ConnectionState.SCANNING,
        G7ConnectionState.CONNECTING,
        G7ConnectionState.DISCOVERING,
        G7ConnectionState.CONNECTED,
    )

private val persistedBusyProtocolStates =
    setOf(
        G7ProtocolState.SCANNING,
        G7ProtocolState.SENSOR_FOUND,
        G7ProtocolState.CONNECTING,
        G7ProtocolState.DISCOVERING,
        G7ProtocolState.DISCOVERING_SERVICES,
        G7ProtocolState.ENABLING_NOTIFICATIONS,
        G7ProtocolState.AUTHENTICATION_START,
        G7ProtocolState.AUTHENTICATION_ROUND_1,
        G7ProtocolState.AUTHENTICATION_ROUND_2,
        G7ProtocolState.AUTHENTICATION_ROUND_3,
        G7ProtocolState.CHALLENGE,
        G7ProtocolState.CERTIFICATE_EXCHANGE,
        G7ProtocolState.KEY_EXCHANGE,
        G7ProtocolState.BONDING,
        G7ProtocolState.AUTHENTICATING,
        G7ProtocolState.AUTHENTICATED,
        G7ProtocolState.REQUESTING_GLUCOSE,
        G7ProtocolState.RECEIVING_GLUCOSE,
        G7ProtocolState.BACKFILL,
    )

private fun hasLiveG7ScanBudget(state: G7PersistedState, attempt: CollectorDiagnosticAttempt?, liveCycle: Boolean, nowEpochMs: Long): Boolean {
    if (!liveCycle || attempt == null || state.activeAttemptId != attempt.attemptId) return false
    if (state.connectionState != G7ConnectionState.SCANNING || state.protocolState != G7ProtocolState.SCANNING || attempt.currentStage != app.aapswear.g7.CollectorDiagnosticStage.SCANNING) return false
    val deadline = state.scanTimeoutAtEpochMs ?: return false
    val attemptDeadline = attempt.deadlineEpochMs ?: return false
    return nowEpochMs < deadline && nowEpochMs < attemptDeadline && deadline <= attempt.startedAtEpochMs + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS
}

internal fun assessG7Runtime(
    state: G7PersistedState,
    activeAttempt: CollectorDiagnosticAttempt?,
    pendingReconnectEpochMs: Long?,
    liveCycle: Boolean,
    sensorConfigured: Boolean,
    nowEpochMs: Long,
    reconnectToleranceMs: Long = G7_RUNTIME_RECONNECT_TOLERANCE_MS,
    maxProgressInactivityMs: Long = G7_RUNTIME_MAX_PROGRESS_INACTIVITY_MS,
): G7RuntimeAssessment {
    if (!state.collectorEnabled || !sensorConfigured) return G7RuntimeAssessment(G7RuntimeHealth.INACTIVE)

    val attemptAge = activeAttempt?.let { (nowEpochMs - it.startedAtEpochMs).coerceAtLeast(0L) }
    val progressAge = activeAttempt?.let { (nowEpochMs - it.lastProgressAtEpochMs).coerceAtLeast(0L) }
    // Discovery may legitimately receive no callbacks until its bounded scan expires.
    // A retained SCANNING flag is insufficient: require the real live job, matching
    // attempt and explicit finite scan/attempt deadlines before relaxing inactivity.
    val waitingForAdvertisement = hasLiveG7ScanBudget(state, activeAttempt, liveCycle, nowEpochMs)
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
        return G7RuntimeAssessment(
            health = G7RuntimeHealth.STALE_ATTEMPT,
            attemptAgeMs = attemptAge,
            lastProgressAgeMs = progressAge,
            futureReconnectEpochMs = futureReconnect,
            cleanupRequired = true,
        )
    }

    if (liveCycle) {
        return if (futureReconnect != null) {
            G7RuntimeAssessment(G7RuntimeHealth.HEALTHY_ACTIVE, attemptAge, progressAge, futureReconnect)
        } else {
            G7RuntimeAssessment(G7RuntimeHealth.RECOVERY_INVARIANT_BROKEN, attemptAge, progressAge)
        }
    }

    val persistedClaimsActivity =
        state.activeAttemptId != null ||
            state.connectionState in persistedBusyConnectionStates ||
            state.protocolState in persistedBusyProtocolStates
    if (persistedClaimsActivity) {
        return G7RuntimeAssessment(
            health = G7RuntimeHealth.ORPHANED_STATE,
            attemptAgeMs = attemptAge,
            lastProgressAgeMs = progressAge,
            futureReconnectEpochMs = futureReconnect,
            cleanupRequired = true,
        )
    }
    if (futureReconnect != null) return G7RuntimeAssessment(G7RuntimeHealth.HEALTHY_ARMED, futureReconnectEpochMs = futureReconnect)
    if (missedReconnect) return G7RuntimeAssessment(G7RuntimeHealth.MISSED_RECONNECT)
    return G7RuntimeAssessment(G7RuntimeHealth.RECOVERY_REQUIRED)
}

/**
 * Reconciles durable state with the current process. Persisted CONNECTING/SCANNING state is never
 * accepted as proof that Android BLE objects survived process or service death.
 */
internal object G7RuntimeReconciler {
    fun reconcile(
        context: Context,
        entryPoint: G7RuntimeEntryPoint,
        liveCycle: Boolean? = null,
        allowRepair: Boolean = true,
        cancelLiveCycle: (() -> Unit)? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): G7RuntimeReconcileResult =
        synchronized(G7SensorStateStore.lifecycleLock) {
            val app = context.applicationContext
            val stateStore = G7SensorStateStore(app)
            val diagnosticStore = G7CollectorDiagnosticStore(app)
            val configured = stateStore.read().sensor != null && G7CredentialStore(app).read() != null
            val state = enforceG7CollectorEligibility(app, nowEpochMs, configured)
            val activeAttempt = diagnosticStore.activeAttempt(state.activeAttemptId)
            val pendingCycle = diagnosticStore.pendingScheduledCycle()
            val pendingReconnect = pendingCycle?.requestedReconnectEpoch
            val effectiveLiveCycle = liveCycle ?: G7CollectorRuntimeRegistry.hasLiveCycle()
            val assessment =
                assessG7Runtime(
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
                !G7CollectorRuntimeRegistry.hasRuntimeService() &&
                entryPoint in
                setOf(
                    G7RuntimeEntryPoint.BOOT,
                    G7RuntimeEntryPoint.PACKAGE_REPLACED,
                    G7RuntimeEntryPoint.SIGNAL_LOSS,
                    G7RuntimeEntryPoint.WATCH_APP,
                )
            ) {
                recordRuntimeEvent(app, "FGS_MISSING", entryPoint, assessment, state.nextReconnectEpochMs, null)
            }
            if (assessment.health == G7RuntimeHealth.STALE_ATTEMPT) {
                recordRuntimeEvent(app, "STALE_ATTEMPT_DETECTED", entryPoint, assessment, state.nextReconnectEpochMs, null)
            }

            val shouldReassertDurableAlarm =
                allowRepair &&
                    assessment.health == G7RuntimeHealth.HEALTHY_ARMED &&
                    entryPoint in
                    setOf(
                        G7RuntimeEntryPoint.BOOT,
                        G7RuntimeEntryPoint.PACKAGE_REPLACED,
                        G7RuntimeEntryPoint.SIGNAL_LOSS,
                    )
            if (shouldReassertDurableAlarm && pendingCycle != null) {
                val rearmed =
                    runCatching {
                        G7ReconnectAlarmScheduler.rearmScheduledCycle(app, pendingCycle, state)
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
                return@synchronized G7RuntimeReconcileResult(
                    assessment,
                    if (rearmedAt != null) G7RuntimeHealth.HEALTHY_ARMED else G7RuntimeHealth.RECOVERY_REQUIRED,
                    rearmedAt,
                )
            }

            if (!allowRepair ||
                assessment.health in setOf(G7RuntimeHealth.INACTIVE, G7RuntimeHealth.HEALTHY_ACTIVE, G7RuntimeHealth.HEALTHY_ARMED)
            ) {
                val healthyEvent =
                    when (assessment.health) {
                        G7RuntimeHealth.HEALTHY_ACTIVE -> "RUNTIME_HEALTHY_ACTIVE"
                        G7RuntimeHealth.HEALTHY_ARMED -> "RUNTIME_HEALTHY_ARMED"
                        G7RuntimeHealth.INACTIVE -> "RUNTIME_INACTIVE"
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
                return@synchronized G7RuntimeReconcileResult(assessment, assessment.health, assessment.futureReconnectEpochMs)
            }

            if (assessment.cleanupRequired) {
                (cancelLiveCycle ?: G7CollectorRuntimeRegistry::cancelLiveCycle).invoke()
                AndroidG7Scanner.forceCleanup()
                G7ExpectedWindowLedger(app).markProcessInterrupted(
                    activeAttempt?.cycle?.expectedWindowId,
                    nowEpochMs,
                )
                diagnosticStore.expireStaleAttempts(nowEpochMs, 0L, interruptedByProcessRestart = !effectiveLiveCycle)
            }
            val sensor = state.sensor
            val reconstructedWindows =
                if (sensor != null && assessment.health != G7RuntimeHealth.INACTIVE) {
                    val ledger = G7ExpectedWindowLedger(app)
                    val latestExpected =
                        ledger.latestExpectedAt(
                            sensorId = sensor.sensorId,
                            sessionId = sensor.sessionId ?: sensor.sensorId,
                        )
                    val firstMissing =
                        latestExpected?.plus(G7_SLOT_INTERVAL_MS)
                            ?: state.lastReading?.timestampEpochMs?.plus(G7_SLOT_INTERVAL_MS)
                    if (firstMissing != null && firstMissing < nowEpochMs - G7_RUNTIME_RECONNECT_TOLERANCE_MS) {
                        ledger.reconstructMissed(
                            sensorId = sensor.sensorId,
                            sessionId = sensor.sessionId ?: sensor.sensorId,
                            fromExpectedAt = firstMissing,
                            untilExclusive = nowEpochMs - G7_RUNTIME_RECONNECT_TOLERANCE_MS,
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
                    connectionState = G7ConnectionState.DISCONNECTED,
                    protocolState = G7ProtocolState.RECOVERING,
                    sessionState = G7SessionState.RECOVERING,
                    activeAttemptId = null,
                    scanStartedAtEpochMs = null,
                    scanTimeoutAtEpochMs = state.pairingDeadlineEpochMs,
                    lastError =
                        G7CollectorError(
                            code = "G7-RUNTIME-276",
                            recoverable = true,
                            occurredAtEpochMs = nowEpochMs,
                            safeMessage = "Collector-Laufzeit wurde automatisch wiederhergestellt",
                        ),
                )
            stateStore.saveFromCollector(cleaned)

            val recovery =
                runCatching {
                    G7ReconnectAlarmScheduler.scheduleRecovery(app, cleaned, nowEpochMs)
                }.getOrNull()
            val recoveryAt = recovery?.requestedReconnectEpoch
            if (recoveryAt != null) {
                recordRuntimeEvent(app, "RECOVERY_SLOT_CALCULATED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            }
            val restored =
                if (recoveryAt != null && recoveryAt > nowEpochMs) {
                    stateStore.saveFromCollector(cleaned.copy(nextReconnectEpochMs = recoveryAt))
                    G7RuntimeHealth.HEALTHY_ARMED
                } else {
                    G7RuntimeHealth.RECOVERY_REQUIRED
                }
            val event =
                when (assessment.health) {
                    G7RuntimeHealth.STALE_ATTEMPT -> "STALE_ATTEMPT_CLEANED"
                    G7RuntimeHealth.ORPHANED_STATE -> "ORPHANED_CONNECTION_STATE"
                    G7RuntimeHealth.MISSED_RECONNECT -> "MISSED_RECONNECT_DETECTED"
                    else -> if (restored == G7RuntimeHealth.HEALTHY_ARMED) "RECOVERY_INVARIANT_RESTORED" else "RECOVERY_ALARM_REARM_FAILED"
                }
            recordRuntimeEvent(app, event, entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            if (reconstructedWindows > 0) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    app.recordG7Diagnostic(
                        code = "MISSED_WINDOWS_RECONSTRUCTED",
                        message = "MISSED_WINDOWS_RECONSTRUCTED · $reconstructedWindows lifecycle gaps",
                        severity = DiagnosticSeverity.WARNING,
                        metadata =
                            mapOf(
                                "processInstanceId" to G7ProcessInstance.id,
                                "reconstructedWindowCount" to reconstructedWindows,
                            ),
                    )
                }
            }
            if (recoveryAt != null) {
                recordRuntimeEvent(app, "RECOVERY_ALARM_ARMED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
                recordRuntimeEvent(app, "RECOVERY_INVARIANT_RESTORED", entryPoint, assessment, state.nextReconnectEpochMs, recoveryAt)
            }
            return@synchronized G7RuntimeReconcileResult(assessment, restored, recoveryAt)
        }

    private fun recordRuntimeEvent(
        context: Context,
        event: String,
        entryPoint: G7RuntimeEntryPoint,
        assessment: G7RuntimeAssessment,
        oldReconnectEpochMs: Long?,
        newReconnectEpochMs: Long?,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            context.recordG7Diagnostic(
                code = event,
                message = "$event · ${entryPoint.name}",
                severity =
                    if (event.endsWith("FAILED") || assessment.health == G7RuntimeHealth.RECOVERY_INVARIANT_BROKEN) {
                        DiagnosticSeverity.WARNING
                    } else {
                        DiagnosticSeverity.INFO
                    },
                metadata =
                    mapOf(
                        "processInstanceId" to G7ProcessInstance.id,
                        "pid" to android.os.Process.myPid(),
                        "processStartedAtEpochMs" to G7ProcessInstance.startedAtEpochMs,
                        "processUptimeMs" to android.os.SystemClock.elapsedRealtime(),
                        "appVersion" to
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull(),
                        "collectorEnabled" to G7SensorStateStore(context).read().collectorEnabled,
                        "sensorId" to G7SensorStateStore(context).read().sensor?.sensorId,
                        "sessionId" to G7SensorStateStore(context).read().sensor?.sessionId,
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

internal const val G7_RUNTIME_RECONNECT_TOLERANCE_MS = 30_000L
internal const val G7_RUNTIME_MAX_PROGRESS_INACTIVITY_MS = 2L * 60_000L
