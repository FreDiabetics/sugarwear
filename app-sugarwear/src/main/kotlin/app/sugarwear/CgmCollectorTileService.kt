package app.sugarwear

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Border
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.sugarlicious.model.ArgbContrast
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GlucoseVisualSpec
import app.sugarlicious.model.PresentationSurface
import app.sugarlicious.model.SugarWearTypography
import app.sugarlicious.model.SugarWearTypographyRole
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisualAsset
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.model.WearGlucoseCardInput
import app.sugarlicious.model.WearGlucoseCardStyle
import app.sugarlicious.model.cgmBoundaryDisplay
import app.sugarlicious.model.wearGlucoseCardPresentation
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.uishared.TrendDrawableResources
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

internal data class CgmTilePresentation(
    val glucoseValue: String,
    val meta: String,
    val age: String,
    val cardBackground: Int,
    val cardForeground: Int,
    val trend: Trend? = null,
) {
    val value: String
        get() {
            val arrow = trend?.let(TherapyDisplayFormatter::trendArrow).orEmpty()
            return listOf(glucoseValue, arrow).filter(String::isNotBlank).joinToString(" ")
        }

    val tileValue: String
        get() = glucoseValue

    val tileMeta: String
        get() = listOf(meta, age).filter(String::isNotBlank).joinToString("  ·  ")

    // Compatibility properties keep the in-app glucose card on the same validated presentation
    // without duplicating source, freshness or range logic in CgmWatchActivity.
    val background: Int
        get() = cardBackground

    val foreground: Int
        get() = cardForeground
}

internal data class CgmTileStatusPresentation(
    val label: String,
    val color: Int,
)

internal fun cgmTilePresentation(
    reading: CgmReading?,
    colors: app.sugarlicious.protocol.WatchGraphColors,
    nowEpochMs: Long,
    thresholds: app.sugarlicious.model.CgmThresholds = app.sugarlicious.model.CgmThresholds.DEFAULT,
    palette: CgmAppearancePalette? = null,
): CgmTilePresentation {
    val shared =
        wearGlucoseCardPresentation(
            WearGlucoseCardInput(
                valueMgDl = reading?.glucoseMgDl,
                displayUnit = GlucoseUnit.MG_DL,
                deltaMgDl = reading?.deltaMgDl,
                trend = reading?.trend ?: Trend.UNKNOWN,
                measuredAtEpochMs = reading?.timestampEpochMs,
                quality =
                    when (reading?.status) {
                        CgmReadingStatus.VALID -> CgmQuality.VALID
                        CgmReadingStatus.SENSOR_ERROR -> CgmQuality.SENSOR_ERROR
                        else -> CgmQuality.INVALID
                    },
                sourceLabel = "",
            ),
            thresholds,
            nowEpochMs,
        )
    val valueColor =
        if (shared.freshness == app.sugarlicious.model.Freshness.SIGNAL_LOSS) {
            0xFF9E9E9E.toInt()
        } else {
            when (shared.rangeClass) {
                CgmRangeClass.VERY_LOW -> palette?.argb(CgmAppearanceRole.GLUCOSE_VERY_LOW) ?: colors.cgmVeryLow
                CgmRangeClass.LOW -> palette?.argb(CgmAppearanceRole.GLUCOSE_LOW) ?: colors.cgmLow
                CgmRangeClass.HIGH -> palette?.argb(CgmAppearanceRole.GLUCOSE_HIGH) ?: colors.cgmHigh
                CgmRangeClass.VERY_HIGH -> palette?.argb(CgmAppearanceRole.GLUCOSE_VERY_HIGH) ?: colors.cgmVeryHigh
                else -> palette?.argb(CgmAppearanceRole.GLUCOSE_IN_RANGE) ?: CGM_TILE_TEXT_PRIMARY
            }
        }
    val boundary = reading?.takeIf { it.status == CgmReadingStatus.VALID }?.glucoseMgDl.let(::cgmBoundaryDisplay)
    return CgmTilePresentation(
        glucoseValue = boundary?.label ?: shared.value,
        meta = if (boundary == null) shared.primaryMeta else "",
        age = shared.secondaryMeta,
        cardBackground = palette?.argb(CgmAppearanceRole.MENU_SURFACE) ?: CGM_TILE_CARD_BACKGROUND,
        cardForeground = valueColor,
        trend = shared.trend.takeIf { boundary == null },
    )
}

internal fun cgmTileStatusPresentation(
    status: CgmStatusPillState,
    palette: CgmAppearancePalette? = null,
): CgmTileStatusPresentation {
    val color =
        when (status) {
            CgmStatusPillState.CONNECTED -> palette?.argb(CgmAppearanceRole.MENU_PRIMARY) ?: CGM_TILE_ACCENT
            CgmStatusPillState.SIGNAL_LOSS -> android.graphics.Color.WHITE
            CgmStatusPillState.SENSOR_ERROR -> palette?.argb(CgmAppearanceRole.GLUCOSE_ERROR) ?: CGM_TILE_ERROR
            CgmStatusPillState.PAUSED, CgmStatusPillState.NO_ACTIVE_SENSOR -> palette?.argb(CgmAppearanceRole.GLUCOSE_NO_SOURCE) ?: CGM_TILE_TEXT_SECONDARY
        }
    return CgmTileStatusPresentation(status.title.uppercase(Locale.GERMANY), color)
}

internal fun tileForegroundFor(backgroundArgb: Int): Int = if (ArgbContrast.isLight(backgroundArgb, threshold = 0.50)) CGM_TILE_TEXT_DARK else CGM_TILE_TEXT_PRIMARY

internal fun sugarWearTileWeight(emphasized: Boolean): Int = SugarWearTypography.protoWeight(emphasized)

internal fun sugarWearTileFontFamily(manufacturer: String): String =
    if (manufacturer.equals("samsung", ignoreCase = true)) "sec" else "sans-serif"

class CgmCollectorTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceVendorOneTypefaces =
        CgmTileTypefaceCache { bold ->
            app.sugarlicious.uishared.loadDeviceTileTypeface(this, bold)
        }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        deviceVendorOneTypefaces.clear()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): SettableFuture<Tile> {
        val future = SettableFuture.create<Tile>()
        tileScope.launch {
            runCatching {
                val rendered = layout(requestParams)
                Tile
                    .Builder()
                    .setResourcesVersion(rendered.version)
                    .setFreshnessIntervalMillis(60_000L)
                    .setTileTimeline(Timeline.fromLayoutElement(rendered.element))
                    .build()
            }.onSuccess(future::set)
                .onFailure(future::setException)
        }
        return future
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private data class RenderedTile(
        val element: LayoutElementBuilders.LayoutElement,
        val version: String,
    )

    private suspend fun layout(requestParams: RequestBuilders.TileRequest): RenderedTile {
        val reading =
            CgmReadingDatabase(this@CgmCollectorTileService).let { database ->
                try {
                    database.getLatest()
                } finally {
                    database.close()
                }
            }
        val persistedState = CgmSensorStateStore(this).read()
        val credentialsPresent = CgmCredentialStore(this).read() != null
        val pillState = deriveCgmStatusPillState(persistedState, credentialsPresent)
        val colorStore = CgmGraphColorStore(this)
        val appearanceStore = CgmAppearanceStore(this)
        val palette = appearanceStore.tileGlucosePalette()
        val presentation = cgmTilePresentation(reading, colorStore.read(), System.currentTimeMillis(), colorStore.readThresholds(), palette)
        val warning = cgmSensorWarning(pillState, reading?.status == CgmReadingStatus.VALID)
        val configuredTrendStyle = appearanceStore.tileTrendArrowStyle()
        val trendStyle = configuredTrendStyle.renderSpec()
        val visualSpec =
            GlucoseVisualSpec(
                surface = PresentationSurface.TILE,
                glucoseTextSize = WearGlucoseCardStyle.VALUE_TEXT_SP,
                trendHeight = GlucoseTrendSizing.arrowHeightForGlucoseHeight(WearGlucoseCardStyle.VALUE_TEXT_SP),
                spacing = 8f,
            ).scaled(appearanceStore.tileGlucoseScalePercent(), configuredTrendStyle.sizePercent)
        val device = requestParams.deviceConfiguration
        val square = cgmSquareTileSpec(device.screenWidthDp, device.screenHeightDp)
        val cardHeight = if (warning != null) square.sideDp else square.sideDp - TILE_HEADER_LANE_DP
        val valueTextSize = (visualSpec.glucoseTextSize * square.sideDp / 146f).coerceIn(30f, 46f)
        val trendHeight = (visualSpec.trendHeight * square.sideDp / 146f).coerceIn(24f, 40f)

        val primaryRow =
            Row
                .Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(text(requestParams.scope, presentation.tileValue, valueTextSize, presentation.cardForeground, bold = true))
                .apply {
                    val spec = presentation.trend?.let(TrendVisuals::spec)
                    if (spec != null) {
                        addContent(Spacer.Builder().setWidth(dp(8f)).build())
                        addContent(trendImage(requestParams.scope, spec, trendStyle.fillColor, trendHeight))
                    }
                }.build()

        val valueContent =
            Column
                .Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(primaryRow)
                .apply {
                    if (presentation.meta.isNotBlank()) {
                        addContent(
                            metadataText(
                                requestParams.scope,
                                presentation.meta,
                                SugarWearTypography.spec(SugarWearTypographyRole.META).sizeSp,
                                android.graphics.Color.WHITE,
                                bold = true,
                            ),
                        )
                    }
                    if (presentation.age.isNotBlank()) {
                        addContent(
                            metadataText(
                                requestParams.scope,
                                presentation.age,
                                SugarWearTypography.spec(SugarWearTypographyRole.META).sizeSp,
                                android.graphics.Color.WHITE,
                                bold = true,
                            ),
                        )
                    }
                }.build()

        val valueCard =
            Box
                .Builder()
                .setWidth(dp(square.sideDp))
                .setHeight(dp(cardHeight))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    Modifiers
                        .Builder()
                        .setBackground(
                            Background
                                .Builder()
                                .setColor(argb(presentation.cardBackground))
                                .setCorner(Corner.Builder().setRadius(dp(square.cornerRadiusDp)).build())
                                .build(),
                        ).setBorder(
                            Border
                                .Builder()
                                .setColor(argb(palette.argb(CgmAppearanceRole.MENU_BORDER)))
                                .setWidth(dp(1f))
                                .build(),
                        ).setPadding(
                            Padding
                                .Builder()
                                .setStart(dp(square.innerPaddingDp))
                                .setEnd(dp(square.innerPaddingDp))
                                .setTop(dp(square.innerPaddingDp))
                                .setBottom(dp(square.innerPaddingDp))
                                .build(),
                        ).build(),
                ).addContent(if (warning != null) glucoseTileWarning(requestParams.scope, warning, square.sideDp - 2f * square.innerPaddingDp) else valueContent)
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
                ).addContent(text(requestParams.scope, "Gewebeglukose", 11f, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY), bold = false))
                .build()
        val content =
            Column
                .Builder()
                .setWidth(dp(square.sideDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT)
                .apply {
                    if (warning == null) {
                        addContent(header)
                        addContent(Spacer.Builder().setHeight(dp(TILE_HEADER_GAP_DP)).build())
                    }
                }.addContent(valueCard)
                .build()

        val root =
            Box
                .Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    Modifiers
                        .Builder()
                        .setBackground(Background.Builder().setColor(argb(palette.argb(CgmAppearanceRole.MENU_BACKGROUND))).build())
                        .setClickable(
                            Clickable
                                .Builder()
                                .setId(OPEN_COLLECTOR_CLICK_ID)
                                .setOnClick(
                                    ActionBuilders.launchAction(
                                        ComponentName(this, CgmWatchActivity::class.java),
                                    ),
                                ).build(),
                        ).build(),
                ).addContent(content)
                .build()
        val token = listOf(presentation, warning, configuredTrendStyle, CgmAppearanceRole.entries.map { palette.argb(it) }, resources.configuration.fontScale, resources.configuration.densityDpi).hashCode()
        return RenderedTile(root, "$RESOURCES_VERSION-$token")
    }

    private fun trendImage(
        scope: ProtoLayoutScope,
        spec: app.sugarlicious.model.TrendVisualSpec,
        color: Int,
        height: Float,
    ): LayoutElementBuilders.LayoutElement {
        val width = height * spec.aspectRatio
        return Column
            .Builder()
            .setWidth(dp(width))
            .setHeight(dp(height + 2f * CGM_TREND_OPTICAL_OFFSET_DP))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(Spacer.Builder().setHeight(dp(2f * CGM_TREND_OPTICAL_OFFSET_DP)).build())
            .addContent(
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
                    ).setWidth(dp(width))
                    .setHeight(dp(height))
                    .setColorFilter(ColorFilter.Builder().setTint(argb(color)).build())
                    .build(),
            ).build()
    }

    /** DeviceVendor's Tile host ignores installed FlipFont families in native ProtoLayout text. */
    private fun deviceVendorOneText(
        scope: ProtoLayoutScope,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
        metadata: Boolean,
    ): LayoutElementBuilders.LayoutElement? {
        if (!android.os.Build.MANUFACTURER
                .equals("samsung", ignoreCase = true)
        ) {
            return null
        }
        val face = deviceVendorOneTypefaces.get(bold) ?: return null
        val content = if (metadata) app.sugarlicious.uishared.glucoseMetadataText(value) else value
        val bitmap = app.sugarlicious.uishared.renderDeviceTileTextBitmap(this, face, content, size, color)
        val bytes =
            java.io
                .ByteArrayOutputStream()
                .apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }
                .toByteArray()
        val density = resources.displayMetrics.density
        val image =
            ImageResource
                .Builder()
                .setInlineResource(
                    androidx.wear.protolayout.ResourceBuilders.InlineImageResource
                        .Builder()
                        .setData(bytes)
                        .setWidthPx(bitmap.width)
                        .setHeightPx(bitmap.height)
                        .build(),
                ).build()
        val id = "samsung_one_${value.hashCode()}_${size}_${color}_${bold}_${resources.configuration.fontScale}"
        val result =
            Image
                .Builder(scope)
                .setImageResource(image, id)
                .setWidth(dp(bitmap.width / density))
                .setHeight(dp(bitmap.height / density))
                .setModifiers(
                    Modifiers
                        .Builder()
                        .setSemantics(
                            androidx.wear.protolayout.ModifiersBuilders.Semantics
                                .Builder()
                                .setContentDescription(value)
                                .build(),
                        ).build(),
                ).build()
        bitmap.recycle()
        return result
    }

    private fun metadataText(scope: ProtoLayoutScope, value: String, size: Float, color: Int, bold: Boolean): LayoutElementBuilders.LayoutElement {
        deviceVendorOneText(scope, value, size, color, bold, metadata = true)?.let { return it }
        val builder = LayoutElementBuilders.Spannable.Builder().setMaxLines(1)
        Regex("[A-Za-z/]+|[^A-Za-z/]+").findAll(value).forEach { match ->
            val secondary = match.value.first().isLetter() || match.value.first() == '/'
            builder.addSpan(
                LayoutElementBuilders.SpanText
                    .Builder()
                    .setText(match.value)
                    .setFontStyle(
                        FontStyle
                            .Builder()
                            .setSize(sp(size * if (secondary) .82f else 1f))
                            .setColor(argb(if (secondary) CGM_TILE_TEXT_SECONDARY else color))
                            .setWeight(sugarWearTileWeight(bold))
                            .setPreferredFontFamilies(sugarWearTileFontFamily(android.os.Build.MANUFACTURER))
                            .build(),
                    ).build(),
            )
        }
        return builder.build()
    }

    private fun text(
        scope: ProtoLayoutScope,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
    ): LayoutElementBuilders.LayoutElement =
        deviceVendorOneText(scope, value, size, color, bold, metadata = false) ?: Text
            .Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle
                    .Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(sugarWearTileWeight(bold))
                    .setPreferredFontFamilies(sugarWearTileFontFamily(android.os.Build.MANUFACTURER))
                    .build(),
            ).build()

    companion object {
        private const val RESOURCES_VERSION = "g7-collector-23-white-metadata-values"
        private const val OPEN_COLLECTOR_CLICK_ID = "open_g7_watch_collector"
        private const val TILE_HEADER_LANE_DP = 21f
        private const val TILE_HEADER_GAP_DP = 4f

        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(CgmCollectorTileService::class.java)
            TileService.getUpdater(context).requestUpdate(CgmGraphTileService::class.java)
        }
    }
}

private fun trendResourceId(asset: TrendVisualAsset): String = "trend_${asset.name.lowercase()}"

internal fun withTileAlpha(
    color: Int,
    alpha: Int,
): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

internal const val CGM_TILE_BACKGROUND = 0xFF181818.toInt()
internal const val CGM_TILE_CARD_BACKGROUND = 0xFF242424.toInt()
internal const val CGM_TILE_CARD_BORDER = 0xFF404040.toInt()
internal const val CGM_TILE_TEXT_PRIMARY = 0xFFF5F5F5.toInt()
internal const val CGM_TILE_TEXT_SECONDARY = 0xFFB5B5B5.toInt()
internal const val CGM_TILE_TEXT_DARK = 0xFF181818.toInt()
internal const val CGM_TILE_ACCENT = 0xFF6DE892.toInt()
internal const val CGM_TILE_WARNING = 0xFFFFC107.toInt()
internal const val CGM_TILE_ERROR = 0xFFFF5C69.toInt()
