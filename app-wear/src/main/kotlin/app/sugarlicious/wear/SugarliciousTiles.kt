package app.sugarlicious.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Border
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.IMAGE_FORMAT_RGB_565
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.InlineImageResource
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.sugarlicious.complications.DirectSensorReadingResolver
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.GlucoseAgeText
import app.sugarlicious.model.GlucoseBarScale
import app.sugarlicious.model.GlucoseBarState
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.TherapyBridgeDisplayField
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyIndicatorGeometry
import app.sugarlicious.model.TherapyIndicatorIcon
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressSemantics
import app.sugarlicious.model.TherapyRingGeometry
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisualAsset
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.model.basalIndicatorIcon
import app.sugarlicious.model.effectiveBasalPresentation
import app.sugarlicious.protocol.WatchUiColors
import app.sugarlicious.storage.TherapyStateStore
import app.sugarlicious.uishared.GlucoseRangeBarRenderer
import app.sugarlicious.uishared.SharedWearCgmGraphInput
import app.sugarlicious.uishared.SharedWearCgmGraphPalette
import app.sugarlicious.uishared.SharedWearCgmGraphRenderer
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import app.sugarlicious.uishared.TrendDrawableResources
import app.sugarlicious.uishared.toRgb565Image
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Bump when visual resources/typography change so Wear OS cannot reuse an older cached tile tree.
private const val TILE_RESOURCES_VERSION = "sugarlicious-19-glucose-mobile-parity"
private const val TILE_GRAPH_RESOURCE_ID = "live_cgm_graph"
private const val TILE_GRAPH_WIDTH_PX = 296
private const val TILE_GRAPH_HEIGHT_PX = 120

/** Use the same real bold weight as native, bitmap and Compose rendering. */
internal fun sugarliciousTileWeight(emphasized: Boolean): Int =
    app.sugarlicious.model.SystemTypographyTokens
        .weight(emphasized)

internal data class WearGlucoseTilePresentation(
    val value: String,
    val meta: String,
    val footer: String,
    val status: String,
    val valueColor: Int,
    val statusColor: Int,
    val trend: Trend? = null,
    val freshness: Freshness = Freshness.NO_DATA,
    val barState: GlucoseBarState? = null,
    val ageText: String = "—",
    val deltaText: String = "—",
)

internal fun wearGlucoseTilePresentation(
    state: TherapyDisplayState?,
    colors: WatchUiColors,
    now: Long,
    thresholds: CgmThresholds = CgmThresholds.DEFAULT,
): WearGlucoseTilePresentation {
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val glucose = state?.glucose
    val retainsLastState = TherapyDisplayFormatter.isGlucoseDisplayable(state, now) && glucose != null
    val displayable = TherapyDisplayFormatter.isGlucoseKnown(state) && glucose != null
    val statusColor =
        when (freshness) {
            Freshness.CURRENT -> colors.accent
            Freshness.DELAYED -> colors.glucoseHigh
            Freshness.SIGNAL_LOSS -> android.graphics.Color.WHITE
            Freshness.STALE, Freshness.ERROR, Freshness.NO_DATA -> colors.glucoseLow
        }
    if (!displayable) {
        val message =
            when (freshness) {
                Freshness.STALE -> "KEINE AKTUELLEN CGM-DATEN"
                Freshness.SIGNAL_LOSS -> "SIGNALVERLUST"
                Freshness.ERROR -> "G7 SENSORFEHLER"
                Freshness.NO_DATA -> "KEINE CGM-DATEN"
                else -> TherapyDisplayFormatter.freshnessLabel(freshness)
            }
        return WearGlucoseTilePresentation(
            value = "—",
            meta = message,
            footer = TherapyDisplayFormatter.sourceName(state?.source),
            status = TherapyDisplayFormatter.freshnessLabel(freshness),
            valueColor = colors.textPrimary,
            statusColor = statusColor,
            freshness = freshness,
            deltaText = message,
        )
    }
    val valueColor =
        if (freshness == Freshness.SIGNAL_LOSS) {
            0xFF9E9E9E.toInt()
        } else {
            when (thresholds.classify(glucose.valueMgDl)) {
                CgmRangeClass.VERY_LOW -> colors.glucoseVeryLow
                CgmRangeClass.LOW -> colors.glucoseLow
                CgmRangeClass.IN_RANGE -> colors.glucoseInRange
                CgmRangeClass.HIGH -> colors.glucoseHigh
                CgmRangeClass.VERY_HIGH -> colors.glucoseVeryHigh
                null -> colors.glucoseInRange
            }
        }
    val delta = TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, glucose.displayUnit).ifBlank { "—" }
    val unit = if (glucose.displayUnit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
    val age = TherapyDisplayFormatter.ageMinutes(glucose.measuredAtEpochMs, now).takeUnless { it == "—" }.orEmpty()
    return WearGlucoseTilePresentation(
        value = TherapyDisplayFormatter.glucose(glucose),
        meta = "$delta  ·  $unit",
        footer = listOf(TherapyDisplayFormatter.sourceName(state.source), age).filter(String::isNotBlank).joinToString("  ·  "),
        status = TherapyDisplayFormatter.freshnessLabel(freshness),
        valueColor = valueColor,
        statusColor = statusColor,
        trend = glucose.trend.takeIf { retainsLastState && it != Trend.UNKNOWN },
        freshness = freshness,
        barState =
            glucose.valueMgDl
                .takeIf { freshness in setOf(Freshness.CURRENT, Freshness.DELAYED) }
                ?.let { GlucoseBarScale.state(it, thresholds) },
        ageText = GlucoseAgeText.fromAgeMinutes(age),
        deltaText = "$delta $unit",
    )
}

internal data class WearTherapyTilePresentation(
    val iob: String,
    val cob: String,
    val basal: String,
    val iobSecondary: String? = null,
    val basalSecondary: String? = null,
    val status: String,
    val footer: String,
    val displayable: Boolean,
)

internal fun wearTherapyTilePresentation(
    state: TherapyDisplayState?,
    now: Long,
): WearTherapyTilePresentation {
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val displayable = state?.let { it.insulin != null || it.carbs != null || it.basal != null } == true
    return WearTherapyTilePresentation(
        iob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, state?.insulin?.totalIob, state),
        cob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, state?.carbs?.cobGrams, state),
        basal = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, effectiveBasalPresentation(state, now)?.unitsPerHour, state),
        iobSecondary = wearLatestBolusPresentation(state, now),
        basalSecondary = effectiveBasalPresentation(state, now)?.percent?.takeIf { it != 100 }?.let { "@$it%" },
        status = TherapyDisplayFormatter.freshnessLabel(freshness),
        footer =
            if (displayable) {
                buildList {
                    add(TherapyDisplayFormatter.sourceName(state.source))
                    state.loop
                        ?.status
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::add)
                    if (freshness != Freshness.CURRENT) add("letzter Stand")
                }.joinToString("  ·  ")
            } else {
                when (freshness) {
                    Freshness.STALE -> "THERAPIEDATEN AUSGEBLENDET"
                    Freshness.SIGNAL_LOSS -> "SIGNALVERLUST"
                    Freshness.ERROR -> "SENSORFEHLER"
                    Freshness.NO_DATA -> "KEINE THERAPIEDATEN"
                    else -> TherapyDisplayFormatter.freshnessLabel(freshness)
                }
            },
        displayable = displayable,
    )
}

private fun wearLatestBolusPresentation(
    state: TherapyDisplayState?,
    now: Long,
): String? {
    val kinds = setOf(TherapyEventKind.MEAL_BOLUS, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.SMB)
    val event =
        state
            ?.therapyEvents
            .orEmpty()
            .asSequence()
            .filter { it.kind in kinds && it.timestampEpochMs <= now && it.amount.isFinite() && it.amount > 0.0 }
            .maxWithOrNull(compareBy({ it.timestampEpochMs }, { it.id }))
            ?: return null
    val amount = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(event.amount)
    val minutes = (now - event.timestampEpochMs).coerceAtLeast(0L) / 60_000L
    return "${amount}U·${if (minutes < 60L) "${minutes}m" else "${minutes / 60L}h"}"
}

internal data class WearTileGraphPoint(
    val sample: GlucoseSample,
    val xDp: Float,
)

internal fun wearTileGraphPoints(
    state: TherapyDisplayState?,
    now: Long,
    graphHours: Int,
    plotWidthDp: Float,
): List<WearTileGraphPoint> {
    val window = GraphTimeWindow.live(now, graphHours * 60L * 60_000L)
    val samples =
        buildList {
            addAll(state?.glucoseHistory.orEmpty())
            state?.glucose?.let { glucose ->
                add(
                    GlucoseSample(
                        valueMgDl = glucose.valueMgDl,
                        measuredAtEpochMs = glucose.measuredAtEpochMs,
                        source = glucose.source,
                        sensorId = glucose.sensorId,
                        sessionId = glucose.sessionId,
                        sequenceNumber = glucose.sequenceNumber,
                        receivedAtEpochMs = glucose.receivedAtEpochMs,
                        quality = glucose.quality,
                    ),
                )
            }
        }
    return samples
        .asSequence()
        .filter { it.quality == app.sugarlicious.model.CgmQuality.VALID }
        .filter { it.measuredAtEpochMs in window.startEpochMs..window.endEpochMs }
        .distinctBy { listOf(it.sensorId, it.sessionId, it.sequenceNumber, it.measuredAtEpochMs, it.source) }
        .sortedBy(GlucoseSample::measuredAtEpochMs)
        .map { WearTileGraphPoint(it, window.plotX(it.measuredAtEpochMs, 0f, plotWidthDp)) }
        .toList()
}

abstract class SugarliciousTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tileTypography = SugarliciousTileTypography(this)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        tileTypography.clear()
    }

    protected abstract val tileKind: WearTileKind

    public override fun onTileRequest(requestParams: RequestBuilders.TileRequest): com.google.common.util.concurrent.ListenableFuture<Tile> {
        val future = SettableFuture.create<Tile>()
        tileScope.launch {
            runCatching { buildTile(requestParams) }
                .onSuccess(future::set)
                .onFailure(future::setException)
        }
        return future
    }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): Tile {
        val phoneState = TherapyStateStore(this@SugarliciousTileService).state.first()
        val state = DirectSensorReadingResolver.resolve(this@SugarliciousTileService, phoneState)
        val colors = WearTileAppearanceStore.read(this, tileKind)
        val content = WearTileContentStore.read(this, tileKind)
        val therapySelection = TherapyTileSelectionStore.read(this)
        val therapyAppearance = TherapyTileGeometryStore.read(this)
        val preferences = WearDisplayPreferences.read(this)
        val now = System.currentTimeMillis()
        val resourcesVersion =
            if (tileKind == WearTileKind.THERAPY) {
                "$TILE_RESOURCES_VERSION-therapy-${therapySelection.canonical.joinToString { it.name }}-${therapyAppearance.hashCode()}"
            } else if (content == WearTileContent.GRAPH) {
                "$TILE_RESOURCES_VERSION-graph-${now / 60_000L}-${state?.glucoseHistory.hashCode()}-${state?.glucose.hashCode()}-${preferences.hashCode()}-${colors.hashCode()}"
            } else {
                TILE_RESOURCES_VERSION
            }
        return Tile
            .Builder()
            .setResourcesVersion("$resourcesVersion-${listOf(state.hashCode(), colors.hashCode(), preferences.hashCode(), resources.configuration.fontScale, resources.configuration.densityDpi, now / 60_000L).hashCode()}")
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    if (tileKind == WearTileKind.THERAPY) {
                        therapyTileContent(
                            requestParams.scope,
                            state,
                            colors,
                            now,
                            therapySelection,
                            therapyAppearance,
                            minOf(requestParams.deviceConfiguration.screenWidthDp, requestParams.deviceConfiguration.screenHeightDp).toFloat(),
                            tileTypography,
                        )
                    } else {
                        when (content) {
                            WearTileContent.GLUCOSE -> glucoseTileContent(requestParams.scope, state, colors, now, preferences, tileTypography)
                            WearTileContent.GRAPH -> graphTileContent(requestParams.scope, state, colors, now, preferences, resources.configuration.fontScale, tileTypography)
                        }
                    },
                ),
            ).build()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}

class GlucoseTileService : SugarliciousTileService() {
    override val tileKind: WearTileKind = WearTileKind.GLUCOSE
}

private fun glucoseTileContent(
    scope: ProtoLayoutScope,
    state: TherapyDisplayState?,
    colors: WatchUiColors,
    now: Long,
    preferences: WearDisplayPreferences,
    textRenderer: SugarliciousTileTypography,
): LayoutElementBuilders.LayoutElement {
    val presentation = wearGlucoseTilePresentation(state, colors, now, preferences.cgmThresholds)
    val contentWidth = textRenderer.contentWidthDp
    val trendSpace =
        presentation.trend?.let(TrendVisuals::spec)?.let {
            7f + GlucoseTrendSizing.arrowHeightForGlucoseHeight(42f) * preferences.trendArrowStyle.renderSpec().scale * it.aspectRatio
        } ?: 0f
    val primary =
        Row
            .Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                textRenderer.text(
                    scope,
                    presentation.value,
                    42f * GlucoseTrendSizing.scaleFactor(preferences.glucoseScalePercent),
                    presentation.valueColor,
                    bold = true,
                    maxWidthDp = (contentWidth - trendSpace - 12f).coerceAtLeast(24f),
                ),
            ).apply {
                presentation.trend?.let(TrendVisuals::spec)?.let { spec ->
                    addContent(Spacer.Builder().setWidth(dp(7f)).build())
                    addContent(tileTrendImage(scope, spec, preferences.trendArrowStyle.renderSpec()))
                }
            }.build()

    val barWidthDp = (contentWidth * 0.96f).coerceAtLeast(24f)
    val barHeightDp = 12f
    val scale = 2f
    val barBitmap = createBitmap((barWidthDp * scale).toInt().coerceAtLeast(1), (barHeightDp * scale).toInt().coerceAtLeast(1))
    val barPixels =
        try {
            Canvas(barBitmap).apply {
                drawColor(colors.background)
                GlucoseRangeBarRenderer().draw(
                    canvas = this,
                    widthPx = barBitmap.width.toFloat(),
                    heightPx = barBitmap.height.toFloat(),
                    state = presentation.barState,
                    palette = preferences.uiColors.glucoseRangeBarPalette(),
                    gapPx = 7f * scale,
                )
            }
            barBitmap.toRgb565Image()
        } finally {
            barBitmap.recycle()
        }
    val progressBar =
        Image
            .Builder(scope)
            .setImageResource(
                ImageResource
                    .Builder()
                    .setInlineResource(
                        InlineImageResource
                            .Builder()
                            .setData(barPixels.data)
                            .setWidthPx(barPixels.widthPx)
                            .setHeightPx(barPixels.heightPx)
                            .setFormat(IMAGE_FORMAT_RGB_565)
                            .build(),
                    ).build(),
                "glucose_range_progress",
            ).setWidth(dp(barWidthDp))
            .setHeight(dp(barHeightDp))
            .build()

    val metadata =
        Column
            .Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(textRenderer.text(scope, presentation.ageText, 13f, colors.textSecondary, bold = true, maxWidthDp = contentWidth * 0.82f))
            .addContent(Spacer.Builder().setHeight(dp(3f)).build())
            .addContent(textRenderer.text(scope, presentation.deltaText, 15f, colors.textPrimary, bold = true, maxWidthDp = contentWidth * 0.82f))
            .build()
    val pill =
        Box
            .Builder()
            .setWidth(dp(contentWidth * 0.91f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers
                    .Builder()
                    .setBackground(
                        Background
                            .Builder()
                            .setColor(argb(colors.tileBackground))
                            .setCorner(Corner.Builder().setRadius(dp(36f)).build())
                            .build(),
                    ).setPadding(Padding.Builder().setAll(dp(10f)).build())
                    .build(),
            ).addContent(metadata)
            .build()
    val column =
        Column
            .Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(primary)
            .addContent(Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(progressBar)
            .addContent(Spacer.Builder().setHeight(dp(14f)).build())
            .addContent(pill)
            .apply {
                if (presentation.freshness !in setOf(Freshness.CURRENT, Freshness.DELAYED)) {
                    addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    addContent(textRenderer.text(scope, presentation.status, 10f, presentation.statusColor, bold = true))
                }
            }.build()
    return tileRoot(colors.background, column, paddingDp = 12f)
}

class TherapyTileService : SugarliciousTileService() {
    override val tileKind: WearTileKind = WearTileKind.THERAPY
}

private fun graphTileContent(
    scope: ProtoLayoutScope,
    state: TherapyDisplayState?,
    colors: WatchUiColors,
    now: Long,
    preferences: WearDisplayPreferences,
    fontScale: Float,
    textRenderer: SugarliciousTileTypography,
): LayoutElementBuilders.LayoutElement {
    val bitmap = renderWearTileGraph(state, preferences, now, fontScale, textRenderer.typeface(false), textRenderer.typeface(true))
    val graphPixels =
        try {
            bitmap.toRgb565Image()
        } finally {
            bitmap.recycle()
        }
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
    val column =
        Column
            .Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(textRenderer.text(scope, "CGM · ${preferences.graphHours}h", 12f, colors.accent, bold = true))
            .addContent(Spacer.Builder().setHeight(dp(5f)).build())
            .addContent(
                Image
                    .Builder(scope)
                    .setImageResource(graphResource, TILE_GRAPH_RESOURCE_ID)
                    .setWidth(dp(148f))
                    .setHeight(dp(60f))
                    .build(),
            ).build()
    return tileRoot(colors.background, roundedTileCard(colors, 18f, column))
}

internal fun renderWearTileGraph(
    state: TherapyDisplayState?,
    preferences: WearDisplayPreferences,
    now: Long,
    fontScale: Float = 1f,
    regularTypeface: android.graphics.Typeface? = null,
    boldTypeface: android.graphics.Typeface? = null,
): Bitmap {
    val bitmap = createBitmap(TILE_GRAPH_WIDTH_PX, TILE_GRAPH_HEIGHT_PX)
    val graphColors = preferences.graphColors
    SharedWearCgmGraphRenderer.render(
        canvas = Canvas(bitmap),
        widthPx = bitmap.width,
        heightPx = bitmap.height,
        density = 2f,
        scaledDensity = 2f * fontScale,
        input =
            SharedWearCgmGraphInput(
                history = wearTileGraphPoints(state, now, preferences.graphHours, bitmap.width.toFloat()).map(WearTileGraphPoint::sample),
                timeWindow = GraphTimeWindow.live(now, preferences.graphHours * 60L * 60_000L),
                nowEpochMs = now,
                thresholds = preferences.cgmThresholds,
                palette =
                    SharedWearCgmGraphPalette(
                        background = graphColors.graphBackground,
                        targetArea = graphColors.rangeInRange,
                        highArea = graphColors.rangeHigh,
                        lowArea = graphColors.rangeLow,
                        highLine = graphColors.highLine,
                        lowLine = graphColors.lowLine,
                        dotHigh = graphColors.cgmHigh,
                        dotInRange = graphColors.cgmInRange,
                        dotLow = graphColors.cgmLow,
                        dotVeryHigh = graphColors.cgmVeryHigh,
                        dotVeryLow = graphColors.cgmVeryLow,
                        dotOutline = graphColors.outline,
                        axisText = graphColors.axisLabel,
                        axisTick = graphColors.axisTick,
                        nowLine = graphColors.nowLine,
                        border = graphColors.border,
                        predictionIob = graphColors.predictionIob,
                        predictionCob = graphColors.predictionCob,
                        predictionUam = graphColors.predictionUam,
                        predictionZeroTemp = graphColors.predictionZeroTemp,
                        targetText = graphColors.targetValue,
                        emptyText = graphColors.signalLoss,
                    ),
                style =
                    SharedWearCgmGraphStyle(
                        dotRadiusDp = preferences.graphStyle.cgmDotRadiusDp,
                        dotOutlineWidthDp = preferences.graphStyle.cgmDotOutlineWidthDp,
                        historicalDotOutlineWidthDp = preferences.graphStyle.cgmHistoricalDotOutlineWidthDp,
                        currentDotOutlineWidthDp = preferences.graphStyle.cgmCurrentDotOutlineWidthDp,
                        dotOutlineEnabled = preferences.graphStyle.cgmDotOutlineEnabled,
                        historicalDotOutlineEnabled = preferences.graphStyle.cgmHistoricalDotOutlineEnabled,
                        currentDotOutlineEnabled = true,
                        scaleLaneOpacityPercent = preferences.graphStyle.scaleLaneOpacityPercent,
                        regularTypeface = regularTypeface,
                        boldTypeface = boldTypeface,
                    ),
            ),
    )
    return bitmap
}

private fun roundedTileCard(
    colors: WatchUiColors,
    padding: Float,
    child: LayoutElementBuilders.LayoutElement,
): Box =
    Box
        .Builder()
        .setWidth(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            Modifiers
                .Builder()
                .setBackground(
                    Background
                        .Builder()
                        .setColor(argb(colors.tileBackground))
                        .setCorner(Corner.Builder().setRadius(dp(28f)).build())
                        .build(),
                ).setBorder(
                    Border
                        .Builder()
                        .setWidth(dp(1f))
                        .setColor(argb(colors.tileBorder))
                        .build(),
                ).setPadding(Padding.Builder().setAll(dp(padding)).build())
                .build(),
        ).addContent(child)
        .build()

private fun tileRoot(
    background: Int,
    child: LayoutElementBuilders.LayoutElement,
    paddingDp: Float = 18f,
): Box =
    Box
        .Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            Modifiers
                .Builder()
                .setBackground(Background.Builder().setColor(argb(background)).build())
                .setPadding(Padding.Builder().setAll(dp(paddingDp)).build())
                .build(),
        ).addContent(child)
        .build()

private fun tileTrendImage(
    scope: ProtoLayoutScope,
    spec: app.sugarlicious.model.TrendVisualSpec,
    style: app.sugarlicious.model.TrendArrowRenderSpec,
): Image =
    Image
        .Builder(scope)
        .setImageResource(
            ImageResource
                .Builder()
                .setAndroidResourceByResId(
                    AndroidImageResourceByResId
                        .Builder()
                        .setResourceId(TrendDrawableResources.forAsset(spec.asset))
                        .build(),
                ).build(),
            trendResourceId(spec.asset),
        ).setWidth(dp(GlucoseTrendSizing.arrowHeightForGlucoseHeight(42f) * style.scale * spec.aspectRatio))
        .setHeight(dp(GlucoseTrendSizing.arrowHeightForGlucoseHeight(42f) * style.scale))
        .setColorFilter(ColorFilter.Builder().setTint(argb(style.fillColor)).build())
        .build()

private fun trendResourceId(asset: TrendVisualAsset): String = "trend_${asset.name.lowercase()}"

internal fun requestSugarliciousTileUpdates(context: Context) {
    requestSugarliciousTileUpdates(context, setOf(GlucoseTileService::class.java, TherapyTileService::class.java))
}

private fun therapyTileContent(
    scope: ProtoLayoutScope,
    state: TherapyDisplayState?,
    colors: WatchUiColors,
    now: Long,
    selection: TherapyTileSelection,
    appearance: TherapyProgressAppearance,
    availableSideDp: Float,
    textRenderer: SugarliciousTileTypography,
): LayoutElementBuilders.LayoutElement {
    val presentation = wearTherapyTilePresentation(state, now)
    val effectiveAppearance = appearance.copy(enabledMetrics = selection.canonical.mapTo(linkedSetOf()) { it.toProgressMetric() })
    val side = (availableSideDp - 12f).coerceAtLeast(1f)
    val resolved = centeredTherapyTileLayout(side, effectiveAppearance)
    val group =
        Box
            .Builder()
            .setWidth(dp(side))
            .setHeight(dp(side))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
    resolved.forEach { item ->
        val card =
            therapyMetricCard(
                scope,
                item.metric.toTileMetric(),
                presentation,
                state,
                colors,
                now,
                TherapyRingLayoutSpec(item.slot.diameterDp, strokeWidthDp = item.strokeWidthDp),
                item.primaryTextSizeSp,
                item.iconSizeDp,
                textRenderer,
            )
        group.addContent(
            Box
                .Builder()
                .setWidth(dp(item.slot.rightDp))
                .setHeight(dp(item.slot.bottomDp + TherapyIndicatorGeometry.iconBottomOverflowDp(item.slot.diameterDp, item.iconSizeDp)))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(card)
                .build(),
        )
    }
    return tileRoot(colors.background, group.build(), paddingDp = 6f)
}

private fun therapyMetricCard(
    scope: ProtoLayoutScope,
    metric: TherapyTileMetric,
    presentation: WearTherapyTilePresentation,
    state: TherapyDisplayState?,
    colors: WatchUiColors,
    nowEpochMs: Long,
    ring: TherapyRingLayoutSpec,
    primaryTextSizeSp: Float,
    configuredIconSizeDp: Float,
    textRenderer: SugarliciousTileTypography,
): Box {
    val value = therapyMetricValue(metric, presentation)
    val progress = wearTherapyProgress(metric, state, nowEpochMs)
    val accent =
        when (metric) {
            TherapyTileMetric.IOB -> colors.iob
            TherapyTileMetric.COB -> colors.cob
            TherapyTileMetric.BASAL -> colors.basal
        }
    val bitmap = renderTherapyTileRing(ring, progress, accent, colors.background)
    val pixels =
        try {
            bitmap.toRgb565Image()
        } finally {
            bitmap.recycle()
        }
    val ringImage =
        Image
            .Builder(scope)
            .setImageResource(
                ImageResource
                    .Builder()
                    .setInlineResource(
                        InlineImageResource
                            .Builder()
                            .setData(pixels.data)
                            .setWidthPx(pixels.widthPx)
                            .setHeightPx(pixels.heightPx)
                            .setFormat(IMAGE_FORMAT_RGB_565)
                            .build(),
                    ).build(),
                "therapy_ring_${metric.name.lowercase()}",
            ).setWidth(dp(ring.diameterDp))
            .setHeight(dp(ring.diameterDp))
            .build()
    val fittedPrimarySp = textRenderer.fittedTextSize(value, TherapyIndicatorGeometry.fontSizeSp(primaryTextSizeSp, TherapyIndicatorGeometry.REFERENCE_DIAMETER_DP, textRenderer.fontScale), ring.diameterDp * TherapyIndicatorGeometry.VALUE_WIDTH_FRACTION, bold = true)
    val valueContent = textRenderer.text(scope, value, fittedPrimarySp, colors.textPrimary, bold = true, maxWidthDp = ring.diameterDp * TherapyIndicatorGeometry.VALUE_WIDTH_FRACTION, maxHeightDp = ring.diameterDp * .34f)
    val textContent =
        Box
            .Builder()
            .setWidth(dp(ring.diameterDp))
            .setHeight(dp(ring.diameterDp))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(valueContent)
            .build()
    val iconState = wearTherapyIcon(metric, state, nowEpochMs)
    val iconSize = configuredIconSizeDp
    val iconOverflow = TherapyIndicatorGeometry.iconBottomOverflowDp(ring.diameterDp, iconSize)
    val icon =
        Image
            .Builder(scope)
            .setImageResource(
                ImageResource
                    .Builder()
                    .setAndroidResourceByResId(
                        AndroidImageResourceByResId.Builder().setResourceId(wearTherapyIconResource(iconState)).build(),
                    ).build(),
                "therapy_${iconState.name.lowercase()}",
            ).setWidth(dp(iconSize))
            .setHeight(dp(iconSize))
            .setColorFilter(ColorFilter.Builder().setTint(argb(accent)).build())
            .build()
    val iconOverlay =
        Box
            .Builder()
            .setWidth(dp(ring.diameterDp))
            .setHeight(dp(ring.diameterDp + iconOverflow))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .setModifiers(Modifiers.Builder().setPadding(Padding.Builder().setBottom(dp((ring.diameterDp * (1f - TherapyIndicatorGeometry.ICON_CENTER_Y_FRACTION) - iconSize / 2f).coerceAtLeast(0f))).build()).build())
            .addContent(icon)
            .build()
    return Box
        .Builder()
        .setWidth(dp(ring.diameterDp))
        .setHeight(dp(ring.diameterDp + iconOverflow))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
        .addContent(ringImage)
        .addContent(textContent)
        .addContent(iconOverlay)
        .build()
}

internal fun renderTherapyTileRing(ring: TherapyRingLayoutSpec, progress: Float?, accent: Int, background: Int): Bitmap {
    val scale = 3f
    val side = (ring.diameterDp * scale).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(side, side)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)
    val stroke = ring.strokeWidthDp * scale
    val inset = stroke / 2f
    val bounds = android.graphics.RectF(inset, inset, side - inset, side - inset)
    app.sugarlicious.uishared
        .TherapyRingRenderer()
        .draw(canvas, bounds, stroke, progress, accent, ring.geometry)
    return bitmap
}

internal data class TherapyRingLayoutSpec(
    val diameterDp: Float,
    val geometry: TherapyRingGeometry = TherapyRingGeometry(),
    val strokeWidthDp: Float = geometry.strokeWidthDp,
) {
    val protoLayoutStartDegrees: Float get() = geometry.protoLayoutStartDegrees
    val sweepDegrees: Float get() = geometry.sweepDegrees
}

internal data class TherapyTileGroupGeometry(
    val ringDiameterDp: Float,
    val gapDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val originXDp: Float,
    val originYDp: Float,
)

internal fun therapyTileGroupGeometry(
    availableSideDp: Float,
    metricCount: Int,
): TherapyTileGroupGeometry {
    val count = metricCount.coerceIn(1, 3)
    val gap = 6f
    val ring =
        when (count) {
            1 -> minOf(112f, availableSideDp * 0.62f)
            else -> minOf(70f, ((availableSideDp * 0.80f) - gap) / 2f)
        }.coerceAtLeast(48f)
    val width = if (count == 1) ring else ring * 2f + gap
    val height = if (count == 3) ring * 2f + gap else ring
    return TherapyTileGroupGeometry(
        ringDiameterDp = ring,
        gapDp = gap,
        widthDp = width,
        heightDp = height,
        originXDp = (availableSideDp - width) / 2f,
        originYDp = (availableSideDp - height) / 2f,
    )
}

internal fun therapyMetricValue(
    metric: TherapyTileMetric,
    presentation: WearTherapyTilePresentation,
): String =
    when (metric) {
        TherapyTileMetric.IOB -> presentation.iob
        TherapyTileMetric.COB -> presentation.cob
        TherapyTileMetric.BASAL -> presentation.basal.removeSuffix("/h")
    }

internal fun wearTherapyProgress(
    metric: TherapyTileMetric,
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): Float? =
    when (metric) {
        TherapyTileMetric.IOB -> TherapyProgressSemantics.scaled(state?.insulin?.totalIob, 10.0)
        TherapyTileMetric.COB -> TherapyProgressSemantics.scaled(state?.carbs?.cobGrams, 300.0)
        TherapyTileMetric.BASAL -> {
            effectiveBasalPresentation(state, nowEpochMs)?.percent?.let(TherapyProgressSemantics::basal)
        }
    }

internal fun wearTherapyIcon(
    metric: TherapyTileMetric,
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): TherapyIndicatorIcon =
    when (metric) {
        TherapyTileMetric.IOB -> TherapyIndicatorIcon.IOB
        TherapyTileMetric.COB -> TherapyIndicatorIcon.COB
        TherapyTileMetric.BASAL -> basalIndicatorIcon(effectiveBasalPresentation(state, nowEpochMs)?.percent)
    }

private fun wearTherapyIconResource(icon: TherapyIndicatorIcon): Int =
    when (icon) {
        TherapyIndicatorIcon.IOB -> app.sugarlicious.uishared.R.drawable.ic_iob
        TherapyIndicatorIcon.COB -> app.sugarlicious.uishared.R.drawable.ic_cob
        TherapyIndicatorIcon.BASAL -> app.sugarlicious.uishared.R.drawable.ic_basal
        TherapyIndicatorIcon.BASAL_LESS -> app.sugarlicious.uishared.R.drawable.ic_basalless
        TherapyIndicatorIcon.BASAL_MORE -> app.sugarlicious.uishared.R.drawable.ic_basalmore
    }

internal fun affectedSugarliciousTiles(
    old: TherapyDisplayState?,
    new: TherapyDisplayState,
): Set<Class<out TileService>> {
    if (old == null) return setOf(GlucoseTileService::class.java, TherapyTileService::class.java)
    val affected = linkedSetOf<Class<out TileService>>()
    if (
        old.glucose != new.glucose ||
        old.glucoseHistory != new.glucoseHistory ||
        old.glucosePredictions != new.glucosePredictions ||
        old.target != new.target ||
        old.source != new.source
    ) {
        affected += GlucoseTileService::class.java
    }
    if (
        old.insulin != new.insulin ||
        old.carbs != new.carbs ||
        old.basal != new.basal ||
        old.therapyHistory != new.therapyHistory
    ) {
        affected += TherapyTileService::class.java
    }
    return affected
}

internal fun requestSugarliciousTileUpdates(
    context: Context,
    services: Set<Class<out TileService>>,
) {
    val updater = TileService.getUpdater(context)
    services.forEach(updater::requestUpdate)
}
