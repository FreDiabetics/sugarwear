package app.sugarwear

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
import androidx.activity.viewModels
import app.sugarlicious.uishared.SharedNumberEditor
import java.util.Locale

@dagger.hilt.android.AndroidEntryPoint
class CgmAlarmSettingsActivity : CgmSettingsComposeActivity() {
    private lateinit var root: LinearLayout
    private lateinit var scrollView: ScrollView
    private val access by viewModels<CgmSettingsAccessViewModel>()
    private var selectedAlarm: SugarWearAlarmType?
        get() = access.navigation.value.selectedAlarm
        set(value) = access.selectAlarm(value)
    private var alarmPage: String?
        get() = access.navigation.value.alarmPage
        set(value) = access.selectPage(value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(access.state, { old, new -> old.snapshot != new.snapshot || old.error != new.error }, ::render)
        bindSettingsState(access.navigation, render = ::render)

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            },
        )
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    addView(root)
                }.applyCgmEdgeFade()
        showSettingsView(scrollView)
        access.refresh(prepare = true)
        render()
    }

    override fun onResume() {
        super.onResume()
        access.refresh(prepare = true)
        render()
    }

    private fun render() {
        if (!settingsReady(access.state.value, { access.refresh() })) return
        showSettingsView(scrollView)
        val restoreScrollY = scrollView.scrollY
        val palette = checkNotNull(access.state.value.snapshot).palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val configured = checkNotNull(access.state.value.snapshot).alarms
        val activeProfile = configured.activeProfile(System.currentTimeMillis()).profile
        val editedProfile = access.navigation.value.editedAlarmProfile ?: activeProfile
        val settings = configured.forProfile(editedProfile)

        root.apply {
            removeAllViews()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            app.sugarlicious.uishared.WearSettingsUi
                .pagePadding(this)
            setBackgroundColor(background)
            addView(topBar(palette), fullWidth())

            val alarmAccess = checkNotNull(access.state.value.snapshot).alarmAccess
            val selected = selectedAlarm
            if (selected != null) {
                addView(alarmDetailCard(selected, settings, palette), cardParams(7))
            } else if (alarmPage == "Ruhemodi") {
                addView(quietModesCard(settings, palette), cardParams(7))
            } else {
                // Main Menu
                addView(
                    card(palette).apply {
                        addView(toggle("SugarWear-Alarme", checkNotNull(access.state.value.snapshot).localAlarmsEnabled, palette) { access.localAlarms(it) })
                        addView(label("Unabhängig von der Glukosequelle in Sugarlicious. Bei pausiertem Sensor bleiben Alarme aus.", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                    },
                    cardParams(7),
                )
                if (!alarmAccess.notificationPermissionGranted || !alarmAccess.appNotificationsEnabled) {
                    addView(
                        card(palette).apply {
                            addView(label("BENACHRICHTIGUNGEN BLOCKIERT", 10f, 0xFFFF5C69.toInt(), true))
                            addView(
                                label(
                                    "SugarWear-Alarme können nicht auf der Uhr angezeigt werden. Erlaube App-Benachrichtigungen in den Uhreinstellungen.",
                                    11f,
                                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                                ),
                            )
                            addView(
                                actionButton("Berechtigungen prüfen", palette) {
                                    startActivity(android.content.Intent(this@CgmAlarmSettingsActivity, CgmPermissionsActivity::class.java))
                                },
                            )
                        },
                        cardParams(7),
                    )
                }
                addView(pauseAlarmsCard(palette), cardParams(7))
                addView(alarmProfilesCard(configured, activeProfile, editedProfile, palette), cardParams(7))

                // Section 1: General/Quiet Modes
                val generalRows =
                    listOf("Ruhemodi").map { title ->
                        app.sugarlicious.uishared.WearSettingsUi.navigationRow(this@CgmAlarmSettingsActivity, title, null, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)) {
                            alarmPage = title
                            scrollView.scrollTo(0, 0)
                            render()
                        }
                    }
                addView(
                    app.sugarlicious.uishared.WearSettingsUi
                        .navigationGroup(this@CgmAlarmSettingsActivity, generalRows, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                    cardParams(10),
                )

                // Section 2: Glucose Warnings
                addView(label("GEWEBEGLUKOSE-WARNUNGEN", 9f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true).apply { setPadding(0, 8.dp, 0, 4.dp) })
                val glucoseAlarmTypes =
                    listOf(
                        SugarWearAlarmType.URGENT_LOW,
                        SugarWearAlarmType.URGENT_LOW_SOON,
                        SugarWearAlarmType.LOW,
                        SugarWearAlarmType.HIGH,
                        SugarWearAlarmType.RAPID_RISE,
                        SugarWearAlarmType.RAPID_FALL,
                    )
                val glucoseRows =
                    glucoseAlarmTypes.map { type ->
                        val rule = settings.ruleFor(type)
                        val statusText =
                            if (type == SugarWearAlarmType.URGENT_LOW) {
                                "55 mg/dL (Aktiv)"
                            } else if (rule.enabled) {
                                "Aktiv"
                            } else {
                                "Aus"
                            }
                        app.sugarlicious.uishared.WearSettingsUi.navigationRow(this@CgmAlarmSettingsActivity, cgmAlarmTitle(type), statusText, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)) {
                            access.editAlarmProfile(editedProfile)
                            selectedAlarm = type
                            scrollView.scrollTo(0, 0)
                            render()
                        }
                    }
                addView(
                    app.sugarlicious.uishared.WearSettingsUi
                        .navigationGroup(this@CgmAlarmSettingsActivity, glucoseRows, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                    cardParams(6),
                )

                // Section 3: System Warnings
                addView(label("SYSTEMWARNUNGEN", 9f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true).apply { setPadding(0, 8.dp, 0, 4.dp) })
                val systemAlarmTypes =
                    listOf(
                        SugarWearAlarmType.SIGNAL_LOSS,
                        SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM,
                        SugarWearAlarmType.TECHNICAL_WARNING,
                    )
                val systemRows =
                    systemAlarmTypes.map { type ->
                        val rule = settings.ruleFor(type)
                        val statusText =
                            if (type == SugarWearAlarmType.TECHNICAL_WARNING) {
                                "Aktiv"
                            } else if (rule.enabled) {
                                "Aktiv"
                            } else {
                                "Aus"
                            }
                        app.sugarlicious.uishared.WearSettingsUi.navigationRow(this@CgmAlarmSettingsActivity, cgmAlarmTitle(type), statusText, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)) {
                            access.editAlarmProfile(editedProfile)
                            selectedAlarm = type
                            scrollView.scrollTo(0, 0)
                            render()
                        }
                    }
                addView(
                    app.sugarlicious.uishared.WearSettingsUi
                        .navigationGroup(this@CgmAlarmSettingsActivity, systemRows, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                    cardParams(6),
                )

                // Section 4: Reset
                addView(
                    card(palette).apply {
                        addView(
                            actionButton("Warnungseinstellungen zurücksetzen", palette) {
                                edit(CgmAlarmSettingsEdit.ResetAll)
                            },
                        )
                    },
                    cardParams(10),
                )
            }
        }
        restoreScrollPosition(restoreScrollY)
    }

    private fun quietModesCard(
        settings: SugarWearAlarmSettings,
        palette: CgmAppearancePalette,
    ): LinearLayout =
        card(palette).apply {
            addView(label("RUHEMODI", 8f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true))
            addView(toggle("Vibrieren", settings.globalVibrateOnly, palette) { edit(CgmAlarmSettingsEdit.GlobalVibrateOnly(it)) })
            addView(toggle("Alle stumm", settings.globalMuteAll, palette) { edit(CgmAlarmSettingsEdit.GlobalMuteAll(it)) })
            addView(label("Im Modus 'Vibrieren' werden alle Alarme nur als Vibration ausgegeben. Bei 'Alle stumm' werden Ton und Vibration unterdrückt. Fachliche Alarmzustände bleiben aktiv.", 8.5f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
        }

    private var pauseDurationMinutes = 60

    private fun pauseAlarmsCard(palette: CgmAppearancePalette): LinearLayout {
        val snapshot = checkNotNull(access.state.value.snapshot)
        val pauseUntil = snapshot.pauseUntilEpochMs
        val now = System.currentTimeMillis()
        val isPaused = pauseUntil != null && pauseUntil > now

        return card(palette).apply {
            addView(label("ALARME PAUSIEREN (AUSSER DRINGEND NIEDRIG)", 8f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true))
            if (isPaused) {
                val pauseUntilTime = checkNotNull(pauseUntil)
                val remainingMs = maxOf(0L, pauseUntilTime - now)
                val remainingMinutes = (remainingMs + 59_999L) / 60_000L
                val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(pauseUntilTime))
                addView(label("Alarme pausiert bis $timeFormat Uhr", 13f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                addView(label("Verbleibend: $remainingMinutes Min.", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                addView(label("Dringend niedriger Alarm (55 mg/dL) bleibt aktiv!", 11f, 0xFFFF5C69.toInt(), true))
                addView(
                    actionButton("Pausierung aufheben", palette) {
                        access.clearAlarmPause()
                        render()
                    },
                )
            } else {
                if (pauseDurationMinutes !in 15..720) pauseDurationMinutes = snapshot.lastPauseDurationMinutes
                addView(label("Schlummert alle Alarme außer Niedriger Wert (dringend) für die gewählte Dauer.", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                val durationText =
                    if (pauseDurationMinutes >= 60 && pauseDurationMinutes % 60 == 0) {
                        "${pauseDurationMinutes / 60} Std."
                    } else {
                        "$pauseDurationMinutes Min."
                    }
                addView(
                    stepper("Pausendauer", durationText, palette, {
                        pauseDurationMinutes =
                            when {
                                pauseDurationMinutes > 240 -> pauseDurationMinutes - 120
                                pauseDurationMinutes > 60 -> pauseDurationMinutes - 60
                                pauseDurationMinutes > 30 -> pauseDurationMinutes - 15
                                else -> maxOf(15, pauseDurationMinutes - 5)
                            }
                        render()
                    }, {
                        pauseDurationMinutes =
                            when {
                                pauseDurationMinutes >= 240 -> minOf(720, pauseDurationMinutes + 120)
                                pauseDurationMinutes >= 60 -> pauseDurationMinutes + 60
                                pauseDurationMinutes >= 30 -> pauseDurationMinutes + 15
                                else -> pauseDurationMinutes + 5
                            }
                        render()
                    }),
                )
                addView(
                    actionButton("Alarme pausieren ($durationText)", palette) {
                        access.pauseAlarms(pauseDurationMinutes)
                        render()
                    },
                )
            }
        }
    }

    private fun alarmDetailCard(
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
        palette: CgmAppearancePalette,
    ): LinearLayout =
        card(palette).apply {
            val rule = settings.ruleFor(type)
            addView(label(cgmAlarmTitle(type).uppercase(Locale.GERMAN), 8f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true))

            // Non-disableable check
            if (type.isMandatory) {
                addView(label("Immer aktiv (nicht deaktivierbar)", 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
            } else {
                addView(toggle("Alarm aktiv", rule.enabled, palette) { edit(CgmAlarmSettingsEdit.AlarmEnabled(type, it)) })
            }

            // Specific type rules & controls
            when (type) {
                SugarWearAlarmType.URGENT_LOW -> {
                    addView(label("Feste Warnstufe: 55 mg/dL", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                    addView(label("Schlummerzeit: 30 Min. (nicht änderbar)", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                }
                SugarWearAlarmType.URGENT_LOW_SOON -> {
                    addView(label("Warnt, wenn 55 mg/dL in weniger als 20 Min. erreicht wird.", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                    addView(label("Schlummerzeit: 30 Min.", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                }
                SugarWearAlarmType.LOW -> {
                    addView(label("Grenzwert: ${rule.thresholdMgDl.toInt()} mg/dL", 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        actionButton("Grenzwert bearbeiten", palette) {
                            showNumberEditor("Niedrig-Grenzwert", rule.thresholdMgDl.toInt().toString(), InputType.TYPE_CLASS_NUMBER) { value ->
                                value.toDoubleOrNull()?.let { edit(CgmAlarmSettingsEdit.Threshold(type, it)) }
                            }
                        },
                    )
                    addView(toggle("Schlummern aktiv", rule.snoozeEnabled, palette) { edit(CgmAlarmSettingsEdit.SnoozeEnabled(type, it)) })
                    if (rule.snoozeEnabled) {
                        addView(
                            stepper("Schlummerzeit", "${rule.snoozeMinutes} min", palette, {
                                edit(CgmAlarmSettingsEdit.SnoozeMinutes(type, rule.snoozeMinutes - 5))
                            }, {
                                edit(CgmAlarmSettingsEdit.SnoozeMinutes(type, rule.snoozeMinutes + 5))
                            }),
                        )
                    }
                }
                SugarWearAlarmType.HIGH -> {
                    addView(label("Grenzwert: ${rule.thresholdMgDl.toInt()} mg/dL", 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        actionButton("Grenzwert bearbeiten", palette) {
                            showNumberEditor("Hoch-Grenzwert", rule.thresholdMgDl.toInt().toString(), InputType.TYPE_CLASS_NUMBER) { value ->
                                value.toDoubleOrNull()?.let { edit(CgmAlarmSettingsEdit.Threshold(type, it)) }
                            }
                        },
                    )
                    val delayText =
                        when {
                            rule.highDelayMinutes == 0 -> "Aus (0 min)"
                            rule.highDelayMinutes < 60 -> "${rule.highDelayMinutes} min"
                            rule.highDelayMinutes % 60 == 0 -> "${rule.highDelayMinutes / 60} Std."
                            else -> "${rule.highDelayMinutes / 60} Std. ${rule.highDelayMinutes % 60} min"
                        }
                    addView(
                        stepper("Erste Warnung verzögern", delayText, palette, {
                            val delta = if (rule.highDelayMinutes > 60) 15 else 5
                            edit(CgmAlarmSettingsEdit.HighDelay(type, rule.highDelayMinutes - delta))
                        }, {
                            val delta = if (rule.highDelayMinutes >= 60) 15 else 5
                            edit(CgmAlarmSettingsEdit.HighDelay(type, rule.highDelayMinutes + delta))
                        }),
                    )
                    addView(
                        actionButton("Verzögerung eingeben (5 min - 4 Std.)", palette) {
                            showNumberEditor("Verzögerung in Min. (5-240)", rule.highDelayMinutes.toString(), InputType.TYPE_CLASS_NUMBER) { value ->
                                value.toIntOrNull()?.let { edit(CgmAlarmSettingsEdit.HighDelay(type, it)) }
                            }
                        },
                    )
                    addView(toggle("Schlummern aktiv", rule.snoozeEnabled, palette) { edit(CgmAlarmSettingsEdit.SnoozeEnabled(type, it)) })
                    if (rule.snoozeEnabled) {
                        addView(
                            stepper("Schlummerzeit", "${rule.snoozeMinutes} min", palette, {
                                edit(CgmAlarmSettingsEdit.SnoozeMinutes(type, rule.snoozeMinutes - 5))
                            }, {
                                edit(CgmAlarmSettingsEdit.SnoozeMinutes(type, rule.snoozeMinutes + 5))
                            }),
                        )
                    }
                }
                SugarWearAlarmType.RAPID_RISE -> {
                    val stepText = if (rule.rateStep == 1) "Stufe 1: 2 bis <3 mg/dL/min (↑)" else "Stufe 2: ≥3 mg/dL/min (↑↑)"
                    addView(label("Ratenstufe: $stepText", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        actionButton("Ratenstufe umschalten", palette) {
                            val nextStep = if (rule.rateStep == 1) 2 else 1
                            edit(CgmAlarmSettingsEdit.RateStep(type, nextStep))
                        },
                    )
                    addView(label("Zusatzgrenze: > ${rule.additionalGlucoseThresholdMgDl.toInt()} mg/dL", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                    addView(
                        actionButton("Zusatzgrenze bearbeiten", palette) {
                            showNumberEditor("Zusatzgrenze (steigend)", rule.additionalGlucoseThresholdMgDl.toInt().toString(), InputType.TYPE_CLASS_NUMBER) { value ->
                                value.toDoubleOrNull()?.let { edit(CgmAlarmSettingsEdit.AdditionalGlucoseThreshold(type, it)) }
                            }
                        },
                    )
                }
                SugarWearAlarmType.RAPID_FALL -> {
                    val stepText = if (rule.rateStep == 1) "Stufe 1: 2 bis <3 mg/dL/min (↓)" else "Stufe 2: ≥3 mg/dL/min (↓↓)"
                    addView(label("Ratenstufe: $stepText", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        actionButton("Ratenstufe umschalten", palette) {
                            val nextStep = if (rule.rateStep == 1) 2 else 1
                            edit(CgmAlarmSettingsEdit.RateStep(type, nextStep))
                        },
                    )
                    addView(label("Zusatzgrenze: < ${rule.additionalGlucoseThresholdMgDl.toInt()} mg/dL", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                    addView(
                        actionButton("Zusatzgrenze bearbeiten", palette) {
                            showNumberEditor("Zusatzgrenze (fallend)", rule.additionalGlucoseThresholdMgDl.toInt().toString(), InputType.TYPE_CLASS_NUMBER) { value ->
                                value.toDoubleOrNull()?.let { edit(CgmAlarmSettingsEdit.AdditionalGlucoseThreshold(type, it)) }
                            }
                        },
                    )
                }
                SugarWearAlarmType.SIGNAL_LOSS -> {
                    addView(label("Warnung nach: ${rule.durationMinutes} Min. Signalverlust", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        stepper("Signalausfall-Zeit", "${rule.durationMinutes} min", palette, {
                            edit(CgmAlarmSettingsEdit.DurationMinutes(type, rule.durationMinutes - 5))
                        }, {
                            edit(CgmAlarmSettingsEdit.DurationMinutes(type, rule.durationMinutes + 5))
                        }),
                    )
                }
                SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> {
                    addView(label("Warnung nach: ${rule.durationMinutes} Min. Messproblem", 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        stepper("Problemzeitraum", "${rule.durationMinutes} min", palette, {
                            edit(CgmAlarmSettingsEdit.DurationMinutes(type, rule.durationMinutes - 5))
                        }, {
                            edit(CgmAlarmSettingsEdit.DurationMinutes(type, rule.durationMinutes + 5))
                        }),
                    )
                }
                SugarWearAlarmType.TECHNICAL_WARNING -> {
                    addView(label("Warnt bei unbehebbaren Sensorfehlern oder erforderlicher Benutzerintervention.", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                }
            }

            // Delivery Mode Selection
            addView(label("Ausgabemodus: ${rule.deliveryMode.labelGerman}", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true).apply { setPadding(0, 6.dp, 0, 2.dp) })
            val modes =
                listOf(
                    SugarWearAlertDeliveryMode.MATCH_DEVICE,
                    SugarWearAlertDeliveryMode.SOUND,
                    SugarWearAlertDeliveryMode.VIBRATE,
                )
            modes.forEach { mode ->
                addView(
                    actionButton("${if (rule.deliveryMode == mode) "✓ " else ""}${mode.labelGerman}", palette) {
                        edit(CgmAlarmSettingsEdit.DeliveryMode(type, mode))
                    },
                )
            }

            // Test Button
            addView(
                actionButton("Test-Ton abspielen", palette) {
                    access.test(type, editedProfile())
                },
            )
        }

    private fun edit(edit: CgmAlarmSettingsEdit) {
        access.edit(edit, editedProfile())
        render()
    }

    private fun editedProfile(): SugarWearAlarmProfile =
        access.navigation.value.editedAlarmProfile
            ?: checkNotNull(access.state.value.snapshot).alarms.activeProfile(System.currentTimeMillis()).profile

    private fun alarmProfilesCard(
        settings: SugarWearAlarmSettings,
        active: SugarWearAlarmProfile,
        edited: SugarWearAlarmProfile,
        palette: CgmAppearancePalette,
    ): LinearLayout =
        card(palette).apply {
            val primary = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
            val secondary = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)
            addView(label("Alarmprofile", 14f, primary, true))
            addView(label("Aktiv: ${active.label} · Bearbeiten: ${edited.label}", 11f, secondary))
            addView(
                actionButton("Profil bearbeiten: ${if (edited == SugarWearAlarmProfile.DAY) "Nacht" else "Tag"}", palette) {
                    access.editAlarmProfile(if (edited == SugarWearAlarmProfile.DAY) SugarWearAlarmProfile.NIGHT else SugarWearAlarmProfile.DAY)
                },
            )
            val schedule = settings.schedule
            if (active != edited || settings.scheduleEnabled) {
                addView(actionButton("${edited.label} manuell aktivieren", palette) { edit(CgmAlarmSettingsEdit.ManualProfile(edited)) })
            }
            addView(
                toggle("Zeitplan", settings.scheduleEnabled, palette) { enabled ->
                    if (enabled) {
                        edit(CgmAlarmSettingsEdit.Schedule(schedule))
                    } else {
                        edit(CgmAlarmSettingsEdit.ManualProfile(active))
                    }
                },
            )
            if (settings.scheduleEnabled) {
                addView(actionButton("Tag ab ${timeLabel(schedule.dayStartsMinute)}", palette) { editProfileTime(schedule, SugarWearAlarmProfile.DAY) })
                addView(actionButton("Nacht ab ${timeLabel(schedule.nightStartsMinute)}", palette) { editProfileTime(schedule, SugarWearAlarmProfile.NIGHT) })
            }
        }

    private fun editProfileTime(schedule: SugarWearAlarmSchedule, profile: SugarWearAlarmProfile) {
        val night = profile == SugarWearAlarmProfile.NIGHT
        val minute = if (night) schedule.nightStartsMinute else schedule.dayStartsMinute
        val otherMinute = if (night) schedule.dayStartsMinute else schedule.nightStartsMinute
        val scrollY = scrollView.scrollY
        SharedNumberEditor.showText(
            activity = this,
            title = "${profile.label} ab (HHMM)",
            current = timeLabel(minute).replace(":", ""),
            inputType = InputType.TYPE_CLASS_NUMBER,
            validate = { text ->
                val parsed = profileMinute(text)
                when {
                    parsed == null -> "Uhrzeit als HHMM eingeben"
                    parsed == otherMinute -> "Tag und Nacht brauchen verschiedene Startzeiten"
                    else -> null
                }
            },
            onValue = { text ->
                profileMinute(text)?.let {
                    val updated = if (night) schedule.copy(nightStartsMinute = it) else schedule.copy(dayStartsMinute = it)
                    edit(CgmAlarmSettingsEdit.Schedule(updated))
                }
            },
            onDismiss = { restoreScrollPosition(scrollY) },
        )
    }

    private fun timeLabel(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    private fun profileMinute(text: String): Int? {
        val digits = text.trim()
        if (digits.length !in 3..4 || !digits.all { it in '0'..'9' }) return null
        val time = digits.toInt()
        if (time / 100 > 23 || time % 100 > 59) return null
        return time / 100 * 60 + time % 100
    }

    private fun topBar(palette: CgmAppearancePalette) = cgmSettingsHeader(selectedAlarm?.let(::cgmAlarmTitle) ?: alarmPage ?: "Alarme", palette) { navigateBack() }

    private fun navigateBack() {
        if (selectedAlarm == null && alarmPage == null) {
            finish()
        } else {
            selectedAlarm = null
            alarmPage = null
            scrollView.scrollTo(0, 0)
            render()
        }
    }

    private fun showNumberEditor(
        title: String,
        current: String,
        inputType: Int,
        onValue: (String) -> Unit,
    ) {
        val restoreScrollY = scrollView.scrollY
        SharedNumberEditor.showText(
            activity = this,
            title = title,
            current = current,
            inputType = inputType,
            validate = { null },
            onValue = { text -> onValue(text) },
            onDismiss = { restoreScrollPosition(restoreScrollY) },
        )
    }

    private fun restoreScrollPosition(scrollY: Int) {
        val scheduledScrollY = scrollView.scrollY
        scrollView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                    val maxScroll = (root.measuredHeight - scrollView.height).coerceAtLeast(0)
                    if (scrollView.scrollY == scheduledScrollY) scrollView.scrollTo(0, scrollY.coerceAtMost(maxScroll))
                    return true
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun toggle(
        title: String,
        checked: Boolean,
        palette: CgmAppearancePalette,
        changed: (Boolean) -> Unit,
    ) = app.sugarlicious.uishared.ScrollSafeSwitch(this).apply {
        app.sugarlicious.uishared.WearSettingsUi
            .toggle(this, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        text = title
        isChecked = checked
        setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        textSize = 15f
        app.sugarlicious.uishared.SystemTypography
            .apply(this, true)
        setOnCheckedChangeListener { _, value -> changed(value) }
    }

    private fun stepper(
        title: String,
        value: String,
        palette: CgmAppearancePalette,
        minus: () -> Unit,
        plus: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            label("$title · $value", 8.5f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(actionButton("−", palette, minus), LinearLayout.LayoutParams(42.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(actionButton("+", palette, plus), LinearLayout.LayoutParams(42.dp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = 4.dp })
    }

    private fun actionButton(
        title: String,
        palette: CgmAppearancePalette,
        action: () -> Unit,
    ) = Button(this).apply {
        text = title
        textSize = 14f
        minimumHeight = 40.dp
        app.sugarlicious.uishared.SystemTypography
            .apply(this, true)
        isAllCaps = false
        setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        app.sugarlicious.uishared.WearSettingsUi
            .actionButton(this, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        setOnClickListener { action() }
    }

    private fun card(palette: CgmAppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp, 10.dp, 13.dp, 10.dp)
            background = rounded(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), 19f)
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
            app.sugarlicious.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
        app.sugarlicious.uishared.SystemTypography
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
