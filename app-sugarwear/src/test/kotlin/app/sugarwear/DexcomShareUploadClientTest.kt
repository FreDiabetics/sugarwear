package app.sugarwear

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DexcomShareUploadClientTest {
    private val accountId = "4ab5cc51-e5bb-4ddc-8a21-dd0aa119a502"
    private val session = "e31d30cc-fd2a-4db2-9ef4-7dcd007f5401"
    private val config = DexcomShareUploadConfig("account", "secret", "AB12345678", DexcomShareRegion.INTERNATIONAL, 2L)
    private val egv = DexcomShareEgv(1_800_000_000_000L, 123, 4)

    private class FakeHttp(
        private val respond: (String, Int) -> DexcomShareHttpResponse,
    ) : DexcomShareHttpTransport {
        val requests = mutableListOf<Pair<String, JSONObject?>>()

        override fun post(url: String, body: JSONObject?): DexcomShareHttpResponse {
            requests += url to body
            return respond(url, requests.size)
        }
    }

    @Test fun `successful upload verifies account then submits measurement on international endpoint`() =
        runBlocking {
            val http =
                FakeHttp { url, _ ->
                    when {
                        url.contains("AuthenticatePublisherAccount") -> DexcomShareHttpResponse(200, "\"$accountId\"")
                        url.contains("LoginPublisherAccountById") -> DexcomShareHttpResponse(200, "\"$session\"")
                        else -> DexcomShareHttpResponse(200, "")
                    }
                }
            DexcomShareUploadClient(http).upload(config, listOf(egv))
            assertEquals(3, http.requests.size)
            assertTrue(http.requests.all { it.first.startsWith("https://shareous1.dexcom.com/") })
            assertTrue(
                http.requests
                    .last()
                    .first
                    .contains("Publisher/PostReceiverEgvRecords"),
            )
            assertEquals(
                123,
                http.requests
                    .last()
                    .second!!
                    .getJSONArray("Egvs")
                    .getJSONObject(0)
                    .getInt("Value"),
            )
        }

    @Test fun `inactive monitoring restarts once and then uploads`() =
        runBlocking {
            var uploads = 0
            val http =
                FakeHttp { url, _ ->
                    when {
                        url.contains("AuthenticatePublisherAccount") -> DexcomShareHttpResponse(200, "\"$accountId\"")
                        url.contains("LoginPublisherAccountById") -> DexcomShareHttpResponse(200, "\"$session\"")
                        url.contains("StartRemoteMonitoringSession") -> DexcomShareHttpResponse(200, "")
                        else -> {
                            uploads++
                            if (uploads == 1) {
                                DexcomShareHttpResponse(500, "{\"Code\":\"MonitoringSessionNotActive\"}")
                            } else {
                                DexcomShareHttpResponse(200, "")
                            }
                        }
                    }
                }
            DexcomShareUploadClient(http).upload(config, listOf(egv))
            assertEquals(2, uploads)
            assertEquals(1, http.requests.count { it.first.contains("StartRemoteMonitoringSession") })
        }

    @Test fun `expired session causes at most one reauthentication`() =
        runBlocking {
            val http =
                FakeHttp { url, _ ->
                    when {
                        url.contains("AuthenticatePublisherAccount") -> DexcomShareHttpResponse(200, "\"$accountId\"")
                        url.contains("LoginPublisherAccountById") -> DexcomShareHttpResponse(200, "\"$session\"")
                        else -> DexcomShareHttpResponse(500, "{\"Code\":\"SessionNotValid\"}")
                    }
                }
            val client = DexcomShareUploadClient(http)
            val problem =
                try {
                    client.upload(config, listOf(egv))
                    null
                } catch (e: DexcomShareProtocolException) {
                    e
                }
            assertEquals("SessionNotValid", problem?.serverCode)
            assertEquals(4, http.requests.count { it.first.contains("General/") })
            assertEquals(2, http.requests.count { it.first.contains("PostReceiverEgvRecords") })
        }

    @Test fun `invalid receiver is not treated as successful upload`() =
        runBlocking {
            val http =
                FakeHttp { url, _ ->
                    when {
                        url.contains("AuthenticatePublisherAccount") -> DexcomShareHttpResponse(200, "\"$accountId\"")
                        url.contains("LoginPublisherAccountById") -> DexcomShareHttpResponse(200, "\"$session\"")
                        else -> DexcomShareHttpResponse(500, "{\"Code\":\"MonitoredReceiverSerialNumberDoesNotMatch\"}")
                    }
                }
            val problem =
                try {
                    DexcomShareUploadClient(http).upload(config, listOf(egv))
                    null
                } catch (e: DexcomShareProtocolException) {
                    e
                }
            assertEquals(DexcomShareUploadError.RECEIVER_MISMATCH, problem?.issue)
            assertEquals(false, problem?.recoverable)
        }

    @Test fun `missing receiver blocks upload before sending credentials or measurements`() =
        runBlocking {
            val http = FakeHttp { _, _ -> error("No network request expected") }
            val problem =
                try {
                    DexcomShareUploadClient(http).upload(config.copy(receiverSerial = ""), listOf(egv))
                    null
                } catch (error: DexcomShareProtocolException) {
                    error
                }
            assertEquals(DexcomShareUploadError.RECEIVER_NOT_ASSIGNED, problem?.issue)
            assertTrue(http.requests.isEmpty())
        }

    @Test fun `valid login does not falsely claim an uploaded glucose row`() =
        runBlocking {
            val http =
                FakeHttp { url, _ ->
                    when {
                        url.contains("AuthenticatePublisherAccount") -> DexcomShareHttpResponse(200, "\"$accountId\"")
                        url.contains("LoginPublisherAccountById") -> DexcomShareHttpResponse(200, "\"$session\"")
                        else -> error("A connection test must not publish readings")
                    }
                }
            DexcomShareUploadClient(http).testAccount(config.copy(receiverSerial = ""))
            assertEquals(2, http.requests.size)
            assertTrue(http.requests.none { it.first.contains("PostReceiverEgvRecords") })
        }
}
