package app.sugarlicious.wear

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

internal object WearSurfaceUpdateCoordinator {
    private suspend fun isolate(update: suspend () -> Unit) {
        try {
            update()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Each surface is independently invalidated; one unavailable provider must not delay the others.
        }
    }

    suspend fun dispatch(
        updateApp: suspend () -> Unit,
        updateTiles: suspend () -> Unit,
        updateComplications: suspend () -> Unit,
    ) = supervisorScope {
        listOf(
            async { isolate(updateApp) },
            async { isolate(updateTiles) },
            async { isolate(updateComplications) },
        ).awaitAll()
    }
}
