package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7FailureClass
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7ReconnectScheduler
import app.aapswear.g7.G7Sensor

internal fun shouldKeepG7RuntimeForeground(collectorEnabled: Boolean): Boolean = collectorEnabled

internal fun shouldUpdateG7ForegroundNotification(inserted: Boolean, classification: CollectorCycleClassification): Boolean =
    inserted && classification == CollectorCycleClassification.SUCCESS_FRESH

internal fun restoreAuthenticatedG7Address(sensor: G7Sensor, sharedKeyAddress: String?): G7Sensor =
    if (sensor.deviceAddress.isNullOrBlank() && !sharedKeyAddress.isNullOrBlank()) sensor.copy(deviceAddress = sharedKeyAddress) else sensor

internal fun shouldRepairG7RuntimeOnServiceStart(action: String?): Boolean = action != G7CollectorService.ACTION_RECONNECT

internal fun shouldCoalesceG7CollectorTrigger(automatic: Boolean, activeCycle: Boolean): Boolean = automatic && activeCycle

private fun trustedG7CadenceReading(state: G7PersistedState, nowEpochMs: Long): CgmReading? {
    if (!state.collectorEnabled) return null
    val sensor = state.sensor ?: return null
    val reading = state.lastReading ?: return null
    if (reading.status != CgmReadingStatus.VALID || reading.origin != CgmReadingOrigin.LIVE) return null
    if (reading.sensorId != sensor.sensorId || reading.sessionId != (sensor.sessionId ?: sensor.sensorId)) return null
    return reading.takeIf { it.timestampEpochMs <= nowEpochMs }
}

internal fun shouldAwaitG7SensorWindow(state: G7PersistedState, nowEpochMs: Long): Boolean {
    val reading = trustedG7CadenceReading(state, nowEpochMs) ?: return false
    val nextLeadAt = reading.timestampEpochMs + G7ReconnectScheduler.EXPECTED_READING_INTERVAL_MS - G7ReconnectScheduler.PRECONNECT_LEAD_MS
    return nowEpochMs < nextLeadAt
}

/** Restore the armed current-session window even when its last LIVE value is stale. */
internal fun g7StartupSensorWindow(state: G7PersistedState, pending: CollectorCycleTiming?, nowEpochMs: Long): CollectorCycleTiming? {
    val reading = trustedG7CadenceReading(state, nowEpochMs) ?: return null
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
    if (!shouldAwaitG7SensorWindow(state, nowEpochMs)) return null
    val expected = reading.timestampEpochMs + G7ReconnectScheduler.EXPECTED_READING_INTERVAL_MS
    return CollectorCycleTiming(expectedReadingEpoch = expected, requestedReconnectEpoch = expected - G7ReconnectScheduler.PRECONNECT_LEAD_MS)
}

internal fun needsG7FollowUpRepair(collectorEnabled: Boolean, pendingReconnectEpochMs: Long?, nowEpochMs: Long): Boolean =
    collectorEnabled && (pendingReconnectEpochMs == null || pendingReconnectEpochMs <= nowEpochMs)

internal fun hasEstablishedG7SensorSession(state: G7PersistedState): Boolean {
    val sensor = state.sensor ?: return false
    if (!sensor.deviceAddress.isNullOrBlank() && state.authenticationState == app.aapswear.g7.G7AuthenticationState.AUTHENTICATED) return true
    val reading = state.lastReading ?: return false
    return reading.status == CgmReadingStatus.VALID && reading.sensorId == sensor.sensorId && reading.sessionId == (sensor.sessionId ?: sensor.sensorId)
}

internal fun ensureG7PairingAttempt(state: G7PersistedState, nowEpochMs: Long): G7PersistedState {
    if (!state.collectorEnabled) return state
    if (state.sensor == null) {
        return app.aapswear.g7
            .G7SessionManager(state)
            .stop()
    }
    if (hasEstablishedG7SensorSession(state)) return state
    if (state.pairingStartedAtEpochMs == null && state.pairingDeadlineEpochMs == null) {
        return app.aapswear.g7.G7SessionManager(state).stop().copy(
            lastError = app.aapswear.g7.G7CollectorError("G7-SETUP-001", false, nowEpochMs, "Keine aktive Verbindung · bitte Sensor verbinden wählen"),
        )
    }
    val startedAt = state.pairingStartedAtEpochMs ?: nowEpochMs
    val deadline = requireNotNull(g7InitialPairingDeadline(state))
    if (nowEpochMs >= deadline) {
        return app.aapswear.g7.G7SessionManager(state).stop().copy(
            lastError =
                app.aapswear.g7.G7CollectorError(
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
internal fun startG7PairingAttempt(state: G7PersistedState, nowEpochMs: Long): G7PersistedState {
    if (!state.collectorEnabled || state.sensor == null || hasEstablishedG7SensorSession(state)) return state
    return state.copy(
        pairingAttemptId =
            java.util.UUID
                .randomUUID()
                .toString(),
        pairingStartedAtEpochMs = nowEpochMs,
        pairingDeadlineEpochMs = nowEpochMs + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
        scanTimeoutAtEpochMs = nowEpochMs + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
    )
}

internal fun resetG7RuntimeForRestart(state: G7PersistedState): G7PersistedState =
    state.copy(
        connectionState = G7ConnectionState.DISCONNECTED,
        protocolState = G7ProtocolState.UNINITIALIZED,
        authenticationState = if (hasEstablishedG7SensorSession(state)) state.authenticationState else app.aapswear.g7.G7AuthenticationState.UNKNOWN,
        retryCount = 0,
        nextReconnectEpochMs = null,
        lastError = null,
        activeAttemptId = null,
        scanStartedAtEpochMs = null,
        scanTimeoutAtEpochMs = state.pairingDeadlineEpochMs,
    )

internal fun clearG7ScanRuntime(state: G7PersistedState): G7PersistedState =
    state.copy(scanStartedAtEpochMs = null, scanTimeoutAtEpochMs = null)

internal fun collectorAttemptDeadlineMs(state: G7PersistedState): Long =
    if (state.sensor?.deviceAddress.isNullOrBlank() || !hasEstablishedG7SensorSession(state)) {
        G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS + 2L * 60_000L
    } else {
        3L * 60_000L
    }

internal const val RADIO_DEGRADED_CLUSTER_THRESHOLD = 3
internal const val G7_RUNTIME_RECYCLE_COOLDOWN_MS = 15L * 60_000L
internal const val G7_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE = 3

private val RADIO_FAILURE_CLASSES =
    setOf(
        G7FailureClass.DIRECT_NO_CALLBACK,
        G7FailureClass.DIRECT_GATT_133,
        G7FailureClass.DIRECT_OTHER_GATT_ERROR,
        G7FailureClass.SCAN_RADIO_FAILURE,
        G7FailureClass.SENSOR_NOT_ADVERTISING,
        G7FailureClass.SENSOR_UNREACHABLE,
    )

/**
 * A known address remains the fast path while the radio is healthy. After a durable cluster of
 * complete radio failures, waiting for the sensor advertisement is safer than repeatedly asking a
 * degraded vendor Bluetooth stack to connect blindly. A successful reading clears the health
 * streak, so this is a temporary recovery mode and never rewrites the user's configured strategy.
 */
internal fun effectiveG7ReconnectStrategy(
    configured: G7ReconnectStrategy,
    health: G7CollectorHealth,
): G7ReconnectStrategy =
    when {
        configured != G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT -> configured
        health.consecutiveFailures > 0 && health.lastFailureClass == G7FailureClass.DIRECT_NO_CALLBACK -> G7ReconnectStrategy.BOUNDED_SCAN
        health.consecutiveFailures < RADIO_DEGRADED_CLUSTER_THRESHOLD -> configured
        health.lastFailureClass !in RADIO_FAILURE_CLASSES -> configured
        else -> {
            val degradedOffset = health.consecutiveFailures - RADIO_DEGRADED_CLUSTER_THRESHOLD
            val recoveryCycleLength = G7_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE + 1
            if (degradedOffset % recoveryCycleLength < G7_DEGRADED_SCAN_WINDOWS_BEFORE_DIRECT_PROBE) {
                G7ReconnectStrategy.BOUNDED_SCAN
            } else {
                G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT
            }
        }
    }

internal fun shouldRecycleG7Runtime(
    cycle: app.aapswear.g7.CollectorCycleTiming?,
    radioFailureStreak: Int,
    knownSensorAddress: Boolean = false,
    lastRecycleAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (radioFailureStreak < RADIO_DEGRADED_CLUSTER_THRESHOLD || cycle == null) return false
    if ((cycle.scanExactAddressResults ?: 0) > 0) return false
    if (lastRecycleAtEpochMs != null && nowEpochMs - lastRecycleAtEpochMs < G7_RUNTIME_RECYCLE_COOLDOWN_MS) return false

    val recoverableDirectCluster =
        cycle.fallbackScanUsed &&
            cycle.directConnectResult in
            setOf(
                app.aapswear.g7.DirectConnectResult.NO_CALLBACK,
                app.aapswear.g7.DirectConnectResult.STATUS_133,
                app.aapswear.g7.DirectConnectResult.OTHER_STATUS,
                app.aapswear.g7.DirectConnectResult.TIMEOUT,
                app.aapswear.g7.DirectConnectResult.DISCONNECTED_EARLY,
            )
    val degradedScanOnlyMiss =
        knownSensorAddress &&
            cycle.radioDegradedCluster &&
            cycle.directConnectResult == null &&
            cycle.scanStartedAt != null &&
            cycle.scanEndedAt != null

    return recoverableDirectCluster || degradedScanOnlyMiss
}

internal fun nextRadioFailureStreak(previous: G7CollectorHealth, current: G7FailureClass): Int =
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
    cycle: app.aapswear.g7.CollectorCycleTiming?,
): Boolean =
    classification == CollectorCycleClassification.FALLBACK_SCAN_FAILED ||
        (classification == CollectorCycleClassification.GATT_CONNECT_FAILED && cycle?.fallbackScanUsed == true) ||
        (
            classification == CollectorCycleClassification.NO_ADVERTISEMENT &&
                cycle?.scanStartedAt != null &&
                cycle.scanEndedAt != null &&
                (cycle.scanExactAddressResults ?: 0) == 0
        )

internal fun directConnectDiagnosticCode(result: app.aapswear.g7.DirectConnectResult): String =
    when (result) {
        app.aapswear.g7.DirectConnectResult.NO_CALLBACK -> "DIRECT_CONNECT_NO_CALLBACK"
        app.aapswear.g7.DirectConnectResult.STATUS_133 -> "DIRECT_CONNECT_STATUS_133"
        app.aapswear.g7.DirectConnectResult.STATUS_19 -> "DIRECT_CONNECT_STATUS_19"
        app.aapswear.g7.DirectConnectResult.OTHER_STATUS -> "DIRECT_CONNECT_OTHER_STATUS"
        app.aapswear.g7.DirectConnectResult.TIMEOUT -> "DIRECT_CONNECT_TIMEOUT"
        app.aapswear.g7.DirectConnectResult.DISCONNECTED_EARLY -> "DIRECT_CONNECT_DISCONNECTED_EARLY"
        app.aapswear.g7.DirectConnectResult.DEVICE_UNAVAILABLE -> "DIRECT_CONNECT_DEVICE_UNAVAILABLE"
        app.aapswear.g7.DirectConnectResult.SECURITY_ERROR -> "DIRECT_CONNECT_SECURITY_ERROR"
        app.aapswear.g7.DirectConnectResult.SUCCESS -> "DIRECT_CONNECT_SUCCESS"
    }

internal fun sensorForG7LivePublication(
    collectionSensor: G7Sensor,
    currentSensor: G7Sensor?,
    liveState: app.aapswear.g7.G7SensorState,
): G7Sensor =
    (
        currentSensor?.takeIf {
            it.sensorId == collectionSensor.sensorId && it.sessionId == collectionSensor.sessionId
        } ?: collectionSensor
    ).copy(state = liveState)
