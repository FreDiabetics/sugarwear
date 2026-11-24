package app.sugarwear

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class DexcomShareInvitationTest {
    private val config = DexcomShareUploadConfig("owner", "secret", "", DexcomShareRegion.INTERNATIONAL, 1)
    private val invitation = DexcomShareInvitation("A & B", "test+tag@example.org", "Mein Name")
    private val contact = "8045c741-73fb-423c-8e85-f925fc0d9df4"
    private val subscription = "2897d83e-f4ab-4cf2-b806-063ae41ca2bf"
    private val session = "0df4aa52-d005-4ca3-991f-9d2384810a30"

    private fun client(reply: (String, URI, JSONObject?) -> DexcomShareHttpResponse): DexcomShareUploadClient =
        DexcomShareUploadClient(
            object : DexcomShareHttpTransport {
                override fun post(url: String, body: JSONObject?): DexcomShareHttpResponse {
                    val uri = URI(url)
                    val operation = uri.path.substringAfterLast("/")
                    return when (operation) {
                        "AuthenticatePublisherAccount", "LoginPublisherAccountById" -> DexcomShareHttpResponse(200, JSONObject.quote(session))
                        else -> {
                            assertTrue(uri.rawQuery.startsWith("sessionId=$session"))
                            reply(operation, uri, body)
                        }
                    }
                }
            },
        )

    @Test fun `invitation encodes the intended address and sends only for the newly confirmed contact`() =
        runBlocking {
            var saved: String? = null
            val operations = mutableListOf<String>()
            val client =
                client { op, uri, body ->
                    operations += op
                    when (op) {
                        "DoesContactExistByName" -> DexcomShareHttpResponse(200, "false")
                        "CreateContact" -> {
                            assertTrue(uri.rawQuery.contains("contactName=A+%26+B"))
                            assertTrue(uri.rawQuery.contains("emailAddress=test%2Btag%40example.org"))
                            DexcomShareHttpResponse(200, JSONObject.quote(contact))
                        }
                        "CreateSubscriptionInvitation" -> {
                            assertEquals(contact, saved)
                            assertTrue(uri.rawQuery.endsWith("contactId=$contact"))
                            assertEquals(invitation.displayName, body!!.getString("DisplayName"))
                            assertEquals(1, body.getInt("Permissions"))
                            val alerts = body.getJSONObject("AlertSettings")
                            assertEquals(4, alerts.length())
                            for (name in alerts.keys()) assertFalse(alerts.getJSONObject(name).getBoolean("IsEnabled"))
                            DexcomShareHttpResponse(200, JSONObject.quote(subscription))
                        }
                        else -> error("Unexpected operation $op")
                    }
                }
            assertEquals(DexcomShareInviteResult.REQUESTED, client.inviteFollower(config, invitation, null) { saved = it })
            assertEquals(listOf("DoesContactExistByName", "CreateContact", "CreateSubscriptionInvitation"), operations)
        }

    @Test fun `invitation sends the selected data scope and notification settings`() {
        val payload =
            invitation
                .copy(
                    sharing = DexcomShareDataSharing.CURRENT_VALUE,
                    alerts = setOf(DexcomShareFollowerAlert.URGENT_LOW, DexcomShareFollowerAlert.NO_DATA),
                ).payload()

        assertEquals(0, payload.getInt("Permissions"))
        val alerts = payload.getJSONObject("AlertSettings")
        assertTrue(alerts.getJSONObject("FixedLowAlert").getBoolean("IsEnabled"))
        assertTrue(alerts.getJSONObject("NoDataAlert").getBoolean("IsEnabled"))
        assertFalse(alerts.getJSONObject("LowAlert").getBoolean("IsEnabled"))
        assertFalse(alerts.getJSONObject("HighAlert").getBoolean("IsEnabled"))
    }

    @Test fun `existing followers show the permission returned by Dexcom and removal targets the contact`() =
        runBlocking {
            val client =
                client { operation, uri, _ ->
                    when (operation) {
                        "ListPublisherAccountSubscriptions" ->
                            DexcomShareHttpResponse(
                                200,
                                """[{"ContactId":"$contact","ContactName":"Kontakt","SubscriberId":"$session","SubscriptionId":"$subscription","IsEnabled":true,"Permissions":0}]""",
                            )
                        "DeleteContact" -> {
                            assertTrue(uri.rawQuery.endsWith("contactId=$contact"))
                            DexcomShareHttpResponse(200, "")
                        }
                        else -> error("Unexpected operation $operation")
                    }
                }
            val follower = client.listFollowers(config).single()
            assertEquals(subscription, follower.subscriptionId)
            assertEquals("Kontakt", follower.name)
            assertEquals(true, follower.enabled)
            assertEquals(true, follower.accepted)
            assertEquals(DexcomShareDataSharing.CURRENT_VALUE, follower.sharing)
            assertTrue(follower.alerts.isEmpty())
            client.removeFollower(config, follower.contactId)
        }

    @Test fun `missing and invalid subscription identifiers never fall back to contact or subscriber`() =
        runBlocking {
            for (id in listOf(null, "invalid", "00000000-0000-0000-0000-000000000000")) {
                val entry = JSONObject().put("ContactId", contact).put("SubscriberId", session).put("SubscriptionId", id)
                val client =
                    client { op, _, _ ->
                        assertEquals("ListPublisherAccountSubscriptions", op)
                        DexcomShareHttpResponse(200, "[$entry]")
                    }
                assertEquals(null, client.listFollowers(config).single().subscriptionId)
            }
        }

    @Test fun `an existing name never authorizes sending to an unknown address`() =
        runBlocking {
            val client =
                client { op, _, _ ->
                    assertEquals("DoesContactExistByName", op)
                    DexcomShareHttpResponse(200, "true")
                }
            assertEquals(DexcomShareInviteResult.CONTACT_EXISTS, client.inviteFollower(config, invitation, null) { error("Unexpected contact") })
        }

    @Test fun `retry checks existing subscription before using a previously created contact`() =
        runBlocking {
            for (alreadySent in listOf(false, true)) {
                var sends = 0
                val client =
                    client { op, _, _ ->
                        when (op) {
                            "ListPublisherAccountSubscriptions" -> DexcomShareHttpResponse(200, if (alreadySent) """[{"ContactId":"$contact","SubscriptionId":"$subscription"}]""" else "[]")
                            "CreateSubscriptionInvitation" -> {
                                sends++
                                DexcomShareHttpResponse(200, JSONObject.quote(subscription))
                            }
                            else -> error("Unexpected operation $op")
                        }
                    }
                val result = client.inviteFollower(config, invitation, contact) { error("Unexpected second contact") }
                assertEquals(if (alreadySent) DexcomShareInviteResult.ALREADY_INVITED else DexcomShareInviteResult.REQUESTED, result)
                assertEquals(if (alreadySent) 0 else 1, sends)
            }
        }

    @Test fun `lost invitation receipt keeps the confirmed contact for an explicit retry`() =
        runBlocking {
            var saved: String? = null
            var requests = 0
            val client =
                client { op, _, _ ->
                    when (op) {
                        "DoesContactExistByName" -> DexcomShareHttpResponse(200, "false")
                        "CreateContact" -> DexcomShareHttpResponse(200, JSONObject.quote(contact))
                        "CreateSubscriptionInvitation" -> {
                            requests++
                            throw java.io.IOException("lost response")
                        }
                        else -> error("Unexpected operation $op")
                    }
                }
            val problem = runCatching { client.inviteFollower(config, invitation, null) { saved = it } }.exceptionOrNull()
            assertTrue(problem is DexcomShareProtocolException)
            assertEquals(contact, saved)
            assertEquals(1, requests)
        }

    @Test fun `invalid address and unconfirmed responses never report success`() =
        runBlocking {
            val client =
                client { op, _, _ ->
                    when (op) {
                        "DoesContactExistByName" -> DexcomShareHttpResponse(200, "false")
                        "CreateContact" -> DexcomShareHttpResponse(200, "null")
                        else -> error("Unexpected operation $op")
                    }
                }
            assertTrue(runCatching { client.inviteFollower(config, invitation.copy(email = "invalid"), null) { } }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { client.inviteFollower(config, invitation, null) { } }.exceptionOrNull() is DexcomShareProtocolException)
        }
}
