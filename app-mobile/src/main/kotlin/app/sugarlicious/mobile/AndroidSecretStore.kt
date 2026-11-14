package app.sugarlicious.mobile
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidSecretStore(
    private val preferencesName: String,
    private val keyAlias: String,
) {
    private val valueKey = "value"
    private val ivKey = "iv"

    fun save(
        context: Context,
        value: String,
    ) {
        if (value.isBlank()) {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit { clear() }
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit {
            putString(
                valueKey,
                android.util.Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP),
            )
            putString(ivKey, android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
        }
    }

    fun read(context: Context): String? =
        runCatching {
            val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val encrypted = android.util.Base64.decode(preferences.getString(valueKey, null) ?: return null, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(preferences.getString(ivKey, null) ?: return null, android.util.Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(doFinal(encrypted), StandardCharsets.UTF_8)
            }
        }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }
}
