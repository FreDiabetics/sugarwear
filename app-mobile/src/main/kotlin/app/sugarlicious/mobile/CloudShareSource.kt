package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.datasource.manufacturer.*
import app.sugarlicious.model.DataSourceId
import kotlinx.coroutines.*
import org.json.JSONObject

internal object CloudShareCredentialsStore {
    private val secrets = AndroidSecretStore("dexcom_share_secret", "sugarlicious_dexcom_share_v1")

    fun read(context: Context): ShareCredentials? =
        runCatching {
            val stored = JSONObject(secrets.read(context) ?: return null)
            ShareCredentials(stored.getString("account"), stored.getString("password"), stored.optString("region", "ous"))
        }.getOrNull()

    fun save(context: Context, account: String, password: String, region: String) {
        require(account.isNotBlank() && password.isNotBlank() && region in setOf("us", "ous"))
        secrets.save(
            context,
            JSONObject()
                .put("account", account.trim())
                .put("password", password)
                .put("region", region)
                .toString(),
        )
        context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE).edit {
            putString("source.share.region", region)
            putLong("source.share.credentials_revision", System.currentTimeMillis())
        }
        PersistentBridgeService.start(context)
    }
}

/** Owned by the existing foreground bridge; no parallel service or phone BLE collector. */
internal class CloudShareSource(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var signature: String? = null

    fun refresh() {
        val configuration = MobileSourceConfigurationStore.read(context)
        val next = shareJobSignature(configuration)
        if (signature == next) return
        signature = next
        job?.cancel()
        job = null
        if (MobileGlucoseSource.DEXCOM_SHARE !in configuration.enabledGlucoseSources) return
        val credentials =
            CloudShareCredentialsStore.read(context) ?: run {
                status("Zugangsdaten fehlen")
                return
            }
        job =
            scope.launch(Dispatchers.IO) {
                val sourceStore = MobileSourceInputStore(context)
                val identity =
                    java.security.MessageDigest
                        .getInstance("SHA-256")
                        .digest("${credentials.region}:${credentials.account.trim()}".toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                MobileCanonicalStateCoordinator.activateShareAccount(context, identity)
                val cached = sourceStore.read()[DataSourceId.DEXCOM_SHARE]
                val cachedTimes =
                    cached
                        ?.glucoseHistory
                        .orEmpty()
                        .map { it.measuredAtEpochMs }
                        .distinct()
                        .sorted()
                val nowAtStart = System.currentTimeMillis()
                val coversDay = cachedTimes.firstOrNull()?.let { it <= nowAtStart - 86_400_000L + 450_000L } == true && cachedTimes.zipWithNext().none { (a, b) -> b - a > 450_000L }
                val client = CloudShareClient(credentials, initialMeasurementEpochMs = cachedTimes.lastOrNull().takeIf { coversDay })
                var failures = 0
                while (isActive) {
                    var waitMs = 300_000L
                    try {
                        val now = System.currentTimeMillis()
                        val result = client.read(now)
                        context.getSharedPreferences("source_diagnostics", Context.MODE_PRIVATE).edit { putString("dexcom_share_read", client.lastReadDiagnostics) }
                        ensureActive()
                        if (shareJobSignature(MobileSourceConfigurationStore.read(context)) != next) break
                        if (result != null) {
                            MobileCanonicalStateCoordinator.savePhoneInput(context, result.copy(sourceVersion = identity), now)
                            ensureActive()
                            val timestamp = result.glucose!!.measuredAtEpochMs
                            waitMs = (timestamp + 315_000L - System.currentTimeMillis()).coerceIn(30_000L, 300_000L)
                            status("Empfang aktiv")
                        } else {
                            status("Keine aktuellen Share-Werte")
                        }
                        failures = 0
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: ShareFailure) {
                        failures++
                        status(
                            when (failure.kind) {
                                ShareFailureKind.AUTHENTICATION -> "Anmeldung fehlgeschlagen"
                                ShareFailureKind.NETWORK -> "Share nicht erreichbar"
                                ShareFailureKind.RESPONSE -> "Ungültige Share-Antwort"
                            },
                        )
                        waitMs = if (failure.kind == ShareFailureKind.AUTHENTICATION) 900_000L else (60_000L * (1L shl failures.coerceAtMost(4))).coerceAtMost(900_000L)
                    }
                    delay(waitMs)
                }
            }
    }

    private fun shareJobSignature(configuration: MobileSourceConfiguration): String =
        "${MobileGlucoseSource.DEXCOM_SHARE in configuration.enabledGlucoseSources}:${configuration.shareRegion}:${configuration.credentialsRevision}"

    private fun status(value: String) = context.getSharedPreferences("source_diagnostics", Context.MODE_PRIVATE).edit { putString("dexcom_share", value) }

    fun close() {
        job?.cancel()
        job = null
        signature = null
    }
}
