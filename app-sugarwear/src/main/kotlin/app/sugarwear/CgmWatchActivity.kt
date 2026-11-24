package app.sugarwear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.model.WearGlucoseCardInput
import app.sugarlicious.model.WearGlucoseCardStyle
import app.sugarlicious.model.cgmBoundaryDisplay
import app.sugarlicious.model.wearGlucoseCardPresentation
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensorState
import app.sugarlicious.uishared.TrendDrawableResources
import app.sugarlicious.uishared.centerOnGlucoseMetadata
import kotlin.math.roundToInt

internal fun nextDirectGraphHours(current: Int): Int {
    val options = CgmDirectToWatchSettingsStore.HOUR_OPTIONS
    val index = options.indexOf(current)
    return options[if (index < 0) 0 else (index + 1) % options.size]
}

@dagger.hilt.android.AndroidEntryPoint
class CgmWatchActivity : ComponentActivity() {
    private val runtimePermissions =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestMultiplePermissions(),
        ) {
            // A system dialog is not a companion-device association. Resume the
            // original pairing request only if Android actually granted both BLE rights.
            pendingPairingPermissionToken?.let { token ->
                pendingPairingPermissionToken = null
                if (CgmSystemAccess.missingBluetoothPermissions(this).isEmpty()) {
                    pairingModel.accessReady(token)
                } else {
                    pairingModel.accessFailed(token, "Bluetooth-Berechtigung fehlt. Bitte Geräte in der Nähe für SugarWear erlauben.")
                }
            }
            refreshScreen()
        }
    private val overviewModel by viewModels<CollectorViewModel>()
    private val sensorStatusModel by viewModels<CgmSystemStatusViewModel>()
    private val screenHost by lazy { FrameLayout(this).apply { setBackgroundColor(Color.BLACK) } }
    private var overviewState = CgmOverviewState()
    private val systemBackCallback = android.window.OnBackInvokedCallback { handleSystemBack() }
    private val appearanceStore by lazy { CgmAppearanceStore(this) }
    private val directSettings by lazy { CgmDirectToWatchSettingsStore(this) }
    private var activePalette: CgmAppearancePalette? = null
    private var screenBuilt = false

    @javax.inject.Inject internal lateinit var pairingCommands: AndroidCgmPairingCommands
    private val pairingModel by viewModels<SensorConnectionViewModel>()
    private val pairingStep get() = pairingModel.state.value.step
    private var pairingCodeDraft: String
        get() = pairingModel.state.value.draft
        set(value) = pairingModel.editCode(value)
    private val pairingStartInFlight get() = pairingModel.state.value.startInFlight
    private val pairingAccessPending get() = pairingModel.state.value.accessPending
    private val unlinkFailed get() = pairingModel.state.value.unlinkFailed
    private val unlinkFailureReason get() = pairingModel.state.value.unlinkFailureReason
    private val pairingBondAccessInFlight get() = pairingModel.state.value.bondInFlight
    private val pairingBondAccessFailure get() = pairingModel.state.value.bondFailure
    private val pairingStartedAtEpochMs get() = pairingModel.state.value.startedAt
    private val pairingFailureMessage get() = pairingModel.state.value.failureMessage
    private var claimedPairingEffect: Long? = null
    private var pendingPairingPermissionToken: Long? = null
    internal var connectSensor: (android.content.Context, String) -> Unit
        get() = { _, code -> pairingCommands.connectCommand(code) }
        set(value) {
            val app = applicationContext
            pairingCommands.connectCommand = { value(app, it) }
        }
    private val companionConsent: CgmCompanionConsentLauncher = CgmCompanionConsentLauncher(this) { request, result -> companionBondAccess.onActivityResult(request, result) }
    private val companionBondAccess: CgmCompanionBondAccess by lazy { CgmCompanionBondAccess(this, launchConsent = companionConsent::launch) }
    private var companionAccessClosed = false
    internal var prepareUnlinkAccess: (onReady: () -> Unit, onFailure: (String) -> Unit) -> Unit = { ready, failed ->
        companionBondAccess.ensureForUnlink(ready, failed)
    }
    internal var unlinkSensor: (android.content.Context) -> CgmUnlinkResult
        get() = { pairingCommands.unlinkCommand() }
        set(value) {
            val app = applicationContext
            pairingCommands.unlinkCommand = { value(app) }
        }
    internal var executeAssociationTask: (() -> Unit) -> Unit
        get() = pairingCommands.executionOverride ?: { task -> mainHandler.post(task) }
        set(value) {
            pairingCommands.executionOverride = value
        }
    private var pairingErrorCode: String? = null
    private var renderedPairingProgress: CgmPairingProgress? = null
    private var renderedPairingRadio: Pair<Boolean?, Boolean?>? = null
    private var renderedPairingStep: CgmPairingScreenStep? = null
    private var renderedOtherSensorCount = 0
    private var overviewResumed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var scrollView: ScrollView
    private lateinit var statusHost: LinearLayout
    private var stopRequested = false
    private var renderedTileMode: CgmGlucoseTileMode? = null
    private lateinit var glucoseHost: LinearLayout
    private lateinit var graphHost: LinearLayout
    private lateinit var graphView: CgmCollectorGraphView
    private lateinit var graphPeriodPill: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by overviewModel.state.collectAsStateWithLifecycle()
            val pairing by pairingModel.state.collectAsStateWithLifecycle()
            AndroidView(factory = { screenHost }, modifier = Modifier.fillMaxSize(), update = {
                overviewState = state
                if (overviewResumed) {
                    consumePairingEffect(pairing.effect)
                    refreshScreen()
                }
            })
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback)
        val setup = getSharedPreferences("g7_setup", MODE_PRIVATE)
        // Imported pending cycles refer to the old UID's alarms; rearm them for this package.
        CgmRuntimeReconciler.reconcile(this, if (setup.getBoolean("productAccessReview", false)) CgmRuntimeEntryPoint.PACKAGE_REPLACED else CgmRuntimeEntryPoint.WATCH_APP)
        // Pairing credentials must not be copied into the Activity saved-state bundle.
        val restoredStep =
            savedInstanceState?.getString(KEY_PAIRING_STEP)?.let {
                runCatching { CgmPairingScreenStep.valueOf(it) }.getOrNull()
            }
        restoredStep?.let { pairingModel.open(it) }
        stopRequested = savedInstanceState?.getBoolean("offer_stop", false) == true
        consumePairingIntent()
        if (setup.getBoolean("productAccessReview", false)) {
            setup.edit { remove("productAccessReview") }
            val access = CgmSystemAccess.snapshot(this)
            if (!access.bluetoothGranted || !access.notificationsGranted || !access.batteryUnrestricted || !access.exactAlarmsGranted) {
                startActivity(Intent(this, CgmSetupActivity::class.java))
            }
        } else if (!setup.getBoolean("complete", false)) {
            if (CgmSensorStateStore(this).read().sensor != null || CgmCredentialStore(this).read() != null) {
                // Existing installations keep their running sensor; onboarding is for fresh installs.
                setup.edit { putBoolean("complete", true) }
            } else {
                startActivity(Intent(this, CgmSetupActivity::class.java))
            }
        } else {
            requestMissingPermissions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PAIRING_STEP, pairingStep?.name)
        outState.putBoolean("offer_stop", stopRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePairingIntent()
        refreshScreen()
    }

    private fun consumePairingIntent() {
        if (intent?.getBooleanExtra(EXTRA_SENSOR_UNLINKED, false) == true) {
            intent.removeExtra(EXTRA_SENSOR_UNLINKED)
            if (!hasCgmSensorAssociation(CgmSensorStateStore(this).read())) stopRequested = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_PAIRING, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PAIRING)
            pairingModel.requestOpen()
        }
    }

    override fun onResume() {
        super.onResume()
        overviewResumed = true
        pairingModel.setVisible(true)
        // Rehydrate the separately installed Vigil complication provider as well. This also
        // repairs settings after that package was reinstalled while SugarWear data stayed intact.
        directSettings.sync()
        refreshScreen()
    }

    override fun onPause() {
        overviewResumed = false
        pairingModel.setVisible(false)
        super.onPause()
    }

    private fun showScreen(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        screenHost.removeAllViews()
        screenHost.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshScreen() {
        val state =
            overviewState.snapshot?.persisted ?: if (pairingStep != null) {
                CgmPersistedState()
            } else {
                screenBuilt = false
                if (overviewState.failed) {
                    val palette = appearanceStore.load()
                    showScreen(
                        label("Anzeigedaten konnten nicht geladen werden. Zum Wiederholen tippen.", 13f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY)).apply {
                            setOnClickListener { overviewModel.retry() }
                        },
                    )
                } else if (screenHost.childCount > 0) {
                    screenHost.removeAllViews()
                }
                return
            }
        val palette = appearanceStore.load()
        pairingModel.sensorChanged(state)
        if (!screenBuilt || palette != activePalette) buildScreen(palette) else refreshLiveContent()
    }

    private fun renderPairingTile(
        palette: CgmAppearancePalette,
        state: CgmPersistedState,
    ) {
        activePalette = palette
        val background = palette.argb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val content =
            CgmPairingTileLayout(this).apply {
                tag = "glucose-tile-pairing"
                this.background = rounded(palette.argb(CgmAppearanceRole.MENU_SURFACE), Color.TRANSPARENT, WearGlucoseCardStyle.CARD_RADIUS_DP)
            }
        val step = pairingStep ?: CgmPairingScreenStep.ENTER_CODE
        val pairingFailed = pairingBondAccessFailure != null || isTerminalCgmPairingFailure(state, pairingStartedAtEpochMs, System.currentTimeMillis())
        val progress = cgmPairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
        val accessMessage =
            when {
                pairingAccessPending -> "Bluetooth-Berechtigung wird benötigt. Bitte die Systemfreigabe bestätigen."
                state.collectorEnabled && overviewState.snapshot?.bluetoothPermission == false -> "Bluetooth-Berechtigung fehlt. Bitte Geräte in der Nähe für SugarWear erlauben."
                state.collectorEnabled && overviewState.snapshot?.bluetoothEnabled == false -> "Bluetooth deaktiviert! Bitte Bluetooth in den Systemeinstellungen aktivieren."
                else -> null
            }
        renderedPairingProgress = progress
        renderedPairingRadio = overviewState.snapshot?.bluetoothPermission to overviewState.snapshot?.bluetoothEnabled
        renderedOtherSensorCount = state.otherSensorCount
        content.addView(
            label(
                when (step) {
                    CgmPairingScreenStep.ENTER_CODE -> "Sensor verbinden"
                    CgmPairingScreenStep.CONNECTING -> "Sensor verbinden"
                    CgmPairingScreenStep.CONNECTED -> "Sensor verbunden"
                },
                15f,
                palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                true,
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            },
        )
        if (step == CgmPairingScreenStep.CONNECTING || step == CgmPairingScreenStep.CONNECTED) {
            content.addView(
                CgmSensorConnectionProgressView(this, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.argb(if (pairingFailed) CgmAppearanceRole.GLUCOSE_ERROR else CgmAppearanceRole.MENU_PRIMARY), !pairingFailed && step == CgmPairingScreenStep.CONNECTING && progress.animate, 28),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 4.dp
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            content.addView(
                label(
                    if (step ==
                        CgmPairingScreenStep.CONNECTED
                    ) {
                        "Sensor erfolgreich verbunden"
                    } else {
                        accessMessage ?: progress.message
                    },
                    16f,
                    palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    true,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(4.dp, 4.dp, 4.dp, 2.dp)
                },
            )
            cgmOtherSensorsLabel(state.otherSensorCount)?.let { otherSensors ->
                content.addView(
                    label(
                        otherSensors,
                        11f,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(4.dp, 1.dp, 4.dp, 1.dp)
                    },
                )
            }
            if (step == CgmPairingScreenStep.CONNECTING) {
                (pairingCodeDraft.takeIf { it.length == 4 } ?: overviewState.snapshot?.pairingCode)?.let { content.addView(connectionCodeLabel(it, palette)) }
            }
            if (step == CgmPairingScreenStep.CONNECTED) {
                content.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_success_check)
                        contentDescription = "Sensor erfolgreich verbunden"
                    },
                    LinearLayout.LayoutParams(32.dp, 32.dp).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = 2.dp
                    },
                )
            }
            if (step == CgmPairingScreenStep.CONNECTING && pairingFailed) {
                content.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_pairing_error)
                        contentDescription = "Sensorverbindung fehlgeschlagen"
                    },
                    LinearLayout.LayoutParams(52.dp, 52.dp).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = 4.dp
                    },
                )
                content.addView(
                    label(
                        pairingBondAccessFailure ?: state.lastError?.safeMessage ?: "Verbindung zum Sensor konnte nicht hergestellt werden",
                        12f,
                        palette.argb(CgmAppearanceRole.GLUCOSE_ERROR),
                        true,
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(4.dp, 4.dp, 4.dp, 0)
                    },
                )
                content.addView(
                    pairingPill("Erneut versuchen") {
                        pairingModel.retry()
                        refreshScreen()
                    },
                )
            }
            if (step == CgmPairingScreenStep.CONNECTING) {
                content.addView(
                    pairingPill("Code ändern") {
                        companionBondAccess.cancel()
                        pairingModel.correctCode()
                        refreshScreen()
                    },
                )
                content.addView(
                    pairingPill("Zurück") {
                        if (pairingAccessPending) {
                            pairingModel.cancelPendingAccess()
                            companionBondAccess.cancel()
                            refreshScreen()
                        } else {
                            performSensorUnlink(closePairing = true)
                        }
                    },
                )
            }
        } else {
            content.addView(
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_sensor_outline)
                    setColorFilter(palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
                    contentDescription = "Sensor verbinden"
                },
                LinearLayout.LayoutParams(32.dp, 32.dp).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = 3.dp
                },
            )
            content.addView(
                label(
                    "Sensorcode eingeben",
                    13f,
                    palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 2.dp, 0, 1.dp)
                    maxLines = 1
                },
            )
            pairingFailureMessage?.let { message ->
                content.addView(label(message, 11f, palette.argb(CgmAppearanceRole.GLUCOSE_ERROR), true).apply { gravity = Gravity.CENTER })
            }
            val code =
                EditText(this).apply {
                    hint = "0000"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(android.text.InputFilter.LengthFilter(4))
                    gravity = Gravity.CENTER
                    textSize = 24f
                    setTextColor(palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                    setHintTextColor(palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY))
                    this.background = null
                    setPadding(0, 0, 0, 0)
                    letterSpacing = 0.18f
                    setText(pairingCodeDraft)
                    setSelection(text.length)
                    addTextChangedListener(
                        object : android.text.TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                                pairingCodeDraft =
                                    s?.toString().orEmpty()
                            }

                            override fun afterTextChanged(s: android.text.Editable?) = Unit
                        },
                    )
                    contentDescription = "Vierstelliger G7 Verbindungscode"
                }
            content.addView(code, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp).apply { gravity = Gravity.CENTER_HORIZONTAL })
            content.addView(
                pairingPill("Verbinden") {
                    val entered = code.text?.toString().orEmpty()
                    if (!canStartCgmPairing(pairingStep, pairingStartInFlight, entered)) {
                        if (pairingStartInFlight) return@pairingPill
                        code.error = "Bitte genau vier Ziffern eingeben"
                        return@pairingPill
                    }
                    code.isEnabled = false
                    pairingModel.start(entered)
                    refreshScreen()
                },
            )
        }

        if (step == CgmPairingScreenStep.ENTER_CODE) {
            content.addView(
                pairingPill("Zurück") {
                    pairingModel.close()
                    refreshScreen()
                },
            )
        }
        renderedPairingStep = step
        glucoseHost.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildScreen(palette: CgmAppearancePalette) {
        val previousScrollY = if (screenBuilt) scrollView.scrollY else 0
        activePalette = palette
        val background = palette.argb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(18.dp, 30.dp, 18.dp, 30.dp)
                setBackgroundColor(background)
            }

        renderedTileMode = null
        glucoseHost =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                tag = "glucose-host"
            }
        content.addView(glucoseHost, cardParams(top = 4))
        graphHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(graphHost, cardParams(top = 8))
        content.addView(header())
        CgmBottomNavigation.appendAtEnd(this, content, CgmNavTab.OVERVIEW)
        scrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    setBackgroundColor(background)
                    addView(content)
                }.applyCgmEdgeFade()
        showScreen(scrollView)
        screenBuilt = true
        refreshLiveContent(preserveScroll = false)
        if (previousScrollY > 0) scrollView.post { scrollView.scrollTo(0, previousScrollY) }
    }

    private fun header() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            statusHost =
                LinearLayout(this@CgmWatchActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            addView(statusHost)
        }

    private fun buildAlarmCard(alarm: SugarWearAlarmInstance, palette: CgmAppearancePalette): View {
        val persisted = overviewState.snapshot?.persisted ?: CgmPersistedState()
        val reading =
            persisted.lastReading?.takeIf {
                (alarm.sensorId == null || alarm.sensorId == it.sensorId) &&
                    (alarm.sessionId == null || alarm.sessionId == it.sessionId) &&
                    cgmReadingBelongsToSession(persisted, it) &&
                    it.status == CgmReadingStatus.VALID &&
                    !it.displayOnly &&
                    it.glucoseMgDl.isFinite() &&
                    overviewState.now - it.timestampEpochMs in 0 until CGM_SIGNAL_LOSS_AFTER_MS
            }
        val thresholds = CgmGraphColorStore(this).readThresholds()
        val role =
            when (reading?.let { thresholds.classify(it.glucoseMgDl) }) {
                CgmRangeClass.VERY_HIGH, CgmRangeClass.HIGH -> CgmAppearanceRole.GLUCOSE_HIGH
                CgmRangeClass.VERY_LOW, CgmRangeClass.LOW -> CgmAppearanceRole.GLUCOSE_LOW
                CgmRangeClass.IN_RANGE -> CgmAppearanceRole.GLUCOSE_IN_RANGE
                null -> CgmAppearanceRole.GLUCOSE_NO_SOURCE
            }
        return CgmAlarmFullBleedView(
            this,
            cgmAlarmArtwork(alarm.type) ?: CgmAlarmArtwork.SENSOR_ERROR,
            reading,
            palette.argb(role),
            appearanceStore.trendArrowStyle().renderSpec(),
            mode = CgmAlarmContentMode.IN_GLUCOSE_TILE,
            titleOverride = if (alarm.type == SugarWearAlarmType.TECHNICAL_WARNING) cgmAlarmHeadline(alarm.type) else null,
            onAcknowledge = {
                CgmAlarmCoordinator.acknowledgeEpisode(this, alarm)
                overviewModel.retry()
            },
        ).apply {
            tag = "glucose-tile-alarm"
            minimumHeight = WearGlucoseCardStyle.CARD_HEIGHT_DP.dp
            background = rounded(palette.argb(CgmAppearanceRole.MENU_SURFACE), Color.TRANSPARENT, WearGlucoseCardStyle.CARD_RADIUS_DP)
            clipToOutline = true
        }
    }

    private fun connectionCodeLabel(code: String, palette: CgmAppearancePalette) =
        label(code, 24f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY), true).apply {
            gravity = Gravity.CENTER
            tag = "connection-sensor-code"
            setPadding(0, 6.dp, 0, 6.dp)
        }

    private fun addPausedValue(tile: LinearLayout, historical: CgmReading?, palette: CgmAppearancePalette) {
        tile.addView(
            label(historical?.let { "Letzter Messwert: ${kotlin.math.round(it.glucoseMgDl).toInt()} mg/dL" } ?: "Kein letzter Messwert verfügbar", 12f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY)).apply {
                gravity = Gravity.CENTER
                tag = "paused-historical-value"
            },
        )
    }

    private fun glucoseTile(
        transition: CgmUiSensorTransition?,
        warningText: String?,
        palette: CgmAppearancePalette,
    ): LinearLayout {
        val tile =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = WearGlucoseCardStyle.CARD_HEIGHT_DP.dp
                setPadding(
                    WearGlucoseCardStyle.HORIZONTAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.VERTICAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.HORIZONTAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.VERTICAL_PADDING_DP.dp,
                )
                background =
                    rounded(
                        palette.argb(CgmAppearanceRole.MENU_SURFACE),
                        palette.argb(CgmAppearanceRole.MENU_BORDER),
                        WearGlucoseCardStyle.CARD_RADIUS_DP,
                    )
            }

        fun addFingerstickAction() {
            tile.addView(
                pill(getString(R.string.fingerstick_log)) {
                    startActivity(Intent(this, FingerstickActivity::class.java))
                },
            )
        }

        val now = overviewState.now
        val persisted = overviewState.snapshot?.persisted ?: CgmPersistedState()
        val bluetoothPermission = overviewState.snapshot?.bluetoothPermission == true
        val bluetoothEnabled = overviewState.snapshot?.bluetoothEnabled == true
        val display = cgmOverviewPresentation(persisted, overviewState.snapshot?.credentialsPresent == true, transition, now, bluetoothEnabled, bluetoothPermission, overviewState.snapshot?.lastValidReading)
        val isPaused = display.mode == CgmOverviewMode.PAUSED
        val noActiveSensor = display.mode == CgmOverviewMode.NO_SENSOR
        val isResumeInFlight = display.mode == CgmOverviewMode.CONNECTING
        val isBluetoothOff = display.mode == CgmOverviewMode.BLUETOOTH_OFF
        val isSensorError = display.mode == CgmOverviewMode.SENSOR_ERROR
        val isSignalLoss = display.mode == CgmOverviewMode.SIGNAL_LOSS
        val isWarningState = display.mode != CgmOverviewMode.LIVE || warningText == "Anzeigedaten konnten nicht geladen werden"

        if (isWarningState) {
            val iconRes: Int
            val iconTint: Int?
            val titleText: String
            var subtitleText: String? = display.phase.takeUnless { isResumeInFlight }

            when {
                isBluetoothOff -> {
                    iconRes = R.drawable.ic_status_bluetooth
                    iconTint = palette.argb(CgmAppearanceRole.STATUS_ICON)
                    titleText = "Bluetooth\ndeaktiviert!"
                    subtitleText = "Bitte aktivieren Sie\nIhr Bluetooth in den\nEinstellungen"
                }
                display.mode == CgmOverviewMode.PERMISSION_REQUIRED -> {
                    iconRes = R.drawable.ic_status_bluetooth
                    iconTint = palette.argb(CgmAppearanceRole.STATUS_ICON)
                    titleText = "Bluetooth-Berechtigung erforderlich"
                }
                isSensorError -> {
                    iconRes = R.drawable.ic_status_sensor_error
                    iconTint = null
                    titleText = "Sensorfehler!"
                    subtitleText = "Diese Phase kann bis zu 3h dauern."
                }
                isSignalLoss -> {
                    iconRes = R.drawable.ic_status_signal_loss
                    iconTint = null
                    titleText = "Signalverlust"
                    subtitleText = "Bleiben Sie in Reichweite des Sensors"
                }
                isPaused -> {
                    iconRes = R.drawable.ic_status_paused
                    iconTint = null
                    titleText = "Datenempfang pausiert"
                }
                noActiveSensor -> {
                    iconRes = R.drawable.ic_artwork_low
                    iconTint = null
                    titleText = "Kein aktiver Sensor verbunden"
                }
                else -> {
                    iconRes = app.sugarlicious.uishared.R.drawable.ic_alert
                    iconTint = 0xFFFF5C69.toInt()
                    titleText =
                        if (isResumeInFlight) {
                            "Sensor verbinden"
                        } else {
                            display.phase ?: warningText ?: "Keine aktuellen Daten"
                        }
                }
            }

            val statusIconLayout = cgmStatusIconLayout(iconRes)
            val iconDensity = resources.displayMetrics.density * if (iconRes == R.drawable.ic_status_bluetooth) 0.65f else 1f
            val fallbackIconSize = if (noActiveSensor) 40.dp else 66.dp
            tile.addView(
                LinearLayout(this@CgmWatchActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, 4.dp, 0, 4.dp)
                    addView(
                        ImageView(this@CgmWatchActivity).apply {
                            setImageResource(iconRes)
                            visibility = if (isResumeInFlight) View.GONE else View.VISIBLE
                            if (iconTint != null) {
                                imageTintList =
                                    android.content.res.ColorStateList
                                        .valueOf(iconTint)
                            } else {
                                imageTintList = null
                            }
                            adjustViewBounds = statusIconLayout == null
                            scaleType = if (statusIconLayout == null) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.MATRIX
                            if (statusIconLayout != null) imageMatrix = statusIconLayout.matrix(iconDensity, drawable.intrinsicWidth)
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        LinearLayout.LayoutParams(statusIconLayout?.width(iconDensity) ?: fallbackIconSize, statusIconLayout?.height(iconDensity) ?: fallbackIconSize).apply {
                            bottomMargin = 8.dp
                            gravity = Gravity.CENTER_HORIZONTAL
                        },
                    )
                    addView(
                        label(titleText, 16f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true).apply {
                            gravity = Gravity.CENTER
                        },
                    )
                    if (!subtitleText.isNullOrBlank() && subtitleText != titleText) {
                        addView(
                            label(subtitleText, 12f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY)).apply {
                                gravity = Gravity.CENTER
                                setPadding(0, 4.dp, 0, 0)
                            },
                        )
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            if (isResumeInFlight) {
                val phaseText = display.phase.orEmpty()
                val inlineProgress =
                    LinearLayout(this@CgmWatchActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, 8.dp, 0, 0)

                        addView(
                            CgmSensorConnectionProgressView(this@CgmWatchActivity, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.argb(CgmAppearanceRole.MENU_PRIMARY), display.animate, 28),
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                        )

                        addView(
                            label(phaseText, 16f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true).apply {
                                gravity = Gravity.CENTER
                                setPadding(4.dp, 9.dp, 4.dp, 6.dp)
                            },
                        )
                    }
                overviewState.snapshot?.pairingCode?.let { code ->
                    inlineProgress.addView(connectionCodeLabel(code, palette))
                }
                tile.addView(
                    inlineProgress,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                tile.addView(
                    pill("Sensorcode korrigieren") {
                        companionBondAccess.cancel()
                        pairingModel.correctCode()
                        refreshScreen()
                    },
                )
                tile.addView(pill("Zurück") { sensorStatusModel.pause() })
            } else if (isPaused) {
                addPausedValue(
                    tile,
                    overviewState.snapshot?.lastValidReading?.takeIf {
                        cgmReadingBelongsToSession(persisted, it) && it.status == CgmReadingStatus.VALID && !it.displayOnly && it.glucoseMgDl.isFinite() && it.timestampEpochMs <= now
                    } ?: display.reading,
                    palette,
                )
                val resumeBtn =
                    CgmTileActionButton(this@CgmWatchActivity).apply {
                        setText(R.string.cgm_action_resume)
                        setOnClickListener {
                            sensorStatusModel.resumeCollector()
                        }
                        tag = "overview-resume-collector"
                    }
                tile.addView(
                    resumeBtn,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = 10.dp
                    },
                )
            } else if (noActiveSensor) {
                val credentialsPresent = overviewState.snapshot?.credentialsPresent == true
                val canRetrySetup =
                    persisted.sensor != null &&
                        persisted.sensor?.state != CgmSensorState.ENDED &&
                        credentialsPresent
                val requiresExplicitUnlink = !canRetrySetup && (persisted.sensor != null || credentialsPresent)
                tile.addView(
                    pill(if (requiresExplicitUnlink) "Sensor trennen" else "Sensor verbinden") {
                        when {
                            requiresExplicitUnlink -> performSensorUnlink()
                            canRetrySetup -> {
                                pairingModel.open(CgmPairingScreenStep.CONNECTING, System.currentTimeMillis())
                                sensorStatusModel.resumeCollector()
                            }
                            else -> pairingModel.open(CgmPairingScreenStep.ENTER_CODE)
                        }
                        refreshScreen()
                    },
                )
            }

            if (display.mode in setOf(CgmOverviewMode.SENSOR_ERROR, CgmOverviewMode.SIGNAL_LOSS, CgmOverviewMode.BLUETOOTH_OFF, CgmOverviewMode.PERMISSION_REQUIRED)) {
                overviewState.snapshot?.lastValidReading?.takeIf { cgmReadingBelongsToSession(persisted, it) && it.status == CgmReadingStatus.VALID }?.let {
                    tile.addView(
                        label("Letzter Messwert · ${((now - it.timestampEpochMs).coerceAtLeast(0L) / 60_000L)} Min.", 12f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY)).apply {
                            gravity = Gravity.CENTER
                            tag = "glucose-status-age"
                        },
                    )
                }
            }
            addFingerstickAction()
            return tile
        }

        val liveReading = display.reading ?: return tile
        val basePresentation =
            wearGlucoseCardPresentation(
                WearGlucoseCardInput(
                    valueMgDl = liveReading.glucoseMgDl,
                    displayUnit = GlucoseUnit.MG_DL,
                    deltaMgDl = liveReading.deltaMgDl,
                    trend = liveReading.trend,
                    measuredAtEpochMs = liveReading.timestampEpochMs,
                    quality = CgmQuality.VALID,
                    sourceLabel = "",
                ),
                CgmGraphColorStore(this).readThresholds(),
                now,
                showSignalLossLabel = false,
            )
        val boundary = cgmBoundaryDisplay(liveReading.glucoseMgDl)
        val presentation =
            if (boundary == null) {
                basePresentation
            } else {
                basePresentation.copy(
                    value = boundary.label,
                    trend = null,
                    primaryMeta = "",
                )
            }

        val valueColor =
            when {
                presentation.freshness == app.sugarlicious.model.Freshness.SIGNAL_LOSS -> 0xFF9E9E9E.toInt()
                !presentation.displayable -> palette.argb(CgmAppearanceRole.GLUCOSE_NO_SOURCE)
                presentation.rangeClass == CgmRangeClass.VERY_LOW || presentation.rangeClass == CgmRangeClass.LOW -> palette.argb(CgmAppearanceRole.GLUCOSE_LOW)
                presentation.rangeClass == CgmRangeClass.VERY_HIGH || presentation.rangeClass == CgmRangeClass.HIGH -> palette.argb(CgmAppearanceRole.GLUCOSE_HIGH)
                else -> palette.argb(CgmAppearanceRole.GLUCOSE_IN_RANGE)
            }

        tile.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                // Baseline alignment lifts non-text children relative to the glucose glyphs.
                // Center both render boxes instead so the arrow's visual midpoint matches the value.
                isBaselineAligned = false
                addView(
                    label(
                        presentation.value,
                        WearGlucoseCardStyle.VALUE_TEXT_SP * GlucoseTrendSizing.scaleFactor(appearanceStore.glucoseScalePercent()),
                        valueColor,
                        true,
                    ),
                )
                presentation.trend?.let { addView(trendIndicator(it)) }
            },
        )

        // Normal metadata pill (Delta and Age)
        fun metadataRow(value: String, icon: Int): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                isBaselineAligned = false
                val iconSide = (20 * resources.configuration.fontScale * resources.displayMetrics.density).toInt()
                val metadataText =
                    label(value, app.sugarlicious.model.SugarWearTypography.GLUCOSE_METADATA_SP, palette.argb(CgmAppearanceRole.METADATA_PILL_VALUE), true).apply {
                        includeFontPadding = false
                        text = app.sugarlicious.uishared.glucoseMetadataText(value)
                    }
                addView(
                    ImageView(this@CgmWatchActivity).apply {
                        setImageResource(icon)
                        centerOnGlucoseMetadata(metadataText)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(iconSide, iconSide).apply { marginEnd = 5.dp },
                )
                addView(metadataText)
            }

        val metadata =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(18.dp, 10.dp, 18.dp, 10.dp)
                background = rounded(palette.argb(CgmAppearanceRole.METADATA_PILL_BACKGROUND), android.graphics.Color.TRANSPARENT, 36f)
                tag = "glucose-metadata-pill"
            }

        fun addMetadata(value: String, icon: Int) {
            metadata.addView(
                metadataRow(value, icon),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        addMetadata(presentation.primaryMeta, app.sugarlicious.uishared.R.drawable.ic_metadata_delta)
        if (presentation.secondaryMeta.isNotBlank()) {
            metadata.addView(
                View(this).apply {
                    setBackgroundColor(palette.argb(CgmAppearanceRole.METADATA_PILL_DIVIDER))
                    tag = "metadata-pill-divider"
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp.coerceAtLeast(1)).apply {
                    topMargin = 6.dp
                    bottomMargin = 6.dp
                },
            )
            val age = cgmCompactAgeText(presentation.secondaryMeta)
            addMetadata(age, app.sugarlicious.uishared.R.drawable.ic_metadata_age)
        }
        tile.addView(
            metadata,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addFingerstickAction()
        return tile
    }

    private fun warmupTile(
        presentation: CgmWarmupPresentation,
        palette: CgmAppearancePalette,
    ): LinearLayout {
        val tile =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = WearGlucoseCardStyle.CARD_HEIGHT_DP.dp
                setPadding(
                    WearGlucoseCardStyle.HORIZONTAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.VERTICAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.HORIZONTAL_PADDING_DP.dp,
                    WearGlucoseCardStyle.VERTICAL_PADDING_DP.dp,
                )
                background =
                    rounded(
                        palette.argb(CgmAppearanceRole.MENU_SURFACE),
                        palette.argb(CgmAppearanceRole.MENU_BORDER),
                        WearGlucoseCardStyle.CARD_RADIUS_DP,
                    )
            }

        when (presentation) {
            is CgmWarmupPresentation.CountingDown -> {
                tile.addView(
                    label(
                        presentation.remainingMinutes.toString(),
                        CgmWarmupTokens.COUNTDOWN_TEXT_SP,
                        palette.argb(CgmAppearanceRole.MENU_PRIMARY),
                        true,
                    ).apply { gravity = Gravity.CENTER },
                )
                tile.addView(
                    label(
                        "Minuten verbleibend",
                        CgmWarmupTokens.DESCRIPTION_TEXT_SP,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                        false,
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, CgmWarmupTokens.TEXT_SPACING_DP.dp, 0, CgmWarmupTokens.SECTION_SPACING_DP.dp)
                    },
                )
                tile.addView(
                    label(
                        "Sensoraufwärmphase",
                        CgmWarmupTokens.TITLE_TEXT_SP,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        true,
                    ).apply { gravity = Gravity.CENTER },
                )
            }
            is CgmWarmupPresentation.UnknownRemainingTime -> {
                tile.addView(
                    label(
                        "Sensor wird aufgewärmt",
                        16f,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        false,
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, CgmWarmupTokens.SECTION_SPACING_DP.dp)
                    },
                )
                tile.addView(
                    label(
                        "Sensoraufwärmphase",
                        CgmWarmupTokens.TITLE_TEXT_SP,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        true,
                    ).apply { gravity = Gravity.CENTER },
                )
            }
            is CgmWarmupPresentation.AwaitingFirstReading -> {
                tile.addView(
                    label(
                        "Warte auf erste Messung",
                        18f,
                        palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        true,
                    ).apply { gravity = Gravity.CENTER },
                )
            }
            is CgmWarmupPresentation.NotWarmingUp -> Unit
        }
        return tile
    }

    private fun refreshLiveContent(preserveScroll: Boolean = true) {
        if (!screenBuilt) {
            refreshScreen()
            return
        }
        val palette = appearanceStore.load()
        if (palette != activePalette) {
            buildScreen(palette)
            return
        }
        preserveScrollPosition(preserveScroll) {
            val snapshot = overviewState.snapshot ?: if (pairingStep != null) CgmOverviewSnapshot(CgmPersistedState(), false, emptyList(), null, bluetoothPermission = false, bluetoothEnabled = false) else return
            val state = snapshot.persisted
            val lastReading = state.lastReading
            val effectiveNow = maxOf(overviewState.now, lastReading?.timestampEpochMs ?: 0L)
            val pillState = deriveCgmStatusPillState(state, snapshot.credentialsPresent, effectiveNow, snapshot.lastValidReading)

            statusHost.removeAllViews()
            val warning =
                if (overviewState.failed) {
                    "Anzeigedaten konnten nicht geladen werden"
                } else if (state.sensor != null && state.lastReading?.status == CgmReadingStatus.VALID && pillState != CgmStatusPillState.PAUSED) {
                    null
                } else {
                    cgmSensorWarning(pillState, state.lastReading?.status == CgmReadingStatus.VALID)
                }
            val warmup =
                CgmWarmupPolicy.evaluate(
                    sensor = state.sensor,
                    reading = state.lastReading,
                    collectorEnabled = state.collectorEnabled,
                    nowEpochMs = overviewState.now,
                )

            val mode = cgmGlucoseTileMode(snapshot.pendingAlarms.isNotEmpty(), pairingStep, stopRequested || pairingModel.state.value.offerStop, unlinkFailed)
            val progress = cgmPairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
            val pairingUnchanged =
                mode == CgmGlucoseTileMode.PAIRING &&
                    renderedTileMode == mode &&
                    renderedPairingStep == pairingStep &&
                    pairingErrorCode == pairingFailureMessage &&
                    renderedOtherSensorCount == state.otherSensorCount &&
                    (pairingStep == CgmPairingScreenStep.ENTER_CODE || (renderedPairingProgress == progress && renderedPairingRadio == (snapshot.bluetoothPermission to snapshot.bluetoothEnabled)))
            if (!pairingUnchanged) {
                glucoseHost.removeAllViews()
                glucoseHost.addView(
                    label("Glukose", 11f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY), true).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(WearGlucoseCardStyle.CARD_RADIUS_DP.toInt().dp, 4.dp, 0, 5.dp)
                    },
                )
                when (mode) {
                    CgmGlucoseTileMode.ALARM -> glucoseHost.addView(buildAlarmCard(snapshot.pendingAlarms.first(), palette))
                    CgmGlucoseTileMode.UNLINK_ERROR ->
                        glucoseHost.addView(
                            LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                gravity = Gravity.CENTER
                                minimumHeight = WearGlucoseCardStyle.CARD_HEIGHT_DP.dp
                                setPadding(10.dp, 8.dp, 10.dp, 8.dp)
                                background = rounded(palette.argb(CgmAppearanceRole.MENU_SURFACE), Color.TRANSPARENT, WearGlucoseCardStyle.CARD_RADIUS_DP)
                                addView(label("Entkopplung durch Android nicht bestätigt. Sensor bleibt pausiert und kann nicht als vollständig getrennt gelten. Auf Samsung-Uhren sind BLE-Geräte im Bluetooth-Menü oft nicht sichtbar; bitte die Entkopplung hier erneut versuchen. Die Glukosehistorie bleibt erhalten.", 11f, palette.argb(CgmAppearanceRole.GLUCOSE_ERROR)))
                                unlinkFailureReason?.let { addView(label(it, 11f, palette.argb(CgmAppearanceRole.GLUCOSE_ERROR))) }
                                addView(pill("Entkopplung erneut versuchen") { performSensorUnlink() })
                            },
                        )
                    CgmGlucoseTileMode.PAIRING -> {
                        renderPairingTile(palette, state)
                        pairingErrorCode = pairingFailureMessage
                    }
                    CgmGlucoseTileMode.STOP ->
                        glucoseHost.addView(
                            LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                gravity = Gravity.CENTER
                                tag = "glucose-tile-unlinked"
                                minimumHeight = WearGlucoseCardStyle.CARD_HEIGHT_DP.dp
                                setPadding(10.dp, 8.dp, 10.dp, 8.dp)
                                background = rounded(palette.argb(CgmAppearanceRole.MENU_SURFACE), Color.TRANSPARENT, WearGlucoseCardStyle.CARD_RADIUS_DP)
                                addView(label("Sensor getrennt", 18f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                                addView(pill("App Stoppen") { finishAffinity() })
                                addView(
                                    pill("Zurück") {
                                        stopRequested = false
                                        pairingModel.dismissStop()
                                        refreshLiveContent()
                                    },
                                )
                            },
                        )
                    CgmGlucoseTileMode.SENSOR ->
                        if (warmup is CgmWarmupPresentation.NotWarmingUp) {
                            glucoseHost.addView(glucoseTile(snapshot.transition, warning, palette))
                        } else {
                            glucoseHost.addView(warmupTile(warmup, palette))
                        }
                }
                renderedTileMode = mode
            }

            graphHost.removeAllViews()
            graphHost.addView(graphHeader(palette))
            graphHost.addView(graphTile(snapshot.graphReadings, palette))
            updateGraphOnly(preserveScroll = false)
        }
    }

    internal fun performSensorUnlink(closePairing: Boolean = false) {
        pairingModel.unlink(closePairing)
    }

    private fun consumePairingEffect(effect: CgmPairingEffect?) {
        if (effect == null || effect.token == claimedPairingEffect || companionAccessClosed) return
        claimedPairingEffect = effect.token
        when (effect) {
            is CgmPairingEffect.Access ->
                when (effect.operation) {
                    CgmPairingOperation.START -> {
                        // Pairing a new G7 sensor needs BLE SCAN/CONNECT, not a
                        // CompanionDeviceManager association for a stale old bond.
                        val missing = CgmSystemAccess.missingBluetoothPermissions(this)
                        if (missing.isEmpty()) {
                            pairingModel.accessReady(effect.token)
                        } else {
                            pendingPairingPermissionToken = effect.token
                            runtimePermissions.launch(missing)
                        }
                    }
                    CgmPairingOperation.UNLINK ->
                        prepareUnlinkAccess(
                            { pairingModel.accessReady(effect.token) },
                            { reason -> pairingModel.accessFailed(effect.token, reason) },
                        )
                }
            is CgmPairingEffect.VerifyBond ->
                companionBondAccess.ensureForAuthenticatedSensor(
                    effect.address,
                    { pairingModel.bondReady(effect.token) },
                    { reason -> pairingModel.bondFailed(effect.token, reason) },
                )
        }
    }

    internal fun handleSystemBack() {
        if (stopRequested || pairingModel.state.value.offerStop) {
            stopRequested = false
            pairingModel.dismissStop()
            refreshLiveContent()
        } else if (pairingStep == CgmPairingScreenStep.ENTER_CODE) {
            pairingModel.close()
            refreshScreen()
        } else if (pairingStep == CgmPairingScreenStep.CONNECTING) {
            // Navigating away from a pending scan must not forcibly unpair the sensor.
            pairingModel.close()
            refreshScreen()
        } else {
            finish()
        }
    }

    private fun updateGraphOnly(preserveScroll: Boolean = true) {
        if (!screenBuilt) return
        val palette = activePalette ?: return
        preserveScrollPosition(preserveScroll) {
            val hours = directSettings.graphHours()
            graphPeriodPill.text = "Glukoseverlauf · ${hours}h"
            val readings = overviewState.snapshot?.graphReadings.orEmpty()
            graphView.bind(
                readings = readings,
                palette = palette,
                graphHours = hours,
                activeHistoryIdentity = overviewState.snapshot?.activeHistoryIdentity,
            )
        }
    }

    private inline fun preserveScrollPosition(
        enabled: Boolean,
        update: () -> Unit,
    ) {
        val previousScrollY = if (enabled && screenBuilt) scrollView.scrollY else 0
        update()
        if (enabled && screenBuilt) scrollView.post { scrollView.scrollTo(0, previousScrollY) }
    }

    private fun graphHeader(palette: CgmAppearancePalette): TextView =
        label("Glukoseverlauf · ${directSettings.graphHours()}h", 11f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY), true).apply {
            graphPeriodPill = this
            tag = "graph-scale-control"
            contentDescription = "Graphskalierung"
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(WearGlucoseCardStyle.CARD_RADIUS_DP.toInt().dp, 4.dp, 0, 5.dp)
            setOnClickListener {
                advanceGraphScale(directSettings.graphHours())
                updateGraphOnly()
            }
        }

    private fun graphTile(
        readings: List<CgmReading>,
        palette: CgmAppearancePalette,
    ): FrameLayout {
        val hours = directSettings.graphHours()
        return FrameLayout(this).apply {
            setOnClickListener {
                advanceGraphScale(directSettings.graphHours())
                updateGraphOnly()
            }
            graphView =
                CgmCollectorGraphView(this@CgmWatchActivity).apply {
                    bind(
                        readings = readings,
                        palette = palette,
                        graphHours = hours,
                        activeHistoryIdentity = overviewState.snapshot?.activeHistoryIdentity,
                    )
                }
            addView(graphView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150.dp))
        }
    }

    private fun advanceGraphScale(current: Int) {
        val next = nextDirectGraphHours(current)
        directSettings.saveGraphHours(next)
    }

    private fun pill(textValue: String, action: () -> Unit) =
        CgmTileActionButton(this).apply {
            text = textValue
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = 6.dp
                }
            setOnClickListener { action() }
        }

    private fun pairingPill(textValue: String, action: () -> Unit) =
        CgmTileActionButton(this).apply {
            text = textValue
            usePairingStyle()
            layoutParams = CgmPairingTileLayout.actionLayoutParams(this@CgmWatchActivity)
            setOnClickListener { action() }
        }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(3.dp, 3.dp, 3.dp, 3.dp)
        app.sugarlicious.uishared.SystemTypography
            .apply(this, bold)
    }

    private fun rounded(
        fill: Int,
        stroke: Int,
        radiusDp: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(1.dp, stroke)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun cardParams(top: Int = 8) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, top.dp, 0, 0)
        }

    private fun requestMissingPermissions() {
        val missing =
            buildList {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        if (missing.isNotEmpty()) runtimePermissions.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(systemBackCallback)
        pairingModel.platformDetached()
        companionAccessClosed = true
        companionBondAccess.cancel()
        super.onDestroy()
    }

    private fun trendIndicator(
        trend: Trend,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(WearGlucoseCardStyle.TREND_GAP_DP.dp, 0, 0, 0)
        TrendVisuals.spec(trend)?.let { spec ->
            val style = appearanceStore.trendArrowStyle().renderSpec()
            val height = (WearGlucoseCardStyle.TREND_SIZE_DP * style.scale).toInt().dp
            val width = (WearGlucoseCardStyle.TREND_SIZE_DP * style.scale * spec.aspectRatio).toInt().dp
            val opticalOffset = (CGM_TREND_OPTICAL_OFFSET_DP * resources.displayMetrics.density).roundToInt()
            val containerHeight = height + 2 * opticalOffset
            addView(
                android.widget.FrameLayout(this@CgmWatchActivity).apply {
                    clipChildren = false
                    clipToPadding = false

                    fun arrow(
                        tint: Int,
                        x: Float = 0f,
                        y: Float = 0f,
                    ) = ImageView(this@CgmWatchActivity).apply {
                        setImageResource(TrendDrawableResources.forAsset(spec.asset))
                        setColorFilter(tint, PorterDuff.Mode.SRC_IN)
                        translationX = x
                        translationY = y
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }

                    fun arrowLayoutParams() =
                        android.widget.FrameLayout.LayoutParams(width, height).apply {
                            topMargin = 2 * opticalOffset
                        }

                    if (style.outlineThicknessDp > 0f) {
                        val offset = style.outlineThicknessDp * resources.displayMetrics.density
                        listOf(-offset to 0f, offset to 0f, 0f to -offset, 0f to offset).forEach { (x, y) ->
                            addView(arrow(style.outlineColor, x, y), arrowLayoutParams())
                        }
                    }
                    addView(
                        arrow(style.fillColor).apply { contentDescription = "Trend ${trend.name}" },
                        arrowLayoutParams(),
                    )
                    contentDescription = "Trend ${trend.name}"
                },
                LinearLayout.LayoutParams(width, containerHeight),
            )
        }
    }

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        internal const val EXTRA_SENSOR_UNLINKED = "g7.sensor_unlinked"
        internal const val EXTRA_OPEN_PAIRING = "g7.open_pairing"
        const val PERMISSION_REQUEST = 7
        const val KEY_PAIRING_STEP = "pairing_step"
        const val PAIRING_SUCCESS_DURATION_MS = CGM_PAIRING_SUCCESS_DURATION_MS
    }
}

internal const val CGM_TREND_OPTICAL_OFFSET_DP = 1f
