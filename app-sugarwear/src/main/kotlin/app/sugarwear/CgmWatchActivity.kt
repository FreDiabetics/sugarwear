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
import app.sugarlicious.uishared.TrendDrawableResources
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
            refreshScreen()
        }
    private val overviewModel by viewModels<CollectorViewModel>()
    private val screenHost by lazy { FrameLayout(this) }
    private var overviewState = CgmOverviewState()
    private val systemBackCallback = android.window.OnBackInvokedCallback { handleSystemBack() }
    private val appearanceStore by lazy { CgmAppearanceStore(this) }
    private val directSettings by lazy { CgmDirectToWatchSettingsStore(this) }
    private var activePalette: CgmAppearancePalette? = null
    private var screenBuilt = false
    private var pairingGateVisible = false

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
        companionBondAccess.ensureForUnlink(ready, failed, explicitRetirement = overviewState.snapshot?.persisted?.let(::hasCgmSensorAssociation) == true)
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
    private var pairingScrollView: ScrollView? = null
    private var renderedPairingStep: CgmPairingScreenStep? = null
    private val pairingScrollPositions = mutableMapOf<CgmPairingScreenStep, Int>()
    private var overviewResumed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var scrollView: ScrollView
    private lateinit var statusHost: LinearLayout
    private lateinit var signalLossHeader: app.sugarlicious.uishared.SignalLossWarningView
    private lateinit var collectorStatusTitle: TextView
    private lateinit var collectorAttemptLabel: TextView
    private lateinit var glucoseHost: LinearLayout
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
        consumePairingIntent()
        if (setup.getBoolean("productAccessReview", false)) {
            setup.edit { remove("productAccessReview") }
            val access = CgmSystemAccess.snapshot(this)
            if (!access.bluetoothGranted || !access.notificationsGranted || !access.batteryUnrestricted || !access.exactAlarmsGranted || !access.dndAccessGranted) {
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
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePairingIntent()
        refreshScreen()
    }

    private fun consumePairingIntent() {
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
        screenHost.removeAllViews()
        screenHost.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun refreshScreen() {
        val palette = appearanceStore.load()
        val state =
            overviewState.snapshot?.persisted ?: if (pairingStep != null) {
                CgmPersistedState()
            } else {
                screenBuilt = false
                showScreen(
                    label(if (overviewState.failed) "Anzeigedaten konnten nicht geladen werden. Zum Wiederholen tippen." else "Daten werden geladen", 13f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY)).apply {
                        if (overviewState.failed) setOnClickListener { overviewModel.retry() }
                    },
                )
                return
            }
        pairingModel.sensorChanged(state)
        val currentStep = pairingStep
        if (currentStep != null) {
            val nextError = state.lastError?.takeIf { it.occurredAtEpochMs >= pairingStartedAtEpochMs }?.code
            val progress = cgmPairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
            if (!pairingGateVisible ||
                palette != activePalette ||
                renderedPairingStep != currentStep ||
                nextError != pairingErrorCode ||
                renderedPairingProgress != progress
            ) {
                pairingErrorCode = nextError
                buildPairingGate(palette, state)
            }
        } else if (!screenBuilt || pairingGateVisible || palette != activePalette) {
            pairingGateVisible = false

            buildScreen(palette)
        } else {
            refreshLiveContent()
        }
    }

    private fun buildPairingGate(
        palette: CgmAppearancePalette,
        state: CgmPersistedState,
    ) {
        renderedPairingStep?.let { previous -> pairingScrollView?.let { pairingScrollPositions[previous] = it.scrollY } }
        pairingGateVisible = true
        screenBuilt = false
        activePalette = palette
        val background = palette.argb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(28.dp, 8.dp, 28.dp, 16.dp)
                setBackgroundColor(background)
            }
        val step = pairingStep ?: CgmPairingScreenStep.ENTER_CODE
        val pairingFailed = pairingBondAccessFailure != null || isTerminalCgmPairingFailure(state, pairingStartedAtEpochMs, System.currentTimeMillis())
        val progress = cgmPairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
        renderedPairingProgress = progress
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
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        ImageView(this@CgmWatchActivity).apply {
                            setImageResource(R.drawable.ic_sensor_outline)
                            setColorFilter(palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
                            contentDescription = "Sensor"
                        },
                        LinearLayout.LayoutParams(40.dp, 40.dp),
                    )
                    addView(
                        CgmConnectionDotsView(this@CgmWatchActivity).apply {
                            contentDescription = "7 Verbindungsdots"
                            color =
                                if (pairingFailed) {
                                    palette.argb(
                                        CgmAppearanceRole.GLUCOSE_ERROR,
                                    )
                                } else {
                                    palette.argb(CgmAppearanceRole.MENU_PRIMARY)
                                }
                            isAnimating = !pairingFailed && step == CgmPairingScreenStep.CONNECTING && progress.animate
                        },
                        LinearLayout.LayoutParams(0, 24.dp, 1f).apply { setMargins(10.dp, 0, 10.dp, 0) },
                    )
                    addView(
                        ImageView(this@CgmWatchActivity).apply {
                            setImageResource(R.drawable.ic_watch_device)
                            setColorFilter(palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
                            contentDescription = "Smartwatch"
                        },
                        LinearLayout.LayoutParams(40.dp, 40.dp),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10.dp
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
                        progress.message
                    },
                    16f,
                    palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    true,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(4.dp, 9.dp, 4.dp, 6.dp)
                },
            )
            if (step == CgmPairingScreenStep.CONNECTED) {
                content.addView(
                    ImageView(this).apply {
                        setImageResource(R.drawable.ic_success_check)
                        contentDescription = "Sensor erfolgreich verbunden"
                    },
                    LinearLayout.LayoutParams(52.dp, 52.dp).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = 6.dp
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
                        setPadding(4.dp, 10.dp, 4.dp, 0)
                    },
                )
                content.addView(
                    pill("Erneut versuchen", PillStyle.PRIMARY, palette) {
                        pairingModel.retry()
                        refreshScreen()
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 5.dp
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                )
            }
            if (step == CgmPairingScreenStep.CONNECTING) {
                content.addView(
                    pill("Abbrechen", PillStyle.SECONDARY, palette) {
                        if (pairingAccessPending) {
                            pairingModel.cancelPendingAccess()
                            companionBondAccess.cancel()
                            refreshScreen()
                        } else {
                            performSensorUnlink(closePairing = true)
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 6.dp
                        gravity = Gravity.CENTER_HORIZONTAL
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
                LinearLayout.LayoutParams(42.dp, 42.dp).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = 6.dp
                },
            )
            content.addView(
                label(
                    "Sensorcode eingeben",
                    13f,
                    palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 4.dp, 0, 3.dp)
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
            content.addView(code, LinearLayout.LayoutParams(170.dp, 44.dp).apply { gravity = Gravity.CENTER_HORIZONTAL })
            content.addView(
                pill("Verbinden", PillStyle.PRIMARY, palette) {
                    val entered = code.text?.toString().orEmpty()
                    if (!canStartCgmPairing(pairingStep, pairingStartInFlight, entered)) {
                        if (pairingStartInFlight) return@pill
                        code.error = "Bitte genau vier Ziffern eingeben"
                        return@pill
                    }
                    code.isEnabled = false
                    pairingModel.start(entered)
                    buildPairingGate(palette, state)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 7.dp
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
        }

        val newScrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    clipToPadding = false
                    setBackgroundColor(background)
                    addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }.applyCgmEdgeFade()
        pairingScrollView = newScrollView
        renderedPairingStep = step
        showScreen(
            FrameLayout(this).apply {
                setBackgroundColor(background)
                addView(
                    newScrollView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        newScrollView.post { newScrollView.scrollTo(0, pairingScrollPositions[step] ?: 0) }
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

        signalLossHeader =
            app.sugarlicious.uishared
                .SignalLossWarningView(this)
                .apply { visibility = View.GONE }
        content.addView(signalLossHeader, cardParams(top = 4))
        collectorStatusTitle = label("", 16f, android.graphics.Color.WHITE, true).apply { gravity = Gravity.CENTER }
        collectorAttemptLabel = label("", 12f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY)).apply { gravity = Gravity.CENTER }
        content.addView(collectorStatusTitle, cardParams(top = 2))
        content.addView(collectorAttemptLabel, cardParams(top = 2))
        glucoseHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(glucoseHost, cardParams(top = 4))
        val graphReadings = overviewState.snapshot?.graphReadings.orEmpty()
        content.addView(graphTile(graphReadings, palette), cardParams(top = 7))
        content.addView(header())

        content.addView(
            android.widget.Button(this).apply {
                app.sugarlicious.uishared.WearOverviewSettingsButton.apply(
                    this,
                    R.drawable.ic_settings,
                    palette.argb(CgmAppearanceRole.MENU_SURFACE),
                    palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                )
                setOnClickListener { startActivity(Intent(this@CgmWatchActivity, CgmSettingsActivity::class.java)) }
            },
            cardParams(top = 12).apply {
                marginStart = 6.dp
                marginEnd = 6.dp
            },
        )
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

    private fun glucoseTile(
        reading: CgmReading?,
        palette: CgmAppearancePalette,
    ): LinearLayout {
        val now = overviewState.now
        val basePresentation =
            wearGlucoseCardPresentation(
                WearGlucoseCardInput(
                    valueMgDl = reading?.glucoseMgDl,
                    displayUnit = GlucoseUnit.MG_DL,
                    deltaMgDl = reading?.deltaMgDl,
                    trend = reading?.trend ?: Trend.UNKNOWN,
                    measuredAtEpochMs = reading?.timestampEpochMs,
                    quality =
                        when (reading?.status) {
                            CgmReadingStatus.VALID -> CgmQuality.VALID
                            CgmReadingStatus.SENSOR_ERROR -> CgmQuality.SENSOR_ERROR
                            CgmReadingStatus.INVALID, null -> CgmQuality.INVALID
                        },
                    sourceLabel = "",
                ),
                CgmGraphColorStore(this).readThresholds(),
                now,
                showSignalLossLabel = false,
            )
        val boundary = reading?.takeIf { it.status == CgmReadingStatus.VALID }?.glucoseMgDl.let(::cgmBoundaryDisplay)
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
                presentation.rangeClass == CgmRangeClass.VERY_LOW -> palette.argb(CgmAppearanceRole.GLUCOSE_VERY_LOW)
                presentation.rangeClass == CgmRangeClass.LOW -> palette.argb(CgmAppearanceRole.GLUCOSE_LOW)
                presentation.rangeClass == CgmRangeClass.VERY_HIGH -> palette.argb(CgmAppearanceRole.GLUCOSE_VERY_HIGH)
                presentation.rangeClass == CgmRangeClass.HIGH -> palette.argb(CgmAppearanceRole.GLUCOSE_HIGH)
                else -> palette.argb(CgmAppearanceRole.GLUCOSE_IN_RANGE)
            }
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
                presentation.trend?.let { addView(trendIndicator(it, valueColor)) }
            },
        )

        fun metadataRow(value: String, icon: Int): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                isBaselineAligned = false
                val iconSide = (18 * resources.configuration.fontScale * resources.displayMetrics.density).toInt()
                addView(
                    android.widget.ImageView(this@CgmWatchActivity).apply {
                        setImageResource(icon)
                        imageTintList =
                            android.content.res.ColorStateList
                                .valueOf(0xFFB5B5B5.toInt())
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(iconSide, iconSide).apply { marginEnd = 5.dp },
                )
                addView(
                    label(value, app.sugarlicious.model.SugarWearTypography.GLUCOSE_METADATA_SP, android.graphics.Color.WHITE, true).apply {
                        text = app.sugarlicious.uishared.glucoseMetadataText(value)
                    },
                )
            }
        // Size the centered block to its widest row, then share its left-hand icon column.
        // Centering each row separately shifts the icons whenever the text widths differ.
        val metadata = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun addMetadata(value: String, icon: Int) {
            metadata.addView(
                metadataRow(value, icon),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        addMetadata(presentation.primaryMeta, app.sugarlicious.uishared.R.drawable.ic_metadata_delta)
        if (presentation.secondaryMeta.isNotBlank()) {
            val age =
                presentation.secondaryMeta.replace(
                    Regex("\\b(\\d+) Min\\b"),
                ) { match ->
                    val minutes = match.groupValues[1]
                    "$minutes ${if (minutes == "1") "Minute" else "Minuten"}"
                }
            addMetadata(age, app.sugarlicious.uishared.R.drawable.ic_metadata_age)
        }
        tile.addView(
            metadata,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return tile
    }

    private fun refreshLiveContent(preserveScroll: Boolean = true) {
        if (pairingGateVisible) {
            refreshScreen()
            return
        }
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
            val snapshot = overviewState.snapshot ?: return
            val state = snapshot.persisted
            val pillState = deriveCgmStatusPillState(state, snapshot.credentialsPresent)

            statusHost.removeAllViews()
            if (unlinkFailed) {
                statusHost.addView(label("Trennen fehlgeschlagen. Sensorzuordnung bleibt erhalten. Bluetooth-Verbindung bei Bedarf in den Systemeinstellungen entfernen.", 11f, palette.argb(CgmAppearanceRole.GLUCOSE_ERROR)))
                unlinkFailureReason?.let { statusHost.addView(label(it, 11f, palette.argb(CgmAppearanceRole.GLUCOSE_ERROR))) }
            }
            val warning = if (overviewState.failed) "Anzeigedaten konnten nicht geladen werden" else cgmSensorWarning(pillState, state.lastReading?.status == CgmReadingStatus.VALID)
            signalLossHeader.visibility = if (warning == null) View.GONE else View.VISIBLE
            warning?.let(signalLossHeader::setMessage)
            signalLossHeader.setOnClickListener(if (overviewState.failed) View.OnClickListener { overviewModel.retry() } else null)
            val attempt = collectorAttemptPresentation(state, snapshot.transition, overviewState.now)
            collectorStatusTitle.text = if (pillState == CgmStatusPillState.PAUSED) "Datenempfang pausiert" else attempt?.title.orEmpty()
            collectorAttemptLabel.text = attempt?.phase.orEmpty()
            collectorStatusTitle.visibility = if (warning != null || collectorStatusTitle.text.isBlank()) View.GONE else View.VISIBLE
            collectorAttemptLabel.visibility = if (attempt == null) View.GONE else View.VISIBLE
            glucoseHost.removeAllViews()
            glucoseHost.addView(
                label("Gewebeglukose", 11f, 0xFFFFFFFF.toInt(), false).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(WearGlucoseCardStyle.CARD_RADIUS_DP.toInt().dp, 4.dp, 0, 5.dp)
                },
            )
            glucoseHost.addView(glucoseTile(state.lastReading.takeIf { state.collectorEnabled && !overviewState.failed }, palette))
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
                prepareUnlinkAccess(
                    { pairingModel.accessReady(effect.token) },
                    { reason -> pairingModel.accessFailed(effect.token, reason) },
                )
            is CgmPairingEffect.VerifyBond ->
                companionBondAccess.ensureForAuthenticatedSensor(
                    effect.address,
                    { pairingModel.bondReady(effect.token) },
                    { reason -> pairingModel.bondFailed(effect.token, reason) },
                )
        }
    }

    internal fun handleSystemBack() {
        if (pairingStep == CgmPairingScreenStep.ENTER_CODE) {
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
            val latestTimestamp =
                overviewState.snapshot
                    ?.persisted
                    ?.lastReading
                    ?.timestampEpochMs
            val ageMinutes = latestTimestamp?.let { ((overviewState.now - it).coerceAtLeast(0L) / 60_000L) }
            graphPeriodPill.text =
                buildString {
                    append(hours).append('h')
                    if (ageMinutes != null) append(" • ").append(ageMinutes).append('m')
                }
            val readings = overviewState.snapshot?.graphReadings.orEmpty()
            graphView.bind(
                readings = readings,
                palette = palette,
                graphHours = hours,
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
                    )
                }
            addView(graphView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150.dp))

            graphPeriodPill =
                label("${hours}h", 11f, palette.argb(CgmAppearanceRole.MENU_TEXT_SECONDARY), true).apply {
                    tag = "graph-scale-control"
                    contentDescription = "Graphskalierung"
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(4.dp, 0, 0, 0)
                    minWidth = 0
                    minHeight = 0
                    setOnClickListener {
                        advanceGraphScale(directSettings.graphHours())
                        updateGraphOnly()
                    }
                }
            graphView.onHeaderColorChanged = { graphPeriodPill.setTextColor(it) }
            addView(
                graphPeriodPill,
                FrameLayout.LayoutParams(72.dp, 34.dp, Gravity.TOP or Gravity.START).apply {
                    topMargin = 3.dp
                    marginStart = 4.dp
                },
            )
        }
    }

    private fun advanceGraphScale(current: Int) {
        val next = nextDirectGraphHours(current)
        directSettings.saveGraphHours(next)
    }

    private enum class PillStyle { PRIMARY, SECONDARY, DANGER }

    private fun pill(
        textValue: String,
        style: PillStyle,
        palette: CgmAppearancePalette,
        action: () -> Unit,
    ) = TextView(this).apply {
        text = textValue
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = 40.dp
        setPadding(13.dp, 8.dp, 13.dp, 8.dp)
        app.sugarlicious.uishared.SystemTypography
            .apply(this, true)
        val (fill, textColor, stroke) =
            when (style) {
                PillStyle.PRIMARY ->
                    Triple(
                        palette.argb(CgmAppearanceRole.MENU_PRIMARY),
                        palette.argb(CgmAppearanceRole.MENU_BACKGROUND),
                        palette.argb(CgmAppearanceRole.MENU_PRIMARY),
                    )
                PillStyle.SECONDARY ->
                    Triple(
                        palette.argb(CgmAppearanceRole.MENU_SURFACE),
                        palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        palette.argb(CgmAppearanceRole.MENU_BORDER),
                    )
                PillStyle.DANGER ->
                    Triple(
                        withAlpha(palette.argb(CgmAppearanceRole.GLUCOSE_ERROR), 36),
                        palette.argb(CgmAppearanceRole.GLUCOSE_ERROR),
                        palette.argb(CgmAppearanceRole.GLUCOSE_ERROR),
                    )
            }
        setTextColor(textColor)
        background = rounded(fill, stroke, 999f)
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
        color: Int,
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
        internal const val EXTRA_OPEN_PAIRING = "g7.open_pairing"
        const val PERMISSION_REQUEST = 7
        const val KEY_PAIRING_STEP = "pairing_step"
        const val PAIRING_SUCCESS_DURATION_MS = CGM_PAIRING_SUCCESS_DURATION_MS
    }
}

internal const val CGM_TREND_OPTICAL_OFFSET_DP = 1f
