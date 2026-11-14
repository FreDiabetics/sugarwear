package app.sugarlicious.datasource.therapybridge

import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.PredictionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Normalizes the public Suggested/Enacted predBGs payload without depending on AAPS classes. */
object TherapyBridgePredictionParser {
    private const val STEP_MS = 5 * 60_000L
    private const val MAX_POINTS_PER_SERIES = 96
    private val json = Json { ignoreUnknownKeys = true }
    private val keys =
        listOf(
            "IOB" to PredictionKind.IOB,
            "COB" to PredictionKind.COB,
            "aCOB" to PredictionKind.ACOB,
            "UAM" to PredictionKind.UAM,
            "ZT" to PredictionKind.ZERO_TEMP,
        )

    fun parse(
        payload: String?,
        baseAtEpochMs: Long?,
    ): List<GlucosePrediction> {
        if (payload.isNullOrBlank() || baseAtEpochMs == null || baseAtEpochMs <= 0L) return emptyList()
        val predictions =
            runCatching {
                json.parseToJsonElement(payload).jsonObject["predBGs"]?.jsonObject
            }.getOrNull() ?: return emptyList()

        return keys.mapNotNull { (key, kind) ->
            val samples =
                runCatching { predictions[key]?.jsonArray }
                    .getOrNull()
                    ?.take(MAX_POINTS_PER_SERIES)
                    ?.mapIndexedNotNull { index, item ->
                        val value =
                            runCatching { item.jsonPrimitive.doubleOrNull }
                                .getOrNull()
                                ?.takeIf { it.isFinite() && it in 20.0..1_000.0 }
                        value?.let { GlucoseSample(it, baseAtEpochMs + index * STEP_MS) }
                    }.orEmpty()
            samples.takeIf { it.size >= 2 }?.let { GlucosePrediction(kind, it) }
        }
    }
}
