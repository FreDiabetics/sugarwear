package app.sugarwear

import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.uishared.SharedWearCgmGraphStyle

// Preserve fields changed by preceding queued commands while applying this renderer's edit.
internal fun mergeCgmGraphStyleEdit(baseline: SharedWearCgmGraphStyle, edited: SharedWearCgmGraphStyle, current: SharedWearCgmGraphStyle): SharedWearCgmGraphStyle =
    current.copy(
        dotRadiusDp = if (edited.dotRadiusDp != baseline.dotRadiusDp) edited.dotRadiusDp else current.dotRadiusDp,
        dotOutlineWidthDp = if (edited.dotOutlineWidthDp != baseline.dotOutlineWidthDp) edited.dotOutlineWidthDp else current.dotOutlineWidthDp,
        historicalDotOutlineEnabled = if (edited.historicalDotOutlineEnabled != baseline.historicalDotOutlineEnabled) edited.historicalDotOutlineEnabled else current.historicalDotOutlineEnabled,
        historicalDotOutlineWidthDp = if (edited.historicalDotOutlineWidthDp != baseline.historicalDotOutlineWidthDp) edited.historicalDotOutlineWidthDp else current.historicalDotOutlineWidthDp,
        currentDotOutlineWidthDp = if (edited.currentDotOutlineWidthDp != baseline.currentDotOutlineWidthDp) edited.currentDotOutlineWidthDp else current.currentDotOutlineWidthDp,
        cornerRadiusDp = if (edited.cornerRadiusDp != baseline.cornerRadiusDp) edited.cornerRadiusDp else current.cornerRadiusDp,
        borderEnabled = if (edited.borderEnabled != baseline.borderEnabled) edited.borderEnabled else current.borderEnabled,
        timeAxisEnabled = if (edited.timeAxisEnabled != baseline.timeAxisEnabled) edited.timeAxisEnabled else current.timeAxisEnabled,
        scaleLaneOpacityPercent = if (edited.scaleLaneOpacityPercent != baseline.scaleLaneOpacityPercent) edited.scaleLaneOpacityPercent else current.scaleLaneOpacityPercent,
        maximumScaleEnabled = if (edited.maximumScaleEnabled != baseline.maximumScaleEnabled) edited.maximumScaleEnabled else current.maximumScaleEnabled,
    )

internal fun mergeCgmTrendStyleEdit(baseline: TrendArrowStyle, edited: TrendArrowStyle, current: TrendArrowStyle): TrendArrowStyle =
    current.copy(
        fillColor = if (edited.fillColor != baseline.fillColor) edited.fillColor else current.fillColor,
        outlineEnabled = if (edited.outlineEnabled != baseline.outlineEnabled) edited.outlineEnabled else current.outlineEnabled,
        outlineColor = if (edited.outlineColor != baseline.outlineColor) edited.outlineColor else current.outlineColor,
        outlineThicknessDp = if (edited.outlineThicknessDp != baseline.outlineThicknessDp) edited.outlineThicknessDp else current.outlineThicknessDp,
        sizePercent = if (edited.sizePercent != baseline.sizePercent) edited.sizePercent else current.sizePercent,
        alpha = if (edited.alpha != baseline.alpha) edited.alpha else current.alpha,
    )

internal fun mergeCgmGraphColorEdit(baseline: WatchGraphColors, edited: WatchGraphColors, current: WatchGraphColors): WatchGraphColors =
    current.copy(
        graphBackground = if (edited.graphBackground != baseline.graphBackground) edited.graphBackground else current.graphBackground,
        rangeLow = if (edited.rangeLow != baseline.rangeLow) edited.rangeLow else current.rangeLow,
        rangeInRange = if (edited.rangeInRange != baseline.rangeInRange) edited.rangeInRange else current.rangeInRange,
        rangeHigh = if (edited.rangeHigh != baseline.rangeHigh) edited.rangeHigh else current.rangeHigh,
        cgmLow = if (edited.cgmLow != baseline.cgmLow) edited.cgmLow else current.cgmLow,
        cgmInRange = if (edited.cgmInRange != baseline.cgmInRange) edited.cgmInRange else current.cgmInRange,
        cgmHigh = if (edited.cgmHigh != baseline.cgmHigh) edited.cgmHigh else current.cgmHigh,
        cgmVeryLow = if (edited.cgmVeryLow != baseline.cgmVeryLow) edited.cgmVeryLow else current.cgmVeryLow,
        cgmVeryHigh = if (edited.cgmVeryHigh != baseline.cgmVeryHigh) edited.cgmVeryHigh else current.cgmVeryHigh,
        divider = if (edited.divider != baseline.divider) edited.divider else current.divider,
        border = if (edited.border != baseline.border) edited.border else current.border,
        highLine = if (edited.highLine != baseline.highLine) edited.highLine else current.highLine,
        lowLine = if (edited.lowLine != baseline.lowLine) edited.lowLine else current.lowLine,
        axisLabel = if (edited.axisLabel != baseline.axisLabel) edited.axisLabel else current.axisLabel,
        axisTick = if (edited.axisTick != baseline.axisTick) edited.axisTick else current.axisTick,
        nowLine = if (edited.nowLine != baseline.nowLine) edited.nowLine else current.nowLine,
        outline = if (edited.outline != baseline.outline) edited.outline else current.outline,
        predictionIob = if (edited.predictionIob != baseline.predictionIob) edited.predictionIob else current.predictionIob,
        predictionCob = if (edited.predictionCob != baseline.predictionCob) edited.predictionCob else current.predictionCob,
        predictionUam = if (edited.predictionUam != baseline.predictionUam) edited.predictionUam else current.predictionUam,
        predictionZeroTemp = if (edited.predictionZeroTemp != baseline.predictionZeroTemp) edited.predictionZeroTemp else current.predictionZeroTemp,
        targetValue = if (edited.targetValue != baseline.targetValue) edited.targetValue else current.targetValue,
        signalLoss = if (edited.signalLoss != baseline.signalLoss) edited.signalLoss else current.signalLoss,
    )
