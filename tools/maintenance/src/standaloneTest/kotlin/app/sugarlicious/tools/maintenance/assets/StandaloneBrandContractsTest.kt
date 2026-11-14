package app.sugarlicious.tools.maintenance.assets

import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** SugarWear's live brand contracts; source ownership remains in its repository. */
class StandaloneBrandContractsTest {
    private val root = Path.of(System.getProperty("maintenance.repositoryRoot"))

    @Test fun sugarwearUsesOnlyFinalVisibleSourceSymbol() {
        val source = parse(root.resolve("design-assets/sugarwear/adaptive108/combined.svg")).documentElement
        val symbol = parse(root.resolve("design-assets/sugarwear/adaptive108/foreground.svg")).documentElement
        val visible = source.all("path").last().getAttribute("d")
        assertEquals(visible, symbol.child("path").getAttribute("d"))
        val runtime = parse(root.resolve("shared/sugarlicious/ui-shared/src/main/res/drawable/ic_sugarwear_filled.xml")).documentElement
        val paths = runtime.all("path")
        assertEquals(1, paths.size)
        assertEquals(visible, paths.single().getAttributeNS(ANDROID, "pathData"))
        assertEquals("#FFFFFFFF", paths.single().getAttributeNS(ANDROID, "fillColor"))
    }

    @Test fun launcherGradientsMatchFullMastersAndKeepSymbolsSeparate() {
        val master = parse(root.resolve("design-assets/sugarwear/adaptive108/combined.svg")).documentElement
        val runtime = parse(root.resolve("app-sugarwear/src/main/res/drawable/sugarwear_launcher_background.xml")).documentElement
        if (master.all("image").isNotEmpty()) {
            assertEquals("bitmap", runtime.tagName)
            assertEquals("fill", runtime.getAttributeNS(ANDROID, "gravity"))
            val expectedFile = Files.createTempDirectory("sugarwear-source-raster").resolve("background.png")
            svgPng(Files.readString(root.resolve("design-assets/sugarwear/adaptive108/background.svg")), expectedFile, 432)
            val expected = ImageIO.read(expectedFile.toFile())
            val actual = ImageIO.read(root.resolve("app-sugarwear/src/main/res/drawable-nodpi/sugarwear_launcher_background_art.png").toFile())
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            assertContentEquals(expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width), actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width))
        } else {
            val source = master.all("linearGradient").single()
            val gradient = runtime.all("gradient").single()
            for ((svg, android) in listOf("x1" to "startX", "y1" to "startY", "x2" to "endX", "y2" to "endY")) assertEquals(source.getAttribute(svg), gradient.getAttributeNS(ANDROID, android))
            assertEquals(source.children().map { it.getAttribute("offset") to it.getAttribute("stop-color").lowercase() }, gradient.children().map { it.getAttributeNS(ANDROID, "offset") to ("#" + it.getAttributeNS(ANDROID, "color").drop(3)).lowercase() })
        }
    }

    @Test fun adaptiveLayersCenterExactSourceInsideSafe66UnitMotif() {
        val data = parse(root.resolve("design-assets/sugarwear/adaptive108/foreground.svg")).documentElement.child("path").getAttribute("d")
        val directory = root.resolve("app-sugarwear/src/main/res/drawable")
        val runtime = parse(directory.resolve("ic_sensor_launcher_foreground.xml")).documentElement
        assertEquals("vector", runtime.tagName)
        for (dimension in listOf("viewportWidth", "viewportHeight")) assertEquals("108", runtime.getAttributeNS(ANDROID, dimension))
        assertEquals(data, runtime.all("path").single().getAttributeNS(ANDROID, "pathData"))
        val group = runtime.child("group")
        for (axis in listOf("X", "Y")) {
            assertEquals((66.0 / 108).toString(), group.getAttributeNS(ANDROID, "scale$axis"))
            assertEquals(21.0, group.getAttributeNS(ANDROID, "translate$axis").toDouble())
        }
        assertContentEquals(Files.readAllBytes(directory.resolve("ic_sensor_launcher_foreground.xml")), Files.readAllBytes(directory.resolve("ic_sensor_launcher_monochrome.xml")))
    }
}
