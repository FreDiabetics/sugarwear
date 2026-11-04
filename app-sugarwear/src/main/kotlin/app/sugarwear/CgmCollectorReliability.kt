package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmBleLinkState
import app.sugarlicious.sensor.protocol.CgmCollectorHealth
import app.sugarlicious.sensor.protocol.CgmCollectorRuntimeState
import app.sugarlicious.sensor.protocol.CgmDataHealth
import app.sugarlicious.sensor.protocol.CgmFailureClass
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmRecoveryStage
import app.sugarlicious.sensor.protocol.CgmSensorAvailability
import app.sugarlicious.sensor.protocol.CgmSensorIdentityState
import app.sugarlicious.sensor.protocol.CgmSensorState

/** Pure policy engine. Android callbacks are inputs; durable state is the sole output. */
internal object CgmCollectorReliability {
    fun rehydrate(
        state: CgmPersistedState,
        now: Long,
    ): CgmPersistedState {
        val sensor = state.sensor
        val identity =
            when {
                sensor == null -> CgmSensorIdentityState.NO_SENSOR
                sensor.state == CgmSensorState.ENDED -> CgmSensorIdentityState.SENSOR_EXPIRED
                sensor.state == CgmSensorState.ACTIVE -> CgmSensorIdentityState.SENSOR_ACTIVE
                else -> CgmSensorIdentityState.SENSOR_KNOWN
            }
        val dataHealth = dataHealth(state.lastReading?.timestampEpochMs, now)
        return state.copy(
            health =
                state.health.copy(
                    sensorIdentity = identity,
                    bleLink = CgmBleLinkState.DISCONNECTED,
                    runtime =
                        when {
                            !state.collectorEnabled -> CgmCollectorRuntimeState.DISABLED
                            identity == CgmSensorIdentityState.NO_SENSOR -> CgmCollectorRuntimeState.IDLE
                            else -> CgmCollectorRuntimeState.WAITING_FOR_WINDOW
                        },
                    dataHealth = dataHealth,
                    lastFreshReadingAt = state.lastReading?.timestampEpochMs,
                    lastSuccessfulConnectionAt = state.lastSuccessfulConnectionEpochMs,
                ),
        )
    }

    fun collecting(health: CgmCollectorHealth) =
        health.copy(
            bleLink = CgmBleLinkState.CONNECTING,
            runtime = CgmCollectorRuntimeState.COLLECTING,
        )

    fun reachable(
        health: CgmCollectorHealth,
        now: Long,
        connected: Boolean = false,
    ) = health.copy(
        sensorAvailability = CgmSensorAvailability.AVAILABLE,
        bleLink = if (connected) CgmBleLinkState.CONNECTED else health.bleLink,
        lastReachabilityEvidenceAt = now,
    )

    fun authenticated(
        health: CgmCollectorHealth,
        now: Long,
    ) = reachable(health, now, connected = true).copy(
        bleLink = CgmBleLinkState.READY,
    )

    fun succeeded(
        health: CgmCollectorHealth,
        measuredAt: Long,
        connectedAt: Long,
    ) = health.copy(
        sensorAvailability = CgmSensorAvailability.AVAILABLE,
        bleLink = CgmBleLinkState.DISCONNECTED,
        runtime = CgmCollectorRuntimeState.WAITING_FOR_WINDOW,
        dataHealth = CgmDataHealth.FRESH,
        recoveryStage = CgmRecoveryStage.NORMAL,
        consecutiveFailures = 0,
        lastFailureClass = CgmFailureClass.NONE,
        lastFreshReadingAt = measuredAt,
        lastSuccessfulConnectionAt = connectedAt,
        lastReachabilityEvidenceAt = connectedAt,
        recoveryStageEnteredAt = null,
    )

    fun failed(
        health: CgmCollectorHealth,
        failure: CgmFailureClass,
        sensorAdvertisementSeen: Boolean,
        foreignAdvertisementsSeen: Boolean,
        now: Long,
    ): CgmCollectorHealth {
        val failures = health.consecutiveFailures + 1
        val unavailable = !sensorAdvertisementSeen && foreignAdvertisementsSeen
        val availability =
            when {
                unavailable && failures >= OWNERSHIP_SUSPECT_THRESHOLD -> CgmSensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR
                unavailable -> CgmSensorAvailability.TEMPORARILY_UNREACHABLE
                failure == CgmFailureClass.SCAN_RADIO_FAILURE -> CgmSensorAvailability.UNKNOWN
                else -> CgmSensorAvailability.TEMPORARILY_UNREACHABLE
            }
        val stage =
            when {
                failures == 1 && failure in directFailures -> CgmRecoveryStage.DIRECT_RETRY
                failures < ESCALATION_THRESHOLD && failure == CgmFailureClass.DIRECT_GATT_133 -> CgmRecoveryStage.STACK_COOLDOWN
                failures < ESCALATION_THRESHOLD -> CgmRecoveryStage.SCAN_RECOVERY
                failures < LOW_POWER_THRESHOLD -> CgmRecoveryStage.WAIT_NEXT_SENSOR_WINDOW
                else -> CgmRecoveryStage.RECOVERY_ESCALATED
            }
        return health.copy(
            sensorAvailability = availability,
            bleLink = CgmBleLinkState.DISCONNECTED,
            runtime = if (failures >= LOW_POWER_THRESHOLD) CgmCollectorRuntimeState.DEGRADED else CgmCollectorRuntimeState.RECOVERING,
            dataHealth = dataHealth(health.lastFreshReadingAt, now),
            recoveryStage = stage,
            consecutiveFailures = failures,
            lastFailureClass = if (unavailable) CgmFailureClass.SENSOR_NOT_ADVERTISING else failure,
            recoveryStageEnteredAt = if (stage != health.recoveryStage) now else health.recoveryStageEnteredAt,
        )
    }

    fun releasePending(
        health: CgmCollectorHealth,
        now: Long,
    ) = health.copy(
        sensorAvailability = CgmSensorAvailability.RELEASE_PENDING,
        bleLink = CgmBleLinkState.CLOSING,
        runtime = CgmCollectorRuntimeState.IDLE,
        releaseRequestedAt = now,
    )

    fun dataHealth(
        measuredAt: Long?,
        now: Long,
    ): CgmDataHealth =
        when {
            measuredAt == null -> CgmDataHealth.NO_DATA
            now - measuredAt <= FRESH_MS -> CgmDataHealth.FRESH
            now - measuredAt <= LATE_MS -> CgmDataHealth.LATE
            else -> CgmDataHealth.STALE
        }

    fun shouldRunPresenceScan(health: CgmCollectorHealth): Boolean =
        health.recoveryStage != CgmRecoveryStage.RECOVERY_ESCALATED ||
            health.consecutiveFailures % ESCALATED_SCAN_EVERY_CYCLES == 0

    private val directFailures =
        setOf(CgmFailureClass.DIRECT_NO_CALLBACK, CgmFailureClass.DIRECT_GATT_133, CgmFailureClass.DIRECT_OTHER_GATT_ERROR)
    const val OWNERSHIP_SUSPECT_THRESHOLD = 3
    const val ESCALATION_THRESHOLD = 3
    const val LOW_POWER_THRESHOLD = 6
    const val ESCALATED_SCAN_EVERY_CYCLES = 3
    const val FRESH_MS = 6L * 60_000L
    const val LATE_MS = 15L * 60_000L
}

internal fun cgmFailureClass(
    errorCode: String,
    sensorAdvertisementSeen: Boolean,
    foreignAdvertisementsSeen: Boolean,
    directResult: app.sugarlicious.sensor.protocol.DirectConnectResult? = null,
): CgmFailureClass =
    when {
        !sensorAdvertisementSeen && foreignAdvertisementsSeen -> CgmFailureClass.SENSOR_NOT_ADVERTISING
        errorCode == "G7-BLE-FALLBACK-107" && directResult == app.sugarlicious.sensor.protocol.DirectConnectResult.NO_CALLBACK -> CgmFailureClass.DIRECT_NO_CALLBACK
        errorCode == "G7-BLE-FALLBACK-107" && directResult == app.sugarlicious.sensor.protocol.DirectConnectResult.STATUS_133 -> CgmFailureClass.DIRECT_GATT_133
        errorCode == CGM_GATT_133_ERROR_CODE -> CgmFailureClass.DIRECT_GATT_133
        errorCode == CGM_DIRECT_CONNECT_TIMEOUT_ERROR_CODE || errorCode == "DIRECT_CONNECT_NO_CALLBACK" -> CgmFailureClass.DIRECT_NO_CALLBACK
        errorCode.contains("AUTH") -> CgmFailureClass.AUTH_FAILURE
        errorCode.contains("PROCESS") -> CgmFailureClass.PROCESS_INTERRUPTED
        errorCode.contains("SCAN") -> CgmFailureClass.SCAN_RADIO_FAILURE
        errorCode.contains("TIMEOUT") -> CgmFailureClass.DATA_TIMEOUT
        else -> CgmFailureClass.DIRECT_OTHER_GATT_ERROR
    }
