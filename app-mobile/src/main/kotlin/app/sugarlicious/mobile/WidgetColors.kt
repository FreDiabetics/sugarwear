package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.AppearanceTerminology

internal enum class WidgetColorRole(
    val preferenceKey: String,
    val label: String,
    val sourceRole: SugarliciousColorRole? = null,
) {
    HIGH("high", AppearanceTerminology.GLUCOSE_HIGH),
    IN_RANGE("in_range", AppearanceTerminology.GLUCOSE_IN_RANGE),
    LOW("low", AppearanceTerminology.GLUCOSE_LOW),
    URGENT_LOW("urgent_low", AppearanceTerminology.GLUCOSE_VERY_LOW),
    VERY_HIGH("very_high", AppearanceTerminology.GLUCOSE_VERY_HIGH),
    BACKGROUND("background", "Hintergrund"),
    GRAPH_BACKGROUND("graph_background", AppearanceTerminology.GRAPH_BACKGROUND),
    GRAPH_OUTLINE("graph_outline", "Graphkontur"),
    IOB_PROGRESS("iob_progress", "IOB · Progressbar", SugarliciousColorRole.THERAPY_IOB_PROGRESS),
    IOB_TRACK("iob_track", "IOB · Ringhintergrund", SugarliciousColorRole.THERAPY_IOB_PROGRESS),
    IOB_ICON("iob_icon", "IOB · Icon", SugarliciousColorRole.THERAPY_IOB_PROGRESS),
    IOB_VALUE("iob_value", "IOB · Wert", SugarliciousColorRole.TEXT_PRIMARY),
    IOB_SECONDARY("iob_secondary", "IOB · Zusatzzeile", SugarliciousColorRole.TEXT_SECONDARY),
    COB_PROGRESS("cob_progress", "COB · Progressbar", SugarliciousColorRole.THERAPY_COB_PROGRESS),
    COB_TRACK("cob_track", "COB · Ringhintergrund", SugarliciousColorRole.THERAPY_COB_PROGRESS),
    COB_ICON("cob_icon", "COB · Icon", SugarliciousColorRole.THERAPY_COB_PROGRESS),
    COB_VALUE("cob_value", "COB · Wert", SugarliciousColorRole.TEXT_PRIMARY),
    COB_SECONDARY("cob_secondary", "COB · Zusatzzeile", SugarliciousColorRole.TEXT_SECONDARY),
    BASAL_PROGRESS("basal_progress", "Basal · Progressbar", SugarliciousColorRole.THERAPY_BASAL_PROGRESS),
    BASAL_TRACK("basal_track", "Basal · Ringhintergrund", SugarliciousColorRole.THERAPY_BASAL_PROGRESS),
    BASAL_ICON("basal_icon", "Basal · Icon", SugarliciousColorRole.THERAPY_BASAL_PROGRESS),
    BASAL_VALUE("basal_value", "Basal · Wert", SugarliciousColorRole.TEXT_PRIMARY),
    BASAL_SECONDARY("basal_secondary", "Basal · Zusatzzeile", SugarliciousColorRole.TEXT_SECONDARY),
    RANGE_HIGH("range_high", AppearanceTerminology.GRAPH_HIGH_AREA),
    RANGE_IN_RANGE("range_in_range", AppearanceTerminology.GRAPH_TARGET_AREA),
    RANGE_LOW("range_low", AppearanceTerminology.GRAPH_LOW_AREA),
    HIGH_LINE("high_line", AppearanceTerminology.GRAPH_HIGH_LINE),
    LOW_LINE("low_line", AppearanceTerminology.GRAPH_LOW_LINE),
    DOT_OUTLINE("dot_outline", app.sugarlicious.model.AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE),
    CURRENT_DOT_OUTLINE("current_dot_outline", app.sugarlicious.model.AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE),
    DOT_HIGH("dot_high", AppearanceTerminology.GRAPH_DOT_HIGH),
    DOT_IN_RANGE("dot_in_range", AppearanceTerminology.GRAPH_DOT_IN_RANGE),
    DOT_LOW("dot_low", AppearanceTerminology.GRAPH_DOT_LOW),
    DIVIDER("divider", AppearanceTerminology.GRAPH_NOW_LINE),
    AXIS("axis", AppearanceTerminology.GRAPH_AXIS_TEXT),
    HIGH_REGION_LABEL("high_region_label", "Graphtext im Hochbereich"),
    AXIS_TICK("axis_tick", AppearanceTerminology.GRAPH_AXIS_TICK),
    TEXT("text", "Text"),
    DELTA_UNIT("delta_unit", AppearanceTerminology.DELTA_UNIT),
    DELTA("widget_delta", "Delta"),
    AGE("widget_age", "Alter"),
    TIR("widget_tir", "Zeit im Ziel"),
    DELTA_ICON("widget_delta_icon", "Delta-Icon Hintergrund"),
    AGE_ICON("widget_age_icon", "Alter-Icon Hintergrund"),
    TIR_ICON("widget_tir_icon", "Zeit-im-Ziel-Icon Hintergrund"),
    DELTA_SYMBOL("widget_delta_symbol", "Delta-Symbol"),
    AGE_SYMBOL("widget_age_symbol", "Alter-Symbol"),
    TIR_SYMBOL("widget_tir_symbol", "Zeit-im-Ziel-Symbol"),
    UNIT("widget_unit", "Maßeinheit"),
    TREND("trend", AppearanceTerminology.TREND_ARROW),
    TREND_HIGH("trend_high", "Trend Hoch"),
    TREND_IN_RANGE("trend_in_range", "Trend im Ziel"),
    TREND_LOW("trend_low", "Trend Tief"),
}

internal data class WidgetPalette(
    private val values: Map<WidgetColorRole, Int>,
) {
    fun argb(role: WidgetColorRole): Int = values.getValue(role)

    fun with(
        role: WidgetColorRole,
        argb: Int,
    ): WidgetPalette = WidgetPalette(values + (role to argb))

    fun with(overrides: Map<WidgetColorRole, Int>): WidgetPalette = WidgetPalette(values + overrides)
}

internal object WidgetColorStore {
    private const val PREFERENCES = "dashboard_ui"
    private const val PREFIX = "widget.color.override."
    private const val VERY_HIGH_TEXT_COLOR_MIGRATION = "widget.color.very_high_text.v2"

    fun load(context: Context): WidgetPalette =
        load(
            context,
            SugarliciousColorStore.activeMode(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)),
        )

    fun load(
        context: Context,
        mode: AppearanceMode,
    ): WidgetPalette {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        migrateLegacy(preferences)
        migrateLegacyVeryHighTextColor(preferences)
        val mobile = SugarliciousColorStore.load(preferences, mode)
        return WidgetPalette(
            WidgetColorRole.entries.associateWith { role ->
                preferences.getInt(key(mode, role), mobileDefault(role, mobile::argb))
            },
        )
    }

    fun save(
        context: Context,
        role: WidgetColorRole,
        argb: Int,
    ) = save(
        context,
        SugarliciousColorStore.activeMode(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)),
        role,
        argb,
    )

    fun save(
        context: Context,
        mode: AppearanceMode,
        role: WidgetColorRole,
        argb: Int,
    ) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        migrateLegacy(preferences)
        preferences.edit { putInt(key(mode, role), argb) }
    }

    fun reset(
        context: Context,
        role: WidgetColorRole,
    ) = reset(
        context,
        SugarliciousColorStore.activeMode(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)),
        role,
    )

    fun reset(
        context: Context,
        mode: AppearanceMode,
        role: WidgetColorRole,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { remove(key(mode, role)) }
    }

    fun resetAll(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                WidgetColorRole.entries.forEach { remove(key(it)) }
                AppearanceMode.entries.forEach { mode -> WidgetColorRole.entries.forEach { remove(key(mode, it)) } }
            }
    }

    /** Takes a point-in-time copy. Later Mobile graph changes no longer mutate widget colors. */
    fun copyFromMobileGraph(context: Context) =
        copyFromMobileGraph(
            context,
            SugarliciousColorStore.activeMode(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)),
        )

    fun copyFromMobileGraph(
        context: Context,
        mode: AppearanceMode,
    ) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        migrateLegacy(preferences)
        val mobile = SugarliciousColorStore.load(preferences, mode)
        preferences.edit {
            WidgetColorRole.entries.forEach { role ->
                val sourceRole =
                    when (role) {
                        WidgetColorRole.DELTA_SYMBOL, WidgetColorRole.AGE_SYMBOL, WidgetColorRole.TIR_SYMBOL -> SugarliciousColorRole.BACKGROUND
                        WidgetColorRole.HIGH -> SugarliciousColorRole.CGM_DOT_HIGH
                        WidgetColorRole.IN_RANGE -> SugarliciousColorRole.CGM_DOT_IN_RANGE
                        WidgetColorRole.LOW -> SugarliciousColorRole.CGM_DOT_LOW
                        WidgetColorRole.URGENT_LOW -> SugarliciousColorRole.RANGE_LOW
                        WidgetColorRole.VERY_HIGH -> SugarliciousColorRole.GLUCOSE_VERY_HIGH
                        WidgetColorRole.BACKGROUND -> SugarliciousColorRole.GRAPH_BACKGROUND
                        WidgetColorRole.GRAPH_BACKGROUND -> SugarliciousColorRole.GRAPH_BACKGROUND
                        WidgetColorRole.RANGE_HIGH -> SugarliciousColorRole.RANGE_HIGH
                        WidgetColorRole.RANGE_IN_RANGE -> SugarliciousColorRole.RANGE_IN_RANGE
                        WidgetColorRole.RANGE_LOW -> SugarliciousColorRole.RANGE_LOW
                        WidgetColorRole.HIGH_LINE -> SugarliciousColorRole.GRAPH_HIGH_LINE
                        WidgetColorRole.LOW_LINE -> SugarliciousColorRole.GRAPH_LOW_LINE
                        WidgetColorRole.DOT_OUTLINE, WidgetColorRole.GRAPH_OUTLINE -> SugarliciousColorRole.GRAPH_CURRENT_OUTLINE
                        WidgetColorRole.CURRENT_DOT_OUTLINE -> SugarliciousColorRole.GRAPH_CURRENT_OUTLINE
                        WidgetColorRole.DOT_HIGH -> SugarliciousColorRole.CGM_DOT_HIGH
                        WidgetColorRole.DOT_IN_RANGE -> SugarliciousColorRole.CGM_DOT_IN_RANGE
                        WidgetColorRole.DOT_LOW -> SugarliciousColorRole.CGM_DOT_LOW
                        WidgetColorRole.DIVIDER -> SugarliciousColorRole.GRAPH_NOW_LINE
                        WidgetColorRole.AXIS -> SugarliciousColorRole.GRAPH_LABEL
                        WidgetColorRole.HIGH_REGION_LABEL -> SugarliciousColorRole.GRAPH_LABEL
                        WidgetColorRole.AXIS_TICK -> SugarliciousColorRole.GRAPH_AXIS_TICK
                        WidgetColorRole.TEXT -> SugarliciousColorRole.GRAPH_LABEL
                        WidgetColorRole.DELTA_UNIT -> SugarliciousColorRole.DELTA_UNIT
                        WidgetColorRole.DELTA, WidgetColorRole.AGE, WidgetColorRole.UNIT, WidgetColorRole.TIR, WidgetColorRole.DELTA_ICON, WidgetColorRole.AGE_ICON, WidgetColorRole.TIR_ICON -> SugarliciousColorRole.DELTA_UNIT
                        WidgetColorRole.TREND -> SugarliciousColorRole.TEXT_PRIMARY
                        WidgetColorRole.TREND_HIGH -> SugarliciousColorRole.GLUCOSE_HIGH
                        WidgetColorRole.TREND_IN_RANGE -> SugarliciousColorRole.GLUCOSE_IN_RANGE
                        WidgetColorRole.TREND_LOW -> SugarliciousColorRole.GLUCOSE_LOW
                        else -> requireNotNull(role.sourceRole)
                    }
                val source = mobile.argb(sourceRole)
                putInt(key(mode, role), if (role.name.endsWith("_TRACK")) (source and 0x00FFFFFF) or (((android.graphics.Color.alpha(source) * .30f).toInt()) shl 24) else source)
            }
        }
    }

    fun hasOverride(
        context: Context,
        role: WidgetColorRole,
    ): Boolean =
        hasOverride(
            context,
            SugarliciousColorStore.activeMode(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)),
            role,
        )

    fun hasOverride(
        context: Context,
        mode: AppearanceMode,
        role: WidgetColorRole,
    ): Boolean = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).contains(key(mode, role))

    internal fun key(role: WidgetColorRole): String = PREFIX + role.preferenceKey

    internal fun key(
        mode: AppearanceMode,
        role: WidgetColorRole,
    ): String = "$PREFIX${mode.storageKey}.${role.preferenceKey}"

    private fun migrateLegacy(preferences: android.content.SharedPreferences) {
        if (preferences.getBoolean("widget.appearance.profiles.v1", false)) return
        if (WidgetColorRole.entries.none { preferences.contains(key(it)) }) return
        preferences.edit {
            WidgetColorRole.entries.forEach { role ->
                if (preferences.contains(key(role))) {
                    val value = preferences.getInt(key(role), 0)
                    AppearanceMode.entries.forEach { mode ->
                        if (!preferences.contains(key(mode, role))) putInt(key(mode, role), value)
                    }
                }
            }
            putBoolean("widget.appearance.profiles.v1", true)
        }
    }

    private fun migrateLegacyVeryHighTextColor(preferences: android.content.SharedPreferences) {
        if (preferences.getBoolean(VERY_HIGH_TEXT_COLOR_MIGRATION, false)) return
        val mobileByMode = AppearanceMode.entries.associateWith { SugarliciousColorStore.load(preferences, it) }
        val legacyRangeFills = mobileByMode.values.map { it.argb(SugarliciousColorRole.RANGE_HIGH) }.toSet()
        preferences.edit {
            AppearanceMode.entries.forEach { mode ->
                val veryHighKey = key(mode, WidgetColorRole.VERY_HIGH)
                if (preferences.contains(veryHighKey)) {
                    val stored = preferences.getInt(veryHighKey, 0)
                    if ((stored ushr 24) != 0xFF && stored in legacyRangeFills) {
                        putInt(veryHighKey, mobileByMode.getValue(mode).argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH))
                    }
                }
            }
            val legacyKey = key(WidgetColorRole.VERY_HIGH)
            if (preferences.contains(legacyKey)) {
                val stored = preferences.getInt(legacyKey, 0)
                if ((stored ushr 24) != 0xFF && stored in legacyRangeFills) {
                    putInt(
                        legacyKey,
                        mobileByMode.getValue(AppearanceMode.DARK).argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
                    )
                }
            }
            putBoolean(VERY_HIGH_TEXT_COLOR_MIGRATION, true)
        }
    }

    private fun mobileDefault(
        role: WidgetColorRole,
        color: (SugarliciousColorRole) -> Int,
    ): Int {
        role.sourceRole?.let { source ->
            val value = color(source)
            return if (role.name.endsWith("_TRACK")) (value and 0x00FFFFFF) or (((android.graphics.Color.alpha(value) * .30f).toInt()) shl 24) else value
        }
        if (role in listOf(WidgetColorRole.TREND, WidgetColorRole.TREND_HIGH, WidgetColorRole.TREND_IN_RANGE, WidgetColorRole.TREND_LOW)) return color(SugarliciousColorRole.TEXT_PRIMARY)
        if (role in listOf(WidgetColorRole.DELTA_SYMBOL, WidgetColorRole.AGE_SYMBOL, WidgetColorRole.TIR_SYMBOL)) return color(SugarliciousColorRole.BACKGROUND)
        if (role == WidgetColorRole.DELTA_ICON) return color(SugarliciousColorRole.DELTA_UNIT)
        if (role == WidgetColorRole.AGE_ICON) return 0xFF59FFFF.toInt()
        if (role == WidgetColorRole.TIR_ICON) return 0xFF59FF59.toInt()
        if (role == WidgetColorRole.HIGH_REGION_LABEL) return 0xFF151515.toInt()
        return color(
            when (role) {
                WidgetColorRole.DELTA_SYMBOL, WidgetColorRole.AGE_SYMBOL, WidgetColorRole.TIR_SYMBOL -> SugarliciousColorRole.BACKGROUND
                WidgetColorRole.HIGH -> SugarliciousColorRole.GLUCOSE_HIGH
                WidgetColorRole.IN_RANGE -> SugarliciousColorRole.GLUCOSE_IN_RANGE
                WidgetColorRole.LOW -> SugarliciousColorRole.GLUCOSE_LOW
                WidgetColorRole.URGENT_LOW -> SugarliciousColorRole.RED
                WidgetColorRole.VERY_HIGH -> SugarliciousColorRole.GLUCOSE_VERY_HIGH
                WidgetColorRole.BACKGROUND -> SugarliciousColorRole.SURFACE
                WidgetColorRole.GRAPH_BACKGROUND -> SugarliciousColorRole.GRAPH_BACKGROUND
                WidgetColorRole.RANGE_HIGH -> SugarliciousColorRole.RANGE_HIGH
                WidgetColorRole.RANGE_IN_RANGE -> SugarliciousColorRole.RANGE_IN_RANGE
                WidgetColorRole.RANGE_LOW -> SugarliciousColorRole.RANGE_LOW
                WidgetColorRole.HIGH_LINE -> SugarliciousColorRole.GRAPH_HIGH_LINE
                WidgetColorRole.LOW_LINE -> SugarliciousColorRole.GRAPH_LOW_LINE
                WidgetColorRole.DOT_OUTLINE, WidgetColorRole.GRAPH_OUTLINE -> SugarliciousColorRole.GRAPH_CURRENT_OUTLINE
                WidgetColorRole.CURRENT_DOT_OUTLINE -> SugarliciousColorRole.GRAPH_CURRENT_OUTLINE
                WidgetColorRole.DOT_HIGH -> SugarliciousColorRole.CGM_DOT_HIGH
                WidgetColorRole.DOT_IN_RANGE -> SugarliciousColorRole.CGM_DOT_IN_RANGE
                WidgetColorRole.DOT_LOW -> SugarliciousColorRole.CGM_DOT_LOW
                WidgetColorRole.DIVIDER -> SugarliciousColorRole.GRAPH_NOW_LINE
                WidgetColorRole.AXIS -> SugarliciousColorRole.GRAPH_LABEL
                WidgetColorRole.HIGH_REGION_LABEL -> SugarliciousColorRole.GRAPH_LABEL
                WidgetColorRole.AXIS_TICK -> SugarliciousColorRole.GRAPH_AXIS_TICK
                WidgetColorRole.TEXT -> SugarliciousColorRole.TEXT_PRIMARY
                WidgetColorRole.DELTA_UNIT -> SugarliciousColorRole.DELTA_UNIT
                WidgetColorRole.DELTA, WidgetColorRole.AGE, WidgetColorRole.UNIT, WidgetColorRole.TIR, WidgetColorRole.DELTA_ICON, WidgetColorRole.AGE_ICON, WidgetColorRole.TIR_ICON -> SugarliciousColorRole.DELTA_UNIT
                WidgetColorRole.TREND -> SugarliciousColorRole.PRIMARY
                WidgetColorRole.TREND_HIGH -> SugarliciousColorRole.GLUCOSE_HIGH
                WidgetColorRole.TREND_IN_RANGE -> SugarliciousColorRole.GLUCOSE_IN_RANGE
                WidgetColorRole.TREND_LOW -> SugarliciousColorRole.GLUCOSE_LOW
                else -> requireNotNull(role.sourceRole)
            },
        )
    }
}

internal fun widgetGlucoseColorRole(
    valueMgDl: Double,
    lowMgDl: Double,
    highMgDl: Double,
): WidgetColorRole =
    widgetGlucoseColorRole(
        valueMgDl,
        app.sugarlicious.model.CgmThresholds(
            veryHighMgDl = maxOf(app.sugarlicious.model.CgmThresholds.DEFAULT_VERY_HIGH_MG_DL, highMgDl + 1.0),
            highMgDl = highMgDl,
            lowMgDl = lowMgDl,
            veryLowMgDl = minOf(app.sugarlicious.model.CgmThresholds.DEFAULT_VERY_LOW_MG_DL, lowMgDl - 1.0),
        ),
    )

internal fun widgetGlucoseColorRole(
    valueMgDl: Double,
    thresholds: app.sugarlicious.model.CgmThresholds,
): WidgetColorRole =
    when (thresholds.classify(valueMgDl)) {
        app.sugarlicious.model.CgmRangeClass.VERY_LOW -> WidgetColorRole.URGENT_LOW
        app.sugarlicious.model.CgmRangeClass.LOW -> WidgetColorRole.LOW
        app.sugarlicious.model.CgmRangeClass.IN_RANGE -> WidgetColorRole.IN_RANGE
        app.sugarlicious.model.CgmRangeClass.HIGH -> WidgetColorRole.HIGH
        app.sugarlicious.model.CgmRangeClass.VERY_HIGH -> WidgetColorRole.VERY_HIGH
        null -> WidgetColorRole.IN_RANGE
    }
