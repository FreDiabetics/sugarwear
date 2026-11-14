package app.sugarlicious.wear

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.protocol.WatchAppearanceProfile
import app.sugarlicious.protocol.WatchColorSync
import app.sugarlicious.protocol.WatchConfig
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.protocol.WatchGlucoseUnit
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.protocol.WatchUiColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import app.sugarlicious.complications.R as ComplicationR

@RunWith(RobolectricTestRunner::class)
class WearActivityTest {
    @Test
    fun `therapy overview keeps separate cards with IOB and COB side by side`() {
        val activity = Robolectric.buildActivity(WearActivity::class.java).setup().get()
        listOf(
            R.id.wear_iob_progress to R.id.wear_iob_card,
            R.id.wear_cob_progress to R.id.wear_cob_card,
            R.id.wear_basal_progress to R.id.wear_basal_card,
        ).forEach { (ringId, cardId) ->
            val ring = activity.findViewById<WearTherapyProgressView>(ringId)
            assertNotNull(ring)
            assertEquals(activity.findViewById<View>(cardId), (ring.parent as View).parent)
        }
        assertEquals(LinearLayout.HORIZONTAL, activity.findViewById<LinearLayout>(R.id.wear_therapy_row).orientation)
    }

    @Test
    fun `overview glucose uses value then shared progress bar then stacked age and delta pill`() {
        val activity = Robolectric.buildActivity(WearActivity::class.java).setup().get()
        val info = activity.findViewById<LinearLayout>(R.id.wear_glucose_info_block)
        val pill = activity.findViewById<LinearLayout>(R.id.wear_glucose_meta_row)

        assertEquals(3, info.childCount)
        assertEquals(R.id.wear_glucose_primary_row, info.getChildAt(0).id)
        assertTrue(info.getChildAt(1) is WearGlucoseRangeBarView)
        assertEquals(pill, info.getChildAt(2))
        assertEquals(R.id.wear_age, pill.getChildAt(0).id)
        assertEquals(R.id.wear_delta, pill.getChildAt(1).id)
        assertNotNull(pill.background)
    }

    @Test
    fun `overview ends with native settings action and contains no app branding`() {
        val activity = Robolectric.buildActivity(WearActivity::class.java).setup().get()
        val settings = activity.findViewById<android.widget.Button>(R.id.wear_settings_action)
        val menu = settings.parent as ViewGroup
        val views = mutableListOf<View>()

        fun collect(view: View) {
            views += view
            if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
        }
        collect(menu)
        assertTrue(views.filterIsInstance<TextView>().none { it.text.toString() == "Sugarlicious" })
        assertTrue(views.filterIsInstance<ImageView>().none { it.contentDescription?.toString() == "Sugarlicious" })
        assertEquals("Einstellungen", settings.text.toString())
        assertEquals(menu.childCount - 1, menu.indexOfChild(settings))
        assertTrue(settings.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
        assertNotNull(settings.compoundDrawablesRelative[0])
        settings.performClick()
        assertEquals(
            WearSettingsActivity::class.java.name,
            org.robolectric.Shadows
                .shadowOf(activity)
                .nextStartedActivity.component
                ?.className,
        )
    }

    @Test
    fun `first card starts inside the round display safe area`() {
        val activity = Robolectric.buildActivity(WearActivity::class.java).setup().get()
        val menu = activity.findViewById<View>(R.id.wear_glucose_card).parent as ViewGroup

        assertEquals((30 * activity.resources.displayMetrics.density).toInt(), menu.paddingTop)
    }

    @Test
    fun `wear appearance is dark by default and explicit selection remains active`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences(WearDisplayPreferences.PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertEquals(AppearanceMode.DARK, WearDisplayPreferences.activeAppearanceMode(context))
        WearDisplayPreferences.setActiveAppearanceMode(context, AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, WearDisplayPreferences.activeAppearanceMode(context))
        WearDisplayPreferences.setActiveAppearanceMode(context, AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, WearDisplayPreferences.activeAppearanceMode(context))
    }

    @Test
    fun `custom Mobile glucose progress colors survive Wear appearance persistence`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = WearDisplayPreferences.read(context, AppearanceMode.DARK)
        val palette =
            original.uiColors.copy(
                glucoseBarLow = 0xFFBA2436.toInt(),
                glucoseBarTarget = 0xFF00E886.toInt(),
                glucoseBarHigh = 0xFFE5AC10.toInt(),
                glucoseBarUnderlay = 0x33808080,
            )
        WearDisplayPreferences.saveLocal(context, AppearanceMode.DARK, original.copy(uiColors = palette))

        val stored = WearDisplayPreferences.read(context, AppearanceMode.DARK).uiColors
        assertEquals(palette.glucoseBarLow, stored.glucoseBarLow)
        assertEquals(palette.glucoseBarTarget, stored.glucoseBarTarget)
        assertEquals(palette.glucoseBarHigh, stored.glucoseBarHigh)
        assertEquals(palette.glucoseBarUnderlay, stored.glucoseBarUnderlay)
        WearDisplayPreferences.saveLocal(context, AppearanceMode.DARK, original)
    }

    @Test
    fun `legacy Wear source selection cannot override AndroidAPS phone policy`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences(WearDisplayPreferences.PREFS, android.content.Context.MODE_PRIVATE)
        prefs
            .edit()
            .clear()
            .putString("data_source", WatchDataSource.DEXCOM_G7_WATCH.name)
            .commit()

        assertEquals(WatchDataSource.PHONE, WearDisplayPreferences.read(context).dataSource)
    }

    @Test
    fun `graph scale cycles through every supported duration and wraps`() {
        assertEquals(2, nextWearGraphHours(1))
        assertEquals(3, nextWearGraphHours(2))
        assertEquals(6, nextWearGraphHours(3))
        assertEquals(12, nextWearGraphHours(6))
        assertEquals(24, nextWearGraphHours(12))
        assertEquals(1, nextWearGraphHours(24))
        assertEquals(1, nextWearGraphHours(99))
    }

    @Test
    fun `graph status combines scale with event timestamp age`() {
        assertEquals("3h • 2m", wearGraphScaleAgeText(3, 1_000L, 121_000L))
        assertEquals("24h • —", wearGraphScaleAgeText(24, null, 121_000L))
    }

    @Test
    fun `settings are round safe and grouped into independent sections`() {
        val controller = Robolectric.buildActivity(WearSettingsActivity::class.java).setup()
        val activity = controller.get()
        try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val headers = mutableListOf<View>()

            fun collect(view: View) {
                if (view.tag?.toString()?.startsWith("settings-category-") == true) headers += view
                if (view is ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
            }
            awaitSettings(root) { root.findViewWithTag<View>("settings-category-display") != null }
            collect(root)

            assertEquals(
                listOf("display", "glucose", "trend", "delta", "therapy", "graph", "tiles", "watchfaces", "connection", "diagnostics", "about"),
                headers.map { it.tag.toString().removePrefix("settings-category-") },
            )
            assertTrue(headers.all { it.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt() })

            root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 480, 480)
            val categoryScroll = settingsScroll(root)!!
            categoryScroll.scrollTo(0, 200)
            assertTrue(categoryScroll.scrollY > 0)

            // Opening an element exposes its complete controls without the other elements' controls.
            headers.single { it.tag == "settings-category-trend" }.performClick()
            awaitSettings(root) { root.findViewWithTag<View>("settings-category-display") == null && settingsScroll(root) != null }
            assertEquals("A new submenu must open at its header", 0, settingsScroll(root)!!.scrollY)
            val text = mutableListOf<String>()

            fun collectText(view: View) {
                if (view is TextView) text += view.text.toString()
                if (view is ViewGroup) (0 until view.childCount).forEach { collectText(view.getChildAt(it)) }
            }
            collectText(root)
            assertTrue("Trendpfeil" in text)
            assertTrue("Trendpfeil · Füllfarbe" in text)
            assertTrue("Trendpfeil-Kontur" in text)
            assertTrue("Trend-Deckkraft" in text)
            assertTrue("Trend-Stil zurücksetzen" in text)
            assertTrue("IOB" !in text && "COB" !in text && "Basal" !in text)
        } finally {
            controller.pause().stop().destroy()
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
        }
    }

    private fun settingsScroll(view: View): android.widget.ScrollView? {
        if (view is android.widget.ScrollView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) settingsScroll(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitSettings(root: View, ready: () -> Boolean) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (!ready() && System.nanoTime() < deadline) {
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(16))
            root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 480, 480)
            Thread.sleep(2)
        }
        assertTrue("Settings must load in their Compose window", ready())
    }

    @Test
    fun `therapy overview uses colored supplied icons and neutral values`() {
        val activity =
            Robolectric
                .buildActivity(WearActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .get()
        val colors = WearDisplayPreferences.read(activity).uiColors

        assertEquals(
            colors.iob,
            activity.findViewById<ImageView>(R.id.wear_iob_icon).imageTintList?.defaultColor,
        )
        assertEquals(
            colors.cob,
            activity.findViewById<ImageView>(R.id.wear_cob_icon).imageTintList?.defaultColor,
        )
        assertEquals(
            colors.basal,
            activity.findViewById<ImageView>(R.id.wear_basal_icon).imageTintList?.defaultColor,
        )
        assertEquals(colors.textPrimary, activity.findViewById<TextView>(R.id.wear_iob).currentTextColor)
        assertEquals(colors.textPrimary, activity.findViewById<TextView>(R.id.wear_cob).currentTextColor)
        assertEquals(colors.textPrimary, activity.findViewById<TextView>(R.id.wear_basal).currentTextColor)
        assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, activity.findViewById<LinearLayout>(R.id.wear_basal_card).gravity)
    }

    @Test
    fun `basal icon follows normal higher and lower temp basal`() {
        assertEquals(
            ComplicationR.drawable.ic_complication_basal,
            basalIconResource(BasalState(currentUnitsPerHour = 0.8)),
        )
        assertEquals(
            ComplicationR.drawable.ic_complication_basal_more,
            basalIconResource(
                BasalState(
                    currentUnitsPerHour = 0.8,
                    tempAbsoluteUnitsPerHour = 1.2,
                ),
            ),
        )
        assertEquals(
            ComplicationR.drawable.ic_complication_basal_less,
            basalIconResource(
                BasalState(
                    currentUnitsPerHour = 0.8,
                    tempAbsoluteUnitsPerHour = 0.4,
                ),
            ),
        )
        assertEquals(
            ComplicationR.drawable.ic_complication_basal_more,
            basalIconResource(BasalState(tempPercent = 150)),
        )
        assertEquals(
            ComplicationR.drawable.ic_complication_basal_less,
            basalIconResource(BasalState(tempPercent = 50)),
        )
    }

    @Test
    fun `glucose metadata is centered in a compact pill with age above delta`() {
        val activity =
            Robolectric
                .buildActivity(WearActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .get()
        val root = activity.findViewById<View>(R.id.wear_root)
        val info = activity.findViewById<ViewGroup>(R.id.wear_glucose_info_block)
        val primary = activity.findViewById<ViewGroup>(R.id.wear_glucose_primary_row)
        val meta = activity.findViewById<ViewGroup>(R.id.wear_glucose_meta_row)
        val glucose = activity.findViewById<TextView>(R.id.wear_glucose)
        val trend = activity.findViewById<View>(R.id.wear_trend_container)
        val delta = activity.findViewById<TextView>(R.id.wear_delta)
        val age = activity.findViewById<TextView>(R.id.wear_age)
        val density = activity.resources.displayMetrics.density

        glucose.text = "123"
        delta.text = app.sugarlicious.uishared.glucoseMetadataText("-5 mg/dL")
        age.text = app.sugarlicious.uishared.glucoseMetadataText("vor 2 Min")
        age.visibility = View.VISIBLE
        trend.visibility = View.VISIBLE
        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                (320f * density).toInt(),
                View.MeasureSpec.EXACTLY,
            ),
            View.MeasureSpec.makeMeasureSpec(
                (320f * density).toInt(),
                View.MeasureSpec.EXACTLY,
            ),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, info.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, primary.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, meta.layoutParams.width)
        assertEquals(Gravity.CENTER_HORIZONTAL, (meta.layoutParams as LinearLayout.LayoutParams).gravity)
        assertNotNull(meta.background)
        val colors = WearDisplayPreferences.read(activity, WearDisplayPreferences.activeAppearanceMode(activity)).uiColors
        assertEquals(colors.textPrimary, delta.currentTextColor)
        assertEquals(colors.textSecondary, age.currentTextColor)
        val styled = delta.text as android.text.Spanned
        assertTrue(styled.getSpans(0, styled.length, android.text.style.RelativeSizeSpan::class.java).isNotEmpty())
        assertTrue(styled.getSpans(0, styled.length, android.text.style.ForegroundColorSpan::class.java).any { it.foregroundColor == android.graphics.Color.rgb(181, 181, 181) })
        assertEquals(View.VISIBLE, age.visibility)
        assertTrue(age.bottom <= delta.top)
        assertEquals(1, age.lineCount)
        assertTrue(info.measuredWidth > primary.measuredWidth)
        assertTrue(meta.measuredWidth <= info.measuredWidth)
        assertEquals(1, delta.lineCount)
        assertEquals(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                14f,
                activity.resources.displayMetrics,
            ),
            delta.textSize,
            0.5f,
        )
        assertEquals(0, activity.resources.getIdentifier("wear_glucose_status", "id", activity.packageName))
    }

    @Test
    fun `dashboard exposes chart and bridge status`() {
        val activity =
            Robolectric
                .buildActivity(WearActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .get()

        assertEquals(
            "—",
            activity
                .findViewById<TextView>(
                    R.id.wear_glucose,
                ).text
                .toString(),
        )
        assertNotNull(
            activity.findViewById<WearGlucoseChart>(
                R.id.wear_glucose_chart,
            ),
        )
        assertNotNull(
            activity.findViewById<TextView>(
                R.id.wear_connection,
            ),
        )
        assertEquals(
            0,
            activity.resources.getIdentifier(
                "wear_config_info",
                "id",
                activity.packageName,
            ),
        )
    }

    @Test
    fun `graph scale age control is pill free and changes persisted viewport`() {
        val activity =
            Robolectric
                .buildActivity(WearActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .get()
        WearDisplayPreferences.saveLocal(activity, WearDisplayPreferences.read(activity).copy(graphHours = 3))
        val control = activity.findViewById<TextView>(R.id.wear_graph_period)

        control.performClick()

        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while ((!control.text.toString().startsWith("6h • ") || WearDisplayPreferences.read(activity).graphHours != 6) && System.nanoTime() < deadline) {
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(16))
            Thread.sleep(2)
        }

        assertEquals(6, WearDisplayPreferences.read(activity).graphHours)
        assertTrue(control.text.toString().startsWith("6h • "))
        assertEquals(null, control.background)
        assertTrue(control.minimumWidth >= (72 * activity.resources.displayMetrics.density).toInt())
    }

    @Test
    fun `watch config does not overwrite independent local display preferences`() {
        val context =
            ApplicationProvider.getApplicationContext<
                android.content.Context,
            >()

        WearDisplayPreferences.saveLocal(
            context,
            WearDisplayPreferences(
                graphHours = 12,
                showPredictions = true,
                glucoseUnit = WatchGlucoseUnit.MG_DL,
                showTherapyStats = true,
                graphColors = WatchGraphColors(nowLine = 0xFFABCDEF.toInt()),
                uiColors = WatchUiColors(basal = 0xFFABCDEF.toInt()),
            ),
        )

        WearDisplayPreferences.save(
            context,
            WatchConfig(
                graphHours = 6,
                showPredictions = false,
                glucoseUnit = WatchGlucoseUnit.MMOL_L,
                showTherapyStats = false,
                graphColors = WatchGraphColors(nowLine = 0xFF123456.toInt()),
                uiColors = WatchUiColors(basal = 0xFF123456.toInt()),
                sentAtEpochMs = 1234L,
            ),
        )

        val preferences =
            WearDisplayPreferences.read(context)

        assertEquals(12, preferences.graphHours)
        assertEquals(true, preferences.showPredictions)
        assertEquals(
            WatchGlucoseUnit.MG_DL,
            preferences.glucoseUnit,
        )
        assertEquals(true, preferences.showTherapyStats)
        assertEquals(0xFFABCDEF.toInt(), preferences.graphColors.nowLine)
        assertEquals(0xFFABCDEF.toInt(), preferences.uiColors.basal)
        assertEquals(1234L, preferences.syncedAtEpochMs)
    }

    @Test
    fun `explicit color sync updates every semantic graph role`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val colors =
            WatchGraphColors(
                highLine = 0xFF110001.toInt(),
                lowLine = 0xFF220002.toInt(),
                axisLabel = 0xFF330003.toInt(),
                axisTick = 0xFF440004.toInt(),
                nowLine = 0xFF550005.toInt(),
                divider = 0xFF660006.toInt(),
            )

        WearDisplayPreferences.applySyncedColors(
            context,
            WatchColorSync(graphColors = colors, sentAtEpochMs = 4321L),
        )

        assertEquals(colors, WearDisplayPreferences.read(context).graphColors)
    }

    @Test
    fun `explicit color sync never overwrites local glucose unit or target range`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val localThresholds = CgmThresholds(240.0, 165.0, 82.0, 55.0)
        WearDisplayPreferences.saveLocal(
            context,
            WearDisplayPreferences(glucoseUnit = WatchGlucoseUnit.MMOL_L, cgmThresholds = localThresholds),
        )

        WearDisplayPreferences.applySyncedColors(
            context,
            WatchColorSync(graphColors = WatchGraphColors(), cgmThresholds = CgmThresholds.DEFAULT, sentAtEpochMs = 9876L),
        )

        val result = WearDisplayPreferences.read(context)
        assertEquals(WatchGlucoseUnit.MMOL_L, result.glucoseUnit)
        assertEquals(localThresholds, result.cgmThresholds)
    }

    @Test
    fun `explicit color sync keeps light and dark profiles independent`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences(WearDisplayPreferences.PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val light = WatchGraphColors(graphBackground = 0xFFF4F4F4.toInt())
        val dark = WatchGraphColors(graphBackground = 0xFF090909.toInt())
        WearDisplayPreferences.applySyncedColors(
            context,
            WatchColorSync(
                graphColors = dark,
                lightProfile = WatchAppearanceProfile(graphColors = light),
                darkProfile = WatchAppearanceProfile(graphColors = dark),
                sentAtEpochMs = 555L,
            ),
        )
        assertEquals(light, WearDisplayPreferences.read(context, AppearanceMode.LIGHT).graphColors)
        assertEquals(dark, WearDisplayPreferences.read(context, AppearanceMode.DARK).graphColors)
    }
}
