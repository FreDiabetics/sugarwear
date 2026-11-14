package app.sugarlicious.datasource.therapybridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TherapyBridgeTargetParserTest {
    @Test
    fun `parses effective targetBG from APS payload`() {
        assertEquals(
            100.0,
            requireNotNull(TherapyBridgeTargetParser.parse("{\"targetBG\":100,\"predBGs\":{\"IOB\":[120,115]}}")),
            0.0,
        )
    }

    @Test
    fun `parses temp target value without inventing expiry`() {
        assertEquals(
            140.0,
            requireNotNull(TherapyBridgeTargetParser.parse("{\"targetBG\":140,\"temporary\":true}")),
            0.0,
        )
        val parsed = requireNotNull(TherapyBridgeTargetParser.parseTarget("{\"targetBG\":140,\"temporary\":true}"))
        assertEquals(true, parsed.temporary)
        assertEquals(
            false,
            requireNotNull(TherapyBridgeTargetParser.parseTarget("{\"targetBG\":140,\"reason\":\"active temp target\"}")).temporary,
        )
        assertEquals(false, requireNotNull(TherapyBridgeTargetParser.parseTarget("{\"targetBG\":100}")).temporary)
    }

    @Test
    fun `rejects missing malformed or implausible targets`() {
        assertNull(TherapyBridgeTargetParser.parse(null))
        assertNull(TherapyBridgeTargetParser.parse("not-json"))
        assertNull(TherapyBridgeTargetParser.parse("{\"targetBG\":5}"))
    }
}
