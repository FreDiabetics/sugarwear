package app.aapswear.g7watch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
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
class G7CollectorGraphViewTest {
    private val now = 20_000_000L
    private val highArea = Color.rgb(208, 72, 48)
    private val background = Color.rgb(25, 25, 25)

    @Test
    fun `SugarWear collector graph advances all measured timestamps with wall clock`() {
        val readingAt = now
        val atArrival = g7CollectorGraphWindow(readingAt, 3)
        val oneMinuteLater = g7CollectorGraphWindow(readingAt + 60_000L, 3)
        val fourMinutesLater = g7CollectorGraphWindow(readingAt + 4 * 60_000L, 3)

        assertEquals(1f, atArrival.xFraction(readingAt), 0.0001f)
        assertTrue(oneMinuteLater.xFraction(readingAt) < atArrival.xFraction(readingAt))
        assertTrue(fourMinutesLater.xFraction(readingAt) < oneMinuteLater.xFraction(readingAt))
    }

    @Test
    fun `SugarWear collector graph uses measured time for delayed backfill`() {
        val measuredAt = now - 45 * 60_000L
        val receivedAt = now
        val window = g7CollectorGraphWindow(now, 3)

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

        G7AppearanceStore.ALLOWED_GRAPH_HOURS.forEach { hours ->
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

        assertEquals(G7AppearanceStore.ALLOWED_GRAPH_HOURS.size + 3, expectedFiles.size)
        expectedFiles.forEach { file ->
            assertTrue("Missing visual QA preview: ${file.path}", file.isFile)
            assertTrue("Empty visual QA preview: ${file.path}", file.length() > 0L)
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
        palette: G7AppearancePalette,
    ): Bitmap {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directSettings = G7DirectToWatchSettingsStore(context)
        val currentColors = directSettings.graphColors()
        directSettings.saveGraphColors(
            currentColors.copy(
                graphBackground = palette.argb(G7AppearanceRole.GRAPH_BACKGROUND),
                rangeInRange = palette.argb(G7AppearanceRole.GRAPH_TARGET_AREA),
                rangeHigh = palette.argb(G7AppearanceRole.GRAPH_HIGH_AREA),
                rangeLow = palette.argb(G7AppearanceRole.GRAPH_LOW_AREA),
                highLine = palette.argb(G7AppearanceRole.GRAPH_HIGH_LINE),
                lowLine = palette.argb(G7AppearanceRole.GRAPH_LOW_LINE),
                cgmHigh = palette.argb(G7AppearanceRole.GRAPH_DOT_HIGH),
                cgmInRange = palette.argb(G7AppearanceRole.GRAPH_DOT_IN_RANGE),
                cgmLow = palette.argb(G7AppearanceRole.GRAPH_DOT_LOW),
                outline = palette.argb(G7AppearanceRole.GRAPH_DOT_OUTLINE),
                axisLabel = palette.argb(G7AppearanceRole.GRAPH_AXIS_TEXT),
                axisTick = palette.argb(G7AppearanceRole.GRAPH_GRID),
                divider = palette.argb(G7AppearanceRole.GRAPH_TILE_BORDER),
            ),
        )
        directSettings.saveGraphHours(graphHours)
        directSettings.saveThresholds(
            app.aapswear.model.CgmThresholds(
                veryHighMgDl = 250.0,
                highMgDl = 160.0,
                lowMgDl = 80.0,
                veryLowMgDl = 55.0,
            ),
        )
        val view = G7CollectorGraphView(context)
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

    private fun testPalette(): G7AppearancePalette {
        val colors =
            G7AppearanceRole.entries.associateWith { it.defaultArgb }.toMutableMap().apply {
                this[G7AppearanceRole.GRAPH_BACKGROUND] = background
                this[G7AppearanceRole.GRAPH_HIGH_AREA] = highArea
                this[G7AppearanceRole.GRAPH_TARGET_AREA] = Color.rgb(88, 88, 88)
                this[G7AppearanceRole.GRAPH_HIGH_LINE] = Color.YELLOW
                this[G7AppearanceRole.GRAPH_LOW_LINE] = Color.RED
                this[G7AppearanceRole.GRAPH_GRID] = Color.TRANSPARENT
                this[G7AppearanceRole.GRAPH_AXIS_TEXT] = Color.TRANSPARENT
                this[G7AppearanceRole.GRAPH_TILE_BORDER] = Color.TRANSPARENT
            }
        return G7AppearancePalette(colors)
    }

    private fun defaultPalette(): G7AppearancePalette = G7AppearancePalette(G7AppearanceRole.entries.associateWith { it.defaultArgb })

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
