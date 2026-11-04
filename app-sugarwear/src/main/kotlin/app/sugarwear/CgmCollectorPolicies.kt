package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorHealth
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmFailureClass
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmReconnectScheduler
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import app.sugarlicious.sensor.protocol.CollectorDiagnosticAttempt

internal fun shouldKeepCgmRuntimeForeground(collectorEnabled: Boolean): Boolean = collectorEnabled

internal fun shouldUpdateCgmForegroundNotification(inserted: Boolean, classification: CollectorCycleClassification): Boolean =
    inserted && classification == CollectorCycleClassification.SUCCESS_FRESH

internal fun restoreAuthenticatedCgmAddress(sensor: CgmSensor, sharedKeyAddress: String?): CgmSensor =
    if (sensor.deviceAddress.isNullOrBlank() && !sharedKeyAddress.isNullOrBlank()) sensor.copy(deviceAddress = sharedKeyAddress) else sensor

internal fun shouldRepairCgmRuntimeOnServiceStart(action: String?): Boolean = action != CgmCollectorService.ACTION_RECONNECT

internal fun shouldCoalesceCgmCollectorTrigger(automatic: Boolean, activeCycle: Boolean): Boolean = automatic && activeCycle

private fun trustedCgmCadenceReading(state: CgmPersistedState, nowEpochMs: Long): CgmReading? {
    if (!state.collectorEnabled) return null
    val sensor = state.sensor ?: return null
    val reading = state.lastReading ?: return null
    if (reading.status != CgmReadingStatus.VALID || reading.origin != CgmReadingOrigin.LIVE) return null
    if (reading.sensorId != sensor.sensorId || reading.sessionId != (sensor.sessionId ?: sensor.sensorId)) return null
    return reading.takeIf { it.timestampEpochMs <= nowEpochMs }
}

internal fun shouldAwaitCgmSensorWindow(state: CgmPersistedState, nowEpochMs: Long): Boolean {
    val reading = trustedCgmCadenceReading(state, nowEpochMs) ?: return false
    val nextLeadAt = reading.timestampEpochMs + CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS - CgmReconnectScheduler.PRECONNECT_LEAD_MS
    return nowEpochMs < nextLeadAt
}

/** Restore the armed current-session window even when its last LIVE value is stale. */
internal fun cgmStartupSensorWindow(state: CgmPersistedState, pending: CollectorCycleTiming?, nowEpochMs: Long): CollectorCycleTiming? {
    val reading = trustedCgmCadenceReading(state, nowEpochMs) ?: return null
    pending?.let { cycle ->
        val expected = cycle.expectedReadingEpoch
        val requested = cycle.requestedReconnectEpoch
        if (expected != null &&
            requested != null &&
            requested > nowEpochMs &&
            expected >= requested &&
            cycle.expectedWindowId == expectedWindowId(reading.sensorId, reading.sessionId, expected)
        ) {
            return cycle
        }
    }
    if (!shouldAwaitCgmSensorWindow(state, nowEpochMs)) return null
    val expected = reading.timestampEpochMs + CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS
    return CollectorCycleTiming(expectedReadingEpoch = expected, requestedReconnectEpoch = expected - CgmReconnectScheduler.PRECONNECT_LEAD_MS)
}

internal fun needsCgmFollowUpRepair(collectorEnabled: Boolean, pendingReconnectEpochMs: Long?, nowEpochMs: Long): Boolean =
    collectorEnabled && (pendingReconnectEpochMs == null || pendingReconnectEpochMs <= nowEpochMs)

internal fun hasEstablishedCgmSensorSession(state: CgmPersistedState): Boolean {
    val sensor = state.sensor ?: return false
    if (!sensor.deviceAddress.isNullOrBlank() && state.authenticationState == app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED) return true
    val reading = state.lastReading ?: return false
    return reading.status == CgmReadingStatus.VALID && reading.sensorId == sensor.sensorId && reading.sessionId == (sensor.sessionId ?: sensor.sensorId)
}

internal fun ensureCgmPairingAttempt(state: CgmPersistedState, nowEpochMs: Long): CgmPersistedState {
    if (!state.collectorEnabled) return state
    if (state.sensor == null) {
        return app.sugarlicious.sensor.protocol
            .CgmSessionManager(state)
            .stop()
    }
    if (hasEstablishedCgmSensorSession(state)) return state
    if (state.pairingStartedAtEpochMs == null && state.pairingDeadlineEpochMs == null) {
        return app.sugarlicious.sensor.protocol.CgmSessionManager(state).stop().copy(
            lastError =
                app.sugarlicious.sensor.protocol
                    .CgmCollectorError("G7-SETUP-001", false, nowEpochMs, "Keine aktive Verbindung · bitte Sensor verbinden wählen"),
        )
    }
    val startedAt = state.pairingStartedAtEpochMs ?: nowEpochMs
    val deadline = requireNotNull(cgmInitialPairingDeadline(state))
    if (nowEpochMs >= deadline) {
        return app.sugarlicious.sensor.protocol.CgmSessionManager(state).stop().copy(
            lastError =
                app.sugarlicious.sensor.protocol.CgmCollectorError(
                    "G7-PAIRING-TIMEOUT",
                    false,
                    nowEpochMs,
                    "Kein Sensor verbunden · Verbindung beendet. Bitte erneut starten.",
                ),
        )
    }
    return state.copy(
        pairingAttemptId =
            state.pairingAttemptId ?: java.util.UUID
                .randomUUID()
                .toString(),
        pairingStartedAtEpochMs = startedAt,
        pairingDeadlineEpochMs = deadline,
        scanTimeoutAtEpochMs = deadline,
    )
}

/** Called only by the explicit user start, never by boot/alarm/runtime reconciliation. */
internal fun startCgmPairingAttempt(state: CgmPersistedState, nowEpochMs: Long): CgmPersistedState {
    if (!state.collectorEnabled || state.sensor == null || hasEstablishedCgmSensorSession(state)) return state
    return state.copy(
        pairingAttemptId =
            java.util.UUID
                .randomUUID()
                .toString(),
        pairingStartedAtEpochMs = nowEpochMs,
        pairingDeadlineEpochMs = nowEpochMs + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
        scanTimeoutAtEpochMs = nowEpochMs + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
    )
}

internal fun resetCgmRuntimeForRestart(state: CgmPersistedState): CgmPersistedState =
    state.copy(
        connectionState = CgmConnectionState.DISCONNECTED,
        protocolState = CgmProtocolState.UNINITIALIZED,
        authenticationState = if (hasEstablishedCgmSensorSession(state)) state.authenticationState else app.sugarlicious.sensor.protocol.CgmAuthenticationState.UNKNOWN,
        retryCount = 0,
        nextReconnectEpochMs = null,
        lastError = null,
        activeAttemptId = null,
        scanStartedAtEpochMs = null,
        scanTimeoutAtEpochMs = state.pairingDeadlineEpochMs,
    )

internal fun clearCgmScanRuntime(state: CgmPersistedState): CgmPersistedState =
    state.copy(scanStartedAtEpochMs = null, scanTimeoutAtEpochMs = null)

internal fun collectorAttemptDeadlineMs(state: CgmPersistedState): Long =
    if (state.sensor?.deviceAddress.isNullOrBlank() || !hasEstablishedCgmSensorSession(state)) {
        CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS + 2L * 60_000L
    } else {
        3L * 60_000L
    }

internal const val RADIO_DEGRADED_CLUSTER_THRESHOLD = 3
internal const val CGM_RUNTIME_RECYCLE_COOLDOWN_MS = 15L * 60_000L
internal const val CGM_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE = 3

private val RADIO_FAILURE_CLASSES =
    setOf(
        CgmFailureClass.DIRECT_NO_CALLBACK,
        CgmFailureClass.DIRECT_GATT_133,
        CgmFailureClass.DIRECT_OTHER_GATT_ERROR,
        CgmFailureClass.SCAN_RADIO_FAILURE,
        CgmFailureClass.SENSOR_NOT_ADVERTISING,
        CgmFailureClass.SENSOR_UNREACHABLE,
    )

/**
 * A known address remains the fast path while the radio is healthy. After a durable cluster of
 * complete radio failures, waiting for the sensor advertisement is safer than repeatedly asking a
 * degraded vendor Bluetooth stack to connect blindly. A successful reading clears the health
 * streak, so this is a temporary recovery mode and never rewrites the user's configured strategy.
 */
internal fun effectiveCgmReconnectStrategy(
    configured: CgmReconnectStrategy,
    health: CgmCollectorHealth,
): CgmReconnectStrategy =
    when {
        configured != CgmReconnectStrategy.KNOWN_ADDRESS_DIRECT -> configured
        health.consecutiveFailures > 0 && health.lastFailureClass == CgmFailureClass.DIRECT_NO_CALLBACK -> CgmReconnectStrategy.BOUNDED_SCAN
        health.consecutiveFailures < RADIO_DEGRADED_CLUSTER_THRESHOLD -> configured
        health.lastFailureClass !in RADIO_FAILURE_CLASSES -> configured
        else -> {
            val degradedOffset = health.consecutiveFailures - RADIO_DEGRADED_CLUSTER_THRESHOLD
            val recoveryCycleLength = CGM_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE + 1
            if (degradedOffset % recoveryCycleLength < CGM_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE) {
                CgmReconnectStrategy.BOUNDED_SCAN
            } else {
                CgmReconnectStrategy.KNOWN_ADDRESS_DIRECT
            }
        }
    }

internal fun shouldRecycleCgmRuntime(
    cycle: app.sugarlicious.sensor.protocol.CollectorCycleTiming?,
    radioFailureStreak: Int,
    knownSensorAddress: Boolean = false,
    lastRecycleAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (radioFailureStreak < RADIO_DEGRADED_CLUSTER_THRESHOLD || cycle == null) return false
    if ((cycle.scanExactAddressResults ?: 0) > 0) return false
    if (lastRecycleAtEpochMs != null && nowEpochMs - lastRecycleAtEpochMs < CGM_RUNTIME_RECYCLE_COOLDOWN_MS) return false

    val recoverableDirectCluster =
        cycle.fallbackScanUsed &&
            cycle.directConnectResult in
            setOf(
                app.sugarlicious.sensor.protocol.DirectConnectResult.NO_CALLBACK,
                app.sugarlicious.sensor.protocol.DirectConnectResult.STATUS_133,
                app.sugarlicious.sensor.protocol.DirectConnectResult.OTHER_STATUS,
                app.sugarlicious.sensor.protocol.DirectConnectResult.TIMEOUT,
                app.sugarlicious.sensor.protocol.DirectConnectResult.DISCONNECTED_EARLY,
            )
    val degradedScanOnlyMiss =
        knownSensorAddress &&
            cycle.radioDegradedCluster &&
            cycle.directConnectResult == null &&
            cycle.scanStartedAt != null &&
            cycle.scanEndedAt != null

    return recoverableDirectCluster || degradedScanOnlyMiss
}

internal fun nextRadioFailureStreak(previous: CgmCollectorHealth, current: CgmFailureClass): Int =
    if (current in RADIO_FAILURE_CLASSES && previous.lastFailureClass in RADIO_FAILURE_CLASSES) previous.consecutiveFailures + 1 else 1

internal fun consecutiveRadioFailures(attempts: List<CollectorDiagnosticAttempt>, currentAttemptId: Long): Int =
    attempts
        .asSequence()
        .filter { it.attemptId < currentAttemptId }
        .sortedByDescending { it.attemptId }
        .takeWhile { isCompleteRadioFailure(it.classification, it.cycle) }
        .count()

internal fun isCompleteRadioFailure(
    classification: CollectorCycleClassification?,
    cycle: app.sugarlicious.sensor.protocol.CollectorCycleTiming?,
): Boolean =
    classification == CollectorCycleClassification.FALLBACK_SCAN_FAILED ||
        (classification == CollectorCycleClassification.GATT_CONNECT_FAILED && cycle?.fallbackScanUsed == true) ||
        (
            classification == CollectorCycleClassification.NO_ADVERTISEMENT &&
                cycle?.scanStartedAt != null &&
                cycle.scanEndedAt != null &&
                (cycle.scanExactAddressResults ?: 0) == 0
        )

internal fun directConnectDiagnosticCode(result: app.sugarlicious.sensor.protocol.DirectConnectResult): String =
    when (result) {
        app.sugarlicious.sensor.protocol.DirectConnectResult.NO_CALLBACK -> "DIRECT_CONNECT_NO_CALLBACK"
        app.sugarlicious.sensor.protocol.DirectConnectResult.STATUS_133 -> "DIRECT_CONNECT_STATUS_133"
        app.sugarlicious.sensor.protocol.DirectConnectResult.STATUS_19 -> "DIRECT_CONNECT_STATUS_19"
        app.sugarlicious.sensor.protocol.DirectConnectResult.OTHER_STATUS -> "DIRECT_CONNECT_OTHER_STATUS"
        app.sugarlicious.sensor.protocol.DirectConnectResult.TIMEOUT -> "DIRECT_CONNECT_TIMEOUT"
        app.sugarlicious.sensor.protocol.DirectConnectResult.DISCONNECTED_EARLY -> "DIRECT_CONNECT_DISCONNECTED_EARLY"
        app.sugarlicious.sensor.protocol.DirectConnectResult.DEVICE_UNAVAILABLE -> "DIRECT_CONNECT_DEVICE_UNAVAILABLE"
        app.sugarlicious.sensor.protocol.DirectConnectResult.SECURITY_ERROR -> "DIRECT_CONNECT_SECURITY_ERROR"
        app.sugarlicious.sensor.protocol.DirectConnectResult.SUCCESS -> "DIRECT_CONNECT_SUCCESS"
    }

internal fun sensorForCgmLivePublication(
    collectionSensor: CgmSensor,
    currentSensor: CgmSensor?,
    liveState: app.sugarlicious.sensor.protocol.CgmSensorState,
): CgmSensor =
    (
        currentSensor?.takeIf {
            it.sensorId == collectionSensor.sensorId && it.sessionId == collectionSensor.sessionId
        } ?: collectionSensor
    ).copy(state = liveState)
