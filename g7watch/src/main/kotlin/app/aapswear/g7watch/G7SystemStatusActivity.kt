package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.G7PersistedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class G7SystemStatusActivity : Activity() {
    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var unlinkInFlight = false
    private var unlinkFailed = false
    private var unlinkFailureReason: String? = null
    private var companionAccessClosed = false
    private val companionBondAccess by lazy { G7CompanionBondAccess(this) }
    private var showPairingCode = false
    private var hardwareExpanded = false
    private var diagnosticsExpanded = false
    private var scrollView: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        G7RuntimeReconciler.reconcile(this, G7RuntimeEntryPoint.SYSTEM_STATUS)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        showPairingCode = false
        super.onPause()
    }

    private fun render() {
        val oldScrollY = scrollView?.scrollY ?: 0
        val palette = G7AppearanceStore(this).load()
        val background = palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)

        val state = G7SensorStateStore(this).read()
        val credentials = G7CredentialStore(this).read()
        val userStatus = deriveG7UserStatus(state, credentials != null)
        val diagnostics = G7CollectorDiagnosticStore(this)
        val attempt = diagnostics.snapshot().firstOrNull()
        val cycle = attempt?.cycle ?: diagnostics.pendingScheduledCycle()
        val hardwareMetrics = G7ExpectedWindowLedger(this).metrics()

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(18.dp, 32.dp, 18.dp, 56.dp)
                setBackgroundColor(background)
                addView(
                    g7SettingsHeader("Systemstatus", palette),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )

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
                        addView(row("FGS", if (G7CollectorService.isServiceRunning()) "Aktiv" else "Nicht aktiv", palette))
                    },
                    cardParams(),
                )

                addView(
                    group("Sensor", palette).apply {
                        addView(row("Sensorstatus", state.sensor?.state?.name ?: "—", palette))
                        addView(row("Session", state.sensor?.sessionId ?: state.lastReading?.sessionId ?: "—", palette))
                        addView(row("Sensorcode", pairingCodeDisplayValue(credentials?.pairingCode, showPairingCode), palette))
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
                                "Sensorstart",
                                formatTimestamp(state.sensor?.sensorStartEpochMs ?: state.lastReading?.sensorStartEpochMs),
                                palette,
                            ),
                        )
                        addView(
                            row(
                                "Sensorende",
                                formatTimestamp(state.sensor?.sensorEndEpochMs ?: state.lastReading?.sensorEndEpochMs),
                                palette,
                            ),
                        )
                        addView(
                            row(
                                "Kulanzende",
                                formatTimestamp(state.sensor?.graceEndEpochMs ?: state.lastReading?.graceEndEpochMs),
                                palette,
                            ),
                        )
                    },
                    cardParams(),
                )
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
                        addView(row("Reconnect-Strategie", G7ReconnectStrategyStore.read(this@G7SystemStatusActivity).name, palette))
                        addView(row("Letzter Fehler", state.lastError?.let { "${it.code} · ${it.safeMessage}" } ?: "—", palette))
                        addView(row("Hinweis", userStatus.description, palette))
                        addView(row("Empfohlene Aktion", userStatus.action, palette))
                    },
                    cardParams(),
                )
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
                                if (G7BackgroundAccess.isBatteryUnrestricted(
                                        this@G7SystemStatusActivity,
                                    )
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
                addView(
                    group("Aktionen", palette).apply {
                        addActionRows(this, state, palette)
                    },
                    cardParams(),
                )

                addView(
                    label(
                        "Nur einen direkten Sensor-Collector gleichzeitig verwenden. Juggluco oder xDrip vorher beenden.",
                        9f,
                        palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                    ).apply { setPadding(8.dp, 12.dp, 8.dp, 0) },
                )
                addView(
                    label(
                        "Der Collector verbindet sich nur im erwarteten Sensorfenster und beendet die Verbindung danach wieder.",
                        9f,
                        palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                    ).apply { setPadding(8.dp, 6.dp, 8.dp, 0) },
                )
            }

        val currentScroll = scrollView
        if (currentScroll == null) {
            scrollView = G7EdgeFadeScrollView(this).apply { isFillViewport = true }.applyG7EdgeFade()
            setContentView(scrollView)
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
        palette: G7AppearancePalette,
        action: () -> Unit,
    ) = label("${if (expanded) "▾" else "▸"}  $title", 10.5f, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), true).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = 44.dp
        setOnClickListener { action() }
        contentDescription = "$title ${if (expanded) "einklappen" else "ausklappen"}"
    }

    private fun addCycleRows(
        target: LinearLayout,
        cycle: CollectorCycleTiming?,
        palette: G7AppearancePalette,
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
            row("G7 / bekannte Adresse", "${cycle?.scanNamedG7Results ?: "—"} / ${cycle?.scanExactAddressResults ?: "—"}", palette),
        )
        target.addView(row("Scan-RSSI", cycle?.scanMinRssi?.let { "$it..${cycle.scanMaxRssi ?: it} dBm" } ?: "—", palette))
        target.addView(row("GATT verbunden", formatTimestamp(cycle?.gattConnectedAt), palette))
        target.addView(row("Auth Start", formatTimestamp(cycle?.authStartedAt), palette))
        target.addView(row("Auth Erfolg", formatTimestamp(cycle?.authSucceededAt), palette))
        target.addView(row("Glukose empfangen", formatTimestamp(cycle?.glucosePacketReceivedAt), palette))
        target.addView(row("Zyklusende", formatTimestamp(cycle?.cycleEndedAt), palette))
    }

    private fun addPermissionRows(target: LinearLayout, palette: G7AppearancePalette) {
        target.addView(
            pill("Berechtigungen", palette) {
                startActivity(Intent(this, G7PermissionsActivity::class.java))
            },
            buttonParams(),
        )
    }

    private fun addActionRows(
        target: LinearLayout,
        state: G7PersistedState,
        palette: G7AppearancePalette,
    ) {
        val associated = hasG7SensorAssociation(state)
        target.addView(
            pill(if (associated) "Sensor trennen" else "Sensor verbinden", palette) {
                if (unlinkInFlight) return@pill
                if (hasG7SensorAssociation(G7SensorStateStore(this).read())) {
                    G7CollectorUiTransitionStore.record(this, G7UiSensorAction.UNLINK)
                    unlinkInFlight = true
                    unlinkFailureReason = null
                    render()
                    diagnosticScope.launch {
                        val released = runCatching { releaseG7CollectorOwnership(applicationContext) }.isSuccess
                        runOnUiThread {
                            if (companionAccessClosed || isFinishing || isDestroyed) return@runOnUiThread
                            if (!released) {
                                unlinkInFlight = false
                                unlinkFailed = true
                                render()
                            } else {
                                companionBondAccess.ensureForUnlink(
                                    onReady = {
                                        diagnosticScope.launch {
                                            val detached = runCatching { unlinkG7Sensor(applicationContext).detached }.getOrDefault(false)
                                            runOnUiThread {
                                                unlinkInFlight = false
                                                unlinkFailed = !detached
                                                if (!companionAccessClosed && !isFinishing && !isDestroyed) render()
                                            }
                                        }
                                    },
                                    onFailure = { reason ->
                                        unlinkFailureReason = reason
                                        unlinkInFlight = false
                                        unlinkFailed = true
                                        if (!companionAccessClosed && !isFinishing && !isDestroyed) render()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    startActivity(Intent(this, G7WatchActivity::class.java).putExtra(G7WatchActivity.EXTRA_OPEN_PAIRING, true))
                }
            }.apply { isEnabled = !unlinkInFlight },
            buttonParams(),
        )
        if (unlinkFailed) {
            target.addView(label("Trennen fehlgeschlagen. Sensorzuordnung bleibt erhalten. Bluetooth-Verbindung bei Bedarf in den Systemeinstellungen entfernen.", 11f, palette.argb(G7AppearanceRole.GLUCOSE_ERROR)))
            unlinkFailureReason?.let { target.addView(label(it, 11f, palette.argb(G7AppearanceRole.GLUCOSE_ERROR))) }
        }
        if (associated || state.collectorEnabled) {
            target.addView(
                pill(if (state.collectorEnabled) "Pausieren" else "Fortsetzen", palette) {
                    if (unlinkInFlight) return@pill
                    val enabled = G7SensorStateStore(this).read().collectorEnabled
                    G7CollectorUiTransitionStore.record(this, if (enabled) G7UiSensorAction.PAUSE else G7UiSensorAction.RESUME)
                    if (enabled) pauseCollectorWithExitOffer() else G7CollectorService.start(this)
                    Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing && !isDestroyed) render() }, 350L)
                }.apply { isEnabled = !unlinkInFlight },
                buttonParams(),
            )
        }
    }

    internal fun pauseCollectorWithExitOffer() {
        G7CollectorService.stop(this)
        android.app.AlertDialog
            .Builder(this)
            .setTitle("SugarWear beenden?")
            .setMessage("Der Datenempfang ist pausiert. SugarWear jetzt schließen?")
            .setPositiveButton("Beenden") { _, _ -> finishAffinity() }
            .setNegativeButton("Geöffnet lassen", null)
            .show()
    }

    private fun hasNearbyPermission(): Boolean =
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun group(
        title: String,
        palette: G7AppearancePalette,
    ) = app.aapswear.uishared.WearSettingsUi
        .elementCard(
            this,
            title,
            palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
            palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
        ).apply { tag = "status-group-$title" }

    private fun row(
        title: String,
        value: String,
        palette: G7AppearancePalette,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8.dp, 5.dp, 8.dp, 5.dp)
        addView(
            label(title, 9.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)).apply {
                gravity = Gravity.START
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            label(value, 9.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true).apply {
                gravity = Gravity.START
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun pill(
        text: String,
        palette: G7AppearancePalette,
        danger: Boolean = false,
        action: () -> Unit,
    ) = TextView(this).apply {
        this.text = text
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = 40.dp
        setPadding(13.dp, 8.dp, 13.dp, 8.dp)
        app.aapswear.uishared.SystemTypography
            .apply(this, true)
        val color = if (danger) palette.argb(G7AppearanceRole.GLUCOSE_ERROR) else palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY)
        setTextColor(color)
        background =
            rounded(
                if (danger) withAlpha(color, 36) else palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                if (danger) color else palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
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
            app.aapswear.uishared.WearSettingsUi
                .textSize(size)
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
        setStroke(0, stroke)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun canScheduleExactReconnects(): Boolean = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!companionBondAccess.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        companionAccessClosed = true
        companionBondAccess.cancel()
        diagnosticScope.cancel()
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
