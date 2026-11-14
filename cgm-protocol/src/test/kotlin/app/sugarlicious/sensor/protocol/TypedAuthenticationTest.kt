package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.engine.Calc
import app.sugarlicious.sensor.auth.engine.Config
import app.sugarlicious.sensor.auth.engine.Context
import app.sugarlicious.sensor.auth.engine.Packet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TypedAuthenticationTest {
    @Test fun `null output is an unknown legacy failure rather than a fabricated timeout`() {
        assertEquals(AuthStep.Failed(AuthError.LegacyEngineUnavailable), decodeLegacyAuthStep(null))
    }

    @Test fun `send output preserves extra data before authentication command`() {
        val step = assertIs<AuthStep.Send>(decodeLegacyAuthStep(arrayOf(byteArrayOf(0x0a), byteArrayOf(0x11))))
        assertEquals(listOf(AuthChannel.EXTRA, AuthChannel.AUTHENTICATION), step.writes.map { it.channel })
        assertContentEquals(byteArrayOf(0x11), step.writes[0].payload.copyBytes())
        assertContentEquals(byteArrayOf(0x0a), step.writes[1].payload.copyBytes())
    }

    @Test fun `extra only output does not invent an authentication command`() {
        val step = assertIs<AuthStep.Send>(decodeLegacyAuthStep(arrayOf(null, byteArrayOf(0x11))))
        assertEquals(listOf(AuthChannel.EXTRA), step.writes.map { it.channel })
    }

    @Test fun `completion retains the command consumed by the caller`() {
        val step = assertIs<AuthStep.Complete>(decodeLegacyAuthStep(arrayOf(byteArrayOf(0x4e))))
        assertContentEquals(byteArrayOf(0x4e), step.command.copyBytes())
    }

    @Test fun `bond required retains the legacy recovery command`() {
        val step = assertIs<AuthStep.BondRequired>(decodeLegacyAuthStep(arrayOf(byteArrayOf(0x4e, 0x0a, 0xa9.toByte()), null, null)))
        assertEquals("AuthBytes(size=3)", step.command.toString())
        assertContentEquals(byteArrayOf(0x4e, 0x0a, 0xa9.toByte()), step.command.copyBytes())
    }

    @Test fun `unexpected array sizes are explicit errors`() {
        for (size in listOf(0, 4)) {
            assertEquals(AuthStep.Failed(AuthError.InvalidLength(AuthField.OUTPUT_ARITY, size)), decodeLegacyAuthStep(arrayOfNulls(size)))
        }
    }

    @Test fun `missing completion and bond payloads are rejected`() {
        assertEquals(AuthStep.Failed(AuthError.MissingPayload(AuthField.COMMAND)), decodeLegacyAuthStep(arrayOf(null)))
        assertEquals(AuthStep.Failed(AuthError.MissingPayload(AuthField.COMMAND)), decodeLegacyAuthStep(arrayOf(null, null, null)))
    }

    @Test fun `empty send output and unexpected bond payloads are rejected`() {
        assertIs<AuthStep.Failed>(decodeLegacyAuthStep(arrayOf(null, null)))
        assertIs<AuthStep.Failed>(decodeLegacyAuthStep(arrayOf(byteArrayOf(1), byteArrayOf(2), null)))
        assertIs<AuthStep.Failed>(decodeLegacyAuthStep(arrayOf(byteArrayOf(), null)))
    }

    @Test fun `packet ownership prevents mutation and secret rendering`() {
        val source = byteArrayOf(0x12, 0x34)
        val value = AuthBytes(source)
        source[0] = 0
        value.copyBytes()[1] = 0
        assertContentEquals(byteArrayOf(0x12, 0x34), value.copyBytes())
        assertEquals(AuthBytes(byteArrayOf(0x12, 0x34)), value)
        assertEquals("AuthBytes(size=2)", value.toString())
    }

    @Test fun `real engine is usable through typed interface without leaking array signals`() {
        val authentication: SensorAuthentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234", persistedKey = ByteArray(16) { it.toByte() }))
        assertEquals(AuthInputResult.Advance, authentication.connected())
        assertIs<AuthStep.Send>(authentication.next())
        assertEquals(AuthInputResult.Advance, authentication.onAuthenticationData(byteArrayOf(3) + ByteArray(8) + ByteArray(8) { it.toByte() }))
        val response = assertIs<AuthStep.Send>(authentication.next())
        assertContentEquals(
            byteArrayOf(4, 0x3d, 0xfb.toByte(), 0xdc.toByte(), 0xc4.toByte(), 0x10, 0xda.toByte(), 0x6b, 0x0b),
            response.writes
                .single()
                .payload
                .copyBytes(),
        )
        assertEquals(AuthInputResult.Advance, authentication.onAuthenticationData(byteArrayOf(5, 1, 1)))
        assertIs<AuthStep.Complete>(authentication.next())
        val key = assertIs<AuthKeyResult.Available>(authentication.sharedKey())
        assertContentEquals(ByteArray(16) { it.toByte() }, key.key.copyBytes())
    }

    @Test fun `no next action and absent shared key remain explicit states`() {
        val authentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234"))
        assertEquals(AuthInputResult.NoAdvance, authentication.onAuthenticationData(byteArrayOf(5, 1, 1)))
        assertEquals(AuthKeyResult.Unavailable, authentication.sharedKey())
    }

    @Test fun `empty input has a typed length error`() {
        val authentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234"))
        assertEquals(AuthInputResult.Failed(AuthError.InvalidLength(AuthField.AUTHENTICATION_INPUT, 0)), authentication.onAuthenticationData(byteArrayOf()))
        assertEquals(AuthInputResult.Failed(AuthError.InvalidLength(AuthField.EXTRA_INPUT, 0)), authentication.onExtraData(byteArrayOf()))
    }

    @Test fun `truncated challenge maps to a malformed frame without exposing exception text`() {
        val authentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234", persistedKey = ByteArray(16)))
        authentication.connected()
        authentication.next()
        assertEquals(AuthInputResult.Failed(AuthError.MalformedFrame(AuthField.AUTHENTICATION_INPUT, 1)), authentication.onAuthenticationData(byteArrayOf(3)))
    }

    @Test fun `explicit peer proof mismatch is distinguished from unknown legacy failure`() {
        val (session, challenge) = freshSessionAwaitingChallenge()
        val authentication = ProtocolAuthenticationAdapter(session)
        val mismatched = challenge.copyOf().apply { this[1] = (this[1].toInt() xor 1).toByte() }
        assertEquals(
            AuthInputResult.Failed(AuthError.PeerAuthenticationMismatch),
            authentication.onAuthenticationData(mismatched),
        )
    }

    @Test fun `fresh peer proof can authenticate through the typed boundary`() {
        val (session, challenge) = freshSessionAwaitingChallenge()
        val authentication: SensorAuthentication = ProtocolAuthenticationAdapter(session)
        assertEquals(AuthInputResult.Advance, authentication.onAuthenticationData(challenge))
        assertIs<AuthStep.Send>(authentication.next())
        assertEquals(AuthInputResult.Advance, authentication.onAuthenticationData(byteArrayOf(5, 1, 1)))
        assertIs<AuthStep.Complete>(authentication.next())
    }

    @Test fun `invalid certificate response becomes an explicit rejected input`() {
        val authentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234", persistedKey = ByteArray(16)))
        authentication.connected()
        authentication.next()
        authentication.onAuthenticationData(byteArrayOf(3) + ByteArray(16))
        authentication.next()
        authentication.onAuthenticationData(byteArrayOf(5, 1, 0))
        authentication.next()
        assertEquals(
            AuthInputResult.Failed(AuthError.InputRejected(AuthField.AUTHENTICATION_INPUT)),
            authentication.onAuthenticationData(byteArrayOf(0x0b)),
        )
    }

    @Test fun `typed bond detection preserves native extension packets`() {
        val authentication: SensorAuthentication = ProtocolAuthenticationAdapter(CgmAuthenticationSession("1234"))
        assertEquals(true, authentication.shouldBond(byteArrayOf(6, 0x19)))
        assertEquals(false, authentication.shouldBond(byteArrayOf(6, 0x19, 0)))
    }

    private fun freshSessionAwaitingChallenge(): Pair<CgmAuthenticationSession, ByteArray> {
        val session = CgmAuthenticationSession("1234")
        val peer =
            Context().apply {
                password = "1234"
                alice =
                    Config.Get.BOB.bytes
                        .copyOf()
                bob =
                    Config.Get.ALICE.bytes
                        .copyOf()
                keyA =
                    app.sugarlicious.sensor.auth.engine
                        .KeyPair()
                KeyB =
                    app.sugarlicious.sensor.auth.engine
                        .KeyPair()
            }
        session.connected()
        session.next()
        session.onExtraData(Calc.getRound1Packet(peer).output())
        peer.packet[1] = Packet.parse(assertNotNull(assertNotNull(session.next())[1]))
        session.onExtraData(Calc.getRound2Packet(peer).output())
        peer.packet[2] = Packet.parse(assertNotNull(assertNotNull(session.next())[1]))
        session.onExtraData(Calc.getRound3Packet(peer).output())
        val request = assertNotNull(session.next())
        peer.packet[3] = Packet.parse(assertNotNull(request[1]))
        peer.savedKey = Calc.getShortSharedKey(peer)
        peer.challenge = assertNotNull(request[0]).copyOfRange(1, 9)
        val proof = assertNotNull(Calc.calculateHash(peer))
        return session to (byteArrayOf(3) + proof + ByteArray(8))
    }
}
