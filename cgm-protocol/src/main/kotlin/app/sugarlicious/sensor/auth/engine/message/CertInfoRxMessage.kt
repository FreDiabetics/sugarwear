package app.sugarlicious.sensor.auth.engine.message

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JamOrHam / xDrip libkeks certificate reply, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class CertInfoRxMessage(
    packet: ByteArray,
) : BaseMessage() {
    var size = -1
        private set

    var which = -1
        private set

    var state = 0
        private set

    init {
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        data = buffer
        if (packet.size == FRAME_BYTES && buffer.get() == opcode) {
            state = buffer.get().toInt()
            which = buffer.get().toInt()
            // Deliberately signed 16-bit legacy field; do not reinterpret as unsigned/int.
            size = buffer.short.toInt()
        }
    }

    fun valid(): Boolean = size > 0 && state == 0 && which >= 0

    companion object {
        const val opcode: Byte = 0x0b
        private const val FRAME_BYTES = 7
    }
}
