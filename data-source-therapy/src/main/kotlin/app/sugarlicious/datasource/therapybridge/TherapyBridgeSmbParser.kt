package app.sugarlicious.datasource.therapybridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

data class TherapyBridgeSmb(
    val units: Double,
    val deliveredAtEpochMs: Long,
)

/** Reads a delivered SMB from AAPS' public enacted JSON without importing private AAPS models. */
object TherapyBridgeSmbParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        payload: String?,
        fallbackAtEpochMs: Long?,
    ): TherapyBridgeSmb? {
        if (payload.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val units =
            runCatching { root["units"]?.jsonPrimitive?.doubleOrNull }
                .getOrNull()
                ?.takeIf { it.isFinite() && it in 0.001..25.0 } ?: return null
        val deliveredAt =
            runCatching { root["deliverAt"]?.jsonPrimitive }.getOrNull()?.let { value ->
                value.longOrNull?.let(::normalizeEpoch)
                    ?: value.contentOrNull?.let { text ->
                        text.toLongOrNull()?.let(::normalizeEpoch)
                            ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                    }
            } ?: fallbackAtEpochMs
        return deliveredAt?.takeIf { it > 0L }?.let { TherapyBridgeSmb(units, it) }
    }

    private fun normalizeEpoch(value: Long): Long = if (value in 1..9_999_999_999L) value * 1_000L else value
}
