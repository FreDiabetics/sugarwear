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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.activity.viewModels
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
                connecting -> "Konto wird geprüft"
                ui.setupError -> "Einstellungen fehlgeschlagen"
                status.error != DexcomShareUploadError.NONE -> "Verbindungsfehler"
                !status.enabled -> "Nicht verbunden"
                uploadFresh -> "Upload bestätigt"
                status.lastUploadEpochMs > 0L -> "Upload verzögert"
                status.lastConnectionEpochMs > 0L -> "Konto bestätigt"
                else -> "Nicht verbunden"
            }
        val connectionBody =
            when {
                connecting -> "Anmeldung bei Dexcom Share läuft."
                ui.setupError -> "Share-Einstellungen konnten nicht gespeichert oder entfernt werden."
                status.error != DexcomShareUploadError.NONE -> errorDescription(status.error)
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
                addView(detailRow("Übertragung", if (status.enabled) "Ein" else "Aus", white, gray) { toggle() })
                addView(label("Nur bestätigte G7-Messwerte. Dexcom Follow ist keine garantierte Alarmübertragung.", gray, false, 12f))
            },
            spacing(10),
        )

        root.addView(action("Konto einrichten", palette) { accountDialog() }, spacing(10))
        root.addView(action(if (connecting) "Bitte prüfen …" else "Verbindung testen", palette) { testConnection() }, spacing(4))
        if (status.configured) {
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
        if (settings.state.value.status.enabled) {
            settings.toggleUpload()
            return
        }
        if (!settings.state.value.status.configured) {
            accountDialog()
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle("Dexcom Share aktivieren?")
            .setMessage("Eigene Glukosewerte werden über das Internet an Dexcom übertragen. Die inoffizielle Anbindung kann ausfallen und ersetzt keine medizinischen Alarme.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Aktivieren") { _, _ ->
                settings.toggleUpload()
            }.show()
    }

    private fun accountDialog() {
        val region = settings.state.value.status.region
        val items = arrayOf("EU / International", "USA")
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
        val serial = field("Share-Receiver-Seriennummer")
        // Wear OS must not request passwords using a watch text field. The system
        // credential picker handles user verification and never exposes the secret in a View.
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
                        selectedPassword = passwordCredential.password
                        account.setText(passwordCredential.id)
                        credentialPicker.text = "Passwort sicher ausgewählt"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: GetCredentialException) {
                    android.util.Log.i("SugarWearShare", "Credential selection unavailable: ${error.javaClass.simpleName}")
                    Toast.makeText(
                        this@CgmShareSettingsActivity,
                        "Keine passenden gespeicherten Zugangsdaten. Bitte einen unterstützten Passwortmanager einrichten.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        fields.addView(credentialPicker)
        fields.addView(label("Die Share-Seriennummer ist nicht der G7-Kopplungscode.", Color.LTGRAY, false, 12f))
        var selected = if (region == DexcomShareRegion.INTERNATIONAL) 0 else 1
        fields.addView(label("Serverregion", Color.LTGRAY, true, 13f))
        val regionPicker =
            label(items[selected], Color.WHITE, true, 14f).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                contentDescription = "Serverregion wählen"
            }
        regionPicker.setOnClickListener {
            AlertDialog
                .Builder(this)
                .setTitle("Serverregion")
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
                .setPositiveButton("Speichern", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val user = account.text.toString().trim()
                val secret = selectedPassword
                val receiver = serial.text.toString().trim()
                if (user.isBlank() || secret.isNullOrBlank() || !receiver.matches(Regex("[A-Za-z0-9_-]{5,40}"))) {
                    Toast.makeText(this, "Gespeichertes Passwort wählen und gültige Share-Seriennummer eingeben", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                settings.saveAccount(user, secret, receiver, if (selected == 0) DexcomShareRegion.INTERNATIONAL else DexcomShareRegion.US)
            }
        }
        dialog.setOnDismissListener { selectedPassword = null }
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
            DexcomShareUploadError.RECEIVER_NOT_ASSIGNED -> "Share-Receiver ist dem Konto nicht zugeordnet."
            DexcomShareUploadError.RECEIVER_MISMATCH -> "Share-Receiver-Seriennummer stimmt nicht überein."
            DexcomShareUploadError.MONITORING -> "Remote-Monitoring-Sitzung nicht aktiv."
            DexcomShareUploadError.RATE_LIMIT -> "Server begrenzt die Anfragehäufigkeit."
            DexcomShareUploadError.SERVER -> "Dexcom-Server hat den Upload nicht bestätigt."
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
