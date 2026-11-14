package app.sugarlicious.datasource.therapybridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Extracts the effective APS target from the public suggested/enacted payload. */
object TherapyBridgeTargetParser {
    private val json = Json { ignoreUnknownKeys = true }

    data class ParsedTarget(
        val valueMgDl: Double,
        val temporary: Boolean,
    )

    fun parse(payload: String?): Double? = parseTarget(payload)?.valueMgDl

    fun parseTarget(payload: String?): ParsedTarget? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val objectValue = json.parseToJsonElement(payload).jsonObject
            val target =
                objectValue["targetBG"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?.takeIf { it.isFinite() && it in 20.0..1_000.0 }
                    ?: return@runCatching null
            val explicitTemporary =
                objectValue["temporary"]?.jsonPrimitive?.booleanOrNull == true ||
                    objectValue["isTempTarget"]?.jsonPrimitive?.booleanOrNull == true
            ParsedTarget(
                valueMgDl = target,
                temporary = explicitTemporary,
            )
        }.getOrNull()
    }
}
