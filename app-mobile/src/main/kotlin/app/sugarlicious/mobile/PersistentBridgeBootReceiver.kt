package app.sugarlicious.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun shouldRestorePersistentBridge(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

/** Restoration is best effort: a retained widget surface must never terminate the bridge process. */
internal suspend fun restoreBootWidgets(readSnapshot: suspend () -> Unit, updateWidgets: suspend () -> Unit, onFailure: suspend (Exception) -> Unit) {
    try {
        readSnapshot()
        updateWidgets()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
    }
}

class PersistentBridgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (shouldRestorePersistentBridge(intent.action)) {
            PersistentBridgeService.start(context)
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    // Reading is intentional: restoration must not rewrite timestamps, resolver
                    // counters, or turn the persisted snapshot into a new measurement event.
                    restoreBootWidgets(
                        readSnapshot = {
                            TherapyStateStore(context.applicationContext).state.first()
                        },
                        updateWidgets = { SugarliciousWidgets.update(context.applicationContext) },
                        onFailure = { error ->
                            context.recordMobileDiagnostic(
                                module = "WIDGET",
                                code = "WIDGET-RESTORE-503",
                                message = "Widget restoration retained previous surfaces; bridge remains active",
                                severity = app.sugarlicious.model.DiagnosticSeverity.WARNING,
                                metadata = mapOf("error" to error.javaClass.simpleName),
                            )
                        },
                    )
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
