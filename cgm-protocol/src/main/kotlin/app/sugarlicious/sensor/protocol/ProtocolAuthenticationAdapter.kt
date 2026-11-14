package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.engine.AuthenticationMismatchException
import java.security.InvalidParameterException

/** Typed session facade; the Kotlin state machine now supplies AuthStep directly. */
class ProtocolAuthenticationAdapter(
    private val session: CgmAuthenticationSession,
) : SensorAuthentication {
    override fun connected(): AuthInputResult {
        session.connected()
        return AuthInputResult.Advance
    }

    override fun next(): AuthStep = session.nextStep()

    override fun onAuthenticationData(data: ByteArray): AuthInputResult =
        receive(AuthField.AUTHENTICATION_INPUT, data, session::onAuthenticationData)

    override fun onExtraData(data: ByteArray): AuthInputResult =
        receive(AuthField.EXTRA_INPUT, data, session::onExtraData)

    override fun shouldBond(data: ByteArray): Boolean = session.shouldBond(data)

    override fun sharedKey(): AuthKeyResult =
        session.sharedKey()?.let { AuthKeyResult.Available(AuthBytes(it)) } ?: AuthKeyResult.Unavailable

    private inline fun receive(
        field: AuthField,
        data: ByteArray,
        consume: (ByteArray) -> Boolean,
    ): AuthInputResult {
        if (data.isEmpty()) return AuthInputResult.Failed(AuthError.InvalidLength(field, data.size))
        return try {
            if (consume(data.copyOf())) AuthInputResult.Advance else AuthInputResult.NoAdvance
        } catch (_: AuthenticationMismatchException) {
            AuthInputResult.Failed(AuthError.PeerAuthenticationMismatch)
        } catch (_: InvalidParameterException) {
            AuthInputResult.Failed(AuthError.InputRejected(field))
        } catch (_: IndexOutOfBoundsException) {
            AuthInputResult.Failed(AuthError.MalformedFrame(field, data.size))
        }
    }
}

/** Only this adapter knows the legacy array/null control protocol. No wire bytes are rewritten. */
internal fun decodeLegacyAuthStep(output: Array<ByteArray?>?): AuthStep {
    if (output == null) return AuthStep.Failed(AuthError.LegacyEngineUnavailable)
    return when (output.size) {
        1, 3 -> {
            val command = output[0] ?: return AuthStep.Failed(AuthError.MissingPayload(AuthField.COMMAND))
            if (command.isEmpty()) return AuthStep.Failed(AuthError.InvalidLength(AuthField.COMMAND, 0))
            if (output.drop(1).any { it != null }) return AuthStep.Failed(AuthError.UnexpectedOutputShape)
            if (output.size == 1) AuthStep.Complete(AuthBytes(command)) else AuthStep.BondRequired(AuthBytes(command))
        }
        2 -> {
            val writes =
                buildList {
                    output[1]?.let { add(AuthWrite(AuthChannel.EXTRA, AuthBytes(it))) }
                    output[0]?.let { add(AuthWrite(AuthChannel.AUTHENTICATION, AuthBytes(it))) }
                }
            when {
                writes.isEmpty() -> AuthStep.Failed(AuthError.UnexpectedOutputShape)
                output[0]?.isEmpty() == true -> AuthStep.Failed(AuthError.InvalidLength(AuthField.COMMAND, 0))
                output[1]?.isEmpty() == true -> AuthStep.Failed(AuthError.InvalidLength(AuthField.EXTRA_DATA, 0))
                else -> AuthStep.Send(writes)
            }
        }
        else -> AuthStep.Failed(AuthError.InvalidLength(AuthField.OUTPUT_ARITY, output.size))
    }
}
