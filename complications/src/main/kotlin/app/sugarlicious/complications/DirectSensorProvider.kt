package app.sugarlicious.complications

import android.content.Context
import android.database.Cursor
import android.net.Uri
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.Trend

internal data class DirectReading(
    val sensorId: String,
    val sessionId: String,
    val sequenceNumber: Long?,
    val value: Double,
    val measuredAt: Long,
    val receivedAt: Long,
    val delta: Double?,
    val trend: Trend,
    val quality: CgmQuality,
    val trendRateMgDlPerMinute: Double? = null,
)

internal data class DirectStatus(
    val sensorState: String,
    val sessionState: String,
    val collectorEnabled: Boolean = true,
    val sensorId: String? = null,
    val sessionId: String? = null,
)

internal data class DirectSnapshot(
    val readings: List<DirectReading>,
    val status: DirectStatus?,
)

internal enum class ProviderFailureKind { UNAVAILABLE, DENIED, MALFORMED }

internal sealed interface DirectProviderOutcome {
    data class Success(
        val snapshot: DirectSnapshot,
    ) : DirectProviderOutcome

    data object Empty : DirectProviderOutcome

    data class Failure(
        val kind: ProviderFailureKind,
    ) : DirectProviderOutcome
}

internal fun interface DirectCgmProvider {
    fun read(): DirectProviderOutcome
}

internal class AndroidDirectCgmProvider(
    private val context: Context,
    private val cursorQuery: (Uri) -> Cursor? = { uri -> context.contentResolver.query(uri, null, null, null, null) },
) : DirectCgmProvider {
    override fun read(): DirectProviderOutcome =
        try {
            val readings = queryReadings()
            val status = queryStatus()
            if (readings.isEmpty() && status == null) DirectProviderOutcome.Empty else DirectProviderOutcome.Success(DirectSnapshot(readings, status))
        } catch (_: SecurityException) {
            DirectProviderOutcome.Failure(ProviderFailureKind.DENIED)
        } catch (_: IllegalArgumentException) {
            DirectProviderOutcome.Failure(ProviderFailureKind.MALFORMED)
        } catch (_: IllegalStateException) {
            DirectProviderOutcome.Failure(ProviderFailureKind.MALFORMED)
        } catch (_: RuntimeException) {
            DirectProviderOutcome.Failure(ProviderFailureKind.UNAVAILABLE)
        }

    private fun queryStatus(): DirectStatus? =
        query(STATE_URI)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DirectStatus(
                sensorState = cursor.requiredString("sensor_state"),
                sessionState = cursor.requiredString("session_state"),
                collectorEnabled = cursor.getInt(cursor.requiredIndex("collector_enabled")) == 1,
                sensorId = cursor.optionalString("sensor_id"),
                sessionId = cursor.optionalString("session_id"),
            )
        }

    private fun queryReadings(): List<DirectReading> =
        query(READINGS_URI)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val quality =
                            when (cursor.requiredString("status")) {
                                "VALID" -> CgmQuality.VALID
                                "SENSOR_ERROR" -> CgmQuality.SENSOR_ERROR
                                else -> continue
                            }
                        val value = cursor.getDouble(cursor.requiredIndex("glucose"))
                        if (quality == CgmQuality.VALID && (!value.isFinite() || value !in 20.0..1_000.0)) continue
                        add(
                            DirectReading(
                                sensorId = cursor.requiredString("sensor_id"),
                                sessionId = cursor.requiredString("session_id"),
                                sequenceNumber = cursor.nullableLong("sequence_number"),
                                value = value,
                                measuredAt = cursor.getLong(cursor.requiredIndex("measured_at")),
                                receivedAt = cursor.getLong(cursor.requiredIndex("received_at")),
                                delta = cursor.nullableDouble("delta"),
                                trendRateMgDlPerMinute = cursor.nullableDouble("trend_rate"),
                                trend = runCatching { Trend.valueOf(cursor.requiredString("trend")) }.getOrDefault(Trend.UNKNOWN),
                                quality = quality,
                            ),
                        )
                    }
                }
            }.orEmpty()

    private fun query(uri: Uri): Cursor? = cursorQuery(uri)

    private fun Cursor.requiredIndex(name: String): Int = getColumnIndexOrThrow(name)

    private fun Cursor.requiredString(name: String): String = getString(requiredIndex(name)) ?: throw IllegalArgumentException("Null $name")

    private fun Cursor.optionalString(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { getString(it) }?.takeIf { it.isNotBlank() }

    private fun Cursor.nullableLong(name: String): Long? = requiredIndex(name).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.nullableDouble(name: String): Double? = requiredIndex(name).let { if (isNull(it)) null else getDouble(it) }

    private companion object {
        val READINGS_URI: Uri = Uri.parse("content://app.sugarwear.readings/readings")
        val STATE_URI: Uri = Uri.parse("content://app.sugarwear.readings/state")
    }
}
