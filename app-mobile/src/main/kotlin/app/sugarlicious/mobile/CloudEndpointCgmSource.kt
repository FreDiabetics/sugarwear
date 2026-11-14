package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.datasource.external.ExternalCgmAdapters
import app.sugarlicious.model.DataSourceId
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

/** Shared authenticated transport; redirects never forward credentials to a different host. */
internal object CloudEndpointHttp {
    fun get(configuration: CloudEndpointConfiguration, secret: String?, endpoint: String): String {
        require(configuration.isValid())
        val suffix =
            if (configuration.authMode == CloudEndpointAuthMode.ACCESS_TOKEN && !secret.isNullOrBlank()) {
                (if ('?' in endpoint) "&" else "?") + "token=" + URLEncoder.encode(secret, "UTF-8")
            } else {
                ""
            }
        val connection = URI(configuration.normalizedBaseUrl + endpoint + suffix).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (configuration.authMode == CloudEndpointAuthMode.API_SECRET && !secret.isNullOrBlank()) {
                val digest = MessageDigest.getInstance("SHA-1").digest(secret.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                connection.setRequestProperty("api-secret", digest)
            }
            val status = connection.responseCode
            check(status in 200..299) { "HTTP $status" }
            val bytes =
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 2_000_000) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            check(bytes.size <= 2_000_000) { "Antwort zu groß" }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}

internal class CloudEndpointCgmSource(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var signature: String? = null

    fun refresh() {
        val source = MobileSourceConfigurationStore.read(context)
        val (config, secret) = CloudEndpointConfigurationStore.access(context)
        val cgmEnabled = MobileGlucoseSource.NIGHTSCOUT in source.enabledGlucoseSources
        val enabled = cgmEnabled || config.enabled
        val next = "$cgmEnabled:${config.enabled}:${config.normalizedBaseUrl}:${config.authMode}:${source.cloudEndpointRevision}"
        if (signature == next) return
        close()
        signature = next
        if (!enabled) return
        if (!config.isValid()) {
            status("HTTPS-Adresse fehlt")
            return
        }
        val identity = MessageDigest.getInstance("SHA-256").digest("${config.normalizedBaseUrl}:${config.authMode}:${secret.orEmpty()}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        job =
            scope.launch(Dispatchers.IO) {
                MobileCanonicalStateCoordinator.activateRemoteAccount(context, DataSourceId.NIGHTSCOUT, identity)
                val checkedGaps = mutableMapOf<Pair<Long, Long?>, Long>()
                var failures = 0
                while (isActive) {
                    var waitMs = if (cgmEnabled) 300_000L else 900_000L
                    try {
                        val now = System.currentTimeMillis()
                        if (config.enabled) CloudEndpointTreatmentSync.syncIfDue(context, now)
                        if (cgmEnabled) {
                            val cached = MobileSourceInputStore(context).read()[DataSourceId.NIGHTSCOUT]
                            val times =
                                cached
                                    ?.glucoseHistory
                                    .orEmpty()
                                    .map { it.measuredAtEpochMs }
                                    .filter { it in localDayStartEpochMs(now)..now }
                                    .distinct()
                                    .sorted()

                            suspend fun readWindow(since: Long, before: Long? = null): app.sugarlicious.model.TherapyDisplayState? {
                                val upper = "&find%5Bdate%5D%5B%24lt%5D=${before ?: (now + 1)}"
                                val payload = CloudEndpointHttp.get(config, secret, "/api/v1/entries/sgv.json?find%5Bdate%5D%5B%24gt%5D=$since$upper&count=1440")
                                ensureActive()
                                val currentConfig = MobileSourceConfigurationStore.read(context)
                                if (currentConfig.cloudEndpointRevision != source.cloudEndpointRevision || MobileGlucoseSource.NIGHTSCOUT !in currentConfig.enabledGlucoseSources) throw CancellationException("Source changed")
                                val array =
                                    kotlinx.serialization.json.Json
                                        .parseToJsonElement(payload) as? kotlinx.serialization.json.JsonArray ?: error("Ungültige Antwort")
                                val receivedAt = System.currentTimeMillis()
                                val received = ExternalCgmAdapters.cloudEndpoint(payload, receivedAt)
                                check(array.isEmpty() || received != null) { "Keine gültigen Messwerte" }
                                if (received != null) MobileCanonicalStateCoordinator.savePhoneInput(context, received.copy(sourceVersion = identity), receivedAt)
                                return received
                            }
                            checkedGaps.entries.removeAll { now - it.value >= 1_800_000L }
                            val windows = cloudEndpointDailyWindows(times, now)
                            var state: app.sugarlicious.model.TherapyDisplayState? = null
                            for (window in windows) {
                                val isHistoricalGap = window.second != null
                                val alreadyChecked = isHistoricalGap && window in checkedGaps
                                if (alreadyChecked) continue
                                val received = readWindow(window.first, window.second)
                                if (received != null) state = received
                                if (isHistoricalGap) {
                                    checkedGaps[window] = now
                                }
                            }
                            ensureActive()
                            failures = 0
                            status(if (state?.glucose?.measuredAtEpochMs?.let { now - it <= 900_000L } == true) "Empfang aktiv" else "Keine neueren Werte")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failures++
                        status("Nightscout-Abruf fehlgeschlagen – Adresse und Zugang prüfen")
                        waitMs = (60_000L * (1L shl failures.coerceAtMost(4))).coerceAtMost(900_000L)
                    }
                    delay(waitMs)
                }
            }
    }

    private fun status(value: String) = context.getSharedPreferences("source_diagnostics", Context.MODE_PRIVATE).edit { putString("nightscout_cgm", value) }

    fun close() {
        job?.cancel()
        job = null
        signature = null
    }
}

/** Exclusive API bounds: midnight itself is included, cached endpoints are not fetched again. */
internal fun cloudEndpointDailyWindows(times: List<Long>, now: Long): List<Pair<Long, Long?>> {
    val start = localDayStartEpochMs(now)
    val today = times.filter { it in start..now }.distinct().sorted()
    if (today.isEmpty()) return listOf((start - 1) to null)
    val intervals =
        today
            .zipWithNext()
            .map { (a, b) -> b - a }
            .filter { it in 30_000L..330_000L }
            .sorted()
    val cadence = intervals.getOrNull((intervals.size - 1).coerceAtLeast(0) / 2)?.coerceIn(60_000L, 300_000L) ?: 300_000L
    return buildList {
        if (today.first() > start) add((start - 1) to today.first())
        today.zipWithNext().forEach { (a, b) ->
            if (b - a > cadence * 3 / 2) add(a to b)
        }
        add(today.last() to null)
    }
}
