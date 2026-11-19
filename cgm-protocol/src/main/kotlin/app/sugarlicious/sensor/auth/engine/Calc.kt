package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.crypto.Digest
import app.sugarlicious.sensor.auth.crypto.SHA256
import app.sugarlicious.sensor.auth.engine.util.AESwrapper
import app.sugarlicious.sensor.auth.engine.util.Util
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * JamOrHam / xDrip libkeks J-PAKE calculations, translated without changing the formulas.
 * See LICENSES/xDrip-libkeks.md and https://ia.cr/2010/190.
 */
class Calc private constructor() {
    class ZKP {
        private val exponent = Curve.getExponent()
        private val g: ECPoint
        private val keyPair: KeyPair
        private val party: ByteArray?
        private var gv: ECPoint? = null

        constructor(g: ECPoint, keyPair: KeyPair, party: ByteArray?) {
            this.g = g
            this.keyPair = keyPair
            this.party = party
        }

        internal fun getGv(): ECPoint {
            if (gv == null) gv = g.multiply(exponent).normalize()
            return gv!!
        }

        fun getProof(): BigInteger =
            exponent
                .subtract(
                    getZeroKnowledgeHash(g, getGv(), keyPair.getPublicKey()!!, party)
                        .multiply(keyPair.getPrivateKey()!!),
                ).mod(Curve.Q)
    }

    companion object {
        @JvmStatic
        fun getRound12Packet(context: Context, part2: Boolean): Packet {
            val key = (if (part2) context.KeyB else context.keyA)!!
            val proof = ZKP(Curve.G, key, context.alice)
            return Packet(proof.getProof(), key.getPublicKey()!!, proof.getGv())
        }

        @JvmStatic fun getRound1Packet(context: Context): Packet = getRound12Packet(context, false)

        @JvmStatic fun getRound2Packet(context: Context): Packet = getRound12Packet(context, true)

        @JvmStatic
        fun validateRound1Packet(packet: Packet?, party: ByteArray?): Boolean =
            packet != null &&
                validateZeroKnowledgeProof(Curve.G, packet.publicKeyPoint1, packet.publicKeyPoint2, packet.hash, party)

        @JvmStatic
        fun validateRound1Packet(context: Context): Boolean = validateRound1Packet(context.getRound1Packet(), context.bob)

        @JvmStatic
        fun validateRound2Packet(context: Context): Boolean = validateRound1Packet(context.getRound2Packet(), context.bob)

        @JvmStatic
        fun getRound3Packet(context: Context): Packet {
            val packet1 = context.getRound1Packet()!!
            val packet2 = context.getRound2Packet()!!
            val x1 = context.keyA!!.getPublicKey()!!
            val x2 = context.KeyB!!.getPrivateKey()!!
            val x3 = packet1.getPublicKeyPoint1()
            val x4 = packet2.getPublicKeyPoint1()
            val secret = context.getPasswordBigInteger()
            val x2s = x2.multiply(secret).mod(Curve.Q)
            val x134 = x1.add(x3).add(x4).normalize()
            val a = x134.multiply(x2s).normalize()
            val proof = ZKP(x134, KeyPair(x2s, a), context.alice)
            return Packet(proof.getProof(), a, proof.getGv())
        }

        @JvmStatic
        fun validateRound3Packet(context: Context): Boolean {
            val packet = context.getRound3Packet() ?: return false
            val x1 = context.keyA!!.getPublicKey()!!
            val x2 = context.KeyB!!.getPublicKey()!!
            val x3 = context.getRound1Packet()!!.getPublicKeyPoint1()
            val g = x1.add(x2).add(x3).normalize()
            return validateZeroKnowledgeProof(g, packet.getPublicKeyPoint1(), packet.getPublicKeyPoint2(), packet.getHash(), context.bob)
        }

        @JvmStatic
        fun getSharedKey(context: Context): ByteArray? {
            val packet = context.getRound3Packet() ?: return null
            val point1 = packet.getPublicKeyPoint1()
            val x2 = context.KeyB!!.getPrivateKey()!!
            val x4 = context.getRound2Packet()!!.getPublicKeyPoint1()
            val secret = context.getPasswordBigInteger()
            val key = point1.subtract(x4.multiply(x2.multiply(secret).mod(Curve.Q))).multiply(x2).normalize()
            return SHA256.hash(key.xCoord.encoded)
        }

        @JvmStatic fun getShortSharedKey(context: Context): ByteArray? = Util.arrayReduce(getSharedKey(context), 16)

        @JvmStatic
        fun calculateHash(context: Context): ByteArray? {
            val data = context.challenge
            val key = context.savedKey ?: getShortSharedKey(context) ?: return null
            var buffer = ByteBuffer.allocate(16)
            buffer.put(data)
            buffer.put(data)
            val encrypted = AESwrapper(key).aes(buffer.array())
            buffer = ByteBuffer.allocate(8)
            buffer.put(encrypted, 0, 8)
            return buffer.array()
        }

        @JvmStatic
        fun validateZeroKnowledgeProof(g: ECPoint, publicKey: ECPoint, gv: ECPoint, proof: BigInteger, party: ByteArray?): Boolean {
            val hash = getZeroKnowledgeHash(g, gv, publicKey, party)
            return g.multiply(proof).add(publicKey.multiply(hash)).normalize() == gv
        }

        @JvmStatic
        fun getZeroKnowledgeHash(g: ECPoint, gv: ECPoint, gx: ECPoint, party: ByteArray?): BigInteger {
            val result = ByteArray(32)
            val digest = Digest(result)
            updateDigestIncludingSize(digest, g.getEncoded(false))
            updateDigestIncludingSize(digest, gv.getEncoded(false))
            updateDigestIncludingSize(digest, gx.getEncoded(false))
            updateDigestIncludingSize(digest, party!!)
            digest.doFinal()
            return BigIntegers.fromUnsignedByteArray(result).mod(Curve.Q)
        }

        private fun updateDigestIncludingSize(digest: Digest, bytes: ByteArray) {
            digest.update(Util.intToByteArray(bytes.size))
            digest.update(bytes)
        }

        @JvmStatic
        fun challenger(bytes: ByteArray?, challenge: ByteArray): ByteArray? {
            val toSign = ByteArray(16)
            System.arraycopy(challenge, 2, toSign, 0, toSign.size)
            return DSAChallenger(KeyPair.fromBytes(bytes)).response(toSign)
        }
    }
}
