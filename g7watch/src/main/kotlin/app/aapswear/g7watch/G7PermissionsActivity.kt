package app.aapswear.g7watch

import android.app.Activity
import android.app.NotificationManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit

/** User-initiated access setup, shared by Pixel Watch and Galaxy Watch. */
class G7PermissionsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var scroll: G7EdgeFadeScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
        scroll =
            G7EdgeFadeScrollView(this).apply {
                isFillViewport = true
                addView(root)
            }
        scroll.applyG7EdgeFade()
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        G7CgmAlarmNotifier.prepareCurrentChannels(this)
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        val y = scroll.scrollY
        val palette = G7AppearanceStore(this).load()
        val background = palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)
        root.removeAllViews()
        root.setPadding(18.dp, 32.dp, 18.dp, 56.dp)
        root.setBackgroundColor(background)
        root.addView(g7SettingsHeader("Berechtigungen", palette))
        val state = G7SystemAccess.snapshot(this)

        fun card(title: String, status: String, detail: String, action: String, click: () -> Unit) {
            val content =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(13.dp, 12.dp, 13.dp, 12.dp)
                    this.background =
                        GradientDrawable().apply {
                            setColor(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE))
                            cornerRadius = 26.dp.toFloat()
                        }
                }

            fun label(value: String, bold: Boolean) =
                TextView(this).apply {
                    text = value
                    textSize = if (bold) 14f else 12f
                    setTextColor(palette.settingsArgb(if (bold) G7AppearanceRole.MENU_TEXT_PRIMARY else G7AppearanceRole.MENU_TEXT_SECONDARY))
                    app.aapswear.uishared.SystemTypography
                        .apply(this, bold)
                }
            content.addView(label(title, true))
            content.addView(label(status, true))
            content.addView(label(detail, false))
            content.addView(
                Button(this).apply {
                    text = action
                    isAllCaps = false
                    textSize = 13f
                    minimumHeight = 48.dp
                    setTextColor(palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                    app.aapswear.uishared.WearSettingsUi
                        .actionButton(this, palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                    setOnClickListener { click() }
                },
            )
            root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 7.dp })
        }

        fun status(granted: Boolean) = if (granted) "Erlaubt" else "Nicht erlaubt"

        fun open(intents: List<android.content.Intent>) {
            if (!G7SystemAccess.open(this, intents)) unavailable()
        }

        fun requestOrOpen(permissions: Set<String>, fallback: List<android.content.Intent>) {
            val missing = G7SystemAccess.missingRuntimePermissions(this).filter { it in permissions }.toTypedArray()
            val preferences = getSharedPreferences("g7_permission_requests", MODE_PRIVATE)
            val permanentlyDenied = missing.any { preferences.getBoolean(it, false) && !shouldShowRequestPermissionRationale(it) }
            if (missing.isNotEmpty() && !permanentlyDenied) {
                preferences.edit { missing.forEach { putBoolean(it, true) } }
                requestPermissions(missing, 701)
            } else {
                open(fallback)
            }
        }
        card("Einrichtung", "Schritt für Schritt", "Alle benötigten Freigaben erneut prüfen. Bestehende Sensordaten bleiben erhalten.", "Einrichtung öffnen") {
            startActivity(android.content.Intent(this, G7SetupActivity::class.java))
        }
        card("Bluetooth", status(state.bluetoothGranted), "Geräte in der Nähe: Sensorempfang und Verbindung. Kein Standortzugriff erforderlich.", "Berechtigungen öffnen") {
            requestOrOpen(setOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT), G7SystemAccess.appSettingsIntents(packageName))
        }
        card("Benachrichtigungen", status(state.notificationsGranted), "Collectorstatus und Glukosealarme. Nach dauerhafter Ablehnung in den App-Einstellungen erlauben.", "Benachrichtigungen öffnen") {
            requestOrOpen(setOf(android.Manifest.permission.POST_NOTIFICATIONS), G7SystemAccess.notificationSettingsIntents(packageName))
        }
        card("Hintergrundbetrieb", status(state.batteryUnrestricted), "Die Akkuausnahme schützt den direkten Sensorempfang im Ruhemodus. Hersteller können diese Auswahl einschränken.", "Akkueinstellungen öffnen") {
            if (!G7BackgroundAccess.openBatterySettings(this)) unavailable()
        }
        card("Genaue Alarme", status(state.exactAlarmsGranted), "Für zeitgenaue Wiederverbindung, Sensorfenster und Alarmwiederholung.", "Alarmzugriff öffnen") {
            open(G7SystemAccess.exactAlarmSettingsIntents(packageName))
        }
        card("Nicht stören", g7AlarmAudioPolicyLabel(G7AlarmSystemAccess.snapshot(this)), "Systemzugriff: ${status(state.dndAccessGranted)}. Eigene Töne benötigen die Systemausnahme Alarme im Ruhemodus. Die Kanal-Ausnahme allein gibt den Ton nicht frei.", "Systemfreigabe öffnen") {
            if (G7AlarmSystemAccess.openPolicySettings(this) != G7SettingsOpenResult.OPENED) unavailable()
        }
        val ready = state.alarmChannels.count { it.importance != null && it.importance > NotificationManager.IMPORTANCE_NONE && it.bypassDnd }
        card("Alarmkanäle", "$ready von ${state.alarmChannels.size} mit Nicht-Stören-Ausnahme", "Alle Alarmkanäle gemeinsam verwalten. SugarWear spielt die eigenen Alarmtöne direkt ab.", "Alarmkanäle öffnen") {
            open(G7SystemAccess.notificationSettingsIntents(packageName))
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun unavailable() = Toast.makeText(this, "Diese Systemeinstellung ist auf dieser Watch nicht verfügbar.", Toast.LENGTH_LONG).show()

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
