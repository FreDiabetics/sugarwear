package app.sugarwear

import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CgmStatusVectorSourceParityTest {
    data class VectorSpec(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<PathSpec>,
    )

    data class PathSpec(
        val id: String?,
        val fillColor: String,
        val pathData: String,
    )

    private data class IconParityPair(
        val svgFileName: String,
        val drawableResId: Int,
        val expectedPathCount: Int,
        val expectedColors: List<String>,
    )

    @Test
    fun `all four status icon SVG sources match their vector drawables`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pairs =
            listOf(
                IconParityPair(
                    svgFileName = "ic_bluetooth.svg",
                    drawableResId = R.drawable.ic_status_bluetooth,
                    expectedPathCount = 1,
                    expectedColors = listOf("#FFFFFF"),
                ),
                IconParityPair(
                    svgFileName = "ic_datenpausiert.svg",
                    drawableResId = R.drawable.ic_status_paused,
                    expectedPathCount = 2,
                    expectedColors = listOf("#FFFFFF", "#FCC93B"),
                ),
                IconParityPair(
                    svgFileName = "ic_sensorfehler.svg",
                    drawableResId = R.drawable.ic_status_sensor_error,
                    expectedPathCount = 2,
                    expectedColors = listOf("#FFFFFF", "#F73D45"),
                ),
                IconParityPair(
                    svgFileName = "ic_signalverlust.svg",
                    drawableResId = R.drawable.ic_status_signal_loss,
                    expectedPathCount = 3,
                    expectedColors = listOf("#FFFFFF", "#F73D45", "#848484"),
                ),
            )

        val failures = mutableListOf<String>()

        for (pair in pairs) {
            val svgFile = findSvgFile(pair.svgFileName)
            val svgSpec = parseSvgFile(svgFile)
            val vectorSpec = parseVectorDrawableResource(context, pair.drawableResId)

            try {
                assertVectorParity(pair.svgFileName, svgSpec, vectorSpec)

                assertEquals("${pair.svgFileName} path count", pair.expectedPathCount, vectorSpec.paths.size)
                val actualColors = vectorSpec.paths.map { it.fillColor }
                assertEquals("${pair.svgFileName} fill colors", pair.expectedColors, actualColors)
            } catch (e: AssertionError) {
                failures += e.message ?: "Unknown parity failure for ${pair.svgFileName}"
            }
        }

        assertTrue(
            "Parity failures:\n${failures.joinToString("\n\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun `status drawables preserve white sensor icon and yellow pause bars without black on black`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val pausedSpec = parseVectorDrawableResource(context, R.drawable.ic_status_paused)
        val sensorErrorSpec = parseVectorDrawableResource(context, R.drawable.ic_status_sensor_error)
        val signalLossSpec = parseVectorDrawableResource(context, R.drawable.ic_status_signal_loss)
        val bluetoothSpec = parseVectorDrawableResource(context, R.drawable.ic_status_bluetooth)

        assertEquals("#FFFFFF", pausedSpec.paths[0].fillColor)
        assertEquals("#FFFFFF", sensorErrorSpec.paths[0].fillColor)
        assertEquals("#FFFFFF", signalLossSpec.paths[0].fillColor)
        assertEquals("#FFFFFF", bluetoothSpec.paths[0].fillColor)

        assertEquals("#FCC93B", pausedSpec.paths[1].fillColor)

        listOf(pausedSpec, sensorErrorSpec, signalLossSpec, bluetoothSpec).forEach { spec ->
            assertFalse(
                "Status drawables must not contain black fill color",
                spec.paths.any { it.fillColor == "#000000" },
            )
        }
    }

    @Test
    fun `in memory negative test detects modified color`() {
        val baseSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z")),
            )
        val modifiedColorSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#000000", "M29.8,30.88L30,30Z")),
            )

        var failed = false
        try {
            assertVectorParity("in-memory-color-test", baseSpec, modifiedColorSpec)
        } catch (e: AssertionError) {
            failed = true
            assertTrue(
                "Message should detail color mismatch: ${e.message}",
                e.message?.contains("Color mismatch") == true,
            )
        }
        assertTrue("Expected assertion failure for modified color", failed)
    }

    @Test
    fun `in memory negative test detects modified path data`() {
        val baseSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z")),
            )
        val modifiedPathSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M10,10L50,50Z")),
            )

        var failed = false
        try {
            assertVectorParity("in-memory-path-test", baseSpec, modifiedPathSpec)
        } catch (e: AssertionError) {
            failed = true
            assertTrue(
                "Message should detail path mismatch: ${e.message}",
                (e.message?.contains("PathData mismatch") == true) || (e.message?.contains("bounds mismatch") == true),
            )
        }
        assertTrue("Expected assertion failure for modified path", failed)
    }

    @Test
    fun `in memory negative test detects modified viewport`() {
        val baseSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z")),
            )
        val modifiedViewportSpec =
            VectorSpec(
                viewportWidth = 100.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z")),
            )

        var failed = false
        try {
            assertVectorParity("in-memory-viewport-test", baseSpec, modifiedViewportSpec)
        } catch (e: AssertionError) {
            failed = true
            assertTrue(
                "Message should detail viewport mismatch: ${e.message}",
                e.message?.contains("Viewport mismatch") == true,
            )
        }
        assertTrue("Expected assertion failure for modified viewport", failed)
    }

    @Test
    fun `in memory negative test detects path count mismatch`() {
        val baseSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths = listOf(PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z")),
            )
        val extraPathSpec =
            VectorSpec(
                viewportWidth = 66.0f,
                viewportHeight = 66.0f,
                paths =
                    listOf(
                        PathSpec("sensor", "#FFFFFF", "M29.8,30.88L30,30Z"),
                        PathSpec("bar", "#FCC93B", "M5,5L15,15Z"),
                    ),
            )

        var failed = false
        try {
            assertVectorParity("in-memory-count-test", baseSpec, extraPathSpec)
        } catch (e: AssertionError) {
            failed = true
            assertTrue(
                "Message should detail path count mismatch: ${e.message}",
                e.message?.contains("Path count mismatch") == true,
            )
        }
        assertTrue("Expected assertion failure for path count mismatch", failed)
    }

    private fun findSvgFile(fileName: String): File {
        val candidates =
            listOf(
                File("design-assets/alarms", fileName),
                File("../design-assets/alarms", fileName),
                File(System.getProperty("user.dir"), "design-assets/alarms/$fileName"),
                File(System.getProperty("user.dir"), "../design-assets/alarms/$fileName"),
            )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError(
                "SVG source file not found: $fileName. Checked paths:\n" +
                    candidates.joinToString("\n") { it.absolutePath },
            )
    }

    private fun parseSvgFile(file: File): VectorSpec {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val svgElement = doc.documentElement

        val viewBox = svgElement.getAttribute("viewBox")
        if (viewBox.isBlank()) {
            throw AssertionError("SVG ${file.name} is missing viewBox attribute")
        }
        val viewBoxParts = viewBox.trim().split(Regex("""[\s,]+""")).map { it.toFloat() }
        if (viewBoxParts.size != 4) {
            throw AssertionError("SVG ${file.name} has invalid viewBox: '$viewBox'")
        }
        val viewportWidth = viewBoxParts[2]
        val viewportHeight = viewBoxParts[3]

        val cssMap = mutableMapOf<String, String>()
        val styleNodes = doc.getElementsByTagName("style")
        for (i in 0 until styleNodes.length) {
            val styleText = styleNodes.item(i).textContent
            cssMap.putAll(parseCssStyleBlock(styleText))
        }

        val pathNodes = doc.getElementsByTagName("path")
        val paths = mutableListOf<PathSpec>()
        for (i in 0 until pathNodes.length) {
            val elem = pathNodes.item(i) as Element
            val id = elem.getAttribute("id").ifEmpty { null }
            val d = elem.getAttribute("d")
            if (d.isBlank()) {
                throw AssertionError("SVG ${file.name} path $i has empty 'd' attribute")
            }

            val classAttr = elem.getAttribute("class")
            val fillAttr = elem.getAttribute("fill")
            val styleAttr = elem.getAttribute("style")

            val fillColor =
                when {
                    fillAttr.isNotBlank() && fillAttr != "none" -> normalizeColor(fillAttr)
                    styleAttr.contains("fill:") -> {
                        val match =
                            Regex("""fill\s*:\s*([^;}]+)""").find(styleAttr)
                                ?: throw AssertionError("SVG ${file.name} path $i has unparseable fill in style: '$styleAttr'")
                        normalizeColor(match.groupValues[1])
                    }

                    classAttr.isNotBlank() -> {
                        classAttr.split(Regex("""\s+""")).firstNotNullOfOrNull { cssMap[it] }
                            ?: throw AssertionError("SVG ${file.name} path $i has class '$classAttr' not found in CSS map $cssMap")
                    }

                    else -> throw AssertionError("SVG ${file.name} path $i has no fill color resolved")
                }

            paths.add(PathSpec(id = id, fillColor = fillColor, pathData = d))
        }

        if (paths.isEmpty()) {
            throw AssertionError("SVG ${file.name} contains no <path> elements")
        }

        return VectorSpec(viewportWidth, viewportHeight, paths)
    }

    private fun parseCssStyleBlock(styleContent: String): Map<String, String> {
        val classToColorMap = mutableMapOf<String, String>()
        val ruleRegex = Regex("""([^{]+)\{\s*([^}]+)\s*\}""")
        val fillRegex = Regex("""fill\s*:\s*([^;}]+)""")

        ruleRegex.findAll(styleContent).forEach { ruleMatch ->
            val selectors = ruleMatch.groupValues[1].split(",")
            val body = ruleMatch.groupValues[2]
            val fillMatch = fillRegex.find(body)
            if (fillMatch != null) {
                val rawColor = fillMatch.groupValues[1].trim()
                val normalizedColor = normalizeColor(rawColor)
                selectors.forEach { selector ->
                    val className = selector.trim().removePrefix(".")
                    if (className.isNotEmpty()) {
                        classToColorMap[className] = normalizedColor
                    }
                }
            }
        }
        return classToColorMap
    }

    private fun parseVectorDrawableResource(
        context: android.content.Context,
        drawableResId: Int,
    ): VectorSpec {
        val ns = "http://schemas.android.com/apk/res/android"
        val resName = context.resources.getResourceEntryName(drawableResId)
        var viewportWidth = 0f
        var viewportHeight = 0f
        val paths = mutableListOf<PathSpec>()

        context.resources.getXml(drawableResId).use { xml ->
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG) {
                    when (xml.name) {
                        "vector" -> {
                            val vw =
                                xml.getAttributeValue(ns, "viewportWidth")
                                    ?: throw AssertionError("Vector drawable $resName missing viewportWidth")
                            val vh =
                                xml.getAttributeValue(ns, "viewportHeight")
                                    ?: throw AssertionError("Vector drawable $resName missing viewportHeight")
                            viewportWidth = vw.toFloat()
                            viewportHeight = vh.toFloat()
                        }

                        "group" -> {
                            val tx = xml.getAttributeValue(ns, "translateX")?.toFloat() ?: 0f
                            val ty = xml.getAttributeValue(ns, "translateY")?.toFloat() ?: 0f
                            val sx = xml.getAttributeValue(ns, "scaleX")?.toFloat() ?: 1f
                            val sy = xml.getAttributeValue(ns, "scaleY")?.toFloat() ?: 1f
                            val rot = xml.getAttributeValue(ns, "rotation")?.toFloat() ?: 0f

                            if (tx != 0f || ty != 0f || sx != 1f || sy != 1f || rot != 0f) {
                                throw AssertionError(
                                    "Vector drawable $resName group has active non-identity transformation: " +
                                        "tx=$tx, ty=$ty, sx=$sx, sy=$sy, rot=$rot",
                                )
                            }
                        }

                        "path" -> {
                            val pathData =
                                xml.getAttributeValue(ns, "pathData")
                                    ?: throw AssertionError("Vector drawable $resName path missing pathData")
                            val rawColor =
                                xml.getAttributeValue(ns, "fillColor")
                                    ?: throw AssertionError("Vector drawable $resName path missing fillColor")
                            val fillColor = normalizeColor(rawColor)
                            val name = xml.getAttributeValue(ns, "name")
                            paths.add(PathSpec(id = name, fillColor = fillColor, pathData = pathData))
                        }
                    }
                }
                xml.next()
            }
        }

        if (viewportWidth == 0f || viewportHeight == 0f) {
            throw AssertionError("Vector drawable $resName invalid viewport dimensions: $viewportWidth x $viewportHeight")
        }
        if (paths.isEmpty()) {
            throw AssertionError("Vector drawable $resName contains no paths")
        }

        return VectorSpec(viewportWidth, viewportHeight, paths)
    }

    private fun assertVectorParity(
        pairName: String,
        expectedSvg: VectorSpec,
        actualVector: VectorSpec,
    ) {
        if (expectedSvg.viewportWidth != actualVector.viewportWidth ||
            expectedSvg.viewportHeight != actualVector.viewportHeight
        ) {
            fail(
                "[$pairName] Viewport mismatch: SVG is ${expectedSvg.viewportWidth}x${expectedSvg.viewportHeight}, " +
                    "Vector is ${actualVector.viewportWidth}x${actualVector.viewportHeight}",
            )
        }

        if (expectedSvg.paths.size != actualVector.paths.size) {
            fail(
                "[$pairName] Path count mismatch: SVG has ${expectedSvg.paths.size} paths, " +
                    "Vector has ${actualVector.paths.size} paths",
            )
        }

        for (i in expectedSvg.paths.indices) {
            val svgPath = expectedSvg.paths[i]
            val vectorPath = actualVector.paths[i]

            if (svgPath.fillColor != vectorPath.fillColor) {
                fail(
                    "[$pairName] Color mismatch at path index $i: " +
                        "SVG color is ${svgPath.fillColor}, Vector color is ${vectorPath.fillColor}",
                )
            }

            val normSvgData = normalizePathData(svgPath.pathData)
            val normVectorData = normalizePathData(vectorPath.pathData)
            if (normSvgData != normVectorData) {
                fail(
                    "[$pairName] PathData mismatch at path index $i:\n" +
                        "SVG:    ${svgPath.pathData}\n" +
                        "Vector: ${vectorPath.pathData}",
                )
            }

            val svgBounds = computePathBounds(svgPath.pathData)
            val vectorBounds = computePathBounds(vectorPath.pathData)

            val deltaL = kotlin.math.abs(svgBounds.left - vectorBounds.left)
            val deltaT = kotlin.math.abs(svgBounds.top - vectorBounds.top)
            val deltaR = kotlin.math.abs(svgBounds.right - vectorBounds.right)
            val deltaB = kotlin.math.abs(svgBounds.bottom - vectorBounds.bottom)

            if (deltaL > 0.001f || deltaT > 0.001f || deltaR > 0.001f || deltaB > 0.001f) {
                fail(
                    "[$pairName] Geometric bounds mismatch at path index $i: " +
                        "SVG bounds=$svgBounds, Vector bounds=$vectorBounds",
                )
            }

            if (vectorBounds.left < -0.01f ||
                vectorBounds.top < -0.01f ||
                vectorBounds.right > actualVector.viewportWidth + 0.01f ||
                vectorBounds.bottom > actualVector.viewportHeight + 0.01f
            ) {
                fail(
                    "[$pairName] Path index $i clipped outside viewport 0..${actualVector.viewportWidth} x 0..${actualVector.viewportHeight}: $vectorBounds",
                )
            }
        }
    }

    private fun normalizePathData(pathData: String): String = pathData.trim().replace(Regex("""\s+"""), " ")

    private fun computePathBounds(pathData: String): RectF {
        val bounds = RectF()
        val path = PathParser.createPathFromPathData(pathData)
        path.computeBounds(bounds, true)
        return bounds
    }

    private fun normalizeColor(colorStr: String): String {
        val trimmed = colorStr.trim().lowercase()
        if (!trimmed.startsWith("#")) {
            throw AssertionError("Unsupported color format: '$colorStr'. Expected hex color starting with #")
        }
        val hex = trimmed.substring(1)
        val expanded =
            when (hex.length) {
                3 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
                4 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}"
                6 -> hex
                8 -> if (hex.startsWith("ff")) hex.substring(2) else hex
                else -> throw AssertionError("Invalid hex color length: '$colorStr'")
            }
        return "#${expanded.uppercase()}"
    }
}
