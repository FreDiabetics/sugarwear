package app.sugarlicious.sensor.auth.engine.message

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JamOrHam / xDrip libkeks message buffer, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
open class BaseMessage {
    @JvmField
    var data: ByteBuffer? = null

    @Volatile
    @JvmField
    var byteSequence: ByteArray? = null

    protected fun init(opcode: Byte, length: Int) {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        data = buffer
        buffer.put(opcode)
        if (length == 1) getByteSequence()
    }

    fun getByteSequence(): ByteArray = data!!.array().also { byteSequence = it }
}
