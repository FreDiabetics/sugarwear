package app.sugarlicious.sensor.protocol

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CgmReading(
    val id: String,
    val source: DataSourceId,
    val sensorId: String,
    val sessionId: String,
    val glucoseMgDl: Double,
    val timestampEpochMs: Long,
    val receivedAtEpochMs: Long,
    val deltaMgDl: Double? = null,
    val trend: Trend = Trend.UNKNOWN,
    val trendRateMgDlPerMinute: Double? = null,
    val predictedMgDl: Double? = null,
    val sensorAgeSeconds: Long? = null,
    val status: CgmReadingStatus = CgmReadingStatus.VALID,
    val sequenceNumber: Long? = null,
    val displayOnly: Boolean = false,
    val rawSourceTimestamp: Long? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val protocolStatusCode: Int? = null,
    val calibrationStateCode: Int? = null,
    val reservedField: Int? = null,
    val origin: CgmReadingOrigin = CgmReadingOrigin.LIVE,
)

@Serializable enum class CgmReadingStatus { VALID, SENSOR_ERROR, INVALID }

@Serializable enum class CgmReadingOrigin { LIVE, BACKFILL }

@Serializable enum class CgmTrend { DOUBLE_DOWN, SINGLE_DOWN, FORTY_FIVE_DOWN, FLAT, FORTY_FIVE_UP, SINGLE_UP, DOUBLE_UP, UNKNOWN }

@Serializable enum class CgmSensorState { UNKNOWN, WARMUP, ACTIVE, GRACE_PERIOD, ENDED, ERROR }

@Serializable enum class CgmConnectionState { DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, CONNECTED }

@Serializable
enum class CgmProtocolState {
    UNINITIALIZED,
    IDLE,
    SCANNING,
    SENSOR_FOUND,
    CONNECTING,
    DISCOVERING,
    DISCOVERING_SERVICES,
    ENABLING_NOTIFICATIONS,
    AUTHENTICATION_START,
    AUTHENTICATION_ROUND_1,
    AUTHENTICATION_ROUND_2,
    AUTHENTICATION_ROUND_3,
    CHALLENGE,
    CERTIFICATE_EXCHANGE,
    KEY_EXCHANGE,
    BONDING,
    AUTHENTICATING,
    AUTHENTICATED,
    REQUESTING_GLUCOSE,
    RECEIVING_GLUCOSE,
    BACKFILL,
    WAITING_FOR_NEXT_READING,
    DISCONNECTED,
    RECOVERING,
    ERROR,
}

@Serializable enum class CgmAuthenticationState { UNKNOWN, REQUIRED, AUTHENTICATING, AUTHENTICATED, FAILED }

@Serializable
enum class CgmSessionState {
    UNINITIALIZED,
    INITIAL_SETUP,
    AUTHENTICATING,
    AUTHENTICATED,
    READY_FOR_RECONNECT,
    REAUTHENTICATING,
    ACTIVE,
    WAITING_FOR_NEXT_READING,
    RECOVERING,
    REQUIRES_REBOND,
    REQUIRES_FULL_HANDSHAKE,
    USER_INTERVENTION_REQUIRED,
}

@Serializable enum class CgmCollectorState { DISABLED, STARTING, SCANNING, CONNECTING, AUTHENTICATING, CONNECTED, RECEIVING, WAITING, RECOVERING, ERROR, USER_ACTION_REQUIRED }

@Serializable enum class CollectorOwner { PHONE, WATCH, TRANSITION_TO_PHONE, TRANSITION_TO_WATCH, UNKNOWN }

@Serializable enum class CgmRecoveryStep { NORMAL_RECONNECT, AUTH_RETRY, SHORT_RETRY, BLE_RESCAN, DEVICE_ADDRESS_REFRESH, SESSION_REAUTH, SESSION_RESET, REBOND, FULL_HANDSHAKE, USER_INTERVENTION_REQUIRED }

@Serializable
enum class CollectorDiagnosticStage {
    EXPECTED_WINDOW,
    WATCHDOG,
    IDLE,
    WAITING_FOR_WINDOW,
    ALARM_RECEIVED,
    SERVICE_START,
    WAKE_LOCK,
    SCAN_START,
    SCANNING,
    ADVERTISEMENT_FOUND,
    CONNECT_REQUEST,
    GATT_CONNECTED,
    SERVICE_DISCOVERY,
    SERVICE_READY,
    AUTH_START,
    AUTH_CHALLENGE,
    AUTH_RESPONSE,
    AUTH_SUCCESS,
    AUTH_FAILURE,
    GLUCOSE_REQUEST,
    GLUCOSE_RECEIVED,
    VALIDATION,
    STORE,
    SYNC,
    GATT_CLOSE,
    RETRY,
    RECOVERY,
    COMPLETE,
    ERROR,
}

@Serializable
enum class CollectorDiagnosticResult {
    STARTED,
    INFO,
    SUCCESS,
    RECOVERABLE_ERROR,
    FATAL_ERROR,
    CANCELLED,
}

@Serializable
enum class CollectorAlarmKind { EXACT, INEXACT, NONE }

@Serializable
enum class CollectorCycleClassification {
    SUCCESS_FRESH,
    SUCCESS_AGED,
    ALARM_LATE,
    SERVICE_START_FAILED,
    SCAN_STARTED_LATE,
    NO_ADVERTISEMENT,
    GATT_CONNECT_FAILED,
    AUTH_FAILED,
    GLUCOSE_TIMEOUT,
    INVALID_PACKET,
    STORE_FAILED,
    CANCELLED,
    DIRECT_CONNECT_FAILED,
    FALLBACK_SCAN_FAILED,
    HUNG,
    PROCESS_INTERRUPTED,
    COALESCED,
    MISSED_SENSOR_WINDOW,
    DEVICE_OFF_OR_REBOOT_GAP,
    GATT_NO_CALLBACK,
}

@Serializable
enum class DirectConnectResult {
    NO_CALLBACK,
    STATUS_133,
    STATUS_19,
    OTHER_STATUS,
    TIMEOUT,
    DISCONNECTED_EARLY,
    DEVICE_UNAVAILABLE,
    SECURITY_ERROR,
    SUCCESS,
}

/** Orthogonal, persisted collector truth. None of these values implies another dimension. */
@Serializable enum class CgmSensorIdentityState { NO_SENSOR, SENSOR_KNOWN, SENSOR_ACTIVE, SENSOR_EXPIRED, SENSOR_REPLACED }

@Serializable enum class CgmSensorAvailability { UNKNOWN, AVAILABLE, TEMPORARILY_UNREACHABLE, POSSIBLY_OWNED_BY_OTHER_COLLECTOR, RELEASE_PENDING, UNAVAILABLE }

@Serializable enum class CgmBleLinkState { DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATING, READY, CLOSING }

@Serializable enum class CgmCollectorRuntimeState { DISABLED, IDLE, WAITING_FOR_WINDOW, COLLECTING, RECOVERING, BACKFILLING, DEGRADED }

@Serializable enum class CgmDataHealth { FRESH, LATE, STALE, NO_DATA }

@Serializable enum class CgmRecoveryStage { NORMAL, DIRECT_RETRY, STACK_COOLDOWN, SCAN_RECOVERY, WAIT_NEXT_SENSOR_WINDOW, RECOVERY_ESCALATED }

@Serializable enum class CgmFailureClass {
    NONE,
    SCHEDULING_MISSED,
    PROCESS_INTERRUPTED,
    DIRECT_NO_CALLBACK,
    DIRECT_GATT_133,
    DIRECT_OTHER_GATT_ERROR,
    AUTH_FAILURE,
    DATA_TIMEOUT,
    SENSOR_NOT_ADVERTISING,
    SCAN_RADIO_FAILURE,
    SENSOR_UNREACHABLE,
    BACKFILL_TRIGGER_FAILURE,
    BACKFILL_REQUEST_FAILURE,
    BACKFILL_DATA_MISSING,
}

@Serializable
data class CgmCollectorHealth(
    val sensorIdentity: CgmSensorIdentityState = CgmSensorIdentityState.NO_SENSOR,
    val sensorAvailability: CgmSensorAvailability = CgmSensorAvailability.UNKNOWN,
    val bleLink: CgmBleLinkState = CgmBleLinkState.DISCONNECTED,
    val runtime: CgmCollectorRuntimeState = CgmCollectorRuntimeState.DISABLED,
    val dataHealth: CgmDataHealth = CgmDataHealth.NO_DATA,
    val recoveryStage: CgmRecoveryStage = CgmRecoveryStage.NORMAL,
    val consecutiveFailures: Int = 0,
    val lastFailureClass: CgmFailureClass = CgmFailureClass.NONE,
    val lastFreshReadingAt: Long? = null,
    val lastSuccessfulConnectionAt: Long? = null,
    val lastReachabilityEvidenceAt: Long? = null,
    val recoveryStageEnteredAt: Long? = null,
    val releaseRequestedAt: Long? = null,
)

@Serializable
enum class CollectorSlotStrategy {
    DIRECT_ONLY_SUCCESS,
    DIRECT_RETRY_SUCCESS,
    FALLBACK_SCAN_SUCCESS,
    FULL_SLOT_FAILED,
}

@Serializable
enum class CollectorWindowTerminalState {
    SUCCESS_FRESH,
    SUCCESS_BACKFILL_ONLY,
    DUPLICATE_SUPPRESSED,
    SENSOR_ERROR,
    ALARM_NOT_DELIVERED,
    RECEIVER_NOT_REACHED,
    SERVICE_START_FAILED,
    PROCESS_INTERRUPTED,
    GATT_STATUS,
    NO_CALLBACK,
    CONNECT_FAILED,
    DISCOVERY_FAILED,
    AUTH_FAILED,
    SCAN_FAILED,
    CANCELLED,
    SUPERSEDED,
    MISSED_WINDOW,
    DEVICE_UNAVAILABLE,
    UNKNOWN,
}

@Serializable enum class CgmGapRecoveryState { OPEN, RECOVERY_REQUIRED, RECOVERY_IN_FLIGHT, RECOVERED, UNRECOVERABLE, SESSION_ENDED }

@Serializable enum class CgmBackfillOutcome { REQUEST_STARTED, REQUEST_FAILED, RESPONSE_RECEIVED, PARTIAL_RECOVERY, RESPONSE_DID_NOT_CONTAIN_GAP, GAP_RECOVERED, ALREADY_PRESENT }

@Serializable
data class CollectorCycleTiming(
    val expectedWindowId: String? = null,
    val expectedReadingEpoch: Long? = null,
    val requestedReconnectEpoch: Long? = null,
    val alarmKind: CollectorAlarmKind = CollectorAlarmKind.NONE,
    val canScheduleExactAlarms: Boolean? = null,
    val batteryUnrestricted: Boolean? = null,
    val deviceIdleMode: Boolean? = null,
    val isInteractive: Boolean? = null,
    val charging: Boolean? = null,
    val alarmTriggeredAt: Long? = null,
    val receiverReceivedAt: Long? = null,
    val serviceOnStartCommandAt: Long? = null,
    val wakeLockAcquiredAt: Long? = null,
    val scanStartedAt: Long? = null,
    val advertisementFoundAt: Long? = null,
    val advertisementRssi: Int? = null,
    val connectGattStartedAt: Long? = null,
    val gattGeneration: Long? = null,
    val directConnectCallbackAt: Long? = null,
    val directConnectStartedElapsedRealtimeMs: Long? = null,
    val processUptimeAtDirectConnectMs: Long? = null,
    val directConnectResult: DirectConnectResult? = null,
    val directConnectStatus: Int? = null,
    val directConnectNewState: Int? = null,
    val directConnectAttempts: Int = 0,
    val fallbackScanUsed: Boolean = false,
    val scanEndedAt: Long? = null,
    val scanMode: Int? = null,
    val scanCallbackType: Int? = null,
    val scanTotalResults: Int? = null,
    val scanConnectableResults: Int? = null,
    @SerialName("scanNamedG7Results")
    val scanNamedCgmResults: Int? = null,
    val scanExactAddressResults: Int? = null,
    val scanDuplicateResults: Int? = null,
    val scanMinRssi: Int? = null,
    val scanMaxRssi: Int? = null,
    val bluetoothAdapterState: Int? = null,
    val radioFailureStreak: Int = 0,
    val radioDegradedCluster: Boolean = false,
    val slotStrategy: CollectorSlotStrategy? = null,
    val gattConnectedAt: Long? = null,
    val serviceDiscoveryAt: Long? = null,
    val authStartedAt: Long? = null,
    val authSucceededAt: Long? = null,
    val glucosePacketReceivedAt: Long? = null,
    val sensorAgeSeconds: Long? = null,
    val measurementTimestamp: Long? = null,
    val sequenceNumber: Long? = null,
    val storeCompletedAt: Long? = null,
    val cycleEndedAt: Long? = null,
) {
    val alarmLatenessMs: Long?
        get() = receiverReceivedAt?.let { received -> requestedReconnectEpoch?.let { received - it } }
    val serviceStartLatenessMs: Long?
        get() = serviceOnStartCommandAt?.let { started -> receiverReceivedAt?.let { started - it } }
    val scanStartLatenessMs: Long?
        get() = scanStartedAt?.let { scan -> requestedReconnectEpoch?.let { scan - it } }
    val advertisementLatencyMs: Long?
        get() = advertisementFoundAt?.let { found -> scanStartedAt?.let { found - it } }
    val gattLatencyMs: Long?
        get() = gattConnectedAt?.let { connected -> connectGattStartedAt?.let { connected - it } }
    val totalCycleLatencyMs: Long?
        get() = cycleEndedAt?.let { ended -> receiverReceivedAt?.let { ended - it } }
}

@Serializable
data class CollectorExpectedWindow(
    val expectedWindowId: String,
    val expectedAt: Long,
    val windowCreatedAt: Long? = null,
    val primaryAlarmScheduledAt: Long? = null,
    val primaryAlarmTriggeredAt: Long? = null,
    val cycleStartedAt: Long? = null,
    val advertisementSeenAt: Long? = null,
    val fallbackScanUsed: Boolean = false,
    val scanStartedAt: Long? = null,
    val scanEndedAt: Long? = null,
    val scanResultCount: Int = 0,
    val gattStartedAt: Long? = null,
    val gattGeneration: Long? = null,
    val gattResult: DirectConnectResult? = null,
    val gattAttempts: Int = 0,
    val gatt133Count: Int = 0,
    val noCallbackCount: Int = 0,
    val readingReceivedAt: Long? = null,
    val finalResult: CollectorCycleClassification? = null,
    val recoveryRequired: Boolean = false,
    val watchdogScheduledAt: Long? = null,
    val watchdogTriggeredAt: Long? = null,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val alarmKind: CollectorAlarmKind = CollectorAlarmKind.NONE,
    val alarmDeliveredAt: Long? = null,
    val receiverReachedAt: Long? = null,
    val serviceRequestedAt: Long? = null,
    val serviceStartedAt: Long? = null,
    val wakeLockAcquiredAt: Long? = null,
    val processId: Int? = null,
    val processInstanceId: String? = null,
    val processUptimeMs: Long? = null,
    val bootId: String? = null,
    val attemptId: Long? = null,
    val strategy: CollectorSlotStrategy? = null,
    val retryCount: Int = 0,
    val terminalState: CollectorWindowTerminalState? = null,
    val terminalReason: String? = null,
    val completedAt: Long? = null,
    val gapDetectedAt: Long? = null,
    val nextLiveMeasuredAt: Long? = null,
    val nextLiveReceivedAt: Long? = null,
    val liveCommittedAt: Long? = null,
    val backfillRequestedAt: Long? = null,
    val backfillResponseAt: Long? = null,
    val backfillInsertedAt: Long? = null,
    val recoveredMeasuredAt: Long? = null,
    val recoveryAttemptCount: Int = 0,
    val completeRecoveryMissCount: Int = 0,
    val lastRecoveryAttemptAt: Long? = null,
    val lastRecoveryOutcome: String? = null,
    val gapRecoveryState: CgmGapRecoveryState = CgmGapRecoveryState.OPEN,
    val firstRecoveryOpportunityAt: Long? = null,
    val terminalGapReason: String? = null,
)

@Serializable
data class CollectorHardwareMetrics(
    val expectedWindows: Int = 0,
    val attemptedWindows: Int = 0,
    val successfulWindows: Int = 0,
    val missedWindows: Int = 0,
    val deviceUnavailableWindows: Int = 0,
    val firstAttemptSuccess: Int = 0,
    val retrySuccess: Int = 0,
    val gatt133Count: Int = 0,
    val noCallbackCount: Int = 0,
    val fallbackScanCount: Int = 0,
    val longestReadingGapMs: Long? = null,
    val availabilityPercent: Double = 0.0,
    val medianReceiveDelayMs: Long? = null,
    val p95ReceiveDelayMs: Long? = null,
    val connectAttempts: Int = 0,
    val bleScanTimeMs: Long = 0,
    val wakeLockDurationMs: Long = 0,
    val sensorNotVisibleEpisodes: Int = 0,
    val silentWindows: Int = 0,
    val duplicateCanonicalWindows: Int = 0,
    val recoveredGapCount: Int = 0,
    val backfillWithinTenMinutesCount: Int = 0,
    val backfillWithinTenMinutesPercent: Double = 0.0,
)

@Serializable
data class CollectorSlotSummary(
    val expectedReadingEpoch: Long,
    val attemptId: Long,
    val strategy: CollectorSlotStrategy,
    val directResult: DirectConnectResult? = null,
    val directAttempts: Int = 0,
    val fallbackScanUsed: Boolean = false,
    val scanResultCount: Int? = null,
    val finalClassification: CollectorCycleClassification,
    val readingAgeSeconds: Long? = null,
    val durationMs: Long? = null,
    val radioFailureStreak: Int = 0,
)

@Serializable
data class CollectorDiagnosticEvent(
    val timestampEpochMs: Long,
    val attemptId: Long,
    val stage: CollectorDiagnosticStage,
    val result: CollectorDiagnosticResult,
    val message: String,
    val errorCode: String? = null,
    val sensorId: String? = null,
    val sequence: Long? = null,
    val durationMs: Long? = null,
)

@Serializable
data class CollectorDiagnosticAttempt(
    val attemptId: Long,
    val startedAtEpochMs: Long,
    val processInstanceId: String? = null,
    val lastProgressAtEpochMs: Long = startedAtEpochMs,
    val currentStage: CollectorDiagnosticStage = CollectorDiagnosticStage.IDLE,
    val deadlineEpochMs: Long? = null,
    val manual: Boolean = false,
    val restart: Boolean = false,
    val completedAtEpochMs: Long? = null,
    val result: CollectorDiagnosticResult = CollectorDiagnosticResult.STARTED,
    val summary: String = "Collection-Versuch läuft",
    val events: List<CollectorDiagnosticEvent> = emptyList(),
    val cycle: CollectorCycleTiming? = null,
    val classification: CollectorCycleClassification? = null,
)

@Serializable
data class CgmSensor(
    val sensorId: String,
    val sessionId: String? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val state: CgmSensorState = CgmSensorState.UNKNOWN,
)

@Serializable
data class SensorProtocolReading(
    val sensorId: String,
    val sessionId: String,
    val sequenceNumber: Long?,
    val glucoseMgDl: Double,
    val sensorTimestampEpochMs: Long,
    val receivedAtEpochMs: Long,
    val trendRateMgDlPerMinute: Double? = null,
    val predictedMgDl: Double? = null,
    val sensorAgeSeconds: Long? = null,
    val sensorState: CgmSensorState = CgmSensorState.UNKNOWN,
    val displayOnly: Boolean = false,
    val sensorClockSeconds: Long? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val protocolStatusCode: Int? = null,
    val calibrationStateCode: Int? = null,
    val reservedField: Int? = null,
    val origin: CgmReadingOrigin = CgmReadingOrigin.LIVE,
)

@Serializable
data class CgmCollectorError(
    val code: String,
    val recoverable: Boolean,
    val occurredAtEpochMs: Long,
    val safeMessage: String,
)

@Serializable
data class CgmPersistedState(
    val sensor: CgmSensor? = null,
    val collectorEnabled: Boolean = false,
    val collectorOwner: CollectorOwner = CollectorOwner.UNKNOWN,
    val connectionState: CgmConnectionState = CgmConnectionState.DISCONNECTED,
    val protocolState: CgmProtocolState = CgmProtocolState.UNINITIALIZED,
    val authenticationState: CgmAuthenticationState = CgmAuthenticationState.UNKNOWN,
    val sessionState: CgmSessionState = CgmSessionState.UNINITIALIZED,
    val lastReading: CgmReading? = null,
    val lastSuccessfulConnectionEpochMs: Long? = null,
    val nextReconnectEpochMs: Long? = null,
    val retryCount: Int = 0,
    val lastError: CgmCollectorError? = null,
    val activeAttemptId: Long? = null,
    val scanStartedAtEpochMs: Long? = null,
    val scanTimeoutAtEpochMs: Long? = null,
    val pairingAttemptId: String? = null,
    val pairingStartedAtEpochMs: Long? = null,
    val pairingDeadlineEpochMs: Long? = null,
    val lastScanAtEpochMs: Long? = null,
    val lastAttemptCompletedAtEpochMs: Long? = null,
    val health: CgmCollectorHealth = CgmCollectorHealth(),
)
