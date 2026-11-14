package app.sugarlicious.wear

import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ResourceBuilders.IMAGE_FORMAT_RGB_565
import androidx.wear.tiles.RequestBuilders
import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseBarRange
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyIndicatorIcon
import app.sugarlicious.model.Trend
import app.sugarlicious.protocol.WatchUiColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarliciousTilesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now = 50_000_000L
    private val colors =
        WatchUiColors(
            glucoseLow = 0xFFAA0000.toInt(),
            glucoseInRange = 0xFF00AA00.toInt(),
            glucoseHigh = 0xFFAAAA00.toInt(),
        )

    @Test
    fun `tile emphasis is calibrated to the Wear app system font`() {
        assertEquals(700, sugarliciousTileWeight(true))
        assertEquals(400, sugarliciousTileWeight(false))
    }

    @Test
    fun `glucose tile keeps value trend and source separate and explicit`() {
        val presentation = wearGlucoseTilePresentation(state(123.0, now - 2 * 60_000L), colors, now)

        assertEquals("123", presentation.value)
        assertEquals(Trend.FORTY_FIVE_UP, presentation.trend)
        assertTrue(presentation.meta.contains("mg/dL"))
        assertTrue(presentation.footer.contains("AndroidAPS"))
        assertEquals(colors.glucoseInRange, presentation.valueColor)
        assertEquals("AKTUELL", presentation.status)
    }

    @Test
    fun `glucose tile uses the Mobile progress interval and stacked metadata`() {
        val presentation = wearGlucoseTilePresentation(state(123.0, now - 2 * 60_000L), colors, now)

        assertEquals(GlucoseBarRange.TARGET, presentation.barState?.range)
        assertEquals("vor 2 Min.", presentation.ageText)
        assertEquals("+5 mg/dL", presentation.deltaText)
    }

    @Test
    fun `stale tile keeps its explicit status but hides the active progress marker`() {
        val presentation = wearGlucoseTilePresentation(state(123.0, now - 20 * 60_000L), colors, now)

        assertEquals("SIGNALVERLUST", presentation.status)
        assertEquals(null, presentation.barState)
    }

    @Test
    fun `glucose tile registers the shared Kotlin progress bar as an inline image`() {
        WearTileContentStore.write(context, WearTileKind.GLUCOSE, WearTileContent.GLUCOSE)
        val device =
            DeviceParameters
                .Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .setScreenDensity(2f)
                .build()
        val service = Robolectric.buildService(GlucoseTileService::class.java).create().get()
        val request =
            RequestBuilders.TileRequest
                .Builder()
                .setDeviceConfiguration(device)
                .build()

        service.onTileRequest(request).get()
        val progress =
            request.scope
                .collectResources()
                .idToImageMapping
                .getValue("glucose_range_progress")
                .inlineResource!!
        assertEquals(IMAGE_FORMAT_RGB_565, progress.format)
        assertEquals(progress.widthPx * progress.heightPx * 2, progress.data.size)
        service.onDestroy()
    }

    @Test
    fun `signal loss tile preserves last validated values and labels signal loss`() {
        val stale = wearGlucoseTilePresentation(state(123.0, now - 20 * 60_000L), colors, now)
        val therapy = wearTherapyTilePresentation(state(123.0, now - 20 * 60_000L), now)

        assertEquals("123", stale.value)
        assertEquals(Trend.FORTY_FIVE_UP, stale.trend)
        assertEquals("SIGNALVERLUST", stale.status)
        assertTrue(therapy.displayable)
        assertEquals("1.20U", therapy.iob)
        assertEquals("18g", therapy.cob)
        assertEquals("0.70U/h", therapy.basal)
        assertTrue(therapy.footer.contains("letzter Stand"))
    }

    @Test
    fun `therapy tile exposes three independent modern metric cards`() {
        val presentation = wearTherapyTilePresentation(state(123.0, now - 60_000L), now)

        assertTrue(presentation.displayable)
        assertEquals("1.20U", presentation.iob)
        assertEquals("18g", presentation.cob)
        assertEquals("0.70U/h", presentation.basal)
    }

    @Test
    fun `therapy tile displays negative and zero AndroidAPS IOB instead of a dash`() {
        val negative = wearTherapyTilePresentation(state(123.0, now).copy(insulin = InsulinState(totalIob = -0.1)), now)
        val zero = wearTherapyTilePresentation(state(123.0, now).copy(insulin = InsulinState(totalIob = 0.0)), now)

        assertEquals("-0.10U", negative.iob)
        assertEquals("0.00U", zero.iob)
    }

    @Test
    fun `therapy rings expose values without duplicate metric headings`() {
        val presentation = wearTherapyTilePresentation(state(123.0, now - 60_000L), now)

        assertEquals("1.20U", therapyMetricValue(TherapyTileMetric.IOB, presentation))
        assertEquals("18g", therapyMetricValue(TherapyTileMetric.COB, presentation))
        assertEquals("0.70U", therapyMetricValue(TherapyTileMetric.BASAL, presentation))
    }

    @Test
    fun `enlarged therapy tile rings balance radial margins on round displays`() {
        for (side in listOf(168f, 180f, 204f)) {
            val appearance = app.sugarlicious.model.TherapyProgressAppearance()
            val original = app.sugarlicious.model.resolveRoundTherapyLayout(side, appearance, side * .58f)
            val adjusted = centeredTherapyTileLayout(side, appearance)
            val center = side / 2f
            adjusted.forEachIndexed { index, item ->
                assertTrue(item.slot.diameterDp > original[index].slot.diameterDp)
                assertTrue(kotlin.math.hypot(item.slot.centerXDp - center, item.slot.centerYDp - center) + item.slot.diameterDp / 2f <= center + .001f)
            }
            val radialExtents = adjusted.map { kotlin.math.hypot(it.slot.centerXDp - center, it.slot.centerYDp - center) + it.slot.diameterDp / 2f }
            assertEquals(radialExtents[0], radialExtents[1], .001f)
            assertEquals(radialExtents[0], radialExtents[2], .01f)
        }
    }

    @Test
    fun `three ring group is derived and centered as one square composition`() {
        val geometry = therapyTileGroupGeometry(192f, 3)

        assertEquals(geometry.ringDiameterDp * 2f + geometry.gapDp, geometry.widthDp, 0.001f)
        assertEquals(geometry.widthDp, geometry.heightDp, 0.001f)
        assertEquals((192f - geometry.widthDp) / 2f, geometry.originXDp, 0.001f)
        assertEquals((192f - geometry.heightDp) / 2f, geometry.originYDp, 0.001f)
    }

    @Test
    fun `therapy tile renders every valid one two and three metric selection`() {
        val selections =
            listOf(
                setOf(TherapyTileMetric.IOB),
                setOf(TherapyTileMetric.COB),
                setOf(TherapyTileMetric.BASAL),
                setOf(TherapyTileMetric.IOB, TherapyTileMetric.COB),
                setOf(TherapyTileMetric.IOB, TherapyTileMetric.BASAL),
                setOf(TherapyTileMetric.COB, TherapyTileMetric.BASAL),
                TherapyTileMetric.entries.toSet(),
            )
        val device =
            DeviceParameters
                .Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .setScreenDensity(2f)
                .build()
        selections.forEach { selected ->
            TherapyTileSelectionStore.write(context, selected)
            val service = Robolectric.buildService(TherapyTileService::class.java).create().get()
            val request =
                RequestBuilders.TileRequest
                    .Builder()
                    .setDeviceConfiguration(device)
                    .build()
            val tile =
                service
                    .onTileRequest(request)
                    .get()
            assertTrue(tile.resourcesVersion.contains("therapy"))
            assertEquals(selected.size, TherapyTileSelectionStore.read(context).metrics.size)
            val resourceIds =
                request.scope
                    .collectResources()
                    .idToImageMapping.keys
            selected.forEach { metric ->
                val expectedPrefix = if (metric == TherapyTileMetric.BASAL) "therapy_basal" else "therapy_${metric.name.lowercase()}"
                assertTrue(resourceIds.any { it.startsWith(expectedPrefix) })
            }
            service.onDestroy()
        }
    }

    @Test
    fun `therapy rings keep equal bounds and the mobile stroke geometry`() {
        val single = TherapyRingLayoutSpec(therapyTileGroupGeometry(192f, 1).ringDiameterDp)
        val pair = TherapyRingLayoutSpec(therapyTileGroupGeometry(192f, 2).ringDiameterDp)
        val triple = TherapyRingLayoutSpec(therapyTileGroupGeometry(192f, 3).ringDiameterDp)

        assertEquals(7f, single.strokeWidthDp, 0f)
        assertEquals(220f, single.protoLayoutStartDegrees, 0f)
        assertEquals(280f, single.sweepDegrees, 0f)
        assertEquals(pair.diameterDp, triple.diameterDp, 0f)
        assertEquals(pair.strokeWidthDp, triple.strokeWidthDp, 0f)
    }

    @Test
    fun `therapy rings use the shared mobile icon states`() {
        assertEquals(TherapyIndicatorIcon.IOB, wearTherapyIcon(TherapyTileMetric.IOB, null, now))
        assertEquals(TherapyIndicatorIcon.COB, wearTherapyIcon(TherapyTileMetric.COB, null, now))
        assertEquals(
            TherapyIndicatorIcon.BASAL_LESS,
            wearTherapyIcon(TherapyTileMetric.BASAL, state(123.0, now).copy(basal = BasalState(currentUnitsPerHour = 0.7, tempPercent = 80)), now),
        )
        assertEquals(
            TherapyIndicatorIcon.BASAL_MORE,
            wearTherapyIcon(TherapyTileMetric.BASAL, state(123.0, now).copy(basal = BasalState(currentUnitsPerHour = 0.7, tempPercent = 120)), now),
        )
    }

    @Test
    fun `graph tile positions every dot by measured time and advances with the minute clock`() {
        val measuredAt = now - 5 * 60_000L
        val oldReceivedNow = GlucoseSample(110.0, measuredAt, receivedAtEpochMs = now)
        val source = state(123.0, now).copy(glucoseHistory = listOf(oldReceivedNow))

        val initial = wearTileGraphPoints(source, now, 3, 180f)
        val oneMinuteLater = wearTileGraphPoints(source, now + 60_000L, 3, 180f)
        val oldInitial = initial.single { it.sample.measuredAtEpochMs == measuredAt }
        val oldLater = oneMinuteLater.single { it.sample.measuredAtEpochMs == measuredAt }
        val currentInitial = initial.single { it.sample.measuredAtEpochMs == now }

        assertEquals(180f, currentInitial.xDp, 0.001f)
        assertTrue(oldLater.xDp < oldInitial.xDp)
        assertTrue(oldInitial.xDp < currentInitial.xDp)
    }

    @Test
    fun `graph tile registers its inline image on the tile request scope`() {
        WearTileContentStore.write(context, WearTileKind.GLUCOSE, WearTileContent.GRAPH)
        val device =
            DeviceParameters
                .Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .setScreenDensity(2f)
                .build()
        val service = Robolectric.buildService(GlucoseTileService::class.java).create().get()
        val request =
            RequestBuilders.TileRequest
                .Builder()
                .setDeviceConfiguration(device)
                .build()

        service.onTileRequest(request).get()
        val resources = request.scope.collectResources()

        assertTrue(request.scope.hasResources())
        val inline = resources.idToImageMapping.getValue("live_cgm_graph").inlineResource!!
        assertEquals(IMAGE_FORMAT_RGB_565, inline.format)
        assertTrue(inline.widthPx > 0)
        assertTrue(inline.heightPx > 0)
        assertEquals(inline.widthPx * inline.heightPx * 2, inline.data.size)
        service.onDestroy()
    }

    private fun state(
        value: Double,
        measuredAt: Long,
    ) = TherapyDisplayState(
        source = DataSourceId.ANDROID_APS,
        receivedAtEpochMs = now,
        glucose =
            GlucoseState(
                valueMgDl = value,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FORTY_FIVE_UP,
                measuredAtEpochMs = measuredAt,
                deltaMgDl = 5.0,
            ),
        insulin = InsulinState(totalIob = 1.2),
        carbs = CarbState(cobGrams = 18.0),
        basal = BasalState(currentUnitsPerHour = 0.70),
        target = TargetState(lowMgDl = 80.0, highMgDl = 160.0),
    )
}
