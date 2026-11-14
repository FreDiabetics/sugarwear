package app.sugarlicious.datasource.manufacturer

import app.sugarlicious.model.DataSourceId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudShareClientTest {
    @Test fun `duplicate Share uploads do not truncate day history and subsequent fetch only requests recent gap`() {
        val now = 1_800_000_000_000L
        val samples = org.json.JSONArray()
        repeat(288) { index ->
            val timestamp = now - (287 - index) * 300_000L
            repeat(2) { copy ->
                samples.put(
                    org.json
                        .JSONObject()
                        .put("Value", 120 + index % 5)
                        .put("Trend", 4)
                        .put("WT", "Date(${timestamp - copy * 500L})"),
                )
            }
        }
        val queries = mutableListOf<Map<String, String>>()
        val replies = ArrayDeque(listOf(ShareResponse(200, "\"account\""), ShareResponse(200, "\"session\""), ShareResponse(200, samples.toString()), ShareResponse(200, "[]")))
        val client =
            CloudShareClient(
                ShareCredentials("user", "password", "ous"),
                ShareTransport { _, query, _ ->
                    queries += query
                    replies.removeFirst()
                },
            )
        val state = requireNotNull(client.read(now))
        assertEquals(288, state.glucoseHistory.size)
        assertEquals(now - 287 * 300_000L, state.glucoseHistory.first().measuredAtEpochMs)
        assertEquals("1440", queries[2]["maxCount"])
        client.read(now + 300_000L)
        assertEquals("7", queries.last()["minutes"])
        assertTrue(queries.last()["maxCount"]!!.toInt() < 20)
    }

    @Test fun `G7 Share Date timestamps without slash delimiters retain backfill`() {
        val replies =
            ArrayDeque(
                listOf(
                    ShareResponse(200, "\"account\""),
                    ShareResponse(200, "\"session\""),
                    ShareResponse(200, "[{\"Value\":123,\"Trend\":4,\"WT\":\"Date(1800000000000)\"},{\"Value\":120,\"Trend\":4,\"WT\":\"Date(1799999700000+0200)\"}]"),
                ),
            )
        val client = CloudShareClient(ShareCredentials("user", "password", "ous"), ShareTransport { _, _, _ -> replies.removeFirst() })
        val state = requireNotNull(client.read(1_800_000_000_000L))
        assertEquals(2, state.glucoseHistory.size)
        assertEquals(1_800_000_000_000L, state.glucose!!.measuredAtEpochMs)
        assertEquals(123.0, state.glucose!!.valueMgDl, 0.0)
        assertTrue(client.lastReadDiagnostics.contains("parsed=2; accepted=2"))
    }

    @Test fun `Share supports G7 account response and reuses its session`() {
        val replies =
            ArrayDeque(
                listOf(
                    ShareResponse(200, "{\"accountId\":\"account\"}"),
                    ShareResponse(200, "\"session\""),
                    ShareResponse(200, "[{\"Value\":123,\"Trend\":4,\"WT\":\"/Date(1800000000000)/\"}]"),
                    ShareResponse(200, "[{\"Value\":123,\"Trend\":4,\"WT\":\"/Date(1800000000000)/\"}]"),
                ),
            )
        var requests = 0
        val client =
            CloudShareClient(
                ShareCredentials("user", "password", "ous"),
                ShareTransport { _, _, _ ->
                    requests++
                    replies.removeFirst()
                },
            )
        val state = client.read(1_800_000_000_000L)!!
        assertEquals(DataSourceId.DEXCOM_SHARE, state.source)
        assertEquals(123.0, state.glucose!!.valueMgDl, 0.0)
        assertEquals(1_800_000_000_000L, state.glucose!!.measuredAtEpochMs)
        assertNull(state.insulin)
        client.read(1_800_000_000_000L)
        assertEquals(4, requests)
    }

    @Test fun `invalid Share session is renewed once without an unbounded retry`() {
        val replies =
            ArrayDeque(
                listOf(
                    ShareResponse(200, "\"account\""),
                    ShareResponse(200, "\"session\""),
                    ShareResponse(500, "{\"Code\":\"SessionIdNotFound\"}"),
                    ShareResponse(200, "\"account\""),
                    ShareResponse(200, "\"session2\""),
                    ShareResponse(500, "{\"Code\":\"SessionIdNotFound\"}"),
                ),
            )
        val client = CloudShareClient(ShareCredentials("user", "secret", "us"), ShareTransport { _, _, _ -> replies.removeFirst() })
        try {
            client.read(1_800_000_000_000L)
            fail("must report failed authentication")
        } catch (failure: ShareFailure) {
            assertEquals(ShareFailureKind.AUTHENTICATION, failure.kind)
            assertFalse(failure.message.orEmpty().contains("secret"))
        }
        assertTrue(replies.isEmpty())
    }
}
