package app.sugarwear

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuses canonical commands; serial IO matches the former single-thread association executor. */
internal class AndroidCgmPairingCommands(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : CgmPairingCommands {
    private val app = context.applicationContext
    internal var connectCommand: (String) -> Unit = { connectCgmSensorToThisWatch(app, it) }
    internal var unlinkCommand: () -> CgmUnlinkResult = { unlinkCgmSensor(app) }
    @Volatile
    private var lastUnlinkFailure: String? = null

    override val unlinkWarning: String? get() = lastUnlinkFailure
    internal var executionOverride: (((() -> Unit)) -> Unit)? = null

    override suspend fun releaseOwnership() = withContext(dispatcher) { releaseCgmCollectorOwnership(app) }

    override suspend fun connect(code: String) = execute { connectCommand(code) }

    override suspend fun unlink(): Boolean =
        execute {
            val outcome = unlinkCommand()
            lastUnlinkFailure = outcome.failureReason
            outcome.detached
        }

    private suspend fun <T> execute(block: () -> T): T =
        withContext(dispatcher) {
            val executor = executionOverride
            if (executor == null) {
                block()
            } else {
                suspendCancellableCoroutine { continuation ->
                    executor {
                        if (continuation.isActive) {
                            try {
                                continuation.resume(block())
                            } catch (error: Exception) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }
                }
            }
        }
}
