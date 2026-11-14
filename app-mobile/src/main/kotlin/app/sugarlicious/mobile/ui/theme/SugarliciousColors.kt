package app.sugarlicious.mobile.ui.theme

import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.AppearanceTerminology
import android.graphics.Color as AndroidColor

enum class SugarliciousColorGroup(
    val label: String,
) {
    APP("App-Oberfläche"),
    SETTINGS_ICONS("Einstellungen · Icon-Hintergründe"),
    GLUCOSE("Glukose & Zielbereich"),
    THERAPY("Therapie-Akzente"),
    GRAPH("Graphen & Prognosen"),
}

enum class SugarliciousColorRole(
    val preferenceKey: String,
    val label: String,
    val group: SugarliciousColorGroup,
    val defaultArgb: Int,
    val lightArgb: Int = defaultArgb,
    val configurable: Boolean = false,
) {
    BRAND_GREEN("brand_green", "Marken-Grün", SugarliciousColorGroup.APP, 0xFF22EB60.toInt()),
    SETTINGS_GENERAL("settings_general", "Allgemein · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFF86C852.toInt(), configurable = true),
    SETTINGS_GLUCOSE("settings_glucose", "Glukose und Zielbereiche · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFFFF7994.toInt(), configurable = true),
    SETTINGS_GRAPH("settings_graph", "Übersicht und Graphen · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFF5B94FF.toInt(), configurable = true),
    SETTINGS_APPEARANCE("settings_appearance", "Farben & Darstellung · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFF8477FF.toInt(), configurable = true),
    SETTINGS_NOTIFICATION("settings_notification", "Benachrichtigungen und Alarme · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFFAD73FF.toInt(), configurable = true),
    SETTINGS_DATA("settings_data", "Datenverwaltung · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFF43C9C8.toInt(), configurable = true),
    SETTINGS_DIAGNOSIS("settings_diagnosis", "Diagnose · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFFFFAD55.toInt(), configurable = true),
    SETTINGS_ABOUT("settings_about", "Über · Icon-Hintergrund", SugarliciousColorGroup.SETTINGS_ICONS, 0xFF8477FF.toInt(), configurable = true),
    PRIMARY("primary", "Primär / Hauptakzent", SugarliciousColorGroup.APP, 0xFF6DE892.toInt()),
    ON_PRIMARY("on_primary", "Text auf Primärfarbe", SugarliciousColorGroup.APP, 0xFF181818.toInt(), 0xFF102114.toInt()),
    SECONDARY("secondary", "Sekundär / Cyan", SugarliciousColorGroup.APP, 0xFF19D7E8.toInt()),
    ON_SECONDARY("on_secondary", "Text auf Sekundärfarbe", SugarliciousColorGroup.APP, 0xFF181818.toInt()),
    BACKGROUND(
        "background",
        AppearanceTerminology.APP_BACKGROUND,
        SugarliciousColorGroup.APP,
        0xFF191919.toInt(),
        0xFFF2F2F2.toInt(),
        true,
    ),
    SURFACE("surface", AppearanceTerminology.SURFACE_BACKGROUND, SugarliciousColorGroup.APP, 0xFF191919.toInt(), 0xFFFDFDFD.toInt(), true),
    OVERVIEW_GLUCOSE_PILL("overview_glucose_pill", "Glukosepille · Hintergrund", SugarliciousColorGroup.APP, 0xFF363636.toInt(), 0xFFDDDDDD.toInt(), true),
    OVERVIEW_TIR_PILL("overview_tir_pill", "TIR-Auswahlpille · Hintergrund", SugarliciousColorGroup.APP, 0xFF363636.toInt(), 0xFFDDDDDD.toInt(), true),
    SURFACE_HIGH("surface_high", "Erhöhte Fläche", SugarliciousColorGroup.APP, 0xFF303030.toInt(), 0xFFE7E7E7.toInt()),
    SURFACE_RAISED("surface_raised", "Progress-/Raised-Fläche", SugarliciousColorGroup.APP, 0xFF363636.toInt(), 0xFFDDDDDD.toInt()),
    SURFACE_SELECTED("surface_selected", "Ausgewählte Fläche", SugarliciousColorGroup.APP, 0xFF3A3A3A.toInt(), 0xFFDCE8DF.toInt()),
    BORDER(
        "border",
        AppearanceTerminology.SURFACE_BORDER,
        SugarliciousColorGroup.APP,
        0xFF404040.toInt(),
        0xFFD0D0D0.toInt(),
        configurable = true,
    ),
    TEXT_PRIMARY("text_primary", AppearanceTerminology.PRIMARY_TEXT, SugarliciousColorGroup.APP, 0xFFF5F5F5.toInt(), 0xFF252525.toInt()),
    TEXT_SECONDARY(
        "text_secondary",
        AppearanceTerminology.SECONDARY_TEXT,
        SugarliciousColorGroup.APP,
        0xFFB5B5B5.toInt(),
        0xFF666666.toInt(),
    ),
    DELTA_UNIT(
        "delta_unit",
        AppearanceTerminology.DELTA_UNIT,
        SugarliciousColorGroup.GLUCOSE,
        0xFFB5B5B5.toInt(),
        0xFF666666.toInt(),
        true,
    ),

    GLUCOSE_LOW("glucose_low", AppearanceTerminology.GLUCOSE_LOW, SugarliciousColorGroup.GLUCOSE, 0xFFF73D45.toInt(), 0xFFF73D45.toInt(), true),
    GLUCOSE_IN_RANGE(
        "glucose_in_range",
        AppearanceTerminology.GLUCOSE_IN_RANGE,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        true,
    ),
    GLUCOSE_HIGH(
        "glucose_high",
        AppearanceTerminology.GLUCOSE_HIGH,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    GLUCOSE_VERY_LOW(
        "glucose_very_low",
        AppearanceTerminology.GLUCOSE_VERY_LOW,
        SugarliciousColorGroup.GLUCOSE,
        0xFFF73D45.toInt(),
        0xFFF73D45.toInt(),
        configurable = true,
    ),
    GLUCOSE_VERY_HIGH(
        "glucose_very_high",
        AppearanceTerminology.GLUCOSE_VERY_HIGH,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    RANGE_LOW("range_low", AppearanceTerminology.GRAPH_LOW_AREA, SugarliciousColorGroup.GLUCOSE, 0xFFF73D45.toInt(), 0xFFF73D45.toInt(), true),
    RANGE_VERY_LOW(
        "range_very_low",
        AppearanceTerminology.GRAPH_VERY_LOW_AREA,
        SugarliciousColorGroup.GLUCOSE,
        0xFFF73D45.toInt(),
        0xFFF73D45.toInt(),
        configurable = true,
    ),
    RANGE_IN_RANGE(
        "range_in_range",
        AppearanceTerminology.GRAPH_TARGET_AREA,
        SugarliciousColorGroup.GLUCOSE,
        0xFF202020.toInt(),
        0xFFEDEDED.toInt(),
        true,
    ),
    RANGE_HIGH(
        "range_high",
        AppearanceTerminology.GRAPH_HIGH_AREA,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    RANGE_VERY_HIGH(
        "range_very_high",
        AppearanceTerminology.GRAPH_VERY_HIGH_AREA,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    PROGRESS_BELOW("progress_below", "Progressbar · unter Ziel", SugarliciousColorGroup.GLUCOSE, 0xFFF73D45.toInt(), 0xFFF73D45.toInt(), true),
    PROGRESS_IN_RANGE(
        "progress_in_range",
        "Progressbar · im Ziel",
        SugarliciousColorGroup.GLUCOSE,
        0xFF4BFA7C.toInt(),
        0xFF4BFA7C.toInt(),
        true,
    ),
    PROGRESS_ABOVE(
        "progress_above",
        "Progressbar · über Ziel",
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    CGM_DOT_VERY_LOW(
        "cgm_dot_very_low",
        AppearanceTerminology.GRAPH_DOT_VERY_LOW,
        SugarliciousColorGroup.GLUCOSE,
        0xFFF73D45.toInt(),
        0xFFF73D45.toInt(),
        configurable = true,
    ),
    CGM_DOT_LOW(
        "cgm_dot_low",
        AppearanceTerminology.GRAPH_DOT_LOW,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        configurable = true,
    ),
    CGM_DOT_IN_RANGE(
        "cgm_dot_in_range",
        AppearanceTerminology.GRAPH_DOT_IN_RANGE,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        true,
    ),
    CGM_DOT_VERY_HIGH(
        "cgm_dot_very_high",
        AppearanceTerminology.GRAPH_DOT_VERY_HIGH,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        configurable = true,
    ),
    CGM_DOT_HIGH(
        "cgm_dot_high",
        AppearanceTerminology.GRAPH_DOT_HIGH,
        SugarliciousColorGroup.GLUCOSE,
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        true,
    ),

    /**
     * Compatibility enum slot: the target range itself is rendered via RANGE_IN_RANGE since #51.
     * This role now owns the independent target_value preference for effective target line/text.
     * Old target_band preferences stay untouched and are not reinterpreted.
     */
    TARGET_BAND(
        "target_value",
        AppearanceTerminology.GRAPH_TARGET_VALUE,
        SugarliciousColorGroup.GLUCOSE,
        0xFFDCDCDC.toInt(),
        0xFFF0F0F0.toInt(),
        true,
    ),

    GREEN("green", "Grün / Status", SugarliciousColorGroup.THERAPY, 0xFF54DF30.toInt()),
    BLUE("blue", "Blau / IOB", SugarliciousColorGroup.THERAPY, 0xFF64BFFF.toInt()),
    ORANGE("orange", "Orange / COB", SugarliciousColorGroup.THERAPY, 0xFFFF9D18.toInt()),
    YELLOW("yellow", "Gelb / Warnung", SugarliciousColorGroup.THERAPY, 0xFFF4DE00.toInt()),
    PURPLE("purple", "Violett", SugarliciousColorGroup.THERAPY, 0xFFD69AFF.toInt()),
    RED("red", "Rot / Fehler", SugarliciousColorGroup.THERAPY, 0xFFFF5C69.toInt()),
    THERAPY_IOB_PROGRESS("therapy_iob_progress", "IOB-Ring", SugarliciousColorGroup.THERAPY, 0xFF64BFFF.toInt(), 0xFF64BFFF.toInt(), true),
    THERAPY_COB_PROGRESS("therapy_cob_progress", "COB-Ring", SugarliciousColorGroup.THERAPY, 0xFFFF9D18.toInt(), 0xFFFF9D18.toInt(), true),
    THERAPY_BASAL_PROGRESS(
        "therapy_basal_progress",
        "Basal-Ring",
        SugarliciousColorGroup.THERAPY,
        0xFF19D7E8.toInt(),
        0xFF19D7E8.toInt(),
        true,
    ),

    PREDICTION_IOB("prediction_iob", AppearanceTerminology.PREDICTION_IOB, SugarliciousColorGroup.GRAPH, 0xFF52C1FF.toInt()),
    PREDICTION_COB("prediction_cob", AppearanceTerminology.PREDICTION_COB, SugarliciousColorGroup.GRAPH, 0xFFF4DE00.toInt()),
    PREDICTION_UAM("prediction_uam", AppearanceTerminology.PREDICTION_UAM, SugarliciousColorGroup.GRAPH, 0xFFFFAE1F.toInt()),
    PREDICTION_ZERO_TEMP(
        "prediction_zero_temp",
        AppearanceTerminology.PREDICTION_ZERO_TEMP,
        SugarliciousColorGroup.GRAPH,
        0xFF30DBDE.toInt(),
    ),
    GRAPH_BACKGROUND(
        "graph_background",
        AppearanceTerminology.GRAPH_BACKGROUND,
        SugarliciousColorGroup.GRAPH,
        0xFF191919.toInt(),
        0xFFFFFFFF.toInt(),
        true,
    ),
    GRAPH_IOB("graph_iob", "IOB", SugarliciousColorGroup.GRAPH, 0xFF64BFFF.toInt(), 0xFF64BFFF.toInt(), true),
    GRAPH_COB("graph_cob", "COB", SugarliciousColorGroup.GRAPH, 0xFFFF9D18.toInt(), 0xFFFF9D18.toInt(), true),
    GRAPH_INSULIN_ACTIVITY(
        "graph_insulin_activity",
        "Insulinaktivität",
        SugarliciousColorGroup.GRAPH,
        0xFF24A4B0.toInt(),
        0xFF24A4B0.toInt(),
        true,
    ),
    GRAPH_GRID("graph_grid", "Graph-Gitter", SugarliciousColorGroup.GRAPH, 0xFF464646.toInt(), 0xFFD5D5D5.toInt()),
    GRAPH_LABEL("graph_label", AppearanceTerminology.GRAPH_AXIS_TEXT, SugarliciousColorGroup.GRAPH, 0xFFD2D2D2.toInt(), 0xFF575757.toInt()),
    GRAPH_AXIS_TICK(
        "graph_axis_tick",
        AppearanceTerminology.GRAPH_AXIS_TICK,
        SugarliciousColorGroup.GRAPH,
        0xFF969696.toInt(),
        0xFF747474.toInt(),
        true,
    ),
    GRAPH_HIGH_LINE(
        "graph_high_line",
        AppearanceTerminology.GRAPH_HIGH_LINE,
        SugarliciousColorGroup.GRAPH,
        0xFFFCC93B.toInt(),
        0xFFFCC93B.toInt(),
        true,
    ),
    GRAPH_VERY_HIGH_LINE(
        "graph_very_high_line",
        AppearanceTerminology.GRAPH_VERY_HIGH_LINE,
        SugarliciousColorGroup.GRAPH,
        0x00FCC93B,
        0x00FCC93B,
        true,
    ),
    GRAPH_LOW_LINE(
        "graph_low_line",
        AppearanceTerminology.GRAPH_LOW_LINE,
        SugarliciousColorGroup.GRAPH,
        0xFFF73D45.toInt(),
        0xFFF73D45.toInt(),
        configurable = true,
    ),
    GRAPH_VERY_LOW_LINE(
        "graph_very_low_line",
        AppearanceTerminology.GRAPH_VERY_LOW_LINE,
        SugarliciousColorGroup.GRAPH,
        0x00F73D45,
        0x00F73D45,
        configurable = true,
    ),
    GRAPH_NOW_LINE(
        "graph_now_line",
        AppearanceTerminology.GRAPH_NOW_LINE,
        SugarliciousColorGroup.GRAPH,
        0xFF969696.toInt(),
        0xFF747474.toInt(),
        true,
    ),
    GRAPH_MUTED("graph_muted", "Graph-Hinweise / Trennlinie", SugarliciousColorGroup.GRAPH, 0xFF969696.toInt(), 0xFF777777.toInt()),
    GRAPH_DIVIDER(
        "graph_divider",
        AppearanceTerminology.GRAPH_DIVIDER,
        SugarliciousColorGroup.GRAPH,
        0xFF969696.toInt(),
        0xFF747474.toInt(),
        true,
    ),
    GRAPH_BORDER("graph_border", AppearanceTerminology.GRAPH_BORDER, SugarliciousColorGroup.GRAPH, 0xFF969696.toInt()),
    GRAPH_SIGNAL_LOSS("graph_signal_loss", "Signalverlust", SugarliciousColorGroup.GRAPH, 0xB26E6E6E.toInt(), 0x38808080, true),
    GRAPH_CURRENT_OUTLINE("graph_current_outline", "Aktueller Punkt · Kontur", SugarliciousColorGroup.GRAPH, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    OVERVIEW_WAVE_VERY_LOW("overview_wave_very_low", "Sehr tief", SugarliciousColorGroup.GLUCOSE, 0xFFF73D45.toInt(), configurable = false),
    OVERVIEW_WAVE_LOW("overview_wave_low", "Tief", SugarliciousColorGroup.GLUCOSE, 0xFFF73D45.toInt(), configurable = true),
    OVERVIEW_WAVE_IN_RANGE("overview_wave_in_range", "Im Ziel", SugarliciousColorGroup.GLUCOSE, 0xFF22EB60.toInt(), configurable = true),
    OVERVIEW_WAVE_HIGH("overview_wave_high", "Hoch", SugarliciousColorGroup.GLUCOSE, 0xFFFCC93B.toInt(), configurable = true),
    OVERVIEW_WAVE_UNDERLAY("overview_wave_underlay", "Unterlage", SugarliciousColorGroup.GLUCOSE, 0x4DB0B0B0, 0x4D808080, true),
    OVERVIEW_WAVE_VERY_HIGH("overview_wave_very_high", "Sehr hoch", SugarliciousColorGroup.GLUCOSE, 0xFFFCC93B.toInt(), configurable = false),
    ;

    companion object {
        /** Semantic alias used by graph/settings without adding another enum entry. */
        val TARGET_VALUE: SugarliciousColorRole
            get() = TARGET_BAND
    }
}

data class SugarliciousPalette(
    private val values: Map<SugarliciousColorRole, Int>,
    val isLight: Boolean = false,
) {
    fun argb(role: SugarliciousColorRole): Int = values[role] ?: if (isLight) role.lightArgb else role.defaultArgb

    fun compose(role: SugarliciousColorRole): Color = Color(argb(role))

    companion object {
        fun defaults(): SugarliciousPalette =
            SugarliciousPalette(
                values =
                    SugarliciousColorRole.entries.associateWith {
                        it.defaultArgb
                    },
                isLight = false,
            )
    }
}

/** Keeps the target line in the in-range hue while making it visibly brighter. */
fun derivedTargetValueArgb(inRangeArgb: Int): Int {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(inRangeArgb, hsv)
    hsv[2] = hsv[2].coerceAtLeast(0.94f)
    return AndroidColor.HSVToColor(255, hsv)
}

object SugarliciousColorStore {
    private const val LEGACY_PREFIX = "color."
    private const val DARK_PREFIX = "color.dark."
    private const val LIGHT_PREFIX = "color.light."
    private const val OVERRIDE_PREFIX = "color.override."

    private fun systemIsLight(): Boolean =
        (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES

    fun activeMode(preferences: SharedPreferences): AppearanceMode =
        when (preferences.getString("themeMode", "SYSTEM")) {
            "LIGHT" -> AppearanceMode.LIGHT
            "DARK" -> AppearanceMode.DARK
            else -> if (systemIsLight()) AppearanceMode.LIGHT else AppearanceMode.DARK
        }

    fun load(preferences: SharedPreferences): SugarliciousPalette = load(preferences, activeMode(preferences))

    fun load(
        preferences: SharedPreferences,
        mode: AppearanceMode,
    ): SugarliciousPalette {
        val light = mode == AppearanceMode.LIGHT
        val prefix = if (light) LIGHT_PREFIX else DARK_PREFIX

        val values =
            SugarliciousColorRole.entries
                .associateWith { role ->
                    val modeKey = prefix + role.preferenceKey

                    when {
                        preferences.contains(modeKey) ->
                            preferences.getInt(
                                modeKey,
                                if (light) {
                                    role.lightArgb
                                } else {
                                    role.defaultArgb
                                },
                            )

                        preferences.contains(OVERRIDE_PREFIX + role.preferenceKey) ->
                            preferences.getInt(OVERRIDE_PREFIX + role.preferenceKey, if (light) role.lightArgb else role.defaultArgb)

                        preferences.contains(LEGACY_PREFIX + role.preferenceKey) ->
                            preferences.getInt(LEGACY_PREFIX + role.preferenceKey, if (light) role.lightArgb else role.defaultArgb)

                        light ->
                            role.lightArgb

                        else ->
                            role.defaultArgb
                    }
                }.toMutableMap()

        fun hasExplicit(role: SugarliciousColorRole): Boolean =
            preferences.contains(prefix + role.preferenceKey) ||
                preferences.contains(OVERRIDE_PREFIX + role.preferenceKey) ||
                preferences.contains(LEGACY_PREFIX + role.preferenceKey)

        val targetRole = SugarliciousColorRole.TARGET_VALUE
        val targetHasExplicitColor =
            hasExplicit(targetRole)
        if (!targetHasExplicitColor && hasExplicit(SugarliciousColorRole.RANGE_IN_RANGE)) {
            values[targetRole] = derivedTargetValueArgb(values.getValue(SugarliciousColorRole.RANGE_IN_RANGE))
        }

        if (!hasExplicit(SugarliciousColorRole.GLUCOSE_VERY_LOW) && hasExplicit(SugarliciousColorRole.GLUCOSE_LOW)) {
            values[SugarliciousColorRole.GLUCOSE_VERY_LOW] = values.getValue(SugarliciousColorRole.GLUCOSE_LOW)
        }
        if (!hasExplicit(SugarliciousColorRole.GLUCOSE_VERY_HIGH) && hasExplicit(SugarliciousColorRole.GLUCOSE_HIGH)) {
            values[SugarliciousColorRole.GLUCOSE_VERY_HIGH] = values.getValue(SugarliciousColorRole.GLUCOSE_HIGH)
        }
        mapOf(
            SugarliciousColorRole.OVERVIEW_WAVE_VERY_LOW to SugarliciousColorRole.GLUCOSE_VERY_LOW,
            SugarliciousColorRole.OVERVIEW_WAVE_LOW to SugarliciousColorRole.GLUCOSE_LOW,
            SugarliciousColorRole.OVERVIEW_WAVE_HIGH to SugarliciousColorRole.GLUCOSE_HIGH,
            SugarliciousColorRole.OVERVIEW_WAVE_VERY_HIGH to SugarliciousColorRole.GLUCOSE_VERY_HIGH,
        ).forEach { (wave, glucose) ->
            if (!hasExplicit(wave)) values[wave] = values.getValue(glucose)
        }
        // Preserve existing graph colors until an independent dot color is chosen.
        if (!hasExplicit(SugarliciousColorRole.CGM_DOT_VERY_LOW)) {
            values[SugarliciousColorRole.CGM_DOT_VERY_LOW] = values.getValue(SugarliciousColorRole.GLUCOSE_VERY_LOW)
        }
        if (!hasExplicit(SugarliciousColorRole.CGM_DOT_VERY_HIGH)) {
            values[SugarliciousColorRole.CGM_DOT_VERY_HIGH] = values.getValue(SugarliciousColorRole.GLUCOSE_VERY_HIGH)
        }
        if (!hasExplicit(SugarliciousColorRole.RANGE_VERY_LOW) && hasExplicit(SugarliciousColorRole.GLUCOSE_VERY_LOW)) {
            values[SugarliciousColorRole.RANGE_VERY_LOW] = values.getValue(SugarliciousColorRole.GLUCOSE_VERY_LOW)
        }
        if (!hasExplicit(SugarliciousColorRole.RANGE_VERY_HIGH) && hasExplicit(SugarliciousColorRole.GLUCOSE_VERY_HIGH)) {
            values[SugarliciousColorRole.RANGE_VERY_HIGH] = values.getValue(SugarliciousColorRole.GLUCOSE_VERY_HIGH)
        }
        // Keep existing installs visually unchanged while giving every graph element its own
        // independently configurable semantic role from now on.
        if (!hasExplicit(SugarliciousColorRole.GRAPH_HIGH_LINE) && hasExplicit(SugarliciousColorRole.RANGE_HIGH)) {
            values[SugarliciousColorRole.GRAPH_HIGH_LINE] = values.getValue(SugarliciousColorRole.RANGE_HIGH)
        }
        if (!hasExplicit(SugarliciousColorRole.GRAPH_LOW_LINE) && hasExplicit(SugarliciousColorRole.RANGE_LOW)) {
            values[SugarliciousColorRole.GRAPH_LOW_LINE] = values.getValue(SugarliciousColorRole.RANGE_LOW)
        }
        if (!hasExplicit(SugarliciousColorRole.GRAPH_VERY_LOW_LINE) && hasExplicit(SugarliciousColorRole.RANGE_VERY_LOW)) {
            values[SugarliciousColorRole.GRAPH_VERY_LOW_LINE] = values.getValue(SugarliciousColorRole.RANGE_VERY_LOW)
        }
        if (!hasExplicit(SugarliciousColorRole.GRAPH_VERY_HIGH_LINE) && hasExplicit(SugarliciousColorRole.RANGE_VERY_HIGH)) {
            values[SugarliciousColorRole.GRAPH_VERY_HIGH_LINE] = values.getValue(SugarliciousColorRole.RANGE_VERY_HIGH)
        }
        if (!hasExplicit(SugarliciousColorRole.GRAPH_AXIS_TICK) && hasExplicit(SugarliciousColorRole.GRAPH_DIVIDER)) {
            values[SugarliciousColorRole.GRAPH_AXIS_TICK] = values.getValue(SugarliciousColorRole.GRAPH_DIVIDER)
        }
        if (!hasExplicit(SugarliciousColorRole.GRAPH_NOW_LINE) && hasExplicit(SugarliciousColorRole.GRAPH_DIVIDER)) {
            values[SugarliciousColorRole.GRAPH_NOW_LINE] = values.getValue(SugarliciousColorRole.GRAPH_DIVIDER)
        }

        return SugarliciousPalette(
            values = values,
            isLight = light,
        )
    }

    fun save(
        preferences: SharedPreferences,
        role: SugarliciousColorRole,
        argb: Int,
    ) = save(preferences, activeMode(preferences), role, argb)

    fun save(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        role: SugarliciousColorRole,
        argb: Int,
    ) {
        migrateLegacy(preferences)
        preferences.edit {
            putInt(
                (if (mode == AppearanceMode.LIGHT) LIGHT_PREFIX else DARK_PREFIX) +
                    role.preferenceKey,
                argb,
            )
        }
    }

    fun reset(
        preferences: SharedPreferences,
        role: SugarliciousColorRole,
    ) = reset(preferences, activeMode(preferences), role)

    fun reset(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        role: SugarliciousColorRole,
    ) {
        val light = mode == AppearanceMode.LIGHT
        preferences.edit {
            remove(
                (if (light) LIGHT_PREFIX else DARK_PREFIX) +
                    role.preferenceKey,
            )
        }
    }

    fun resetAll(preferences: SharedPreferences) {
        preferences.edit {
            SugarliciousColorRole.entries.forEach { role ->
                remove(
                    LEGACY_PREFIX +
                        role.preferenceKey,
                )
                remove(
                    DARK_PREFIX +
                        role.preferenceKey,
                )
                remove(
                    LIGHT_PREFIX +
                        role.preferenceKey,
                )
                remove(
                    OVERRIDE_PREFIX +
                        role.preferenceKey,
                )
            }
        }
    }

    fun hasUserOverride(
        preferences: SharedPreferences,
        role: SugarliciousColorRole,
    ): Boolean = hasUserOverride(preferences, activeMode(preferences), role)

    fun hasUserOverride(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        role: SugarliciousColorRole,
    ): Boolean =
        preferences.contains(
            (if (mode == AppearanceMode.LIGHT) LIGHT_PREFIX else DARK_PREFIX) + role.preferenceKey,
        )

    private fun migrateLegacy(preferences: SharedPreferences) {
        if (preferences.getBoolean("appearance.profiles.v1", false)) return
        val hasLegacy =
            SugarliciousColorRole.entries.any { role ->
                preferences.contains(OVERRIDE_PREFIX + role.preferenceKey) ||
                    preferences.contains(LEGACY_PREFIX + role.preferenceKey)
            }
        if (!hasLegacy) return
        preferences.edit {
            SugarliciousColorRole.entries.forEach { role ->
                val legacy =
                    when {
                        preferences.contains(OVERRIDE_PREFIX + role.preferenceKey) ->
                            preferences.getInt(OVERRIDE_PREFIX + role.preferenceKey, role.defaultArgb)
                        preferences.contains(LEGACY_PREFIX + role.preferenceKey) ->
                            preferences.getInt(LEGACY_PREFIX + role.preferenceKey, role.defaultArgb)
                        else -> null
                    }
                if (legacy != null) {
                    if (!preferences.contains(DARK_PREFIX + role.preferenceKey)) putInt(DARK_PREFIX + role.preferenceKey, legacy)
                    if (!preferences.contains(LIGHT_PREFIX + role.preferenceKey)) putInt(LIGHT_PREFIX + role.preferenceKey, legacy)
                }
            }
            putBoolean("appearance.profiles.v1", true)
        }
    }
}

/**
 * Runtime palette shared by classic Views, custom Canvas charts and Compose.
 *
 * The backing palette is Compose state so open Compose screens recompose
 * immediately when a color is saved in Settings.
 */
object SugarliciousColors {
    var palette by mutableStateOf(
        SugarliciousPalette.defaults(),
    )
        private set

    fun apply(palette: SugarliciousPalette) {
        this.palette = palette
    }

    fun argb(role: SugarliciousColorRole): Int = palette.argb(role)

    fun color(role: SugarliciousColorRole): Color = palette.compose(role)

    val BrandGreen get() = color(SugarliciousColorRole.BRAND_GREEN)
    val Primary get() = color(SugarliciousColorRole.PRIMARY)
    val OnPrimary get() = color(SugarliciousColorRole.ON_PRIMARY)
    val Secondary get() = color(SugarliciousColorRole.SECONDARY)
    val OnSecondary get() = color(SugarliciousColorRole.ON_SECONDARY)

    val Background get() = color(SugarliciousColorRole.BACKGROUND)
    val Surface get() = color(SugarliciousColorRole.SURFACE)
    val SurfaceHigh get() = color(SugarliciousColorRole.SURFACE_HIGH)
    val SurfaceRaised get() = color(SugarliciousColorRole.SURFACE_RAISED)
    val SurfaceSelected get() = color(SugarliciousColorRole.SURFACE_SELECTED)
    val Border get() = color(SugarliciousColorRole.BORDER)

    val TextPrimary get() = color(SugarliciousColorRole.TEXT_PRIMARY)
    val TextSecondary get() = color(SugarliciousColorRole.TEXT_SECONDARY)
    val DeltaUnit get() = color(SugarliciousColorRole.DELTA_UNIT)

    val GlucoseLow get() = color(SugarliciousColorRole.GLUCOSE_LOW)
    val GlucoseInRange get() = color(SugarliciousColorRole.GLUCOSE_IN_RANGE)
    val GlucoseHigh get() = color(SugarliciousColorRole.GLUCOSE_HIGH)
    val GlucoseVeryLow get() = color(SugarliciousColorRole.GLUCOSE_VERY_LOW)
    val GlucoseVeryHigh get() = color(SugarliciousColorRole.GLUCOSE_VERY_HIGH)
    val TargetBand get() = color(SugarliciousColorRole.RANGE_IN_RANGE)
    val TargetValue get() = color(SugarliciousColorRole.TARGET_VALUE)

    val Green get() = color(SugarliciousColorRole.GREEN)
    val Blue get() = color(SugarliciousColorRole.BLUE)
    val Orange get() = color(SugarliciousColorRole.ORANGE)
    val Yellow get() = color(SugarliciousColorRole.YELLOW)
    val Red get() = color(SugarliciousColorRole.RED)
}
