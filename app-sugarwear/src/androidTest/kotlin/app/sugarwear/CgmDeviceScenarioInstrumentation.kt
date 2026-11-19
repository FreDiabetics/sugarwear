package app.sugarwear

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Device-only presentation fixtures: never persist CGM samples, credentials or alarm episodes. */
class CgmDeviceScenarioInstrumentation : Instrumentation() {
    private val results = mutableListOf<String>()
    private lateinit var output: File
    private lateinit var activity: CgmWatchActivity
    private var runAudioTests = false
    private var pairingHeight: Int? = null
    private val now get() = System.currentTimeMillis()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runAudioTests = arguments?.getString("audio") == "true"
        start()
    }

    override fun onStart() {
        output = File(targetContext.filesDir, if (runAudioTests) "device-scenarios" else "device-scenarios-visual").apply { mkdirs() }
        val before = CgmSensorStateStore(targetContext).read()
        val manager = targetContext.getSystemService(NotificationManager::class.java)
        val idMethod = CgmAlarmNotifier::class.java.getDeclaredMethod("notificationId", SugarWearAlarmType::class.java).apply { isAccessible = true }
        SugarWearAlarmType.entries.filterNot { CgmAlarmCoordinator.isVisible(targetContext, it) }.forEach {
            manager.cancel(idMethod.invoke(CgmAlarmNotifier, it) as Int)
        }
        try {
            activity = startActivitySync(Intent(targetContext, CgmWatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CgmWatchActivity
            waitForIdleSync()
            Thread.sleep(700)
            val sensor = CgmSensor("device-fixture", "device-session", deviceAddress = "AA:BB:CC:DD:EE:FF", state = CgmSensorState.ACTIVE)

            fun reading(value: Double = 120.0, trend: Trend = Trend.FLAT, age: Long = 0L) = CgmReading("device-fixture", DataSourceId.DEXCOM_G7_WATCH, sensor.sensorId, sensor.sessionId!!, value, now - age, now - age, trend = trend)
            val base = CgmPersistedState(sensor = sensor, collectorEnabled = true, lastReading = reading(), authenticationState = CgmAuthenticationState.AUTHENTICATED)

            fun snapshot(state: CgmPersistedState = base, permission: Boolean = true, bluetooth: Boolean = true, transition: CgmUiSensorTransition? = null, alarms: List<SugarWearAlarmInstance> = emptyList()) = CgmOverviewSnapshot(state, state.sensor != null, emptyList(), transition, pendingAlarms = alarms, bluetoothPermission = permission, bluetoothEnabled = bluetooth, lastValidReading = state.lastReading?.takeIf { it.status == CgmReadingStatus.VALID }, pairingCode = "0123")
            captureOverview("live", snapshot())
            captureOverview("no-sensor", snapshot(CgmPersistedState()))
            captureOverview("paused", snapshot(base.copy(collectorEnabled = false), transition = CgmUiSensorTransition(CgmUiSensorAction.PAUSE, now, sensor.sensorId, sensor.sessionId)))
            captureOverview("bluetooth-off", snapshot(bluetooth = false))
            captureOverview("permission-missing", snapshot(permission = false))
            captureOverview("sensor-error", snapshot(base.copy(sensor = sensor.copy(state = CgmSensorState.ERROR), lastReading = reading().copy(status = CgmReadingStatus.SENSOR_ERROR))))
            captureOverview("signal-loss", snapshot(base.copy(lastReading = reading(age = 25 * 60_000L))))
            captureOverview("resume", snapshot(base.copy(lastReading = reading(age = 5 * 60_000L), protocolState = CgmProtocolState.SCANNING), transition = CgmUiSensorTransition(CgmUiSensorAction.RESUME, now - 1_000L, sensor.sensorId, sensor.sessionId)))
            captureOverview("waiting-first-value", snapshot(base.copy(lastReading = null, protocolState = CgmProtocolState.AUTHENTICATED, lastSuccessfulConnectionEpochMs = now), transition = CgmUiSensorTransition(CgmUiSensorAction.PAIR, now - 1_000L, sensor.sensorId, sensor.sessionId, connectedAt = now)))
            captureOverview("pairing-code", snapshot(CgmPersistedState()), CgmPairingUiState(step = CgmPairingScreenStep.ENTER_CODE, draft = "0123"))
            captureOverview("pairing-connecting", snapshot(base.copy(lastReading = null, protocolState = CgmProtocolState.SCANNING)), CgmPairingUiState(step = CgmPairingScreenStep.CONNECTING, draft = "0123", startedAt = now - 1_000L))
            captureOverview("pairing-error", snapshot(base.copy(lastReading = null)), CgmPairingUiState(step = CgmPairingScreenStep.ENTER_CODE, failureMessage = "Test: Sensor nicht gefunden"))
            captureOverview("pairing-success", snapshot(base), CgmPairingUiState(step = CgmPairingScreenStep.CONNECTED))
            captureOverview("unlink-failed", snapshot(base), CgmPairingUiState(unlinkFailed = true, unlinkFailureReason = "Test: Bond-Removal nicht bestätigt"))
            captureOverview("unlink-confirmed", snapshot(CgmPersistedState()), CgmPairingUiState(offerStop = true))
            for (type in SugarWearAlarmType.entries) {
                val trend =
                    when (type) {
                        SugarWearAlarmType.RAPID_RISE -> Trend.DOUBLE_UP
                        SugarWearAlarmType.RAPID_FALL -> Trend.DOUBLE_DOWN
                        SugarWearAlarmType.LOW, SugarWearAlarmType.URGENT_LOW, SugarWearAlarmType.URGENT_LOW_SOON -> Trend.SINGLE_DOWN
                        else -> Trend.SINGLE_UP
                    }
                val value =
                    when (type) {
                        SugarWearAlarmType.LOW, SugarWearAlarmType.URGENT_LOW -> 65.0
                        SugarWearAlarmType.URGENT_LOW_SOON -> 90.0
                        else -> 202.0
                    }
                val sample = reading(value, trend)
                val alarm = SugarWearAlarmInstance(type, SugarWearAlarmState.ACTIVE, sensor.sensorId, sensor.sessionId, triggeredAtEpochMs = now, conditionFirstMetAtEpochMs = now)
                captureOverview("alarm-${type.id}", snapshot(base.copy(lastReading = sample), alarms = listOf(alarm)))
            }
            for (artwork in CgmAlarmArtwork.entries) {
                onUi {
                    applyCgmAlarmFullBleedChrome(activity.window)
                    val value =
                        when (artwork) {
                            CgmAlarmArtwork.LOW -> 65.0
                            CgmAlarmArtwork.LOW_SOON -> 90.0
                            else -> 202.0
                        }
                    val appearance = CgmAppearanceStore(targetContext)
                    val palette = appearance.load()
                    val role =
                        when (CgmGraphColorStore(targetContext).readThresholds().classify(value)) {
                            app.sugarlicious.model.CgmRangeClass.VERY_HIGH, app.sugarlicious.model.CgmRangeClass.HIGH -> CgmAppearanceRole.GLUCOSE_HIGH
                            app.sugarlicious.model.CgmRangeClass.VERY_LOW, app.sugarlicious.model.CgmRangeClass.LOW -> CgmAppearanceRole.GLUCOSE_LOW
                            app.sugarlicious.model.CgmRangeClass.IN_RANGE -> CgmAppearanceRole.GLUCOSE_IN_RANGE
                            null -> CgmAppearanceRole.GLUCOSE_NO_SOURCE
                        }
                    val view =
                        CgmAlarmFullBleedView(
                            activity,
                            artwork,
                            reading(
                                value,
                                when (artwork) {
                                    CgmAlarmArtwork.RAPID_FALL -> Trend.DOUBLE_DOWN
                                    CgmAlarmArtwork.RAPID_RISE -> Trend.DOUBLE_UP
                                    CgmAlarmArtwork.LOW, CgmAlarmArtwork.LOW_SOON -> Trend.SINGLE_DOWN
                                    else -> Trend.SINGLE_UP
                                },
                            ),
                            glucoseColor = palette.argb(role),
                            trendStyle = appearance.trendArrowStyle().renderSpec(),
                        )
                    method("showScreen", View::class.java).invoke(activity, view)
                }
                capture("full-${artwork.name.lowercase()}")
            }
            onUi {
                val appearance = CgmAppearanceStore(targetContext)
                method("showScreen", View::class.java).invoke(activity, CgmAlarmFullBleedView(activity, CgmAlarmArtwork.HIGH, reading(202.0, Trend.DOUBLE_UP), glucoseColor = appearance.load().argb(CgmAppearanceRole.GLUCOSE_HIGH), trendStyle = appearance.trendArrowStyle().renderSpec()))
            }
            capture("full-high-double-trend")
            uiAutomation.executeShellCommand("input keyevent 3").close()
            Thread.sleep(500)
            // Dedicated test notification IDs and the existing bounded settings test path.
            for (type in SugarWearAlarmType.entries) {
                if (!runAudioTests) {
                    results += "SKIP sound ${type.id}: silent visual run"
                    continue
                }
                if (CgmAlarmCoordinator.isActive(targetContext, type)) {
                    results += "SKIP audio ${type.id}: real episode active"
                    continue
                }
                onUi { CgmAlarmNotifier.showTest(targetContext, type, CgmAlarmSettingsStore.read(targetContext)) }
                Thread.sleep(1_200)
                val manager = targetContext.getSystemService(NotificationManager::class.java)
                check(manager.activeNotifications.any { it.id == CgmAlarmNotifier.testNotificationId(type) }) { "Test notification missing: $type" }
                capture("notification-${type.id}")
                results += "PASS notification ${type.id}; audioPlaying=${CgmAlarmAudioService.isPlaying()}"
                if (!CgmAlarmCoordinator.isActive(targetContext, type)) onUi { CgmAlarmAudioService.stop(targetContext, type) }
                manager.cancel(CgmAlarmNotifier.testNotificationId(type))
            }
            if (!runAudioTests) {
                val manager = targetContext.getSystemService(NotificationManager::class.java)
                val idMethod = CgmAlarmNotifier::class.java.getDeclaredMethod("notificationId", SugarWearAlarmType::class.java).apply { isAccessible = true }
                for (type in SugarWearAlarmType.entries) {
                    val id = idMethod.invoke(CgmAlarmNotifier, type) as Int
                    if (manager.activeNotifications.any { it.id == id } || CgmAlarmCoordinator.isActive(targetContext, type)) {
                        results += "SKIP silent notification ${type.id}: real notice present"
                        continue
                    }
                    uiAutomation.executeShellCommand("input keyevent 3").close()
                    Thread.sleep(400)
                    val settings = CgmAlarmSettingsStore.read(targetContext)
                    val channel = manager.getNotificationChannel(CgmAlarmNotifier.channelId(targetContext, type, settings))
                    if (channel?.sound != null || channel?.shouldVibrate() == true) {
                        results += "SKIP silent notification ${type.id}: user channel may make sound or vibrate"
                        continue
                    }
                    val audioWasPlaying = CgmAlarmAudioService.isPlaying()
                    val sampleTrend =
                        when (type) {
                            SugarWearAlarmType.RAPID_RISE -> Trend.DOUBLE_UP
                            SugarWearAlarmType.RAPID_FALL -> Trend.DOUBLE_DOWN
                            SugarWearAlarmType.LOW, SugarWearAlarmType.URGENT_LOW, SugarWearAlarmType.URGENT_LOW_SOON -> Trend.SINGLE_DOWN
                            else -> Trend.SINGLE_UP
                        }
                    val sampleValue =
                        when (type) {
                            SugarWearAlarmType.LOW, SugarWearAlarmType.URGENT_LOW -> 65.0
                            SugarWearAlarmType.URGENT_LOW_SOON -> 90.0
                            else -> 202.0
                        }
                    val sample =
                        reading(sampleValue, sampleTrend, if (type == SugarWearAlarmType.SIGNAL_LOSS) 25 * 60_000L else 0L).let {
                            if (type == SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM) it.copy(status = CgmReadingStatus.SENSOR_ERROR) else it
                        }
                    val preview = SugarWearAlarmInstance(type, SugarWearAlarmState.ACTIVE, sensor.sensorId, sensor.sessionId, triggeredAtEpochMs = now, conditionFirstMetAtEpochMs = now)
                    onUi { CgmAlarmNotifier.show(targetContext, preview, settings, onlyAlertOnce = true, reading = sample) }
                    val deadline = System.currentTimeMillis() + 5_000L
                    var posted = manager.activeNotifications.firstOrNull { it.id == id }?.notification
                    while (posted == null && System.currentTimeMillis() < deadline) {
                        Thread.sleep(50)
                        posted = manager.activeNotifications.firstOrNull { it.id == id }?.notification
                    }
                    val delivered = posted ?: error("Notification was not published: $type")
                    check(delivered.smallIcon.resId == R.drawable.ic_cgm_notification && delivered.getLargeIcon() == null) { "Alarm changed the fixed notification icon: $type" }
                    val previewId = CgmAlarmNotifier.testNotificationId(type)
                    onUi {
                        val marked =
                            android.app.Notification.Builder
                                .recoverBuilder(targetContext, delivered)
                                .setContentTitle("[TEST] ${cgmAlarmHeadline(type)}")
                                .setActions()
                                .setSilent(true)
                                .build()
                        manager.notify(previewId, marked)
                    }
                    if (!CgmAlarmCoordinator.isActive(targetContext, type)) manager.cancel(id)
                    Thread.sleep(700)
                    check(manager.activeNotifications.any { it.id == previewId }) { "Silent production notification missing: $type" }
                    if (!audioWasPlaying) check(!CgmAlarmAudioService.isPlaying()) { "Silent preview started playback" }
                    capture("silent-notification-${type.id}-headsup")
                    uiAutomation.executeShellCommand("input swipe 24 240 450 240 350").close()
                    Thread.sleep(600)
                    capture("silent-notification-${type.id}-card")
                    manager.cancel(previewId)
                    results += "PASS silent production notification ${type.id}; no playback requested"
                }
            }
            val after = CgmSensorStateStore(targetContext).read()
            check(before.sensor?.sensorId == after.sensor?.sensorId && before.sensor?.sessionId == after.sensor?.sessionId && before.collectorEnabled == after.collectorEnabled) { "Sensor association or reception setting changed" }
            results += "PASS real sensor association and collector setting preserved"
            onUi { activity.finish() }
            File(output, "results.txt").writeText(results.joinToString("\n"))
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "PASS ${results.size} device checks; files/device-scenarios") })
        } catch (error: Exception) {
            if (::activity.isInitialized) onUi { activity.finish() }
            File(output, "results.txt").writeText(results.joinToString("\n") + "\nFAIL " + error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", error.toString()) })
        }
    }

    private fun onUi(action: () -> Unit) {
        var failure: Exception? = null
        super.runOnMainSync {
            try {
                action()
            } catch (error: Exception) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun field(name: String) = CgmWatchActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun method(name: String, vararg parameters: Class<*>) = CgmWatchActivity::class.java.getDeclaredMethod(name, *parameters).apply { isAccessible = true }

    private fun captureOverview(name: String, snapshot: CgmOverviewSnapshot, pairing: CgmPairingUiState = CgmPairingUiState()) {
        onUi {
            field("overviewResumed").setBoolean(activity, false)
            val model = ViewModelProvider(activity)[SensorConnectionViewModel::class.java]
            val stateField = SensorConnectionViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val state = stateField.get(model) as MutableStateFlow<CgmPairingUiState>
            state.value = pairing
            field("overviewState").set(activity, CgmOverviewState(snapshot, now))
            field("screenBuilt").setBoolean(activity, false)
            method("refreshScreen").invoke(activity)

            fun isolateActions(view: View) {
                if (view !is EditText && view.hasOnClickListeners()) view.setOnClickListener { /* Presentation fixture: no real sensor commands. */ }
                if (view is ViewGroup) for (index in 0 until view.childCount) isolateActions(view.getChildAt(index))
            }
            isolateActions(activity.window.decorView)
            val host = activity.window.decorView.findViewWithTag<ViewGroup>("glucose-host")
            check(host != null && host.childCount == 2) { "Expected one glucose heading and one tile: $name" }
            if (pairing.step == CgmPairingScreenStep.ENTER_CODE) {
                check(findEditor(host) != null) { "Inline editor missing" }
            }
        }
        capture(name)
        onUi {
            if (pairing.step != null) {
                val card = activity.window.decorView.findViewWithTag<View>("glucose-tile-pairing")
                pairingHeight?.let { check(card.height == it) { "Pairing tile changed height: $name" } }
                pairingHeight = card.height
            }
            if (name == "resume" || name == "pairing-connecting") {
                val code = activity.window.decorView.findViewWithTag<TextView>("connection-sensor-code")
                check(code?.text?.toString() == "0123") { "Submitted sensor code missing below progress: $name" }
            }

            fun scroll(view: View): ScrollView? {
                if (view is ScrollView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) scroll(view.getChildAt(index))?.let { return it }
                return null
            }
            val host = activity.window.decorView.findViewWithTag<ViewGroup>("glucose-host")
            val container = scroll(activity.window.decorView)
            if (container != null && host != null) container.scrollTo(0, (host.bottom - container.height + 28 * activity.resources.displayMetrics.density).toInt().coerceAtLeast(0))
        }
        capture("$name-bottom")
        if (name == "pairing-code") {
            val tap = IntArray(2)
            onUi {
                val editor = findEditor(activity.window.decorView)!!
                editor.getLocationOnScreen(tap)
                tap[0] += editor.width / 2
                tap[1] += editor.height / 2
            }
            uiAutomation.executeShellCommand("input tap ${tap[0]} ${tap[1]}").close()
            Thread.sleep(500)
            onUi {
                val editor = findEditor(activity.window.decorView)!!
                editor.requestFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(700)
            capture("pairing-code-keyboard")
            onUi {
                val editor = findEditor(activity.window.decorView)!!
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
                editor.clearFocus()
                check(editor.text.toString() == "0123") { "Keyboard return lost pairing draft" }
            }
            Thread.sleep(350)
        }
    }

    private fun findEditor(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findEditor(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun capture(name: String) {
        waitForIdleSync()
        Thread.sleep(250)
        onUi {
            fun validate(view: View) {
                if (view is TextView && view.parent is CgmAlarmFullBleedView) {
                    val layout = view.layout ?: error("Native alarm text has no layout")
                    check(layout.getLineEnd(0) == view.text.length) { "Truncated native alarm text: $name" }
                    check(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom) { "Vertically clipped native alarm text: $name" }
                }
                if (view is ViewGroup) for (index in 0 until view.childCount) validate(view.getChildAt(index))
            }
            validate(activity.window.decorView)
        }
        val bitmap = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable: $name")
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        results += "PASS screenshot $name ${bitmap.width}x${bitmap.height}"
    }
}
