package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.crypto.SHA256
import app.sugarlicious.sensor.auth.engine.Calc
import app.sugarlicious.sensor.auth.engine.Config
import app.sugarlicious.sensor.auth.engine.Context
import app.sugarlicious.sensor.auth.engine.Curve
import app.sugarlicious.sensor.auth.engine.DSAChallenger
import app.sugarlicious.sensor.auth.engine.Packet
import app.sugarlicious.sensor.auth.engine.Plugin
import app.sugarlicious.sensor.auth.engine.message.AuthStatusRxMessage
import app.sugarlicious.sensor.auth.engine.message.CertInfoTxMessage
import app.sugarlicious.sensor.auth.engine.util.AESwrapper
import app.sugarlicious.sensor.auth.engine.util.Util
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic data only. These characterize the unchanged Java engine, not a physical sensor. */
class LegacyAuthenticationReferenceTest {
    @Test fun `legacy hex swaps nibbles rather than decoding standard hex`() {
        assertContentEquals(byteArrayOf(0x21, 0x43, 0x65, 0x87.toByte(), 0xa9.toByte(), 0xcb.toByte(), 0xed.toByte(), 0x0f), Util.hexStringToByteArray("123456789ABCDEF0"))
        assertContentEquals(byteArrayOf(0x21), Util.hexStringToByteArray("12"))
    }

    @Test fun `tolerant hex strips separators before applying legacy nibble order`() {
        assertContentEquals(byteArrayOf(0x21, 0xba.toByte()), Util.tolerantHexStringToByteArray("12 : ab"))
        assertNull(Util.hexStringToByteArray(""))
        assertNull(Util.hexStringToByteArray("123"))
    }

    @Test fun `hash length prefixes remain big endian while certificate lengths are little endian`() {
        assertContentEquals(byteArrayOf(0, 0, 1, 2), Util.intToByteArray(258))
        val plugin = Plugin("1234")
        plugin.setPersistence(8, ByteArray(258))
        assertContentEquals(byteArrayOf(0x0b, 0, 2, 1, 0, 0), CertInfoTxMessage.expectMyCert1(plugin))
    }

    @Test fun `AES preserves the FIPS 197 AES128 known answer`() {
        // Standard hex here is deliberate: do not decode fixtures using the legacy codec.
        val key = ByteArray(16) { it.toByte() }
        val plaintext = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte())
        assertEquals("69C4E0D86A7B0430D8CDB78070B4C55A", Util.bytesToHex(AESwrapper(key).aes(plaintext)))
    }

    @Test fun `challenge repeats eight bytes and truncates AES output to eight bytes`() {
        val context =
            Context().apply {
                savedKey = ByteArray(16) { it.toByte() }
                challenge = ByteArray(8) { it.toByte() }
            }
        // Independently calculated with platform AES, not with Calc or the legacy hex codec.
        assertEquals("3DFBDCC410DA6B0B", Util.bytesToHex(Calc.calculateHash(context)))
    }

    @Test fun `SHA256 preserves published empty and abc known answers`() {
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", Util.bytesToHex(SHA256.hash(byteArrayOf())))
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", Util.bytesToHex(SHA256.hash("abc".toByteArray(Charsets.US_ASCII))))
    }

    @Test fun `ECDSA response is unsigned fixed width r and s and verifies through public JCA`() {
        val provider = BouncyCastleProvider()
        val keys =
            KeyPairGenerator
                .getInstance("EC", provider)
                .apply {
                    initialize(ECGenParameterSpec("secp256r1"))
                }.generateKeyPair()
        val challenge = ByteArray(16) { it.toByte() }
        val raw = assertNotNull(DSAChallenger(keys.private).response(challenge))
        assertEquals(64, raw.size)
        val der =
            DERSequence(
                arrayOf(
                    ASN1Integer(BigInteger(1, raw.copyOfRange(0, 32))),
                    ASN1Integer(BigInteger(1, raw.copyOfRange(32, 64))),
                ),
            ).encoded
        val verifier = Signature.getInstance("SHA256withECDSA", provider)
        verifier.initVerify(keys.public)
        verifier.update(challenge)
        assertTrue(verifier.verify(der))
        verifier.initVerify(keys.public)
        verifier.update(challenge.copyOf().apply { this[0] = 1 })
        assertFalse(verifier.verify(der))
    }

    @Test fun `stored key transcript retains alternate slot challenge reply and completion command`() {
        val session = storedSession()
        session.connected()
        val request = assertNotNull(session.next())
        assertEquals(2, request.size)
        val command = assertNotNull(request[0])
        assertEquals(10, command.size)
        assertEquals(0x02, command.first().toInt())
        assertEquals(0x01, command.last().toInt()) // Channel 6 value 0x02 selects the alternate end byte 0x01.
        assertNull(request[1])
        assertTrue(session.onAuthenticationData(challengeFrame()))
        val response = assertNotNull(session.next())
        assertEquals("043DFBDCC410DA6B0B", Util.bytesToHex(response[0]))
        assertNull(response[1])
        assertTrue(session.onAuthenticationData(byteArrayOf(5, 1, 1)))
        val completed = assertNotNull(session.next())
        assertEquals(1, completed.size)
        assertContentEquals(byteArrayOf(0x4e), completed[0])
        assertContentEquals(ByteArray(16) { it.toByte() }, session.sharedKey())
        assertNull(session.next())
    }

    @Test fun `primary slot is encoded differently without replacing the random token`() {
        val session = storedSession(CgmReceiverSlot.PRIMARY)
        session.connected()
        assertEquals(0x02, assertNotNull(assertNotNull(session.next())[0]).last().toInt())
    }

    @Test fun `rejected unbonded status emits the three element bond failure signal`() {
        val session = afterChallenge()
        assertTrue(session.onAuthenticationData(byteArrayOf(5, 0, 0)))
        val output = assertNotNull(session.next())
        assertEquals(3, output.size)
        assertContentEquals(byteArrayOf(0x4e, 0x0a, 0xa9.toByte()), output[0])
        assertNull(output[1])
        assertNull(output[2])
    }

    @Test fun `authenticated unbonded peer requests first certificate without changing byte order`() {
        val session = afterChallenge()
        assertTrue(session.onAuthenticationData(byteArrayOf(5, 1, 0)))
        val output = assertNotNull(session.next())
        val length = CgmDefaultGKey.parts.certificateAuthority.size
        assertContentEquals(byteArrayOf(0x0b, 0, length.toByte(), (length shr 8).toByte(), 0, 0), output[0])
        assertNull(output[1])
    }

    @Test fun `bond detection accepts exactly the three legacy extensions`() {
        val session = storedSession()
        assertTrue(session.shouldBond(byteArrayOf(6, 0x19)))
        assertTrue(session.shouldBond(byteArrayOf(0xff.toByte(), 6, 1)))
        assertTrue(session.shouldBond(byteArrayOf(6, 0)))
        assertFalse(session.shouldBond(byteArrayOf(6)))
        assertFalse(session.shouldBond(byteArrayOf(6, 0, 0)))
    }

    @Test fun `status distinguishes authenticated bonded and refresh flags`() {
        assertTrue(AuthStatusRxMessage(byteArrayOf(5, 1, 1)).isAuthenticated)
        assertTrue(AuthStatusRxMessage(byteArrayOf(5, 1, 1)).isBonded)
        assertTrue(AuthStatusRxMessage(byteArrayOf(5, 0, 3)).needsRefresh())
        assertFalse(AuthStatusRxMessage(byteArrayOf(4, 1, 1)).isAuthenticated)
        assertFalse(AuthStatusRxMessage(byteArrayOf(5)).isAuthenticated)
    }

    @Test fun `fresh exchange accepts fragmented peer proofs and derives matching session keys`() {
        val plugin = Plugin("1234")
        // Test-only synthetic private scalars. Production randomness is untouched.
        plugin.context.keyA = fixedKey(1)
        plugin.context.KeyB = fixedKey(2)
        val peer =
            Context().apply {
                password = "1234"
                alice =
                    Config.Get.BOB.bytes
                        .copyOf()
                bob =
                    Config.Get.ALICE.bytes
                        .copyOf()
                keyA = fixedKey(3)
                KeyB = fixedKey(4)
            }
        plugin.amConnected()
        assertContentEquals(byteArrayOf(0x0a, 0), assertNotNull(plugin.aNext())[0])
        val peerFirst = Calc.getRound1Packet(peer).output()
        assertFalse(plugin.receivedData(peerFirst.copyOfRange(0, 80)))
        assertTrue(plugin.receivedData(peerFirst.copyOfRange(80, 160)))
        val clientFirst = assertNotNull(plugin.aNext())
        assertContentEquals(byteArrayOf(0x0a, 1), clientFirst[0])
        peer.packet[1] = Packet.parse(assertNotNull(clientFirst[1]))
        assertTrue(Calc.validateRound1Packet(peer))
        assertTrue(plugin.receivedData(Calc.getRound2Packet(peer).output()))
        val clientSecond = assertNotNull(plugin.aNext())
        assertContentEquals(byteArrayOf(0x0a, 2), clientSecond[0])
        peer.packet[2] = Packet.parse(assertNotNull(clientSecond[1]))
        assertTrue(Calc.validateRound2Packet(peer))
        assertTrue(plugin.receivedData(Calc.getRound3Packet(peer).output()))
        val request = assertNotNull(plugin.aNext())
        peer.packet[3] = Packet.parse(assertNotNull(request[1]))
        assertTrue(Calc.validateRound3Packet(peer))
        assertContentEquals(Calc.getShortSharedKey(peer), plugin.getPersistence(1))
        // Independently derived from SHA256(x(32 * unsigned("1234") * G)), first 16 bytes.
        assertEquals("178641C6B47728F1D1068EA7D14B14A0", Util.bytesToHex(plugin.getPersistence(1)))
        assertEquals(16, assertNotNull(plugin.getPersistence(1)).size)
        assertEquals(10, assertNotNull(request[0]).size)
    }

    @Test fun `packet encoding retains full coordinates and unsigned leading zero padding`() {
        val packet = Packet(BigInteger.ONE, Curve.G, Curve.G)
        val coordinates =
            "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296" +
                "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5"
        val expected = coordinates + coordinates + "00".repeat(31) + "01"
        assertEquals(expected, Util.bytesToHex(packet.output()))
        val parsed = assertNotNull(Packet.parse(packet.output()))
        assertEquals(BigInteger.ONE, parsed.hash)
        assertEquals(Curve.G.normalize(), parsed.publicKeyPoint1.normalize())
    }

    private fun fixedKey(scalar: Long): app.sugarlicious.sensor.auth.engine.KeyPair {
        val privateKey = BigInteger.valueOf(scalar)
        return app.sugarlicious.sensor.auth.engine
            .KeyPair(privateKey, Curve.G.multiply(privateKey).normalize())
    }

    private fun storedSession(slot: CgmReceiverSlot = CgmReceiverSlot.WATCH_ALTERNATE) =
        CgmAuthenticationSession("1234", persistedKey = ByteArray(16) { it.toByte() }, receiverSlot = slot)

    private fun challengeFrame() = byteArrayOf(3) + ByteArray(8) + ByteArray(8) { it.toByte() }

    private fun afterChallenge(): CgmAuthenticationSession =
        storedSession().apply {
            connected()
            next()
            onAuthenticationData(challengeFrame())
            next()
        }
}
