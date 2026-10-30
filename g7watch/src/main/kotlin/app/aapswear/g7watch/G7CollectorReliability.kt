package app.aapswear.g7watch

import app.aapswear.g7.G7BleLinkState
import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7CollectorRuntimeState
import app.aapswear.g7.G7DataHealth
import app.aapswear.g7.G7FailureClass
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7RecoveryStage
import app.aapswear.g7.G7SensorAvailability
import app.aapswear.g7.G7SensorIdentityState
import app.aapswear.g7.G7SensorState

/** Pure policy engine. Android callbacks are inputs; durable state is the sole output. */
internal object G7CollectorReliability {
    fun rehydrate(
        state: G7PersistedState,
        now: Long,
    ): G7PersistedState {
        val sensor = state.sensor
        val identity =
            when {
                sensor == null -> G7SensorIdentityState.NO_SENSOR
                sensor.state == G7SensorState.ENDED -> G7SensorIdentityState.SENSOR_EXPIRED
                sensor.state == G7SensorState.ACTIVE -> G7SensorIdentityState.SENSOR_ACTIVE
                else -> G7SensorIdentityState.SENSOR_KNOWN
            }
        val dataHealth = dataHealth(state.lastReading?.timestampEpochMs, now)
        return state.copy(
            health =
                state.health.copy(
                    sensorIdentity = identity,
                    bleLink = G7BleLinkState.DISCONNECTED,
                    runtime =
                        when {
                            !state.collectorEnabled -> G7CollectorRuntimeState.DISABLED
                            identity == G7SensorIdentityState.NO_SENSOR -> G7CollectorRuntimeState.IDLE
                            else -> G7CollectorRuntimeState.WAITING_FOR_WINDOW
                        },
                    dataHealth = dataHealth,
                    lastFreshReadingAt = state.lastReading?.timestampEpochMs,
                    lastSuccessfulConnectionAt = state.lastSuccessfulConnectionEpochMs,
                ),
        )
    }

    fun collecting(health: G7CollectorHealth) =
        health.copy(
            bleLink = G7BleLinkState.CONNECTING,
            runtime = G7CollectorRuntimeState.COLLECTING,
        )

    fun reachable(
        health: G7CollectorHealth,
        now: Long,
        connected: Boolean = false,
    ) = health.copy(
        sensorAvailability = G7SensorAvailability.AVAILABLE,
        bleLink = if (connected) G7BleLinkState.CONNECTED else health.bleLink,
        lastReachabilityEvidenceAt = now,
    )

    fun authenticated(
        health: G7CollectorHealth,
        now: Long,
    ) = reachable(health, now, connected = true).copy(
        bleLink = G7BleLinkState.READY,
    )

    fun succeeded(
        health: G7CollectorHealth,
        measuredAt: Long,
        connectedAt: Long,
    ) = health.copy(
        sensorAvailability = G7SensorAvailability.AVAILABLE,
        bleLink = G7BleLinkState.DISCONNECTED,
        runtime = G7CollectorRuntimeState.WAITING_FOR_WINDOW,
        dataHealth = G7DataHealth.FRESH,
        recoveryStage = G7RecoveryStage.NORMAL,
        consecutiveFailures = 0,
        lastFailureClass = G7FailureClass.NONE,
        lastFreshReadingAt = measuredAt,
        lastSuccessfulConnectionAt = connectedAt,
        lastReachabilityEvidenceAt = connectedAt,
        recoveryStageEnteredAt = null,
    )

    fun failed(
        health: G7CollectorHealth,
        failure: G7FailureClass,
        sensorAdvertisementSeen: Boolean,
        foreignAdvertisementsSeen: Boolean,
        now: Long,
    ): G7CollectorHealth {
        val failures = health.consecutiveFailures + 1
        val unavailable = !sensorAdvertisementSeen && foreignAdvertisementsSeen
        val availability =
            when {
                unavailable && failures >= OWNERSHIP_SUSPECT_THRESHOLD -> G7SensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR
                unavailable -> G7SensorAvailability.TEMPORARILY_UNREACHABLE
                failure == G7FailureClass.SCAN_RADIO_FAILURE -> G7SensorAvailability.UNKNOWN
                else -> G7SensorAvailability.TEMPORARILY_UNREACHABLE
            }
        val stage =
            when {
                failures == 1 && failure in directFailures -> G7RecoveryStage.DIRECT_RETRY
                failures < ESCALATION_THRESHOLD && failure == G7FailureClass.DIRECT_GATT_133 -> G7RecoveryStage.STACK_COOLDOWN
                failures < ESCALATION_THRESHOLD -> G7RecoveryStage.SCAN_RECOVERY
                failures < LOW_POWER_THRESHOLD -> G7RecoveryStage.WAIT_NEXT_SENSOR_WINDOW
                else -> G7RecoveryStage.RECOVERY_ESCALATED
            }
        return health.copy(
            sensorAvailability = availability,
            bleLink = G7BleLinkState.DISCONNECTED,
            runtime = if (failures >= LOW_POWER_THRESHOLD) G7CollectorRuntimeState.DEGRADED else G7CollectorRuntimeState.RECOVERING,
            dataHealth = dataHealth(health.lastFreshReadingAt, now),
            recoveryStage = stage,
            consecutiveFailures = failures,
            lastFailureClass = if (unavailable) G7FailureClass.SENSOR_NOT_ADVERTISING else failure,
            recoveryStageEnteredAt = if (stage != health.recoveryStage) now else health.recoveryStageEnteredAt,
        )
    }

    fun releasePending(
        health: G7CollectorHealth,
        now: Long,
    ) = health.copy(
        sensorAvailability = G7SensorAvailability.RELEASE_PENDING,
        bleLink = G7BleLinkState.CLOSING,
        runtime = G7CollectorRuntimeState.IDLE,
        releaseRequestedAt = now,
    )

    fun dataHealth(
        measuredAt: Long?,
        now: Long,
    ): G7DataHealth =
        when {
            measuredAt == null -> G7DataHealth.NO_DATA
            now - measuredAt <= FRESH_MS -> G7DataHealth.FRESH
            now - measuredAt <= LATE_MS -> G7DataHealth.LATE
            else -> G7DataHealth.STALE
        }

    fun shouldRunPresenceScan(health: G7CollectorHealth): Boolean =
        health.recoveryStage != G7RecoveryStage.RECOVERY_ESCALATED ||
            health.consecutiveFailures % ESCALATED_SCAN_EVERY_CYCLES == 0

    private val directFailures =
        setOf(G7FailureClass.DIRECT_NO_CALLBACK, G7FailureClass.DIRECT_GATT_133, G7FailureClass.DIRECT_OTHER_GATT_ERROR)
    const val OWNERSHIP_SUSPECT_THRESHOLD = 3
    const val ESCALATION_THRESHOLD = 3
    const val LOW_POWER_THRESHOLD = 6
    const val ESCALATED_SCAN_EVERY_CYCLES = 3
    const val FRESH_MS = 6L * 60_000L
    const val LATE_MS = 15L * 60_000L
}

internal fun g7FailureClass(
    errorCode: String,
    sensorAdvertisementSeen: Boolean,
    foreignAdvertisementsSeen: Boolean,
    directResult: app.aapswear.g7.DirectConnectResult? = null,
): G7FailureClass =
    when {
        !sensorAdvertisementSeen && foreignAdvertisementsSeen -> G7FailureClass.SENSOR_NOT_ADVERTISING
        errorCode == "G7-BLE-FALLBACK-107" && directResult == app.aapswear.g7.DirectConnectResult.NO_CALLBACK -> G7FailureClass.DIRECT_NO_CALLBACK
        errorCode == "G7-BLE-FALLBACK-107" && directResult == app.aapswear.g7.DirectConnectResult.STATUS_133 -> G7FailureClass.DIRECT_GATT_133
        errorCode == G7_GATT_133_ERROR_CODE -> G7FailureClass.DIRECT_GATT_133
        errorCode == G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE || errorCode == "DIRECT_CONNECT_NO_CALLBACK" -> G7FailureClass.DIRECT_NO_CALLBACK
        errorCode.contains("AUTH") -> G7FailureClass.AUTH_FAILURE
        errorCode.contains("PROCESS") -> G7FailureClass.PROCESS_INTERRUPTED
        errorCode.contains("SCAN") -> G7FailureClass.SCAN_RADIO_FAILURE
        errorCode.contains("TIMEOUT") -> G7FailureClass.DATA_TIMEOUT
        else -> G7FailureClass.DIRECT_OTHER_GATT_ERROR
    }
