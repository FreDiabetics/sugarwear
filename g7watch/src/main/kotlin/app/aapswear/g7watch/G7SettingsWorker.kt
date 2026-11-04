package app.aapswear.g7watch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal enum class G7SettingsIoError { STORAGE_UNAVAILABLE }

internal data class G7SettingsIoState<T>(
    val snapshot: T? = null,
    val loading: Boolean = true,
    val error: G7SettingsIoError? = null,
)

/** One ordered, lifecycle-owned queue: no preference/database/channel IO runs on the UI thread. */
internal class G7SettingsWorker<T>(
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val read: () -> T,
) {
    private data class Work(
        val action: () -> Unit,
        val completed: () -> Unit,
        val failed: () -> Unit = {},
        val refreshDelayMs: Long = 0L,
    )

    private val queue = Channel<Work>(Channel.UNLIMITED)
    private var pending = 1
    private val mutableState = MutableStateFlow(G7SettingsIoState<T>())
    val state = mutableState.asStateFlow()

    init {
        queue.trySend(Work({}, {}))
        scope
            .launch {
                for (work in queue) {
                    mutableState.value = state.value.copy(loading = true, error = null)
                    try {
                        withContext(io) { work.action() }
                        if (work.refreshDelayMs > 0L) delay(work.refreshDelayMs)
                        val snapshot = withContext(io) { read() }
                        pending -= 1
                        mutableState.value = G7SettingsIoState(snapshot = snapshot, loading = pending > 0)
                        work.completed()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (error !is IOException && error !is SecurityException && error !is android.database.SQLException) throw error
                        java.util.logging.Logger
                            .getLogger("G7Settings")
                            .warning("Settings IO failed: ${error.javaClass.simpleName}")
                        pending -= 1
                        mutableState.value = state.value.copy(loading = pending > 0, error = G7SettingsIoError.STORAGE_UNAVAILABLE)
                        work.failed()
                    }
                }
            }.invokeOnCompletion { queue.close() }
    }

    fun submit(action: () -> Unit = {}, completed: () -> Unit = {}, failed: () -> Unit = {}, refreshDelayMs: Long = 0L) {
        pending += 1
        mutableState.value = state.value.copy(loading = true)
        if (queue.trySend(Work(action, completed, failed, refreshDelayMs)).isFailure) pending -= 1
    }

    fun snapshot(): T = checkNotNull(state.value.snapshot) { "Settings have not loaded" }
}
