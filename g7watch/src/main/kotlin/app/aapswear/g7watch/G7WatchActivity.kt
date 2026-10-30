package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
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
import androidx.core.content.edit
import androidx.core.view.isVisible
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7PersistedState
import app.aapswear.model.CgmQuality
import app.aapswear.model.CgmRangeClass
import app.aapswear.model.GlucoseTrendSizing
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.Trend
import app.aapswear.model.TrendVisuals
import app.aapswear.model.WearGlucoseCardInput
import app.aapswear.model.WearGlucoseCardStyle
import app.aapswear.model.cgmBoundaryDisplay
import app.aapswear.model.wearGlucoseCardPresentation
import app.aapswear.uishared.TrendDrawableResources
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal fun hasUsableCollectorSession(
    reading: CgmReading?,
    sensorId: String?,
): Boolean = reading != null && reading.status == CgmReadingStatus.VALID && reading.sensorId == sensorId

internal fun hasG7SensorAssociation(state: G7PersistedState): Boolean =
    state.sensor != null &&
        (
            !state.sensor?.deviceAddress.isNullOrBlank() ||
                state.authenticationState == app.aapswear.g7.G7AuthenticationState.AUTHENTICATED ||
                hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
        )

@Suppress("UNUSED_PARAMETER")
internal fun isG7PairingAttemptActive(
    state: G7PersistedState,
    nowEpochMs: Long,
): Boolean = state.collectorEnabled && state.pairingAttemptId != null && state.lastError?.recoverable != false

internal fun pairingSuccessRemainingMs(
    deadlineEpochMs: Long,
    nowEpochMs: Long,
): Long = (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)

internal enum class G7PairingScreenStep { ENTER_CODE, CONNECTING, CONNECTED }

@Suppress("UNUSED_PARAMETER")
internal fun initialG7PairingScreenStep(
    state: G7PersistedState,
    restored: G7PairingScreenStep? = null,
): G7PairingScreenStep? {
    if (restored == G7PairingScreenStep.ENTER_CODE) return restored
    if (restored == G7PairingScreenStep.CONNECTING) return restored
    return restored?.takeIf { it == G7PairingScreenStep.CONNECTED }
}

internal fun advanceG7PairingScreen(
    current: G7PairingScreenStep,
    state: G7PersistedState,
): G7PairingScreenStep =
    if (
        current == G7PairingScreenStep.CONNECTING &&
        !state.sensor?.deviceAddress.isNullOrBlank() &&
        hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
    ) {
        G7PairingScreenStep.CONNECTED
    } else {
        current
    }

internal fun canStartG7Pairing(
    step: G7PairingScreenStep?,
    inFlight: Boolean,
    code: String,
): Boolean = step == G7PairingScreenStep.ENTER_CODE && !inFlight && code.length == 4 && code.all(Char::isDigit)

internal fun shouldScheduleG7PairingCompletion(
    step: G7PairingScreenStep?,
    alreadyScheduled: Boolean,
): Boolean = step == G7PairingScreenStep.CONNECTED && !alreadyScheduled

@Suppress("UNUSED_PARAMETER")
internal fun isTerminalG7PairingFailure(
    state: G7PersistedState,
    pairingStartedAtEpochMs: Long,
    nowEpochMs: Long,
): Boolean {
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= pairingStartedAtEpochMs }
    return error?.code == "G7-AUTH-204" || error?.recoverable == false
}

internal fun nextDirectGraphHours(current: Int): Int {
    val options = G7DirectToWatchSettingsStore.HOUR_OPTIONS
    val index = options.indexOf(current)
    return options[if (index < 0) 0 else (index + 1) % options.size]
}

class G7WatchActivity : Activity() {
    private val systemBackCallback = android.window.OnBackInvokedCallback { handleSystemBack() }
    private val appearanceStore by lazy { G7AppearanceStore(this) }
    private val directSettings by lazy { G7DirectToWatchSettingsStore(this) }
    private var readingObserverRegistered = false
    private var activePalette: G7AppearancePalette? = null
    private var screenBuilt = false
    private var pairingGateVisible = false
    private var pairingStep: G7PairingScreenStep? = null
    private var pairingCodeDraft = ""
    private var pairingStartInFlight = false
    private var pairingAccessPending = false
    private var pairingStartGeneration = 0L
    internal var connectSensor: (android.content.Context, String) -> Unit = { context, code -> connectG7SensorToThisWatch(context, code) }
    private var unlinkInFlight = false
    private var unlinkFailed = false
    private var unlinkFailureReason: String? = null
    private val companionBondAccess by lazy { G7CompanionBondAccess(this) }
    private var companionAccessClosed = false
    private var pairingBondAccessInFlight = false
    private var pairingBondAccessReady = false
    private var pairingBondAccessFailure: String? = null
    internal var prepareUnlinkAccess: (onReady: () -> Unit, onFailure: (String) -> Unit) -> Unit = { ready, failed ->
        executeAssociationTask {
            val release = runCatching { releaseG7CollectorOwnership(applicationContext) }
            mainHandler.post {
                if (!companionAccessClosed && !isFinishing && !isDestroyed) {
                    if (release.isSuccess) {
                        companionBondAccess.ensureForUnlink(ready, failed)
                    } else {
                        failed("Sensorempfang konnte nicht beendet werden")
                    }
                }
            }
        }
    }
    internal var unlinkSensor: (android.content.Context) -> G7UnlinkResult = { unlinkG7Sensor(it) }
    internal var executeAssociationTask: (() -> Unit) -> Unit = { pairingExecutor.execute(it) }
    private var pairingCompletionScheduled = false
    private var pairingErrorCode: String? = null
    private var renderedPairingProgress: G7PairingProgress? = null
    private var pairingStartedAtEpochMs = Long.MAX_VALUE
    private var pairingFailureMessage: String? = null
    private var pairingScrollView: ScrollView? = null
    private var renderedPairingStep: G7PairingScreenStep? = null
    private val pairingScrollPositions = mutableMapOf<G7PairingScreenStep, Int>()
    private val pairingExecutor = Executors.newSingleThreadExecutor()
    private val pairingSuccessFinish =
        Runnable {
            if (!pairingCompletionScheduled) return@Runnable
            pairingCompletionScheduled = false
            pairingStep = null
            pairingGateVisible = false
            if (!isFinishing && !isDestroyed) buildScreen(appearanceStore.load())
        }
    private val pairingRefresh =
        object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed && pairingGateVisible) {
                    refreshScreen()
                    mainHandler.postDelayed(this, 1_000L)
                }
            }
        }
    private val graphClockRefresh =
        object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed && screenBuilt && G7SensorStateStore(this@G7WatchActivity).read().collectorEnabled) refreshLiveContent()
            }
        }
    private var overviewResumed = false
    private val collectorPreferences by lazy { getSharedPreferences("g7_collector_state", MODE_PRIVATE) }
    private val collectorPhaseRefresh = Runnable { if (overviewResumed && screenBuilt && !pairingGateVisible) refreshLiveContent() }
    private val collectorPhaseListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "state" && overviewResumed && screenBuilt && !pairingGateVisible) {
                val transition = G7CollectorUiTransitionStore.read(this)
                if (transition != null && (collectorAttemptPresentation(G7SensorStateStore(this).read(), transition, System.currentTimeMillis()) != null || collectorAttemptLabel.isVisible)) {
                    mainHandler.removeCallbacks(collectorPhaseRefresh)
                    mainHandler.postDelayed(collectorPhaseRefresh, 50L)
                }
            }
        }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var scrollView: ScrollView
    private lateinit var statusHost: LinearLayout
    private lateinit var signalLossHeader: app.aapswear.uishared.SignalLossWarningView
    private lateinit var collectorStatusTitle: TextView
    private lateinit var collectorAttemptLabel: TextView
    private val attemptExpiryRefresh = Runnable { if (screenBuilt && !pairingGateVisible) refreshLiveContent() }
    private lateinit var glucoseHost: LinearLayout
    private lateinit var graphView: G7CollectorGraphView
    private lateinit var graphPeriodPill: TextView
    private val readingObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!isFinishing && !isDestroyed) refreshScreen()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback)
        val setup = getSharedPreferences("g7_setup", MODE_PRIVATE)
        // Imported pending cycles refer to the old UID's alarms; rearm them for this package.
        G7RuntimeReconciler.reconcile(this, if (setup.getBoolean("productAccessReview", false)) G7RuntimeEntryPoint.PACKAGE_REPLACED else G7RuntimeEntryPoint.WATCH_APP)
        // Pairing credentials must not be copied into the Activity saved-state bundle.
        pairingCodeDraft = ""
        val restoredStep =
            savedInstanceState?.getString(KEY_PAIRING_STEP)?.let {
                runCatching { G7PairingScreenStep.valueOf(it) }.getOrNull()
            }
        pairingStep = initialG7PairingScreenStep(G7SensorStateStore(this).read(), restoredStep)
        if (pairingStep == G7PairingScreenStep.CONNECTING) {
            pairingStartedAtEpochMs = G7SensorStateStore(this).read().pairingStartedAtEpochMs ?: System.currentTimeMillis()
        }
        consumePairingIntent()
        if (setup.getBoolean("productAccessReview", false)) {
            setup.edit { remove("productAccessReview") }
            val access = G7SystemAccess.snapshot(this)
            if (!access.bluetoothGranted || !access.notificationsGranted || !access.batteryUnrestricted || !access.exactAlarmsGranted || !access.dndAccessGranted) {
                startActivity(Intent(this, G7SetupActivity::class.java))
            }
        } else if (!setup.getBoolean("complete", false)) {
            if (G7SensorStateStore(this).read().sensor != null || G7CredentialStore(this).read() != null) {
                // Existing installations keep their running sensor; onboarding is for fresh installs.
                setup.edit { putBoolean("complete", true) }
            } else {
                startActivity(Intent(this, G7SetupActivity::class.java))
            }
        } else {
            requestMissingPermissions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PAIRING_STEP, pairingStep?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePairingIntent()
        refreshScreen()
    }

    private fun consumePairingIntent() {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PAIRING, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PAIRING)
            if (!hasG7SensorAssociation(G7SensorStateStore(this).read())) {
                pairingStep = G7PairingScreenStep.ENTER_CODE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overviewResumed = true
        collectorPreferences.registerOnSharedPreferenceChangeListener(collectorPhaseListener)
        // Rehydrate the separately installed Vigil complication provider as well. This also
        // repairs settings after that package was reinstalled while SugarWear data stayed intact.
        directSettings.sync()
        registerReadingObserver()
        refreshScreen()
        scheduleOverviewClock()
    }

    private fun scheduleOverviewClock() {
        mainHandler.removeCallbacks(graphClockRefresh)
        val state = G7SensorStateStore(this).read()
        if (overviewResumed && !pairingGateVisible && state.collectorEnabled && state.sensor != null) {
            val now = System.currentTimeMillis()
            mainHandler.postDelayed(graphClockRefresh, 60_000L - now % 60_000L)
        }
    }

    override fun onPause() {
        overviewResumed = false
        collectorPreferences.unregisterOnSharedPreferenceChangeListener(collectorPhaseListener)
        mainHandler.removeCallbacks(collectorPhaseRefresh)
        mainHandler.removeCallbacks(pairingRefresh)
        mainHandler.removeCallbacks(graphClockRefresh)
        mainHandler.removeCallbacks(attemptExpiryRefresh)
        unregisterReadingObserver()
        super.onPause()
    }

    private fun registerReadingObserver() {
        if (readingObserverRegistered) return
        contentResolver.registerContentObserver(
            G7ReadingProvider.CONTENT_URI,
            true,
            readingObserver,
        )
        readingObserverRegistered = true
    }

    private fun unregisterReadingObserver() {
        if (!readingObserverRegistered) return
        contentResolver.unregisterContentObserver(readingObserver)
        readingObserverRegistered = false
    }

    private fun refreshScreen() {
        val palette = appearanceStore.load()
        val state = G7SensorStateStore(this).read()
        val currentStep = pairingStep
        if (currentStep != null) {
            val nextStep = if (pairingStartInFlight) currentStep else advanceG7PairingScreen(currentStep, state)
            if (nextStep == G7PairingScreenStep.CONNECTED && android.os.Build.VERSION.SDK_INT < 36) pairingBondAccessReady = true
            if (nextStep == G7PairingScreenStep.CONNECTED && !pairingBondAccessReady) {
                pairingStep = G7PairingScreenStep.CONNECTING
                if (!pairingGateVisible) buildPairingGate(palette, state)
                if (!pairingBondAccessInFlight && pairingBondAccessFailure == null) {
                    pairingBondAccessInFlight = true
                    val address = state.sensor?.deviceAddress
                    if (address.isNullOrBlank()) {
                        pairingBondAccessInFlight = false
                        // Address publication can lag the first reading briefly. Keep observing
                        // the same session instead of latching a recoverable publication race.
                        buildPairingGate(palette, state)
                    } else {
                        companionBondAccess.ensureForAuthenticatedSensor(
                            address,
                            onReady = {
                                pairingBondAccessInFlight = false
                                pairingBondAccessReady = true
                                if (!companionAccessClosed && !isFinishing && !isDestroyed) refreshScreen()
                            },
                            onFailure = { reason ->
                                pairingBondAccessInFlight = false
                                pairingBondAccessFailure = reason
                                if (!companionAccessClosed && !isFinishing && !isDestroyed) buildPairingGate(palette, G7SensorStateStore(this).read())
                            },
                        )
                    }
                }
                return
            }
            val nextError =
                state.lastError
                    ?.takeIf { it.occurredAtEpochMs >= pairingStartedAtEpochMs }
                    ?.code
                    .takeIf { nextStep == G7PairingScreenStep.CONNECTING }
            if (nextStep != currentStep) {
                pairingStep = nextStep
                pairingStartInFlight = false
                buildPairingGate(palette, state)
                schedulePairingCompletion()
            } else if (!pairingGateVisible ||
                palette != activePalette ||
                nextError != pairingErrorCode ||
                renderedPairingProgress != g7PairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
            ) {
                pairingErrorCode = nextError
                buildPairingGate(palette, state)
            }
            if (nextStep == G7PairingScreenStep.CONNECTED) schedulePairingCompletion()
        } else if (!screenBuilt || pairingGateVisible || palette != activePalette) {
            pairingGateVisible = false
            mainHandler.removeCallbacks(pairingRefresh)
            buildScreen(palette)
        } else {
            refreshLiveContent()
        }
    }

    private fun buildPairingGate(
        palette: G7AppearancePalette,
        state: G7PersistedState,
    ) {
        renderedPairingStep?.let { previous -> pairingScrollView?.let { pairingScrollPositions[previous] = it.scrollY } }
        pairingGateVisible = true
        screenBuilt = false
        activePalette = palette
        val background = palette.argb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(28.dp, 8.dp, 28.dp, 16.dp)
                setBackgroundColor(background)
            }
        val step = pairingStep ?: G7PairingScreenStep.ENTER_CODE
        val pairingFailed = pairingBondAccessFailure != null || isTerminalG7PairingFailure(state, pairingStartedAtEpochMs, System.currentTimeMillis())
        val progress = g7PairingProgress(state, pairingStartedAtEpochMs, pairingStartInFlight, pairingBondAccessInFlight, pairingBondAccessFailure)
        renderedPairingProgress = progress
        content.addView(
            label(
                when (step) {
                    G7PairingScreenStep.ENTER_CODE -> "Sensor verbinden"
                    G7PairingScreenStep.CONNECTING -> "Sensor verbinden"
                    G7PairingScreenStep.CONNECTED -> "Sensor verbunden"
                },
                15f,
                palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                true,
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            },
        )
        if (step == G7PairingScreenStep.CONNECTING || step == G7PairingScreenStep.CONNECTED) {
            content.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        ImageView(this@G7WatchActivity).apply {
                            setImageResource(R.drawable.ic_sensor_outline)
                            setColorFilter(palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
                            contentDescription = "Sensor"
                        },
                        LinearLayout.LayoutParams(40.dp, 40.dp),
                    )
                    addView(
                        G7ConnectionDotsView(this@G7WatchActivity).apply {
                            contentDescription = "7 Verbindungsdots"
                            color =
                                if (pairingFailed) {
                                    palette.argb(
                                        G7AppearanceRole.GLUCOSE_ERROR,
                                    )
                                } else {
                                    palette.argb(G7AppearanceRole.MENU_PRIMARY)
                                }
                            isAnimating = !pairingFailed && step == G7PairingScreenStep.CONNECTING && progress.animate
                        },
                        LinearLayout.LayoutParams(0, 24.dp, 1f).apply { setMargins(10.dp, 0, 10.dp, 0) },
                    )
                    addView(
                        ImageView(this@G7WatchActivity).apply {
                            setImageResource(R.drawable.ic_watch_device)
                            setColorFilter(palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
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
                        G7PairingScreenStep.CONNECTED
                    ) {
                        "Sensor erfolgreich verbunden"
                    } else {
                        progress.message
                    },
                    16f,
                    palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                    true,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(4.dp, 9.dp, 4.dp, 6.dp)
                },
            )
            if (step == G7PairingScreenStep.CONNECTED) {
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
            if (step == G7PairingScreenStep.CONNECTING && pairingFailed) {
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
                        palette.argb(G7AppearanceRole.GLUCOSE_ERROR),
                        true,
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(4.dp, 10.dp, 4.dp, 0)
                    },
                )
                content.addView(
                    pill("Erneut versuchen", PillStyle.PRIMARY, palette) {
                        pairingStartInFlight = false
                        if (pairingBondAccessFailure != null) {
                            pairingBondAccessFailure = null
                            refreshScreen()
                        } else {
                            pairingStep = G7PairingScreenStep.ENTER_CODE
                            buildPairingGate(palette, state)
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 5.dp
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                )
            }
            if (step == G7PairingScreenStep.CONNECTING) {
                content.addView(
                    pill("Abbrechen", PillStyle.SECONDARY, palette) {
                        if (pairingAccessPending) {
                            ++pairingStartGeneration
                            pairingAccessPending = false
                            pairingStartInFlight = false
                            pairingStep = null
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
                    setColorFilter(palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY), PorterDuff.Mode.SRC_IN)
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
                    palette.argb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 4.dp, 0, 3.dp)
                    maxLines = 1
                },
            )
            pairingFailureMessage?.let { message ->
                content.addView(label(message, 11f, palette.argb(G7AppearanceRole.GLUCOSE_ERROR), true).apply { gravity = Gravity.CENTER })
            }
            val code =
                EditText(this).apply {
                    hint = "0000"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(android.text.InputFilter.LengthFilter(4))
                    gravity = Gravity.CENTER
                    textSize = 24f
                    setTextColor(palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                    setHintTextColor(palette.argb(G7AppearanceRole.MENU_TEXT_SECONDARY))
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
                    if (!canStartG7Pairing(pairingStep, pairingStartInFlight, entered)) {
                        if (pairingStartInFlight) return@pill
                        code.error = "Bitte genau vier Ziffern eingeben"
                        return@pill
                    }
                    pairingStartInFlight = true
                    pairingFailureMessage = null
                    pairingBondAccessReady = false
                    pairingBondAccessFailure = null
                    pairingStartedAtEpochMs = System.currentTimeMillis()
                    code.isEnabled = false
                    pairingStep = G7PairingScreenStep.CONNECTING
                    buildPairingGate(palette, G7SensorStateStore(this).read())
                    val startGeneration = ++pairingStartGeneration
                    pairingAccessPending = true

                    fun failed(reason: String) {
                        if (startGeneration != pairingStartGeneration) return
                        pairingAccessPending = false
                        pairingStartInFlight = false
                        pairingStep = G7PairingScreenStep.ENTER_CODE
                        pairingFailureMessage = reason
                        if (!companionAccessClosed && !isFinishing && !isDestroyed) {
                            buildPairingGate(appearanceStore.load(), G7SensorStateStore(this).read())
                        }
                    }
                    prepareUnlinkAccess(
                        {
                            if (startGeneration == pairingStartGeneration && !companionAccessClosed && !isFinishing && !isDestroyed) {
                                pairingAccessPending = false
                                executeAssociationTask {
                                    if (startGeneration == pairingStartGeneration) {
                                        val result = runCatching { connectSensor(applicationContext, entered) }
                                        mainHandler.post {
                                            if (startGeneration == pairingStartGeneration) {
                                                if (result.isSuccess) {
                                                    pairingStartInFlight = false
                                                    if (!companionAccessClosed && !isFinishing && !isDestroyed) refreshScreen()
                                                } else {
                                                    failed("Verbindung konnte nicht gestartet werden. Bitte erneut versuchen.")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        { reason -> failed(reason) },
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 7.dp
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
        }

        val newScrollView =
            G7EdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    clipToPadding = false
                    setBackgroundColor(background)
                    addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }.applyG7EdgeFade()
        pairingScrollView = newScrollView
        renderedPairingStep = step
        setContentView(
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
        mainHandler.removeCallbacks(pairingRefresh)
        // Poll state while pairing, but rebuild only when the semantic pairing presentation
        // changes. The animated dots own their own animation and must not recreate the page.
        if (step == G7PairingScreenStep.CONNECTING) mainHandler.postDelayed(pairingRefresh, 1_000L)
    }

    private fun schedulePairingCompletion() {
        if (!shouldScheduleG7PairingCompletion(pairingStep, pairingCompletionScheduled)) return
        pairingCompletionScheduled = true
        mainHandler.removeCallbacks(pairingSuccessFinish)
        mainHandler.postDelayed(pairingSuccessFinish, PAIRING_SUCCESS_DURATION_MS)
    }

    private fun buildScreen(palette: G7AppearancePalette) {
        val previousScrollY = if (screenBuilt) scrollView.scrollY else 0
        activePalette = palette
        val background = palette.argb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(18.dp, 30.dp, 18.dp, 30.dp)
                setBackgroundColor(background)
            }

        signalLossHeader =
            app.aapswear.uishared
                .SignalLossWarningView(this)
                .apply { visibility = View.GONE }
        content.addView(signalLossHeader, cardParams(top = 4))
        collectorStatusTitle = label("", 16f, android.graphics.Color.WHITE, true).apply { gravity = Gravity.CENTER }
        collectorAttemptLabel = label("", 12f, palette.argb(G7AppearanceRole.MENU_TEXT_SECONDARY)).apply { gravity = Gravity.CENTER }
        content.addView(collectorStatusTitle, cardParams(top = 2))
        content.addView(collectorAttemptLabel, cardParams(top = 2))
        glucoseHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(glucoseHost, cardParams(top = 4))
        val graphReadings = G7ReadingDatabase(this).use { it.query(limit = 300) }
        content.addView(graphTile(graphReadings, palette), cardParams(top = 7))
        content.addView(header())

        content.addView(
            android.widget.Button(this).apply {
                app.aapswear.uishared.WearOverviewSettingsButton.apply(
                    this,
                    R.drawable.ic_settings,
                    palette.argb(G7AppearanceRole.MENU_SURFACE),
                    palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                )
                setOnClickListener { startActivity(Intent(this@G7WatchActivity, G7SettingsActivity::class.java)) }
            },
            cardParams(top = 12).apply {
                marginStart = 6.dp
                marginEnd = 6.dp
            },
        )
        scrollView =
            G7EdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    setBackgroundColor(background)
                    addView(content)
                }.applyG7EdgeFade()
        setContentView(scrollView)
        screenBuilt = true
        refreshLiveContent(preserveScroll = false)
        if (previousScrollY > 0) scrollView.post { scrollView.scrollTo(0, previousScrollY) }
    }

    private fun header() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            statusHost =
                LinearLayout(this@G7WatchActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            addView(statusHost)
        }

    private fun glucoseTile(
        reading: CgmReading?,
        palette: G7AppearancePalette,
    ): LinearLayout {
        val now = System.currentTimeMillis()
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
                G7GraphColorStore(this).readThresholds(),
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
                presentation.freshness == app.aapswear.model.Freshness.SIGNAL_LOSS -> 0xFF9E9E9E.toInt()
                !presentation.displayable -> palette.argb(G7AppearanceRole.GLUCOSE_NO_SOURCE)
                presentation.rangeClass == CgmRangeClass.VERY_LOW -> palette.argb(G7AppearanceRole.GLUCOSE_VERY_LOW)
                presentation.rangeClass == CgmRangeClass.LOW -> palette.argb(G7AppearanceRole.GLUCOSE_LOW)
                presentation.rangeClass == CgmRangeClass.VERY_HIGH -> palette.argb(G7AppearanceRole.GLUCOSE_VERY_HIGH)
                presentation.rangeClass == CgmRangeClass.HIGH -> palette.argb(G7AppearanceRole.GLUCOSE_HIGH)
                else -> palette.argb(G7AppearanceRole.GLUCOSE_IN_RANGE)
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
                        palette.argb(G7AppearanceRole.MENU_SURFACE),
                        palette.argb(G7AppearanceRole.MENU_BORDER),
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
                gravity = Gravity.CENTER
                isBaselineAligned = false
                val iconSide = (18 * resources.configuration.fontScale * resources.displayMetrics.density).toInt()
                addView(
                    android.widget.ImageView(this@G7WatchActivity).apply {
                        setImageResource(icon)
                        imageTintList =
                            android.content.res.ColorStateList
                                .valueOf(0xFFB5B5B5.toInt())
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(iconSide, iconSide).apply { marginEnd = 5.dp },
                )
                addView(
                    label(value, app.aapswear.model.SugarWearTypography.GLUCOSE_METADATA_SP, android.graphics.Color.WHITE, true).apply {
                        text = app.aapswear.uishared.glucoseMetadataText(value)
                    },
                )
            }
        tile.addView(metadataRow(presentation.primaryMeta, app.aapswear.uishared.R.drawable.ic_metadata_delta))
        if (presentation.secondaryMeta.isNotBlank()) {
            tile.addView(metadataRow(presentation.secondaryMeta, app.aapswear.uishared.R.drawable.ic_metadata_age))
        }
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
            val state = G7SensorStateStore(this).read()
            val credentials = G7CredentialStore(this).read()
            val pillState = deriveG7StatusPillState(state, credentials != null)

            statusHost.removeAllViews()
            if (unlinkFailed) {
                statusHost.addView(label("Trennen fehlgeschlagen. Sensorzuordnung bleibt erhalten. Bluetooth-Verbindung bei Bedarf in den Systemeinstellungen entfernen.", 11f, palette.argb(G7AppearanceRole.GLUCOSE_ERROR)))
                unlinkFailureReason?.let { statusHost.addView(label(it, 11f, palette.argb(G7AppearanceRole.GLUCOSE_ERROR))) }
            }
            val warning = g7SensorWarning(pillState, state.lastReading?.status == CgmReadingStatus.VALID)
            signalLossHeader.visibility = if (warning == null) View.GONE else View.VISIBLE
            warning?.let(signalLossHeader::setMessage)
            val attempt = collectorAttemptPresentation(state, G7CollectorUiTransitionStore.read(this), System.currentTimeMillis())
            collectorStatusTitle.text = if (pillState == G7StatusPillState.PAUSED) "Datenempfang pausiert" else attempt?.title.orEmpty()
            collectorAttemptLabel.text = attempt?.phase.orEmpty()
            collectorStatusTitle.visibility = if (warning != null || collectorStatusTitle.text.isBlank()) View.GONE else View.VISIBLE
            collectorAttemptLabel.visibility = if (attempt == null) View.GONE else View.VISIBLE
            mainHandler.removeCallbacks(attemptExpiryRefresh)
            attempt?.expiresAt?.let { deadline -> mainHandler.postDelayed(attemptExpiryRefresh, (deadline - System.currentTimeMillis()).coerceAtLeast(1L)) }
            glucoseHost.removeAllViews()
            glucoseHost.addView(
                label("Gewebeglukose", 11f, 0xFFFFFFFF.toInt(), false).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(WearGlucoseCardStyle.CARD_RADIUS_DP.toInt().dp, 4.dp, 0, 5.dp)
                },
            )
            glucoseHost.addView(glucoseTile(state.lastReading.takeIf { state.collectorEnabled }, palette))
            updateGraphOnly(preserveScroll = false)
        }
        scheduleOverviewClock()
    }

    internal fun performSensorUnlink(closePairing: Boolean = false) {
        if (unlinkInFlight) return
        unlinkInFlight = true
        unlinkFailureReason = null
        if (!closePairing) refreshLiveContent()

        fun finish(detached: Boolean) {
            unlinkInFlight = false
            unlinkFailed = !detached
            if (closePairing) {
                pairingStep = null
                pairingStartInFlight = false
                pairingCompletionScheduled = false
                mainHandler.removeCallbacks(pairingSuccessFinish)
            }
            if (!companionAccessClosed && !isFinishing && !isDestroyed) refreshScreen()
        }
        prepareUnlinkAccess(
            {
                if (!companionAccessClosed && !isFinishing && !isDestroyed) {
                    executeAssociationTask {
                        val detached = runCatching { unlinkSensor(applicationContext).detached }.getOrDefault(false)
                        mainHandler.post { finish(detached) }
                    }
                }
            },
            { reason ->
                unlinkFailureReason = reason
                finish(false)
            },
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!companionBondAccess.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    internal fun handleSystemBack() {
        if (pairingStep == G7PairingScreenStep.ENTER_CODE) {
            pairingStep = null
            pairingCodeDraft = ""
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
            val latestTimestamp = G7SensorStateStore(this).read().lastReading?.timestampEpochMs
            val ageMinutes = latestTimestamp?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L) }
            graphPeriodPill.text =
                buildString {
                    append(hours).append('h')
                    if (ageMinutes != null) append(" • ").append(ageMinutes).append('m')
                }
            val readings = G7ReadingDatabase(this).use { it.query(limit = 300) }
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
        palette: G7AppearancePalette,
    ): FrameLayout {
        val hours = directSettings.graphHours()
        return FrameLayout(this).apply {
            setOnClickListener {
                advanceGraphScale(directSettings.graphHours())
                updateGraphOnly()
            }
            graphView =
                G7CollectorGraphView(this@G7WatchActivity).apply {
                    bind(
                        readings = readings,
                        palette = palette,
                        graphHours = hours,
                    )
                }
            addView(graphView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150.dp))

            graphPeriodPill =
                label("${hours}h", 11f, palette.argb(G7AppearanceRole.MENU_TEXT_SECONDARY), true).apply {
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
        palette: G7AppearancePalette,
        action: () -> Unit,
    ) = TextView(this).apply {
        text = textValue
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = 40.dp
        setPadding(13.dp, 8.dp, 13.dp, 8.dp)
        app.aapswear.uishared.SystemTypography
            .apply(this, true)
        val (fill, textColor, stroke) =
            when (style) {
                PillStyle.PRIMARY ->
                    Triple(
                        palette.argb(G7AppearanceRole.MENU_PRIMARY),
                        palette.argb(G7AppearanceRole.MENU_BACKGROUND),
                        palette.argb(G7AppearanceRole.MENU_PRIMARY),
                    )
                PillStyle.SECONDARY ->
                    Triple(
                        palette.argb(G7AppearanceRole.MENU_SURFACE),
                        palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                        palette.argb(G7AppearanceRole.MENU_BORDER),
                    )
                PillStyle.DANGER ->
                    Triple(
                        withAlpha(palette.argb(G7AppearanceRole.GLUCOSE_ERROR), 36),
                        palette.argb(G7AppearanceRole.GLUCOSE_ERROR),
                        palette.argb(G7AppearanceRole.GLUCOSE_ERROR),
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
        app.aapswear.uishared.SystemTypography
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
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) refreshLiveContent()
    }

    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(systemBackCallback)
        companionAccessClosed = true
        companionBondAccess.cancel()
        mainHandler.removeCallbacks(pairingSuccessFinish)
        pairingExecutor.shutdown()
        unregisterReadingObserver()
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
            val opticalOffset = (G7_TREND_OPTICAL_OFFSET_DP * resources.displayMetrics.density).roundToInt()
            val containerHeight = height + 2 * opticalOffset
            addView(
                android.widget.FrameLayout(this@G7WatchActivity).apply {
                    clipChildren = false
                    clipToPadding = false

                    fun arrow(
                        tint: Int,
                        x: Float = 0f,
                        y: Float = 0f,
                    ) = ImageView(this@G7WatchActivity).apply {
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
        const val PAIRING_SUCCESS_DURATION_MS = 3_000L
    }
}

internal const val G7_TREND_OPTICAL_OFFSET_DP = 1f
