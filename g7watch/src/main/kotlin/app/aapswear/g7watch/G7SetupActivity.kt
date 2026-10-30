package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit

/** First-run guide. Only the OS can grant access; skipping never claims success. */
class G7SetupActivity : Activity() {
    private var step = 0
    private var awaitingSystem = false
    private val steps = listOf("Bluetooth", "Benachrichtigungen", "Hintergrundbetrieb", "Genaue Alarme", "Nicht stören", "Alarmkanäle", "Alarmlautstärke")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        awaitingSystem = savedInstanceState?.getBoolean("awaiting") ?: false
        G7CgmAlarmNotifier.prepareCurrentChannels(this)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        outState.putBoolean("awaiting", awaitingSystem)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        G7CgmAlarmNotifier.prepareCurrentChannels(this)
        if (awaitingSystem) awaitingSystem = false
        render()
    }

    private fun granted(): Boolean {
        val state = G7SystemAccess.snapshot(this)
        return when (step) {
            0 -> state.bluetoothGranted
            1 -> state.notificationsGranted
            2 -> state.batteryUnrestricted
            3 -> state.exactAlarmsGranted
            4 -> state.dndAccessGranted && G7AlarmSystemAccess.snapshot(this).dndAllowsAlarmAudio == true
            5 -> state.alarmChannels.all { it.importance != null && it.importance > 0 && it.bypassDnd }
            else -> true
        }
    }

    private fun explanation(): String =
        when (step) {
            0 -> "Geräte in der Nähe für den direkten Sensorempfang. Der Sensor wird erst über Sensor verbinden eingerichtet."
            1 -> "Erlaubt Glukosealarme und den laufenden Collectorstatus."
            2 -> "Die Akkuausnahme schützt den zeitkritischen Sensorempfang vor Doze-Einschränkungen."
            3 -> "Erlaubt zeitgenaue Sensorfenster und Alarmwiederholungen."
            4 -> "Der Systemzugriff erlaubt Kanal-Ausnahmen. Eigene Alarmtöne benötigen zusätzlich die Systemausnahme Alarme im Ruhemodus; Kanal-Ausnahmen allein reichen dafür nicht. Aktueller Tonstatus: ${g7AlarmAudioPolicyLabel(G7AlarmSystemAccess.snapshot(this))}."
            5 -> "Alle SugarWear-Alarmkanäle gemeinsam prüfen. Ihre Ausnahme betrifft die Meldungen. Eigene Töne verwenden die System-Alarmausgabe und benötigen deren Freigabe im Ruhemodus."
            else -> "Standard ist die System-Alarmlautstärke. Unter Alarme lässt sich optional eine eigene Lautstärke wählen und jeder eigene SugarWear-Ton testen. Lautstärke 0 bleibt ohne Überschreibung stumm."
        }

    private fun render() {
        val palette = G7AppearanceStore(this).load()
        val density = resources.displayMetrics.density
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((18 * density).toInt(), (30 * density).toInt(), (18 * density).toInt(), (32 * density).toInt())
                setBackgroundColor(palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))
            }

        fun text(value: String, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = if (bold) 16f else 14f
                setTextColor(palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                app.aapswear.uishared.SystemTypography
                    .apply(this, bold)
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

        fun button(title: String, action: () -> Unit) =
            Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 14f
                app.aapswear.uishared.WearSettingsUi
                    .actionButton(this, palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                setOnClickListener { action() }
            }
        applyG7SystemChrome(window, palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))
        root.addView(g7SettingsHeader("Einrichtung", palette))
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE))
                        cornerRadius = 26 * density
                    }
            }
        root.addView(text("${step + 1} von ${steps.size}"))
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        card.addView(text(steps[step], true))
        card.addView(text(explanation()))
        card.addView(
            text(
                if (step == steps.lastIndex) {
                    "Lautstärke prüfen"
                } else if (granted()) {
                    "Erlaubt"
                } else {
                    "Noch nicht erlaubt"
                },
                true,
            ),
        )
        card.addView(button(if (step == steps.lastIndex) "Alarme öffnen" else "Systemeinstellung öffnen") { openStep() })
        root.addView(
            button(
                if (step == steps.lastIndex) {
                    "Fertig"
                } else if (granted()) {
                    "Weiter"
                } else {
                    "Später einrichten"
                },
            ) {
                if (step < steps.lastIndex) {
                    step++
                    render()
                } else {
                    getSharedPreferences("g7_setup", MODE_PRIVATE).edit { putBoolean("complete", true) }
                    finish()
                }
            },
        )
        val scroll = G7EdgeFadeScrollView(this).apply { addView(root, ViewGroup.LayoutParams(-1, -2)) }
        setContentView(scroll)
    }

    private fun openStep() {
        awaitingSystem = true
        when (step) {
            0, 1 -> {
                val permissions = if (step == 0) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                val missing = G7SystemAccess.missingRuntimePermissions(this).filter { it in permissions }.toTypedArray()
                if (missing.isNotEmpty()) requestPermissions(missing, 702) else G7SystemAccess.open(this, G7SystemAccess.appSettingsIntents(packageName))
            }
            2 -> if (!G7BackgroundAccess.openBatterySettings(this)) unavailable()
            3 -> if (!G7SystemAccess.open(this, G7SystemAccess.exactAlarmSettingsIntents(packageName))) unavailable()
            4 -> if (G7AlarmSystemAccess.openPolicySettings(this) != G7SettingsOpenResult.OPENED) unavailable()
            5 -> if (!G7SystemAccess.open(this, G7SystemAccess.notificationSettingsIntents(packageName))) unavailable()
            else -> startActivity(Intent(this, G7AlarmSettingsActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        awaitingSystem = false
        render()
    }

    private fun unavailable() {
        awaitingSystem = false
        Toast.makeText(this, "Diese Systemfreigabe ist auf dieser Uhr nicht verfügbar. Sie wurde nicht erteilt.", Toast.LENGTH_LONG).show()
    }
}
