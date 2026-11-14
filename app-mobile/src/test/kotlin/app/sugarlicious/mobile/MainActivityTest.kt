package app.sugarlicious.mobile

import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.derivedTargetValueArgb
import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.PredictionKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:org.junit.Rule val mainDispatcher = MobileMainDispatcherRule()

    @Test fun `stopped overview defers state observation until visible again`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = createActivity()
        val mainLooper = shadowOf(android.os.Looper.getMainLooper())
        val diagnostics = context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
        val model = androidx.lifecycle.ViewModelProvider(controller.get())[DashboardViewModel::class.java]

        fun awaitObservation(description: String, condition: () -> Boolean) {
            repeat(200) {
                mainLooper.idle()
                if (condition()) return
                Thread.sleep(5)
            }
            assertTrue(description, condition())
        }
        awaitObservation("Visible overview must start its canonical observation") {
            diagnostics.contains("mobileObservedAt") && model.uiState.value.nowEpochMs > 0
        }
        controller.pause().stop()
        // WhileSubscribed resets replay only after its previous upstream collection has finished
        // cancellation. Await that barrier before clearing the old observation marker.
        awaitObservation("Stopped overview must finish cancelling its upstream observation") {
            model.uiState.value == DashboardUiState()
        }
        diagnostics.edit().remove("mobileObservedAt").commit()
        kotlinx.coroutines.runBlocking {
            app.sugarlicious.storage.TherapyStateStore(context).save(
                app.sugarlicious.model.TherapyDisplayState(receivedAtEpochMs = 1234L),
            )
        }
        repeat(20) {
            mainLooper.idle()
            Thread.sleep(10)
        }
        assertFalse("Hidden UI must not consume state or refresh charts", diagnostics.contains("mobileObservedAt"))
        controller.restart().start().resume()
        repeat(40) {
            mainLooper.idle()
            if (!diagnostics.contains("mobileObservedAt")) Thread.sleep(10)
        }
        assertTrue("Returning to the overview must immediately observe the latest state", diagnostics.contains("mobileObservedAt"))
        controller.pause().stop().destroy()
    }

    @Test fun `settings overview hides back while submenus keep it`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.top_back).visibility)
        openSettingsCategory(activity, "general")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.top_back).visibility)
        controller.pause().stop().destroy()
    }

    @Test fun `expanded submenu title fades without resizing its text box`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        openSettingsCategory(activity, "notifications")
        val root = activity.findViewById<View>(R.id.root)
        val content = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        val expand = MainActivity::class.java.getDeclaredMethod("setSubmenuExpansion", Float::class.javaPrimitiveType).apply { isAccessible = true }

        fun titleHeight(expansion: Float): Int {
            expand.invoke(activity, expansion * activity.resources.displayMetrics.density)
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2400)
            val header = content.findViewWithTag<View>("settings_submenu_expansion")
            return if (header is TextView) header.height else (header as ViewGroup).getChildAt(0).height
        }
        val partial = titleHeight(110f)
        assertTrue(partial > 0)
        assertEquals("Pulling must change opacity and spacing, never reveal a growing text box", partial, titleHeight(160f))
        controller.pause().stop().destroy()
    }

    @Test fun `submenu row stays just above the first card throughout a pull`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        openSettingsCategory(activity, "notifications")
        val root = activity.findViewById<View>(R.id.root)
        val bar = activity.findViewById<View>(R.id.top_app_bar)
        val back = activity.findViewById<View>(R.id.top_back)
        val content = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        val density = activity.resources.displayMetrics.density
        val update = MainActivity::class.java.getDeclaredMethod("updateSettingsScrollHeader", Int::class.javaPrimitiveType).apply { isAccessible = true }

        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2400)
            update.invoke(activity, 0)
        }
        layout()
        val start = IntArray(2).also(bar::getLocationOnScreen)
        MainActivity::class.java
            .getDeclaredMethod("setSubmenuExpansion", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(activity, 96f * density)
        layout()
        val moved = IntArray(2).also(bar::getLocationOnScreen)
        assertEquals("Back and compact title must travel together with the card", 96f * density, (moved[1] - start[1]).toFloat(), 1f)

        fun nativeCard(view: View): View? =
            if (view.tag == "oneui_settings_card") {
                view
            } else if (view is ViewGroup) {
                (0 until view.childCount).firstNotNullOfOrNull { nativeCard(view.getChildAt(it)) }
            } else {
                null
            }
        val first = requireNotNull(nativeCard(content))
        val firstY = IntArray(2).also(first::getLocationOnScreen)[1]
        val backBottom = IntArray(2).also(back::getLocationOnScreen)[1] + back.height
        assertTrue("The back row must remain close above its card", (firstY - backBottom) / density in 4f..16f)
        controller.pause().stop().destroy()
    }

    @Test fun `background refresh preserves long submenu position without synthetic scroll padding`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        openSettingsCategory(activity, "appearance")
        val root = activity.findViewById<View>(R.id.root)
        val content = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        val scroll = activity.findViewById<DashboardScrollView>(R.id.dashboard_scroll)

        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2400)
        }
        layout()
        val update = MainActivity::class.java.getDeclaredMethod("updateSettingsScrollHeader", Int::class.javaPrimitiveType).apply { isAccessible = true }
        update.invoke(activity, 0)
        layout()
        // The appearance repository loads its real controls asynchronously. Await their natural
        // measured height before asserting that this long page can scroll.
        awaitUiCondition(activity, "Appearance controls must provide their natural scroll range") {
            content.height - scroll.height + scroll.paddingTop + scroll.paddingBottom >= 250
        }
        val padding = content.paddingBottom
        assertEquals((24 * activity.resources.displayMetrics.density).toInt(), padding)
        scroll.scrollTo(0, 250)
        val position = scroll.scrollY
        assertTrue(position > 0)
        MainActivity::class.java
            .getDeclaredMethod("refresh", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(activity, false)
        assertEquals("Data/status refresh must preserve the natural content bounds", padding, content.paddingBottom)
        layout()
        assertEquals(position, scroll.scrollY)
        controller.pause().stop().destroy()
    }

    @Test fun `settings scrolling transforms the title without requesting another layout`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        val root = activity.findViewById<View>(R.id.root)

        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2400)
        }

        layout()
        val update = MainActivity::class.java.getDeclaredMethod("updateSettingsScrollHeader", Int::class.javaPrimitiveType).apply { isAccessible = true }
        update.invoke(activity, 0)
        layout()
        val heading = activity.findViewById<ViewGroup>(R.id.dashboard_content).findViewWithTag<TextView>("settings_scroll_title")
        val height = heading.height
        update.invoke(activity, (40 * activity.resources.displayMetrics.density).toInt())
        assertFalse("The first drag must not remeasure the scrolling title", heading.isLayoutRequested)
        assertEquals(height, heading.height)
        assertTrue(heading.scaleX < 1f)
        controller.pause().stop().destroy()
    }

    @Test fun `settings category rows match DeviceVendor compact height`() {
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        val row = activity.findViewById<ViewGroup>(R.id.dashboard_content).findViewWithTag<View>("settings-category-general")
        assertEquals((52 * activity.resources.displayMetrics.density).toInt(), row.minimumHeight)
        controller.pause().stop().destroy()
    }

    @Test fun `settings container preserves device styling independently from therapy palette overrides`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .putBoolean("compact", true)
            .putString(DashboardUiPreferences.GLUCOSE_TILE_DETAIL_MODE_KEY, GlucoseTileDetailMode.TIR.name)
            .commit()
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.PRIMARY, Color.RED)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.TEXT_PRIMARY, Color.GREEN)
        val controller = createActivity()
        val activity = controller.get()
        openSettings(activity)
        val root = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        val heading = root.findViewWithTag<TextView>("settings_scroll_title")
        assertEquals(MobileSettingsStyle.text(false), heading.currentTextColor)
        val styledTitle = heading.text as android.text.Spanned
        assertTrue(
            styledTitle.getSpans(0, styledTitle.length, android.text.style.ForegroundColorSpan::class.java).any {
                it.foregroundColor ==
                    app.sugarlicious.mobile.ui.theme.SugarliciousColors
                        .argb(SugarliciousColorRole.BRAND_GREEN)
            },
        )
        openSettingsCategory(activity, "overview_graphs")
        val actual = activity.findViewById<android.widget.Switch>(R.id.dashboard_compact_switch)
        val expected = mobileSettingsSwitch(activity, actual.isChecked) {}
        val checkedState = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        assertEquals(expected.trackTintList?.getColorForState(checkedState, 0), actual.trackTintList?.getColorForState(checkedState, 0))
        controller.pause().stop().destroy()
        preferences.edit().clear().commit()
        app.sugarlicious.mobile.ui.theme.SugarliciousColors
            .apply(SugarliciousColorStore.load(preferences))
    }

    @Test fun `watch config uses mobile therapy icon colors`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val iob = Color.rgb(10, 20, 30)
        val cob = Color.rgb(40, 50, 60)
        val basal = Color.rgb(70, 80, 90)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.THERAPY_IOB_PROGRESS, iob)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.THERAPY_COB_PROGRESS, cob)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.THERAPY_BASAL_PROGRESS, basal)

        val colors = readWatchConfig(context).uiColors

        assertEquals(iob, colors.iob)
        assertEquals(cob, colors.cob)
        assertEquals(basal, colors.basal)
    }

    @Test fun `watch config remains phone sourced despite a legacy direct G7 preference`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("dataSource", DataSourcePreference.DEXCOM_G7_WATCH.name)
            .commit()
        assertEquals(app.sugarlicious.protocol.WatchDataSource.PHONE, readWatchConfig(context).dataSource)
    }

    @Test fun `app surfaces are neutral gray and system accent follows the icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            R.color.app_background,
            R.color.app_surface,
            R.color.app_surface_high,
            R.color.app_surface_raised,
            R.color.app_surface_selected,
        ).forEach { colorId ->
            val color = context.getColor(colorId)
            assertEquals(Color.red(color), Color.green(color))
            assertEquals(Color.green(color), Color.blue(color))
        }
        assertEquals(Color.rgb(34, 235, 96), context.getColor(R.color.app_accent))
    }

    @Test fun `diagnostics update while overview dashboard stays visible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostics = context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
        diagnostics.edit().clear().commit()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val controller = createActivity()
        val activity = controller.get()
        val dashboard = activity.findViewById<ViewGroup>(R.id.dashboard_content)

        assertNotNull(dashboard)
        assertTrue(dashboard.childCount > 0)
        val originalComposeView = dashboard.getChildAt(0)

        diagnostics
            .edit()
            .putLong("received", 1_000L)
            .putString("contract", "AAPS_EXTENDED_STATUS_V1")
            .putString("sourceVersion", "4.0.0-dev-b")
            .putInt("reachableWatches", 1)
            .putString("lastSyncStatus", "ok")
            .commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(dashboard.childCount > 0)
        assertSame(originalComposeView, dashboard.getChildAt(0))

        openSettings(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val settingsText = textOf(activity.findViewById(R.id.dashboard_content))
        assertTrue(settingsText.contains("Datenverwaltung"))
        openSettingsCategory(activity, "general")
        assertNotNull(activity.findViewById<ViewGroup>(R.id.dashboard_content).findViewWithTag<View>("mobile-data-sources"))
        activity.findViewById<View>(R.id.top_back).performClick()
        openSettingsCategory(activity, "data")
        val dataText = textOf(activity.findViewById(R.id.dashboard_content))
        assertTrue(dataText.contains("Dauerbetrieb"))
        assertTrue(dataText.contains("Einstellungen sichern"))
        assertTrue(dataText.contains("Einstellungen wiederherstellen"))
        controller.pause().stop().destroy()
    }

    @Test fun `settings persist from their category pages`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
            .putBoolean("compact", true)
            .putString(DashboardUiPreferences.GLUCOSE_TILE_DETAIL_MODE_KEY, GlucoseTileDetailMode.TIR.name)
            .commit()
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        openSettingsCategory(activity, "overview_graphs")
        assertNull(activity.findViewById<View>(R.id.dashboard_details_switch))
        val compact = activity.findViewById<android.widget.Switch>(R.id.dashboard_compact_switch)
        assertTrue(compact.isChecked)
        compact.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(preferences.getBoolean("compact", true))

        activity.findViewById<View>(R.id.top_back).performClick()
        openSettingsCategory(activity, "notifications")
        val live = activity.findViewById<android.widget.Switch>(R.id.dashboard_live_notification_switch)
        assertFalse(live.isChecked)
        live.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(preferences.getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false))

        controller.pause().stop().destroy()
    }

    @Test fun `color store preserves configured transparency`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val configured = Color.argb(96, 84, 223, 48)

        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_IN_RANGE, configured)

        assertEquals(96, Color.alpha(SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.RANGE_IN_RANGE)))
    }

    @Test fun `light target value defaults to the opaque graph line color`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "LIGHT")
            .commit()

        val target = SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.TARGET_VALUE)

        assertEquals(255, Color.alpha(target))
        assertEquals(derivedTargetValueArgb(SugarliciousColorRole.RANGE_IN_RANGE.lightArgb), target)
    }

    @Test fun `cgm dot appearance settings are read from preferences`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putFloat("cgm.dotRadiusDp", 4.25f)
            .putBoolean("cgm.dotOutlineEnabled", false)
            .putFloat("cgm.dotOutlineWidthDp", 1.75f)
            .commit()

        val ui = DashboardUiPreferences.read(preferences)

        assertEquals(4.25f, ui.cgmDotRadiusDp, 0.001f)
        assertFalse(ui.cgmHistoricalDotOutlineEnabled)
        assertEquals(1.75f, ui.cgmHistoricalDotOutlineWidthDp, 0.001f)
        assertEquals(1.75f, ui.cgmCurrentDotOutlineWidthDp, 0.001f)
    }

    @Test fun `notification graph defaults are independent from dashboard graph`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putInt("graphHours", 24)
            .putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED, false)
            .putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 2)
            .commit()

        val ui = DashboardUiPreferences.read(preferences)

        assertEquals(24, ui.graphHours)
        assertFalse(ui.notificationGraphEnabled)
        assertEquals(2, ui.notificationGraphHours)
    }

    @Test fun `fresh install uses requested overview and CGM defaults`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = createActivity()

        val ui = DashboardUiPreferences.read(preferences)
        assertTrue(ui.showCgmGraph)
        assertFalse(preferences.contains("cgm.targetRange"))
        assertTrue(ui.showCgmTargetValue)
        assertTrue(ui.showCgmBasal)
        assertTrue(ui.showCgmActivity)
        assertTrue(ui.anyCgmPredictionEnabled)
        assertTrue(ui.showMetabolicGraph)
        assertTrue(ui.notificationGraphEnabled)
        assertEquals(2, ui.notificationGraphHours)

        controller.pause().stop().destroy()
    }

    @Test fun `current and base basal graph streams persist independently`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean("cgm.basal.current", true)
            .putBoolean("cgm.basal.base", false)
            .commit()

        val ui = DashboardUiPreferences.read(preferences)
        assertTrue(ui.showCgmCurrentBasal)
        assertFalse(ui.showCgmBaseBasal)
    }

    @Test fun `IOB and COB graph maxima persist independently from progress rings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putFloat(DashboardUiPreferences.IOB_PROGRESS_MAXIMUM_KEY, 9f)
            .putFloat(DashboardUiPreferences.COB_PROGRESS_MAXIMUM_KEY, 250f)
            .putFloat(DashboardUiPreferences.IOB_GRAPH_MAXIMUM_KEY, 12f)
            .putFloat(DashboardUiPreferences.COB_GRAPH_MAXIMUM_KEY, 180f)
            .commit()

        val firstRead = DashboardUiPreferences.read(preferences)
        val afterRestart = DashboardUiPreferences.read(preferences)

        assertEquals(9f, firstRead.iobProgressMaximumUnits, 0f)
        assertEquals(250f, firstRead.cobProgressMaximumGrams, 0f)
        assertEquals(12f, firstRead.iobGraphMaximumUnits, 0f)
        assertEquals(180f, firstRead.cobGraphMaximumGrams, 0f)
        assertEquals(firstRead.iobGraphMaximumUnits, afterRestart.iobGraphMaximumUnits, 0f)
        assertEquals(firstRead.cobGraphMaximumGrams, afterRestart.cobGraphMaximumGrams, 0f)
    }

    @Test fun `IOB and COB graph scale modes persist independently`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString(DashboardUiPreferences.IOB_GRAPH_SCALE_MODE_KEY, CgmGraphScaleMode.DYNAMIC.name)
            .putString(DashboardUiPreferences.COB_GRAPH_SCALE_MODE_KEY, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC.name)
            .commit()

        val ui = DashboardUiPreferences.read(preferences)

        assertEquals(CgmGraphScaleMode.DYNAMIC, ui.iobGraphScaleMode)
        assertEquals(CgmGraphScaleMode.LOGARITHMIC_DYNAMIC, ui.cobGraphScaleMode)
    }

    @Test fun `IOB and COB graph maximum controls are visible in mobile settings`() {
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        openSettingsCategory(activity, "overview_graphs")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val settingsText = textOf(activity.findViewById(R.id.dashboard_content))

        assertTrue(settingsText.contains("IOB Graph Maximum"))
        assertTrue(settingsText.contains("COB Graph Maximum"))
        assertTrue(settingsText.contains("IOB-Skalierung"))
        assertTrue(settingsText.contains("COB-Skalierung"))
        controller.pause().stop().destroy()
    }

    @Test
    fun `prediction master switch controls every prediction stream and divider`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("prediction_master_contract", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean("showPredictions", false)
            .putBoolean("cgm.prediction.iob", true)
            .putBoolean("cgm.prediction.cob", true)
            .putBoolean("cgm.prediction.uam", true)
            .putBoolean("cgm.prediction.zeroTemp", true)
            .commit()

        val disabled = DashboardUiPreferences.read(preferences)
        assertFalse(disabled.predictionsEnabled)
        assertFalse(disabled.showPredictionDivider)

        preferences.edit().putBoolean("showPredictions", true).commit()
        val enabled = DashboardUiPreferences.read(preferences)
        assertTrue(enabled.predictionsEnabled)
        assertTrue(enabled.showPredictionDivider)
        assertEquals(
            PredictionKind.entries.toSet(),
            enabled.enabledPredictionKinds,
        )
    }

    @Test fun `saving carousel position does not rebuild the visible dashboard`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putInt("watchFaceIndex", 1)
            .commit()
        val controller = createActivity()
        val activity = controller.get()
        val dashboard = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        val originalComposeView = dashboard.getChildAt(0)

        preferences.edit().putInt("watchFaceIndex", 2).commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(2, preferences.getInt("watchFaceIndex", -1))
        assertSame(originalComposeView, dashboard.getChildAt(0))
        controller.pause().stop().destroy()
    }

    @Test fun `Sugarlicious about tile shows compact branding and contact pills`() {
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        openSettingsCategory(activity, "about")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val settingsText = textOf(activity.findViewById(R.id.dashboard_content))
        assertTrue(settingsText.contains("Sugarlicious"))
        assertFalse(settingsText.contains("typ1.diafreddy@gmail.com"))
        assertFalse(settingsText.contains("FreDiabetics/aaps_wearable-suite"))
        assertFalse(settingsText.contains("Unabh\u00e4ngiges Projekt"))
        assertTrue(settingsText.contains("GitHub"))
        assertTrue(settingsText.contains("E-Mail"))
        assertFalse(settingsText.contains("Wear OS und Watchfaces"))
        assertFalse(settingsText.contains("Treatments und Nightscout"))
        assertFalse(settingsText.contains("Datenquellen"))

        activity.findViewById<View>(R.id.dashboard_github).performClick()
        val githubIntent = shadowOf(activity).nextStartedActivity
        assertEquals("github.com", githubIntent.data?.host)

        activity.findViewById<View>(R.id.dashboard_contact_email).performClick()
        val emailIntent = shadowOf(activity).nextStartedActivity
        assertEquals("mailto", emailIntent.data?.scheme)
        assertEquals("typ1.diafreddy@gmail.com", emailIntent.data?.schemeSpecificPart)
        controller.pause().stop().destroy()
    }

    @Test fun `bottom navigation is removed and compact top navigation is available`() {
        val controller = createActivity()
        val activity = controller.get()
        assertEquals(0, activity.resources.getIdentifier("menu_button", "id", activity.packageName))
        assertEquals(0, activity.resources.getIdentifier("more_button", "id", activity.packageName))
        assertEquals(0, activity.resources.getIdentifier("bottom_navigation", "id", activity.packageName))
        assertNotNull(activity.findViewById<View>(R.id.top_app_bar))
        assertNotNull(activity.findViewById<View>(R.id.top_settings))
        openSettings(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(textOf(activity.findViewById(R.id.dashboard_content)).contains("Einstellungen"))
        controller.pause().stop().destroy()
    }

    @Test fun `system back navigates to overview and can never finish the main activity`() {
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.top_app_bar).visibility)

        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.top_app_bar).visibility)
        assertFalse(activity.isFinishing)

        repeat(3) {
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertFalse(activity.isFinishing)

        controller.pause().stop().destroy()
    }

    @Test fun `settings categories keep order and back returns from a page before leaving settings`() {
        val controller = createActivity()
        var activity = controller.get()
        openSettings(activity)

        fun categories(): List<String> {
            settleUi(activity)
            val root = activity.findViewById<ViewGroup>(R.id.dashboard_content)

            fun descendants(view: View): List<View> =
                listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            return descendants(root)
                .mapNotNull {
                    it.tag
                        ?.toString()
                        ?.takeIf { tag -> tag.startsWith("settings-category-") }
                        ?.removePrefix("settings-category-")
                }
        }
        val expected = listOf("general", "glucose_ranges", "notifications", "overview_graphs", "appearance", "data", "diagnostics", "about")
        assertEquals(expected, categories())
        val root = activity.findViewById<ViewGroup>(R.id.dashboard_content)
        listOf("general" to "glucose_ranges", "overview_graphs" to "appearance", "data" to "diagnostics").forEach { (first, second) ->
            val firstRow = root.findViewWithTag<View>("settings-category-$first")
            val secondRow = root.findViewWithTag<View>("settings-category-$second")
            assertSame(firstRow.parent, secondRow.parent)
            assertEquals(3, (firstRow.parent as ViewGroup).childCount)
        }
        openSettingsCategory(activity, "general")
        assertTrue(categories().isEmpty())
        assertEquals(
            "Datenquellen",
            activity
                .findViewById<TextView>(R.id.app_title)
                .text
                .toString(),
        )
        assertNotNull(activity.findViewById<ViewGroup>(R.id.dashboard_content).findViewWithTag<View>("mobile-data-sources"))
        controller.recreate()
        activity = controller.get()
        assertEquals(
            "Datenquellen",
            activity
                .findViewById<TextView>(R.id.app_title)
                .text
                .toString(),
        )
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(expected, categories())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.top_app_bar).visibility)
        activity.findViewById<View>(R.id.top_back).performClick()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.top_app_bar).visibility)
        openSettings(activity)
        assertEquals(expected, categories())
        controller.pause().stop().destroy()
    }

    @Test fun `watch menu keeps its header outside the scrolling sections`() {
        val controller = createActivity()
        val activity = controller.get()
        val scroll = activity.findViewById<DashboardScrollView>(R.id.dashboard_scroll)

        assertFalse("Overview uses its viewport-bounded Compose scroller", scroll.isUserScrollEnabled)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scroll_fade).visibility)

        activity.findViewById<View>(R.id.watch_fixed_header)
        activity.javaClass.getDeclaredMethod("navigate", DashboardScreen::class.java).apply {
            isAccessible = true
            invoke(activity, DashboardScreen.WATCH)
        }
        // The first ScrollView measurement happens before any posted resize callback.
        val watchContent = activity.findViewById<ViewGroup>(R.id.dashboard_content).getChildAt(0)
        assertTrue("Watch must have a finite height before attachment is measured", watchContent.layoutParams.height > 0)
        val root = activity.findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2400)
        // Do not drain an endlessly blinking Compose animation to idleness.
        // Native navigation state is synchronous; this test exercises the first measure.

        assertFalse(scroll.isUserScrollEnabled)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.top_app_bar).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.watch_fixed_header).visibility)
        assertEquals("Watch", activity.findViewById<TextView>(R.id.app_title).text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `live notification switch opens the Android 16 promotion permission when needed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
            .commit()
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        openSettingsCategory(activity, "notifications")
        activity.findViewById<View>(R.id.dashboard_live_notification_switch).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(
            context
                .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
                .getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false),
        )
        val settingsIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, settingsIntent.action)
        assertEquals(activity.packageName, settingsIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `existing live preference resumes promotion setup once per app session`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()

        val controller = createActivity()
        val activity = controller.get()
        val firstIntent = shadowOf(activity).nextStartedActivity

        assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, firstIntent.action)
        assertEquals(activity.packageName, firstIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        controller.pause().resume()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, shadowOf(activity).nextStartedActivity?.action)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `live notification switch requests exact alarm access before promotion permission`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
            .commit()
        val controller = createActivity()
        val activity = controller.get()

        openSettings(activity)
        openSettingsCategory(activity, "notifications")
        activity.findViewById<View>(R.id.dashboard_live_notification_switch).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val settingsIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, settingsIntent.action)
        assertEquals("package:${activity.packageName}", settingsIntent.dataString)
        assertFalse(
            context
                .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
                .getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false),
        )
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller.pause().resume()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(
            context
                .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
                .getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false),
        )

        val promotionIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, promotionIntent.action)
        assertEquals(activity.packageName, promotionIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        controller.pause().stop().destroy()
    }

    @Test
    fun `live notification status keeps deviceVendor path independent from android promotion`() {
        val status = liveNotificationStatus(true, 35, "Samsung", canPostPromoted = null)

        assertEquals("Samsung", status.device)
        assertEquals("angefordert", status.deviceVendorNowBar)
        assertEquals("nicht unterst\u00fctzt", status.androidLiveUpdates)
        assertEquals("nicht verf\u00fcgbar", status.promotedNotifications)
        assertEquals("nicht gepr\u00fcft", status.promotableCharacteristics)
        assertEquals("nicht angewendet", status.deviceVendorExtras)
        assertEquals("noch nicht erfolgt", status.lastPostAttempt)
        assertEquals("nicht erforderlich", status.exactFreshnessAlarm)
        assertEquals("nicht durchgef\u00fchrt", status.hardwareValidation)
    }

    private val activities = mutableListOf<ActivityController<MainActivity>>()

    private fun createActivity(): ActivityController<MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        activities += controller
        controller.setup().visible()
        settleUi(controller.get())
        return controller
    }

    @After fun destroyActivities() {
        activities.asReversed().forEach { controller ->
            val activity = controller.get()
            if (!activity.isDestroyed) {
                if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) controller.pause()
                if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) controller.stop()
                controller.destroy()
            }
        }
        activities.clear()
    }

    private fun settleUi(activity: MainActivity) {
        val root = activity.window.decorView
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1080, 2400)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        }
    }

    private fun awaitUiCondition(activity: MainActivity, description: String, condition: () -> Boolean) {
        repeat(120) {
            settleUi(activity)
            if (condition()) return
            Thread.sleep(5)
        }

        fun dimensions(view: View, depth: Int = 0): String {
            val own = "${" ".repeat(depth)}${view.javaClass.simpleName} tag=${view.tag} h=${view.height} measured=${view.measuredHeight} w=${view.width} requested=${view.isLayoutRequested}"
            return own + if (view is ViewGroup && depth < 8) (0 until view.childCount).joinToString("\n", prefix = "\n") { dimensions(view.getChildAt(it), depth + 1) } else ""
        }
        val appearance =
            androidx.lifecycle
                .ViewModelProvider(activity)["mobile-appearance", AppearanceSettingsViewModel::class.java]
                .uiState.value
        throw AssertionError("$description; appearanceSnapshot=${appearance.snapshot != null} error=${appearance.error}\n" + dimensions(activity.findViewById(R.id.dashboard_scroll)))
    }

    private fun awaitSettingsView(activity: MainActivity, tag: String): View {
        repeat(80) {
            settleUi(activity)
            activity.findViewById<ViewGroup>(R.id.dashboard_content).findViewWithTag<View>(tag)?.let { view ->
                if (view.isAttachedToWindow) return view
            }
            Thread.sleep(5)
        }
        throw AssertionError("Settings did not attach view $tag")
    }

    private fun openSettings(activity: MainActivity) {
        activity.findViewById<View>(R.id.top_settings).performClick()
        awaitSettingsView(activity, "settings_scroll_title")
    }

    private fun openSettingsCategory(activity: MainActivity, key: String) {
        awaitSettingsView(activity, "settings-category-$key").performClick()
        settleUi(activity)
    }

    private fun textOf(view: View): String =
        when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString(" ") { textOf(view.getChildAt(it)) }
            else -> ""
        }
}
