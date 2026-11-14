package app.sugarlicious.tools.maintenance.assets

import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class OrbitHandsetTest {
    @Test fun `current authored outlines match installed resources without an obsolete accent`() {
        val root = Path.of(System.getProperty("maintenance.sharedRoot"))
        val canonical = root.resolve("watchfaces/sugarlicious-orbit/src/main/res")
        val resources = Files.createTempDirectory("orbit-current-hands")
        copy(canonical.resolve("raw/watchface.xml"), resources.resolve("raw/watchface.xml"))
        copy(canonical.resolve("values/strings.xml"), resources.resolve("values/strings.xml"))
        importOrbitHandset(root.resolve("design-assets/watchfaces/orbit/hands_4"), resources)
        listOf("hour", "minute").forEach { hand ->
            listOf("", "_accent").forEach { suffix ->
                val name = "drawable-nodpi/orbit_custom_${hand}_4$suffix.png"
                val expected = ImageIO.read(resources.resolve(name).toFile())
                val actual = ImageIO.read(canonical.resolve(name).toFile())
                assertEquals(512, actual.width)
                assertEquals(512, actual.height)
                assertContentEquals(expected.getRGB(0, 0, 512, 512, null, 0, 512), actual.getRGB(0, 0, 512, 512, null, 0, 512))
                if (suffix.isNotEmpty()) {
                    assertTrue((0 until 512).all { y -> (0 until 512).all { x -> actual.getRGB(x, y) ushr 24 == 0 } })
                } else {
                    assertEquals(0xffffffff.toInt(), actual.getRGB(243, 150))
                    assertEquals(0, actual.getRGB(256, 150))
                }
            }
        }
    }

    @Test fun `fourth handset preserves full canvas and separates white tint layers`() {
        val root = Path.of(System.getProperty("maintenance.sharedRoot"))
        val resources = Files.createTempDirectory("orbit-handset")
        copy(root.resolve("watchfaces/sugarlicious-orbit/src/main/res/raw/watchface.xml"), resources.resolve("raw/watchface.xml"))
        copy(root.resolve("watchfaces/sugarlicious-orbit/src/main/res/values/strings.xml"), resources.resolve("values/strings.xml"))
        importOrbitHandset(root.resolve("tools/maintenance/src/test/resources/orbit-handset-tinted"), resources)
        val first = Files.readAllBytes(resources.resolve("raw/watchface.xml"))
        importOrbitHandset(root.resolve("tools/maintenance/src/test/resources/orbit-handset-tinted"), resources)
        assertContentEquals(first, Files.readAllBytes(resources.resolve("raw/watchface.xml")))
        val face = parse(resources.resolve("raw/watchface.xml")).documentElement
        val options =
            face
                .all("ListConfiguration")
                .single { it.getAttribute("id") == "apexHands" }
                .children()
                .filter { it.tagName == "ListOption" }
        assertEquals(listOf("0", "1", "2", "3"), options.map { it.getAttribute("id") })
        val hands = face.all("Group").single { it.getAttribute("name") == "hands-3" }
        val children = hands.children()
        assertTrue(children.indexOfFirst { it.tagName == "AnalogClock" } < children.indexOfFirst { it.getAttribute("name") == "custom-hub-4" })
        assertTrue(children.indexOfFirst { it.getAttribute("name") == "custom-hub-4" } < children.indexOfFirst { it.getAttribute("name") == "seconds-3-0" })
        assertEquals("custom-top-4", children.last().getAttribute("name"))
        val tinted = (hands.all("HourHand") + hands.all("MinuteHand")).filter { it.getAttribute("resource").endsWith("accent") }
        assertEquals(2, tinted.size)
        tinted.forEach { assertEquals("[CONFIGURATION.tertiary.2]", it.getAttribute("tintColor")) }
        val accent = ImageIO.read(resources.resolve("drawable-nodpi/orbit_custom_hour_4_accent.png").toFile())
        assertEquals(512, accent.width)
        assertEquals(-1, accent.getRGB(256, 150))
        val outline = ImageIO.read(resources.resolve("drawable-nodpi/orbit_custom_hour_4.png").toFile())
        assertEquals(0, outline.getRGB(256, 150))
        assertEquals(-1, outline.getRGB(246, 150))
        val second = ImageIO.read(resources.resolve("drawable-nodpi/orbit_custom_second_4.png").toFile())
        assertEquals(-1, second.getRGB(256, 100))
        listOf("hour_4_accent", "minute_4_accent", "second_4").forEach { name ->
            val mask = ImageIO.read(resources.resolve("drawable-nodpi/orbit_custom_$name.png").toFile())
            assertTrue(
                (0 until mask.height).all { y ->
                    (0 until mask.width).all { x ->
                        val pixel = mask.getRGB(x, y)
                        pixel ushr 24 == 0 || pixel and 0xffffff == 0xffffff
                    }
                },
                "$name must be a white tint mask",
            )
        }
        assertEquals(if (face.all("ListConfiguration").any { it.getAttribute("id") == "secondsMotion" }) 6 else 3, hands.all("SecondHand").size)
        hands.all("SecondHand").forEach { secondHand ->
            assertEquals("0.5", secondHand.getAttribute("pivotX"))
            assertEquals("0.5", secondHand.getAttribute("pivotY"))
            assertTrue(secondHand.children().any { it.tagName == "Variant" && it.getAttribute("mode") == "AMBIENT" && it.getAttribute("value") == "0" })
        }
        assertEquals(300, ImageIO.read(resources.resolve("drawable-nodpi/orbit_hand_preview_18.png").toFile()).width)
    }
}
