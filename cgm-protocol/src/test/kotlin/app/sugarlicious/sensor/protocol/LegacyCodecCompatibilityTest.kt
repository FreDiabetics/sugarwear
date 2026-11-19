package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.engine.message.AuthChallengeTxMessage
import app.sugarlicious.sensor.auth.engine.message.AuthRequestTxMessage2
import app.sugarlicious.sensor.auth.engine.message.AuthStatusRxMessage
import app.sugarlicious.sensor.auth.engine.message.BaseMessage
import app.sugarlicious.sensor.auth.engine.message.CertInfoRxMessage
import app.sugarlicious.sensor.auth.engine.message.SignChallengeTxMessage
import app.sugarlicious.sensor.auth.engine.util.ByteArrayHashMap
import app.sugarlicious.sensor.auth.engine.util.Util
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Compatibility edges captured against Java before migration; none are sensor credentials. */
class LegacyCodecCompatibilityTest {
    @Test fun `certificate opcode retains its JVM byte field descriptor`() {
        assertEquals(Byte::class.javaPrimitiveType, CertInfoRxMessage::class.java.getField("opcode").type)
    }

    @Test fun `failed base initialization retains the allocated buffer as Java did`() {
        class Probe : BaseMessage() {
            fun initializeEmpty() = init(1, 0)
        }
        val probe = Probe()
        assertFailsWith<java.nio.BufferOverflowException> { probe.initializeEmpty() }
        assertEquals(0, assertNotNull(probe.data).capacity())
        assertNull(probe.byteSequence)
    }

    @Test fun `all ASCII character pairs match the pre migration Java parser fingerprint`() {
        val digest = MessageDigest.getInstance("SHA-256")
        for (first in 0..127) {
            for (second in 0..127) {
                val decoded = Util.hexStringToByteArray(charArrayOf(first.toChar(), second.toChar()).concatToString())
                digest.update(if (decoded == null) 0.toByte() else 1.toByte())
                if (decoded != null) {
                    digest.update(decoded.size.toByte())
                    digest.update(decoded)
                }
            }
        }
        // Captured from Util.java at 2197aa52, before replacing the source.
        assertEquals("4F249C4552B3AEC267D15D9B007C55948D8EFFF521583476C79884FB03FD4F0D", Util.bytesToHex(digest.digest()))
    }

    @Test fun `hex retains Java trimming Unicode digits and invalid digit arithmetic`() {
        val vectors =
            listOf(
                "12" to byteArrayOf(0x21),
                " 12\t" to byteArrayOf(0x21),
                "\u000012\u0000" to byteArrayOf(0x21),
                "１２" to byteArrayOf(0x21),
                "GG" to byteArrayOf(0xef.toByte()),
                "1G" to byteArrayOf(0xf1.toByte()),
                "G1" to byteArrayOf(0x0f),
                "0x12" to byteArrayOf(0xf0.toByte(), 0x21),
                "\u00a012\u00a0" to byteArrayOf(0x0f, 0xf2.toByte()),
                "ß" to byteArrayOf(0xef.toByte()),
            )
        for ((text, expected) in vectors) assertContentEquals(expected, Util.hexStringToByteArray(text))
        for (text in listOf(null, "", " ", "1", "123")) assertNull(Util.hexStringToByteArray(text))
    }

    @Test fun `tolerant decoder strips non ASCII characters and preserves legacy null failure`() {
        assertContentEquals(byteArrayOf(0x21, 0xba.toByte()), Util.tolerantHexStringToByteArray("１２ : 12-ab!?"))
        assertNull(Util.tolerantHexStringToByteArray("１２"))
        assertFailsWith<NullPointerException> { Util.tolerantHexStringToByteArray(null) }
    }

    @Test fun `every byte value retains swapped hex decoding and normal hex encoding`() {
        val digits = "0123456789ABCDEF"
        for (high in 0..15) {
            for (low in 0..15) {
                val text = "${digits[high]}${digits[low]}"
                assertContentEquals(byteArrayOf(((low shl 4) or high).toByte()), Util.hexStringToByteArray(text))
                assertEquals(text, Util.bytesToHex(byteArrayOf(((high shl 4) or low).toByte())))
            }
        }
        assertEquals("", Util.bytesToHex(null))
        assertEquals("", Util.bytesToHex(byteArrayOf()))
    }

    @Test fun `array helpers copy content without padding an oversized reduction`() {
        val source = byteArrayOf(1, 2)
        val appended = Util.arrayAppend(source, byteArrayOf(3))
        val reduced = assertNotNull(Util.arrayReduce(source, 1))
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), appended)
        assertContentEquals(byteArrayOf(1), reduced)
        assertContentEquals(byteArrayOf(), Util.arrayReduce(source, 0))
        assertNull(Util.arrayReduce(null, 2))
        assertFailsWith<IndexOutOfBoundsException> { Util.arrayReduce(source, 3) }
        assertFailsWith<NegativeArraySizeException> { Util.arrayReduce(source, -1) }
    }

    @Test fun `integer length encoding retains sign and big endian ordering`() {
        assertContentEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), Util.intToByteArray(0x12345678))
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), Util.intToByteArray(-1))
    }

    @Test fun `packet buffer lazily allocates one fixed width value per key`() {
        val buffers = ByteArrayHashMap()
        val first = buffers.mget(1)
        assertEquals(32, first.size)
        first[0] = 7
        assertSame(first, buffers.mget(1))
        assertEquals(0, buffers.mget(2)[0].toInt())
        buffers[3] = null
        assertEquals(32, buffers.mget(3).size)
        assertEquals(32, buffers.mget(null).size)
    }

    @Test fun `base message preserves opcode little endian writes and array alias`() {
        class Probe : BaseMessage() {
            fun encode(): ByteArray {
                init(0x0b, 5)
                assertNotNull(data).putInt(0x12345678)
                return getByteSequence()
            }
        }
        val probe = Probe()
        assertNull(probe.data)
        assertNull(probe.byteSequence)
        val encoded = probe.encode()
        assertContentEquals(byteArrayOf(0x0b, 0x78, 0x56, 0x34, 0x12), encoded)
        assertEquals(ByteOrder.LITTLE_ENDIAN, assertNotNull(probe.data).order())
        assertSame(encoded, probe.byteSequence)
    }

    @Test fun `auth challenge retains short input zero padding and overflow behavior`() {
        assertContentEquals(byteArrayOf(4, 1, 2, 0, 0, 0, 0, 0, 0), AuthChallengeTxMessage(byteArrayOf(1, 2)).byteSequence)
        assertFailsWith<java.nio.BufferOverflowException> { AuthChallengeTxMessage(ByteArray(9)) }
    }

    @Test fun `auth request constructors preserve slots token bytes and signed challenge offset`() {
        val standard = AuthRequestTxMessage2(8)
        assertEquals(10, assertNotNull(standard.byteSequence).size)
        assertContentEquals(standard.singleUseToken, assertNotNull(standard.byteSequence).copyOfRange(1, 9))
        assertEquals(2, assertNotNull(standard.byteSequence).last().toInt())
        assertEquals(1, assertNotNull(AuthRequestTxMessage2(8, true, byteArrayOf()).byteSequence).last().toInt())
        assertEquals(0, assertNotNull(AuthRequestTxMessage2(8, true, byteArrayOf(0, 0, -1)).byteSequence).last().toInt())
        assertEquals(7, assertNotNull(AuthRequestTxMessage2(8, 7).byteSequence).last().toInt())
        assertFailsWith<IndexOutOfBoundsException> { AuthRequestTxMessage2(17) }
    }

    @Test fun `sign challenge retains explicit bytes padding getter and random constructor shape`() {
        val challenge = byteArrayOf(1, 2)
        val message = SignChallengeTxMessage(challenge)
        assertSame(challenge, message.getChallengeHash())
        assertEquals(17, assertNotNull(message.byteSequence).size)
        assertContentEquals(byteArrayOf(0x0c, 1, 2), assertNotNull(message.byteSequence).copyOfRange(0, 3))
        assertTrue(assertNotNull(message.byteSequence).drop(3).all { it == 0.toByte() })
        assertFailsWith<java.nio.BufferOverflowException> { SignChallengeTxMessage(ByteArray(17)) }
        val random = SignChallengeTxMessage()
        assertEquals(16, random.getChallengeHash().size)
        assertContentEquals(random.getChallengeHash(), assertNotNull(random.byteSequence).copyOfRange(1, 17))
    }

    @Test fun `status retains signed raw flags and ignores wrong opcode`() {
        val status = AuthStatusRxMessage(byteArrayOf(5, -1, -128, 9))
        assertEquals(-1, status.authenticated)
        assertEquals(-128, status.bonded)
        assertFalse(status.isAuthenticated)
        assertFalse(status.isBonded)
        val ignored = AuthStatusRxMessage(byteArrayOf(4, 1, 1))
        assertEquals(0, ignored.authenticated)
        assertNull(ignored.data)
    }

    @Test fun `certificate info retains exact length signed fields and little endian size`() {
        val valid = CertInfoRxMessage(byteArrayOf(0x0b, 0, 1, 2, 1, 0, 0))
        assertTrue(valid.valid())
        assertEquals(258, valid.size)
        assertEquals(1, valid.which)
        assertEquals(0, valid.state)
        val signed = CertInfoRxMessage(byteArrayOf(0x0b, -1, -1, -1, -1, 0, 0))
        assertEquals(-1, signed.size)
        assertEquals(-1, signed.which)
        assertEquals(-1, signed.state)
        assertFalse(signed.valid())
        for (bytes in listOf(byteArrayOf(0x0b), byteArrayOf(0x0b, 0, 1, 2, 1, 0, 0, 0), byteArrayOf(0x0a, 0, 1, 2, 1, 0, 0))) {
            assertFalse(CertInfoRxMessage(bytes).valid())
        }
    }
}
