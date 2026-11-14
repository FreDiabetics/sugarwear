package app.sugarlicious.complications

import android.graphics.Color
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VigilHeaderLayoutTest {
    @Test fun `disconnected warning stays above graph without overview sensor actions`() {
        val service = Robolectric.buildService(DirectToWatchAnalogHeaderComplication::class.java).create().get()
        val presentation = DirectToWatchPresentationFormatter.header(null, 1_800_000_000_000L)
        listOf(false, true).forEach { ambient ->
            val analog = service.renderAnalogHeader(presentation, ambient)

            fun ink(top: Int, bottom: Int): Int = (top until bottom).sumOf { y -> (0 until analog.width).count { x -> Color.alpha(analog.getPixel(x, y)) > 0 } }
            org.junit.Assert.assertTrue(ink(22, 130) > 0)
            assertEquals(0, ink(140, 450))
        }
    }

    @Test fun `cached editor clock preview never expires into the system warning placeholder`() {
        val service = Robolectric.buildService(DirectToWatchClockComplication::class.java).create().get()
        val preview = service.getPreviewData(androidx.wear.watchface.complications.data.ComplicationType.SMALL_IMAGE)
        assertEquals(androidx.wear.watchface.complications.data.TimeRange.ALWAYS, preview.validTimeRange)
    }

    @Test fun `strong clock has visibly more ink than standard bold`() {
        val strong = renderWatchfaceText("12:42", 270, 78, 150, Color.WHITE, true, android.graphics.Paint.Align.CENTER)
        val standard = android.graphics.Bitmap.createBitmap(270, 78, android.graphics.Bitmap.Config.ARGB_8888)
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                textAlign = android.graphics.Paint.Align.CENTER
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        android.graphics.Canvas(standard).drawText("12:42", 135f, 39f - (paint.ascent() + paint.descent()) / 2f, paint)

        fun ink(bitmap: android.graphics.Bitmap): Long =
            (0 until bitmap.height).sumOf { y ->
                (0 until bitmap.width).sumOf { x -> Color.alpha(bitmap.getPixel(x, y)).toLong() }
            }
        org.junit.Assert.assertTrue("The clock must be visibly stronger than the existing 700-weight face", ink(strong) > ink(standard))
    }

    @Test fun `clock follows system twelve and twenty four hour preference`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val now = 1_800_000_000_000L
        listOf("12", "24").forEach { format ->
            android.provider.Settings.System
                .putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, format)
            assertEquals(
                android.text.format.DateFormat
                    .getTimeFormat(context)
                    .format(java.util.Date(now)),
                formatVigilSystemTime(context, now),
            )
        }
    }

    @Test fun `status switches between white and black with background luminance`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(DirectToWatchPreferences.NAME, android.content.Context.MODE_PRIVATE)
        listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE).forEach { (background, foreground) ->
            preferences.edit().putInt("graph_color_background", background).commit()
            assertEquals(foreground, vigilStatusContrastColor(null, 1_800_000_000_000L, context))
        }
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w226dp-h226dp-340dpi")
    fun `native Vigil preview uses the same centered header and graph providers`() {
        val service = Robolectric.buildService(DirectToWatchHeaderComplication::class.java).create().get()
        val graphService = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        // Preview-only palette matches the established selector artwork; user preferences and
        // production defaults are never changed by this deterministic fixture.
        service
            .getSharedPreferences(DirectToWatchPreferences.NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putFloat("target.low_mg_dl", 80f)
            .putFloat("target.high_mg_dl", 160f)
            .putInt("graph_color_cgm_in", Color.WHITE)
            .putInt("graph_color_background", 0xFF1C1C1C.toInt())
            .putInt("graph_color_range_in", 0xFF333333.toInt())
            .putInt("graph_color_high_line", 0xFFCDAD2A.toInt())
            .putInt("graph_color_low_line", 0xFFFF4D5E.toInt())
            .commit()
        val now = 1_800_000_000_000L
        val history = List(24) { index -> app.sugarlicious.model.GlucoseSample(valueMgDl = 120.0 + index * 32.0 / 23, measuredAtEpochMs = now - (23 - index) * 300_000L - 120_000L, source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH) }
        val state =
            app.sugarlicious.model.TherapyDisplayState(
                source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH,
                receivedAtEpochMs = now,
                sourceContract = "CANONICAL_CGM_V2:WATCH_DIRECT:test",
                glucose = app.sugarlicious.model.GlucoseState(valueMgDl = 152.0, displayUnit = app.sugarlicious.model.GlucoseUnit.MG_DL, trend = Trend.FLAT, measuredAtEpochMs = now - 120_000L, deltaMgDl = 1.0, source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH),
                glucoseHistory = history,
            )
        val header = service.renderHeader(DirectToWatchHeaderPresentation("152", "+1 mg/dL", Trend.FLAT))
        val graph = graphService.renderGraph(state, now, 2)
        val preview = android.graphics.Bitmap.createBitmap(450, 450, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(preview)
        canvas.drawColor(Color.BLACK)
        canvas.clipPath(android.graphics.Path().apply { addCircle(225f, 225f, 225f, android.graphics.Path.Direction.CW) })
        canvas.drawBitmap(header, 90f, 28f, null)
        canvas.drawBitmap(graph, 0f, 140f, null)
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        canvas.drawText("2h • 2m", 16f, 168f, paint)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.textSize = 56f
        canvas.drawText("12:42", 225f, 405f, paint)
        val output = java.io.File("build/reports/vigil-preview").apply { mkdirs() }
        listOf("preview.png" to preview, "header.png" to header, "graph.png" to graph).forEach { (name, bitmap) ->
            java.io
                .File(output, name)
                .outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(450, preview.width)
    }

    @Test
    fun `glucose trend and secondary line share the actual watchface center in active and ambient modes`() {
        val service = Robolectric.buildService(DirectToWatchHeaderComplication::class.java).create().get()
        listOf(false, true).forEach { ambient ->
            val bitmap = service.renderHeader(DirectToWatchHeaderPresentation("152", "+1 mg/dL", Trend.FLAT), ambient = ambient)
            listOf(0 until 70, 70 until 108).forEach { rows ->
                val xs = rows.flatMap { y -> (0 until bitmap.width).filter { x -> Color.alpha(bitmap.getPixel(x, y)) > 100 } }
                assertEquals("header content must center at face x225, local x135", 135f, (xs.min() + xs.max()) / 2f, 3f)
            }
        }
    }
}
