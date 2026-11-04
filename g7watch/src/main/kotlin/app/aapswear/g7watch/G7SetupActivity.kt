package app.aapswear.g7watch

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

/** First-run guide. Only the OS can grant access; skipping never claims success. */
class G7SetupActivity : G7SettingsComposeActivity() {
    private val access by viewModels<G7SettingsAccessViewModel> { androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application) }
    private val setup by viewModels<G7SetupViewModel> {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return G7SetupViewModel(access::completeSetup) as T
            }
        }
    }
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            setup.systemReturned()
            access.refresh()
            render()
        }
    private val step get() = setup.state.value.step
    private val awaitingSystem get() = setup.state.value.awaitingSystem
    private val steps = listOf("Bluetooth", "Benachrichtigungen", "Hintergrundbetrieb", "Genaue Alarme", "Nicht stören", "Alarmkanäle", "Alarmlautstärke")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(access.state, render = ::render)
        bindSettingsState(setup.state, render = ::render)
        bindSettingsState(access.setupCompleted, render = ::render)

        if (savedInstanceState != null) setup.restore(savedInstanceState.getInt("step"), savedInstanceState.getBoolean("awaiting"))
        access.refresh(prepare = true)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        outState.putBoolean("awaiting", awaitingSystem)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        access.refresh(prepare = true)
        setup.systemReturned()
        access.refresh()
        render()
    }

    private fun granted(): Boolean = setup.granted(checkNotNull(access.state.value.snapshot).access, checkNotNull(access.state.value.snapshot).alarmAccess)

    private fun explanation(): String = setup.explanation(checkNotNull(access.state.value.snapshot).alarmAccess)

    private fun render() {
        if (!settingsReady(access.state.value, { access.refresh() })) return
        if (access.setupCompleted.value) {
            finish()
            return
        }
        if (setup.state.value.complete) access.completeSetup()
        val palette = checkNotNull(access.state.value.snapshot).palette
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
                setup.advance()
                render()
            },
        )
        val scroll = G7EdgeFadeScrollView(this).apply { addView(root, ViewGroup.LayoutParams(-1, -2)) }
        showSettingsView(scroll)
    }

    private fun openStep() {
        if (!setup.beginSystemRequest()) return
        when (step) {
            0, 1 -> {
                val permissions = if (step == 0) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                val missing = G7SystemAccess.missingRuntimePermissions(this).filter { it in permissions }.toTypedArray()
                if (missing.isNotEmpty()) permissionLauncher.launch(missing) else G7SystemAccess.open(this, G7SystemAccess.appSettingsIntents(packageName))
            }
            2 -> if (!G7BackgroundAccess.openBatterySettings(this)) unavailable()
            3 -> if (!G7SystemAccess.open(this, G7SystemAccess.exactAlarmSettingsIntents(packageName))) unavailable()
            4 -> if (G7AlarmSystemAccess.openPolicySettings(this) != G7SettingsOpenResult.OPENED) unavailable()
            5 -> if (!G7SystemAccess.open(this, G7SystemAccess.notificationSettingsIntents(packageName))) unavailable()
            else -> startActivity(Intent(this, G7AlarmSettingsActivity::class.java))
        }
    }

    private fun unavailable() {
        setup.systemReturned()
        Toast.makeText(this, "Diese Systemfreigabe ist auf dieser Uhr nicht verfügbar. Sie wurde nicht erteilt.", Toast.LENGTH_LONG).show()
    }
}
