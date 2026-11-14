package app.sugarwear

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.AppearanceTerminology
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmWatchActivityLayoutTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    private val activeControllers = mutableListOf<org.robolectric.android.controller.ActivityController<out androidx.activity.ComponentActivity>>()

    @org.junit.After fun destroyComposeActivities() {
        activeControllers.forEach { controller ->
            val lifecycle = controller.get().lifecycle
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) controller.pause()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) controller.stop()
            if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) controller.destroy()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /** Compose needs an attached window and the real IO snapshot before inspecting its views. */
    private fun <T : android.app.Activity> T.awaitUi(): T {
        if (this !is androidx.activity.ComponentActivity) return this
        measureAndLayout(window.decorView)
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (findScrollView(window.decorView) == null && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            measureAndLayout(window.decorView)
            Thread.sleep(2)
        }
        val labels = mutableListOf<String>()
        collectText(window.decorView, labels)
        val dataReady =
            if (this is CgmWatchActivity) {
                androidx.lifecycle
                    .ViewModelProvider(this)[CollectorViewModel::class.java]
                    .state.value.snapshot != null
            } else {
                null
            }
        assertNotNull("Collector UI must load in an attached, resumed window: labels=$labels; dataReady=$dataReady", findScrollView(window.decorView))
        return this
    }

    private fun awaitCondition(activity: android.app.Activity, ready: () -> Boolean) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (!ready() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            measureAndLayout(activity.window.decorView)
            Thread.sleep(2)
        }
        val labels = mutableListOf<String>()
        collectText(activity.window.decorView, labels)
        assertTrue("Expected lifecycle-driven UI action to complete: $labels", ready())
    }

    @Test
    fun `overview keeps pending alarm until acknowledgement and updates immediately`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmSensorStateStore(context).read().copy(collectorEnabled = true))
        CgmAlertPolicyStore.setLocalEnabled(context, true)
        val now = System.currentTimeMillis()
        CgmAlarmCoordinator.onReading(context, CgmReading("layout-alarm", DataSourceId.DEXCOM_G7_WATCH, "layout-sensor", "layout-session", 65.0, now, now), now)
        val activity = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup().visible().get().awaitUi()
        fun hasAlarm(): Boolean {
            val labels = mutableListOf<String>()
            collectText(activity.window.decorView, labels)
            return "Tief Alarm!" in labels
        }
        awaitCondition(activity) { hasAlarm() }
        CgmAlarmCoordinator.acknowledge(context, app.sugarlicious.sensor.protocol.CgmAlarmType.LOW, now)
        awaitCondition(activity) { !hasAlarm() }
    }

    @Test
    @Config(shadows = [PausedOverviewCredentialShadow::class])
    fun `paused overview offers direct resume and preserves sensor and last reading`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val store = CgmSensorStateStore(context)
        val before = store.read().copy(collectorEnabled = false)
        store.save(before)
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .visible()
                .get()
                .awaitUi()
        val root = activity.window.decorView
        val resume = root.findViewWithTag<View>("overview-resume-collector")
        assertEquals(View.VISIBLE, resume.visibility)
        val warning = root.findViewWithTag<ViewGroup>("signal-loss-warning")
        assertEquals(R.drawable.ic_reception_paused, Shadows.shadowOf((warning.getChildAt(0) as ImageView).drawable).createdFromResId)
        resume.performClick()
        awaitCondition(activity) { store.read().collectorEnabled && resume.visibility == View.GONE }
        assertEquals(before.sensor, store.read().sensor)
        assertEquals(before.lastReading, store.read().lastReading)
        assertEquals(CgmUiSensorAction.RESUME, CgmCollectorUiTransitionStore.read(context)?.action)
    }

    @Test
    fun `appearance exposes a complete independent WearOS tile graph section`() {
        val activity =
            Robolectric
                .buildActivity(CgmAppearanceActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        activity.window.decorView
            .findViewWithTag<View>("settings-element-WearOS-Tile · Graph")
            .performClick()
        val text = mutableListOf<String>()
        val tileGraphTitle = findText(activity.window.decorView, "WearOS-Tile · Graph")
        assertNotNull("Tile graph must have its own element card", tileGraphTitle)
        collectText(activity.window.decorView, text)

        assertTrue(text.contains("WearOS-Tile · Graph"))
        assertTrue(text.any { it.startsWith("Punktgröße") })
        assertTrue(text.contains(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE))
        assertFalse(text.contains("Kontur · aktueller Wert"))
        assertTrue(text.any { it.startsWith("Aktuell ·") })
        assertTrue(text.any { it.startsWith("Historisch ·") })
        assertFalse(text.any { it.startsWith("Deckkraft ·") })
        assertTrue(text.contains("Zeitachse"))
        assertTrue(text.any { it.startsWith("Eckenradius") })
        assertTrue(text.any { it.startsWith("Skalenbereich-Deckkraft") })
        assertTrue(text.contains("TILE-GRAPH RESET"))
        CgmAppearanceRole.entries.filter { it.section == CgmAppearanceSection.GRAPH }.forEach { role ->
            assertTrue("missing ${role.label}", text.contains(role.label))
        }
    }

    @Test
    fun `collector updates cannot advance an unstarted pairing flow`() {
        val backgroundPairing = CgmPersistedState(sensor = CgmSensor("old"), collectorEnabled = true)
        assertEquals(CgmPairingScreenStep.ENTER_CODE, advanceCgmPairingScreen(CgmPairingScreenStep.ENTER_CODE, backgroundPairing))
        assertEquals(CgmPairingScreenStep.CONNECTING, advanceCgmPairingScreen(CgmPairingScreenStep.CONNECTING, backgroundPairing))
    }

    @Test
    fun `direct graph scale cycles through every duration and wraps`() {
        assertEquals(2, nextDirectGraphHours(1))
        assertEquals(3, nextDirectGraphHours(2))
        assertEquals(6, nextDirectGraphHours(3))
        assertEquals(12, nextDirectGraphHours(6))
        assertEquals(24, nextDirectGraphHours(12))
        assertEquals(1, nextDirectGraphHours(24))
        assertEquals(1, nextDirectGraphHours(99))
    }

    @Test
    fun `pairing success delay is three seconds`() {
        val startedAt = 1_000_000L
        val deadline = startedAt + 3_000L
        assertEquals(3_000L, pairingSuccessRemainingMs(deadline, startedAt))
        assertEquals(0L, pairingSuccessRemainingMs(deadline, startedAt + 3_000L))
    }

    @Test
    fun `rejected authentication returns to actionable sensor move form`() {
        val now = 1_000_000L
        val rejected =
            CgmPersistedState(
                collectorEnabled = true,
                pairingAttemptId = "pairing",
                pairingDeadlineEpochMs = now + 60_000L,
                lastError =
                    app.sugarlicious.sensor.protocol
                        .CgmCollectorError("G7-AUTH-204", false, now, "rejected"),
            )

        assertFalse(isCgmPairingAttemptActive(rejected, now))
        assertTrue(isCgmPairingAttemptActive(rejected.copy(lastError = null), now))
        assertTrue(isCgmPairingAttemptActive(rejected.copy(lastError = null), now + 60_000L))
        assertFalse(isCgmPairingAttemptActive(rejected.copy(pairingAttemptId = null, lastError = null), now))
    }

    @Before
    fun resetGraphPeriod() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmAppearanceStore(context).setGraphHours(3)
        CgmDirectToWatchSettingsStore(context).saveGraphHours(3)
        val seed =
            CgmReading(
                id = "layout-seed",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "layout-sensor",
                sessionId = "layout-session",
                glucoseMgDl = 120.0,
                timestampEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
                status = CgmReadingStatus.VALID,
            )
        CgmSensorStateStore(context).save(
            CgmSensorStateStore(context).read().copy(
                sensor = CgmSensor("layout-sensor", "layout-session"),
                lastReading = seed,
            ),
        )
    }

    @Test
    fun `ordinary launch never selects pairing from sensor state alone`() {
        listOf(
            CgmPersistedState(),
            CgmPersistedState(sensor = CgmSensor("ended", state = CgmSensorState.ENDED)),
            CgmPersistedState(sensor = CgmSensor("prepared"), collectorEnabled = false),
            CgmPersistedState(sensor = CgmSensor("error", state = CgmSensorState.ERROR), collectorEnabled = true),
        ).forEach { assertNull(initialCgmPairingScreenStep(it)) }
        assertEquals(CgmPairingScreenStep.ENTER_CODE, initialCgmPairingScreenStep(CgmPersistedState(), CgmPairingScreenStep.ENTER_CODE))
    }

    @Test
    fun `association action distinguishes prepared metadata from a connected sensor`() {
        assertFalse(hasCgmSensorAssociation(CgmPersistedState()))
        assertFalse(hasCgmSensorAssociation(CgmPersistedState(sensor = CgmSensor("prepared"), collectorEnabled = false)))
        assertTrue(hasCgmSensorAssociation(CgmPersistedState(sensor = CgmSensor("paired", deviceAddress = "AA:BB:CC:DD:EE:FF"))))
    }

    @Test
    fun `pairing starts explicitly and every page has scroll fallback without field chrome`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNotNull(findScrollView(root))
        assertNotNull(findGraph(root))
        assertNull(findEditor(root))
        assertNull(findText(root, "Verbinden"))
        assertNull(findText(root, "Sensor verbinden"))
        activity.intent = android.content.Intent(context, CgmWatchActivity::class.java).putExtra(CgmWatchActivity.EXTRA_OPEN_PAIRING, true)
        CgmWatchActivity::class.java
            .getDeclaredMethod("onNewIntent", android.content.Intent::class.java)
            .apply { isAccessible = true }
            .invoke(activity, activity.intent)
        val codeRoot = activity.findViewById<android.view.View>(android.R.id.content)
        assertNotNull(findScrollView(codeRoot))
        assertNotNull(findText(codeRoot, "Sensorcode eingeben"))
        assertNull(findText(codeRoot, "Vierstelligen Sensorcode eingeben"))
        assertNotNull(findText(codeRoot, "Verbinden"))
        val editor = findEditor(codeRoot)
        assertNotNull(editor)
        assertTrue(editor?.isFocusable == true)
        assertTrue(editor?.isFocusableInTouchMode == true)
        assertNull(editor?.background)
        activity.finish()
    }

    @Test
    fun `system status has one explicit pairing action and no automatic editor`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        val controller = Robolectric.buildActivity(CgmSystemStatusActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        try {
            val statusModel = androidx.lifecycle.ViewModelProvider(activity)[CgmSystemStatusViewModel::class.java]
            assertSame(activity.application, statusModel.getApplication<android.app.Application>())
            assertNull("Cleared sensor state must remain cleared before rendering", CgmSensorStateStore(activity).read().sensor)
            awaitCondition(activity) { findText(activity.window.decorView, "Sensor verbinden") != null }
            val texts = mutableListOf<String>()
            collectText(activity.window.decorView, texts)
            assertEquals(1, texts.count { it == "Sensor verbinden" })
            assertFalse(texts.contains("Sensor trennen"))
            assertFalse(texts.contains("Fortsetzen"))
            assertNull(findEditor(activity.window.decorView))
            findText(activity.window.decorView, "Sensor verbinden")!!.performClick()
            val target = Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(CgmWatchActivity::class.java.name, target.component?.className)
            assertTrue(target.getBooleanExtra(CgmWatchActivity.EXTRA_OPEN_PAIRING, false))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `overview removes the connected pill and keeps a dedicated outage header above the value card`() {
        val controller = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup()
        try {
            val root =
                controller
                    .get()
                    .awaitUi()
                    .window.decorView
            val texts = mutableListOf<String>()
            collectText(root, texts)
            assertFalse(texts.any { it.contains("VERBUNDEN") || it.contains("SENSOREMPFANG PAUSIERT") })
            val warning = root.findViewWithTag<View>("signal-loss-warning")
            assertNotNull(warning)
            val children = warning.parent as ViewGroup
            assertTrue(children.indexOfChild(warning) < children.indexOfChild(findText(root, "Gewebeglukose")!!.parent as View))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `sensor actions are only available from settings`() {
        val overview = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup()
        val settings = Robolectric.buildActivity(CgmSettingsActivity::class.java).also { activeControllers += it }.setup()
        val sensor = Robolectric.buildActivity(CgmSystemStatusActivity::class.java).also { activeControllers += it }.setup()
        try {
            assertNull(
                findText(
                    overview
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Sensor trennen",
                ),
            )
            assertNull(
                findText(
                    overview
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Pausieren",
                ),
            )
            assertNotNull(
                findText(
                    settings
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Sensor",
                ),
            )
            assertNotNull(
                findText(
                    sensor
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Sensor trennen",
                ),
            )
            assertTrue(
                findText(
                    sensor
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Pausieren",
                ) != null ||
                    findText(
                        sensor
                            .get()
                            .awaitUi()
                            .window.decorView,
                        "Fortsetzen",
                    ) != null,
            )
        } finally {
            overview.pause().stop().destroy()
            settings.pause().stop().destroy()
            sensor.pause().stop().destroy()
        }
    }

    @Test
    fun `explicit code entry survives recreation without starting collection`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
        try {
            assertNotNull(
                findEditor(
                    controller
                        .get()
                        .awaitUi()
                        .window.decorView,
                ),
            )
            assertNotNull(
                findText(
                    controller
                        .get()
                        .awaitUi()
                        .window.decorView,
                    "Verbinden",
                ),
            )
            assertFalse(CgmSensorStateStore(context).read().collectorEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `immediate unlink preserves overview history scroll and guards duplicate taps`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val stateStore = CgmSensorStateStore(context)
        val reading = stateStore.read().lastReading!!
        runBlocking { CgmReadingDatabase(context).use { it.insert(reading) } }
        val beforeHistory = CgmReadingDatabase(context).use { it.query(limit = 300) }
        val controller = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        var calls = 0
        var pending: (() -> Unit)? = null
        activity.prepareUnlinkAccess = { ready, _ -> ready() }
        activity.executeAssociationTask = { pending = it }
        activity.unlinkSensor = {
            calls++
            stateStore.save(CgmPersistedState())
            CgmUnlinkResult(CgmBondRemovalStatus.REMOVED)
        }
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val overview = content.getChildAt(0)
            val scroll = findScrollView(overview)!!
            overview.measure(View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY))
            overview.layout(0, 0, 454, 454)
            scroll.scrollTo(0, 30)
            val oldY = scroll.scrollY
            activity.performSensorUnlink()
            activity.performSensorUnlink()
            assertEquals(0, calls)
            awaitCondition(activity) { pending != null }
            assertNotNull(pending)
            pending!!.invoke()
            awaitCondition(activity) { calls == 1 }
            assertEquals(1, calls)
            assertSame(overview, content.getChildAt(0))
            assertEquals(oldY, scroll.scrollY)
            assertNull(findText(overview, "Sensor verbinden"))
            assertNull(findEditor(overview))
            assertNull(findText(overview, "Sensor trennen"))
            assertEquals(beforeHistory, CgmReadingDatabase(context).use { it.query(limit = 300) })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `failed immediate unlink retains association and offers retry action`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val before = CgmSensorStateStore(context).read()
        val controller = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        activity.prepareUnlinkAccess = { ready, _ -> ready() }
        activity.executeAssociationTask = { it() }
        activity.unlinkSensor = { CgmUnlinkResult(CgmBondRemovalStatus.FAILED) }
        try {
            activity.performSensorUnlink()
            awaitCondition(activity) { findText(activity.window.decorView, "Entkopplung durch Android nicht bestätigt. Sensor bleibt pausiert und kann nicht als vollständig getrennt gelten. Auf Samsung-Uhren sind BLE-Geräte im Bluetooth-Menü oft nicht sichtbar; bitte die Entkopplung hier erneut versuchen. Die Glukosehistorie bleibt erhalten.") != null }
            assertEquals(before.sensor, CgmSensorStateStore(context).read().sensor)
            assertNull(findText(activity.window.decorView, "Sensor trennen"))
            assertNull(findText(activity.window.decorView, "Sensor verbinden"))
            assertNotNull(findText(activity.window.decorView, "Entkopplung erneut versuchen"))
            assertNotNull(findText(activity.window.decorView, "Entkopplung durch Android nicht bestätigt. Sensor bleibt pausiert und kann nicht als vollständig getrennt gelten. Auf Samsung-Uhren sind BLE-Geräte im Bluetooth-Menü oft nicht sichtbar; bitte die Entkopplung hier erneut versuchen. Die Glukosehistorie bleibt erhalten."))
            assertNull(findEditor(activity.window.decorView))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `cancelled companion access never runs unlink or changes the association`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val before = CgmSensorStateStore(context).read().sensor
        val controller = Robolectric.buildActivity(CgmWatchActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        var unlinkCalls = 0
        activity.prepareUnlinkAccess = { _, failed -> failed("Systemfreigabe abgebrochen") }
        activity.unlinkSensor = {
            unlinkCalls++
            CgmUnlinkResult(CgmBondRemovalStatus.REMOVED)
        }
        try {
            activity.performSensorUnlink()
            awaitCondition(activity) { findText(activity.window.decorView, "Systemfreigabe abgebrochen") != null }
            assertEquals(0, unlinkCalls)
            assertEquals(before, CgmSensorStateStore(context).read().sensor)
            assertNull(findText(activity.window.decorView, "Sensor trennen"))
            assertNull(findEditor(activity.window.decorView))
            assertNotNull(findText(activity.window.decorView, "Systemfreigabe abgebrochen"))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `system back leaves explicit code entry on the overview before closing the activity`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
        val activity = controller.get().awaitUi()
        try {
            assertNotNull(findEditor(activity.window.decorView))
            activity.handleSystemBack()
            awaitCondition(activity) { findEditor(activity.window.decorView) == null }
            assertFalse(activity.isFinishing)
            assertNull(findEditor(activity.window.decorView))
            assertNull(findText(activity.window.decorView, "Sensor verbinden"))
            activity.handleSystemBack()
            assertTrue(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `new sensor start does not require companion consent for a stale old bond`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.app.Application>()
        Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        CgmSensorStateStore(context).save(CgmPersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
        val activity = controller.get().awaitUi()
        var starts = 0
        activity.prepareUnlinkAccess = { _, _ -> error("Companion consent must not gate new sensor pairing") }
        activity.executeAssociationTask = { it() }
        activity.connectSensor = { _, code ->
            assertEquals("0123", code)
            starts++
        }
        try {
            findEditor(activity.window.decorView)!!.setText("0123")
            findText(activity.window.decorView, "Verbinden")!!.performClick()
            awaitCondition(activity) { starts == 1 }
            assertEquals(1, starts)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `missing nearby device access cannot start a new sensor`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.app.Application>()
        Shadows.shadowOf(context).denyPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        CgmSensorStateStore(context).save(CgmPersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
        val activity = controller.get().awaitUi()
        var starts = 0
        activity.prepareUnlinkAccess = { _, _ -> error("Denied runtime BLE access must not trigger companion consent") }
        activity.executeAssociationTask = { it() }
        activity.connectSensor = { _, _ -> starts++ }
        try {
            findEditor(activity.window.decorView)!!.setText("0123")
            findText(activity.window.decorView, "Verbinden")!!.performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, starts)
            assertTrue(CgmSystemAccess.missingBluetoothPermissions(activity).isNotEmpty())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `permissions entry opens the dedicated app permissions page`() {
        val controller = Robolectric.buildActivity(CgmSettingsActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        try {
            activity.window.decorView
                .findViewWithTag<View>("settings-category-permissions")
                .performClick()
            assertEquals(
                CgmPermissionsActivity::class.java.name,
                Shadows
                    .shadowOf(activity)
                    .nextStartedActivity.component
                    ?.className,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `only connecting can consume pairing success`() {
        val reading =
            CgmReading(
                id = "success",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor",
                sessionId = "session",
                glucoseMgDl = 120.0,
                timestampEpochMs = 1_000L,
                receivedAtEpochMs = 1_000L,
                status = CgmReadingStatus.VALID,
            )
        val beforeAddress = CgmPersistedState(sensor = CgmSensor("sensor", "session"), lastReading = reading)
        assertEquals(CgmPairingScreenStep.CONNECTING, advanceCgmPairingScreen(CgmPairingScreenStep.CONNECTING, beforeAddress))
        val connected = beforeAddress.copy(sensor = beforeAddress.sensor!!.copy(deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals(CgmPairingScreenStep.ENTER_CODE, advanceCgmPairingScreen(CgmPairingScreenStep.ENTER_CODE, connected))
        assertEquals(CgmPairingScreenStep.CONNECTED, advanceCgmPairingScreen(CgmPairingScreenStep.CONNECTING, connected))
        assertEquals(CgmPairingScreenStep.CONNECTED, advanceCgmPairingScreen(CgmPairingScreenStep.CONNECTED, connected))
    }

    @Test
    fun `connect action is debounced and completion is scheduled once`() {
        assertTrue(canStartCgmPairing(CgmPairingScreenStep.ENTER_CODE, false, "1234"))
        assertFalse(canStartCgmPairing(CgmPairingScreenStep.ENTER_CODE, true, "1234"))
        assertFalse(canStartCgmPairing(CgmPairingScreenStep.CONNECTING, false, "1234"))
        assertFalse(canStartCgmPairing(CgmPairingScreenStep.ENTER_CODE, false, "123"))
        assertTrue(shouldScheduleCgmPairingCompletion(CgmPairingScreenStep.CONNECTED, false))
        assertFalse(shouldScheduleCgmPairingCompletion(CgmPairingScreenStep.CONNECTED, true))
    }

    @Test
    fun `recoverable BLE errors keep pairing active but terminal errors stop it`() {
        val startedAt = 1_000_000L
        val recoverable =
            CgmPersistedState(
                pairingStartedAtEpochMs = startedAt,
                pairingDeadlineEpochMs = startedAt + 60_000L,
                lastError =
                    app.sugarlicious.sensor.protocol
                        .CgmCollectorError("G7-GATT-133", true, startedAt + 1_000L, "temporary"),
            )
        assertFalse(isTerminalCgmPairingFailure(recoverable, startedAt, startedAt + 2_000L))
        assertFalse(
            isTerminalCgmPairingFailure(
                recoverable.copy(
                    lastError =
                        app.sugarlicious.sensor.protocol
                            .CgmCollectorError("G7-AUTH-MISMATCH", true, startedAt + 1_000L, "candidate mismatch"),
                ),
                startedAt,
                startedAt + 2_000L,
            ),
        )
        assertTrue(
            isTerminalCgmPairingFailure(
                recoverable.copy(
                    lastError =
                        app.sugarlicious.sensor.protocol
                            .CgmCollectorError("G7-AUTH-204", false, startedAt + 1_000L, "rejected"),
                ),
                startedAt,
                startedAt + 2_000L,
            ),
        )
        assertFalse(isTerminalCgmPairingFailure(recoverable, startedAt, startedAt + 60_000L))
    }

    @Test
    fun `durable background pairing does not open the wizard without an explicit UI request`() {
        val state =
            CgmPersistedState(
                sensor = CgmSensor("pairing"),
                collectorEnabled = true,
                pairingAttemptId = "pairing-request",
                pairingStartedAtEpochMs = 1_000L,
                pairingDeadlineEpochMs = 2_000L,
                lastError =
                    app.sugarlicious.sensor.protocol
                        .CgmCollectorError("G7-BLE-111", true, 2_000L, "retry"),
            )

        assertNull(initialCgmPairingScreenStep(state))
        assertEquals(CgmPairingScreenStep.CONNECTING, initialCgmPairingScreenStep(state, CgmPairingScreenStep.CONNECTING))
    }

    @Test
    fun `restored connecting screen never animates a paused collector as an ongoing scan`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.CONNECTING.name) }
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
        try {
            val root =
                controller
                    .get()
                    .awaitUi()
                    .window.decorView
            assertNotNull(findText(root, "Sensorsuche pausiert"))
            assertFalse(requireNotNull(findConnectionDots(root)).isAnimating)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `connecting screen has one animated indicator and a cancel action`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val now = System.currentTimeMillis()
        CgmSensorStateStore(context).save(
            CgmPersistedState(
                sensor = CgmSensor("pairing"),
                collectorEnabled = true,
                pairingAttemptId = "pairing-request",
                pairingStartedAtEpochMs = now,
                pairingDeadlineEpochMs = now + 60_000L,
            ),
        )
        val saved = android.os.Bundle().apply { putString("pairing_step", CgmPairingScreenStep.CONNECTING.name) }
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create(saved)
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNotNull(findConnectionDots(root))
        assertNull(findProgressBar(root))
        assertNotNull(findText(root, "Abbrechen"))
        activity.finish()
    }

    @Test
    fun `collector settings contain product sections and retain about`() {
        val activity =
            Robolectric
                .buildActivity(CgmSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        val headers = mutableListOf<android.view.View>()

        fun collect(view: android.view.View) {
            if (view.tag?.toString()?.startsWith("settings-category-") == true) headers += view
            if (view is ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
        }
        collect(root)

        assertNotNull(findText(root, "Sensorempfang"))
        assertEquals(listOf("Anzeige", "Berechtigungen", "Alarme", "Sensor", "Vigil", "Über"), CgmSettingsSection.entries.map { it.title })
        assertEquals(
            CgmSettingsSection.entries.map { "settings-category-${it.name.lowercase()}" },
            headers.map { it.tag.toString() },
        )
        assertTrue(headers.all { it.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt() })
        activity.finish()
    }

    @Test
    fun `system status entry opens system status`() {
        val activity =
            Robolectric
                .buildActivity(CgmSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        root.findViewWithTag<View>("settings-category-sugarwear").performClick()

        assertEquals(
            CgmSystemStatusActivity::class.java.name,
            Shadows
                .shadowOf(activity)
                .nextStartedActivity.component
                ?.className,
        )
        activity.finish()
    }

    @Test
    fun `alarm settings menu opens alarm configuration and not system status`() {
        val activity =
            Robolectric
                .buildActivity(CgmSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        root.findViewWithTag<View>("settings-category-alarms").performClick()

        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(CgmAlarmSettingsActivity::class.java.name, intent.component?.className)
        assertFalse(intent.component?.className == CgmSystemStatusActivity::class.java.name)
        activity.finish()
    }

    @Test
    fun `direct to watch category opens complete watchface settings`() {
        val settings =
            Robolectric
                .buildActivity(CgmSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = settings.findViewById<android.view.View>(android.R.id.content)
        root.findViewWithTag<View>("settings-category-vigil").performClick()
        assertEquals(
            CgmDirectToWatchSettingsActivity::class.java.name,
            Shadows
                .shadowOf(settings)
                .nextStartedActivity.component
                ?.className,
        )

        val activity =
            Robolectric
                .buildActivity(CgmDirectToWatchSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val allTexts = mutableListOf<String>()
        val settingsRoot = activity.findViewById<View>(android.R.id.content)
        assertNotNull(findText(settingsRoot, "Vigil"))

        fun cardText(title: String): List<String> {
            settingsRoot.findViewWithTag<View>("settings-element-$title").performClick()
            val texts = mutableListOf<String>().also { collectText(settingsRoot, it) }
            allTexts += texts
            val back = arrayListOf<View>()
            settingsRoot.findViewsWithText(back, "Zurück", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
            back.single().performClick()
            return texts
        }
        val glucose = cardText("Glukose")
        assertTrue("mg/dL" in glucose)
        assertTrue("Zielbereich" in glucose)
        assertTrue("Zuckerwert fett" in glucose)
        assertTrue(cardText("Alter und Skala").any { it.startsWith("Größe ·") })
        assertTrue("Farbe" in cardText("Uhrzeit"))
        assertTrue("Füllfarbe" in cardText("Trendpfeil"))
        val graph = cardText("Graph")
        assertTrue("LOW-Bereich" in graph)
        assertTrue("HIGH-Bereich" in graph)
        assertTrue("Graphkontur" in graph)
        assertTrue("Zeitachsenskala" in graph)
        assertTrue(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE in graph)
        assertTrue(graph.any { it.startsWith("Aktuell ·") })
        assertTrue(AppearanceTerminology.DELTA_UNIT in cardText("Delta und Einheit"))
        assertFalse("Horizontale Zielwert-Striche" in allTexts)
        assertFalse("Kontur · aktueller Wert" in allTexts)
        assertFalse("Range-Hintergrundfärbung" in allTexts)
        assertFalse(allTexts.any { it.startsWith("Prognose ·") })
        activity.finish()
        settings.finish()
    }

    @Test
    fun `direct to watch keeps independent unit and target range`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences(
                app.sugarlicious.protocol.DirectToWatchSettingsContract.PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            ).edit()
            .clear()
            .commit()
        val store = CgmDirectToWatchSettingsStore(context)
        val thresholds = CgmThresholds(250.0, 168.0, 81.0, 50.0)

        store.saveGlucoseUnit(GlucoseUnit.MMOL_L)
        store.saveGlucoseBold(false)
        store.saveActiveAppearanceMode(AppearanceMode.LIGHT)
        assertTrue(store.saveThresholds(thresholds))

        assertEquals(GlucoseUnit.MMOL_L, store.glucoseUnit())
        assertFalse(store.glucoseBold())
        assertEquals(AppearanceMode.LIGHT, store.activeAppearanceMode(AppearanceMode.DARK))
        assertEquals(thresholds, store.thresholds())
    }

    @Test
    fun `direct settings keep prior fields when several controls update rapidly`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences(
                app.sugarlicious.protocol.DirectToWatchSettingsContract.PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            ).edit()
            .clear()
            .commit()
        val store = CgmDirectToWatchSettingsStore(context)

        store.saveGraphStyle(store.graphStyle().copy(dotRadiusDp = 5f))
        store.saveGraphStyle(store.graphStyle().copy(rangeBackgroundEnabled = false))
        store.saveGraphColors(store.graphColors().copy(graphBackground = 0xFF010203.toInt()))
        store.saveGraphColors(store.graphColors().copy(rangeHigh = 0x44112233))
        store.saveTrendStyle(AppearanceMode.DARK, store.trendStyle(AppearanceMode.DARK).copy(sizePercent = 200))
        store.saveTrendStyle(AppearanceMode.DARK, store.trendStyle(AppearanceMode.DARK).copy(alpha = .4f))

        assertEquals(5f, store.graphStyle().dotRadiusDp)
        assertTrue(store.graphStyle().rangeBackgroundEnabled)
        assertEquals(0xFF010203.toInt(), store.graphColors().graphBackground)
        assertEquals(0x44112233, store.graphColors().rangeHigh)
        assertEquals(200, store.trendStyle(AppearanceMode.DARK).sizePercent)
        assertEquals(.4f, store.trendStyle(AppearanceMode.DARK).alpha)
    }

    @Test
    fun `direct to watch settings preserve the scroll view and position when a choice changes`() {
        val activity =
            Robolectric
                .buildActivity(CgmDirectToWatchSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        root.findViewWithTag<View>("settings-element-Glukose").performClick()
        val scroll = findScrollView(root)!!
        assertFalse(scroll.isVerticalFadingEdgeEnabled)
        assertEquals(0, scroll.verticalFadingEdgeLength)
        measureAndLayout(root)
        scroll.scrollTo(0, 180)

        findText(root, "mmol/L")!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertSame(scroll, findScrollView(activity.findViewById(android.R.id.content)))
        assertEquals(180, scroll.scrollY)
        activity.finish()
    }

    @Test
    fun `direct settings sliders and toggles do not rebuild or reset the page`() {
        val activity =
            Robolectric
                .buildActivity(CgmDirectToWatchSettingsActivity::class.java)
                .also { activeControllers += it }
                .setup()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        root.findViewWithTag<View>("settings-element-Graph").performClick()
        val scroll = findScrollView(root)!!
        measureAndLayout(root)
        scroll.scrollTo(0, 220)

        val sliderRow = findTextStartingWith(root, "Punktgröße ·")!!.parent as ViewGroup
        val seekBar = findSeekBar(sliderRow)!!
        seekBar.setProgress((seekBar.progress + 4).coerceAtMost(seekBar.max), true)
        val toggleRow = findText(root, "Graphkontur")!!.parent as ViewGroup
        val toggle = (0 until toggleRow.childCount).map { toggleRow.getChildAt(it) }.filterIsInstance<Switch>().single()
        toggle.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertSame(scroll, findScrollView(activity.findViewById(android.R.id.content)))
        assertEquals(220, scroll.scrollY)
        activity.finish()
    }

    @Test
    fun `collector overview contains only primary data and action elements`() {
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val texts = mutableListOf<String>()
        collectText(activity.findViewById(android.R.id.content), texts)

        assertFalse(texts.any { it.contains("3h Verlauf", ignoreCase = true) })
        assertTrue(texts.any { it.startsWith("3h") })
        assertFalse(texts.any { it.contains("Watch Direct", ignoreCase = true) })

        assertFalse(texts.contains("Systemstatus"))
        assertFalse(texts.contains("SugarWear"))
        assertFalse(texts.contains("by Sugarlicious"))
        assertFalse(texts.contains("SENSOR"))
        assertFalse(texts.contains("VERBINDUNG"))
        assertFalse(texts.contains("ZEITPLANUNG"))
        assertFalse(texts.contains("DIAGNOSE"))
        assertFalse(texts.any { it == "Sensor einrichten" || it == "Sensor neu verbinden" })
        assertFalse(texts.any { it == "Collector starten" || it == "Collector stoppen" })
        assertFalse(texts.contains("←"))
        val root = activity.findViewById<View>(android.R.id.content)
        val settings = findText(root, "Einstellungen")
        assertTrue(settings is android.widget.Button)
        assertNotNull((settings as android.widget.Button).compoundDrawablesRelative[0])
        assertNull(findImageByDescription(root, "SugarWear"))
        assertTrue(containsNativeButton(root))
        activity.finish()
    }

    @Test
    fun `new reading updates existing graph and preserves scroll position`() {
        val controller =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
        val activity = controller.get().awaitUi()
        val before = findGraph(activity.findViewById(android.R.id.content))
        assertNotNull(before)
        val scroll = findScrollView(activity.findViewById(android.R.id.content))!!
        measureAndLayout(activity.findViewById(android.R.id.content))
        scroll.scrollTo(0, 120)

        val now = System.currentTimeMillis()
        runBlocking {
            val database = CgmReadingDatabase(activity)
            try {
                database.insert(
                    CgmReading(
                        id = "ui-refresh-${System.nanoTime()}",
                        source = DataSourceId.DEXCOM_G7_WATCH,
                        sensorId = "sensor-ui-refresh",
                        sessionId = "session-ui-refresh",
                        glucoseMgDl = 123.0,
                        timestampEpochMs = now,
                        receivedAtEpochMs = now + 1_000L,
                        trend = Trend.FLAT,
                        status = CgmReadingStatus.VALID,
                    ),
                )
            } finally {
                database.close()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val after = findGraph(activity.findViewById(android.R.id.content))
        assertNotNull(after)
        assertSame(before, after)
        assertEquals(120, scroll.scrollY)

        controller.pause().stop().destroy()
    }

    @Test
    fun `overview omits system status pill and native settings action opens settings`() {
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNull(findText(root, "Systemstatus"))

        val settings = findText(root, "Einstellungen")
        assertTrue(settings is android.widget.Button)
        assertNotNull((settings as android.widget.Button).compoundDrawablesRelative[0])
        assertTrue(settings.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
        settings.performClick()
        assertEquals(
            CgmSettingsActivity::class.java.name,
            Shadows
                .shadowOf(activity)
                .nextStartedActivity.component
                ?.className,
        )
        activity.finish()
    }

    private fun findImageByDescription(
        root: android.view.View,
        description: String,
    ): ImageView? {
        if (root is ImageView && root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findImageByDescription(root.getChildAt(index), description)?.let { return it }
            }
        }
        return null
    }

    private fun findProgressBar(root: android.view.View): ProgressBar? {
        if (root is ProgressBar) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) findProgressBar(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun findConnectionDots(root: android.view.View): CgmConnectionDotsView? {
        if (root is CgmConnectionDotsView) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findConnectionDots(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun `collector overview uses canonical drawable trend arrow geometry`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val now = System.currentTimeMillis() + 60_000L
        val reading =
            CgmReading(
                id = "canonical-trend-${System.nanoTime()}",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor-canonical-trend",
                sessionId = "session-canonical-trend",
                glucoseMgDl = 123.0,
                timestampEpochMs = now,
                receivedAtEpochMs = now,
                trend = Trend.FORTY_FIVE_UP,
                status = CgmReadingStatus.VALID,
            )
        runBlocking {
            CgmReadingDatabase(context).let { database ->
                try {
                    database.insert(reading)
                } finally {
                    database.close()
                }
            }
        }
        CgmSensorStateStore(context).save(
            CgmSensorStateStore(context).read().copy(
                sensor = CgmSensor(reading.sensorId, reading.sessionId),
                collectorEnabled = true,
                lastReading = reading,
            ),
        )
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val arrows = mutableListOf<ImageView>()
        collectTrendArrows(activity.findViewById(android.R.id.content), arrows)

        assertEquals(1, arrows.size)
        val arrow = arrows.single()
        val arrowContainer = arrow.parent as android.widget.FrameLayout
        assertEquals(1f, CGM_TREND_OPTICAL_OFFSET_DP)
        val opticalOffsetPx = (CGM_TREND_OPTICAL_OFFSET_DP * activity.resources.displayMetrics.density).roundToInt()
        assertEquals(0f, arrow.rotation)
        assertEquals(0f, arrow.translationY)
        assertEquals(arrow.layoutParams.height + 2 * opticalOffsetPx, arrowContainer.layoutParams.height)
        assertEquals(2 * opticalOffsetPx, (arrow.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin)
        assertTrue(
            arrow
                .contentDescription
                .toString()
                .contains("FORTY_FIVE_UP"),
        )
        activity.finish()
    }

    @Test
    fun `system status separates operational controls from preserved technical detail pages`() {
        val controller = Robolectric.buildActivity(CgmSystemStatusActivity::class.java).also { activeControllers += it }.setup()
        val activity = controller.get().awaitUi()
        val before = CgmSensorStateStore(activity).read()
        try {
            val texts = mutableListOf<String>()
            collectText(activity.window.decorView, texts)
            assertTrue(texts.contains("Systemstatus"))
            assertFalse(texts.contains("GATT verbunden"))
            assertFalse(texts.contains("Sensorcode"))
            assertTrue(texts.any { it == "Sensor verbinden" || it == "Sensor trennen" })
            val pages =
                mapOf(
                    "Sensor" to listOf("Sensorcode", "GTIN", "Seriennummer", "Sensorstart · berechnet", "Sensorende · berechnet", "Kulanzende · berechnet"),
                    "Verbindung" to listOf("Reconnect-Strategie", "Empfohlene Aktion"),
                    "Zeitplanung und Rechte" to listOf("Nächster Reconnect", "Geräte in der Nähe"),
                    "LIVE COLLECTOR STATUS" to listOf("Aktueller Attempt", "FGS"),
                    "Hardwaretest" to listOf("GATT verbunden", "Erwartete Fenster"),
                    "Diagnose" to listOf("Aktiver Attempt", "Fehlercode"),
                )
            pages.forEach { (key, fields) ->
                activity.window.decorView
                    .findViewWithTag<View>("status-details-$key")
                    .performClick()
                texts.clear()
                collectText(activity.window.decorView, texts)
                fields.forEach { assertTrue("Missing $key field $it", texts.contains(it)) }
                val backs = arrayListOf<View>()
                activity.window.decorView.findViewsWithText(backs, "Zurück", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
                backs.single().performClick()
                assertFalse(activity.isFinishing)
            }
            assertEquals(before, CgmSensorStateStore(activity).read())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `period change updates only graph state and preserves screen and scroll`() {
        val activity =
            Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .also { activeControllers += it }
                .also { activeControllers += it }
                .create()
                .start()
                .resume()
                .visible()
                .get()
                .awaitUi()
        val rootBefore = activity.findViewById<android.view.View>(android.R.id.content).let { (it as ViewGroup).getChildAt(0) }
        val graphBefore = findGraph(rootBefore)!!
        val scroll = findScrollView(rootBefore)!!
        measureAndLayout(activity.findViewById(android.R.id.content))
        scroll.scrollTo(0, 120)

        findTextStartingWith(rootBefore, "3h")!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val rootAfter = activity.findViewById<android.view.View>(android.R.id.content).let { (it as ViewGroup).getChildAt(0) }
        assertSame(rootBefore, rootAfter)
        assertSame(graphBefore, findGraph(rootAfter))
        assertNotNull(findTextStartingWith(rootAfter, "6h"))
        assertEquals(120, scroll.scrollY)
        activity.finish()
    }

    private fun collectText(
        view: android.view.View,
        output: MutableList<String>,
    ) {
        if (view is TextView) output += view.text?.toString().orEmpty()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectText(view.getChildAt(index), output)
        }
    }

    private fun findSeekBar(view: View): SeekBar? {
        if (view is SeekBar) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findSeekBar(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun findGraph(view: android.view.View): CgmCollectorGraphView? {
        if (view is CgmCollectorGraphView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findGraph(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun findScrollView(view: android.view.View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findScrollView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun findText(
        view: android.view.View,
        value: String,
    ): TextView? {
        if (view is TextView && view.text?.toString() == value) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findText(view.getChildAt(index), value)?.let { return it }
        return null
    }

    private fun findEditor(view: android.view.View): android.widget.EditText? {
        if (view is android.widget.EditText) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findEditor(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun collectTrendArrows(
        view: android.view.View,
        output: MutableList<ImageView>,
    ) {
        if (view is ImageView && view.contentDescription?.toString()?.startsWith("Trend ") == true) output += view
        if (view is ViewGroup) for (index in 0 until view.childCount) collectTrendArrows(view.getChildAt(index), output)
    }

    private fun findTextStartingWith(
        view: android.view.View,
        value: String,
    ): TextView? {
        if (view is TextView && view.text?.toString()?.startsWith(value) == true) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findTextStartingWith(view.getChildAt(index), value)?.let { return it }
        return null
    }

    private fun measureAndLayout(view: android.view.View) {
        view.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 400, 400)
    }

    private fun containsNativeButton(view: android.view.View): Boolean {
        if (view is Button) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsNativeButton(view.getChildAt(index))) return true
            }
        }
        return false
    }
}
