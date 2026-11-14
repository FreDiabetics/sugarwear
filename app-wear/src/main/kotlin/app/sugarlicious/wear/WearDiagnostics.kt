package app.sugarlicious.wear

import android.content.Context
import androidx.core.net.toUri
import app.sugarlicious.model.DiagnosticBatch
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.protocol.WearProtocol
import app.sugarlicious.storage.DiagnosticEventStore
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

internal suspend fun Context.recordWatchDiagnostic(
    module: String,
    code: String,
    message: String,
    severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    metadata: Map<String, Any?> = emptyMap(),
) {
    DiagnosticEventStore(applicationContext).record(
        origin = "WATCH",
        module = module,
        code = code,
        severity = severity,
        message = message,
        metadata = metadata,
    )
}

internal suspend fun sendWatchDiagnostics(
    context: Context,
    nodeId: String,
) {
    val localEvents = DiagnosticEventStore(context).snapshot()
    val cgmEvents = readCgmDiagnostics(context)
    val events = (localEvents + cgmEvents).distinctBy(DiagnosticEvent::id).sortedBy(DiagnosticEvent::occurredAtEpochMs).takeLast(1_000)
    Wearable
        .getMessageClient(context)
        .sendMessage(
            nodeId,
            WearProtocol.DIAGNOSTICS_BATCH_PATH,
            WearProtocol.encodeDiagnostics(DiagnosticBatch(events, System.currentTimeMillis())),
        ).await()
}

private fun readCgmDiagnostics(context: Context): List<DiagnosticEvent> =
    runCatching {
        context.contentResolver
            .query(
                "content://app.sugarwear.readings/diagnostics".toUri(),
                null,
                null,
                null,
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DiagnosticEvent(
                                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                                occurredAtEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
                                origin = cursor.getString(cursor.getColumnIndexOrThrow("origin")),
                                module = cursor.getString(cursor.getColumnIndexOrThrow("module")),
                                code = cursor.getString(cursor.getColumnIndexOrThrow("code")),
                                severity =
                                    runCatching {
                                        DiagnosticSeverity.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("severity")))
                                    }.getOrDefault(DiagnosticSeverity.WARNING),
                                message = cursor.getString(cursor.getColumnIndexOrThrow("message")),
                                metadata =
                                    cursor
                                        .getString(cursor.getColumnIndexOrThrow("metadata"))
                                        .takeIf(String::isNotBlank)
                                        ?.let { mapOf("details" to it) }
                                        .orEmpty(),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }.getOrDefault(emptyList())
