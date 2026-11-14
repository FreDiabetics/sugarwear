package app.sugarlicious.datasource.therapybridge

import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyEventSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Parses the optional read-only treatment history added to the existing AAPS status contract. */
object TherapyBridgeTherapyEventParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String?): List<TherapyEvent> {
        if (payload.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(payload).jsonArray }.getOrNull() ?: return emptyList()
        return array
            .mapNotNull { element ->
                val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val kind = item["type"]?.jsonPrimitive?.contentOrNull?.let(::parseKind) ?: return@mapNotNull null
                val timestamp =
                    item["timestamp"]
                        ?.jsonPrimitive
                        ?.longOrNull
                        ?.let(::normalizeEpoch)
                        ?.takeIf { it > 0L }
                        ?: return@mapNotNull null
                val amount =
                    item["amount"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0.0 }
                        ?: return@mapNotNull null
                val suppliedId =
                    item["id"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                TherapyEvent(
                    id = suppliedId ?: "${kind.name}:$timestamp:$amount",
                    kind = kind,
                    timestampEpochMs = timestamp,
                    amount = amount,
                    source = TherapyEventSource.AAPS_ONLY,
                    originalSourceId = suppliedId,
                    insulinUnits = amount.takeIf { kind in insulinKinds },
                    carbsGrams = amount.takeIf { kind in carbKinds },
                    eventType = item["type"]?.jsonPrimitive?.contentOrNull,
                )
            }.distinctBy(TherapyEvent::id)
            .sortedBy(TherapyEvent::timestampEpochMs)
    }

    private fun parseKind(value: String): TherapyEventKind? =
        when (value.trim().uppercase()) {
            "SMB" -> TherapyEventKind.SMB
            "CORRECTION", "MANUAL_CORRECTION" -> TherapyEventKind.MANUAL_CORRECTION
            "MEAL_BOLUS" -> TherapyEventKind.MEAL_BOLUS
            "CARBS", "MEAL_CARBS" -> TherapyEventKind.MEAL_CARBS
            "ECARBS", "ECARB" -> TherapyEventKind.ECARBS
            else -> null
        }

    private fun normalizeEpoch(value: Long): Long = if (value in 1..9_999_999_999L) value * 1_000L else value

    private val insulinKinds = setOf(TherapyEventKind.SMB, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.MEAL_BOLUS)
    private val carbKinds = setOf(TherapyEventKind.MEAL_CARBS, TherapyEventKind.ECARBS)
}
