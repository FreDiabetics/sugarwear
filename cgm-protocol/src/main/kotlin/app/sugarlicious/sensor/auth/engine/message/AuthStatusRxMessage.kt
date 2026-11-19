package app.sugarlicious.sensor.auth.engine.message

import java.nio.ByteBuffer

/** JamOrHam / xDrip libkeks auth status, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class AuthStatusRxMessage(
    packet: ByteArray,
) : BaseMessage() {
    @JvmField
    var authenticated = 0

    @JvmField
    var bonded = 0

    init {
        if (packet.size >= MINIMUM_BYTES && packet[0].toInt() == opcode) {
            val buffer = ByteBuffer.wrap(packet)
            data = buffer
            authenticated = buffer.get(1).toInt()
            bonded = buffer.get(2).toInt()
        }
    }

    val isAuthenticated: Boolean get() = authenticated == 1

    val isBonded: Boolean get() = bonded == 1

    fun needsRefresh(): Boolean = bonded == REFRESH_STATUS

    companion object {
        const val opcode = 0x05
        private const val MINIMUM_BYTES = 3
        private const val REFRESH_STATUS = 3
    }
}
