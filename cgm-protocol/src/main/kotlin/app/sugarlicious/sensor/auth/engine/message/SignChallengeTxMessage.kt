package app.sugarlicious.sensor.auth.engine.message

import app.sugarlicious.sensor.auth.engine.util.Util
import java.nio.ByteBuffer

/** JamOrHam / xDrip libkeks signing request, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class SignChallengeTxMessage(
    private val challenge: ByteArray,
) : BaseMessage() {
    constructor() : this(Util.getRandomKey())

    init {
        val buffer = ByteBuffer.allocate(FRAME_BYTES)
        buffer.put(OPCODE)
        buffer.put(challenge)
        data = buffer
        byteSequence = buffer.array()
    }

    fun getChallengeHash(): ByteArray = challenge

    private companion object {
        private const val OPCODE: Byte = 0x0c
        private const val FRAME_BYTES = 17
    }
}
