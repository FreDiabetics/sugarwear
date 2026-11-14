package app.sugarlicious.mobile

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@SuppressLint("SetTextI18n")
@dagger.hilt.android.AndroidEntryPoint
class CloudEndpointTreatmentSettingsActivity : ComponentActivity() {
    private val model by viewModels<CloudEndpointTreatmentSettingsViewModel>()
    private val localNetworkPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> model.permissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Nightscout Treatments"
        setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.requestPermission) {
                if (state.requestPermission) {
                    model.consumePermissionRequest()
                    localNetworkPermissionLauncher.launch(LocalNetworkAccessPolicy.PERMISSION)
                }
            }
            LaunchedEffect(state.message) {
                state.message?.let {
                    model.clearMessage()
                    Toast.makeText(this@CloudEndpointTreatmentSettingsActivity, it, Toast.LENGTH_LONG).show()
                }
            }
            LaunchedEffect(state.finish) {
                if (state.finish) {
                    model.consumeFinish()
                    finish()
                }
            }
            if (state.loaded) {
                val controls = remember { createControls(state) }
                AndroidView(factory = { controls.root }, update = { controls.render(state) })
            }
        }
    }

    private fun submit(test: Boolean) {
        model.submit(test, Build.VERSION.SDK_INT, ContextCompat.checkSelfPermission(this, LocalNetworkAccessPolicy.PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }

    private data class Controls(
        val root: ScrollView,
        val enabled: Switch,
        val url: EditText,
        val auth: Spinner,
        val secret: EditText,
        val status: TextView,
        val test: Button,
        val save: Button,
    ) {
        fun render(state: CloudEndpointTreatmentSettingsState) {
            enabled.isChecked = state.enabled
            if (url.text.toString() != state.url) url.setText(state.url)
            if (secret.text.toString() != state.secret) secret.setText(state.secret)
            val selected = if (state.authMode == CloudEndpointAuthMode.API_SECRET) 0 else 1
            if (auth.selectedItemPosition != selected) auth.setSelection(selected)
            status.text = state.status
            listOf(enabled, url, auth, secret, test, save).forEach { it.isEnabled = !state.busy }
        }
    }

    private fun createControls(initial: CloudEndpointTreatmentSettingsState): Controls {
        val light = app.sugarlicious.mobile.ui.theme.SugarliciousColors.palette.isLight
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }

        fun label(value: String) =
            TextView(this).apply {
                text = value
                setTextColor(MobileSettingsStyle.secondary(light))
                textSize = 13f
                setPadding(0, padding / 2, 0, padding / 4)
            }
        val enabled =
            mobileSettingsSwitch(this, initial.enabled) { model.update(enabled = it) }.apply {
                text = "Nightscout Treatment-Anreicherung aktivieren"
                textSize = 18f
                setTextColor(MobileSettingsStyle.text(light))
            }
        val url =
            EditText(this).apply {
                hint = "https://dein-nightscout.example"
                setTextColor(MobileSettingsStyle.text(light))
                setHintTextColor(MobileSettingsStyle.secondary(light))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(initial.url)
                setSingleLine(true)
            }
        val auth =
            Spinner(this).apply {
                // Keep the selection and popup interaction while omitting the platform arrow.
                background = null
                adapter =
                    ArrayAdapter(
                        this@CloudEndpointTreatmentSettingsActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("API Secret", "Access Token"),
                    )
                setSelection(if (initial.authMode == CloudEndpointAuthMode.API_SECRET) 0 else 1)
            }
        val secret =
            EditText(this).apply {
                setTextColor(MobileSettingsStyle.text(light))
                setHintTextColor(MobileSettingsStyle.secondary(light))
                hint = if (initial.hasSecret) "Gespeichert ï¿½ leer lassen zum Beibehalten" else "Secret oder Token"
                isSaveEnabled = false
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
            }
        val status =
            TextView(this).apply {
                text = initial.status
                setPadding(0, padding, 0, padding)
            }

        url.doAfterTextChanged { model.update(url = it?.toString().orEmpty()) }
        secret.doAfterTextChanged { model.update(secret = it?.toString().orEmpty()) }
        auth.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    model.update(authMode = if (position == 0) CloudEndpointAuthMode.API_SECRET else CloudEndpointAuthMode.ACCESS_TOKEN)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        val test =
            Button(this).apply {
                text = "Verbindung testen und Treatments laden"
                setOnClickListener { submit(test = true) }
            }
        val saveButton =
            Button(this).apply {
                text = "Speichern"
                setOnClickListener { submit(test = false) }
            }

        fun card(vararg views: android.view.View) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(MobileSettingsStyle.card(light))
                        cornerRadius = 28 * resources.displayMetrics.density
                    }
                views.forEach { addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = padding }
            }
        content.addView(
            TextView(this).apply {
                text = "\u2039  Nightscout Treatments"
                textSize = 20f
                setTextColor(MobileSettingsStyle.text(light))
                setPadding(0, padding / 2, 0, padding)
                contentDescription = "Zur\u00fcck \u2013 Nightscout Treatments"
                isFocusable = true
                setOnClickListener { finish() }
            },
        )
        content.addView(card(enabled))
        content.addView(card(label("Nightscout URL"), url, label("Authentifizierung"), auth, label("Zugangsdaten (verschl\u00fcsselt gespeichert)"), secret))
        content.addView(status)
        content.addView(test)
        content.addView(saveButton)
        content.addView(
            TextView(this).apply {
                text =
                    "Nightscout erg\u00e4nzt ausschlie\u00dflich fehlende Bolus- und Carb-Marker. CGM, IOB- und COB-Kurven bleiben AndroidAPS-Daten."
                gravity = Gravity.CENTER
                setPadding(0, padding, 0, padding)
            },
        )
        val root =
            ScrollView(this).apply {
                fitsSystemWindows = true
                setBackgroundColor(MobileSettingsStyle.page(light))
                addView(content)
            }
        return Controls(root, enabled, url, auth, secret, status, test, saveButton)
    }
}
