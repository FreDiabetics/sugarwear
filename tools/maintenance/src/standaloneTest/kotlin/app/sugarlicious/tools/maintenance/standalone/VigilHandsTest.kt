package app.sugarlicious.tools.maintenance.standalone

import app.sugarlicious.tools.maintenance.watchface.*
import org.w3c.dom.Element
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.*
import kotlin.test.*

class VigilHandsTest {
    private val root = Path.of(System.getProperty("maintenance.repositoryRoot"))
    private val shared = Path.of(System.getProperty("maintenance.sharedRoot"))
    private val faceRoot = root.resolve("watchfaces/vigil")
    private val face by lazy { parseXml(faceRoot.resolve("src/main/res/raw/watchface.xml")) }

    private fun slot(id: Any) = face.need(".//ComplicationSlot[@slotId='$id']")

    private fun bounds(element: Element) = listOf("x", "y", "width", "height").map { element.attr(it) }

    @Test fun openAppActionIsLocalNativeLaunchNotFullFaceComplication() {
        val action = face.need("Scene/Group[@name='vigil-open-sugarwear']")
        assertEquals("300", action.attr("width"))
        assertEquals("108", action.attr("height"))
        assertEquals("app.sugarwear/app.sugarwear.CgmWatchActivity", action.need("Launch").attr("target"))
    }

    @Test fun analogTextAndPairingButtonRenderBelowNativeHands() {
        val ids = face.need("Scene").all("ComplicationSlot").map { it.attr("slotId") }
        assertTrue(ids.indexOf("9") < ids.indexOf("7"))
    }

    @Test fun scaleAgeTextIsInsetInsideCurrentGraphSurface() {
        assertEquals(40, slot(2).attr("x").toInt())
        assertEquals(146, slot(2).attr("y").toInt())
    }

    @Test fun analogPreviewKeepsSquareProviderArtInBothMarkerLayouts() {
        slot(9).all(".//PartImage").forEach {
            assertEquals("450", it.attr("width"))
            assertEquals("450", it.attr("height"))
        }
    }

    @Test fun providerClockKeepsFullRenderingAndCenterTapSeparateWithEightSlots() {
        val scene = face.need("Scene")
        val slots = scene.all(".//ComplicationSlot")
        assertEquals(8, slots.size)
        assertTrue(slots.all { it.attr("isCustomizable") == "FALSE" })
        val hands = slot(7)
        val center = slot(8)
        assertSame(center, scene.children().last())
        assertEquals(8, scene.all("ComplicationSlot").size)
        assertEquals(listOf("0", "0", "450", "450"), bounds(hands))
        assertEquals(listOf("0", "0", "450", "450"), bounds(hands.need("BoundingBox")))
        assertEquals(listOf("200", "200", "50", "50"), bounds(center))
        assertEquals(listOf("0", "0", "50", "50"), bounds(center.need("BoundingOval")))
        assertNull(scene.one("AnalogClock"))
        assertEquals("ANALOG", face.need("Metadata[@key='CLOCK_TYPE']").attr("value"))
        val build = shared.resolve("complications/build.gradle.kts").readText()
        listOf("hour_hand_transparent", "minute_hand_transparent", "second_hand_transparent").forEach { resource ->
            assertContains(build, "\"$resource.png\"")
            assertTrue(shared.resolve("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/$resource.png").isRegularFile())
        }
    }

    @Test fun digitalClockAndHandsBothSupplyActiveAndAmbientImages() {
        assertNull(face.one("Scene/ComplicationSlot[@slotId='6']"))
        listOf(0, 1, 5).forEach { id ->
            val s = slot(id)
            assertEquals(listOf("[COMPLICATION.SMALL_IMAGE]", "[COMPLICATION.SMALL_IMAGE_AMBIENT]"), s.all(".//PartImage/Image").map { it.attr("resource") })
            val parts = s.all(".//PartImage")
            assertEquals("0", parts[0].need("Variant[@mode='AMBIENT']").attr("value"))
            assertEquals("255", parts[1].need("Variant[@mode='AMBIENT']").attr("value"))
        }
    }

    @Test fun headerAndGraphActionMirrorsRestoreTapsAboveHands() {
        val slots = face.need("Scene").all("ComplicationSlot")
        assertEquals(listOf("0", "1", "2", "5", "9", "7", "10", "8"), slots.map { it.attr("slotId") })
        assertEquals(listOf("0", "140", "450", "285"), bounds(slot(1)))
        assertEquals(listOf("0", "140", "450", "205"), bounds(slot(10)))
        val analog = slot(9)
        assertEquals(listOf("0", "0", "450", "450"), bounds(analog))
        assertContains(analog.need("DefaultProviderPolicy").attr("primaryProvider"), "DirectToWatchAnalogHeaderComplication")
        assertEquals("[CONFIGURATION.showHands] == \"TRUE\"", analog.need(".//Expression").textContent)
    }

    @Test fun nativeVisibilitySwitchHidesHandsAndCenterContent() {
        assertEquals("FALSE", face.need("UserConfigurations/BooleanConfiguration[@id='showHands']").attr("defaultValue"))
        listOf(7, 8).forEach { id ->
            val condition = slot(id).need("Complication/Group/Condition")
            assertEquals("[CONFIGURATION.showHands] == \"TRUE\"", condition.need("Expressions/Expression").textContent)
            assertNull(condition.one("Default"))
        }
        assertEquals("true", parseXml(faceRoot.resolve("src/main/res/xml/watch_face_info.xml")).need("Editable").attr("value"))
    }

    @Test fun digitalTimeCanBeHiddenInBothDisplayModes() {
        assertEquals("TRUE", face.need("UserConfigurations/BooleanConfiguration[@id='showDigitalTime']").attr("defaultValue"))
        val condition = slot(5).need("Complication/Condition")
        assertEquals("[CONFIGURATION.showDigitalTime] == \"TRUE\" && [CONFIGURATION.showHands] == \"FALSE\"", condition.need("Expressions/Expression").textContent)
        assertEquals(2, condition.all("Compare/Group/PartImage").size)
        assertNull(condition.one("Default"))
    }

    @Test fun hourMarkersHaveIndependentVisibilitySwitch() {
        assertNotNull(face.one("UserConfigurations/BooleanConfiguration[@id='showHourMarkers']"))
        assertEquals("[CONFIGURATION.showHourMarkers] == \"TRUE\"", face.need(".//Expression[@name='hourVisible']").textContent)
    }

    @Test fun minuteMarkersHaveIndependentVisibilitySwitch() {
        assertNotNull(face.one("UserConfigurations/BooleanConfiguration[@id='showMinuteMarkers']"))
        assertEquals("[CONFIGURATION.showMinuteMarkers] == \"TRUE\"", face.need(".//Expression[@name='minuteVisible']").textContent)
    }

    @Test fun markersPreserveUserArtworkAndFollowHandVisibility() {
        val markers = face.need("Scene/Group[@name='vigil-perimeter-markers']")
        assertEquals("[CONFIGURATION.showHands] == \"TRUE\"", markers.need("Condition/Expressions/Expression").textContent)
        assertNull(markers.one(".//PartDraw"))
        val images = markers.all(".//PartImage")
        assertEquals(listOf("vigil_minutemarkers_close", "vigil_minutemarkers_wide", "vigil_hourmarkers_close", "vigil_hourmarkers_wide"), images.map { it.need("Image").attr("resource") })
        images.forEach { image ->
            assertEquals(listOf("0", "0", "450", "450"), bounds(image))
            val resource = image.need("Image").attr("resource")
            assertEquals("0 0 450 450", parseXml(root.resolve("design-assets/vigil/$resource.svg")).attr("viewBox"))
        }
    }

    @Test fun markerGrayscaleOptionsStartWithOriginalAndHaveNoHues() {
        listOf("minute", "hour").forEach { kind ->
            val options = face.all("UserConfigurations/ColorConfiguration[@id='${kind}MarkerColor']/ColorOption")
            assertEquals("original", options[0].attr("id"))
            assertEquals("#FFFFFFFF", options[1].attr("colors"))
            assertEquals("#FF000000", options.last().attr("colors"))
            options.forEach {
                val color = it.attr("colors")
                assertEquals(color.substring(3, 5), color.substring(5, 7))
                assertEquals(color.substring(5, 7), color.substring(7, 9))
            }
        }
    }

    @Test fun bottomHourMarkerIsHiddenInBothVariants() {
        listOf("close", "wide").forEach { variant ->
            val image = ImageIO.read(faceRoot.resolve("src/main/res/drawable-nodpi/vigil_hourmarkers_$variant.png").toFile())
            for (y in 400 until 450) for (x in 219 until 232) assertEquals(0, image.getRGB(x, y).ushr(24), "$variant ($x,$y)")
        }
    }

    @Test fun nativeSecondsShareCenterOpacityMaskAndOfferRedOrMediumGray() {
        val color = face.need("UserConfigurations/ColorConfiguration[@id='secondColor']")
        assertEquals("red", color.attr("defaultValue"))
        assertEquals(listOf("#FFFF3B30", "#FF9E9E9E"), color.children().map { it.attr("colors") })
        val group = face.need(".//Group[@name='vigil-native-hands']")
        assertEquals("160", group.need("Variant[@mode='AMBIENT']").attr("value"))
        assertEquals("[COMPLICATION.SMALL_IMAGE]", group.need("PartImage[@renderMode='MASK']/Image").attr("resource"))
        assertEquals("TRUE", face.need("UserConfigurations/BooleanConfiguration[@id='showSecondHand']").attr("defaultValue"))
        assertEquals("[CONFIGURATION.showSecondHand] == \"TRUE\"", group.need("Condition/Expressions/Expression").textContent)
        val hands = group.all(".//AnalogClock/SecondHand")
        assertEquals(2, hands.size)
        assertEquals("[CONFIGURATION.secondColor.0]", hands.last().attr("tintColor"))
        assertTrue(hands.all { it.one("Sweep") != null })
        assertTrue(group.all(".//AnalogClock").filter { it.one("SecondHand") != null }.all { it.need("Variant[@mode='AMBIENT']").attr("value") == "0" })
    }
}
