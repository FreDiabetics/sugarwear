package app.sugarwear

import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.uishared.SharedWearCgmGraphInput
import app.sugarlicious.uishared.SharedWearCgmGraphPalette
import app.sugarlicious.uishared.SharedWearCgmGraphStyle

/** Shared SugarWear adapter. Both the in-app Canvas and Graph Tile use this exact input. */
internal fun cgmSharedGraphInput(
    readings: List<CgmReading>,
    palette: CgmAppearancePalette,
    settings: CgmDirectToWatchSettingsStore,
    graphHours: Int,
    nowEpochMs: Long,
    emptyLabel: String = "",
    styleOverride: SharedWearCgmGraphStyle? = null,
    outsideClipColor: Int? = null,
    topLeftLabel: String = "",
): SharedWearCgmGraphInput {
    val colors =
        settings.graphColors().copy(
            graphBackground = palette.argb(CgmAppearanceRole.GRAPH_BACKGROUND),
            rangeLow = palette.argb(CgmAppearanceRole.GRAPH_LOW_AREA),
            rangeInRange = palette.argb(CgmAppearanceRole.GRAPH_TARGET_AREA),
            rangeHigh = palette.argb(CgmAppearanceRole.GRAPH_HIGH_AREA),
            cgmLow = palette.argb(CgmAppearanceRole.GRAPH_DOT_LOW),
            cgmInRange = palette.argb(CgmAppearanceRole.GRAPH_DOT_IN_RANGE),
            cgmHigh = palette.argb(CgmAppearanceRole.GRAPH_DOT_HIGH),
            cgmVeryLow = palette.argb(CgmAppearanceRole.GLUCOSE_VERY_LOW),
            cgmVeryHigh = palette.argb(CgmAppearanceRole.GLUCOSE_VERY_HIGH),
            divider = palette.argb(CgmAppearanceRole.GRAPH_TILE_BORDER),
            border = palette.argb(CgmAppearanceRole.GRAPH_TILE_BORDER),
            highLine = palette.argb(CgmAppearanceRole.GRAPH_HIGH_LINE),
            lowLine = palette.argb(CgmAppearanceRole.GRAPH_LOW_LINE),
            axisLabel = palette.argb(CgmAppearanceRole.GRAPH_AXIS_TEXT),
            axisTick = palette.argb(CgmAppearanceRole.GRAPH_GRID),
            outline = palette.argb(CgmAppearanceRole.GRAPH_DOT_OUTLINE),
        )
    val hours = graphHours.takeIf { it in CgmDirectToWatchSettingsStore.HOUR_OPTIONS } ?: 3
    return SharedWearCgmGraphInput(
        history =
            CanonicalCgmHistory.merge(
                samples = normalizeCgmLocalHistory(readings).map(CgmReading::toCgmGraphSample),
                nowEpochMs = nowEpochMs,
                windowMs = hours * 60L * 60_000L,
            ),
        timeWindow = cgmCollectorGraphWindow(nowEpochMs, hours),
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

internal fun normalizeCgmLocalHistory(source: List<CgmReading>): List<CgmReading> {
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

private fun CgmReading.toCgmGraphSample() =
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
