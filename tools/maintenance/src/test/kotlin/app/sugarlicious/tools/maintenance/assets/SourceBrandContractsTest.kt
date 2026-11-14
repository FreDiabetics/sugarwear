package app.sugarlicious.tools.maintenance.assets

import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Live source contracts formerly covered by tools/tests/test_brand_assets.py. */
class SourceBrandContractsTest {
    private val root = Path.of(System.getProperty("maintenance.sharedRoot"))
    private val targets =
        mapOf(
            "app-mobile" to listOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml", "drawable/ic_foreground.xml", "drawable/ic_sugarlicious_monochrome.xml", "drawable/ic_notification.xml", "drawable/ic_notification_outlined.xml", "drawable-anydpi/ic_notification.xml", "drawable-anydpi/ic_notification_outlined.xml"),
            "app-wear" to listOf("drawable/ic_foreground.xml", "drawable/ic_monochrome_outlined.xml", "drawable/ic_notification.xml"),
        )

    @Test fun activeMobileWearAndNotificationVectorsUseSuppliedPath() {
        val data = parse(root.resolve("design-assets/app-icons/adaptive108/foreground.svg")).documentElement.child("path").getAttribute("d")
        targets.forEach { (module, names) ->
            names.forEach { name ->
                val vector = parse(root.resolve("$module/src/main/res/$name")).documentElement
                val paths = vector.all("path")
                assertEquals(1, paths.size, "$module/$name")
                assertEquals(data, paths.single().getAttributeNS(ANDROID, "pathData"), "$module/$name")
                val launcher = name in listOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml")
                when {
                    launcher -> assertEquals("108", vector.getAttributeNS(ANDROID, "viewportWidth"))
                    "notification" in name -> {
                        assertEquals("#FFFFFFFF", paths.single().getAttributeNS(ANDROID, "fillColor"))
                        assertEquals("66", vector.getAttributeNS(ANDROID, "viewportWidth"))
                    }
                    else -> {
                        assertEquals("108", vector.getAttributeNS(ANDROID, "viewportWidth"))
                        assertEquals("21", vector.child("group").getAttributeNS(ANDROID, "translateX"))
                    }
                }
                if (!launcher) {
                    val normalized = vector.all("group").single { it.getAttributeNS(ANDROID, "name") == "source108ToLegacy66" }
                    assertEquals(((if (module == "app-mobile" && "notification" in name) 82.0 else 60.0) / 108).toString(), normalized.getAttributeNS(ANDROID, "scaleX"))
                }
            }
        }
    }

    @Test fun launcherGradientsMatchFullMastersAndKeepSymbolsSeparate() {
        val source = parse(root.resolve("design-assets/app-icons/adaptive108/combined.svg")).documentElement.all("linearGradient").single()
        for (module in targets.keys) {
            val runtime = parse(root.resolve("$module/src/main/res/drawable/ic_launcher_background.xml")).documentElement.all("gradient").single()
            for ((svg, android) in listOf("x1" to "startX", "y1" to "startY", "x2" to "endX", "y2" to "endY")) assertEquals(source.getAttribute(svg), runtime.getAttributeNS(ANDROID, android))
            assertEquals(source.children().map { it.getAttribute("offset") to it.getAttribute("stop-color").lowercase() }, runtime.children().map { it.getAttributeNS(ANDROID, "offset") to ("#" + it.getAttributeNS(ANDROID, "color").drop(3)).lowercase() })
        }
        Files.newDirectoryStream(root.resolve("app-wear/src/main/res"), "mipmap-*").use { directories ->
            assertTrue(directories.none { Files.exists(it.resolve("ic_launcher_background.webp")) }, "Obsolete raster launcher background")
        }
    }

    @Test fun adaptiveLayersCenterExactSourceInsideSafe66UnitMotif() {
        val data = parse(root.resolve("design-assets/app-icons/adaptive108/foreground.svg")).documentElement.child("path").getAttribute("d")
        for (module in listOf("app-mobile", "app-wear")) {
            val drawable = root.resolve("$module/src/main/res/drawable")
            val vector = parse(drawable.resolve("ic_launcher_foreground.xml")).documentElement
            assertEquals("vector", vector.tagName)
            for (dimension in listOf("viewportWidth", "viewportHeight")) assertEquals("108", vector.getAttributeNS(ANDROID, dimension))
            assertEquals(data, vector.all("path").single().getAttributeNS(ANDROID, "pathData"))
            val group = vector.child("group")
            for (axis in listOf("X", "Y")) {
                assertEquals((66.0 / 108).toString(), group.getAttributeNS(ANDROID, "scale$axis"))
                assertEquals(21.0, group.getAttributeNS(ANDROID, "translate$axis").toDouble())
            }
            assertContentEquals(Files.readAllBytes(drawable.resolve("ic_launcher_foreground.xml")), Files.readAllBytes(drawable.resolve("ic_launcher_monochrome.xml")))
        }
    }

    @Test fun storeImagesAreFullSourceRenderings() {
        val temp = Files.createTempDirectory("live-brand-source")
        val expectedPath = temp.resolve("store.png")
        svgPng(Files.readString(root.resolve("design-assets/app-icons/adaptive108/combined.svg")), expectedPath, 512)
        val expected = ImageIO.read(expectedPath.toFile())
        for (module in listOf("app-mobile", "app-wear")) {
            val actual = ImageIO.read(root.resolve("$module/src/main/ic_launcher-playstore.png").toFile())
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            val sums = LongArray(4)
            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    val a = expected.getRGB(x, y)
                    val b = actual.getRGB(x, y)
                    for (channel in 0..3) sums[channel] += abs((a ushr (channel * 8) and 255) - (b ushr (channel * 8) and 255))
                }
            }
            assertTrue(sums.max().toDouble() / (expected.width * expected.height) < 0.75, "$module store image differs from source")
        }
    }
}
