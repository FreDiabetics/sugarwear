package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.util.Util
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers

/** JamOrHam / xDrip libkeks coordinate codec, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class JECPoint(
    val point: ECPoint?,
) {
    fun toBytes(): ByteArray {
        val current = point!!
        return Util.arrayAppend(current.xCoord.encoded, current.yCoord.encoded)
    }

    companion object {
        @JvmStatic
        fun pointFromBytes(xBytes: ByteArray?, yBytes: ByteArray?): ECPoint =
            Curve.curve.createPoint(BigIntegers.fromUnsignedByteArray(xBytes), BigIntegers.fromUnsignedByteArray(yBytes))
    }
}
