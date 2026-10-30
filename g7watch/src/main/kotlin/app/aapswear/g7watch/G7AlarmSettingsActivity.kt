package app.aapswear.g7watch

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.aapswear.g7.CgmAlarmSettings
import app.aapswear.g7.CgmAlarmType
import app.aapswear.uishared.SharedNumberEditor
import java.util.Locale

/** Immediate-save alarm configuration. Test notifications never touch the real alarm state. */
class G7AlarmSettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView =
            G7EdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    addView(root)
                }.applyG7EdgeFade()
        setContentView(scrollView)
        G7CgmAlarmNotifier.prepareCurrentChannels(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        G7CgmAlarmNotifier.prepareCurrentChannels(this)
        render()
    }

    private fun render() {
        val restoreScrollY = scrollView.scrollY
        val palette = G7AppearanceStore(this).load()
        val background = palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)
        val settings = G7AlarmSettingsStore.read(this)

        root.apply {
            removeAllViews()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18.dp, 32.dp, 18.dp, 56.dp)
            setBackgroundColor(background)
            addView(topBar(palette), fullWidth())
            addView(dndCard(palette), cardParams(7))
            addView(globalCard(settings, palette), cardParams(7))
            CgmAlarmType.entries.forEach { type -> addView(alarmCard(type, settings, palette), cardParams(7)) }
        }
        restoreScrollPosition(restoreScrollY)
    }

    private fun dndCard(palette: G7AppearancePalette): LinearLayout {
        val status = G7AlarmSystemAccess.snapshot(this)
        val granted = status.policyAccessGranted
        return card(palette).apply {
            addView(label("NICHT STÖREN", 8f, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), true))
            addView(
                label(
                    if (granted) "Nicht-Stören-Zugriff erlaubt" else "Nicht-Stören-Zugriff fehlt",
                    12f,
                    if (granted) palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY) else palette.argb(G7AppearanceRole.GLUCOSE_STALE),
                    true,
                ),
            )
            addView(
                label(
                    "Eigene SugarWear-Töne verwenden die System-Alarmausgabe. Im Ruhemodus muss dort Alarme erlaubt sein. Die Kanal-Ausnahme allein gibt den eigenen Ton nicht frei. Lautstärke 0 bleibt ohne eigene Lautstärke stumm.",
                    8.5f,
                    palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                ),
            )
            addView(label(g7AlarmAudioPolicyLabel(status), 10f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true))
            addView(label(if (G7BackgroundAccess.isBatteryUnrestricted(this@G7AlarmSettingsActivity)) "Doze-Akkuexemption erlaubt" else "Doze-Akkuexemption fehlt", 9f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)))
            addView(
                actionButton("Berechtigungen und Alarmkan\u00e4le", palette) {
                    startActivity(Intent(this@G7AlarmSettingsActivity, G7PermissionsActivity::class.java))
                },
            )
        }
    }

    private fun globalCard(
        settings: CgmAlarmSettings,
        palette: G7AppearancePalette,
    ): LinearLayout =
        card(palette).apply {
            addView(label("ALARMVERHALTEN", 8f, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), true))
            addView(toggle("Sound", settings.soundEnabled, palette) { save(settings.copy(soundEnabled = it)) })
            val volumeOverride = G7AlarmVolume.enabled(this@G7AlarmSettingsActivity)
            addView(
                toggle("Eigene Alarmlautstärke", volumeOverride, palette) {
                    G7AlarmVolume.configure(this@G7AlarmSettingsActivity, it)
                    render()
                },
            )
            val audio = getSystemService(android.media.AudioManager::class.java)
            addView(label("System · ${audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)}", 9f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)))
            if (volumeOverride) {
                addView(
                    stepper("Alarmlautstärke", "${G7AlarmVolume.percent(this@G7AlarmSettingsActivity)} %", palette, {
                        G7AlarmVolume.configure(this@G7AlarmSettingsActivity, true, G7AlarmVolume.percent(this@G7AlarmSettingsActivity) - 10)
                        render()
                    }, {
                        G7AlarmVolume.configure(this@G7AlarmSettingsActivity, true, G7AlarmVolume.percent(this@G7AlarmSettingsActivity) + 10)
                        render()
                    }),
                )
            }
            addView(label("Ohne eigene Lautstärke gilt die System-Alarmlautstärke. Mit eigener Lautstärke wird sie für 30 Sekunden überschrieben und anschließend wiederhergestellt. Eigene SugarWear-Töne bleiben aktiv.", 8.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)))

            addView(toggle("Vibration", settings.vibrationEnabled, palette) { save(settings.copy(vibrationEnabled = it)) })
            addView(toggle("Wiederholen", settings.repeatEnabled, palette) { save(settings.copy(repeatEnabled = it)) })
            addView(
                stepper("Wiederholung", "${settings.repeatIntervalMinutes} min", palette, {
                    save(settings.copy(repeatIntervalMinutes = (settings.repeatIntervalMinutes - 5).coerceAtLeast(5)))
                }, {
                    save(settings.copy(repeatIntervalMinutes = (settings.repeatIntervalMinutes + 5).coerceAtMost(120)))
                }),
            )
        }

    private fun alarmCard(
        type: CgmAlarmType,
        settings: CgmAlarmSettings,
        palette: G7AppearancePalette,
    ): LinearLayout =
        card(palette).apply {
            addView(toggle(g7AlarmTitle(type), alarmEnabled(settings, type), palette) { save(withAlarmEnabled(settings, type, it)) })
            thresholdText(
                type,
                settings,
            )?.let { addView(label("Schwelle · $it", 9f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true)) }
            addView(label("Sound · ${g7AlarmSoundName(type)}", 8.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)))
            addView(
                label(
                    "${if (settings.vibrationEnabled) "Vibration aktiv" else "Vibration aus"} · ${if (settings.repeatEnabled) "Wiederholung ${settings.repeatIntervalMinutes} min" else "Keine Wiederholung"}",
                    8.5f,
                    palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                ),
            )
            if (type in editableThresholdTypes) {
                addView(
                    actionButton("Schwelle eingeben · ${thresholdText(type, settings).orEmpty()}", palette) {
                        showThresholdEditor(type, settings)
                    },
                )
            }
            addView(
                actionButton("Test", palette) {
                    G7CgmAlarmNotifier.showTest(this@G7AlarmSettingsActivity, type, G7AlarmSettingsStore.read(this@G7AlarmSettingsActivity))
                },
            )
        }

    private fun save(settings: CgmAlarmSettings) {
        G7AlarmSettingsStore.write(this, settings)
        G7CgmAlarmNotifier.ensureAllChannels(this, settings)
        render()
    }

    private fun topBar(palette: G7AppearancePalette) = g7SettingsHeader("Alarme", palette)

    private fun showThresholdEditor(
        type: CgmAlarmType,
        settings: CgmAlarmSettings,
    ) {
        val restoreScrollY = scrollView.scrollY
        val current = thresholdValue(type, settings) ?: return
        val shown =
            if (type == CgmAlarmType.RAPID_RISE || type == CgmAlarmType.RAPID_FALL) {
                String.format(Locale.US, "%.1f", current)
            } else {
                current.toInt().toString()
            }

        fun parsed(value: String) = value.trim().replace(',', '.').toDoubleOrNull()
        SharedNumberEditor.showText(
            activity = this,
            title = "${g7AlarmTitle(type)} · Schwelle",
            current = shown,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { text ->
                if (parsed(text)?.let { withThreshold(settings, type, it) } == null) {
                    thresholdValidationMessage(type, settings)
                } else {
                    null
                }
            },
            onValue = { text ->
                parsed(text)?.let { withThreshold(settings, type, it) }?.let(::save)
            },
            onDismiss = { restoreScrollPosition(restoreScrollY) },
        )
    }

    private fun restoreScrollPosition(scrollY: Int) {
        scrollView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                    val maxScroll = (root.measuredHeight - scrollView.height).coerceAtLeast(0)
                    scrollView.scrollTo(0, scrollY.coerceAtMost(maxScroll))
                    return true
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun toggle(
        title: String,
        checked: Boolean,
        palette: G7AppearancePalette,
        changed: (Boolean) -> Unit,
    ) = app.aapswear.uishared.ScrollSafeSwitch(this).apply {
        app.aapswear.uishared.WearSettingsUi
            .toggle(this, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        text = title
        isChecked = checked
        setTextColor(palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        textSize = 15f
        app.aapswear.uishared.SystemTypography
            .apply(this, true)
        setOnCheckedChangeListener { _, value -> if (value != checked) changed(value) }
    }

    private fun stepper(
        title: String,
        value: String,
        palette: G7AppearancePalette,
        minus: () -> Unit,
        plus: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            label("$title · $value", 8.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(actionButton("−", palette, minus), LinearLayout.LayoutParams(42.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(actionButton("+", palette, plus), LinearLayout.LayoutParams(42.dp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = 4.dp })
    }

    private fun actionButton(
        title: String,
        palette: G7AppearancePalette,
        action: () -> Unit,
    ) = Button(this).apply {
        text = title
        textSize = 14f
        minimumHeight = 40.dp
        app.aapswear.uishared.SystemTypography
            .apply(this, true)
        isAllCaps = false
        setTextColor(palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        app.aapswear.uishared.WearSettingsUi
            .actionButton(this, palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        setOnClickListener { action() }
    }

    private fun card(palette: G7AppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp, 10.dp, 13.dp, 10.dp)
            background = rounded(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 19f)
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        includeFontPadding = false
        text = value
        textSize =
            app.aapswear.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
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
        cornerRadius = maxOf(radiusDp, 26f) * resources.displayMetrics.density
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardParams(top: Int) = fullWidth().apply { topMargin = top.dp }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

internal fun alarmEnabled(
    settings: CgmAlarmSettings,
    type: CgmAlarmType,
): Boolean =
    when (type) {
        CgmAlarmType.VERY_HIGH -> settings.veryHighEnabled
        CgmAlarmType.HIGH -> settings.highEnabled
        CgmAlarmType.LOW -> settings.lowEnabled
        CgmAlarmType.VERY_LOW -> settings.veryLowEnabled
        CgmAlarmType.RAPID_RISE -> settings.rapidRiseEnabled
        CgmAlarmType.RAPID_FALL -> settings.rapidFallEnabled
        CgmAlarmType.SIGNAL_LOSS -> settings.signalLossEnabled
        CgmAlarmType.SENSOR_ERROR -> settings.sensorErrorEnabled
    }

internal fun withAlarmEnabled(
    settings: CgmAlarmSettings,
    type: CgmAlarmType,
    enabled: Boolean,
): CgmAlarmSettings =
    when (type) {
        CgmAlarmType.VERY_HIGH -> settings.copy(veryHighEnabled = enabled)
        CgmAlarmType.HIGH -> settings.copy(highEnabled = enabled)
        CgmAlarmType.LOW -> settings.copy(lowEnabled = enabled)
        CgmAlarmType.VERY_LOW -> settings.copy(veryLowEnabled = enabled)
        CgmAlarmType.RAPID_RISE -> settings.copy(rapidRiseEnabled = enabled)
        CgmAlarmType.RAPID_FALL -> settings.copy(rapidFallEnabled = enabled)
        CgmAlarmType.SIGNAL_LOSS -> settings.copy(signalLossEnabled = enabled)
        CgmAlarmType.SENSOR_ERROR -> settings.copy(sensorErrorEnabled = enabled)
    }

internal fun thresholdText(
    type: CgmAlarmType,
    settings: CgmAlarmSettings,
): String? =
    when (type) {
        CgmAlarmType.VERY_HIGH -> "${settings.veryHighThreshold.toInt()} mg/dL"
        CgmAlarmType.HIGH -> "${settings.highThreshold.toInt()} mg/dL"
        CgmAlarmType.LOW -> "${settings.lowThreshold.toInt()} mg/dL"
        CgmAlarmType.VERY_LOW -> "${settings.veryLowThreshold.toInt()} mg/dL"
        CgmAlarmType.RAPID_RISE -> String.format(Locale.US, "%.1f mg/dL/min", settings.rapidRiseThreshold)
        CgmAlarmType.RAPID_FALL -> String.format(Locale.US, "%.1f mg/dL/min", settings.rapidFallThreshold)
        CgmAlarmType.SIGNAL_LOSS -> "${settings.signalLossMinutes} min"
        CgmAlarmType.SENSOR_ERROR -> null
    }

private val editableThresholdTypes =
    setOf(
        CgmAlarmType.VERY_HIGH,
        CgmAlarmType.HIGH,
        CgmAlarmType.LOW,
        CgmAlarmType.VERY_LOW,
        CgmAlarmType.RAPID_RISE,
        CgmAlarmType.RAPID_FALL,
    )

internal fun thresholdValue(
    type: CgmAlarmType,
    settings: CgmAlarmSettings,
): Double? =
    when (type) {
        CgmAlarmType.VERY_HIGH -> settings.veryHighThreshold
        CgmAlarmType.HIGH -> settings.highThreshold
        CgmAlarmType.LOW -> settings.lowThreshold
        CgmAlarmType.VERY_LOW -> settings.veryLowThreshold
        CgmAlarmType.RAPID_RISE -> settings.rapidRiseThreshold
        CgmAlarmType.RAPID_FALL -> settings.rapidFallThreshold
        CgmAlarmType.SIGNAL_LOSS, CgmAlarmType.SENSOR_ERROR -> null
    }

internal fun withThreshold(
    settings: CgmAlarmSettings,
    type: CgmAlarmType,
    value: Double,
): CgmAlarmSettings? {
    if (!value.isFinite()) return null
    val updated =
        when (type) {
            CgmAlarmType.VERY_HIGH -> if (value in (settings.highThreshold + 1.0)..400.0) settings.copy(veryHighThreshold = value) else null
            CgmAlarmType.HIGH ->
                if (value > settings.lowThreshold &&
                    value < settings.veryHighThreshold
                ) {
                    settings.copy(highThreshold = value)
                } else {
                    null
                }
            CgmAlarmType.LOW ->
                if (value > settings.veryLowThreshold &&
                    value < settings.highThreshold
                ) {
                    settings.copy(lowThreshold = value)
                } else {
                    null
                }
            CgmAlarmType.VERY_LOW -> if (value in 40.0..<settings.lowThreshold) settings.copy(veryLowThreshold = value) else null
            CgmAlarmType.RAPID_RISE -> if (value in 0.5..10.0) settings.copy(rapidRiseThreshold = value) else null
            CgmAlarmType.RAPID_FALL -> if (value in 0.5..10.0) settings.copy(rapidFallThreshold = value) else null
            CgmAlarmType.SIGNAL_LOSS, CgmAlarmType.SENSOR_ERROR -> null
        }
    return updated?.takeIf {
        app.aapswear.model
            .CgmThresholds(it.veryHighThreshold, it.highThreshold, it.lowThreshold, it.veryLowThreshold)
            .isValid &&
            it.rapidRiseThreshold in 0.5..10.0 &&
            it.rapidFallThreshold in 0.5..10.0
    }
}

private fun thresholdValidationMessage(
    type: CgmAlarmType,
    settings: CgmAlarmSettings,
): String =
    when (type) {
        CgmAlarmType.VERY_HIGH -> "Wert muss über ${settings.highThreshold.toInt()} und höchstens 400 sein"
        CgmAlarmType.HIGH -> "Wert muss zwischen ${settings.lowThreshold.toInt()} und ${settings.veryHighThreshold.toInt()} liegen"
        CgmAlarmType.LOW -> "Wert muss zwischen ${settings.veryLowThreshold.toInt()} und ${settings.highThreshold.toInt()} liegen"
        CgmAlarmType.VERY_LOW -> "Wert muss mindestens 40 und unter ${settings.lowThreshold.toInt()} sein"
        CgmAlarmType.RAPID_RISE, CgmAlarmType.RAPID_FALL -> "Wert muss zwischen 0,5 und 10,0 liegen"
        CgmAlarmType.SIGNAL_LOSS, CgmAlarmType.SENSOR_ERROR -> "Nicht editierbar"
    }

internal fun adjustThreshold(
    settings: CgmAlarmSettings,
    type: CgmAlarmType,
    delta: Double,
): CgmAlarmSettings =
    when (type) {
        CgmAlarmType.VERY_HIGH ->
            settings.copy(
                veryHighThreshold = (settings.veryHighThreshold + delta).coerceIn(settings.highThreshold + 5.0, 400.0),
            )
        CgmAlarmType.HIGH ->
            settings.copy(
                highThreshold =
                    (settings.highThreshold + delta).coerceIn(
                        settings.lowThreshold + 5.0,
                        settings.veryHighThreshold - 5.0,
                    ),
            )
        CgmAlarmType.LOW ->
            settings.copy(
                lowThreshold =
                    (settings.lowThreshold + delta).coerceIn(
                        settings.veryLowThreshold + 5.0,
                        settings.highThreshold - 5.0,
                    ),
            )
        CgmAlarmType.VERY_LOW ->
            settings.copy(
                veryLowThreshold = (settings.veryLowThreshold + delta).coerceIn(40.0, settings.lowThreshold - 5.0),
            )
        CgmAlarmType.RAPID_RISE -> settings.copy(rapidRiseThreshold = (settings.rapidRiseThreshold + delta).coerceIn(0.5, 10.0))
        CgmAlarmType.RAPID_FALL -> settings.copy(rapidFallThreshold = (settings.rapidFallThreshold + delta).coerceIn(0.5, 10.0))
        CgmAlarmType.SIGNAL_LOSS, CgmAlarmType.SENSOR_ERROR -> settings
    }
