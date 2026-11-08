package app.sugarwear

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@dagger.hilt.android.AndroidEntryPoint
class CgmSystemStatusActivity : CgmSettingsComposeActivity() {
    private val status by viewModels<CgmSystemStatusViewModel>()
    private val pairing by viewModels<SensorConnectionViewModel>()
    private val unlinkInFlight get() = pairing.state.value.unlinkInFlight
    private val unlinkFailed get() = pairing.state.value.unlinkFailed
    private val unlinkFailureReason get() = pairing.state.value.unlinkFailureReason
    private var companionAccessClosed = false
    private var claimedAccessToken: Long? = null
    private val companionConsent: CgmCompanionConsentLauncher = CgmCompanionConsentLauncher(this) { request, result -> companionBondAccess.onActivityResult(request, result) }
    private val companionBondAccess: CgmCompanionBondAccess by lazy { CgmCompanionBondAccess(this, launchConsent = companionConsent::launch) }
    private var showPairingCode: Boolean
        get() = status.state.value.showPairingCode
        set(value) = status.showCode(value)
    private var selectedSection: String?
        get() = status.state.value.section
        set(value) = status.section(value)
    private var hardwareExpanded: Boolean
        get() = status.state.value.hardwareExpanded
        set(value) = status.expandHardware(value)
    private var diagnosticsExpanded: Boolean
        get() = status.state.value.diagnosticsExpanded
        set(value) = status.expandDiagnostics(value)
    private var scrollView: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        var wasUnlinking = pairing.state.value.unlinkInFlight
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { status.state.collect { render() } }
                launch {
                    pairing.state.collect { state ->
                        if (wasUnlinking && !state.unlinkInFlight) status.refresh()
                        wasUnlinking = state.unlinkInFlight
                        render()
                        val effect = state.effect as? CgmPairingEffect.Access ?: return@collect
                        if (effect.operation != CgmPairingOperation.UNLINK || claimedAccessToken == effect.token) return@collect
                        claimedAccessToken = effect.token
                        companionBondAccess.ensureForUnlink(
                            onReady = {
                                if (!companionAccessClosed) {
                                    pairing.accessReady(effect.token)
                                    status.refresh()
                                }
                            },
                            onFailure = { reason -> if (!companionAccessClosed) pairing.accessFailed(effect.token, reason) },
                        )
                    }
                }
            }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        status.refresh()
        render()
    }

    override fun onPause() {
        showPairingCode = false
        super.onPause()
    }

    private fun render() {
        val oldScrollY = scrollView?.scrollY ?: 0
        val loaded = status.state.value
        if (!settingsReady(CgmSettingsIoState(loaded.snapshot, loaded.loading, loaded.error), status::refresh)) return
        val snapshot = checkNotNull(loaded.snapshot)
        val palette = snapshot.palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)

        val state = snapshot.sensor
        val credentials = snapshot.credentials
        val userStatus = deriveCgmUserStatus(state, credentials != null)
        val attempt = snapshot.attempt
        val cycle = snapshot.cycle
        val hardwareMetrics = snapshot.metrics

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(background)
                addView(
                    cgmSettingsHeader(selectedSection?.let(::sectionTitle) ?: "Systemstatus", palette) { navigateBack() },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )

                if (selectedSection == null) {
                    addView(
                        group(userStatus.title, palette).apply {
                            addView(
                                label(
                                    state.lastReading?.let {
                                        "Letzter Wert ${it.glucoseMgDl.toInt()} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.timestampEpochMs))}"
                                    } ?: "Noch kein Sensorwert",
                                    9f,
                                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                                ).apply { gravity = Gravity.START },
                            )
                            addActionRows(this, state, palette)
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == "LIVE COLLECTOR STATUS") {
                    addView(
                        group("LIVE COLLECTOR STATUS", palette).apply {
                            val lastEvent = attempt?.events?.maxByOrNull { it.timestampEpochMs }
                            val livePhase = lastEvent?.stage?.name ?: userStatus.phase
                            addView(row("Collector", if (state.collectorEnabled) "Aktiv" else "Pausiert", palette))
                            addView(row("Aktueller Zustand", livePhase, palette))
                            addView(
                                row(
                                    "Letzter gültiger Wert",
                                    state.lastReading?.let { "${it.glucoseMgDl.toInt()} · ${formatTimestamp(it.timestampEpochMs)}" } ?: "—",
                                    palette,
                                ),
                            )
                            addView(
                                row(
                                    "Alter",
                                    state.lastReading?.let { formatDurationMs(System.currentTimeMillis() - it.timestampEpochMs) } ?: "—",
                                    palette,
                                ),
                            )
                            addView(row("Nächster Sensorzyklus", formatTimestamp(cycle?.expectedReadingEpoch), palette))
                            addView(
                                row("Nächster Wakeup", formatTimestamp(state.nextReconnectEpochMs ?: cycle?.requestedReconnectEpoch), palette),
                            )
                            addView(row("Aktueller Attempt", state.activeAttemptId?.toString() ?: "—", palette))
                            addView(row("Verbindungsweg", liveCollectorPath(cycle, livePhase), palette))
                            addView(row("Letzter Fehler", state.lastError?.let { "${it.code} · ${it.safeMessage}" } ?: "—", palette))
                            addView(row("Letzte Verbindung", formatTimestamp(state.lastSuccessfulConnectionEpochMs), palette))
                            addView(row("FGS", if (snapshot.serviceRunning) "Aktiv" else "Nicht aktiv", palette))
                        },
                        cardParams(),
                    )
                }

                if (selectedSection == "Sensor") {
                    addView(
                        group("Sensor", palette).apply {
                            addView(row("Sensorstatus", state.sensor?.state?.name ?: "—", palette))
                            addView(row("Session", state.sensor?.sessionId ?: state.lastReading?.sessionId ?: "—", palette))
                            addView(
                                row(
                                    "Sensorcode",
                                    if (showPairingCode) {
                                        credentials?.pairingCode ?: "\u2014"
                                    } else if (credentials != null) {
                                        "\u2022\u2022\u2022\u2022"
                                    } else {
                                        "\u2014"
                                    },
                                    palette,
                                ),
                            )
                            if (credentials != null) {
                                addView(
                                    pill(if (showPairingCode) "Sensorcode verbergen" else "Sensorcode anzeigen", palette) {
                                        showPairingCode = !showPairingCode
                                        render()
                                    },
                                )
                            }
                            addView(row("GTIN", credentials?.gtin ?: "—", palette))
                            addView(row("Seriennummer", credentials?.sensorSerial ?: "—", palette))
                            addView(
                                row(
                                    "Letzter Wert",
                                    state.lastReading?.let { "${it.glucoseMgDl.toInt()} · ${formatTimestamp(it.timestampEpochMs)}" } ?: "—",
                                    palette,
                                ),
                            )
                            addView(row("Sensoralter", state.lastReading?.sensorAgeSeconds?.let(::formatDurationSeconds) ?: "—", palette))
                            addView(
                                row(
                                    "Trendrate",
                                    state.lastReading?.trendRateMgDlPerMinute?.let { "%.1f mg/dL/min".format(it) } ?: "—",
                                    palette,
                                ),
                            )
                            addView(row("Sensor-ID", state.sensor?.sensorId ?: state.lastReading?.sensorId ?: "—", palette))
                            addView(row("Sequenz", state.lastReading?.sequenceNumber?.toString() ?: "—", palette))
                            addView(row("BLE-Name", state.sensor?.deviceName ?: "—", palette))
                            addView(
                                row(
                                    "Sensorstart · berechnet",
                                    formatTimestamp(state.sensor?.sensorStartEpochMs ?: state.lastReading?.sensorStartEpochMs),
                                    palette,
                                ),
                            )
                            addView(
                                row(
                                    "Sensorende · berechnet",
                                    formatTimestamp(state.sensor?.sensorEndEpochMs ?: state.lastReading?.sensorEndEpochMs),
                                    palette,
                                ),
                            )
                            addView(
                                row(
                                    "Kulanzende · berechnet",
                                    formatTimestamp(state.sensor?.graceEndEpochMs ?: state.lastReading?.graceEndEpochMs),
                                    palette,
                                ),
                            )
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == "Verbindung") {
                    addView(
                        group("Verbindung", palette).apply {
                            addView(row("Zustand", userStatus.title, palette))
                            addView(row("Status", userStatus.status, palette))
                            addView(row("Verbindung", state.connectionState.name, palette))
                            addView(row("Collector", if (state.collectorEnabled) "Aktiviert" else "Pausiert", palette))
                            addView(row("Phase", userStatus.phase, palette))
                            addView(row("Protokoll", state.protocolState.name, palette))
                            addView(row("Letzter Connect", formatTimestamp(state.lastSuccessfulConnectionEpochMs), palette))
                            addView(row("Bekannte Adresse", state.sensor?.deviceAddress ?: "Nicht vorhanden", palette))
                            addView(row("Reconnect-Strategie", snapshot.reconnectStrategy, palette))
                            addView(row("Letzter Fehler", state.lastError?.let { "${it.code} · ${it.safeMessage}" } ?: "—", palette))
                            addView(row("Hinweis", userStatus.description, palette))
                            addView(row("Empfohlene Aktion", userStatus.action, palette))
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == "Zeitplanung und Rechte") {
                    addView(
                        group("Zeitplanung und Rechte", palette).apply {
                            addView(row("Nächster Wert", formatTimestamp(cycle?.expectedReadingEpoch), palette))
                            addView(
                                row(
                                    "Nächster Reconnect",
                                    formatTimestamp(state.nextReconnectEpochMs ?: cycle?.requestedReconnectEpoch),
                                    palette,
                                ),
                            )
                            addView(row("Alarm", cycle?.alarmKind?.name ?: "—", palette))
                            addView(row("Exact Alarm", if (canScheduleExactReconnects()) "Erlaubt" else "Nicht erlaubt", palette))
                            addView(
                                row(
                                    "Akkuoptimierung",
                                    if (snapshot.access.batteryUnrestricted
                                    ) {
                                        "Uneingeschränkt"
                                    } else {
                                        "Optimiert"
                                    },
                                    palette,
                                ),
                            )
                            addView(row("Geräte in der Nähe", if (hasNearbyPermission()) "Erlaubt" else "Nicht erlaubt", palette))
                            addView(row("Benachrichtigungen", if (hasNotificationPermission()) "Erlaubt" else "Nicht erlaubt", palette))
                            addView(row("Retry", state.retryCount.toString(), palette))
                            addPermissionRows(this, palette)
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == "Hardwaretest") {
                    addView(
                        group("Hardwaretest", palette).apply {
                            addView(
                                expandableHeader("Details", hardwareExpanded, palette) {
                                    hardwareExpanded = !hardwareExpanded
                                    render()
                                },
                            )
                            if (hardwareExpanded) {
                                addCycleRows(this, cycle, palette)
                                addView(row("Erwartete Fenster", hardwareMetrics.expectedWindows.toString(), palette))
                                addView(row("Versuchte Fenster", hardwareMetrics.attemptedWindows.toString(), palette))
                                addView(row("Erfolgreiche Fenster", hardwareMetrics.successfulWindows.toString(), palette))
                                addView(row("Verpasste Fenster", hardwareMetrics.missedWindows.toString(), palette))
                                addView(row("Uhr aus/Neustart", hardwareMetrics.deviceUnavailableWindows.toString(), palette))
                                addView(row("First Attempt", hardwareMetrics.firstAttemptSuccess.toString(), palette))
                                addView(row("Retry-Erfolg", hardwareMetrics.retrySuccess.toString(), palette))
                                addView(row("GATT 133", hardwareMetrics.gatt133Count.toString(), palette))
                                addView(row("No Callback", hardwareMetrics.noCallbackCount.toString(), palette))
                                addView(row("Fallback Scans", hardwareMetrics.fallbackScanCount.toString(), palette))
                                addView(row("Verfügbarkeit", "%.1f %%".format(hardwareMetrics.availabilityPercent), palette))
                                addView(row("Längste Wertelücke", hardwareMetrics.longestReadingGapMs?.let(::formatDurationMs) ?: "—", palette))
                                addView(row("Median Empfang", hardwareMetrics.medianReceiveDelayMs?.let(::formatDurationMs) ?: "—", palette))
                                addView(row("p95 Empfang", hardwareMetrics.p95ReceiveDelayMs?.let(::formatDurationMs) ?: "—", palette))
                            }
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == "Diagnose") {
                    addView(
                        group("Diagnose", palette).apply {
                            addView(
                                expandableHeader("Details", diagnosticsExpanded, palette) {
                                    diagnosticsExpanded = !diagnosticsExpanded
                                    render()
                                },
                            )
                            if (diagnosticsExpanded) {
                                val lastEvent = attempt?.events?.maxByOrNull { it.timestampEpochMs }
                                addView(row("Aktiver Attempt", state.activeAttemptId?.toString() ?: "—", palette))
                                addView(
                                    row(
                                        "Attempt-Alter",
                                        attempt?.takeIf { it.completedAtEpochMs == null }?.let {
                                            formatDurationMs(
                                                System.currentTimeMillis() - it.startedAtEpochMs,
                                            )
                                        }
                                            ?: "—",
                                        palette,
                                    ),
                                )
                                addView(row("Letztes Ergebnis", attempt?.result?.name ?: "—", palette))
                                addView(row("Klassifikation", attempt?.classification?.name ?: "—", palette))
                                addView(row("Letzte Stufe", lastEvent?.stage?.name ?: "—", palette))
                                addView(row("Fehlercode", lastEvent?.errorCode ?: state.lastError?.code ?: "—", palette))
                                addView(row("Letzte Meldung", lastEvent?.message ?: userStatus.description, palette))
                                addView(row("Slot-Strategie", cycle?.slotStrategy?.name ?: "—", palette))
                                addView(row("Radio-Fehlerfolge", cycle?.radioFailureStreak?.toString() ?: "0", palette))
                                addView(row("Radio-Cluster", if (cycle?.radioDegradedCluster == true) "Aktiv" else "Nein", palette))
                            }
                        },
                        cardParams(),
                    )
                }
                if (selectedSection == null) {
                    val destinations = listOf("Sensor" to "Sensordetails", "Verbindung" to "Verbindung", "Zeitplanung und Rechte" to "Zeitplanung", "LIVE COLLECTOR STATUS" to "Verbindungsversuch", "Hardwaretest" to "Empfangsstatistik", "Diagnose" to "Diagnose")
                    val rows =
                        destinations.map { (key, title) ->
                            app.sugarlicious.uishared.WearSettingsUi
                                .navigationRow(this@CgmSystemStatusActivity, title, null, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)) {
                                    selectedSection = key
                                    hardwareExpanded = true
                                    diagnosticsExpanded = true
                                    showPairingCode = false
                                    scrollView?.scrollTo(0, 0)
                                    render()
                                }.apply { tag = "status-details-$key" }
                        }
                    addView(
                        app.sugarlicious.uishared.WearSettingsUi
                            .navigationGroup(this@CgmSystemStatusActivity, rows, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                        cardParams(),
                    )
                }
                addView(
                    label(
                        "Nur einen direkten Sensor-Collector gleichzeitig verwenden. Juggluco oder xDrip vorher beenden.",
                        9f,
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                    ).apply { setPadding(8.dp, 12.dp, 8.dp, 0) },
                )
                addView(
                    label(
                        "Der Collector verbindet sich nur im erwarteten Sensorfenster und beendet die Verbindung danach wieder.",
                        9f,
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                    ).apply { setPadding(8.dp, 6.dp, 8.dp, 0) },
                )
            }

        val currentScroll = scrollView
        if (currentScroll == null) {
            scrollView = CgmEdgeFadeScrollView(this).apply { isFillViewport = true }.applyCgmEdgeFade()
            scrollView?.let(::showSettingsView)
        } else {
            currentScroll.removeAllViews()
        }
        scrollView?.apply {
            setBackgroundColor(background)
            addView(content)
            viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        viewTreeObserver.removeOnPreDrawListener(this)
                        val maxScroll = (content.measuredHeight - height).coerceAtLeast(0)
                        scrollTo(0, oldScrollY.coerceAtMost(maxScroll))
                        return true
                    }
                },
            )
        }
    }

    private fun sectionTitle(key: String): String =
        when (key) {
            "LIVE COLLECTOR STATUS" -> "Verbindungsversuch"
            "Hardwaretest" -> "Empfangsstatistik"
            "Zeitplanung und Rechte" -> "Zeitplanung"
            "Sensor" -> "Sensordetails"
            else -> key
        }

    private fun navigateBack() {
        if (selectedSection == null) {
            finish()
        } else {
            selectedSection = null
            showPairingCode = false
            scrollView?.scrollTo(0, 0)
            render()
        }
    }

    private fun liveCollectorPath(
        cycle: CollectorCycleTiming?,
        phase: String,
    ): String =
        when {
            cycle?.authStartedAt != null && cycle.authSucceededAt == null -> "Auth"
            cycle?.gattConnectedAt != null -> "GATT"
            cycle?.scanEndedAt == null && cycle?.directConnectResult?.name?.contains("FAILED") == true -> "Scan"
            cycle?.directConnectAttempts?.let { it > 1 } == true -> "Retry"
            cycle?.connectGattStartedAt != null -> "Direct Connect"
            phase.isNotBlank() -> phase
            else -> "Waiting"
        }

    private fun expandableHeader(
        title: String,
        expanded: Boolean,
        palette: CgmAppearancePalette,
        action: () -> Unit,
    ) = label("${if (expanded) "▾" else "▸"}  $title", 10.5f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = 44.dp
        setOnClickListener { action() }
        contentDescription = "$title ${if (expanded) "einklappen" else "ausklappen"}"
    }

    private fun addCycleRows(
        target: LinearLayout,
        cycle: CollectorCycleTiming?,
        palette: CgmAppearancePalette,
    ) {
        target.addView(row("Letzter Versuch", formatTimestamp(cycle?.receiverReceivedAt ?: cycle?.serviceOnStartCommandAt), palette))
        target.addView(row("Advertisement", formatTimestamp(cycle?.advertisementFoundAt), palette))
        target.addView(row("RSSI", cycle?.advertisementRssi?.let { "$it dBm" } ?: "—", palette))
        target.addView(row("GATT Start", formatTimestamp(cycle?.connectGattStartedAt), palette))
        target.addView(row("Fenster-ID", cycle?.expectedWindowId ?: "—", palette))
        target.addView(row("GATT-Generation", cycle?.gattGeneration?.toString() ?: "—", palette))
        target.addView(row("Direct-Ergebnis", cycle?.directConnectResult?.name ?: "—", palette))
        target.addView(row("Direct-Versuche", cycle?.directConnectAttempts?.toString() ?: "—", palette))
        target.addView(row("Direct-Status", cycle?.directConnectStatus?.toString() ?: "—", palette))
        target.addView(row("Scan beendet", formatTimestamp(cycle?.scanEndedAt), palette))
        target.addView(row("Scan-Ergebnisse", cycle?.scanTotalResults?.toString() ?: "—", palette))
        target.addView(
            row("G7 / bekannte Adresse", "${cycle?.scanNamedCgmResults ?: "—"} / ${cycle?.scanExactAddressResults ?: "—"}", palette),
        )
        target.addView(row("Scan-RSSI", cycle?.scanMinRssi?.let { "$it..${cycle.scanMaxRssi ?: it} dBm" } ?: "—", palette))
        target.addView(row("GATT verbunden", formatTimestamp(cycle?.gattConnectedAt), palette))
        target.addView(row("Auth Start", formatTimestamp(cycle?.authStartedAt), palette))
        target.addView(row("Auth Erfolg", formatTimestamp(cycle?.authSucceededAt), palette))
        target.addView(row("Glukose empfangen", formatTimestamp(cycle?.glucosePacketReceivedAt), palette))
        target.addView(row("Zyklusende", formatTimestamp(cycle?.cycleEndedAt), palette))
    }

    private fun addPermissionRows(target: LinearLayout, palette: CgmAppearancePalette) {
        target.addView(
            pill("Berechtigungen", palette) {
                startActivity(Intent(this, CgmPermissionsActivity::class.java))
            },
            buttonParams(),
        )
    }

    private fun addActionRows(
        target: LinearLayout,
        state: CgmPersistedState,
        palette: CgmAppearancePalette,
    ) {
        val associated = hasCgmSensorAssociation(state)
        target.addView(
            pill(if (associated) "Sensor trennen" else "Sensor verbinden", palette) {
                if (unlinkInFlight) return@pill
                if (hasCgmSensorAssociation(
                        status.state.value.snapshot
                            ?.sensor ?: state,
                    )
                ) {
                    status.recordUnlink { pairing.unlink() }
                } else {
                    startActivity(Intent(this, CgmWatchActivity::class.java).putExtra(CgmWatchActivity.EXTRA_OPEN_PAIRING, true))
                }
            }.apply { isEnabled = !unlinkInFlight },
            buttonParams(),
        )
        if (unlinkFailed) {
            target.addView(
                label(
                    "Entkopplung durch Android nicht bestätigt. Sensor bleibt pausiert und kann nicht als vollständig getrennt gelten. Auf Samsung-Uhren sind BLE-Geräte im Bluetooth-Menü oft nicht sichtbar; bitte die Entkopplung hier erneut versuchen. Die Glukosehistorie bleibt erhalten.",
                    11f,
                    palette.argb(CgmAppearanceRole.GLUCOSE_ERROR),
                ),
            )
            target.addView(
                pill("Entkopplung erneut versuchen", palette) {
                    status.recordUnlink { pairing.unlink() }
                },
                buttonParams(),
            )
        }
        unlinkFailureReason?.let { target.addView(label(it, 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY))) }
        if (associated || state.collectorEnabled) {
            target.addView(
                pill(if (state.collectorEnabled) "Pausieren" else "Fortsetzen", palette) {
                    if (unlinkInFlight) return@pill
                    val enabled =
                        status.state.value.snapshot
                            ?.sensor
                            ?.collectorEnabled == true
                    if (enabled) pauseCollectorWithExitOffer() else status.resumeCollector()
                }.apply { isEnabled = !unlinkInFlight },
                buttonParams(),
            )
        }
    }

    internal fun pauseCollectorWithExitOffer() {
        status.pause()
        android.app.AlertDialog
            .Builder(this)
            .setTitle("SugarWear beenden?")
            .setMessage("Der Datenempfang ist pausiert. SugarWear jetzt schließen?")
            .setPositiveButton("Beenden") { _, _ -> finishAffinity() }
            .setNegativeButton("Geöffnet lassen", null)
            .show()
    }

    private fun hasNearbyPermission(): Boolean =
        status.state.value.snapshot
            ?.access
            ?.bluetoothGranted == true

    private fun hasNotificationPermission(): Boolean =
        status.state.value.snapshot
            ?.access
            ?.notificationsGranted == true

    private fun group(
        title: String,
        palette: CgmAppearancePalette,
    ) = app.sugarlicious.uishared.WearSettingsUi
        .elementCard(
            this,
            title,
            palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
        ).apply { tag = "status-group-$title" }

    private fun row(
        title: String,
        value: String,
        palette: CgmAppearancePalette,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8.dp, 5.dp, 8.dp, 5.dp)
        addView(
            label(title, 9.5f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)).apply {
                gravity = Gravity.START
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            label(value, 9.5f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true).apply {
                gravity = Gravity.START
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun pill(
        text: String,
        palette: CgmAppearancePalette,
        danger: Boolean = false,
        action: () -> Unit,
    ) = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        minHeight = 48.dp
        setPadding(13.dp, 8.dp, 13.dp, 8.dp)
        app.sugarlicious.uishared.SystemTypography
            .apply(this, true)
        val color = if (danger) palette.argb(CgmAppearanceRole.GLUCOSE_ERROR) else palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
        setTextColor(color)
        background =
            rounded(
                if (danger) withAlpha(color, 36) else palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                if (danger) color else palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
                999f,
            )
        setOnClickListener { action() }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        textSize =
            app.sugarlicious.uishared.WearSettingsUi
                .textSize(size)
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
        setStroke(0, stroke)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun canScheduleExactReconnects(): Boolean =
        status.state.value.snapshot
            ?.access
            ?.exactAlarmsGranted == true

    override fun onDestroy() {
        companionAccessClosed = true
        companionBondAccess.cancel()
        pairing.platformDetached()
        super.onDestroy()
    }

    private fun formatTimestamp(value: Long?): String = value?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "—"

    private fun formatDurationSeconds(value: Long): String = formatDurationMs(value * 1_000L)

    private fun formatDurationMs(value: Long): String {
        val seconds = value.coerceAtLeast(0L) / 1_000L
        return if (seconds < 120L) "$seconds s" else "${seconds / 60L} min"
    }

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun cardParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin =
                7.dp
        }

    private fun buttonParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin =
                7.dp
        }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SECTION = "g7.settings.section"
    }
}
