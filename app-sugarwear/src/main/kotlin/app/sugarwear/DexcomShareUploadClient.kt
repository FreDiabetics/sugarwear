package app.sugarwear

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Network adapter is injectable for tests; TLS trust and hostname verification remain intact. */
internal interface DexcomShareHttpTransport {
    @Throws(IOException::class)
    fun post(url: String, body: JSONObject? = null): DexcomShareHttpResponse
}

internal data class DexcomShareHttpResponse(
    val status: Int,
    val body: String,
)

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
            val content =
                stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader ->
                        val buffer = CharArray(8_192)
                        var length = 0
                        while (length < buffer.size) {
                            val count = reader.read(buffer, length, buffer.size - length)
                            if (count < 0) break
                            length += count
                        }
                        String(buffer, 0, length)
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
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IOException(issue.name, cause)

/** Deliberately independent from BLE, alarms, graph providers and canonical glucose storage. */
internal class DexcomShareUploadClient(
    private val transport: DexcomShareHttpTransport = DexcomShareHttpsTransport(),
) {
    private var sessionForAccount: String? = null
    private var sessionForRevision: Long = -1L

    suspend fun testAccount(config: DexcomShareUploadConfig) =
        withContext(Dispatchers.IO) {
            authenticate(config, force = true)
            Unit
        }

    /** Only a positive assignment for this authenticated account can supply an upload serial. */
    suspend fun checkReceiver(config: DexcomShareUploadConfig, candidate: String = XDRIP_DEFAULT_RECEIVER): String =
        withContext(Dispatchers.IO) {
            require(isValidShareReceiver(candidate))
            val result = request(config, "Publisher/CheckMonitoredReceiverAssignmentStatus", null, authenticate(config), candidate, DexcomShareUploadError.RECEIVER_SERVER)
            when (result.trim()) {
                "AssignedToYou", "\"AssignedToYou\"" -> candidate
                "NotAssigned", "\"NotAssigned\"" -> throw DexcomShareProtocolException(DexcomShareUploadError.RECEIVER_NOT_ASSIGNED)
                "AssignedToOther", "\"AssignedToOther\"" -> throw DexcomShareProtocolException(DexcomShareUploadError.RECEIVER_MISMATCH)
                else -> throw DexcomShareProtocolException(DexcomShareUploadError.RECEIVER_RESPONSE)
            }
        }

    /** Reads server-confirmed assignments without replacing any existing publisher. */
    suspend fun resolveReceiver(config: DexcomShareUploadConfig): String {
        val saved = config.receiverSerial
        return if (saved.isEmpty() || saved == XDRIP_DEFAULT_RECEIVER) {
            checkReceiver(config)
        } else {
            try {
                checkReceiver(config, saved)
            } catch (problem: DexcomShareProtocolException) {
                if (!problem.receiverRejected()) throw problem
                checkReceiver(config)
            }
        }
    }

    /** Called only by the explicit watch setup action. A failed HTTP request never authorizes replacement. */
    suspend fun setupWatchReceiver(config: DexcomShareUploadConfig): String =
        withContext(Dispatchers.IO) {
            try {
                return@withContext resolveReceiver(config)
            } catch (problem: DexcomShareProtocolException) {
                if (problem.issue != DexcomShareUploadError.RECEIVER_NOT_ASSIGNED || problem.httpStatus != null) throw problem
            }
            request(config, "Publisher/ReplacePublisherAccountMonitoredReceiver", null, authenticate(config), XDRIP_DEFAULT_RECEIVER, DexcomShareUploadError.RECEIVER_SERVER)
            checkReceiver(config)
        }

    suspend fun recoverReceiver(config: DexcomShareUploadConfig, problem: DexcomShareProtocolException): String {
        if (!problem.receiverRejected() || config.receiverSerial == XDRIP_DEFAULT_RECEIVER) throw problem
        return checkReceiver(config)
    }

    suspend fun listFollowers(config: DexcomShareUploadConfig): List<DexcomShareFollower> =
        withContext(Dispatchers.IO) {
            val subscriptions = subscriptions(config, authenticate(config))
            List(subscriptions.length()) { i ->
                val entry = subscriptions.optJSONObject(i) ?: throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION)
                val id =
                    entry.optString("ContactId").takeIf(::validUuid)
                        ?: throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION)
                val alerts = entry.optJSONObject("AlertSettings")
                val subscriber = entry.opt("SubscriberId") as? String
                DexcomShareFollower(
                    contactId = id,
                    name = entry.optString("ContactName").ifBlank { "Follower" },
                    email = (entry.opt("EmailAddress") as? String)?.takeIf(String::isNotBlank),
                    accepted = subscriber?.let(::validUuid),
                    enabled = entry.opt("IsEnabled") as? Boolean,
                    sharing = DexcomShareDataSharing.entries.find { it.permission == entry.optInt("Permissions", -1) },
                    alerts =
                        DexcomShareFollowerAlert.entries
                            .mapNotNull { type ->
                                (alerts?.optJSONObject(type.field)?.opt("IsEnabled") as? Boolean)?.let { type to it }
                            }.toMap(),
                )
            }
        }

    suspend fun removeFollower(config: DexcomShareUploadConfig, contactId: String) =
        withContext(Dispatchers.IO) {
            require(validUuid(contactId))
            request(
                config,
                "Publisher/DeleteContact",
                sessionId = authenticate(config),
                serverError = DexcomShareUploadError.INVITATION,
                parameters = mapOf("contactId" to contactId),
            )
            Unit
        }

    private fun subscriptions(config: DexcomShareUploadConfig, session: String): JSONArray {
        val response = request(config, "Publisher/ListPublisherAccountSubscriptions", sessionId = session, serverError = DexcomShareUploadError.INVITATION)
        return try {
            JSONArray(response)
        } catch (error: JSONException) {
            throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION, cause = error)
        }
    }

    private fun DexcomShareProtocolException.receiverRejected(): Boolean =
        issue == DexcomShareUploadError.RECEIVER_MISMATCH || issue == DexcomShareUploadError.RECEIVER_NOT_ASSIGNED

    suspend fun inviteFollower(
        config: DexcomShareUploadConfig,
        invitation: DexcomShareInvitation,
        pendingContactId: String?,
        rememberContact: (String) -> Unit,
    ): DexcomShareInviteResult =
        withContext(Dispatchers.IO) {
            require(invitation.valid())
            val session = authenticate(config)

            fun publisher(operation: String, parameters: Map<String, String> = emptyMap(), body: JSONObject? = null) =
                request(config, "Publisher/$operation", body, session, serverError = DexcomShareUploadError.INVITATION, parameters = parameters)

            val contactId =
                if (pendingContactId == null) {
                    val name = mapOf("contactName" to invitation.contactName)
                    when (publisher("DoesContactExistByName", name).trim()) {
                        "true" -> return@withContext DexcomShareInviteResult.CONTACT_EXISTS
                        "false" -> Unit
                        else -> throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION)
                    }
                    val response = publisher("CreateContact", name + ("emailAddress" to invitation.email))
                    invitationId(response).also(rememberContact)
                } else {
                    // A previous invitation may have reached Dexcom even if its response was lost.
                    val subscriptions = subscriptions(config, session)
                    for (i in 0 until subscriptions.length()) {
                        val entry = subscriptions.optJSONObject(i) ?: throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION)
                        if (pendingContactId.equals(entry.optString("ContactId"), ignoreCase = true) && validUuid(entry.optString("SubscriptionId"))) {
                            return@withContext DexcomShareInviteResult.ALREADY_INVITED
                        }
                    }
                    pendingContactId
                }
            invitationId(publisher("CreateSubscriptionInvitation", mapOf("contactId" to contactId), invitation.payload()))
            DexcomShareInviteResult.REQUESTED
        }

    private fun invitationId(response: String): String {
        val id = runCatching { JSONTokener(response).nextValue() as? String }.getOrNull()
        return id?.takeIf(::validUuid) ?: throw DexcomShareProtocolException(DexcomShareUploadError.INVITATION)
    }

    private fun validUuid(id: String): Boolean =
        id != ZERO_UUID && runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)

    /** Returns only on confirmed server acceptance. Reauthentication and monitoring recovery are bounded. */
    suspend fun upload(config: DexcomShareUploadConfig, rows: List<DexcomShareEgv>) =
        withContext(Dispatchers.IO) {
            require(rows.isNotEmpty() && rows.size <= DexcomShareUploadPolicy.MAX_BATCH)
            if (!isValidShareReceiver(config.receiverSerial)) throw DexcomShareProtocolException(DexcomShareUploadError.RECEIVER_NOT_ASSIGNED)
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
        val account =
            request(
                config,
                "General/AuthenticatePublisherAccount",
                JSONObject()
                    .put("accountName", config.account)
                    .put("password", config.password)
                    .put("applicationId", APPLICATION_ID),
                serverError = DexcomShareUploadError.ACCOUNT_SERVER,
            )
        val accountId = decodeUuid(account, accountResponse = true)
        val login =
            request(
                config,
                "General/LoginPublisherAccountById",
                JSONObject()
                    .put("accountId", accountId)
                    .put("password", config.password)
                    .put("applicationId", APPLICATION_ID),
                serverError = DexcomShareUploadError.ACCOUNT_SERVER,
            )
        return decodeUuid(login).also {
            sessionForAccount = it
            sessionForRevision = config.revision
        }
    }

    private fun decodeUuid(value: String, accountResponse: Boolean = false): String {
        val decoded = runCatching { JSONTokener(value).nextValue() }.getOrNull()
        val id = if (accountResponse && decoded is JSONObject) decoded.opt("accountId") as? String else decoded as? String
        if (id == ZERO_UUID) throw DexcomShareProtocolException(DexcomShareUploadError.AUTHENTICATION)
        return id?.takeIf(::validUuid)
            ?: throw DexcomShareProtocolException(DexcomShareUploadError.ACCOUNT_RESPONSE)
    }

    private fun request(
        config: DexcomShareUploadConfig,
        path: String,
        body: JSONObject? = null,
        sessionId: String? = null,
        receiverSerial: String? = null,
        serverError: DexcomShareUploadError = DexcomShareUploadError.UPLOAD_SERVER,
        parameters: Map<String, String> = emptyMap(),
    ): String {
        val endpoint =
            buildString {
                append(config.region.server)
                append(path)
                if (sessionId != null) {
                    append("?sessionId=")
                    append(URLEncoder.encode(sessionId, "UTF-8"))
                    for ((name, value) in parameters) {
                        append("&").append(name).append("=").append(URLEncoder.encode(value, "UTF-8"))
                    }
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
                throw DexcomShareProtocolException(DexcomShareUploadError.NETWORK, recoverable = true, cause = e)
            }
        if (result.status in 200..299) return result.body
        val details = runCatching { JSONObject(result.body) }.getOrNull()
        val code = details?.optString("Code")?.takeIf { it.isNotBlank() }
        val message = details?.optString("Message").orEmpty()
        val issue =
            when {
                code in setOf("MonitoredReceiverSerialNumberDoesNotMatch") -> DexcomShareUploadError.RECEIVER_MISMATCH
                code in setOf("MonitoredReceiverNotAssigned") -> DexcomShareUploadError.RECEIVER_NOT_ASSIGNED
                code in setOf("MonitoringSessionNotActive") -> DexcomShareUploadError.MONITORING
                code in setOf("SSO_AuthenticatePasswordInvalid", "SSO_AuthenticateAccountNotFound", "AccountPasswordInvalid") -> DexcomShareUploadError.AUTHENTICATION
                code == "SSO_AuthenticateMaxAttemptsExceeded" -> DexcomShareUploadError.LOGIN_LIMIT
                result.status == 429 -> DexcomShareUploadError.RATE_LIMIT
                code == "SSO_InternalError" && ("Cannot Authenticate by AccountName" in message || "Cannot Authenticate by AccountId" in message) -> DexcomShareUploadError.AUTHENTICATION
                serverError == DexcomShareUploadError.ACCOUNT_SERVER && result.status in setOf(401, 403) -> DexcomShareUploadError.AUTHENTICATION
                else -> serverError
            }
        throw DexcomShareProtocolException(
            issue = issue,
            serverCode = code,
            httpStatus = result.status,
            recoverable = issue == DexcomShareUploadError.RATE_LIMIT || (issue == serverError && result.status in 500..599),
        )
    }

    private companion object {
        // Public application identifier used by the independently implemented Share protocol.
        const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"

        // xDrip+ NewDataObserver supplies this default when share_key is absent.
        const val XDRIP_DEFAULT_RECEIVER = "SM00000000"
        const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
    }
}
