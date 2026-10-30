package app.aapswear.g7watch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.net.toUri
import app.aapswear.storage.DiagnosticEventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class G7ReadingProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri.lastPathSegment == "state") {
            val state = G7SensorStateStore(requireNotNull(context)).read()
            return MatrixCursor(arrayOf("sensor_state", "session_state", "collector_enabled", "sensor_id", "session_id")).apply {
                addRow(
                    arrayOf<Any?>(
                        state.sensor?.state?.name ?: "NOT_ACTIVE",
                        state.sessionState.name,
                        if (state.collectorEnabled) 1 else 0,
                        state.sensor?.sensorId,
                        state.sensor?.sessionId,
                    ),
                )
            }
        }
        if (uri.lastPathSegment == "diagnostics") {
            val columns =
                arrayOf(
                    "id",
                    "occurred_at",
                    "origin",
                    "module",
                    "code",
                    "severity",
                    "message",
                    "metadata",
                )
            val cursor = MatrixCursor(columns)
            val events =
                runBlocking(Dispatchers.IO) {
                    DiagnosticEventStore(requireNotNull(context)).snapshot()
                }
            events.forEach { event ->
                cursor.addRow(
                    arrayOf<Any?>(
                        event.id,
                        event.occurredAtEpochMs,
                        event.origin,
                        event.module,
                        event.code,
                        event.severity.name,
                        event.message,
                        event.metadata.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    ),
                )
            }
            return cursor
        }

        val columns =
            arrayOf(
                "id",
                "sensor_id",
                "session_id",
                "sequence_number",
                "glucose",
                "measured_at",
                "received_at",
                "delta",
                "trend",
                "trend_rate",
                "status",
                "predicted",
                "sensor_age",
                "display_only",
                "sensor_clock",
                "sensor_start",
                "sensor_end",
                "grace_end",
                "protocol_status",
                "calibration_state",
                "reserved_field",
                "origin",
                "collector_enabled",
            )
        val cursor = MatrixCursor(columns)
        val collectorEnabled = G7SensorStateStore(requireNotNull(context)).read().collectorEnabled
        val limit = if (uri.lastPathSegment == "latest") 1 else 300
        val readings =
            G7ReadingDatabase(requireNotNull(context)).let { database ->
                try {
                    database.query(limit = limit)
                } finally {
                    database.close()
                }
            }
        readings.forEach { reading ->
            cursor.addRow(
                arrayOf<Any?>(
                    reading.id,
                    reading.sensorId,
                    reading.sessionId,
                    reading.sequenceNumber,
                    reading.glucoseMgDl,
                    reading.timestampEpochMs,
                    reading.receivedAtEpochMs,
                    reading.deltaMgDl,
                    reading.trend.name,
                    reading.trendRateMgDlPerMinute,
                    reading.status.name,
                    reading.predictedMgDl,
                    reading.sensorAgeSeconds,
                    if (reading.displayOnly) 1 else 0,
                    reading.rawSourceTimestamp,
                    reading.sensorStartEpochMs,
                    reading.sensorEndEpochMs,
                    reading.graceEndEpochMs,
                    reading.protocolStatusCode,
                    reading.calibrationStateCode,
                    reading.reservedField,
                    reading.origin.name,
                    if (collectorEnabled) 1 else 0,
                ),
            )
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, CONTENT_URI)
        return cursor
    }

    override fun getType(uri: Uri): String =
        when (uri.lastPathSegment) {
            "latest" -> "vnd.android.cursor.item/vnd.sugarlicious.g7"
            "diagnostics" -> "vnd.android.cursor.dir/vnd.sugarlicious.diagnostics"
            "state" -> "vnd.android.cursor.item/vnd.sugarlicious.g7.state"
            else -> "vnd.android.cursor.dir/vnd.sugarlicious.g7"
        }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    companion object {
        val CONTENT_URI: Uri = "content://app.sugarwear.readings/readings".toUri()
        val DIAGNOSTICS_URI: Uri = "content://app.sugarwear.readings/diagnostics".toUri()
        val STATE_URI: Uri = "content://app.sugarwear.readings/state".toUri()
    }
}
