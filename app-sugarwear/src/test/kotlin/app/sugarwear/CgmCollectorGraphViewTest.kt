package app.sugarwear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CgmCollectorGraphViewTest {
    private val now = 20_000_000L
    private val highArea = Color.rgb(208, 72, 48)
    private val background = Color.rgb(25, 25, 25)

    @Test
    fun `SugarWear collector graph advances all measured timestamps with wall clock`() {
        val readingAt = now
        val atArrival = cgmCollectorGraphWindow(readingAt, 3)
        val oneMinuteLater = cgmCollectorGraphWindow(readingAt + 60_000L, 3)
        val fourMinutesLater = cgmCollectorGraphWindow(readingAt + 4 * 60_000L, 3)

        assertEquals(1f, atArrival.xFraction(readingAt), 0.0001f)
        assertTrue(oneMinuteLater.xFraction(readingAt) < atArrival.xFraction(readingAt))
        assertTrue(fourMinutesLater.xFraction(readingAt) < oneMinuteLater.xFraction(readingAt))
    }

    @Test
    fun `SugarWear collector graph uses measured time for delayed backfill`() {
        val measuredAt = now - 45 * 60_000L
        val receivedAt = now
        val window = cgmCollectorGraphWindow(now, 3)

        assertTrue(window.xFraction(measuredAt) < window.xFraction(receivedAt))
    }

    @Test
    fun `range fill in label gutter uses configured focus opacity`() {
        val graph =
            render(
                readings = listOf(reading("1", 120.0, now)),
                graphHours = 3,
                nowEpochMs = now,
                palette = testPalette(),
            )

        val gutter = graph.getPixel(380, 75)
        assertEquals(255, Color.alpha(gutter))
        assertTrue(gutter != background)
    }

    @Test
    fun `first high reading turns on configured high area`() {
        val oneHigh = render(listOf(reading("1", 166.0, now)))
        val twoHigh =
            render(
                listOf(
                    reading("1", 166.0, now - 5 * 60_000L),
                    reading("2", 171.0, now),
                ),
            )

        val x = 137
        val y = 12
        assertEquals(highArea, oneHigh.getPixel(x, y))
        assertEquals(highArea, twoHigh.getPixel(x, y))
    }

    @Test
    fun `return to target immediately removes high area`() {
        val graph =
            render(
                listOf(
                    reading("1", 166.0, now - 10 * 60_000L),
                    reading("2", 171.0, now - 5 * 60_000L),
                    reading("3", 150.0, now),
                ),
            )

        assertNotEquals(highArea, graph.getPixel(137, 12))
    }

    @Test
    fun `visual QA previews cover periods and range states`() {
        val previewNow = 2_000_000_000L
        val outputDir = File("build/reports/g7-visual-qa").apply { mkdirs() }
        val expectedFiles = mutableListOf<File>()

        CgmAppearanceStore.ALLOWED_GRAPH_HOURS.forEach { hours ->
            expectedFiles +=
                writePreview(
                    outputDir = outputDir,
                    fileName = "g7-collector-${hours}h.png",
                    bitmap =
                        render(
                            readings = previewReadings(hours, previewNow, listOf(122.0, 126.0)),
                            graphHours = hours,
                            nowEpochMs = previewNow,
                            palette = defaultPalette(),
                        ),
                )
        }

        expectedFiles +=
            writePreview(
                outputDir,
                "g7-collector-state-in-range.png",
                render(
                    readings = previewReadings(3, previewNow, listOf(118.0, 124.0)),
                    graphHours = 3,
                    nowEpochMs = previewNow,
                    palette = defaultPalette(),
                ),
            )
        expectedFiles +=
            writePreview(
                outputDir,
                "g7-collector-state-high.png",
                render(
                    readings = previewReadings(3, previewNow, listOf(168.0, 174.0)),
                    graphHours = 3,
                    nowEpochMs = previewNow,
                    palette = defaultPalette(),
                ),
            )
        expectedFiles +=
            writePreview(
                outputDir,
                "g7-collector-state-low.png",
                render(
                    readings = previewReadings(3, previewNow, listOf(74.0, 68.0)),
                    graphHours = 3,
                    nowEpochMs = previewNow,
                    palette = defaultPalette(),
                ),
            )

        assertEquals(CgmAppearanceStore.ALLOWED_GRAPH_HOURS.size + 3, expectedFiles.size)
        expectedFiles.forEach { file ->
            assertTrue("Missing visual QA preview: ${file.path}", file.isFile)
            assertTrue("Empty visual QA preview: ${file.path}", file.length() > 0L)
        }
    }

    @Test
    fun `scale and age contrast follows rendered high area including transparency`() {
        for ((area, expected) in listOf(Color.YELLOW to Color.BLACK, Color.DKGRAY to Color.WHITE, 0x10FFFF00 to Color.WHITE)) {
            val palette =
                CgmAppearancePalette(
                    CgmAppearanceRole.entries.associateWith {
                        when (it) {
                            CgmAppearanceRole.GRAPH_BACKGROUND -> Color.BLACK
                            CgmAppearanceRole.GRAPH_HIGH_AREA -> area
                            else -> it.defaultArgb
                        }
                    },
                )
            var actual: Int? = null
            render(listOf(reading("high", 210.0, now)), 3, now, palette) { actual = it }
            assertEquals("High-area color $area", expected, actual)
            render(listOf(reading("target", 110.0, now)), 3, now, palette) { actual = it }
            assertEquals("Target reading must remove high-area contrast", Color.WHITE, actual)
        }
    }

    private fun render(readings: List<CgmReading>): Bitmap =
        render(
            readings = readings,
            graphHours = 3,
            nowEpochMs = now,
            palette = testPalette(),
        )

    private fun render(
        readings: List<CgmReading>,
        graphHours: Int,
        nowEpochMs: Long,
        palette: CgmAppearancePalette,
        onHeaderColor: ((Int) -> Unit)? = null,
    ): Bitmap {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directSettings = CgmDirectToWatchSettingsStore(context)
        val currentColors = directSettings.graphColors()
        directSettings.saveGraphColors(
            currentColors.copy(
                graphBackground = palette.argb(CgmAppearanceRole.GRAPH_BACKGROUND),
                rangeInRange = palette.argb(CgmAppearanceRole.GRAPH_TARGET_AREA),
                rangeHigh = palette.argb(CgmAppearanceRole.GRAPH_HIGH_AREA),
                rangeLow = palette.argb(CgmAppearanceRole.GRAPH_LOW_AREA),
                highLine = palette.argb(CgmAppearanceRole.GRAPH_HIGH_LINE),
                lowLine = palette.argb(CgmAppearanceRole.GRAPH_LOW_LINE),
                cgmHigh = palette.argb(CgmAppearanceRole.GRAPH_DOT_HIGH),
                cgmInRange = palette.argb(CgmAppearanceRole.GRAPH_DOT_IN_RANGE),
                cgmLow = palette.argb(CgmAppearanceRole.GRAPH_DOT_LOW),
                outline = palette.argb(CgmAppearanceRole.GRAPH_DOT_OUTLINE),
                axisLabel = palette.argb(CgmAppearanceRole.GRAPH_AXIS_TEXT),
                axisTick = palette.argb(CgmAppearanceRole.GRAPH_GRID),
                divider = palette.argb(CgmAppearanceRole.GRAPH_TILE_BORDER),
            ),
        )
        directSettings.saveGraphHours(graphHours)
        directSettings.saveThresholds(
            app.sugarlicious.model.CgmThresholds(
                veryHighMgDl = 250.0,
                highMgDl = 160.0,
                lowMgDl = 80.0,
                veryLowMgDl = 55.0,
            ),
        )
        val view = CgmCollectorGraphView(context)
        view.onHeaderColorChanged = onHeaderColor
        view.bind(
            readings = readings,
            palette = palette,
            graphHours = graphHours,
            nowEpochMs = nowEpochMs,
        )
        view.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(150, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 400, 150)
        val bitmap = Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun testPalette(): CgmAppearancePalette {
        val colors =
            CgmAppearanceRole.entries.associateWith { it.defaultArgb }.toMutableMap().apply {
                this[CgmAppearanceRole.GRAPH_BACKGROUND] = background
                this[CgmAppearanceRole.GRAPH_HIGH_AREA] = highArea
                this[CgmAppearanceRole.GRAPH_TARGET_AREA] = Color.rgb(88, 88, 88)
                this[CgmAppearanceRole.GRAPH_HIGH_LINE] = Color.YELLOW
                this[CgmAppearanceRole.GRAPH_LOW_LINE] = Color.RED
                this[CgmAppearanceRole.GRAPH_GRID] = Color.TRANSPARENT
                this[CgmAppearanceRole.GRAPH_AXIS_TEXT] = Color.TRANSPARENT
                this[CgmAppearanceRole.GRAPH_TILE_BORDER] = Color.TRANSPARENT
            }
        return CgmAppearancePalette(colors)
    }

    private fun defaultPalette(): CgmAppearancePalette = CgmAppearancePalette(CgmAppearanceRole.entries.associateWith { it.defaultArgb })

    private fun previewReadings(
        hours: Int,
        previewNow: Long,
        terminalValues: List<Double>,
    ): List<CgmReading> {
        require(terminalValues.size == 2)
        val totalMinutes = hours * 60
        val history = mutableListOf<CgmReading>()
        var sequence = 0
        var minutesAgo = totalMinutes
        while (minutesAgo >= 10) {
            val value =
                when ((sequence / 4) % 4) {
                    0 -> 106.0 + (sequence % 4) * 2.0
                    1 -> 126.0 + (sequence % 4) * 3.0
                    2 -> 146.0 - (sequence % 4) * 4.0
                    else -> 116.0 - (sequence % 4) * 2.0
                }
            history += reading("preview-$hours-$sequence", value, previewNow - minutesAgo * 60_000L)
            minutesAgo -= 5
            sequence += 1
        }
        history += reading("preview-$hours-terminal-1", terminalValues[0], previewNow - 5 * 60_000L)
        history += reading("preview-$hours-terminal-2", terminalValues[1], previewNow)
        return history
    }

    private fun writePreview(
        outputDir: File,
        fileName: String,
        bitmap: Bitmap,
    ): File {
        val file = outputDir.resolve(fileName)
        FileOutputStream(file).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        return file
    }

    private fun reading(
        id: String,
        value: Double,
        measuredAt: Long,
    ) = CgmReading(
        id = id,
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = value,
        timestampEpochMs = measuredAt,
        receivedAtEpochMs = measuredAt + 1_000L,
        trend = Trend.FLAT,
        status = CgmReadingStatus.VALID,
    )
}
