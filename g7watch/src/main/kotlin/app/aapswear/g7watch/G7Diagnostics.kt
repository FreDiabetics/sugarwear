package app.aapswear.g7watch

import android.content.Context
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.storage.DiagnosticEventStore

internal suspend fun Context.recordG7Diagnostic(
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
