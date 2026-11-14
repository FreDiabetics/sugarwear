package app.sugarlicious.protocol

import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.DiagnosticBatch
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WearEnvelope(
    val protocolVersion: Int = CURRENT,
    val state: TherapyDisplayState,
    val generatedAtEpochMs: Long = state.receivedAtEpochMs,
    val eventId: String = eventIdFor(state),
    val source: DataSourceId = state.source,
    val sensorId: String? = state.glucose?.sensorId,
    val sessionId: String? = state.glucose?.sessionId,
) {
    companion object {
        const val CURRENT = 8

        fun eventIdFor(state: TherapyDisplayState): String =
            listOf(
                state.source.name,
                state.glucose?.sensorId.orEmpty(),
                state.glucose?.sessionId.orEmpty(),
                state.glucose?.measuredAtEpochMs ?: 0L,
                state.receivedAtEpochMs,
                state.canonicalRevision,
            ).joinToString(":")
    }
}

@Serializable
enum class WatchGlucoseUnit {
    AAPS,
    MG_DL,
    MMOL_L,
}

@Serializable
enum class WatchDataSource {
    AUTOMATIC,
    PHONE,
    DEXCOM_G7_WATCH,
}

@Serializable
data class CgmSetupCommand(
    val pairingCode: String,
    val sensorSerial: String? = null,
    val gtin: String? = null,
) {
    init {
        require(pairingCode.length == 4 && pairingCode.all(Char::isDigit))
    }
}

@Serializable
data class WatchGraphColors(
    val graphBackground: Int = 0xFF202020.toInt(),
    val rangeLow: Int = 0xFFFF5C69.toInt(),
    val rangeInRange: Int = 0xFF54DF30.toInt(),
    val rangeHigh: Int = 0xFFFFD040.toInt(),
    val cgmLow: Int = 0xFFFF5C69.toInt(),
    val cgmInRange: Int = 0xFF54DF30.toInt(),
    val cgmHigh: Int = 0xFFFFD040.toInt(),
    val cgmVeryLow: Int = cgmLow,
    val cgmVeryHigh: Int = cgmHigh,
    val divider: Int = 0xFF969696.toInt(),
    val border: Int = divider,
    val highLine: Int = rangeHigh,
    val lowLine: Int = rangeLow,
    val axisLabel: Int = divider,
    val axisTick: Int = divider,
    val nowLine: Int = divider,
    val outline: Int = 0xFF000000.toInt(),
    val predictionIob: Int = 0xFF52C1FF.toInt(),
    val predictionCob: Int = 0xFFF4DE00.toInt(),
    val predictionUam: Int = 0xFFFFAE1F.toInt(),
    val predictionZeroTemp: Int = 0xFF30DBDE.toInt(),
    val targetValue: Int = 0xFFF5F5F5.toInt(),
    val signalLoss: Int = 0x46FF5C69,
)

object DirectToWatchGraphColorDefaults {
    fun create() =
        WatchGraphColors(
            graphBackground = -14935012,
            cgmLow = 0xFFFFFFFF.toInt(),
            cgmVeryLow = 0xFFFFFFFF.toInt(),
            cgmInRange = 0xFFFFFFFF.toInt(),
            cgmHigh = 0xFFFFFFFF.toInt(),
            cgmVeryHigh = 0xFFFFFFFF.toInt(),
            highLine = 0xFFFFD040.toInt(),
            lowLine = 0xFFFF5C69.toInt(),
            rangeInRange = 1717328988,
            rangeHigh = 1308610624,
            rangeLow = 1308580969,
        )
}

@Serializable
data class WatchColorSync(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val graphColors: WatchGraphColors,
    val lightProfile: WatchAppearanceProfile? = null,
    val darkProfile: WatchAppearanceProfile? = null,
    val cgmThresholds: CgmThresholds = CgmThresholds.DEFAULT,
    val sentAtEpochMs: Long,
) {
    companion object {
        const val CURRENT_SCHEMA = 4
    }
}

@Serializable
data class WatchAppearanceProfile(
    val graphColors: WatchGraphColors = WatchGraphColors(),
    val graphStyle: WatchGraphStyle = WatchGraphStyle(),
    val uiColors: WatchUiColors = WatchUiColors(),
)

@Serializable
data class WatchUiColors(
    val background: Int = 0xFF181818.toInt(),
    val tileBackground: Int = 0xFF242424.toInt(),
    val tileBorder: Int = 0xFF404040.toInt(),
    val textPrimary: Int = 0xFFF5F5F5.toInt(),
    val textSecondary: Int = 0xFFB5B5B5.toInt(),
    val deltaUnit: Int = textSecondary,
    val accent: Int = 0xFF6DE892.toInt(),
    val glucoseLow: Int = 0xFFFF5C69.toInt(),
    val glucoseInRange: Int = 0xFFF5F5F5.toInt(),
    val glucoseHigh: Int = 0xFFFFD040.toInt(),
    val glucoseVeryLow: Int = glucoseLow,
    val glucoseVeryHigh: Int = glucoseHigh,
    val iob: Int = 0xFF64BFFF.toInt(),
    val cob: Int = 0xFFFF9D18.toInt(),
    val basal: Int = 0xFF19D7E8.toInt(),
    // Explicit Mobile progress-bar color roles, kept independent of graph target-area colors.
    val glucoseBarLow: Int = 0xFFF73D45.toInt(),
    val glucoseBarTarget: Int = 0xFF22EB60.toInt(),
    val glucoseBarHigh: Int = 0xFFFCC93B.toInt(),
    val glucoseBarUnderlay: Int = 0x4DB0B0B0,
)

@Serializable
data class WatchGraphStyle(
    val cgmDotRadiusDp: Float = 2.4f,
    /** Legacy master switch retained for backwards-compatible config decoding. */
    val cgmDotOutlineEnabled: Boolean = true,
    val cgmHistoricalDotOutlineEnabled: Boolean = true,
    val cgmCurrentDotOutlineEnabled: Boolean = true,
    val cgmDotOutlineWidthDp: Float = 0.95f,
    val cgmHistoricalDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
    val cgmCurrentDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
    val scaleLaneOpacityPercent: Int = 30,
    val borderEnabled: Boolean = false,
)

@Serializable
data class WatchRuntimeStatus(
    val activeSugarliciousFaceIndex: Int? = null,
    val activeComplicationIds: List<Int> = emptyList(),
    val sentAtEpochMs: Long = 0L,
)

@Serializable
data class WatchConfig(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val graphHours: Int = 3,
    val showPredictions: Boolean = false,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val dataSource: WatchDataSource = WatchDataSource.AUTOMATIC,
    val showTherapyStats: Boolean = true,
    val graphColors: WatchGraphColors = WatchGraphColors(),
    val graphStyle: WatchGraphStyle = WatchGraphStyle(),
    val uiColors: WatchUiColors = WatchUiColors(),
    val cgmThresholds: CgmThresholds = CgmThresholds.DEFAULT,
    val sentAtEpochMs: Long = 0L,
) {
    companion object {
        const val CURRENT_SCHEMA = 10
    }
}

object WearProtocol {
    const val MAX_STATE_PAYLOAD_BYTES = 90_000
    const val STATE_PATH = "/aaps-display/v1/state"
    const val REQUEST_PATH = "/aaps-display/v1/request"
    const val WATCH_CONFIG_PATH = "/aaps-display/v1/watch-config"
    const val WATCH_CONFIG_REQUEST_PATH = "/aaps-display/v1/watch-config-request"
    const val COMPLICATION_PRESET_PATH = "/aaps-display/v1/complication-preset"
    const val COMPLICATION_APPEARANCE_PATH = "/aaps-display/v1/complication-appearance"
    const val WATCH_FACE_APPLY_PATH = "/aaps-display/v1/watchface-apply"
    const val WATCH_FACE_STATUS_PATH = "/aaps-display/v1/watchface-status"
    const val WATCH_RUNTIME_STATUS_PATH = "/aaps-display/v1/watch-runtime-status"
    const val WATCH_RUNTIME_REQUEST_PATH = "/aaps-display/v1/watch-runtime-request"
    const val CGM_SETUP_PATH = "/aaps-display/v1/g7-setup"
    const val WATCH_COLOR_SYNC_PATH = "/aaps-display/v1/watch-color-sync"
    const val DIAGNOSTICS_REQUEST_PATH = "/aaps-display/v1/diagnostics-request"
    const val DIAGNOSTICS_BATCH_PATH = "/aaps-display/v1/diagnostics-batch"
    const val SUGARLICIOUS_WATCH_FACE_MAX_INDEX = 5

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun encode(
        state: TherapyDisplayState,
        generatedAtEpochMs: Long = System.currentTimeMillis(),
    ): ByteArray =
        json
            .encodeToString(
                WearEnvelope(state = state, generatedAtEpochMs = generatedAtEpochMs),
            ).encodeToByteArray()

    /**
     * Builds a Wear Data Layer safe state without changing the locally persisted Mobile model.
     * Google Play services rejects DataItems around 100 KiB with DATA_ITEM_TOO_LARGE. Histories
     * grow during normal use, so transporting the unbounded persistence object is unsafe.
     */
    fun encodeStateForTransport(
        state: TherapyDisplayState,
        maxBytes: Int = MAX_STATE_PAYLOAD_BYTES,
    ): ByteArray {
        require(maxBytes > 0)
        var transport =
            state.copy(
                // These raw AAPS JSON documents are diagnostic input, not Wear presentation data,
                // and can each be larger than the complete Data Layer allowance.
                loop = state.loop?.copy(suggestedPayload = null, enactedPayload = null),
                glucoseHistory = state.glucoseHistory.takeLast(288),
                therapyHistory = state.therapyHistory.takeLast(288),
                therapyEvents = state.therapyEvents.takeLast(180),
                targetHistory = state.targetHistory.takeLast(96),
                glucosePredictions = state.glucosePredictions.map { it.copy(samples = it.samples.takeLast(72)) },
            )
        var payload = encode(transport)
        while (payload.size > maxBytes) {
            transport =
                when {
                    transport.therapyHistory.size > 72 ->
                        transport.copy(
                            therapyHistory = transport.therapyHistory.drop(oldestQuarter(transport.therapyHistory.size, 72)),
                        )
                    transport.therapyEvents.size > 40 ->
                        transport.copy(
                            therapyEvents = transport.therapyEvents.drop(oldestQuarter(transport.therapyEvents.size, 40)),
                        )
                    transport.targetHistory.size > 24 ->
                        transport.copy(
                            targetHistory = transport.targetHistory.drop(oldestQuarter(transport.targetHistory.size, 24)),
                        )
                    transport.glucoseHistory.size > 72 ->
                        transport.copy(
                            glucoseHistory = transport.glucoseHistory.drop(oldestQuarter(transport.glucoseHistory.size, 72)),
                        )
                    transport.glucosePredictions.any { it.samples.size > 12 } ->
                        transport.copy(
                            glucosePredictions =
                                transport.glucosePredictions.map { prediction ->
                                    prediction.copy(
                                        samples = prediction.samples.takeLast((prediction.samples.size * 3 / 4).coerceAtLeast(12)),
                                    )
                                },
                        )
                    else -> throw IllegalArgumentException("Wear state cannot fit into $maxBytes bytes")
                }
            payload = encode(transport)
        }
        return payload
    }

    private fun oldestQuarter(
        size: Int,
        minimum: Int,
    ): Int = (size / 4).coerceAtLeast(1).coerceAtMost(size - minimum)

    fun decode(bytes: ByteArray): TherapyDisplayState {
        val envelope = decodeEnvelope(bytes)
        return migrate(envelope.state)
    }

    fun decodeEnvelope(bytes: ByteArray): WearEnvelope {
        val envelope = json.decodeFromString<WearEnvelope>(bytes.decodeToString())
        require(envelope.protocolVersion in 1..WearEnvelope.CURRENT)
        return envelope.copy(state = migrate(envelope.state))
    }

    fun encodeConfig(config: WatchConfig): ByteArray = json.encodeToString(config).encodeToByteArray()

    fun encodeRuntimeStatus(status: WatchRuntimeStatus): ByteArray = json.encodeToString(status).encodeToByteArray()

    fun encodeCgmSetup(command: CgmSetupCommand): ByteArray = json.encodeToString(command).encodeToByteArray()

    fun encodeWatchColorSync(sync: WatchColorSync): ByteArray = json.encodeToString(sync).encodeToByteArray()

    fun decodeWatchColorSync(bytes: ByteArray): WatchColorSync {
        val decoded = json.decodeFromString<WatchColorSync>(bytes.decodeToString())
        require(decoded.schemaVersion in 1..WatchColorSync.CURRENT_SCHEMA)
        return decoded
    }

    fun decodeCgmSetup(bytes: ByteArray): CgmSetupCommand = json.decodeFromString<CgmSetupCommand>(bytes.decodeToString())

    fun encodeDiagnostics(batch: DiagnosticBatch): ByteArray = json.encodeToString(batch).encodeToByteArray()

    fun decodeDiagnostics(bytes: ByteArray): DiagnosticBatch {
        val decoded = json.decodeFromString<DiagnosticBatch>(bytes.decodeToString())
        return decoded.copy(events = decoded.events.takeLast(1_000))
    }

    fun decodeRuntimeStatus(bytes: ByteArray): WatchRuntimeStatus {
        val decoded = json.decodeFromString<WatchRuntimeStatus>(bytes.decodeToString())
        return decoded.copy(
            activeSugarliciousFaceIndex =
                decoded.activeSugarliciousFaceIndex?.coerceIn(0, SUGARLICIOUS_WATCH_FACE_MAX_INDEX),
            activeComplicationIds = decoded.activeComplicationIds.distinct().take(12),
        )
    }

    fun decodeConfig(bytes: ByteArray): WatchConfig {
        val decoded = json.decodeFromString<WatchConfig>(bytes.decodeToString())
        return decoded.copy(
            graphHours = decoded.graphHours.takeIf { it in listOf(1, 2, 3, 6, 12, 24) } ?: 3,
            graphStyle =
                decoded.graphStyle.copy(
                    cgmDotRadiusDp = decoded.graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f),
                    cgmDotOutlineWidthDp = decoded.graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f),
                ),
        )
    }

    private fun migrate(state: TherapyDisplayState): TherapyDisplayState {
        if (state.schemaVersion >= TherapyDisplayState.CURRENT_SCHEMA) return state

        val legacyContract =
            state.sourceContract
                ?: state.sourceVersion?.takeIf { it.startsWith("AAPS_") }
        val actualSourceVersion =
            state.sourceVersion?.takeUnless { it.startsWith("AAPS_") }

        return state.copy(
            schemaVersion = TherapyDisplayState.CURRENT_SCHEMA,
            sourceVersion = actualSourceVersion,
            sourceContract = legacyContract,
        )
    }
}
