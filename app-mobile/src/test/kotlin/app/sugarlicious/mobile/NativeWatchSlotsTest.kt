package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeWatchSlotsTest {
    @Test
    fun `external and system providers cannot be edited or assigned a variant`() {
        val slot = NativeWatchSlot(1, setOf("SHORT_TEXT"), NativeSlotBounds("BoundingBox", 0f, 0f, 100f, 100f))
        for (provider in listOf("", "com.samsung.health/Steps", "com.google.weather/Weather", "app.aapswear.other/app.sugarlicious.complications.GlucoseComplication")) {
            val external = slot.copy(provider = provider)
            assertTrue(!isNativeSlotEditable(external))
            assertTrue(eligibleNativeVariants(external).isEmpty())
        }
        val own = slot.copy(provider = "app.sugarlicious/app.sugarlicious.complications.GlucoseComplication")
        assertTrue(isNativeSlotEditable(own))
        assertTrue(eligibleNativeVariants(own).isNotEmpty())
    }

    @Test
    fun `curved preview glyphs center on the native text circle`() {
        assertEquals(10f, nativeCircularTextBaselineOffset(-28f, 8f), .001f)
    }

    @Test
    fun `Orbit inner isolated previews share the outer inner slot geometry`() {
        val slots = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots)
        val outer = slots.single { it.id == 4 }
        val inner = slots.single { it.id == 5 }
        val normalized = nativeIsolatedPreviewSlot(2, inner, outer.bounds)
        assertEquals(outer.bounds.width, normalized.bounds.width, .01f)
        val outerText =
            outer.templates
                .getValue("SHORT_TEXT")
                .texts
                .first()
        val innerText =
            normalized.templates
                .getValue("SHORT_TEXT")
                .texts
                .first()
        assertEquals(outerText.bounds.width, innerText.bounds.width, .01f)
        assertEquals(inner, nativeIsolatedPreviewSlot(0, inner, outer.bounds))
        assertEquals(5, normalized.id)
    }

    @Test
    fun `Orbit lower left preview preserves the 280 degree counterclockwise track and clockwise fill`() {
        val slot = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots).single { it.id == 3 }
        for (type in listOf("RANGED_VALUE", "GOAL_PROGRESS")) {
            val template = slot.templates.getValue(type)
            val track = requireNotNull(template.track)
            assertEquals(-280f, nativeArcSweep(track.startAngle, track.endAngle, template.trackClockwise), .001f)
            assertEquals(220f, requireNotNull(template.fillStartAngle), .001f)
            assertTrue(template.fillClockwise)
            assertEquals(19.5f, template.trackGapDegrees, .001f)
        }
        assertEquals(57f, nativeArcSweep(284f, 341f, true), .001f)
        assertEquals(-90f, nativeArcSweep(180f, 90f, false), .001f)
    }

    @Test
    fun `isolated slot icons and tracks resolve each face palette and native track alpha`() {
        val apex =
            source("sugarlicious-analog")
                .inputStream()
                .use(::readNativeWatchSlots)
                .single { it.id == 0 }
                .templates
                .getValue("RANGED_VALUE")
        val orbit =
            source("sugarlicious-orbit")
                .inputStream()
                .use(::readNativeWatchSlots)
                .single { it.id == 0 }
                .templates
                .getValue("RANGED_VALUE")
        assertEquals(0xFFFFCCB1.toInt(), apex.iconColor)
        assertEquals(0x37FF935C, apex.trackColor)
        assertEquals(0xFFFF5F0D.toInt(), apex.fillColor)
        assertEquals(0xFFD4DAB6.toInt(), orbit.iconColor)
        assertEquals(0x3CA4AF73, orbit.trackColor)
        assertEquals(0xFF758544.toInt(), orbit.fillColor)
    }

    @Test
    fun `slot display swaps preserve each native slot identity`() {
        val apex = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        val orbit = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots)
        assertEquals(listOf(0, 1, 4, 5, 3, 2, 6), nativeSlotEditorOrder(0, apex).map { it.id })
        assertEquals(listOf(0, 7, 4, 5, 3, 2), nativeSlotEditorOrder(2, orbit).map { it.id })
        assertTrue(nativeSlotEditorOrder(0, apex).all { it === apex.single { original -> original.id == it.id } })
    }

    @Test
    fun `circular ranged glucose trend uses matching proportions and a separate canonical icon`() {
        val apex = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots).single { it.id == 6 }
        val orbit = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots).single { it.id == 3 }
        for (slot in listOf(apex, orbit)) {
            val template = nativeCircularGlucoseTrendTemplate(slot, slot.templates.getValue("RANGED_VALUE"), app.sugarlicious.model.Trend.DOUBLE_UP)
            val text = template.texts.single()
            val icon = requireNotNull(template.icon)
            assertEquals(.24f, text.size / slot.bounds.width, .001f)
            assertEquals(.48f, (text.bounds.y + text.bounds.height / 2 - slot.bounds.y) / slot.bounds.height, .001f)
            assertEquals(.85f, (icon.y + icon.height / 2 - slot.bounds.y) / slot.bounds.height, .001f)
            assertEquals(.145f, icon.height / slot.bounds.height, .001f)
            assertEquals(125f / 60f, icon.width / icon.height, .001f)
        }
        assertEquals("123", nativeDemoValue(PhonePreview("123", "", trend = app.sugarlicious.model.Trend.FORTY_FIVE_UP), hasTrendIcon = true))
        assertEquals(app.sugarlicious.uishared.R.drawable.ic_trend_fortyfiveup, nativeTrendIconResource(app.sugarlicious.model.Trend.FORTY_FIVE_UP))
        assertEquals(null, nativeTrendIconResource(app.sugarlicious.model.Trend.UNKNOWN))
    }

    @Test
    fun `slot previews preserve measured native character spacing`() {
        val slots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        assertTrue(
            slots
                .single { it.id == 1 }
                .templates
                .getValue("RANGED_VALUE")
                .texts
                .all { it.letterSpacing == -.03f },
        )
        assertTrue(
            slots
                .single { it.id == 0 }
                .templates
                .getValue("RANGED_VALUE")
                .texts
                .all { it.letterSpacing == 0f },
        )
    }

    @Test
    fun `native font colors use captured WFF default palette roles`() {
        val apex =
            source("sugarlicious-analog")
                .inputStream()
                .use(::readNativeWatchSlots)
                .single { it.id == 4 }
                .templates
                .getValue("SHORT_TEXT")
        assertEquals(0xFFFFCCB1.toInt(), apex.texts.single { it.expression.contains("TEXT") }.color)
        assertEquals(0xFFA4AF73.toInt(), apex.texts.single { it.expression.contains("TITLE") }.color)
        val orbit =
            source("sugarlicious-orbit")
                .inputStream()
                .use(::readNativeWatchSlots)
                .single { it.id == 3 }
                .templates
                .getValue("SHORT_TEXT")
        assertEquals(0xFFD4DAB6.toInt(), orbit.texts.single { it.expression.contains("TEXT") }.color)
        assertEquals(0xFFA4AF73.toInt(), orbit.texts.single { it.expression.contains("TITLE") }.color)
        assertEquals(0xFF123456.toInt(), resolveNativeFontColor("#FF123456", emptyMap()))
        assertEquals(0xFF123456.toInt(), resolveNativeFontColor("[COMPLICATION.RANGED_VALUE_COLORS]", mapOf("materialAccent" to listOf(0xFF123456.toInt()))))
    }

    @Test
    fun `native delta provider retains its canonical age and delta presentation`() {
        val slots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        assertEquals(app.sugarlicious.model.SugarliciousComplicationIds.TIME_DELTA, nativeDefaultVariant(slots.single { it.provider.endsWith(".GlucoseDeltaComplication") }))
    }

    @Test
    fun `Apex conditional branches produce one recentered text value when title is absent`() {
        val middle = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots).single { it.id == 4 }
        for (type in listOf("SHORT_TEXT", "RANGED_VALUE")) {
            val resolved = resolveNativeTemplateText(middle.templates.getValue(type), "", "1.20 U")
            assertEquals(1, resolved.size)
            assertEquals("1.2\u2009U", resolved.single().value)
            assertEquals(
                232.5f,
                resolved
                    .single()
                    .template.bounds.y,
                .01f,
            )
        }
    }

    @Test
    fun `Apex title and value retain their distinct native positions without duplicate alternatives`() {
        val middle = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots).single { it.id == 4 }
        val resolved = resolveNativeTemplateText(middle.templates.getValue("SHORT_TEXT"), "1.2 U", "15 g")
        assertEquals(2, resolved.size)
        assertEquals(setOf("1.2\u2009U", "15\u2009g"), resolved.map { it.value }.toSet())
        assertEquals(setOf(210f, 255f), resolved.map { it.template.bounds.y }.toSet())
        val date = resolveNativeTemplateText(middle.templates.getValue("SHORT_TEXT"), "", "Sonntag")
        assertEquals("Sonntag", date.single().value)
    }

    @Test
    fun `demo progress reflects displayed provider values and native gauge limits`() {
        assertEquals(.12f, nativeDemoProgress(app.sugarlicious.model.SugarliciousComplicationIds.IOB_RANGED, "1.2 U", "")!!, .001f)
        assertEquals(.1f, nativeDemoProgress(app.sugarlicious.model.SugarliciousComplicationIds.COB_RANGED, "15 g", "")!!, .001f)
        assertEquals(.76f, nativeDemoProgress(null, "76%", "WATCH_BATTERY")!!, .001f)
        assertEquals(.654f, nativeDemoProgress(null, "6.540", "STEP_COUNT")!!, .001f)
        assertEquals(null, nativeDemoProgress(app.sugarlicious.model.SugarliciousComplicationIds.SENSOR_AGE_RANGED, "—", ""))
    }

    @Test
    fun `dynamic templates retain native Orbit text radius independently of touch bounds`() {
        val slots = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots)
        val upperLeft = slots.single { it.id == 0 }
        val text =
            upperLeft.templates
                .getValue("RANGED_VALUE")
                .texts
                .first()
        assertEquals(375.46f, text.bounds.width, .01f)
        assertEquals(32f, text.size, .01f)
        assertTrue(text.bounds.width < upperLeft.bounds.width)
        assertTrue(upperLeft.templates.getValue("RANGED_VALUE").icon != null)
    }

    @Test
    fun `native candidates match each slot supported data formats`() {
        val slots = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots)
        val outer = slots.single { it.id == 0 }
        assertTrue(eligibleNativeVariants(outer).all { it.type.name in outer.supportedTypes })
        assertTrue(eligibleNativeVariants(outer).none { it.type == ComplicationVariantType.SMALL_IMAGE })
    }

    @Test
    fun `upper left ink mask rejects pixels from lower left quadrant`() {
        assertEquals(315f, nativePreviewAngle(-1f, -1f, 270f), .01f)
        assertTrue(nativePreviewAngle(-1f, 1f, 270f) > 360f)
        assertEquals(45f, nativePreviewAngle(1f, -1f, 0f), .01f)
        assertEquals(135f, nativePreviewAngle(1f, 1f, 90f), .01f)
        assertEquals(225f, nativePreviewAngle(-1f, 1f, 180f), .01f)
    }

    @Test
    fun `outer preview pills add breathing space without changing watchface hit targets`() {
        val original =
            source("sugarlicious-analog")
                .inputStream()
                .use(::readNativeWatchSlots)
                .single { it.id == 0 }
                .bounds
        val preview = nativeSlotPreviewBounds(original)
        assertEquals(original.thickness + 24f, preview.thickness, 0f)
        assertEquals(64f, original.thickness, 0f)
        assertEquals(original.startAngle, preview.startAngle, 0f)
        val circle = NativeSlotBounds("BoundingOval", 0f, 0f, 123f, 123f)
        assertEquals(circle, nativeSlotPreviewBounds(circle))
    }

    @Test
    fun `expanded content never crosses adjoining outer slots but uses free arc gaps`() {
        val apexSlots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        val apex = apexSlots.map { it.bounds }
        val a = nativeSlotPreviewContentBounds(apexSlots.single { it.id == 0 }.bounds, apex)
        assertEquals(270f, a.startAngle, 0f)
        assertEquals(360f, a.endAngle, 0f)
        val orbit = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots).map { it.bounds }
        val o = nativeSlotPreviewContentBounds(orbit[0], orbit)
        assertEquals(272f, o.startAngle, 0f)
        assertEquals(352f, o.endAngle, 0f)
    }

    @Test
    fun `slot previews omit fixed graph and retain native editor names and scene order`() {
        val slots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        assertEquals((0..6).toSet(), nativeSlotPreviewEntries(slots).map { it.id }.toSet())
        assertEquals("Outer upper left", slots.single { it.id == 0 }.displayName)
        val orbit = projectFile("app-mobile/build/generated/nativeWatchSlotRes/raw/native_orbit_slots.xml").inputStream().use(::readNativeWatchSlots)
        assertEquals(listOf(0, 7, 2, 3, 4, 5), nativeSlotPreviewEntries(orbit).map { it.id })
        assertEquals(listOf("Oben links", "Oben rechts", "Unten rechts", "Unten links", "Innen außen", "Innen"), nativeSlotPreviewEntries(orbit).map { it.displayName })
    }

    @Test
    fun `isolated previews retain native provider and format without watchface artwork`() {
        val slots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        assertTrue(slots.single { it.id == 0 }.provider.endsWith("IobRangedValueComplication"))
        assertEquals("RANGED_VALUE", slots.single { it.id == 0 }.providerType)
        assertEquals("WATCH_BATTERY", slots.single { it.id == 1 }.systemProvider)
    }

    @Test
    fun `all editable scene slots are shown without flavor duplicates or fixed internal providers`() {
        val apex = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        val orbit = source("sugarlicious-orbit").inputStream().use(::readNativeWatchSlots)
        assertEquals((0..6).toSet(), apex.map { it.id }.toSet())
        assertEquals(setOf(0, 2, 3, 4, 5, 7), orbit.map { it.id }.toSet())
        assertEquals(7, apex.size)
        assertEquals(6, orbit.size)
        assertTrue(apex.all { it.supportedTypes.isNotEmpty() })
    }

    @Test
    fun `highlight uses native local oval and outer arc bounds on the shared canvas`() {
        val slots = source("sugarlicious-analog").inputStream().use(::readNativeWatchSlots)
        val arc = slots.single { it.id == 1 }
        assertEquals(0f, arc.bounds.startAngle, 0f)
        assertEquals(90f, arc.bounds.endAngle, 0f)
        assertEquals(440f, arc.bounds.width, 0f)
        val circle = slots.single { it.id == 4 }
        assertEquals(83f, circle.bounds.x, 0f)
        assertEquals(195f, circle.bounds.y, 0f)
        assertEquals(123f, circle.bounds.width, 0f)
    }

    private fun source(face: String): File = projectFile("watchfaces/$face/src/main/res/raw/watchface.xml")

    private fun projectFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return listOf(File(cwd, path), File(cwd.parentFile, path)).first(File::isFile)
    }
}
