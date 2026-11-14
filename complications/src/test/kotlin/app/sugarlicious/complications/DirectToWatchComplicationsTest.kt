package app.sugarlicious.complications

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmSourceState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.protocol.DirectToWatchGraphColorDefaults
import app.sugarlicious.protocol.DirectToWatchSettingsContract
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DirectToWatchComplicationsTest {
    @Test fun `vigil live edge follows wall clock while measured timestamps keep every dot aligned`() {
        val readingAt = now
        val durationHours = 3
        val atArrival = directToWatchGraphWindow(readingAt, durationHours)
        val afterOneMinute = directToWatchGraphWindow(readingAt + 60_000L, durationHours)
        val afterFourMinutes = directToWatchGraphWindow(readingAt + 4 * 60_000L, durationHours)
        val afterFiveMinutes = directToWatchGraphWindow(readingAt + 5 * 60_000L, durationHours)

        assertEquals(1f, atArrival.xFraction(readingAt), 0.0001f)
        assertTrue(afterOneMinute.xFraction(readingAt) < atArrival.xFraction(readingAt))
        assertTrue(afterFourMinutes.xFraction(readingAt) < afterOneMinute.xFraction(readingAt))
        assertEquals(1f, afterFiveMinutes.xFraction(readingAt + 5 * 60_000L), 0.0001f)
        assertEquals(
            atArrival.xFraction(readingAt - 5 * 60_000L),
            afterFiveMinutes.xFraction(readingAt),
            0.0001f,
        )
    }

    @Test fun `vigil positions backfill only by measured time even when received now`() {
        val measuredAt = now - 45 * 60_000L
        val backfill =
            GlucoseSample(
                valueMgDl = 120.0,
                measuredAtEpochMs = measuredAt,
                receivedAtEpochMs = now,
                source = DataSourceId.DEXCOM_G7_WATCH,
            )
        val window = directToWatchGraphWindow(now, 3)

        assertEquals(window.xFraction(measuredAt), window.xFraction(backfill.measuredAtEpochMs), 0.0001f)
        assertTrue(window.xFraction(backfill.measuredAtEpochMs) < window.xFraction(now))
    }

    @Test fun `phone configured no source state remains AndroidAPS rather than Other`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fallback =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now - 20 * 60_000L,
                glucose = null,
            )

        val resolved =
            DirectSensorReadingResolver.resolve(
                context = context,
                fallback = fallback,
                nowEpochMs = now,
                dataSource = WatchDataSource.PHONE,
            )

        assertEquals(DataSourceId.ANDROID_APS, resolved?.source)
        assertEquals(CgmSourceState.NO_SOURCE, DirectSensorReadingResolver.sourceState(resolved))
    }

    private val now = 1_800_000_000_000L

    @Test fun `fresh watch direct renders glucose trend then delta and unit`() {
        val header = DirectToWatchPresentationFormatter.header(directState(now - 2 * 60_000L), now)
        assertEquals("152", header.glucose)
        assertEquals("+1 mg/dL", header.secondary)
        assertEquals(Trend.FLAT, header.trend)
        assertEquals("3h • 2m", DirectToWatchPresentationFormatter.graphStatus(directState(now - 2 * 60_000L), now, 3).text)
    }

    @Test fun `direct presentation applies its own mmol unit without changing source data`() {
        val state = directState(now - 2 * 60_000L)
        val header = DirectToWatchPresentationFormatter.header(state, now, GlucoseUnit.MMOL_L)
        assertEquals("8.4", header.glucose)
        assertEquals("+0.1 mmol/L", header.secondary)
        assertEquals(GlucoseUnit.MG_DL, state.glucose?.displayUnit)
    }

    @Test fun `direct boundary values replace number and trend only outside sensor range`() {
        fun header(value: Double) =
            DirectToWatchPresentationFormatter.header(
                directState(now - 60_000L).copy(glucose = directState(now - 60_000L).glucose?.copy(valueMgDl = value)),
                now,
            )
        assertEquals("NIEDRIG", header(39.0).glucose)
        assertEquals(null, header(39.0).trend)
        assertEquals("40", header(40.0).glucose)
        assertEquals("400", header(400.0).glucose)
        assertEquals("HOCH", header(401.0).glucose)
        assertEquals(null, header(401.0).trend)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `Vigil signal loss footer uses white text and a red alert icon`() {
        val service =
            org.robolectric.Robolectric
                .buildService(DirectToWatchHeaderComplication::class.java)
                .create()
                .get()
        val bitmap = renderVigilSignalLossFooter(service)
        assertTrue(
            (40 until 78).any { y ->
                (0 until bitmap.width).any { x ->
                    val pixel = bitmap.getPixel(x, y)
                    Color.alpha(pixel) > 200 && Color.red(pixel) > 230 && Color.green(pixel) > 230 && Color.blue(pixel) > 230
                }
            },
        )
        assertTrue(
            (0 until 36).any { y ->
                (110 until 160).any { x ->
                    val pixel = bitmap.getPixel(x, y)
                    Color.alpha(pixel) > 200 && Color.red(pixel) > 200 && Color.green(pixel) < 130
                }
            },
        )
    }

    @Test fun `signal loss retains last value and trend and marks only glucose`() {
        val header = DirectToWatchPresentationFormatter.header(directState(now - 16 * 60_000L), now)
        assertEquals("152", header.glucose)
        assertEquals("+1 mg/dL", header.secondary)
        assertEquals(Trend.FLAT, header.trend)
        assertTrue(header.signalLoss)
        assertEquals("3h • 16m", DirectToWatchPresentationFormatter.graphStatus(directState(now - 16 * 60_000L), now, 3).text)
    }

    @Test fun `mobile fresh is never shown as direct`() {
        val mobile =
            directState(now - 60_000L).copy(
                source = DataSourceId.ANDROID_APS,
                sourceContract = "CANONICAL_CGM_V2:MOBILE_PRIMARY:test",
                glucose = directState(now - 60_000L).glucose?.copy(source = DataSourceId.ANDROID_APS),
            )
        val header = DirectToWatchPresentationFormatter.header(mobile, now)
        assertEquals("-", header.glucose)
        assertEquals("Keine aktuellen\nGlukosewerte oder Alarme\nverfügbar", header.secondary)
        assertTrue(DirectToWatchPresentationFormatter.samples(mobile, now, 3).isEmpty())
    }

    @Test fun `absent data is explicit no source`() {
        val header = DirectToWatchPresentationFormatter.header(null, now)
        assertEquals("Kein Sensor verbunden", header.secondary)
        assertTrue(header.sensorDisconnected)
        assertEquals("3h", DirectToWatchPresentationFormatter.graphStatus(null, now, 3).text)
    }

    @Test fun `active restored session without a loaded reading shows loading state only`() {
        val state =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:restore:SENSOR_ACTIVE:SESSION_ACTIVE",
                receivedAtEpochMs = now - 60_000L,
                glucose = null,
                glucoseHistory = emptyList(),
            )

        val header = DirectToWatchPresentationFormatter.header(state, now)
        assertEquals("-", header.glucose)
        assertEquals("Keine aktuellen Daten\nBitte warten oder Verbindung\nzum Sensor prüfen", header.secondary)
        assertEquals("", DirectToWatchPresentationFormatter.graphStatus(state, now, 3).text)

        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val bitmap = service.renderGraph(state, now, 3)
        assertTrue((0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) != 0 } })
    }

    @Test fun `vigil distinguishes sensor error from an ended sensor in active and ambient data`() {
        val error =
            directState(now - 60_000L).copy(
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ERROR",
                glucose = directState(now - 60_000L).glucose?.copy(quality = CgmQuality.SENSOR_ERROR),
            )
        val ended =
            directState(now - 60_000L).copy(
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ENDED",
            )

        assertEquals("Sensorfehler", DirectToWatchPresentationFormatter.header(error, now).secondary)
        val presentation = DirectToWatchPresentationFormatter.header(ended, now)
        assertEquals("-", presentation.glucose)
        assertEquals("Kein Sensor verbunden", presentation.secondary)
        assertTrue(presentation.sensorDisconnected)
        assertEquals("3h", DirectToWatchPresentationFormatter.graphStatus(ended, now, 3).text)
    }

    @Test fun `vigil keeps disconnected graph visible without covering it with status`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val ended =
            directState(now - 60_000L).copy(
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ENDED",
            )
        val bitmap = service.renderGraph(ended, now, 3)

        assertTrue((0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) != 0 } })
        assertEquals("Kein Sensor verbunden", DirectToWatchPresentationFormatter.header(ended, now).secondary)
        val waiting = directState(now - 60_000L).copy(glucose = null, glucoseHistory = emptyList())
        assertTrue("Disconnected status belongs in the delta area, never over the graph", service.renderGraph(null, now, 3).sameAs(service.renderGraph(waiting, now, 3)))
    }

    @Test fun `vigil treats missing collector state as disconnected rather than signal loss`() {
        assertTrue(isVigilSensorDisconnected(null))
        val header = DirectToWatchPresentationFormatter.header(null, now)
        assertTrue(header.sensorDisconnected)
        assertEquals("Kein Sensor verbunden", header.secondary)
    }

    @Test fun `vigil keeps history visible and reports signal loss in header for stale direct data`() {
        val stale =
            directState(now - 20 * 60_000L).copy(
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ACTIVE",
            )

        assertTrue(DirectToWatchPresentationFormatter.header(stale, now).signalLoss)
        assertTrue(DirectToWatchPresentationFormatter.samples(stale, now, 3).isNotEmpty())
    }

    @Test fun `invalid delta is not invented`() {
        val state = directState(now - 60_000L).copy(glucose = directState(now - 60_000L).glucose?.copy(deltaMgDl = null))
        assertEquals("- mg/dL", DirectToWatchPresentationFormatter.header(state, now).secondary)
    }

    @Test fun `graph accepts only valid direct samples inside selected window`() {
        val state =
            directState(now - 60_000L).copy(
                glucoseHistory =
                    listOf(
                        GlucoseSample(110.0, now - 4 * 60 * 60_000L, source = DataSourceId.DEXCOM_G7_WATCH),
                        GlucoseSample(120.0, now - 2 * 60 * 60_000L, source = DataSourceId.DEXCOM_G7_WATCH),
                        GlucoseSample(130.0, now - 10 * 60_000L, source = DataSourceId.ANDROID_APS),
                        GlucoseSample(140.0, now - 5 * 60_000L, source = DataSourceId.DEXCOM_G7_WATCH, quality = CgmQuality.INVALID),
                    ),
            )
        val samples = DirectToWatchPresentationFormatter.samples(state, now, 3)
        assertTrue(samples.any { it.valueMgDl == 120.0 })
        assertTrue(samples.none { it.valueMgDl in setOf(110.0, 130.0, 140.0) })
    }

    @Test fun `vigil ambient graph is transparent outside target and grayscale`() {
        val service = Robolectric.buildService(DirectToWatchAmbientGraphComplication::class.java).create().get()
        val bitmap = service.renderGraph(directState(now - 60_000L), now, 3, ambient = true)
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width / 2, 8)))
        var coloredPixelFound = false
        for (y in 0 until bitmap.height step 5) {
            for (x in 0 until bitmap.width step 5) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) == 0) continue
                coloredPixelFound = true
                assertEquals(Color.red(pixel), Color.green(pixel))
                assertEquals(Color.green(pixel), Color.blue(pixel))
            }
        }
        assertTrue(coloredPixelFound)
    }

    @Test fun `vigil ambient palette preserves configured visibility`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val colors =
            DirectToWatchGraphColorDefaults.create().copy(
                nowLine = 0x00123456,
                highLine = 0x80123456.toInt(),
                cgmInRange = 0x40123456,
            )

        val ambient = with(service) { colors.ambient() }

        assertEquals(0, Color.alpha(ambient.nowLine))
        assertEquals(0x80, Color.alpha(ambient.highLine))
        assertEquals(0x40, Color.alpha(ambient.cgmInRange))
    }

    @Test fun `overlaid ambient vigil slots preserve the active tap actions`() {
        val ambientHeader = Robolectric.buildService(DirectToWatchAmbientHeaderComplication::class.java).create().get()
        val ambientGraph = Robolectric.buildService(DirectToWatchAmbientGraphComplication::class.java).create().get()

        org.junit.Assert.assertNull(ambientHeader.getPreviewData(ComplicationType.SMALL_IMAGE).tapAction)
        assertTrue(ambientGraph.getPreviewData(ComplicationType.SMALL_IMAGE).tapAction != null)
    }

    @Test fun `payload remains visible while renderer changes it to stale`() {
        val measuredAt = now - 2 * 60_000L
        val range = DirectToWatchPresentationFormatter.validTimeRange(directState(measuredAt), now)
        val staleAt = measuredAt + FreshnessPolicy.DELAYED_MAX_MS
        assertTrue(Instant.ofEpochMilli(staleAt) in range)
        assertTrue(Instant.ofEpochMilli(staleAt + 1) in range)
    }

    @Test fun `graph scale cycles in its own persistence file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(DirectToWatchPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        assertEquals(2, DirectToWatchPreferences.graphHours(context))
        assertEquals(3, DirectToWatchPreferences.cycleGraphHours(context))
        assertEquals(3, DirectToWatchPreferences.graphHours(context))
        assertFalse(context.getSharedPreferences("watch_display", Context.MODE_PRIVATE).contains("graph.hours"))
    }

    @Test fun `graph appearance persists and resets only in direct watch preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val direct = context.getSharedPreferences(DirectToWatchPreferences.NAME, Context.MODE_PRIVATE)
        val shared = context.getSharedPreferences("watch_display", Context.MODE_PRIVATE)
        direct.edit().clear().commit()
        shared.edit().putInt("graph_color_background", 0xFF010203.toInt()).commit()
        val referenceColors = DirectToWatchPreferences.graphColors(context)
        val referenceStyle = DirectToWatchPreferences.graphStyle(context)

        val colors =
            WatchGraphColors().copy(
                graphBackground = 0xFF112233.toInt(),
                rangeInRange = 0xFF445566.toInt(),
                cgmInRange = 0xFF778899.toInt(),
                cgmVeryHigh = 0xFFABCDEF.toInt(),
                predictionUam = 0xFF123456.toInt(),
            )
        val style =
            SharedWearCgmGraphStyle(
                dotRadiusDp = 4.2f,
                historicalDotOutlineEnabled = false,
                currentDotOutlineEnabled = true,
                dotOutlineWidthDp = 1.7f,
                cornerRadiusDp = 31f,
            )
        DirectToWatchPreferences.saveGraphColors(context, colors)
        DirectToWatchPreferences.saveGraphStyle(context, style)

        assertEquals(colors.graphBackground, DirectToWatchPreferences.graphColors(context).graphBackground)
        assertEquals(colors.rangeInRange, DirectToWatchPreferences.graphColors(context).rangeInRange)
        assertEquals(colors.cgmVeryHigh, DirectToWatchPreferences.graphColors(context).cgmVeryHigh)
        assertEquals(colors.predictionUam, DirectToWatchPreferences.graphColors(context).predictionUam)
        assertEquals(4.2f, DirectToWatchPreferences.graphStyle(context).dotRadiusDp)
        assertEquals(31f, DirectToWatchPreferences.graphStyle(context).cornerRadiusDp)
        assertFalse(DirectToWatchPreferences.graphStyle(context).historicalDotOutlineEnabled)
        assertTrue(DirectToWatchPreferences.graphStyle(context).currentDotOutlineEnabled)
        assertFalse(DirectToWatchPreferences.graphStyle(context).targetTicksEnabled)
        assertTrue(DirectToWatchPreferences.graphStyle(context).targetLabelsInsidePlot)
        assertEquals(0xFF010203.toInt(), shared.getInt("graph_color_background", 0))

        DirectToWatchPreferences.saveGraphStyle(context, style.copy(timeAxisEnabled = false))
        assertFalse(DirectToWatchPreferences.graphStyle(context).timeAxisEnabled)
        DirectToWatchPreferences.resetGraphAppearance(context)
        assertEquals(
            referenceColors.graphBackground,
            DirectToWatchPreferences.graphColors(context).graphBackground,
        )
        assertEquals(referenceStyle.dotRadiusDp, DirectToWatchPreferences.graphStyle(context).dotRadiusDp)
        assertFalse(DirectToWatchPreferences.graphStyle(context).borderEnabled)
        assertTrue(DirectToWatchPreferences.graphStyle(context).timeAxisEnabled)
        assertEquals(0xFF010203.toInt(), shared.getInt("graph_color_background", 0))
    }

    @Test fun `explicit settings handoff is consumed by runtime preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(DirectToWatchPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val values =
            Bundle().apply {
                putInt("appearance.trend.dark.sizePercent", 200)
                putFloat("appearance.trend.dark.alpha", .35f)
                putString("appearance.active_mode", "dark")
                putInt("watchface.clock_size_percent", 400)
                putBoolean("graph_style_maximum_scale_enabled", true)
                putInt("watchface.clock_color", 0xFF123456.toInt())
                putBoolean("watchface.clock_bold", true)
                putBoolean("graph_style_range_background_enabled", false)
                putInt("graph_color_background", 0xFF010203.toInt())
            }

        DirectToWatchSettingsReceiver().onReceive(
            context,
            Intent(DirectToWatchSettingsContract.ACTION_APPLY).putExtra(DirectToWatchSettingsContract.EXTRA_VALUES, values),
        )

        assertEquals(200, DirectToWatchPreferences.trendStyle(context, app.sugarlicious.model.AppearanceMode.DARK).sizePercent)
        assertEquals(.35f, DirectToWatchPreferences.trendStyle(context, app.sugarlicious.model.AppearanceMode.DARK).alpha)
        assertEquals(app.sugarlicious.model.AppearanceMode.DARK, DirectToWatchPreferences.activeAppearanceMode(context))
        assertEquals(400, DirectToWatchPreferences.clockSizePercent(context))
        assertTrue(DirectToWatchPreferences.graphStyle(context).maximumScaleEnabled)
        assertEquals(0xFF123456.toInt(), DirectToWatchPreferences.clockColor(context))
        assertTrue(DirectToWatchPreferences.clockBold(context))
        assertTrue(DirectToWatchPreferences.graphStyle(context).rangeBackgroundEnabled)
        assertEquals(0xFF010203.toInt(), DirectToWatchPreferences.graphColors(context).graphBackground)
    }

    private fun directState(measuredAt: Long) =
        TherapyDisplayState(
            source = DataSourceId.DEXCOM_G7_WATCH,
            sourceContract = "CANONICAL_CGM_V2:WATCH_DIRECT:test",
            receivedAtEpochMs = now,
            glucose =
                GlucoseState(
                    valueMgDl = 152.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FLAT,
                    measuredAtEpochMs = measuredAt,
                    deltaMgDl = 1.0,
                    source = DataSourceId.DEXCOM_G7_WATCH,
                ),
        )
}
