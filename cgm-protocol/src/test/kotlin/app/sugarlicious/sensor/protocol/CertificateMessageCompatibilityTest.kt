package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.engine.Context
import app.sugarlicious.sensor.auth.engine.Plugin
import app.sugarlicious.sensor.auth.engine.message.CertInfoTxMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CertificateMessageCompatibilityTest {
    @Test fun `certificate requests preserve little endian length and wrapper selection`() {
        val plugin = Plugin("1234")
        plugin.setPersistence(8, ByteArray(258))
        plugin.setPersistence(9, ByteArray(65_538))
        assertContentEquals(byteArrayOf(0x0b, 0, 2, 1, 0, 0), CertInfoTxMessage.expectMyCert1(plugin))
        assertContentEquals(byteArrayOf(0x0b, 1, 2, 0, 1, 0), CertInfoTxMessage.expectMyCert2(plugin))
    }

    @Test fun `only integer selector zero selects certificate A before byte narrowing`() {
        val plugin = Plugin("1234")
        plugin.setPersistence(8, ByteArray(1))
        plugin.setPersistence(9, ByteArray(2))
        assertContentEquals(byteArrayOf(0x0b, 0, 2, 0, 0, 0), CertInfoTxMessage.expectMyCert(plugin, 256))
        assertContentEquals(byteArrayOf(0x0b, -1, 2, 0, 0, 0), CertInfoTxMessage.expectMyCert(plugin, -1))
        assertEquals(Byte::class.javaPrimitiveType, CertInfoTxMessage::class.java.getField("opcode").type)
    }

    @Test fun `context getter returns the same initialized instance and persistence material`() {
        val plugin = Plugin("1234")
        val context = plugin.context
        assertSame(context, plugin.context)
        assertEquals("1234", context.password)
        val material = byteArrayOf(1, 2, 3)
        plugin.setPersistence(8, material)
        assertSame(material, context.partA)
    }

    @Test fun `explicit context accessors retain nullable fields and array aliasing`() {
        val context = Context()
        assertNull(context.partA)
        assertNull(context.partB)
        assertNull(context.partC)
        val a = byteArrayOf(1)
        val b = byteArrayOf(2)
        val c = byteArrayOf(3)
        context.partA = a
        context.partB = b
        context.partC = c
        assertSame(a, context.partA)
        assertSame(b, context.partB)
        assertSame(c, context.partC)
        context.partA = null
        context.partB = null
        context.partC = null
        assertNull(context.partA)
        assertNull(context.partB)
        assertNull(context.partC)
    }

    @Test fun `missing selected material still fails rather than emitting a fabricated certificate length`() {
        val plugin = Plugin("1234")
        assertFailsWith<NullPointerException> { CertInfoTxMessage.expectMyCert1(plugin) }
        assertFailsWith<NullPointerException> { CertInfoTxMessage.expectMyCert2(plugin) }
    }
}
