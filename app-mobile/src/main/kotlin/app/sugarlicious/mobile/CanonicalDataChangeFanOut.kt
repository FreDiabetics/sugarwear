package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * The single post-commit fan-out for Mobile's canonical state.
 *
 * Widget rendering can involve multiple Glance hosts. It must therefore never sit in front of the
 * latency-sensitive Wear message in one sequential coroutine.
 */
internal object CanonicalDataChangeFanOut {
    suspend fun dispatch(
        updateWidgets: suspend () -> Unit,
        pushWear: suspend () -> Unit,
    ) = supervisorScope {
        val widget = async { updateWidgets() }
        val wear = async { pushWear() }
        widget.await()
        wear.await()
    }
}

internal suspend fun dispatchCanonicalDataChanged(
    context: Context,
    state: TherapyDisplayState,
) {
    val app = context.applicationContext
    val diagnostics = app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    diagnostics.edit { putLong("canonicalCommittedAt", System.currentTimeMillis()) }

    CanonicalDataChangeFanOut.dispatch(
        updateWidgets = {
            runCatching { SugarliciousWidgets.update(app, state) }
                .onSuccess { diagnostics.edit { putLong("widgetObservedAt", System.currentTimeMillis()) } }
                .onFailure { error ->
                    app.recordMobileDiagnostic(
                        "WIDGET",
                        "WIDGET-UPDATE-503",
                        "Canonical widget invalidation failed",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                }
        },
        pushWear = {
            diagnostics.edit { putLong("wearSendStartedAt", System.currentTimeMillis()) }
            runCatching { publishState(app, state) }
                .onSuccess { diagnostics.edit { putLong("wearSendCompletedAt", System.currentTimeMillis()) } }
                .onFailure { error ->
                    app.recordMobileDiagnostic(
                        "SYNC",
                        "SYNC-WATCH-503",
                        "Canonical state could not be published to Watch",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                }
        },
    )
}
