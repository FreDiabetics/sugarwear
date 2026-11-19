package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.crypto.Digest
import app.sugarlicious.sensor.auth.engine.util.Util
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class HelperMigrationCompatibilityTest {
    @Test fun `coordinate codec retains unsigned full width coordinates without point normalization`() {
        val point = Curve.G
        val codec = JECPoint(point)
        assertSame(point, codec.point)
        val bytes = codec.toBytes()
        assertEquals(
            "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296" +
                "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5",
            Util.bytesToHex(bytes),
        )
        assertEquals(point.normalize(), JECPoint.pointFromBytes(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 64)).normalize())
    }

    @Test fun `coordinate wrapper retains nullable getter and failure at serialization`() {
        val codec = JECPoint(null)
        assertNull(codec.point)
        assertFailsWith<NullPointerException> { codec.toBytes() }
    }

    @Test fun `digest retains incremental input snapshot and destination prefix behavior`() {
        val destination = ByteArray(8)
        val digest = Digest(destination)
        val first = byteArrayOf(0x61)
        digest.update(first)
        first[0] = 0
        digest.update(byteArrayOf(0x62, 0x63))
        digest.doFinal()
        assertEquals("BA7816BF8F01CFEA", Util.bytesToHex(destination))
        digest.doFinal()
        assertEquals("BA7816BF8F01CFEA", Util.bytesToHex(destination))
    }

    @Test fun `digest retains empty zero length nullable and oversized destination contracts`() {
        val destination = ByteArray(32)
        Digest(destination).doFinal()
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", Util.bytesToHex(destination))
        Digest(byteArrayOf()).doFinal()
        assertFailsWith<NullPointerException> { Digest(null).doFinal() }
        assertFailsWith<IndexOutOfBoundsException> { Digest(ByteArray(33)).doFinal() }
    }

    @Test fun `key pair explicit constructor and getters retain supplied objects`() {
        val scalar = BigInteger.ONE
        val point = Curve.G
        val keys = KeyPair(scalar, point)
        assertSame(scalar, keys.privateKey)
        assertSame(scalar, keys.getPrivateKey())
        assertSame(point, keys.publicKey)
        assertSame(point, keys.getPublicKey())
    }

    @Test fun `packet constructor getters short frames and trailing bytes remain compatible`() {
        val hash = BigInteger.ONE
        val point = Curve.G
        val packet = Packet(hash, point, point)
        assertSame(hash, packet.getHash())
        assertSame(point, packet.getPublicKeyPoint1())
        assertSame(point, packet.getPublicKeyPoint2())
        assertNull(Packet.parse(ByteArray(159)))
        assertContentEquals(packet.output(), assertNotNull(Packet.parse(packet.output() + byteArrayOf(1, 2))).output())
    }

    @Test fun `authentication mismatch retains its security exception contract`() {
        val error = AuthenticationMismatchException()
        assertIs<SecurityException>(error)
        assertEquals("Mismatch - wait", error.message)
    }
}
