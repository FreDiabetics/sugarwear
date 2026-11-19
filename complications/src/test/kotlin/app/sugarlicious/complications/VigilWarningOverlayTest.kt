package app.sugarlicious.complications

import android.graphics.Color
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VigilWarningOverlayTest {
    private val now = 1_800_000_000_000L

    @Test fun `graph reaches both round display edges at the display middle`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val image = service.renderGraph(null, now, 2)
        assertTrue(Color.alpha(image.getPixel(0, 85)) > 0)
        assertTrue(Color.alpha(image.getPixel(449, 85)) > 0)
        assertEquals(0, Color.alpha(image.getPixel(0, 0)))
    }

    @Test fun `no sensor header has alert above message instead of an embedded pairing button`() {
        val service = Robolectric.buildService(DirectToWatchHeaderComplication::class.java).create().get()
        val image = service.renderHeader(DirectToWatchHeaderPresentation("-", "Bitte Sensor\nstarten oder\nkoppeln"))
        save(image, "inactive-header")
        assertTrue(Color.alpha(image.getPixel(135, 30)) > 0)
        assertEquals(0, Color.alpha(image.getPixel(135, 107)))
    }

    @Test fun `signal loss leaves graph colors untouched and uses an unboxed icon over white text below it`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val stale =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ACTIVE",
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, Trend.FLAT, now - 20 * 60_000L, source = DataSourceId.DEXCOM_G7_WATCH),
            )
        val fresh = stale.copy(glucose = stale.glucose!!.copy(measuredAtEpochMs = now))
        val oldGraph = service.renderGraph(stale, now, 2)
        val currentGraph = service.renderGraph(fresh, now, 2)
        for (y in listOf(8, 175)) assertEquals(currentGraph.getPixel(225, y), oldGraph.getPixel(225, y))
        val footer = renderVigilSignalLossFooter(service)
        save(footer, "signal-loss-footer")
        assertEquals(0, Color.alpha(footer.getPixel(0, 0)))
        assertEquals(0, Color.alpha(footer.getPixel(10, 60)))
        assertTrue((0 until 36).any { y -> (110 until 160).any { x -> Color.alpha(footer.getPixel(x, y)) > 0 } })
        assertTrue((42 until 78).any { y -> (10 until 260).any { x -> footer.getPixel(x, y) == Color.WHITE } })
        assertEquals(0, Color.alpha(oldGraph.getPixel(0, 0)))
    }

    @Test fun `empty direct state offers pairing consistently with warning header`() {
        val state = TherapyDisplayState(source = DataSourceId.DEXCOM_G7_WATCH, sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test", receivedAtEpochMs = now)
        assertTrue(vigilNeedsPairing(state, now))
    }

    @Test fun `paused collector has its own header and never becomes signal loss`() {
        val state =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                sourceContract = "CANONICAL_CGM_V2:NO_SOURCE:test:SENSOR_ACTIVE:SESSION_PAUSED:PROVIDER_OK",
                receivedAtEpochMs = now,
            )
        val header = DirectToWatchPresentationFormatter.header(state, now)
        assertTrue(header.collectorPaused)
        assertEquals("Datenempfang pausiert", header.secondary)
        org.junit.Assert.assertFalse(hasVigilSignalLoss(state, now))
        org.junit.Assert.assertFalse(vigilNeedsPairing(state, now))
    }

    @Test fun `signal loss value uses opaque gray glyphs without a strike line`() {
        val service = Robolectric.buildService(DirectToWatchHeaderComplication::class.java).create().get()
        val image = service.renderHeader(DirectToWatchHeaderPresentation("152", "", signalLoss = true))
        var gray = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getPixel(x, y) == 0xFF9E9E9E.toInt()) gray++
            }
        }
        assertTrue(gray > 100)
        save(image, "signal-loss-gray-value")
    }

    private fun save(image: android.graphics.Bitmap, name: String) {
        val directory = java.io.File("build/reports/vigil-warnings").apply { mkdirs() }
        java.io
            .File(directory, "$name.png")
            .outputStream()
            .use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
