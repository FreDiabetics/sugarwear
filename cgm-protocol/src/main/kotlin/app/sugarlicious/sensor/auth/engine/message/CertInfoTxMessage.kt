package app.sugarlicious.sensor.auth.engine.message

import app.sugarlicious.sensor.auth.engine.Plugin

/** JamOrHam / xDrip libkeks certificate request, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class CertInfoTxMessage : BaseMessage() {
    companion object {
        const val opcode: Byte = 0x0b
        private const val FRAME_BYTES = 6

        @JvmStatic
        fun expectMyCert(plugin: Plugin, which: Int): ByteArray {
            val context = plugin.context
            val message = CertInfoTxMessage()
            message.init(opcode, FRAME_BYTES)
            val buffer = message.data!!
            buffer.put(which.toByte())
            buffer.putInt(if (which == 0) context.partA!!.size else context.partB!!.size)
            return message.getByteSequence()
        }

        @JvmStatic
        fun expectMyCert1(plugin: Plugin): ByteArray = expectMyCert(plugin, 0)

        @JvmStatic
        fun expectMyCert2(plugin: Plugin): ByteArray = expectMyCert(plugin, 1)
    }
}
