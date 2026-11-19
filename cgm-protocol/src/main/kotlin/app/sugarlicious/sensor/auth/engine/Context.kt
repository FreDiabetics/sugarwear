package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.util.Util
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger

/** JamOrHam / xDrip libkeks session context, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Context {
    @Volatile @JvmField
    var keyA: KeyPair? = null

    // Preserve the public legacy JVM field spelling used by callers and reference fixtures.
    @Suppress("VariableNaming")
    @Volatile
    @JvmField
    var KeyB: KeyPair? = null

    @Volatile @JvmField
    var password: String? = null

    @Volatile @JvmField
    var passwordBytes: ByteArray? = null

    @Volatile @JvmField
    var alice: ByteArray? = null

    @Volatile @JvmField
    var bob: ByteArray? = null

    @Volatile @JvmField
    var challenge: ByteArray? = null

    @Volatile @JvmField
    var savedKey: ByteArray? = null

    @Volatile @JvmField
    var packet: Array<Packet?> = arrayOfNulls(4)

    @Volatile @JvmField
    var sequence = 0

    @Volatile var partA: ByteArray? = null

    @Volatile var partB: ByteArray? = null

    @Volatile var partC: ByteArray? = null

    fun validateParts(): Boolean =
        partA != null &&
            partB != null &&
            partC != null &&
            partA!!.size > 100 &&
            partB!!.size > 100 &&
            partC!!.size > 100

    fun reset() {
        savedKey = null
        packet.fill(null)
    }

    fun resetIfNotReady() {
        sequence = 0
        if (savedKey == null && getRound3Packet() == null) reset()
    }

    fun getPasswordBytes(): ByteArray {
        val current = checkNotNull(password) { "G7 authentication password is missing" }
        if (passwordBytes == null) {
            passwordBytes = current.toByteArray(Charsets.UTF_8)
            if (current.length == 6) passwordBytes = Util.arrayAppend(Config.Get.PREFIX.bytes, passwordBytes!!)
        }
        return passwordBytes!!
    }

    fun getPasswordBigInteger(): BigInteger = BigIntegers.fromUnsignedByteArray(getPasswordBytes())

    fun getRound1Packet(): Packet? = packet[1]

    fun getRound2Packet(): Packet? = packet[2]

    fun getRound3Packet(): Packet? = packet[3]
}
