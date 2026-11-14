package app.sugarlicious.tools.maintenance.watchface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Source contracts preserved from test_native_watchface_rendering.py. */
class NativeWatchfaceRenderingTest {
    private val root = watchfaceRepositoryRoot()

    private fun face(family: String) = parseXml(root.resolve("watchfaces/sugarlicious-$family/src/main/res/raw/watchface.xml"))

    @Test fun handsRemainOutsideComplicationClipping() {
        listOf("analog" to "7", "orbit" to "8").forEach { (family, id) ->
            val xml = face(family)
            assertEquals(8, xml.all("Scene//ComplicationSlot").size)
            val slot = xml.need("Scene/ComplicationSlot[@slotId='$id']")
            assertEquals("LONG_TEXT", slot.need("DefaultProviderPolicy").attr("primaryProviderType"))
            assertNotNull(xml.one("Scene/Group//HourHand"))
            assertNull(slot.one(".//HourHand"))
            assertEquals(listOf("91", "91", "330", if (family == "analog") "97" else "165"), listOf("x", "y", "width", "height").map { slot.need("BoundingBox").attr(it) })
            xml.all(".//Group[@name='fixed-gradient-content']").forEach { fixed ->
                assertTrue(fixed.all(".//Rectangle").isEmpty())
                assertTrue(fixed.all("PartDraw/Arc/Stroke/SweepGradient").isNotEmpty())
                fixed.all("PartDraw/Arc").forEach { assertEquals("ROUND", it.need("Stroke").attr("cap")) }
            }
        }
    }

    @Test fun originalSlotsAndMiddleGaugesStayStable() {
        val apex = face("analog")
        assertEquals(listOf("7", "0", "1", "2", "3", "4", "5", "6"), apex.all("Scene/ComplicationSlot").map { it.attr("slotId") })
        listOf("4", "5", "6").forEach { id ->
            assertFalse(apex.need("Scene/ComplicationSlot[@slotId='$id']").all(".//Group").any { it.attr("name").startsWith("progress-marker-") })
        }
        val arc = face("orbit").need(".//Group[@name='round-gradient']/Group[@name='fixed-gradient-content']/PartDraw/Arc")
        assertEquals("CLOCKWISE", arc.attr("direction"))
        assertEquals(8.0, arc.attr("startAngle").toDouble())
    }

    @Test fun roundCapsAndApexGraphOnlyPolicyRemain() {
        listOf("analog", "orbit").forEach { family ->
            val xml = face(family)
            xml.all(".//Group[@name='fixed-gradient-content']").forEach { fixed ->
                listOf("start", "end").forEach { end ->
                    assertNotNull(fixed.one("PartDraw[@name='$end-rounded-cap-base']/Ellipse/Fill"))
                    assertNotNull(fixed.one("PartDraw[@name='$end-rounded-cap-light']/Ellipse/Fill"))
                }
            }
            if (family == "analog") {
                assertEquals("FALSE", xml.need("Scene/ComplicationSlot[@slotId='7']").attr("isCustomizable"))
                listOf("arcContent", "upperMode").forEach { assertNull(xml.one("UserConfigurations/ListConfiguration[@id='$it']")) }
            }
        }
    }

    @Test fun lowerIconAndLiveRangeGlyphPaddingRemain() {
        val xml = face("analog")
        val short = xml.need("Scene/ComplicationSlot[@slotId='3']/Complication[@type='SHORT_TEXT']/PartImage")
        assertEquals(70.0, short.need("Transform[@target='x']").attr("value").toDouble() + short.attr("width").toDouble() / 2)
        assertEquals(320.0, short.need("Transform[@target='y']").attr("value").toDouble() + short.attr("height").toDouble() / 2)
        listOf("0", "1", "2", "3").forEach { id ->
            val slot = xml.need("Scene/ComplicationSlot[@slotId='$id']")
            assertEquals("5.640898", slot.need(".//Group[@name='progress-marker-$id']//Stroke").attr("thickness"))
            val image = slot.need("Complication[@type='RANGED_VALUE']/PartImage")
            assertEquals(22.0, image.attr("width").toDouble())
            val value = image.need("Transform[@target='width']").attr("value")
            assertTrue("[COMPLICATION.RANGED_VALUE_MAX] == 260" in value)
            assertTrue("52.0000" in value)
            assertFalse("[COMPLICATION.RANGED_VALUE_MAX] == 10" in value)
        }
    }

    @Test fun centerTapUsesReferenceWithoutMaskingHands() {
        listOf("analog", "orbit").forEach { family ->
            val xml = face(family)
            val tap = xml.need("Scene/PartDraw[@name='native-hands-center-tap']")
            assertEquals(listOf("230", "230", "52", "52"), listOf("x", "y", "width", "height").map(tap::attr))
            assertNotNull(tap.one("Launch"))
            val reference = xml.need("Scene/ComplicationSlot/Complication/Group[@name='native-hands-opacity-state']/Reference")
            assertEquals("nativeHandsOpacity", reference.attr("name"))
            assertEquals("alpha", reference.attr("source"))
            val hands = xml.all("Scene/Group").first { it.attr("name") in setOf("hands", "hands-and-indicators") }
            assertEquals("[REFERENCE.nativeHandsOpacity]", hands.need("Transform[@target='alpha']").attr("value"))
            assertNotNull(hands.one(".//HourHand"))
            assertNull(hands.one(".//*[@renderMode='MASK']"))
        }
    }

    @Test fun nativeProvidersStayHiddenAndPublicGraphsRemainImages() {
        val manifest = parseXml(root.resolve("app-wear/src/main/AndroidManifest.xml"))
        val services = manifest.all("application/service").associateBy { it.attr("android:name") }
        listOf("Apex", "Orbit", "GlucoseGraph", "OrbitGlucoseGraph").forEach { family ->
            val native = family in setOf("Apex", "Orbit")
            val name = if (native) "Native${family}GraphComplication" else "${family}Complication"
            val metadata = services.getValue("app.sugarlicious.complications.$name").all("meta-data").associate { it.attr("android:name") to it.attr("android:value") }
            if (native) assertEquals("true", metadata["android.support.wearable.complications.HIDDEN"])
            assertEquals(if (native) "LONG_TEXT" else "SMALL_IMAGE", metadata["android.support.wearable.complications.SUPPORTED_TYPES"])
        }
    }
}
