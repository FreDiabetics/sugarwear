package app.aapswear.g7watch

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
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SensorState
import app.aapswear.model.AppearanceMode
import app.aapswear.model.AppearanceTerminology
import app.aapswear.model.CgmThresholds
import app.aapswear.model.DataSourceId
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.Trend
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
class G7WatchActivityLayoutTest {
    @Test
    fun `appearance exposes a complete independent WearOS tile graph section`() {
        val activity = Robolectric.buildActivity(G7AppearanceActivity::class.java).setup().get()
        val text = mutableListOf<String>()
        val tileGraphTitle = findText(activity.window.decorView, "WearOS-Tile · Graph")
        assertNotNull("Tile graph must have its own element card", tileGraphTitle)
        collectText(tileGraphTitle!!.parent as android.view.View, text)

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
        G7AppearanceRole.entries.filter { it.section == G7AppearanceSection.GRAPH }.forEach { role ->
            assertTrue("missing ${role.label}", text.contains(role.label))
        }
    }

    @Test
    fun `collector updates cannot advance an unstarted pairing flow`() {
        val backgroundPairing = G7PersistedState(sensor = G7Sensor("old"), collectorEnabled = true)
        assertEquals(G7PairingScreenStep.ENTER_CODE, advanceG7PairingScreen(G7PairingScreenStep.ENTER_CODE, backgroundPairing))
        assertEquals(G7PairingScreenStep.CONNECTING, advanceG7PairingScreen(G7PairingScreenStep.CONNECTING, backgroundPairing))
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
            G7PersistedState(
                collectorEnabled = true,
                pairingAttemptId = "pairing",
                pairingDeadlineEpochMs = now + 60_000L,
                lastError = app.aapswear.g7.G7CollectorError("G7-AUTH-204", false, now, "rejected"),
            )

        assertFalse(isG7PairingAttemptActive(rejected, now))
        assertTrue(isG7PairingAttemptActive(rejected.copy(lastError = null), now))
        assertTrue(isG7PairingAttemptActive(rejected.copy(lastError = null), now + 60_000L))
        assertFalse(isG7PairingAttemptActive(rejected.copy(pairingAttemptId = null, lastError = null), now))
    }

    @Before
    fun resetGraphPeriod() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        G7AppearanceStore(context).setGraphHours(3)
        G7DirectToWatchSettingsStore(context).saveGraphHours(3)
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
        G7SensorStateStore(context).save(
            G7SensorStateStore(context).read().copy(
                sensor = G7Sensor("layout-sensor", "layout-session"),
                lastReading = seed,
            ),
        )
    }

    @Test
    fun `ordinary launch never selects pairing from sensor state alone`() {
        listOf(
            G7PersistedState(),
            G7PersistedState(sensor = G7Sensor("ended", state = G7SensorState.ENDED)),
            G7PersistedState(sensor = G7Sensor("prepared"), collectorEnabled = false),
            G7PersistedState(sensor = G7Sensor("error", state = G7SensorState.ERROR), collectorEnabled = true),
        ).forEach { assertNull(initialG7PairingScreenStep(it)) }
        assertEquals(G7PairingScreenStep.ENTER_CODE, initialG7PairingScreenStep(G7PersistedState(), G7PairingScreenStep.ENTER_CODE))
    }

    @Test
    fun `association action distinguishes prepared metadata from a connected sensor`() {
        assertFalse(hasG7SensorAssociation(G7PersistedState()))
        assertFalse(hasG7SensorAssociation(G7PersistedState(sensor = G7Sensor("prepared"), collectorEnabled = false)))
        assertTrue(hasG7SensorAssociation(G7PersistedState(sensor = G7Sensor("paired", deviceAddress = "AA:BB:CC:DD:EE:FF"))))
    }

    @Test
    fun `pairing starts explicitly and every page has scroll fallback without field chrome`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        G7SensorStateStore(context).save(G7PersistedState())
        val activity =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNotNull(findScrollView(root))
        assertNotNull(findGraph(root))
        assertNull(findEditor(root))
        assertNull(findText(root, "Verbinden"))
        assertNull(findText(root, "Sensor verbinden"))
        activity.intent = android.content.Intent(context, G7WatchActivity::class.java).putExtra(G7WatchActivity.EXTRA_OPEN_PAIRING, true)
        G7WatchActivity::class.java
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
        G7SensorStateStore(context).save(G7PersistedState())
        val controller = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup()
        val activity = controller.get()
        try {
            val texts = mutableListOf<String>()
            collectText(activity.window.decorView, texts)
            assertEquals(1, texts.count { it == "Sensor verbinden" })
            assertFalse(texts.contains("Sensor trennen"))
            assertFalse(texts.contains("Fortsetzen"))
            assertNull(findEditor(activity.window.decorView))
            findText(activity.window.decorView, "Sensor verbinden")!!.performClick()
            val target = Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(G7WatchActivity::class.java.name, target.component?.className)
            assertTrue(target.getBooleanExtra(G7WatchActivity.EXTRA_OPEN_PAIRING, false))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `overview removes the connected pill and keeps a dedicated outage header above the value card`() {
        val controller = Robolectric.buildActivity(G7WatchActivity::class.java).setup()
        try {
            val root = controller.get().window.decorView
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
        val overview = Robolectric.buildActivity(G7WatchActivity::class.java).setup()
        val settings = Robolectric.buildActivity(G7SettingsActivity::class.java).setup()
        val sensor = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup()
        try {
            assertNull(findText(overview.get().window.decorView, "Sensor trennen"))
            assertNull(findText(overview.get().window.decorView, "Pausieren"))
            assertNotNull(findText(settings.get().window.decorView, "Sensor"))
            assertNotNull(findText(sensor.get().window.decorView, "Sensor trennen"))
            assertTrue(findText(sensor.get().window.decorView, "Pausieren") != null || findText(sensor.get().window.decorView, "Fortsetzen") != null)
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
        G7SensorStateStore(context).save(G7PersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
        try {
            assertNotNull(findEditor(controller.get().window.decorView))
            assertNotNull(findText(controller.get().window.decorView, "Verbinden"))
            assertFalse(G7SensorStateStore(context).read().collectorEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `immediate unlink preserves overview history scroll and guards duplicate taps`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val stateStore = G7SensorStateStore(context)
        val reading = stateStore.read().lastReading!!
        runBlocking { G7ReadingDatabase(context).use { it.insert(reading) } }
        val beforeHistory = G7ReadingDatabase(context).use { it.query(limit = 300) }
        val controller = Robolectric.buildActivity(G7WatchActivity::class.java).setup()
        val activity = controller.get()
        var calls = 0
        var pending: (() -> Unit)? = null
        activity.prepareUnlinkAccess = { ready, _ -> ready() }
        activity.executeAssociationTask = { pending = it }
        activity.unlinkSensor = {
            calls++
            stateStore.save(G7PersistedState())
            G7UnlinkResult(G7BondRemovalStatus.REMOVED)
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
            assertNotNull(pending)
            pending!!.invoke()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, calls)
            assertSame(overview, content.getChildAt(0))
            assertEquals(oldY, scroll.scrollY)
            assertNull(findText(overview, "Sensor verbinden"))
            assertNull(findEditor(overview))
            assertNull(findText(overview, "Sensor trennen"))
            assertEquals(beforeHistory, G7ReadingDatabase(context).use { it.query(limit = 300) })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `failed immediate unlink retains association and offers native cleanup`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val before = G7SensorStateStore(context).read()
        val controller = Robolectric.buildActivity(G7WatchActivity::class.java).setup()
        val activity = controller.get()
        activity.prepareUnlinkAccess = { ready, _ -> ready() }
        activity.executeAssociationTask = { it() }
        activity.unlinkSensor = { G7UnlinkResult(G7BondRemovalStatus.FAILED) }
        try {
            activity.performSensorUnlink()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before.sensor, G7SensorStateStore(context).read().sensor)
            assertNull(findText(activity.window.decorView, "Sensor trennen"))
            assertNull(findText(activity.window.decorView, "Sensor verbinden"))
            assertNull(findText(activity.window.decorView, "Bluetooth-Einstellungen"))
            assertNotNull(findText(activity.window.decorView, "Trennen fehlgeschlagen. Sensorzuordnung bleibt erhalten. Bluetooth-Verbindung bei Bedarf in den Systemeinstellungen entfernen."))
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
        val before = G7SensorStateStore(context).read().sensor
        val controller = Robolectric.buildActivity(G7WatchActivity::class.java).setup()
        val activity = controller.get()
        var unlinkCalls = 0
        activity.prepareUnlinkAccess = { _, failed -> failed("Systemfreigabe abgebrochen") }
        activity.unlinkSensor = {
            unlinkCalls++
            G7UnlinkResult(G7BondRemovalStatus.REMOVED)
        }
        try {
            activity.performSensorUnlink()
            assertEquals(0, unlinkCalls)
            assertEquals(before, G7SensorStateStore(context).read().sensor)
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
        G7SensorStateStore(context).save(G7PersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
        val activity = controller.get()
        try {
            assertNotNull(findEditor(activity.window.decorView))
            activity.handleSystemBack()
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
    fun `explicit connect waits for old bond access before preparing and starting`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        G7SensorStateStore(context).save(G7PersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
        val activity = controller.get()
        var ready: (() -> Unit)? = null
        var starts = 0
        activity.prepareUnlinkAccess = { granted, _ -> ready = granted }
        activity.executeAssociationTask = { it() }
        activity.connectSensor = { _, code ->
            assertEquals("0123", code)
            starts++
        }
        try {
            findEditor(activity.window.decorView)!!.setText("0123")
            findText(activity.window.decorView, "Verbinden")!!.performClick()
            assertEquals(0, starts)
            assertNotNull(ready)
            ready!!.invoke()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, starts)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `cancelled old bond access returns to code entry without starting connection`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        G7SensorStateStore(context).save(G7PersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.ENTER_CODE.name) }
        val controller =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
        val activity = controller.get()
        var starts = 0
        activity.prepareUnlinkAccess = { _, failed -> failed("Systemfreigabe abgebrochen") }
        activity.connectSensor = { _, _ -> starts++ }
        try {
            findEditor(activity.window.decorView)!!.setText("0123")
            findText(activity.window.decorView, "Verbinden")!!.performClick()
            assertEquals(0, starts)
            assertNotNull(findEditor(activity.window.decorView))
            assertNotNull(findText(activity.window.decorView, "Verbinden"))
            assertNotNull(findText(activity.window.decorView, "Systemfreigabe abgebrochen"))
            assertFalse(G7SensorStateStore(context).read().collectorEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `permissions entry opens the dedicated app permissions page`() {
        val controller = Robolectric.buildActivity(G7SettingsActivity::class.java).setup()
        val activity = controller.get()
        try {
            (findText(activity.window.decorView, "Berechtigungen")!!.parent.parent as View).performClick()
            assertEquals(
                G7PermissionsActivity::class.java.name,
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
        val beforeAddress = G7PersistedState(sensor = G7Sensor("sensor", "session"), lastReading = reading)
        assertEquals(G7PairingScreenStep.CONNECTING, advanceG7PairingScreen(G7PairingScreenStep.CONNECTING, beforeAddress))
        val connected = beforeAddress.copy(sensor = beforeAddress.sensor!!.copy(deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals(G7PairingScreenStep.ENTER_CODE, advanceG7PairingScreen(G7PairingScreenStep.ENTER_CODE, connected))
        assertEquals(G7PairingScreenStep.CONNECTED, advanceG7PairingScreen(G7PairingScreenStep.CONNECTING, connected))
        assertEquals(G7PairingScreenStep.CONNECTED, advanceG7PairingScreen(G7PairingScreenStep.CONNECTED, connected))
    }

    @Test
    fun `connect action is debounced and completion is scheduled once`() {
        assertTrue(canStartG7Pairing(G7PairingScreenStep.ENTER_CODE, false, "1234"))
        assertFalse(canStartG7Pairing(G7PairingScreenStep.ENTER_CODE, true, "1234"))
        assertFalse(canStartG7Pairing(G7PairingScreenStep.CONNECTING, false, "1234"))
        assertFalse(canStartG7Pairing(G7PairingScreenStep.ENTER_CODE, false, "123"))
        assertTrue(shouldScheduleG7PairingCompletion(G7PairingScreenStep.CONNECTED, false))
        assertFalse(shouldScheduleG7PairingCompletion(G7PairingScreenStep.CONNECTED, true))
    }

    @Test
    fun `recoverable BLE errors keep pairing active but terminal errors stop it`() {
        val startedAt = 1_000_000L
        val recoverable =
            G7PersistedState(
                pairingStartedAtEpochMs = startedAt,
                pairingDeadlineEpochMs = startedAt + 60_000L,
                lastError = app.aapswear.g7.G7CollectorError("G7-GATT-133", true, startedAt + 1_000L, "temporary"),
            )
        assertFalse(isTerminalG7PairingFailure(recoverable, startedAt, startedAt + 2_000L))
        assertFalse(
            isTerminalG7PairingFailure(
                recoverable.copy(lastError = app.aapswear.g7.G7CollectorError("G7-AUTH-MISMATCH", true, startedAt + 1_000L, "candidate mismatch")),
                startedAt,
                startedAt + 2_000L,
            ),
        )
        assertTrue(
            isTerminalG7PairingFailure(
                recoverable.copy(lastError = app.aapswear.g7.G7CollectorError("G7-AUTH-204", false, startedAt + 1_000L, "rejected")),
                startedAt,
                startedAt + 2_000L,
            ),
        )
        assertFalse(isTerminalG7PairingFailure(recoverable, startedAt, startedAt + 60_000L))
    }

    @Test
    fun `durable background pairing does not open the wizard without an explicit UI request`() {
        val state =
            G7PersistedState(
                sensor = G7Sensor("pairing"),
                collectorEnabled = true,
                pairingAttemptId = "pairing-request",
                pairingStartedAtEpochMs = 1_000L,
                pairingDeadlineEpochMs = 2_000L,
                lastError = app.aapswear.g7.G7CollectorError("G7-BLE-111", true, 2_000L, "retry"),
            )

        assertNull(initialG7PairingScreenStep(state))
        assertEquals(G7PairingScreenStep.CONNECTING, initialG7PairingScreenStep(state, G7PairingScreenStep.CONNECTING))
    }

    @Test
    fun `restored connecting screen never animates a paused collector as an ongoing scan`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        G7SensorStateStore(context).save(G7PersistedState())
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.CONNECTING.name) }
        val controller =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
        try {
            val root = controller.get().window.decorView
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
        G7SensorStateStore(context).save(
            G7PersistedState(
                sensor = G7Sensor("pairing"),
                collectorEnabled = true,
                pairingAttemptId = "pairing-request",
                pairingStartedAtEpochMs = now,
                pairingDeadlineEpochMs = now + 60_000L,
            ),
        )
        val saved = android.os.Bundle().apply { putString("pairing_step", G7PairingScreenStep.CONNECTING.name) }
        val activity =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create(saved)
                .start()
                .resume()
                .get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNotNull(findConnectionDots(root))
        assertNull(findProgressBar(root))
        assertNotNull(findText(root, "Abbrechen"))
        activity.finish()
    }

    @Test
    fun `collector settings contain product sections and retain about`() {
        val activity = Robolectric.buildActivity(G7SettingsActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        val headers = mutableListOf<android.view.View>()

        fun collect(view: android.view.View) {
            if (view.tag?.toString()?.startsWith("settings-category-") == true) headers += view
            if (view is ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
        }
        collect(root)

        assertNotNull(findText(root, "Sensorempfang"))
        assertEquals(listOf("Anzeige", "Berechtigungen", "Alarme", "Sensor", "Vigil", "Über"), G7SettingsSection.entries.map { it.title })
        assertEquals(
            G7SettingsSection.entries.map { "settings-category-${it.name.lowercase()}" },
            headers.map { it.tag.toString() },
        )
        assertTrue(headers.all { it.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt() })
        activity.finish()
    }

    @Test
    fun `system status entry opens system status`() {
        val activity = Robolectric.buildActivity(G7SettingsActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        (findText(root, "Sensor")!!.parent.parent as android.view.View).performClick()

        assertEquals(
            G7SystemStatusActivity::class.java.name,
            Shadows
                .shadowOf(activity)
                .nextStartedActivity.component
                ?.className,
        )
        activity.finish()
    }

    @Test
    fun `alarm settings menu opens alarm configuration and not system status`() {
        val activity = Robolectric.buildActivity(G7SettingsActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        val alarmHeaderText = findText(root, "Alarme")!!
        (alarmHeaderText.parent.parent as android.view.View).performClick()

        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(G7AlarmSettingsActivity::class.java.name, intent.component?.className)
        assertFalse(intent.component?.className == G7SystemStatusActivity::class.java.name)
        activity.finish()
    }

    @Test
    fun `direct to watch category opens complete watchface settings`() {
        val settings = Robolectric.buildActivity(G7SettingsActivity::class.java).setup().get()
        val root = settings.findViewById<android.view.View>(android.R.id.content)
        val header = findText(root, "Vigil")!!
        (header.parent.parent as android.view.View).performClick()
        assertEquals(
            G7DirectToWatchSettingsActivity::class.java.name,
            Shadows
                .shadowOf(settings)
                .nextStartedActivity.component
                ?.className,
        )

        val activity = Robolectric.buildActivity(G7DirectToWatchSettingsActivity::class.java).setup().get()
        val texts = mutableListOf<String>()
        collectText(activity.findViewById(android.R.id.content), texts)
        assertTrue("Watchface" in texts)
        assertTrue(texts.any { it.startsWith("Größe ·") })
        assertTrue("LOW-Bereich" in texts)
        assertTrue("Zielbereich" in texts)
        assertTrue("HIGH-Bereich" in texts)
        assertTrue("Eckenrundung · 20.0 dp" in texts)
        assertTrue("Graphkontur" in texts)
        assertTrue("Zeitachsenskala" in texts)
        assertTrue("Zuckerwert fett" in texts)
        assertFalse("Horizontale Zielwert-Striche" in texts)
        assertTrue(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE in texts)
        assertFalse("Kontur · aktueller Wert" in texts)
        assertTrue(texts.any { it.startsWith("Aktuell ·") })
        assertFalse("Range-Hintergrundfärbung" in texts)
        assertFalse(texts.any { it.startsWith("Prognose ·") })
        val settingsRoot = activity.findViewById<android.view.View>(android.R.id.content)

        fun cardText(title: String): List<String> {
            val heading = findText(settingsRoot, title)
            assertNotNull("Missing element card: $title", heading)
            return mutableListOf<String>().also { collectText(heading!!.parent as android.view.View, it) }
        }
        val glucose = cardText("Glukose")
        assertTrue("mg/dL" in glucose)
        assertTrue("Zielbereich" in glucose)
        assertTrue("Zuckerwert fett" in glucose)
        assertTrue(cardText("Alter und Skala").any { it.startsWith("Größe ·") })
        assertTrue("Farbe" in cardText("Uhrzeit"))
        assertTrue("Füllfarbe" in cardText("Trendpfeil"))
        assertTrue("LOW-Bereich" in cardText("Graph"))
        assertTrue(AppearanceTerminology.DELTA_UNIT in cardText("Delta und Einheit"))
        assertTrue("Farbe" in texts)
        assertTrue("Fett" in texts)
    }

    @Test
    fun `direct to watch keeps independent unit and target range`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences(
                app.aapswear.protocol.DirectToWatchSettingsContract.PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            ).edit()
            .clear()
            .commit()
        val store = G7DirectToWatchSettingsStore(context)
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
                app.aapswear.protocol.DirectToWatchSettingsContract.PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            ).edit()
            .clear()
            .commit()
        val store = G7DirectToWatchSettingsStore(context)

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
        val activity = Robolectric.buildActivity(G7DirectToWatchSettingsActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
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
        val activity = Robolectric.buildActivity(G7DirectToWatchSettingsActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        val scroll = findScrollView(root)!!
        measureAndLayout(root)
        scroll.scrollTo(0, 220)

        val sliderRow = findTextStartingWith(root, "Punktgröße ·")!!.parent as ViewGroup
        val seekBar = (0 until sliderRow.childCount).map { sliderRow.getChildAt(it) }.filterIsInstance<SeekBar>().single()
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
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
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
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
        val activity = controller.get()
        val before = findGraph(activity.findViewById(android.R.id.content))
        assertNotNull(before)
        val scroll = findScrollView(activity.findViewById(android.R.id.content))!!
        measureAndLayout(activity.findViewById(android.R.id.content))
        scroll.scrollTo(0, 120)

        val now = System.currentTimeMillis()
        runBlocking {
            val database = G7ReadingDatabase(activity)
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
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val root = activity.findViewById<android.view.View>(android.R.id.content)

        assertNull(findText(root, "Systemstatus"))

        val settings = findText(root, "Einstellungen")
        assertTrue(settings is android.widget.Button)
        assertNotNull((settings as android.widget.Button).compoundDrawablesRelative[0])
        assertTrue(settings.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
        settings.performClick()
        assertEquals(
            G7SettingsActivity::class.java.name,
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

    private fun findConnectionDots(root: android.view.View): G7ConnectionDotsView? {
        if (root is G7ConnectionDotsView) return root
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
            G7ReadingDatabase(context).let { database ->
                try {
                    database.insert(reading)
                } finally {
                    database.close()
                }
            }
        }
        G7SensorStateStore(context).save(
            G7SensorStateStore(context).read().copy(
                sensor = G7Sensor(reading.sensorId, reading.sessionId),
                collectorEnabled = true,
                lastReading = reading,
            ),
        )
        val activity =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val arrows = mutableListOf<ImageView>()
        collectTrendArrows(activity.findViewById(android.R.id.content), arrows)

        assertEquals(1, arrows.size)
        val arrow = arrows.single()
        val arrowContainer = arrow.parent as android.widget.FrameLayout
        assertEquals(1f, G7_TREND_OPTICAL_OFFSET_DP)
        val opticalOffsetPx = (G7_TREND_OPTICAL_OFFSET_DP * activity.resources.displayMetrics.density).roundToInt()
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
    fun `system status screen keeps all real status groups and back navigation`() {
        val activity =
            Robolectric
                .buildActivity(G7SystemStatusActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val texts = mutableListOf<String>()
        collectText(activity.findViewById(android.R.id.content), texts)

        listOf(
            "LIVE COLLECTOR STATUS",
            "SYSTEMSTATUS",
            "SENSOR",
            "VERBINDUNG",
            "ZEITPLANUNG",
            "HARDWARETEST",
            "DIAGNOSE",
            "AKTIONEN",
        ).forEach {
            assertTrue("Missing group $it", texts.any { text -> text.contains(it, ignoreCase = true) })
        }
        findTextStartingWith(activity.findViewById(android.R.id.content), "▸  Details")!!.performClick()
        findTextStartingWith(activity.findViewById(android.R.id.content), "▸  Details")!!.performClick()
        texts.clear()
        collectText(activity.findViewById(android.R.id.content), texts)
        listOf(
            "Sensorstatus",
            "Session",
            "Sensorcode",
            "GTIN",
            "Seriennummer",
            "Letzter Wert",
            "Trendrate",
            "BLE-Name",
            "Kulanzende",
            "Status",
            "Reconnect-Strategie",
            "Hinweis",
            "Empfohlene Aktion",
            "Nächster Reconnect",
            "Geräte in der Nähe",
            "Benachrichtigungen",
            "GATT verbunden",
            "Aktiver Attempt",
            "Fehlercode",
        ).forEach {
            assertTrue("Missing status field $it", texts.contains(it))
        }
        assertTrue(texts.contains("Systemstatus"))
        val backViews = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(backViews, "Zurück", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        assertEquals(1, backViews.size)
        backViews.single().performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `period change updates only graph state and preserves screen and scroll`() {
        val activity =
            Robolectric
                .buildActivity(G7WatchActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
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

    private fun findGraph(view: android.view.View): G7CollectorGraphView? {
        if (view is G7CollectorGraphView) return view
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
