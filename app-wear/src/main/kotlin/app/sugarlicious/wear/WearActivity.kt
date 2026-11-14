package app.sugarlicious.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.model.BasalState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.GlucoseAgeText
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyBridgeDisplayField
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.model.WearGlucoseCardInput
import app.sugarlicious.model.WearGlucoseCardStyle
import app.sugarlicious.model.wearGlucoseCardPresentation
import app.sugarlicious.protocol.WatchGlucoseUnit
import app.sugarlicious.uishared.TrendDrawableResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import app.sugarlicious.complications.R as ComplicationR

internal fun nextWearGraphHours(current: Int): Int {
    val values = WearDisplayPreferences.allowedGraphHours
    val index = values.indexOf(current)
    return values[if (index < 0) 0 else (index + 1) % values.size]
}

internal fun wearGraphScaleAgeText(
    graphHours: Int,
    measuredAtEpochMs: Long?,
    nowEpochMs: Long,
): String {
    val age =
        measuredAtEpochMs
            ?.takeIf { it <= nowEpochMs + 60_000L }
            ?.let { "${((nowEpochMs - it).coerceAtLeast(0L) / 60_000L)}m" }
            ?: "—"
    return "${graphHours}h • $age"
}

@dagger.hilt.android.AndroidEntryPoint
class WearActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { granted ->
            notificationPermissionResult(granted)
        }
    private val watchFacePermission =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) {
            renderWatchFacePushStatus()
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val overviewModel by viewModels<WearOverviewViewModel>()
    private var lastRenderedState: TherapyDisplayState? = null
    private var lastRenderedPreferences: WearDisplayPreferences? = null
    private var lastRenderedConnectedNodes: Int? = null
    private var lastRenderedNowEpochMs: Long? = null
    private var hasRendered = false
    private var batteryRequestPending = false

    private lateinit var glucose: TextView
    private lateinit var glucoseRangeBar: WearGlucoseRangeBarView
    private lateinit var trendContainer: LinearLayout
    private lateinit var trendArrow1: ImageView
    private lateinit var delta: TextView
    private lateinit var age: TextView
    private lateinit var source: TextView
    private lateinit var connection: TextView
    private lateinit var syncHint: TextView
    private lateinit var iob: TextView
    private lateinit var cob: TextView
    private lateinit var basal: TextView
    private lateinit var iobIcon: ImageView
    private lateinit var cobIcon: ImageView
    private lateinit var basalIcon: ImageView
    private lateinit var therapyRow: LinearLayout
    private lateinit var chart: WearGlucoseChart
    private lateinit var watchFacePushStatus: TextView
    private var currentBasalIconRes: Int = ComplicationR.drawable.ic_complication_basal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { StateDataLayerService.start(this) }
            .onFailure { error ->
                recordRuntimeDiagnostic(
                    "WATCH-FGS-500",
                    "Wear runtime foreground service could not be started from the app",
                    DiagnosticSeverity.ERROR,
                    error,
                )
            }

        // Keep the approved round-screen XML/chart geometry inside the Compose lifecycle host.
        val overview = layoutInflater.inflate(R.layout.activity_wear, android.widget.FrameLayout(this), false)
        configureTherapyOverviewLayout(overview.findViewById(R.id.wear_root))
        app.sugarlicious.uishared.SystemTypography
            .applyDeclaredWeights(overview)
        bindViews(overview)
        overview.findViewById<View>(R.id.wear_connection_card).setOnClickListener { overviewModel.refresh() }
        overview.findViewById<View>(R.id.wear_settings_action).setOnClickListener {
            startActivity(Intent(this, WearSettingsActivity::class.java))
        }
        overview.findViewById<View>(R.id.wear_graph_period).apply {
            setOnClickListener { overviewModel.advanceGraphPeriod() }
            bringToFront()
        }
        setContent {
            val state by overviewModel.uiState.collectAsStateWithLifecycle()
            AndroidView(factory = { overview }, modifier = Modifier.fillMaxSize(), update = { render(it, state) })
        }

        val notificationRequestStarted = requestRuntimeNotificationPermission()
        if (!notificationRequestStarted) {
            val batteryRequestStarted = requestBatteryExemptionIfNeeded()
            if (!batteryRequestStarted) requestWatchFacePermissionOnFirstLaunch()
        }
        overviewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!batteryRequestPending) return

        batteryRequestPending = false
        val unrestricted = WearBackgroundAccess.isBatteryUnrestricted(this)
        if (unrestricted) {
            Toast.makeText(this, "Dauerbetrieb ist uneingeschränkt", Toast.LENGTH_SHORT).show()
            recordRuntimeDiagnostic(
                "WATCH-BG-200",
                "Battery optimization exemption granted for Sugarlicious Wear",
                DiagnosticSeverity.INFO,
            )
        } else {
            Toast
                .makeText(
                    this,
                    "Dauerbetrieb nicht freigegeben – Akkuoptimierung ist weiterhin aktiv",
                    Toast.LENGTH_LONG,
                ).show()
            recordRuntimeDiagnostic(
                "WATCH-BG-403",
                "Battery optimization exemption was not granted for Sugarlicious Wear",
                DiagnosticSeverity.WARNING,
            )
        }
        runCatching { StateDataLayerService.start(this) }
        requestWatchFacePermissionOnFirstLaunch()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews(root: View) {
        glucose = root.findViewById(R.id.wear_glucose)
        trendContainer = root.findViewById(R.id.wear_trend_container)
        trendArrow1 = root.findViewById(R.id.wear_trend_arrow_1)
        delta = root.findViewById(R.id.wear_delta)
        age = root.findViewById(R.id.wear_age)
        // Reuse the existing Wear layout host; add the shared glucose bar in Kotlin.
        val infoBlock = root.findViewById<LinearLayout>(R.id.wear_glucose_info_block)
        val metaPill = root.findViewById<LinearLayout>(R.id.wear_glucose_meta_row)
        metaPill.removeView(age)
        metaPill.addView(age, 0)
        val density = resources.displayMetrics.density

        fun pixels(dp: Float): Int = (dp * density).roundToInt()

        val pillParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        pillParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
        metaPill.layoutParams = pillParams
        metaPill.setPadding(pixels(14f), pixels(8f), pixels(14f), pixels(8f))
        glucoseRangeBar = WearGlucoseRangeBarView(this)
        val barParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pixels(11f))
        barParams.topMargin = pixels(9f)
        barParams.bottomMargin = pixels(12f)
        infoBlock.addView(glucoseRangeBar, 1, barParams)
        source = root.findViewById(R.id.wear_source)
        connection = root.findViewById(R.id.wear_connection)
        syncHint = root.findViewById(R.id.wear_sync_hint)
        iob = root.findViewById(R.id.wear_iob)
        cob = root.findViewById(R.id.wear_cob)
        basal = root.findViewById(R.id.wear_basal)
        iobIcon = root.findViewById(R.id.wear_iob_icon)
        cobIcon = root.findViewById(R.id.wear_cob_icon)
        basalIcon = root.findViewById(R.id.wear_basal_icon)
        therapyRow = root.findViewById(R.id.wear_therapy_row)
        chart = root.findViewById(R.id.wear_glucose_chart)
        watchFacePushStatus = root.findViewById(R.id.wear_watchface_push_status)
    }

    private fun render(root: View, uiState: WearOverviewUiState) {
        if (!::glucose.isInitialized) return

        val now = uiState.nowEpochMs
        val preferences = uiState.snapshot.preferences
        // Retain the last snapshot for recovery, but never display it as current after storage failure.
        val state = uiState.displayTherapy
        val connectedNodes = uiState.connectedNodes
        val refreshClock = lastRenderedNowEpochMs != now
        val previousState = lastRenderedState
        val glucoseState = state?.glucose
        val previousPreferences = lastRenderedPreferences
        val firstRender = !hasRendered

        if (firstRender || previousPreferences == null || previousPreferences.uiColors != preferences.uiColors || previousPreferences.graphStyle.borderEnabled != preferences.graphStyle.borderEnabled) {
            applyUiColors(root, preferences)
        }

        val thresholds = preferences.cgmThresholds
        val resolvedUnit = resolveUnit(glucoseState?.displayUnit, preferences.glucoseUnit)
        val glucosePresentation =
            wearGlucoseCardPresentation(
                WearGlucoseCardInput(
                    valueMgDl = glucoseState?.valueMgDl,
                    displayUnit = resolvedUnit,
                    deltaMgDl = glucoseState?.deltaMgDl,
                    trend = glucoseState?.trend ?: app.sugarlicious.model.Trend.UNKNOWN,
                    measuredAtEpochMs = glucoseState?.measuredAtEpochMs,
                    quality = glucoseState?.quality ?: app.sugarlicious.model.CgmQuality.INVALID,
                    sourceLabel = TherapyDisplayFormatter.sourceName(state?.source),
                ),
                thresholds,
                now,
            )
        val canShowValue = glucosePresentation.displayable

        val glucoseSectionChanged =
            firstRender ||
                refreshClock ||
                previousState?.glucose != glucoseState ||
                previousState?.target != state?.target ||
                previousState?.source != state?.source ||
                previousPreferences == null ||
                previousPreferences.glucoseUnit != preferences.glucoseUnit ||
                previousPreferences.glucoseScalePercent != preferences.glucoseScalePercent ||
                previousPreferences.trendScalePercent != preferences.trendScalePercent ||
                previousPreferences.uiColors != preferences.uiColors ||
                previousPreferences.graphStyle.borderEnabled != preferences.graphStyle.borderEnabled

        if (glucoseSectionChanged) {
            val presentation = glucosePresentation
            glucose.text = presentation.value
            glucose.textSize = WearGlucoseCardStyle.VALUE_TEXT_SP * GlucoseTrendSizing.scaleFactor(preferences.glucoseScalePercent)
            val valueColor =
                when {
                    presentation.freshness == app.sugarlicious.model.Freshness.SIGNAL_LOSS -> 0xFF9E9E9E.toInt()
                    !presentation.displayable -> preferences.uiColors.textPrimary
                    presentation.rangeClass == app.sugarlicious.model.CgmRangeClass.VERY_LOW -> preferences.uiColors.glucoseVeryLow
                    presentation.rangeClass == app.sugarlicious.model.CgmRangeClass.LOW -> preferences.uiColors.glucoseLow
                    presentation.rangeClass == app.sugarlicious.model.CgmRangeClass.VERY_HIGH -> preferences.uiColors.glucoseVeryHigh
                    presentation.rangeClass == app.sugarlicious.model.CgmRangeClass.HIGH -> preferences.uiColors.glucoseHigh
                    else -> preferences.uiColors.glucoseInRange
                }
            glucose.setTextColor(valueColor)
            glucoseRangeBar.bind(
                glucoseState?.valueMgDl?.takeIf {
                    presentation.displayable && presentation.freshness in setOf(Freshness.CURRENT, Freshness.DELAYED)
                },
                thresholds,
                preferences.uiColors.glucoseRangeBarPalette(),
            )
            val glucoseFill = preferences.uiColors.tileBackground
            renderTrend(
                trend = presentation.trend,
                color = valueColor,
                background = glucoseFill,
                scale = GlucoseTrendSizing.scaleFactor(preferences.trendScalePercent),
                style = preferences.trendArrowStyle,
            )
            delta.text = app.sugarlicious.uishared.glucoseMetadataText(presentation.primaryMeta)
            delta.setTextColor(preferences.uiColors.textPrimary)
            age.setTextColor(preferences.uiColors.textSecondary)
            val ageText =
                if (presentation.freshness in setOf(Freshness.CURRENT, Freshness.DELAYED)) {
                    GlucoseAgeText.fromAgeMinutes(TherapyDisplayFormatter.ageMinutes(glucoseState?.measuredAtEpochMs, now))
                } else {
                    presentation.secondaryMeta
                }
            age.text = app.sugarlicious.uishared.glucoseMetadataText(ageText)
            age.visibility = if (ageText.isBlank()) View.GONE else View.VISIBLE
            root.findViewById<View>(R.id.wear_glucose_meta_row).background =
                roundedBackground(preferences.uiColors.background, android.graphics.Color.TRANSPARENT, 36f)

            root.findViewById<View>(R.id.wear_glucose_card).background =
                roundedBackground(glucoseFill, if (preferences.graphStyle.borderEnabled) preferences.uiColors.tileBorder else android.graphics.Color.TRANSPARENT, WearGlucoseCardStyle.CARD_RADIUS_DP)
        }

        chart.bind(
            newState = state,
            graphHours = preferences.graphHours,
            showPredictions = preferences.showPredictions,
            colors = preferences.graphColors.copy(border = preferences.uiColors.tileBorder),
            style = preferences.graphStyle,
            thresholds = preferences.cgmThresholds,
        )
        root.findViewById<TextView>(R.id.wear_graph_period).apply {
            text = wearGraphScaleAgeText(preferences.graphHours, state?.glucose?.measuredAtEpochMs, now)
            setTextColor(preferences.uiColors.textSecondary)
            background = null
        }
        if (refreshClock) chart.invalidate()

        if (
            firstRender ||
            refreshClock ||
            previousPreferences?.showTherapyStats != preferences.showTherapyStats ||
            previousPreferences.uiColors != preferences.uiColors ||
            previousState?.glucose != state?.glucose ||
            previousState?.insulin != state?.insulin ||
            previousState?.carbs != state?.carbs ||
            previousState?.basal != state?.basal ||
            previousState?.therapyHistory != state?.therapyHistory ||
            previousState?.therapyEvents != state?.therapyEvents
        ) {
            therapyRow.visibility = if (preferences.showTherapyStats) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.wear_basal_card).visibility =
                if (preferences.showTherapyStats) View.VISIBLE else View.GONE

            iob.text = if (canShowValue) TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, state?.insulin?.totalIob, state) else "—"
            cob.text = if (canShowValue) TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, state?.carbs?.cobGrams, state) else "—"
            basal.text =
                if (canShowValue) {
                    TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, basalDisplayUnitsPerHour(state?.basal), state).removeSuffix("/h")
                } else {
                    "—"
                }
            currentBasalIconRes = basalIconResource(state?.basal)
            basalIcon.renderSugarliciousWearIcon(
                drawableRes = currentBasalIconRes,
                tintArgb = preferences.uiColors.basal,
                backgroundArgb = preferences.uiColors.tileBackground,
            )
            val presentation = wearTherapyTilePresentation(state, now)
            listOf(
                Triple(TherapyTileMetric.IOB, R.id.wear_iob_progress, preferences.uiColors.iob),
                Triple(TherapyTileMetric.COB, R.id.wear_cob_progress, preferences.uiColors.cob),
                Triple(TherapyTileMetric.BASAL, R.id.wear_basal_progress, preferences.uiColors.basal),
            ).forEach { (metric, id, color) ->
                root.findViewById<WearTherapyProgressView>(id).bind(if (canShowValue) wearTherapyProgress(metric, state, now) else null, color)
            }
            root.findViewById<TextView>(R.id.wear_iob_secondary).apply {
                text = if (canShowValue) presentation.iobSecondary.orEmpty() else ""
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
            val basalDetails = wearBasalDetails(if (canShowValue) state?.basal else null, now)
            root.findViewById<TextView>(R.id.wear_basal_base).text = basalDetails.base
            root.findViewById<TextView>(R.id.wear_basal_secondary).text = basalDetails.temporary
            val detailIcon =
                when (currentBasalIconRes) {
                    ComplicationR.drawable.ic_complication_basal_more -> app.sugarlicious.uishared.R.drawable.ic_basalmore_bckgrnd
                    ComplicationR.drawable.ic_complication_basal_less -> app.sugarlicious.uishared.R.drawable.ic_basalless_bckgrnd
                    else -> app.sugarlicious.uishared.R.drawable.ic_basal_bckgrnd
                }
            root.findViewById<ImageView>(R.id.wear_basal_base_icon).renderSugarliciousWearIcon(app.sugarlicious.uishared.R.drawable.ic_basal_bckgrnd, preferences.uiColors.basal, preferences.uiColors.tileBackground)
            root.findViewById<ImageView>(R.id.wear_basal_temp_icon).renderSugarliciousWearIcon(detailIcon, preferences.uiColors.basal, preferences.uiColors.tileBackground)
        }

        if (firstRender || previousState?.source != state?.source || previousState?.sourceVersion != state?.sourceVersion) {
            source.text =
                when (state?.source) {
                    DataSourceId.DEXCOM_G7_WATCH -> "SugarWear"
                    DataSourceId.ANDROID_APS -> "AndroidAPS"
                    DataSourceId.DEXCOM_G7_APP -> "Dexcom G7 App"
                    DataSourceId.DEXCOM_SHARE -> "Dexcom Share"
                    DataSourceId.NIGHTSCOUT -> "Nightscout"
                    DataSourceId.XDRIP_PLUS -> "xDrip+"
                    DataSourceId.LIBRE_PATCHED -> "Libre patched app"
                    DataSourceId.OTHER -> "Other"
                    null -> "Datenquelle nicht verfügbar"
                }
        }

        if (
            firstRender ||
            lastRenderedConnectedNodes != connectedNodes ||
            previousPreferences?.uiColors != preferences.uiColors
        ) {
            connection.text = if (connectedNodes > 0) "● Telefon verbunden" else "○ Telefon nicht erreichbar"
            connection.setTextColor(
                if (connectedNodes > 0) preferences.uiColors.accent else preferences.uiColors.textSecondary,
            )
        }

        syncHint.text =
            when {
                uiState.error != null ->
                    when (uiState.error) {
                        WearOverviewError.STORAGE_UNAVAILABLE -> "Gespeicherte Daten nicht verfügbar · zum Wiederholen tippen"
                        WearOverviewError.PHONE_REFRESH_FAILED -> "Telefonaktualisierung fehlgeschlagen"
                        WearOverviewError.PREFERENCES_SAVE_FAILED -> "Graphzeitraum konnte nicht gespeichert werden"
                    }
                uiState.refreshing -> getString(R.string.syncing_values)
                uiState.refreshAttempted && connectedNodes == 0 -> getString(R.string.phone_unreachable)
                else -> getString(R.string.tap_to_refresh)
            }
        renderWatchFacePushStatus()
        lastRenderedState = state
        lastRenderedPreferences = preferences
        lastRenderedConnectedNodes = connectedNodes
        lastRenderedNowEpochMs = now
        hasRendered = true
    }

    private fun renderTrend(
        trend: app.sugarlicious.model.Trend?,
        color: Int,
        background: Int,
        scale: Float,
        style: app.sugarlicious.model.TrendArrowStyle,
    ) {
        val spec = trend?.let(TrendVisuals::spec)
        if (spec == null) {
            trendContainer.visibility = View.GONE
            return
        }
        trendContainer.visibility = View.VISIBLE
        trendArrow1.renderSugarliciousWearIcon(TrendDrawableResources.forAsset(spec.asset), color, background, trendStyle = style)
        trendArrow1.rotation = 0f
        val density = resources.displayMetrics.density
        trendArrow1.layoutParams =
            trendArrow1.layoutParams.apply {
                height = (WearGlucoseCardStyle.TREND_SIZE_DP * scale * density * resources.configuration.fontScale).roundToInt()
                width = (WearGlucoseCardStyle.TREND_SIZE_DP * scale * spec.aspectRatio * density * resources.configuration.fontScale).roundToInt()
            }
        trendArrow1.visibility = View.VISIBLE
    }

    private fun applyUiColors(root: View, preferences: WearDisplayPreferences) {
        val ui = preferences.uiColors
        root.findViewById<View>(R.id.wear_root).setBackgroundColor(ui.background)

        listOf(R.id.wear_basal_card, R.id.wear_iob_card, R.id.wear_cob_card).forEach { id ->
            root.findViewById<View>(id).background = roundedBackground(ui.tileBackground, if (preferences.graphStyle.borderEnabled) ui.tileBorder else android.graphics.Color.TRANSPARENT, 20f)
        }
        listOf(R.id.wear_connection_card).forEach { id ->
            root.findViewById<View>(id).background = roundedBackground(ui.tileBackground, if (preferences.graphStyle.borderEnabled) ui.tileBorder else android.graphics.Color.TRANSPARENT, 22f)
        }
        root.findViewById<View>(R.id.wear_graph_card).background = null

        listOf(
            R.id.wear_glucose,
            R.id.wear_basal,
            R.id.wear_basal_base,
            R.id.wear_iob,
            R.id.wear_cob,
            R.id.wear_source,
        ).forEach { id -> root.findViewById<TextView>(id).setTextColor(ui.textPrimary) }

        listOf(
            R.id.wear_delta,
            R.id.wear_age,
            R.id.wear_sync_hint,
            R.id.wear_iob_secondary,
            R.id.wear_cob_secondary,
            R.id.wear_basal_secondary,
            R.id.wear_watchface_push_status,
        ).forEach { id -> root.findViewById<TextView>(id).setTextColor(ui.textSecondary) }
        iobIcon.renderSugarliciousWearIcon(app.sugarlicious.uishared.R.drawable.ic_iob, ui.iob, ui.tileBackground)
        cobIcon.renderSugarliciousWearIcon(app.sugarlicious.uishared.R.drawable.ic_cob, ui.cob, ui.tileBackground)
        basalIcon.renderSugarliciousWearIcon(currentBasalIconRes, ui.basal, ui.tileBackground)
        app.sugarlicious.uishared.WearOverviewSettingsButton.apply(
            root.findViewById(R.id.wear_settings_action),
            R.drawable.ic_settings,
            ui.tileBackground,
            ui.textPrimary,
        )
    }

    private fun roundedBackground(
        fill: Int,
        border: Int,
        radiusDp: Float,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(fill)
            setStroke((1f * resources.displayMetrics.density).roundToInt().coerceAtLeast(1), border)
        }

    private fun requestRuntimeNotificationPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    private fun requestBatteryExemptionIfNeeded(): Boolean {
        if (WearBackgroundAccess.isBatteryUnrestricted(this)) return false

        batteryRequestPending = true
        if (WearBackgroundAccess.openBatterySettings(this)) return true

        batteryRequestPending = false
        Toast
            .makeText(
                this,
                "Akku-Einstellungen konnten auf dieser Watch nicht geöffnet werden",
                Toast.LENGTH_LONG,
            ).show()
        recordRuntimeDiagnostic(
            "WATCH-BG-404",
            "Battery optimization settings could not be opened for Sugarlicious Wear",
            DiagnosticSeverity.ERROR,
        )
        return false
    }

    private fun requestWatchFacePermissionOnFirstLaunch() {
        val onboarding = getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        if (onboarding.getBoolean(KEY_WFP_PERMISSION_REQUESTED, false)) return

        onboarding.edit { putBoolean(KEY_WFP_PERMISSION_REQUESTED, true) }
        if (SugarliciousWatchFacePush.isSupported() && !SugarliciousWatchFacePush.hasActivationPermission(this)) {
            watchFacePermission.launch(SugarliciousWatchFacePush.ACTIVE_PERMISSION)
        }
    }

    private fun renderWatchFacePushStatus() {
        if (!::watchFacePushStatus.isInitialized) return
        watchFacePushStatus.text =
            when {
                !SugarliciousWatchFacePush.isSupported() -> "Watchface-Direktwechsel: Wear OS 6+ erforderlich"
                SugarliciousWatchFacePush.hasActivationPermission(this) -> "Watchface-Direktwechsel freigegeben"
                else -> "Watchface-Direktwechsel nicht freigegeben"
            }
    }

    private fun recordRuntimeDiagnostic(
        code: String,
        message: String,
        severity: DiagnosticSeverity,
        error: Throwable? = null,
    ) {
        scope.launch(Dispatchers.IO) {
            applicationContext.recordWatchDiagnostic(
                "RUNTIME",
                code,
                message,
                severity,
                error?.let { mapOf("error" to it.javaClass.simpleName) }.orEmpty(),
            )
        }
    }

    private fun notificationPermissionResult(granted: Boolean) {
        if (!granted) {
            Toast
                .makeText(
                    this,
                    "Benachrichtigungen nicht freigegeben – die Dauerbetrieb-Anzeige kann verborgen bleiben",
                    Toast.LENGTH_LONG,
                ).show()
            recordRuntimeDiagnostic(
                "WATCH-NOTIFY-403",
                "Notification permission was not granted for Sugarlicious Wear",
                DiagnosticSeverity.WARNING,
            )
        }
        val batteryRequestStarted = requestBatteryExemptionIfNeeded()
        if (!batteryRequestStarted) requestWatchFacePermissionOnFirstLaunch()
    }

    private fun resolveUnit(
        stateUnit: GlucoseUnit?,
        preference: WatchGlucoseUnit,
    ): GlucoseUnit =
        when (preference) {
            WatchGlucoseUnit.AAPS -> stateUnit ?: GlucoseUnit.MG_DL
            WatchGlucoseUnit.MG_DL -> GlucoseUnit.MG_DL
            WatchGlucoseUnit.MMOL_L -> GlucoseUnit.MMOL_L
        }

    private fun formatNumber(
        value: Double?,
        digits: Int,
        suffix: String,
    ): String = value?.let { String.format(Locale.US, "%.${digits}f%s", it, suffix) } ?: "—"

    companion object {
        private const val ONBOARDING_PREFS = "wear_onboarding"
        private const val KEY_WFP_PERMISSION_REQUESTED = "watchface_permission_requested"
    }
}

internal fun basalDisplayUnitsPerHour(basal: BasalState?): Double? = basal?.currentUnitsPerHour

internal fun basalIconResource(basal: BasalState?): Int {
    val absolute = basal?.tempAbsoluteUnitsPerHour
    val base = basal?.currentUnitsPerHour
    val percent = basal?.tempPercent
    return when {
        absolute != null && base != null && absolute > base + BASAL_COMPARE_EPSILON ->
            ComplicationR.drawable.ic_complication_basal_more
        absolute != null && base != null && absolute < base - BASAL_COMPARE_EPSILON ->
            ComplicationR.drawable.ic_complication_basal_less
        percent != null && percent > 100 -> ComplicationR.drawable.ic_complication_basal_more
        percent != null && percent < 100 -> ComplicationR.drawable.ic_complication_basal_less
        else -> ComplicationR.drawable.ic_complication_basal
    }
}

private const val BASAL_COMPARE_EPSILON = 0.001
