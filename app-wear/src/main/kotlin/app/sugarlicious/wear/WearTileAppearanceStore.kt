package app.sugarlicious.wear

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TherapyMetricAppearance
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.protocol.WatchUiColors

enum class WearTileKind(
    internal val preferenceName: String,
) {
    GLUCOSE("wear_tile_glucose_appearance"),
    THERAPY("wear_tile_therapy_appearance"),
}

enum class WearTileContent(
    val label: String,
) {
    GLUCOSE("Glukose"),
    GRAPH("Graph"),
}

enum class TherapyTileMetric(
    val label: String,
) {
    IOB("IOB"),
    COB("COB"),
    BASAL("Basal"),
}

data class TherapyTileSelection(
    val metrics: List<TherapyTileMetric>,
) {
    init {
        require(metrics.isNotEmpty())
        require(metrics.distinct().size == metrics.size)
    }

    val canonical: List<TherapyTileMetric>
        get() = TherapyTileMetric.entries.filter(metrics::contains)
}

data class TherapyTilePlacement(
    val metric: TherapyTileMetric,
    val row: Int,
    val column: Int,
)

internal fun therapyTilePlacements(selection: TherapyTileSelection): List<TherapyTilePlacement> {
    val metrics = selection.canonical
    return when (metrics.size) {
        1 -> listOf(TherapyTilePlacement(metrics.single(), 0, 0))
        2 -> metrics.mapIndexed { index, metric -> TherapyTilePlacement(metric, 0, index) }
        else ->
            listOf(
                TherapyTilePlacement(TherapyTileMetric.IOB, 0, 0),
                TherapyTilePlacement(TherapyTileMetric.COB, 0, 1),
                TherapyTilePlacement(TherapyTileMetric.BASAL, 1, 0),
            )
    }
}

internal object TherapyTileSelectionStore {
    private const val PREFERENCES = "wear_tile_content"
    private const val KEY = "therapy.metrics.v1"

    fun read(context: Context): TherapyTileSelection {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences.getStringSet(KEY, null)
        if (stored == null) {
            val legacy = preferences.getString(WearTileKind.THERAPY.name, null)
            val migrated = TherapyTileMetric.entries.firstOrNull { it.name == legacy }
            if (migrated != null) {
                write(context, setOf(migrated))
                return TherapyTileSelection(listOf(migrated))
            }
        }
        val metrics = stored?.mapNotNull { raw -> TherapyTileMetric.entries.firstOrNull { it.name == raw } }.orEmpty()
        return TherapyTileSelection(TherapyTileMetric.entries.filter(metrics::contains).ifEmpty { TherapyTileMetric.entries })
    }

    fun write(
        context: Context,
        metrics: Set<TherapyTileMetric>,
    ) {
        require(metrics.isNotEmpty())
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putStringSet(KEY, metrics.mapTo(linkedSetOf(), TherapyTileMetric::name))
            }
    }
}

internal object TherapyTileGeometryStore {
    private const val PREFERENCES = "wear_tile_therapy_geometry"

    fun read(context: Context): TherapyProgressAppearance {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains("enabled")) {
            val legacy = TherapyTileSelectionStore.read(context).canonical.mapTo(linkedSetOf()) { it.toProgressMetric() }
            write(context, TherapyProgressAppearance(enabledMetrics = legacy))
        }

        fun metric(prefix: String) =
            TherapyMetricAppearance(
                overallScalePercent = preferences.getInt(prefix + "overall", 100),
                ringThicknessPercent = preferences.getInt(prefix + "ring", 100),
                primaryTextPercent = preferences.getInt(prefix + "primary", 100),
                secondaryTextPercent = preferences.getInt(prefix + "secondary", 100),
                iconPercent = preferences.getInt(prefix + "icon", 100),
            ).validated()
        val enabled =
            preferences
                .getStringSet("enabled", null)
                ?.mapNotNull { raw -> runCatching { TherapyProgressMetric.valueOf(raw) }.getOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: TherapyProgressMetric.entries.toSet()
        return TherapyProgressAppearance(
            enabledMetrics = enabled,
            groupScalePercent = preferences.getInt("group_scale", 100),
            gapDp = preferences.getInt("gap_dp", 6),
            global = metric("global."),
            metricOverrides = TherapyProgressMetric.entries.associateWith { metric("${it.name.lowercase()}.") },
        ).validated()
    }

    fun write(
        context: Context,
        appearance: TherapyProgressAppearance,
    ) {
        val safe = appearance.validated()
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putStringSet("enabled", safe.enabledMetrics.mapTo(linkedSetOf()) { it.name })
                putInt("group_scale", safe.groupScalePercent)
                putInt("gap_dp", safe.gapDp)

                fun writeMetric(prefix: String, value: TherapyMetricAppearance) {
                    putInt(prefix + "overall", value.overallScalePercent)
                    putInt(prefix + "ring", value.ringThicknessPercent)
                    putInt(prefix + "primary", value.primaryTextPercent)
                    putInt(prefix + "secondary", value.secondaryTextPercent)
                    putInt(prefix + "icon", value.iconPercent)
                }
                writeMetric("global.", safe.global)
                TherapyProgressMetric.entries.forEach { metric ->
                    writeMetric("${metric.name.lowercase()}.", safe.metricOverrides[metric] ?: TherapyMetricAppearance())
                }
            }
        TherapyTileSelectionStore.write(context, safe.enabledMetrics.mapTo(linkedSetOf()) { it.toTileMetric() })
    }
}

internal fun TherapyTileMetric.toProgressMetric(): TherapyProgressMetric = TherapyProgressMetric.valueOf(name)

internal fun TherapyProgressMetric.toTileMetric(): TherapyTileMetric = TherapyTileMetric.valueOf(name)

/** The two system tile slots keep their own content choice; no global fake tile state. */
internal object WearTileContentStore {
    private const val PREFERENCES = "wear_tile_content"

    fun read(
        context: Context,
        kind: WearTileKind,
    ): WearTileContent {
        val fallback = WearTileContent.GLUCOSE
        val raw =
            context
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(kind.name, fallback.name)
        return WearTileContent.entries.firstOrNull { it.name == raw } ?: fallback
    }

    fun write(
        context: Context,
        kind: WearTileKind,
        content: WearTileContent,
    ) {
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putString(kind.name, content.name)
            }
    }
}

/** Independent appearance state for each Wear OS tile. */
internal object WearTileAppearanceStore {
    private const val PREFIX = "color."

    fun read(
        context: Context,
        kind: WearTileKind,
    ): WatchUiColors = read(context, kind, WearDisplayPreferences.activeAppearanceMode(context))

    fun read(
        context: Context,
        kind: WearTileKind,
        mode: AppearanceMode,
    ): WatchUiColors {
        val defaults = WatchUiColors()
        val preferences = context.getSharedPreferences(kind.preferenceName, Context.MODE_PRIVATE)
        migrateLegacy(preferences)
        val prefix = "$PREFIX${mode.storageKey}."
        return WatchUiColors(
            background = preferences.getInt(prefix + "background", defaults.background),
            tileBackground = preferences.getInt(prefix + "tile_background", defaults.tileBackground),
            tileBorder = preferences.getInt(prefix + "tile_border", defaults.tileBorder),
            textPrimary = preferences.getInt(prefix + "text_primary", defaults.textPrimary),
            textSecondary = preferences.getInt(prefix + "text_secondary", defaults.textSecondary),
            deltaUnit = preferences.getInt(prefix + "delta_unit", defaults.deltaUnit),
            accent = preferences.getInt(prefix + "accent", defaults.accent),
            glucoseLow = preferences.getInt(prefix + "glucose_low", defaults.glucoseLow),
            glucoseInRange = preferences.getInt(prefix + "glucose_in_range", defaults.glucoseInRange),
            glucoseHigh = preferences.getInt(prefix + "glucose_high", defaults.glucoseHigh),
            glucoseVeryLow = preferences.getInt(prefix + "glucose_very_low", defaults.glucoseVeryLow),
            glucoseVeryHigh = preferences.getInt(prefix + "glucose_very_high", defaults.glucoseVeryHigh),
            iob = preferences.getInt(prefix + "iob", defaults.iob),
            cob = preferences.getInt(prefix + "cob", defaults.cob),
            basal = preferences.getInt(prefix + "basal", defaults.basal),
        )
    }

    fun write(
        context: Context,
        kind: WearTileKind,
        colors: WatchUiColors,
    ) = write(context, kind, WearDisplayPreferences.activeAppearanceMode(context), colors)

    fun write(
        context: Context,
        kind: WearTileKind,
        mode: AppearanceMode,
        colors: WatchUiColors,
    ) {
        val preferences = context.getSharedPreferences(kind.preferenceName, Context.MODE_PRIVATE)
        migrateLegacy(preferences)
        val prefix = "$PREFIX${mode.storageKey}."
        preferences
            .edit {
                putInt(prefix + "background", colors.background)
                    .putInt(prefix + "tile_background", colors.tileBackground)
                    .putInt(prefix + "tile_border", colors.tileBorder)
                    .putInt(prefix + "text_primary", colors.textPrimary)
                    .putInt(prefix + "text_secondary", colors.textSecondary)
                    .putInt(prefix + "delta_unit", colors.deltaUnit)
                    .putInt(prefix + "accent", colors.accent)
                    .putInt(prefix + "glucose_low", colors.glucoseLow)
                    .putInt(prefix + "glucose_in_range", colors.glucoseInRange)
                    .putInt(prefix + "glucose_high", colors.glucoseHigh)
                    .putInt(prefix + "glucose_very_low", colors.glucoseVeryLow)
                    .putInt(prefix + "glucose_very_high", colors.glucoseVeryHigh)
                    .putInt(prefix + "iob", colors.iob)
                    .putInt(prefix + "cob", colors.cob)
                    .putInt(prefix + "basal", colors.basal)
            }
    }

    private fun migrateLegacy(preferences: android.content.SharedPreferences) {
        if (preferences.getBoolean("appearance_profiles_v1", false)) return
        preferences
            .edit {
                preferences.all.forEach { (key, raw) ->
                    if (!key.startsWith(PREFIX) ||
                        raw !is Int ||
                        key.startsWith("${PREFIX}light.") ||
                        key.startsWith("${PREFIX}dark.")
                    ) {
                        return@forEach
                    }
                    val suffix = key.removePrefix(PREFIX)
                    AppearanceMode.entries.forEach { mode ->
                        val target = "$PREFIX${mode.storageKey}.$suffix"
                        if (!preferences.contains(target)) putInt(target, raw)
                    }
                }
                putBoolean("appearance_profiles_v1", true)
            }
    }
}
