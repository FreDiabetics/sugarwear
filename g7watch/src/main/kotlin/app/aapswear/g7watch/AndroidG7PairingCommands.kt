package app.aapswear.g7watch

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuses canonical commands; serial IO matches the former single-thread association executor. */
internal class AndroidG7PairingCommands(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : G7PairingCommands {
    private val app = context.applicationContext
    internal var connectCommand: (String) -> Unit = { connectG7SensorToThisWatch(app, it) }
    internal var unlinkCommand: () -> G7UnlinkResult = { unlinkG7Sensor(app) }
    internal var executionOverride: (((() -> Unit)) -> Unit)? = null
    private var lastUnlink: G7UnlinkResult? = null
    override val unlinkWarning: String?
        get() =
            if (lastUnlink?.let { it.detached && it.bondRemovalStatus == G7BondRemovalStatus.FAILED } == true) {
                "Sensor getrennt. Ein verbliebener Android-Bluetooth-Eintrag verhindert das Verbinden eines neuen Sensors nicht."
            } else {
                null
            }

    override suspend fun releaseOwnership() = withContext(dispatcher) { releaseG7CollectorOwnership(app) }

    override suspend fun connect(code: String) = execute { connectCommand(code) }

    override suspend fun unlink(): Boolean = execute { unlinkCommand().also { lastUnlink = it }.detached }

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
