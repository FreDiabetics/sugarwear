package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetCatalogContractTest {
    @Test
    fun `picker headings have no redundant provider description subtitle`() {
        File("src/main/res/xml").listFiles()!!.filter { it.name.endsWith("_info.xml") }.forEach {
            assertFalse(it.name, it.readText().contains("android:description="))
        }
    }

    @Test
    fun `compact widget has its own native DeviceVendor card and additional widgets sort last`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("@xml/device_vendor_compact_family"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertEquals(3, Regex(">Zusätzlich<").findAll(strings).count())
        listOf("glucose", "graph").forEach { family ->
            val layout = if (family == "graph") "widget_graph_preview" else "widget_glucose_family_small_preview"
            val preview = File("src/main/res/layout/$layout.xml").readText()
            assertTrue(preview.contains("android:scaleType=\"fitEnd\""))
        }
    }

    @Test
    fun `additional widgets use one visible provider while legacy instances remain supported`() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver")
        listOf("GlucoseFamily2x1Receiver", "GraphFamily2x1Receiver", "TherapyFamily2x1Receiver").forEach { name ->
            val receiver =
                (0 until receivers.length)
                    .map { receivers.item(it) as org.w3c.dom.Element }
                    .single { it.getAttribute("android:name") == ".$name" }
            val entries = receiver.getElementsByTagName("meta-data")
            val providerResource =
                (0 until entries.length)
                    .map { entries.item(it) as org.w3c.dom.Element }
                    .single { it.getAttribute("android:name") == "android.appwidget.provider" }
                    .getAttribute("android:resource")
                    .removePrefix("@xml/")
            val provider =
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(File("src/main/res/xml/$providerResource.xml"))
                    .documentElement
            assertTrue(provider.getAttribute("android:widgetFeatures").contains("hide_from_picker"))
            assertEquals("horizontal|vertical", provider.getAttribute("android:resizeMode"))
            assertFalse(
                (0 until entries.length)
                    .map { entries.item(it) as org.w3c.dom.Element }
                    .any { it.getAttribute("android:name") == "samsung.appwidget.colorful.info" },
            )
        }
        assertEquals(1, Regex("android:name=\".AdditionalWidgetReceiver\"").findAll(File("src/main/AndroidManifest.xml").readText()).count())
        val additional = File("src/main/res/xml/additional_widget_info.xml").readText()
        assertFalse(additional.contains("hide_from_picker"))
        assertTrue(additional.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("@xml/device_vendor_additional_family"))
        val deviceVendor = File("src/main/res/xml/device_vendor_additional_family.xml").readText()
        assertTrue(deviceVendor.contains("app:widgetSize=\"0x02\""))
        assertTrue(deviceVendor.contains("app:previewLayoutSmall="))
    }

    @Test
    fun `one ui colorful metadata exposes the intended family sizes and previews`() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val metadata = manifest.getElementsByTagName("meta-data")
        val deviceVendorResources =
            (0 until metadata.length)
                .map { metadata.item(it) as org.w3c.dom.Element }
                .filter { it.getAttribute("android:name") == "samsung.appwidget.colorful.info" }
                .map { it.getAttribute("android:resource") }
                .toSet()
        listOf("glucose", "graph", "therapy").forEach { family ->
            assertTrue("@xml/device_vendor_${family}_family" in deviceVendorResources)
            val root =
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(File("src/main/res/xml/device_vendor_${family}_family.xml"))
                    .documentElement
            assertEquals("samsung-appwidget-info", root.tagName)
            // One UI transports these values as strings and interprets the mask as hexadecimal.
            assertEquals("0x1a", root.getAttribute("app:widgetSize"))
            assertEquals("0x15", root.getAttribute("app:targetHost"))
            assertTrue(root.getAttribute("app:previewLayoutSmall").startsWith("@layout/"))
            assertTrue(root.getAttribute("app:previewLayoutMedium").startsWith("@layout/"))
        }
    }

    @Test
    fun `adaptive glucose and graph presets are registered with exact target cells`() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver")
        val names =
            (0 until receivers.length).associate { index ->
                val element = receivers.item(index) as org.w3c.dom.Element
                val name = element.getAttribute("android:name")
                val metadata = element.getElementsByTagName("meta-data")
                val resource = if (metadata.length > 0) (metadata.item(0) as org.w3c.dom.Element).getAttribute("android:resource") else ""
                name to resource
            }

        assertEquals("@xml/glucose_family_2x1_info", names[".GlucoseFamily2x1Receiver"])
        assertEquals("@xml/glucose_widget_2x2_info", names[".GlucoseWidget2x2Receiver"])
        assertEquals("@xml/glucose_widget_4x2_info", names[".GlucoseWidget4x2Receiver"])
        assertEquals("@xml/graph_family_2x1_info", names[".GraphFamily2x1Receiver"])
        assertEquals("@xml/graph_widget_2x2_info", names[".GraphWidget2x2Receiver"])
        assertEquals("@xml/graph_widget_4x2_info", names[".GraphWidget4x2Receiver"])

        assertTargetCells("glucose_family_2x1_info.xml", 2, 1)
        assertTargetCells("glucose_widget_2x2_info.xml", 2, 2)
        assertTargetCells("glucose_widget_4x2_info.xml", 4, 2)
        assertTargetCells("graph_family_2x1_info.xml", 2, 1)
        assertTargetCells("graph_widget_2x2_info.xml", 2, 2)
        assertTargetCells("graph_widget_4x2_info.xml", 4, 2)
        assertEquals("@xml/therapy_family_2x1_info", names[".TherapyFamily2x1Receiver"])
        assertEquals("@xml/therapy_family_2x2_info", names[".TherapyFamily2x2Receiver"])
        assertEquals("@xml/therapy_family_4x2_info", names[".TherapyFamily4x2Receiver"])
        assertTargetCells("therapy_family_2x1_info.xml", 2, 1)
        assertTargetCells("therapy_family_2x2_info.xml", 2, 2)
        assertTargetCells("therapy_family_4x2_info.xml", 4, 2)
    }

    @Test
    fun `new families use automatic shape while legacy providers remain developer widgets`() {
        assertFalse(widgetUsesAutomaticShape("app.sugarlicious.mobile.GlucoseFamily2x1Receiver"))
        assertTrue(widgetUsesAutomaticShape("app.sugarlicious.mobile.GlucoseWidget2x2Receiver"))
        assertFalse(widgetUsesAutomaticShape("app.sugarlicious.mobile.GraphFamily2x1Receiver"))
        assertTrue(widgetUsesAutomaticShape("app.sugarlicious.mobile.TherapyFamily2x2Receiver"))
        assertTrue(widgetUsesAutomaticShape("app.sugarlicious.mobile.GlucoseWidgetReceiver"))
        assertTrue(widgetUsesAutomaticShape("app.sugarlicious.mobile.GraphWidgetReceiver"))
        assertTrue(widgetUsesAutomaticShape("app.sugarlicious.mobile.MetabolicWidgetReceiver"))

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".GlucoseWidgetReceiver\" android:label=\"@string/widget_glucose_family_label"))
        assertTrue(manifest.contains(".GraphWidgetReceiver\" android:label=\"@string/widget_graph_family_label"))
        assertTrue(manifest.contains(".MetabolicWidgetReceiver\" android:label=\"@string/widget_therapy_family_label"))
        assertTrue(manifest.contains(".GlucoseGraphWidgetReceiver\" android:label=\"@string/widget_developer_compact_label"))
    }

    private fun assertTargetCells(fileName: String, width: Int, height: Int) {
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/xml/$fileName"))
                .documentElement
        assertEquals(width.toString(), root.getAttribute("android:targetCellWidth"))
        assertEquals(height.toString(), root.getAttribute("android:targetCellHeight"))
    }

    @Test
    fun `activity widget is absent from the product catalog`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val source = File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()

        assertFalse(manifest.contains("ActivityWidgetReceiver"))
        assertFalse(source.contains("class ActivityWidget"))
        assertFalse(source.contains("WidgetKind.ACTIVITY"))
        assertFalse(File("src/main/res/xml/activity_widget_info.xml").exists())
    }

    @Test
    fun `therapy widget reuses mobile presentations and ring geometry`() {
        val source = File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()

        assertTrue(source.contains("therapyIndicatorPresentations("))
        assertTrue(source.contains("TherapyRingGeometry()"))
        assertTrue(source.contains("renderTherapyIndicatorWidget("))
    }

    @Test
    fun `widget background keeps configured alpha and reset color`() {
        assertEquals(0x480A141E, widgetBackgroundArgb(true, 0x480A141E))
        assertEquals(0x00000000, widgetBackgroundArgb(false, 0xFFFF0000.toInt()))
        assertEquals(0x00000000, widgetHostBackgroundArgb())
        assertEquals(0xFF000000.toInt(), WidgetInstanceConfiguration().backgroundArgb)
    }
}
