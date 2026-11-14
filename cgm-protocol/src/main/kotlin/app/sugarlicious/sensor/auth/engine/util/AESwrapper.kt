package app.sugarlicious.sensor.auth.engine.util

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** JamOrHam / xDrip libkeks AES adapter, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class AESwrapper(
    private val key: ByteArray?,
) {
    fun aes(plaintext: ByteArray): ByteArray? =
        try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secret = SecretKeySpec(key, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secret)
            cipher.doFinal(plaintext, 0, plaintext.size)
        } catch (failure: Exception) {
            Log.cryptoFailure("AES_ENCRYPT", failure)
            null
        }
}
