package app.sugarlicious.tools.maintenance.assets

import org.w3c.dom.Element
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val mobileTargets = listOf("drawable/ic_launcher_monochrome.xml", "drawable/ic_foreground.xml", "drawable/ic_sugarlicious_monochrome.xml", "drawable/ic_notification.xml", "drawable/ic_notification_outlined.xml", "drawable-anydpi/ic_notification.xml", "drawable-anydpi/ic_notification_outlined.xml")
private val wearTargets = listOf("drawable/ic_foreground.xml", "drawable/ic_monochrome_outlined.xml", "drawable/ic_notification.xml")

private fun serialized(node: org.w3c.dom.Node): String =
    java.io
        .StringWriter()
        .also {
            javax.xml.transform.TransformerFactory
                .newInstance()
                .newTransformer()
                .transform(
                    javax.xml.transform.dom
                        .DOMSource(node),
                    javax.xml.transform.stream
                        .StreamResult(it),
                )
        }.toString()

private fun vector() = parseXml("<vector xmlns:android=\"$ANDROID\" android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"66\" android:viewportHeight=\"66\"/>")

private fun symbol(parent: Element, data: String): Element =
    parent.add("path").apply {
        android("fillColor", "#FFFFFFFF")
        android("pathData", data)
        if (data.length > 3000) setAttributeNS("http://schemas.android.com/tools", "tools:ignore", "VectorPath")
    }

private fun background(path: Path, gradient: Element, shape: String = "M0,0H66V66H0Z", viewport: Int = 66) {
    val doc = vector()
    val root = doc.documentElement
    root.android("viewportWidth", viewport)
    root.android("viewportHeight", viewport)
    val p = root.add("path")
    p.android("pathData", shape)
    val attr = doc.createElementNS("http://schemas.android.com/aapt", "aapt:attr")
    attr.setAttribute("name", "android:fillColor")
    p.appendChild(attr)
    val g = attr.add("gradient")
    g.android("type", "linear")
    g.android("tileMode", "clamp")
    listOf("x1" to "startX", "y1" to "startY", "x2" to "endX", "y2" to "endY").forEach { (s, t) -> g.android(t, gradient.getAttribute(s)) }
    gradient.children().forEach { stop ->
        g.add("item").apply {
            android("offset", stop.getAttribute("offset"))
            android("color", "#FF" + stop.getAttribute("stop-color").removePrefix("#"))
        }
    }
    writeXml(path, doc)
}

private fun foreground(path: Path, data: String) {
    val doc = vector()
    val root = doc.documentElement
    root.android("viewportWidth", 108)
    root.android("viewportHeight", 108)
    val group = root.add("group")
    listOf("scaleX" to 66.0 / 108, "scaleY" to 66.0 / 108, "translateX" to 21.0, "translateY" to 21.0).forEach { (k, v) -> group.android(k, v) }
    symbol(group, data)
    writeXml(path, doc)
}

private fun normalized(path: Path, data: String, scale: Double, offset: Int) {
    val doc = parse(path)
    val root = doc.documentElement
    root.all("group").filter { it.getAttributeNS(ANDROID, "name") == "source108ToLegacy66" }.forEach { group ->
        group.children().forEach { group.parentNode.insertBefore(it, group) }
        group.parentNode.removeChild(group)
    }
    val target = root.all("path").firstOrNull() ?: error("Missing vector path $path")
    val parent = target.parentNode as Element
    parent.removeChild(target)
    val group = parent.add("group")
    listOf("name" to "source108ToLegacy66", "scaleX" to scale.toString(), "scaleY" to scale.toString(), "translateX" to offset.toString(), "translateY" to offset.toString()).forEach { (k, v) -> group.android(k, v) }
    symbol(group, data)
    writeXml(path, doc)
}

internal fun brandTool(command: String, args: List<String>, defaultRoot: Path) {
    val adaptive = command == "import-adaptive-brand-icons"
    val opts = options(args, setOf("--source", "--source-dir", "--product", "--repository-root", "--preview-dir"))
    require(if (adaptive) "--source" !in opts else "--source-dir" !in opts) { "Unsupported source option" }
    val root = Path.of(opts["--repository-root"] ?: defaultRoot.toString()).toAbsolutePath().normalize()
    val product = opts["--product"] ?: "sugarlicious"
    require(product in setOf("sugarlicious", "sugarwear")) { "Unknown product $product" }
    val wear = product == "sugarwear"
    val preview = Path.of(opts["--preview-dir"] ?: root.resolve(if (adaptive) "app-mobile/build/reports/brand-icons108" else "build/reports/brand-icons").toString())
    Files.createDirectories(preview)
    if (!adaptive) {
        val source = Path.of(opts["--source"] ?: error("Missing --source")).toAbsolutePath().normalize()
        val art = parse(source).documentElement
        require(art.getAttribute("viewBox") == "0 0 66 66") { "Expected supplied 66x66 artwork" }
        val painted = art.children().filter { it.localName != "defs" }
        require(painted.size >= 2 && painted.last().localName == "path" && painted[painted.lastIndex - 1].localName == "rect") { "Expected final white symbol after opaque gradient circle" }
        val gradient = art.all("linearGradient").firstOrNull() ?: error("Missing source gradient")
        require(gradient.getAttribute("gradientUnits") == "userSpaceOnUse") { "Expected explicit source gradient geometry" }
        val data = painted.last().getAttribute("d")
        val directory = root.resolve(if (wear) "design-assets/sugarwear" else "design-assets/app-icons")
        val stem = if (wear) "ic_sugarwear" else "ic_sugarlicious"
        copy(source, directory.resolve("$stem.svg"))
        val svg = parseXml("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 66 66\"/>")
        svg.documentElement.add("path", "fill" to "#ffffff", "d" to data)
        writeXml(directory.resolve("${stem}_symbol.svg"), svg)
        val targets = if (wear) mapOf("app-sugarwear" to listOf("drawable/ic_sensor_launcher_foreground.xml", "drawable/ic_sensor_launcher_monochrome.xml", "drawable/ic_notification.xml")) else mapOf("app-mobile" to listOf("drawable/ic_launcher_foreground.xml") + mobileTargets, "app-wear" to wearTargets)
        targets.forEach { (module, files) ->
            files.forEach { name ->
                val path = root.resolve("$module/src/main/res/$name")
                val doc = parse(path)
                val paths = doc.documentElement.all("path")
                require(paths.size == 1) { "Expected one vector path in $path" }
                paths.single().android("pathData", data)
                paths.single().android("fillColor", "#FFFFFFFF")
                writeXml(path, doc)
            }
            background(root.resolve("$module/src/main/res/drawable/" + if (wear) "sugarwear_launcher_background.xml" else "ic_launcher_background.xml"), gradient)
            svgPng(Files.readString(source), root.resolve("$module/src/main/ic_launcher-playstore.png"), 512)
        }
        svgPng(Files.readString(source), preview.resolve("imported-master.png"), 432)
        svgPng(serialized(svg), preview.resolve("imported-symbol.png"), 432)
        return
    }
    val sources = Path.of(opts["--source-dir"] ?: "C:/Users/typ1d/Desktop/Sugarlicious/Watchfaces")
    val suffix = if (wear) "" else "2"
    val fgPath = sources.resolve("ic_foreground$suffix.svg")
    val bgPath = sources.resolve("ic_background$suffix.svg")
    val fg = parse(fgPath).documentElement
    val bg = parse(bgPath).documentElement
    require(fg.getAttribute("viewBox") == "0 0 108 108" && bg.getAttribute("viewBox") == "0 0 108 108") { "Expected native 108x108 layer" }
    val paths = fg.children().filter { it.localName == "path" }
    require(paths.size == 1) { "Expected one exact source symbol" }
    val data = paths.single().getAttribute("d")
    val directory = root.resolve("design-assets/" + (if (wear) "sugarwear" else "app-icons") + "/adaptive108")
    copy(fgPath, directory.resolve("foreground.svg"))
    copy(bgPath, directory.resolve("background.svg"))
    val combined = parse(bgPath)
    val group = combined.createElementNS("http://www.w3.org/2000/svg", "g")
    group.setAttribute("transform", "translate(21.0 21.0) scale(${66.0 / 108})")
    combined.documentElement.appendChild(group)
    val p = combined.importNode(paths.single(), true) as Element
    p.removeAttribute("class")
    p.setAttribute("fill", "#ffffff")
    group.appendChild(p)
    writeXml(directory.resolve("combined.svg"), combined)
    val targets = if (wear) listOf("ui-shared/src/main/res/drawable/ic_sugarwear_filled.xml") else mobileTargets.map { "app-mobile/src/main/res/$it" } + wearTargets.map { "app-wear/src/main/res/$it" }
    targets.forEach { name ->
        val path = root.resolve(name)
        if (wear && !Files.isRegularFile(path)) {
            require(parse(root.resolve("shared/sugarlicious/$name")).documentElement.all("path").any { it.getAttributeNS(ANDROID, "pathData") == data }) { "Update canonical ui-shared SugarWear symbol and its submodule revision first" }
        } else {
            val notification = !wear && name.startsWith("app-mobile/") && "notification" in name
            normalized(
                path,
                data,
                (
                    if (notification) {
                        82.0
                    } else if (wear) {
                        56.0
                    } else {
                        60.0
                    }
                ) / 108,
                if (notification) {
                    -8
                } else if (wear) {
                    5
                } else {
                    3
                },
            )
        }
    }
    val raster = bg.children().any { it.localName == "image" }
    val gradient = bg.all("linearGradient").firstOrNull()
    val rect = bg.children().firstOrNull { it.localName == "rect" }
    if (!raster) require(gradient != null && rect != null && rect.getAttribute("rx") == rect.getAttribute("ry")) { "Unsupported supplied background geometry" }
    for (module in if (wear) listOf("app-sugarwear") else listOf("app-mobile", "app-wear")) {
        val res = root.resolve("$module/src/main/res")
        val mono = if (wear) "ic_sensor_launcher_monochrome" else "ic_launcher_monochrome"
        foreground(res.resolve("drawable/$mono.xml"), data)
        foreground(res.resolve("drawable/" + if (wear) "ic_sensor_launcher_foreground.xml" else "ic_launcher_foreground.xml"), data)
        Files.newDirectoryStream(res, "mipmap-anydpi-*").use { dirs ->
            dirs.forEach { dir ->
                Files.newDirectoryStream(dir, "ic*launcher*.xml").use { files ->
                    files.forEach { file ->
                        val doc = parse(file)
                        doc.documentElement.children().firstOrNull { it.tagName == "monochrome" }?.let {
                            it.android("drawable", "@drawable/$mono")
                            writeXml(file, doc)
                        }
                    }
                }
            }
        }
        val bgName = if (wear) "sugarwear_launcher_background" else "ic_launcher_background"
        if (raster) {
            svgPng(Files.readString(bgPath), res.resolve("drawable-nodpi/${bgName}_art.png"), 432)
            writeXml(res.resolve("drawable/$bgName.xml"), parseXml("<bitmap xmlns:android=\"$ANDROID\" android:src=\"@drawable/${bgName}_art\" android:gravity=\"fill\" android:filter=\"true\"/>"))
        } else {
            val radius = rect!!.getAttribute("rx")
            val edge = 108 - radius.toDouble()
            val e = if (edge % 1 == 0.0) edge.toInt().toString() else edge.toString()
            background(res.resolve("drawable/$bgName.xml"), gradient!!, "M$radius,0H${e}A$radius,$radius 0 0 1 108,$radius V${e}A$radius,$radius 0 0 1 $e,108H${radius}A$radius,$radius 0 0 1 0,$e V${radius}A$radius,$radius 0 0 1 $radius,0Z", 108)
        }
        if (!wear) svgPng(serialized(combined), root.resolve("$module/src/main/ic_launcher-playstore.png"), 512)
    }
    brandPresentations(listOf("--product", product, "--repository-root", root.toString()), root)
    val sheet = BufferedImage(972, 728, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until 728) for (x in 0 until 972) sheet.setRGB(x, y, 0x25252c)

    fun paste(image: BufferedImage, left: Int, top: Int) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val s = image.getRGB(x, y)
                val d = sheet.getRGB(left + x, top + y)
                val alpha = s ushr 24

                fun blend(shift: Int): Int {
                    val value = ((s ushr shift and 255) * alpha + (d ushr shift and 255) * (255 - alpha)) + 128
                    return ((value + (value ushr 8)) ushr 8) shl shift
                }
                sheet.setRGB(left + x, top + y, blend(16) or blend(8) or blend(0))
            }
        }
    }
    listOf("background", "foreground", "combined").forEachIndexed { column, name ->
        val output = preview.resolve("$product-$name.png")
        svgPng(Files.readString(directory.resolve("$name.svg")), output, 324)
        val row = if (wear) 0 else 1
        paste(ImageIO.read(output.toFile()), column * 324, row * 364 + 32)
        val label = AssetToolResources::class.java.getResourceAsStream("/assets/brand-labels/$product-$name.png") ?: error("Missing fixed contact-sheet label")
        label.use { paste(ImageIO.read(it), column * 324 + 8, row * 364 + 8) }
    }
    ImageIO.write(sheet, "png", preview.resolve("adaptive108-contact-sheet.png").toFile())
}

private object AssetToolResources
