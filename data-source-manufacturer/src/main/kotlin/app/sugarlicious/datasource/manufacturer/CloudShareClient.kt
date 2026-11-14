package app.sugarlicious.datasource.manufacturer

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.TherapyDisplayState
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class ShareCredentials(
    val account: String,
    val password: String,
    val region: String,
)

class ShareResponse(
    val status: Int,
    val body: String,
)

fun interface ShareTransport {
    fun post(path: String, query: Map<String, String>, body: String): ShareResponse
}

enum class ShareFailureKind { AUTHENTICATION, NETWORK, RESPONSE }

class ShareFailure(
    val kind: ShareFailureKind,
) : IOException("Dexcom Share: ${kind.name}")

/** Read-only Share client. Session and credentials never enter canonical data or diagnostics. */
class CloudShareClient(
    private val credentials: ShareCredentials,
    private val transport: ShareTransport = ShareHttpsTransport(credentials.region),
    initialMeasurementEpochMs: Long? = null,
) {
    private var session: String? = null
    private var lastMeasurementEpochMs: Long? = initialMeasurementEpochMs
    var lastReadDiagnostics: String = "Noch kein Abruf"
        private set

    @Synchronized fun read(now: Long): TherapyDisplayState? {
        repeat(2) { attempt ->
            val current = session ?: authenticate().also { session = it }
            val minutes = lastMeasurementEpochMs?.let { ((now - it).coerceAtLeast(0L) / 60_000L + 2L).coerceIn(5L, 1440L) } ?: 1440L
            val maxCount = if (minutes == 1440L) 1440L else (((minutes + 4L) / 5L) * 4L).coerceIn(4L, 1440L)
            val response = transport.post("Publisher/ReadPublisherLatestGlucoseValues", mapOf("sessionId" to current, "minutes" to minutes.toString(), "maxCount" to maxCount.toString()), "{}")
            val parsed = parse(response.body)
            val code = (parsed as? JSONObject)?.optString("Code").orEmpty()
            if (response.status in setOf(401, 403) || code in setOf("SessionIdNotFound", "SessionNotValid", "SessionNotFound")) {
                session = null
                if (attempt == 0) return@repeat
                throw ShareFailure(ShareFailureKind.AUTHENTICATION)
            }
            if (response.status !in 200..299) throw ShareFailure(ShareFailureKind.NETWORK)
            val items = parsed as? JSONArray ?: throw ShareFailure(ShareFailureKind.RESPONSE)
            if (items.length() > 2000) throw ShareFailure(ShareFailureKind.RESPONSE)
            val readings =
                (0 until items.length()).mapNotNull { index ->
                    val item = items.optJSONObject(index) ?: return@mapNotNull null
                    val date =
                        Regex("/?Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/?")
                            .matchEntire(item.optString("WT"))
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull() ?: return@mapNotNull null
                    val trend =
                        when (val raw = item.opt("Trend")?.toString()) {
                            "1" -> "DoubleUp"
                            "2" -> "SingleUp"
                            "3" -> "FortyFiveUp"
                            "4" -> "Flat"
                            "5" -> "FortyFiveDown"
                            "6" -> "SingleDown"
                            "7" -> "DoubleDown"
                            else -> raw
                        }
                    BroadcastGlucoseReading(item.optDouble("Value", Double.NaN), date, manufacturerTrend(trend))
                }
            val result = manufacturerBatch(readings, DataSourceId.DEXCOM_SHARE, "DEXCOM_SHARE_V1", now)
            lastReadDiagnostics = "records=${items.length()}; parsed=${readings.size}; accepted=${result?.glucoseHistory?.size ?: 0}; latestAgeMinutes=${readings.minOfOrNull { (now - it.timestamp) / 60_000L } ?: -1}"
            result?.glucose?.measuredAtEpochMs?.let { latest ->
                lastMeasurementEpochMs = maxOf(lastMeasurementEpochMs ?: Long.MIN_VALUE, latest)
            }
            return result
        }
        throw ShareFailure(ShareFailureKind.AUTHENTICATION)
    }

    private fun authenticate(): String {
        if (credentials.account.isBlank() || credentials.password.isBlank()) throw ShareFailure(ShareFailureKind.AUTHENTICATION)
        val authBody = JSONObject().put("accountName", credentials.account).put("password", credentials.password).put("applicationId", APPLICATION_ID)
        val auth = transport.post("General/AuthenticatePublisherAccount", emptyMap(), authBody.toString())
        val payload = parse(auth.body)
        val accountId =
            when (payload) {
                is String -> payload
                is JSONObject -> payload.optString("accountId")
                else -> null
            }
        if (auth.status !in 200..299 || accountId.isNullOrBlank() || accountId == ZERO_UUID) throw ShareFailure(ShareFailureKind.AUTHENTICATION)
        val login =
            transport.post(
                "General/LoginPublisherAccountById",
                emptyMap(),
                JSONObject()
                    .put("accountId", accountId)
                    .put("password", credentials.password)
                    .put("applicationId", APPLICATION_ID)
                    .toString(),
            )
        val id = parse(login.body) as? String
        if (login.status !in 200..299 || id.isNullOrBlank() || id == ZERO_UUID) throw ShareFailure(ShareFailureKind.AUTHENTICATION)
        return id
    }

    private fun parse(value: String): Any? = runCatching { JSONTokener(value).nextValue() }.getOrNull()

    private companion object {
        const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"
        const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
    }
}

/** Only fixed Manufacturer HTTPS origins are allowed; redirects never receive account credentials. */
class ShareHttpsTransport(
    region: String,
) : ShareTransport {
    private val origin = if (region == "us") "https://share2.dexcom.com" else "https://shareous1.dexcom.com"

    override fun post(path: String, query: Map<String, String>, body: String): ShareResponse {
        require(path in setOf("General/AuthenticatePublisherAccount", "General/LoginPublisherAccountById", "Publisher/ReadPublisherLatestGlucoseValues"))
        val parameters = query.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val connection = URI.create("$origin/ShareWebServices/Services/$path" + if (parameters.isEmpty()) "" else "?$parameters").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes =
                input?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 2_000_000) {
                        val count = stream.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: byteArrayOf()
            if (bytes.size > 2_000_000) throw ShareFailure(ShareFailureKind.RESPONSE)
            return ShareResponse(status, bytes.toString(Charsets.UTF_8))
        } catch (failure: ShareFailure) {
            throw failure
        } catch (_: IOException) {
            throw ShareFailure(ShareFailureKind.NETWORK)
        } finally {
            connection.disconnect()
        }
    }
}
