package app.sugarlicious.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun shouldRestoreWearRuntime(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

class WearRuntimeBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!shouldRestoreWearRuntime(intent.action)) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { StateDataLayerService.start(context) }
                    .onFailure { error ->
                        context.applicationContext.recordWatchDiagnostic(
                            "RUNTIME",
                            "WATCH-FGS-503",
                            "Wear runtime foreground service could not be restored",
                            DiagnosticSeverity.ERROR,
                            mapOf("error" to error.javaClass.simpleName, "action" to intent.action),
                        )
                    }
                runCatching { resolveAndPublishCurrentCgmAlertMode(context.applicationContext) }
                    .onFailure { error ->
                        context.applicationContext.recordWatchDiagnostic(
                            "SOURCE",
                            "SRC-ALARM-MODE-503",
                            "Canonical G7 alarm mode could not be restored",
                            DiagnosticSeverity.WARNING,
                            mapOf("error" to error.javaClass.simpleName, "action" to intent.action),
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
