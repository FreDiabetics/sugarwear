package app.sugarlicious.wear

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.TherapyMetricAppearance
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.protocol.WatchGraphStyle
import app.sugarlicious.protocol.WatchUiColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearTileAppearanceStoreTest {
    @Test
    fun `therapy tile geometry round trips separately from colors`() {
        val expected =
            TherapyProgressAppearance(
                enabledMetrics = setOf(TherapyProgressMetric.COB, TherapyProgressMetric.BASAL),
                groupScalePercent = 117,
                gapDp = 9,
                global = TherapyMetricAppearance(ringThicknessPercent = 144),
                metricOverrides = mapOf(TherapyProgressMetric.COB to TherapyMetricAppearance(iconPercent = 155)),
            )

        TherapyTileGeometryStore.write(context, expected)

        val stored = TherapyTileGeometryStore.read(context)
        assertEquals(expected.enabledMetrics, stored.enabledMetrics)
        assertEquals(117, stored.groupScalePercent)
        assertEquals(9, stored.gapDp)
        assertEquals(144, stored.global.ringThicknessPercent)
        assertEquals(155, stored.metricOverrides.getValue(TherapyProgressMetric.COB).iconPercent)
        assertEquals(listOf(TherapyTileMetric.COB, TherapyTileMetric.BASAL), TherapyTileSelectionStore.read(context).canonical)
    }

    @Test
    fun `each system tile persists its content independently`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WearTileContentStore.write(context, WearTileKind.GLUCOSE, WearTileContent.GRAPH)

        assertEquals(WearTileContent.GRAPH, WearTileContentStore.read(context, WearTileKind.GLUCOSE))
    }

    @Test
    fun `therapy selection persists in canonical order and rejects empty state`() {
        TherapyTileSelectionStore.write(context, setOf(TherapyTileMetric.BASAL, TherapyTileMetric.IOB))

        assertEquals(listOf(TherapyTileMetric.IOB, TherapyTileMetric.BASAL), TherapyTileSelectionStore.read(context).metrics)
        assertThrows(IllegalArgumentException::class.java) { TherapyTileSelectionStore.write(context, emptySet()) }
    }

    @Test
    fun `legacy singleton therapy choice migrates without changing user selection`() {
        context
            .getSharedPreferences("wear_tile_content", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(WearTileKind.THERAPY.name, "COB")
            .commit()

        assertEquals(listOf(TherapyTileMetric.COB), TherapyTileSelectionStore.read(context).canonical)
        assertEquals(setOf("COB"), context.getSharedPreferences("wear_tile_content", 0).getStringSet("therapy.metrics.v1", null))
    }

    @Test
    fun `therapy layout is deterministic for one two and three metrics`() {
        assertEquals(
            listOf(TherapyTilePlacement(TherapyTileMetric.COB, 0, 0)),
            therapyTilePlacements(TherapyTileSelection(listOf(TherapyTileMetric.COB))),
        )
        assertEquals(
            listOf(TherapyTilePlacement(TherapyTileMetric.IOB, 0, 0), TherapyTilePlacement(TherapyTileMetric.COB, 0, 1)),
            therapyTilePlacements(TherapyTileSelection(listOf(TherapyTileMetric.COB, TherapyTileMetric.IOB))),
        )
        assertEquals(
            listOf(
                TherapyTilePlacement(TherapyTileMetric.IOB, 0, 0),
                TherapyTilePlacement(TherapyTileMetric.COB, 0, 1),
                TherapyTilePlacement(TherapyTileMetric.BASAL, 1, 0),
            ),
            therapyTilePlacements(TherapyTileSelection(TherapyTileMetric.entries)),
        )
    }

    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPreferences() {
        context
            .getSharedPreferences("wear_tile_content", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        WearTileKind.entries.forEach { kind ->
            context
                .getSharedPreferences(kind.preferenceName, android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        context
            .getSharedPreferences("wear_tile_therapy_geometry", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `glucose and therapy tile appearance are independent`() {
        val glucose = WatchUiColors(background = 0xFF112233.toInt(), glucoseHigh = 0xFF445566.toInt())
        val therapy = WatchUiColors(background = 0xFF778899.toInt(), iob = 0xFFAABBCC.toInt())

        WearTileAppearanceStore.write(context, WearTileKind.GLUCOSE, glucose)
        WearTileAppearanceStore.write(context, WearTileKind.THERAPY, therapy)

        assertEquals(glucose, WearTileAppearanceStore.read(context, WearTileKind.GLUCOSE))
        assertEquals(therapy, WearTileAppearanceStore.read(context, WearTileKind.THERAPY))
        assertNotEquals(
            WearTileAppearanceStore.read(context, WearTileKind.GLUCOSE).background,
            WearTileAppearanceStore.read(context, WearTileKind.THERAPY).background,
        )
    }

    @Test
    fun `tile writes do not mutate wear overview colors`() {
        val overview = WearDisplayPreferences(uiColors = WatchUiColors(background = 0xFF010203.toInt()))
        WearDisplayPreferences.saveLocal(context, overview)

        WearTileAppearanceStore.write(
            context,
            WearTileKind.GLUCOSE,
            WatchUiColors(background = 0xFFABCDEF.toInt()),
        )

        assertEquals(overview.uiColors, WearDisplayPreferences.read(context).uiColors)
    }

    @Test
    fun `delta and unit color remains isolated between wear overview and tile`() {
        val overview = WearDisplayPreferences(uiColors = WatchUiColors(deltaUnit = 0xFF112233.toInt()))
        WearDisplayPreferences.saveLocal(context, overview)
        WearTileAppearanceStore.write(
            context,
            WearTileKind.GLUCOSE,
            WatchUiColors(deltaUnit = 0x88445566.toInt()),
        )

        assertEquals(0xFF112233.toInt(), WearDisplayPreferences.read(context).uiColors.deltaUnit)
        assertEquals(0x88445566.toInt(), WearTileAppearanceStore.read(context, WearTileKind.GLUCOSE).deltaUnit)
    }

    @Test
    fun `wear graph persists current and historical outlines independently`() {
        val expected = WatchGraphStyle(cgmHistoricalDotOutlineEnabled = false, cgmCurrentDotOutlineEnabled = true)
        WearDisplayPreferences.saveLocal(context, WearDisplayPreferences(graphStyle = expected))
        assertEquals(expected, WearDisplayPreferences.read(context).graphStyle)
    }
}
