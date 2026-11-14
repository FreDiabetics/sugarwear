package app.sugarlicious.sensor.auth.engine.util

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale

/** JamOrHam / xDrip libkeks utility methods, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Util private constructor() {
    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"
        private const val RANDOM_KEY_BYTES = 16

        @JvmStatic
        fun bytesToHex(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val chars = CharArray(bytes.size * 2)
            for (index in bytes.indices) {
                val value = bytes[index].toInt() and 0xff
                chars[index * 2] = HEX_DIGITS[value ushr 4]
                chars[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
            }
            return String(chars)
        }

        @JvmStatic
        fun arrayAppend(existing: ByteArray, addon: ByteArray): ByteArray =
            existing.copyOf(existing.size + addon.size).also {
                System.arraycopy(addon, 0, it, existing.size, addon.size)
            }

        @JvmStatic
        fun arrayReduce(existing: ByteArray?, length: Int): ByteArray? {
            if (existing == null) return null
            return ByteArray(length).also { System.arraycopy(existing, 0, it, 0, length) }
        }

        @JvmStatic
        fun tolerantHexStringToByteArray(text: String?): ByteArray? =
            hexStringToByteArray(text!!.uppercase(Locale.getDefault()).filter { it in 'A'..'F' || it in '0'..'9' })

        /**
         * Compatibility codec, not standard hex: "12" is 0x21.
         * Preserve Java trim (only code units <= space) and Character.digit arithmetic,
         * including legacy invalid-digit results. Empty, odd-length and null inputs return null.
         */
        @JvmStatic
        fun hexStringToByteArray(text: String?): ByteArray? {
            val normalized = text?.uppercase(Locale.getDefault())?.trim { it <= ' ' } ?: return null
            if (normalized.isEmpty() || normalized.length % 2 != 0) return null
            return ByteArray(normalized.length / 2) { index ->
                val low = Character.digit(normalized[index * 2], 16)
                val high = Character.digit(normalized[index * 2 + 1], 16)
                ((high shl 4) + low).toByte()
            }
        }

        @JvmStatic
        fun intToByteArray(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

        @JvmStatic
        fun getRandomKey(): ByteArray = ByteArray(RANDOM_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
