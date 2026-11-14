package app.sugarlicious.tools.maintenance.assets

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal const val ANDROID = "http://schemas.android.com/apk/res/android"

internal fun parse(path: Path) = builder().parse(path.toFile())

internal fun parseXml(text: String) = builder().parse(org.xml.sax.InputSource(StringReader(text)))

private fun builder() =
    DocumentBuilderFactory
        .newInstance()
        .apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder()

internal fun Element.children() = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

internal fun Element.all(name: String): List<Element> =
    buildList {
        if (localName == name || tagName == name) add(this@all)
        children().forEach { addAll(it.all(name)) }
    }

internal fun Element.child(name: String) = children().firstOrNull { it.localName == name || it.tagName == name } ?: error("Missing $name in $tagName")

internal fun Element.add(name: String, vararg attrs: Pair<String, Any>): Element =
    ownerDocument.createElement(name).also { node ->
        attrs.forEach { (key, value) -> node.setAttribute(key, value.toString()) }
        appendChild(node)
    }

internal fun Element.android(name: String, value: Any) = setAttributeNS(ANDROID, "android:$name", value.toString())

internal fun writeXml(path: Path, node: Node) {
    Files.createDirectories(path.parent)
    TransformerFactory
        .newInstance()
        .newTransformer()
        .apply {
            setOutputProperty("indent", "yes")
            setOutputProperty("encoding", "UTF-8")
        }.transform(DOMSource(node), StreamResult(path.toFile()))
}

internal fun svgPng(svg: String, output: Path, size: Int) {
    cairoSvg(svg, output, size)
}

internal fun copy(from: Path, to: Path) {
    Files.createDirectories(to.parent)
    if (from.toAbsolutePath().normalize() != to.toAbsolutePath().normalize()) Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}

internal fun options(args: List<String>, allowed: Set<String>): Map<String, String> {
    require(args.size % 2 == 0) { "Options require values" }
    return args.chunked(2).associate { (key, value) ->
        require(key in allowed) { "Unknown option $key" }
        key to value
    }
}

/** All paths are owned by the selected repository; shared checkouts are read-only inputs. */
fun runAssetTool(command: String, args: List<String>): Int? {
    val root = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    when (command) {
        "refresh-brand-presentations" -> brandPresentations(args, root)
        "configure-orbit-seconds-motion" -> {
            require(args.isEmpty())
            val res = root.resolve("watchfaces/sugarlicious-orbit/src/main/res")
            val face =
                app.sugarlicious.tools.maintenance.watchface
                    .parseXml(res.resolve("raw/watchface.xml"))
            val strings =
                app.sugarlicious.tools.maintenance.watchface
                    .parseXml(res.resolve("values/strings.xml"))
            app.sugarlicious.tools.maintenance.watchface
                .configureOrbitSecondsMotion(face, strings)
            app.sugarlicious.tools.maintenance.watchface
                .writeXml(face, res.resolve("raw/watchface.xml"))
            app.sugarlicious.tools.maintenance.watchface
                .writeXml(strings, res.resolve("values/strings.xml"))
        }
        "import-orbit-handset" -> {
            val opts = options(args, setOf("--source", "--resources"))
            importOrbitHandset(Path.of(opts["--source"] ?: root.resolve("design-assets/watchfaces/orbit/hands_4").toString()), Path.of(opts["--resources"] ?: root.resolve("watchfaces/sugarlicious-orbit/src/main/res").toString()))
        }
        "import-app-brand-icons", "import-adaptive-brand-icons" -> brandTool(command, args, root)
        "import-orbit-weather" -> weatherTool(args, root)
        "render-orbit-hands" -> handTool(args, root)
        "configure-vigil-decoration" -> {
            require(args.isEmpty())
            configureVigil(root)
        }
        "import-vigil-markers" -> vigilMarkers(args, root)
        else -> return null
    }
    return 0
}
