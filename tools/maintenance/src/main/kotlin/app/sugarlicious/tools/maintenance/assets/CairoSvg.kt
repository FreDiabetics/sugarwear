package app.sugarlicious.tools.maintenance.assets

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt

/** Small, strict SVG renderer for the owned source masters. Cairo supplies the same
 * rasterization primitive as the previous tool; unsupported artwork fails explicitly. */
internal interface Cairo : Library {
    fun cairoVersionString(): String

    fun cairoImageSurfaceCreate(format: Int, width: Int, height: Int): Pointer

    fun cairoCreate(surface: Pointer): Pointer

    fun cairoDestroy(context: Pointer)

    fun cairoSurfaceDestroy(surface: Pointer)

    fun cairoSurfaceWriteToPng(surface: Pointer, path: String): Int

    fun cairoSave(c: Pointer)

    fun cairoRestore(c: Pointer)

    fun cairoScale(c: Pointer, x: Double, y: Double)

    fun cairoTranslate(c: Pointer, x: Double, y: Double)

    fun cairoRotate(c: Pointer, radians: Double)

    fun cairoMoveTo(c: Pointer, x: Double, y: Double)

    fun cairoLineTo(c: Pointer, x: Double, y: Double)

    fun cairoCurveTo(c: Pointer, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double)

    fun cairoRelLineTo(c: Pointer, x: Double, y: Double)

    fun cairoRelMoveTo(c: Pointer, x: Double, y: Double)

    fun cairoRelCurveTo(c: Pointer, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double)

    fun cairoRectangle(c: Pointer, x: Double, y: Double, w: Double, h: Double)

    fun cairoArc(c: Pointer, x: Double, y: Double, r: Double, a1: Double, a2: Double)

    fun cairoClip(c: Pointer)

    fun cairoImageSurfaceCreateFromPng(path: String): Pointer

    fun cairoImageSurfaceGetWidth(surface: Pointer): Int

    fun cairoImageSurfaceGetHeight(surface: Pointer): Int

    fun cairoPatternCreateForSurface(surface: Pointer): Pointer

    fun cairoPatternSetFilter(pattern: Pointer, filter: Int)

    fun cairoPaintWithAlpha(c: Pointer, alpha: Double)

    fun cairoClosePath(c: Pointer)

    fun cairoNewPath(c: Pointer)

    fun cairoPathExtents(c: Pointer, x1: com.sun.jna.ptr.DoubleByReference, y1: com.sun.jna.ptr.DoubleByReference, x2: com.sun.jna.ptr.DoubleByReference, y2: com.sun.jna.ptr.DoubleByReference)

    fun cairoFillPreserve(c: Pointer)

    fun cairoStroke(c: Pointer)

    fun cairoSetSourceRgba(c: Pointer, r: Double, g: Double, b: Double, a: Double)

    fun cairoSetLineWidth(c: Pointer, w: Double)

    fun cairoSetLineCap(c: Pointer, cap: Int)

    fun cairoSetLineJoin(c: Pointer, join: Int)

    fun cairoSetMiterLimit(c: Pointer, limit: Double)

    fun cairoPatternCreateLinear(x1: Double, y1: Double, x2: Double, y2: Double): Pointer

    fun cairoPatternAddColorStopRgba(p: Pointer, offset: Double, r: Double, g: Double, b: Double, a: Double)

    fun cairoPatternSetExtend(p: Pointer, mode: Int)

    fun cairoSetSource(c: Pointer, p: Pointer)

    fun cairoPatternDestroy(p: Pointer)
}

internal val cairo: Cairo by lazy {
    val configured = System.getProperty("maintenance.cairo.library") ?: System.getenv("SUGARLICIOUS_CAIRO_LIBRARY")
    val name = configured ?: if (System.getProperty("os.name").startsWith("Windows")) "cairo-2" else "cairo"
    val symbols =
        com.sun.jna.FunctionMapper { _, method ->
            method.name.replace(Regex("[A-Z]")) { "_${it.value.lowercase()}" }
        }
    Native.load(name, Cairo::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to symbols)).also { require(it.cairoVersionString() == "1.18.4") { "Asset parity requires Cairo 1.18.4; use SUGARLICIOUS_CAIRO_LIBRARY to select the pinned library" } }
}

internal data class VectorBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

internal fun cairoPathBounds(data: String): VectorBounds {
    val lib = cairo
    val surface = lib.cairoImageSurfaceCreate(0, 1, 1)
    val context = lib.cairoCreate(surface)
    try {
        cairoPath(lib, context, data)
        val values =
            List(4) {
                com.sun.jna.ptr
                    .DoubleByReference()
            }
        lib.cairoPathExtents(context, values[0], values[1], values[2], values[3])
        return VectorBounds(values[0].value, values[1].value, values[2].value, values[3].value)
    } finally {
        lib.cairoDestroy(context)
        lib.cairoSurfaceDestroy(surface)
    }
}

internal fun cairoSvg(svg: String, output: Path, size: Int) {
    val root = parseXml(svg).documentElement
    val view =
        root
            .getAttribute("viewBox")
            .trim()
            .split(Regex("[ ,]+"))
            .map(String::toDouble)
    require(view.size == 4 && view[0] == 0.0 && view[1] == 0.0) { "Unsupported SVG viewBox" }
    val css = mutableMapOf<String, Map<String, String>>()

    fun declarations(text: String) = text.split(';').mapNotNull { item -> item.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
    root.all("style").forEach { style -> Regex("\\.([\\w-]+)\\s*\\{([^}]+)}").findAll(style.textContent).forEach { css[it.groupValues[1]] = declarations(it.groupValues[2]) } }
    val gradients = root.all("linearGradient").associateBy { it.getAttribute("id") }
    val lib = cairo
    val surface = lib.cairoImageSurfaceCreate(0, size, size)
    val c = lib.cairoCreate(surface)

    fun color(text: String): DoubleArray {
        val hex =
            when (text.lowercase()) {
                "white" -> "ffffff"
                "black" -> "000000"
                else -> text.removePrefix("#")
            }.let { if (it.length == 3) it.map { ch -> "$ch$ch" }.joinToString("") else it }
        require(hex.length == 6) { "Unsupported SVG color $text" }
        return doubleArrayOf(hex.substring(0, 2).toInt(16) / 255.0, hex.substring(2, 4).toInt(16) / 255.0, hex.substring(4, 6).toInt(16) / 255.0)
    }

    fun paint(value: String, alpha: Double) {
        if (value.startsWith("url(#")) {
            val g = gradients[value.removePrefix("url(#").removeSuffix(")")] ?: error("Missing SVG gradient")
            require(g.getAttribute("gradientUnits") == "userSpaceOnUse") { "Unsupported gradient coordinates" }
            val pattern = lib.cairoPatternCreateLinear(g.getAttribute("x1").toDouble(), g.getAttribute("y1").toDouble(), g.getAttribute("x2").toDouble(), g.getAttribute("y2").toDouble())
            try {
                g.children().forEach { stop ->
                    val rgb = color(stop.getAttribute("stop-color"))
                    val offset = stop.getAttribute("offset")
                    lib.cairoPatternAddColorStopRgba(pattern, if (offset.endsWith('%')) offset.dropLast(1).toDouble() / 100 else offset.toDouble(), rgb[0], rgb[1], rgb[2], alpha)
                }
                lib.cairoPatternSetExtend(pattern, 3)
                lib.cairoSetSource(c, pattern)
            } finally {
                lib.cairoPatternDestroy(pattern)
            }
        } else {
            val rgb = color(value)
            lib.cairoSetSourceRgba(c, rgb[0], rgb[1], rgb[2], alpha)
        }
    }

    fun draw(element: Element, inherited: Map<String, String>) {
        if (element.localName in setOf("defs", "style", "metadata", "title", "desc")) return
        lib.cairoSave(c)
        try {
            val style = inherited.toMutableMap()
            element.getAttribute("class").split(' ').forEach { css[it]?.let(style::putAll) }
            for (key in listOf("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "fill-opacity", "stroke-opacity", "opacity")) if (element.hasAttribute(key)) style[key] = element.getAttribute(key)
            style.putAll(declarations(element.getAttribute("style")))
            val transform = element.getAttribute("transform")
            Regex("([a-z]+)\\(([^)]+)\\)").findAll(transform).forEach { t ->
                val v =
                    t.groupValues[2]
                        .trim()
                        .split(Regex("[ ,]+"))
                        .map(String::toDouble)
                when (t.groupValues[1]) {
                    "translate" -> lib.cairoTranslate(c, v[0], v.getOrElse(1) { 0.0 })
                    "scale" -> lib.cairoScale(c, v[0], v.getOrElse(1) { v[0] })
                    "rotate" -> {
                        if (v.size == 3) lib.cairoTranslate(c, v[1], v[2])
                        lib.cairoRotate(c, Math.toRadians(v[0]))
                        if (v.size == 3) lib.cairoTranslate(c, -v[1], -v[2])
                    }
                    else -> error("Unsupported SVG transform $transform")
                }
            }
            when (element.localName) {
                "svg", "g" -> element.children().forEach { draw(it, style) }
                "image" -> {
                    val href = element.getAttributeNS("http://www.w3.org/1999/xlink", "href").ifEmpty { element.getAttribute("href") }
                    require(href.startsWith("data:image/png;base64,")) { "Only embedded PNG image artwork is supported" }
                    val data =
                        java.util.Base64
                            .getMimeDecoder()
                            .decode(href.substringAfter(','))
                    val temp = Files.createTempFile("svg-image-", ".png")
                    try {
                        Files.write(temp, data)
                        val image = lib.cairoImageSurfaceCreateFromPng(temp.toString())
                        try {
                            val iw = lib.cairoImageSurfaceGetWidth(image).toDouble()
                            val ih = lib.cairoImageSurfaceGetHeight(image).toDouble()
                            require(iw > 0 && ih > 0) { "Invalid embedded PNG" }

                            fun n(name: String, default: Double) = element.getAttribute(name).ifEmpty { default.toString() }.toDouble()
                            val width = n("width", iw)
                            val height = n("height", ih)
                            val x = n("x", 0.0)
                            val y = n("y", 0.0)
                            val none = element.getAttribute("preserveAspectRatio") == "none"
                            val sx = if (none) width / iw else minOf(width / iw, height / ih)
                            val sy = if (none) height / ih else sx
                            val tx = if (none) 0.0 else (width - sx * iw) / 2
                            val ty = if (none) 0.0 else (height - sy * ih) / 2
                            lib.cairoRectangle(c, x, y, width, height)
                            lib.cairoClip(c)
                            lib.cairoTranslate(c, x + tx, y + ty)
                            lib.cairoScale(c, sx, sy)
                            val pattern = lib.cairoPatternCreateForSurface(image)
                            try {
                                lib.cairoPatternSetFilter(pattern, 1)
                                lib.cairoSetSource(c, pattern)
                                lib.cairoPaintWithAlpha(c, style["opacity"]?.toDouble() ?: 1.0)
                            } finally {
                                lib.cairoPatternDestroy(pattern)
                            }
                        } finally {
                            lib.cairoSurfaceDestroy(image)
                        }
                    } finally {
                        Files.delete(temp)
                    }
                }
                "path", "rect", "line", "circle" -> {
                    lib.cairoNewPath(c)

                    fun n(name: String, default: Double = 0.0) =
                        element
                            .getAttribute(name)
                            .ifEmpty { default.toString() }
                            .removeSuffix("px")
                            .toDouble()
                    when (element.localName) {
                        "path" -> cairoPath(lib, c, element.getAttribute("d"))
                        "line" -> {
                            lib.cairoMoveTo(c, n("x1"), n("y1"))
                            lib.cairoLineTo(c, n("x2"), n("y2"))
                        }
                        "circle" -> lib.cairoArc(c, n("cx"), n("cy"), n("r"), 0.0, 2 * Math.PI)
                        "rect" -> {
                            val x = n("x")
                            val y = n("y")
                            val w = n("width")
                            val h = n("height")
                            val rx = minOf(n("rx", n("ry")), w / 2)
                            val ry = minOf(n("ry", rx), h / 2)
                            if (rx == 0.0 || ry == 0.0) {
                                lib.cairoRectangle(c, x, y, w, h)
                            } else {
                                val k = 4 * (sqrt(2.0) - 1) / 3
                                val a = rx * k
                                val b = ry * k
                                lib.cairoMoveTo(c, x + rx, y)
                                lib.cairoRelLineTo(c, w - 2 * rx, 0.0)
                                lib.cairoRelCurveTo(c, a, 0.0, rx, b, rx, ry)
                                lib.cairoRelLineTo(c, 0.0, h - 2 * ry)
                                lib.cairoRelCurveTo(c, 0.0, b, a - rx, ry, -rx, ry)
                                lib.cairoRelLineTo(c, -w + 2 * rx, 0.0)
                                lib.cairoRelCurveTo(c, -a, 0.0, -rx, -b, -rx, -ry)
                                lib.cairoRelLineTo(c, 0.0, -h + 2 * ry)
                                lib.cairoRelCurveTo(c, 0.0, -b, rx - a, -ry, rx, -ry)
                                lib.cairoClosePath(c)
                            }
                        }
                    }
                    val opacity = style["opacity"]?.toDouble() ?: 1.0
                    val fill = style["fill"] ?: "black"
                    if (fill != "none") {
                        paint(fill, opacity * (style["fill-opacity"]?.toDouble() ?: 1.0))
                        lib.cairoFillPreserve(c)
                    }
                    val stroke = style["stroke"] ?: "none"
                    if (stroke != "none") {
                        paint(stroke, opacity * (style["stroke-opacity"]?.toDouble() ?: 1.0))
                        lib.cairoSetLineWidth(c, style["stroke-width"]?.removeSuffix("px")?.toDouble() ?: 1.0)
                        lib.cairoSetLineCap(
                            c,
                            when (style["stroke-linecap"]) {
                                "round" -> 1
                                "square" -> 2
                                else -> 0
                            },
                        )
                        lib.cairoSetLineJoin(
                            c,
                            when (style["stroke-linejoin"]) {
                                "round" -> 1
                                "bevel" -> 2
                                else -> 0
                            },
                        )
                        lib.cairoSetMiterLimit(c, style["stroke-miterlimit"]?.toDouble() ?: 4.0)
                        lib.cairoStroke(c)
                    }
                    lib.cairoNewPath(c)
                }
                else -> error("Unsupported SVG element ${element.localName}; preserve original tooling until supported")
            }
        } finally {
            lib.cairoRestore(c)
        }
    }
    try {
        lib.cairoScale(c, size / view[2], size / view[3])
        draw(root, emptyMap())
        Files.createDirectories(output.parent)
        check(lib.cairoSurfaceWriteToPng(surface, output.toAbsolutePath().toString()) == 0) { "Cairo PNG write failed" }
    } finally {
        lib.cairoDestroy(c)
        lib.cairoSurfaceDestroy(surface)
    }
}

private fun cairoPath(lib: Cairo, c: Pointer, data: String) {
    val tokens = Regex("[a-zA-Z]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?").findAll(data).map { it.value }.toList()
    var i = 0
    var command = ' '
    var previous = ' '
    var x = 0.0
    var y = 0.0
    var startX = 0.0
    var startY = 0.0
    var controlX = 0.0
    var controlY = 0.0

    fun number() = tokens[i++].toDouble()
    while (i < tokens.size) {
        if (tokens[i].length == 1 && tokens[i][0].isLetter()) command = tokens[i++][0]
        val relative = command.isLowerCase()

        fun point(): Pair<Double, Double> {
            val a = number()
            val b = number()
            return (a + if (relative)x else 0.0) to (b + if (relative)y else 0.0)
        }
        when (command.uppercaseChar()) {
            'M' -> {
                val p = point()
                x = p.first
                y = p.second
                startX = x
                startY = y
                lib.cairoMoveTo(c, x, y)
                command = if (relative)'l' else 'L'
            }
            'L' -> {
                val p = point()
                if (relative) lib.cairoRelLineTo(c, p.first - x, p.second - y) else lib.cairoLineTo(c, p.first, p.second)
                x = p.first
                y = p.second
            }
            'H' -> {
                val value = number()
                if (relative) lib.cairoRelLineTo(c, value, 0.0) else lib.cairoLineTo(c, value, y)
                x = value + if (relative)x else 0.0
            }
            'V' -> {
                val value = number()
                if (relative) lib.cairoRelLineTo(c, 0.0, value) else lib.cairoLineTo(c, x, value)
                y = value + if (relative)y else 0.0
            }
            'C' -> {
                val a = point()
                val b = point()
                val p = point()
                if (relative) lib.cairoRelCurveTo(c, a.first - x, a.second - y, b.first - x, b.second - y, p.first - x, p.second - y) else lib.cairoCurveTo(c, a.first, a.second, b.first, b.second, p.first, p.second)
                controlX = b.first
                controlY = b.second
                x = p.first
                y = p.second
            }
            'S' -> {
                val a = if (previous.uppercaseChar() in setOf('C', 'S')) (2 * x - controlX) to (2 * y - controlY) else x to y
                val b = point()
                val p = point()
                if (relative) lib.cairoRelCurveTo(c, a.first - x, a.second - y, b.first - x, b.second - y, p.first - x, p.second - y) else lib.cairoCurveTo(c, a.first, a.second, b.first, b.second, p.first, p.second)
                controlX = b.first
                controlY = b.second
                x = p.first
                y = p.second
            }
            'Z' -> {
                lib.cairoClosePath(c)
                x = startX
                y = startY
            }
            else -> error("Unsupported SVG path command $command")
        }
        previous = command
    }
}
