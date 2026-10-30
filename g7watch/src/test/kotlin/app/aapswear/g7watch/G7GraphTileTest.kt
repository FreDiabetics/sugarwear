package app.aapswear.g7watch

import android.content.Intent
import android.graphics.Color
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ResourceBuilders.IMAGE_FORMAT_RGB_565
import androidx.wear.tiles.RequestBuilders
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.uishared.SharedWearCgmGraphRenderer
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
class G7GraphTileTest {
    @Test fun `graph tile header contains scale and latest CGM age`() {
        assertEquals("Verlauf 2h · 3m", g7GraphTileHeaderLabel(2, now - 3 * 60_000L, now))
        assertEquals("Verlauf 2h", g7GraphTileHeaderLabel(2, null, now))
    }

    private val context =
        androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
    private val now = 20_000_000L

    @Test fun `rounded square sizing is responsive and safe on round watches`() {
        listOf(192 to 192, 227 to 227, 240 to 240, 220 to 180).forEach { (width, height) ->
            val spec = g7SquareTileSpec(width, height)
            assertEquals(minOf(width, height) * 0.76f, spec.sideDp, 0.01f)
            assertTrue(spec.sideDp < minOf(width, height))
            assertTrue(spec.cornerRadiusDp > 0f)
            assertTrue(spec.cornerRadiusDp < spec.sideDp / 2f)
        }
    }

    @Test fun `graph tile uses measured time and keeps only the newest sensor session`() {
        val oldSession = reading("sensor-old", "session-old", 1, now - 40 * 60_000L, CgmReadingOrigin.LIVE)
        val backfill = reading("sensor-new", "session-new", 2, now - 20 * 60_000L, CgmReadingOrigin.BACKFILL)
        val live = reading("sensor-new", "session-new", 3, now - 2 * 60_000L, CgmReadingOrigin.LIVE)
        val input =
            g7SharedGraphInput(
                readings = listOf(oldSession, live, backfill),
                palette = G7AppearanceStore(context).load(),
                settings = G7DirectToWatchSettingsStore(context),
                graphHours = 3,
                nowEpochMs = now,
            )

        assertEquals(listOf(backfill.timestampEpochMs, live.timestampEpochMs), input.history.map { it.measuredAtEpochMs })
        assertTrue(input.timeWindow.plotX(backfill.timestampEpochMs, 0f, 100f) < input.timeWindow.plotX(live.timestampEpochMs, 0f, 100f))
        assertEquals(g7CollectorGraphWindow(now, 3), input.timeWindow)
    }

    @Test fun `graph tile moves scale and age out of the bitmap overlay`() {
        val snapshot =
            G7GraphTileSnapshot(
                readings = listOf(reading("sensor", "session", 1, now - 3 * 60_000L, CgmReadingOrigin.LIVE)),
                palette = G7AppearanceStore(context).load(),
                pillState = G7StatusPillState.CONNECTED,
                graphHours = 2,
                nowEpochMs = now,
            )

        assertEquals("", g7GraphTileGraphInput(snapshot, G7DirectToWatchSettingsStore(context)).topLeftLabel)
    }

    @Test fun `live copy wins over duplicate backfill without inventing a point`() {
        val backfill = reading("sensor", "session", 7, now - 5 * 60_000L, CgmReadingOrigin.BACKFILL)
        val live = backfill.copy(id = "live", origin = CgmReadingOrigin.LIVE, receivedAtEpochMs = now)
        val normalized = normalizeG7LocalHistory(listOf(backfill, live))

        assertEquals(1, normalized.size)
        assertEquals(CgmReadingOrigin.LIVE, normalized.single().origin)
    }

    @Test fun `sequence metadata cannot create a second graph point for one measurement`() {
        val backfill = reading("sensor", "session", 7, now - 5 * 60_000L, CgmReadingOrigin.BACKFILL)
        val live = backfill.copy(id = "live", sequenceNumber = 7007, origin = CgmReadingOrigin.LIVE, receivedAtEpochMs = now)

        val input =
            g7SharedGraphInput(
                readings = listOf(backfill, live),
                palette = G7AppearanceStore(context).load(),
                settings = G7DirectToWatchSettingsStore(context),
                graphHours = 3,
                nowEpochMs = now,
            )

        assertEquals(1, input.history.size)
    }

    @Test fun `graph empty states are explicit and history remains visible while stale`() {
        assertEquals("Keine Daten", g7GraphEmptyLabel(G7StatusPillState.CONNECTED, false))
        assertEquals("Signalverlust", g7GraphEmptyLabel(G7StatusPillState.SIGNAL_LOSS, false))
        assertEquals("Sensorfehler", g7GraphEmptyLabel(G7StatusPillState.SENSOR_ERROR, false))
        assertEquals("Kein aktiver Sensor", g7GraphEmptyLabel(G7StatusPillState.NO_ACTIVE_SENSOR, false))
        assertEquals("", g7GraphEmptyLabel(G7StatusPillState.SIGNAL_LOSS, true))
    }

    @Test fun `both SugarWear tile providers are registered`() {
        val services =
            context.packageManager
                .queryIntentServices(
                    Intent("androidx.wear.tiles.action.BIND_TILE_PROVIDER").setPackage(context.packageName),
                    0,
                ).map { it.serviceInfo.name }
                .toSet()

        assertTrue(services.any { it.endsWith("G7CollectorTileService") })
        assertTrue(services.any { it.endsWith("G7GraphTileService") })
    }

    @Test fun `graph tile returns a square layout and an inline graph resource`() {
        val device =
            DeviceParameters
                .Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .setScreenDensity(2f)
                .build()
        val service = Robolectric.buildService(G7GraphTileService::class.java).create().get()
        val request =
            RequestBuilders.TileRequest
                .Builder()
                .setDeviceConfiguration(device)
                .build()
        val tile = service.onTileRequest(request).get()
        val resources = request.scope.collectResources()

        assertTrue(tile.resourcesVersion.startsWith("g7-graph-13-system-font-700-"))
        assertTrue(request.scope.hasResources())
        val inline = resources.idToImageMapping.getValue("sugarwear_graph").inlineResource!!
        val content = g7GraphTileContentSpec(192, 192)
        assertEquals(IMAGE_FORMAT_RGB_565, inline.format)
        assertEquals((content.widthDp * 2f).toInt(), inline.widthPx)
        assertEquals((content.heightDp * 2f).toInt(), inline.heightPx)
        assertEquals(inline.widthPx * inline.heightPx * 2, inline.data.size)
        service.onDestroy()
    }

    @Test fun `backfill changes graph resource version even when latest reading is unchanged`() {
        val latest = reading("sensor", "session", 3, now - 60_000L, CgmReadingOrigin.LIVE)
        val base =
            G7GraphTileSnapshot(
                readings = listOf(latest),
                palette = G7AppearanceStore(context).load(),
                pillState = G7StatusPillState.CONNECTED,
                graphHours = 3,
                nowEpochMs = now,
            )
        val withBackfill =
            base.copy(
                readings = base.readings + reading("sensor", "session", 2, now - 6 * 60_000L, CgmReadingOrigin.BACKFILL),
            )

        assertTrue(base.resourceVersion != withBackfill.resourceVersion)
    }

    @Test fun `stored history produces visible graph dots at small and Galaxy round sizes`() {
        val palette = G7AppearanceStore(context).load()
        val snapshot =
            G7GraphTileSnapshot(
                readings =
                    listOf(
                        reading("sensor", "session", 1, now - 10 * 60_000L, CgmReadingOrigin.BACKFILL),
                        reading("sensor", "session", 2, now - 5 * 60_000L, CgmReadingOrigin.LIVE),
                        reading("sensor", "session", 3, now - 60_000L, CgmReadingOrigin.LIVE),
                    ),
                palette = palette,
                pillState = G7StatusPillState.CONNECTED,
                graphHours = 3,
                nowEpochMs = now,
            )
        val service = Robolectric.buildService(G7GraphTileService::class.java).create().get()

        listOf(192 to 112, 454 to 220).forEach { (width, height) ->
            val bitmap = service.renderGraphBitmap(snapshot, width, height, 1f)
            val input =
                g7SharedGraphInput(
                    snapshot.readings,
                    snapshot.palette,
                    G7DirectToWatchSettingsStore(context),
                    snapshot.graphHours,
                    snapshot.nowEpochMs,
                )
            val metrics = SharedWearCgmGraphRenderer.metrics(width, height, 1f, input.thresholds, input.style)
            assertTrue(
                input.history.any { sample ->
                    val x = metrics.xFor(input.timeWindow, sample.measuredAtEpochMs).toInt().coerceIn(0, bitmap.width - 1)
                    val y = metrics.yFor(sample.valueMgDl).toInt().coerceIn(0, bitmap.height - 1)
                    val referenceX = (x - 15).coerceAtLeast(0)
                    bitmap.getPixel(x, y) != bitmap.getPixel(referenceX, y)
                },
            )
            bitmap.recycle()
        }
        service.onDestroy()
    }

    @Test fun `graph fills the existing tile contour without an inner surface`() {
        listOf(192 to 192, 227 to 227, 240 to 240, 220 to 180).forEach { (width, height) ->
            val square = g7SquareTileSpec(width, height)
            val graph = g7GraphTileContentSpec(width, height)
            assertEquals(square.sideDp, graph.widthDp, 0.01f)
            assertEquals(square.sideDp - 21f, graph.heightDp, 0.01f)
            assertEquals(square.cornerRadiusDp, graph.cornerRadiusDp, 0.01f)
            assertEquals(0f, graph.outerPaddingDp, 0f)
        }
    }

    @Test fun `tile graph settings and colors are independent from in app graph`() {
        val appearance = G7AppearanceStore(context)
        val inApp = G7DirectToWatchSettingsStore(context)
        appearance.resetTileGraph()
        assertTrue(appearance.tileGraphStyle().timeAxisEnabled)
        inApp.saveGraphHours(3)

        appearance.setTileGraphHours(12)
        appearance.saveTileGraphStyle(appearance.tileGraphStyle().copy(dotRadiusDp = 5f, timeAxisEnabled = true))
        appearance.saveTileGraphColor(G7AppearanceRole.GRAPH_BACKGROUND, Color.MAGENTA)

        assertEquals(12, appearance.tileGraphHours())
        assertEquals(3, inApp.graphHours())
        assertEquals(5f, appearance.tileGraphStyle().dotRadiusDp, 0f)
        assertTrue(appearance.tileGraphStyle().timeAxisEnabled)
        assertEquals(Color.MAGENTA, appearance.tileGraphPalette().argb(G7AppearanceRole.GRAPH_BACKGROUND))
        assertTrue(Color.MAGENTA != appearance.load().argb(G7AppearanceRole.GRAPH_BACKGROUND))
    }

    @Test fun `outside the graph contour matches the tile while graph fills the contour`() {
        val palette = G7AppearanceStore(context).load()
        val snapshot =
            G7GraphTileSnapshot(
                readings = emptyList(),
                palette = palette,
                pillState = G7StatusPillState.SIGNAL_LOSS,
                graphHours = 3,
                nowEpochMs = now,
            )
        val service = Robolectric.buildService(G7GraphTileService::class.java).create().get()

        val bitmap = service.renderGraphBitmap(snapshot, 300, 180, 1f)

        assertEquals(palette.argb(G7AppearanceRole.MENU_BACKGROUND), bitmap.getPixel(0, 0))
        assertTrue(
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) !=
                palette.argb(G7AppearanceRole.MENU_BACKGROUND),
        )
        assertEquals(Color.alpha(bitmap.getPixel(0, 0)), 255)
        bitmap.recycle()
        service.onDestroy()
    }

    private fun reading(
        sensor: String,
        session: String,
        sequence: Long,
        measuredAt: Long,
        origin: CgmReadingOrigin,
    ) = CgmReading(
        id = "$sensor-$sequence-$origin",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = sensor,
        sessionId = session,
        glucoseMgDl = 120.0 + sequence,
        timestampEpochMs = measuredAt,
        receivedAtEpochMs = now,
        sequenceNumber = sequence,
        status = CgmReadingStatus.VALID,
        origin = origin,
    )
}
