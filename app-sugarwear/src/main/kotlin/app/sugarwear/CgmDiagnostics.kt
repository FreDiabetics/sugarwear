package app.sugarwear

import android.content.Context
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.storage.DiagnosticEventStore

internal suspend fun Context.recordCgmDiagnostic(
    code: String,
    message: String,
    severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    metadata: Map<String, Any?> = emptyMap(),
) {
    DiagnosticEventStore(applicationContext).record(
        origin = "G7-WATCH",
        module = "G7-COLLECTOR",
        code = code,
        severity = severity,
        message = message,
        metadata = metadata,
    )
}
