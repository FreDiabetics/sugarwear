package app.sugarlicious.mobile

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TherapyMetricAppearance
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.model.TrendArrowStyleOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetInstanceConfigurationTest {
    @Test
    fun `widget theme follows Mobile while its own display details remain isolated`() {
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("themeMode", "DARK")
            .commit()
        val value = WidgetInstanceConfigurationStore.read(context, 917).copy(themeMode = DashboardThemeMode.LIGHT, graphHours = 6)
        WidgetInstanceConfigurationStore.save(context, 917, value)
        val restored = WidgetInstanceConfigurationStore.read(context, 917)
        assertEquals(DashboardThemeMode.DARK, restored.themeMode)
        assertEquals(6, restored.graphHours)
    }

    @Test
    fun `new glucose family retains captured icon defaults without replacing saved instances`() {
        val defaults = WidgetSystemDefaults.forNewInstance(context, ConfigurableWidgetKind.GLUCOSE)
        assertEquals(0xFFCCCCCC.toInt(), defaults.colorOverrides[WidgetColorRole.DELTA_ICON])
        assertEquals(0xFF59FFFF.toInt(), defaults.lightColorOverrides[WidgetColorRole.AGE_ICON])
        assertEquals(0xFF59FF59.toInt(), defaults.colorOverrides[WidgetColorRole.TIR_ICON])
        WidgetInstanceConfigurationStore.save(context, 9983, defaults.copy(colorOverrides = defaults.colorOverrides + (WidgetColorRole.AGE_ICON to Color.RED)))
        WidgetSystemDefaults.forNewInstance(context, ConfigurableWidgetKind.GLUCOSE)
        assertEquals(Color.RED, WidgetInstanceConfigurationStore.read(context, 9983).colorOverrides[WidgetColorRole.AGE_ICON])
    }

    @Test
    fun `graph contour persists independently of outer contour and other instances`() {
        WidgetInstanceConfigurationStore.save(context, 9981, WidgetInstanceConfiguration(graphOutlineEnabled = true, graphOutlineWidthDp = 2.3f, outlineEnabled = false))
        val stored = WidgetInstanceConfigurationStore.read(context, 9981)
        assertTrue(stored.graphOutlineEnabled)
        assertEquals(2.3f, stored.graphOutlineWidthDp)
        assertFalse(stored.outlineEnabled)
        assertFalse(WidgetInstanceConfigurationStore.read(context, 9982).graphOutlineEnabled)
    }

    @Test
    fun `additional widget kinds survive resolution independently without changing legacy providers`() {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, AdditionalWidgetReceiver::class.java)
        org.robolectric.Shadows
            .shadowOf(manager)
            .addInstalledProvider(android.appwidget.AppWidgetProviderInfo().apply { provider = component })
        org.robolectric.Shadows
            .shadowOf(manager)
            .setAllowedToBindAppWidgets(true)
        manager.bindAppWidgetIdIfAllowed(9940, component)
        manager.bindAppWidgetIdIfAllowed(9941, component)
        manager.updateAppWidgetOptions(9940, android.os.Bundle().apply { putString(ADDITIONAL_WIDGET_KIND, "GRAPH") })
        manager.updateAppWidgetOptions(9941, android.os.Bundle().apply { putString(ADDITIONAL_WIDGET_KIND, "METABOLIC") })
        assertEquals(ConfigurableWidgetKind.GRAPH, configurableWidgetKind(context, 9940, "AdditionalWidgetReceiver"))
        assertEquals(ConfigurableWidgetKind.METABOLIC, configurableWidgetKind(context, 9941, "AdditionalWidgetReceiver"))
        assertEquals(ConfigurableWidgetKind.GLUCOSE, configurableWidgetKind(context, 9942, "AdditionalWidgetReceiver"))
        assertEquals(ConfigurableWidgetKind.GLUCOSE, configurableWidgetKind(context, 9940, "GlucoseFamily2x1Receiver"))
        assertEquals(ConfigurableWidgetKind.GRAPH, configurableWidgetKind(context, 9941, "GraphFamily2x1Receiver"))
    }

    @Test
    fun `split divider color and height persist per widget without changing outline`() {
        val config = WidgetInstanceConfiguration(splitDividerArgb = Color.LTGRAY, splitDividerHeightPercent = 75)
        WidgetInstanceConfigurationStore.save(context, 9930, config)
        val restored = WidgetInstanceConfigurationStore.read(context, 9930)
        assertEquals(Color.LTGRAY, restored.splitDividerArgb)
        assertEquals(75, restored.splitDividerHeightPercent)
        assertEquals(config.outlineArgb, restored.outlineArgb)
        assertEquals(90, WidgetInstanceConfigurationStore.read(context, 9931).splitDividerHeightPercent)
    }

    @Test
    fun `time axis defaults on and axis weights persist independently`() {
        assertTrue(WidgetInstanceConfiguration().showTimeAxis)
        assertFalse(WidgetInstanceConfiguration().graphTimeAxisBold)
        assertFalse(WidgetInstanceConfiguration().graphValueAxisBold)
        WidgetInstanceConfigurationStore.save(context, 9928, WidgetInstanceConfiguration(graphTimeAxisBold = true))
        val restored = WidgetInstanceConfigurationStore.read(context, 9928)
        assertTrue(restored.graphTimeAxisBold)
        assertFalse(restored.graphValueAxisBold)
    }

    @Test
    fun `detail text defaults bold without changing compact delta and age defaults`() {
        val defaults = WidgetInstanceConfiguration()
        assertTrue(defaults.tirBold)
        assertTrue(defaults.detailDeltaBold && defaults.detailAgeBold)
        assertFalse(defaults.deltaBold)
        assertFalse(defaults.ageBold)
    }

    @Test
    fun `detail bold switches remain independently editable and persisted`() {
        WidgetInstanceConfigurationStore.save(context, 9923, WidgetInstanceConfiguration(detailDeltaBold = false, detailAgeBold = false, tirBold = false))
        val saved = WidgetInstanceConfigurationStore.read(context, 9923)
        assertFalse(saved.detailDeltaBold || saved.detailAgeBold || saved.tirBold)
        assertTrue(WidgetInstanceConfigurationStore.read(context, 9924).detailDeltaBold)
    }

    @Test
    fun `detail font weights and all icon colors persist per widget and theme`() {
        val roles = listOf(WidgetColorRole.DELTA_ICON, WidgetColorRole.AGE_ICON, WidgetColorRole.TIR_ICON, WidgetColorRole.DELTA_SYMBOL, WidgetColorRole.AGE_SYMBOL, WidgetColorRole.TIR_SYMBOL)
        val saved =
            WidgetInstanceConfiguration(
                deltaBold = true,
                ageBold = true,
                tirBold = true,
                graphLabelBold = true,
                colorOverrides = roles.associateWith { Color.RED },
                lightColorOverrides = roles.associateWith { Color.BLUE },
            )
        WidgetInstanceConfigurationStore.save(context, 9921, saved)
        val restored = WidgetInstanceConfigurationStore.read(context, 9921)
        assertTrue(restored.deltaBold && restored.ageBold && restored.tirBold && restored.graphLabelBold)
        roles.forEach { role ->
            assertEquals(Color.RED, restored.resolvedAppearance(AppearanceMode.DARK).colorOverrides[role])
            assertEquals(Color.BLUE, restored.resolvedAppearance(AppearanceMode.LIGHT).colorOverrides[role])
        }
        assertTrue(WidgetInstanceConfigurationStore.read(context, 9922).tirBold)
    }

    @Test
    fun `new widgets start with fully opaque backgrounds in both themes`() {
        ConfigurableWidgetKind.entries.forEach { kind ->
            val value = WidgetSystemDefaults.forNewInstance(context, kind)
            AppearanceMode.entries.forEach { mode ->
                assertEquals("$kind $mode", 255, Color.alpha(value.resolvedAppearance(mode).backgroundArgb))
                if (mode == AppearanceMode.LIGHT) assertEquals("$kind light background", Color.WHITE, value.resolvedAppearance(mode).backgroundArgb)
            }
        }
    }

    @Test
    fun `fresh widgets use the approved provider specific templates`() {
        val glucose = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.GLUCOSE)
        assertEquals(WidgetShapeMode.PILL, glucose.shapeMode)
        assertEquals(110, glucose.trendScalePercent)

        val graph = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.GRAPH)
        assertTrue(graph.showTimeAxis)
        assertEquals(WidgetScaleMode.DYNAMIC, graph.scaleMode)
        assertEquals(.7f, graph.historicalDotOutlineWidthDp)
        assertEquals(1.25f, graph.currentDotOutlineWidthDp)

        val combined = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.GLUCOSE_GRAPH)
        assertTrue(combined.showTimeAxis)
        assertEquals(30, combined.cornerRadiusDp)
        assertEquals(38, combined.glucoseGraphValuePercent)
        assertEquals(.4f, combined.historicalDotOutlineWidthDp)
        assertEquals(1.3f, combined.currentDotOutlineWidthDp)

        val therapy = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC)
        assertEquals(WidgetShapeMode.PILL, therapy.shapeMode)
        assertEquals(20, therapy.therapyProgressAppearance.gapDp)
        assertEquals(TherapyProgressMetric.entries.toSet(), therapy.therapyProgressAppearance.enabledMetrics)
    }

    @Test
    fun `settings changes remain local until the explicit action pill saves them`() {
        val id = 9931
        val initial = WidgetInstanceConfiguration(graphHours = 3)
        val edited = initial.copy(graphHours = 6)
        WidgetInstanceConfigurationStore.save(context, id, initial)
        assertEquals(initial, resolvedWidgetConfigurationOnExit(initial, edited, save = false))
        assertEquals(initial, WidgetInstanceConfigurationStore.read(context, id))
        val saved = resolvedWidgetConfigurationOnExit(initial, edited, save = true)
        var published = false
        commitWidgetSettings(
            commit = { WidgetInstanceConfigurationStore.save(context, id, saved) },
            publish = {
                publishWidgetSurface(context, id, testNativeWidgetFrame(context, Color.BLUE)) { frame ->
                    // The new settings must already be committed when the completed view is sent.
                    assertEquals(6, WidgetInstanceConfigurationStore.read(context, id).graphHours)
                    val root = frame.apply(context, null)
                    assertTrue(root.findViewById<android.widget.ImageView>(R.id.widget_bitmap).drawable != null)
                    published = true
                }
            },
            rollback = { WidgetInstanceConfigurationStore.save(context, id, initial) },
        )
        assertTrue("Save must publish immediately, not wait for a data update", published)
        assertEquals(6, WidgetInstanceConfigurationStore.read(context, id).graphHours)
    }

    @Test
    fun `therapy progress appearance round trips independently per widget`() {
        val firstAppearance =
            TherapyProgressAppearance(
                enabledMetrics = setOf(TherapyProgressMetric.IOB, TherapyProgressMetric.BASAL),
                groupScalePercent = 124,
                gapDp = 11,
                global = TherapyMetricAppearance(ringThicknessPercent = 135, primaryTextPercent = 92),
                metricOverrides =
                    mapOf(
                        TherapyProgressMetric.IOB to TherapyMetricAppearance(overallScalePercent = 118, iconPercent = 74),
                        TherapyProgressMetric.BASAL to TherapyMetricAppearance(secondaryTextPercent = 141),
                    ),
            )
        val secondAppearance = TherapyProgressAppearance(enabledMetrics = setOf(TherapyProgressMetric.COB), gapDp = 2)

        WidgetInstanceConfigurationStore.save(context, 1201, WidgetInstanceConfiguration(therapyProgressAppearance = firstAppearance))
        WidgetInstanceConfigurationStore.save(context, 1202, WidgetInstanceConfiguration(therapyProgressAppearance = secondAppearance))

        assertEquals(firstAppearance, WidgetInstanceConfigurationStore.read(context, 1201).therapyProgressAppearance)
        assertEquals(secondAppearance, WidgetInstanceConfigurationStore.read(context, 1202).therapyProgressAppearance)
        WidgetInstanceConfigurationStore.delete(context, 1201)
        WidgetInstanceConfigurationStore.delete(context, 1202)
    }

    @Test
    fun `therapy progress settings reject disabling the final metric`() {
        val onlyIob = TherapyProgressAppearance(enabledMetrics = setOf(TherapyProgressMetric.IOB))

        assertEquals(onlyIob, toggleTherapyMetric(onlyIob, TherapyProgressMetric.IOB, enabled = false))
        assertEquals(
            setOf(TherapyProgressMetric.IOB, TherapyProgressMetric.COB),
            toggleTherapyMetric(onlyIob, TherapyProgressMetric.COB, enabled = true).enabledMetrics,
        )
    }

    @Test
    fun `corrupt therapy progress preferences recover into supported ranges`() {
        val id = 1203
        context
            .getSharedPreferences("widget_instance_configuration", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("$id.therapy.enabled", "UNKNOWN")
            .putInt("$id.therapy.group_scale", 999)
            .putInt("$id.therapy.gap_dp", -40)
            .putInt("$id.therapy.iob.icon", 999)
            .apply()

        val stored = WidgetInstanceConfigurationStore.read(context, id).therapyProgressAppearance
        assertEquals(TherapyProgressMetric.entries.toSet(), stored.enabledMetrics)
        assertEquals(150, stored.groupScalePercent)
        assertEquals(0, stored.gapDp)
        assertEquals(180, stored.metricOverrides.getValue(TherapyProgressMetric.IOB).iconPercent)
        WidgetInstanceConfigurationStore.delete(context, id)
    }

    @Test
    fun `configuration options follow the concrete widget provider`() {
        assertEquals(ConfigurableWidgetKind.GLUCOSE, configurableWidgetKind("app.sugarlicious.mobile.GlucoseWidgetReceiver"))
        assertEquals(ConfigurableWidgetKind.GLUCOSE, configurableWidgetKind("app.sugarlicious.mobile.GlucoseWidget2x2Receiver"))
        assertEquals(ConfigurableWidgetKind.GLUCOSE, configurableWidgetKind("app.sugarlicious.mobile.GlucoseWidget4x2Receiver"))
        assertEquals(ConfigurableWidgetKind.GRAPH, configurableWidgetKind("app.sugarlicious.mobile.GraphWidgetReceiver"))
        assertEquals(ConfigurableWidgetKind.GRAPH, configurableWidgetKind("app.sugarlicious.mobile.GraphWidget2x2Receiver"))
        assertEquals(ConfigurableWidgetKind.GRAPH, configurableWidgetKind("app.sugarlicious.mobile.GraphWidget4x2Receiver"))
        assertEquals(ConfigurableWidgetKind.GLUCOSE_GRAPH, configurableWidgetKind("app.sugarlicious.mobile.GlucoseGraphWidgetReceiver"))
        assertFalse(ConfigurableWidgetKind.GLUCOSE.hasGraph)
        assertTrue(ConfigurableWidgetKind.GRAPH.hasGraph)
    }

    @Test
    fun `provider presets retain their requested initial layout`() {
        assertEquals(WidgetFamilyLayout.COMPACT_2X1, widgetProviderPreset("app.sugarlicious.mobile.GlucoseWidgetReceiver"))
        assertEquals(WidgetFamilyLayout.DETAIL_2X2, widgetProviderPreset("app.sugarlicious.mobile.GlucoseWidget2x2Receiver"))
        assertEquals(WidgetFamilyLayout.WIDE_4X2, widgetProviderPreset("app.sugarlicious.mobile.GraphWidget4x2Receiver"))
    }

    @Test
    fun `target scale side follows actually visible predictions only`() {
        assertTrue(targetScaleOnRight(hasVisiblePredictions = false))
        assertFalse(targetScaleOnRight(hasVisiblePredictions = true))
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `existing widgets materialize every inherited setting once and remain isolated`() {
        val id = 9121
        val originalDark = Color.rgb(12, 34, 56)
        val changedDark = Color.rgb(210, 45, 67)
        try {
            WidgetColorStore.save(context, AppearanceMode.DARK, WidgetColorRole.DOT_IN_RANGE, originalDark)
            WidgetInstanceConfigurationStore.save(
                context,
                id,
                WidgetInstanceConfiguration(cgmDotRadiusDp = 5.25f),
            )

            val migrated =
                WidgetInstanceConfigurationStore.readIsolated(
                    context,
                    id,
                    ConfigurableWidgetKind.GRAPH,
                )
            assertEquals(WidgetColorRole.entries.toSet(), migrated.colorOverrides.keys)
            assertEquals(WidgetColorRole.entries.toSet(), migrated.lightColorOverrides.keys)
            assertEquals(originalDark, migrated.colorOverrides[WidgetColorRole.DOT_IN_RANGE])
            assertEquals(5.25f, migrated.cgmDotRadiusDp, 0.001f)
            assertFalse(migrated.darkTrendStyle.isEmpty)
            assertFalse(migrated.lightTrendStyle.isEmpty)
            assertTrue(migrated.darkTrendStyle.fillColor != null)
            assertTrue(migrated.darkTrendStyle.sizePercent != null)

            WidgetColorStore.save(context, AppearanceMode.DARK, WidgetColorRole.DOT_IN_RANGE, changedDark)
            val reread =
                WidgetInstanceConfigurationStore.readIsolated(
                    context,
                    id,
                    ConfigurableWidgetKind.GRAPH,
                )
            assertEquals(originalDark, reread.colorOverrides[WidgetColorRole.DOT_IN_RANGE])
            assertEquals(5.25f, reread.cgmDotRadiusDp, 0.001f)
            assertEquals(migrated.darkTrendStyle.fillColor, reread.darkTrendStyle.fillColor)
            assertEquals(migrated.darkTrendStyle.sizePercent, reread.darkTrendStyle.sizePercent)
        } finally {
            WidgetInstanceConfigurationStore.delete(context, id)
            WidgetColorStore.reset(context, AppearanceMode.DARK, WidgetColorRole.DOT_IN_RANGE)
        }
    }

    @Test
    fun `new widget captures a complete independent settings profile`() {
        val configuration = WidgetSystemDefaults.forNewInstance(context, ConfigurableWidgetKind.GLUCOSE_GRAPH)

        assertEquals(WidgetColorRole.entries.toSet(), configuration.colorOverrides.keys)
        assertEquals(WidgetColorRole.entries.toSet(), configuration.lightColorOverrides.keys)
        assertFalse(configuration.darkTrendStyle.isEmpty)
        assertFalse(configuration.lightTrendStyle.isEmpty)
        assertEquals(38, configuration.glucoseGraphValuePercent)
    }

    @Test
    fun `glucose widget supplement settings round trip independently per instance`() {
        val first =
            WidgetInstanceConfiguration(
                showDelta = true,
                showAge = true,
                deltaScalePercent = 115,
                deltaOffsetXDp = -4,
                deltaOffsetYDp = 3,
                ageScalePercent = 90,
                ageOffsetXDp = 2,
                ageOffsetYDp = -1,
                unitScalePercent = 85,
                unitOffsetXDp = 1,
                unitOffsetYDp = 4,
            )
        val second = WidgetInstanceConfiguration(showDelta = false, showAge = true, ageScalePercent = 130)

        WidgetInstanceConfigurationStore.save(context, 9231, first)
        WidgetInstanceConfigurationStore.save(context, 9232, second)

        assertEquals(first, WidgetInstanceConfigurationStore.read(context, 9231))
        assertEquals(second, WidgetInstanceConfigurationStore.read(context, 9232))
        WidgetInstanceConfigurationStore.delete(context, 9231)
        WidgetInstanceConfigurationStore.delete(context, 9232)
    }

    @Test
    fun `version one widget profile gains newly introduced supplement colors`() {
        val id = 9240
        val prefs = context.getSharedPreferences("widget_instance_configuration", android.content.Context.MODE_PRIVATE)
        WidgetInstanceConfigurationStore.save(context, id, WidgetInstanceConfiguration())
        prefs.edit().putInt("$id.isolated_profile_version", 1).commit()

        val migrated = WidgetInstanceConfigurationStore.readIsolated(context, id, ConfigurableWidgetKind.GLUCOSE)

        assertTrue(WidgetColorRole.DELTA in migrated.colorOverrides)
        assertTrue(WidgetColorRole.AGE in migrated.colorOverrides)
        assertTrue(WidgetColorRole.UNIT in migrated.colorOverrides)
        WidgetInstanceConfigurationStore.delete(context, id)
    }

    @Test
    fun `version two graph profile gains high region label color`() {
        val id = 9241
        val prefs = context.getSharedPreferences("widget_instance_configuration", android.content.Context.MODE_PRIVATE)
        WidgetInstanceConfigurationStore.save(context, id, WidgetInstanceConfiguration())
        prefs.edit().putInt("$id.isolated_profile_version", 2).commit()

        val migrated = WidgetInstanceConfigurationStore.readIsolated(context, id, ConfigurableWidgetKind.GRAPH)

        assertTrue(WidgetColorRole.HIGH_REGION_LABEL in migrated.colorOverrides)
        WidgetInstanceConfigurationStore.delete(context, id)
    }

    @Test
    fun `widget settings only keep edited values when save is chosen`() {
        val initial = WidgetInstanceConfiguration(graphHours = 3)
        val edited = initial.copy(graphHours = 24)

        assertEquals(initial, resolvedWidgetConfigurationOnExit(initial, edited, save = false))
        assertEquals(edited, resolvedWidgetConfigurationOnExit(initial, edited, save = true))
    }

    @Test
    fun `two widget instances retain independent appearance and graph settings`() {
        val first =
            WidgetInstanceConfiguration(
                6,
                true,
                WidgetScaleMode.DYNAMIC,
                Color.argb(120, 10, 20, 30),
                "app.sugarlicious",
                backgroundEnabled = false,
                graphCornerRadiusDp = 12,
                glucoseScalePercent = 88,
                trendScalePercent = 112,
                colorOverrides = mapOf(WidgetColorRole.TREND_HIGH to Color.MAGENTA),
            )
        val second =
            WidgetInstanceConfiguration(
                24,
                false,
                WidgetScaleMode.LOGARITHMIC,
                Color.BLACK,
                "com.dexcom.g7",
                graphCornerRadiusDp = 26,
                colorOverrides = mapOf(WidgetColorRole.DOT_IN_RANGE to Color.GREEN),
            )
        WidgetInstanceConfigurationStore.save(context, 101, first)
        WidgetInstanceConfigurationStore.save(context, 202, second)

        assertEquals(first, WidgetInstanceConfigurationStore.read(context, 101))
        assertEquals(second, WidgetInstanceConfigurationStore.read(context, 202))
        assertNotEquals(WidgetInstanceConfigurationStore.read(context, 101), WidgetInstanceConfigurationStore.read(context, 202))
    }

    @Test
    fun `one widget retains independent light and dark appearance profiles`() {
        val darkBackground = Color.rgb(7, 11, 18)
        val lightBackground = Color.rgb(242, 236, 219)
        val darkDot = Color.CYAN
        val lightDot = Color.BLUE
        val value =
            WidgetInstanceConfiguration(
                backgroundArgb = darkBackground,
                lightBackgroundArgb = lightBackground,
                backgroundEnabled = false,
                lightBackgroundEnabled = true,
                outlineEnabled = true,
                lightOutlineEnabled = false,
                colorOverrides = mapOf(WidgetColorRole.DOT_IN_RANGE to darkDot),
                lightColorOverrides = mapOf(WidgetColorRole.DOT_IN_RANGE to lightDot),
            )

        WidgetInstanceConfigurationStore.save(context, 212, value)
        val stored = WidgetInstanceConfigurationStore.read(context, 212)

        assertEquals(darkBackground, stored.resolvedAppearance(AppearanceMode.DARK).backgroundArgb)
        assertEquals(lightBackground, stored.resolvedAppearance(AppearanceMode.LIGHT).backgroundArgb)
        assertFalse(stored.resolvedAppearance(AppearanceMode.DARK).backgroundEnabled)
        assertTrue(stored.resolvedAppearance(AppearanceMode.LIGHT).backgroundEnabled)
        assertTrue(stored.resolvedAppearance(AppearanceMode.DARK).outlineEnabled)
        assertFalse(stored.resolvedAppearance(AppearanceMode.LIGHT).outlineEnabled)
        assertEquals(darkDot, stored.resolvedAppearance(AppearanceMode.DARK).colorOverrides[WidgetColorRole.DOT_IN_RANGE])
        assertEquals(lightDot, stored.resolvedAppearance(AppearanceMode.LIGHT).colorOverrides[WidgetColorRole.DOT_IN_RANGE])
        WidgetInstanceConfigurationStore.delete(context, 212)
    }

    @Test
    fun `widget trend overrides persist independently and inherit missing values`() {
        val parent = TrendArrowStyle.defaults(AppearanceMode.DARK, Color.WHITE)
        val value =
            WidgetInstanceConfiguration(
                trendScalePercent = 135,
                darkTrendStyle = TrendArrowStyleOverride(outlineEnabled = false, alpha = 0.55f),
                lightTrendStyle = TrendArrowStyleOverride(outlineColor = Color.MAGENTA, outlineThicknessDp = 1.25f),
            )
        WidgetInstanceConfigurationStore.save(context, 213, value)
        val stored = WidgetInstanceConfigurationStore.read(context, 213)

        assertFalse(stored.trendStyle(AppearanceMode.DARK, parent).outlineEnabled)
        assertEquals(0.55f, stored.trendStyle(AppearanceMode.DARK, parent).alpha, 0.001f)
        assertEquals(Color.MAGENTA, stored.trendStyle(AppearanceMode.LIGHT, parent).outlineColor)
        assertEquals(1.25f, stored.trendStyle(AppearanceMode.LIGHT, parent).outlineThicknessDp, 0.001f)
        assertEquals(135, stored.trendStyle(AppearanceMode.DARK, parent).sizePercent)
        WidgetInstanceConfigurationStore.delete(context, 213)
    }

    @Test
    fun `legacy global tap target is captured independently by each widget`() {
        val sugarlicious = WidgetLaunchTarget("app.sugarlicious", "Sugarlicious")
        val manufacturer = WidgetLaunchTarget("com.dexcom.g7", "Dexcom G7")
        try {
            WidgetLaunchTargetStore.select(context, manufacturer)
            val migrated = WidgetInstanceConfigurationStore.read(context, 707)
            assertEquals(manufacturer.packageName, migrated.launchPackage)
            WidgetInstanceConfigurationStore.save(context, 707, migrated)

            WidgetLaunchTargetStore.select(context, sugarlicious)
            assertEquals(manufacturer.packageName, WidgetInstanceConfigurationStore.read(context, 707).launchPackage)
            assertEquals(sugarlicious.packageName, WidgetInstanceConfigurationStore.read(context, 808).launchPackage)
        } finally {
            WidgetInstanceConfigurationStore.delete(context, 707)
            WidgetInstanceConfigurationStore.delete(context, 808)
            WidgetLaunchTargetStore.select(context, sugarlicious)
        }
    }

    @Test
    fun `deleting one widget leaves the other configuration intact`() {
        val first = WidgetInstanceConfiguration(graphHours = 12)
        val second = WidgetInstanceConfiguration(graphHours = 2)
        WidgetInstanceConfigurationStore.save(context, 303, first)
        WidgetInstanceConfigurationStore.save(context, 404, second)

        WidgetInstanceConfigurationStore.delete(context, 303)

        assertEquals(WidgetInstanceConfiguration(), WidgetInstanceConfigurationStore.read(context, 303))
        assertEquals(second, WidgetInstanceConfigurationStore.read(context, 404))
    }

    @Test
    fun `standard and pill shapes remain independent per widget instance`() {
        val standard = WidgetInstanceConfiguration(shapeMode = WidgetShapeMode.STANDARD, cornerRadiusDp = 24)
        val pill = WidgetInstanceConfiguration(shapeMode = WidgetShapeMode.PILL)
        WidgetInstanceConfigurationStore.save(context, 505, standard)
        WidgetInstanceConfigurationStore.save(context, 606, pill)

        assertEquals(standard, WidgetInstanceConfigurationStore.read(context, 505))
        assertEquals(pill, WidgetInstanceConfigurationStore.read(context, 606))
    }

    @Test
    fun `combined widget keeps value graph ratio and unit setting per instance`() {
        val compactValue =
            WidgetInstanceConfiguration(
                glucoseGraphValuePercent = 35,
                showGlucoseUnit = false,
                glucoseBold = false,
                deltaUnitBold = true,
                historicalDotOutlineEnabled = false,
                currentDotOutlineEnabled = true,
                historicalDotOutlineWidthDp = 0.65f,
                currentDotOutlineWidthDp = 1.8f,
            )
        val largeValue =
            WidgetInstanceConfiguration(
                glucoseGraphValuePercent = 42,
                showGlucoseUnit = true,
                glucoseBold = true,
                deltaUnitBold = false,
                historicalDotOutlineEnabled = true,
                currentDotOutlineEnabled = false,
                historicalDotOutlineWidthDp = 2.25f,
                currentDotOutlineWidthDp = 0.4f,
            )
        WidgetInstanceConfigurationStore.save(context, 909, compactValue)
        WidgetInstanceConfigurationStore.save(context, 910, largeValue)

        assertEquals(compactValue, WidgetInstanceConfigurationStore.read(context, 909))
        assertEquals(largeValue.copy(currentDotOutlineEnabled = true), WidgetInstanceConfigurationStore.read(context, 910))
        assertTrue(combinedWidgetTopHeight(400, 35) < combinedWidgetTopHeight(400, 42))
    }

    @Test
    fun `legacy oversized graph region migrates to balanced default`() {
        val id = 911
        context
            .getSharedPreferences("widget_instance_configuration", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("$id.glucose_graph_value_percent", 27)
            .apply()

        assertEquals(DEFAULT_COMBINED_WIDGET_VALUE_PERCENT, WidgetInstanceConfigurationStore.read(context, id).glucoseGraphValuePercent)
        WidgetInstanceConfigurationStore.delete(context, id)
    }

    @Test
    fun `pill radius tracks half of every resized widget height`() {
        val pill = WidgetInstanceConfiguration(shapeMode = WidgetShapeMode.PILL)
        assertEquals(40f, resolveWidgetCornerRadiusDp(pill, 80f), 0.01f)
        assertEquals(75f, resolveWidgetCornerRadiusDp(pill, 150f), 0.01f)
    }

    @Test
    fun `one by two graph widget supports pill shape`() {
        val pill = WidgetInstanceConfiguration(shapeMode = WidgetShapeMode.PILL)

        assertTrue(widgetSupportsPill(ConfigurableWidgetKind.GRAPH))
        assertEquals(
            55f,
            resolveWidgetCornerRadiusDp(
                configuration = pill,
                heightDp = 110f,
                pillAllowed = widgetSupportsPill(ConfigurableWidgetKind.GRAPH),
            ),
            0.01f,
        )
    }

    @Test
    fun `standard shape always uses deviceVendor system radius`() {
        assertEquals(
            DEVICE_VENDOR_WIDGET_RADIUS_FALLBACK_DP,
            resolveWidgetCornerRadiusDp(WidgetInstanceConfiguration(), 120f),
            0.01f,
        )
        assertEquals(
            31f,
            resolveWidgetCornerRadiusDp(
                WidgetInstanceConfiguration(cornerRadiusDp = 18),
                120f,
                systemDefaultDp = 31f,
            ),
            0.01f,
        )
    }

    @Test
    fun `all y scale modes keep target and readings inside the plot`() {
        val plot = android.graphics.RectF(0f, 0f, 100f, 100f)
        WidgetScaleMode.entries.forEach { mode ->
            val scale = widgetYScale(mode, listOf(55.0, 120.0, 260.0), 80.0, 160.0)
            listOf(55.0, 80.0, 120.0, 160.0, 260.0).forEach { value ->
                check(scale.map(value, plot) in 0f..100f)
            }
        }
    }

    @Test
    fun `dynamic widget scale does not reserve the sensor floor without low readings`() {
        val scale = widgetYScale(WidgetScaleMode.DYNAMIC, listOf(118.0, 123.0, 129.0), 80.0, 160.0)

        assertEquals(80.0 - scale.minimum, scale.maximum - 160.0, 0.001)
        assertTrue(scale.minimum > 40.0)
    }
}
