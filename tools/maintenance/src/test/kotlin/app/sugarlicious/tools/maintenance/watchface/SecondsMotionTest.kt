package app.sugarlicious.tools.maintenance.watchface

import java.nio.file.Path
import kotlin.test.*

class SecondsMotionTest {
    @Test fun `tick choice preserves color off ambient and all four handset choices`() {
        val res = Path.of(System.getProperty("maintenance.sharedRoot")).resolve("watchfaces/sugarlicious-orbit/src/main/res")
        val face = parseXml(res.resolve("raw/watchface.xml"))
        val strings = parseXml(res.resolve("values/strings.xml"))
        configureOrbitSecondsMotion(face, strings)
        configureOrbitSecondsMotion(face, strings)
        val config = face.need("UserConfigurations/ListConfiguration[@id='secondsMotion']")
        assertEquals("0", config.attr("defaultValue"))
        assertEquals(listOf("0", "1"), config.children().map { it.attr("id") })
        val hands = face.need("Scene/Group[@name='hands']")
        val ticks = hands.all(".//SecondHand[Tick]")
        val smooth = hands.all(".//SecondHand[Sweep]")
        assertEquals(12, ticks.size)
        assertEquals(12, smooth.size)
        ticks.forEach { hand ->
            assertEquals("0.1", hand.need("Tick").attr("duration"))
            assertEquals("0.2", hand.need("Tick").attr("strength"))
            assertEquals("0", hand.need("Variant[@mode='AMBIENT']").attr("value"))
            val wrapper = hand.parentNode.parentNode as org.w3c.dom.Element
            assertTrue("secondsMotion" in wrapper.need("Transform").attr("value"))
        }
        assertEquals(4, face.need("UserConfigurations/ListConfiguration[@id='apexHands']").children().size)
        assertEquals(4, face.need("UserConfigurations/ListConfiguration[@id='secondSweep']").children().size)
    }
}
