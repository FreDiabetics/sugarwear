package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.util.Log
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi
import org.bouncycastle.math.ec.ECPoint
import java.io.IOException
import java.math.BigInteger
import java.security.PrivateKey
import java.util.concurrent.CancellationException

/** JamOrHam / xDrip libkeks key adapter, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class KeyPair {
    @JvmField val privateKey: BigInteger?

    @JvmField val publicKey: ECPoint?

    constructor(privateKey: BigInteger?, publicKey: ECPoint?) {
        this.privateKey = privateKey
        this.publicKey = publicKey
    }

    @Throws(IOException::class)
    constructor(publicBytes: ByteArray, privateBytes: ByteArray) {
        val factory = KeyFactorySpi.EC()
        publicKey = (factory.generatePublic(SubjectPublicKeyInfo.getInstance(publicBytes)) as BCECPublicKey).q
        privateKey = BigInteger((factory.generatePrivate(PrivateKeyInfo.getInstance(privateBytes)) as BCECPrivateKey).d.toString())
    }

    constructor() {
        try {
            val generator = KeyPairGeneratorSpi.EC()
            generator.initialize(Curve.curveSpec)
            val keys = generator.genKeyPair()
            publicKey = (keys.public as BCECPublicKey).q
            privateKey = (keys.private as BCECPrivateKey).d
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            throw IllegalStateException("G7 EC key generation failed", failure)
        }
    }

    @Throws(IOException::class)
    constructor(bytes: Array<ByteArray>) : this(bytes[0], bytes[1])

    fun getPrivateKey(): BigInteger? = privateKey

    fun getPublicKey(): ECPoint? = publicKey

    companion object {
        @JvmStatic
        fun fromBytes(bytes: Array<ByteArray>?): KeyPair? =
            try {
                KeyPair(bytes!![0], bytes[1])
            } catch (failure: Exception) {
                Log.cryptoFailure("KEY_PAIR_IMPORT", failure)
                null
            }

        @JvmStatic
        fun fromBytes(privateBytes: ByteArray?): PrivateKey? =
            try {
                KeyFactorySpi.EC().generatePrivate(PrivateKeyInfo.getInstance(privateBytes))
            } catch (failure: Exception) {
                Log.cryptoFailure("PRIVATE_KEY_IMPORT", failure)
                null
            }
    }
}
