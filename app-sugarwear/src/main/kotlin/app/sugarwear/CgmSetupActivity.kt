package app.sugarwear

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
import dagger.hilt.android.lifecycle.withCreationCallback

/** First-run guide. Only the OS can grant access; skipping never claims success. */
@dagger.hilt.android.AndroidEntryPoint
class CgmSetupActivity : CgmSettingsComposeActivity() {
    private val access by viewModels<CgmSettingsAccessViewModel>()
    private val setup by viewModels<CgmSetupViewModel>(extrasProducer = {
        defaultViewModelCreationExtras.withCreationCallback<CgmSetupViewModel.Factory> { it.create(access::completeSetup) }
    })
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
                setBackgroundColor(palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND))
            }

        fun text(value: String, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = if (bold) 16f else 14f
                setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, bold)
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

        fun button(title: String, action: () -> Unit) =
            Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 14f
                app.sugarlicious.uishared.WearSettingsUi
                    .actionButton(this, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                setOnClickListener { action() }
            }
        applyCgmSystemChrome(window, palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND))
        root.addView(cgmSettingsHeader("Einrichtung", palette))
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
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
        val scroll = CgmEdgeFadeScrollView(this).apply { addView(root, ViewGroup.LayoutParams(-1, -2)) }
        showSettingsView(scroll)
    }

    private fun openStep() {
        if (!setup.beginSystemRequest()) return
        when (step) {
            0, 1 -> {
                val permissions = if (step == 0) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                val missing = CgmSystemAccess.missingRuntimePermissions(this).filter { it in permissions }.toTypedArray()
                if (missing.isNotEmpty()) permissionLauncher.launch(missing) else CgmSystemAccess.open(this, CgmSystemAccess.appSettingsIntents(packageName))
            }
            2 -> if (!CgmBackgroundAccess.openBatterySettings(this)) unavailable()
            3 -> if (!CgmSystemAccess.open(this, CgmSystemAccess.exactAlarmSettingsIntents(packageName))) unavailable()
            4 -> if (CgmAlarmSystemAccess.openPolicySettings(this) != CgmSettingsOpenResult.OPENED) unavailable()
            5 -> if (!CgmSystemAccess.open(this, CgmSystemAccess.notificationSettingsIntents(packageName))) unavailable()
            else -> startActivity(Intent(this, CgmAlarmSettingsActivity::class.java))
        }
    }

    private fun unavailable() {
        setup.systemReturned()
        Toast.makeText(this, "Diese Systemfreigabe ist auf dieser Uhr nicht verfügbar. Sie wurde nicht erteilt.", Toast.LENGTH_LONG).show()
    }
}
