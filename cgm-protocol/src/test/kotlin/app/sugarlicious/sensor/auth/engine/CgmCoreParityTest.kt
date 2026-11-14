package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.crypto.SHA256
import app.sugarlicious.sensor.auth.engine.util.AESwrapper
import app.sugarlicious.sensor.auth.engine.util.Util
import app.sugarlicious.sensor.protocol.CgmDefaultGKey
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CgmCoreParityTest {
    private val fixture =
        Properties().apply {
            CgmCoreParityTest::class.java.getResourceAsStream("/auth-reference/java-peer.properties").use {
                load(assertNotNull(it))
            }
        }

    private fun bytes(key: String): ByteArray = Base64.getDecoder().decode(fixture.getProperty(key))

    @Test fun `fixed Java peer transcript authenticates and preserves derived key and challenge bytes`() {
        val plugin = Plugin("1234")
        plugin.context.keyA = KeyPair(BigInteger.ONE, Curve.G)
        plugin.context.KeyB = KeyPair(BigInteger.TWO, Curve.G.multiply(BigInteger.TWO).normalize())
        plugin.setPersistence(6, byteArrayOf(2))
        plugin.amConnected()
        assertContentEquals(byteArrayOf(0x0a, 0), assertNotNull(plugin.aNext())[0])
        for ((round, opcode) in listOf("round1" to 1, "round2" to 2)) {
            assertTrue(plugin.receivedData(bytes(round)))
            val output = assertNotNull(plugin.aNext())
            assertContentEquals(byteArrayOf(0x0a, opcode.toByte()), output[0])
            assertEquals(160, assertNotNull(output[1]).size)
        }
        assertTrue(plugin.receivedData(bytes("round3")))
        val request = assertNotNull(plugin.aNext())
        val command = assertNotNull(request[0])
        assertEquals(1, command.last().toInt())
        val token = command.copyOfRange(1, 9)
        val challenge = ByteArray(8) { it.toByte() }
        val proof = aes(bytes("sharedKey"), token + token).copyOf(8)
        assertTrue(plugin.receivedResponse(byteArrayOf(3) + proof + challenge))
        assertContentEquals(byteArrayOf(4) + aes(bytes("sharedKey"), challenge + challenge).copyOf(8), assertNotNull(plugin.aNext())[0])
        assertContentEquals(bytes("sharedKey"), plugin.getPersistence(1))
        assertTrue(plugin.receivedResponse(byteArrayOf(5, 1, 1)))
        assertContentEquals(byteArrayOf(0x4e), assertNotNull(plugin.aNext())[0])
        assertNull(plugin.aNext())
    }

    @Test fun `certificate phases preserve commands extra payloads signature and final extension`() {
        val plugin = Plugin("1234")
        plugin.setPersistence(2, ByteArray(16))
        plugin.setPersistence(8, CgmDefaultGKey.parts.certificateAuthority)
        plugin.setPersistence(9, CgmDefaultGKey.parts.certificate)
        plugin.setPersistence(10, CgmDefaultGKey.parts.privateKey)
        plugin.amConnected()
        plugin.aNext()
        plugin.receivedResponse(byteArrayOf(3) + ByteArray(16))
        plugin.aNext()
        plugin.receivedResponse(byteArrayOf(5, 1, 0))
        val first = assertNotNull(plugin.aNext())
        assertEquals(0x0b, assertNotNull(first[0])[0].toInt())
        assertTrue(plugin.receivedResponse(byteArrayOf(0x0b, 0, 0, 0xa0.toByte(), 0, 0, 0)))
        assertContentEquals(CgmDefaultGKey.parts.certificateAuthority, assertNotNull(plugin.aNext())[1])
        assertTrue(plugin.receivedData(bytes("round1")))
        assertEquals(0x0b, assertNotNull(assertNotNull(plugin.aNext())[0])[0].toInt())
        assertTrue(plugin.receivedResponse(byteArrayOf(0x0b, 0, 1, 0xa0.toByte(), 0, 0, 0)))
        assertContentEquals(CgmDefaultGKey.parts.certificate, assertNotNull(plugin.aNext())[1])
        assertTrue(plugin.receivedData(bytes("round2")))
        assertEquals(17, assertNotNull(assertNotNull(plugin.aNext())[0]).size)
        val challenge = ByteArray(16) { it.toByte() }
        assertTrue(plugin.receivedResponse(byteArrayOf(0x0d, 0) + challenge))
        val signed = assertNotNull(plugin.aNext())
        assertContentEquals(byteArrayOf(0x0d, 0, 2), signed[0])
        val raw = assertNotNull(signed[1])
        assertEquals(64, raw.size)
        val der =
            DERSequence(
                arrayOf(
                    ASN1Integer(BigInteger(1, raw.copyOfRange(0, 32))),
                    ASN1Integer(BigInteger(1, raw.copyOfRange(32, 64))),
                ),
            ).encoded
        val publicKey =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(CgmDefaultGKey.parts.certificate))
                .publicKey
        val verifier = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider())
        verifier.initVerify(publicKey)
        verifier.update(challenge)
        assertTrue(verifier.verify(der))
        assertTrue(plugin.receivedResponse(byteArrayOf(0)))
        assertContentEquals(byteArrayOf(6, 0x19), assertNotNull(plugin.aNext())[0])
        assertContentEquals(byteArrayOf(0x4e, 0x0a, 0xa9.toByte()), assertNotNull(plugin.aNext())[0])
    }

    @Test fun `configuration bytes retain the pre migration Java fingerprint`() {
        val digest = MessageDigest.getInstance("SHA-256")
        for (item in Config.Get.values()) {
            digest.update(item.name.toByteArray(Charsets.US_ASCII))
            digest.update(0.toByte())
            digest.update(Util.intToByteArray(item.bytes.size))
            digest.update(item.bytes)
        }
        assertContentEquals(bytes("configSha256"), digest.digest())
    }

    @Test fun `SHA padding and multiple blocks remain equal to independent JCA SHA256`() {
        for (size in (0..257).toList() + listOf(4095, 4096, 4097)) {
            val message = ByteArray(size) { (it * 73 + 129).toByte() }
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(message), SHA256.hash(message), "size=$size")
        }
    }

    @Test fun `key imports retain generated public private material and malformed input behavior`() {
        val generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val generated = generator.generateKeyPair()
        val imported = assertNotNull(KeyPair.fromBytes(arrayOf(generated.public.encoded, generated.private.encoded)))
        assertEquals((generated.private as java.security.interfaces.ECPrivateKey).s, imported.getPrivateKey())
        assertEquals((generated.public as org.bouncycastle.jce.interfaces.ECPublicKey).q, imported.getPublicKey())
        assertNotNull(KeyPair.fromBytes(generated.private.encoded))
        assertNull(KeyPair.fromBytes(byteArrayOf(1, 2, 3)))
        assertNull(KeyPair.fromBytes(arrayOf(byteArrayOf(1), byteArrayOf(2))))
        assertNull(KeyPair.fromBytes(null as ByteArray?))
        assertNull(KeyPair.fromBytes(null as Array<ByteArray>?))
    }

    @Test fun `AES provider selection and invalid input contracts remain compatible`() {
        val key = ByteArray(16) { it.toByte() }
        assertContentEquals(aes(key, ByteArray(16)), AESwrapper(key).aes(ByteArray(16)))
        assertNull(AESwrapper(ByteArray(3)).aes(ByteArray(16)))
        assertNull(AESwrapper(key).aes(ByteArray(15)))
        assertNull(AESwrapper(null).aes(ByteArray(16)))
    }

    @Test fun `context preserves six character prefix caching and session reset semantics`() {
        val context = Context()
        context.password = "123456"
        val cached = context.getPasswordBytes()
        assertContentEquals("00123456".toByteArray(), cached)
        assertSame(cached, context.getPasswordBytes())
        context.savedKey = ByteArray(16)
        context.packet[1] = Packet(BigInteger.ONE, Curve.G, Curve.G)
        context.reset()
        assertNull(context.savedKey)
        assertNull(context.packet[1])
        assertSame(cached, context.getPasswordBytes())
    }

    private fun aes(key: ByteArray, input: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(input)
        }
}
