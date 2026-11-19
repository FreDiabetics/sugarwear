package app.sugarlicious.sensor.auth.engine.message

import java.nio.ByteBuffer

/** JamOrHam / xDrip libkeks challenge frame, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class AuthChallengeTxMessage(
    challenge: ByteArray,
) : BaseMessage() {
    init {
        val buffer = ByteBuffer.allocate(FRAME_BYTES)
        buffer.put(OPCODE)
        buffer.put(challenge)
        data = buffer
        byteSequence = buffer.array()
    }

    private companion object {
        private const val OPCODE: Byte = 0x04
        private const val FRAME_BYTES = 9
    }
}
