package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.protocol.AuthChannel
import app.sugarlicious.sensor.protocol.AuthError
import app.sugarlicious.sensor.protocol.AuthStep
import java.math.BigInteger
import java.util.Base64
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TypedCoreTransitionTest {
    @Test fun `native typed proof rounds retain extra before authentication order`() {
        val fixture =
            Properties().apply {
                TypedCoreTransitionTest::class.java.getResourceAsStream("/auth-reference/java-peer.properties").use { load(assertNotNull(it)) }
            }
        val plugin = Plugin("1234")
        plugin.context.keyA = KeyPair(BigInteger.ONE, Curve.G)
        plugin.context.KeyB = KeyPair(BigInteger.TWO, Curve.G.multiply(BigInteger.TWO).normalize())
        plugin.amConnected()
        plugin.nextStep()
        for (round in listOf("round1", "round2", "round3")) {
            plugin.receivedData(Base64.getDecoder().decode(fixture.getProperty(round)))
            val step = assertIs<AuthStep.Send>(plugin.nextStep())
            assertEquals(listOf(AuthChannel.EXTRA, AuthChannel.AUTHENTICATION), step.writes.map { it.channel })
            assertEquals(
                160,
                step.writes
                    .first()
                    .payload
                    .copyBytes()
                    .size,
            )
        }
    }

    @Test fun `state machine directly emits typed challenge and completion steps`() {
        val plugin = Plugin("1234")
        plugin.setPersistence(2, ByteArray(16) { it.toByte() })
        plugin.amConnected()
        val request = assertIs<AuthStep.Send>(plugin.nextStep())
        assertEquals(AuthChannel.AUTHENTICATION, request.writes.single().channel)
        plugin.receivedResponse(byteArrayOf(3) + ByteArray(8) + ByteArray(8) { it.toByte() })
        val challenge = assertIs<AuthStep.Send>(plugin.nextStep())
        assertContentEquals(
            byteArrayOf(4, 0x3d, 0xfb.toByte(), 0xdc.toByte(), 0xc4.toByte(), 0x10, 0xda.toByte(), 0x6b, 0x0b),
            challenge.writes
                .single()
                .payload
                .copyBytes(),
        )
        plugin.receivedResponse(byteArrayOf(5, 1, 1))
        assertContentEquals(byteArrayOf(0x4e), assertIs<AuthStep.Complete>(plugin.nextStep()).command.copyBytes())
        assertEquals(AuthStep.Failed(AuthError.LegacyEngineUnavailable), plugin.nextStep())
    }

    @Test fun `state machine directly emits typed bond failure without losing recovery command`() {
        val plugin = Plugin("1234")
        plugin.setPersistence(2, ByteArray(16))
        plugin.amConnected()
        plugin.nextStep()
        plugin.receivedResponse(byteArrayOf(3) + ByteArray(16))
        plugin.nextStep()
        plugin.receivedResponse(byteArrayOf(5, 0, 0))
        assertContentEquals(byteArrayOf(0x4e, 0x0a, 0xa9.toByte()), assertIs<AuthStep.BondRequired>(plugin.nextStep()).command.copyBytes())
    }
}
