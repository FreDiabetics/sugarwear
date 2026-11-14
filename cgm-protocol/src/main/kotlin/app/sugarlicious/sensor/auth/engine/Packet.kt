package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.util.ByteArrayHashMap
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.nio.ByteBuffer

/** JamOrHam / xDrip libkeks packet serialization, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Packet(
    @JvmField var hash: BigInteger,
    @JvmField var publicKeyPoint1: ECPoint,
    @JvmField var publicKeyPoint2: ECPoint,
) {
    constructor(buffers: ByteArrayHashMap) : this(
        BigIntegers.fromUnsignedByteArray(buffers.mget(HBYTES1_ID)),
        JECPoint.pointFromBytes(buffers.mget(POINT1X_ID), buffers.mget(POINT1Y_ID)),
        JECPoint.pointFromBytes(buffers.mget(POINT2X_ID), buffers.mget(POINT2Y_ID)),
    )

    fun getHash(): BigInteger = hash

    fun getPublicKeyPoint1(): ECPoint = publicKeyPoint1

    fun getPublicKeyPoint2(): ECPoint = publicKeyPoint2

    fun output(): ByteArray {
        val buffer = ByteBuffer.allocate(Curve.PACKET_SIZE)
        buffer.put(JECPoint(publicKeyPoint1).toBytes())
        buffer.put(JECPoint(publicKeyPoint2).toBytes())
        buffer.put(BigIntegers.asUnsignedByteArray(Curve.FIELD_SIZE, hash))
        val bytes = buffer.array()
        check(bytes.size == Curve.PACKET_SIZE) { "Invalid size" }
        return bytes
    }

    companion object {
        const val POINT1X_ID = 28082
        const val POINT1Y_ID = 37603
        const val POINT2X_ID = 54247
        const val POINT2Y_ID = 40255
        const val HBYTES1_ID = 65535
        private val ids = intArrayOf(POINT1X_ID, POINT1Y_ID, POINT2X_ID, POINT2Y_ID, HBYTES1_ID)

        @JvmStatic
        fun parse(packet: ByteArray): Packet? {
            if (packet.size < Curve.PACKET_SIZE) return null
            val buffers = ByteArrayHashMap()
            val input = ByteBuffer.wrap(packet)
            for (id in ids) input.get(buffers.mget(id))
            return Packet(buffers)
        }
    }
}
