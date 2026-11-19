package app.sugarlicious.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Canonical complication presentation shared by Wear providers and phone previews.
 *
 * Short-text complications deliberately keep the main value, optional secondary value and trend
 * separate. Wear OS can then decide how to lay out text and icon without us concatenating a long
 * string that gets clipped in small slots.
 */
data class ComplicationPresentation(
    val text: String,
    val title: String? = null,
    val trend: Trend? = null,
    val contentDescription: String,
)

enum class TrendVisualAsset { DOUBLE_UP, UP, FORTY_FIVE_UP, FLAT, FORTY_FIVE_DOWN, DOWN, DOUBLE_DOWN }

data class TrendVisualSpec(
    val asset: TrendVisualAsset,
    val canvasWidth: Float = 60f,
    val canvasHeight: Float = 60f,
) {
    val aspectRatio: Float get() = canvasWidth / canvasHeight
}

object TrendVisuals {
    fun spec(trend: Trend): TrendVisualSpec? =
        when (trend) {
            Trend.DOUBLE_UP -> TrendVisualSpec(TrendVisualAsset.DOUBLE_UP, 125f)
            Trend.SINGLE_UP -> TrendVisualSpec(TrendVisualAsset.UP)
            Trend.FORTY_FIVE_UP -> TrendVisualSpec(TrendVisualAsset.FORTY_FIVE_UP)
            Trend.FLAT -> TrendVisualSpec(TrendVisualAsset.FLAT)
            Trend.FORTY_FIVE_DOWN -> TrendVisualSpec(TrendVisualAsset.FORTY_FIVE_DOWN)
            Trend.SINGLE_DOWN -> TrendVisualSpec(TrendVisualAsset.DOWN)
            Trend.DOUBLE_DOWN -> TrendVisualSpec(TrendVisualAsset.DOUBLE_DOWN, 125f)
            Trend.UNKNOWN -> null
        }
}

object SugarliciousComplicationIds {
    const val GLUCOSE = 1
    const val GLUCOSE_TREND = 2
    const val TIME_DELTA = 3
    const val GLUCOSE_TREND_DELTA = 4
    const val GLUCOSE_AGE = 5
    const val GRAPH = 9
    const val IOB = 11
    const val COB = 14
    const val BASAL = 16
    const val LOOP = 19
    const val RESERVOIR = 22
    const val GLUCOSE_PLUS_DELTA = 29
    const val SENSOR_AGE = 30
    const val TIR = 31
    const val GLUCOSE_TREND_DELTA_AGE = 32
    const val GLUCOSE_TREND_AGE = 33
    const val IOB_COB_BASAL = 34
    const val TREND_ONLY = 35
    const val DELTA_ONLY = 36
    const val DATE = 53
    const val IOB_COB = 54

    const val GLUCOSE_LONG = 37
    const val GLUCOSE_RANGED = 38
    const val IOB_RANGED = 39
    const val COB_RANGED = 40
    const val GLUCOSE_TREND_LONG = 41
    const val GLUCOSE_TREND_RANGED = 42
    const val GLUCOSE_PLUS_DELTA_LONG = 43
    const val GLUCOSE_TREND_AGE_LONG = 44
    const val GLUCOSE_TREND_DELTA_AGE_LONG = 45
    const val IOB_COB_BASAL_LONG = 46
    const val LOOP_ICON = 47
    const val RESERVOIR_RANGED = 48
    const val SENSOR_AGE_RANGED = 49
    const val TIR_GOAL = 50
    const val TIR_WEIGHTED = 51
    const val GRAPH_LARGE = 52
    const val GRAPH_ORBIT = 57
    const val IOB_COB_LONG = 55
    const val TIR_RANGED = 56

    val ordered =
        listOf(
            GLUCOSE,
            GLUCOSE_TREND,
            GLUCOSE_PLUS_DELTA,
            GLUCOSE_TREND_AGE,
            GLUCOSE_TREND_DELTA,
            GLUCOSE_TREND_DELTA_AGE,
            GRAPH,
            TREND_ONLY,
            DELTA_ONLY,
            GLUCOSE_AGE,
            TIME_DELTA,
            SENSOR_AGE,
            BASAL,
            IOB,
            COB,
            IOB_COB,
            IOB_COB_BASAL,
            LOOP,
            RESERVOIR,
            TIR,
            DATE,
        )

    val variantsByBase =
        mapOf(
            GLUCOSE to listOf(GLUCOSE, GLUCOSE_LONG, GLUCOSE_RANGED),
            IOB to listOf(IOB, IOB_RANGED),
            COB to listOf(COB, COB_RANGED),
            GLUCOSE_TREND to listOf(GLUCOSE_TREND, GLUCOSE_TREND_LONG, GLUCOSE_TREND_RANGED),
            GLUCOSE_PLUS_DELTA to listOf(GLUCOSE_PLUS_DELTA, GLUCOSE_PLUS_DELTA_LONG),
            GLUCOSE_TREND_AGE to listOf(GLUCOSE_TREND_AGE, GLUCOSE_TREND_AGE_LONG),
            GLUCOSE_TREND_DELTA_AGE to listOf(GLUCOSE_TREND_DELTA_AGE, GLUCOSE_TREND_DELTA_AGE_LONG),
            IOB_COB_BASAL to listOf(IOB_COB_BASAL, IOB_COB_BASAL_LONG),
            IOB_COB to listOf(IOB_COB, IOB_COB_LONG),
            LOOP to listOf(LOOP, LOOP_ICON),
            RESERVOIR to listOf(RESERVOIR, RESERVOIR_RANGED),
            SENSOR_AGE to listOf(SENSOR_AGE, SENSOR_AGE_RANGED),
            TIR to listOf(TIR, TIR_GOAL, TIR_WEIGHTED, TIR_RANGED),
            GRAPH to listOf(GRAPH, GRAPH_LARGE, GRAPH_ORBIT),
        )

    val all: List<Int> =
        ordered.flatMap { base -> variantsByBase[base] ?: listOf(base) }

    fun baseId(id: Int): Int = variantsByBase.entries.firstOrNull { (_, variants) -> id in variants }?.key ?: id
}

object ComplicationPresentationFormatter {
    fun format(
        id: Int,
        state: TherapyDisplayState?,
        nowEpochMs: Long = System.currentTimeMillis(),
        thresholds: CgmThresholds = CgmThresholds.DEFAULT,
    ): ComplicationPresentation {
        val glucose = state?.glucose
        val freshness = TherapyDisplayFormatter.freshness(state, nowEpochMs)
        val retained = freshness in setOf(Freshness.CURRENT, Freshness.DELAYED, Freshness.STALE, Freshness.SIGNAL_LOSS)
        val liveGlucose = glucose.takeIf { retained }
        val rawGlucoseText = liveGlucose?.let(TherapyDisplayFormatter::glucose) ?: DASH
        val glucoseText = rawGlucoseText
        val delta = liveGlucose?.let { TherapyDisplayFormatter.signedDelta(it.deltaMgDl, it.displayUnit) }.orEmpty()
        val age = TherapyDisplayFormatter.ageMinutes(glucose?.measuredAtEpochMs, nowEpochMs)
        val trend = liveGlucose?.trend?.takeUnless { it == Trend.UNKNOWN }

        return when (SugarliciousComplicationIds.baseId(id)) {
            SugarliciousComplicationIds.GLUCOSE ->
                p(glucoseText, desc = "Glukose $glucoseText")

            SugarliciousComplicationIds.TREND_ONLY ->
                p(
                    text = if (trend == null) DASH else "",
                    trend = trend,
                    desc = trend?.let { "Glukosetrend ${TherapyDisplayFormatter.trendArrow(it)}" } ?: "Kein Glukosetrend",
                )

            SugarliciousComplicationIds.DELTA_ONLY ->
                p(delta.ifBlank { DASH }, desc = "Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.GLUCOSE_AGE ->
                p(age, desc = "Glukosealter $age")

            SugarliciousComplicationIds.BASAL -> {
                val basal = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, state?.basal?.currentUnitsPerHour, state)
                p(basal, desc = "Basal $basal")
            }

            SugarliciousComplicationIds.IOB -> {
                val iob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, state?.insulin?.totalIob, state)
                p(iob, desc = "IOB $iob")
            }

            SugarliciousComplicationIds.COB -> {
                val cob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, state?.carbs?.cobGrams, state)
                p(cob, desc = "COB $cob")
            }

            SugarliciousComplicationIds.IOB_COB -> {
                val iob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, state?.insulin?.totalIob, state)
                val cob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, state?.carbs?.cobGrams, state)
                val freshnessLabel =
                    when (freshness) {
                        Freshness.CURRENT -> null
                        Freshness.DELAYED -> "verzögert"
                        Freshness.STALE -> "veraltet"
                        Freshness.SIGNAL_LOSS -> "Signalverlust"
                        Freshness.ERROR -> "Sensorfehler"
                        Freshness.NO_DATA -> if (state == null) "keine Quelle" else "keine aktuellen CGM-Daten"
                    }
                p(
                    text = cob,
                    title = iob,
                    desc = listOfNotNull(freshnessLabel, "IOB $iob", "COB $cob").joinToString(", "),
                )
            }

            SugarliciousComplicationIds.GLUCOSE_TREND ->
                p(
                    text = glucoseText,
                    title = DASH.takeIf { trend == null },
                    trend = trend,
                    desc = if (trend == null) "Glukose $glucoseText, Trend nicht verfügbar" else "Glukose $glucoseText mit Trend",
                )

            SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA ->
                p(glucoseText, delta.ifBlank { DASH }, desc = "Glukose $glucoseText, Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.TIME_DELTA ->
                p(delta.ifBlank { DASH }, age, desc = "Delta ${delta.ifBlank { DASH }}, Alter $age")

            SugarliciousComplicationIds.GLUCOSE_TREND_AGE ->
                p(glucoseText, age, trend, "Glukose $glucoseText mit Trend, Alter $age")

            SugarliciousComplicationIds.GLUCOSE_TREND_DELTA ->
                p(glucoseText, delta.ifBlank { DASH }, trend, "Glukose $glucoseText mit Trend, Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE -> {
                val secondary = listOf(delta.ifBlank { DASH }, age).joinToString(" · ")
                p(glucoseText, secondary, trend, "Glukose $glucoseText mit Trend, Delta ${delta.ifBlank { DASH }}, Alter $age")
            }

            SugarliciousComplicationIds.IOB_COB_BASAL -> {
                val basal = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, state?.basal?.currentUnitsPerHour, state)
                val iob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, state?.insulin?.totalIob, state)
                val cob = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, state?.carbs?.cobGrams, state)
                p(
                    text = "$iob · $cob",
                    title = basal,
                    desc = "Basal $basal, IOB $iob, COB $cob",
                )
            }

            SugarliciousComplicationIds.LOOP -> {
                val loop = loopPresentation(state)
                p(loop.shortText, desc = loop.label)
            }

            SugarliciousComplicationIds.RESERVOIR -> {
                val reservoir = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.RESERVOIR, state?.pump?.reservoirUnits, state)
                p(reservoir, state?.pump?.status?.takeIf { it.isNotBlank() }, desc = "Reservoir $reservoir")
            }

            SugarliciousComplicationIds.SENSOR_AGE ->
                p(DASH, desc = "Sensoralter nicht verfügbar")

            SugarliciousComplicationIds.TIR -> {
                val tir = tirPercent(state, nowEpochMs, thresholds)
                val value = tir?.let { "$it%" } ?: DASH
                p(
                    value,
                    "${thresholds.lowMgDl.roundToInt()}–${thresholds.highMgDl.roundToInt()}",
                    desc = "TIR $value",
                )
            }

            SugarliciousComplicationIds.DATE -> {
                val localDate = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneId.systemDefault())
                val weekday = germanWeekday(localDate.dayOfWeek)
                p(localDate.dayOfMonth.toString(), weekday, desc = "$weekday ${localDate.dayOfMonth}")
            }

            else -> p(DASH, desc = "Keine Daten")
        }
    }

    fun tirPercent(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
        thresholds: CgmThresholds = CgmThresholds.DEFAULT,
    ): Int? {
        val cutoff = nowEpochMs - 24L * 60L * 60_000L
        val samples =
            state?.glucoseHistory.orEmpty().filter {
                it.measuredAtEpochMs in cutoff..(nowEpochMs + FreshnessPolicy.FUTURE_TOLERANCE_MS) &&
                    it.valueMgDl in 20.0..1000.0
            }
        if (samples.isEmpty()) return null
        return (
            samples.count { it.valueMgDl in thresholds.lowMgDl..thresholds.highMgDl } *
                100.0 / samples.size
        ).roundToInt()
    }

    private fun p(
        text: String,
        title: String? = null,
        trend: Trend? = null,
        desc: String,
    ) = ComplicationPresentation(text = text, title = title, trend = trend, contentDescription = desc)

    internal fun germanWeekday(dayOfWeek: DayOfWeek): String =
        when (dayOfWeek) {
            DayOfWeek.MONDAY -> "MON"
            DayOfWeek.TUESDAY -> "DIE"
            DayOfWeek.WEDNESDAY -> "MIT"
            DayOfWeek.THURSDAY -> "DON"
            DayOfWeek.FRIDAY -> "FRE"
            DayOfWeek.SATURDAY -> "SAM"
            DayOfWeek.SUNDAY -> "SON"
        }

    private const val DASH = "—"
}
