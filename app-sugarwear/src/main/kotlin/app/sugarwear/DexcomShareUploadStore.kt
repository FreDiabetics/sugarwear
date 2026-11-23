package app.sugarwear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class DexcomShareRegion(
    val server: String,
) {
    US("https://share2.dexcom.com/ShareWebServices/Services/"),
    INTERNATIONAL("https://shareous1.dexcom.com/ShareWebServices/Services/"),
}

internal data class DexcomShareUploadConfig(
    val account: String,
    val password: String,
    val receiverSerial: String,
    val region: DexcomShareRegion,
    val revision: Long,
)

internal enum class DexcomShareUploadError {
    NONE,
    NETWORK,
    SCHEDULING,
    AUTHENTICATION,
    RECEIVER_NOT_ASSIGNED,
    RECEIVER_MISMATCH,
    MONITORING,
    RATE_LIMIT,
    SERVER,
    ACCOUNT_SERVER,
    ACCOUNT_RESPONSE,
    LOGIN_LIMIT,
    RECEIVER_SERVER,
    RECEIVER_RESPONSE,
    UPLOAD_SERVER,
    INVITATION,
}

internal data class DexcomShareUploadStatus(
    val enabled: Boolean,
    val configured: Boolean,
    val revision: Long,
    val hasReceiver: Boolean,
    val region: DexcomShareRegion,
    val lastConnectionEpochMs: Long,
    val lastUploadEpochMs: Long,
    val error: DexcomShareUploadError,
    val httpStatus: Int?,
)

/** Share secrets use their own Keystore key; they never enter BLE credentials or logs. */
internal class DexcomShareUploadStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun status(): DexcomShareUploadStatus =
        DexcomShareUploadStatus(
            enabled = enabled(),
            revision = revision(),
            configured = preferences.contains(KEY_ACCOUNT) && preferences.contains(KEY_PASSWORD),
            hasReceiver = preferences.contains(KEY_RECEIVER),
            region = region(),
            lastConnectionEpochMs = preferences.getLong(KEY_LAST_CONNECTION, 0L),
            lastUploadEpochMs = preferences.getLong(KEY_LAST_UPLOAD, 0L),
            httpStatus = preferences.getInt(KEY_HTTP_STATUS, 0).takeIf { it in 300..599 },
            error =
                runCatching { DexcomShareUploadError.valueOf(preferences.getString(KEY_ERROR, "NONE") ?: "NONE") }
                    .getOrDefault(DexcomShareUploadError.SERVER),
        )

    fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun region(): DexcomShareRegion =
        runCatching { DexcomShareRegion.valueOf(preferences.getString(KEY_REGION, "INTERNATIONAL") ?: "INTERNATIONAL") }
            .getOrDefault(DexcomShareRegion.INTERNATIONAL)

    /** Saving a different account always revokes upload permission and invalidates in-flight work. */
    fun saveAccount(account: String, password: String, region: DexcomShareRegion) {
        require(account.isNotBlank() && account.length <= 256)
        require(password.isNotBlank() && password.length <= 512)
        val sealedAccount = seal(account.trim())
        val sealedPassword = seal(password)
        synchronized(lock) {
            val previous = readConfig()
            val editor = preferences.edit()
            if (previous == null || previous.account != account.trim() || previous.region != region) {
                editor.remove(KEY_RECEIVER)
                preferences.all.keys
                    .filter { it.startsWith(INVITE_PREFIX) }
                    .forEach { editor.remove(it) }
            }
            check(
                editor
                    .putString(KEY_ACCOUNT, sealedAccount)
                    .putString(KEY_PASSWORD, sealedPassword)
                    .putString(KEY_REGION, region.name)
                    .putBoolean(KEY_ENABLED, false)
                    .putLong(KEY_REVISION, revision() + 1L)
                    .remove(KEY_LAST_UPLOAD)
                    .remove(KEY_LAST_CONNECTION)
                    .remove(KEY_ERROR)
                    .remove(KEY_HTTP_STATUS)
                    .commit(),
            ) { "Dexcom Share account could not be persisted" }
        }
    }

    fun saveReceiver(serial: String, expectedRevision: Long) {
        require(isValidShareReceiver(serial))
        synchronized(lock) {
            if (revision() != expectedRevision) return
            val config = checkNotNull(readConfig())
            if (config.receiverSerial == serial) {
                // Rechecking the same receiver must preserve the last confirmed upload.
                val error = status().error
                if (error in
                    setOf(
                        DexcomShareUploadError.RECEIVER_SERVER,
                        DexcomShareUploadError.RECEIVER_RESPONSE,
                        DexcomShareUploadError.RECEIVER_NOT_ASSIGNED,
                        DexcomShareUploadError.RECEIVER_MISMATCH,
                        DexcomShareUploadError.SERVER,
                    )
                ) {
                    check(
                        preferences
                            .edit()
                            .remove(KEY_ERROR)
                            .remove(KEY_HTTP_STATUS)
                            .commit(),
                    )
                }
                return
            }
            check(
                preferences
                    .edit()
                    .putString(KEY_RECEIVER, seal(serial))
                    .putLong(KEY_REVISION, revision() + 1L)
                    .remove(KEY_LAST_UPLOAD)
                    .remove(KEY_ERROR)
                    .remove(KEY_HTTP_STATUS)
                    .commit(),
            )
        }
    }

    fun enableConfirmedReceiver(config: DexcomShareUploadConfig, serial: String) =
        synchronized(lock) {
            check(revision() == config.revision && !enabled())
            require(isValidShareReceiver(serial))
            val editor = preferences.edit()
            if (config.receiverSerial != serial) editor.remove(KEY_LAST_UPLOAD)
            check(
                editor
                    .putString(KEY_RECEIVER, seal(serial))
                    .putBoolean(KEY_ENABLED, true)
                    .putLong(KEY_REVISION, config.revision + 1L)
                    .remove(KEY_ERROR)
                    .remove(KEY_HTTP_STATUS)
                    .commit(),
            )
        }

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            if (value) require(readConfig()?.receiverSerial?.isNotEmpty() == true) { "Share receiver must be configured first" }
            if (enabled() == value) return
            check(
                preferences
                    .edit()
                    .putBoolean(KEY_ENABLED, value)
                    .putLong(KEY_REVISION, revision() + 1L)
                    .commit(),
            ) {
                "Dexcom Share setting could not be persisted"
            }
        }
    }

    /** Returns null for absent or unreadable secrets. No fallback to an empty credential. */
    fun readConfig(): DexcomShareUploadConfig? =
        synchronized(lock) {
            val account = preferences.getString(KEY_ACCOUNT, null)?.let(::unseal)?.decodeToString() ?: return@synchronized null
            val password = preferences.getString(KEY_PASSWORD, null)?.let(::unseal)?.decodeToString() ?: return@synchronized null
            val serial =
                preferences
                    .getString(KEY_RECEIVER, null)
                    ?.let { encrypted ->
                        unseal(encrypted)?.decodeToString()?.takeIf(::isValidShareReceiver) ?: return@synchronized null
                    }.orEmpty()
            if (account.isBlank() || password.isBlank()) return@synchronized null
            DexcomShareUploadConfig(account, password, serial, region(), revision())
        }

    fun readEnabledConfig(): DexcomShareUploadConfig? =
        synchronized(lock) { if (enabled()) readConfig()?.takeIf { it.receiverSerial.isNotEmpty() } else null }

    fun cursor(config: DexcomShareUploadConfig, sensorId: String, sessionId: String): Long =
        preferences.getLong(cursorKey(config, sensorId, sessionId), 0L)

    fun markVerified(config: DexcomShareUploadConfig, nowEpochMs: Long) =
        synchronized(lock) {
            if (revision() != config.revision) return@synchronized
            val editor = preferences.edit().putLong(KEY_LAST_CONNECTION, nowEpochMs)
            // A successful login must never erase a previous receiver or upload failure.
            if (status().error in setOf(DexcomShareUploadError.AUTHENTICATION, DexcomShareUploadError.ACCOUNT_SERVER, DexcomShareUploadError.ACCOUNT_RESPONSE, DexcomShareUploadError.LOGIN_LIMIT)) {
                editor.remove(KEY_ERROR).remove(KEY_HTTP_STATUS)
            }
            check(editor.commit())
        }

    /** Cursor changes only after the Share server has accepted a measurement batch. */
    fun markAccepted(
        config: DexcomShareUploadConfig,
        sensorId: String,
        sessionId: String,
        lastMeasurementEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean =
        synchronized(lock) {
            if (!enabled() || revision() != config.revision) return@synchronized false
            val key = cursorKey(config, sensorId, sessionId)
            check(
                preferences
                    .edit()
                    .putLong(key, maxOf(preferences.getLong(key, 0L), lastMeasurementEpochMs))
                    .putLong(KEY_LAST_UPLOAD, nowEpochMs)
                    .putLong(KEY_LAST_CONNECTION, nowEpochMs)
                    .putString(KEY_ERROR, "NONE")
                    .remove(KEY_HTTP_STATUS)
                    .commit(),
            ) { "Share upload cursor was not persisted" }
            true
        }

    fun markError(config: DexcomShareUploadConfig, error: DexcomShareUploadError, httpStatus: Int? = null) =
        synchronized(lock) {
            if (revision() != config.revision) return@synchronized
            check(
                preferences
                    .edit()
                    .putString(KEY_ERROR, error.name)
                    .putInt(KEY_HTTP_STATUS, httpStatus ?: 0)
                    .commit(),
            )
        }

    // Only the encrypted contact ID is retained, so a partly completed invite can be resumed.
    fun pendingContact(config: DexcomShareUploadConfig, email: String): String? =
        synchronized(lock) {
            check(revision() == config.revision)
            preferences.getString(invitationKey(config, email), null)?.let { checkNotNull(unseal(it)).decodeToString() }
        }

    fun rememberContact(config: DexcomShareUploadConfig, email: String, contactId: String?) =
        synchronized(lock) {
            check(revision() == config.revision)
            check(preferences.edit().putString(invitationKey(config, email), contactId?.let(::seal)).commit())
        }

    private fun invitationKey(config: DexcomShareUploadConfig, email: String): String {
        val identity = org.json.JSONArray(listOf(config.region.name, config.account, email)).toString()
        return INVITE_PREFIX + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(identity.encodeToByteArray()))
    }

    fun clear() {
        synchronized(lock) {
            val revokedRevision = revision() + 1L
            check(
                preferences
                    .edit()
                    .clear()
                    .putLong(KEY_REVISION, revokedRevision)
                    .commit(),
            ) { "Share account could not be removed" }
            if (java.security.Security.getProvider("AndroidKeyStore") != null) {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun revision() = preferences.getLong(KEY_REVISION, 0L)

    private fun cursorKey(config: DexcomShareUploadConfig, sensorId: String, sessionId: String): String {
        val source = "${config.region}:${config.account.lowercase(java.util.Locale.ROOT)}:${config.receiverSerial}:$sensorId:$sessionId"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.encodeToByteArray())
        return "cursor_" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun seal(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.encodeToByteArray()))
    }

    private fun unseal(value: String): ByteArray? =
        runCatching {
            val bytes = Base64.getDecoder().decode(value)
            require(bytes.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size))
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
        val lock = Any()
        const val PREFERENCES = "sugarwear_dexcom_share"
        const val INVITE_PREFIX = "pending_invitation_"
        const val KEY_ALIAS = "sugarwear_dexcom_share_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_ACCOUNT = "encrypted_account"
        const val KEY_PASSWORD = "encrypted_password"
        const val KEY_RECEIVER = "encrypted_receiver"
        const val KEY_REGION = "region"
        const val KEY_ENABLED = "enabled"
        const val KEY_REVISION = "revision"
        const val KEY_LAST_CONNECTION = "last_connection"
        const val KEY_LAST_UPLOAD = "last_upload"
        const val KEY_ERROR = "error"
        const val KEY_HTTP_STATUS = "http_status"
    }
}

private val shareReceiverPattern = Regex("[A-Za-z0-9_-]{5,40}")

internal fun isValidShareReceiver(serial: String): Boolean = shareReceiverPattern.matches(serial)
