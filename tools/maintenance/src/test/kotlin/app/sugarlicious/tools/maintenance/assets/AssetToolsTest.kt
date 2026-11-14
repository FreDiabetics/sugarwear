package app.sugarlicious.tools.maintenance.assets

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class AssetToolsTest {
    private val shared = Path.of(System.getProperty("maintenance.sharedRoot"))

    private fun assertGolden(resource: String, actual: Path) {
        val golden = javaClass.getResourceAsStream("/assets/$resource")!!.use { ImageIO.read(it) }
        val image = ImageIO.read(actual.toFile())
        assertEquals(golden.width, image.width)
        assertEquals(golden.height, image.height)
        assertContentEquals(golden.getRGB(0, 0, golden.width, golden.height, null, 0, golden.width), image.getRGB(0, 0, image.width, image.height, null, 0, image.width), "Pixel parity: $resource")
    }

    @Test fun nativePlacementPreservesAllChannelsIncludingTransparentRgb() {
        val strip = BufferedImage(50, 275, BufferedImage.TYPE_INT_ARGB)
        strip.setRGB(3, 7, 0x00ab1234)
        strip.setRGB(49, 274, 0x7f123456)
        val result = nativeHand(strip)
        assertEquals(0x00ab1234, result.getRGB(203, 7))
        assertEquals(0x7f123456, result.getRGB(249, 274))
        assertEquals(0, result.getRGB(199, 7))
        assertEquals(0, result.getRGB(250, 274))
        assertFailsWith<IllegalArgumentException> { nativeHand(BufferedImage(51, 275, BufferedImage.TYPE_INT_ARGB)) }
    }

    @Test fun actualNativeHandStripsAndAccentsRetainSourceGeometry() {
        val output = shared.resolve("tools/maintenance/build/reports/assets-parity/hands")
        handTool(listOf("--output", output.toString()), shared)
        val reference = shared.resolve("watchfaces/sugarlicious-orbit/reference/hands")
        for (style in 0..14) {
            for (kind in listOf("hour", "minute")) {
                val name = "orbit_${kind}_$style.png"
                val original = ImageIO.read(reference.resolve(name).toFile())
                val actual = ImageIO.read(output.resolve(name).toFile())
                assertEquals(450, actual.width)
                assertEquals(450, actual.height)
                val expected =
                    if (original.colorModel.colorSpace.type == java.awt.color.ColorSpace.TYPE_GRAY) {
                        IntArray(50 * 275) { index ->
                            val x = index % 50
                            val y = index / 50
                            val luminance = original.raster.getSample(x, y, 0)
                            val alpha = if (original.colorModel.hasAlpha()) original.raster.getSample(x, y, 1) else 255
                            (alpha shl 24) or (luminance * 0x010101)
                        }
                    } else {
                        original.getRGB(0, 0, 50, 275, null, 0, 50)
                    }
                assertContentEquals(expected, actual.getRGB(200, 0, 50, 275, null, 0, 50), name)
            }
        }
        val accent = ImageIO.read(output.resolve("orbit_hour_14_accent.png").toFile())
        assertEquals(-1, accent.getRGB(220, 98))
        assertEquals(-1, accent.getRGB(229, 161))
        assertEquals(0, accent.getRGB(230, 161))
        assertEquals(300, ImageIO.read(output.resolve("orbit_hand_preview_17.png").toFile()).width)
    }

    @Test fun brandSourcePathAndSafeMotifArePreservedInProductOwnedOutputs() {
        val root = Files.createTempDirectory("brand-import")
        val sources = root.resolve("sources")
        Files.createDirectories(sources)
        val source = shared.resolve("design-assets/app-icons/adaptive108")
        copy(source.resolve("foreground.svg"), sources.resolve("ic_foreground2.svg"))
        copy(source.resolve("background.svg"), sources.resolve("ic_background2.svg"))
        val names = mapOf("app-mobile" to listOf("drawable/ic_launcher_monochrome.xml", "drawable/ic_foreground.xml", "drawable/ic_sugarlicious_monochrome.xml", "drawable/ic_notification.xml", "drawable/ic_notification_outlined.xml", "drawable-anydpi/ic_notification.xml", "drawable-anydpi/ic_notification_outlined.xml"), "app-wear" to listOf("drawable/ic_foreground.xml", "drawable/ic_monochrome_outlined.xml", "drawable/ic_notification.xml"))
        names.forEach { (module, files) ->
            files.forEach { file -> copy(shared.resolve("$module/src/main/res/$file"), root.resolve("$module/src/main/res/$file")) }
            Files.createDirectories(root.resolve("$module/src/main/res/mipmap-anydpi-v26"))
        }
        val preview = shared.resolve("tools/maintenance/build/reports/assets-parity/brand")
        val args = listOf("--source-dir", sources.toString(), "--repository-root", root.toString(), "--preview-dir", preview.toString())
        brandTool("import-adaptive-brand-icons", args, shared)
        brandTool("import-adaptive-brand-icons", args, shared)
        val data =
            parse(source.resolve("foreground.svg"))
                .documentElement
                .children()
                .single { it.localName == "path" }
                .getAttribute("d")
        for (module in names.keys) {
            val fg = parse(root.resolve("$module/src/main/res/drawable/ic_launcher_foreground.xml")).documentElement
            assertEquals("108", fg.getAttributeNS(ANDROID, "viewportWidth"))
            assertEquals(data, fg.all("path").single().getAttributeNS(ANDROID, "pathData"))
            assertEquals(66.0 / 108, fg.child("group").getAttributeNS(ANDROID, "scaleX").toDouble())
            assertContentEquals(Files.readAllBytes(root.resolve("$module/src/main/res/drawable/ic_launcher_foreground.xml")), Files.readAllBytes(root.resolve("$module/src/main/res/drawable/ic_launcher_monochrome.xml")))
        }
        val notification = parse(root.resolve("app-mobile/src/main/res/drawable/ic_notification.xml")).documentElement
        assertEquals(1, notification.all("group").count { it.getAttributeNS(ANDROID, "name") == "source108ToLegacy66" })
        val normalized = notification.all("group").single { it.getAttributeNS(ANDROID, "name") == "source108ToLegacy66" }
        assertEquals(82.0 / 108, normalized.getAttributeNS(ANDROID, "scaleX").toDouble())
        assertEquals("-8", normalized.getAttributeNS(ANDROID, "translateX"))
        copy(root.resolve("app-mobile/src/main/ic_launcher-playstore.png"), preview.resolve("store.png"))
        for (name in listOf("background", "foreground", "combined")) assertGolden("brand/$name-324.png", preview.resolve("sugarlicious-$name.png"))
        assertGolden("brand/store-512.png", preview.resolve("store.png"))
    }

    @Test fun vigilConfigurationIsIdempotentAndKeepsMaskAndNativeHandsTogether() {
        val root = Files.createTempDirectory("vigil-config")
        val path = root.resolve("watchfaces/vigil/src/main/res/raw/watchface.xml")
        Files.createDirectories(path.parent)
        Files.writeString(path, """<WatchFace><Metadata key="PREVIEW_TIME" value="00:00:00"/><UserConfigurations><BooleanConfiguration id="showHands" defaultValue="TRUE"/></UserConfigurations><Scene><ComplicationSlot slotId="7"><Complication type="SMALL_IMAGE"/></ComplicationSlot><ComplicationSlot slotId="8"><BoundingOval/><Complication type="SMALL_IMAGE"/></ComplicationSlot></Scene></WatchFace>""")
        configureVigil(root)
        configureVigil(root)
        val face = parse(path).documentElement
        assertEquals("10:08:32", face.child("Metadata").getAttribute("value"))
        assertEquals(1, face.all("ColorConfiguration").size)
        assertEquals(listOf("#FFFF3B30", "#FF9E9E9E"), face.all("ColorOption").map { it.getAttribute("colors") })
        val group = face.all("Group").single { it.getAttribute("name") == "vigil-native-hands" }
        assertEquals("MASK", group.child("PartImage").getAttribute("renderMode"))
        assertEquals(2, group.all("SecondHand").size)
        assertEquals(2, group.all("Sweep").size)
        assertEquals("[CONFIGURATION.secondColor.0]", group.all("SecondHand").last().getAttribute("tintColor"))
        assertEquals(2, face.all("Expression").count { it.textContent == "[CONFIGURATION.showHands] == \"TRUE\"" })
    }

    @Test fun vigilMarkersKeepSourceAndRemoveOnlyBottomHourLine() {
        val root = Files.createTempDirectory("vigil-marker")
        val source = root.resolve("input")
        Files.createDirectories(source)
        val artwork = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 450 450"><g fill="none" stroke="#282828" stroke-width="10" stroke-linecap="round"><line x1="225" y1="411" x2="225" y2="415"/><line x1="225" y1="35" x2="225" y2="39"/></g><circle fill="#282828" cx="40" cy="225" r="3"/></svg>"""
        val names = listOf("vigil_minutemarkers_close", "vigil_minutemarkers_wide", "vigil_hourmarkers_close", "vigil_hourmarkers_wide")
        names.forEach { Files.writeString(source.resolve("$it.svg"), artwork) }
        val hands = root.resolve("shared/sugarlicious/watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")
        Files.createDirectories(hands)
        for (kind in listOf("hour", "minute", "second")) ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", hands.resolve("${kind}_hand_transparent.png").toFile())
        vigilMarkers(listOf("--source", source.toString()), root)
        for (name in names) {
            assertEquals(artwork, Files.readString(root.resolve("design-assets/vigil/$name.svg")))
            val image = ImageIO.read(root.resolve("watchfaces/vigil/src/main/res/drawable-nodpi/$name.png").toFile())
            assertEquals(-1, image.getRGB(225, 35))
            assertEquals(if ("hourmarkers" in name) 0 else -1, image.getRGB(225, 413))
        }
    }

    @Test fun realVigilArtworkMatchesOriginalCairoRasterPixelForPixel() {
        val root = Files.createTempDirectory("vigil-parity")
        val source = root.resolve("design-assets/vigil")
        Files.createDirectories(source)
        val names = listOf("vigil_minutemarkers_close", "vigil_minutemarkers_wide", "vigil_hourmarkers_close", "vigil_hourmarkers_wide")
        names.forEach { name -> javaClass.getResourceAsStream("/assets/vigil/$name.svg")!!.use { Files.copy(it, source.resolve("$name.svg")) } }
        val hands = root.resolve("shared/sugarlicious/watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")
        Files.createDirectories(hands)
        for (kind in listOf("hour", "minute", "second")) ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", hands.resolve("${kind}_hand_transparent.png").toFile())
        vigilMarkers(emptyList(), root)
        names.forEach { assertGolden("vigil/$it.png", root.resolve("watchfaces/vigil/src/main/res/drawable-nodpi/$it.png")) }
    }

    @Test fun realLegacyMastersAndSugarWearAdaptiveLayersMatchOriginalRaster() {
        val output = Files.createTempDirectory("legacy-raster")
        for (name in listOf("sugarlicious66", "sugarwear66", "sugarwear-foreground108", "sugarwear-background108")) {
            val svg = javaClass.getResourceAsStream("/assets/brand/$name.svg")!!.use { it.readBytes().toString(Charsets.UTF_8) }
            svgPng(svg, output.resolve("$name.png"), 432)
            assertGolden("brand/$name-432.png", output.resolve("$name.png"))
        }
    }

    @Test fun weatherImportCopiesBytesAndKeepsAllConditionMappingsAndAlphaBounds() {
        val root = Files.createTempDirectory("weather-import")
        val output = root.resolve("watchfaces/sugarlicious-orbit/src/main/res/drawable-nodpi")
        Files.createDirectories(output)
        val image = BufferedImage(8, 9, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(2, 3, -1)
        image.setRGB(5, 7, 0x7fffffff)
        val data =
            java.io
                .ByteArrayOutputStream()
                .also { ImageIO.write(image, "png", it) }
                .toByteArray()
        val names = listOf("sunny", "cloudy", "fog", "heavy_rain", "heavy_snow", "rain", "snow", "thunderstorm", "rain_and_snow", "light_snow", "shower", "partly_cloud", "wind", "clear", "partly_cloud_night")
        val deviceVendor = root.resolve("samsung.apk")
        val google = root.resolve("google.apk")

        fun zip(path: Path, entries: Map<String, ByteArray>) {
            java.util.zip.ZipOutputStream(Files.newOutputStream(path)).use { stream ->
                entries.forEach { (name, bytes) ->
                    stream.putNextEntry(java.util.zip.ZipEntry(name))
                    stream.write(bytes)
                    stream.closeEntry()
                }
            }
        }
        zip(deviceVendor, names.associate { "res/drawable/$it.png" to data })
        val table = root.resolve("table.txt")
        Files.writeString(table, "\uFEFF" + names.joinToString("\n") { "drawable/$it () (file) res/drawable/$it.png" })
        val reference = "<WatchFace><BitmapFonts>" + listOf("day", "night").joinToString("") { period -> "<BitmapFont name=\"weather_icons_pack_1_$period\">" + (1..15).joinToString("") { "<Character name=\"$it\" resource=\"weather_$it\"/>" } + "</BitmapFont>" } + "</BitmapFonts></WatchFace>"
        zip(google, (1..15).associate { "res/drawable/weather_$it.png" to data } + ("res/raw/watchface.xml" to reference.toByteArray()))
        weatherTool(listOf("--samsung-apk", deviceVendor.toString(), "--google-reference-apk", google.toString(), "--samsung-resource-table", table.toString()), root)
        val manifest =
            kotlinx.serialization.json.Json
                .parseToJsonElement(Files.readString(root.resolve("watchfaces/sugarlicious-orbit/reference/weather/manifest.json"))) as kotlinx.serialization.json.JsonObject
        val packs = manifest["packs"] as kotlinx.serialization.json.JsonObject
        assertEquals(4, packs.size)
        packs.values.forEach { assertEquals(15, (it as kotlinx.serialization.json.JsonObject).size) }
        val assets = manifest["assets"] as kotlinx.serialization.json.JsonObject
        val first = assets["orbit_weather_device_vendor_sunny"] as kotlinx.serialization.json.JsonObject
        assertEquals("[2,3,6,8]", first["bounds"].toString())
        assertEquals("[8,9]", first["canvas"].toString())
        assertContentEquals(data, Files.readAllBytes(output.resolve("orbit_weather_device_vendor_sunny.png")))
    }
}
