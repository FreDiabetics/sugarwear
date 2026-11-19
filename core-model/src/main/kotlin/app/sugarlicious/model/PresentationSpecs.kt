package app.sugarlicious.model

enum class PresentationSurface {
    MOBILE,
    WIDGET,
    NOTIFICATION,
    WEAR,
    COLLECTOR,
    TILE,
    COMPLICATION,
    WATCHFACE,
}

/** Semantic glucose geometry shared by renderers; renderers still own their platform primitives. */
data class GlucoseVisualSpec(
    val surface: PresentationSurface,
    val glucoseTextSize: Float,
    val trendHeight: Float,
    val unitScale: Float = 1f,
    val secondaryTextScale: Float = 1f,
    val spacing: Float,
    val baselineAligned: Boolean = true,
) {
    fun scaled(
        glucosePercent: Int,
        trendPercent: Int,
    ): GlucoseVisualSpec =
        copy(
            glucoseTextSize = glucoseTextSize * GlucoseTrendSizing.scaleFactor(glucosePercent),
            trendHeight = trendHeight * GlucoseTrendSizing.scaleFactor(trendPercent),
        )

    companion object {
        /** The accepted 2x2 widget appearance is the cross-surface ratio reference. */
        fun twoByTwoWidgetReference(): GlucoseVisualSpec =
            GlucoseVisualSpec(
                surface = PresentationSurface.WIDGET,
                glucoseTextSize = GlucoseTrendSizing.REFERENCE_GLUCOSE_TEXT_SP,
                trendHeight = GlucoseTrendSizing.REFERENCE_TREND_HEIGHT_DP,
                spacing = 8f,
            )
    }
}

data class GraphTimePolicy(
    val historyDurationMs: Long,
    val futureDurationMs: Long = 0L,
    val anchorAtLatestCgm: Boolean = true,
)

data class GraphAxisSpec(
    val showRelativeTime: Boolean = true,
    val showNowLabel: Boolean = true,
    val targetScaleSide: AxisSide = AxisSide.RIGHT,
)

/** Shared semantic spacing for the plot -> tick -> label relationship on every graph surface. */
data class GraphAxisLayoutSpec(
    val plotToTickGapDp: Float = 2f,
    val tickLengthDp: Float = 5f,
    val tickToLabelGapDp: Float = 3f,
    val outerEdgePaddingDp: Float = 6f,
) {
    init {
        require(plotToTickGapDp >= 0f)
        require(tickLengthDp > 0f)
        require(tickToLabelGapDp >= 0f)
        require(outerEdgePaddingDp >= 0f)
    }

    companion object {
        val DEFAULT = GraphAxisLayoutSpec(plotToTickGapDp = 3f, tickLengthDp = 4f, tickToLabelGapDp = 2f)
        val COMPACT = GraphAxisLayoutSpec(plotToTickGapDp = 2.4f, tickLengthDp = 3.2f, tickToLabelGapDp = 1.6f, outerEdgePaddingDp = 4f)
    }
}

/** System trend size plus sparse surface overrides; null means inherit the system value. */
data class TrendAppearance(
    val systemTrendScalePercent: Int = GlucoseTrendSizing.DEFAULT_SCALE_PERCENT,
    val surfaceOverrides: Map<PresentationSurface, Int> = emptyMap(),
) {
    fun resolvedPercent(surface: PresentationSurface): Int =
        (surfaceOverrides[surface] ?: systemTrendScalePercent)
            .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)

    fun withOverride(
        surface: PresentationSurface,
        percent: Int?,
    ): TrendAppearance =
        copy(
            surfaceOverrides =
                if (percent == null || percent == systemTrendScalePercent) {
                    surfaceOverrides - surface
                } else {
                    surfaceOverrides +
                        (surface to percent.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT))
                },
        )
}

enum class AxisSide { LEFT, RIGHT }

data class GraphRangeSpec(
    val lowMgDl: Int,
    val highMgDl: Int,
    val minimumMgDl: Int = 40,
    val confirmationCount: Int = 2,
)

data class GraphDotSpec(
    val diameterDp: Float,
    val outlineWidthDp: Float,
)

data class GraphSpec(
    val time: GraphTimePolicy,
    val axis: GraphAxisSpec,
    val range: GraphRangeSpec,
    val dots: GraphDotSpec,
    val showPredictions: Boolean,
    val showTreatments: Boolean,
)

/** Persisted schema versions. Every incompatible settings change must add a migration. */
object SettingsSchemaVersions {
    const val APPEARANCE = 4
    const val WIDGET = 3
    const val WEAR = 6
    const val COLLECTOR = 3
}
