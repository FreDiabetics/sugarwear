package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.SugarWearTypography
import app.sugarlicious.model.SugarWearTypographyRole
import app.sugarlicious.model.Trend
import app.sugarlicious.model.sugarWearTypographyParitySamples
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmCollectorTilePresentationTest {
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
    fun `tile requests registered DeviceVendor system family with portable fallback`() {
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
        val noData = cgmTilePresentation(null, colors, now)
        assertEquals(CGM_TILE_CARD_BACKGROUND, noData.cardBackground)
        assertNull(noData.trend)

        val stale = cgmTilePresentation(reading(120.0, now - CGM_SIGNAL_LOSS_AFTER_MS), colors, now)
        assertEquals("120 →", stale.value)
        assertEquals(CGM_TILE_CARD_BACKGROUND, stale.cardBackground)
        assertEquals(0xFF9E9E9E.toInt(), stale.cardForeground)
        assertEquals(Trend.FLAT, stale.trend)

        val invalid = cgmTilePresentation(reading(120.0, status = CgmReadingStatus.INVALID), colors, now)
        assertEquals("—", invalid.value)
        assertEquals(CGM_TILE_CARD_BACKGROUND, invalid.cardBackground)

        val sensorError = cgmTilePresentation(reading(0.0, status = CgmReadingStatus.SENSOR_ERROR), colors, now)
        assertEquals("—", sensorError.value)
        assertEquals(CGM_TILE_CARD_BACKGROUND, sensorError.cardBackground)
    }

    @Test
    fun `glucose card stays neutral while value follows range color`() {
        val low = cgmTilePresentation(reading(69.0), colors, now)
        val normal = cgmTilePresentation(reading(123.0), colors, now)
        val high = cgmTilePresentation(reading(181.0), colors, now)

        assertEquals(CGM_TILE_CARD_BACKGROUND, low.cardBackground)
        assertEquals(CGM_TILE_CARD_BACKGROUND, normal.cardBackground)
        assertEquals(CGM_TILE_CARD_BACKGROUND, high.cardBackground)
        assertEquals(colors.cgmLow, low.cardForeground)
        assertEquals(CGM_TILE_TEXT_PRIMARY, normal.cardForeground)
        assertEquals(colors.cgmHigh, high.cardForeground)
        assertEquals(CGM_TILE_BACKGROUND, 0xFF181818.toInt())
        assertEquals(CGM_TILE_CARD_BORDER, 0xFF404040.toInt())
    }

    @Test
    fun `tile shows vector trend delta unit and compact age without source`() {
        val presentation =
            cgmTilePresentation(
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
            cgmTilePresentation(
                reading(123.0, delta = 5.0, trend = Trend.FORTY_FIVE_UP),
                colors,
                now,
            )
        val doubleDown =
            cgmTilePresentation(
                reading(98.0, delta = -9.0, trend = Trend.DOUBLE_DOWN),
                colors,
                now,
            )

        assertEquals("123 ↗", up.value)
        assertEquals("98 ⇊", doubleDown.value)
        assertEquals("+5 mg/dL", up.meta)
        assertEquals("vor 0 Min.", up.age)
        assertEquals(up.cardBackground, up.background)
        assertEquals(up.cardForeground, up.foreground)
    }

    @Test
    fun `unknown trend never invents a tile or in-app arrow`() {
        val presentation = cgmTilePresentation(reading(123.0, trend = Trend.UNKNOWN), colors, now)
        assertNull(presentation.trend)
        assertEquals("123", presentation.value)
        assertEquals("123", presentation.tileValue)
    }

    @Test
    fun `tile pill uses only stable SugarWear status`() {
        val connected = cgmTileStatusPresentation(CgmStatusPillState.CONNECTED)
        val signalLoss = cgmTileStatusPresentation(CgmStatusPillState.SIGNAL_LOSS)
        val sensorError = cgmTileStatusPresentation(CgmStatusPillState.SENSOR_ERROR)
        val noSensor = cgmTileStatusPresentation(CgmStatusPillState.NO_ACTIVE_SENSOR)

        assertEquals("VERBUNDEN", connected.label)
        assertEquals("SIGNALVERLUST", signalLoss.label)
        assertEquals("SENSORFEHLER", sensorError.label)
        assertEquals("KEIN AKTIVER SENSOR VERBUNDEN", noSensor.label)
        assertEquals(CGM_TILE_ACCENT, connected.color)
        assertEquals(0x246DE892, withTileAlpha(CGM_TILE_ACCENT, 36))
    }

    @Test
    fun `card foreground follows light and dark alarm backgrounds`() {
        assertEquals(CGM_TILE_TEXT_DARK, tileForegroundFor(0xFFFFD040.toInt()))
        assertEquals(CGM_TILE_TEXT_PRIMARY, tileForegroundFor(0xFF242424.toInt()))
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
