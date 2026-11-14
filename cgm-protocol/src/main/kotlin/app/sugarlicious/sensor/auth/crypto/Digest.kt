package app.sugarlicious.sensor.auth.crypto

/** JamOrHam / xDrip libkeks digest buffer, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Digest(
    private val destination: ByteArray?,
) {
    private var store = byteArrayOf()

    fun update(data: ByteArray) {
        val combined = ByteArray(store.size + data.size)
        System.arraycopy(data, 0, combined, store.size, data.size)
        System.arraycopy(store, 0, combined, 0, store.size)
        store = combined
    }

    fun doFinal() {
        val result = SHA256.hash(store)
        val target = destination!!
        System.arraycopy(result, 0, target, 0, target.size)
    }
}
