package app.aapswear.g7watch

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
import app.aapswear.g7.CgmReading
import app.aapswear.model.SugarWearTypography
import app.aapswear.model.SugarWearTypographyRole
import app.aapswear.uishared.Rgb565Image
import app.aapswear.uishared.SharedWearCgmGraphRenderer
import app.aapswear.uishared.toRgb565Image
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class G7GraphTileSnapshot(
    val readings: List<CgmReading>,
    val palette: G7AppearancePalette,
    val pillState: G7StatusPillState,
    val graphHours: Int,
    val nowEpochMs: Long,
    val graphStyle: app.aapswear.uishared.SharedWearCgmGraphStyle =
        app.aapswear.uishared.DirectToWatchGraphDefaults
            .style(),
) {
    val resourceVersion: String
        get() = "g7-graph-13-system-font-700-${g7GraphHistoryFingerprint(readings)}-${nowEpochMs / G7_GRAPH_TILE_FRESHNESS_INTERVAL_MS}-${palette.hashCode()}-$graphHours-${graphStyle.hashCode()}-${pillState.name}"
}

internal data class G7GraphTileContentSpec(
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float,
    val outerPaddingDp: Float = 0f,
)

internal fun g7GraphTileContentSpec(
    screenWidthDp: Int,
    screenHeightDp: Int,
): G7GraphTileContentSpec {
    val square = g7SquareTileSpec(screenWidthDp, screenHeightDp)
    return G7GraphTileContentSpec(square.sideDp, square.sideDp - 21f, square.cornerRadiusDp)
}

/** Cache identity covers the complete canonical graph, including late BACKFILL rows. */
internal fun g7GraphHistoryFingerprint(readings: List<CgmReading>): String =
    normalizeG7LocalHistory(readings)
        .fold(1L) { hash, reading ->
            var next = hash * 31L + reading.sensorId.hashCode()
            next = next * 31L + reading.sessionId.hashCode()
            next = next * 31L + reading.timestampEpochMs
            next = next * 31L + reading.glucoseMgDl.toBits()
            next = next * 31L + reading.status.ordinal
            next
        }.toULong()
        .toString(16)

internal fun g7GraphEmptyLabel(
    state: G7StatusPillState,
    hasHistory: Boolean,
): String =
    when {
        hasHistory -> ""
        state == G7StatusPillState.PAUSED -> "Sensorempfang pausiert"
        state == G7StatusPillState.NO_ACTIVE_SENSOR -> "Kein aktiver Sensor"
        state == G7StatusPillState.SENSOR_ERROR -> "Sensorfehler"
        state == G7StatusPillState.SIGNAL_LOSS -> "Signalverlust"
        else -> "Keine Daten"
    }

internal fun g7GraphScaleAgeLabel(graphHours: Int, measuredAtEpochMs: Long?, nowEpochMs: Long): String {
    val age = measuredAtEpochMs?.let { ((nowEpochMs - it).coerceAtLeast(0L) / 60_000L).toString() + "m" }
    return listOfNotNull("${graphHours}h", age).joinToString(" • ")
}

internal fun g7GraphTileHeaderLabel(
    graphHours: Int,
    measuredAtEpochMs: Long?,
    nowEpochMs: Long,
): String = "Verlauf ${g7GraphScaleAgeLabel(graphHours, measuredAtEpochMs, nowEpochMs).replace(" • ", " · ")}"

internal fun g7GraphTileGraphInput(
    snapshot: G7GraphTileSnapshot,
    settings: G7DirectToWatchSettingsStore,
) = g7SharedGraphInput(
    readings = snapshot.readings,
    palette = snapshot.palette,
    settings = settings,
    graphHours = snapshot.graphHours,
    nowEpochMs = snapshot.nowEpochMs,
    emptyLabel = g7GraphEmptyLabel(snapshot.pillState, normalizeG7LocalHistory(snapshot.readings).isNotEmpty()),
    styleOverride = snapshot.graphStyle,
    outsideClipColor = snapshot.palette.argb(G7AppearanceRole.MENU_BACKGROUND),
)

class G7GraphTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public override fun onTileRequest(requestParams: RequestBuilders.TileRequest): SettableFuture<Tile> {
        val future = SettableFuture.create<Tile>()
        tileScope.launch {
            runCatching {
                val snapshot = snapshot()
                Tile
                    .Builder()
                    .setResourcesVersion(snapshot.resourceVersion)
                    .setFreshnessIntervalMillis(G7_GRAPH_TILE_FRESHNESS_INTERVAL_MS)
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
        snapshot: G7GraphTileSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        val device = requestParams.deviceConfiguration
        val square = g7SquareTileSpec(device.screenWidthDp, device.screenHeightDp)
        val palette = snapshot.palette
        val titleColor =
            when (snapshot.pillState) {
                G7StatusPillState.CONNECTED -> palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY)
                G7StatusPillState.SIGNAL_LOSS -> android.graphics.Color.WHITE
                G7StatusPillState.SENSOR_ERROR -> palette.argb(G7AppearanceRole.GLUCOSE_ERROR)
                G7StatusPillState.PAUSED, G7StatusPillState.NO_ACTIVE_SENSOR -> palette.argb(G7AppearanceRole.GLUCOSE_NO_SOURCE)
            }
        val contentSpec = g7GraphTileContentSpec(device.screenWidthDp, device.screenHeightDp)
        val effectiveGraphStyle = G7AppearanceStore(this).tileGraphStyle(contentSpec.cornerRadiusDp)
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
                        g7GraphTileHeaderLabel(
                            snapshot.graphHours,
                            normalizeG7LocalHistory(snapshot.readings).lastOrNull()?.timestampEpochMs,
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
                    .setBackground(Background.Builder().setColor(argb(palette.argb(G7AppearanceRole.MENU_BACKGROUND))).build())
                    .setClickable(
                        Clickable
                            .Builder()
                            .setId(OPEN_GRAPH_CLICK_ID)
                            .setOnClick(ActionBuilders.launchAction(ComponentName(this, G7WatchActivity::class.java)))
                            .build(),
                    ).build(),
            ).addContent(content)
            .apply {
                g7SensorWarning(snapshot.pillState, snapshot.readings.any { it.status == app.aapswear.g7.CgmReadingStatus.VALID })?.let { message ->
                    addContent(graphTileWarning(requestParams.scope, message, device.screenWidthDp.toFloat(), device.screenHeightDp.toFloat()))
                }
            }.build()
    }

    private suspend fun snapshot(): G7GraphTileSnapshot {
        val now = System.currentTimeMillis()
        val appearance = G7AppearanceStore(this)
        val hours = appearance.tileGraphHours()
        val readings =
            G7ReadingDatabase(this).let { database ->
                try {
                    database.getRange(now - hours * 60L * 60_000L, now + 5L * 60_000L)
                } finally {
                    database.close()
                }
            }
        val state = G7SensorStateStore(this).read()
        return G7GraphTileSnapshot(
            readings = readings,
            palette = appearance.tileGraphPalette(),
            pillState = deriveG7StatusPillState(state, G7CredentialStore(this).read() != null, now),
            graphHours = hours,
            nowEpochMs = now,
            graphStyle = appearance.tileGraphStyle(),
        )
    }

    private fun renderGraph(
        snapshot: G7GraphTileSnapshot,
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
        snapshot: G7GraphTileSnapshot,
        widthPx: Int,
        heightPx: Int,
        density: Float,
    ): Bitmap {
        val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val settings = G7DirectToWatchSettingsStore(this)
        SharedWearCgmGraphRenderer.render(
            Canvas(bitmap),
            widthPx,
            heightPx,
            density,
            density * resources.configuration.fontScale,
            g7GraphTileGraphInput(snapshot, settings),
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

internal const val G7_GRAPH_TILE_FRESHNESS_INTERVAL_MS = 5L * 60_000L
