package app.sugarlicious.sensor.auth.engine

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.SecureRandom

/** JamOrHam / xDrip libkeks curve parameters, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Curve private constructor() {
    companion object {
        private val random = SecureRandom()
        const val name = "secp256r1"

        @JvmField val curveSpec = ECNamedCurveTable.getParameterSpec(name)

        @JvmField val G = curveSpec.g

        @JvmField val curve = curveSpec.curve

        @JvmField val Q = curve.order

        @JvmField val QM1 = Q.subtract(BigInteger.ONE)

        @JvmField val CURVE_BITS = curve.fieldSize

        @JvmField val FIELD_SIZE = (CURVE_BITS + 7) / 8

        @JvmField val PACKET_SIZE = FIELD_SIZE * 5

        @JvmStatic
        fun getExponent(): BigInteger = BigIntegers.createRandomInRange(BigInteger.ONE, QM1, random)
    }
}
