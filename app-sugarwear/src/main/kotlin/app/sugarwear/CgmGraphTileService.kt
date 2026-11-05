package app.sugarwear

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.IMAGE_FORMAT_RGB_565
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.InlineImageResource
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.sugarlicious.model.SugarWearTypography
import app.sugarlicious.model.SugarWearTypographyRole
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.uishared.Rgb565Image
import app.sugarlicious.uishared.SharedWearCgmGraphRenderer
import app.sugarlicious.uishared.toRgb565Image
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class CgmGraphTileSnapshot(
    val readings: List<CgmReading>,
    val palette: CgmAppearancePalette,
    val pillState: CgmStatusPillState,
    val graphHours: Int,
    val nowEpochMs: Long,
    val graphStyle: app.sugarlicious.uishared.SharedWearCgmGraphStyle =
        app.sugarlicious.uishared.DirectToWatchGraphDefaults
            .style(),
) {
    val resourceVersion: String
        get() = "g7-graph-13-system-font-700-${cgmGraphHistoryFingerprint(readings)}-${nowEpochMs / CGM_GRAPH_TILE_FRESHNESS_INTERVAL_MS}-${palette.hashCode()}-$graphHours-${graphStyle.hashCode()}-${pillState.name}"
}

internal data class CgmGraphTileContentSpec(
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float,
    val outerPaddingDp: Float = 0f,
)

internal fun cgmGraphTileContentSpec(
    screenWidthDp: Int,
    screenHeightDp: Int,
): CgmGraphTileContentSpec {
    val square = cgmSquareTileSpec(screenWidthDp, screenHeightDp)
    return CgmGraphTileContentSpec(square.sideDp, square.sideDp - 21f, square.cornerRadiusDp)
}

/** Cache identity covers the complete canonical graph, including late BACKFILL rows. */
internal fun cgmGraphHistoryFingerprint(readings: List<CgmReading>): String =
    normalizeCgmLocalHistory(readings)
        .fold(1L) { hash, reading ->
            var next = hash * 31L + reading.sensorId.hashCode()
            next = next * 31L + reading.sessionId.hashCode()
            next = next * 31L + reading.timestampEpochMs
            next = next * 31L + reading.glucoseMgDl.toBits()
            next = next * 31L + reading.status.ordinal
            next
        }.toULong()
        .toString(16)

internal fun cgmGraphEmptyLabel(
    state: CgmStatusPillState,
    hasHistory: Boolean,
): String =
    when {
        hasHistory -> ""
        state == CgmStatusPillState.PAUSED -> "Sensorempfang pausiert"
        state == CgmStatusPillState.NO_ACTIVE_SENSOR -> "Kein aktiver Sensor"
        state == CgmStatusPillState.SENSOR_ERROR -> "Sensorfehler"
        state == CgmStatusPillState.SIGNAL_LOSS -> "Signalverlust"
        else -> "Keine Daten"
    }

internal fun cgmGraphScaleAgeLabel(graphHours: Int, measuredAtEpochMs: Long?, nowEpochMs: Long): String {
    val age = measuredAtEpochMs?.let { ((nowEpochMs - it).coerceAtLeast(0L) / 60_000L).toString() + "m" }
    return listOfNotNull("${graphHours}h", age).joinToString(" • ")
}

internal fun cgmGraphTileHeaderLabel(
    graphHours: Int,
    measuredAtEpochMs: Long?,
    nowEpochMs: Long,
): String = "Verlauf ${cgmGraphScaleAgeLabel(graphHours, measuredAtEpochMs, nowEpochMs).replace(" • ", " · ")}"

internal fun cgmGraphTileGraphInput(
    snapshot: CgmGraphTileSnapshot,
    settings: CgmDirectToWatchSettingsStore,
) = cgmSharedGraphInput(
    readings = snapshot.readings,
    palette = snapshot.palette,
    settings = settings,
    graphHours = snapshot.graphHours,
    nowEpochMs = snapshot.nowEpochMs,
    emptyLabel = cgmGraphEmptyLabel(snapshot.pillState, normalizeCgmLocalHistory(snapshot.readings).isNotEmpty()),
    styleOverride = snapshot.graphStyle,
    outsideClipColor = snapshot.palette.argb(CgmAppearanceRole.MENU_BACKGROUND),
)

class CgmGraphTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public override fun onTileRequest(requestParams: RequestBuilders.TileRequest): SettableFuture<Tile> {
        val future = SettableFuture.create<Tile>()
        tileScope.launch {
            runCatching {
                val snapshot = snapshot()
                Tile
                    .Builder()
                    .setResourcesVersion(snapshot.resourceVersion)
                    .setFreshnessIntervalMillis(CGM_GRAPH_TILE_FRESHNESS_INTERVAL_MS)
                    .setTileTimeline(Timeline.fromLayoutElement(layout(requestParams, snapshot)))
                    .build()
            }.onSuccess(future::set).onFailure(future::setException)
        }
        return future
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun layout(
        requestParams: RequestBuilders.TileRequest,
        snapshot: CgmGraphTileSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        val device = requestParams.deviceConfiguration
        val square = cgmSquareTileSpec(device.screenWidthDp, device.screenHeightDp)
        val palette = snapshot.palette
        val titleColor =
            when (snapshot.pillState) {
                CgmStatusPillState.CONNECTED -> palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
                CgmStatusPillState.SIGNAL_LOSS -> android.graphics.Color.WHITE
                CgmStatusPillState.SENSOR_ERROR -> palette.argb(CgmAppearanceRole.GLUCOSE_ERROR)
                CgmStatusPillState.PAUSED, CgmStatusPillState.NO_ACTIVE_SENSOR -> palette.argb(CgmAppearanceRole.GLUCOSE_NO_SOURCE)
            }
        val contentSpec = cgmGraphTileContentSpec(device.screenWidthDp, device.screenHeightDp)
        val effectiveGraphStyle = CgmAppearanceStore(this).tileGraphStyle(contentSpec.cornerRadiusDp)
        val graphWidth = contentSpec.widthDp
        val cardHeight = contentSpec.heightDp
        val graphHeight = contentSpec.heightDp
        val density = device.screenDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
        val graphPixels = renderGraph(snapshot.copy(graphStyle = effectiveGraphStyle), graphWidth, graphHeight, density)
        val graphResource =
            ImageResource
                .Builder()
                .setInlineResource(
                    InlineImageResource
                        .Builder()
                        .setData(graphPixels.data)
                        .setWidthPx(graphPixels.widthPx)
                        .setHeightPx(graphPixels.heightPx)
                        .setFormat(IMAGE_FORMAT_RGB_565)
                        .build(),
                ).build()
        val graphImage =
            Image
                .Builder(requestParams.scope)
                .setImageResource(graphResource, GRAPH_RESOURCE_ID)
                .setWidth(dp(graphWidth))
                .setHeight(dp(graphHeight))
                .build()
        val card =
            Box
                .Builder()
                .setWidth(dp(square.sideDp))
                .setHeight(dp(cardHeight))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(graphImage)
                .build()

        val header =
            Box
                .Builder()
                .setWidth(dp(square.sideDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT)
                .setModifiers(
                    Modifiers
                        .Builder()
                        .setPadding(Padding.Builder().setStart(dp(square.cornerRadiusDp)).build())
                        .build(),
                ).addContent(
                    label(
                        cgmGraphTileHeaderLabel(
                            snapshot.graphHours,
                            normalizeCgmLocalHistory(snapshot.readings).lastOrNull()?.timestampEpochMs,
                            snapshot.nowEpochMs,
                        ),
                        SugarWearTypography.spec(SugarWearTypographyRole.TILE_TITLE).sizeSp,
                        titleColor,
                    ),
                ).build()
        val content =
            Column
                .Builder()
                .setWidth(dp(square.sideDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT)
                .addContent(header)
                .addContent(Spacer.Builder().setHeight(dp(TILE_HEADER_GAP_DP)).build())
                .addContent(card)
                .build()

        return Box
            .Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers
                    .Builder()
                    .setBackground(Background.Builder().setColor(argb(palette.argb(CgmAppearanceRole.MENU_BACKGROUND))).build())
                    .setClickable(
                        Clickable
                            .Builder()
                            .setId(OPEN_GRAPH_CLICK_ID)
                            .setOnClick(ActionBuilders.launchAction(ComponentName(this, CgmWatchActivity::class.java)))
                            .build(),
                    ).build(),
            ).addContent(content)
            .apply {
                cgmSensorWarning(snapshot.pillState, snapshot.readings.any { it.status == app.sugarlicious.sensor.protocol.CgmReadingStatus.VALID })?.let { message ->
                    addContent(graphTileWarning(requestParams.scope, message, device.screenWidthDp.toFloat(), device.screenHeightDp.toFloat()))
                }
            }.build()
    }

    private suspend fun snapshot(): CgmGraphTileSnapshot {
        val now = System.currentTimeMillis()
        val appearance = CgmAppearanceStore(this)
        val hours = appearance.tileGraphHours()
        val readings =
            CgmReadingDatabase(this).let { database ->
                try {
                    database.getRange(now - hours * 60L * 60_000L, now + 5L * 60_000L)
                } finally {
                    database.close()
                }
            }
        val state = CgmSensorStateStore(this).read()
        return CgmGraphTileSnapshot(
            readings = readings,
            palette = appearance.tileGraphPalette(),
            pillState = deriveCgmStatusPillState(state, CgmCredentialStore(this).read() != null, now),
            graphHours = hours,
            nowEpochMs = now,
            graphStyle = appearance.tileGraphStyle(),
        )
    }

    private fun renderGraph(
        snapshot: CgmGraphTileSnapshot,
        widthDp: Float,
        heightDp: Float,
        density: Float,
    ): Rgb565Image {
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
        val bitmap = renderGraphBitmap(snapshot, widthPx, heightPx, density)
        return try {
            bitmap.toRgb565Image()
        } finally {
            bitmap.recycle()
        }
    }

    internal fun renderGraphBitmap(
        snapshot: CgmGraphTileSnapshot,
        widthPx: Int,
        heightPx: Int,
        density: Float,
    ): Bitmap {
        val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val settings = CgmDirectToWatchSettingsStore(this)
        SharedWearCgmGraphRenderer.render(
            Canvas(bitmap),
            widthPx,
            heightPx,
            density,
            density * resources.configuration.fontScale,
            cgmGraphTileGraphInput(snapshot, settings),
        )
        return bitmap
    }

    private fun label(
        value: String,
        sizeSp: Float,
        color: Int,
    ): Text =
        Text
            .Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle
                    .Builder()
                    .setSize(
                        androidx.wear.protolayout.DimensionBuilders
                            .sp(sizeSp),
                    ).setColor(argb(color))
                    .setWeight(sugarWearTileWeight(emphasized = true))
                    .build(),
            ).build()

    companion object {
        private const val GRAPH_RESOURCE_ID = "sugarwear_graph"
        private const val OPEN_GRAPH_CLICK_ID = "open_sugarwear_graph"
        private const val TILE_HEADER_LANE_DP = 21f
        private const val TILE_HEADER_GAP_DP = 4f
    }
}

internal const val CGM_GRAPH_TILE_FRESHNESS_INTERVAL_MS = 5L * 60_000L
