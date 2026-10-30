package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingOrigin
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.CanonicalCgmHistory
import app.aapswear.model.CgmQuality
import app.aapswear.model.GlucoseSample
import app.aapswear.uishared.SharedWearCgmGraphInput
import app.aapswear.uishared.SharedWearCgmGraphPalette
import app.aapswear.uishared.SharedWearCgmGraphStyle

/** Shared SugarWear adapter. Both the in-app Canvas and Graph Tile use this exact input. */
internal fun g7SharedGraphInput(
    readings: List<CgmReading>,
    palette: G7AppearancePalette,
    settings: G7DirectToWatchSettingsStore,
    graphHours: Int,
    nowEpochMs: Long,
    emptyLabel: String = "",
    styleOverride: SharedWearCgmGraphStyle? = null,
    outsideClipColor: Int? = null,
    topLeftLabel: String = "",
): SharedWearCgmGraphInput {
    val colors =
        settings.graphColors().copy(
            graphBackground = palette.argb(G7AppearanceRole.GRAPH_BACKGROUND),
            rangeLow = palette.argb(G7AppearanceRole.GRAPH_LOW_AREA),
            rangeInRange = palette.argb(G7AppearanceRole.GRAPH_TARGET_AREA),
            rangeHigh = palette.argb(G7AppearanceRole.GRAPH_HIGH_AREA),
            cgmLow = palette.argb(G7AppearanceRole.GRAPH_DOT_LOW),
            cgmInRange = palette.argb(G7AppearanceRole.GRAPH_DOT_IN_RANGE),
            cgmHigh = palette.argb(G7AppearanceRole.GRAPH_DOT_HIGH),
            cgmVeryLow = palette.argb(G7AppearanceRole.GLUCOSE_VERY_LOW),
            cgmVeryHigh = palette.argb(G7AppearanceRole.GLUCOSE_VERY_HIGH),
            divider = palette.argb(G7AppearanceRole.GRAPH_TILE_BORDER),
            border = palette.argb(G7AppearanceRole.GRAPH_TILE_BORDER),
            highLine = palette.argb(G7AppearanceRole.GRAPH_HIGH_LINE),
            lowLine = palette.argb(G7AppearanceRole.GRAPH_LOW_LINE),
            axisLabel = palette.argb(G7AppearanceRole.GRAPH_AXIS_TEXT),
            axisTick = palette.argb(G7AppearanceRole.GRAPH_GRID),
            outline = palette.argb(G7AppearanceRole.GRAPH_DOT_OUTLINE),
        )
    val hours = graphHours.takeIf { it in G7DirectToWatchSettingsStore.HOUR_OPTIONS } ?: 3
    return SharedWearCgmGraphInput(
        history =
            CanonicalCgmHistory.merge(
                samples = normalizeG7LocalHistory(readings).map(CgmReading::toG7GraphSample),
                nowEpochMs = nowEpochMs,
                windowMs = hours * 60L * 60_000L,
            ),
        timeWindow = g7CollectorGraphWindow(nowEpochMs, hours),
        nowEpochMs = nowEpochMs,
        thresholds = settings.thresholds(),
        palette =
            SharedWearCgmGraphPalette(
                background = colors.graphBackground,
                targetArea = colors.rangeInRange,
                highArea = colors.rangeHigh,
                lowArea = colors.rangeLow,
                highLine = colors.highLine,
                lowLine = colors.lowLine,
                dotHigh = colors.cgmHigh,
                dotInRange = colors.cgmInRange,
                dotLow = colors.cgmLow,
                dotVeryHigh = colors.cgmVeryHigh,
                dotVeryLow = colors.cgmVeryLow,
                dotOutline = colors.outline,
                axisText = colors.axisLabel,
                axisTick = colors.axisTick,
                nowLine = colors.nowLine,
                border = colors.border,
                predictionIob = colors.predictionIob,
                predictionCob = colors.predictionCob,
                predictionUam = colors.predictionUam,
                predictionZeroTemp = colors.predictionZeroTemp,
                targetText = colors.targetValue,
                emptyText = colors.signalLoss,
            ),
        style = styleOverride ?: settings.graphStyle(),
        emptyLabel = emptyLabel,
        topLeftLabel = topLeftLabel,
        outsideClipColor = outsideClipColor ?: colors.graphBackground,
    )
}

internal fun normalizeG7LocalHistory(source: List<CgmReading>): List<CgmReading> {
    val valid = source.filter { it.status == CgmReadingStatus.VALID }
    val newest = valid.maxByOrNull(CgmReading::timestampEpochMs)
    val sameSession =
        newest
            ?.let { latest ->
                valid.filter { it.sensorId == latest.sensorId && it.sessionId == latest.sessionId }
            }.orEmpty()

    return sameSession
        .groupBy { reading -> Triple(reading.sensorId, reading.sessionId, reading.timestampEpochMs) }
        .values
        .map { duplicates ->
            duplicates.maxWithOrNull(
                compareBy<CgmReading> { if (it.origin == CgmReadingOrigin.LIVE) 1 else 0 }
                    .thenBy(CgmReading::receivedAtEpochMs),
            ) ?: duplicates.first()
        }.sortedBy(CgmReading::timestampEpochMs)
}

private fun CgmReading.toG7GraphSample() =
    GlucoseSample(
        valueMgDl = glucoseMgDl,
        measuredAtEpochMs = timestampEpochMs,
        source = source,
        sensorId = sensorId,
        sessionId = sessionId,
        sequenceNumber = sequenceNumber,
        receivedAtEpochMs = receivedAtEpochMs,
        quality = CgmQuality.VALID,
    )
