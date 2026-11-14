package app.sugarlicious.sensor.protocol

/** Session-confined authentication boundary. Its owner serializes calls and owns transport deadlines. */
interface SensorAuthentication {
    fun connected(): AuthInputResult

    fun next(): AuthStep

    fun onAuthenticationData(data: ByteArray): AuthInputResult

    fun onExtraData(data: ByteArray): AuthInputResult

    fun shouldBond(data: ByteArray): Boolean

    fun sharedKey(): AuthKeyResult
}

/** Owned bytes with value equality and redacted rendering, including when nested in results. */
class AuthBytes(
    bytes: ByteArray,
) {
    private val value = bytes.copyOf()

    fun copyBytes(): ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean = other is AuthBytes && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "AuthBytes(size=${value.size})"
}

enum class AuthChannel { EXTRA, AUTHENTICATION }

data class AuthWrite(
    val channel: AuthChannel,
    val payload: AuthBytes,
)

sealed interface AuthStep {
    /** Write in list order: KEKS extra data precedes the authentication command. */
    data class Send(
        val writes: List<AuthWrite>,
    ) : AuthStep

    /** Keep the final command available to the caller; completion does not silently discard it. */
    data class Complete(
        val command: AuthBytes,
    ) : AuthStep

    data class BondRequired(
        val command: AuthBytes,
    ) : AuthStep

    data class Failed(
        val error: AuthError,
    ) : AuthStep
}

sealed interface AuthInputResult {
    data object Advance : AuthInputResult

    /** The legacy engine requested no next action; it does not reveal whether it waits or rejects. */
    data object NoAdvance : AuthInputResult

    data class Failed(
        val error: AuthError,
    ) : AuthInputResult
}

sealed interface AuthKeyResult {
    data class Available(
        val key: AuthBytes,
    ) : AuthKeyResult

    data object Unavailable : AuthKeyResult
}

enum class AuthField { OUTPUT_ARITY, COMMAND, EXTRA_DATA, AUTHENTICATION_INPUT, EXTRA_INPUT, ENGINE }

sealed interface AuthError {
    data class InvalidLength(
        val field: AuthField,
        val actual: Int,
    ) : AuthError

    data class MissingPayload(
        val field: AuthField,
    ) : AuthError

    data class MalformedFrame(
        val field: AuthField,
        val actual: Int,
    ) : AuthError

    data class InputRejected(
        val field: AuthField,
    ) : AuthError

    data object UnexpectedOutputShape : AuthError

    data object PeerAuthenticationMismatch : AuthError

    /** Legacy null conveys no root cause. In particular, it is not evidence of a crypto timeout. */
    data object LegacyEngineUnavailable : AuthError
}
