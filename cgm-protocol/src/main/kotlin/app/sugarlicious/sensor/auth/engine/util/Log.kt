package app.sugarlicious.sensor.auth.engine.util

import java.util.concurrent.CancellationException
import java.util.logging.Logger

/** JamOrHam / xDrip compatibility logging; raw auth diagnostics remain suppressed. */
class Log private constructor() {
    companion object {
        private val logger = Logger.getLogger("app.aapswear.g7.crypto")

        // Upstream messages can contain secrets. Never forward either raw argument to a logger.
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic
        fun d(tag: String?, message: String?) = Unit

        @JvmStatic
        fun l(message: String?) = d("KEKS-Plugin", message)

        /** No exception message, stack trace, packet, key or pairing code is included. */
        fun cryptoFailure(operation: String, failure: Exception) {
            if (failure is CancellationException) throw failure
            logger.fine("$operation:${failure.javaClass.simpleName}")
        }
    }
}
