package app.sugarlicious.sensor.auth.engine.message

import app.sugarlicious.sensor.auth.engine.util.Util
import java.nio.ByteBuffer

/** JamOrHam / xDrip libkeks auth request, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class AuthRequestTxMessage2(
    tokenSize: Int,
    slot: Int,
) : BaseMessage() {
    @JvmField
    val opcode: Byte = 0x02

    @JvmField
    var singleUseToken: ByteArray

    constructor(tokenSize: Int) : this(tokenSize, false, byteArrayOf())

    constructor(tokenSize: Int, alt: Boolean, challenge: ByteArray) : this(
        tokenSize,
        (if (alt) ALTERNATE_SLOT else STANDARD_SLOT) + (if (challenge.size > 2) challenge[2].toInt() else 0),
    )

    init {
        init(opcode, tokenSize + ENVELOPE_BYTES)
        val token = ByteBuffer.allocate(tokenSize)
        token.put(Util.getRandomKey(), 0, tokenSize)
        singleUseToken = token.array()
        val buffer = data!!
        buffer.put(singleUseToken)
        buffer.put(slot.toByte())
        byteSequence = buffer.array()
    }

    private companion object {
        private const val STANDARD_SLOT = 2
        private const val ALTERNATE_SLOT = 1
        private const val ENVELOPE_BYTES = 2
    }
}
