package app.sugarlicious.mobile

import android.content.Context
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.protocol.WearProtocol
import app.sugarlicious.storage.DiagnosticEventStore

internal suspend fun Context.recordMobileDiagnostic(
    module: String,
    code: String,
    message: String,
    severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    metadata: Map<String, Any?> = emptyMap(),
) {
    DiagnosticEventStore(applicationContext).record(
        origin = "MOBILE",
        module = module,
        code = code,
        severity = severity,
        message = message,
        metadata = metadata,
    )
}

internal suspend fun requestWatchDiagnostics(context: Context): Int {
    val nodes = refreshReachableWatchNodeIds(context)
    nodes.forEach { nodeId ->
        context.sendLoggedWearMessage(nodeId, WearProtocol.DIAGNOSTICS_REQUEST_PATH, byteArrayOf(), "Watch-Diagnose anfordern")
    }
    return nodes.size
}
