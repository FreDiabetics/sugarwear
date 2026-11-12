package app.sugarwear

import android.app.NotificationManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.lifecycle.withCreationCallback

/** User-initiated access setup, shared by Pixel Watch and Galaxy Watch. */
@dagger.hilt.android.AndroidEntryPoint
class CgmPermissionsActivity : CgmSettingsComposeActivity() {
    private val access by viewModels<CgmSettingsAccessViewModel>()
    private val requests by viewModels<CgmPermissionRequestViewModel>(extrasProducer = {
        defaultViewModelCreationExtras.withCreationCallback<CgmPermissionRequestViewModel.Factory> { it.create(access::recordRequests) }
    })
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requests.systemReturned()
            access.refresh()
            render()
        }
    private lateinit var root: LinearLayout
    private lateinit var scroll: CgmEdgeFadeScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(access.state, render = ::render)

        root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
        scroll =
            CgmEdgeFadeScrollView(this).apply {
                isFillViewport = true
                addView(root)
            }
        scroll.applyCgmEdgeFade()
        showSettingsView(scroll)
    }

    override fun onResume() {
        super.onResume()
        access.refresh(prepare = true)
        render()
    }

    private fun render() {
        if (!settingsReady(access.state.value, { access.refresh() })) return
        showSettingsView(scroll)
        val y = scroll.scrollY
        val palette = checkNotNull(access.state.value.snapshot).palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        root.removeAllViews()
        app.sugarlicious.uishared.WearSettingsUi
            .pagePadding(root)
        root.setBackgroundColor(background)
        root.addView(cgmSettingsHeader("Berechtigungen", palette))
        val state = checkNotNull(access.state.value.snapshot).access

        fun card(title: String, status: String, detail: String, action: String?, click: () -> Unit) {
            val content =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(13.dp, 12.dp, 13.dp, 12.dp)
                    this.background =
                        GradientDrawable().apply {
                            setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
                            cornerRadius = 26.dp.toFloat()
                        }
                }

            fun label(value: String, bold: Boolean) =
                TextView(this).apply {
                    text = value
                    textSize = if (bold) 14f else 12f
                    setTextColor(palette.settingsArgb(if (bold) CgmAppearanceRole.MENU_TEXT_PRIMARY else CgmAppearanceRole.MENU_TEXT_SECONDARY))
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, bold)
                }
            content.addView(label(title, true))
            content.addView(label(status, true))
            content.addView(label(detail, false))
            if (action != null) {
                content.addView(
                    Button(this).apply {
                        text = action
                        isAllCaps = false
                        textSize = 13f
                        minimumHeight = 48.dp
                        setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                        app.sugarlicious.uishared.WearSettingsUi
                            .actionButton(this, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                        setOnClickListener { click() }
                    },
                )
            }
            root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7.dp })
        }

        fun status(granted: Boolean) = if (granted) "Erlaubt" else "Nicht erlaubt"

        fun open(intents: List<android.content.Intent>) {
            if (!CgmSystemAccess.open(this, intents)) unavailable()
        }

        fun requestOrOpen(permissions: Set<String>, fallback: List<android.content.Intent>) {
            val missing = CgmSystemAccess.missingRuntimePermissions(this).filter { it in permissions }.toTypedArray()
            val rationale = missing.filter { shouldShowRequestPermissionRationale(it) }.toSet()
            if (requests.shouldRequest(missing.toList(), access.requestedPermissions(), rationale)) {
                permissionLauncher.launch(missing)
            } else {
                open(fallback)
            }
        }
        card("Einrichtung", "Schritt für Schritt", "Alle benötigten Freigaben erneut prüfen. Bestehende Sensordaten bleiben erhalten.", "Einrichtung öffnen") {
            startActivity(android.content.Intent(this, CgmSetupActivity::class.java))
        }
        card("Bluetooth", status(state.bluetoothGranted), "Geräte in der Nähe: Sensorempfang und Verbindung. Kein Standortzugriff erforderlich.", "Berechtigungen öffnen") {
            requestOrOpen(setOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT), CgmSystemAccess.appSettingsIntents(packageName))
        }
        card("Benachrichtigungen", status(state.notificationsGranted), "Collectorstatus und Glukosealarme. Nach dauerhafter Ablehnung in den App-Einstellungen erlauben.", "Benachrichtigungen öffnen") {
            requestOrOpen(setOf(android.Manifest.permission.POST_NOTIFICATIONS), CgmSystemAccess.notificationSettingsIntents(packageName))
        }
        card("Hintergrundbetrieb", status(state.batteryUnrestricted), "Die Akkuausnahme schützt den direkten Sensorempfang im Ruhemodus. Hersteller können diese Auswahl einschränken.", "Akkueinstellungen öffnen") {
            if (!CgmBackgroundAccess.openBatterySettings(this)) unavailable()
        }
        card("Genaue Alarme", status(state.exactAlarmsGranted), "Für zeitgenaue Wiederverbindung, Sensorfenster und Alarmwiederholung.", "Alarmzugriff öffnen") {
            open(CgmSystemAccess.exactAlarmSettingsIntents(packageName))
        }
        val alarm = checkNotNull(access.state.value.snapshot).alarmAccess
        card("Nicht stören", cgmAlarmAccessLabel(alarm), "${cgmAlarmAudioPolicyLabel(alarm)}. ${cgmAlarmAccessGuidance(alarm)}", cgmAlarmSettingsAction(alarm)?.label) {
            if (CgmAlarmSystemAccess.openRecommendedSettings(this, alarm) != CgmSettingsOpenResult.OPENED) unavailable()
        }
        val ready = state.alarmChannels.count { it.importance != null && it.importance > NotificationManager.IMPORTANCE_NONE && it.bypassDnd }
        card("Alarmkanäle", "$ready von ${state.alarmChannels.size} mit Nicht-Stören-Ausnahme", "Alle Alarmkanäle gemeinsam verwalten. SugarWear spielt die eigenen Alarmtöne direkt ab.", "Alarmkanäle öffnen") {
            open(CgmSystemAccess.notificationSettingsIntents(packageName))
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun unavailable() = Toast.makeText(this, "Diese Systemeinstellung ist auf dieser Watch nicht verfügbar.", Toast.LENGTH_LONG).show()

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
