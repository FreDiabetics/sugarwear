package app.aapswear.g7watch

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import app.aapswear.g7.G7DefaultGKey
import app.aapswear.g7.G7GKeyParts
import app.aapswear.g7.G7SetupPayload
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class StoredG7Credentials(
    override val pairingCode: String,
    override val sensorSerial: String?,
    override val gtin: String?,
    val gKey: G7GKeyParts,
    val sharedKey: ByteArray?,
    val sharedKeyAddress: String?,
) : G7CredentialStore.StoredCredentials

/** Keeps pairing and session secrets encrypted by a non-exportable Android Keystore key. */
internal class G7CredentialStore(
    context: Context,
) {
    internal interface StoredCredentials {
        val pairingCode: String
        val sensorSerial: String?
        val gtin: String?
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveSetup(
        payload: G7SetupPayload,
        gKey: G7GKeyParts = G7DefaultGKey.parts,
    ) {
        require(gKey.isComplete())
        preferences.edit {
            putString(KEY_PAIRING_CODE, encrypt(payload.pairingCode.encodeToByteArray()))
            putString(KEY_SENSOR_SERIAL, payload.sensorSerial)
            putString(KEY_GTIN, payload.gtin)
            putString(KEY_GKEY_1, encrypt(gKey.certificateAuthority))
            putString(KEY_GKEY_2, encrypt(gKey.certificate))
            putString(KEY_GKEY_3, encrypt(gKey.privateKey))
            remove(KEY_SHARED_KEY)
            remove(KEY_SHARED_ADDRESS)
        }
    }

    fun read(): StoredG7Credentials? {
        val pairingCode = preferences.getString(KEY_PAIRING_CODE, null)?.let(::decrypt)?.decodeToString() ?: return null
        val gKey =
            G7GKeyParts(
                preferences.getString(KEY_GKEY_1, null)?.let(::decrypt) ?: G7DefaultGKey.parts.certificateAuthority,
                preferences.getString(KEY_GKEY_2, null)?.let(::decrypt) ?: G7DefaultGKey.parts.certificate,
                preferences.getString(KEY_GKEY_3, null)?.let(::decrypt) ?: G7DefaultGKey.parts.privateKey,
            )
        if (!gKey.isComplete()) return null
        return StoredG7Credentials(
            pairingCode = pairingCode,
            sensorSerial = preferences.getString(KEY_SENSOR_SERIAL, null),
            gtin = preferences.getString(KEY_GTIN, null),
            gKey = gKey,
            sharedKey = preferences.getString(KEY_SHARED_KEY, null)?.let(::decrypt)?.takeIf { it.size == 16 },
            sharedKeyAddress = preferences.getString(KEY_SHARED_ADDRESS, null),
        )
    }

    fun saveSharedKey(
        address: String,
        key: ByteArray,
    ) {
        require(key.size == 16)
        preferences.edit {
            putString(KEY_SHARED_KEY, encrypt(key))
            putString(KEY_SHARED_ADDRESS, address)
        }
    }

    fun clearSessionKey() {
        preferences.edit {
            remove(KEY_SHARED_KEY)
            remove(KEY_SHARED_ADDRESS)
        }
    }

    // Keep the Boolean disk-commit result so migration cannot acknowledge an unwritten key.
    @Suppress("UseKtx")
    fun commitPendingWrites(): Boolean = preferences.edit().commit()

    fun clearAll() {
        preferences.edit { clear() }
    }

    private fun encrypt(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = cipher.iv + cipher.doFinal(value)
        return Base64.getEncoder().encodeToString(encoded)
    }

    private fun decrypt(value: String): ByteArray? =
        runCatching {
            val encoded = Base64.getDecoder().decode(value)
            require(encoded.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encoded.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(encoded.copyOfRange(IV_BYTES, encoded.size))
        }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS = "g7_credentials"
        const val KEY_ALIAS = "sugarlicious_g7_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_SENSOR_SERIAL = "sensor_serial"
        const val KEY_GTIN = "gtin"
        const val KEY_GKEY_1 = "gkey_1"
        const val KEY_GKEY_2 = "gkey_2"
        const val KEY_GKEY_3 = "gkey_3"
        const val KEY_SHARED_KEY = "shared_key"
        const val KEY_SHARED_ADDRESS = "shared_address"
    }
}
