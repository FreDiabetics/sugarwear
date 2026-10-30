package app.aapswear.g7watch

import android.annotation.SuppressLint
import android.content.Context
import app.aapswear.g7.G7Sensor
import java.util.Locale

/** Only explicit addressless setup without an old authenticated session may rediscover exclusions. */
internal fun allowsRetiredG7CandidateForSetup(
    sensor: G7Sensor,
    hasSharedKey: Boolean,
    sharedKeyAddress: String?,
): Boolean = sensor.deviceAddress.isNullOrBlank() && !hasSharedKey && sharedKeyAddress.isNullOrBlank()

/** Address deny-list only: no old session, code, key or reconnect task is retained. */
internal class G7RetiredSensors(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences("g7_retired_addresses", Context.MODE_PRIVATE)

    fun contains(address: String?): Boolean =
        address != null && address.uppercase(Locale.ROOT) in preferences.getStringSet("addresses", emptySet()).orEmpty()

    fun excludesDiscovery(address: String?, freshSetup: Boolean): Boolean = contains(address) && !freshSetup

    /** Call only after application-level authentication in the current collector generation. */
    @SuppressLint("UseKtx")
    fun reactivateAuthenticated(address: String) =
        synchronized(lock) {
            val retired = preferences.getStringSet("addresses", emptySet()).orEmpty() - address.uppercase(Locale.ROOT)
            check(preferences.edit().putStringSet("addresses", retired).commit()) { "G7 exclusion list could not be persisted" }
        }

    @SuppressLint("UseKtx") // KTX edit discards commit's result; detachment must fail if persistence fails.
    fun retire(addresses: Set<String>) =
        synchronized(lock) {
            val retired = preferences.getStringSet("addresses", emptySet()).orEmpty() + addresses.map { it.uppercase(Locale.ROOT) }
            check(preferences.edit().putStringSet("addresses", retired).commit()) { "G7 exclusion list could not be persisted" }
        }

    private companion object {
        val lock = Any()
    }
}
