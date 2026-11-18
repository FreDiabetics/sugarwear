package app.sugarwear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CgmAlarmArtworkTest {
    @Test fun `all artwork uses native text with unclipped geometry and original black background`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = File("build/reports/alarm-artwork").apply { mkdirs() }
        CgmAlarmArtwork.entries.forEach { artwork ->
            val value =
                when (artwork) {
                    CgmAlarmArtwork.HIGH -> 202.0
                    CgmAlarmArtwork.LOW -> 68.0
                    else -> 90.0
                }
            val trend =
                when (artwork) {
                    CgmAlarmArtwork.RAPID_RISE -> app.sugarlicious.model.Trend.DOUBLE_UP
                    CgmAlarmArtwork.RAPID_FALL -> app.sugarlicious.model.Trend.DOUBLE_DOWN
                    CgmAlarmArtwork.HIGH -> app.sugarlicious.model.Trend.SINGLE_UP
                    else -> app.sugarlicious.model.Trend.SINGLE_DOWN
                }
            val reading =
                app.sugarlicious.sensor.protocol
                    .CgmReading("artwork-fixture", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "fixture", "fixture-session", value, 1L, 1L, trend = trend)
            val color =
                when (artwork) {
                    CgmAlarmArtwork.HIGH -> CgmAlarmDesignTokens.HIGH
                    CgmAlarmArtwork.LOW -> CgmAlarmDesignTokens.LOW
                    else -> android.graphics.Color.WHITE
                }
            val view = CgmAlarmFullBleedView(context, artwork, reading, color)
            val spec = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec)
            view.layout(0, 0, 450, 450)
            view.viewTreeObserver.dispatchOnPreDraw()
            val texts = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<TextView>()
            File(output, "${artwork.name.lowercase()}.txt").writeText(texts.joinToString("\n") { "${it.text} box=${it.left},${it.top},${it.right},${it.bottom} baseline=${it.baseline} size=${it.textSize} color=${it.currentTextColor} left=${it.layout.getLineLeft(0)} scroll=${it.scrollX},${it.scrollY}" })
            assertTrue(texts.isNotEmpty())
            texts.forEach { assertTrue(it.typeface.weight == 400 || it.typeface.weight == 700) }
            texts.forEach {
                assertTrue("$artwork: ${it.text} must fit horizontally", it.left >= 0 && it.right <= 450)
                assertTrue("$artwork: ${it.text} must fit vertically", it.top >= 0 && it.bottom <= 450)
                assertEquals("$artwork: native text must include its final glyph", it.text.length, it.layout.getLineEnd(0))
                assertTrue("$artwork: ${it.text} must fit its text container", it.paint.measureText(it.text.toString()) <= it.width + 1)
            }
            assertNotNull(view.findViewWithTag<View>("original-alarm-artwork"))
            val bitmap = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(0, 0))
            var outsideRoundDisplay = 0
            for (y in 0 until 450) {
                for (x in 0 until 450) {
                    val dx = x + .5f - 225f
                    val dy = y + .5f - 225f
                    if (dx * dx + dy * dy > 226f * 226f && bitmap.getPixel(x, y) and 0x00FFFFFF != 0) outsideRoundDisplay++
                }
            }
            assertEquals("$artwork: artwork must fit the circular display", 0, outsideRoundDisplay)
            File(output, "${artwork.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun `all alarm types use common artwork and validated prediction uses soon artwork`() {
        assertEquals(CgmAlarmArtwork.RAPID_FALL, cgmAlarmArtwork(SugarWearAlarmType.RAPID_FALL))
        assertEquals(CgmAlarmArtwork.TECHNICAL_WARNING, cgmAlarmArtwork(SugarWearAlarmType.TECHNICAL_WARNING))
        assertEquals(CgmAlarmArtwork.LOW_SOON, cgmAlarmArtwork(SugarWearAlarmType.URGENT_LOW_SOON))
    }

    @Test fun `tile renderer adds internal acknowledgement and preserves original geometry`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var acknowledged = false
        val reading =
            app.sugarlicious.sensor.protocol
                .CgmReading("tile", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 202.0, 1L, 1L, trend = app.sugarlicious.model.Trend.SINGLE_UP)
        val palette = CgmAppearanceStore(context).load()
        val full = CgmAlarmFullBleedView(context, CgmAlarmArtwork.HIGH, reading, palette.argb(CgmAppearanceRole.GLUCOSE_HIGH))
        val tile =
            CgmAlarmFullBleedView(context, CgmAlarmArtwork.HIGH, reading, palette.argb(CgmAppearanceRole.GLUCOSE_HIGH), mode = CgmAlarmContentMode.IN_GLUCOSE_TILE, onAcknowledge = { acknowledged = true }).apply {
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(palette.argb(CgmAppearanceRole.MENU_SURFACE))
                        cornerRadius = app.sugarlicious.model.WearGlucoseCardStyle.CARD_RADIUS_DP * resources.displayMetrics.density
                    }
                clipToOutline = true
            }
        val width = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
        full.measure(width, width)
        full.layout(0, 0, 450, 450)
        tile.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)
        assertEquals(full.findViewWithTag<View>("original-alarm-artwork").top, tile.findViewWithTag<View>("original-alarm-artwork").top)
        for (index in 0 until full.childCount) {
            val expected = full.getChildAt(index)
            val actual = tile.getChildAt(index)
            assertEquals(expected.left, actual.left)
            assertEquals(expected.top, actual.top)
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            if (expected is TextView && actual is TextView) {
                assertEquals(expected.text.toString(), actual.text.toString())
                assertEquals(expected.textSize, actual.textSize)
                assertEquals(expected.currentTextColor, actual.currentTextColor)
            }
        }
        assertNull(full.findViewWithTag<View>("alarm-acknowledge"))
        val button = tile.findViewWithTag<View>("alarm-acknowledge")
        assertNotNull(button)
        assertTrue(button.bottom <= tile.height)
        val nativeButton = button as TextView
        assertEquals(0, nativeButton.compoundPaddingTop)
        assertEquals(0, nativeButton.compoundPaddingBottom)
        assertTrue(nativeButton.layout.height <= nativeButton.height)
        button.performClick()
        assertTrue(acknowledged)
        val bitmap = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        tile.draw(Canvas(bitmap))
        File("build/reports/alarm-artwork/high-tile.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `rapid alarms render two styled native arrows and never invent a direction`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spec = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
        val style = app.sugarlicious.model.TrendArrowRenderSpec(0x8044CCFF.toInt(), 0xFF112233.toInt(), 2f, 1.2f)
        for ((artwork, trend) in listOf(CgmAlarmArtwork.RAPID_RISE to app.sugarlicious.model.Trend.DOUBLE_UP, CgmAlarmArtwork.RAPID_FALL to app.sugarlicious.model.Trend.DOUBLE_DOWN)) {
            val reading =
                app.sugarlicious.sensor.protocol
                    .CgmReading("rapid", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 100.0, 1L, 1L, trend = trend)
            val view = CgmAlarmFullBleedView(context, artwork, reading, trendStyle = style)
            view.measure(spec, spec)
            view.layout(0, 0, 450, 450)
            val arrows = (0 until view.childCount).map { view.getChildAt(it) }.filter { it.tag?.toString()?.startsWith("alarm-trend-") == true }
            assertEquals(10, arrows.size) // two fills, each above four outline layers
            val fills = arrows.filterIndexed { index, _ -> index == 4 || index == 9 }
            assertEquals(fills[0].width, fills[1].width)
            assertTrue(fills[1].left > fills[0].right)
            assertEquals(fills[0].top, fills[1].top)
            val number = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<TextView>().first { it.text.toString() == "100" }
            assertTrue(number.right < fills[0].left)
            assertEquals(225f, (number.left + fills[1].right) / 2f, 1f)
            val invalid = CgmAlarmFullBleedView(context, artwork, reading.copy(trend = app.sugarlicious.model.Trend.UNKNOWN), trendStyle = style)
            assertNull(invalid.findViewWithTag<View>("alarm-trend-0"))
        }
    }

    @Test fun `notification trend uses real icons and plain text never contains arrow glyphs`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reading =
            app.sugarlicious.sensor.protocol
                .CgmReading("notification", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 180.0, 10_000L, 10_000L, trend = app.sugarlicious.model.Trend.DOUBLE_UP)
        assertEquals("180 mg/dL", cgmAlarmValue(reading, 10_000L))
        assertNotNull(cgmAlarmTrendIcon(context, reading, SugarWearAlarmType.RAPID_RISE, 10_000L))
        assertNotNull(cgmAlarmTrendIcon(context, reading, SugarWearAlarmType.HIGH, 10_000L))
        assertNull(cgmAlarmTrendIcon(context, reading, SugarWearAlarmType.RAPID_FALL, 10_000L))
        assertNull(cgmAlarmTrendIcon(context, reading, SugarWearAlarmType.HIGH, 10_000L + CGM_SIGNAL_LOSS_AFTER_MS))
        assertNull(cgmAlarmTrendIcon(context, reading.copy(displayOnly = true), SugarWearAlarmType.HIGH, 10_000L))
        assertNull(cgmAlarmTrendIcon(context, reading.copy(trend = app.sugarlicious.model.Trend.UNKNOWN), SugarWearAlarmType.HIGH, 10_000L))
    }

    @Test fun `normal double trend keeps its wide asset proportions and centers the complete value row`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reading =
            app.sugarlicious.sensor.protocol
                .CgmReading("wide", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 202.0, 1L, 1L, trend = app.sugarlicious.model.Trend.DOUBLE_UP)
        val view = CgmAlarmFullBleedView(context, CgmAlarmArtwork.HIGH, reading)
        val spec = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 450, 450)
        val arrow = view.findViewWithTag<View>("alarm-trend-0")
        val number = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<TextView>().first { it.text.toString() == "202" }
        assertEquals(125f / 60f, arrow.width.toFloat() / arrow.height, .03f)
        assertTrue(number.right < arrow.left)
        assertEquals(225f, (number.left + arrow.right) / 2f, 1f)
    }

    @Test fun `saved arrow sizes preserve a clear value gap for every known trend`() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spec = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
        for (scale in listOf(.7f, 1.5f, 2f)) {
            for (trend in app.sugarlicious.model.Trend.entries
                .filter { it != app.sugarlicious.model.Trend.UNKNOWN }) {
                val reading =
                    app.sugarlicious.sensor.protocol
                        .CgmReading("sizing", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 202.0, 1L, 1L, trend = trend)
                val style = app.sugarlicious.model.TrendArrowRenderSpec(0x8044CCFF.toInt(), 0xFF112233.toInt(), 2f, scale)
                val view = CgmAlarmFullBleedView(context, CgmAlarmArtwork.HIGH, reading, trendStyle = style)
                view.measure(spec, spec)
                view.layout(0, 0, 450, 450)
                val arrow = view.findViewWithTag<View>("alarm-trend-0")
                val number = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<TextView>().first { it.text.toString() == "202" }
                assertTrue("$trend at $scale: value and arrow must not overlap", number.right < arrow.left)
                assertEquals(225f, (number.left + arrow.right) / 2f, 1f)
                assertEquals(number.text.length, number.layout.getLineEnd(0))
            }
        }
    }

    @Test fun `rapid and technical presentations omit the alarm suffix everywhere`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (type in listOf(SugarWearAlarmType.RAPID_RISE, SugarWearAlarmType.RAPID_FALL, SugarWearAlarmType.TECHNICAL_WARNING)) {
            assertFalse(cgmAlarmHeadline(type).contains("Alarm"))
            val view = CgmAlarmFullBleedView(context, cgmAlarmArtwork(type)!!)
            val labels = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<TextView>()
            assertFalse(labels.any { it.text.contains("Alarm") })
        }
    }

    @Test fun `high and low notification accents follow the saved appearance roles`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val palette = CgmAppearanceStore(context).load()
        for ((type, role, id) in listOf(Triple(SugarWearAlarmType.HIGH, CgmAppearanceRole.GLUCOSE_HIGH, 7104), Triple(SugarWearAlarmType.LOW, CgmAppearanceRole.GLUCOSE_LOW, 7103))) {
            CgmAlarmNotifier.show(context, SugarWearAlarmInstance(type, SugarWearAlarmState.ACTIVE), SugarWearAlarmDefaults.defaultSettings(), onlyAlertOnce = true)
            val notification =
                org.robolectric.Shadows
                    .shadowOf(manager)
                    .getNotification(id)
            assertNotNull(notification)
            assertEquals(palette.argb(role), notification.color)
            assertEquals(R.drawable.ic_cgm_notification, notification.smallIcon.resId)
            assertNull(notification.getLargeIcon())
        }
    }

    @Test fun `native trend icons preserve the saved fill transparency`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reading =
            app.sugarlicious.sensor.protocol
                .CgmReading("alpha", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 202.0, 1L, 1L, trend = app.sugarlicious.model.Trend.SINGLE_UP)
        val style = app.sugarlicious.model.TrendArrowRenderSpec(0x80FFFFFF.toInt(), android.graphics.Color.BLACK, 0f, 1f)
        val view = CgmAlarmFullBleedView(context, CgmAlarmArtwork.HIGH, reading, trendStyle = style)
        val spec = View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 450, 450)
        val arrow = view.findViewWithTag<View>("alarm-trend-0")
        val bitmap = Bitmap.createBitmap(arrow.width, arrow.height, Bitmap.Config.ARGB_8888)
        arrow.draw(Canvas(bitmap))
        val alpha = (0 until bitmap.height).maxOf { y -> (0 until bitmap.width).maxOf { x -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) } }
        assertEquals(128, alpha)
        bitmap.recycle()
    }

    @Test fun `technical warnings never show glucose trend or a glucose unit`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reading =
            app.sugarlicious.sensor.protocol
                .CgmReading("technical", app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 202.0, 1L, 1L, trend = app.sugarlicious.model.Trend.DOUBLE_UP)
        for (mode in CgmAlarmContentMode.entries) {
            val view = CgmAlarmFullBleedView(context, CgmAlarmArtwork.TECHNICAL_WARNING, reading, mode = mode, onAcknowledge = {})
            val children = (0 until view.childCount).map { view.getChildAt(it) }
            val texts = children.filterIsInstance<TextView>().map { it.text.toString() }
            assertFalse(texts.any { it.contains("mg/dL") || it.contains("202") })
            assertFalse(children.any { it.tag?.toString()?.startsWith("alarm-trend-") == true })
        }
        CgmAlarmNotifier.show(context, SugarWearAlarmInstance(SugarWearAlarmType.TECHNICAL_WARNING, SugarWearAlarmState.ACTIVE), SugarWearAlarmDefaults.defaultSettings(), onlyAlertOnce = true, reading = reading)
        val notification =
            org.robolectric.Shadows
                .shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
                .getNotification(7109)
        assertNull(notification.getLargeIcon())
        assertFalse(
            notification.extras
                .getCharSequence(android.app.Notification.EXTRA_TEXT)
                .toString()
                .contains("mg/dL"),
        )
        assertNull(cgmAlarmTrendIcon(context, reading, SugarWearAlarmType.TECHNICAL_WARNING, 1L))
    }

    @Test fun `connection cue claim survives new objects and rejects duplicate operation`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("cgm_connection_cue", 0)
            .edit()
            .clear()
            .commit()
        assertTrue(CgmConnectionCue.claim(context, "resume:1:s:session"))
        assertFalse(CgmConnectionCue.claim(context, "resume:1:s:session"))
        assertTrue(CgmConnectionCue.claim(context, "resume:2:s:session"))
    }
}
