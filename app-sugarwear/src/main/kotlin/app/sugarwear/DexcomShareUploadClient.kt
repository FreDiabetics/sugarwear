package app.sugarwear

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Network adapter is injectable for tests; TLS trust and hostname verification remain intact. */
internal interface DexcomShareHttpTransport {
    @Throws(IOException::class)
    fun post(url: String, body: JSONObject? = null): DexcomShareHttpResponse
}

internal data class DexcomShareHttpResponse(val status: Int, val body: String)

internal class DexcomShareHttpsTransport : DexcomShareHttpTransport {
    override fun post(url: String, body: JSONObject?): DexcomShareHttpResponse {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        try {
            connection.outputStream.use { output ->
                output.write((body?.toString() ?: "").toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val limit = CharArray(8_193)
                val count = reader.read(limit)
                if (count < 0) "" else String(limit, 0, count.coerceAtMost(8_192))
            }.orEmpty()
            return DexcomShareHttpResponse(status, content)
        } finally {
            connection.disconnect()
        }
    }
}

internal class DexcomShareProtocolException(
    val issue: DexcomShareUploadError,
    val serverCode: String? = null,
    val recoverable: Boolean = false,
) : IOException(issue.name)

/** Deliberately independent from BLE, alarms, graph providers and canonical glucose storage. */
internal class DexcomShareUploadClient(
    private val transport: DexcomShareHttpTransport = DexcomShareHttpsTransport(),
) {
    private var sessionForAccount: String? = null
    private var sessionForRevision: Long = -1L

    suspend fun testAccount(config: DexcomShareUploadConfig) = withContext(Dispatchers.IO) {
        authenticate(config, force = true)
        Unit
    }

    /** Returns only on confirmed server acceptance. Reauthentication and monitoring recovery are bounded. */
    suspend fun upload(config: DexcomShareUploadConfig, rows: List<DexcomShareEgv>) = withContext(Dispatchers.IO) {
        require(rows.isNotEmpty() && rows.size <= DexcomShareUploadPolicy.MAX_BATCH)
        var session = authenticate(config)
        var refreshed = false
        var started = false
        while (true) {
            try {
                postUpload(config, session, rows)
                return@withContext
            } catch (problem: DexcomShareProtocolException) {
                when {
                    problem.serverCode in setOf("SessionNotValid", "SessionIdNotFound") && !refreshed -> {
                        refreshed = true
                        session = authenticate(config, force = true)
                    }
                    problem.serverCode == "MonitoringSessionNotActive" && !started -> {
                        started = true
                        request(config, "Publisher/StartRemoteMonitoringSession", null, session, config.receiverSerial)
                    }
                    problem.serverCode == "DuplicateEgvPosted" && rows.size == 1 -> return@withContext
                    problem.serverCode == "DuplicateEgvPosted" && rows.size > 1 -> {
                        // A duplicate response for a batch cannot prove which rows were accepted.
                        rows.forEach { postUpload(config, session, listOf(it), acceptDuplicate = true) }
                        return@withContext
                    }
                    else -> throw problem
                }
            }
        }
    }

    private fun postUpload(
        config: DexcomShareUploadConfig,
        session: String,
        rows: List<DexcomShareEgv>,
        acceptDuplicate: Boolean = false,
    ) {
        try {
            request(
                config,
                "Publisher/PostReceiverEgvRecords",
                DexcomShareUploadPolicy.payload(config.receiverSerial, rows),
                session,
            )
        } catch (problem: DexcomShareProtocolException) {
            if (!acceptDuplicate || problem.serverCode != "DuplicateEgvPosted") throw problem
        }
    }

    private fun authenticate(config: DexcomShareUploadConfig, force: Boolean = false): String {
        if (!force && sessionForRevision == config.revision) sessionForAccount?.let { return it }
        sessionForAccount = null
        val account = request(
            config,
            "General/AuthenticatePublisherAccount",
            JSONObject()
                .put("accountName", config.account)
                .put("password", config.password)
                .put("applicationId", APPLICATION_ID),
        )
        val accountId = decodeUuid(account)
        val login = request(
            config,
            "General/LoginPublisherAccountById",
            JSONObject()
                .put("accountId", accountId)
                .put("password", config.password)
                .put("applicationId", APPLICATION_ID),
        )
        return decodeUuid(login).also {
            sessionForAccount = it
            sessionForRevision = config.revision
        }
    }

    private fun decodeUuid(value: String): String =
        (runCatching { JSONTokener(value).nextValue() as? String }.getOrNull())
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess && it != ZERO_UUID }
            ?: throw DexcomShareProtocolException(DexcomShareUploadError.AUTHENTICATION)

    private fun request(
        config: DexcomShareUploadConfig,
        path: String,
        body: JSONObject? = null,
        sessionId: String? = null,
        receiverSerial: String? = null,
    ): String {
        val endpoint = buildString {
            append(config.region.server)
            append(path)
            if (sessionId != null) {
                append("?sessionId=")
                append(URLEncoder.encode(sessionId, "UTF-8"))
                if (receiverSerial != null) {
                    append("&serialNumber=")
                    append(URLEncoder.encode(receiverSerial, "UTF-8"))
                }
            }
        }
        val result =
            try {
                transport.post(endpoint, body)
            } catch (e: IOException) {
                throw DexcomShareProtocolException(DexcomShareUploadError.NETWORK, recoverable = true)
            }
        if (result.status in 200..299) return result.body
        val code = runCatching { JSONObject(result.body).optString("Code") }.getOrNull()?.takeIf { it.isNotBlank() }
        val issue =
            when {
                code in setOf("MonitoredReceiverSerialNumberDoesNotMatch") -> DexcomShareUploadError.RECEIVER_MISMATCH
                code in setOf("MonitoredReceiverNotAssigned") -> DexcomShareUploadError.RECEIVER_NOT_ASSIGNED
                code in setOf("MonitoringSessionNotActive") -> DexcomShareUploadError.MONITORING
                code in setOf("SSO_AuthenticatePasswordInvalid", "SSO_AuthenticateAccountNotFound") -> DexcomShareUploadError.AUTHENTICATION
                result.status == 429 -> DexcomShareUploadError.RATE_LIMIT
                result.status == 401 || result.status == 403 -> DexcomShareUploadError.AUTHENTICATION
                else -> DexcomShareUploadError.SERVER
            }
        throw DexcomShareProtocolException(
            issue = issue,
            serverCode = code,
            recoverable = result.status in 500..599 || result.status == 429,
        )
    }

    private companion object {
        // Public application identifier used by the independently implemented Share protocol.
        const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"
        const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
    }
}
