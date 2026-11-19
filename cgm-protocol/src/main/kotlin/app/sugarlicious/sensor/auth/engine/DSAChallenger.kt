package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.util.Log
import app.sugarlicious.sensor.auth.engine.util.Util
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.jcajce.provider.asymmetric.ec.SignatureSpi
import org.bouncycastle.util.BigIntegers
import java.security.PrivateKey
import java.security.SignatureException

/** JamOrHam / xDrip libkeks signing adapter; original provider preserved. See LICENSES/xDrip-libkeks.md. */
class DSAChallenger(
    private val key: PrivateKey?,
) : SignatureSpi.ecDSA256() {
    fun response(challenge: ByteArray): ByteArray? =
        try {
            engineInitSign(key)
            engineUpdate(challenge)
            val sequence = ASN1InputStream(engineSign()).use { ASN1Sequence.getInstance(it.readObject()) }
            sequenceToBytes(sequence, 2)
        } catch (failure: Exception) {
            Log.cryptoFailure("CHALLENGE_SIGN", failure)
            null
        }

    @Throws(SignatureException::class)
    fun engineUpdate(bytes: ByteArray) {
        digest.update(bytes, 0, bytes.size)
    }

    private fun sequenceToBytes(sequence: ASN1Sequence, count: Int): ByteArray? {
        if (sequence.size() < count) return null
        var reply = byteArrayOf()
        for (index in 0 until count) {
            val item = sequence.getObjectAt(index) ?: return null
            reply = Util.arrayAppend(reply, BigIntegers.asUnsignedByteArray(BYTES_PER_INTEGER, (item as ASN1Integer).positiveValue))
        }
        return reply
    }

    private companion object {
        const val BYTES_PER_INTEGER = 32
    }
}
