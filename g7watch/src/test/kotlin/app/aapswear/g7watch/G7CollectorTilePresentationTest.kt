package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.SugarWearTypography
import app.aapswear.model.SugarWearTypographyRole
import app.aapswear.model.Trend
import app.aapswear.model.sugarWearTypographyParitySamples
import app.aapswear.protocol.WatchGraphColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class G7CollectorTilePresentationTest {
    private val now = 2_000_000L
    private val colors =
        WatchGraphColors(
            graphBackground = 0xFF111111.toInt(),
            rangeLow = 0xFFAA0000.toInt(),
            rangeHigh = 0xFFCCAA00.toInt(),
            cgmLow = 0xFFFF0000.toInt(),
            cgmHigh = 0xFFFFCC00.toInt(),
        )

    @Test
    fun `tile requests registered Samsung system family with portable fallback`() {
        assertEquals("sec", sugarWearTileFontFamily("Samsung"))
        assertEquals("sans-serif", sugarWearTileFontFamily("Google"))
    }

    @Test
    fun `tile emphasis is calibrated to match the in app system font`() {
        assertEquals(700, sugarWearTileWeight(true))
        assertEquals(400, sugarWearTileWeight(false))
    }

    @Test
    fun `collector and graph tiles use shared SugarWear typography roles`() {
        assertEquals(44f, SugarWearTypography.spec(SugarWearTypographyRole.GLUCOSE_VALUE).sizeSp, 0f)
        assertEquals(20f, SugarWearTypography.spec(SugarWearTypographyRole.META).sizeSp, 0f)
        assertEquals(11f, SugarWearTypography.spec(SugarWearTypographyRole.TILE_TITLE).sizeSp, 0f)
        assertEquals("sans-serif", SugarWearTypography.APP_FONT_FAMILY)
        assertEquals(SugarWearTypography.APP_FONT_FAMILY, SugarWearTypography.PROTO_FONT_FAMILY)
        SugarWearTypographyRole.entries.forEach { role ->
            assertEquals("role=$role", "sans-serif", SugarWearTypography.spec(role).fontFamily)
        }
        assertEquals(700, SugarWearTypography.spec(SugarWearTypographyRole.TILE_TITLE).protoWeight)
    }

    @Test
    fun `required parity strings remain real text supported by the shared family`() {
        assertEquals(
            listOf("188", "→", "-3 mg/dL · 2m", "VERBUNDEN"),
            sugarWearTypographyParitySamples(),
        )
    }

    @Test
    fun `signal loss keeps validated value and trend while invalid states stay neutral`() {
        val noData = g7TilePresentation(null, colors, now)
        assertEquals(G7_TILE_CARD_BACKGROUND, noData.cardBackground)
        assertNull(noData.trend)

        val stale = g7TilePresentation(reading(120.0, now - G7_SIGNAL_LOSS_AFTER_MS), colors, now)
        assertEquals("120 →", stale.value)
        assertEquals(G7_TILE_CARD_BACKGROUND, stale.cardBackground)
        assertEquals(0xFF9E9E9E.toInt(), stale.cardForeground)
        assertEquals(Trend.FLAT, stale.trend)

        val invalid = g7TilePresentation(reading(120.0, status = CgmReadingStatus.INVALID), colors, now)
        assertEquals("—", invalid.value)
        assertEquals(G7_TILE_CARD_BACKGROUND, invalid.cardBackground)

        val sensorError = g7TilePresentation(reading(0.0, status = CgmReadingStatus.SENSOR_ERROR), colors, now)
        assertEquals("—", sensorError.value)
        assertEquals(G7_TILE_CARD_BACKGROUND, sensorError.cardBackground)
    }

    @Test
    fun `glucose card stays neutral while value follows range color`() {
        val low = g7TilePresentation(reading(69.0), colors, now)
        val normal = g7TilePresentation(reading(123.0), colors, now)
        val high = g7TilePresentation(reading(181.0), colors, now)

        assertEquals(G7_TILE_CARD_BACKGROUND, low.cardBackground)
        assertEquals(G7_TILE_CARD_BACKGROUND, normal.cardBackground)
        assertEquals(G7_TILE_CARD_BACKGROUND, high.cardBackground)
        assertEquals(colors.cgmLow, low.cardForeground)
        assertEquals(G7_TILE_TEXT_PRIMARY, normal.cardForeground)
        assertEquals(colors.cgmHigh, high.cardForeground)
        assertEquals(G7_TILE_BACKGROUND, 0xFF181818.toInt())
        assertEquals(G7_TILE_CARD_BORDER, 0xFF404040.toInt())
    }

    @Test
    fun `tile shows vector trend delta unit and compact age without source`() {
        val presentation =
            g7TilePresentation(
                reading(123.0, now - 2 * 60_000L, delta = 5.0, trend = Trend.FORTY_FIVE_UP),
                colors,
                now,
            )

        assertEquals("123", presentation.tileValue)
        assertEquals(Trend.FORTY_FIVE_UP, presentation.trend)
        assertTrue(presentation.tileMeta.contains("+5"))
        assertTrue(presentation.tileMeta.contains("vor 2 Min"))
        assertTrue(presentation.tileMeta.contains("mg/dL"))
        assertFalse(presentation.tileMeta.contains("Watch Direct"))
        assertFalse(presentation.tileMeta.contains("min"))
        assertFalse(presentation.tileMeta.contains("Aktuell", ignoreCase = true))
    }

    @Test
    fun `collector app value shows the same validated trend beside glucose`() {
        val up =
            g7TilePresentation(
                reading(123.0, delta = 5.0, trend = Trend.FORTY_FIVE_UP),
                colors,
                now,
            )
        val doubleDown =
            g7TilePresentation(
                reading(98.0, delta = -9.0, trend = Trend.DOUBLE_DOWN),
                colors,
                now,
            )

        assertEquals("123 ↗", up.value)
        assertEquals("98 ⇊", doubleDown.value)
        assertEquals("+5 mg/dL", up.meta)
        assertEquals("vor 0 Min", up.age)
        assertEquals(up.cardBackground, up.background)
        assertEquals(up.cardForeground, up.foreground)
    }

    @Test
    fun `unknown trend never invents a tile or in-app arrow`() {
        val presentation = g7TilePresentation(reading(123.0, trend = Trend.UNKNOWN), colors, now)
        assertNull(presentation.trend)
        assertEquals("123", presentation.value)
        assertEquals("123", presentation.tileValue)
    }

    @Test
    fun `tile pill uses only stable SugarWear status`() {
        val connected = g7TileStatusPresentation(G7StatusPillState.CONNECTED)
        val signalLoss = g7TileStatusPresentation(G7StatusPillState.SIGNAL_LOSS)
        val sensorError = g7TileStatusPresentation(G7StatusPillState.SENSOR_ERROR)
        val noSensor = g7TileStatusPresentation(G7StatusPillState.NO_ACTIVE_SENSOR)

        assertEquals("VERBUNDEN", connected.label)
        assertEquals("SIGNALVERLUST", signalLoss.label)
        assertEquals("SENSORFEHLER", sensorError.label)
        assertEquals("KEIN AKTIVER SENSOR VERBUNDEN", noSensor.label)
        assertEquals(G7_TILE_ACCENT, connected.color)
        assertEquals(0x246DE892, withTileAlpha(G7_TILE_ACCENT, 36))
    }

    @Test
    fun `card foreground follows light and dark alarm backgrounds`() {
        assertEquals(G7_TILE_TEXT_DARK, tileForegroundFor(0xFFFFD040.toInt()))
        assertEquals(G7_TILE_TEXT_PRIMARY, tileForegroundFor(0xFF242424.toInt()))
    }

    private fun reading(
        value: Double,
        timestamp: Long = now,
        delta: Double? = null,
        trend: Trend = Trend.FLAT,
        status: CgmReadingStatus = CgmReadingStatus.VALID,
    ) = CgmReading(
        id = "reading-$value-$timestamp-$status",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = value,
        timestampEpochMs = timestamp,
        receivedAtEpochMs = timestamp,
        deltaMgDl = delta,
        trend = trend,
        status = status,
    )
}
