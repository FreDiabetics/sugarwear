package app.sugarlicious.model

/** Persistence ownership. Values never cross an application boundary implicitly. */
enum class AppearanceOwner {
    MOBILE,
    WEAR,
    COLLECTOR,
}

/** Resolution order inside one owner: app default -> surface -> component -> instance. */
enum class AppearanceScopeLevel {
    APP_DEFAULT,
    SURFACE,
    COMPONENT,
    INSTANCE,
}

data class AppearanceScope(
    val owner: AppearanceOwner,
    val level: AppearanceScopeLevel,
    val surface: PresentationSurface? = null,
    val componentId: String? = null,
    val instanceId: String? = null,
) {
    init {
        require(level == AppearanceScopeLevel.APP_DEFAULT || surface != null)
        require(level < AppearanceScopeLevel.COMPONENT || !componentId.isNullOrBlank())
        require(level != AppearanceScopeLevel.INSTANCE || !instanceId.isNullOrBlank())
    }
}

enum class AppearanceValueType { COLOR_ARGB, BOOLEAN, FLOAT, INTEGER, ENUM }

/** Developer-facing provenance for the effective value shown by a renderer. */
data class AppearanceResolution<T>(
    val settingKey: String,
    val scope: AppearanceScope,
    val sourceLevel: AppearanceScopeLevel,
    val value: T,
    val defaultValue: T,
)

data class AppearanceSettingDefinition(
    val key: String,
    val type: AppearanceValueType,
    val defaultValue: String,
    val minimum: Float? = null,
    val maximum: Float? = null,
    val previewable: Boolean = true,
) {
    init {
        require(key.isNotBlank())
        require(minimum == null || maximum == null || minimum <= maximum)
    }
}

/** Complete cross-surface trend style. Renderers consume [renderSpec], never raw preferences. */
data class TrendArrowStyle(
    val fillColor: Int,
    val outlineEnabled: Boolean,
    val outlineColor: Int,
    val outlineThicknessDp: Float,
    val sizePercent: Int,
    val alpha: Float,
) {
    fun normalized(): TrendArrowStyle =
        copy(
            outlineThicknessDp = outlineThicknessDp.coerceIn(MIN_OUTLINE_DP, MAX_OUTLINE_DP),
            sizePercent = sizePercent.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            alpha = alpha.coerceIn(0f, 1f),
        )

    fun renderSpec(): TrendArrowRenderSpec {
        val value = normalized()
        return TrendArrowRenderSpec(
            fillColor = ArgbColor.withAlpha(value.fillColor, value.alpha),
            outlineColor = ArgbColor.withAlpha(value.outlineColor, value.alpha),
            outlineThicknessDp = if (value.outlineEnabled) value.outlineThicknessDp else 0f,
            scale = GlucoseTrendSizing.scaleFactor(value.sizePercent),
        )
    }

    companion object {
        const val MIN_OUTLINE_DP = 0.25f
        const val MAX_OUTLINE_DP = 4f

        fun defaults(
            mode: AppearanceMode,
            fillColor: Int,
        ): TrendArrowStyle =
            TrendArrowStyle(
                fillColor = fillColor,
                outlineEnabled = mode == AppearanceMode.DARK,
                outlineColor = 0xAE000000.toInt(),
                outlineThicknessDp = 0.65f,
                sizePercent = GlucoseTrendSizing.DEFAULT_SCALE_PERCENT,
                alpha = 1f,
            )
    }
}

data class TrendArrowRenderSpec(
    val fillColor: Int,
    val outlineColor: Int,
    val outlineThicknessDp: Float,
    val scale: Float,
)

/** Sparse component/instance override; null always means inherit from the parent scope. */
data class TrendArrowStyleOverride(
    val fillColor: Int? = null,
    val outlineEnabled: Boolean? = null,
    val outlineColor: Int? = null,
    val outlineThicknessDp: Float? = null,
    val sizePercent: Int? = null,
    val alpha: Float? = null,
) {
    fun resolve(parent: TrendArrowStyle): TrendArrowStyle =
        parent
            .copy(
                fillColor = fillColor ?: parent.fillColor,
                outlineEnabled = outlineEnabled ?: parent.outlineEnabled,
                outlineColor = outlineColor ?: parent.outlineColor,
                outlineThicknessDp = outlineThicknessDp ?: parent.outlineThicknessDp,
                sizePercent = sizePercent ?: parent.sizePercent,
                alpha = alpha ?: parent.alpha,
            ).normalized()

    val isEmpty: Boolean get() =
        fillColor == null &&
            outlineEnabled == null &&
            outlineColor == null &&
            outlineThicknessDp == null &&
            sizePercent == null &&
            alpha == null
}

/** Platform-independent ARGB conversion used by every color editor. */
object ArgbColor {
    fun format(argb: Int): String = "#%08X".format(argb.toLong() and 0xFFFFFFFFL)

    fun parse(value: String?): Int? {
        val raw = value.orEmpty().trim().removePrefix("#")
        val parsed = raw.toLongOrNull(16) ?: return null
        return when (raw.length) {
            6 -> (0xFF000000L or parsed).toInt()
            8 -> parsed.toInt()
            else -> null
        }
    }

    fun withAlpha(
        argb: Int,
        alpha: Float,
    ): Int = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (argb and 0x00FFFFFF)
}

object AppearanceSchema {
    /** Version of the shared definition catalog; persistence versions advance only with migrations. */
    const val VERSION = 1
    val trendArrow =
        listOf(
            AppearanceSettingDefinition("trend.fill", AppearanceValueType.COLOR_ARGB, "#FFFFFFFF"),
            AppearanceSettingDefinition("trend.outline.enabled", AppearanceValueType.BOOLEAN, "false"),
            AppearanceSettingDefinition("trend.outline.color", AppearanceValueType.COLOR_ARGB, "#AE000000"),
            AppearanceSettingDefinition(
                "trend.outline.thicknessDp",
                AppearanceValueType.FLOAT,
                "0.65",
                TrendArrowStyle.MIN_OUTLINE_DP,
                TrendArrowStyle.MAX_OUTLINE_DP,
            ),
            AppearanceSettingDefinition(
                "trend.sizePercent",
                AppearanceValueType.INTEGER,
                "100",
                GlucoseTrendSizing.MIN_SCALE_PERCENT.toFloat(),
                GlucoseTrendSizing.MAX_SCALE_PERCENT.toFloat(),
            ),
            AppearanceSettingDefinition("trend.alpha", AppearanceValueType.FLOAT, "1.0", 0f, 1f),
        )
}
