package app.sugarlicious.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.protocol.WatchAppearanceProfile
import app.sugarlicious.protocol.WatchColorSync
import app.sugarlicious.protocol.WatchConfig
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.protocol.WatchGlucoseUnit
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.protocol.WatchUiColors
import app.sugarlicious.protocol.WearProtocol
import app.sugarlicious.storage.CanonicalStateStore
import app.sugarlicious.storage.DiagnosticEventStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MobileDataLayerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onMessageReceived(event: MessageEvent) {
        recordWatchContact(applicationContext)
        when (event.path) {
            WearProtocol.REQUEST_PATH -> {
                scope.launch {
                    applicationContext.recordMobileDiagnostic("SYNC", "SYNC-WATCH-100", "Watch requested current state")
                    val phoneState = CanonicalStateStore(this@MobileDataLayerService).reconcile()
                    runCatching {
                        phoneState?.let { publishState(this@MobileDataLayerService, it) }
                        publishWatchConfig(this@MobileDataLayerService)
                    }.onFailure { error ->
                        applicationContext.recordMobileDiagnostic(
                            "SYNC",
                            "SYNC-REQUEST-503",
                            "Watch request could not be fulfilled",
                            DiagnosticSeverity.WARNING,
                            metadata = mapOf("error" to error.javaClass.simpleName),
                        )
                    }
                }
            }

            WearProtocol.WATCH_CONFIG_REQUEST_PATH -> {
                scope.launch {
                    applicationContext.recordMobileDiagnostic("SYNC", "SYNC-CONFIG-101", "Watch requested display configuration")
                    runCatching { publishWatchConfig(this@MobileDataLayerService) }
                        .onFailure { error ->
                            applicationContext.recordMobileDiagnostic(
                                "SYNC",
                                "SYNC-CONFIG-503",
                                "Watch configuration request failed",
                                DiagnosticSeverity.WARNING,
                                metadata = mapOf("error" to error.javaClass.simpleName),
                            )
                        }
                }
            }
            WearProtocol.WATCH_FACE_STATUS_PATH -> {
                val message = event.data.decodeToString()
                Handler(Looper.getMainLooper()).post {
                    Toast
                        .makeText(
                            applicationContext,
                            message,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
            WearProtocol.WATCH_RUNTIME_STATUS_PATH -> {
                runCatching { WearProtocol.decodeRuntimeStatus(event.data) }
                    .onSuccess {
                        WatchRuntimeStatusStore.save(applicationContext, it)
                        scope.launch {
                            applicationContext.recordMobileDiagnostic(
                                "WATCH",
                                "WATCH-STATUS-200",
                                "Watch runtime status received",
                                metadata =
                                    mapOf(
                                        "complications" to it.activeComplicationIds.size,
                                        "watchface" to it.activeSugarliciousFaceIndex,
                                    ),
                            )
                        }
                    }.onFailure {
                        scope.launch {
                            applicationContext.recordMobileDiagnostic(
                                "WATCH",
                                "WATCH-STATUS-401",
                                "Invalid Watch runtime status",
                                DiagnosticSeverity.WARNING,
                            )
                        }
                    }
            }

            WearProtocol.DIAGNOSTICS_BATCH_PATH -> {
                scope.launch {
                    runCatching { WearProtocol.decodeDiagnostics(event.data) }
                        .onSuccess { batch ->
                            DiagnosticEventStore(applicationContext).append(batch.events)
                            applicationContext.recordMobileDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-200",
                                "Watch diagnostics received",
                                metadata = mapOf("eventCount" to batch.events.size),
                            )
                        }.onFailure {
                            applicationContext.recordMobileDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-401",
                                "Watch diagnostics could not be decoded",
                                DiagnosticSeverity.WARNING,
                            )
                        }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal fun readWatchConfig(context: Context): WatchConfig {
    val preferences =
        context.getSharedPreferences(
            "dashboard_ui",
            Context.MODE_PRIVATE,
        )

    val unit =
        runCatching {
            WatchGlucoseUnit.valueOf(
                preferences.getString("unit", WatchGlucoseUnit.AAPS.name)
                    ?: WatchGlucoseUnit.AAPS.name,
            )
        }.getOrDefault(WatchGlucoseUnit.AAPS)

    val palette = SugarliciousColorStore.load(preferences)
    return WatchConfig(
        graphHours =
            preferences
                .getInt("graphHours", 3)
                .takeIf { it in listOf(1, 2, 3, 6, 12, 24) }
                ?: 3,
        showPredictions =
            listOf(
                "cgm.prediction.iob",
                "cgm.prediction.cob",
                "cgm.prediction.uam",
                "cgm.prediction.zeroTemp",
            ).any { preferences.getBoolean(it, false) },
        glucoseUnit = unit,
        // Mobile is intentionally AndroidAPS-only. Do not leak a removed legacy source choice
        // into Wear configuration before the next AAPS payload has had a chance to migrate it.
        dataSource = WatchDataSource.PHONE,
        showTherapyStats = preferences.getBoolean("showDetails", true),
        graphColors =
            WatchGraphColors(
                graphBackground = palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND),
                rangeLow = palette.argb(SugarliciousColorRole.RANGE_LOW),
                rangeInRange = palette.argb(SugarliciousColorRole.RANGE_IN_RANGE),
                rangeHigh = palette.argb(SugarliciousColorRole.RANGE_HIGH),
                cgmLow = palette.argb(SugarliciousColorRole.CGM_DOT_LOW),
                cgmInRange = palette.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE),
                cgmHigh = palette.argb(SugarliciousColorRole.CGM_DOT_HIGH),
                cgmVeryLow = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_LOW),
                cgmVeryHigh = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
                divider = palette.argb(SugarliciousColorRole.GRAPH_DIVIDER),
                border = palette.argb(SugarliciousColorRole.GRAPH_BORDER),
                highLine = palette.argb(SugarliciousColorRole.GRAPH_HIGH_LINE),
                lowLine = palette.argb(SugarliciousColorRole.GRAPH_LOW_LINE),
                axisLabel = palette.argb(SugarliciousColorRole.GRAPH_LABEL),
                axisTick = palette.argb(SugarliciousColorRole.GRAPH_AXIS_TICK),
                nowLine = palette.argb(SugarliciousColorRole.GRAPH_NOW_LINE),
                outline = palette.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE),
                predictionIob = palette.argb(SugarliciousColorRole.PREDICTION_IOB),
                predictionCob = palette.argb(SugarliciousColorRole.PREDICTION_COB),
                predictionUam = palette.argb(SugarliciousColorRole.PREDICTION_UAM),
                predictionZeroTemp = palette.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP),
                targetValue = palette.argb(SugarliciousColorRole.TARGET_VALUE),
                signalLoss = palette.argb(SugarliciousColorRole.GRAPH_SIGNAL_LOSS),
            ),
        graphStyle = readMobileGraphStyle(preferences),
        uiColors =
            WatchUiColors(
                background = palette.argb(SugarliciousColorRole.BACKGROUND),
                tileBackground = palette.argb(SugarliciousColorRole.SURFACE),
                tileBorder = palette.argb(SugarliciousColorRole.BORDER),
                textPrimary = palette.argb(SugarliciousColorRole.TEXT_PRIMARY),
                textSecondary = palette.argb(SugarliciousColorRole.TEXT_SECONDARY),
                deltaUnit = palette.argb(SugarliciousColorRole.DELTA_UNIT),
                accent = palette.argb(SugarliciousColorRole.PRIMARY),
                glucoseLow = palette.argb(SugarliciousColorRole.GLUCOSE_LOW),
                glucoseInRange = palette.argb(SugarliciousColorRole.GLUCOSE_IN_RANGE),
                glucoseHigh = palette.argb(SugarliciousColorRole.GLUCOSE_HIGH),
                glucoseVeryLow = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_LOW),
                glucoseVeryHigh = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
                glucoseBarLow = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_LOW),
                glucoseBarTarget = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_IN_RANGE),
                glucoseBarHigh = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_HIGH),
                glucoseBarUnderlay = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_UNDERLAY),
                iob = palette.argb(SugarliciousColorRole.THERAPY_IOB_PROGRESS),
                cob = palette.argb(SugarliciousColorRole.THERAPY_COB_PROGRESS),
                basal = palette.argb(SugarliciousColorRole.THERAPY_BASAL_PROGRESS),
            ),
        cgmThresholds = CgmThresholdPreferences.read(preferences),
        sentAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun readWatchAppearanceProfile(
    context: Context,
    mode: AppearanceMode,
): WatchAppearanceProfile {
    val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
    val palette = SugarliciousColorStore.load(preferences, mode)
    return WatchAppearanceProfile(
        graphColors =
            WatchGraphColors(
                graphBackground = palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND),
                rangeLow = palette.argb(SugarliciousColorRole.RANGE_LOW),
                rangeInRange = palette.argb(SugarliciousColorRole.RANGE_IN_RANGE),
                rangeHigh = palette.argb(SugarliciousColorRole.RANGE_HIGH),
                cgmLow = palette.argb(SugarliciousColorRole.CGM_DOT_LOW),
                cgmInRange = palette.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE),
                cgmHigh = palette.argb(SugarliciousColorRole.CGM_DOT_HIGH),
                cgmVeryLow = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_LOW),
                cgmVeryHigh = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
                divider = palette.argb(SugarliciousColorRole.GRAPH_DIVIDER),
                border = palette.argb(SugarliciousColorRole.GRAPH_BORDER),
                highLine = palette.argb(SugarliciousColorRole.GRAPH_HIGH_LINE),
                lowLine = palette.argb(SugarliciousColorRole.GRAPH_LOW_LINE),
                axisLabel = palette.argb(SugarliciousColorRole.GRAPH_LABEL),
                axisTick = palette.argb(SugarliciousColorRole.GRAPH_AXIS_TICK),
                nowLine = palette.argb(SugarliciousColorRole.GRAPH_NOW_LINE),
                outline = palette.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE),
                predictionIob = palette.argb(SugarliciousColorRole.PREDICTION_IOB),
                predictionCob = palette.argb(SugarliciousColorRole.PREDICTION_COB),
                predictionUam = palette.argb(SugarliciousColorRole.PREDICTION_UAM),
                predictionZeroTemp = palette.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP),
                targetValue = palette.argb(SugarliciousColorRole.TARGET_VALUE),
                signalLoss = palette.argb(SugarliciousColorRole.GRAPH_SIGNAL_LOSS),
            ),
        graphStyle = readMobileGraphStyle(preferences, mode),
        uiColors =
            WatchUiColors(
                background = palette.argb(SugarliciousColorRole.BACKGROUND),
                tileBackground = palette.argb(SugarliciousColorRole.SURFACE),
                tileBorder = palette.argb(SugarliciousColorRole.BORDER),
                textPrimary = palette.argb(SugarliciousColorRole.TEXT_PRIMARY),
                textSecondary = palette.argb(SugarliciousColorRole.TEXT_SECONDARY),
                deltaUnit = palette.argb(SugarliciousColorRole.DELTA_UNIT),
                accent = palette.argb(SugarliciousColorRole.PRIMARY),
                glucoseLow = palette.argb(SugarliciousColorRole.GLUCOSE_LOW),
                glucoseInRange = palette.argb(SugarliciousColorRole.GLUCOSE_IN_RANGE),
                glucoseHigh = palette.argb(SugarliciousColorRole.GLUCOSE_HIGH),
                glucoseVeryLow = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_LOW),
                glucoseVeryHigh = palette.argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
                glucoseBarLow = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_LOW),
                glucoseBarTarget = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_IN_RANGE),
                glucoseBarHigh = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_HIGH),
                glucoseBarUnderlay = palette.argb(SugarliciousColorRole.OVERVIEW_WAVE_UNDERLAY),
                iob = palette.argb(SugarliciousColorRole.THERAPY_IOB_PROGRESS),
                cob = palette.argb(SugarliciousColorRole.THERAPY_COB_PROGRESS),
                basal = palette.argb(SugarliciousColorRole.THERAPY_BASAL_PROGRESS),
            ),
    )
}

internal suspend fun publishWatchConfig(context: Context) {
    val request =
        PutDataRequest
            .create(WearProtocol.WATCH_CONFIG_PATH)
            .setData(
                WearProtocol.encodeConfig(
                    readWatchConfig(context),
                ),
            ).setUrgent()

    context.putLoggedWearData(request, "Watch-Konfiguration")
}

internal suspend fun publishWatchColors(context: Context) {
    val active = readWatchConfig(context)
    val light = readWatchAppearanceProfile(context, AppearanceMode.LIGHT)
    val dark = readWatchAppearanceProfile(context, AppearanceMode.DARK)
    val request =
        PutDataRequest
            .create(WearProtocol.WATCH_COLOR_SYNC_PATH)
            .setData(
                WearProtocol.encodeWatchColorSync(
                    WatchColorSync(
                        graphColors = active.graphColors,
                        lightProfile = light,
                        darkProfile = dark,
                        cgmThresholds = CgmThresholdPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
                        sentAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            ).setUrgent()
    context.putLoggedWearData(request, "Watch-Farben")
}

internal suspend fun requestWatchRuntimeStatus(context: Context) {
    refreshReachableWatchNodeIds(context).forEach { nodeId ->
        context.sendLoggedWearMessage(nodeId, WearProtocol.WATCH_RUNTIME_REQUEST_PATH, byteArrayOf(), "Watch-Status anfordern")
    }
}
