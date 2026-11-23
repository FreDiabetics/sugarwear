package app.sugarwear

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** One UI Watch layout: confirmed Share & Follow design, independent of the collector screens. */
@dagger.hilt.android.AndroidEntryPoint
class CgmShareSettingsActivity : CgmSettingsComposeActivity() {
    private val settings by viewModels<DexcomShareSettingsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(settings.state, render = ::render)
        render()
    }

    override fun onResume() {
        super.onResume()
        settings.refresh()
        render()
    }

    private fun render() {
        val palette = CgmAppearanceStore(this).load()
        applyCgmSystemChrome(window, Color.BLACK)
        val white = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
        val gray = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)
        val ui = settings.state.value
        val status = ui.status
        val connecting = ui.connecting
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(Color.BLACK)
            }
        val title =
            TextView(this).apply {
                text = "Share & Follow"
                textSize = 18f
                setTextColor(white)
                gravity = Gravity.CENTER
                minimumHeight = dp(56)
                includeFontPadding = false
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
                if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(
            View(this).apply { setBackgroundColor(0x225F6368) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(3)
                bottomMargin = dp(12)
            },
        )

        val heading =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
        heading.addView(label("Dexcom Share", white, true, 15f), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label(if (status.enabled) "Aktiviert" else "Deaktiviert", gray, false, 12f))
        root.addView(heading, spacing(0))

        val uploadFresh =
            status.enabled &&
                status.lastUploadEpochMs > 0L &&
                System.currentTimeMillis() - status.lastUploadEpochMs in 0L..900_000L
        val connectionText =
            when {
                connecting -> "Share wird geprüft"
                ui.setupError -> "Einstellungen fehlgeschlagen"
                status.lastConnectionEpochMs > 0L && status.error == DexcomShareUploadError.RECEIVER_NOT_ASSIGNED -> "Konto bestätigt · Kennung fehlt"
                status.error != DexcomShareUploadError.NONE -> "Verbindungsfehler"
                !status.enabled && status.lastConnectionEpochMs > 0L -> "Konto bestätigt"
                !status.enabled -> "Übertragung aus"
                uploadFresh -> "Upload bestätigt"
                status.lastUploadEpochMs > 0L -> "Upload verzögert"
                status.lastConnectionEpochMs > 0L -> "Konto bestätigt"
                else -> "Nicht verbunden"
            }
        val connectionBody =
            when {
                connecting -> "Anmeldung bei Dexcom Share läuft."
                ui.setupError -> "Share-Einstellungen konnten nicht gespeichert oder entfernt werden."
                status.error != DexcomShareUploadError.NONE -> errorDescription(status.error) + status.httpStatus?.let { " (HTTP $it)" }.orEmpty()
                status.configured && !status.hasReceiver -> "Für den Upload fehlt die Kennung deines Dexcom-Share-Empfängers."
                !status.enabled -> "Die Übertragung an Dexcom Share ist derzeit deaktiviert."
                uploadFresh -> "Letzter Upload vom Share-Server bestätigt."
                status.lastUploadEpochMs > 0L -> "Kein neuer Upload bestätigt. Letzten Upload unten prüfen."
                status.lastConnectionEpochMs > 0L -> "Anmeldung erfolgreich. Ein Upload wurde noch nicht bestätigt."
                else -> "Die Übertragung an Dexcom Share ist noch nicht eingerichtet."
            }
        root.addView(
            card(palette).apply {
                addView(label(connectionText, white, true, 15f))
                addView(label(connectionBody, gray, false, 12f))
            },
            spacing(10),
        )

        root.addView(
            card(palette).apply {
                addView(detailRow("Region", if (status.region == DexcomShareRegion.INTERNATIONAL) "EU" else "USA", white, gray) { accountDialog() })
                addView(separator())
                addView(detailRow("Konto", if (status.configured) "Eingerichtet" else "Nicht eingerichtet", white, gray) { accountDialog() })
                addView(separator())
                addView(detailRow("Letzter Upload", formatTime(status.lastUploadEpochMs), white, gray))
            },
            spacing(10),
        )

        root.addView(
            card(palette).apply {
                addView(
                    app.sugarlicious.uishared.ScrollSafeSwitch(this@CgmShareSettingsActivity).apply {
                        app.sugarlicious.uishared.WearSettingsUi
                            .toggle(this, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), white)
                        text = "Dexcom Share aktivieren"
                        setTextColor(white)
                        textSize = 15f
                        isChecked = status.enabled
                        isEnabled = !ui.updating && !connecting
                        setOnClickListener {
                            // Keep showing the saved state until consent and the settings update finish.
                            isChecked = settings.state.value.status.enabled
                            toggle()
                        }
                    },
                )
                addView(label("Nur bestätigte G7-Messwerte. Dexcom Follow ist keine garantierte Alarmübertragung.", gray, false, 12f))
            },
            spacing(10),
        )

        if (status.configured &&
            !status.enabled &&
            status.lastConnectionEpochMs > 0L &&
            (
                status.error == DexcomShareUploadError.RECEIVER_NOT_ASSIGNED &&
                    status.httpStatus == null ||
                    status.hasReceiver &&
                    status.error == DexcomShareUploadError.NONE
            )
        ) {
            root.addView(
                card(palette).apply {
                    addView(label("Mit diesem Tippen erlaubst du die Zuordnung bei Dexcom und den Upload deiner Glukosewerte. Ein bisheriger Share-Uploader kann dadurch abgelöst werden.", gray, false, 12f))
                    addView(action("Diese Uhr für Share einrichten", palette) { settings.setupWatch(status.revision) }.apply { isEnabled = !ui.updating && !connecting })
                },
                spacing(10),
            )
        }

        root.addView(action("Konto einrichten", palette) { accountDialog() }, spacing(10))
        root.addView(
            android.widget.Button(this).apply {
                text = if (connecting) "Wird geprüft …" else "Verbindung testen"
                isAllCaps = false
                app.sugarlicious.uishared.WearSettingsUi
                    .actionButton(this, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), white)
                setTextColor(white)
                isEnabled = !ui.updating && !connecting
                setOnClickListener { testConnection() }
            },
            spacing(4),
        )
        if (status.configured) {
            root.addView(
                action("Follower einladen", palette) { invitationDialog() }.apply { isEnabled = !ui.updating && !connecting },
                spacing(4),
            )
            ui.invitationMessage?.let {
                root.addView(label(it, white, false, 13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }, spacing(4))
            }
            if (!status.enabled) {
                root.addView(action(if (status.hasReceiver) "Share-Empfänger ändern" else "Kennung manuell eingeben", palette) { receiverDialog() }, spacing(4))
            }
            root.addView(action("Konto entfernen", palette) { confirmClear() }, spacing(4))
        }
        root.addView(
            label("Experimenteller, von Dexcom nicht offiziell unterstützter Share-Uploader. Nur nach eigener Freigabe aktivieren.", gray, false, 12f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(12), dp(6), dp(12))
            },
            spacing(5),
        )
        val scroll =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    addView(root, ViewGroup.LayoutParams(-1, -2))
                }.applyCgmEdgeFade()
        showSettingsView(scroll)
    }

    private fun toggle() {
        val status = settings.state.value.status
        if (status.enabled) {
            settings.toggleUpload()
            return
        }
        if (!status.configured) {
            accountDialog()
            return
        }
        val setupRequired = status.error == DexcomShareUploadError.RECEIVER_NOT_ASSIGNED && status.httpStatus == null && status.lastConnectionEpochMs > 0L
        if (!status.hasReceiver && !setupRequired) {
            testConnection()
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle("Dexcom Share aktivieren?")
            .setMessage(if (setupRequired) "Diese Uhr bei Dexcom zuordnen und Glukosewerte übertragen? Das kann den bisherigen Share-Uploader ablösen." else "Eigene Glukosewerte werden über das Internet an Dexcom übertragen. Die inoffizielle Anbindung kann ausfallen und ersetzt keine medizinischen Alarme.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Aktivieren") { _, _ ->
                if (setupRequired) settings.setupWatch(status.revision) else settings.toggleUpload()
            }.show()
    }

    private fun invitationDialog() {
        val fields =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(4), dp(16), dp(8))
            }

        fun entry(title: String, input: Int): EditText =
            EditText(this).apply {
                hint = title
                isSingleLine = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT or input
                fields.addView(this)
            }
        val name = entry("Name des Followers", android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        val email = entry("E-Mail des Followers", android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val displayName = entry("Dein Anzeigename (sonst SugarWear)", android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        fields.addView(label("Nach Annahme sieht der Follower deine bei Dexcom verfügbaren Glukosewerte und den Verlauf. Follower-Alarme sind in der Einladung ausgeschaltet.", Color.LTGRAY, false, 13f))
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Follower einladen")
                .setView(android.widget.ScrollView(this).apply { addView(fields) })
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Einladung senden", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val invite =
                    DexcomShareInvitation(
                        name.text.toString().trim(),
                        email.text.toString().trim(),
                        displayName.text
                            .toString()
                            .trim()
                            .ifEmpty { "SugarWear" },
                    )
                when {
                    invite.contactName.isEmpty() -> name.error = "Namen eingeben"
                    !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(invite.email)
                        .matches() -> email.error = "E-Mail-Adresse prüfen"
                    else -> {
                        settings.inviteFollower(invite)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun accountDialog() {
        val region = settings.state.value.status.region
        val items = arrayOf("International (z. B. Deutschland)", "USA")
        val fields =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(4))
            }

        fun field(hintText: String): EditText =
            EditText(this).apply {
                hint = hintText
                isSingleLine = true
                textSize = 14f
                fields.addView(this, LinearLayout.LayoutParams(-1, dp(52)))
            }
        val account = field("Dexcom-Kontoname")
        val password =
            manualSharePasswordField(this)?.also { editor ->
                fields.addView(editor, LinearLayout.LayoutParams(-1, dp(52)))
            }
        account.isFocusableInTouchMode = password != null
        account.isFocusable = password != null
        var selectedPassword: String? = null
        val credentialPicker =
            label("Gespeichertes Dexcom-Passwort wählen", Color.WHITE, true, 13f).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                contentDescription = "Passwort über den Android Passwortmanager auswählen"
            }
        credentialPicker.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val response =
                        CredentialManager
                            .create(this@CgmShareSettingsActivity)
                            .getCredential(
                                context = this@CgmShareSettingsActivity,
                                request = GetCredentialRequest(listOf(GetPasswordOption())),
                            )
                    val passwordCredential = response.credential as? PasswordCredential
                    if (passwordCredential == null) {
                        Toast.makeText(this@CgmShareSettingsActivity, "Kein Passwort ausgewählt", Toast.LENGTH_SHORT).show()
                    } else {
                        if (password == null) selectedPassword = passwordCredential.password else password.setText(passwordCredential.password)
                        account.setText(passwordCredential.id)
                        credentialPicker.text = "Passwort sicher ausgewählt"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: GetCredentialException) {
                    android.util.Log.i("SugarWearShare", "Credential selection unavailable: ${error.javaClass.simpleName}")
                    Toast
                        .makeText(
                            this@CgmShareSettingsActivity,
                            "Keine passenden gespeicherten Zugangsdaten.",
                            Toast.LENGTH_LONG,
                        ).show()
                } catch (error: Exception) {
                    android.util.Log.w("SugarWearShare", "Credential provider failed: ${error.javaClass.simpleName}")
                    Toast
                        .makeText(
                            this@CgmShareSettingsActivity,
                            "Passwortmanager derzeit nicht verfügbar.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
        fields.addView(credentialPicker)
        var selected = if (region == DexcomShareRegion.INTERNATIONAL) 0 else 1
        fields.addView(label("Land des Dexcom-Kontos", Color.LTGRAY, true, 13f))
        val regionPicker =
            label(items[selected], Color.WHITE, true, 14f).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                contentDescription = "Land des Dexcom-Kontos wählen"
            }
        regionPicker.setOnClickListener {
            AlertDialog
                .Builder(this)
                .setTitle("Land des Dexcom-Kontos")
                .setItems(items) { _, which ->
                    selected = which
                    regionPicker.text = items[which]
                }.show()
        }
        fields.addView(regionPicker)
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Dexcom Share")
                .setView(android.widget.ScrollView(this).apply { addView(fields) })
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern und prüfen", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val user = account.text.toString().trim()
                val secret = password?.text?.toString() ?: selectedPassword
                if (user.isBlank() || secret.isNullOrBlank()) {
                    Toast.makeText(this, "Dexcom-Kontoname und Passwort angeben", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                settings.saveAccount(user, secret, if (selected == 0) DexcomShareRegion.INTERNATIONAL else DexcomShareRegion.US)
            }
        }
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener {
            password?.text?.clear()
            selectedPassword = null
        }
        dialog.show()
    }

    private fun receiverDialog() {
        val serial =
            EditText(this).apply {
                hint = "Share-Empfängerkennung"
                isSingleLine = true
            }
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Share-Empfänger")
                .setMessage("Bei einem funktionierenden xDrip+-Upload steht die Kennung unter Cloud-Upload → Dexcom Share Server Upload. Bei einem Share-Empfänger steht die Seriennummer (SN) auf der Rückseite. SugarWear prüft die Zuordnung bei Dexcom.")
                .setView(serial)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = serial.text.toString().trim()
                if (isValidShareReceiver(value)) {
                    settings.saveReceiver(value)
                    dialog.dismiss()
                } else {
                    serial.error = "Gültige Share-Empfängerkennung eingeben"
                }
            }
        }
        dialog.show()
    }

    private fun testConnection() {
        if (settings.state.value.connecting || !settings.state.value.status.configured) {
            if (!settings.state.value.status.configured) accountDialog()
            return
        }
        settings.testConnection()
    }

    private fun confirmClear() {
        AlertDialog
            .Builder(this)
            .setTitle("Share-Konto entfernen?")
            .setMessage("Die Zugangsdaten werden von der Watch entfernt und Uploads gestoppt. Sensordaten bleiben erhalten.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Entfernen") { _, _ ->
                settings.clearAccount()
            }.show()
    }

    private fun errorDescription(issue: DexcomShareUploadError): String =
        when (issue) {
            DexcomShareUploadError.NONE -> "Keine Fehler"
            DexcomShareUploadError.NETWORK -> "Dexcom Share derzeit nicht erreichbar."
            DexcomShareUploadError.SCHEDULING -> "Hintergrundübertragung konnte nicht gestartet werden."
            DexcomShareUploadError.AUTHENTICATION -> "Konto oder Passwort prüfen."
            DexcomShareUploadError.RECEIVER_NOT_ASSIGNED -> "Dexcom bestätigt die geprüfte Kennung für dieses Konto nicht. Prüfe die Einrichtung unten."
            DexcomShareUploadError.RECEIVER_MISMATCH -> "Share-Receiver-Seriennummer stimmt nicht überein."
            DexcomShareUploadError.MONITORING -> "Remote-Monitoring-Sitzung nicht aktiv."
            DexcomShareUploadError.RATE_LIMIT -> "Server begrenzt die Anfragehäufigkeit."
            DexcomShareUploadError.SERVER -> "Share-Fehler aus der letzten Prüfung. Tippe auf „Verbindung testen“ für Einzelheiten."
            DexcomShareUploadError.ACCOUNT_SERVER -> "Dexcom konnte die Anmeldung nicht abschließen."
            DexcomShareUploadError.ACCOUNT_RESPONSE -> "Die Antwort von Dexcom bei der Anmeldung war unerwartet."
            DexcomShareUploadError.LOGIN_LIMIT -> "Dexcom hat weitere Anmeldeversuche vorübergehend gesperrt. Bitte später erneut versuchen."
            DexcomShareUploadError.RECEIVER_SERVER -> "Konto bestätigt. Dexcom konnte die Zuordnung der Empfängerkennung nicht prüfen."
            DexcomShareUploadError.RECEIVER_RESPONSE -> "Konto bestätigt. Die Antwort von Dexcom zur Empfängerkennung war unerwartet."
            DexcomShareUploadError.INVITATION -> "Dexcom hat die Einladung nicht bestätigt."
            DexcomShareUploadError.UPLOAD_SERVER -> "Dexcom hat die Übertragung der Messwerte nicht bestätigt."
        }

    private fun card(palette: CgmAppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background =
                GradientDrawable().apply {
                    setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
                    cornerRadius = dp(26).toFloat()
                }
        }

    private fun detailRow(name: String, value: String, white: Int, gray: Int, click: (() -> Unit)? = null) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            addView(label(name, white, false, 13f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(value, gray, false, 12f))
            if (click != null) {
                isClickable = true
                isFocusable = true
                contentDescription = "$name: $value. Antippen zum Bearbeiten."
                setOnClickListener { click() }
                minimumHeight = dp(48)
            }
        }

    private fun label(value: String, color: Int, bold: Boolean, size: Float) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            app.sugarlicious.uishared.SystemTypography
                .apply(this, bold)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun action(textValue: String, palette: CgmAppearancePalette, click: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
            app.sugarlicious.uishared.SystemTypography
                .apply(this, true)
            background =
                GradientDrawable().apply {
                    setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
                    cornerRadius = dp(26).toFloat()
                }
            setOnClickListener { click() }
        }

    private fun separator() =
        View(this).apply { setBackgroundColor(0x225F6368) }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }

    private fun spacing(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun formatTime(epochMs: Long): String =
        if (epochMs <= 0L) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
